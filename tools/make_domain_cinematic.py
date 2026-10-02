#!/usr/bin/env python3
"""Builds the domain cinematic from DomainCinematicClientTest's screenshots.

Usage: python3 tools/make_domain_cinematic.py OUT.mp4 DIR [DIR ...] [--fps 60] [--plain] [--timeline FILE]

Each DIR is one recording run's screenshots folder (with its frames.txt); the runs are joined in the order given. A long
run can run out of memory, so the scenes are recorded in several runs (build/cinematic.txt picks the scenes) and each
run's folder is kept apart, since every run numbers its frames from zero.

The recording ran the game frozen, one tick at a time, each tick drawn at several sub-tick moments (frames.txt logs the
game time each frame shows and the section it belongs to). The video is rebuilt on an exact 60 fps grid at real speed
(20 ticks a second), with frames blended between the two nearest captures. A section name is
``NAME;top line;sub line;r,g,b;speed``: the lines are its caption (shown over its first two seconds), the speed slows it
(0.25 = quarter speed, which the recording must have enough captures per tick for). Clashes get no letterbox so nothing is
hidden, and the last section fades to white instead of black (the beam fills the lens).

Unless --plain, the film is cut as: a short hook of the best moments, a disclaimer card, then every scene in order and a
white hold. EDIT below is that cut. --timeline writes where each part starts and a brightness trace of every frame, which
tools/make_domain_soundtrack.py scores to; the soundtrack is muxed on afterwards, this film is silent.
"""
import argparse
import bisect
import glob
import json
import os
import subprocess
import sys

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONTS = '/usr/share/fonts/truetype/'
BOLD = FONTS + 'dejavu/DejaVuSans-Bold.ttf'
SANS = FONTS + 'liberation/LiberationSans-Regular.ttf'
SANS_BOLD = FONTS + 'liberation/LiberationSans-Bold.ttf'
CAPTION_SECONDS = 2.2

# ---- The cut -------------------------------------------------------------------------------------------------------
# The hook: one clip per interval between HOOK_CUTS (seconds into the hook), each (scene, seconds into that scene). The
# cuts are the bass hits of the hook's music (purple_music), so the soundtrack script scores the very same list.
HOOK_CUTS = [0.0, 0.76, 1.57, 2.44, 3.28, 4.08, 4.93, 5.37, 5.69, 6.13, 6.61, 6.97, 7.88]
HOOK = [
    ('SHOW0', 0.45),          # Gojo, the dome of his domain swelling round him
    ('CLASH_gojo', 1.33),     # the burst of Unlimited Void
    ('SHOW1', 4.22),          # Malevolent Shrine lighting red
    ('SHOW2', 3.85),          # Idle Death Gamble's doors
    ('SHOW3', 4.74),          # Authentic Mutual Love's field of swords
    ('CLASH_gojo', 7.57),     # then the clash, cut faster and faster
    ('CLASH_yuta', 5.02),
    ('CLASH_gojo', 10.07),
    ('CLASH_yuta', 11.17),
    ('CLASH_gojo', 8.44),
    ('CLASH_yuta', 6.85),
    ('FINALE', 3.00),         # True Love Beam coming down the lens, out to white
]
HOOK_TITLE = ('JUJUTSU', 'A JJS MOD FOR MINECRAFT')
TITLE_SECONDS = 1.9

# The disclaimer, verbatim (the one rewording: the pasted "Al" is AI, and the line break in "Thanks for / watching!" is
# gone). Each card is (seconds on screen, [(paragraph, seconds in when it fades up)]); ** marks the bold, tinted lead.
CARDS = [
    (17.0, [('**Disclaimer:** This mod is a passion project, made purely out of love for Minecraft and Jujutsu '
             'Shenanigans (JJS). It\'s heavily inspired by JJS and is essentially a port, using very similar mechanics '
             'and the original audio. All credit for the original ideas, sounds, and designs goes to the JJS team. If '
             'they\'d like anything changed or taken down, I\'ll gladly comply, no questions asked.', 0.9)]),
    (11.0, [('I used every tool available to me to build this, including AI. If that\'s not your thing, I completely '
             'understand. I just really wanted a JJS mod for Minecraft.', 0.9),
            ('##Thanks for watching!##', 6.5)]),
]
END_HOLD_SECONDS, END_FADE_SECONDS = 1.2, 0.8

CARD_BG = (6, 6, 9)
CARD_INK = (226, 226, 234)
CARD_TINT = (150, 176, 255)


def parse(section):
    p = (section.split(';') + ['', '', '', '', ''])[:5]
    rgb = tuple(int(v) for v in p[3].split(',')) if p[3] else (255, 255, 255)
    return {'name': p[0], 'top': p[1], 'sub': p[2], 'rgb': rgb, 'speed': float(p[4]) if p[4] else 1.0}


def group_name(name):
    """CLASH_Agojo..CLASH_Egojo are one clash, FINALE/FIRE/ENGULFED one blast; every other section stands alone."""
    if name.startswith('CLASH_'):
        return 'CLASH_' + name[7:]
    return 'FINALE' if name in ('FINALE', 'FIRE', 'ENGULFED') else name


class Recording:
    """The captured frames of several runs, in recording order, split into scenes (one section name each)."""

    def __init__(self, dirs):
        rows = []
        for src in dirs:
            names = {}
            for f in glob.glob(os.path.join(src, '*.png')) + glob.glob(os.path.join(src, '*.jpg')):
                names[os.path.basename(f).split('_', 1)[-1].rsplit('.', 1)[0]] = f
            for line in open(os.path.join(src, 'frames.txt')):
                parts = line.rstrip('\n').split(' ', 2)
                if len(parts) < 3 or parts[0] not in names:
                    continue
                rows.append((float(parts[1]), names[parts[0]], parts[2].strip()))
        # Game time restarts per scene (each is staged afresh): keep the recording order, not the clock.
        self.rows = []
        for r in rows:
            if not self.rows or r[0] != self.rows[-1][0] or r[2] != self.rows[-1][2]:
                self.rows.append(r)
        self.secs = [parse(r[2]) for r in self.rows]
        self.cache = {}
        self.scenes = []
        i = 0
        while i < len(self.rows):
            j = i
            while (j + 1 < len(self.rows) and self.secs[j + 1]['name'] == self.secs[i]['name']
                   and 0 <= self.rows[j + 1][0] - self.rows[j][0] < 3):
                j += 1
            self.scenes.append(Scene(self, i, j))
            i = j + 1
        self.groups = []
        for sc in self.scenes:
            key = group_name(sc.name)
            if self.groups and self.groups[-1].name == key:
                self.groups[-1].scenes.append(sc)
            else:
                self.groups.append(Group(key, [sc]))
        h, w = self.image(0).shape[:2]
        self.w, self.h = w - w % 2, h - h % 2

    def image(self, i):
        if i not in self.cache:
            if len(self.cache) > 8:
                self.cache.clear()
            self.cache[i] = np.asarray(Image.open(self.rows[i][1]).convert('RGB'), dtype=np.float32)
        return self.cache[i]

    def group(self, name):
        for g in self.groups:
            if g.name == name:
                return g
        raise KeyError('no scene ' + name + ' in ' + ', '.join(g.name for g in self.groups))


class Scene:
    """One section's run of captures. Its frames are indexed on the output grid: frame k shows game time t0 + k*step."""

    def __init__(self, rec, a, b):
        self.rec, self.a, self.b = rec, a, b
        sec = rec.secs[a]
        self.name, self.top, self.sub, self.rgb, self.speed = sec['name'], sec['top'], sec['sub'], sec['rgb'], sec['speed']
        self.times = [rec.rows[k][0] for k in range(a, b + 1)]
        self.t0, self.t1 = self.times[0], self.times[-1]
        self.bars = not self.name.startswith('CLASH_D')

    def count(self, fps):
        return max(1, int((self.t1 - self.t0) / (20.0 / fps * self.speed)))

    def seconds(self, fps):
        return self.count(fps) / fps

    def raw(self, k, fps):
        """The picture k output frames in, blended between the two captures either side of its game time."""
        t = self.t0 + k * 20.0 / fps * self.speed
        jj = bisect.bisect_right(self.times, t) - 1
        jj = max(0, min(jj, len(self.times) - 2)) if len(self.times) > 1 else 0
        ia = self.a + jj
        ib = min(self.b, ia + 1)
        ta, tb = self.rec.rows[ia][0], self.rec.rows[ib][0]
        f = 0 if ib == ia or tb - ta > 1.5 else min(1, max(0, (t - ta) / max(1e-6, tb - ta)))
        return self.rec.image(ia) if f < 0.02 else self.rec.image(ia) * (1 - f) + self.rec.image(ib) * f


class Group:
    """Consecutive sections that play as one scene: a clash's beats, the beam's charge and blast. No dip between them."""

    def __init__(self, name, scenes):
        self.name, self.scenes = name, scenes

    def count(self, fps):
        return sum(s.count(fps) for s in self.scenes)

    def seconds(self, fps):
        return self.count(fps) / fps

    def locate(self, k, fps):
        """(section, frame within it) for the group's k-th frame."""
        for s in self.scenes:
            n = s.count(fps)
            if k < n:
                return s, k
            k -= n
        return self.scenes[-1], self.scenes[-1].count(fps) - 1

    def frame(self, k, fps, caption=True, bars=None):
        s, kk = self.locate(k, fps)
        return dress(s, kk, fps, s.raw(kk, fps), caption, bars)


class Writer:
    """Pipes frames to ffmpeg's x264 and keeps the timeline: where each part starts, and every frame's brightness."""

    def __init__(self, out, w, h, fps, crf=23, preset='slow'):
        self.w, self.h, self.fps = w, h, fps
        self.parts, self.luma, self.n = [], [], 0
        self.proc = subprocess.Popen([
            imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24',
            '-s', '%dx%d' % (w, h), '-r', str(fps), '-i', '-', '-c:v', 'libx264', '-preset', preset, '-crf', str(crf),
            '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], stdin=subprocess.PIPE)

    def part(self, kind, name, frames):
        start = self.n
        for arr in frames:
            arr = arr.clip(0, 255).astype(np.uint8)
            self.proc.stdin.write(arr.tobytes())
            self.luma.append(round(float(arr[::8, ::8].mean()), 1))
            self.n += 1
        self.parts.append({'kind': kind, 'name': name, 'start': start / self.fps, 'seconds': (self.n - start) / self.fps})

    def close(self):
        self.proc.stdin.close()
        if self.proc.wait() != 0:
            raise SystemExit('ffmpeg failed')

    def timeline(self):
        return {'fps': self.fps, 'seconds': self.n / self.fps, 'parts': self.parts, 'luma': self.luma}


# ---- Parts ---------------------------------------------------------------------------------------------------------
def dress(scene, k, fps, frame, caption=True, bars=None):
    """Letterbox bars and the caption over one picture."""
    w, h = scene.rec.w, scene.rec.h
    bar = int(h * 0.09)
    im = Image.fromarray(frame[:h, :w].clip(0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    if scene.bars if bars is None else bars:
        d.rectangle((0, 0, w, bar), fill=(0, 0, 0))
        d.rectangle((0, h - bar, w, h), fill=(0, 0, 0))
    age = k / fps
    window = min(CAPTION_SECONDS, scene.count(fps) / fps)
    if caption and scene.top and age < window:
        big, small = ImageFont.truetype(BOLD, int(h * 0.075)), ImageFont.truetype(BOLD, int(h * 0.04))
        alpha = min(1, age / 0.2, (window - age) / 0.3)
        col = tuple(int(v * alpha) for v in scene.rgb)
        y = int(h * 0.72)
        d.text((w // 2, y), scene.top, font=small, fill=(int(255 * alpha),) * 3, anchor='mm', stroke_width=3, stroke_fill=(0, 0, 0))
        if scene.sub:
            d.text((w // 2, y + int(h * 0.075)), scene.sub, font=big, fill=col, anchor='mm', stroke_width=4, stroke_fill=(0, 0, 0))
    return np.asarray(im, dtype=np.float32)


def scene_part(group, fps, first=False, last=False):
    """A whole scene: a short dip to black either side, the captions over its sections' first seconds; the last whites out."""
    n = group.count(fps)
    for k in range(n):
        arr = group.frame(k, fps)
        arr *= min(1, k / (fps * (0.6 if first else 0.25)))
        if last:
            tail = (n - k) / (fps * 1.4)
            if tail < 1:
                arr = arr + (255 - arr) * (1 - max(0, tail))
        elif n - k < fps * 0.25:
            arr *= (n - k) / (fps * 0.25)
        yield arr


def clip_part(rec, scene_name, start, frames, fps, fade_in=0.0):
    """A hard-cut slice of a scene (seconds into the whole scene, clash beats and all): no caption, always letterboxed."""
    group = rec.group(scene_name)
    k0 = int(round(start * fps))
    for i in range(frames):
        k = min(group.count(fps) - 1, k0 + i)
        arr = group.frame(k, fps, caption=False, bars=True)
        yield arr * min(1, i / (fade_in * fps)) if fade_in else arr


def black_part(rec, seconds, fps):
    for _ in range(int(round(seconds * fps))):
        yield np.zeros((rec.h, rec.w, 3), np.float32)


def end_part(rec, fps):
    """The beam's white held, then let go to black."""
    hold, fade = int(END_HOLD_SECONDS * fps), int(END_FADE_SECONDS * fps)
    for k in range(hold + fade):
        yield np.full((rec.h, rec.w, 3), 255 * (1 if k < hold else 1 - (k - hold) / fade), np.float32)


def spaced(draw, text, font, cx, cy, tracking, fill):
    """Centered text with its letters spread by ``tracking`` (a fraction of the font size)."""
    gap = font.size * tracking
    widths = [font.getlength(c) for c in text]
    x = cx - (sum(widths) + gap * (len(text) - 1)) / 2
    for c, cw in zip(text, widths):
        draw.text((x, cy), c, font=font, fill=fill, anchor='lm')
        x += cw + gap


def title_part(rec, lines, seconds, fps):
    """The hook's last beat: the title coming up as the beam's white drains to black."""
    w, h = rec.w, rec.h
    base = Image.new('RGB', (w, h), (0, 0, 0))
    d = ImageDraw.Draw(base)
    spaced(d, lines[0], ImageFont.truetype(SANS_BOLD, int(h * 0.13)), w / 2, h * 0.455, 0.22, (255, 255, 255))
    spaced(d, lines[1], ImageFont.truetype(SANS_BOLD, int(h * 0.036)), w / 2, h * 0.455 + h * 0.125, 0.3, CARD_TINT)
    base = np.asarray(base, dtype=np.float32)
    for k in range(int(round(seconds * fps))):
        t = k / fps
        text = base * min(1, t / 0.5) * min(1, (seconds - t) / 0.4)
        yield np.maximum(text, np.full_like(base, 255 * max(0, 1 - t / 0.45)))


def layout(text, w, h, size):
    """Wraps one paragraph into a block, returned as an RGB float image on black; **lead** and ##big## are styled."""
    body, bold = ImageFont.truetype(SANS, size), ImageFont.truetype(SANS_BOLD, size)
    big = ImageFont.truetype(SANS_BOLD, int(size * 1.55))
    big_line = text.startswith('##') and text.endswith('##')
    if big_line:
        text = text.strip('#')
    col_w = int(w * 0.76)
    lines, line, width = [], [], 0
    space = (big if big_line else body).getlength(' ')
    for token in text.split(' '):
        lead = token.startswith('**') and token.endswith('**')
        word = token.strip('*')
        font = big if big_line else (bold if lead else body)
        colour = CARD_TINT if lead else (255, 255, 255) if big_line else CARD_INK
        ww = font.getlength(word)
        if line and width + ww > col_w:
            lines.append(line)
            line, width = [], 0
        line.append((word, font, colour, width))
        width += ww + space
    lines.append(line)
    lh = int(size * (1.9 if big_line else 1.52))
    im = Image.new('RGB', (col_w, lh * len(lines) + size), (0, 0, 0))
    d = ImageDraw.Draw(im)
    for i, ln in enumerate(lines):
        for word, font, colour, x in ln:
            d.text((x, int(size * 1.15) + i * lh), word, font=font, fill=colour, anchor='ls')
    return im


def card_part(rec, seconds, paras, fps, size=None):
    """A black card: each paragraph fades up at its own time, the whole card in and out."""
    w, h = rec.w, rec.h
    size = size or int(h * 0.0525)
    blocks = [(layout(text, w, h, size), at) for text, at in paras]
    gap = int(size * 0.9)
    total = sum(b.height for b, _ in blocks) + gap * (len(blocks) - 1)
    y = (h - total) // 2
    placed = []
    for b, at in blocks:
        layer = np.zeros((h, w, 3), np.float32)
        layer[y:y + b.height, (w - b.width) // 2:(w - b.width) // 2 + b.width] = np.asarray(b, dtype=np.float32)
        placed.append((layer, at))
        y += b.height + gap
    bg = np.empty((h, w, 3), np.float32)
    bg[:] = CARD_BG
    n = int(round(seconds * fps))
    for k in range(n):
        t = k / fps
        frame = bg.copy()
        for layer, at in placed:
            a = min(1, max(0, (t - at) / 0.8))
            if a > 0:
                frame = np.maximum(frame, layer * a)
        yield frame * min(1, t / 0.7) * min(1, (seconds - t) / 0.7)


# ---- Main ----------------------------------------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('dirs', nargs='+')
    ap.add_argument('--fps', type=int, default=60)
    ap.add_argument('--plain', action='store_true', help='just the scenes: no hook, disclaimer or end hold')
    ap.add_argument('--parts', default='hook,title,cards,scenes,end', help='which parts to build (to iterate on one)')
    ap.add_argument('--timeline')
    ap.add_argument('--no-captions', action='store_true', help='leave the scene captions off (for the vertical Short, which crops them)')
    ap.add_argument('--crf', type=int, default=23)
    ap.add_argument('--preset', default='slow')
    args = ap.parse_args()
    fps = args.fps
    if args.no_captions:
        global CAPTION_SECONDS
        CAPTION_SECONDS = 0
    rec = Recording(args.dirs)
    print('%d frames, %d scenes' % (len(rec.rows), len(rec.groups)))
    wr = Writer(args.out, rec.w, rec.h, fps, args.crf, args.preset)
    parts = set(args.parts.split(','))
    if not args.plain:
        marks = [int(round(c * fps)) for c in HOOK_CUTS]
        if 'hook' in parts:
            for i, (name, start) in enumerate(HOOK):
                wr.part('hook', name, clip_part(rec, name, start, marks[i + 1] - marks[i], fps, fade_in=0.35 if i == 0 else 0))
        if 'title' in parts:
            wr.part('title', HOOK_TITLE[0], title_part(rec, HOOK_TITLE, TITLE_SECONDS, fps))
        if 'cards' in parts:
            for i, (seconds, paras) in enumerate(CARDS):
                wr.part('card', 'card%d' % (i + 1), card_part(rec, seconds, paras, fps))
    last = len(rec.groups) - 1
    for i, group in enumerate(rec.groups):
        if 'scenes' in parts or args.plain:
            wr.part('scene', group.name, scene_part(group, fps, first=i == 0 and args.plain, last=i == last))
    if not args.plain and 'end' in parts:
        wr.part('end', 'white', end_part(rec, fps))
    wr.close()
    if args.timeline:
        json.dump(wr.timeline(), open(args.timeline, 'w'))
    print('wrote %s: %d frames, %.1f s at %d fps' % (args.out, wr.n, wr.n / fps, fps))


if __name__ == '__main__':
    main()
