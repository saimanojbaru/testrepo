// Draw-the-route game: finger drawing on the map + scoring against the real
// geometry. Score = F1 of coverage (how much of the route you traced) and
// precision (how much of your line stayed on the route), within a tolerance.
import { resample, distToLineKm } from './data.js';

export const LEVELS = {
  easy: { label: 'Easy', tolKm: 1.0, stops: true, roads: true, note: 'Road map and stops shown · 1 km tolerance' },
  pro: { label: 'Pro', tolKm: 0.7, stops: false, roads: false, note: 'Only start and end · 700 m tolerance' },
  expert: { label: 'Expert', tolKm: 0.45, stops: false, roads: false, dim: true, note: 'From memory on a dimmed map · 450 m tolerance' },
};

function minDistToStrokes(p, strokes) {
  let best = Infinity;
  for (const s of strokes) best = Math.min(best, distToLineKm(p, s));
  return best;
}

export function scoreDrawing(strokes, truth, tolKm) {
  const lines = strokes.filter((s) => s.length > 1);
  if (!lines.length) return { score: 0, coverage: 0, precision: 0, avgOffM: null, correct: false };
  const T = resample(truth, 0.08);
  const D = lines.flatMap((s) => resample(s, 0.08));
  let cov = 0;
  for (const p of T) if (minDistToStrokes(p, lines) <= tolKm) cov++;
  let prec = 0, off = 0;
  for (const p of D) {
    const d = distToLineKm(p, truth);
    off += d;
    if (d <= tolKm) prec++;
  }
  const coverage = cov / T.length, precision = prec / D.length;
  const f1 = coverage + precision ? (2 * coverage * precision) / (coverage + precision) : 0;
  const score = Math.round(f1 * 100);
  return { score, coverage, precision, avgOffM: Math.round((off / D.length) * 1000), correct: score >= 70 };
}

// Split strokes into green (on route) and red (off route) pieces.
export function judgeStrokes(strokes, truth, tolKm) {
  const out = [];
  for (const s of strokes) {
    if (s.length < 2) continue;
    let run = [s[0]];
    let ok = distToLineKm(s[0], truth) <= tolKm;
    for (let i = 1; i < s.length; i++) {
      const o = distToLineKm(s[i], truth) <= tolKm;
      run.push(s[i]);
      if (o !== ok) {
        out.push({ type: 'Feature', geometry: { type: 'LineString', coordinates: run }, properties: { ok: ok ? 'yes' : 'no' } });
        run = [s[i]];
        ok = o;
      }
    }
    if (run.length > 1) out.push({ type: 'Feature', geometry: { type: 'LineString', coordinates: run }, properties: { ok: ok ? 'yes' : 'no' } });
  }
  return out;
}

// Finger drawing over the map. In "move" mode the map pans/zooms as usual.
export class DrawPad {
  constructor(atlasMap, onChange) {
    this.am = atlasMap;
    this.onChange = onChange;
    this.strokes = [];
    this.mode = 'draw';
    this.cur = null;
    this.raf = 0;
    this.el = atlasMap.el;
    this.down = (e) => this.onDown(e);
    this.move = (e) => this.onMove(e);
    this.up = (e) => this.onUp(e);
  }

  start() {
    this.am.drawing = true;
    this.el.addEventListener('pointerdown', this.down, true);
    this.el.addEventListener('pointermove', this.move, true);
    window.addEventListener('pointerup', this.up, true);
    window.addEventListener('pointercancel', this.up, true);
    this.setMode('draw');
  }

  stop() {
    this.el.removeEventListener('pointerdown', this.down, true);
    this.el.removeEventListener('pointermove', this.move, true);
    window.removeEventListener('pointerup', this.up, true);
    window.removeEventListener('pointercancel', this.up, true);
    this.am.drawing = false;
    this.am.setGestures(true);
    this.el.style.cursor = '';
  }

  setMode(mode) {
    this.mode = mode;
    this.am.setGestures(mode === 'move');
    this.el.style.cursor = mode === 'draw' ? 'crosshair' : '';
  }

  ll(e) {
    const r = this.el.getBoundingClientRect();
    const p = this.am.map.unproject([e.clientX - r.left, e.clientY - r.top]);
    return [p.lng, p.lat];
  }

  onDown(e) {
    if (this.mode !== 'draw' || !e.isPrimary) return;
    e.preventDefault();
    e.stopPropagation();
    this.cur = [this.ll(e)];
    this.lastPx = [e.clientX, e.clientY];
    this.strokes.push(this.cur);
    this.render();
  }

  onMove(e) {
    if (this.mode !== 'draw' || !this.cur || !e.isPrimary) return;
    e.preventDefault();
    e.stopPropagation();
    const dx = e.clientX - this.lastPx[0], dy = e.clientY - this.lastPx[1];
    if (dx * dx + dy * dy < 16) return;
    this.lastPx = [e.clientX, e.clientY];
    this.cur.push(this.ll(e));
    this.render();
  }

  onUp() {
    if (!this.cur) return;
    if (this.cur.length < 2) this.strokes.pop();
    this.cur = null;
    this.render();
    if (this.onChange) this.onChange(this.strokes);
  }

  undo() {
    this.strokes.pop();
    this.render();
    if (this.onChange) this.onChange(this.strokes);
  }

  render() {
    if (this.raf) return;
    this.raf = requestAnimationFrame(() => {
      this.raf = 0;
      this.am.setDraw(this.strokes.filter((s) => s.length > 1).map((s) => ({ type: 'Feature', geometry: { type: 'LineString', coordinates: s }, properties: { ok: 'pending' } })));
    });
  }
}
