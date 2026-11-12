#!/usr/bin/env python3
"""
The pose and motion vocabulary the clip authoring scripts (tools/anim_*.py) are written in.

A pose is built with P(): where the body's mass is (root offset, hips and chest lean / twist / roll), where the head
looks, and for each limb either joint angles (A, L) or a target the wrist or sole has to reach (H, HC, F), which is
solved for the mod's jointed skeleton. Feet given as targets stay planted while the hips travel, so weight shifts,
crouches and lunges read as real steps. A clip is a list of such poses in time, with:

  * lag   - successive breaking of joints: the hips and legs arrive first, the chest next, the arms last;
  * hit() - anticipation -> contact (whipped in) -> overshoot -> settle;
  * breathe() - a held pose that keeps a slow breathing loop instead of freezing.

Timing rules never change here: a clip keeps the length, hold and priority its move expects (see Clip).

Conventions (docs/ANIMATION.md): degrees [x, y, z]; x+ pitches forward, y+ turns to the character's right, z+ tips the
torso to its left / swings a right limb out. Targets are (right, up, forward) pixels from the spot between the feet.
"""
import json
import math
import os

import numpy as np

from anim_preview import BONES, PARENT, REST, ROOT as OUT, rot_zyx

K = math.pi / 180
ARM = {'right': ('rightArm', 'rightForearm', 'rightHand'), 'left': ('leftArm', 'leftForearm', 'leftHand')}
LEG = {'right': ('rightThigh', 'rightShin', 'rightFoot'), 'left': ('leftThigh', 'leftShin', 'leftFoot')}


# ---------------------------------------------------------------- limb specs

def A(raise_=0.0, swing=0.0, out=0.0, elbow=0.0, wrist=0.0, twist=None):
    """Arm by angles: raise (x, -90 = straight ahead), swing (y), out (z, away from the body), elbow (x, negative folds), wrist."""
    return ('A', raise_, swing, out, elbow, wrist)


def L(swing=0.0, knee=0.0, out=0.0, foot=0.0, turn=0.0):
    """Leg by angles relative to the hips: swing (x, negative = forward), knee (x, positive bends), out (z), foot, turn (y)."""
    return ('L', swing, knee, out, foot, turn)


def H(right, up, fwd, pole=None, wrist=0.0, roll=0.0):
    """Wrist at a point fixed to the ground frame (a punch reaching its target)."""
    return ('H', (right, up, fwd), pole, wrist, roll, False)


def HC(right, up, fwd, pole=None, wrist=0.0, roll=0.0):
    """Wrist at a point carried by the chest: (right, up, forward) from the base of the neck, in the chest's own frame."""
    return ('H', (right, up, fwd), pole, wrist, roll, True)


def F(right, up, fwd, toe=0.0, yaw=0.0, pole=None, rel=False):
    """Sole at a point on the ground frame (rel: carried by the root instead). toe > 0 points the toes down (heel up)."""
    return ('F', (right, up, fwd), toe, yaw, pole, rel)


# ---------------------------------------------------------------- solving

def _euler(m):
    """ZYX Euler degrees of a rotation matrix (R = Rz Ry Rx)."""
    sy = -m[2, 0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(sy) > 0.9999:
        x = math.atan2(-m[1, 2], m[1, 1])
        z = 0.0
    else:
        x = math.atan2(m[2, 1], m[2, 2])
        z = math.atan2(m[1, 0], m[0, 0])
    return [x / K, y / K, z / K]


def _rot(r):
    return rot_zyx(r[2] * K, r[1] * K, r[0] * K)


def _T(v):
    m = np.eye(4)
    m[:3, 3] = v
    return m


def _model(p):
    """(right, up, forward) -> model space."""
    return np.array([-p[0], 24 - p[1], -p[2]])


def _local(bone):
    r, pr = REST[bone], REST[PARENT[bone]]
    return np.array([r[0] - pr[0], r[1] - pr[1], r[2] - pr[2]], dtype=float)


def _chain_end(parent, bones, e, bend):
    up = parent @ _T(_local(bones[0])) @ _rot(e)
    lo = up @ _T(_local(bones[1])) @ _rot([bend, 0, 0])
    end = lo @ _T(_local(bones[2]))
    return lo[:3, 3], end[:3, 3]


def _ik(parent, bones, target, pole, arm):
    """Upper-bone Euler and the hinge bend that put the chain's end (wrist / ankle) on target, the joint toward pole."""
    j = (parent @ _T(_local(bones[0])))[:3, 3]
    rp = parent[:3, :3]
    a = _local(bones[1])
    l1, l2 = float(np.linalg.norm(a)), float(np.linalg.norm(_local(bones[2])))
    d = rp.T @ (target - j)
    dist = float(np.linalg.norm(d))
    reach = max(abs(l1 - l2) + 0.05, min(dist, l1 + l2 - 0.02))
    u = d / max(dist, 1e-6)
    p = rp.T @ pole
    pp = p - np.dot(p, u) * u
    if np.linalg.norm(pp) < 1e-4:
        pp = np.array([0.0, 0.0, 1.0]) - u[2] * u
    pp /= np.linalg.norm(pp)
    ca = max(-1.0, min(1.0, (l1 * l1 + reach * reach - l2 * l2) / (2 * l1 * reach)))
    alpha = math.acos(ca)
    u1 = math.cos(alpha) * u + math.sin(alpha) * pp
    u2 = u * reach - u1 * l1
    u2 /= np.linalg.norm(u2)
    perp = u2 - np.dot(u2, u1) * u1
    if np.linalg.norm(perp) < 1e-5:
        perp = -pp if arm else pp
    perp /= np.linalg.norm(perp)
    zl = -perp if arm else perp
    xl = np.cross(u1, zl)
    frame = np.column_stack([xl, u1, zl])
    # The upper bone's own axis is not exactly its local y (the elbow sits a pixel out): rotate so that axis lands on u1.
    ah = a / l1
    zr = np.array([0.0, 0.0, 1.0])
    xr = np.cross(ah, zr)
    xr /= np.linalg.norm(xr)
    rest = np.column_stack([xr, ah, np.cross(xr, ah)])
    e = _euler(frame @ rest.T)
    ci = max(-1.0, min(1.0, (l1 * l1 + l2 * l2 - reach * reach) / (2 * l1 * l2)))
    bend = (180 - math.acos(ci) / K) * (-1 if arm else 1)
    # Polish numerically (the arm's offset elbow), keeping the joint near where the pole put it.
    want_mid = j + rp @ (u1 * l1)
    goal = j + rp @ (u * reach)
    x = np.array(e + [bend])

    def res(v):
        mid, end = _chain_end(parent, bones, v[:3], v[3])
        return np.concatenate([end - goal, 0.15 * (mid - want_mid)])

    for _ in range(12):
        r0 = res(x)
        if np.linalg.norm(r0[:3]) < 0.02:
            break
        jac = np.zeros((6, 4))
        for i in range(4):
            dx = np.zeros(4)
            dx[i] = 0.5
            jac[:, i] = (res(x + dx) - r0) / 0.5
        step = np.linalg.lstsq(jac, -r0, rcond=None)[0]
        x = x + np.clip(step, -25, 25)
    if arm:
        x[3] = min(0.0, x[3])
    else:
        x[3] = max(0.0, x[3])
    return [float(v) for v in x[:3]], float(x[3])


def P(root=(0, 0, 0), yaw=0.0, hips=(0, 0, 0), chest=(0, 0, 0), head=(0, 0, 0), follow=0.25,
      ra=None, la=None, rl=None, ll=None, tilt=(0, 0), neck=None, blade=None):
    """
    A whole-body pose. root: (right, up, forward) px. yaw: the whole body turned (root y; tilt = root x, z for flips).
    hips / chest: (lean, twist, roll), chest relative to the hips. head: (pitch, yaw, roll) of the gaze in the ground
    frame; follow blends in how much the head goes with the body. Limbs: A / H / HC and L / F specs (None = rest).
    """
    out = {'root': {'pos': [float(root[0]), float(root[1]), float(root[2])], 'rot': [float(tilt[0]), float(yaw), float(tilt[1])]},
           'hips': {'rot': [float(v) for v in hips]}, 'chest': {'rot': [float(v) for v in chest]}}
    ik = set()
    w = {}
    w['root'] = _T([REST['root'][0] - root[0], REST['root'][1] - root[1], REST['root'][2] - root[2]]) @ _rot(out['root']['rot'])
    w['hips'] = w['root'] @ _T(_local('hips')) @ _rot(out['hips']['rot'])
    w['chest'] = w['hips'] @ _T(_local('chest')) @ _rot(out['chest']['rot'])
    w['neck'] = w['chest'] @ _T(_local('neck'))
    if neck is not None:
        out['neck'] = {'rot': [float(v) for v in neck]}
        w['neck'] = w['neck'] @ _rot(neck)
    # Head: toward the gaze, partly carried by the body.
    want = _rot(head)[:3, :3]
    comp = _euler(w['neck'][:3, :3].T @ want)
    hr = [comp[i] * (1 - follow) + head[i] * follow for i in range(3)]
    hr = [max(-80.0, min(80.0, hr[0])), max(-85.0, min(85.0, hr[1])), max(-40.0, min(40.0, hr[2]))]
    out['head'] = {'rot': hr}
    for side, spec in (('right', ra), ('left', la)):
        s = 1 if side == 'right' else -1
        b = ARM[side]
        if spec is None:
            continue
        if spec[0] == 'A':
            _, raise_, swing, o, elbow, wrist = spec
            out[b[0]] = {'rot': [raise_, swing * s, o * s]}
            out[b[1]] = {'rot': [elbow, 0, 0]}
            out[b[2]] = {'rot': [wrist, 0, 0]}
        else:
            _, tgt, pole, wrist, roll, rel = spec
            if rel:
                nk = w['chest'] @ _T(_local('neck'))
                target = (nk @ np.array([-tgt[0], -tgt[1], -tgt[2], 1.0]))[:3]
            else:
                target = _model(tgt)
            if pole is None:
                pl = w['chest'][:3, :3] @ np.array([-0.55 * s, 0.6, 0.6])
            else:
                pl = np.array([-pole[0], -pole[1], -pole[2]], dtype=float)
            e, bend = _ik(w['chest'], b, target, pl, True)
            out[b[0]] = {'rot': e}
            out[b[1]] = {'rot': [bend, 0, 0]}
            out[b[2]] = {'rot': [wrist, 0, roll * s]}
            ik.add(b[0])
    if blade is not None and 'rightArm' in out:
        # A held blade points out of the fist along the hand's forward: aim it (degrees up, degrees to his right, roll).
        fw = w['chest'] @ _T(_local('rightArm')) @ _rot(out['rightArm']['rot']) @ _T(_local('rightForearm')) @ _rot(out['rightForearm']['rot'])
        want_b = _rot([-blade[0], blade[1], blade[2] if len(blade) > 2 else 0.0])[:3, :3]
        out['rightHand'] = {'rot': _euler(fw[:3, :3].T @ want_b)}
        ik.add('rightHand')
    for side, spec in (('right', rl), ('left', ll)):
        s = 1 if side == 'right' else -1
        b = LEG[side]
        if spec is None:
            continue
        if spec[0] == 'L':
            _, swing, knee, o, foot, turn = spec
            out[b[0]] = {'rot': [swing, turn * s, o * s]}
            out[b[1]] = {'rot': [knee, 0, 0]}
            out[b[2]] = {'rot': [foot, 0, 0]}
        else:
            _, tgt, toe, fyaw, pole, rel = spec
            sole = (w['root'] @ np.array([-tgt[0], -tgt[1], -tgt[2], 1.0]))[:3] if rel else _model(tgt)
            base = w['root'][:3, :3] if rel else np.eye(3)
            want_foot = base @ _rot([toe, fyaw * s, 0])[:3, :3]
            ankle = sole + want_foot @ np.array([0.0, -2.0, 0.0])
            if pole is None:
                pl = w['hips'][:3, :3] @ np.array([-0.3 * s, -0.15, -1.0])
            else:
                pl = np.array([-pole[0], -pole[1], -pole[2]], dtype=float)
            e, bend = _ik(w['hips'], b, ankle, pl, False)
            out[b[0]] = {'rot': e}
            out[b[1]] = {'rot': [bend, 0, 0]}
            shin = w['hips'] @ _T(_local(b[0])) @ _rot(e) @ _T(_local(b[1])) @ _rot([bend, 0, 0])
            fe = _euler(shin[:3, :3].T @ want_foot)
            out[b[2]] = {'rot': [max(-70.0, min(70.0, fe[0])), max(-40.0, min(40.0, fe[1])), max(-40.0, min(40.0, fe[2]))]}
            ik.add(b[0])
            ik.add(b[2])
    out['_ik'] = ik | {'head'}
    return out


# ---------------------------------------------------------------- pose arithmetic

def mirror(p):
    """The same pose, left for right."""
    out = {}
    for k, v in p.items():
        if k == '_ik':
            out[k] = {(('left' + b[5:]) if b.startswith('right') else ('right' + b[4:]) if b.startswith('left') else b) for b in v}
            continue
        n = ('left' + k[5:]) if k.startswith('right') else ('right' + k[4:]) if k.startswith('left') else k
        d = {}
        if 'rot' in v:
            d['rot'] = [v['rot'][0], -v['rot'][1], -v['rot'][2]]
        if 'pos' in v:
            d['pos'] = [-v['pos'][0], v['pos'][1], v['pos'][2]]
        if 'scale' in v:
            d['scale'] = v['scale']
        out[n] = d
    return out


def _near(a, ref):
    """a, shifted by whole turns to sit nearest ref."""
    return [x + 360.0 * round((r - x) / 360.0) for x, r in zip(a, ref)]


def lerp(p, q, f):
    """p toward q by f (f < 0 or > 1 extrapolates: an overshoot past q, or a deeper version of p)."""
    out = {'_ik': set(p.get('_ik', ())) | set(q.get('_ik', ()))}
    for b in p:
        if b == '_ik':
            continue
        d = {}
        for ch in ('rot', 'pos', 'scale'):
            if ch in p[b]:
                if b in q and ch in q[b]:
                    qq = _near(q[b][ch], p[b][ch]) if ch == 'rot' else q[b][ch]
                    d[ch] = [x + (y - x) * f for x, y in zip(p[b][ch], qq)]
                else:
                    d[ch] = list(p[b][ch])
        out[b] = d
    for b in q:
        if b not in out:
            out[b] = {ch: list(v) for ch, v in q[b].items()}
    return out


def over(hit, wind, k=0.12):
    """The pose a little past the hit, along the way it came from the wind-up (follow-through)."""
    return lerp(hit, wind, -k)


def tweak(p, **bones):
    """A copy with some bones' rotation nudged: tweak(pose, chest=(4, 0, 0), rightHand=(-20, 0, 0))."""
    out = {b: ({ch: list(v) for ch, v in d.items()} if b != '_ik' else set(d)) for b, d in p.items()}
    for b, dv in bones.items():
        if b == 'root_pos':
            out['root']['pos'] = [x + y for x, y in zip(out['root']['pos'], dv)]
            continue
        cur = out.setdefault(b, {}).get('rot', [0.0, 0.0, 0.0])
        out[b]['rot'] = [x + y for x, y in zip(cur, dv)]
    return out


def only(p, *bones):
    """Just these bones of a pose (for a partial key or an upper-body clip)."""
    out = {b: p[b] for b in bones if b in p}
    out['_ik'] = set(p.get('_ik', ())) & set(bones)
    return out


UPPER = ('chest', 'neck', 'head', 'rightArm', 'rightForearm', 'rightHand', 'leftArm', 'leftForearm', 'leftHand')
LOWER = ('root', 'hips', 'rightThigh', 'rightShin', 'rightFoot', 'leftThigh', 'leftShin', 'leftFoot')
_CORE = ('root', 'hips', 'rightThigh', 'rightShin', 'rightFoot', 'leftThigh', 'leftShin', 'leftFoot')
_MID = ('chest', 'neck')


# ---------------------------------------------------------------- clips

def meta(group, name):
    path = os.path.join(OUT, group, name + '.json')
    return json.load(open(path)) if os.path.exists(path) else None


class Clip:
    """
    One clip being authored. Unless told otherwise it keeps what the existing file says about timing and playback
    (duration, hold, loop, priority, layer, blends, look, tremble, speed, interrupt window): those belong to the move.
    """
    KEEP = ('fps', 'hold', 'loop', 'loopStart', 'loopEnd', 'stopAfter', 'blendIn', 'blendEase', 'blendOut', 'priority', 'layer',
            'interruptible', 'interruptWindow', 'speed', 'look', 'tremble', 'rig')

    def __init__(self, group, name, note, duration=None, **over_):
        self.group, self.name, self.note = group, name, note
        old = meta(group, name) or {}
        self.d = {}
        for k in self.KEEP:
            if k in old:
                self.d[k] = old[k]
        if duration is None:
            if 'duration' in old:
                duration = old['duration']
            elif 'endFrame' in old:
                duration = (old['endFrame'] - old.get('refStart', 0)) * 1000.0 / old.get('fps', 50)
            elif 'frames' in old:
                duration = old['frames'] * 1000.0 / old.get('fps', 50)
        self.duration = duration
        self.d.pop('fps', None)
        self.d.update(over_)
        self.keys = []
        self.last = {}
        self.markers = []

    def key(self, t, pose, ease=None, lag=0, marker=None, eases=None, bones=None):
        """
        pose at t ms. ease shapes the move into it. lag: the hips and legs get there lag ms early, the chest half
        that, the arms and head on t. eases: per-bone overrides. bones: only these.
        """
        groups = {}
        for b, ch in pose.items():
            if b == '_ik' or (bones is not None and b not in bones):
                continue
            off = -lag if b in _CORE else -lag * 0.5 if b in _MID else 0
            tt = max(0, int(round(t + off)))
            d = {}
            for c, v in ch.items():
                if c == 'rot':
                    v = list(v)
                    if b in self.last and (b in pose.get('_ik', ()) or b not in ('root', 'hips')):
                        v = _near(v, self.last[b])
                    self.last[b] = v
                    d['rot'] = [round(x, 1) + 0.0 for x in v]
                elif c == 'pos':
                    if b != 'root' and not any(abs(x) > 1e-6 for x in v):
                        continue
                    d['pos'] = [round(x, 2) + 0.0 for x in v]
                else:
                    d[c] = v
            if b == 'root' and not any(abs(x) > 1e-6 for x in d.get('rot', [0])) and not self._root_rot:
                d.pop('rot', None)
            if eases and b in eases:
                d['ease'] = eases[b]
            if not d:
                continue
            if list(d) == ['rot']:
                d = d['rot']
            groups.setdefault(tt, {})[b] = d
        first = True
        for tt in sorted(groups):
            k = {'t': tt}
            if ease:
                k['ease'] = ease
            if marker and (tt == max(groups)):
                k['marker'] = marker
            k['bones'] = groups[tt]
            self.keys.append(k)
            first = False
        return self

    _root_rot = False

    def spins(self):
        """This clip turns the root (spins, flips): key its rotation on every key so it never holds a stale turn."""
        self._root_rot = True
        return self

    def hit(self, t, wind, hit, ease='EASE_IN', lag=30, k=0.10, carry=60, settle=None, settle_at=None, marker='impact'):
        """Contact at t (whipped in from wind), a follow-through past it at t + carry, then settled."""
        self.key(t, hit, ease, lag=lag, marker=marker)
        self.key(t + carry, over(hit, wind, k), 'EASE_OUT', marker='follow-through')
        if settle is not None:
            self.key(settle_at if settle_at is not None else t + carry + 110, settle, 'EASE_IN_OUT')
        return self

    def breathe(self, t0, pose, period=1800, amp=1.0, sway=0.0):
        """From t0 the pose is held alive: a slow breath (chest, shoulders, head) looping until the move releases it."""
        b = tweak(only(pose, *UPPER), chest=(1.6 * amp, sway, 0), head=(-1.2 * amp, -sway * 0.6, 0),
                  rightArm=(1.8 * amp, 0, 1.2 * amp), leftArm=(1.8 * amp, 0, -1.2 * amp),
                  rightForearm=(-2.0 * amp, 0, 0), leftForearm=(-2.0 * amp, 0, 0))
        b['_ik'] = set()
        up = only(pose, *UPPER)
        up['_ik'] = set()
        self.key(t0, pose, 'EASE_OUT')
        self.key(t0 + period / 2, b, 'EASE_IN_OUT')
        self.key(t0 + period, up, 'EASE_IN_OUT')
        self.d['loop'] = True
        self.d['loopStart'] = int(t0)
        self.d['loopEnd'] = int(t0 + period)
        self.duration = int(t0 + period)
        return self

    def write(self):
        d = {'name': self.name}
        if self.duration is not None:
            d['duration'] = int(self.duration) if float(self.duration).is_integer() else self.duration
        d.update(self.d)
        d['authored'] = self.note
        self.keys.sort(key=lambda k: k['t'])
        lines = ['{']
        for k, v in d.items():
            lines.append(' %s: %s,' % (json.dumps(k), json.dumps(v)))
        lines.append(' "keys": [')
        for i, k in enumerate(self.keys):
            lines.append('  ' + json.dumps(k) + (',' if i < len(self.keys) - 1 else ''))
        lines.append(' ]')
        lines.append('}')
        os.makedirs(os.path.join(OUT, self.group), exist_ok=True)
        with open(os.path.join(OUT, self.group, self.name + '.json'), 'w') as f:
            f.write('\n'.join(lines) + '\n')
        WRITTEN.append(self.name)


WRITTEN = []
