"""Bake Anny (Apache-2.0, MakeHuman CC0 assets) bodies with sewn-on clothes into
rigged .glb files for CLOCK OUT.

Bodies are synthetic (phenotype sliders), no scans. Anny's own head is cut off
at the neck: the game seats the GNM head there. Clothes are built from the body
itself: a garment is the body surface over a region, pushed out along the normal,
so it shares the body's skin weights and bends with the skeleton for free. Loose
parts (kurta skirt, saree, dupatta, pallu) are generated tubes and ribbons,
skinned to the pelvis/legs/spine.

Coordinates: Anny is Z-up with the hips at the origin; glTF is Y-up. We map
(x, y, z) -> (x, z, -y), so the character faces +Z, and lift the feet to y = 0.
"""
import json, struct, sys
import numpy as np
import torch
import anny

M = anny.Anny(local_changes="default").to(dtype=torch.float32)
BONES = list(M.bone_labels)
PARENTS = [int(p) for p in (M.bone_parents.tolist() if hasattr(M.bone_parents, 'tolist') else M.bone_parents)]
FACES = M.get_triangular_faces().numpy().astype(np.int64)
VBI = M.vertex_bone_indices.numpy().astype(np.int64)
VBW = M.vertex_bone_weights.numpy().astype(np.float64)
C = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], np.float64)  # z-up -> y-up, facing +Z


def srgb_lin(h):
    h = int(h.lstrip('#'), 16) if isinstance(h, str) else h
    c = np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255]) / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def bone_weight(names):
    """Per-vertex total weight on bones whose label starts with any of names."""
    idx = [i for i, b in enumerate(BONES) if any(b.startswith(n) for n in names)]
    w = np.zeros(len(VBI))
    for i in idx:
        w += (VBW * (VBI == i)).sum(1)
    return w


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def build(spec):
    out = M(phenotype_kwargs={k: spec['phenotype'].get(k, 0.5) for k in M.phenotype_labels},
            local_changes_kwargs=spec.get('local', {}))
    v = out['rest_vertices'][0].numpy().astype(np.float64) @ C.T
    poses = out['rest_bone_poses'][0].numpy().astype(np.float64)
    ground = -v[:, 1].min()
    v[:, 1] += ground
    W = np.zeros_like(poses)
    for i, P in enumerate(poses):
        R = C @ P[:3, :3] @ C.T
        t = C @ P[:3, 3] + np.array([0, ground, 0])
        W[i, :3, :3] = R
        W[i, :3, 3] = t
        W[i, 3, 3] = 1
    return v, W


def normals(p, tris):
    n = np.zeros_like(p)
    fn = np.cross(p[tris[:, 1]] - p[tris[:, 0]], p[tris[:, 2]] - p[tris[:, 0]])
    for k in range(3):
        np.add.at(n, tris[:, k], fn)
    return n / (np.linalg.norm(n, axis=1, keepdims=True) + 1e-12)


def top4(vbi, vbw):
    order = np.argsort(-vbw, axis=1)[:, :4]
    j = np.take_along_axis(vbi, order, 1)
    w = np.take_along_axis(vbw, order, 1)
    w = w / (w.sum(1, keepdims=True) + 1e-12)
    j[w == 0] = 0
    return j.astype(np.uint16), w.astype(np.float32)


# --- Garments --------------------------------------------------------------

def region_masks(v):
    torso = bone_weight(['spine', 'clavicle', 'shoulder01', 'breast', 'pelvis', 'root'])
    neck = bone_weight(['neck', 'head', 'eye', 'jaw', 'tongue', 'levator', 'orbicularis', 'oris', 'temporalis', 'risorius', 'special'])
    upper_arm = bone_weight(['upperarm'])
    lower_arm = bone_weight(['lowerarm'])
    hand = bone_weight(['wrist', 'finger', 'thumb', 'index', 'middle', 'ring', 'pinky', 'metacarpal'])
    thigh = bone_weight(['upperleg'])
    shin = bone_weight(['lowerleg'])
    foot = bone_weight(['foot', 'toe'])
    return dict(torso=torso, neck=neck, upper_arm=upper_arm, lower_arm=lower_arm, hand=hand, thigh=thigh, shin=shin, foot=foot)


_ADJ = None


def adjacency():
    global _ADJ
    if _ADJ is None:
        import scipy.sparse as sp
        f = FACES
        rows = np.concatenate([f[:, 0], f[:, 1], f[:, 2], f[:, 1], f[:, 2], f[:, 0]])
        cols = np.concatenate([f[:, 1], f[:, 2], f[:, 0], f[:, 0], f[:, 1], f[:, 2]])
        A = sp.coo_matrix((np.ones(len(rows)), (rows, cols)), shape=(len(VBI), len(VBI))).tocsr()
        A.data[:] = 1
        deg = np.asarray(A.sum(1)).ravel()
        _ADJ = sp.diags(1 / np.maximum(deg, 1)) @ A
    return _ADJ


def shell(v, n, mask, thick, name, color, extra_keep=None, smooth=0):
    keep = (mask[FACES] > 0.5).all(1)
    if extra_keep is not None:
        keep &= extra_keep
    q = v + n * (thick * np.clip(mask, 0, 1))[:, None]
    if smooth:
        # Fabric doesn't follow every muscle: relax the shell (inside the garment only).
        A = adjacency()
        m = (np.clip(mask, 0, 1) > 0.5)[:, None]
        for _ in range(smooth):
            q = np.where(m, 0.5 * q + 0.5 * (A @ q), q)
        # Never sink back inside the body.
        d = ((q - v) * n).sum(1)
        q = q + n * np.maximum(0, thick * 0.6 - d)[:, None] * m
    return {'name': name, 'pos': q, 'idx': FACES[keep], 'color': color, 'skin_from_body': True}


def band(v, n, a, b, c, width, thick, name, color, base_mask=None, smooth=6):
    """A drape band across the body surface: vertices within width/2 of the plane through a, b, c
    (i.e. a sash from a to b), limited to front/back by base_mask."""
    nrm = np.cross(b - a, c - a); nrm /= np.linalg.norm(nrm)
    d = np.abs((v - a) @ nrm)
    w = smoothstep(width / 2 + 0.01, width / 2 - 0.01, d)
    if base_mask is not None:
        w = w * base_mask
    return shell(v, n, w, thick, name, color, smooth=smooth)


def tube(v, W, y_top, y_bottom, flare, color, name, rings=10, seg=40, open_front=False, weights=None):
    """A skirt-like tube from y_top down to y_bottom, fitted to the body's widest
    cross-section in that band and flaring by `flare` metres at the hem."""
    band = (v[:, 1] < y_top) & (v[:, 1] > y_bottom) & (bone_weight(['spine', 'pelvis', 'root', 'upperleg', 'lowerleg']) > 0.6)
    cx, cz = np.median(v[band, 0]), np.median(v[band, 2])
    # Radius per angle from the body's silhouette in the top band (hips).
    core = (bone_weight(['spine', 'pelvis', 'root', 'upperleg', 'lowerleg']) > 0.6)
    hip = v[(v[:, 1] < y_top) & (v[:, 1] > y_top - 0.25) & core]
    ang = np.arctan2(hip[:, 2] - cz, hip[:, 0] - cx)
    rad = np.hypot(hip[:, 0] - cx, hip[:, 2] - cz)
    angles = np.linspace(-np.pi, np.pi, seg, endpoint=False)
    base = np.array([rad[np.abs(((ang - a + np.pi) % (2 * np.pi)) - np.pi) < 0.35].max(initial=0.12) for a in angles]) + 0.012
    pos, idx = [], []
    for r in range(rings + 1):
        t = r / rings
        y = y_top + (y_bottom - y_top) * t
        for s, a in enumerate(angles):
            rr = base[s] + flare * t ** 1.5
            # Gentle pleat ripple.
            rr += 0.006 * np.sin(a * 9) * t
            pos.append([cx + np.cos(a) * rr, y, cz + np.sin(a) * rr])
    for r in range(rings):
        for s in range(seg):
            a, b = r * seg + s, r * seg + (s + 1) % seg
            c, d = a + seg, b + seg
            idx += [[a, c, b], [b, c, d]]
    pos = np.array(pos)
    # Skinning: top rings follow the pelvis; lower rings share with the thighs so walking moves the hem.
    pel = BONES.index('root')
    ul, ur = BONES.index('upperleg01.L'), BONES.index('upperleg01.R')
    j = np.zeros((len(pos), 4), np.uint16)
    w = np.zeros((len(pos), 4), np.float32)
    for i, p in enumerate(pos):
        t = (y_top - p[1]) / (y_top - y_bottom)
        side = np.clip((p[0] - cx) / 0.12, -1, 1)
        leg = 0.45 * t
        j[i] = [pel, ul, ur, 0]
        w[i] = [1 - leg, leg * (0.5 + 0.5 * side), leg * (0.5 - 0.5 * side), 0]
    return {'name': name, 'pos': pos, 'idx': np.array(idx), 'color': color, 'joints': j, 'weights': w, 'double': True}


def ribbon(points, width, color, name, bone_names, thick_dir=None, seg_per=6):
    """A flat cloth strip along a polyline (dupatta, pallu), skinned rigidly-ish to given bones."""
    pts = np.array(points, np.float64)
    # Resample along the polyline.
    res = []
    for a, b in zip(pts[:-1], pts[1:]):
        for k in range(seg_per):
            res.append(a + (b - a) * k / seg_per)
    res.append(pts[-1])
    res = np.array(res)
    pos, idx = [], []
    for i, p in enumerate(res):
        tng = res[min(i + 1, len(res) - 1)] - res[max(i - 1, 0)]
        tng /= np.linalg.norm(tng) + 1e-9
        side = np.cross(tng, thick_dir if thick_dir is not None else np.array([0, 0, 1.0]))
        side /= np.linalg.norm(side) + 1e-9
        pos += [p - side * width / 2, p + side * width / 2]
    for i in range(len(res) - 1):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 2, 2 * i + 3
        idx += [[a, c, b], [b, c, d]]
    pos = np.array(pos)
    bi = [BONES.index(b) for b in bone_names]
    j = np.zeros((len(pos), 4), np.uint16)
    w = np.zeros((len(pos), 4), np.float32)
    for i in range(len(pos)):
        t = (i // 2) / max(1, len(res) - 1)
        # Blend along the strip from the first bone to the last.
        k = t * (len(bi) - 1)
        a = int(np.floor(k)); b = min(a + 1, len(bi) - 1); f = k - a
        j[i] = [bi[a], bi[b], 0, 0]
        w[i] = [1 - f, f, 0, 0]
    return {'name': name, 'pos': pos, 'idx': np.array(idx), 'color': color, 'joints': j, 'weights': w, 'double': True}


def dress(spec, v, W):
    n = normals(v, FACES)
    R = region_masks(v)
    y = v[:, 1]
    outfit = spec['outfit']
    kind = outfit['kind']
    parts = []
    head_y = W[BONES.index('neck01'), 1, 3]
    waist_y = W[BONES.index('spine03'), 1, 3]
    hip_y = W[BONES.index('upperleg01.L'), 1, 3]
    knee_y = W[BONES.index('lowerleg01.L'), 1, 3]
    ankle_y = W[BONES.index('foot.L'), 1, 3]
    body_neck_cut = smoothstep(head_y - 0.01, head_y - 0.05, y)  # loose garments (kurta, blouse) stop below the neck
    collar_cut = smoothstep(head_y + 0.05, head_y + 0.02, y)       # shirts wrap the neck base like a collar
    feet = np.clip(R['foot'], 0, 1)
    parts.append(shell(v, n, np.clip(feet * 1.2, 0, 1), 0.012, 'shoes', outfit.get('shoes', '1e1c1a')))
    if kind in ('shirt', 'uniform'):
        sleeve = R['upper_arm'] + (R['lower_arm'] if outfit.get('fullSleeve') else R['lower_arm'] * smoothstep(0.35, 0.8, R['upper_arm'] + 0.5 * R['lower_arm']))
        top = np.clip(R['torso'] + sleeve + R['neck'] * (y < head_y + 0.05), 0, 1) * collar_cut * smoothstep(hip_y - 0.02, hip_y + 0.05, y)
        parts.append(shell(v, n, top, 0.016, 'shirt', outfit['top'], smooth=14))
        legs = np.clip(R['thigh'] + R['shin'] + R['torso'] * smoothstep(hip_y + 0.12, hip_y + 0.06, y), 0, 1) * smoothstep(ankle_y + 0.01, ankle_y + 0.05, y)
        legs_thick = 0.014 + 0.012 * smoothstep(hip_y + 0.0, hip_y + 0.06, y)  # waistband over the tucked shirt
        tq = shell(v, n, legs, 1.0, 'trousers', outfit['bottom'], smooth=10)
        tq['pos'] = v + n * (legs_thick * np.clip(legs, 0, 1))[:, None]
        A = adjacency(); m = (np.clip(legs, 0, 1) > 0.5)[:, None]
        for _ in range(10):
            tq['pos'] = np.where(m, 0.5 * tq['pos'] + 0.5 * (A @ tq['pos']), tq['pos'])
        d = ((tq['pos'] - v) * n).sum(1)
        tq['pos'] = tq['pos'] + n * np.maximum(0, legs_thick * 0.8 - d)[:, None] * m
        parts.append(tq)
        belt = np.clip(R['torso'], 0, 1) * smoothstep(hip_y + 0.03, hip_y + 0.06, y) * smoothstep(hip_y + 0.11, hip_y + 0.08, y)
        parts.append(shell(v, n, belt, 0.03, 'belt', outfit.get('belt', '2a2016'), smooth=4))
    elif kind == 'kurta':
        sleeve = R['upper_arm'] + R['lower_arm'] * 0.5
        top = np.clip(R['torso'] + sleeve, 0, 1) * body_neck_cut
        parts.append(shell(v, n, top, 0.014, 'kurta', outfit['top'], smooth=12))
        parts.append(tube(v, W, hip_y + 0.08, knee_y + 0.02, 0.07, outfit['top'], 'kurtaSkirt'))
        legs = np.clip(R['thigh'] + R['shin'], 0, 1) * smoothstep(ankle_y + 0.005, ankle_y + 0.03, y)
        parts.append(shell(v, n, legs, 0.006, 'churidar', outfit['bottom']))
        if outfit.get('dupatta'):
            # Worn as a sash: left shoulder across to the right hip.
            sL = W[BONES.index('clavicle.L'), :3, 3]
            hipR = W[BONES.index('upperleg01.R'), :3, 3]
            torso = np.clip(R['torso'], 0, 1) * body_neck_cut
            a0 = hipR + np.array([-0.05, 0.14, 0.0]); b0 = sL + np.array([0.04, 0.02, 0.0])
            parts.append(band(v, n, a0, b0, a0 + np.array([0, 0, 1.0]), 0.16, 0.028, 'dupatta', outfit['dupatta'], base_mask=torso))
    elif kind == 'saree':
        blouse = np.clip(R['torso'] + R['upper_arm'] * 0.8, 0, 1) * body_neck_cut * smoothstep(waist_y + 0.02, waist_y + 0.06, y)
        parts.append(shell(v, n, blouse, 0.01, 'blouse', outfit['blouse'], smooth=8))
        parts.append(tube(v, W, waist_y + 0.02, ankle_y - 0.02, 0.1, outfit['top'], 'saree'))
        # Pallu: a diagonal sash from the right hip over the left shoulder.
        sL = W[BONES.index('clavicle.L'), :3, 3]
        hipR = W[BONES.index('upperleg01.R'), :3, 3]
        torso = np.clip(R['torso'], 0, 1) * body_neck_cut
        a0 = hipR + np.array([-0.05, 0.1, 0.0]); b0 = sL + np.array([0.04, 0.02, 0.0])
        parts.append(band(v, n, a0, b0, a0 + np.array([0, 0, 1.0]), 0.2, 0.03, 'pallu', outfit.get('pallu', outfit['top']), base_mask=torso))
        if outfit.get('apron'):
            pass  # (apron: TODO as a panel over the saree tube)
    return parts


# --- glTF writer (skinned) ---------------------------------------------------

def mat_to_trs(Mx):
    t = Mx[:3, 3]
    R = Mx[:3, :3]
    s = np.linalg.norm(R, axis=0)
    R = R / s
    # quaternion from rotation matrix
    tr = np.trace(R)
    if tr > 0:
        S = np.sqrt(tr + 1.0) * 2; w = 0.25 * S; x = (R[2, 1] - R[1, 2]) / S; y = (R[0, 2] - R[2, 0]) / S; z = (R[1, 0] - R[0, 1]) / S
    elif R[0, 0] > R[1, 1] and R[0, 0] > R[2, 2]:
        S = np.sqrt(1.0 + R[0, 0] - R[1, 1] - R[2, 2]) * 2; w = (R[2, 1] - R[1, 2]) / S; x = 0.25 * S; y = (R[0, 1] + R[1, 0]) / S; z = (R[0, 2] + R[2, 0]) / S
    elif R[1, 1] > R[2, 2]:
        S = np.sqrt(1.0 + R[1, 1] - R[0, 0] - R[2, 2]) * 2; w = (R[0, 2] - R[2, 0]) / S; x = (R[0, 1] + R[1, 0]) / S; y = 0.25 * S; z = (R[1, 2] + R[2, 1]) / S
    else:
        S = np.sqrt(1.0 + R[2, 2] - R[0, 0] - R[1, 1]) * 2; w = (R[1, 0] - R[0, 1]) / S; x = (R[0, 2] + R[2, 0]) / S; y = (R[1, 2] + R[2, 1]) / S; z = 0.25 * S
    q = np.array([x, y, z, w]); q /= np.linalg.norm(q)
    return t.tolist(), q.tolist(), s.tolist()


def write_glb(path, W, prims, extras):
    buf = bytearray()
    views, accessors, materials, gprims = [], [], [], []
    mats = {}

    def view(data, target=None, stride=None):
        nonlocal buf
        while len(buf) % 4:
            buf += b'\0'
        off = len(buf); buf += data
        vw = {'buffer': 0, 'byteOffset': off, 'byteLength': len(data)}
        if target: vw['target'] = target
        if stride: vw['byteStride'] = stride
        views.append(vw); return len(views) - 1

    def acc(arr, comp, typ, target=None, minmax=False, normalized=False, stride=None):
        a = {'bufferView': view(arr.tobytes(), target, stride), 'componentType': comp, 'count': int(arr.shape[0]), 'type': typ}
        if normalized: a['normalized'] = True
        if minmax: a['min'] = arr.min(0).tolist(); a['max'] = arr.max(0).tolist()
        accessors.append(a); return len(accessors) - 1

    for pr in prims:
        used = np.unique(pr['idx'])
        remap = -np.ones(len(pr['pos']), np.int64); remap[used] = np.arange(len(used))
        idx = remap[pr['idx']].astype(np.uint32).reshape(-1)
        if pr.get('double'):
            t = idx.reshape(-1, 3)
            idx = np.concatenate([t, t[:, ::-1]]).reshape(-1).astype(np.uint32)
        pos = pr['pos'][used].astype(np.float32)
        attrs = {'POSITION': acc(pos, 5126, 'VEC3', 34962, True)}
        j, w = pr['joints'][used], pr['weights'][used]
        attrs['JOINTS_0'] = acc(j.astype(np.uint16), 5123, 'VEC4', 34962)
        attrs['WEIGHTS_0'] = acc(w.astype(np.float32), 5126, 'VEC4', 34962)
        if pr.get('col') is not None:
            c = np.ones((len(used), 4), np.float32); c[:, :3] = pr['col'][used]
            attrs['COLOR_0'] = acc(np.clip(np.round(c * 255), 0, 255).astype(np.uint8), 5121, 'VEC4', 34962, normalized=True, stride=4)
        ind = acc(idx, 5125, 'SCALAR', 34963)
        key = pr['name'] + ':' + str(pr.get('color', ''))
        if key not in mats:
            mats[key] = len(materials)
            materials.append({'name': pr['name'], 'extras': {'color': pr.get('color'), 'kind': pr['name']}})
        gprims.append({'attributes': attrs, 'indices': ind, 'material': mats[key]})
    # Skeleton nodes: node i = bone i; mesh node last.
    nodes = []
    for i, b in enumerate(BONES):
        p = PARENTS[i]
        local = W[i] if p < 0 else np.linalg.inv(W[p]) @ W[i]
        t, q, s = mat_to_trs(local)
        nodes.append({'name': b, 'translation': t, 'rotation': q, 'scale': s})
    for i, p in enumerate(PARENTS):
        if p >= 0:
            nodes[p].setdefault('children', []).append(i)
    ibm = np.stack([np.linalg.inv(Wi) for Wi in W]).astype(np.float32).transpose(0, 2, 1)  # column-major
    ibm_acc = acc(ibm.reshape(len(BONES), 16), 5126, 'MAT4')
    mesh_node = len(nodes)
    nodes.append({'name': 'body', 'mesh': 0, 'skin': 0})
    roots = [i for i, p in enumerate(PARENTS) if p < 0]
    gltf = {'asset': {'version': '2.0', 'generator': 'clockout-anny-export'}, 'scene': 0,
            'scenes': [{'nodes': roots + [mesh_node], 'extras': extras}], 'nodes': nodes,
            'meshes': [{'primitives': gprims}], 'skins': [{'joints': list(range(len(BONES))), 'inverseBindMatrices': ibm_acc, 'skeleton': roots[0]}],
            'materials': materials, 'buffers': [{'byteLength': 0}], 'bufferViews': views, 'accessors': accessors}
    while len(buf) % 4:
        buf += b'\0'
    gltf['buffers'][0]['byteLength'] = len(buf)
    js = json.dumps(gltf, separators=(',', ':')).encode()
    while len(js) % 4:
        js += b' '
    with open(path, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(buf)))
        f.write(struct.pack('<II', len(js), 0x4E4F534A) + js)
        f.write(struct.pack('<II', len(buf), 0x004E4942) + bytes(buf))


def export(path, spec):
    v, W = build(spec)
    j4, w4 = top4(VBI, VBW)
    R = region_masks(v)
    # Cut Anny's head: drop triangles mostly weighted to neck02+/head/face bones.
    headw = bone_weight(['head', 'eye', 'jaw', 'tongue', 'levator', 'orbicularis', 'oris', 'temporalis', 'risorius', 'special'])
    body_tris = FACES[(headw[FACES] < 0.5).all(1)]
    skin = np.tile(srgb_lin(spec['skin']), (len(v), 1))
    prims = [{'name': 'skin', 'pos': v, 'idx': body_tris, 'col': skin, 'joints': j4, 'weights': w4}]
    for g in dress(spec, v, W):
        if g.get('skin_from_body'):
            g['joints'], g['weights'] = j4, w4
        if len(g['idx']):
            prims.append(g)
    neck = W[BONES.index('neck02'), :3, 3]
    extras = {'id': spec['id'], 'skin': spec['skin'], 'neck': neck.tolist(), 'height': float(v[:, 1].max())}
    write_glb(path, W, prims, extras)


if __name__ == '__main__':
    specs = json.load(open(sys.argv[1]))
    for s in specs:
        export(f"{sys.argv[2]}/{s['id']}.glb", s)
        print('wrote', s['id'])
