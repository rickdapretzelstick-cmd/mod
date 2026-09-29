#!/usr/bin/env python3
"""Builds the Gojo vs Sukuna domain clash cinematic from ClashCinematicClientTest's screenshots.

Usage: python3 tools/make_clash_video.py build/run/clientGameTest/screenshots clash.mp4 [fps]

The recording ran the game slowed down and logged the game time each frame shows (frames.txt). The video is rebuilt at
real speed (20 ticks a second) on an exact frame grid (60 fps by default), with frames blended between the two nearest
captures, a letterbox, captions per section and fades in and out.
"""
import bisect
import glob
import os
import sys

import imageio.v2 as imageio
import numpy as np
from PIL import Image, ImageDraw, ImageFont

BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
CAPTIONS = {
    'MALEVOLENT SHRINE': ('DOMAIN EXPANSION', 'MALEVOLENT SHRINE', (255, 90, 80)),
    'INFINITE VOID': ('DOMAIN EXPANSION', 'INFINITE VOID', (140, 200, 255)),
    'DOMAIN CLASH': ('DOMAIN CLASH', '', (255, 225, 140)),
}
CAPTION_TICKS = 40


def main():
    src, out = sys.argv[1], sys.argv[2]
    fps = int(sys.argv[3]) if len(sys.argv) > 3 else 60
    names = {}
    for f in glob.glob(os.path.join(src, '*.png')):
        names[os.path.basename(f).split('_', 1)[-1][:-4]] = f
    rows = []
    for line in open(os.path.join(src, 'frames.txt')):
        parts = line.split(' ', 2)
        if len(parts) < 3 or parts[0] not in names:
            continue
        rows.append((float(parts[1]), names[parts[0]], parts[2].strip()))
    rows.sort()
    # Drop repeats of the same moment (keep the first capture of each).
    uniq = []
    for r in rows:
        if not uniq or r[0] - uniq[-1][0] > 1e-3:
            uniq.append(r)
    times = [r[0] for r in uniq]
    t0, t1 = times[0], times[-1]
    per_tick = len(uniq) / max(1e-6, t1 - t0)
    print('%d frames over %.1f ticks (%.2f captures per tick = %.0f fps source)' % (len(uniq), t1 - t0, per_tick, per_tick * 20))

    first = {}
    for t, _, sec in uniq:
        first.setdefault(sec, t)

    cache = {}

    def img(i):
        if i not in cache:
            if len(cache) > 8:
                cache.clear()
            cache[i] = np.asarray(Image.open(uniq[i][1]).convert('RGB'), dtype=np.float32)
        return cache[i]

    h0, w0 = img(0).shape[:2]
    w, h = w0 - w0 % 2, h0 - h0 % 2
    bar = int(h * 0.09)
    big, small = ImageFont.truetype(BOLD, int(h * 0.075)), ImageFont.truetype(BOLD, int(h * 0.04))
    writer = imageio.get_writer(out, fps=fps, codec='libx264', quality=9, macro_block_size=2, pixelformat='yuv420p')
    step = 20.0 / fps
    n = int((t1 - t0) / step)
    for k in range(n):
        t = t0 + k * step
        j = bisect.bisect_right(times, t) - 1
        j = max(0, min(j, len(uniq) - 2))
        a, b = times[j], times[j + 1]
        f = 0 if b - a > 1.5 else min(1, max(0, (t - a) / max(1e-6, b - a)))
        frame = img(j) if f < 0.02 else img(j) * (1 - f) + img(j + 1) * f
        im = Image.fromarray(frame[:h, :w].clip(0, 255).astype(np.uint8))
        d = ImageDraw.Draw(im)
        sec = uniq[j][2]
        if sec != 'DOMAIN CLASH':
            d.rectangle((0, 0, w, bar), fill=(0, 0, 0))
            d.rectangle((0, h - bar, w, h), fill=(0, 0, 0))
        if sec in CAPTIONS and t - first[sec] < CAPTION_TICKS:
            top, sub, col = CAPTIONS[sec]
            age = t - first[sec]
            alpha = min(1, age / 4, (CAPTION_TICKS - age) / 6)
            c = tuple(int(v * alpha) for v in col)
            y = int(h * 0.72)
            d.text((w // 2, y), top, font=small, fill=(int(255 * alpha),) * 3, anchor='mm', stroke_width=3, stroke_fill=(0, 0, 0))
            if sub:
                d.text((w // 2, y + int(h * 0.075)), sub, font=big, fill=c, anchor='mm', stroke_width=4, stroke_fill=(0, 0, 0))
        arr = np.asarray(im, dtype=np.float32)
        fade = min(1, k / (fps * 0.6), (n - k) / (fps * 0.8))
        writer.append_data((arr * fade).astype(np.uint8))
    writer.close()
    print('wrote %s: %d frames, %.1f s at %d fps' % (out, n, n / fps, fps))


if __name__ == '__main__':
    main()
