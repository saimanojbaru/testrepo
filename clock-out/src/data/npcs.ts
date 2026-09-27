import type { NPCDef, NPCLook } from './types';

// The cast: one HITEC City floor, people from all over India. Region shows up in
// small, true details (a thin vibhuti line, a kara, a chandanam mark, a few words
// of home language in the barks) and never as costume or caricature.
// Every bark should reveal something human; the office is the villain, not them.

export const NPC_DEFS: Record<string, NPCDef> = {
  ramesh: {
    id: 'ramesh',
    name: 'Ramesh Iyer',
    role: 'Senior Manager',
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
    name: 'Kavita Deshpande',
    role: 'Admin & Facilities',
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
    name: 'Rinku Gogoi',
    role: 'Fresher (Week 3)',
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
    name: 'Priya Menon',
    role: 'HR Business Partner',
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
    name: 'Rohit Malhotra',
    role: 'Senior Software Engineer',
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
    name: 'Srinivas Goud',
    role: 'Facilities & Security',
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
    name: 'Lakshmi Reddy',
    role: 'Cafeteria Supervisor',
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
    name: 'Deepak Hegde',
    role: 'Team Lead',
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
    name: 'Sanjay Mukherjee',
    role: 'Director (Returned from Dallas)',
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

export const NPC_LOOKS: Record<string, NPCLook> = {
  ramesh: {
    skin: 0x8a5a3c, hair: 0x15110f, hairStyle: 'short', pants: 0x3a3d44, voice: 150, height: 1.03,
    tagline: 'Chennai. "Quick sync at 6:45?" is not a question.',
    accessories: ['halfSleeve', 'belt', 'penPocket', 'watch'],
    face: {
      skin: 0x8a5a3c, hair: 0x15110f, hairStyle: 'sidepart', greyTemples: true, facialHair: 'moustache',
      glasses: 'gold', forehead: 'vibhuti', mouth: 'neutral', browTilt: 0.2, browThickness: 1.4,
      shape: { w: 0.94, h: 1.08, d: 0.98 }, noseLength: 1.1,
    },
  },
  kavita: {
    skin: 0xa8704c, hair: 0x120d0b, hairStyle: 'bun', pants: 0xe9e2d0, voice: 320, height: 0.93, trim: 0xa3195b,
    tagline: 'Pune. Knows who ordered biryani on the team card. And why.',
    accessories: ['kurti', 'dupatta', 'greenBangles', 'mangalsutra'],
    face: {
      skin: 0xa8704c, hair: 0x120d0b, hairStyle: 'bun', gajra: true, forehead: 'chandrakor',
      earrings: 'stud', mouth: 'smirk', eyeSquint: 0.62, browRaise: 0.012, browTilt: 0.02, lipColor: 0x7a3434, shape: { w: 0.9, h: 1.1, d: 0.95 },
    },
  },
  rinku: {
    skin: 0xd8b08a, hair: 0x14100d, hairStyle: 'swoop', pants: 0x3b4a66, voice: 290, height: 0.98, trim: 0xb3202a,
    tagline: 'Jorhat. Three weeks in. Still believes in all of it.',
    accessories: ['lanyard', 'backpack', 'gamosaStrap'],
    face: {
      skin: 0xd8b08a, hair: 0x14100d, hairStyle: 'crop', mouth: 'open', eyeSize: 1.1, catchlight: true, browTilt: -0.14, browThickness: 0.9,
      shape: { w: 0.93, h: 1.05, d: 0.95 }, noseLength: 0.85,
    },
  },
  priya: {
    skin: 0x9a6a4a, hair: 0x100c0a, hairStyle: 'bun', pants: 0xf1e9d6, voice: 260, height: 0.97, trim: 0xc9a24a,
    tagline: 'Thrissur. Has a form for this. Has a form for the form.',
    accessories: ['kurti', 'clipboard', 'watch'],
    face: {
      skin: 0x9a6a4a, hair: 0x100c0a, hairStyle: 'braid', forehead: 'chandanam', earrings: 'jhumka',
      glasses: 'black', mouth: 'neutral', browTilt: 0.12, shape: { w: 0.9, h: 1.1, d: 0.96 },
    },
  },
  rohit: {
    skin: 0xc2946c, hair: 0x1b1411, hairStyle: 'short', pants: 0x2b3345, voice: 190, height: 1.06,
    tagline: 'Ludhiana. Will tell everyone about you. Already has.',
    accessories: ['halfSleeve', 'belt', 'watch', 'kara'],
    face: {
      skin: 0xc2946c, hair: 0x1b1411, hairStyle: 'spiky', facialHair: 'beard', mouth: 'smirk',
      browThickness: 1.6, browTilt: 0.1, shape: { w: 0.96, h: 1.06, d: 0.98 },
    },
  },
  srinivas: {
    skin: 0x7a4e33, hair: 0x1a1512, hairStyle: 'cap', pants: 0x2f3a2c, voice: 130, height: 1.02,
    tagline: 'Secunderabad. Knows every access card and every face. Nobody asks.',
    accessories: ['clipboard', 'kalava', 'belt'],
    face: {
      skin: 0x7a4e33, hair: 0x1a1512, hairStyle: 'crop', hat: 'securityCap', facialHair: 'thickMoustache', mouth: 'neutral',
      browThickness: 1.3, shape: { w: 0.97, h: 1.04, d: 0.98 },
    },
  },
  lakshmi: {
    skin: 0x8e5c3e, hair: 0x15100d, hairStyle: 'bun', pants: 0x8f4a3a, voice: 240, height: 0.92, trim: 0xd4af37,
    tagline: 'Warangal. Twenty-two years in the canteen. Knows everyone\'s order.',
    accessories: ['kurti', 'apron', 'goldBangles', 'mangalsutra'],
    face: {
      skin: 0x8e5c3e, hair: 0x15100d, hairStyle: 'bun', greyTemples: true, forehead: 'bindi', noseRing: true,
      mouth: 'smile', shape: { w: 0.95, h: 1.05, d: 0.96 },
    },
  },
  deepak: {
    skin: 0x9c6b48, hair: 0x16110e, hairStyle: 'short', pants: 0x2a2a2a, voice: 210, height: 1.0, trim: 0xd62828,
    tagline: 'Mangaluru. Takes the credit, gives the tickets.',
    accessories: ['lanyard', 'watch', 'belt'],
    face: {
      skin: 0x9c6b48, hair: 0x16110e, hairStyle: 'sidepart', facialHair: 'stubble', glasses: 'black', mouth: 'smirk', browRaise: 0.01,
      shape: { w: 0.95, h: 1.06, d: 0.97 },
    },
  },
  sanjay: {
    skin: 0xb88660, hair: 0x2a2320, hairStyle: 'short', pants: 0x2c3a55, voice: 165, height: 1.04,
    tagline: 'Kolkata, via Dallas. Mostly via Dallas.',
    accessories: ['blazer', 'tie', 'watch'],
    face: {
      skin: 0xb88660, hair: 0x2a2320, hairStyle: 'receding', greyTemples: true, glasses: 'black', mouth: 'tired',
      shape: { w: 0.98, h: 1.04, d: 0.98 },
    },
  },
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
