// Validates level ASCII: row widths, NPC/patrol cells walkable, exit reachable from spawn.
import fs from 'node:fs';
const dir = new URL('../src/data/levels/', import.meta.url);
const levels = [];
for (const f of fs.readdirSync(dir).filter((f) => f.endsWith('.ts') && f !== 'index.ts').sort()) {
  const mod = await import(new URL(f, dir));
  levels.push(...Object.values(mod));
}
const WALK = new Set(['.', 'P', 'X']);
let bad = 0;
for (const L of levels) {
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
  const off = [[0, -1], [1, 0], [0, 1], [-1, 0]];
  L.ascii.forEach((r, z) => [...r].forEach((ch, x) => {
    if (ch === 'P') start = [x, z];
    if (ch === 'X') exits.push([x, z]);
    // A fire exit's trigger is the floor cell in front of it.
    if (ch === 'Q') for (const [dx, dz] of off) if (walk(x + dx, z + dz)) exits.push([x + dx, z + dz]);
  }));
  const seen = new Set([start.join()]); const q = [start];
  while (q.length) { const [x, z] = q.shift(); for (const [dx, dz] of [[1,0],[-1,0],[0,1],[0,-1]]) { const k = [x+dx, z+dz]; if (walk(...k) && !seen.has(k.join())) { seen.add(k.join()); q.push(k); } } }
  if (!exits.some(e => seen.has(e.join()))) errs.push('exit unreachable');
  for (const n of L.npcs) if (!seen.has(n.cell.join())) errs.push(`${n.def} not connected`);
  for (const o of L.objectives ?? []) if (!seen.has(o.cell.join())) errs.push(`objective ${o.cell} unreachable ('${at(...o.cell)}')`);
  for (const b of L.scriptedBeats) if (b.payload?.cell && !walk(...b.payload.cell)) errs.push(`beat ${b.type} cell ${b.payload.cell} is '${at(...b.payload.cell)}'`);
  if (!Number.isInteger(L.chapter)) errs.push('missing chapter');
  console.log(L.id, errs.length ? errs : 'OK');
  bad += errs.length;
}
process.exit(bad ? 1 : 0);
