import { events } from '../core/Events';
import { WANDERER_POOL } from '../data/npcs';
import type { PlayerController } from '../player/PlayerController';
import type { LevelRuntime } from '../world/LevelBuilder';
import type { NPC } from './NPC';
import { NPCState } from './NPCStates';

// Global tension pacing. Heat is a blend of how aware anyone is of you, how much
// noise you've been making and how close you are to the exit. Low heat for too
// long means the level has gone quiet, so a wanderer shows up on a route that
// crosses your likely path. High heat freezes patrols so trouble doesn't dogpile.

const TUNING = {
  weightAwareness: 0.55,
  weightNoise: 0.25,
  weightExit: 0.2,
  noisePerEvent: 0.06,
  noiseDecay: 0.25,
  lowHeat: 0.22,
  highHeat: 0.6,
  lowHeatSpawnAfter: 20,
  minSpawnDistance: 12,
  /** A spawn cell must be at least this far outside the player's view direction (degrees). */
  minSpawnAngle: 80,
  firstSpawnNotBefore: 25,
};

export class Director {
  heat = 0;
  private noiseHeat = 0;
  private lowTimer = 0;
  private spawned = 0;
  private time = 0;
  private off: () => void;

  constructor(
    private readonly level: LevelRuntime,
    private readonly budget: number,
    private readonly spawn: (defId: string, cell: [number, number], patrol: [number, number][]) => NPC,
  ) {
    this.off = events.on('noise', (e) => {
      if (e.fromPlayer) this.noiseHeat = Math.min(1, this.noiseHeat + TUNING.noisePerEvent * (e.radius / 7));
    });
  }

  update(dt: number, player: PlayerController, npcs: NPC[]): void {
    this.time += dt;
    this.noiseHeat = Math.max(0, this.noiseHeat - TUNING.noiseDecay * dt);
    let maxAw = 0;
    let focus: NPC | null = null;
    for (const n of npcs) if (n.awareness > maxAw) { maxAw = n.awareness; focus = n; }
    const [W, H] = this.level.data.gridSize;
    const diag = Math.hypot(W, H) * this.level.data.cellSize;
    const exitDist = Math.hypot(player.x - this.level.exitCenter.x, player.z - this.level.exitCenter.z);
    const exitProx = 1 - Math.min(1, exitDist / (diag * 0.5));
    this.heat = Math.min(1, TUNING.weightAwareness * maxAw + TUNING.weightNoise * this.noiseHeat + TUNING.weightExit * exitProx);

    // Hold everyone but the focused NPC in place while things are tense.
    const hold = this.heat > TUNING.highHeat;
    for (const n of npcs) n.held = hold && n !== focus && n.state === NPCState.PATROL;

    if (this.heat < TUNING.lowHeat) this.lowTimer += dt;
    else this.lowTimer = 0;
    if (this.lowTimer > TUNING.lowHeatSpawnAfter && this.spawned < this.budget && this.time > TUNING.firstSpawnNotBefore) {
      this.lowTimer = 0;
      this.trySpawn(player, npcs);
    }
  }

  private trySpawn(player: PlayerController, npcs: NPC[]): void {
    const present = new Set(npcs.map((n) => n.def.id));
    const defId = WANDERER_POOL.find((id) => !present.has(id));
    if (!defId) return;
    const nav = this.level.nav;
    // Route crosses the player's likely path: the midpoint of their path to the exit.
    const path = nav.findPath({ x: player.x, z: player.z }, this.level.exitCenter);
    if (path.length === 0) return;
    const mid = path[Math.floor(path.length / 2)];
    const midCell = nav.nearestWalkable(...nav.worldToCell(mid.x, mid.z));
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    const cell = nav.randomWalkable((c, r) => {
      const p = nav.cellCenter(c, r);
      const dx = p.x - player.x, dz = p.z - player.z;
      const d = Math.hypot(dx, dz);
      if (d < TUNING.minSpawnDistance) return false;
      if (this.level.isExit(p.x, p.z)) return false;
      const angle = (Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / d))) * 180) / Math.PI;
      const hidden = this.level.colliders.segmentBlocked(player.x, 1.6, player.z, p.x, 1.6, p.z);
      // Never pop into view: it must be behind a wall, or well behind the player.
      return hidden || angle > TUNING.minSpawnAngle;
    });
    if (!cell) return;
    const npc = this.spawn(defId, cell, [cell, midCell]);
    npc.wanderer = true;
    this.spawned++;
  }

  dispose(): void {
    this.off();
  }
}
