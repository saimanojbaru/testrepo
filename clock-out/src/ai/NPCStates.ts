import type { ColliderWorld } from '../world/Colliders';
import type { NavGrid, Vec2 } from '../world/NavGrid';
import type { PlayerController } from '../player/PlayerController';
import type { NPC } from './NPC';
import { checkSight } from './Vision';
import { STATE_BARKS, CHATTER, pick } from '../data/dialogueLines';

// NPC behaviour. Perception writes `awareness` (0..1); thresholds on it drive the
// escalation IDLE/PATROL -> SUSPICIOUS -> CONFRONT -> (dialogue) or CHASE -> CAUGHT.
// Only one NPC may escalate to CONFRONT at a time (the world's canEscalate), so a
// bad moment never becomes a dogpile.

export const NPCState = {
  IDLE: 'IDLE',
  PATROL: 'PATROL',
  CHATTER: 'CHATTER',
  SUSPICIOUS: 'SUSPICIOUS',
  INVESTIGATE: 'INVESTIGATE',
  CONFRONT: 'CONFRONT',
  CHASE: 'CHASE',
  ESCORT: 'ESCORT',
  RETURN: 'RETURN',
} as const;
export type NPCStateId = (typeof NPCState)[keyof typeof NPCState];

const TUNING = {
  suspiciousAt: 0.35,
  confrontAt: 1.0,
  /** Awareness per second for a standing player in full view at max range. */
  fillBase: 0.4,
  /** Extra fill multiplier that ramps in as the player gets closer (x(1+nearBonus) at 0 m). */
  nearBonus: 1.8,
  peripheralMul: 0.5,
  crouchMul: 0.4,
  sprintMul: 1.6,
  hydratingMul: 0.3,
  chatterMul: 0.5,
  distractedRangeMul: 0.35,
  distractedFovMul: 0.5,
  chatterRangeMul: 0.7,
  decayDelay: 2.0,
  decayRate: 0.15,
  /** While someone else is confronting, others top out here. */
  blockedCap: 0.92,
  suspiciousSpeedMul: 0.75,
  suspiciousGiveUpBelow: 0.15,
  confrontSpeedMul: 1.45,
  chaseSpeed: 4.7,
  confrontReach: 1.9,
  catchReach: 1.05,
  chaseTriggerDist: 3.2,
  /** Seconds of walking away from a confronting NPC before they give chase. */
  ignoreBeforeChase: 2.0,
  chaseGiveUp: 4.0,
  confrontTimeout: 10,
  investigateTime: 4.0,
  /** Close enough to the spot (targets are often props sitting in non-walkable cells). */
  investigateArrive: 1.9,
  investigateMaxWalk: 12,
  distractionTime: 10,
  patrolPauseMin: 1.0,
  patrolPauseMax: 2.6,
  barkMin: 10,
  barkMax: 20,
  barkHearRange: 15,
  chatterRange: 2.6,
  chatterTime: 6.5,
  chatterCooldown: 30,
  snitchCooldown: 20,
  escortSideOffset: 1.1,
};

export interface NPCWorld {
  time: number;
  player: PlayerController;
  colliders: ColliderWorld;
  nav: NavGrid;
  npcs: NPC[];
  /** Global FOV multiplier (the all-hands doubles it). */
  fovMul: number;
  playerHydrating: boolean;
  canEscalate(npc: NPC): boolean;
  startEncounter(npc: NPC): void;
  caught(npc: NPC, reason: 'chase'): void;
  onConfront(npc: NPC): void;
  onSnitch(npc: NPC): void;
}

export function effectiveVision(npc: NPC, w: NPCWorld): { fov: number; range: number } {
  let fov = npc.def.visionFov * w.fovMul;
  let range = npc.def.visionRange;
  if (w.time < npc.distractedUntil) {
    fov *= TUNING.distractedFovMul;
    range *= TUNING.distractedRangeMul;
  }
  if (npc.state === NPCState.CHATTER) range *= TUNING.chatterRangeMul;
  return { fov: Math.min(fov, 340), range };
}

function perceive(npc: NPC, dt: number, w: NPCWorld): void {
  const { fov, range } = effectiveVision(npc, w);
  const p = w.player;
  const sight = checkSight(npc, p, w.colliders, fov, range);
  npc.seeing = sight.visible;
  const graced = w.time < npc.graceUntil || npc.state === NPCState.ESCORT;
  if (sight.visible && !graced) {
    const near = 1 + TUNING.nearBonus * (1 - sight.dist / range);
    let rate = TUNING.fillBase * near;
    if (sight.peripheral) rate *= TUNING.peripheralMul;
    if (p.crouched) rate *= TUNING.crouchMul;
    if (p.sprinting) rate *= TUNING.sprintMul;
    if (w.playerHydrating) rate *= TUNING.hydratingMul;
    if (npc.state === NPCState.CHATTER) rate *= TUNING.chatterMul;
    npc.awareness = Math.min(1, npc.awareness + rate * dt);
    npc.lastSeen = { x: p.x, z: p.z };
    npc.lastStimulus = w.time;
    if (npc.awareness > 0.2) {
      npc.sawCrouch = npc.sawCrouch || p.crouched;
      npc.sawSprint = npc.sawSprint || p.sprinting;
    }
  } else if (w.time - npc.lastStimulus > TUNING.decayDelay) {
    npc.awareness = Math.max(0, npc.awareness - TUNING.decayRate * dt);
    if (npc.awareness === 0) {
      npc.sawCrouch = npc.sawSprint = false;
    }
  }
  if (graced) npc.awareness = Math.min(npc.awareness, 0.3);
  if (!w.canEscalate(npc)) npc.awareness = Math.min(npc.awareness, TUNING.blockedCap);
}

function calm(npc: NPC): boolean {
  return npc.state === NPCState.IDLE || npc.state === NPCState.PATROL || npc.state === NPCState.CHATTER || npc.state === NPCState.RETURN;
}

/** Shared escalation check for calm and searching states. Returns true if the state changed. */
function escalate(npc: NPC, w: NPCWorld): boolean {
  if (npc.awareness >= TUNING.confrontAt && w.canEscalate(npc)) {
    endChatter(npc);
    npc.setState(NPCState.CONFRONT);
    npc.ignoreTimer = 0;
    npc.lastConfrontDist = Infinity;
    npc.say(pick(STATE_BARKS.confront), 2);
    w.onConfront(npc);
    return true;
  }
  if (calm(npc) && npc.awareness >= TUNING.suspiciousAt) {
    endChatter(npc);
    npc.setState(NPCState.SUSPICIOUS);
    npc.say(pick(STATE_BARKS.suspicious), 1.6);
    if (npc.def.id === 'rohit' && npc.snitchCooldown <= 0) {
      npc.snitchCooldown = TUNING.snitchCooldown;
      w.onSnitch(npc);
    }
    return true;
  }
  return false;
}

function endChatter(npc: NPC): void {
  const partner = npc.chatPartner;
  npc.chatPartner = null;
  if (partner && partner.chatPartner === npc) {
    partner.chatPartner = null;
    if (partner.state === NPCState.CHATTER) partner.setState(partner.patrol.length ? NPCState.PATROL : NPCState.RETURN);
  }
}

export function updateNPC(npc: NPC, dt: number, w: NPCWorld): void {
  perceive(npc, dt, w);
  npc.snitchCooldown -= dt;
  npc.chatterCooldown -= dt;
  const walk = npc.def.walkSpeed;
  const p = w.player;
  const distToPlayer = Math.hypot(p.x - npc.x, p.z - npc.z);

  switch (npc.state) {
    case NPCState.IDLE: {
      if (escalate(npc, w)) break;
      npc.stopMoving();
      if (w.time < npc.distractedUntil) {
        // On the phone: turned sideways from what they normally watch, head down.
        npc.turnTo(npc.home.yaw - Math.PI / 2, dt);
        npc.lookAround(w.time, 0.15);
      } else {
        const dHome = Math.hypot(npc.home.x - npc.x, npc.home.z - npc.z);
        if (dHome > 0.3) { npc.setState(NPCState.RETURN); break; }
        npc.turnTo(npc.home.yaw, dt);
        npc.lookAround(w.time, 0.75);
      }
      ambientBark(npc, dt, distToPlayer);
      break;
    }
    case NPCState.PATROL: {
      if (escalate(npc, w)) break;
      if (!npc.patrol.length) { npc.setState(NPCState.IDLE); break; }
      if (npc.held) { npc.stopMoving(); npc.lookAround(w.time); break; }
      if (npc.waitTimer > 0) {
        npc.waitTimer -= dt;
        npc.stopMoving();
        npc.lookAround(w.time, 0.9);
      } else {
        const target = npc.patrol[npc.patrolIdx];
        if (npc.moveTo(target, walk, dt, w.nav)) {
          npc.patrolIdx = (npc.patrolIdx + 1) % npc.patrol.length;
          npc.waitTimer = TUNING.patrolPauseMin + Math.random() * (TUNING.patrolPauseMax - TUNING.patrolPauseMin);
        } else {
          npc.lookAround(w.time, 0.25);
        }
      }
      tryStartChatter(npc, w);
      ambientBark(npc, dt, distToPlayer);
      break;
    }
    case NPCState.CHATTER: {
      if (escalate(npc, w)) break;
      const partner = npc.chatPartner;
      if (!partner || partner.state !== NPCState.CHATTER) {
        npc.chatPartner = null;
        npc.setState(npc.patrol.length ? NPCState.PATROL : NPCState.RETURN);
        break;
      }
      npc.stopMoving();
      npc.faceToward(partner.x, partner.z, dt);
      // Speaker 0 opens, speaker 1 answers a beat later.
      if (!npc.chatSaid && npc.chatPair && npc.stateTime > (npc.chatLine === 0 ? 0.3 : 2.8)) {
        npc.chatSaid = true;
        if (distToPlayer < TUNING.barkHearRange) npc.say(npc.chatPair[npc.chatLine], 2.4);
      }
      if (npc.stateTime > TUNING.chatterTime) {
        npc.chatPartner = null;
        npc.setState(npc.patrol.length ? NPCState.PATROL : NPCState.RETURN);
      }
      break;
    }
    case NPCState.SUSPICIOUS: {
      if (escalate(npc, w)) break;
      if (npc.awareness < TUNING.suspiciousGiveUpBelow) {
        npc.say(pick(STATE_BARKS.giveUp), 1.8);
        npc.setState(NPCState.RETURN);
        break;
      }
      const target = npc.lastSeen ?? npc.lastHeard;
      if (!target) { npc.setState(NPCState.RETURN); break; }
      if (npc.seeing) {
        // Stop and stare while it sinks in; walking toward you comes after.
        npc.stopMoving();
        npc.faceToward(p.x, p.z, dt);
        npc.glanceAt(p.x, p.z);
      } else if (npc.moveTo(target, walk * TUNING.suspiciousSpeedMul, dt, w.nav)) {
        startInvestigate(npc, target, TUNING.investigateTime, false);
      }
      break;
    }
    case NPCState.INVESTIGATE: {
      if (!npc.investigateIsDistraction && escalate(npc, w)) break;
      if (!npc.investigateIsDistraction && npc.seeing && npc.awareness >= TUNING.suspiciousAt) {
        npc.setState(NPCState.SUSPICIOUS);
        break;
      }
      const target = npc.investigateTarget;
      if (target && Math.hypot(target.x - npc.x, target.z - npc.z) > TUNING.investigateArrive && npc.stateTime < TUNING.investigateMaxWalk) {
        npc.moveTo(target, walk, dt, w.nav);
        break;
      }
      npc.stopMoving();
      if (target && npc.investigateIsDistraction) npc.faceToward(target.x, target.z, dt);
      npc.lookAround(w.time * 1.6, 1.1);
      npc.investigateTimer -= dt;
      if (npc.investigateTimer <= 0) {
        if (!npc.investigateIsDistraction && Math.random() < 0.6) npc.say(pick(STATE_BARKS.investigate), 2);
        npc.investigateIsDistraction = false;
        npc.setState(NPCState.RETURN);
      }
      break;
    }
    case NPCState.CONFRONT: {
      npc.faceToward(p.x, p.z, dt);
      if (distToPlayer <= TUNING.confrontReach) {
        npc.stopMoving();
        w.startEncounter(npc);
        break;
      }
      // Walking away from someone calling your name counts as running, just slower.
      if (p.speed > 0.5 && distToPlayer > npc.lastConfrontDist + 0.001) npc.ignoreTimer += dt;
      npc.lastConfrontDist = distToPlayer;
      if ((p.sprinting && distToPlayer > TUNING.chaseTriggerDist) || npc.ignoreTimer > TUNING.ignoreBeforeChase) {
        npc.setState(NPCState.CHASE);
        npc.say(pick(npc.ignoreTimer > TUNING.ignoreBeforeChase ? STATE_BARKS.ignored : STATE_BARKS.chase), 2.2);
        break;
      }
      if (npc.stateTime > TUNING.confrontTimeout && !npc.seeing) {
        npc.awareness = 0.6;
        startInvestigate(npc, npc.lastSeen ?? { x: p.x, z: p.z }, TUNING.investigateTime, false);
        break;
      }
      npc.moveTo({ x: p.x, z: p.z }, walk * TUNING.confrontSpeedMul, dt, w.nav, true);
      break;
    }
    case NPCState.CHASE: {
      if (distToPlayer <= TUNING.catchReach) {
        npc.stopMoving();
        w.caught(npc, 'chase');
        break;
      }
      if (!npc.seeing && w.time - npc.lastStimulus > TUNING.chaseGiveUp) {
        npc.say(pick(STATE_BARKS.giveUp), 2);
        npc.awareness = 0.7;
        startInvestigate(npc, npc.lastSeen ?? { x: p.x, z: p.z }, TUNING.investigateTime, false);
        break;
      }
      npc.awareness = 1;
      npc.moveTo(npc.seeing ? { x: p.x, z: p.z } : (npc.lastSeen ?? { x: p.x, z: p.z }), TUNING.chaseSpeed, dt, w.nav, true);
      break;
    }
    case NPCState.ESCORT: {
      // Walk alongside the player, a step to their right, watching them.
      const side = { x: p.x + Math.cos(p.yaw) * TUNING.escortSideOffset, z: p.z - Math.sin(p.yaw) * TUNING.escortSideOffset };
      const target = w.nav.isWalkable(...w.nav.worldToCell(side.x, side.z)) ? side : { x: p.x, z: p.z };
      if (Math.hypot(target.x - npc.x, target.z - npc.z) > 0.6) {
        npc.moveTo(target, Math.max(walk * 1.3, p.speed * 1.05), dt, w.nav, true);
      } else npc.stopMoving();
      npc.glanceAt(p.x, p.z);
      npc.probeTimer -= dt;
      break;
    }
    case NPCState.RETURN: {
      if (escalate(npc, w)) break;
      const target = npc.patrol.length ? npc.patrol[npc.patrolIdx] : npc.home;
      if (npc.moveTo(target, walk, dt, w.nav)) {
        npc.setState(npc.patrol.length ? NPCState.PATROL : NPCState.IDLE);
      }
      break;
    }
  }
}

export function startInvestigate(npc: NPC, target: Vec2, seconds: number, distraction: boolean): void {
  endChatter(npc);
  npc.setState(NPCState.INVESTIGATE);
  npc.investigateTarget = { ...target };
  npc.investigateTimer = seconds;
  npc.investigateIsDistraction = distraction;
}

export function startDistraction(npc: NPC, target: Vec2): void {
  if (npc.state === NPCState.CONFRONT || npc.state === NPCState.CHASE || npc.state === NPCState.ESCORT) return;
  startInvestigate(npc, target, TUNING.distractionTime, true);
}

function ambientBark(npc: NPC, dt: number, distToPlayer: number): void {
  npc.barkTimer -= dt;
  if (npc.barkTimer > 0) return;
  npc.barkTimer = TUNING.barkMin + Math.random() * (TUNING.barkMax - TUNING.barkMin);
  if (distToPlayer > TUNING.barkHearRange) return;
  let i = Math.floor(Math.random() * npc.def.barks.length);
  if (i === npc.lastBark) i = (i + 1) % npc.def.barks.length;
  npc.lastBark = i;
  npc.say(npc.def.barks[i]);
}

function tryStartChatter(npc: NPC, w: NPCWorld): void {
  if (npc.chatterCooldown > 0 || npc.held) return;
  for (const other of w.npcs) {
    if (other === npc || other.chatterCooldown > 0) continue;
    if (other.state !== NPCState.IDLE && other.state !== NPCState.PATROL) continue;
    if (Math.hypot(other.x - npc.x, other.z - npc.z) > TUNING.chatterRange) continue;
    const pair = pick(CHATTER);
    for (const [a, line] of [[npc, 0], [other, 1]] as Array<[NPC, number]>) {
      a.setState(NPCState.CHATTER);
      a.chatterCooldown = TUNING.chatterCooldown;
      a.chatPair = pair;
      a.chatLine = line;
      a.chatSaid = false;
      a.stopMoving();
    }
    npc.chatPartner = other;
    other.chatPartner = npc;
    return;
  }
}

export const NPC_TUNING = TUNING;
