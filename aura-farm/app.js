/* ═══════════════════════════════════════════════════
   AURA FARM v1 — Brain Rot Slayer
   Pure vanilla JS, localStorage persistence
   ═══════════════════════════════════════════════════ */

// ─── DATA MODEL ───
const DB_KEY = 'aura_farm_data';

const DEFAULT_DATA = {
  packName: '',
  auraPoints: 0,
  freezes: 3,
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
  owned: [],
  equipped: { avatar: 'av_brain', theme: 'th_default', title: null },
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

// ─── THEME BASE VARS (defaults that themes override) ───
const BASE_THEME = {
  '--bg': '#0a0a0a', '--bg2': '#111111', '--bg3': '#1a1a1a',
  '--accent': '#c8ff00', '--danger': '#ff3e6c', '--purple': '#b388ff', '--cyan': '#00e5ff'
};

// ─── SHOP: redeem aura points for explicit customizations ───
const SHOP = {
  avatars: [
    { id: 'av_brain',  preview: '🧠',  name: 'Default NPC Brain',    desc: 'the broke starter look. mid.',                      cost: 0 },
    { id: 'av_clown',  preview: '🤡',  name: 'Simp Clown',           desc: 'for when you fumbled the bag fr',                   cost: 80 },
    { id: 'av_skull',  preview: '💀',  name: 'Cooked Skull',         desc: 'certified down bad. own it.',                       cost: 120 },
    { id: 'av_drool',  preview: '🤤',  name: 'Down Bad Drooler',     desc: 'thirsty lil menace energy',                         cost: 180 },
    { id: 'av_peach',  preview: '🍑',  name: 'Certified Cake',       desc: 'all aura, all ass, no cap',                         cost: 250 },
    { id: 'av_devil',  preview: '😈',  name: 'Horny Lil Devil',      desc: 'horns OUT, aura unhinged 🔥',                       cost: 300 },
    { id: 'av_hot',    preview: '🥵',  name: 'Insufferably Hot',     desc: 'you a whole snack and you know it',                  cost: 400 },
    { id: 'av_demon',  preview: '👹',  name: 'Goon Demon Boss',      desc: 'final form of a reformed degenerate',               cost: 600 },
    { id: 'av_alien',  preview: '👽',  name: 'Sigma Alien',          desc: 'aura not even from this dimension',                 cost: 900 },
    { id: 'av_goat',   preview: '🐐',  name: 'Literal GOAT',         desc: 'greatest aura farmer of all time',                  cost: 1500 }
  ],
  themes: [
    { id: 'th_default', name: 'Degenerate Default',  desc: 'the OG lime-green grindset', vars: {},                                                              cost: 0 },
    { id: 'th_slut',    name: 'Slutty Red 💋',        desc: 'paint the town red you menace', vars: { '--accent': '#ff2d55', '--purple': '#ff7eb6' },              cost: 250 },
    { id: 'th_femboy',  name: 'Femboy Pink 🎀',       desc: 'soft but feral, ate that look', vars: { '--accent': '#ff8fd6', '--cyan': '#ffc1f0' },                cost: 300 },
    { id: 'th_goon',    name: 'Goon Cave Purple 🍆',  desc: 'lights off, aura on', vars: { '--accent': '#b388ff', '--bg': '#0c0716', '--bg2': '#140d22' },        cost: 350 },
    { id: 'th_cyber',   name: 'Cyber Rizz 🤖',        desc: 'neon teal, rizz from 3024', vars: { '--accent': '#00ffe0', '--danger': '#ff4d6d' },               cost: 450 },
    { id: 'th_sigma',   name: 'Sigma Gold 👑',        desc: 'drip so loud it has a sound', vars: { '--accent': '#ffd700', '--purple': '#ffe98a' },               cost: 600 },
    { id: 'th_demon',   name: 'Demon Mode 😈',        desc: 'blood-red, zero chill', vars: { '--accent': '#ff2030', '--bg': '#160000', '--bg2': '#220404', '--danger': '#ff5c5c' }, cost: 900 }
  ],
  titles: [
    { id: 'ti_npc',    name: 'Reformed NPC',        desc: 'barely sentient but trying', cost: 60 },
    { id: 'ti_goon',   name: 'Certified Goon',      desc: 'wear the shame with pride',  cost: 120 },
    { id: 'ti_slut',   name: 'Slut for Progress',   desc: 'down astronomical for gains', cost: 220 },
    { id: 'ti_rizz',   name: 'Rizzler Supreme',     desc: 'unspoken rizz, spoken aura', cost: 400 },
    { id: 'ti_demon',  name: 'Aura Demon',          desc: 'feeds on brain rot corpses', cost: 650 },
    { id: 'ti_sigma',  name: 'Sigma Overlord',      desc: 'touched grass, touched god',  cost: 1200 }
  ]
};


const REDEEM_HYPE = [
  "REDEEMED 🔥 you spent aura like a high roller, respect",
  "purchase made, drip acquired, aura demolished 💸✨",
  "ka-ching 🤑 your fuckability just went up a tier",
  "bought it. flexed it. you absolute menace 😈"
];
const BROKE_ROASTS = [
  "broke bitch alert 🚨 farm more aura before you window shop 💀",
  "you can't afford that, go starve some brain rot first 😭",
  "insufficient aura, NPC. grind harder you degenerate 💸💀",
  "lmao you're aura-broke. tap some habits and come back 🫵"
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

// ─── STREAK FREEZE + BROKEN-STREAK ROASTS ───
const FREEZE_SAVES = [
  "🧊 streak FROZEN, your aura's safe this time you lucky bitch",
  "🧊 freeze token spent — chain protected. don't make slacking a habit",
  "🧊 saved your streak from the void, you owe me one degenerate",
  "🧊 cryo-locked that streak. now lock tf back in tomorrow, slut",
  "🧊 brain rot ALMOST won, but we froze that L. clutch af"
];
const FREEZE_BROKE = [
  "out of freeze tokens, you broke bitch 💀 go farm some aura",
  "no freezes left — your streak's cooked if you slip, degenerate 🧊❌",
  "freeze inventory: empty. natural selection time bestie 💀",
  "you fumbled all your freezes already? skill issue fr 🧊💀"
];
const FREEZE_NOTNEEDED = [
  "yesterday's already locked in, save your freeze you hoarder 🧊",
  "no gap to freeze bestie, you're already on streak ✨",
  "nothing to save here, you're not even cooked yet 😌"
];
const STREAK_BROKEN_ROASTS = [
  "💀 your '{name}' streak BROKE. all that grind, gone. you fumbled the bag fr",
  "💀 '{name}' chain snapped. the NPCs are laughing at you rn",
  "💀 '{name}' streak reset to 0. hope that slip was worth it bestie",
  "💀 you let '{name}' die. embarrassing. lock back in immediately, degenerate"
];

// ─── HABIT SUGGESTIONS (suggestive examples) ───
const HABIT_SUGGESTIONS = {
  starve: [
    { emoji: '🍆', name: 'Gooning' },
    { emoji: '🥵', name: 'Edging' },
    { emoji: '💦', name: 'Breaking No-Nut' },
    { emoji: '🥺', name: 'Simp Behavior' },
    { emoji: '📸', name: 'Thirst Trapping' },
    { emoji: '💌', name: 'Sliding into DMs' },
    { emoji: '🔞', name: 'Watching Porn' },
    { emoji: '💸', name: 'Subbing to OnlyFans' },
    { emoji: '📱', name: 'Doomscrolling' },
    { emoji: '👀', name: 'Stalking the Ex' },
    { emoji: '🛏️', name: 'Rotting in Bed' },
    { emoji: '🚬', name: 'Vaping' },
    { emoji: '🍺', name: 'Drinking' },
    { emoji: '🎰', name: 'Gambling' },
    { emoji: '🍔', name: 'Binge Eating' },
    { emoji: '🎮', name: 'Rage Gaming' }
  ],
  farm: [
    { emoji: '🧘', name: 'Mewing' },
    { emoji: '🔒', name: 'No-Nut Streak' },
    { emoji: '😎', name: 'Cold Approach Practice' },
    { emoji: '💬', name: 'Rizz Practice' },
    { emoji: '🪞', name: 'Looksmaxxing' },
    { emoji: '🏋️', name: 'Gym' },
    { emoji: '🧊', name: 'Cold Shower' },
    { emoji: '📖', name: 'Reading' },
    { emoji: '📝', name: 'Journaling' },
    { emoji: '🌅', name: '5AM Wake Up' },
    { emoji: '💤', name: '8hr Sleep' },
    { emoji: '🌱', name: 'Touching Grass' },
    { emoji: '💧', name: 'Hydrating' },
    { emoji: '🥗', name: 'Eating Clean' },
    { emoji: '🏃', name: 'Running' },
    { emoji: '🧠', name: 'Deep Work' }
  ]
};

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

const EMOJI_OPTIONS = ['📱','🍆','🥵','💦','🥺','📸','💌','🔞','💸','👀','🛏️','🚬','🍺','🎰','🍔','🎮','🔒','😎','💬','🪞','🏋️','📖','🧊','🧘','💤','🏃','💧','🥗','📝','🧠','💪','🌅','🌱','🎯','🔥'];

const GREETINGS = [
  "sup {name}, you thirsty bitch 💦",
  "look who's back, {name} you degenerate 👀",
  "rise and grind, {name}. lock the fuck in 🔒",
  "{name} is in the building. aura: immeasurable 🧠",
  "oh shit, it's {name}. time to farm some aura ✨",
  "welcome back {name}, you absolute menace 😈"
];

// ─── SOUND FX (Web Audio API — zero external files) ───
let audioCtx = null;
function getAudioCtx() {
  if (!audioCtx) try { audioCtx = new (window.AudioContext || window.webkitAudioContext)(); } catch {}
  return audioCtx;
}
function sfx(type) {
  const ctx = getAudioCtx();
  if (!ctx) return;
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.connect(gain);
  gain.connect(ctx.destination);
  const t = ctx.currentTime;
  if (type === 'tap') {
    osc.type = 'sine';
    osc.frequency.setValueAtTime(600, t);
    osc.frequency.exponentialRampToValueAtTime(1200, t + 0.08);
    gain.gain.setValueAtTime(0.18, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.12);
    osc.start(t); osc.stop(t + 0.12);
  } else if (type === 'untap') {
    osc.type = 'sine';
    osc.frequency.setValueAtTime(800, t);
    osc.frequency.exponentialRampToValueAtTime(300, t + 0.12);
    gain.gain.setValueAtTime(0.15, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.15);
    osc.start(t); osc.stop(t + 0.15);
  } else if (type === 'buy') {
    osc.type = 'square';
    osc.frequency.setValueAtTime(523, t);
    osc.frequency.setValueAtTime(659, t + 0.08);
    osc.frequency.setValueAtTime(784, t + 0.16);
    gain.gain.setValueAtTime(0.12, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.28);
    osc.start(t); osc.stop(t + 0.28);
  } else if (type === 'freeze') {
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(1800, t);
    osc.frequency.exponentialRampToValueAtTime(400, t + 0.2);
    gain.gain.setValueAtTime(0.1, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.25);
    osc.start(t); osc.stop(t + 0.25);
  } else if (type === 'error') {
    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(200, t);
    osc.frequency.setValueAtTime(180, t + 0.08);
    gain.gain.setValueAtTime(0.1, t);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.18);
    osc.start(t); osc.stop(t + 0.18);
  }
}
function haptic(ms) { try { navigator.vibrate(ms || 12); } catch {} }

// ─── CONFETTI SYSTEM ───
const confettiParticles = [];
let confettiCanvas = null;
let confettiCtx = null;
let confettiRAF = null;

function initConfetti() {
  confettiCanvas = document.getElementById('confettiCanvas');
  if (!confettiCanvas) return;
  confettiCtx = confettiCanvas.getContext('2d');
  resizeConfetti();
  window.addEventListener('resize', resizeConfetti);
}
function resizeConfetti() {
  if (!confettiCanvas) return;
  confettiCanvas.width = window.innerWidth;
  confettiCanvas.height = window.innerHeight;
}
function spawnConfetti(count) {
  if (!confettiCtx) return;
  const colors = ['#c8ff00', '#ff3e6c', '#b388ff', '#00e5ff', '#ff9100', '#ffd700', '#ff8fd6'];
  for (let i = 0; i < count; i++) {
    confettiParticles.push({
      x: window.innerWidth / 2 + (Math.random() - 0.5) * 200,
      y: window.innerHeight * 0.35,
      vx: (Math.random() - 0.5) * 12,
      vy: -(Math.random() * 8 + 4),
      w: Math.random() * 8 + 4,
      h: Math.random() * 6 + 3,
      color: colors[Math.floor(Math.random() * colors.length)],
      rotation: Math.random() * 360,
      rotSpeed: (Math.random() - 0.5) * 15,
      life: 1,
      decay: 0.008 + Math.random() * 0.008
    });
  }
  if (!confettiRAF) animateConfetti();
}
function animateConfetti() {
  if (!confettiCtx) return;
  confettiCtx.clearRect(0, 0, confettiCanvas.width, confettiCanvas.height);
  for (let i = confettiParticles.length - 1; i >= 0; i--) {
    const p = confettiParticles[i];
    p.x += p.vx;
    p.y += p.vy;
    p.vy += 0.18;
    p.vx *= 0.99;
    p.rotation += p.rotSpeed;
    p.life -= p.decay;
    if (p.life <= 0) { confettiParticles.splice(i, 1); continue; }
    confettiCtx.save();
    confettiCtx.translate(p.x, p.y);
    confettiCtx.rotate((p.rotation * Math.PI) / 180);
    confettiCtx.globalAlpha = p.life;
    confettiCtx.fillStyle = p.color;
    confettiCtx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
    confettiCtx.restore();
  }
  if (confettiParticles.length > 0) {
    confettiRAF = requestAnimationFrame(animateConfetti);
  } else {
    confettiRAF = null;
  }
}

// ─── STATE ───
let data = {};
let currentTab = 'tabDen';

function dateKey(d) {
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}
function todayKey() { return dateKey(new Date()); }
function yesterdayKey() {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return dateKey(d);
}

function load() {
  try {
    const raw = localStorage.getItem(DB_KEY);
    if (raw) {
      data = JSON.parse(raw);
      if (!data.habits) data.habits = DEFAULT_DATA.habits;
      if (!data.commits) data.commits = [];
      if (typeof data.freezes !== 'number') data.freezes = 3;
      if (data.calMonth === undefined) { data.calMonth = new Date().getMonth(); data.calYear = new Date().getFullYear(); }
      if (!Array.isArray(data.owned)) data.owned = [];
      if (!data.equipped) data.equipped = { avatar: 'av_brain', theme: 'th_default', title: null };
      if (!data.owned.includes('av_brain')) data.owned.push('av_brain');
      if (!data.owned.includes('th_default')) data.owned.push('th_default');
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
// A day "counts" if it's tapped (true) OR frozen ('frozen').
// Today is a grace period: not tapping today yet doesn't nuke the streak —
// we start counting from yesterday until a full day is actually missed.
function calcStreak(habit) {
  let streak = 0;
  let d = new Date();
  if (!habit.taps[dateKey(d)]) {
    d.setDate(d.getDate() - 1); // today still in progress, count from yesterday
  }
  while (habit.taps[dateKey(d)]) {
    streak++;
    d.setDate(d.getDate() - 1);
  }
  habit.streak = streak;
  if (streak > habit.bestStreak) habit.bestStreak = streak;
  return streak;
}

// count how many days in the current streak were saved by a freeze
function frozenInStreak(habit) {
  let frozen = 0;
  let d = new Date();
  if (!habit.taps[dateKey(d)]) d.setDate(d.getDate() - 1);
  while (habit.taps[dateKey(d)]) {
    if (habit.taps[dateKey(d)] === 'frozen') frozen++;
    d.setDate(d.getDate() - 1);
  }
  return frozen;
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
    el.textContent = amount > 0 ? `+${amount} AP, absolutely ate that shit 🔥` : `${amount} AP, fumbled the bag you degenerate 💀`;
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
  renderHabitList('starve', 'starveList');
  renderHabitList('farm', 'farmList');
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
      ${type === 'starve' ? "no brain rot tracked?? you're telling me you have zero bad habits? lying ass bitch 🤨💀" : "no aura habits?? you're just existing with no grind? embarrassing, add something you NPC 💀🔥"}
    </div>`;
    return;
  }

  container.innerHTML = habits.map(h => {
    const tapped = !!h.taps[today];
    const streak = calcStreak(h);
    const frozen = frozenInStreak(h);
    const tapClass = tapped ? (type === 'starve' ? 'tapped-bad' : 'tapped') : '';
    const frozenTag = frozen > 0 ? ` <span class="text-cyan">🧊x${frozen}</span>` : '';
    const streakText = streak > 0
      ? `🔥 ${streak}d streak — absolute slut for discipline`
      : (type === 'starve' ? '0d streak, you\'re getting cooked alive 💀' : '0d streak, do something you lazy bitch 🔒');
    // freeze is offered when yesterday is a gap but there WAS a chain before it
    // (the day before yesterday was kept) — i.e. a real streak is salvageable
    const dby = new Date(); dby.setDate(dby.getDate() - 2);
    const atRisk = !h.taps[yesterdayKey()] && !!h.taps[dateKey(dby)];
    return `
      <div class="habit-item">
        <span class="habit-emoji">${h.emoji}</span>
        <div class="habit-info">
          <div class="habit-name">${h.name}</div>
          <div class="habit-streak">${streakText}${frozenTag} · best: ${h.bestStreak}d</div>
        </div>
        ${atRisk ? `<button class="habit-freeze" onclick="freezeStreak('${type}','${h.id}')" title="freeze yesterday's gap (${data.freezes} left)">🧊</button>` : ''}
        <button class="habit-tap ${tapClass}" data-tap="${h.id}" onclick="tapHabit('${type}','${h.id}',event)" title="${tapped ? 'undo, you fell off' : 'tap it, lock in'}">
          ${tapped ? '✓' : (type === 'starve' ? '🚫' : '✨')}
        </button>
        <button class="habit-delete" onclick="deleteHabit('${type}','${h.id}')" title="yeet this habit">✕</button>
      </div>
    `;
  }).join('');
}

// ─── STREAK FREEZE ───
function freezeStreak(type, id) {
  const habit = data.habits[type].find(h => h.id === id);
  if (!habit) return;
  const yKey = yesterdayKey();
  if (habit.taps[yKey]) { sfx('error'); toast(rand(FREEZE_NOTNEEDED), 'good'); return; }
  if (data.freezes <= 0) { sfx('error'); haptic(50); toast(rand(FREEZE_BROKE), 'bad'); return; }

  habit.taps[yKey] = 'frozen';
  data.freezes--;
  calcStreak(habit);
  habit.lastStreak = habit.streak;
  save();
  renderHabits();
  sfx('freeze'); haptic(20);
  toast(rand(FREEZE_SAVES), 'good');
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
    sfx('untap'); haptic(30);
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
    spawnConfetti(total > 30 ? 40 : 20);
    sfx('tap'); haptic(12);
    toast(rand(type === 'starve' ? STARVE_ROASTS : FARM_HYPES), 'good');
  }

  calcStreak(habit);
  habit.lastStreak = habit.streak;
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
  sfx('untap'); haptic(20);
  toast('habit yeeted into the fucking void, gone forever bestie 🕳️💀', 'bad');
}

// ─── ADD HABIT MODAL ───
function openAddHabit(type) {
  const modal = document.getElementById('modalContent');
  const title = type === 'starve' ? '💀 STARVE THAT BRAIN ROT' : '✨ FARM THAT AURA';
  const subtitle = type === 'starve'
    ? 'pick a degenerate habit to starve, or name your own poison 🚫'
    : 'pick a habit to farm aura with, or write your own grind ✨';
  const placeholder = type === 'starve'
    ? 'or type it... gooning, simp behavior, thirst trapping...'
    : 'or type it... mewing, cold approach, no-nut streak...';
  const suggestions = HABIT_SUGGESTIONS[type] || [];

  modal.innerHTML = `
    <h3>${title}</h3>
    <p style="font-size:0.7rem;color:var(--fg2);margin-bottom:10px;">${subtitle}</p>

    <label style="font-size:0.65rem;color:var(--fg2);margin-bottom:6px;display:block;">
      ${type === 'starve' ? '// tap a brain rot to add it 💀' : '// tap a habit to add it ✨'}
    </label>
    <div class="sugg-chips">
      ${suggestions.map((s, i) => `
        <button class="sugg-chip ${type}" onclick="fillSuggestion('${type}',${i})">
          <span>${s.emoji}</span> ${s.name}
        </button>`).join('')}
    </div>

    <label style="font-size:0.65rem;color:var(--fg2);margin:12px 0 6px;display:block;">// or roll your own — pick an emoji bestie</label>
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

// pre-fill the form from a suggestion chip
function fillSuggestion(type, idx) {
  const s = HABIT_SUGGESTIONS[type][idx];
  if (!s) return;
  selectedEmoji = s.emoji;
  const input = document.getElementById('habitNameInput');
  if (input) input.value = s.name;
  // sync the emoji picker highlight if this emoji is in the grid
  document.querySelectorAll('.emoji-opt').forEach(e => {
    e.classList.toggle('selected', e.dataset.emoji === s.emoji);
  });
  // highlight the chosen chip
  document.querySelectorAll('.sugg-chip').forEach(c => c.classList.remove('picked'));
  const chips = document.querySelectorAll('.sugg-chip');
  if (chips[idx]) chips[idx].classList.add('picked');
}

function addHabit(type) {
  const name = document.getElementById('habitNameInput')?.value.trim();
  if (!name) { sfx('error'); toast("name it something you braindead NPC, I can't track vibes 💀", 'bad'); return; }

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
  sfx('tap'); haptic(12);
  toast(type === 'starve'
    ? `"${name}" added to the brain rot kill list, starve that bitch 💀🔥`
    : `"${name}" added to the aura farm, now grind it you slut ✨🔥`, 'good');
  selectedEmoji = EMOJI_OPTIONS[0];
}

// ─── MODAL ───
function closeModal() {
  document.getElementById('modalOverlay').style.display = 'none';
}
function closeModalBackdrop(e) {
  if (e.target === document.getElementById('modalOverlay')) closeModal();
}

// ─── QUIT TIMERS ───
function renderQuitTimers() {
  const container = document.getElementById('quitTimerList');
  if (!container) return;
  const habits = data.habits.starve || [];

  if (habits.length === 0) {
    container.innerHTML = '<div class="text-center text-muted" style="padding:20px;font-size:0.75rem;">no brain rot to detox from?? sus as fuck, add your demons bestie 🤨💀</div>';
    return;
  }

  container.innerHTML = habits.map(h => {
    const streak = calcStreak(h);
    const days = streak;
    const hrs = days * 24;
    let timerText, motivText;

    if (days === 0) {
      timerText = "0 days";
      motivText = "zero days clean you absolute degenerate, start NOW 💀";
    } else if (days < 3) {
      timerText = `${days} day${days > 1 ? 's' : ''}`;
      motivText = "barely breathing, don't you dare fuck this up 👶🔥";
    } else if (days < 7) {
      timerText = `${days} days (${hrs}h)`;
      motivText = "brain rot is starving but still kicking, smother that bitch 🔥";
    } else if (days < 30) {
      timerText = `${days} days`;
      motivText = "actually unhinged discipline ngl, your brain rot is getting BODIED 😤💀";
    } else if (days < 100) {
      timerText = `${days} days`;
      motivText = "GIGACHAD AURA, brain rot on life support, pull the fucking plug 💀🔥";
    } else {
      timerText = `${days} days`;
      motivText = "you've transcended mortal degeneracy, ascended sigma shit 🧬✨";
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
  if (!text) { sfx('error'); toast("write something you braindead NPC, the field is right there 💀", 'bad'); return; }

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
  sfx('tap'); haptic(12);
  toast("commit logged, you're actually journaling?? the glow up is REAL, slay 📝✨", 'good');
}

function renderCommitLog() {
  const container = document.getElementById('commitLog');
  if (!container) return;

  if (data.commits.length === 0) {
    container.innerHTML = '<div class="text-center text-muted mt-2" style="font-size:0.75rem;">commit log is bone dry... just like your discipline and your DMs 💀📭</div>';
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

// ─── SHOP / REWARDS ───
let shopTab = 'avatars';

function renderRewards() {
  const container = document.getElementById('rewardsList');
  if (!container) return;

  const tabs = [
    { key: 'avatars', icon: '🤡', label: 'Avatars' },
    { key: 'themes',  icon: '🎨', label: 'Themes' },
    { key: 'titles',  icon: '🏷️', label: 'Titles' }
  ];

  let html = `<div class="shop-balance">💰 <span class="text-accent">${data.auraPoints.toLocaleString()}</span> aura to blow like a degenerate</div>`;
  html += `<div class="shop-tabs">`;
  tabs.forEach(t => {
    html += `<button class="shop-tab ${shopTab === t.key ? 'active' : ''}" onclick="setShopTab('${t.key}')">${t.icon} ${t.label}</button>`;
  });
  html += `</div>`;

  const items = SHOP[shopTab] || [];
  html += `<div class="shop-grid">`;
  items.forEach(item => {
    const owned = data.owned.includes(item.id);
    const equipped = data.equipped[shopTab.slice(0, -1)] === item.id;
    const canAfford = data.auraPoints >= item.cost;

    let actionBtn;
    if (equipped) {
      actionBtn = `<button class="btn btn-sm shop-equipped" disabled>equipped 👑</button>`;
    } else if (owned) {
      actionBtn = `<button class="btn btn-sm btn-accent" onclick="equipItem('${shopTab}','${item.id}')">equip</button>`;
    } else if (canAfford) {
      actionBtn = `<button class="btn btn-sm btn-accent" onclick="buyItem('${shopTab}','${item.id}')">${item.cost} AP</button>`;
    } else {
      actionBtn = `<button class="btn btn-sm shop-broke" disabled>🔒 ${item.cost} AP</button>`;
    }

    let preview = '';
    if (shopTab === 'avatars') {
      preview = `<div class="shop-preview-avatar">${item.preview}</div>`;
    } else if (shopTab === 'themes') {
      const vars = { ...BASE_THEME, ...item.vars };
      preview = `<div class="shop-preview-theme">
        <span class="swatch" style="background:${vars['--accent']}"></span>
        <span class="swatch" style="background:${vars['--bg'] || BASE_THEME['--bg']}"></span>
        <span class="swatch" style="background:${vars['--danger'] || BASE_THEME['--danger']}"></span>
      </div>`;
    } else {
      preview = `<div class="shop-preview-title">"${item.name}"</div>`;
    }

    html += `
      <div class="shop-card ${owned ? 'owned' : ''} ${equipped ? 'is-equipped' : ''} ${!owned && !canAfford ? 'locked' : ''}">
        ${preview}
        <div class="shop-card-name">${item.name}</div>
        <div class="shop-card-desc">${item.desc}</div>
        ${actionBtn}
      </div>`;
  });
  html += `</div>`;

  // milestone badges
  html += `<div class="section-title" style="margin-top:20px;">🏅 // MILESTONES</div>`;
  html += REWARDS.map(r => {
    const unlocked = data.auraPoints >= r.cost;
    const claimed = (data.rewards || []).includes(r.id);
    return `
      <div class="reward-item ${unlocked ? 'unlocked' : 'locked'}">
        <span class="reward-icon">${unlocked ? r.icon : '🔒'}</span>
        <div class="reward-info">
          <div class="reward-name">${r.name} ${claimed ? '<span class="text-accent">✓</span>' : ''}</div>
          <div class="reward-desc">${r.desc}</div>
        </div>
        <div style="text-align:right;">
          <div class="reward-cost">${r.cost >= 1000 ? (r.cost/1000)+'K' : r.cost} AP</div>
          ${unlocked && !claimed ? `<button class="btn btn-accent btn-sm" style="margin-top:4px;" onclick="claimReward('${r.id}')">claim</button>` : ''}
        </div>
      </div>`;
  }).join('');

  container.innerHTML = html;
}

function setShopTab(tab) {
  shopTab = tab;
  renderRewards();
}

function buyItem(category, id) {
  const item = SHOP[category].find(x => x.id === id);
  if (!item) return;
  if (data.auraPoints < item.cost) {
    sfx('error'); haptic(50);
    toast(rand(BROKE_ROASTS), 'bad');
    return;
  }
  data.auraPoints -= item.cost;
  data.owned.push(id);
  const catKey = category.slice(0, -1);
  data.equipped[catKey] = id;
  save();
  updateAuraDisplay();
  renderRewards();
  applyEquipped();
  spawnConfetti(30);
  sfx('buy'); haptic(15);
  toast(rand(REDEEM_HYPE), 'good');
}

function equipItem(category, id) {
  const catKey = category.slice(0, -1);
  data.equipped[catKey] = id;
  save();
  renderRewards();
  applyEquipped();
  sfx('tap'); haptic(12);
  toast("equipped 👑 your drip just leveled up, aura recalibrated fr ✨", 'good');
}

function claimReward(id) {
  if (!data.rewards) data.rewards = [];
  if (data.rewards.includes(id)) return;
  const r = REWARDS.find(x => x.id === id);
  if (!r || data.auraPoints < r.cost) return;
  data.rewards.push(id);
  save();
  renderRewards();
  spawnConfetti(50);
  sfx('buy'); haptic(15);
  toast(`🏆 ${r.name} CLAIMED — ${r.desc} you filthy grinder`, 'good');
}

function applyTheme() {
  const themeId = data.equipped.theme || 'th_default';
  const theme = SHOP.themes.find(t => t.id === themeId);
  const vars = { ...BASE_THEME, ...(theme ? theme.vars : {}) };
  Object.entries(vars).forEach(([k, v]) => {
    document.documentElement.style.setProperty(k, v);
  });
}

function applyEquipped() {
  applyTheme();
  const avatar = SHOP.avatars.find(a => a.id === data.equipped.avatar);
  const logo = document.querySelector('.header-logo');
  if (logo && avatar) logo.textContent = avatar.preview;
  renderGreeting();
}

function getEquippedTitle() {
  if (!data.equipped.title) return null;
  const t = SHOP.titles.find(x => x.id === data.equipped.title);
  return t ? t.name : null;
}

// ─── CALENDAR ───
let selectedCalDay = null;

// per-day breakdown of what went down
function dayStats(key) {
  const farmDone = (data.habits.farm || []).filter(h => h.taps[key] === true);
  const starveDone = (data.habits.starve || []).filter(h => h.taps[key] === true);
  const frozen = [...(data.habits.starve || []), ...(data.habits.farm || [])]
    .filter(h => h.taps[key] === 'frozen');
  const total = farmDone.length + starveDone.length;
  // aura earned that day: farm +15, starve +10 (matches tapHabit base values)
  const auraEarned = farmDone.length * 15 + starveDone.length * 10;
  return { farmDone, starveDone, frozen, total, auraEarned };
}

// earliest day we started tracking (first tap of any kind) — days before this stay neutral
function trackingStart() {
  let min = null;
  ['starve', 'farm'].forEach(type => {
    (data.habits[type] || []).forEach(h => {
      Object.keys(h.taps).forEach(k => { if (!min || k < min) min = k; });
    });
  });
  return min;
}

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
  const tKey = todayKey();
  const start = trackingStart();

  const headers = ['Su','Mo','Tu','We','Th','Fr','Sa'];
  let html = '<div class="calendar-grid">';
  headers.forEach(h => { html += `<div class="cal-header">${h}</div>`; });

  for (let i = 0; i < firstDay; i++) {
    html += '<div class="cal-day empty"></div>';
  }

  for (let d = 1; d <= daysInMonth; d++) {
    const key = `${year}-${String(month+1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
    const isToday = key === tKey;
    const st = dayStats(key);
    const classes = ['cal-day'];
    let glyph = d;

    if (isToday) classes.push('today');
    if (key === selectedCalDay) classes.push('selected');

    if (st.total > 0) {
      // green heat tiered by how much you locked in
      if (st.total >= 5) classes.push('aura-3');
      else if (st.total >= 3) classes.push('aura-2');
      else classes.push('aura-1');
    } else if (st.frozen.length > 0) {
      classes.push('frozen');
      glyph = '🧊';
    } else if (start && key >= start && key < tKey) {
      // a past day inside your active window with nothing logged = brain rot won
      classes.push('rot');
    }

    html += `<div class="${classes.join(' ')}" onclick="showDaySummary('${key}')">${glyph}</div>`;
  }

  html += '</div>';
  container.innerHTML = html;

  if (selectedCalDay) renderDaySummary(selectedCalDay);
}

// ─── DAY SUMMARY (explicit receipts) ───
function showDaySummary(key) {
  selectedCalDay = key;
  renderCalendar('appCalendar');
  renderDaySummary(key);
}

function renderDaySummary(key) {
  const el = document.getElementById('daySummary');
  if (!el) return;

  const [y, m, dd] = key.split('-').map(Number);
  const dateObj = new Date(y, m - 1, dd);
  const dateLabel = dateObj.toLocaleDateString('en', { weekday: 'long', month: 'short', day: 'numeric' });
  const st = dayStats(key);
  const tKey = todayKey();
  const start = trackingStart();
  const isToday = key === tKey;
  const isFuture = key > tKey;
  const isPast = key < tKey;

  // verdict line, explicit slang depending on the day's vibe
  let verdict;
  if (isFuture) {
    verdict = `<span class="text-muted">🔮 the future, bestie. can't farm aura in advance — manifest it then lock tf in.</span>`;
  } else if (st.total === 0 && st.frozen.length > 0) {
    verdict = `<span class="text-cyan">🧊 you froze this one. streak survived, but don't make slacking a personality.</span>`;
  } else if (st.total === 0) {
    if (isToday) {
      verdict = `<span class="text-danger">👀 nothing logged yet today. day ain't over — lock the fuck in before you get cooked.</span>`;
    } else if (start && key >= start) {
      verdict = `<span class="text-danger">💀 you were COOKED this day. zero aura, brain rot won. embarrassing fr.</span>`;
    } else {
      verdict = `<span class="text-muted">😶 before your time on the farm. nothing tracked, no shame.</span>`;
    }
  } else if (st.total >= 5) {
    verdict = `<span class="text-accent">🔥 absolutely ATE this day. ${st.total} taps, +${st.auraEarned} aura — you sigma menace.</span>`;
  } else if (st.total >= 3) {
    verdict = `<span class="text-accent">💪 solid grind, ${st.total} taps. aura farmed, brain rot starved. keep that energy.</span>`;
  } else {
    verdict = `<span class="text-accent">✨ you showed up (${st.total} tap${st.total>1?'s':''}). mid but it counts, don't get comfortable.</span>`;
  }

  let html = `<div class="day-summary">
    <div class="ds-date">› ${dateLabel}${isToday ? ' <span class="text-accent">(today)</span>' : ''}</div>
    <div class="ds-verdict">${verdict}</div>`;

  if (st.farmDone.length) {
    html += `<div class="ds-label">✨ aura farmed</div><div class="ds-row">`;
    html += st.farmDone.map(h => `<span class="ds-tag good">${h.emoji} ${h.name}</span>`).join('');
    html += `</div>`;
  }
  if (st.starveDone.length) {
    html += `<div class="ds-label">💀 brain rot starved</div><div class="ds-row">`;
    html += st.starveDone.map(h => `<span class="ds-tag good">${h.emoji} ${h.name} 🚫</span>`).join('');
    html += `</div>`;
  }
  if (st.frozen.length) {
    html += `<div class="ds-label">🧊 frozen</div><div class="ds-row">`;
    html += st.frozen.map(h => `<span class="ds-tag ice">${h.emoji} ${h.name}</span>`).join('');
    html += `</div>`;
  }
  if (st.total > 0) {
    html += `<div class="ds-row" style="margin-top:10px;"><span class="ds-tag good">💰 +${st.auraEarned} aura points this day</span></div>`;
  }

  html += `</div>`;
  el.innerHTML = html;
}

function calNav(dir) {
  data.calMonth += dir;
  if (data.calMonth > 11) { data.calMonth = 0; data.calYear++; }
  if (data.calMonth < 0) { data.calMonth = 11; data.calYear--; }
  selectedCalDay = null;
  const ds = document.getElementById('daySummary');
  if (ds) ds.innerHTML = '';
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
      <div class="stat-label">tapped today</div>
    </div>
    <div class="stat-box">
      <div class="stat-num">${bestStreak}</div>
      <div class="stat-label">filthiest streak</div>
    </div>
    <div class="stat-box">
      <div class="stat-num">${totalDays.size}</div>
      <div class="stat-label">days not cooked</div>
    </div>
    <div class="stat-box">
      <div class="stat-num text-cyan">🧊${data.freezes ?? 0}</div>
      <div class="stat-label">freeze tokens</div>
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

  const title = getEquippedTitle();
  const avatar = SHOP.avatars.find(a => a.id === (data.equipped && data.equipped.avatar));
  const avatarEmoji = avatar ? avatar.preview : '🧠';

  el.innerHTML = `
    <div class="greeting-name"><span class="text-purple">${avatarEmoji}</span> ${greeting}</div>
    ${title ? `<div class="greeting-title">「${title}」</div>` : ''}
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
  if (tabId === 'tabFarm') renderCommitLog();
  if (tabId === 'tabStarve') renderQuitTimers();
  if (tabId === 'tabDen') renderHabits();
}

// ─── APP ENTRY ───
function enterApp() {
  if (!data.packName) {
    const modal = document.getElementById('modalContent');
    modal.innerHTML = `
      <h3>🧠 WHAT'S YOUR NAME, YOU DEGENERATE?</h3>
      <p style="font-size:0.7rem;color:var(--fg2);margin-bottom:12px;">this is your pack name. make it go hard af or don't even bother, NPC 🔥💀</p>
      <input type="text" id="packNameInput" placeholder="e.g. RizzLord, GigaChad, BrainRotSlayer..." maxlength="20" autofocus>
      <div class="modal-actions">
        <button class="btn btn-accent btn-block" onclick="setPackName()">lock the fuck in → 🔒🔥</button>
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
  if (!name) { sfx('error'); toast("name yourself you coward, tf am I supposed to call you?? 💀", 'bad'); return; }
  data.packName = name;
  save();
  closeModal();
  activateApp();
  sfx('buy'); haptic(15);
  toast(`welcome to the farm, ${name} you degenerate 🔥✨ now lock tf in and farm that aura`, 'good');
}

function activateApp() {
  document.getElementById('screenLanding').classList.remove('active');
  document.getElementById('screenApp').classList.add('active');
  document.getElementById('bottomNav').style.display = 'flex';
  document.getElementById('headerRight').innerHTML = `
    <button class="btn btn-sm" onclick="resetApp()" title="nuke it all, you masochist">🗑️</button>
    <button class="btn btn-sm" onclick="goLanding()" title="dip out to the landing">←</button>
  `;
  applyEquipped();
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
      this will nuke ALL your shit. streaks, aura points, habits, shop drip — everything gone forever.<br>
      are you absolutely fucking sure, you masochist? 🗑️💀
    </p>
    <div class="modal-actions">
      <button class="btn" onclick="closeModal()">nah I'm not that stupid</button>
      <button class="btn btn-danger" onclick="confirmReset()">nuke it all, I'm unhinged 💀</button>
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
  sfx('untap'); haptic(40);
  toast("everything nuked to shit. fresh start, zero aura, back to being a nobody 💀🔥", 'bad');
}

// ─── SIGN IN (FAKE) ───
function showSignIn() {
  const modal = document.getElementById('modalContent');
  modal.innerHTML = `
    <h3>🔒 SIGN IN</h3>
    <p style="font-size:0.7rem;color:var(--fg2);margin-bottom:12px;">
      lmao this is a local-first PWA bestie. your data lives in YOUR browser, nobody's spying on your degenerate habits.<br>
      no accounts, no servers, no corporate bullshit. privacy is bussin fr. 🫡
    </p>
    <p style="font-size:0.7rem;color:var(--accent);margin-bottom:12px;">
      just hit "Enter your Zone" and start farming aura like the absolute menace you are ✨🔥
    </p>
    <div class="modal-actions">
      <button class="btn btn-accent btn-block" onclick="closeModal()">got it, now let me farm 🔥</button>
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
  const heat = ['aura-3','aura-2','aura-1','aura-2','rot','aura-1','aura-3','rot','aura-2'];
  for (let d = 1; d <= daysInMonth; d++) {
    const isToday = d === now.getDate();
    const classes = ['cal-day'];
    if (isToday) classes.push('today');
    else if (d < now.getDate()) {
      // deterministic-ish demo heat so the landing looks like a real grind
      const pick = heat[(d * 7) % heat.length];
      classes.push(pick);
    }
    html += `<div class="${classes.join(' ')}">${d}</div>`;
  }
  html += '</div>';
  html += `<div class="cal-legend">
    <span><i class="legend-dot aura"></i> aura day</span>
    <span><i class="legend-dot rot"></i> cooked</span>
    <span><i class="legend-dot ice"></i> frozen</span>
  </div>`;
  container.innerHTML = html;
}

// recalc every streak/bestStreak from tap history & persist (fixes streaks not
// saving on load). Returns habits whose streak just died since last session.
function recalcAllStreaks() {
  const broken = [];
  ['starve', 'farm'].forEach(type => {
    (data.habits[type] || []).forEach(h => {
      if (!h.taps) h.taps = {};
      if (typeof h.bestStreak !== 'number') h.bestStreak = 0;
      const prev = typeof h.lastStreak === 'number' ? h.lastStreak : 0;
      calcStreak(h);
      if (prev >= 2 && h.streak === 0) broken.push(h);
      h.lastStreak = h.streak;
    });
  });
  save();
  return broken;
}

// ─── INIT ───
function init() {
  load();
  const broken = recalcAllStreaks();
  renderLandingCalendar();

  if (data.packName) {
    activateApp();
    // roast the user for any streak they let die since last time
    if (broken.length) {
      setTimeout(() => {
        broken.slice(0, 3).forEach((h, i) => {
          setTimeout(() => toast(rand(STREAK_BROKEN_ROASTS).replace('{name}', h.name), 'bad'), i * 600);
        });
      }, 900);
    }
  }

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }

  initPWA();
  initOfflineWatch();
  initConfetti();

  document.getElementById('commitInput')?.addEventListener('keydown', e => {
    if (e.key === 'Enter') addCommit();
  });
}

// ─── PWA INSTALL PROMPT ───
let deferredInstallPrompt = null;
const INSTALL_DISMISS_KEY = 'aura_install_dismissed';

function initPWA() {
  // browser fires this when the app is installable — stash it & show our banner
  window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault();
    deferredInstallPrompt = e;
    if (localStorage.getItem(INSTALL_DISMISS_KEY) !== '1') {
      const banner = document.getElementById('installBanner');
      if (banner) banner.classList.add('show');
    }
  });

  // already installed / launched standalone → never nag
  window.addEventListener('appinstalled', () => {
    deferredInstallPrompt = null;
    const banner = document.getElementById('installBanner');
    if (banner) banner.classList.remove('show');
    sfx('buy');
    toast("INSTALLED 🔥 you're a real one now, farm aura even when you're cooked offline ✨😈", 'good');
  });
}

function triggerInstall() {
  const banner = document.getElementById('installBanner');
  if (!deferredInstallPrompt) {
    // iOS Safari & co. don't support the prompt API — tell em how
    toast("no auto-install here bestie — hit Share → 'Add to Home Screen', it's not that hard you NPC 📲", 'good');
    if (banner) banner.classList.remove('show');
    return;
  }
  deferredInstallPrompt.prompt();
  deferredInstallPrompt.userChoice.then(choice => {
    if (choice.outcome === 'accepted') {
      sfx('buy');
      toast("LET'S GOOO, aura farm is on your home screen you absolute menace 😈🔥", 'good');
    } else {
      toast("you really said no to free aura farming?? absolutely cooked behavior 💀😭", 'bad');
    }
    deferredInstallPrompt = null;
    if (banner) banner.classList.remove('show');
  });
}

function dismissInstall() {
  localStorage.setItem(INSTALL_DISMISS_KEY, '1');
  const banner = document.getElementById('installBanner');
  if (banner) banner.classList.remove('show');
  toast("aight, install it later when you stop being mid af and grow some balls 🙄💀", 'bad');
}

// ─── OFFLINE MODE WATCH ───
function initOfflineWatch() {
  const apply = () => {
    const offline = !navigator.onLine;
    document.body.classList.toggle('is-offline', offline);
  };
  window.addEventListener('online', () => {
    apply();
    toast("back online 📡✨ the algorithm missed your aura, now get back to grinding you slut", 'good');
  });
  window.addEventListener('offline', () => {
    apply();
    toast("you're offline 💀 signal died but your aura doesn't, keep farming you disconnected degenerate ✨", 'bad');
  });
  apply(); // set initial state on load
}

document.addEventListener('DOMContentLoaded', init);
