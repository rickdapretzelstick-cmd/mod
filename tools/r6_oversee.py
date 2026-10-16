# AI overseer for a tracked repetition (tools/r6_track_rep.py output): the tracker flags frames it may have got wrong,
# draws a review sheet per flagged frame with alternative poses beside the current fit, a reviewer (Claude, looking at
# the sheets) picks one or writes a correction, and the tool refines the corrected pose, carries it forward and logs
# every corrected frame. Stages:
#   flag   TRACKDIR NAME                          suspect frames and why (low agreement, joint jump, fwd/bwd disagree)
#   sheets VIDEO TRACKDIR NAME OUTDIR [FRAMES]    review sheets OUTDIR/sheets/<n>.png + OUTDIR/sheets/<n>.json
#   apply  VIDEO TRACKDIR NAME OUTDIR CORR.json   corrected track OUTDIR/<NAME>_oversee.json + report
#   clip   VIDEO TRACK.json NAME CLIPNAME         preview clip + viewer conversion from a track (overseen or global)
# FRAMES: comma list (e.g. 540,541,560-570); default every flagged frame. Several jobs side by side: OMP_NUM_THREADS=1.
#
# Corrections (CORR.json), per frame of the forward track:
#   {"542": {"pick": "D"}}                                  a sheet's candidate (refined from its seed)
#   {"560": {"pose": {"rightArm.x": -90, "rightArm.z": 0}, "turn": 90}}
#        the current fit with these joint angles (bone.axis in degrees, absolute) set and the body turned by `turn` deg
#   {"570": {"pick": "C", "pose": {...}}}                   a candidate, then edited
#   "keep_tol": 0.03 (top level, optional): a correction is kept unless its agreement is this much below the track's.
import sys, os, json, math, time
import numpy as np, torch, cv2
torch.set_num_threads(1); cv2.setNumThreads(1)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T

PAL = os.environ.get('R6_PALETTE', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'palettes', 'side_colours.json'))
pal = json.load(open(PAL)); F.use_palette(pal)
NC = len(F.CAM)
BONES = ['hips', 'head', 'rightArm', 'leftArm', 'rightThigh', 'leftThigh']
AX = {'x': 0, 'y': 1, 'z': 2}
LOW, JUMP_DEG, DISAGREE_DEG = 0.85, 60.0, 45.0  # flag thresholds
KEEP_TOL = 0.03       # a correction is kept unless it costs more than this agreement (the ruler counts trails as body)
SAME_DEG, SAME_RUN = 12.0, 2  # carrying forward stops once the re-tracked pose matches the old track this long
WORSE_TOL = 0.02  # ... or at the first re-tracked frame this much worse than the old track's (which is then kept)


# ---- tracks ----
def load(trackdir, name):
    fw = json.load(open(os.path.join(trackdir, f'{name}_fwd.json')))
    bp = os.path.join(trackdir, f'{name}_bwd.json')
    bw = json.load(open(bp)) if os.path.exists(bp) else None
    return fw, bw


def rows(track):
    return {int(k): v for k, v in track['frames'].items()}


def R_of(p, b):
    return T.rotm(torch.tensor([p[F.JI[(b, a)]] for a in range(3)], dtype=torch.float64)[None])[0].numpy()


def bone_deg(p, q, b):
    """How far bone b points differently in p and q: the hips' whole rotation (heading and lean); a limb's or the
    head's long axis only (a hanging arm twisted half round looks nearly the same, so twist is not a disagreement)."""
    A, B = R_of(p, b), R_of(q, b)
    if b != 'hips':
        return math.degrees(math.acos(max(-1.0, min(1.0, float(A[:, 1] @ B[:, 1])))))
    return math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(A.T @ B) - 1) / 2))))


def max_bone_deg(p, q, bones=BONES):
    d = {b: bone_deg(p, q, b) for b in bones}
    b = max(d, key=d.get)
    return d[b], b


def bwd_offset(fw, bw):
    """The backward track's headings sit at an offset (its camera was found on the other idle frame): the median
    difference where both fit well (as r6_fuse_views)."""
    if bw is None:
        return 0.0
    h = F.JI[('hips', 1)]
    both = [k for k in fw if k in bw and fw[k]['agreement'] > 0.82 and bw[k]['agreement'] > 0.82]
    d = [((fw[k]['params'][h] - bw[k]['params'][h] + 180) % 360 - 180) for k in both]
    return float(np.median(d)) if len(d) >= 5 else 0.0


def bwd_pose(fwp, bwp, off):
    """The backward track's joints under the forward track's camera and heading."""
    q = np.array(fwp, float)
    q[NC:] = np.asarray(bwp, float)[NC:]
    h = F.JI[('hips', 1)]
    q[h] = (q[h] + off + 180) % 360 - 180
    return q


def flag(fw, bw):
    """Suspect frames of the forward track: {frame: [reasons]}."""
    fr = rows(fw)
    br = rows(bw) if bw else {}
    off = bwd_offset(fr, br)
    out = {}
    ks = sorted(fr)
    for i, k in enumerate(ks):
        r = fr[k]
        if r['how'] == 'idle':
            continue
        why = []
        if r['agreement'] < LOW:
            why.append(f"agreement {r['agreement']:.2f}")
        if i > 0:
            d, b = max_bone_deg(r['params'], fr[ks[i - 1]]['params'])
            if d > JUMP_DEG:
                why.append(f'{b} jumped {d:.0f} deg')
        if k in br and br[k]['how'] != 'idle':
            d, b = max_bone_deg(r['params'], bwd_pose(r['params'], br[k]['params'], off))
            if d > DISAGREE_DEG:
                why.append(f'backward track: {b} {d:.0f} deg off')
        if why:
            out[k] = why
    return out


# ---- the footage and the tracker around a frame ----
class Clip:
    def __init__(self, video, fw):
        self.cap = cv2.VideoCapture(video)
        self.cam_lock = fw['cam_lock']
        self.light = (np.array(fw['light'][0], float), float(fw['light'][1]))
        self.f0 = F.Fitter()
        self.f0.light = self.light
        self.cache = {}

    def rgb(self, n):
        if n not in self.cache:
            self.cap.set(cv2.CAP_PROP_POS_FRAMES, n)
            ok, f = self.cap.read()
            self.cache = {n: f[..., ::-1].copy()}
        return self.cache[n]

    def track(self, n, seed, search=True, fine=True):
        """track_frame from `seed` (a whole params vector); search=False: re-centre + gradient steps (+ the fine
        stage) only, so the seed's pose is refined locally, not searched away from. Returns (params, agreement, fit)."""
        rgb = self.rgb(n)
        p, ag, how, fit = T.track_frame(rgb, F.classify(rgb, pal), np.asarray(seed, float), self.cam_lock, self.light,
                                        self.f0, good=LOW if search else -1.0, detail=True, fine=fine)
        return p, float(ag), how, fit


def turned(p, deg):
    q = np.array(p, float)
    h = F.JI[('hips', 1)]
    q[h] = (q[h] + deg + 180) % 360 - 180
    return q


def clamp(q):
    for (b, a), (lo, hi) in F.LIMITS.items():
        j = F.JI[(b, a)]
        q[j] = min(hi, max(lo, q[j]))
    return q


def candidates(fr, br, off, k):
    """Alternative seeds for frame k: letter -> (label, params). The camera is always the current fit's."""
    p = np.array(fr[k]['params'], float)
    c = {'A': ('current fit', p)}
    if k - 1 in fr:
        q = p.copy(); q[NC:] = np.asarray(fr[k - 1]['params'])[NC:]
        c['B'] = ('previous frame', q)
    if k in br and br[k]['how'] != 'idle':
        c['C'] = ('backward track', bwd_pose(p, br[k]['params'], off))
    q = p.copy()
    for a, s in ((0, 1), (1, -1), (2, -1)):  # mirrored across the body: x kept, y and z negated
        q[F.JI[('rightArm', a)]], q[F.JI[('leftArm', a)]] = s * p[F.JI[('leftArm', a)]], s * p[F.JI[('rightArm', a)]]
    c['D'] = ('arms swapped L/R', clamp(q))
    c['E'] = ('body turned +90', turned(p, 90))
    c['F'] = ('body turned -90', turned(p, -90))
    c['G'] = ('body turned 180', turned(p, 180))
    q = p.copy()
    for b in ('rightArm', 'leftArm'):  # forward <-> back: the swing about the shoulder mirrored
        q[F.JI[(b, 0)]] = -q[F.JI[(b, 0)]]
    c['H'] = ('arms flipped fwd/back', clamp(q))
    # Kicks: a leg hidden behind the body draws no pixels, so nothing pulls it out to where it really is, and the
    # search's leg cost keeps legs near standing; each leg swung out sideways and forward.
    for L, label, b, x, z in (('I', 'right leg out sideways', 'rightThigh', 0, 90),
                              ('J', 'left leg out sideways', 'leftThigh', 0, -90),
                              ('K', 'right leg kicked forward', 'rightThigh', -90, 0),
                              ('L', 'left leg kicked forward', 'leftThigh', -90, 0)):
        q = p.copy()
        q[F.JI[(b, 0)]], q[F.JI[(b, 1)]], q[F.JI[(b, 2)]] = x, 0, z
        c[L] = (label, clamp(q))
    return c


# ---- drawing ----
def crop_box(ps, pad=1.35):
    pts = np.concatenate([T.rig_outline(p, LIGHT[0]).reshape(-1, 2) for p in ps])
    lo, hi = pts.min(0), pts.max(0)
    c = 0.5 * (lo + hi)
    half = max(0.5 * pad * float((hi - lo).max()), 20.0)
    return c[0] - half, c[1] - half, 2 * half


LIGHT = [None]


def crop(rgb, box, size):
    x0, y0, w = box
    M = np.array([[size / w, 0, -x0 * size / w], [0, size / w, -y0 * size / w]], np.float32)
    return cv2.warpAffine(rgb, M, (size, size), flags=cv2.INTER_AREA, borderValue=(30, 30, 30))


def overlay(img, p, box, size, alpha=0.3):
    x0, y0, w = box
    k = size / w
    over = img.copy()
    edges = []
    for poly, cls, sh in F.faces(p, LIGHT[0]):
        pts = np.round((np.array(poly) - [x0, y0]) * k * 4).astype(np.int32)
        col = tuple(int(c * (0.55 + 0.45 * sh)) for c in F.SHOW[cls])
        cv2.fillPoly(over, [pts], col, lineType=cv2.LINE_AA, shift=2)
        edges.append(pts)
    out = cv2.addWeighted(over, alpha, img, 1 - alpha, 0)
    cv2.polylines(out, edges, True, (255, 255, 255), 1, cv2.LINE_AA, shift=2)
    return out


def side_view(p, size, turn=90):
    """The rig alone, seen from `turn` degrees round from the camera (depth: an arm toward or away from the camera)."""
    q = np.array(p, float)
    q[0] += turn
    q[1] = 10.0
    q[5] = 0.0
    s = size / 52.0
    q[2], q[3], q[4] = s, size / 2, size / 2 + 8 * s
    img = np.full((size, size, 3), 45, np.uint8)
    for poly, cls, sh in F.faces(q, LIGHT[0]):
        pts = np.round(np.array(poly) * 4).astype(np.int32)
        cv2.fillPoly(img, [pts], tuple(int(c * sh) for c in F.SHOW[cls]), lineType=cv2.LINE_AA, shift=2)
    return img


def text(img, s, xy, scale=0.5, col=(255, 255, 255)):
    cv2.putText(img, s, xy, cv2.FONT_HERSHEY_SIMPLEX, scale, (0, 0, 0), 3, cv2.LINE_AA)
    cv2.putText(img, s, xy, cv2.FONT_HERSHEY_SIMPLEX, scale, col, 1, cv2.LINE_AA)


def sheet(clip, k, why, cands, path):
    """One review sheet: the footage (and 3 frames either side), then each candidate over the footage with its
    agreement after a local refine and the rig seen from the side."""
    S = 256
    box = crop_box([cands['A'][2]], pad=1.7)  # the current fit's place: the footage stays put across tiles
    top = [crop(clip.rgb(k), box, S)]
    text(top[0], f'frame {k}', (6, 18))
    ctx = []
    for d in (-3, 3):
        im = crop(clip.rgb(k + d), box, S // 2)
        text(im, f'{d:+d}', (4, 14), 0.4)
        ctx.append(im)
    top.append(np.vstack(ctx))
    head = np.hstack(top)
    info = np.full((S, 4 * (S + 4) - head.shape[1], 3), 25, np.uint8)
    for i, line in enumerate(['flagged: ' + '; '.join(why), 'score = colour agreement after a local refine',
                              'inset: the rig seen from 90 deg round (depth)', 'pick a letter or write a pose']):
        cv2.putText(info, line, (8, 24 + 22 * i), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (230, 230, 230), 1, cv2.LINE_AA)
    head = np.hstack([head, info])
    tiles = []
    for L, (label, seed, p, ag) in cands.items():
        im = overlay(crop(clip.rgb(k), box, S), p, box, S)
        text(im, f'{L} {ag:.2f}', (6, 20), 0.6, (255, 255, 120) if L == 'A' else (255, 255, 255))
        text(im, label, (6, S - 8), 0.42)
        sv = side_view(p, 100)
        im[4:4 + sv.shape[0], S - 4 - sv.shape[1]:S - 4] = sv
        tiles.append(np.pad(im, ((2, 2), (2, 2), (0, 0)), constant_values=60))
    while len(tiles) % 4:
        tiles.append(np.full_like(tiles[0], 25))
    grid = np.vstack([np.hstack(tiles[i:i + 4]) for i in range(0, len(tiles), 4)])
    w = max(head.shape[1], grid.shape[1])
    pad = lambda a: np.pad(a, ((0, 0), (0, w - a.shape[1]), (0, 0)), constant_values=25)
    cv2.imwrite(path, np.vstack([pad(head), pad(grid)])[..., ::-1])


def make_sheet(job):
    video, trackdir, name, outdir, k, why = job
    fw, bw = load(trackdir, name)
    fr, br = rows(fw), rows(bw) if bw else {}
    clip = Clip(video, fw)
    LIGHT[0] = clip.light
    out, t0 = {}, time.time()
    for L, (label, seed) in candidates(fr, br, bwd_offset(fr, br), k).items():
        if L == 'A':
            p, ag = np.array(seed), fr[k]['agreement']  # the track's own result (fine stage included)
        else:
            p, ag, _, _ = clip.track(k, seed, search=False, fine=False)
        out[L] = (label, seed, p, ag)
    os.makedirs(os.path.join(outdir, 'sheets'), exist_ok=True)
    sheet(clip, k, why, out, os.path.join(outdir, 'sheets', f'{k}.png'))
    json.dump({'frame': k, 'why': why, 'candidates': {L: {'label': v[0], 'seed': list(map(float, v[1])),
                                                         'params': list(map(float, v[2])), 'agreement': round(v[3], 4)}
                                                     for L, v in out.items()}},
              open(os.path.join(outdir, 'sheets', f'{k}.json'), 'w'))
    best = max(out, key=lambda L: out[L][3])
    return k, best, out[best][3], out['A'][3], time.time() - t0


def frame_list(s):
    out = []
    for part in s.split(','):
        a, _, b = part.partition('-')
        out += list(range(int(a), int(b or a) + 1))
    return out


# ---- applying corrections ----
def seed_of(corr, k, fr, outdir):
    c = corr[str(k)]
    p = np.array(fr[k]['params'], float)
    if 'pick' in c:
        sh = json.load(open(os.path.join(outdir, 'sheets', f'{k}.json')))
        p = np.array(sh['candidates'][c['pick']]['params'], float)
    for key, v in (c.get('pose') or {}).items():
        b, a = key.split('.')
        p[F.JI[(b, AX[a])]] = float(v)
    if c.get('turn'):
        p = turned(p, float(c['turn']))
    return clamp(p)


def apply(video, trackdir, name, outdir, corr_path):
    fw, bw = load(trackdir, name)
    fr = rows(fw)
    corr = json.load(open(corr_path))
    tol = float(corr.pop('keep_tol', KEEP_TOL))
    corr = {k: v for k, v in corr.items() if not k.startswith('_')}
    clip = Clip(video, fw)
    new = {k: dict(v) for k, v in fr.items()}
    log, todo = [], sorted(int(k) for k in corr)
    last = max(fr)
    t0 = time.time()
    for k in todo:
        seed = seed_of(corr, k, new, outdir)
        p, ag, how, fit = clip.track(k, seed, search=False, fine=True)
        old = new[k]['agreement']
        kept = ag >= old - tol
        entry = {'frame': k, 'correction': corr[str(k)], 'before': round(old, 4), 'after': round(ag, 4), 'kept': kept}
        print(f'{name} {k}: correction {json.dumps(corr[str(k)])} {old:.3f} -> {ag:.3f}' +
              ('' if kept else ' (rejected: worse than the track)'), flush=True)
        if not kept:
            log.append(entry)
            continue
        m, s = T.part_agreement(fit)
        new[k] = {'params': list(map(float, p)), 'agreement': ag, 'how': 'ai', 'parts': [m.tolist(), s.tolist()],
                  'correction': corr[str(k)]}
        # Carried forward: the following frames re-tracked from the corrected pose until they come back to the old
        # track (every bone within SAME_DEG for SAME_RUN frames) or the next corrected frame.
        same, n, carried = 0, k + 1, []
        nxt = min([j for j in todo if j > k], default=last + 1)
        while n < nxt and n <= last and fr[n]['how'] != 'idle':
            p, ag, how, fit = clip.track(n, p, search=True, fine=True)
            if ag < new[n]['agreement'] - WORSE_TOL:
                carried.append((n, round(fr[n]['agreement'], 3), round(ag, 3), 'stop: worse, old track kept'))
                break
            d, _ = max_bone_deg(p, fr[n]['params'])
            m, s = T.part_agreement(fit)
            new[n] = {'params': list(map(float, p)), 'agreement': ag, 'how': 'carried:' + how,
                      'parts': [m.tolist(), s.tolist()], 'from_ai': k}
            carried.append((n, round(fr[n]['agreement'], 3), round(ag, 3), round(d, 1)))
            same = same + 1 if d < SAME_DEG else 0
            n += 1
            if same >= SAME_RUN:
                break
        entry['carried'] = carried
        log.append(entry)
        print(f'  carried {len(carried)} frames: ' + ' '.join(f'{c[0]}:{c[1]:.2f}->{c[2]:.2f}' + ('(stop)' if len(c) > 3 and isinstance(c[3], str) else '') for c in carried),
              flush=True)
    out = dict(fw)
    out['frames'] = {str(k): v for k, v in sorted(new.items())}
    out['ai_corrected'] = [e['frame'] for e in log if e['kept']]
    out['oversee_log'] = log
    path = os.path.join(outdir, f'{name}_oversee.json')
    json.dump(out, open(path, 'w'))
    moving = [k for k in fr if fr[k]['how'] != 'idle']
    a0 = np.array([fr[k]['agreement'] for k in moving])
    a1 = np.array([new[k]['agreement'] for k in moving])
    rep = {'frames': len(moving), 'mean_before': round(float(a0.mean()), 4), 'mean_after': round(float(a1.mean()), 4),
           'min_before': round(float(a0.min()), 4), 'min_after': round(float(a1.min()), 4),
           'at_least_0.90_before': int((a0 >= 0.9).sum()), 'at_least_0.90_after': int((a1 >= 0.9).sum()),
           'ai_corrected': out['ai_corrected'], 'rejected': [e['frame'] for e in log if not e['kept']],
           'minutes': round((time.time() - t0) / 60, 1)}
    json.dump({'summary': rep, 'log': log}, open(os.path.join(outdir, f'{name}_oversee_report.json'), 'w'), indent=1)
    print('SUMMARY', json.dumps(rep), flush=True)
    return rep


def write_clip(video, track_path, name, clipname):
    tr = json.load(open(track_path))
    fr = rows(tr)
    clip = Clip(video, tr)
    idx = sorted(fr)
    fps = clip.cap.get(cv2.CAP_PROP_FPS) or 30.0  # the recording's own frame rate
    n = max(len(fr[k]['params']) for k in idx)  # frames with joint offsets are longer: the rest get zero offsets
    P = np.stack([np.pad(np.array(fr[k]['params'], float), (0, n - len(fr[k]['params']))) for k in idx])
    times = np.array([(k - idx[0]) * 1000.0 / fps for k in idx])
    od = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'build', 'fit', clipname)  # where the viewer looks
    os.makedirs(od, exist_ok=True)
    path, nk = F.write_clip('preview/' + clipname, times, P, 4.0, False, f'gd6 {name} view, AI overseen',
                            os.path.join(od, 'clip.json'))
    print(f'{path}: {nk} keys, {int(times[-1])} ms')
    frames = {k: clip.rgb(k) for k in idx}
    f0 = F.Fitter(96)
    f0.light = clip.light
    F.write_conversion(frames, {k: np.array(fr[k]['params'], float) for k in idx}, idx, times, 'preview/' + clipname,
                       f0, od, f'gd6 {name} view {idx[0]}-{idx[-1]} ' + ('(whole-clip selection, r6_global)' if 'global' in tr else f'(AI overseen: {len(tr.get("ai_corrected", []))} frames corrected)'))


def main():
    a = sys.argv[1:]
    if not a:
        print(__doc__ or open(__file__).read().split('\nimport')[0]); return
    if a[0] == 'flag':
        fw, bw = load(a[1], a[2])
        fl = flag(fw, bw)
        for k, why in sorted(fl.items()):
            print(k, '; '.join(why))
        print(f'{len(fl)} flagged of {len(fw["frames"])}')
    elif a[0] == 'sheets':
        video, trackdir, name, outdir = a[1:5]
        fw, bw = load(trackdir, name)
        fl = flag(fw, bw)
        ks = frame_list(a[5]) if len(a) > 5 else sorted(fl)
        jobs = [(video, trackdir, name, outdir, k, fl.get(k, ['asked for'])) for k in ks]
        import multiprocessing as mp
        with mp.get_context('fork').Pool(int(os.environ.get('JOBS', '4'))) as pool:
            for k, best, bag, cur, sec in pool.imap_unordered(make_sheet, jobs):
                print(f'sheet {k}: current {cur:.3f}, best candidate {best} {bag:.3f} ({sec:.0f} s)', flush=True)
    elif a[0] == 'apply':
        apply(*a[1:6])
    elif a[0] == 'clip':
        write_clip(*a[1:5])
    else:
        sys.exit('stages: flag | sheets | apply | clip')


if __name__ == '__main__':
    main()
