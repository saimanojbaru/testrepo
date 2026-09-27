// Keyboard + mouse, exposed as intent ("move forward", "interact") instead of raw
// keys so gameplay code never cares about layouts or bindings.

const TUNING = {
  defaultSensitivity: 0.0022,
};

const GAME_KEYS = new Set([
  'KeyW', 'KeyA', 'KeyS', 'KeyD', 'KeyC', 'KeyE', 'Space', 'ShiftLeft', 'ShiftRight',
  'ControlLeft', 'ControlRight', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight',
  'Digit1', 'Digit2', 'Digit3', 'Digit4', 'Enter', 'Tab',
]);

export interface MoveIntent {
  forward: number; // -1..1
  right: number;   // -1..1
  sprint: boolean;
  crouchHeld: boolean;
}

export class Input {
  sensitivity = TUNING.defaultSensitivity;
  locked = false;

  private down = new Set<string>();
  private pressed = new Set<string>();
  private lookX = 0;
  private lookY = 0;
  private wheel = 0;
  private mouseY = 0;
  private clicked = false;
  private dragging = false;
  // Touch controls feed these instead of real keys.
  private virtualForward = 0;
  private virtualRight = 0;
  private virtualSprint = false;
  private lockListeners: Array<(locked: boolean) => void> = [];

  constructor(private readonly lockTarget: HTMLElement) {
    window.addEventListener('keydown', (e) => {
      // Ctrl+W can't be intercepted by any page; Ctrl crouch is still offered,
      // but the C toggle is the recommended binding (see README).
      if (GAME_KEYS.has(e.code) && !(e.target instanceof HTMLInputElement)) e.preventDefault();
      if (!e.repeat) this.pressed.add(e.code);
      this.down.add(e.code);
    });
    window.addEventListener('keyup', (e) => this.down.delete(e.code));
    window.addEventListener('blur', () => this.down.clear());
    // Fallback when pointer lock is refused (some embeds): drag with the left button to look.
    this.lockTarget.addEventListener('mousedown', (e) => { if (e.button === 0) this.dragging = true; });
    window.addEventListener('mouseup', () => { this.dragging = false; });
    window.addEventListener('mousemove', (e) => {
      if (!this.locked && this.dragging) {
        this.lookX += e.movementX;
        this.lookY += e.movementY;
      }
      if (this.locked) {
        this.lookX += e.movementX;
        this.lookY += e.movementY;
        this.mouseY += e.movementY;
      }
    });
    window.addEventListener('mousedown', (e) => {
      if (e.button === 0 && this.locked) this.clicked = true;
    });
    window.addEventListener('wheel', (e) => {
      if (this.locked) this.wheel += Math.sign(e.deltaY);
    }, { passive: true });
    document.addEventListener('pointerlockchange', () => {
      this.locked = document.pointerLockElement === this.lockTarget;
      this.down.clear();
      for (const l of this.lockListeners) l(this.locked);
    });
  }

  // --- Virtual input (touch controls) ---

  setVirtualMove(forward: number, right: number): void {
    this.virtualForward = forward;
    this.virtualRight = right;
  }

  setVirtualSprint(on: boolean): void {
    this.virtualSprint = on;
  }

  /** A one-frame virtual key press, e.g. the on-screen USE button sends 'KeyE'. */
  pressVirtual(code: string): void {
    this.pressed.add(code);
  }

  /** Look input from a touch drag, in the same pixel units as mouse movement. */
  addLook(dx: number, dy: number): void {
    this.lookX += dx;
    this.lookY += dy;
  }

  /** A tap counts as a click for skipping lines and continuing verdicts. */
  tap(): void {
    this.clicked = true;
  }

  clearVirtual(): void {
    this.virtualForward = this.virtualRight = 0;
    this.virtualSprint = false;
  }

  requestLock(): void {
    if (this.locked) return;
    // Some browsers return a promise that rejects if the gesture was too old; that
    // is recoverable (the pause overlay asks for another click), so swallow it.
    try {
      const p = this.lockTarget.requestPointerLock() as unknown;
      if (p instanceof Promise) p.catch(() => undefined);
    } catch {
      /* the pause overlay handles the retry */
    }
  }

  releaseLock(): void {
    if (document.pointerLockElement) document.exitPointerLock();
  }

  onLockChange(fn: (locked: boolean) => void): void {
    this.lockListeners.push(fn);
  }

  isDown(code: string): boolean {
    return this.down.has(code);
  }

  wasPressed(code: string): boolean {
    return this.pressed.has(code);
  }

  move(): MoveIntent {
    const f = (this.isDown('KeyW') || this.isDown('ArrowUp') ? 1 : 0) - (this.isDown('KeyS') || this.isDown('ArrowDown') ? 1 : 0);
    const r = (this.isDown('KeyD') || this.isDown('ArrowRight') ? 1 : 0) - (this.isDown('KeyA') || this.isDown('ArrowLeft') ? 1 : 0);
    return {
      forward: Math.max(-1, Math.min(1, f + this.virtualForward)),
      right: Math.max(-1, Math.min(1, r + this.virtualRight)),
      sprint: this.isDown('ShiftLeft') || this.isDown('ShiftRight') || this.virtualSprint,
      crouchHeld: this.isDown('ControlLeft') || this.isDown('ControlRight'),
    };
  }

  get interact(): boolean { return this.wasPressed('KeyE'); }
  get crouchToggle(): boolean { return this.wasPressed('KeyC'); }
  get confirm(): boolean { return this.clicked || this.wasPressed('Space') || this.wasPressed('Enter') || this.wasPressed('KeyE'); }

  /** 0-based choice index from number keys, or -1. */
  choiceKey(): number {
    for (let i = 0; i < 4; i++) if (this.wasPressed(`Digit${i + 1}`)) return i;
    return -1;
  }

  /** -1 / +1 when the player nudges the dialogue highlight with wheel, arrows or W/S. */
  navDelta(): number {
    let d = this.wheel;
    if (this.wasPressed('ArrowUp') || this.wasPressed('KeyW')) d -= 1;
    if (this.wasPressed('ArrowDown') || this.wasPressed('KeyS')) d += 1;
    // Vertical mouse travel also moves the highlight, so a locked pointer can "point" at choices.
    const step = 60;
    while (this.mouseY > step) { d += 1; this.mouseY -= step; }
    while (this.mouseY < -step) { d -= 1; this.mouseY += step; }
    return Math.sign(d);
  }

  /** Mouse look is applied per rendered frame, not per sim step, so it stays smooth. */
  consumeLook(): { dx: number; dy: number } {
    const out = { dx: this.lookX * this.sensitivity, dy: this.lookY * this.sensitivity };
    this.lookX = 0;
    this.lookY = 0;
    return out;
  }

  resetDialogueNav(): void {
    this.mouseY = 0;
    this.wheel = 0;
  }

  /** Clears edge-triggered input. Only call after at least one sim step consumed it. */
  endFrame(): void {
    this.pressed.clear();
    this.clicked = false;
    this.wheel = 0;
  }
}
