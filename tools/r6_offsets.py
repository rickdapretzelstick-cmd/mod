# Joint offsets on a fitted path: Roblox moves a limb's joint as well as turning it (Motor6D positions), so in the
# footage arms and legs come away from the body; a rig that can only turn its limbs bends them wrong to make up for it.
# Per frame: each limb's (and the head's) offset searched with no limit on how far (Rick: "it needs to be unlimited")
# -- first a wide grid in the camera's picture plane (anywhere within WIDE rig px of the body, a step of GRID), then
# descent on colour + W x parts (r6_partfit's Judge), then the limbs re-swept with the offsets in place, then the
# offsets again. COST_PX (default 0) can make each px of offset pay. Offsets ride after the joint angles in the params
# (r6_fit.OFF_BONES); the judging window is widened so a limb moved far is still seen -- the only bound is that window
# (past it the footage cannot tell; raise WIDE to look further: 30 rig px = 5 studs).
#   python3 tools/r6_offsets.py VIDEO PATH.json CANDS.json NET.pt OUT.json [W=0.5]
import sys, os, json, time
import numpy as np, torch
from multiprocessing import Pool
torch.set_num_threads(1)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T
import r6_oversee as O, r6_partnet as P, r6_partfit as PF

NC, NJ = O.NC, len(F.JOINTS)
MAX_PX = float(os.environ.get('MAX_PX', 'inf'))  # no limit unless asked (rig px; 6 px = 1 stud)
COST_PX = float(os.environ.get('COST_PX', '0.0001'))  # per px of offset: only breaks ties (a limb hidden behind the
# body scores the same anywhere back there, so it stays put instead of wandering)
WIDE = float(os.environ.get('WIDE', '30'))  # the plane grid's reach (rig px); descent can go on past it
GRID = 3.0
STEPS = (6.0, 3.0, 1.5, 0.75)


def plane_dirs(p):
    """Per OFF_BONES bone: the parent-frame offsets that move it one rig px right and one rig px up in the picture
    (all these bones hang off the hips)."""
    world = F.pose_world(p[:F.NP])
    view, _ = F.camera_of(p)
    Rp = world['hips'][:3, :3]
    M2W = F.MODEL_TO_WORLD
    right = Rp.T @ M2W @ view.T @ np.array([1.0, 0, 0])
    up = Rp.T @ M2W @ view.T @ np.array([0, 1.0, 0])
    return right, up


def plane_search(judge, J, p):
    """Each bone in turn: its offset tried on a grid over the picture plane (±WIDE rig px), the best kept."""
    right, up = plane_dirs(p)
    best = float(judge(J[None])[0][0])
    g = np.arange(-WIDE, WIDE + 1e-6, GRID)
    for i in range(len(F.OFF_BONES)):
        c = NJ + 3 * i
        base = J[c:c + 3].clone()
        cands = []
        for a in g:
            for b in g:
                if a * a + b * b > WIDE * WIDE:
                    continue
                q = J.clone()
                q[c:c + 3] = base + torch.tensor(a * right + b * up, dtype=T.DT)
                cands.append(q)
        Js = torch.stack(cands)
        v = judge(Js)[0] - COST_PX * Js[:, NJ:].abs().sum(1).numpy()
        o = int(np.argmax(v))
        if v[o] > best + 1e-6:
            J, best = Js[o].clone(), float(v[o])
    return J


def widened(p):
    """Copies of p with every offset bone pushed WIDE rig px each way in the picture plane: added to the judging
    window's poses so a limb found far from the body still lands inside it."""
    right, up = plane_dirs(p)
    out = []
    for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
        d = (a * right + b * up) * 1.3 * WIDE
        out.append(np.concatenate([p[:F.NP], np.tile(d, len(F.OFF_BONES))]))
    return out


def descend(judge, J):
    """Offset columns of J (NJ: NJ + NOFF) by batched coordinate descent: all single-column moves tried at once, the
    best taken, until none helps."""
    def value(Js):
        s = judge(Js)[0]
        return s - COST_PX * Js[:, NJ:].abs().sum(1).numpy()

    best = float(value(J[None])[0])
    for step in STEPS:
        for _ in range(12):
            cands = []
            for c in range(NJ, NJ + F.NOFF):
                for d in (-step, step):
                    q = J.clone()
                    q[c] = float(np.clip(float(q[c]) + d, -MAX_PX, MAX_PX))
                    if float(q[c - (c - NJ) % 3:c - (c - NJ) % 3 + 3].norm()) > 1.25 * WIDE:
                        continue  # past the judging window's edge: the footage can't tell there
                    cands.append(q)
            Js = torch.stack(cands)
            v = value(Js)
            o = int(np.argmax(v))
            if v[o] <= best + 1e-6:
                break
            J, best = Js[o].clone(), float(v[o])
    return J, best


SEEN_MIN = 120    # a moved limb must show this many judged px...
ACC_MIN = 0.6     # ...of which this share agree in colour, or its offset is undone: a limb that only "fits" by
# leaving the picture (into the window's unjudged border) or by hiding is not evidence of a gap


def seen_parts(judge, J):
    """Per OFF_BONES bone: (judged drawn px, share agreeing) for joints J in the judge's window."""
    v, fit = judge.v, judge.fit
    Jt, C = J[None], judge.cam
    parts = T.hard_draw(v, 0, Jt, C, fit.persp[0], fit.lights[0], judge.pid)[0]
    drawn = T.hard_draw(v, 0, Jt, C, fit.persp[0], fit.lights[0], fit.classes)
    o, known, drawn = T._hard_counts(v, 0, drawn)
    drawn = drawn[0]
    out = {}
    for b in F.OFF_BONES:
        boxes = [n + 1 for n, (bone, _, _, _) in enumerate(F.PARTS) if bone == b]
        m = np.isin(parts, boxes) & known
        n = int(m.sum())
        out[b] = (n, float((m & (drawn == o)).sum()) / max(n, 1))
    return out


def undo_unseen(judge, J):
    """Offsets of bones that moved but are not seen well where they went: back to zero."""
    seen = seen_parts(judge, J)
    for i, b in enumerate(F.OFF_BONES):
        c = NJ + 3 * i
        if float(J[c:c + 3].norm()) > 1.5 and (seen[b][0] < SEEN_MIN or seen[b][1] < ACC_MIN):
            J = J.clone()
            J[c:c + 3] = 0
    return J


def frame_job(job):
    video, track, net_path, k, p, cands, w = job
    t0 = time.time()
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    p = np.array(p, float)
    ps = [p[:F.NP]] + [np.array(c['params'], float)[:F.NP] for c in cands] + widened(p)
    judge = PF.Judge(clip, net, k, ps, w)
    J0 = torch.tensor(np.concatenate([p[NC:F.NP], np.zeros(F.NOFF)]), dtype=T.DT)
    _, c0, p0 = judge(J0[None])
    J = plane_search(judge, J0, p)
    J, _ = descend(judge, J)
    J = undo_unseen(judge, J)
    J, _ = PF.sweep(judge, J, rounds=1)
    J, _ = descend(judge, J)
    J = undo_unseen(judge, J)
    _, c1, p1 = judge(J[None])
    if c1[0] + w * p1[0] - COST_PX * float(J[NJ:].abs().sum()) < c0[0] + w * p0[0]:
        J, c1, p1 = J0, c0, p0  # no better: keep the frame as it was
    q = np.concatenate([p[:NC], J.numpy()])
    off = np.abs(J[NJ:].numpy()).reshape(-1, 3).sum(1)
    print(f'{k}: {time.time() - t0:.1f}s colour {c0[0]:.3f} -> {c1[0]:.3f} parts {p0[0]:.3f} -> {p1[0]:.3f} offsets px '
          + ' '.join(f'{b} {o:.1f}' for b, o in zip(F.OFF_BONES, off) if o > 0.1), flush=True)
    return k, q.tolist(), float(c0[0]), float(c1[0]), float(p1[0])


def main(video, path_json, cands_json, net_path, out, w=0.5):
    tr = json.load(open(path_json))
    cd = json.load(open(cands_json))
    cands, track = cd['frames'], cd['track']
    jobs = [(video, track, net_path, int(k), r['params'], cands.get(k, []), w) for k, r in tr['frames'].items()
            if not r.get('how', '').endswith('idle')]
    if os.environ.get('FRAMES'):  # a,b,c: only these (a quick look)
        only = {int(x) for x in os.environ['FRAMES'].split(',')}
        jobs = [j for j in jobs if j[3] in only]
    res = {}
    with Pool(int(os.environ.get('PROCS', '4'))) as pool:
        for k, q, a0, a1, pp in pool.imap_unordered(frame_job, jobs):
            res[k] = (q, a0, a1, pp)
    for k, (q, a0, a1, pp) in res.items():
        r = tr['frames'][str(k)]
        r['params'], r['agreement'], r['parts_agreement'] = q, a1, pp
        r['how'] = r.get('how', '') + '+offsets'
    json.dump(tr, open(out, 'w'))
    a0 = [v[1] for v in res.values()]
    a1 = [v[2] for v in res.values()]
    rep = {'frames': len(res), 'colour_before': round(float(np.mean(a0)), 4), 'colour_after': round(float(np.mean(a1)), 4),
           'ge_0.90_before': int(sum(a >= 0.9 for a in a0)), 'ge_0.90_after': int(sum(a >= 0.9 for a in a1))}
    print('OFFSETS', json.dumps(rep), flush=True)


if __name__ == '__main__':
    a = sys.argv[1:]
    if len(a) < 5:
        sys.exit('VIDEO PATH.json CANDS.json NET.pt OUT.json [W]')
    main(*a[:5], float(a[5]) if len(a) > 5 else 0.5)
