#!/usr/bin/env python3
"""
Ports an Emotecraft-format player animation (the format of Emotecraft emotes, Better Combat's player_animations and
PlayerAnimator's legacy JSON) into this mod's clip format (assets/jjk/animations/<group>/<name>.json, read by
client.anim.AnimLibrary), as a starting point to rework, not a finished clip:

    python3 tools/import_emote.py <emote.json> <group> <name> [--priority ATTACK] [--speed 1.0] [--mirror]

Mapping: head -> head, torso -> chest (its offsets -> root), rightArm/leftArm -> the arms (their bend -> the forearms),
rightLeg/leftLeg -> the thighs (their bend -> the shins). Emotecraft keys each axis separately at its own ticks; the
clip keys every bone's full rotation at every tick any axis moved, sampling each axis's own curve between its keys.
Radians are converted to degrees (unless the emote says "degrees": true); ticks become milliseconds (x50 / speed).
"""
import json
import math
import sys

BONES = {'head': 'head', 'torso': 'chest', 'body': 'chest', 'rightArm': 'rightArm', 'leftArm': 'leftArm', 'rightLeg': 'rightThigh',
         'leftLeg': 'leftThigh'}
BEND = {'rightArm': 'rightForearm', 'leftArm': 'leftForearm', 'rightLeg': 'rightShin', 'leftLeg': 'leftShin'}
ROT = {'pitch': 0, 'yaw': 1, 'roll': 2}
POS = {'x': 0, 'y': 1, 'z': 2}
EASE = {'LINEAR': None, 'CONSTANT': None}


def ease_of(e):
    e = (e or 'LINEAR').upper()
    if 'INOUT' in e:
        return 'EASE_IN_OUT'
    if e.startswith('EASEIN'):
        return 'EASE_IN'
    if e.startswith('EASEOUT'):
        return 'EASE_OUT'
    return None


def sample(curve, t):
    """Value of one axis's keyframes at tick t (linear between its keys, held at the ends)."""
    if not curve:
        return 0.0
    curve = sorted(curve)
    if t <= curve[0][0]:
        return curve[0][1]
    for (t0, v0), (t1, v1) in zip(curve, curve[1:]):
        if t0 <= t <= t1:
            return v0 if t1 == t0 else v0 + (v1 - v0) * (t - t0) / (t1 - t0)
    return curve[-1][1]


def convert(src, name, priority='ATTACK', speed=1.0, mirror=False):
    d = json.load(open(src))
    e = d.get('emote', d)
    degrees = str(e.get('degrees', False)).lower() == 'true'
    k = 1.0 if degrees else 180 / math.pi
    curves = {}  # (bone, kind, axis) -> [(tick, value)]
    eases = {}
    for m in e['moves']:
        t = m['tick']
        for part, axes in m.items():
            if not isinstance(axes, dict):
                continue
            if mirror:
                part = {'rightArm': 'leftArm', 'leftArm': 'rightArm', 'rightLeg': 'leftLeg', 'leftLeg': 'rightLeg'}.get(part, part)
            for ax, v in axes.items():
                if ax in ROT:
                    val = v * k * (-1 if mirror and ax in ('yaw', 'roll') else 1)
                    curves.setdefault((part, 'rot', ROT[ax]), []).append((t, val))
                elif ax in POS:
                    curves.setdefault((part, 'pos', POS[ax]), []).append((t, v * (-1 if mirror and ax == 'x' else 1)))
                elif ax == 'bend':
                    curves.setdefault((part, 'bend', 0), []).append((t, v * k))
            eases[t] = ease_of(m.get('easing'))
    ticks = sorted({t for c in curves.values() for t, _ in c} | {0})
    end = max(ticks + [int(e.get('stopTick', 0)), int(e.get('endTick', 0))])
    keys = []
    for t in ticks:
        bones = {}
        for part in {p for p, _, _ in curves}:
            if part not in BONES:
                continue
            rot = [round(sample(curves.get((part, 'rot', i)), t), 2) for i in range(3)]
            if any(abs(v) > 1e-3 for v in rot):
                bones.setdefault(BONES[part], {})['rot'] = rot
            if part in ('torso', 'body'):
                pos = [round(sample(curves.get((part, 'pos', i)), t), 2) for i in range(3)]
                if any(abs(v) > 1e-3 for v in pos):
                    bones['root'] = {'pos': pos}
            bend = sample(curves.get((part, 'bend', 0)), t)
            if part in BEND and abs(bend) > 1e-3:
                # The bend folds the lower limb forward (arms) / back (legs) about its joint.
                bones[BEND[part]] = {'rot': [round(-abs(bend) if 'Arm' in part else abs(bend), 2), 0, 0]}
        key = {'t': round(t * 50 / speed), 'bones': bones}
        if eases.get(t):
            key['ease'] = eases[t]
        keys.append(key)
    return {'name': name, 'duration': round(end * 50 / speed), 'blendIn': 50, 'blendOut': 150, 'priority': priority,
            'source': f'ported from {src.split("/")[-1]} ({d.get("author", "unknown author")}); rework before shipping', 'keys': keys}


if __name__ == '__main__':
    a = sys.argv[1:]
    if len(a) < 3:
        print(__doc__)
        sys.exit(1)
    pr = a[a.index('--priority') + 1] if '--priority' in a else 'ATTACK'
    sp = float(a[a.index('--speed') + 1]) if '--speed' in a else 1.0
    clip = convert(a[0], a[2], pr, sp, '--mirror' in a)
    out = f'src/main/resources/assets/jjk/animations/{a[1]}/{a[2]}.json'
    json.dump(clip, open(out, 'w'), indent=1)
    print(f'{out}: {len(clip["keys"])} keys, {clip["duration"]} ms')
