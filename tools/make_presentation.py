"""Builds the captioned presentation video from PresentationClientTest's screenshots.

Usage: python3 tools/make_presentation.py build/run/clientGameTest/screenshots out.mp4

frames.txt lists each screenshot with the game tick it shows and the segment it belongs to. The video plays at real
speed (20 ticks per second): unfilmed stretches between segments are cut, a caption strip under the footage names the
mechanic on screen, and the video opens and closes on title cards.
"""
import glob
import os
import sys

import imageio.v2 as imageio
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FPS = 30
STRIP = 64
BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
REGULAR = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def card(w, h, lines, accent=(120, 180, 255)):
    """A dark title card: big first line, smaller lines under it."""
    im = Image.new('RGB', (w, h), (6, 8, 16))
    d = ImageDraw.Draw(im)
    # A slanted accent band, like the in-game cut-ins.
    d.polygon([(0, h * 0.58), (w, h * 0.46), (w, h * 0.50), (0, h * 0.62)], fill=accent)
    y = h * 0.22
    for i, (text, size, color) in enumerate(lines):
        f = font(BOLD if i == 0 else REGULAR, size)
        tw = d.textlength(text, font=f)
        d.text(((w - tw) / 2, y), text, font=f, fill=color)
        y += size * 1.35 + (14 if i == 0 else 0)
    return np.array(im)


def main():
    shots, out = sys.argv[1], sys.argv[2]
    rows = []
    with open(os.path.join(shots, 'frames.txt')) as f:
        for line in f:
            parts = line.rstrip('\n').split(' ', 2)
            rows.append((float(parts[1]), parts[0], parts[2] if len(parts) > 2 else ''))
    files = {os.path.basename(p).split('_', 1)[1][:-4]: p for p in glob.glob(os.path.join(shots, '*_p*.png'))}
    # Close the unfilmed gaps between segments.
    timeline, offset, prev = [], 0.0, None
    for t, name, sec in rows:
        if prev is not None and t - prev > 3:
            offset += (t - prev) - 1
        timeline.append((t - offset, name, sec))
        prev = t
    times = np.array([t for t, _, _ in timeline])
    sample = Image.open(files[timeline[0][1]])
    w, h = sample.size
    H = h + STRIP
    sections = []
    for _, _, sec in timeline:
        if sec and (not sections or sections[-1] != sec):
            sections.append(sec)
    cap_font, num_font = font(BOLD, 22), font(REGULAR, 15)

    writer = imageio.get_writer(out, fps=FPS, codec='libx264', quality=8, macro_block_size=2, pixelformat='yuv420p')
    title = card(w, H, [('JUJUTSU KAISEN MOD', 46, (255, 255, 255)),
                        ('Gojo Satoru: full kit presentation', 24, (200, 220, 255)),
                        ('Fabric · Minecraft 26.3 · recorded in game with real input', 16, (150, 160, 180))])
    for _ in range(int(FPS * 3.5)):
        writer.append_data(title)

    start, end = timeline[0][0], timeline[-1][0]
    n = int((end - start) / 20 * FPS)
    cache = {}
    for i in range(n):
        t = start + i * 20 / FPS
        k = int(np.argmin(np.abs(times - t)))
        _, name, sec = timeline[k]
        key = (name, sec)
        if key not in cache:
            cache.clear()
            frame = Image.new('RGB', (w, H), (8, 10, 18))
            frame.paste(Image.open(files[name]).convert('RGB'), (0, 0))
            if sec:
                d = ImageDraw.Draw(frame)
                d.rectangle([0, h, w, h + 3], fill=(120, 180, 255))
                idx = sections.index(sec) + 1 if sec in sections else 0
                label = f'{idx:02d}'
                d.text((16, h + 20), label, font=num_font, fill=(120, 180, 255))
                d.text((52, h + 16), sec, font=cap_font, fill=(255, 255, 255))
            cache[key] = np.array(frame)
        writer.append_data(cache[key])

    moves = ['Melee chain · heavy · uppercut · lunge', 'Guard · dash · Infinity', 'Lapse Blue · Reversal Red · Teleport',
             'Awakening → MAX Blue · MAX Red · Hollow Purple', 'Battle damage restores itself',
             'Infinite Void: physical domain, sure-hit, exact restore', 'Domain counter → versus → rhythm clash']
    end_card = card(w, H, [('GOJO SATORU', 42, (255, 255, 255))] + [(m, 17, (200, 215, 240)) for m in moves])
    for _ in range(int(FPS * 4)):
        writer.append_data(end_card)
    writer.close()
    total = n / FPS + 7.5
    print('wrote', out, f'{total:.1f} s', len(sections), 'segments')


if __name__ == '__main__':
    main()
