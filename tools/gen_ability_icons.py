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

# --- Reserve Balls: three steel pachinko balls in flight with speed ticks. ---
i = Icon()
for (cx, cy, r) in ((4.5, 10.5, 2.6), (8.5, 6.5, 2.2), (12.5, 3.5, 1.8)):
    i.disk(r, CHROME_D, cx, cy)
    i.disk(r - 0.9, CHROME, cx, cy)
    i.set(round(cx - 0.8), round(cy - 0.8), 0xFFFFFF)
for x, y in ((1, 13), (2, 14), (5, 8), (9, 4)):
    i.set(x, y, HOT)
i.outline()
i.save('reserve_balls')

# --- Shutter Doors: two steel shutters sliding shut, with a pink strip and inward arrows. ---
i = Icon()
for x0 in (1, 9):
    for y in range(2, 15):
        for x in range(x0, x0 + 6):
            i.set(x, y, [0xB8C0CC, 0x9AA2AE, 0x7A828E][y % 3])
    for x in range(x0, x0 + 6):
        i.set(x, 13, PINK)
for y in (7, 8):
    i.set(7, y, 0xFFFFFF)
    i.set(8, y, 0xFFFFFF)
i.outline()
i.save('shutter_doors')

# --- Rough Energy: a fist wrapped in jagged, coarse cursed energy. ---
i = Icon()
for k in range(10):
    a = k * math.pi / 5 + 0.3
    r = 7.2 if k % 2 == 0 else 5.2
    i.line(C + math.cos(a) * 2.5, C + math.sin(a) * 2.5, C + math.cos(a) * r, C + math.sin(a) * r, JADE if k % 2 else 0xE8FFF4)
for y in range(5, 11):
    for x in range(5, 11):
        i.set(x, y, 0xE8B894)
for x in range(5, 11):
    i.set(x, 5, 0xC89874)
for y in (7, 9):
    for x in range(6, 10):
        i.set(x, y, 0xB88864)
i.set(10, 8, 0xC89874)
i.outline()
i.save('rough_energy')

# --- Fever Breaker: a spinning kick (a leg sweeping an arc) and a double impact burst. ---
i = Icon()
i.ring(5.2, 6.6, PINK, 7, 8, when=lambda a: -2.6 < a < 0.4)
for k in range(6):
    i.set(3 + k, 12 - k // 2, 0xF4F0F6)
    i.set(3 + k, 13 - k // 2, 0x2A2A38)
for k in range(8):
    a = k * math.pi / 4
    i.line(12 + math.cos(a) * 1, 5 + math.sin(a) * 1, 12 + math.cos(a) * 3.2, 5 + math.sin(a) * 3.2, GOLD if k % 2 else 0xFFFFFF)
i.outline()
i.save('fever_breaker')

# --- Door Guard: the red lacquer gamble door with its gold 7. ---
i = Icon()
for y in range(1, 15):
    for x in range(3, 13):
        edge = x in (3, 12) or y in (1, 14)
        i.set(x, y, GOLD if edge else (0x8A1020 if x in (7, 8) else 0xB01828))
for x, y in ((6, 4), (7, 4), (8, 4), (9, 4), (9, 5), (8, 6), (8, 7), (7, 8), (7, 9), (7, 10)):
    i.set(x, y, 0xFFFFFF)
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

# --- Lucky Volley: a flurry of fists with a gold streak burst. ---
i = Icon()
for (cx, cy) in ((4, 5), (7, 9), (11, 6)):
    for y in range(cy - 2, cy + 2):
        for x in range(cx - 2, cx + 2):
            i.set(x, y, 0xE8B894)
    i.set(cx - 2, cy - 2, 0xC89874)
for x, y in ((1, 12), (2, 13), (13, 12), (14, 11), (8, 2), (9, 1)):
    i.set(x, y, JADE)
i.line(1, 14, 5, 12, GOLD)
i.outline()
i.save('lucky_volley')

# --- Lucky Rushdown: a charging silhouette leaning into a sprint, speed lines behind. ---
i = Icon()
for y in range(3, 14):
    for x in range(7, 11):
        i.set(x + (13 - y) // 4, y, 0x2A2A38)
i.disk(1.8, 0x3A3A48, 11.5, 3.5)
i.set(12, 3, JADE)
for y, x0 in ((6, 1), (9, 0), (12, 2)):
    for x in range(x0, x0 + 5):
        i.set(x, y, JADE if x % 2 else 0xE8FFF4)
i.line(10, 13, 13, 15, 0x2A2A38)
i.outline()
i.save('lucky_rushdown')

# --- Overwhelming Luck: a huge fist over a burst of repeated impacts. ---
i = Icon()
for k in range(12):
    a = k * math.pi / 6
    i.line(C + math.cos(a) * 3, C + math.sin(a) * 3, C + math.cos(a) * 7.5, C + math.sin(a) * 7.5, JADE if k % 3 else GOLD)
for y in range(4, 12):
    for x in range(4, 12):
        i.set(x, y, 0xF0C8A4)
for x in range(4, 12):
    i.set(x, 4, 0xD0A884)
for y in (6, 8, 10):
    for x in range(5, 11):
        i.set(x, y, 0xC89874)
i.outline()
i.save('overwhelming_luck')

# --- Energy Surge: a jade energy bolt splitting into an afterimage and a downward kick arrow. ---
i = Icon()
for (x, y) in ((3, 2), (4, 3), (5, 4), (6, 5), (5, 6), (6, 7), (7, 8), (8, 9)):
    i.set(x, y, JADE)
    i.set(x + 1, y, 0xE8FFF4)
for y in range(8, 15):
    i.set(11, y, 0xE8FFF4)
    i.set(12, y, JADE)
for x, y in ((9, 12), (10, 13), (13, 13), (14, 12)):
    i.set(x, y, JADE)
i.disk(1.3, 0x3A3A48, 12, 5)
i.outline()
i.save('energy_surge')

# --- Rhythm: two beamed eighth notes over a gold beat bar. ---
i = Icon()
for y in range(2, 11):
    i.set(5, y, 0xF4F0F6)
    i.set(12, y - 1, 0xF4F0F6)
for x in range(5, 13):
    i.set(x, 2 + (0 if x < 9 else 0), PINK)
    i.set(x, 3, PINK)
i.disk(1.8, 0xF4F0F6, 3.8, 11)
i.disk(1.8, 0xF4F0F6, 10.8, 10)
for x in range(1, 15):
    i.set(x, 14, GOLD if x % 3 else JADE)
i.outline()
i.save('rhythm')

print('icons written to', os.path.normpath(OUT))
