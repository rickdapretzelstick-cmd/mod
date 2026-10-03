#!/usr/bin/env python3
"""
Draws a Blockbench model in poses from a clip, the way the mod's BbModel renderer poses it, for checking animations
without starting the game. Faces are filled with the average colour of their texture area and shaded by facing.

    python3 tools/bb_pose_preview.py model.bbmodel texture.png clip.json out.png [t_ms ...]

Without times it draws the clip's key times. Each column is one time; the rows are front, side (facing left) and
three-quarter views. The pose math mirrors client/model/BbModel.apply: T(pivot + pos with x negated) R(rest + rot with
x and y negated, ZYX) S T(-pivot).
"""
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw


def rot_zyx(rz, ry, rx):
    cz, sz, cy, sy, cx, sx = math.cos(rz), math.sin(rz), math.cos(ry), math.sin(ry), math.cos(rx), math.sin(rx)
    Rz = np.array([[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])
    Ry = np.array([[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]])
    Rx = np.array([[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]])
    return Rz @ Ry @ Rx


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = [x, y, z]
    return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


class Model:
    def __init__(self, path, texture):
        d = json.load(open(path))
        self.tw = d.get('resolution', {}).get('width', 16)
        self.th = d.get('resolution', {}).get('height', 16)
        self.elements = {e['uuid']: e for e in d['elements']}
        self.tex = np.asarray(Image.open(texture).convert('RGBA')).astype(float)
        self.roots = [self.group(g, None) for g in d['outliner'] if isinstance(g, dict)]

    def group(self, o, parent):
        g = {'name': o['name'], 'origin': o.get('origin', [0, 0, 0]), 'rot': [math.radians(v) for v in o.get('rotation', [0, 0, 0])],
             'cubes': [], 'children': []}
        for c in o.get('children', []):
            if isinstance(c, dict):
                g['children'].append(self.group(c, g))
            elif c in self.elements:
                g['cubes'].append(self.elements[c])
        return g

    def colour(self, face):
        uv = face.get('uv')
        if not uv:
            return None
        h, w = self.tex.shape[:2]
        x1, y1, x2, y2 = uv
        a, b = sorted([x1 * w / self.tw, x2 * w / self.tw])
        c, d = sorted([y1 * h / self.th, y2 * h / self.th])
        a, c = int(a), int(c)
        b, d = max(a + 1, int(math.ceil(b))), max(c + 1, int(math.ceil(d)))
        px = self.tex[c:d, a:b].reshape(-1, 4)
        px = px[px[:, 3] > 10]
        if len(px) == 0:
            return None
        return px[:, :3].mean(axis=0)

    def quads(self, pose):
        out = []
        for g in self.roots:
            self.walk(g, np.eye(4), pose, out)
        return out

    def walk(self, g, parent, pose, out):
        b = pose.get(g['name'], {})
        p, r, s = b.get('pos'), b.get('rot'), b.get('scale')
        ox, oy, oz = g['origin']
        m = parent @ T(ox + (-p[0] if p else 0), oy + (p[1] if p else 0), oz + (p[2] if p else 0))
        rx, ry, rz = g['rot']
        if r:
            rx -= math.radians(r[0])
            ry -= math.radians(r[1])
            rz += math.radians(r[2])
        m = m @ rot_zyx(rz, ry, rx)
        if s is not None:
            s = s if isinstance(s, list) else [s, s, s]
            m = m @ S(*s)
        m = m @ T(-ox, -oy, -oz)
        for c in g['cubes']:
            cm = m
            cr = [math.radians(v) for v in c.get('rotation', [0, 0, 0])]
            if any(cr):
                o = c.get('origin', [0, 0, 0])
                cm = m @ T(*o) @ rot_zyx(cr[2], cr[1], cr[0]) @ T(-o[0], -o[1], -o[2])
            inf = c.get('inflate', 0)
            x1, y1, z1 = [v - inf for v in c['from']]
            x2, y2, z2 = [v + inf for v in c['to']]
            faces = {
                'north': [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
                'south': [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
                'east': [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
                'west': [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
                'up': [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
                'down': [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
            }
            for name, pts in faces.items():
                f = c.get('faces', {}).get(name)
                if not f or f.get('texture') is None and 'texture' in f:
                    continue
                col = self.colour(f)
                if col is None:
                    continue
                w = [(cm @ np.array([*pt, 1.0]))[:3] for pt in pts]
                out.append((w, col))
        for ch in g['children']:
            self.walk(ch, m, pose, out)


def sample(clip, t):
    """The clip's pose at t ms: each bone channel interpolated between the keys that set it (linear)."""
    tracks = {}
    for k in clip['keys']:
        for bone, ch in k['bones'].items():
            if isinstance(ch, list):
                ch = {'rot': ch}
            for c in ('rot', 'pos', 'scale'):
                if c in ch:
                    tracks.setdefault((bone, c), []).append((k['t'], ch[c]))
    pose = {}
    for (bone, c), keys in tracks.items():
        if t < keys[0][0]:
            continue  # before a channel's first key the bone is at rest (clips here key every bone at 0 anyway)
        v = keys[0][1]
        for i, (kt, kv) in enumerate(keys):
            if kt <= t:
                v = kv
                if i + 1 < len(keys) and keys[i + 1][0] > t:
                    nt, nv = keys[i + 1]
                    f = (t - kt) / max(1, nt - kt)
                    a = kv if isinstance(kv, list) else [kv] * 3
                    b = nv if isinstance(nv, list) else [nv] * 3
                    v = [x + (y - x) * f for x, y in zip(a, b)]
        pose.setdefault(bone, {})[c] = v
    return pose


def draw(model, pose, view, size=300, scale=3.4):
    img = Image.new('RGB', (size, size), (34, 34, 40))
    dr = ImageDraw.Draw(img)
    yaw = {'front': 0, 'side': math.pi / 2, 'three': math.radians(35)}[view]
    # The model faces -z: the front view looks at it from -z.
    cy, sy = math.cos(yaw), math.sin(yaw)
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)
    polys = []
    for w, col in model.quads(pose):
        pts = []
        for x, y, z in w:
            vx = x * cy + z * sy
            vz = -x * sy + z * cy
            pts.append((vx, y, vz))
        n = np.cross(np.array(w[1]) - np.array(w[0]), np.array(w[3]) - np.array(w[0]))
        ln = np.linalg.norm(n)
        shade = 0.55 + 0.45 * abs(float(np.dot(n / ln, light))) if ln > 1e-9 else 0.7
        depth = sum(p[2] for p in pts) / 4
        polys.append((depth, pts, tuple(int(min(255, c * shade)) for c in col)))
    # Far first (larger z is farther from a camera at -z).
    polys.sort(key=lambda p: -p[0])
    for _, pts, col in polys:
        dr.polygon([(size / 2 - p[0] * scale, size - 12 - p[1] * scale) for p in pts], fill=col)
    dr.line([(0, size - 12), (size, size - 12)], fill=(80, 80, 90))
    return img


def main():
    model = Model(sys.argv[1], sys.argv[2])
    clip = json.load(open(sys.argv[3]))
    out = sys.argv[4]
    times = [float(x) for x in sys.argv[5:]] or sorted({k['t'] for k in clip['keys']})
    views = ['front', 'side', 'three']
    size = 300
    sheet = Image.new('RGB', (size * len(times), size * len(views)), (20, 20, 24))
    for i, t in enumerate(times):
        pose = sample(clip, t)
        for j, v in enumerate(views):
            im = draw(model, pose, v, size)
            ImageDraw.Draw(im).text((6, 6), '%s %dms' % (v, t), fill=(220, 220, 220))
            sheet.paste(im, (i * size, j * size))
    sheet.save(out)
    print('wrote', out, len(times), 'poses')


if __name__ == '__main__':
    main()
