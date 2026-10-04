#!/usr/bin/env python3
"""
Installs a Blockbench model (4.x or 5.x, embedded texture) for the mod's BbModel renderer and clip engine:

    python3 tools/install_bbmodel.py path/to/model.bbmodel <name>

  - models/bb/<name>.bbmodel: Blockbench 5 keeps each group's name, pivot and rotation in a separate "groups" list;
    the loader reads them from the outliner, so they are merged back in. The embedded texture is stripped out to
    textures/entity/<name>.png; the animations are removed from the model file.
  - animations/<name>/<name>_<clip>.json (rig <name>): each animation, as tools/bbanim_to_clips.py converts them.

Used for the common curses (fly_head, school_crawler, school_maw). tools/install_prison_realm.py does the same plus
the Prison Realm's item models.
"""
import base64
import io
import json
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')
EASE = {'catmullrom': 'EASE_IN_OUT', 'linear': 'LINEAR', 'step': 'STEP', 'bezier': 'EASE_IN_OUT'}
CHAN = {'rotation': 'rot', 'position': 'pos', 'scale': 'scale'}


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0


def merge_groups(d):
    groups = {g['uuid']: g for g in d.get('groups', [])}
    if not groups:
        return

    def fix(node):
        if not isinstance(node, dict):
            return node
        g = groups.get(node.get('uuid'), {})
        return {'name': g.get('name', node.get('name', 'group')), 'origin': g.get('origin', node.get('origin', [0, 0, 0])),
                'rotation': g.get('rotation', node.get('rotation', [0, 0, 0])), 'uuid': node.get('uuid'), 'export': True,
                'isOpen': False, 'children': [fix(c) for c in node.get('children', [])]}

    d['outliner'] = [fix(n) for n in d['outliner']]
    d.pop('groups', None)
    d['meta']['format_version'] = '4.10'


def clips(d, src, name):
    out = os.path.join(ROOT, 'animations', name)
    os.makedirs(out, exist_ok=True)
    for a in d.get('animations', []):
        clip_name = name + '_' + a['name'].split('.')[-1]
        keys = {}
        for anim in a.get('animators', {}).values():
            bone = anim.get('name')
            for kf in anim.get('keyframes', []):
                c = CHAN.get(kf.get('channel'))
                if not c:
                    continue
                t = round(float(kf['time']) * 1000)
                p = kf['data_points'][0]
                k = keys.setdefault(t, {'t': t, 'bones': {}})
                b = k['bones'].setdefault(bone, {})
                b[c] = [num(p.get('x')), num(p.get('y')), num(p.get('z'))]
                b['ease'] = EASE.get(kf.get('interpolation', 'linear'), 'LINEAR')
        clip = {'name': clip_name, 'rig': name, 'reference': 'converted from %s (%s)' % (os.path.basename(src), a['name']),
                'duration': round(float(a.get('length', 0)) * 1000), 'priority': priority(clip_name), 'blendIn': 80, 'blendOut': 80,
                'keys': [keys[t] for t in sorted(keys)]}
        if a.get('loop') == 'loop':
            clip['loop'] = True
        elif a.get('loop') == 'hold' or clip_name.endswith('_death'):
            clip['hold'] = True
        with open(os.path.join(out, clip_name + '.json'), 'w') as f:
            json.dump(clip, f, indent=1)
        print('wrote animations/%s/%s.json' % (name, clip_name))


# The usual creature controller order: death > hurt > attack > move > idle (by the clip's name suffix).
PRIORITY = {'idle': 'IDLE', 'move': 'MOVEMENT', 'walk': 'MOVEMENT', 'attack': 'ATTACK', 'hurt': 'SPECIAL', 'death': 'RAGDOLL'}


def priority(clip_name):
    return PRIORITY.get(clip_name.rsplit('_', 1)[-1], 'ATTACK')


def main():
    src, name = sys.argv[1], sys.argv[2]
    d = json.load(open(src))
    clips(d, src, name)
    merge_groups(d)
    t = d['textures'][0].get('source', '')
    if not t.startswith('data:image/png;base64,'):
        sys.exit('no embedded PNG texture in ' + src)
    os.makedirs(os.path.join(ROOT, 'textures', 'entity'), exist_ok=True)
    Image.open(io.BytesIO(base64.b64decode(t.split(',', 1)[1]))).save(os.path.join(ROOT, 'textures', 'entity', name + '.png'))
    for tx in d['textures']:
        tx['source'] = ''
    d.pop('animations', None)
    with open(os.path.join(ROOT, 'models', 'bb', name + '.bbmodel'), 'w') as f:
        json.dump(d, f, separators=(',', ':'))
    print('wrote models/bb/%s.bbmodel and textures/entity/%s.png' % (name, name))


if __name__ == '__main__':
    main()
