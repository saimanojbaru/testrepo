// MapLibre scene: dark-graded satellite imagery with terrain relief, a
// spotlight mask around the city, raised translucent slabs for
// corporations/zones, glowing borders, the ORR as a double white line, real
// 3D buildings and named roads when zoomed in, metro lines and highways.
//
// Camera "4D" movement: pan, zoom, rotate and tilt freely (gestures or the
// camera dial), the tilt follows zoom automatically, and two motion modes:
// orbit (slow circle around a place) and fly-along (drone flight down a route).
import { LabelLayer } from './labels.js';
import { pointOf, bboxOf, pointInGeom, km, bearing as bearingOf } from './data.js';
import { CONFIG } from './config.js';

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
const ROAD_NAME = ['coalesce', ['get', 'name:en'], ['get', 'name_en'], ['get', 'name:latin'], ['get', 'name']];
const angleDiff = (a, b) => ((a - b + 540) % 360) - 180;

export class AtlasMap {
  constructor(el, labelsEl, A, opts = {}) {
    this.A = A;
    this.el = el;
    this.depth = 'corp';
    this.selected = null;
    this.tapHandler = null;
    this.selectHandler = null;
    this.pitch = 52;
    this.labelsOn = true;
    this.labelsEl = labelsEl;
    this.lite = !!opts.lite;
    this.terrainOn = !opts.lite && opts.terrain !== false;
    this.autoTilt = opts.autoTilt !== false;
    this.telugu = !!opts.telugu;
    this.userPitchAt = 0;
    this.motion = null;
    this.cameraListeners = [];
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
    const corridorFC = fc(A.corridors.filter((r) => r.id !== 'r-orr').map((r) =>
      feat({ type: 'LineString', coordinates: r.line || r.stops.map((s) => [s.lng, s.lat]) }, { id: r.id, ref: r.ref || '' }, r.id)));
    const riverFC = fc((A.rivers || []).map((r) => feat({ type: 'MultiLineString', coordinates: r.lines }, { id: r.id }, r.id)));
    const railFC = fc((A.rail || []).map((r) => feat({ type: 'LineString', coordinates: r.line }, { id: r.id }, r.id)));

    const style = {
      version: 8,
      glyphs: CONFIG.vector.glyphs,
      sky: {
        'sky-color': '#020403', 'horizon-color': '#0a1410', 'fog-color': '#040706',
        'sky-horizon-blend': 0.6, 'horizon-fog-blend': 0.7, 'fog-ground-blend': 0.35, 'atmosphere-blend': 0,
      },
      light: { anchor: 'viewport', color: '#ffffff', intensity: 0.32, position: [1.4, 200, 35] },
      sources: {
        sat: { type: 'raster', tiles: CONFIG.satellite.tiles, tileSize: 256, maxzoom: CONFIG.satellite.maxzoom },
        omt: { type: 'vector', url: CONFIG.vector.url },
        dem: { type: 'raster-dem', tiles: CONFIG.terrain.tiles, tileSize: 256, maxzoom: CONFIG.terrain.maxzoom, encoding: 'terrarium' },
        demHill: { type: 'raster-dem', tiles: CONFIG.terrain.tiles, tileSize: 256, maxzoom: CONFIG.terrain.maxzoom, encoding: 'terrarium' },
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
        river: { type: 'geojson', data: riverFC, promoteId: 'id' },
        rail: { type: 'geojson', data: railFC, promoteId: 'id' },
        hl: { type: 'geojson', data: fc([]) },
        quiz: { type: 'geojson', data: fc([]) },
        draw: { type: 'geojson', data: fc([]) },
        truth: { type: 'geojson', data: fc([]) },
      },
      layers: [
        { id: 'bg', type: 'background', paint: { 'background-color': '#050807' } },
        { id: 'sat', type: 'raster', source: 'sat', paint: { 'raster-saturation': -0.18, 'raster-contrast': 0.12, 'raster-brightness-max': 0.86, 'raster-fade-duration': 250 } },
        { id: 'hillshade', type: 'hillshade', source: 'demHill', layout: { visibility: this.lite ? 'none' : 'visible' },
          paint: { 'hillshade-exaggeration': 0.45, 'hillshade-shadow-color': '#000000', 'hillshade-highlight-color': '#fff6e0', 'hillshade-accent-color': '#000000', 'hillshade-illumination-direction': 315 } },
        { id: 'mask', type: 'fill', source: 'mask', paint: { 'fill-color': '#000', 'fill-opacity': 0.62 } },
        { id: 'domain-dark', type: 'fill', source: 'domain', paint: { 'fill-color': '#000', 'fill-opacity': 0, 'fill-opacity-transition': { duration: 600 } } },

        // ---- vector roads (always shown when zoomed in; at all zooms with "road map" on)
        { id: 'road-minor', type: 'line', source: 'omt', 'source-layer': 'transportation', minzoom: 14,
          filter: ['in', ['get', 'class'], ['literal', ['minor', 'service']]],
          paint: { 'line-color': '#e9e4d8', 'line-opacity': ['interpolate', ['linear'], ['zoom'], 14, 0, 15, 0.28], 'line-width': ['interpolate', ['linear'], ['zoom'], 14, 0.5, 18, 5] } },
        { id: 'road-secondary', type: 'line', source: 'omt', 'source-layer': 'transportation', minzoom: 12,
          filter: ['in', ['get', 'class'], ['literal', ['secondary', 'tertiary']]],
          paint: { 'line-color': '#efe9dc', 'line-opacity': ['interpolate', ['linear'], ['zoom'], 12, 0, 13, 0.45], 'line-width': ['interpolate', ['linear'], ['zoom'], 12, 0.6, 18, 9] } },
        { id: 'road-primary', type: 'line', source: 'omt', 'source-layer': 'transportation', minzoom: 11,
          filter: ['==', ['get', 'class'], 'primary'],
          paint: { 'line-color': '#f6ead0', 'line-opacity': ['interpolate', ['linear'], ['zoom'], 11, 0, 12, 0.7], 'line-width': ['interpolate', ['linear'], ['zoom'], 11, 0.8, 18, 14] } },
        { id: 'road-major-glow', type: 'line', source: 'omt', 'source-layer': 'transportation', minzoom: 9,
          filter: ['in', ['get', 'class'], ['literal', ['motorway', 'trunk']]],
          paint: { 'line-color': '#ffffff', 'line-blur': 5, 'line-opacity': ['interpolate', ['linear'], ['zoom'], 9, 0, 11, 0.35], 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 3, 18, 26] } },
        { id: 'road-major', type: 'line', source: 'omt', 'source-layer': 'transportation', minzoom: 9,
          filter: ['in', ['get', 'class'], ['literal', ['motorway', 'trunk']]],
          paint: { 'line-color': '#f3e6c8', 'line-opacity': ['interpolate', ['linear'], ['zoom'], 9, 0, 11, 0.9], 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.8, 18, 16] } },
        { id: 'rail-osm', type: 'line', source: 'omt', 'source-layer': 'transportation', minzoom: 12,
          filter: ['==', ['get', 'class'], 'rail'],
          paint: { 'line-color': '#c8cdd0', 'line-opacity': 0.55, 'line-width': ['interpolate', ['linear'], ['zoom'], 12, 0.8, 18, 3], 'line-dasharray': [3, 2] } },

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

        { id: 'river-glow', type: 'line', source: 'river', layout: { visibility: 'none', 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': '#4fb3ff', 'line-width': 10, 'line-blur': 8, 'line-opacity': 0.45 } },
        { id: 'river-line', type: 'line', source: 'river', layout: { visibility: 'none', 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': '#8fd0ff', 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 1.5, 14, 4] } },

        { id: 'orr-glow', type: 'line', source: 'orr', paint: { 'line-color': '#fff', 'line-width': 7, 'line-blur': 6, 'line-opacity': 0.18 } },
        { id: 'orr', type: 'line', source: 'orr', paint: { 'line-color': '#f4f1ea', 'line-width': 1.1, 'line-gap-width': ['interpolate', ['linear'], ['zoom'], 9, 1.6, 14, 6], 'line-opacity': 0.9 } },

        { id: 'rail-line', type: 'line', source: 'rail', layout: { visibility: 'none', 'line-join': 'round' },
          paint: { 'line-color': '#d9b3ff', 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 1.5, 14, 3.5], 'line-dasharray': [2, 1.2], 'line-opacity': ['case', ['boolean', ['feature-state', 'dim'], false], 0.15, 0.85] } },
        { id: 'corridor-glow', type: 'line', source: 'corridor', layout: { visibility: 'none', 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': '#f2a93b', 'line-width': ['case', ['boolean', ['feature-state', 'sel'], false], 16, 9], 'line-blur': 7,
            'line-opacity': ['case', ['boolean', ['feature-state', 'dim'], false], 0.05, 0.5] } },
        { id: 'corridor-line', type: 'line', source: 'corridor', layout: { visibility: 'none', 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': ['case', ['boolean', ['feature-state', 'sel'], false], '#fff3c4', '#f7c548'], 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 2.2, 14, 6],
            'line-opacity': ['case', ['boolean', ['feature-state', 'dim'], false], 0.18, 1] } },
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

        // ---- real 3D buildings rise out of the ground as you zoom in
        { id: 'building-3d', type: 'fill-extrusion', source: 'omt', 'source-layer': 'building', minzoom: 14,
          layout: { visibility: this.lite ? 'none' : 'visible' },
          paint: {
            'fill-extrusion-color': ['interpolate', ['linear'], ['coalesce', ['get', 'render_height'], 6], 0, '#a7b1ac', 25, '#cdd6d1', 70, '#e9f1ee', 150, '#f6fbff'],
            'fill-extrusion-height': ['interpolate', ['linear'], ['zoom'], 14, 0, 15.3, ['coalesce', ['get', 'render_height'], 6]],
            'fill-extrusion-base': ['coalesce', ['get', 'render_min_height'], 0],
            'fill-extrusion-opacity': 0.9, 'fill-extrusion-vertical-gradient': true,
          } },

        // ---- road names and highway shields
        { id: 'road-shield', type: 'symbol', source: 'omt', 'source-layer': 'transportation_name', minzoom: 10,
          filter: ['all', ['has', 'ref'], ['in', ['get', 'class'], ['literal', ['motorway', 'trunk', 'primary']]], ['<=', ['length', ['get', 'ref']], 7]],
          layout: { 'symbol-placement': 'line', 'symbol-spacing': 420, 'text-field': ['get', 'ref'], 'text-font': ['Noto Sans Bold'], 'text-size': 10.5,
            'icon-image': 'shield', 'icon-text-fit': 'both', 'icon-text-fit-padding': [2, 5, 2, 5], 'text-rotation-alignment': 'viewport', 'icon-rotation-alignment': 'viewport', 'text-letter-spacing': 0.04 },
          paint: { 'text-color': '#13110c' } },
        { id: 'road-label', type: 'symbol', source: 'omt', 'source-layer': 'transportation_name', minzoom: 13,
          filter: ['in', ['get', 'class'], ['literal', ['motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor']]],
          layout: { 'symbol-placement': 'line', 'text-field': ROAD_NAME, 'text-font': ['Noto Sans Regular'], 'text-size': ['interpolate', ['linear'], ['zoom'], 13, 10, 17, 13], 'text-max-angle': 30, 'symbol-spacing': 350 },
          paint: { 'text-color': '#fbf6ea', 'text-halo-color': 'rgba(0,0,0,0.85)', 'text-halo-width': 1.4 } },
        { id: 'poi-label', type: 'symbol', source: 'omt', 'source-layer': 'poi', minzoom: 15.5,
          filter: ['all', ['has', 'name'], ['<=', ['coalesce', ['get', 'rank'], 99], 20],
            ['in', ['get', 'class'], ['literal', ['hospital', 'college', 'school', 'shop', 'place_of_worship', 'attraction', 'museum', 'park', 'railway', 'bus', 'cinema', 'stadium', 'bank', 'fuel', 'restaurant', 'cafe']]]],
          layout: { 'text-field': ROAD_NAME, 'text-font': ['Noto Sans Regular'], 'text-size': 10.5, 'text-max-width': 9, 'text-offset': [0, 0.2] },
          paint: { 'text-color': '#d8e6ff', 'text-halo-color': 'rgba(0,0,0,0.85)', 'text-halo-width': 1.2 } },

        { id: 'quiz-fill', type: 'fill', source: 'quiz', filter: ['==', '$type', 'Polygon'], paint: { 'fill-color': ['get', 'color'], 'fill-opacity': 0.35 } },
        { id: 'quiz-outline', type: 'line', source: 'quiz', filter: ['==', '$type', 'Polygon'], paint: { 'line-color': ['get', 'color'], 'line-width': 2.5 } },
        { id: 'quiz-line', type: 'line', source: 'quiz', filter: ['==', '$type', 'LineString'],
          paint: { 'line-color': ['get', 'color'], 'line-width': ['coalesce', ['get', 'w'], 2.5], 'line-dasharray': [1.5, 1.2] } },

        // ---- draw-the-route game
        { id: 'truth-glow', type: 'line', source: 'truth', layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': '#5be49b', 'line-width': 16, 'line-blur': 10, 'line-opacity': 0.55 } },
        { id: 'truth-line', type: 'line', source: 'truth', layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': '#5be49b', 'line-width': 4 } },
        { id: 'draw-glow', type: 'line', source: 'draw', layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': ['match', ['get', 'ok'], 'yes', '#5be49b', 'no', '#ff5a6e', '#ffd84d'], 'line-width': 14, 'line-blur': 9, 'line-opacity': 0.6 } },
        { id: 'draw-line', type: 'line', source: 'draw', layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': ['match', ['get', 'ok'], 'yes', '#c8ffe0', 'no', '#ffb3bd', '#fff3b0'], 'line-width': 4.5 } },
      ],
    };

    const home = this.homeCamera();
    this.map = new maplibregl.Map({
      container: this.el, style, center: home.center, pitch: 20, bearing: 0, zoom: home.zoom - 0.6,
      maxPitch: 78, maxZoom: 18.5, attributionControl: false, fadeDuration: 150,
      dragRotate: true, pitchWithRotate: true, renderWorldCopies: false,
      maxBounds: [[77.6, 16.8], [79.4, 18.0]],
      pixelRatio: Math.min(window.devicePixelRatio || 1, this.lite ? 1.5 : 2),
      antialias: false,
    });
    this.map.touchZoomRotate.enableRotation();
    this.map.on('error', (e) => console.warn('map:', e.error && e.error.message));
    this.map.on('styleimagemissing', (e) => { if (e.id === 'shield') this.addShield(); });
    this.labels = new LabelLayer(this.map, this.labelsEl);
    this.map.on('click', (e) => this.onClick(e));

    // remember when the user tilts by hand, so auto-tilt doesn't fight them
    this.map.on('pitchstart', (e) => { if (e.originalEvent) this.userPitchAt = Date.now(); });
    this.map.on('zoomend', (e) => { if (e.originalEvent) this.autoTiltTo(); });
    this.map.on('rotate', () => this.emitCamera());
    this.map.on('pitch', () => this.emitCamera());
    // any touch stops orbit / fly-along
    const stopOnTouch = () => { if (this.motion && !this.motion.keep) this.stopMotion(); };
    this.el.addEventListener('pointerdown', stopOnTouch, { passive: true });
    this.el.addEventListener('wheel', stopOnTouch, { passive: true });

    // Start even if an online source (vector tiles, terrain) is slow or down:
    // 'load' waits for every source, so fall back after a few seconds.
    return new Promise((resolve) => {
      let done = false;
      const go = () => {
        if (done) return;
        done = true;
        try { this.addShield(); } catch { /* retried via styleimagemissing */ }
        if (this.terrainOn) { try { this.applyTerrain(true); } catch { /* terrain source unavailable */ } }
        resolve();
      };
      this.map.on('load', go);
      const fallback = () => (this.map.style && this.map.style._loaded ? go() : setTimeout(fallback, 500));
      setTimeout(fallback, 6000);
    });
  }

  // White NH-style shield behind highway numbers, drawn at runtime.
  addShield() {
    if (this.map.hasImage('shield')) return;
    const r = 2, w = 32 * r, h = 22 * r;
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const g = c.getContext('2d');
    g.fillStyle = '#ffffff';
    g.strokeStyle = '#13110c';
    g.lineWidth = 2 * r;
    g.beginPath();
    g.roundRect(r, r, w - 2 * r, h - 2 * r, 5 * r);
    g.fill(); g.stroke();
    g.fillStyle = '#f2b23a';
    g.fillRect(3 * r, 3 * r, w - 6 * r, 2.5 * r);
    this.map.addImage('shield', g.getImageData(0, 0, w, h), {
      pixelRatio: r, stretchX: [[8 * r, 24 * r]], stretchY: [[8 * r, 14 * r]], content: [5 * r, 5 * r, 27 * r, 18 * r],
    });
  }

  applyTerrain(on) {
    this.terrainOn = on;
    if (!this.map) return;
    if (on && !this.lite) this.map.setTerrain({ source: 'dem', exaggeration: CONFIG.terrain.exaggeration });
    else this.map.setTerrain(null);
  }

  setLite(lite) {
    this.lite = lite;
    this.vis('building-3d', !lite);
    this.vis('hillshade', !lite);
    this.applyTerrain(!lite && this.terrainOn);
  }

  // --------------------------------------------------------------- camera
  // Screen area the map content should fit into (headline above, panels below).
  padding(extra = {}) {
    const H = this.el.clientHeight || 800;
    const base = this.viewPad || { top: 150, bottom: Math.round(H * 0.15), left: 14, right: 14 };
    return { ...base, ...extra };
  }

  homeCamera(pad = this.padding()) {
    const W = this.el.clientWidth || 390, H = this.el.clientHeight || 800;
    const [[x0, y0], [x1, y1]] = bboxOf({ geom: this.A.domain });
    const usableW = W - pad.left - pad.right, usableH = H - pad.top - pad.bottom;
    const lngSpan = x1 - x0, latSpan = (y1 - y0) / Math.cos((17.4 * Math.PI) / 180);
    const zoom = Math.log2(Math.min(usableW / lngSpan, usableH / latSpan) * 360 / 512);
    // a tilted camera magnifies the near (south) edge, so back off with tilt
    const tiltLoss = (this.pitch / 60) * 0.3;
    return { center: [(x0 + x1) / 2, (y0 + y1) / 2 + 0.01], zoom: Math.min(zoom - 0.04 - tiltLoss, 11), padding: pad };
  }

  home(opts = {}) {
    this.stopMotion();
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
    this.stopMotion();
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
    const zoom = Math.min(fitZoom + (opts.zoomAdj ?? 0.15) - ((opts.pitch ?? this.pitch) / 60) * 0.25, opts.maxZoom ?? 14.5);
    this.map.flyTo({ center, zoom, pitch: opts.pitch ?? this.pitch, bearing, padding, duration: opts.duration ?? 1500, essential: true, curve: 1.3 });
  }

  setPitch(p) {
    this.pitch = p;
    this.map.easeTo({ pitch: p, duration: 700 });
  }

  // Tilt follows zoom like a drone camera: flat-ish over the city, steep in close.
  pitchForZoom(z) {
    if (this.pitch === 0) return 0;
    if (z <= 11) return this.pitch;
    return Math.min(70, this.pitch + (z - 11) * 4.2);
  }

  autoTiltTo() {
    if (!this.autoTilt || this.motion || this.tapHandler || this.drawing) return;
    if (Date.now() - this.userPitchAt < 8000) return;
    const target = this.pitchForZoom(this.map.getZoom());
    if (Math.abs(target - this.map.getPitch()) > 3) this.map.easeTo({ pitch: target, duration: 650 });
  }

  // One-hand camera dial: drag sideways to rotate, up/down to tilt.
  nudgeCamera(dBearing, dPitch) {
    this.stopMotion();
    this.userPitchAt = Date.now();
    const m = this.map;
    m.jumpTo({ bearing: m.getBearing() + dBearing, pitch: Math.max(0, Math.min(78, m.getPitch() + dPitch)) });
  }

  resetNorth() {
    this.stopMotion();
    this.map.easeTo({ bearing: 0, duration: 600 });
  }

  onCamera(f) { this.cameraListeners.push(f); }
  emitCamera() {
    const b = this.map.getBearing(), p = this.map.getPitch();
    this.cameraListeners.forEach((f) => f(b, p));
  }

  // --------------------------------------------------------------- motion
  stopMotion() {
    if (!this.motion) return;
    cancelAnimationFrame(this.motion.raf);
    const done = this.motion.onStop;
    this.motion = null;
    if (done) done();
  }

  // Fly in close and circle slowly around a place.
  orbit(it, { onStop } = {}) {
    this.stopMotion();
    const center = pointOf(it);
    const zoom = it.kind === 'circle' ? 14 : it.kind === 'zone' ? 12.6 : 16.1;
    const m = this.map;
    m.flyTo({ center, zoom, pitch: 66, bearing: m.getBearing() - 30, duration: 2400, essential: true, padding: this.padding({ top: 60 }) });
    const motion = this.motion = { onStop, raf: 0 };
    m.once('moveend', () => {
      if (this.motion !== motion) return;
      let last = performance.now();
      const spin = (t) => {
        if (this.motion !== motion) return;
        const dt = Math.min(64, t - last); last = t;
        m.setBearing(m.getBearing() + dt * 0.006);
        motion.raf = requestAnimationFrame(spin);
      };
      motion.raf = requestAnimationFrame(spin);
    });
  }

  // Drone flight along a line. coords: [[lng,lat],...]
  flyAlong(coords, { zoom = 14.6, pitch = 64, kmPerSec = 0.55, onTick, onEnd, onStop } = {}) {
    this.stopMotion();
    const cum = [0];
    for (let i = 1; i < coords.length; i++) cum.push(cum[i - 1] + km(coords[i - 1], coords[i]));
    const total = cum.at(-1);
    const at = (s) => {
      s = Math.max(0, Math.min(total, s));
      let lo = 0, hi = cum.length - 1;
      while (hi - lo > 1) { const mid = (lo + hi) >> 1; if (cum[mid] <= s) lo = mid; else hi = mid; }
      const seg = cum[hi] - cum[lo] || 1, k = (s - cum[lo]) / seg;
      return [coords[lo][0] + (coords[hi][0] - coords[lo][0]) * k, coords[lo][1] + (coords[hi][1] - coords[lo][1]) * k];
    };
    const m = this.map;
    let b = bearingOf(at(0), at(0.8));
    const motion = this.motion = { onStop, raf: 0, speed: 1, s: 0, total };
    m.flyTo({ center: at(0), zoom, pitch, bearing: b, duration: 2200, essential: true, padding: this.padding({ top: 120, bottom: 160 }) });
    m.once('moveend', () => {
      if (this.motion !== motion) return;
      let last = performance.now();
      const step = (t) => {
        if (this.motion !== motion) return;
        const dt = Math.min(64, t - last) / 1000; last = t;
        if (!motion.paused) motion.s += kmPerSec * motion.speed * dt;
        const p = at(motion.s);
        const target = bearingOf(p, at(motion.s + 0.9));
        b += angleDiff(target, b) * Math.min(1, dt * 1.6);
        m.jumpTo({ center: p, bearing: b });
        if (onTick) onTick(motion.s, total, p);
        if (motion.s >= total) {
          this.motion = null;
          if (onEnd) onEnd();
          return;
        }
        motion.raf = requestAnimationFrame(step);
      };
      motion.raf = requestAnimationFrame(step);
    });
    return motion;
  }

  // --------------------------------------------------------------- depth
  vis(id, on) { if (this.map.getLayer(id)) this.map.setLayoutProperty(id, 'visibility', on ? 'visible' : 'none'); }

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
      'corridor-glow': depth === 'routes',
      'river-line': depth === 'routes' || depth === 'landmark',
      'river-glow': depth === 'routes',
      'rail-line': depth === 'routes' || depth === 'metro',
    };
    for (const [id, on] of Object.entries(show)) this.vis(id, on);
    this.map.setPaintProperty('circle-line', 'line-opacity', depth === 'circle' ? 0.6 : depth === 'locality' ? 0.5 : 0.22);
    this.map.setPaintProperty('domain-dark', 'fill-opacity', depth === 'corp' || depth === 'zone' ? 0.42 : depth === 'metro' || depth === 'routes' ? 0.35 : 0.08);
    this.map.setPaintProperty('sat', 'raster-brightness-max', depth === 'metro' ? 0.6 : 0.86);
    this.applyRoadMode();
    this.dimLines(null);
    if (animate && (depth === 'corp' || depth === 'zone')) this.riseSlabs(depth);
    this.refreshLabels();
  }

  // Roads: always visible when zoomed in; "road map" (or Routes depth) shows
  // the highway network from city zoom too.
  applyRoadMode() {
    const strong = this.roadsOn || this.depth === 'routes';
    const quiet = this.depth === 'routes' && !this.roadsOn;
    const majorOpacity = quiet ? ['interpolate', ['linear'], ['zoom'], 8, 0.25, 12, 0.55] : strong ? ['interpolate', ['linear'], ['zoom'], 8, 0.55, 11, 0.95] : ['interpolate', ['linear'], ['zoom'], 11, 0, 12.5, 0.85];
    const glowOpacity = quiet ? 0 : strong ? ['interpolate', ['linear'], ['zoom'], 8, 0.25, 11, 0.4] : ['interpolate', ['linear'], ['zoom'], 11, 0, 12.5, 0.3];
    const primaryOpacity = strong ? ['interpolate', ['linear'], ['zoom'], 10, 0.35, 12, 0.75] : ['interpolate', ['linear'], ['zoom'], 12, 0, 13, 0.7];
    this.map.setPaintProperty('road-major', 'line-opacity', majorOpacity);
    this.map.setPaintProperty('road-major-glow', 'line-opacity', glowOpacity);
    this.map.setPaintProperty('road-primary', 'line-opacity', primaryOpacity);
    this.map.setLayerZoomRange('road-major', strong ? 7 : 11, 24);
    this.map.setLayerZoomRange('road-major-glow', strong ? 7 : 11, 24);
    this.map.setLayerZoomRange('road-primary', strong ? 10 : 12, 24);
    this.map.setLayerZoomRange('road-shield', strong ? 9 : 12, 24);
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
    // names on the base map would give answers away too
    for (const id of ['road-label', 'poi-label']) this.vis(id, !on);
  }

  setRoads(on) {
    this.roadsOn = on;
    this.applyRoadMode();
  }

  dimLines(keepId) {
    for (const l of this.A.metro) this.map.setFeatureState({ source: 'metro', id: l.id }, { dim: !!keepId && l.id !== keepId });
    for (const r of this.A.corridors) if (r.id !== 'r-orr') this.map.setFeatureState({ source: 'corridor', id: r.id }, { dim: !!keepId && r.id !== keepId, sel: r.id === keepId });
    for (const r of this.A.rail || []) this.map.setFeatureState({ source: 'rail', id: r.id }, { dim: !!keepId && r.id !== keepId });
  }

  markDone(ids) {
    for (const id of ids) {
      const it = this.A.get(id);
      if (it && it.kind === 'locality') this.map.setFeatureState({ source: 'locality', id }, { done: true });
    }
  }

  // --------------------------------------------------------------- labels
  te(it) { return this.telugu && it.te ? it.te : undefined; }

  labelSpecs(depth, sel) {
    const A = this.A;
    const selId = sel && sel.id;
    switch (depth) {
      case 'corp':
        return A.corporations.map((c) => ({ id: c.id, lngLat: c.label, text: c.name, sub: this.te(c), kind: 'corp', pill: `Corporation ${c.num}`, pillColor: c.color, priority: 100, sel: c.id === selId }));
      case 'zone':
        return A.zones.map((z) => ({ id: z.id, lngLat: z.label, text: z.name, sub: this.te(z), kind: 'zone', pill: 'Zone', pillColor: z.color, priority: 50 + z.areaKm2 / 10, sel: z.id === selId }));
      case 'circle':
        return A.circles.map((c) => ({ id: c.id, lngLat: c.label, text: c.name, sub: this.te(c), kind: 'circle', priority: c.id === selId ? 999 : c.areaKm2, sel: c.id === selId }));
      case 'locality': {
        const lead = new Set(A.circles.map((c) => c.children[0]));
        return A.localities.map((l) => ({ id: l.id, lngLat: [l.lng, l.lat], text: l.name, sub: this.te(l), kind: 'locality', dot: true, priority: l.id === selId ? 999 : lead.has(l.id) ? 20 : 10, sel: l.id === selId }));
      }
      case 'landmark':
        return A.landmarks.map((m) => ({ id: m.id, lngLat: [m.lng, m.lat], text: m.name, sub: this.te(m), kind: 'landmark', icon: m.cat, img: m.photo && m.photo.thumb, color: A.color[m.corp] || '#fff', priority: m.id === selId ? 999 : (m.photo ? 20 : 10) + (m.rank || 0), sel: m.id === selId }));
      case 'metro':
        return A.stations.map((s) => ({ id: s.id, lngLat: [s.lng, s.lat], text: s.name, kind: 'station', metro: A.get(s.lines[0]).color, priority: s.id === selId ? 999 : s.lines.length > 1 ? 30 : 10, sel: s.id === selId }));
      case 'routes': {
        const out = [];
        const seen = new Set();
        for (const r of A.corridors) {
          if (r.ref && r.line) {
            const mid = r.line[Math.floor(r.line.length * 0.55)];
            out.push({ id: 'sh-' + r.id, lngLat: mid, text: r.ref, kind: 'shield', priority: 60 });
          }
          for (const s of r.stops) {
            if (seen.has(s.name)) continue;
            seen.add(s.name);
            out.push({ id: 'st-' + s.name, lngLat: [s.lng, s.lat], text: s.name, kind: 'stop', dot: true, dotColor: '#f2cf4a', priority: 10 });
          }
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
  setTelugu(on) { this.telugu = on; this.refreshLabels(); }

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
    } else if (it.kind === 'line' || it.kind === 'corridor' || it.kind === 'rail') {
      this.dimLines(it.id);
      this.setHl(it.id === 'r-orr' ? feat({ type: 'LineString', coordinates: it.line }, { color: '#ffffff' }) : fc([]));
    } else if (it.kind === 'river') {
      this.setHl(feat({ type: 'MultiLineString', coordinates: it.lines }, { color: '#4fb3ff' }));
    } else {
      this.setHl(feat({ type: 'Point', coordinates: pointOf(it) }, { color }));
    }
    this.refreshLabels();
    if (fly) {
      const zoomFor = { corp: 0, zone: -0.55, circle: -0.45, locality: -0.1 };
      const parent = it.kind === 'locality' ? this.A.get(it.circle) : null;
      if (parent) this.fitItem(parent, { zoomAdj: 0.2 });
      else if (it.kind === 'landmark' || it.kind === 'station') this.fitItem(it, { zoom: 15.2, pitch: this.pitchForZoom(15.2) });
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
  setDraw(features) { this.map.getSource('draw').setData(fc(features)); }
  setTruth(features) { this.map.getSource('truth').setData(fc(features)); }

  // Freeze map gestures while the user draws with a finger.
  setGestures(on) {
    const m = this.map;
    for (const h of [m.dragPan, m.touchZoomRotate, m.doubleClickZoom, m.dragRotate, m.touchPitch, m.scrollZoom]) on ? h.enable() : h.disable();
    if (on) m.touchZoomRotate.enableRotation();
  }

  // --------------------------------------------------------------- picking
  onClick(e) {
    if (this.drawing) return;
    const ll = [e.lngLat.lng, e.lngLat.lat];
    if (this.tapHandler) { this.tapHandler(ll, e.point); return; }
    const it = this.pick(ll, e.point);
    if (this.selectHandler) this.selectHandler(it);
  }

  pick(ll, px) {
    const A = this.A;
    const near = (list, maxPx = 28) => {
      let best = null, bd = maxPx;
      for (const it of list) {
        const p = this.map.project(pointOf(it));
        const d = Math.hypot(p.x - px.x, p.y - px.y);
        if (d < bd) { bd = d; best = it; }
      }
      return best;
    };
    const nearLine = (items, getLines, maxKm) => {
      let best = null, bd = maxKm;
      for (const it of items) for (const line of getLines(it)) for (const p of line) { const d = km(p, ll); if (d < bd) { bd = d; best = it; } }
      return best;
    };
    switch (this.depth) {
      case 'corp': return A.corporations.find((c) => pointInGeom(ll, c.geom)) || null;
      case 'zone': return A.zones.find((c) => pointInGeom(ll, c.geom)) || null;
      case 'circle': return A.circles.find((c) => pointInGeom(ll, c.geom)) || null;
      case 'locality': return near(A.localities) || A.localities.find((c) => pointInGeom(ll, c.geom)) || null;
      case 'landmark': return near(A.landmarks, 34);
      case 'metro': return near(A.stations) || nearLine(A.metro, (l) => [l.line], 0.6) || nearLine(A.rail || [], (r) => [r.line], 0.5);
      case 'routes': {
        const scale = Math.max(0.25, 2.5 / Math.pow(2, this.map.getZoom() - 10));
        return nearLine(A.corridors, (r) => [r.line || r.stops.map((s) => [s.lng, s.lat])], scale)
          || nearLine(A.rivers || [], (r) => r.lines, scale) || nearLine(A.metro, (l) => [l.line], scale * 0.6);
      }
    }
    return null;
  }
}
