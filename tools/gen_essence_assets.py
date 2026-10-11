#!/usr/bin/env python3
"""Item icons for the Grade 1 essence step: the Exorcism Seal (a paper talisman, inked, gold-bound) and the Sealed Grade 1
Essence (a dark violet knot of energy held in a small inked seal). Writes the textures, item models and item definitions.
Re-run after changing it; never hand-edit the outputs."""
import json, math, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')


def save(img, name):
    p = os.path.join(ROOT, 'textures', 'item', name + '.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def write_json(obj, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def item(name):
    write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jjk:item/' + name}}, 'models', 'item', name + '.json')
    write_json({'model': {'type': 'minecraft:model', 'model': 'jjk:item/' + name}}, 'items', name + '.json')


PAPER, PAPER_DARK = (236, 226, 196, 255), (198, 184, 150, 255)
INK, INK_SOFT = (24, 18, 30, 255), (70, 50, 80, 255)
GOLD, GOLD_DARK = (240, 196, 70, 255), (170, 120, 30, 255)
RED = (190, 40, 50, 255)


def seal():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    # A tall paper strip, slightly tilted by its shading; a gold band top and bottom.
    for y in range(1, 15):
        for x in range(5, 11):
            img.putpixel((x, y), PAPER_DARK if x == 10 or y == 14 else PAPER)
    for x in range(5, 11):
        img.putpixel((x, 2), GOLD)
        img.putpixel((x, 13), GOLD_DARK)
    # Inked script down the middle, a red stamp near the bottom.
    for y, row in zip(range(4, 11), ['..##', '.#..', '..#.', '###.', '.#.#', '..#.', '.##.']):
        for i, c in enumerate(row):
            if c == '#':
                img.putpixel((6 + i, y), INK if y % 2 else INK_SOFT)
    for x, y in ((7, 11), (8, 11), (7, 12), (8, 12)):
        img.putpixel((x, y), RED)
    return img


def essence():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            ang = math.atan2(y - cy, x - cx)
            # A swirl: brighter along two spiral arms, dark at the core.
            arm = (math.sin(ang * 2 + d * 1.3) + 1) / 2
            if d <= 6.2:
                v = 1 - d / 6.2
                r = int(40 + 120 * arm * v + 30 * v)
                g = int(10 + 40 * arm * v)
                b = int(70 + 170 * arm * v + 20 * v)
                if d < 1.6:
                    r, g, b = 14, 6, 22
                img.putpixel((x, y), (min(255, r), min(255, g), min(255, b), 255))
            elif d <= 7.2:
                # The seal's inked ring round it.
                img.putpixel((x, y), INK if (x + y) % 3 else GOLD_DARK)
    for x, y in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        img.putpixel((x, y), GOLD)
    return img


save(seal(), 'exorcism_seal')
item('exorcism_seal')
save(essence(), 'sealed_grade1_essence')
item('sealed_grade1_essence')
print('essence assets written')
