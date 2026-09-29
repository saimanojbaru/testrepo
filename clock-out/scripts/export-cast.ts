// Generates tools/cast.json from src/data/cast.ts: the finished head and body specs the
// Python exporters (tools/gnm, tools/anny) build from, so they carry no cast logic of their own.
//   npm run cast:export     write tools/cast.json
//   npm run check:cast      fail if tools/cast.json is stale (run before building assets)
//   npm run cast:defaults   list every field still at its default, per character
import { readFileSync, writeFileSync } from 'node:fs';
import { CAST, bodySpec, defaultedFields, headSpec } from '../src/data/cast.ts';

const OUT = new URL('../tools/cast.json', import.meta.url);
const mode = process.argv[2];

if (mode === '--defaults') {
  for (const m of Object.values(CAST)) {
    console.log(`${m.id.padEnd(9)} chosen: ${m.chosen.join(', ')}`);
    console.log(`${''.padEnd(9)} default: ${defaultedFields(m).join(', ')}`);
  }
  process.exit(0);
}

const doc = {
  note: 'GENERATED from src/data/cast.ts by `npm run cast:export`. Do not edit by hand.',
  members: Object.values(CAST).map((m) => ({ id: m.id, head: headSpec(m), body: bodySpec(m) })),
};
const text = JSON.stringify(doc, null, 1) + '\n';

if (mode === '--check') {
  let current = '';
  try { current = readFileSync(OUT, 'utf8'); } catch { /* missing counts as stale */ }
  if (current !== text) {
    console.error('tools/cast.json is out of date with src/data/cast.ts. Run `npm run cast:export`.');
    process.exit(1);
  }
  console.log('tools/cast.json is up to date.');
} else {
  writeFileSync(OUT, text);
  console.log(`wrote tools/cast.json (${doc.members.length} members)`);
}
