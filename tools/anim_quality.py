#!/usr/bin/env python3
"""
Scores player clips (assets/jjk/animations) against the basic principles of animation, from the numbers alone, so a
pass can be measured instead of eyeballed:

  anticipation   before the contact, the striking limb first moves *away* from where it will hit (a wind-up)
  hit-stop       right after the contact the striking limb holds (a beat where the blow reads)
  settle         after the contact there is follow-through before the return to guard (not one straight ease home)
  overlap        the body leads and the limb follows: hips peak before the chest, the chest before the striking arm,
                 the arm before the forearm (successive breaking of joints), spread over 20+ ms, not on the same frame
  whole body     the hips, chest and legs carry a real share of the motion, not just the arms (35%; 20% for a clip
                 tagged "style": "effortless", a deliberately calm gesture like Gojo's one-handed techniques)
  alive          a held or looping pose keeps moving a little (breath, weight shift) instead of freezing

Usage:
    python3 tools/anim_quality.py hakari            # every clip in a folder
    python3 tools/anim_quality.py hakari/rough_strike common/light_1

A clip marks its contact frame with a key carrying "marker": "contact" (the authoring script writes it). Without one,
the contact is taken as the moment the fastest bone peaks.
"""
import glob
import json
import math
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'animations')

LIMBS = ['rightArm', 'rightForearm', 'leftArm', 'leftForearm', 'rightThigh', 'rightShin', 'leftThigh', 'leftShin']
CORE = ['hips', 'chest', 'head']
LEGS = ['rightThigh', 'rightShin', 'leftThigh', 'leftShin']
ARMS = ['rightArm', 'rightForearm', 'rightHand', 'leftArm', 'leftForearm', 'leftHand']
CHILD = {'rightArm': 'rightForearm', 'leftArm': 'leftForearm', 'rightThigh': 'rightShin', 'leftThigh': 'leftShin'}


def ease(name, f):
    f = max(0.0, min(1.0, f))
    n = (name or 'EASE_IN_OUT').upper()
    if n == 'LINEAR':
        return f
    if n == 'EASE_IN':
        return f * f * f
    if n == 'EASE_OUT':
        return 1 - (1 - f) ** 3
    if n == 'STEP':
        return 1.0 if f >= 1 else 0.0
    if n in ('BACK', 'OVERSHOOT'):
        c = 1.70158
        g = f - 1
        return 1 + (c + 1) * g ** 3 + c * g * g
    if n == 'SPRING':
        return 1 - math.exp(-6 * f) * math.cos(f * math.pi * 4.5)
    return 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2


def quat_zyx(x, y, z):
    """The rig's rotation: rotationZYX(z, y, x) of degrees, as a quaternion (w, x, y, z)."""
    x, y, z = (math.radians(a) / 2 for a in (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return (cz * cy * cx + sz * sy * sx, cz * cy * sx - sz * sy * cx, cz * sy * cx + sz * cy * sx, sz * cy * cx - cz * sy * sx)


def angle(a, b):
    d = abs(sum(p * q for p, q in zip(a, b)))
    return math.degrees(2 * math.acos(min(1.0, d)))


class Clip:
    """A clip read through tools/anim_kit.ClipData (frames or ms, named and mirrored poses, markers): its rotations."""

    def __init__(self, path):
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import anim_kit
        group, name = os.path.basename(os.path.dirname(path)), os.path.basename(path)[:-5]
        cd = anim_kit.ClipData(group, name)
        d = cd.meta
        self.path, self.name = path, d.get('name', name)
        self.hold, self.loop = bool(d.get('hold')), bool(d.get('loop'))
        # A clip tagged "motion": "transition" moves between poses (a step, a bounce, a leap): no blow to judge.
        self.transition = d.get('motion') == 'transition'
        # A blow that lands the tick its clip starts has no room to wind up: "windup" names the clip that winds it.
        self.windup = d.get('windup')
        # "style": "effortless" (Gojo's one-handed techniques): a deliberate, calm gesture; the body may stay quiet.
        self.effortless = d.get('style') == 'effortless'
        self.tracks = {b: ch['rot'] for b, ch in cd.tracks.items() if 'rot' in ch}
        self.contact = next((t for t, m in cd.markers if m == 'contact'), None)
        if self.contact is None:
            self.contact = next((t for t, m in cd.markers if m in ('impact', 'hit')), None)
        self.keys = len(d.get('keys', []))
        self.duration = cd.duration
        self.loop_start = d.get('loopStart', 0)
        self.loop_end = d.get('loopEnd', self.duration)

    def rot(self, bone, t):
        tr = self.tracks.get(bone)
        if not tr:
            return (1.0, 0.0, 0.0, 0.0)
        if t <= tr[0][0]:
            return quat_zyx(*tr[0][1])
        for (t0, a, _), (t1, b, e) in zip(tr, tr[1:]):
            if t <= t1:
                k = ease(e, (t - t0) / max(1e-6, t1 - t0))
                return quat_zyx(*[p + (q - p) * k for p, q in zip(a, b)])
        return quat_zyx(*tr[-1][1])

    def speeds(self, bone, step=10):
        """Degrees per second, sampled every {step} ms."""
        out, prev = [], self.rot(bone, 0)
        t = step
        while t <= self.duration:
            q = self.rot(bone, t)
            out.append((t, angle(prev, q) * 1000 / step))
            prev, t = q, t + step
        return out

    def travel(self, bones, t0=0, t1=None, step=10):
        t1 = self.duration if t1 is None else t1
        total, t, prev = 0.0, t0 + step, {b: self.rot(b, t0) for b in bones}
        while t <= t1:
            for b in bones:
                q = self.rot(b, t)
                total += angle(prev[b], q)
                prev[b] = q
            t += step
        return total


def score(c):
    """{principle: (passed, note)}; principles that don't apply to the clip are left out."""
    res = {}
    moving = [b for b in LIMBS if b in c.tracks]
    # (A blow held on its last pose until the next one replaces it is judged as a blow.)
    if (c.hold or c.loop) and not (c.hold and c.contact is not None):
        # A held pose should keep a little life in it.
        if c.loop:
            amp = c.travel(list(c.tracks), c.loop_start, c.loop_end)
            res['alive'] = (amp > 6, f'{amp:.0f}° of motion per loop')
        else:
            res['alive'] = (False, 'held on its last key: frozen')
        return res
    if not moving or c.duration < 150:
        return res
    if c.transition:
        core, legs, arms = c.travel(CORE), c.travel(LEGS), c.travel(ARMS)
        share = (core + legs) / max(1e-6, core + legs + arms)
        res['whole body'] = (share >= 0.35, f'{share:.0%} of the motion in hips/chest/head/legs')
        return res
    # The striking limb: the one that moves fastest; the contact: its marker, or that limb's peak.
    peak = {b: max(c.speeds(b), key=lambda p: p[1]) for b in moving}
    if c.contact is not None:
        # The striking limb is the one moving fastest into the contact (not an arm flailing elsewhere in a kick).
        near = {b: max([s for t, s in c.speeds(b) if c.contact - 100 <= t <= c.contact + 20] or [0]) for b in moving}
        strike = max(near, key=near.get)
        contact = c.contact
    else:
        strike = max(peak, key=lambda b: peak[b][1])
        contact = peak[strike][0]
    hit_pose = c.rot(strike, contact)
    # Anticipation: the limb gets farther from its contact pose for a while before closing in.
    dist = [(t, angle(c.rot(strike, t), hit_pose)) for t in range(0, int(contact) + 1, 10)]
    away, best = 0, 0
    for (t0, d0), (t1, d1) in zip(dist, dist[1:]):
        away = away + (t1 - t0) if d1 > d0 + 0.2 else 0
        best = max(best, away)
    ok = best >= 60 or (dist and max(d for _, d in dist) > dist[0][1] + 15)
    note = f'{best} ms winding away ({strike})'
    if not ok and c.windup:
        # Wound in the clip before: its last pose should be at least as far from the contact as this clip's first.
        prev_path = os.path.join(os.path.dirname(c.path), c.windup + '.json')
        if os.path.exists(prev_path):
            prev = Clip(prev_path)
            end = prev.loop_end if prev.loop else prev.duration
            gap_prev, gap_here = angle(prev.rot(strike, end), hit_pose), dist[0][1] if dist else 0
            # The blow starts from a wound pose (if the clip before stopped short of it, the blend in pulls the limb
            # back the rest of the way first: anticipation all the same).
            ok = gap_here > 30
            note = f'wound in {c.windup}: {gap_prev:.0f}° from contact there, {gap_here:.0f}° here'
    res['anticipation'] = (ok, note)
    # Hit-stop: the striking limb all but stops for 40+ ms right after contact.
    sp = [s for t, s in c.speeds(strike) if contact < t <= contact + 90]
    approach = max([s for t, s in c.speeds(strike) if t <= contact] or [1])
    slow = sum(10 for s in sp if s < approach * 0.25)
    res['hit-stop'] = (slow >= 40, f'{slow} ms held after contact')
    # Settle: a pose between the contact and the return to guard.
    after = sorted({t for tr in c.tracks.values() for t, _, _ in tr if contact + 20 < t < c.duration - 20})
    res['settle'] = (len(after) >= 1, f'{len(after)} key(s) between contact and the end')
    # Overlap: hips -> chest -> arm -> forearm peak in that order, spread over 30+ ms.
    # (A bone that hardly moves has no peak worth ordering: it's left out.)
    # (A bone that hardly moves has no peak worth ordering: it's left out.)
    top = max([s for t, s in c.speeds(strike) if t <= contact + 60] or [1])
    chain = [b for b in ('hips', 'chest', strike, CHILD.get(strike)) if b and b in c.tracks and c.travel([b], 0, contact + 60) > 10
             and max([s for t, s in c.speeds(b) if t <= contact + 60] or [0]) >= 0.3 * top]
    times = [max([p for p in c.speeds(b) if p[0] <= contact + 60] or [(0, 0)], key=lambda p: p[1])[0] for b in chain]
    # 20+ ms from first to last: a frame or more of lead at 50-60 fps.
    ordered = all(a <= b for a, b in zip(times, times[1:])) and (times[-1] - times[0] >= 20 if len(times) > 1 else False)
    if len(chain) > 1:  # (one bone moving alone has no chain to order: not applicable)
        res['overlap'] = (ordered, ' > '.join(f'{b}@{t}' for b, t in zip(chain, times)))
    # Whole body: the core and legs carry a fair share.
    core, legs, arms = c.travel(CORE), c.travel(LEGS), c.travel(ARMS)
    share = (core + legs) / max(1e-6, core + legs + arms)
    need = 0.20 if c.effortless else 0.35
    res['whole body'] = (share >= need, f'{share:.0%} of the motion in hips/chest/head/legs (needs {need:.0%})')
    return res


def main(args):
    paths = []
    for a in args or ['hakari']:
        if os.path.isdir(os.path.join(ROOT, a)):
            paths += sorted(glob.glob(os.path.join(ROOT, a, '*.json')))
        else:
            paths.append(os.path.join(ROOT, a + ('' if a.endswith('.json') else '.json')))
    totals = [0, 0]
    for p in paths:
        try:
            c = Clip(p)
        except Exception as e:  # pose references and other formats this reader doesn't follow
            print(f'{os.path.basename(p)[:-5]:<22} (skipped: {e})')
            continue
        r = score(c)
        passed = sum(1 for ok, _ in r.values() if ok)
        totals[0] += passed
        totals[1] += len(r)
        marks = '  '.join(f"{'+' if ok else '-'}{k}" for k, (ok, _) in r.items())
        print(f'{c.name:<22} {c.duration:>5}ms {c.keys:>2}k  {passed}/{len(r)}  {marks}')
        for k, (ok, note) in r.items():
            if not ok:
                print(f'{"":<34}- {k}: {note}')
    print(f'\nprinciples met: {totals[0]}/{totals[1]}' + (f' ({totals[0] / totals[1]:.0%})' if totals[1] else ''))


if __name__ == '__main__':
    main(sys.argv[1:])
