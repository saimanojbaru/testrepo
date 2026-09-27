import type { ExcuseDef } from './types';

// Tag vocabulary understood by ExcuseRegistry:
//   repeatable    - reuse penalty is halved (people forgive coffee)
//   needs_detail  - NPC always asks the followUp
//   near_kitchen / near_exit / near_copier - context fit: offered more often there,
//                   and costs +2 when used far from that context
//   funny / believable / legend - flavor; 'legend' excuses become trophies on success
//
// Every excuse has a followUp probe and a pivotLine recovery. Pivot lines are
// deliberately a little worse than the excuse: recovering is survival, not style.

export const EXCUSES: ExcuseDef[] = [
  // --- The spec's fifteen, verbatim ---
  {
    id: 'car', category: 'domestic', risk: 2, tags: ['believable', 'near_exit'],
    text: "My car's being towed. I can see the truck from the window.",
    followUp: 'Which window? We face the parking garage.',
    pivotLine: "The garage has a window. For trucks. I'll explain later.",
  },
  {
    id: 'casserole', category: 'domestic', risk: 3, tags: ['funny'],
    text: 'I have a casserole in the oven.',
    followUp: 'What kind of casserole?',
    pivotLine: 'The kind with a timer. That is going off. Right now.',
  },
  {
    id: 'migraine', category: 'medical', risk: 1, tags: ['believable'],
    text: "I think I'm getting a migraine. Light sensitivity.",
    followUp: "You're not even squinting.",
    pivotLine: "I'm squinting internally. It's worse.",
  },
  {
    id: 'dentist', category: 'corporate', risk: 1, tags: ['believable'],
    text: 'Dentist. Emergency root canal.',
    followUp: 'Which dentist?',
    pivotLine: 'Dr. — the one with the fish tank. You know the one.',
  },
  {
    id: 'dog', category: 'domestic', risk: 3, tags: ['funny'],
    text: 'My dog locked my roommate out on the balcony.',
    followUp: 'How does a dog lock a door?',
    pivotLine: "He's a very smart dog. That's actually the problem.",
  },
  {
    id: 'goldfish', category: 'absurd', risk: 5, tags: ['funny', 'legend'],
    text: 'My goldfish is drowning.',
    followUp: "…Fish can't drown.",
    pivotLine: "That's what they said about the Titanic.",
  },
  {
    id: 'crush', category: 'romantic', risk: 4, tags: ['needs_detail'],
    text: "I'm meeting someone. It's… complicated.",
    followUp: 'Complicated how? Do I know them?',
    pivotLine: "You'd love them. I can't say more. I've said too much.",
  },
  {
    id: 'sync', category: 'corporate', risk: 2, tags: ['believable'], immuneArchetypes: ['boss'],
    text: 'I have a sync with another team. Different floor.',
    followUp: 'Which team? I run all the syncs.',
    pivotLine: 'The new one. It was formed this morning. Out of necessity.',
  },
  {
    id: 'therapy', category: 'medical', risk: 1, tags: ['believable'],
    text: "Therapy. It's on the calendar. Under 'personal'.",
    followUp: "It says 'personal' in all caps with three exclamation points.",
    pivotLine: "Yes. That's the therapy.",
  },
  {
    id: 'sourdough', category: 'absurd', risk: 4, tags: ['funny'],
    text: 'My sourdough starter is at peak. If I miss it, it dies.',
    followUp: "Can't you just… feed it later?",
    pivotLine: 'It has a name. His name is Gerald. Please.',
  },
  {
    id: 'contractor', category: 'domestic', risk: 2, tags: ['believable'],
    text: "Contractor's at my apartment. He needs a decision about a wall.",
    followUp: 'What kind of decision does a wall need?',
    pivotLine: 'Whether it stays. It is load-bearing. Emotionally.',
  },
  {
    id: 'nosebleed', category: 'medical', risk: 1, tags: ['believable'],
    text: "Nosebleed. It's stopped but I should go.",
    followUp: "If it stopped, why go?",
    pivotLine: "Because it knows I'm here. It'll come back.",
  },
  {
    id: 'vendetta', category: 'evasive', risk: 5, tags: ['legend'],
    text: "I have to go. I can't say why. It's not illegal.",
    followUp: 'Why would you say "not illegal"?',
    pivotLine: 'Because it is not. Illegal. Goodbye.',
  },
  {
    id: 'sprint', category: 'corporate', risk: 3, tags: [], immuneArchetypes: ['boss'],
    text: "I'm on a sprint. Standup's remote.",
    followUp: "It's 1 PM. Standup is in the morning.",
    pivotLine: "It's a sit-down now. Agile. It keeps changing.",
  },
  {
    id: 'hamster', category: 'absurd', risk: 5, tags: ['funny', 'legend'],
    text: "My sister's hamster escaped and I'm the only one who can catch it.",
    followUp: 'Why only you?',
    pivotLine: 'He trusts me. We have a thing. A hamster thing.',
  },

  // --- Additions in the same register ---
  {
    id: 'package', category: 'domestic', risk: 2, tags: ['near_exit'],
    text: "There's a package downstairs that needs my signature. Mine. Specifically mine.",
    followUp: "What's in it?",
    pivotLine: "Toner. For home. I print a lot at home. For reasons.",
  },
  {
    id: 'landlord', category: 'domestic', risk: 2, tags: ['believable'],
    text: "My landlord's calling. If I don't pick up, he changes the Wi-Fi password.",
    followUp: "Your phone's not ringing.",
    pivotLine: "It's on vibrate. In my soul. I can feel it.",
  },
  {
    id: 'vet', category: 'domestic', risk: 3, tags: ['funny'],
    text: 'Surprise vet appointment. The cat booked it.',
    followUp: 'How does a cat book an appointment?',
    pivotLine: 'Same way the dog locks doors. We have a very capable household.',
  },
  {
    id: 'elevatorguy', category: 'absurd', risk: 4, tags: ['funny', 'near_exit'],
    text: 'The elevator guy is here and he asked for me by name.',
    followUp: 'Why would the elevator guy ask for you?',
    pivotLine: 'We have history. Professional history. Cable-related.',
  },
  {
    id: 'hotdesk', category: 'corporate', risk: 2, tags: ['believable'],
    text: "Someone's in my hot desk, so I'm going to go work from a different, colder desk.",
    followUp: "We don't do hot desking. You have a desk. It has your name on it.",
    pivotLine: "The name is being disputed. It's a whole thing with facilities.",
  },
  {
    id: 'coffee', category: 'corporate', risk: 1, tags: ['near_kitchen', 'repeatable', 'believable'],
    text: 'Just grabbing a coffee. Want anything?',
    followUp: 'Your mug is on your desk.',
    pivotLine: "That's my backup mug. This is a two-mug day.",
  },
  {
    id: 'bathroom', category: 'medical', risk: 2, tags: ['repeatable'],
    text: "Bathroom. Please don't follow up on this.",
    followUp: "You've been to the bathroom four times today.",
    pivotLine: 'Hydration is a journey. I am on it.',
  },
  {
    id: 'walkingmeeting', category: 'corporate', risk: 3, tags: ['funny'],
    text: "Walking meeting. I'm the meeting. I'm walking.",
    followUp: "Who's the meeting with?",
    pivotLine: 'Myself. I have a lot of action items for myself.',
  },
  {
    id: 'jumpstart', category: 'domestic', risk: 2, tags: ['believable', 'near_exit'],
    text: "Jumping a coworker's car. He's down in the lot. He's crying a little.",
    followUp: 'Which coworker?',
    pivotLine: "I shouldn't say. He's embarrassed. It's a Prius.",
  },
  {
    id: 'pipe', category: 'absurd', risk: 4, tags: ['funny'],
    text: 'Pipe burst. My neighbor sent a photo. There is a fish in it.',
    followUp: 'Where did the fish come from?',
    pivotLine: "That's what I intend to find out.",
  },
  {
    id: 'jury', category: 'corporate', risk: 2, tags: ['believable'],
    text: "Jury duty callback. They said 'immediately' in a way that scared me.",
    followUp: "Jury duty doesn't call back.",
    pivotLine: 'It does now. Budget cuts. They have to be efficient.',
  },
  {
    id: 'nephew', category: 'domestic', risk: 2, tags: ['believable'],
    text: 'School called. My nephew bit someone. Allegedly.',
    followUp: "Why are they calling you and not his parents?",
    pivotLine: "I'm the fun uncle. Or aunt. It's a title. I earned it.",
  },
  {
    id: 'badge', category: 'corporate', risk: 2, tags: ['near_exit'],
    text: "My badge stopped working. I'm going to go stand near the security desk until it does.",
    followUp: 'It opened the door this morning.',
    pivotLine: "That was its last act. I want to be there for it.",
  },
  {
    id: 'offsite', category: 'corporate', risk: 2, tags: [], immuneArchetypes: ['boss'],
    text: "I'm expected at the offsite. It's on-site, but off this floor.",
    followUp: "Nobody told me about an offsite.",
    pivotLine: 'It was a very small offsite. Just the site, really.',
  },
  {
    id: 'mercury', category: 'absurd', risk: 5, tags: ['funny', 'legend'],
    text: "Mercury's in retrograde. I'm not supposed to be near spreadsheets.",
    followUp: "Is that… a real thing?",
    pivotLine: 'Look at Q3 and tell me it isn\'t.',
  },
  {
    id: 'straightener', category: 'domestic', risk: 3, tags: [],
    text: 'I think I left my straightener on. My whole apartment might be a straightener now.',
    followUp: "Don't those turn off automatically?",
    pivotLine: 'Mine is vintage. It has opinions.',
  },
  {
    id: 'ex', category: 'romantic', risk: 3, tags: ['near_exit'],
    text: 'My ex is in the lobby and I need to leave before he stops being in the lobby.',
    followUp: 'Wait, which ex? The one with the boat?',
    pivotLine: "There's a boat now? Oh no. I have to go even faster.",
  },
  {
    id: 'anniversary', category: 'romantic', risk: 2, tags: ['believable'],
    text: "It's our anniversary. Me and my partner. Not me and the job.",
    followUp: 'Which anniversary?',
    pivotLine: 'The important one. They are all the important one.',
  },
  {
    // Only unlocks once the original casserole has been used on this profile.
    id: 'casserole2', category: 'domestic', risk: 4, tags: ['funny', 'legend', 'needs_detail'],
    text: 'The casserole situation has… developed.',
    followUp: "It's been in the oven since Tuesday.",
    pivotLine: "It's a slow cooker now. It evolved. I have to go see what it became.",
    requires: { memoryFlag: 'used:casserole' },
  },
];
