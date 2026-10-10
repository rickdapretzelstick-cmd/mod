"""Cursed Item slot icon, and the Cursed Compass's item frames.

    python3 tools/gen_cursed_kit_assets.py
"""
import json
import math
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')


def save(img, *path):
    p = os.path.join(ROOT, 'textures', *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def write_json(obj, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


# --- The empty Cursed Item slot: a faint blade outline (like the vanilla empty armour icons) ---
SLOT = [
    "................",
    ".............XX.",
    "............X..X",
    "...........X..X.",
    "..........X..X..",
    ".........X..X...",
    "........X..X....",
    ".......X..X.....",
    "...X..X..X......",
    "....XX..X.......",
    "....X.XX........",
    "...X.X.X........",
    "..X.X...X.......",
    ".X.X............",
    "X.X.............",
    ".X..............",
]
icon = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y, row in enumerate(SLOT):
    for x, ch in enumerate(row):
        if ch == 'X':
            icon.putpixel((x, y), (60, 40, 70, 150))
save(icon, 'gui', 'sprites', 'container', 'slot', 'cursed_item.png')
print('slot icon written')
