import type { DialogueNode, Outcome } from './types';

// Openers are chosen by specificity: a node whose `who` is the NPC id and whose
// `when` contexts all match beats an archetype node, which beats 'any'.
// Characters reference Office Memory ('heard_before', 'caught_before') because the
// running gags are what make the office feel like it remembers you.
// Home-language words are sprinkled lightly: one per line at most, meaning clear from context.

export const OPENERS: DialogueNode[] = [
  // --- Kavita (Pune, admin, gossip) ---
  { who: 'kavita', lines: [
    "Arre, where are we going? Don't say 'nowhere', I'll know.",
    "There you are! I was just telling Lakshmi about you. Nothing bad. Mostly.",
    "You have that look. The 'leaving early' look. Tell me everything, ho na?",
  ] },
  { who: 'kavita', when: ['crouching'], lines: [
    "Were you just… crawling? Past my desk? This is going in the WhatsApp group.",
  ] },
  { who: 'kavita', when: ['heard_before'], lines: [
    "Let me guess, another emergency? I remember the last one. I remember all of them.",
  ] },
  { who: 'kavita', when: ['caught_before'], lines: [
    "After last time? Ramesh sir mentioned it. Well. I mentioned it to Ramesh sir.",
  ] },
  { who: 'kavita', when: ['near_exit'], lines: [
    "Going already? It's not even two. I'm not judging. I'm noting.",
  ] },

  // --- Rinku (Jorhat, fresher) ---
  { who: 'rinku', lines: [
    "Sir, going somewhere? Should I also come? I'll sit quietly only.",
    "Sir, what are you working on? I'm asking everyone. For learning purpose.",
    "Sir, you're leaving? Leaving is allowed? They didn't tell in induction.",
  ] },
  { who: 'rinku', when: ['crouching'], lines: [
    "Sir, fire drill is happening? Should I also sit down? I'm sitting down.",
  ] },
  { who: 'rinku', when: ['heard_before'], lines: [
    "Oh! You're the one from before! I wrote your excuse in my notebook. For learning.",
  ] },

  // --- Ramesh (Chennai, senior manager) ---
  { who: 'ramesh', lines: [
    "One minute, ma. Walk with me. Actually, stand with me. Where are we going?",
    "There's my rockstar. Small doubt, ma. Where are you going, location-wise?",
    "You look like someone with a vision. Is the vision the lift?",
  ] },
  { who: 'ramesh', when: ['crouching'], lines: [
    "Did you drop something? Your commitment, maybe? Ha. Joking. Unless.",
  ] },
  { who: 'ramesh', when: ['caught_before'], lines: [
    "We spoke about this. We had a whole connect. There was a whiteboard.",
  ] },
  { who: 'ramesh', when: ['near_exit'], lines: [
    "Lift, aa? Bold. I like bold. I don't like unscheduled bold.",
  ] },

  // --- Priya (Thrissur, HR) ---
  { who: 'priya', lines: [
    "Hi. Quick check-in. This is not a formal conversation. It will be documented informally.",
    "Hello. Just making sure everyone is where their Outlook calendar says they are.",
    "Do you have a moment? You do. I checked your calendar.",
  ] },
  { who: 'priya', when: ['crouching'], lines: [
    "Kindly stand up and explain why you were not standing up.",
  ] },
  { who: 'priya', when: ['heard_before'], lines: [
    "I have your last excuse on file. Would you like to add an addendum?",
  ] },
  { who: 'priya', when: ['caught_before'], lines: [
    "Ah. We've met. Professionally. There is a folder.",
  ] },

  // --- Rohit (Ludhiana, the snitch) ---
  { who: 'rohit', lines: [
    "Oye, oye. Where do you think you're going? And can I also come?",
    "Sneaking out? Classic. I'll tell Ramesh sir. Unless you tell me how.",
    "Bhai, it's the middle of the day. Some of us have to be here. Me. I have to be here.",
  ] },
  { who: 'rohit', when: ['near_exit'], lines: [
    "Going UP? Nobody goes up. What's up there? Who's up there?",
  ] },

  // --- Srinivas (Secunderabad, security) ---
  { who: 'srinivas', lines: [
    "Saar, everything okay? You're walking like a man with somewhere to be.",
    "Exit is that side. Not that you need it. At this time.",
  ] },
  { who: 'srinivas', when: ['crouching'], lines: [
    "You're checking the floor? That's my job, saar. You want my job?",
  ] },

  // --- Lakshmi (Warangal, cafeteria) ---
  { who: 'lakshmi', lines: [
    "Tinnara? You didn't eat, no? Where are you running without eating?",
    "Ah, you. The one who takes two chapatis and says one. Going where?",
  ] },
  { who: 'lakshmi', when: ['heard_before'], lines: [
    "Kavita told me about your last emergency. All of us were very worried. For two minutes.",
  ] },

  // --- Deepak (Mangaluru, TL) ---
  { who: 'deepak', lines: [
    "Guru! Perfect timing. Small update? Also, where are you going?",
    "You look like you're escaping. Before you go, can you just close three tickets?",
  ] },

  // --- Sanjay (Kolkata via Dallas, director) ---
  { who: 'sanjay', lines: [
    "Ki holo? In Dallas, nobody walks this fast unless there's a fire drill.",
    "Hey, buddy. Quick one. In my previous company in Dallas, we'd call this 'disengagement'.",
  ] },

  // --- Archetype fallbacks ---
  { who: 'boss', lines: ["Got a minute? Everyone's got a minute. That's the beauty of minutes."] },
  { who: 'gossip', lines: ["Ooh, where are you going? Tell me. I'll only tell a few people."] },
  { who: 'chatty', lines: ["Arre, hi! Sorry. Hi. Where are you going?"] },
  { who: 'hr', lines: ["A quick word. Everything is fine. That's what I'm here to confirm."] },
  { who: 'intern', lines: ["Hi! Sorry. Are you allowed to be over here?"] },
  { who: 'security', lines: ["Namaste, saar. Where are we going?"] },
  { who: 'coworker', lines: ["Arre, where are you going?"] },

  // --- Context fallbacks (any NPC) ---
  { who: 'any', when: ['voluntary'], lines: ["Oh, hi. You wanted to talk to me? Nobody wants to talk to me."] },
  { who: 'any', when: ['sprinting'], lines: ["Why are you running? Fire is there? Should I also run?"] },
  { who: 'any', when: ['probe_violation'], lines: ["One minute. That's not the way to your desk. That's the way to the door."] },
];

/** Reactions after the verdict. Keyed by NPC id, then outcome. Falls back to archetype, then 'any'. */
export const REACTIONS: Record<string, Partial<Record<Outcome, string[]>>> = {
  kavita: {
    PASSED: ["Arre deva, poor thing. Go, go. I'll tell everyone. Nicely.", "That is the most interesting thing today. Go."],
    PROBED: ["Chal, I'll walk with you. I was going that side anyway. I'm always going that side."],
    ESCORTED: ["Come, I'll drop you to your desk. You look pale. I'll tell you about my kitchen renovation."],
    CAUGHT: ["Arre. Ramesh sir will want to hear this. From me. Right now."],
  },
  rinku: {
    PASSED: ["Ho jayega sir, don't worry. Aap jaao, I'll manage!", "Tension mat lo, sir. If anyone asks, you're on a call. Which call? I'll manage."],
    PROBED: ["Sir, I'll also come till the lift. For learning purpose."],
    ESCORTED: ["Sir, come, I'll drop you to your desk. Team player, no? Deepak sir told me."],
    CAUGHT: ["Sorry sir, I have to tell Ramesh sir. It's in the induction deck. Slide forty."],
  },
  ramesh: {
    PASSED: ["Good initiative. Go. Circle back.", "Say no more. Actually, put it in a mail. Go."],
    PROBED: ["Great. I'll walk with you. I love a walk-and-talk. I'll talk. You walk."],
    ESCORTED: ["Come, champ, back to your desk. We'll brainstorm on the way. Out loud."],
    CAUGHT: ["Let's grab a room. Quick connect. It won't be quick."],
  },
  priya: {
    PASSED: ["Understood. I'll make a note that I made no note.", "That's covered under policy. Go."],
    PROBED: ["I'll accompany you. For support. Documented support."],
    ESCORTED: ["Let's walk back to your workstation. I'll explain the attendance policy. Both volumes."],
    CAUGHT: ["I'll need you to come with me. Bring nothing. We have pens."],
  },
  rohit: {
    PASSED: ["Haha, fine. Legend. Bring me back a samosa.", "Okay, that's actually good. I'm stealing it, bhai."],
    PROBED: ["Nahi yaar, I'm walking with you. If you're escaping, I'm witnessing."],
    ESCORTED: ["Back to the desk, paaji. If I suffer, everyone suffers."],
    CAUGHT: ["RAMESH SIR! Sir, one minute! Come and see this!"],
  },
  srinivas: {
    PASSED: ["Hau, saar, go. These house owners, no? Go.", "Okay, saar. Wet floor near the lift. Always wet."],
    PROBED: ["I'll walk with you, saar. That side I have to check anyway. Always checking."],
    ESCORTED: ["Come, saar, back to your seat. I'll tell you about the fire exit alarm."],
    CAUGHT: ["I have to inform on the walkie, saar. I have a walkie. I love using it."],
  },
  lakshmi: {
    PASSED: ["Fine, go. But take this banana. You didn't eat.", "Okay, okay. Go. Come back for the pesarattu."],
    PROBED: ["I'll walk with you. My legs need it. Twenty-two years standing behind that counter."],
    ESCORTED: ["Back you go. I'll walk you. I'll tell you what Ramesh sir eats."],
    CAUGHT: ["Sorry, nanna. Rules are rules. I didn't make them. I laminated them."],
  },
  deepak: {
    PASSED: ["Okay, guru, go. But update the ticket before you go. Joking. Not joking.", "Genuine reason, guru. Go, go. I'll handle standup."],
    PROBED: ["I'll walk with you. I'm technically in a meeting. The meeting is walking."],
    ESCORTED: ["Let me drop you back. We can discuss your tickets. All eleven."],
    CAUGHT: ["Sorry, guru. I have to escalate. I don't like escalating. I'm very good at it."],
  },
  sanjay: {
    PASSED: ["Fine. In Dallas we'd need a JIRA for this. Go.", "Okay. I respect that. Very Dallas of you."],
    PROBED: ["I'll walk with you. In Dallas we called this 'alignment'."],
    ESCORTED: ["Let's get you back to your desk. On the way I'll tell you about Dallas."],
    CAUGHT: ["This is going to Ramesh. And to me. I'm above Ramesh."],
  },
  any: {
    PASSED: ["Oh. Okay. Go."],
    PROBED: ["I'll walk with you a little."],
    ESCORTED: ["Let's get you back to your desk."],
    CAUGHT: ["I think you should talk to Ramesh sir."],
  },
};

/** Said when the player times out and blurts. The excuse itself follows. */
export const PANIC_PREFIX = ['Uh — ', 'Sir, actually — ', 'So basically what happened is — ', 'Actually, sir — '];

/** Generic deflections offered next to the real pivot line in the follow-up round. */
export const DEFLECTIONS = [
  'New haircut, sir? Very sharp.',
  "Can we take this offline, sir? Tomorrow?",
  "Good question. I'll revert on that.",
  "I'd rather not discuss at work, sir.",
];

/** The bad pivot option. Always offered; always a little tragic. */
export const FLOUNDERS = ['Um.', '…Yes, sir.', "I don't… I don't know.", 'Sorry, what was the question?'];

/** Barks for state transitions (shown in speech bubbles). */
export const STATE_BARKS = {
  suspicious: ['Hm?', 'Who is that?', 'Kaun hai?', 'Hello?', 'Enti?'],
  investigate: ['I could have sworn…', 'Something is fishy.', 'Must be the AC.', 'Nothing only.'],
  confront: ['Oye! One minute!', 'Oh, hi! Wait wait!', 'Ek minute!', 'Hello, you. Hi.'],
  chase: ['Arre, why are you RUNNING?', 'HEY! No running on the floor!', 'Stop! There is a no-running policy!'],
  ignored: ["Hello? HELLO? I'm talking to you only!", 'Excuse me? EXCUSE me?', "Don't walk away from a sync!"],
  giveUp: ['…Okay then.', 'Where did they go?', 'Fine. I saw nothing. I saw everything.'],
  tomSnitch: ["PRIYA MA'AM! Someone is going UP!", 'Sir! SIR! Somebody is sneaking!'],
  jam: ['Oh, not again.', 'Is the printer… screaming?', 'Who jammed it? WHO JAMMED IT?'],
  allHands: ['Town hall? Now?', 'Oh, the town hall.', 'Is it mandatory? It says mandatory.'],
  probeDone: ['Okay. See you.', 'Okay, I believe you. Mostly.'],
};

/** Chatter pairs: when two NPCs meet on patrol they trade these. */
export const CHATTER = [
  ['Did you see the mail?', 'Only the subject line. Enough tension.'],
  ['Appraisal letters are coming this week?', 'They said that in March also.'],
  ['Who took my tiffin?', 'Kavita knows. Kavita always knows.'],
  ['Lunch? Biryani?', 'Only if someone else is paying.'],
  ['How was traffic?', 'ORR was a parking lot. I read a whole novel.'],
  ['Town hall is mandatory?', 'Physically, yes.'],
];

/** Pieces for the HR incident report on the Caught screen. */
export const HR_REPORT = {
  recommendations: [
    "Mandatory completion of the 'Ownership Mindset' e-learning (4 hrs, cannot be skipped, video cannot be muted).",
    'Relocation to the desk beside the pantry microwave.',
    'Participation in Annual Day dance practice (Thursdays, 6 PM, attendance tracked).',
    'Daily status mail to Ramesh sir, cc Deepak, bcc the entire floor.',
    'A calendar hold titled "Quick Connect :)" recurring daily at 6:45 PM, indefinitely.',
    'Removal from the team lunch WhatsApp group. (Kavita has been informed.)',
    'Revocation of WFH privileges pending review.',
  ],
  observations: [
    'Employee was observed checking Uber prices at 12:58 PM.',
    'Employee made sustained eye contact with the lift.',
    'Employee walked with what witnesses described as "purpose."',
    'Employee was observed looking at the clock with visible hope.',
    "Employee's chair was found pushed in, suggesting premeditation.",
    'Employee closed eleven browser tabs in under four seconds when approached.',
  ],
  archetypeNotes: {
    boss: 'Reporting party has requested a follow-up connect to discuss "the vibe."',
    gossip: 'Reporting party has already informed 11 colleagues and 2 WhatsApp groups. Correction: 3.',
    chatty: 'Reporting party asked if this could be a Teams call instead.',
    hr: 'Reporting party is also the author of this report. There was no conflict of interest.',
    intern: 'Reporting party asked whether they "did a good job." They did.',
    security: 'Reporting party used the walkie-talkie. Twice. Once to say "copy."',
    coworker: 'Reporting party said they "were not going to say anything" and then said everything.',
  } as Record<string, string>,
};

export function pick<T>(arr: readonly T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}
