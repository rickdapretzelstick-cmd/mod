#!/usr/bin/env python3
"""
Builds the common curses from scratch: Blockbench models, painted textures and every animation clip.

    python3 tools/gen_curse_models.py [kind ...]

Kinds: school_maw, school_crawler, fly_head, forest_stalker (all when none are given). Writes, per kind:
  - models/bb/<kind>.bbmodel      the model (bones with pivots and rest rotations, cubes with per-face UVs)
  - textures/entity/<kind>.png    its texture (each cube face has its own island, painted by material)
  - animations/<kind>/<kind>_<clip>.json   its clips (rig <kind>)

Model space is Blockbench's: pixels (16 to the block), y up, the creature's front toward -z, +x on its left as seen
from the front. Bones rotate with the mod's animator convention (BbModel.apply): a clip's rot is [x, y, z] degrees,
x + tips a bone's far end toward the front/down (x + opens a jaw hinged at the back; x -90 swings a hanging arm
forward), z + rolls the +x side up; pos is pixels [x, up, z] with z - toward the front.

Clip timings follow the server (1 tick = 50 ms): each impact key sits on the tick the entity's code deals the hit
(SchoolMawEntity, SchoolCrawlerEntity, FlyHeadEntity, ForestStalkerEntity, CommonCurseEntity.STAGGER_TICKS).
"""
import json
import math
import os
import random
import sys
import uuid as uuidlib
import zlib

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jjk')
DENSITY = 2  # texels per model pixel


# ----------------------------------------------------------------------------------------------------------- materials

def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


class Mat:
    """How a material is painted: base colour, mottling, fine grain, dark lines (veins, cracks), and shading."""

    def __init__(self, base, blotch=None, blotch_amt=0.0, blotch_scale=4, grain=0.05, lines=None, line_count=0,
                 shade=True, highlight=None, holes=0.0, flat=False, spots=None, spot_count=0, rim=0.18, bands=None):
        self.base = rgb(base)
        self.blotch = rgb(blotch) if blotch else None
        self.blotch_amt = blotch_amt
        self.blotch_scale = blotch_scale
        self.grain = grain
        self.lines = rgb(lines) if lines else None
        self.line_count = line_count
        self.shade = shade
        self.highlight = rgb(highlight) if highlight else None
        self.holes = holes
        self.flat = flat
        self.spots = rgb(spots) if spots else None
        self.spot_count = spot_count
        self.rim = rim
        self.bands = rgb(bands) if bands else None


def value_noise(rng, w, h, cell):
    gw, gh = max(2, w // cell + 2), max(2, h // cell + 2)
    g = rng.random((gh, gw))
    ys = np.linspace(0, gh - 1.001, h)
    xs = np.linspace(0, gw - 1.001, w)
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


FACE_LIGHT = {'up': 1.12, 'down': 0.62, 'north': 1.0, 'south': 0.9, 'east': 0.94, 'west': 0.94}


def paint(img, x, y, w, h, mat, face, rng):
    col = np.ones((h, w, 3)) * np.array(mat.base)
    if mat.blotch is not None and mat.blotch_amt > 0:
        n = value_noise(rng, w, h, mat.blotch_scale)
        n = np.clip((n - 0.45) * 2.2, 0, 1)[:, :, None] * mat.blotch_amt
        col = col * (1 - n) + np.array(mat.blotch) * n
    if mat.bands is not None and h >= 2:
        for r in range(0, h, 3):
            col[r] = col[r] * 0.6 + np.array(mat.bands) * 0.4
    if mat.grain > 0:
        col = col * (1 + (rng.random((h, w, 1)) - 0.5) * 2 * mat.grain)
    if mat.spots is not None and mat.spot_count > 0 and w * h > 6:
        for _ in range(max(1, int(mat.spot_count * w * h / 64))):
            sx, sy = rng.integers(0, w), rng.integers(0, h)
            col[sy, sx] = col[sy, sx] * 0.4 + np.array(mat.spots) * 0.6
    if mat.lines is not None and mat.line_count > 0 and w > 3 and h > 3:
        for _ in range(max(1, int(mat.line_count * (w * h) / 256))):
            px, py = rng.random() * w, rng.random() * h
            ang = rng.random() * math.pi * 2
            for _ in range(int(3 + rng.random() * 8)):
                ix, iy = int(px), int(py)
                if 0 <= ix < w and 0 <= iy < h:
                    col[iy, ix] = col[iy, ix] * 0.45 + np.array(mat.lines) * 0.55
                ang += (rng.random() - 0.5) * 1.2
                px += math.cos(ang)
                py += math.sin(ang)
    if mat.shade and not mat.flat:
        col = col * FACE_LIGHT[face]
        if face in ('north', 'south', 'east', 'west') and h > 2:
            grad = np.linspace(1.06, 0.82, h)[:, None, None]
            col = col * grad
        if w >= 3 and h >= 3 and mat.rim > 0:
            edge = np.ones((h, w, 1))
            edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = 1 - mat.rim
            col = col * edge
    if mat.highlight is not None and w >= 2 and h >= 2:
        col[0, 0] = mat.highlight
        if w >= 4 and h >= 4:
            col[1, 1] = np.array(mat.highlight) * 0.7 + col[1, 1] * 0.3
    alpha = np.ones((h, w)) * 255
    if mat.holes > 0 and w * h > 8:
        hn = value_noise(rng, w, h, 3)
        alpha[hn > 1 - mat.holes] = 0
        # Ragged bottom edge on cloth.
        for c in range(w):
            if rng.random() < mat.holes * 2:
                alpha[h - 1, c] = 0
    out = np.clip(col * 255, 0, 255)
    img[y:y + h, x:x + w, :3] = out
    img[y:y + h, x:x + w, 3] = alpha


# ----------------------------------------------------------------------------------------------------------- the model

class Bone:
    def __init__(self, name, pivot, rot=(0, 0, 0), parent=None):
        self.name, self.pivot, self.rot, self.parent = name, list(pivot), list(rot), parent
        self.uuid = str(uuidlib.UUID(int=random.getrandbits(128)))
        self.children = []  # Bone or cube uuid


class Model:
    def __init__(self, name, mats):
        self.name = name
        self.mats = mats
        self.roots = []
        self.bones = {}
        self.cubes = []
        random.seed(name)

    def bone(self, name, pivot, parent=None, rot=(0, 0, 0)):
        b = Bone(name, pivot, rot, parent)
        self.bones[name] = b
        if parent is None:
            self.roots.append(b)
        else:
            self.bones[parent].children.append(b)
        return name

    def cube(self, bone, frm, to, mat, rot=None, origin=None, inflate=0.0, name=None, faces=None):
        frm, to = list(map(float, frm)), list(map(float, to))
        for i in range(3):
            if frm[i] > to[i]:
                frm[i], to[i] = to[i], frm[i]
        c = {'name': name or mat, 'uuid': str(uuidlib.UUID(int=random.getrandbits(128))), 'from': frm, 'to': to, 'mat': mat,
             'rotation': list(rot) if rot else [0, 0, 0], 'inflate': inflate,
             'origin': list(origin) if origin else [(frm[i] + to[i]) / 2 for i in range(3)],
             'faces': faces or ('north', 'south', 'east', 'west', 'up', 'down')}
        self.cubes.append(c)
        self.bones[bone].children.append(c['uuid'])
        return c

    def pair(self, bone_l, bone_r, frm, to, mat, rot=None, origin=None, inflate=0.0):
        """A cube on the +x side (in bone_l) and its mirror on the -x side (in bone_r)."""
        self.cube(bone_l, frm, to, mat, rot, origin, inflate)
        mf = [-to[0], frm[1], frm[2]]
        mt = [-frm[0], to[1], to[2]]
        mrot = [rot[0], -rot[1], -rot[2]] if rot else None
        mo = [-origin[0], origin[1], origin[2]] if origin else None
        self.cube(bone_r, mf, mt, mat, mrot, mo, inflate)

    def bone_pair(self, name, pivot, parent_l, parent_r, rot=(0, 0, 0)):
        self.bone(name + '_left', pivot, parent_l, rot)
        self.bone(name + '_right', [-pivot[0], pivot[1], pivot[2]], parent_r, (rot[0], -rot[1], -rot[2]))

    # ---- output

    def write(self):
        rng = np.random.default_rng(zlib.crc32(self.name.encode()))
        # Face sizes in texels.
        rects = []
        for c in self.cubes:
            sx, sy, sz = (c['to'][i] - c['from'][i] for i in range(3))
            dims = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}
            for f in c['faces']:
                w, h = dims[f]
                rects.append((c, f, max(1, round(w * DENSITY)), max(1, round(h * DENSITY))))
        W = 256
        while True:
            placed, ok = self.pack(rects, W)
            H = max((p[3] + p[5] for p in placed), default=1)
            size_h = 1
            while size_h < H:
                size_h *= 2
            if size_h <= W * 2 or W >= 1024:
                break
            W *= 2
        img = np.zeros((size_h, W, 4), dtype=np.uint8)
        elements = []
        uv = {}
        for c, f, x, y, w, h in placed:
            paint(img, x, y, w, h, self.mats[c['mat']], f, rng)
            uv.setdefault(c['uuid'], {})[f] = [x, y, x + w, y + h]
        for c in self.cubes:
            faces = {f: {'uv': uv[c['uuid']][f], 'texture': 0} for f in c['faces']}
            e = {'name': c['name'], 'type': 'cube', 'uuid': c['uuid'], 'from': c['from'], 'to': c['to'], 'origin': c['origin'],
                 'rotation': c['rotation'], 'box_uv': False, 'faces': faces}
            if c['inflate']:
                e['inflate'] = c['inflate']
            elements.append(e)

        def node(b):
            return {'name': b.name, 'origin': b.pivot, 'rotation': b.rot, 'uuid': b.uuid, 'export': True, 'isOpen': False,
                    'children': [node(ch) if isinstance(ch, Bone) else ch for ch in b.children]}

        d = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': self.name,
             'model_identifier': '', 'visible_box': [1, 1, 0], 'resolution': {'width': W, 'height': size_h},
             'elements': elements, 'outliner': [node(b) for b in self.roots], 'textures': []}
        with open(os.path.join(ASSETS, 'models', 'bb', self.name + '.bbmodel'), 'w') as fh:
            json.dump(d, fh, indent=1)
        Image.fromarray(img, 'RGBA').save(os.path.join(ASSETS, 'textures', 'entity', self.name + '.png'))
        print(self.name, len(self.cubes), 'cubes,', len(self.bones), 'bones, texture', W, 'x', size_h)

    @staticmethod
    def pack(rects, W):
        order = sorted(rects, key=lambda r: (-r[3], -r[2]))
        x = y = row = 0
        out = []
        for c, f, w, h in order:
            if x + w > W:
                x, y, row = 0, y + row, 0
            out.append((c, f, x, y, w, h))
            x += w
            row = max(row, h)
        return out, True


# ----------------------------------------------------------------------------------------------------------- clips

def write_clips(kind, clips):
    out = os.path.join(ASSETS, 'animations', kind)
    os.makedirs(out, exist_ok=True)
    for f in os.listdir(out):
        if f.endswith('.json'):
            os.remove(os.path.join(out, f))
    for name, c in clips.items():
        keys = []
        for t, bones in sorted(c['keys'], key=lambda k: k[0]):
            kb = {}
            for b, ch in bones.items():
                e = {}
                for k in ('rot', 'pos', 'scale'):
                    if k in ch:
                        e[k] = [round(float(v), 3) for v in ch[k]]
                if 'ease' in ch:
                    e['ease'] = ch['ease']
                kb[b] = e
            keys.append({'t': round(t, 1), 'bones': kb})
        d = {'name': kind + '_' + name, 'rig': kind, 'reference': 'tools/gen_curse_models.py', 'duration': c['duration'],
             'priority': c.get('priority', 'ATTACK'), 'blendIn': c.get('blendIn', 80), 'blendOut': c.get('blendOut', 120), 'keys': keys}
        if c.get('loop'):
            d['loop'] = True
        if c.get('hold'):
            d['hold'] = True
        with open(os.path.join(out, kind + '_' + name + '.json'), 'w') as fh:
            json.dump(d, fh, indent=1)
    print(kind, len(clips), 'clips')


class ClipBuilder:
    """Keys per time; bones listed at a time get those channels keyed there."""

    def __init__(self, duration, priority='ATTACK', loop=False, hold=False, blendIn=80, blendOut=120):
        self.c = {'duration': duration, 'priority': priority, 'loop': loop, 'hold': hold, 'blendIn': blendIn, 'blendOut': blendOut, 'keys': []}

    def key(self, t, **bones):
        self.c['keys'].append((t, {b: v for b, v in bones.items()}))
        return self

    def at(self, t, bones):
        self.c['keys'].append((t, bones))
        return self

    def done(self):
        return self.c


def R(x=0, y=0, z=0):
    return {'rot': [x, y, z]}


def P(x=0, y=0, z=0):
    return {'pos': [x, y, z]}


def RP(rot, pos):
    return {'rot': list(rot), 'pos': list(pos)}


def S(x=1, y=1, z=1):
    return {'scale': [x, y, z]}


def merge(*ds):
    out = {}
    for d in ds:
        for b, ch in d.items():
            out.setdefault(b, {}).update(ch)
    return out


def zero(bones):
    return {b: {'rot': [0, 0, 0], 'pos': [0, 0, 0]} for b in bones}


def osc(cb, bones_fn, duration, steps):
    """Keys a looping motion: bones_fn(phase 0..1) -> bones, sampled `steps` times (first == last)."""
    for i in range(steps + 1):
        ph = i / steps
        cb.at(duration * ph, bones_fn(ph % 1.0 if i < steps else 0.0))
    return cb


TAU = math.pi * 2


def sin(ph, k=1.0, off=0.0):
    return math.sin(TAU * (ph * k + off))


# ----------------------------------------------------------------------------------------------------------- posing math

def _rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def rest_min_y(m):
    """The lowest corner of the model at rest, through the bones' rest rotations (as BbModel draws it)."""
    lowest = [1e9]

    def walk(b, M):
        p = np.array(b.pivot, float)
        Rb = _rot(*(math.radians(v) for v in b.rot))
        T = np.eye(4)
        T[:3, :3] = Rb
        A = np.eye(4); A[:3, 3] = p
        B = np.eye(4); B[:3, 3] = -p
        M2 = M @ A @ T @ B
        for ch in b.children:
            if isinstance(ch, Bone):
                walk(ch, M2)
            else:
                c = next(c for c in m.cubes if c['uuid'] == ch)
                Rc = _rot(*(math.radians(v) for v in c['rotation']))
                o = np.array(c['origin'], float)
                for x in (c['from'][0], c['to'][0]):
                    for y in (c['from'][1], c['to'][1]):
                        for z in (c['from'][2], c['to'][2]):
                            v = Rc @ (np.array([x, y, z]) - o) + o
                            w = M2 @ np.array([*v, 1])
                            lowest[0] = min(lowest[0], w[1])

    for r in m.roots:
        walk(r, np.eye(4))
    return lowest[0]


def ground(m, at=0.0):
    """Moves the whole model so its lowest point rests at y = at."""
    dy = at - rest_min_y(m)
    for b in m.bones.values():
        b.pivot[1] += dy
    for c in m.cubes:
        c['from'][1] += dy
        c['to'][1] += dy
        c['origin'][1] += dy


def eye(m, bone, cx, cy, z, size, mat_iris='iris', depth=None, pupil=True, protrude=0.4):
    """An eyeball on a front face at z: sclera, iris and pupil, each standing a little proud of the last."""
    h = size / 2
    d = depth or size * 0.8
    m.cube(bone, [cx - h, cy - h, z - 0.2], [cx + h, cy + h, z + d], 'sclera')
    i = size * 0.32
    m.cube(bone, [cx - i, cy - i, z - 0.2 - protrude * 0.5], [cx + i, cy + i, z], mat_iris)
    if pupil:
        q = max(0.25, size * 0.12)
        m.cube(bone, [cx - q, cy - q * 1.6, z - 0.25 - protrude * 0.5 - 0.05], [cx + q, cy + q * 1.6, z - 0.2 - protrude * 0.5], 'pupil')


def teeth_row(m, bone, x0, x1, y_root, length, z_front, depth, step, down=True, mat='tooth', jag=0.6, rng=None):
    rng = rng or random.Random(bone)
    x = x0
    i = 0
    while x + step * 0.5 <= x1 + 1e-6:
        L = length * (0.65 + 0.7 * rng.random()) if jag else length
        w = step * 0.42
        y0, y1 = (y_root - L, y_root) if down else (y_root, y_root + L)
        m.cube(bone, [x + step * 0.5 - w, y0, z_front], [x + step * 0.5 + w, y1, z_front + depth], mat)
        x += step
        i += 1


# ----------------------------------------------------------------------------------------------------------- School Maw

def school_maw():
    mats = {
        'skin': Mat('#7a6a86', blotch='#4b3c59', blotch_amt=0.55, blotch_scale=5, lines='#3a2c46', line_count=3, spots='#9d8aa8', spot_count=2),
        'skin_dark': Mat('#57475f', blotch='#3a2d44', blotch_amt=0.5, lines='#2a2032', line_count=2),
        'belly': Mat('#b3a3ae', blotch='#8d7c8a', blotch_amt=0.4, lines='#6d5767', line_count=2, bands='#94818f'),
        'flesh': Mat('#5c111c', blotch='#2e060c', blotch_amt=0.6, highlight='#c0505e', rim=0.1),
        'tongue': Mat('#b4415c', blotch='#7c1d33', blotch_amt=0.5, lines='#6a1428', line_count=3, highlight='#e4889a'),
        'tooth': Mat('#ebe2c4', blotch='#b8a77c', blotch_amt=0.35, grain=0.04, rim=0.1),
        'claw': Mat('#2b2329', grain=0.06, highlight='#6a5a66', rim=0.1),
        'sclera': Mat('#efe7cb', blotch='#d49a8a', blotch_amt=0.35, lines='#b04040', line_count=4, rim=0.12),
        'iris': Mat('#f2c21c', blotch='#b07a08', blotch_amt=0.4, rim=0.05, shade=False),
        'pupil': Mat('#100606', grain=0.0, shade=False, highlight='#ffffff'),
        'spine': Mat('#d2c6a8', blotch='#9a8b68', blotch_amt=0.4, lines='#6e6146', line_count=2),
    }
    m = Model('school_maw', mats)
    m.bone('root', [0, 0, 0])
    m.bone('body', [0, 10, 2], 'root')
    m.bone('head', [0, 15.5, 6.5], 'body')
    m.bone('jaw', [0, 14.5, 6.5], 'body')
    m.bone('tongue', [0, 13, 5], 'jaw')
    m.bone('tongue2', [0, 13.1, -1], 'tongue')
    m.bone('tongue3', [0, 13.1, -3], 'tongue2')
    m.bone('tongue_tip', [0, 13.1, -4], 'tongue3')
    m.bone_pair('arm', [9.5, 20, 2], 'body', 'body')
    m.bone_pair('forearm', [11.5, 12.5, 2], 'arm_left', 'arm_right')
    m.bone_pair('hand', [11.7, 4.5, 2], 'forearm_left', 'forearm_right')
    m.bone_pair('leg', [4.5, 9, 3], 'body', 'body')
    m.bone_pair('foot', [4.5, 5, 3], 'leg_left', 'leg_right')

    # Body: a heavy belly under the mouth, a hump behind it.
    m.cube('body', [-7, 7, -3], [7, 15, 8], 'skin')
    m.cube('body', [-5.5, 7.6, -3.6], [5.5, 13.6, -2.8], 'belly')
    m.cube('body', [-6.5, 12.5, 3], [6.5, 19, 9.5], 'skin')
    m.cube('body', [-5, 17.5, 5], [5, 20.5, 10], 'skin_dark')
    for i, (x, z) in enumerate([(-3, 9.4), (0, 9.8), (3, 9.4), (-1.5, 7), (1.5, 7)]):
        m.cube('body', [x - 0.6, 19, z - 0.6], [x + 0.6, 21.4 + (i % 2), z + 0.6], 'spine', rot=[-25, 0, 0])

    # Head: the roof of the mouth and the skull, hinged at the back; a cluster of mismatched eyes on top.
    m.cube('head', [-9, 16, -6], [9, 23.5, 7], 'skin')
    m.cube('head', [-8.2, 15.5, -5.4], [8.2, 16, 6.5], 'flesh')
    m.cube('head', [-8.6, 21, -6.8], [8.6, 23, -5.6], 'skin_dark')
    m.cube('head', [-7, 23.5, -4], [7, 25.5, 5], 'skin')
    m.cube('head', [-5, 25.5, -2], [5, 26.3, 3.5], 'skin_dark')
    eye(m, 'head', -3.6, 24.2, -5.6, 3.6)
    eye(m, 'head', 3.9, 23.9, -5.7, 4.2)
    eye(m, 'head', 0.4, 26.0, -3.0, 1.8)
    eye(m, 'head', -7.1, 22.3, -6.0, 1.6)
    eye(m, 'head', 7.3, 21.6, -6.1, 1.4)
    for x, z in [(-5, 0), (-2, 2), (2, 1.5), (5, -0.5), (0, 4)]:
        m.cube('head', [x - 0.5, 25.6, z - 0.5], [x + 0.5, 28, z + 0.5], 'spine', rot=[-30, 0, 0])
    rr = random.Random(7)
    teeth_row(m, 'head', -8, 8, 16, 2.6, -6.1, 1.2, 1.6, True, rng=rr)
    for side in (1, -1):
        for z in (-4, -1.8, 0.4, 2.6):
            L = 1.6 + rr.random()
            m.cube('head', [side * 7.6 - 0.5, 16 - L, z - 0.5], [side * 7.6 + 0.5, 16, z + 0.5], 'tooth')
        m.cube('head', [side * 6 - 0.8, 12.4, -6.5], [side * 6 + 0.8, 16, -5.1], 'tooth')

    # Jaw: a trough (floor, walls, lower teeth) with the tongue lying in it.
    m.cube('jaw', [-8.6, 10.4, -6.5], [8.6, 12.2, 7], 'skin')
    m.cube('jaw', [-7.4, 9.6, -5.5], [7.4, 10.4, 6], 'belly')
    for side in (1, -1):
        m.cube('jaw', [side * 7.3, 12.2, -6.5], [side * 8.6, 14.6, 7], 'skin')
    m.cube('jaw', [-7.3, 12.2, -6.5], [7.3, 14.6, -5.3], 'skin')
    m.cube('jaw', [-7.3, 12.2, 6], [7.3, 14.2, 7], 'skin')
    m.cube('jaw', [-7.3, 12.2, -5.3], [7.3, 12.5, 6], 'flesh')
    teeth_row(m, 'jaw', -7.2, 7.2, 14.6, 2.2, -6.2, 1.1, 1.8, False, rng=rr)
    for side in (1, -1):
        for z in (-3, -0.6, 1.8):
            m.cube('jaw', [side * 7.9 - 0.45, 14.6, z - 0.45], [side * 7.9 + 0.45, 16, z + 0.45], 'tooth')

    # Four overlapping lengths (each slides out up to TONGUE_REACH along the one behind it).
    m.cube('tongue', [-2.6, 12.4, -1], [2.6, 13.9, 6], 'tongue')
    m.cube('tongue2', [-2.3, 12.5, -4.6], [2.3, 13.7, 1.4], 'tongue')
    m.cube('tongue3', [-2.0, 12.6, -5], [2.0, 13.5, 0.6], 'tongue')
    m.cube('tongue_tip', [-2.4, 12.4, -5.2], [2.4, 13.9, -0.6], 'tongue')
    m.cube('tongue_tip', [-1.3, 13.9, -4.8], [1.3, 15.2, -2.9], 'sclera')
    m.cube('tongue_tip', [-0.7, 14.2, -5.05], [0.7, 14.95, -4.8], 'iris')
    m.cube('tongue_tip', [-0.25, 14.35, -5.15], [0.25, 14.8, -5.0], 'pupil')

    # Long knuckle-walking arms.
    m.pair('arm_left', 'arm_right', [9, 12, -0.5], [13, 21, 4.5], 'skin')
    m.pair('arm_left', 'arm_right', [8.4, 17.5, -1.2], [13.6, 22, 5.2], 'skin_dark')
    m.pair('forearm_left', 'forearm_right', [9.5, 4, 0], [13.7, 13, 4.4], 'skin')
    m.pair('forearm_left', 'forearm_right', [9.2, 8, -0.3], [14, 10, 4.7], 'skin_dark')
    m.pair('hand_left', 'hand_right', [9, 0, -1], [14.6, 4.6, 5], 'skin_dark')
    for i in range(3):
        m.pair('hand_left', 'hand_right', [9.4 + i * 1.8, 0, -2.3], [10.6 + i * 1.8, 1.8, -0.9], 'claw')
    m.pair('hand_left', 'hand_right', [14.4, 1.4, 1], [15.4, 3.4, 2.6], 'claw')

    # Short thick legs.
    m.pair('leg_left', 'leg_right', [2, 4.5, 0.5], [7, 10, 6], 'skin')
    m.pair('foot_left', 'foot_right', [2, 0, -1.5], [7, 5, 5.5], 'skin_dark')
    for i in range(3):
        m.pair('foot_left', 'foot_right', [2.3 + i * 1.6, 0, -2.6], [3.4 + i * 1.6, 1.2, -1.5], 'claw')
    ground(m)
    m.write()

    bones = list(m.bones)
    clips = {}

    def stance(breath=0.0):
        return {'body': RP([3, 0, 0], [0, -0.5 + breath, 0]), 'arm_left': R(-8, 0, -4), 'arm_right': R(-8, 0, 4),
                'forearm_left': R(-12, 0, 0), 'forearm_right': R(-12, 0, 0), 'leg_left': R(-6, 0, 0), 'leg_right': R(-6, 0, 0),
                'foot_left': R(6, 0, 0), 'foot_right': R(6, 0, 0)}

    c = ClipBuilder(2400, 'IDLE', loop=True, blendIn=200, blendOut=200)
    osc(c, lambda ph: merge(stance(0.4 * sin(ph)), {
        'body': {'scale': [1 + 0.012 * sin(ph), 1 + 0.02 * sin(ph), 1 + 0.012 * sin(ph)]},
        'jaw': R(4 + 4 * sin(ph, 1, 0.1)), 'head': R(-2 - 2 * sin(ph, 1, 0.1)),
        'tongue_tip': R(-6 * sin(ph, 2), 8 * sin(ph, 1)), 'arm_left': R(-8 + 3 * sin(ph), 0, -4), 'arm_right': R(-8 - 3 * sin(ph), 0, 4)}), 2400, 8)
    clips['idle'] = c.done()

    c = ClipBuilder(1000, 'MOVEMENT', loop=True, blendIn=150, blendOut=150)
    osc(c, lambda ph: {
        'body': RP([8, 4 * sin(ph), 4 * sin(ph)], [0, -0.6 + 0.9 * abs(sin(ph)), 0]),
        'arm_left': R(-10 - 28 * sin(ph), 0, -4), 'arm_right': R(-10 + 28 * sin(ph), 0, 4),
        'forearm_left': R(-14 + 10 * max(0, sin(ph)), 0, 0), 'forearm_right': R(-14 + 10 * max(0, -sin(ph)), 0, 0),
        'leg_left': R(22 * sin(ph)), 'leg_right': R(-22 * sin(ph)),
        'foot_left': R(-10 * max(0, sin(ph, 1, 0.25))), 'foot_right': R(-10 * max(0, -sin(ph, 1, 0.25))),
        'jaw': R(6 + 4 * abs(sin(ph))), 'head': R(-3)}, 1000, 8)
    clips['move'] = c.done()

    def bite(hit, end):
        c = ClipBuilder(end, 'ATTACK')
        gape = hit * 0.72
        c.at(0, merge(stance(), {'jaw': R(0), 'head': R(0), 'body': RP([3, 0, 0], [0, -0.5, 0])}))
        c.at(gape, merge(stance(), {'jaw': R(58), 'head': R(-26), 'body': RP([-10, 0, 0], [0, 0.6, 2]),
                                    'arm_left': R(-30, 0, -25), 'arm_right': R(-30, 0, 25), 'tongue': R(-10)}))
        c.at(hit - 100, merge(stance(), {'jaw': R(62), 'head': R(-30), 'body': RP([-6, 0, 0], [0, 0.4, 0])}))
        c.at(hit, merge(stance(), {'jaw': R(-2), 'head': R(8), 'body': RP([20, 0, 0], [0, -1.5, -4]),
                                   'arm_left': R(-45, 0, -10), 'arm_right': R(-45, 0, 10), 'tongue': R(0)}))
        c.at(hit + 150, merge(stance(), {'jaw': R(4), 'head': R(4), 'body': RP([16, 0, 0], [0, -1.2, -3.4])}))
        c.at(end, merge(stance(), {'jaw': R(4), 'head': R(-2)}))
        return c.done()

    clips['attack'] = bite(700, 1000)
    clips['bite_quick'] = bite(350, 650)

    c = ClipBuilder(900, 'ATTACK')
    c.at(0, merge(stance(), {'jaw': R(0), 'head': R(0)}))
    c.at(400, merge(stance(), {'jaw': R(34), 'head': R(-18), 'body': RP([-8, 0, 0], [0, 0.5, 1.5]), 'tongue': R(-30),
                               'tongue2': P(0, 0, 1), 'tongue_tip': R(-20)}))
    c.at(500, merge(stance(), {'jaw': R(38), 'head': R(-20), 'body': RP([12, 0, 0], [0, -0.5, -2]), 'tongue': R(-36),
                               'tongue2': RP([0, 0, 0], [0, 0, -5.5]), 'tongue3': RP([0, 0, 0], [0, 0, -5.5]), 'tongue_tip': RP([-4, 0, 0], [0, 0, -5.5])}))
    c.at(650, merge(stance(), {'jaw': R(36), 'head': R(-18), 'body': RP([10, 0, 0], [0, -0.4, -1.5]), 'tongue': R(-34),
                               'tongue2': RP([0, 0, 0], [0, 0, -5]), 'tongue3': RP([0, 0, 0], [0, 0, -5]), 'tongue_tip': RP([6, 0, 0], [0, 0, -5])}))
    c.at(900, merge(stance(), {'jaw': R(6), 'head': R(-2), 'tongue': R(0), 'tongue2': RP([0, 0, 0], [0, 0, 0]),
                               'tongue3': RP([0, 0, 0], [0, 0, 0]), 'tongue_tip': RP([0, 0, 0], [0, 0, 0])}))
    clips['lash'] = c.done()

    c = ClipBuilder(1100, 'ATTACK')
    c.at(0, stance())
    c.at(650, merge(stance(), {'body': RP([-28, 0, 0], [0, 2.5, 2]), 'head': R(-20), 'jaw': R(40),
                               'arm_left': R(-170, 0, -18), 'arm_right': R(-170, 0, 18), 'forearm_left': R(-30), 'forearm_right': R(-30),
                               'leg_left': R(-20), 'leg_right': R(-20), 'foot_left': R(20), 'foot_right': R(20)}))
    c.at(820, merge(stance(), {'body': RP([-32, 0, 0], [0, 3, 2.4]), 'head': R(-22), 'jaw': R(48),
                               'arm_left': R(-178, 0, -14), 'arm_right': R(-178, 0, 14), 'forearm_left': R(-40), 'forearm_right': R(-40)}))
    c.at(900, merge(stance(), {'body': RP([34, 0, 0], [0, -3, -3]), 'head': R(10), 'jaw': R(10),
                               'arm_left': R(-62, 0, -8), 'arm_right': R(-62, 0, 8), 'forearm_left': R(-10), 'forearm_right': R(-10),
                               'leg_left': R(-34), 'leg_right': R(-34), 'foot_left': R(30), 'foot_right': R(30)}))
    c.at(1100, merge(stance(), {'body': RP([30, 0, 0], [0, -2.6, -2.6]), 'head': R(8), 'jaw': R(14),
                                'arm_left': R(-58, 0, -8), 'arm_right': R(-58, 0, 8), 'leg_left': R(-30), 'leg_right': R(-30),
                                'foot_left': R(26), 'foot_right': R(26)}))
    clips['slam'] = c.done()

    c = ClipBuilder(1400, 'ATTACK', blendIn=120)

    def slump(ph):
        return merge(stance(), {'body': RP([30 + 3 * sin(ph, 2), 0, 2 * sin(ph)], [0, -2.2 + 0.4 * sin(ph, 2), -1.5]),
                                'head': R(18), 'jaw': R(46 + 4 * sin(ph, 2)), 'tongue': R(14), 'tongue2': RP([12, 0, 0], [0, 0, -3]),
                                'tongue3': RP([16, 0, 0], [0, 0, -2]), 'tongue_tip': R(20 + 8 * sin(ph, 3)),
                                'arm_left': R(-40, 0, -6), 'arm_right': R(-40, 0, 6), 'forearm_left': R(-5), 'forearm_right': R(-5),
                                'leg_left': R(-24), 'leg_right': R(-24), 'foot_left': R(22), 'foot_right': R(22)})

    for i in range(8):
        c.at(i * 200, slump(i / 7))
    clips['recover'] = c.done()

    c = ClipBuilder(400, 'SPECIAL', blendIn=40)
    c.at(0, stance())
    c.at(90, merge(stance(), {'body': RP([-14, 0, 6], [0, 0.4, 1.8]), 'head': R(-12), 'jaw': R(24), 'arm_left': R(-24, 0, -30), 'arm_right': R(-20, 0, 26)}))
    c.at(400, stance())
    clips['hurt'] = c.done()

    c = ClipBuilder(1700, 'SPECIAL', blendIn=40, blendOut=200)
    sway = [(0, 0), (120, 1), (400, -1), (700, 1), (1000, -0.7), (1300, 0.4), (1700, 0)]
    for t, s in sway:
        k = 1 - t / 1700
        c.at(t, merge(stance(), {'body': RP([-22 * k, 0, 18 * s * k], [0, -1.5 * k, 3 * k]), 'head': R(-14 * k, 0, -10 * s * k),
                                 'jaw': R(40 * k), 'arm_left': R(-60 * k, 0, -50 * k), 'arm_right': R(-40 * k, 0, 55 * k),
                                 'leg_left': R(-16 * k * s), 'leg_right': R(16 * k * s), 'tongue_tip': R(20 * k * s)}))
    clips['stagger'] = c.done()

    c = ClipBuilder(1200, 'RAGDOLL', hold=True, blendIn=60)
    c.at(0, stance())
    c.at(250, merge(stance(), {'body': RP([-20, 0, 10], [0, 0, 1]), 'head': R(-20), 'jaw': R(50), 'arm_left': R(-80, 0, -40), 'arm_right': R(-70, 0, 40)}))
    c.at(800, merge(stance(), {'body': RP([-84, 0, 14], [0, -6, 8]), 'head': R(-10), 'jaw': R(62), 'arm_left': R(-120, 0, -70),
                               'arm_right': R(-110, 0, 70), 'leg_left': R(-50), 'leg_right': R(-40), 'tongue': R(30), 'tongue2': RP([20, 0, 0], [0, 0, -4])}))
    c.at(1200, merge(stance(), {'body': RP([-88, 0, 14], [0, -6.5, 8.5]), 'head': R(-6), 'jaw': R(60), 'arm_left': R(-124, 0, -72),
                                'arm_right': R(-114, 0, 72), 'leg_left': R(-54), 'leg_right': R(-44), 'tongue': R(34), 'tongue2': RP([24, 0, 0], [0, 0, -4])}))
    clips['death'] = c.done()
    write_clips('school_maw', clips)



# ----------------------------------------------------------------------------------------------------------- School Crawler

CRAWLER_ARMS = ((1, -3.5, 30), (2, 1.5, 0), (3, 6.5, -32))  # (index, z, splay forward degrees)


def limb(sign, fwd=0.0, lift=0.0, twist=0.0):
    """An arm's rotation from its sideways rest: fwd swings it toward the front, lift raises it (sign: +1 the +x side)."""
    return {'rot': [twist, -fwd * sign, lift * sign]}


def school_crawler():
    mats = {
        'skin': Mat('#cdbda6', blotch='#937e6a', blotch_amt=0.5, blotch_scale=4, lines='#7a5f52', line_count=3, spots='#a88f7c', spot_count=2),
        'skin_dark': Mat('#8f7b69', blotch='#6a5747', blotch_amt=0.5, lines='#4d3d32', line_count=2),
        'ribs': Mat('#b9a790', bands='#6f5a4a', grain=0.04),
        'flesh': Mat('#5a0f18', blotch='#2a0509', blotch_amt=0.6, highlight='#b8505c', rim=0.1),
        'tooth': Mat('#efe8d0', blotch='#c0b088', blotch_amt=0.3, grain=0.04, rim=0.1),
        'claw': Mat('#3a2f29', grain=0.06, highlight='#7a6a60', rim=0.1),
        'sclera': Mat('#f2ead6', blotch='#d8a090', blotch_amt=0.3, lines='#b84848', line_count=4, rim=0.12),
        'iris': Mat('#c41818', blotch='#6a0808', blotch_amt=0.4, rim=0.05, shade=False),
        'pupil': Mat('#0a0404', grain=0.0, shade=False, highlight='#ffffff'),
    }
    m = Model('school_crawler', mats)
    m.bone('root', [0, 0, 0])
    m.bone('body', [0, 8, 2], 'root')
    m.bone('head', [0, 9, -5], 'body')
    m.bone('jaw', [0, 6.6, -6], 'head')
    m.bone('tail', [0, 8, 8], 'body')
    m.bone('tail2', [0, 8, 13], 'tail')
    for n, z, splay in CRAWLER_ARMS:
        m.bone_pair('arm%d' % n, [5, 9, z], 'body', 'body', rot=(0, splay, 28))
        m.bone_pair('forearm%d' % n, [12, 9, z], 'arm%d_left' % n, 'arm%d_right' % n, rot=(0, 0, -10))
        m.bone_pair('hand%d' % n, [12, -3, z], 'forearm%d_left' % n, 'forearm%d_right' % n)

    m.cube('body', [-5, 5, -5.5], [5, 11.5, 8], 'skin')
    m.cube('body', [-4, 4.4, -4], [4, 5, 7], 'skin_dark')
    for sd in (1, -1):
        m.cube('body', [sd * 5, 6.5, -3], [sd * 5.4, 10.5, 5], 'ribs')
    for z in (-4, -1.5, 1, 3.5, 6):
        m.cube('body', [-1, 11.5, z], [1, 12.6, z + 1.4], 'skin_dark')
        m.cube('body', [-0.4, 12.6, z + 0.3], [0.4, 13.4, z + 1.0], 'claw')

    m.cube('head', [-4.6, 6.6, -11.6], [4.6, 13.2, -5], 'skin')
    m.cube('head', [-4.2, 11.4, -12.2], [4.2, 12.4, -11.4], 'skin_dark')
    m.cube('head', [-0.8, 9.2, -12.3], [0.8, 10.8, -11.6], 'skin_dark')
    for sd in (1, -1):
        m.cube('head', [sd * 4.6, 7, -11], [sd * 5.2, 10, -7], 'skin')
    eye(m, 'head', -2.2, 10.6, -11.6, 2.6)
    eye(m, 'head', 2.4, 10.4, -11.6, 2.2)
    eye(m, 'head', -3.9, 12.7, -11.0, 1.1)
    eye(m, 'head', 3.8, 12.8, -11.0, 1.3)
    eye(m, 'head', 0.1, 12.9, -10.8, 1.2)
    m.cube('head', [-4, 6.6, -11.4], [4, 7.1, -5.5], 'flesh')
    rr = random.Random(11)
    teeth_row(m, 'head', -4, 4, 7.0, 1.7, -11.95, 0.8, 1.0, True, rng=rr)
    m.cube('jaw', [-4.3, 4.6, -12], [4.3, 6.6, -5.6], 'skin')
    m.cube('jaw', [-3.7, 6.6, -11.4], [3.7, 6.8, -6], 'flesh')
    teeth_row(m, 'jaw', -3.6, 3.6, 6.6, 1.5, -11.95, 0.8, 1.2, False, rng=rr)

    m.cube('tail', [-4, 5.6, 8], [4, 10.6, 13], 'skin')
    m.cube('tail', [-0.9, 10.6, 9], [0.9, 11.4, 12], 'skin_dark')
    m.cube('tail2', [-2.8, 6.4, 13], [2.8, 9.6, 17], 'skin')
    m.cube('tail2', [-0.6, 7.4, 17], [0.6, 8.6, 19.5], 'claw')

    for n, z, _ in CRAWLER_ARMS:
        L, F, H = 'arm%d_left' % n, 'forearm%d_left' % n, 'hand%d_left' % n
        m.pair(L, 'arm%d_right' % n, [5, 8.2, z - 1.1], [12, 9.8, z + 1.1], 'skin')
        m.pair(F, 'forearm%d_right' % n, [11.2, 8, z - 1.3], [12.8, 10, z + 1.3], 'skin_dark')
        m.pair(F, 'forearm%d_right' % n, [11.1, -3, z - 0.85], [12.9, 9, z + 0.85], 'skin')
        m.pair(H, 'hand%d_right' % n, [10.4, -3.8, z - 1.7], [13.6, -2.6, z + 1.7], 'skin_dark')
        for k in range(3):
            m.pair(H, 'hand%d_right' % n, [13.6, -3.8, z - 1.5 + k * 1.1], [15.2, -3.2, z - 0.8 + k * 1.1], 'claw')
    ground(m)
    m.write()

    arms = [(n, sd, sg) for n, _, _ in CRAWLER_ARMS for sd, sg in (('left', 1), ('right', -1))]
    tripod_a = {(1, 'left'), (2, 'right'), (3, 'left')}

    def legs(fn):
        out = {}
        for n, sd, sg in arms:
            up, fore = fn(n, sd, sg)
            out['arm%d_%s' % (n, sd)] = up
            if fore is not None:
                out['forearm%d_%s' % (n, sd)] = fore
        return out

    clips = {}
    c = ClipBuilder(1600, 'IDLE', loop=True, blendIn=200, blendOut=200)
    osc(c, lambda ph: merge({'body': RP([0, 0, 1.5 * sin(ph)], [0, 0.35 * sin(ph, 2), 0]), 'body_': {},
                             'head': R(4 * sin(ph, 1, 0.2), 8 * sin(ph, 1, 0.5), 6 * sin(ph, 1)), 'jaw': R(5 + 5 * max(0, sin(ph, 2))),
                             'tail': R(6 * sin(ph, 1, 0.3), 8 * sin(ph)), 'tail2': R(0, 10 * sin(ph, 1, 0.25))},
                            legs(lambda n, sd, sg: (limb(sg, 3 * sin(ph, 1, n * 0.2), 2 * sin(ph, 2, n * 0.3 + (sg > 0) * 0.5)), None)),
                            {'arm1_left': limb(1, 3, 6 + 14 * max(0, sin(ph, 2, 0.1)))}), 1600, 16)
    for k in c.c['keys']:
        k[1].pop('body_', None)
    clips['idle'] = c.done()

    c = ClipBuilder(600, 'MOVEMENT', loop=True, blendIn=120, blendOut=150)

    def gait(ph):
        def one(n, sd, sg):
            q = ph if (n, sd) in tripod_a else (ph + 0.5) % 1.0
            swing = sin(q)
            lift = max(0.0, sin(q, 1, 0.25)) * 22
            return limb(sg, 22 * swing, lift), {'rot': [0, 0, -sg * lift * 0.5]}
        return merge({'body': RP([2, 3 * sin(ph), 3 * sin(ph)], [0, 0.6 * abs(sin(ph, 2)), 0]), 'head': R(-2, -4 * sin(ph), -3 * sin(ph)),
                      'jaw': R(10 + 6 * abs(sin(ph, 2))), 'tail': R(4, 10 * sin(ph)), 'tail2': R(0, 14 * sin(ph, 1, 0.25))}, legs(one))
    osc(c, gait, 600, 12)
    clips['move'] = c.done()

    crouch = merge({'body': RP([6, 0, 0], [0, -3.2, 1]), 'head': R(10), 'jaw': R(34), 'tail': R(22), 'tail2': R(14)},
                   legs(lambda n, sd, sg: (limb(sg, 8 if n == 1 else -4, 26), {'rot': [0, 0, -sg * 14]})))
    c = ClipBuilder(600, 'ATTACK', blendIn=60)
    c.at(0, merge({'body': RP([0, 0, 0], [0, 0, 0]), 'head': R(0), 'jaw': R(5), 'tail': R(0), 'tail2': R(0)},
                  legs(lambda n, sd, sg: (limb(sg), {'rot': [0, 0, 0]}))))
    c.at(380, crouch)
    for t, j in ((450, 1), (520, -1), (600, 1)):
        c.at(t, merge(crouch, {'body': RP([6, 0, 1.5 * j], [0, -3.4, 1])}))
    clips['crouch'] = c.done()

    c = ClipBuilder(700, 'ATTACK', blendIn=40)
    reach = merge({'body': RP([-16, 0, 0], [0, 2.5, -2]), 'head': R(-14), 'jaw': R(55), 'tail': R(-14), 'tail2': R(-10)},
                  legs(lambda n, sd, sg: ((limb(sg, 70, 40), {'rot': [-30, 0, -sg * 30]}) if n == 1 else
                                          (limb(sg, 30, 20), {'rot': [0, 0, -sg * 10]}) if n == 2 else
                                          (limb(sg, -60, 10), {'rot': [30, 0, sg * 10]}))))
    c.at(0, crouch)
    c.at(120, reach)
    c.at(400, merge(reach, {'body': RP([8, 0, 0], [0, 0.6, -2])}))
    c.at(700, merge({'body': RP([10, 0, 0], [0, -1, -1]), 'head': R(6), 'jaw': R(20), 'tail': R(6), 'tail2': R(0)},
                    legs(lambda n, sd, sg: (limb(sg, 10 if n == 1 else 0, 6), {'rot': [0, 0, 0]}))))
    clips['attack'] = c.done()

    def flat(ph):
        tw = sin(ph, 3)
        return merge({'body': RP([4, 0, 3 * tw], [0, -4.6, 0]), 'head': R(22, 10 * sin(ph), 12), 'jaw': R(28 + 6 * tw), 'tail': R(-6), 'tail2': R(-8)},
                     legs(lambda n, sd, sg: (limb(sg, (34 if n == 1 else 0 if n == 2 else -34), -4 + 5 * sin(ph, 4, n * 0.3)),
                                             {'rot': [0, 0, sg * 48]})))
    c = ClipBuilder(1500, 'ATTACK', blendIn=60)
    for k in range(9):
        c.at(k * 150, flat(k / 8))
    c.at(1500, merge(flat(0), {'body': RP([0, 0, 0], [0, -2, 0])}))
    clips['sprawl'] = c.done()

    c = ClipBuilder(900, 'ATTACK', blendIn=60)
    rest_legs = legs(lambda n, sd, sg: (limb(sg), {'rot': [0, 0, 0]}))
    c.at(0, merge({'body': RP([0, 0, 0], [0, 0, 0]), 'head': R(0), 'jaw': R(5)}, rest_legs))
    up = merge({'body': RP([-18, 0, 0], [0, 1.5, 1.5]), 'head': R(-12), 'jaw': R(40)}, rest_legs,
               {'arm1_left': limb(1, 30, 58), 'arm1_right': limb(-1, 30, 58),
                'forearm1_left': {'rot': [-40, 0, -20]}, 'forearm1_right': {'rot': [-40, 0, 20]}})
    c.at(380, up)
    c.at(500, merge({'body': RP([14, 0, 0], [0, -1.2, -2.5]), 'head': R(10), 'jaw': R(10)}, rest_legs,
                    {'arm1_left': limb(1, 60, -2), 'arm1_right': limb(-1, 60, -2),
                     'forearm1_left': {'rot': [30, 0, 30]}, 'forearm1_right': {'rot': [30, 0, -30]}}))
    c.at(900, merge({'body': RP([0, 0, 0], [0, 0, 0]), 'head': R(0), 'jaw': R(5)}, rest_legs))
    clips['swipe'] = c.done()

    c = ClipBuilder(350, 'ATTACK', blendIn=40)
    c.at(0, crouch)
    c.at(140, merge(crouch, {'body': RP([0, 0, 26], [3, -1.5, 0]), 'head': R(0, 20, -10)},
                    legs(lambda n, sd, sg: (limb(sg, 0, 30 if sg > 0 else -6), {'rot': [0, 0, -sg * 20 if sg > 0 else 0]}))))
    c.at(350, merge(crouch, {'body': RP([0, 0, -8], [0, -2.5, 0])}))
    clips['feint'] = c.done()

    c = ClipBuilder(400, 'SPECIAL', blendIn=30)
    c.at(0, rest_legs)
    c.at(90, merge({'body': RP([-12, 0, 14], [0, 1, 1.5]), 'head': R(-16, 0, -10), 'jaw': R(30)},
                   legs(lambda n, sd, sg: (limb(sg, 0, 20 if sg > 0 else -10), None))))
    c.at(400, merge({'body': RP([0, 0, 0], [0, 0, 0]), 'head': R(0), 'jaw': R(5)}, rest_legs))
    clips['hurt'] = c.done()

    c = ClipBuilder(1700, 'SPECIAL', blendIn=30, blendOut=200)
    for t, s_ in ((0, 0), (120, 1), (380, -1), (650, 1), (950, -0.6), (1300, 0.3), (1700, 0)):
        k = 1 - t / 1700
        c.at(t, merge({'body': RP([-20 * k, 0, 32 * s_ * k], [0, 2 * k, 2 * k]), 'head': R(-20 * k, 14 * s_ * k, -12 * s_ * k), 'jaw': R(45 * k),
                       'tail': R(-20 * k, 20 * s_ * k)},
                      legs(lambda n, sd, sg: (limb(sg, 30 * s_ * k * (1 if n % 2 else -1), 40 * k), {'rot': [0, 0, sg * 30 * k]}))))
    clips['stagger'] = c.done()

    c = ClipBuilder(1200, 'RAGDOLL', hold=True, blendIn=40)
    c.at(0, rest_legs)
    c.at(300, merge({'body': RP([-20, 0, 70], [0, 3, 0]), 'head': R(-20, 0, 0), 'jaw': R(50)},
                    legs(lambda n, sd, sg: (limb(sg, 0, 40), {'rot': [0, 0, sg * 20]}))))
    curled = merge({'body': RP([0, 0, 176], [0, 4.5, 0]), 'head': R(20, 0, 0), 'jaw': R(60), 'tail': R(30), 'tail2': R(30)},
                   legs(lambda n, sd, sg: (limb(sg, 0, 70), {'rot': [0, 0, -sg * 80]})))
    c.at(800, curled)
    c.at(1200, merge(curled, legs(lambda n, sd, sg: (limb(sg, 0, 76), {'rot': [0, 0, -sg * 95]}))))
    clips['death'] = c.done()
    write_clips('school_crawler', clips)


# ----------------------------------------------------------------------------------------------------------- Fly Head

FLY_LEGS = ((1, -1.0), (2, 0.6), (3, 2.2))


def fly_head():
    mats = {
        'skin': Mat('#cbbd8a', blotch='#8b8550', blotch_amt=0.55, blotch_scale=3, lines='#7a4636', line_count=5, spots='#a39a62', spot_count=3),
        'skin_dark': Mat('#8e8656', blotch='#5e5934', blotch_amt=0.5, lines='#4a3424', line_count=3),
        'compound': Mat('#7a0d12', spots='#ff5a5a', spot_count=24, blotch='#3a0408', blotch_amt=0.5, highlight='#ffd0d0', rim=0.05),
        'wing': Mat('#cfd8dc', lines='#4c4a40', line_count=10, grain=0.03, rim=0.35, blotch='#9aa4a8', blotch_amt=0.3),
        'leg': Mat('#2a2420', grain=0.05, rim=0.0),
        'flesh': Mat('#4a0a10', blotch='#200306', blotch_amt=0.5, rim=0.05),
        'tooth': Mat('#ece4c8', grain=0.04, rim=0.08),
        'sclera': Mat('#f0e8d0', lines='#b84848', line_count=3, rim=0.1),
        'iris': Mat('#3c9c3c', blotch='#1a5a1a', blotch_amt=0.4, shade=False, rim=0.05),
        'pupil': Mat('#060404', grain=0.0, shade=False, highlight='#ffffff'),
    }
    m = Model('fly_head', mats)
    m.bone('root', [0, 0, 0])
    m.bone('body', [0, 8, 0], 'root')
    m.bone('jaw', [0, 4.8, -1.5], 'body')
    m.bone_pair('wing', [2, 11.5, 1.5], 'body', 'body', rot=(0, -12, 18))
    m.bone_pair('wing2', [2, 11.1, 3.5], 'body', 'body', rot=(0, -28, 10))
    for n, z in FLY_LEGS:
        m.bone_pair('leg%d' % n, [1.6, 4.8, z], 'body', 'body', rot=(0, 0, 18))
    m.bone_pair('antenna', [1.3, 12.6, -3.2], 'body', 'body', rot=(-28, 0, -12))

    m.cube('body', [-4.5, 4.6, -4.5], [4.5, 12, 4], 'skin')
    m.cube('body', [-3.6, 12, -3.4], [3.6, 13.2, 2.8], 'skin')
    m.cube('body', [-3.5, 5.5, 4], [3.5, 11, 5.2], 'skin_dark')
    for sd in (1, -1):
        m.cube('body', [sd * 4.5, 5.4, -3.8], [sd * 5.1, 8.6, 0.2], 'skin')
        m.cube('body', [sd * 2.4, 7.4, -5.1], [sd * 5.5, 11.8, -1.2], 'compound')
        m.cube('body', [sd * 3.0, 8.0, -5.4], [sd * 5.0, 11.2, -4.9], 'compound')
    eye(m, 'body', 0, 10.4, -4.5, 1.9)
    m.cube('body', [-0.6, 7.0, -5.0], [0.6, 8.6, -4.5], 'skin_dark')
    m.cube('body', [-3.0, 4.7, -4.75], [3.0, 6.6, -4.5], 'flesh')
    rr = random.Random(5)
    teeth_row(m, 'body', -2.8, 2.8, 6.6, 1.1, -4.95, 0.5, 0.8, True, rng=rr)
    m.cube('jaw', [-3.2, 3.4, -4.9], [3.2, 4.7, -1], 'skin')
    teeth_row(m, 'jaw', -2.6, 2.6, 4.7, 1.0, -4.95, 0.5, 0.9, False, rng=rr)

    m.pair('wing_left', 'wing_right', [2, 11.5, 0.4], [12.5, 11.7, 4.2], 'wing')
    m.pair('wing_left', 'wing_right', [2, 11.42, 0.3], [12.5, 11.78, 0.8], 'leg')
    m.pair('wing2_left', 'wing2_right', [2, 11.1, 3.0], [9.5, 11.3, 6.2], 'wing')
    for n, z in FLY_LEGS:
        m.pair('leg%d_left' % n, 'leg%d_right' % n, [1.35, 0.6, z - 0.25], [1.85, 4.8, z + 0.25], 'leg')
        m.pair('leg%d_left' % n, 'leg%d_right' % n, [1.35, 0.3, z - 1.0], [1.85, 0.7, z + 0.25], 'leg')
    m.pair('antenna_left', 'antenna_right', [1.1, 12.6, -3.4], [1.5, 16.4, -3.0], 'leg')
    m.pair('antenna_left', 'antenna_right', [0.9, 16.2, -3.6], [1.7, 17.0, -2.8], 'skin_dark')
    m.write()

    def flap(ph, amp=42, base=8):
        a = base + amp * sin(ph)
        b = base * 0.6 + amp * 0.8 * sin(ph, 1, 0.12)
        return {'wing_left': R(0, 0, a), 'wing_right': R(0, 0, -a), 'wing2_left': R(0, 0, b), 'wing2_right': R(0, 0, -b)}

    def dangle(ph, k=1.0):
        out = {}
        for n, _ in FLY_LEGS:
            out['leg%d_left' % n] = R(10 * k * sin(ph, 1, n * 0.21), 0, 6 * k * sin(ph, 1, n * 0.33))
            out['leg%d_right' % n] = R(10 * k * sin(ph, 1, n * 0.21 + 0.5), 0, -6 * k * sin(ph, 1, n * 0.33 + 0.5))
        return out

    clips = {}
    c = ClipBuilder(1000, 'IDLE', loop=True, blendIn=150, blendOut=150)
    osc(c, lambda ph: merge({'body': RP([3 * sin(ph, 1, 0.25), 0, 4 * sin(ph)], [0, 0.9 * sin(ph), 0]), 'jaw': R(6 + 6 * max(0, sin(ph, 3))),
                             'antenna_left': R(8 * sin(ph, 2)), 'antenna_right': R(8 * sin(ph, 2, 0.3))},
                            flap(ph * 10 % 1.0), dangle(ph)), 1000, 40)
    clips['idle'] = c.done()
    c = ClipBuilder(1000, 'MOVEMENT', loop=True, blendIn=150, blendOut=150)
    osc(c, lambda ph: merge({'body': RP([16, 0, 6 * sin(ph)], [0, 0.6 * sin(ph, 2), 0]), 'jaw': R(10),
                             'antenna_left': R(-20), 'antenna_right': R(-20)},
                            flap(ph * 12 % 1.0, 48), dangle(ph, 1.6)), 1000, 48)
    clips['move'] = c.done()

    c = ClipBuilder(1150, 'ATTACK', blendIn=40)
    for k in range(10):
        t = k * 45
        c.at(t, merge({'body': RP([-24 * min(1, t / 300), 0, 0], [0, 1.2 * min(1, t / 300), 2 * min(1, t / 300)]), 'jaw': R(50 * min(1, t / 300)),
                       'antenna_left': R(-30), 'antenna_right': R(-30)}, flap((k % 2) * 0.5 + 0.25, 30, 30), dangle(0, 0)))
    dive = merge({'body': RP([42, 0, 0], [0, -1, -3]), 'jaw': R(60), 'wing_left': R(0, -40, 10), 'wing_right': R(0, 40, -10),
                  'wing2_left': R(0, -40, 4), 'wing2_right': R(0, 40, -4), 'antenna_left': R(-50), 'antenna_right': R(-50)},
                 {('leg%d_%s' % (n, sd)): R(50) for n, _ in FLY_LEGS for sd in ('left', 'right')})
    c.at(500, dive)
    c.at(1150, dive)
    clips['attack'] = c.done()

    c = ClipBuilder(800, 'ATTACK', blendIn=60)
    for k in range(13):
        t = k * 50
        puff = min(1, t / 520)
        c.at(t, merge({'body': {'rot': [-6 * puff, 0, 0], 'pos': [0, 0, 1.2 * puff], 'scale': [1 + 0.14 * puff, 1 + 0.1 * puff, 1 + 0.14 * puff]},
                       'jaw': R(-4 * puff)}, flap((k % 2) * 0.5, 36)))
    c.at(620, merge({'body': {'rot': [-16, 0, 0], 'pos': [0, 0.5, 2.5], 'scale': [0.94, 0.96, 0.94]}, 'jaw': R(58)}, flap(0.25, 36)))
    c.at(800, merge({'body': {'rot': [0, 0, 0], 'pos': [0, 0, 0], 'scale': [1, 1, 1]}, 'jaw': R(10)}, flap(0.75, 36)))
    clips['spit'] = c.done()

    def floored(ph):
        return merge({'body': RP([10, 0, 78 + 4 * sin(ph, 3)], [0, -5.4, 0]), 'jaw': R(30 + 10 * sin(ph, 4)),
                      'wing_left': R(0, 0, -50), 'wing_right': R(0, 0, -20 + 40 * max(0, sin(ph, 6))),
                      'wing2_left': R(0, 0, -40), 'wing2_right': R(0, 0, -10 + 30 * max(0, sin(ph, 6, 0.2))),
                      'antenna_left': R(20), 'antenna_right': R(-10 + 20 * sin(ph, 5))},
                     {('leg%d_%s' % (n, sd)): R(30 * sin(ph, 6, n * 0.3 + (sd == 'left') * 0.5), 0, 20) for n, _ in FLY_LEGS for sd in ('left', 'right')})
    c = ClipBuilder(2000, 'ATTACK', blendIn=80)
    for k in range(17):
        c.at(k * 100, floored(k / 16))
    c.at(2000, merge({'body': RP([0, 0, 20], [0, -2, 0])}, flap(0.25)))
    clips['grounded'] = c.done()

    c = ClipBuilder(400, 'SPECIAL', blendIn=30)
    c.at(0, merge({'body': RP([0, 0, 0], [0, 0, 0])}, flap(0)))
    c.at(100, merge({'body': RP([-20, 30, 40], [0, 1.5, 1.5]), 'jaw': R(40)}, flap(0.25, 60)))
    c.at(250, merge({'body': RP([10, -10, -20], [0, 0.5, 0.5]), 'jaw': R(20)}, flap(0.75, 60)))
    c.at(400, merge({'body': RP([0, 0, 0], [0, 0, 0]), 'jaw': R(6)}, flap(0)))
    clips['hurt'] = c.done()
    clips['stagger'] = dict(c.done(), duration=400)

    c = ClipBuilder(1200, 'RAGDOLL', hold=True, blendIn=40)
    c.at(0, flap(0))
    c.at(300, merge({'body': RP([-20, 0, 60], [0, 0, 0]), 'jaw': R(50)}, flap(0.25, 60)))
    c.at(900, merge({'body': RP([10, 0, 100], [0, -6.5, 0]), 'jaw': R(55), 'wing_left': R(0, 0, -60), 'wing_right': R(0, 0, -10),
                     'wing2_left': R(0, 0, -50), 'wing2_right': R(0, 0, -5)},
                    {('leg%d_%s' % (n, sd)): R(40, 0, 30) for n, _ in FLY_LEGS for sd in ('left', 'right')}))
    c.at(1200, merge({'body': RP([10, 0, 100], [0, -6.8, 0]), 'jaw': R(58)}))
    clips['death'] = c.done()
    write_clips('fly_head', clips)


# ----------------------------------------------------------------------------------------------------------- the Hunter's Shade

def forest_stalker():
    mats = {
        'skin': Mat('#3e3c37', blotch='#24231f', blotch_amt=0.6, blotch_scale=3, lines='#1a1916', line_count=3, spots='#57534a', spot_count=2),
        'skin_dark': Mat('#26241f', blotch='#151411', blotch_amt=0.5, lines='#0e0d0b', line_count=2),
        'ribs': Mat('#4a4740', bands='#1c1b18', grain=0.05),
        'bone': Mat('#dcd2b6', blotch='#a49778', blotch_amt=0.45, lines='#6e6248', line_count=4, grain=0.05),
        'antler': Mat('#b9a886', blotch='#7c6c50', blotch_amt=0.5, lines='#5a4c36', line_count=3),
        'socket': Mat('#090706', grain=0.0, shade=False, rim=0.0),
        'glow': Mat('#fff4a8', grain=0.0, shade=False, highlight='#ffffff', rim=0.0),
        'cloth': Mat('#3e4b2e', blotch='#232c19', blotch_amt=0.6, lines='#1a2112', line_count=4, holes=0.08, bands='#34402a'),
        'leather': Mat('#4f3a26', blotch='#2e2014', blotch_amt=0.5, lines='#22180e', line_count=2),
        'claw': Mat('#161412', grain=0.06, highlight='#5a5550', rim=0.1),
        'tooth': Mat('#d8ceb0', grain=0.04, rim=0.08),
    }
    m = Model('forest_stalker', mats)
    m.bone('root', [0, 0, 0])
    m.bone('hips', [0, 11, 3], 'root')
    m.bone('torso', [0, 12, 3], 'hips', rot=(-38, 0, 0))
    m.bone('neck', [0, 21.5, 2], 'torso', rot=(20, 0, 0))
    m.bone('head', [0, 24, 1.8], 'neck', rot=(18, 0, 0))
    m.bone('jaw', [0, 24.2, -0.4], 'head')
    m.bone('cloak', [0, 21.6, 5], 'torso', rot=(8, 0, 0))
    m.bone_pair('arm', [4.6, 20.5, 2.5], 'torso', 'torso', rot=(-10, 0, 8))
    m.bone_pair('forearm', [5.2, 13.2, 2.5], 'arm_left', 'arm_right', rot=(28, 0, 0))
    m.bone_pair('hand', [5.2, 4.2, 2.5], 'forearm_left', 'forearm_right', rot=(8, 0, 0))
    m.bone_pair('leg', [2.6, 11, 3.5], 'hips', 'hips', rot=(-38, 0, 0))
    m.bone_pair('shin', [2.7, 5.6, 3.5], 'leg_left', 'leg_right', rot=(78, 0, 0))
    m.bone_pair('foot', [2.7, 0.6, 3.5], 'shin_left', 'shin_right', rot=(-40, 0, 0))

    m.cube('hips', [-3.4, 9.5, 1.2], [3.4, 12.5, 5.6], 'skin')
    m.cube('hips', [-3.6, 10.2, 1.0], [3.6, 11.4, 5.8], 'leather')
    m.cube('torso', [-3.8, 12, 0.8], [3.8, 21.5, 5.2], 'skin')
    for sd in (1, -1):
        m.cube('torso', [sd * 3.8, 13.5, 1.4], [sd * 4.2, 19.5, 4.6], 'ribs')
    m.cube('torso', [-3, 13.2, 0.5], [3, 19.5, 0.8], 'ribs')
    m.cube('torso', [-4.6, 19.5, 0.4], [4.6, 22.2, 5.6], 'skin_dark')
    for y in (13, 15, 17, 19, 21):
        m.cube('torso', [-0.6, y, 5.2], [0.6, y + 1.2, 6.2], 'bone')
    m.cube('torso', [-4.2, 15.6, 0.3], [4.2, 16.4, 5.7], 'leather', rot=[0, 0, 28])
    m.cube('neck', [-1.3, 20.8, 0.8], [1.3, 24.4, 3.2], 'skin')
    # The skull: a long deer's head of bare bone, black sockets with a light deep in them, antlers.
    m.cube('head', [-2.8, 23.4, -1.2], [2.8, 28.2, 4.2], 'bone')
    m.cube('head', [-1.9, 23.8, -6.2], [1.9, 26.6, -1.2], 'bone')
    m.cube('head', [-1.3, 24.0, -7.4], [1.3, 25.6, -6.2], 'bone')
    for sd in (1, -1):
        m.cube('head', [sd * 0.6, 25.8, -1.45], [sd * 2.4, 27.4, -1.15], 'socket')
        m.cube('head', [sd * 1.0, 26.2, -1.6], [sd * 1.9, 27.0, -1.4], 'glow')
        m.cube('head', [sd * 1.4, 27.6, 1.4], [sd * 2.4, 32.4, 2.4], 'antler', rot=[0, 0, -sd * 22], origin=[sd * 1.9, 27.6, 1.9])
        m.cube('head', [sd * 2.8, 30.4, 1.5], [sd * 3.6, 34.4, 2.3], 'antler', rot=[-20, 0, -sd * 40], origin=[sd * 3.2, 30.4, 1.9])
        m.cube('head', [sd * 1.6, 31.0, 1.4], [sd * 2.4, 33.6, 2.2], 'antler', rot=[18, 0, sd * 10], origin=[sd * 2.0, 31.0, 1.8])
        m.cube('head', [sd * 3.2, 29.0, 0.9], [sd * 3.9, 31.2, 1.6], 'antler', rot=[-40, 0, -sd * 70], origin=[sd * 3.5, 29.0, 1.2])
    m.cube('head', [-0.9, 25.0, -7.6], [0.9, 25.5, -7.3], 'socket')
    rr = random.Random(3)
    teeth_row(m, 'head', -1.6, 1.6, 24.0, 0.8, -6.0, 0.4, 0.5, True, rng=rr)
    m.cube('jaw', [-1.6, 22.9, -6.6], [1.6, 23.9, -0.6], 'bone')
    teeth_row(m, 'jaw', -1.4, 1.4, 23.9, 0.6, -6.5, 0.4, 0.55, False, rng=rr)
    # The hood, and a tattered hunter's cloak hanging from the shoulders.
    m.cube('head', [-3.4, 22.8, 1.6], [3.4, 28.8, 5.2], 'cloth')
    m.cube('head', [-3.6, 26.8, -0.4], [3.6, 29.2, 1.8], 'cloth')
    m.cube('cloak', [-4.8, 8.5, 4.9], [4.8, 22.2, 6.0], 'cloth')
    m.cube('cloak', [-4.4, 5.5, 5.2], [4.4, 8.5, 5.9], 'cloth')

    m.pair('arm_left', 'arm_right', [3.9, 12.6, 1.5], [5.6, 21.2, 3.5], 'skin')
    m.pair('arm_left', 'arm_right', [3.7, 18.6, 1.2], [5.9, 21.6, 3.8], 'cloth')
    m.pair('forearm_left', 'forearm_right', [4.3, 3.8, 1.7], [6.0, 13.6, 3.3], 'skin')
    m.pair('forearm_left', 'forearm_right', [4.1, 6.5, 1.5], [6.2, 8.0, 3.5], 'leather')
    m.pair('hand_left', 'hand_right', [3.9, 1.8, 1.2], [6.4, 4.4, 3.8], 'skin_dark')
    for k in range(3):
        m.pair('hand_left', 'hand_right', [4.0 + k * 0.8, -1.8, 1.4], [4.6 + k * 0.8, 1.9, 2.0], 'claw', rot=[-18, 0, 0], origin=[4.3 + k * 0.8, 1.8, 1.7])
    m.pair('hand_left', 'hand_right', [6.2, 1.0, 2.0], [6.8, 3.6, 2.6], 'claw')

    m.pair('leg_left', 'leg_right', [1.5, 5.4, 2.4], [3.9, 11.6, 4.8], 'skin')
    m.pair('leg_left', 'leg_right', [1.3, 8.4, 2.2], [4.1, 11.0, 5.0], 'leather')
    m.pair('shin_left', 'shin_right', [1.8, 0.4, 2.7], [3.6, 6.0, 4.3], 'skin')
    m.pair('foot_left', 'foot_right', [1.6, -0.6, 0.4], [3.8, 1.0, 4.4], 'skin_dark')
    for k in range(3):
        m.pair('foot_left', 'foot_right', [1.7 + k * 0.7, -0.6, -0.8], [2.2 + k * 0.7, 0.4, 0.4], 'claw')
    ground(m)
    m.write()

    def stance(b=0.0):
        return {'hips': RP([0, 0, 0], [0, b, 0]), 'torso': R(0), 'neck': R(0), 'head': R(0), 'jaw': R(4),
                'arm_left': R(0, 0, 0), 'arm_right': R(0, 0, 0), 'forearm_left': R(0), 'forearm_right': R(0),
                'leg_left': R(0), 'leg_right': R(0), 'shin_left': R(0), 'shin_right': R(0), 'foot_left': R(0), 'foot_right': R(0), 'cloak': R(0)}

    clips = {}
    c = ClipBuilder(2400, 'IDLE', loop=True, blendIn=200, blendOut=200)
    osc(c, lambda ph: merge(stance(0.3 * sin(ph)), {'torso': R(2 * sin(ph)), 'head': R(4 * sin(ph, 1, 0.3), 14 * sin(ph, 0.5) if False else 10 * sin(ph), 12 * sin(ph, 1, 0.25)),
                                                  'cloak': R(-3 - 3 * sin(ph, 1, 0.4), 0, 2 * sin(ph)), 'hand_left': R(10 * max(0, sin(ph, 2))),
                                                  'jaw': R(4 + 4 * max(0, sin(ph, 3, 0.2)))}), 2400, 12)
    clips['idle'] = c.done()

    c = ClipBuilder(900, 'MOVEMENT', loop=True, blendIn=150, blendOut=150)
    osc(c, lambda ph: merge(stance(0.6 * abs(sin(ph))), {
        'torso': R(8, 4 * sin(ph), 0), 'neck': R(-6), 'head': R(-8, -4 * sin(ph), 0),
        'arm_left': R(-30 * sin(ph) - 14), 'arm_right': R(30 * sin(ph) - 14), 'forearm_left': R(-10 + 14 * max(0, sin(ph))), 'forearm_right': R(-10 + 14 * max(0, -sin(ph))),
        'leg_left': R(26 * sin(ph)), 'leg_right': R(-26 * sin(ph)), 'shin_left': R(-18 * max(0, sin(ph, 1, 0.25))), 'shin_right': R(-18 * max(0, -sin(ph, 1, 0.25))),
        'cloak': R(-14 - 6 * abs(sin(ph, 2)), 0, 4 * sin(ph))}), 900, 12)
    clips['move'] = c.done()

    low = merge(stance(-3.2), {'torso': R(14), 'neck': R(-10), 'head': R(-14), 'jaw': R(18),
                               'arm_left': R(-38, 0, -20), 'arm_right': R(-38, 0, 20), 'forearm_left': R(-24), 'forearm_right': R(-24),
                               'hand_left': R(-30), 'hand_right': R(-30), 'leg_left': R(-26), 'leg_right': R(-26), 'shin_left': R(30), 'shin_right': R(30),
                               'foot_left': R(-6), 'foot_right': R(-6), 'cloak': R(-10)})
    c = ClipBuilder(1100, 'ATTACK', blendIn=80)
    c.at(0, stance())
    c.at(250, merge(stance(0.8), {'torso': R(-8), 'head': R(-6, 0, 18), 'jaw': R(8)}))
    c.at(800, low)
    for t, j in ((880, 1), (960, -1), (1040, 1), (1100, 0)):
        c.at(t, merge(low, {'hips': RP([0, 0, 1.5 * j], [0, -3.2, 0])}))
    clips['telegraph'] = c.done()

    c = ClipBuilder(700, 'ATTACK', blendIn=40)
    lunge = merge(stance(1.5), {'torso': R(24), 'neck': R(-16), 'head': R(-18), 'jaw': R(40),
                                'arm_left': R(-120, 0, -12), 'arm_right': R(-120, 0, 12), 'forearm_left': R(10), 'forearm_right': R(10),
                                'hand_left': R(-20), 'hand_right': R(-20), 'leg_left': R(44), 'leg_right': R(36), 'shin_left': R(-30), 'shin_right': R(-20),
                                'cloak': R(-50)})
    c.at(0, low)
    c.at(120, lunge)
    c.at(500, merge(lunge, {'hips': RP([0, 0, 0], [0, 0.4, 0]), 'cloak': R(-40)}))
    c.at(700, merge(lunge, {'torso': R(30), 'arm_left': R(-80, 0, -12), 'arm_right': R(-80, 0, 12), 'hips': RP([0, 0, 0], [0, -1, 0])}))
    clips['lunge'] = c.done()

    def crashed(ph):
        tw = sin(ph, 3)
        return merge(stance(-6.5), {'torso': R(48, 0, 6 * tw), 'neck': R(-20), 'head': R(-30, 30, 20), 'jaw': R(24 + 8 * tw),
                                    'arm_left': R(-60, 0, -60 - 8 * tw), 'arm_right': R(-70, 0, 56), 'forearm_left': R(-30), 'forearm_right': R(-40 + 10 * tw),
                                    'leg_left': R(40), 'leg_right': R(56), 'shin_left': R(-40), 'shin_right': R(-60), 'cloak': R(30, 0, 10)})
    c = ClipBuilder(2200, 'ATTACK', blendIn=60)
    for k in range(10):
        c.at(k * 200, crashed(k / 9))
    c.at(2200, merge(stance(-3), {'torso': R(20)}))
    clips['exposed'] = c.done()

    c = ClipBuilder(800, 'ATTACK', blendIn=60)
    c.at(0, stance())
    c.at(330, merge(stance(1.4), {'torso': R(-22, 18, 0), 'head': R(-10, -10, 0), 'jaw': R(30), 'arm_right': R(-170, 0, 30), 'forearm_right': R(-30),
                                  'arm_left': R(-30, 0, -20), 'cloak': R(10)}))
    c.at(450, merge(stance(-1), {'torso': R(26, -24, 0), 'head': R(-14, 10, 0), 'jaw': R(14), 'arm_right': R(-50, 0, -20), 'forearm_right': R(10),
                                 'arm_left': R(-100, 0, -10), 'forearm_left': R(-20), 'cloak': R(-20)}))
    c.at(560, merge(stance(-1), {'torso': R(22, 20, 0), 'arm_left': R(-40, 0, 24), 'forearm_left': R(10), 'arm_right': R(-40, 0, -10)}))
    c.at(800, stance())
    clips['rake'] = c.done()

    c = ClipBuilder(1000, 'ATTACK', blendIn=80)
    skulk = merge(stance(-1.8), {'torso': R(10), 'head': R(-10, 20, 0), 'cloak': R(-10)})
    for k in range(5):
        ph = k / 4
        c.at(k * 250, merge(skulk, {'leg_left': R(-20 * sin(ph, 1)), 'leg_right': R(20 * sin(ph, 1)), 'arm_left': R(16 * sin(ph, 1) - 10), 'arm_right': R(-16 * sin(ph, 1) - 10)}))
    clips['retreat'] = c.done()

    c = ClipBuilder(400, 'SPECIAL', blendIn=30)
    c.at(0, stance())
    c.at(90, merge(stance(0.6), {'torso': R(-18, 0, 10), 'head': R(-20, 0, -14), 'jaw': R(30), 'arm_left': R(-40, 0, -30), 'arm_right': R(-30, 0, 34)}))
    c.at(400, stance())
    clips['hurt'] = c.done()

    c = ClipBuilder(1700, 'SPECIAL', blendIn=30, blendOut=200)
    for t, s_ in ((0, 0), (120, 1), (400, -1), (700, 1), (1000, -0.6), (1300, 0.3), (1700, 0)):
        k = 1 - t / 1700
        c.at(t, merge(stance(-1.5 * k), {'torso': R(-26 * k, 0, 20 * s_ * k), 'head': R(-24 * k, 20 * s_ * k, -14 * s_ * k), 'jaw': R(40 * k),
                                       'arm_left': R(-80 * k, 0, -50 * k), 'arm_right': R(-60 * k, 0, 60 * k), 'cloak': R(20 * k * s_)}))
    clips['stagger'] = c.done()

    c = ClipBuilder(1200, 'RAGDOLL', hold=True, blendIn=40)
    c.at(0, stance())
    c.at(300, merge(stance(0.5), {'torso': R(-30, 0, 20), 'head': R(-30, 0, 20), 'jaw': R(40), 'arm_left': R(-120, 0, -40), 'arm_right': R(-100, 0, 40)}))
    dead = merge(stance(-8.5), {'torso': R(58, 0, 30), 'neck': R(-10), 'head': R(-20, 40, 30), 'jaw': R(44), 'arm_left': R(-40, 0, -80), 'arm_right': R(-80, 0, 70),
                                'leg_left': R(50), 'leg_right': R(70), 'shin_left': R(-60), 'shin_right': R(-70), 'cloak': R(40, 0, 20)})
    c.at(800, dead)
    c.at(1200, dead)
    clips['death'] = c.done()
    write_clips('forest_stalker', clips)


KINDS = {}


def main():
    want = sys.argv[1:] or list(KINDS)
    for k in want:
        KINDS[k]()


if __name__ == '__main__':
    KINDS.update({k: globals()[k] for k in ('school_maw', 'school_crawler', 'fly_head', 'forest_stalker') if k in globals()})
    main()
