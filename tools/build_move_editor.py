#!/usr/bin/env python3
"""
Builds the move editor: one web page to animate the mod's player rig and make whole moves on it -- the clip (keys on
the same bones, easings and rotation order the game uses: client.anim.AnimLibrary), plus a timeline of hitboxes,
grabs, effects, sounds, camera, motion, statuses and logic that the game runs (core.move.MoveAbility).

    python3 tools/build_move_editor.py            # writes build/move_editor.html

The page opens any existing player clip (resolved as the viewer resolves it: poses, mirrors, frames, aliases) and saves:
  - a move file      -> <game>/config/jjk/moves/<id>.json, then /jjkmove reload and /jjkmove bind <key> <id>
  - a plain clip     -> src/main/resources/assets/jjk/animations/<group>/<name>.json (the existing clip format)
The template is tools/move_editor/template.html; this fills its /*CLIPS*/ /*POSES*/ /*FX*/ /*SOUNDS*/ /*STATUSES*/
/*TAGS*/ markers from the mod's sources. The format is in tools/move_editor/FORMAT.md.

Published as the "JJK Move Editor" artifact: https://claude.ai/artifact/Qtzgsb6rivaCDHA7K3Z3Jg (republish build/move_editor.html there).
"""
import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_anim_viewer as V

ROOT = V.ROOT
SRC = os.path.join(ROOT, 'src')
TEMPLATE = os.path.join(ROOT, 'tools', 'move_editor', 'template.html')
OUT = os.path.join(ROOT, 'build', 'move_editor.html')


def flat_clip(d, poses):
    """A clip's keys with named poses merged in: [{t, ease, bones: {bone: {rot, pos}}}] in ms."""
    fps = d.get('fps', 50)
    frame = 1000.0 / fps
    ref = d.get('refStart', 0)
    keys = []
    for k in d.get('keys', []):
        t = k['t'] if 't' in k else (k['f'] - ref) * frame
        vals = {}
        names = k.get('pose', [])
        for p in [names] if isinstance(names, str) else names:
            m = p.startswith('!') or p.startswith('mirror:') or p.endswith('@mirror')
            base = poses.get(re.sub(r'^(!|mirror:)|@mirror$', '', p))
            if base:
                vals.update(V.mirror(base) if m else base)
        vals.update(V.bone_values(k.get('bones')))
        bones = {}
        for b, v in vals.items():
            e = {c: [round(x, 2) for x in v[c]] for c in ('rot', 'pos') if c in v}
            if 'ease' in v:
                e['ease'] = V.ease_name(v['ease'])
            bones[b] = e
        keys.append({'t': round(t, 1), 'ease': V.ease_name(k.get('ease')), 'bones': bones})
    keys.sort(key=lambda k: k['t'])
    dur = d.get('duration') or (keys[-1]['t'] if keys else 500)
    return {'duration': round(dur), 'keys': keys}


def clips():
    poses = V.load_poses()
    out = []
    for f in sorted(glob.glob(os.path.join(V.ANIMS, '*', '*.json'))):
        group = os.path.basename(os.path.dirname(f))
        if group == 'poses':
            continue
        try:
            d = json.load(open(f))
        except Exception:
            continue
        for c in d.get('clips', [d]):
            if c.get('rig', 'player') != 'player' or 'keys' not in c:
                continue
            fc = flat_clip(c, poses)
            out.append({'id': group + '/' + c.get('name', os.path.basename(f)[:-5]), 'group': group,
                        'name': c.get('name', os.path.basename(f)[:-5]), 'priority': c.get('priority', 'SPECIAL'),
                        'hold': bool(c.get('hold')), 'blendIn': c.get('blendIn', 75), 'blendOut': c.get('blendOut', 150), **fc})
    return out, poses


def fx_ids():
    ids = set()
    for f in glob.glob(os.path.join(SRC, 'client', 'java', 'dev', 'rick', 'jjk', 'client', 'fx', '*.java')):
        ids.update(re.findall(r'case "([a-z0-9_]+)"', open(f).read()))
    return sorted(ids)


def sounds():
    s = open(os.path.join(SRC, 'main', 'java', 'dev', 'rick', 'jjk', 'registry', 'ModSounds.java')).read()
    block = s[s.index('NAMES = {'):s.index('};', s.index('NAMES = {'))]
    block = re.sub(r'//[^\n]*', '', block)
    return sorted(set(re.findall(r'"([a-z0-9_]+)"', block)))


def statuses():
    s = open(os.path.join(SRC, 'main', 'java', 'dev', 'rick', 'jjk', 'core', 'combat', 'CombatStatus.java')).read()
    return re.findall(r'builder\("([a-z0-9_]+)"\)', s)


def tags():
    s = open(os.path.join(SRC, 'main', 'java', 'dev', 'rick', 'jjk', 'core', 'combat', 'AttackTag.java')).read()
    return re.findall(r'of\("([a-z0-9_]+)"\)', s)


def main():
    cl, poses = clips()
    page = open(TEMPLATE).read()
    for marker, data in (('CLIPS', cl), ('POSES', poses), ('FX', fx_ids()), ('SOUNDS', sounds()), ('STATUSES', statuses()), ('TAGS', tags())):
        page = page.replace(f'/*{marker}*/[]', json.dumps(data, separators=(',', ':')), 1).replace(f'/*{marker}*/{{}}', json.dumps(data, separators=(',', ':')), 1)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, 'w').write(page)
    print(f'{OUT}: {len(cl)} clips, {len(poses)} poses, {len(page) // 1024} KB')


if __name__ == '__main__':
    main()
