import * as THREE from 'three';
import type { ColliderWorld } from './Colliders';
import type { PropSpec } from '../data/types';

// Environmental storytelling for the dark levels: a handful of lit points of
// interest that prove the darkness is on purpose. Each prop is a few boxes plus,
// at most, one small point light. Point lights are created once at build time and
// only ever change intensity, so toggling them never recompiles a shader.

const TUNING = {
  emergencyIntensity: 9,
  emergencyDistance: 7,
  /** Seconds for an emergency light to stutter on after the mains drop. */
  emergencyStartup: 0.6,
  callRoomIntensity: 20,
  lampIntensity: 6,
  phoneIntensity: 2,
  /** Phone lights up this often (s) and stays lit this long (s). */
  phoneEvery: 7,
  phoneLit: 3.2,
};

export interface PropRuntime {
  /** Called every frame with the level beat time and whether the mains are out. */
  update(t: number, powerCut: boolean): void;
}

function mat(color: number): THREE.MeshLambertMaterial {
  return new THREE.MeshLambertMaterial({ color, flatShading: true });
}

function glow(color: number): THREE.MeshBasicMaterial {
  return new THREE.MeshBasicMaterial({ color, fog: false });
}

function box(parent: THREE.Object3D, x: number, y0: number, z: number, sx: number, sy: number, sz: number, m: THREE.Material): THREE.Mesh {
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(sx, sy, sz), m);
  mesh.position.set(x, y0 + sy / 2, z);
  parent.add(mesh);
  return mesh;
}

function point(parent: THREE.Object3D, color: number, intensity: number, distance: number, x: number, y: number, z: number): THREE.PointLight {
  const l = new THREE.PointLight(color, intensity, distance, 1.6);
  l.position.set(x, y, z);
  parent.add(l);
  return l;
}

export function buildProps(root: THREE.Group, props: PropSpec[] | undefined, cs: number, colliders: ColliderWorld): PropRuntime {
  const updaters: Array<(t: number, powerCut: boolean) => void> = [];
  for (const p of props ?? []) {
    const g = new THREE.Group();
    const [c, r] = p.cell;
    const [ox, oz] = p.offset ?? [0, 0];
    g.position.set((c + 0.5) * cs + ox, 0, (r + 0.5) * cs + oz);
    g.rotation.y = THREE.MathUtils.degToRad(p.yaw ?? 0);
    g.name = `prop-${p.kind}`;
    root.add(g);

    switch (p.kind) {
      case 'emergencyLight': {
        // Wall box with two lamp heads, the kind that only ever works during drills.
        const red = p.color === 'red';
        const hue = red ? 0xff2a14 : 0x1cff6a;
        box(g, 0, 2.3, 0, 0.42, 0.16, 0.1, mat(0xe8e6de));
        const lensOn = glow(hue);
        const lensOff = mat(red ? 0x5a2320 : 0x234a30);
        const heads = [-0.13, 0.13].map((x) => box(g, x, 2.22, 0.07, 0.1, 0.08, 0.08, lensOff));
        if (!red) {
          // Green EXIT sign: lit on battery all the time, it's the law.
          box(g, 0, 2.5, 0, 0.5, 0.2, 0.05, glow(0x19d45a));
        }
        const light = point(g, hue, 0, TUNING.emergencyDistance, 0, 2.1, 0.5);
        let onSince = -1;
        updaters.push((t, cut) => {
          if (!cut) { onSince = -1; light.intensity = 0; heads.forEach((h) => (h.material = lensOff)); return; }
          if (onSince < 0) onSince = t;
          const age = t - onSince;
          // Stutter on: a couple of dropouts in the first half second.
          const on = age > TUNING.emergencyStartup || Math.sin(age * 40) > 0.2;
          light.intensity = on ? TUNING.emergencyIntensity * (0.92 + 0.08 * Math.sin(t * 3.1)) : 0;
          heads.forEach((h) => (h.material = on ? lensOn : lensOff));
        });
        break;
      }
      case 'callRoom': {
        // Someone's laptop on a call at the far end: warm pool, blue screen, a figure nodding.
        const light = point(g, 0xffc27a, TUNING.callRoomIntensity, 8, 0, 2.2, 0.8);
        box(g, 0, 0.72, 0.3, 0.36, 0.02, 0.26, mat(0x2a2d33));
        const screen = box(g, 0, 0.74, 0.18, 0.34, 0.22, 0.015, glow(0x8fc4ff));
        screen.rotation.x = -0.25;
        const spot = point(g, 0x7fb2ff, 1.2, 2.5, 0, 1.0, 0.0);
        void spot;
        updaters.push((t) => {
          light.intensity = TUNING.callRoomIntensity * (0.96 + 0.04 * Math.sin(t * 0.7));
        });
        break;
      }
      case 'cleaningCart': {
        // Housekeeping's trolley, parked mid-shift. Mop, bucket, a yellow wet-floor sign.
        box(g, 0, 0.12, 0, 0.9, 0.05, 0.5, mat(0x3a5a8a));
        box(g, 0, 0.75, 0, 0.9, 0.05, 0.5, mat(0x3a5a8a));
        for (const [x, z] of [[-0.42, -0.22], [0.42, -0.22], [-0.42, 0.22], [0.42, 0.22]]) box(g, x, 0.12, z, 0.04, 0.68, 0.04, mat(0x9aa0a8));
        box(g, -0.2, 0.17, 0, 0.36, 0.3, 0.36, mat(0xe0c030));
        box(g, 0.25, 0.8, 0.05, 0.18, 0.22, 0.12, mat(0x5ab4e0));
        box(g, 0.25, 0.8, -0.13, 0.12, 0.16, 0.08, mat(0xe86aa0));
        const mop = box(g, -0.2, 0.3, 0.05, 0.03, 1.3, 0.03, mat(0xb08a50));
        mop.rotation.z = 0.18;
        const sign = new THREE.Group();
        sign.position.set(0.85, 0, 0.35);
        const s1 = box(sign, 0, 0, 0.09, 0.32, 0.6, 0.02, mat(0xf2c300));
        const s2 = box(sign, 0, 0, -0.09, 0.32, 0.6, 0.02, mat(0xf2c300));
        s1.rotation.x = -0.28; s2.rotation.x = 0.28;
        g.add(sign);
        const wp = g.getWorldPosition(new THREE.Vector3());
        colliders.add({ minX: wp.x - 0.5, maxX: wp.x + 0.5, minZ: wp.z - 0.3, maxZ: wp.z + 0.3, minY: 0, maxY: 0.8, kind: 'prop', blocksSight: false });
        break;
      }
      case 'deskLamp': {
        // Security's night desk: a small table, a register, the one lamp on the floor.
        box(g, 0, 0, 0, 1.1, 0.74, 0.6, mat(0x5a4632));
        box(g, -0.2, 0.74, 0.02, 0.36, 0.03, 0.26, mat(0xd8d2c0));
        box(g, 0.3, 0.74, -0.12, 0.12, 0.02, 0.12, mat(0x222222));
        box(g, 0.3, 0.76, -0.12, 0.02, 0.36, 0.02, mat(0x222222));
        const shade = new THREE.Mesh(new THREE.ConeGeometry(0.12, 0.14, 12, 1, true), mat(0x2f6a4a));
        shade.position.set(0.3, 1.14, -0.08);
        g.add(shade);
        box(g, 0.3, 1.06, -0.08, 0.08, 0.02, 0.08, glow(0xfff0c0));
        const light = point(g, 0xffd79a, TUNING.lampIntensity, 4, 0.3, 1.0, -0.05);
        void light;
        const wp = g.getWorldPosition(new THREE.Vector3());
        colliders.add({ minX: wp.x - 0.6, maxX: wp.x + 0.6, minZ: wp.z - 0.35, maxZ: wp.z + 0.35, minY: 0, maxY: 0.76, kind: 'prop', blocksSight: false });
        break;
      }
      case 'phoneGlow': {
        // A phone face-up on a desk that keeps lighting up. Nobody picks it up.
        box(g, 0, 0.755, 0, 0.08, 0.01, 0.16, mat(0x111111));
        const scr = box(g, 0, 0.766, 0, 0.07, 0.002, 0.145, mat(0x0c0c0e));
        const lit = glow(0xcfe6ff);
        const dark = scr.material;
        const light = point(g, 0xa8ccff, 0, 1.6, 0, 0.9, 0);
        const phase = (p.cell[0] * 1.7) % TUNING.phoneEvery;
        updaters.push((t) => {
          const k = (t + phase) % TUNING.phoneEvery;
          const on = k < TUNING.phoneLit;
          const fade = on ? Math.min(1, (TUNING.phoneLit - k) / 0.8) : 0;
          scr.material = on ? lit : dark;
          light.intensity = TUNING.phoneIntensity * fade;
        });
        break;
      }
    }
  }
  return { update: (t, cut) => updaters.forEach((u) => u(t, cut)) };
}
