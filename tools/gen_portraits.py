"""Generates the 32x32 character emblems for the character select screen. Run: python3 tools/gen_portraits.py

One simple symbol per character, readable at any size (the screen draws them at textures/gui/portrait/<id>.png):
  - gojo: his blindfold
  - hakari: a slot machine
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
