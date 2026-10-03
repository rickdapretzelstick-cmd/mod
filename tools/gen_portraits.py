"""Generates the 32x32 character emblems for the character select screen. Run: python3 tools/gen_portraits.py

One simple symbol per character, readable at any size (the screen draws them at textures/gui/portrait/<id>.png):
  - gojo: his blindfold
  - hakari: a slot machine
  - yuta: Rika's ring, the cursed ring of their promise
  - ryu: the comb for his pompadour
  - none: a plain grey silhouette (the No kit card)
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'textures', 'gui', 'portrait')
os.makedirs(OUT, exist_ok=True)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


def draw(rows, palette, name):
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((x, y), rgb(palette[ch]))
    img.save(os.path.join(OUT, name + '.png'))


# Gojo: his blindfold — a curved black band (as if worn across the eyes), a knot at the side and two trailing tails.
def gojo():
    import math
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = {}
    for x in range(1, 24):
        cy = 12 + 0.008 * (x - 12) ** 2
        for y in range(32):
            d = y - cy
            if -4 <= d <= 4:
                px[(x, y)] = 0x5A5E70 if d < -3 else 0x0C0C12 if d > 3 else 0x17181F
    # Knot at the side of the head.
    for x in range(22, 29):
        for y in range(8, 20):
            if (x - 25) ** 2 / 9 + (y - 13.5) ** 2 / 25 <= 1:
                px[(x, y)] = 0x2A2C36
    # Two short tails fluttering out behind the knot.
    for i in range(9):
        for w in range(3):
            px[(26 + i // 2 + w, 17 + i)] = 0x1C1D26
            px[(23 + i // 3 + w, 18 + i)] = 0x131419
    px = {k: v for k, v in px.items() if 0 <= k[0] < 32 and 0 <= k[1] < 32}
    for (x, y), c in px.items():
        img.putpixel((x, y), rgb(c))
    # Dark outline for contrast on any background.
    base = img.copy()
    for (x, y) in list(px):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if 0 <= q[0] < 32 and 0 <= q[1] < 32 and q not in px:
                base.putpixel(q, rgb(0x6A6E80))
    base.save(os.path.join(OUT, 'gojo.png'))


gojo()

# Hakari: a slot machine — gold cabinet, three reels showing 7 7 7, a red lever and a pink marquee.
HAKARI = [
    "................................",
    "................................",
    "......OOOOOOOOOOOOOOOOOO........",
    ".....OPPPPPPPPPPPPPPPPPPO.......",
    ".....OPWPPWPPWPPWPPWPPWPO.......",
    ".....OPPPPPPPPPPPPPPPPPPO.......",
    "....OGGGGGGGGGGGGGGGGGGGGO......",
    "....OGDDDDDDDDDDDDDDDDDDGO......",
    "....OGDWWWWWDWWWWWDWWWWWGO.OO...",
    "....OGDWRRRWDWRRRWDWRRRWGO.ORO..",
    "....OGDWWWRWDWWWRWDWWWRWGO.ORO..",
    "....OGDWWRWWDWWRWWDWWRWWGO..OO..",
    "....OGDWWRWWDWWRWWDWWRWWGO..OO..",
    "....OGDWWRWWDWWRWWDWWRWWGO..OO..",
    "....OGDWWWWWDWWWWWDWWWWWGOOOOO..",
    "....OGDDDDDDDDDDDDDDDDDDGGGGO...",
    "....OGGGGGGGGGGGGGGGGGGGGOOO....",
    "....OGgggggggggggggggggggO......",
    "....OGgKKKKKKKKKKKKKKKKKgO......",
    "....OGgggggggggggggggggggO......",
    "....OGGGGGGGGGGGGGGGGGGGGO......",
    "....OGgKKKKKKKKKKKKKKKKKgO......",
    "....OGgggggggggggggggggggO......",
    "...OGGGGGGGGGGGGGGGGGGGGGGO.....",
    "...OOOOOOOOOOOOOOOOOOOOOOOO.....",
]
draw(HAKARI, {'O': 0x140810, 'P': 0xFF3FA0, 'W': 0xF6F2F8, 'G': 0xF0C040, 'g': 0xB08820, 'D': 0x2A1420, 'R': 0xE0102A,
              'K': 0x3A2A10}, 'hakari')
print('emblems written to', os.path.normpath(OUT))


# Yuji: one of Sukuna's cursed fingers — withered, dark, a black claw, bound by a talisman strip.
def yuji():
    import math
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    ax, ay, bx, by = 6, 26, 25, 6
    L = math.hypot(bx - ax, by - ay)
    for y in range(32):
        for x in range(32):
            t = ((x - ax) * (bx - ax) + (y - ay) * (by - ay)) / (L * L)
            if not -0.02 <= t <= 1.02:
                continue
            px_, py_ = ax + (bx - ax) * t, ay + (by - ay) * t
            d = math.hypot(x - px_, y - py_)
            w = 4.2 - 1.2 * t
            if d > w:
                continue
            col = 0x5A2A22 if d < w - 1.2 else 0x2E1410
            # Knuckle creases.
            if abs(t - 0.35) < 0.03 or abs(t - 0.66) < 0.03:
                col = 0x2A120E
            # The claw at the tip.
            if t > 0.88:
                col = 0x101014 if d < w - 0.6 else 0x050506
            # The talisman strip around the base.
            if 0.08 < t < 0.2:
                col = 0xE8DCC0 if d < w - 0.4 else 0xB8A888
                if abs(t - 0.14) < 0.015:
                    col = 0xC01020
            img.putpixel((x, y), rgb(col))
    img.save(os.path.join(OUT, 'yuji.png'))


yuji()


# Yuta: the ring (seen at an angle), silver, with a pink glint of Rika's cursed energy.
def yuta():
    import math
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            dx, dy = (x - 15.5) / 12.5, (y - 16.5) / 8.5
            r = math.hypot(dx, dy)
            if 0.62 <= r <= 1.0:
                top = dy < 0
                lit = math.atan2(dy, dx)
                col = 0xF0F2F8 if (top and -2.4 < lit < -1.0) else 0xC8CCD8 if top else 0x8C92A0 if r > 0.8 else 0xA8AEBC
                img.putpixel((x, y), rgb(col))
            elif 0.56 <= r < 0.62 or 1.0 < r <= 1.08:
                img.putpixel((x, y), rgb(0x2A2A34))
    for (x, y, c) in ((8, 9, 0xFFFFFF), (9, 9, 0xFFC8FF), (8, 10, 0xFFC8FF), (7, 9, 0xF569FF), (8, 8, 0xF569FF), (10, 9, 0xF569FF), (8, 11, 0xF569FF)):
        img.putpixel((x, y), rgb(c))
    for (x, y) in ((25, 22), (26, 21), (24, 23)):
        img.putpixel((x, y), rgb(0xF569FF))
    img.save(os.path.join(OUT, 'yuta.png'))


yuta()


# Ryu: the comb he keeps his pompadour in shape with, a glint of True Cannon's blue on its spine.
def ryu():
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = {}
    # The spine: a long bar with rounded ends, its top edge lit.
    for x in range(3, 29):
        for y in range(8, 14):
            if (x in (3, 28)) and y in (8, 13):
                continue
            px[(x, y)] = 0x5A6478 if y == 8 else 0x2E3440 if y < 12 else 0x1E222C
    # The teeth: one every other column, all the same length but the end ones, which are a little shorter.
    for i, x in enumerate(range(4, 28, 2)):
        length = 9 if 0 < i < 11 else 7
        for y in range(14, 14 + length):
            px[(x, y)] = 0x2E3440 if y < 14 + length - 1 else 0x454D5E
    # True Cannon's glint along the spine.
    for x in range(6, 13):
        px[(x, 9)] = 0xA8D8FF
    px[(7, 10)] = 0x3A86E8
    px[(8, 10)] = 0x3A86E8
    for (x, y) in ((24, 9), (25, 9)):
        px[(x, y)] = 0x7FA6D8
    # A pale outline so it reads on the dark screen.
    for (x, y) in list(px):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if 0 <= q[0] < 32 and 0 <= q[1] < 32 and q not in px:
                img.putpixel(q, rgb(0x8A93A8))
    for (x, y), c in px.items():
        img.putpixel((x, y), rgb(c))
    img.save(os.path.join(OUT, 'ryu.png'))


ryu()


# No kit: a plain grey silhouette, an ordinary person with no technique.
def none():
    import math
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = {}
    for y in range(32):
        for x in range(32):
            head = math.hypot(x - 15.5, y - 10) <= 5.2
            shoulders = y >= 18 and y <= 29 and abs(x - 15.5) <= 4 + (y - 18) * 0.75 and math.hypot((x - 15.5) / 11, (y - 30) / 12) <= 1
            if head or shoulders:
                px[(x, y)] = 0x9A9AA6 if (head and y < 8) or (shoulders and y < 21) else 0x6E6E7A
    for (x, y) in list(px):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if 0 <= q[0] < 32 and 0 <= q[1] < 32 and q not in px:
                img.putpixel(q, rgb(0x2A2A32))
    for (x, y), c in px.items():
        img.putpixel((x, y), rgb(c))
    img.save(os.path.join(OUT, 'none.png'))


none()
