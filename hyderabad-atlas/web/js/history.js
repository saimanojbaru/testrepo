// History: Wikipedia summaries fetched on the phone (cached), and the
// time-travel controller that steps the map through the city's eras.

const WIKI_KEY = 'hyd-wiki-v1';
const WIKI_TTL = 30 * 864e5;

export async function wikiSummary(title) {
  if (!title) return null;
  let cache = {};
  try { cache = JSON.parse(localStorage.getItem(WIKI_KEY) || '{}'); } catch { /* corrupted cache */ }
  const hit = cache[title];
  if (hit && Date.now() - hit.t < WIKI_TTL) return hit;
  try {
    const res = await fetch('https://en.wikipedia.org/api/rest_v1/page/summary/' + encodeURIComponent(title), { headers: { Accept: 'application/json' } });
    if (!res.ok) return null;
    const j = await res.json();
    if (!j.extract || j.type === 'disambiguation') return null;
    const out = { extract: j.extract, url: (j.content_urls && j.content_urls.mobile && j.content_urls.mobile.page) || `https://en.wikipedia.org/wiki/${encodeURIComponent(title)}`, t: Date.now() };
    cache[title] = out;
    // keep the cache small: drop the oldest entries beyond 250
    const keys = Object.keys(cache);
    if (keys.length > 250) keys.sort((a, b) => cache[a].t - cache[b].t).slice(0, keys.length - 250).forEach((k) => delete cache[k]);
    try { localStorage.setItem(WIKI_KEY, JSON.stringify(cache)); } catch { /* storage full */ }
    return out;
  } catch {
    return null; // offline
  }
}

// Which era a year falls into.
export function eraOf(eras, year) {
  return eras.find((e) => year >= e.start && year <= e.end) || eras[eras.length - 1];
}

// All timeline events in order, flattened.
export function allEvents(eras) {
  return eras.flatMap((e) => e.events.map((ev) => ({ ...ev, era: e }))).sort((a, b) => a.year - b.year);
}

// Time-travel panel: era chips, a year slider, the event card, and autoplay.
export class TimeTravel {
  constructor(app) {
    this.app = app;
    this.A = app.A;
    this.events = allEvents(this.A.eras);
    this.year = 1591;
    this.playing = false;
    this.timer = 0;
  }

  mount(el) {
    const A = this.A;
    el.innerHTML = `
      <div class="tt-eras hscroll">${A.eras.map((e, i) => `<button class="tt-era" data-i="${i}"><b>${e.start}</b>${e.title}</button>`).join('')}</div>
      <div class="tt-card glass">
        <div class="tt-top"><div class="tt-year">1591</div><button class="btn primary small tt-play">Play</button></div>
        <input class="tt-slider" type="range" min="${A.eras[0].start}" max="${A.eras.at(-1).end}" step="1" value="1591">
        <div class="tt-event"></div>
        <div class="tt-nav"><button class="btn ghost small" data-step="-1">‹ Earlier</button><button class="btn ghost small" data-step="1">Later ›</button></div>
      </div>`;
    this.el = el;
    this.slider = el.querySelector('.tt-slider');
    this.slider.oninput = () => { this.stop(); this.setYear(+this.slider.value, { fly: false }); };
    this.slider.onchange = () => this.flyToNearest();
    el.querySelectorAll('.tt-era').forEach((b) => (b.onclick = () => {
      this.stop();
      const era = A.eras[+b.dataset.i];
      const ev = this.events.find((e) => e.era === era);
      this.setYear(ev ? ev.year : era.start, { fly: true, event: ev });
    }));
    el.querySelectorAll('[data-step]').forEach((b) => (b.onclick = () => { this.stop(); this.step(+b.dataset.step); }));
    el.querySelector('.tt-play').onclick = () => (this.playing ? this.stop() : this.play());
    this.setYear(this.year, { fly: false });
  }

  current() {
    let ev = null;
    for (const e of this.events) if (e.year <= this.year) ev = e;
    return ev || this.events[0];
  }

  step(dir) {
    const cur = this.current();
    const i = this.events.indexOf(cur);
    const next = this.events[Math.max(0, Math.min(this.events.length - 1, i + dir))];
    this.setYear(next.year, { fly: true, event: next });
  }

  flyToNearest() { const ev = this.current(); if (ev) this.focus(ev); }

  setYear(year, { fly = true, event = null } = {}) {
    this.year = year;
    const A = this.A;
    const ev = event || this.current();
    const era = ev ? ev.era : A.eras[0];
    this.slider.value = year;
    this.el.querySelector('.tt-year').textContent = year;
    this.el.querySelectorAll('.tt-era').forEach((b) => b.classList.toggle('on', A.eras[+b.dataset.i] === era));
    const evs = era.events.filter((e) => e.year <= year);
    const shown = ev && ev.year <= year ? ev : evs.at(-1);
    this.el.querySelector('.tt-event').innerHTML = shown
      ? `<div class="kicker">${era.title} · ${era.start}–${era.end}</div><div class="tt-text"><b>${shown.year}</b> ${escapeHtml(shown.text)}</div>`
      : `<div class="kicker">${era.title} · ${era.start}–${era.end}</div><div class="tt-text">${escapeHtml(era.sub)}</div>`;
    this.app.map.setTimeYear(year, shown && shown.id);
    this.app.setHeadlineTime(year, era);
    if (fly && shown) this.focus(shown);
  }

  focus(ev) {
    const it = ev.id && this.A.get(ev.id);
    if (it) this.app.map.fitItem(it, { zoom: 14.2, pitch: this.app.map.pitchForZoom(14.2), duration: 1600, padding: { bottom: 330 } });
    else this.app.map.home({ duration: 1400 });
  }

  play() {
    this.playing = true;
    this.el.querySelector('.tt-play').textContent = 'Pause';
    let i = this.events.indexOf(this.current());
    if (i >= this.events.length - 1) i = -1;
    const tick = () => {
      if (!this.playing) return;
      i++;
      if (i >= this.events.length) { this.stop(); return; }
      this.setYear(this.events[i].year, { fly: true, event: this.events[i] });
      this.timer = setTimeout(tick, 3600);
    };
    tick();
  }

  stop() {
    this.playing = false;
    clearTimeout(this.timer);
    const b = this.el && this.el.querySelector('.tt-play');
    if (b) b.textContent = 'Play';
  }
}

function escapeHtml(s) { return String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }
