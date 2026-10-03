#!/usr/bin/env python3
"""
Installs the Prison Realm asset pack (prison_realm_pack.zip, extracted):

    python3 tools/install_prison_realm.py path/to/prison_realm_pack

  - prison_realm.bbmodel -> models/bb/prison_realm.bbmodel. The pack is a Blockbench 5 file, which keeps each group's
    name, pivot and rotation in a separate "groups" list and only uuids in the outliner; the mod's BbModel loader reads
    them from the outliner (the 4.x layout of rika.bbmodel), so they are merged back in. The embedded texture is
    stripped out to textures/entity/prison_realm.png.
  - Its five animations -> animations/prison_realm/prison_realm_<idle|open|open_idle|seal|full_sequence>.json (rig
    prison_realm), the same conversion as tools/bbanim_to_clips.py.
  - The static Java item model (namespace prisonrealm -> jjk) -> models/item/prison_realm.json and, re-pointed at a
    drained grey copy of the texture, models/item/dormant_prison_realm.json; items/<name>.json for both.
"""
import base64
import copy
import io
import json
import os
import sys

from PIL import Image, ImageEnhance

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')
EASE = {'catmullrom': 'EASE_IN_OUT', 'linear': 'LINEAR', 'step': 'STEP', 'bezier': 'EASE_IN_OUT'}
CHAN = {'rotation': 'rot', 'position': 'pos', 'scale': 'scale'}


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0


def merge_groups(d):
    """Blockbench 5 -> 4.x outliner: every outliner group object gets its group's name, origin and rotation."""
    groups = {g['uuid']: g for g in d.get('groups', [])}

    def fix(node):
        if not isinstance(node, dict):
            return node
        g = groups.get(node.get('uuid'), {})
        out = {'name': g.get('name', node.get('name', 'group')), 'origin': g.get('origin', [0, 0, 0]),
               'rotation': g.get('rotation', [0, 0, 0]), 'uuid': node.get('uuid'), 'export': True, 'isOpen': False}
        out['children'] = [fix(c) for c in node.get('children', [])]
        return out

    d['outliner'] = [fix(n) for n in d['outliner']]
    d.pop('groups', None)
    d['meta']['format_version'] = '4.10'


def write(path, data, compact=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        if compact:
            json.dump(data, f, separators=(',', ':'))
        else:
            json.dump(data, f, indent=1)
    print('wrote', os.path.relpath(path, ROOT))


def clips(d, src):
    out = os.path.join(ROOT, 'animations', 'prison_realm')
    for a in d.get('animations', []):
        name = 'prison_realm_' + a['name'].split('.')[-1]
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
        clip = {'name': name, 'rig': 'prison_realm', 'reference': 'converted from %s (%s)' % (os.path.basename(src), a['name']),
                'duration': round(float(a.get('length', 0)) * 1000), 'priority': 'IDLE', 'blendIn': 0, 'blendOut': 0,
                'keys': [keys[t] for t in sorted(keys)]}
        if a.get('loop') == 'loop':
            clip['loop'] = True
        elif a.get('loop') == 'hold':
            clip['hold'] = True
        write(os.path.join(out, name + '.json'), clip)


def items(pack):
    base = os.path.join(pack, 'assets', 'prisonrealm')
    tex = Image.open(os.path.join(base, 'textures', 'item', 'prison_realm.png')).convert('RGBA')
    tdir = os.path.join(ROOT, 'textures', 'item')
    os.makedirs(tdir, exist_ok=True)
    tex.save(os.path.join(tdir, 'prison_realm.png'))
    # The dormant cube: no cursed energy in it yet, so drained and grey.
    rgb = ImageEnhance.Brightness(ImageEnhance.Color(tex.convert('RGB')).enhance(0.15)).enhance(0.8).convert('RGBA')
    rgb.putalpha(tex.getchannel('A'))
    rgb.save(os.path.join(tdir, 'dormant_prison_realm.png'))
    print('wrote textures/item/prison_realm.png and dormant_prison_realm.png')
    model = json.load(open(os.path.join(base, 'models', 'item', 'prison_realm.json')))
    for name in ('prison_realm', 'dormant_prison_realm'):
        m = copy.deepcopy(model)
        m['textures'] = {'0': 'jjk:item/' + name, 'particle': 'jjk:item/' + name}
        write(os.path.join(ROOT, 'models', 'item', name + '.json'), m)
        write(os.path.join(ROOT, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': 'jjk:item/' + name}})


def main():
    pack = sys.argv[1]
    src = os.path.join(pack, 'prison_realm.bbmodel')
    d = json.load(open(src))
    clips(d, src)
    merge_groups(d)
    t = d['textures'][0].get('source', '')
    if not t.startswith('data:image/png;base64,'):
        sys.exit('no embedded PNG texture in ' + src)
    png = Image.open(io.BytesIO(base64.b64decode(t.split(',', 1)[1])))
    os.makedirs(os.path.join(ROOT, 'textures', 'entity'), exist_ok=True)
    png.save(os.path.join(ROOT, 'textures', 'entity', 'prison_realm.png'))
    print('wrote textures/entity/prison_realm.png')
    for tx in d['textures']:
        tx['source'] = ''
    d.pop('animations', None)
    write(os.path.join(ROOT, 'models', 'bb', 'prison_realm.bbmodel'), d, compact=True)
    items(pack)


if __name__ == '__main__':
    main()
