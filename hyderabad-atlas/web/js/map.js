// MapLibre scene: dark-graded satellite imagery, a spotlight mask around the
// city, raised translucent slabs for corporations/zones, glowing borders,
// the ORR as a double white line, metro lines and route schematics.
import { LabelLayer } from './labels.js';
import { pointOf, bboxOf, pointInGeom, km } from './data.js';

const SAT = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
const ROADS = 'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}';

export const DEPTHS = [
  { id: 'corp', label: 'Corporations' },
  { id: 'zone', label: 'Zones' },
  { id: 'circle', label: 'Circles' },
  { id: 'locality', label: 'Areas' },
  { id: 'landmark', label: 'Landmarks' },
  { id: 'metro', label: 'Metro' },
  { id: 'routes', label: 'Routes' },
];

const SLAB_H = { corp: 1500, zone: 800 };
const lighten = (hex, t) => {
  const n = parseInt(hex.slice(1), 16);
  const c = [n >> 16, (n >> 8) & 255, n & 255].map((v) => Math.round(v + (255 - v) * t));
  return '#' + c.map((v) => v.toString(16).padStart(2, '0')).join('');
};
const fc = (features) => ({ type: 'FeatureCollection', features });
const feat = (geometry, properties = {}, id) => ({ type: 'Feature', id, geometry, properties });
const corpColor = (fallback = '#ffffff') => ['coalesce', ['get', 'color'], fallback];

export class AtlasMap {
  constructor(el, labelsEl, A) {
    this.A = A;
    this.el = el;
    this.depth = 'corp';
    this.selected = null;
    this.tapHandler = null;
    this.selectHandler = null;
    this.pitch = 52;
    this.labelsOn = true;
    this.labelsEl = labelsEl;
  }

  init() {
    const A = this.A;
    const regionFC = (list, key = 'geom') => fc(list.filter((it) => it[key]).map((it) =>
      feat(it[key], { id: it.id, name: it.name, corp: it.corp || it.id, color: it.color, colorHi: lighten(it.color, 0.45) }, it.id)));

    // world polygon with the city cut out: dims everything outside the city
    const world = [[-180, -85], [180, -85], [180, 85], [-180, 85], [-180, -85]];
    const domainRing = A.domain.coordinates[0];
    const mask = feat({ type: 'Polygon', coordinates: [world, domainRing.slice().reverse()] });

    const metroFC = fc(A.metro.map((l) => feat({ type: 'LineString', coordinates: l.line }, { id: l.id, color: l.color }, l.id)));
    const corridorFC = fc(A.corridors.map((r) => feat({ type: 'LineString', coordinates: r.stops.map((s) => [s.lng, s.lat]) }, { id: r.id }, r.id)));

    const style = {
      version: 8,
      sky: {
        'sky-color': '#020403', 'horizon-color': '#0a1410', 'fog-color': '#040706',
        'sky-horizon-blend': 0.6, 'horizon-fog-blend': 0.7, 'fog-ground-blend': 0.35, 'atmosphere-blend': 0,
      },
      sources: {
        sat: { type: 'raster', tiles: [SAT], tileSize: 256, maxzoom: 19, attribution: 'Imagery © Esri, Maxar, Earthstar Geographics · © OpenStreetMap contributors' },
        roads: { type: 'raster', tiles: [ROADS], tileSize: 256, maxzoom: 18 },
        mask: { type: 'geojson', data: mask },
        domain: { type: 'geojson', data: feat(A.domain) },
        orr: { type: 'geojson', data: feat(A.orr) },
        corp: { type: 'geojson', data: regionFC(A.corporations), promoteId: 'id' },
        corpSlab: { type: 'geojson', data: regionFC(A.corporations, 'slab'), promoteId: 'id' },
        zone: { type: 'geojson', data: regionFC(A.zones), promoteId: 'id' },
        zoneSlab: { type: 'geojson', data: regionFC(A.zones, 'slab'), promoteId: 'id' },
        circle: { type: 'geojson', data: regionFC(A.circles), promoteId: 'id' },
        locality: { type: 'geojson', data: regionFC(A.localities), promoteId: 'id' },
        metro: { type: 'geojson', data: metroFC, promoteId: 'id' },
        corridor: { type: 'geojson', data: corridorFC, promoteId: 'id' },
        hl: { type: 'geojson', data: fc([]) },
        quiz: { type: 'geojson', data: fc([]) },
      },
      layers: [
        { id: 'bg', type: 'background', paint: { 'background-color': '#050807' } },
        { id: 'sat', type: 'raster', source: 'sat', paint: { 'raster-saturation': -0.18, 'raster-contrast': 0.12, 'raster-brightness-max': 0.86, 'raster-fade-duration': 250 } },
        { id: 'roads', type: 'raster', source: 'roads', layout: { visibility: 'none' }, paint: { 'raster-opacity': 0.75 } },
        { id: 'mask', type: 'fill', source: 'mask', paint: { 'fill-color': '#000', 'fill-opacity': 0.62 } },
        { id: 'domain-dark', type: 'fill', source: 'domain', paint: { 'fill-color': '#000', 'fill-opacity': 0, 'fill-opacity-transition': { duration: 600 } } },

        { id: 'locality-fill', type: 'fill', source: 'locality', layout: { visibility: 'none' },
          paint: { 'fill-color': corpColor(), 'fill-opacity': ['case', ['boolean', ['feature-state', 'sel'], false], 0.42, ['boolean', ['feature-state', 'done'], false], 0.2, 0.08] } },
        { id: 'circle-fill', type: 'fill', source: 'circle', layout: { visibility: 'none' },
          paint: { 'fill-color': corpColor(), 'fill-opacity': ['case', ['boolean', ['feature-state', 'sel'], false], 0.5, 0.2] } },
        { id: 'locality-line', type: 'line', source: 'locality', layout: { visibility: 'none' },
          paint: { 'line-color': '#ffffff', 'line-opacity': 0.28, 'line-width': 0.7 } },
        { id: 'circle-line', type: 'line', source: 'circle', layout: { visibility: 'none' },
          paint: { 'line-color': '#ffffff', 'line-opacity': 0.6, 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.7, 13, 1.6] } },
        { id: 'zone-line', type: 'line', source: 'zone', layout: { visibility: 'none' },
          paint: { 'line-color': '#ffffff', 'line-opacity': 0.9, 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 1.4, 13, 3] } },
        { id: 'corp-glow', type: 'line', source: 'corp',
          paint: { 'line-color': corpColor(), 'line-width': 9, 'line-blur': 8, 'line-opacity': 0.55 } },
        { id: 'corp-line', type: 'line', source: 'corp',
          paint: { 'line-color': ['get', 'colorHi'], 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 1.4, 13, 2.6], 'line-opacity': 0.95 } },

        { id: 'orr-glow', type: 'line', source: 'orr', paint: { 'line-color': '#fff', 'line-width': 7, 'line-blur': 6, 'line-opacity': 0.18 } },
        { id: 'orr', type: 'line', source: 'orr', paint: { 'line-color': '#f4f1ea', 'line-width': 1.1, 'line-gap-width': ['interpolate', ['linear'], ['zoom'], 9, 1.6, 14, 6], 'line-opacity': 0.9 } },

        { id: 'corridor-line', type: 'line', source: 'corridor', layout: { visibility: 'none', 'line-cap': 'round' },
          paint: { 'line-color': '#f2cf4a', 'line-width': ['case', ['boolean', ['feature-state', 'sel'], false], 4, 2.2], 'line-dasharray': [2, 1.5],
            'line-opacity': ['case', ['boolean', ['feature-state', 'dim'], false], 0.2, 0.9] } },
        { id: 'metro-glow', type: 'line', source: 'metro', layout: { visibility: 'none', 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': ['get', 'color'], 'line-width': 12, 'line-blur': 9, 'line-opacity': ['case', ['boolean', ['feature-state', 'dim'], false], 0.05, 0.55] } },
        { id: 'metro-line', type: 'line', source: 'metro', layout: { visibility: 'none', 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': ['get', 'color'], 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 2.5, 14, 6], 'line-opacity': ['case', ['boolean', ['feature-state', 'dim'], false], 0.18, 1] } },

        { id: 'zone-slab', type: 'fill-extrusion', source: 'zoneSlab', layout: { visibility: 'none' },
          paint: {
            'fill-extrusion-color': ['case', ['boolean', ['feature-state', 'sel'], false], ['get', 'colorHi'], ['get', 'color']],
            'fill-extrusion-height': ['+', ['*', SLAB_H.zone, ['coalesce', ['feature-state', 'rise'], 1]], ['coalesce', ['feature-state', 'lift'], 0]],
            'fill-extrusion-opacity': 0.58, 'fill-extrusion-vertical-gradient': true,
          } },
        { id: 'corp-slab', type: 'fill-extrusion', source: 'corpSlab',
          paint: {
            'fill-extrusion-color': ['case', ['boolean', ['feature-state', 'sel'], false], ['get', 'colorHi'], ['get', 'color']],
            'fill-extrusion-height': ['+', ['*', SLAB_H.corp, ['coalesce', ['feature-state', 'rise'], 1]], ['coalesce', ['feature-state', 'lift'], 0]],
            'fill-extrusion-opacity': 0.6, 'fill-extrusion-vertical-gradient': true,
          } },

        { id: 'hl-fill', type: 'fill', source: 'hl', filter: ['==', '$type', 'Polygon'], paint: { 'fill-color': ['coalesce', ['get', 'color'], '#5be49b'], 'fill-opacity': 0.28 } },
        { id: 'hl-glow', type: 'line', source: 'hl', paint: { 'line-color': ['coalesce', ['get', 'color'], '#5be49b'], 'line-width': 12, 'line-blur': 9, 'line-opacity': 0.9 } },
        { id: 'hl-line', type: 'line', source: 'hl', paint: { 'line-color': '#ffffff', 'line-width': 2.2 } },
        { id: 'hl-pt', type: 'circle', source: 'hl', filter: ['==', '$type', 'Point'],
          paint: { 'circle-radius': 16, 'circle-color': 'rgba(0,0,0,0)', 'circle-stroke-color': ['coalesce', ['get', 'color'], '#5be49b'], 'circle-stroke-width': 2.5, 'circle-blur': 0.1 } },

        { id: 'quiz-fill', type: 'fill', source: 'quiz', filter: ['==', '$type', 'Polygon'], paint: { 'fill-color': ['get', 'color'], 'fill-opacity': 0.35 } },
        { id: 'quiz-outline', type: 'line', source: 'quiz', filter: ['==', '$type', 'Polygon'], paint: { 'line-color': ['get', 'color'], 'line-width': 2.5 } },
        { id: 'quiz-line', type: 'line', source: 'quiz', filter: ['==', '$type', 'LineString'],
          paint: { 'line-color': ['get', 'color'], 'line-width': ['coalesce', ['get', 'w'], 2.5], 'line-dasharray': [1.5, 1.2] } },
      ],
    };

    const home = this.homeCamera();
    this.map = new maplibregl.Map({
      container: this.el, style, center: home.center, pitch: 20, bearing: 0, zoom: home.zoom - 0.6,
      maxPitch: 72, attributionControl: false, fadeDuration: 150,
      dragRotate: true, pitchWithRotate: true, renderWorldCopies: false,
      maxBounds: [[77.6, 16.8], [79.4, 18.0]],
    });
    this.map.touchZoomRotate.enableRotation();
    this.map.on('error', (e) => console.warn('map:', e.error && e.error.message));
    this.labels = new LabelLayer(this.map, this.labelsEl);
    this.map.on('click', (e) => this.onClick(e));
    return new Promise((resolve) => this.map.on('load', () => resolve()));
  }

  // --------------------------------------------------------------- camera
  // Screen area the map content should fit into (headline above, panels below).
  padding(extra = {}) {
    const H = this.el.clientHeight || 800;
    const base = this.viewPad || { top: 165, bottom: Math.round(H * 0.13), left: 14, right: 14 };
    return { ...base, ...extra };
  }

  homeCamera(pad = this.padding()) {
    const W = this.el.clientWidth || 390, H = this.el.clientHeight || 800;
    const [[x0, y0], [x1, y1]] = bboxOf({ geom: this.A.domain });
    const usableW = W - pad.left - pad.right, usableH = H - pad.top - pad.bottom;
    const lngSpan = x1 - x0, latSpan = (y1 - y0) / Math.cos((17.4 * Math.PI) / 180);
    const zoom = Math.log2(Math.min(usableW / lngSpan, usableH / latSpan) * 360 / 512);
    return { center: [(x0 + x1) / 2, (y0 + y1) / 2], zoom: Math.min(zoom - 0.06, 11), padding: pad };
  }

  home(opts = {}) {
    const cam = this.homeCamera(this.padding(opts.padding || {}));
    this.map.flyTo({ ...cam, pitch: this.pitch, bearing: opts.bearing ?? -10, duration: opts.duration ?? 1600, essential: true, curve: 1.2 });
  }

  intro() {
    const cam = this.homeCamera();
    this.map.jumpTo({ ...cam, zoom: cam.zoom - 0.8, pitch: 10, bearing: 25 });
    this.riseSlabs('corp');
    this.map.flyTo({ ...cam, pitch: this.pitch, bearing: -10, duration: 3200, curve: 1.1, essential: true });
  }

  fitItem(it, opts = {}) {
    const b = bboxOf(it);
    const isPoint = b[0][0] === b[1][0];
    const padding = this.padding(opts.padding || {});
    const bearing = opts.bearing ?? this.map.getBearing();
    if (isPoint) {
      this.map.flyTo({ center: b[0], zoom: opts.zoom ?? 13.2, pitch: opts.pitch ?? this.pitch, bearing, padding, duration: opts.duration ?? 1500, essential: true });
      return;
    }
    // cameraForBounds already offsets its centre for padding; we pass padding to
    // flyTo instead, so only take its zoom and use the plain bbox centre.
    const cam = this.map.cameraForBounds(b, { padding, bearing });
    const center = [(b[0][0] + b[1][0]) / 2, (b[0][1] + b[1][1]) / 2];
    const fitZoom = cam && cam.zoom != null ? cam.zoom : 11;
    const zoom = Math.min(fitZoom + (opts.zoomAdj ?? 0.15), opts.maxZoom ?? 14.5);
    this.map.flyTo({ center, zoom, pitch: opts.pitch ?? this.pitch, bearing, padding, duration: opts.duration ?? 1500, essential: true, curve: 1.3 });
  }

  setPitch(p) {
    this.pitch = p;
    this.map.easeTo({ pitch: p, duration: 700 });
  }

  // --------------------------------------------------------------- depth
  vis(id, on) { this.map.setLayoutProperty(id, 'visibility', on ? 'visible' : 'none'); }

  setDepth(depth, { animate = true } = {}) {
    this.depth = depth;
    const blank = depth === 'blank';
    const show = {
      'corp-glow': !blank,
      'corp-line': !blank,
      'corp-slab': depth === 'corp',
      'zone-slab': depth === 'zone',
      'circle-fill': depth === 'circle',
      'circle-line': ['circle', 'locality', 'landmark'].includes(depth),
      'zone-line': ['circle', 'locality'].includes(depth),
      'locality-fill': depth === 'locality',
      'locality-line': depth === 'locality',
      'metro-line': depth === 'metro' || depth === 'routes',
      'metro-glow': depth === 'metro',
      'corridor-line': depth === 'routes',
    };
    for (const [id, on] of Object.entries(show)) this.vis(id, on);
    this.map.setPaintProperty('circle-line', 'line-opacity', depth === 'circle' ? 0.6 : depth === 'locality' ? 0.5 : 0.22);
    this.map.setPaintProperty('domain-dark', 'fill-opacity', depth === 'corp' || depth === 'zone' ? 0.42 : depth === 'metro' || depth === 'routes' ? 0.35 : 0.08);
    this.map.setPaintProperty('sat', 'raster-brightness-max', depth === 'metro' ? 0.6 : 0.86);
    this.vis('roads', depth === 'routes' || this.roadsOn);
    this.dimLines(null);
    if (animate && (depth === 'corp' || depth === 'zone')) this.riseSlabs(depth);
    this.refreshLabels();
  }

  // slabs grow out of the ground one by one
  riseSlabs(depth) {
    const list = depth === 'corp' ? this.A.corporations : this.A.zones;
    const source = depth === 'corp' ? 'corpSlab' : 'zoneSlab';
    const t0 = performance.now();
    const dur = 900, stagger = depth === 'corp' ? 160 : 70;
    const step = () => {
      const t = performance.now() - t0;
      let busy = false;
      list.forEach((it, i) => {
        const k = Math.min(1, Math.max(0, (t - i * stagger) / dur));
        if (k < 1) busy = true;
        const e = 1 - Math.pow(1 - k, 3);
        this.map.setFeatureState({ source, id: it.id }, { rise: 0.02 + 0.98 * e });
      });
      if (busy) requestAnimationFrame(step);
    };
    step();
  }

  // Quiz mode: grey out colour coding so colours don't give answers away.
  setNeutral(on) {
    if (this.neutral === on) return;
    this.neutral = on;
    const sel = ['boolean', ['feature-state', 'sel'], false];
    const slab = on ? ['case', sel, '#5be49b', '#8e9a94'] : ['case', sel, ['get', 'colorHi'], ['get', 'color']];
    this.map.setPaintProperty('corp-slab', 'fill-extrusion-color', slab);
    this.map.setPaintProperty('zone-slab', 'fill-extrusion-color', slab);
    this.map.setPaintProperty('circle-fill', 'fill-color', on ? ['case', sel, '#5be49b', '#cfd8d3'] : corpColor());
    this.map.setPaintProperty('locality-fill', 'fill-color', on ? '#cfd8d3' : corpColor());
    this.map.setPaintProperty('corp-glow', 'line-color', on ? '#ffffff' : corpColor());
    this.map.setPaintProperty('corp-glow', 'line-opacity', on ? 0.18 : 0.55);
    this.map.setPaintProperty('corp-line', 'line-color', on ? '#e8eee9' : ['get', 'colorHi']);
    this.map.setPaintProperty('hl-glow', 'line-color', on ? '#5be49b' : ['coalesce', ['get', 'color'], '#5be49b']);
    this.map.setPaintProperty('hl-fill', 'fill-color', on ? '#5be49b' : ['coalesce', ['get', 'color'], '#5be49b']);
  }

  setRoads(on) {
    this.roadsOn = on;
    this.vis('roads', on || this.depth === 'routes');
  }

  dimLines(keepId) {
    for (const l of this.A.metro) this.map.setFeatureState({ source: 'metro', id: l.id }, { dim: !!keepId && l.id !== keepId });
    for (const r of this.A.corridors) this.map.setFeatureState({ source: 'corridor', id: r.id }, { dim: !!keepId && r.id !== keepId, sel: r.id === keepId });
  }

  markDone(ids) {
    for (const id of ids) {
      const it = this.A.get(id);
      if (it && it.kind === 'locality') this.map.setFeatureState({ source: 'locality', id }, { done: true });
    }
  }

  // --------------------------------------------------------------- labels
  labelSpecs(depth, sel) {
    const A = this.A;
    const selId = sel && sel.id;
    switch (depth) {
      case 'corp':
        return A.corporations.map((c) => ({ id: c.id, lngLat: c.label, text: c.name, kind: 'corp', pill: `Corporation ${c.num}`, pillColor: c.color, priority: 100, sel: c.id === selId }));
      case 'zone':
        return A.zones.map((z) => ({ id: z.id, lngLat: z.label, text: z.name, kind: 'zone', pill: 'Zone', pillColor: z.color, priority: 50 + z.areaKm2 / 10, sel: z.id === selId }));
      case 'circle':
        return A.circles.map((c) => ({ id: c.id, lngLat: c.label, text: c.name, kind: 'circle', priority: c.id === selId ? 999 : c.areaKm2, sel: c.id === selId }));
      case 'locality': {
        const lead = new Set(A.circles.map((c) => c.children[0]));
        return A.localities.map((l) => ({ id: l.id, lngLat: [l.lng, l.lat], text: l.name, kind: 'locality', dot: true, priority: l.id === selId ? 999 : lead.has(l.id) ? 20 : 10, sel: l.id === selId }));
      }
      case 'landmark':
        return A.landmarks.map((m) => ({ id: m.id, lngLat: [m.lng, m.lat], text: m.name, kind: 'landmark', icon: m.cat, color: A.color[m.corp] || '#fff', priority: m.id === selId ? 999 : 10, sel: m.id === selId }));
      case 'metro':
        return A.stations.map((s) => ({ id: s.id, lngLat: [s.lng, s.lat], text: s.name, kind: 'station', dot: true, dotColor: A.get(s.lines[0]).color, priority: s.id === selId ? 999 : s.lines.length > 1 ? 30 : 10, sel: s.id === selId }));
      case 'routes': {
        const out = [];
        const seen = new Set();
        for (const r of A.corridors) for (const s of r.stops) {
          if (seen.has(s.id)) continue;
          seen.add(s.id);
          out.push({ id: s.id, lngLat: [s.lng, s.lat], text: s.name, kind: 'stop', dot: true, dotColor: '#f2cf4a', priority: 10 });
        }
        return out;
      }
    }
    return [];
  }

  refreshLabels() {
    if (this.labelOverride) return;
    this.labels.set(this.labelsOn ? this.labelSpecs(this.depth, this.selected) : []);
  }

  setLabelOverride(specs) {
    this.labelOverride = specs;
    if (specs) this.labels.set(specs);
    else this.refreshLabels();
  }

  setLabelsOn(on) { this.labelsOn = on; this.refreshLabels(); }

  // --------------------------------------------------------------- selection
  select(it, { fly = true } = {}) {
    this.clearSelection(false);
    this.selected = it;
    if (!it) { this.refreshLabels(); return; }
    const color = it.color || '#5be49b';
    if (it.kind === 'corp' || it.kind === 'zone') {
      const source = it.kind === 'corp' ? 'corpSlab' : 'zoneSlab';
      this.map.setFeatureState({ source, id: it.id }, { sel: true });
      this.animateLift(source, it.id, it.kind === 'corp' ? 1300 : 900);
      if (this.depth !== it.kind) this.setHl(feat(it.geom, { color }));
    } else if (it.geom) {
      const source = it.kind;
      this.map.setFeatureState({ source, id: it.id }, { sel: true });
      this.setHl(feat(it.geom, { color }));
    } else if (it.kind === 'line') {
      this.dimLines(it.id);
      this.setHl(fc([]));
    } else if (it.kind === 'corridor') {
      this.dimLines(it.id);
    } else {
      this.setHl(feat({ type: 'Point', coordinates: pointOf(it) }, { color }));
    }
    this.refreshLabels();
    if (fly) {
      const zoomFor = { corp: 0, zone: -0.55, circle: -0.45, locality: -0.1 };
      const parent = it.kind === 'locality' ? this.A.get(it.circle) : null;
      if (parent) this.fitItem(parent, { zoomAdj: 0.2 });
      else this.fitItem(it, { zoomAdj: zoomFor[it.kind] ?? 0 });
    }
  }

  clearSelection(refresh = true) {
    const it = this.selected;
    if (it) {
      if (it.kind === 'corp' || it.kind === 'zone') {
        const source = it.kind === 'corp' ? 'corpSlab' : 'zoneSlab';
        this.map.setFeatureState({ source, id: it.id }, { sel: false });
        this.animateLift(source, it.id, 0);
      } else if (it.geom) {
        this.map.setFeatureState({ source: it.kind, id: it.id }, { sel: false });
      }
    }
    this.dimLines(null);
    this.setHl(fc([]));
    this.selected = null;
    if (refresh) this.refreshLabels();
  }

  animateLift(source, id, to) {
    const from = (this.map.getFeatureState({ source, id }) || {}).lift || 0;
    const t0 = performance.now(), dur = 550;
    const step = () => {
      const k = Math.min(1, (performance.now() - t0) / dur);
      const e = 1 - Math.pow(1 - k, 3);
      this.map.setFeatureState({ source, id }, { lift: from + (to - from) * e });
      if (k < 1) requestAnimationFrame(step);
    };
    step();
  }

  setHl(data) { this.map.getSource('hl').setData(data.type === 'Feature' ? fc([data]) : data); }
  setQuiz(features) { this.map.getSource('quiz').setData(fc(features)); }

  // --------------------------------------------------------------- picking
  onClick(e) {
    const ll = [e.lngLat.lng, e.lngLat.lat];
    if (this.tapHandler) { this.tapHandler(ll, e.point); return; }
    const it = this.pick(ll, e.point);
    if (this.selectHandler) this.selectHandler(it);
  }

  pick(ll, px) {
    const A = this.A;
    const near = (list) => {
      let best = null, bd = 26;
      for (const it of list) {
        const p = this.map.project(pointOf(it));
        const d = Math.hypot(p.x - px.x, p.y - px.y);
        if (d < bd) { bd = d; best = it; }
      }
      return best;
    };
    switch (this.depth) {
      case 'corp': return A.corporations.find((c) => pointInGeom(ll, c.geom)) || null;
      case 'zone': return A.zones.find((c) => pointInGeom(ll, c.geom)) || null;
      case 'circle': return A.circles.find((c) => pointInGeom(ll, c.geom)) || null;
      case 'locality': return near(A.localities) || A.localities.find((c) => pointInGeom(ll, c.geom)) || null;
      case 'landmark': return near(A.landmarks);
      case 'metro': {
        const st = near(A.stations);
        if (st) return st;
        let best = null, bd = 0.6;
        for (const l of A.metro) for (const p of l.line) { const d = km(p, ll); if (d < bd) { bd = d; best = l; } }
        return best;
      }
      case 'routes': {
        let best = null, bd = 1.2;
        for (const r of A.corridors) for (const s of r.stops) { const d = km([s.lng, s.lat], ll); if (d < bd) { bd = d; best = r; } }
        return best;
      }
    }
    return null;
  }
}
