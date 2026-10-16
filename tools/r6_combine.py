# One animation from all four views: each view's fitted path (joint offsets included, r6_offsets.py) put on the
# reference view's timeline (r6_multiview's body-turn timing, to a tenth of a frame); then per moment, part by part
# (body, head, each arm, each leg), that part is taken -- rotation and offset -- from the view where THAT part scores
# best (its drawn pixels' colour agreement, less when mostly hidden; the nearer of the two frames either side of the
# exact time counts more), not the whole pose from one view. By the numbers only. Writes the clip, one viewer
# conversion per view (the combined pose over that view's footage) and a report of how it fits each view.
#   python3 tools/r6_combine.py NAME [OUTDIR]
import sys, os, json, math
import numpy as np, torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T
import r6_oversee as O, r6_multiview as MV, r6_partfit as PF

NC, NJ = O.NC, len(F.JOINTS)
ROOT = MV.ROOT
PATHS = {'left': 'build/fit/v2/left_offsets.json', 'front': 'build/fit/v3/front_offsets.json',
         'back': 'build/fit/v3/back_offsets.json', 'right': 'build/fit/v3/right_offsets.json'}
CANDS = MV.CANDS
BONES = MV.BONES
COLS = {b: [F.JI[(b, a)] - NC for a in range(3)] for b in BONES}
ROBUST_DEG = 25.0
# Which drawn boxes (F.PARTS entries) speak for each bone, and which bone each offset belongs to.
BONE_BOXES = {b: [n for n, (bone, _, _, _) in enumerate(F.PARTS) if bone == b] for b in BONES}
SEEN_FULL = 250  # drawn known pixels at which a part counts as fully seen (fewer: hidden, its say shrinks)


def part_weights(job):
    """Per bone, how accurate that part is in this view's frame: (its drawn pixels whose footage colour agrees / its
    drawn pixels whose colour is known)^2, scaled down when the part is mostly hidden. Returns (view, k, {bone: w})."""
    v, k, params, cands = job
    clip = MV.ctx()['clip'][v]
    p = np.array(params, float)
    ps = [p[:F.NP]] + [np.array(q, float)[:F.NP] for q in cands]
    fit, view = window(clip, k, ps)
    J, C = torch.tensor(p[None, NC:], dtype=T.DT), torch.tensor(p[None, :5], dtype=T.DT)
    pid = (T.PART_OF_FACE + 1).to(torch.int64)
    parts = T.hard_draw(view, 0, J, C, fit.persp[0], fit.lights[0], pid)[0]
    drawn = T.hard_draw(view, 0, J, C, fit.persp[0], fit.lights[0], fit.classes)
    o, known, drawn = T._hard_counts(view, 0, drawn)
    drawn = drawn[0]
    out = {}
    for b, boxes in BONE_BOXES.items():
        m = np.isin(parts, [n + 1 for n in boxes]) & known
        seen = int(m.sum())
        acc = float((m & (drawn == o)).sum()) / max(seen, 1)
        out[b] = acc ** 2 * min(1.0, seen / SEEN_FULL) + 1e-4
    return v, k, out


def window(clip, k, ps):
    import r6_global as G
    rgb = clip.rgb(k)
    enc = F.classify(rgb, O.pal)
    view = T.View([rgb], {0: enc}, [0], 30, 112)
    view.lite = True
    win = G.UnionWindow(clip.f0, ps, clip.light, 112)
    fit = T.Fit([view], ps[0][None, NC:], [ps[0][None, :5]], [float(ps[0][5])], [clip.light])
    view.window(0, ps[0], win)
    return fit, view


def load():
    V = {}
    for v, p in PATHS.items():
        path = json.load(open(os.path.join(ROOT, p)))
        cd = json.load(open(os.path.join(ROOT, CANDS[v])))
        tr = json.load(open(cd['track'] if os.path.isabs(cd['track']) else os.path.join(ROOT, cd['track'])))
        h0 = tr['frames'][str(min(int(k) for k in tr['frames']))]['params'][F.JI[('hips', 1)]]
        V[v] = {'path': {int(k): r for k, r in path['frames'].items()}, 'h0': float(h0), 'track': path,
                'cands': {int(k): c for k, c in cd['frames'].items()}}
    return V


def joints(r, h0):
    """A path row -> view-free joints with offsets (NJ + NOFF)."""
    p = np.array(r['params'], float)
    J = MV.to_pose(p, h0)
    full = np.zeros(NJ + F.NOFF)
    full[:len(J)] = J[:NJ + F.NOFF]
    return full


def project(M):
    u, _, vt = np.linalg.svd(M)
    R = u @ vt
    if np.linalg.det(R) < 0:
        u[:, -1] *= -1
        R = u @ vt
    return R


def wmedian(x, w):
    o = np.argsort(x)
    c = np.cumsum(w[o])
    return float(x[o][np.searchsorted(c, 0.5 * c[-1])])


def combine(V, shift, t, PW):
    cands = []  # (view-free joints, time weight, view, {bone: part weight})
    for v, D in V.items():
        x = t - MV.REPS[MV.REF] + MV.REPS[v] + shift[v][2]
        a = math.floor(x)
        for k, wt in ((a, 1 - (x - a)), (a + 1, x - a)):
            r = D['path'].get(k)
            if r is None or wt < 1e-3:
                continue
            cands.append((joints(r, D['h0']), wt, v, PW[(v, k)]))
    # Rick: per part, the view whose part scored highest (by the numbers): its rotation, and its offset.
    J = np.zeros(NJ + F.NOFF)
    support = {}
    for b in BONES:
        best = max(cands, key=lambda c: c[1] * c[3][b])
        J[COLS[b]] = best[0][COLS[b]]
        if b in F.OFF_BONES:
            i = F.OFF_BONES.index(b)
            J[NJ + 3 * i:NJ + 3 * i + 3] = best[0][NJ + 3 * i:NJ + 3 * i + 3]
        support[b] = {'view': best[2], 'score': round(best[3][b], 3)}
    return J, support


def main(name, outdir=None):
    outdir = outdir or os.path.join(ROOT, 'build', 'fit', name)
    os.makedirs(outdir, exist_ok=True)
    V = load()
    shift = MV.time_shifts(V)
    print('time shifts (frames, body-turn error deg, exact):', shift, flush=True)
    ref = V[MV.REF]
    ts = sorted(ref['path'])
    jobs = [(v, k, r['params'], [q['params'] for q in D['cands'].get(k, [])]) for v, D in V.items()
            for k, r in D['path'].items()]
    from multiprocessing import Pool
    with Pool(int(os.environ.get('PROCS', '4'))) as pool:
        PW = {(v, k): w for v, k, w in pool.imap_unordered(part_weights, jobs, chunksize=8)}
    fused, support = {}, {}
    for t in ts:
        fused[t], support[t] = combine(V, shift, t, PW)
    clips = MV.ctx()['clip']
    report = {'time_shift': {v: shift[v][2] for v in V}, 'views': {}}
    for v, D in V.items():
        out = dict(D['track'])
        out['frames'] = {}
        own, comb = [], []
        for t in ts:
            k = t - MV.REPS[MV.REF] + MV.REPS[v] + shift[v][0]
            r = D['path'].get(k)
            if r is None:
                continue
            cam = np.array(r['params'], float)
            p = MV.to_view(fused[t], D['h0'], cam)
            ps = [cam[:F.NP]] + [np.array(q['params'], float)[:F.NP] for q in D['cands'].get(k, [])]
            fit_ag = colour(clips[v], k, ps, p)
            out['frames'][str(k)] = {'params': p.tolist(), 'agreement': fit_ag, 'how': 'combined'}
            own.append(r['agreement'])
            comb.append(fit_ag)
        tp = os.path.join(outdir, f'{v}_combined.json')
        json.dump(out, open(tp, 'w'))
        report['views'][v] = {'own_mean': round(float(np.mean(own)), 4), 'own_ge_0.90': int(sum(a >= 0.9 for a in own)),
                              'combined_mean': round(float(np.mean(comb)), 4),
                              'combined_ge_0.90': int(sum(a >= 0.9 for a in comb)), 'n': len(comb)}
        print(v, report['views'][v], flush=True)
        O.write_clip(MV.VIDEO, tp, v, f'{name}_{v}')
    report['support'] = {t: support[t] for t in ts}
    json.dump(report, open(os.path.join(outdir, 'combine_report.json'), 'w'), indent=1)
    print('COMBINED', json.dumps(report['views']), flush=True)


def colour(clip, k, ps, p):
    """Colour agreement of params p in frame k, in the common window of ps and p."""
    fit, _ = window(clip, k, ps + [p[:F.NP]])
    return float(T.agreement(fit, 0, 0, torch.tensor(p[None, NC:], dtype=T.DT), torch.tensor(p[None, :5], dtype=T.DT))[0])


if __name__ == '__main__':
    a = sys.argv[1:]
    if not a:
        sys.exit('NAME [OUTDIR]')
    main(a[0], a[1] if len(a) > 1 else None)
