# Whole-repetition pose selection: per frame several candidate poses found on the footage alone (no motion cost),
# then the one sequence through them that fits best overall while moving plausibly, chosen at once by dynamic
# programming (Viterbi). Replaces one-frame-at-a-time tracking's habit of paying a motion cost per frame, which makes
# a sudden real move (a kick, a punch) lose to the last frame's pose: here a move the footage shows over several frames
# wins, and a one-frame fluke loses because its neighbours disagree.
#   python3 tools/r6_global.py candidates VIDEO TRACKDIR NAME OUTDIR    per-frame candidates -> OUTDIR/<NAME>_cands.json
#   python3 tools/r6_global.py select OUTDIR NAME [LAMBDA]               best path -> OUTDIR/<NAME>_global.json + report
# TRACKDIR holds r6_track_rep.py's <NAME>_fwd.json / <NAME>_bwd.json (starts and the still camera). JOBS=4 side by side.
import sys, os, json, math, time
import numpy as np, torch, cv2
torch.set_num_threads(1); cv2.setNumThreads(1)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T
import r6_oversee as O

NC = O.NC
LIMBS = ('rightArm', 'leftArm', 'rightThigh', 'leftThigh')
# Motion between consecutive frames that costs nothing (deg; at 30 fps an arm crosses ~90 deg in a strike), and the
# cost per degree beyond it (agreement units: 90 deg beyond costs .07).
FREE = {'hips': 25.0, 'head': 40.0, 'rightArm': 60.0, 'leftArm': 60.0, 'rightThigh': 45.0, 'leftThigh': 45.0}
LAMBDA = float(os.environ.get('R6_GLOBAL_LAMBDA', '0.0008'))
MOVE_PX = 0.002  # per footage px the body's place moves beyond FREE_PX between frames
FREE_PX = 12.0


class UnionWindow(T.TrackWindow):
    """A TrackWindow whose gate is the union of several candidates' outlines: every candidate of a frame is judged
    against the same footage pixels (a compact pose must not hide the colours it fails to cover)."""

    def __init__(self, fitter, ps, light, res, reach=1.25):
        super().__init__(fitter, ps[0], light, res, reach)
        pts = np.concatenate([T.rig_outline(p, light).reshape(-1, 2) for p in ps]).astype(np.float32)
        self.hull = cv2.convexHull(pts)[:, 0]


def common_scores(clip, k, ps):
    """Agreement of each params vector of frame k in one common window."""
    rgb = clip.rgb(k)
    enc = F.classify(rgb, O.pal)
    v = T.View([rgb], {0: enc}, [0], 30, 112)
    v.lite = True
    win = UnionWindow(clip.f0, ps, clip.light, 112)
    ref = np.mean(np.stack(ps), 0)
    ref[NC:] = ps[0][NC:]
    fit = T.Fit([v], ps[0][None, NC:], [ps[0][None, :5]], [float(ps[0][5])], [clip.light])
    v.window(0, ps[0], win)
    P = np.stack(ps)
    a = T.agreement(fit, 0, 0, torch.tensor(P[:, NC:], dtype=T.DT), torch.tensor(P[:, :5], dtype=T.DT))
    return [float(x) for x in a]


def frame_candidates(job):
    video, trackdir, name, k = job
    fw, bw = O.load(trackdir, name)
    fr, br = O.rows(fw), O.rows(bw) if bw else {}
    off = O.bwd_offset(fr, br)
    clip = O.Clip(video, fw)
    if os.environ.get('R6_GLOBAL_START'):  # a later round: start from an earlier round's chosen path
        fr = O.rows(json.load(open(os.environ['R6_GLOBAL_START'])))
    t0 = time.time()
    starts = [('fwd', np.array(fr[k]['params'], float))]
    if k in br:
        starts.append(('bwd', O.bwd_pose(fr[k]['params'], br[k]['params'], off)))
    for d in (-1, 1):
        if k + d in fr and fr[k + d]['how'] != 'idle':
            q = np.array(fr[k]['params'], float)
            q[NC:] = np.asarray(fr[k + d]['params'])[NC:]
            starts.append((f'fwd{d:+d}', q))
    out = [('track', np.array(fr[k]['params'], float))]  # the track's own pose, unchanged
    for label, s in starts:
        # The start refined locally, then every limb searched over its whole range with no motion or stance cost
        # (limb_sweep without prev / leg_w), then refined again.
        p, ag, _, fit = clip.track(k, s, search=False, fine=False)
        T.limb_sweep(fit, 0, rounds=2)
        q = fit.params_of(0, 0)
        p2, ag2, _, _ = clip.track(k, q, search=False, fine=True)
        out.append((label, p))
        out.append((label + '+sweep', p2))
    # Distinct ones only (every bone within 8 deg and the place within 3 px of an earlier one: the same pose).
    keep = []
    for label, p in out:
        if any(O.max_bone_deg(p, q)[0] < 8 and np.hypot(*(p[3:5] - q[3:5])) < 3 for _, q in keep):
            continue
        keep.append((label, p))
    sc = common_scores(clip, k, [p for _, p in keep])
    return k, [{'from': l, 'params': [float(x) for x in p], 'agreement': a} for (l, p), a in zip(keep, sc)], \
        time.time() - t0


def candidates(video, trackdir, name, outdir):
    fw, _ = O.load(trackdir, name)
    fr = O.rows(fw)
    ks = [k for k in sorted(fr) if fr[k]['how'] != 'idle']
    res = {}
    import multiprocessing as mp
    with mp.get_context('fork').Pool(int(os.environ.get('JOBS', '4'))) as pool:
        for k, c, sec in pool.imap_unordered(frame_candidates, [(video, trackdir, name, k) for k in ks]):
            res[k] = c
            best = max(c, key=lambda x: x['agreement'])
            print(f'{k}: {len(c)} candidates, best {best["agreement"]:.3f} ({best["from"]}), track {fr[k]["agreement"]:.3f}'
                  f' ({sec:.0f} s)', flush=True)
    # Idle frames: their own fit is the only candidate.
    for k in fr:
        if fr[k]['how'] == 'idle':
            res[k] = [{'from': 'idle', 'params': fr[k]['params'], 'agreement': fr[k]['agreement']}]
    json.dump({'name': name, 'track': os.path.join(trackdir, f'{name}_fwd.json'),
               'frames': {str(k): res[k] for k in sorted(res)}}, open(os.path.join(outdir, f'{name}_cands.json'), 'w'))


def move_cost(p, q, lam):
    c = 0.0
    for b, free in FREE.items():
        c += lam * max(0.0, O.bone_deg(p, q, b) - free)
    c += MOVE_PX * max(0.0, float(np.hypot(p[3] - q[3], p[4] - q[4])) - FREE_PX)
    return c


def select(outdir, name, lam=LAMBDA):
    data = json.load(open(os.path.join(outdir, f'{name}_cands.json')))
    track = json.load(open(data['track']))
    fr = O.rows(track)
    ks = sorted(int(k) for k in data['frames'])
    C = {k: data['frames'][str(k)] for k in ks}
    P = {k: [np.array(c['params'], float) for c in C[k]] for k in ks}
    # Viterbi: best total agreement less motion costs, over one candidate per frame.
    score = {ks[0]: [c['agreement'] for c in C[ks[0]]]}
    back = {}
    for a, b in zip(ks, ks[1:]):
        score[b], back[b] = [], []
        for j, q in enumerate(P[b]):
            opts = [score[a][i] - move_cost(p, q, lam) for i, p in enumerate(P[a])]
            i = int(np.argmax(opts))
            score[b].append(opts[i] + C[b][j]['agreement'])
            back[b].append(i)
    j = int(np.argmax(score[ks[-1]]))
    path = {}
    for k in reversed(ks):
        path[k] = j
        if k in back:
            j = back[k][j]
    out = dict(track)
    out['frames'] = {}
    for k in ks:
        c = C[k][path[k]]
        out['frames'][str(k)] = {'params': c['params'], 'agreement': c['agreement'], 'how': 'global:' + c['from'],
                                 'parts': fr[k].get('parts')}
    out['global'] = {'lambda': lam, 'free_deg': FREE}
    json.dump(out, open(os.path.join(outdir, f'{name}_global.json'), 'w'))
    moving = [k for k in ks if fr[k]['how'] != 'idle']
    own = lambda k: next((c['agreement'] for c in C[k] if c['from'] in ('track', 'idle')), fr[k]['agreement'])
    a0 = np.array([own(k) for k in moving])  # the track's pose, judged in the same window as the others
    a1 = np.array([C[k][path[k]]['agreement'] for k in moving])
    best = np.array([max(c['agreement'] for c in C[k]) for k in moving])
    from collections import Counter
    rep = {'frames': len(moving), 'lambda': lam,
           'track_mean': round(float(a0.mean()), 4), 'global_mean': round(float(a1.mean()), 4),
           'best_candidate_mean': round(float(best.mean()), 4),
           'track_min': round(float(a0.min()), 4), 'global_min': round(float(a1.min()), 4),
           'track_ge_0.90': int((a0 >= 0.9).sum()), 'global_ge_0.90': int((a1 >= 0.9).sum()),
           'picked_from': dict(Counter(C[k][path[k]]['from'] for k in moving))}
    json.dump(rep, open(os.path.join(outdir, f'{name}_global_report.json'), 'w'), indent=1)
    print('SUMMARY', json.dumps(rep), flush=True)
    return rep


if __name__ == '__main__':
    a = sys.argv[1:]
    if a and a[0] == 'candidates':
        candidates(*a[1:5])
    elif a and a[0] == 'select':
        select(a[1], a[2], float(a[3]) if len(a) > 3 else LAMBDA)
    else:
        sys.exit('stages: candidates VIDEO TRACKDIR NAME OUTDIR | select OUTDIR NAME [LAMBDA]')
