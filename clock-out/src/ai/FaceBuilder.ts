import * as THREE from 'three';

// Layered-primitive faces driven by a data recipe. A head is a squashed sphere with
// eyes, brows, nose, mouth, ears and hair built on top, so characters read as
// people (and as *specific* people) instead of boxes. Regional identity is carried
// by small details, never costumes: a vibhuti line, a chandrakor bindi, a steel kara.
//
// Coordinates are local to the head group: origin at the top of the neck, face
// looking down -Z, 1 unit = 1 m.

export interface FaceRecipe {
  skin: number;
  /** Head proportions relative to a 0.15 m sphere. */
  shape?: { w: number; h: number; d: number };
  eyeSize?: number;           // 1 = default
  eyeColor?: number;
  browThickness?: number;     // 1 = default
  browTilt?: number;          // radians; + reads stern, - reads worried
  browColor?: number;
  noseLength?: number;        // 1 = default
  mouth?: 'neutral' | 'smile' | 'smirk' | 'tired';
  lipColor?: number;
  hair: number;
  hairStyle: 'sidepart' | 'bun' | 'braid' | 'spiky' | 'bob' | 'receding' | 'crop';
  greyTemples?: boolean;
  facialHair?: 'moustache' | 'thickMoustache' | 'beard' | 'stubble';
  glasses?: 'gold' | 'black' | 'none';
  /** Subtle regional markers. */
  forehead?: 'vibhuti' | 'tilak' | 'chandrakor' | 'bindi' | 'chandanam';
  hat?: 'securityCap';
  gajra?: boolean;            // jasmine string in the hair
  earrings?: 'jhumka' | 'stud';
  noseRing?: boolean;
}

const TUNING = {
  radius: 0.15,
  sphereSegments: 20,
  centerY: 0.17,
};

const mats = new Map<string, THREE.MeshLambertMaterial>();
function mat(color: number, flat = false): THREE.MeshLambertMaterial {
  const key = `${color}-${flat}`;
  let m = mats.get(key);
  if (!m) {
    m = new THREE.MeshLambertMaterial({ color, flatShading: flat });
    mats.set(key, m);
  }
  return m;
}

function mesh(parent: THREE.Object3D, geo: THREE.BufferGeometry, color: number, x: number, y: number, z: number, flat = false): THREE.Mesh {
  const m = new THREE.Mesh(geo, mat(color, flat));
  m.position.set(x, y, z);
  parent.add(m);
  return m;
}

function shade(color: number, k: number): number {
  return new THREE.Color(color).multiplyScalar(k).getHex();
}

/** z of the head surface on the centre line at height dy above the head centre. */
function surfaceZ(R: number, s: { w: number; h: number; d: number }, dy: number): number {
  const t = Math.min(0.99, dy / (R * s.h));
  return -R * s.d * Math.sqrt(1 - t * t);
}

export function buildFace(head: THREE.Group, r: FaceRecipe): void {
  const R = TUNING.radius;
  const s = r.shape ?? { w: 0.92, h: 1.1, d: 0.97 };
  const cy = TUNING.centerY;
  const front = -R * s.d; // z of the face surface at the centre
  const skin = r.skin;

  // Skull + jaw: a squashed sphere with a slightly narrower lower half.
  const skull = mesh(head, new THREE.SphereGeometry(R, TUNING.sphereSegments, 16), skin, 0, cy, 0);
  skull.scale.set(s.w, s.h, s.d);
  const jaw = mesh(head, new THREE.SphereGeometry(R * 0.82, TUNING.sphereSegments, 12), skin, 0, cy - 0.05, -0.01);
  jaw.scale.set(s.w * 0.98, 0.8, s.d);

  // Ears.
  for (const side of [-1, 1]) {
    const ear = mesh(head, new THREE.SphereGeometry(0.03, 10, 8), shade(skin, 0.95), side * R * s.w * 0.98, cy, 0.005);
    ear.scale.set(0.45, 1.15, 0.8);
  }

  // Eyes: white, iris, a highlight-free pupil. Slight sink into the face.
  const es = r.eyeSize ?? 1;
  const eyeY = cy + 0.02;
  for (const side of [-1, 1]) {
    const x = side * 0.052;
    const white = mesh(head, new THREE.SphereGeometry(0.019 * es, 12, 10), 0xefeae0, x, eyeY, front + 0.016);
    white.scale.set(1.35, 0.8, 0.5);
    mesh(head, new THREE.SphereGeometry(0.0095 * es, 10, 8), r.eyeColor ?? 0x4a2c18, x, eyeY - 0.001, front + 0.009).scale.set(1, 1, 0.5);
    mesh(head, new THREE.SphereGeometry(0.0045 * es, 8, 6), 0x0a0a0a, x, eyeY - 0.001, front + 0.006).scale.set(1, 1, 0.5);
    // Upper lid line gives the eye a shape instead of a googly disc.
    mesh(head, new THREE.BoxGeometry(0.05 * es, 0.004, 0.008), shade(skin, 0.6), x, eyeY + 0.014 * es, front + 0.012, true);
    // Brows.
    const bt = r.browThickness ?? 1;
    const brow = mesh(head, new THREE.BoxGeometry(0.045, 0.009 * bt, 0.012), r.browColor ?? r.hair, x, eyeY + 0.036, front + 0.012, true);
    brow.rotation.z = side * -(r.browTilt ?? 0.08);
  }

  // Nose: a small wedge pointing out of the face.
  const nl = r.noseLength ?? 1;
  const nose = mesh(head, new THREE.ConeGeometry(0.018, 0.05 * nl, 4), shade(skin, 0.93), 0, cy - 0.015, front - 0.004);
  nose.rotation.x = -Math.PI / 2.6;
  nose.rotation.y = Math.PI / 4;

  // Mouth: a thin arc; its rotation carries the mood.
  const lip = r.lipColor ?? shade(skin, 0.72);
  const mouthY = cy - 0.058;
  const bearded = r.facialHair === 'beard';
  // A curved mouth floats oddly over a beard; bearded faces get a simple line.
  const mood = bearded ? 'neutral' : r.mouth ?? 'neutral';
  if (mood === 'neutral' || mood === 'tired') {
    const m = mesh(head, new THREE.BoxGeometry(0.05, 0.007, 0.01), lip, 0, mouthY, front + 0.022, true);
    if (mood === 'tired') { m.scale.x = 0.8; m.position.y -= 0.004; }
  } else {
    const arc = mesh(head, new THREE.TorusGeometry(0.028, 0.0045, 6, 14, Math.PI * 0.8), lip, 0, mouthY + 0.012, front + 0.02);
    arc.rotation.z = Math.PI + Math.PI * 0.1;
    if (mood === 'smirk') { arc.rotation.z += 0.35; arc.position.x = 0.008; }
  }
  if (mood === 'tired') {
    // Under-eye shadows: the look of someone who has been in a 6:45 PM "quick sync".
    for (const side of [-1, 1]) mesh(head, new THREE.BoxGeometry(0.03, 0.006, 0.006), shade(skin, 0.8), side * 0.052, eyeY - 0.022, front + 0.016, true);
  }

  // Facial hair.
  const fh = r.facialHair;
  if (fh === 'moustache' || fh === 'thickMoustache') {
    const thick = fh === 'thickMoustache';
    const m = mesh(head, new THREE.BoxGeometry(thick ? 0.075 : 0.058, thick ? 0.016 : 0.009, 0.014), r.hair, 0, mouthY + 0.02, front + 0.012, true);
    if (thick) {
      for (const side of [-1, 1]) {
        const tip = mesh(head, new THREE.BoxGeometry(0.014, 0.022, 0.012), r.hair, side * 0.036, mouthY + 0.008, front + 0.018, true);
        tip.rotation.z = side * 0.3;
      }
    }
    void m;
  } else if (fh === 'beard' || fh === 'stubble') {
    // A shell just outside the jaw, lower half only, so it hugs the face instead of floating.
    const beard = mesh(head, new THREE.SphereGeometry(R * 0.85, 32, 24, 0, Math.PI * 2, Math.PI * 0.42, Math.PI * 0.58), fh === 'beard' ? r.hair : shade(skin, 0.7), 0, cy - 0.05, -0.01);
    beard.scale.set(s.w * 1.05, 0.84, s.d * 1.07);
    // Keep the mouth visible on top of the beard.
    if (fh === 'beard') mesh(head, new THREE.BoxGeometry(0.04, 0.007, 0.01), shade(skin, 0.55), 0, mouthY, surfaceZ(R, s, mouthY - cy) - 0.03, true);
    if (fh === 'beard') mesh(head, new THREE.BoxGeometry(0.06, 0.012, 0.014), r.hair, 0, mouthY + 0.02, front + 0.012, true);
  }

  buildHair(head, r, s, cy);

  if (r.glasses && r.glasses !== 'none') {
    const frame = r.glasses === 'gold' ? 0xc9a24a : 0x1c1c1c;
    for (const side of [-1, 1]) {
      const ring = mesh(head, new THREE.TorusGeometry(0.026, 0.003, 6, 16), frame, side * 0.052, eyeY, front - 0.004);
      ring.scale.set(1.2, 0.9, 1);
      const arm = mesh(head, new THREE.BoxGeometry(0.004, 0.004, 0.13), frame, side * 0.13, eyeY + 0.004, front + 0.07);
      arm.rotation.y = side * 0.08;
    }
    mesh(head, new THREE.BoxGeometry(0.03, 0.004, 0.004), frame, 0, eyeY + 0.006, front - 0.006);
  }

  // Forehead markers, drawn flush with the skin. Small on purpose.
  const fy = cy + 0.068;
  // Sit on the curved forehead surface, a hair in front of it, so the mark is never swallowed by the skull.
  const fz = surfaceZ(R, s, fy - cy) - 0.003;
  switch (r.forehead) {
    case 'vibhuti':
      // Three pale horizontal lines of sacred ash with a small kumkum dot: common across Tamil Nadu.
      for (let i = 0; i < 3; i++) mesh(head, new THREE.BoxGeometry(0.044 - i * 0.004, 0.0024, 0.004), 0xcfc6b6, 0, fy + 0.01 - i * 0.008, fz, true);
      mesh(head, new THREE.SphereGeometry(0.0032, 8, 6), 0xa3202a, 0, fy + 0.002, fz - 0.002);
      break;
    case 'tilak':
      mesh(head, new THREE.BoxGeometry(0.008, 0.035, 0.006), 0xc0392b, 0, fy - 0.01, fz, true);
      break;
    case 'chandrakor': {
      // Crescent bindi, a quiet Maharashtrian signature.
      const c = mesh(head, new THREE.TorusGeometry(0.0075, 0.0026, 6, 12, Math.PI), 0x8b1b30, 0, fy - 0.004, fz);
      c.rotation.z = Math.PI;
      break;
    }
    case 'bindi':
      mesh(head, new THREE.SphereGeometry(0.0048, 8, 6), 0x8b1b30, 0, fy - 0.004, fz - 0.002).scale.set(1, 1, 0.4);
      break;
    case 'chandanam':
      // A thin line of sandal paste: a Kerala morning-temple habit that lasts till lunch.
      mesh(head, new THREE.BoxGeometry(0.0045, 0.026, 0.004), 0xd8b870, 0, fy - 0.006, fz, true);
      break;
  }

  if (r.hat === 'securityCap') {
    const capTop = mesh(head, new THREE.SphereGeometry(R * 1.1, 18, 10, 0, Math.PI * 2, 0, Math.PI * 0.36), 0x2c3e66, 0, cy + 0.01, 0);
    capTop.scale.set(s.w, s.h * 1.05, s.d);
    const brim = mesh(head, new THREE.CylinderGeometry(0.07, 0.07, 0.008, 16, 1, false, -Math.PI / 2, Math.PI), 0x223052, 0, cy + 0.098, front + 0.02);
    brim.scale.set(1.1, 1, 0.9);
  }
  if (r.noseRing) mesh(head, new THREE.TorusGeometry(0.007, 0.0016, 6, 10), 0xd4af37, 0.013, cy - 0.03, front - 0.006).rotation.y = Math.PI / 2;
  if (r.earrings) {
    for (const side of [-1, 1]) {
      const x = side * R * s.w * 1.0;
      if (r.earrings === 'jhumka') {
        mesh(head, new THREE.SphereGeometry(0.006, 6, 6), 0xd4af37, x, cy - 0.03, 0.0);
        const bell = mesh(head, new THREE.ConeGeometry(0.011, 0.018, 8, 1, true), 0xd4af37, x, cy - 0.048, 0.0);
        bell.material = mat(0xd4af37);
        (bell.material as THREE.MeshLambertMaterial).side = THREE.DoubleSide;
      } else {
        mesh(head, new THREE.SphereGeometry(0.005, 6, 6), 0xe8e4dc, x, cy - 0.03, -0.004);
      }
    }
  }
}

function buildHair(head: THREE.Group, r: FaceRecipe, s: { w: number; h: number; d: number }, cy: number): void {
  const R = 0.15;
  const hair = r.hair;
  const grey = 0x9a9690;
  // A cap: the upper part of a slightly larger sphere. `cut` is how far down it comes (0..PI).
  const cap = (cut: number, grow = 1.06, color = hair) => {
    const g = new THREE.SphereGeometry(R * grow, 20, 12, 0, Math.PI * 2, 0, cut);
    const m = mesh(head, g, color, 0, cy, 0);
    m.scale.set(s.w, s.h, s.d);
    return m;
  };
  // Crown cap: the tilted style caps below leave the top of a tall skull bare, this covers it.
  // Its edge stays above the forehead so markers there remain visible.
  cap(Math.PI * 0.3, 1.055);
  switch (r.hairStyle) {
    case 'sidepart': {
      const c = cap(Math.PI * 0.44);
      c.rotation.x = 0.42; // hairline sits back from the forehead, longer at the nape
      c.rotation.z = -0.1; // the parting side sits higher
      if (r.greyTemples) for (const side of [-1, 1]) {
        const t = mesh(head, new THREE.SphereGeometry(0.03, 8, 8), grey, side * 0.128 * s.w, cy + 0.055, 0.0);
        t.scale.set(0.35, 1, 1.2);
      }
      break;
    }
    case 'receding': {
      const c = cap(Math.PI * 0.38);
      c.rotation.x = 0.55;
      if (r.greyTemples) for (const side of [-1, 1]) mesh(head, new THREE.BoxGeometry(0.014, 0.06, 0.07), grey, side * 0.137 * s.w, cy + 0.03, 0.01, true);
      break;
    }
    case 'crop': {
      const c = cap(Math.PI * 0.4, 1.035);
      c.rotation.x = 0.25;
      break;
    }
    case 'spiky': {
      const c = cap(Math.PI * 0.42, 1.04);
      c.rotation.x = 0.4;
      // Gelled front: a row of small wedges, the Friday-night-in-Chandigarh look.
      for (let i = -2; i <= 2; i++) {
        const spike = mesh(head, new THREE.ConeGeometry(0.02, 0.05, 4), hair, i * 0.03, cy + 0.155 - Math.abs(i) * 0.008, -0.08 + Math.abs(i) * 0.01);
        spike.rotation.x = -0.5;
      }
      break;
    }
    case 'bob': {
      const c = cap(Math.PI * 0.62, 1.08);
      c.rotation.x = 0.15;
      break;
    }
    case 'bun':
    case 'braid': {
      // Deep cap tipped far back: hairline high at the front, hair down over the ears and nape.
      const c = cap(Math.PI * 0.6, 1.08);
      c.rotation.x = 0.8;
      if (r.hairStyle === 'bun') {
        mesh(head, new THREE.SphereGeometry(0.055, 14, 10), hair, 0, cy - 0.02, 0.15);
        if (r.gajra) {
          // Jasmine string wrapped around the bun.
          const g = mesh(head, new THREE.TorusGeometry(0.055, 0.012, 6, 18), 0xfbf8ee, 0, cy - 0.02, 0.15);
          g.rotation.y = Math.PI / 2;
          g.rotation.x = 0.3;
        }
      } else {
        for (let i = 0; i < 6; i++) mesh(head, new THREE.SphereGeometry(0.032 - i * 0.002, 10, 8), hair, 0, cy - 0.04 - i * 0.055, 0.15 + (i === 0 ? 0 : 0.01));
        if (r.gajra) {
          for (let i = 0; i < 4; i++) mesh(head, new THREE.SphereGeometry(0.012, 6, 6), 0xfbf8ee, 0.03, cy - 0.02 - i * 0.05, 0.16);
        }
      }
      break;
    }
  }
}
