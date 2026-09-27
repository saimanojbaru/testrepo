import type * as THREE from 'three';
import type { MoveIntent } from '../core/Input';
import type { AABB, ColliderWorld } from '../world/Colliders';

// First-person office worker. No jumping: this is an office.
// Yaw convention (shared with NPCs): 0 faces -Z (north), -PI/2 faces +X (east);
// it is literally the camera's rotation.y.

const TUNING = {
  walkSpeed: 3.2,
  crouchSpeed: 1.55,
  sprintSpeed: 5.6,
  accel: 12,
  radius: 0.35,
  heightStand: 1.8,
  heightCrouch: 1.1,
  eyeStand: 1.64,
  eyeCrouch: 0.98,
  crouchLerp: 9,
  pitchLimit: 1.35,
  // Head bob: amplitude per state; frequency comes from distance travelled per stride.
  bobWalk: 0.032,
  bobSprint: 0.055,
  bobCrouch: 0.018,
  bobStride: 1.5,
  crouchSway: 0.012,
  /** Colliding with furniture above this speed is a noisy bump. */
  bumpMinSpeed: 2.4,
  bumpCooldown: 1.2,
};

export type MoveState = 'idle' | 'crouch' | 'walk' | 'sprint';

export class PlayerController {
  x = 0;
  z = 0;
  yaw = 0;
  pitch = 0;
  vx = 0;
  vz = 0;
  speed = 0;
  crouched = false;
  sprinting = false;
  /** 0 standing .. 1 fully crouched; drives eye height smoothly. */
  crouchT = 0;
  frozen = false;
  onBump: ((hit: AABB) => void) | null = null;

  private crouchLatched = false;
  private bobPhase = 0;
  private bumpCooldown = 0;
  private time = 0;

  constructor(private readonly colliders: ColliderWorld) {}

  get radius(): number { return TUNING.radius; }

  reset(x: number, z: number, yaw: number): void {
    this.x = x;
    this.z = z;
    this.yaw = yaw;
    this.pitch = 0;
    this.vx = this.vz = this.speed = 0;
    this.crouched = this.crouchLatched = this.sprinting = false;
    this.crouchT = 0;
    this.frozen = false;
  }

  look(dx: number, dy: number): void {
    if (this.frozen) return;
    this.yaw -= dx;
    this.pitch = Math.max(-TUNING.pitchLimit, Math.min(TUNING.pitchLimit, this.pitch - dy));
  }

  get moveState(): MoveState {
    if (this.speed < 0.3) return 'idle';
    if (this.crouched) return 'crouch';
    return this.sprinting ? 'sprint' : 'walk';
  }

  get eyeHeight(): number {
    return TUNING.eyeStand + (TUNING.eyeCrouch - TUNING.eyeStand) * this.crouchT;
  }

  /** Top of the head, used as the primary vision sample point. */
  get headHeight(): number {
    return (TUNING.heightStand + (TUNING.heightCrouch - TUNING.heightStand) * this.crouchT) - 0.08;
  }

  get height(): number {
    return TUNING.heightStand + (TUNING.heightCrouch - TUNING.heightStand) * this.crouchT;
  }

  update(dt: number, move: MoveIntent, crouchToggle: boolean): void {
    this.time += dt;
    this.bumpCooldown -= dt;
    if (this.frozen) {
      this.vx = this.vz = this.speed = 0;
      this.sprinting = false;
      return;
    }
    if (crouchToggle) this.crouchLatched = !this.crouchLatched;
    const wantsMove = move.forward !== 0 || move.right !== 0;
    // Sprinting stands you up; crouch-sprinting would defeat the noise model.
    if (move.sprint && wantsMove && move.forward > 0) this.crouchLatched = false;
    this.crouched = this.crouchLatched || move.crouchHeld;
    this.sprinting = move.sprint && wantsMove && move.forward > 0 && !this.crouched;
    this.crouchT += ((this.crouched ? 1 : 0) - this.crouchT) * Math.min(1, dt * TUNING.crouchLerp);

    const max = this.crouched ? TUNING.crouchSpeed : this.sprinting ? TUNING.sprintSpeed : TUNING.walkSpeed;
    let f = move.forward, r = move.right;
    const len = Math.hypot(f, r);
    if (len > 1) { f /= len; r /= len; }
    const sin = Math.sin(this.yaw), cos = Math.cos(this.yaw);
    const tx = (-sin * f + cos * r) * max;
    const tz = (-cos * f - sin * r) * max;
    const k = Math.min(1, dt * TUNING.accel);
    this.vx += (tx - this.vx) * k;
    this.vz += (tz - this.vz) * k;

    const preSpeed = Math.hypot(this.vx, this.vz);
    const nx = this.x + this.vx * dt;
    const nz = this.z + this.vz * dt;
    const res = this.colliders.resolveCircle(nx, nz, TUNING.radius, 0.05, this.height);
    // Velocity becomes the displacement we actually achieved, which gives wall sliding for free.
    this.vx = (res.x - this.x) / dt;
    this.vz = (res.z - this.z) / dt;
    this.x = res.x;
    this.z = res.z;
    this.speed = Math.hypot(this.vx, this.vz);

    if (res.hit && res.hit.kind !== 'wall' && preSpeed > TUNING.bumpMinSpeed && this.bumpCooldown <= 0) {
      this.bumpCooldown = TUNING.bumpCooldown;
      this.onBump?.(res.hit);
    }
    this.bobPhase += (this.speed * dt * Math.PI) / TUNING.bobStride;
  }

  applyCamera(camera: THREE.PerspectiveCamera): void {
    const amp = this.crouched ? TUNING.bobCrouch : this.sprinting ? TUNING.bobSprint : TUNING.bobWalk;
    const moving = Math.min(1, this.speed / TUNING.walkSpeed);
    const bob = Math.sin(this.bobPhase * 2) * amp * moving;
    camera.position.set(this.x, this.eyeHeight + bob, this.z);
    camera.rotation.order = 'YXZ';
    camera.rotation.y = this.yaw;
    camera.rotation.x = this.pitch;
    // A slow roll while crouched: you are a grown adult hiding behind a cubicle and your body knows it.
    camera.rotation.z = Math.sin(this.time * 1.3) * TUNING.crouchSway * this.crouchT + Math.cos(this.bobPhase) * amp * 0.25 * moving;
  }
}
