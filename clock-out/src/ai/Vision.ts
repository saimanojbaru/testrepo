import type { ColliderWorld } from '../world/Colliders';
import type { PlayerController } from '../player/PlayerController';
import type { NPC } from './NPC';

// Vision cone + line of sight. The player is sampled at head and chest height, so
// a standing player's head pokes over a 1.5 m divider (seen) while a crouched one
// sits entirely below it (hidden). That single height relationship is the whole
// stealth game, so keep these sample heights in sync with the divider height.

const TUNING = {
  /** Outer fraction of the half-FOV that counts as peripheral (fills at half rate). */
  peripheralFraction: 0.3,
  /** Inside this distance you're noticed regardless of facing (you walked into them). */
  touchRange: 1.0,
  /** Second sample point below the head. */
  chestDrop: 0.45,
};

export interface SightResult {
  visible: boolean;
  peripheral: boolean;
  dist: number;
}

export function checkSight(npc: NPC, player: PlayerController, colliders: ColliderWorld, fovDeg: number, range: number): SightResult {
  const dx = player.x - npc.x;
  const dz = player.z - npc.z;
  const dist = Math.hypot(dx, dz);
  const miss: SightResult = { visible: false, peripheral: false, dist };
  if (dist > range) return miss;

  let peripheral = false;
  if (dist > TUNING.touchRange) {
    const fx = -Math.sin(npc.lookYaw), fz = -Math.cos(npc.lookYaw);
    const cos = (fx * dx + fz * dz) / dist;
    const angle = Math.acos(Math.max(-1, Math.min(1, cos)));
    const half = ((fovDeg / 2) * Math.PI) / 180;
    if (angle > half) return miss;
    peripheral = angle > half * (1 - TUNING.peripheralFraction);
  }

  const ey = npc.eyeY;
  const samples = [player.headHeight, player.headHeight - TUNING.chestDrop];
  for (const sy of samples) {
    if (!colliders.segmentBlocked(npc.x, ey, npc.z, player.x, sy, player.z)) return { visible: true, peripheral, dist };
  }
  return miss;
}
