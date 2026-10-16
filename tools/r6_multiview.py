# All four views of the move judged together. The move is a game animation, so its four repetitions (one filmed from
# each side) play the same joint angles: per moment, one pose (the hips relative to each repetition's starting heading,
# r6_fuse_views' convention) must explain every view at once. What one view cannot tell apart -- side-on, the arm over
# the torso; an arm behind the body -- another sees. Per moment: every view's best candidates (r6_global/r6_partfit
# sets, +-1 frame for timing) pooled, each scored in all four views by colour + W x parts (r6_partfit's Judge), the
# best few swept limb by limb on the four-view score; then Viterbi over the clip on the left view's params.
#   python3 tools/r6_multiview.py OUTDIR [W=0.5]   (env NET=build/synth/partnet2.pt, FRAMES=a-b: a quick look)
# First the views' cameras are made to agree (calibrate; OUTDIR/cameras.json, reused if there).
import sys, os, json, time, math
import numpy as np, torch
from multiprocessing import Pool
torch.set_num_threads(1)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T
import r6_oversee as O, r6_global as G, r6_partnet as P, r6_partfit as PF

NC = O.NC
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
VIDEO = os.path.join(ROOT, 'build', 'video.mp4')
CANDS = {'left': 'build/fit/v2/partfit2_0.5/left_cands.json', 'front': 'build/fit/v3/front/partfit/front_cands.json',
         'back': 'build/fit/v3/back/partfit/back_cands.json', 'right': 'build/fit/v3/right/partfit/right_cands.json'}
REPS = {'left': 525, 'back': 104, 'right': 299, 'front': 798}  # each repetition's first moving frame (r6_fuse_views)
REF = 'left'
BONES = ['hips', 'head', 'rightArm', 'leftArm', 'rightThigh', 'leftThigh']
HIPS = [F.JI[('hips', a)] - NC for a in range(3)]
TOP = 6        # candidates per view-frame put in the pool
STARTS = 3     # pooled poses swept on the four-view score
SHIFTS = range(-8, 9)


def R_of(xyz):
    return T.rotm(torch.tensor(xyz, dtype=torch.float64)[None])[0].numpy()


def angle(A, B):
    return math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(A.T @ B) - 1) / 2))))


def load_view(v):
    data = json.load(open(os.path.join(ROOT, CANDS[v])))
    tr = json.load(open(data['track'] if os.path.isabs(data['track']) else os.path.join(ROOT, data['track'])))
    path = json.load(open(os.path.join(ROOT, CANDS[v]).replace('_cands.json', '_global.json')))
    h0 = tr['frames'][str(min(int(k) for k in tr['frames']))]['params'][F.JI[('hips', 1)]]
    return {'data': data, 'track': tr, 'path': {int(k): r for k, r in path['frames'].items()},
            'cands': {int(k): c for k, c in data['frames'].items()}, 'h0': float(h0)}


def to_pose(p, h0):
    """View params -> view-free joints: the same joint vector with the hips re-expressed from the view's heading."""
    J = np.array(p[NC:], float)
    R = R_of([0, -h0, 0]) @ R_of([J[HIPS[0]], J[HIPS[1]], J[HIPS[2]]])
    J[HIPS] = T._euler_zyx(torch.tensor(R))
    return J


def to_view(J, h0, cam):
    """View-free joints -> params in a view (its camera/position cam = the first NC params)."""
    Jv = np.array(J, float)
    R = R_of([0, h0, 0]) @ R_of([J[HIPS[0]], J[HIPS[1]], J[HIPS[2]]])
    Jv[HIPS] = T._euler_zyx(torch.tensor(R))
    return np.concatenate([cam[:NC], Jv])


def bone_R(J, b):
    return R_of([J[F.JI[(b, a)] - NC] for a in range(3)])


def time_shifts(V):
    """Each view's frame offset against the reference (beyond the repetitions' detected starts), from the body's turn
    over the move -- the one angle every view sees well (the arms' are what the views disagree on). Found to a tenth of
    a frame on the views' paths, then rounded (the candidates are per frame). Returns {view: (frames, error deg, exact)}."""
    def yaw(v):
        ks = np.array(sorted(V[v]['path']))
        y = np.array([to_pose(V[v]['path'][k]['params'], V[v]['h0'])[HIPS[1]] for k in ks])
        return ks, np.unwrap(y, period=360)
    kl, yl = yaw(REF)
    m = (kl >= REPS[REF] + 2) & (kl <= kl.max() - 12)
    out = {REF: (0, 0.0, 0.0)}
    for v in V:
        if v == REF:
            continue
        kv, yv = yaw(v)
        best = None
        for phi in np.arange(-4, 4.01, 0.1):
            yi = np.interp(kl[m] - REPS[REF] + REPS[v] + phi, kv, yv)
            e = float(np.median(np.abs((yi - yl[m] + 180) % 360 - 180)))
            if best is None or e < best[1]:
                best = (int(round(phi)), e, round(float(phi), 1))
        out[v] = best
    return out


def calibrate(V, clips, net, shift, most=10):
    """The views' cameras were each found alone, so they disagree slightly (a few degrees of turn and pitch, a little
    scale) and each view's poses lean to make up for it: the same pose then fails in the other views (.55-.88 even near
    idle). Here each other view's turn, pitch and scale are re-found once, shared by all its frames, so that the
    reference view's own pose on the still frames before the move fits it too (the position per frame as the median
    shift). Returns {view: [d yaw, d pitch, d scale, d x, d y]}."""
    ref = V[REF]
    cal = {REF: [0.0] * 5}
    # the still frames before the move: the pose is near the rest pose, so no view's limb ambiguity gets in
    good = [k for k in sorted(ref['path']) if k < REPS[REF] and ref['path'][k]['agreement'] >= 0.95]
    for v in V:
        if v == REF:
            continue
        rows = []
        for t in good:
            k = t - REPS[REF] + REPS[v] + shift[v][0]
            if k in V[v]['path'] and k in V[v]['cands']:
                rows.append((t, k))
        rows = rows[:most]
        setups = []
        for t, k in rows:
            cam = np.array(V[v]['path'][k]['params'], float)
            ps = [cam] + [np.array(q['params'], float) for q in V[v]['cands'][k]]
            jd = PF.Judge(clips[v], net, k, ps, 0.0)
            setups.append((jd, cam, to_pose(ref['path'][t]['params'], ref['h0'])))

        def total(d, own_xy=None):
            out, xys = 0.0, []
            for n, (jd, cam, J) in enumerate(setups):
                best = None
                for dx in (-2, -1, 0, 1, 2) if own_xy is None else (own_xy[n][0],):
                    for dy in (-2, -1, 0, 1, 2) if own_xy is None else (own_xy[n][1],):
                        cm = cam.copy()
                        cm[:5] += [d[0], d[1], d[2], d[3] + dx, d[4] + dy]
                        p = to_view(J, V[v]['h0'], cm)
                        a = float(T.agreement(jd.fit, 0, 0, torch.tensor(p[None, NC:], dtype=T.DT),
                                              torch.tensor(p[None, :5], dtype=T.DT))[0])
                        if best is None or a > best[0]:
                            best = (a, (dx, dy))
                out += best[0]
                xys.append(best[1])
            return out / max(len(setups), 1), xys

        d = np.zeros(5)
        best, xys = total(d)
        before = best
        for step in (8, 4, 2, 1, 0.5):
            for _ in range(3):
                moved = False
                for i, unit in ((0, 1.0), (1, 1.0), (2, 0.05), (3, 1.0), (4, 1.0)):
                    for sgn in (-1, 1):
                        q = d.copy()
                        q[i] += sgn * step * unit
                        s, xy = total(q)
                        if s > best:
                            best, d, xys, moved = s, q, xy, True
                if not moved:
                    break
        d[3] += float(np.median([x for x, _ in xys]))
        d[4] += float(np.median([y for _, y in xys]))
        cal[v] = d.tolist()
        print(f'calibrated {v} on {len(setups)} frames: reference pose fits {before:.3f} -> {best:.3f}; '
              f'turn {d[0]:+.1f} pitch {d[1]:+.1f} scale {d[2]:+.2f} shift {d[3]:+.1f},{d[4]:+.1f}', flush=True)
    return cal




def cam_of(v, k):
    c = _ctx['V'][v]
    cam = np.array(c['path'][k]['params'], float)
    cam[:5] += _ctx['cal'][v]
    return cam


_ctx = {}


def ctx():
    if not _ctx:
        _ctx['V'] = {v: load_view(v) for v in CANDS}
        _ctx['clip'] = {v: O.Clip(VIDEO, _ctx['V'][v]['track']) for v in CANDS}
        _ctx['net'] = P.load(os.path.join(ROOT, os.environ.get('NET', 'build/synth/partnet2.pt')))
        calp = os.environ.get('CAL')
        _ctx['cal'] = json.load(open(calp)) if calp and os.path.exists(calp) else {v: [0.0] * 5 for v in CANDS}
    return _ctx


class MultiJudge:
    """Four-view score of view-free joint sets at moment t: the mean over the views that have that frame of
    colour + W x parts, each in its own common window (its candidates there plus the pooled ones)."""

    def __init__(self, t, shift, pool, w):
        c = ctx()
        self.views = []
        for v, V in c['V'].items():
            k = t - REPS[REF] + REPS[v] + shift[v][0]
            if k not in V['path'] or k not in V['cands']:
                continue
            cam = cam_of(v, k)
            own = [np.array(q['params'], float) for q in V['cands'][k]]
            ps = [cam] + own + [to_view(J, V['h0'], cam) for J in pool]
            self.views.append((v, k, V['h0'], cam, PF.Judge(c['clip'][v], c['net'], k, ps, w)))

    def __call__(self, Js):
        """Js (B, NJ) view-free -> (total, {view: (colour, parts)})."""
        tot = np.zeros(len(Js))
        per = {}
        for v, k, h0, cam, judge in self.views:
            Pv = np.stack([to_view(J, h0, cam) for J in Js.numpy()])
            s, col, par = judge(torch.tensor(Pv[:, NC:], dtype=T.DT))
            tot += s
            per[v] = (col, par)
        return tot / max(len(self.views), 1), per


def moment_job(job):
    t, shift, w = job
    t0 = time.time()
    c = ctx()
    pool, src = [], []
    for v, V in c['V'].items():
        k0 = t - REPS[REF] + REPS[v] + shift[v][0]
        for dt in (-1, 0, 1):
            cs = V['cands'].get(k0 + dt)
            if not cs:
                continue
            for q in sorted(cs, key=lambda q: -q['agreement'])[:TOP]:
                pool.append(to_pose(q['params'], V['h0']))
                src.append(f'{v}{dt:+d}:{q["from"]}')
    # drop near-duplicates (every bone within 8 deg of a kept pose)
    keep = []
    for i, J in enumerate(pool):
        if all(max(angle(bone_R(J, b), bone_R(pool[j], b)) for b in BONES) > 8 for j in keep):
            keep.append(i)
    pool, src = [pool[i] for i in keep], [src[i] for i in keep]
    judge = MultiJudge(t, shift, pool, w)
    Js = torch.tensor(np.stack(pool), dtype=T.DT)
    tot, _ = judge(Js)
    out = [(pool[i], src[i]) for i in range(len(pool))]
    for o in np.argsort(-tot)[:STARTS]:
        J, _ = PF.sweep(judge_scalar(judge), Js[o].clone())
        out.append((J.numpy(), 'mv-sweep<' + src[o]))
    Jall = torch.tensor(np.stack([J for J, _ in out]), dtype=T.DT)
    tot, per = judge(Jall)
    ref = c['V'][REF]
    kref = t
    cam = cam_of(REF, kref)
    cands = []
    for i, (J, s) in enumerate(out):
        cands.append({'from': s, 'params': to_view(J, ref['h0'], cam).tolist(), 'agreement': float(tot[i]),
                      'views': {v: [float(per[v][0][i]), float(per[v][1][i])] for v in per}})
    b = int(np.argmax(tot))
    print(f'{t}: {time.time() - t0:.1f}s pool {len(pool)} best {out[b][1]} '
          + ' '.join(f'{v} {per[v][0][b]:.2f}' for v in per), flush=True)
    return t, cands


def judge_scalar(judge):
    """PF.sweep wants judge(Js) -> (score, colour, parts); the four-view total stands in for all three."""
    def f(Js):
        tot, _ = judge(Js)
        return tot, tot, tot
    return f


def main(outdir, w=0.5):
    c = ctx()
    V = c['V']
    os.makedirs(outdir, exist_ok=True)
    calp = os.path.join(outdir, 'cameras.json')
    if not os.path.exists(calp):
        json.dump(calibrate(V, c['clip'], c['net'], time_shifts(V)), open(calp, 'w'))
    os.environ['CAL'] = calp
    c['cal'] = json.load(open(calp))
    shift = time_shifts(V)
    print('time shifts (frames, body-turn error deg, exact):', shift, flush=True)
    ref = V[REF]
    ts = sorted(ref['cands'])
    jobs = [(t, shift, w) for t in ts if ref['cands'][t][0]['from'] != 'idle']
    if os.environ.get('FRAMES'):  # a-b: a quick look at a stretch (nothing selected or written)
        a, b = map(int, os.environ['FRAMES'].split('-'))
        with Pool(int(os.environ.get('PROCS', '4'))) as pool:
            list(pool.imap_unordered(moment_job, [j for j in jobs if a <= j[0] <= b]))
        return
    frames = {str(t): ref['cands'][t] for t in ts}
    with Pool(int(os.environ.get('PROCS', '4'))) as pool:
        for t, cands in pool.imap_unordered(moment_job, jobs):
            frames[str(t)] = cands
    os.makedirs(outdir, exist_ok=True)
    data = {'name': 'left', 'track': ref['data']['track'], 'frames': frames}
    json.dump(data, open(os.path.join(outdir, 'left_cands.json'), 'w'))
    G.select(outdir, 'left')
    full = json.load(open(os.path.join(outdir, 'left_global.json')))
    # per view: the chosen pose in that view's footage, against that view's own best path
    rep = {'time_shift': {v: shift[v][2] for v in V}, 'turn_error_deg': {v: round(shift[v][1], 1) for v in V},
           'cameras': c['cal']}
    views = {}
    for t, row in full['frames'].items():
        cs = frames[t]
        cand = next(q for q in cs if q['params'] == row['params'])
        for v, (col, par) in cand.get('views', {}).items():
            k = int(t) - REPS[REF] + REPS[v] + shift[v][0]
            views.setdefault(v, []).append((col, par, V[v]['path'][k]['agreement'], k, cand['params']))
    for v, rows in views.items():
        rep[v] = {'four_view_colour_mean': round(float(np.mean([r[0] for r in rows])), 4),
                  'four_view_ge_0.90': int(sum(r[0] >= 0.9 for r in rows)),
                  'own_path_colour_mean': round(float(np.mean([r[2] for r in rows])), 4),
                  'own_path_ge_0.90': int(sum(r[2] >= 0.9 for r in rows)),
                  'parts_mean': round(float(np.mean([r[1] for r in rows])), 4), 'n': len(rows)}
        # that view's path with the four-view poses, for clips/sheets
        out = dict(V[v]['track'])
        out['frames'] = {}
        for t, row in full['frames'].items():
            k = int(t) - REPS[REF] + REPS[v] + shift[v][0]
            if k not in V[v]['path']:
                continue
            cand = next(q for q in frames[t] if q['params'] == row['params'])
            if v not in cand.get('views', {}):
                continue
            J = to_pose(cand['params'], V[REF]['h0'])
            p = to_view(J, V[v]['h0'], cam_of(v, k))
            out['frames'][str(k)] = {'params': p.tolist(), 'agreement': cand['views'][v][0],
                                     'parts_agreement': cand['views'][v][1], 'how': 'multiview:' + cand['from']}
        json.dump(out, open(os.path.join(outdir, f'{v}_multiview.json'), 'w'))
    json.dump(rep, open(os.path.join(outdir, 'multiview_report.json'), 'w'), indent=1)
    print('MULTIVIEW', json.dumps(rep), flush=True)


if __name__ == '__main__':
    a = sys.argv[1:]
    if not a:
        sys.exit('OUTDIR [W]')
    main(a[0], float(a[1]) if len(a) > 1 else 0.5)
