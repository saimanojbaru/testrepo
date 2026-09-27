import * as THREE from 'three';
import { Clock } from './Clock';
import { Input } from './Input';
import { events } from './Events';
import { GameState, StateMachine } from './GameState';
import { AudioBus } from '../audio/AudioBus';
import { buildLevel, type LevelRuntime } from '../world/LevelBuilder';
import { setupLighting } from '../world/Lighting';
import { PlayerController } from '../player/PlayerController';
import { PlayerNoise } from '../player/PlayerNoise';
import { Interactor } from '../player/Interactor';
import { NPC } from '../ai/NPC';
import { NPCState, effectiveVision, startDistraction, updateNPC, type NPCWorld } from '../ai/NPCStates';
import { Hearing } from '../ai/Hearing';
import { Director } from '../ai/Director';
import { OfficeMemory } from '../dialogue/OfficeMemory';
import { ExcuseRegistry, type EncounterFacts } from '../dialogue/ExcuseRegistry';
import { DialogueSystem, type EncounterResult } from '../dialogue/DialogueSystem';
import { DialogueUI } from '../dialogue/DialogueUI';
import { HUD } from '../ui/HUD';
import { Vignette } from '../ui/Vignette';
import { ResultsScreen, type ExcuseLog, type RunSummary } from '../ui/ResultsScreen';
import { MainMenu, loadSettings, type Settings } from '../ui/MainMenu';
import { TouchControls, isTouchDevice } from '../ui/TouchControls';
import { getNpcDef, getNpcLook } from '../data/npcs';
import { STATE_BARKS, pick } from '../data/dialogueLines';
import type { EncounterContext, Ending, ExcuseDef, LevelData, NPCDef, ScriptedBeat } from '../data/types';
import { level1 } from '../data/levels/level1_lunch';
import { level2 } from '../data/levels/level2_crush';
import { level3 } from '../data/levels/level3_casserole';

// Bootstraps renderer, scene, camera and every system, and runs the top-level
// state machine: MENU -> (intro) -> PLAYING <-> DIALOGUE -> ESCAPED | CAUGHT.

const TUNING = {
  fov: 72,
  near: 0.05,
  far: 140,
  maxPixelRatio: 2,
  /** Phones get a lower render resolution; fill rate is the bottleneck there. */
  maxPixelRatioTouch: 1.5,
  nearExitDist: 7,
  nearKitchenDist: 6,
  nearCopierDist: 4,
  escortPenaltySeconds: 45,
  probeSeconds: 8,
  probeViolationDist: 2.5,
  /** How far away someone can be and still react out loud to the all-hands. */
  allHandsBarkRange: 18,
  /** Where the escorting NPC is left standing after walking you back. */
  escortDropOffset: 1.2,
  passGraceSeconds: 12,
  /** After any conversation nobody can start a new one for this long. */
  encounterCooldown: 3,
  maxCharisma: 2,
  hydrateSeconds: 4,
  hydrateReach: 2.2,
  jamRadius: 20,
  jamSeconds: 10,
  jamHeat: 0.25,
  elevatorCallDelay: 10,
  elevatorOpenSeconds: 10,
  allHandsFovMul: 2,
  npcBodyRadius: 0.32,
  talkReach: 2.4,
  starSlack: 1.5,
  dialogueVignette: 0.65,
  dialogueCamLerp: 4,
};

export const LEVELS: LevelData[] = [level1, level2, level3];

type CaughtReason = NonNullable<RunSummary['caughtReason']>;

interface Run {
  data: LevelData;
  level: LevelRuntime;
  player: PlayerController;
  noise: PlayerNoise;
  hearing: Hearing;
  director: Director;
  world: NPCWorld;
  npcs: NPC[];
  time: number;
  beatTime: number;
  firedBeats: Set<ScriptedBeat>;
  charisma: number;
  log: ExcuseLog[];
  dialogues: number;
  bossPassedCorporate: boolean;
  absurdSuccesses: ExcuseDef[];
  favors: number;
  deadline: number | null;
  allHandsUntil: number;
  hydratingUntil: number;
  probe: { npc: NPC; startExitDist: number } | null;
  elevatorCallTimer: number;
  encounterCooldownUntil: number;
  fading: boolean;
  repBefore: string;
  ended: boolean;
}

export class Game {
  readonly renderer: THREE.WebGLRenderer;
  readonly scene = new THREE.Scene();
  readonly camera: THREE.PerspectiveCamera;
  readonly input: Input;
  readonly state = new StateMachine();
  readonly audio = new AudioBus();
  readonly memory = new OfficeMemory();
  readonly registry: ExcuseRegistry;
  readonly settings: Settings;
  run: Run | null = null;

  private clock = new Clock();
  private interactor = new Interactor();
  private dialogue: DialogueSystem;
  private hud: HUD;
  private vignette: Vignette;
  private results: ResultsScreen;
  private menu: MainMenu;
  private introPending = false;
  private readonly debug: boolean;
  private readonly isTouch = isTouchDevice();
  private touch: TouchControls | null = null;

  constructor(private readonly container: HTMLElement) {
    this.debug = new URLSearchParams(location.search).has('debug');
    this.settings = loadSettings();
    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, this.isTouch ? TUNING.maxPixelRatioTouch : TUNING.maxPixelRatio));
    container.append(this.renderer.domElement);
    this.camera = new THREE.PerspectiveCamera(TUNING.fov, 1, TUNING.near, TUNING.far);
    setupLighting(this.scene);
    this.resize();
    window.addEventListener('resize', () => this.resize());

    const ui = document.createElement('div');
    ui.className = 'ui-root';
    container.append(ui);
    this.vignette = new Vignette(ui);
    this.hud = new HUD(ui);
    const dui = new DialogueUI(ui);
    this.results = new ResultsScreen(ui);

    this.input = new Input(this.renderer.domElement);
    dui.onTap = () => this.input.tap();
    if (this.isTouch) {
      this.touch = new TouchControls(ui, this.input, () => { if (this.state.is(GameState.PLAYING)) this.pause(); });
      this.state.onChange((next) => this.touch?.show(next === GameState.PLAYING));
    }
    this.applySettings(this.settings);
    this.registry = new ExcuseRegistry(this.memory);
    this.dialogue = new DialogueSystem(dui, this.registry, this.memory, {
      voice: (p) => this.audio.voice(p),
      tick: (u) => this.audio.timerTick(u),
      select: () => this.audio.uiSelect(),
    }, (r) => this.onEncounterResolved(r));

    this.menu = new MainMenu(ui, LEVELS, this.memory, this.settings, {
      onPlay: (id) => this.startLevel(id),
      onReset: () => this.memory.reset(),
      onSettings: (s) => this.applySettings(s),
      onGesture: () => this.audio.unlock(),
    });

    this.input.onLockChange((locked) => this.onLockChange(locked));
    this.renderer.domElement.addEventListener('click', () => {
      if (this.state.is(GameState.PLAYING, GameState.DIALOGUE) && !this.input.locked) this.grabPointer();
    });

    if (this.debug) (window as unknown as { __clockout: Game }).__clockout = this;
    this.showMenu();
    requestAnimationFrame((t) => this.frame(t));
  }

  // -------------------------------------------------------------------------
  // Flow
  // -------------------------------------------------------------------------

  showMenu(): void {
    this.teardownLevel();
    this.state.set(GameState.MENU);
    this.input.releaseLock();
    this.hud.show(false);
    this.hud.hidePause();
    this.hud.hideIntro();
    this.results.hide();
    this.vignette.reset();
    this.audio.ambienceOn(false);
    this.menu.show();
  }

  startLevel(id: string): void {
    const data = LEVELS.find((l) => l.id === id);
    if (!data) throw new Error(`Unknown level ${id}`);
    this.audio.unlock();
    this.teardownLevel();
    this.menu.hide();
    this.results.hide();
    this.hud.hidePause();

    const level = buildLevel(data);
    this.scene.add(level.root);
    const player = new PlayerController(level.colliders);
    player.reset(level.spawn.x, level.spawn.z, level.spawn.yaw);
    const noise = new PlayerNoise((kind) => this.audio.footstep(kind));
    player.onBump = () => {
      this.audio.bump();
      noise.oneShot('bump', player.x, player.z);
    };

    const npcs: NPC[] = [];
    const world: NPCWorld = {
      time: 0, player, colliders: level.colliders, nav: level.nav, npcs, fovMul: 1, playerHydrating: false,
      canEscalate: (npc) => this.canEscalate(npc),
      startEncounter: (npc) => this.startEncounter(npc, {}),
      caught: (npc, reason) => this.endCaught(npc.def, reason),
      onConfront: () => this.audio.sting(),
      onSnitch: (npc) => this.onSnitch(npc),
    };
    const hearing = new Hearing(() => npcs, () => world.time, (npc, e) => {
      startDistraction(npc, { x: e.x, z: e.z });
      if (Math.random() < 0.7) npc.say(pick(STATE_BARKS.jam), 2.2);
    });
    const director = new Director(level, data.directorBudget, (defId, cell, patrol) => this.spawnNpc(defId, cell, patrol));
    const run: Run = {
      data, level, player, noise, hearing, director, world, npcs,
      time: 0, beatTime: 0, firedBeats: new Set(), charisma: 0, log: [], dialogues: 0,
      bossPassedCorporate: false, absurdSuccesses: [], favors: data.favors ?? 0, deadline: null,
      allHandsUntil: -1, hydratingUntil: -1, probe: null, elevatorCallTimer: -1, encounterCooldownUntil: 0,
      fading: false, repBefore: this.memory.reputationLabel(), ended: false,
    };
    this.run = run;

    for (const p of data.npcs) this.spawnNpc(p.def, p.cell, p.patrol, p.facing);
    this.setupInteractables(run);

    let extra = '';
    if (data.forceUsedExcuses?.length) {
      for (const id of data.forceUsedExcuses) {
        this.memory.ensureUsed(id, run.npcs.map((n) => n.def.id));
      }
      extra = 'Office Memory: everyone on this floor has heard about the casserole. It will be in your hand, marked [USED]. Do not use it.';
    }
    if (this.memory.timesCaught > 0) {
      extra += `${extra ? ' ' : ''}You have been caught ${this.memory.timesCaught} time${this.memory.timesCaught > 1 ? 's' : ''}. Every excuse costs a little more.`;
    }

    this.hud.setObjective(data.name, data.goalText);
    this.hud.clearToasts();
    this.hud.show(true);
    this.vignette.reset();
    this.audio.ambienceOn(true);
    player.applyCamera(this.camera);
    this.introPending = true;
    this.state.set(GameState.PAUSED);
    this.state.resumeTo = GameState.PLAYING;
    this.hud.showIntro(data.name, data.goalText, data.intro, extra, () => {
      this.introPending = false;
      this.audio.unlock();
      this.grabPointer();
      this.clock.reset();
      this.state.set(GameState.PLAYING);
    });
  }

  private teardownLevel(): void {
    const run = this.run;
    if (!run) return;
    this.dialogue.abort();
    this.dialogueNpc = null;
    run.hearing.dispose();
    run.director.dispose();
    for (const n of run.npcs) {
      this.scene.remove(n.group, n.cone);
      n.dispose();
    }
    this.scene.remove(run.level.root);
    run.level.dispose();
    this.interactor.clear();
    events.clear();
    this.run = null;
  }

  private onLockChange(locked: boolean): void {
    if (this.debug) return;
    if (!locked && this.state.is(GameState.PLAYING, GameState.DIALOGUE)) this.pause();
    else if (locked && this.state.is(GameState.PAUSED) && !this.introPending) this.resume();
  }

  private pause(): void {
    this.state.set(GameState.PAUSED);
    this.audio.duck(true);
    this.hud.showPause(() => {
      if (this.isTouch) { this.resume(); return; }
      this.input.requestLock();
      // If the browser refuses pointer lock we still resume; drag-to-look is the fallback.
      window.setTimeout(() => { if (this.state.is(GameState.PAUSED)) this.resume(); }, 300);
    }, () => this.showMenu());
  }

  /** Pointer lock is a desktop thing; on touch devices the look comes from drags. */
  private grabPointer(): void {
    if (!this.isTouch) this.input.requestLock();
  }

  private resume(): void {
    this.hud.hidePause();
    this.clock.reset();
    this.state.set(this.state.resumeTo === GameState.PAUSED ? GameState.PLAYING : this.state.resumeTo);
    if (!this.dialogue.active) this.audio.duck(false);
  }

  // -------------------------------------------------------------------------
  // Loop
  // -------------------------------------------------------------------------

  private frame(now: number): void {
    requestAnimationFrame((t) => this.frame(t));
    const steps = this.clock.tick(now, (dt) => this.fixedUpdate(dt));
    const run = this.run;
    const look = this.input.consumeLook();
    if (run) {
      if (this.state.is(GameState.PLAYING) && !run.fading) run.player.look(look.dx, look.dy);
      if (this.state.is(GameState.DIALOGUE) && this.dialogueNpc) this.faceCameraToward(this.dialogueNpc, 1 / 60);
      run.player.applyCamera(this.camera);
    }
    this.renderer.render(this.scene, this.camera);
    if (steps > 0) this.input.endFrame();
  }

  private dialogueNpc: NPC | null = null;

  private fixedUpdate(dt: number): void {
    const run = this.run;
    if (!run) return;
    if (!this.input.locked && this.input.wasPressed('Escape') && this.state.is(GameState.PLAYING)) this.pause();
    if (this.state.is(GameState.PLAYING) && !run.fading) this.updatePlaying(run, dt);
    else if (this.state.is(GameState.DIALOGUE)) {
      this.dialogue.update(dt, this.input);
      this.animateWorld(run, dt);
    }
    if (this.state.is(GameState.PLAYING, GameState.DIALOGUE)) {
      const target = this.state.is(GameState.DIALOGUE) ? TUNING.dialogueVignette : Math.max(0, ...run.npcs.map((n) => n.awareness));
      const beat = this.vignette.update(dt, target);
      if (beat > 0) this.audio.heartbeat(beat);
    }
  }

  private updatePlaying(run: Run, dt: number): void {
    run.time += dt;
    run.beatTime += dt;
    const w = run.world;
    w.time = run.beatTime;
    for (const beat of run.data.scriptedBeats) {
      if (!run.firedBeats.has(beat) && beat.atTime <= run.beatTime) {
        run.firedBeats.add(beat);
        this.fireBeat(run, beat);
      }
    }

    const p = run.player;
    p.update(dt, this.input.move(), this.input.crouchToggle);
    this.pushPlayerFromNpcs(run);
    run.noise.update(dt, p);
    p.applyCamera(this.camera);
    this.interactor.update(this.camera, run.level.colliders);
    if (this.input.interact) this.interactor.tryUse();

    // Elevator call arrives after a delay.
    if (run.elevatorCallTimer > 0) {
      run.elevatorCallTimer -= dt;
      if (run.elevatorCallTimer <= 0) this.openElevators(run, TUNING.elevatorOpenSeconds);
    }

    w.fovMul = run.beatTime < run.allHandsUntil ? TUNING.allHandsFovMul : 1;
    w.playerHydrating = run.beatTime < run.hydratingUntil && run.level.coolers.some((c) => Math.hypot(c.x - p.x, c.z - p.z) < TUNING.hydrateReach);
    for (const npc of [...run.npcs]) {
      if (!this.state.is(GameState.PLAYING)) break;
      updateNPC(npc, dt, w);
    }
    this.animateWorld(run, dt);
    run.director.update(dt, p, run.npcs);
    if (!this.state.is(GameState.PLAYING)) return;

    this.updateProbe(run);
    if (!this.state.is(GameState.PLAYING)) return;

    // The 1:15 sync invite.
    if (run.deadline !== null && this.clockMinutes(run) >= run.deadline) {
      const boss = run.npcs.find((n) => n.def.archetype === 'boss');
      this.endCaught(boss?.def ?? run.npcs[0].def, 'deadline');
      return;
    }

    this.checkExit(run);
    this.updateHud(run);
  }

  /** Animation and props that keep moving even while a conversation is on screen. */
  private animateWorld(run: Run, dt: number): void {
    const p = run.player;
    for (const npc of run.npcs) {
      const v = effectiveVision(npc, run.world);
      npc.animate(dt, v.fov, v.range, this.settings.cones, Math.hypot(npc.x - p.x, npc.z - p.z));
    }
    for (const e of run.level.elevators) e.update(dt);
    for (const d of run.level.doors) d.update(dt);
    const t = run.beatTime;
    for (const c of run.level.copiers) {
      const jammed = t < c.jammedUntil;
      (c.panel.material as THREE.MeshBasicMaterial).color.setHex(jammed ? (Math.floor(t * 6) % 2 ? 0xff3b2f : 0x401010) : 0x4fdc7a);
    }
    const allHands = t < run.allHandsUntil;
    run.level.screenMaterial.color.setHex(allHands ? (Math.floor(t * 4) % 2 ? 0xff6b3d : 0xf2f2f2) : 0x8fb4d8);
    for (const el of run.level.elevators) {
      el.buttonMat.color.setHex(run.elevatorCallTimer > 0 ? 0xffc34d : 0x777777);
    }
  }

  private faceCameraToward(npc: NPC, dt: number): void {
    const p = this.run!.player;
    const target = Math.atan2(-(npc.x - p.x), -(npc.z - p.z));
    let d = target - p.yaw;
    while (d > Math.PI) d -= Math.PI * 2;
    while (d < -Math.PI) d += Math.PI * 2;
    const k = Math.min(1, dt * TUNING.dialogueCamLerp);
    p.yaw += d * k;
    const dist = Math.hypot(npc.x - p.x, npc.z - p.z);
    const pitchTarget = Math.atan2(npc.eyeY - 0.05 - p.eyeHeight, Math.max(0.5, dist));
    p.pitch += (pitchTarget - p.pitch) * k;
  }

  // -------------------------------------------------------------------------
  // NPCs
  // -------------------------------------------------------------------------

  spawnNpc(defId: string, cell: [number, number], patrol?: [number, number][], facing?: number): NPC {
    const run = this.run!;
    const nav = run.level.nav;
    const def = getNpcDef(defId);
    const pos = nav.cellCenter(...cell);
    // Level data faces in compass degrees (0 north, 90 east); our yaw turns the other way.
    const yaw = facing !== undefined ? -THREE.MathUtils.degToRad(facing) : 0;
    const npc = new NPC(def, getNpcLook(defId), pos.x, pos.z, yaw);
    if (patrol?.length) {
      npc.setPatrol(patrol.map((c) => nav.cellCenter(...c)));
      npc.setState(NPCState.PATROL);
    }
    npc.onSay = (n, text) => {
      const pl = this.run?.player;
      if (pl) this.audio.bark(text, n.look.voice, Math.hypot(n.x - pl.x, n.z - pl.z));
    };
    this.scene.add(npc.group, npc.cone);
    run.npcs.push(npc);
    this.interactor.items.push({
      object: npc.group,
      label: () => {
        const r = this.run;
        if (!r || !this.state.is(GameState.PLAYING)) return null;
        if (npc.state === NPCState.ESCORT || npc.state === NPCState.CHASE || !this.canEscalate(npc)) return null;
        if (Math.hypot(npc.x - r.player.x, npc.z - r.player.z) > TUNING.talkReach) return null;
        return `[E] Talk to ${def.name.split(' ')[0]}`;
      },
      use: () => this.startEncounter(npc, { voluntary: true }),
    });
    return npc;
  }

  private pushPlayerFromNpcs(run: Run): void {
    const p = run.player;
    for (const n of run.npcs) {
      const dx = p.x - n.x, dz = p.z - n.z;
      const d = Math.hypot(dx, dz);
      const min = p.radius + TUNING.npcBodyRadius;
      if (d >= min || d < 1e-4) continue;
      p.x = n.x + (dx / d) * min;
      p.z = n.z + (dz / d) * min;
    }
    const res = run.level.colliders.resolveCircle(p.x, p.z, p.radius, 0.05, p.height);
    p.x = res.x;
    p.z = res.z;
  }

  private canEscalate(npc: NPC): boolean {
    const run = this.run;
    if (!run || !this.state.is(GameState.PLAYING)) return false;
    if (run.world.time < run.encounterCooldownUntil) return false;
    if (run.probe && run.probe.npc !== npc) return false;
    return !run.npcs.some((o) => o !== npc && (o.state === NPCState.CONFRONT || o.state === NPCState.CHASE || o.state === NPCState.ESCORT));
  }

  private onSnitch(tom: NPC): void {
    const run = this.run!;
    this.hud.toast(`${tom.def.name.split(' ')[0]}: "${pick(STATE_BARKS.tomSnitch)}"`);
    tom.say(pick(STATE_BARKS.tomSnitch), 2.5);
    for (const n of run.npcs) {
      if (n === tom || n.state === NPCState.ESCORT) continue;
      n.awareness = Math.max(n.awareness, 0.5);
      n.lastSeen = { x: run.player.x, z: run.player.z };
      n.lastStimulus = run.world.time;
    }
  }

  // -------------------------------------------------------------------------
  // Dialogue
  // -------------------------------------------------------------------------

  private startEncounter(npc: NPC, opts: { voluntary?: boolean; probeViolation?: boolean }): void {
    const run = this.run;
    if (!run || !this.state.is(GameState.PLAYING)) return;
    const p = run.player;
    const ctx = new Set<EncounterContext>();
    if (npc.sawCrouch || (p.crouched && npc.seeing)) ctx.add('crouching');
    if (npc.sawSprint) ctx.add('sprinting');
    if (Math.hypot(p.x - run.level.exitCenter.x, p.z - run.level.exitCenter.z) < TUNING.nearExitDist) ctx.add('near_exit');
    if (run.level.kitchens.some((k) => Math.hypot(k.x - p.x, k.z - p.z) < TUNING.nearKitchenDist)) ctx.add('near_kitchen');
    if (run.level.copiers.some((c) => Math.hypot(c.x - p.x, c.z - p.z) < TUNING.nearCopierDist)) ctx.add('near_copier');
    if (this.memory.timesCaught > 0) ctx.add('caught_before');
    if (this.memory.heardAnything(npc.def.id)) ctx.add('heard_before');
    if (opts.voluntary) ctx.add('voluntary');
    if (opts.probeViolation) ctx.add('probe_violation');
    const facts: EncounterFacts = { npc: npc.def, contexts: ctx, charisma: run.charisma, priorStops: npc.encounters, forced: run.data.forceUsedExcuses, levelModifier: run.data.suspicionModifier };

    for (const o of run.npcs) if (o !== npc && o.state === NPCState.CONFRONT) o.setState(NPCState.RETURN);
    npc.setState(NPCState.CONFRONT);
    npc.awareness = 1;
    npc.stopMoving();
    npc.faceToward(p.x, p.z, 10);
    run.probe = null;
    this.dialogueNpc = npc;
    this.state.set(GameState.DIALOGUE);
    this.hud.setInteract(null);
    this.hud.setStatus('');
    this.audio.duck(true);
    this.input.resetDialogueNav();
    this.dialogue.start(npc, facts);
  }

  private onEncounterResolved(r: EncounterResult): void {
    const run = this.run;
    if (!run) return;
    const npc = r.npc;
    this.memory.recordUse(r.excuse.id, npc.def.id);
    run.dialogues++;
    npc.encounters++;
    run.log.push({
      excuse: r.excuse, npc: npc.def, outcome: r.outcome, blurted: r.blurted,
      followUp: r.pivot ? r.excuse.followUp ?? null : null,
      pivotText: r.pivot && r.pivot !== 'timeout' ? (r.pivot === 'good' ? r.excuse.pivotLine ?? null : '(a deflection)') : null,
      atClock: formatClock(this.clockMinutes(run)),
    });
    this.dialogueNpc = null;
    this.audio.duck(false);
    npc.sawCrouch = npc.sawSprint = false;
    run.encounterCooldownUntil = run.world.time + TUNING.encounterCooldown;

    // Tom tells everyone. Whatever you told him, Priya hears a version of it.
    if (npc.def.id === 'tom' && r.outcome !== 'CAUGHT') this.onSnitch(npc);

    switch (r.outcome) {
      case 'PASSED': {
        run.charisma = Math.min(TUNING.maxCharisma, run.charisma + 1);
        npc.awareness = 0;
        npc.graceUntil = run.world.time + TUNING.passGraceSeconds;
        npc.setState(NPCState.RETURN);
        if (npc.def.archetype === 'boss' && r.excuse.category === 'corporate') run.bossPassedCorporate = true;
        if (r.excuse.category === 'absurd' || r.excuse.tags.includes('legend')) run.absurdSuccesses.push(r.excuse);
        this.state.set(GameState.PLAYING);
        break;
      }
      case 'PROBED': {
        npc.awareness = 0;
        npc.setState(NPCState.ESCORT);
        npc.probeTimer = TUNING.probeSeconds;
        run.probe = { npc, startExitDist: this.exitDist(run) };
        this.hud.toast(`${npc.def.name.split(' ')[0]} is walking with you for a bit. Act natural. Don't head for the exit.`, 4);
        this.state.set(GameState.PLAYING);
        break;
      }
      case 'ESCORTED': {
        this.state.set(GameState.PLAYING);
        run.fading = true;
        const first = npc.def.name.split(' ')[0];
        void this.hud.fadeThrough(`${first} walks you back to your desk. The long way. (−${TUNING.escortPenaltySeconds} seconds)`, () => {
          run.time += TUNING.escortPenaltySeconds;
          const s = run.level.spawn;
          run.player.reset(s.x, s.z, s.yaw);
          // They walked you here; now they walk back.
          const [c, rr] = run.level.nav.nearestWalkable(...run.level.nav.worldToCell(s.x + TUNING.escortDropOffset, s.z));
          const spot = run.level.nav.cellCenter(c, rr);
          npc.x = spot.x;
          npc.z = spot.z;
          npc.awareness = 0;
          npc.graceUntil = run.world.time + TUNING.passGraceSeconds;
          npc.setState(NPCState.RETURN);
          for (const o of run.npcs) o.awareness = Math.min(o.awareness, 0.2);
        }).then(() => { run.fading = false; });
        break;
      }
      case 'CAUGHT':
        this.endCaught(npc.def, 'dialogue');
        break;
    }
  }

  private updateProbe(run: Run): void {
    const probe = run.probe;
    if (!probe) return;
    const npc = probe.npc;
    if (npc.state !== NPCState.ESCORT) { run.probe = null; return; }
    if (this.exitDist(run) < probe.startExitDist - TUNING.probeViolationDist || run.level.isExit(run.player.x, run.player.z)) {
      run.probe = null;
      this.audio.sting();
      this.startEncounter(npc, { probeViolation: true });
      return;
    }
    if (npc.probeTimer <= 0) {
      run.probe = null;
      npc.say(pick(STATE_BARKS.probeDone), 2);
      npc.graceUntil = run.world.time + TUNING.passGraceSeconds;
      npc.setState(NPCState.RETURN);
    }
  }

  // -------------------------------------------------------------------------
  // World interaction + beats
  // -------------------------------------------------------------------------

  private setupInteractables(run: Run): void {
    const { level } = run;
    for (const c of level.copiers) {
      this.interactor.items.push({
        object: c.group,
        highlight: c.materials,
        label: () => {
          if (!run.data.favors) return null;
          if (run.beatTime < c.jammedUntil) return 'Copier is screaming';
          return run.favors > 0 ? `[E] Jam the copier (uses 1 of ${run.favors} favors)` : 'No favors left';
        },
        use: () => {
          if (!run.data.favors || run.favors <= 0 || run.beatTime < c.jammedUntil) return;
          run.favors--;
          c.jammedUntil = run.beatTime + TUNING.jamSeconds;
          this.audio.copierJam();
          run.director.heat = Math.min(1, run.director.heat + TUNING.jamHeat);
          events.emit('noise', { x: c.x, z: c.z, radius: TUNING.jamRadius, kind: 'jam', fromPlayer: false });
          this.hud.toast('You jam the copier. It makes a noise like a dying printer god.');
        },
      });
    }
    for (const cool of level.coolers) {
      this.interactor.items.push({
        object: cool.group,
        highlight: cool.materials,
        label: () => (run.beatTime < run.hydratingUntil ? 'Hydrating…' : '[E] Hydrate (look busy)'),
        use: () => {
          if (run.beatTime < run.hydratingUntil) return;
          run.hydratingUntil = run.beatTime + TUNING.hydrateSeconds;
          this.audio.sip();
          this.hud.toast('Nobody suspects a person who is hydrating.');
        },
      });
    }
    for (const el of level.elevators) {
      this.interactor.items.push({
        object: el.group,
        label: () => {
          if (level.elevators.some((e) => e.isOpen)) return null;
          return run.elevatorCallTimer > 0 ? 'Elevator is on its way…' : '[E] Call elevator';
        },
        use: () => {
          if (run.elevatorCallTimer > 0 || level.elevators.some((e) => e.isOpen)) return;
          run.elevatorCallTimer = TUNING.elevatorCallDelay;
          this.audio.uiSelect();
          this.hud.toast('You press the button. The elevator considers it.');
        },
      });
    }
    level.doors.forEach((door) => {
      this.interactor.items.push({
        object: door.pivot,
        highlight: [door.material],
        label: () => (door.locked && !door.open ? 'Locked ("inventory")' : door.open ? '[E] Close door' : '[E] Open door (it creaks)'),
        use: () => {
          if (door.locked && !door.open) return;
          door.toggle();
          if (door.open) {
            this.audio.doorCreak();
            run.noise.oneShot('door', door.collider.minX, door.collider.minZ);
          }
        },
      });
    });
  }

  private fireBeat(run: Run, beat: ScriptedBeat): void {
    const pl = beat.payload ?? {};
    const npcById = (id: string) => run.npcs.find((n) => n.def.id === id);
    switch (beat.type) {
      case 'bark': npcById(pl.npc)?.say(pl.text); break;
      case 'spawn': {
        const npc = this.spawnNpc(pl.def, pl.cell, pl.patrol, pl.facing);
        if (pl.text) npc.say(pl.text);
        break;
      }
      case 'moveTo': {
        const npc = npcById(pl.npc);
        if (!npc) break;
        const nav = run.level.nav;
        if (pl.patrol) npc.setPatrol((pl.patrol as [number, number][]).map((c) => nav.cellCenter(...c)));
        else if (pl.cell) npc.home = { ...nav.cellCenter(...(pl.cell as [number, number])), yaw: npc.yaw };
        if (npc.state === NPCState.IDLE || npc.state === NPCState.RETURN) npc.setState(pl.patrol ? NPCState.PATROL : NPCState.RETURN);
        break;
      }
      case 'phoneRing': {
        const npc = npcById(pl.npc);
        if (!npc) break;
        this.audio.phoneRing();
        npc.distractedUntil = run.beatTime + (pl.duration ?? 8);
        window.setTimeout(() => { if (this.run === run) npc.say(pl.line ?? 'Hello? …Hello?', pl.duration ?? 8); }, 1400);
        this.hud.toast(`${npc.def.name.split(' ')[0]}'s phone is ringing. They turn away to answer.`);
        break;
      }
      case 'lockDoor': {
        const door = run.level.doors[pl.door ?? 0];
        if (!door) break;
        const p = run.player;
        const inDoorway = p.x > door.collider.minX - 0.6 && p.x < door.collider.maxX + 0.6 && p.z > door.collider.minZ - 0.6 && p.z < door.collider.maxZ + 0.6;
        if (door.open && !inDoorway) door.toggle();
        door.locked = !!pl.locked;
        if (pl.toast) this.hud.toast(pl.toast);
        break;
      }
      case 'calendar':
        this.audio.calendarPing();
        run.deadline = this.clockMinutes(run) + (pl.minutes ?? 15);
        this.hud.toast(`📅 ${pl.from ?? 'Calendar'}: "${pl.title ?? 'Sync'}" in ${pl.minutes ?? 15}:00. Accept / Accept / Accept`, 5);
        break;
      case 'elevator':
        if (pl.action === 'open') this.openElevators(run, pl.duration ?? TUNING.elevatorOpenSeconds);
        break;
      case 'allHands': {
        run.allHandsUntil = run.beatTime + (pl.duration ?? 5);
        this.audio.allHandsChime();
        this.hud.toast('ALL-HANDS REMINDER on every monitor. Every head turns. Everyone sees twice as much.', 5);
        for (const n of run.npcs.slice(0, 3)) {
          const p = run.player;
          if (Math.hypot(n.x - p.x, n.z - p.z) < TUNING.allHandsBarkRange) n.say(pick(STATE_BARKS.allHands), 2);
        }
        break;
      }
    }
  }

  private openElevators(run: Run, seconds: number): void {
    for (const e of run.level.elevators) e.openFor(seconds);
    run.elevatorCallTimer = -1;
    this.audio.elevatorDing();
    this.hud.toast(`DING. The elevator is here. ${Math.round(seconds)} seconds.`);
  }

  private checkExit(run: Run): void {
    const p = run.player;
    if (!run.level.isExit(p.x, p.z)) return;
    if (run.data.exitType === 'elevator' && !run.level.elevators.some((e) => e.isOpen)) return;
    // Leaving while someone is calling your name is not escaping; it's a scene.
    const pursuer = run.npcs.find((n) => n.state === NPCState.CONFRONT || n.state === NPCState.CHASE);
    if (pursuer) {
      this.endCaught(pursuer.def, 'fled');
      return;
    }
    this.endEscaped(run);
  }

  // -------------------------------------------------------------------------
  // Endings
  // -------------------------------------------------------------------------

  private endEscaped(run: Run): void {
    if (run.ended) return;
    run.ended = true;
    this.state.set(GameState.ESCAPED);
    this.input.releaseLock();
    const t = run.time;
    const par = run.data.parTime;
    const underPar = t <= par;
    let stars = t <= par ? 3 : t <= par * TUNING.starSlack ? 2 : 1;
    let flavor = '';
    const priya = run.npcs.find((n) => n.def.id === 'priya');
    if (run.data.exitType === 'stairwell' && priya && priya.awareness > 0) {
      stars = Math.max(1, stars - 1);
      flavor = `Priya followed you upstairs. She is "just checking on a thing." Sam from Legal waves. Priya waves back. It's a long ten minutes.`;
    } else if (run.data.id === 'level2') {
      flavor = 'Floor 7. Sam from Legal looks up. "Oh, hey. You came." You came.';
    }
    let ending: Ending = 'ESCAPED';
    if (run.bossPassedCorporate && underPar) ending = 'PROMOTION';
    else if (run.absurdSuccesses.length) ending = 'LEGEND';
    else if (run.dialogues === 0 && underPar) ending = 'CLEAN';
    if (ending === 'PROMOTION' && run.data.exitType !== 'elevator') {
      flavor = `${flavor ? `${flavor} ` : ''}Marcus holds the door. "Good initiative." You're not sure what just happened, but it's on your review now.`;
    }
    const newLegends = run.absurdSuccesses.filter((e) => this.memory.recordLegend(e.id));
    this.memory.recordEscape(run.data.id, stars, t, ending);
    this.audio.elevatorDing();
    window.setTimeout(() => this.audio.escapedFanfare(), 700);
    this.showResults(run, ending, stars, flavor, newLegends);
  }

  private endCaught(by: NPCDef, reason: CaughtReason): void {
    const run = this.run;
    if (!run || run.ended) return;
    run.ended = true;
    this.state.set(GameState.CAUGHT);
    this.input.releaseLock();
    this.memory.recordCaught();
    this.audio.duck(false);
    this.audio.caughtStinger();
    const first = by.name.split(' ')[0];
    const flavor = {
      chase: `${first} caught up with you. Running from a coworker is never, ever a good look.`,
      fled: `You made it to the door. So did ${first}. "Hey! Didn't you hear me?" Everyone heard.`,
      deadline: 'The invite landed. "Oh good, you\'re still here! Grab a chair." It was not a quick sync.',
      dialogue: '',
    }[reason];
    this.showResults(run, 'CAUGHT', 0, flavor, [], by, reason);
  }

  private showResults(run: Run, ending: Ending, stars: number, flavor: string, newLegends: ExcuseDef[], caughtBy?: NPCDef, caughtReason?: CaughtReason): void {
    const idx = LEVELS.findIndex((l) => l.id === run.data.id);
    this.hud.show(false);
    this.hud.hideIntro();
    this.hud.hidePause();
    this.hud.setInteract(null);
    this.vignette.reset();
    this.results.show({
      level: run.data, ending, time: run.time, stars, log: run.log,
      repBefore: run.repBefore, repAfter: this.memory.reputationLabel(),
      newLegends, caughtBy, caughtReason, flavor, hasNext: idx >= 0 && idx < LEVELS.length - 1,
    }, this.memory, {
      onRetry: () => this.startLevel(run.data.id),
      onNext: () => this.startLevel(LEVELS[idx + 1].id),
      onMenu: () => this.showMenu(),
    });
  }

  // -------------------------------------------------------------------------
  // HUD + helpers
  // -------------------------------------------------------------------------

  private updateHud(run: Run): void {
    const p = run.player;
    const raw = this.interactor.current?.label() ?? null;
    const label = raw && this.isTouch ? raw.replace('[E] ', 'USE: ') : raw;
    this.hud.setInteract(label);
    this.touch?.setCrouched(p.crouched);
    this.hud.setCrouched(p.crouched);
    this.hud.setNoise(run.noise.currentRadius);
    const clock = this.clockMinutes(run);
    const info: Parameters<HUD['setTopRight']>[0] = { clock: formatClock(clock) };
    if (run.deadline !== null) {
      const left = Math.max(0, (run.deadline - clock) * 60);
      info.deadline = `Sync in ${Math.floor(left / 60)}:${String(Math.floor(left % 60)).padStart(2, '0')}`;
      info.deadlineUrgent = left < 180;
    }
    if (run.data.favors) info.favors = { left: run.favors, total: run.data.favors };
    if (run.data.exitType === 'elevator') {
      const open = run.level.elevators.find((e) => e.timeLeft > 0);
      info.elevator = open ? `Elevator OPEN · ${Math.ceil(open.timeLeft)}s` : run.elevatorCallTimer > 0 ? `Elevator arriving · ${Math.ceil(run.elevatorCallTimer)}s` : 'Elevator: closed';
    }
    this.hud.setTopRight(info);

    let status = '';
    if (run.probe) status = `${run.probe.npc.def.name.split(' ')[0]} is walking with you · act natural · ${Math.max(0, Math.ceil(run.probe.npc.probeTimer))}s`;
    else if (run.beatTime < run.allHandsUntil) status = 'ALL-HANDS REMINDER · everyone is looking up';
    else if (run.world.playerHydrating) status = 'Hydrating… (people look away)';
    else if (run.data.exitType === 'elevator' && run.level.isExit(p.x, p.z)) status = 'The doors are closed. Call the elevator, or wait for it.';
    this.hud.setStatus(status);
  }

  private exitDist(run: Run): number {
    return Math.hypot(run.player.x - run.level.exitCenter.x, run.player.z - run.level.exitCenter.z);
  }

  clockMinutes(run: Run): number {
    const c = run.data.clock ?? { startMinutes: 12 * 60, rate: 1 / 6 };
    return c.startMinutes + run.time * c.rate;
  }

  private applySettings(s: Settings): void {
    this.input.sensitivity = 0.0022 * s.sensitivity;
    this.audio.setVolume(s.volume);
  }

  private resize(): void {
    const w = this.container.clientWidth || window.innerWidth;
    const h = this.container.clientHeight || window.innerHeight;
    this.renderer.setSize(w, h);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
  }
}

export function formatClock(minutes: number): string {
  const total = Math.floor(minutes);
  let h = Math.floor(total / 60) % 24;
  const m = total % 60;
  const ampm = h >= 12 ? 'PM' : 'AM';
  h = h % 12 || 12;
  return `${h}:${String(m).padStart(2, '0')} ${ampm}`;
}
