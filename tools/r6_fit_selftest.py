#!/usr/bin/env python3
"""
Accuracy check for tools/r6_fit.py: renders a known kick-like motion (body lean and turn, an arm swing, a leg swinging
up to ~110 degrees) as R6 footage in a palette's colours, fits it blind, and prints the mean error of every joint.

    python3 tools/r6_fit_selftest.py [six|noob] [camera_yaw camera_pitch]

Measured when written: six colours from the side (250, 12): 6.8 degrees mean, camera recovered exactly; six colours from
above and behind (200, 35): 9.0 (swings toward the camera foreshorten: record from the side); noob colours: 26.
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw

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
    truth[:, :5] = yaw, pitch, 5, 200, 200
    n = len(F.CAM)
    for k, v in {0: 25 * s, 1: 60 * t, 3: 10 * s, 5: -40 - 50 * s, 6: 30 * s, 7: 30 * s, 8: -45 * s, 9: -110 * s * s,
                 10: 20 * s, 11: 20 * s}.items():
        truth[:, n + k] = v
    tmp = tempfile.mkdtemp()
    frames = []
    for p in truth:
        img = Image.new('RGB', (400, 400), (45, 45, 48))
        d = ImageDraw.Draw(img)
        for _, poly, cls in F.boxes(p):
            d.polygon(poly, fill=F.SHOW[cls])
        frames.append(img)
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
