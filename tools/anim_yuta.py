#!/usr/bin/env python3
"""
Authors Yuta Okkotsu's clips (assets/jjk/animations/yuta) with tools/animkit.py.

    python3 tools/anim_yuta.py            (BLADE=1 python3 tools/anim_preview.py ... draws the katana)

How he moves: a swordsman, and a quiet one. Upright, light on his feet, the katana carried low at his right side with
the left hand resting on the sheath. Cuts are drawn through with the hips and finished: every one ends in a real
position (the blade stopped, the body behind it) and flows into the next from there. Nothing is muscled. With the steel
arm he is heavier and more direct: the casing swings like a hammer, and his free hand does the quick work. Around Rika
he gestures rather than strikes. Timings are the abilities' own (the contact times of the clips these replace, each
keyed from its JJS GIF).
"""
from animkit import *

NOTE = 'tools/anim_yuta.py'

FL, FR = F(-2.2, 0, 2.6, yaw=6), F(2.6, 0, -2.8, toe=14, yaw=24)
SHEATH = HC(-4.4, -8.6, 2.2, pole=(-1, 0.2, -0.6))      # left hand on the sheath's mouth


def T(root=(0, -1.0, 0), hips=(2, 16, 0), chest=(4, 10, 0), ra=HC(5.0, -8.4, 3.0), la=SHEATH, rl=FR, ll=FL, head=(2, 0, 0), blade=(-24, -8), **kw):
    return P(root=root, hips=hips, chest=chest, ra=ra, la=la, rl=rl, ll=ll, head=head, blade=blade, **kw)


REST = T()
AIR = dict(rl=L(-20, 56, 5, 24), ll=L(-48, 80, 5, 24))
AIR0 = T(root=(0, 0.6, 0), **AIR)
STEP_F = dict(rl=F(2.6, 0, -2.2, toe=40, yaw=10), ll=F(-2.2, 0, 4.6, yaw=6))     # weight gone forward onto the lead foot


def cut(name, t_h, wind, hit, start=None, end=None, t_w=None, carry=70, k=0.12, lag=30, rec=None, **over_):
    """One cut: drawn back, through on the hit time with the hips ahead of the blade, carried on, then recovered."""
    c = Clip('yuta', name, NOTE, **over_)
    d = c.duration
    c.key(0, start or REST)
    if start is not wind:
        c.key(t_w if t_w is not None else max(40, int(t_h * 0.5)), wind, 'EASE_OUT', marker='anticipation')
    c.hit(t_h, wind, hit, lag=lag, k=k, carry=carry)
    if rec is not None:
        c.key(min(d - 80, t_h + carry + 160), rec, 'EASE_OUT', marker='recovery')
    else:
        c.key(min(d - 80, t_h + carry + 140), lerp(hit, end or REST, 0.5), 'EASE_IN_OUT', marker='recovery')
    c.key(d, end or REST, 'EASE_IN_OUT')
    c.write()


def katana():
    # light_1: the draw is the cut. The blade leaves the sheath rising across him from left hip to right shoulder.
    w = T(root=(0.2, -1.6, -0.4), hips=(6, -20, 0), chest=(8, -22, 0), ra=HC(-3.6, -8.0, 4.0, pole=(1, 0.4, 0.2)), blade=(-10, -150), head=(4, 0, 0))
    h = T(root=(-0.2, -1.2, 1.6), hips=(4, 26, 0), chest=(0, 30, 0), ra=H(8.4, 22.0, 8.6), blade=(40, 50), la=HC(-4.6, -8.0, 1.0), **STEP_F)
    cut('yuta_light_1', 110, w, h)
    # light_2: it turns over at the top and comes straight back down the same line, right shoulder to left hip.
    w2 = T(root=(0, -1.0, 0.4), hips=(2, 30, 0), chest=(-2, 34, 0), ra=HC(6.0, 3.0, 0.5, pole=(1, 0.2, -0.2)), blade=(80, 30), la=HC(-4.4, -7.0, 2.0))
    h2 = T(root=(-0.4, -2.0, 2.0), hips=(8, -24, 0), chest=(10, -28, 0), ra=H(-5.0, 11.0, 10.0), blade=(-34, -44), la=HC(-5.0, -8.0, 0.0), **STEP_F)
    cut('yuta_light_2', 110, w2, h2)
    # light_3: a flat cut from his left, the off hand joining the hilt for it, both feet turning with the hips.
    w3 = T(root=(-0.4, -1.8, 0), hips=(6, -30, 0), chest=(6, -34, 0), ra=HC(-4.6, -3.0, 3.6, pole=(1, 0.6, 0)), blade=(6, -120), la=HC(-5.6, -4.0, 2.6))
    h3 = T(root=(0.4, -1.8, 1.6), hips=(6, 30, 0), chest=(4, 36, 0), ra=H(8.6, 17.0, 7.0), blade=(0, 74), la=HC(-3.0, -5.0, 5.0), ll=F(-2.2, 0, 2.6, toe=16, yaw=-30))
    cut('yuta_light_3', 120, w3, h3)
    # light_4, the finisher: both hands, the blade lifted right over his head, a full step and a cut straight down that
    # stops at knee height with his weight on the front foot.
    w4 = T(root=(0, 0.2, -0.8), hips=(-6, 0, 0), chest=(-10, 0, 0), ra=HC(1.4, 7.6, -0.4, pole=(1, 0.2, 0.4)), la=HC(-1.0, 6.4, 0.4, pole=(-1, 0.2, 0.4)), blade=(124, 0),
           rl=F(2.6, 0, -2.8, toe=24, yaw=24), head=(-4, 0, 0))
    h4 = T(root=(0, -3.0, 3.4), hips=(20, 0, 0), chest=(16, 0, 0), ra=H(1.2, 10.0, 12.6), la=H(0.0, 9.0, 10.8, pole=(-1, 0, 0)), blade=(-30, 0),
           rl=F(2.6, 0, -2.4, toe=50, yaw=10), ll=F(-2.2, 0, 5.6, yaw=6), head=(6, 0, 0))
    cut('yuta_light_4', 140, w4, h4, carry=90, k=0.06)

    # Air cuts: the same three, legs tucked, the body turning freely under the blade.
    def air(p):
        q = dict(p)
        return q
    wa = T(root=(0, 0.8, 0), hips=(0, -20, 0), chest=(8, -22, 0), ra=HC(-3.6, -8.0, 4.0, pole=(1, 0.4, 0.2)), blade=(-10, -150), **AIR)
    ha = T(root=(0, 1.2, 0.6), hips=(-4, 26, 0), chest=(0, 30, 0), ra=HC(7.0, 0.0, 6.6), blade=(40, 50), la=HC(-4.6, -8.0, 1.0), rl=L(6, 40, 5, 30), ll=L(-60, 90, 5, 24))
    cut('yuta_air_1', 110, wa, ha, start=AIR0, end=AIR0)
    wa = T(root=(0, 1.0, 0), hips=(-4, 30, 0), chest=(-2, 34, 0), ra=HC(6.0, 3.0, 0.5, pole=(1, 0.2, -0.2)), blade=(80, 30), **AIR)
    ha = T(root=(0, 0.8, 0.6), hips=(10, -24, 0), chest=(10, -28, 0), ra=HC(-4.0, -9.0, 7.0), blade=(-34, -44), rl=L(-60, 90, 5, 24), ll=L(6, 40, 5, 30))
    cut('yuta_air_2', 110, wa, ha, start=AIR0, end=AIR0)
    wa = T(root=(0, 0.8, 0), hips=(0, -30, 0), chest=(6, -34, 0), ra=HC(-4.6, -3.0, 3.6, pole=(1, 0.6, 0)), blade=(6, -120), la=HC(-5.6, -4.0, 2.6), **AIR)
    ha = T(root=(0, 1.2, 0.4), hips=(0, 34, 0), chest=(4, 36, 0), ra=HC(7.6, -3.0, 5.0), blade=(0, 74), la=HC(-3.0, -5.0, 5.0), rl=L(-10, 30, 12, 30), ll=L(-40, 70, 8, 24))
    cut('yuta_air_3', 110, wa, ha, start=AIR0, end=AIR0)

    # uppercut: the point dropped behind him almost to the floor, then drawn up in front in one long vertical stroke;
    # he rises onto his toes and off them as it passes his head.
    w = T(root=(0.4, -3.6, -0.4), hips=(14, 24, 0), chest=(12, 20, 0), ra=HC(6.0, -11.0, -1.0, pole=(1, 0, -1)), blade=(-50, 150), head=(6, 0, 0))
    h = T(root=(0, 1.8, 1.4), hips=(-8, -14, 0), chest=(-12, -14, 0), ra=H(2.0, 29.0, 6.0, pole=(0.5, -0.2, 1)), blade=(80, 0), la=HC(-5.0, -7.0, 0.0),
          rl=L(8, 12, 4, 36), ll=L(-26, 44, 4, 24), head=(-14, 0, 0))
    cut('yuta_uppercut', 150, w, h, t_w=90, carry=80, k=0.08, rec=T(root=(0, -2.2, 0.6), hips=(10, 8, 0), chest=(8, 6, 0)))
    # downslam: reversed grip in the air, both hands on the hilt, the point driven straight down.
    w = T(root=(0, 1.6, 0), hips=(-12, 0, 0), chest=(-10, 0, 0), ra=HC(1.4, 5.0, 2.0, pole=(1, 0.2, 0.2)), la=HC(-1.0, 4.0, 2.6, pole=(-1, 0.2, 0.2)), blade=(-70, 0),
          rl=L(-30, 80, 5, 24), ll=L(-50, 96, 5, 24), head=(14, 0, 0))
    h = T(root=(0, 0, 0.8), hips=(18, 0, 0), chest=(16, 0, 0), ra=HC(1.4, -9.0, 5.6), la=HC(-1.0, -8.0, 5.0), blade=(-86, 0),
          rl=L(-70, 100, 5, 20), ll=L(-40, 70, 5, 20), head=(20, 0, 0))
    cut('yuta_downslam', 140, w, h, start=AIR0, t_w=80, carry=80, k=0.05,
        rec=T(root=(0, -3.6, 0.8), hips=(18, 0, 0), chest=(12, 0, 0), ra=H(1.4, 9.0, 7.0), la=H(-0.6, 8.0, 6.4), blade=(-80, 0), rl=F(2.8, 0, -2.2), ll=F(-2.6, 0, 2.6)))
    # stomp: the same thing standing over them: lifted in both hands and put down through them like a stake.
    w = T(root=(0, 0, 0), hips=(-4, 0, 0), chest=(-8, 0, 0), ra=HC(1.4, 4.0, 3.0, pole=(1, 0.2, 0.2)), la=HC(-1.0, 3.0, 3.6, pole=(-1, 0.2, 0.2)), blade=(-76, 0), head=(26, 0, 0))
    h = T(root=(0, -3.6, 1.0), hips=(20, 0, 0), chest=(14, 0, 0), ra=H(1.4, 8.6, 7.6), la=H(-0.6, 7.6, 7.0, pole=(-1, 0, 0)), blade=(-84, 0), head=(26, 0, 0),
          rl=F(2.8, 0, -2.4, toe=24), ll=F(-2.6, 0, 3.0))
    cut('yuta_stomp', 140, w, h, t_w=80, carry=70, k=0.04)
    # sprint: a running draw-cut through them at waist height; he passes and finishes facing away along the blade.
    run = T(root=(0, -1.8, 0), hips=(26, 10, 0), chest=(8, -20, 0), ra=HC(-3.0, -8.0, 4.0, pole=(1, 0.4, 0.2)), blade=(-10, -150), rl=L(-52, 40, 3, 10), ll=L(40, 70, 3, 20), head=(-12, 0, 0))
    h = T(root=(0.6, -2.6, 4.6), hips=(16, 40, 0), chest=(6, 40, 0), ra=H(9.4, 14.0, 6.0), blade=(0, 84), la=HC(-5.0, -8.0, 0.0), rl=F(2.6, 0, -1.0, toe=50), ll=F(-2.2, 0, 6.6), head=(0, 20, 0))
    cut('yuta_sprint', 130, run, h, start=run, carry=90, k=0.10)
    # heavy_charge / heavy: the blade laid back over his right shoulder in both hands, front foot light; released, it
    # is one diagonal cut with everything through it, ending low on his left with his body right round.
    hc = T(root=(0.6, -2.2, -1.4), hips=(4, 40, 0), chest=(2, 30, 0), ra=HC(5.6, 2.0, -1.0, pole=(1, 0.2, -0.3)), la=HC(3.0, 0.6, 0.6, pole=(-1, 0.6, 0.2)), blade=(60, 120),
           rl=F(2.8, 0, -3.6, yaw=36), ll=F(-2.2, 0, 3.0, toe=10), head=(2, 0, 0))
    c = Clip('yuta', 'yuta_heavy_charge', NOTE)
    c.key(0, REST).breathe(260, hc, period=1300, amp=0.6)
    c.write()
    hh = T(root=(-0.8, -3.2, 4.0), hips=(14, -40, 0), chest=(14, -40, 0), ra=H(-6.0, 8.0, 8.0), la=H(-5.4, 9.6, 6.6, pole=(-1, 0, 0)), blade=(-30, -70),
           rl=F(2.8, 0, -2.4, toe=56, yaw=-20), ll=F(-2.2, 0, 5.6), head=(4, 10, 0))
    cut('yuta_heavy', 140, hc, hh, start=hc, carry=100, k=0.10, lag=40)
    # guard_katana: the blade held upright in front of him, edge out, the left palm braced on its back.
    gd = T(root=(0, -2.0, -0.4), hips=(8, 10, 0), chest=(8, 6, 0), ra=HC(2.6, -6.0, 6.0, pole=(1, 0.6, 0)), la=HC(-1.6, -0.6, 6.4, pole=(-1, 0.6, 0)), blade=(84, -30), head=(6, 0, 0))
    c = Clip('yuta', 'yuta_guard_katana', NOTE)
    c.key(0, lerp(REST, gd, 0.5)).breathe(150, gd, period=1700, amp=0.4)
    c.write()


S_R, S_L = HC(4.0, -5.0, 4.0), HC(-3.6, -3.4, 6.0)


def ST(root=(0, -1.4, 0), hips=(4, 16, 0), chest=(6, 12, 0), ra=S_R, la=S_L, rl=FR, ll=FL, head=(2, 0, 0), **kw):
    """The steel-arm guard: the casing carried low and back like a mace, the bare hand up in front."""
    return P(root=root, hips=hips, chest=chest, ra=ra, la=la, rl=rl, ll=ll, head=head, **kw)


SREST = ST()


def blow(name, t_h, wind, hit, start=None, end=None, t_w=None, carry=70, k=0.12, lag=30, rec=None, **over_):
    c = Clip('yuta', name, NOTE, **over_)
    d = c.duration
    c.key(0, start or SREST)
    if start is not wind:
        c.key(t_w if t_w is not None else max(40, int(t_h * 0.5)), wind, 'EASE_OUT', marker='anticipation')
    c.hit(t_h, wind, hit, lag=lag, k=k, carry=carry)
    c.key(min(d - 80, t_h + carry + 150), rec if rec is not None else lerp(hit, end or SREST, 0.5), 'EASE_IN_OUT', marker='recovery')
    c.key(d, end or SREST, 'EASE_IN_OUT')
    c.write()


def steel():
    SA = dict(rl=L(-20, 56, 5, 24), ll=L(-48, 80, 5, 24))
    A0 = ST(root=(0, 0.6, 0), **SA)
    # steel_light_1: the bare left hand first, a palm heel straight to the chin.
    w = ST(root=(0.2, -1.6, -0.4), hips=(4, 22, 0), chest=(6, 16, 0), la=HC(-3.6, -3.6, 4.4))
    h = ST(root=(-0.3, -1.6, 2.2), hips=(6, 0, 0), chest=(6, 30, 0), la=H(-0.8, 20.0, 15.4, wrist=-50), rl=F(2.6, 0, -2.8, toe=30, yaw=10))
    blow('yuta_steel_light_1', 100, w, h, k=0.08, carry=50, lag=16)
    # steel_light_2: the casing swung in behind it like a mace, a wide flat hook with the elbow locked.
    w = ST(root=(0.8, -2.2, -0.6), hips=(6, 40, 0), chest=(8, 30, -4), ra=HC(7.6, -5.0, -2.5, pole=(1, 0.2, -0.6)), la=HC(-2.6, -2.0, 7.0))
    h = ST(root=(-0.8, -2.0, 2.4), hips=(8, -34, 0), chest=(8, -44, 4), ra=H(-4.4, 17.4, 11.0, pole=(1, 0.4, 0)), la=HC(-5.4, -4.0, 0.6), rl=F(2.6, 0, -2.4, toe=44, yaw=-24))
    blow('yuta_steel_light_2', 110, w, h, k=0.14)
    # steel_light_3: the left elbow driven across on the rebound, stepping inside.
    w = ST(root=(-0.6, -1.8, 0.6), hips=(6, -30, 0), chest=(6, -30, 0), la=HC(-6.6, -2.4, 0.6, pole=(-1, 0.3, -0.4)), ra=HC(1.0, -6.0, 5.0))
    h = ST(root=(0.6, -1.8, 3.0), hips=(8, 30, 0), chest=(8, 44, -3), la=HC(-0.6, -1.6, 5.0, pole=(-1, 0.7, 0.4)), ra=HC(5.6, -7.0, -1.0), ll=F(-2.2, 0, 4.4, toe=14, yaw=-30),
           rl=F(2.6, 0, -1.6, toe=40))
    blow('yuta_steel_light_3', 110, w, h, k=0.12)
    # steel_light_4, the finisher: the casing raised over his head and brought down like a sledgehammer; it carries
    # his whole upper body after it.
    w = ST(root=(0.4, 0, -0.8), hips=(-8, 20, 0), chest=(-12, 16, 0), ra=HC(4.4, 7.6, -1.6, pole=(1, 0.2, 0.3)), la=HC(-2.0, -1.0, 8.0), head=(-4, 0, 0))
    h = ST(root=(-0.4, -3.4, 3.6), hips=(22, -24, 0), chest=(20, -26, 0), ra=H(-0.6, 9.6, 14.0), la=HC(-6.0, -5.0, -2.0, pole=(-1, 0, -1)), rl=F(2.6, 0, -2.2, toe=50), ll=F(-2.2, 0, 5.6),
           head=(8, 0, 0))
    blow('yuta_steel_light_4', 140, w, h, carry=90, k=0.06)
    w = ST(root=(0, 0.8, 0), hips=(-2, 22, 0), chest=(6, 16, 0), la=HC(-3.6, -3.6, 4.4), **SA)
    h = ST(root=(0, 1.2, 0.8), hips=(6, 0, 0), chest=(6, 30, 0), la=HC(-0.8, -2.0, 13.4, wrist=-50), rl=L(6, 40, 5, 30), ll=L(-60, 90, 5, 24))
    blow('yuta_steel_air_1', 100, w, h, start=A0, end=A0, k=0.08, carry=50, lag=14)
    w = ST(root=(0, 0.8, 0), hips=(-2, 40, 0), chest=(8, 30, -4), ra=HC(7.6, -5.0, -2.5, pole=(1, 0.2, -0.6)), la=HC(-2.6, -2.0, 7.0), **SA)
    h = ST(root=(0, 1.2, 0.8), hips=(6, -34, 0), chest=(8, -44, 4), ra=HC(-3.4, -4.0, 9.0, pole=(1, 0.4, 0)), la=HC(-5.4, -4.0, 0.6), rl=L(-60, 90, 5, 24), ll=L(6, 40, 5, 30))
    blow('yuta_steel_air_2', 100, w, h, start=A0, end=A0, k=0.14)
    w = ST(root=(0, 1.4, 0), hips=(-10, 20, 0), chest=(-12, 16, 0), ra=HC(4.4, 7.6, -1.6, pole=(1, 0.2, 0.3)), la=HC(-2.0, -1.0, 8.0), **SA)
    h = ST(root=(0, 0.6, 1.0), hips=(18, -24, 0), chest=(20, -26, 0), ra=HC(1.0, -10.0, 8.0), la=HC(-6.0, -5.0, -2.0, pole=(-1, 0, -1)), rl=L(-60, 96, 5, 20), ll=L(-30, 60, 5, 20))
    blow('yuta_steel_air_3', 100, w, h, start=A0, end=A0, k=0.06)
    # steel_uppercut: the casing dragged up from the floor in a shovel hook, the steel leading.
    w = ST(root=(0.6, -4.6, -0.4), hips=(18, 30, 0), chest=(16, 20, 4), ra=HC(6.0, -11.0, 1.0, pole=(1, 0, -1)), head=(8, 0, 0))
    h = ST(root=(-0.4, 2.2, 2.0), hips=(-10, -22, 0), chest=(-10, -22, -4), ra=H(1.4, 29.6, 6.6, pole=(0.4, -0.2, 1)), la=HC(-4.6, -6.0, 1.0), rl=L(10, 14, 4, 34), ll=L(-28, 44, 4, 20),
          head=(-16, 0, 0))
    blow('yuta_steel_uppercut', 140, w, h, t_w=85, carry=90, k=0.06, rec=ST(root=(0, -2.6, 0.6), hips=(10, 8, 0), chest=(10, 6, 0)))
    w = ST(root=(0, 1.6, -0.4), hips=(-16, 10, 0), chest=(-16, 10, 0), ra=HC(4.0, 7.6, -2.0, pole=(1, 0.2, 0.3)), la=HC(-3.0, -2.0, 7.0), rl=L(26, 70, 5, 30), ll=L(10, 60, 5, 30))
    h = ST(root=(0, 0.2, 1.4), hips=(22, -14, 0), chest=(22, -16, 0), ra=HC(0.6, -11.0, 7.0), la=HC(-6.0, -5.0, -1.0), rl=L(-60, 90, 5, 20), ll=L(-44, 70, 5, 20), head=(10, 0, 0))
    blow('yuta_steel_downslam', 130, w, h, start=A0, t_w=75, carry=80, k=0.06,
         rec=ST(root=(0, -3.2, 0.8), hips=(18, 0, 0), chest=(12, 0, 0), ra=H(3.0, 5.0, 8.0), rl=F(2.8, 0, -2.2), ll=F(-2.6, 0, 2.6)))
    # steel_stomp: he drops to a knee over them and puts the steel fist straight down.
    w = ST(root=(0.4, -1.0, 0), hips=(4, 20, 0), chest=(0, 20, 0), ra=HC(5.6, 5.0, -1.0, pole=(1, 0.2, -0.2)), la=HC(-4.0, -5.0, 5.0), head=(24, 0, 0))
    h = ST(root=(0, -6.2, 1.6), hips=(26, -14, 0), chest=(20, -16, 0), ra=H(1.0, 1.6, 8.0), la=A(30, 0, 36, -40), rl=F(2.8, 0, -3.4, toe=56), ll=F(-2.6, 0, 3.2), head=(26, 0, 0))
    blow('yuta_steel_stomp', 130, w, h, t_w=75, carry=70, k=0.04)
    # steel_sprint: the casing held out in front like a ram and run through them.
    run = ST(root=(0, -1.8, 0), hips=(26, 10, 0), chest=(10, 20, 0), ra=HC(6.6, -6.0, -2.0, pole=(1, 0.2, -1)), la=A(-50, 0, 8, -84), rl=L(-52, 40, 3, 10), ll=L(40, 70, 3, 20), head=(-12, 0, 0))
    h = ST(root=(-0.6, -2.4, 5.4), hips=(30, -34, 0), chest=(12, -36, -4), ra=H(-0.8, 15.0, 20.0), la=A(46, 0, 30, -40), rl=F(2.8, 0.8, -1.0, toe=60), ll=F(-2.2, 0, 6.6), head=(-10, 0, 0))
    blow('yuta_steel_sprint', 120, run, h, start=run, carry=90, k=0.08)
    # steel_heavy_charge / steel_heavy: the casing cocked right back behind his hip, the bare hand out sighting; then
    # a straight that he steps all the way through.
    hc = ST(root=(1.2, -3.8, -2.2), hips=(12, 46, 0), chest=(12, 30, 4), ra=HC(7.0, -7.4, -3.4, pole=(1, 0.2, -1)), la=H(-1.6, 17.6, 11.6, wrist=-40), rl=F(3.0, 0, -4.2, toe=24, yaw=30),
            ll=F(-2.4, 0, 4.0), head=(6, 0, 0))
    c = Clip('yuta', 'yuta_steel_heavy_charge', NOTE)
    c.key(0, SREST).breathe(260, hc, period=1200, amp=0.8)
    c.write()
    hh = ST(root=(-1.2, -3.4, 6.2), hips=(24, -40, 0), chest=(16, -38, -6), ra=H(-1.4, 16.0, 20.6), la=HC(-6.0, -4.8, -2.0, pole=(-1, 0, -1)), rl=F(3.0, 1.0, -2.6, toe=64), ll=F(-2.4, 0, 5.6))
    blow('yuta_steel_heavy', 140, hc, hh, start=hc, carry=100, k=0.10, lag=40)
    # steel_jab (upper body, after each of the first three M1s): a short stab of the casing from where it hangs.
    up = ('chest', 'rightArm', 'rightForearm', 'rightHand')
    c = Clip('yuta', 'yuta_steel_jab', NOTE)
    c.key(0, only(ST(chest=(6, 20, 0), ra=HC(5.6, -6.0, 1.0)), *up)).key(60, only(ST(chest=(8, -20, 0), ra=HC(1.0, -4.0, 12.4)), *up), 'EASE_IN', marker='impact')
    c.key(110, only(ST(chest=(8, -24, 0), ra=HC(0.6, -4.0, 12.8)), *up), 'EASE_OUT').key(260, only(SREST, *up), 'EASE_IN_OUT').write()


def techniques():
    g = 'yuta'
    # --- Severing Path. severing_path: the draw, the point dropped to the floor beside him, and the slide: body low
    # and turned side-on, the blade trailing along the ground behind his lead foot.
    dr = T(root=(0, -1.6, 0), hips=(6, -16, 0), chest=(8, -18, 0), ra=HC(-3.0, -7.0, 5.0, pole=(1, 0.4, 0.2)), blade=(-10, -140))
    lo = T(root=(0.6, -4.6, 1.0), hips=(20, 30, 0), chest=(12, 26, 0), ra=H(7.0, 3.6, -3.0, pole=(1, 0.2, -0.4)), blade=(-14, 150), la=HC(-4.0, -4.0, 6.0), rl=F(2.8, 0, -4.4, toe=30, yaw=30),
           ll=F(-2.4, 0, 4.4), head=(-6, 0, 0))
    sl = T(root=(0.6, -5.4, 2.4), hips=(28, 34, 0), chest=(10, 30, 0), ra=H(7.6, 2.6, -4.4, pole=(1, 0.2, -0.4)), blade=(-8, 160), la=HC(-3.0, -3.0, 8.0), rl=F(3.0, 0, -5.6, toe=44, yaw=40),
           ll=F(-2.4, 0, 5.6), head=(-14, 0, 0))
    c = Clip(g, 'yuta_severing_path', NOTE)
    c.key(0, REST).key(180, dr, 'EASE_OUT', marker='draw').key(300, lo, 'EASE_IN', lag=20, marker='lower').key(480, sl, 'LINEAR', marker='slide')
    c.key(760, tweak(sl, root_pos=(0, 0.6, -0.6), chest=(-3, 0, 0)), 'EASE_OUT').write()
    # severing_swings: three cuts on the spot at 300 / 600 / 900: up from the floor, back across, and the launching
    # cut rising two-handed.
    s1 = T(root=(-0.2, -1.6, 3.0), hips=(4, -20, 0), chest=(0, -24, 0), ra=H(-4.0, 23.0, 10.0), blade=(50, -40), la=HC(-5.0, -7.0, 0.0), **STEP_F)
    b1 = T(root=(0, -2.0, 2.6), hips=(6, -34, 0), chest=(6, -36, 0), ra=HC(-5.0, -2.0, 3.0, pole=(1, 0.6, 0)), blade=(10, -130), **STEP_F)
    s2 = T(root=(0.4, -2.0, 3.2), hips=(6, 30, 0), chest=(4, 36, 0), ra=H(8.6, 16.0, 8.6), blade=(-4, 76), la=HC(-3.0, -5.0, 5.0), **STEP_F)
    b2 = T(root=(0.4, -3.6, 2.4), hips=(14, 30, 0), chest=(12, 24, 0), ra=H(6.0, 5.0, 0.0, pole=(1, 0, -1)), la=H(5.0, 6.6, 1.4, pole=(-1, 0.4, 0)), blade=(-40, 140), **STEP_F)
    s3 = T(root=(0, 0.6, 3.6), hips=(-8, -16, 0), chest=(-12, -16, 0), ra=H(1.6, 30.0, 8.0, pole=(0.5, -0.2, 1)), la=H(0.4, 28.6, 7.0, pole=(-0.5, -0.2, 1)), blade=(84, 0),
           rl=F(2.6, 0, -1.6, toe=50), ll=F(-2.2, 0, 4.6, toe=24), head=(-12, 0, 0))
    c = Clip(g, 'yuta_severing_swings', NOTE)
    c.key(0, sl).key(220, lo, 'EASE_OUT').key(300, s1, 'EASE_IN', lag=24, marker='swing 1').key(360, over(s1, lo, 0.1), 'EASE_OUT')
    c.key(520, b1, 'EASE_IN_OUT').key(600, s2, 'EASE_IN', lag=24, marker='swing 2').key(660, over(s2, b1, 0.1), 'EASE_OUT')
    c.key(820, b2, 'EASE_IN_OUT').key(900, s3, 'EASE_IN', lag=30, marker='launch').key(980, over(s3, b2, 0.06), 'EASE_OUT').key(1150, lerp(s3, REST, 0.7), 'EASE_IN_OUT').write()
    c = Clip(g, 'yuta_severing_whiff', NOTE)
    c.key(0, sl).key(350, tweak(lo, root_pos=(0, 1.6, 0), hips=(-8, -10, 0)), 'EASE_OUT').key(700, REST, 'EASE_IN_OUT').write()
    # veilstep: a back roll. He sits back over his heels, goes over his right shoulder with the blade tucked along
    # his forearm, and comes up on one knee facing them.
    c = Clip(g, 'yuta_veilstep', NOTE)
    tuck = T(root=(0, -5.0, -1.6), hips=(-30, 0, 0), chest=(40, 0, 0), ra=HC(4.0, -6.0, 3.0), blade=(20, 160), la=HC(-4.0, -6.0, 3.0), rl=L(-100, 120, 6, 0), ll=L(-96, 116, 6, 0), head=(40, 0, 0), follow=1.0)
    c.key(0, REST).key(90, tuck, 'EASE_IN')
    c.key(450, tweak(tuck, hips=(-300, 0, 0), root_pos=(0, 1.0, -1.0)), 'LINEAR').key(600, tweak(tuck, hips=(-360, 0, 0)), 'EASE_OUT').key(601, tuck, 'STEP')
    c.key(900, T(root=(0, -4.6, -0.6), hips=(14, 10, 0), chest=(8, 6, 0), rl=F(2.8, 0, -3.6, toe=60), ll=F(-2.4, 0, 3.0), ra=H(6.0, 5.0, 2.0), blade=(-10, 20)), 'EASE_IN_OUT').write()

    # --- Resolute Slash. resolute_windup: blade swept back level behind him, body wound low, eyes on their neck; then
    # he is gone. resolute_slash: reappearing on the cut, a flat two-handed slash at throat height, turned right through.
    rw = T(root=(0.6, -3.0, -1.0), hips=(10, 44, 0), chest=(8, 34, 0), ra=H(8.6, 15.0, -6.0, pole=(1, 0, -0.4)), blade=(4, 150), la=HC(-3.0, -4.0, 7.0), rl=F(2.8, 0, -3.8, yaw=36),
           ll=F(-2.4, 0, 3.4), head=(2, 0, 0))
    c = Clip(g, 'yuta_resolute_windup', NOTE)
    c.key(0, REST).key(200, rw, 'EASE_OUT', lag=40, marker='anticipation').key(500, tweak(rw, hips=(2, 4, 0), chest=(2, 4, 0)), 'EASE_IN_OUT').write()
    rs = T(root=(-0.6, -2.2, 3.0), hips=(8, -40, 0), chest=(4, -46, 0), ra=H(-8.6, 19.6, 6.0), la=H(-7.6, 19.0, 4.6, pole=(-1, 0, 0)), blade=(0, -84),
           rl=F(2.6, 0, -2.2, toe=50, yaw=-24), ll=F(-2.2, 0, 4.6), head=(0, 12, 0))
    cut('yuta_resolute_slash', 150, rw, rs, start=rw, carry=110, k=0.10, lag=40)
    # Resolute Black Flash: the windup is a fist this time, the blade reversed along his left side; the blow is a
    # straight right with a Black Flash in it and he skids to a stop behind it.
    fw = T(root=(1.0, -4.0, -2.0), hips=(12, 46, 0), chest=(12, 30, 4), ra=HC(6.8, -7.6, -3.0, pole=(1, 0.2, -1)), blade=(0, 0), la=H(-1.6, 16.6, 11.0), rl=F(3.0, 0, -4.2, toe=24, yaw=30),
           ll=F(-2.4, 0, 4.0), head=(6, 0, 0))
    c = Clip(g, 'yuta_resolute_flash_windup', NOTE)
    c.key(0, rs).key(300, fw, 'EASE_OUT', lag=50).key(500, tweak(fw, hips=(2, 4, 0), chest=(2, 3, 0)), 'EASE_IN_OUT').write()
    fh = T(root=(-1.2, -3.2, 7.0), hips=(26, -42, 0), chest=(18, -40, -6), ra=H(-1.6, 15.8, 21.4), blade=(0, 0), la=HC(-6.2, -4.6, -2.5, pole=(-1, 0, -1)),
           rl=F(3.2, 1.6, -2.4, toe=68), ll=F(-2.4, 0, 6.0), head=(0, 0, 0))
    cut('yuta_resolute_flash', 90, fw, fh, start=fw, carry=130, k=0.14, lag=30,
        rec=T(root=(-0.8, -3.0, 5.4), hips=(18, -22, 0), chest=(12, -18, -2), ra=H(-0.8, 13.5, 14.6), rl=F(3.0, 0, 1.0, toe=26), ll=F(-2.4, 0, 6.0)))

    # --- Outburst. outburst_grip: hand on the hilt in the sheath, sunk over his front knee, the energy building.
    og = T(root=(0, -3.4, 0.6), hips=(16, -24, 0), chest=(12, -20, 0), ra=HC(-3.4, -8.0, 4.4, pole=(1, 0.5, 0.2)), blade=(-10, -150), rl=F(2.8, 0, -3.6, toe=30, yaw=24), ll=F(-2.4, 0, 3.4),
           head=(-6, 0, 0))
    c = Clip(g, 'yuta_outburst_grip', NOTE)
    c.key(0, REST).key(260, og, 'EASE_OUT', lag=40).breathe(400, og, period=900, amp=0.9)
    c.write()
    # outburst_swing: the draw (70), a full turn with the blade out flat (160), and it finishes pointing at the sky
    # as the burst goes up (240).
    c = Clip(g, 'yuta_outburst_swing', NOTE).spins()
    d1 = T(root=(0, -2.4, 1.0), yaw=40, hips=(10, 20, 0), chest=(4, 30, 0), ra=H(9.0, 16.0, 6.0), blade=(0, 80), la=HC(-5.0, -8.0, 0.0), rl=F(2.8, 0, -3.0, toe=30, rel=True), ll=F(-2.4, 0, 3.0, rel=True))
    d2 = T(root=(0, -1.6, 0.6), yaw=-200, hips=(6, 0, 0), chest=(2, 20, 0), ra=HC(8.6, -2.0, 2.0), blade=(6, 90), la=HC(-5.0, -8.0, 0.0), rl=F(2.8, 0, -1.6, toe=30, rel=True), ll=F(-2.4, 0, 1.6, rel=True), follow=1.0)
    d3 = T(root=(0, 0.4, 0), yaw=-360, hips=(-6, 0, 0), chest=(-10, 0, 0), ra=H(3.0, 30.6, 2.0, pole=(1, 0, 0.4)), blade=(88, 0), la=A(-10, 0, 40, -30), rl=F(2.6, 0, -1.6, toe=30), ll=F(-2.4, 0, 1.6, toe=30),
           head=(-16, 0, 0))
    d3n = T(root=(0, 0.4, 0), hips=(-6, 0, 0), chest=(-10, 0, 0), ra=H(3.0, 30.6, 2.0, pole=(1, 0, 0.4)), blade=(88, 0), la=A(-10, 0, 40, -30), rl=F(2.6, 0, -1.6, toe=30), ll=F(-2.4, 0, 1.6, toe=30), head=(-16, 0, 0))
    c.key(0, og).key(70, d1, 'EASE_IN', marker='draw').key(160, d2, 'LINEAR', marker='spin').key(240, d3, 'EASE_OUT', marker='burst').key(241, d3n, 'STEP')
    c.key(560, tweak(d3n, rightArm=(6, 0, 4), root_pos=(0, -0.6, 0)), 'EASE_OUT').key(900, REST, 'EASE_IN_OUT').write()
    # outburst_parry: the half-drawn blade snapped up into the blow, edge out, and the push that follows it.
    pp = T(root=(0, -2.2, 1.4), hips=(8, 10, 0), chest=(6, 16, 0), ra=H(4.0, 21.0, 9.6, pole=(1, 0.4, 0)), blade=(70, -50), la=HC(-2.0, -1.0, 7.0), **STEP_F)
    c = Clip(g, 'yuta_outburst_parry', NOTE)
    c.key(0, og).key(70, pp, 'EASE_IN', lag=10, marker='parry').key(140, tweak(pp, rightArm=(-6, 0, 0), root_pos=(0, 0, 0.8)), 'EASE_OUT').key(300, pp, 'EASE_IN_OUT')
    c.key(600, REST, 'EASE_IN_OUT').write()

    # --- Second Wind. second_wind: the rush, low, free hand reaching for a face. second_wind_slam: he has it; lifted
    # (250) and driven into the floor under his whole weight (350).
    c = Clip(g, 'yuta_second_wind', NOTE)
    r1 = T(root=(0, -2.6, 1.0), hips=(34, 0, 0), chest=(8, -16, 0), ra=HC(5.6, -8.0, -2.0), blade=(-10, 160), la=H(-0.6, 15.0, 15.0, wrist=-40), rl=L(-64, 40, 3, 10), ll=L(40, 80, 3, 20), head=(-24, 0, 0))
    r2 = T(root=(0, -2.6, 1.0), hips=(34, 0, 0), chest=(8, -16, 0), ra=HC(5.6, -8.0, -2.0), blade=(-10, 160), la=H(-0.6, 15.6, 15.6, wrist=-40), rl=L(40, 80, 3, 20), ll=L(-64, 40, 3, 10), head=(-24, 0, 0))
    m = tweak(lerp(r1, r2, 0.5), root_pos=(0, 0.8, 0))
    c.key(0, REST).key(120, r1, 'EASE_OUT').key(300, r2, 'LINEAR').key(390, m, 'LINEAR').key(480, r1, 'LINEAR').key(600, r2, 'LINEAR').write()
    lift = T(root=(0, -0.6, 1.6), hips=(-4, 20, 0), chest=(-8, 24, 0), la=H(-1.0, 25.0, 11.0, wrist=-30), ra=HC(5.6, -8.0, -1.0), blade=(-10, 150), **STEP_F)
    slam = T(root=(0, -6.0, 3.0), hips=(34, 20, 0), chest=(22, 26, 0), la=H(-1.0, 2.6, 10.6, wrist=-30), ra=A(40, 0, 30, -30), blade=(-10, 150), rl=F(2.8, 0, -3.0, toe=56), ll=F(-2.4, 0, 4.6), head=(20, 0, 0))
    c = Clip(g, 'yuta_second_wind_slam', NOTE)
    c.key(0, r1).key(250, lift, 'EASE_OUT', marker='lift').key(350, slam, 'EASE_IN', lag=20, marker='slam').key(430, tweak(slam, root_pos=(0, 0.8, 0), chest=(-4, 0, 0)), 'EASE_OUT')
    c.key(650, slam, 'EASE_IN_OUT').key(1000, REST, 'EASE_IN_OUT').write()
    c = Clip(g, 'yuta_second_wind_whiff', NOTE)
    c.key(0, r1).key(250, T(root=(0, -2.6, 2.6), hips=(24, -10, 0), chest=(10, -10, 0), la=H(-2.0, 12.0, 12.0), rl=F(2.6, 0, 1.0, toe=20), ll=F(-2.2, 0, 5.6)), 'EASE_OUT', marker='stumble')
    c.key(700, REST, 'EASE_IN_OUT').write()
    c = Clip(g, 'yuta_second_wind_blocked', NOTE)
    bo = T(root=(0.4, -1.4, -2.6), hips=(-8, 20, 0), chest=(-12, 20, 0), la=HC(-6.0, -1.0, 3.0), ra=HC(6.0, -6.0, 0.0), rl=F(2.8, 0, -5.4), ll=F(-2.2, 0, 1.0, toe=24), head=(-10, 0, 0))
    c.key(0, r1).key(150, bo, 'EASE_OUT', eases={'chest': 'OVERSHOOT'}, marker='bounced off').key(600, lerp(bo, REST, 0.6), 'EASE_IN_OUT').key(900, REST, 'EASE_IN_OUT').write()
    # second_wind_pummel (the variant, bare hands): kneeling on them, four blows at 200 / 450 / 700 / 950 alternating
    # hands, each from the shoulder, then he hauls them up and throws them (1200).
    kb = P(root=(0, -6.4, 2.6), hips=(24, 0, 0), chest=(10, 0, 0), ra=HC(4.0, -4.0, 4.0), la=HC(-4.0, -4.0, 4.0), rl=F(2.8, 0, -1.6, toe=60), ll=F(-2.6, 0, 4.2), head=(22, 0, 0))

    def kn(side, up):
        ch = (-6 if up else 26, (30 if up else -26) * side, 0)
        hi, lo = HC(6.0, 4.0, -1.0, pole=(1, 0.2, -0.3)), H(0.6, 3.0, 10.0)
        p = P(root=(0, -6.4 + (0.8 if up else -0.4), 2.6), hips=(20 if up else 30, 0, 0), chest=ch, ra=hi if up else lo, la=HC(-4.0, -5.0, 4.0),
              rl=F(2.8, 0, -1.6, toe=60), ll=F(-2.6, 0, 4.2), head=(22, 0, 0))
        return p if side > 0 else mirror(p)
    c = Clip(g, 'yuta_second_wind_pummel', NOTE)
    c.key(0, slam).key(120, kn(1, True), 'EASE_OUT')
    t, side = 200, 1
    for i in range(4):
        c.key(t, kn(side, False), 'EASE_IN', lag=16, marker='blow %d' % (i + 1))
        if i < 3:
            c.key(t + 170, kn(-side, True), 'EASE_OUT')
        t += 250
        side = -side
    c.key(1050, T(root=(0, -4.0, 2.0), hips=(20, 30, 0), chest=(10, 30, 0), la=H(-1.0, 6.0, 9.0), ra=H(1.0, 6.0, 9.0), blade=(0, 0), **STEP_F), 'EASE_OUT')
    c.key(1200, T(root=(0, 0, 1.0), hips=(-8, -30, 0), chest=(-10, -34, 0), la=A(-150, 0, 20, -10), ra=A(-140, 0, 16, -10), blade=(0, 0), **STEP_F), 'EASE_IN', lag=30, marker='throw')
    c.key(1600, REST, 'EASE_IN_OUT').write()
    # second_wind_swords (katana out): four alternating cuts at 200 / 450 / 700 / 950, then a spinning axe kick (1200).
    ca = T(root=(-0.2, -2.0, 2.4), hips=(8, -26, 0), chest=(10, -30, 0), ra=H(-5.0, 11.0, 10.0), blade=(-34, -44), **STEP_F)
    cb = T(root=(0.2, -1.6, 2.4), hips=(4, 26, 0), chest=(0, 30, 0), ra=H(8.4, 21.0, 8.6), blade=(40, 50), **STEP_F)
    c = Clip(g, 'yuta_second_wind_swords', NOTE).spins()
    c.key(0, T(root=(0, -1.0, 1.6), hips=(2, 30, 0), chest=(-2, 34, 0), ra=HC(6.0, 3.0, 0.5, pole=(1, 0.2, -0.2)), blade=(80, 30), **STEP_F))
    for i, (t, p) in enumerate(((200, ca), (450, cb), (700, ca), (950, cb))):
        c.key(t, p, 'EASE_IN', lag=20, marker='swing %d' % (i + 1))
        c.key(t + 60, over(p, cb if p is ca else ca, 0.06), 'EASE_OUT')
    kw = T(root=(0, 0.6, 2.0), yaw=-200, hips=(-10, 0, 0), chest=(10, 0, 0), ra=HC(6.0, -6.0, 0.0), blade=(0, 90), la=A(-40, 0, 40, -40), rl=L(-150, 10, 8, -20), ll=L(-10, 40, 4, 30), follow=1.0)
    kh = T(root=(0, -1.0, 2.4), yaw=-360, hips=(14, 0, 0), chest=(8, 0, 0), ra=HC(6.0, -8.0, 0.0), blade=(-10, 60), la=A(-20, 0, 40, -30), rl=L(-58, 2, 4, 10), ll=F(-2.2, 0, 2.0, toe=20, rel=True), head=(12, 0, 0))
    khn = T(root=(0, -1.0, 2.4), hips=(14, 0, 0), chest=(8, 0, 0), ra=HC(6.0, -8.0, 0.0), blade=(-10, 60), la=A(-20, 0, 40, -30), rl=L(-58, 2, 4, 10), ll=F(-2.2, 0, 2.0, toe=20), head=(12, 0, 0))
    c.key(1050, lerp(cb, kw, 0.5), 'EASE_IN').key(1130, kw, 'LINEAR').key(1200, kh, 'EASE_IN', marker='axe kick').key(1201, khn, 'STEP')
    c.key(1600, REST, 'EASE_IN_OUT').write()

    # --- Rika. rika_command (upper body): a pointed finger, eyes on the target, then the hand drops.
    up = ('chest', 'neck', 'head', 'rightArm', 'rightForearm', 'rightHand', 'leftArm', 'leftForearm', 'leftHand')
    pt = only(T(chest=(4, -20, 0), la=H(-1.0, 20.0, 14.0), head=(0, 0, 0)), *up)
    c = Clip(g, 'yuta_rika_command', NOTE)
    c.key(0, only(REST, *up)).key(120, pt, 'EASE_IN', marker='point').key(180, tweak(pt, leftArm=(-4, 0, 0)), 'EASE_OUT').key(420, pt, 'EASE_IN_OUT').key(650, only(REST, *up), 'EASE_IN_OUT').write()
    # rika_launch: she gets under him and throws; he crouches onto her hand, then is flung forward flat, arms back.
    cr = T(root=(0, -4.6, 0), hips=(22, 0, 0), chest=(10, 0, 0), ra=HC(5.0, -8.0, 2.0), la=HC(-5.0, -8.0, 2.0), rl=F(2.6, 0, -1.6, toe=30), ll=F(-2.4, 0, 1.6), head=(-10, 0, 0))
    fly = T(root=(0, 1.6, 3.0), hips=(46, 0, 0), chest=(8, 0, 0), ra=A(60, 0, 20, -20), la=A(60, 0, 20, -20), blade=(-10, 170), rl=L(30, 30, 4, 40), ll=L(-40, 80, 4, 20), head=(-40, 0, 0))
    c = Clip(g, 'yuta_rika_launch', NOTE)
    c.key(0, REST).key(160, cr, 'EASE_OUT').key(240, fly, 'EASE_IN', marker='boost').key(600, tweak(fly, hips=(-10, 0, 0), leftThigh=(20, 0, 0)), 'EASE_IN_OUT')
    c.key(900, T(root=(0, -2.4, 1.0), hips=(12, 6, 0), chest=(8, 4, 0)), 'EASE_IN').write()
    upf = T(root=(0, 2.4, 0), hips=(-8, 0, 0), chest=(-8, 0, 0), ra=A(20, 0, 30, -30), la=A(-150, 0, 20, -30), blade=(-40, 30), rl=L(-64, 100, 5, 30), ll=L(-10, 40, 5, 40), head=(-24, 0, 0))
    c = Clip(g, 'yuta_rika_launch_air', NOTE)
    c.key(0, AIR0).key(160, T(root=(0, -1.0, 0), hips=(10, 0, 0), chest=(14, 0, 0), rl=L(-80, 120, 5, 10), ll=L(-76, 116, 5, 10)), 'EASE_OUT')
    c.key(250, upf, 'EASE_IN', marker='boost').key(900, AIR0, 'EASE_IN_OUT').write()
    # rika_throw: he is the projectile. Picked up by the collar (250), a long wind back (850), thrown (950), flying
    # sword-first.
    pk = T(root=(0, 3.0, -0.6), hips=(10, 0, 0), chest=(16, 0, 0), ra=A(10, 0, 14, -20), la=A(10, 0, 14, -20), blade=(-50, 0), rl=L(-10, 30, 5, 40), ll=L(-24, 44, 5, 40), head=(14, 0, 0))
    wb = T(root=(0, 4.0, -3.4), hips=(-20, 0, 0), chest=(-10, 0, 0), ra=A(-30, 0, 20, -40), la=A(-30, 0, 20, -40), blade=(10, 0), rl=L(-50, 60, 5, 30), ll=L(-60, 70, 5, 30), head=(-10, 0, 0))
    th = T(root=(0, 3.0, 3.0), hips=(60, 0, 0), chest=(10, 0, 0), ra=H(1.0, 23.0, 20.0), la=A(60, 0, 20, -20), blade=(0, 0), rl=L(20, 20, 4, 40), ll=L(30, 30, 4, 40), head=(-50, 0, 0))
    c = Clip(g, 'yuta_rika_throw', NOTE)
    c.key(0, REST).key(250, pk, 'EASE_OUT', marker='picked up').key(850, wb, 'EASE_IN_OUT', marker='wound back').key(950, th, 'EASE_IN', marker='thrown')
    c.key(2400, tweak(th, hips=(4, 0, 0)), 'EASE_IN_OUT').write()
    # rika_throw_hit: through them. He tumbles once off the hit and lands sliding on one knee.
    c = Clip(g, 'yuta_rika_throw_hit', NOTE)
    tk = T(root=(0, 2.0, 0), hips=(-20, 0, 0), chest=(40, 0, 0), ra=HC(4.0, -6.0, 3.0), blade=(20, 160), la=HC(-4.0, -6.0, 3.0), rl=L(-100, 120, 6, 0), ll=L(-96, 116, 6, 0), head=(30, 0, 0), follow=1.0)
    c.key(0, th).key(300, tk, 'EASE_OUT', marker='rebound').key(500, tweak(tk, hips=(-360, 0, 0)), 'EASE_OUT').key(501, tk, 'STEP')
    c.key(700, T(root=(0, -4.6, 0.6), hips=(14, 10, 0), chest=(8, 6, 0), rl=F(2.8, 0, -3.6, toe=60), ll=F(-2.4, 0, 3.0)), 'EASE_IN').write()


def awakened():
    g = 'yuta'
    # true_love: the necklace gripped (320) and torn off (520); the ring slid onto his finger at arm's length (720);
    # he lowers his head as she arrives (1100); the right arm is offered out to the side (1500) and the casing
    # slams round it (2000), jolting him.
    grip = ST(root=(0, -0.8, 0), hips=(2, 0, 0), chest=(8, 0, 0), ra=HC(1.0, -2.6, 3.6, pole=(1, 0.5, 0)), la=A(4, 0, 8, -14), head=(18, 0, 0), rl=F(2.4, 0, -0.8, yaw=14), ll=F(-2.4, 0, 0.8, yaw=14))
    torn = ST(root=(0, -0.6, -0.4), hips=(0, 14, 0), chest=(-4, 20, 0), ra=HC(7.0, -4.0, 3.0), la=A(4, 0, 8, -14), head=(0, 0, 0), rl=F(2.4, 0, -0.8, yaw=14), ll=F(-2.4, 0, 0.8, yaw=14))
    ring = ST(root=(0, -0.6, 0), hips=(0, 0, 0), chest=(2, 0, 0), ra=HC(1.6, -3.0, 7.0, pole=(1, 0.5, 0)), la=HC(-1.4, -3.0, 6.4, pole=(-1, 0.5, 0)), head=(14, 0, 0), rl=F(2.4, 0, -0.8, yaw=14),
                ll=F(-2.4, 0, 0.8, yaw=14))
    come = ST(root=(0, -1.0, 0), hips=(4, 0, 0), chest=(10, 0, 0), ra=A(0, 0, 10, -20), la=A(0, 0, 10, -20), head=(26, 0, 0), rl=F(2.4, 0, -0.8, yaw=14), ll=F(-2.4, 0, 0.8, yaw=14))
    arm = ST(root=(0, -0.6, 0), hips=(0, -10, 0), chest=(-4, -10, 4), ra=A(-30, 0, 80, -6, -30), la=A(0, 0, 14, -24), head=(-4, 30, 0), rl=F(2.8, 0, -0.8, yaw=20), ll=F(-2.6, 0, 0.8, yaw=14))
    case = tweak(arm, chest=(4, 6, -6), rightArm=(8, 0, -10), root_pos=(0.6, -0.8, 0))
    c = Clip(g, 'yuta_true_love', NOTE)
    c.key(0, SREST).key(320, grip, 'EASE_IN', marker='necklace').key(520, torn, 'EASE_IN', marker='torn off').key(720, ring, 'EASE_OUT', marker='the ring')
    c.key(1100, come, 'EASE_OUT', marker='Rika').key(1500, arm, 'EASE_IN', marker='arm out').key(2000, case, 'SPRING', marker='casing')
    c.key(2450, tweak(arm, rightArm=(20, 0, -30), rightForearm=(-60, 0, 0), head=(0, 0, 0)), 'EASE_IN_OUT').key(3000, SREST, 'EASE_IN_OUT').write()

    # --- Elbow Rush. elbow_rush: the dash ends in a lead elbow with his whole body lined up behind it (120).
    ew = ST(root=(0, -2.6, 0), hips=(26, 30, 0), chest=(10, 30, 0), ra=HC(6.6, -4.0, -1.0, pole=(1, 0.2, -0.6)), la=A(-40, 0, 10, -80), rl=L(-52, 40, 3, 10), ll=L(40, 70, 3, 20), head=(-10, 0, 0))
    eh = ST(root=(-0.6, -2.6, 4.6), hips=(22, -40, 0), chest=(12, -50, -4), ra=HC(-1.0, -2.0, 5.0, pole=(1, 0.7, 0.4)), la=A(40, 0, 30, -50), rl=F(2.8, 0, -2.0, toe=56), ll=F(-2.2, 0, 5.6),
            head=(0, 14, 0))
    c = Clip(g, 'yuta_elbow_rush', NOTE)
    c.key(0, ew)
    c.hit(120, ew, eh, lag=30, k=0.12, carry=90, marker='elbow')
    c.key(600, lerp(eh, SREST, 0.6), 'EASE_IN_OUT').write()
    # elbow_barrage: seven blows, one every 150 ms, alternating the casing and the bare hand at their back, feet
    # planted, shoulders turning like a drum roll.
    base = ST(root=(0, -2.0, 1.4), hips=(10, 0, 0), chest=(10, 0, 0), ra=HC(3.6, -3.4, 4.6), la=HC(-3.6, -3.4, 4.6), head=(2, 0, 0))
    r = ST(root=(-0.5, -2.2, 2.6), hips=(10, -24, 0), chest=(12, -32, 3), ra=H(-0.4, 17.6, 17.0), la=HC(-4.4, -4.0, 2.4), rl=F(2.6, 0, -2.8, toe=40, yaw=-20), head=(2, 0, 0))
    l = ST(root=(0.5, -2.2, 2.6), hips=(10, 22, 0), chest=(12, 32, -3), la=H(0.4, 17.6, 17.0), ra=HC(4.4, -4.0, 2.4), head=(2, 0, 0))
    c = Clip(g, 'yuta_elbow_barrage', NOTE)
    c.key(0, base)
    t = 100
    for i in range(7):
        p = r if i % 2 == 0 else l
        c.key(t, p, 'EASE_IN', lag=14, marker='blow')
        c.key(t + 70, lerp(p, base, 0.5), 'EASE_OUT')
        t += 150
    c.key(1150, base, 'EASE_IN_OUT').write()
    # elbow_final: the casing swung up underneath them from behind his hip (110).
    fw = ST(root=(0.6, -4.0, 0.6), hips=(16, 34, 0), chest=(14, 24, 4), ra=HC(6.4, -10.0, -0.6, pole=(1, 0, -1)), head=(6, 0, 0))
    fh = ST(root=(-0.4, 0.6, 2.6), hips=(-8, -26, 0), chest=(-10, -26, -4), ra=H(0.6, 28.0, 9.0, pole=(0.4, -0.2, 1)), la=HC(-5.0, -6.0, 0.6), rl=F(2.6, 0, -2.4, toe=50), ll=F(-2.2, 0, 3.6, toe=20),
            head=(-14, 0, 0))
    c = Clip(g, 'yuta_elbow_final', NOTE)
    c.key(0, fw)
    c.hit(110, fw, fh, lag=30, k=0.08, carry=90, marker='launch')
    c.key(350, lerp(fh, SREST, 0.4), 'EASE_OUT').key(650, SREST, 'EASE_IN_OUT').write()
    # elbow_whiff: nothing there. The elbow carries him round and off balance; a long stumble before he has his feet.
    c = Clip(g, 'yuta_elbow_whiff', NOTE)
    ovx = ST(root=(-1.6, -3.4, 6.0), hips=(30, -60, 6), chest=(14, -50, 6), ra=HC(-3.0, -3.0, 4.0, pole=(1, 0.7, 0.4)), la=A(50, 0, 40, -30), rl=F(2.8, 1.0, -1.0, toe=60), ll=F(-2.2, 0, 6.6),
             head=(6, 20, 0))
    c.key(0, ew).key(300, ovx, 'EASE_OUT', marker='overextended').key(800, ST(root=(-1.0, -4.0, 5.0), hips=(26, -30, 0), chest=(16, -20, 0), ra=H(4.0, 4.0, 8.0), rl=F(2.8, 0, 2.0), ll=F(-2.2, 0, 6.6)), 'EASE_IN_OUT')
    c.key(1300, lerp(ovx, SREST, 0.7), 'EASE_IN_OUT').key(1700, SREST, 'EASE_IN_OUT').write()

    # --- Copy / Cursed Speech: a hand hooks his collar down off his mouth (300), he leans in and says it (600).
    col = ST(root=(0, -0.8, 0), hips=(0, 0, 0), chest=(2, 0, 0), la=HC(-1.0, -1.0, 4.0, pole=(-1, 0.5, 0)), ra=A(0, 0, 8, -14), head=(-6, 0, 0), rl=F(2.4, 0, -0.8, yaw=14), ll=F(-2.4, 0, 0.8, yaw=14))
    say = ST(root=(0, -1.6, 1.4), hips=(10, 0, 0), chest=(10, 0, 0), la=HC(-1.4, -2.4, 4.4, pole=(-1, 0.5, 0)), ra=A(20, 0, 20, -30), head=(-4, 0, 0), rl=F(2.4, 0, -1.6, toe=20, yaw=14), ll=F(-2.4, 0, 1.8, yaw=14))
    c = Clip(g, 'yuta_cursed_speech', NOTE)
    c.key(0, SREST).key(300, col, 'EASE_OUT', marker='collar').key(600, say, 'EASE_IN', lag=30, marker='command').key(900, tweak(say, chest=(-3, 0, 0)), 'EASE_OUT')
    c.key(1200, SREST, 'EASE_IN_OUT').write()

    # --- Energy Ripple: the katana raised point-down in both hands (450) and driven into the floor in front of him
    # (700), kneeling on it while the dome goes out.
    er = T(root=(0, 0.2, 0), hips=(-6, 0, 0), chest=(-10, 0, 0), ra=HC(1.4, 5.6, 2.6, pole=(1, 0.2, 0.2)), la=HC(-1.0, 4.6, 3.2, pole=(-1, 0.2, 0.2)), blade=(-80, 0), head=(10, 0, 0))
    es = T(root=(0, -5.4, 1.4), hips=(22, 0, 0), chest=(14, 0, 0), ra=H(1.4, 9.6, 8.0), la=H(-0.6, 8.6, 7.4, pole=(-1, 0, 0)), blade=(-86, 0), rl=F(2.8, 0, -3.4, toe=56), ll=F(-2.6, 0, 3.4), head=(8, 0, 0))
    c = Clip(g, 'yuta_energy_ripple', NOTE)
    c.key(0, REST).key(450, er, 'EASE_OUT', marker='raised').key(700, es, 'EASE_IN', lag=20, marker='stab').key(780, tweak(es, root_pos=(0, 0.6, 0), chest=(-4, 0, 0)), 'EASE_OUT')
    c.key(1100, es, 'EASE_IN_OUT').key(1500, lerp(es, REST, 0.8), 'EASE_IN_OUT').write()
    # fakeout: the stab never lands; the blade is turned over and swung flat through them instead (100).
    fo = T(root=(-0.4, -2.2, 2.6), hips=(8, -36, 0), chest=(6, -40, 0), ra=H(-8.0, 16.0, 7.0), la=H(-7.0, 15.4, 5.6, pole=(-1, 0, 0)), blade=(0, -80), **STEP_F)
    cut('yuta_fakeout', 100, er, fo, start=er, carry=110, k=0.12)

    # --- True Love Beam. beam_conjure: both hands brought together in front of his chest round the orb (400), held
    # and steadied while he aims. beam_quick: one hand thrown forward (100).
    orb = ST(root=(0, -1.8, -0.4), hips=(4, 0, 0), chest=(6, 0, 0), ra=H(2.0, 17.6, 9.6, pole=(1, 0, -0.2)), la=H(-2.0, 17.6, 9.6, pole=(-1, 0, -0.2)), rl=F(3.0, 0, -2.0, yaw=20), ll=F(-2.8, 0, 2.0, yaw=10),
              head=(4, 0, 0))
    c = Clip(g, 'yuta_beam_conjure', NOTE)
    c.key(0, SREST).key(400, orb, 'EASE_OUT', lag=40, marker='orb').key(1100, tweak(orb, rightArm=(0, 0, 4), leftArm=(0, 0, -4), chest=(2, 0, 0)), 'EASE_IN_OUT').write()
    bq = ST(root=(0, -1.8, 1.6), hips=(8, -24, 0), chest=(8, -26, 0), ra=H(0.4, 18.6, 16.0, wrist=-50), la=HC(-5.0, -6.0, 0.6), rl=F(2.6, 0, -2.6, toe=30), ll=F(-2.2, 0, 3.4))
    c = Clip(g, 'yuta_beam_quick', NOTE)
    c.key(0, orb).key(100, bq, 'EASE_IN', lag=20, marker='release').key(180, tweak(bq, rightArm=(-8, 0, 0), chest=(-4, 0, 0), root_pos=(0, 0, -0.6)), 'EASE_OUT')
    c.key(400, bq, 'EASE_IN_OUT').key(700, SREST, 'EASE_IN_OUT').write()

    # --- Authentic Mutual Love. domain_sign: hands clasped in front of his chest, fingers laced, head bowed over them.
    ds = ST(root=(0, -0.8, 0), hips=(0, 0, 0), chest=(6, 0, 0), ra=HC(0.9, -3.6, 4.8, pole=(1, 0.5, 0)), la=HC(-0.9, -3.6, 4.8, pole=(-1, 0.5, 0)), head=(18, 0, 0), rl=F(2.4, 0, -0.8, yaw=14),
            ll=F(-2.4, 0, 0.8, yaw=14))
    c = Clip(g, 'yuta_domain_sign', NOTE)
    c.key(0, SREST).key(420, ds, 'EASE_OUT').breathe(600, ds, period=2200, amp=0.4)
    c.write()
    # yuta_domain_release: the hands open and lower, palms up, and he lifts his head: an offering, not a threat.
    ex = ST(root=(0, -0.4, 0), hips=(-2, 0, 0), chest=(-6, 0, 0), ra=A(-36, 0, 30, -30, -40), la=A(-36, 0, 30, -30, -40), head=(-8, 0, 0), rl=F(2.4, 0, -0.8, yaw=14), ll=F(-2.4, 0, 0.8, yaw=14))
    c = Clip(g, 'yuta_domain_release', NOTE, 1200, priority='AWAKENING', blendIn=120, blendOut=300)
    c.key(0, ds).key(200, ex, 'EASE_IN_OUT', marker='expand').key(600, tweak(ex, rightArm=(6, 0, 6), leftArm=(6, 0, -6)), 'EASE_IN_OUT').key(1050, SREST, 'EASE_IN_OUT', marker='settle')
    c.key(1200, SREST).write()
    # blade_run / blade_swing (a blade pulled from the domain's floor): running with it held back and low; then one
    # rising cut with his weight thrown through (110).
    b1 = T(root=(0, -1.8, 0.6), hips=(28, 16, 0), chest=(6, 10, 0), ra=H(6.6, 7.0, -5.0, pole=(1, 0.2, -0.4)), blade=(-10, 170), la=A(-50, 0, 8, -84), rl=L(-56, 40, 3, 10), ll=L(40, 76, 3, 20), head=(-16, 0, 0))
    b2 = T(root=(0, -1.8, 0.6), hips=(28, 22, 0), chest=(6, 10, 0), ra=H(6.6, 7.6, -5.4, pole=(1, 0.2, -0.4)), blade=(-10, 170), la=A(40, 0, 8, -80), rl=L(40, 76, 3, 20), ll=L(-56, 40, 3, 10), head=(-16, 0, 0))
    m = tweak(lerp(b1, b2, 0.5), root_pos=(0, 0.8, 0))
    c = Clip(g, 'yuta_blade_run', NOTE)
    c.key(0, b1).key(100, m, 'LINEAR').key(200, b2, 'LINEAR').key(300, m, 'LINEAR').key(400, b1, 'LINEAR').write()
    bs = T(root=(-0.4, -1.0, 3.6), hips=(2, -30, 0), chest=(-6, -34, 0), ra=H(-3.0, 27.0, 9.0), blade=(60, -30), la=A(40, 0, 30, -40), rl=F(2.6, 0, -2.0, toe=56), ll=F(-2.2, 0, 5.0), head=(-8, 0, 0))
    cut('yuta_blade_swing', 110, b1, bs, start=b1, carry=100, k=0.10)
    # jacobs_ladder: one hand raised straight up, palm open to the sky, the other on his chest; face lifted, still.
    jl = ST(root=(0, -0.2, 0), hips=(-4, 0, 0), chest=(-8, 0, 0), ra=A(-174, 0, 8, -8, -30), la=HC(-1.0, -4.0, 4.0, pole=(-1, 0.5, 0)), head=(-24, 0, 0), rl=F(2.4, 0, -0.8, yaw=14), ll=F(-2.4, 0, 0.8, yaw=14))
    c = Clip(g, 'yuta_jacobs_ladder', NOTE)
    c.key(0, SREST).key(360, jl, 'EASE_OUT').breathe(500, jl, period=2400, amp=0.5)
    c.write()


if __name__ == '__main__':
    katana()
    steel()
    techniques()
    awakened()
    print('wrote', len(WRITTEN), 'clips:', ' '.join(WRITTEN))
