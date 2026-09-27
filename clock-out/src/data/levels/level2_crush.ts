import type { LevelData } from '../types';

// One long corridor. Tom sits with a clean view of the UP stairwell door and Priya
// walks the corridor loop. The intended solves: jam the copier near Tom to pull him
// off his desk, hide in the (creaky) supply closet while Priya passes, or wait for
// Tom's phone.
export const level2: LevelData = {
  id: 'level2',
  name: 'The Forbidden Floor',
  brief: 'Your crush is on floor 7. At 3 PM everyone goes DOWN. You need to go UP.',
  goalText: 'Take the UP stairwell to floor 7',
  intro:
    "3:00 PM. The whole building drains downward toward the coffee cart. You're on 6. Sam from Legal is on 7, and Sam said " +
    '"come by sometime" in a way you have thought about for eleven days. Nobody takes the stairs up. Tom will notice. Tom notices everything.',
  parTime: 110,
  gridSize: [30, 14],
  cellSize: 1.5,
  exitType: 'stairwell',
  directorBudget: 1,
  favors: 3,
  clock: { startMinutes: 15 * 60, rate: 1 / 6 },
  ascii: [
    '##############################',
    '#####.CDDC..#.....#..G.......#',
    '#####.CCCC..#.....#..G..DD...#',
    '#####.CDDC..#.....#..G.......#',
    '#####.......#.....#K.G.......#',
    '#...#..L....###O###..GGG.GGGG#',
    '#...#........................#',
    '#.P.........................XS',
    '#...#........................#',
    '#...#........................#',
    '#####..CDDC..........L.......#',
    '#####..CCCC........K..CDC....#',
    '#####..CDDC...........CCC....#',
    '##############################',
  ],
  npcs: [
    { def: 'tom', cell: [23, 10], facing: 55 },
    { def: 'priya', cell: [20, 6], patrol: [[26, 6], [26, 9], [13, 9], [6, 9], [6, 6], [20, 6]] },
  ],
  scriptedBeats: [
    { atTime: 6, type: 'bark', payload: { npc: 'tom', text: "Three o'clock. Basically four. Basically five. Basically never." } },
    { atTime: 30, type: 'bark', payload: { npc: 'priya', text: "Floor walk. Nobody panic. I'm just walking. On the floor." } },
    { atTime: 65, type: 'phoneRing', payload: { npc: 'tom', duration: 9, line: "Tom Becker. …Mom? I'm at work. No, I'm at WORK." } },
    { atTime: 120, type: 'lockDoor', payload: { door: 0, locked: true, toast: 'Facilities locked the supply closet for "inventory".' } },
  ],
};
