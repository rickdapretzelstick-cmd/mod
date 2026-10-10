"""The Cursed Rifle's scope overlay (textures/gui/rifle_scope.png), 512x512.

    python3 tools/gen_rifle_scope.py

A precision optic first, a cursed weapon second: the centre is perfectly clear glass; toward the edge the lens darkens
with a faint teal tint and a thin chromatic fringe; the tube is dark machined metal with etched range ticks, four small
crimson sigils at the cardinal points and a faint violet inner glow; a very soft glint sits upper left. Outside the
tube is opaque black. The reticle itself is drawn in code (RifleScope), so it sits exactly on the aim.
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'textures', 'gui', 'rifle_scope.png')
N = 512
C = (N - 1) / 2
R = N / 2  # the tube's outer edge
LENS = 0.86 * R  # where the glass ends and the tube begins


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


img = Image.new('RGBA', (N, N))
px = img.load()
for y in range(N):
    for x in range(N):
        dx, dy = x - C, y - C
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        if r >= R - 0.5:
            px[x, y] = (4, 3, 6, 255)
            continue
        if r >= LENS:
            # The tube: dark metal, a bevel catching a little light on its inner lip, etched ticks, a violet glow.
            k = (r - LENS) / (R - LENS)
            base = 14 + int(10 * (1 - k))
            col = [base, base, base + 3]
            lip = max(0.0, 1 - (r - LENS) / 5.0)
            col = [c + int(40 * lip) for c in col]
            glow = max(0.0, 1 - (r - LENS) / 14.0) * 0.6
            col[0] += int(28 * glow)
            col[2] += int(46 * glow)
            # Range ticks: 72 round the tube, every 6th longer.
            deg = (math.degrees(a) + 360) % 360
            tick = abs(((deg + 2.5) % 5) - 2.5) < 0.35
            major = abs(((deg + 15) % 30) - 15) < 0.45
            if (tick and LENS + 7 < r < LENS + 15) or (major and LENS + 5 < r < LENS + 24):
                col = [96, 92, 110]
            # Four sigils at the cardinal points: a small crimson diamond with a dot.
            for ca in (0, 90, 180, 270):
                sx = C + math.cos(math.radians(ca)) * (LENS + 21)
                sy = C + math.sin(math.radians(ca)) * (LENS + 21)
                d = abs(x - sx) + abs(y - sy)
                if 5.5 < d < 8:
                    col = [150, 22, 44]
                elif d < 2:
                    col = [210, 40, 70]
            px[x, y] = (min(255, col[0]), min(255, col[1]), min(255, col[2]), 255)
            continue
        # The glass: clear at the centre, darkening and tinting toward the tube.
        v = smooth(0.52 * LENS, LENS, r)
        alpha = int(205 * v ** 1.6)
        col = [6, 18, 24]
        # A thin chromatic fringe just inside the tube (teal outside, magenta a hair further in).
        fringe_t = math.exp(-((r - (LENS - 5)) / 1.8) ** 2)
        fringe_m = math.exp(-((r - (LENS - 9)) / 1.8) ** 2)
        col = [int(col[0] + 40 * fringe_m), int(col[1] + 70 * fringe_t), int(col[2] + 80 * fringe_t + 40 * fringe_m)]
        alpha = max(alpha, int(90 * fringe_t), int(60 * fringe_m))
        # A soft glint, upper left.
        ga = math.atan2(dy, dx)
        glint = math.exp(-((r - 0.72 * LENS) / 9.0) ** 2) * math.exp(-((ga + 2.25) / 0.28) ** 2)
        if glint > 0.02:
            col = [int(col[0] + 220 * glint), int(col[1] + 230 * glint), int(col[2] + 240 * glint)]
            alpha = max(alpha, int(24 * glint))
        px[x, y] = (min(255, col[0]), min(255, col[1]), min(255, col[2]), alpha)
img.save(OUT)
print('wrote', OUT)
