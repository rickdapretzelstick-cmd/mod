"""Builds a YouTube Short (1080x1920, 30 fps, under 60 s) from ShortClientTest's portrait screenshots.

Usage: python3 tools/make_short.py build/run/clientGameTest/screenshots short.mp4

Real speed (20 game ticks per second), cut to the EDIT list below: a hook line opens the video, each segment gets a
bold caption that pops in, cuts between techniques flash white, technique shots slowly push in, and the domain, the
counter, the clash and the win run as one continuous sequence.
"""
import glob
import os
import sys

import imageio.v2 as imageio
import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, H, FPS = 1080, 1920, 30
BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

# Segment -> (caption, sub-caption, colour, caption height as a share of the screen, ticks to show it for or None).
CAPTIONS = {
    'LAPSE BLUE': ('LAPSE BLUE', 'pulls everything in', (120, 190, 255), 0.2, None),
    'REVERSAL RED': ('REVERSAL RED', 'pushes everything out', (255, 90, 90), 0.2, None),
    'AWAKENING': ('AWAKENING', 'the blindfold comes off', (255, 255, 255), 0.2, None),
    'MAX BLUE': ('MAX BLUE', '', (120, 190, 255), 0.2, None),
    'MAX RED': ('MAX RED', '', (255, 90, 90), 0.2, None),
    'HOLLOW PURPLE': ('HOLLOW PURPLE', '', (200, 120, 255), 0.2, None),
    'DOMAIN EXPANSION': ('', '', (255, 255, 255), 0.2, None),
    'RIVAL DOMAIN': ('THEY OPEN A DOMAIN...', '', (255, 150, 150), 0.62, None),
    'COUNTER': ('...SO I COUNTER', '', (150, 200, 255), 0.62, 10),
    'DOMAIN CLASH': ('DOMAIN CLASH', 'hit the beat to push your domain', (255, 225, 140), 0.66, 50),
    'WINNER': ('INFINITE VOID WINS', '', (255, 255, 255), 0.4, None),
}
HOOK = ('I ADDED GOJO', 'TO MINECRAFT')


def font(size):
    return ImageFont.truetype(BOLD, size)


def outlined(d, xy, text, f, fill, stroke=7, anchor='mm'):
    d.text(xy, text, font=f, fill=fill, stroke_width=stroke, stroke_fill=(0, 0, 0), anchor=anchor)


def fit(d, text, size, max_w):
    f = font(size)
    while d.textlength(text, font=f) > max_w and size > 30:
        size -= 4
        f = font(size)
    return f


# The edit: (segment, first tick, last tick, how it cuts in, push-in?, ticks to hold the last frame). Ticks are offsets
# into each recorded segment. The order builds from the base techniques through Awakening and the MAX moves to Hollow
# Purple, then the domain, and ends on the clash as the payoff. Clips marked None continue the previous clip's footage
# (they were recorded back to back), so they cut without a flash.
EDIT = [
    ('LAPSE BLUE', 8, 28, None, True, 0),        # hook
    ('REVERSAL RED', 8, 56, 'flash', True, 0),
    ('AWAKENING', 7, 50, 'flash', True, 0),
    ('MAX BLUE', 7, 50, 'flash', True, 0),
    ('MAX RED', 9, 70, 'flash', True, 0),
    ('HOLLOW PURPLE', 6, 88, 'flash', True, 0),
    ('DOMAIN EXPANSION', 7, 87, 'black', False, 0),  # the final stage begins
    ('RIVAL DOMAIN', 4, 16, 'flash', False, 0),
    ('COUNTER', 0, 24, None, False, 0),
    ('COUNTER', 36, 70, None, False, 0),         # jump cut through the held versus card
    ('DOMAIN CLASH', 0, 999, None, False, 0),
    ('WINNER', 0, 999, None, False, 12),         # let the win breathe
]


def main():
    shots, out = sys.argv[1], sys.argv[2]
    segs = {}
    with open(os.path.join(shots, 'frames.txt')) as fh:
        for line in fh:
            p = line.rstrip('\n').split(' ', 2)
            segs.setdefault(p[2] if len(p) > 2 else '', []).append((float(p[1]), p[0]))
    files = {os.path.basename(p).split('_', 1)[1][:-4]: p for p in glob.glob(os.path.join(shots, '*_p*.png'))}

    writer = imageio.get_writer(out, fps=FPS, codec='libx264', quality=9, macro_block_size=2, pixelformat='yuv420p')
    cache_name, base = None, None
    total = 0
    for ci, (sec, t_in, t_out, cut, push, hold) in enumerate(EDIT):
        frames = segs[sec]
        s0 = frames[0][0]
        times = np.array([t - s0 for t, _ in frames])
        t_out = min(t_out, times[-1])
        n = int((t_out - t_in) / 20 * FPS) + int(hold / 20 * FPS)
        for j in range(n):
            age = min(t_in + j * 20 / FPS, t_out)  # ticks into the recorded segment
            clip_age = j * 20 / FPS               # ticks into this clip
            name = frames[int(np.argmin(np.abs(times - age)))][1]
            if name != cache_name:
                base = Image.open(files[name]).convert('RGB').resize((W, H), Image.LANCZOS)
                cache_name = name
            frame = base
            if push:
                # A slow push-in across each technique shot (never on the domain, clash or UI shots).
                z = 1 + 0.05 * min(1.0, (age - t_in) / max(1.0, t_out - t_in))
                cw, ch = int(W / z), int(H / z)
                frame = base.crop(((W - cw) // 2, (H - ch) // 2, (W - cw) // 2 + cw, (H - ch) // 2 + ch)).resize((W, H), Image.BILINEAR)
            if cut == 'flash' and clip_age < 3:
                frame = Image.blend(frame, Image.new('RGB', (W, H), (255, 255, 255)), 200 * (1 - clip_age / 3) / 255)
            elif cut == 'black' and clip_age < 6:
                frame = Image.blend(frame, Image.new('RGB', (W, H), (0, 0, 0)), 1 - clip_age / 6)
            else:
                frame = frame.copy()
            d = ImageDraw.Draw(frame)
            hooking = ci == 0
            cap = CAPTIONS.get(sec)
            if cap and cap[0] and not hooking and (cap[4] is None or age < cap[4]):
                title, sub, color, y, _ = cap
                pop_age = clip_age if EDIT[ci - 1][0] != sec else age
                pop = 1.0 + 0.35 * max(0.0, 1 - pop_age / 4)
                f = fit(d, title, int(104 * pop), W - 80)
                cy = int(H * y)
                outlined(d, (W // 2, cy), title, f, color, stroke=8)
                if sub:
                    outlined(d, (W // 2, cy + 90), sub, fit(d, sub, 48, W - 100), (255, 255, 255), stroke=5)
            if hooking:
                outlined(d, (W // 2, int(H * 0.2)), HOOK[0], font(96), (255, 255, 255), stroke=8)
                outlined(d, (W // 2, int(H * 0.2) + 110), HOOK[1], font(96), (120, 190, 255), stroke=8)
            writer.append_data(np.asarray(frame))
        total += n
        print(f'{total / FPS:5.1f}s  {sec}')
    writer.close()
    print('wrote', out, f'{total / FPS:.1f} s')


if __name__ == '__main__':
    main()
