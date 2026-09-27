import type { Input } from '../core/Input';

// On-screen controls for phones and tablets: a floating joystick on the left half,
// drag-to-look on the right half, and USE / CROUCH / SPRINT / PAUSE buttons.
// Everything feeds Input's virtual methods, so gameplay code can't tell the difference.

const TUNING = {
  /** Joystick travel in CSS px that maps to full speed. */
  stickRadius: 55,
  /** Small offsets are ignored so a resting thumb doesn't creep forward. */
  deadZone: 0.15,
  /** Look drag is scaled up: a thumb travels less than a mouse. */
  lookScale: 1.7,
  /** Fraction of the screen width that belongs to the joystick. */
  stickZoneWidth: 0.45,
};

export function isTouchDevice(): boolean {
  return window.matchMedia('(pointer: coarse)').matches || 'ontouchstart' in window;
}

export class TouchControls {
  private root: HTMLDivElement;
  private base: HTMLDivElement;
  private knob: HTMLDivElement;
  private crouchBtn: HTMLButtonElement;
  private sprintBtn: HTMLButtonElement;
  private stickId: number | null = null;
  private stickX = 0;
  private stickY = 0;
  private lookId: number | null = null;
  private lookX = 0;
  private lookY = 0;

  constructor(parent: HTMLElement, private readonly input: Input, onPause: () => void) {
    this.root = document.createElement('div');
    this.root.className = 'touch-controls hidden';
    const zone = document.createElement('div');
    zone.className = 'touch-zone';
    this.base = document.createElement('div');
    this.base.className = 'stick-base hidden';
    this.knob = document.createElement('div');
    this.knob.className = 'stick-knob';
    this.base.append(this.knob);

    const use = this.button('USE', 'btn-use', () => input.pressVirtual('KeyE'));
    this.crouchBtn = this.button('CROUCH', 'btn-crouch', () => input.pressVirtual('KeyC'));
    this.sprintBtn = this.button('SPRINT', 'btn-sprint', () => input.setVirtualSprint(true), () => input.setVirtualSprint(false));
    const pause = this.button('II', 'btn-pause', onPause);
    this.root.append(zone, this.base, use, this.crouchBtn, this.sprintBtn, pause);
    parent.append(this.root);

    zone.addEventListener('pointerdown', (e) => this.down(e));
    zone.addEventListener('pointermove', (e) => this.move(e));
    zone.addEventListener('pointerup', (e) => this.up(e));
    zone.addEventListener('pointercancel', (e) => this.up(e));
  }

  private button(label: string, cls: string, onDown: () => void, onUp?: () => void): HTMLButtonElement {
    const b = document.createElement('button');
    b.className = `touch-btn ${cls}`;
    b.textContent = label;
    b.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      e.stopPropagation();
      capture(b, e.pointerId);
      b.classList.add('held');
      onDown();
    });
    const release = () => {
      b.classList.remove('held');
      onUp?.();
    };
    b.addEventListener('pointerup', release);
    b.addEventListener('pointercancel', release);
    return b;
  }

  private down(e: PointerEvent): void {
    e.preventDefault();
    capture(e.currentTarget as HTMLElement, e.pointerId);
    if (e.clientX < window.innerWidth * TUNING.stickZoneWidth && this.stickId === null) {
      // Floating stick: it appears wherever the left thumb lands.
      this.stickId = e.pointerId;
      this.stickX = e.clientX;
      this.stickY = e.clientY;
      this.base.style.left = `${e.clientX}px`;
      this.base.style.top = `${e.clientY}px`;
      this.knob.style.transform = 'translate(-50%, -50%)';
      this.base.classList.remove('hidden');
    } else if (this.lookId === null) {
      this.lookId = e.pointerId;
      this.lookX = e.clientX;
      this.lookY = e.clientY;
    }
  }

  private move(e: PointerEvent): void {
    if (e.pointerId === this.stickId) {
      let dx = e.clientX - this.stickX, dy = e.clientY - this.stickY;
      const len = Math.hypot(dx, dy);
      if (len > TUNING.stickRadius) { dx *= TUNING.stickRadius / len; dy *= TUNING.stickRadius / len; }
      this.knob.style.transform = `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px))`;
      const nx = dx / TUNING.stickRadius, ny = dy / TUNING.stickRadius;
      const mag = Math.hypot(nx, ny);
      if (mag < TUNING.deadZone) this.input.setVirtualMove(0, 0);
      else this.input.setVirtualMove(-ny, nx);
    } else if (e.pointerId === this.lookId) {
      this.input.addLook((e.clientX - this.lookX) * TUNING.lookScale, (e.clientY - this.lookY) * TUNING.lookScale);
      this.lookX = e.clientX;
      this.lookY = e.clientY;
    }
  }

  private up(e: PointerEvent): void {
    if (e.pointerId === this.stickId) {
      this.stickId = null;
      this.input.setVirtualMove(0, 0);
      this.base.classList.add('hidden');
    } else if (e.pointerId === this.lookId) {
      this.lookId = null;
    }
  }

  show(on: boolean): void {
    this.root.classList.toggle('hidden', !on);
    if (!on) {
      this.stickId = this.lookId = null;
      this.base.classList.add('hidden');
      this.input.clearVirtual();
      this.sprintBtn.classList.remove('held');
    }
  }

  setCrouched(on: boolean): void {
    this.crouchBtn.classList.toggle('active', on);
  }
}

/** Capture keeps a drag alive when the thumb slides off its zone. Failure is harmless. */
function capture(el: HTMLElement, pointerId: number): void {
  try {
    el.setPointerCapture(pointerId);
  } catch {
    // Synthetic or already-released pointers can't be captured; events still arrive.
  }
}
