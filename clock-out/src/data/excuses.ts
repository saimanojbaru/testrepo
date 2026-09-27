import type { ExcuseDef } from './types';

// Tag vocabulary understood by ExcuseRegistry:
//   repeatable    - reuse penalty is halved (people forgive a chai break)
//   needs_detail  - NPC always asks the followUp
//   near_kitchen / near_exit / near_copier - context fit: offered more often there,
//                   and costs +2 when used far from that context
//   funny / believable / legend - flavor; 'legend' excuses become trophies on success
//
// Written for a Hyderabad IT floor. Every excuse has a followUp probe and a
// pivotLine recovery; pivots are a little worse than the excuse on purpose.

export const EXCUSES: ExcuseDef[] = [
  // --- Home front ---
  {
    id: 'cooker', category: 'domestic', risk: 3, tags: ['funny'],
    text: "Cooker is on at home. Third whistle is about to come.",
    followUp: 'Who is at home watching it?',
    pivotLine: "That's the problem, sir. Nobody. The cooker is alone.",
  },
  {
    // Only unlocks once the cooker has been used on this profile.
    id: 'cooker2', category: 'domestic', risk: 4, tags: ['funny', 'legend', 'needs_detail'],
    text: 'The cooker situation has… escalated. The society secretary called.',
    followUp: "You said the same cooker was on last Tuesday.",
    pivotLine: "Same cooker. Different whistle. It's a long story, sir.",
    requires: { memoryFlag: 'used:cooker' },
  },
  {
    id: 'powercut', category: 'domestic', risk: 3, tags: ['funny'],
    text: 'Power cut at home, sir. Inverter also went. My work laptop is at zero for tomorrow.',
    followUp: 'What kind of kanjoos person cannot buy a UPS for one thousand rupees?',
    pivotLine: "I'm buying it today only, sir. That's actually where I'm going.",
  },
  {
    id: 'amma', category: 'domestic', risk: 2, tags: ['believable'],
    text: "Amma has called four times. If I don't go, she'll call you next.",
    followUp: 'How does your mother have my number?',
    pivotLine: 'Sir, she has everyone\'s number. Please don\'t make her use it.',
  },
  {
    id: 'relatives', category: 'domestic', risk: 3, tags: ['funny'],
    text: 'Relatives came from native place without telling. They are standing at Secunderabad station.',
    followUp: "Can't they take an auto?",
    pivotLine: "Sir, it's my periamma. She doesn't take autos. She takes people.",
  },
  {
    id: 'bike', category: 'domestic', risk: 2, tags: ['believable', 'near_exit'],
    text: "My bike is in no-parking. The towing van is already at the gate.",
    followUp: 'You park in the basement. I see you every day.',
    pivotLine: "Basement was full, sir. Today I was brave. Wrongly.",
  },
  {
    id: 'cylinder', category: 'domestic', risk: 2, tags: ['believable'],
    text: "Gas cylinder delivery. If I miss him, next slot is after Diwali.",
    followUp: "Can't your flatmate take it?",
    pivotLine: 'He is also at office, sir. Also lying to his manager, probably.',
  },
  {
    id: 'houseowner', category: 'domestic', risk: 2, tags: ['believable'],
    text: "House owner uncle is at my door for rent. He doesn't believe in UPI.",
    followUp: 'Why not just transfer it?',
    pivotLine: "He wants cash, sir. And to talk for one hour about his son in Canada.",
  },
  {
    id: 'geyser', category: 'domestic', risk: 3, tags: [],
    text: 'I think I left the geyser on. Whole flat must be a sauna now.',
    followUp: "Don't geysers turn off by themselves?",
    pivotLine: "Mine is from the previous tenant, sir. It has its own ideas.",
  },
  {
    id: 'nephew', category: 'domestic', risk: 2, tags: ['believable'],
    text: 'School called. My nephew bit someone. Allegedly. His parents are in Dubai.',
    followUp: 'Why are they calling you?',
    pivotLine: "I'm the local guardian, sir. It's on a form. I regret the form.",
  },
  {
    id: 'delivery', category: 'domestic', risk: 2, tags: ['near_exit'],
    text: 'Open-box delivery downstairs. The OTP comes only to my phone.',
    followUp: 'What did you order?',
    pivotLine: 'A UPS, sir. Because of the power cut. Everything is connected.',
  },

  // --- Medical (the sick-leave gauntlet) ---
  {
    id: 'fever', category: 'medical', risk: 1, tags: ['believable', 'needs_detail'],
    text: 'Slight fever, sir. Body pain also. Since morning.',
    followUp: "Let's go to the doctor together. Director needs a prescription for sick leave.",
    pivotLine: "Sir, I'm not a school student. Leave is there, I'm taking it.",
  },
  {
    id: 'rootcanal', category: 'medical', risk: 1, tags: ['believable'],
    text: 'Root canal appointment. Third sitting. The doctor is very strict.',
    followUp: 'Which clinic?',
    pivotLine: "The one near the metro, sir. With the fish tank. You know the one.",
  },
  {
    id: 'blood', category: 'medical', risk: 1, tags: ['believable'],
    text: "Blood donation. A friend's father is at the hospital, they need O-negative urgently.",
    followUp: 'Which hospital?',
    pivotLine: "The big one on the main road, sir. They're all on the main road.",
  },
  {
    id: 'heat', category: 'medical', risk: 1, tags: ['believable'],
    text: "It's the heat, sir. Nose started bleeding a little. It's stopped, but I should go.",
    followUp: "It's 21 degrees in here. Srinivas locked the AC.",
    pivotLine: "Outside heat, sir. It follows you in. It's a Hyderabad thing.",
  },
  {
    id: 'washroom', category: 'medical', risk: 2, tags: ['repeatable'],
    text: "Washroom. Please don't follow up on this.",
    followUp: "You've been to the washroom four times today.",
    pivotLine: 'Hydration is a journey, sir. I am on it.',
  },

  // --- Corporate ---
  {
    id: 'chai', category: 'corporate', risk: 1, tags: ['near_kitchen', 'repeatable', 'believable'],
    text: 'Just going for chai. You want one? Cutting?',
    followUp: 'Your cup is still on your desk.',
    pivotLine: "That's my backup cup. Today is a two-cup day.",
  },
  {
    id: 'kt', category: 'corporate', risk: 2, tags: ['believable'], immuneArchetypes: ['boss'],
    text: 'KT session with the other team. Different tower.',
    followUp: 'Which team? I approve all KT sessions.',
    pivotLine: 'The new one, sir. It was formed this morning. Out of necessity.',
  },
  {
    id: 'onsite', category: 'corporate', risk: 3, tags: [], immuneArchetypes: ['boss'],
    text: 'Onsite coordinator wants a call from a quiet place. Their morning, our afternoon.',
    followUp: "It's 3 AM in Dallas.",
    pivotLine: "He's an early riser, sir. Very dedicated. Very American.",
  },
  {
    id: 'visa', category: 'corporate', risk: 2, tags: ['needs_detail'],
    text: 'Visa slot opened for 2 PM only. If I miss it, next one is in eight months.',
    followUp: "You're not on any onsite plan.",
    pivotLine: "That's why I need the visa, sir. For the plan. That I'll get. Eventually.",
  },
  {
    id: 'aadhaar', category: 'corporate', risk: 2, tags: ['believable'],
    text: 'Aadhaar-PAN linking. Last date is today. Again.',
    followUp: "That can be done online.",
    pivotLine: "Website is down, sir. Physical verification only. Very Indian problem.",
  },
  {
    id: 'passport', category: 'corporate', risk: 2, tags: ['believable'],
    text: "Passport verification. Police station called. They said 'immediately' in a scary way.",
    followUp: 'Police stations call people?',
    pivotLine: 'This one does, sir. The constable has my number. We have history.',
  },
  {
    id: 'accesscard', category: 'corporate', risk: 2, tags: ['near_exit'],
    text: "My access card stopped working. I'm going to admin before they close the ticket.",
    followUp: 'It opened the turnstile this morning.',
    pivotLine: "That was its last act, sir. I want to be there for it.",
  },
  {
    id: 'hotdesk', category: 'corporate', risk: 2, tags: ['believable'],
    text: 'My hot-desk booking got cancelled. Going to find a seat in the other tower.',
    followUp: 'You have a fixed desk. It has your name on it.',
    pivotLine: 'The name is being disputed, sir. Admin is involved.',
  },
  {
    id: 'traffic', category: 'corporate', risk: 3, tags: ['believable'],
    text: 'If I leave after five, ORR traffic means I reach home tomorrow.',
    followUp: "Everyone faces the same traffic.",
    pivotLine: "Yes, sir. That's why I'm leaving before everyone. Strategy.",
  },
  {
    id: 'walkingmeeting', category: 'corporate', risk: 3, tags: ['funny'],
    text: "Walking one-on-one with myself. I'm the meeting. I'm walking.",
    followUp: "Who's taking the minutes?",
    pivotLine: 'Also me, sir. Very aligned meeting.',
  },

  // --- Romantic ---
  {
    id: 'crush', category: 'romantic', risk: 4, tags: ['needs_detail'],
    text: "I'm meeting someone. It's… complicated. Please don't tell Kavita.",
    followUp: 'Complicated how? Is it someone from this floor?',
    pivotLine: "You'd like them. I can't say more. I've said too much.",
  },
  {
    id: 'ladkidekhna', category: 'romantic', risk: 3, tags: ['funny'],
    text: 'Parents fixed a meeting with a family. Today. I found out at lunch.',
    followUp: 'Which family? From where?',
    pivotLine: "I also don't know, sir. That's the whole system.",
  },
  {
    id: 'anniversary', category: 'romantic', risk: 2, tags: ['believable'],
    text: "It's our anniversary. She has reminded me three times. The fourth time will be different.",
    followUp: 'Which anniversary?',
    pivotLine: "The important one, sir. They're all the important one.",
  },
  {
    id: 'ex', category: 'romantic', risk: 3, tags: ['near_exit'],
    text: 'My ex is in the lobby with her new one. I cannot be seen. Not today.',
    followUp: 'Wait, which ex? The one from the Coorg trip?',
    pivotLine: "There was a Coorg trip? Oh no. I have to go even faster.",
  },

  // --- Absurd ---
  {
    id: 'rahukalam', category: 'absurd', risk: 4, tags: ['funny', 'legend'],
    text: "Rahu kalam starts at 1:30. Nothing good can happen in this building after that.",
    followUp: 'Since when do you follow Rahu kalam?',
    pivotLine: 'Since my last appraisal, sir.',
  },
  {
    id: 'astrologer', category: 'absurd', risk: 5, tags: ['funny', 'legend'],
    text: 'My astrologer said if I sit at this desk after 2 PM, my appraisal is finished.',
    followUp: "You believe that?",
    pivotLine: "Sir, have you seen last year's appraisal? He predicted it.",
  },
  {
    id: 'pickle', category: 'absurd', risk: 4, tags: ['funny'],
    text: "Amma's mango pickle is at the exact stage. If I don't turn the jar now, the whole batch is gone.",
    followUp: "Can't she turn it herself?",
    pivotLine: "She's in Nellore, sir. The jar is here. It's a long-distance pickle.",
  },
  {
    id: 'parrot', category: 'absurd', risk: 5, tags: ['funny', 'legend'],
    text: "Neighbour's parrot escaped. It only comes down for me.",
    followUp: 'Why only you?',
    pivotLine: 'It trusts me, sir. We have a thing. A parrot thing.',
  },
  {
    id: 'goldfish', category: 'absurd', risk: 5, tags: ['funny', 'legend'],
    text: 'Sir, my goldfish is drowning.',
    followUp: "…Fish can't drown.",
    pivotLine: "That's what they said about the Titanic, sir.",
  },
  {
    id: 'liftmechanic', category: 'absurd', risk: 4, tags: ['funny', 'near_exit'],
    text: 'The lift mechanic is here and he asked for me by name.',
    followUp: 'Why would the lift mechanic ask for you?',
    pivotLine: 'We have history, sir. Professional history. Cable-related.',
  },

  // --- Evasive ---
  {
    id: 'network', category: 'evasive', risk: 4, tags: ['funny'],
    text: "Sir, network issue. I'll be offline for some time.",
    followUp: '…We are standing in the same office.',
    pivotLine: 'Mobile network, sir. Very bad on this floor. Better outside. Much outside.',
  },
  {
    id: 'cantsay', category: 'evasive', risk: 5, tags: ['legend'],
    text: "I have to go. I can't say why. It's not cricket.",
    followUp: 'Why would you say "not cricket"?',
    pivotLine: 'Because it is not, sir. It is… nothing. Bye.',
  },
];
