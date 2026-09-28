import type { Input } from '../core/Input';
import type { EncounterContext, ExcuseDef, Outcome } from '../data/types';
import { DEFLECTIONS, FLOUNDERS, OPENERS, PANIC_PREFIX, REACTIONS, pick } from '../data/dialogueLines';
import type { NPC } from '../ai/NPC';
import { outcomeFor, type EncounterFacts, type ExcuseRegistry, type LineItem } from './ExcuseRegistry';
import type { DialogueUI } from './DialogueUI';
import type { OfficeMemory } from './OfficeMemory';

// The heart of the game: opener -> 3-4 excuses on a 6 s fuse -> maybe a follow-up
// probe with a 3 s pivot -> priced verdict -> outcome. Running out of time is not
// a skip: you blurt the riskiest thing on screen. Panic is a mechanic.

const TUNING = {
  choiceSeconds: 6,
  pivotSeconds: 3,
  pauseAfterLine: 0.35,
  /** The verdict auto-continues after this long once the reaction has finished typing. */
  verdictHold: 4.5,
  followUpBase: 0.3,
  followUpPerTalk: 0.55,
  bigHandTalkativeness: 0.8,
  pivot: { good: 0, deflect: 1, flounder: 2, timeout: 3 },
  /** Ignore confirm briefly so the key that started the talk doesn't skip the opener. */
  inputGrace: 0.25,
  playerVoice: 205,
  blipEveryChars: 2,
};

type Phase = 'opener' | 'choose' | 'player' | 'followUp' | 'pivot' | 'pivotLine' | 'reaction';
export type PivotResult = keyof typeof TUNING.pivot | null;

export interface EncounterResult {
  npc: NPC;
  excuse: ExcuseDef;
  outcome: Outcome;
  total: number;
  items: LineItem[];
  blurted: boolean;
  pivot: PivotResult;
  facts: EncounterFacts;
}

export interface DialogueAudio {
  voice(pitch: number): void;
  tick(urgent: boolean): void;
  select(): void;
}

export class DialogueSystem {
  active = false;
  private phase: Phase = 'opener';
  private speaker: 'npc' | 'player' = 'npc';
  private npc!: NPC;
  private facts!: EncounterFacts;
  private hand: ExcuseDef[] = [];
  private excuse!: ExcuseDef;
  private blurted = false;
  private pivot: PivotResult = null;
  private pivotOptions: Array<{ text: string; kind: Exclude<PivotResult, null | 'timeout'> }> = [];
  private phaseTime = 0;
  private timerStartsAt = 0;
  private highlight = -1;
  private waiting = 0;
  private items: LineItem[] = [];
  private total = 0;
  private outcome: Outcome = 'PASSED';
  private voicePitch = 200;
  private blipCount = 0;
  private lastTick = 0;
  private sinceStart = 0;

  constructor(
    private readonly ui: DialogueUI,
    private readonly registry: ExcuseRegistry,
    private readonly memory: OfficeMemory,
    private readonly audio: DialogueAudio,
    private readonly onResolve: (r: EncounterResult) => void,
  ) {}

  /** The NPC's line is still typing out (drives lip-flap on photoreal heads). */
  get npcTalking(): boolean {
    return this.active && this.speaker === 'npc' && !this.ui.lineDone;
  }

  get activeNpc(): NPC | null {
    return this.active ? this.npc : null;
  }

  start(npc: NPC, facts: EncounterFacts): void {
    this.active = true;
    this.npc = npc;
    this.facts = facts;
    this.blurted = false;
    this.pivot = null;
    this.sinceStart = 0;
    const handSize = npc.def.talkativeness >= TUNING.bigHandTalkativeness ? 4 : 3;
    this.hand = this.registry.draw(facts, handSize);
    this.ui.open();
    this.ui.setHint('Space / click: skip');
    this.say('npc', this.opener());
    this.enter('opener');
  }

  /** Tear down mid-conversation (quit to menu from pause). No result is reported. */
  abort(): void {
    if (!this.active) return;
    this.active = false;
    this.ui.close();
  }

  update(dt: number, input: Input): void {
    if (!this.active) return;
    this.sinceStart += dt;
    this.phaseTime += dt;
    const typed = this.ui.update(dt);
    this.blipFor(typed);
    const confirm = this.sinceStart > TUNING.inputGrace && input.confirm;

    switch (this.phase) {
      case 'opener':
      case 'player':
      case 'followUp':
      case 'pivotLine': {
        if (!this.ui.lineDone) {
          if (confirm) this.ui.skip();
          break;
        }
        this.waiting += dt;
        if (this.waiting < TUNING.pauseAfterLine) break;
        if (this.phase === 'opener') this.showExcuses();
        else if (this.phase === 'player') this.afterExcuse();
        else if (this.phase === 'followUp') this.showPivots();
        else this.resolve();
        break;
      }
      case 'choose':
      case 'pivot': {
        const count = this.phase === 'choose' ? this.hand.length : this.pivotOptions.length;
        const key = input.choiceKey();
        if (key >= 0 && key < count) { this.choose(key); break; }
        const nav = input.navDelta();
        if (nav) {
          this.highlight = this.highlight < 0 ? 0 : (this.highlight + nav + count) % count;
          this.ui.setHighlight(this.highlight);
        }
        if (confirm && this.highlight >= 0) { this.choose(this.highlight); break; }
        const limit = this.phase === 'choose' ? TUNING.choiceSeconds : TUNING.pivotSeconds;
        const elapsed = Math.max(0, this.phaseTime - this.timerStartsAt);
        const left = limit - elapsed;
        this.ui.setTimer(left / limit, left);
        if (this.phaseTime > this.timerStartsAt && Math.ceil(left) !== this.lastTick && left > 0) {
          this.lastTick = Math.ceil(left);
          this.audio.tick(left < 2);
        }
        if (left <= 0) this.timeout();
        break;
      }
      case 'reaction': {
        if (!this.ui.lineDone) {
          if (confirm) this.ui.skip();
          break;
        }
        this.waiting += dt;
        if (confirm || this.waiting > TUNING.verdictHold) this.finish();
        break;
      }
    }
  }

  /** Mouse clicks on DOM buttons land here (when the pointer isn't locked). */
  private choose(i: number): void {
    this.audio.select();
    this.ui.markPicked(i);
    this.ui.hideTimer();
    if (this.phase === 'choose') {
      this.excuse = this.hand[i];
      this.say('player', this.excuse.text);
      this.enter('player');
    } else {
      const opt = this.pivotOptions[i];
      this.pivot = opt.kind;
      this.say('player', opt.text);
      this.enter('pivotLine');
    }
  }

  private timeout(): void {
    this.ui.hideTimer();
    if (this.phase === 'choose') {
      this.blurted = true;
      this.excuse = this.registry.worst(this.hand);
      this.ui.markPicked(this.hand.indexOf(this.excuse));
      this.say('player', pick(PANIC_PREFIX) + this.excuse.text);
      this.enter('player');
    } else {
      this.pivot = 'timeout';
      this.say('player', '…');
      this.enter('pivotLine');
    }
  }

  private showExcuses(): void {
    const views = this.hand.map((e) => ({
      text: e.text,
      used: this.memory.useCount(e.id),
      chips: [e.category, '●'.repeat(e.risk) + '○'.repeat(5 - e.risk)],
    }));
    this.timerStartsAt = this.ui.showChoices(views, (i) => this.phase === 'choose' && this.choose(i));
    this.ui.setHint('1-4 · or wheel/mouse + click · too slow and you blurt the worst one');
    this.enter('choose');
  }

  private afterExcuse(): void {
    const e = this.excuse;
    const chance = TUNING.followUpBase + TUNING.followUpPerTalk * this.npc.def.talkativeness;
    if (e.followUp && (e.tags.includes('needs_detail') || Math.random() < chance)) {
      this.say('npc', e.followUp);
      this.enter('followUp');
    } else {
      this.resolve();
    }
  }

  private showPivots(): void {
    const opts: typeof this.pivotOptions = [
      { text: this.excuse.pivotLine ?? "You know what, I'm getting coffee first. Want one?", kind: 'good' },
      { text: pick(DEFLECTIONS), kind: 'deflect' },
      { text: pick(FLOUNDERS), kind: 'flounder' },
    ];
    for (let i = opts.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [opts[i], opts[j]] = [opts[j], opts[i]];
    }
    this.pivotOptions = opts;
    this.timerStartsAt = this.ui.showChoices(opts.map((o) => ({ text: o.text })), (i) => this.phase === 'pivot' && this.choose(i));
    this.ui.setHint('3 seconds. Recover.');
    this.enter('pivot');
  }

  private resolve(): void {
    this.items = this.registry.price(this.excuse, this.facts, { panic: this.blurted });
    if (this.pivot) {
      const labels = { good: 'Pivoted smoothly', deflect: 'Deflected (they noticed)', flounder: 'Floundered', timeout: 'Said nothing at all' };
      this.items.push({ label: labels[this.pivot], value: TUNING.pivot[this.pivot] });
    }
    const sum = this.items.reduce((s, it) => s + it.value, 0);
    this.total = Math.max(0, Math.round(sum));
    this.outcome = outcomeFor(this.total);
    // Nobody files an HR report the first time they stop you; they just walk you back.
    // Panic blurts don't get this mercy: that's the cost of freezing.
    if (this.outcome === 'CAUGHT' && this.facts.priorStops === 0 && !this.blurted) {
      this.outcome = 'ESCORTED';
      this.items.push({ label: 'First offense: let off with a walk', value: 0 });
    }
    this.ui.clearChoices();
    this.npc.react(this.outcome);
    this.say('npc', this.reaction());
    this.ui.showVerdict(this.items, this.total, this.outcome, this.blurted);
    this.ui.setHint('Space / click to continue');
    this.enter('reaction');
  }

  private finish(): void {
    this.active = false;
    this.ui.close();
    this.onResolve({
      npc: this.npc, excuse: this.excuse, outcome: this.outcome, total: this.total,
      items: this.items, blurted: this.blurted, pivot: this.pivot, facts: this.facts,
    });
  }

  private enter(phase: Phase): void {
    this.phase = phase;
    this.phaseTime = 0;
    this.waiting = 0;
    this.highlight = -1;
    this.lastTick = 0;
  }

  private say(who: 'npc' | 'player', text: string): void {
    this.speaker = who;
    if (who === 'npc') {
      const d = this.npc.def;
      this.ui.setSpeaker(d.name, d.role, this.npc.look.tagline, `#${d.color.toString(16).padStart(6, '0')}`, false);
      this.voicePitch = this.npc.look.voice;
    } else {
      this.ui.setSpeaker('You', 'Mid-level. Wants to leave.', '', '#e8e2cf', true);
      this.voicePitch = TUNING.playerVoice;
    }
    this.ui.setLine(text);
    this.blipCount = 0;
  }

  private blipFor(chars: string): void {
    for (const ch of chars) {
      if (!/[a-z0-9]/i.test(ch)) continue;
      if (this.blipCount++ % TUNING.blipEveryChars === 0) this.audio.voice(this.voicePitch);
    }
  }

  private opener(): string {
    const ctx = this.facts.contexts;
    const id = this.npc.def.id, arch = this.npc.def.archetype;
    let best: string[] = [];
    let bestScore = -1;
    for (const node of OPENERS) {
      const whoScore = node.who === id ? 3 : node.who === arch ? 2 : node.who === 'any' ? 1 : -1;
      if (whoScore < 0) continue;
      const when: EncounterContext[] = node.when ?? [];
      if (!when.every((c) => ctx.has(c))) continue;
      const score = whoScore + when.length * 3;
      if (score > bestScore) { bestScore = score; best = [...node.lines]; }
      else if (score === bestScore) best.push(...node.lines);
    }
    return best.length ? pick(best) : 'Arre, where are you going?';
  }

  private reaction(): string {
    const d = this.npc.def;
    if (this.outcome === 'PASSED' && d.archetype === 'boss' && this.excuse.category === 'corporate') {
      return 'Good initiative. I mean that. Go. Circle back.';
    }
    const lines = REACTIONS[d.id]?.[this.outcome] ?? REACTIONS[d.archetype]?.[this.outcome] ?? REACTIONS.any[this.outcome] ?? ['…Okay.'];
    return pick(lines);
  }
}
