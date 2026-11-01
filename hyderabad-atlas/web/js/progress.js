// Progress lives in localStorage. Each item has a Leitner box (0-5):
// right answer moves it up one box, wrong answer drops it two.
// Box >= 3 counts as "mastered".

const KEY = 'hyd-atlas-v1';
const today = () => new Date().toISOString().slice(0, 10);

const blank = () => ({
  items: {}, lessons: {}, games: {}, xp: 0,
  streak: { days: 0, last: null }, explored: {},
  settings: { unlockAll: false, labels: true, roads: false, tilt: true },
});

export class Progress {
  constructor() {
    try { this.s = Object.assign(blank(), JSON.parse(localStorage.getItem(KEY) || '{}')); }
    catch { this.s = blank(); }
    this.s.settings = Object.assign(blank().settings, this.s.settings);
    this.listeners = [];
  }

  save() {
    try { localStorage.setItem(KEY, JSON.stringify(this.s)); } catch { /* storage full or disabled */ }
    this.listeners.forEach((f) => f());
  }

  onChange(f) { this.listeners.push(f); }

  item(id) { return this.s.items[id] || { b: 0, c: 0, w: 0, t: 0 }; }

  record(id, correct) {
    const it = { ...this.item(id) };
    if (correct) { it.b = Math.min(5, it.b + 1); it.c++; }
    else { it.b = Math.max(0, it.b - 2); it.w++; }
    it.t = Date.now();
    this.s.items[id] = it;
  }

  addXp(n) {
    this.s.xp += n;
    const d = today();
    if (this.s.streak.last !== d) {
      const y = new Date(Date.now() - 864e5).toISOString().slice(0, 10);
      this.s.streak.days = this.s.streak.last === y ? this.s.streak.days + 1 : 1;
      this.s.streak.last = d;
    }
  }

  streakDays() {
    const { last, days } = this.s.streak;
    const y = new Date(Date.now() - 864e5).toISOString().slice(0, 10);
    return last === today() || last === y ? days : 0;
  }

  mastered(id) { return this.item(id).b >= 3; }
  seen(id) { return !!this.s.items[id]; }

  mastery(ids) {
    if (!ids.length) return 0;
    let sum = 0;
    for (const id of ids) sum += Math.min(this.item(id).b, 3) / 3;
    return sum / ids.length;
  }

  // weighted pick: unseen and weak items first, recently-asked ones last
  weighted(ids, n) {
    const now = Date.now();
    const scored = ids.map((id) => {
      const it = this.item(id);
      const age = it.t ? (now - it.t) / 6e4 : 1e4;
      const w = (6 - it.b) * 2 + Math.min(age, 60) / 20 + Math.random() * 3 + (it.w > it.c ? 2 : 0);
      return [w, id];
    });
    return scored.sort((a, b) => b[0] - a[0]).slice(0, n).map((x) => x[1]);
  }

  lesson(id) { return this.s.lessons[id] || { best: 0, plays: 0 }; }
  finishLesson(id, pct) {
    const l = { ...this.lesson(id) };
    l.best = Math.max(l.best, pct); l.plays++;
    this.s.lessons[id] = l;
  }

  game(id) { return this.s.games[id] || { best: 0, plays: 0 }; }
  finishGame(id, score) {
    const g = { ...this.game(id) };
    g.best = Math.max(g.best, score); g.plays++;
    this.s.games[id] = g;
  }

  weakest(n = 8) {
    return Object.entries(this.s.items)
      .filter(([, v]) => v.w > 0 && v.b < 3)
      .sort((a, b) => (b[1].w - b[1].c) - (a[1].w - a[1].c) || a[1].b - b[1].b)
      .slice(0, n).map(([id]) => id);
  }

  reset() { this.s = blank(); this.save(); }
}
