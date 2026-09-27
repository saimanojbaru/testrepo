import * as THREE from 'three';
import type { NPCDef, NPCLook } from '../data/types';
import type { NavGrid, Vec2 } from '../world/NavGrid';
import type { NoiseEvent } from '../core/Events';
import { NPCState, type NPCStateId } from './NPCStates';

// An NPC is a body (blocky, readable silhouette), a set of senses (Vision/Hearing
// write into `awareness`), a memory of where it last noticed the player, and a
// state machine (NPCStates) that decides what to do with all of that.

const TUNING = {
  turnRate: 5.5,
  waypointReach: 0.18,
  repathMoving: 0.8,
  eyeHeight: 1.62,
  /** Short NPCs still see over a 1.5 m divider when standing, like real people do. */
  minEyeHeight: 1.56,
  bubbleSeconds: 3.6,
  bubbleCharsPerLine: 30,
  hearGain: { crouch: 0.04, walk: 0.1, sprint: 0.2, bump: 0.35, door: 0.35, jam: 0, phone: 0 } as Record<string, number>,
  headLookSeconds: 1.8,
  coneOpacity: 0.13,
  coneSegments: 20,
};

export class NPC {
  readonly group = new THREE.Group();
  readonly head = new THREE.Group();
  private legs: THREE.Object3D[] = [];
  private arms: THREE.Object3D[] = [];
  private indicator: THREE.Sprite;
  private indicatorKind: '' | '?' | '!' = '';
  private bubble: THREE.Sprite | null = null;
  private bubbleTimer = 0;
  readonly cone: THREE.Mesh;
  private coneFov = -1;
  private coneMat: THREE.MeshBasicMaterial;

  x: number;
  z: number;
  yaw: number;
  headYaw = 0;
  private headTargetYaw = 0;
  private headLookTimer = 0;

  state: NPCStateId = NPCState.IDLE;
  stateTime = 0;
  awareness = 0;
  lastStimulus = -99;
  lastSeen: Vec2 | null = null;
  lastHeard: Vec2 | null = null;
  seeing = false;
  sawCrouch = false;
  sawSprint = false;

  home: { x: number; z: number; yaw: number };
  patrol: Vec2[] = [];
  patrolIdx = 0;
  waitTimer = 0;

  path: Vec2[] = [];
  private pathIdx = 0;
  private pathTarget: Vec2 | null = null;
  private repathTimer = 0;

  /** Awareness can't grow until this sim time (after a PASSED encounter). */
  graceUntil = 0;
  distractedUntil = 0;
  investigateTarget: Vec2 | null = null;
  investigateTimer = 0;
  investigateIsDistraction = false;
  chatPartner: NPC | null = null;
  chatterCooldown = 10 + Math.random() * 10;
  chatPair: string[] | null = null;
  chatLine = 0;
  chatSaid = false;
  held = false;
  barkTimer = 4 + Math.random() * 8;
  lastBark = -1;
  snitchCooldown = 0;
  probeTimer = 0;
  /** How long the player has been walking away from this NPC's CONFRONT. */
  ignoreTimer = 0;
  lastConfrontDist = Infinity;
  /** Set by the Director for NPCs it spawned mid-level. */
  wanderer = false;
  /** Awareness at confront time is remembered for opener choice. */
  encounters = 0;

  private walkPhase = 0;
  private walkAmount = 0;
  onSay: ((npc: NPC, text: string) => void) | null = null;

  constructor(readonly def: NPCDef, readonly look: NPCLook, x: number, z: number, yaw: number) {
    this.x = x;
    this.z = z;
    this.yaw = yaw;
    this.home = { x, z, yaw };
    buildModel(this, this.group, this.head, this.legs, this.arms);
    this.group.scale.setScalar(look.height);
    this.indicator = new THREE.Sprite(new THREE.SpriteMaterial({ map: glyphTexture('?'), transparent: true, depthTest: false }));
    this.indicator.scale.set(0.34, 0.34, 1);
    this.indicator.position.y = 2.25;
    this.indicator.visible = false;
    this.indicator.renderOrder = 10;
    this.group.add(this.indicator);
    this.coneMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: TUNING.coneOpacity, depthWrite: false, side: THREE.DoubleSide });
    this.cone = new THREE.Mesh(new THREE.BufferGeometry(), this.coneMat);
    this.cone.renderOrder = 1;
    this.syncTransform();
  }

  get id(): string { return this.def.id; }
  get eyeY(): number { return Math.max(TUNING.minEyeHeight, TUNING.eyeHeight * this.look.height); }
  get lookYaw(): number { return this.yaw + this.headYaw; }

  setState(next: NPCStateId): void {
    if (next === this.state) return;
    this.state = next;
    this.stateTime = 0;
    this.path = [];
    this.pathTarget = null;
  }

  setPatrol(points: Vec2[]): void {
    this.patrol = points;
    this.patrolIdx = 0;
    if (points.length) this.home = { x: points[0].x, z: points[0].z, yaw: this.yaw };
  }

  /** Called by Hearing when a noise event reaches this NPC. */
  hear(e: NoiseEvent, time: number): void {
    if (e.fromPlayer) {
      if (time < this.graceUntil) return;
      // On the phone you only notice things that are genuinely loud.
      if (time < this.distractedUntil && e.kind !== 'bump' && e.kind !== 'door' && e.kind !== 'sprint') return;
      this.awareness = Math.min(1, this.awareness + (TUNING.hearGain[e.kind] ?? 0));
      this.lastHeard = { x: e.x, z: e.z };
      this.lastStimulus = time;
    }
    this.glanceAt(e.x, e.z);
  }

  /** Turns the head (not the body) toward a point for a moment. */
  glanceAt(x: number, z: number): void {
    const target = Math.atan2(-(x - this.x), -(z - this.z));
    this.headTargetYaw = clampAngle(wrap(target - this.yaw), 1.3);
    this.headLookTimer = TUNING.headLookSeconds;
  }

  lookAround(t: number, amount = 0.7): void {
    if (this.headLookTimer > 0) return;
    this.headTargetYaw = Math.sin(t * 0.8 + this.x) * amount;
  }

  faceToward(x: number, z: number, dt: number): void {
    this.turnTo(Math.atan2(-(x - this.x), -(z - this.z)), dt);
  }

  turnTo(target: number, dt: number): void {
    const d = wrap(target - this.yaw);
    const step = TUNING.turnRate * dt;
    this.yaw = wrap(this.yaw + Math.max(-step, Math.min(step, d)));
  }

  /** Walks toward `target` along the nav grid. Returns true on arrival. */
  moveTo(target: Vec2, speed: number, dt: number, nav: NavGrid, movingTarget = false): boolean {
    const retarget = !this.pathTarget || Math.hypot(this.pathTarget.x - target.x, this.pathTarget.z - target.z) > 0.6;
    this.repathTimer -= dt;
    if (retarget || (movingTarget && this.repathTimer <= 0)) {
      this.path = nav.findPath({ x: this.x, z: this.z }, target);
      if (!this.path.length && nav.lineWalkable({ x: this.x, z: this.z }, target)) this.path = [target];
      this.pathIdx = 0;
      this.pathTarget = { ...target };
      this.repathTimer = TUNING.repathMoving;
    }
    while (this.pathIdx < this.path.length) {
      const wp = this.path[this.pathIdx];
      const dx = wp.x - this.x, dz = wp.z - this.z;
      const d = Math.hypot(dx, dz);
      if (d < TUNING.waypointReach) { this.pathIdx++; continue; }
      const step = Math.min(d, speed * dt);
      this.x += (dx / d) * step;
      this.z += (dz / d) * step;
      this.turnTo(Math.atan2(-dx, -dz), dt);
      this.walkAmount = Math.min(1, speed / 1.4);
      this.walkPhase += speed * dt * 4.2;
      return false;
    }
    return true;
  }

  stopMoving(): void {
    this.walkAmount = 0;
  }

  say(text: string, seconds = TUNING.bubbleSeconds): void {
    if (this.bubble) {
      this.group.remove(this.bubble);
      this.bubble.material.map?.dispose();
      this.bubble.material.dispose();
    }
    this.bubble = makeBubble(this.def.name.split(' ')[0], text, TUNING.bubbleCharsPerLine);
    this.bubble.position.y = 2.55;
    this.group.add(this.bubble);
    this.bubbleTimer = seconds + text.length * 0.02;
    this.onSay?.(this, text);
  }

  /** Per-step animation + bookkeeping that doesn't depend on AI state. */
  animate(dt: number, fovDeg: number, range: number, showCone: boolean, playerDist: number): void {
    this.stateTime += dt;
    if (this.headLookTimer > 0) this.headLookTimer -= dt;
    this.headYaw += (this.headTargetYaw - this.headYaw) * Math.min(1, dt * 5);
    if (this.walkAmount > 0) this.walkAmount = Math.max(0, this.walkAmount - dt * 3);
    const swing = Math.sin(this.walkPhase) * 0.55 * this.walkAmount;
    this.legs[0].rotation.x = swing;
    this.legs[1].rotation.x = -swing;
    this.arms[0].rotation.x = -swing * 0.8;
    this.arms[1].rotation.x = swing * 0.8;
    this.head.rotation.y = this.headYaw;

    if (this.bubble) {
      this.bubbleTimer -= dt;
      this.bubble.visible = playerDist < 18;
      if (this.bubbleTimer <= 0) {
        this.group.remove(this.bubble);
        this.bubble.material.map?.dispose();
        this.bubble.material.dispose();
        this.bubble = null;
      }
    }

    const kind: '' | '?' | '!' =
      this.state === NPCState.CONFRONT || this.state === NPCState.CHASE ? '!' : this.awareness > 0.06 ? '?' : '';
    if (kind !== this.indicatorKind) {
      this.indicatorKind = kind;
      this.indicator.visible = kind !== '';
      if (kind) this.indicator.material.map = glyphTexture(kind);
      this.indicator.material.needsUpdate = true;
    }
    if (kind === '?') {
      this.indicator.material.color.setRGB(1, 1 - this.awareness * 0.6, 0.2);
      this.indicator.material.opacity = 0.35 + 0.65 * this.awareness;
      const s = 0.24 + 0.16 * this.awareness;
      this.indicator.scale.set(s, s, 1);
    } else if (kind === '!') {
      this.indicator.material.color.setRGB(1, 0.25, 0.2);
      this.indicator.material.opacity = 1;
      this.indicator.scale.set(0.42, 0.42, 1);
    }

    this.cone.visible = showCone && this.state !== NPCState.ESCORT;
    if (this.cone.visible) {
      if (Math.abs(fovDeg - this.coneFov) > 1) {
        this.coneFov = fovDeg;
        this.cone.geometry.dispose();
        const half = THREE.MathUtils.degToRad(Math.min(fovDeg, 350) / 2);
        const g = new THREE.CircleGeometry(1, TUNING.coneSegments, Math.PI / 2 - half, half * 2);
        g.rotateX(-Math.PI / 2);
        this.cone.geometry = g;
      }
      this.cone.scale.set(range, 1, range);
      const a = this.awareness;
      this.coneMat.color.setRGB(1, 1 - a * 0.75, 1 - a * 0.95);
      this.coneMat.opacity = TUNING.coneOpacity + a * 0.12;
    }
    this.syncTransform();
  }

  private syncTransform(): void {
    this.group.position.set(this.x, 0, this.z);
    this.group.rotation.y = this.yaw;
    this.cone.position.set(this.x, 0.03, this.z);
    this.cone.rotation.y = this.lookYaw;
  }

  dispose(): void {
    // Body materials are shared through matCache across every NPC, so only geometry is freed here.
    this.group.traverse((o) => (o as THREE.Mesh).geometry?.dispose());
    if (this.bubble) {
      this.bubble.material.map?.dispose();
      this.bubble.material.dispose();
    }
    this.indicator.material.dispose();
    this.cone.geometry.dispose();
    this.coneMat.dispose();
  }
}

export function wrap(a: number): number {
  while (a > Math.PI) a -= Math.PI * 2;
  while (a < -Math.PI) a += Math.PI * 2;
  return a;
}

function clampAngle(a: number, max: number): number {
  return Math.max(-max, Math.min(max, a));
}

// ---------------------------------------------------------------------------
// Model: a blocky person. Silhouette and colour carry the character, because
// most of the time you only see a head and shoulders over a cubicle wall.
// ---------------------------------------------------------------------------

const matCache = new Map<number, THREE.MeshLambertMaterial>();
function mat(color: number): THREE.MeshLambertMaterial {
  let m = matCache.get(color);
  if (!m) {
    m = new THREE.MeshLambertMaterial({ color, flatShading: true });
    matCache.set(color, m);
  }
  return m;
}

function part(parent: THREE.Object3D, sx: number, sy: number, sz: number, color: number, x: number, y: number, z: number): THREE.Mesh {
  const m = new THREE.Mesh(new THREE.BoxGeometry(sx, sy, sz), mat(color));
  m.position.set(x, y, z);
  parent.add(m);
  return m;
}

function buildModel(npc: NPC, root: THREE.Group, head: THREE.Group, legs: THREE.Object3D[], arms: THREE.Object3D[]): void {
  const { def, look } = npc;
  const body = def.color;
  const acc = new Set(look.accessories);

  for (const side of [-1, 1]) {
    const hip = new THREE.Group();
    hip.position.set(side * 0.11, 0.86, 0);
    part(hip, 0.15, 0.8, 0.17, look.pants, 0, -0.4, 0);
    part(hip, 0.16, 0.08, 0.26, 0x1e1c1a, 0, -0.82, -0.04);
    root.add(hip);
    legs.push(hip);
  }
  part(root, 0.46, 0.64, 0.27, body, 0, 1.16, 0);
  if (acc.has('cardigan')) {
    part(root, 0.14, 0.6, 0.02, 0xefe6dc, 0, 1.17, -0.14);
  }
  if (acc.has('tie')) {
    part(root, 0.16, 0.5, 0.02, 0xe8e6df, 0, 1.2, -0.14);
    part(root, 0.06, 0.42, 0.02, 0xa82828, 0, 1.2, -0.152);
  }
  if (acc.has('lanyard')) {
    part(root, 0.02, 0.3, 0.02, 0x2f6dbf, -0.07, 1.33, -0.145).rotation.z = -0.3;
    part(root, 0.02, 0.3, 0.02, 0x2f6dbf, 0.07, 1.33, -0.145).rotation.z = 0.3;
    part(root, 0.1, 0.13, 0.015, 0xf2f2f2, 0, 1.15, -0.15);
  }
  if (acc.has('backpack')) part(root, 0.36, 0.42, 0.16, 0x33373d, 0, 1.15, 0.2);
  if (acc.has('toolbelt')) {
    part(root, 0.5, 0.08, 0.3, 0x5a4027, 0, 0.88, 0);
    part(root, 0.1, 0.14, 0.08, 0x5a4027, 0.2, 0.8, -0.14);
  }
  if (acc.has('pearls')) part(root, 0.22, 0.03, 0.02, 0xf5f1e6, 0, 1.43, -0.13);

  for (const side of [-1, 1]) {
    const shoulder = new THREE.Group();
    shoulder.position.set(side * 0.3, 1.43, 0);
    part(shoulder, 0.12, 0.6, 0.14, body, 0, -0.3, 0);
    part(shoulder, 0.1, 0.1, 0.1, look.skin, 0, -0.64, 0);
    if (side === 1 && acc.has('clipboard')) {
      part(shoulder, 0.02, 0.32, 0.24, 0x8a6a42, -0.05, -0.62, -0.1);
      part(shoulder, 0.01, 0.26, 0.2, 0xf4f1e8, -0.064, -0.6, -0.1);
    }
    if (side === -1 && acc.has('mug')) {
      const m = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.045, 0.11, 8), mat(0xe8e6df));
      m.position.set(0.02, -0.66, -0.06);
      shoulder.add(m);
    }
    root.add(shoulder);
    arms.push(shoulder);
  }

  part(root, 0.12, 0.08, 0.12, look.skin, 0, 1.51, 0);
  head.position.set(0, 1.55, 0);
  root.add(head);
  part(head, 0.28, 0.32, 0.28, look.skin, 0, 0.16, 0);
  part(head, 0.045, 0.045, 0.02, 0x1a1a1a, -0.065, 0.2, -0.145);
  part(head, 0.045, 0.045, 0.02, 0x1a1a1a, 0.065, 0.2, -0.145);
  part(head, 0.05, 0.07, 0.04, shade(look.skin, 0.92), 0, 0.13, -0.155);
  if (acc.has('glasses')) {
    part(head, 0.1, 0.07, 0.015, 0x222222, -0.068, 0.2, -0.155);
    part(head, 0.1, 0.07, 0.015, 0x222222, 0.068, 0.2, -0.155);
  }
  if (acc.has('headset')) {
    part(head, 0.34, 0.03, 0.05, 0x1c1c1c, 0, -0.05, 0.02);
    part(head, 0.05, 0.1, 0.1, 0x1c1c1c, -0.17, -0.02, 0.0);
    part(head, 0.05, 0.1, 0.1, 0x1c1c1c, 0.17, -0.02, 0.0);
  }
  const hair = look.hair;
  switch (look.hairStyle) {
    case 'bob':
      part(head, 0.32, 0.1, 0.32, hair, 0, 0.35, 0.01);
      part(head, 0.05, 0.26, 0.3, hair, -0.16, 0.2, 0.02);
      part(head, 0.05, 0.26, 0.3, hair, 0.16, 0.2, 0.02);
      part(head, 0.3, 0.26, 0.05, hair, 0, 0.2, 0.15);
      break;
    case 'big': {
      // Brenda's hair has its own postcode; you will spot it over any divider.
      const s = new THREE.Mesh(new THREE.IcosahedronGeometry(0.25, 0), mat(hair));
      s.position.set(0, 0.36, 0.04);
      s.scale.set(1.15, 0.85, 1.05);
      head.add(s);
      part(head, 0.06, 0.24, 0.3, hair, -0.17, 0.18, 0.03);
      part(head, 0.06, 0.24, 0.3, hair, 0.17, 0.18, 0.03);
      break;
    }
    case 'bald':
      part(head, 0.3, 0.07, 0.2, hair, 0, 0.2, 0.06).scale.set(1, 1, 1);
      break;
    case 'short':
      part(head, 0.3, 0.07, 0.3, hair, 0, 0.34, 0.01);
      part(head, 0.3, 0.14, 0.05, hair, 0, 0.26, 0.14);
      break;
    case 'bun': {
      part(head, 0.3, 0.07, 0.3, hair, 0, 0.34, 0.01);
      part(head, 0.3, 0.2, 0.05, hair, 0, 0.23, 0.14);
      const bun = new THREE.Mesh(new THREE.IcosahedronGeometry(0.09, 0), mat(hair));
      bun.position.set(0, 0.42, 0.1);
      head.add(bun);
      break;
    }
    case 'cap':
      part(head, 0.31, 0.1, 0.31, 0x2c3e66, 0, 0.36, 0.01);
      part(head, 0.26, 0.02, 0.14, 0x2c3e66, 0, 0.32, -0.2);
      break;
    case 'swoop':
      part(head, 0.3, 0.09, 0.3, hair, 0, 0.35, 0.01);
      part(head, 0.22, 0.08, 0.1, hair, 0.05, 0.38, -0.12).rotation.z = -0.2;
      break;
  }
}

function shade(color: number, k: number): number {
  const c = new THREE.Color(color).multiplyScalar(k);
  return c.getHex();
}

// ---------------------------------------------------------------------------
// Sprites
// ---------------------------------------------------------------------------

const glyphCache = new Map<string, THREE.CanvasTexture>();
function glyphTexture(glyph: string): THREE.CanvasTexture {
  let t = glyphCache.get(glyph);
  if (t) return t;
  const c = document.createElement('canvas');
  c.width = c.height = 128;
  const g = c.getContext('2d')!;
  g.font = 'bold 110px Helvetica, Arial, sans-serif';
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  g.lineWidth = 10;
  g.strokeStyle = 'rgba(20,20,20,0.9)';
  g.strokeText(glyph, 64, 70);
  g.fillStyle = '#ffffff';
  g.fillText(glyph, 64, 70);
  t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  glyphCache.set(glyph, t);
  return t;
}

export function wrapText(text: string, perLine: number): string[] {
  const words = text.split(' ');
  const lines: string[] = [];
  let cur = '';
  for (const w of words) {
    if ((cur + ' ' + w).trim().length > perLine && cur) {
      lines.push(cur);
      cur = w;
    } else cur = (cur + ' ' + w).trim();
  }
  if (cur) lines.push(cur);
  return lines;
}

function makeBubble(name: string, text: string, perLine: number): THREE.Sprite {
  const lines = wrapText(text, perLine).slice(0, 5);
  const W = 512, lh = 40, pad = 22;
  const H = pad * 2 + 30 + lines.length * lh;
  const c = document.createElement('canvas');
  c.width = W;
  c.height = H + 20;
  const g = c.getContext('2d')!;
  g.fillStyle = 'rgba(250,248,240,0.94)';
  g.strokeStyle = 'rgba(40,40,40,0.5)';
  g.lineWidth = 3;
  g.beginPath();
  g.roundRect(4, 4, W - 8, H - 8, 18);
  g.moveTo(W / 2 - 16, H - 5);
  g.lineTo(W / 2, H + 16);
  g.lineTo(W / 2 + 16, H - 5);
  g.fill();
  g.stroke();
  g.fillStyle = '#7a6a5a';
  g.font = 'bold 24px Helvetica, Arial, sans-serif';
  g.fillText(name.toUpperCase(), pad, pad + 20);
  g.fillStyle = '#2b2b2e';
  g.font = '30px Helvetica, Arial, sans-serif';
  lines.forEach((l, i) => g.fillText(l, pad, pad + 30 + (i + 1) * lh - 8));
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthTest: true }));
  const width = 2.0;
  sprite.scale.set(width, (width * c.height) / W, 1);
  sprite.center.set(0.5, 0);
  sprite.renderOrder = 11;
  return sprite;
}
