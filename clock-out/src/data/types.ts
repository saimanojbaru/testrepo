import type { FaceRecipe } from '../ai/FaceBuilder';

// Core data contracts. The first five interfaces follow the design spec exactly;
// everything after the "extensions" marker is additive and documented in README.

export interface LevelData {
  id: string;
  name: string;
  brief: string;              // shown on level select
  goalText: string;           // HUD objective
  parTime: number;            // seconds, for star rating
  gridSize: [number, number]; // nav grid dimensions (cells)
  cellSize: number;           // world units per cell (use 1.5)
  // Layout is authored as ASCII: '#'=wall, '.'=floor, 'D'=desk, 'C'=cubicle divider,
  // 'P'=player spawn, 'X'=exit trigger, 'K'=copier/cover prop, 'W'=watercooler,
  // 'M'=meeting room table, 'E'=elevator doors, 'S'=stairwell door
  // Extensions: 'G'=glass wall, 'L'=potted plant, 'O'=closet door (creaks),
  // 'V'=tea/coffee vending machine, 'A'=acoustic-panel wall (dampens your noise), 'R'=reception desk,
  // 'T'=stage platform, 'Z'=server rack (hum masks your noise), 'B'=bean bag, 'H'=theatre seat row,
  // 'F'=cafeteria counter, 'U'=cinema screen wall, 'Q'=fire exit door (escape, but the alarm goes off).
  ascii: string[];
  npcs: NPCPlacement[];
  exitType: 'elevator' | 'stairwell' | 'outside-door';
  scriptedBeats: ScriptedBeat[];

  // --- extensions (optional, additive) ---
  /** Copier-jam favors available in this level. 0/undefined = copiers are just cover. */
  favors?: number;
  /** In-fiction wall clock: start time in minutes after midnight, and game-minutes per real second. */
  clock?: { startMinutes: number; rate: number };
  /** Excuses the level forces into "already used" before it starts (Level 3's cooker). */
  forceUsedExcuses?: string[];
  /** Flavor paragraph shown on the level intro card. */
  intro: string;
  /** Max wanderers the Director may add on top of authored NPCs. */
  directorBudget: number;
  /** Flat suspicion every excuse costs on this level, shown as a verdict line item. */
  suspicionModifier?: { label: string; value: number };

  /** Chapter number shown in the menu; chapters unlock in this order. */
  chapter: number;
  /** Lighting and decoration set. */
  theme?: 'day' | 'night' | 'theatre' | 'festival';
  /** Lit points of interest for the dark levels (emergency lights, a call running late, a phone). */
  props?: PropSpec[];
  /** Things to do, in order, before the exit counts ("grab your charger"). */
  objectives?: Array<{ cell: [number, number]; label: string; done: string }>;
  /**
   * "Looking busy": while this NPC is still at their post, everyone else notices you
   * faster and every excuse costs extra ("Ramesh sir hasn't logged off yet").
   */
  watcher?: { npc: string; fillMul: number; label: string; value: number };
  /** Ending lines specific to this chapter. */
  caughtFlavor?: string;
  escapeFlavor?: string;
}

export interface NPCPlacement {
  def: string;
  cell: [number, number];
  patrol?: [number, number][];
  facing?: number; // degrees: 0 = north (toward row 0), 90 = east, 180 = south, 270 = west
}

export interface ScriptedBeat {
  atTime: number;
  // Spec types plus three level-specific ones (calendar, elevator, allHands).
  type:
    | 'bark' | 'spawn' | 'moveTo' | 'phoneRing' | 'lockDoor' | 'calendar' | 'elevator' | 'allHands'
    // Chapter mechanics: lights out, everyone drifts to one spot, everyone gets warier, someone logs off.
    | 'powerCut' | 'gather' | 'heat' | 'despawn' | 'toast';
  payload: any;
}

export interface NPCDef {
  id: string;
  name: string;
  role: string;
  color: number;
  visionRange: number;
  visionFov: number;
  hearingMul: number;
  walkSpeed: number;
  talkativeness: number; // 0..1, how long they hold you
  archetype: 'boss' | 'gossip' | 'chatty' | 'hr' | 'intern' | 'security' | 'coworker';
  barks: string[];
}

export interface ExcuseDef {
  id: string;
  category: 'medical' | 'domestic' | 'romantic' | 'absurd' | 'corporate' | 'evasive';
  text: string;
  risk: 1 | 2 | 3 | 4 | 5; // base suspicion cost
  tags: string[];           // e.g. ['funny','believable','needs_detail','repeatable']
  followUp?: string;        // NPC's probing question
  pivotLine?: string;       // a second line to recover if challenged
  requires?: { trait?: keyof NPCDef; memoryFlag?: string };
  immuneArchetypes?: NPCDef['archetype'][]; // e.g. boss never buys 'absurd'
}

// ---------------------------------------------------------------------------
// Extensions
// ---------------------------------------------------------------------------

export type Archetype = NPCDef['archetype'];
export type ExcuseCategory = ExcuseDef['category'];

/** Visual + voice identity. Kept out of NPCDef so the spec schema stays exact. */
export interface NPCLook {
  skin: number;
  hair: number;
  hairStyle: 'bob' | 'big' | 'bald' | 'short' | 'bun' | 'cap' | 'swoop';
  pants: number;
  accessories: Array<
    | 'tie' | 'lanyard' | 'glasses' | 'clipboard' | 'mug' | 'headset' | 'backpack' | 'toolbelt' | 'pearls' | 'cardigan'
    // Indian-office wear and small regional tells.
    | 'halfSleeve' | 'belt' | 'penPocket' | 'kurti' | 'dupatta' | 'watch' | 'kara' | 'greenBangles' | 'goldBangles'
    | 'kalava' | 'mangalsutra' | 'rudraksha' | 'gamosaStrap' | 'apron' | 'blazer'
  >;
  /** Layered-primitive face. When present it replaces the old box head and hair. */
  face?: FaceRecipe;
  /** Accent colour for kurti borders, dupattas and similar trims. */
  trim?: number;
  /** Base pitch in Hz for the Animal-Crossing-style voice blips. */
  voice: number;
  /** One-line bio shown under the name in dialogue. */
  tagline: string;
  /** Height multiplier; silhouettes matter when you only see heads over dividers. */
  height: number;
}

/** Context flags the dialogue system can key openers and excuse fit on. */
export type EncounterContext =
  | 'crouching'
  | 'sprinting'
  | 'near_exit'
  | 'near_kitchen'
  | 'near_copier'
  | 'caught_before'
  | 'heard_before'
  | 'voluntary'
  | 'probe_violation';

export interface DialogueNode {
  /** Which NPC id or archetype this line belongs to ('any' = generic). */
  who: string;
  /** All listed contexts must be present for this node to be eligible. */
  when?: EncounterContext[];
  lines: string[];
}

export type Outcome = 'PASSED' | 'PROBED' | 'ESCORTED' | 'CAUGHT';

export type Ending = 'CLEAN' | 'ESCAPED' | 'LEGEND' | 'PROMOTION' | 'CAUGHT';

export interface PropSpec {
  kind: 'emergencyLight' | 'callRoom' | 'cleaningCart' | 'deskLamp' | 'phoneGlow';
  cell: [number, number];
  /** Metres from the cell centre. */
  offset?: [number, number];
  /** Degrees; 0 faces +Z. */
  yaw?: number;
  color?: 'red' | 'green';
}
