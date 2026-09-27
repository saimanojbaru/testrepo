import type { DialogueNode, Outcome } from './types';

// Openers are chosen by specificity: a node whose `who` is the NPC id and whose
// `when` contexts all match beats an archetype node, which beats 'any'.
// Characters reference Office Memory ('heard_before', 'caught_before') because the
// running gags are what make the office feel like it remembers you.

export const OPENERS: DialogueNode[] = [
  // --- Brenda ---
  { who: 'brenda', lines: [
    "Oh! Hi, hon. Where are we off to? Don't say 'nowhere', I'll know.",
    "There you are. I was just telling Linda about you. Nothing bad. Mostly.",
    "Sweetie, you've got that look. The 'leaving' look. Talk to me.",
  ] },
  { who: 'brenda', when: ['crouching'], lines: [
    "Were you just… crawling? Past my desk? Oh, this is going in the newsletter.",
  ] },
  { who: 'brenda', when: ['heard_before'], lines: [
    "Let me guess, another emergency? I remember the last one. I remember all of them.",
  ] },
  { who: 'brenda', when: ['caught_before'], lines: [
    "Oh, you. After last time? Marcus mentioned it. Well, I mentioned it to Marcus.",
  ] },
  { who: 'brenda', when: ['near_exit'], lines: [
    "Heading out, are we? It's not even two. I'm not judging. I'm noting.",
  ] },

  // --- Dev ---
  { who: 'dev', lines: [
    "Hey! Hey. Are you going somewhere cool? Can I come? I'll be quiet.",
    "Oh hi! I'm doing a thing where I ask everyone what they're working on. What are you working on?",
    "Wait, are you leaving? Is leaving allowed? Nobody tells me the rules.",
  ] },
  { who: 'dev', when: ['crouching'], lines: [
    "Whoa, is this a drill? Should I also be crouching? I'm crouching.",
  ] },
  { who: 'dev', when: ['heard_before'], lines: [
    "Oh! You're the one from before! I wrote your excuse down. For learning.",
  ] },

  // --- Marcus ---
  { who: 'marcus', lines: [
    "Hey, buddy. Walk with me. Actually, stand with me. Where are we headed?",
    "There's my rockstar. Quick question. Quick. Where's your head at, location-wise?",
    "You look like someone with a vision. Is the vision the elevator?",
  ] },
  { who: 'marcus', when: ['crouching'], lines: [
    "Were you — did you drop something? Your commitment, maybe? Ha. Kidding. Unless.",
  ] },
  { who: 'marcus', when: ['caught_before'], lines: [
    "We talked about this. We had a whole chat. There was a whiteboard.",
  ] },
  { who: 'marcus', when: ['near_exit'], lines: [
    "Elevator, huh? Bold. Love bold. Hate unscheduled bold.",
  ] },

  // --- Priya ---
  { who: 'priya', lines: [
    "Hi. Quick check-in. This is not a formal conversation. It will be documented informally.",
    "Hello. Just making sure everyone is where their calendar says they are.",
    "Do you have a moment? You do. I checked your calendar.",
  ] },
  { who: 'priya', when: ['crouching'], lines: [
    "I'm going to need you to stand up and explain why you weren't standing up.",
  ] },
  { who: 'priya', when: ['heard_before'], lines: [
    "I have your last excuse on file. Would you like to add an addendum?",
  ] },
  { who: 'priya', when: ['caught_before'], lines: [
    "Ah. We've met. Professionally. There's a folder.",
  ] },

  // --- Tom ---
  { who: 'tom', lines: [
    "Whoa whoa whoa. Where do you think you're going? And can I come?",
    "Oh, you're sneaking out? Classic. I'm telling. Unless you tell me how.",
    "Buddy. It's the middle of the day. Some of us have to be here. Me. I have to be here.",
  ] },
  { who: 'tom', when: ['near_exit'], lines: [
    "Going UP? Nobody goes up. What's up there? Who's up there?",
  ] },

  // --- Gary ---
  { who: 'gary', lines: [
    "Hey there. Everything alright? You're walking like a guy who's got somewhere to be.",
    "Help you find something? Exit's that way. Not that you'd need it. At this hour.",
  ] },
  { who: 'gary', when: ['crouching'], lines: [
    "You checking the baseboards? 'Cause that's my job. You after my job?",
  ] },

  // --- Linda ---
  { who: 'linda', lines: [
    "Oh. Going somewhere? Must be nice. I've been here since 7:40.",
    "Hi, dear. You wouldn't be leaving, would you? Before your expense report?",
  ] },
  { who: 'linda', when: ['heard_before'], lines: [
    "Brenda told me about your last emergency. We were all very concerned. Briefly.",
  ] },

  // --- Kevin ---
  { who: 'kevin', lines: [
    "Oh, hey! Perfect timing. Have you updated your password this quarter? Also where are you going?",
    "Yo. You look like you're escaping. I respect it. I'm escaping Friday. Permanently.",
  ] },

  // --- Archetype fallbacks ---
  { who: 'boss', lines: ["Got a minute? Everyone's got a minute. That's the beauty of minutes."] },
  { who: 'gossip', lines: ["Ooh, where are you going? Tell me everything. I'll only tell a few people."] },
  { who: 'chatty', lines: ["Hey! Hey. Sorry. Hi. Where are you headed?"] },
  { who: 'hr', lines: ["A quick word. Everything is fine. That's what I'm here to confirm."] },
  { who: 'intern', lines: ["Oh hi! Sorry. Are you allowed to be over here?"] },
  { who: 'security', lines: ["Afternoon. Where we headed?"] },
  { who: 'coworker', lines: ["Hey. Where are you going?"] },

  // --- Context fallbacks (any NPC) ---
  { who: 'any', when: ['voluntary'], lines: ["Oh — hi. You wanted to talk to me? Nobody wants to talk to me."] },
  { who: 'any', when: ['sprinting'], lines: ["Why are you running? Is there a fire? Is it a fun fire?"] },
  { who: 'any', when: ['probe_violation'], lines: ["Hang on. That's not the way to your desk. That's the way to the door."] },
];

/** Reactions after the verdict. Keyed by NPC id, then outcome. Falls back to archetype, then 'any'. */
export const REACTIONS: Record<string, Partial<Record<Outcome, string[]>>> = {
  brenda: {
    PASSED: ["Oh, you poor thing. Go, go. I'll tell everyone. Nicely.", "Well, that's the most interesting thing that's happened all day. Off you go."],
    PROBED: ["Mm-hm. I'll walk with you. I was going that way anyway. I'm always going that way."],
    ESCORTED: ["Let's get you back to your desk, hon. You look peaky. I'll tell you about my kitchen remodel."],
    CAUGHT: ["Oh, sweetie. Marcus is going to want to hear this. From me. Right now."],
  },
  dev: {
    PASSED: ["Oh my gosh, that's so valid. Go! I'll cover for you! I don't know how!", "Wow. You're so good at this. Is this what being senior is?"],
    PROBED: ["Cool cool cool. I'll just walk with you. For learning."],
    ESCORTED: ["I'm going to walk you back to your desk, because I think that's what a good teammate does? Right?"],
    CAUGHT: ["I have to tell Marcus. I'm so sorry. It's in the onboarding doc."],
  },
  marcus: {
    PASSED: ["Good initiative. Go. Circle back.", "Say no more. Actually, say it in an email later. Go."],
    PROBED: ["Great. I'll walk with you. I love a walk-and-talk. I'll talk. You walk."],
    ESCORTED: ["Let's get you back to your desk, champ. I'll walk you. We'll brainstorm. Out loud."],
    CAUGHT: ["Let's grab a room. Quick chat. It won't be quick."],
  },
  priya: {
    PASSED: ["Understood. I'll make a note that I made no note.", "That's covered under policy. Go."],
    PROBED: ["I'll accompany you. For support. Documented support."],
    ESCORTED: ["Let's walk back to your workstation together. I'll explain the attendance policy. Both volumes."],
    CAUGHT: ["I'm going to need you to come with me. Bring nothing. We have pens."],
  },
  tom: {
    PASSED: ["Ha. Fine. Legend. Bring me back something.", "Okay, that's actually good. I'm stealing it."],
    PROBED: ["Nah, I'm walking with you. If you're escaping, I'm witnessing."],
    ESCORTED: ["Back to the desk, pal. If I suffer, we all suffer."],
    CAUGHT: ["MARCUS! Hey Marcus! Come look at this!"],
  },
  gary: {
    PASSED: ["Say no more. Contractors, man. Go.", "Alright. Mind the wet floor sign. It's always wet."],
    PROBED: ["I'll walk you. Gotta check that area anyway. Always checking."],
    ESCORTED: ["Let's get you back to your spot. I'll tell you about the ceiling tiles."],
    CAUGHT: ["I'm gonna have to radio this one in. I have a radio. I love using it."],
  },
  linda: {
    PASSED: ["Fine. Go. I'm not going to remember this. I'm going to remember this.", "Oh, well, that's different. Take a banana bread."],
    PROBED: ["I'll walk with you. My legs need it. Twenty-two years of sitting."],
    ESCORTED: ["Back you go, dear. I'll walk you. I have stories about 2009."],
    CAUGHT: ["I'm sorry, dear. Rules are rules. I didn't make them. I did laminate them."],
  },
  kevin: {
    PASSED: ["Respect. Go. Your secret dies with me Friday.", "Valid. Extremely valid. Go."],
    PROBED: ["I'll walk with you. I'm technically on a ticket. The ticket is walking."],
    ESCORTED: ["Let me walk you back. Your monitor's been flickering anyway. We can talk about it."],
    CAUGHT: ["Ugh. I have to escalate this. I hate escalating. Tickets and people."],
  },
  any: {
    PASSED: ["Oh. Okay. Go ahead."],
    PROBED: ["I'll walk with you a bit."],
    ESCORTED: ["Let's get you back to your desk."],
    CAUGHT: ["I think you should talk to Marcus."],
  },
};

/** Said when the player times out and blurts. The excuse itself follows. */
export const PANIC_PREFIX = ['Uh — ', 'I — um — ', 'So the thing is — ', 'Okay, so — '];

/** Generic deflections offered next to the real pivot line in the follow-up round. */
export const DEFLECTIONS = [
  "Hey, is that a new haircut? It's working.",
  "Can we circle back on that? Like, tomorrow?",
  "Great question. Let me take that offline.",
  "I'd rather not get into it at work.",
];

/** The bad pivot option. Always offered; always a little tragic. */
export const FLOUNDERS = ['Um.', "…Yes.", "I don't — I don't know.", 'What was the question?'];

/** Barks for state transitions (shown in speech bubbles). */
export const STATE_BARKS = {
  suspicious: ['Hm?', 'Who\'s there?', 'Was that…?', 'Hello?', 'Huh.'],
  investigate: ['I could have sworn…', 'Weird.', 'Must be the building.', 'Probably nothing.'],
  confront: ['Hey! Got a sec?', 'Oh — hi! Hold on!', 'Wait up!', "Hey, you. Hi."],
  chase: ['Are you RUNNING?', 'HEY! Nobody runs here!', 'Stop! We have a no-running policy!'],
  giveUp: ['…Okay then.', 'Where did they go?', 'Fine. I saw nothing. I saw everything.'],
  tomSnitch: ['PRIYA! Someone\'s going UP!', 'Hey! HEY! Somebody\'s sneaking!'],
  jam: ['Oh, not again.', 'Is the copier… screaming?', 'Who jammed it? WHO JAMMED IT?'],
  allHands: ['All-hands? Now?', 'Oh, the all-hands.', 'Is it mandatory? It says mandatory.'],
  probeDone: ['Alright. See you around.', 'Okay, I believe you. Mostly.'],
};

/** Chatter pairs: when two NPCs meet on patrol they trade these. */
export const CHATTER = [
  ['Did you see the email?', 'I saw the subject line. That was enough.'],
  ['Is it Thursday?', 'It feels like a Thursday. Spiritually.'],
  ['Who took my yogurt?', 'Brenda knows. Brenda always knows.'],
  ['Are you going to the all-hands?', 'Physically, yes.'],
  ['This hum is getting to me.', 'Lean into it. Become the hum.'],
];

/** Pieces for the HR incident report on the Caught screen. */
export const HR_REPORT = {
  recommendations: [
    "Mandatory completion of 'Time Is A Shared Resource' (4 hrs, unskippable).",
    'Relocation to the desk beside the printer that clicks.',
    'Enrollment in the Thursday Fun Committee (attendance tracked).',
    'A calendar hold titled "Check-in :)" recurring daily, indefinitely.',
    'Removal from the birthday-card envelope rotation. (Brenda has been informed.)',
    'One-on-one with Marcus. Topic: "Presence." Duration: TBD.',
    'Revocation of stairwell privileges pending review.',
  ],
  observations: [
    'Employee displayed a heightened interest in exits.',
    'Employee made sustained eye contact with the elevator.',
    'Employee walked with what witnesses described as "purpose."',
    'Employee was observed checking the clock with visible hope.',
    'Employee\'s chair was found pushed in, suggesting premeditation.',
  ],
  archetypeNotes: {
    boss: 'Reporting party has requested a follow-up sync to discuss "the vibe."',
    gossip: 'Reporting party has already informed 11 colleagues. Correction: 14.',
    chatty: 'Reporting party asked if this report could be "a group chat instead."',
    hr: 'Reporting party is also the author of this report. There was no conflict of interest.',
    intern: 'Reporting party asked whether they "did a good job." They did.',
    security: 'Reporting party used the radio. Twice. Once to say "copy."',
    coworker: 'Reporting party stated they "weren\'t going to say anything" and then said everything.',
  } as Record<string, string>,
};

export function pick<T>(arr: readonly T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}
