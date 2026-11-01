// Gen Z column: a data-driven "vibe" profile per circle (from OpenStreetMap
// hangout places) plus quick polls. Votes are saved on the phone for now; a
// shared backend can aggregate them later (CONFIG.community).

export const CAT_ICON = { cafe: '☕', food: '🍔', night: '🌙', fun: '🎬', fit: '🏋️', campus: '🎓', green: '🌳' };
export const VIBE_STOPS = [[0, '#1b1f3a'], [30, '#4c1d95'], [55, '#a21caf'], [75, '#ec4899'], [90, '#f97316'], [100, '#fde047']];

const POLL_KEY = 'hyd-polls-v1';

export function polls(circle) {
  const v = circle.vibe;
  const spots = [...(v.spots.cafe || []), ...(v.spots.fun || []), ...(v.spots.green || []), ...(v.spots.food || [])]
    .map((s) => s.name).filter((n, i, a) => a.indexOf(n) === i).slice(0, 4);
  const out = [
    { id: 'vibe', q: `${circle.name} vibe enti?`, options: ['Chill', 'Buzzing', 'Party', 'Study mode'] },
    { id: 'traffic', q: `Traffic in ${circle.name}?`, options: ['Smooth', 'Okay-ish', 'Pain', 'Nightmare'] },
    { id: 'time', q: 'Best time to hang out here?', options: ['Morning chai', 'Lunch', 'Evening', 'Late night'] },
  ];
  if (spots.length >= 3) out.unshift({ id: 'spot', q: `Best hangout in ${circle.name}?`, options: spots });
  return out;
}

export function myVotes() {
  try { return JSON.parse(localStorage.getItem(POLL_KEY) || '{}'); } catch { return {}; }
}

export function vote(circleId, pollId, option) {
  const v = myVotes();
  v[`${circleId}:${pollId}`] = { option, t: Date.now() };
  try { localStorage.setItem(POLL_KEY, JSON.stringify(v)); } catch { /* storage full */ }
}

// City-wide leaderboards for the Gen Z screen.
export function rankings(A) {
  const by = (k) => A.circles.slice().sort((a, b) => b.vibe.counts[k] - a.vibe.counts[k]).filter((c) => c.vibe.counts[k] > 0).slice(0, 5);
  return [
    { id: 'score', title: 'Top hangout circles', icon: '🔥', list: A.circles.slice().sort((a, b) => b.vibe.score - a.vibe.score).slice(0, 5), val: (c) => `${c.vibe.score}` },
    { id: 'cafe', title: 'Café capitals', icon: CAT_ICON.cafe, list: by('cafe'), val: (c) => `${c.vibe.counts.cafe} cafés` },
    { id: 'night', title: 'Night-out spots', icon: CAT_ICON.night, list: by('night'), val: (c) => `${c.vibe.counts.night} places` },
    { id: 'fun', title: 'Malls & movies', icon: CAT_ICON.fun, list: by('fun'), val: (c) => `${c.vibe.counts.fun} places` },
    { id: 'campus', title: 'Campus zones', icon: CAT_ICON.campus, list: by('campus'), val: (c) => `${c.vibe.counts.campus} colleges` },
    { id: 'green', title: 'Green & chill', icon: CAT_ICON.green, list: by('green'), val: (c) => `${c.vibe.counts.green} parks` },
  ];
}

// One-line "hot take" built from the circle's ranks.
export function hotTake(A, c) {
  const v = c.vibe;
  const best = Object.entries(v.rank).filter(([k]) => v.counts[k] >= 3).sort((a, b) => a[1] - b[1])[0];
  const names = A.vibeCats;
  if (!best) return `${c.name} keeps it low-key: more homes than hangouts.`;
  const [k, r] = best;
  if (r === 1) return `${c.name} is Hyderabad's #1 circle for ${names[k].toLowerCase()}.`;
  if (r <= 5) return `${c.name} is top ${r} of 60 circles for ${names[k].toLowerCase()}.`;
  return `${c.name}'s strongest suit: ${names[k].toLowerCase()} (#${r} of 60).`;
}
