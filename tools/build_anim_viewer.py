#!/usr/bin/env python3
"""
Builds the animation viewer: a single web page that plays every player clip of the mod on the mod's own rig (the same
bones, pivots, elbow/knee splits and rotation order as client.anim.rig.Rig), with a clip list, play/pause, scrub, speed
and an orbiting camera, so animation work can be judged in real time without starting the game.

    python3 tools/build_anim_viewer.py            # writes build/anim_viewer.html

Published as the "JJK Animation Viewer" artifact: https://claude.ai/artifact/JK8NYCyfE9kRSoXrzX5zCY
After changing clips, re-run this and republish build/anim_viewer.html to that URL (Artifact publish with url=...).

Reads assets/jjk/animations/*/*.json (player-rig clips only: entity rigs are skipped) and poses/*.json, resolving what
the game's loader resolves: named poses (and their mirrored "!name" form), frame keys ("f" with fps/refStart), bone
aliases, bare-array bone values (rotation). Each clip becomes per-bone tracks of [ms, [x, y, z], ease], as AnimLibrary
compiles them (a bone's value between its own keys, eased by the later key's ease, held before the first and after
the last). The page template is tools/anim_viewer/template.html; the data replaces its /*CLIPS*/ marker.
"""
import glob
import json
import os
import re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ANIMS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'jjk', 'animations')
TEMPLATE = os.path.join(ROOT, 'tools', 'anim_viewer', 'template.html')
OUT = os.path.join(ROOT, 'build', 'anim_viewer.html')

BONES = ['root', 'hips', 'chest', 'neck', 'head', 'rightArm', 'rightForearm', 'rightHand', 'leftArm', 'leftForearm', 'leftHand',
         'rightThigh', 'rightShin', 'rightFoot', 'leftThigh', 'leftShin', 'leftFoot']
ALIAS = {}
for b in BONES:
    ALIAS[b.lower()] = b
for b, names in {'hips': 'waist pelvis lowerTorso lower_torso', 'chest': 'torso body upperTorso upper_torso spine',
                 'rightArm': 'rightUpperArm right_upper_arm mainArm right_arm', 'rightForearm': 'rightLowerArm right_lower_arm rightElbow mainForearm',
                 'rightHand': 'rightFist rightWrist mainHand', 'leftArm': 'leftUpperArm left_upper_arm offArm left_arm',
                 'leftForearm': 'leftLowerArm left_lower_arm leftElbow offForearm', 'leftHand': 'leftFist leftWrist offHand',
                 'rightThigh': 'rightLeg rightUpperLeg right_upper_leg right_leg', 'rightShin': 'rightLowerLeg right_lower_leg rightKnee rightCalf',
                 'rightFoot': 'rightAnkle', 'leftThigh': 'leftLeg leftUpperLeg left_upper_leg left_leg',
                 'leftShin': 'leftLowerLeg left_lower_leg leftKnee leftCalf', 'leftFoot': 'leftAnkle'}.items():
    for n in names.split():
        ALIAS[n.lower()] = b
EASES = {'LINEAR', 'EASE_IN', 'EASE_OUT', 'EASE_IN_OUT', 'STEP', 'OVERSHOOT', 'SPRING'}


def ease_name(s, default='EASE_IN_OUT'):
    if not s:
        return default
    s = s.upper().replace('-', '_')
    s = {'IN': 'EASE_IN', 'OUT': 'EASE_OUT', 'IN_OUT': 'EASE_IN_OUT', 'SMOOTH': 'EASE_IN_OUT'}.get(s, s)
    return s if s in EASES else default


def bone_values(bones):
    """{bone: {'rot': [...], 'pos': [...]}} from a clip's or pose's bones object (bare arrays are rotations)."""
    out = {}
    for name, v in (bones or {}).items():
        b = ALIAS.get(name.lower())
        if not b:
            continue
        if isinstance(v, list):
            out[b] = {'rot': v}
        elif isinstance(v, dict):
            out[b] = {k: v[k] for k in ('rot', 'pos') if k in v}
            if 'ease' in v:
                out[b]['ease'] = v['ease']
    return out


def mirror(vals):
    out = {}
    for b, v in vals.items():
        n = b.replace('right', '\0').replace('left', 'right').replace('\0', 'left')
        m = {}
        if 'rot' in v:
            r = v['rot']
            m['rot'] = [r[0], -r[1], -r[2]]
        if 'pos' in v:
            p = v['pos']
            m['pos'] = [-p[0], p[1], p[2]]
        out[n] = m
    return out


def load_poses():
    poses = {}
    for f in glob.glob(os.path.join(ANIMS, 'poses', '*.json')):
        d = json.load(open(f))
        for name, p in d.get('poses', {}).items():
            poses[name] = bone_values(p.get('bones'))
    return poses


def compile_clip(d, poses):
    fps = d.get('fps', 50)
    frame = 1000.0 / fps
    ref = d.get('refStart', 0)
    tracks = {}
    for k in d.get('keys', []):
        t = k['t'] if 't' in k else (k['f'] - ref) * frame
        ease = ease_name(k.get('ease'))
        vals = {}
        names = k.get('pose', [])
        for p in [names] if isinstance(names, str) else names:
            m = p.startswith('!') or p.startswith('mirror:')
            base = poses.get(re.sub(r'^(!|mirror:)', '', p))
            if base:
                vals.update(mirror(base) if m else base)
        vals.update(bone_values(k.get('bones')))
        for b, v in vals.items():
            for ch in ('rot', 'pos'):
                if ch in v:
                    tracks.setdefault(b, {}).setdefault(ch, []).append([round(t, 1), [round(x, 2) for x in v[ch]], ease_name(v.get('ease'), ease)])
    for b in tracks.values():
        for ch in b.values():
            ch.sort(key=lambda e: e[0])
    return tracks


def main():
    poses = load_poses()
    clips = []
    for f in sorted(glob.glob(os.path.join(ANIMS, '*', '*.json'))):
        group = os.path.basename(os.path.dirname(f))
        if group == 'poses':
            continue
        try:
            d = json.load(open(f))
        except Exception:
            continue
        if d.get('rig', 'player') != 'player' or 'keys' not in d:
            continue
        clips.append({'name': d.get('name', os.path.basename(f)[:-5]), 'group': group, 'duration': d.get('duration', 500),
                      'hold': bool(d.get('hold')), 'priority': d.get('priority', ''), 'authored': bool(d.get('authored')),
                      'tracks': compile_clip(d, poses)})
    html = open(TEMPLATE).read().replace('/*CLIPS*/[]', json.dumps(clips, separators=(',', ':')))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, 'w').write(html)
    print(f'{OUT}: {len(clips)} player clips, {len(html) // 1024} KB')


if __name__ == '__main__':
    main()
