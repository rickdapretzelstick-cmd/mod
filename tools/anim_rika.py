#!/usr/bin/env python3
"""
Secondary motion for Rika's clips (assets/jjk/animations/rika, "rig": "rika").

    python3 tools/anim_rika.py

Her moves are keyed by hand against the JJS GIFs on the body, head, jaw and arms, and those keys (and every time in
them) are left exactly as they are. What this adds is the part of her that was not moving: the neck and the long tail.
Each time the body is keyed, the neck gives a little against it and the tail follows it late, segment after segment,
so a wind-up drags the tail one way and the blow cracks it through the other; the idle gets a slow sway down the tail.
Re-running replaces what an earlier run added (the neck and tail tracks) and nothing else.
"""
import glob
import json
import math
import os

from anim_preview import ROOT

TAIL = ['tail_%d' % i for i in range(0, 12, 2)]
ADDED = set(TAIL) | {'neck'}
STEP = 42   # ms each tail segment trails the one before


def r1(v):
    return [round(x, 1) + 0.0 for x in v]


def main():
    done = []
    for path in sorted(glob.glob(os.path.join(ROOT, 'rika', '*.json'))):
        d = json.load(open(path))
        name = d.get('name', os.path.basename(path)[:-5])
        if name == 'rika_calibrate':
            continue
        keys = []
        for k in d['keys']:
            b = {n: v for n, v in k.get('bones', {}).items() if n not in ADDED}
            if b or 'marker' in k:
                kk = dict(k)
                kk['bones'] = b
                keys.append(kk)
        dur = d.get('duration', max(k['t'] for k in keys))
        extra = []
        if name == 'rika_idle':
            for t in range(0, int(dur) + 1, 500):
                ph = 2 * math.pi * t / dur
                bones = {n: r1([2.0 * math.sin(ph - 0.5 * i), (5.0 + i) * math.sin(ph - 0.7 * i), 0]) for i, n in enumerate(TAIL)}
                bones['neck'] = r1([1.5 * math.sin(ph), 0, 0])
                extra.append({'t': t, 'ease': 'LINEAR', 'bones': bones})
        else:
            # Her moves keyed by hand, left as they are, with the rest of her answering them:
            #  - the neck gives against every turn of the body (late, a little), and whips through on a blow;
            #  - the jaw opens wider on the hits and clamps on the recoveries;
            #  - the head is thrown the other way from the body's pitch (weight), then catches up;
            #  - the tail follows late, segment after segment, cracking through on a blow and swaying wide on a wind-up;
            #  - the whole body sinks a hair after a blow lands (the floor taking her weight), then rises again.
            prev_rot = (0, 0, 0)
            for k in keys:
                body = k['bones'].get('body')
                rot = body if isinstance(body, list) else (body or {}).get('rot')
                if rot is None:
                    continue
                x, y, z = rot
                dx, dy = x - prev_rot[0], y - prev_rot[1]
                prev_rot = (x, y, z)
                hit = k.get('ease') == 'EASE_IN'
                wind = k.get('ease') == 'EASE_OUT' and not hit
                big = abs(dx) + abs(dy) > 30
                # The neck: against the body's pitch and turn, late; whipped through on a hit and back.
                extra.append({'t': min(dur, k['t'] + 20), 'ease': 'EASE_OUT' if not hit else 'OVERSHOOT',
                              'bones': {'neck': r1([-x * (0.34 if hit else 0.28), -y * 0.24, 0])}})
                if hit and big:
                    extra.append({'t': min(dur, k['t'] + 110), 'ease': 'EASE_OUT', 'bones': {'neck': r1([-x * 0.18 + dx * 0.10, -y * 0.2, 0])}})
                    # The jaw, wider on the blow, and the whole body sinking into the floor and recovering.
                    jaw = k['bones'].get('jaw')
                    jrot = jaw if isinstance(jaw, list) else (jaw or {}).get('rot')
                    if jrot is not None:
                        extra.append({'t': min(dur, k['t'] + 60), 'ease': 'OVERSHOOT', 'bones': {'jaw': r1([jrot[0] + 10, 0, 0])}})
                    pos = (body or {}).get('pos') if isinstance(body, dict) else None
                    if pos is not None and k['t'] + 140 < dur:
                        nxt = min((kk['t'] for kk in keys if kk['t'] > k['t'] and 'body' in kk['bones']), default=dur)
                        if nxt - k['t'] > 220:
                            extra.append({'t': k['t'] + 70, 'ease': 'EASE_OUT', 'bones': {'body': {'pos': r1([pos[0], pos[1] - 1.6, pos[2]])}}})
                            extra.append({'t': min(nxt - 40, k['t'] + 200), 'ease': 'EASE_IN_OUT', 'bones': {'body': {'pos': r1(pos)}}})
                for i, n in enumerate(TAIL):
                    g = 0.22 + 0.06 * i
                    t = min(dur, k['t'] + STEP * (i + 1))
                    sway = (1.6 if wind else 1.3)
                    extra.append({'t': t, 'ease': 'OVERSHOOT' if hit else 'EASE_OUT', 'bones': {n: r1([-x * g, -y * g * sway, y * 0.1 + (dx * 0.04 if hit else 0)])}})
                    if hit and big:
                        # The crack through: past the pose and back.
                        extra.append({'t': min(dur, t + 90), 'ease': 'EASE_OUT', 'bones': {n: r1([-x * g * 1.25, -y * g * 1.5, y * 0.12])}})
        d['keys'] = sorted(keys + extra, key=lambda k: k['t'])
        d['secondary'] = 'tools/anim_rika.py (neck and tail follow-through)'
        lines = ['{']
        for kk, v in d.items():
            if kk != 'keys':
                lines.append(' %s: %s,' % (json.dumps(kk), json.dumps(v)))
        lines.append(' "keys": [')
        lines += ['  ' + json.dumps(k) + (',' if i < len(d['keys']) - 1 else '') for i, k in enumerate(d['keys'])]
        lines += [' ]', '}']
        open(path, 'w').write('\n'.join(lines) + '\n')
        done.append(name)
    print('added neck and tail motion to', len(done), 'clips:', ' '.join(done))


if __name__ == '__main__':
    main()
