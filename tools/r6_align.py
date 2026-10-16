# A second take as a guide for the parts the main take can't see. Two takes of one move from two cameras are two
# performances, not one filmed twice: their timing drifts, so they can't be merged frame by frame. But the body's own
# pose (each limb and the head relative to the hips) is the same move in both, so the takes are lined up in time by
# dynamic time warping on the parts BOTH cameras see in front (each frame's hidden share, r6_limbpass.hidden_weights),
# and then, for each frame of the main take, every part the OTHER camera sees well gets that take's pose at the lined-up
# moment, with the direction that camera looked along (it sees a part turn across its picture, not along its line of
# sight: a side camera sees a leg swing forward, not out to the side). r6_limbpass (R6_GUIDE=this file) then holds a part the main take hides near that pose instead of near rest;
# a part the main take shows is still the main footage's alone, so the timing drift can't touch it.
#   python3 tools/r6_align.py VIDEO MAIN_FINAL.json MAIN_CANDS.json OTHER_FINAL.json OTHER_CANDS.json NET.pt OUT.json
import sys, os, json
import numpy as np, torch
from multiprocessing import Pool
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_oversee as O, r6_partnet as P, r6_partfit as PF, r6_offsets as OF
import r6_limbpass as L

GUIDE_BONES = ('head', 'rightArm', 'leftArm', 'rightThigh', 'leftThigh')  # relative to the hips: camera-free
SEEN_MIN = 0.5    # a part counts as seen in a frame when at most this much of it is hidden
SLOPE = 2         # DTW steps: one take may run at most twice as fast as the other over any stretch


def vis_job(job):
    video, track, net_path, k, p = job
    clip = O.Clip(video, json.load(open(track)))
    judge = PF.Judge(clip, P.load(net_path), k, [np.asarray(p, float)[:F.NP]] + OF.widened(np.asarray(p, float)), L.W)
    hid = L.hidden_weights(judge, L.full_J(p), np.asarray(p, float)[:O.NC])
    return k, {b: 1.0 - hid[b] for b in GUIDE_BONES}


def take(video, final, cands, net_path):
    tr = json.load(open(final))
    cache = final[:-5] + '_seen.json'  # per frame, how much of each part this take's camera sees
    if os.path.exists(cache) and os.path.getmtime(cache) > os.path.getmtime(final):
        ks = sorted(int(k) for k in tr['frames'])
        params = {k: np.array(tr['frames'][str(k)]['params'], float) for k in ks}
        vis = {int(k): v for k, v in json.load(open(cache)).items()}
        rots = {b: L.rot_batch(np.stack([L.full_J(params[k])[L.COLS[b]] for k in ks])) for b in GUIDE_BONES}
        return ks, params, np.array([[vis[k][b] for b in GUIDE_BONES] for k in ks]), rots
    track = json.load(open(cands))['track']
    ks = sorted(int(k) for k in tr['frames'])
    params = {k: np.array(tr['frames'][str(k)]['params'], float) for k in ks}
    with Pool(int(os.environ.get('PROCS', '4'))) as pool:
        vis = dict(pool.imap_unordered(vis_job, [(video, track, net_path, k, params[k]) for k in ks]))
    json.dump({str(k): v for k, v in vis.items()}, open(cache, 'w'))
    rots = {b: L.rot_batch(np.stack([L.full_J(params[k])[L.COLS[b]] for k in ks])) for b in GUIDE_BONES}
    return ks, params, np.array([[vis[k][b] for b in GUIDE_BONES] for k in ks]), rots


def cost_matrix(va, ra, vb, rb):
    """Mean turn (deg) between the two takes' poses over the parts both see, per pair of frames."""
    n, m = len(va), len(vb)
    num, den = np.zeros((n, m)), np.full((n, m), 1e-3)
    for i, b in enumerate(GUIDE_BONES):
        w = np.minimum.outer(va[:, i], vb[:, i])
        qa, qb = ra[b].as_quat(), rb[b].as_quat()
        deg = np.degrees(2 * np.arccos(np.clip(np.abs(qa @ qb.T), 0, 1)))
        num += w * deg
        den += w
    return num / den


def dtw(C):
    """Cheapest monotone path from (0, 0) to (n-1, m-1), steps (1,1), (1,2..SLOPE), (2..SLOPE,1)."""
    n, m = C.shape
    steps = [(1, 1)] + [(1, s) for s in range(2, SLOPE + 1)] + [(s, 1) for s in range(2, SLOPE + 1)]
    D = np.full((n, m), np.inf)
    back = {}
    D[0, 0] = C[0, 0]
    for i in range(n):
        for j in range(m):
            if i == j == 0:
                continue
            for di, dj in steps:
                a, b = i - di, j - dj
                if a >= 0 and b >= 0 and np.isfinite(D[a, b]):
                    # a long step crosses frames it skips: charge it for them, so skipping is never free
                    c = D[a, b] + C[i, j] * max(di, dj)
                    if c < D[i, j]:
                        D[i, j], back[(i, j)] = c, (a, b)
    path, at = [], (n - 1, m - 1)
    while at != (0, 0):
        path.append(at)
        at = back[at]
    path.append((0, 0))
    return path[::-1], float(D[n - 1, m - 1] / (n + m))


def main(video, main_final, main_cands, other_final, other_cands, net_path, out):
    ka, pa, va, ra = take(video, main_final, main_cands, net_path)
    kb, pb, vb, rb = take(video, other_final, other_cands, net_path)
    C = cost_matrix(va, ra, vb, rb)
    path, mean = dtw(C)
    diag = float(np.mean([C[i, round(i * (len(kb) - 1) / (len(ka) - 1))] for i in range(len(ka))]))
    print(f'aligned {len(ka)} x {len(kb)} frames: mean turn on shared parts {mean:.1f} deg (straight in step: {diag:.1f})')
    match = {}
    for (a, b), (i, j) in zip(path, path[1:]):  # a long step's skipped frames go with the frames it spans
        for t in range(1, max(i - a, j - b) + 1):
            match.setdefault(a + round(t * (i - a) / max(i - a, j - b)), []).append(b + round(t * (j - b) / max(i - a, j - b)))
    match.setdefault(0, []).append(0)
    frames = {}
    for i, k in enumerate(ka):
        js = match[i]
        g = {}
        for n, b in enumerate(GUIDE_BONES):
            w = np.array([vb[j, n] for j in js])
            if w.max() < 1 - SEEN_MIN:
                continue
            j = js[int(np.argmax(w))]
            r, u = OF.plane_dirs(pb[kb[j]])  # the other camera's picture plane, in the hips' frame
            v = np.cross(r, u)
            g[b] = {'rot': [float(x) for x in L.full_J(pb[kb[j]])[L.COLS[b]]], 'seen': float(w.max()),
                    'view': [float(x) for x in v / np.linalg.norm(v)],
                    'main_seen': float(va[i, n]), 'other_frame': kb[j]}
        frames[str(k)] = g
    json.dump({'main': main_final, 'other': other_final, 'mean_deg': mean, 'in_step_deg': diag,
               'path': [[ka[i], kb[j]] for i, j in path], 'frames': frames}, open(out, 'w'))
    print('wrote', out)


if __name__ == '__main__':
    a = sys.argv[1:]
    if len(a) != 7:
        sys.exit(__doc__ or 'VIDEO MAIN_FINAL MAIN_CANDS OTHER_FINAL OTHER_CANDS NET OUT')
    main(*a)
