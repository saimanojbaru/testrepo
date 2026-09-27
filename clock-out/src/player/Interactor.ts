import * as THREE from 'three';
import type { ColliderWorld } from '../world/Colliders';

// Centre-screen raycast against things you can press E on. Hits are confirmed with
// a collider line-of-sight test so you can't use a copier through a wall.

const TUNING = {
  range: 2.4,
  /** Pull the LOS test short of the hit point so the target's own collider doesn't block it. */
  losBackoff: 0.45,
  highlight: 0x2a2a18,
};

export interface Interactable {
  object: THREE.Object3D;
  /** Current prompt, or null when it can't be used right now. */
  label(): string | null;
  use(): void;
  /** Materials to tint while targeted. */
  highlight?: THREE.MeshLambertMaterial[];
}

export class Interactor {
  items: Interactable[] = [];
  current: Interactable | null = null;
  private ray = new THREE.Raycaster();
  private center = new THREE.Vector2(0, 0);

  clear(): void {
    this.setHighlight(null);
    this.items = [];
  }

  update(camera: THREE.PerspectiveCamera, colliders: ColliderWorld): void {
    this.ray.setFromCamera(this.center, camera);
    this.ray.far = TUNING.range;
    let best: Interactable | null = null;
    let bestDist = Infinity;
    for (const item of this.items) {
      if (!item.object.visible || item.label() === null) continue;
      const hits = this.ray.intersectObject(item.object, true);
      if (!hits.length || hits[0].distance >= bestDist) continue;
      const p = hits[0].point;
      const o = camera.position;
      const d = hits[0].distance;
      const t = Math.max(0, (d - TUNING.losBackoff) / d);
      if (colliders.segmentBlocked(o.x, o.y, o.z, o.x + (p.x - o.x) * t, o.y + (p.y - o.y) * t, o.z + (p.z - o.z) * t)) continue;
      best = item;
      bestDist = d;
    }
    this.setHighlight(best);
  }

  tryUse(): boolean {
    if (!this.current || this.current.label() === null) return false;
    this.current.use();
    return true;
  }

  private setHighlight(next: Interactable | null): void {
    if (next === this.current) return;
    for (const m of this.current?.highlight ?? []) m.emissive.setHex(0x000000);
    for (const m of next?.highlight ?? []) m.emissive.setHex(TUNING.highlight);
    this.current = next;
  }
}
