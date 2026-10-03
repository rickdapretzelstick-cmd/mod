"""Pixel art for the Survival progression (Cursed Soul Sand, the bottles, the glasses, the cauldron's cursed energy,
the Cursed Finger and the battle-room seal), plus their block/item models and item definitions.

Every texture is 16x16 (animated ones are vertical strips of 16x16 frames) and drawn pixel by pixel in Minecraft's
style: a few flat shades per material, hard edges, no smoothing. Run: python3 tools/gen_progression_assets.py
"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')
TEX = os.path.join(ROOT, 'textures')
for d in ('block', 'item'):
    os.makedirs(os.path.join(TEX, d), exist_ok=True)
for d in ('models/block', 'models/item', 'items', 'blockstates'):
    os.makedirs(os.path.join(ROOT, d), exist_ok=True)

rng = np.random.default_rng(1026)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def save(img, *path):
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(os.path.join(TEX, *path))


def write_json(obj, *path):
    with open(os.path.join(ROOT, *path), 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def blank(h=16, w=16):
    return np.zeros((h, w, 4), np.uint8)


def put(img, x, y, c):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = c


# --------------------------------------------------------------------------------------------------------------------
# Cursed Soul Sand: soul sand's grain and its faint trapped faces, gone darker and colder, split by violet cracks that
# pulse slowly (8-frame animation), with the faces' eyes catching the light.
# --------------------------------------------------------------------------------------------------------------------
SAND = [hexc('#2f2320'), hexc('#3a2b26'), hexc('#45342c'), hexc('#4f3d33'), hexc('#5a4639')]
VEIN = [hexc('#2a1238'), hexc('#4a1f6b'), hexc('#7a33b8'), hexc('#a85cf0'), hexc('#d3a6ff')]

base_idx = rng.integers(1, 5, (16, 16))
# Grain: a little vertical streaking, like sand settled in layers.
for x in range(16):
    for y in range(16):
        if rng.random() < 0.18:
            base_idx[y, x] = max(0, base_idx[y, x] - 1)
# Two sunken faces (dark hollows for eyes and a mouth), as soul sand has.
faces = [(3, 3), (9, 9)]
face_px = []
for fx, fy in faces:
    for dx, dy in [(0, 0), (3, 0), (1, 3), (2, 3), (0, 4), (3, 4)]:
        face_px.append((fx + dx, fy + dy))
for x, y in face_px:
    base_idx[y % 16, x % 16] = 0
eyes = [((fx) % 16, fy % 16) for fx, fy in faces] + [((fx + 3) % 16, fy % 16) for fx, fy in faces]

# Cracks: a few random walks that wrap round the edges, so the texture tiles.
crack = np.zeros((16, 16), np.int32)
for start in [(1, 12), (12, 2), (6, 7)]:
    x, y = start
    for step in range(14):
        crack[y % 16, x % 16] = max(crack[y % 16, x % 16], 2 if step % 4 else 3)
        x += rng.choice([-1, 0, 1])
        y += rng.choice([0, 1, 1])
        if rng.random() < 0.3:
            crack[(y + 1) % 16, x % 16] = max(crack[(y + 1) % 16, x % 16], 1)

frames = []
for f in range(8):
    pulse = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
    img = blank()
    for y in range(16):
        for x in range(16):
            img[y, x] = SAND[base_idx[y, x]]
            c = crack[y, x]
            if c:
                # Mostly dark seams; only the cracks' cores brighten, and only as the pulse peaks.
                lvl = c - 1 + (1 if c == 3 and pulse > 0.6 else 0)
                img[y, x] = VEIN[max(0, min(3, lvl))]
    for x, y in eyes:
        img[y, x] = VEIN[3] if pulse > 0.35 else VEIN[2]
    frames.append(img)
save(np.concatenate(frames, 0), 'block', 'cursed_soul_sand.png')
with open(os.path.join(TEX, 'block', 'cursed_soul_sand.png.mcmeta'), 'w') as fh:
    json.dump({'animation': {'frametime': 5, 'interpolate': True}}, fh)

# --------------------------------------------------------------------------------------------------------------------
# Bottles: one glass bottle shape (cork, neck, round body), filled with what each holds.
# --------------------------------------------------------------------------------------------------------------------
GLASS_EDGE = hexc('#cfe6ee', 235)
GLASS_SHADE = hexc('#8fb4c2', 235)
GLASS_EMPTY = hexc('#d9f0f7', 70)
CORK = [hexc('#5a3a22'), hexc('#7c5232'), hexc('#9a6a42')]
HIGHLIGHT = hexc('#ffffff', 200)

# Each row: (outline columns, interior columns).
BOTTLE_ROWS = {
    3: ([6, 9], range(7, 9)),
    4: ([6, 9], range(7, 9)),
    5: ([5, 10], range(6, 10)),
    6: ([4, 11], range(5, 11)),
    7: ([3, 12], range(4, 12)),
    8: ([3, 12], range(4, 12)),
    9: ([3, 12], range(4, 12)),
    10: ([3, 12], range(4, 12)),
    11: ([3, 12], range(4, 12)),
    12: ([4, 11], range(5, 11)),
}


def bottle(fill, fill_top=6):
    """fill(x, y) -> colour for an interior pixel at or below fill_top (None: empty glass)."""
    img = blank()
    for x in range(6, 10):
        put(img, x, 1, CORK[0] if x in (6, 9) else CORK[1])
        put(img, x, 2, CORK[1] if x in (6, 9) else CORK[2])
    for y, (edge, inner) in BOTTLE_ROWS.items():
        for x in edge:
            put(img, x, y, GLASS_SHADE if x > 8 else GLASS_EDGE)
        for x in inner:
            c = fill(x, y) if y >= fill_top else None
            put(img, x, y, c if c is not None else GLASS_EMPTY)
    for x in range(5, 11):
        put(img, x, 13, GLASS_SHADE if x > 8 else GLASS_EDGE)
    # The glass catching the light down its left side.
    for y in (7, 8, 9):
        put(img, 4, y, HIGHLIGHT)
    put(img, 5, 6, HIGHLIGHT)
    return img


# Soul in a Bottle: murky soul-fire teal, a pale soul rising in it with two dark eyes.
SOUL_DEEP = [hexc('#123038'), hexc('#18414b'), hexc('#1f525e')]
SOUL = {
    (7, 7): '#9ff6ff', (8, 6): '#d9fdff',
    (6, 8): '#5fe3f2', (7, 8): '#c9fbff', (8, 8): '#e8feff', (9, 8): '#5fe3f2',
    (5, 9): '#3fc4d6', (6, 9): '#9ff6ff', (7, 9): '#1a3b44', (8, 9): '#c9fbff', (9, 9): '#1a3b44', (10, 9): '#3fc4d6',
    (6, 10): '#5fe3f2', (7, 10): '#c9fbff', (8, 10): '#9ff6ff', (9, 10): '#5fe3f2',
    (7, 11): '#3fc4d6', (8, 11): '#5fe3f2',
}


def soul_fill(x, y):
    if (x, y) in SOUL:
        return hexc(SOUL[(x, y)], 245)
    return SOUL_DEEP[(x * 7 + y * 3) % 3][:3] + (230,)


save(bottle(soul_fill), 'item', 'soul_in_a_bottle.png')

# Cursed Energy in a Bottle: near-black violet churning with a spiral of brighter energy and a hot pink-white core.
CE_DEEP = [hexc('#14081f'), hexc('#1d0b2e'), hexc('#2a1042')]
CE = {
    (9, 6): '#7a33b8', (10, 7): '#a85cf0', (10, 8): '#7a33b8', (5, 7): '#4a1f6b',
    (4, 8): '#7a33b8', (4, 9): '#a85cf0', (5, 10): '#a85cf0', (6, 11): '#7a33b8', (8, 11): '#4a1f6b',
    (9, 11): '#7a33b8', (10, 10): '#a85cf0', (11, 9): '#7a33b8',
    (6, 8): '#4a1f6b', (7, 8): '#a85cf0', (8, 8): '#d3a6ff', (9, 9): '#a85cf0',
    (7, 9): '#ff8ae0', (8, 9): '#ffe6fb', (7, 10): '#d3a6ff', (8, 10): '#ff8ae0',
    (6, 12): '#4a1f6b', (9, 12): '#4a1f6b',
}


def ce_fill(x, y):
    if (x, y) in CE:
        return hexc(CE[(x, y)], 250)
    return CE_DEEP[(x * 5 + y * 3) % 3][:3] + (240,)


save(bottle(ce_fill, fill_top=5), 'item', 'cursed_energy_bottle.png')

# --------------------------------------------------------------------------------------------------------------------
# Glasses: front view, two squarish lenses, a bridge and the hinges. Cursed Glasses: blackened frames, violet lenses
# with energy caught in them.
# --------------------------------------------------------------------------------------------------------------------


def glasses(frame, frame_hi, lens, glint, aura=None):
    img = blank()
    for x0 in (1, 9):
        for x in range(x0, x0 + 6):
            put(img, x, 6, frame_hi)
            put(img, x, 10, frame)
        for y in range(7, 10):
            put(img, x0, y, frame)
            put(img, x0 + 5, y, frame)
            for x in range(x0 + 1, x0 + 5):
                put(img, x, y, lens)
        put(img, x0 + 1, 7, glint)
        put(img, x0 + 2, 7, glint[:3] + (glint[3] // 2,))
    put(img, 7, 7, frame_hi)
    put(img, 8, 7, frame_hi)
    put(img, 0, 7, frame)
    put(img, 15, 7, frame)
    if aura:
        for x, y in [(0, 5), (6, 4), (15, 5), (9, 4), (3, 12), (13, 12), (7, 11), (11, 3)]:
            put(img, x, y, aura)
    return img


save(glasses(hexc('#2d2f36'), hexc('#5d6270'), hexc('#a8d6ea', 150), hexc('#ffffff', 230)), 'item', 'glasses.png')
save(glasses(hexc('#1a0f22'), hexc('#4a2a63'), hexc('#7b3cff', 200), hexc('#ffd6ff', 240), aura=hexc('#b77cff', 150)),
     'item', 'cursed_glasses.png')

# The worn model's textures: a frame strip (top half) and a lens (bottom half).


def worn(frame, frame_hi, lens, glint):
    img = blank()
    for y in range(8):
        for x in range(16):
            img[y, x] = frame_hi if y < 2 else frame
    for y in range(8, 16):
        for x in range(16):
            img[y, x] = lens
    for x in range(1, 5):
        img[9, x] = glint
    img[10, 1] = glint
    return img


save(worn(hexc('#2d2f36'), hexc('#5d6270'), hexc('#a8d6ea', 120), hexc('#ffffff', 200)), 'item', 'glasses_worn.png')
save(worn(hexc('#1a0f22'), hexc('#4a2a63'), hexc('#7b3cff', 170), hexc('#ffd6ff', 230)), 'item', 'cursed_glasses_worn.png')

# --------------------------------------------------------------------------------------------------------------------
# Cursed Finger: a long, withered, grey-violet finger with a black nail and a wrapped talisman band.
# --------------------------------------------------------------------------------------------------------------------
FINGER = {
    '.': None,
    'o': hexc('#24161e'),  # outline
    's': hexc('#6b5560'),  # skin shade
    'S': hexc('#8a7280'),  # skin
    'h': hexc('#a8909c'),  # highlight
    'n': hexc('#120a10'),  # nail
    'N': hexc('#3a2a36'),  # nail shine
    'w': hexc('#c9b98f'),  # wrap
    'W': hexc('#a8975f'),  # wrap shade
    'r': hexc('#8f1b22'),  # talisman mark
    'k': hexc('#4a3442'),  # knuckle crease
}
FINGER_ART = [
    "................",
    "............oo..",
    "...........onNo.",
    "..........onnNo.",
    ".........oShnno.",
    "........oShSso..",
    ".......oShSkso..",
    "......oShSsso...",
    ".....owwrwWo....",
    "....owwwrWWo....",
    "...oShSsso......",
    "..oShkSso.......",
    ".oShSsso........",
    ".oSSsso.........",
    "..ooo...........",
    "................",
]
img = blank()
for y, row in enumerate(FINGER_ART):
    for x, ch in enumerate(row):
        if FINGER[ch]:
            img[y, x] = FINGER[ch]
save(img, 'item', 'cursed_finger.png')

# --------------------------------------------------------------------------------------------------------------------
# Cursed energy (the cauldron's contents): dark violet churning slowly, 16 frames; and a brighter "reacting" version for
# the infusion ritual.
# --------------------------------------------------------------------------------------------------------------------


def energy(colors, frames=16, speed=1.0):
    out = []
    yy, xx = np.mgrid[0:16, 0:16]
    for f in range(frames):
        t = f / frames * math.tau
        v = (np.sin((xx / 16) * math.tau * 1 + t * speed) + np.sin((yy / 16) * math.tau * 2 - t * speed)
             + np.sin(((xx + yy) / 16) * math.tau * 1 + t * 2 * speed)) / 3
        idx = np.clip(((v + 1) / 2 * len(colors)).astype(int), 0, len(colors) - 1)
        img = blank()
        for y in range(16):
            for x in range(16):
                img[y, x] = colors[idx[y, x]]
        out.append(img)
    return np.concatenate(out, 0)


CE_STILL = [hexc('#12061c'), hexc('#1d0b2e'), hexc('#2a1042'), hexc('#3d1660'), hexc('#5a2390'), hexc('#7a33b8')]
CE_ACTIVE = [hexc('#2a1042'), hexc('#4a1f6b'), hexc('#7a33b8'), hexc('#a85cf0'), hexc('#d3a6ff'), hexc('#ffe6fb')]
save(energy(CE_STILL), 'block', 'cursed_energy_still.png')
save(energy(CE_ACTIVE, speed=2.0), 'block', 'cursed_energy_active.png')
for n in ('cursed_energy_still', 'cursed_energy_active'):
    with open(os.path.join(TEX, 'block', n + '.png.mcmeta'), 'w') as fh:
        json.dump({'animation': {'frametime': 3, 'interpolate': True}}, fh)

# --------------------------------------------------------------------------------------------------------------------
# The battle-room seal's top: polished blackstone carved with a ring and a cross, the grooves holding a dull red glow.
# --------------------------------------------------------------------------------------------------------------------
STONE = [hexc('#1b1820'), hexc('#24202a'), hexc('#2e2934'), hexc('#38323f')]
GROOVE = hexc('#0c0a0f')
GLOW = [hexc('#5a0e18'), hexc('#8f1b22'), hexc('#c83a3a')]
img = blank()
for y in range(16):
    for x in range(16):
        img[y, x] = STONE[int(rng.integers(1, 4))]
for i in range(16):
    img[0, i] = img[15, i] = img[i, 0] = img[i, 15] = STONE[0]
SEAL_ART = [
    "................",
    "................",
    ".....gGGGGg.....",
    "....G..r...G....",
    "...G...r....G...",
    "..g...ooo....g..",
    "..G..o...o...G..",
    "..Grro.ee.orrG..",
    "..G..o.ee.o..G..",
    "..g...ooo....g..",
    "...G....r...G...",
    "....G...r..G....",
    ".....gGGGGg.....",
    "................",
    "................",
    "................",
]
SEAL = {'g': GROOVE, 'G': GLOW[0], 'r': GLOW[1], 'o': GLOW[1], 'e': GLOW[2]}
for y, row in enumerate(SEAL_ART):
    for x, ch in enumerate(row):
        if ch in SEAL:
            img[y + 1 if y < 15 else y, x] = SEAL[ch]
save(img, 'block', 'cursed_seal_top.png')

# --------------------------------------------------------------------------------------------------------------------
# Models, block states and item definitions.
# --------------------------------------------------------------------------------------------------------------------
write_json({'variants': {'': {'model': 'jjk:block/cursed_soul_sand'}}}, 'blockstates', 'cursed_soul_sand.json')
write_json({'parent': 'minecraft:block/cube_all', 'textures': {'all': 'jjk:block/cursed_soul_sand'}}, 'models', 'block', 'cursed_soul_sand.json')
write_json({'model': {'type': 'minecraft:model', 'model': 'jjk:block/cursed_soul_sand'}}, 'items', 'cursed_soul_sand.json')

write_json({'variants': {'site=0': {'model': 'jjk:block/cursed_seal'}, 'site=1': {'model': 'jjk:block/cursed_seal'}}},
           'blockstates', 'cursed_seal.json')
write_json({'parent': 'minecraft:block/cube_bottom_top', 'textures': {
    'top': 'jjk:block/cursed_seal_top', 'side': 'minecraft:block/polished_blackstone', 'bottom': 'minecraft:block/polished_blackstone'}},
    'models', 'block', 'cursed_seal.json')

# The cauldron: vanilla's cauldron, plus the energy's surface at the level's height (glowing more as it fills).
parts = [{'apply': {'model': 'minecraft:block/cauldron'}}]
for lv in range(1, 5):
    top = 6 + lv * 2.25
    for active in (False, True):
        if active and lv < 4:
            continue
        name = 'cursed_energy_cauldron_level%d%s' % (lv, '_active' if active else '')
        tex = 'jjk:block/cursed_energy_active' if active else 'jjk:block/cursed_energy_still'
        write_json({'textures': {'particle': tex, 'content': tex}, 'elements': [{
            'from': [2, 4, 2], 'to': [14, top, 14], 'light_emission': 4 + lv * 2 + (4 if active else 0),
            'faces': {'up': {'uv': [2, 2, 14, 14], 'texture': '#content'}}}]}, 'models', 'block', name + '.json')
        when = {'level': str(lv)}
        if lv == 4:
            when['infusion'] = '1|2|3|4|5' if active else '0'
        parts.append({'when': when, 'apply': {'model': 'jjk:block/' + name}})
write_json({'multipart': parts}, 'blockstates', 'cursed_energy_cauldron.json')

for name in ('soul_in_a_bottle', 'cursed_energy_bottle', 'cursed_finger', 'glasses', 'cursed_glasses'):
    write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jjk:item/' + name}}, 'models', 'item', name + '.json')
for name in ('soul_in_a_bottle', 'cursed_energy_bottle', 'cursed_finger'):
    write_json({'model': {'type': 'minecraft:model', 'model': 'jjk:item/' + name}}, 'items', name + '.json')


def box(fr, to, tex):
    return {'from': fr, 'to': to, 'faces': {f: {'uv': [0, 2, 16, 8], 'texture': tex} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}


def worn_model(texture, glow):
    """The glasses as worn: 1 unit here is 0.625 px on the head (the head slot draws models at 10/16 scale), the front of
    the face is at z = 1.6, the eyes at y 6.4-8, the sides of the head at x = 1.6 and 14.4."""
    els = []
    for x0, x1 in ((2.4, 7.2), (8.8, 13.6)):
        els.append(box([x0, 8.0, 0.6], [x1, 8.8, 1.4], '#frame'))      # top bar
        els.append(box([x0, 5.6, 0.6], [x1, 6.4, 1.4], '#frame'))      # bottom bar
        els.append(box([x0, 6.4, 0.6], [x0 + 0.8, 8.0, 1.4], '#frame'))  # sides
        els.append(box([x1 - 0.8, 6.4, 0.6], [x1, 8.0, 1.4], '#frame'))
        lens = {'from': [x0 + 0.8, 6.4, 1.0], 'to': [x1 - 0.8, 8.0, 1.0],
                'faces': {'north': {'uv': [0, 8, 16, 16], 'texture': '#frame'}, 'south': {'uv': [0, 8, 16, 16], 'texture': '#frame'}}}
        if glow:
            lens['light_emission'] = glow
        els.append(lens)
    els.append(box([7.2, 7.4, 0.6], [8.8, 8.0, 1.4], '#frame'))   # bridge
    els.append(box([0.8, 7.4, 0.6], [2.4, 8.2, 1.4], '#frame'))   # hinges
    els.append(box([13.6, 7.4, 0.6], [15.2, 8.2, 1.4], '#frame'))
    els.append(box([0.8, 7.4, 1.4], [1.6, 8.2, 9.6], '#frame'))   # arms, back along the sides of the head
    els.append(box([14.4, 7.4, 1.4], [15.2, 8.2, 9.6], '#frame'))
    return {'textures': {'frame': texture, 'particle': texture}, 'elements': els}


write_json(worn_model('jjk:item/glasses_worn', 0), 'models', 'item', 'glasses_worn.json')
write_json(worn_model('jjk:item/cursed_glasses_worn', 6), 'models', 'item', 'cursed_glasses_worn.json')
for name in ('glasses', 'cursed_glasses'):
    write_json({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                          'cases': [{'when': 'head', 'model': {'type': 'minecraft:model', 'model': 'jjk:item/' + name + '_worn'}}],
                          'fallback': {'type': 'minecraft:model', 'model': 'jjk:item/' + name}}}, 'items', name + '.json')

print('progression assets written')
