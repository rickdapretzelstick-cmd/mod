#!/usr/bin/env python3
"""
Draws player clips as filmstrips without starting the game: the jointed player skeleton posed exactly as the mod's
Rig.solve poses it (bones local to their parent, rotations ZYX in degrees, offsets right/up/forward in pixels), boxes
for the body segments, seen three-quarters on and from the side.

    python3 tools/anim_preview.py out.png clip[:t1,t2,...] [clip ...]     # names as in assets/jjk/animations
    python3 tools/anim_preview.py --check                                 # validates every clip, draws nothing

Without times a clip is drawn at its key times (thinned to at most 10). Bones a clip leaves alone are drawn at rest.
The sampler mirrors client/anim (Track, Easing, AnimLibrary: poses, @mirror, mirrorOf, f/fps/refStart keys).
"""
import glob
import json
import math
import os
import sys

import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'animations')

PARENT = {'root': None, 'hips': 'root', 'chest': 'hips', 'neck': 'chest', 'head': 'neck',
          'rightArm': 'chest', 'rightForearm': 'rightArm', 'rightHand': 'rightForearm',
          'leftArm': 'chest', 'leftForearm': 'leftArm', 'leftHand': 'leftForearm',
          'rightThigh': 'hips', 'rightShin': 'rightThigh', 'rightFoot': 'rightShin',
          'leftThigh': 'hips', 'leftShin': 'leftThigh', 'leftFoot': 'leftShin'}
BONES = list(PARENT)
# Model-space rest pivots (pixels, y down, the character's right at -x, forward at -z), as Rig.Parts derives them.
REST = {'root': (0, 24, 0), 'hips': (0, 12, 0), 'chest': (0, 8, 0), 'neck': (0, 0, 0), 'head': (0, 0, 0),
        'rightArm': (-5, 2, 0), 'rightForearm': (-6, 6, 0), 'rightHand': (-6, 10, 0),
        'leftArm': (5, 2, 0), 'leftForearm': (6, 6, 0), 'leftHand': (6, 10, 0),
        'rightThigh': (-1.9, 12, 0), 'rightShin': (-1.9, 18, 0), 'rightFoot': (-1.9, 22, 0),
        'leftThigh': (1.9, 12, 0), 'leftShin': (1.9, 18, 0), 'leftFoot': (1.9, 22, 0)}
# Boxes per bone: (min, max) in the bone's own space, and a colour.
BOX = {'head': ((-4, -8, -4), (4, 0, 4), (224, 172, 132)),
       'chest': ((-4, -8, -2), (4, 0, 2), (60, 110, 190)),
       'hips': ((-4, -4, -2), (4, 0, 2), (50, 90, 160)),
       'rightArm': ((-3, -2, -2), (1, 4, 2), (200, 70, 70)), 'rightForearm': ((-2, 0, -2), (2, 4, 2), (230, 110, 90)),
       'rightHand': ((-2, 0, -2), (2, 2, 2), (250, 190, 150)),
       'leftArm': ((-1, -2, -2), (3, 4, 2), (70, 160, 90)), 'leftForearm': ((-2, 0, -2), (2, 4, 2), (110, 200, 120)),
       'leftHand': ((-2, 0, -2), (2, 2, 2), (250, 190, 150)),
       'rightThigh': ((-2, 0, -2), (2, 6, 2), (110, 60, 60)), 'rightShin': ((-2, 0, -2), (2, 4, 2), (150, 80, 70)),
       'rightFoot': ((-2, 0, -2), (2, 2, 2), (60, 40, 40)),
       'leftThigh': ((-2, 0, -2), (2, 6, 2), (50, 90, 60)), 'leftShin': ((-2, 0, -2), (2, 4, 2), (70, 120, 80)),
       'leftFoot': ((-2, 0, -2), (2, 2, 2), (40, 50, 40))}
ALIAS = {'waist': 'hips', 'pelvis': 'hips', 'torso': 'chest', 'body': 'chest', 'uppertorso': 'chest', 'spine': 'chest',
         'mainarm': 'rightArm', 'mainforearm': 'rightForearm', 'mainhand': 'rightHand', 'offarm': 'leftArm',
         'offforearm': 'leftForearm', 'offhand': 'leftHand', 'rightleg': 'rightThigh', 'leftleg': 'leftThigh',
         'rightknee': 'rightShin', 'leftknee': 'leftShin', 'rightelbow': 'rightForearm', 'leftelbow': 'leftForearm',
         'rightupperarm': 'rightArm', 'leftupperarm': 'leftArm', 'rightcalf': 'rightShin', 'leftcalf': 'leftShin',
         'rightfist': 'rightHand', 'leftfist': 'leftHand', 'rightwrist': 'rightHand', 'leftwrist': 'leftHand',
         'rightankle': 'rightFoot', 'leftankle': 'leftFoot', 'rightlowerarm': 'rightForearm', 'leftlowerarm': 'leftForearm',
         'rightlowerleg': 'rightShin', 'leftlowerleg': 'leftShin', 'rightupperleg': 'rightThigh', 'leftupperleg': 'leftThigh'}
LOWER = {b.lower(): b for b in BONES}
EASES = ('LINEAR', 'EASE_IN', 'EASE_OUT', 'EASE_IN_OUT', 'STEP', 'OVERSHOOT', 'SPRING')
EASE_ALIAS = {'IN': 'EASE_IN', 'STRIKE': 'EASE_IN', 'ACCEL': 'EASE_IN', 'OUT': 'EASE_OUT', 'SETTLE': 'EASE_OUT', 'DECEL': 'EASE_OUT',
              'IN_OUT': 'EASE_IN_OUT', 'SMOOTH': 'EASE_IN_OUT', 'HOLD': 'STEP', 'CONSTANT': 'STEP', 'BACK': 'OVERSHOOT', 'SNAP': 'OVERSHOOT',
              'ELASTIC': 'SPRING'}
PRIORITIES = ('IDLE', 'MOVEMENT', 'COMBAT', 'ATTACK', 'SPECIAL', 'AWAKENING', 'RAGDOLL')
LAYERS = ('BASE', 'LOWER_BODY', 'UPPER_BODY', 'HEAD')


def bone(name):
    b = LOWER.get(name.lower()) or ALIAS.get(name.lower().replace('_', ''))
    if b is None:
        raise ValueError('unknown bone ' + name)
    return b


def ease_name(s):
    s = s.upper().replace('-', '_')
    s = EASE_ALIAS.get(s, s)
    if s not in EASES:
        raise ValueError('unknown ease ' + s)
    return s


def ease(kind, f):
    if f <= 0:
        return 0.0
    if f >= 1:
        return 1.0
    if kind == 'LINEAR':
        return f
    if kind == 'EASE_IN':
        return f ** 3
    if kind == 'EASE_OUT':
        return 1 - (1 - f) ** 3
    if kind == 'EASE_IN_OUT':
        return 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2
    if kind == 'STEP':
        return 0.0
    if kind == 'OVERSHOOT':
        g = f - 1
        return 1 + 2.9 * g ** 3 + 1.9 * g * g
    return 1 - math.exp(-6 * f) * math.cos(f * math.pi * 4.5)


def mirror_bone(b):
    if b.startswith('right'):
        return 'left' + b[5:]
    if b.startswith('left'):
        return 'right' + b[4:]
    return b


def mirror_pose(p):
    out = {}
    for b, ch in p.items():
        d = {}
        if 'rot' in ch:
            d['rot'] = [ch['rot'][0], -ch['rot'][1], -ch['rot'][2]]
        if 'pos' in ch:
            d['pos'] = [-ch['pos'][0], ch['pos'][1], ch['pos'][2]]
        if 'scale' in ch:
            d['scale'] = ch['scale']
        out[mirror_bone(b)] = d
    return out


def parse_bones(o, rig='player'):
    """bones object -> {bone: {rot, pos, scale, ease}}"""
    out = {}
    for name, v in (o or {}).items():
        b = bone(name) if rig == 'player' else name
        d = {}
        if isinstance(v, list):
            if len(v) != 3:
                raise ValueError('expected [x, y, z], got %s' % v)
            d['rot'] = [float(x) for x in v]
        else:
            for c in ('rot', 'pos'):
                if c in v:
                    if len(v[c]) != 3:
                        raise ValueError('expected [x, y, z], got %s' % v[c])
                    d[c] = [float(x) for x in v[c]]
            if 'scale' in v:
                s = v['scale']
                d['scale'] = [float(s)] * 3 if not isinstance(s, list) else [float(x) for x in s]
            if 'ease' in v:
                d['ease'] = ease_name(v['ease'])
        out[b] = d
    return out


class Library:
    def __init__(self, root=ROOT):
        self.poses, self.raw, self.group, self.errors = {}, {}, {}, []
        pose_json = {}
        for path in sorted(glob.glob(os.path.join(root, '**', '*.json'), recursive=True)):
            rel = os.path.relpath(path, root).replace(os.sep, '/')[:-5].split('/')
            base, group = rel[-1], rel[0] if len(rel) > 1 else 'misc'
            try:
                o = json.load(open(path))
            except Exception as ex:
                self.errors.append('%s: %s' % (path, ex))
                continue
            if group == 'poses':
                if 'poses' in o:
                    pose_json.update(o['poses'])
                else:
                    pose_json[o.get('name', base)] = o
                continue
            for c in o.get('clips', [o]):
                n = c.get('name', base)
                self.raw[n] = c
                self.group[n] = group
        for n in pose_json:
            self._pose(n, pose_json, set())
        self.clips = {}
        for n, c in self.raw.items():
            if 'mirrorOf' in c:
                continue
            try:
                self.clips[n] = self.compile(n, c)
            except Exception as ex:
                self.errors.append('clip %s: %s' % (n, ex))
        for n, c in self.raw.items():
            if 'mirrorOf' in c:
                src = self.clips.get(c['mirrorOf'])
                if src is None:
                    self.errors.append('clip %s: mirrorOf unknown clip' % n)
                else:
                    m = dict(src)
                    m['tracks'] = {}
                    for (b, ch), tr in src['tracks'].items():
                        vals = [[-v[0], v[1], v[2]] if ch == 'pos' else [v[0], -v[1], -v[2]] if ch == 'rot' else v for v in tr[1]]
                        m['tracks'][(mirror_bone(b), ch)] = (tr[0], vals, tr[2])
                    self.clips[n] = m

    def _pose(self, name, js, visiting):
        if name in self.poses:
            return self.poses[name]
        o = js.get(name)
        if o is None or name in visiting:
            self.errors.append('pose %s: unknown or extends itself' % name)
            return None
        visiting.add(name)
        bones = {}
        ext = o.get('extends', [])
        for parent in ([ext] if isinstance(ext, str) else ext):
            mir = parent.endswith('@mirror')
            p = self._pose(parent[:-7] if mir else parent, js, visiting)
            if p:
                merge(bones, mirror_pose(p) if mir else p)
        try:
            merge(bones, parse_bones(o.get('bones'), o.get('rig', 'player')))
        except Exception as ex:
            self.errors.append('pose %s: %s' % (name, ex))
        self.poses[name] = bones
        return bones

    def compile(self, name, o):
        fps = float(o.get('fps', 50))
        frame = 1000.0 / fps
        ref = float(o.get('refStart', 0))
        rig = o.get('rig', 'player')
        keyed, last, times, markers = {}, 0.0, [], []
        if 'keys' not in o:
            raise ValueError('no keys')
        for k in o['keys']:
            if 'f' in k and 't' not in k:
                t = (float(k['f']) - ref) * frame
            elif 't' in k:
                t = float(k['t'])
            else:
                raise ValueError('a key has neither t nor f')
            last = max(last, t)
            times.append(t)
            key_ease = ease_name(k.get('ease', 'EASE_IN_OUT'))
            pose = {}
            if 'pose' in k:
                for p in ([k['pose']] if isinstance(k['pose'], str) else k['pose']):
                    mir = p.endswith('@mirror')
                    pn = p[:-7] if mir else p
                    pn = pn[6:] if pn.startswith('poses/') else pn
                    d = self.poses.get(pn)
                    if d is None:
                        self.errors.append('clip %s: unknown pose %s' % (name, p))
                    else:
                        merge(pose, mirror_pose(d) if mir else d)
            merge(pose, parse_bones(k.get('bones'), rig))
            for b, ch in pose.items():
                e = ch.get('ease', key_ease)
                for c in ('rot', 'pos', 'scale'):
                    if c in ch:
                        for x in ch[c]:
                            if not math.isfinite(x):
                                raise ValueError('non-finite value on ' + b)
                        keyed.setdefault((b, c), []).append((t, ch[c], e))
            if 'marker' in k:
                markers.append((t, k['marker']))
        for m in o.get('markers', []):
            markers.append(((float(m['f']) - ref) * frame if 'f' in m and 't' not in m else float(m.get('t', 0)), m.get('label', '?')))
        tracks = {}
        for key, lst in keyed.items():
            lst.sort(key=lambda x: x[0])
            dedup = []
            for kk in lst:
                if dedup and abs(dedup[-1][0] - kk[0]) < 1e-3:
                    dedup[-1] = kk
                else:
                    dedup.append(kk)
            tracks[key] = ([x[0] for x in dedup], [x[1] for x in dedup], [x[2] for x in dedup])
        if 'endFrame' in o:
            duration = (float(o['endFrame']) - ref) * frame
        elif 'duration' in o:
            duration = float(o['duration'])
        elif 'frames' in o:
            duration = float(o['frames']) * frame
        else:
            duration = last
        if o.get('priority', 'SPECIAL').upper() not in PRIORITIES:
            raise ValueError('unknown priority ' + o['priority'])
        if o.get('layer', 'BASE').upper() not in LAYERS:
            raise ValueError('unknown layer ' + o['layer'])
        if 'blendEase' in o:
            ease_name(o['blendEase'])
        loop_start = (float(o['loopStartFrame']) - ref) * frame if 'loopStartFrame' in o else float(o.get('loopStart', 0))
        loop_end = (float(o['loopEndFrame']) - ref) * frame if 'loopEndFrame' in o else float(o.get('loopEnd', duration))
        return {'name': name, 'duration': duration, 'tracks': tracks, 'times': sorted(set(times)), 'markers': sorted(markers), 'rig': rig,
                'hold': bool(o.get('hold', False)), 'loop': bool(o.get('loop', False)), 'loopStart': loop_start, 'loopEnd': loop_end,
                'priority': o.get('priority', 'SPECIAL').upper(), 'blendIn': float(o.get('blendIn', 75)), 'blendOut': float(o.get('blendOut', 150))}


def merge(into, frm):
    for b, ch in frm.items():
        into.setdefault(b, {}).update(ch)


def sample_track(tr, t):
    times, values, eases = tr
    n = len(times)
    if t <= times[0] or n == 1:
        return list(values[0])
    if t >= times[-1]:
        return list(values[-1])
    lo = max(i for i in range(n) if times[i] <= t)
    hi = lo + 1
    span = times[hi] - times[lo]
    f = 1.0 if span <= 0 else ease(eases[hi], (t - times[lo]) / span)
    return [a + (b - a) * f for a, b in zip(values[lo], values[hi])]


def local_time(clip, t):
    if clip['loop'] and clip['loopEnd'] > clip['loopStart'] and t > clip['loopEnd']:
        return clip['loopStart'] + (t - clip['loopStart']) % (clip['loopEnd'] - clip['loopStart'])
    return max(0.0, min(t, clip['duration']))


def sample(clip, t):
    """{bone: {'rot': [...deg], 'pos': [...], 'scale': [...]}} at t ms."""
    t = local_time(clip, t)
    out = {}
    for (b, ch), tr in clip['tracks'].items():
        out.setdefault(b, {})[ch] = sample_track(tr, t)
    return out


# ---------------------------------------------------------------- skeleton

def rot_zyx(z, y, x):
    cz, sz, cy, sy, cx, sx = math.cos(z), math.sin(z), math.cos(y), math.sin(y), math.cos(x), math.sin(x)
    rz = np.array([[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1.0]])
    ry = np.array([[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1.0]])
    rx = np.array([[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1.0]])
    return rz @ ry @ rx


def solve(pose):
    """Bone world matrices (model space) for a sampled pose."""
    world = {}
    k = math.pi / 180
    for b in BONES:
        ch = pose.get(b, {})
        rot = ch.get('rot', (0, 0, 0))
        pos = ch.get('pos', (0, 0, 0))
        parent = np.eye(4) if PARENT[b] is None else world[PARENT[b]]
        r = REST[b]
        pr = (0, 0, 0) if PARENT[b] is None else REST[PARENT[b]]
        tm = np.eye(4)
        tm[:3, 3] = [r[0] - pr[0] - pos[0], r[1] - pr[1] - pos[1], r[2] - pr[2] - pos[2]]
        world[b] = parent @ tm @ rot_zyx(rot[2] * k, rot[1] * k, rot[0] * k)
    return world


def joint(world, b, local=(0, 0, 0)):
    """A point of a bone in character space: (right, up, forward) pixels from the spot between the feet at rest."""
    p = world[b] @ np.array([local[0], local[1], local[2], 1.0])
    return (-p[0], 24 - p[1], -p[2])


FACES = [((0, 1, 3, 2), (-1, 0, 0)), ((4, 6, 7, 5), (1, 0, 0)), ((0, 4, 5, 1), (0, -1, 0)), ((2, 3, 7, 6), (0, 1, 0)),
         ((0, 2, 6, 4), (0, 0, -1)), ((1, 5, 7, 3), (0, 0, 1))]


def draw(draw_ctx, world, ox, oy, yaw_deg, scale=5.0, pitch_deg=10):
    """Paints the figure with its feet's rest spot at (ox, oy). yaw 0 looks at the character's front."""
    a, pch = math.radians(yaw_deg), math.radians(pitch_deg)
    # Camera basis: the character faces -z; the camera sits in front of it, turned by yaw around the vertical.
    view = rot_zyx(0, a, 0)
    tilt = rot_zyx(0, 0, -pch)
    polys = []
    boxes = dict(BOX)
    if os.environ.get('BLADE'):
        boxes['blade'] = ((-0.4, 1.2, -15), (0.4, 2.0, -1.5), (225, 225, 235))
    for b, (lo, hi, col) in boxes.items():
        m = tilt @ view @ world['rightHand' if b == 'blade' else b]
        corners = []
        for i in range(8):
            p = np.array([hi[0] if i & 4 else lo[0], hi[1] if i & 2 else lo[1], hi[2] if i & 1 else lo[2], 1.0])
            corners.append(m @ p)
        for idx, nrm in FACES:
            n = m[:3, :3] @ np.array(nrm, dtype=float)
            if n[2] >= 0:
                continue  # faces away (the camera looks down +z from -z)
            depth = sum(corners[i][2] for i in idx) / 4
            shade = 0.55 + 0.45 * max(0.0, -n[2]) * 0.8 + 0.2 * max(0.0, -n[1])
            c = tuple(min(255, int(v * min(1.15, shade))) for v in col)
            polys.append((depth, [(ox + corners[i][0] * scale, oy + (corners[i][1] - 24) * scale) for i in idx], c))
    polys.sort(key=lambda x: -x[0])
    for _, pts, c in polys:
        draw_ctx.polygon(pts, fill=c, outline=(20, 20, 20))


def filmstrip(lib, specs, out, views=(-35, 80), scale=5.0):
    from PIL import Image, ImageDraw
    rows = []
    for spec in specs:
        name, _, ts = spec.partition(':')
        clip = lib.clips[name]
        if ts:
            times = [float(x) for x in ts.split(',')]
        else:
            times = clip['times'] or [0]
            if len(times) > 10:
                step = len(times) / 10.0
                times = [times[int(i * step)] for i in range(10)]
        rows.append((clip, times))
    cw, chh = int(46 * scale), int(52 * scale)
    width = max(len(t) for _, t in rows) * cw + 10
    img = Image.new('RGB', (width, len(rows) * len(views) * chh + 10), (236, 236, 232))
    d = ImageDraw.Draw(img)
    y = 0
    for clip, times in rows:
        for vi, v in enumerate(views):
            for i, t in enumerate(times):
                ox, oy = i * cw + cw // 2, y + chh - int(10 * scale)
                d.line([(ox - cw // 2 + 4, oy), (ox + cw // 2 - 4, oy)], fill=(170, 170, 165))
                draw(d, solve(sample(clip, t)), ox, oy, v, scale)
                if vi == 0:
                    mk = [m for mt, m in clip['markers'] if abs(mt - t) < 1]
                    d.text((i * cw + 4, y + 2), '%s %d' % (clip['name'] if i == 0 else '', t) + (' ' + mk[0] if mk else ''), fill=(0, 0, 0))
            y += chh
    img.save(out)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == '--check':
        lib = Library()
        for e in lib.errors:
            print('ERROR', e)
        print('%d clips, %d poses, %d errors' % (len(lib.clips), len(lib.poses), len(lib.errors)))
        for m in lint(lib, sys.argv[2:] or None):
            print('LINT', m)
        sys.exit(1 if lib.errors else 0)
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    lib = Library()
    filmstrip(lib, sys.argv[2:], sys.argv[1])



def lint(lib, names=None, step=20.0):
    """Looks for what the eye would catch: feet through the floor, and bones that jump between two frames."""
    out = []
    for n in sorted(names or lib.clips):
        c = lib.clips[n]
        if c['rig'] != 'player':
            continue
        end = max(c['duration'], c['times'][-1] if c['times'] else 0)
        low, snap, t, prev = 0.0, (0.0, None, 0), 0.0, None
        while t <= end + 1e-3:
            pose = sample(c, t)
            w = solve(pose)
            rooty = pose.get('root', {}).get('pos', (0, 0, 0))[1]
            for f in ('rightFoot', 'leftFoot'):
                y = joint(w, f, (0, 2, 0))[1]
                if y < low and rooty > -8:
                    low = y
            if prev is not None:
                for b, ch in pose.items():
                    if 'rot' in ch and b in prev and 'rot' in prev[b]:
                        d = max(abs(a - q) for a, q in zip(ch['rot'], prev[b]['rot']))
                        if d > snap[0]:
                            snap = (d, b, t)
            prev = pose
            t += step
        msg = []
        if low < -1.2:
            msg.append('foot %.1f px under the floor' % -low)
        if snap[0] > 150:
            msg.append('%s turns %.0f deg in %d ms at %d' % (snap[1], snap[0], step, snap[2]))
        if msg:
            out.append('%s: %s' % (n, '; '.join(msg)))
    return out


if __name__ == '__main__':
    main()
