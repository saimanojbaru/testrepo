// Guided flyover: the camera visits each item of a lesson with a caption,
// like the reel's narration. Ends by handing over to the quiz.
import { crumb, KIND_LABEL } from './data.js';
import { describe } from './describe.js';

const DEPTH_OF = { corp: 'corp', zone: 'zone', circle: 'circle', locality: 'locality', landmark: 'landmark', station: 'metro', line: 'metro', corridor: 'routes' };

export class Tour {
  constructor(app) {
    this.app = app;
    this.root = document.getElementById('tour');
    this.card = this.root.querySelector('.tour-card');
    this.root.querySelector('[data-act="close"]').onclick = () => this.close(false);
    this.root.querySelector('[data-act="skip"]').onclick = () => this.close(true);
    this.root.querySelector('[data-act="prev"]').onclick = () => this.go(this.i - 1);
    this.root.querySelector('[data-act="next"]').onclick = () => (this.i < this.ids.length - 1 ? this.go(this.i + 1) : this.close(true));
  }

  start(lesson, onDone) {
    this.lesson = lesson;
    this.onDone = onDone;
    const A = this.app.A;
    this.ids = lesson.tour || lesson.items;
    // big lessons: tour the most important ~10, the quiz covers the rest
    if (this.ids.length > 12) {
      const lead = new Set(A.circles.map((c) => c.children[0]));
      const firsts = this.ids.filter((id) => lead.has(id));
      this.ids = (firsts.length >= 4 ? firsts : this.ids).slice(0, 12);
    }
    this.root.querySelector('.dots').innerHTML = this.ids.map(() => '<i></i>').join('');
    this.app.setMode('tour');
    this.root.hidden = false;
    const first = A.get(this.ids[0]);
    if (lesson.line) this.app.map.setDepth('metro');
    else this.app.map.setDepth(DEPTH_OF[first.kind] || 'corp');
    this.go(0);
  }

  go(i) {
    if (i < 0 || i >= this.ids.length) return;
    this.i = i;
    const A = this.app.A, m = this.app.map;
    const it = A.get(this.ids[i]);
    const depth = DEPTH_OF[it.kind] || 'corp';
    if (m.depth !== depth) m.setDepth(depth, { animate: depth === 'corp' || depth === 'zone' });
    if (this.lesson.line) m.dimLines(this.lesson.line);
    m.select(it, { fly: false });
    if (this.lesson.line) m.dimLines(this.lesson.line);
    if (it.kind === 'corp') m.fitItem(it, { zoomAdj: -0.1, padding: { bottom: 300 } });
    else if (it.kind === 'zone') m.fitItem(it, { zoomAdj: -0.2, padding: { bottom: 300 } });
    else if (it.kind === 'circle') m.fitItem(it, { zoomAdj: -0.4, maxZoom: 13.2, padding: { bottom: 300 } });
    else if (it.kind === 'corridor' || it.kind === 'line') m.fitItem(it, { padding: { bottom: 300 } });
    else m.fitItem(it, { zoom: it.kind === 'station' ? 13 : 12.6, padding: { bottom: 300 } });

    this.root.querySelectorAll('.dots i').forEach((d, k) => d.classList.toggle('on', k <= i));
    const kicker = `${i + 1} of ${this.ids.length} · ${KIND_LABEL[it.kind]}`;
    this.card.querySelector('.kicker').textContent = kicker;
    this.card.querySelector('.t-title').textContent = it.name;
    this.card.querySelector('.t-crumb').textContent = crumb(A, it).join(' › ');
    this.card.querySelector('.t-fact').textContent = describe(A, it);
    this.root.querySelector('[data-act="prev"]').disabled = i === 0;
    this.root.querySelector('[data-act="next"]').textContent = i === this.ids.length - 1 ? 'Start quiz' : 'Next';
    this.card.style.animation = 'none'; void this.card.offsetWidth; this.card.style.animation = '';
  }

  close(toQuiz) {
    this.root.hidden = true;
    this.app.map.clearSelection();
    const cb = this.onDone;
    this.onDone = null;
    if (cb) cb(toQuiz);
    else this.app.afterOverlay(false);
  }
}
