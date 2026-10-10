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


# --- The Cursed Compass: a darkened compass, violet rim, a pale-blue needle; 32 frames like the vanilla compass ---
# Frame 0 points straight up (ahead), frames go clockwise; the item model maps the jjk:cursed_compass property the same
# way the vanilla compass maps its own (0.5 = ahead = frame 0).
random.seed(23)
CASE = (34, 30, 42)
RIM_DARK, RIM_LIGHT = (70, 40, 110), (130, 80, 200)
FACE = (18, 16, 26)


def compass_base():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 7.2:
                if d > 6.0:
                    # The rim: lit from the top left.
                    lit = (x - cx) + (y - cy) < 0
                    c = RIM_LIGHT if lit else RIM_DARK
                elif d > 5.2:
                    c = CASE
                else:
                    n = random.randint(-3, 3)
                    c = tuple(max(0, v + n) for v in FACE)
                img.putpixel((x, y), c + (255,))
    # Four tick marks, faint violet; a dim glyph at the top.
    for x, y in ((7, 3), (8, 3), (12, 7), (12, 8), (7, 12), (8, 12), (3, 7), (3, 8)):
        img.putpixel((x, y), (90, 60, 140, 255))
    return img


BASE = compass_base()
for f in range(32):
    img = BASE.copy()
    a = f / 32 * 2 * math.pi
    dx, dy = math.sin(a), -math.cos(a)
    # The tail (dull) then the head (bright pale blue with a violet tip).
    for i in range(-3, 6):
        t = i * 0.8
        px, py = round(7.5 + dx * t), round(7.5 + dy * t)
        if 0 <= px < 16 and 0 <= py < 16:
            if i < 0:
                c = (70, 70, 96)
            elif i >= 4:
                c = (190, 130, 255)
            else:
                c = (140, 200, 255)
            img.putpixel((px, py), c + (255,))
    img.putpixel((7, 7), (230, 230, 240, 255))
    save(img, 'item', 'cursed_compass_%02d.png' % f)
    write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jjk:item/cursed_compass_%02d' % f}},
               'models', 'item', 'cursed_compass_%02d.json' % f)
entries = [{'threshold': 0.0, 'model': {'type': 'minecraft:model', 'model': 'jjk:item/cursed_compass_16'}}]
for k in range(1, 32):
    entries.append({'threshold': k - 0.5, 'model': {'type': 'minecraft:model', 'model': 'jjk:item/cursed_compass_%02d' % ((16 + k) % 32)}})
entries.append({'threshold': 31.5, 'model': {'type': 'minecraft:model', 'model': 'jjk:item/cursed_compass_16'}})
write_json({'model': {'type': 'minecraft:range_dispatch', 'property': 'jjk:cursed_compass', 'scale': 32.0, 'entries': entries}},
           'items', 'cursed_compass.json')
print('cursed compass written')
