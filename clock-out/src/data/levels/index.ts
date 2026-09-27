import type { LevelData } from '../types';
import { level1 } from './level1_lunch';
import { coorg } from './ch02_coorg';
import { sixthirty } from './ch03_sixthirty';
import { level2 } from './level2_crush';
import { clientvisit } from './ch05_clientvisit';
import { powercut } from './ch06_powercut';
import { level3 } from './level3_cooker';
import { appraisal } from './ch08_appraisal';
import { nightshift } from './ch09_nightshift';
import { cafeteria } from './ch10_cafeteria';
import { salary } from './ch11_salary';
import { theatre } from './ch12_theatre';
import { festival } from './ch13_festival';

// Every chapter, in play order. Chapters unlock one after another.
export const LEVELS: LevelData[] = [
  level1, coorg, sixthirty, level2, clientvisit, powercut, level3,
  appraisal, nightshift, cafeteria, salary, theatre, festival,
].sort((a, b) => a.chapter - b.chapter);
