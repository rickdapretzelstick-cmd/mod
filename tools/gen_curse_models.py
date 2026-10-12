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

def write_clips(kind, clips, reference='tools/gen_curse_models.py'):
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
        d = {'name': kind + '_' + name, 'rig': kind, 'reference': reference, 'duration': c['duration'],
             'priority': c.get('priority', 'ATTACK'), 'blendIn': c.get('blendIn', 80), 'blendOut': c.get('blendOut', 120), 'keys': keys}
        if c.get('loop'):
            d['loop'] = True
        if c.get('hold'):
            d['hold'] = True
        if c.get('tremble'):
            d['tremble'] = c['tremble']
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


def grapple_set(base, grip, shove, press, wind, throw):
    """
    A strength contest's clips (progression.contest.StrengthContests), all from one grip pose (merged over `base`):
    grapple (the grab closing into the grip), grapple_shove (a good tap pushes it back), grapple_press (a miss: it bears
    down) and grapple_fling (it won: a heave from `wind` and a throw at `throw`). All but the fling end in the grip and
    hold it, trembling with the strain, until the next one replaces it.
    """
    g = merge(base, grip)
    out = {}
    c = ClipBuilder(380, 'SPECIAL', hold=True, blendIn=60, blendOut=160)
    c.at(0, base)
    c.at(150, merge(g, press))
    c.at(380, g)
    out['grapple'] = c.done()
    c = ClipBuilder(520, 'SPECIAL', hold=True, blendIn=30, blendOut=160)
    c.at(0, g)
    c.at(90, merge(g, shove))
    c.at(200, merge(g, shove))
    c.at(520, g)
    out['grapple_shove'] = c.done()
    c = ClipBuilder(600, 'SPECIAL', hold=True, blendIn=40, blendOut=160)
    c.at(0, g)
    c.at(180, merge(g, press))
    c.at(300, merge(g, press))
    c.at(600, g)
    out['grapple_press'] = c.done()
    c = ClipBuilder(820, 'SPECIAL', blendIn=40, blendOut=220)
    c.at(0, g)
    c.at(280, merge(g, wind))
    c.at(400, merge(g, throw))
    c.at(560, merge(g, throw))
    c.at(820, base)
    out['grapple_fling'] = c.done()
    for k in ('grapple', 'grapple_shove', 'grapple_press'):
        out[k]['tremble'] = 1.6
    return out


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
    # Strength contests: both claws clamp on and the jaws work at your shoulder.
    clips.update(grapple_set(
        stance(),
        {'body': RP([12, 0, 0], [0, -0.6, -1.5]), 'head': R(-12), 'jaw': R(34), 'arm_left': R(-64, 22, -12), 'arm_right': R(-64, -22, 12),
         'forearm_left': R(-26), 'forearm_right': R(-26), 'leg_left': R(-18), 'leg_right': R(8), 'foot_left': R(14), 'foot_right': R(-2)},
        {'body': RP([-8, 0, 5], [0, 0.4, 2.2]), 'head': R(-26, 0, 6), 'jaw': R(50), 'arm_left': R(-46, 12, -20), 'arm_right': R(-50, -12, 18)},
        {'body': RP([22, 0, -3], [0, -1.4, -3.2]), 'head': R(6), 'jaw': R(56), 'arm_left': R(-84, 26, -6), 'arm_right': R(-84, -26, 6),
         'forearm_left': R(-42), 'forearm_right': R(-42)},
        {'body': RP([-24, 0, -10], [0, 2, 1.5]), 'head': R(-20), 'jaw': R(22), 'arm_left': R(-150, 0, -20), 'arm_right': R(-150, 0, 20),
         'forearm_left': R(-30), 'forearm_right': R(-30)},
        {'body': RP([30, 0, 14], [0, -2.4, -3]), 'head': R(10), 'jaw': R(46), 'arm_left': R(-40, 0, -34), 'arm_right': R(-30, 0, 30),
         'forearm_left': R(-6), 'forearm_right': R(-6)}))
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
    # Strength contests: reared up on its back legs, the front pair locked round you, the middle pair braced.
    clips.update(grapple_set(
        merge({'body': RP([0, 0, 0], [0, 0, 0]), 'head': R(0), 'jaw': R(5), 'tail': R(0), 'tail2': R(0)}, rest_legs),
        merge({'body': RP([-14, 0, 0], [0, 2.2, -1.5]), 'head': R(-10), 'jaw': R(48), 'tail': R(-12), 'tail2': R(-8)},
              legs(lambda n, sd, sg: ((limb(sg, 64, 44), {'rot': [-36, 0, -sg * 34]}) if n == 1 else
                                      (limb(sg, 24, 16), {'rot': [0, 0, -sg * 8]}) if n == 2 else
                                      (limb(sg, -54, 8), {'rot': [30, 0, sg * 10]})))),
        merge({'body': RP([-26, 0, 6], [0, 2.8, 1.2]), 'head': R(-24, 0, 8), 'jaw': R(60)},
              {'arm1_left': limb(1, 48, 56), 'arm1_right': limb(-1, 52, 56)}),
        merge({'body': RP([4, 0, 0], [0, 1.2, -3]), 'head': R(4), 'jaw': R(62)},
              {'arm1_left': limb(1, 78, 30), 'arm1_right': limb(-1, 78, 30),
               'forearm1_left': {'rot': [-48, 0, -40]}, 'forearm1_right': {'rot': [-48, 0, 40]}}),
        merge({'body': RP([-30, 0, -18], [0, 3.4, 0.5]), 'head': R(-20), 'jaw': R(30)},
              {'arm1_left': limb(1, 90, 70), 'arm1_right': limb(-1, 90, 70)}),
        merge({'body': RP([12, 0, 26], [0, 0, -2.5]), 'head': R(10, 20, 0), 'jaw': R(50)},
              {'arm1_left': limb(1, 20, 30), 'arm1_right': limb(-1, 30, 20)})))
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
    # Strength contests: hunched over you, both claws on your shoulders, its face in yours.
    clips.update(grapple_set(
        stance(),
        {'hips': RP([0, 0, 0], [0, -1.2, 0]), 'torso': R(22), 'neck': R(-12), 'head': R(-10), 'jaw': R(30),
         'arm_left': R(-92, 10, -14), 'arm_right': R(-92, -10, 14), 'forearm_left': R(-30), 'forearm_right': R(-30),
         'hand_left': R(-24), 'hand_right': R(-24), 'leg_left': R(-24), 'leg_right': R(18), 'shin_left': R(24), 'shin_right': R(6), 'cloak': R(-16)},
        {'hips': RP([0, 0, 0], [0, -0.6, 0]), 'torso': R(4, 0, 6), 'neck': R(-24), 'head': R(-22, 0, 8), 'jaw': R(44),
         'arm_left': R(-74, 16, -22), 'arm_right': R(-78, -14, 20), 'cloak': R(-6)},
        {'hips': RP([0, 0, 0], [0, -2, 0]), 'torso': R(36), 'neck': R(-4), 'head': R(-2), 'jaw': R(48),
         'arm_left': R(-104, 6, -8), 'arm_right': R(-104, -6, 8), 'forearm_left': R(-44), 'forearm_right': R(-44), 'cloak': R(-30)},
        {'hips': RP([0, 0, 0], [0, 0.6, 0]), 'torso': R(-16, -24, 0), 'neck': R(-8), 'head': R(-14), 'jaw': R(20),
         'arm_left': R(-160, 0, -16), 'arm_right': R(-160, 0, 16), 'forearm_left': R(-20), 'forearm_right': R(-20), 'cloak': R(-4)},
        {'hips': RP([0, 0, 0], [0, -1.6, 0]), 'torso': R(34, 30, 0), 'neck': R(-6), 'head': R(-6), 'jaw': R(46),
         'arm_left': R(-60, 0, -30), 'arm_right': R(-30, 0, 40), 'forearm_left': R(-8), 'forearm_right': R(-8), 'cloak': R(-44)}))
    write_clips('forest_stalker', clips)



def sway_keys(c, duration, fn, steps):
    return osc(c, fn, duration, steps)


def hurt_clip(base, recoil):
    c = ClipBuilder(400, 'SPECIAL', blendIn=30)
    c.at(0, base)
    c.at(90, merge(base, recoil))
    c.at(400, base)
    return c.done()


def stagger_clip(base, fn):
    c = ClipBuilder(1700, 'SPECIAL', blendIn=30, blendOut=200)
    for t, s_ in ((0, 0), (120, 1), (400, -1), (700, 1), (1000, -0.6), (1300, 0.3), (1700, 0)):
        c.at(t, merge(base, fn(1 - t / 1700, s_)))
    return c.done()


def death_clip(base, mid, end):
    c = ClipBuilder(1200, 'RAGDOLL', hold=True, blendIn=40)
    c.at(0, base)
    c.at(300, merge(base, mid))
    c.at(800, merge(base, end))
    c.at(1200, merge(base, end))
    return c.done()


# ----------------------------------------------------------------------------------------------------------- Grave Hound

def grave_hound():
    mats = {
        'skin': Mat('#8c2f29', blotch='#55140f', blotch_amt=0.55, blotch_scale=3, lines='#e8c8a8', line_count=5),
        'skin_dark': Mat('#5c1c18', blotch='#3a0e0b', blotch_amt=0.5, lines='#c8a888', line_count=3),
        'ribs': Mat('#dccbaa', bands='#7a2620', grain=0.04),
        'bone': Mat('#ddd0b2', blotch='#a49272', blotch_amt=0.4, lines='#6e6146', line_count=3),
        'socket': Mat('#070505', grain=0.0, shade=False, rim=0.0),
        'glow': Mat('#ff3a20', grain=0.0, shade=False, rim=0.0, highlight='#ffd0a0'),
        'tooth': Mat('#efe6c8', grain=0.04, rim=0.08),
        'claw': Mat('#1e1816', grain=0.06, highlight='#5a4a44', rim=0.1),
        'flesh': Mat('#4a0a10', blotch='#200306', blotch_amt=0.5, rim=0.05),
    }
    m = Model('grave_hound', mats)
    m.bone('root', [0, 0, 0])
    m.bone('body', [0, 9, 2], 'root')
    m.bone('neck', [0, 10.5, -3.5], 'body')
    m.bone('head', [0, 11, -6.8], 'neck')
    m.bone('jaw', [0, 9.8, -8.5], 'head')
    m.bone('tail', [0, 10.2, 8], 'body')
    m.bone('tail2', [0, 10.2, 12], 'tail')
    for n, z in (('front', -2.6), ('back', 6.2)):
        m.bone_pair('leg_' + n, [2.6, 8.2, z], 'body', 'body')
        m.bone_pair('shin_' + n, [2.6, 4.6, z], 'leg_%s_left' % n, 'leg_%s_right' % n)

    m.cube('body', [-3, 6.5, -4], [3, 11.6, 3], 'skin')
    for sd in (1, -1):
        m.cube('body', [sd * 3, 7.2, -3.4], [sd * 3.35, 11.2, 2.4], 'ribs')
    m.cube('body', [-2.4, 7.2, 3], [2.4, 10.9, 8.2], 'skin')
    for z in (-3.4, -1.4, 0.6, 2.6, 4.6, 6.6):
        m.cube('body', [-0.5, 11.4, z], [0.5, 12.5, z + 1], 'bone')
    m.cube('neck', [-1.8, 8.8, -7.2], [1.8, 12.2, -3.2], 'skin')
    m.cube('head', [-2.4, 9.8, -11], [2.4, 13.6, -6.4], 'bone')
    m.cube('head', [-1.6, 9.8, -15.2], [1.6, 12.2, -11], 'skin')
    m.cube('head', [-1.0, 11.4, -15.4], [1.0, 12.0, -15.2], 'socket')
    for sd in (1, -1):
        m.cube('head', [sd * 1.1, 11.6, -11.15], [sd * 2.2, 12.6, -10.95], 'socket')
        m.cube('head', [sd * 1.5, 11.9, -11.25], [sd * 1.9, 12.3, -11.1], 'glow')
        m.cube('head', [sd * 1.6, 13.4, -8.4], [sd * 2.4, 15.2, -7.4], 'bone', rot=[-25, 0, -sd * 15], origin=[sd * 2, 13.4, -7.9])
    rr = random.Random(21)
    teeth_row(m, 'head', -1.5, 1.5, 9.9, 1.3, -15.3, 0.5, 0.6, True, rng=rr)
    for sd in (1, -1):
        for z in (-14, -12.6):
            m.cube('head', [sd * 1.5 - 0.25, 8.9, z - 0.25], [sd * 1.5 + 0.25, 9.9, z + 0.25], 'tooth')
    m.cube('jaw', [-1.5, 8.3, -15], [1.5, 9.8, -8.4], 'skin_dark')
    teeth_row(m, 'jaw', -1.3, 1.3, 9.8, 1.0, -14.9, 0.5, 0.65, False, rng=rr)
    m.cube('tail', [-0.6, 9.6, 8], [0.6, 10.8, 12.2], 'skin')
    m.cube('tail2', [-0.4, 9.8, 12], [0.4, 10.6, 16.2], 'bone')
    for n, z in (('front', -2.6), ('back', 6.2)):
        L, S_ = 'leg_%s_left' % n, 'shin_%s_left' % n
        m.pair(L, 'leg_%s_right' % n, [1.6, 4.4, z - 1.1], [3.6, 8.8, z + 1.3], 'skin')
        m.pair(S_, 'shin_%s_right' % n, [1.85, 0.8, z - 0.8], [3.35, 4.8, z + 0.8], 'skin_dark')
        m.pair(S_, 'shin_%s_right' % n, [1.6, 0, z - 1.8], [3.6, 0.9, z + 0.9], 'skin_dark')
        for k in range(2):
            m.pair(S_, 'shin_%s_right' % n, [1.8 + k * 1.0, 0, z - 2.5], [2.4 + k * 1.0, 0.6, z - 1.8], 'claw')
    ground(m)
    m.write()

    legs4 = ('leg_front_left', 'leg_front_right', 'leg_back_left', 'leg_back_right')
    base = {b: R(0) for b in legs4}
    base.update({'body': RP([0, 0, 0], [0, 0, 0]), 'neck': R(0), 'head': R(0), 'jaw': R(4), 'tail': R(-10), 'tail2': R(0)})
    clips = {}
    c = ClipBuilder(1600, 'IDLE', loop=True, blendIn=200, blendOut=200)
    osc(c, lambda ph: merge(base, {'body': RP([0, 0, 0], [0, 0.3 * sin(ph, 2), 0]), 'neck': R(4 * sin(ph), 10 * sin(ph, 1, 0.3), 0),
                                   'head': R(6 * max(0, sin(ph, 3)), 0, 8 * sin(ph, 1, 0.5)), 'jaw': R(6 + 6 * max(0, sin(ph, 2))),
                                   'tail': R(-10, 18 * sin(ph, 2)), 'tail2': R(0, 20 * sin(ph, 2, 0.2))}), 1600, 16)
    clips['idle'] = c.done()
    c = ClipBuilder(500, 'MOVEMENT', loop=True, blendIn=100, blendOut=150)
    osc(c, lambda ph: merge(base, {'body': RP([6 * sin(ph), 0, 0], [0, 0.9 * abs(sin(ph)), 0]),
                                   'leg_front_left': R(-38 * sin(ph)), 'leg_front_right': R(-38 * sin(ph, 1, 0.08)),
                                   'leg_back_left': R(38 * sin(ph)), 'leg_back_right': R(38 * sin(ph, 1, 0.08)),
                                   'shin_front_left': R(20 * max(0, sin(ph, 1, 0.25))), 'shin_front_right': R(20 * max(0, sin(ph, 1, 0.33))),
                                   'shin_back_left': R(-20 * max(0, -sin(ph, 1, 0.25))), 'shin_back_right': R(-20 * max(0, -sin(ph, 1, 0.33))),
                                   'neck': R(-6 * sin(ph)), 'jaw': R(18), 'tail': R(-25, 10 * sin(ph))}), 500, 10)
    clips['move'] = c.done()
    crouch = merge(base, {'body': RP([4, 0, 0], [0, -2.6, 0.5]), 'leg_front_left': R(-30), 'leg_front_right': R(-30),
                          'shin_front_left': R(60), 'shin_front_right': R(60), 'leg_back_left': R(-40), 'leg_back_right': R(-40),
                          'shin_back_left': R(70), 'shin_back_right': R(70), 'neck': R(14), 'head': R(-8), 'jaw': R(30), 'tail': R(10)})
    c = ClipBuilder(500, 'ATTACK', blendIn=60)
    c.at(0, base)
    c.at(300, crouch)
    for t, j in ((380, 1), (440, -1), (500, 1)):
        c.at(t, merge(crouch, {'body': RP([4, 0, 1.5 * j], [0, -2.8, 0.5])}))
    clips['crouch'] = c.done()
    leap = merge(base, {'body': RP([-12, 0, 0], [0, 1.5, -1]), 'leg_front_left': R(-70), 'leg_front_right': R(-70),
                        'leg_back_left': R(70), 'leg_back_right': R(70), 'neck': R(-10), 'head': R(-14), 'jaw': R(55), 'tail': R(-30)})
    c = ClipBuilder(600, 'ATTACK', blendIn=30)
    c.at(0, crouch)
    c.at(100, leap)
    c.at(450, merge(leap, {'body': RP([8, 0, 0], [0, 0.5, -1])}))
    c.at(600, merge(base, {'jaw': R(20)}))
    clips['attack'] = c.done()

    def flop(ph):
        return merge(base, {'body': RP([0, 0, 72], [0, -3.4, 0]), 'neck': R(10, 0, -20), 'jaw': R(30 + 8 * sin(ph, 3)),
                            'leg_front_left': R(-30 * sin(ph, 4)), 'leg_front_right': R(30 * sin(ph, 4, 0.3)),
                            'leg_back_left': R(30 * sin(ph, 4, 0.5)), 'leg_back_right': R(-30 * sin(ph, 4, 0.7)), 'tail': R(10)})
    c = ClipBuilder(1300, 'ATTACK', blendIn=60)
    for k in range(8):
        c.at(k * 150, flop(k / 7))
    c.at(1300, merge(base, {'body': RP([0, 0, 20], [0, -1.5, 0])}))
    clips['stumble'] = c.done()
    c = ClipBuilder(600, 'ATTACK', blendIn=40)
    c.at(0, base)
    c.at(200, merge(base, {'body': RP([-6, 0, 0], [0, 0.4, 1]), 'neck': R(-16), 'head': R(-10), 'jaw': R(55)}))
    c.at(300, merge(base, {'body': RP([10, 0, 0], [0, -0.6, -2]), 'neck': R(22), 'head': R(8), 'jaw': R(0)}))
    c.at(600, base)
    clips['bite'] = c.done()
    sit = merge(base, {'body': RP([-24, 0, 0], [0, -1, 1.5]), 'leg_back_left': R(-60), 'leg_back_right': R(-60),
                       'shin_back_left': R(80), 'shin_back_right': R(80), 'leg_front_left': R(20), 'leg_front_right': R(20)})
    c = ClipBuilder(1200, 'ATTACK', blendIn=80)
    c.at(0, base)
    c.at(400, merge(sit, {'neck': R(-40), 'head': R(-20), 'jaw': R(10)}))
    for k, t in enumerate(range(600, 1201, 100)):
        c.at(t, merge(sit, {'neck': R(-58 + 3 * (k % 2)), 'head': R(-24), 'jaw': R(44 + 4 * (k % 2)), 'tail': R(-20, 10 * (1 if k % 2 else -1))}))
    clips['howl'] = c.done()
    clips['hurt'] = hurt_clip(base, {'body': RP([-10, 0, 12], [0, 0.6, 1.5]), 'neck': R(-20, 0, -10), 'jaw': R(40)})
    clips['stagger'] = stagger_clip(base, lambda k, s_: {'body': RP([-16 * k, 0, 30 * s_ * k], [0, 1 * k, 2 * k]), 'neck': R(-24 * k, 20 * s_ * k, 0),
                                                         'jaw': R(40 * k), 'leg_front_left': R(-40 * k * s_), 'leg_back_right': R(40 * k * s_)})
    clips['death'] = death_clip(base, {'body': RP([-20, 0, 40], [0, 1, 0]), 'jaw': R(50)},
                                {'body': RP([0, 0, 88], [0, -3.6, 0]), 'neck': R(20, 0, -10), 'jaw': R(46), 'leg_front_left': R(-30),
                                 'leg_front_right': R(-40), 'leg_back_left': R(30), 'leg_back_right': R(40), 'tail': R(20)})
    # Strength contests: jaws locked on your arm, hindquarters down, tugging.
    clips.update(grapple_set(
        base,
        {'body': RP([-10, 0, 0], [0, -1.2, 1.2]), 'neck': R(-18), 'head': R(-6), 'jaw': R(14), 'tail': R(-30),
         'leg_front_left': R(-34), 'leg_front_right': R(-26), 'shin_front_left': R(30), 'shin_front_right': R(24),
         'leg_back_left': R(-30), 'leg_back_right': R(-36), 'shin_back_left': R(56), 'shin_back_right': R(62)},
        {'body': RP([-18, 0, 8], [0, -0.8, 2.4]), 'neck': R(-34, 14, 0), 'head': R(-12, 0, 10), 'jaw': R(22), 'tail': R(-10)},
        {'body': RP([6, 0, 0], [0, -1.6, -1.6]), 'neck': R(10), 'head': R(8), 'jaw': R(6), 'tail': R(-44),
         'leg_front_left': R(-56), 'leg_front_right': R(-50), 'leg_back_left': R(20), 'leg_back_right': R(14)},
        {'body': RP([-20, -30, 0], [0, -0.4, 1.6]), 'neck': R(-30, 30, 0), 'head': R(-10, 20, 0), 'jaw': R(10), 'tail': R(-20, 30)},
        {'body': RP([8, 34, 10], [0, -1.4, -1]), 'neck': R(16, -40, 0), 'head': R(6, -24, 0), 'jaw': R(50), 'tail': R(-10, -30)}))
    write_clips('grave_hound', clips)


# ----------------------------------------------------------------------------------------------------------- Veiled Bride

def veiled_bride():
    mats = {
        'dress': Mat('#d6d0c2', blotch='#857a66', blotch_amt=0.5, blotch_scale=4, lines='#5e5444', line_count=4, holes=0.03),
        'veil': Mat('#ebe7df', grain=0.04, rim=0.0, holes=0.28, lines='#b8b0a0', line_count=3),
        'skin': Mat('#a3a9b2', blotch='#6e7682', blotch_amt=0.45, lines='#4a5466', line_count=4),
        'socket': Mat('#060507', grain=0.0, shade=False, rim=0.0),
        'glow': Mat('#e8f0ff', grain=0.0, shade=False, rim=0.0),
        'flesh': Mat('#3a0a0e', grain=0.05, rim=0.0),
        'tooth': Mat('#e6decc', grain=0.04, rim=0.08),
        'claw': Mat('#34323a', grain=0.05, highlight='#7a7880', rim=0.1),
        'flower': Mat('#5a1a24', blotch='#2a0a10', blotch_amt=0.5, spots='#8a3040', spot_count=6),
    }
    m = Model('veiled_bride', mats)
    m.bone('root', [0, 0, 0])
    m.bone('body', [0, 16, 0], 'root')
    m.bone('skirt', [0, 16, 0], 'body')
    m.bone('torso', [0, 16, 0], 'body')
    m.bone('head', [0, 30.5, 0], 'torso')
    m.bone('veil', [0, 36.5, 0], 'head')
    m.bone_pair('arm', [4, 29.5, 0], 'torso', 'torso', rot=(0, 0, 4))
    m.bone_pair('forearm', [4.6, 20.5, 0], 'arm_left', 'arm_right')
    m.bone_pair('hand', [4.6, 11, 0], 'forearm_left', 'forearm_right')
    m.cube('skirt', [-5.6, 0, -4.6], [5.6, 6, 4.6], 'dress')
    m.cube('skirt', [-4.8, 6, -4], [4.8, 11, 4], 'dress')
    m.cube('skirt', [-4, 11, -3.4], [4, 16.2, 3.4], 'dress')
    m.cube('skirt', [-5.9, 0, 2.5], [5.9, 2, 7], 'dress')
    m.cube('torso', [-2.8, 16, -1.8], [2.8, 24, 1.8], 'dress')
    m.cube('torso', [-3.4, 24, -2.2], [3.4, 29, 2.2], 'dress')
    m.cube('torso', [-1, 29, -0.8], [1, 31, 0.8], 'skin')
    m.cube('head', [-2.3, 30.8, -2.3], [2.3, 35.6, 2.3], 'skin')
    for sd in (1, -1):
        m.cube('head', [sd * 0.4, 33.4, -2.45], [sd * 1.5, 34.5, -2.3], 'socket')
        m.cube('head', [sd * 0.75, 33.75, -2.5], [sd * 1.1, 34.1, -2.42], 'glow')
    m.cube('head', [-1.9, 31.8, -2.42], [1.9, 32.7, -2.3], 'flesh')
    rr = random.Random(31)
    teeth_row(m, 'head', -1.8, 1.8, 32.7, 0.5, -2.48, 0.2, 0.45, True, rng=rr)
    teeth_row(m, 'head', -1.6, 1.6, 31.8, 0.4, -2.48, 0.2, 0.5, False, rng=rr)
    for x, z in ((-1.8, -1), (0, -1.8), (1.8, -0.6), (1.2, 1.4), (-1.2, 1.5)):
        m.cube('head', [x - 0.5, 35.5, z - 0.5], [x + 0.5, 36.4, z + 0.5], 'flower')
    m.cube('veil', [-2.9, 29.6, -2.8], [2.9, 36.8, 2.8], 'veil')
    m.cube('veil', [-2.6, 36.8, -2.5], [2.6, 37.3, 2.5], 'veil')
    m.cube('veil', [-2.7, 16, 2.6], [2.7, 30, 3.3], 'veil')
    m.pair('arm_left', 'arm_right', [3.2, 20, -0.8], [4.8, 30, 0.8], 'skin')
    m.pair('arm_left', 'arm_right', [3.0, 24, -1.1], [5.0, 30.2, 1.1], 'dress')
    m.pair('forearm_left', 'forearm_right', [3.85, 11, -0.65], [5.35, 20.6, 0.65], 'skin')
    m.pair('hand_left', 'hand_right', [3.6, 8, -1], [5.6, 11.2, 1], 'skin')
    for k in range(4):
        m.pair('hand_left', 'hand_right', [3.65 + k * 0.5, 3.4, -0.3 - (k % 2) * 0.3], [4.05 + k * 0.5, 8.1, 0.2 - (k % 2) * 0.3], 'claw')
    m.write()

    base = {'body': RP([0, 0, 0], [0, 0, 0]), 'skirt': R(0), 'torso': R(0), 'head': R(0, 0, 8), 'veil': R(0),
            'arm_left': R(0), 'arm_right': R(0), 'forearm_left': R(0), 'forearm_right': R(0), 'hand_left': R(0), 'hand_right': R(0)}
    clips = {}
    c = ClipBuilder(3000, 'IDLE', loop=True, blendIn=250, blendOut=250)
    osc(c, lambda ph: merge(base, {'body': RP([0, 0, 2 * sin(ph)], [0, 0.4 * sin(ph, 2), 0]), 'head': R(4 * sin(ph, 1, 0.3), 6 * sin(ph), 10 + 10 * sin(ph, 1, 0.2)),
                                   'veil': R(3 * sin(ph, 2), 0, 3 * sin(ph, 1, 0.4)), 'skirt': R(0, 0, -2 * sin(ph)),
                                   'hand_left': R(10 * max(0, sin(ph, 4))), 'hand_right': R(10 * max(0, sin(ph, 3, 0.3))),
                                   'arm_left': R(2 * sin(ph)), 'arm_right': R(-2 * sin(ph))}), 3000, 12)
    clips['idle'] = c.done()
    c = ClipBuilder(1500, 'MOVEMENT', loop=True, blendIn=200, blendOut=200)
    osc(c, lambda ph: merge(base, {'body': RP([6, 0, 3 * sin(ph)], [0, 0.6 * sin(ph, 2), 0]), 'skirt': R(-4, 0, -4 * sin(ph)),
                                   'head': R(-6, 0, 14), 'veil': R(-8 - 3 * sin(ph, 2)), 'arm_left': R(14 + 6 * sin(ph)), 'arm_right': R(14 - 6 * sin(ph)),
                                   'forearm_left': R(8), 'forearm_right': R(8)}), 1500, 12)
    clips['move'] = c.done()
    c = ClipBuilder(800, 'ATTACK', blendIn=60)
    c.at(0, base)
    for k, t in enumerate(range(100, 801, 50)):
        j = 1 if k % 2 else -1
        u = min(1, t / 600)
        c.at(t, merge(base, {'body': RP([0, 2 * j * u, 2 * j * u], [0, 0.8 * u, 0]), 'veil': {'rot': [-22 * u, 0, 4 * j], 'pos': [0, 1.5 * u, 0]},
                             'arm_left': R(-10 * u, 0, 45 * u), 'arm_right': R(-10 * u, 0, -45 * u), 'head': R(-10 * u, 0, 30 * u * j)}))
    clips['flicker'] = c.done()
    c = ClipBuilder(700, 'ATTACK', blendIn=40)
    c.at(0, base)
    reach = merge(base, {'torso': R(14), 'head': R(10, 0, 0), 'arm_left': R(-110, 0, 30), 'arm_right': R(-110, 0, -30),
                         'forearm_left': R(-10), 'forearm_right': R(-10), 'hand_left': R(-20), 'hand_right': R(-20)})
    c.at(330, reach)
    c.at(400, merge(reach, {'torso': R(24), 'arm_left': R(-80, 20, -12), 'arm_right': R(-80, -20, 12)}))
    c.at(700, base)
    clips['grasp'] = c.done()
    c = ClipBuilder(900, 'ATTACK', blendIn=60)
    c.at(0, base)
    back = merge(base, {'torso': R(-14), 'head': R(-40, 0, 0), 'veil': R(-20), 'arm_left': R(20, 0, 70), 'arm_right': R(20, 0, -70)})
    c.at(600, back)
    c.at(700, merge(base, {'torso': R(18), 'head': R(24, 0, 0), 'veil': R(-35), 'arm_left': R(-30, 0, 80), 'arm_right': R(-30, 0, -80)}))
    c.at(900, merge(base, {'torso': R(16), 'head': R(20, 0, 0), 'arm_left': R(-20, 0, 60), 'arm_right': R(-20, 0, -60)}))
    clips['wail'] = c.done()

    def hunch(ph):
        return merge(base, {'body': RP([0, 0, 0], [0, -1 + 0.5 * sin(ph, 2), 0]), 'torso': R(38 + 3 * sin(ph, 2)), 'head': R(34, 0, 20),
                            'veil': R(10), 'arm_left': R(30, 0, 4), 'arm_right': R(28, 0, -4), 'forearm_left': R(10), 'forearm_right': R(12)})
    c = ClipBuilder(1500, 'ATTACK', blendIn=80)
    for k in range(7):
        c.at(k * 250, hunch(k / 6))
    clips['breath'] = c.done()
    clips['hurt'] = hurt_clip(base, {'torso': R(-14, 0, 8), 'head': R(-20, 0, -14), 'arm_left': R(-30, 0, 30), 'arm_right': R(-20, 0, -26)})
    clips['stagger'] = stagger_clip(base, lambda k, s_: {'torso': R(-20 * k, 0, 18 * s_ * k), 'head': R(-24 * k, 0, -20 * s_ * k),
                                                         'arm_left': R(-60 * k, 0, 50 * k), 'arm_right': R(-50 * k, 0, -55 * k), 'veil': R(-20 * k)})
    clips['death'] = death_clip(base, {'torso': R(-20, 0, 10), 'head': R(-30), 'arm_left': R(-100, 0, 40), 'arm_right': R(-90, 0, -40)},
                                {'body': RP([0, 0, 82], [0, -7, 0]), 'torso': R(10, 0, 0), 'head': R(20, 0, 30), 'arm_left': R(-30, 0, 70),
                                 'arm_right': R(-60, 0, -40), 'veil': R(20)})
    # Strength contests: she looms over you, both long hands at your throat, the veil hanging into your face.
    clips.update(grapple_set(
        base,
        {'torso': R(22), 'head': R(18, 0, 6), 'veil': R(8), 'arm_left': R(-96, 14, -6), 'arm_right': R(-96, -14, 6),
         'forearm_left': R(-22), 'forearm_right': R(-22), 'hand_left': R(-30), 'hand_right': R(-30)},
        {'body': RP([0, 0, 0], [0, 0.6, 1.4]), 'torso': R(4, 0, -6), 'head': R(-12, 0, -10), 'veil': R(-18),
         'arm_left': R(-82, 0, 26), 'arm_right': R(-86, 0, -22), 'forearm_left': R(-8), 'forearm_right': R(-8)},
        {'body': RP([0, 0, 0], [0, 0.8, -1.2]), 'torso': R(34), 'head': R(30, 0, 12), 'veil': R(16),
         'arm_left': R(-112, 0, 8), 'arm_right': R(-112, 0, -8), 'forearm_left': R(-36), 'forearm_right': R(-36), 'hand_left': R(-44), 'hand_right': R(-44)},
        {'body': RP([0, 0, 0], [0, 1.6, 0.6]), 'torso': R(-12, 20, 0), 'head': R(-24), 'veil': R(-24),
         'arm_left': R(-170, 0, 10), 'arm_right': R(-170, 0, -10), 'forearm_left': R(-10), 'forearm_right': R(-10)},
        {'body': RP([0, 0, 0], [0, -0.4, -1]), 'torso': R(30, -26, 0), 'head': R(24), 'veil': R(-34),
         'arm_left': R(-40, 0, 60), 'arm_right': R(-50, 0, -30), 'forearm_left': R(0), 'forearm_right': R(0)}))
    write_clips('veiled_bride', clips)


# ----------------------------------------------------------------------------------------------------------- Bloat Toad

def bloat_toad():
    mats = {
        'skin': Mat('#6a7a3a', blotch='#3a4a1e', blotch_amt=0.6, blotch_scale=3, spots='#a6a062', spot_count=6, lines='#2e3a14', line_count=2),
        'skin_dark': Mat('#4a5426', blotch='#2e3614', blotch_amt=0.5),
        'belly': Mat('#cbbb7c', blotch='#9a8a50', blotch_amt=0.4, bands='#a89860'),
        'wart': Mat('#8f8f4c', blotch='#5a5a2a', blotch_amt=0.5, rim=0.1),
        'hand': Mat('#c4ac94', blotch='#8e7660', blotch_amt=0.4, lines='#6a5040', line_count=2),
        'flesh': Mat('#5a1a20', blotch='#2a080c', blotch_amt=0.5, rim=0.05),
        'tooth': Mat('#ece4ca', grain=0.04, rim=0.08),
        'sclera': Mat('#e8dcb0', lines='#a04030', line_count=3, rim=0.1),
        'iris': Mat('#e88a12', blotch='#8a4a04', blotch_amt=0.4, shade=False, rim=0.05),
        'pupil': Mat('#060404', grain=0.0, shade=False, highlight='#ffffff'),
        'claw': Mat('#2a2622', grain=0.05, rim=0.1),
    }
    m = Model('bloat_toad', mats)
    m.bone('root', [0, 0, 0])
    m.bone('body', [0, 8, 1], 'root')
    m.bone('head', [0, 10, -5.5], 'body')
    m.bone('jaw', [0, 7, -5], 'body')
    m.bone('throat', [0, 5.5, -6.5], 'body')
    m.bone_pair('arm', [6.4, 7, -5.2], 'body', 'body')
    m.bone_pair('hand', [6.8, 1.5, -6.2], 'arm_left', 'arm_right')
    m.bone_pair('thigh', [7.5, 7, 5.5], 'body', 'body')
    m.bone_pair('foot', [9, 2, 4.5], 'thigh_left', 'thigh_right')
    m.cube('body', [-9, 3, -6], [9, 15, 9], 'skin')
    m.cube('body', [-7.5, 2.4, -5], [7.5, 3, 7.5], 'belly')
    m.cube('body', [-6.5, 15, -3], [6.5, 16.6, 6.5], 'skin')
    rr = random.Random(41)
    for _ in range(22):
        x, z = rr.uniform(-8, 8), rr.uniform(-4, 8)
        y = 15 if abs(x) < 6 and -3 < z < 6.5 else rr.uniform(6, 14.5)
        w = rr.uniform(0.8, 1.8)
        if abs(x) >= 6 or not (-3 < z < 6.5):
            side = 1 if x > 0 else -1
            m.cube('body', [side * 9 - (0 if side > 0 else 0.7), y, z], [side * 9 + (0.7 if side > 0 else 0), y + w, z + w], 'wart')
        else:
            m.cube('body', [x, 16.6, z], [x + w, 17.2 + w * 0.3, z + w], 'wart')
    eye(m, 'body', -2.0, 13.4, 9.05, 1.2, pupil=True)
    eye(m, 'body', 2.4, 12.6, 9.05, 1.0, pupil=True)
    m.cube('head', [-8.6, 7, -10], [8.6, 13, -5.4], 'skin')
    m.cube('head', [-8.2, 6.6, -9.6], [8.2, 7.0, -5.6], 'flesh')
    for sd in (1, -1):
        m.cube('head', [sd * 3.2, 12, -9], [sd * 7, 15.4, -5.6], 'skin')
        eye(m, 'head', sd * 5.1, 14.1, -9.2, 3.0)
    teeth_row(m, 'head', -7.6, 7.6, 7.0, 1.1, -10.1, 0.6, 1.2, True, rng=rr)
    m.cube('jaw', [-8.6, 4.4, -10.3], [8.6, 7, -5], 'skin')
    m.cube('jaw', [-7.6, 7, -9.4], [7.6, 7.2, -5.6], 'flesh')
    teeth_row(m, 'jaw', -7.2, 7.2, 7.0, 0.9, -10.25, 0.6, 1.3, False, rng=rr)
    m.cube('throat', [-4, 3.4, -9.4], [4, 5.4, -5], 'belly')
    m.pair('arm_left', 'arm_right', [5.6, 2.4, -6.4], [7.6, 8, -4], 'skin')
    m.pair('hand_left', 'hand_right', [5.4, 0, -8], [8.6, 1.4, -5], 'hand')
    for k in range(4):
        m.pair('hand_left', 'hand_right', [5.3 + k * 0.85, 0, -10.2 + abs(k - 1.5) * 0.4], [5.9 + k * 0.85, 0.8, -8], 'hand')
        m.pair('hand_left', 'hand_right', [5.35 + k * 0.85, 0, -10.6 + abs(k - 1.5) * 0.4], [5.85 + k * 0.85, 0.6, -10.2 + abs(k - 1.5) * 0.4], 'claw')
    m.pair('thigh_left', 'thigh_right', [6.5, 3, 2.5], [11.2, 9.5, 9], 'skin')
    m.pair('foot_left', 'foot_right', [7.6, 0, -0.5], [11.8, 1.6, 7], 'skin_dark')
    for k in range(3):
        m.pair('foot_left', 'foot_right', [7.8 + k * 1.4, 0, -2.4], [8.8 + k * 1.4, 0.8, -0.5], 'skin_dark')
    ground(m)
    m.write()

    base = {'body': {'rot': [0, 0, 0], 'pos': [0, 0, 0], 'scale': [1, 1, 1]}, 'head': R(0), 'jaw': R(2), 'throat': S(1, 1, 1),
            'arm_left': R(0), 'arm_right': R(0), 'thigh_left': R(0), 'thigh_right': R(0), 'foot_left': R(0), 'foot_right': R(0)}
    clips = {}
    c = ClipBuilder(2000, 'IDLE', loop=True, blendIn=200, blendOut=200)
    osc(c, lambda ph: merge(base, {'body': {'rot': [0, 0, 0], 'pos': [0, 0.2 * sin(ph), 0], 'scale': [1 + 0.02 * sin(ph), 1 + 0.03 * sin(ph), 1 + 0.02 * sin(ph)]},
                                   'throat': S(1 + 0.25 * max(0, sin(ph, 2)), 1 + 0.5 * max(0, sin(ph, 2)), 1 + 0.25 * max(0, sin(ph, 2))),
                                   'jaw': R(2 + 3 * max(0, sin(ph, 1, 0.6)))}), 2000, 16)
    clips['idle'] = c.done()
    c = ClipBuilder(800, 'MOVEMENT', loop=True, blendIn=120, blendOut=150)
    osc(c, lambda ph: merge(base, {'body': {'rot': [0, 0, 4 * sin(ph)], 'pos': [0, 0.4 * abs(sin(ph)), 0], 'scale': [1, 1, 1]},
                                   'arm_left': R(-20 * sin(ph)), 'arm_right': R(20 * sin(ph)), 'thigh_left': R(10 * sin(ph)), 'thigh_right': R(-10 * sin(ph))}), 800, 8)
    clips['move'] = c.done()
    squat = merge(base, {'body': {'rot': [4, 0, 0], 'pos': [0, -1.6, 0], 'scale': [1.06, 0.94, 1.06]}, 'thigh_left': R(-25), 'thigh_right': R(-25),
                         'foot_left': R(25), 'foot_right': R(25), 'arm_left': R(10, 0, 10), 'arm_right': R(10, 0, -10)})
    c = ClipBuilder(500, 'ATTACK', blendIn=40)
    c.at(0, base)
    c.at(120, squat)
    c.at(250, merge(base, {'body': {'rot': [-14, 0, 0], 'pos': [0, 0.8, 0], 'scale': [0.96, 1.08, 0.96]}, 'thigh_left': R(45), 'thigh_right': R(45),
                           'foot_left': R(-30), 'foot_right': R(-30), 'arm_left': R(-40), 'arm_right': R(-40)}))
    c.at(500, base)
    clips['hop'] = c.done()
    c = ClipBuilder(900, 'ATTACK', blendIn=60)
    c.at(0, base)
    for k, t in enumerate(range(300, 901, 100)):
        u = min(1, t / 900)
        c.at(t, merge(squat, {'body': {'rot': [4, 0, 2 * (1 if k % 2 else -1)], 'pos': [0, -2.4 * u, 0], 'scale': [1 + 0.14 * u, 1 + 0.08 * u, 1 + 0.14 * u]},
                              'throat': S(1 + 0.5 * u, 1 + 0.8 * u, 1 + 0.5 * u), 'jaw': R(-2)}))
    clips['squat'] = c.done()
    spread = merge(base, {'body': {'rot': [12, 0, 0], 'pos': [0, 0, 0], 'scale': [1.08, 0.95, 1.08]}, 'arm_left': R(-30, 0, 60), 'arm_right': R(-30, 0, -60),
                          'thigh_left': R(30, 0, 50), 'thigh_right': R(30, 0, -50), 'jaw': R(30)})
    c = ClipBuilder(800, 'ATTACK', blendIn=40)
    c.at(0, merge(base, {'body': {'rot': [-20, 0, 0], 'pos': [0, 0, 0], 'scale': [0.95, 1.1, 0.95]}, 'thigh_left': R(60), 'thigh_right': R(60)}))
    c.at(250, spread)
    c.at(800, spread)
    clips['flop'] = c.done()

    def back(ph):
        k = sin(ph, 5)
        return merge(base, {'body': {'rot': [0, 0, 178], 'pos': [0, 2.0, 0], 'scale': [1, 1, 1]}, 'jaw': R(24 + 8 * sin(ph, 3)),
                            'arm_left': R(-40 * k, 0, 20), 'arm_right': R(40 * k, 0, -20), 'thigh_left': R(30 * sin(ph, 5, 0.3)), 'thigh_right': R(-30 * sin(ph, 5, 0.6)),
                            'foot_left': R(20 * k), 'foot_right': R(-20 * k), 'throat': S(1.2, 1.4, 1.2)})
    c = ClipBuilder(2000, 'ATTACK', blendIn=80)
    for k in range(9):
        c.at(k * 200, back(k / 8))
    c.at(2000, merge(base, {'body': {'rot': [0, 0, 40], 'pos': [0, 0.5, 0], 'scale': [1, 1, 1]}}))
    clips['on_back'] = c.done()
    c = ClipBuilder(800, 'ATTACK', blendIn=60)
    c.at(0, base)
    c.at(300, merge(base, {'body': {'rot': [-10, 0, 0], 'pos': [0, 0.4, 0], 'scale': [1.05, 1.05, 1.05]}, 'head': R(-26), 'jaw': R(10), 'throat': S(1.6, 2.0, 1.6)}))
    c.at(500, merge(base, {'body': {'rot': [10, 0, 0], 'pos': [0, -0.4, -1], 'scale': [0.97, 0.97, 0.97]}, 'head': R(-34), 'jaw': R(58), 'throat': S(0.8, 0.8, 0.8)}))
    c.at(800, base)
    clips['belch'] = c.done()
    clips['hurt'] = hurt_clip(base, {'body': {'rot': [-8, 0, 6], 'pos': [0, 0.4, 1], 'scale': [0.96, 1.04, 0.96]}, 'head': R(-14), 'jaw': R(30)})
    clips['stagger'] = stagger_clip(base, lambda k, s_: {'body': {'rot': [-12 * k, 0, 20 * s_ * k], 'pos': [0, 1.5 * k, 1.5 * k], 'scale': [1, 1, 1]},
                                                         'jaw': R(40 * k), 'arm_left': R(-40 * k, 0, 30 * k), 'arm_right': R(-30 * k, 0, -40 * k)})
    clips['death'] = death_clip(base, {'body': {'rot': [0, 0, 60], 'pos': [0, 1, 0], 'scale': [1, 1, 1]}, 'jaw': R(40)},
                                {'body': {'rot': [0, 0, 178], 'pos': [0, 2.0, 0], 'scale': [1.02, 0.9, 1.02]}, 'jaw': R(50), 'arm_left': R(-60, 0, 30),
                                 'arm_right': R(-70, 0, -30), 'thigh_left': R(40), 'thigh_right': R(50), 'throat': S(0.8, 0.8, 0.8)})
    # Strength contests: it bears its whole bulk down on you, both forelegs pinning, the throat swelling.
    clips.update(grapple_set(
        base,
        {'body': {'rot': [14, 0, 0], 'pos': [0, -0.6, -1.4], 'scale': [1.06, 0.96, 1.06]}, 'head': R(-10), 'jaw': R(22), 'throat': S(1.3, 1.5, 1.3),
         'arm_left': R(-56, 0, 14), 'arm_right': R(-56, 0, -14), 'thigh_left': R(-20), 'thigh_right': R(-20), 'foot_left': R(18), 'foot_right': R(18)},
        {'body': {'rot': [-4, 0, 6], 'pos': [0, 0.2, 1.2], 'scale': [0.96, 1.06, 0.96]}, 'head': R(-24), 'jaw': R(36), 'throat': S(0.9, 0.9, 0.9),
         'arm_left': R(-36, 0, 26), 'arm_right': R(-40, 0, -22)},
        {'body': {'rot': [24, 0, 0], 'pos': [0, -1.2, -2.6], 'scale': [1.14, 0.9, 1.14]}, 'head': R(4), 'jaw': R(44), 'throat': S(1.7, 2.0, 1.7),
         'arm_left': R(-70, 0, 8), 'arm_right': R(-70, 0, -8)},
        {'body': {'rot': [-22, 0, 0], 'pos': [0, 1.4, 0.6], 'scale': [0.94, 1.12, 0.94]}, 'head': R(-30), 'jaw': R(12), 'throat': S(1.8, 2.2, 1.8),
         'arm_left': R(-120, 0, 10), 'arm_right': R(-120, 0, -10), 'thigh_left': R(-40), 'thigh_right': R(-40)},
        {'body': {'rot': [28, 0, 10], 'pos': [0, -1.6, -2.4], 'scale': [1.12, 0.9, 1.12]}, 'head': R(-4), 'jaw': R(60), 'throat': S(0.8, 0.8, 0.8),
         'arm_left': R(-30, 0, 40), 'arm_right': R(-20, 0, -50), 'thigh_left': R(30), 'thigh_right': R(30)}))
    write_clips('bloat_toad', clips)


# ----------------------------------------------------------------------------------------------------------- Brood Mother

BROOD_LEGS = ((1, -0.2, 26), (2, 3.8, -24))


def brood_mother():
    mats = {
        'skin': Mat('#7a6a74', blotch='#4a3c46', blotch_amt=0.55, blotch_scale=3, lines='#2e2228', line_count=3),
        'skin_dark': Mat('#4e424a', blotch='#2e242c', blotch_amt=0.5),
        'ribs': Mat('#8a7a84', bands='#3a2e36', grain=0.04),
        'sac': Mat('#e2d8b4', blotch='#5a4a34', blotch_amt=0.75, blotch_scale=2, lines='#8a3a6a', line_count=8, rim=0.08),
        'growth': Mat('#b8a888', blotch='#6a5a40', blotch_amt=0.5, lines='#8a3a6a', line_count=3),
        'sclera': Mat('#f0ead0', rim=0.1),
        'iris': Mat('#8cff3a', blotch='#3a8a10', blotch_amt=0.3, shade=False, rim=0.05),
        'pupil': Mat('#060404', grain=0.0, shade=False, highlight='#ffffff'),
        'flesh': Mat('#4a0a14', grain=0.05, rim=0.05),
        'tooth': Mat('#e6dcc0', grain=0.04, rim=0.08),
        'claw': Mat('#1e1a1c', grain=0.05, highlight='#5a5258', rim=0.1),
    }
    m = Model('brood_mother', mats)
    m.bone('root', [0, 0, 0])
    m.bone('body', [0, 12, 2], 'root')
    m.bone('torso', [0, 13.5, 1], 'body', rot=(-28, 0, 0))
    m.bone('head', [0, 22, -0.5], 'torso', rot=(24, 0, 0))
    m.bone('jaw', [0, 21.6, -3], 'head')
    m.bone('sac', [0, 15, 4], 'body')
    m.bone_pair('arm', [3.4, 21, 0.5], 'torso', 'torso', rot=(-10, 0, 10))
    m.bone_pair('forearm', [4, 13.5, 0.5], 'arm_left', 'arm_right', rot=(30, 0, 0))
    for n, z, splay in BROOD_LEGS:
        m.bone_pair('leg%d' % n, [2.4, 12, z], 'body', 'body', rot=(0, splay, 38))
        m.bone_pair('shin%d' % n, [7.5, 12, z], 'leg%d_left' % n, 'leg%d_right' % n, rot=(0, 0, -16))
    m.cube('body', [-2.6, 10.4, -1.2], [2.6, 13.8, 4.4], 'skin')
    m.cube('torso', [-2.6, 13.5, -1.4], [2.6, 22, 2.4], 'skin')
    for sd in (1, -1):
        m.cube('torso', [sd * 2.6, 15, -1], [sd * 2.9, 20.5, 2], 'ribs')
    m.cube('head', [-2.1, 20.6, -4.6], [2.1, 25, -0.4], 'skin')
    m.cube('head', [-1.6, 25, -3.6], [1.6, 26, -1], 'skin_dark')
    for (x, y, sz) in ((-1.1, 23.6, 1.0), (1.0, 23.8, 1.1), (0, 24.7, 0.7), (-1.5, 22.6, 0.6), (1.5, 22.7, 0.6), (0, 22.6, 0.5)):
        eye(m, 'head', x, y, -4.6, sz, pupil=sz > 0.6)
    m.cube('jaw', [-1.7, 19.8, -4.9], [1.7, 21, -1.4], 'skin_dark')
    rr = random.Random(51)
    teeth_row(m, 'jaw', -1.5, 1.5, 21.0, 0.7, -4.95, 0.3, 0.5, False, rng=rr)
    teeth_row(m, 'head', -1.6, 1.6, 21.0, 0.6, -4.7, 0.3, 0.5, True, rng=rr)
    m.cube('sac', [-5.8, 7.5, 3.6], [5.8, 20.5, 15.5], 'sac')
    m.cube('sac', [-4.6, 20.5, 5], [4.6, 22, 13.5], 'sac')
    m.cube('sac', [-4.4, 6.4, 5.5], [4.4, 7.5, 13], 'sac')
    for _ in range(9):
        x, y, z = rr.uniform(-5.4, 4.4), rr.uniform(9, 19), rr.uniform(5, 14)
        w = rr.uniform(0.8, 1.6)
        side = rr.choice((1, -1))
        m.cube('sac', [side * 5.8 - (0 if side > 0 else w * 0.5), y, z], [side * 5.8 + (w * 0.5 if side > 0 else 0), y + w, z + w], 'growth')
    m.pair('arm_left', 'arm_right', [2.9, 13.5, -0.1], [4.1, 21.4, 1.1], 'skin')
    m.pair('forearm_left', 'forearm_right', [3.4, 5.5, 0], [4.6, 14, 1], 'skin')
    for k in range(3):
        m.pair('forearm_left', 'forearm_right', [3.1 + k * 0.6, 1.5, 0.1], [3.5 + k * 0.6, 5.8, 0.6], 'claw')
    for n, z, _ in BROOD_LEGS:
        m.pair('leg%d_left' % n, 'leg%d_right' % n, [2.4, 11.4, z - 0.55], [7.8, 12.6, z + 0.55], 'skin')
        m.pair('shin%d_left' % n, 'shin%d_right' % n, [7.0, -1.5, z - 0.45], [8.0, 12.4, z + 0.45], 'skin_dark')
        m.pair('shin%d_left' % n, 'shin%d_right' % n, [7.1, -2.4, z - 0.35], [7.9, -1.5, z + 0.35], 'claw')
    ground(m)
    m.write()

    legs = [('leg%d_%s' % (n, sd), sg) for n, _, _ in BROOD_LEGS for sd, sg in (('left', 1), ('right', -1))]
    base = {'body': RP([0, 0, 0], [0, 0, 0]), 'torso': R(0), 'head': R(0), 'jaw': R(4), 'sac': {'rot': [0, 0, 0], 'scale': [1, 1, 1]},
            'arm_left': R(0), 'arm_right': R(0), 'forearm_left': R(0), 'forearm_right': R(0)}
    clips = {}
    c = ClipBuilder(2400, 'IDLE', loop=True, blendIn=200, blendOut=200)
    osc(c, lambda ph: merge(base, {'sac': {'rot': [2 * sin(ph), 0, 0], 'scale': [1 + 0.04 * sin(ph, 2), 1 + 0.05 * sin(ph, 2), 1 + 0.04 * sin(ph, 2)]},
                                   'torso': R(3 * sin(ph)), 'head': R(0, 14 * sin(ph, 1, 0.3), 10 * sin(ph, 2)), 'jaw': R(4 + 8 * max(0, sin(ph, 3))),
                                   'arm_left': R(4 * sin(ph)), 'arm_right': R(-4 * sin(ph))}), 2400, 16)
    clips['idle'] = c.done()
    c = ClipBuilder(1200, 'MOVEMENT', loop=True, blendIn=150, blendOut=150)
    osc(c, lambda ph: merge(base, {'body': RP([0, 0, 4 * sin(ph)], [0, 0.5 * abs(sin(ph)), 0]), 'sac': {'rot': [4 * sin(ph, 2), 0, -3 * sin(ph)], 'scale': [1, 1, 1]},
                                   'torso': R(4), 'head': R(-4), 'arm_left': R(-14 * sin(ph)), 'arm_right': R(14 * sin(ph))},
                            {b: {'rot': [0, -sg * 18 * sin(ph, 1, 0.5 * (i % 2)), sg * 12 * max(0, sin(ph, 1, 0.25 + 0.5 * (i % 2)))]} for i, (b, sg) in enumerate(legs)}), 1200, 12)
    clips['move'] = c.done()
    c = ClipBuilder(1200, 'ATTACK', blendIn=60)
    c.at(0, base)
    for k, t in enumerate(range(200, 1201, 100)):
        u = t / 1200
        j = 1 if k % 2 else -1
        c.at(t, merge(base, {'torso': R(-20 * u), 'head': R(-14 * u, 0, 10 * j * u), 'jaw': R(30 * u),
                             'sac': {'rot': [-6 * u, 2 * j, 2 * j], 'scale': [1 + 0.3 * u, 1 + 0.25 * u, 1 + 0.3 * u]},
                             'arm_left': R(-60 * u, 0, -20 * u), 'arm_right': R(-60 * u, 0, 20 * u)}))
    clips['birth'] = c.done()

    def sag(ph):
        return merge(base, {'body': RP([0, 0, 0], [0, -0.8 + 0.3 * sin(ph, 2), 0]), 'torso': R(26 + 3 * sin(ph, 2)), 'head': R(28, 0, 14), 'jaw': R(34),
                            'sac': {'rot': [22, 0, 4 * sin(ph)], 'scale': [0.82, 0.62, 0.86]}, 'arm_left': R(30), 'arm_right': R(26)})
    c = ClipBuilder(1800, 'ATTACK', blendIn=60)
    for k in range(10):
        c.at(k * 200, sag(k / 9))
    c.at(1800, merge(base, {'sac': {'rot': [8, 0, 0], 'scale': [0.9, 0.8, 0.92]}}))
    clips['recoil'] = c.done()
    c = ClipBuilder(1000, 'ATTACK', blendIn=60)
    c.at(0, base)
    c.at(600, merge(base, {'torso': R(-26), 'head': R(-22), 'jaw': R(10), 'arm_left': R(-30, 0, -30), 'arm_right': R(-30, 0, 30)}))
    c.at(700, merge(base, {'torso': R(16), 'head': R(18), 'jaw': R(46)}))
    c.at(1000, base)
    clips['spit'] = c.done()
    c = ClipBuilder(800, 'ATTACK', blendIn=60)
    c.at(0, base)
    c.at(400, merge(base, {'torso': R(-12, 20, 0), 'arm_right': R(-160, 0, -20), 'forearm_right': R(-20), 'head': R(-10)}))
    c.at(500, merge(base, {'torso': R(18, -24, 0), 'arm_right': R(-40, 0, 30), 'forearm_right': R(10), 'head': R(10)}))
    c.at(800, base)
    clips['slap'] = c.done()
    clips['hurt'] = hurt_clip(base, {'torso': R(-16, 0, 10), 'head': R(-18), 'jaw': R(30), 'sac': {'rot': [-10, 0, 8], 'scale': [1.05, 0.95, 1.05]}})
    clips['stagger'] = stagger_clip(base, lambda k, s_: {'torso': R(-22 * k, 0, 18 * s_ * k), 'head': R(-20 * k, 16 * s_ * k, 0), 'jaw': R(40 * k),
                                                         'sac': {'rot': [-14 * k, 0, 20 * s_ * k], 'scale': [1, 1, 1]},
                                                         'arm_left': R(-60 * k, 0, -40 * k), 'arm_right': R(-50 * k, 0, 45 * k)})
    clips['death'] = death_clip(base, {'torso': R(-20), 'head': R(-30), 'jaw': R(40), 'sac': {'rot': [0, 0, 0], 'scale': [1.15, 1.1, 1.15]}},
                                {'body': RP([0, 0, 0], [0, -5.5, 0]), 'torso': R(60, 0, 20), 'head': R(20, 30, 0), 'jaw': R(44),
                                 'sac': {'rot': [30, 0, 30], 'scale': [0.75, 0.5, 0.8]}, 'arm_left': R(-40, 0, -60), 'arm_right': R(-70, 0, 50)})
    # Strength contests: the claws hook into you and pull you toward the swollen sac, its legs splayed to hold.
    clips.update(grapple_set(
        base,
        merge({'body': RP([0, 0, 0], [0, -0.8, 0]), 'torso': R(18), 'head': R(10), 'jaw': R(30), 'sac': {'rot': [-6, 0, 0], 'scale': [1.08, 1.06, 1.08]},
               'arm_left': R(-88, 0, 12), 'arm_right': R(-88, 0, -12), 'forearm_left': R(-34), 'forearm_right': R(-34)},
              {b: {'rot': [0, 0, sg * 14]} for b, sg in legs}),
        {'torso': R(-6, 0, 8), 'head': R(-18, 0, -8), 'jaw': R(44), 'sac': {'rot': [-14, 0, 6], 'scale': [1.12, 1.0, 1.12]},
         'arm_left': R(-70, 0, 26), 'arm_right': R(-74, 0, -22), 'forearm_left': R(-16), 'forearm_right': R(-16)},
        {'body': RP([0, 0, 0], [0, -1.4, -1]), 'torso': R(30), 'head': R(22), 'jaw': R(50), 'sac': {'rot': [6, 0, 0], 'scale': [1.2, 1.14, 1.2]},
         'arm_left': R(-96, 0, 4), 'arm_right': R(-96, 0, -4), 'forearm_left': R(-58), 'forearm_right': R(-58)},
        {'torso': R(-20, 24, 0), 'head': R(-20), 'jaw': R(16), 'sac': {'rot': [-10, 0, 0], 'scale': [1.0, 1.1, 1.0]},
         'arm_left': R(-150, 0, -10), 'arm_right': R(-150, 0, 10), 'forearm_left': R(-24), 'forearm_right': R(-24)},
        {'torso': R(26, -30, 0), 'head': R(16), 'jaw': R(46), 'sac': {'rot': [14, 0, -8], 'scale': [1.1, 1.0, 1.1]},
         'arm_left': R(-40, 0, -40), 'arm_right': R(-30, 0, 50), 'forearm_left': R(0), 'forearm_right': R(0)}))
    write_clips('brood_mother', clips)


KINDS = {}


def main():
    want = sys.argv[1:] or list(KINDS)
    for k in want:
        KINDS[k]()


if __name__ == '__main__':
    KINDS.update({k: globals()[k] for k in ('school_maw', 'school_crawler', 'fly_head', 'forest_stalker', 'grave_hound', 'veiled_bride', 'bloat_toad', 'brood_mother') if k in globals()})
    main()
