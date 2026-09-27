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
      // Parody of the corporate motivational poster: black border, a scenic gradient, a title that has given up.
      const [title, body] = quotes[index % quotes.length];
      const palettes = [['#1d3557', '#e76f51'], ['#264653', '#e9c46a'], ['#3d2c5e', '#f4a261'], ['#0b3d2e', '#a3c585']];
      const [top, bottom] = palettes[Math.floor(rand() * palettes.length)];
      g.fillStyle = '#111';
      g.fillRect(0, 0, W, H);
      const grad = g.createLinearGradient(0, 30, 0, H * 0.58);
      grad.addColorStop(0, top);
      grad.addColorStop(1, bottom);
      g.fillStyle = grad;
      g.fillRect(34, 34, W - 68, H * 0.55);
      // Mountain silhouette, because every motivational poster has one.
      g.fillStyle = 'rgba(0,0,0,0.55)';
      g.beginPath();
      g.moveTo(34, H * 0.55 + 34);
      for (let x = 34; x <= W - 34; x += 40) g.lineTo(x, H * 0.38 + Math.sin(x * 0.03 + index) * 40 + rand() * 30);
      g.lineTo(W - 34, H * 0.55 + 34);
      g.fill();
      g.fillStyle = '#f4f1e8';
      g.font = 'bold 50px Georgia, serif';
      g.fillText(title, W / 2, H * 0.7);
      g.font = 'italic 30px Georgia, serif';
      wrap(g, body, W - 110).forEach((l, i) => g.fillText(l, W / 2, H * 0.79 + i * 38));
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
