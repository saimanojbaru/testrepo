// Top-level game states. MENU is an addition to the spec's five so the loop can
// idle cheaply behind the main menu.

export const GameState = {
  MENU: 'MENU',
  PLAYING: 'PLAYING',
  DIALOGUE: 'DIALOGUE',
  CAUGHT: 'CAUGHT',
  ESCAPED: 'ESCAPED',
  PAUSED: 'PAUSED',
} as const;

export type GameStateId = (typeof GameState)[keyof typeof GameState];

export class StateMachine {
  current: GameStateId = GameState.MENU;
  /** State to return to when unpausing. */
  resumeTo: GameStateId = GameState.PLAYING;
  private listeners: Array<(next: GameStateId, prev: GameStateId) => void> = [];

  set(next: GameStateId): void {
    if (next === this.current) return;
    const prev = this.current;
    if (next === GameState.PAUSED) this.resumeTo = prev;
    this.current = next;
    for (const l of this.listeners) l(next, prev);
  }

  is(...states: GameStateId[]): boolean {
    return states.includes(this.current);
  }

  onChange(fn: (next: GameStateId, prev: GameStateId) => void): void {
    this.listeners.push(fn);
  }
}
