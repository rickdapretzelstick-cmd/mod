# Whole-clip pass per body part: a fitted path's frames, judged one at a time, let a limb jump between poses that
# score alike -- a square R6 arm twisted a quarter or half turn looks almost the same, a hidden arm fits anywhere behind
# the body -- so the animation flips 90-180 degrees between frames where the real move turned smoothly. Here, per bone
# (hips, head, each arm and leg), each frame gets candidate poses for that bone alone (its own, its neighbours',
# the frame's other fitted candidates, each limb also twisted 90/180/270 degrees about its length), each scored in the
# frame's own window with the rest of the pose kept (colour + W x parts, r6_partfit's Judge); then one choice per frame
# by dynamic programming: total score less a cost on the bone's real turn between frames (geodesic, twist included:
# LAMBDA x (deg / 90)^2) and on its joint offset's change. A flip the footage does not clearly ask for loses.
# Then each frame is polished in place (small steps only, within 15 deg: R6_POLISH=0 skips it).
# Last, the clip is smoothed: each frame moved (within 20 deg) to trade its score against the acceleration of every
# bone's turn and offset across its neighbours, so poses the footage can't tell apart stop shaking frame to frame
# (R6_SMOOTH sweeps, 0 skips it; SMOOTH_MU its strength).
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


PARTNER = {'rightArm': 'leftArm', 'leftArm': 'rightArm', 'rightThigh': 'leftThigh', 'leftThigh': 'rightThigh'}


def box_mid(b):
    """The middle of bone b's box in its own frame (rig px, at the avatar's proportions)."""
    lo, hi, part = next((lo, hi, part) for bb, lo, hi, part in F.PARTS if bb == b and part != 'face')
    lo, hi = F.scaled_box(part, lo, hi)
    return (np.array(lo) + np.array(hi)) / 2


def candidates(path, cands, ks, b):
    """Per frame, candidate (rotation euler, offset) for bone b, the frame's own first. A limb this frame has thrown
    away from the body (offset > 1.5 px) also gets every other rotation with the offset re-set so its box stays where
    this frame's own pose put it: the place is the footage's, the way it points can then follow its neighbours."""
    out = {}
    mid = box_mid(b)
    for i, k in enumerate(ks):
        src = [path[k]]
        for d in (-2, -1, 1, 2):
            if 0 <= i + d < len(ks):
                src.append(path[ks[i + d]])
        src += [full_J(c['params']) for c in cands.get(k, [])]
        src.append(np.zeros_like(path[k]))  # rest: where a part the footage can't see settles (see pose_prior)
        own_rot, own_off = bone_of(path[k], b)
        own_mid = own_off + euler_to_R(own_rot).apply(mid)
        thrown = OFFC[b] is not None and np.linalg.norm(own_off) > 1.5
        opts, quats = [], []
        for J in src:
            rot, off = bone_of(J, b)
            base = euler_to_R(rot)
            for tw in ((0, 90, 180, 270) if b in LIMBS else (0,)):
                r = base * Rot.from_euler('y', tw, degrees=True) if tw else base
                q = r.as_quat()
                for o in ((off, own_mid - r.apply(mid)) if thrown else (off,)):
                    # Already there (within 3 degrees and half a px of offset)?
                    if any(abs(float(q @ q2)) > np.cos(np.radians(1.5)) and np.abs(o2[1] - o).max() <= 0.5 for q2, o2 in zip(quats, opts)):
                        continue
                    opts.append((R_to_euler(r, rot), np.array(o, float)))
                    quats.append(q)
        mate = PARTNER.get(b)
        if mate is not None:
            # The same-sized partner limb thrown here (the hunt can't tell which leg kicked, so neighbouring frames
            # may disagree): this bone in its place, so the thrown limb can be one bone through the whole throw.
            m_rot, m_off = bone_of(path[k], mate)
            if np.linalg.norm(m_off) > 1.5:
                Rm = euler_to_R(m_rot)
                there = np.array(F.REST[mate], float) + m_off + Rm.apply(box_mid(mate))
                for tw in (0, 90, 180, 270):
                    r = Rm * Rot.from_euler('y', tw, degrees=True) if tw else Rm
                    opts.append((R_to_euler(r, own_rot), there - np.array(F.REST[b], float) - r.apply(mid)))
        out[k] = opts
    return out


REST_PRIOR = float(os.environ.get('REST_PRIOR', '0.04'))  # agreement units per (90 deg of a limb or the head from rest)^2
OFF_PRIOR = float(os.environ.get('OFF_PRIOR', '0.002'))   # ... per rig px of joint offset, up to OFF_PRIOR_CAP
OFF_PRIOR_CAP = 0.04
PRIOR_BONES = ('head', 'rightArm', 'leftArm', 'rightThigh', 'leftThigh')


HIDDEN_SHARE = 0.1  # a part showing less than this share of the torso's judged px is hidden (the prior holds it)


def hidden_weights(judge, J):
    """Per part, how hidden it is in this frame (1: the footage shows none of it, 0: it shows HIDDEN_SHARE of the
    torso's px or more), judged at the frame's own pose."""
    seen = OF.seen_parts(judge, torch.tensor(np.asarray(J, float), dtype=T.DT))
    torso = max(seen['hips'][0], 120)
    return {b: float(np.clip(1 - seen[b][0] / (HIDDEN_SHARE * torso), 0, 1)) for b in PRIOR_BONES}


def pose_prior(Js, hidden):
    """A small cost on each hidden part's turn from rest and on its joint offset: where the footage can't see a part
    (a far leg behind the near one in a side view) many poses score alike, and the judge's own small biases (a rig
    leg that peeks out from behind where the real one doesn't) push it anywhere it hides best -- a leg sideways, a
    leg sunk into the body. The prior settles it on the natural pose (standing: limbs down, head forward). A part
    the footage shows (`hidden` 0) pays nothing: a punch or a kick into the dummy is the footage's alone."""
    Js = np.atleast_2d(np.asarray(Js, float))
    cost = np.zeros(len(Js))
    for b in PRIOR_BONES:
        if hidden.get(b, 0) <= 0:
            continue
        deg = np.degrees(rot_batch(Js[:, COLS[b]]).magnitude())
        c = REST_PRIOR * np.minimum((deg / 90) ** 2, 2.0)
        if OFFC[b] is not None:
            px = np.linalg.norm(Js[:, OFFC[b]:OFFC[b] + 3], axis=1)
            c += np.minimum(OFF_PRIOR * px, OFF_PRIOR_CAP)
        cost += hidden[b] * c
    return cost


def score_job(job):
    video, track, net_path, k, p, cps, opts = job
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    p = np.asarray(p, float)
    ps = [p[:F.NP]] + [np.asarray(c, float)[:F.NP] for c in cps] + OF.widened(p)
    judge = PF.Judge(clip, net, k, ps, W)
    J0 = full_J(p)
    hid = hidden_weights(judge, J0)
    res = {}
    for b, cand in opts.items():
        Js = []
        for e, off in cand:
            J = J0.copy()
            J[COLS[b]] = e
            if OFFC[b] is not None:
                J[OFFC[b]:OFFC[b] + 3] = off
            Js.append(J)
        res[b] = [float(x) for x in judge(torch.tensor(np.stack(Js), dtype=T.DT))[0] - pose_prior(np.stack(Js), hid)]
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
    hid = hidden_weights(judge, J0.numpy())
    # Offsets pay r6_offsets' tie-break cost: a limb hidden behind the body scores alike anywhere back there, and
    # must stay put rather than drift off (an arm pulled a stud out of a standing idle pose).
    value = lambda Js: judge(Js)[0] - OF.COST_PX * Js[:, NJ:].abs().sum(1).numpy() - pose_prior(Js.numpy(), hid)
    best = start = float(value(J[None])[0])
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
            v = value(Js)
            o = int(np.argmax(v))
            if v[o] <= best + 1e-6:
                break
            J, best = Js[o].clone(), float(v[o])
    return k, np.concatenate([p[:NC], J.numpy()]), start, float(value(J[None])[0])


SMOOTH_SWEEPS = int(os.environ.get('R6_SMOOTH', '5'))   # whole-clip smoothing sweeps (0: none)
SMOOTH_MU = float(os.environ.get('SMOOTH_MU', '0.015'))   # agreement units per (SMOOTH_DEG of turn acceleration)^2 ...
SMOOTH_DEG = 30.0                                        # deg/frame^2: quadratic below, linear above, capped
SMOOTH_PX = 3.0                                          # rig px/frame^2, the same for joint offsets
SMOOTH_CAP = 4.0                                         # a sudden real snap (a punch, a kick out) costs no more than this
SMOOTH_MAX = 20.0                                        # deg (rig px) a joint may move from the pose the pass chose


def rough(x, unit):
    """Cost of an acceleration x (deg or px per frame^2): quadratic to `unit`, linear past it, capped at SMOOTH_CAP."""
    x = np.asarray(x) / unit
    return np.minimum(np.where(x <= 1, x * x, 2 * x - 1), SMOOTH_CAP)


def rot_batch(E):
    E = np.atleast_2d(E)
    return Rot.from_euler('ZYX', E[:, ::-1], degrees=True)


def roughness(Js, nb):
    """Per candidate pose Js[i] for frame k, the acceleration cost of the clip's curves at k-1, k and k+1 with the
    neighbouring frames nb[d] (d = -2..2, those that exist) held: each bone's turn rate (rotation vector between
    frames, so no Euler form or gimbal lock enters) and each joint offset's velocity, changed from frame to frame."""
    n = len(Js)
    cost = np.zeros(n)
    for b in BONES:
        Rc = rot_batch(Js[:, COLS[b]])
        R = {d: rot_batch(nb[d][COLS[b]]) for d in nb}
        w = {}  # angular velocity between frames k+d and k+d+1
        if -1 in R:
            w[-1] = (R[-1].inv() * Rc).as_rotvec(degrees=True)
            if -2 in R:
                w[-2] = np.repeat((R[-2].inv() * R[-1]).as_rotvec(degrees=True), n, 0)
        if 1 in R:
            w[0] = (Rc.inv() * R[1]).as_rotvec(degrees=True)
            if 2 in R:
                w[1] = np.repeat((R[1].inv() * R[2]).as_rotvec(degrees=True), n, 0)
        for d in (-2, -1, 0):
            if d in w and d + 1 in w:
                cost += rough(np.linalg.norm(w[d + 1] - w[d], axis=1), SMOOTH_DEG)
        if OFFC[b] is not None:
            c = slice(OFFC[b], OFFC[b] + 3)
            x = {d: np.repeat(nb[d][c][None], n, 0) for d in nb}
            x[0] = Js[:, c]
            for d in (-1, 0, 1):
                if d - 1 in x and d in x and d + 1 in x:
                    cost += rough(np.linalg.norm(x[d + 1] - 2 * x[d] + x[d - 1], axis=1), SMOOTH_PX)
    return SMOOTH_MU * cost


def smooth_job(job):
    """One frame moved, its neighbours held, to the pose that best trades the footage's score against a smooth clip.
    The judge cannot tell many poses apart (a square limb a few degrees either way, a joint hidden in the body), and
    a frame-by-frame pick lands on any of them, so the clip shakes; here the smooth one of those wins. A big real
    change between frames pays at most a fixed cost, so the footage still decides it."""
    video, track, net_path, k, p, cps, anchor, nb = job
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    p = np.asarray(p, float)
    ps = [p[:F.NP]] + [np.asarray(c, float)[:F.NP] for c in cps] + OF.widened(p)
    judge = PF.Judge(clip, net, k, ps, W)
    A = full_J(anchor)
    J = full_J(p)
    hid = hidden_weights(judge, J)
    value = lambda Js: (judge(torch.tensor(Js, dtype=T.DT))[0] - OF.COST_PX * np.abs(Js[:, NJ:]).sum(1) - pose_prior(Js, hid)
                        - roughness(Js, nb))
    start = float(value(J[None])[0])
    best = start
    for step in POLISH_STEPS:
        for _ in range(10):
            cands = []
            for c in range(len(J)):
                for d in (-step, step):
                    q = J.copy()
                    q[c] += d
                    if abs(q[c] - A[c]) <= SMOOTH_MAX:
                        cands.append(q)
            Js = np.stack(cands)
            v = value(Js)
            o = int(np.argmax(v))
            if v[o] <= best + 1e-6:
                break
            J, best = Js[o], float(v[o])
    return k, np.concatenate([p[:NC], J]), start, best


def smooth_clip(video, track, net_path, params, cands, ks):
    """Gauss-Seidel over the clip: every other frame moved at once (each with its neighbours held), then the rest."""
    anchor = {k: params[k].copy() for k in ks}
    for sweep in range(SMOOTH_SWEEPS):
        t0, gain = time.time(), []
        for parity in (0, 1):
            jobs = []
            for i, k in enumerate(ks):
                if i % 2 != parity:
                    continue
                nb = {d: full_J(params[ks[i + d]]) for d in (-2, -1, 1, 2) if 0 <= i + d < len(ks)}
                jobs.append((video, track, net_path, k, params[k], [c['params'] for c in cands.get(k, [])],
                             anchor[k], nb))
            with Pool(int(os.environ.get('PROCS', '4'))) as pool:
                for k, q, a, b in pool.imap_unordered(smooth_job, jobs):
                    params[k] = q
                    gain.append(b - a)
        print(f'smooth sweep {sweep + 1}: value +{np.mean(gain):.4f} per frame, {time.time() - t0:.0f} s', flush=True)


def huber(d):
    """Quadratic up to 1 (stud), linear past it, and no more than a 3-stud move: offset jitter pays, but a limb thrown
    far for real (a kick into the dummy, out and back within two frames) is not priced out of the clip."""
    return min(d * d if d <= 1 else 2 * d - 1, 5.0)


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


THROWN = 12.0  # rig px (2 studs): a limb this far off its joint is thrown, not just loose


def swap(J, b, mate):
    """J with limbs b and mate trading places: each takes the other's turn and moves so its box lands where the
    other's was. Same-sized limbs with the same side colours (R6 arms, R6 legs): the picture is the same."""
    J = J.copy()
    rb, ob = bone_of(J, b)
    rm, om = bone_of(J, mate)
    for x, (r, o, y) in ((b, (rm, om, mate)), (mate, (rb, ob, b))):
        R = euler_to_R(r)
        there = np.array(F.REST[y], float) + o + R.apply(box_mid(y))
        J[COLS[x]] = r
        J[OFFC[x]:OFFC[x] + 3] = there - np.array(F.REST[x], float) - R.apply(box_mid(x))
    return J


def consistent_throws(params, ks):
    """The footage can't say which of two same-sized limbs was thrown (the hunt may pick the left leg in one frame
    and the right in the next): through a throw, the same bone stays the thrown one, so the clip shows one leg kicking
    out and back instead of two legs taking turns."""
    n = 0
    for b, mate in (('rightArm', 'leftArm'), ('rightThigh', 'leftThigh')):
        prev = None
        for k in ks:
            J = full_J(params[k])
            far = {x: np.linalg.norm(bone_of(J, x)[1]) > THROWN for x in (b, mate)}
            now = b if far[b] and not far[mate] else mate if far[mate] and not far[b] else None
            if now is not None and prev is not None and now != prev:
                params[k] = np.concatenate([params[k][:NC], swap(J, b, mate)])
                now, n = prev, n + 1
            prev = now
    return n


RETHROW_BONUS = 0.02  # agreement credited per neighbouring frame that throws the same limb (see rethrow)


def rethrow_job(job):
    """Frame k throws limb x where a neighbour throws limb b instead: b tried in x's place (turned every way to fit
    there, offsets refined) with x back in the neighbour's pose for it. Returns the better by score plus
    RETHROW_BONUS per neighbour that throws the same limb."""
    video, track, net_path, k, p, cps, props = job
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    p = np.asarray(p, float)
    ps = [p[:F.NP]] + [np.asarray(c, float)[:F.NP] for c in cps] + OF.widened(p)
    judge = PF.Judge(clip, net, k, ps, W)
    J0 = full_J(p)
    hid = hidden_weights(judge, J0)
    value = lambda J: (float(judge(torch.tensor(J[None], dtype=T.DT))[0][0]) - OF.COST_PX * float(np.abs(J[NJ:]).sum())
                       - float(pose_prior(J, hid)[0]))
    best, bestJ, base_n = None, None, None
    for b, x, xpose, n_b, n_x in props:
        if best is None:
            best, bestJ, base_n = value(J0) + RETHROW_BONUS * n_x, J0, n_x
        lo, hi, part = OF.box_of(x)
        at = OF.box_centre(np.concatenate([p[:NC], J0]), x, lo, hi, part)
        q = J0.copy()
        q[COLS[x]] = xpose[0]
        q[OFFC[x]:OFFC[x] + 3] = xpose[1]
        Jt = OF.place_search(judge, torch.tensor(q, dtype=T.DT), np.concatenate([p[:NC], q]), b, at)
        Jt, _ = OF.descend(judge, Jt)
        Jn = Jt.numpy().astype(float)
        v = value(Jn) + RETHROW_BONUS * n_b
        if v > best + 1e-6:
            best, bestJ = v, Jn
    return k, np.concatenate([p[:NC], bestJ]), bestJ is not J0


def rethrow(video, track, net_path, params, cands, ks):
    """Arms and legs are boxes of one size whose colours smoke and light make alike, so a frame can pick the wrong one
    to throw by a hair (an arm into the dummy between two frames that kick it with a leg). Here each frame whose
    thrown limb differs from its neighbours' gets their choice tried, and keeps it unless the footage clearly says
    otherwise: the clip throws one limb out and back, not three in turn."""
    far = {k: {b for b in LIMBS if np.linalg.norm(bone_of(full_J(params[k]), b)[1]) > THROWN} for k in ks}
    jobs = []
    for i, k in enumerate(ks):
        nbs = [ks[j] for j in (i - 1, i + 1) if 0 <= j < len(ks)]
        props = []
        for x in far[k]:
            for b in LIMBS:
                if b == x or b in far[k] or not any(b in far[m] for m in nbs):
                    continue
                m = next(m for m in nbs if b in far[m])
                xpose = bone_of(full_J(params[m]), x)
                if np.linalg.norm(xpose[1]) > THROWN:
                    continue
                props.append((b, x, xpose, sum(b in far[m] for m in nbs), sum(x in far[m] for m in nbs)))
        if props:
            jobs.append((video, track, net_path, k, params[k], [c['params'] for c in cands.get(k, [])], props))
    n = 0
    if jobs:
        with Pool(int(os.environ.get('PROCS', '4'))) as pool:
            for k, q, changed in pool.imap_unordered(rethrow_job, jobs):
                params[k] = q
                n += bool(changed)
    print(f'{n} of {len(jobs)} frames threw their neighbours\' limb instead', flush=True)
    return n


def main(video, path_json, cands_json, net_path, out, rounds=2):
    tr = json.load(open(path_json))
    cd = json.load(open(cands_json))
    track = cd['track']
    ks = sorted(int(k) for k in tr['frames'])
    params = {k: np.array(tr['frames'][str(k)]['params'], float) for k in ks}
    cands = {int(k): v for k, v in cd['frames'].items()}
    print(f'{consistent_throws(params, ks)} frames had the thrown limb swapped to its neighbours\'', flush=True)
    if os.environ.get('R6_RETHROW', '1') != '0':
        rethrow(video, track, net_path, params, cands, ks)
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
    if SMOOTH_SWEEPS > 0:
        smooth_clip(video, track, net_path, params, cands, ks)
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
