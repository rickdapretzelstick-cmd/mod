#!/usr/bin/env python3
"""
Installs the Grade 1 guardians' models from the Blockbench pack ("Grades 1-4 voxel rebuild": four bodies, three head
variants each, every .bbmodel with its texture embedded):

    python3 tools/install_guardians.py path/to/Grades-1-4-Rebuilt/models

Each pack body is one of the contract guardians (progression.guardian.Guardians), its reference_a/b/c the guardian's
variants 0/1/2:

    grade_4 -> guardian_claw_hunter      (gaunt, long claws, a dark fringe)
    grade_3 -> guardian_pale_stalker     (pale hair over a split face)
    grade_2 -> guardian_cyclops_bruiser  (one eye, heavy)
    grade_1 -> guardian_veiled_reacher   (veiled, long arms)

Writes, per variant:
  - models/bb/<guardian>_<v>.bbmodel: Blockbench 5 keeps each group's name, pivot and rotation in a separate "groups"
    list; the mod's loader reads them from the outliner, so they are merged back in. Only what the loader reads is
    kept (a compact copy: the pack's files carry per-element metadata the renderer never uses).
  - textures/entity/<guardian>_<v>.png: the embedded texture.

The clips are built separately (tools/gen_guardian_anims.py), on the bones these models share.
"""
import base64
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'jjk')
BODIES = {'grade_4': 'guardian_claw_hunter', 'grade_3': 'guardian_pale_stalker', 'grade_2': 'guardian_cyclops_bruiser',
          'grade_1': 'guardian_veiled_reacher'}
VARIANTS = {'reference_a': 0, 'reference_b': 1, 'reference_c': 2}


def r(v):
    return [round(float(x), 4) for x in v]


def compact_element(e):
    out = {'uuid': e['uuid'], 'from': r(e['from']), 'to': r(e['to'])}
    if any(e.get('origin', [0, 0, 0])):
        out['origin'] = r(e['origin'])
    if any(e.get('rotation', [0, 0, 0])):
        out['rotation'] = r(e['rotation'])
    if e.get('inflate'):
        out['inflate'] = round(float(e['inflate']), 4)
    if e.get('type', 'cube') != 'cube':
        out['type'] = e['type']
    if e.get('visibility') is False:
        out['visibility'] = False
    faces = {}
    for name, f in (e.get('faces') or {}).items():
        if 'uv' not in f or f.get('texture') is None:
            continue
        g = {'uv': r(f['uv'])}
        if f.get('rotation'):
            g['rotation'] = f['rotation']
        faces[name] = g
    out['faces'] = faces
    return out


def compact_outliner(node, groups):
    if isinstance(node, str):
        return node
    g = groups.get(node.get('uuid'), node)
    out = {'name': g['name'], 'origin': r(g.get('origin', [0, 0, 0]))}
    if any(g.get('rotation', [0, 0, 0])):
        out['rotation'] = r(g['rotation'])
    out['children'] = [compact_outliner(c, groups) for c in node.get('children', [])]
    return out


def install(path, name):
    d = json.load(open(path))
    tex = d['textures'][0]
    src = tex.get('source', '')
    if not src.startswith('data:image/png;base64,'):
        sys.exit('no embedded PNG texture in ' + path)
    png = os.path.join(ROOT, 'textures', 'entity', name + '.png')
    os.makedirs(os.path.dirname(png), exist_ok=True)
    open(png, 'wb').write(base64.b64decode(src.split(',', 1)[1]))
    groups = {g['uuid']: g for g in d.get('groups', [])}
    # Short ids in place of the elements' uuids (the loader only matches them up).
    ids = {e['uuid']: format(i, 'x') for i, e in enumerate(d['elements'])}
    elements = [compact_element(e) for e in d['elements']]
    for e in elements:
        e['uuid'] = ids[e['uuid']]

    def short(node):
        if isinstance(node, str):
            return ids.get(node, node)
        node['children'] = [short(c) for c in node['children']]
        return node

    out = {
        'meta': {'format_version': '5.0', 'model_format': 'free', 'source': os.path.basename(path)},
        'name': name,
        'resolution': d['resolution'],
        'elements': elements,
        'outliner': [short(compact_outliner(n, groups)) for n in d['outliner']],
    }
    model = os.path.join(ROOT, 'models', 'bb', name + '.bbmodel')
    os.makedirs(os.path.dirname(model), exist_ok=True)
    with open(model, 'w') as fh:
        json.dump(out, fh, separators=(',', ':'))
    print('wrote', name, len(out['elements']), 'cubes,', os.path.getsize(model) // 1024, 'KB')


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    folder = sys.argv[1]
    done = 0
    for f in sorted(os.listdir(folder)):
        if not f.endswith('.bbmodel'):
            continue
        stem = f[:-len('.bbmodel')]
        body = next((v for k, v in BODIES.items() if stem.startswith(k + '_')), None)
        variant = next((v for k, v in VARIANTS.items() if stem.endswith(k)), None)
        if body is None or variant is None:
            print('skipped', f)
            continue
        install(os.path.join(folder, f), body + '_' + str(variant))
        done += 1
    print(done, 'models installed')


if __name__ == '__main__':
    main()
