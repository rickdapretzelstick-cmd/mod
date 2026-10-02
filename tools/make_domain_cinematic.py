#!/usr/bin/env python3
"""Builds the domain cinematic from DomainCinematicClientTest's screenshots.

Usage: python3 tools/make_domain_cinematic.py build/run/clientGameTest/screenshots domains.mp4 [fps]

The recording ran the game frozen, one tick at a time, each tick drawn at several sub-tick moments (frames.txt logs the
game time each frame shows and the section it belongs to). The video is rebuilt on an exact 60 fps grid at real speed
(20 ticks a second), with frames blended between the two nearest captures. A section name is
``NAME;top line;sub line;r,g,b;speed``: the lines are its caption (shown over its first two seconds), the speed slows it
(0.25 = quarter speed, which the recording must have enough captures per tick for). Clashes get no letterbox so nothing is
hidden, and the last section fades to white instead of black (the beam fills the lens).
"""
import bisect
import glob
import os
import sys

import imageio.v2 as imageio
import numpy as np
from PIL import Image, ImageDraw, ImageFont

BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
CAPTION_SECONDS = 2.2


def parse(section):
    p = (section.split(';') + ['', '', '', '', ''])[:5]
    rgb = tuple(int(v) for v in p[3].split(',')) if p[3] else (255, 255, 255)
    return {'name': p[0], 'top': p[1], 'sub': p[2], 'rgb': rgb, 'speed': float(p[4]) if p[4] else 1.0}


def main():
    src, out = sys.argv[1], sys.argv[2]
    fps = int(sys.argv[3]) if len(sys.argv) > 3 else 60
    names = {}
    for f in glob.glob(os.path.join(src, '*.png')) + glob.glob(os.path.join(src, '*.jpg')):
        names[os.path.basename(f).split('_', 1)[-1].rsplit('.', 1)[0]] = f
    rows = []
    for line in open(os.path.join(src, 'frames.txt')):
        parts = line.rstrip('\n').split(' ', 2)
        if len(parts) < 3 or parts[0] not in names:
            continue
        rows.append((float(parts[1]), names[parts[0]], parts[2].strip()))
    # Game time restarts per scene (each is staged afresh): keep the recording order, not the clock.
    uniq = []
    for r in rows:
        if not uniq or r[0] != uniq[-1][0] or r[2] != uniq[-1][2]:
            uniq.append(r)
    secs = [parse(r[2]) for r in uniq]
    print('%d frames' % len(uniq))

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

    # The source index advances by the game time each captured frame shows, scaled by the section's speed; sections
    # are played one after another. Ticks per captured frame inside a section come from its own capture times.
    n_out = 0
    first_out = {}
    total = 0
    i = 0
    scene_starts = []
    while i < len(uniq):
        j = i
        while j + 1 < len(uniq) and secs[j + 1]['name'] == secs[i]['name'] and (uniq[j + 1][0] - uniq[j][0]) < 3 and uniq[j + 1][0] >= uniq[j][0]:
            j += 1
        scene_starts.append((i, j))
        i = j + 1
    last_scene = len(scene_starts) - 1
    # A section can span several scenes' worth of frames only if names repeat; names are unique per scene by design.
    for si, (a, b) in enumerate(scene_starts):
        sec = secs[a]
        t0, t1 = uniq[a][0], uniq[b][0]
        times = [uniq[k][0] for k in range(a, b + 1)]
        step = 20.0 / fps * sec['speed']
        n = max(1, int((t1 - t0) / step))
        for k in range(n):
            t = t0 + k * step
            jj = bisect.bisect_right(times, t) - 1
            jj = max(0, min(jj, len(times) - 2)) if len(times) > 1 else 0
            ia = a + jj
            ib = min(b, ia + 1)
            ta, tb = uniq[ia][0], uniq[ib][0]
            f = 0 if ib == ia or tb - ta > 1.5 else min(1, max(0, (t - ta) / max(1e-6, tb - ta)))
            frame = img(ia) if f < 0.02 else img(ia) * (1 - f) + img(ib) * f
            im = Image.fromarray(frame[:h, :w].clip(0, 255).astype(np.uint8))
            d = ImageDraw.Draw(im)
            age = k / fps
            if not sec['name'].startswith('CLASH_D'):
                d.rectangle((0, 0, w, bar), fill=(0, 0, 0))
                d.rectangle((0, h - bar, w, h), fill=(0, 0, 0))
            if sec['top'] and age < CAPTION_SECONDS:
                alpha = min(1, age / 0.2, (CAPTION_SECONDS - age) / 0.3)
                col = tuple(int(v * alpha) for v in sec['rgb'])
                y = int(h * 0.72)
                d.text((w // 2, y), sec['top'], font=small, fill=(int(255 * alpha),) * 3, anchor='mm', stroke_width=3, stroke_fill=(0, 0, 0))
                if sec['sub']:
                    d.text((w // 2, y + int(h * 0.075)), sec['sub'], font=big, fill=col, anchor='mm', stroke_width=4, stroke_fill=(0, 0, 0))
            arr = np.asarray(im, dtype=np.float32)
            # Fade in at the very start; between scenes a short dip; the last scene whites out.
            if si == 0:
                arr *= min(1, k / (fps * 0.6))
            else:
                arr *= min(1, k / (fps * 0.25))
            if si == last_scene:
                tail = (n - k) / (fps * 1.4)
                if tail < 1:
                    arr = arr + (255 - arr) * (1 - max(0, tail))
            elif n - k < fps * 0.25:
                arr *= (n - k) / (fps * 0.25)
            writer.append_data(arr.clip(0, 255).astype(np.uint8))
            n_out += 1
    writer.close()
    print('wrote %s: %d frames, %.1f s at %d fps' % (out, n_out, n_out / fps, fps))


if __name__ == '__main__':
    main()
