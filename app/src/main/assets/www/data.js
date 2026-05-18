/* Game content: episodes, festivals, memory cards, retainers, actions */

const STATS = [
  { key: "discipline", label: "Discipline" },
  { key: "creativity", label: "Creativity" },
  { key: "financialWisdom", label: "Financial Wisdom" },
  { key: "familyBond", label: "Family Bond" },
  { key: "mentalHealth", label: "Mental Health" },
  { key: "charisma", label: "Charisma" },
  { key: "politics", label: "Politics" },
  { key: "wisdom", label: "Wisdom" },
];

const RETAINERS_INIT = [
  { id: "maa", name: "Maa", emoji: "👩🏽", affinity: 4 },
  { id: "papa", name: "Papa", emoji: "👨🏽", affinity: 3 },
  { id: "nani", name: "Nani", emoji: "👵🏽", affinity: 2 },
];

const DAILY_ACTIONS = [
  { id: "grind", name: "📚 Deep Grind", cost: 30, effects: { discipline: 12, mentalHealth: -6 }, flavor: "+Discipline, -Mental Health" },
  { id: "family", name: "🤗 Family Time", cost: 20, effects: { familyBond: 14, mentalHealth: 10 }, retainer: "maa", flavor: "+Family Bond, +Mental Health" },
  { id: "train", name: "🎨 Skill Training", cost: 25, effects: { creativity: 10, charisma: 4 }, flavor: "+Creativity, +Charisma" },
  { id: "rest", name: "🌿 Rest & Reflect", cost: 15, effects: { mentalHealth: 16, wisdom: 4 }, flavor: "+Mental Health, +Wisdom" },
];

/* Festivals: month is 1-12, day is approximate.
   Some festivals shift year-to-year; we keep a per-year overrides map for major ones,
   and fall back to a typical date if year not listed. */
const FESTIVALS = [
  { id: "raksha",   name: "Raksha Bandhan",   emoji: "🪢", month: 8,  day: 9,  byYear: { 2026: [8, 9],  2027: [8, 28] } },
  { id: "ganesh",   name: "Ganesh Chaturthi", emoji: "🐘", month: 9,  day: 14, byYear: { 2026: [9, 14], 2027: [9, 4] } },
  { id: "navratri", name: "Navratri Begins",  emoji: "🪔", month: 9,  day: 22, byYear: { 2026: [9, 22], 2027: [10, 11] } },
  { id: "dussehra", name: "Dussehra",         emoji: "🏹", month: 10, day: 1,  byYear: { 2026: [10, 1], 2027: [10, 20] } },
  { id: "diwali",   name: "Diwali",           emoji: "🪔", month: 11, day: 8,  byYear: { 2026: [11, 8], 2027: [10, 29] } },
  { id: "christmas",name: "Christmas",        emoji: "🎄", month: 12, day: 25 },
  { id: "newyear",  name: "New Year",         emoji: "🎆", month: 1,  day: 1 },
  { id: "republic", name: "Republic Day",     emoji: "🇮🇳", month: 1, day: 26 },
  { id: "holi",     name: "Holi",             emoji: "🎨", month: 3, day: 4,   byYear: { 2027: [3, 23], 2028: [3, 12] } },
  { id: "eid",      name: "Eid ul-Fitr",      emoji: "🌙", month: 3, day: 21,  byYear: { 2026: [3, 21], 2027: [3, 10], 2028: [2, 28] } },
];

/* Chapter 1 episodes.
   Each episode: id, title, bg, charEmoji, mood, lines (speaker+text), choices (apply stat deltas + flags),
   optional dynamic line overrides via "if".
*/
const EPISODES = [
  {
    id: "ch1_e1",
    chapter: 1, episode: 1, age: 5,
    title: "Morning Tiffin",
    bg: "morning", charEmoji: "🧒",
    lines: [
      { speaker: "Narrator", text: "Golden sunlight slips through the wooden window grill. Dust motes float in warm air." },
      { speaker: "Maa", text: "Beta, uth jao! School ki bus aane wali hai." },
      { speaker: "Maa", text: "Yeh tiffin abhi-abhi bana hai. Aaj aloo paratha." },
    ],
    choices: [
      { text: "Thanks Maa! It smells amazing.", effects: { familyBond: 12, mentalHealth: 8 }, flags: ["HighFamilyWarmth"], memory: "tiffin_glow" },
      { text: "Aloo paratha again?", effects: { creativity: 9, wisdom: 3 }, flags: ["PlayfulRebel"], memory: "tiffin_glow" },
    ],
  },
  {
    id: "ch1_e2",
    chapter: 1, episode: 2, age: 7,
    title: "Summer at Nani's House",
    bg: "village", charEmoji: "🧒",
    lines: [
      { speaker: "Narrator", text: "Mango trees, kites in a hot blue sky, cousins shrieking on the terrace." },
      { speaker: "Nani", text: "Arre waah! Kitne din baad aaya hai mera laal!", ifFlag: "HighFamilyWarmth" },
      { speaker: "Nani", text: "Aa gaya tu! Cousins kab se intezaar kar rahe the.", ifNotFlag: "HighFamilyWarmth" },
    ],
    choices: [
      { text: "Help Nani in the kitchen", effects: { familyBond: 15, wisdom: 5 }, flags: ["TraditionalKid"], memory: "mango_tree" },
      { text: "Run out with cousins to fly kites", effects: { creativity: 12, charisma: 10 }, flags: ["SocialKid"], memory: "mango_tree" },
      { text: "Curl up with Nani's old story books", effects: { wisdom: 14, creativity: 6 }, flags: ["BookishKid"], memory: "mango_tree" },
    ],
  },
  {
    id: "ch1_e3",
    chapter: 1, episode: 3, age: 8,
    title: "Rainy Day Homecoming",
    bg: "rain", charEmoji: "🧒",
    lines: [
      { speaker: "Narrator", text: "Rain hammering on the tin roof. You squelch home, uniform clinging." },
      { speaker: "Maa", text: "Arre kitna bheeg gaya hai! Jaldi andar aa, change kar le." },
      { speaker: "Maa", text: "Pakode bana rahi hoon. Tea bhi.", ifFlag: "HighFamilyWarmth" },
    ],
    choices: [
      { text: "Sorry Maa, I forgot the raincoat.", effects: { familyBond: 10, mentalHealth: 4 }, flags: ["PeoplePleaserSeed"], memory: "monsoon_pakora" },
      { text: "It was so fun jumping in puddles!", effects: { creativity: 12, mentalHealth: 8 }, flags: ["DreamerSeed"], memory: "monsoon_pakora" },
      { text: "Just change quietly and sit down.", effects: { discipline: 6, mentalHealth: -2 }, flags: ["Reserved"], memory: "monsoon_pakora" },
    ],
  },
  {
    id: "ch1_e4",
    chapter: 1, episode: 4, age: 10,
    title: "Papa's Test Question",
    bg: "morning", charEmoji: "🧒",
    lines: [
      { speaker: "Narrator", text: "Papa is on the floor with the household account book, pencil tapping." },
      { speaker: "Papa", text: "Aaj test hai na? Tayyari ho gayi, beta?" },
    ],
    choices: [
      { text: "I'll make you proud, Papa.", effects: { discipline: 16, mentalHealth: -3 }, flags: ["GrinderPath"], memory: "papa_accounts" },
      { text: "I studied… mostly.", effects: { wisdom: 6, mentalHealth: 4 }, flags: ["BalancedKid"], memory: "papa_accounts" },
      { text: "I want to invent something new today!", effects: { creativity: 18, discipline: -4 }, flags: ["DreamerPath"], memory: "papa_accounts" },
    ],
  },
  {
    id: "ch1_e5",
    chapter: 1, episode: 5, age: 11,
    title: "Power Cut Evening",
    bg: "night", charEmoji: "🧒", mood: "stressed",
    lines: [
      { speaker: "Narrator", text: "A candle flame, the smell of warm milk. Papa squints at numbers in lamplight." },
      { speaker: "Maa", text: "Beta, doodh leke aayi hoon. Thoda break le lo." },
      { speaker: "Papa", text: "...", ifFlag: "GrinderPath" },
    ],
    choices: [
      { text: "I'll study harder so we don't worry.", effects: { discipline: 14, financialWisdom: 10, mentalHealth: -8 }, flags: ["GrinderPath", "GuiltMemory"], memory: "powercut_lamp" },
      { text: "One day I'll buy us a big house, Papa.", effects: { creativity: 14, charisma: 8, financialWisdom: 4 }, flags: ["DreamerPath"], memory: "powercut_lamp" },
      { text: "Sit quietly and keep studying.", effects: { discipline: 8, mentalHealth: -4 }, flags: ["Reserved"], memory: "powercut_lamp" },
    ],
  },
  {
    id: "ch1_e6",
    chapter: 1, episode: 6, age: 11,
    title: "Festival Shopping Tension",
    bg: "festival", charEmoji: "🧒",
    lines: [
      { speaker: "Narrator", text: "The market is loud with lanterns and crowds. Papa's wallet feels thin." },
      { speaker: "Maa", text: "Is baar achhe kapde toh lene hi padenge… log kya kahenge?" },
    ],
    choices: [
      { text: "Let's buy good ones for everyone.", effects: { familyBond: 12, financialWisdom: -4 }, flags: ["FamilyFirst"], memory: "festival_shop" },
      { text: "I don't need new clothes.", effects: { financialWisdom: 14, familyBond: 4, mentalHealth: -4 }, flags: ["EarlySaver", "GuiltMemory"], memory: "festival_shop" },
      { text: "I want that expensive shirt!", effects: { creativity: 8, charisma: 6, financialWisdom: -8 }, flags: ["RiskTaker"], memory: "festival_shop" },
    ],
  },
  {
    id: "ch1_e7",
    chapter: 1, episode: 7, age: 12,
    title: "School Annual Day",
    bg: "classroom", charEmoji: "🧒", mood: "happy",
    lines: [
      { speaker: "Narrator", text: "Hot spotlight, painted backdrop. Parents craning their necks in the audience." },
      { speaker: "Teacher", text: "Beta, tumhara turn aa gaya." },
    ],
    choices: [
      { text: "Walk on confidently and deliver.", effects: { charisma: 14, mentalHealth: 4 }, flags: ["Stagey"], memory: "stage_light" },
      { text: "Nervous, but push through.", effects: { discipline: 8, charisma: 6 }, flags: ["BalancedKid"], memory: "stage_light" },
      { text: "Add your own creative twist.", effects: { creativity: 16, charisma: 8 }, flags: ["DreamerPath"], memory: "stage_light" },
    ],
  },
  {
    id: "ch1_e8",
    chapter: 1, episode: 8, age: 9,
    title: "Learning the Bicycle",
    bg: "village", charEmoji: "🚴",
    lines: [
      { speaker: "Narrator", text: "Gravel scrape. A wobble, a fall. Scraped elbow stings." },
      { speaker: "Papa", text: "Uth ja. Phir try kar." },
    ],
    choices: [
      { text: "Get up. Try again immediately.", effects: { discipline: 14, mentalHealth: 6 }, flags: ["Resilient"], memory: "bicycle_fall" },
      { text: "Ask Papa to hold the seat.", effects: { familyBond: 8, mentalHealth: 4 }, flags: ["PeoplePleaserSeed"], memory: "bicycle_fall" },
      { text: "Tinker with the chain — make it smoother.", effects: { creativity: 12, wisdom: 4 }, flags: ["DreamerPath"], memory: "bicycle_fall" },
    ],
  },
  {
    id: "ch1_e9",
    chapter: 1, episode: 9, age: 13,
    title: "When Money is Tight",
    bg: "evening", charEmoji: "🧒", mood: "stressed",
    lines: [
      { speaker: "Narrator", text: "Late at night you hear them through the door — half-whispered numbers, sighs." },
      { speaker: "Maa", text: "Bachhon ko pata nahi chalna chahiye." },
    ],
    choices: [
      { text: "Offer to skip new toys this month.", effects: { financialWisdom: 14, familyBond: 8 }, flags: ["FamilyFirst", "EarlySaver"], memory: "helping_hands" },
      { text: "Ask Papa to explain the household budget.", effects: { financialWisdom: 16, wisdom: 8 }, flags: ["MoneyAware"], memory: "helping_hands" },
      { text: "Promise yourself you'll earn so much one day.", effects: { creativity: 8, charisma: 6 }, flags: ["DreamerPath", "GuiltMemory"], memory: "helping_hands" },
    ],
  },
  {
    id: "ch1_e10",
    chapter: 1, episode: 10, age: 13,
    title: "Terrace Stories",
    bg: "night", charEmoji: "🧒",
    lines: [
      { speaker: "Narrator", text: "Charpai on the terrace. Stars. Nani begins another story." },
      { speaker: "Nani", text: "Sun, ek raja tha jo apne dil ki sunta tha…" },
    ],
    choices: [
      { text: "Listen carefully to every word.", effects: { wisdom: 14, familyBond: 10 }, flags: ["BookishKid"], memory: "terrace_stories" },
      { text: "Ask Nani a hundred questions.", effects: { wisdom: 10, creativity: 8 }, flags: ["Curious"], memory: "terrace_stories" },
      { text: "Tell Nani your own dream-stories instead.", effects: { creativity: 14, charisma: 6 }, flags: ["DreamerPath"], memory: "terrace_stories" },
    ],
  },
  {
    id: "ch1_e11",
    chapter: 1, episode: 11, age: 14,
    title: "Final Exam Preparation",
    bg: "night", charEmoji: "📚", mood: "stressed",
    lines: [
      { speaker: "Narrator", text: "Tube light hum. Notebooks stacked like a fortress. The clock keeps moving." },
      { speaker: "Maa", text: "Bas thoda aur. Phir aram karenge.", ifFlag: "HighFamilyWarmth" },
    ],
    choices: [
      { text: "Strict schedule. Hour by hour.", effects: { discipline: 16, mentalHealth: -8 }, flags: ["GrinderPath"], memory: "tubelight_study" },
      { text: "Mind-map everything. Make it visual.", effects: { creativity: 14, discipline: 8 }, flags: ["DreamerPath"], memory: "tubelight_study" },
    ],
  },
  {
    id: "ch1_e12",
    chapter: 1, episode: 12, age: 15,
    title: "Result Day",
    bg: "morning", charEmoji: "🤗", mood: "happy",
    lines: [
      { speaker: "Narrator", text: "Phone rings. Result is up. You scroll, hand shaking…" },
      { speaker: "Narrator", text: "You cracked it. You actually cracked the entrance." },
      { speaker: "Maa", text: "Beta…", ifFlag: "HighFamilyWarmth" },
      { speaker: "Papa", text: "Mujhe pata tha. Mujhe pata tha tu kar lega.", ifFlag: "GrinderPath" },
      { speaker: "Papa", text: "Dekho zara… sapne dekhne wala mera ladka.", ifFlag: "DreamerPath" },
    ],
    choices: [
      { text: "Hug them. Don't say anything.", effects: { familyBond: 24, mentalHealth: 20, charisma: 8 }, flags: ["Ch1Complete"], memory: "family_hug" },
    ],
    endsChapter: true,
  },
];

const MEMORY_CARDS = [
  { id: "tiffin_glow",       name: "Morning Tiffin Glow",   emoji: "🍞", desc: "The warmth of a steel tiffin in small hands." },
  { id: "mango_tree",        name: "Mango Tree Summer",     emoji: "🥭", desc: "Cousins, kites, kachha aam with salt." },
  { id: "monsoon_pakora",    name: "Monsoon Pakoras",       emoji: "🌧️", desc: "Wet uniform, hot pakoras, rain on tin." },
  { id: "papa_accounts",     name: "Papa's Account Book",   emoji: "📓", desc: "Pencil tap. Numbers. A long quiet look." },
  { id: "powercut_lamp",     name: "Power Cut Lamp Night",  emoji: "🕯️", desc: "Candle, warm milk, the smell of kerosene." },
  { id: "festival_shop",     name: "Festival Market",       emoji: "🛍️", desc: "Lanterns, prices, log-kya-kahenge." },
  { id: "stage_light",       name: "Stage Spotlight",       emoji: "🎤", desc: "Hot light, parents craning, a single line nailed." },
  { id: "bicycle_fall",      name: "First Bicycle",         emoji: "🚲", desc: "Scraped elbow. The exact moment of balance." },
  { id: "helping_hands",     name: "Helping Hands",         emoji: "🤝", desc: "Half-whispered numbers behind a door." },
  { id: "terrace_stories",   name: "Terrace Stories",       emoji: "🌌", desc: "Charpai, stars, a story that took decades to land." },
  { id: "tubelight_study",   name: "Tube Light Study",      emoji: "💡", desc: "Notebooks like a fortress. The clock kept moving." },
  { id: "family_hug",        name: "Family Hug, Golden",    emoji: "🤗", desc: "Maa cried. Papa pretended he didn't." },
];

/* Expose globally for the WebView script (no module system in WebView assets) */
window.GAME_DATA = { STATS, RETAINERS_INIT, DAILY_ACTIONS, FESTIVALS, EPISODES, MEMORY_CARDS };
