#!/usr/bin/env python3
"""
Converts the animations inside a Blockbench model into clip JSON for the animation engine (docs/ANIMATION.md).

    python3 tools/bbanim_to_clips.py model.bbmodel <rig> <out_dir> [--prefix rika_]

Each Blockbench animation becomes <out_dir>/<prefix><name>.json with "rig": <rig>. Times are converted to ms;
rotations, positions and scales are copied in Blockbench's own animator convention (the model renderer applies them
the way Blockbench does); catmullrom keys become EASE_IN_OUT, linear LINEAR, step STEP. Loops become loop clips.
"""
import json
import os
import sys


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0  # molang expressions are not evaluated


def main():
    src, rig, out = sys.argv[1], sys.argv[2], sys.argv[3]
    prefix = sys.argv[sys.argv.index('--prefix') + 1] if '--prefix' in sys.argv else rig + '_'
    d = json.load(open(src))
    os.makedirs(out, exist_ok=True)
    ease = {'catmullrom': 'EASE_IN_OUT', 'linear': 'LINEAR', 'step': 'STEP', 'bezier': 'EASE_IN_OUT'}
    chan = {'rotation': 'rot', 'position': 'pos', 'scale': 'scale'}
    for a in d.get('animations', []):
        name = prefix + a['name'].split('.')[-1]
        keys = {}
        for anim in a.get('animators', {}).values():
            bone = anim.get('name')
            for kf in anim.get('keyframes', []):
                c = chan.get(kf.get('channel'))
                if not c:
                    continue
                t = round(float(kf['time']) * 1000)
                p = kf['data_points'][0]
                k = keys.setdefault(t, {'t': t, 'bones': {}})
                b = k['bones'].setdefault(bone, {})
                b[c] = [num(p.get('x')), num(p.get('y')), num(p.get('z'))]
                b['ease'] = ease.get(kf.get('interpolation', 'linear'), 'LINEAR')
        clip = {
            'name': name, 'rig': rig, 'reference': 'converted from %s (%s)' % (os.path.basename(src), a['name']),
            'duration': round(float(a.get('length', 0)) * 1000),
            'priority': 'IDLE',
            'blendIn': 200, 'blendOut': 200,
            'keys': [keys[t] for t in sorted(keys)],
        }
        if a.get('loop') == 'loop':
            clip['loop'] = True
        elif a.get('loop') == 'hold':
            clip['hold'] = True
        path = os.path.join(out, name + '.json')
        json.dump(clip, open(path, 'w'), indent=1)
        print('wrote', path, len(clip['keys']), 'keys')


if __name__ == '__main__':
    main()
