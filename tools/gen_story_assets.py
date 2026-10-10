"""Pixel art for the character storylines: the five Essences, the five character objects (Human Earthworm VHS,
Blindfold, Cursed Ring, Comb, Scratch-Off Ticket) and their infused (world-unique) forms, plus item models, item
definitions and the Blindfold as worn on the head.

Every texture is 16x16, drawn pixel by pixel in Minecraft's style (a few flat shades per material, hard edges). The
infused forms are the same object steeped in cursed energy: darker, a violet rim and a hot core, and the enchantment
glint in game. Run: python3 tools/gen_story_assets.py
"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')
TEX = os.path.join(ROOT, 'textures', 'item')
os.makedirs(TEX, exist_ok=True)
for d in ('models/item', 'items'):
    os.makedirs(os.path.join(ROOT, d), exist_ok=True)

rng = np.random.default_rng(2611)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def blank():
    return np.zeros((16, 16, 4), np.uint8)


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img[y, x] = c


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def save(img, name):
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(os.path.join(TEX, name + '.png'))


def write_json(obj, *path):
    with open(os.path.join(ROOT, *path), 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def outline(img, c):
    """A one-pixel outline round everything drawn (Minecraft items read better with one)."""
    a = img[:, :, 3] > 0
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if a[y, x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16 and a[y + dy, x + dx]:
                    out[y, x] = c
                    break
    return out


def infuse(img):
    """The infused form: shadows pulled toward violet-black, a violet rim, a few hot violet motes."""
    out = img.copy().astype(np.int32)
    a = img[:, :, 3] > 0
    for y in range(16):
        for x in range(16):
            if not a[y, x]:
                continue
            r, g, b, al = out[y, x]
            lum = (r * 3 + g * 6 + b) / 10
            # Darken and shift toward violet, keeping the brightest highlights.
            k = 0.55 if lum < 170 else 0.85
            out[y, x] = (int(r * k + 40 * (1 - k)), int(g * k * 0.8), int(b * k + 90 * (1 - k)), al)
    rim = hexc('#7b3cff')
    for y in range(16):
        for x in range(16):
            if a[y, x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16 and a[y + dy, x + dx]:
                    out[y, x] = rim[:3] + (150,)
                    break
    for _ in range(4):
        ys, xs = np.nonzero(a)
        i = rng.integers(0, len(xs))
        out[ys[i], xs[i]] = hexc('#d3a6ff')
    return out.clip(0, 255).astype(np.uint8)


# --------------------------------------------------------------------------------------------------------------------
# Essences: a condensed drop of what the storyline was about, hanging in a little cloud of its own energy. One colour
# family each, so they read at a glance in a chest: Yuji red-orange, Gojo blue-white, Yuta pink, Ryu gold, Hakari teal.
# --------------------------------------------------------------------------------------------------------------------
ESSENCE = {
    'yuji_essence': ['#3a0a0a', '#7a1414', '#c42a1c', '#ff6a3a', '#ffd2b0'],
    'gojo_essence': ['#0a1840', '#1c3f9e', '#3a8cff', '#9ad8ff', '#ffffff'],
    'yuta_essence': ['#2a0a24', '#6a1a5a', '#c83c9c', '#ff8ad6', '#ffe6f6'],
    'ryu_essence': ['#3a2006', '#7a4a0c', '#d08a1a', '#ffd04a', '#fff6c8'],
    'hakari_essence': ['#06302a', '#0c6a5a', '#1ab89a', '#6affd8', '#e6fff8'],
}


def essence(colors):
    img = blank()
    c = [hexc(h) for h in colors]
    cx, cy = 7.5, 8.0
    for y in range(16):
        for x in range(16):
            # A teardrop: round below, drawn up to a point.
            dy = y - cy
            dx = x - cx
            w = 4.6 if dy > 0 else 4.6 + dy * 0.75
            d = math.sqrt((dx / max(0.6, w / 4.6)) ** 2 + (dy * (1.0 if dy > 0 else 0.55)) ** 2)
            if dy < -6.5 or d > 4.6:
                continue
            shade = 1 + (1 if d < 3.6 else 0) + (1 if d < 2.2 and dx < 0.5 and dy < 1 else 0)
            put(img, x, y, c[shade])
    put(img, 6, 6, c[4])
    put(img, 6, 7, c[4])
    put(img, 5, 8, c[3])
    img = outline(img, c[0])
    # Motes of its energy drifting off it.
    for (x, y) in ((2, 3), (13, 5), (12, 13), (3, 12), (14, 10)):
        put(img, x, y, c[3][:3] + (190,))
    return img


for name, cols in ESSENCE.items():
    save(essence(cols), name)


# --------------------------------------------------------------------------------------------------------------------
# The character objects.
# --------------------------------------------------------------------------------------------------------------------
def vhs():
    """A black VHS cassette, label side up: the white label with a red title smear (the film), two reels in the window."""
    img = blank()
    body, edge, hi = hexc('#18181c'), hexc('#2c2c34'), hexc('#44444e')
    rect(img, 1, 3, 14, 12, body)
    rect(img, 1, 3, 14, 3, hi)
    rect(img, 1, 12, 14, 12, edge)
    rect(img, 3, 4, 12, 7, hexc('#e8e2d0'))           # label
    rect(img, 4, 5, 11, 5, hexc('#b01818'))           # title
    rect(img, 4, 6, 8, 6, hexc('#7a6a5a'))
    rect(img, 4, 9, 11, 11, hexc('#0a0a0c'))          # window
    for x in (5, 10):
        put(img, x, 10, hexc('#8a8a96'))
        put(img, x - 1, 10, hexc('#5a5a64'))
        put(img, x + 1, 10, hexc('#5a5a64'))
    return outline(img, hexc('#060608'))


def blindfold():
    """Gojo's blindfold: a band of black cloth, knotted, the tails hanging."""
    img = blank()
    dark, mid, hi = hexc('#101014'), hexc('#24242c'), hexc('#3c3c48')
    for x in range(1, 13):
        y = 6 + (1 if x in (1, 12) else 0)
        rect(img, x, y, x, y + 3, mid)
        put(img, x, y, hi)
        put(img, x, y + 3, dark)
    rect(img, 12, 6, 14, 9, dark)                      # the knot
    put(img, 13, 7, hi)
    for i in range(5):                                 # the tails
        put(img, 13 + (i % 2), 10 + i, mid)
        put(img, 11 - (i // 2), 10 + i, dark)
    return outline(img, hexc('#040406'))


def blindfold_worn_tex():
    img = blank()
    rect(img, 0, 0, 15, 15, hexc('#18181e'))
    for x in range(16):
        put(img, x, 2, hexc('#2c2c36'))
        put(img, x, 13, hexc('#0c0c10'))
    return img


def ring():
    """Yuta's ring: a plain silver band, seen at an angle, a small stone."""
    img = blank()
    s0, s1, s2, s3 = hexc('#5a5e6a'), hexc('#8e94a2'), hexc('#c8ccd6'), hexc('#f4f6fa')
    cx, cy = 7.5, 9.0
    for y in range(16):
        for x in range(16):
            d = math.sqrt(((x - cx) / 5.2) ** 2 + ((y - cy) / 3.6) ** 2)
            if 0.62 <= d <= 1.0:
                put(img, x, y, s2 if y < cy - 1 else s1 if y < cy + 2 else s0)
    put(img, 4, 7, s3)
    put(img, 5, 6, s3)
    rect(img, 7, 4, 8, 5, hexc('#e0e8ff'))             # the stone
    put(img, 7, 4, hexc('#ffffff'))
    return outline(img, hexc('#1c1e24'))


def comb():
    """Ryu's comb: a tortoiseshell fine-tooth comb."""
    img = blank()
    a, b, c = hexc('#4a2a10'), hexc('#7a4a1c'), hexc('#b47a34')
    rect(img, 2, 4, 13, 6, b)
    for x in range(2, 14):
        put(img, x, 4, c if x % 3 else b)
        put(img, x, 6, a)
    for x in range(2, 14, 1):
        if x % 2 == 0:
            rect(img, x, 7, x, 12 if x < 12 else 10, b)
            put(img, x, 12 if x < 12 else 10, a)
    put(img, 3, 5, hexc('#e0a85a'))
    return outline(img, hexc('#1e1006'))


def ticket():
    """A scratch-off: a bright card, three silver panels, one scratched to a number."""
    img = blank()
    card, edge = hexc('#f0d84a'), hexc('#b8901c')
    rect(img, 2, 2, 13, 13, card)
    rect(img, 2, 13, 13, 13, edge)
    rect(img, 13, 2, 13, 13, edge)
    rect(img, 3, 3, 12, 4, hexc('#c41c2c'))            # the banner
    put(img, 5, 3, hexc('#ffffff'))
    put(img, 8, 3, hexc('#ffffff'))
    put(img, 11, 3, hexc('#ffffff'))
    for i, x in enumerate((3, 6, 9)):
        rect(img, x, 7, x + 2, 11, hexc('#a8acb6'))
        put(img, x, 7, hexc('#d6d8de'))
    rect(img, 9, 7, 11, 11, hexc('#fff6e0'))           # scratched
    put(img, 10, 8, hexc('#1a1a1a'))
    put(img, 10, 9, hexc('#1a1a1a'))
    put(img, 10, 10, hexc('#1a1a1a'))
    return outline(img, hexc('#3a2a06'))


OBJECTS = {
    'human_earthworm_vhs': vhs(),
    'blindfold': blindfold(),
    'cursed_ring': ring(),
    'comb': comb(),
    'scratch_off_ticket': ticket(),
}

for name, img in OBJECTS.items():
    save(img, name)
    save(infuse(img), 'infused_' + name)
save(blindfold_worn_tex(), 'blindfold_worn')
save(infuse(blindfold_worn_tex()), 'infused_blindfold_worn')


# --------------------------------------------------------------------------------------------------------------------
# Models and item definitions.
# --------------------------------------------------------------------------------------------------------------------
def flat(name):
    write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jjk:item/' + name}}, 'models', 'item', name + '.json')


def box(fr, to, tex):
    return {'from': fr, 'to': to, 'faces': {f: {'uv': [0, 0, 16, 16], 'texture': tex} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}


def blindfold_worn_model(texture, glow):
    """The band round the eyes, in the head slot's space (the same as the glasses': the face at z = 1.6, the eyes at
    y 6.4-8, the sides of the head at x = 1.6 and 14.4): front, both sides and the back, a knot behind."""
    els = [box([1.0, 5.6, 0.8], [15.0, 9.0, 1.6], '#cloth'),
           box([0.8, 5.6, 1.6], [1.6, 9.0, 14.6], '#cloth'),
           box([14.4, 5.6, 1.6], [15.2, 9.0, 14.6], '#cloth'),
           box([1.0, 5.6, 14.4], [15.0, 9.0, 15.2], '#cloth'),
           box([7.0, 6.0, 15.2], [9.0, 8.6, 16.0], '#cloth')]
    if glow:
        for e in els:
            e['light_emission'] = glow
    return {'textures': {'cloth': texture, 'particle': texture}, 'elements': els}


for name in list(ESSENCE) + list(OBJECTS) + ['infused_' + n for n in OBJECTS]:
    flat(name)
    if 'blindfold' not in name:
        write_json({'model': {'type': 'minecraft:model', 'model': 'jjk:item/' + name}}, 'items', name + '.json')

write_json(blindfold_worn_model('jjk:item/blindfold_worn', 0), 'models', 'item', 'blindfold_worn.json')
write_json(blindfold_worn_model('jjk:item/infused_blindfold_worn', 4), 'models', 'item', 'infused_blindfold_worn.json')
for name in ('blindfold', 'infused_blindfold'):
    write_json({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                          'cases': [{'when': 'head', 'model': {'type': 'minecraft:model', 'model': 'jjk:item/' + name + '_worn'}}],
                          'fallback': {'type': 'minecraft:model', 'model': 'jjk:item/' + name}}}, 'items', name + '.json')

print('story assets written')
