import * as THREE from 'three';
import type { NPC } from './NPC';

// Experimental: a photoreal Gaussian-splat avatar (.gvrm, Gaussian-VRM) standing in
// for one NPC. The NPC keeps doing everything (AI, colliders, speech bubbles, the "?"
// marker); the splat only replaces what you see. Splats carry the lighting they were
// captured in, so a tint uniform is patched into the splat shader to follow the
// night theme and power cuts. The library is imported lazily so the normal game
// never downloads it.

const TUNING = {
  /** Arms hang this far down from the T-pose the rig loads in (radians). */
  armDrop: 1.2,
  elbowBend: 0.15,
  breathHz: 0.25,
  breathAmount: 0.02,
  /** Leg swing while walking, per m/s of speed. */
  strideSwing: 0.45,
  strideHz: 1.7,
  /** Extra lift (m) if a capture's feet float or sink. */
  groundOffset: 0,
};

type Gvrm = import('@naruya/gaussian-vrm').GvrmInstance;
type Humanoid = { getNormalizedBoneNode(name: string): THREE.Object3D | null };

export class SplatAvatar {
  private npc: NPC | null = null;
  private t = 0;
  private lastX = 0;
  private lastZ = 0;
  private speed = 0;
  private readonly tint = new THREE.Color(1, 1, 1);
  private readonly groundOffset = TUNING.groundOffset;
  /** Debug: raw measurements, read by tests. */
  debug: Record<string, number> = {};

  private constructor(private readonly gvrm: Gvrm, private readonly scene: THREE.Scene) {
    const vrm = gvrm.character.currentVrm;
    // The rig's scene sits belly-at-origin, but the splats are offset against where it
    // started, so the feet already land on y=0. Keep the raw numbers for tests.
    const box = new THREE.Box3().setFromObject(vrm.scene);
    const ch = gvrm.character as unknown as { ground?: number };
    this.debug = { boxMinY: box.min.y, boxMaxY: box.max.y, ground: ch.ground ?? NaN };
    this.patchTint();
  }

  /** Loads the avatar, or resolves null (and logs why) so the caller keeps the low-poly NPC. */
  static async load(url: string, scene: THREE.Scene, camera: THREE.Camera, renderer: THREE.WebGLRenderer): Promise<SplatAvatar | null> {
    try {
      const head = await fetch(url, { method: 'HEAD' });
      if (!head.ok) return null;
      const { GVRM } = await import('@naruya/gaussian-vrm');
      const gvrm = await GVRM.load(url, scene, camera, renderer, url.split('/').pop() ?? 'avatar.gvrm');
      return new SplatAvatar(gvrm, scene);
    } catch (e) {
      console.warn('Splat avatar failed to load; keeping the low-poly NPC.', e);
      return null;
    }
  }

  attach(npc: NPC): void {
    this.npc = npc;
    npc.setBodyVisible(false);
    this.lastX = npc.x;
    this.lastZ = npc.z;
  }

  isFor(npc: NPC): boolean {
    return this.npc === npc;
  }

  /** Multiplies every splat's colour; 1 = as captured. */
  setBrightness(k: number): void {
    this.tint.setScalar(k);
  }

  update(dt: number): void {
    const npc = this.npc;
    if (!npc) return;
    this.t += dt;
    const moved = Math.hypot(npc.x - this.lastX, npc.z - this.lastZ);
    this.lastX = npc.x;
    this.lastZ = npc.z;
    this.speed += ((dt > 0 ? moved / dt : 0) - this.speed) * Math.min(1, dt * 8);

    const vrm = this.gvrm.character.currentVrm;
    vrm.scene.position.set(npc.x, this.groundOffset, npc.z);
    // The rig's front matches the NPC model's front at the same yaw.
    vrm.scene.rotation.set(0, npc.group.rotation.y, 0);
    this.pose(vrm.humanoid as unknown as Humanoid);
    this.gvrm.update();
  }

  private pose(h: Humanoid): void {
    const bone = (n: string) => h.getNormalizedBoneNode(n);
    const breath = Math.sin(this.t * Math.PI * 2 * TUNING.breathHz) * TUNING.breathAmount;
    const swing = Math.sin(this.t * Math.PI * 2 * TUNING.strideHz) * Math.min(0.6, this.speed * TUNING.strideSwing);
    const set = (n: string, x: number, y: number, z: number) => { const b = bone(n); if (b) b.rotation.set(x, y, z); };
    set('leftUpperArm', swing * 0.6, 0, TUNING.armDrop - breath);
    set('rightUpperArm', -swing * 0.6, 0, -TUNING.armDrop + breath);
    set('leftLowerArm', 0, -TUNING.elbowBend, 0);
    set('rightLowerArm', 0, TUNING.elbowBend, 0);
    set('leftUpperLeg', swing, 0, 0);
    set('rightUpperLeg', -swing, 0, 0);
    set('leftLowerLeg', -Math.max(0, -swing) * 1.2, 0, 0);
    set('rightLowerLeg', -Math.max(0, swing) * 1.2, 0, 0);
    set('spine', breath * 0.5, 0, 0);
  }

  /** Adds `uniform vec3 splatTint` to the splat fragment shader and multiplies the output by it. */
  private patchTint(): void {
    const mat = this.gvrm.gs.viewer.splatMesh.material as THREE.ShaderMaterial;
    if (mat.fragmentShader.includes('splatTint')) return;
    mat.uniforms.splatTint = { value: this.tint };
    mat.fragmentShader = mat.fragmentShader
      .replace('uniform vec3 debugColor;', 'uniform vec3 debugColor;\nuniform vec3 splatTint;')
      .replace(/gl_FragColor = vec4\(([^,]+), /, 'gl_FragColor = vec4(($1) * splatTint, ');
    mat.needsUpdate = true;
  }

  async dispose(): Promise<void> {
    this.npc?.setBodyVisible(true);
    this.npc = null;
    const { GVRM } = await import('@naruya/gaussian-vrm');
    await GVRM.remove(this.gvrm, this.scene);
  }
}
