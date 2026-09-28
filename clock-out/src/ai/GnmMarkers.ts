import * as THREE from 'three';
import type { GnmAnchors } from './GnmHead';

// Cultural markers and accessories for the GNM heads. Forehead marks are real
// decals: a small grid is projected onto the skin by raycasting, so a vibhuti or a
// chandrakor follows that particular face's curvature.

export type GnmMarker =
  | 'vibhuti' | 'chandrakor' | 'chandanam' | 'bindi' | 'noseRing' | 'jhumka'
  | 'glassesGold' | 'glassesBlack' | 'glassesSilver' | 'securityCap';

const v3 = (a: number[] | undefined) => new THREE.Vector3(a?.[0] ?? 0, a?.[1] ?? 0, a?.[2] ?? 0);

function flat(color: number): THREE.MeshStandardMaterial {
  return new THREE.MeshStandardMaterial({ color, roughness: 0.8, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 });
}

function metal(color: number, rough = 0.3): THREE.MeshStandardMaterial {
  return new THREE.MeshStandardMaterial({ color, metalness: 0.9, roughness: rough });
}

const ray = new THREE.Raycaster();

/** Project a flat shape (given as a geometry in the XY plane, centred on cx,cy) onto the skin along -Z. */
function decal(skin: THREE.Mesh, shape: THREE.BufferGeometry, cx: number, cy: number, mat: THREE.Material): THREE.Mesh | null {
  const pos = shape.attributes.position as THREE.BufferAttribute;
  const dir = new THREE.Vector3(0, 0, -1);
  for (let i = 0; i < pos.count; i++) {
    const x = cx + pos.getX(i), y = cy + pos.getY(i);
    ray.set(new THREE.Vector3(x, y, 0.5), dir);
    const hit = ray.intersectObject(skin, false)[0];
    if (!hit) return null;
    const n = hit.face?.normal ?? new THREE.Vector3(0, 0, 1);
    pos.setXYZ(i, hit.point.x + n.x * 0.0007, hit.point.y + n.y * 0.0007, hit.point.z + n.z * 0.0007);
  }
  pos.needsUpdate = true;
  shape.computeVertexNormals();
  return new THREE.Mesh(shape, mat);
}

function glasses(a: GnmAnchors, color: number): THREE.Group {
  const g = new THREE.Group();
  const mat = color === 0x111111 ? new THREE.MeshStandardMaterial({ color, roughness: 0.4 }) : metal(color);
  const eyeL = v3(a.leftEyeOuter).add(v3(a.leftEyeInner)).multiplyScalar(0.5);
  const eyeR = v3(a.rightEyeOuter).add(v3(a.rightEyeInner)).multiplyScalar(0.5);
  const z = Math.max(eyeL.z, eyeR.z) + 0.016;
  const r = v3(a.leftEyeOuter).distanceTo(v3(a.leftEyeInner)) * 0.72;
  const tube = color === 0x111111 ? 0.0022 : 0.0011;
  for (const e of [eyeL, eyeR]) {
    const rim = new THREE.Mesh(new THREE.TorusGeometry(r, tube, 6, 28), mat);
    rim.scale.set(1.12, 0.85, 1);
    rim.position.set(e.x, e.y - 0.002, z);
    g.add(rim);
    const lens = new THREE.Mesh(new THREE.CircleGeometry(r, 24), new THREE.MeshPhysicalMaterial({ color: 0xffffff, transparent: true, opacity: 0.08, roughness: 0, clearcoat: 1, depthWrite: false }));
    lens.scale.set(1.12, 0.85, 1);
    lens.position.set(e.x, e.y - 0.002, z);
    g.add(lens);
  }
  const bridge = new THREE.Mesh(new THREE.CylinderGeometry(tube, tube, Math.abs(eyeL.x - eyeR.x) - r * 2.2, 6), mat);
  bridge.rotation.z = Math.PI / 2;
  bridge.position.set((eyeL.x + eyeR.x) / 2, (eyeL.y + eyeR.y) / 2 + 0.002, z + 0.002);
  g.add(bridge);
  for (const [e, ear] of [[eyeL, v3(a.leftEar)], [eyeR, v3(a.rightEar)]] as const) {
    const start = new THREE.Vector3(e.x + Math.sign(e.x - (eyeL.x + eyeR.x) / 2) * r * 1.1, e.y, z);
    const end = new THREE.Vector3(ear.x * 0.97, e.y + 0.004, ear.z + 0.005);
    const len = start.distanceTo(end);
    const arm = new THREE.Mesh(new THREE.CylinderGeometry(tube * 0.9, tube * 0.9, len, 5), mat);
    arm.position.copy(start).add(end).multiplyScalar(0.5);
    arm.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), end.clone().sub(start).normalize());
    g.add(arm);
  }
  return g;
}

function jhumka(at: THREE.Vector3): THREE.Group {
  const g = new THREE.Group();
  const gold = metal(0xd4a437, 0.35);
  const stud = new THREE.Mesh(new THREE.SphereGeometry(0.0035, 10, 8), gold);
  const bell = new THREE.Mesh(new THREE.ConeGeometry(0.008, 0.011, 14, 1, true), gold);
  (bell.material as THREE.Material).side = THREE.DoubleSide;
  bell.position.y = -0.011;
  const drop = new THREE.Mesh(new THREE.SphereGeometry(0.0022, 8, 6), metal(0xb01830, 0.4));
  drop.position.y = -0.018;
  g.add(stud, bell, drop);
  g.position.copy(at).add(new THREE.Vector3(0, -0.012, 0.004));
  return g;
}

function securityCap(a: GnmAnchors, box: THREE.Box3): THREE.Group {
  // Sized from ear-to-ear width (the skin box includes the shoulders).
  const g = new THREE.Group();
  const navy = new THREE.MeshStandardMaterial({ color: 0x1d2a4a, roughness: 0.85 });
  void box;
  const brow = v3(a.browMid);
  const mid = v3(a.leftEar).add(v3(a.rightEar)).multiplyScalar(0.5);
  const crown = new THREE.Vector3(mid.x, 0, mid.z + 0.02);
  const capR = v3(a.leftEar).distanceTo(v3(a.rightEar)) * 0.56;
  const dome = new THREE.Mesh(new THREE.SphereGeometry(capR, 28, 14, 0, Math.PI * 2, 0, Math.PI * 0.5), navy);
  const base = brow.y + 0.022;
  const top = v3(a.crown).y + 0.012;
  dome.scale.set(1, (top - base) / capR, 1.12);
  dome.position.set(crown.x, base, crown.z);
  const band = new THREE.Mesh(new THREE.CylinderGeometry(capR, capR, 0.022, 28, 1, true), navy);
  band.scale.set(1, 1, 1.12);
  band.position.set(crown.x, base - 0.011, crown.z);
  (band.material as THREE.Material).side = THREE.DoubleSide;
  const brim = new THREE.Mesh(new THREE.CylinderGeometry(capR * 0.95, capR * 0.95, 0.004, 24, 1, false, -Math.PI / 2 - 0.9, 1.8), new THREE.MeshStandardMaterial({ color: 0x111827, roughness: 0.6 }));
  brim.position.set(crown.x, base - 0.018, crown.z + 0.03);
  brim.rotation.x = 0.18;
  const badge = new THREE.Mesh(new THREE.CircleGeometry(0.008, 16), metal(0xc9a227, 0.4));
  badge.position.set(crown.x, base + 0.012, crown.z + capR * 1.1);
  g.add(dome, band, brim, badge);
  return g;
}

export function addGnmMarkers(head: THREE.Group, skin: THREE.Mesh, a: GnmAnchors, box: THREE.Box3, markers: GnmMarker[]): void {
  const fc = v3(a.foreheadCenter);
  const bm = v3(a.browMid);
  for (const m of markers) {
    switch (m) {
      case 'vibhuti': {
        // Three horizontal ash lines and a kumkum dot (Ramesh, Chennai).
        const ash = flat(0xe6ded0);
        for (let i = 0; i < 3; i++) {
          const d = decal(skin, new THREE.PlaneGeometry(0.056 - i * 0.004, 0.0032, 14, 1), fc.x, fc.y + 0.004 + i * 0.0068, ash);
          if (d) head.add(d);
        }
        const dot = decal(skin, new THREE.CircleGeometry(0.0034, 14), fc.x, fc.y + 0.0105, flat(0xb3122a));
        if (dot) head.add(dot);
        break;
      }
      case 'chandrakor': {
        // Crescent (Maharashtrian) above a small dot (Kavita, Pune).
        const ring = new THREE.RingGeometry(0.0045, 0.0068, 20, 1, Math.PI * 1.1, Math.PI * 0.8);
        const d = decal(skin, ring, bm.x, bm.y + 0.016, flat(0xa0122a));
        if (d) head.add(d);
        const dot = decal(skin, new THREE.CircleGeometry(0.0018, 10), bm.x, bm.y + 0.0105, flat(0xa0122a));
        if (dot) head.add(dot);
        break;
      }
      case 'chandanam': {
        // Sandal-paste line (Priya, Thrissur).
        const d = decal(skin, new THREE.PlaneGeometry(0.0055, 0.024, 1, 8), bm.x, bm.y + 0.02, flat(0xe8cc86));
        if (d) head.add(d);
        const k = decal(skin, new THREE.CircleGeometry(0.0022, 10), bm.x, bm.y + 0.012, flat(0xa0122a));
        if (k) head.add(k);
        break;
      }
      case 'bindi': {
        const d = decal(skin, new THREE.CircleGeometry(0.0042, 16), bm.x, bm.y + 0.012, flat(0x9c0f25));
        if (d) head.add(d);
        break;
      }
      case 'noseRing': {
        const wing = v3(a.noseLeftWing).x > v3(a.noseRightWing).x ? v3(a.noseLeftWing) : v3(a.noseRightWing);
        const ring = new THREE.Mesh(new THREE.TorusGeometry(0.0045, 0.0008, 6, 16), metal(0xd4a437));
        ring.position.copy(wing).add(new THREE.Vector3(0.002, -0.001, 0.001));
        ring.rotation.y = Math.PI / 2;
        head.add(ring);
        break;
      }
      case 'jhumka':
        head.add(jhumka(v3(a.leftLobe ?? a.leftEar)), jhumka(v3(a.rightLobe ?? a.rightEar)));
        break;
      case 'glassesGold': head.add(glasses(a, 0xc9a24a)); break;
      case 'glassesBlack': head.add(glasses(a, 0x111111)); break;
      case 'glassesSilver': head.add(glasses(a, 0xb8bcc4)); break;
      case 'securityCap': head.add(securityCap(a, box)); break;
    }
  }
}
