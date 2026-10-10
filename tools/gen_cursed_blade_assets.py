"""The Cursed Blade: its 3D model, textures, inventory icon, Blockbench source and ability icons.

    python3 tools/gen_cursed_blade_assets.py

One element list drives everything, so the game's model (models/item/cursed_blade_3d.json), the Blockbench project
(tools/blockbench/cursed_blade.bbmodel, open it to edit, then mirror the change here) and the textures always agree.

The design (the concept sheet): a large cleaver-like blade, its chisel tip cut at an angle and a bright bevel down the
edge, two round holes near the base; white cloth wrapped where the blade meets the guard; black thorny growths bursting
out round the guard; a wooden handle ending in an angular pommel. Model units are pixels (16 = one block); the blade
runs up +Y from the pommel, X is its width (the edge on +X), Z its thickness.
"""
import base64
import io
import json
import math
import os
import uuid

import numpy as np
from PIL import Image

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jjk')
TEX = os.path.join(ROOT, 'textures', 'item')
ICONS = os.path.join(ROOT, 'textures', 'gui', 'ability')
for d in (TEX, ICONS, os.path.join(ROOT, 'models', 'item'), os.path.join(ROOT, 'items'), os.path.join(HERE, 'blockbench')):
    os.makedirs(d, exist_ok=True)

rng = np.random.default_rng(4242)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def blank(w=16, h=16):
    return np.zeros((h, w, 4), np.uint8)


def put(img, x, y, c):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = c


def save(img, path):
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(path)


# ------------------------------------------------------------------------------------------------------------------
# Textures (16x16 each, one per material; the blade face maps continuously across its pieces)
# ------------------------------------------------------------------------------------------------------------------
def steel():
    """The blade's flat: brushed grey steel, brighter toward the tip, faint diagonal scratches, a darker spine."""
    img = blank()
    for y in range(16):
        for x in range(16):
            t = (15 - y) / 15
            base = 120 + int(70 * t) + int(rng.integers(-7, 8))
            if x <= 1:
                base -= 30  # the spine
            if (x + y * 2) % 11 == 0 and rng.random() < 0.5:
                base += 18  # a scratch catching the light
            v = max(60, min(225, base))
            img[y, x] = (v, v, min(255, v + 6), 255)
    return img


def edge():
    """The bevel down the cutting edge: nearly white, a cold glint."""
    img = blank()
    for y in range(16):
        for x in range(16):
            v = 205 + int(rng.integers(-8, 10)) + (20 if (y % 5 == 0) else 0)
            img[y, x] = (min(255, v), min(255, v), min(255, v + 8), 255)
    return img


def cloth():
    """White wrapping: bands of cloth, fold shadows, a frayed thread or two."""
    img = blank()
    for y in range(16):
        for x in range(16):
            band = (y // 3) % 2
            v = 236 - band * 14 - (22 if y % 3 == 2 else 0) + int(rng.integers(-6, 6))
            if (x * 3 + y) % 13 == 0:
                v -= 18
            img[y, x] = (v, v, max(0, v - 6), 255)
    return img


def thorn():
    """The cursed growth: black, with a dark red seam of cursed energy running through it."""
    img = blank()
    for y in range(16):
        for x in range(16):
            v = 16 + int(rng.integers(0, 14))
            c = (v, v - 4 if v > 4 else 0, v + 4, 255)
            if (x + y) % 7 == 0 or rng.random() < 0.05:
                c = (90 + int(rng.integers(0, 50)), 8, 18, 255)
            img[y, x] = c
    return img


def wood():
    """The handle: warm brown wood, grain running its length, darker knots."""
    img = blank()
    for y in range(16):
        for x in range(16):
            grain = math.sin(x * 1.3 + math.sin(y * 0.35) * 1.5) * 10
            v = 0.62 + grain / 255
            r, g, b = int(150 * v + rng.integers(-6, 6)), int(92 * v + rng.integers(-5, 5)), int(52 * v + rng.integers(-4, 4))
            if (x, y) in ((4, 5), (11, 12)):
                r, g, b = r - 40, g - 30, b - 20
            img[y, x] = (max(0, r), max(0, g), max(0, b), 255)
    return img


def guard():
    """The guard under the thorns: dark iron."""
    img = blank()
    for y in range(16):
        for x in range(16):
            v = 44 + int(rng.integers(-6, 8)) + (14 if y == 0 else 0)
            img[y, x] = (v, v, v + 4, 255)
    return img


TEXTURES = {'steel': steel(), 'edge': edge(), 'cloth': cloth(), 'thorn': thorn(), 'wood': wood(), 'guard': guard()}
for name, img in TEXTURES.items():
    save(img, os.path.join(TEX, 'cursed_blade_' + name + '.png'))

# ------------------------------------------------------------------------------------------------------------------
# Geometry
# ------------------------------------------------------------------------------------------------------------------
# The blade's flat spans x 5..10.2 (then the edge bevel to 11), y 11..30.
BLADE_X0, BLADE_X1, BLADE_Y0, BLADE_Y1 = 5.0, 10.2, 11.0, 30.0
ELEMENTS = []


def blade_uv(x0, y0, x1, y1):
    """UVs of a piece of the blade's flat, continuous across the pieces (the steel texture spans the whole flat)."""
    u0 = (x0 - BLADE_X0) / (BLADE_X1 - BLADE_X0) * 16
    u1 = (x1 - BLADE_X0) / (BLADE_X1 - BLADE_X0) * 16
    v0 = (BLADE_Y1 - y1) / (BLADE_Y1 - BLADE_Y0) * 16
    v1 = (BLADE_Y1 - y0) / (BLADE_Y1 - BLADE_Y0) * 16
    return [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]


def cube(name, frm, to, tex, rotation=None, flat=False):
    """A box. {@code flat}: its north/south faces are part of the blade's continuous flat."""
    faces = {}
    sx, sy, sz = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]

    def fit(a, b):
        # Small faces sample a matching-size patch of the texture (so detail keeps its scale).
        return [0, 0, round(min(16, max(1, a)), 3), round(min(16, max(1, b)), 3)]

    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        if flat and f in ('north', 'south'):
            uv = blade_uv(frm[0], frm[1], to[0], to[1])
            if f == 'south':
                uv = [uv[2], uv[1], uv[0], uv[3]]
        elif f in ('north', 'south'):
            uv = fit(sx, sy)
        elif f in ('east', 'west'):
            uv = fit(sz, sy)
        else:
            uv = fit(sx, sz)
        faces[f] = {'uv': uv, 'texture': '#' + tex}
    e = {'name': name, 'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': faces}
    if rotation:
        e['rotation'] = rotation
    ELEMENTS.append(e)


def rot(axis, angle, origin):
    return {'angle': angle, 'axis': axis, 'origin': origin}


Z0, Z1 = 7.6, 8.4  # the blade's thickness
# The flat, in pieces round the two holes (each hole 1.8 x 1.8, on the blade's centre line near its base).
HX0, HX1 = 6.9, 8.7
cube('blade_base', [BLADE_X0, 11, Z0], [BLADE_X1, 13.0, Z1], 'steel', flat=True)
cube('blade_hole1_l', [BLADE_X0, 13.0, Z0], [HX0, 14.8, Z1], 'steel', flat=True)
cube('blade_hole1_r', [HX1, 13.0, Z0], [BLADE_X1, 14.8, Z1], 'steel', flat=True)
cube('blade_mid', [BLADE_X0, 14.8, Z0], [BLADE_X1, 16.4, Z1], 'steel', flat=True)
cube('blade_hole2_l', [BLADE_X0, 16.4, Z0], [HX0, 18.2, Z1], 'steel', flat=True)
cube('blade_hole2_r', [HX1, 16.4, Z0], [BLADE_X1, 18.2, Z1], 'steel', flat=True)
cube('blade_body', [BLADE_X0, 18.2, Z0], [BLADE_X1, 27.4, Z1], 'steel', flat=True)
# The chisel tip: the flat steps down from the spine (high) to the edge (low).
cube('blade_tip_spine', [BLADE_X0, 27.4, Z0], [7.0, 30.0, Z1], 'steel', flat=True)
cube('blade_tip_mid', [7.0, 27.4, Z0], [8.7, 29.0, Z1], 'steel', flat=True)
cube('blade_tip_low', [8.7, 27.4, Z0], [BLADE_X1, 28.1, Z1], 'steel', flat=True)
# The bevel down the cutting edge (thinner), and its slanted tip.
cube('edge_bevel', [BLADE_X1, 11, 7.75], [11.0, 27.5, 8.25], 'edge')
cube('edge_tip', [8.6, 27.2, 7.75], [11.0, 28.1, 8.25], 'edge', rotation=rot('z', 22.5, [11.0, 27.6, 8.0]))
cube('spine_tip', [BLADE_X0, 29.2, 7.75], [7.6, 30.4, 8.25], 'edge', rotation=rot('z', -22.5, [BLADE_X0, 30.0, 8.0]))

# White cloth wrapped round the blade's base, a looser band over it, a frayed end hanging.
cube('wrap', [4.6, 7.4, 6.9], [11.4, 11.6, 9.1], 'cloth')
cube('wrap_band', [4.3, 9.0, 6.7], [11.7, 10.2, 9.3], 'cloth', rotation=rot('z', -22.5, [8, 9.6, 8]))
cube('wrap_tail', [10.6, 4.8, 7.4], [11.6, 7.6, 8.4], 'cloth', rotation=rot('z', 22.5, [11.1, 7.4, 8]))

# The guard, and the black thorns bursting out round it in every direction.
cube('guard', [5.6, 5.4, 6.4], [10.4, 7.4, 9.6], 'guard')
THORNS = [
    # (length, thickness, height, axis, angle, along) - along: 'x' = a spike across the width, 'z' = through the thickness
    (14.0, 0.9, 6.4, 'y', 0, 'x'), (12.0, 0.8, 6.0, 'y', 45, 'x'), (12.5, 0.8, 6.8, 'y', -45, 'x'), (11.0, 0.8, 6.2, 'y', 22.5, 'z'),
    (10.0, 0.7, 6.9, 'y', -22.5, 'z'), (11.5, 0.7, 6.6, 'z', 22.5, 'x'), (11.0, 0.7, 6.1, 'z', -22.5, 'x'), (9.5, 0.7, 6.5, 'x', 22.5, 'z'),
    (9.0, 0.7, 6.3, 'x', -22.5, 'z'), (8.0, 0.6, 7.2, 'y', 22.5, 'x'), (8.5, 0.6, 5.7, 'y', -22.5, 'x'), (7.5, 0.6, 7.3, 'z', 45, 'x'),
    (7.0, 0.6, 5.6, 'z', -45, 'x'), (7.5, 0.6, 7.0, 'x', 45, 'z'),
]
for i, (length, t, y, axis, angle, along) in enumerate(THORNS):
    h = length / 2
    if along == 'x':
        frm, to = [8 - h, y - t / 2, 8 - t / 2], [8 + h, y + t / 2, 8 + t / 2]
    else:
        frm, to = [8 - t / 2, y - t / 2, 8 - h], [8 + t / 2, y + t / 2, 8 + h]
    cube('thorn_%d' % i, frm, to, 'thorn', rotation=rot(axis, angle, [8, y, 8]) if angle else None)
# A few short barbs on the spikes, so the growth reads as thorny rather than a star.
for i, (x, y, z, axis, angle) in enumerate([(3.0, 6.9, 8, 'z', 45), (13.0, 6.0, 8, 'z', -45), (8, 6.9, 3.2, 'x', 45), (8, 6.1, 12.8, 'x', -45),
                                            (4.6, 6.6, 4.6, 'y', 45), (11.4, 6.6, 11.4, 'y', 45)]):
    cube('barb_%d' % i, [x - 0.3, y - 1.2, z - 0.3], [x + 0.3, y + 1.2, z + 0.3], 'thorn', rotation=rot(axis, angle, [x, y, z]))

# The handle, and its angular pommel.
cube('handle', [6.8, -4.4, 6.8], [9.2, 5.4, 9.2], 'wood')
cube('handle_ring', [6.6, 0.4, 6.6], [9.4, 1.0, 9.4], 'guard')
cube('pommel', [6.5, -6.4, 6.6], [9.7, -4.2, 9.4], 'wood', rotation=rot('z', -22.5, [8, -5.3, 8]))

# ------------------------------------------------------------------------------------------------------------------
# The game's models and item definition
# ------------------------------------------------------------------------------------------------------------------
TEXTURE_REFS = {k: 'jjk:item/cursed_blade_' + k for k in TEXTURES}
TEXTURE_REFS['particle'] = 'jjk:item/cursed_blade_steel'

# Display transforms (tuned by looking at it in game: StoryClientTest/BladeClientTest screenshots).
DISPLAY = {
    'thirdperson_righthand': {'rotation': [0, -90, 10], 'translation': [0, 7.0, -1.0], 'scale': [0.9, 0.9, 0.9]},
    'thirdperson_lefthand': {'rotation': [0, 90, -10], 'translation': [0, 7.0, -1.0], 'scale': [0.9, 0.9, 0.9]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.5, 3.6, 0.5], 'scale': [0.42, 0.42, 0.42]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.5, 3.6, 0.5], 'scale': [0.42, 0.42, 0.42]},
    'ground': {'rotation': [0, 0, 45], 'translation': [0, 2, 0], 'scale': [0.35, 0.35, 0.35]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'gui': {'rotation': [0, 0, -45], 'translation': [0, 0, 0], 'scale': [0.42, 0.42, 0.42]},
}
model = {'credit': 'tools/gen_cursed_blade_assets.py', 'texture_size': [16, 16], 'textures': TEXTURE_REFS, 'elements': ELEMENTS,
         'display': DISPLAY, 'gui_light': 'front'}
with open(os.path.join(ROOT, 'models', 'item', 'cursed_blade_3d.json'), 'w') as f:
    json.dump(model, f, indent=1)
    f.write('\n')

# The inventory icon: the blade's own silhouette laid on the diagonal (handle low left, tip high right), pixel art.
icon = blank()
OX, OY = 5.4, 10.6  # where the guard sits
for y in range(16):
    for x in range(16):
        dx, dy = x + 0.5 - OX, OY - (y + 0.5)
        u, v = (dx + dy) / math.sqrt(2), (dy - dx) / math.sqrt(2)  # u: along the blade, v: across it (+v the spine)
        c = None
        if 0.6 <= u and -2.3 <= v <= 2.3 and u <= 11.0 - (2.3 - v) * 0.7:
            near_hole = any((u - hu) ** 2 + v ** 2 < 0.55 for hu in (2.4, 4.4))
            if near_hole:
                c = hexc('#16161c')
            elif v < -1.3:
                c = hexc('#f2f4f8')  # the bright edge
            elif v > 1.4:
                c = hexc('#6c707a')  # the spine
            else:
                c = hexc('#c2c6ce') if u > 6 else hexc('#a8acb6')
        elif -1.2 <= u < 0.6 and -2.0 <= v <= 2.0:
            c = hexc('#efeee8') if int(u * 2 + v) % 2 else hexc('#cfcdc4')  # the cloth
        elif -2.2 <= u < -1.2 and -3.2 <= v <= 3.2:
            c = hexc('#121016') if (int(v * 1.7) % 2 == 0) else hexc('#7a1018')  # the thorns
        elif -5.2 <= u < -2.2 and -0.9 <= v <= 0.9:
            c = hexc('#a0683a') if v > 0 else hexc('#7a4a24')  # the handle
        if c is not None:
            put(icon, x, y, c)
a = icon[:, :, 3] > 0
out = icon.copy()
for y in range(16):
    for x in range(16):
        if a[y, x]:
            continue
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16 and a[y + dy, x + dx]:
                out[y, x] = hexc('#0c0c10')
                break
save(out, os.path.join(TEX, 'cursed_blade.png'))
with open(os.path.join(ROOT, 'models', 'item', 'cursed_blade.json'), 'w') as f:
    json.dump({'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'jjk:item/cursed_blade'}}, f, indent=2)
    f.write('\n')
# In the inventory and hotbar: the icon. Everywhere else (in the hand, on the ground, on the back, in a frame): the model.
with open(os.path.join(ROOT, 'items', 'cursed_blade.json'), 'w') as f:
    json.dump({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                         'cases': [{'when': 'gui', 'model': {'type': 'minecraft:model', 'model': 'jjk:item/cursed_blade'}}],
                         'fallback': {'type': 'minecraft:model', 'model': 'jjk:item/cursed_blade_3d'}}}, f, indent=2)
    f.write('\n')

# ------------------------------------------------------------------------------------------------------------------
# The Blockbench project (the same elements and textures, embedded)
# ------------------------------------------------------------------------------------------------------------------
def b64(img):
    buf = io.BytesIO()
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(buf, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()


names = list(TEXTURES)
bb_textures = [{'name': 'cursed_blade_' + n + '.png', 'id': str(i), 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cb_tex_' + n)),
                'width': 16, 'height': 16, 'uv_width': 16, 'uv_height': 16, 'source': b64(TEXTURES[n])} for i, n in enumerate(names)]
bb_elements = []
for e in ELEMENTS:
    r = e.get('rotation')
    rotation = [0, 0, 0]
    origin = [8, 8, 8]
    if r:
        rotation[{'x': 0, 'y': 1, 'z': 2}[r['axis']]] = r['angle']
        origin = r['origin']
    bb_elements.append({'name': e['name'], 'type': 'cube', 'from': e['from'], 'to': e['to'], 'origin': origin, 'rotation': rotation,
                        'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'cb_el_' + e['name'])), 'box_uv': False,
                        'faces': {k: {'uv': v['uv'], 'texture': names.index(v['texture'][1:])} for k, v in e['faces'].items()}})
bb = {'meta': {'format_version': '4.10', 'model_format': 'java_block', 'box_uv': False}, 'name': 'cursed_blade',
      'resolution': {'width': 16, 'height': 16}, 'elements': bb_elements, 'outliner': [el['uuid'] for el in bb_elements],
      'textures': bb_textures, 'display': DISPLAY}
with open(os.path.join(HERE, 'blockbench', 'cursed_blade.bbmodel'), 'w') as f:
    json.dump(bb, f)

# ------------------------------------------------------------------------------------------------------------------
# Ability icons (16x16, the HUD's style: a dark frame is drawn round them by the HUD)
# ------------------------------------------------------------------------------------------------------------------
RED, RED_HI, RED_DK, INK, WHITE, STEEL_C = hexc('#d0182c'), hexc('#ff6a78'), hexc('#5a0610'), hexc('#120a0e'), hexc('#ffffff'), hexc('#c8ccd4')


def arc(img, cx, cy, r, a0, a1, c, w=1.0):
    for k in range(80):
        a = math.radians(a0 + (a1 - a0) * k / 79)
        for d in np.arange(-w / 2, w / 2 + 0.01, 0.5):
            put(img, int(round(cx + math.cos(a) * (r + d))), int(round(cy - math.sin(a) * (r + d))), c)


def outline(img):
    a = img[:, :, 3] > 0
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if a[y, x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16 and a[y + dy, x + dx]:
                    out[y, x] = INK
                    break
    return out


def blade_mini(img, x0, y0, dx, dy, n):
    for i in range(n):
        put(img, x0 + dx * i, y0 + dy * i, STEEL_C)
        put(img, x0 + dx * i + 1, y0 + dy * i, WHITE if i % 3 else STEEL_C)


def icon_heavy_slash():
    img = blank()
    arc(img, 3, 13, 11, 5, 85, RED_DK, 2.5)
    arc(img, 3, 13, 10, 8, 82, RED, 1.5)
    arc(img, 3, 13, 9, 15, 75, RED_HI, 0.5)
    blade_mini(img, 3, 12, 1, -1, 5)
    return outline(img)


def icon_cursed_wave():
    img = blank()
    for i, r in enumerate((6, 8.5)):
        arc(img, 4, 8, r, -70, 70, RED if i else RED_DK, 1.5)
    arc(img, 4, 8, 7, -55, 55, RED_HI, 0.5)
    for y in (4, 8, 12):
        for x in range(1, 4):
            put(img, x, y, RED_DK)
    return outline(img)


def icon_rising_cut():
    img = blank()
    for y in range(2, 15):
        put(img, 8, y, RED if y > 3 else RED_HI)
        put(img, 7, y, RED_DK if y > 6 else RED)
    arc(img, 8, 8, 5, 170, 370, RED_DK, 0.5)
    for i in range(3):
        put(img, 8 - i, 2 + i, RED_HI)
        put(img, 8 + i, 2 + i, RED_HI)
    put(img, 8, 8, WHITE)
    return outline(img)


def icon_thorn_lunge():
    img = blank()
    blade_mini(img, 2, 8, 1, 0, 9)
    for x in (12, 13, 14):
        put(img, x, 8, RED_HI)
    for (x, y) in [(5, 6), (6, 5), (8, 11), (9, 12), (11, 5), (4, 11)]:
        put(img, x, y, INK)
        put(img, x, y + (1 if y < 8 else -1), RED_DK)
    return outline(img)


def icon_thorn_guard():
    img = blank()
    for y in range(2, 13):
        put(img, 7, y, STEEL_C)
        put(img, 8, y, WHITE if y % 4 else STEEL_C)
    for y in range(12, 15):
        put(img, 7, y, hexc('#8a5428'))
        put(img, 8, y, hexc('#8a5428'))
    for a in range(0, 360, 45):
        x, y = 7.5 + math.cos(math.radians(a)) * 6, 8 - math.sin(math.radians(a)) * 6
        put(img, int(round(x)), int(round(y)), INK)
        x2, y2 = 7.5 + math.cos(math.radians(a)) * 4.5, 8 - math.sin(math.radians(a)) * 4.5
        put(img, int(round(x2)), int(round(y2)), RED)
    return outline(img)


def icon_black_thorn():
    img = blank()
    for x in range(1, 15):
        put(img, x, 14, RED_DK)
    for (x, h) in [(2, 5), (4, 9), (6, 6), (8, 12), (10, 7), (12, 10), (14, 4)]:
        for y in range(14 - h, 14):
            put(img, x, y, INK)
            if y > 14 - h + 1:
                put(img, x - 1 if x > 1 else x, y, INK if y % 2 else RED_DK)
        put(img, x, 14 - h, RED_HI)
    return outline(img)


for name, fn in {'cb_heavy_slash': icon_heavy_slash, 'cb_cursed_wave': icon_cursed_wave, 'cb_rising_cut': icon_rising_cut,
                 'cb_thorn_lunge': icon_thorn_lunge, 'cb_thorn_guard': icon_thorn_guard, 'cb_black_thorn': icon_black_thorn}.items():
    save(fn(), os.path.join(ICONS, name + '.png'))

print('cursed blade assets written: %d elements' % len(ELEMENTS))
