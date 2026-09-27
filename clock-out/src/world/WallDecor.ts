import * as THREE from 'three';
import { localToWorld, standaloneYaw, type Dir } from './OfficeKit';

// What makes a floor look like an Indian IT office instead of a render: framed
// team photos from Annual Day and the Wonderla offsite, motivational posters that
// have given up, a Thought-for-the-Day board, a sprint board where DONE says
// "Lunch", the cork notice board with the lost steel tiffin. Everything is painted
// onto canvases at load time, so there are still no external assets.

const TUNING = {
  /** Roughly one decoration per this many candidate wall faces. */
  facesPerItem: 3,
  maxItems: 22,
  /** Keep items at least this many cells apart along the wall. */
  spacing: 2,
  texSize: 512,
  toranHeight: 2.62,
};

export const PAIN_QUOTES: Array<[string, string]> = [
  ['WORK-LIFE BALANCE', 'Work is life. Balance is optional.'],
  ['DEADLINES', "It was due yesterday. Welcome to today's deadline."],
  ['APPRAISAL', 'The hike is coming. — every cycle since 2019'],
  ['FAMILY', "We are a family. Families don't discuss salary."],
  ['COMMITMENT', 'Stay late. Look busy. Leave eventually.'],
  ['WEEKENDS', 'A myth invented by HR to keep you going.'],
  ['LEAVE POLICY', 'Your leave is approved.* *Subject to project needs.'],
  ['OWNERSHIP', 'You own the problem. Not the stock options.'],
  ['DREAM BIG', 'Log in early. Log out never.'],
  ['TEAMWORK', "There is no 'I' in TEAM. There is no 'hike' either."],
  ['QUICK CALL', "Every 'quick call' is 45 minutes. Plan accordingly."],
  ['ONSITE', 'Onsite opportunity coming soon. (Since 2016.)'],
  ['PATIENCE', 'Salary is delayed. Character is being built.'],
  ['OUR PEOPLE', 'Our greatest asset is our people. Second: the biometric machine.'],
  ['CHAI BREAK', 'Break: 10 min. Queue: 20 min. Do the math.'],
  ['THE BENCH', 'Bench is not a place. It is a state of mind.'],
  ['NOTICE PERIOD', 'Yours: 90 days. Ours: 0 days.'],
  ['INNOVATION', 'Happens at 11 PM on a Sunday. — Management'],
  ['PRODUCTIVITY', 'If you are reading this, your stand-up is running late.'],
  ['FRIDAY', 'Friday deployments build character.'],
  ['GROWTH', "You'll grow here. Mostly your screen time."],
  ['EXCELLENCE', 'Excellence is not an act. It is a recurring 6:45 PM meeting.'],
  ['REVERT BACK', 'Kindly do the needful and revert back at the earliest.'],
  ['HAPPY HOURS', 'Happy hours: none. Hours: many.'],
  ['SECRET SANTA', 'You got the stapler. Again. From the same person.'],
  ['HACKATHON', '48 hours. 1 pizza. 0 sleep. Runner-up.'],
  ['ONE PIECE', 'Of cake left for eleven people. Choose wisely.'],
];

const THOUGHTS = [
  'Hard work never killed anybody. But why take the chance?',
  "Don't count the days. The attendance system does it for you.",
  "Yesterday's bug is today's feature.",
  "Success is 1% inspiration and 99% 'as discussed'.",
  'The early bird gets the parking. The rest get the ORR.',
  'Be like the office printer. Work only when nobody is watching.',
  'Every problem is an opportunity. For another meeting.',
];

const TEAM_CAPTIONS = [
  ['TEAM PHOENIX', 'Annual Day 2023'],
  ['Q3 OFFSITE', 'Wonderla · (before the reorg)'],
  ['HACKATHON 2022', "Runner-up · (winner: Deepak's team)"],
  ['TEAM OUTING', 'Ramoji Film City · 6 AM bus'],
  ['DIWALI CELEBRATION', 'Ethnic Day 2023'],
  ['SPRINT 142 RETRO', 'Nobody smiled'],
  ['CRICKET LEAGUE', 'Semi-final · Rohit still arguing'],
  ['FIVE YEAR AWARDS', 'Silver coin · still not encashed'],
];

const NOTICES = [
  "LOST: Steel tiffin (3-tier). Contains sambar. It's my mother's. Please.",
  'Fire drill Friday 4 PM. Mandatory. (Last time nobody came.)',
  'Cab roster changed AGAIN. Check with Srinivas.',
  "Cricket league sign-up: 2 slots left (Rohit's team, again)",
  'Kindly do not keep fish curry in the common fridge. — Admin',
  'Yoga Day, 7 AM. Attendance tracked. Breathing optional.',
  'FOUND: one AirPod. Left. Lonely.',
  'Blood donation camp Thursday. Free biscuit.',
];

type Kind = 'quote' | 'teamPhoto' | 'thought' | 'sprint' | 'notice' | 'eotm' | 'birthday';

/** Weighted deck so every level gets variety but mostly posters and photos. */
const DECK: Kind[] = ['quote', 'teamPhoto', 'quote', 'thought', 'teamPhoto', 'quote', 'sprint', 'notice', 'quote', 'eotm', 'teamPhoto', 'birthday', 'quote', 'teamPhoto'];

const SIZES: Record<Kind, [number, number]> = {
  quote: [0.72, 0.96],
  teamPhoto: [1.05, 0.72],
  thought: [1.35, 0.85],
  sprint: [1.6, 0.95],
  notice: [1.1, 0.78],
  eotm: [0.6, 0.8],
  birthday: [1.7, 0.36],
};

const HEIGHTS: Record<Kind, number> = { quote: 1.65, teamPhoto: 1.7, thought: 1.45, sprint: 1.45, notice: 1.5, eotm: 1.7, birthday: 2.25 };

export function decorateWalls(
  root: THREE.Group, cellAt: (c: number, r: number) => string, W: number, H: number, cs: number,
  rand: () => number, theme: string,
): void {
  const off: Array<[Dir, number, number]> = [[0, 0, -1], [1, 1, 0], [2, 0, 1], [3, -1, 0]];
  const faces: Array<{ c: number; r: number; dir: Dir }> = [];
  for (let r = 0; r < H; r++) for (let c = 0; c < W; c++) {
    if (cellAt(c, r) !== '#') continue;
    for (const [dir, dx, dz] of off) if ('.P'.includes(cellAt(c + dx, r + dz))) faces.push({ c, r, dir });
  }
  if (theme === 'festival') addToran(root, faces, cs);

  const count = Math.min(TUNING.maxItems, Math.round(faces.length / TUNING.facesPerItem));
  const chosen: typeof faces = [];
  const pool = [...faces];
  while (chosen.length < count && pool.length) {
    const f = pool.splice(Math.floor(rand() * pool.length), 1)[0];
    if (chosen.some((o) => o.dir === f.dir && Math.abs(o.c - f.c) + Math.abs(o.r - f.r) < TUNING.spacing)) continue;
    chosen.push(f);
  }
  const quotes = shuffled(PAIN_QUOTES, rand);
  const captions = shuffled(TEAM_CAPTIONS, rand);
  // One-of-a-kind boards: a floor has one sprint board, one notice board, one Employee of the Month.
  const caps: Partial<Record<Kind, number>> = { sprint: 1, notice: 1, eotm: 1, birthday: 1, thought: 2 };
  const used: Partial<Record<Kind, number>> = {};
  let photos = 0, posters = 0;
  chosen.forEach((spot, i) => {
    let kind = DECK[(i + Math.floor(rand() * 3)) % DECK.length];
    if ((used[kind] ?? 0) >= (caps[kind] ?? Infinity)) kind = i % 2 ? 'quote' : 'teamPhoto';
    used[kind] = (used[kind] ?? 0) + 1;
    const idx = kind === 'teamPhoto' ? photos++ : kind === 'quote' ? posters++ : i;
    const tex = paint(kind, rand, quotes, captions, idx);
    const [w, h] = SIZES[kind];
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshLambertMaterial({ map: tex }));
    const [wx, wz] = localToWorld({ cx: (spot.c + 0.5) * cs, cz: (spot.r + 0.5) * cs, dir: spot.dir }, (rand() - 0.5) * 0.3, cs / 2 + 0.012);
    mesh.position.set(wx, HEIGHTS[kind] + (rand() - 0.5) * 0.08, wz);
    mesh.rotation.y = standaloneYaw(spot.dir);
    // Photo frames and posters are never quite straight in real offices either.
    if (kind === 'quote' || kind === 'teamPhoto' || kind === 'eotm') mesh.rotation.z = (rand() - 0.5) * 0.04;
    root.add(mesh);
  });
}

function shuffled<T>(arr: readonly T[], rand: () => number): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function canvas(w: number, h: number): [HTMLCanvasElement, CanvasRenderingContext2D] {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return [c, c.getContext('2d')!];
}

function toTexture(c: HTMLCanvasElement): THREE.CanvasTexture {
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  return t;
}

function wrap(g: CanvasRenderingContext2D, text: string, maxW: number): string[] {
  const words = text.split(' ');
  const lines: string[] = [];
  let cur = '';
  for (const w of words) {
    const t = cur ? `${cur} ${w}` : w;
    if (g.measureText(t).width > maxW && cur) { lines.push(cur); cur = w; } else cur = t;
  }
  if (cur) lines.push(cur);
  return lines;
}

const HAND = '"Comic Sans MS", "Chalkboard SE", "Marker Felt", "Segoe Print", cursive';
const SKIN = ['#8a5a3c', '#a8704c', '#c2946c', '#7a4e33', '#d8b08a', '#9a6a4a', '#b88660'];
const SHIRT = ['#3f5f8a', '#c9d9ea', '#8a2323', '#1f5f6b', '#e9e2d0', '#9d93cf', '#2c3a55', '#e0a030', '#5a8a6a'];

function paint(kind: Kind, rand: () => number, quotes: Array<[string, string]>, captions: string[][], index: number): THREE.CanvasTexture {
  const S = TUNING.texSize;
  const [w, h] = SIZES[kind];
  const [c, g] = canvas(S, Math.round((S * h) / w));
  const W = c.width, H = c.height;
  g.textAlign = 'center';
  g.textBaseline = 'middle';

  switch (kind) {
    case 'quote': {
      // Each poster is its own joke: a flat illustration over a colour of its own.
      const [title, body] = quotes[index % quotes.length];
      const hue = (hash(title) % 360);
      g.fillStyle = '#111';
      g.fillRect(0, 0, W, H);
      g.fillStyle = `hsl(${hue}, 38%, 32%)`;
      g.fillRect(34, 34, W - 68, H * 0.55);
      g.fillStyle = `hsl(${hue}, 45%, 44%)`;
      g.fillRect(34, 34 + H * 0.4, W - 68, H * 0.15);
      g.save();
      g.translate(W / 2, 34 + H * 0.29);
      (ILLUSTRATIONS[title] ?? ILLUSTRATIONS.DEFAULT)(g);
      g.restore();
      g.fillStyle = '#f4f1e8';
      g.textAlign = 'center';
      g.font = 'bold 46px Georgia, serif';
      g.fillText(title, W / 2, H * 0.7);
      g.font = 'italic 29px Georgia, serif';
      wrap(g, body, W - 110).forEach((l, i) => g.fillText(l, W / 2, H * 0.79 + i * 37));
      break;
    }
    case 'teamPhoto': {
      const [title, sub] = captions[index % captions.length];
      g.fillStyle = '#5b3a22';
      g.fillRect(0, 0, W, H);
      g.fillStyle = '#e8e2d4';
      g.fillRect(16, 16, W - 32, H - 32);
      const bg = g.createLinearGradient(0, 30, 0, H * 0.78);
      bg.addColorStop(0, ['#9ec5e8', '#e8d9b0', '#b7d3a8', '#d9b8c8'][Math.floor(rand() * 4)]);
      bg.addColorStop(1, '#f2efe6');
      g.fillStyle = bg;
      g.fillRect(30, 30, W - 60, H * 0.72);
      // Two rows of colleagues, back row standing, front row crouching. One of them left the company.
      const ghost = Math.floor(rand() * 11);
      let n = 0;
      for (const [row, y, count, scale] of [[0, H * 0.3, 6, 1], [1, H * 0.48, 5, 1.12]] as const) {
        for (let i = 0; i < count; i++) {
          const x = 60 + (row ? 40 : 0) + i * ((W - 120 - (row ? 80 : 0)) / (count - 1));
          const alpha = n === ghost ? 0.25 : 1;
          g.globalAlpha = alpha;
          g.fillStyle = SHIRT[Math.floor(rand() * SHIRT.length)];
          g.beginPath();
          g.roundRect(x - 26 * scale, y, 52 * scale, 70 * scale, 14);
          g.fill();
          g.fillStyle = SKIN[Math.floor(rand() * SKIN.length)];
          g.beginPath();
          g.arc(x, y - 16 * scale, 20 * scale, 0, Math.PI * 2);
          g.fill();
          g.fillStyle = '#15110f';
          g.beginPath();
          g.arc(x, y - 24 * scale, 20 * scale, Math.PI, 0);
          g.fill();
          g.globalAlpha = 1;
          n++;
        }
      }
      g.fillStyle = '#2b2b2e';
      g.font = 'bold 30px Helvetica, Arial, sans-serif';
      g.fillText(title, W / 2, H * 0.85);
      g.font = 'italic 22px Helvetica, Arial, sans-serif';
      g.fillText(sub, W / 2, H * 0.92);
      break;
    }
    case 'thought': {
      whiteboard(g, W, H);
      g.fillStyle = '#1f3b73';
      g.font = `bold 40px ${HAND}`;
      g.fillText('THOUGHT FOR THE DAY', W / 2, 60);
      g.fillStyle = '#b3202a';
      g.font = `30px ${HAND}`;
      wrap(g, `"${THOUGHTS[Math.floor(rand() * THOUGHTS.length)]}"`, W - 90).forEach((l, i) => g.fillText(l, W / 2, 130 + i * 42));
      g.fillStyle = '#333';
      g.font = `22px ${HAND}`;
      g.textAlign = 'right';
      g.fillText('— Admin team :)', W - 40, H - 40);
      break;
    }
    case 'sprint': {
      whiteboard(g, W, H);
      const cols = ['TO DO', 'IN PROGRESS', 'DONE'];
      const notes = [
        ['Fix prod', 'Fix the fix of prod', 'Timesheet', "Reply to 'quick q'", 'Update Jira'],
        ['Refactor (since March)', 'KT', 'Waiting for onsite'],
        ['Lunch'],
      ];
      const colW = W / 3;
      g.strokeStyle = '#555';
      g.lineWidth = 3;
      for (let i = 1; i < 3; i++) { g.beginPath(); g.moveTo(colW * i, 30); g.lineTo(colW * i, H - 30); g.stroke(); }
      cols.forEach((t, i) => {
        g.fillStyle = '#222';
        g.font = `bold 26px ${HAND}`;
        g.fillText(t, colW * i + colW / 2, 50);
        notes[i].forEach((note, j) => {
          const x = colW * i + 22 + (j % 2) * (colW / 2 - 12), y = 72 + Math.floor(j / 2) * 84;
          g.save();
          g.translate(x + 38, y + 36);
          g.rotate((rand() - 0.5) * 0.15);
          g.fillStyle = ['#ffe66d', '#ffadad', '#a0e7e5', '#b4f8c8'][Math.floor(rand() * 4)];
          g.fillRect(-38, -36, 76, 72);
          g.fillStyle = '#222';
          g.font = `15px ${HAND}`;
          wrap(g, note, 68).slice(0, 4).forEach((l, k) => g.fillText(l, 0, -20 + k * 16));
          g.restore();
        });
      });
      break;
    }
    case 'notice': {
      g.fillStyle = '#6b4a2b';
      g.fillRect(0, 0, W, H);
      g.fillStyle = '#b98a5a';
      g.fillRect(14, 14, W - 28, H - 28);
      for (let i = 0; i < 900; i++) {
        g.fillStyle = `rgba(90,60,30,${0.2 + rand() * 0.3})`;
        g.fillRect(14 + rand() * (W - 28), 14 + rand() * (H - 28), 2, 2);
      }
      const items = shuffled(NOTICES, rand).slice(0, 4);
      items.forEach((t, i) => {
        const x = 40 + (i % 2) * (W / 2 - 20), y = 40 + Math.floor(i / 2) * (H / 2 - 20);
        g.save();
        g.translate(x + 100, y + 70);
        g.rotate((rand() - 0.5) * 0.12);
        g.fillStyle = i % 3 === 0 ? '#fff6c2' : '#fbfaf4';
        g.fillRect(-100, -70, 200, 140);
        g.fillStyle = '#c0392b';
        g.beginPath();
        g.arc(0, -60, 7, 0, Math.PI * 2);
        g.fill();
        g.fillStyle = '#222';
        g.font = '17px Helvetica, Arial, sans-serif';
        wrap(g, t, 180).slice(0, 6).forEach((l, k) => g.fillText(l, 0, -38 + k * 20));
        g.restore();
      });
      break;
    }
    case 'eotm': {
      g.fillStyle = '#b8912f';
      g.fillRect(0, 0, W, H);
      g.fillStyle = '#fbf6e6';
      g.fillRect(20, 20, W - 40, H - 40);
      g.fillStyle = '#6b4a12';
      g.font = 'bold 34px Georgia, serif';
      g.fillText('EMPLOYEE', W / 2, 70);
      g.fillText('OF THE MONTH', W / 2, 110);
      g.fillStyle = '#9c6b48';
      g.beginPath();
      g.arc(W / 2, H * 0.47, 70, 0, Math.PI * 2);
      g.fill();
      g.fillStyle = '#16110e';
      g.beginPath();
      g.arc(W / 2, H * 0.43, 72, Math.PI, 0);
      g.fill();
      g.fillStyle = '#4a4a6a';
      g.fillRect(W / 2 - 90, H * 0.6, 180, 90);
      g.fillStyle = '#2b2b2e';
      g.font = 'bold 32px Helvetica, Arial, sans-serif';
      g.fillText('Deepak Hegde', W / 2, H * 0.84);
      g.font = 'italic 24px Helvetica, Arial, sans-serif';
      g.fillText(['(again)', '(11th time)', '(he nominated himself)'][Math.floor(rand() * 3)], W / 2, H * 0.91);
      break;
    }
    case 'birthday': {
      g.fillStyle = '#fff4d6';
      g.fillRect(0, 0, W, H);
      const colors = ['#ef476f', '#ffd166', '#06d6a0', '#118ab2', '#9b5de5'];
      const text = `HAPPY BIRTHDAY ${['RINKU', 'KAVITA MA\'AM', 'SRINIVAS ANNA', 'DEEPAK'][Math.floor(rand() * 4)]}!`;
      g.font = 'bold 60px Helvetica, Arial, sans-serif';
      const total = g.measureText(text).width;
      let x = (W - total) / 2;
      g.textAlign = 'left';
      [...text].forEach((ch, i) => {
        g.fillStyle = colors[i % colors.length];
        g.fillText(ch, x, H / 2);
        x += g.measureText(ch).width;
      });
      break;
    }
  }
  return toTexture(c);
}

function whiteboard(g: CanvasRenderingContext2D, W: number, H: number): void {
  g.fillStyle = '#9aa0a6';
  g.fillRect(0, 0, W, H);
  g.fillStyle = '#fbfbf8';
  g.fillRect(10, 10, W - 20, H - 20);
  // Ghosts of meetings past: half-erased marker.
  g.strokeStyle = 'rgba(80,90,120,0.12)';
  g.lineWidth = 6;
  for (let i = 0; i < 6; i++) {
    g.beginPath();
    g.moveTo(40 + i * 60, H - 60 - i * 10);
    g.bezierCurveTo(120 + i * 40, H - 140, 200, H - 30, 300 + i * 20, H - 90);
    g.stroke();
  }
}

/** Marigold-and-mango-leaf toran strung along every wall for the festival chapter. */
function addToran(root: THREE.Group, faces: Array<{ c: number; r: number; dir: Dir }>, cs: number): void {
  const per = 9;
  const flowerGeo = new THREE.IcosahedronGeometry(0.05, 0);
  const leafGeo = new THREE.ConeGeometry(0.035, 0.14, 4);
  const flowers = new THREE.InstancedMesh(flowerGeo, new THREE.MeshLambertMaterial({ color: 0xffffff }), faces.length * per);
  const leaves = new THREE.InstancedMesh(leafGeo, new THREE.MeshLambertMaterial({ color: 0x3f7a2e }), faces.length * per);
  const m = new THREE.Matrix4();
  const col = new THREE.Color();
  let k = 0;
  for (const f of faces) {
    for (let i = 0; i < per; i++) {
      const lx = -cs / 2 + (i + 0.5) * (cs / per);
      const sag = Math.sin(((i + 0.5) / per) * Math.PI) * 0.12;
      const [x, z] = localToWorld({ cx: (f.c + 0.5) * cs, cz: (f.r + 0.5) * cs, dir: f.dir }, lx, cs / 2 + 0.05);
      m.makeTranslation(x, TUNING.toranHeight - sag, z);
      flowers.setMatrixAt(k, m);
      flowers.setColorAt(k, col.set(i % 2 ? 0xff9f1c : 0xffd60a));
      const [lx2, lz2] = localToWorld({ cx: (f.c + 0.5) * cs, cz: (f.r + 0.5) * cs, dir: f.dir }, lx, cs / 2 + 0.04);
      m.makeTranslation(lx2, TUNING.toranHeight - sag - 0.12, lz2);
      m.multiply(new THREE.Matrix4().makeRotationX(Math.PI));
      leaves.setMatrixAt(k, m);
      k++;
    }
  }
  root.add(flowers, leaves);
}

function hash(t: string): number {
  let h = 7;
  for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) >>> 0;
  return h;
}

// Poster illustrations, drawn around (0,0) in a roughly 380x240 box.
type Draw = (g: CanvasRenderingContext2D) => void;
const INK = '#f4f1e8', RED = '#e63946', YEL = '#ffd166', DARK = '#1b1b1f', GRN = '#7ee07e';

function rect(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, c: string): void { g.fillStyle = c; g.fillRect(x, y, w, h); }
function circle(g: CanvasRenderingContext2D, x: number, y: number, r: number, c: string): void { g.fillStyle = c; g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.fill(); }
function label(g: CanvasRenderingContext2D, t: string, x: number, y: number, size: number, c: string): void {
  g.fillStyle = c; g.font = `bold ${size}px Helvetica, Arial, sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(t, x, y);
}
function clock(g: CanvasRenderingContext2D, x: number, y: number, r: number, hh: number, mm: number): void {
  circle(g, x, y, r, INK); g.strokeStyle = DARK; g.lineWidth = 6; g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.stroke();
  g.lineCap = 'round';
  const hand = (a: number, len: number, w: number) => { g.lineWidth = w; g.beginPath(); g.moveTo(x, y); g.lineTo(x + Math.sin(a) * len, y - Math.cos(a) * len); g.stroke(); };
  hand(((hh % 12) + mm / 60) / 12 * Math.PI * 2, r * 0.5, 8);
  hand(mm / 60 * Math.PI * 2, r * 0.8, 5);
}
function laptop(g: CanvasRenderingContext2D, x: number, y: number, s: number): void {
  rect(g, x - 50 * s, y - 60 * s, 100 * s, 62 * s, DARK); rect(g, x - 44 * s, y - 54 * s, 88 * s, 50 * s, '#8fb4d8');
  rect(g, x - 62 * s, y + 2 * s, 124 * s, 10 * s, '#c9ccd0');
}

const ILLUSTRATIONS: Record<string, Draw> = {
  DEFAULT: (g) => circle(g, 0, 0, 70, YEL),
  'WORK-LIFE BALANCE': (g) => {
    // A seesaw: the laptop wins, the little house flies.
    g.save(); g.rotate(-0.22); rect(g, -170, -6, 340, 12, INK); g.restore();
    g.fillStyle = INK; g.beginPath(); g.moveTo(-20, 60); g.lineTo(20, 60); g.lineTo(0, 8); g.fill();
    laptop(g, -115, 18, 0.9);
    g.fillStyle = YEL; g.beginPath(); g.moveTo(95, -78); g.lineTo(140, -110); g.lineTo(185, -78); g.fill(); rect(g, 105, -78, 70, 45, YEL);
  },
  DEADLINES: (g) => {
    rect(g, -110, -95, 220, 190, INK); rect(g, -110, -95, 220, 44, RED);
    label(g, 'DUE', 0, -73, 30, INK); label(g, 'YESTERDAY', 0, 10, 36, DARK);
    g.strokeStyle = RED; g.lineWidth = 7; g.beginPath(); g.ellipse(0, 10, 105, 38, 0, 0, Math.PI * 2); g.stroke();
  },
  APPRAISAL: (g) => {
    // A flat line chart from 2019 onwards.
    g.strokeStyle = INK; g.lineWidth = 4; g.beginPath(); g.moveTo(-150, -90); g.lineTo(-150, 80); g.lineTo(160, 80); g.stroke();
    g.strokeStyle = GRN; g.lineWidth = 8; g.beginPath(); g.moveTo(-140, 40); g.lineTo(-60, 38); g.lineTo(20, 40); g.lineTo(100, 36); g.lineTo(150, 39); g.stroke();
    ['19', '20', '21', '22', '23'].forEach((y, i) => label(g, `'${y}`, -130 + i * 68, 100, 20, INK));
    label(g, '+0.5%?', 60, -40, 34, YEL);
  },
  FAMILY: (g) => {
    for (let i = 0; i < 4; i++) { circle(g, -120 + i * 80, -30, 26, INK); rect(g, -145 + i * 80, 0, 50, 70, INK); }
    label(g, '₹ ???', 0, -95, 36, YEL);
  },
  COMMITMENT: (g) => { clock(g, -40, 0, 85, 9, 47); circle(g, 120, -60, 40, YEL); circle(g, 138, -72, 36, `hsl(0,0%,0%,0)`); label(g, 'PM', 90, 70, 40, INK); },
  WEEKENDS: (g) => {
    ['SAT', 'SUN'].forEach((d, i) => { rect(g, -150 + i * 160, -80, 140, 160, INK); label(g, d, -80 + i * 160, 0, 44, DARK); });
    g.strokeStyle = RED; g.lineWidth = 12; g.beginPath(); g.moveTo(-160, -90); g.lineTo(160, 90); g.moveTo(160, -90); g.lineTo(-160, 90); g.stroke();
  },
  'LEAVE POLICY': (g) => {
    rect(g, -100, -100, 200, 200, INK);
    for (let i = 0; i < 6; i++) rect(g, -80, -80 + i * 22, 160, 6, '#b9b2a0');
    g.save(); g.rotate(-0.3); g.strokeStyle = RED; g.lineWidth = 6; g.strokeRect(-120, -28, 240, 56); label(g, 'APPROVED*', 0, 0, 38, RED); g.restore();
  },
  OWNERSHIP: (g) => {
    // A small fire with your name on it.
    g.fillStyle = '#ff7b00'; g.beginPath(); g.moveTo(-60, 70); g.quadraticCurveTo(-80, -10, -10, -90); g.quadraticCurveTo(0, -30, 30, -60); g.quadraticCurveTo(80, 0, 60, 70); g.fill();
    g.fillStyle = YEL; g.beginPath(); g.moveTo(-30, 70); g.quadraticCurveTo(-30, 10, 0, -30); g.quadraticCurveTo(30, 10, 30, 70); g.fill();
    rect(g, -70, 70, 140, 30, INK); label(g, 'PROD BUG · YOU', 0, 85, 18, DARK);
  },
  'DREAM BIG': (g) => { rect(g, -160, 20, 320, 60, INK); rect(g, -160, -20, 60, 40, '#b9b2a0'); laptop(g, 40, 20, 1); label(g, 'z z z', -90, -70, 38, YEL); },
  TEAMWORK: (g) => { ['T', 'E', 'A', 'M'].forEach((c, i) => { rect(g, -170 + i * 88, -45, 76, 90, [YEL, RED, GRN, '#8ecae6'][i]); label(g, c, -132 + i * 88, 0, 60, DARK); }); },
  'QUICK CALL': (g) => { rect(g, -55, -100, 110, 200, DARK); rect(g, -45, -85, 90, 150, '#2a9d8f'); label(g, '00:45:12', 0, -10, 24, INK); circle(g, 0, 82, 10, RED); },
  ONSITE: (g) => {
    g.fillStyle = INK; g.beginPath(); g.moveTo(-170, 10); g.lineTo(150, -5); g.quadraticCurveTo(185, 0, 150, 15); g.lineTo(-170, 25); g.fill();
    g.beginPath(); g.moveTo(-20, 5); g.lineTo(-80, -70); g.lineTo(-40, -70); g.lineTo(40, 5); g.fill();
    g.strokeStyle = '#ddd'; g.lineWidth = 2; for (let i = 0; i < 6; i++) { g.beginPath(); g.arc(120, -60, 10 + i * 8, 0, Math.PI); g.stroke(); }
    label(g, 'SINCE 2016', 0, 80, 30, YEL);
  },
  PATIENCE: (g) => { rect(g, -120, -60, 240, 130, '#7a4e33'); rect(g, -120, -60, 240, 30, '#5b3a22'); label(g, '₹ 0', 0, 20, 60, INK); label(g, 'om', 110, -90, 30, YEL); },
  'OUR PEOPLE': (g) => {
    rect(g, -70, -100, 140, 200, DARK); circle(g, 0, -10, 45, GRN);
    g.strokeStyle = DARK; g.lineWidth = 3; for (let i = 1; i < 6; i++) { g.beginPath(); g.ellipse(0, -10, i * 7, i * 9, 0, 0, Math.PI * 2); g.stroke(); }
    label(g, 'BEEP', 0, 70, 24, GRN);
  },
  'CHAI BREAK': (g) => { for (let i = 0; i < 7; i++) { rect(g, -175 + i * 52, -10 - (i % 2) * 6, 36, 50, INK); rect(g, -170 + i * 52, -4 - (i % 2) * 6, 26, 12, '#c68642'); } label(g, 'QUEUE: 20 MIN', 0, -70, 30, YEL); },
  'THE BENCH': (g) => { rect(g, -160, 0, 320, 18, '#a0522d'); rect(g, -160, -50, 320, 14, '#a0522d'); rect(g, -140, 18, 14, 60, DARK); rect(g, 126, 18, 14, 60, DARK); laptop(g, 0, -2, 0.7); },
  'NOTICE PERIOD': (g) => { rect(g, -170, -70, 150, 140, INK); label(g, '90', -95, 0, 70, DARK); rect(g, 20, -70, 150, 140, INK); label(g, '0', 95, 0, 70, RED); },
  INNOVATION: (g) => { circle(g, -30, -20, 70, YEL); rect(g, -60, 45, 60, 40, '#c9ccd0'); label(g, 'SUN 11 PM', 100, 60, 28, INK); circle(g, 130, -70, 26, INK); },
  PRODUCTIVITY: (g) => { for (let i = 0; i < 5; i++) { circle(g, -140 + i * 70, -40, 20, INK); rect(g, -160 + i * 70, -18, 40, 50, INK); rect(g, -165 + i * 70, 35, 50, 12, '#7a4e33'); } label(g, 'STAND-UP (SEATED)', 0, 80, 22, YEL); },
  FRIDAY: (g) => { rect(g, -70, -70, 140, 150, DARK); for (let i = 0; i < 5; i++) rect(g, -55, -55 + i * 26, 110, 14, '#3a3d44'); g.fillStyle = '#ff7b00'; g.beginPath(); g.moveTo(-40, -70); g.quadraticCurveTo(0, -150, 40, -70); g.fill(); label(g, 'DEPLOY 6:55 PM', 0, 100, 22, YEL); },
  GROWTH: (g) => { rect(g, -45, -100, 90, 170, DARK); rect(g, -38, -88, 76, 140, '#8fb4d8'); rect(g, -30, 30, 16, 12, GRN); rect(g, -10, 0, 16, 42, GRN); rect(g, 10, -40, 16, 82, RED); label(g, '9h 42m', 0, 95, 22, INK); },
  EXCELLENCE: (g) => { g.fillStyle = YEL; g.beginPath(); g.moveTo(-60, -80); g.lineTo(60, -80); g.quadraticCurveTo(55, 20, 0, 30); g.quadraticCurveTo(-55, 20, -60, -80); g.fill(); rect(g, -12, 30, 24, 30, YEL); rect(g, -50, 60, 100, 18, YEL); label(g, '6:45 PM ↻', 0, -40, 22, DARK); },
  'REVERT BACK': (g) => { rect(g, -120, -70, 240, 150, INK); g.strokeStyle = DARK; g.lineWidth = 5; g.beginPath(); g.moveTo(-120, -70); g.lineTo(0, 20); g.lineTo(120, -70); g.stroke(); label(g, 'PFA', 0, 50, 34, RED); },
  'HAPPY HOURS': (g) => { clock(g, -60, 0, 80, 23, 10); g.fillStyle = 'rgba(244,241,232,0.3)'; g.beginPath(); g.moveTo(70, -60); g.lineTo(150, -60); g.lineTo(110, 10); g.fill(); rect(g, 106, 10, 8, 60, INK); rect(g, 85, 68, 50, 8, INK); },
  'SECRET SANTA': (g) => {
    // A stapler wearing a Santa hat.
    rect(g, -130, 20, 260, 40, '#3a3d44'); g.fillStyle = '#5a5d64'; g.beginPath(); g.moveTo(-130, 20); g.lineTo(120, -10); g.lineTo(130, 20); g.fill();
    g.fillStyle = RED; g.beginPath(); g.moveTo(20, -8); g.lineTo(110, -18); g.lineTo(90, -110); g.fill(); rect(g, 15, -18, 100, 18, INK); circle(g, 90, -112, 14, INK);
  },
  HACKATHON: (g) => {
    label(g, '{ }', -80, 0, 130, GRN);
    g.fillStyle = YEL; g.beginPath(); g.moveTo(60, -60); g.lineTo(170, 60); g.lineTo(40, 60); g.fill();
    for (const [x, y] of [[80, 20], [110, 40], [65, 45]]) circle(g, x, y, 9, RED);
  },
  'ONE PIECE': (g) => {
    // One slice of birthday cake on a paper plate, eleven forks.
    g.fillStyle = INK; g.beginPath(); g.ellipse(0, 50, 150, 30, 0, 0, Math.PI * 2); g.fill();
    g.fillStyle = '#f7c59f'; g.beginPath(); g.moveTo(-70, 40); g.lineTo(70, 20); g.lineTo(70, -30); g.lineTo(-70, -10); g.fill();
    g.fillStyle = '#ff99c8'; g.beginPath(); g.moveTo(-70, -10); g.lineTo(70, -30); g.lineTo(40, -50); g.lineTo(-90, -25); g.fill();
    rect(g, 20, -80, 8, 35, '#8ecae6'); circle(g, 24, -86, 7, YEL);
    for (let i = 0; i < 11; i++) { g.save(); g.rotate(-1.2 + i * 0.22); rect(g, -3, -160, 6, 50, '#c9ccd0'); g.restore(); }
  },
};
