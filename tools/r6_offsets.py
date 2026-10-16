# Joint offsets on a fitted path: Roblox moves a limb's joint as well as turning it (Motor6D positions), so in the
# footage arms and legs come away from the body; a rig that can only turn its limbs bends them wrong to make up for it.
# Per frame: each limb's (and the head's) offset found by descent on colour + W x parts (r6_partfit's Judge), every
# px of offset paying a little so nothing moves without a reason, then the limbs re-swept with the offsets in place,
# then the offsets again. Offsets ride after the joint angles in the params (r6_fit.OFF_BONES).
#   python3 tools/r6_offsets.py VIDEO PATH.json CANDS.json NET.pt OUT.json [W=0.5]
import sys, os, json, time
import numpy as np, torch
from multiprocessing import Pool
torch.set_num_threads(1)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T
import r6_oversee as O, r6_partnet as P, r6_partfit as PF

NC, NJ = O.NC, len(F.JOINTS)
MAX_PX = 8.0       # a joint moves at most this far (rig px; 6 px = 1 stud)
COST_PX = 0.002    # score paid per px of offset (L1): a real gap pays for itself, noise does not
STEPS = (3.0, 1.5, 0.75)


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
                    cands.append(q)
            Js = torch.stack(cands)
            v = value(Js)
            o = int(np.argmax(v))
            if v[o] <= best + 1e-6:
                break
            J, best = Js[o].clone(), float(v[o])
    return J, best


def frame_job(job):
    video, track, net_path, k, p, cands, w = job
    t0 = time.time()
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    p = np.array(p, float)
    ps = [p[:F.NP]] + [np.array(c['params'], float)[:F.NP] for c in cands]
    judge = PF.Judge(clip, net, k, ps, w)
    J0 = torch.tensor(np.concatenate([p[NC:F.NP], np.zeros(F.NOFF)]), dtype=T.DT)
    _, c0, p0 = judge(J0[None])
    J, _ = descend(judge, J0)
    J, _ = PF.sweep(judge, J, rounds=1)
    J, _ = descend(judge, J)
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
