// Every sound in the game is synthesised here with the Web Audio API: the office
// hum, footsteps, Animal-Crossing voice blips, the elevator's salvation ding.
// The context is created lazily on the first user gesture (browser autoplay rules).

const TUNING = {
  master: 0.7,
  ambience: 0.22,
  humFreq: 60,
  humDetune: 0.35,
  humGain: 0.05,
  noiseGain: 0.09,
  ambienceLowpass: 900,
  duckTo: 0.3,
  duckLowpass: 350,
  duckTime: 0.15,
  sfx: 0.8,
  voiceGain: 0.07,
  voiceSpread: 0.28,
  voiceMaxDistance: 18,
};

export class AudioBus {
  private ctx: AudioContext | null = null;
  private master!: GainNode;
  private sfx!: GainNode;
  private ambience!: GainNode;
  private ambienceFilter!: BiquadFilterNode;
  private noiseBuffer!: AudioBuffer;
  private volume = 1;

  /** Must be called from a user gesture handler. Safe to call repeatedly. */
  unlock(): void {
    if (this.ctx) {
      if (this.ctx.state === 'suspended') void this.ctx.resume();
      return;
    }
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    const ctx = new Ctor();
    this.ctx = ctx;
    const comp = ctx.createDynamicsCompressor();
    comp.connect(ctx.destination);
    this.master = ctx.createGain();
    this.master.gain.value = TUNING.master * this.volume;
    this.master.connect(comp);
    this.sfx = ctx.createGain();
    this.sfx.gain.value = TUNING.sfx;
    this.sfx.connect(this.master);
    this.ambienceFilter = ctx.createBiquadFilter();
    this.ambienceFilter.type = 'lowpass';
    this.ambienceFilter.frequency.value = TUNING.ambienceLowpass;
    this.ambience = ctx.createGain();
    this.ambience.gain.value = 0;
    this.ambienceFilter.connect(this.ambience);
    this.ambience.connect(this.master);
    this.noiseBuffer = this.makeBrownNoise(2);
    this.startHum();
  }

  setVolume(v: number): void {
    this.volume = v;
    if (this.ctx) this.master.gain.value = TUNING.master * v;
  }

  private makeBrownNoise(seconds: number): AudioBuffer {
    const ctx = this.ctx!;
    const buf = ctx.createBuffer(1, ctx.sampleRate * seconds, ctx.sampleRate);
    const d = buf.getChannelData(0);
    let last = 0;
    for (let i = 0; i < d.length; i++) {
      const white = Math.random() * 2 - 1;
      last = (last + 0.02 * white) / 1.02;
      d[i] = last * 3.5;
    }
    return buf;
  }

  private startHum(): void {
    const ctx = this.ctx!;
    // Two detuned mains-hum sines beat slowly against each other: the sound of your soul.
    for (const f of [TUNING.humFreq - TUNING.humDetune, TUNING.humFreq + TUNING.humDetune, TUNING.humFreq * 2]) {
      const o = ctx.createOscillator();
      o.frequency.value = f;
      const g = ctx.createGain();
      g.gain.value = f > 100 ? TUNING.humGain * 0.35 : TUNING.humGain;
      o.connect(g).connect(this.ambienceFilter);
      o.start();
    }
    const n = ctx.createBufferSource();
    n.buffer = this.noiseBuffer;
    n.loop = true;
    const g = ctx.createGain();
    g.gain.value = TUNING.noiseGain;
    n.connect(g).connect(this.ambienceFilter);
    n.start();
  }

  ambienceOn(on: boolean): void {
    if (!this.ctx) return;
    const t = this.ctx.currentTime;
    this.ambience.gain.cancelScheduledValues(t);
    this.ambience.gain.setTargetAtTime(on ? TUNING.ambience : 0, t, 0.4);
  }

  /** Dialogue ducks the office away so the conversation feels claustrophobic. */
  duck(on: boolean): void {
    if (!this.ctx) return;
    const t = this.ctx.currentTime;
    const tc = TUNING.duckTime / 3;
    this.ambience.gain.setTargetAtTime(on ? TUNING.ambience * TUNING.duckTo : TUNING.ambience, t, tc);
    this.ambienceFilter.frequency.setTargetAtTime(on ? TUNING.duckLowpass : TUNING.ambienceLowpass, t, tc);
  }

  private env(g: GainNode, t: number, peak: number, attack: number, decay: number): void {
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(Math.max(0.0002, peak), t + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, t + attack + decay);
  }

  private noiseBurst(filterType: BiquadFilterType, freq: number, q: number, peak: number, decay: number, when = 0): void {
    const ctx = this.ctx!;
    const t = ctx.currentTime + when;
    const src = ctx.createBufferSource();
    src.buffer = this.noiseBuffer;
    src.playbackRate.value = 4 + Math.random();
    const f = ctx.createBiquadFilter();
    f.type = filterType;
    f.frequency.value = freq;
    f.Q.value = q;
    const g = ctx.createGain();
    this.env(g, t, peak, 0.004, decay);
    src.connect(f).connect(g).connect(this.sfx);
    src.start(t, Math.random() * 1.5);
    src.stop(t + decay + 0.05);
  }

  private tone(type: OscillatorType, freq: number, peak: number, attack: number, decay: number, when = 0, dest?: AudioNode): OscillatorNode {
    const ctx = this.ctx!;
    const t = ctx.currentTime + when;
    const o = ctx.createOscillator();
    o.type = type;
    o.frequency.setValueAtTime(freq, t);
    const g = ctx.createGain();
    this.env(g, t, peak, attack, decay);
    o.connect(g).connect(dest ?? this.sfx);
    o.start(t);
    o.stop(t + attack + decay + 0.05);
    return o;
  }

  footstep(kind: 'crouch' | 'walk' | 'sprint'): void {
    if (!this.ctx) return;
    // Carpet: a low thump. Crouch is muffled, sprint is sharp and loud.
    const p = { crouch: [260, 0.08, 0.06], walk: [520, 0.2, 0.08], sprint: [1100, 0.34, 0.07] }[kind];
    this.noiseBurst('lowpass', p[0] * (0.9 + Math.random() * 0.2), 0.8, p[1], p[2]);
  }

  bump(): void {
    if (!this.ctx) return;
    this.tone('sine', 90, 0.5, 0.005, 0.25);
    this.noiseBurst('lowpass', 700, 1, 0.4, 0.15);
  }

  /** One syllable of NPC gibberish. Pitch jitter is what makes it read as speech. */
  voice(base: number, gain = 1): void {
    if (!this.ctx || gain <= 0.01) return;
    const ctx = this.ctx;
    const t = ctx.currentTime;
    const f = base * (1 + (Math.random() - 0.5) * TUNING.voiceSpread);
    const o = ctx.createOscillator();
    o.type = 'square';
    o.frequency.setValueAtTime(f, t);
    o.frequency.exponentialRampToValueAtTime(f * (0.9 + Math.random() * 0.25), t + 0.07);
    const filt = ctx.createBiquadFilter();
    filt.type = 'lowpass';
    filt.frequency.value = 1800;
    const g = ctx.createGain();
    this.env(g, t, TUNING.voiceGain * gain, 0.008, 0.07);
    o.connect(filt).connect(g).connect(this.sfx);
    o.start(t);
    o.stop(t + 0.1);
  }

  /** Voice blips for a whole bark, attenuated by distance to the listener. */
  bark(text: string, pitch: number, distance: number): void {
    if (!this.ctx) return;
    const gain = Math.max(0, 1 - distance / TUNING.voiceMaxDistance);
    if (gain <= 0.02) return;
    const syllables = Math.min(14, Math.max(2, Math.round(text.replace(/[^a-z]/gi, '').length / 3.2)));
    for (let i = 0; i < syllables; i++) {
      window.setTimeout(() => this.voice(pitch, gain), i * 85 + Math.random() * 20);
    }
  }

  elevatorDing(): void {
    if (!this.ctx) return;
    // Clean two-tone sine with a long tail. Salvation.
    this.tone('sine', 1318.5, 0.35, 0.005, 1.6);
    this.tone('sine', 1046.5, 0.35, 0.005, 2.2, 0.32);
    this.tone('sine', 2637, 0.05, 0.005, 0.8);
  }

  calendarPing(): void {
    if (!this.ctx) return;
    this.tone('triangle', 880, 0.25, 0.005, 0.18);
    this.tone('triangle', 1175, 0.25, 0.005, 0.3, 0.12);
  }

  /** Dissonant FM "piano" hit for being spotted. Use sparingly. */
  sting(): void {
    if (!this.ctx) return;
    const ctx = this.ctx;
    const t = ctx.currentTime;
    for (const [f, ratio] of [[233.08, 3.5], [246.94, 2.01], [349.2, 1.5]]) {
      const car = ctx.createOscillator();
      car.frequency.value = f;
      const mod = ctx.createOscillator();
      mod.frequency.value = f * ratio;
      const modGain = ctx.createGain();
      modGain.gain.setValueAtTime(f * 2.5, t);
      modGain.gain.exponentialRampToValueAtTime(1, t + 0.9);
      mod.connect(modGain).connect(car.frequency);
      const g = ctx.createGain();
      this.env(g, t, 0.14, 0.004, 1.1);
      car.connect(g).connect(this.sfx);
      car.start(t); mod.start(t);
      car.stop(t + 1.2); mod.stop(t + 1.2);
    }
  }

  heartbeat(strength: number): void {
    if (!this.ctx) return;
    this.tone('sine', 55, 0.35 * strength, 0.01, 0.12);
    this.tone('sine', 48, 0.25 * strength, 0.01, 0.14, 0.16);
  }

  copierJam(): void {
    if (!this.ctx) return;
    // Grinding sawtooth plus a paper-crunch noise, then a sad beep. Loud.
    const o = this.tone('sawtooth', 70, 0.22, 0.02, 1.8);
    o.frequency.linearRampToValueAtTime(52, this.ctx.currentTime + 1.8);
    for (let i = 0; i < 6; i++) this.noiseBurst('bandpass', 1400 + Math.random() * 900, 2, 0.25, 0.12, i * 0.22);
    for (let i = 0; i < 3; i++) this.tone('square', 1960, 0.08, 0.005, 0.1, 2 + i * 0.3);
  }

  doorCreak(): void {
    if (!this.ctx) return;
    const ctx = this.ctx;
    const t = ctx.currentTime;
    const o = ctx.createOscillator();
    o.type = 'sawtooth';
    o.frequency.setValueAtTime(180, t);
    o.frequency.linearRampToValueAtTime(320, t + 0.35);
    o.frequency.linearRampToValueAtTime(210, t + 0.8);
    const f = ctx.createBiquadFilter();
    f.type = 'bandpass';
    f.frequency.value = 900;
    f.Q.value = 6;
    const g = ctx.createGain();
    this.env(g, t, 0.25, 0.05, 0.8);
    o.connect(f).connect(g).connect(this.sfx);
    o.start(t);
    o.stop(t + 0.95);
  }

  phoneRing(): void {
    if (!this.ctx) return;
    for (let r = 0; r < 2; r++) {
      for (let i = 0; i < 8; i++) this.tone('square', i % 2 ? 1300 : 1600, 0.05, 0.002, 0.04, r * 0.9 + i * 0.05);
    }
  }

  allHandsChime(): void {
    if (!this.ctx) return;
    [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => this.tone('triangle', f, 0.18, 0.005, 0.5, i * 0.12));
  }

  sip(): void {
    if (!this.ctx) return;
    for (let i = 0; i < 4; i++) this.noiseBurst('bandpass', 500 + i * 120, 4, 0.12, 0.08, i * 0.11);
    this.tone('sine', 300, 0.06, 0.02, 0.3, 0.5).frequency.exponentialRampToValueAtTime(900, this.ctx.currentTime + 0.8);
  }

  uiSelect(): void {
    if (!this.ctx) return;
    this.tone('triangle', 660, 0.12, 0.003, 0.08);
  }

  timerTick(urgent: boolean): void {
    if (!this.ctx) return;
    this.tone('square', urgent ? 1500 : 1000, urgent ? 0.06 : 0.03, 0.002, 0.03);
  }

  caughtStinger(): void {
    if (!this.ctx) return;
    [392, 370, 349.2, 329.6].forEach((f, i) => this.tone('triangle', f, 0.2, 0.01, 0.45, i * 0.28));
  }

  escapedFanfare(): void {
    if (!this.ctx) return;
    [523.25, 659.25, 783.99, 1046.5, 1318.5].forEach((f, i) => this.tone('sine', f, 0.18, 0.01, 0.6, i * 0.1));
  }
}
