# A converted recording (r6_convert.py's clip) cut into the game's own player clips: the keys between two frames of
# the recording, re-timed from the first, written as assets/jjk/animations/<group>/<name>.json for Anim.play.
#   python3 tools/r6_game_clip.py CLIP.json TAKE_START FROM TO GROUP/NAME [--priority SPECIAL] [--blend-in 40]
#       [--blend-out 150] [--video VIDEO]
# TAKE_START: the frame the converted clip starts at (its idle frame); FROM / TO: recording frames to keep (TO past
# the clip's end keeps all of it). Frames are turned into clip time with the video's own frame rate. The cut starts
# on a key interpolated at FROM, so the pose there is the recording's, not the nearest key's.
import sys, os, json, argparse
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def pose_at(keys, t):
    """The clip's pose at time t (ms): linear between keys, per bone and channel (the game's own interpolation)."""
    before = [k for k in keys if k['t'] <= t]
    after = [k for k in keys if k['t'] >= t]
    a = before[-1] if before else keys[0]
    b = after[0] if after else keys[-1]
    if b['t'] == a['t']:
        return json.loads(json.dumps(a['bones']))
    w = (t - a['t']) / (b['t'] - a['t'])
    out = {}
    for bone in set(a['bones']) | set(b['bones']):
        out[bone] = {}
        for ch in set(a['bones'].get(bone, {})) | set(b['bones'].get(bone, {})):
            va = np.array(a['bones'].get(bone, {}).get(ch, [0, 0, 0]), float)
            vb = np.array(b['bones'].get(bone, {}).get(ch, [0, 0, 0]), float)
            out[bone][ch] = [round(float(x), 2) for x in va + w * (vb - va)]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('clip')
    ap.add_argument('take_start', type=int)
    ap.add_argument('frm', type=int)
    ap.add_argument('to', type=int)
    ap.add_argument('name', help='GROUP/NAME, e.g. yuji/cursed_strikes_flurry')
    ap.add_argument('--priority', default='SPECIAL')
    ap.add_argument('--blend-in', type=int, default=40)
    ap.add_argument('--blend-out', type=int, default=150)
    ap.add_argument('--video', default=os.path.join(ROOT, 'build', 'video.mp4'))
    a = ap.parse_args()
    import cv2
    fps = cv2.VideoCapture(a.video).get(cv2.CAP_PROP_FPS) or 30.0
    src = json.load(open(a.clip))
    keys = src['keys']
    t0 = (a.frm - a.take_start) * 1000.0 / fps
    t1 = min((a.to - a.take_start) * 1000.0 / fps, keys[-1]['t'])
    out_keys = [{'t': 0, 'ease': 'LINEAR', 'bones': pose_at(keys, t0)}]
    out_keys += [{'t': int(round(k['t'] - t0)), 'ease': k.get('ease', 'LINEAR'), 'bones': k['bones']}
                 for k in keys if t0 < k['t'] < t1]
    out_keys.append({'t': int(round(t1 - t0)), 'ease': 'LINEAR', 'bones': pose_at(keys, t1)})
    group, name = a.name.split('/')
    d = {'name': name, 'duration': int(round(t1 - t0)), 'blendIn': a.blend_in, 'blendOut': a.blend_out,
         'priority': a.priority,
         'fitted': f"tools/r6_convert.py + r6_game_clip.py: {os.path.relpath(a.clip, ROOT)} frames {a.frm}-{min(a.to, a.take_start + int(t1 * fps / 1000))}",
         'keys': out_keys}
    path = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'jjk', 'animations', group, name + '.json')
    json.dump(d, open(path, 'w'), indent=1)
    print(f'{path}: {len(out_keys)} keys, {d["duration"]} ms')


if __name__ == '__main__':
    main()
