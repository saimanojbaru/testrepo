import type { LineItem } from './ExcuseRegistry';
import type { Outcome } from '../data/types';

// Bottom-third DOM overlay: speaker, typewriter line, draining timer bar, staggered
// choice buttons and the verdict breakdown. Pure presentation; DialogueSystem owns
// the flow and timing.

const TUNING = {
  msPerChar: 30,
  choiceStaggerMs: 100,
  timerRedBelow: 2,
};

export interface ChoiceView {
  text: string;
  /** Small chips under the text, e.g. category, risk dots. */
  chips?: string[];
  used?: number;
}

export class DialogueUI {
  private root: HTMLDivElement;
  private nameEl: HTMLSpanElement;
  private roleEl: HTMLSpanElement;
  private taglineEl: HTMLDivElement;
  private lineEl: HTMLDivElement;
  private timerEl: HTMLDivElement;
  private timerFill: HTMLDivElement;
  private choicesEl: HTMLDivElement;
  private verdictEl: HTMLDivElement;
  private hintEl: HTMLDivElement;
  private buttons: HTMLButtonElement[] = [];
  /** Tapping the panel (not a choice) acts like Space: skip / continue. Needed on touch screens. */
  onTap: (() => void) | null = null;

  private full = '';
  private shown = 0;
  private acc = 0;

  constructor(parent: HTMLElement) {
    this.root = el('div', 'dialogue hidden');
    const panel = el('div', 'dlg-panel');
    const speaker = el('div', 'dlg-speaker');
    this.nameEl = el('span', 'dlg-name');
    this.roleEl = el('span', 'dlg-role');
    speaker.append(this.nameEl, this.roleEl);
    this.taglineEl = el('div', 'dlg-tagline');
    this.lineEl = el('div', 'dlg-line');
    this.timerEl = el('div', 'dlg-timer hidden');
    this.timerFill = el('div', 'dlg-timer-fill');
    this.timerEl.append(this.timerFill);
    this.choicesEl = el('div', 'dlg-choices');
    this.verdictEl = el('div', 'dlg-verdict hidden');
    this.hintEl = el('div', 'dlg-hint');
    panel.append(speaker, this.taglineEl, this.lineEl, this.timerEl, this.choicesEl, this.verdictEl, this.hintEl);
    this.root.append(panel);
    parent.append(this.root);
    this.lineEl.addEventListener('click', () => this.skip());
    this.root.addEventListener('pointerdown', (e) => {
      if (!(e.target as HTMLElement).closest('.dlg-choice')) this.onTap?.();
    });
  }

  open(): void {
    this.root.classList.remove('hidden');
    this.verdictEl.classList.add('hidden');
    this.clearChoices();
  }

  close(): void {
    this.root.classList.add('hidden');
    this.clearChoices();
  }

  setSpeaker(name: string, role: string, tagline: string, color: string, isPlayer: boolean): void {
    this.nameEl.textContent = name;
    this.roleEl.textContent = role;
    this.taglineEl.textContent = tagline;
    this.nameEl.style.color = color;
    this.root.classList.toggle('player-speaking', isPlayer);
  }

  setLine(text: string): void {
    this.full = text;
    this.shown = 0;
    this.acc = 0;
    this.lineEl.textContent = '';
  }

  get lineDone(): boolean {
    return this.shown >= this.full.length;
  }

  skip(): void {
    this.shown = this.full.length;
    this.lineEl.textContent = this.full;
  }

  /** Advances the typewriter; returns the newly revealed characters (for voice blips). */
  update(dt: number): string {
    if (this.lineDone) return '';
    this.acc += dt * 1000;
    const n = Math.floor(this.acc / TUNING.msPerChar);
    if (n <= 0) return '';
    this.acc -= n * TUNING.msPerChar;
    const before = this.shown;
    this.shown = Math.min(this.full.length, this.shown + n);
    this.lineEl.textContent = this.full.slice(0, this.shown);
    return this.full.slice(before, this.shown);
  }

  showChoices(choices: ChoiceView[], onPick: (i: number) => void): number {
    this.clearChoices();
    choices.forEach((c, i) => {
      const b = document.createElement('button');
      b.className = 'dlg-choice';
      b.style.animationDelay = `${i * TUNING.choiceStaggerMs}ms`;
      const num = el('span', 'num');
      num.textContent = String(i + 1);
      const txt = el('span', 'txt');
      txt.textContent = c.text;
      b.append(num, txt);
      const chips = [...(c.chips ?? [])];
      if (c.used) chips.unshift(`[USED${c.used > 1 ? ` x${c.used}` : ''}]`);
      if (chips.length) {
        const meta = el('span', 'meta');
        for (const chip of chips) {
          const s = el('span', chip.startsWith('[USED') ? 'chip used' : 'chip');
          s.textContent = chip;
          meta.append(s);
        }
        b.append(meta);
      }
      b.addEventListener('click', (e) => {
        e.stopPropagation();
        onPick(i);
      });
      b.addEventListener('mouseenter', () => this.setHighlight(i));
      this.choicesEl.append(b);
      this.buttons.push(b);
    });
    // Seconds until the last button has appeared; the timer starts after that.
    return (choices.length * TUNING.choiceStaggerMs) / 1000;
  }

  clearChoices(): void {
    this.choicesEl.innerHTML = '';
    this.buttons = [];
    this.timerEl.classList.add('hidden');
  }

  setHighlight(i: number): void {
    this.buttons.forEach((b, j) => b.classList.toggle('active', j === i));
  }

  markPicked(i: number): void {
    this.buttons.forEach((b, j) => {
      b.disabled = true;
      b.classList.toggle('picked', j === i);
    });
  }

  setTimer(fraction: number, secondsLeft: number): void {
    this.timerEl.classList.remove('hidden');
    this.timerFill.style.transform = `scaleX(${Math.max(0, fraction)})`;
    this.timerEl.classList.toggle('red', secondsLeft < TUNING.timerRedBelow);
  }

  hideTimer(): void {
    this.timerEl.classList.add('hidden');
  }

  setHint(text: string): void {
    this.hintEl.textContent = text;
  }

  showVerdict(items: LineItem[], total: number, outcome: Outcome): void {
    this.verdictEl.innerHTML = '';
    const list = el('div', 'items');
    for (const it of items) {
      const row = el('div', `item ${it.value > 0 ? 'bad' : it.value < 0 ? 'good' : ''}`);
      const l = el('span', 'l');
      l.textContent = it.label;
      const v = el('span', 'v');
      v.textContent = `${it.value > 0 ? '+' : ''}${formatNum(it.value)}`;
      row.append(l, v);
      list.append(row);
    }
    const sum = el('div', `sum outcome-${outcome.toLowerCase()}`);
    sum.innerHTML = `<span>Suspicion ${formatNum(total)}</span><strong>${outcomeLabel(outcome)}</strong>`;
    this.verdictEl.append(list, sum);
    this.verdictEl.classList.remove('hidden');
  }
}

function el<K extends keyof HTMLElementTagNameMap>(tag: K, cls: string): HTMLElementTagNameMap[K] {
  const e = document.createElement(tag);
  e.className = cls;
  return e;
}

function formatNum(n: number): string {
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

export function outcomeLabel(o: Outcome): string {
  return { PASSED: 'PASSED', PROBED: 'PROBED — they walk with you', ESCORTED: 'ESCORTED BACK TO YOUR DESK', CAUGHT: 'CAUGHT' }[o];
}
