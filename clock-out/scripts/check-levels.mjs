// Validates level ASCII: row widths, NPC/patrol cells walkable, exit reachable from spawn.
import { level1 } from '../src/data/levels/level1_lunch.ts';
import { level2 } from '../src/data/levels/level2_crush.ts';
import { level3 } from '../src/data/levels/level3_casserole.ts';
const WALK = new Set(['.', 'P', 'X']);
let bad = 0;
for (const L of [level1, level2, level3]) {
  const [w, h] = L.gridSize;
  const errs = [];
  if (L.ascii.length !== h) errs.push(`rows ${L.ascii.length} != ${h}`);
  L.ascii.forEach((r, i) => { if (r.length !== w) errs.push(`row ${i} len ${r.length} != ${w}: ${r}`); });
  const at = (c, r) => (L.ascii[r] ?? '')[c] ?? '#';
  const walk = (c, r) => WALK.has(at(c, r));
  for (const n of L.npcs) {
    if (!walk(...n.cell)) errs.push(`${n.def} cell ${n.cell} is '${at(...n.cell)}'`);
    for (const p of n.patrol ?? []) if (!walk(...p)) errs.push(`${n.def} patrol ${p} is '${at(...p)}'`);
  }
  for (const b of L.scriptedBeats) for (const p of b.payload?.patrol ?? []) if (!walk(...p)) errs.push(`beat patrol ${p} '${at(...p)}'`);
  let start; const exits = [];
  L.ascii.forEach((r, z) => [...r].forEach((ch, x) => { if (ch === 'P') start = [x, z]; if (ch === 'X') exits.push([x, z]); }));
  const seen = new Set([start.join()]); const q = [start];
  while (q.length) { const [x, z] = q.shift(); for (const [dx, dz] of [[1,0],[-1,0],[0,1],[0,-1]]) { const k = [x+dx, z+dz]; if (walk(...k) && !seen.has(k.join())) { seen.add(k.join()); q.push(k); } } }
  if (!exits.some(e => seen.has(e.join()))) errs.push('exit unreachable');
  for (const n of L.npcs) if (!seen.has(n.cell.join())) errs.push(`${n.def} not connected`);
  console.log(L.id, errs.length ? errs : 'OK');
  bad += errs.length;
}
process.exit(bad ? 1 : 0);
