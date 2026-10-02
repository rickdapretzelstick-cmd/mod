#!/usr/bin/env python3
"""Scores the domain cinematic with the Jujutsu Shenanigans soundtrack, cut to its picture.

Usage: python3 tools/make_domain_soundtrack.py TIMELINE.json OUT.wav [--plot OVERVIEW.png]

TIMELINE.json is what ``tools/make_domain_cinematic.py --timeline`` wrote: where the hook, the disclaimer and every scene
start, and the brightness of every frame (the soundtrack finds each flash in it, so the hits land on the picture). The
music is the original JJS tracks, full length (the ones tools/roblox_sounds.py caches in build/roblox-cache); the hits
and voices are the mod's own sounds; the risers, whooshes and booms are made here. Each domain's theme comes in on its
burst, the hook is cut on the bass hits of purple_music, and the disclaimer sits on a quiet bed. The mix is mastered to
-14 LUFS with a -1.5 dB ceiling. Mux it on with ffmpeg afterwards (the film itself is silent).
"""
import argparse
import json
import os
import subprocess

import imageio_ffmpeg
import numpy as np
import pyloudnorm
import soundfile as sf
from scipy import signal

import make_domain_cinematic

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, 'build/roblox-cache')
SOUNDS = os.path.join(ROOT, 'src/main/resources/assets/jjk/sounds')
SR = 48000

# The original tracks by Roblox audio id (the cache tools/roblox_sounds.py fills).
TRACKS = {'purple': 14326861262, 'uv': 16071901783, 'shrine': 15583493700, 'idg': 9039704032, 'clash': 89526560746434,
          'aml': 84395583379130, 'aml_clashwin': 107921124631537, 'aml_ambiance': 91942331202663,
          'tlb_music': 117663808507739}

MAIN = -17.0       # LUFS each music cue is levelled to before the master
BED = MAIN - 8     # the disclaimer's bed, well under it
MASTER = -13.6     # integrated LUFS of the mix going into the limiter (about -14 coming out)
CEILING = -1.5     # dB, after the limiter
FIRE_AT = 2.8      # seconds into the finale that the beam is fired (56 ticks of charge)
METER = pyloudnorm.Meter(SR)

# Where each track's own hit is, found by looking (tools/analysis in the soundtrack's history): the bass drop of the
# shrine's theme, the groove coming back after IDG's gap, the pause-and-slam in the Unlimited Void theme.
UV_HIT, SHRINE_DROP, IDG_GAP, IDG_DROP = 3.585, 5.085, 132.40, 133.78


# ---- Sources -------------------------------------------------------------------------------------------------------
_tracks = {}


def track(name):
    """A full-length original, stereo 48 kHz float."""
    if name not in _tracks:
        raw = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-v', 'error', '-i', os.path.join(CACHE, '%d.bin' % TRACKS[name]),
                              '-f', 'f32le', '-ar', str(SR), '-ac', '2', '-'], check=True, capture_output=True).stdout
        _tracks[name] = np.frombuffer(raw, np.float32).reshape(-1, 2).copy()
    return _tracks[name]


def sfx(name, peak=None):
    """One of the mod's own sounds (mono), as centred stereo; ``peak`` sets its loudest sample, in dB."""
    y, sr = sf.read(os.path.join(SOUNDS, name + '.ogg'), dtype='float32')
    assert sr == SR, name
    y = y if y.ndim == 1 else y.mean(axis=1)
    x = np.stack([y, y], 1)
    return x * (10 ** (peak / 20) / np.abs(x).max()) if peak is not None else x


def seg(x, start, length):
    """``length`` seconds of ``x`` from ``start`` (silence where it runs out)."""
    i, n = int(round(start * SR)), int(round(length * SR))
    out = np.zeros((n, x.shape[1]), np.float32)
    a, b = max(0, -i), min(n, len(x) - i)
    if b > a:
        out[a:b] = x[i + a:i + b]
    return out


def lufs(x):
    return METER.integrated_loudness(x.astype(np.float64))


def levelled(x, window, target=MAIN):
    """``x`` scaled so ``window`` (start, length in seconds) measures ``target`` LUFS."""
    return x * 10 ** ((target - lufs(seg(x, *window))) / 20)


def fade(x, fade_in=0.0, fade_out=0.0):
    x = x.copy()
    k = min(len(x), int(fade_in * SR))
    if k:
        x[:k] *= (np.sin(np.linspace(0, np.pi / 2, k)) ** 2)[:, None]
    k = min(len(x), int(fade_out * SR))
    if k:
        x[-k:] *= (np.cos(np.linspace(0, np.pi / 2, k)) ** 2)[:, None]
    return x


def gain_curve(x, points):
    """A level envelope: ``points`` are (seconds into x, dB), joined by straight lines in dB."""
    t = np.arange(len(x)) / SR
    g = np.interp(t, [p[0] for p in points], [p[1] for p in points])
    return x * (10 ** (g / 20))[:, None].astype(np.float32)


def lowpass_curve(x, points, block=240, order=4):
    """``x`` low-passed with a cutoff that follows ``points`` = (seconds, Hz), joined on a log scale."""
    ts = np.array([p[0] for p in points], float)
    fs = np.log(np.array([p[1] for p in points], float))
    out = np.empty_like(x)
    zi = [np.zeros((order // 2, 2)) for _ in range(x.shape[1])]
    for s in range(0, len(x), block):
        fc = min(float(np.exp(np.interp((s + block / 2) / SR, ts, fs))), SR / 2 * 0.98)
        sos = signal.butter(order, fc, 'low', fs=SR, output='sos')
        for c in range(x.shape[1]):
            out[s:s + block, c], zi[c] = signal.sosfilt(sos, x[s:s + block, c], zi=zi[c])
    return out


def loop_to(x, seconds, crossfade=1.5):
    """``x`` repeated to fill ``seconds``, each seam crossfaded."""
    out = np.zeros((int(seconds * SR), x.shape[1]), np.float32)
    k = int(crossfade * SR)
    pos, first = 0, True
    while pos < len(out):
        piece = x.copy()
        if not first:
            piece[:k] *= (np.sin(np.linspace(0, np.pi / 2, k)) ** 2)[:, None]
        piece[-k:] *= (np.cos(np.linspace(0, np.pi / 2, k)) ** 2)[:, None]
        end = min(len(out), pos + len(piece))
        out[pos:end] += piece[:end - pos]
        pos += len(piece) - k
        first = False
    return out


# ---- Made here -----------------------------------------------------------------------------------------------------
def riser(seconds, lo=250, hi=9000, seed=1):
    """Noise sweeping up and swelling, ending the instant the hit it leads into lands."""
    n = int(seconds * SR)
    x = np.random.default_rng(seed).standard_normal((n, 2)).astype(np.float32)
    x = lowpass_curve(x, [(0, lo), (seconds, hi)])
    x = signal.sosfilt(signal.butter(2, 180, 'high', fs=SR, output='sos'), x, axis=0)
    t = np.linspace(0, 1, n)
    env = t ** 2.4
    tone = np.sin(2 * np.pi * np.cumsum(180 * 8.0 ** t) / SR) * env * 0.35
    x = x * env[:, None] + tone[:, None]
    return (x / np.abs(x).max()).astype(np.float32)


def whoosh(seconds=0.5, lo=300, hi=6000, seed=2):
    """A short pass of air, loudest two thirds of the way through."""
    n = int(seconds * SR)
    x = np.random.default_rng(seed).standard_normal((n, 2)).astype(np.float32)
    x = lowpass_curve(x, [(0, lo), (seconds, hi)], order=2)
    x = signal.sosfilt(signal.butter(2, 250, 'high', fs=SR, output='sos'), x, axis=0)
    t = np.linspace(0, 1, n)
    env = np.sin(np.pi * t ** 0.6) ** 2
    return (x * env[:, None] / np.abs(x).max()).astype(np.float32)


def boom(seconds=3.0, f0=120.0, f1=44.0, drop=0.09, decay=0.8, seed=3):
    """A sub drop with a knock and a thump on top: the weight under a hit. Its floor is 42 Hz, where small speakers
    still carry it and no headroom goes on notes nobody hears."""
    f1, decay = max(f1, 42.0), min(decay, 1.1)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    body = np.sin(2 * np.pi * np.cumsum(f1 + (f0 - f1) * np.exp(-t / drop)) / SR) * np.exp(-t / decay)
    thump = signal.sosfilt(signal.butter(2, 1200, 'low', fs=SR, output='sos'), np.random.default_rng(seed).standard_normal(n))
    thump *= np.exp(-t / 0.06) / np.abs(thump).max()
    knock = np.sin(2 * np.pi * 220 * t) * np.exp(-t / 0.1) * 0.5
    x = np.tanh(2.6 * (body + 0.5 * thump + knock)) / np.tanh(2.6)
    x = signal.sosfilt(signal.butter(2, 32, 'high', fs=SR, output='sos'), x)
    x[:96] *= np.linspace(0, 1, 96)
    return np.stack([x, x], 1).astype(np.float32)


def charge_tone(seconds, f0=70.0, f1=620.0):
    """A power-up: a thick tone climbing and shivering faster, swelling to the moment it's let go."""
    n = int(seconds * SR)
    u = np.arange(n) / n
    ph = 2 * np.pi * np.cumsum(f0 * (f1 / f0) ** (u ** 1.4)) / SR
    x = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph)
    x *= (0.65 + 0.35 * np.sin(2 * np.pi * np.cumsum(5 + 14 * u) / SR)) * u ** 1.3
    x = signal.sosfilt(signal.butter(2, 4000, 'low', fs=SR, output='sos'), x)
    x[-int(0.03 * SR):] *= np.linspace(1, 0, int(0.03 * SR))     # let go cleanly
    d = int(0.007 * SR)
    out = np.stack([x, np.concatenate([np.zeros(d), x[:-d]])], 1)
    return (out / np.abs(out).max()).astype(np.float32)


def reverb(x, wet=0.3, seconds=2.4, seed=4):
    """A cheap hall: the sound's own tail, smeared, mixed under it."""
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal((n, 2)) * np.exp(-t / (seconds / 6.5))[:, None]
    ir = signal.sosfilt(signal.butter(2, 5000, 'low', fs=SR, output='sos'), ir, axis=0)
    ir /= np.sqrt((ir ** 2).sum(axis=0))
    out = np.zeros((len(x) + n - 1, 2), np.float32)
    for c in range(2):
        out[:, c] = signal.fftconvolve(x[:, c], ir[:, c]) * wet
    out[:len(x)] += x * (1 - wet)
    return out


TAIL = 0.15        # a cue runs this far into the next scene, fading out under its dip to black


def duck_gain(key, depth_db=5.0, hop=480, attack=0.03, release=0.3):
    """Gain (linear, per sample) that eases the music back wherever ``key`` is sounding: a sidechain duck."""
    m = key.mean(axis=1)
    n = len(m) // hop
    env = 10 * np.log10((m[:n * hop].reshape(n, hop) ** 2).mean(axis=1) + 1e-12)
    target = np.clip((env + 50) / 15, 0, 1) * depth_db      # -50 dB in the key: nothing; -35 dB and up: the full depth
    a, r = np.exp(-hop / SR / attack), np.exp(-hop / SR / release)
    g, cur = np.zeros(n), 0.0
    for i in range(n):
        c = a if target[i] > cur else r
        cur = c * cur + (1 - c) * target[i]
        g[i] = cur
    return 10 ** (-np.interp(np.arange(len(key)) / SR, (np.arange(n) + 0.5) * hop / SR, g) / 20)


class Mix:
    """Four buses: the music (eased back under the voices), the voices, the hits and the air (risers, whooshes, the bed)."""

    def __init__(self, seconds):
        n = int(round(seconds * SR))
        self.bus = {b: np.zeros((n, 2), np.float32) for b in ('music', 'voice', 'hit', 'air')}

    def add(self, x, at, gain=0.0, until=None, bus='music'):
        """Sums ``x`` in at ``at`` seconds, ``gain`` dB; ``until`` ends it there (a quick fade) if it runs longer."""
        if until is not None and len(x) > int((until - at) * SR):
            x = fade(x[:max(0, int((until - at) * SR))], 0.0, 0.25)
        buf = self.bus[bus]
        i = int(round(at * SR))
        a, b = max(0, -i), min(len(x), len(buf) - i)
        if b > a:
            buf[i + a:i + b] += x[a:b] * 10 ** (gain / 20)

    def render(self):
        music = self.bus['music'] * duck_gain(self.bus['voice'])[:, None]
        return music + self.bus['voice'] + self.bus['hit'] + self.bus['air']


# ---- The timeline --------------------------------------------------------------------------------------------------
class Film:
    def __init__(self, path):
        t = json.load(open(path))
        self.fps, self.seconds, self.luma, self.parts = t['fps'], t['seconds'], np.array(t['luma']), t['parts']
        self.scene = {p['name']: p for p in t['parts'] if p['kind'] == 'scene'}
        self.cards = [p for p in t['parts'] if p['kind'] == 'card']
        self.title = next(p for p in t['parts'] if p['kind'] == 'title')
        self.end = next(p for p in t['parts'] if p['kind'] == 'end')

    def stop(self, scene):
        return self.scene[scene]['start'] + self.scene[scene]['seconds']

    def start(self, scene):
        return self.scene[scene]['start']

    def flash(self, scene, lo, hi):
        """When the picture's brightness climbs fastest in [lo, hi] seconds of ``scene``: the middle of its steepest 0.1 s."""
        f0 = int(round(self.start(scene) * self.fps))
        w = int(round(0.1 * self.fps))
        rise = [self.luma[f0 + i + w] - self.luma[f0 + i] for i in range(int(lo * self.fps), int(hi * self.fps))]
        i = int(np.argmax(rise)) + int(lo * self.fps)
        print('  flash %-10s %.2f-%.2f s: %+.0f at %.3f s' % (scene, lo, hi, max(rise), i / self.fps + w / self.fps / 2))
        return self.start(scene) + i / self.fps + w / self.fps / 2


# ---- The score -----------------------------------------------------------------------------------------------------
def score(film):
    mix = Mix(film.seconds + 0.5)
    show = ['SHOW0', 'SHOW1', 'SHOW2', 'SHOW3']
    clash_g, clash_y, finale = 'CLASH_gojo', 'CLASH_yuta', 'FINALE'
    cuts = make_domain_cinematic.HOOK_CUTS
    VOICE, HIT = -7, -11

    # -- The hook: purple_music, whose bass hits the cuts are made on, then its tail under the title.
    purple = levelled(track('purple'), (0.5, 7.5), MAIN + 1)
    mix.add(fade(purple, 0.03, 0.9), 0.0)
    mix.add(boom(3.2, 120, 36, decay=0.9), cuts[1] - 0.02, -9, bus='hit')
    for cut in cuts[2:6]:
        mix.add(whoosh(0.4), cut - 0.28, -16, bus='air')
    mix.add(riser(1.85, 300, 9000, seed=11), cuts[-1] - 1.85, -14, bus='air')
    mix.add(reverb(boom(4.0, 150, 28, decay=1.5, seed=5), wet=0.35), cuts[-1] - 0.02, -5, bus='hit')

    # -- The disclaimer: a dark bed, and the Unlimited Void theme's first bars rising out of it.
    s0, f0 = film.start(show[0]), film.flash(show[0], 1.0, 2.5)
    bed_from, bed_to = film.cards[0]['start'] + 0.6, s0 - 1.0
    bed = loop_to(track('aml_ambiance'), bed_to - bed_from)
    mid, side = (bed[:, :1] + bed[:, 1:]) / 2, (bed[:, :1] - bed[:, 1:]) / 2     # it is very wide: keep half the sides for phones
    bed = np.concatenate([mid + side * 0.5, mid - side * 0.5], axis=1)
    bed = bed * 10 ** ((BED - lufs(bed)) / 20)
    mix.add(fade(bed, 2.8, 2.0), bed_from, bus='air')

    # -- Unlimited Void: its track opens up from muffled, goes quiet for a breath, and hits on the burst.
    uv = levelled(track('uv'), (UV_HIT, 3.0))
    uv = lowpass_curve(uv, [(0, 450), (UV_HIT - 1.7, 450), (UV_HIT - 0.03, 16000)])
    uv = gain_curve(uv, [(0, 0), (UV_HIT - 0.55, 0), (UV_HIT - 0.14, -12), (UV_HIT - 0.012, -12), (UV_HIT, 0)])
    mix.add(fade(seg(uv, 0, UV_HIT + film.stop(show[0]) - f0 + TAIL), 0.2, 0.45), f0 - UV_HIT)
    mix.add(riser(f0 - 0.14 - (s0 - 0.8), 400, 11000, seed=12), s0 - 0.8, -13, bus='air')
    mix.add(sfx('domain_charge', VOICE), s0 + 0.42, bus='voice')
    mix.add(reverb(boom(3.0, 125, 35, decay=0.8), wet=0.25), f0 - 0.02, -8, bus='hit')
    mix.add(sfx('domain_expand', HIT), f0 - 0.25, until=film.stop(show[0]), bus='hit')

    # -- Malevolent Shrine: the theme's own drop lands on the flash.
    s1, f1 = film.start(show[1]), film.flash(show[1], 1.0, 2.5)
    shrine = levelled(track('shrine'), (SHRINE_DROP, 3.0))
    mix.add(fade(seg(shrine, SHRINE_DROP - (f1 - s1), film.stop(show[1]) - s1 + TAIL), 0.12, 0.45), s1)
    mix.add(riser(f1 - 0.1 - (s1 + 0.3), 400, 9000, seed=13), s1 + 0.3, -15, bus='air')
    mix.add(sfx('shrine_voice', VOICE), s1 + 0.42, bus='voice')
    mix.add(reverb(boom(3.0, 105, 32, decay=0.8, seed=6), wet=0.25), f1 - 0.02, -8, bus='hit')
    mix.add(sfx('shrine_expand', HIT), f1 - 0.25, bus='hit')
    mix.add(boom(1.6, 90, 33, decay=0.5, seed=7), s1 + 4.22 - 0.02, -12, bus='hit')

    # -- Idle Death Gamble: the groove drops out, a beat of near silence for Hakari's voice, then it comes back.
    s2, f2 = film.start(show[2]), film.flash(show[2], 1.0, 2.5)
    idg = levelled(track('idg'), (IDG_DROP, 3.0))
    enter = s2 + max(0.0, IDG_GAP - (IDG_DROP - (f2 - s2)))
    mix.add(fade(seg(idg, IDG_GAP, film.stop(show[2]) - enter + TAIL), 0.02, 0.45), enter)
    mix.add(riser(f2 - 0.1 - (s2 + 0.9), 400, 9000, seed=14), s2 + 0.9, -17, bus='air')
    mix.add(sfx('idg_voice', VOICE), s2 + 0.42, bus='voice')
    mix.add(reverb(boom(3.0, 130, 40, decay=0.8, seed=8), wet=0.25), f2 - 0.02, -8, bus='hit')
    mix.add(sfx('idg_sealed', HIT), f2 - 0.25, until=film.stop(show[2]), bus='hit')
    mix.add(boom(1.6, 100, 36, decay=0.5, seed=9), s2 + 3.85 - 0.02, -12, bus='hit')

    # -- Authentic Mutual Love: a steady groove, so it opens up from muffled (with a breath of quiet) on the white flash.
    s3, f3 = film.start(show[3]), film.flash(show[3], 1.5, 3.0)
    hit = f3 - s3
    aml = track('aml')
    u0 = beat_times(aml, 8.0, 22.0)[0] - hit
    aml = levelled(aml, (u0 + hit, 3.0))
    body = seg(aml, u0, film.stop(show[3]) - s3 + TAIL)
    body = lowpass_curve(body, [(0, 380), (hit - 1.4, 380), (hit - 0.03, 16000)])
    body = gain_curve(body, [(0, -7), (hit - 1.4, -7), (hit - 0.5, -4), (hit - 0.14, -14), (hit - 0.012, -14), (hit, 0)])
    mix.add(fade(body, 0.12, 0.45), s3)
    mix.add(riser(hit - 0.14 - 0.5, 400, 9000, seed=15), s3 + 0.5, -15, bus='air')
    mix.add(sfx('aml_voice', VOICE), s3 + 0.42, bus='voice')
    mix.add(sfx('aml_start', HIT - 2), s3 + 0.42, until=film.stop(show[3]), bus='hit')
    mix.add(reverb(boom(3.0, 95, 31, decay=0.8, seed=10), wet=0.25), f3 - 0.02, -8, bus='hit')
    mix.add(boom(1.6, 95, 33, decay=0.5, seed=16), s3 + 4.74 - 0.02, -12, bus='hit')

    # -- The Gojo / Sukuna clash: clash_music from its first beat; its hits are the B and C beats of the scene.
    g0, g1 = film.start(clash_g), film.stop(clash_g)
    clash = levelled(track('clash'), (1.0, 8.0))
    mix.add(fade(seg(clash, 0, g1 - g0 + TAIL), 0.04, 2.4), g0)
    mix.add(sfx('shrine_voice', VOICE), g0 + 0.88, bus='voice')
    c1 = film.flash(clash_g, 1.0, 1.8)
    mix.add(reverb(boom(3.0, 125, 35, decay=0.8, seed=17), wet=0.25), c1 - 0.02, -9, bus='hit')
    mix.add(sfx('domain_expand', HIT), c1 - 0.25, bus='hit')
    c2 = film.flash(clash_g, 3.0, 4.0)
    mix.add(sfx('domain_clash', HIT), c2 - 0.25, bus='hit')
    for lo, hi, name in ((7.2, 8.0, 'clash_hit'), (8.2, 8.9, 'clash_hit_2'), (9.8, 10.4, 'clash_hit_3'), (10.4, 10.9, 'clash_hit')):
        mix.add(sfx(name, HIT), film.flash(clash_g, lo, hi) - 0.05, bus='hit')
    c3 = film.flash(clash_g, 14.3, 15.0)
    mix.add(reverb(boom(3.0, 110, 34, decay=0.9, seed=18), wet=0.25), c3 - 0.02, -10, bus='hit')
    mix.add(sfx('clash_win', HIT), c3 - 0.05, until=g1, bus='hit')

    # -- The Yuta / Hakari clash.
    y0, y1 = film.start(clash_y), film.stop(clash_y)
    cw = levelled(track('aml_clashwin'), (3.0, 8.0))
    mix.add(fade(seg(cw, 0, y1 - y0 + TAIL), 0.0, 2.0), y0)
    mix.add(sfx('idg_voice', VOICE), y0 + 0.88, bus='voice')
    d1 = film.flash(clash_y, 1.0, 1.7)
    mix.add(reverb(boom(3.0, 120, 35, decay=0.8, seed=19), wet=0.25), d1 - 0.02, -8, bus='hit')
    mix.add(sfx('domain_expand', HIT), d1 - 0.25, bus='hit')
    d2 = film.flash(clash_y, 3.2, 3.8)
    mix.add(sfx('domain_clash', HIT), d2 - 0.25, bus='hit')
    d3 = film.flash(clash_y, 6.6, 7.3)
    mix.add(sfx('clash_hit_2', HIT), d3 - 0.05, bus='hit')
    d4 = film.flash(clash_y, 10.9, 11.7)
    mix.add(reverb(boom(3.0, 110, 34, decay=0.9, seed=20), wet=0.25), d4 - 0.02, -10, bus='hit')
    mix.add(sfx('clash_win', HIT), d4 - 0.05, until=y1, bus='hit')

    # -- True Love Beam: the charge, the muffled rush in slow motion, and the whiteout it all opens onto.
    f_0 = film.start(finale)
    fire = f_0 + FIRE_AT
    flash = film.flash(finale, 3.3, 4.2)
    mix.add(sfx('tlb_charge', HIT), f_0, bus='hit')
    mix.add(charge_tone(FIRE_AT - 0.35, 70, 620), f_0 + 0.3, -13, bus='air')
    mix.add(riser(flash - 0.05 - (fire - 0.4), 300, 12000, seed=21), fire - 0.4, -13, bus='air')
    mix.add(sfx('tlb_small', HIT), fire - 0.1, bus='hit')
    mix.add(sfx('tlb_power', HIT - 1), flash - 1.3, bus='hit')
    peak = 1.64
    tlb = levelled(track('tlb_music'), (peak, 3.0), MAIN + 1)
    tlb = lowpass_curve(tlb, [(0, 600), (peak - 0.03, 600), (peak + 0.02, 18000)])
    mix.add(fade(seg(tlb, 0, film.seconds - (flash - peak)), 0.2, 2.2), flash - peak)
    mix.add(reverb(boom(4.5, 140, 27, decay=1.7, seed=22), wet=0.3), flash - 0.02, -6, bus='hit')
    return mix


def beat_times(x, lo, hi):
    """Beat times (seconds into ``x``) between ``lo`` and ``hi``: where a steady groove's bars can be cut."""
    import librosa
    m = x[int(lo * SR):int(hi * SR)].mean(axis=1)
    _, beats = librosa.beat.beat_track(y=m, sr=SR, units='time')
    return beats + lo


# ---- Mastering and the picture of it -------------------------------------------------------------------------------
def soft_clip(x, ceiling, knee=0.6):
    """Peaks above ``knee * ceiling`` are bent smoothly up to ``ceiling`` instead of being cut off."""
    lo = knee * ceiling
    a = np.abs(x)
    return np.where(a <= lo, x, np.sign(x) * (lo + (ceiling - lo) * np.tanh((a - lo) / (ceiling - lo))))


def master(buf, seconds):
    x = buf[:int(round(seconds * SR))].astype(np.float64)
    x = signal.sosfilt(signal.butter(4, 34, 'high', fs=SR, output='sos'), x, axis=0)
    x *= 10 ** ((MASTER - lufs(x)) / 20)
    x = soft_clip(x, 10 ** (-1.0 / 20))
    k = int(0.6 * SR)
    x[-k:] *= (np.cos(np.linspace(0, np.pi / 2, k)) ** 2)[:, None]
    return x.astype(np.float32)


def limit(path_in, path_out):
    ceiling = 10 ** (CEILING / 20)
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-v', 'error', '-i', path_in, '-af',
                    'alimiter=limit=%.4f:attack=5:release=80:level=0' % ceiling, path_out], check=True)


def overview(film, wav, path):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    x, sr = sf.read(wav)
    m = x.mean(axis=1)
    hop = sr // 20
    env = 20 * np.log10(np.sqrt((m[:len(m) // hop * hop].reshape(-1, hop) ** 2).mean(axis=1)) + 1e-7)
    fig, ax = plt.subplots(3, 1, figsize=(22, 8), sharex=True, gridspec_kw={'height_ratios': [1, 1, 2]})
    ax[0].plot(np.arange(len(film.luma)) / film.fps, film.luma, lw=0.8, color='tab:orange')
    ax[0].set_ylabel('picture')
    ax[1].plot(np.arange(len(env)) / 20, env, lw=0.8)
    ax[1].set_ylim(-60, 0)
    ax[1].set_ylabel('dB')
    f, t, s = signal.spectrogram(m, sr, nperseg=2048, noverlap=1536)
    sel = f < 9000
    ax[2].pcolormesh(t, f[sel], 10 * np.log10(s[sel] + 1e-12), shading='auto', cmap='magma', vmin=-100, vmax=-30)
    for p in film.parts:
        for a in ax:
            a.axvline(p['start'], color='w' if a is ax[2] else 'k', lw=0.5, alpha=0.6)
    ax[2].set_xlim(0, film.seconds)
    fig.tight_layout()
    fig.savefig(path, dpi=60)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('timeline')
    ap.add_argument('out')
    ap.add_argument('--plot')
    args = ap.parse_args()
    film = Film(args.timeline)
    mix = score(film)
    x = master(mix.render(), film.seconds)
    raw = args.out + '.raw.wav'
    sf.write(raw, x, SR, subtype='FLOAT')
    limit(raw, args.out)
    os.remove(raw)
    y, _ = sf.read(args.out)
    print('wrote %s: %.1f s, %.1f LUFS, peak %.1f dBFS' % (args.out, len(y) / SR, lufs(y), 20 * np.log10(np.abs(y).max())))
    if args.plot:
        overview(film, args.out, args.plot)


if __name__ == '__main__':
    main()
