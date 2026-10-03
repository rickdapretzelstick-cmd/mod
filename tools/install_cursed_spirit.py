#!/usr/bin/env python3
"""
Installs the Finger Bearer's model: the supplied cursed_spirit.bbmodel goes to models/bb/ (its embedded texture
stripped out, as with rika.bbmodel) and the texture to textures/entity/cursed_spirit.png.

    python3 tools/install_cursed_spirit.py path/to/cursed_spirit.bbmodel

Its own idle animation is converted separately by tools/gen_finger_bearer_anims.py (with the other clips).
"""
import base64
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')


def main():
    d = json.load(open(sys.argv[1]))
    tex = d['textures'][0]
    src = tex.get('source', '')
    if not src.startswith('data:image/png;base64,'):
        sys.exit('no embedded PNG texture in ' + sys.argv[1])
    png = os.path.join(ROOT, 'textures', 'entity', 'cursed_spirit.png')
    os.makedirs(os.path.dirname(png), exist_ok=True)
    open(png, 'wb').write(base64.b64decode(src.split(',', 1)[1]))
    for t in d['textures']:
        t['source'] = ''
    out = os.path.join(ROOT, 'models', 'bb', 'cursed_spirit.bbmodel')
    json.dump(d, open(out, 'w'), separators=(',', ':'))
    print('wrote', png, 'and', out)


if __name__ == '__main__':
    main()
