# The part network proposes poses, not only judges them: from each frame's best few candidates, every limb (arms, then
# legs) is swung through its whole range, and the body turned, each pose ranked by colour + W x part agreement (the rig
# drawn as body parts against the network's part labels, r6_partscore.py), the best kept, twice round, then a fine pass.
# The results join the frame's candidates and the whole-clip pick (r6_global's Viterbi) runs on colour + W x parts.
#   python3 tools/r6_partfit.py VIDEO CANDS.json NET.pt OUTDIR [W=1.0] [FRAMES a-b]
import sys, os, json, time
import numpy as np, torch
from multiprocessing import Pool
torch.set_num_threads(1)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T
import r6_oversee as O, r6_global as G, r6_partnet as P, r6_partscore as S

NC = O.NC
STARTS = 3
LIMB_BONES = ('rightArm', 'leftArm', 'rightThigh', 'leftThigh')


class Judge:
    """Colour + W x parts of joint sets of one frame, all in one window (the union of the frame's candidates)."""

    def __init__(self, clip, net, k, ps, w):
        rgb = clip.rgb(k)
        enc = F.classify(rgb, O.pal)
        self.v = v = T.View([rgb], {0: enc}, [0], 30, 112)
        v.lite = True
        win = G.UnionWindow(clip.f0, ps, clip.light, 112)
        self.fit = T.Fit([v], ps[0][None, NC:], [ps[0][None, :5]], [float(ps[0][5])], [clip.light])
        v.window(0, ps[0], win)
        self.pred = P.predict(net, [P.window_rgb(rgb, win.roi(ps[0], enc))])[0].argmax(0)
        self.cam = torch.tensor(ps[0][None, :5], dtype=T.DT)
        self.pid = (T.PART_OF_FACE + 1).to(torch.int64)
        self.w = w

    def parts(self, Js):
        out = []
        for c in range(0, len(Js), 64):
            J = Js[c:c + 64]
            drawn = T.hard_draw(self.v, 0, J, self.cam.expand(len(J), 5), self.fit.persp[0], self.fit.lights[0], self.pid)
            out.append(S.part_scores(self.pred, S.BOX_PART[drawn]))
        return np.concatenate(out)

    def colour(self, Js):
        return np.concatenate([T.agreement(self.fit, 0, 0, Js[c:c + 64], self.cam.expand(len(Js[c:c + 64]), 5)).numpy()
                               for c in range(0, len(Js), 64)])

    def __call__(self, Js):
        c, p = self.colour(Js), self.parts(Js)
        return c + self.w * p, c, p


def clipj(q, cols):
    for j in cols:
        q[j] = float(np.clip(float(q[j]), *F.LIMITS[F.JOINTS[j]]))
    return q


def sweep(judge, J, step=30, rounds=2):
    def better(cands):
        nonlocal J, best
        Js = torch.stack(cands)
        sc = judge(Js)[0]
        o = int(np.argmax(sc))
        if sc[o] > best:
            J, best = Js[o].clone(), float(sc[o])

    best = float(judge(J[None])[0][0])
    for _ in range(rounds):
        # The body's turn and lean first (a whole-body error makes every limb look wrong).
        hy, hx = F.JI[('hips', 1)] - NC, F.JI[('hips', 0)] - NC
        better([clipj(_set(J, {hy: J[hy] + dy, hx: J[hx] + dx}), [hy, hx])
                for dy in range(-180, 180, 30) for dx in (-30, 0, 30)])
        for bone in LIMB_BONES:
            jx, jy, jz = (F.JI[(bone, a)] - NC for a in range(3))
            lx, lz = F.LIMITS[(bone, 0)], F.LIMITS[(bone, 2)]
            turns = (-90, -45, 0, 45, 90) if 'Arm' in bone else (-45, 0, 45)
            better([_set(J, {jx: ax, jy: ay, jz: az}) for ax in np.arange(lx[0], lx[1] + 1, step)
                    for az in np.arange(lz[0], lz[1] + 1, step) for ay in turns])
        hd = (F.JI[('head', 0)] - NC, F.JI[('head', 1)] - NC)
        better([clipj(_set(J, {hd[0]: J[hd[0]] + a, hd[1]: J[hd[1]] + b}), hd) for a in (-30, 0, 30) for b in (-45, 0, 45)])
    for bone in LIMB_BONES:
        jx, jy, jz = (F.JI[(bone, a)] - NC for a in range(3))
        better([clipj(_set(J, {jx: J[jx] + dx, jy: J[jy] + dy, jz: J[jz] + dz}), [jx, jy, jz])
                for dx in (-20, -10, 0, 10, 20) for dz in (-20, -10, 0, 10, 20) for dy in (-30, 0, 30)])
    return J, best


def _set(J, vals):
    q = J.clone()
    for j, x in vals.items():
        q[j] = float(x)
    return q


def frame_job(job):
    video, track, net_path, k, cands, w = job
    t0 = time.time()
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    ps = [np.array(c['params'], float) for c in cands]
    judge = Judge(clip, net, k, ps, w)
    Js = torch.tensor(np.stack(ps)[:, NC:], dtype=T.DT)
    _, col, par = judge(Js)
    letters = [c.get('agreement', 0) - c.get('colour', c['agreement']) for c in cands]
    out = []
    for c, a, b, l in zip(cands, col, par, letters):
        out.append(dict(c, colour=float(a), parts=float(b), letters=float(l)))
    top = np.argsort(-(col + w * par))[:STARTS]
    for o in top:
        J, _ = sweep(judge, Js[o].clone())
        q = ps[o].copy()
        q[NC:] = J.numpy()
        _, a, b = judge(J[None])
        out.append({'from': f'partfit<{cands[o]["from"]}', 'params': q.tolist(), 'colour': float(a[0]), 'parts': float(b[0]),
                    'letters': 0.0})
    for c in out:
        c['agreement'] = c['colour'] + w * c['parts']
    best = max(out, key=lambda c: c['agreement'])
    print(f'{k}: {time.time() - t0:.1f}s best {best["from"]} colour {best["colour"]:.3f} parts {best["parts"]:.3f}'
          f' (was colour {max(col):.3f})', flush=True)
    return k, out


def main(video, cands_json, net_path, outdir, w=1.0, frames=None):
    data = json.load(open(cands_json))
    ks = sorted(data['frames'], key=int)
    if frames:
        a, b = map(int, frames.split('-'))
        ks = [k for k in ks if a <= int(k) <= b]
    jobs = [(video, data['track'], net_path, int(k), data['frames'][k], w) for k in ks
            if data['frames'][k][0]['from'] != 'idle']
    with Pool(int(os.environ.get('PROCS', '4'))) as pool:
        for k, out in pool.imap_unordered(frame_job, jobs):
            data['frames'][str(k)] = out
    for k in ks:  # idle frames: keep as they are, with a parts field so the report can read them
        for c in data['frames'][k]:
            c.setdefault('colour', c['agreement'])
            c.setdefault('parts', 0.0)
    os.makedirs(outdir, exist_ok=True)
    data['frames'] = {k: data['frames'][k] for k in ks}
    json.dump(data, open(os.path.join(outdir, f'{data["name"]}_cands.json'), 'w'))
    G.select(outdir, data['name'])
    full = json.load(open(os.path.join(outdir, f'{data["name"]}_global.json')))
    path = full['frames']
    pick = {k: next(c for c in data['frames'][k] if c['params'] == path[k]['params']) for k in path}
    moving = [k for k in pick if data['frames'][k][0]['from'] != 'idle']
    rep = {'part_weight': w, 'colour_mean': round(float(np.mean([pick[k]['colour'] for k in moving])), 4),
           'colour_ge_0.90': int(sum(pick[k]['colour'] >= 0.9 for k in moving)),
           'parts_mean': round(float(np.mean([pick[k]['parts'] for k in moving])), 4),
           'parts_ge_0.80': int(sum(pick[k]['parts'] >= 0.8 for k in moving)),
           'picked_partfit': int(sum(pick[k]['from'].startswith('partfit') for k in moving))}
    for k in path:
        path[k]['agreement'], path[k]['parts_agreement'] = pick[k]['colour'], pick[k]['parts']
    full['parts'] = rep
    json.dump(full, open(os.path.join(outdir, f'{data["name"]}_global.json'), 'w'))
    print('PARTFIT', json.dumps(rep), flush=True)


if __name__ == '__main__':
    a = sys.argv[1:]
    if len(a) < 4:
        sys.exit('VIDEO CANDS.json NET.pt OUTDIR [W] [FRAMES a-b]')
    main(a[0], a[1], a[2], a[3], float(a[4]) if len(a) > 4 else 1.0, a[5] if len(a) > 5 else None)
