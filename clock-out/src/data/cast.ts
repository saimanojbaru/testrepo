import type { FaceRecipe } from '../ai/FaceBuilder';
import type { NPCLook } from './types';

// THE cast: the one place a character's identity lives. Everything that draws or voices a
// character derives from this file:
//   - cartoon looks and faces        -> src/data/npcs.ts (NPC_LOOKS)
//   - realistic head config          -> src/data/gnmCast.ts (GNM_CAST)
//   - realistic head + body builds   -> tools/cast.json (generated: `npm run cast:export`),
//                                       read by tools/gnm and tools/anny
// Where a renderer can't express a value (the cartoon has no "messy" hair), the translation
// is in the tables below, not in the renderer.
//
// `chosen` lists the fields the game's author has decided. Everything else is a default
// filled in to get something on screen; `npm run cast:defaults` lists them.

export type Gender = 'male' | 'female';
export type Hair = 'sidepart' | 'receding' | 'crop' | 'short' | 'messy' | 'bun' | 'braid';
export type FacialHair = 'moustache' | 'thickMoustache' | 'beard' | 'stubble';
export type Glasses = 'gold' | 'black' | 'silver';
/** Visible identity details. Forehead marks and jewellery; also religious threads and bangles. */
export type Marker =
  | 'vibhuti' | 'chandrakor' | 'chandanam' | 'bindi'
  | 'noseRing' | 'jhumka' | 'studs' | 'gajra' | 'securityCap'
  | 'mangalsutra' | 'kara' | 'kalava' | 'gamosa' | 'greenBangles' | 'goldBangles';

export type CastField =
  | 'name' | 'role' | 'city' | 'region' | 'gender' | 'age' | 'heightCm' | 'build' | 'skin'
  | 'hair' | 'hairColor' | 'greyTemples' | 'facialHair' | 'glasses' | 'markers' | 'voice'
  | 'tagline' | 'habit' | 'wardrobe' | 'outfit' | 'face' | 'gait' | 'rest';

export interface CastMember {
  id: string;
  name: string;
  role: string;
  city: string;
  region: string;
  gender: Gender;
  /** Years. */
  age: number;
  heightCm: number;
  /** 0..1, used by the realistic body. `belly` adds a paunch. */
  build: { weight: number; muscle: number; belly?: number };
  /** Canonical skin tone (sRGB hex). Realistic heads and bodies use it as is; the cartoon lifts it (cartoonSkin). */
  skin: string;
  hairColor: string;
  hair: Hair;
  greyTemples?: boolean;
  facialHair?: FacialHair;
  glasses?: Glasses;
  markers: Marker[];
  /** Base pitch (Hz) for the voice blips. */
  voice: number;
  tagline: string;
  /** A habit or object that makes them a person rather than a type. */
  habit?: string;
  /** Wardrobe brief not yet built (e.g. the Gen Z reset). */
  wardrobe?: string;
  realistic: {
    /** GNM identity: decoder seed, latent scale, identity-component nudges. */
    face: { seed: number; latent: number; overrides?: Record<string, number>; lip: string; browThick: number; kajal?: boolean };
    /** Morph weights held at rest. */
    rest?: Record<string, number>;
    /** Variation on the shared mocap walk. */
    gait?: { tempo?: number; lean?: number; arms?: number };
    /** Sewn outfit for the realistic body. */
    outfit: { kind: 'shirt' | 'uniform' | 'kurta' | 'saree'; top: string; bottom: string; fullSleeve?: boolean; dupatta?: string };
    /** Ships a realistic body (public/anny/<id>.glb). Otherwise the head sits on the cartoon body. */
    body?: boolean;
  };
  chosen: CastField[];
}

const ALWAYS_CHOSEN: CastField[] = ['name', 'role', 'age'];

export const CAST: Record<string, CastMember> = {
  ramesh: {
    id: 'ramesh', name: 'Ramesh Iyer', role: 'Senior Manager', city: 'Chennai', region: 'Tamil Nadu',
    gender: 'male', age: 50, heightCm: 173, build: { weight: 0.75, muscle: 0.15, belly: 0.5 },
    skin: '5e3926', hairColor: '2b2724', hair: 'sidepart', greyTemples: true, facialHair: 'moustache', glasses: 'gold',
    markers: ['vibhuti'], voice: 150,
    tagline: 'Chennai. "Quick sync at 6:45?" is not a question.',
    habit: 'Holds his phone face-up, waiting for a reply.',
    realistic: {
      face: { seed: 3002, latent: 1.6, overrides: { head_000: -1.0 }, lip: '6e3f36', browThick: 0.005 },
      gait: { tempo: 0.9, lean: -0.06, arms: 0.7 },
      outfit: { kind: 'shirt', top: 'c3cbd2', bottom: '26282c', fullSleeve: true },
      body: true,
    },
    chosen: [...ALWAYS_CHOSEN],
  },
  kavita: {
    id: 'kavita', name: 'Kavita Deshpande', role: 'Admin & Facilities', city: 'Pune', region: 'Maharashtra',
    gender: 'female', age: 35, heightCm: 161, build: { weight: 0.58, muscle: 0.3 },
    skin: '986a4f', hairColor: '140e0b', hair: 'bun',
    markers: ['chandrakor', 'gajra', 'studs', 'mangalsutra', 'greenBangles'], voice: 320,
    tagline: 'Pune. Knows who ordered biryani on the team card. And why.',
    habit: 'Reading glasses pushed up on her head; pulls them down to look at you when she is "noting" something.',
    realistic: {
      face: { seed: 4107, latent: 1.6, lip: '8a4a44', browThick: 0.0032, kajal: true },
      rest: { smirk: 0.55, squint: 0.35 },
      gait: { tempo: 1.12, lean: 0.03, arms: 0.8 },
      outfit: { kind: 'kurta', top: '0b5c63', bottom: 'e6ddd0', dupatta: 'a0124a' },
    },
    chosen: [...ALWAYS_CHOSEN, 'region'],
  },
  rinku: {
    id: 'rinku', name: 'Rinku Gogoi', role: 'Fresher (Week 3)', city: 'Jorhat', region: 'Assam',
    // UNDECIDED: the author has not chosen Rinku's gender. Built male as a placeholder.
    gender: 'male', age: 22, heightCm: 173, build: { weight: 0.35, muscle: 0.4 },
    skin: 'ba8c6e', hairColor: '0e0b0a', hair: 'messy',
    markers: ['gamosa'], voice: 290,
    tagline: 'Jorhat. Three weeks in. Still believes in all of it.',
    habit: 'A notebook everywhere; writes down whatever you say ("for learning").',
    wardrobe: 'Cargo pants, oversized tee under an open check shirt, AirPods, backpack with keychains.',
    realistic: {
      face: { seed: 5106, latent: 1.6, overrides: { head_000: 1.0 }, lip: '8e5a4c', browThick: 0.004 },
      rest: { smile: 0.5, jawOpen: 0.28, browRaise: 0.3 },
      outfit: { kind: 'shirt', top: '5e9a6a', bottom: '2b3a55', fullSleeve: false },
    },
    chosen: [...ALWAYS_CHOSEN, 'region', 'wardrobe'],
  },
  priya: {
    id: 'priya', name: 'Priya Menon', role: 'HR Business Partner', city: 'Thrissur', region: 'Kerala',
    gender: 'female', age: 30, heightCm: 163, build: { weight: 0.45, muscle: 0.35 },
    skin: '774d35', hairColor: '0b0908', hair: 'braid', glasses: 'black',
    markers: ['chandanam', 'jhumka'], voice: 260,
    tagline: 'Thrissur. Has a form for this. Has a form for the form.',
    habit: 'Clicks her lanyard badge while you talk.',
    wardrobe: 'Minimalist jewellery, high-waisted trousers, tinted sunglasses pushed up, coffee cup always.',
    realistic: {
      face: { seed: 2007, latent: 1.6, lip: '7c4038', browThick: 0.0032, kajal: true },
      outfit: { kind: 'kurta', top: 'efe9dc', bottom: 'efe9dc', dupatta: 'c9a227' },
    },
    chosen: [...ALWAYS_CHOSEN, 'region', 'wardrobe'],
  },
  rohit: {
    id: 'rohit', name: 'Rohit Malhotra', role: 'Senior Software Engineer', city: 'Ludhiana', region: 'Punjab',
    gender: 'male', age: 28, heightCm: 183, build: { weight: 0.55, muscle: 0.6 },
    skin: 'c49779', hairColor: '15100d', hair: 'short', facialHair: 'beard',
    markers: ['kara'], voice: 190,
    tagline: 'Ludhiana. Will tell everyone about you. Already has.',
    habit: 'Headphones round his neck; slides one ear-cup off the moment there is gossip.',
    wardrobe: 'Office bro: trimmed beard (not full), clean shoes, one earbud always in, wristwatch.',
    realistic: {
      face: { seed: 3009, latent: 1.6, lip: '884e44', browThick: 0.005 },
      gait: { tempo: 0.94, lean: -0.03, arms: 1.35 },
      outfit: { kind: 'shirt', top: 'a49ac9', bottom: '1c2233', fullSleeve: true },
    },
    chosen: [...ALWAYS_CHOSEN, 'wardrobe'],
  },
  srinivas: {
    id: 'srinivas', name: 'Srinivas Goud', role: 'Facilities & Security', city: 'Secunderabad', region: 'Telangana',
    gender: 'male', age: 45, heightCm: 175, build: { weight: 0.72, muscle: 0.45, belly: 0.3 },
    skin: '4f2f1e', hairColor: '120e0c', hair: 'crop', facialHair: 'thickMoustache',
    markers: ['securityCap', 'kalava'], voice: 130,
    tagline: 'Secunderabad. Knows every access card and every face. Nobody asks.',
    habit: 'Taps his walkie antenna against his palm; waiting for a reason to use it.',
    realistic: {
      face: { seed: 3011, latent: 1.6, overrides: { head_001: 0.8 }, lip: '643a30', browThick: 0.005 },
      outfit: { kind: 'uniform', top: '3a4a5e', bottom: '1f2a1f', fullSleeve: true },
    },
    chosen: [...ALWAYS_CHOSEN, 'region'],
  },
  lakshmi: {
    id: 'lakshmi', name: 'Lakshmi Reddy', role: 'Cafeteria Supervisor', city: 'Warangal', region: 'Telangana',
    gender: 'female', age: 50, heightCm: 159, build: { weight: 0.66, muscle: 0.3 },
    skin: '69422e', hairColor: '4a4540', hair: 'bun', greyTemples: true,
    markers: ['bindi', 'noseRing', 'mangalsutra', 'goldBangles'], voice: 240,
    tagline: 'Warangal. Twenty-two years in the canteen. Knows everyone\'s order.',
    habit: 'A steel tumbler of filter coffee; squints when you talk.',
    realistic: {
      face: { seed: 2010, latent: 1.6, overrides: { head_000: -0.6 }, lip: '74443a', browThick: 0.003, kajal: true },
      gait: { tempo: 0.86, lean: 0.07, arms: 0.55 },
      outfit: { kind: 'kurta', top: '8c2a1c', bottom: '8c2a1c', dupatta: 'b8862a' },
    },
    chosen: [...ALWAYS_CHOSEN, 'region', 'habit', 'outfit'],
  },
  deepak: {
    id: 'deepak', name: 'Deepak Hegde', role: 'Team Lead', city: 'Mangaluru', region: 'Karnataka',
    gender: 'male', age: 33, heightCm: 177, build: { weight: 0.5, muscle: 0.4 },
    skin: '835d47', hairColor: '120d0b', hair: 'sidepart', facialHair: 'stubble', glasses: 'black',
    markers: [], voice: 210,
    tagline: 'Mangaluru. Takes the credit, gives the tickets.',
    habit: 'Peels a sticky note off a pad and sticks it on something whenever he talks.',
    wardrobe: 'Short trendy cut or man bun, short beard, sleeves rolled, casual-Friday energy, sneakers.',
    realistic: {
      face: { seed: 3006, latent: 1.6, lip: '7e4a40', browThick: 0.0045 },
      rest: { browRaise: 0.35 },
      outfit: { kind: 'shirt', top: '3d3a6a', bottom: '141414', fullSleeve: true },
    },
    chosen: [...ALWAYS_CHOSEN, 'region', 'wardrobe'],
  },
  sanjay: {
    id: 'sanjay', name: 'Sanjay Mukherjee', role: 'Director (Returned from Dallas)', city: 'Kolkata', region: 'West Bengal',
    gender: 'male', age: 45, heightCm: 180, build: { weight: 0.5, muscle: 0.4 },
    skin: 'a1755b', hairColor: '3a3530', hair: 'receding', greyTemples: true, glasses: 'silver',
    markers: [], voice: 165,
    tagline: 'Kolkata, via Dallas. Mostly via Dallas.',
    habit: 'Keeps checking his watch (Dallas time); one AirPod always in.',
    realistic: {
      face: { seed: 3001, latent: 1.6, lip: '80503f', browThick: 0.0045 },
      outfit: { kind: 'shirt', top: '1b2a4a', bottom: '1b2a4a', fullSleeve: true },
    },
    chosen: [...ALWAYS_CHOSEN, 'region'],
  },
};

// ---------------------------------------------------------------------------
// Translations: canonical values -> what each renderer can express.
// ---------------------------------------------------------------------------

/** Cartoon face hair styles (FaceBuilder) and realistic hair shells (tools/gnm). */
export const HAIR: Record<Hair, { cartoon: FaceRecipe['hairStyle']; realistic: string; hairlineUp?: number }> = {
  sidepart: { cartoon: 'sidepart', realistic: 'side' },
  receding: { cartoon: 'receding', realistic: 'side', hairlineUp: 0.045 },
  crop: { cartoon: 'crop', realistic: 'crop' },
  short: { cartoon: 'spiky', realistic: 'short' },
  // Neither renderer has real messy hair yet (needs strand/card hair, week-2 item).
  messy: { cartoon: 'crop', realistic: 'spiky' },
  bun: { cartoon: 'bun', realistic: 'bun' },
  braid: { cartoon: 'braid', realistic: 'braid' },
};

/** Realistic head grooming for each facial-hair choice (tools/gnm parameters). */
export const FACIAL_HAIR: Record<FacialHair, Record<string, number | string>> = {
  moustache: { moustache: 0.006 },
  thickMoustache: { beard: 'moustache' },
  beard: { beard: 'full' },
  stubble: { stubble: 0.35 },
};

/** Markers the realistic head can draw (GnmMarkers), in the order they're layered. */
export const REALISTIC_HEAD_MARKERS: Marker[] = ['vibhuti', 'chandrakor', 'chandanam', 'bindi', 'noseRing', 'jhumka', 'securityCap'];

/** Cartoon body accessories that carry a marker. */
export const CARTOON_MARKER_ACCESSORIES: Partial<Record<Marker, NPCLook['accessories'][number]>> = {
  mangalsutra: 'mangalsutra', kara: 'kara', kalava: 'kalava', gamosa: 'gamosaStrap',
  greenBangles: 'greenBangles', goldBangles: 'goldBangles',
};

// ---------------------------------------------------------------------------
// Derived values.
// ---------------------------------------------------------------------------

/** The cartoon's flat Lambert shading reads darker than PBR skin: lift by this much L*. */
const CARTOON_SKIN_LIFT = 10;

function hexToLab(hex: string): [number, number, number] {
  const n = parseInt(hex, 16);
  const lin = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => { const c = v / 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; });
  const X = (0.4124 * lin[0] + 0.3576 * lin[1] + 0.1805 * lin[2]) / 0.95047;
  const Y = 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2];
  const Z = (0.0193 * lin[0] + 0.1192 * lin[1] + 0.9505 * lin[2]) / 1.08883;
  const f = (t: number) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  return [116 * f(Y) - 16, 500 * (f(X) - f(Y)), 200 * (f(Y) - f(Z))];
}

function labToHexNumber(L: number, a: number, b: number): number {
  const fy = (L + 16) / 116, fx = fy + a / 500, fz = fy - b / 200;
  const inv = (t: number) => (t ** 3 > 0.008856 ? t ** 3 : (t - 16 / 116) / 7.787);
  const X = inv(fx) * 0.95047, Y = inv(fy), Z = inv(fz) * 1.08883;
  const lin = [3.2406 * X - 1.5372 * Y - 0.4986 * Z, -0.9689 * X + 1.8758 * Y + 0.0415 * Z, 0.0557 * X - 0.204 * Y + 1.057 * Z];
  const enc = lin.map((v) => Math.round(255 * Math.max(0, Math.min(1, v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055))));
  return (enc[0] << 16) | (enc[1] << 8) | enc[2];
}

export function cartoonSkin(m: CastMember): number {
  const [L, a, b] = hexToLab(m.skin);
  return labToHexNumber(Math.min(90, L + CARTOON_SKIN_LIFT), a, b);
}

export const hex = (s: string): number => parseInt(s, 16);

/** Anny body phenotype values (tools/anny). Anny: gender 0 male / 1 female; age anchors child 1/3 (~10 y), young 2/3 (~25 y), old 1 (~90 y). */
export function annyPhenotype(m: CastMember): Record<string, number> {
  const age = m.age < 25 ? 1 / 3 + (m.age - 10) / 15 / 3 : 2 / 3 + (m.age - 25) / 65 / 3;
  const base = m.gender === 'female' ? 145 : 155;
  const round = (v: number) => Math.round(v * 1000) / 1000;
  return {
    gender: m.gender === 'female' ? 1 : 0,
    age: round(age),
    weight: m.build.weight,
    muscle: m.build.muscle,
    height: round(Math.max(0, Math.min(1, (m.heightCm - base) / 40))),
    proportions: 0.5,
  };
}

/** Everything tools/gnm needs to bake this character's realistic head. */
export function headSpec(m: CastMember): Record<string, unknown> {
  const f = m.realistic.face;
  const hair = HAIR[m.hair];
  return {
    id: m.id, seed: f.seed, female: m.gender === 'female', ...(f.kajal ? { kajal: 1 } : {}), latent: f.latent,
    ...(f.overrides ? { overrides: f.overrides } : {}),
    skin: m.skin, lip: f.lip, hair: hair.realistic, hairColor: m.hairColor,
    ...(m.facialHair ? FACIAL_HAIR[m.facialHair] : {}),
    ...(hair.hairlineUp ? { forehead: hair.hairlineUp } : {}),
    browThick: f.browThick,
  };
}

/** Everything tools/anny needs to build this character's realistic body. */
export function bodySpec(m: CastMember): Record<string, unknown> {
  return {
    id: m.id, phenotype: annyPhenotype(m),
    ...(m.build.belly ? { local: { 'stomach-pregnant-incr': m.build.belly } } : {}),
    skin: m.skin, outfit: m.realistic.outfit,
  };
}

/** Fields still at their default for a member. */
export function defaultedFields(m: CastMember): CastField[] {
  const all: CastField[] = ['name', 'role', 'city', 'region', 'gender', 'age', 'heightCm', 'build', 'skin', 'hair', 'hairColor', 'greyTemples', 'facialHair', 'glasses', 'markers', 'voice', 'tagline', 'habit', 'wardrobe', 'outfit', 'face', 'gait', 'rest'];
  return all.filter((f) => !m.chosen.includes(f));
}
