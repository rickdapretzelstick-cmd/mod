"""Item sprites, models and item definitions for the cursed tools (Slaughter Demon, Cursed Cleaver).

16x16 pixel art, held like a sword (minecraft:item/handheld). Re-run after changing a palette or shape:
    python3 tools/gen_cursed_tool_assets.py
"""
import json
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


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


def sprite(rows, palette):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), palette[ch])
    return img


# Slaughter Demon: a short, slightly curved dark blade with a violet cursed edge, a wrapped grip and a small guard.
SLAUGHTER = [
    "..............E.",
    ".............EBE",
    "............EBD.",
    "...........EBD..",
    "..........EBD...",
    ".........EBD....",
    "........EBD.....",
    ".......EBD......",
    "..G...EBD.......",
    "...G.EBD........",
    "....GGD.........",
    "....WGG.........",
    "...WK..G........",
    "..WK............",
    ".PK.............",
    "PP..............",
]
SLAUGHTER_PAL = {
    'E': hexc('#c38bff'),  # cursed edge
    'B': hexc('#3b2d4a'),  # blade
    'D': hexc('#1d1426'),  # blade spine
    'G': hexc('#8a6a3a'),  # guard
    'W': hexc('#5a1f2a'),  # grip wrap
    'K': hexc('#2a0f16'),  # wrap shadow
    'P': hexc('#c38bff'),  # pommel glint
}

# Cursed Cleaver: a broad, square-ended blade on a long handle, cursed energy seeping along the edge.
CLEAVER = [
    ".........EEEEE..",
    "........EBBBBBE.",
    ".......EBBBBBBDE",
    "......EBBBBBBD.E",
    ".....EBBBBBBD...",
    "......EBBBBD....",
    ".......EBBD.....",
    "......HHBD......",
    ".....HH.........",
    "....WH..........",
    "...WK...........",
    "..WK............",
    ".WK.............",
    "WK..............",
    "P...............",
    "................",
]
CLEAVER_PAL = {
    'E': hexc('#a46bff'),
    'B': hexc('#4a4452'),
    'D': hexc('#24202b'),
    'H': hexc('#6b4a2a'),
    'W': hexc('#3a2618'),
    'K': hexc('#20140c'),
    'P': hexc('#a46bff'),
}

for name, rows, pal in (('slaughter_demon', SLAUGHTER, SLAUGHTER_PAL), ('cursed_cleaver', CLEAVER, CLEAVER_PAL)):
    assert all(len(r) == 16 for r in rows) and len(rows) == 16, name
    save(sprite(rows, pal), 'item', name + '.png')
    write_json({'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'jjk:item/' + name}}, 'models', 'item', name + '.json')
    write_json({'model': {'type': 'minecraft:model', 'model': 'jjk:item/' + name}}, 'items', name + '.json')
print('cursed tool assets written')
