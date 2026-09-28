import * as THREE from 'three';

// Extra ambient light that only the realistic (physically based) heads and bodies receive.
// The office lighting is tuned for flat Lambert materials and crushes realistic skin by
// 15-25 L*. An environment map fixed that but halved the frame rate; one uniform colour
// added to indirect diffuse does the same job for free. Set per light theme (Lighting.ts).

export const realAmbient = { value: new THREE.Color(0, 0, 0) };

/** Adds the realistic-ambient term to a material, keeping any existing onBeforeCompile. */
export function withRealAmbient<T extends THREE.Material>(m: T): T {
  const prev = m.onBeforeCompile;
  m.onBeforeCompile = (sh, r) => {
    prev?.call(m, sh, r);
    sh.uniforms.realAmbient = realAmbient;
    sh.fragmentShader = sh.fragmentShader
      .replace('#include <common>', '#include <common>\nuniform vec3 realAmbient;')
      .replace('#include <lights_fragment_end>', '#include <lights_fragment_end>\nreflectedLight.indirectDiffuse += diffuseColor.rgb * realAmbient;');
  };
  m.customProgramCacheKey = () => `realAmbient|${prev?.toString() ?? ''}`;
  return m;
}
