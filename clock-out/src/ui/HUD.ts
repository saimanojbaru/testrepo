// In-game DOM overlay: crosshair + interact label, objective, wall clock and
// deadline, crouch/noise indicator, status line, toasts, intro card, pause card
// and a full-screen fade used for "escorted back to your desk".

const TUNING = {
  toastSeconds: 3.2,
  noiseBars: 5,
  noiseMax: 14,
};

export interface HudTopRight {
  clock: string;
  deadline?: string;
  deadlineUrgent?: boolean;
  favors?: { left: number; total: number };
  elevator?: string;
}

export class HUD {
  readonly root: HTMLDivElement;
  private crosshair: HTMLDivElement;
  private interactLabel: HTMLDivElement;
  private objective: HTMLDivElement;
  private topRight: HTMLDivElement;
  private crouch: HTMLDivElement;
  private noise: HTMLDivElement;
  private noiseBars: HTMLSpanElement[] = [];
  private status: HTMLDivElement;
  private toasts: HTMLDivElement;
  private card: HTMLDivElement;
  private fade: HTMLDivElement;
  private pause: HTMLDivElement;

  constructor(parent: HTMLElement) {
    this.root = div('hud hidden');
    this.crosshair = div('crosshair');
    this.interactLabel = div('interact-label');
    this.objective = div('objective');
    this.topRight = div('top-right');
    this.crouch = div('crouch-indicator');
    this.crouch.textContent = 'CROUCHED';
    this.noise = div('noise-meter');
    const lbl = document.createElement('span');
    lbl.className = 'noise-label';
    lbl.textContent = 'noise';
    this.noise.append(lbl);
    for (let i = 0; i < TUNING.noiseBars; i++) {
      const b = document.createElement('span');
      b.className = 'bar';
      this.noise.append(b);
      this.noiseBars.push(b);
    }
    this.status = div('status-line');
    this.toasts = div('toasts');
    this.root.append(this.crosshair, this.interactLabel, this.objective, this.topRight, this.crouch, this.noise, this.status, this.toasts);
    this.card = div('overlay-card hidden');
    this.fade = div('fade');
    this.pause = div('overlay-card pause hidden');
    parent.append(this.root, this.card, this.fade, this.pause);
  }

  show(on: boolean): void {
    this.root.classList.toggle('hidden', !on);
  }

  setObjective(levelName: string, goal: string): void {
    this.objective.innerHTML = '';
    const n = div('obj-level');
    n.textContent = levelName;
    const g = div('obj-goal');
    g.textContent = goal;
    this.objective.append(n, g);
  }

  setInteract(label: string | null): void {
    this.crosshair.classList.toggle('active', !!label);
    this.interactLabel.textContent = label ?? '';
  }

  setCrouched(on: boolean): void {
    this.crouch.classList.toggle('on', on);
  }

  setNoise(radius: number): void {
    const lit = Math.ceil((radius / TUNING.noiseMax) * TUNING.noiseBars);
    this.noiseBars.forEach((b, i) => {
      b.classList.toggle('lit', i < lit);
      b.classList.toggle('loud', i < lit && i >= 3);
    });
  }

  setTopRight(info: HudTopRight): void {
    const parts = [`<div class="clock">${info.clock}</div>`];
    if (info.deadline) parts.push(`<div class="deadline ${info.deadlineUrgent ? 'urgent' : ''}">${info.deadline}</div>`);
    if (info.favors) {
      const dots = '●'.repeat(info.favors.left) + '○'.repeat(info.favors.total - info.favors.left);
      parts.push(`<div class="favors">Favors ${dots}</div>`);
    }
    if (info.elevator) parts.push(`<div class="elevator">${info.elevator}</div>`);
    const html = parts.join('');
    if (this.topRight.innerHTML !== html) this.topRight.innerHTML = html;
  }

  setStatus(text: string): void {
    if (this.status.textContent !== text) this.status.textContent = text;
  }

  toast(text: string, seconds = TUNING.toastSeconds): void {
    const t = div('toast');
    t.textContent = text;
    this.toasts.append(t);
    window.setTimeout(() => t.classList.add('out'), seconds * 1000);
    window.setTimeout(() => t.remove(), seconds * 1000 + 600);
  }

  clearToasts(): void {
    this.toasts.innerHTML = '';
  }

  /** Level intro card. Resolves when the player clicks (that click also grabs pointer lock). */
  showIntro(title: string, goal: string, body: string, extra: string, onStart: () => void): void {
    this.card.innerHTML = '';
    const h = document.createElement('h2');
    h.textContent = title;
    const g = div('card-goal');
    g.textContent = goal;
    const p = document.createElement('p');
    p.textContent = body;
    this.card.append(h, g, p);
    if (extra) {
      const x = div('card-extra');
      x.textContent = extra;
      this.card.append(x);
    }
    const b = document.createElement('button');
    b.className = 'btn primary';
    b.textContent = 'Clock in (click)';
    b.addEventListener('click', () => {
      this.card.classList.add('hidden');
      onStart();
    });
    this.card.append(b);
    this.card.classList.remove('hidden');
  }

  hideIntro(): void {
    this.card.classList.add('hidden');
  }

  showPause(onResume: () => void, onQuit: () => void): void {
    this.pause.innerHTML = '';
    const h = document.createElement('h2');
    h.textContent = 'On a break';
    const p = document.createElement('p');
    p.textContent = 'The office waits. The office always waits.';
    const resume = document.createElement('button');
    resume.className = 'btn primary';
    resume.textContent = 'Back to work (click)';
    resume.addEventListener('click', onResume);
    const quit = document.createElement('button');
    quit.className = 'btn';
    quit.textContent = 'Quit to menu';
    quit.addEventListener('click', onQuit);
    this.pause.append(h, p, resume, quit);
    this.pause.classList.remove('hidden');
  }

  hidePause(): void {
    this.pause.classList.add('hidden');
  }

  /** Fade to black, show a line of text, run `mid`, fade back in. */
  fadeThrough(text: string, mid: () => void, holdMs = 2600): Promise<void> {
    return new Promise((resolve) => {
      this.fade.textContent = text;
      this.fade.classList.add('on');
      window.setTimeout(() => {
        mid();
        window.setTimeout(() => {
          this.fade.classList.remove('on');
          window.setTimeout(resolve, 500);
        }, holdMs);
      }, 500);
    });
  }
}

function div(cls: string): HTMLDivElement {
  const d = document.createElement('div');
  d.className = cls;
  return d;
}
