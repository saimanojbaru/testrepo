import type { LevelData } from '../types';

// Farthest desk from the door, long open sightlines, two dense wanderer loops and
// Kavita parked beside the exit. The all-hands beat (T+30) doubles every FOV for
// five seconds, and Kavita's phone (T+55) is the one gap in her attention.
export const level3: LevelData = {
  id: 'level3',
  name: 'Pressure Cooker Run',
  brief: 'You already used the cooker excuse. Everyone heard. You need a new excuse and an old door.',
  goalText: 'Leave the building. Do not mention the cooker.',
  intro:
    '4:10 PM. You told them about the cooker on Tuesday. Kavita told everyone else by Tuesday at 4:12. ' +
    'Your desk is the farthest one from the exit, which Admin assures you was random.',
  parTime: 150,
  gridSize: [34, 19],
  cellSize: 1.5,
  exitType: 'outside-door',
  directorBudget: 1,
  forceUsedExcuses: ['cooker'],
  suspicionModifier: { label: 'Leaving at 4:10 is a statement', value: 2 },
  clock: { startMinutes: 16 * 60 + 10, rate: 1 / 6 },
  ascii: [
    '##################################',
    '#.............G.....G............#',
    '#..DDDDDDDD...G...D.G.......L....#',
    '#..DDDDDDDD...G...D.G...........XS',
    '#.............GGG.GGG...........XS',
    '#......................DDDD......#',
    '#......................DDDD....D.#',
    '#................................#',
    '#..DDDDDDDD...DDDDDDD..##........#',
    '#..DDDDDDDD...DDDDDDD..KK........#',
    '#................................#',
    '#......####.####.................#',
    '#......#W.....M#...DDDDDDDD......#',
    '#......#......M#...DDDDDDDD......#',
    '#......#.......#.................#',
    '#......###.#####.................#',
    '#....L...........................#',
    '#P.............................L.#',
    '##################################',
  ],
  npcs: [
    { def: 'kavita', cell: [30, 6], facing: 245 },
    { def: 'ramesh', cell: [16, 3], facing: 180 },
    { def: 'srinivas', cell: [2, 7], patrol: [[2, 7], [21, 7], [21, 10], [12, 10], [2, 10]] },
    { def: 'lakshmi', cell: [31, 14], patrol: [[31, 14], [17, 14], [17, 16], [31, 16], [31, 10], [24, 10], [24, 7], [31, 7]] },
  ],
  scriptedBeats: [
    { atTime: 3, type: 'bark', payload: { npc: 'kavita', text: 'Did everyone hear about the cooker? Tuesday? No? Arre, let me tell you.' } },
    { atTime: 30, type: 'allHands', payload: { duration: 5 } },
    { atTime: 55, type: 'phoneRing', payload: { npc: 'kavita', duration: 10, line: "Hello? Haan, Kavita speaking. …No. NO. Tell me everything. Slowly." } },
    { atTime: 90, type: 'bark', payload: { npc: 'ramesh', text: "Is that a cooker whistle? No? Then what is that sound? Is that… ambition?" } },
  ],
};
