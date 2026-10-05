"""The hunting lodge's blocks and the Cursed Rifle's item definition.

    python3 tools/gen_lodge_assets.py

- gun rack: three states (sealed with chain and a paper talisman; open with the Cursed Rifle resting on its pegs, built
  from boxes in the rifle's own colours; empty), four facings;
- mounted scope: a rifle scope on a small swivel stand (the lodge's windowsill prop);
- the Cursed Rifle: a flat 16x16 icon for inventories, the ground and frames, and its held model (the Blockbench model,
  drawn by the jjk:cursed_rifle special renderer and posed by its clips) everywhere else.
"""
import json
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')
random.seed(11)


def write_json(obj, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def save(img, *path):
    p = os.path.join(ROOT, 'textures', *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def noisy(base, spread, size=16, alpha=255):
    img = Image.new('RGBA', (size, size))
    for y in range(size):
        for x in range(size):
            n = random.randint(-spread, spread)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (alpha,))
    return img


# --- Textures ---
metal = noisy((52, 54, 64), 8)
for x in range(16):
    metal.putpixel((x, 3), (82, 86, 98, 255))
save(metal, 'block', 'rifle_metal.png')
wood = noisy((92, 58, 36), 10)
for y in range(0, 16, 3):
    for x in range(16):
        if random.random() < 0.5:
            r, g, b, a = wood.getpixel((x, y))
            wood.putpixel((x, y), (r - 18, g - 12, b - 8, a))
save(wood, 'block', 'rifle_wood.png')
bone = noisy((196, 188, 170), 9)
save(bone, 'block', 'rifle_bone.png')
lens = noisy((120, 90, 230), 14)
for x, y in ((5, 5), (6, 5), (5, 6)):
    lens.putpixel((x, y), (230, 220, 255, 255))
save(lens, 'block', 'rifle_lens.png')
glass = noisy((90, 140, 170), 10)
glass.putpixel((5, 5), (230, 245, 255, 255))
save(glass, 'block', 'scope_glass.png')
# A paper talisman: off-white, a red border, ink strokes down it.
tal = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16):
        v = 222 + random.randint(-8, 4)
        tal.putpixel((x, y), (v, v - 8, v - 30, 255))
for i in range(16):
    for x, y in ((0, i), (15, i), (i, 0), (i, 15)):
        tal.putpixel((x, y), (150, 30, 30, 255))
for y in range(3, 13):
    for x in (6, 7, 9):
        if random.random() < 0.75:
            tal.putpixel((x, y), (40, 20, 30, 255))
save(tal, 'block', 'rack_talisman.png')
chain = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for x in range(16):
    for y in (6, 7, 8, 9):
        link = (x // 3) % 2
        if link == 0 and y in (6, 9) or link == 1 and y in (7, 8):
            chain.putpixel((x, y), (96, 92, 88, 255) if (x + y) % 4 else (140, 136, 128, 255))
save(chain, 'block', 'rack_chain.png')

# The rifle's icon: a long dark rifle, a pale folded arm along the barrel, a violet lens at the muzzle.
ICON = [
    "................",
    "................",
    "................",
    "................",
    "..........SS....",
    "LBBBBBBBBRRRWWW.",
    "LMMMMMMMMRRRWWWW",
    ".AAAAAAA.RGRWWWW",
    ".........G...WWW",
    "........GG......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
PAL = {'B': (60, 62, 72, 255), 'M': (40, 42, 50, 255), 'R': (70, 72, 84, 255), 'S': (24, 24, 28, 255), 'W': (104, 66, 40, 255),
       'G': (30, 30, 34, 255), 'A': (200, 192, 172, 255), 'L': (150, 110, 255, 255)}
icon = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y, row in enumerate(ICON):
    for x, ch in enumerate(row):
        if ch in PAL:
            icon.putpixel((x, y), PAL[ch])
# Tilt it: shift rows so it reads as held at an angle, like the other weapons' icons.
tilted = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y in range(16):
    for x in range(16):
        p = icon.getpixel((x, y))
        if p[3]:
            ny = y + (8 - x) // 3
            if 0 <= ny < 16:
                tilted.putpixel((x, ny), p)
save(tilted, 'item', 'cursed_rifle.png')


# --- Models ---
def box(frm, to, tex, rot=None, faces=('north', 'south', 'east', 'west', 'up', 'down')):
    e = {'from': frm, 'to': to, 'faces': {f: {'texture': tex} for f in faces}}
    if rot:
        e['rotation'] = rot
    return e


TEX = {'particle': 'minecraft:block/spruce_planks', 'board': 'minecraft:block/spruce_planks', 'peg': 'minecraft:block/dark_oak_log',
       'metal': 'jjk:block/rifle_metal', 'wood': 'jjk:block/rifle_wood', 'bone': 'jjk:block/rifle_bone', 'lens': 'jjk:block/rifle_lens',
       'talisman': 'jjk:block/rack_talisman', 'chain': 'jjk:block/rack_chain'}
frame = [
    box([1, 1, 14.5], [15, 15, 16], '#board'),
    box([1, 14, 13.5], [15, 15, 14.5], '#board'),
]
for x0 in (2.5, 12.5):
    for y0 in (5, 9):
        frame.append(box([x0, y0, 11.5], [x0 + 1, y0 + 1, 14.5], '#peg'))
sealed = frame + [
    box([0.5, 7.6, 13.4], [15.5, 8.4, 13.8], '#chain', {'angle': 45, 'axis': 'z', 'origin': [8, 8, 13.6]}),
    box([0.5, 7.6, 13.0], [15.5, 8.4, 13.4], '#chain', {'angle': -45, 'axis': 'z', 'origin': [8, 8, 13.2]}),
    box([6, 4.5, 12.6], [10, 11.5, 12.9], '#talisman', faces=('north', 'south')),
]
rifle = [
    # Lying across the upper pegs, muzzle to the left (west when it faces north).
    box([1.5, 10.1, 11.9], [10.5, 11.1, 12.9], '#metal'),          # barrel
    box([1.0, 9.9, 11.7], [2.0, 11.3, 13.1], '#lens'),            # aperture
    box([9.5, 9.6, 11.6], [12.5, 11.8, 13.2], '#metal'),          # receiver
    box([10.2, 11.8, 11.9], [12.2, 12.6, 12.9], '#metal'),        # sight
    box([12.5, 9.3, 11.7], [15.3, 11.5, 13.1], '#wood'),          # stock
    box([10.8, 7.6, 12.0], [11.6, 9.6, 12.8], '#wood'),           # grip
    box([2.0, 9.4, 11.8], [8.5, 9.9, 13.0], '#bone'),             # folded arms
    box([2.0, 11.3, 11.8], [8.5, 11.8, 13.0], '#bone'),
    box([1.4, 9.3, 11.7], [2.2, 9.9, 13.1], '#lens'),
    box([1.4, 11.3, 11.7], [2.2, 11.9, 13.1], '#lens'),
]
torn = [box([6, 4.5, 12.6], [10, 6.5, 12.9], '#talisman', faces=('north', 'south'))]
for name, elements in (('gun_rack_sealed', sealed), ('gun_rack_open', frame + rifle + torn), ('gun_rack_empty', frame + torn)):
    write_json({'parent': 'minecraft:block/block', 'textures': TEX, 'elements': elements}, 'models', 'block', name + '.json')
variants = {}
for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
    for state in ('sealed', 'open', 'empty'):
        v = {'model': 'jjk:block/gun_rack_' + state}
        if y:
            v['y'] = y
        variants['facing=%s,rack=%s' % (facing, state)] = v
write_json({'variants': variants}, 'blockstates', 'gun_rack.json')
write_json({'model': {'type': 'minecraft:model', 'model': 'jjk:block/gun_rack_open'}}, 'items', 'gun_rack.json')

scope = [
    box([5, 0, 5], [11, 1, 11], '#metal'),
    box([7.5, 1, 7.5], [8.5, 9, 8.5], '#metal'),
    box([6.5, 9, 6.5], [9.5, 10, 9.5], '#metal'),
    box([6.75, 10, 2], [9.25, 12.5, 14], '#metal'),
    box([6, 9.5, 0], [10, 13, 2], '#metal'),
    box([6.4, 9.9, -0.01], [9.6, 12.6, 0.2], '#glass', faces=('north',)),
    box([7, 10.25, 14], [9, 12.25, 16], '#metal'),
    box([7.2, 10.45, 15.81], [8.8, 12.05, 16.01], '#glass', faces=('south',)),
    box([7.5, 12.5, 6], [8.5, 13.5, 8], '#metal'),
]
write_json({'parent': 'minecraft:block/block', 'textures': {'particle': 'jjk:block/rifle_metal', 'metal': 'jjk:block/rifle_metal',
                                                            'glass': 'jjk:block/scope_glass'}, 'elements': scope},
           'models', 'block', 'mounted_scope.json')
write_json({'variants': {('facing=' + f): ({'model': 'jjk:block/mounted_scope', 'y': y} if y else {'model': 'jjk:block/mounted_scope'})
                         for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}}, 'blockstates', 'mounted_scope.json')
write_json({'model': {'type': 'minecraft:model', 'model': 'jjk:block/mounted_scope'}}, 'items', 'mounted_scope.json')

# --- The rifle ---
write_json({'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'jjk:item/cursed_rifle'}}, 'models', 'item', 'cursed_rifle.json')
# Held: the special renderer draws the model with its grip at the item's centre, muzzle forward (-Z).
write_json({
    'gui_light': 'front',
    'textures': {'particle': 'jjk:item/cursed_rifle'},
    'display': {
        'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 2, 1], 'scale': [1, 1, 1]},
        'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 2, 1], 'scale': [1, 1, 1]},
        'firstperson_righthand': {'rotation': [0, -6, 0], 'translation': [2, 4.5, -3], 'scale': [0.75, 0.75, 0.75]},
        'firstperson_lefthand': {'rotation': [0, -6, 0], 'translation': [2, 4.5, -3], 'scale': [0.75, 0.75, 0.75]},
        'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]},
    },
}, 'models', 'item', 'cursed_rifle_in_hand.json')
write_json({'hand_animation_on_swap': False, 'model': {
    'type': 'minecraft:select',
    'property': 'minecraft:display_context',
    'cases': [{'when': ['gui', 'ground', 'fixed', 'on_shelf'], 'model': {'type': 'minecraft:model', 'model': 'jjk:item/cursed_rifle'}}],
    'fallback': {'type': 'minecraft:special', 'base': 'jjk:item/cursed_rifle_in_hand', 'model': {'type': 'jjk:cursed_rifle'}},
}}, 'items', 'cursed_rifle.json')
print('lodge assets written')
