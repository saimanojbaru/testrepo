// Question generators + quiz runner.
import {
  shuffle, pick, pointOf, crumb, km, bearing, compassOf, COMPASS_LONG,
  pointInGeom, distToGeomKm, KIND_LABEL,
} from './data.js';
import { burst, buzz, toast } from './ui.js';

const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
const em = (s) => `<em>${esc(s)}</em>`;
const TOL = { locality: 1.3, landmark: 1.2, station: 0.7, stop: 1.3 };
const DEPTH_OF = { corp: 'corp', zone: 'zone', circle: 'circle', locality: 'locality', landmark: 'landmark', station: 'metro' };

// ------------------------------------------------------------------ generators
// Each returns a question or null if it doesn't apply to the item.
export function makeGenerators(A) {
  const kindList = (k) => A.lists[k];
  const others = (it, n, pool) => {
    pool = (pool || kindList(it.kind)).filter((x) => x.id !== it.id && x.name !== it.name);
    const near = (it.near || []).map((id) => A.get(id)).filter((x) => x && x.id !== it.id);
    let out = shuffle(near).slice(0, n);
    if (out.length < n) {
      const p = pointOf(it);
      const rest = pool.filter((x) => !out.includes(x)).sort((a, b) => km(pointOf(a), p) - km(pointOf(b), p));
      out = out.concat(shuffle(rest.slice(0, n * 3)).slice(0, n - out.length));
    }
    return out;
  };
  const opts = (right, wrong) => shuffle([right, ...wrong]).map((x) => ({ label: x.name, value: x.id }));
  const pointSpec = (it, kind = 'quiz', text = it.name) => ({ id: 'q-' + it.id, lngLat: pointOf(it), text, kind, dot: true, force: true, priority: 999 });
  const sampleIn = (parentId, kind) => {
    const p = A.get(parentId);
    if (kind === 'locality') {
      const locs = p.kind === 'circle' ? p.children : p.kind === 'zone' ? p.children.flatMap((c) => A.get(c).children)
        : A.localities.filter((l) => l.corp === p.id).map((l) => l.id);
      return A.get(pick(locs));
    }
    if (kind === 'circle') return A.get(pick(p.kind === 'zone' ? p.children : A.circles.filter((c) => c.corp === p.id).map((c) => c.id)));
    return null;
  };

  const G = {};

  // ---- name a highlighted region
  G.name = (it) => {
    if (!it.geom) return G.namePoint(it);
    const n = it.kind === 'corp' ? 2 : 3;
    return {
      kicker: 'Name it', prompt: `What is this ${KIND_LABEL[it.kind].toLowerCase()}?`,
      sub: it.kind === 'corp' ? '' : crumb(A, it).slice(0, 1).join(' · '),
      mode: 'choice', options: opts(it, others(it, n)), answer: it.id, ids: [it.id],
      setup(m) {
        m.setDepth(it.kind, { animate: false });
        m.setLabelOverride([]);
        m.select(it, { fly: false });
        if (it.kind === 'corp' || it.kind === 'zone') m.home({ duration: 1100 });
        else m.fitItem(A.get(it.zone), { zoomAdj: 0.1, duration: 1100 });
      },
      reveal(m) { m.setLabelOverride([{ ...pointSpec(it, 'good'), dot: false }]); },
    };
  };

  // ---- name a highlighted point
  G.namePoint = (it) => {
    const pool = kindList(it.kind);
    const p = pointOf(it);
    const near = pool.filter((x) => x.id !== it.id && x.name !== it.name).sort((a, b) => km(pointOf(a), p) - km(pointOf(b), p));
    const wrong = shuffle(near.slice(0, 7)).slice(0, 3);
    return {
      kicker: 'Name it', prompt: `Which ${KIND_LABEL[it.kind].toLowerCase()} is marked?`,
      sub: it.circle ? `${A.get(it.zone).name} zone · ${A.get(it.corp).name}` : '',
      mode: 'choice', options: opts(it, wrong), answer: it.id, ids: [it.id],
      setup(m) {
        m.setDepth(it.kind === 'station' ? 'metro' : 'landmark', { animate: false });
        m.setLabelOverride([pointSpec(it, 'quiz', '?')]);
        m.select(it, { fly: false });
        m.fitItem(it, { zoom: it.kind === 'station' ? 12.6 : 12.3, duration: 1200 });
      },
      reveal(m) { m.setLabelOverride([pointSpec(it, 'good')]); },
    };
  };

  // ---- tap the map to find something
  G.find = (it) => (it.geom ? findRegion(it) : G.findPoint(it));
  const findRegion = (it) => ({
    kicker: 'Find it', prompt: `Tap ${em(it.name)}`,
    sub: it.kind === 'corp' ? 'Tap anywhere inside it' : `${KIND_LABEL[it.kind]} · ${crumb(A, it).join(' · ')}`,
    mode: 'tap', ids: [it.id],
    setup(m) {
      m.setDepth(it.kind === 'locality' ? 'locality' : it.kind, { animate: false });
      m.clearSelection(false);
      m.setLabelOverride([]);
      if (it.kind === 'circle') m.fitItem(A.get(it.corp), { zoomAdj: 0.05, duration: 1100 });
      else m.home({ duration: 1100 });
    },
    check(ll) {
      if (pointInGeom(ll, it.geom)) return { correct: true, score: 100 };
      const d = distToGeomKm(ll, it.geom);
      return { correct: false, score: Math.max(0, Math.round(60 - d * 12)), msg: `${d.toFixed(1)} km off.` };
    },
    reveal(m, res, ll) {
      m.setQuiz([{ type: 'Feature', geometry: it.geom, properties: { color: '#5be49b' } }]);
      const specs = [{ ...pointSpec(it, 'good'), dot: false }];
      if (ll && !res.correct) specs.push({ id: 'tap', lngLat: ll, text: '', kind: 'tap', dot: true, force: true });
      m.setLabelOverride(specs);
    },
  });

  G.findPoint = (it) => {
    const tol = TOL[it.kind] || 1.2;
    const zone = it.zone && A.get(it.zone);
    const inCity = it.inCity !== false && zone;
    // faint reference names so you can navigate: circles for areas, zones for landmarks
    const refItems = it.kind === 'locality' ? zone.children.map((id) => A.get(id))
      : it.kind === 'landmark' ? A.zones : [];
    const refs = refItems.map((r) => ({ id: 'r-' + r.id, lngLat: r.label, text: r.name, kind: 'ref', priority: 1 }));
    return {
      kicker: 'Pin drop', prompt: `Find ${em(it.name)}`,
      sub: inCity ? `${zone.name} zone · ${A.get(it.corp).name}` : 'Somewhere around the city',
      mode: 'tap', ids: [it.id], refs,
      setup(m) {
        m.setDepth(it.kind === 'station' ? 'metro' : 'landmark', { animate: false });
        m.clearSelection(false);
        m.setLabelOverride(refs);
        if (inCity && it.kind !== 'station') m.fitItem(zone, { zoomAdj: 0.05, duration: 1100 });
        else m.home({ duration: 1100 });
      },
      check(ll) {
        const d = km(ll, pointOf(it));
        const inCell = it.geom && pointInGeom(ll, it.geom);
        if (d <= tol || inCell) return { correct: true, score: d < tol / 2 ? 100 : 85, msg: `${d.toFixed(1)} km from the centre.` };
        return { correct: false, score: Math.max(0, Math.round(70 - d * 14)), msg: `${d.toFixed(1)} km off.` };
      },
      reveal(m, res, ll) {
        const specs = [pointSpec(it, 'good'), ...refs];
        const feats = [];
        if (ll && !res.correct) {
          specs.push({ id: 'tap', lngLat: ll, text: '', kind: 'tap', dot: true, force: true });
          feats.push({ type: 'Feature', geometry: { type: 'LineString', coordinates: [ll, pointOf(it)] }, properties: { color: '#ff5a6e' } });
        }
        m.setQuiz(feats);
        m.setLabelOverride(specs);
        if (ll && !res.correct) m.fitItem({ kind: 'corridor', stops: [{ lng: ll[0], lat: ll[1] }, { lng: pointOf(it)[0], lat: pointOf(it)[1] }] }, { maxZoom: 13, zoomAdj: -0.2, duration: 900 });
      },
    };
  };
  G.findStation = (it) => (it.kind === 'station' ? G.findPoint(it) : null);

  // ---- which parent is this in?
  const whichParent = (it, parentKind, subject) => {
    const parent = A.get(subject[parentKind]);
    let pool;
    if (parentKind === 'corp') pool = A.corporations.filter((c) => c.id !== parent.id);
    else pool = others(parent, 3);
    const wrong = parentKind === 'corp' ? pool : pool.slice(0, 3);
    return {
      kicker: `Which ${KIND_LABEL[parentKind].toLowerCase()}?`,
      prompt: `${em(subject.name)} is in which ${KIND_LABEL[parentKind].toLowerCase()}?`,
      sub: subject.kind === 'landmark' ? 'Landmark' : KIND_LABEL[subject.kind],
      mode: 'choice', options: opts(parent, wrong), answer: parent.id, ids: [it.id],
      setup(m) {
        m.setDepth('blank', { animate: false });
        m.clearSelection(false);
        m.setLabelOverride([pointSpec(subject)]);
        if (subject.geom && subject.kind !== 'locality') m.select(subject, { fly: false });
        if (parentKind === 'corp') m.home({ duration: 1100 });
        else m.fitItem(A.get(parentKind === 'zone' ? parent.corp : parent.zone), { zoomAdj: 0.1, duration: 1100 });
      },
      reveal(m) {
        m.setQuiz([{ type: 'Feature', geometry: parent.geom, properties: { color: parent.color } }]);
        m.setLabelOverride([pointSpec(subject, 'good'), { id: 'p-' + parent.id, lngLat: parent.label, text: parent.name, kind: parentKind === 'corp' ? 'corp' : 'zone', force: true, priority: 900 }]);
      },
    };
  };
  G.whichCorp = (it) => {
    const subject = it.kind === 'corp' ? sampleIn(it.id, 'locality') : it;
    return subject && subject.corp ? whichParent(it, 'corp', subject) : null;
  };
  G.whichZone = (it) => {
    const subject = it.kind === 'zone' ? sampleIn(it.id, Math.random() < 0.5 ? 'circle' : 'locality') : it;
    return subject && subject.zone ? whichParent(it, 'zone', subject) : null;
  };
  G.whichCircle = (it) => {
    const subject = it.kind === 'circle' ? sampleIn(it.id, 'locality') : it;
    return subject && subject.circle ? whichParent(it, 'circle', subject) : null;
  };

  // ---- neighbours
  G.neighbour = (it) => {
    if (!it.near || !it.near.length) return null;
    const right = A.get(pick(it.near));
    const banned = new Set([it.id, ...it.near]);
    const p = pointOf(it);
    const far = kindList(it.kind).filter((x) => !banned.has(x.id)).sort((a, b) => km(pointOf(a), p) - km(pointOf(b), p));
    const wrong = shuffle(far.slice(0, 8)).slice(0, 3);
    if (wrong.length < 2) return null;
    return {
      kicker: 'Neighbours', prompt: `Which of these shares a border with ${em(it.name)}?`, sub: KIND_LABEL[it.kind],
      mode: 'choice', options: opts(right, wrong), answer: right.id, ids: [it.id],
      setup(m) {
        m.setDepth(it.kind, { animate: false });
        m.setLabelOverride([{ ...pointSpec(it), dot: false }]);
        m.select(it, { fly: false });
        m.fitItem(A.get(it.corp) && it.kind !== 'zone' ? A.get(it.corp) : { geom: A.domain }, { duration: 1100 });
      },
      reveal(m) {
        m.setQuiz([{ type: 'Feature', geometry: right.geom, properties: { color: '#f2cf4a' } }]);
        m.setLabelOverride([{ ...pointSpec(it), dot: false }, { ...pointSpec(right, 'good'), dot: false }]);
      },
    };
  };

  // ---- compass
  const REFS = ['Charminar', 'Hussain Sagar', 'Secunderabad Junction', 'Cyber Towers', 'Golconda Fort', 'Rajiv Gandhi Intl. Cricket Stadium']
    .map((n) => A.landmarks.find((m) => m.name === n)).filter(Boolean);
  G.compass = (it) => {
    if (it.kind === 'corp' || it.kind === 'zone' || it.kind === 'circle') it = A.get(it.children[0]) || it;
    const p = pointOf(it);
    const cands = REFS.filter((r) => r.id !== it.id && km(pointOf(r), p) > 3);
    if (!cands.length) return null;
    const ref = pick(cands);
    const dir = compassOf(bearing(pointOf(ref), p));
    return {
      kicker: 'Compass', prompt: `From ${esc(ref.name)}, which way is ${em(it.name)}?`, sub: `${km(pointOf(ref), p).toFixed(0)} km away`,
      mode: 'compass', answer: dir, ids: [it.id],
      setup(m) {
        m.setDepth('blank', { animate: false });
        m.clearSelection(false);
        m.setLabelOverride([{ ...pointSpec(ref, 'locality'), dot: true }]);
        m.fitItem({ kind: 'corridor', stops: [{ lng: pointOf(ref)[0], lat: pointOf(ref)[1] }, { lng: p[0], lat: p[1] }] }, { bearing: 0, maxZoom: 12, zoomAdj: -0.5, duration: 1100 });
      },
      check(v) {
        const order = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
        const diff = Math.min((order.indexOf(v) - order.indexOf(dir) + 8) % 8, (order.indexOf(dir) - order.indexOf(v) + 8) % 8);
        if (diff === 0) return { correct: true, score: 100 };
        return { correct: false, score: diff === 1 ? 40 : 0, msg: `It's ${COMPASS_LONG[dir]}${diff === 1 ? ' — close!' : '.'}` };
      },
      reveal(m) {
        m.setQuiz([{ type: 'Feature', geometry: { type: 'LineString', coordinates: [pointOf(ref), p] }, properties: { color: '#5be49b', w: 3 } }]);
        m.setLabelOverride([{ ...pointSpec(ref, 'locality') }, pointSpec(it, 'good')]);
      },
    };
  };

  // ---- metro
  const lineOfStation = (st) => A.get(st.lines[0]);
  G.lineOf = (it) => {
    if (it.kind !== 'station' || it.lines.length > 1) return null;
    const line = lineOfStation(it);
    return {
      kicker: 'Metro', prompt: `Which line stops at ${em(it.name)}?`, sub: '',
      mode: 'choice', options: A.metro.map((l) => ({ label: l.name, value: l.id, color: l.color })), answer: line.id, ids: [it.id],
      setup(m) {
        m.setDepth('blank', { animate: false });
        m.clearSelection(false);
        m.setLabelOverride([pointSpec(it)]);
        m.fitItem(it, { zoom: 11.6, duration: 1000 });
      },
      reveal(m) {
        m.setDepth('metro', { animate: false });
        m.dimLines(line.id);
        m.setLabelOverride([pointSpec(it, 'good')]);
      },
    };
  };

  G.nextStop = (it) => {
    if (it.kind !== 'station') return null;
    const line = A.get(pick(it.lines));
    const ids = line.stationIds;
    const i = ids.indexOf(it.id);
    const fwd = i < ids.length - 1 && (i === 0 || Math.random() < 0.6);
    const j = fwd ? i + 1 : i - 1;
    if (j < 0 || j >= ids.length) return null;
    const right = A.get(ids[j]);
    const towards = A.get(fwd ? ids.at(-1) : ids[0]);
    const pool = ids.filter((x) => x !== it.id && x !== right.id).map((x) => A.get(x));
    const near = pool.sort((a, b) => Math.abs(ids.indexOf(a.id) - j) - Math.abs(ids.indexOf(b.id) - j));
    const wrong = shuffle(near.slice(0, 5)).slice(0, 3);
    return {
      kicker: line.name, prompt: `Towards ${esc(towards.name)}: after ${em(it.name)} comes…`, sub: 'Next station',
      mode: 'choice', options: opts(right, wrong), answer: right.id, ids: [it.id, right.id],
      setup(m) {
        m.setDepth('metro', { animate: false });
        m.clearSelection(false);
        m.dimLines(line.id);
        m.setLabelOverride([pointSpec(it), { ...pointSpec(towards, 'station'), dotColor: line.color }]);
        m.fitItem(line, { duration: 1100 });
      },
      reveal(m) { m.setLabelOverride([pointSpec(it, 'station'), pointSpec(right, 'good')]); },
    };
  };

  G.interchange = () => {
    const st = pick(A.interchanges);
    const [a, b] = st.lines.map((l) => A.get(l));
    const pool = [...a.stationIds, ...b.stationIds].filter((x) => x !== st.id).map((x) => A.get(x));
    const wrong = shuffle(pool).slice(0, 3);
    return {
      kicker: 'Interchange', prompt: `Where do you change between the ${esc(a.name)} and ${esc(b.name)}?`, sub: '',
      mode: 'choice', options: opts(st, wrong), answer: st.id, ids: [st.id],
      setup(m) {
        m.setDepth('metro', { animate: false });
        m.clearSelection(false);
        m.dimLines(null);
        m.setLabelOverride([]);
        m.home({ duration: 1000 });
      },
      reveal(m) { m.setLabelOverride([pointSpec(st, 'good')]); },
    };
  };

  // ---- ordering (metro lines and road corridors)
  G.order = (it) => {
    let line, stops, title, from, to, ids;
    if (it.kind === 'station' || it.kind === 'line') {
      line = it.kind === 'line' ? it : A.get(pick(it.lines));
      const all = line.stationIds;
      const n = Math.min(5, all.length);
      const span = Math.max(n, Math.min(all.length, n + 6));
      const s0 = Math.floor(Math.random() * (all.length - span + 1));
      const idx = shuffle([...Array(span).keys()].slice(1, span - 1)).slice(0, n - 2).concat([0, span - 1]).sort((a, b) => a - b);
      stops = idx.map((k) => A.get(all[s0 + k]));
      if (Math.random() < 0.5) stops.reverse();
      title = line.name;
      ids = stops.map((s) => s.id);
    } else if (it.kind === 'corridor') {
      stops = it.stops.map((s) => ({ ...A.get(s.id), name: s.name }));
      if (Math.random() < 0.4) stops = stops.slice().reverse();
      title = it.name;
      ids = [it.id];
    } else return null;
    from = stops[0]; to = stops.at(-1);
    return {
      kicker: 'Route builder', prompt: `${esc(title)}: tap the stops in order from ${em(from.name)}`,
      sub: `Ends at ${to.name}`,
      mode: 'order', options: shuffle(stops.slice(1).map((s) => ({ label: s.name, value: s.id }))), answer: stops.slice(1).map((s) => s.id), ids,
      first: from,
      setup(m) {
        m.clearSelection(false);
        if (line) { m.setDepth('metro', { animate: false }); m.dimLines(line.id); m.fitItem(line, { duration: 1100 }); }
        else { m.setDepth('routes', { animate: false }); m.dimLines(it.id); m.fitItem(it, { zoomAdj: -0.1, duration: 1100 }); }
        m.setLabelOverride([pointSpec(from)]);
      },
      check(seq) {
        const ans = this.answer;
        let pairs = 0, good = 0;
        for (let i = 0; i < seq.length; i++) for (let j = i + 1; j < seq.length; j++) {
          pairs++; if (ans.indexOf(seq[i]) < ans.indexOf(seq[j])) good++;
        }
        const exact = seq.every((v, i) => v === ans[i]);
        return exact ? { correct: true, score: 100 } : { correct: false, score: Math.round((good / pairs) * 70), msg: `Right order: ${stops.map((s) => s.name).join(' → ')}` };
      },
      reveal(m) {
        m.setLabelOverride(stops.map((s, i) => ({ id: 'o-' + s.id, lngLat: pointOf(s), text: `${i + 1}. ${s.name}`, kind: i ? 'good' : 'quiz', dot: true, force: true, priority: 900 - i })));
      },
    };
  };

  G.whichRoad = (it) => {
    if (it.kind !== 'corridor') return null;
    const a = it.stops[0], b = it.stops.at(-1);
    const wrong = shuffle(A.corridors.filter((r) => r.id !== it.id)).slice(0, 3);
    return {
      kicker: 'Which road?', prompt: `Which route links ${em(a.name)} and ${em(b.name)}?`, sub: it.desc,
      mode: 'choice', options: opts(it, wrong), answer: it.id, ids: [it.id],
      setup(m) {
        m.setDepth('blank', { animate: false });
        m.clearSelection(false);
        m.setLabelOverride([pointSpec({ ...a, kind: 'stop', id: 'a' }), pointSpec({ ...b, kind: 'stop', id: 'b' })]);
        m.fitItem(it, { zoomAdj: -0.3, duration: 1100 });
      },
      reveal(m) {
        m.setDepth('routes', { animate: false });
        m.dimLines(it.id);
        m.setLabelOverride(it.stops.map((s, i) => ({ id: 'o-' + s.id, lngLat: [s.lng, s.lat], text: s.name, kind: i === 0 || i === it.stops.length - 1 ? 'good' : 'stop', dot: true, force: true, priority: 900 - i })));
      },
    };
  };

  return G;
}

// ------------------------------------------------------------------ runner
const AUTO_GAMES = {
  corp: ['name', 'find', 'whichCorp'],
  zone: ['name', 'find', 'neighbour', 'whichZone'],
  circle: ['name', 'find', 'whichCircle', 'neighbour'],
  locality: ['findPoint', 'namePoint', 'whichCircle', 'whichCorp', 'compass'],
  landmark: ['findPoint', 'namePoint', 'whichCorp', 'compass'],
  station: ['nextStop', 'lineOf', 'findStation', 'order'],
  line: ['order'],
  corridor: ['order', 'whichRoad'],
};

export class Quiz {
  constructor(app) {
    this.app = app;
    this.A = app.A;
    this.G = makeGenerators(app.A);
    this.root = document.getElementById('quiz');
    this.card = this.root.querySelector('.q-card');
    this.answers = this.root.querySelector('.q-answers');
    this.feedback = this.root.querySelector('.q-feedback');
    this.root.querySelector('[data-act="close"]').onclick = () => this.close();
  }

  // Build a question list from items + game types, weighted towards weak items.
  build(ids, games, count) {
    const P = this.app.P;
    const order = P.weighted(ids, ids.length);
    const qs = [];
    const auto = games === 'auto';
    let gi = 0, tries = 0;
    const gameSeq = auto ? [] : shuffle(games);
    while (qs.length < count && tries < count * 6) {
      tries++;
      const id = order[(tries - 1) % order.length];
      const item = this.A.get(id);
      if (!item) continue;
      const g = auto ? pick(AUTO_GAMES[item.kind] || ['namePoint']) : gameSeq[gi++ % gameSeq.length];
      const q = this.G[g] && this.G[g](item);
      if (!q) continue;
      q.key = g + ':' + q.ids.join(',') + ':' + q.answer;
      if (!qs.some((x) => x.key === q.key)) { q.game = g; qs.push(q); }
    }
    return qs;
  }

  start({ title, ids, games, count = 10, onDone, key }) {
    this.questions = this.build(ids, games, count);
    if (!this.questions.length) { toast('Nothing to quiz yet'); return; }
    this.title = title; this.onDone = onDone; this.key = key;
    this.i = 0; this.score = 0; this.right = 0; this.misses = [];
    this.app.setMode('quiz');
    this.app.map.setNeutral(true);
    this.root.hidden = false;
    this.show();
  }

  show() {
    const q = this.q = this.questions[this.i];
    const m = this.app.map;
    m.setQuiz([]);
    m.tapHandler = null;
    this.root.querySelector('.qbar-fill').style.width = `${(this.i / this.questions.length) * 100}%`;
    this.root.querySelector('.qscore').textContent = this.score;
    this.card.hidden = false;
    this.card.style.animation = 'none'; void this.card.offsetWidth; this.card.style.animation = '';
    this.card.querySelector('.kicker').textContent = `${q.kicker} · ${this.i + 1}/${this.questions.length}`;
    this.card.querySelector('.q-prompt').innerHTML = q.prompt;
    this.card.querySelector('.q-sub').textContent = q.sub || '';
    this.feedback.hidden = true;
    this.answers.className = 'q-answers';
    this.answers.innerHTML = '';
    this.root.querySelector('.q-result')?.remove();
    q.setup(m);

    if (q.mode === 'choice') {
      if (q.options.length === 3) this.answers.classList.add('one');
      q.options.forEach((o) => {
        const b = document.createElement('button');
        b.className = 'opt';
        b.innerHTML = (o.color ? `<span class="chip-dot" style="display:inline-block;width:9px;height:9px;border-radius:50%;background:${o.color};margin-right:8px"></span>` : '') + esc(o.label);
        b.onclick = () => this.answerChoice(o.value, b);
        this.answers.appendChild(b);
      });
    } else if (q.mode === 'compass') {
      const grid = document.createElement('div');
      grid.className = 'compass';
      ['NW', 'N', 'NE', 'W', '', 'E', 'SW', 'S', 'SE'].forEach((d) => {
        const b = document.createElement(d ? 'button' : 'div');
        if (d) { b.className = 'opt'; b.textContent = d; b.onclick = () => this.answerValue(d, b); }
        else { b.className = 'mid'; b.innerHTML = '<svg viewBox="0 0 24 24" style="width:26px;height:26px;color:#5be49b"><path d="M12 3l3 9-3 9-3-9z"/></svg>'; }
        grid.appendChild(b);
      });
      this.answers.appendChild(grid);
    } else if (q.mode === 'tap') {
      this.answers.classList.add('one');
      this.answers.innerHTML = '<div class="tap-hint">Tap the map to drop a pin</div>';
      m.tapHandler = (ll) => {
        this.pending = ll;
        m.setLabelOverride([{ id: 'tap', lngLat: ll, text: '', kind: 'quiz', dot: true, force: true }, ...(q.refs || [])]);
        if (!this.answers.querySelector('.btn')) {
          this.answers.innerHTML = '<div class="tap-hint">Tap again to move the pin</div>';
          const b = document.createElement('button');
          b.className = 'btn primary'; b.textContent = 'Lock in';
          b.onclick = () => this.answerValue(this.pending);
          this.answers.appendChild(b);
        }
      };
    } else if (q.mode === 'order') {
      this.seq = [];
      const btns = [];
      q.options.forEach((o) => {
        const b = document.createElement('button');
        b.className = 'opt';
        b.dataset.v = o.value;
        b.textContent = o.label;
        b.dataset.label = o.label;
        b.onclick = () => {
          if (this.seq.includes(o.value)) return;
          this.seq.push(o.value);
          b.innerHTML = `<span class="n">${this.seq.length + 1}</span>${esc(o.label)}`;
          b.classList.add('dim');
          buzz(8);
          if (this.seq.length === q.options.length) this.answerValue(this.seq.slice());
        };
        btns.push(b);
        this.answers.appendChild(b);
      });
      const reset = document.createElement('button');
      reset.className = 'btn ghost small'; reset.textContent = 'Reset'; reset.style.gridColumn = 'span 2';
      reset.onclick = () => { this.seq = []; btns.forEach((b) => { b.textContent = b.dataset.label; b.classList.remove('dim'); }); };
      this.answers.appendChild(reset);
    }
  }

  answerChoice(v, btn) {
    const q = this.q;
    const correct = v === q.answer;
    this.answers.querySelectorAll('.opt').forEach((b, i) => {
      b.disabled = true;
      const o = q.options[i];
      if (o.value === q.answer) b.classList.add('good');
      else if (b === btn) b.classList.add('bad');
      else b.classList.add('dim');
    });
    const right = q.options.find((o) => o.value === q.answer);
    this.resolve({ correct, score: correct ? 100 : 0, msg: correct ? '' : `It's ${right.label}.` }, null, btn);
  }

  answerValue(v, btn) {
    const q = this.q;
    const res = q.check(v);
    if (q.mode === 'compass') {
      this.answers.querySelectorAll('.opt').forEach((b) => {
        b.disabled = true;
        if (b.textContent === q.answer) b.classList.add('good');
        else if (b === btn) b.classList.add('bad');
        else b.classList.add('dim');
      });
    }
    this.app.map.tapHandler = null;
    this.resolve(res, q.mode === 'tap' ? v : null, btn);
  }

  resolve(res, ll, btn) {
    const q = this.q;
    const P = this.app.P;
    q.ids.forEach((id) => P.record(id, res.correct));
    this.score += res.score;
    if (res.correct) this.right++;
    else this.misses.push(q);
    q.reveal(this.app.map, res, ll);
    this.root.querySelector('.qscore').textContent = this.score;

    if (res.correct) {
      buzz(15);
      const r = (btn || this.card).getBoundingClientRect();
      burst(r.left + r.width / 2, r.top + r.height / 2, 26);
    } else buzz([30, 50, 30]);

    const item = this.A.get(q.ids[0]);
    const fact = item && item.fact ? item.fact : '';
    this.feedback.className = `q-feedback ${res.correct ? 'good' : 'bad'}`;
    this.feedback.innerHTML = `<div class="fh"><span>${res.correct ? pick(['Correct!', 'Spot on!', 'Nailed it!', 'Yes!']) : res.score > 30 ? 'Close!' : 'Not quite'}</span><span>+${res.score}</span></div>`
      + `<div class="fb">${esc([res.msg, fact].filter(Boolean).join(' '))}</div>`;
    this.feedback.hidden = false;

    const next = document.createElement('button');
    next.className = 'btn primary';
    next.textContent = this.i === this.questions.length - 1 ? 'See results' : 'Next';
    next.onclick = () => { this.i++; this.i < this.questions.length ? this.show() : this.finish(); };
    if (q.mode === 'tap' || q.mode === 'order') { this.answers.className = 'q-answers one'; this.answers.innerHTML = ''; }
    else { this.answers.classList.add('locked'); }
    const holder = document.createElement('div');
    holder.style.gridColumn = '1 / -1'; holder.style.display = 'flex';
    holder.appendChild(next);
    this.answers.appendChild(holder);
    P.save();
  }

  finish() {
    const n = this.questions.length;
    const pct = Math.round((this.right / n) * 100);
    const P = this.app.P;
    P.addXp(Math.round(this.score / 10));
    if (this.key) this.key.lesson ? P.finishLesson(this.key.lesson, pct) : P.finishGame(this.key.game, this.score);
    P.save();
    this.app.map.tapHandler = null;
    this.app.map.setQuiz([]);
    this.app.map.setLabelOverride(null);
    this.app.map.clearSelection();
    this.app.map.setNeutral(false);
    this.root.querySelector('.qbar-fill').style.width = '100%';
    this.card.hidden = true;
    this.feedback.hidden = true;
    this.answers.innerHTML = '';
    const pass = pct >= 70;
    const box = document.createElement('div');
    box.className = 'glass card q-result';
    box.style.cssText = 'margin:auto 12px 14px;padding:18px;border-radius:22px;animation:rise .45s var(--ease)';
    box.innerHTML = `<div class="result">
        <div class="kicker" style="font-size:10px;font-weight:800;letter-spacing:.18em;color:var(--green);text-transform:uppercase">${esc(this.title)}</div>
        <div class="score">${this.right}<em>/${n}</em></div>
        <div class="rl">${pct}% correct · ${this.score} points · +${Math.round(this.score / 10)} XP</div>
        <div class="rl" style="color:${pass ? 'var(--green)' : 'var(--yellow)'};margin-top:10px;font-weight:800">${pass ? (this.key?.lesson ? 'Lesson complete — next one unlocked' : 'Great run!') : 'Score 70% to complete this lesson'}</div>
      </div>
      ${this.misses.length ? `<div class="ic-sec">Review</div><div class="chips miss">${[...new Set(this.misses.map((q) => q.ids[0]))].slice(0, 8).map((id) => `<span class="chip">${esc(this.A.get(id)?.name || '')}</span>`).join('')}</div>` : ''}
      <div class="ic-actions"><button class="btn ghost" data-r="retry">Retry</button><button class="btn primary" data-r="done">Continue</button></div>`;
    this.root.querySelector('.q-bottom').before(box);
    box.querySelector('[data-r="retry"]').onclick = () => { box.remove(); this.restart(); };
    box.querySelector('[data-r="done"]').onclick = () => { box.remove(); this.close(true); };
    if (pass) burst(window.innerWidth / 2, window.innerHeight * 0.55, 90);
    this.app.map.home();
    this.app.map.setDepth('corp');
  }

  restart() {
    const qs = this.questions;
    this.questions = this.build([...new Set(qs.flatMap((q) => q.ids))], [...new Set(qs.map((q) => q.game))], qs.length);
    this.i = 0; this.score = 0; this.right = 0; this.misses = [];
    this.show();
  }

  close(done = false) {
    this.root.querySelector('.q-result')?.remove();
    this.root.hidden = true;
    const m = this.app.map;
    m.setNeutral(false);
    m.tapHandler = null;
    m.setQuiz([]);
    m.setLabelOverride(null);
    m.clearSelection();
    const cb = this.onDone;
    this.onDone = null;
    this.app.afterOverlay(done);
    if (cb) cb(done);
  }
}
