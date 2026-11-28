#!/usr/bin/env python3
"""Builds the full mod showcase from ShowcaseFilmClientTest's recordings, with its sound.

Usage: python3 tools/make_showcase.py OUT.mp4 DIR [DIR ...] [--parts hook,title,acts,cards,end] [--crf 20]

Each DIR is one recording run's screenshots folder (frames.txt, sounds.txt, refused.txt and the captures), given in the
script's order. See docs/SHOWCASE_RECORDING_PROMPT.md for the plan this follows. The picture reuses the domain
cinematic's recording code (tools/make_domain_cinematic.py: rebuilt on an exact 60 fps grid by game time, slow motion
from extra captures, never by stretching). A shot is everything filmed from one staging (sections ``S<n>_...``); its
sections change the caption, not the shot, so they play straight through. Shots are hard cut, an act change cuts
through a white flash, and the domain showcases and the clash play as the cinematic cuts them.

The sound is the game's own: every sound the client was asked to play during the recording (sounds.txt: game time, id,
position, volume, pitch) is mixed in at the frame it was played on, from the mod's own .ogg files (and the vanilla ones
in Loom's asset cache). Under it, each act has a music bed from the mod's JJS tracks, eased back under the hits, and the
mix is mastered to -14 LUFS with a -1.5 dB ceiling.
"""
import argparse
import glob
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile

import imageio_ffmpeg
import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw, ImageFont
from scipy import signal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_domain_cinematic as mdc  # noqa: E402
import make_domain_soundtrack as mds  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/jjk')
LOOM = os.path.expanduser('~/.gradle/caches/fabric-loom/assets')
SR = mds.SR
FPS = 60

# ---- The cut -------------------------------------------------------------------------------------------------------
TITLE = ('JUJUTSU', 'A JJS MOD FOR MINECRAFT')
# Acts in the script's order: (key, music bed, bed level in LUFS). The key is the second word of a shot's section name
# (S12_GOJO_BLUE -> GOJO); the domain showcases, the clash and the beam have their own.
ACTS = {
    'GOJO': ('uv_music', -19),
    'HAKARI': ('idg_music', -19),
    'YUJI': ('shrine_music', -19),
    'YUTA': ('aml_music', -19),
    'RYU': ('ryu_music', -19),
    'DOMAINS': (None, -22),
    'CLASH': ('clash_music', -17),
    'FEEL': ('zero_two_music', -20),
    'BEAM': ('entrusted_music', -17),
}
DOMAIN_THEMES = {'SHOW0': 'uv_music', 'SHOW1': 'shrine_music', 'SHOW2': 'idg_music', 'SHOW3': 'aml_music'}
HOOK_MUSIC = 'purple_music'
# The hook: (section name, seconds into that section, seconds on screen). Filled in from the footage.
HOOK = []
END_LINES = ('JUJUTSU', 'MINECRAFT 26.3  ·  FABRIC')


def act_of(name):
    if name.startswith('SHOW'):
        return 'DOMAINS'
    if name.startswith('CLASH_'):
        return 'CLASH'
    if name in ('FINALE', 'FIRE', 'ENGULFED'):
        return 'BEAM'
    m = re.match(r'S\d+_([A-Z]+)', name)
    return m.group(1) if m else 'OTHER'


_group = mdc.group_name


def group_name(name):
    """Every section of one staging (S<n>_...) is one shot; the cinematic's own scenes group as it groups them."""
    m = re.match(r'(S\d+)_', name)
    return m.group(1) if m else _group(name)


mdc.group_name = group_name


# ---- Picture -------------------------------------------------------------------------------------------------------
def dress(scene, k, fps, frame, caption=True, bars=True):
    """Letterbox and caption. A one-line caption is the technique in its colour; two lines are a name over a title."""
    w, h = scene.rec.w, scene.rec.h
    bar = int(h * 0.075)
    im = Image.fromarray(frame[:h, :w].clip(0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    if bars:
        d.rectangle((0, 0, w, bar), fill=(0, 0, 0))
        d.rectangle((0, h - bar, w, h), fill=(0, 0, 0))
    age = k / fps
    window = min(mdc.CAPTION_SECONDS, scene.count(fps) / fps)
    if caption and scene.top and age < window:
        alpha = min(1, age / 0.18, (window - age) / 0.3)
        col = tuple(int(v * alpha) for v in scene.rgb)
        white = (int(255 * alpha),) * 3
        big, small = ImageFont.truetype(mdc.BOLD, int(h * 0.068)), ImageFont.truetype(mdc.BOLD, int(h * 0.036))
        y = int(h * 0.75)
        if scene.sub:
            d.text((w // 2, y - int(h * 0.05)), scene.top, font=small, fill=white, anchor='mm', stroke_width=3, stroke_fill=(0, 0, 0))
            d.text((w // 2, y + int(h * 0.02)), scene.sub, font=big, fill=col, anchor='mm', stroke_width=4, stroke_fill=(0, 0, 0))
        else:
            d.text((w // 2, y), scene.top, font=big, fill=col, anchor='mm', stroke_width=4, stroke_fill=(0, 0, 0))
    return np.asarray(im, dtype=np.float32)


def group_frame(group, k, fps, caption=True):
    s, kk = group.locate(k, fps)
    if s.name.startswith(('SHOW', 'CLASH_', 'FINALE', 'FIRE', 'ENGULFED')):
        return mdc.dress(s, kk, fps, s.raw(kk, fps), caption)
    return dress(s, kk, fps, s.raw(kk, fps), caption)


def shot_part(group, fps, flash=False, fade_out=False, dip=False):
    """One shot. ``flash`` comes in from white (an act change); ``dip`` comes in from black; ``fade_out`` goes to black."""
    n = group.count(fps)
    for k in range(n):
        arr = group_frame(group, k, fps)
        if flash and k < fps * 0.35:
            a = 1 - k / (fps * 0.35)
            arr = arr + (255 - arr) * a * a
        if dip:
            arr = arr * min(1, k / (fps * 0.3))
        if fade_out and n - k < fps * 0.3:
            arr = arr * ((n - k) / (fps * 0.3))
        yield arr


def find(rec, section):
    """(group, output frame within it) where the first section named ``section`` (without its S<n>_ prefix) starts."""
    for g in rec.groups:
        k = 0
        for s in g.scenes:
            if re.sub(r'^S\d+_', '', s.name) == section:
                return g, k
            k += s.count(FPS)
    raise KeyError(section)


def hook_part(rec, fps):
    for i, (section, start, seconds) in enumerate(HOOK):
        g, k0 = find(rec, section)
        for j in range(int(round(seconds * fps))):
            arr = group_frame(g, min(g.count(fps) - 1, k0 + int(start * fps) + j), fps, caption=False)
            if i == 0:
                arr = arr * min(1, j / (fps * 0.3))
            yield arr


def end_card(rec, fps, seconds=4.0):
    w, h = rec.w, rec.h
    base = Image.new('RGB', (w, h), (0, 0, 0))
    d = ImageDraw.Draw(base)
    mdc.spaced(d, END_LINES[0], ImageFont.truetype(mdc.SANS_BOLD, int(h * 0.12)), w / 2, h * 0.46, 0.22, (255, 255, 255))
    mdc.spaced(d, END_LINES[1], ImageFont.truetype(mdc.SANS_BOLD, int(h * 0.034)), w / 2, h * 0.46 + h * 0.115, 0.3, mdc.CARD_TINT)
    base = np.asarray(base, dtype=np.float32)
    n = int(seconds * fps)
    for k in range(n):
        t = k / fps
        yield base * min(1, t / 0.6) * min(1, (seconds - t) / 1.0)


# ---- Sound ---------------------------------------------------------------------------------------------------------
class Sounds:
    """Resolves a sound event id to a file: the mod's sounds.json, then the vanilla one in Loom's asset cache."""

    def __init__(self):
        self.events = {}
        for ev, spec in json.load(open(os.path.join(ASSETS, 'sounds.json'))).items():
            self.events['jjk:' + ev] = [self._entry(s, 'jjk') for s in spec.get('sounds', [])]
        self.vanilla = {}
        idx = sorted(glob.glob(os.path.join(LOOM, 'indexes', '*.json')))
        if idx:
            objects = json.load(open(idx[-1]))['objects']
            self.vanilla = {k: v['hash'] for k, v in objects.items()}
            if 'minecraft/sounds.json' in self.vanilla:
                for ev, spec in json.load(open(self._obj('minecraft/sounds.json'))).items():
                    self.events['minecraft:' + ev] = [self._entry(s, 'minecraft') for s in spec.get('sounds', [])]
        self.cache, self.missing = {}, {}

    @staticmethod
    def _entry(s, ns):
        if isinstance(s, str):
            return {'name': s if ':' in s else ns + ':' + s, 'volume': 1.0, 'pitch': 1.0, 'event': False}
        name = s['name'] if ':' in s['name'] else ns + ':' + s['name']
        return {'name': name, 'volume': s.get('volume', 1.0), 'pitch': s.get('pitch', 1.0), 'event': s.get('type') == 'event'}

    def _obj(self, key):
        h = self.vanilla[key]
        return os.path.join(LOOM, 'objects', h[:2], h)

    def _path(self, name):
        ns, path = name.split(':', 1)
        if ns == 'jjk':
            p = os.path.join(ASSETS, 'sounds', path + '.ogg')
            return p if os.path.exists(p) else None
        key = 'minecraft/sounds/' + path + '.ogg'
        return self._obj(key) if key in self.vanilla else None

    def load(self, event, seed):
        """(mono samples at SR, volume) for one play of ``event``, the variant picked by ``seed``; None if unknown."""
        entries = self.events.get(event)
        if not entries:
            self.missing[event] = self.missing.get(event, 0) + 1
            return None
        e = entries[int(hashlib.md5(seed.encode()).hexdigest(), 16) % len(entries)]
        if e['event']:
            got = self.load(e['name'], seed)
            return None if got is None else (got[0], got[1] * e['volume'])
        p = self._path(e['name'])
        if p is None:
            self.missing[event] = self.missing.get(event, 0) + 1
            return None
        if p not in self.cache:
            y, sr = sf.read(p, dtype='float32', always_2d=True)
            y = y.mean(axis=1)
            if sr != SR:
                y = signal.resample_poly(y, SR, sr).astype(np.float32)
            self.cache[p] = y
        return self.cache[p], e['volume']


def repitch(y, pitch):
    if abs(pitch - 1) < 0.01:
        return y
    n = max(1, int(len(y) / pitch))
    return np.interp(np.linspace(0, len(y) - 1, n), np.arange(len(y)), y).astype(np.float32)


def music(name):
    y, sr = sf.read(os.path.join(ASSETS, 'sounds', name + '.ogg'), dtype='float32', always_2d=True)
    y = y.mean(axis=1)
    if sr != SR:
        y = signal.resample_poly(y, SR, sr).astype(np.float32)
    return np.stack([y, y], 1)


def bed(name, seconds, target):
    x = music(name)
    x = mds.loop_to(x, seconds + 0.5) if len(x) / SR < seconds + 0.5 else x[:int((seconds + 0.5) * SR)]
    x = x * 10 ** ((target - mds.lufs(x[:min(len(x), 20 * SR)])) / 20)
    return mds.fade(x[:int(seconds * SR)], 0.4, 0.8)


# ---- Assembly ------------------------------------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('dirs', nargs='+')
    ap.add_argument('--parts', default='hook,title,acts,cards,end')
    ap.add_argument('--crf', type=int, default=20)
    ap.add_argument('--preset', default='medium')
    ap.add_argument('--silent', action='store_true')
    args = ap.parse_args()
    parts = set(args.parts.split(','))
    rec = mdc.Recording(args.dirs)
    print('%d frames, %d shots' % (len(rec.rows), len(rec.groups)))
    work = tempfile.mkdtemp(prefix='showcase_')
    film = os.path.join(work, 'film.mp4')
    wr = mdc.Writer(film, rec.w, rec.h, FPS, args.crf, args.preset)

    # Where each shot's frames came from (its run's folder) and where it lands in the film, for its sounds.
    placed = []   # (run dir, film start seconds, scene list with their output offsets)
    cues = []     # (film seconds, act) where each act starts
    if 'hook' in parts and HOOK:
        cues.append((0.0, 'HOOK'))
        wr.part('hook', 'hook', hook_part(rec, FPS))
    if 'title' in parts:
        wr.part('title', 'title', mdc.title_part(rec, TITLE, mdc.TITLE_SECONDS, FPS))
    if 'acts' in parts:
        last_act = None
        for i, g in enumerate(rec.groups):
            act = act_of(g.scenes[0].name)
            start = wr.n / FPS
            if act != last_act:
                cues.append((start, act))
            k = 0
            offsets = []
            for s in g.scenes:
                offsets.append((s, start + k / FPS))
                k += s.count(FPS)
            placed.append((os.path.dirname(rec.rows[g.scenes[0].a][1]), offsets))
            domain = g.name.startswith(('SHOW', 'CLASH_', 'FINALE'))
            wr.part('shot', g.name, shot_part(g, FPS, flash=act != last_act and i > 0 and not domain,
                                              dip=domain or i == 0, fade_out=domain or i == len(rec.groups) - 1))
            last_act = act
    acts_end = wr.n / FPS
    if 'cards' in parts:
        cues.append((acts_end, 'CARDS'))
        for i, (seconds, paras) in enumerate(mdc.CARDS):
            wr.part('card', 'card%d' % (i + 1), mdc.card_part(rec, seconds, paras, FPS))
    if 'end' in parts:
        wr.part('end', 'end', end_card(rec, FPS))
    wr.close()
    total = wr.n / FPS
    json.dump(wr.timeline(), open(os.path.join(work, 'timeline.json'), 'w'))
    print('picture: %.1f s' % total)
    if args.silent:
        os.replace(film, args.out)
        return

    # --- The game's own sounds, at their frames.
    snd = Sounds()
    n = int(total * SR) + SR
    sfx = np.zeros(n, np.float32)
    played = 0
    used_by = {}
    for run, offsets in placed:
        log = os.path.join(run, 'sounds.txt')
        if not os.path.exists(log):
            continue
        events = []
        per_tick = {}
        for line in open(log):
            p = line.split()
            if len(p) < 8 or 'music' in p[1]:
                continue    # the beds are the music; the game's own (and the menu's) would fight them
            # A blast breaking a hundred blocks in one tick is one crunch, not a hundred stacked: three per tick.
            key = (p[1], int(float(p[0])) // (2 if '.break' in p[1] else 1))
            per_tick[key] = per_tick.get(key, 0) + 1
            if per_tick[key] > (1 if '.break' in p[1] or '.step' in p[1] else 4):
                continue
            events.append((float(p[0]), p[1], float(p[6]), float(p[7])))
        used = used_by.setdefault(run, set())
        last_start = {}   # id -> (game time, length in ticks) of its last play
        for s, at in offsets:
            lo, hi = s.t0 - 0.5, s.t1 + 0.5
            for ei, (t, ev, vol, pitch) in enumerate(events):
                if not (lo <= t < hi) or ei in used:
                    continue
                used.add(ei)
                got = snd.load(ev, '%s %.3f' % (ev, t))
                if got is None:
                    continue
                y, base = got
                y = repitch(y, pitch)
                # A sound asked for again while it is still playing (a hum or loop re-sent every few ticks) carries on
                # instead of stacking; quick repeats of a short one (a flurry's hits) stay, 0.12 s apart at least.
                length = len(y) / SR * 20
                prev = last_start.get(ev)
                if prev and (t - prev[0] < 2.4 or (prev[1] > 30 and t - prev[0] < prev[1] * 0.9)):
                    continue
                last_start[ev] = (t, length)
                # A slowed section plays its sounds where they fall on its stretched clock, at their own speed.
                when = at + max(0.0, t - s.t0) / (20.0 * s.speed)
                i = int(when * SR)
                if i >= n:
                    continue
                gain = min(1.0, vol) * base * (0.22 if '.break' in ev or '.step' in ev else 0.8 if ev.startswith('minecraft:') else 1.0)
                m = min(len(y), n - i)
                sfx[i:i + m] += y[:m] * gain
                played += 1
    print('sounds: %d played, missing %s' % (played, dict(sorted(snd.missing.items(), key=lambda kv: -kv[1])[:12])))
    sfx2 = np.stack([sfx, sfx], 1)

    # --- Music beds under it, one per act, eased back under the hits.
    mus = np.zeros((n, 2), np.float32)
    bounds = cues + [(total, 'END')]
    for (t0, act), (t1, _) in zip(bounds, bounds[1:]):
        if act == 'HOOK':
            x = music(HOOK_MUSIC)
            x = x * 10 ** ((-19 - mds.lufs(x)) / 20)
            x = mds.fade(x[:int((t1 - t0 + 0.8) * SR)], 0.0, 0.8)
        elif act == 'CARDS':
            x = bed('entrusted_music', t1 - t0, -30)
        elif act == 'DOMAINS':
            continue
        elif act in ACTS and ACTS[act][0]:
            x = bed(ACTS[act][0], t1 - t0, ACTS[act][1])
        else:
            continue
        i = int(t0 * SR)
        m = min(len(x), n - i)
        mus[i:i + m] += x[:m]
    # Each domain showcase gets its own theme, entering on the burst of its dome.
    for run, offsets in placed:
        for s, at in offsets:
            if s.name[:5] in DOMAIN_THEMES and s is offsets[0][0]:
                dur = sum(sc.count(FPS) for sc, _ in offsets) / FPS
                x = bed(DOMAIN_THEMES[s.name[:5]], dur, -16)
                i = int(at * SR)
                m = min(len(x), n - i)
                mus[i:i + m] += x[:m]
    mus *= mds.duck_gain(sfx2, depth_db=5)[:, None]
    mix = mus + sfx2 * 10 ** (-4 / 20)
    if os.environ.get('SHOWCASE_STEMS'):
        for name, x in (('sfx', sfx2), ('music', mus)):
            sf.write(os.path.join(os.environ['SHOWCASE_STEMS'], name + '.wav'), x[:int(total * SR)], SR, subtype='FLOAT')
    mastered = mds.master(mix, total)
    raw = os.path.join(work, 'mix.wav')
    lim = os.path.join(work, 'mix_lim.wav')
    sf.write(raw, mastered, SR, subtype='FLOAT')
    mds.limit(raw, lim)
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-v', 'error', '-i', film, '-i', lim, '-map', '0:v', '-map', '1:a',
                    '-c:v', 'copy', '-c:a', 'aac', '-b:a', '256k', '-shortest', '-movflags', '+faststart', args.out], check=True)
    print('wrote %s: %.1f s, loudness %.1f LUFS' % (args.out, total, mds.lufs(sf.read(lim, dtype='float32')[0])))


if __name__ == '__main__':
    main()
