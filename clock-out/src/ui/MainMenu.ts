import type { LevelData } from '../data/types';
import type { OfficeMemory } from '../dialogue/OfficeMemory';
import { EXCUSES } from '../data/excuses';
import { NPC_DEFS, NPC_LOOKS } from '../data/npcs';
import { esc, formatTime } from './ResultsScreen';

// Title screen: level select, reputation, legend trophies, the cast, how to play,
// settings and the memory reset.

export interface Settings {
  sensitivity: number; // multiplier on Input's default
  volume: number;      // 0..1
  cones: boolean;      // draw NPC vision cones on the floor
}

const SETTINGS_KEY = 'clockout.settings.v1';

export function loadSettings(): Settings {
  const fallback: Settings = { sensitivity: 1, volume: 0.8, cones: true };
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    return raw ? { ...fallback, ...(JSON.parse(raw) as Partial<Settings>) } : fallback;
  } catch {
    return fallback;
  }
}

export function saveSettings(s: Settings): void {
  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(s));
  } catch {
    // Non-fatal: settings just won't persist.
  }
}

const REP_BLURB: Record<string, string> = {
  nobody: 'Nobody has noticed you yet. Keep it that way.',
  flaky: "People have started saying “oh, they're flaky” with a little smile.",
  suspicious: 'Priya has a folder. It has tabs.',
  legend: 'People tell your excuses at other companies.',
};

export class MainMenu {
  private root: HTMLDivElement;
  private resetArmed = false;

  constructor(
    parent: HTMLElement,
    private readonly levels: LevelData[],
    private readonly memory: OfficeMemory,
    private readonly settings: Settings,
    private readonly actions: { onPlay: (id: string) => void; onReset: () => void; onSettings: (s: Settings) => void; onGesture: () => void },
  ) {
    this.root = document.createElement('div');
    this.root.className = 'screen menu hidden';
    parent.append(this.root);
    // Any click on the menu counts as the audio-unlock gesture.
    this.root.addEventListener('pointerdown', () => this.actions.onGesture());
  }

  hide(): void {
    this.root.classList.add('hidden');
  }

  show(): void {
    this.resetArmed = false;
    const m = this.memory;
    const rep = m.reputationLabel();
    const trophies = m.legendExcuses.map((id) => EXCUSES.find((e) => e.id === id)).filter((e) => !!e);
    this.root.innerHTML = `
      <header class="menu-title">
        <h1>CLOCK OUT</h1>
        <p>A first-person stealth game about leaving work. The office is the enemy.</p>
      </header>
      <div class="menu-grid">
        <section class="menu-levels">
          <h2>Today's escapes</h2>
          ${this.levels.map((l, i) => {
            const rec = m.levelRecord(l.id);
            const stars = rec ? '★'.repeat(rec.stars) + '☆'.repeat(3 - rec.stars) : '☆☆☆';
            return `<div class="level-card">
              <div class="level-num">${i + 1}</div>
              <div class="level-info">
                <h3>${esc(l.name)}</h3>
                <p>${esc(l.brief)}</p>
                <div class="level-meta"><span class="stars">${stars}</span>${rec ? `<span>best ${formatTime(rec.bestTime)} · ${esc(rec.ending)}</span>` : '<span>not attempted</span>'}<span>par ${formatTime(l.parTime)}</span></div>
              </div>
              <button class="btn primary" data-play="${l.id}">Clock out</button>
            </div>`;
          }).join('')}
        </section>
        <aside class="menu-side">
          <div class="panel">
            <h2>Your reputation</h2>
            <div class="rep rep-${rep}">${rep.toUpperCase()}</div>
            <p class="muted">${REP_BLURB[rep]}</p>
            <p class="muted small">Escapes: ${m.escapes} · Times caught: ${m.timesCaught} · Excuses on record: ${m.history().reduce((s, [, n]) => s + n, 0)}</p>
          </div>
          <div class="panel">
            <h2>Legend excuses</h2>
            ${trophies.length ? `<ul class="trophies">${trophies.map((e) => `<li>🏆 “${esc(e.text)}”</li>`).join('')}</ul>` : '<p class="muted">No legends yet. Absurd excuses that actually work end up here. Have you tried the goldfish?</p>'}
          </div>
          <details class="panel">
            <summary><h2>How to play</h2></summary>
            <ul class="howto">
              <li><b>WASD</b> move · <b>Mouse</b> look · <b>Shift</b> sprint (loud) · <b>C</b> toggle crouch (or hold <b>Ctrl</b>) · <b>E</b> use / talk · <b>Esc</b> pause</li>
              <li>Dividers are 1.5 m tall. Crouch behind them and you're invisible. Stand up and you're a head on a stick.</li>
              <li>Footsteps are noise: crouch 3 m, walk 7 m, sprint 14 m. Walls don't block sound.</li>
              <li>The <b>?</b> over a head is growing suspicion. At <b>!</b> they come to talk.</li>
              <li>In a conversation you have <b>6 seconds</b> to pick an excuse (1–4). Too slow and you blurt the worst one.</li>
              <li>The office remembers. Reused excuses are marked <b>[USED]</b> and cost more every time.</li>
              <li>Sprint away from someone who's confronting you and they'll chase. If they catch you, that's a Quick Chat.</li>
            </ul>
          </details>
          <details class="panel">
            <summary><h2>Who's in today</h2></summary>
            <ul class="cast">
              ${Object.values(NPC_DEFS).map((d) => `<li><span class="swatch" style="background:#${d.color.toString(16).padStart(6, '0')}"></span><b>${esc(d.name)}</b> <span class="muted">${esc(d.role)}</span><br><i>${esc(NPC_LOOKS[d.id]?.tagline ?? '')}</i></li>`).join('')}
            </ul>
          </details>
          <details class="panel">
            <summary><h2>Settings</h2></summary>
            <label class="setting">Mouse sensitivity <input type="range" min="0.3" max="2.5" step="0.05" data-set="sensitivity" value="${this.settings.sensitivity}"></label>
            <label class="setting">Volume <input type="range" min="0" max="1" step="0.05" data-set="volume" value="${this.settings.volume}"></label>
            <label class="setting check"><input type="checkbox" data-set="cones" ${this.settings.cones ? 'checked' : ''}> Show vision cones</label>
          </details>
          <button class="btn danger" data-reset>Reset office memory</button>
        </aside>
      </div>`;
    this.root.querySelectorAll<HTMLButtonElement>('[data-play]').forEach((b) =>
      b.addEventListener('click', () => this.actions.onPlay(b.dataset.play!)));
    this.root.querySelectorAll<HTMLInputElement>('[data-set]').forEach((input) =>
      input.addEventListener('input', () => {
        const key = input.dataset.set as keyof Settings;
        if (key === 'cones') this.settings.cones = input.checked;
        else this.settings[key] = parseFloat(input.value);
        saveSettings(this.settings);
        this.actions.onSettings(this.settings);
      }));
    const reset = this.root.querySelector<HTMLButtonElement>('[data-reset]')!;
    reset.addEventListener('click', () => {
      if (!this.resetArmed) {
        this.resetArmed = true;
        reset.textContent = 'Really? Brenda will forget everything. Click again.';
        return;
      }
      this.actions.onReset();
      this.show();
    });
    this.root.classList.remove('hidden');
  }
}
