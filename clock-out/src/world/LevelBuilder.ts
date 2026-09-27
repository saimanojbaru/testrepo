import * as THREE from 'three';
import { buildProps, type PropRuntime } from './Props';
import type { LevelData } from '../data/types';
import { ColliderWorld } from './Colliders';
import { NavGrid, type Vec2 } from './NavGrid';
import {
  KitBuilder, ElevatorDoors, ClosetDoor, ceilingLight, copier, desk, divider, exitDoor, glassCell,
  meetingTable, mulberry32, plant, wallRun, watercooler,
  vendingMachine, acousticWall, receptionDesk, stage, serverRack, beanBag, theatreSeats, foodCounter, cinemaScreen, fireExitDoor,
  type Copier, type Cooler, type Dir, type Vending,
} from './OfficeKit';
import { decorateWalls } from './WallDecor';

// Turns a LevelData ASCII map into a scene graph, a collider list and a nav grid.
// Everything a level needs at runtime hangs off LevelRuntime so teardown is one call.

const TUNING = {
  wallHeight: 3,
  lightEveryCols: 3,
  lightEveryRows: 2,
  posterCount: 6,
  carpetRepeatMeters: 3,
  /** Acoustic panels and server hum hide your footsteps within this radius (factor multiplies noise radius). */
  acousticRadius: 2.6,
  acousticFactor: 0.55,
  serverRadius: 3.5,
  serverFactor: 0.4,
  ceilingTileMeters: 0.75,
  ceilingTint: 0xc9cdc0,
};

const WALKABLE = new Set(['.', 'P', 'X']);
const WALLISH = new Set(['#', 'G', 'E', 'S', 'O', 'A', 'U', 'Q']);


export interface LevelRuntime {
  data: LevelData;
  root: THREE.Group;
  colliders: ColliderWorld;
  nav: NavGrid;
  spawn: { x: number; z: number; yaw: number };
  exitCells: Set<string>;
  exitCenter: Vec2;
  kitchens: Vec2[];
  copiers: Copier[];
  coolers: Cooler[];
  elevators: ElevatorDoors[];
  doors: ClosetDoor[];
  screenMaterial: THREE.MeshBasicMaterial;
  vendings: Vending[];
  /** Places that soak up your footsteps (acoustic panels, server racks). */
  dampers: Array<{ x: number; z: number; radius: number; factor: number }>;
  theme: 'day' | 'night' | 'theatre' | 'festival';
  props: PropRuntime;
  cellAt(c: number, r: number): string;
  isExit(x: number, z: number): boolean;
  /** Floor cell in front of a fire exit: it's an exit, but the alarm goes off. */
  isAlarmExit(x: number, z: number): boolean;
  dispose(): void;
}

export function buildLevel(data: LevelData): LevelRuntime {
  const [W, H] = data.gridSize;
  const cs = data.cellSize;
  validate(data);
  const cellAt = (c: number, r: number): string => (r < 0 || r >= H || c < 0 || c >= W ? '#' : data.ascii[r][c]);
  const center = (c: number, r: number): Vec2 => ({ x: (c + 0.5) * cs, z: (r + 0.5) * cs });
  const rand = mulberry32(hash(data.id));

  const colliders = new ColliderWorld();
  const kit = new KitBuilder(colliders, rand);
  const root = new THREE.Group();
  root.name = `level-${data.id}`;

  const exitCells = new Set<string>();
  const kitchens: Vec2[] = [];
  const copiers: Copier[] = [];
  const coolers: Cooler[] = [];
  const elevators: ElevatorDoors[] = [];
  const doors: ClosetDoor[] = [];
  const vendings: Vending[] = [];
  const dampers: LevelRuntime['dampers'] = [];
  const alarmCells = new Set<string>();
  const theme = data.theme ?? 'day';
  const screenRow = data.ascii.findIndex((row) => row.includes('U'));
  let spawnCell: [number, number] = [1, 1];

  const links = (c: number, r: number, set: Set<string>) => ({
    n: set.has(cellAt(c, r - 1)), e: set.has(cellAt(c + 1, r)), s: set.has(cellAt(c, r + 1)), w: set.has(cellAt(c - 1, r)),
  });

  /** Which way a prop in this cell should face: toward open floor, away from its backing. */
  const frontDir = (c: number, r: number, order: Dir[] = [2, 0, 1, 3]): Dir => {
    const off: Record<Dir, [number, number]> = { 0: [0, -1], 1: [1, 0], 2: [0, 1], 3: [-1, 0] };
    let best: Dir = order[0];
    let bestScore = -1;
    for (const d of order) {
      const [dx, dz] = off[d];
      let score = 0;
      if (WALKABLE.has(cellAt(c + dx, r + dz))) score += 2;
      const behind = cellAt(c - dx, r - dz);
      if (behind === 'C' || behind === '#' || behind === 'D' || behind === 'G') score += 1;
      if (score > bestScore) { bestScore = score; best = d; }
    }
    return best;
  };

  // Walls: merge horizontal runs so a whole wall line is one box.
  for (let r = 0; r < H; r++) {
    let c = 0;
    while (c < W) {
      if (cellAt(c, r) !== '#') { c++; continue; }
      const start = c;
      while (c < W && cellAt(c, r) === '#') c++;
      wallRun(kit, start * cs, r * cs, c * cs, (r + 1) * cs);
    }
  }

  for (let r = 0; r < H; r++) {
    for (let c = 0; c < W; c++) {
      const ch = cellAt(c, r);
      const { x, z } = center(c, r);
      switch (ch) {
        case 'P': spawnCell = [c, r]; break;
        case 'X':
          exitCells.add(`${c},${r}`);
          kit.box(x, 0, z, cs * 0.9, 0.012, cs * 0.9, data.exitType === 'elevator' ? 0x4a4440 : 0x5a5850, { noAO: true });
          break;
        case 'C': divider(kit, x, z, cs, links(c, r, new Set(['C']))); break;
        case 'G': glassCell(kit, x, z, cs, links(c, r, WALLISH)); break;
        case 'D': desk(kit, { cx: x, cz: z, dir: frontDir(c, r, [0, 2, 1, 3]) }); break;
        case 'L': plant(kit, x, z); break;
        case 'M': meetingTable(kit, x, z, cs, {
          n: cellAt(c, r - 1) !== 'M', e: cellAt(c + 1, r) !== 'M', s: cellAt(c, r + 1) !== 'M', w: cellAt(c - 1, r) !== 'M',
        }); break;
        case 'K': {
          const cp = copier(kit, { cx: x, cz: z, dir: frontDir(c, r) });
          copiers.push(cp);
          root.add(cp.group);
          break;
        }
        case 'W': {
          const wc = watercooler(kit, { cx: x, cz: z, dir: frontDir(c, r) });
          coolers.push(wc);
          kitchens.push({ x: wc.x, z: wc.z });
          root.add(wc.group);
          break;
        }
        case 'E': {
          exitCells.add(`${c},${r}`);
          const el = new ElevatorDoors(kit, { cx: x, cz: z, dir: frontDir(c, r) }, cs);
          elevators.push(el);
          root.add(el.group);
          break;
        }
        case 'S': {
          exitCells.add(`${c},${r}`);
          const variant = data.exitType === 'outside-door' ? 'outside-door' : 'stairwell';
          root.add(exitDoor(kit, { cx: x, cz: z, dir: frontDir(c, r) }, cs, variant));
          break;
        }
        case 'V': {
          const v = vendingMachine(kit, { cx: x, cz: z, dir: frontDir(c, r) });
          vendings.push(v);
          kitchens.push({ x: v.x, z: v.z });
          root.add(v.group);
          break;
        }
        case 'A': {
          const faces: Dir[] = [];
          ([[0, 0, -1], [1, 1, 0], [2, 0, 1], [3, -1, 0]] as Array<[Dir, number, number]>).forEach(([d, dx, dz]) => { if (WALKABLE.has(cellAt(c + dx, r + dz))) faces.push(d); });
          acousticWall(kit, x, z, cs, faces);
          dampers.push({ x, z, radius: TUNING.acousticRadius, factor: TUNING.acousticFactor });
          break;
        }
        case 'R': receptionDesk(kit, { cx: x, cz: z, dir: frontDir(c, r) }); break;
        case 'T': stage(kit, x, z, cs, rand); break;
        case 'Z':
          serverRack(kit, { cx: x, cz: z, dir: frontDir(c, r) }, rand);
          dampers.push({ x, z, radius: TUNING.serverRadius, factor: TUNING.serverFactor });
          break;
        case 'B': beanBag(kit, x, z, [0xe76f51, 0x2a9d8f, 0xe9c46a, 0x6d597a][(c + r) % 4]); break;
        case 'H': theatreSeats(kit, { cx: x, cz: z, dir: screenRow >= 0 && screenRow < r ? 2 : 0 }); break;
        case 'F': foodCounter(kit, { cx: x, cz: z, dir: frontDir(c, r) }); break;
        case 'U': cinemaScreen(kit, x, z, cs, frontDir(c, r)); break;
        case 'Q': {
          const dir = frontDir(c, r);
          root.add(fireExitDoor(kit, { cx: x, cz: z, dir }, cs));
          const off: Record<Dir, [number, number]> = { 0: [0, -1], 1: [1, 0], 2: [0, 1], 3: [-1, 0] };
          alarmCells.add(`${c + off[dir][0]},${r + off[dir][1]}`);
          break;
        }
        case 'O': {
          const door = new ClosetDoor(kit, { cx: x, cz: z, dir: frontDir(c, r, [2, 1, 0, 3]) }, cs);
          doors.push(door);
          root.add(door.pivot);
          break;
        }
        default: break;
      }
      // Night: only every other panel is on. Theatre: house lights are off.
      const lit = theme === 'theatre' ? false
        : theme === 'night' ? c % (TUNING.lightEveryCols * 2) === 1 && r % (TUNING.lightEveryRows * 2) === 1
          : c % TUNING.lightEveryCols === 1 && r % TUNING.lightEveryRows === 1;
      if (!WALLISH.has(ch) && ch !== '#' && lit) ceilingLight(kit, x, z);
    }
  }

  // Meeting tables double as the kitchen marker in levels whose kitchen has no cooler.
  if (!kitchens.length) {
    data.ascii.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === 'M') kitchens.push(center(c, r)); }));
  }

  if (theme !== 'theatre') decorateWalls(root, cellAt, W, H, cs, rand, theme);
  if (theme === 'festival') addRangoli(root, exitCells, cs, rand);
  root.add(kit.build());
  root.add(makeFloor(W * cs, H * cs));
  root.add(makeCeiling(W * cs, H * cs, theme));

  const props = buildProps(root, data.props, cs, colliders);
  // Floor props with colliders also take their cell out of the nav grid.
  const propBlocked = new Set((data.props ?? []).filter((p) => p.kind === 'cleaningCart' || p.kind === 'deskLamp').map((p) => p.cell.join(',')));
  const nav = new NavGrid(W, H, cs, (c, r) => WALKABLE.has(cellAt(c, r)) && !propBlocked.has(`${c},${r}`));

  let ex = 0, ez = 0;
  for (const key of exitCells) {
    const [c, r] = key.split(',').map(Number);
    const p = center(c, r);
    ex += p.x; ez += p.z;
  }
  const exitCenter = { x: ex / exitCells.size, z: ez / exitCells.size };
  const sp = center(...spawnCell);
  // Face the exit on spawn so the first thing the player sees is where they want to be.
  const spawnYaw = Math.atan2(-(exitCenter.x - sp.x), -(exitCenter.z - sp.z));

  return {
    data, root, colliders, nav,
    spawn: { x: sp.x, z: sp.z, yaw: spawnYaw },
    exitCells, exitCenter, kitchens, copiers, coolers, elevators, doors,
    screenMaterial: kit.screenMaterial,
    vendings, dampers, theme, props,
    cellAt,
    isAlarmExit(px: number, pz: number) {
      return alarmCells.has(`${Math.floor(px / cs)},${Math.floor(pz / cs)}`);
    },
    isExit(px: number, pz: number) {
      const key = `${Math.floor(px / cs)},${Math.floor(pz / cs)}`;
      return exitCells.has(key) || alarmCells.has(key);
    },
    dispose() {
      root.traverse((o) => {
        const m = o as THREE.Mesh;
        if (m.geometry) m.geometry.dispose();
        const mat = m.material as THREE.Material | THREE.Material[] | undefined;
        for (const mm of Array.isArray(mat) ? mat : mat ? [mat] : []) {
          const map = (mm as THREE.MeshBasicMaterial).map;
          if (map) map.dispose();
          mm.dispose();
        }
      });
    },
  };
}

function validate(data: LevelData): void {
  const [W, H] = data.gridSize;
  if (data.ascii.length !== H) throw new Error(`${data.id}: expected ${H} rows, got ${data.ascii.length}`);
  data.ascii.forEach((row, i) => {
    if (row.length !== W) throw new Error(`${data.id}: row ${i} has ${row.length} cols, expected ${W}`);
  });
  if (!data.ascii.some((r) => r.includes('P'))) throw new Error(`${data.id}: no player spawn`);
  if (!data.ascii.some((r) => r.includes('X'))) throw new Error(`${data.id}: no exit trigger`);
}

function hash(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
}

/** A rangoli painted in front of each exit for the festival chapter. */
function addRangoli(root: THREE.Group, exitCells: Set<string>, cs: number, rand: () => number): void {
  const tex = canvasTexture(256, (g, sz) => {
    g.clearRect(0, 0, sz, sz);
    const colors = ['#e63946', '#ffb703', '#2a9d8f', '#9b5de5', '#fb8500', '#f1faee'];
    for (let ring = 5; ring >= 0; ring--) {
      g.fillStyle = colors[ring % colors.length];
      const petals = 8 + ring * 2;
      for (let i = 0; i < petals; i++) {
        const a = (i / petals) * Math.PI * 2 + rand() * 0.02;
        g.beginPath();
        g.ellipse(sz / 2 + Math.cos(a) * ring * 18, sz / 2 + Math.sin(a) * ring * 18, 14, 7, a, 0, Math.PI * 2);
        g.fill();
      }
    }
  });
  tex.wrapS = tex.wrapT = THREE.ClampToEdgeWrapping;
  const mat = new THREE.MeshLambertMaterial({ map: tex, transparent: true, depthWrite: false });
  for (const key of exitCells) {
    const [c, r] = key.split(',').map(Number);
    const m = new THREE.Mesh(new THREE.PlaneGeometry(cs * 0.95, cs * 0.95), mat);
    m.rotation.x = -Math.PI / 2;
    m.position.set((c + 0.5) * cs, 0.016, (r + 0.5) * cs);
    root.add(m);
    // Diyas at the corners of the rangoli.
    for (const [dx, dz] of [[-0.55, -0.55], [0.55, 0.55]]) {
      const diya = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.035, 0.035, 8), new THREE.MeshLambertMaterial({ color: 0x9c4a1a }));
      diya.position.set((c + 0.5) * cs + dx, 0.02, (r + 0.5) * cs + dz);
      const flame = new THREE.Mesh(new THREE.ConeGeometry(0.015, 0.05, 6), new THREE.MeshBasicMaterial({ color: 0xffc14d }));
      flame.position.set(diya.position.x, 0.07, diya.position.z);
      root.add(diya, flame);
    }
  }
}

function canvasTexture(size: number, draw: (g: CanvasRenderingContext2D, size: number) => void): THREE.CanvasTexture {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  draw(c.getContext('2d')!, size);
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 8;
  return t;
}

function makeFloor(w: number, h: number): THREE.Mesh {
  const rand = mulberry32(7);
  const tex = canvasTexture(256, (g, s) => {
    g.fillStyle = '#7f7c72';
    g.fillRect(0, 0, s, s);
    // Speckle + faint carpet-tile seams: reads as commercial carpet at a glance.
    for (let i = 0; i < 5000; i++) {
      const v = 100 + Math.floor(rand() * 50);
      g.fillStyle = `rgba(${v},${v - 4},${v - 14},0.5)`;
      g.fillRect(rand() * s, rand() * s, 1.5, 1.5);
    }
    g.strokeStyle = 'rgba(40,40,36,0.25)';
    g.lineWidth = 2;
    g.strokeRect(0, 0, s, s);
  });
  tex.repeat.set(w / TUNING.carpetRepeatMeters, h / TUNING.carpetRepeatMeters);
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshLambertMaterial({ map: tex }));
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.set(w / 2, 0, h / 2);
  mesh.name = 'floor';
  return mesh;
}

function makeCeiling(w: number, h: number, theme: string): THREE.Mesh {
  const tex = canvasTexture(128, (g, s) => {
    g.fillStyle = '#e4e2da';
    g.fillRect(0, 0, s, s);
    const rand = mulberry32(3);
    for (let i = 0; i < 600; i++) {
      g.fillStyle = 'rgba(120,118,110,0.35)';
      g.fillRect(rand() * s, rand() * s, 1, 1);
    }
    g.strokeStyle = '#b9b6ab';
    g.lineWidth = 4;
    g.strokeRect(0, 0, s, s);
  });
  tex.repeat.set(w / TUNING.ceilingTileMeters, h / TUNING.ceilingTileMeters);
  // Unlit: a downward-facing Lambert ceiling only gets the hemisphere's dim ground colour and reads as grime.
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ map: tex, color: theme === 'day' || theme === 'festival' ? TUNING.ceilingTint : 0x3a3c40 }));
  mesh.rotation.x = Math.PI / 2;
  mesh.position.set(w / 2, TUNING.wallHeight, h / 2);
  mesh.name = 'ceiling';
  return mesh;
}
