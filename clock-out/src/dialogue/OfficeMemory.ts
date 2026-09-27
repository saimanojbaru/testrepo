// What the office remembers about you. Persisted to localStorage so the same
// excuse gets worse every time you use it, across sessions, forever (or until you
// press Reset, which the office also remembers, spiritually).

const TUNING = {
  storageKey: 'clockout.memory.v1',
  /** Extra suspicion for the 2nd, 3rd, 4th+ use of the same excuse. */
  reusePenalty: [0, 2, 4, 6],
  timesCaughtPenalty: 0.5,
  /** Extra suspicion when this specific NPC has already heard this excuse from you. */
  sameNpcPenalty: 1,
  reputation: { flaky: 3, suspicious: 8, legendTrophies: 3 },
};

export type Reputation = 'nobody' | 'flaky' | 'suspicious' | 'legend';

export interface LevelRecord { stars: number; bestTime: number; ending: string; }

interface MemoryData {
  usedExcuses: Record<string, number>;
  timesCaught: number;
  /** Raw reputation score; the label is derived from it. */
  reputation: number;
  legendExcuses: string[];
  /** npcId -> excuse ids they've personally heard. */
  heardBy: Record<string, string[]>;
  levels: Record<string, LevelRecord>;
  escapes: number;
}

export interface SessionData {
  usedExcuses: Record<string, number>;
  caughtThisSession: number;
}

function blank(): MemoryData {
  return { usedExcuses: {}, timesCaught: 0, reputation: 0, legendExcuses: [], heardBy: {}, levels: {}, escapes: 0 };
}

export class OfficeMemory {
  private data: MemoryData;
  readonly session: SessionData = { usedExcuses: {}, caughtThisSession: 0 };

  constructor() {
    this.data = this.load();
  }

  private load(): MemoryData {
    try {
      const raw = localStorage.getItem(TUNING.storageKey);
      if (!raw) return blank();
      return { ...blank(), ...(JSON.parse(raw) as Partial<MemoryData>) };
    } catch {
      return blank();
    }
  }

  private save(): void {
    try {
      localStorage.setItem(TUNING.storageKey, JSON.stringify(this.data));
    } catch {
      // Storage can be unavailable (private mode, quota). Memory still works for this session.
    }
  }

  reset(): void {
    this.data = blank();
    this.session.usedExcuses = {};
    this.session.caughtThisSession = 0;
    this.save();
  }

  useCount(excuseId: string): number {
    return this.data.usedExcuses[excuseId] ?? 0;
  }

  get timesCaught(): number { return this.data.timesCaught; }
  get legendExcuses(): readonly string[] { return this.data.legendExcuses; }
  get escapes(): number { return this.data.escapes; }
  get reputationScore(): number { return this.data.reputation; }

  /** Memory flags used by ExcuseDef.requires.memoryFlag, e.g. 'used:casserole'. */
  hasFlag(flag: string): boolean {
    if (flag.startsWith('used:')) return this.useCount(flag.slice(5)) > 0;
    if (flag === 'caught') return this.data.timesCaught > 0;
    if (flag.startsWith('legend:')) return this.data.legendExcuses.includes(flag.slice(7));
    return false;
  }

  heard(npcId: string, excuseId: string): boolean {
    return this.data.heardBy[npcId]?.includes(excuseId) ?? false;
  }

  heardAnything(npcId: string): boolean {
    return (this.data.heardBy[npcId]?.length ?? 0) > 0;
  }

  /** Suspicion added by history: reuse, being caught before, and telling the same person twice. */
  memoryPenalty(excuseId: string, repeatable: boolean, npcId: string): { reuse: number; caught: number; sameNpc: number } {
    const n = this.useCount(excuseId);
    let reuse = TUNING.reusePenalty[Math.min(n, TUNING.reusePenalty.length - 1)];
    if (repeatable) reuse = Math.floor(reuse / 2);
    return {
      reuse,
      caught: this.data.timesCaught * TUNING.timesCaughtPenalty,
      sameNpc: this.heard(npcId, excuseId) ? TUNING.sameNpcPenalty : 0,
    };
  }

  recordUse(excuseId: string, npcId: string): void {
    this.data.usedExcuses[excuseId] = this.useCount(excuseId) + 1;
    this.session.usedExcuses[excuseId] = (this.session.usedExcuses[excuseId] ?? 0) + 1;
    const heard = (this.data.heardBy[npcId] ??= []);
    if (!heard.includes(excuseId)) heard.push(excuseId);
    this.data.reputation += 1;
    this.save();
  }

  /**
   * Level 3 needs the casserole to have happened, whether or not you were there,
   * and everyone on that floor to have heard about it. Returns true if it was forced.
   */
  ensureUsed(excuseId: string, heardByNpcs: string[]): boolean {
    const forced = this.useCount(excuseId) === 0;
    if (forced) this.data.usedExcuses[excuseId] = 1;
    for (const id of heardByNpcs) {
      const heard = (this.data.heardBy[id] ??= []);
      if (!heard.includes(excuseId)) heard.push(excuseId);
    }
    this.save();
    return forced;
  }

  recordCaught(): void {
    this.data.timesCaught += 1;
    this.data.reputation += 2;
    this.session.caughtThisSession += 1;
    this.save();
  }

  recordLegend(excuseId: string): boolean {
    if (this.data.legendExcuses.includes(excuseId)) return false;
    this.data.legendExcuses.push(excuseId);
    this.save();
    return true;
  }

  recordEscape(levelId: string, stars: number, time: number, ending: string): void {
    this.data.escapes += 1;
    const prev = this.data.levels[levelId];
    if (!prev || stars > prev.stars || (stars === prev.stars && time < prev.bestTime)) {
      this.data.levels[levelId] = { stars, bestTime: time, ending };
    }
    this.save();
  }

  levelRecord(levelId: string): LevelRecord | undefined {
    return this.data.levels[levelId];
  }

  reputationLabel(): Reputation {
    if (this.data.legendExcuses.length >= TUNING.reputation.legendTrophies) return 'legend';
    if (this.data.reputation >= TUNING.reputation.suspicious) return 'suspicious';
    if (this.data.reputation >= TUNING.reputation.flaky) return 'flaky';
    return 'nobody';
  }

  /** Excuse ids used on this profile, most-used first (for the HR report). */
  history(): Array<[string, number]> {
    return Object.entries(this.data.usedExcuses).sort((a, b) => b[1] - a[1]);
  }
}
