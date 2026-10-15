# Motion scanner: a second, independent way to read a repetition's poses, to back up the colour-matching tracker
# (r6_track_rep / r6_global). It never scores colours: points on the character's boxes (the printed letters, face
# corners, shading edges) are followed from frame to frame by optical flow, each point knowing where it sits on which
# box, and every frame's pose is the one whose boxes carry those points to where the flow put them (least squares over
# the joints, the still camera's turn and tilt held). It starts from the idle frame's calibrated pose only; lost points
# are replaced by new ones read off the boxes as the scanner itself now places them.
#   python3 tools/r6_motion.py VIDEO TRACKDIR NAME fwd|bwd OUTDIR      -> OUTDIR/<NAME>_motion_<dir>.json
# Merge with the colour method: python3 tools/r6_motion.py merge OUTDIR NAME  (adds the motion poses as candidates to
# <NAME>_cands.json, rescored in each frame's common window, then re-runs r6_global's selection) and reports how often
# the two scanners agree.
import sys, os, json, math, time
import numpy as np, cv2
from scipy.optimize import least_squares
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F

PAL = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'palettes', 'side_colours.json')
pal = json.load(open(PAL)); F.use_palette(pal)
NC = len(F.CAM)
LK = dict(winSize=(21, 21), maxLevel=4, criteria=(cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 30, 0.01))
FB_PX = 1.0         # forward-backward flow disagreement allowed (px)
OUTLIER_PX = 4.0    # a point further than this from where the solved pose puts it is dropped (hidden, slid off)
PER_PART = 120      # points kept per box
GRID = 3            # px between seeded points (a grid on every visible face; dense flow follows them)
INSET = 2           # px: points only this far inside a visible face (its edges move against the background)
PRIOR_W = 0.02      # px of residual per degree a joint moves from the last frame (holds joints no point sees)
MIN_POINTS = 60     # fewer tracked points than this: the frame's reading is marked weak
BOXES = [(n, bone, lo, hi, part) for n, (bone, lo, hi, part) in enumerate(F.PARTS) if part != 'face']


def box_corners_local(part, lo, hi):
    lo, hi = F.scaled_box(part, lo, hi)
    return np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])], float)


LOCAL = {n: box_corners_local(part, lo, hi) for n, bone, lo, hi, part in BOXES}


def project(p, pts_by_box):
    """{box n: (N, 3) points in its bone's space} -> {box n: (N, 2) footage px} under params p."""
    world = F.pose_world(p)
    view, proj = F.camera_of(p)
    out = {}
    for n, L in pts_by_box.items():
        bone = F.PARTS[n][0]
        h = np.c_[L, np.ones(len(L))].T
        w = F.MODEL_TO_WORLD @ (world[bone] @ h)[:3]
        out[n] = proj(view @ w)
    return out


def visible_faces(p, shape):
    """A label image of which box face is in front at each pixel (-1: none) and, per face id, (box n, local corners
    of its 4 points in order, their footage px)."""
    world = F.pose_world(p)
    view, proj = F.camera_of(p)
    persp = p[5]
    faces = []
    for n, bone, lo, hi, part in BOXES:
        L = LOCAL[n]
        w = F.MODEL_TO_WORLD @ (world[bone] @ np.c_[L, np.ones(8)].T)[:3]
        v = view @ w
        uv = proj(v)
        rotw = F.MODEL_TO_WORLD @ world[bone][:3, :3]
        for idx, normal in F.FACES:
            nc = view @ (rotw @ normal)
            centre = v[:, idx].mean(1)
            toward = -centre + np.array([0, 0, 1 / persp]) if persp > 1e-6 else np.array([0, 0, 1.0])
            if nc @ toward <= 0:
                continue
            faces.append((centre[2], n, L[idx], uv[idx]))
    faces.sort(key=lambda f: f[0])
    lab = np.full(shape, -1, np.int32)
    info = []
    for fid, (_, n, Lq, uvq) in enumerate(faces):
        cv2.fillConvexPoly(lab, np.round(uvq * 4).astype(np.int32), fid, lineType=cv2.LINE_8, shift=2)
        info.append((n, Lq, uvq))
    return lab, info


def lift(pts, lab, info):
    """Footage points on visible faces -> (box n, local 3-D point) each (None where not inside a face by INSET)."""
    out = []
    ids = np.unique(lab[lab >= 0])
    inner = {}
    for fid in ids:
        m = (lab == fid).astype(np.uint8)
        inner[fid] = cv2.erode(m, np.ones((2 * INSET + 1, 2 * INSET + 1), np.uint8))
    H, W = lab.shape
    for x, y in pts:
        xi, yi = int(round(x)), int(round(y))
        if not (0 <= xi < W and 0 <= yi < H):
            out.append(None); continue
        fid = lab[yi, xi]
        if fid < 0 or not inner[fid][yi, xi]:
            out.append(None); continue
        n, Lq, uvq = info[fid]
        A = np.c_[uvq[1] - uvq[0], uvq[3] - uvq[0]]
        try:
            a, b = np.linalg.solve(A, np.array([x, y]) - uvq[0])
        except np.linalg.LinAlgError:
            out.append(None); continue
        out.append((n, Lq[0] + a * (Lq[1] - Lq[0]) + b * (Lq[3] - Lq[0])))
    return out


class Points:
    def __init__(self):
        self.uv = np.zeros((0, 2), np.float32)
        self.box = np.zeros(0, int)
        self.local = np.zeros((0, 3))

    def add(self, uv, lifted):
        keep = [(u, l) for u, l in zip(uv, lifted) if l is not None]
        if not keep:
            return
        self.uv = np.r_[self.uv, np.array([u for u, _ in keep], np.float32)]
        self.box = np.r_[self.box, [l[0] for _, l in keep]]
        self.local = np.r_[self.local, np.array([l[1] for _, l in keep])]

    def keep(self, m):
        self.uv, self.box, self.local = self.uv[m], self.box[m], self.local[m]


def reseed(gray, p, pts):
    """New points on boxes that have fewer than PER_PART, read off the faces as pose p draws them."""
    lab, info = visible_faces(p, gray.shape)
    counts = {n: int((pts.box == n).sum()) for n, *_ in BOXES}
    need = [fid for fid, (n, _, _) in enumerate(info) if counts[n] < PER_PART]
    if not need:
        return
    mask = np.isin(lab, need).astype(np.uint8) * 255
    mask = cv2.erode(mask, np.ones((2 * INSET + 1, 2 * INSET + 1), np.uint8))
    for u in pts.uv:
        cv2.circle(mask, (int(u[0]), int(u[1])), 5, 0, -1)
    ys, xs = np.nonzero(mask[::GRID, ::GRID])
    if not len(xs):
        return
    c = np.c_[xs, ys].astype(np.float32) * GRID
    c = c[np.random.default_rng(len(pts.box)).permutation(len(c))]
    pts.add(c, lift(c, lab, info))
    # at most PER_PART per box: the newest beyond it dropped
    m = np.ones(len(pts.box), bool)
    for n, *_ in BOXES:
        idx = np.nonzero(pts.box == n)[0]
        m[idx[PER_PART:]] = False
    pts.keep(m)


DIS = cv2.DISOpticalFlow_create(cv2.DISOPTICAL_FLOW_PRESET_MEDIUM)


def dense_track(g0, g1, uv, pad=160):
    """Points followed by dense optical flow (DIS) over a crop around them, kept where the backward flow brings them
    back within FB_PX. Returns (new places, kept)."""
    H, W = g0.shape
    x0, y0 = np.maximum(0, np.floor(uv.min(0) - pad).astype(int))
    x1, y1 = np.minimum([W, H], np.ceil(uv.max(0) + pad).astype(int))
    a, b = np.ascontiguousarray(g0[y0:y1, x0:x1]), np.ascontiguousarray(g1[y0:y1, x0:x1])
    f = DIS.calc(a, b, None)
    r = DIS.calc(b, a, None)
    q = uv - [x0, y0]
    at = lambda fl, pt: cv2.remap(fl, pt[:, 0].reshape(-1, 1).astype(np.float32), pt[:, 1].reshape(-1, 1).astype(np.float32),
                                  cv2.INTER_LINEAR).reshape(-1, 2)
    fw = at(f, q)
    q1 = q + fw
    back = q1 + at(r, q1)
    ok = (np.linalg.norm(back - q, axis=1) < FB_PX) & (q1[:, 0] >= 0) & (q1[:, 1] >= 0) & (q1[:, 0] < x1 - x0) & (q1[:, 1] < y1 - y0)
    return (q1 + [x0, y0]).astype(np.float32), ok


def solve(p0, pts, cam_lock):
    """The pose carrying every point to its tracked place: joints and the camera's scale and place free (turn, tilt,
    perspective held), each joint held lightly to p0."""
    free = [2, 3, 4] + list(range(NC, F.NP))
    lo = [p0[2] * 0.9, -np.inf, -np.inf] + [F.LIMITS[j][0] for j in F.JOINTS]
    hi = [p0[2] * 1.1, np.inf, np.inf] + [F.LIMITS[j][1] for j in F.JOINTS]
    x0 = np.clip(p0[free], np.array(lo) + 1e-6, np.array(hi) - 1e-6)
    by = {n: np.nonzero(pts.box == n)[0] for n in np.unique(pts.box)}

    def full(x):
        p = p0.copy()
        p[free] = x
        p[0], p[1] = cam_lock[0], cam_lock[1]
        return p

    def res(x):
        p = full(x)
        pr = project(p, {n: pts.local[i] for n, i in by.items()})
        r = np.zeros((len(pts.box), 2))
        for n, i in by.items():
            r[i] = pr[n] - pts.uv[i]
        return np.r_[r.ravel(), PRIOR_W * (x[3:] - x0[3:])]

    s = least_squares(res, x0, bounds=(lo, hi), loss='soft_l1', f_scale=2.0, x_scale=np.r_[0.05, 1, 1, [10] * len(F.JOINTS)],
                      max_nfev=60)
    p = full(s.x)
    pr = project(p, {n: pts.local[i] for n, i in by.items()})
    err = np.zeros(len(pts.box))
    for n, i in by.items():
        err[i] = np.linalg.norm(pr[n] - pts.uv[i], axis=1)
    return p, err


def run(video, trackdir, name, dirn, outdir):
    tr = json.load(open(os.path.join(trackdir, f'{name}_{dirn}.json')))
    fr = {int(k): v for k, v in tr['frames'].items()}
    order = sorted(fr) if dirn == 'fwd' else sorted(fr, reverse=True)
    cam_lock = tr['cam_lock']
    cap = cv2.VideoCapture(video)
    cap.set(cv2.CAP_PROP_POS_FRAMES, min(order))
    frames = {}
    for k in range(min(order), max(order) + 1):
        ok, f = cap.read()
        frames[k] = cv2.cvtColor(f, cv2.COLOR_BGR2GRAY)
    p = np.array(fr[order[0]]['params'], float)  # the idle frame's calibrated pose: the only thing taken from tracking
    pts = Points()
    reseed(frames[order[0]], p, pts)
    rows = {order[0]: {'params': list(map(float, p)), 'points': int(len(pts.box)), 'how': 'idle'}}
    t0 = time.time()
    for a, b in zip(order, order[1:]):
        g0, g1 = frames[a], frames[b]
        if len(pts.box):
            nxt, ok = dense_track(g0, g1, pts.uv)
            pts.uv = nxt
            pts.keep(ok)
        seen = {F.PARTS[n][3] for n in np.unique(pts.box)}
        if len(pts.box) >= 6:
            p, err = solve(p, pts, cam_lock)
            pts.keep(err < OUTLIER_PX)
            if len(pts.box) >= 6:  # once more without the outliers
                p, err = solve(p, pts, cam_lock)
        per = {part: int(sum(F.PARTS[n][3] == part for n in pts.box)) for part in ('torso', 'head', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg')}
        rows[b] = {'params': list(map(float, p)), 'points': int(len(pts.box)), 'per_part': per,
                   'rms_px': round(float(np.sqrt(np.mean(err ** 2))) if len(pts.box) else -1, 2), 'how': 'motion'}
        print(f'{name} motion {dirn} {b}: {len(pts.box)} points, rms {rows[b]["rms_px"]} px, '
              f'{" ".join(f"{k[:2]}{v}" for k, v in per.items())}', flush=True)
        if len(pts.box) >= MIN_POINTS and rows[b]['rms_px'] < 2.5:
            reseed(g1, p, pts)  # new points only from a pose the points themselves pinned down
        else:
            rows[b]['how'] = 'motion:weak'
            reseed(g1, p, pts)
    out = {'name': name, 'dir': dirn, 'video': video, 'cam_lock': cam_lock, 'light': tr['light'],
           'frames': {str(k): rows[k] for k in sorted(rows)}}
    path = os.path.join(outdir, f'{name}_motion_{dirn}.json')
    json.dump(out, open(path, 'w'))
    print(f'{path} ({(time.time() - t0) / 60:.1f} min)', flush=True)


def step(video, path_json, name, outdir):
    """One-step mode (no drift): every frame's pose in a chosen path (r6_global's) carried one frame forward and one
    back by the flow alone. Each carried pose is a motion reading of the neighbour frame, made from different evidence
    than the colours (pixel motion), and its distance from the neighbour's own pose says whether the colour method's
    change between them is one the footage's motion supports. -> OUTDIR/<NAME>_motion_step.json"""
    tr = json.load(open(path_json))
    fr = {int(k): v for k, v in tr['frames'].items()}
    ks = sorted(fr)
    cam_lock = tr['cam_lock']
    cap = cv2.VideoCapture(video)
    cap.set(cv2.CAP_PROP_POS_FRAMES, ks[0])
    frames = {}
    for k in range(ks[0], ks[-1] + 1):
        ok, f = cap.read()
        frames[k] = cv2.cvtColor(f, cv2.COLOR_BGR2GRAY)
    out = {}
    for k in ks:
        p = np.array(fr[k]['params'], float)
        for d in (1, -1):
            n = k + d
            if n not in fr:
                continue
            pts = Points()
            reseed(frames[k], p, pts)
            if not len(pts.box):
                continue
            nxt, ok = dense_track(frames[k], frames[n], pts.uv)
            pts.uv = nxt
            pts.keep(ok)
            if len(pts.box) < MIN_POINTS:
                continue
            q, err = solve(p, pts, cam_lock)
            pts.keep(err < OUTLIER_PX)
            if len(pts.box) < MIN_POINTS:
                continue
            q, err = solve(q, pts, cam_lock)
            own = np.array(fr[n]['params'], float)
            import r6_oversee as O
            diff = {b: round(O.bone_deg(q, own, b), 1) for b in O.BONES}
            out.setdefault(str(n), []).append({'from': f'flow{-d:+d}', 'params': list(map(float, q)),
                                               'points': int(len(pts.box)), 'rms_px': round(float(np.sqrt(np.mean(err ** 2))), 2),
                                               'deg_from_path': diff})
        print(k, ' '.join(f"{c['from']}:{c['points']}p,{max(c['deg_from_path'].values()):.0f}deg" for c in out.get(str(k), [])), flush=True)
    json.dump({'name': name, 'path': path_json, 'frames': out}, open(os.path.join(outdir, f'{name}_motion_step.json'), 'w'))


def merge(outdir, name, lam=None):
    """The motion poses added to the colour method's candidates (all rescored together per frame), then the
    whole-clip selection re-run; and how often the two scanners agree on each bone."""
    import r6_global as G, r6_oversee as O
    data = json.load(open(os.path.join(outdir, f'{name}_cands.json')))
    track = json.load(open(data['track']))
    clip = O.Clip(track['video'] if os.path.exists(track['video']) else sys.argv[-1], track)
    glob = json.load(open(os.path.join(outdir, f'{name}_global.json')))['frames']
    agree, n = {b: 0 for b in O.BONES}, 0
    sp = os.path.join(outdir, f'{name}_motion_step.json')
    if os.path.exists(sp):
        for k, cs in json.load(open(sp))['frames'].items():
            if k not in data['frames'] or data['frames'][k][0]['from'] == 'idle':
                continue
            for c in cs:
                data['frames'][k].append({'from': 'motion_' + c['from'], 'params': c['params'], 'agreement': 0.0})
                n += 1
                for b in O.BONES:
                    agree[b] += c['deg_from_path'][b] < 25
    for d in ([] if os.path.exists(sp) else ['fwd', 'bwd']):
        path = os.path.join(outdir, f'{name}_motion_{d}.json')
        if not os.path.exists(path):
            continue
        mo = json.load(open(path))['frames']
        for k, row in mo.items():
            if row['how'] == 'idle' or k not in data['frames']:
                continue
            data['frames'][k].append({'from': f'motion_{d}', 'params': row['params'], 'agreement': 0.0})
            if d == 'fwd' and k in glob:
                n += 1
                for b in O.BONES:
                    agree[b] += O.bone_deg(np.array(row['params']), np.array(glob[k]['params']), b) < 25
    for k, cands in data['frames'].items():
        if cands[0]['from'] == 'idle':
            continue
        sc = G.common_scores(clip, int(k), [np.array(c['params'], float) for c in cands])
        for c, a in zip(cands, sc):
            c['agreement'] = a
    path = os.path.join(outdir, f'{name}_cands_motion.json')
    json.dump(data, open(path, 'w'))
    os.makedirs(os.path.join(outdir, 'with_motion'), exist_ok=True)
    os.replace(path, os.path.join(outdir, 'with_motion', f'{name}_cands.json'))
    rep = G.select(os.path.join(outdir, 'with_motion'), name, lam or G.LAMBDA)
    rep['motion_vs_colour_agree_within_25deg'] = {b: round(v / max(n, 1), 2) for b, v in agree.items()}
    print('AGREE', json.dumps(rep['motion_vs_colour_agree_within_25deg']))
    return rep


if __name__ == '__main__':
    a = sys.argv[1:]
    if a and a[0] == 'step':
        step(*a[1:5])
    elif a and a[0] == 'merge':
        merge(a[1], a[2])
    elif len(a) >= 5:
        run(*a[:5])
    else:
        sys.exit('usage: VIDEO TRACKDIR NAME fwd|bwd OUTDIR | merge OUTDIR NAME')
