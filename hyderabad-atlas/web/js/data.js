// Loads atlas.json and builds lookups for the hierarchy:
// corporation > zone > circle > locality, plus landmarks, metro and corridors.

export const KIND_LABEL = {
  corp: 'Corporation', zone: 'Zone', circle: 'Circle', locality: 'Area',
  landmark: 'Landmark', station: 'Metro station', line: 'Metro line', corridor: 'Route',
};

const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

export async function loadAtlas() {
  const res = await fetch('data/atlas.json');
  const A = await res.json();
  const byId = new Map();
  const put = (it, kind) => { it.kind = kind; byId.set(it.id, it); return it; };

  A.corporations.forEach((c) => put(c, 'corp'));
  A.zones.forEach((z) => put(z, 'zone'));
  A.circles.forEach((c) => put(c, 'circle'));
  A.localities.forEach((l) => put(l, 'locality'));
  A.landmarks.forEach((m) => put(m, 'landmark'));

  // Stations: one entity per name, shared by lines at interchanges.
  A.stations = [];
  for (const line of A.metro) {
    put(line, 'line');
    line.stationIds = line.stations.map((s) => {
      const id = 's-' + slug(s.name);
      let st = byId.get(id);
      if (!st) {
        st = put({ id, name: s.name, lat: s.lat, lng: s.lng, lines: [] }, 'station');
        A.stations.push(st);
      }
      st.lines.push(line.id);
      return id;
    });
  }
  A.corridors.forEach((r) => put(r, 'corridor'));

  // children + colours
  const color = Object.fromEntries(A.corporations.map((c) => [c.id, c.color]));
  for (const it of byId.values()) {
    it.children = [];
    if (it.corp) it.color = color[it.corp];
  }
  A.corporations.forEach((c) => (c.color = color[c.id]));
  A.zones.forEach((z) => byId.get(z.corp).children.push(z.id));
  A.circles.forEach((c) => byId.get(c.zone).children.push(c.id));
  A.localities.forEach((l) => byId.get(l.circle).children.push(l.id));

  // nearest locality for stations, so they inherit a corporation colour
  for (const st of A.stations) {
    const home = A.localities.find((l) => pointInGeom([st.lng, st.lat], l.geom));
    if (home) { st.locality = home.id; st.circle = home.circle; st.zone = home.zone; st.corp = home.corp; }
  }

  A.byId = byId;
  A.get = (id) => byId.get(id);
  A.color = color;
  A.lists = {
    corp: A.corporations, zone: A.zones, circle: A.circles, locality: A.localities,
    landmark: A.landmarks, station: A.stations, line: A.metro, corridor: A.corridors,
  };
  A.interchanges = A.stations.filter((s) => s.lines.length > 1);
  return A;
}

// ---------------------------------------------------------------- helpers

export function pointOf(it) {
  if (it.label) return it.label;
  if (it.lng != null) return [it.lng, it.lat];
  if (it.kind === 'line') return it.line[Math.floor(it.line.length / 2)];
  if (it.kind === 'corridor') { const s = it.stops[Math.floor(it.stops.length / 2)]; return [s.lng, s.lat]; }
  return [78.47, 17.4];
}

export function crumb(A, it) {
  const out = [];
  if (it.corp && it.kind !== 'corp') out.push(A.get(it.corp).name);
  if (it.zone && it.kind !== 'zone') out.push(A.get(it.zone).name + ' zone');
  if (it.circle && it.kind !== 'circle' && it.kind !== 'locality') out.push(A.get(it.circle).name + ' circle');
  if (it.kind === 'locality') out.push(A.get(it.circle).name + ' circle');
  return out;
}

export function pointInRing(pt, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i], [xj, yj] = ring[j];
    if ((yi > pt[1]) !== (yj > pt[1]) && pt[0] < ((xj - xi) * (pt[1] - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

function polys(geom) {
  return geom.type === 'Polygon' ? [geom.coordinates] : geom.coordinates;
}

export function pointInGeom(pt, geom) {
  return polys(geom).some((p) => pointInRing(pt, p[0]) && !p.slice(1).some((h) => pointInRing(pt, h)));
}

export function km(a, b) {
  const R = 6371, rad = Math.PI / 180;
  const dLat = (b[1] - a[1]) * rad, dLng = (b[0] - a[0]) * rad;
  const s = Math.sin(dLat / 2) ** 2 + Math.cos(a[1] * rad) * Math.cos(b[1] * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(s));
}

function segDistKm(p, a, b) {
  // planar approx around Hyderabad is fine for scoring
  const kx = 111.32 * Math.cos((p[1] * Math.PI) / 180), ky = 110.57;
  const P = [p[0] * kx, p[1] * ky], A = [a[0] * kx, a[1] * ky], B = [b[0] * kx, b[1] * ky];
  const dx = B[0] - A[0], dy = B[1] - A[1];
  const t = Math.max(0, Math.min(1, ((P[0] - A[0]) * dx + (P[1] - A[1]) * dy) / (dx * dx + dy * dy || 1)));
  return Math.hypot(P[0] - (A[0] + t * dx), P[1] - (A[1] + t * dy));
}

export function distToGeomKm(pt, geom) {
  if (pointInGeom(pt, geom)) return 0;
  let best = Infinity;
  for (const p of polys(geom)) {
    const r = p[0];
    for (let i = 1; i < r.length; i++) best = Math.min(best, segDistKm(pt, r[i - 1], r[i]));
  }
  return best;
}

export function bearing(a, b) {
  const rad = Math.PI / 180;
  const y = Math.sin((b[0] - a[0]) * rad) * Math.cos(b[1] * rad);
  const x = Math.cos(a[1] * rad) * Math.sin(b[1] * rad) - Math.sin(a[1] * rad) * Math.cos(b[1] * rad) * Math.cos((b[0] - a[0]) * rad);
  return ((Math.atan2(y, x) / rad) + 360) % 360;
}

export const COMPASS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
export const COMPASS_LONG = { N: 'north', NE: 'north-east', E: 'east', SE: 'south-east', S: 'south', SW: 'south-west', W: 'west', NW: 'north-west' };
export const compassOf = (deg) => COMPASS[Math.round(deg / 45) % 8];

export function bboxOf(it) {
  let pts = [];
  if (it.geom) polys(it.geom).forEach((p) => (pts = pts.concat(p[0])));
  else if (it.kind === 'line') pts = it.line;
  else if (it.kind === 'corridor') pts = it.stops.map((s) => [s.lng, s.lat]);
  else pts = [pointOf(it)];
  let [x0, y0, x1, y1] = [180, 90, -180, -90];
  for (const [x, y] of pts) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
  return [[x0, y0], [x1, y1]];
}

export function shuffle(a) {
  a = a.slice();
  for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
  return a;
}

export const pick = (a) => a[Math.floor(Math.random() * a.length)];
