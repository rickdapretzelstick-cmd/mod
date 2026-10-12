#!/usr/bin/env python3
"""
Every clip of the four Grade 1 guardian bodies (their models come from tools/install_guardians.py):

    python3 tools/gen_guardian_anims.py [body ...]

Bodies: claw_hunter, pale_stalker, cyclops_bruiser, veiled_reacher (all when none are given). Writes
animations/guardian_<body>/guardian_<body>_<clip>.json, rig guardian_<body>: one set per body, shared by its three looks
(they have the same skeleton: hips > torso > head > jaw, upper arm > forearm > hand > fingers, thigh > shin > foot, and
hair or veil strips of their own).

Each body fights with the moves of the curse it stood in for (GraveHoundEntity, ForestStalkerEntity, SchoolMawEntity,
VeiledBrideEntity), so its clips carry those names and hit on the same ticks (1 tick = 50 ms), redrawn for a humanoid:
the hound's bite is a claw slash, the maw's bite a hammer blow. "unleash" is the phase beat (GuardianPhases.BEAT_TICKS).

Conventions are the mod's animator's (BbModel.apply, as in tools/gen_curse_models.py): rot [x, y, z] degrees added to the
rest pose; x + leans a torso or nods a head forward and swings a hanging limb back (x -90 raises an arm to the front);
z + rolls the +x (the creature's left) side up, so an arm goes out to the side with z + on the left and z - on the right
(arm() and arms() take the left's angles and mirror them; a raised arm's y + turns it outward, y - in across the chest);
pos is pixels, z - toward the front. The models stand hunched; legs are solved (two-bone, in the side plane) so the feet
stay where they are put whatever the hips do.
"""
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gen_curse_models import ASSETS, TAU, ClipBuilder, R, RP, death_clip, grapple_set, hurt_clip, merge, osc, sin, stagger_clip, write_clips  # noqa: E402

SIDES = ('right', 'left')
CORE = re.compile(r'^(root|hips|torso|head|jaw|(right|left)_(upper_arm|forearm|hand|hand_finger_\d(_tip)?|hand_thumb|thigh|shin|foot))$')
# Fingers close toward the palm with x +.
CURL = 1


def mirror(side, x, y, z):
    """A pose given for the left side, mirrored for the right (y and z flip)."""
    return (x, y, z) if side == 'left' else (x, -y, -z)


class Body:
    def __init__(self, name):
        self.kind = 'guardian_' + name
        bones, self.hair = {}, set()
        for v in range(3):
            path = os.path.join(ASSETS, 'models', 'bb', '%s_%d.bbmodel' % (self.kind, v))
            m = json.load(open(path))

            def walk(n):
                if isinstance(n, str):
                    return
                bones.setdefault(n['name'], n['origin'])
                if not CORE.match(n['name']):
                    self.hair.add(n['name'])
                for c in n['children']:
                    walk(c)
            for n in m['outliner']:
                walk(n)
        self.hair = sorted(self.hair)
        self.bones = set(bones)
        self.legs = {}
        for s in SIDES:
            h, k, a = (bones[s + b] for b in ('_thigh', '_shin', '_foot'))
            self.legs[s] = ((h[1], h[2]), (k[1], k[2]), (a[1], a[2]))
        self.fingers = {s: sorted(b for b in bones if b.startswith(s + '_hand_finger_') and not b.endswith('_tip')) for s in SIDES}

    # --- the parts of a pose ---

    def rest(self):
        out = {'hips': RP([0, 0, 0], [0, 0, 0]), 'torso': R(), 'head': R(), 'jaw': R()}
        for s in SIDES:
            for b in ('_upper_arm', '_forearm', '_hand', '_thigh', '_shin', '_foot', '_hand_thumb'):
                out[s + b] = R()
            for f in self.fingers[s]:
                out[f] = R()
                out[f + '_tip'] = R()
        for b in self.hair:
            out[b] = R()
        return out

    def stand(self, dy=0.0, dz=0.0, lean=0.0, twist=0.0, roll=0.0, dx=0.0, feet=None):
        """
        The hips (lowered dy, moved dz, pitched forward by lean, turned by twist, rolled) with both feet planted:
        feet = {side: (up, toward the back, toe pitch)} moves a foot from where it stands.
        """
        out = {'hips': RP([lean, twist, roll], [dx, dy, dz])}
        feet = feet or {}
        for s in SIDES:
            (hy, hz), (ky, kz), (ay, az) = self.legs[s]
            l1, l2 = math.hypot(ky - hy, kz - hz), math.hypot(ay - ky, az - kz)
            fy, fz, fp = (tuple(feet.get(s, ())) + (0, 0, 0))[:3]
            hy2, hz2, ay2, az2 = hy + dy, hz + dz, ay + fy, az + fz
            d = min(max(math.hypot(ay2 - hy2, az2 - hz2), abs(l1 - l2) + 0.01), (l1 + l2) * 0.999)
            # Angles from straight down, + toward the front (-z); the knee stays forward of the hip-ankle line.
            to_ankle = math.atan2(-(az2 - hz2), -(ay2 - hy2))
            bend = math.acos(max(-1.0, min(1.0, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d))))
            thigh = to_ankle + bend
            ky2, kz2 = hy2 - l1 * math.cos(thigh), hz2 - l1 * math.sin(thigh)
            shin = math.atan2(-(az2 - kz2), -(ay2 - ky2))
            dt = math.degrees(thigh - math.atan2(-(kz - hz), -(ky - hy)))
            ds = math.degrees(shin - math.atan2(-(az - kz), -(ay - ky)))
            out[s + '_thigh'] = R(-dt - lean)
            out[s + '_shin'] = R(dt - ds)
            out[s + '_foot'] = R(ds + fp)
        return out

    def legs_free(self, thigh=(0, 0), shin=(0, 0), foot=(0, 0)):
        """Leg angles set directly (right, left), for when the feet leave the ground."""
        out = {}
        for i, s in enumerate(SIDES):
            out[s + '_thigh'] = R(thigh[i])
            out[s + '_shin'] = R(shin[i])
            out[s + '_foot'] = R(foot[i])
        return out

    def claw(self, k, side=None):
        """Fingers: k 1 hooked shut, 0 at rest, below 0 splayed open."""
        out = {}
        for s in ([side] if side else SIDES):
            for f in self.fingers[s]:
                out[f] = R(CURL * 38 * k)
                out[f + '_tip'] = R(CURL * 46 * k)
            out[s + '_hand_thumb'] = R(CURL * 20 * k)
        return out

    def locks(self, ph, amp=6.0, x=0.0, z=0.0, k=1.0):
        """Hair and veil strips: an offset (x + swings them back) and a ripple running along them (ph = phase)."""
        out = {}
        for i, b in enumerate(self.hair):
            j = i * 0.37
            out[b] = R(x + amp * math.sin(TAU * (ph * k) + j), 0, z + amp * 0.6 * math.sin(TAU * (ph * k) + j * 1.7 + 1.1))
        return out


def write(B, clips):
    """Writes a body's clips, leaving out any bone its models don't have (the veiled reacher has no jaw)."""
    for c in clips.values():
        c['keys'] = [(t, {b: ch for b, ch in bones.items() if b in B.bones}) for t, bones in c['keys']]
    write_clips(B.kind, clips, 'tools/gen_guardian_anims.py')


def arm(side, x=0, y=0, z=0, fore=0, hand=0):
    """One arm, given as for the left (z + out to the side); mirrored for the right."""
    return {side + '_upper_arm': R(*mirror(side, x, y, z)), side + '_forearm': R(fore), side + '_hand': R(hand)}


def arms(x=0, z=0, fore=0, hand=0, y=0):
    return merge(arm('right', x, y, z, fore, hand), arm('left', x, y, z, fore, hand))


def look(x=0, y=0, z=0, jaw=0, torso=None):
    out = {'head': R(x, y, z), 'jaw': R(jaw)}
    if torso is not None:
        out['torso'] = R(*torso)
    return out


def gait(B, period, stride, lift, swing, bob, base, extra=lambda ph: {}, steps=16):
    """A walking loop: feet tracing a step (lifted while they come forward), arms swinging against them."""
    c = ClipBuilder(period, 'MOVEMENT', loop=True, blendIn=150, blendOut=150)

    def at(ph):
        feet, s = {}, sin(ph)
        for side, off in (('right', 0.0), ('left', 0.5)):
            u = TAU * (ph + off)
            feet[side] = (lift * max(0.0, math.cos(u)), -stride * math.sin(u), -10 * max(0.0, math.cos(u)))
        b = base(ph)
        pose = merge(B.rest(), b['pose'], B.stand(b.get('dy', 0) - bob * abs(s), 0, b.get('lean', 0), 6 * s, 0, feet=feet),
                     arm('right', swing * s + b.get('ax', 0), 0, b.get('az', 0), b.get('fore', 0) - 8 * max(0.0, -s)),
                     arm('left', -swing * s + b.get('ax', 0), 0, b.get('az', 0), b.get('fore', 0) - 8 * max(0.0, s)),
                     {'torso': R(b.get('torso', 0), -8 * s, 0)}, extra(ph))
        return pose
    osc(c, at, period, steps)
    return c.done()


def idle(B, period, fn, steps=12):
    c = ClipBuilder(period, 'IDLE', loop=True, blendIn=220, blendOut=220)
    osc(c, lambda ph: merge(B.rest(), fn(ph)), period, steps)
    return c.done()


def keys(c, *poses):
    for t, p in poses:
        c.at(t, p)
    return c.done()


# ----------------------------------------------------------------------------------------------------------- claw hunter

def claw_hunter():
    """Grade 4's body: gaunt, long-clawed, a dark fringe. Fights as the Grave Hound: slash, crouch and pounce, howl."""
    B = Body('claw_hunter')

    def stance(dy=-1.0, lean=6, **kw):
        return merge(B.rest(), B.stand(dy, 0, lean, **kw), arms(-12, 10, -14), B.claw(0.3), look(-6, 0, 0, 6))

    S = stance()
    clips = {}
    clips['idle'] = idle(B, 2000, lambda ph: merge(
        stance(-1.0 + 0.35 * sin(ph), 6 + 2 * sin(ph)), {'torso': R(2 * sin(ph, 1, 0.1))},
        look(-6 + 5 * sin(ph, 1, 0.3), 16 * sin(ph), 10 * sin(ph, 1, 0.25), 6 + 8 * max(0.0, sin(ph, 3))),
        arm('right', -12 + 4 * sin(ph), 0, 10, -14 - 6 * sin(ph, 2)), arm('left', -12 - 4 * sin(ph), 0, 10, -14 + 6 * sin(ph, 2)),
        B.claw(0.3 + 0.35 * max(0.0, sin(ph, 2)), 'right'), B.claw(0.3 + 0.35 * max(0.0, sin(ph, 2, 0.5)), 'left'),
        B.locks(ph, 4)), 16)
    # A loping run, bent low, claws trailing.
    clips['move'] = gait(B, 640, 4.6, 2.6, 34, 0.8, lambda ph: {
        'pose': merge(look(-18, 0, 6 * sin(ph), 18), B.claw(0.5), B.locks(ph, 5, 18, 0, 2)),
        'dy': -1.4, 'lean': 16, 'torso': 6, 'ax': 6, 'az': 12, 'fore': -18})

    # bite: a claw slash, landing on BITE_HIT (6 ticks).
    wind = merge(stance(-1.6, 0, twist=-14, feet={'right': (0, 2.5)}), arm('right', -150, 0, 34, -40), arm('left', -30, 0, 16, -20),
                 B.claw(-0.3, 'right'), look(-14, 10, 0, 22), {'torso': R(-6, -18, 0)}, B.locks(0, 0, 12))
    cut = merge(stance(-2.2, 18, twist=16, feet={'left': (0, -3)}), arm('right', -40, 0, -34, -10), arm('left', 14, 0, 16, -10),
                B.claw(0.7, 'right'), look(-4, -10, 0, 40), {'torso': R(10, 22, 0)}, B.locks(0, 0, -14))
    clips['bite'] = keys(ClipBuilder(600, 'ATTACK', blendIn=50), (0, S), (200, wind), (300, cut),
                         (380, merge(cut, arm('right', -20, 0, -44, -6), {'torso': R(14, 30, 0)})), (600, S))

    # crouch: the pounce's windup (LEAP_WINDUP_TICKS 10), shaking with it.
    low = merge(B.rest(), B.stand(-5.2, 1.0, 26), arms(28, 22, -22), B.claw(-0.4), look(-34, 0, 0, 30), {'torso': R(8)}, B.locks(0, 0, 10))
    c = ClipBuilder(500, 'ATTACK', blendIn=60)
    c.at(0, S)
    c.at(300, low)
    for t, j in ((380, 1), (440, -1), (500, 1)):
        c.at(t, merge(low, {'hips': RP([26, 0, 1.6 * j], [0, -5.2, 1.0])}))
    clips['crouch'] = c.done()
    # attack: the pounce (LEAP), claws first.
    leap = merge(B.rest(), {'hips': RP([34, 0, 0], [0, 1.2, -1.5])}, B.legs_free((24, 4), (40, 20), (20, 10)),
                 arms(-160, 14, -10), B.claw(-0.5), look(-40, 0, 0, 55), {'torso': R(6)}, B.locks(0, 0, 34))
    clips['attack'] = keys(ClipBuilder(600, 'ATTACK', blendIn=30), (0, low), (100, leap),
                           (420, merge(leap, arms(-110, 14, -24), B.claw(0.6), {'hips': RP([24, 0, 0], [0, 0.6, -1.5])})),
                           (600, merge(stance(-2.4, 16), look(-20, 0, 0, 24))))

    # stumble: the pounce missed. Down on hands and knees, clawing at the ground (STUMBLE_TICKS 26), then up.
    def sprawl(ph):
        f = sin(ph, 3)
        return merge(B.rest(), B.stand(-7.5, -1.0, 50), arm('right', -58 + 18 * f, 0, 18, -10), arm('left', -58 - 18 * f, 0, 18, -10),
                     B.claw(0.8), look(-38, 18 * f, 10 * f, 32 + 10 * f), {'torso': R(10, 0, 4 * f)}, B.locks(ph, 6, -16))
    c = ClipBuilder(1300, 'ATTACK', blendIn=60)
    for k in range(8):
        c.at(k * 150, sprawl(k / 7))
    c.at(1300, merge(stance(-3, 22), look(-26, 0, 0, 20)))
    clips['stumble'] = c.done()

    # howl: gathers itself, then throws its head back (HOWL_AT 18 ticks) and holds it.
    gather = merge(stance(-2.4, 26), arms(-40, -26, -50), B.claw(0.9), look(30, 0, 0, 8), B.locks(0, 0, -10))
    howl = merge(stance(-0.4, -10), arms(34, 58, -50), B.claw(-0.6), look(-46, 0, 0, 58), {'torso': R(-20)}, B.locks(0, 0, 30))
    c = ClipBuilder(1200, 'ATTACK', blendIn=80)
    c.at(0, S)
    c.at(420, gather)
    c.at(900, howl)
    for k, t in enumerate(range(960, 1201, 60)):
        j = 1 if k % 2 else -1
        c.at(t, merge(howl, look(-46 + 2 * j, 0, 3 * j, 58 + 4 * j), arms(34 + 2 * j, 58 - 3 * j, -50)))
    clips['howl'] = c.done()

    # unleash (a phase beat): it goes still, bent over and limp, then rears up screaming and drops into the hunt.
    limp = merge(stance(-4, 36), arms(-20, 4, -4), B.claw(0.1), look(34, 0, 10, 10), {'torso': R(10)}, B.locks(0, -20, 0))
    rear = merge(stance(0.4, -16), arms(10, 82, -40), B.claw(-0.8), look(-52, 0, 0, 62), {'torso': R(-22)}, B.locks(0, 0, 40))
    hunt = merge(stance(-3.2, 22), arms(-44, 26, -30), B.claw(0.7), look(-24, 0, 0, 26), B.locks(0, 0, 8))
    c = ClipBuilder(2500, 'SPECIAL', blendIn=80, blendOut=240)
    c.at(0, S)
    c.at(350, limp)
    c.at(800, merge(limp, look(38, 0, -8, 6)))
    c.at(1100, rear)
    for k, t in enumerate(range(1180, 1901, 80)):
        j = 1 if k % 2 else -1
        c.at(t, merge(rear, look(-52 + 2 * j, 4 * j, 2 * j, 62 + 5 * j), {'hips': RP([-16, 0, 1.2 * j], [0, 0.4, 0])}))
    c.at(2200, hunt)
    c.at(2500, hunt)
    clips['unleash'] = c.done()

    clips['hurt'] = hurt_clip(S, merge(B.stand(-0.6, 1.2, -12, twist=12), look(-24, 0, 14, 34), arm('right', -40, 0, 34, -30), arm('left', -20, 0, 40, -20),
                                       B.claw(-0.4), B.locks(0, 0, 20)))
    clips['stagger'] = stagger_clip(S, lambda k, s: merge(
        B.stand(-1 - 1.6 * k, 1.4 * k, -18 * k, roll=14 * s * k, feet={'right': (0, 2.4 * s * k)}),
        look(-30 * k, 22 * s * k, -12 * s * k, 44 * k), arms(-60 * k, 44 * k, -26 * k), B.claw(-0.5 * k), {'torso': R(-10 * k, 0, 10 * s * k)},
        B.locks(0, 0, 24 * k, 10 * s * k)))
    clips['death'] = death_clip(S, merge(B.stand(-6, 0.5, 14), look(-34, 0, 0, 46), arms(-100, 40, -20), B.claw(-0.6), B.locks(0, 0, 20)),
                                merge({'hips': RP([84, 0, 6], [0, -10.5, 0])}, B.legs_free((-6, 4), (14, 20), (10, 0)), arms(-26, 48, -10),
                                      B.claw(0.4), look(-10, 54, 0, 44), {'torso': R(0)}, B.locks(0, 30, 0)))
    # Strength contests: its claws in your shoulders, its face in yours.
    clips.update(grapple_set(
        S,
        merge(B.stand(-1.8, 0, 16, feet={'right': (0, 3)}), arms(-86, -10, -30), B.claw(0.8), look(-14, 0, 0, 36)),
        merge(B.stand(-1.0, 1.6, 0, feet={'right': (0, 3)}), arms(-72, -6, -12), look(-26, 0, 8, 46), B.locks(0, 0, 14)),
        merge(B.stand(-3.0, -0.6, 34, feet={'right': (0, 3.6)}), arms(-104, -12, -46), B.claw(1.0), look(-2, 0, 0, 52), B.locks(0, 0, -10)),
        merge(B.stand(-0.6, 0, -16, twist=-24), arms(-162, 12, -20), look(-16, 0, 0, 22), {'torso': R(-10, -20, 0)}),
        merge(B.stand(-2.4, 0, 34, twist=30), arm('right', -40, 0, 40, -8), arm('left', -60, 0, 30, -8), look(-8, 0, 0, 50), {'torso': R(14, 26, 0)})))
    write(B, clips)


# ----------------------------------------------------------------------------------------------------------- pale stalker

def pale_stalker():
    """Grade 3's body: pale hair over a split face. Fights as the Forest Stalker: the long telegraph, the lunge, rakes."""
    B = Body('pale_stalker')

    def stance(dy=-0.6, lean=4, **kw):
        return merge(B.rest(), B.stand(dy, 0, lean, **kw), arms(-6, 5, -10), B.claw(0.2), look(-4, 0, 12, 4))

    S = stance()
    clips = {}
    # Still but never at rest: the head ticks from one tilt to the other, the fingers working.
    clips['idle'] = idle(B, 2400, lambda ph: merge(
        stance(-0.6 + 0.3 * sin(ph), 4 + 2 * sin(ph)), {'torso': R(2 * sin(ph), 0, 2 * sin(ph, 1, 0.2))},
        look(-4 + 4 * sin(ph, 1, 0.3), 12 * sin(ph), 18 * (1 if sin(ph, 1, 0.25) > 0 else -1) * min(1.0, 3 * abs(sin(ph, 1, 0.25))), 4 + 6 * max(0.0, sin(ph, 2))),
        B.claw(0.2 + 0.4 * max(0.0, sin(ph, 4)), 'right'), B.claw(0.2 + 0.4 * max(0.0, sin(ph, 3, 0.3)), 'left'), B.locks(ph, 4)), 16)
    clips['move'] = gait(B, 900, 4.2, 2.2, 18, 0.6, lambda ph: {
        'pose': merge(look(-12, -6 * sin(ph), 14, 8), B.claw(0.3), B.locks(ph, 4, 10, 0, 2)),
        'dy': -1.0, 'lean': 10, 'torso': 4, 'ax': -4, 'az': 6, 'fore': -12})

    # telegraph (TELEGRAPH_TICKS 22): it rises, head turning on its side, then sinks low and locks on (LOCK_AT 16), shaking.
    tall = merge(stance(0.6, -8), arms(-10, 30, -6), B.claw(-0.3), look(-10, 0, 30, 12), B.locks(0, 0, 0, 10))
    low = merge(B.rest(), B.stand(-4.2, 0.8, 26), arms(26, 26, -22), B.claw(-0.5), look(-30, 0, 6, 26), {'torso': R(6)}, B.locks(0, 0, 12))
    c = ClipBuilder(1100, 'ATTACK', blendIn=80)
    c.at(0, S)
    c.at(250, tall)
    c.at(800, low)
    for t, j in ((880, 1), (960, -1), (1040, 1), (1100, 0)):
        c.at(t, merge(low, {'hips': RP([26, 0, 1.6 * j], [0, -4.2, 0.8])}))
    clips['telegraph'] = c.done()
    # lunge (LUNGE_TICKS 14): a dive, both claws out ahead of it.
    dive = merge(B.rest(), {'hips': RP([36, 0, 0], [0, 0.2, -2])}, B.legs_free((20, -10), (34, 14), (14, 6)), arms(-118, 10, 6), B.claw(-0.4),
                 look(-38, 0, 0, 40), B.locks(0, 0, 36))
    clips['lunge'] = keys(ClipBuilder(700, 'ATTACK', blendIn=40), (0, low), (120, dive),
                          (500, merge(dive, {'hips': RP([38, 0, 0], [0, 0.4, -2])}, B.locks(0, 0, 28))),
                          (700, merge(dive, {'hips': RP([44, 0, 0], [0, -1, -2])}, arms(-84, 10, -10), B.claw(0.6))))

    # exposed (EXPOSED_TICKS 44): it crashed down after a missed lunge, twitching on its knees and hands.
    def crashed(ph):
        tw = sin(ph, 3)
        return merge(B.rest(), B.stand(-8, -1.2, 54), arm('right', -60, 0, 30 + 8 * tw, -20), arm('left', -52, 0, 24, -14 + 10 * tw),
                     B.claw(0.9), look(-30, 28, 22 + 6 * tw, 26 + 8 * tw), {'torso': R(8, 0, 6 * tw)}, B.locks(ph, 5, -24))
    c = ClipBuilder(2200, 'ATTACK', blendIn=60)
    for k in range(10):
        c.at(k * 200, crashed(k / 9))
    c.at(2200, merge(stance(-3, 22), look(-20, 0, 14, 10)))
    clips['exposed'] = c.done()
    # rake (RAKE_HIT 9, RAKE_END 16): an overhead claw from the right, the left following through.
    up = merge(stance(0.6, -10, twist=-16), arm('right', -172, 0, 30, -30), arm('left', -30, 0, 20, -10), B.claw(-0.5, 'right'),
               look(-12, 10, 10, 30), {'torso': R(-12, -20, 0)}, B.locks(0, 0, 16))
    down = merge(stance(-1.6, 24, twist=18, feet={'left': (0, -3)}), arm('right', -46, 0, -24, 10), arm('left', -100, 0, 10, -20),
                 B.claw(0.8, 'right'), look(-16, -10, 10, 14), {'torso': R(14, 24, 0)}, B.locks(0, 0, -16))
    back = merge(stance(-1.6, 20, twist=-14, feet={'left': (0, -3)}), arm('right', -40, 0, 10, -10), arm('left', -40, 0, -30, 10),
                 B.claw(0.8), look(-14, 10, 10, 12), {'torso': R(12, -22, 0)})
    clips['rake'] = keys(ClipBuilder(800, 'ATTACK', blendIn=60), (0, S), (330, up), (450, down), (560, back), (800, S))
    # retreat (BREAK_TICKS 20): skulking back out of reach, eyes still on you.
    c = ClipBuilder(1000, 'ATTACK', blendIn=80)
    for k in range(5):
        ph = k / 4
        c.at(k * 250, merge(B.rest(), B.stand(-1.8, 0.4 * sin(ph), 10, feet={'right': (1.2 * max(0, sin(ph)), 2.4 * sin(ph)), 'left': (1.2 * max(0, -sin(ph)), -2.4 * sin(ph))}),
                            arm('right', -16 + 14 * sin(ph), 0, 8, -16), arm('left', -16 - 14 * sin(ph), 0, 8, -16), B.claw(0.5), look(-18, 24, 16, 10), B.locks(ph, 4, 6)))
    clips['retreat'] = c.done()

    # unleash: its chin drops to its chest, then the head snaps up on its side and the arms open out high; a scream.
    sag = merge(stance(-1.8, 18), arms(-4, 2, 0), B.claw(0), look(44, 0, 0, 6), B.locks(0, -24, 0))
    open_ = merge(stance(0.8, -10), arms(-30, 104, -20), B.claw(-0.8), look(-26, 0, 42, 60), {'torso': R(-14)}, B.locks(0, 10, 20))
    hunt = merge(stance(-3.4, 22), arms(-30, 30, -24), B.claw(0.6), look(-26, 0, 20, 30), B.locks(0, 0, 10))
    c = ClipBuilder(2500, 'SPECIAL', blendIn=80, blendOut=240)
    c.at(0, S)
    c.at(500, sag)
    c.at(900, merge(sag, look(46, 0, 6, 4)))
    c.at(1000, merge(open_, look(-26, 0, 50, 30)))
    c.at(1250, open_)
    for k, t in enumerate(range(1330, 2051, 80)):
        j = 1 if k % 2 else -1
        c.at(t, merge(open_, look(-26 + 2 * j, 3 * j, 42 + 3 * j, 60 + 5 * j), arms(-30, 104 + 3 * j, -20)))
    c.at(2300, hunt)
    c.at(2500, hunt)
    clips['unleash'] = c.done()

    clips['hurt'] = hurt_clip(S, merge(B.stand(-0.4, 1.2, -16), look(-22, 0, -16, 30), arm('right', -40, 0, 30, -20), arm('left', -30, 0, 34, -20),
                                       B.claw(-0.4), B.locks(0, 0, 22)))
    clips['stagger'] = stagger_clip(S, lambda k, s: merge(
        B.stand(-0.6 - 1.4 * k, 1.2 * k, -24 * k, roll=16 * s * k, feet={'left': (0, 2.2 * s * k)}),
        look(-24 * k, 20 * s * k, -14 * s * k, 40 * k), arms(-70 * k, 50 * k, -20 * k), B.claw(-0.5 * k), {'torso': R(-8 * k, 0, 12 * s * k)},
        B.locks(0, 0, 22 * k, 10 * s * k)))
    clips['death'] = death_clip(S, merge(B.stand(-5.6, 0.5, 10), look(-30, 0, 20, 40), arms(-110, 40, -20), B.claw(-0.6), B.locks(0, 0, 20)),
                                merge({'hips': RP([84, 0, -6], [0, -10.5, 0])}, B.legs_free((4, -8), (20, 12), (0, 10)), arms(-30, 56, -16),
                                      B.claw(0.3), look(-6, -54, 20, 40), B.locks(0, -30, 0)))
    # Strength contests: hunched over you, both claws on your shoulders, its face tilted into yours.
    clips.update(grapple_set(
        S,
        merge(B.stand(-1.4, 0, 18, feet={'left': (0, 3)}), arms(-90, -10, -30), B.claw(0.8), look(-12, 0, 18, 30)),
        merge(B.stand(-0.8, 1.6, 2, feet={'left': (0, 3)}), arms(-74, -4, -12), look(-24, 0, 26, 44), B.locks(0, 0, 14)),
        merge(B.stand(-2.4, -0.6, 34, feet={'left': (0, 3.6)}), arms(-106, -10, -46), B.claw(1.0), look(-4, 0, 10, 48), B.locks(0, 0, -10)),
        merge(B.stand(-0.4, 0, -14, twist=-24), arms(-160, 14, -20), look(-14, 0, 20, 20), {'torso': R(-8, -20, 0)}),
        merge(B.stand(-1.8, 0, 32, twist=30), arm('right', -30, 0, 40, -8), arm('left', -60, 0, 30, -8), look(-6, 0, 10, 46), {'torso': R(12, 24, 0)})))
    write(B, clips)


# ----------------------------------------------------------------------------------------------------------- cyclops bruiser

def cyclops_bruiser():
    """Grade 2's body: one eye, heavy. Fights as the School Maw: hammer blows, a quick hook, the tongue, the slam."""
    B = Body('cyclops_bruiser')

    def stance(dy=-0.8, lean=4, **kw):
        return merge(B.rest(), B.stand(dy, 0, lean, **kw), arms(-4, 9, -14), B.claw(0.6), look(-4, 0, 0, 4))

    S = stance()
    clips = {}
    # Heavy breathing: the chest heaving, shoulders rolling, the head swinging slowly round.
    clips['idle'] = idle(B, 2600, lambda ph: merge(
        stance(-0.8 + 0.4 * sin(ph), 4 + 1.5 * sin(ph)), {'torso': R(-3 * sin(ph), 0, 2 * sin(ph, 1, 0.3))},
        look(-4 + 3 * sin(ph), 14 * sin(ph, 1, 0.2), 4 * sin(ph, 1, 0.5), 4 + 8 * max(0.0, sin(ph))),
        arm('right', -4 + 3 * sin(ph), 0, 9 + 3 * sin(ph), -14), arm('left', -4 - 3 * sin(ph), 0, 9 + 3 * sin(ph), -14), B.claw(0.6 + 0.3 * max(0.0, sin(ph, 2)))))
    # A lumbering walk, shoulders swinging with each step.
    clips['move'] = gait(B, 1000, 4.0, 2.0, 20, 1.0, lambda ph: {
        'pose': merge(look(-8, -8 * sin(ph), 0, 10), B.claw(0.8)),
        'dy': -1.2, 'lean': 8, 'torso': 4, 'ax': -2, 'az': 12, 'fore': -18}, lambda ph: {'torso': R(4, -10 * sin(ph), -5 * sin(ph))})

    # attack: the full bite timing (BITE_HIT 14, BITE_END 20): both fists over its head, brought down as one.
    raise_ = merge(stance(0.6, -12), arms(-172, -10, -30), B.claw(1.0), look(-18, 0, 0, 30), {'torso': R(-8)})
    smash = merge(stance(-2.6, 30, feet={'left': (0, -3.2)}), arms(-58, -8, -10), B.claw(1.0), look(-26, 0, 0, 40), {'torso': R(10)})
    clips['attack'] = keys(ClipBuilder(1000, 'ATTACK', blendIn=60), (0, S), (450, raise_), (620, merge(raise_, arms(-178, -10, -36))), (700, smash),
                           (800, merge(smash, {'hips': RP([32, 0, 0], [0, -2.9, 0])})), (1000, merge(stance(-1.6, 16), look(-14, 0, 0, 20))))
    # bite_quick: the reeled-in or enraged bite (7-10 ticks): a short hook with the right.
    cock = merge(stance(-1.2, 6, twist=-22), arm('right', 24, 0, 30, -86), arm('left', -30, 0, 10, -40), B.claw(1.0), look(-10, 16, 0, 20), {'torso': R(0, -22, 0)})
    hook = merge(stance(-1.8, 18, twist=24, feet={'left': (0, -2.6)}), arm('right', -96, 0, -4, -12), arm('left', 10, 0, 16, -40), B.claw(1.0),
                 look(-16, -16, 0, 34), {'torso': R(8, 28, 0)})
    clips['bite_quick'] = keys(ClipBuilder(800, 'ATTACK', blendIn=40), (0, S), (220, cock), (350, hook), (500, hook), (800, S))
    # lash (LASH_FIRE 10): it rears and throws its head forward, the tongue flying out of the gaping mouth.
    rear = merge(stance(-0.4, -12), arms(14, 22, -20), look(-22, 0, 0, 18), {'torso': R(-10)})
    throw = merge(stance(-1.6, 22, feet={'left': (0, -2)}), arms(34, 30, -10), B.claw(-0.4), look(-30, 0, 0, 64), {'torso': R(10)})
    clips['lash'] = keys(ClipBuilder(900, 'ATTACK', blendIn=60), (0, S), (380, rear), (500, throw), (760, merge(throw, look(-26, 0, 0, 58))), (900, merge(stance(-1, 10), look(-10, 0, 0, 20))))
    # slam (SLAM_HIT 18): up on its toes with both fists high, then down into the ground around it.
    high = merge(stance(1.2, -16, feet={'right': (0, 0, -16), 'left': (0, 0, -16)}), arms(-176, -4, -20), B.claw(1.0), look(-26, 0, 0, 44), {'torso': R(-10)})
    floor = merge(stance(-4.4, 40), arms(-34, -6, -4), B.claw(1.0), look(-30, 0, 0, 52), {'torso': R(14)})
    clips['slam'] = keys(ClipBuilder(1000, 'ATTACK', blendIn=60), (0, S), (500, high), (800, merge(high, arms(-180, -4, -26))), (900, floor),
                         (1000, merge(floor, {'hips': RP([42, 0, 0], [0, -4.6, 0])})))

    # recover (RECOVER_TICKS 28, or after the slam): bent over with its hands on its knees, panting, mouth hanging open.
    def pant(ph):
        return merge(stance(-2.6 + 0.4 * sin(ph, 4), 32), arms(-26, 8, -34), B.claw(0.3), look(-24, 0, 0, 30 + 8 * sin(ph, 4)), {'torso': R(4 + 3 * sin(ph, 4))})
    c = ClipBuilder(1400, 'ATTACK', blendIn=80)
    for k in range(8):
        c.at(k * 150, pant(k / 8))
    c.at(1400, S)
    clips['recover'] = c.done()

    # unleash: fists flexed up beside its head, pounding its chest, then the roar leaning into you.
    flex = merge(stance(0, -10), arms(-10, 86, -110), B.claw(1.0), look(-30, 0, 0, 52), {'torso': R(-12)})
    roar = merge(stance(-2, 20), arms(0, 40, -22), B.claw(0.6), look(-28, 0, 0, 64), {'torso': R(6)})
    c = ClipBuilder(2500, 'SPECIAL', blendIn=80, blendOut=240)
    c.at(0, S)
    c.at(350, flex)
    for k, t in enumerate(range(600, 1601, 140)):
        side = 'right' if k % 2 else 'left'
        c.at(t, merge(flex, arm(side, -60, 0, -26, -100), look(-24, 0, 0, 44)))
        c.at(t + 70, flex)
    c.at(1900, roar)
    for k, t in enumerate(range(1980, 2501, 80)):
        j = 1 if k % 2 else -1
        c.at(t, merge(roar, look(-28 + 2 * j, 3 * j, 2 * j, 64 + 4 * j)))
    clips['unleash'] = c.done()

    clips['hurt'] = hurt_clip(S, merge(B.stand(-0.6, 1.0, -10, twist=10), look(-20, 0, 12, 30), arms(-30, 26, -30)))
    clips['stagger'] = stagger_clip(S, lambda k, s: merge(
        B.stand(-0.8 - 1.4 * k, 1.2 * k, -14 * k, roll=12 * s * k, feet={'right': (0, 2.4 * s * k)}),
        look(-24 * k, 20 * s * k, -10 * s * k, 40 * k), arms(-50 * k, 40 * k, -30 * k), {'torso': R(-8 * k, 0, 10 * s * k)}))
    clips['death'] = death_clip(S, merge(B.stand(-6, 0.5, 12), look(-30, 0, 0, 44), arms(-80, 30, -30)),
                                merge({'hips': RP([84, 0, 4], [0, -10, 0])}, B.legs_free((-4, 6), (16, 12), (6, 0)), arms(-24, 44, -16),
                                      B.claw(0.6), look(-8, 50, 0, 40)))
    # Strength contests: both hands round your shoulders, its weight coming down on you.
    clips.update(grapple_set(
        S,
        merge(B.stand(-1.8, 0, 14, feet={'right': (0, 3.2)}), arms(-84, -14, -34), B.claw(1.0), look(-14, 0, 0, 30)),
        merge(B.stand(-1.0, 1.8, -2, feet={'right': (0, 3.2)}), arms(-70, -8, -16), look(-24, 0, 8, 40)),
        merge(B.stand(-3.2, -0.6, 32, feet={'right': (0, 3.8)}), arms(-102, -16, -50), B.claw(1.0), look(-4, 0, 0, 50)),
        merge(B.stand(-0.4, 0, -16, twist=-26), arms(-166, 10, -24), look(-16, 0, 0, 30), {'torso': R(-10, -22, 0)}),
        merge(B.stand(-2.6, 0, 36, twist=32), arm('right', -34, 0, 44, -8), arm('left', -60, 0, 30, -8), look(-8, 0, 0, 54), {'torso': R(14, 28, 0)})))
    write(B, clips)


# ----------------------------------------------------------------------------------------------------------- veiled reacher

def veiled_reacher():
    """Grade 1's body: veiled, long-armed. Fights as the Veiled Bride: it flickers, grasps, wails, and has to breathe."""
    B = Body('veiled_reacher')

    def stance(dy=0.0, lean=-2, **kw):
        return merge(B.rest(), B.stand(dy, 0, lean, **kw), arms(0, 3, -4), B.claw(0.1), look(0, 0, 8, 2))

    S = stance()
    clips = {}
    # Drifting: the body swaying, veils and hair moving on their own.
    clips['idle'] = idle(B, 3000, lambda ph: merge(
        stance(0.4 * sin(ph, 2), -2, roll=2 * sin(ph)), look(4 * sin(ph, 1, 0.3), 6 * sin(ph), 10 + 10 * sin(ph, 1, 0.2), 2 + 4 * max(0.0, sin(ph, 2))),
        arm('right', 3 * sin(ph), 0, 3, -4 - 4 * sin(ph, 2)), arm('left', -3 * sin(ph), 0, 3, -4 + 4 * sin(ph, 2)),
        B.claw(0.1 + 0.3 * max(0.0, sin(ph, 4)), 'right'), B.claw(0.1 + 0.3 * max(0.0, sin(ph, 3, 0.3)), 'left'), B.locks(ph, 5)), 16)
    clips['move'] = gait(B, 1500, 3.0, 1.2, 6, 0.3, lambda ph: {
        'pose': merge(look(-6, 0, 14, 2), B.locks(ph, 5, 14, 0, 2)),
        'dy': -0.2, 'lean': 8, 'torso': 0, 'ax': 14, 'az': 4, 'fore': 8})

    # flicker (FLICKER_TICKS 16): jerking between two shapes faster and faster as it slips behind you.
    c = ClipBuilder(800, 'ATTACK', blendIn=60)
    c.at(0, S)
    for k, t in enumerate(range(100, 801, 50)):
        j = 1 if k % 2 else -1
        u = min(1.0, t / 600)
        c.at(t, merge(stance(0.8 * u, -2, twist=4 * j * u, roll=4 * j * u), arms(-10 * u, 46 * u, -20 * u), B.claw(-0.6 * u),
                      look(-10 * u, 10 * j * u, 30 * u * j, 30 * u), B.locks(0, -20 * u, 14 * j)))
    clips['flicker'] = c.done()
    # grasp (GRASP_HIT 8): the long arms reach out and close.
    reach = merge(stance(-0.4, 14), arms(-112, 28, -8), B.claw(-0.6), look(-10, 0, 0, 20), B.locks(0, 0, 10))
    clamp = merge(stance(-1.0, 22, feet={'left': (0, -2)}), arms(-84, -14, -20, 0, -16), B.claw(1.0), look(-14, 0, 0, 30), B.locks(0, 0, -6))
    clips['grasp'] = keys(ClipBuilder(700, 'ATTACK', blendIn=40), (0, S), (330, reach), (400, clamp), (520, clamp), (700, S))
    # wail (WAIL_HIT 14): it draws back, head thrown up and arms wide, then screams forward.
    draw = merge(stance(0.6, -10), arms(20, 70, -10), B.claw(-0.4), look(-40, 0, 0, 30), {'torso': R(-14)}, B.locks(0, 20, 0))
    scream = merge(stance(-1, 16), arms(-30, 82, -10), B.claw(-0.8), look(-6, 0, 0, 64), {'torso': R(14)}, B.locks(0, 0, 30))
    clips['wail'] = keys(ClipBuilder(900, 'ATTACK', blendIn=60), (0, S), (600, draw), (700, scream), (820, merge(scream, look(-8, 0, 4, 60))),
                         (900, merge(stance(-0.6, 12), arms(-20, 60, -10), look(0, 0, 0, 30))))

    # breath (BREATH_TICKS 30): spent after the wail, it hunches over, heaving.
    def hunch(ph):
        return merge(stance(-1.2 + 0.5 * sin(ph, 2), 10), arms(26, 4, -10), B.claw(0.2), look(30, 0, 18, 10 + 6 * sin(ph, 2)),
                     {'torso': R(30 + 3 * sin(ph, 2))}, B.locks(ph, 3, -14))
    c = ClipBuilder(1500, 'ATTACK', blendIn=80)
    for k in range(7):
        c.at(k * 250, hunch(k / 6))
    clips['breath'] = c.done()

    # unleash: it rises, arms lifting out until they are high and wide, veils flaring; the wail; then the reach.
    rise = merge(stance(1.0, -6), arms(-24, 54, -34), B.claw(-0.2), look(-20, 0, 0, 10), B.locks(0, 16, 0))
    flare = merge(stance(1.4, -10), arms(-30, 116, -26), B.claw(-0.9), look(-40, 0, 0, 64), {'torso': R(-14)}, B.locks(0, 30, 10))
    reach = merge(stance(-0.6, 16), arms(-84, 18, -10), B.claw(0.7), look(-14, 0, 10, 30), B.locks(0, 0, 10))
    c = ClipBuilder(2500, 'SPECIAL', blendIn=80, blendOut=240)
    c.at(0, S)
    c.at(600, rise)
    c.at(1200, flare)
    for k, t in enumerate(range(1280, 2001, 80)):
        j = 1 if k % 2 else -1
        c.at(t, merge(flare, look(-40 + 2 * j, 4 * j, 3 * j, 64 + 4 * j), B.locks(0, 30 + 6 * j, 10 * j)))
    c.at(2250, reach)
    c.at(2500, reach)
    clips['unleash'] = c.done()

    clips['hurt'] = hurt_clip(S, merge(B.stand(-0.4, 1.0, -14), look(-20, 0, -14, 30), arm('right', -30, 0, 30, -10), arm('left', -20, 0, 26, -10),
                                       B.claw(-0.5), B.locks(0, 0, 20)))
    clips['stagger'] = stagger_clip(S, lambda k, s: merge(
        B.stand(-1.2 * k, 1.2 * k, -20 * k, roll=14 * s * k, feet={'left': (0, 2.2 * s * k)}),
        look(-24 * k, 0, -20 * s * k, 40 * k), arms(-60 * k, 52 * k, -10 * k), B.claw(-0.6 * k), {'torso': R(-10 * k, 0, 12 * s * k)},
        B.locks(0, 0, 26 * k, 12 * s * k)))
    clips['death'] = death_clip(S, merge(B.stand(-5, 0.5, 6), look(-30, 0, 0, 50), arms(-100, 40, -10), B.claw(-0.8), B.locks(0, 0, 20)),
                                merge({'hips': RP([84, 0, -4], [0, -10.5, 0])}, B.legs_free((2, -6), (16, 10), (0, 0)), arms(-150, 30, 0),
                                      B.claw(0.2), look(-4, -50, 0, 44), B.locks(0, -20, 0)))
    # Strength contests: it looms over you, both long hands at your throat, the veil hanging into your face.
    clips.update(grapple_set(
        S,
        merge(B.stand(-0.8, 0, 16, feet={'left': (0, 3)}), arms(-94, -14, -24, 0, -14), B.claw(0.9), look(10, 0, 6, 20), B.locks(0, -10, 0)),
        merge(B.stand(-0.2, 1.6, 0, feet={'left': (0, 3)}), arms(-80, -6, -10), look(-12, 0, -10, 40), B.locks(0, 0, 16)),
        merge(B.stand(-1.6, -0.8, 30, feet={'left': (0, 3.6)}), arms(-110, -16, -36, 0, -20), B.claw(1.0), look(20, 0, 12, 50), B.locks(0, -20, 0)),
        merge(B.stand(0.6, 0, -12, twist=-22), arms(-168, 10, -10), look(-24, 0, 0, 20), {'torso': R(-10, -20, 0)}, B.locks(0, 20, 0)),
        merge(B.stand(-0.8, 0, 30, twist=28), arm('right', -40, 0, 50, 0), arm('left', -60, 0, 30, 0), look(20, 0, 0, 44), {'torso': R(12, 24, 0)},
              B.locks(0, 0, -24))))
    write(B, clips)


BODIES = {'claw_hunter': claw_hunter, 'pale_stalker': pale_stalker, 'cyclops_bruiser': cyclops_bruiser, 'veiled_reacher': veiled_reacher}


def main():
    for name in sys.argv[1:] or list(BODIES):
        if name not in BODIES:
            sys.exit('unknown body %s (one of %s)' % (name, ', '.join(BODIES)))
        BODIES[name]()


if __name__ == '__main__':
    main()
