import type { FaceRecipe } from '../ai/FaceBuilder';
import type { NPCDef, NPCLook } from './types';
import { CARTOON_MARKER_ACCESSORIES, CAST, HAIR, cartoonSkin, hex, type Marker } from './cast';

// The cast: one HITEC City floor, people from all over India. Region shows up in
// small, true details (a thin vibhuti line, a kara, a chandanam mark, a few words
// of home language in the barks) and never as costume or caricature.
// Every bark should reveal something human; the office is the villain, not them.

export const NPC_DEFS: Record<string, NPCDef> = {
  ramesh: {
    id: 'ramesh',
    name: CAST.ramesh.name,
    role: CAST.ramesh.role,
    color: 0xc9d9ea, // pale blue half-sleeve formal shirt
    visionRange: 18,
    visionFov: 95,
    hearingMul: 0.8, // earphones in for "calls with onsite"
    walkSpeed: 1.25,
    talkativeness: 0.7,
    archetype: 'boss',
    barks: [
      "Quick sync at 6:45, ma. Five minutes only. Maybe forty.",
      "Ownership mindset, people. Ownership. Of the work. Not of the exit.",
      "In my time we didn't have WFH. We had W-F-Office. We were happy.",
      "Why is the stand-up sitting down? Stand-up means standing.",
      "I'm not a boss, I'm a servant leader. Serve me the status report.",
      "Appraisal is not about hours. But also, I have noted the hours.",
      "Nobody leaves before the manager. That's not a rule. It is a tradition.",
      "Filter coffee from the machine is not filter coffee. It is a crime.",
    ],
  },
  kavita: {
    id: 'kavita',
    name: CAST.kavita.name,
    role: CAST.kavita.role,
    color: 0x1f5f6b, // teal cotton kurti
    visionRange: 13,
    visionFov: 110,
    hearingMul: 1.3, // hears everything; that is her whole thing
    walkSpeed: 1.05,
    talkativeness: 0.9,
    archetype: 'gossip',
    barks: [
      "Arre, someone heated fish in the pantry microwave again. I have a list.",
      "Did you give for Karthik's farewell gift? He's going to Amazon. I'm not supposed to know.",
      "I'm not gossiping. I'm keeping the WhatsApp group informed.",
      "Ramesh sir had two coffees before 10. Something is happening at home, ho na?",
      "Annual Day dance practice is Thursday. I've already put your name.",
      "Lakshmi says the new fresher eats lunch at 11:30. Who does that?",
      "My husband says take VRS. I say, and then what? Not know things?",
      "The plants I water myself. Nobody else here will keep anything alive.",
    ],
  },
  rinku: {
    id: 'rinku',
    name: CAST.rinku.name,
    role: CAST.rinku.role,
    color: 0x5a8a6a, // company welcome-kit hoodie, still creased
    visionRange: 12,
    visionFov: 100,
    hearingMul: 1.0,
    walkSpeed: 1.35,
    talkativeness: 1.0,
    archetype: 'intern',
    barks: [
      "Sir, where do they keep the good pens? The ones that click?",
      "I made a Confluence page for our Confluence pages. People seem worried.",
      "Is it okay that I actually like it here? Everyone laughs when I say that.",
      "My TL said 'do the needful'. I have been doing everything. Is that the needful?",
      "Free Maggi in the pantry. FREE Maggi. Why is nobody talking about this?",
      "Ma called from Jorhat. She asked if I'm eating. I said yes. Maggi counts.",
      "Hoi, I'm shadowing whoever looks busiest. Right now it's the printer.",
      "Training said 'no question is a stupid question'. They have not met my questions.",
    ],
  },
  priya: {
    id: 'priya',
    name: CAST.priya.name,
    role: CAST.priya.role,
    color: 0xf1e9d6, // cream kurta with a thin gold border
    visionRange: 15,
    visionFov: 100,
    hearingMul: 1.2,
    walkSpeed: 1.4,
    talkativeness: 0.8,
    archetype: 'hr',
    barks: [
      "Friendly reminder: a 'friendly reminder' is a formal warning.",
      "I'm not here to judge. I'm here to document. Judging is a different team.",
      "Has everyone completed the POSH module? And the other module? And the module about modules?",
      "We are a family here. Legally we are not a family. But culturally, alle?",
      "I can sense an unused leave balance from across the floor.",
      "Fun Friday is optional. Attendance is being taken. Both are true.",
      "I went into HR to help people. Now I help Excel help people.",
      "Swipe-in time, swipe-out time. The data doesn't lie. The people, sometimes.",
    ],
  },
  rohit: {
    id: 'rohit',
    name: CAST.rohit.name,
    role: CAST.rohit.role,
    color: 0x9d93cf, // lavender shirt, sleeves rolled up
    visionRange: 16,
    visionFov: 90,
    hearingMul: 1.0,
    walkSpeed: 1.2,
    talkativeness: 0.6,
    archetype: 'coworker',
    barks: [
      "I'm not saying I'm leaving. But my Naukri profile is very updated, bhai.",
      "Cricket fantasy league, auction is Thursday. You're in. You don't get a say.",
      "Six-thirty. Six-thirty. Why is it never six-thirty?",
      "If anyone sneaks out early, I'll tell Ramesh sir. Not because I care. Because I can't.",
      "Three years same sticky note on my monitor. It says 'resign'.",
      "Went to the washroom for eleven minutes. Best part of my day.",
      "Ramesh sir called me 'champ' today. I've been thinking about it all morning.",
    ],
  },
  srinivas: {
    id: 'srinivas',
    name: CAST.srinivas.name,
    role: CAST.srinivas.role,
    color: 0x51606f, // security uniform shirt
    visionRange: 14,
    visionFov: 105,
    hearingMul: 1.1,
    walkSpeed: 1.3,
    talkativeness: 0.5,
    archetype: 'security',
    barks: [
      "AC is locked at 21 degrees. Nakko ask me. I'm only having the key.",
      "Somebody put a spoon in the coffee machine. Again. I know it's a spoon.",
      "Every access card I know. Every face I know. Nobody asks me anything.",
      "That hum? That's the building. The building is also tired, saar.",
      "Fire exit alarm is working. I tested. Twice. Nobody came.",
      "Twenty years. Never took the stairs up. Stairs are for going home.",
    ],
  },
  lakshmi: {
    id: 'lakshmi',
    name: CAST.lakshmi.name,
    role: CAST.lakshmi.role,
    color: 0x8f4a3a, // maroon cotton saree under a white apron
    visionRange: 14,
    visionFov: 100,
    hearingMul: 1.0,
    walkSpeed: 1.2,
    talkativeness: 0.7,
    archetype: 'coworker',
    barks: [
      "Tinnara? You ate? You didn't eat. I can see you didn't eat.",
      "One o'clock rush, everyone comes like a flood. Twelve fifty-five, nobody. Why?",
      "Twenty-two years in this canteen. I've seen four CEOs and one working microwave.",
      "The pesarattu is today only. Tomorrow you will ask. Tomorrow is idli.",
      "Ramesh sir takes extra papad. Every day. Don't tell anyone I told you.",
      "Nobody leaves without eating on my floor. Not a rule. A promise.",
    ],
  },
  deepak: {
    id: 'deepak',
    name: CAST.deepak.name,
    role: CAST.deepak.role,
    color: 0x4a4a6a, // dark polo, red-and-yellow lanyard
    visionRange: 12,
    visionFov: 100,
    hearingMul: 1.1,
    walkSpeed: 1.3,
    talkativeness: 0.85,
    archetype: 'chatty',
    barks: [
      "Guru, any update? Small update? Even an update about the update?",
      "Swalpa adjust maadi, sprint is only two weeks. Everything fits in two weeks.",
      "I'll present this to Ramesh sir. Don't worry, I'll mention you. Maybe.",
      "Let's parallelly work on it. Parallelly means you, and I'll check.",
      "My ticket count is highest in the team. Because I assign them.",
      "Weekend? Weekend is also a day, no?",
    ],
  },
  sanjay: {
    id: 'sanjay',
    name: CAST.sanjay.name,
    role: CAST.sanjay.role,
    color: 0x2c3a55, // the only blazer in Hyderabad in May
    visionRange: 15,
    visionFov: 100,
    hearingMul: 1.0,
    walkSpeed: 1.3,
    talkativeness: 0.8,
    archetype: 'boss',
    barks: [
      "In Dallas, we had a culture of accountability. Also of parking.",
      "Ki holo? Why is the stand-up forty minutes? In Dallas, fifteen.",
      "I'm not comparing. I'm just saying in Dallas this would be done.",
      "Here everyone says 'will do'. In Dallas, people did.",
      "Proper mishti doi is only in Kolkata. Proper process is only in Dallas.",
      "My door is always open. It is glass. It is always also closed.",
    ],
  },
};

/**
 * Cartoon looks. Identity (skin, hair, facial hair, glasses, markers, height, voice, tagline)
 * comes from CAST (src/data/cast.ts); only cartoon-specific shaping and clothing live here.
 */
function look(id: string, c: { pants: number; trim?: number; clothes: NPCLook['accessories']; face: Omit<FaceRecipe, 'skin' | 'hair' | 'hairStyle'> }): NPCLook {
  const m = CAST[id];
  const skin = cartoonSkin(m);
  const hair = hex(m.hairColor);
  const has = (k: Marker) => m.markers.includes(k);
  const forehead = (['vibhuti', 'chandrakor', 'chandanam', 'bindi'] as const).find(has);
  const earrings = has('jhumka') ? 'jhumka' : has('studs') ? 'stud' : undefined;
  const markerAccessories = m.markers.map((k) => CARTOON_MARKER_ACCESSORIES[k]).filter((a): a is NPCLook['accessories'][number] => !!a);
  return {
    skin, hair, pants: c.pants, voice: m.voice, height: m.heightCm / 170, tagline: m.tagline,
    // Only drawn by faceless legacy heads; every cast member has a face recipe.
    hairStyle: m.hair === 'bun' || m.hair === 'braid' ? 'bun' : has('securityCap') ? 'cap' : 'short',
    ...(c.trim !== undefined ? { trim: c.trim } : {}),
    accessories: [...c.clothes, ...markerAccessories],
    face: {
      ...c.face, skin, hair, hairStyle: HAIR[m.hair].cartoon,
      ...(m.greyTemples ? { greyTemples: true } : {}),
      ...(m.facialHair ? { facialHair: m.facialHair } : {}),
      ...(m.glasses ? { glasses: m.glasses } : {}),
      ...(forehead ? { forehead } : {}),
      ...(has('securityCap') ? { hat: 'securityCap' as const } : {}),
      ...(has('gajra') ? { gajra: true } : {}),
      ...(earrings ? { earrings } : {}),
      ...(has('noseRing') ? { noseRing: true } : {}),
    },
  };
}

export const NPC_LOOKS: Record<string, NPCLook> = {
  ramesh: look('ramesh', { pants: 0x3a3d44, clothes: ['halfSleeve', 'belt', 'penPocket', 'watch'],
    face: { mouth: 'neutral', browTilt: 0.2, browThickness: 1.4, shape: { w: 0.94, h: 1.08, d: 0.98 }, noseLength: 1.1 } }),
  kavita: look('kavita', { pants: 0xe9e2d0, trim: 0xa3195b, clothes: ['kurti', 'dupatta'],
    face: { mouth: 'smirk', eyeSquint: 0.62, browRaise: 0.012, browTilt: 0.02, lipColor: 0x7a3434, shape: { w: 0.9, h: 1.1, d: 0.95 } } }),
  rinku: look('rinku', { pants: 0x3b4a66, trim: 0xb3202a, clothes: ['lanyard', 'backpack'],
    face: { mouth: 'open', eyeSize: 1.1, catchlight: true, browTilt: -0.14, browThickness: 0.9, shape: { w: 0.93, h: 1.05, d: 0.95 }, noseLength: 0.85 } }),
  priya: look('priya', { pants: 0xf1e9d6, trim: 0xc9a24a, clothes: ['kurti', 'clipboard', 'watch'],
    face: { mouth: 'neutral', browTilt: 0.12, shape: { w: 0.9, h: 1.1, d: 0.96 } } }),
  rohit: look('rohit', { pants: 0x2b3345, clothes: ['halfSleeve', 'belt', 'watch'],
    face: { mouth: 'smirk', browThickness: 1.6, browTilt: 0.1, shape: { w: 0.96, h: 1.06, d: 0.98 } } }),
  srinivas: look('srinivas', { pants: 0x2f3a2c, clothes: ['clipboard', 'belt'],
    face: { mouth: 'neutral', browThickness: 1.3, shape: { w: 0.97, h: 1.04, d: 0.98 } } }),
  lakshmi: look('lakshmi', { pants: 0x8f4a3a, trim: 0xd4af37, clothes: ['kurti', 'apron'],
    face: { mouth: 'smile', shape: { w: 0.95, h: 1.05, d: 0.96 } } }),
  deepak: look('deepak', { pants: 0x2a2a2a, trim: 0xd62828, clothes: ['lanyard', 'watch', 'belt'],
    face: { mouth: 'smirk', browRaise: 0.01, shape: { w: 0.95, h: 1.06, d: 0.97 } } }),
  sanjay: look('sanjay', { pants: 0x2c3a55, clothes: ['blazer', 'tie', 'watch'],
    face: { mouth: 'tired', shape: { w: 0.98, h: 1.04, d: 0.98 } } }),
};

/** NPCs the Director is allowed to bring in as wanderers, in preference order. */
export const WANDERER_POOL = ['deepak', 'sanjay', 'srinivas', 'lakshmi', 'rinku'];

export function getNpcDef(id: string): NPCDef {
  const def = NPC_DEFS[id];
  if (!def) throw new Error(`Unknown NPC def "${id}"`);
  return def;
}

export function getNpcLook(id: string): NPCLook {
  const look = NPC_LOOKS[id];
  if (!look) throw new Error(`Unknown NPC look "${id}"`);
  return look;
}
