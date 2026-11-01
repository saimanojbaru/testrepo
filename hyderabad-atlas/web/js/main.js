import { loadAtlas, crumb, KIND_LABEL, km, pointOf, pathOf, lengthKm } from './data.js';
import { isLowEndDevice } from './config.js';
import { LEVELS } from './draw.js';
import { AtlasMap, DEPTHS } from './map.js';
import { Progress } from './progress.js';
import { buildCurriculum, allLessons } from './curriculum.js';
import { Quiz } from './quiz.js';
import { Tour } from './tour.js';
import { describe, nearestStation, nearbyLandmarks } from './describe.js';
import { toast, ring, ICON, buzz } from './ui.js';

const $ = (s, el = document) => el.querySelector(s);
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const fmt = (n) => n.toLocaleString('en-IN');
const DEPTH_OF = { corp: 'corp', zone: 'zone', circle: 'circle', locality: 'locality', landmark: 'landmark', station: 'metro', line: 'metro', corridor: 'routes', exit: 'routes', river: 'routes', rail: 'metro' };
const PLAY_ICON = '<svg viewBox="0 0 24 24"><path d="M7 4v16l13-8z"/></svg>';
const ORBIT_ICON = '<svg viewBox="0 0 24 24"><ellipse cx="12" cy="12" rx="10" ry="4"/><circle cx="12" cy="12" r="2.5"/></svg>';

const GAMES = [
  { id: 'daily', title: 'Daily mix', desc: '10 questions across everything you\'ve met so far', icon: 'bolt', wide: true, bg: 'radial-gradient(120% 120% at 100% 0%, #2c9b62 0%, #0d3a24 55%, #08150f 100%)' },
  { id: 'pin', title: 'Pin drop', desc: 'Tap where it is on the map', icon: 'pin', scope: ['zone', 'circle', 'locality', 'landmark', 'station'], bg: 'radial-gradient(120% 120% at 100% 0%, #2a5d8f, #0b1a2a 70%)' },
  { id: 'name', title: 'Name it', desc: 'What\'s highlighted?', icon: 'tag', scope: ['zone', 'circle', 'locality', 'landmark', 'station'], bg: 'radial-gradient(120% 120% at 100% 0%, #8f7a2a, #241c08 70%)' },
  { id: 'mycorp', title: 'Mee area ye corporation?', desc: 'GHMC, Cyberabad or Malkajgiri?', icon: 'layers', bg: 'linear-gradient(135deg, rgba(91,228,155,.45), rgba(125,185,240,.35) 50%, rgba(242,207,74,.4))' },
  { id: 'compass', title: 'Compass', desc: 'Which way from the landmark?', icon: 'compass', bg: 'radial-gradient(120% 120% at 100% 0%, #5e3a8f, #170b26 70%)' },
  { id: 'neighbours', title: 'Neighbours', desc: 'Who shares a border?', icon: 'grid', scope: ['zone', 'circle', 'locality'], bg: 'radial-gradient(120% 120% at 100% 0%, #8f3a4a, #260b11 70%)' },
  { id: 'metro', title: 'Metro master', desc: 'Lines, next stops, interchanges', icon: 'train', bg: 'linear-gradient(135deg, rgba(229,72,77,.5), rgba(62,139,255,.45) 55%, rgba(48,196,108,.45))' },
  { id: 'draw', title: 'Draw the route', desc: 'Trace metro lines, highways, the ORR and the Musi from memory. Scored on how closely you follow the real path.', icon: 'route', wide: true, adv: true, bg: 'radial-gradient(130% 140% at 100% 0%, #c0392b 0%, #3b0f12 45%, #0b0606 100%)' },
  { id: 'photo', title: 'Picture this', desc: 'Name the place from its photo', icon: 'target', bg: 'radial-gradient(120% 120% at 100% 0%, #1f7a8f, #08191e 70%)' },
  { id: 'exits', title: 'ORR exits', desc: 'Exit 1 to 19 by heart', icon: 'route', bg: 'radial-gradient(120% 120% at 100% 0%, #7a6a2a, #1d1907 70%)' },
  { id: 'routes', title: 'Route builder', desc: 'Put highway & metro stops in order', icon: 'route', bg: 'radial-gradient(120% 120% at 100% 0%, #9a6a1c, #261807 70%)' },
  { id: 'review', title: 'Fix my mistakes', desc: 'Drill the places you keep missing', icon: 'redo', wide: true, bg: 'radial-gradient(120% 140% at 100% 0%, #6b2a33, #170a0c 70%)' },
];
const SCOPE_LABEL = { zone: 'Zones', circle: 'Circles', locality: 'Areas', landmark: 'Landmarks', station: 'Stations' };

class App {
  async boot() {
    this.A = await loadAtlas();
    this.P = new Progress();
    this.phases = buildCurriculum(this.A);
    this.lessons = allLessons(this.phases);
    this.el = $('#app');
    const st = this.P.s.settings;
    if (st.lite === undefined) { st.lite = isLowEndDevice(); st.terrain = true; st.autoTilt = true; st.telugu = false; this.P.save(); }
    this.map = new AtlasMap($('#map'), $('#labels'), this.A, { lite: st.lite, terrain: st.terrain, autoTilt: st.autoTilt, telugu: st.telugu });
    await this.map.init();
    this.quiz = new Quiz(this);
    this.tour = new Tour(this);
    this.map.selectHandler = (it) => this.exploreSelect(it, false);
    this.map.setLabelsOn(this.P.s.settings.labels);
    this.map.setRoads(this.P.s.settings.roads);
    this.map.pitch = this.P.s.settings.tilt ? 52 : 0;
    this.bindChrome();
    this.renderDepthBar();
    this.setDepth('corp', false);
    this.updateChips();
    this.P.onChange(() => this.updateChips());
    this.markDone();
    window.atlasBack = () => this.back();
    setTimeout(() => {
      $('#splash').classList.add('gone');
      this.map.intro();
    }, 350);
  }

  // ---------------------------------------------------------------- chrome
  setMode(mode) {
    this.el.dataset.mode = mode;
    // flatter camera in quizzes so taps land where you expect
    this.map.pitch = mode === 'quiz' ? 32 : this.P.s.settings.tilt ? 52 : 0;
    const H = window.innerHeight;
    this.map.viewPad = mode === 'quiz' ? { top: 190, bottom: Math.round(Math.max(230, H * 0.3)), left: 14, right: 14 }
      : mode === 'tour' ? { top: 60, bottom: 290, left: 14, right: 14 } : null;
  }

  bindChrome() {
    document.querySelectorAll('#tabbar button').forEach((b) => (b.onclick = () => this.tab(b.dataset.tab)));
    document.querySelectorAll('#map-tools .tool:not(.dial)').forEach((b) => (b.onclick = () => this.tool(b.dataset.tool, b)));
    this.syncTools();
    $('#btn-search').onclick = () => this.openSearch();
    $('#search [data-act="close"]').onclick = () => this.closeSearch();
    $('#search-input').oninput = (e) => this.runSearch(e.target.value);
    $('#sheet .sheet-grip').onclick = () => this.tab('explore');
    this.bindDial();
    this.bindFlight();
  }

  // ---------------------------------------------------------------- camera dial
  bindDial() {
    const d = $('[data-tool="dial"]');
    const needle = $('.dial-needle', d), label = $('.dial-tilt', d);
    let start = null, last = null, moved = false;
    d.addEventListener('pointerdown', (e) => {
      d.setPointerCapture(e.pointerId);
      start = last = [e.clientX, e.clientY];
      moved = false;
      d.classList.add('active');
    });
    d.addEventListener('pointermove', (e) => {
      if (!start) return;
      if (Math.abs(e.clientX - start[0]) + Math.abs(e.clientY - start[1]) > 6) moved = true;
      if (moved) this.map.nudgeCamera((e.clientX - last[0]) * 0.8, -(e.clientY - last[1]) * 0.45);
      last = [e.clientX, e.clientY];
    });
    const up = () => {
      if (!start) return;
      d.classList.remove('active');
      if (!moved) this.dialTap();
      start = null;
    };
    d.addEventListener('pointerup', up);
    d.addEventListener('pointercancel', up);
    this.map.onCamera((b, p) => {
      needle.style.transform = `rotate(${-b}deg)`;
      label.textContent = p < 4 ? '2D' : `${Math.round(p)}°`;
    });
    this.map.emitCamera();
  }

  // tap: point north first; if already north, switch between 3D and flat
  dialTap() {
    const m = this.map.map, s = this.P.s.settings;
    buzz(8);
    if (Math.abs(m.getBearing()) > 2) { this.map.resetNorth(); return; }
    s.tilt = !s.tilt;
    this.map.pitch = s.tilt ? 52 : 0;
    m.easeTo({ pitch: s.tilt ? this.map.pitchForZoom(m.getZoom()) : 0, duration: 700 });
    toast(s.tilt ? '3D view' : 'Flat map view');
    this.P.save();
  }

  // ---------------------------------------------------------------- flight
  bindFlight() {
    const f = $('#flight');
    $('[data-f="stop"]', f).onclick = () => this.map.stopMotion();
    $('[data-f="pause"]', f).onclick = (e) => {
      const mo = this.map.motion;
      if (!mo) return;
      mo.paused = !mo.paused;
      e.target.textContent = mo.paused ? 'Resume' : 'Pause';
    };
    f.querySelectorAll('.fl-speed button').forEach((b) => (b.onclick = () => {
      if (this.map.motion) this.map.motion.speed = +b.dataset.s;
      f.querySelectorAll('.fl-speed button').forEach((x) => x.classList.toggle('on', x === b));
    }));
  }

  startFlight(it) {
    const A = this.A;
    const coords = pathOf(it);
    if (coords.length < 2) return;
    let stops = [];
    if (it.kind === 'line') stops = it.stationIds.map((id) => A.get(id));
    else if (it.stops) stops = it.stops;
    // distance along the path where each stop sits
    const cum = [0];
    for (let i = 1; i < coords.length; i++) cum.push(cum[i - 1] + km(coords[i - 1], coords[i]));
    const marks = stops.map((s) => {
      let bi = 0, bd = Infinity;
      coords.forEach((c, i) => { const d = km(c, [s.lng, s.lat]); if (d < bd) { bd = d; bi = i; } });
      return { name: s.name, at: cum[bi] };
    }).sort((a, b) => a.at - b.at);
    this.hideCard();
    this.flightItem = it;
    this.setMode('flight');
    const f = $('#flight');
    f.hidden = false;
    $('.fl-title', f).textContent = it.name;
    $('.fl-kicker', f).textContent = `Fly along · ${lengthKm(coords).toFixed(0)} km`;
    $('[data-f="pause"]', f).textContent = 'Pause';
    f.querySelectorAll('.fl-speed button').forEach((x) => x.classList.toggle('on', x.dataset.s === '1'));
    const d = DEPTH_OF[it.kind] || 'routes';
    if (this.map.depth !== d) this.map.setDepth(d, { animate: false });
    this.map.select(it, { fly: false });
    const now = $('.fl-now', f), next = $('.fl-next', f), bar = $('.fl-bar', f);
    now.textContent = marks[0] ? marks[0].name : it.name;
    next.textContent = 'Taking off…';
    this.map.flyAlong(coords, {
      zoom: it.kind === 'line' ? 14.9 : 14.3, pitch: 66, kmPerSec: it.kind === 'line' ? 0.42 : 0.65,
      onTick: (s, total) => {
        bar.style.width = `${(s / total) * 100}%`;
        let prev = null, nxt = null;
        for (const mk of marks) { if (mk.at <= s + 0.15) prev = mk; else { nxt = mk; break; } }
        if (prev && now.textContent !== prev.name) { now.textContent = prev.name; buzz(6); }
        next.textContent = nxt ? `Next: ${nxt.name} · ${(nxt.at - s).toFixed(1)} km` : `${(total - s).toFixed(1)} km to the end`;
      },
      onEnd: () => { next.textContent = 'Arrived'; setTimeout(() => this.endFlight(), 1600); },
      onStop: () => this.endFlight(),
    });
  }

  endFlight() {
    if ($('#flight').hidden) return;
    this.map.stopMotion();
    $('#flight').hidden = true;
    this.setMode('explore');
    const it = this.flightItem;
    this.flightItem = null;
    if (it) { this.setHeadline(it); this.renderCard(it); }
  }

  orbit(it) {
    toast('Orbiting · touch the map to take control', 2600);
    this.map.orbit(it);
  }

  syncTools() {
    const s = this.P.s.settings;
    document.querySelector('[data-tool="roads"]').classList.toggle('on', s.roads);
    document.querySelector('[data-tool="labels"]').classList.toggle('on', s.labels);
  }

  tool(t) {
    const s = this.P.s.settings;
    if (t === 'recenter') { this.map.clearSelection(); this.hideCard(); this.map.home(); }
    if (t === 'roads') { s.roads = !s.roads; this.map.setRoads(s.roads); toast(s.roads ? 'Road map on' : 'Road map off'); }
    if (t === 'labels') { s.labels = !s.labels; this.map.setLabelsOn(s.labels); }
    this.P.save();
    this.syncTools();
    buzz(8);
  }

  updateChips() {
    $('#xp-chip span').textContent = fmt(this.P.s.xp);
    $('#streak-chip span').textContent = this.P.streakDays();
  }

  markDone() {
    this.map.markDone(this.A.localities.filter((l) => this.P.mastered(l.id)).map((l) => l.id));
  }

  tab(name) {
    document.querySelectorAll('#tabbar button').forEach((b) => b.classList.toggle('on', b.dataset.tab === name));
    this.currentTab = name;
    if (name === 'explore') return this.closeSheet();
    this.hideCard();
    this.map.clearSelection();
    if (name === 'learn') this.renderLearn();
    if (name === 'play') this.renderPlay();
    if (name === 'progress') this.renderProgress();
  }

  openSheet(html) {
    const sh = $('#sheet');
    $('.sheet-body', sh).innerHTML = html;
    $('.sheet-body', sh).scrollTop = 0;
    sh.hidden = false;
    this.el.dataset.sheet = 'open';
    return $('.sheet-body', sh);
  }

  closeSheet() {
    $('#sheet').hidden = true;
    this.el.dataset.sheet = '';
    document.querySelectorAll('#tabbar button').forEach((b) => b.classList.toggle('on', b.dataset.tab === 'explore'));
    this.currentTab = 'explore';
  }

  back() {
    if (!$('#search').hidden) { this.closeSearch(); return true; }
    if (!$('#flight').hidden) { this.map.stopMotion(); return true; }
    if (this.map.motion) { this.map.stopMotion(); return true; }
    if (!$('#quiz').hidden) { this.quiz.close(); return true; }
    if (!$('#tour').hidden) { this.tour.close(false); return true; }
    if (this.sheetBack) { const f = this.sheetBack; this.sheetBack = null; f(); return true; }
    if (!$('#sheet').hidden) { this.tab('explore'); return true; }
    if (this.map.selected) { this.map.clearSelection(); this.hideCard(); this.setHeadline(); return true; }
    if (this.map.depth !== 'corp') { this.setDepth('corp'); this.map.home(); return true; }
    return false;
  }

  // called when a quiz or tour closes
  afterOverlay() {
    this.setMode('explore');
    this.map.setLabelOverride(null);
    this.setDepth(this.map.depth === 'blank' ? 'corp' : this.map.depth, false);
    this.markDone();
    if (this.returnTo) { const f = this.returnTo; this.returnTo = null; f(); }
  }

  // ---------------------------------------------------------------- explore
  renderDepthBar() {
    const counts = { corp: 3, zone: 12, circle: 60, locality: this.A.localities.length, landmark: this.A.landmarks.length, metro: this.A.stations.length, routes: this.A.corridors.length };
    $('#depth-bar').innerHTML = DEPTHS.map((d) => `<button class="depth-chip" data-d="${d.id}">${d.label}<b>${counts[d.id]}</b></button>`).join('');
    document.querySelectorAll('.depth-chip').forEach((b) => (b.onclick = () => { buzz(6); this.setDepth(b.dataset.d); }));
  }

  setDepth(d, fly = true) {
    this.map.clearSelection(false);
    this.hideCard();
    this.map.setDepth(d);
    document.querySelectorAll('.depth-chip').forEach((b) => b.classList.toggle('on', b.dataset.d === d));
    const chip = document.querySelector(`.depth-chip[data-d="${d}"]`);
    if (chip) chip.scrollIntoView({ inline: 'center', behavior: 'smooth', block: 'nearest' });
    this.setHeadline();
    this.renderStrip(d);
    if (fly && this.el.dataset.mode === 'explore') {
      if (d === 'metro') this.map.fitItem({ kind: 'corridor', stops: this.A.stations }, { zoomAdj: 0.1, duration: 1200 });
      else this.map.home({ duration: 1200 });
    }
  }

  // Swipeable photo cards of landmarks (Landmarks depth) and routes (Routes depth).
  renderStrip(d) {
    const strip = $('#places-strip');
    const A = this.A;
    let html = '';
    if (d === 'landmark') {
      html = A.photos.map((m) => `<button class="place-card" data-id="${m.id}"><img src="${m.photo.thumb || m.photo.remoteThumb}" alt="" loading="lazy" decoding="async" onerror="this.remove()"><span><small>${esc(m.cat)}</small>${esc(m.name)}</span></button>`).join('');
    } else if (d === 'routes') {
      html = [A.orrRoute, ...A.highways, ...(A.rivers || [])].filter(Boolean).map((r) => `<button class="place-card route-card" data-id="${r.id}" style="background:linear-gradient(135deg,#3a2a0c,#0d0a04)"><span><small>${r.ref ? esc(r.ref) : esc(KIND_LABEL[r.kind])} · ${Math.round(r.lengthKm || lengthKm(pathOf(r)))} km</small>${esc(r.name)}</span></button>`).join('');
    }
    strip.innerHTML = html;
    strip.hidden = !html;
    strip.scrollLeft = 0;
    strip.querySelectorAll('[data-id]').forEach((b) => (b.onclick = () => this.exploreSelect(A.get(b.dataset.id))));
  }

  setHeadline(it) {
    const h = $('#headline');
    const A = this.A;
    if (it) {
      const parent = crumb(A, it)[0];
      const kicker = it.kind === 'corp' ? `Corporation ${it.num}` : `${KIND_LABEL[it.kind]}${parent ? ' · ' + parent : ''}`;
      $('.kicker', h).textContent = kicker;
      $('.title', h).textContent = it.name;
      $('.sub', h).innerHTML = '';
      return;
    }
    const text = {
      corp: ['Hyderabad · since Feb 2026', 'One city. <em>Three corporations.</em>', [`~${fmt(A.meta.areaKm2)} km²`, '60 circles', '300 wards']],
      zone: ['Level 2', '<em>12 zones</em> across three corporations.', ['6 GHMC', '3 Cyberabad', '3 Malkajgiri']],
      circle: ['Level 3', '<em>60 circles.</em> 300 wards.', ['30 GHMC', '16 Cyberabad', '14 Malkajgiri']],
      locality: ['Level 4', `<em>${A.localities.length} areas</em> to know by heart.`, ['Tap any area']],
      landmark: ['Level 5', `<em>${A.landmarks.length} landmarks</em> to anchor your map.`, []],
      metro: ['Level 6', `<em>3 lines.</em> ${A.stations.length} stations.`, A.metro.map((l) => l.name)],
      routes: ['Level 7', `<em>${A.highways.length} highways,</em> the ORR and ${A.exits.length} exits.`, ['Real road paths', 'Tap a route']],
    }[this.map.depth] || ['', '', []];
    $('.kicker', h).textContent = text[0];
    $('.title', h).innerHTML = text[1];
    $('.sub', h).innerHTML = text[2].map((t, i) => `<span class="pill ${i ? 'dim' : ''}">${esc(t)}</span>`).join('');
  }

  exploreSelect(it, fly = true) {
    if (!it) { this.map.clearSelection(); this.hideCard(); this.setHeadline(); return; }
    const d = DEPTH_OF[it.kind];
    if (d && this.map.depth !== d) this.setDepth(d, false);
    this.setHeadline(it);
    this.renderCard(it);
    const top = $('#info-card').getBoundingClientRect().top;
    this.map.viewPad = { top: 70, bottom: Math.round(window.innerHeight - top + 8), left: 14, right: 14 };
    this.map.select(it, { fly: true });
    this.P.s.explored[it.id] = 1;
    this.P.save();
    buzz(6);
  }

  hideCard() {
    $('#info-card').hidden = true;
    this.el.dataset.card = '';
    if (this.el.dataset.mode === 'explore') this.map.viewPad = null;
  }

  stats(it) {
    const A = this.A;
    const wards = (ids) => ids.reduce((s, id) => s + (A.get(id).wards || 0), 0);
    switch (it.kind) {
      case 'corp': {
        const circles = A.circles.filter((c) => c.corp === it.id);
        return [[`${fmt(Math.round(it.areaKm2))}`, 'km²'], [it.children.length, 'zones'], [circles.length, 'circles'], [wards(circles.map((c) => c.id)), 'wards']];
      }
      case 'zone': return [[fmt(Math.round(it.areaKm2)), 'km²'], [it.children.length, 'circles'], [wards(it.children), 'wards']];
      case 'circle': return [[fmt(Math.round(it.areaKm2)), 'km²'], [it.wards, 'wards'], [it.children.length, 'areas']];
      case 'locality':
      case 'landmark': {
        const ns = nearestStation(A, it);
        return [[ns ? ns.km.toFixed(1) : '–', `km to metro`], [km([78.4746, 17.3616], pointOf(it)).toFixed(0), 'km to Charminar']];
      }
      case 'station': return [[it.lines.length, it.lines.length > 1 ? 'lines' : 'line'], [km([78.4746, 17.3616], pointOf(it)).toFixed(0), 'km to Charminar']];
      case 'line': {
        let len = 0;
        for (let i = 1; i < it.line.length; i++) len += km(it.line[i - 1], it.line[i]);
        return [[it.stationIds.length, 'stations'], [len.toFixed(0), 'km']];
      }
      case 'corridor': return [[Math.round(it.lengthKm || lengthKm(pathOf(it))), 'km'], [it.stops.length, it.loop ? 'exits' : 'stops']];
      case 'exit': return [[it.ref, 'exit no.'], [km([78.4746, 17.3616], pointOf(it)).toFixed(0), 'km to Charminar']];
      case 'river':
      case 'rail': return [[lengthKm(pathOf(it)).toFixed(0), 'km mapped']];
    }
    return [];
  }

  renderCard(it) {
    const A = this.A;
    const card = $('#info-card');
    const chip = (x, label = x.name) => `<button class="chip" data-id="${x.id}"><i style="background:${x.color || '#fff'}"></i>${esc(label)}</button>`;
    let sections = '';
    if (it.children && it.children.length) {
      sections += `<div class="ic-sec">Inside · ${it.children.length}</div><div class="chips">${it.children.map((id) => chip(A.get(id))).join('')}</div>`;
    }
    if (it.near && it.near.length) {
      sections += `<div class="ic-sec">Borders</div><div class="chips">${it.near.map((id) => chip(A.get(id))).join('')}</div>`;
    }
    if (it.kind === 'locality' || it.kind === 'landmark') {
      const ns = nearestStation(A, it);
      const lm = nearbyLandmarks(A, it).slice(0, 5);
      if (it.kind === 'landmark' && it.locality) sections += `<div class="ic-sec">Area</div><div class="chips">${chip(A.get(it.locality))}</div>`;
      if (ns) sections += `<div class="ic-sec">Nearest metro</div><div class="chips">${chip({ ...ns.station, color: A.get(ns.station.lines[0]).color }, `${ns.station.name} · ${ns.km.toFixed(1)} km`)}</div>`;
      if (lm.length) sections += `<div class="ic-sec">Nearby landmarks</div><div class="chips">${lm.map((m) => chip(m)).join('')}</div>`;
    }
    if (it.kind === 'station') {
      sections += `<div class="ic-sec">Lines</div><div class="chips">${it.lines.map((l) => chip(A.get(l))).join('')}</div>`;
      const nb = [];
      for (const l of it.lines) {
        const ids = A.get(l).stationIds, i = ids.indexOf(it.id);
        if (i > 0) nb.push(A.get(ids[i - 1]));
        if (i < ids.length - 1) nb.push(A.get(ids[i + 1]));
      }
      sections += `<div class="ic-sec">Next stops</div><div class="chips">${nb.map((s) => chip({ ...s, color: A.get(s.lines[0]).color })).join('')}</div>`;
    }
    if (it.kind === 'line') {
      sections += `<div class="ic-sec">Stations in order</div><div class="chips">${it.stationIds.map((id, i) => chip({ ...A.get(id), color: it.color }, `${i + 1}. ${A.get(id).name}`)).join('')}</div>`;
    }
    if (it.kind === 'corridor') {
      sections += `<div class="ic-sec">${it.loop ? 'Exits in order' : 'Stops in order'}</div><div class="chips">${it.stops.map((s, i) =>
        `<button class="chip" data-ll="${s.lng},${s.lat}"><i style="background:#f2cf4a"></i>${esc(it.loop ? `Exit ${A.get(s.id)?.ref || i + 1} · ${s.name}` : `${i + 1}. ${s.name}`)}</button>`).join('')}</div>`;
    }
    if (it.kind === 'exit') sections += `<div class="ic-sec">Road</div><div class="chips">${chip({ ...A.orrRoute, color: '#ffffff' })}</div>`;
    const pill = it.kind === 'corp' ? `Corporation ${it.num}` : KIND_LABEL[it.kind];
    const mastered = this.P.mastered(it.id);
    const ph = it.photo;
    const hero = ph ? `<div class="ic-hero"><img src="${ph.hero || ph.remote}" alt="${esc(it.name)}" decoding="async" onerror="this.parentNode.remove()"><div class="credit" data-credit>Photo: Wikimedia Commons</div></div>` : '';
    const lineLike = ['line', 'corridor', 'river', 'rail'].includes(it.kind);
    const canOrbit = ['landmark', 'locality', 'station', 'circle', 'zone', 'exit'].includes(it.kind);
    const shield = it.kind === 'corridor' && it.ref ? `<span class="shield-inline"><i></i>${esc(it.ref)}</span>` : '';
    card.innerHTML = `
      ${hero}
      <div class="ic-head"><div class="grow">
        <span class="pill" style="background:${it.color || '#5be49b'}">${esc(pill)}</span>
        ${mastered ? '<span class="pill dim" style="margin-left:4px">Mastered</span>' : ''}
        <div class="ic-title">${shield}${esc(it.name)}</div>
        ${it.te ? `<div class="te-name">${esc(it.te)}</div>` : ''}
        <div class="ic-crumb">${crumb(A, it).map((c) => `<b>${esc(c)}</b>`).join(' › ') || (it.full ? esc(it.full) : '')}</div>
      </div><button class="close-x" data-x>${ICON.close}</button></div>
      <div class="ic-fact">${esc(describe(A, it))}</div>
      <div class="ic-stats">${this.stats(it).map(([v, k]) => `<div class="stat"><div class="v">${v}</div><div class="k">${k}</div></div>`).join('')}</div>
      ${sections}
      <div class="ic-actions">
        ${lineLike ? `<button class="btn ghost" data-fly>${PLAY_ICON}Fly along</button>` : ''}
        ${canOrbit ? `<button class="btn ghost" data-orbit>${ORBIT_ICON}Orbit</button>` : ''}
        <button class="btn primary" data-q>Quiz me</button>
      </div>`;
    card.hidden = false;
    this.el.dataset.card = 'open';
    card.scrollTop = 0;
    card.style.animation = 'none'; void card.offsetWidth; card.style.animation = '';
    $('[data-x]', card).onclick = () => { this.map.clearSelection(); this.hideCard(); this.setHeadline(); };
    card.querySelectorAll('.chip[data-id]').forEach((c) => (c.onclick = () => { const x = A.get(c.dataset.id); if (x) this.exploreSelect(x); }));
    card.querySelectorAll('.chip[data-ll]').forEach((c) => (c.onclick = () => {
      const [lng, lat] = c.dataset.ll.split(',').map(Number);
      this.map.fitItem({ lng, lat, kind: 'stop' }, { zoom: 14.6, pitch: this.map.pitchForZoom(14.6) });
    }));
    $('[data-q]', card).onclick = () => this.quickQuiz(it);
    const fly = $('[data-fly]', card), orb = $('[data-orbit]', card);
    if (fly) fly.onclick = () => this.startFlight(it);
    if (orb) orb.onclick = () => this.orbit(it);
    if (ph) this.fillCredit(ph, $('[data-credit]', card));
  }

  // Photo credit: bundled photos carry it; remote ones ask the Commons API once.
  async fillCredit(ph, el) {
    const show = (c) => { if (el && c) el.innerHTML = `Photo: ${esc(c.author)} · ${esc(c.license)} · <a href="${ph.source}" target="_blank" rel="noopener">Wikimedia Commons</a>`; };
    if (ph.author) return show(ph);
    const cache = JSON.parse(localStorage.getItem('hyd-credits') || '{}');
    if (cache[ph.file]) return show(cache[ph.file]);
    try {
      const url = 'https://commons.wikimedia.org/w/api.php?action=query&format=json&origin=*&prop=imageinfo&iiprop=extmetadata&titles=' + encodeURIComponent('File:' + ph.file);
      const j = await (await fetch(url)).json();
      const md = Object.values(j.query.pages)[0].imageinfo[0].extmetadata;
      const strip = (h) => (h || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
      const c = { author: strip(md.Artist && md.Artist.value).slice(0, 60) || 'Wikimedia Commons contributor', license: strip(md.LicenseShortName && md.LicenseShortName.value) || 'see source' };
      cache[ph.file] = c;
      localStorage.setItem('hyd-credits', JSON.stringify(cache));
      show(c);
    } catch { /* offline: keep the generic credit line */ }
  }

  quickQuiz(it) {
    const A = this.A;
    let ids = [it.id];
    if (it.children && it.children.length) ids = ids.concat(it.children);
    else if (it.near) ids = ids.concat(it.near.slice(0, 4));
    if (it.kind === 'line') ids = it.stationIds;
    if (it.kind === 'station') ids = ids.concat(A.get(it.lines[0]).stationIds.slice(0, 6));
    if (it.kind === 'corridor' && it.loop) ids = [it.id, ...A.exits.map((e) => e.id)];
    this.hideCard();
    this.map.clearSelection();
    this.quiz.start({ title: `Quiz · ${it.name}`, ids, games: 'auto', count: Math.min(8, Math.max(5, ids.length)) });
  }

  // ---------------------------------------------------------------- search
  openSearch() {
    $('#search').hidden = false;
    $('#search-input').value = '';
    this.runSearch('');
    setTimeout(() => $('#search-input').focus(), 50);
  }
  closeSearch() { $('#search').hidden = true; $('#search-input').blur(); }

  runSearch(q) {
    const A = this.A;
    q = q.trim().toLowerCase();
    const all = [...A.corporations, ...A.zones, ...A.circles, ...A.localities, ...A.landmarks, ...A.stations, ...A.metro, ...A.corridors, ...A.exits, ...A.rivers, ...A.rail];
    let res;
    if (!q) res = [...A.corporations, ...A.zones];
    else {
      res = all.filter((x) => x.name.toLowerCase().includes(q) || (x.te && x.te.includes(q)))
        .sort((a, b) => (b.name.toLowerCase().startsWith(q) - a.name.toLowerCase().startsWith(q)) || a.name.length - b.name.length);
    }
    $('#search-results').innerHTML = res.slice(0, 40).map((x) => `<button class="sr" data-id="${x.id}"><i style="background:${x.color || '#fff'}"></i><div><div class="sn">${esc(x.name)}</div><div class="ss">${KIND_LABEL[x.kind]}${crumb(A, x).length ? ' · ' + esc(crumb(A, x).join(' › ')) : ''}</div></div></button>`).join('')
      || '<div class="sh-lead" style="padding:14px">No matches</div>';
    document.querySelectorAll('.sr').forEach((b) => (b.onclick = () => {
      this.closeSearch();
      if (this.currentTab !== 'explore') this.tab('explore');
      this.exploreSelect(A.get(b.dataset.id));
    }));
  }

  // ---------------------------------------------------------------- learn
  lessonUnlocked(i) {
    if (this.P.s.settings.unlockAll || i === 0) return true;
    return this.P.lesson(this.lessons[i - 1].id).best >= 70;
  }

  renderLearn() {
    this.sheetBack = null;
    const P = this.P;
    const nextIdx = this.lessons.findIndex((l, i) => this.lessonUnlocked(i) && P.lesson(l.id).best < 70);
    const done = this.lessons.filter((l) => P.lesson(l.id).best >= 70).length;
    let html = `<div class="sh-kicker">Your path · ${done}/${this.lessons.length} lessons</div>
      <div class="sh-title">Learn Hyderabad <em>layer by layer.</em></div>
      <div class="sh-lead">Each lesson is a short flyover, then a quiz. Score 70% to unlock the next one.</div>`;
    let gi = 0;
    this.phases.forEach((ph, pi) => {
      const ids = ph.lessons.flatMap((l) => l.items);
      html += `<div class="phase"><div class="phase-head"><div><div class="phase-num">Phase ${pi + 1}</div><div class="phase-title">${esc(ph.title)}</div></div><div class="phase-meta">${Math.round(P.mastery(ids) * 100)}% mastered</div></div><div class="lessons">`;
      ph.lessons.forEach((l) => {
        const i = gi++;
        const open = this.lessonUnlocked(i);
        const best = P.lesson(l.id).best;
        const m = P.mastery(l.items);
        const inner = !open ? ICON.lock : best >= 70 ? ICON.check : i === nextIdx ? ICON.play : `${Math.round(m * 100)}`;
        html += `<button class="lesson ${open ? '' : 'locked'} ${i === nextIdx ? 'next' : ''}" data-i="${i}">
          ${ring(best >= 70 ? Math.max(m, 0.34) : m, inner)}
          <div style="min-width:0"><div class="lt">${esc(l.title)}</div><div class="ls">${esc(l.sub)}</div></div>
          <span class="go">${ICON.chev}</span></button>`;
      });
      html += '</div></div>';
    });
    html += `<div class="credits">Tip: Settings in Progress can unlock every lesson if you already know the basics.</div>`;
    const body = this.openSheet(html);
    body.querySelectorAll('.lesson').forEach((b) => (b.onclick = () => {
      const i = +b.dataset.i;
      if (!this.lessonUnlocked(i)) { toast('Finish the previous lesson first'); buzz(20); return; }
      this.renderLesson(this.lessons[i]);
    }));
    const nx = body.querySelector('.lesson.next');
    if (nx) setTimeout(() => nx.scrollIntoView({ block: 'center', behavior: 'smooth' }), 250);
  }

  renderLesson(l) {
    const A = this.A, P = this.P;
    this.sheetBack = () => this.renderLearn();
    const best = P.lesson(l.id).best;
    const items = l.items.map((id) => A.get(id));
    const body = this.openSheet(`
      <button class="text-btn" data-back style="display:flex;align-items:center;gap:2px;padding-left:0">${ICON.back}Path</button>
      <div class="sh-kicker">Phase ${l.phaseIndex + 1} · Lesson ${l.index + 1}</div>
      <div class="sh-title">${esc(l.title)}</div>
      <div class="sh-lead">${esc(l.sub)}</div>
      <div class="ic-stats" style="margin-top:14px">
        <div class="stat"><div class="v">${items.length}</div><div class="k">places</div></div>
        <div class="stat"><div class="v">${Math.round(P.mastery(l.items) * 100)}%</div><div class="k">mastered</div></div>
        <div class="stat"><div class="v">${best ? best + '%' : '–'}</div><div class="k">best quiz</div></div>
      </div>
      <div class="ic-sec">You'll learn</div>
      <div class="chips">${items.slice(0, 40).map((x) => `<span class="chip"><i style="background:${x.color || '#fff'}"></i>${esc(x.name)}${P.mastered(x.id) ? ' ✓' : ''}</span>`).join('')}${items.length > 40 ? `<span class="chip">+${items.length - 40} more</span>` : ''}</div>
      <div class="ic-actions" style="margin-top:20px"><button class="btn ghost" data-quiz>Quiz only</button><button class="btn primary" data-start>${best ? 'Replay' : 'Start'} lesson</button></div>`);
    $('[data-back]', body).onclick = () => this.renderLearn();
    $('[data-start]', body).onclick = () => this.startLesson(l, true);
    $('[data-quiz]', body).onclick = () => this.startLesson(l, false);
  }

  startLesson(l, withTour) {
    this.closeSheet();
    this.sheetBack = null;
    const runQuiz = () => this.quiz.start({
      title: l.title, ids: l.items, games: l.games, count: l.count || Math.min(10, Math.max(6, l.items.length * 2)),
      key: { lesson: l.id },
      onDone: () => { this.returnTo = null; this.tab('learn'); },
    });
    if (withTour) this.tour.start(l, (toQuiz) => (toQuiz ? runQuiz() : (this.afterOverlay(), this.tab('learn'))));
    else runQuiz();
  }

  // ---------------------------------------------------------------- play
  renderPlay() {
    this.sheetBack = null;
    const P = this.P;
    const html = `<div class="sh-kicker">Arcade</div><div class="sh-title">Play your way <em>to a mental map.</em></div>
      <div class="sh-lead">Every answer feeds your spaced-repetition memory, so weak spots come back until they stick.</div>
      <div class="games">${GAMES.map((g) => `<button class="game ${g.wide ? 'wide' : ''}" data-g="${g.id}">
        <div class="gfx" style="background:${g.bg}"></div><div class="gi">${ICON[g.icon]}</div>
        ${P.game(g.id).best ? `<div class="gb">BEST ${P.game(g.id).best}</div>` : ''}
        <div class="gt">${esc(g.title)}${g.adv ? '<span class="badge-adv">ADVANCED</span>' : ''}</div><div class="gd">${esc(g.desc)}</div></button>`).join('')}</div>`;
    const body = this.openSheet(html);
    body.querySelectorAll('.game').forEach((b) => (b.onclick = () => {
      const g = GAMES.find((x) => x.id === b.dataset.g);
      if (g.id === 'draw') this.renderDrawSetup(g);
      else if (g.scope) this.renderScope(g);
      else this.startGame(g);
    }));
  }

  renderScope(g) {
    this.sheetBack = () => this.renderPlay();
    let sel = this.lastScope && g.scope.includes(this.lastScope) ? this.lastScope : g.scope[1] || g.scope[0];
    const body = this.openSheet(`
      <button class="text-btn" data-back style="display:flex;align-items:center;gap:2px;padding-left:0">${ICON.back}Games</button>
      <div class="sh-kicker">${esc(g.title)}</div><div class="sh-title">What do you want <em>to practise?</em></div>
      <div class="seg">${g.scope.map((s) => `<button data-s="${s}" class="${s === sel ? 'on' : ''}">${SCOPE_LABEL[s]}</button>`).join('')}</div>
      <div class="ic-actions" style="margin-top:22px"><button class="btn primary" data-go>Play 10 rounds</button></div>`);
    body.querySelectorAll('.seg button').forEach((b) => (b.onclick = () => {
      sel = b.dataset.s;
      body.querySelectorAll('.seg button').forEach((x) => x.classList.toggle('on', x === b));
    }));
    $('[data-back]', body).onclick = () => this.renderPlay();
    $('[data-go]', body).onclick = () => { this.lastScope = sel; this.startGame(g, sel); };
  }

  renderDrawSetup(g) {
    this.sheetBack = () => this.renderPlay();
    const A = this.A;
    const kinds = [
      ['metro', 'Metro lines', A.metro.map((l) => l.id), 3],
      ['highway', 'Highways', A.highways.map((r) => r.id), 3],
      ['orr', 'ORR loop', [A.orrRoute.id], 1],
      ['musi', 'Musi river', A.rivers.map((r) => r.id), 1],
      ['mmts', 'MMTS rail', A.rail.map((r) => r.id), 3],
    ].filter((k) => k[2].length);
    let kind = this.lastDrawKind || 'metro', level = this.lastDrawLevel || 'pro';
    const body = this.openSheet(`
      <button class="text-btn" data-back style="display:flex;align-items:center;gap:2px;padding-left:0">${ICON.back}Games</button>
      <div class="sh-kicker">Draw the route <span class="badge-adv">ADVANCED</span></div>
      <div class="sh-title">Trace it <em>from memory.</em></div>
      <div class="sh-lead">Draw the route with your finger. You score on how much of the real path you covered and how much of your line stayed on it.</div>
      <div class="ic-sec" style="margin-top:18px">Route</div>
      <div class="seg" data-g="kind">${kinds.map(([id, label]) => `<button data-v="${id}" class="${id === kind ? 'on' : ''}">${label}</button>`).join('')}</div>
      <div class="ic-sec" style="margin-top:16px">Difficulty</div>
      <div class="seg" data-g="level">${Object.entries(LEVELS).map(([id, l]) => `<button data-v="${id}" class="${id === level ? 'on' : ''}">${l.label}</button>`).join('')}</div>
      <div class="lvl-note"></div>
      <div class="ic-actions" style="margin-top:22px"><button class="btn primary" data-go>Start drawing</button></div>`);
    const note = $('.lvl-note', body);
    const sync = () => { note.textContent = LEVELS[level].note; };
    sync();
    body.querySelectorAll('.seg').forEach((seg) => seg.querySelectorAll('button').forEach((b) => (b.onclick = () => {
      if (seg.dataset.g === 'kind') kind = b.dataset.v; else level = b.dataset.v;
      seg.querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b));
      sync();
    })));
    $('[data-back]', body).onclick = () => this.renderPlay();
    $('[data-go]', body).onclick = () => {
      this.lastDrawKind = kind; this.lastDrawLevel = level;
      const k = kinds.find((x) => x[0] === kind);
      this.closeSheet();
      this.sheetBack = null;
      this.quiz.start({ title: `Draw · ${k[1]} · ${LEVELS[level].label}`, ids: k[2], games: ['draw'], count: Math.min(k[3], k[2].length), level, key: { game: 'draw' }, onDone: () => this.tab('play') });
    };
  }

  startGame(g, scope) {
    const A = this.A, P = this.P;
    const ids = (list) => list.map((x) => x.id);
    let cfg;
    const regionGame = (k) => (['zone', 'circle'].includes(k) ? 'find' : k === 'station' ? 'findStation' : 'findPoint');
    switch (g.id) {
      case 'daily': {
        const seen = Object.keys(P.s.items).filter((id) => A.get(id));
        const base = seen.length >= 12 ? seen : ids(A.corporations).concat(ids(A.zones), ids(A.landmarks.slice(0, 12)));
        cfg = { ids: base, games: 'auto', count: 10 };
        break;
      }
      case 'pin': cfg = { ids: ids(A.lists[scope]), games: [regionGame(scope)], count: 10 }; break;
      case 'name': cfg = { ids: ids(A.lists[scope]), games: [['zone', 'circle'].includes(scope) ? 'name' : 'namePoint'], count: 10 }; break;
      case 'mycorp': cfg = { ids: ids(A.localities).concat(ids(A.landmarks.filter((m) => m.inCity))), games: ['whichCorp'], count: 12 }; break;
      case 'compass': cfg = { ids: ids(A.localities).concat(ids(A.landmarks)), games: ['compass'], count: 10 }; break;
      case 'neighbours': cfg = { ids: ids(A.lists[scope]), games: ['neighbour'], count: 10 }; break;
      case 'metro': cfg = { ids: ids(A.stations), games: ['nextStop', 'lineOf', 'interchange', 'order', 'findStation'], count: 10 }; break;
      case 'routes': cfg = { ids: ids(A.corridors).concat(ids(A.metro)), games: ['order', 'whichRoad'], count: 8 }; break;
      case 'photo': cfg = { ids: ids(A.photos), games: ['photoName'], count: 10 }; break;
      case 'exits': cfg = { ids: ids(A.exits), games: ['exitNumber', 'findPoint', 'exitNumber'], count: 10 }; break;
      case 'review': {
        const weak = P.weakest(14);
        if (weak.length < 3) { toast('No mistakes to fix yet. Play a few games first.'); return; }
        cfg = { ids: weak, games: 'auto', count: Math.min(10, weak.length + 3) };
        break;
      }
    }
    this.closeSheet();
    this.sheetBack = null;
    this.quiz.start({ title: g.title, ...cfg, key: { game: g.id }, onDone: () => this.tab('play') });
  }

  // ---------------------------------------------------------------- progress
  renderProgress() {
    this.sheetBack = null;
    const A = this.A, P = this.P;
    const levels = [['Corporations', A.corporations], ['Zones', A.zones], ['Circles', A.circles], ['Areas', A.localities],
      ['Landmarks', A.landmarks], ['Metro stations', A.stations], ['Routes', A.corridors]];
    const overall = levels.reduce((s, [, l]) => s + P.mastery(l.map((x) => x.id)), 0) / levels.length;
    const vals = Object.values(P.s.items);
    const c = vals.reduce((s, v) => s + v.c, 0), w = vals.reduce((s, v) => s + v.w, 0);
    const done = this.lessons.filter((l) => P.lesson(l.id).best >= 70).length;
    const weak = P.weakest(6).map((id) => A.get(id)).filter(Boolean);
    const s = P.s.settings;
    const body = this.openSheet(`
      <div class="sh-kicker">Progress</div><div class="sh-title">Your <em>mental map.</em></div>
      <div class="big-ring">${ring(overall, `${Math.round(overall * 100)}%`, 112, 9)}
        <div class="kpis">
          <div class="stat"><div class="v">${fmt(P.s.xp)}</div><div class="k">XP</div></div>
          <div class="stat"><div class="v">${P.streakDays()}</div><div class="k">day streak</div></div>
          <div class="stat"><div class="v">${done}/${this.lessons.length}</div><div class="k">lessons</div></div>
          <div class="stat"><div class="v">${c + w ? Math.round((c / (c + w)) * 100) + '%' : '–'}</div><div class="k">accuracy</div></div>
        </div></div>
      <div class="bars">${levels.map(([name, list]) => {
        const n = list.filter((x) => P.mastered(x.id)).length;
        return `<div class="bar-row"><div class="bl">${name}<span>${n} / ${list.length}</span></div><div class="bar"><i style="width:${P.mastery(list.map((x) => x.id)) * 100}%"></i></div></div>`;
      }).join('')}</div>
      ${weak.length ? `<div class="ic-sec" style="margin-top:22px">Keep missing</div>${weak.map((x) => `<div class="list-row"><i style="width:9px;height:9px;border-radius:50%;background:${x.color || '#fff'}"></i>${esc(x.name)}<span class="rs">${KIND_LABEL[x.kind]}</span></div>`).join('')}
        <div class="ic-actions"><button class="btn primary" data-review>Drill these now</button></div>` : ''}
      <div class="ic-sec" style="margin-top:22px">Settings</div>
      <div class="settings">
        <div class="toggle-row">Unlock all lessons<button class="switch ${s.unlockAll ? 'on' : ''}" data-set="unlockAll"></button></div>
        <div class="toggle-row">3D tilt<button class="switch ${s.tilt ? 'on' : ''}" data-set="tilt"></button></div>
        <div class="toggle-row">Road map overlay<button class="switch ${s.roads ? 'on' : ''}" data-set="roads"></button></div>
        <div class="toggle-row">Map labels<button class="switch ${s.labels ? 'on' : ''}" data-set="labels"></button></div>
        <div class="toggle-row">Telugu names (తెలుగు)<button class="switch ${s.telugu ? 'on' : ''}" data-set="telugu"></button></div>
        <div class="toggle-row">Tilt follows zoom<button class="switch ${s.autoTilt ? 'on' : ''}" data-set="autoTilt"></button></div>
        <div class="toggle-row">3D terrain<button class="switch ${s.terrain ? 'on' : ''}" data-set="terrain"></button></div>
        <div class="toggle-row"><span>Lite mode<br><small style="color:var(--muted);font-weight:600">For older phones: no 3D buildings or terrain</small></span><button class="switch ${s.lite ? 'on' : ''}" data-set="lite"></button></div>
        <div class="toggle-row">Reset all progress<button class="btn ghost small" data-reset>Reset</button></div>
      </div>
      <div class="credits">${A.meta.structure}.<br>${A.meta.sources.map(esc).join('<br>')}<br>Imagery © Esri, Maxar, Earthstar Geographics. 3D buildings and roads © OpenFreeMap, OpenMapTiles, OpenStreetMap contributors. Terrain: AWS Terrain Tiles (Mapzen). Landmark photos: Wikimedia Commons contributors, credited on each photo. Map engine: MapLibre GL JS.<br>Highway symbols and line colours are generic, not official logos; this app is not affiliated with GHMC, CMC, MMC, HMRL or the Government of Telangana.</div>`);
    body.querySelectorAll('[data-set]').forEach((b) => (b.onclick = () => {
      const k = b.dataset.set;
      s[k] = !s[k];
      b.classList.toggle('on', s[k]);
      if (k === 'tilt') this.map.setPitch(s.tilt ? 52 : 0);
      if (k === 'roads') this.map.setRoads(s.roads);
      if (k === 'labels') this.map.setLabelsOn(s.labels);
      if (k === 'telugu') this.map.setTelugu(s.telugu);
      if (k === 'autoTilt') this.map.autoTilt = s.autoTilt;
      if (k === 'terrain') this.map.applyTerrain(s.terrain && !s.lite);
      if (k === 'lite') { this.map.setLite(s.lite); if (!s.lite) this.map.applyTerrain(s.terrain); toast(s.lite ? 'Lite mode on' : 'Full 3D on'); }
      this.P.save();
      this.syncTools();
    }));
    $('[data-reset]', body).onclick = () => {
      if (confirm('Reset all progress, XP and streak?')) { P.reset(); this.renderProgress(); this.markDone(); toast('Progress reset'); }
    };
    const rv = $('[data-review]', body);
    if (rv) rv.onclick = () => this.startGame(GAMES.find((g) => g.id === 'review'));
  }
}

const app = new App();
window.__atlas = app; // handy for debugging from devtools
app.boot().catch((e) => {
  console.error(e);
  document.querySelector('.splash-line').textContent = 'Could not start: ' + e.message;
});
