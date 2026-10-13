#!/usr/bin/env python3
"""
Accuracy check for tools/r6_fit.py: renders a known kick-like motion (body lean and turn, an arm swing with a twist, a
leg swinging up to ~110 degrees, a head tilt) as shaded R6 footage through a perspective camera, in a palette's colours,
fits it blind, and prints the mean error of every joint.

    python3 tools/r6_fit_selftest.py [six|noob] [camera_yaw camera_pitch]

Measured (shaded fitter): six colours from the side (250, 12): 7.8 degrees mean over 18 rotations, twists 3-10, head
tilt 2.5; the worst, 34, is the far arm's sideways angle while the torso hides it (record that side too). Silhouette-only
fitter, flat footage: six colours side 6.8, above-behind 9.0 (swings toward the camera foreshorten), noob colours 26.
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F  # noqa: E402


def main():
    palname = sys.argv[1] if len(sys.argv) > 1 else 'six'
    yaw, pitch = (float(sys.argv[2]), float(sys.argv[3])) if len(sys.argv) > 3 else (250, 12)
    palfile = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'r6_palettes', 'six.json')
    pal = json.load(open(palfile)) if palname == 'six' else F.PALETTE
    F.use_palette(pal)
    t = np.linspace(0, 1, 40)
    s = np.sin(np.pi * t)
    truth = np.zeros((len(t), F.NP))
    truth[:, :6] = yaw, pitch, 5, 200, 200, 0.008
    n = len(F.CAM)
    for j, v in {('hips', 0): 25 * s, ('hips', 1): 60 * t, ('head', 0): 10 * s, ('rightArm', 0): -40 - 50 * s,
                 ('rightArm', 2): 30 * s, ('leftArm', 0): 30 * s, ('leftArm', 2): -45 * s, ('rightThigh', 0): -110 * s * s,
                 ('rightThigh', 2): 20 * s, ('leftThigh', 0): 20 * s, ('rightArm', 1): 50 * s, ('leftThigh', 1): -30 * s,
                 ('head', 2): 15 * s}.items():
        truth[:, F.JI[j]] = v
    tmp = tempfile.mkdtemp()
    light = (np.array([0.5, 0.75, 0.43]) / np.linalg.norm([0.5, 0.75, 0.43]), 0.45)
    frames = [F.draw_frame(p, light) for p in truth]
    gif = os.path.join(tmp, 'synth.gif')
    frames[0].save(gif, save_all=True, append_images=frames[1:], duration=20, loop=0)
    cmd = [sys.executable, os.path.abspath(F.__file__), gif, '--out', tmp] + (['--palette', palfile] if palname == 'six' else [])
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)
    d = json.load(open(os.path.join(tmp, 'params.json')))
    P = np.array([d['frames'][str(i)] for i in range(len(t))])
    e = (P[:, n:] - truth[:, n:] + 180) % 360 - 180
    e[:, 1] = (P[:, n + 1] - P[0, n + 1]) - (truth[:, n + 1] - truth[0, n + 1])  # body yaw counts from the start
    print(f'{palname}, camera fitted {P[0, 0]:.0f}/{P[0, 1]:.0f} (true {yaw:.0f}/{pitch:.0f})')
    for name, v in zip(d['params'][n:], np.abs(e).mean(0)):
        print(f'  {name:14s} {v:5.1f}')
    print(f'  mean          {np.abs(e).mean():5.1f} degrees')


if __name__ == '__main__':
    main()
