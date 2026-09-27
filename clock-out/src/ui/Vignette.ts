// Suspicion vignette: a CSS radial gradient whose opacity follows the highest NPC
// awareness. Above the heartbeat threshold it pulses, and `update` reports when a
// beat lands so the audio can thump in time with it.

const TUNING = {
  heartbeatAbove: 0.6,
  smoothing: 6,
  /** Beat interval shrinks from slow to fast as awareness climbs to 1. */
  slowBeat: 0.95,
  fastBeat: 0.45,
};

export class Vignette {
  private el: HTMLDivElement;
  private level = 0;
  private beatTimer = 0;

  constructor(parent: HTMLElement) {
    this.el = document.createElement('div');
    this.el.className = 'vignette';
    parent.append(this.el);
  }

  /** Returns a heartbeat strength (0..1) on frames where a beat should sound, else 0. */
  update(dt: number, target: number): number {
    this.level += (target - this.level) * Math.min(1, dt * TUNING.smoothing);
    this.el.style.opacity = String(Math.min(1, this.level * 1.1));
    if (this.level <= TUNING.heartbeatAbove) {
      this.beatTimer = 0;
      return 0;
    }
    const t = (this.level - TUNING.heartbeatAbove) / (1 - TUNING.heartbeatAbove);
    this.beatTimer -= dt;
    if (this.beatTimer > 0) return 0;
    this.beatTimer = TUNING.slowBeat + (TUNING.fastBeat - TUNING.slowBeat) * t;
    // Restart the CSS pulse animation so it lines up with the audio beat.
    this.el.classList.remove('beat');
    void this.el.offsetWidth;
    this.el.classList.add('beat');
    return 0.5 + 0.5 * t;
  }

  reset(): void {
    this.level = 0;
    this.el.style.opacity = '0';
    this.el.classList.remove('beat');
  }
}
