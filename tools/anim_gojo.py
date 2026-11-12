#!/usr/bin/env python3
"""
Authors Satoru Gojo's clips (assets/jjk/animations/gojo) with tools/animkit.py.

    python3 tools/anim_gojo.py

How he moves: upright and unhurried, weight back on his heels, the left hand in his pocket whenever it has nothing to
do. He never loads up like a brawler: strikes are flicks, snaps and kicks thrown from standing, and the techniques are
small precise gestures of one hand. The effort only shows where the technique is supposed to cost him (Hollow Purple,
Red MAX). Timings are the abilities' own (the contact times of the clips these replace).
"""
from animkit import *

NOTE = 'tools/anim_gojo.py'

FL, FR = F(-2.1, 0, 0.8), F(2.2, 0, -0.8, yaw=14)
POCKET = A(16, 0, 5, -38, 10)        # the left hand in the pocket
LOOSE = A(4, 0, 6, -12, 0)           # the right arm hanging


def G(root=(0, -0.2, 0), hips=(-3, 6, 0), chest=(-3, 4, 0), ra=LOOSE, la=POCKET, rl=FR, ll=FL, head=(-4, 0, 0), **kw):
    return P(root=root, hips=hips, chest=chest, ra=ra, la=la, rl=rl, ll=ll, head=head, **kw)


REST = G()
AIR = dict(rl=L(-20, 50, 4, 24), ll=L(-44, 70, 4, 24))
AIR0 = G(root=(0, 0.6, 0), **AIR)


def melee():
    g = 'gojo'
    # gojo_light_1: a backhand flick. The hand comes up across his chest and is snapped out at face height from the
    # elbow and wrist; the shoulders hardly turn.
    w = G(hips=(-3, -4, 0), chest=(-2, -16, 0), ra=HC(-2.0, -3.4, 4.6, pole=(0.6, 0.6, 0.2)))
    h = G(root=(0, -0.2, 0.8), hips=(-2, 10, 0), chest=(-2, 16, 0), ra=H(3.4, 20.6, 12.4, wrist=-24), head=(-4, 4, 0))
    c = Clip(g, 'gojo_light_1', NOTE, 450, priority='ATTACK', blendIn=40, blendOut=150)
    c.key(0, REST).key(55, w, 'EASE_OUT')
    c.hit(110, w, h, lag=10, k=0.16, carry=45, settle=lerp(h, REST, 0.5), settle_at=240)
    c.key(450, REST, 'EASE_IN_OUT').write()

    # gojo_light_2: a front snap kick off the lead leg, hands never leaving where they were. Knee up, the shin flicks
    # out and is back under him before the target has moved.
    w = G(root=(0.4, 0, -0.4), hips=(-10, 4, 0), chest=(4, 2, 0), ll=L(-84, 104, 3, 10), rl=F(2.2, 0, -0.8, yaw=14))
    h = G(root=(0.4, 0.2, 0.6), hips=(-18, 0, 0), chest=(8, 0, 0), ll=L(-84, 4, 3, -30), rl=F(2.2, 0, -0.8, toe=14, yaw=14))
    c = Clip(g, 'gojo_light_2', NOTE, 450, priority='ATTACK', blendIn=40, blendOut=150)
    c.key(0, REST).key(60, w, 'EASE_OUT')
    c.key(115, h, 'EASE_IN', marker='impact').key(160, tweak(h, leftShin=(-6, 0, 0)), 'EASE_OUT').key(250, w, 'EASE_IN_OUT')
    c.key(450, REST, 'EASE_IN_OUT').write()

    # gojo_light_3: a roundhouse to the ribs with the right leg; the standing foot turns out, the hips roll over, the
    # arms drift the other way to balance.
    w = G(root=(-0.6, 0, 0), hips=(-4, 26, 0), chest=(0, 10, 0), rl=L(-30, 96, 34, 10), ll=F(-2.1, 0, 0.8, yaw=30), ra=A(-20, 0, 30, -40))
    h = G(root=(-1.0, 0.2, 0.6), hips=(-10, -52, 14), chest=(6, 30, -8), rl=L(-80, 6, 16, -24), ll=F(-2.1, 0, 0.8, toe=12, yaw=70),
          ra=A(20, 0, 40, -30), head=(-2, 0, 0))
    c = Clip(g, 'gojo_light_3', NOTE, 500, priority='ATTACK', blendIn=40, blendOut=150)
    c.key(0, REST).key(65, w, 'EASE_OUT')
    c.hit(125, w, h, lag=24, k=0.10, carry=55)
    c.key(300, G(root=(-0.4, 0, 0.2), hips=(-6, -10, 4), rl=L(-50, 80, 14, 0)), 'EASE_IN_OUT').key(500, REST, 'EASE_IN_OUT').write()

    # gojo_light_4, the finisher: the side kick that sends them away. He leans right back off the line, the knee comes
    # up to his chest, and the heel goes out flat at head height and stays there a beat.
    w = G(root=(-0.8, 0, -0.8), hips=(-16, -40, 8), chest=(2, 14, 0), rl=L(-104, 118, 10, 0), ll=F(-2.1, 0, 0.8, yaw=50), ra=A(-10, 0, 14, -50))
    h = G(root=(-1.2, 0.4, 1.4), hips=(-10, -76, 30), chest=(4, 40, -10), rl=L(-30, 0, 62, 10), ll=F(-2.1, 0, 0.8, toe=10, yaw=96),
          ra=A(26, 0, 24, -20), head=(-4, 0, 0))
    c = Clip(g, 'gojo_light_4', NOTE, 700, priority='ATTACK', blendIn=25, blendOut=200)
    c.key(0, REST).key(100, w, 'EASE_OUT', marker='anticipation')
    c.key(165, h, 'EASE_IN', lag=30, marker='impact').key(300, tweak(h, rightThigh=(-3, 0, 2)), 'EASE_OUT')
    c.key(440, w, 'EASE_IN_OUT', marker='recovery').key(700, REST, 'EASE_IN_OUT').write()

    # Air lights: the same flick, a snap kick, and a turning heel.
    w = G(root=(0, 0.8, 0), chest=(-2, -16, 0), ra=HC(-2.0, -3.4, 4.6, pole=(0.6, 0.6, 0.2)), **AIR)
    h = G(root=(0, 1.2, 0.6), chest=(-2, 16, 0), ra=HC(4.4, -1.6, 9.8, wrist=-24), rl=L(-10, 30, 6, 30), ll=L(-56, 84, 4, 24))
    c = Clip(g, 'gojo_air_1', NOTE, 450, priority='ATTACK', blendIn=50, blendOut=150)
    c.key(0, AIR0).key(55, w, 'EASE_OUT')
    c.hit(110, w, h, lag=10, k=0.16, carry=45, settle=lerp(h, AIR0, 0.6), settle_at=240)
    c.key(450, AIR0, 'EASE_IN_OUT').write()

    w = G(root=(0, 0.8, 0), hips=(-12, 4, 0), chest=(6, 2, 0), ll=L(-90, 110, 3, 10), rl=L(6, 40, 4, 30))
    h = G(root=(0, 1.2, 0.6), hips=(-22, 0, 0), chest=(10, 0, 0), ll=L(-86, 4, 3, -30), rl=L(20, 50, 4, 30))
    c = Clip(g, 'gojo_air_2', NOTE, 450, priority='ATTACK', blendIn=50, blendOut=150)
    c.key(0, AIR0).key(60, w, 'EASE_OUT').key(115, h, 'EASE_IN', marker='impact').key(165, tweak(h, leftShin=(-6, 0, 0)), 'EASE_OUT')
    c.key(260, w, 'EASE_IN_OUT').key(450, AIR0, 'EASE_IN_OUT').write()

    w = G(root=(0, 0.8, 0), hips=(-6, 34, 0), chest=(0, 14, 0), rl=L(-40, 100, 30, 10), ll=L(-20, 40, 4, 20), ra=A(-20, 0, 30, -40))
    h = G(root=(0, 1.4, 0.6), hips=(-14, -56, 16), chest=(8, 32, -8), rl=L(-84, 4, 16, -24), ll=L(-30, 70, 4, 20), ra=A(24, 0, 40, -30))
    c = Clip(g, 'gojo_air_3', NOTE, 500, priority='ATTACK', blendIn=50, blendOut=150)
    c.key(0, AIR0).key(65, w, 'EASE_OUT')
    c.hit(125, w, h, lag=24, k=0.10, carry=55)
    c.key(320, lerp(h, AIR0, 0.6), 'EASE_IN_OUT').key(500, AIR0, 'EASE_IN_OUT').write()

    # gojo_uppercut: no fist at all. A knee lifted through them that opens into a kick straight up past his own
    # shoulder; he rides up after it, hand still in the pocket.
    w = G(root=(0, -2.4, -0.4), hips=(8, 10, 0), chest=(6, 4, 0), rl=F(2.2, 0, -1.6, toe=20), ll=F(-2.1, 0, 0.8), ra=A(20, 0, 10, -40))
    h = G(root=(0, 2.0, 1.0), hips=(-22, 6, 0), chest=(14, 0, 0), rl=L(-150, 6, 4, -34), ll=L(8, 6, 3, 40), ra=A(30, 0, 30, -20), head=(-14, 0, 0))
    c = Clip(g, 'gojo_uppercut', NOTE, 650, priority='ATTACK', blendIn=25, blendOut=200)
    c.key(0, REST).key(95, w, 'EASE_OUT', marker='anticipation')
    c.key(130, G(root=(0, -0.6, 0.4), hips=(-8, 8, 0), rl=L(-96, 110, 4, 0), ll=F(-2.1, 0, 0.8, toe=20)), 'EASE_IN')
    c.key(170, h, 'EASE_IN', marker='impact').key(300, tweak(h, rightThigh=(8, 0, 0), root_pos=(0, 0.6, 0)), 'EASE_OUT')
    c.key(480, G(root=(0, -1.4, 0.4), hips=(4, 6, 0), rl=F(2.2, 0, 1.0)), 'EASE_IN', marker='land').key(650, REST, 'EASE_OUT').write()

    # gojo_downslam: an axe kick out of the air. The leg swings up beside his head, and the heel is dropped through them.
    w = G(root=(0, 1.6, -0.4), hips=(-20, 0, 0), chest=(12, 0, 0), rl=L(-158, 4, 6, -20), ll=L(-10, 60, 4, 30), ra=A(-40, 0, 40, -30), head=(-10, 0, 0))
    h = G(root=(0, 0.4, 1.2), hips=(10, 0, 0), chest=(8, 0, 0), rl=L(-52, 2, 4, 10), ll=L(-50, 96, 4, 30), ra=A(30, 0, 34, -20), head=(16, 0, 0))
    c = Clip(g, 'gojo_downslam', NOTE, 650, priority='ATTACK', blendIn=25, blendOut=200)
    c.key(0, AIR0).key(105, w, 'EASE_OUT', marker='anticipation')
    c.hit(170, w, h, lag=10, k=0.12, carry=70)
    c.key(430, G(root=(0, -2.6, 0.6), hips=(10, 6, 0), chest=(8, 2, 0), rl=F(2.2, 0, 2.6), ll=F(-2.1, 0, -0.6, toe=20)), 'EASE_IN', marker='land')
    c.key(650, REST, 'EASE_IN_OUT').write()

    # gojo_stomp: he does not even bend for it. A look down, the foot lifted lazily, the heel put through them.
    w = G(root=(-0.6, 0, 0), hips=(-6, 6, 3), chest=(4, 2, 0), rl=L(-70, 96, 4, 10), head=(30, 0, 0))
    h = G(root=(0.4, -1.4, 0.6), hips=(6, 4, -2), chest=(6, 2, 0), rl=F(2.2, 0, 2.6), ll=F(-2.1, 0, 0.2, toe=10), head=(36, 0, 0))
    c = Clip(g, 'gojo_stomp', NOTE, 650, priority='ATTACK', blendIn=75, blendOut=150)
    c.key(0, REST).key(100, w, 'EASE_OUT', marker='anticipation')
    c.key(165, h, 'EASE_IN', marker='impact').key(230, tweak(h, root_pos=(0, 0.5, 0)), 'EASE_OUT').key(420, h, 'EASE_IN_OUT')
    c.key(650, REST, 'EASE_IN_OUT').write()

    # gojo_sprint: a flying side kick out of the run, the body laid back along the line of the leg.
    run = G(root=(0, -0.8, 0), hips=(20, 8, 0), chest=(4, -10, 0), rl=L(-46, 40, 3, 10), ll=L(36, 66, 3, 20), ra=A(30, 0, 8, -50))
    w = G(root=(0, 1.2, 1.0), hips=(-10, -40, 6), chest=(8, 16, 0), rl=L(-100, 116, 10, 0), ll=L(10, 70, 4, 30), ra=A(-10, 0, 20, -50))
    h = G(root=(0, 1.8, 5.0), hips=(-10, -78, 34), chest=(4, 40, -10), rl=L(-30, 0, 60, 10), ll=L(-30, 100, -10, 30), ra=A(30, 0, 24, -20))
    c = Clip(g, 'gojo_sprint', NOTE, 800, priority='MOVEMENT', blendIn=50, blendOut=200)
    c.key(0, run).key(100, w, 'EASE_OUT', marker='anticipation')
    c.key(170, h, 'EASE_IN', lag=26, marker='impact').key(330, tweak(h, root_pos=(0, -0.6, -0.6)), 'EASE_OUT')
    c.key(500, G(root=(0, -2.2, 3.4), hips=(6, 20, 0), chest=(6, -6, 0), rl=F(2.4, 0, 5.4), ll=F(-2.1, 0, 0.6, toe=20)), 'EASE_IN', marker='land')
    c.key(800, REST, 'EASE_IN_OUT').write()

    # gojo_heavy_charge: one hand lifted in front of him, middle finger curled behind the thumb. He is going to flick
    # them. Held, perfectly at ease.
    hc = G(root=(0, -0.3, -0.4), hips=(-4, 16, 0), chest=(-2, 12, 0), ra=HC(3.0, -4.6, 6.4, pole=(0.6, 1, 0), wrist=-30), head=(-4, 0, 3))
    c = Clip(g, 'gojo_heavy_charge', NOTE, 350, hold=True, priority='ATTACK', blendIn=75, blendOut=150)
    c.key(0, REST).key(220, lerp(REST, hc, 0.85), 'EASE_OUT').breathe(350, hc, period=1800, amp=0.5)
    c.write()

    # gojo_heavy: the flick. The arm straightens, the wrist snaps, and the only follow-through is the hand opening and
    # drifting down as he watches where they went.
    deep = tweak(hc, rightHand=(-20, 0, 0), chest=(0, 6, 0))
    h = G(root=(0, -0.3, 1.4), hips=(-2, -8, 0), chest=(2, -18, 0), ra=H(0.8, 19.8, 14.6, wrist=34), head=(-4, 0, 0))
    c = Clip(g, 'gojo_heavy', NOTE, 800, priority='ATTACK', blendIn=25, blendOut=250)
    c.key(0, hc).key(130, deep, 'EASE_OUT', marker='anticipation')
    c.key(205, h, 'EASE_IN', lag=14, marker='impact').key(260, tweak(h, rightHand=(16, 0, 0), rightArm=(-4, 0, 0)), 'EASE_OUT')
    c.key(520, G(hips=(-3, 0, 0), chest=(-2, -6, 0), ra=H(1.6, 17.0, 11.0, wrist=20)), 'EASE_IN_OUT', marker='recovery')
    c.key(800, REST, 'EASE_IN_OUT').write()


def techniques():
    g = 'gojo'
    # guard_gojo: he doesn't guard, Infinity does. Weight on one hip, a palm lifted idly between them.
    gd = G(root=(-0.6, -0.2, -0.3), hips=(-4, 8, 4), chest=(-4, 2, -3), ra=HC(1.8, -2.6, 6.6, pole=(0.7, 1, 0), wrist=-40), rl=F(2.6, 0, 1.4, toe=24),
           head=(-6, 0, 5))
    c = Clip(g, 'guard_gojo', NOTE)
    c.key(0, lerp(REST, gd, 0.5)).key(160, gd, 'OVERSHOOT').breathe(250, gd, period=2200, amp=0.5)
    c.write()

    # blue_cast: the hand drops back by his hip, then the arm is thrown out to point at them, two fingers, side-on,
    # and stays dead level while Blue pulls. The shoulders settle only once it has them.
    w = G(hips=(-3, 24, 0), chest=(-2, 16, 0), ra=A(26, 0, 14, -50, 10), head=(-4, 0, 0))
    pt = G(root=(0, -0.4, 0.6), hips=(-2, -30, 0), chest=(0, -30, 0), ra=H(0.4, 20.4, 14.4), rl=F(2.6, 0, -2.2, yaw=40), head=(-2, 0, 0))
    c = Clip(g, 'blue_cast', NOTE)
    c.key(0, REST).key(150, w, 'EASE_OUT', marker='anticipation')
    c.key(320, pt, 'OVERSHOOT', lag=40, marker='point').key(550, tweak(pt, rightHand=(-14, 0, 0), chest=(1, -3, 0)), 'EASE_IN_OUT', marker='blue formed')
    c.key(950, tweak(pt, rightArm=(2, 0, 0), chest=(2, 0, 0)), 'EASE_IN_OUT').key(1200, lerp(pt, REST, 0.7), 'EASE_IN_OUT').write()

    # blue_kick: they hang in front of him. He takes his time: a small step back onto the left, the knee folds up, and
    # the sole goes through their chest.
    w = G(root=(-0.6, -0.6, -1.4), hips=(-8, -30, 4), chest=(4, 10, 0), rl=L(-94, 112, 8, 0), ll=F(-2.1, 0, 0.4, yaw=40), ra=A(-8, 0, 12, -40))
    h = G(root=(-1.0, 0.2, 2.2), hips=(-8, -70, 26), chest=(4, 38, -8), rl=L(-30, 0, 60, 10), ll=F(-2.1, 0, 0.4, toe=14, yaw=84), ra=A(24, 0, 24, -20))
    c = Clip(g, 'blue_kick', NOTE)
    c.key(0, REST).key(250, w, 'EASE_OUT', marker='anticipation')
    c.key(400, h, 'EASE_IN', lag=34, marker='impact').key(480, tweak(h, rightThigh=(-4, 0, 2)), 'EASE_OUT').key(600, lerp(h, w, 0.7), 'EASE_IN_OUT').write()

    # max_blue_cast: both hands this time, cupped round the place it will open, knees bent against the pull of it.
    form = G(root=(0, -2.2, -1.4), hips=(-6, 0, 0), chest=(8, 0, 0), ra=H(2.6, 18.0, 11.5, pole=(1, -0.2, 0)), la=H(-2.6, 18.0, 11.5, pole=(-1, -0.2, 0)),
             rl=F(3.4, 0, -2.2, yaw=20), ll=F(-3.2, 0, 1.6, yaw=10), head=(2, 0, 0))
    c = Clip(g, 'max_blue_cast', NOTE, tremble=0.4)
    c.key(0, G(ra=A(-30, 0, 24, -40), la=A(-30, 0, 24, -40)))
    c.key(400, form, 'OVERSHOOT', lag=50, marker='formed')
    c.key(800, tweak(form, chest=(-3, 0, 0), rightArm=(0, 0, 8), leftArm=(0, 0, -8), root_pos=(0, 0.4, -0.3)), 'EASE_IN_OUT')
    c.key(1100, lerp(form, REST, 0.75), 'EASE_OUT').write()

    # red_charge: the arm swings low behind him and comes up in a long arc to above his head, fingers spread, where Red
    # gathers. Chin up, watching it.
    sw = G(hips=(-2, 22, 0), chest=(4, 20, 0), ra=A(42, 0, 16, -16), head=(0, 0, 0))
    up = G(root=(0, 0, -0.4), hips=(-6, -8, 0), chest=(-8, -14, 0), ra=A(-166, -10, -6, -22, -20), head=(-16, 0, 0), rl=F(2.4, 0, -1.6, yaw=20))
    c = Clip(g, 'red_charge', NOTE)
    c.key(0, REST).key(120, sw, 'EASE_OUT', marker='anticipation').key(350, up, 'EASE_OUT', lag=30, marker='raise')
    c.breathe(600, tweak(up, rightArm=(-4, 0, 0)), period=1500, amp=0.6)
    c.write()

    # red_release: the raised hand is cut down to point and Red goes with it; his weight follows the arm forward a
    # half-step, then he straightens.
    fire = G(root=(0, -0.8, 1.6), hips=(4, -26, 0), chest=(8, -22, 0), ra=H(0.6, 19.0, 15.0, wrist=10), rl=F(2.4, 0, -2.0, toe=24, yaw=-30), head=(0, 0, 0))
    c = Clip(g, 'red_release', NOTE)
    c.key(0, up).key(90, fire, 'EASE_IN', lag=20, marker='release')
    c.key(180, tweak(fire, rightArm=(8, 0, 0), chest=(3, -4, 0), root_pos=(0, -0.3, 0.4)), 'EASE_OUT', marker='follow-through')
    c.key(600, lerp(fire, REST, 0.8), 'EASE_IN_OUT', marker='recovery').key(700, REST, 'EASE_IN_OUT').write()

    # max_red_charge: this one he has to hold on to. Long low stance, the arm out along his eye-line with the other hand
    # clamped on its wrist, shaking with it.
    mr = G(root=(0.6, -3.6, -1.6), hips=(8, -24, 0), chest=(8, -20, 0), ra=H(0.2, 17.6, 13.6), la=H(0.6, 17.0, 9.6, pole=(-1, -0.4, 0)),
           rl=F(3.2, 0, -4.6, toe=20, yaw=36), ll=F(-2.8, 0, 3.8), head=(4, 0, 0))
    c = Clip(g, 'max_red_charge', NOTE, tremble=1.0)
    c.key(0, lerp(REST, mr, 0.3)).key(330, mr, 'EASE_OUT', lag=60).breathe(500, mr, period=1100, amp=1.0)
    c.write()

    # red_upside_down: he is simply there, hanging head-down in the air behind them with one knee bent and a hand in
    # his pocket, a finger already on the back of their head.
    ud = P(root=(0, 26, 0), hips=(180, 0, 0), chest=(-14, 0, 0), head=(-34, 0, 0), follow=1.0, ra=A(-128, 0, 4, -8), la=POCKET,
           rl=L(-8, 24, 6, 20), ll=L(-52, 96, 6, 20))
    c = Clip(g, 'red_upside_down', NOTE)
    c.key(0, tweak(ud, rightArm=(60, 0, 0))).key(200, ud, 'OVERSHOOT', marker='point')
    c.key(550, tweak(ud, rightArm=(-4, 0, 0), leftShin=(8, 0, 0)), 'EASE_IN_OUT', marker='fire').key(700, tweak(ud, rightArm=(30, 0, 0)), 'EASE_OUT').write()

    # limitless_air_kick: out of nothing above them, knee already at his chest; the leg is stamped straight down and his
    # arms float up with the drop.
    ch = G(root=(0, 1.0, 0), hips=(-20, 0, 0), chest=(14, 0, 0), rl=L(-120, 124, 4, 0), ll=L(10, 40, 4, 30), ra=A(-30, 0, 30, -40), la=A(-30, 0, 30, -40),
           head=(24, 0, 0))
    dn = G(root=(0, -0.6, 0.6), hips=(14, 0, 0), chest=(4, 0, 0), rl=L(-20, 2, 4, -30), ll=L(-60, 110, 4, 30), ra=A(-140, 0, 34, -30), la=A(-130, 0, 36, -34),
           head=(30, 0, 0))
    c = Clip(g, 'limitless_air_kick', NOTE)
    c.key(0, ch).key(150, dn, 'EASE_IN', marker='impact').key(230, tweak(dn, rightThigh=(6, 0, 0), root_pos=(0, 0.5, 0)), 'EASE_OUT')
    c.key(500, lerp(dn, AIR0, 0.8), 'EASE_IN_OUT').write()

    # limitless_raise: two fingers lifted beside his face, a small tilt of the head: the space between is about to go.
    rs = G(hips=(-3, -6, 0), chest=(-3, -10, 0), ra=HC(4.4, 1.0, 3.6, pole=(1, 0.4, 0), wrist=-10), head=(-6, 0, 6))
    c = Clip(g, 'limitless_raise', NOTE)
    c.key(0, REST).key(250, rs, 'OVERSHOOT', marker='sign').key(600, tweak(rs, rightArm=(-4, 0, 0), head=(-2, 0, 2)), 'EASE_IN_OUT').write()

    # teleport: arriving. He drops the last inch out of the air onto soft knees, coat still moving, and stands up out of it.
    c = Clip(g, 'teleport', NOTE)
    c.key(0, G(root=(0, -2.0, 0), hips=(12, 6, 0), chest=(6, 4, 0), ra=A(-24, 0, 26, -30), la=A(-20, 0, 24, -34), rl=F(2.6, 0, -1.2), ll=F(-2.5, 0, 1.2)))
    c.key(180, G(root=(0, -1.0, 0), hips=(4, 6, 0), ra=A(-6, 0, 14, -20), la=A(4, 0, 10, -30)), 'EASE_OUT').key(400, REST, 'EASE_IN_OUT').write()

    # teleport_strike: he appears already mid-punch. A straight right from the hip with the whole turn of the body
    # behind it, then the hand drops and he is standing there as if nothing happened.
    w = G(root=(0.4, -1.6, -0.6), hips=(4, 30, 0), chest=(2, 26, 0), ra=HC(6.0, -6.0, -0.5, pole=(1, 0.2, -1)), la=A(-30, 0, 10, -80),
          rl=F(2.6, 0, -2.6, toe=16), ll=F(-2.3, 0, 2.2))
    h = G(root=(-0.4, -1.6, 3.2), hips=(8, -30, 0), chest=(6, -34, -3), ra=H(-0.4, 18.6, 16.4), la=A(20, 0, 12, -60),
          rl=F(2.6, 0, -2.4, toe=44, yaw=-30), ll=F(-2.3, 0, 3.4))
    c = Clip(g, 'teleport_strike', NOTE)
    c.key(0, w)
    c.hit(100, w, h, lag=26, k=0.10, carry=60)
    c.key(300, lerp(h, REST, 0.4), 'EASE_IN_OUT').key(500, REST, 'EASE_IN_OUT').write()

    # face_grater_drag / face_grater_throw (Limitless after Rapid Punches): he has them by the face and walks; then a
    # bored underarm toss.
    drag = G(root=(0, -2.2, 1.0), hips=(22, -14, 0), chest=(12, -16, 0), ra=H(2.6, 5.5, 8.5, pole=(1, 0, -1)), rl=L(-44, 50, 3, 10), ll=L(34, 60, 3, 20),
             head=(14, 6, 0))
    c = Clip(g, 'face_grater_drag', NOTE, 200, hold=True, priority='SPECIAL', blendIn=50, blendOut=100, tremble=0.8)
    c.key(0, drag).key(200, tweak(drag, rightArm=(4, 0, 0), chest=(2, 0, 0)), 'EASE_IN_OUT').write()
    w = G(root=(0.4, -2.4, -0.6), hips=(10, 30, 0), chest=(8, 22, 0), ra=A(40, 0, 20, -20), rl=F(2.6, 0, -2.6, toe=16), ll=F(-2.3, 0, 2.4), head=(4, 0, 0))
    h = G(root=(-0.2, -0.6, 1.6), hips=(-4, -24, 0), chest=(-6, -26, 0), ra=A(-140, 0, 16, -10, -30), rl=F(2.6, 0, -2.4, toe=36), ll=F(-2.3, 0, 2.6),
          head=(-10, 0, 0))
    c = Clip(g, 'face_grater_throw', NOTE, 700, priority='SPECIAL', blendIn=25, blendOut=250)
    c.key(0, drag).key(140, w, 'EASE_OUT', marker='anticipation')
    c.hit(260, w, h, lag=40, k=0.12, carry=80)
    c.key(700, REST, 'EASE_IN_OUT').write()

    # infinity_on: upper body only (he can keep walking). A palm shown to the world, held, lowered.
    palm = only(G(chest=(-3, -8, 0), ra=HC(3.4, -3.0, 7.6, pole=(0.8, 1, 0), wrist=-50), head=(-4, 0, 0)), 'chest', 'rightArm', 'rightForearm', 'rightHand')
    rest = only(REST, 'chest', 'rightArm', 'rightForearm', 'rightHand')
    c = Clip(g, 'infinity_on', NOTE, layer='UPPER_BODY')
    c.key(0, rest).key(200, palm, 'OVERSHOOT', marker='on').key(600, tweak(palm, rightArm=(3, 0, 0)), 'EASE_IN_OUT').key(900, rest, 'EASE_IN_OUT').write()

    # awaken: two fingers hook the blindfold and draw it up and off; the head comes down with the eyes open, and both
    # hands open at his sides as he rises a little off his heels. The Honored One.
    hook = G(chest=(-2, -6, 0), ra=HC(1.4, 1.6, 4.4, pole=(1, 0.3, 0)), head=(10, 0, 0))
    lift = G(root=(0, 0, -0.3), chest=(-8, -4, 0), ra=HC(2.4, 5.0, 3.0, pole=(1, 0.3, 0)), head=(-16, 0, 0))
    off = G(chest=(-4, 12, 0), ra=A(-70, 0, 60, -40, -10), head=(-8, 6, 0))
    open_ = G(root=(0, 0.9, 0), hips=(-6, 0, 0), chest=(-10, 0, 0), ra=A(-20, 0, 34, -24, -30), la=A(-20, 0, 34, -24, -30),
              rl=F(2.2, 0, -0.6, toe=26), ll=F(-2.1, 0, 0.6, toe=26), head=(4, 0, 0))
    c = Clip(g, 'awaken', NOTE)
    c.key(0, REST).key(350, hook, 'EASE_OUT', marker='blindfold').key(600, lift, 'EASE_IN_OUT').key(850, off, 'EASE_IN', marker='off')
    c.key(1200, open_, 'EASE_OUT', lag=80, marker='six eyes').key(1900, tweak(open_, chest=(2, 0, 0), root_pos=(0, 0.3, 0)), 'EASE_IN_OUT')
    c.key(2300, REST, 'EASE_IN_OUT').key(2500, REST).write()

    # domain_sign: the right hand comes up in front of his face, index and middle finger crossed; head bowed, the other
    # hand still in his pocket.
    sign = G(root=(0, -0.2, 0), hips=(-2, 0, 0), chest=(2, -4, 0), ra=HC(0.6, -0.6, 5.0, pole=(1, 0.8, 0), wrist=-20), head=(12, 0, 0), rl=F(2.2, 0, -0.4), ll=F(-2.1, 0, 0.4))
    c = Clip(g, 'domain_sign', NOTE)
    c.key(0, REST).key(150, G(ra=A(10, 0, 14, -70), head=(4, 0, 0)), 'EASE_OUT', marker='anticipation').key(400, sign, 'OVERSHOOT', marker='sign')
    c.breathe(800, sign, period=2000, amp=0.4)
    c.write()

    # domain_release: the sign breaks. Arms flung wide, chest and face turned up, lifted onto his toes as the void opens
    # round him; he hangs there, then comes down.
    ex = G(root=(0, 1.6, -0.4), hips=(-8, 0, 0), chest=(-16, 0, 0), ra=A(-30, 0, 78, -14, -20), la=A(-30, 0, 78, -14, -20),
           rl=F(2.2, 0, -0.4, toe=40), ll=F(-2.1, 0, 0.4, toe=40), head=(-28, 0, 0))
    c = Clip(g, 'gojo_domain_release', NOTE, 1200, priority='AWAKENING', blendIn=120, blendEase='EASE_IN', blendOut=300)
    c.key(0, sign).key(120, ex, 'EASE_IN', lag=20, marker='expand')
    c.key(420, tweak(ex, rightArm=(-8, 0, 6), leftArm=(-8, 0, -6), chest=(-3, 0, 0), root_pos=(0, 0.6, 0)), 'EASE_OUT', marker='float')
    c.key(1050, G(ra=A(-4, 0, 16, -10), la=A(-4, 0, 16, -10)), 'EASE_IN_OUT', marker='settle').key(1200, REST, 'EASE_IN_OUT').write()

    # Hollow Purple. purple_blue: the left arm swings out to the side, palm up, and he looks at what forms there.
    pb = G(root=(0, -0.6, 0), hips=(-2, 0, 0), chest=(-2, 8, 0), la=A(-20, 0, 82, -16, -30), ra=LOOSE, rl=F(2.8, 0, -0.6, yaw=16), ll=F(-2.7, 0, 0.6, yaw=16),
           head=(4, -50, 0))
    c = Clip(g, 'purple_blue', NOTE)
    c.key(0, REST).key(220, pb, 'EASE_OUT', marker='reach').breathe(700, pb, period=1600, amp=0.5)
    c.write()
    # purple_red: the right arm out the other way, Red in that hand; both arms wide, feet set, eyes front again.
    pr = G(root=(0, -1.0, 0), hips=(-2, 0, 0), chest=(-4, 0, 0), la=A(-22, 0, 84, -14, -30), ra=A(-22, 0, 84, -14, -30),
           rl=F(3.2, 0, -0.6, yaw=20), ll=F(-3.1, 0, 0.6, yaw=20), head=(-2, 0, 0))
    c = Clip(g, 'purple_red', NOTE)
    c.key(0, pb).key(220, pr, 'EASE_OUT', marker='reach').breathe(700, pr, period=1600, amp=0.5)
    c.write()
    # purple_fusion: the arms are hauled in against each other and the hands meet in front of his chest; he has to lean
    # into it to keep them together.
    pf = G(root=(0, -2.4, -0.8), hips=(6, 0, 0), chest=(10, 0, 0), ra=H(1.3, 17.2, 8.8, pole=(1, 0.1, -0.2)), la=H(-1.3, 17.2, 8.8, pole=(-1, 0.1, -0.2)),
           rl=F(3.2, 0, -2.4, toe=14, yaw=24), ll=F(-3.1, 0, 2.0, yaw=10), head=(6, 0, 0))
    c = Clip(g, 'purple_fusion', NOTE)
    c.key(0, pr).key(400, pf, 'EASE_IN', lag=60, marker='hands meet')
    c.breathe(800, tweak(pf, chest=(3, 0, 0), root_pos=(0, -0.4, 0)), period=1000, amp=1.2)
    c.write()
    # purple_release: both hands driven forward as it leaves; the recoil stands him up and rocks him onto his back foot.
    fire = G(root=(0, -2.8, 2.6), hips=(14, 0, 0), chest=(12, 0, 0), ra=H(1.4, 17.4, 15.4, pole=(1, 0, 0), wrist=-40), la=H(-1.4, 17.4, 15.4, pole=(-1, 0, 0), wrist=-40),
             rl=F(3.2, 0, -2.4, toe=40, yaw=24), ll=F(-3.1, 0, 3.0, yaw=10), head=(2, 0, 0))
    rec = G(root=(0, -0.8, -1.4), hips=(-8, 0, 0), chest=(-10, 0, 0), ra=A(-60, 0, 30, -40, -20), la=A(-60, 0, 30, -40, -20),
            rl=F(3.2, 0, -3.0, yaw=24), ll=F(-3.1, 0, 2.0, toe=-10, yaw=10), head=(-8, 0, 0))
    c = Clip(g, 'purple_release', NOTE)
    c.key(0, pf).key(80, fire, 'EASE_IN', lag=16, marker='fire').key(300, rec, 'EASE_OUT', marker='recoil')
    c.key(900, G(ra=A(-4, 0, 12, -14), la=A(-4, 0, 12, -14)), 'EASE_IN_OUT', marker='recovery').key(1000, REST, 'EASE_IN_OUT').write()


def rapid():
    g = 'gojo'
    # Rapid Punches (RapidPunches.gif). The one place he boxes: planted wide, a punch every 100 ms on the server's hits.
    FW, BW = F(-3.4, 0, 2.6, yaw=10), F(3.4, 0, -2.6, toe=14, yaw=26)

    def B(**kw):
        d = dict(root=(0, -2.2, 0.6), hips=(8, 0, 0), chest=(8, 0, 0), ra=HC(3.6, -3.4, 4.6), la=HC(-3.6, -3.4, 4.6), rl=BW, ll=FW, head=(2, 0, 0))
        d.update(kw)
        return P(**d)

    setp = B()
    rs = B(root=(-0.4, -2.2, 1.6), hips=(8, -22, 0), chest=(8, -28, 0), ra=H(-0.2, 18.4, 15.6), la=HC(-4.0, -3.6, 3.0))
    ls = B(root=(0.4, -2.2, 1.6), hips=(8, 22, 0), chest=(8, 28, 0), la=H(0.2, 18.4, 15.6), ra=HC(4.0, -3.6, 3.0))
    rh = B(root=(-0.8, -2.4, 1.2), hips=(6, -34, 2), chest=(6, -42, 4), ra=HC(-0.8, -2.6, 7.4, pole=(1, 0.5, 0.2)), la=HC(-4.0, -3.2, 3.0))
    lb = B(root=(0.6, -3.2, 1.4), hips=(12, 26, -3), chest=(12, 28, -4), la=H(0.6, 13.6, 13.0, pole=(-0.6, -1, 0)), ra=HC(4.0, -3.2, 3.2), head=(6, 0, 0))
    c = Clip(g, 'rapid_barrage', NOTE)
    c.key(0, setp, marker='set')
    seq = [(100, rs, 'right straight'), (200, ls, 'left straight'), (300, rh, 'right hook'), (400, lb, 'left body shot'), (500, rs, 'right straight')]
    prev = setp
    for t, p, m in seq:
        c.key(t, p, 'EASE_IN', lag=14, marker=m)
        if t < 500:
            c.key(t + 45, lerp(p, setp, 0.45), 'EASE_OUT')
    c.write()

    # rapid_heavy: three blows that each move him: a rising uppercut, a full-body hook, a stepping left straight (200 /
    # 450 / 700 ms), then both hands swung up over his head for the last one.
    a1 = B(root=(0.6, -4.4, 0), hips=(16, 26, 0), chest=(14, 18, 4), ra=HC(5.0, -10.0, 2.0, pole=(1, 0, -1)), head=(8, 0, 0))
    up = B(root=(-0.4, 0.6, 1.8), hips=(-6, -24, 0), chest=(-8, -24, -4), ra=H(1.0, 27.0, 8.0, pole=(0.4, -0.2, 1)), la=HC(-4.6, -5.0, 1.6),
           rl=F(3.4, 0, -2.6, toe=46, yaw=26), head=(-12, 0, 0))
    a2 = B(root=(-1.4, -2.8, 0.2), hips=(8, -30, 4), chest=(10, -26, 6), la=HC(-6.6, -4.0, 1.4, pole=(-1, 0.2, -0.4)), ra=HC(3.6, -2.6, 4.4), head=(6, -10, 0))
    hk = B(root=(1.0, -2.0, 1.4), hips=(6, 40, -2), chest=(6, 48, -4), la=HC(0.8, -2.4, 7.6, pole=(-1, 0.5, 0.2)), ra=HC(4.0, -2.8, 3.0),
           ll=F(-3.4, 0, 2.6, toe=20, yaw=-44), head=(2, 10, 0))
    a3 = B(root=(-0.4, -2.6, -0.8), hips=(8, -34, 0), chest=(8, -22, 0), la=HC(-5.0, -4.4, 1.6), ra=HC(3.0, -2.2, 6.6))
    st = B(root=(0.6, -2.6, 4.6), hips=(14, 30, 0), chest=(10, 34, 3), la=H(0.4, 17.8, 19.0), ra=HC(5.4, -4.6, -0.5, pole=(1, 0, -1)),
           ll=F(-3.2, 0, 5.6), rl=F(3.4, 0.6, -1.8, toe=54, yaw=26))
    ov = B(root=(0, 0.2, 2.4), hips=(-12, 0, 0), chest=(-18, 0, 0), ra=HC(1.4, 7.0, -1.0, pole=(1, 0.2, 0.6)), la=HC(-1.4, 7.0, -1.0, pole=(-1, 0.2, 0.6)),
           ll=F(-3.2, 0, 5.0, toe=20), rl=F(3.2, 0, 0.6, toe=36), head=(-14, 0, 0))
    c = Clip(g, 'rapid_heavy', NOTE)
    c.key(0, setp).key(120, a1, 'EASE_OUT', marker='anticipation')
    c.key(200, up, 'EASE_IN', lag=24, marker='impact: uppercut').key(260, over(up, a1, 0.08), 'EASE_OUT')
    c.key(340, a2, 'EASE_OUT', marker='wind-up').key(450, hk, 'EASE_IN', lag=28, marker='impact: hook').key(510, over(hk, a2, 0.10), 'EASE_OUT')
    c.key(600, a3, 'EASE_OUT', marker='wind-up').key(700, st, 'EASE_IN', lag=30, marker='impact: left straight').key(760, over(st, a3, 0.08), 'EASE_OUT')
    c.key(860, lerp(st, ov, 0.6), 'EASE_IN', marker='final wind-up').key(1000, ov, 'EASE_OUT').key(1100, tweak(ov, chest=(-4, 0, 0)), 'EASE_IN_OUT').write()

    # rapid_final: cut straight to the hammer landing. Folded over it with both fists at knee height, he sinks through
    # the blow, and only then lets go and stands.
    hm = B(root=(0, -3.4, 3.4), hips=(26, 0, 0), chest=(24, 0, 0), ra=H(1.2, 8.5, 12.0), la=H(-1.2, 8.5, 12.0), ll=F(-3.2, 0, 5.0), rl=F(3.2, 0, 0.6, toe=30),
           head=(14, 0, 0))
    deep = B(root=(0, -4.8, 3.6), hips=(30, 0, 0), chest=(26, 0, 0), ra=H(1.4, 5.5, 11.0), la=H(-1.4, 5.5, 11.0), ll=F(-3.2, 0, 5.0), rl=F(3.2, 0, 0.6, toe=30),
             head=(16, 0, 0))
    c = Clip(g, 'rapid_final', NOTE)
    c.key(0, hm, marker='impact').key(100, deep, 'EASE_OUT', marker='follow-through')
    c.key(400, lerp(deep, REST, 0.35), 'EASE_IN_OUT').key(750, REST, 'EASE_IN_OUT', marker='recovery').key(800, REST).write()


def gif_kicks():
    g = 'gojo'
    # The three kicks keyed frame for frame from the JJS GIFs (RapidPunches.gif, TwofoldKick.gif). Their beats and
    # contact times are the server's and stay exactly where they were; the bodies in between are rebuilt like the rest
    # of his set: real knees and ankles, a planted standing foot, the head spotting the target through the turn, and the
    # left hand in his pocket for as long as he can get away with it.

    # spin_kick (Rapid Punches' opener, contact on the 11th tick = 550 ms). A full turn to the left thrown from
    # standing: the torso tips forward as the trailing leg lifts behind him until he is folded flat over the standing
    # leg with the heel above his own head, and the heel is dropped straight through them as he comes down low.
    ant = G(root=(0.4, -1.6, 0), yaw=24, hips=(2, 8, 0), chest=(8, 14, 0), ra=A(-16, 0, 20, -40), head=(0, 0, 0))
    t1 = P(root=(0, -1.0, 0), yaw=-100, hips=(14, 0, 0), chest=(8, 0, 0), ra=A(-40, 0, 50, -24), la=POCKET,
           rl=L(34, 26, 14, 12), ll=L(-4, 30, 4, 18), head=(-4, 70, 0), follow=1.0)
    t2 = P(root=(0, -0.4, 0.2), yaw=-210, hips=(32, 0, 0), chest=(8, 0, 0), ra=A(-30, 0, 60, -20), la=POCKET,
           rl=L(66, 8, 18, 14), ll=L(-10, 40, 4, 22), head=(-10, 40, 0), follow=1.0)
    t3 = P(root=(0, 0.6, 0.4), yaw=-320, hips=(56, 0, 0), chest=(6, 0, 0), ra=A(-20, 0, 72, -14), la=POCKET,
           rl=L(96, 0, 14, 16), ll=L(-26, 44, 4, 28), head=(-30, 10, 0), follow=1.0)
    top = P(root=(0, 2.2, 1.0), yaw=-360, hips=(72, 0, 0), chest=(4, 0, 0), ra=A(-10, 0, 82, -10), la=A(-10, 0, 82, -10),
            rl=L(104, 0, 8, 20), ll=L(-62, 60, 4, 24), head=(-36, 0, 0), follow=1.0)
    air = P(root=(0, 3.6, 1.6), yaw=-360, hips=(80, 0, 0), chest=(2, 0, 0), ra=A(-6, 0, 86, -8), la=A(-6, 0, 86, -8),
            rl=L(112, 0, 4, 24), ll=L(-70, 70, 4, 30), head=(-40, 0, 0), follow=1.0)
    drop = P(root=(0, -1.2, 2.0), yaw=-360, hips=(30, 0, 0), chest=(26, 0, 0), ra=A(-30, 0, 30, -20), la=A(-20, 0, 36, -24),
             rl=L(-56, 6, 8, 30), ll=F(-2.2, 0, -0.8, rel=True), head=(-6, 0, 0), follow=0.6)
    crouch = G(root=(0, -4.0, 2.4), hips=(26, 0, 0), chest=(20, 0, 0), ra=A(-20, 0, 24, -30), la=A(-14, 0, 28, -34),
               rl=F(2.4, 0, 4.6, toe=10), ll=F(-2.2, 0, -0.4), head=(-4, 0, 0))
    c = Clip(g, 'spin_kick', NOTE, 700).spins()
    c.key(0, REST).key(80, ant, 'EASE_OUT', marker='anticipation').key(170, t1, 'LINEAR', marker='spin')
    c.key(260, t2, 'LINEAR').key(340, t3, 'LINEAR').key(420, top, 'EASE_OUT', marker='heel over his head').key(480, air, 'LINEAR', marker='airborne')
    c.key(540, drop, 'EASE_IN', marker='impact')
    c.key(560, {'root': {'rot': [0.0, 0.0, 0.0], 'pos': [0.0, -1.2, 2.0]}}, 'STEP')
    c.key(620, crouch, 'EASE_OUT', marker='land').key(700, REST, 'EASE_IN_OUT', marker='recovery').write()

    # twofold_1 (Twofold Kick: the first kick lands on the 8th tick = 400 ms; the clip ends at 1100 ms, on the frame the
    # second kick lands and twofold_2 cuts in). A spinning back kick out of a quarter turn, the hand still in the
    # pocket, the heel thrust up into them over his own hip while he looks back at them over his shoulder; the turn
    # carries on round and he lands wide, sits, and throws a back-flip, the kicking leg leading over the top.
    ant = G(root=(0.4, -1.6, -0.4), yaw=24, hips=(4, 8, 0), chest=(8, 14, 0), ra=A(-20, 0, 24, -40), head=(0, 0, 0))
    cham = P(root=(0, -0.6, 0), yaw=-90, hips=(12, 0, 0), chest=(4, -10, 0), ra=A(-50, 0, 30, -60), la=POCKET,
             rl=L(-84, 104, 12, 10), ll=L(-2, 24, 4, 14), head=(-2, 80, 0), follow=1.0)
    kick = P(root=(0, 0.4, 0.6), yaw=-165, hips=(36, 0, 4), chest=(8, -6, 0), ra=A(-40, 0, 44, -24), la=POCKET,
             rl=L(72, 2, 16, 24), ll=L(-30, 36, 4, 26), head=(-14, 84, 0), follow=1.0)
    through = P(root=(0, 0.6, 0.6), yaw=-200, hips=(40, 0, 4), chest=(8, -6, 0), ra=A(-44, 0, 48, -22), la=POCKET,
                rl=L(80, 4, 18, 26), ll=L(-32, 38, 4, 28), head=(-14, 84, 0), follow=1.0)
    recoil = P(root=(0, -0.8, 0.4), yaw=-250, hips=(18, 0, 0), chest=(6, 0, 0), ra=A(-30, 0, 40, -30), la=POCKET,
               rl=L(24, 70, 10, 10), ll=F(-2.6, 0, 0.8, rel=True), head=(-6, 50, 0), follow=1.0)
    land = P(root=(0, -2.4, 0.4), yaw=-335, hips=(8, 0, 0), chest=(14, 0, 0), ra=A(-30, 0, 50, -30), la=A(-20, 0, 40, -40),
             rl=F(3.0, 2.4, -1.0, rel=True), ll=F(-3.0, 2.4, 1.2, rel=True), head=(-6, 20, 0), follow=1.0)
    land2 = P(root=(0, -2.6, 0.4), yaw=-360, hips=(10, 0, 0), chest=(16, 0, 0), ra=A(-26, 0, 46, -30), la=A(-18, 0, 38, -40),
              rl=F(3.0, 2.6, -1.0, rel=True), ll=F(-3.0, 2.6, 1.2, rel=True), head=(-8, 4, 0), follow=1.0)
    sit = P(root=(0, -4.2, 0), hips=(18, 0, 0), chest=(30, 0, 0), ra=A(44, 0, 20, -10), la=A(44, 0, 20, -10),
            rl=F(2.8, 0, -1.0), ll=F(-2.8, 0, 1.2), head=(-20, 0, 0), follow=0.5)
    off = P(root=(0, 2.0, 0.6), hips=(-10, 0, 0), chest=(-10, 0, 0), ra=A(-120, 0, 30, -20), la=A(-120, 0, 30, -20),
            rl=L(-20, 20, 4, 30), ll=L(-16, 24, 4, 30), head=(-24, 0, 0), follow=1.0)
    flip = P(root=(0, 9.0, 0.6), hips=(-120, 0, 0), chest=(-10, 0, 0), ra=A(-150, 0, 30, -20), la=A(-150, 0, 30, -20),
             rl=L(-110, 40, 5, 10), ll=L(-60, 100, 5, 10), head=(-10, 0, 0), follow=1.0)
    over = P(root=(0, 10.4, 0.8), hips=(-220, 0, 0), chest=(-6, 0, 0), ra=A(-140, 0, 44, -14), la=A(-140, 0, 44, -14),
             rl=L(-140, 10, 4, 20), ll=L(-40, 96, 4, 10), head=(-8, 0, 0), follow=1.0)
    top = P(root=(0, 10.0, 1.0), hips=(-330, 0, 0), chest=(-4, 0, 0), ra=A(-120, 0, 60, -10), la=A(-120, 0, 60, -10),
            rl=L(-160, 0, 4, 24), ll=L(-30, 90, 4, 10), head=(-6, 0, 0), follow=1.0)
    c = Clip(g, 'twofold_1', NOTE, 1100).spins()
    c.key(0, REST).key(120, ant, 'EASE_OUT', marker='anticipation').key(280, cham, 'LINEAR', marker='chamber')
    c.key(400, kick, 'EASE_IN', marker='impact').key(460, through, 'EASE_OUT', marker='follow-through').key(560, recoil, 'LINEAR', marker='recoil')
    c.key(660, land, 'EASE_OUT', marker='land').key(730, land2, 'LINEAR')
    c.key(740, {'root': {'rot': [0.0, 0.0, 0.0], 'pos': [0.0, -2.6, 0.4]}}, 'STEP')
    c.key(840, sit, 'EASE_OUT', marker='anticipation: jump').key(900, off, 'EASE_IN', marker='take-off')
    c.key(960, flip, 'LINEAR', marker='flip').key(1030, over, 'LINEAR').key(1100, top, 'LINEAR', marker='leg over the top').write()

    # twofold_2: cut onto the second kick as it lands (the server's hit is on this clip's first frame). He has come
    # almost all the way round; the leading heel is driven up through them, the flip finishes over it, and he drops
    # to a crouched landing and stands up out of it.
    hit = P(root=(0, 7.0, 1.0), hips=(-24, 0, 0), chest=(-8, 0, 0), ra=A(-140, 0, 44, -16), la=A(-140, 0, 44, -16),
            rl=L(-118, 0, 4, 24), ll=L(16, 72, 4, 10), head=(8, 0, 0), follow=1.0)
    thr = P(root=(0, 3.6, 1.0), hips=(8, 0, 0), chest=(2, 0, 0), ra=A(-100, 0, 60, -20), la=A(-100, 0, 60, -20),
            rl=L(-60, 30, 5, 20), ll=L(-10, 60, 5, 20), head=(6, 0, 0), follow=1.0)
    crouch = G(root=(0, -3.2, 1.0), hips=(22, 0, 0), chest=(16, 0, 0), ra=A(-30, 0, 40, -40), la=A(-30, 0, 40, -40),
               rl=F(2.6, 0, 1.6, toe=10), ll=F(-2.6, 0, 0.2), head=(2, 0, 0))
    c = Clip(g, 'twofold_2', NOTE, 600)
    c.key(0, hit, marker='impact').key(120, thr, 'EASE_OUT', marker='follow-through').key(260, crouch, 'EASE_IN', marker='land')
    c.key(340, tweak(crouch, root_pos=(0, 0.6, 0)), 'EASE_OUT').key(520, lerp(crouch, REST, 0.8), 'EASE_IN_OUT', marker='recovery').key(600, REST).write()


if __name__ == '__main__':
    melee()
    techniques()
    rapid()
    gif_kicks()
    print('wrote', len(WRITTEN), 'clips:', ' '.join(WRITTEN))
