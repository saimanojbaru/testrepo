import { events, type NoiseKind } from '../core/Events';
import type { PlayerController } from './PlayerController';

// Footsteps are discrete noise events, one per stride, with a radius set by how
// the player is moving. Walls do not block noise: that is a deliberate design
// choice (simple, readable, and funny when Kavita hears you through drywall).

const TUNING = {
  radius: { crouch: 3, walk: 7, sprint: 14, bump: 10, door: 12 } as Record<string, number>,
  stride: { crouch: 0.65, walk: 0.85, sprint: 1.25 } as Record<string, number>,
};

export class PlayerNoise {
  /** Radius of the most recent continuous noise, for the HUD meter. */
  currentRadius = 0;
  private travelled = 0;

  constructor(private readonly onStep: (kind: 'crouch' | 'walk' | 'sprint') => void) {}

  update(dt: number, player: PlayerController): void {
    const state = player.moveState;
    if (state === 'idle') {
      this.currentRadius = Math.max(0, this.currentRadius - dt * 20);
      return;
    }
    this.currentRadius = TUNING.radius[state];
    this.travelled += player.speed * dt;
    const stride = TUNING.stride[state];
    if (this.travelled >= stride) {
      this.travelled -= stride;
      events.emit('noise', { x: player.x, z: player.z, radius: TUNING.radius[state], kind: state, fromPlayer: true });
      this.onStep(state);
    }
  }

  oneShot(kind: Extract<NoiseKind, 'bump' | 'door'>, x: number, z: number): void {
    this.currentRadius = Math.max(this.currentRadius, TUNING.radius[kind]);
    events.emit('noise', { x, z, radius: TUNING.radius[kind], kind, fromPlayer: true });
  }

  static radiusFor(kind: string): number {
    return TUNING.radius[kind] ?? 0;
  }
}
