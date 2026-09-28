import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { loadGnmHead, setGnmExpression } from '../ai/GnmHead';
import { GNM_CAST } from '../data/gnmCast';

// Debug casting sheet (?gnmcast): renders baked GNM heads in a grid so identities
// can be picked for characters. ids come from ?ids=a,b,c (files in /proto/gnm/).
export async function showGnmCast(container: HTMLElement): Promise<void> {
  const q = new URLSearchParams(location.search);
  const ids = (q.get('ids') ?? 'ramesh,kavita,rinku,priya,rohit,srinivas,lakshmi,deepak,sanjay').split(',');
  const dir = q.get('dir') ?? 'gnm';
  const cols = parseInt(q.get('cols') ?? '5'), rows = Math.ceil(ids.length / cols);
  const cw = 300, ch = 360, W = cols * cw, H = rows * ch;
  const yaw = parseFloat(q.get('yaw') ?? '0.45');
  const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.setSize(W, H);
  renderer.setScissorTest(true);
  container.append(renderer.domElement);
  const env = new THREE.PMREMGenerator(renderer).fromScene(new RoomEnvironment(), 0.04).texture;
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xcfd6cc);
  scene.environment = env;
  scene.environmentIntensity = 0.25;
  scene.add(new THREE.HemisphereLight(0xf2f5ee, 0x8a8270, 0.7));
  const key = new THREE.DirectionalLight(0xfff4e6, 1.5);
  key.position.set(1.2, 1.6, 2.2);
  const fill = new THREE.DirectionalLight(0xdfe6f0, 0.45);
  fill.position.set(-2, 0.5, 1);
  scene.add(key, fill);
  const cam = new THREE.PerspectiveCamera(24, cw / ch, 0.01, 10);
  for (let i = 0; i < ids.length; i++) {
    const [file, expr] = ids[i].split(':');
    const cast = GNM_CAST[file];
    const head = await loadGnmHead(`${dir}/${file}.glb`, cast?.markers ?? []);
    if (cast?.rest && !expr) setGnmExpression(head.group, cast.rest);
    if (expr) setGnmExpression(head.group, Object.fromEntries(expr.split('+').map((e) => { const [k, v] = e.split('='); return [k, parseFloat(v ?? '1')]; })));
    scene.add(head.group);
    const c = head.box.getCenter(new THREE.Vector3());
    cam.position.set(c.x + Math.sin(yaw) * 0.85, c.y + 0.03, c.z + Math.cos(yaw) * 0.85);
    cam.lookAt(c.x, c.y + 0.01, c.z);
    const x = (i % cols) * cw, y = H - (Math.floor(i / cols) + 1) * ch;
    renderer.setViewport(x, y, cw, ch);
    renderer.setScissor(x, y, cw, ch);
    renderer.render(scene, cam);
    scene.remove(head.group);
  }
  (window as unknown as { protoReady: boolean }).protoReady = true;
}
