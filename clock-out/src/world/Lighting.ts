import * as THREE from 'three';

// Cheap fluorescent office light. No shadows, no area lights: a hemisphere fill for
// the flat overhead feel, two weak directionals so box faces separate from each
// other, and a slightly sickly green-white tint. The emissive ceiling panels are
// what sell it; the lights just keep every surface evenly, depressingly lit.
// Themes re-tint the same rig: 1:30 AM night shift, a dark cinema hall, Diwali eve.

const TUNING = {
  tint: 0xe8f0e4,
  groundTint: 0x8a8270,
  ambient: 0.35,
  hemi: 1.1,
  key: 0.55,
  fill: 0.25,
  fogColor: 0xcfd6cc,
  fogDensity: 0.015,
  themes: {
    day: { tint: 0xe8f0e4, fog: 0xcfd6cc, fogDensity: 0.015, level: 1 },
    festival: { tint: 0xfff0d8, fog: 0xe8d9c0, fogDensity: 0.013, level: 1.02 },
    night: { tint: 0x9fb4d9, fog: 0x1d2433, fogDensity: 0.03, level: 0.42 },
    theatre: { tint: 0x8a92c9, fog: 0x0c0d12, fogDensity: 0.03, level: 0.42 },
  } as Record<string, { tint: number; fog: number; fogDensity: number; level: number }>,
  /** Emergency-light level during a power cut, as a fraction of normal. */
  powerCutLevel: 0.28,
};

export interface OfficeLights {
  ambient: THREE.AmbientLight;
  hemi: THREE.HemisphereLight;
  key: THREE.DirectionalLight;
  fill: THREE.DirectionalLight;
  fog: THREE.FogExp2;
  background: THREE.Color;
}

export function setupLighting(scene: THREE.Scene): OfficeLights {
  const background = new THREE.Color(TUNING.fogColor);
  scene.background = background;
  const fog = new THREE.FogExp2(TUNING.fogColor, TUNING.fogDensity);
  scene.fog = fog;
  const ambient = new THREE.AmbientLight(TUNING.tint, TUNING.ambient);
  const hemi = new THREE.HemisphereLight(TUNING.tint, TUNING.groundTint, TUNING.hemi);
  const key = new THREE.DirectionalLight(TUNING.tint, TUNING.key);
  key.position.set(0.4, 1, 0.25);
  const fill = new THREE.DirectionalLight(0xdfe6f0, TUNING.fill);
  fill.position.set(-0.5, 0.8, -0.6);
  scene.add(ambient, hemi, key, fill);
  return { ambient, hemi, key, fill, fog, background };
}

/** Applies a theme; `powerCut` drops everything to emergency lighting. */
export function applyLightTheme(l: OfficeLights, theme: string, powerCut: boolean): void {
  const t = TUNING.themes[theme] ?? TUNING.themes.day;
  const k = t.level * (powerCut ? TUNING.powerCutLevel : 1);
  l.ambient.color.setHex(t.tint);
  l.hemi.color.setHex(t.tint);
  l.key.color.setHex(t.tint);
  l.ambient.intensity = TUNING.ambient * k;
  l.hemi.intensity = TUNING.hemi * k;
  l.key.intensity = TUNING.key * k;
  l.fill.intensity = TUNING.fill * k;
  const fog = powerCut ? 0x14161a : t.fog;
  l.fog.color.setHex(fog);
  l.fog.density = powerCut ? 0.035 : t.fogDensity;
  l.background.setHex(fog);
}
