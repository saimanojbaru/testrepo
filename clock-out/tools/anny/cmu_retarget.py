"""Retarget CMU motion capture (ASF/AMC; "may be copied, modified, or
redistributed without permission") onto an Anny body and bake glTF clips.

Limbs: swing-retarget by bone direction (handles CMU T-pose vs Anny A-pose).
Root/spine/neck/head: copy CMU world-rotation deltas. Walk is extracted as one
gait cycle, made in-place and turned to face +Z; its speed goes in the extras.
"""
import json, sys, re
import numpy as np
import anny_export as AE

IN_TO_M = (1 / 0.45) * 0.0254


def rx(a):
    c, s = np.cos(a), np.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = np.cos(a), np.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = np.cos(a), np.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler(x, y, z):
    return rz(np.radians(z)) @ ry(np.radians(y)) @ rx(np.radians(x))


def parse_asf(path):
    txt = open(path).read()
    bones = {'root': {'dir': np.zeros(3), 'len': 0, 'C': np.eye(3), 'dof': ['rx', 'ry', 'rz']}}
    for blk in re.findall(r'begin(.*?)end', txt.split(':bonedata')[1].split(':hierarchy')[0], re.S):
        name = re.search(r'name\s+(\S+)', blk).group(1)
        d = np.array([float(x) for x in re.search(r'direction\s+(\S+)\s+(\S+)\s+(\S+)', blk).groups()])
        ln = float(re.search(r'length\s+(\S+)', blk).group(1))
        ax = [float(x) for x in re.search(r'axis\s+(\S+)\s+(\S+)\s+(\S+)', blk).groups()]
        dm = re.search(r'dof\s+([^\n]+)', blk)
        bones[name] = {'dir': d, 'len': ln, 'C': euler(*ax), 'dof': dm.group(1).split() if dm else []}
    parent = {}
    for line in txt.split(':hierarchy')[1].split('begin')[1].split('end')[0].strip().splitlines():
        p, *ch = line.split()
        for c in ch:
            parent[c] = p
    return bones, parent


def parse_amc(path):
    frames, cur = [], None
    for line in open(path):
        line = line.strip()
        if not line or line[0] in '#:':
            continue
        if line.isdigit():
            cur = {}; frames.append(cur); continue
        parts = line.split()
        cur[parts[0]] = [float(x) for x in parts[1:]]
    return frames


def globals_for(bones, parent, frame):
    order = ['root']
    while len(order) < len(bones):
        for b in bones:
            if b not in order and parent.get(b) in order:
                order.append(b)
    G, pos = {}, None
    for b in order:
        v = frame.get(b, [])
        if b == 'root':
            pos = np.array(v[:3]) * IN_TO_M
            M = euler(v[3], v[4], v[5])
            G[b] = M
            continue
        vals = dict(zip(bones[b]['dof'], v))
        M = euler(vals.get('rx', 0), vals.get('ry', 0), vals.get('rz', 0))
        C = bones[b]['C']
        G[b] = G[parent[b]] @ C @ M @ C.T
    return G, pos


def swing(a, b):
    a = a / np.linalg.norm(a); b = b / np.linalg.norm(b)
    v = np.cross(a, b); c = np.dot(a, b)
    if np.linalg.norm(v) < 1e-8:
        return np.eye(3) if c > 0 else -np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx * (1 / (1 + c))


# CMU bone -> (Anny bone(s), mode). 'rot' copies world delta, 'dir' swings to the bone direction.
MAP = [
    ('root', ['root'], 'rot'),
    ('lowerback', ['spine05', 'spine04'], 'rot'), ('upperback', ['spine03'], 'rot'), ('thorax', ['spine02', 'spine01'], 'rot'),
    ('lowerneck', ['neck01'], 'rot'), ('upperneck', ['neck02', 'neck03'], 'rot'), ('head', ['head'], 'rot'),
    ('lhumerus', ['upperarm01.L'], 'dir'), ('lradius', ['lowerarm01.L'], 'dir'), ('lhand', ['wrist.L'], 'dir'),
    ('rhumerus', ['upperarm01.R'], 'dir'), ('rradius', ['lowerarm01.R'], 'dir'), ('rhand', ['wrist.R'], 'dir'),
    ('lfemur', ['upperleg01.L'], 'dir'), ('ltibia', ['lowerleg01.L'], 'dir'), ('lfoot', ['foot.L'], 'dir'),
    ('rfemur', ['upperleg01.R'], 'dir'), ('rtibia', ['lowerleg01.R'], 'dir'), ('rfoot', ['foot.R'], 'dir'),
]


def retarget(W, bones, parent, frames, heading_fix):
    B = AE.BONES; P = AE.PARENTS
    Rrest = [W[i][:3, :3] for i in range(len(B))]
    local_rest = [Rrest[i] if P[i] < 0 else Rrest[P[i]].T @ Rrest[i] for i in range(len(B))]
    # Anny bone axis: towards the first child's head (fallback: local Y).
    child = {}
    for i, p in enumerate(P):
        if p >= 0 and p not in child:
            child[p] = i
    adir = [None] * len(B)
    for i in range(len(B)):
        if i in child:
            d = W[child[i]][:3, 3] - W[i][:3, 3]
            adir[i] = d / (np.linalg.norm(d) + 1e-9)
        else:
            adir[i] = Rrest[i][:, 1]
    tracks, root_pos = [], []
    for fi, f in enumerate(frames):
        G, pos = globals_for(bones, parent, f)
        fix = heading_fix[fi] if isinstance(heading_fix, list) else heading_fix
        G = {k: fix @ v for k, v in G.items()}
        target = {}
        for cmu, ann, mode in MAP:
            for a in ann:
                i = B.index(a)
                if mode == 'rot':
                    target[i] = G[cmu] @ Rrest[i]
                else:
                    d = G[cmu] @ bones[cmu]['dir']
                    target[i] = swing(adir[i], d) @ Rrest[i]
        world = [None] * len(B)
        local = [None] * len(B)
        for i in range(len(B)):
            pw = np.eye(3) if P[i] < 0 else world[P[i]]
            if i in target:
                world[i] = target[i]
            else:
                world[i] = pw @ local_rest[i]
            local[i] = pw.T @ world[i]
        tracks.append(local)
        root_pos.append(fix @ pos)
    # Level the gaze: remove the clip-average neck/head offset (actors often walk looking up or down).
    for name in ['neck01', 'neck02', 'neck03', 'head']:
        if name not in B:
            continue
        i = B.index(name)
        D = sum(local_rest[i].T @ t[i] for t in tracks) / len(tracks)
        U, _, Vt = np.linalg.svd(D)
        avg = U @ Vt
        for t in tracks:
            t[i] = local_rest[i] @ avg.T @ (local_rest[i].T @ t[i])
    return tracks, np.array(root_pos)


def heading(root_pos):
    d = root_pos[-1] - root_pos[0]
    yaw = np.arctan2(d[0], d[2])  # angle of travel from +Z
    return ry(-yaw)


def quat(Rm):
    t, q, s = AE.mat_to_trs(np.vstack([np.hstack([Rm, np.zeros((3, 1))]), [0, 0, 0, 1]]))
    return q


def gait_cycle(frames, bones, parent):
    """Indices of one full cycle: between consecutive peaks of the left femur's forward swing."""
    sig = []
    for f in frames:
        G, _ = globals_for(bones, parent, f)
        sig.append((G['lfemur'] @ bones['lfemur']['dir'])[2])
    sig = np.array(sig)
    peaks = [i for i in range(2, len(sig) - 2) if sig[i] == max(sig[i - 2:i + 3]) and sig[i] > np.percentile(sig, 70)]
    # Require a plausible cycle length (0.8-1.6 s at 120 fps).
    for i in range(len(peaks) - 1):
        for j in range(i + 1, len(peaks)):
            if 96 <= peaks[j] - peaks[i] <= 192:
                return peaks[i], peaks[j]
    return peaks[0], peaks[1]


def clip(W, asf, amc, mode, fps_out=30):
    bones, parent = parse_asf(asf)
    frames = parse_amc(amc)
    raw_pos = np.array([np.array(f['root'][:3]) * IN_TO_M for f in frames])
    if mode == 'walk':
        # Find the steadiest 3 s of walking (speed 0.7-1.6 m/s, least variation), then a cycle inside it.
        sp = np.hypot(*np.diff(raw_pos[:, [0, 2]], axis=0).T) * 120
        win = 360
        if len(frames) > win + 10:
            best, bi = 1e9, 0
            for i in range(0, len(sp) - win, 30):
                seg = sp[i:i + win]; m = seg.mean()
                if 0.7 < m < 1.6 and seg.std() < best: best, bi = seg.std(), i
            frames = frames[bi:bi + win]; raw_pos = raw_pos[bi:bi + win]
        a, b = gait_cycle(frames, bones, parent)
        frames = frames[a:b + 1]
        raw_pos = raw_pos[a:b + 1]
        fix = heading(raw_pos)
    else:  # idle: the last second of a clip that ends standing, facing its last heading
        fix = heading(raw_pos[-200:-120])
        frames = frames[-120:]
        raw_pos = raw_pos[-120:]
    frames = frames[::120 // fps_out]
    tracks, rp = retarget(W, bones, parent, frames, fix)
    rp = rp - rp[0]
    speed = float(np.hypot(rp[-1, 0], rp[-1, 2]) / (len(frames) / fps_out)) if mode == 'walk' else 0.0
    bob = rp[:, 1] - rp[:, 1].mean()
    return {'tracks': tracks, 'bob': bob, 'fps': fps_out, 'speed': speed}


def add_animations(glb_path, W, clips):
    """Append clips to an exported .glb (rotations for every bone, root bob as translation)."""
    import struct
    data = open(glb_path, 'rb').read()
    jl = struct.unpack('<I', data[12:16])[0]
    js = json.loads(data[20:20 + jl])
    bin_start = 20 + jl + 8
    buf = bytearray(data[bin_start:])
    B = AE.BONES
    anims = []
    for name, c in clips.items():
        n = len(c['tracks'])
        times = (np.arange(n) / c['fps']).astype(np.float32)

        def add(arr, typ, minmax=False):
            nonlocal buf
            while len(buf) % 4:
                buf += b'\0'
            off = len(buf); buf += arr.tobytes()
            js['bufferViews'].append({'buffer': 0, 'byteOffset': off, 'byteLength': arr.nbytes})
            a = {'bufferView': len(js['bufferViews']) - 1, 'componentType': 5126, 'count': int(arr.shape[0]), 'type': typ}
            if minmax: a['min'] = [float(arr.min())]; a['max'] = [float(arr.max())]
            js['accessors'].append(a); return len(js['accessors']) - 1
        ti = add(times, 'SCALAR', True)
        samplers, channels = [], []
        for i in range(len(B)):
            q = np.array([quat(c['tracks'][f][i]) for f in range(n)], np.float32)
            # Keep quaternions on one hemisphere so interpolation doesn't flip.
            for f in range(1, n):
                if np.dot(q[f], q[f - 1]) < 0: q[f] = -q[f]
            samplers.append({'input': ti, 'output': add(q, 'VEC4'), 'interpolation': 'LINEAR'})
            channels.append({'sampler': len(samplers) - 1, 'target': {'node': i, 'path': 'rotation'}})
        r = B.index('root')
        t0 = np.array(js['nodes'][r]['translation'])
        tr = np.tile(t0, (n, 1)).astype(np.float32); tr[:, 1] += c['bob']
        samplers.append({'input': ti, 'output': add(tr, 'VEC3'), 'interpolation': 'LINEAR'})
        channels.append({'sampler': len(samplers) - 1, 'target': {'node': r, 'path': 'translation'}})
        anims.append({'name': name, 'samplers': samplers, 'channels': channels})
    js['animations'] = anims
    js['scenes'][0].setdefault('extras', {})['walkSpeed'] = clips.get('walk', {}).get('speed', 0)
    while len(buf) % 4:
        buf += b'\0'
    js['buffers'][0]['byteLength'] = len(buf)
    j = json.dumps(js, separators=(',', ':')).encode()
    while len(j) % 4:
        j += b' '
    with open(glb_path, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(j) + 8 + len(buf)))
        f.write(struct.pack('<II', len(j), 0x4E4F534A) + j)
        f.write(struct.pack('<II', len(buf), 0x004E4942) + bytes(buf))


if __name__ == '__main__':
    spec = [s for s in json.load(open(sys.argv[1])) if s['id'] == sys.argv[2]][0]
    out = sys.argv[3]
    AE.export(out, spec)
    v, W = AE.build(spec)
    walk = clip(W, 'cmu/02.asf', 'cmu/02_01.amc', 'walk')
    idle = clip(W, 'cmu/16.asf', 'cmu/16_33.amc', 'idle')
    add_animations(out, W, {'walk': walk, 'idle': idle})
    print('walk frames', len(walk['tracks']), 'speed m/s', round(walk['speed'], 2), 'idle frames', len(idle['tracks']))


def root_heading_fix(bones, parent, frame):
    G, _ = globals_for(bones, parent, frame)
    f = G['root'] @ np.array([0, 0, 1.0])
    return ry(-np.arctan2(f[0], f[2]))


def segment(W, asf, amc, fps_in, start_s, dur_s, fps_out=30, face='root'):
    bones, parent = parse_asf(asf)
    frames = parse_amc(amc)
    a = int(start_s * fps_in); b = min(len(frames), a + int(dur_s * fps_in))
    frames = frames[a:b]
    raw = np.array([np.array(f['root'][:3]) * IN_TO_M for f in frames])
    frames = frames[::max(1, fps_in // fps_out)]
    if face == 'travel':
        fix = heading(raw)
    else:
        # In-place clips: lock the root's yaw every frame so the AI owns facing (the actor's slow turn is removed).
        fix = [root_heading_fix(bones, parent, f) for f in frames]
    tracks, rp = retarget(W, bones, parent, frames, fix)
    rp = rp - rp[0]
    return {'tracks': tracks, 'bob': rp[:, 1] - rp[:, 1].mean(), 'fps': fps_out, 'speed': 0.0, 'nframes_src': len(frames)}


def batch(spec_path, char_id, out, clips):
    spec = [s for s in json.load(open(spec_path)) if s['id'] == char_id][0]
    AE.export(out, spec)
    v, W = AE.build(spec)
    baked = {}
    for name, subj, trial, fps, st, du, hd in clips:
        asf = f'cmu/{subj}.asf'; amc = f'cmu/{subj}_{trial:02d}.amc'
        baked[name] = segment(W, asf, amc, fps, st, du, face=hd)
        print(name, 'frames', len(baked[name]['tracks']))
    add_animations(out, W, baked)
