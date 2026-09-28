import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import * as SkeletonUtils from 'three/examples/jsm/utils/SkeletonUtils.js';
import { skinMaterial } from './GnmHead';
import { withRealAmbient } from './RealLight';

// Realistic bodies: Anny (Apache-2.0, MakeHuman CC0 assets) with clothes sewn from
// the body surface, rigged, carrying CMU motion-capture clips (walk, idle, phone).
// The NPC keeps its AI; this only decides which clip plays and how fast.

export type BodyClip = 'walk' | 'idle' | 'phone';

/** Per-character variation on shared clips, so nine people don't share one walk. */
export interface Gait {
  /** Cadence multiplier on the walk (stride adjusts: speed stays what the AI asks). */
  tempo?: number;
  /** Forward (+) or back (-) lean of the upper body, radians. */
  lean?: number;
  /** Arm swing: 1 = as captured, <1 stiffer, >1 looser. */
  arms?: number;
}

const TUNING = {
  crossFade: 0.25,
  /** Below this measured speed (m/s) an NPC counts as standing. */
  walkThreshold: 0.15,
  /** Walk playback is matched to movement speed, within these limits. */
  minTimeScale: 0.6,
  maxTimeScale: 1.8,
};

export interface RealBody {
  root: THREE.Object3D;
  headBone: THREE.Object3D;
  neckBone: THREE.Object3D;
  update(dt: number, speed: number, onPhone: boolean): void;
}

function fabric(kind: string, color: string): THREE.Material {
  return withRealAmbient(rawFabric(kind, color));
}

function rawFabric(kind: string, color: string): THREE.Material {
  const c = new THREE.Color(`#${color}`);
  if (kind === 'shoes' || kind === 'belt') return new THREE.MeshStandardMaterial({ color: c, roughness: 0.4 });
  if (kind === 'dupatta' || kind === 'pallu' || kind === 'saree') {
    return new THREE.MeshPhysicalMaterial({ color: c, roughness: 0.55, sheen: 0.8, sheenColor: new THREE.Color(0xffffff).lerp(c, 0.5), sheenRoughness: 0.4, side: THREE.DoubleSide });
  }
  return new THREE.MeshStandardMaterial({ color: c, roughness: 0.85, side: kind.endsWith('Skirt') ? THREE.DoubleSide : THREE.FrontSide });
}

const loader = new GLTFLoader();
const cache = new Map<string, Promise<{ scene: THREE.Group; animations: THREE.AnimationClip[] }>>();

export async function loadRealBody(url: string, gait: Gait = {}): Promise<RealBody> {
  if (!cache.has(url)) cache.set(url, loader.loadAsync(url));
  const src = await cache.get(url)!;
  const root = SkeletonUtils.clone(src.scene);
  const walkSpeed = (src.scene.userData as { walkSpeed?: number }).walkSpeed || 1.0;
  const skin = skinMaterial();
  root.traverse((o) => {
    const m = o as THREE.SkinnedMesh;
    if (!m.isMesh) return;
    const mat = m.material as THREE.MeshStandardMaterial;
    const ex = mat.userData as { color?: string; kind?: string };
    m.material = mat.name === 'skin' ? skin : fabric(ex.kind ?? mat.name, ex.color ?? '888888');
    m.frustumCulled = false;
  });
  const mixer = new THREE.AnimationMixer(root);
  const actions = Object.fromEntries(src.animations.map((a) => [a.name, mixer.clipAction(a)])) as Record<string, THREE.AnimationAction>;
  actions.idle?.setLoop(THREE.LoopPingPong, Infinity);
  actions.phone?.setLoop(THREE.LoopPingPong, Infinity);
  let current: BodyClip = 'idle';
  actions.idle?.play();
  const headBone = root.getObjectByName('head')!;
  const spine = root.getObjectByName('spine03');
  const arms = ['upperarm01L', 'upperarm01R'].map((n) => root.getObjectByName(n)).filter((b): b is THREE.Object3D => !!b);
  const armRest = arms.map((b) => b.quaternion.clone());
  const leanQ = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0), gait.lean ?? 0);
  const tmpQ = new THREE.Quaternion();
  const neckBone = root.getObjectByName('neck02') ?? root.getObjectByName('neck01')!;
  return {
    root, headBone, neckBone,
    update(dt, speed, onPhone) {
      const want: BodyClip = onPhone ? 'phone' : speed > TUNING.walkThreshold ? 'walk' : 'idle';
      if (want !== current && actions[want]) {
        actions[want].reset().fadeIn(TUNING.crossFade).play();
        actions[current]?.fadeOut(TUNING.crossFade);
        current = want;
      }
      if (actions.walk) actions.walk.timeScale = THREE.MathUtils.clamp((speed / walkSpeed) * (gait.tempo ?? 1), TUNING.minTimeScale, TUNING.maxTimeScale);
      mixer.update(dt);
      // Post-mixer layers: arm swing amount (relative to the rest pose), then lean.
      const k = gait.arms ?? 1;
      if (k !== 1) arms.forEach((b, i) => { tmpQ.copy(armRest[i]); b.quaternion.copy(tmpQ.slerp(b.quaternion, k)); });
      if (spine && gait.lean) spine.quaternion.multiply(leanQ);
    },
  };
}
