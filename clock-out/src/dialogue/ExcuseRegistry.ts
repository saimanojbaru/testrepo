import { EXCUSES } from '../data/excuses';
import type { Archetype, EncounterContext, ExcuseCategory, ExcuseDef, NPCDef } from '../data/types';
import type { OfficeMemory } from './OfficeMemory';

// Picks which excuses you get offered and prices them. The price is shown to the
// player afterwards as a line-item breakdown, because learning *why* Brenda
// bought the romance and Marcus didn't buy the goldfish is the fun.

const TUNING = {
  contextWeight: 3,
  immuneWeight: 0.35,
  usedWeight: 0.8,
  /** Chance a previously-used excuse is forced into the hand so memory stays visible. */
  showUsedChance: 0.6,
  /** Every hand holds at least one unused excuse at or below this risk. */
  plausibleRisk: 3,
  immunePenalty: 4,
  contextMissPenalty: 2,
  seenCrouching: 2,
  seenSprinting: 1,
  nearExit: 1,
  voluntaryBonus: -1,
  panicPenalty: 1,
  probeViolation: 2,
  /** Each earlier stop by the same person this run: "didn't I just see you?" */
  repeatStop: 3,
};

/** How much each archetype believes each category. Negative = more believable. */
const AFFINITY: Record<Archetype, Partial<Record<ExcuseCategory, [number, string]>>> = {
  boss: { absurd: [3, "doesn't do whimsy"], evasive: [2, 'needs a deliverable'], corporate: [-1, 'respects process'], romantic: [1, 'finds this unprofessional'] },
  gossip: { romantic: [-3, 'LOVES this'], domestic: [-1, 'relates deeply'], absurd: [-1, 'wants the story'], evasive: [2, 'needs details'], corporate: [1, 'is bored by this'] },
  chatty: { absurd: [-2, 'is delighted'], romantic: [-1, 'is rooting for you'], corporate: [1, 'wants to talk shop'] },
  hr: { evasive: [3, 'requires specifics'], medical: [-1, 'legally cannot ask'], romantic: [2, 'reaches for a disclosure form'], absurd: [2, 'is documenting this'] },
  intern: { absurd: [-2, 'believes everything'], corporate: [-1, 'is impressed'], evasive: [-1, "doesn't want to pry"] },
  security: { evasive: [3, 'has seen this before'], domestic: [-1, 'says "contractors, man"'], absurd: [1, 'squints'] },
  coworker: { corporate: [1, 'knows your calendar'], absurd: [1, 'has heard better'], romantic: [-1, 'is invested now'] },
};

const CONTEXT_TAGS: Array<[string, EncounterContext, string]> = [
  ['near_kitchen', 'near_kitchen', 'the kitchen is the other way'],
  ['near_exit', 'near_exit', "you're nowhere near the exit"],
  ['near_copier', 'near_copier', 'no copier in sight'],
];

export interface EncounterFacts {
  npc: NPCDef;
  contexts: Set<EncounterContext>;
  charisma: number;
  /** How many times this NPC already stopped you this run. */
  priorStops: number;
  forced?: string[];
  levelModifier?: LineItem;
}

export interface LineItem { label: string; value: number; }

export class ExcuseRegistry {
  constructor(private readonly memory: OfficeMemory) {}

  get(id: string): ExcuseDef {
    const e = EXCUSES.find((x) => x.id === id);
    if (!e) throw new Error(`Unknown excuse ${id}`);
    return e;
  }

  available(npc: NPCDef): ExcuseDef[] {
    return EXCUSES.filter((e) => {
      if (e.requires?.memoryFlag && !this.memory.hasFlag(e.requires.memoryFlag)) return false;
      if (e.requires?.trait && !npc[e.requires.trait]) return false;
      return true;
    });
  }

  draw(facts: EncounterFacts, count: number): ExcuseDef[] {
    const pool = this.available(facts.npc);
    const weightOf = (e: ExcuseDef): number => {
      let w = 1;
      for (const [tag, ctx] of CONTEXT_TAGS) if (e.tags.includes(tag) && facts.contexts.has(ctx)) w += TUNING.contextWeight;
      if (e.immuneArchetypes?.includes(facts.npc.archetype)) w *= TUNING.immuneWeight;
      if (this.memory.useCount(e.id) > 0) w *= TUNING.usedWeight;
      // Freshly unlocked sequels deserve to be seen.
      if (e.requires?.memoryFlag) w += 2;
      return w;
    };
    const hand: ExcuseDef[] = [];
    for (const id of facts.forced ?? []) {
      const e = pool.find((x) => x.id === id);
      if (e) hand.push(e);
    }
    const rest = pool.filter((e) => !hand.includes(e));
    while (hand.length < count && rest.length) {
      const total = rest.reduce((s, e) => s + weightOf(e), 0);
      let r = Math.random() * total;
      let i = 0;
      for (; i < rest.length - 1; i++) {
        r -= weightOf(rest[i]);
        if (r <= 0) break;
      }
      hand.push(rest.splice(i, 1)[0]);
    }
    // Always leave one plausible out, unless you've burned them all.
    if (!hand.some((e) => e.risk <= TUNING.plausibleRisk && this.memory.useCount(e.id) === 0)) {
      const safe = rest.find((e) => e.risk <= TUNING.plausibleRisk && this.memory.useCount(e.id) === 0);
      if (safe) replaceWorst(hand, safe, facts.forced);
    }
    // Keep Office Memory visible: a used excuse shows up marked [USED] most of the time.
    if (!hand.some((e) => this.memory.useCount(e.id) > 0) && Math.random() < TUNING.showUsedChance) {
      const used = rest.filter((e) => this.memory.useCount(e.id) > 0);
      if (used.length) replaceWorst(hand, used[Math.floor(Math.random() * used.length)], facts.forced);
    }
    return shuffle(hand);
  }

  /** Full priced breakdown for choosing `e` in this encounter. */
  price(e: ExcuseDef, facts: EncounterFacts, opts: { panic: boolean }): LineItem[] {
    const first = facts.npc.name.split(' ')[0];
    const items: LineItem[] = [{ label: `"${shorten(e.text)}" (risk)`, value: e.risk }];
    const mem = this.memory.memoryPenalty(e.id, e.tags.includes('repeatable'), facts.npc.id);
    if (mem.reuse) items.push({ label: `Used ${this.memory.useCount(e.id)}x before`, value: mem.reuse });
    if (mem.sameNpc) items.push({ label: `${first} has heard this one`, value: mem.sameNpc });
    if (mem.caught) items.push({ label: `Caught ${this.memory.timesCaught}x on record`, value: mem.caught });
    const aff = AFFINITY[facts.npc.archetype][e.category];
    if (aff) items.push({ label: `${first} ${aff[1]}`, value: aff[0] });
    if (e.immuneArchetypes?.includes(facts.npc.archetype)) items.push({ label: `${first} isn't buying it`, value: TUNING.immunePenalty });
    for (const [tag, ctx, miss] of CONTEXT_TAGS) {
      if (e.tags.includes(tag) && !facts.contexts.has(ctx)) items.push({ label: `…but ${miss}`, value: TUNING.contextMissPenalty });
    }
    if (facts.contexts.has('crouching')) items.push({ label: `${first} saw you crouching`, value: TUNING.seenCrouching });
    if (facts.contexts.has('sprinting')) items.push({ label: `${first} saw you running`, value: TUNING.seenSprinting });
    if (facts.contexts.has('near_exit') && !e.tags.includes('near_exit')) items.push({ label: 'Caught near the exit', value: TUNING.nearExit });
    if (facts.contexts.has('voluntary')) items.push({ label: 'You approached them (confident)', value: TUNING.voluntaryBonus });
    if (facts.contexts.has('probe_violation')) items.push({ label: 'Wandered toward the exit mid-walk', value: TUNING.probeViolation });
    if (facts.levelModifier) items.push({ ...facts.levelModifier });
    if (facts.priorStops > 0) items.push({ label: `${first} already stopped you ${facts.priorStops === 1 ? 'once' : `${facts.priorStops}x`} today`, value: TUNING.repeatStop * facts.priorStops });
    if (opts.panic) items.push({ label: 'Panic blurt', value: TUNING.panicPenalty });
    if (facts.charisma > 0) items.push({ label: `Smooth streak x${facts.charisma}`, value: -facts.charisma });
    return items;
  }

  /** The excuse you panic into: the riskiest thing on screen. */
  worst(hand: ExcuseDef[]): ExcuseDef {
    return [...hand].sort((a, b) => b.risk - a.risk || this.memory.useCount(b.id) - this.memory.useCount(a.id))[0];
  }
}

/** Swap the riskiest non-forced card in the hand for `incoming`. */
function replaceWorst(hand: ExcuseDef[], incoming: ExcuseDef, forced: string[] = []): void {
  let idx = -1;
  let worst = -1;
  hand.forEach((e, i) => {
    if (forced.includes(e.id)) return;
    if (e.risk > worst) { worst = e.risk; idx = i; }
  });
  if (idx >= 0) hand[idx] = incoming;
}

function shuffle<T>(arr: T[]): T[] {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

function shorten(text: string): string {
  return text.length > 34 ? `${text.slice(0, 32).trimEnd()}…` : text;
}

export function outcomeFor(total: number): 'PASSED' | 'PROBED' | 'ESCORTED' | 'CAUGHT' {
  if (total <= 3) return 'PASSED';
  if (total <= 6) return 'PROBED';
  if (total <= 9) return 'ESCORTED';
  return 'CAUGHT';
}
