import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { addGnmMarkers, type GnmMarker } from './GnmMarkers';

// Photoreal-ish heads baked from Google's GNM Head model (Apache-2.0): synthetic
// identities, no scans of real people. The .glb carries per-part primitives
// (skin, hair, eyes, teeth...) with painted vertex colours, plus anchor points for
// cultural markers in the scene extras. This module turns that into a lit,
// shaded three.js group.

export interface GnmAnchors {
  foreheadCenter: number[]; browMid: number[]; noseTip: number[]; noseLeftWing: number[]; noseRightWing: number[];
  leftEar: number[]; rightEar: number[]; chin: number[]; mouthCenter: number[]; crown: number[];
  nape?: number[]; leftLobe?: number[]; rightLobe?: number[];
  leftEyeOuter: number[]; rightEyeOuter: number[]; leftEyeInner: number[]; rightEyeInner: number[];
}

export interface GnmHeadData {
  group: THREE.Group;
  anchors: GnmAnchors;
  /** Bounding box of the skin, in the group's local space. */
  box: THREE.Box3;
}

const TUNING = {
  skinRoughness: 0.5,
  scatterColor: new THREE.Vector3(0.9, 0.3, 0.2),
  scatterAmount: 0.55,
  poreRepeat: 14,
  poreStrength: 0.12,
};

let poreTexture: THREE.CanvasTexture | null = null;

/** Soft random bumps, tiled: enough to break up the plastic look under a key light. */
function pores(): THREE.CanvasTexture {
  if (poreTexture) return poreTexture;
  const n = 256;
  const c = document.createElement('canvas');
  c.width = c.height = n;
  const g = c.getContext('2d')!;
  const img = g.createImageData(n, n);
  const h = new Float32Array(n * n).map(() => Math.random());
  const b = new Float32Array(n * n);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    let s = 0;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) s += h[((y + dy + n) % n) * n + ((x + dx + n) % n)];
    b[y * n + x] = s / 9;
  }
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    const dx = b[y * n + ((x + 1) % n)] - b[y * n + ((x - 1 + n) % n)];
    const dy = b[((y + 1) % n) * n + x] - b[((y - 1 + n) % n) * n + x];
    const i = (y * n + x) * 4;
    img.data[i] = 128 + dx * 255; img.data[i + 1] = 128 + dy * 255; img.data[i + 2] = 255; img.data[i + 3] = 255;
  }
  g.putImageData(img, 0, 0);
  poreTexture = new THREE.CanvasTexture(c);
  poreTexture.wrapS = poreTexture.wrapT = THREE.RepeatWrapping;
  return poreTexture;
}

/**
 * Skin: physically based with a cheap subsurface term (wrap lighting tinted red),
 * pores as a triplanar normal perturbation (the GNM UVs aren't needed).
 */
export function skinMaterial(): THREE.MeshPhysicalMaterial {
  const m = new THREE.MeshPhysicalMaterial({
    color: 0xffffff, vertexColors: true, roughness: TUNING.skinRoughness, metalness: 0,
    sheen: 0.15, sheenColor: new THREE.Color(0x8a3a2a), sheenRoughness: 0.55, specularIntensity: 0.35,
  });
  const tex = pores();
  m.onBeforeCompile = (sh) => {
    sh.uniforms.poreMap = { value: tex };
    sh.uniforms.poreRepeat = { value: TUNING.poreRepeat };
    sh.uniforms.poreStrength = { value: TUNING.poreStrength };
    sh.uniforms.scatterColor = { value: TUNING.scatterColor };
    sh.uniforms.scatterAmount = { value: TUNING.scatterAmount };
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vObjPos;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvObjPos = position;');
    sh.fragmentShader = sh.fragmentShader
      .replace('#include <common>', `#include <common>
varying vec3 vObjPos;
uniform sampler2D poreMap; uniform float poreRepeat; uniform float poreStrength;
uniform vec3 scatterColor; uniform float scatterAmount;`)
      .replace('#include <normal_fragment_maps>', `#include <normal_fragment_maps>
{
  vec3 an = abs(normalize(vObjPos - vec3(0.0, 0.2, 0.0)));
  an /= (an.x + an.y + an.z);
  vec2 px = texture2D(poreMap, vObjPos.zy * poreRepeat * 10.0).xy - 0.5;
  vec2 py = texture2D(poreMap, vObjPos.xz * poreRepeat * 10.0).xy - 0.5;
  vec2 pz = texture2D(poreMap, vObjPos.xy * poreRepeat * 10.0).xy - 0.5;
  vec2 pp = px * an.x + py * an.y + pz * an.z;
  normal = normalize(normal + vec3(pp * poreStrength, 0.0));
}`)
      .replace('#include <lights_fragment_end>', `#include <lights_fragment_end>
#if NUM_DIR_LIGHTS > 0
for (int i = 0; i < NUM_DIR_LIGHTS; i++) {
  float ndl = dot(geometryNormal, directionalLights[i].direction);
  float w = max(0.0, (ndl + 0.5) / 1.5) - max(0.0, ndl);
  reflectedLight.directDiffuse += diffuseColor.rgb * scatterColor * w * directionalLights[i].color * scatterAmount;
}
#endif`);
  };
  return m;
}

let strandTexture: THREE.CanvasTexture | null = null;

/** Streaky noise: long thin strands, used as albedo + roughness variation on hair. */
function strands(): THREE.CanvasTexture {
  if (strandTexture) return strandTexture;
  const w = 256, h = 256;
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const g = c.getContext('2d')!;
  g.fillStyle = '#808080';
  g.fillRect(0, 0, w, h);
  for (let i = 0; i < 2600; i++) {
    const x = Math.random() * w, y = Math.random() * h, len = 20 + Math.random() * 60, v = Math.floor(60 + Math.random() * 140);
    g.strokeStyle = `rgba(${v},${v},${v},0.35)`;
    g.lineWidth = 0.6 + Math.random() * 0.8;
    g.beginPath(); g.moveTo(x, y); g.lineTo(x + (Math.random() - 0.5) * 6, y + len); g.stroke();
  }
  strandTexture = new THREE.CanvasTexture(c);
  strandTexture.wrapS = strandTexture.wrapT = THREE.RepeatWrapping;
  return strandTexture;
}

/** Hair: matte, strand-streaked (triplanar, streaks run down the head), faint sheen. */
export function hairMaterial(color: number, fadeEdges = false): THREE.MeshPhysicalMaterial {
  const m = new THREE.MeshPhysicalMaterial({ color, roughness: 0.62, sheen: 0.35, sheenColor: new THREE.Color(0x3a3028), sheenRoughness: 0.5, specularIntensity: 0.3 });
  if (fadeEdges) {
    // Shells carry a 0..1 weight in their vertex colour: fade to transparent at the hairline / beard edge.
    m.vertexColors = true;
    m.transparent = true;
    m.alphaTest = 0.02;
    m.polygonOffset = true;
    m.polygonOffsetFactor = -1;
    m.polygonOffsetUnits = -1;
  }
  const tex = strands();
  m.onBeforeCompile = (sh) => {
    sh.uniforms.strandMap = { value: tex };
    sh.vertexShader = sh.vertexShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vObjPos; varying vec3 vObjN;')
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvObjPos = position; vObjN = normal;');
    sh.fragmentShader = sh.fragmentShader
      .replace('#include <common>', '#include <common>\nvarying vec3 vObjPos; varying vec3 vObjN; uniform sampler2D strandMap;')
      .replace('#include <color_fragment>', `${fadeEdges ? '#if defined( USE_COLOR ) || defined( USE_COLOR_ALPHA )\ndiffuseColor.a *= smoothstep(0.04, 0.55, vColor.r);\n#endif' : '#include <color_fragment>'}
{
  vec3 an = abs(normalize(vObjN)); an /= (an.x + an.y + an.z);
  float sx = texture2D(strandMap, vec2(vObjPos.z, vObjPos.y) * vec2(9.0, 3.0)).r;
  float sz = texture2D(strandMap, vec2(vObjPos.x, vObjPos.y) * vec2(9.0, 3.0)).r;
  float sy = texture2D(strandMap, vObjPos.xz * vec2(9.0, 3.0)).r;
  float st = sx * an.x + sz * an.z + sy * an.y;
  diffuseColor.rgb *= 0.55 + st * 0.9;
}`)
      .replace('#include <roughnessmap_fragment>', '#include <roughnessmap_fragment>\nroughnessFactor = clamp(roughnessFactor + 0.0, 0.3, 1.0);');
  };
  return m;
}

function materialFor(name: string, extras: { hairColor: number; iris: number }, skin: THREE.Material): THREE.Material {
  switch (name) {
    case 'skin': return skin;
    case 'hair': return hairMaterial(extras.hairColor, true);
    case 'sclera': return new THREE.MeshPhysicalMaterial({ color: 0xcfc6b8, roughness: 0.3 });
    case 'iris': return new THREE.MeshPhysicalMaterial({ color: extras.iris, roughness: 0.4 });
    case 'pupil': return new THREE.MeshBasicMaterial({ color: 0x050403 });
    case 'cornea': return new THREE.MeshPhysicalMaterial({ color: 0xffffff, roughness: 0.02, transparent: true, opacity: 0.05, clearcoat: 1, clearcoatRoughness: 0, depthWrite: false });
    case 'teeth': return new THREE.MeshPhysicalMaterial({ color: 0xe8dfcc, roughness: 0.3 });
    case 'tongue': return new THREE.MeshPhysicalMaterial({ color: 0xa8505a, roughness: 0.45 });
    default: return new THREE.MeshBasicMaterial({ color: 0x1a0c0a }); // mouth interior, eye interior
  }
}

/** Bun or braid for tied-back hair, hung off the nape anchor. */
function addHairPieces(group: THREE.Group, extras: { hairColor: number; hair: string; anchors: GnmAnchors }): void {
  const a = extras.anchors?.nape;
  if (!a || (extras.hair !== 'bun' && extras.hair !== 'braid')) return;
  const mat = hairMaterial(extras.hairColor);
  const nape = new THREE.Vector3(a[0], a[1], a[2]);
  if (extras.hair === 'bun') {
    const bun = new THREE.Mesh(new THREE.SphereGeometry(0.042, 20, 14), mat);
    bun.scale.set(1.1, 0.85, 0.8);
    bun.position.copy(nape).add(new THREE.Vector3(0, 0.01, -0.025));
    group.add(bun);
    return;
  }
  // Braid: overlapping lobes, alternating slightly, tapering down the back.
  for (let i = 0; i < 11; i++) {
    const r = 0.024 * (1 - i * 0.05);
    const lobe = new THREE.Mesh(new THREE.SphereGeometry(r, 12, 8), mat);
    lobe.scale.set(1, 1.35, 0.8);
    lobe.position.copy(nape).add(new THREE.Vector3((i % 2 ? 1 : -1) * 0.006, -0.02 - i * 0.03, -0.02 - i * 0.004));
    group.add(lobe);
  }
  const tie = new THREE.Mesh(new THREE.TorusGeometry(0.012, 0.004, 6, 12), new THREE.MeshStandardMaterial({ color: 0x8a1f2a, roughness: 0.6 }));
  tie.rotation.x = Math.PI / 2;
  tie.position.copy(nape).add(new THREE.Vector3(0, -0.02 - 11 * 0.03, -0.065));
  group.add(tie);
}

const loader = new GLTFLoader();
const cache = new Map<string, Promise<{ scene: THREE.Group }>>();

/** Morph weights by name (smile, smirk, frown, jawOpen, browRaise, squint, blink); missing names are zeroed. */
export function setGnmExpression(head: THREE.Object3D, weights: Record<string, number>): void {
  head.traverse((o) => {
    const m = o as THREE.Mesh;
    if (!m.morphTargetDictionary || !m.morphTargetInfluences) return;
    for (const [name, i] of Object.entries(m.morphTargetDictionary)) m.morphTargetInfluences[i] = weights[name] ?? 0;
  });
}

export async function loadGnmHead(url: string, markers: GnmMarker[] = []): Promise<GnmHeadData> {
  if (!cache.has(url)) cache.set(url, loader.loadAsync(url));
  const src = await cache.get(url)!;
  // Each NPC gets its own copy (materials and markers are per instance; geometry is shared).
  const gltf = { scene: src.scene.clone(true) };
  const extras = (gltf.scene.userData ?? {}) as { hairColor: number; iris: number; hair: string; anchors: GnmAnchors };
  const skin = skinMaterial();
  const group = new THREE.Group();
  const box = new THREE.Box3();
  let skinMesh: THREE.Mesh | null = null;
  gltf.scene.traverse((o) => {
    const m = o as THREE.Mesh;
    if (!m.isMesh) return;
    const name = (m.material as THREE.Material).name;
    m.material = materialFor(name, extras, skin);
    // Normals aren't stored (quantized file); compute once per shared geometry.
    if (!m.geometry.attributes.normal) m.geometry.computeVertexNormals();
    if (name === 'cornea') m.renderOrder = 2;
    if (name === 'skin') skinMesh = m;
  });
  gltf.scene.updateMatrixWorld(true);
  if (skinMesh) box.setFromObject(skinMesh);
  group.add(gltf.scene);
  addHairPieces(group, extras);
  if (skinMesh && markers.length) {
    addGnmMarkers(group, skinMesh, extras.anchors, box, markers);
  }
  return { group, anchors: extras.anchors, box };
}

/** Average eye height of a loaded head (its own space), used to seat it at the NPC's eye height. */
export function gnmEyeHeight(a: GnmAnchors): number {
  return (a.leftEyeInner[1] + a.leftEyeOuter[1] + a.rightEyeInner[1] + a.rightEyeOuter[1]) / 4;
}
