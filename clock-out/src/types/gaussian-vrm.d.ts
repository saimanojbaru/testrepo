// Minimal typings for the parts of @naruya/gaussian-vrm the game uses.
declare module '@naruya/gaussian-vrm' {
  import type * as THREE from 'three';
  export interface GvrmInstance {
    isReady: boolean;
    character: { currentVrm: { scene: THREE.Object3D; humanoid: unknown } };
    gs: { viewer: { splatMesh: THREE.Mesh } };
    update(): void;
  }
  export const GVRM: {
    load(url: string, scene: THREE.Scene, camera: THREE.Camera, renderer: THREE.WebGLRenderer, fileName?: string): Promise<GvrmInstance>;
    remove(gvrm: GvrmInstance, scene: THREE.Scene): Promise<void>;
  };
}
