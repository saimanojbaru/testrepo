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
  // Extensions: 'G'=glass wall, 'L'=potted plant, 'O'=closet door (creaks).
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
  type: 'bark' | 'spawn' | 'moveTo' | 'phoneRing' | 'lockDoor' | 'calendar' | 'elevator' | 'allHands';
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
    | 'kalava' | 'mangalsutra' | 'rudraksha' | 'gamosaStrap' | 'apron'
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
