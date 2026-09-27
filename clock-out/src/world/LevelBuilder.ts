import * as THREE from 'three';
import type { LevelData } from '../data/types';
import { ColliderWorld } from './Colliders';
import { NavGrid, type Vec2 } from './NavGrid';
import {
  KitBuilder, ElevatorDoors, ClosetDoor, ceilingLight, copier, desk, divider, exitDoor, glassCell,
  makeSign, meetingTable, mulberry32, plant, standaloneYaw, wallRun, watercooler, localToWorld,
  type Copier, type Cooler, type Dir,
} from './OfficeKit';

// Turns a LevelData ASCII map into a scene graph, a collider list and a nav grid.
// Everything a level needs at runtime hangs off LevelRuntime so teardown is one call.

const TUNING = {
  wallHeight: 3,
  lightEveryCols: 3,
  lightEveryRows: 2,
  posterCount: 6,
  carpetRepeatMeters: 3,
  ceilingTileMeters: 0.75,
  ceilingTint: 0xc9cdc0,
};

const WALKABLE = new Set(['.', 'P', 'X']);
const WALLISH = new Set(['#', 'G', 'E', 'S', 'O']);

const POSTERS: string[][] = [
  ['TEAMWORK', 'Because none of us', 'can leave alone.'],
  ['SYNERGY', "We don't know what it", 'means either.'],
  ['TODAY IS A GIFT', 'Please return it', 'by 5 PM.'],
  ['DAYS SINCE LAST', '"QUICK SYNC"', '0'],
  ['YOUR MOTHER', 'DOES NOT WORK HERE', 'Clean the microwave.'],
  ['PLEASE DO NOT TAKE', 'THE GOOD SCISSORS', '— B.'],
  ['MANDATORY FUN', 'Thursday 4 PM', 'Attendance tracked.'],
  ['ATTITUDE', 'is a little thing that', 'makes a big difference.'],
  ['EXCELLENCE', 'is not an act.', 'It is a calendar hold.'],
  ['HANG IN THERE', '(the cat did not', 'make it to Friday)'],
];

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
  cellAt(c: number, r: number): string;
  isExit(x: number, z: number): boolean;
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
        case 'O': {
          const door = new ClosetDoor(kit, { cx: x, cz: z, dir: frontDir(c, r, [2, 1, 0, 3]) }, cs);
          doors.push(door);
          root.add(door.pivot);
          break;
        }
        default: break;
      }
      if (ch !== '#' && c % TUNING.lightEveryCols === 1 && r % TUNING.lightEveryRows === 1) ceilingLight(kit, x, z);
    }
  }

  // Meeting tables double as the kitchen marker in levels whose kitchen has no cooler.
  if (!kitchens.length) {
    data.ascii.forEach((row, r) => [...row].forEach((ch, c) => { if (ch === 'M') kitchens.push(center(c, r)); }));
  }

  addPosters(root, data, cellAt, cs, rand);
  root.add(kit.build());
  root.add(makeFloor(W * cs, H * cs));
  root.add(makeCeiling(W * cs, H * cs));

  const nav = new NavGrid(W, H, cs, (c, r) => WALKABLE.has(cellAt(c, r)));

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
    cellAt,
    isExit(px: number, pz: number) {
      return exitCells.has(`${Math.floor(px / cs)},${Math.floor(pz / cs)}`);
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

function addPosters(root: THREE.Group, data: LevelData, cellAt: (c: number, r: number) => string, cs: number, rand: () => number): void {
  const candidates: Array<{ c: number; r: number; dir: Dir }> = [];
  const [W, H] = data.gridSize;
  const off: Array<[Dir, number, number]> = [[0, 0, -1], [1, 1, 0], [2, 0, 1], [3, -1, 0]];
  for (let r = 0; r < H; r++) for (let c = 0; c < W; c++) {
    if (cellAt(c, r) !== '#') continue;
    for (const [dir, dx, dz] of off) if (cellAt(c + dx, r + dz) === '.') candidates.push({ c, r, dir });
  }
  const pool = [...POSTERS];
  for (let i = 0; i < TUNING.posterCount && candidates.length && pool.length; i++) {
    const spot = candidates.splice(Math.floor(rand() * candidates.length), 1)[0];
    const text = pool.splice(Math.floor(rand() * pool.length), 1)[0];
    const sign = makeSign(text, 0.8, 1.0, { bg: ['#f1e9d2', '#dfe8ef', '#efe0e0'][i % 3], fg: '#3a3a3e', font: 'bold 50px Georgia, serif' });
    const [wx, wz] = localToWorld({ cx: (spot.c + 0.5) * cs, cz: (spot.r + 0.5) * cs, dir: spot.dir }, 0, cs / 2 + 0.01);
    sign.position.set(wx, 1.6, wz);
    sign.rotation.y = standaloneYaw(spot.dir);
    root.add(sign);
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

function makeCeiling(w: number, h: number): THREE.Mesh {
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
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ map: tex, color: TUNING.ceilingTint }));
  mesh.rotation.x = Math.PI / 2;
  mesh.position.set(w / 2, TUNING.wallHeight, h / 2);
  mesh.name = 'ceiling';
  return mesh;
}
