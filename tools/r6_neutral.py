# Standing frames set to the rig's true neutral: where the footage shows the avatar standing still at the start or the
# end of the take (no pixel movement, r6_takes' rule), every joint turn and joint offset is zero and the body keeps
# only which way it faces (the still stretch's mean heading). The moving frames next to them ease in over BLEND frames,
# so nothing snaps. Frames in motion keep their fitted pose.
#   python3 tools/r6_neutral.py VIDEO FINAL.json OUT.json [CANDS.json NET.pt]
# With CANDS and NET the heading is fitted too: the neutral body turned until it agrees best with the footage (the
# judge the limb pass uses) on the stretch's middle frame, since a moving fit's heading drifts a few degrees.
import sys, os, json
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_takes as TK
import r6_fit as F

NC = 6  # camera params before the joints (r6_limbpass.NC)
BLEND = int(os.environ.get('R6_NEUTRAL_BLEND', '4'))
HEADING = F.JI[('hips', 1)]


def still_frames(video, ks):
    """The take's frames (ks, sorted) in the still stretch it starts with and the one it ends with."""
    fr, _ = TK.frames(video)
    d = np.r_[0.0, (np.abs(fr[1:] - fr[:-1]) > TK.GRAY_STEP).mean((1, 2))]
    still = lambda k: 0 <= k < len(d) and d[k] < TK.STILL
    out = set()
    for k in ks:  # from the start forwards (the first frame's own change is from the frame before the take)
        if k != ks[0] and not still(k):
            break
        out.add(k)
    for k in reversed(ks):
        if not still(k):
            break
        out.add(k)
    return out


def fit_heading(video, cands, net, k, p, h0):
    """The heading (deg) under which the neutral body agrees best with frame k (p: its fitted params)."""
    import torch
    import r6_limbpass as L, r6_oversee as O, r6_partnet as P, r6_partfit as PF, r6_offsets as OF, r6_fit_torch as T
    clip = O.Clip(video, json.load(open(json.load(open(cands))['track'])))
    judge = PF.Judge(clip, P.load(net), k, [p[:F.NP]] + OF.widened(p), L.W)

    def score(hs):
        J = np.zeros((len(hs), len(L.full_J(p))))
        J[:, HEADING - NC] = hs
        return np.asarray(judge(torch.tensor(J, dtype=T.DT))[0])
    hs = h0 + np.arange(-30, 31, 2.0)
    best = float(hs[int(np.argmax(score(hs)))])
    hs = best + np.arange(-1.5, 1.6, 0.5)
    return float(hs[int(np.argmax(score(hs)))])


def neutralise(tr, stills, fit=None):
    ks = sorted(int(k) for k in tr['frames'])
    P = {k: np.array(tr['frames'][str(k)]['params'], float) for k in ks}
    runs, cur = [], []
    for k in ks:
        if k in stills:
            cur.append(k)
        elif cur:
            runs.append(cur)
            cur = []
    if cur:
        runs.append(cur)
    target = {}
    for run in runs:
        h = np.degrees(np.angle(np.mean(np.exp(1j * np.radians([P[k][HEADING] for k in run])))))
        if fit:
            mid = run[len(run) // 2]
            h0, h = h, fit(mid, P[mid], h)
            print(f'still frames {run[0]}-{run[-1]}: heading {h0:.1f} -> {h:.1f} (fitted on {mid})', flush=True)
        for k in run:
            q = P[k].copy()
            q[NC:] = 0.0
            q[HEADING] = h
            target[k] = q
    out = {k: P[k].copy() for k in ks}
    for k, q in target.items():
        out[k] = q
    # ease: a moving frame within BLEND frames of a neutral one moves part way towards it
    for i, k in enumerate(ks):
        if k in target:
            continue
        near = [(abs(i - j), ks[j]) for j in range(max(0, i - BLEND), min(len(ks), i + BLEND + 1)) if ks[j] in target]
        if not near:
            continue
        dist, n = min(near)
        w = 1.0 - dist / (BLEND + 1.0)  # 1 next to the neutral frame, 0 past BLEND
        w = w * w * (3 - 2 * w)
        q = P[k].copy()
        t = target[n]
        dh = (t[HEADING] - q[HEADING] + 180) % 360 - 180
        joints = q[NC:] + w * (t[NC:] - q[NC:])
        joints[HEADING - NC] = q[HEADING] + w * dh
        q[NC:] = joints
        out[k] = q
    new = dict(tr)
    new['frames'] = {str(k): dict(tr['frames'][str(k)], params=out[k].tolist()) for k in ks}
    new['neutral'] = sorted(target)
    return new


def main():
    video, final, dst = sys.argv[1:4]
    tr = json.load(open(final))
    ks = sorted(int(k) for k in tr['frames'])
    stills = still_frames(video, ks)
    fit = None
    if len(sys.argv) > 5:
        cands, net = sys.argv[4:6]
        fit = lambda k, p, h0: fit_heading(video, cands, net, k, p, h0)
    new = neutralise(tr, stills, fit)
    json.dump(new, open(dst, 'w'))
    print(f'neutral frames: {new["neutral"]}')
    print('NEUTRAL ' + json.dumps({'frames': len(ks), 'neutral': len(new['neutral'])}))


if __name__ == '__main__':
    main()
