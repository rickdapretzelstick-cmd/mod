"""Procedurally synthesizes the mod's sound effects (mono OGG Vorbis). Run: python3 tools/gen_sounds.py"""
import numpy as np, soundfile as sf, os

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'sounds')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)

def t(d): return np.arange(int(SR * d)) / SR
def env(n, a=0.005, r=None, curve=3.0):
    x = np.linspace(0, 1, n)
    e = np.ones(n)
    na = max(1, int(a * SR))
    e[:na] = np.linspace(0, 1, na)
    tail = (1 - x) ** curve if r is None else np.exp(-x * r)
    return e * tail
def noise(d): return rng.standard_normal(int(SR * d))
def lowpass(x, cutoff):
    # one-pole, cutoff may be an array
    a = np.exp(-2 * np.pi * np.broadcast_to(cutoff, x.shape) / SR)
    y = np.zeros_like(x); s = 0.0
    for i in range(len(x)):
        s = (1 - a[i]) * x[i] + a[i] * s; y[i] = s
    return y
def highpass(x, cutoff): return x - lowpass(x, cutoff)
def bandsweep(x, f0, f1):
    c = np.geomspace(f0, f1, len(x))
    return highpass(lowpass(x, c * 1.6), c * 0.6)
def sine_sweep(d, f0, f1, geo=True):
    f = np.geomspace(f0, f1, int(SR * d)) if geo else np.linspace(f0, f1, int(SR * d))
    return np.sin(2 * np.pi * np.cumsum(f) / SR)
def pad(x, n):
    return np.pad(x, (0, max(0, n - len(x))))[:n]
def mix(*parts):
    n = max(len(p) for p in parts)
    return sum(pad(p, n) for p in parts)
def delay(x, dt, g, taps=4):
    y = x.copy()
    for k in range(1, taps + 1):
        s = int(dt * k * SR)
        y = pad(y, len(x) + s)
        y[s:s + len(x)] += x * (g ** k)
    return y
def softclip(x, drive=1.0): return np.tanh(x * drive)
def save(name, x, gain=0.9):
    x = x - np.mean(x)
    fade = min(len(x), int(0.01 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    sf.write(os.path.join(OUT, name + '.ogg'), x.astype(np.float32), SR, format='OGG', subtype='VORBIS')

def whoosh(d, f0, f1, a=0.04):
    n = noise(d); e = np.sin(np.linspace(0, np.pi, len(n))) ** 1.5
    return bandsweep(n, f0, f1) * e
def thump(d, f0, f1, r=18):
    x = sine_sweep(d, f0, f1); return x * env(len(x), 0.002, r)
def boom(d, f0=90, f1=30, noise_amt=0.6, r=6):
    body = sine_sweep(d, f0, f1) * env(int(SR * d), 0.003, r)
    n = lowpass(noise(d), np.geomspace(4000, 200, int(SR * d))) * env(int(SR * d), 0.001, r * 0.8) * noise_amt
    return softclip(body + n, 1.5)
def chime(d, freqs, r=4, detune=0.0):
    tt = t(d); x = sum(np.sin(2 * np.pi * f * (1 + detune * i) * tt) / (i + 1) for i, f in enumerate(freqs))
    return x * env(len(tt), 0.01, r)
def pad_chord(d, freqs, attack=0.3, vib=4.0):
    tt = t(d); x = np.zeros_like(tt)
    for f in freqs:
        for dt in (-0.004, 0, 0.004):
            x += np.sin(2 * np.pi * f * (1 + dt) * tt + 0.3 * np.sin(2 * np.pi * vib * tt))
    e = np.minimum(1, tt / attack) * np.minimum(1, (d - tt) / (d * 0.4))
    return x * e
def crackle(d, density=300):
    x = np.zeros(int(SR * d))
    idx = rng.integers(0, len(x), int(density * d))
    x[idx] = rng.standard_normal(len(idx))
    return highpass(lowpass(x, 6000), 800)
def reverse(x): return x[::-1].copy()

# --- melee ---
save('swing', whoosh(0.18, 600, 3000))
save('swing_heavy', mix(whoosh(0.32, 250, 1800), thump(0.3, 90, 50, 10) * 0.3))
save('hit_light', mix(thump(0.18, 160, 55, 22), highpass(noise(0.05), 1500) * env(int(SR * 0.05), 0.001, 60) * 0.5))
save('hit_heavy', softclip(mix(thump(0.35, 120, 40, 12), lowpass(noise(0.2), 2500) * env(int(SR * 0.2), 0.001, 25) * 0.8), 2))
save('hit_slam', mix(boom(0.8, 80, 28, 0.9, 5), crackle(0.5, 600) * env(int(SR * 0.5), 0.001, 8) * 0.4))
save('block', mix(thump(0.2, 220, 120, 25), chime(0.25, [930, 1410], 18) * 0.25))
save('parry', mix(chime(0.7, [1250, 1870, 2610, 3330], 6, 0.002), whoosh(0.15, 2000, 5000) * 0.4))
save('guard_break', mix(highpass(noise(0.15), 1200) * env(int(SR * 0.15), 0.001, 30), chime(0.6, [1800, 1350, 900], 7) * 0.5, thump(0.4, 140, 45, 10)))
save('dash', whoosh(0.16, 800, 4000))
save('ground_impact', mix(boom(0.7, 70, 25, 1.0, 6), crackle(0.6, 900) * env(int(SR * 0.6), 0.001, 6) * 0.5))
save('heavy_charge', sine_sweep(0.5, 180, 520) * np.linspace(0.1, 1, int(SR * 0.5)) * 0.5 + whoosh(0.5, 300, 2000) * 0.5)
save('no_energy', thump(0.18, 200, 140, 15) * 0.6)

# --- infinity ---
save('infinity_on', mix(pad_chord(0.9, [523, 784, 1046], 0.05) * env(int(SR * 0.9), 0.02, 3), chime(0.9, [2093, 3136], 5) * 0.3))
save('infinity_off', mix(sine_sweep(0.6, 1046, 400) * env(int(SR * 0.6), 0.01, 5), whoosh(0.4, 3000, 400) * 0.3))
tt = t(0.35); wub = np.sin(2 * np.pi * (220 + 90 * np.sin(2 * np.pi * 28 * tt)) * tt) * np.sin(np.linspace(0, np.pi, len(tt))) ** 2
save('infinity_ripple', mix(wub, chime(0.35, [1760, 2640], 10) * 0.2), 0.7)
save('infinity_hold', chime(0.5, [2400, 3600, 4800], 9) * 0.6)

# --- blue ---
save('blue_cast', reverse(whoosh(0.4, 400, 4000)) )
tt = t(1.0); vwomp = sine_sweep(1.0, 260, 40) * env(len(tt), 0.01, 3)
save('blue_spawn', mix(vwomp, reverse(whoosh(0.5, 300, 5000)) * 0.6, pad_chord(1.0, [110, 164.8]) * 0.2))
tt = t(1.6); hum = (np.sin(2 * np.pi * 55 * tt) + 0.6 * np.sin(2 * np.pi * 57.5 * tt) + 0.3 * np.sin(2 * np.pi * 110 * tt)) * (0.7 + 0.3 * np.sin(2 * np.pi * 3 * tt))
save('blue_hum', mix(hum, bandsweep(noise(1.6), 300, 1200) * 0.35 * (0.5 + 0.5 * np.sin(2 * np.pi * 1.5 * tt))) * np.minimum(1, np.minimum(tt / 0.1, (1.6 - tt) / 0.2)))
save('blue_collapse', mix(reverse(whoosh(0.5, 200, 6000)), np.concatenate([np.zeros(int(SR * 0.45)), boom(0.6, 110, 35, 0.5, 7)])))

# --- red ---
tt = t(1.2); whine = sine_sweep(1.2, 300, 1400) * (0.2 + 0.8 * tt / 1.2)
save('red_charge', mix(whine * 0.4, crackle(1.2, 900) * (0.2 + tt / 1.2), whoosh(1.2, 200, 3000) * 0.3))
save('red_fire', mix(highpass(noise(0.08), 2000) * env(int(SR * 0.08), 0.001, 40), sine_sweep(0.3, 1600, 200) * env(int(SR * 0.3), 0.001, 12) * 0.6, thump(0.3, 150, 60, 12)))
save('red_explosion', mix(boom(1.4, 100, 25, 1.0, 4), crackle(1.2, 1500) * env(int(SR * 1.2), 0.001, 4) * 0.6))
save('red_amplified', mix(boom(2.0, 90, 20, 1.0, 3), crackle(1.8, 2000) * env(int(SR * 1.8), 0.001, 3) * 0.6, chime(2.0, [440, 587, 880], 2) * 0.25))

# --- purple ---
save('purple_form', mix(pad_chord(1.0, [196, 293.7, 392], 0.2), chime(1.0, [1568, 2349], 3) * 0.2))
tt = t(1.2); conv = np.sin(2 * np.pi * (330 + 40 * (1 - tt / 1.2)) * tt) + np.sin(2 * np.pi * (330 - 40 * (1 - tt / 1.2)) * tt)
save('purple_fusion', mix(conv * np.minimum(1, tt / 0.8), reverse(whoosh(1.2, 200, 6000)) * 0.6, pad_chord(1.2, [165, 247]) * 0.3))
save('purple_fire', mix(boom(1.8, 70, 18, 1.2, 2.5), whoosh(1.6, 150, 3000) * 0.9, pad_chord(1.6, [98, 146.8]) * 0.3))
tt = t(1.5); roar = bandsweep(noise(1.5), 200, 900) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * tt))
save('purple_travel', roar * np.minimum(1, np.minimum(tt / 0.1, (1.5 - tt) / 0.3)))
save('purple_end', mix(boom(2.6, 60, 16, 1.2, 2), crackle(2.4, 2500) * env(int(SR * 2.4), 0.001, 2.5) * 0.7, delay(chime(1.0, [880, 1320], 4), 0.18, 0.5) * 0.2))

# --- teleport ---
save('teleport', mix(whoosh(0.12, 2000, 8000), chime(0.25, [2637, 3951], 20) * 0.35))

# --- domain ---
tt = t(1.6)
save('domain_charge', mix(pad_chord(1.6, [110, 130.8, 164.8], 0.6) * np.linspace(0.2, 1, len(tt)), reverse(whoosh(1.6, 100, 4000)) * 0.5))
exp_body = mix(boom(2.8, 55, 18, 1.0, 1.5), reverse(whoosh(0.8, 500, 9000)) * 0.6, np.concatenate([np.zeros(int(SR * 0.3)), pad_chord(2.5, [220, 261.6, 329.6, 440], 0.4) * 0.35]))
save('domain_expand', delay(exp_body, 0.23, 0.35, 3))
tt = t(3.0); amb = pad_chord(3.0, [55, 82.4, 110, 164.8], 0.8, 0.3) + 0.3 * chime(3.0, [3520, 4186, 5274], 0.5, 0.01)
save('domain_ambient', amb * 0.8, 0.6)
save('domain_collapse', mix(sine_sweep(1.2, 880, 60) * env(int(SR * 1.2), 0.01, 3), highpass(noise(0.6), 3000) * env(int(SR * 0.6), 0.001, 8) * 0.5, chime(1.0, [1760, 2217, 2637], 4) * 0.3))
g = np.concatenate([np.sin(2 * np.pi * rng.uniform(400, 4000) * t(0.02)) * rng.uniform(0.3, 1) for _ in range(30)])
save('domain_surehit', mix(g, highpass(noise(0.6), 4000) * env(int(SR * 0.6), 0.001, 6) * 0.3), 0.7)
tt = t(1.5); grind = np.sign(np.sin(2 * np.pi * 110 * tt)) * 0.3 + np.sin(2 * np.pi * 116.5 * tt) + np.sin(2 * np.pi * 233 * tt) * 0.5
save('domain_clash', mix(lowpass(grind, 1500) * env(len(tt), 0.05, 2), crackle(1.5, 1200) * 0.5))
# --- awakening / finishers ---
tt = t(3.0)
rise = sine_sweep(1.2, 80, 900) * np.linspace(0, 1, int(SR * 1.2)) ** 2
erupt = mix(boom(2.2, 70, 20, 1.2, 2), reverse(whoosh(0.6, 300, 9000)) * 0.7, pad_chord(2.2, [220, 330, 440, 659], 0.05) * 0.4,
            chime(2.2, [1760, 2637, 3520], 2.5) * 0.25)
save('awaken', delay(np.concatenate([rise * 0.6, erupt]), 0.21, 0.3, 3))
save('awaken_end', mix(sine_sweep(0.9, 660, 180) * env(int(SR * 0.9), 0.01, 4), whoosh(0.6, 3000, 300) * 0.4))
save('finisher', mix(boom(1.6, 90, 22, 1.4, 3), highpass(noise(0.08), 2500) * env(int(SR * 0.08), 0.001, 30),
                      chime(1.4, [1318, 1975, 2637], 3) * 0.35))
save('domain_block', mix(chime(0.4, [180, 271, 405], 9) * 0.8, thump(0.2, 90, 50, 18)))
save('max_charge', mix(pad_chord(1.6, [110, 164.8, 220], 0.3) * np.linspace(0.3, 1, int(SR * 1.6)), crackle(1.6, 2000) * np.linspace(0.1, 1, int(SR * 1.6)) * 0.6,
                      reverse(whoosh(1.6, 150, 7000)) * 0.5))

# --- polish pass: every technique gets its own voice ---
# Red: the charge squeezes down to a point before it releases.
save('red_compress', mix(reverse(whoosh(0.35, 600, 7000)) * 0.8, sine_sweep(0.35, 300, 1800) * np.linspace(0, 1, int(SR * 0.35)) ** 2 * 0.5))
# Teleport: space folding shut, then snapping open.
save('teleport_out', mix(reverse(whoosh(0.18, 1500, 9000)), sine_sweep(0.18, 900, 2400) * env(int(SR * 0.18), 0.01, 10) * 0.3))
save('teleport_in', mix(highpass(noise(0.03), 3000) * env(int(SR * 0.03), 0.001, 80), whoosh(0.2, 6000, 1200) * 0.6, chime(0.3, [1975, 2960], 14) * 0.3))
# Max Blue: a sub-bass drone that feels like the ground is being pulled away.
tt = t(2.0); sub = (np.sin(2 * np.pi * 32 * tt) + 0.8 * np.sin(2 * np.pi * 33.7 * tt) + 0.5 * np.sin(2 * np.pi * 64 * tt)) * (0.75 + 0.25 * np.sin(2 * np.pi * 1.2 * tt))
save('max_blue_hum', mix(softclip(sub, 1.4), bandsweep(noise(2.0), 120, 600) * 0.4) * np.minimum(1, np.minimum(tt / 0.2, (2.0 - tt) / 0.3)))
save('max_blue_collapse', mix(reverse(whoosh(0.9, 100, 8000)), np.concatenate([np.zeros(int(SR * 0.8)), boom(1.8, 70, 18, 1.2, 2.5)]),
                               np.concatenate([np.zeros(int(SR * 0.8)), chime(1.2, [220, 330], 3) * 0.25])))
# Max Red: a catastrophic blast with a long rolling tail.
save('max_red_explosion', delay(mix(boom(2.6, 110, 16, 1.4, 2), crackle(2.4, 3000) * env(int(SR * 2.4), 0.001, 2.5) * 0.7,
                                    highpass(noise(0.1), 2000) * env(int(SR * 0.1), 0.001, 30)), 0.19, 0.35, 3))
# Purple: the two opposites grinding against each other before they fuse.
tt = t(0.9); beat = np.sin(2 * np.pi * 180 * tt) * np.sin(2 * np.pi * 187 * tt) + 0.5 * np.sign(np.sin(2 * np.pi * 90 * tt))
save('purple_collision', mix(lowpass(beat, 2500) * np.linspace(0.3, 1, len(tt)), crackle(0.9, 3000) * np.linspace(0.2, 1, len(tt)) * 0.6))

# --- domain clash ---
tt = t(2.2)
save('clash_start', mix(boom(2.0, 60, 18, 1.2, 2), np.concatenate([reverse(whoosh(0.7, 200, 9000)) * 0.8, np.zeros(int(SR * 1.5))]),
                        highpass(noise(0.12), 2500) * env(int(SR * 0.12), 0.001, 30), pad_chord(2.2, [110, 116.5, 220], 0.02) * 0.35))
save('clash_perfect', mix(chime(0.7, [1568, 2349, 3136], 5, 0.001) * 0.7, whoosh(0.35, 800, 6000) * 0.5, thump(0.3, 180, 60, 12) * 0.6))
save('clash_hit', mix(chime(0.25, [1175, 1760], 16) * 0.6, thump(0.15, 160, 90, 25) * 0.5))
save('clash_miss', mix(thump(0.35, 110, 55, 10), lowpass(noise(0.25), 800) * env(int(SR * 0.25), 0.001, 14) * 0.5,
                       sine_sweep(0.3, 330, 180) * env(int(SR * 0.3), 0.005, 9) * 0.3), 0.7)
save('clash_win', delay(mix(boom(2.4, 80, 16, 1.4, 1.8), chime(2.4, [523, 659, 784, 1046, 1568], 1.8) * 0.4,
                            reverse(whoosh(0.5, 300, 9000)) * 0.6), 0.2, 0.35, 3))
save('clash_beat', thump(0.18, 95, 45, 20), 0.6)
save('clash_countdown', mix(chime(0.35, [880, 1760], 10), thump(0.2, 200, 120, 20) * 0.4), 0.7)
print('generated', len(os.listdir(OUT)), 'sounds')
