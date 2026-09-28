"""Builds a YouTube Short (1080x1920, 30 fps, under 60 s) from ShortClientTest's portrait screenshots.

Usage: python3 tools/make_short.py build/run/clientGameTest/screenshots short.mp4

Real speed (20 game ticks per second) with the unfilmed resets cut out; each segment gets a bold caption that pops in,
cuts flash white, every shot slowly pushes in, and a hook line opens the video.
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


def main():
    shots, out = sys.argv[1], sys.argv[2]
    rows = []
    with open(os.path.join(shots, 'frames.txt')) as fh:
        for line in fh:
            p = line.rstrip('\n').split(' ', 2)
            rows.append((float(p[1]), p[0], p[2] if len(p) > 2 else ''))
    files = {os.path.basename(p).split('_', 1)[1][:-4]: p for p in glob.glob(os.path.join(shots, '*_p*.png'))}
    # Real-speed timeline with the resets between segments closed up.
    timeline, offset, prev = [], 0.0, None
    for t, name, sec in rows:
        if prev is not None and t - prev > 3:
            offset += (t - prev) - 1
        timeline.append((t - offset, name, sec))
        prev = t
    times = np.array([t for t, _, _ in timeline])
    seg_start, seg_end = {}, {}
    for t, _, sec in timeline:
        seg_start.setdefault(sec, t)
        seg_end[sec] = t
    start, end = timeline[0][0], timeline[-1][0]
    n = int((end - start) / 20 * FPS)

    writer = imageio.get_writer(out, fps=FPS, codec='libx264', quality=9, macro_block_size=2, pixelformat='yuv420p')
    cache_name, base = None, None
    for i in range(n):
        t = start + i * 20 / FPS
        k = int(np.argmin(np.abs(times - t)))
        _, name, sec = timeline[k]
        if name != cache_name:
            base = Image.open(files[name]).convert('RGB').resize((W, H), Image.LANCZOS)
            cache_name = name
        age = t - seg_start.get(sec, t)  # ticks into this segment
        seg_len = max(1.0, seg_end[sec] - seg_start[sec])
        # A slow push-in across each shot.
        z = 1 + 0.05 * min(1.0, age / seg_len)
        cw, ch = int(W / z), int(H / z)
        frame = base.crop(((W - cw) // 2, (H - ch) // 2, (W - cw) // 2 + cw, (H - ch) // 2 + ch)).resize((W, H), Image.BILINEAR)
        d = ImageDraw.Draw(frame)
        # White flash on each cut.
        if age < 3 and i > 0:
            a = int(200 * (1 - age / 3))
            frame = Image.blend(frame, Image.new('RGB', (W, H), (255, 255, 255)), a / 255)
            d = ImageDraw.Draw(frame)
        cap = CAPTIONS.get(sec)
        hooking = t - start < 30
        if cap and cap[0] and not hooking and (cap[4] is None or age < cap[4]):
            title, sub, color, y, _ = cap
            pop = 1.0 + 0.35 * max(0.0, 1 - age / 4)
            f = fit(d, title, int(104 * pop), W - 80)
            cy = int(H * y)
            outlined(d, (W // 2, cy), title, f, color, stroke=8)
            if sub:
                outlined(d, (W // 2, cy + 90), sub, fit(d, sub, 48, W - 100), (255, 255, 255), stroke=5)
        # The hook over the opening shot.
        if hooking:
            outlined(d, (W // 2, int(H * 0.2)), HOOK[0], font(96), (255, 255, 255), stroke=8)
            outlined(d, (W // 2, int(H * 0.2) + 110), HOOK[1], font(96), (120, 190, 255), stroke=8)
        writer.append_data(np.asarray(frame))
    writer.close()
    print('wrote', out, f'{n / FPS:.1f} s')


if __name__ == '__main__':
    main()
