/* Corporate Dragon: Middle-Class Odyssey — runtime */
(function () {
  "use strict";
  const D = window.GAME_DATA;
  const SAVE_KEY = "cd_save_v1";
  const REGEN_PER_HOUR = 20;
  const MAX_LOG = 20;

  /* ---------- State ---------- */
  function newState() {
    const stats = {};
    D.STATS.forEach(s => { stats[s.key] = 50; });
    stats.discipline = 50;
    stats.creativity = 50;
    stats.financialWisdom = 40;
    stats.familyBond = 70;
    stats.mentalHealth = 60;
    stats.charisma = 50;
    stats.politics = 30;
    stats.wisdom = 30;
    return {
      energy: 100,
      maxEnergy: 100,
      stress: 0,
      stats,
      flags: {},
      retainers: D.RETAINERS_INIT.reduce((m, r) => (m[r.id] = { affinity: r.affinity, lastBoost: 0 }, m), {}),
      currentEpisodeIdx: 0,
      memoryCards: {},
      log: [],
      lastTick: Date.now(),
      started: false,
    };
  }

  let state = loadState() || newState();

  function loadState() {
    try {
      const raw = localStorage.getItem(SAVE_KEY);
      if (!raw) return null;
      return JSON.parse(raw);
    } catch (e) { return null; }
  }
  function saveState() {
    try { localStorage.setItem(SAVE_KEY, JSON.stringify(state)); } catch (e) {}
  }
  function resetState() {
    localStorage.removeItem(SAVE_KEY);
    state = newState();
  }

  /* ---------- Energy tick ---------- */
  function tickEnergy() {
    const now = Date.now();
    const hours = (now - state.lastTick) / (1000 * 60 * 60);
    if (hours > 0) {
      const mult = energyMultiplier();
      const regen = hours * REGEN_PER_HOUR * mult;
      state.energy = Math.min(state.maxEnergy, state.energy + regen);
      state.lastTick = now;
    }
  }
  function energyMultiplier() {
    let m = 1;
    if (state.retainers.maa && state.retainers.maa.affinity >= 8) m += 0.35;
    if (isFestivalDay()) m += 0.4;
    return m;
  }
  function isFestivalDay() {
    const now = new Date();
    for (const f of D.FESTIVALS) {
      const [m, d] = festDate(f, now.getFullYear());
      if (m === now.getMonth() + 1 && Math.abs(d - now.getDate()) <= 1) return true;
    }
    return false;
  }

  /* ---------- Festivals ---------- */
  function festDate(f, year) {
    if (f.byYear && f.byYear[year]) return f.byYear[year];
    return [f.month, f.day];
  }
  function nextOccurrence(f) {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    let year = today.getFullYear();
    let [m, d] = festDate(f, year);
    let dt = new Date(year, m - 1, d);
    if (dt < today) {
      year += 1;
      [m, d] = festDate(f, year);
      dt = new Date(year, m - 1, d);
    }
    return dt;
  }
  function upcomingFestivals(limit) {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    return D.FESTIVALS
      .map(f => ({ f, dt: nextOccurrence(f) }))
      .sort((a, b) => a.dt - b.dt)
      .slice(0, limit);
  }
  function daysBetween(a, b) {
    return Math.round((b - a) / (1000 * 60 * 60 * 24));
  }
  function whenLabel(dt) {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const days = daysBetween(today, dt);
    if (days === 0) return "Today!";
    if (days === 1) return "Tomorrow";
    if (days < 30) return `in ${days} days`;
    if (days < 365) return dt.toLocaleDateString(undefined, { month: "short", day: "numeric" });
    return dt.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
  }

  /* ---------- Stat application ---------- */
  function applyEffects(effects) {
    if (!effects) return;
    Object.keys(effects).forEach(k => {
      if (state.stats[k] !== undefined) {
        state.stats[k] = clamp(state.stats[k] + effects[k], 0, 100);
      }
    });
  }
  function setFlags(flags) {
    if (!flags) return;
    flags.forEach(f => { state.flags[f] = true; });
  }
  function unlockMemory(id) {
    if (!id) return;
    if (!state.memoryCards[id]) {
      state.memoryCards[id] = { unlockedAt: Date.now() };
      toast(`Memory unlocked: ${(D.MEMORY_CARDS.find(c => c.id === id) || {}).name || id}`);
    }
  }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

  /* ---------- Logging ---------- */
  function log(entry) {
    state.log.unshift({ t: Date.now(), text: entry });
    if (state.log.length > MAX_LOG) state.log.length = MAX_LOG;
  }

  /* ---------- DOM helpers ---------- */
  const $ = sel => document.querySelector(sel);
  const $$ = sel => Array.from(document.querySelectorAll(sel));
  function show(screenId) {
    $$(".screen").forEach(s => s.classList.remove("active"));
    $("#" + screenId).classList.add("active");
  }

  /* ---------- Rendering ---------- */
  function renderHome() {
    tickEnergy();
    saveState();

    $("#energy-now").textContent = Math.floor(state.energy);
    $("#energy-max").textContent = state.maxEnergy;
    $("#energy-fill").style.width = `${(state.energy / state.maxEnergy) * 100}%`;

    const ep = D.EPISODES[state.currentEpisodeIdx];
    $("#age-tag").textContent = ep ? `Age ${ep.age}` : "Resolution";

    renderStats();
    renderRetainers();
    renderFestivals();
    renderActions();
    renderStory();
    renderLog();
  }

  function renderStats() {
    const ul = $("#stat-list");
    ul.innerHTML = "";
    D.STATS.forEach(s => {
      const li = document.createElement("li");
      const v = Math.floor(state.stats[s.key]);
      li.innerHTML = `<span class="stat-name">${s.label} <small>${v}</small></span>
        <div class="stat-bar"><div style="width:${v}%"></div></div>`;
      ul.appendChild(li);
    });
  }

  function renderRetainers() {
    const ul = $("#retainer-list");
    ul.innerHTML = "";
    D.RETAINERS_INIT.forEach(r => {
      const s = state.retainers[r.id];
      const aff = s ? s.affinity : 0;
      const pct = clamp(aff * 10, 0, 100);
      const li = document.createElement("li");
      li.innerHTML = `<div class="retainer-avatar">${r.emoji}</div>
        <div class="retainer-info">
          <div class="retainer-name">${r.name}</div>
          <div class="retainer-bar"><div style="width:${pct}%"></div></div>
          <div class="retainer-level">Affinity ${aff}/10</div>
        </div>`;
      ul.appendChild(li);
    });
  }

  function renderFestivals() {
    const ul = $("#festival-list");
    ul.innerHTML = "";
    upcomingFestivals(4).forEach(({ f, dt }) => {
      const li = document.createElement("li");
      li.innerHTML = `<div><span class="festival-emoji">${f.emoji}</span><span class="festival-name">${f.name}</span></div>
        <div class="festival-when">${whenLabel(dt)}</div>`;
      ul.appendChild(li);
    });
  }

  function renderActions() {
    const grid = $("#action-grid");
    grid.innerHTML = "";
    D.DAILY_ACTIONS.forEach(a => {
      const btn = document.createElement("button");
      btn.className = "action-btn";
      btn.disabled = state.energy < a.cost;
      btn.innerHTML = `<div class="action-name">${a.name}</div>
        <div class="action-cost">Energy −${a.cost}</div>
        <div class="action-effect">${a.flavor}</div>`;
      btn.addEventListener("click", () => doAction(a));
      grid.appendChild(btn);
    });
  }

  function renderStory() {
    const ep = D.EPISODES[state.currentEpisodeIdx];
    if (!ep) {
      $("#story-status").textContent = "Chapter 1 complete. More chapters coming in the next release.";
      $("#btn-play-episode").disabled = true;
      $("#btn-play-episode").textContent = "✓ Chapter 1 Complete";
    } else {
      $("#story-status").textContent = `Chapter ${ep.chapter} · Episode ${ep.episode} · "${ep.title}"`;
      $("#btn-play-episode").disabled = false;
      $("#btn-play-episode").textContent = `Play: ${ep.title}`;
    }
  }

  function renderLog() {
    const ul = $("#log-list");
    ul.innerHTML = "";
    if (!state.log.length) {
      const li = document.createElement("li");
      li.textContent = "No reflections yet. Make a choice and watch the world remember.";
      ul.appendChild(li);
      return;
    }
    state.log.slice(0, 12).forEach(e => {
      const li = document.createElement("li");
      li.textContent = e.text;
      ul.appendChild(li);
    });
  }

  /* ---------- Actions ---------- */
  function doAction(a) {
    if (state.energy < a.cost) return;
    state.energy -= a.cost;
    applyEffects(a.effects);
    if (a.retainer && state.retainers[a.retainer]) {
      state.retainers[a.retainer].affinity = clamp(state.retainers[a.retainer].affinity + 1, 0, 10);
    }
    log(`${a.name} — ${a.flavor}`);
    saveState();
    renderHome();
  }

  /* ---------- Episode flow ---------- */
  let episodeLineIdx = 0;
  let currentEp = null;
  let visibleLines = [];

  function playEpisode(idx) {
    currentEp = D.EPISODES[idx];
    if (!currentEp) return;
    episodeLineIdx = 0;
    visibleLines = currentEp.lines.filter(ln => {
      if (ln.ifFlag && !state.flags[ln.ifFlag]) return false;
      if (ln.ifNotFlag && state.flags[ln.ifNotFlag]) return false;
      return true;
    });
    $("#ep-chapter").textContent = `Chapter ${currentEp.chapter}`;
    $("#ep-episode").textContent = `Episode ${currentEp.episode}`;
    $("#ep-title").textContent = currentEp.title;
    $("#ep-bg").className = "ep-bg " + (currentEp.bg || "morning");
    $("#ep-char-emoji").textContent = currentEp.charEmoji || "🧒";
    $("#ep-character").className = "ep-character" + (currentEp.mood ? " " + currentEp.mood : "");
    $("#ep-choices").innerHTML = "";
    $("#ep-continue").classList.add("hidden");
    show("screen-episode");
    showNextLine();
  }

  function showNextLine() {
    if (episodeLineIdx >= visibleLines.length) {
      showChoices();
      return;
    }
    const ln = visibleLines[episodeLineIdx++];
    $("#ep-speaker").textContent = ln.speaker;
    $("#ep-line").textContent = ln.text;
    if (episodeLineIdx < visibleLines.length) {
      $("#ep-continue").classList.remove("hidden");
      $("#ep-continue").textContent = "Continue";
      $("#ep-continue").onclick = showNextLine;
      $("#ep-choices").innerHTML = "";
    } else {
      $("#ep-continue").classList.add("hidden");
      showChoices();
    }
  }

  function showChoices() {
    const choices = currentEp.choices || [];
    const wrap = $("#ep-choices");
    wrap.innerHTML = "";
    choices.forEach(c => {
      const b = document.createElement("button");
      b.className = "choice-btn";
      b.innerHTML = c.text + (c.flags && c.flags.length ? `<span class="choice-tag">${c.flags[0].replace(/Path|Seed/g, "")}</span>` : "");
      b.addEventListener("click", () => chooseOption(c));
      wrap.appendChild(b);
    });
    $("#ep-continue").classList.add("hidden");
  }

  function chooseOption(choice) {
    applyEffects(choice.effects);
    setFlags(choice.flags);
    unlockMemory(choice.memory);

    // Bond increase for relevant family episodes
    if (currentEp.id === "ch1_e1" || currentEp.id === "ch1_e3" || currentEp.id === "ch1_e11") {
      bumpRetainer("maa", 1);
    }
    if (currentEp.id === "ch1_e4" || currentEp.id === "ch1_e5" || currentEp.id === "ch1_e9") {
      bumpRetainer("papa", 1);
    }
    if (currentEp.id === "ch1_e2" || currentEp.id === "ch1_e10") {
      bumpRetainer("nani", 1);
    }

    log(`Ep ${currentEp.episode} "${currentEp.title}" → ${choice.text}`);
    state.currentEpisodeIdx += 1;
    saveState();

    // Show outcome line
    $("#ep-choices").innerHTML = "";
    const stamp = summarizeEffects(choice.effects);
    $("#ep-speaker").textContent = "Reflection";
    $("#ep-line").textContent = stamp || "You let the moment land.";
    $("#ep-continue").classList.remove("hidden");
    $("#ep-continue").textContent = currentEp.endsChapter ? "Finish Chapter →" : "Back Home";
    $("#ep-continue").onclick = () => {
      show("screen-home");
      renderHome();
      if (currentEp.endsChapter) toast("Chapter 1 complete. Memories sealed.");
    };
  }

  function bumpRetainer(id, by) {
    if (state.retainers[id]) {
      state.retainers[id].affinity = clamp(state.retainers[id].affinity + by, 0, 10);
    }
  }

  function summarizeEffects(eff) {
    if (!eff) return "";
    const parts = [];
    Object.keys(eff).forEach(k => {
      const s = D.STATS.find(s => s.key === k);
      if (s) parts.push(`${eff[k] > 0 ? "+" : ""}${eff[k]} ${s.label}`);
    });
    return parts.join(" · ");
  }

  /* ---------- Album ---------- */
  function renderAlbum() {
    const grid = $("#album-grid");
    grid.innerHTML = "";
    D.MEMORY_CARDS.forEach(c => {
      const unlocked = !!state.memoryCards[c.id];
      const card = document.createElement("div");
      card.className = "album-card" + (unlocked ? "" : " locked");
      if (unlocked) {
        card.innerHTML = `<div class="album-emoji">${c.emoji}</div>
          <div class="album-name">${c.name}</div>
          <div class="album-desc">${c.desc}</div>`;
      } else {
        card.innerHTML = `<div class="album-emoji">🔒</div>
          <div class="album-name">???</div>
          <div class="album-desc">A memory not yet made.</div>`;
      }
      grid.appendChild(card);
    });
  }

  /* ---------- Toast ---------- */
  let toastTimer;
  function toast(msg) {
    const t = $("#toast");
    t.textContent = msg;
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove("show"), 2400);
  }

  /* ---------- Wiring ---------- */
  function refreshTitleButtons() {
    if (state.started) {
      $("#btn-continue").classList.remove("hidden");
      $("#btn-start").textContent = "Start Over";
    } else {
      $("#btn-continue").classList.add("hidden");
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    refreshTitleButtons();

    $("#btn-start").addEventListener("click", () => {
      if (state.started) {
        if (!confirm("Start over? Your current save will be reset.")) return;
        resetState();
      }
      state.started = true;
      saveState();
      show("screen-home");
      renderHome();
    });

    $("#btn-continue").addEventListener("click", () => {
      show("screen-home");
      renderHome();
    });

    $("#btn-reset").addEventListener("click", () => {
      if (!confirm("Reset save?")) return;
      resetState();
      refreshTitleButtons();
      toast("Save reset.");
    });

    $("#btn-play-episode").addEventListener("click", () => playEpisode(state.currentEpisodeIdx));
    $("#btn-album").addEventListener("click", () => { renderAlbum(); show("screen-album"); });
    $("#btn-album-back").addEventListener("click", () => { show("screen-home"); renderHome(); });

    // Auto-tick every minute while visible
    setInterval(() => { if ($("#screen-home").classList.contains("active")) renderHome(); }, 60 * 1000);
  });
})();
