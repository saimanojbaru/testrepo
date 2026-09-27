// Fixed-step simulation clock. AI, vision and dialogue timers all assume a stable
// dt; rendering runs at whatever rate the display gives us.

const TUNING = {
  step: 1 / 60,
  /** A tab switch can hand us a multi-second delta; clamp it so the sim doesn't lurch. */
  maxDelta: 0.1,
};

export class Clock {
  readonly step = TUNING.step;
  private last = -1;
  private acc = 0;

  /** Runs `update` zero or more times; returns how many steps ran this frame. */
  tick(nowMs: number, update: (dt: number) => void): number {
    if (this.last < 0) this.last = nowMs;
    const delta = Math.min((nowMs - this.last) / 1000, TUNING.maxDelta);
    this.last = nowMs;
    this.acc += delta;
    let steps = 0;
    while (this.acc >= TUNING.step) {
      update(TUNING.step);
      this.acc -= TUNING.step;
      steps++;
    }
    return steps;
  }

  reset(): void {
    this.last = -1;
    this.acc = 0;
  }
}
