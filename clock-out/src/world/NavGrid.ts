// Grid A* over walkable ASCII cells. 8-directional, no corner cutting, then a
// greedy string-pull so NPCs walk in straight lines across open floor instead of
// zig-zagging along cell centres.

export interface Vec2 { x: number; z: number; }

const TUNING = {
  diagonalCost: Math.SQRT2,
  /** Sample spacing (in cells) for the straight-line walkability test. */
  lineStep: 0.2,
  /** Clearance (in cells) checked either side of a smoothed segment. */
  clearance: 0.3,
  maxIterations: 20000,
};

export class NavGrid {
  readonly walkable: Uint8Array;

  constructor(readonly width: number, readonly height: number, readonly cellSize: number, isWalkable: (c: number, r: number) => boolean) {
    this.walkable = new Uint8Array(width * height);
    for (let r = 0; r < height; r++) for (let c = 0; c < width; c++) this.walkable[r * width + c] = isWalkable(c, r) ? 1 : 0;
  }

  isWalkable(c: number, r: number): boolean {
    return c >= 0 && r >= 0 && c < this.width && r < this.height && this.walkable[r * this.width + c] === 1;
  }

  cellCenter(c: number, r: number): Vec2 {
    return { x: (c + 0.5) * this.cellSize, z: (r + 0.5) * this.cellSize };
  }

  worldToCell(x: number, z: number): [number, number] {
    return [Math.floor(x / this.cellSize), Math.floor(z / this.cellSize)];
  }

  /** Nearest walkable cell by expanding rings; used when a target lands on furniture. */
  nearestWalkable(c: number, r: number): [number, number] {
    if (this.isWalkable(c, r)) return [c, r];
    for (let rad = 1; rad < Math.max(this.width, this.height); rad++) {
      let best: [number, number] | null = null;
      let bestD = Infinity;
      for (let dr = -rad; dr <= rad; dr++) {
        for (let dc = -rad; dc <= rad; dc++) {
          if (Math.max(Math.abs(dc), Math.abs(dr)) !== rad) continue;
          if (!this.isWalkable(c + dc, r + dr)) continue;
          const d = dc * dc + dr * dr;
          if (d < bestD) { bestD = d; best = [c + dc, r + dr]; }
        }
      }
      if (best) return best;
    }
    return [c, r];
  }

  findPath(from: Vec2, to: Vec2): Vec2[] {
    const s = this.nearestWalkable(...this.worldToCell(from.x, from.z));
    const g = this.nearestWalkable(...this.worldToCell(to.x, to.z));
    const cells = this.astar(s, g);
    if (!cells) return [];
    const pts = cells.map(([c, r]) => this.cellCenter(c, r));
    // End exactly on the requested point when it is itself walkable (sub-cell precision).
    const [tc, tr] = this.worldToCell(to.x, to.z);
    if (this.isWalkable(tc, tr) && pts.length) pts[pts.length - 1] = { x: to.x, z: to.z };
    return this.smooth(from, pts);
  }

  private astar(start: [number, number], goal: [number, number]): Array<[number, number]> | null {
    const W = this.width;
    const N = W * this.height;
    const gScore = new Float32Array(N).fill(Infinity);
    const came = new Int32Array(N).fill(-1);
    const closed = new Uint8Array(N);
    const heap = new MinHeap();
    const si = start[1] * W + start[0];
    const gi = goal[1] * W + goal[0];
    gScore[si] = 0;
    heap.push(si, this.h(start[0], start[1], goal));
    let iter = 0;
    while (heap.size && iter++ < TUNING.maxIterations) {
      const cur = heap.pop();
      if (cur === gi) break;
      if (closed[cur]) continue;
      closed[cur] = 1;
      const cc = cur % W, cr = (cur / W) | 0;
      for (let dr = -1; dr <= 1; dr++) {
        for (let dc = -1; dc <= 1; dc++) {
          if (!dc && !dr) continue;
          const nc = cc + dc, nr = cr + dr;
          if (!this.isWalkable(nc, nr)) continue;
          // No corner cutting: a diagonal needs both orthogonal neighbours open.
          if (dc && dr && (!this.isWalkable(cc + dc, cr) || !this.isWalkable(cc, cr + dr))) continue;
          const ni = nr * W + nc;
          const ng = gScore[cur] + (dc && dr ? TUNING.diagonalCost : 1);
          if (ng < gScore[ni]) {
            gScore[ni] = ng;
            came[ni] = cur;
            heap.push(ni, ng + this.h(nc, nr, goal));
          }
        }
      }
    }
    if (si !== gi && came[gi] < 0) return null;
    const out: Array<[number, number]> = [];
    for (let i = gi; i >= 0; i = i === si ? -1 : came[i]) out.push([i % W, (i / W) | 0]);
    return out.reverse();
  }

  private h(c: number, r: number, goal: [number, number]): number {
    const dx = Math.abs(c - goal[0]), dy = Math.abs(r - goal[1]);
    return Math.max(dx, dy) + (TUNING.diagonalCost - 1) * Math.min(dx, dy);
  }

  /** Greedy string-pulling: skip every waypoint that a clear straight line can bypass. */
  private smooth(from: Vec2, pts: Vec2[]): Vec2[] {
    // Always smooth, even short paths: the first point is the centre of the mover's own
    // cell, and walking back to it on every repath makes chasers jitter in place.
    if (!pts.length) return pts;
    const out: Vec2[] = [];
    let anchor = from;
    let i = 0;
    while (i < pts.length) {
      let j = pts.length - 1;
      while (j > i && !this.lineWalkable(anchor, pts[j])) j--;
      out.push(pts[j]);
      anchor = pts[j];
      i = j + 1;
    }
    return out;
  }

  lineWalkable(a: Vec2, b: Vec2): boolean {
    const cs = this.cellSize;
    const dx = b.x - a.x, dz = b.z - a.z;
    const len = Math.hypot(dx, dz) / cs;
    const steps = Math.max(1, Math.ceil(len / TUNING.lineStep));
    const px = len > 0 ? (-dz / (len * cs)) * TUNING.clearance * cs : 0;
    const pz = len > 0 ? (dx / (len * cs)) * TUNING.clearance * cs : 0;
    for (let s = 0; s <= steps; s++) {
      const t = s / steps;
      const x = a.x + dx * t, z = a.z + dz * t;
      for (const k of [-1, 0, 1]) {
        const [c, r] = this.worldToCell(x + px * k, z + pz * k);
        if (!this.isWalkable(c, r)) return false;
      }
    }
    return true;
  }

  randomWalkable(filter?: (c: number, r: number) => boolean): [number, number] | null {
    for (let tries = 0; tries < 400; tries++) {
      const c = Math.floor(Math.random() * this.width);
      const r = Math.floor(Math.random() * this.height);
      if (this.isWalkable(c, r) && (!filter || filter(c, r))) return [c, r];
    }
    return null;
  }
}

/** Binary min-heap of (index, priority). Duplicates are allowed; stale entries are skipped via `closed`. */
class MinHeap {
  private idx: number[] = [];
  private pri: number[] = [];

  get size(): number { return this.idx.length; }

  push(i: number, p: number): void {
    this.idx.push(i);
    this.pri.push(p);
    let n = this.idx.length - 1;
    while (n > 0) {
      const parent = (n - 1) >> 1;
      if (this.pri[parent] <= this.pri[n]) break;
      this.swap(n, parent);
      n = parent;
    }
  }

  pop(): number {
    const top = this.idx[0];
    const lastI = this.idx.pop()!;
    const lastP = this.pri.pop()!;
    if (this.idx.length) {
      this.idx[0] = lastI;
      this.pri[0] = lastP;
      let n = 0;
      for (;;) {
        const l = 2 * n + 1, r = l + 1;
        let m = n;
        if (l < this.idx.length && this.pri[l] < this.pri[m]) m = l;
        if (r < this.idx.length && this.pri[r] < this.pri[m]) m = r;
        if (m === n) break;
        this.swap(n, m);
        n = m;
      }
    }
    return top;
  }

  private swap(a: number, b: number): void {
    [this.idx[a], this.idx[b]] = [this.idx[b], this.idx[a]];
    [this.pri[a], this.pri[b]] = [this.pri[b], this.pri[a]];
  }
}
