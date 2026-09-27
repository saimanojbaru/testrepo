import type { LevelData } from '../types';

// Open-plan floor. Ramesh sir's glass cabin (top right) looks straight at the elevator
// bank, so the level is really about waiting for him to leave it (T+40) and being
// in the lobby when the doors open (T+70) or calling the car yourself.
export const level1: LevelData = {
  id: 'level1',
  name: 'The 1 PM Vanishing',
  brief: 'Paradise biryani, 1 PM, before the queue. A "quick sync" invite is about to land. Be in the lift before it does.',
  goalText: 'Reach the lift before the 1:15 "quick sync" lands',
  intro:
    'It is 12:56. Ramesh sir has been hovering over his Outlook all morning with the energy of a man about to schedule something. ' +
    'The biryani place stops taking orders at 1:30. The lift is thirty seconds away. Ramesh sir is ten.',
  parTime: 85,
  gridSize: [32, 19],
  cellSize: 1.5,
  exitType: 'elevator',
  directorBudget: 1,
  clock: { startMinutes: 12 * 60 + 56 + 40 / 60, rate: 1 / 6 },
  ascii: [
    '################################',
    '#W....L#..L............G.......#',
    '#......#...............G...D...#',
    '#..M...#...............G...D...#',
    '#......#...............G.......#',
    '#L.....#...............GGG.GGGG#',
    '##..####.......................#',
    '#..............................#',
    '#..CDDCDDC.....CDDCDDC.........#',
    '#..CCCCCCC.....CCCCCCC........XE',
    '#..CDDCDDC.....CDDCDDC........XE',
    '#..............................#',
    '#..............................#',
    '#..CDDCDDC.....CDDCDDC...##....#',
    '#..CCCCCCC.....CCCCCCC...KK....#',
    '#..CDDCDDC.....CDDCDDC.........#',
    '#....P.........................#',
    '#L............................L#',
    '################################',
  ],
  npcs: [
    { def: 'ramesh', cell: [28, 4], facing: 150 },
    { def: 'kavita', cell: [4, 3], facing: 180, patrol: [[4, 3], [3, 7], [13, 7], [24, 6], [13, 7], [3, 7]] },
    { def: 'rinku', cell: [23, 12], patrol: [[23, 12], [23, 17], [12, 17], [12, 11], [12, 7], [23, 7]] },
  ],
  scriptedBeats: [
    { atTime: 4, type: 'bark', payload: { npc: 'kavita', text: 'Has anyone seen the good scissors? The ones with my name written on them?' } },
    { atTime: 20, type: 'calendar', payload: { title: 'Quick Connect :)', minutes: 15, from: 'Ramesh Iyer' } },
    { atTime: 40, type: 'moveTo', payload: { npc: 'ramesh', patrol: [[27, 4], [26, 6], [23, 11], [11, 11], [11, 7], [22, 7], [26, 6]] } },
    { atTime: 41, type: 'bark', payload: { npc: 'ramesh', text: 'Floor walk, people. Management by walking around. I read it on LinkedIn.' } },
    { atTime: 70, type: 'elevator', payload: { action: 'open', duration: 10 } },
  ],
};
