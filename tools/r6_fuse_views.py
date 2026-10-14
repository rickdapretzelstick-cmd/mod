# python3 tools/r6_fuse_views.py   (env: DIRS=fwd|fwd,bwd, NAME=clip name, FUSE_DIR=tracks dir, ONLY=views)
#
# One animation from every view of a move: the repetitions (each tracked forwards and backwards by rep_track.py) put on
# one timeline, then per moment and per bone the rotations of all tracks averaged, each weighted by how much of that
# bone's part it saw agreeing and how well its frame fits, over a few neighbouring frames (a frame where smoke hides an
# arm takes it from the views and frames that see it); far-off answers are down-weighted (robust reweighting).
# Writes the preview clip, one viewer conversion per view (the fused rig over that view's footage) and a report.
import sys, os, json, math
import numpy as np, torch, cv2
sys.path.insert(0, '/home/user/mod/tools')
import r6_fit as F, r6_fit_torch as T
# The tracks (tools/r6_track_rep.py's output), by default build/fit/tracks.
FUSE = os.environ.get('FUSE_DIR', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'build', 'fit', 'tracks'))
NAME = os.environ.get('NAME', 'cursed_strikes_fused')
REPS = {'left': 525, 'back': 104, 'right': 299, 'front': 798}  # each repetition's first moving frame (find_moves)
REF = 'left'
if os.environ.get('ONLY'):
    REPS = {k: v for k, v in REPS.items() if k in os.environ['ONLY'].split(',')}
DIRS = os.environ.get('DIRS', 'fwd,bwd').split(',')  # DIRS=fwd: the forward tracks alone
BONE_PARTS = {'hips': ['torso'], 'head': ['head', 'face'], 'rightArm': ['rightArm'], 'leftArm': ['leftArm'],
              'rightThigh': ['rightLeg'], 'leftThigh': ['leftLeg']}
BONES = list(BONE_PARTS)
NC = len(F.CAM)
pal = json.load(open('/home/user/mod/tools/palettes/side_colours.json')); F.use_palette(pal)
PARTS = [q[3] for q in F.PARTS]


def euler_of(p, b):
    return [p[F.JI[(b, a)]] for a in range(3)]


def R_of(xyz):
    return T.rotm(torch.tensor(xyz, dtype=torch.float64)[None])[0].numpy()


def Ry(a):
    return R_of([0.0, a, 0.0])


def project(M):
    u, _, vt = np.linalg.svd(M)
    R = u @ vt
    if np.linalg.det(R) < 0:
        u[:, -1] *= -1
        R = u @ vt
    return R


def angle(A, B):
    return math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(A.T @ B) - 1) / 2))))


tracks = {}
for rep in REPS:
    for d in DIRS:
        tracks[(rep, d)] = json.load(open(os.path.join(FUSE, f'{rep}_{d}.json')))

# Per track and frame: each bone's rotation (hips relative to the repetition's starting heading) and its weight.
def heading(p):
    return p[F.JI[('hips', 1)]]

data = {}
report = {'tracks': {}}
for rep, start in REPS.items():
    fw, bw = tracks[(rep, 'fwd')]['frames'], tracks.get((rep, 'bwd'), {'frames': {}})['frames']
    h0 = heading(fw[str(min(int(k) for k in fw))]['params'])
    # The backward track's camera was found on the end's idle frame: its headings sit at an offset, found where both
    # tracks fit well.
    both = [k for k in fw if k in bw and fw[k]['agreement'] > 0.82 and bw[k]['agreement'] > 0.82]
    diffs = [((heading(fw[k]['params']) - heading(bw[k]['params']) + 180) % 360 - 180) for k in both]
    off = float(np.median(diffs)) if len(diffs) >= 5 else 0.0
    report['tracks'][rep] = {'heading_offset_bwd': round(off, 1), 'frames_both_good': len(both)}
    for d, fr, extra in [x for x in (('fwd', fw, 0.0), ('bwd', bw, off)) if x[0] in DIRS]:
        for k, row in fr.items():
            p = row['params']
            ag = row['agreement']
            if row['parts']:
                m, s = row['parts']
            else:
                m = s = None
            for b in BONES:
                xyz = euler_of(p, b)
                R = R_of(xyz)
                if b == 'hips':
                    R = Ry(-(h0 - extra)) @ R
                if m is None:
                    w = 0.5 * ag ** 2  # an idle frame (no part counts): its overall fit only
                else:
                    idx = [i for i, n in enumerate(PARTS) if n in BONE_PARTS[b]]
                    mm, ss = sum(m[i] for i in idx), sum(s[i] for i in idx)
                    w = max((mm / 300.0) * (mm / max(ss, 1)) ** 2, 0.01) * ag ** 2  # unseen: a little weight
                data[(rep, d, int(k), b)] = (R, w)

# Time alignment: each repetition against the reference, by the offset (around their detected starts) where their
# confident arm rotations agree best.
def track_best(rep, k, b):
    c = [data.get((rep, d, k, b)) for d in DIRS]
    c = [x for x in c if x]
    return max(c, key=lambda x: x[1]) if c else None

shift = {REF: 0}
ref_frames = sorted({k for (r, d, k, b) in data if r == REF})
for rep in REPS:
    if rep == REF:
        continue
    best = None
    for o in range(-8, 9):
        num = den = 0.0
        for k in ref_frames:
            kk = k - REPS[REF] + REPS[rep] + o
            for b in ('rightArm', 'leftArm', 'hips'):
                a, c = track_best(REF, k, b), track_best(rep, kk, b)
                if a and c:
                    w = min(a[1], c[1])
                    num += w * angle(a[0], c[0])
                    den += w
        err = num / max(den, 1e-9)
        if best is None or err < best[0]:
            best = (err, o)
    shift[rep] = best[1]
    report['tracks'][rep]['time_shift_frames'] = best[1]
    report['tracks'][rep]['aligned_error_deg'] = round(best[0], 1)

# Fusion on the reference timeline.
def src_frame(rep, t):
    return t - REPS[REF] + REPS[rep] + shift[rep]

SIG = float(os.environ.get('SIG', '0.5'))
WIN = int(os.environ.get('WIN', '2'))
first, last = ref_frames[0], ref_frames[-1]
fused, support = {}, {}
for t in range(first, last + 1):
    fused[t], support[t] = {}, {}
    for b in BONES:
        cands = []
        for rep in REPS:
            for d in DIRS:
                for dt in range(-WIN, WIN + 1):
                    x = data.get((rep, d, src_frame(rep, t) + dt, b))
                    if x and x[1] > 0:
                        cands.append((x[0], x[1] * math.exp(-0.5 * (dt / SIG) ** 2), rep, d, dt))
        if not cands:
            fused[t][b] = None
            continue
        ws = np.array([c[1] for c in cands])
        R = project(sum(w * c[0] for w, c in zip(ws, cands)))
        for _ in range(4):
            dev = np.array([angle(R, c[0]) for c in cands])
            rw = ws / (1 + (dev / 25.0) ** 2)
            R = project(sum(w * c[0] for w, c in zip(rw, cands)))
        fused[t][b] = R
        share = {}
        for w, c in zip(rw, cands):
            share[c[2]] = share.get(c[2], 0) + w
        tot = sum(share.values())
        support[t][b] = {r: round(v / tot, 2) for r, v in share.items()}

# Fill bones no track saw at a moment from the nearest moment that has them.
for b in BONES:
    have = [t for t in fused if fused[t][b] is not None]
    for t in fused:
        if fused[t][b] is None:
            fused[t][b] = fused[min(have, key=lambda s: abs(s - t))][b]


def heading0(rep):
    fw = tracks[(rep, 'fwd')]['frames']
    return heading(fw[str(min(int(k) for k in fw))]['params'])


def params_for(rep, pose, cam_params):
    """A pose (bone -> rotation; the hips' relative to the move's starting heading) as params under view `rep`'s
    camera (cam_params) and starting heading."""
    p = np.array(cam_params, float)
    h0 = heading0(rep)
    for b in BONES:
        R = pose[b]
        if b == 'hips':
            R = R_of([0, h0, 0]) @ R
        x, y, z = T._euler_zyx(torch.tensor(R))
        for a, v in zip((0, 1, 2), (x, y, z)):
            p[F.JI[(b, a)]] = v
    return p


def best_cam(rep, k):
    c = [tracks[(rep, d)]['frames'].get(str(k)) for d in DIRS]
    c = [x for x in c if x]
    return max(c, key=lambda x: x['agreement'])['params'] if c else None


def own_ag(rep, k):
    c = [tracks[(rep, d)]['frames'].get(str(k)) for d in DIRS]
    c = [x for x in c if x]
    return max(x['agreement'] for x in c) if c else None


# Every view's frame, windowed once (its own tracked camera), for scoring poses in all views at once.
cap = cv2.VideoCapture(tracks[(REF, 'fwd')]['video'])
_views = {}


def view_of(rep, k):
    if (rep, k) not in _views:
        cp = best_cam(rep, k)
        if cp is None:
            _views[(rep, k)] = None
            return None
        tr = tracks[(rep, 'fwd')]
        light = (np.array(tr['light'][0]), tr['light'][1])
        cap.set(cv2.CAP_PROP_POS_FRAMES, k); ok, f = cap.read()
        rgb = f[..., ::-1].copy()
        enc = F.classify(rgb, pal)
        v = T.View([rgb], {0: enc}, [0], 30, 112); v.lite = True
        cpa = np.array(cp, float)
        fit = T.Fit([v], cpa[None, NC:], [cpa[None, :5]], [float(cpa[5])], [(torch.tensor(light[0], dtype=T.DT), light[1])])
        fb = F.Fitter(112); fb.light = light
        v.window(0, cpa, fb)
        _views[(rep, k)] = (rgb, fit, cpa, light)
    return _views[(rep, k)]


def multi_score(t, poses):
    """Each pose scored in every view at moment t: the views' agreements, weighted by how well each view's own
    tracking fits there (a view lost in smoke counts less). Returns (totals (B,), per-view agreements {rep: (B,)})."""
    tot = np.zeros(len(poses))
    wsum = 0.0
    per = {}
    for rep in REPS:
        k = src_frame(rep, t)
        vw = view_of(rep, k)
        if vw is None:
            continue
        _, fit, cp, _ = vw
        P = np.stack([params_for(rep, q, cp) for q in poses])
        J = torch.tensor(P[:, NC:], dtype=T.DT)
        C = torch.tensor(P[:, :5], dtype=T.DT)
        ag = T.agreement(fit, 0, 0, J, C).numpy()
        q = own_ag(rep, k) ** 2
        tot += q * ag
        wsum += q
        per[rep] = ag
    return tot / max(wsum, 1e-9), per


# Per moment: the pose that fits all views best -- every track's pose, the weighted average, then each bone swapped
# for any track's version of it while that helps, then each limb nudged.
sel, sel_score = {}, {}
# EVAL=n: every n-th moment only, scored per view, nothing written (for comparing methods).
EVAL = int(os.environ.get('EVAL', '0'))
for t in [t for t in fused if not EVAL or (t - first) % EVAL == 0]:
    cands = [dict(fused[t])]
    opts = {b: [fused[t][b]] for b in BONES}
    for rep in REPS:
        for d in DIRS:
            pose = {}
            for b in BONES:
                x = data.get((rep, d, src_frame(rep, t), b))
                if x:
                    pose[b] = x[0]
                    opts[b].append(x[0])
            if len(pose) == len(BONES):
                cands.append(pose)
    sc, _ = multi_score(t, cands)
    best, bs = cands[int(np.argmax(sc))], float(sc.max())
    for _ in range(2):
        for b in BONES:
            trial = [dict(best, **{b: R}) for R in opts[b]]
            sc, _ = multi_score(t, trial)
            if sc.max() > bs + 1e-6:
                best, bs = trial[int(np.argmax(sc))], float(sc.max())
    for b in ('rightArm', 'leftArm', 'rightThigh', 'leftThigh', 'hips'):
        steps = [(dx, 0, dz) for dx in (-12, 0, 12) for dz in (-12, 0, 12)] if b != 'hips' else [(0, dy, 0) for dy in (-10, -5, 5, 10)]
        trial = [dict(best, **{b: best[b] @ R_of(list(st))}) for st in steps]
        sc, _ = multi_score(t, trial)
        if sc.max() > bs + 1e-6:
            best, bs = trial[int(np.argmax(sc))], float(sc.max())
    sel[t], sel_score[t] = best, bs
print('selected poses for', len(sel), 'moments', flush=True)
if EVAL:
    per_view = {}
    for t in sel:
        for rep in REPS:
            k = src_frame(rep, t)
            vw = view_of(rep, k)
            if vw is None or own_ag(rep, k) is None:
                continue
            _, fit, cp, _ = vw
            p = params_for(rep, sel[t], cp)
            ag = float(T.agreement(fit, 0, 0, torch.tensor(p[None, NC:], dtype=T.DT), torch.tensor(p[None, :5], dtype=T.DT))[0])
            per_view.setdefault(rep, []).append((own_ag(rep, k), ag))
    out = {r: {'own': round(float(np.mean([a for a, _ in v])), 3), 'combined': round(float(np.mean([b for _, b in v])), 3),
               'combined_below_0.80': sum(b < 0.8 for _, b in v), 'n': len(v)} for r, v in per_view.items()}
    out['all_views_mean'] = round(float(np.mean([b for v in per_view.values() for _, b in v])), 3)
    print('EVAL', json.dumps(out), flush=True)
    sys.exit(0)

# The clip: on the reference view's timeline, from 3 frames before the move to the end of the reference repetition.
out_dir = f'/home/user/mod/build/fit/{NAME}'
os.makedirs(out_dir, exist_ok=True)
fps = 30.057875412284524
clip_frames = [t for t in range(REPS[REF] - 3, last + 1)]
P = np.stack([params_for(REF, sel[t], best_cam(REF, src_frame(REF, t))) for t in clip_frames])
times = np.array([(t - clip_frames[0]) * 1000.0 / fps for t in clip_frames])
clip = 'preview/' + NAME
path, nk = F.write_clip(clip, times, P, 4.0, False, 'gd6 four views fused', os.path.join(out_dir, 'clip.json'))
print(f'{path}: {nk} keys, {int(times[-1])} ms')

# Per view: the chosen rig over that view's footage, and how well it fits there (against that view's own tracking).
report['views'] = {}
for rep in REPS:
    frames, params, own, fus, idx = {}, {}, [], [], []
    for t in clip_frames:
        k = src_frame(rep, t)
        vw = view_of(rep, k)
        if vw is None:
            continue
        rgb, fit, cp, light = vw
        p = params_for(rep, sel[t], cp)
        frames[k], params[k] = rgb, p
        idx.append(k)
        J, C = torch.tensor(p[None, NC:], dtype=T.DT), torch.tensor(p[None, :5], dtype=T.DT)
        fus.append(round(float(T.agreement(fit, 0, 0, J, C)[0]), 3))
        own.append(round(own_ag(rep, k), 3))
    f0 = F.Fitter(96)
    f0.light = vw[3]
    t_ = np.array([(i - idx[0]) * 1000.0 / fps for i in idx])
    od = f'/home/user/mod/build/fit/{NAME}_{rep}'
    os.makedirs(od, exist_ok=True)
    F.write_conversion(frames, params, idx, t_, clip, f0, od, f'gd6 {rep} view {idx[0]}-{idx[-1]} (combined pose)')
    report['views'][rep] = {'frames': [idx[0], idx[-1]], 'own_tracking': own, 'combined_pose': fus,
                            'own_mean': round(float(np.mean(own)), 3), 'combined_mean': round(float(np.mean(fus)), 3)}
    print(rep, 'own tracking mean', report['views'][rep]['own_mean'], 'combined pose mean', report['views'][rep]['combined_mean'], flush=True)
report['support'] = {t: support[t] for t in clip_frames}
json.dump(report, open(os.path.join(out_dir, 'fuse_report.json'), 'w'), indent=1)
print(json.dumps(report['tracks']))
