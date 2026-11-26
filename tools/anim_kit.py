#!/usr/bin/env python3
"""
The shared machinery of the animation passes (tools/hakari_anims.py, tools/gojo_anims.py): a clip built pose by pose
with per-bone overlap (Seq), the writer, pose helpers, living holds (breathe), run cycles, and the strike shape
(wind away, a sharp approach, the contact held, an overshoot, a settle, back to rest). Rig conventions: tools/anim_author.py.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anim_author import arm, body, leg, mirror, pose  # noqa: E402,F401

ANIMS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'animations')
# Set by the pass that uses this: the folder clips go to, and what they say wrote them.
OUT = None
AUTHORED = None


# How much earlier (-) or later (+) than the anchor each bone reaches a pose, in ms: the body leads, the limb follows.
OFFSET = {'root': -40, 'hips': -32, 'chest': -16, 'head': 22, 'rightArm': 0, 'leftArm': 0, 'rightForearm': 18, 'leftForearm': 18,
          'rightHand': 30, 'leftHand': 30, 'rightThigh': -8, 'leftThigh': -8, 'rightShin': 12, 'leftShin': 12, 'rightFoot': 22,
          'leftFoot': 22}


class Seq:
    """A clip's keys, built pose by pose, with overlap applied per bone."""

    def __init__(self, duration):
        self.duration = duration
        self.tracks = {}  # bone -> [(t, value, channel, ease)]
        self.markers = []
        self.last = 0

    def add(self, t, p, ease='EASE_IN_OUT', marker=None, overlap=True):
        # The lead and lag shrink to fit the room around the pose: a body leading by 32 ms can't lead a key 20 ms after
        # the last one (it would land on or before it: a snap).
        room = max(0.0, t - self.last)
        lead_scale = min(1.0, room / 70.0)
        lag_scale = min(1.0, max(0.0, self.duration - t) / 40.0)
        for bone, v in p.items():
            ch = 'rot' if 'rot' in v else 'pos'
            o = OFFSET.get(bone, 0) if overlap else 0
            o *= lead_scale if o < 0 else lag_scale
            bt = min(self.duration, max(0, t + o))
            tr = self.tracks.setdefault(bone, [])
            if tr and bt < tr[-1][0] + 8:
                bt = min(self.duration, max(tr[-1][0] + 8, t))
                if bt <= tr[-1][0]:
                    continue
            tr.append((bt, v[ch], ch, ease))
        self.last = t
        if marker:
            self.markers.append((t, marker))
        return self

    def keys(self):
        by_t = {}
        for bone, tr in self.tracks.items():
            for t, val, ch, e in tr:
                by_t.setdefault(int(round(t)), {})[bone] = {ch: [round(x, 2) for x in val], 'ease': e}
        out = []
        for t in sorted(by_t):
            k = {'t': t, 'bones': by_t[t]}
            for mt, m in self.markers:
                if int(round(mt)) == t:
                    k['marker'] = m
            out.append(k)
        # A marker on a time with no key of its own gets one (an anchor bone always keys there, so this is rare).
        for mt, m in self.markers:
            if not any(k.get('marker') == m for k in out):
                near = min(out, key=lambda k: abs(k['t'] - mt))
                near['marker'] = m
        return out


def write(name, seq, loop=None, **extra):
    """Writes a clip, keeping the current file's priority and blends unless given."""
    path = os.path.join(OUT, name + '.json')
    old = json.load(open(path)) if os.path.exists(path) else {}
    d = {'name': name, 'duration': seq.duration}
    if loop:
        d.update({'loop': True, 'loopStart': loop[0], 'loopEnd': loop[1]})
    d['blendIn'] = extra.pop('blendIn', old.get('blendIn', 60))
    d['blendOut'] = extra.pop('blendOut', old.get('blendOut', 150))
    d['priority'] = extra.pop('priority', old.get('priority', 'SPECIAL'))
    for k in ('look', 'layer', 'interruptible', 'interruptWindow'):
        if k in old:
            d[k] = old[k]
    d.update(extra)
    d['authored'] = AUTHORED
    d['keys'] = seq.keys()
    with open(path, 'w') as f:
        json.dump(d, f, indent=1)


def blend(a, b, k):
    """A pose between two poses (same bones)."""
    out = {}
    for bone in a:
        ch = 'rot' if 'rot' in a[bone] else 'pos'
        if bone in b:
            out[bone] = {ch: [x + (y - x) * k for x, y in zip(a[bone][ch], b[bone][ch])]}
        else:
            out[bone] = a[bone]
    return out


def shifted(p, **delta):
    """A pose nudged: delta in degrees per bone axis, e.g. chest=(2, 0, 0), root=(0, -0.4, 0)."""
    q = {b: dict(v) for b, v in p.items()}
    for bone, d in delta.items():
        if bone not in q:
            continue  # (a clip that never keys that bone: nothing to nudge)
        ch = 'rot' if 'rot' in q[bone] else 'pos'
        q[bone] = {ch: [x + y for x, y in zip(q[bone][ch], d)]}
    return q


def breathe(seq, base, start, period, cycles, amp=1.0, tremble=0.0):
    """A living hold: breath through the chest and shoulders, a weight shift through the hips, each bone a little out of
    phase so it never moves as one block. Ends exactly where it starts, so it loops."""
    steps = 8
    for c in range(cycles):
        for s in range(steps + (1 if c == cycles - 1 else 0)):
            ph = 2 * math.pi * s / steps
            t = start + c * period + period * s / steps
            w = lambda lag: math.sin(ph - lag)  # noqa: E731
            q = shifted(base, chest=(2.2 * amp * w(0), 1.2 * amp * w(1.2), 0), hips=(0.8 * amp * w(0.4), 1.6 * amp * w(1.6), 0),
                        head=(1.6 * amp * w(0.8), 0, 0), root=(0, -0.35 * amp * (1 - math.cos(ph)) / 2, 0),
                        rightArm=(2.5 * amp * w(0.5) + tremble * math.sin(ph * 3), 0, 1.2 * amp * w(0.9)),
                        leftArm=(2.5 * amp * w(0.7) + tremble * math.sin(ph * 3 + 1), 0, 1.2 * amp * w(1.1)),
                        rightForearm=(1.8 * amp * w(1.0), 0, 0), leftForearm=(1.8 * amp * w(1.2), 0, 0))
            seq.add(t, q, 'EASE_IN_OUT', overlap=False)


def run_cycle(seq, start, period, cycles, lean=24.0, crouch=1.6, twist=0.0, right_arm=None, left_arm=None, stride=1.0):
    """A run: legs scissoring with knee lift, arms pumping against them, the body bobbing twice per cycle. A fixed arm
    pose (a grip, a guard) replaces that arm's pump. Ends where it starts, so it loops."""
    steps = 8
    for c in range(cycles):
        for s in range(steps + (1 if c == cycles - 1 else 0)):
            ph = 2 * math.pi * s / steps
            t = start + c * period + period * s / steps
            sw = math.sin(ph)
            # Thigh: forward (-) when the knee drives; shin bends most as the leg swings through.
            rt, lt = -38 * sw * stride + 8, 38 * sw * stride + 8
            rk = 25 + 55 * max(0.0, math.sin(ph + 0.9))
            lk = 25 + 55 * max(0.0, math.sin(ph + 0.9 + math.pi))
            bob = 0.7 * (1 - math.cos(2 * ph)) / 2
            p = pose(body(twist=twist + 6 * sw, lean=lean, crouch=crouch + bob, head_down=-8, fwd=0.5),
                     leg('right', rt, rk, 3, -10 * sw), leg('left', lt, lk, 3, 10 * sw),
                     right_arm if right_arm else arm('right', 30 * sw - 30, 6, 12, -95 + 15 * sw, 0),
                     left_arm if left_arm else arm('left', -30 * sw - 30, -6, 12, -95 - 15 * sw, 0))
            seq.add(t, p, 'LINEAR' if 0 < s < steps else 'EASE_IN_OUT', overlap=False)


def strike(seq, wind, hit, over, settle, rest, t_wind, t_hit, t_rest=None, cock=None, start=None, hold_ms=60):
    """Wind away, a sharp approach, the contact held a beat (marked), an overshoot, a settle, back to rest."""
    if start is not None:
        seq.add(0, start, 'EASE_IN_OUT', overlap=False)
    if cock is not None:
        seq.add(max(10, t_hit - 50), cock, 'EASE_IN_OUT')
    elif t_wind is not None:
        seq.add(t_wind, wind, 'EASE_OUT')
    seq.add(t_hit, hit, 'EASE_IN', marker='contact')
    seq.add(t_hit + hold_ms, shifted(hit, chest=(0.6, 0, 0)), 'LINEAR')
    seq.add(t_hit + hold_ms + 70, over, 'EASE_OUT')
    t_settle = t_hit + hold_ms + 70 + max(90, int((seq.duration - t_hit) * 0.25))
    seq.add(min(t_settle, seq.duration - 60), settle, 'EASE_IN_OUT')
    seq.add(seq.duration if t_rest is None else t_rest, rest, 'EASE_IN_OUT', overlap=False)


# ---------------------------------------------------------------------------------------------------------------------
# Reading any clip (frames or ms, named and mirrored poses, markers, per-bone eases) and refining it in place: the passes
# that keep a reference-matched clip's choreography but add what it lacks.

def _mirror_name(b):
    return b.replace('right', '\0').replace('left', 'right').replace('\0', 'left')


def _bone_values(v):
    """A bone entry as {'rot': [..], 'pos': [..]} plus its ease (or None)."""
    if isinstance(v, list):
        return {'rot': list(v)}, None
    out = {k: list(v[k]) for k in ('rot', 'pos') if k in v}
    return out, v.get('ease')


_POSES = None


def poses():
    """The shared pose library (animations/poses/*.json), parents ("extends") resolved."""
    global _POSES
    if _POSES is not None:
        return _POSES
    raw = {}
    for f in sorted(os.listdir(os.path.join(ANIMS, 'poses'))):
        if f.endswith('.json'):
            raw.update(json.load(open(os.path.join(ANIMS, 'poses', f))).get('poses', {}))
    done = {}

    def resolve(name):
        if name in done:
            return done[name]
        p = raw[name]
        bones = {}
        ext = p.get('extends', [])
        for parent in [ext] if isinstance(ext, str) else ext:
            bones.update(ref(parent))
        for b, v in p.get('bones', {}).items():
            bones[b] = _bone_values(v)[0]
        done[name] = bones
        return bones

    def ref(r):
        mirror_it = r.endswith('@mirror')
        n = r[:-len('@mirror')] if mirror_it else r
        n = n[len('poses/'):] if n.startswith('poses/') else n
        b = resolve(n)
        if not mirror_it:
            return b
        out = {}
        for bone, v in b.items():
            m = {}
            if 'rot' in v:
                m['rot'] = [v['rot'][0], -v['rot'][1], -v['rot'][2]]
            if 'pos' in v:
                m['pos'] = [-v['pos'][0], v['pos'][1], v['pos'][2]]
            out[_mirror_name(bone)] = m
        return out

    _POSES = {'_ref': ref}
    return _POSES


class ClipData:
    """A clip as tracks: bone -> channel -> [(t ms, [x, y, z], ease into it)], plus its markers and settings."""

    def __init__(self, group, name):
        self.group, self.name = group, name
        self.path = os.path.join(ANIMS, group, name + '.json')
        d = json.load(open(self.path))
        self.meta = d
        fps = d.get('fps', 50)
        fms = 1000.0 / fps
        ref0 = d.get('refStart', 0)
        self.tracks, self.markers = {}, []
        last = 0.0
        ref = poses()['_ref']
        for k in d['keys']:
            t = float(k['t']) if 't' in k else (k['f'] - ref0) * fms
            last = max(last, t)
            kease = k.get('ease', 'EASE_IN_OUT')
            bones = {}
            prs = k.get('pose', [])
            for pr in [prs] if isinstance(prs, str) else prs:
                for b, v in ref(pr).items():
                    bones.setdefault(b, {}).update({c: list(x) for c, x in v.items()})
            eases = {}
            for b, v in (k.get('bones') or {}).items():
                vals, e = _bone_values(v)
                bones.setdefault(b, {}).update(vals)
                if e:
                    eases[b] = e
            for b, vals in bones.items():
                for ch, x in vals.items():
                    self.tracks.setdefault(b, {}).setdefault(ch, []).append((t, x, eases.get(b, kease)))
            if 'marker' in k:
                self.markers.append((t, k['marker']))
        for m in d.get('markers', []):
            self.markers.append((float(m['t']) if 't' in m else (m['f'] - ref0) * fms, m.get('label', '?')))
        if 'endFrame' in d:
            self.duration = (d['endFrame'] - ref0) * fms
        elif 'duration' in d:
            self.duration = float(d['duration'])
        elif 'frames' in d:
            self.duration = d['frames'] * fms
        else:
            self.duration = last
        for b in self.tracks:
            for ch in self.tracks[b]:
                self.tracks[b][ch].sort(key=lambda e: e[0])

    # --- sampling (as the engine's tracks do: per-axis between keys, the later key's ease)

    @staticmethod
    def _ease(name, f):
        f = max(0.0, min(1.0, f))
        n = (name or 'EASE_IN_OUT').upper()
        if n == 'LINEAR':
            return f
        if n == 'EASE_IN':
            return f ** 3
        if n == 'EASE_OUT':
            return 1 - (1 - f) ** 3
        if n == 'STEP':
            return 1.0 if f >= 1 else 0.0
        if n in ('BACK', 'OVERSHOOT'):
            c, g = 1.70158, f - 1
            return 1 + (c + 1) * g ** 3 + c * g * g
        if n == 'SPRING':
            return 1 - math.exp(-6 * f) * math.cos(f * math.pi * 4.5)
        return 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2

    def value(self, bone, ch, t):
        tr = self.tracks.get(bone, {}).get(ch)
        if not tr:
            return None
        if t <= tr[0][0]:
            return list(tr[0][1])
        for (t0, a, _), (t1, b, e) in zip(tr, tr[1:]):
            if t <= t1:
                k = self._ease(e, (t - t0) / max(1e-6, t1 - t0))
                return [p + (q - p) * k for p, q in zip(a, b)]
        return list(tr[-1][1])

    def _has_key_near(self, bone, ch, t, within):
        return any(abs(k[0] - t) < within for k in self.tracks.get(bone, {}).get(ch, []))

    def _insert(self, bone, ch, t, val, ease):
        tr = self.tracks.setdefault(bone, {}).setdefault(ch, [])
        tr.append((t, val, ease))
        tr.sort(key=lambda e: e[0])

    # --- the refinements

    def overlap(self, keep=()):
        """Each bone's interior keys move by its lead or lag (OFFSET), scaled to fit between its neighbours: the body
        reaches each pose first and the limbs whip after. Keys at the times in {keep} (contacts) stay put for the
        anchor bones (offset 0)."""
        for bone, chans in self.tracks.items():
            o = OFFSET.get(bone, 0)
            if not o:
                continue
            for ch, tr in chans.items():
                new = [tr[0]]
                for i in range(1, len(tr) - 1):
                    t, v, e = tr[i]
                    prev_t, next_t = new[-1][0], tr[i + 1][0]
                    room = (t - prev_t) if o < 0 else (next_t - t)
                    shift = o * min(1.0, max(0.0, (room - 10) / (2 * abs(o))))
                    nt = min(max(t + shift, prev_t + 5), next_t - 5)
                    new.append((nt, v, e))
                new.append(tr[-1]) if len(tr) > 1 else None
                chans[ch] = new

    def hold(self, t, ms=60, push=0.6):
        """The contact held a beat: every moving bone keeps its pose at {t} until t + ms (unless it keys sooner)."""
        for bone, chans in self.tracks.items():
            for ch, tr in chans.items():
                # (Only a key strictly after the contact and inside the hold makes it unnecessary.)
                if len(tr) < 2 or any(t + 5 < k[0] < t + ms + 15 for k in tr):
                    continue
                v = self.value(bone, ch, t)
                if not self._has_key_near(bone, ch, t, 8):
                    self._insert(bone, ch, t, v, self._seg_ease(bone, ch, t))
                v2 = [v[0] + (push if bone == 'chest' and ch == 'rot' else 0), v[1], v[2]]
                self._insert(bone, ch, t + ms, v2, 'LINEAR')

    def _seg_ease(self, bone, ch, t):
        for k in self.tracks[bone][ch]:
            if k[0] >= t:
                return k[2]
        return 'EASE_IN_OUT'

    def overshoot(self, t, at, k=0.10, bones=None):
        """Follow-through: at {at}, the bones carry on a little past where they were at contact (the approach's
        direction, k of its last 60 ms), then the existing keys bring them back."""
        for bone, chans in self.tracks.items():
            if bones and bone not in bones:
                continue
            ch = 'rot'
            if ch not in chans or len(chans[ch]) < 2 or self._has_key_near(bone, ch, at, 25) or at >= self.duration - 20:
                continue
            a, b = self.value(bone, ch, t - 60), self.value(bone, ch, t)
            cur = self.value(bone, ch, at)
            delta = [(q - p) * k * 2 for p, q in zip(a, b)]
            if sum(abs(x) for x in delta) < 1.5:
                continue
            self._insert(bone, ch, at, [c + d for c, d in zip(cur, delta)], 'EASE_OUT')

    def wind(self, t, lead=120, k=0.18, bones=('rightArm', 'leftArm', 'chest', 'hips', 'rightThigh', 'leftThigh')):
        """Anticipation: {lead} ms before the contact, the bones pull back away from where they'll hit (k of the
        distance), so the approach starts from a wind-up."""
        tw = t - lead
        if tw < 20:
            return
        for bone in bones:
            chans = self.tracks.get(bone)
            if not chans or 'rot' not in chans or self._has_key_near(bone, 'rot', tw, 20):
                continue
            b = self.value(bone, 'rot', t)
            # Pull back past the farthest the limb has been from the contact so far (by then it's often already on its
            # way in, so pulling back from where it is would only slow it down).
            cand = [k2[1] for k2 in chans['rot'] if k2[0] <= tw] + [self.value(bone, 'rot', tw)]
            a = max(cand, key=lambda v: sum((x - y) ** 2 for x, y in zip(v, b)))
            if sum(abs(q - p) for p, q in zip(a, b)) < 10:
                continue
            self._insert(bone, 'rot', tw, [p + (p - q) * k for p, q in zip(a, b)], 'EASE_IN_OUT')

    def mark(self, t, label='contact'):
        self.markers = [m for m in self.markers if m[1] != label or abs(m[0] - t) > 1] + [(t, label)]

    def alive(self, period=500, cycles=2, amp=1.0, tremble=0.0):
        """A held clip becomes a loop: after its last key, the final pose breathes (tools: breathe)."""
        base = {}
        for bone, chans in self.tracks.items():
            base[bone] = {ch: self.value(bone, ch, self.duration) for ch in chans}
        start = self.duration
        seq = Seq(start + period * cycles)
        breathe(seq, {b: ({'rot': v['rot']} if 'rot' in v else {'pos': v['pos']}) for b, v in base.items()}, start, period, cycles,
                amp=amp, tremble=tremble)
        for bone, tr in seq.tracks.items():
            for t, val, ch, e in tr:
                if t > start + 0.5:
                    self._insert(bone, ch, t, val, e)
        self.meta.pop('hold', None)
        self.meta.update({'loop': True, 'loopStart': int(round(start)), 'loopEnd': int(round(start + period * cycles))})
        self.duration = start + period * cycles

    def save(self, authored, **extra):
        d = {k: v for k, v in self.meta.items() if k not in ('keys', 'fps', 'endFrame', 'refStart', 'frames', 'markers', 'authored')}
        d['duration'] = int(round(self.duration))
        d.update(extra)
        d['authored'] = authored
        by_t = {}
        for bone, chans in self.tracks.items():
            for ch, tr in chans.items():
                for t, val, e in tr:
                    by_t.setdefault(int(round(t)), {}).setdefault(bone, {})[ch] = [round(x, 2) for x in val]
                    by_t[int(round(t))][bone]['ease'] = e
        for t, _ in self.markers:
            by_t.setdefault(int(round(t)), {})
        keys = []
        for t in sorted(by_t):
            k = {'t': t}
            labels = [m for mt, m in self.markers if int(round(mt)) == t]
            if labels:
                k['marker'] = 'contact' if 'contact' in labels else labels[0]
            k['bones'] = by_t[t]
            keys.append(k)
        # Name keys first so the file reads like the old ones.
        d = {'name': d.pop('name', self.name), **d, 'keys': keys}
        with open(self.path, 'w') as f:
            json.dump(d, f, indent=1)


def _core_scale(cd, scale, bones):
    """Body involvement: each core bone's rotation (and the root's offset) moves {scale}x as far from the clip's first
    pose, keeping the reference's direction and timing."""
    for bone in bones:
        for ch, tr in cd.tracks.get(bone, {}).items():
            if not tr:
                continue
            base = tr[0][1]
            cd.tracks[bone][ch] = [(t, [b + (x - b) * scale for x, b in zip(v, base)], e) for t, v, e in tr]


def _lead_in(cd, ms, start_pose):
    """A clip that cut straight onto its contact gets {ms} of travel into it: every key moves later by {ms} and the clip
    now starts from {start_pose} (the previous clip's last pose: bone -> {ch: value})."""
    for bone, chans in cd.tracks.items():
        for ch, tr in chans.items():
            # (The way into the first key accelerates into the contact.)
            chans[ch] = [(t + ms, v, 'EASE_IN' if i == 0 else e) for i, (t, v, e) in enumerate(tr)]
            if bone in start_pose and ch in start_pose[bone]:
                start = list(start_pose[bone][ch])
                if ch == 'rot' and tr:
                    # The same orientation, taken the short way to the first key: a back-flip that ended at -280 carries
                    # on through 80 -> -10 instead of unwinding 270 degrees back (the clips blend as quaternions, so the
                    # hand-over itself does not move).
                    start = [x + 360.0 * round((target - x) / 360.0) for x, target in zip(start, tr[0][1])]
                chans[ch].insert(0, (0.0, start, 'EASE_IN_OUT'))
    cd.markers = [(t + ms, m) for t, m in cd.markers]
    cd.duration += ms
    cd.meta['blendIn'] = max(cd.meta.get('blendIn', 0), 60)


def end_pose(group, name):
    """A clip's last pose, bone -> {channel: value}."""
    c = ClipData(group, name)
    end = c.meta.get('loopEnd', c.duration) if c.meta.get('loop') else c.duration
    return {b: {ch: c.value(b, ch, end) for ch in chans} for b, chans in c.tracks.items()}


def _body(cd, t_wind, t_contact, t_settle, twist=16.0, lean=8.0, crouch=0.8, head=0.0):
    """The body behind a gesture the reference made with the arm alone: the torso turns away and gathers at {t_wind},
    turns and leans into it at {t_contact}, eases halfway back at {t_settle} (all on top of the clip's own values)."""
    shape = {t_wind: (twist, -lean * 0.4, crouch * 0.5, -head * 0.5), t_contact: (-twist, lean, crouch, head),
             t_settle: (-twist * 0.45, lean * 0.45, crouch * 0.4, head * 0.4)}
    for bone in ('chest', 'hips', 'head', 'root'):
        ch = 'pos' if bone == 'root' else 'rot'
        if ch not in cd.tracks.get(bone, {}):
            first = 0.0
            cd.tracks.setdefault(bone, {})[ch] = [(first, [0.0, 0.0, 0.0], 'EASE_IN_OUT'), (cd.duration, [0.0, 0.0, 0.0], 'EASE_IN_OUT')]
        base = {t: cd.value(bone, ch, t) for t in shape}
        for t, (tw, le, cr, hd) in shape.items():
            if not 0 < t < cd.duration:
                continue
            v = base[t]
            if bone == 'chest':
                v = [v[0] + le * 0.5, v[1] + tw, v[2]]
            elif bone == 'hips':
                v = [v[0] + le, v[1] + tw * 0.55, v[2]]
            elif bone == 'head':
                v = [v[0] + hd, v[1] - tw * 0.5, v[2]]
            else:
                v = [v[0], v[1] - cr, v[2] + le * 0.08]
            cd.tracks[bone][ch] = [k for k in cd.tracks[bone][ch] if abs(k[0] - t) > 15]
            cd._insert(bone, ch, t, v, 'EASE_IN' if t == t_contact else 'EASE_IN_OUT')


def _start_from(cd, prev):
    """A clip whose first key is its contact gets the previous clip's last pose at 0, so it travels into the contact."""
    _lead_in(cd, 0, end_pose(cd.group, prev))


ClipData.core = lambda self, scale=1.35, bones=('hips', 'chest', 'head', 'root'): _core_scale(self, scale, bones)
ClipData.body = _body
ClipData.start_from = _start_from
ClipData.lead_in = _lead_in
