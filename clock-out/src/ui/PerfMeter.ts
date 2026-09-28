import type * as THREE from 'three';

// Settings → "Show frame rate": a small corner readout for measuring on real phones.
// FPS over the last second, the slowest 5% of frame times (what you feel as stutter),
// and what the GPU drew last frame.

const TUNING = { windowFrames: 120, refreshMs: 500 };

export class PerfMeter {
  private readonly el: HTMLDivElement;
  private readonly times: number[] = [];
  private last = 0;
  private shownAt = 0;
  enabled = false;

  constructor(parent: HTMLElement) {
    this.el = document.createElement('div');
    this.el.style.cssText = 'position:fixed;left:8px;bottom:8px;z-index:50;padding:4px 8px;border-radius:4px;'
      + 'background:rgba(0,0,0,.65);color:#9f9;font:12px/1.35 monospace;pointer-events:none;white-space:pre;display:none';
    parent.append(this.el);
  }

  setEnabled(on: boolean): void {
    this.enabled = on;
    this.el.style.display = on ? 'block' : 'none';
  }

  /** Call once per rendered frame, after rendering. */
  frame(now: number, renderer: THREE.WebGLRenderer): void {
    if (!this.enabled) return;
    if (this.last) {
      this.times.push(now - this.last);
      if (this.times.length > TUNING.windowFrames) this.times.shift();
    }
    this.last = now;
    if (now - this.shownAt < TUNING.refreshMs || this.times.length < 10) return;
    this.shownAt = now;
    const avg = this.times.reduce((s, t) => s + t, 0) / this.times.length;
    const sorted = [...this.times].sort((a, b) => a - b);
    const p95 = sorted[Math.floor(sorted.length * 0.95)];
    const info = renderer.info.render;
    const fps = 1000 / avg;
    this.el.style.color = fps >= 45 ? '#9f9' : fps >= 30 ? '#fd6' : '#f77';
    this.el.textContent = `${fps.toFixed(0)} fps  avg ${avg.toFixed(1)} ms  worst5% ${p95.toFixed(1)} ms\n`
      + `${(info.triangles / 1000).toFixed(0)}k tris  ${info.calls} draws  ${window.devicePixelRatio.toFixed(1)}x`;
  }
}
