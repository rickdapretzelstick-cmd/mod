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


SEEN_MIN = 0.25   # a moved limb must show this share of the torso's judged px (the window shrinks the picture when
# it widens to take in a limb thrown far, so a fixed px count would undo exactly those)...
KEEP_GAIN = 0.03  # ...or undoing it costs this much colour + W x parts
ACC_MIN = 0.6     # ...of which this share agree in colour, or its offset is undone: a limb that only "fits" by
# leaving the picture (into the window's unjudged border) or by hiding is not evidence of a gap
KEEP_SURE = 0.06  # ...unless undoing it costs this much: a limb that only hides or leaves the picture gains next to
# nothing, so a gain this big is the footage showing it there (a leg thrown into a dummy, its box not yet turned
# to fit, matches only half its pixels)


def seen_parts(judge, J):
    """Per OFF_BONES bone: (judged drawn px, share agreeing) for joints J in the judge's window."""
    v, fit = judge.v, judge.fit
    Jt, C = J[None], judge.cam
    parts = T.hard_draw(v, 0, Jt, C, fit.persp[0], fit.lights[0], judge.pid)[0]
    drawn = T.hard_draw(v, 0, Jt, C, fit.persp[0], fit.lights[0], fit.classes)
    o, known, drawn = T._hard_counts(v, 0, drawn)
    drawn = drawn[0]
    out = {}
    for b in F.OFF_BONES + ['hips']:
        boxes = [n + 1 for n, (bone, _, _, _) in enumerate(F.PARTS) if bone == b]
        m = np.isin(parts, boxes) & known
        n = int(m.sum())
        out[b] = (n, float((m & (drawn == o)).sum()) / max(n, 1))
    return out


def undo_unseen(judge, J):
    """Offsets of bones that moved but are not seen well where they went: back to zero."""
    seen = seen_parts(judge, J)
    need = SEEN_MIN * max(seen['hips'][0], 120)
    for i, b in enumerate(F.OFF_BONES):
        c = NJ + 3 * i
        if float(J[c:c + 3].norm()) > 1.5 and (seen[b][0] < need or seen[b][1] < ACC_MIN):
            q = J.clone()
            q[c:c + 3] = 0
            # ...unless the footage plainly wants it there: a well-matched limb, partly hidden, that undoing costs a lot
            gain = float(judge(J[None])[0][0] - judge(q[None])[0][0])
            if gain >= KEEP_SURE or (seen[b][1] >= ACC_MIN and gain >= KEEP_GAIN):
                continue
            J = q
    return J


HUNT_REACH = float(os.environ.get('HUNT_REACH', '1.5'))  # how far from the drawn rig to look, in body heights
HUNT_BLOBS = 4  # the largest stray patches tried (scenery in the avatar's colours, a sign or a dummy, can take some)
HUNT_MIN = 0.02     # a stray patch must be at least this share of the rig's drawn area


def hunt(clip, k, p):
    """A limb thrown far from the body (a kick into the dummy, a limb flung off by the animation) is past any grid
    around the joint. Here the footage itself says where to look: patches of the avatar's colours that the fitted rig
    does not cover, within HUNT_REACH body heights of it. Each patch gives, for every offset bone, the pose with that
    bone moved onto it (in the picture plane): starting points the judge then turns and refines."""
    import cv2
    rgb = clip.rgb(k)
    enc = np.asarray(F.classify(rgb, O.pal)) // 1000  # class * 1000 + brightness
    names = [c['name'] for c in O.pal['classes']]
    ids = [i for i, n in enumerate(names, 1) if n not in ('unseen', 'face')]
    avatar = np.isin(enc, ids).astype(np.uint8)
    cover = np.zeros(enc.shape, np.uint8)
    centre = {}
    for (bone, lo, hi, part), (_, poly, _) in zip(F.PARTS, sorted_boxes(p)):
        cv2.fillPoly(cover, [np.round(np.array(poly)).astype(np.int32)], 1)
    for bone, lo, hi, part in F.PARTS:
        if bone in F.OFF_BONES and part != 'face':
            centre.setdefault(bone, box_centre(p, bone, lo, hi, part))
    area = int(cover.sum())
    if area < 50:
        return []
    ys, xs = np.nonzero(cover)
    h = ys.max() - ys.min() + 1
    pad = int(HUNT_REACH * h)
    y0, y1 = max(0, ys.min() - pad), min(enc.shape[0], ys.max() + pad)
    x0, x1 = max(0, xs.min() - pad), min(enc.shape[1], xs.max() + pad)
    r = max(2, int(0.03 * h))
    near = cv2.dilate(cover, np.ones((2 * r + 1, 2 * r + 1), np.uint8))
    stray = (avatar & (1 - near))[y0:y1, x0:x1]
    stray = cv2.morphologyEx(stray, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    n, lab, stats, cents = cv2.connectedComponentsWithStats(stray)
    blobs = sorted((stats[i, cv2.CC_STAT_AREA], cents[i] + (x0, y0)) for i in range(1, n)
                   if stats[i, cv2.CC_STAT_AREA] >= HUNT_MIN * area)[::-1][:HUNT_BLOBS]
    if not blobs:
        return []
    right, up = plane_dirs(p)
    s = float(p[2])
    out = []
    for _, (u, v) in blobs:
        for i, b in enumerate(F.OFF_BONES):
            du, dv = u - centre[b][0], v - centre[b][1]
            q = np.concatenate([p[:F.NP], np.zeros(F.NOFF)])
            q[F.NP:F.NP + len(p) - F.NP] = p[F.NP:]
            q[F.NP + 3 * i:F.NP + 3 * i + 3] += (du / s) * right - (dv / s) * up
            out.append((q, b, (float(u), float(v))))
    return out


PARTNER = {'rightArm': 'leftArm', 'leftArm': 'rightArm', 'rightThigh': 'leftThigh', 'leftThigh': 'rightThigh'}


def box_of(bone):
    return next((lo, hi, part) for b, lo, hi, part in F.PARTS if b == bone and part != 'face')


def centre_on(q, cam, bone, at, right, up):
    """q (joints) with bone's offset shifted so its box's middle projects onto picture point at."""
    c = NJ + 3 * F.OFF_BONES.index(bone)
    u, v = box_centre(np.concatenate([cam, q.numpy()]), bone, *box_of(bone))
    q = q.clone()
    q[c:c + 3] += torch.tensor((at[0] - u) / float(cam[2]) * right - (at[1] - v) / float(cam[2]) * up, dtype=T.DT)
    return q


def place_search(judge, J, p, bone, at):
    """Bone `bone` turned through a grid of directions (30 deg, two twists), its offset each time set so the box's
    middle lands on picture point `at`: a thrown limb is found whatever way it points (turning about the joint alone
    would swing it off the patch). Each also with the partner limb (other arm / other leg) taking the bone's old
    place: the footage cannot say which of two same-sized limbs was thrown, only the judge can."""
    cam = p[:NC]
    cols = lambda b: [F.JI[(b, a)] - NC for a in range(3)]
    right, up = plane_dirs(p)
    bases = [J]
    if bone in PARTNER:
        mate = PARTNER[bone]
        q = J.clone()
        q[cols(mate)] = J[cols(bone)]
        was = box_centre(np.concatenate([cam, J.numpy()]), bone, *box_of(bone))
        bases.append(centre_on(q, cam, mate, was, right, up))
    cands = []
    for base in bases:
        for x in range(-180, 180, 30):
            for z in range(-90, 91, 30):
                for tw in (0, 90, 180, 270):  # all four: a limb's sides are coloured differently
                    q = base.clone()
                    q[cols(bone)] = torch.tensor([float(x), float(tw), float(z)], dtype=T.DT)
                    cands.append(centre_on(q, cam, bone, at, right, up))
    Js = torch.stack(cands)
    sc = judge(Js)[0]
    o = int(np.argmax(sc))
    J, best = Js[o].clone(), float(sc[o])
    # Then finer turns in place (the box kept where it is), so the grid's 30 deg doesn't decide what fits.
    for step in (15.0, 8.0, 4.0):
        for _ in range(6):
            here = box_centre(np.concatenate([cam, J.numpy()]), bone, *box_of(bone))
            cands = []
            for c in cols(bone):
                for d in (-step, step):
                    q = J.clone()
                    q[c] += d
                    cands.append(centre_on(q, cam, bone, here, right, up))
            Js = torch.stack(cands)
            sc = judge(Js)[0]
            o = int(np.argmax(sc))
            if sc[o] <= best + 1e-6:
                break
            J, best = Js[o].clone(), float(sc[o])
    return J


def sorted_boxes(p):
    """F.boxes' projected outlines, in F.PARTS order (F.boxes sorts them far to near)."""
    world = F.pose_world(p)
    view, project = F.camera_of(p)
    out = []
    for bone, lo, hi, part in F.PARTS:
        lo, hi = F.scaled_box(part, lo, hi)
        v = view @ F.corners_of(world, bone, lo, hi)
        out.append((0, F.hull(project(v)), part))
    return out


def box_centre(p, bone, lo, hi, part):
    world = F.pose_world(p)
    view, project = F.camera_of(p)
    lo, hi = F.scaled_box(part, lo, hi)
    v = view @ F.corners_of(world, bone, lo, hi)
    return project(v).mean(0)


def in_plane(J, p):
    """J with every joint offset moved into the picture plane: the footage shows a joint's slide across the picture,
    never its slide toward or away from the camera, so that part of an offset is nothing the footage said (left in,
    a head drifts off the neck along the line of sight with no change in the picture). Attached otherwise."""
    right, up = plane_dirs(p)
    depth = np.cross(right, up)
    depth = torch.tensor(depth / np.linalg.norm(depth), dtype=T.DT)
    J = J.clone()
    for i in range(len(F.OFF_BONES)):
        c = NJ + 3 * i
        J[c:c + 3] = J[c:c + 3] - (J[c:c + 3] @ depth) * depth
    return J


def frame_job(job):
    video, track, net_path, k, p, cands, w = job
    t0 = time.time()
    clip = O.Clip(video, json.load(open(track)))
    net = P.load(net_path)
    p = np.array(p, float)
    found = hunt(clip, k, p) if os.environ.get('R6_HUNT', '1') != '0' else []
    ps = [p[:F.NP]] + [np.array(c['params'], float)[:F.NP] for c in cands] + widened(p) + [q for q, _, _ in found]
    judge = PF.Judge(clip, net, k, ps, w)
    J0 = torch.tensor(np.concatenate([p[NC:F.NP], np.zeros(F.NOFF)]), dtype=T.DT)
    _, c0, p0 = judge(J0[None])
    J = plane_search(judge, J0, p)
    if found:
        # The stray patches' poses: the best few turned (limbs re-swept) and refined, kept when they beat the grid's.
        best = float(judge(J[None])[0][0])
        for (q, bone, at), base in [(f, b) for f in found for b in (J0, J)]:
            Ji = place_search(judge, base, np.concatenate([p[:NC], base.numpy()]), bone, at)
            Ji, v = descend(judge, Ji)
            if v > best + 0.003:
                J, best = Ji, v
    J, _ = descend(judge, J)
    J = undo_unseen(judge, J)
    J, _ = PF.sweep(judge, J, rounds=1)
    J, _ = descend(judge, J)
    J = undo_unseen(judge, J)
    J = in_plane(J, np.concatenate([p[:NC], J.numpy()]))
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
