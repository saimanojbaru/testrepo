import * as THREE from 'three';

// Cheap fluorescent office light. No shadows, no area lights: a hemisphere fill for
// the flat overhead feel, two weak directionals so box faces separate from each
// other, and a slightly sickly green-white tint. The emissive ceiling panels are
// what sell it; the lights just keep every surface evenly, depressingly lit.

const TUNING = {
  tint: 0xe8f0e4,
  groundTint: 0x8a8270,
  ambient: 0.35,
  hemi: 1.1,
  key: 0.55,
  fill: 0.25,
  fogColor: 0xcfd6cc,
  fogDensity: 0.015,
};

export function setupLighting(scene: THREE.Scene): void {
  scene.background = new THREE.Color(TUNING.fogColor);
  scene.fog = new THREE.FogExp2(TUNING.fogColor, TUNING.fogDensity);
  scene.add(new THREE.AmbientLight(TUNING.tint, TUNING.ambient));
  scene.add(new THREE.HemisphereLight(TUNING.tint, TUNING.groundTint, TUNING.hemi));
  const key = new THREE.DirectionalLight(TUNING.tint, TUNING.key);
  key.position.set(0.4, 1, 0.25);
  scene.add(key);
  const fill = new THREE.DirectionalLight(0xdfe6f0, TUNING.fill);
  fill.position.set(-0.5, 0.8, -0.6);
  scene.add(fill);
}
