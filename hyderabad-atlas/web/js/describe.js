// Human-readable one-liners for any item, plus "nearest metro" lookups.
import { km, pointOf, bearing, compassOf, COMPASS_LONG } from './data.js';

export function nearestStation(A, it) {
  const p = pointOf(it);
  let best = null, bd = Infinity;
  for (const s of A.stations) { const d = km(p, [s.lng, s.lat]); if (d < bd) { bd = d; best = s; } }
  return best ? { station: best, km: bd } : null;
}

export function nearbyLandmarks(A, it, maxKm = 2.5) {
  const p = pointOf(it);
  return A.landmarks.filter((m) => m.id !== it.id && km(p, [m.lng, m.lat]) <= maxKm)
    .sort((a, b) => km(p, [a.lng, a.lat]) - km(p, [b.lng, b.lat]));
}

const CHARMINAR = [78.4746, 17.3616];

export function describe(A, it) {
  if (it.fact) return it.fact;
  const p = pointOf(it);
  const d = km(CHARMINAR, p);
  const dir = COMPASS_LONG[compassOf(bearing(CHARMINAR, p))];
  switch (it.kind) {
    case 'locality': {
      const c = A.get(it.circle), z = A.get(it.zone), corp = A.get(it.corp);
      const ns = nearestStation(A, it);
      const metro = ns && ns.km < 2 ? ` Nearest metro: ${ns.station.name} (${ns.km.toFixed(1)} km).` : '';
      return `In ${c.name} circle, ${z.name} zone (${corp.name}). About ${d.toFixed(0)} km ${dir} of Charminar.${metro}`;
    }
    case 'station': {
      const lines = it.lines.map((l) => A.get(l).name);
      const line = A.get(it.lines[0]);
      const i = line.stationIds.indexOf(it.id);
      const pos = i === 0 || i === line.stationIds.length - 1 ? 'Terminus' : `Stop ${i + 1} of ${line.stationIds.length}`;
      return lines.length > 1 ? `Interchange between the ${lines.join(' and ')}.` : `${pos} on the ${lines[0]}. ${it.circle ? `In ${A.get(it.circle).name} circle.` : ''}`;
    }
    case 'line': {
      const a = A.get(it.stationIds[0]).name, b = A.get(it.stationIds.at(-1)).name;
      return `${it.stationIds.length} stations from ${a} to ${b}.`;
    }
    case 'corridor':
      return it.loop ? `${it.desc}. Exits run clockwise from Exit 1 at ${it.stops[0].name}.`
        : `${it.desc}: ${it.stops.map((s) => s.name).join(' → ')}.`;
    case 'exit': {
      const ex = A.exits, i = ex.indexOf(it);
      const nb = [ex[i - 1], ex[i + 1]].filter(Boolean).map((e) => `Exit ${e.ref} ${e.short}`);
      return `Exit ${it.ref} on the Outer Ring Road, about ${d.toFixed(0)} km ${dir} of Charminar. Between ${nb.join(' and ')}.`;
    }
    case 'rail':
      return `MMTS suburban rail line, ${it.name.replace('MMTS ', '')}.`;
    default:
      return '';
  }
}
