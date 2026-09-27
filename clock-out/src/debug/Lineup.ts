import * as THREE from 'three';
import { NPC } from '../ai/NPC';
import type { NPCDef, NPCLook } from '../data/types';
import { NPC_DEFS, NPC_LOOKS } from '../data/npcs';

// Debug-only character lineup (?lineup): renders candidate characters full-body and
// as face close-ups so looks can be compared and signed off before they ship.

const REGION: Record<string, string> = {
  ramesh: 'Chennai, Tamil Nadu', kavita: 'Pune, Maharashtra', rinku: 'Jorhat, Assam', priya: 'Thrissur, Kerala',
  rohit: 'Ludhiana, Punjab', srinivas: 'Secunderabad, Telangana', lakshmi: 'Warangal, Telangana',
  deepak: 'Mangaluru, Karnataka', sanjay: 'Kolkata, West Bengal',
};

interface Candidate { def: NPCDef; look: NPCLook; region: string; }

const CANDIDATES: Candidate[] = Object.values(NPC_DEFS).map((def) => ({ def, look: NPC_LOOKS[def.id], region: REGION[def.id] ?? '' }));

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
    const n = new NPC(c.def, c.look, (i - (CANDIDATES.length - 1) / 2) * 1.0, 0, Math.PI); // yaw PI: facing +Z, toward the camera
    n.animate(0, 90, 1, false, 100);
    scene.add(n.group);
    return n;
  });

  const labels = document.createElement('div');
  labels.style.cssText = 'position:fixed;left:0;right:0;bottom:0;display:grid;grid-template-columns:repeat(9,1fr);gap:10px;padding:10px 14px;background:rgba(244,241,232,.94);font:12px/1.35 Helvetica,Arial,sans-serif;color:#2b2b2e';
  labels.innerHTML = CANDIDATES.map((c) => `<div><div style="font:700 9px monospace;letter-spacing:.08em;color:#8a7a60">${c.region.toUpperCase()}</div><b style="font-size:13px">${c.def.name}</b><div style="opacity:.6;font-size:11px">${c.def.role}</div></div>`).join('');
  document.body.append(labels);

  const cam = new THREE.PerspectiveCamera(30, 1, 0.05, 50);
  let mode: 'full' | 'faces' | 'turn' = 'full';
  const render = () => {
    const w = window.innerWidth, h = window.innerHeight - labels.offsetHeight;
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setScissorTest(false);
    renderer.setClearColor(0xdcdcd2);
    renderer.clear();
    renderer.setScissorTest(true);
    renderer.setClearColor(0xdcdcd2);
    if (mode === 'full') {
      renderer.setViewport(0, labels.offsetHeight, w, h);
      renderer.setScissor(0, labels.offsetHeight, w, h);
      cam.aspect = w / h;
      cam.fov = 30;
      cam.position.set(0, 1.15, 9.5);
      cam.lookAt(0, 0.95, 0);
      cam.updateProjectionMatrix();
      renderer.render(scene, cam);
    } else {
      // One close-up viewport per character, in a grid, slightly three-quarter so noses and hair read.
      const cols = Math.min(npcs.length, 5);
      const rows = Math.ceil(npcs.length / cols);
      const vw = w / cols, vh = h / rows;
      npcs.forEach((n, i) => {
        const x = (i % cols) * vw, y = labels.offsetHeight + (rows - 1 - Math.floor(i / cols)) * vh;
        renderer.setViewport(x, y, vw, vh);
        renderer.setScissor(x, y, vw, vh);
        cam.aspect = vw / vh;
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
