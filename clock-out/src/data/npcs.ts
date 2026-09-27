import type { NPCDef, NPCLook } from './types';

// The cast. Each person should be recognisable from the top of their head over a
// cubicle divider (silhouette) and from two seconds of their voice blips (pitch).
// Barks are the ambient lines they say to nobody in particular; they are the main
// way the player gets to know them, so every line should reveal something human.

export const NPC_DEFS: Record<string, NPCDef> = {
  brenda: {
    id: 'brenda',
    name: 'Brenda Kowalski',
    role: 'Office Manager',
    color: 0x9a5f7a, // mauve cardigan
    visionRange: 13,
    visionFov: 110,
    hearingMul: 1.3, // hears everything; that is her whole thing
    walkSpeed: 1.05,
    talkativeness: 0.9,
    archetype: 'gossip',
    barks: [
      "Someone microwaved fish again. I have a list. It's a short list.",
      "Did you sign Kevin's card? He's leaving. I'm not supposed to know why. It's the fax thing.",
      "I'm not gossiping. I'm keeping people informed.",
      "Marcus had two yogurts today. Two. Something's going on at home.",
      "Five dollars for the card envelope. It's for a gift. The gift is a bigger card.",
      "My sister says I should retire. I say, and do what? Not know things?",
      "Linda and Gary took the same lunch. Same forty minutes. I'm just saying the numbers.",
      "I water the plants because nobody else does. They're the only ones who listen.",
      "If you hear a rumor about me, it's because I started it to see where it'd go.",
    ],
  },
  dev: {
    id: 'dev',
    name: 'Dev Okafor',
    role: 'Summer Associate (Week 3)',
    color: 0x5a8a6a, // company hoodie, green, still creased from the bag
    visionRange: 12,
    visionFov: 100,
    hearingMul: 1.0,
    walkSpeed: 1.35,
    talkativeness: 1.0,
    archetype: 'intern',
    barks: [
      "Do you know where they keep the good pens? The ones that click?",
      "I made a Notion page for our Notion pages. People seem worried.",
      "Is it weird that I love it here? Everyone looks at me like it's weird.",
      "My manager said 'circle back' so I've been walking in circles. Is that right?",
      "I put 'synergy' in an email unironically and I felt something change in me.",
      "Free LaCroix. FREE LaCroix. Do you guys know about this?",
      "I'm shadowing whoever looks busiest. Right now that's the printer.",
      "My mom asks what I do all day and honestly? Same.",
    ],
  },
  marcus: {
    id: 'marcus',
    name: 'Marcus Thorne',
    role: 'Director of Operational Excellence',
    color: 0x2c3a55, // navy suit, the only suit on the floor
    visionRange: 18,
    visionFov: 95,
    hearingMul: 0.8, // noise-cancelling headphones around his neck, always
    walkSpeed: 1.25,
    talkativeness: 0.7,
    archetype: 'boss',
    barks: [
      "Let's take this offline. Actually, let's take it online. Let's take it everywhere.",
      "Quick sync. Five minutes. Maybe forty. Bring a snack.",
      "I'm not a boss, I'm a coach. And your coach says: stay.",
      "Love the energy. Love it. Where's the deck, though?",
      "My door is always open. Physically. I had it removed.",
      "Good initiative. I say that to everyone. It still means something.",
      "Nobody leaves before the leader. That's leadership. I read it.",
      "Does anyone else hear that hum? No? Good. Great. Great culture.",
    ],
  },
  priya: {
    id: 'priya',
    name: 'Priya Raman',
    role: 'HR Business Partner',
    color: 0x3b3b44, // charcoal blazer, crease you could slice bread with
    visionRange: 15,
    visionFov: 100,
    hearingMul: 1.2,
    walkSpeed: 1.4,
    talkativeness: 0.8,
    archetype: 'hr',
    barks: [
      "Friendly reminder: 'friendly reminder' is a formal warning.",
      "I'm not here to judge. I'm here to document. Judging is a different team.",
      "Has everyone done the Respecting Time module? It's four hours.",
      "My clipboard has never once been empty. I have a clipboard for my clipboard.",
      "We're a family here. Legally, we are not a family. But culturally.",
      "I can smell an unused PTO balance from across the floor.",
      "The fun committee meets Thursdays. Attendance is optional. It is tracked.",
      "I went into HR to help people. Now I help paperwork help people.",
    ],
  },
  tom: {
    id: 'tom',
    name: 'Tom Becker',
    role: 'Senior Associate (Since 2019)',
    color: 0x7f8f5f, // khaki polo, company logo half peeled off
    visionRange: 16,
    visionFov: 90,
    hearingMul: 1.0,
    walkSpeed: 1.2,
    talkativeness: 0.6,
    archetype: 'coworker',
    barks: [
      "I'm not saying I'm leaving. But my LinkedIn is very up to date.",
      "Fantasy draft is Thursday. You're in. You don't get a say. You're in.",
      "Four o'clock. Four o'clock. It's never four o'clock.",
      "If anyone sneaks out early, I'm telling. Not because I care. Because I can't.",
      "I've had the same sticky note on my monitor for three years. It says 'quit'.",
      "Went to the bathroom for eleven minutes. Best part of my day.",
      "Marcus called me 'buddy' today. I've been thinking about it all morning.",
    ],
  },
  gary: {
    id: 'gary',
    name: 'Gary Dunn',
    role: 'Facilities & Security',
    color: 0x51606f, // work shirt, name patch
    visionRange: 14,
    visionFov: 105,
    hearingMul: 1.1,
    walkSpeed: 1.3,
    talkativeness: 0.5,
    archetype: 'security',
    barks: [
      "Thermostat's locked at 68. Don't ask me. I'm just the guy with the key.",
      "Somebody put a fork in the vending machine. Again. I know it was a fork.",
      "Every ceiling tile has a number. I know all of them. Nobody asks.",
      "That hum? That's the building. The building is tired too.",
      "Badge reader on six is haunted. I've made my peace with it.",
      "Twenty-one years. Never once took the stairs up. Stairs are for leaving.",
    ],
  },
  linda: {
    id: 'linda',
    name: 'Linda Park',
    role: 'Accounts Payable',
    color: 0xb08a4a, // mustard sweater set
    visionRange: 14,
    visionFov: 100,
    hearingMul: 1.0,
    walkSpeed: 1.25,
    talkativeness: 0.7,
    archetype: 'coworker',
    barks: [
      "Expense reports are due Friday. I say that with love. And a deadline.",
      "My desk calendar is from 2009. The days line up again this year. I knew they would.",
      "Twenty-two years. I've outlasted four CEOs and one ficus.",
      "Nobody leaves early on my watch. Not because of rules. Because I can't, so.",
      "I brought banana bread. It's in the kitchen. Don't take the corner piece.",
      "Receipts. Receipts. Everybody wants to be paid. Nobody wants to show receipts.",
    ],
  },
  kevin: {
    id: 'kevin',
    name: 'Kevin Hsu',
    role: 'IT Support',
    color: 0x4a4a6a, // black band t-shirt under a lanyard
    visionRange: 12,
    visionFov: 100,
    hearingMul: 1.1,
    walkSpeed: 1.3,
    talkativeness: 0.85,
    archetype: 'chatty',
    barks: [
      "Have you tried turning it off and on again? I'm asking about your week.",
      "Someone's been printing on the color printer. I can see everything.",
      "I'm leaving Friday. Brenda already knows. Brenda knew before I did.",
      "The Wi-Fi password is the name of Marcus's boat. He does not have a boat.",
      "Server room's the only quiet place. I eat lunch with the servers. They get me.",
      "If your screen goes blue, that's a feature. Of my life.",
    ],
  },
};

export const NPC_LOOKS: Record<string, NPCLook> = {
  brenda: {
    skin: 0xe8c3a6, hair: 0xb7773e, hairStyle: 'big', pants: 0x4a3f44,
    accessories: ['cardigan', 'glasses', 'mug', 'pearls'], voice: 330,
    tagline: 'Runs the birthday-card envelope. Runs the floor.', height: 0.94,
  },
  dev: {
    skin: 0x6f4a33, hair: 0x1c1512, hairStyle: 'swoop', pants: 0x3b4a66,
    accessories: ['lanyard', 'backpack'], voice: 290,
    tagline: 'Three weeks in. Still believes in all of it.', height: 1.02,
  },
  marcus: {
    skin: 0xd9ad8c, hair: 0x6e6a66, hairStyle: 'bald', pants: 0x2c3a55,
    accessories: ['tie', 'headset'], voice: 150,
    tagline: 'Believes meetings are a form of self-care.', height: 1.08,
  },
  priya: {
    skin: 0xa8765a, hair: 0x14100e, hairStyle: 'bun', pants: 0x2b2b30,
    accessories: ['clipboard', 'glasses'], voice: 260,
    tagline: 'Has a form for this. Has a form for the form.', height: 0.97,
  },
  tom: {
    skin: 0xf0cfb4, hair: 0xa0703a, hairStyle: 'short', pants: 0x6b6250,
    accessories: ['headset'], voice: 175,
    tagline: 'About to quit since 2019. Will tell everyone about you.', height: 1.0,
  },
  gary: {
    skin: 0xc79a7a, hair: 0x3a3a3a, hairStyle: 'cap', pants: 0x3a3f36,
    accessories: ['toolbelt', 'clipboard'], voice: 130,
    tagline: 'Knows where every ceiling tile goes. Nobody asks.', height: 1.04,
  },
  linda: {
    skin: 0xe2bb98, hair: 0x2a2320, hairStyle: 'bob', pants: 0x514536,
    accessories: ['glasses', 'pearls', 'mug'], voice: 240,
    tagline: 'Twenty-two years in Accounts Payable. Has seen things.', height: 0.93,
  },
  kevin: {
    skin: 0xe6c29c, hair: 0x101010, hairStyle: 'short', pants: 0x2a2a2a,
    accessories: ['lanyard', 'glasses', 'backpack'], voice: 210,
    tagline: 'IT. Leaving Friday. Emotionally left in March.', height: 1.0,
  },
};

/** NPCs the Director is allowed to bring in as wanderers, in preference order. */
export const WANDERER_POOL = ['kevin', 'gary', 'linda', 'dev'];

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
