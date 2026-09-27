import { events, type NoiseEvent } from '../core/Events';
import type { NPC } from './NPC';

// Routes noise events to every NPC whose (hearingMul-scaled) range covers them.
// No occlusion: walls don't block sound in this office, on purpose.

export class Hearing {
  private off: () => void;

  constructor(private readonly npcs: () => NPC[], private readonly now: () => number, private readonly onDistraction: (npc: NPC, e: NoiseEvent) => void) {
    this.off = events.on('noise', (e) => this.dispatch(e));
  }

  private dispatch(e: NoiseEvent): void {
    for (const npc of this.npcs()) {
      const d = Math.hypot(npc.x - e.x, npc.z - e.z);
      if (d > e.radius * npc.def.hearingMul) continue;
      if (!e.fromPlayer && e.kind === 'jam') this.onDistraction(npc, e);
      else npc.hear(e, this.now());
    }
  }

  dispose(): void {
    this.off();
  }
}
