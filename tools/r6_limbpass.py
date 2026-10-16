# Whole-clip pass per body part: a fitted path's frames, judged one at a time, let a limb jump between poses that
# score alike -- a square R6 arm twisted a quarter or half turn looks almost the same, a hidden arm fits anywhere behind
# the body -- so the animation flips 90-180 degrees between frames where the real move turned smoothly. Here, per bone
# (hips, head, each arm and leg), each frame gets candidate poses for that bone alone (its own, its neighbours',
# the frame's other fitted candidates, each limb also twisted 90/180/270 degrees about its length), each scored in the
# frame's own window with the rest of the pose kept (colour + W x parts, r6_partfit's Judge); then one choice per frame
# by dynamic programming: total score less a cost on the bone's real turn between frames (geodesic, twist included:
# LAMBDA x (deg / 90)^2) and on its joint offset's change. A flip the footage does not clearly ask for loses.
# Then each frame is polished in place (small steps only, within 15 deg: R6_POLISH=0 skips it).
#   python3 tools/r6_limbpass.py VIDEO PATH.json CANDS.json NET.pt OUT.json [ROUNDS=2]   (ROUNDS 0: polish only)
import sys, os, json, time
import numpy as np, torch
from multiprocessing import Pool
from scipy.spatial.transform import Rotation as Rot
import warnings
warnings.filterwarnings('ignore', message='Gimbal lock')
torch.set_num_threads(1)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T
import r6_oversee as O, r6_partnet as P, r6_partfit as PF, r6_offsets as OF

NC, NJ = O.NC, len(F.JOINTS)
BONES = ['hips', 'head', 'rightArm', 'leftArm', 'rightThigh', 'leftThigh']
LIMBS = ('rightArm', 'leftArm', 'rightThigh', 'leftThigh')
COLS = {b: [F.JI[(b, a)] - NC for a in range(3)] for b in BONES}
OFFC = {b: (NJ + 3 * F.OFF_BONES.index(b) if b in F.OFF_BONES else None) for b in BONES}
LAMBDA = float(os.environ.get('LAMBDA', '0.03'))      # agreement units per (90 deg of turn between frames)^2
LAMBDA_OFF = float(os.environ.get('LAMBDA_OFF', '0.01'))  # ... per huber(studs of offset change; 6 rig px = 1 stud)
W = float(os.environ.get('W', '0.5'))


def euler_to_R(e):
    return Rot.from_euler('ZYX', [e[2], e[1], e[0]], degrees=True)


def R_to_euler(r, near):
    """The (x, y, z) of rotation r (the rigs' Rz Ry Rx order) of its two equivalent forms, each wrapped by 360, that is
    nearest `near`: linear interpolation between neighbouring keys then takes the short way."""
    z, y, x = r.as_euler('ZYX', degrees=True)
    best = None
    for e in (np.array([x, y, z]), np.array([x + 180, 180 - y, z + 180])):
        e = near + (e - near + 180) % 360 - 180
        d = np.abs(e - near).sum()
        if best is None or d < best[0]:
            best = (d, e)
    return best[1]


def full_J(p):
    p = np.asarray(p, float)
    J = np.zeros(NJ + F.NOFF)
    q = p[NC:]
    J[:min(len(q), len(J))] = q[:len(J)]
    return J


def bone_of(J, b):
    rot = J[COLS[b]].copy()
    off = J[OFFC[b]:OFFC[b] + 3].copy() if OFFC[b] is not None else np.zeros(3)
    return rot, off


def candidates(path, cands, ks, b):
    """Per frame, candidate (rotation euler, offset) for bone b, the frame's own first."""
    out = {}
    for i, k in enumerate(ks):
        src = [path[k]]
        for d in (-2, -1, 1, 2):
            if 0 <= i + d < len(ks):
                src.append(path[ks[i + d]])
        src += [full_J(c['params']) for c in cands.get(k, [])]
        opts, quats = [], []
        for J in src:
            rot, off = bone_of(J, b)
            base = euler_to_R(rot)
            for tw in ((0, 90, 180, 270) if b in LIMBS else (0,)):
                r = base * Rot.from_euler('y', tw, degrees=True) if tw else base
                q = r.as_quat()
                # Already there (within 3 degrees and half a px of offset)?
                if any(abs(float(q @ q2)) > np.cos(np.radians(1.5)) and np.abs(o[1] - off).max() <= 0.5 for q2, o in zip(quats, opts)):
                    continue
                opts.append((R_to_euler(r, rot), off.copy()))
                quats.append(q)
        out[k] = opts
    return out


def score_job(job):
    video, track, net_path, k, p, cps, opts = job
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    p = np.asarray(p, float)
    ps = [p[:F.NP]] + [np.asarray(c, float)[:F.NP] for c in cps] + OF.widened(p)
    judge = PF.Judge(clip, net, k, ps, W)
    J0 = full_J(p)
    res = {}
    for b, cand in opts.items():
        Js = []
        for e, off in cand:
            J = J0.copy()
            J[COLS[b]] = e
            if OFFC[b] is not None:
                J[OFFC[b]:OFFC[b] + 3] = off
            Js.append(J)
        res[b] = [float(x) for x in judge(torch.tensor(np.stack(Js), dtype=T.DT))[0]]
    return k, res


POLISH_STEPS = (4.0, 2.0, 1.0)   # deg (and rig px for offsets) per coordinate-descent step
POLISH_MAX = 15.0                 # a joint may drift this far from its chosen pose: small enough that no flip comes back


def polish_job(job):
    """The chosen pose of one frame refined in place: every joint angle and offset nudged by small steps (all single
    moves tried at once, the best taken, until none helps), each kept within POLISH_MAX of where the limb pass put it.
    Discrete candidates land near the footage's pose, not on it; this closes the gap without any frame-to-frame jump."""
    video, track, net_path, k, p, cps = job
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    p = np.asarray(p, float)
    ps = [p[:F.NP]] + [np.asarray(c, float)[:F.NP] for c in cps] + OF.widened(p)
    judge = PF.Judge(clip, net, k, ps, W)
    J0 = torch.tensor(full_J(p), dtype=T.DT)
    J = J0.clone()
    best = start = float(judge(J[None])[0][0])
    for step in POLISH_STEPS:
        for _ in range(10):
            cands = []
            for c in range(len(J)):
                for d in (-step, step):
                    q = J.clone()
                    q[c] = float(q[c]) + d
                    if abs(float(q[c] - J0[c])) > POLISH_MAX:
                        continue
                    cands.append(q)
            Js = torch.stack(cands)
            v = judge(Js)[0]
            o = int(np.argmax(v))
            if v[o] <= best + 1e-6:
                break
            J, best = Js[o].clone(), float(v[o])
    return k, np.concatenate([p[:NC], J.numpy()]), start, best


def huber(d):
    """Quadratic up to 1 (stud), linear past it: offset jitter pays, but a limb thrown far for real (a kick into the
    dummy) is not priced out of the clip."""
    return d * d if d <= 1 else 2 * d - 1


def viterbi(ks, opts, scores, b):
    R = {k: [euler_to_R(e) for e, _ in opts[k]] for k in ks}
    total = [scores[ks[0]][j] for j in range(len(opts[ks[0]]))]
    back = {}
    for a, c in zip(ks, ks[1:]):
        new, bk = [], []
        for j, (e, off) in enumerate(opts[c]):
            best, bi = -1e9, 0
            for i, (e0, off0) in enumerate(opts[a]):
                deg = np.degrees((R[a][i].inv() * R[c][j]).magnitude())
                v = total[i] - LAMBDA * (deg / 90) ** 2 - LAMBDA_OFF * huber(np.linalg.norm(off - off0) / 6)
                if v > best:
                    best, bi = v, i
            new.append(best + scores[c][j])
            bk.append(bi)
        total, back[c] = new, bk
    j = int(np.argmax(total))
    pick = {}
    for k in reversed(ks):
        pick[k] = j
        if k in back:
            j = back[k][j]
    return pick


def main(video, path_json, cands_json, net_path, out, rounds=2):
    tr = json.load(open(path_json))
    cd = json.load(open(cands_json))
    track = cd['track']
    ks = sorted(int(k) for k in tr['frames'])
    params = {k: np.array(tr['frames'][str(k)]['params'], float) for k in ks}
    cands = {int(k): v for k, v in cd['frames'].items()}
    def flips():  # bone turns over 60 deg between consecutive frames
        n = 0
        for b in BONES:
            Rs = [euler_to_R(bone_of(full_J(params[k]), b)[0]) for k in ks]
            n += sum(np.degrees((r0.inv() * r1).magnitude()) > 60 for r0, r1 in zip(Rs, Rs[1:]))
        return int(n)
    flips0, mean = flips(), None
    for rnd in range(rounds):
        t0 = time.time()
        path = {k: full_J(params[k]) for k in ks}
        opts = {b: candidates(path, cands, ks, b) for b in BONES}
        jobs = [(video, track, net_path, k, params[k], [c['params'] for c in cands.get(k, [])], {b: opts[b][k] for b in BONES}) for k in ks]
        scores = {b: {} for b in BONES}
        with Pool(int(os.environ.get('PROCS', '4'))) as pool:
            for k, res in pool.imap_unordered(score_job, jobs):
                for b in BONES:
                    scores[b][k] = res[b]
        changed, got = 0, []
        for b in BONES:
            pick = viterbi(ks, opts[b], scores[b], b)
            got.append(np.mean([scores[b][k][pick[k]] for k in ks]))
            for k in ks:
                e, off = opts[b][k][pick[k]]
                if pick[k] != 0:
                    changed += 1
                q = np.concatenate([params[k][:NC], path[k]])
                q[NC + np.array(COLS[b])] = e
                if OFFC[b] is not None:
                    q[NC + OFFC[b]:NC + OFFC[b] + 3] = off
                params[k] = q
                path[k] = full_J(q)
        mean = float(got[-1])  # the last bone is judged with every other bone's choice already in place
        print(f'round {rnd + 1}: {changed} bone choices changed, score {mean:.4f}, {time.time() - t0:.0f} s', flush=True)
        if changed == 0:
            break
    if os.environ.get('R6_POLISH', '1') != '0':
        t0 = time.time()
        jobs = [(video, track, net_path, k, params[k], [c['params'] for c in cands.get(k, [])]) for k in ks]
        gain = []
        with Pool(int(os.environ.get('PROCS', '4'))) as pool:
            for k, q, a, b in pool.imap_unordered(polish_job, jobs):
                params[k] = q
                gain.append(b - a)
        mean = (mean or 0) + float(np.mean(gain))
        print(f'polish: score +{np.mean(gain):.4f} per frame, {time.time() - t0:.0f} s', flush=True)
    for k in ks:
        r = tr['frames'][str(k)]
        r['params'] = params[k].tolist()
        r['how'] = r.get('how', '') + '+limbpass'
    json.dump(tr, open(out, 'w'))
    print('wrote', out, flush=True)
    print('LIMBPASS ' + json.dumps({'frames': len(ks), 'flips_over_60_before': flips0, 'flips_over_60_after': flips(),
                                    'score_mean': mean, 'part_weight': W}), flush=True)


if __name__ == '__main__':
    a = sys.argv[1:]
    if len(a) < 5:
        sys.exit('VIDEO PATH.json CANDS.json NET.pt OUT.json [ROUNDS]')
    main(*a[:5], int(a[5]) if len(a) > 5 else 2)
