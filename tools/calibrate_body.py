#!/usr/bin/env python3
"""Measures the avatar's body proportions from a calibration recording (the character standing still, the camera
going round it) and stores them in the palette, so every later fit draws boxes the size of the real parts.

  python3 tools/calibrate_body.py <video> --palette tools/palettes/side_colours.json --times 0,1.5,3.5,4.5,5.5,7,9,13

Each listed moment is fitted on its own (side mode; standing still comes out as the plain standing pose), then the
boxes' sizes per body group (torso, head, arms, legs; x, y, z each) are fitted together with every frame's camera,
the pose held, each size kept near the standard R6 one unless the footage says otherwise (0.75-1.35x). Prints the
colour agreement per frame before and after, and writes 'proportions' into the palette (--dry-run: print only).
"""
import argparse
import json
import math
import os
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F  # noqa: E402
import r6_fit_torch as T  # noqa: E402

GROUPS = ['torso', 'head', 'arms', 'legs']
PRIOR = torch.tensor([2.0, 0.3, 2.0, 2.0], dtype=torch.float64)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('video')
    ap.add_argument('--palette', required=True)
    ap.add_argument('--times', default='0,1.5,3.5,4.5,5.5,7,9,13')
    ap.add_argument('--steps', type=int, default=150)
    ap.add_argument('--dry-run', action='store_true')
    a = ap.parse_args()
    pal = json.load(open(a.palette))
    pal.pop('proportions', None)  # measured from the standard sizes
    tmp = a.palette + '.tmp.json'
    json.dump(pal, open(tmp, 'w'))
    F.use_palette(pal)
    times = [float(t) for t in a.times.split(',')]
    import cv2
    cap = cv2.VideoCapture(a.video)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30
    frames = []
    for t in times:
        cap.set(cv2.CAP_PROP_POS_FRAMES, int(round(t * fps)))
        ok, f = cap.read()
        frames.append(f[..., ::-1].copy())
    # Each moment on its own (the pose and the camera at standard sizes).
    params = []
    for n, f in enumerate(frames):
        args = argparse.Namespace(palette=tmp, res=112, at=None, flip=False, quick_start=False)
        fit1, f0, _, ag = T.run_keyframes([f], [0], fps, args, log=lambda *x: None)
        params.append(fit1.params_of(0, 0))
        print(f'{times[n]:5.1f} s: fitted alone, colour agreement {ag[0]:.3f}', flush=True)
    os.remove(tmp)
    F.use_palette(pal)
    idx = list(range(len(frames)))
    encs = {i: F.classify(frames[i], pal) for i in idx}
    view = T.View(frames, encs, idx, fps, 112)
    view.lite = True
    P = np.stack(params)
    fit = T.Fit([view], P[:, T.NC:], [P[:, :5]], [float(P[0, 5])], [F.DEFAULT_LIGHT])
    f0 = F.Fitter(112)
    group_of = [GROUPS.index(F.GROUP_OF.get(part, part)) for _, _, _, part in F.PARTS]
    logs = torch.zeros(len(GROUPS), 3, dtype=T.DT, requires_grad=True)

    def refresh():
        for k, i in enumerate(idx):
            view.window(i, fit.params_of(0, k), f0)

    def score():
        return [float(T.agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * T.CAM_UNIT).detach())[0])
                for k, i in enumerate(idx)]

    refresh()
    before = score()
    opt = torch.optim.Adam([{'params': [logs], 'lr': 0.01}, {'params': [fit.cams[0]], 'lr': 0.05}])
    best = (float(np.mean(before)), logs.detach().clone(), fit.cams[0].detach().clone())
    for st in range(a.steps):
        sig = 1.0 * (0.4 / 1.0) ** (st / max(1, a.steps - 1))
        T.SCALE_OVERRIDE = torch.exp(logs.clamp(math.log(0.75), math.log(1.35)))[group_of]
        opt.zero_grad()
        loss = sum(fit.loss_frames(0, [k], sig, stride=2 if st < a.steps - 20 else 1)[0] for k in idx)
        # Sizes stay standard unless the footage says otherwise; the head's held more loosely (only back and side views
        # measure it: from the front it runs into the shirt's same red).
        loss = loss + ((logs ** 2).sum(1) * PRIOR).sum()
        loss.backward()
        opt.step()
        if st % 25 == 24 or st == a.steps - 1:
            T.SCALE_OVERRIDE = torch.exp(logs.detach().clamp(math.log(0.75), math.log(1.35)))[group_of]
            refresh()
            m = float(np.mean(score()))
            if m > best[0]:
                best = (m, logs.detach().clone(), fit.cams[0].detach().clone())
    scales = torch.exp(best[1].clamp(math.log(0.75), math.log(1.35)))
    T.SCALE_OVERRIDE = scales[group_of]
    with torch.no_grad():
        fit.cams[0].copy_(best[2])
    refresh()
    after = score()
    T.SCALE_OVERRIDE = None
    for n, t in enumerate(times):
        print(f'{t:5.1f} s: colour agreement {before[n]:.3f} -> {after[n]:.3f}')
    print(f'mean {np.mean(before):.3f} -> {np.mean(after):.3f}')
    prop = {g: [round(float(x), 3) for x in scales[n]] for n, g in enumerate(GROUPS)}
    print('proportions (x, y, z against standard R6):', json.dumps(prop))
    if not a.dry_run and np.mean(after) > np.mean(before):
        pal['proportions'] = prop
        json.dump(pal, open(a.palette, 'w'), indent=1)
        print(f'written to {a.palette}')


if __name__ == '__main__':
    main()
