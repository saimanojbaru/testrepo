import * as THREE from 'three';
import { NPC } from '../ai/NPC';
import type { NPCDef, NPCLook } from '../data/types';

// Debug-only character lineup (?lineup): renders candidate characters full-body and
// as face close-ups so looks can be compared and signed off before they ship.

interface Candidate { def: NPCDef; look: NPCLook; region: string; tells: string; }

const base = { visionRange: 12, visionFov: 100, hearingMul: 1, walkSpeed: 1.2, talkativeness: 0.7, barks: [] as string[] };

export const CANDIDATES: Candidate[] = [
  {
    region: 'Chennai, Tamil Nadu',
    tells: 'Vibhuti lines + kumkum dot, oiled side-parting going grey at the temples, moustache, gold-rim glasses, half-sleeve shirt with a pen in the pocket, rudraksha beads peeking at the collar.',
    def: { ...base, id: 'ramesh', name: 'Ramesh Iyer', role: 'Senior Manager', color: 0xc9d9ea, archetype: 'boss' },
    look: {
      skin: 0x8a5a3c, hair: 0x15110f, hairStyle: 'short', pants: 0x3a3d44, voice: 150, height: 1.03,
      tagline: '"Quick sync at 6:45?" is not a question.',
      accessories: ['halfSleeve', 'belt', 'penPocket', 'watch', 'rudraksha'],
      face: {
        skin: 0x8a5a3c, hair: 0x15110f, hairStyle: 'sidepart', greyTemples: true, facialHair: 'moustache',
        glasses: 'gold', forehead: 'vibhuti', mouth: 'neutral', browTilt: 0.2, browThickness: 1.4,
        shape: { w: 0.94, h: 1.08, d: 0.98 }, noseLength: 1.1,
      },
    },
  },
  {
    region: 'Pune, Maharashtra',
    tells: 'Chandrakor (crescent) bindi, green glass bangles, black-bead mangalsutra, small nath, bun with a jasmine gajra, cotton kurti with a Paithani-style magenta-and-gold border.',
    def: { ...base, id: 'kavita', name: 'Kavita Deshpande', role: 'Admin & Facilities', color: 0x1f5f6b, archetype: 'gossip' },
    look: {
      skin: 0xa8704c, hair: 0x120d0b, hairStyle: 'bun', pants: 0xe9e2d0, voice: 320, height: 0.93, trim: 0xa3195b,
      tagline: 'Knows who ordered biryani on the team card. And why.',
      accessories: ['kurti', 'dupatta', 'greenBangles', 'mangalsutra'],
      face: {
        skin: 0xa8704c, hair: 0x120d0b, hairStyle: 'bun', gajra: true, forehead: 'chandrakor', noseRing: true,
        earrings: 'stud', mouth: 'smile', eyeSize: 1.05, browTilt: -0.05, lipColor: 0x7a3434,
        shape: { w: 0.9, h: 1.1, d: 0.95 },
      },
    },
  },
  {
    region: 'Ludhiana, Punjab',
    tells: 'Steel kara on the right wrist, gelled spiky hair, trimmed full beard, thick brows, a smirk, a lavender shirt with sleeves rolled to the elbow, a big steel watch.',
    def: { ...base, id: 'rohit', name: 'Rohit Malhotra', role: 'Senior Software Engineer', color: 0x9d93cf, archetype: 'coworker' },
    look: {
      skin: 0xc2946c, hair: 0x1b1411, hairStyle: 'short', pants: 0x2b3345, voice: 190, height: 1.06,
      tagline: '"Bhai, Ramesh sir ko bata doon?" He already has.',
      accessories: ['halfSleeve', 'belt', 'watch', 'kara'],
      face: {
        skin: 0xc2946c, hair: 0x1b1411, hairStyle: 'spiky', facialHair: 'beard', mouth: 'smirk',
        browThickness: 1.6, browTilt: 0.1, shape: { w: 0.96, h: 1.06, d: 0.98 },
      },
    },
  },
];

export function showLineup(container: HTMLElement): void {
  const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio));
  container.append(renderer.domElement);
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xdcdcd2);
  scene.add(new THREE.HemisphereLight(0xf2f5ee, 0x8a8270, 1.4));
  const key = new THREE.DirectionalLight(0xfff4e6, 1.3);
  key.position.set(1.5, 2.8, 3);
  scene.add(key);
  const fill = new THREE.DirectionalLight(0xdfe6f0, 0.5);
  fill.position.set(-2, 1.5, -1);
  scene.add(fill);
  const floor = new THREE.Mesh(new THREE.CircleGeometry(4, 32), new THREE.MeshLambertMaterial({ color: 0x8e8b80 }));
  floor.rotation.x = -Math.PI / 2;
  scene.add(floor);

  const npcs = CANDIDATES.map((c, i) => {
    const n = new NPC(c.def, c.look, (i - 1) * 1.15, 0, Math.PI); // yaw PI: facing +Z, toward the camera
    n.animate(0, 90, 1, false, 100);
    scene.add(n.group);
    return n;
  });

  const labels = document.createElement('div');
  labels.style.cssText = 'position:fixed;left:0;right:0;bottom:0;display:grid;grid-template-columns:repeat(3,1fr);gap:18px;padding:14px 22px;background:rgba(244,241,232,.94);font:13px/1.45 Helvetica,Arial,sans-serif;color:#2b2b2e';
  labels.innerHTML = CANDIDATES.map((c, i) => `<div><div style="font:700 11px monospace;letter-spacing:.14em;color:#8a7a60">${String.fromCharCode(65 + i)} · ${c.region.toUpperCase()}</div><b style="font-size:16px">${c.def.name}</b> <span style="opacity:.6">${c.def.role}</span><div style="font-style:italic;opacity:.75">${c.look.tagline}</div><div style="margin-top:4px;opacity:.85">${c.tells}</div></div>`).join('');
  document.body.append(labels);

  const cam = new THREE.PerspectiveCamera(30, 1, 0.05, 50);
  let mode: 'full' | 'faces' | 'turn' = 'full';
  const render = () => {
    const w = window.innerWidth, h = window.innerHeight - labels.offsetHeight;
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setScissorTest(true);
    renderer.setClearColor(0xdcdcd2);
    if (mode === 'full') {
      renderer.setViewport(0, labels.offsetHeight, w, h);
      renderer.setScissor(0, labels.offsetHeight, w, h);
      cam.aspect = w / h;
      cam.fov = 30;
      cam.position.set(0, 1.15, 6.2);
      cam.lookAt(0, 0.95, 0);
      cam.updateProjectionMatrix();
      renderer.render(scene, cam);
    } else {
      // One close-up viewport per character, slightly three-quarter so noses and hair read.
      const vw = w / 3;
      npcs.forEach((n, i) => {
        renderer.setViewport(i * vw, labels.offsetHeight, vw, h);
        renderer.setScissor(i * vw, labels.offsetHeight, vw, h);
        cam.aspect = vw / h;
        cam.fov = 22;
        const headY = 1.72 * n.look.height;
        const ang = mode === 'turn' ? 0.9 : 0.35;
        cam.position.set(n.x + Math.sin(ang) * 1.1, headY + 0.05, Math.cos(ang) * 1.1);
        cam.lookAt(n.x, headY - 0.03, 0);
        cam.updateProjectionMatrix();
        renderer.render(scene, cam);
      });
    }
  };
  (window as unknown as { __lineup: (m: typeof mode) => void }).__lineup = (m) => { mode = m; render(); };
  window.addEventListener('resize', render);
  render();
}
