/* ═══════════════════════════════════════════════════
   AURA FARM v1 — Brain Rot Slayer
   Pure vanilla JS, localStorage persistence
   ═══════════════════════════════════════════════════ */

// ─── DATA MODEL ───
const DB_KEY = 'aura_farm_data';

const DEFAULT_DATA = {
  packName: '',
  auraPoints: 0,
  habits: {
    starve: [
      { id: 's1', emoji: '📱', name: 'Doomscrolling', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() },
      { id: 's2', emoji: '🍆', name: 'Gooning', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() },
      { id: 's3', emoji: '🛏️', name: 'Rotting in Bed', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() },
      { id: 's4', emoji: '🚬', name: 'Vaping', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() },
      { id: 's5', emoji: '🍔', name: 'Binge Eating', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() }
    ],
    farm: [
      { id: 'f1', emoji: '🏋️', name: 'Gym', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() },
      { id: 'f2', emoji: '📖', name: 'Reading', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() },
      { id: 'f3', emoji: '🧊', name: 'Cold Shower', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() },
      { id: 'f4', emoji: '🧘', name: 'Mewing', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() },
      { id: 'f5', emoji: '💤', name: '8hr Sleep', streak: 0, bestStreak: 0, taps: {}, createdAt: Date.now() }
    ]
  },
  commits: [],
  rewards: [],
  calMonth: new Date().getMonth(),
  calYear: new Date().getFullYear()
};

const REWARDS = [
  { id: 'r1', icon: '🌱', name: 'Aura Seedling', desc: "you exist and that's something i guess 💀", cost: 50 },
  { id: 'r2', icon: '🔥', name: 'Aura Initiate', desc: "not a total NPC anymore, barely", cost: 100 },
  { id: 'r3', icon: '💅', name: 'Main Character Energy', desc: "POV: you actually have discipline", cost: 250 },
  { id: 'r4', icon: '😈', name: 'Rizz Certified', desc: "your rizz is no longer hypothetical, bestie", cost: 500 },
  { id: 'r5', icon: '👑', name: 'Sigma Grindset', desc: "you've out-grinded 99% of NPCs", cost: 1000 },
  { id: 'r6', icon: '🧬', name: 'Glow Up Unlocked', desc: "glow up your fuckability status: ASCENDED", cost: 2000 },
  { id: 'r7', icon: '🌌', name: 'Sigma Overlord', desc: "you've transcended NPC existence entirely", cost: 5000 },
  { id: 'r8', icon: '💀', name: 'Final Boss', desc: "aura so high it's illegal in 47 states", cost: 10000 }
];

const STARVE_ROASTS = [
  "starved that brain rot, you absolute menace 🔥",
  "another day not being a degenerate, no cap 💀",
  "your brain rot is SHOOK rn, keep going 😈",
  "imagine being this disciplined... oh wait, you are 👑",
  "that brain rot is getting starved tf out 🚫",
  "one more day of not being cooked, slay 💅",
  "your future self is literally simping for you rn ✨",
  "brain rot: defeated. aura: increased. hotel: trivago 🧠",
  "you chose violence against your bad habits, respect 🫡",
  "not today, brain rot. NOT. TODAY. 🔥💀"
];

const FARM_HYPES = [
  "ATE THAT SHIT, you absolute slut for progress 🔥✨",
  "aura points go BRRRR, you're literally unhinged 💅",
  "the grind is GRINDING, keep farming bestie 🌾",
  "gigachad energy detected, aura levels UNGODLY 💪",
  "you really said 'lock tf in' and MEANT it 😤🔥",
  "the algorithm can't handle your aura rn 🧠✨",
  "im literally screaming, this grind is BUSSIN 🗣️",
  "your aura just went through the goddamn roof 📈💀",
  "rizz levels: ASTRONOMICAL after that one 🚀",
  "you just gained +10 fuckability, no cap 🍑✨"
];

const RELAPSE_MSGS = [
  "undo detected... you fell off, get back up you degenerate 💀",
  "slipped? it happens bestie. tomorrow we lock back in 😤",
  "brain rot won this round. don't let it win the war 🚫",
  "took an L on this one. your aura took a hit ngl 📉"
];

const SQUAD_NAMES = [
  { avatar: '💀', name: 'xX_RizzLord_Xx' },
  { avatar: '👑', name: 'GigaChadSophie' },
  { avatar: '🍑', name: 'NPC_Slayer_420' },
  { avatar: '🦊', name: 'MewingQueen' },
  { avatar: '🧊', name: 'ColdShowerKing' },
  { avatar: '😈', name: 'SigmaSarah' },
  { avatar: '🔥', name: 'AuraDemon69' },
  { avatar: '💅', name: 'MainCharVibes' },
  { avatar: '🧠', name: 'NoFap_Naruto' },
  { avatar: '✨', name: 'GlowUpGoblin' }
];

const SQUAD_ACTIONS = [
  "hit a {n} day streak on {habit}, absolutely unhinged 🔥",
  "just starved {habit} for {n} days straight, no cap 💀",
  "farmed {habit} today. aura levels: astronomical 📈",
  "relapsed on {habit}... cooked beyond repair 😭💀",
  "just unlocked '{reward}', rizz is maxed out 👑",
  "cold shower at 5am, my aura is ungodly high rn 🧊✨",
  "3am gym session done, these gains are bussin 🏋️",
  "caught myself doomscrolling and chose violence instead 🚫📱",
  "mewing streak: {n} days. jawline arc initiated 🧘",
  "i am become aura, destroyer of brain rot 💀🔥"
];

const EMOJI_OPTIONS = ['📱','🍆','🛏️','🚬','🍔','🎮','🍺','💊','🎰','🤡','🏋️','📖','🧊','🧘','💤','🏃','💧','🥗','📝','🧠','💪','🌅','🎯','🔥'];

const GREETINGS = [
  "sup {name}, you thirsty bitch 💦",
  "look who's back, {name} you degenerate 👀",
  "rise and grind, {name}. lock the fuck in 🔒",
  "{name} is in the building. aura: immeasurable 🧠",
  "oh shit, it's {name}. time to farm some aura ✨",
  "welcome back {name}, you absolute menace 😈"
];

// ─── STATE ───
let data = {};
let currentTab = 'tabDen';

function todayKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

function load() {
  try {
    const raw = localStorage.getItem(DB_KEY);
    if (raw) {
      data = JSON.parse(raw);
      if (!data.habits) data.habits = DEFAULT_DATA.habits;
      if (!data.commits) data.commits = [];
      if (data.calMonth === undefined) { data.calMonth = new Date().getMonth(); data.calYear = new Date().getFullYear(); }
    } else {
      data = JSON.parse(JSON.stringify(DEFAULT_DATA));
    }
  } catch {
    data = JSON.parse(JSON.stringify(DEFAULT_DATA));
  }
}

function save() {
  localStorage.setItem(DB_KEY, JSON.stringify(data));
}

function rand(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
function uid() { return '_' + Math.random().toString(36).slice(2, 9); }

// ─── STREAKS ───
function calcStreak(habit) {
  const today = new Date();
  let streak = 0;
  let d = new Date(today);
  while (true) {
    const key = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    if (habit.taps[key]) {
      streak++;
      d.setDate(d.getDate() - 1);
    } else {
      break;
    }
  }
  habit.streak = streak;
  if (streak > habit.bestStreak) habit.bestStreak = streak;
  return streak;
}

// ─── TOAST SYSTEM ───
function toast(msg, type = 'good') {
  const container = document.getElementById('toastContainer');
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = msg;
  container.appendChild(el);
  setTimeout(() => el.remove(), 3000);
}

// ─── AURA POINT ANIMATION ───
function showAuraChange(amount) {
  const slot = document.getElementById('auraChangeSlot');
  if (slot) {
    const el = document.createElement('div');
    el.className = `aura-change ${amount > 0 ? 'gain' : 'loss'}`;
    el.textContent = amount > 0 ? `+${amount} AP, ate that shit 🔥` : `${amount} AP, you fell tf off 💀`;
    slot.innerHTML = '';
    slot.appendChild(el);
    setTimeout(() => el.remove(), 2000);
  }

  // glow pulse + radial burst on the big number
  const num = document.getElementById('auraNumber');
  if (num) {
    num.classList.remove('glow-gain', 'glow-loss');
    void num.offsetWidth; // reflow to restart animation
    num.classList.add(amount > 0 ? 'glow-gain' : 'glow-loss');
    setTimeout(() => num.classList.remove('glow-gain', 'glow-loss'), 800);

    if (amount > 0) {
      const display = num.closest('.aura-display');
      if (display) {
        const burst = document.createElement('div');
        burst.className = 'aura-burst';
        display.appendChild(burst);
        setTimeout(() => burst.remove(), 800);
      }
    }
  }
}

// floating +AP particle flying up from a tapped element
function floatPoints(srcEl, amount) {
  if (!srcEl) return;
  const rect = srcEl.getBoundingClientRect();
  const el = document.createElement('div');
  el.className = `ap-float ${amount > 0 ? 'gain' : 'loss'}`;
  el.textContent = amount > 0 ? `+${amount}` : `${amount}`;
  el.style.left = (rect.left + rect.width / 2) + 'px';
  el.style.top = (rect.top - 8) + 'px';
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 1000);
}

function updateAuraDisplay() {
  const el = document.getElementById('auraNumber');
  if (el) el.textContent = data.auraPoints.toLocaleString();
}

// ─── RENDER HABITS ───
function renderHabits() {
  renderHabitList('starve', 'brainRotList');
  renderHabitList('farm', 'auraFarmList');
  renderQuitTimers();
  updateStats();
  updateAuraDisplay();
}

function renderHabitList(type, containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  const habits = data.habits[type] || [];
  const today = todayKey();

  if (habits.length === 0) {
    container.innerHTML = `<div class="text-center text-muted" style="padding:20px;font-size:0.75rem;">
      ${type === 'starve' ? 'no brain rot tracked yet... you lying or what? 🤨' : 'no aura habits yet... add something, NPC 💀'}
    </div>`;
    return;
  }

  container.innerHTML = habits.map(h => {
    const tapped = !!h.taps[today];
    const streak = calcStreak(h);
    const tapClass = tapped ? (type === 'starve' ? 'tapped-bad' : 'tapped') : '';
    const streakText = streak > 0
      ? `🔥 ${streak}d streak, you slut for progress`
      : (type === 'starve' ? 'no streak, you cooked 💀' : 'no streak, lock tf in 🔒');
    return `
      <div class="habit-item">
        <span class="habit-emoji">${h.emoji}</span>
        <div class="habit-info">
          <div class="habit-name">${h.name}</div>
          <div class="habit-streak">${streakText} · best: ${h.bestStreak}d</div>
        </div>
        <button class="habit-tap ${tapClass}" data-tap="${h.id}" onclick="tapHabit('${type}','${h.id}',event)" title="${tapped ? 'undo, you fell off' : 'tap it, lock in'}">
          ${tapped ? '✓' : (type === 'starve' ? '🚫' : '✨')}
        </button>
        <button class="habit-delete" onclick="deleteHabit('${type}','${h.id}')" title="yeet this habit">✕</button>
      </div>
    `;
  }).join('');
}

// ─── TAP HABIT ───
function tapHabit(type, id, evt) {
  const habit = data.habits[type].find(h => h.id === id);
  if (!habit) return;
  const today = todayKey();

  // grab the button that was tapped for ring + particle fx
  const btn = evt ? evt.currentTarget : document.querySelector(`[data-tap="${id}"]`);

  if (habit.taps[today]) {
    delete habit.taps[today];
    calcStreak(habit);
    const loss = type === 'starve' ? 5 : 10;
    data.auraPoints = Math.max(0, data.auraPoints - loss);
    showAuraChange(-loss);
    floatPoints(btn, -loss);
    spawnRing(btn, true);
    toast(rand(RELAPSE_MSGS), 'bad');
  } else {
    habit.taps[today] = true;
    const gain = type === 'starve' ? 10 : 15;
    const streakBonus = Math.min(calcStreak(habit) * 2, 50);
    const total = gain + streakBonus;
    data.auraPoints += total;
    showAuraChange(total);
    floatPoints(btn, total);
    spawnRing(btn, type === 'starve');
    toast(rand(type === 'starve' ? STARVE_ROASTS : FARM_HYPES), 'good');
  }

  calcStreak(habit);
  save();
  renderHabits();
}

// expanding ring fx over the tapped button (body-anchored so the
// list re-render doesn't nuke the animation mid-play)
function spawnRing(btn, isBad) {
  if (!btn) return;
  const r = btn.getBoundingClientRect();
  const ring = document.createElement('span');
  ring.className = 'tap-ring' + (isBad ? ' bad' : '');
  ring.style.position = 'fixed';
  ring.style.left = r.left + 'px';
  ring.style.top = r.top + 'px';
  ring.style.width = r.width + 'px';
  ring.style.height = r.height + 'px';
  ring.style.inset = 'auto';
  ring.style.zIndex = '998';
  document.body.appendChild(ring);
  setTimeout(() => ring.remove(), 600);
}

// ─── DELETE HABIT ───
function deleteHabit(type, id) {
  data.habits[type] = data.habits[type].filter(h => h.id !== id);
  save();
  renderHabits();
  toast('habit yeeted into the void 🕳️', 'bad');
}

// ─── ADD HABIT MODAL ───
function openAddHabit(type) {
  const modal = document.getElementById('modalContent');
  const title = type === 'starve' ? '💀 ADD BRAIN ROT TO STARVE' : '✨ ADD AURA HABIT TO FARM';
  const placeholder = type === 'starve' ? 'e.g. doomscrolling, vaping...' : 'e.g. gym, reading, mewing...';

  modal.innerHTML = `
    <h3>${title}</h3>
    <label style="font-size:0.7rem;color:var(--fg2);margin-bottom:4px;display:block;">pick an emoji, bestie</label>
    <div class="emoji-picker" id="emojiPicker">
      ${EMOJI_OPTIONS.map((e, i) => `<button class="emoji-opt ${i===0?'selected':''}" onclick="pickEmoji(this,'${e}')" data-emoji="${e}">${e}</button>`).join('')}
    </div>
    <input type="text" id="habitNameInput" placeholder="${placeholder}" maxlength="40" autofocus>
    <div class="modal-actions">
      <button class="btn" onclick="closeModal()">nvm 💀</button>
      <button class="btn btn-accent" onclick="addHabit('${type}')">add it 🔥</button>
    </div>
  `;
  document.getElementById('modalOverlay').style.display = 'flex';
  setTimeout(() => document.getElementById('habitNameInput')?.focus(), 100);
}

let selectedEmoji = EMOJI_OPTIONS[0];
function pickEmoji(btn, emoji) {
  selectedEmoji = emoji;
  document.querySelectorAll('.emoji-opt').forEach(e => e.classList.remove('selected'));
  btn.classList.add('selected');
}

function addHabit(type) {
  const name = document.getElementById('habitNameInput')?.value.trim();
  if (!name) { toast("name it something, you NPC 💀", 'bad'); return; }

  data.habits[type].push({
    id: uid(),
    emoji: selectedEmoji,
    name: name,
    streak: 0,
    bestStreak: 0,
    taps: {},
    createdAt: Date.now()
  });

  save();
  renderHabits();
  closeModal();
  toast(type === 'starve'
    ? `"${name}" added to brain rot hit list 💀🔥`
    : `"${name}" added to the aura farm ✨`, 'good');
  selectedEmoji = EMOJI_OPTIONS[0];
}

// ─── MODAL ───
function closeModal(e) {
  if (e && e.target !== document.getElementById('modalOverlay')) return;
  document.getElementById('modalOverlay').style.display = 'none';
}

// ─── QUIT TIMERS ───
function renderQuitTimers() {
  const container = document.getElementById('quitTimerList');
  if (!container) return;
  const habits = data.habits.starve || [];

  if (habits.length === 0) {
    container.innerHTML = '<div class="text-center text-muted" style="padding:20px;font-size:0.75rem;">no brain rot to quit yet... sus 🤨</div>';
    return;
  }

  container.innerHTML = habits.map(h => {
    const streak = calcStreak(h);
    const days = streak;
    const hrs = days * 24;
    let timerText, motivText;

    if (days === 0) {
      timerText = "0 days";
      motivText = "haven't even started? cooked af 💀";
    } else if (days < 3) {
      timerText = `${days} day${days > 1 ? 's' : ''}`;
      motivText = "baby steps, don't fuck it up 👶";
    } else if (days < 7) {
      timerText = `${days} days (${hrs}h)`;
      motivText = "mid but getting there, keep starving it 🔥";
    } else if (days < 30) {
      timerText = `${days} days`;
      motivText = "actually impressive ngl, your brain rot is SHOOK 😤";
    } else if (days < 100) {
      timerText = `${days} days`;
      motivText = "GIGACHAD ENERGY, brain rot is on life support 💀🔥";
    } else {
      timerText = `${days} days`;
      motivText = "you've literally ascended, what even are you 🧬✨";
    }

    return `
      <div class="card" style="margin:8px 0;">
        <div class="flex-between">
          <div>
            <span style="font-size:1.2rem;">${h.emoji}</span>
            <strong style="font-size:0.85rem;margin-left:6px;">${h.name}</strong>
          </div>
          <span class="text-accent" style="font-size:0.75rem;font-weight:700;">${timerText}</span>
        </div>
        <div class="text-muted mt-1" style="font-size:0.7rem;">${motivText}</div>
        <div style="margin-top:8px;height:4px;background:var(--bg);border-radius:2px;overflow:hidden;">
          <div style="width:${Math.min(100, (days / 30) * 100)}%;height:100%;background:${days > 0 ? 'var(--accent)' : 'var(--danger)'};border-radius:2px;transition:width 0.3s;"></div>
        </div>
      </div>
    `;
  }).join('');
}

// ─── COMMIT LOG ───
function addCommit() {
  const input = document.getElementById('commitInput');
  const text = input.value.trim();
  if (!text) { toast("write something you NPC 💀", 'bad'); return; }

  data.commits.unshift({
    id: uid(),
    text: text,
    date: todayKey(),
    time: new Date().toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})
  });

  data.auraPoints += 5;
  showAuraChange(5);
  save();
  input.value = '';
  renderCommitLog();
  updateAuraDisplay();
  toast("commit logged, you're actually journaling? slay 📝✨", 'good');
}

function renderCommitLog() {
  const container = document.getElementById('commitLog');
  if (!container) return;

  if (data.commits.length === 0) {
    container.innerHTML = '<div class="text-center text-muted mt-2" style="font-size:0.75rem;">no commits yet... the log is empty, just like your discipline 💀</div>';
    return;
  }

  container.innerHTML = data.commits.slice(0, 30).map(c => `
    <div class="code-block" style="padding:10px;margin:6px 0;">
      <span class="comment">// ${c.date} @ ${c.time}</span><br>
      <span class="string">"${escapeHtml(c.text)}"</span>
      <button class="habit-delete" style="opacity:1;float:right;" onclick="deleteCommit('${c.id}')" title="yeet this">✕</button>
    </div>
  `).join('');
}

function deleteCommit(id) {
  data.commits = data.commits.filter(c => c.id !== id);
  save();
  renderCommitLog();
}

function escapeHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

// ─── REWARDS ───
function renderRewards() {
  const container = document.getElementById('rewardsList');
  if (!container) return;

  container.innerHTML = REWARDS.map(r => {
    const unlocked = data.auraPoints >= r.cost;
    const claimed = (data.rewards || []).includes(r.id);
    return `
      <div class="reward-item ${unlocked ? 'unlocked' : 'locked'}">
        <span class="reward-icon">${unlocked ? r.icon : '🔒'}</span>
        <div class="reward-info">
          <div class="reward-name">${r.name} ${claimed ? '<span class="text-accent">✓ claimed</span>' : ''}</div>
          <div class="reward-desc">${r.desc}</div>
        </div>
        <div style="text-align:right;">
          <div class="reward-cost">${r.cost >= 1000 ? (r.cost/1000)+'K' : r.cost} AP</div>
          ${unlocked && !claimed ? `<button class="btn btn-accent btn-sm" style="margin-top:4px;" onclick="claimReward('${r.id}')">claim</button>` : ''}
        </div>
      </div>
    `;
  }).join('');
}

function claimReward(id) {
  if (!data.rewards) data.rewards = [];
  if (data.rewards.includes(id)) return;
  const r = REWARDS.find(x => x.id === id);
  if (!r || data.auraPoints < r.cost) return;
  data.rewards.push(id);
  save();
  renderRewards();
  toast(`🏆 ${r.name} CLAIMED — ${r.desc}`, 'good');
}

// ─── CALENDAR ───
function renderCalendar(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;

  const month = data.calMonth;
  const year = data.calYear;
  const label = document.getElementById('calMonthLabel');
  if (label) label.textContent = new Date(year, month).toLocaleDateString('en', { month: 'long', year: 'numeric' });

  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const today = new Date();

  const allTapDays = new Set();
  ['starve', 'farm'].forEach(type => {
    (data.habits[type] || []).forEach(h => {
      Object.keys(h.taps).forEach(k => allTapDays.add(k));
    });
  });

  const headers = ['Su','Mo','Tu','We','Th','Fr','Sa'];
  let html = '<div class="calendar-grid">';
  headers.forEach(h => { html += `<div class="cal-header">${h}</div>`; });

  for (let i = 0; i < firstDay; i++) {
    html += '<div class="cal-day empty"></div>';
  }

  for (let d = 1; d <= daysInMonth; d++) {
    const key = `${year}-${String(month+1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
    const isToday = d === today.getDate() && month === today.getMonth() && year === today.getFullYear();
    const hasTaps = allTapDays.has(key);
    const classes = ['cal-day'];
    if (isToday) classes.push('today');
    if (hasTaps) classes.push('has-taps');
    html += `<div class="${classes.join(' ')}">${d}</div>`;
  }

  html += '</div>';
  container.innerHTML = html;
}

function calNav(dir) {
  data.calMonth += dir;
  if (data.calMonth > 11) { data.calMonth = 0; data.calYear++; }
  if (data.calMonth < 0) { data.calMonth = 11; data.calYear--; }
  save();
  renderCalendar('appCalendar');
}

// ─── STATS ───
function updateStats() {
  const container = document.getElementById('statsRow');
  if (!container) return;

  const allHabits = [...(data.habits.starve || []), ...(data.habits.farm || [])];
  const today = todayKey();
  const todayTaps = allHabits.filter(h => h.taps[today]).length;
  const totalHabits = allHabits.length;
  const bestStreak = allHabits.reduce((max, h) => Math.max(max, h.bestStreak), 0);
  const totalDays = new Set();
  allHabits.forEach(h => Object.keys(h.taps).forEach(k => totalDays.add(k)));

  container.innerHTML = `
    <div class="stat-box">
      <div class="stat-num">${todayTaps}/${totalHabits}</div>
      <div class="stat-label">locked in today</div>
    </div>
    <div class="stat-box">
      <div class="stat-num">${bestStreak}</div>
      <div class="stat-label">best streak</div>
    </div>
    <div class="stat-box">
      <div class="stat-num">${totalDays.size}</div>
      <div class="stat-label">days grinding</div>
    </div>
  `;
}

// ─── SQUAD FEED ───
function renderSquadFeed() {
  const container = document.getElementById('squadFeed');
  if (!container) return;

  const habits = ['doomscrolling','gooning','gym','mewing','cold showers','reading','vaping','sleep schedule'];
  const rewards = REWARDS.map(r => r.name);
  const times = ['just now','2m ago','5m ago','12m ago','23m ago','1h ago','2h ago','3h ago','5h ago','yesterday'];

  const items = [];
  for (let i = 0; i < 8; i++) {
    const person = rand(SQUAD_NAMES);
    let action = rand(SQUAD_ACTIONS);
    action = action.replace('{n}', Math.floor(Math.random() * 60) + 1);
    action = action.replace('{habit}', rand(habits));
    action = action.replace('{reward}', rand(rewards));

    items.push(`
      <div class="squad-item">
        <span class="squad-avatar">${person.avatar}</span>
        <div>
          <div><span class="squad-name">${person.name}</span> <span class="squad-time">${times[i] || rand(times)}</span></div>
          <div class="squad-msg">${action}</div>
        </div>
      </div>
    `);
  }

  container.innerHTML = items.join('');
}

// ─── GREETING ───
function renderGreeting() {
  const el = document.getElementById('appGreeting');
  if (!el) return;
  const name = data.packName || 'Anon';
  const template = rand(GREETINGS);
  const greeting = template.replace('{name}', name);

  const hour = new Date().getHours();
  let timeMsg;
  if (hour < 5) timeMsg = "it's late af, go to sleep you degenerate 🌙";
  else if (hour < 9) timeMsg = "early bird energy, aura farming before the NPCs wake up 🌅";
  else if (hour < 12) timeMsg = "morning grind activated, let's get this aura 🔥";
  else if (hour < 17) timeMsg = "afternoon check-in, don't slip now bestie 👀";
  else if (hour < 21) timeMsg = "evening session, starve that brain rot before bed 🧠";
  else timeMsg = "night owl mode, don't let the brain rot creep in 🦉";

  el.innerHTML = `
    <div class="greeting-name"><span class="text-purple">$</span> ${greeting}</div>
    <div class="greeting-sub"># ${timeMsg}</div>
  `;
}

// ─── NAV ───
function switchTab(tabId, btn) {
  document.querySelectorAll('#screenApp > .container > .screen').forEach(s => s.classList.remove('active'));
  document.getElementById(tabId)?.classList.add('active');
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  if (btn) btn.classList.add('active');
  currentTab = tabId;

  if (tabId === 'tabCalendar') renderCalendar('appCalendar');
  if (tabId === 'tabSquad') renderSquadFeed();
  if (tabId === 'tabReward') renderRewards();
  if (tabId === 'tabCommit') renderCommitLog();
  if (tabId === 'tabQuit') renderQuitTimers();
}

// ─── APP ENTRY ───
function enterApp() {
  if (!data.packName) {
    const modal = document.getElementById('modalContent');
    modal.innerHTML = `
      <h3>🧠 WHAT'S YOUR NAME, BESTIE?</h3>
      <p style="font-size:0.7rem;color:var(--fg2);margin-bottom:12px;">this is your pack name. make it go hard or don't bother 🔥</p>
      <input type="text" id="packNameInput" placeholder="e.g. RizzLord, GigaChad, BrainRotSlayer..." maxlength="20" autofocus>
      <div class="modal-actions">
        <button class="btn btn-accent btn-block" onclick="setPackName()">lock in → 🔒</button>
      </div>
    `;
    document.getElementById('modalOverlay').style.display = 'flex';
    setTimeout(() => document.getElementById('packNameInput')?.focus(), 100);
    return;
  }

  activateApp();
}

function setPackName() {
  const name = document.getElementById('packNameInput')?.value.trim();
  if (!name) { toast("name yourself, coward 💀", 'bad'); return; }
  data.packName = name;
  save();
  closeModal();
  activateApp();
  toast(`welcome to the farm, ${name} 🔥✨ now lock tf in`, 'good');
}

function activateApp() {
  document.getElementById('screenLanding').classList.remove('active');
  document.getElementById('screenApp').classList.add('active');
  document.getElementById('bottomNav').style.display = 'flex';
  document.getElementById('headerRight').innerHTML = `
    <button class="btn btn-sm" onclick="resetApp()" title="nuke it all, you masochist">🗑️</button>
    <button class="btn btn-sm" onclick="goLanding()" title="dip out to the landing">←</button>
  `;
  renderGreeting();
  renderHabits();
  renderCommitLog();
  renderRewards();
  renderCalendar('appCalendar');
}

function goLanding() {
  document.getElementById('screenApp').classList.remove('active');
  document.getElementById('screenLanding').classList.add('active');
  document.getElementById('bottomNav').style.display = 'none';
  document.getElementById('headerRight').innerHTML = `
    <button class="btn btn-sm" id="btnSignIn" onclick="showSignIn()">sign in</button>
  `;
}

function resetApp() {
  const modal = document.getElementById('modalContent');
  modal.innerHTML = `
    <h3>💀 RESET EVERYTHING?</h3>
    <p style="font-size:0.75rem;color:var(--fg2);margin-bottom:16px;">
      this will nuke all your data. streaks, aura points, habits — everything gone.<br>
      are you absolutely sure, you masochist? 🗑️
    </p>
    <div class="modal-actions">
      <button class="btn" onclick="closeModal()">nah im good</button>
      <button class="btn btn-danger" onclick="confirmReset()">nuke it all 💀</button>
    </div>
  `;
  document.getElementById('modalOverlay').style.display = 'flex';
}

function confirmReset() {
  localStorage.removeItem(DB_KEY);
  data = JSON.parse(JSON.stringify(DEFAULT_DATA));
  save();
  closeModal();
  goLanding();
  toast("everything nuked. fresh start, you degenerate 💀🔥", 'bad');
}

// ─── SIGN IN (FAKE) ───
function showSignIn() {
  const modal = document.getElementById('modalContent');
  modal.innerHTML = `
    <h3>🔒 SIGN IN</h3>
    <p style="font-size:0.7rem;color:var(--fg2);margin-bottom:12px;">
      lmao this is a local-first PWA bestie. your data lives in your browser.<br>
      no accounts, no servers, no bullshit. privacy is bussin. 🫡
    </p>
    <p style="font-size:0.7rem;color:var(--accent);margin-bottom:12px;">
      just hit "Enter your Zone" to start farming aura ✨
    </p>
    <div class="modal-actions">
      <button class="btn btn-accent btn-block" onclick="closeModal()">got it, no cap 🔥</button>
    </div>
  `;
  document.getElementById('modalOverlay').style.display = 'flex';
}

// ─── LANDING CALENDAR ───
function renderLandingCalendar() {
  const container = document.getElementById('landingCalendar');
  if (!container) return;
  const now = new Date();
  const month = now.getMonth();
  const year = now.getFullYear();
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const headers = ['Su','Mo','Tu','We','Th','Fr','Sa'];
  let html = `<div style="text-align:center;font-size:0.75rem;color:var(--accent);margin-bottom:8px;">${now.toLocaleDateString('en',{month:'long',year:'numeric'})}</div>`;
  html += '<div class="calendar-grid">';
  headers.forEach(h => { html += `<div class="cal-header">${h}</div>`; });
  for (let i = 0; i < firstDay; i++) html += '<div class="cal-day empty"></div>';
  for (let d = 1; d <= daysInMonth; d++) {
    const isToday = d === now.getDate();
    const fakeActive = d <= now.getDate() && Math.random() > 0.3;
    const classes = ['cal-day'];
    if (isToday) classes.push('today');
    if (fakeActive && d < now.getDate()) classes.push('has-taps');
    html += `<div class="${classes.join(' ')}">${d}</div>`;
  }
  html += '</div>';
  container.innerHTML = html;
}

// recalc every streak/bestStreak from tap history & persist (fixes streaks not saving on load)
function recalcAllStreaks() {
  ['starve', 'farm'].forEach(type => {
    (data.habits[type] || []).forEach(h => {
      if (!h.taps) h.taps = {};
      if (typeof h.bestStreak !== 'number') h.bestStreak = 0;
      calcStreak(h);
    });
  });
  save();
}

// ─── INIT ───
function init() {
  load();
  recalcAllStreaks();
  renderLandingCalendar();

  if (data.packName) {
    activateApp();
  }

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }

  document.getElementById('commitInput')?.addEventListener('keydown', e => {
    if (e.key === 'Enter') addCommit();
  });
}

document.addEventListener('DOMContentLoaded', init);
