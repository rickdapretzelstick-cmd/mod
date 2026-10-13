#!/usr/bin/env python3
"""Key-pose fixer: the page where a person corrects the key poses the fitter got wrong, and the step that turns the
corrected keys into the move's clip.

  build: python3 tools/keypose_editor.py build <video> <fit dir> --palette P --name NAME [--range a:b]
         <fit dir> holds the key-pose fit (r6_fit_torch.py --keyposes ... --out <fit dir>: params.json, keyposes.json).
         Writes <fit dir>/editor.html: each key's footage (a crop around the character), its colour map and fitted pose,
         and every frame's footage for the playback preview. Publish that page; fixes are saved in its database
         under moves/<NAME>/fixes/f<frame> as {frame, params}.
  apply: python3 tools/keypose_editor.py apply <fit dir> <fixes.json> [--clip-out PATH]
         fixes.json: [{frame, params}] (read back from the page's database). Each fixed key takes its corrected pose,
         every frame between keys is eased again (smoothstep), and params_fixed.json plus a mod clip are written.
"""
import argparse
import base64
import io
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F  # noqa: E402
import r6_rects as RR  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
TEMPLATE = os.path.join(HERE, 'keypose_editor', 'template.html')


def _jpeg(img, quality=80):
    buf = io.BytesIO()
    img.save(buf, 'JPEG', quality=quality)
    return 'data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode()


def _crop_box(p, margin=0.75, pad=20):
    """A square around the posed boxes (footage px): (x0, y0, side)."""
    pts = [pt for poly, _, _ in F.faces(p, F.DEFAULT_LIGHT) for pt in poly]
    xs, ys = [x for x, _ in pts], [y for _, y in pts]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    h = max(max(xs) - min(xs), max(ys) - min(ys)) * margin + pad
    return cx - h, cy - h, 2 * h


def build(a):
    pal = json.load(open(a.palette))
    F.use_palette(pal)
    P = json.load(open(os.path.join(a.fit, 'params.json')))
    K = json.load(open(os.path.join(a.fit, 'keyposes.json')))
    params = {int(i): np.array(v) for i, v in P['frames'].items()}
    idx = sorted(params)
    keys = [int(k) for k in K['keys']]
    frames, fps = F.frames_of(a.video, None, every=1, width=a.width)
    STILL, MAP, PLAY = 420, 120, 200
    key_out = []
    for k, i in enumerate(keys):
        p = params[i]
        x0, y0, side = _crop_box(p)
        box = (int(round(x0)), int(round(y0)), int(round(x0 + side)), int(round(y0 + side)))
        img = Image.fromarray(frames[i])
        still = img.crop(box).resize((STILL, STILL), Image.BICUBIC)
        cls = (F.classify(frames[i], pal) // 1000).astype(np.uint8)
        if F.FACE > 0:
            cls[cls == F.FACE] = F.HEAD
        # Only the character's own cluster of colours (the dummy's red hair or a health bar is not body), as the fit.
        body_cls = sorted({F.CLASS_OF[part] for _, _, _, part in F.PARTS})
        cls[np.isin(cls, body_cls) & ~RR.body_cluster(cls, {c: 1 for c in body_cls})] = 0
        cmap = np.asarray(Image.fromarray(cls).crop(box).resize((MAP, MAP), Image.NEAREST), np.uint8)
        key_out.append({'frame': i, 'time': round(i / fps, 3), 'match': K['agreement'][k],
                        'crop': [box[0], box[1], (box[2] - box[0])], 'img': _jpeg(still),
                        'cmap': base64.b64encode(cmap.tobytes()).decode(), 'params': [round(float(x), 3) for x in p]})
    play = []
    for i in idx:
        x0, y0, side = _crop_box(params[i])
        box = (int(round(x0)), int(round(y0)), int(round(x0 + side)), int(round(y0 + side)))
        play.append({'frame': i, 'crop': [box[0], box[1], box[2] - box[0]],
                     'img': _jpeg(Image.fromarray(frames[i]).crop(box).resize((PLAY, PLAY), Image.BICUBIC), 70)})
    data = {
        'name': a.name, 'fps': fps, 'frames': idx, 'keys': key_out, 'play': play, 'mapSize': MAP, 'stillSize': STILL,
        'playSize': PLAY, 'unknown': F.UNKNOWN,
        'classes': {part: F.CLASS_OF[part] for _, _, _, part in F.PARTS},
        'show': {str(c): '#%02x%02x%02x' % rgb for c, rgb in F.SHOW.items()},
        'joints': [[b, ax] for b, ax in F.JOINTS], 'limits': [list(F.LIMITS[j]) for j in F.JOINTS],
    }
    html = open(TEMPLATE).read().replace('/*DATA*/null', json.dumps(data, separators=(',', ':')))
    out = os.path.join(a.fit, 'editor.html')
    open(out, 'w').write(html)
    print(f'{out}: {len(keys)} key poses, {len(idx)} frames, {os.path.getsize(out) // 1024} KB')


def apply(a):
    P = json.load(open(os.path.join(a.fit, 'params.json')))
    K = json.load(open(os.path.join(a.fit, 'keyposes.json')))
    params = {int(i): np.array(v, float) for i, v in P['frames'].items()}
    idx = sorted(params)
    keys = [int(k) for k in K['keys']]
    fixes = {int(f['frame']): np.array(f['params'], float) for f in json.load(open(a.fixes))}
    for i, p in fixes.items():
        if i in params:
            params[i] = p
    nc = len(F.CAM)
    out = {}
    for i in idx:
        if i in keys:
            out[i] = params[i]
            continue
        b = int(np.searchsorted(keys, i))
        ka, kb = keys[max(0, b - 1)], keys[min(len(keys) - 1, b)]
        f = 0.0 if ka == kb else (i - ka) / (kb - ka)
        f = f * f * (3 - 2 * f)
        d = params[kb] - params[ka]
        d[nc:] = (d[nc:] + 180) % 360 - 180
        out[i] = params[ka] + f * d
    json.dump({'fps': P['fps'], 'frames': {i: [round(float(x), 3) for x in out[i]] for i in idx}, 'params': P['params'],
               'fixed': sorted(fixes)}, open(os.path.join(a.fit, 'params_fixed.json'), 'w'))
    times = np.array([(i - idx[0]) * 1000.0 / P['fps'] for i in idx])
    clip_path = a.clip_out or os.path.join(a.fit, 'clip_fixed.json')
    path, n = F.write_clip(a.target, times, np.stack([out[i] for i in idx]), 3.0, False, os.path.basename(a.fit), clip_path)
    print(f'{len(fixes)} key poses fixed; {path}: {n} keys, {int(times[-1])} ms')


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest='cmd', required=True)
    b = sub.add_parser('build')
    b.add_argument('video')
    b.add_argument('fit')
    b.add_argument('--palette', required=True)
    b.add_argument('--name', required=True)
    b.add_argument('--width', type=int, default=960)
    p = sub.add_parser('apply')
    p.add_argument('fit')
    p.add_argument('fixes')
    p.add_argument('--target', default='preview/keypose_fixed')
    p.add_argument('--clip-out', default=None)
    a = ap.parse_args()
    build(a) if a.cmd == 'build' else apply(a)


if __name__ == '__main__':
    main()
