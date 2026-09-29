"""Generates the 16x16 pixel-art ability icons for the combat HUD. Run: python3 tools/gen_ability_icons.py

Every icon is drawn on the same 16x16 grid with hard pixels (no anti-aliasing), a dark 1px outline around the shape
for contrast against any background, and a small shared palette per technique, so they read like Minecraft items.
"""
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'textures', 'gui', 'ability')
os.makedirs(OUT, exist_ok=True)
N = 16
C = 7.5  # centre of the grid


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


class Icon:
    def __init__(self):
        self.px = [[None] * N for _ in range(N)]

    def set(self, x, y, c):
        if 0 <= x < N and 0 <= y < N and c is not None:
            self.px[y][x] = rgb(c) if isinstance(c, int) else c

    def disk(self, r, c, cx=C, cy=C):
        for y in range(N):
            for x in range(N):
                if math.hypot(x - cx, y - cy) <= r:
                    self.set(x, y, c)

    def ring(self, r0, r1, c, cx=C, cy=C, when=lambda a: True):
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - cx, y - cy)
                if r0 <= d <= r1 and when(math.atan2(y - cy, x - cx)):
                    self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(steps + 1):
            t = i / max(1, steps)
            self.set(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)

    def outline(self, c=0x0A0A14):
        """A 1px dark border around every drawn pixel, so the silhouette reads on any background."""
        filled = [[p is not None for p in row] for row in self.px]
        for y in range(N):
            for x in range(N):
                if filled[y][x]:
                    continue
                if any(0 <= x + dx < N and 0 <= y + dy < N and filled[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    self.set(x, y, c)

    def save(self, name):
        img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        for y in range(N):
            for x in range(N):
                if self.px[y][x]:
                    img.putpixel((x, y), self.px[y][x])
        img.save(os.path.join(OUT, name + '.png'))


# --- Lapse Blue: a compact sphere with specks spiralling into it (attraction). ---
i = Icon()
for k in range(4):
    a = k * math.pi / 2 + 0.5
    for s, (r, col) in enumerate(((6.6, 0x2E6BFF), (5.4, 0x6FB6FF))):
        i.set(round(C + math.cos(a + s * 0.35) * r), round(C + math.sin(a + s * 0.35) * r), col)
i.disk(3.6, 0x1E4FD8)
i.disk(2.6, 0x3E8BFF)
i.disk(1.5, 0x9FD8FF)
i.set(6, 6, 0xFFFFFF)
i.set(7, 6, 0xE8F6FF)
i.outline()
i.save('blue')

# --- Reversal Red: a hot core throwing spikes outward (repulsion). ---
i = Icon()
for k in range(8):
    a = k * math.pi / 4
    r1 = 7.2 if k % 2 == 0 else 5.6
    i.line(C + math.cos(a) * 3, C + math.sin(a) * 3, C + math.cos(a) * r1, C + math.sin(a) * r1, 0xFF3B30 if k % 2 == 0 else 0xFF8A4A)
i.disk(3.0, 0xC8141E)
i.disk(2.1, 0xFF3B30)
i.disk(1.1, 0xFFD0B0)
i.set(7, 7, 0xFFFFFF)
i.outline()
i.save('red')

# --- Lapse Blue: MAX: a black hole with a bright accretion ring filling the frame. ---
i = Icon()
i.ring(5.2, 7.6, 0x1E4FD8)
i.ring(5.4, 7.4, 0x3E8BFF, when=lambda a: math.sin(a * 2) > -0.2)
i.ring(6.0, 6.8, 0xBFE6FF, when=lambda a: math.sin(a * 2 + 0.6) > 0.55)
i.ring(3.6, 5.2, 0x6FB6FF)
i.ring(4.2, 4.8, 0xE8F6FF, when=lambda a: math.cos(a) > 0.2)
i.disk(3.5, 0x04040C)
i.set(6, 6, 0x2A2A48)
i.outline()
i.save('max_blue')

# --- Reversal Red: MAX: a violent full-frame burst with jagged spikes and a white-hot core. ---
i = Icon()
for k in range(12):
    a = k * math.pi / 6 + 0.13
    r1 = 8.0 if k % 3 == 0 else 6.6 if k % 3 == 1 else 5.4
    i.line(C + math.cos(a) * 2.5, C + math.sin(a) * 2.5, C + math.cos(a) * r1, C + math.sin(a) * r1, 0xFF3B30 if k % 2 == 0 else 0xFF7A1E)
i.ring(3.6, 4.6, 0xFF8A4A)
i.disk(3.6, 0xE0202A)
i.disk(2.6, 0xFF6A3A)
i.disk(1.6, 0xFFE0A0)
i.disk(0.8, 0xFFFFFF)
i.outline()
i.save('max_red')

# --- Hollow Purple: blue and red colliding into a purple void orb, with the beam it fires. ---
i = Icon()
for x in range(0, 16):
    y = 15 - x
    for w in (-1, 0, 1):
        i.set(x + w, y, 0x5A1E9A)
i.disk(5.0, 0x6A22C8, 8.5, 7.5)
i.disk(3.8, 0xA24DFF, 8.5, 7.5)
i.disk(2.4, 0xD9A8FF, 8.5, 7.5)
i.disk(1.2, 0xFFFFFF, 8.5, 7.5)
i.disk(1.8, 0x3E8BFF, 3.2, 3.2)
i.set(3, 3, 0xBFE6FF)
i.disk(1.8, 0xFF3B30, 13.2, 12.0)
i.set(13, 12, 0xFFD0B0)
i.outline()
i.save('hollow_purple')

# --- Unlimited Void (domain): a sealed starfield dome over the ground line. ---
i = Icon()
for y in range(N):
    for x in range(N):
        if y <= 12 and math.hypot(x - C, (y - 12) * 1.12) <= 7.4:
            i.set(x, y, 0x0C0A22)
i.ring(6.6, 7.6, 0xE8F0FF, cy=12, when=lambda a: a < 0.05)
for x, y in ((5, 6), (9, 5), (11, 9), (4, 10), (7, 9), (8, 3)):
    i.set(x, y, 0xFFFFFF)
for x, y in ((6, 8), (10, 7), (3, 8), (12, 11)):
    i.set(x, y, 0x9F7BFF)
for x in range(0, 16):
    i.set(x, 13, 0x6FB6FF)
i.set(7, 11, 0x9FD8FF)
i.set(8, 11, 0x9FD8FF)
i.outline()
i.save('unlimited_void')

# --- Teleport: vanishing from one point (a fading ring) and snapping open at another, joined by a dotted jump arc. ---
i = Icon()
i.ring(1.4, 2.3, 0x3A5A86, 3.5, 11.5)
i.set(3, 11, 0x2E4A70)
for k, (x, y) in enumerate(((5, 9), (6, 7), (7, 5), (9, 4))):
    i.set(x, y, 0x6FD8FF if k % 2 else 0x9FE7FF)
i.ring(2.2, 3.4, 0x6FD8FF, 11.5, 7.5)
i.ring(2.6, 3.0, 0xE8F6FF, 11.5, 7.5, when=lambda a: math.sin(a) < 0.3)
i.disk(1.4, 0xFFFFFF, 11.5, 7.5)
for x, y in ((11, 2), (15, 7), (11, 13), (8, 10)):
    i.set(x, y, 0xBFE6FF)
i.outline()
i.save('teleport')

# --- Awaken: the Six Eyes, blazing blue under white hair. ---
i = Icon()
for y in range(N):
    for x in range(N):
        if abs(y - 8) <= (1 - ((x - C) / 7.4) ** 2) * 4.2 and 0 <= x < 16:
            i.set(x, y, 0xF4F8FF)
i.disk(3.0, 0x2E6BFF, 7.5, 8)
i.disk(2.2, 0x6FD8FF, 7.5, 8)
i.disk(1.0, 0x0A1030, 7.5, 8)
i.set(6, 7, 0xFFFFFF)
for x in range(2, 14):
    i.set(x, 2 + (1 if x % 3 == 0 else 0), 0xE8EEF8)
    i.set(x, 3 + (1 if x % 4 == 1 else 0), 0xC8D2E4)
i.outline()
i.save('awaken')

# --- Dash: chevrons with speed lines. ---
i = Icon()
for k, col in ((0, 0x8A94A8), (4, 0xE8EEF8)):
    for d in range(4):
        i.set(5 + k + d, 4 + d, col)
        i.set(5 + k + d, 11 - d, col)
for y, x0 in ((5, 0), (8, 1), (10, 0)):
    for x in range(x0, x0 + 4):
        i.set(x, y, 0x5A6478)
i.outline()
i.save('dash')

# --- Guard: a shield. ---
i = Icon()
for y in range(2, 15):
    half = 6 if y < 9 else max(0, 6 - (y - 8))
    for x in range(round(C - half), round(C + half) + 1):
        i.set(x, y, 0x7A8498 if abs(x - C) > half - 1.5 or y == 2 else 0xB8C4D8)
for y in range(3, 13):
    i.set(7, y, 0xE8EEF8)
    i.set(8, y, 0xE8EEF8)
i.outline()
i.save('guard')

# --- Infinity (kept for when it returns to the moveset): the infinity sign in pale blue. ---
i = Icon()
for t in range(200):
    a = t / 200 * 2 * math.pi
    x = C + 6.2 * math.cos(a) / (1 + math.sin(a) ** 2)
    y = C + 6.2 * math.sin(a) * math.cos(a) / (1 + math.sin(a) ** 2)
    i.set(round(x), round(y), 0xCFE8FF)
    i.set(round(x), round(y) + 1, 0x7FB8FF)
i.outline()
i.save('infinity')

# ===================== Hakari =====================
# Casino palette: black, white, hot pink, red, gold, pachinko chrome, and Jackpot green.
PINK, HOT, GOLD, CHROME, CHROME_D, JADE = 0xFF3FA0, 0xFF7FC0, 0xF0C040, 0xD8DEE8, 0x8A94A4, 0x5CFFA8

# Hakari's icons are one bold symbol each, readable at a glance (the domain icon below is the exception: kept as is).

def fist(i, x0, y0, w=11, h=10, col=0xF0CCA8, shade=0xC89A74):
    """A clenched fist from the knuckle side: a rounded block, four finger lines, and the thumb across the bottom."""
    for y in range(h):
        for x in range(w):
            if (x in (0, w - 1)) and (y in (0, h - 1)):
                continue
            i.set(x0 + x, y0 + y, col)
    fh = h * 5 // 10
    for k in range(1, 4):
        for y in range(0, fh):
            i.set(x0 + round(k * w / 4), y0 + y, shade)
    for x in range(1, w * 2 // 3):
        i.set(x0 + x, y0 + fh, shade)
    for y in range(fh, h - 1):
        i.set(x0 + w * 2 // 3, y0 + y, shade)


# --- Reserve Balls: one steel ball. ---
i = Icon()
i.disk(6.2, CHROME_D)
i.disk(5.2, CHROME)
i.disk(2.2, 0xFFFFFF, 5.5, 5.5)
i.set(10, 10, CHROME_D)
i.outline()
i.save('reserve_balls')

# --- Shutter Doors: two doors side by side. ---
i = Icon()
for x0 in (1, 9):
    for y in range(2, 15):
        for x in range(x0, x0 + 6):
            i.set(x, y, 0xC8D0DC if (y - 2) % 3 else 0x8A94A4)
i.outline()
i.save('shutter_doors')

# --- Rough Energy: a fist. ---
i = Icon()
fist(i, 2, 3, 12, 11)
i.outline()
i.save('rough_energy')

# --- Fever Breaker: a kicking leg — thigh to shin to a boot, thrust forward. ---
i = Icon()
for k in range(10):
    for w in range(4):
        i.set(1 + k, 2 + k // 2 + w, 0x2A2A38 if w < 3 else 0x3E3E50)
for y in range(6, 13):
    for x in range(10, 15):
        if not (x == 14 and y == 6):
            i.set(x, y, 0xF4F0F6 if y < 11 else PINK)
for y in range(8, 12):
    i.set(15, y, 0xF4F0F6 if y < 11 else PINK)
i.outline()
i.save('fever_breaker')

# --- Door Guard: a door with a shield on it. ---
i = Icon()
for y in range(1, 15):
    for x in range(2, 13):
        i.set(x, y, 0xB01828 if 3 <= x <= 11 and 2 <= y <= 13 else GOLD)
for y in range(5, 14):
    half = 3 if y < 10 else max(0, 3 - (y - 9))
    for x in range(7 - half, 8 + half):
        i.set(x, y, 0xF4F0F6)
i.set(12, 8, GOLD)
i.outline()
i.save('door_guard')

# --- Idle Death Gamble: a slot window showing 7 7 7 under a pink neon arch. ---
i = Icon()
i.ring(6.4, 7.5, PINK, 7.5, 8.5, when=lambda a: a < 0.05)
for y in range(5, 13):
    for x in range(0, 16):
        i.set(x, y, GOLD)
for x0 in (1, 6, 11):
    for y in range(6, 12):
        for x in range(x0, x0 + 4):
            i.set(x, y, 0xF4F0F6)
    for x, y in ((0, 0), (1, 0), (2, 0), (3, 0), (3, 1), (2, 2), (2, 3), (1, 4), (1, 5)):
        i.set(x0 + x, 6 + y, 0xE0102A)
i.set(7, 2, 0xFFFFFF)
i.set(8, 2, 0xFFFFFF)
i.outline()
i.save('idle_death_gamble')

# --- Lucky Volley: a fist with motion lines. ---
i = Icon()
fist(i, 5, 3, 10, 9)
for y, x0 in ((4, 0), (7, 1), (10, 0)):
    for x in range(x0, x0 + 4):
        i.set(x, y, JADE)
i.outline()
i.save('lucky_volley')

# --- Lucky Rushdown: a forward charge arrow. ---
i = Icon()
for y in range(6, 10):
    for x in range(1, 9):
        i.set(x, y, JADE)
for k in range(7):
    for y in range(8 - k, 8 + k):
        i.set(8 + (6 - k), y, JADE)
for y in (4, 11):
    for x in range(1, 5):
        i.set(x, y, 0xE8FFF4)
i.outline()
i.save('lucky_rushdown')

# --- Overwhelming Luck: a big fist and an impact star. ---
i = Icon()
for k in range(8):
    a = k * math.pi / 4 + 0.39
    i.line(12 + math.cos(a) * 1, 4 + math.sin(a) * 1, 12 + math.cos(a) * 3.6, 4 + math.sin(a) * 3.6, GOLD)
fist(i, 0, 5, 11, 10)
i.outline()
i.save('overwhelming_luck')

# --- Energy Surge: a lightning bolt. ---
i = Icon()
bolt = [(9, 1), (8, 2), (7, 3), (6, 4), (5, 5), (4, 6), (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (9, 8), (8, 9), (7, 10), (6, 11),
        (5, 12), (4, 13), (3, 14)]
for (x, y) in bolt:
    for w in range(3):
        i.set(x + w, y, JADE if w < 2 else 0xE8FFF4)
i.outline()
i.save('energy_surge')

# --- Rhythm: a music note. ---
i = Icon()
for y in range(2, 12):
    i.set(9, y, 0xF4F0F6)
    i.set(10, y, 0xF4F0F6)
for k, y in enumerate(range(2, 6)):
    for x in range(11, 14 - k // 2):
        i.set(x, y + k // 2, GOLD)
i.disk(2.6, 0xF4F0F6, 7.2, 12)
i.outline()
i.save('rhythm')

print('icons written to', os.path.normpath(OUT))

# --- Gojo: Rapid Punches — a fist with a blur of fists behind it. ---
i = Icon()
for k, x0 in enumerate((0, 3)):
    for y in range(5, 11):
        for x in range(x0, x0 + 3):
            i.set(x, y, 0x3A6FB8 if k == 0 else 0x6FA8FF)
fist(i, 6, 3, 9, 10)
i.outline()
i.save('rapid_punches')

# --- Gojo: Twofold Kick — a leg kicking straight up, two arcs over the boot. ---
i = Icon()
for y in range(4, 15):
    for x in range(6, 10):
        i.set(x, y, 0x20202C if x < 9 else 0x34344A)
for y in range(1, 5):
    for x in range(5, 11):
        i.set(x, y, 0xF4F4F8 if y > 1 else 0x8FC8FF)
i.ring(5.5, 6.3, 0x8FC8FF, 7.5, 7.5, when=lambda a: -2.6 < a < -0.5)
i.ring(3.3, 4.0, 0xCFE8FF, 7.5, 7.5, when=lambda a: -2.6 < a < -0.5)
i.outline()
i.save('twofold_kick')

# --- Yuji / Vessel: cyan cursed energy, red eyes; Sukuna: white slashes, black cores, red. ---
CE, CE_L, BFR, INK_, BLOOD_ = 0x5CE6FF, 0xCCF6FF, 0xE01020, 0x0A0A10, 0x9A0A14
SKIN = 0xF0CCA8

# Cursed Strikes: a fist sliding in behind two red streaks.
i = Icon()
for y in (6, 9):
    i.line(0, y, 6, y, BFR)
fist(i, 6, 3, 9, 9)
i.outline()
i.save('cursed_strikes')

# Crushing Blow: a cyan-charged fist driving down into cracked ground.
i = Icon()
fist(i, 3, 1, 10, 8)
for x in range(2, 14):
    i.set(x, 3, None)
i.ring(0, 2.2, CE, 8, 6, when=lambda a: True)
fist(i, 3, 1, 10, 8, CE_L, CE)
for x in range(0, 16):
    i.set(x, 13, 0x5A5A66)
for (x0, x1) in ((3, 6), (9, 13)):
    i.line(x0, 12, x1, 15, 0x2A2A34)
i.line(8, 10, 8, 14, CE)
i.outline()
i.save('crushing_blow')

# Divergent Fist: a fist with its cyan afterimage lagging behind.
i = Icon()
fist(i, 1, 4, 9, 8, CE, 0x2A9AC8)
fist(i, 6, 3, 9, 9)
i.outline()
i.save('divergent_fist')

# Manji Kick: a leg swung up in a white arc.
i = Icon()
i.ring(5.6, 6.6, 0xF4F4FA, when=lambda a: -2.6 < a < 0.3)
for k in range(8):
    for w in range(3):
        i.set(3 + k, 13 - k + w, 0x2A2A38)
for y in range(3, 7):
    for x in range(10, 14):
        i.set(x, y, 0xF4F4FA)
i.outline()
i.save('manji_kick')

# Combat Instincts: the crackling white ring of a feint.
i = Icon()
i.ring(5.4, 6.6, 0xF4F4FA, when=lambda a: math.sin(a * 5) > -0.6)
i.ring(6.6, 7.4, 0xA8B0C0, when=lambda a: math.sin(a * 5 + 1) > 0.4)
i.line(7, 1, 8, 4, 0xFFFFFF)
i.line(12, 11, 14, 13, 0xFFFFFF)
fist(i, 5, 6, 6, 5)
i.outline()
i.save('combat_instincts')

# King of Curses: Sukuna's face, the marks and four red eyes.
i = Icon()
for y in range(2, 15):
    for x in range(3, 13):
        i.set(x, y, SKIN)
for x in range(3, 13):
    i.set(x, 1, 0xE88A9A)
    i.set(x, 2, 0xE88A9A)
for x in (5, 6, 9, 10):
    i.set(x, 6, BFR)
    i.set(x, 9, BFR)
for x in range(3, 5):
    i.set(x, 10, INK_)
    i.set(x + 8, 10, INK_)
i.line(6, 4, 9, 4, INK_)
for x in range(6, 10):
    i.set(x, 12, INK_)
i.outline()
i.save('king_of_curses')

# Cleave: a grip and the crossing cuts through it.
i = Icon()
i.line(2, 2, 13, 13, 0xFFFFFF)
i.line(13, 2, 2, 13, 0xFFFFFF)
i.line(2, 5, 10, 13, BFR)
i.line(5, 2, 13, 10, BFR)
i.disk(1.2, INK_)
i.outline()
i.save('cleave')

# Dismantle: a white crescent with a black core.
i = Icon()
i.ring(5.0, 7.4, 0xFFFFFF, when=lambda a: -2.3 < a < 0.9)
i.ring(5.6, 6.8, INK_, when=lambda a: -2.0 < a < 0.6)
for (x, y) in ((4, 12), (6, 13), (3, 10)):
    i.set(x, y, BLOOD_)
i.outline()
i.save('dismantle')

# Open: an arrow of fire.
i = Icon()
i.line(1, 14, 12, 3, 0xFF8A20)
i.line(2, 14, 13, 3, 0xFFD060)
i.line(1, 13, 12, 2, 0xFF8A20)
for (x, y) in ((13, 1), (14, 2), (12, 1), (14, 3), (11, 2)):
    i.set(x, y, 0xFFF0A0)
for (x, y) in ((0, 11), (3, 15), (1, 15), (0, 13)):
    i.set(x, y, 0xE04010)
i.outline()
i.save('open')

# Rush: a knee driving up out of speed lines, red aura.
i = Icon()
for y in (4, 8, 12):
    i.line(0, y, 5, y, 0xA8B0C0)
for k in range(6):
    for w in range(3):
        i.set(7 + k // 2 + w, 13 - k, 0x2A2A38)
for y in range(4, 8):
    for x in range(9, 14):
        i.set(x, y, 0x2A2A38)
i.ring(6.5, 7.5, BLOOD_, when=lambda a: a < -0.5)
i.outline()
i.save('rush')

# Malevolent Shrine: the shrine — the roof, the red pillars, the mouth between them.
i = Icon()
for y in range(2, 6):
    for x in range(1 + (5 - y), 15 - (5 - y)):
        i.set(x, y, 0x3E7A52 if y < 4 else 0xC8662A)
for y in range(6, 15):
    for x in (3, 4, 11, 12):
        i.set(x, y, BFR)
for y in range(8, 14):
    for x in range(5, 11):
        i.set(x, y, 0xF4C0C8 if y in (8, 13) else INK_)
for x in range(5, 11, 2):
    i.set(x, 9, 0xFFFFFF)
    i.set(x + 1, 12, 0xFFFFFF)
i.outline()
i.save('malevolent_shrine')
