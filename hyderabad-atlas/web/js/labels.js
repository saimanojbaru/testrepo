// DOM label layer drawn over the map, styled like the reel (bold type, pills,
// glowing dots). Labels are placed greedily by priority and hidden if they
// would overlap a more important one.

const ICONS = {
  heritage: '<path d="M4 21h16M6 21V10M18 21V10M3 10l9-6 9 6M10 21v-5h4v5"/>',
  museum: '<path d="M3 21h18M4 9h16M12 3 3 9M12 3l9 6M6 9v9M10 9v9M14 9v9M18 9v9"/>',
  market: '<path d="M4 7h16l-1 13H5zM8 7a4 4 0 0 1 8 0"/>',
  lake: '<path d="M3 15c2-2 4-2 6 0s4 2 6 0 4-2 6 0M3 19c2-2 4-2 6 0s4 2 6 0 4-2 6 0M12 3c3 4 4 6 4 8a4 4 0 0 1-8 0c0-2 1-4 4-8z"/>',
  landmark: '<path d="M12 2l3 7h6l-5 4 2 7-6-4-6 4 2-7-5-4h6z"/>',
  temple: '<path d="M12 2v3M8 9l4-4 4 4M6 21V9h12v12M4 21h16M10 21v-4h4v4"/>',
  park: '<path d="M12 22v-6M7 16h10l-5-12z"/>',
  tech: '<rect x="5" y="3" width="14" height="18" rx="1"/><path d="M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2"/>',
  education: '<path d="M2 9l10-5 10 5-10 5zM6 11v5c3 2 9 2 12 0v-5"/>',
  sport: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/>',
  transit: '<rect x="5" y="3" width="14" height="14" rx="3"/><path d="M5 11h14M8 21l2-4M16 21l-2-4"/>',
  culture: '<path d="M4 4h16v12H4zM8 20h8M12 16v4"/>',
};

export class LabelLayer {
  constructor(map, el) {
    this.map = map;
    this.el = el;
    this.items = [];
    this.hidden = false;
    this.raf = 0;
    const kick = () => this.schedule();
    map.on('move', kick);
    map.on('resize', kick);
  }

  schedule() {
    if (this.raf) return;
    this.raf = requestAnimationFrame(() => { this.raf = 0; this.update(); });
  }

  // specs: {id, lngLat, text, kind, pill?, pillColor?, color?, priority?, icon?, sel?}
  set(specs) {
    const keep = new Map(this.items.map((i) => [i.key, i]));
    const next = [];
    for (const s of specs) {
      const key = s.kind + ':' + s.id + ':' + s.text;
      let it = keep.get(key);
      if (it) keep.delete(key);
      else it = this.make(s, key);
      it.spec = s;
      it.el.classList.toggle('sel', !!s.sel);
      next.push(it);
    }
    for (const it of keep.values()) it.el.remove();
    this.items = next.sort((a, b) => (b.spec.priority || 0) - (a.spec.priority || 0));
    this.measure = true;
    this.schedule();
  }

  make(s, key) {
    const el = document.createElement('div');
    el.className = `lbl lbl-${s.kind} off`;
    let html = '';
    if (s.icon) html += `<span class="ic" style="color:${s.color || '#fff'}"><svg viewBox="0 0 24 24">${ICONS[s.icon] || ICONS.landmark}</svg></span>`;
    else if (s.dot) html += `<span class="dot" style="${s.dotColor ? `background:${s.dotColor};border-color:#fff` : ''}"></span>`;
    html += `<span class="t">${s.text}</span>`;
    if (s.pill) html += `<span class="p" style="background:${s.pillColor || '#5be49b'}">${s.pill}</span>`;
    el.innerHTML = html;
    this.el.appendChild(el);
    return { key, el, w: 0, h: 0, spec: s };
  }

  setHidden(h) {
    this.hidden = h;
    this.el.style.opacity = h ? '0' : '1';
  }

  update() {
    if (this.measure) {
      for (const it of this.items) { it.w = it.el.offsetWidth; it.h = it.el.offsetHeight; }
      this.measure = false;
    }
    const W = this.el.clientWidth, H = this.el.clientHeight;
    const placed = [];
    const pad = 4;
    for (const it of this.items) {
      const p = this.map.project(it.spec.lngLat);
      // anchor: dot/icon items sit on the point; text-only items centre on it
      const anchored = it.spec.dot || it.spec.icon;
      const x = p.x - it.w / 2;
      const y = anchored ? p.y - (it.spec.icon ? 13 : 3) : p.y - it.h / 2;
      const r = [x - pad, y - pad, x + it.w + pad, y + it.h + pad];
      let ok = p.x > -40 && p.x < W + 40 && p.y > 40 && p.y < H + 20;
      if (ok && !it.spec.force) {
        for (const q of placed) {
          if (r[0] < q[2] && r[2] > q[0] && r[1] < q[3] && r[3] > q[1]) { ok = false; break; }
        }
      }
      if (ok) {
        placed.push(r);
        it.el.style.transform = `translate(${x.toFixed(1)}px, ${y.toFixed(1)}px)`;
        it.el.classList.remove('off');
      } else {
        it.el.classList.add('off');
      }
    }
  }
}
