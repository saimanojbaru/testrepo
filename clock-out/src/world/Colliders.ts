// Axis-aligned boxes for everything solid. The player is a vertical capsule, which
// on a flat office floor reduces to a circle in XZ tested against boxes that
// overlap its height span. Sight lines are 3D segment-vs-box slab tests, which is
// what makes 1.5 m cubicle dividers hide a crouching player but not a standing one.

export type ColliderKind = 'wall' | 'prop' | 'glass' | 'divider' | 'door' | 'npc';

export interface AABB {
  minX: number; minY: number; minZ: number;
  maxX: number; maxY: number; maxZ: number;
  kind: ColliderKind;
  blocksSight: boolean;
  enabled: boolean;
}

export interface CircleHit {
  x: number;
  z: number;
  hit: AABB | null;
}

const TUNING = {
  resolveIterations: 3,
  /** Skin keeps the camera from resting exactly on a surface and z-fighting it. */
  skin: 0.001,
};

export class ColliderWorld {
  readonly list: AABB[] = [];

  add(box: Omit<AABB, 'enabled'> & { enabled?: boolean }): AABB {
    const full: AABB = { enabled: true, ...box };
    this.list.push(full);
    return full;
  }

  /** Push a circle (x,z,r) spanning heights [y0,y1] out of every box it overlaps. */
  resolveCircle(x: number, z: number, r: number, y0: number, y1: number): CircleHit {
    let hit: AABB | null = null;
    for (let iter = 0; iter < TUNING.resolveIterations; iter++) {
      let moved = false;
      for (const b of this.list) {
        if (!b.enabled || b.maxY <= y0 || b.minY >= y1) continue;
        const cx = Math.max(b.minX, Math.min(x, b.maxX));
        const cz = Math.max(b.minZ, Math.min(z, b.maxZ));
        const dx = x - cx;
        const dz = z - cz;
        const d2 = dx * dx + dz * dz;
        if (d2 >= r * r) continue;
        if (d2 > 1e-10) {
          const d = Math.sqrt(d2);
          const push = r - d + TUNING.skin;
          x += (dx / d) * push;
          z += (dz / d) * push;
        } else {
          // Centre is inside the box: leave along the axis of least penetration.
          const left = x - b.minX + r, right = b.maxX - x + r;
          const back = z - b.minZ + r, front = b.maxZ - z + r;
          const m = Math.min(left, right, back, front);
          if (m === left) x = b.minX - r;
          else if (m === right) x = b.maxX + r;
          else if (m === back) z = b.minZ - r;
          else z = b.maxZ + r;
        }
        hit = b;
        moved = true;
      }
      if (!moved) break;
    }
    return { x, z, hit };
  }

  /** True if any sight-blocking box intersects the segment a->b. */
  segmentBlocked(ax: number, ay: number, az: number, bx: number, by: number, bz: number, ignore?: AABB): boolean {
    const dx = bx - ax, dy = by - ay, dz = bz - az;
    for (const b of this.list) {
      if (!b.enabled || !b.blocksSight || b === ignore) continue;
      if (segmentHitsBox(ax, ay, az, dx, dy, dz, b)) return true;
    }
    return false;
  }
}

function segmentHitsBox(ax: number, ay: number, az: number, dx: number, dy: number, dz: number, b: AABB): boolean {
  let tmin = 0;
  let tmax = 1;
  const axes: Array<[number, number, number, number]> = [
    [ax, dx, b.minX, b.maxX],
    [ay, dy, b.minY, b.maxY],
    [az, dz, b.minZ, b.maxZ],
  ];
  for (const [o, d, lo, hi] of axes) {
    if (Math.abs(d) < 1e-9) {
      if (o < lo || o > hi) return false;
      continue;
    }
    let t1 = (lo - o) / d;
    let t2 = (hi - o) / d;
    if (t1 > t2) [t1, t2] = [t2, t1];
    tmin = Math.max(tmin, t1);
    tmax = Math.min(tmax, t2);
    if (tmin > tmax) return false;
  }
  return true;
}
