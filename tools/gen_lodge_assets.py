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

# The rifle's icon (32x32, so the scope, the barrel and the stock all read at inventory size): the current model
# (models/bb/cursed_rifle.bbmodel) drawn in its own colours, laid along a diagonal like the vanilla weapons, butt low
# and left, muzzle high and right. Each pixel samples the rifle's side profile in rifle-local units (u along the
# barrel, v across it, down positive).
import math
METAL, METAL_HI, METAL_DK, BLACK = (38, 40, 43), (63, 67, 69), (25, 26, 28), (14, 16, 19)
WOOD, WOOD_DK, WOOD_HI, GRIP = (99, 60, 36), (72, 45, 28), (118, 75, 45), (67, 39, 24)
SCOPE, GLASS, GLASS_HI = (18, 29, 34), (46, 99, 109), (85, 145, 151)
CELL, CELL_HI, ARM, CORE = (236, 59, 81), (250, 110, 123), (45, 33, 38), (255, 218, 202)


def profile(u, v):
    """The colour of the rifle's side at (u, v), or None."""
    # Muzzle and its crimson aperture.
    if 31.0 <= u <= 33.4 and abs(v) <= 1.5:
        if u >= 32.6 and abs(v) <= 0.6:
            return CORE if abs(v) < 0.3 else CELL_HI
        return BLACK if abs(v) > 1.0 else CELL
    # The folded arms along the barrel, their lens tips at the end.
    if 21.0 <= u <= 30.8 and 0.95 <= abs(v) <= 1.45 and v > 0:
        return CELL if u >= 29.8 else ARM
    # Barrel, banded.
    if 17.5 <= u <= 31.0 and abs(v) <= 0.85:
        if int(u) % 4 == 0 and v > -0.3:
            return BLACK
        return (100, 106, 110) if v < -0.35 else METAL
    # Scope: tube above the receiver, mounts down to it, a lit lens at the front.
    if 10.0 <= u <= 19.6 and -4.3 <= v <= -2.3:
        if u >= 19.0:
            return GLASS_HI if v < -3.3 else GLASS
        if u <= 10.6:
            return METAL_DK
        return (100, 106, 110) if v < -3.6 else SCOPE
    if (12.0 <= u <= 13.0 or 16.5 <= u <= 17.5) and -2.3 < v < -1.3:
        return METAL_DK
    # Receiver with its glowing cell.
    if 9.0 <= u <= 17.8 and -1.4 <= v <= 1.4:
        if 13.0 <= u <= 15.4 and -0.5 <= v <= 0.5:
            return CELL_HI if u < 14.0 else CELL
        return (100, 106, 110) if v < -0.9 else METAL
    # Magazine.
    if 14.2 <= u <= 16.6 and 1.4 < v <= 3.8:
        return BLACK
    # Pistol grip and trigger guard.
    if 10.0 <= u <= 12.2 and 1.4 < v <= 4.4 - (u - 10.0) * 0.25:
        return GRIP
    if 12.2 < u <= 13.8 and 1.4 < v <= 2.4 and not (12.6 < u < 13.4 and v < 2.0):
        return METAL_DK
    # Stock: deep at the butt, tapering to the wrist.
    if 0.0 <= u < 9.2:
        top = -1.4 - (9.2 - u) * 0.06
        bot = 1.3 + (9.2 - u) * 0.28
        if top <= v <= bot:
            if u < 1.0:
                return BLACK
            if v < top + 0.8:
                return WOOD_HI
            return WOOD_DK if v > bot - 0.9 else WOOD
    return None


SIZE, ANGLE, LENGTH = 32, math.radians(38), 33.4
d = (math.cos(ANGLE), -math.sin(ANGLE))   # along the barrel, toward the muzzle (up and right)
n = (math.sin(ANGLE), math.cos(ANGLE))    # across it (down and right)
origin = (SIZE / 2 - d[0] * LENGTH / 2 + 0.6, SIZE / 2 - d[1] * LENGTH / 2 + 0.6)
icon = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
for y in range(SIZE):
    for x in range(SIZE):
        rx, ry = x + 0.5 - origin[0], y + 0.5 - origin[1]
        c = profile(rx * d[0] + ry * d[1], rx * n[0] + ry * n[1])
        if c:
            icon.putpixel((x, y), c + (255,))
# A thin dark edge round the silhouette (keeps it legible on any slot background).
edged = icon.copy()
for y in range(SIZE):
    for x in range(SIZE):
        if icon.getpixel((x, y))[3]:
            continue
        if any(0 <= x + dx < SIZE and 0 <= y + dy < SIZE and icon.getpixel((x + dx, y + dy))[3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            edged.putpixel((x, y), (10, 10, 12, 150))
save(edged, 'item', 'cursed_rifle.png')


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
