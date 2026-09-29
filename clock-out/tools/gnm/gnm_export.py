"""Bake synthetic GNM Head identities into small .glb files for CLOCK OUT.

No scans, no real people: every face is a point in GNM's identity space
(Apache-2.0 model, google/gnm-v3). Regional identity is carried the way the
game already does it -- skin tone, grooming, hair, one cultural marker --
never by exaggerating bone structure.

Per head we write one mesh with several primitives (skin, hair shell, eyes,
teeth, tongue, mouth interior), vertex-colour skin detail painted from the 68
landmarks (brows, lash line, lips, stubble), and anchor points in the glTF
extras so the game can place markers (tilak, bindi, jhumka) on the surface.
"""
import json, struct, sys
import numpy as np

M = np.load(sys.argv[1] if len(sys.argv) > 1 else 'gnm_head.npz')
LM = np.loadtxt('repo/gnm/shape/data/landmarks/head_sparse_68.txt')
LM_IDX = LM[:, ::2].astype(int)
LM_W = LM[:, 1::2]
G = dict(zip(M['vertex_group_names'], M['vertex_groups']))
TEMPLATE = M['template_vertex_positions']
ID_BASIS = M['vertex_identity_basis']
EXPR_BASIS = M['expression_basis']
TRIS = M['triangles']
NAMES = list(M['identity_names'])
HEAD_IDS = [i for i, n in enumerate(NAMES) if n.startswith('head')]


def srgb(h):
    if isinstance(h, str):
        h = int(h.lstrip('#'), 16)
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255]) / 255.0


def hexint(h):
    return int(h.lstrip('#'), 16) if isinstance(h, str) else h


def lin(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


_DEC = None


def decode_identity(seed, female, latent_scale=1.0):
    """GNM's own identity CVAE decoder (numpy port of the 5 dense layers).
    Label = [female, male] + ethnicity weights; we keep ethnicity uniform so no
    face is pushed toward any one dataset category."""
    global _DEC
    if _DEC is None:
        import h5py
        f = h5py.File('repo/gnm/shape/data/semantic_sampler/identity_decoder_model.h5', 'r')['model_weights']
        _DEC = [(f[f'dense_{i}/dense_{i}/kernel:0'][()], f[f'dense_{i}/dense_{i}/bias:0'][()]) for i in range(4, 9)]
    rng = np.random.default_rng(seed)
    z = rng.normal(0, 1, 64) * latent_scale
    label = np.array([1.0, 0.0] if female else [0.0, 1.0], np.float32)
    x = np.concatenate([z, label, np.full(4, 0.25)]).astype(np.float32)
    for i, (k, b) in enumerate(_DEC):
        x = x @ k + b
        if i < len(_DEC) - 1:
            x = np.maximum(x, 0)
    return x.astype(np.float32)


def identity(seed, scale=1.0, overrides=None, female=None, latent_scale=1.0):
    if female is not None:
        v = decode_identity(seed, female, latent_scale)
        for k, x in (overrides or {}).items():
            v[NAMES.index(k)] += x
        return v
    rng = np.random.default_rng(seed)
    v = np.zeros(len(NAMES), np.float32)
    v[HEAD_IDS] = rng.normal(0, 1, len(HEAD_IDS)) * scale
    for k, x in (overrides or {}).items():
        v[NAMES.index(k)] = x
    return v


def vertices(id_vec, expr_vec=None):
    p = TEMPLATE + np.tensordot(id_vec, ID_BASIS, axes=1)
    if expr_vec is not None:
        p = p + np.tensordot(expr_vec, EXPR_BASIS, axes=1)
    return p.astype(np.float32)


def landmarks(p):
    return (p[LM_IDX] * LM_W[..., None]).sum(1)


def normals(p, tris):
    n = np.zeros_like(p)
    fn = np.cross(p[tris[:, 1]] - p[tris[:, 0]], p[tris[:, 2]] - p[tris[:, 0]])
    for k in range(3):
        np.add.at(n, tris[:, k], fn)
    n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
    return n.astype(np.float32)


def dist_to_polyline(p, pts):
    d = np.full(len(p), 1e9)
    for a, b in zip(pts[:-1], pts[1:]):
        ab = b - a
        t = np.clip(((p - a) @ ab) / (ab @ ab), 0, 1)
        d = np.minimum(d, np.linalg.norm(p - (a + t[:, None] * ab), axis=1))
    return d


def assign_triangles():
    """Majority vertex group per triangle -> material slot."""
    order = [('pupil', 'pupils'), ('iris', 'irises'), ('sclera', 'scleras'), ('cornea', 'eye_exteriors'),
             ('eyeint', 'eye_interiors'), ('teeth', 'upper_teeth_and_gums'), ('teeth', 'lower_teeth_and_gums'),
             ('tongue', 'tongue'), ('mouth', 'mouth_sock')]
    slot = np.array(['skin'] * len(TRIS), dtype=object)
    done = np.zeros(len(TRIS), bool)
    for name, grp in order:
        w = G[grp][TRIS].mean(1) > 0.5
        sel = w & ~done
        slot[sel] = name
        done |= sel
    return slot


SLOTS = assign_triangles()


_SOFT = {}


def soft_group(name, iters=12):
    """Vertex-group weight diffused over the mesh so painted regions have no hard edges."""
    if name not in _SOFT:
        import scipy.sparse as sp
        f = TRIS
        rows = np.concatenate([f[:, 0], f[:, 1], f[:, 2], f[:, 1], f[:, 2], f[:, 0]])
        cols = np.concatenate([f[:, 1], f[:, 2], f[:, 0], f[:, 0], f[:, 1], f[:, 2]])
        A = sp.coo_matrix((np.ones(len(rows)), (rows, cols)), shape=(len(TEMPLATE), len(TEMPLATE))).tocsr()
        A.data[:] = 1
        A = sp.diags(1 / np.maximum(np.asarray(A.sum(1)).ravel(), 1)) @ A
        w = G[name].astype(np.float64)
        for _ in range(iters):
            w = 0.5 * w + 0.5 * (A @ w)
        _SOFT[name] = w
    return _SOFT[name]


def paint_skin(p, spec):
    """Vertex colours (linear) for the skin: base tone plus brows, lash line, lips, stubble."""
    lm = landmarks(p)
    base = lin(srgb(spec['skin']))
    col = np.tile(base, (len(p), 1))
    # Gentle warmth on cheeks/nose, slightly darker under the eyes.
    for grp, tint, k in [('nose_region', [1.04, 0.98, 0.96], 0.6), ('left_cheek_region', [1.04, 0.97, 0.95], 0.5),
                         ('right_cheek_region', [1.04, 0.97, 0.95], 0.5), ('left_infraorbital_region', [0.9, 0.88, 0.9], 0.6),
                         ('right_infraorbital_region', [0.9, 0.88, 0.9], 0.6)]:
        w = soft_group(grp)[:, None] * k * 0.6
        col = col * (1 - w) + col * np.array(tint) * w
    dark = lin(srgb(spec.get('hairColor', 0x16110e)))
    # Brows: a band around each brow polyline, thicker at the inner end.
    for a, b in [(17, 22), (22, 27)]:
        pts = lm[a:b]
        d = dist_to_polyline(p, pts)
        thick = spec.get('browThick', 0.0045)
        w = np.clip(1 - d / thick, 0, 1) ** 0.7 * spec.get('browDark', 0.9)
        w *= (G['skin'] > 0.5)
        col = col * (1 - w[:, None]) + dark * w[:, None]
    # Lash line.
    for a, b in [(36, 42), (42, 48)]:
        pts = np.vstack([lm[a:b], lm[a]])
        d = dist_to_polyline(p, pts[:4 + 1]) if False else dist_to_polyline(p, pts)
        w = np.clip(1 - d / (0.0032 if spec.get('kajal') else 0.0022), 0, 1) * (0.9 if spec.get('kajal') else 0.75) * (G['skin'] > 0.5)
        col = col * (1 - w[:, None]) + dark * w[:, None]
    # Lips.
    lip = lin(srgb(spec.get('lip', 0x8a4a42)))
    w = np.clip(G['upper_lip'] + G['lower_lip'], 0, 1)[:, None] * 0.55
    col = col * (1 - w) + lip * w
    # Scalp and beard skin darkened under the shells, feathered, so edges read as hair not helmet.
    hw, _ = hair_field(p, spec)
    hw = np.clip(hw * 1.5, 0, 1)
    bw, _ = beard_field(p, spec)
    for fw, k in ((hw, 0.85), (bw, 0.8)):
        f = smoothstep(0.0, 0.5, fw)[:, None] * k
        col = col * (1 - f) + dark * f
    # Moustache / stubble / beard as tone (the beard shell adds volume).
    if spec.get('moustache'):
        d = dist_to_polyline(p, np.array([lm[48], (lm[33] + lm[51]) / 2, lm[54]]))
        w = np.clip(1 - d / spec['moustache'], 0, 1) ** 0.5 * 0.95 * (G['upper_lip_region'] > 0.1)
        col = col * (1 - w[:, None]) + dark * w[:, None]
    if spec.get('stubble'):
        w = np.clip(G['chin_region'] + G['lower_lip_region'] * 0.6 + G['left_parotid_region'] * 0.5 + G['right_parotid_region'] * 0.5 + G['upper_lip_region'] * 0.7, 0, 1)
        w = w * spec['stubble'] * (G['skin'] > 0.5)
        col = col * (1 - w[:, None]) + (col * 0.55 + dark * 0.45) * w[:, None]
    return col.astype(np.float32)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def hair_field(p, spec):
    """Per-vertex hair weight in [0,1] with a soft hairline, and the shell thickness."""
    style = spec.get('hair', 'short')
    if style == 'none':
        return np.zeros(len(p)), 0.0
    lm = landmarks(p)
    skin = G['skin'] > 0.5
    ear_pts = p[G['ears'] > 0.3]
    c = (lm[0] + lm[16]) / 2
    rel = p - c
    theta = np.abs(np.arctan2(rel[:, 0], rel[:, 2]))
    brow_y = lm[17:27, 1].max()
    front_y = brow_y + spec.get('forehead', 0.03)
    side_y = lm[0, 1] + 0.012 + spec.get('temples', 0.0)
    back_y = lm[0, 1] - (0.055 if style in ('long', 'bun', 'braid') else 0.03)
    k1 = smoothstep(0.55, 1.35, theta)
    k2 = smoothstep(1.7, 2.6, theta)
    line = (front_y * (1 - k1) + side_y * k1) * (1 - k2) + back_y * k2
    d_ear = np.min(np.stack([np.linalg.norm(p - e, axis=1) for e in ear_pts[::12]]), axis=0)
    w = smoothstep(line - 0.008, line + 0.01, p[:, 1]) * smoothstep(0.006, 0.014, d_ear) * skin
    w *= 1 - smoothstep(0.3, 0.7, G['hockey_mask']) * (theta < 0.9)
    thick = {'short': 0.005, 'crop': 0.0025, 'side': 0.008, 'long': 0.011, 'bun': 0.009, 'braid': 0.009, 'spiky': 0.009}.get(style, 0.006)
    # Volume: thicker on top and (for tied-back hair) swept fuller at the back.
    vol = 0.5 + 0.5 * smoothstep(line - 0.01, line + 0.05, p[:, 1])
    if style in ('long', 'bun', 'braid'):
        vol *= 1 + 0.5 * smoothstep(1.2, 2.6, theta)
    # Partings: a groove where the scalp shows through.
    part_x = {'side': 0.028}.get(style, 0.0 if style in ('long', 'bun', 'braid') else None)
    if part_x is not None:
        on_top = smoothstep(line.min(), line.min() + 0.04, p[:, 1]) * (rel[:, 2] > -0.02)
        groove = 1 - (1 - smoothstep(0.0015, 0.005, np.abs(rel[:, 0] - part_x))) * on_top
        vol *= 0.15 + 0.85 * groove
    return w * vol, thick


def beard_field(p, spec):
    kind = spec.get('beard')
    if not kind:
        return np.zeros(len(p)), 0.0
    lm = landmarks(p)
    skin = G['skin'] > 0.5
    lips = np.clip(G['upper_lip'] + G['lower_lip'], 0, 1)
    if kind == 'full':
        cx = (lm[0, 0] + lm[16, 0]) / 2
        half = abs(lm[16, 0] - lm[0, 0]) / 2
        ax = np.clip(np.abs(p[:, 0] - cx) / half, 0, 1)
        y_top = (lm[33, 1] - 0.004) * (1 - ax ** 1.4) + (lm[2, 1] + 0.004) * ax ** 1.4
        w = smoothstep(y_top + 0.006, y_top - 0.01, p[:, 1])
        w *= smoothstep(lm[8, 1] - 0.035, lm[8, 1] - 0.018, p[:, 1])          # fade into the neck
        w *= smoothstep(lm[0, 2] - 0.035, lm[0, 2] - 0.005, p[:, 2])          # stop before the ears
        mouth = np.linalg.norm((p - (lm[62] + lm[66]) / 2) * [1.0, 1.6, 1.0], axis=1)
        w *= smoothstep(0.014, 0.022, mouth) * (1 - lips)
        return w * skin, 0.0045
    d = dist_to_polyline(p, np.array([lm[48] + [-0.004, -0.004, 0], (lm[33] + lm[51]) / 2, lm[54] + [0.004, -0.004, 0]]))
    w = smoothstep(0.008, 0.002, d) * (1 - lips) * smoothstep(0.0, 0.2, G['upper_lip_region']) * skin
    return w, 0.003


def shell(p, n, w, thick):
    """Offset shell; returns (pos, nrm, tris, fade) where fade (0..1) becomes alpha at the edges."""
    if thick <= 0 or w.max() < 0.05:
        return None
    q = p + n * (thick * w + 0.0006)[:, None]
    keep = (w[TRIS].max(1) > 0.02) & (SLOTS == 'skin')
    fade = np.tile(np.clip(w, 0, 1)[:, None], (1, 3)).astype(np.float32)
    return q, n, TRIS[keep], fade


# --- Expressions ---------------------------------------------------------
# GNM is linear: vertices = template + identity + expression, so an expression
# vector is the same for every face. We solve for each one from landmark targets
# (ridge regression on the expression basis sampled at the 68 landmarks).

def _lm_basis():
    b = (EXPR_BASIS[:, LM_IDX, :] * LM_W[None, :, :, None]).sum(2)  # [E, 68, 3]
    return b.reshape(len(EXPR_BASIS), -1).T  # [204, E]


def solve_expression(targets, lam=0.2, keep_still=()):
    """targets: {landmark: (dx, dy, dz)} in metres (x = subject's left, y up, z forward)."""
    A = _lm_basis()
    d = np.zeros((68, 3))
    w = np.full(68, 0.15)  # everything else: prefer not to move
    for k in keep_still:
        w[k] = 1.0
    for k, v in targets.items():
        d[k] = v
        w[k] = 1.0
    W = np.repeat(w, 3)
    Aw = A * W[:, None]
    AtA = Aw.T @ Aw
    reg = lam * np.trace(AtA) / A.shape[1]
    x = np.linalg.solve(AtA + reg * np.eye(A.shape[1]), Aw.T @ (d.reshape(-1) * W))
    return x.astype(np.float32)


def expression_set():
    tl = TEMPLATE[LM_IDX[:, 0]]
    left = 1.0 if tl[54, 0] > tl[48, 0] else -1.0  # which landmark side is the subject's left
    L, R = (54, 48) if left > 0 else (48, 54)
    BL, BR = (list(range(22, 27)), list(range(17, 22))) if tl[24, 0] * left > 0 else (list(range(17, 22)), list(range(22, 27)))
    lidsU = [37, 38, 43, 44]; lidsL = [40, 41, 46, 47]
    ex = {}
    ex['smile'] = solve_expression({L: (0.004 * left, 0.005, -0.001), R: (-0.004 * left, 0.005, -0.001), 51: (0, 0.001, 0), 57: (0, -0.001, 0)})
    ex['smirk'] = solve_expression({L: (0.003 * left, 0.005, -0.001), R: (0, -0.0005, 0), **{b: (0, 0.004, 0) for b in BL}}, keep_still=[R] + BR)
    ex['frown'] = solve_expression({L: (0, -0.003, 0), R: (0, -0.003, 0), BL[0]: (-0.002 * left, -0.003, 0), BR[-1]: (0.002 * left, -0.003, 0)})
    ex['jawOpen'] = solve_expression({57: (0, -0.012, 0), 8: (0, -0.012, -0.002), 66: (0, -0.011, 0), 62: (0, 0.0, 0)}, keep_still=[33, 51])
    ex['browRaise'] = solve_expression({b: (0, 0.005, 0) for b in BL + BR})
    ex['squint'] = solve_expression({**{u: (0, -0.0018, 0) for u in lidsU}, **{l: (0, 0.0008, 0) for l in lidsL}})
    ex['blink'] = solve_expression({**{u: (0, -0.0065, 0) for u in lidsU}, **{l: (0, 0.0006, 0) for l in lidsL}})
    return ex


EXPRESSIONS = None


def expression_deltas():
    global EXPRESSIONS
    if EXPRESSIONS is None:
        EXPRESSIONS = {k: np.tensordot(v, EXPR_BASIS, axes=1).astype(np.float32) for k, v in expression_set().items()}
    return EXPRESSIONS


QSCALE = 0.5  # positions/morphs stored as int16 in [-1,1] * QSCALE metres (KHR_mesh_quantization)


def pack_glb(path, prims, extras):
    """glTF 2.0 binary writer with KHR_mesh_quantization: int16 positions and sparse int16
    morph deltas (node scale restores metres), uint8 vertex colours, no stored normals."""
    buf = bytearray()
    views, accessors, meshes_prims, materials = [], [], [], []
    mat_index = {}

    def align():
        nonlocal buf
        while len(buf) % 4:
            buf += b'\0'

    def view(data, target=None, stride=None):
        nonlocal buf
        align()
        off = len(buf)
        buf += data
        v = {'buffer': 0, 'byteOffset': off, 'byteLength': len(data)}
        if target:
            v['target'] = target
        if stride:
            v['byteStride'] = stride
        views.append(v)
        return len(views) - 1

    def q16(x):
        return np.clip(np.round(x / QSCALE * 32767), -32767, 32767).astype(np.int16)

    for pr in prims:
        used = np.unique(pr['idx'])
        remap = -np.ones(len(pr['pos']), np.int64)
        remap[used] = np.arange(len(used))
        idx = remap[pr['idx']].astype(np.uint32).reshape(-1)
        pos = pr['pos'][used]
        qp = np.zeros((len(used), 4), np.int16)
        qp[:, :3] = q16(pos)
        # min/max are in the raw integer domain for normalized accessors (glTF spec).
        accessors.append({'bufferView': view(qp.tobytes(), 34962, 8), 'componentType': 5122, 'normalized': True,
                          'count': len(used), 'type': 'VEC3', 'min': qp[:, :3].min(0).astype(int).tolist(), 'max': qp[:, :3].max(0).astype(int).tolist()})
        attrs = {'POSITION': len(accessors) - 1}
        if pr.get('col') is not None:
            c = np.ones((len(used), 4), np.float32)
            c[:, :3] = pr['col'][used]
            accessors.append({'bufferView': view(np.clip(np.round(c * 255), 0, 255).astype(np.uint8).tobytes(), 34962, 4),
                              'componentType': 5121, 'normalized': True, 'count': len(used), 'type': 'VEC4'})
            attrs['COLOR_0'] = len(accessors) - 1
        accessors.append({'bufferView': view(idx.tobytes(), 34963), 'componentType': 5125, 'count': len(idx), 'type': 'SCALAR'})
        ind = len(accessors) - 1
        targets = []
        for name, full in pr.get('morphs', []):
            dv = full[used]
            nz = np.where(np.abs(dv).max(1) > 2e-5)[0].astype(np.uint32)
            if not len(nz):
                nz = np.array([0], np.uint32)
            vals = q16(dv[nz])
            iv = view(nz.tobytes())
            vv = view(vals.tobytes())
            accessors.append({'componentType': 5122, 'normalized': True, 'count': len(used), 'type': 'VEC3',
                              'min': np.minimum(vals.min(0), 0).astype(int).tolist(), 'max': np.maximum(vals.max(0), 0).astype(int).tolist(),
                              'sparse': {'count': int(len(nz)), 'indices': {'bufferView': iv, 'componentType': 5125}, 'values': {'bufferView': vv}}})
            targets.append({'POSITION': len(accessors) - 1})
        if pr['mat'] not in mat_index:
            mat_index[pr['mat']] = len(materials)
            materials.append({'name': pr['mat']})
        prim = {'attributes': attrs, 'indices': ind, 'material': mat_index[pr['mat']]}
        if targets:
            prim['targets'] = targets
        meshes_prims.append(prim)
    gltf = {'asset': {'version': '2.0', 'generator': 'clockout-gnm-export'},
            'extensionsUsed': ['KHR_mesh_quantization'], 'extensionsRequired': ['KHR_mesh_quantization'],
            'scene': 0, 'scenes': [{'nodes': [0], 'extras': extras}],
            'nodes': [{'name': 'gnm_head', 'mesh': 0, 'scale': [QSCALE] * 3}],
            'meshes': [{'primitives': meshes_prims, 'extras': {'targetNames': [n for n, _ in (prims[0].get('morphs') or [])]}}],
            'materials': materials, 'buffers': [{'byteLength': 0}],
            'bufferViews': views, 'accessors': accessors}
    align()
    gltf['buffers'][0]['byteLength'] = len(buf)
    js = json.dumps(gltf, separators=(',', ':')).encode()
    while len(js) % 4:
        js += b' '
    total = 12 + 8 + len(js) + 8 + len(buf)
    with open(path, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, total))
        f.write(struct.pack('<II', len(js), 0x4E4F534A) + js)
        f.write(struct.pack('<II', len(buf), 0x004E4942) + bytes(buf))


def lobe(p, lm, side):
    cx = (lm[0, 0] + lm[16, 0]) / 2
    e = p[(G['ears'] > 0.5) & (np.sign(p[:, 0] - cx) == side)]
    return e[e[:, 1].argmin()].tolist()


def export(path, spec):
    idv = identity(spec['seed'], spec.get('scale', 1.0), spec.get('overrides'), spec.get('female'), spec.get('latent', 1.0))
    p = vertices(idv)
    n = normals(p, TRIS[SLOTS == 'skin'])
    n_all = normals(p, TRIS)
    n = np.where((np.linalg.norm(n, axis=1) > 0.5)[:, None], n, n_all)
    col = paint_skin(p, spec)
    prims = []
    for slot in ['skin', 'sclera', 'iris', 'pupil', 'cornea', 'teeth', 'tongue', 'mouth', 'eyeint']:
        t = TRIS[SLOTS == slot]
        if len(t):
            prims.append({'pos': p, 'nrm': n_all if slot != 'skin' else n, 'col': col if slot == 'skin' else None, 'idx': t, 'mat': slot})
    for w, thick in (hair_field(p, spec), beard_field(p, spec)):
        sh = shell(p, n, w, thick)
        if sh is not None and len(sh[2]):
            prims.append({'pos': sh[0], 'nrm': sh[1], 'col': sh[3], 'idx': sh[2], 'mat': 'hair'})
    ex = list(expression_deltas().items())
    for pr in prims:
        pr['morphs'] = ex
    lm = landmarks(p)
    extras = {
        'id': spec.get('id', ''), 'hair': spec.get('hair', 'short'),
        'skin': hexint(spec['skin']), 'hairColor': hexint(spec.get('hairColor', 0x16110e)), 'iris': hexint(spec.get('iris', 0x2a180e)),
        'anchors': {
            'foreheadCenter': ((lm[21] + lm[22]) / 2 + np.array([0, 0.012, 0.004])).tolist(),
            'browMid': ((lm[21] + lm[22]) / 2).tolist(),
            'noseTip': lm[30].tolist(), 'noseLeftWing': lm[35].tolist(), 'noseRightWing': lm[31].tolist(),
            'leftEar': p[G['ears'] > 0.5][p[G['ears'] > 0.5][:, 0].argmax()].tolist(),
            'rightEar': p[G['ears'] > 0.5][p[G['ears'] > 0.5][:, 0].argmin()].tolist(),
            'leftEyeOuter': lm[45].tolist(), 'rightEyeOuter': lm[36].tolist(), 'leftEyeInner': lm[42].tolist(), 'rightEyeInner': lm[39].tolist(),
            'chin': lm[8].tolist(), 'mouthCenter': ((lm[62] + lm[66]) / 2).tolist(),
            'crown': p[G['skin'] > 0.5][p[G['skin'] > 0.5][:, 1].argmax()].tolist(),
            'nape': (((lm[0] + lm[16]) / 2) + np.array([0, 0.01, -0.085])).tolist(),
            'leftLobe': lobe(p, lm, +1), 'rightLobe': lobe(p, lm, -1),
        },
    }
    pack_glb(path, prims, extras)


def head_specs(path):
    """tools/cast.json (generated from src/data/cast.ts) -> head specs. A bare list is a scratch spec file."""
    d = json.load(open(path))
    return [m['head'] for m in d['members']] if isinstance(d, dict) else d


if __name__ == '__main__':
    # python3 gnm_export.py gnm_head.npz ../cast.json ../../public/gnm [id ...]
    specs = head_specs(sys.argv[2])
    out = sys.argv[3]
    only = set(sys.argv[4:])
    for s in specs:
        if only and s['id'] not in only:
            continue
        export(f"{out}/{s['id']}.glb", s)
        print('wrote', s['id'])
