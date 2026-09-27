import * as THREE from 'three';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
import type { ColliderKind, ColliderWorld, AABB } from './Colliders';

// Procedural office furniture. Static props are accumulated as box/cylinder
// geometry with baked vertex colours (a fake floor-contact darkening stands in for
// ambient occlusion) and merged into a handful of meshes, so a whole floor costs a
// few draw calls. Anything that moves or can be used (copiers, doors, elevators)
// is a standalone object with its own material so it can animate and highlight.

export const PALETTE = {
  beige: 0xcfc7b4,
  grey: 0x8f8f8a,
  dark: 0x2b2b2e,
  blue: 0x3f5f8a,
  green: 0x4a6b3f,
  wall: 0xd9d3c3,
  baseboard: 0x55524c,
  divider: 0x8f949a,
  dividerTrim: 0xb9b2a0,
  wood: 0xa88a64,
  paper: 0xefece3,
  steel: 0xa3a7a8,
  glass: 0xbcd6d2,
  screen: 0x8fb4d8,
  light: 0xe8f0e4,
  exitGreen: 0x3fbf6a,
};

const TUNING = {
  wallHeight: 3,
  dividerHeight: 1.5, // the exact height that makes crouching meaningful
  dividerThickness: 0.08,
  deskHeight: 0.74,
  copierHeight: 1.3,
  /** Vertex brightness at floor level; ramps to 1.0 by aoHeight. */
  aoFloor: 0.68,
  aoHeight: 1.3,
  tintJitter: 0.035,
};

/** Cardinal facing used by builders. The "front" is where a person using the prop stands. */
export type Dir = 0 | 1 | 2 | 3; // N, E, S, W

const FRONT: Record<Dir, [number, number]> = { 0: [0, -1], 1: [1, 0], 2: [0, 1], 3: [-1, 0] };
const RIGHT: Record<Dir, [number, number]> = { 0: [1, 0], 1: [0, 1], 2: [-1, 0], 3: [0, -1] };

export function dirToYaw(dir: Dir): number {
  // Our yaw convention: 0 faces -Z (north), positive turns toward +X (east).
  return -dir * (Math.PI / 2);
}

/** Rotation for standalone groups built with local +Z pointing at the prop's front. */
export function standaloneYaw(dir: Dir): number {
  return dirToYaw(dir) + Math.PI;
}

export interface BoxOpts {
  collide?: ColliderKind;
  sight?: boolean;
  layer?: 'solid' | 'glow' | 'screen' | 'glass';
  noAO?: boolean;
}

/** Local frame for a prop: lx runs along its right, lz toward its front. */
export interface Frame { cx: number; cz: number; dir: Dir; }

export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export class KitBuilder {
  private layers: Record<NonNullable<BoxOpts['layer']>, THREE.BufferGeometry[]> = { solid: [], glow: [], screen: [], glass: [] };
  readonly screenMaterial = new THREE.MeshBasicMaterial({ color: PALETTE.screen });
  private tmpColor = new THREE.Color();

  constructor(readonly colliders: ColliderWorld, readonly rand: () => number) {}

  /** World-space box with its bottom at y0. */
  box(x: number, y0: number, z: number, sx: number, sy: number, sz: number, color: number, opts: BoxOpts = {}): AABB | null {
    const g = new THREE.BoxGeometry(sx, sy, sz);
    g.translate(x, y0 + sy / 2, z);
    this.push(g, color, opts);
    if (!opts.collide) return null;
    return this.colliders.add({
      minX: x - sx / 2, maxX: x + sx / 2,
      minY: y0, maxY: y0 + sy,
      minZ: z - sz / 2, maxZ: z + sz / 2,
      kind: opts.collide,
      blocksSight: opts.sight ?? true,
    });
  }

  cyl(x: number, y0: number, z: number, rTop: number, rBot: number, h: number, color: number, opts: BoxOpts = {}, segs = 10): void {
    const g = new THREE.CylinderGeometry(rTop, rBot, h, segs);
    g.translate(x, y0 + h / 2, z);
    this.push(g, color, opts);
    if (opts.collide) {
      const r = Math.max(rTop, rBot);
      this.colliders.add({ minX: x - r, maxX: x + r, minY: y0, maxY: y0 + h, minZ: z - r, maxZ: z + r, kind: opts.collide, blocksSight: opts.sight ?? false });
    }
  }

  /** Box in a prop's local frame (lx along its right, lz toward its front). */
  lbox(f: Frame, lx: number, y0: number, lz: number, sx: number, sy: number, sz: number, color: number, opts: BoxOpts = {}): AABB | null {
    const [wx, wz] = localToWorld(f, lx, lz);
    const swap = f.dir === 1 || f.dir === 3;
    return this.box(wx, y0, wz, swap ? sz : sx, sy, swap ? sx : sz, color, opts);
  }

  lcyl(f: Frame, lx: number, y0: number, lz: number, rTop: number, rBot: number, h: number, color: number, opts: BoxOpts = {}, segs = 10): void {
    const [wx, wz] = localToWorld(f, lx, lz);
    this.cyl(wx, y0, wz, rTop, rBot, h, color, opts, segs);
  }

  private push(g: THREE.BufferGeometry, color: number, opts: BoxOpts): void {
    g.deleteAttribute('uv');
    const pos = g.getAttribute('position');
    const colors = new Float32Array(pos.count * 3);
    const jitter = 1 + (this.rand() - 0.5) * 2 * TUNING.tintJitter;
    this.tmpColor.set(color);
    for (let i = 0; i < pos.count; i++) {
      const y = pos.getY(i);
      const t = Math.min(1, Math.max(0, y / TUNING.aoHeight));
      const ao = opts.noAO ? 1 : TUNING.aoFloor + (1 - TUNING.aoFloor) * t * t * (3 - 2 * t);
      const k = ao * jitter;
      colors[i * 3] = this.tmpColor.r * k;
      colors[i * 3 + 1] = this.tmpColor.g * k;
      colors[i * 3 + 2] = this.tmpColor.b * k;
    }
    g.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    this.layers[opts.layer ?? 'solid'].push(g);
  }

  build(): THREE.Group {
    const group = new THREE.Group();
    const mats: Record<string, THREE.Material> = {
      solid: new THREE.MeshLambertMaterial({ vertexColors: true, flatShading: true }),
      glow: new THREE.MeshBasicMaterial({ vertexColors: true }),
      screen: this.screenMaterial,
      glass: new THREE.MeshLambertMaterial({ vertexColors: true, transparent: true, opacity: 0.22, depthWrite: false }),
    };
    for (const [name, geos] of Object.entries(this.layers)) {
      if (!geos.length) continue;
      const merged = mergeGeometries(geos, false);
      if (!merged) throw new Error(`Failed to merge ${name} geometry`);
      for (const g of geos) g.dispose();
      const mesh = new THREE.Mesh(merged, mats[name]);
      mesh.name = `kit-${name}`;
      if (name === 'glass') mesh.renderOrder = 2;
      group.add(mesh);
    }
    return group;
  }
}

export function localToWorld(f: Frame, lx: number, lz: number): [number, number] {
  const [fx, fz] = FRONT[f.dir];
  const [rx, rz] = RIGHT[f.dir];
  return [f.cx + rx * lx + fx * lz, f.cz + rz * lx + fz * lz];
}

// ---------------------------------------------------------------------------
// Static builders (merged)
// ---------------------------------------------------------------------------

export function wallRun(kit: KitBuilder, x0: number, z0: number, x1: number, z1: number): void {
  const cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
  const sx = x1 - x0, sz = z1 - z0;
  kit.box(cx, 0, cz, sx, TUNING.wallHeight, sz, PALETTE.wall, { collide: 'wall' });
  kit.box(cx, 0, cz, sx + 0.03, 0.1, sz + 0.03, PALETTE.baseboard);
}

/** Cubicle divider: a post plus half-cell panels toward each linked neighbour. */
export function divider(kit: KitBuilder, cx: number, cz: number, cell: number, links: { n: boolean; e: boolean; s: boolean; w: boolean }): void {
  const h = TUNING.dividerHeight, t = TUNING.dividerThickness, half = cell / 2;
  const any = links.n || links.e || links.s || links.w;
  const segs: Array<[number, number, number, number]> = []; // cx, cz, sx, sz
  if (links.e || !any) segs.push([cx + half / 2, cz, half, t]);
  if (links.w || !any) segs.push([cx - half / 2, cz, half, t]);
  if (links.n) segs.push([cx, cz - half / 2, t, half]);
  if (links.s) segs.push([cx, cz + half / 2, t, half]);
  for (const [x, z, sx, sz] of segs) {
    kit.box(x, 0.02, z, sx, h - 0.05, sz, PALETTE.divider, { collide: 'divider' });
    kit.box(x, h - 0.03, z, sx + 0.01, 0.04, sz + 0.01, PALETTE.dividerTrim);
  }
  kit.box(cx, 0, cz, t * 1.6, h + 0.02, t * 1.6, PALETTE.dividerTrim);
}

export function desk(kit: KitBuilder, f: Frame): void {
  const r = kit.rand;
  const top = TUNING.deskHeight;
  kit.lbox(f, 0, top - 0.04, -0.25, 1.4, 0.04, 0.75, PALETTE.beige, { collide: 'prop' });
  // The top's collider starts at the floor so you can't walk under the desk.
  kit.colliders.add(boxCollider(f, 0, 0, -0.25, 1.4, top, 0.75, 'prop', true));
  kit.lbox(f, -0.68, 0, -0.25, 0.04, top - 0.04, 0.7, PALETTE.grey);
  kit.lbox(f, 0.68, 0, -0.25, 0.04, top - 0.04, 0.7, PALETTE.grey);
  kit.lbox(f, 0, 0.15, -0.6, 1.3, 0.5, 0.03, PALETTE.grey);
  // Monitor, facing the chair.
  kit.lbox(f, 0, top, -0.45, 0.2, 0.02, 0.15, PALETTE.dark);
  kit.lbox(f, 0, top, -0.47, 0.05, 0.25, 0.04, PALETTE.dark);
  kit.lbox(f, 0, top + 0.18, -0.45, 0.58, 0.36, 0.04, PALETTE.dark);
  kit.lbox(f, 0, top + 0.2, -0.425, 0.52, 0.3, 0.01, PALETTE.screen, { layer: 'screen', noAO: true });
  kit.lbox(f, 0, top, -0.12, 0.45, 0.02, 0.15, PALETTE.dark);
  // Clutter, so no two desks read as copies.
  if (r() < 0.6) kit.lcyl(f, 0.45 + r() * 0.15, top, -0.15, 0.045, 0.04, 0.1, [0xc0392b, 0xe8e6df, 0x3f5f8a, 0xd4a017][Math.floor(r() * 4)]);
  if (r() < 0.5) kit.lbox(f, -0.45, top, -0.2, 0.22, 0.03 + r() * 0.06, 0.3, PALETTE.paper);
  if (r() < 0.35) kit.lbox(f, -0.55, top, -0.48, 0.12, 0.16, 0.03, PALETTE.wood);
  if (r() < 0.25) {
    kit.lcyl(f, 0.55, top, -0.45, 0.05, 0.04, 0.08, 0xb5651d);
    kit.lcyl(f, 0.55, top + 0.08, -0.45, 0.01, 0.07, 0.15, PALETTE.green, {}, 6);
  }
  chair(kit, f, 0, 0.3 + r() * 0.12, (r() - 0.5) * 0.4);
}

function boxCollider(f: Frame, lx: number, y0: number, lz: number, sx: number, sy: number, sz: number, kind: ColliderKind, sight: boolean): Omit<AABB, 'enabled'> {
  const [wx, wz] = localToWorld(f, lx, lz);
  const swap = f.dir === 1 || f.dir === 3;
  const hx = (swap ? sz : sx) / 2, hz = (swap ? sx : sz) / 2;
  return { minX: wx - hx, maxX: wx + hx, minY: y0, maxY: y0 + sy, minZ: wz - hz, maxZ: wz + hz, kind, blocksSight: sight };
}

export function chair(kit: KitBuilder, f: Frame, lx: number, lz: number, _twist: number): void {
  kit.lcyl(f, lx, 0, lz, 0.25, 0.25, 0.04, PALETTE.dark, {}, 5);
  kit.lcyl(f, lx, 0.04, lz, 0.03, 0.03, 0.38, PALETTE.steel, {}, 6);
  kit.lbox(f, lx, 0.42, lz, 0.48, 0.07, 0.46, PALETTE.dark);
  kit.lbox(f, lx, 0.5, lz + 0.22, 0.46, 0.5, 0.06, PALETTE.dark);
}

export function plant(kit: KitBuilder, cx: number, cz: number): void {
  kit.cyl(cx, 0, cz, 0.24, 0.18, 0.42, 0x8a6f58, { collide: 'prop', sight: false });
  kit.cyl(cx, 0.42, cz, 0.23, 0.23, 0.03, 0x3b2f25);
  kit.cyl(cx, 0.45, cz, 0.02, 0.3, 0.7, PALETTE.green, {}, 7);
  kit.cyl(cx + 0.05, 0.85, cz - 0.03, 0.01, 0.24, 0.6, 0x5a7d4a, {}, 6);
  kit.cyl(cx - 0.06, 1.15, cz + 0.04, 0.0, 0.17, 0.45, PALETTE.green, {}, 6);
}

export function meetingTable(kit: KitBuilder, cx: number, cz: number, cell: number, open: { n: boolean; e: boolean; s: boolean; w: boolean }): void {
  const top = TUNING.deskHeight;
  kit.box(cx, top - 0.05, cz, cell, 0.05, cell, PALETTE.wood, { collide: 'prop', sight: false });
  kit.colliders.add({ minX: cx - cell / 2, maxX: cx + cell / 2, minY: 0, maxY: top, minZ: cz - cell / 2, maxZ: cz + cell / 2, kind: 'prop', blocksSight: false });
  kit.cyl(cx, 0, cz, 0.08, 0.2, top - 0.05, PALETTE.dark, {}, 8);
  // Chairs on every side that isn't more table.
  const sides: Array<[boolean, Dir]> = [[open.n, 0], [open.e, 1], [open.s, 2], [open.w, 3]];
  for (const [isOpen, dir] of sides) {
    if (!isOpen) continue;
    // A chair's local front is the sitter's back, so it points away from the table.
    const [fx, fz] = FRONT[dir];
    chair(kit, { cx: cx + fx * (cell / 2 + 0.05), cz: cz + fz * (cell / 2 + 0.05), dir }, 0, 0, 0);
  }
  kit.box(cx - 0.2, top, cz + 0.1, 0.3, 0.01, 0.22, PALETTE.paper);
}

export function ceilingLight(kit: KitBuilder, cx: number, cz: number): void {
  kit.box(cx, TUNING.wallHeight - 0.06, cz, 1.26, 0.05, 0.66, PALETTE.steel, { noAO: true });
  kit.box(cx, TUNING.wallHeight - 0.07, cz, 1.18, 0.02, 0.58, PALETTE.light, { layer: 'glow', noAO: true });
}

/** Glass office wall: half-panes from the cell centre toward each linked neighbour, like dividers. */
export function glassCell(kit: KitBuilder, cx: number, cz: number, cell: number, links: { n: boolean; e: boolean; s: boolean; w: boolean }): void {
  const t = 0.06, half = cell / 2, H = TUNING.wallHeight;
  const any = links.n || links.e || links.s || links.w;
  const segs: Array<[number, number, number, number]> = [];
  if (links.e || !any) segs.push([cx + half / 2, cz, half, t]);
  if (links.w || !any) segs.push([cx - half / 2, cz, half, t]);
  if (links.n) segs.push([cx, cz - half / 2, t, half]);
  if (links.s) segs.push([cx, cz + half / 2, t, half]);
  for (const [x, z, sx, sz] of segs) {
    kit.box(x, 0, z, sx, H, sz, PALETTE.glass, { layer: 'glass', noAO: true });
    kit.colliders.add({ minX: x - sx / 2 - 0.03, maxX: x + sx / 2 + 0.03, minY: 0, maxY: H, minZ: z - sz / 2 - 0.03, maxZ: z + sz / 2 + 0.03, kind: 'glass', blocksSight: false });
    // Sill, head rail and a frosted band at desk height, like every glass office ever.
    kit.box(x, 0, z, sx + 0.01, 0.08, sz + 0.01, PALETTE.steel);
    kit.box(x, H - 0.1, z, sx + 0.01, 0.1, sz + 0.01, PALETTE.steel, { noAO: true });
    kit.box(x, 1.05, z, sx + 0.004, 0.12, sz + 0.004, 0xe7eeec, { noAO: true });
  }
  kit.box(cx, 0, cz, 0.07, H, 0.07, PALETTE.steel);
}

/** Wall filler above/around doors (the E/S cells themselves). */
export function doorSurround(kit: KitBuilder, f: Frame, cell: number, openingW: number, openingH: number): void {
  const side = (cell - openingW) / 2;
  kit.lbox(f, -(openingW / 2 + side / 2), 0, 0, side, TUNING.wallHeight, cell, PALETTE.wall, { collide: 'wall' });
  kit.lbox(f, openingW / 2 + side / 2, 0, 0, side, TUNING.wallHeight, cell, PALETTE.wall, { collide: 'wall' });
  kit.lbox(f, 0, openingH, 0, openingW, TUNING.wallHeight - openingH, cell, PALETTE.wall);
  // Back wall of the recess; the door panels live in front of it.
  kit.lbox(f, 0, 0, -0.45, openingW, openingH, cell - 0.9, PALETTE.dark, { collide: 'wall' });
}

// ---------------------------------------------------------------------------
// Standalone (interactive / animated) props
// ---------------------------------------------------------------------------

function lambert(color: number): THREE.MeshLambertMaterial {
  return new THREE.MeshLambertMaterial({ color, flatShading: true });
}

function addBox(parent: THREE.Object3D, x: number, y0: number, z: number, sx: number, sy: number, sz: number, mat: THREE.Material): THREE.Mesh {
  const m = new THREE.Mesh(new THREE.BoxGeometry(sx, sy, sz), mat);
  m.position.set(x, y0 + sy / 2, z);
  parent.add(m);
  return m;
}

export function makeLabelTexture(lines: string[], opts: { w?: number; h?: number; bg?: string; fg?: string; font?: string } = {}): THREE.CanvasTexture {
  const w = opts.w ?? 512, h = opts.h ?? 256;
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const g = c.getContext('2d')!;
  g.fillStyle = opts.bg ?? '#f4f1e8';
  g.fillRect(0, 0, w, h);
  g.fillStyle = opts.fg ?? '#2b2b2e';
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  const font = opts.font ?? 'bold 44px Helvetica, Arial, sans-serif';
  g.font = font;
  const lh = h / (lines.length + 1);
  lines.forEach((line, i) => {
    // Shrink long lines to fit rather than clipping them.
    let size = parseInt(/(\d+)px/.exec(font)?.[1] ?? '44', 10);
    while (g.measureText(line).width > w * 0.9 && size > 12) {
      size -= 2;
      g.font = font.replace(/\d+px/, `${size}px`);
    }
    g.fillText(line, w / 2, lh * (i + 1));
    g.font = font;
  });
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

export function makeSign(lines: string[], width: number, height: number, opts: { bg?: string; fg?: string; font?: string; glow?: boolean } = {}): THREE.Mesh {
  const tex = makeLabelTexture(lines, { w: 512, h: Math.round(512 * (height / width)), ...opts });
  const mat = opts.glow ? new THREE.MeshBasicMaterial({ map: tex }) : new THREE.MeshLambertMaterial({ map: tex });
  return new THREE.Mesh(new THREE.PlaneGeometry(width, height), mat);
}

/** Places a flat object on a wall/door face so it faces along `f.dir`. */
export function orientTo(obj: THREE.Object3D, f: Frame, lx: number, y: number, lz: number): void {
  const [wx, wz] = localToWorld(f, lx, lz);
  obj.position.set(wx, y, wz);
  obj.rotation.y = dirToYaw(f.dir);
}

export interface Copier {
  group: THREE.Group;
  materials: THREE.MeshLambertMaterial[];
  panel: THREE.Mesh;
  x: number;
  z: number;
  jammedUntil: number;
}

export function copier(kit: KitBuilder, f: Frame): Copier {
  const group = new THREE.Group();
  const body = lambert(0xd6d3cb), dark = lambert(PALETTE.dark), tray = lambert(0xbcb7ab);
  const [wx, wz] = localToWorld(f, 0, -0.2);
  group.position.set(wx, 0, wz);
  group.rotation.y = standaloneYaw(f.dir);
  addBox(group, 0, 0, 0, 1.0, 0.9, 0.7, body);
  addBox(group, 0, 0.9, 0, 1.0, 0.3, 0.7, tray);
  addBox(group, 0, 1.2, 0.02, 0.98, 0.08, 0.66, dark);
  addBox(group, 0.62, 0.7, 0, 0.26, 0.04, 0.4, tray);
  const panelMat = new THREE.MeshBasicMaterial({ color: 0x4fdc7a });
  const panel = addBox(group, -0.3, 1.2, 0.36, 0.25, 0.06, 0.04, panelMat);
  panel.rotation.x = -0.4;
  addBox(group, 0, 0.1, 0.36, 0.9, 0.02, 0.02, dark);
  addBox(group, 0, 0.45, 0.36, 0.9, 0.02, 0.02, dark);
  const col = boxCollider(f, 0, 0, -0.2, 1.05, TUNING.copierHeight, 0.75, 'prop', true);
  kit.colliders.add(col);
  return { group, materials: [body, tray], panel, x: wx, z: wz, jammedUntil: 0 };
}

export interface Cooler { group: THREE.Group; materials: THREE.MeshLambertMaterial[]; x: number; z: number; }

export function watercooler(kit: KitBuilder, f: Frame): Cooler {
  const group = new THREE.Group();
  const [wx, wz] = localToWorld(f, 0, -0.3);
  group.position.set(wx, 0, wz);
  group.rotation.y = standaloneYaw(f.dir);
  const body = lambert(0xe9e7e0);
  addBox(group, 0, 0, 0, 0.36, 0.95, 0.36, body);
  const bottle = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.15, 0.45, 12), new THREE.MeshLambertMaterial({ color: 0x7fb3d9, transparent: true, opacity: 0.7 }));
  bottle.position.y = 0.95 + 0.225;
  group.add(bottle);
  addBox(group, 0.08, 0.72, 0.19, 0.05, 0.05, 0.04, lambert(0xc0392b));
  addBox(group, -0.08, 0.72, 0.19, 0.05, 0.05, 0.04, lambert(0x2f6dbf));
  kit.colliders.add({ minX: wx - 0.2, maxX: wx + 0.2, minY: 0, maxY: 1.4, minZ: wz - 0.2, maxZ: wz + 0.2, kind: 'prop', blocksSight: false });
  return { group, materials: [body], x: wx, z: wz };
}

export class ElevatorDoors {
  readonly group = new THREE.Group();
  readonly left: THREE.Mesh;
  readonly right: THREE.Mesh;
  readonly button: THREE.Mesh;
  readonly buttonMat = new THREE.MeshBasicMaterial({ color: 0x777777 });
  readonly lampMat = new THREE.MeshBasicMaterial({ color: 0x333333 });
  readonly collider: AABB;
  /** 0 closed .. 1 open. */
  openAmount = 0;
  private openTimer = 0;
  private readonly width = 1.1;

  constructor(kit: KitBuilder, readonly frame: Frame, cell: number) {
    const w = this.width, h = 2.2, face = cell / 2;
    doorSurround(kit, frame, cell, w, h);
    const [wx, wz] = localToWorld(frame, 0, 0);
    this.group.position.set(wx, 0, wz);
    this.group.rotation.y = standaloneYaw(frame.dir);
    const steel = new THREE.MeshLambertMaterial({ color: 0xb8bcbd, flatShading: true });
    // Car interior: warm light so an open door reads as "salvation" from across the floor.
    const inside = new THREE.MeshBasicMaterial({ color: 0xf6ecd0 });
    addBox(this.group, 0, 0, -0.14, w, h, 0.02, inside);
    addBox(this.group, 0, 0, -0.14, w, 0.02, 0.7, lambert(0x6b5a48));
    this.left = addBox(this.group, -w / 4, 0, face - 0.12, w / 2, h, 0.05, steel);
    this.right = addBox(this.group, w / 4, 0, face - 0.12, w / 2, h, 0.05, steel);
    const trim = lambert(0x8d9192);
    addBox(this.group, 0, h, face + 0.01, w + 0.16, 0.08, 0.04, trim);
    addBox(this.group, -(w / 2 + 0.04), 0, face + 0.01, 0.08, h, 0.04, trim);
    addBox(this.group, w / 2 + 0.04, 0, face + 0.01, 0.08, h, 0.04, trim);
    // Up/down lamp over the door, call button on the frame's right.
    addBox(this.group, 0, h + 0.2, face + 0.01, 0.3, 0.12, 0.02, this.lampMat);
    this.button = addBox(this.group, w / 2 + 0.13, 1.1, face + 0.02, 0.08, 0.14, 0.03, this.buttonMat);
    const [dx, dz] = localToWorld(frame, 0, face - 0.12);
    const alongX = frame.dir === 0 || frame.dir === 2;
    this.collider = kit.colliders.add({
      minX: dx - (alongX ? w / 2 : 0.06), maxX: dx + (alongX ? w / 2 : 0.06),
      minY: 0, maxY: h,
      minZ: dz - (alongX ? 0.06 : w / 2), maxZ: dz + (alongX ? 0.06 : w / 2),
      kind: 'door', blocksSight: true,
    });
  }

  get isOpen(): boolean { return this.openAmount > 0.85; }

  get timeLeft(): number { return Math.max(0, this.openTimer); }

  openFor(seconds: number): void {
    this.openTimer = seconds;
  }

  update(dt: number): void {
    const target = this.openTimer > 0 ? 1 : 0;
    if (this.openTimer > 0) this.openTimer -= dt;
    this.openAmount += Math.sign(target - this.openAmount) * Math.min(Math.abs(target - this.openAmount), dt / 1.2);
    const slide = (this.width / 2) * this.openAmount;
    this.left.position.x = -this.width / 4 - slide;
    this.right.position.x = this.width / 4 + slide;
    this.collider.enabled = this.openAmount < 0.5;
    this.lampMat.color.set(this.openTimer > 0 ? 0xffe28a : 0x333333);
  }
}

export class ClosetDoor {
  readonly pivot = new THREE.Group();
  readonly panel: THREE.Mesh;
  readonly material = lambert(0xb89f7d);
  readonly collider: AABB;
  open = false;
  locked = false;
  private angle = 0;

  constructor(kit: KitBuilder, readonly frame: Frame, cell: number) {
    const w = 1.0, h = 2.15;
    doorSurroundNoBack(kit, frame, cell, w, h);
    const [hx, hz] = localToWorld(frame, -w / 2, 0);
    this.pivot.position.set(hx, 0, hz);
    this.pivot.rotation.y = dirToYaw(frame.dir);
    this.panel = addBox(this.pivot, w / 2, 0, 0, w, h, 0.05, this.material);
    addBox(this.pivot, w - 0.12, 1.0, -0.05, 0.1, 0.03, 0.04, lambert(PALETTE.steel));
    const sign = makeSign(['SUPPLIES'], 0.4, 0.12, { bg: '#2b2b2e', fg: '#e8e6df' });
    sign.position.set(w / 2, 1.75, -0.03);
    sign.rotation.y = Math.PI;
    this.pivot.add(sign);
    // Handle on the corridor side (local -Z under dirToYaw).
    const [cx, cz] = localToWorld(frame, 0, 0);
    const alongX = frame.dir === 0 || frame.dir === 2;
    this.collider = kit.colliders.add({
      minX: cx - (alongX ? w / 2 : 0.08), maxX: cx + (alongX ? w / 2 : 0.08),
      minY: 0, maxY: h,
      minZ: cz - (alongX ? 0.08 : w / 2), maxZ: cz + (alongX ? 0.08 : w / 2),
      kind: 'door', blocksSight: true,
    });
  }

  toggle(): void {
    this.open = !this.open;
    this.collider.enabled = !this.open;
  }

  update(dt: number): void {
    // Swings away from the corridor (into the closet), i.e. toward the frame's back.
    const target = this.open ? -Math.PI / 2 * 0.92 : 0;
    this.angle += (target - this.angle) * Math.min(1, dt * 6);
    this.panel.parent!.rotation.y = dirToYaw(this.frame.dir) + this.angle;
  }
}

function doorSurroundNoBack(kit: KitBuilder, f: Frame, cell: number, openingW: number, openingH: number): void {
  const side = (cell - openingW) / 2;
  kit.lbox(f, -(openingW / 2 + side / 2), 0, 0, side, TUNING.wallHeight, cell, PALETTE.wall, { collide: 'wall' });
  kit.lbox(f, openingW / 2 + side / 2, 0, 0, side, TUNING.wallHeight, cell, PALETTE.wall, { collide: 'wall' });
  kit.lbox(f, 0, openingH, 0, openingW, TUNING.wallHeight - openingH, cell, PALETTE.wall);
}

/** Stairwell door or glass exit door set into a wall cell. Purely visual; X cells are the trigger. */
export function exitDoor(kit: KitBuilder, f: Frame, cell: number, variant: 'stairwell' | 'outside-door'): THREE.Group {
  const group = new THREE.Group();
  const w = variant === 'outside-door' ? 1.3 : 1.0, h = 2.2, face = cell / 2;
  doorSurround(kit, f, cell, w, h);
  const [fx, fz] = localToWorld(f, 0, 0);
  group.position.set(fx, 0, fz);
  group.rotation.y = standaloneYaw(f.dir);
  if (variant === 'stairwell') {
    addBox(group, 0, 0, face - 0.1, w, h, 0.05, lambert(0x7d8a8f));
    addBox(group, 0.18, 1.2, face - 0.07, 0.25, 0.5, 0.01, new THREE.MeshBasicMaterial({ color: 0xd8e4e0 }));
    addBox(group, 0, 0.95, face - 0.05, w * 0.8, 0.05, 0.05, lambert(PALETTE.steel));
    const sign = makeSign(['STAIRS', 'UP 7  /  DOWN 5'], 0.7, 0.3, { bg: '#1f3b2c', fg: '#e8f0e4', glow: true });
    sign.position.set(0, 2.45, face + 0.02);
    group.add(sign);
  } else {
    const glass = new THREE.MeshLambertMaterial({ color: 0xcfe6e6, transparent: true, opacity: 0.35 });
    // Daylight behind the glass. It's been a while.
    addBox(group, 0, 0, -0.14, w, h, 0.02, new THREE.MeshBasicMaterial({ color: 0xfff6dc }));
    addBox(group, -w / 4, 0, face - 0.1, w / 2 - 0.02, h, 0.04, glass);
    addBox(group, w / 4, 0, face - 0.1, w / 2 - 0.02, h, 0.04, glass);
    addBox(group, -0.08, 1.0, face - 0.06, 0.03, 0.4, 0.04, lambert(PALETTE.steel));
    addBox(group, 0.08, 1.0, face - 0.06, 0.03, 0.4, 0.04, lambert(PALETTE.steel));
  }
  const exit = makeSign(['EXIT'], 0.5, 0.18, { bg: '#1a1a1a', fg: '#3fbf6a', glow: true, font: 'bold 120px Helvetica, Arial, sans-serif' });
  exit.position.set(0, 2.75, face + 0.02);
  group.add(exit);
  return group;
}

// ---------------------------------------------------------------------------
// Hyderabad-office props (chapters 2+)
// ---------------------------------------------------------------------------

export interface Vending { group: THREE.Group; materials: THREE.MeshLambertMaterial[]; x: number; z: number; }

/** Tea/coffee premix machine. Tall enough to hide behind standing; tastes the same whichever button you press. */
export function vendingMachine(kit: KitBuilder, f: Frame): Vending {
  const group = new THREE.Group();
  const [wx, wz] = localToWorld(f, 0, -0.25);
  group.position.set(wx, 0, wz);
  group.rotation.y = standaloneYaw(f.dir);
  const body = lambert(0x8a2323);
  addBox(group, 0, 0, 0, 0.85, 1.8, 0.65, body);
  addBox(group, 0, 1.0, 0.33, 0.7, 0.55, 0.02, new THREE.MeshBasicMaterial({ color: 0xd9c9a8 }));
  const label = makeSign(['TEA · COFFEE · SOUP', '(all same taste)'], 0.66, 0.2, { bg: '#f4e2c0', fg: '#6b1d1d', font: 'bold 44px Helvetica, Arial, sans-serif' });
  label.position.set(0, 1.62, 0.335);
  group.add(label);
  for (let i = 0; i < 4; i++) addBox(group, -0.2 + i * 0.13, 0.82, 0.34, 0.08, 0.06, 0.02, new THREE.MeshBasicMaterial({ color: [0x7ee07e, 0xffd166, 0xef476f, 0x8ecae6][i] }));
  addBox(group, 0, 0.35, 0.33, 0.24, 0.25, 0.04, lambert(0x1c1c1c));
  addBox(group, 0, 0.36, 0.34, 0.06, 0.08, 0.06, lambert(0xf2efe6));
  kit.colliders.add(boxCollider(f, 0, 0, -0.25, 0.9, 1.8, 0.7, 'prop', true));
  return { group, materials: [body], x: wx, z: wz };
}

/** Fabric acoustic panels on the floor-facing sides of a wall cell. */
export function acousticWall(kit: KitBuilder, cx: number, cz: number, cell: number, faces: Dir[]): void {
  kit.box(cx, 0, cz, cell, TUNING.wallHeight, cell, PALETTE.wall, { collide: 'wall' });
  const colors = [0x3f6b6b, 0x6b6b8a, 0x8a6b4a];
  for (const dir of faces) {
    const f: Frame = { cx, cz, dir };
    for (let i = 0; i < 2; i++) {
      kit.lbox(f, -0.35 + i * 0.7, 0.6 + (i % 2) * 0.35, cell / 2 + 0.02, 0.6, 1.1, 0.05, colors[(i + dir) % colors.length], { noAO: true });
    }
  }
}

/** Reception desk with a small Ganesha idol and the visitor register nobody reads. */
export function receptionDesk(kit: KitBuilder, f: Frame): void {
  kit.lbox(f, 0, 0, -0.1, 1.45, 1.05, 0.7, 0x6f5238, { collide: 'prop', sight: true });
  kit.lbox(f, 0, 1.05, -0.1, 1.5, 0.04, 0.75, 0xd9cfbb);
  kit.lbox(f, 0, 0.2, 0.26, 1.45, 0.08, 0.02, 0xd4af37, { noAO: true });
  // Idol on a small pedestal with a marigold ring: every Indian reception desk, ever.
  kit.lbox(f, -0.5, 1.09, -0.25, 0.16, 0.06, 0.16, 0x8a2323);
  kit.lcyl(f, -0.5, 1.15, -0.25, 0.045, 0.065, 0.11, 0xe8a33d, {}, 8);
  kit.lcyl(f, -0.5, 1.26, -0.25, 0.04, 0.045, 0.06, 0xe8a33d, {}, 8);
  kit.lcyl(f, -0.5, 1.12, -0.25, 0.09, 0.09, 0.025, 0xf59e0b, {}, 10);
  kit.lbox(f, 0.25, 1.09, -0.05, 0.36, 0.03, 0.26, 0x1f3b73);
  kit.lbox(f, 0.25, 1.12, -0.05, 0.34, 0.005, 0.24, PALETTE.paper);
}

/** Temporary stage for the client visit: carpet, fairy lights, a lonely mic. */
export function stage(kit: KitBuilder, cx: number, cz: number, cell: number, rand: () => number): void {
  kit.box(cx, 0, cz, cell, 0.35, cell, 0x7a1c2a, { collide: 'prop', sight: false });
  if (rand() < 0.25) {
    kit.cyl(cx, 0.35, cz, 0.012, 0.012, 1.25, PALETTE.dark, {}, 6);
    kit.cyl(cx, 1.6, cz, 0.025, 0.018, 0.08, PALETTE.dark, {}, 8);
  }
  for (let i = 0; i < 4; i++) kit.box(cx - cell / 2 + 0.2 + i * 0.37, 0.3, cz + cell / 2 - 0.02, 0.05, 0.05, 0.02, [0xffd166, 0xef476f, 0x7ee07e, 0x8ecae6][i], { layer: 'glow', noAO: true });
}

/** Server rack: tall, dark, blinking, and loud enough that nobody hears you next to it. */
export function serverRack(kit: KitBuilder, f: Frame, rand: () => number): void {
  kit.lbox(f, 0, 0, -0.1, 0.75, 2.1, 0.9, 0x202226, { collide: 'prop', sight: true });
  for (let i = 0; i < 9; i++) {
    kit.lbox(f, 0, 0.2 + i * 0.2, 0.36, 0.62, 0.012, 0.01, 0x3a3d44, { noAO: true });
    kit.lbox(f, -0.24 + rand() * 0.05, 0.25 + i * 0.2, 0.362, 0.02, 0.02, 0.01, rand() < 0.8 ? 0x5cff8a : 0xffb347, { layer: 'glow', noAO: true });
  }
}

export function beanBag(kit: KitBuilder, cx: number, cz: number, color: number): void {
  kit.cyl(cx, 0, cz, 0.28, 0.42, 0.48, color, { collide: 'prop', sight: false }, 10);
}

/** A cinema seat row: two seats whose backs are just tall enough to hide a crouching adult. */
export function theatreSeats(kit: KitBuilder, f: Frame): void {
  for (const lx of [-0.37, 0.37]) {
    kit.lbox(f, lx, 0, 0, 0.6, 0.45, 0.55, 0x2b2b30);
    kit.lbox(f, lx, 0.45, -0.02, 0.58, 0.1, 0.5, 0x8a1e2b);
    kit.lbox(f, lx, 0.45, 0.26, 0.58, 0.6, 0.1, 0x8a1e2b);
  }
  // Seat backs face the rear of the hall; the collider covers the row up to 1.05 m.
  kit.colliders.add(boxCollider(f, 0, 0, 0.05, 1.5, 1.05, 0.65, 'prop', true));
}

/** Canteen counter with steel vessels and today's menu. */
export function foodCounter(kit: KitBuilder, f: Frame): void {
  kit.lbox(f, 0, 0, -0.05, 1.5, 0.95, 0.75, 0x9aa0a6, { collide: 'prop', sight: false });
  kit.lbox(f, 0, 0.95, -0.05, 1.52, 0.04, 0.78, 0xc9ccd0);
  for (let i = 0; i < 3; i++) {
    kit.lcyl(f, -0.45 + i * 0.45, 0.99, -0.15, 0.17, 0.15, 0.22, 0xc9ccd0, {}, 12);
    kit.lcyl(f, -0.45 + i * 0.45, 1.21, -0.15, 0.17, 0.17, 0.02, [0xe0a030, 0xf2efe6, 0xc0392b][i], { noAO: true }, 12);
  }
}

/** Cinema screen on a wall face (the face toward open floor). */
export function cinemaScreen(kit: KitBuilder, cx: number, cz: number, cell: number, dir: Dir): void {
  kit.box(cx, 0, cz, cell, TUNING.wallHeight, cell, 0x1a1a1e, { collide: 'wall' });
  kit.lbox({ cx, cz, dir }, 0, 0.6, cell / 2 + 0.02, cell + 0.02, 2.2, 0.02, 0xcfe0f0, { layer: 'glow', noAO: true });
}

/** Fire exit: red door, big warning, and an alarm you will absolutely trigger. */
export function fireExitDoor(kit: KitBuilder, f: Frame, cell: number): THREE.Group {
  const group = new THREE.Group();
  const w = 1.0, h = 2.2, face = cell / 2;
  doorSurround(kit, f, cell, w, h);
  const [fx, fz] = localToWorld(f, 0, 0);
  group.position.set(fx, 0, fz);
  group.rotation.y = standaloneYaw(f.dir);
  addBox(group, 0, 0, face - 0.1, w, h, 0.05, lambert(0xa8231f));
  addBox(group, 0, 0.95, face - 0.05, w * 0.85, 0.06, 0.06, lambert(0xd9d9d9));
  const warn = makeSign(['FIRE EXIT', 'ALARM WILL SOUND'], 0.7, 0.3, { bg: '#f4f1e8', fg: '#a8231f' });
  warn.position.set(0, 1.5, face - 0.07);
  group.add(warn);
  const exit = makeSign(['EXIT'], 0.5, 0.18, { bg: '#1a1a1a', fg: '#3fbf6a', glow: true, font: 'bold 120px Helvetica, Arial, sans-serif' });
  exit.position.set(0, 2.75, face + 0.02);
  group.add(exit);
  return group;
}
