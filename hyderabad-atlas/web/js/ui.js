// Small UI helpers: toast, haptics, particle burst, ring svg.

let toastTimer = 0;
export function toast(msg, ms = 2200) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.hidden = false;
  el.style.animation = 'none'; void el.offsetWidth; el.style.animation = '';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.hidden = true), ms);
}

export function buzz(pattern) {
  try { if (navigator.vibrate) navigator.vibrate(pattern); } catch { /* not supported */ }
}

// Particle burst on the fx canvas (refined confetti in the brand colours).
const COLORS = ['#5be49b', '#7db9f0', '#f2cf4a', '#ffffff'];
let parts = [], running = false;
export function burst(x, y, n = 40) {
  const cv = document.getElementById('fx');
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  if (cv.width !== innerWidth * dpr) { cv.width = innerWidth * dpr; cv.height = innerHeight * dpr; }
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2, s = 2 + Math.random() * 6;
    parts.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s - 3, life: 1, r: 2 + Math.random() * 3, c: COLORS[i % COLORS.length], rot: Math.random() * 6, sq: Math.random() < 0.5 });
  }
  if (!running) { running = true; requestAnimationFrame(tick); }
}
function tick() {
  const cv = document.getElementById('fx');
  const ctx = cv.getContext('2d');
  const dpr = cv.width / innerWidth;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, innerWidth, innerHeight);
  parts = parts.filter((p) => p.life > 0);
  for (const p of parts) {
    p.x += p.vx; p.y += p.vy; p.vy += 0.18; p.vx *= 0.985; p.life -= 0.016; p.rot += 0.1;
    ctx.globalAlpha = Math.max(0, p.life);
    ctx.fillStyle = p.c;
    ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot);
    if (p.sq) ctx.fillRect(-p.r, -p.r / 2, p.r * 2, p.r);
    else { ctx.beginPath(); ctx.arc(0, 0, p.r * 0.8, 0, 7); ctx.fill(); }
    ctx.restore();
  }
  if (parts.length) requestAnimationFrame(tick);
  else { running = false; ctx.clearRect(0, 0, innerWidth, innerHeight); }
}

export function ring(frac, inner = '', size = 44, stroke = 4, color = '') {
  const r = (size - stroke) / 2, c = 2 * Math.PI * r;
  return `<div class="ring" style="width:${size}px;height:${size}px"><svg viewBox="0 0 ${size} ${size}" style="width:${size}px;height:${size}px">
    <circle class="bg" cx="${size / 2}" cy="${size / 2}" r="${r}" style="stroke-width:${stroke}"/>
    <circle class="fg" cx="${size / 2}" cy="${size / 2}" r="${r}" style="stroke-width:${stroke};stroke-dasharray:${c};stroke-dashoffset:${c * (1 - Math.max(0, Math.min(1, frac)))};${color ? `stroke:${color}` : ''}"/>
  </svg><div class="in">${inner}</div></div>`;
}

export const ICON = {
  lock: '<svg viewBox="0 0 24 24"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>',
  check: '<svg viewBox="0 0 24 24"><path d="m5 12 5 5 9-10"/></svg>',
  play: '<svg viewBox="0 0 24 24"><path d="M7 4v16l13-8z"/></svg>',
  chev: '<svg viewBox="0 0 24 24"><path d="m9 6 6 6-6 6"/></svg>',
  back: '<svg viewBox="0 0 24 24"><path d="m15 6-6 6 6 6"/></svg>',
  close: '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18"/></svg>',
  pin: '<svg viewBox="0 0 24 24"><path d="M12 21s7-6 7-12a7 7 0 0 0-14 0c0 6 7 12 7 12z"/><circle cx="12" cy="9" r="2.5"/></svg>',
  tag: '<svg viewBox="0 0 24 24"><path d="M3 12V4h8l10 10-8 8z"/><circle cx="7.5" cy="8.5" r="1.5"/></svg>',
  layers: '<svg viewBox="0 0 24 24"><path d="m12 3 9 5-9 5-9-5zM3 13l9 5 9-5"/></svg>',
  compass: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="m15 9-2 4-4 2 2-4z"/></svg>',
  grid: '<svg viewBox="0 0 24 24"><path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"/></svg>',
  train: '<svg viewBox="0 0 24 24"><rect x="5" y="3" width="14" height="14" rx="3"/><path d="M5 11h14M8 21l2-4M16 21l-2-4"/></svg>',
  route: '<svg viewBox="0 0 24 24"><circle cx="6" cy="19" r="2"/><circle cx="18" cy="5" r="2"/><path d="M8 19h6a4 4 0 0 0 0-8h-4a4 4 0 0 1 0-8h6"/></svg>',
  bolt: '<svg viewBox="0 0 24 24"><path d="M13 2 4 14h7l-1 8 9-12h-7z"/></svg>',
  target: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/></svg>',
  redo: '<svg viewBox="0 0 24 24"><path d="M4 12a8 8 0 1 0 3-6.2M4 4v5h5"/></svg>',
};
