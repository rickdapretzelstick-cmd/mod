#!/usr/bin/env python3
"""
Authors Kinji Hakari's clips (assets/jjk/animations/hakari) with tools/animkit.py.

    python3 tools/anim_hakari.py

How he moves: a street fighter who is enjoying himself. Hands low and loose, chin out, never still: he rocks from
foot to foot and every blow is a swing with the whole shoulder behind it, thrown a little too wide and landed anyway.
Kicks are stamps and boots, not technique. In Jackpot the same swings come faster and looser. Timings are the
abilities' own (the contact times of the clips these replace).
"""
from animkit import *

NOTE = 'tools/anim_hakari.py'

FL, FR = F(-3.2, 0, 2.2, yaw=14), F(3.4, 0, -2.2, toe=10, yaw=24)
LO_R, LO_L = HC(4.6, -7.4, 3.6), HC(-4.4, -6.4, 5.0)


def K(root=(0, -1.8, 0), hips=(8, 12, 0), chest=(8, 8, 0), ra=LO_R, la=LO_L, rl=FR, ll=FL, head=(-2, 0, 0), **kw):
    return P(root=root, hips=hips, chest=chest, ra=ra, la=la, rl=rl, ll=ll, head=head, **kw)


REST = K()
AIR = dict(rl=L(-24, 60, 8, 24), ll=L(-50, 80, 8, 24))
AIR0 = K(root=(0, 0.6, 0), **AIR)


def swing(c, t_w, t_h, w, h, end, t_end, lag=34, k=0.14, carry=65, rec=None, t_rec=None):
    c.key(0, REST if rec is not False else w).key(t_w, w, 'EASE_OUT', marker='anticipation')
    c.hit(t_h, w, h, lag=lag, k=k, carry=carry)
    if rec is not None and rec is not False:
        c.key(t_rec, rec, 'EASE_OUT', marker='recovery')
    c.key(t_end, end, 'EASE_IN_OUT').write()


def melee():
    g = 'hakari'
    M = dict(priority='ATTACK', blendIn=40, blendOut=150)
    # hakari_light_1: a looping right swing from down by his hip, shoulder rolled over the top of it.
    w = K(root=(0.8, -2.4, -0.6), hips=(8, 34, -3), chest=(10, 24, -4), ra=HC(7.4, -6.0, -1.0, pole=(1, 0.3, -0.6)), la=HC(-3.4, -4.0, 6.0))
    h = K(root=(-0.8, -2.0, 2.6), hips=(10, -32, 3), chest=(10, -42, 6), ra=HC(-1.6, -3.0, 7.8, pole=(1, 0.8, 0)), la=HC(-6.0, -6.0, 0.5),
          rl=F(3.4, 0, -2.0, toe=40, yaw=-20))
    swing(Clip(g, 'hakari_light_1', NOTE, 450, **M), 50, 112, w, h, REST, 450, lag=26)
    # hakari_light_2: the left comes back the other way off the rebound, an overhand clubbing down across them.
    w = K(root=(-0.8, -1.6, 0.6), hips=(4, -30, 3), chest=(0, -28, 4), la=HC(-6.4, 1.5, 0.5, pole=(-1, 0.4, -0.4)), ra=HC(3.0, -5.0, 6.0), head=(-4, -8, 0))
    h = K(root=(0.6, -2.8, 2.4), hips=(14, 30, -3), chest=(18, 38, -6), la=HC(1.6, -6.0, 8.0, pole=(-1, 0.8, 0)), ra=HC(6.2, -6.0, 0.0),
          ll=F(-3.2, 0, 2.6, toe=14, yaw=-24), head=(4, 6, 0))
    swing(Clip(g, 'hakari_light_2', NOTE, 450, **M), 52, 114, w, h, REST, 450, lag=26)
    # hakari_light_3: he grabs air with the left and drives the right knee up through their middle.
    w = K(root=(0, -2.6, -0.8), hips=(14, 14, 0), chest=(12, 6, 0), ra=HC(4.0, -2.0, 7.0), la=HC(-4.0, -2.0, 7.0), rl=F(3.4, 0, -3.4, toe=30))
    h = K(root=(0, 0.4, 2.4), hips=(-22, -10, 0), chest=(24, 0, 0), ra=HC(3.6, -6.5, 5.0), la=HC(-3.6, -6.5, 5.0), rl=L(-104, 122, 4, 30),
          ll=F(-3.0, 0, 2.6, toe=30), head=(4, 0, 0))
    swing(Clip(g, 'hakari_light_3', NOTE, 500, **M), 60, 124, w, h, REST, 500, lag=0, k=0.08)
    # hakari_light_4, the finisher: the boot. He rocks back, the leg comes up bent, and the flat of his foot is shoved
    # through their chest with his whole weight walking in behind it.
    w = K(root=(0.4, -1.2, -2.2), hips=(-14, 10, 0), chest=(10, 6, 0), ra=HC(6.5, -5.0, 2.0), la=HC(-6.5, -4.0, 3.0), rl=L(-96, 110, 8, 0), ll=F(-3.0, 0, 1.6),
          head=(2, 0, 0))
    h = K(root=(0.4, -1.4, 3.6), hips=(-30, 6, 0), chest=(22, 4, 0), ra=HC(7.5, -5.0, -2.0), la=HC(-7.0, -4.0, -1.0), rl=L(-66, 4, 6, -24),
          ll=F(-3.0, 0, 1.6, toe=26), head=(4, 0, 0))
    land = K(root=(0.2, -2.6, 3.4), hips=(10, -8, 0), chest=(10, -6, 0), rl=F(2.8, 0, 6.6), ll=F(-3.0, 0, 1.2, toe=30))
    swing(Clip(g, 'hakari_light_4', NOTE, 700, priority='ATTACK', blendIn=25, blendOut=200), 95, 166, w, h, REST, 700, lag=20, k=0.08, carry=80, rec=land, t_rec=400)

    A_ = dict(priority='ATTACK', blendIn=50, blendOut=150)
    w = K(root=(0, 0.8, 0), hips=(0, 34, -3), chest=(10, 24, -4), ra=HC(7.4, -6.0, -1.0, pole=(1, 0.3, -0.6)), la=HC(-3.4, -4.0, 6.0), **AIR)
    h = K(root=(0, 1.2, 1.2), hips=(6, -32, 3), chest=(10, -42, 6), ra=HC(-1.6, -3.0, 7.8, pole=(1, 0.8, 0)), la=HC(-6.0, -6.0, 0.5), rl=L(10, 50, 8, 30), ll=L(-70, 100, 8, 24))
    c = Clip(g, 'hakari_air_1', NOTE, 450, **A_)
    c.key(0, AIR0).key(50, w, 'EASE_OUT')
    c.hit(112, w, h, lag=22, k=0.14, carry=60, settle=lerp(h, AIR0, 0.6), settle_at=260)
    c.key(450, AIR0, 'EASE_IN_OUT').write()
    w = K(root=(0, 0.8, 0), hips=(0, -30, 3), chest=(0, -28, 4), la=HC(-6.4, 1.5, 0.5, pole=(-1, 0.4, -0.4)), ra=HC(3.0, -5.0, 6.0), **AIR)
    h = K(root=(0, 1.0, 1.2), hips=(12, 30, -3), chest=(18, 38, -6), la=HC(1.6, -6.0, 8.0, pole=(-1, 0.8, 0)), ra=HC(6.2, -6.0, 0.0), rl=L(-70, 100, 8, 24), ll=L(10, 50, 8, 30))
    c = Clip(g, 'hakari_air_2', NOTE, 450, **A_)
    c.key(0, AIR0).key(52, w, 'EASE_OUT')
    c.hit(114, w, h, lag=22, k=0.14, carry=60, settle=lerp(h, AIR0, 0.6), settle_at=260)
    c.key(450, AIR0, 'EASE_IN_OUT').write()
    # hakari_air_3: both boots, a drop-kick thrown flat in the air.
    w = K(root=(0, 1.2, -0.6), hips=(-10, 0, 0), chest=(30, 0, 0), ra=HC(5.0, -3.0, 5.0), la=HC(-5.0, -3.0, 5.0), rl=L(-100, 120, 6, 0), ll=L(-96, 116, 6, 0))
    h = K(root=(0, 1.6, 2.0), hips=(-56, 0, 0), chest=(26, 0, 0), ra=A(30, 0, 40, -20), la=A(30, 0, 40, -20), rl=L(-40, 2, 4, -30), ll=L(-34, 6, 4, -30), head=(10, 0, 0))
    c = Clip(g, 'hakari_air_3', NOTE, 500, **A_)
    c.key(0, AIR0).key(62, w, 'EASE_OUT').key(124, h, 'EASE_IN', marker='impact').key(190, tweak(h, hips=(-4, 0, 0)), 'EASE_OUT')
    c.key(330, lerp(h, AIR0, 0.6), 'EASE_IN_OUT').key(500, AIR0, 'EASE_IN_OUT').write()

    # hakari_uppercut: scooped up from the floor with a jump in it, his free arm flung behind him.
    w = K(root=(0.8, -5.0, -0.4), hips=(20, 30, -4), chest=(18, 20, 0), ra=HC(6.4, -11.0, 2.0, pole=(1, 0, -1)), la=HC(-5.0, -4.0, 4.0), head=(6, 0, 0))
    h = K(root=(-0.4, 2.6, 2.2), hips=(-12, -26, 0), chest=(-12, -24, -4), ra=H(1.2, 30.4, 7.0, pole=(0.4, -0.2, 1)), la=A(40, 0, 34, -30),
          rl=L(14, 20, 6, 34), ll=L(-40, 70, 6, 20), head=(-18, 0, 0))
    c = Clip(g, 'hakari_uppercut', NOTE, 650, priority='ATTACK', blendIn=25, blendOut=200)
    c.key(0, REST).key(95, w, 'EASE_OUT', marker='anticipation').key(166, h, 'EASE_IN', lag=30, marker='impact')
    c.key(300, tweak(h, root_pos=(0, 0.8, 0), rightArm=(8, 0, 0)), 'EASE_OUT')
    c.key(490, K(root=(0, -3.2, 0.8), hips=(14, 0, 0), chest=(10, 0, 0)), 'EASE_IN', marker='land').key(650, REST, 'EASE_OUT').write()
    # hakari_downslam: a double axe-handle, hands locked, swung from behind his head like he is splitting a log.
    w = K(root=(0, 1.6, -0.6), hips=(-20, 0, 0), chest=(-20, 0, 0), ra=HC(1.2, 5.5, -3.0, pole=(1, 0.2, 0.4)), la=HC(-1.2, 5.5, -3.0, pole=(-1, 0.2, 0.4)),
          rl=L(30, 80, 8, 30), ll=L(20, 70, 8, 30), head=(-16, 0, 0))
    h = K(root=(0, 0.2, 1.8), hips=(24, 0, 0), chest=(24, 0, 0), ra=HC(1.2, -11.5, 5.5), la=HC(-1.2, -11.5, 5.5), rl=L(-64, 90, 10, 20), ll=L(-52, 76, 10, 20), head=(8, 0, 0))
    c = Clip(g, 'hakari_downslam', NOTE, 650, priority='ATTACK', blendIn=25, blendOut=200)
    c.key(0, AIR0).key(102, w, 'EASE_OUT', marker='anticipation')
    c.hit(170, w, h, lag=26, k=0.08, carry=70)
    c.key(430, K(root=(0, -3.8, 1.0), hips=(20, 0, 0), chest=(14, 0, 0), ra=HC(3.0, -9.0, 5.0), la=HC(-3.0, -9.0, 5.0)), 'EASE_IN', marker='land')
    c.key(650, REST, 'EASE_IN_OUT').write()
    # hakari_stomp: he jumps on them. Both knees up, both heels down.
    w = K(root=(0, 2.4, 0.6), hips=(-10, 0, 0), chest=(16, 0, 0), ra=A(-70, 0, 50, -40), la=A(-70, 0, 50, -40), rl=L(-80, 110, 8, 0), ll=L(-76, 106, 8, 0), head=(26, 0, 0))
    h = K(root=(0, -3.4, 1.2), hips=(18, 0, 0), chest=(10, 0, 0), ra=A(10, 0, 44, -30), la=A(10, 0, 44, -30), rl=F(2.6, 0, 2.6), ll=F(-2.6, 0, 2.2), head=(20, 0, 0))
    c = Clip(g, 'hakari_stomp', NOTE, 650, priority='ATTACK', blendIn=75, blendOut=150)
    c.key(0, REST).key(60, K(root=(0, -3.0, 0), hips=(14, 6, 0)), 'EASE_OUT').key(115, w, 'EASE_OUT', marker='anticipation')
    c.key(166, h, 'EASE_IN', marker='impact').key(240, tweak(h, root_pos=(0, 0.9, 0)), 'EASE_OUT').key(650, REST, 'EASE_IN_OUT').write()
    # hakari_sprint: he doesn't slow down, he just runs through them shoulder first.
    run = K(root=(0, -1.6, 0), hips=(28, 8, 0), chest=(12, -14, 0), ra=A(40, 0, 10, -80), la=A(-50, 0, 8, -84), rl=L(-52, 40, 3, 10), ll=L(40, 70, 3, 20))
    w = K(root=(0.6, -3.0, 0.6), hips=(26, 30, 0), chest=(14, 26, 6), ra=HC(5.0, -6.0, 1.0), la=HC(-3.0, -5.0, 5.0), rl=L(-40, 60, 4, 10), ll=L(20, 60, 4, 20))
    h = K(root=(-0.8, -2.6, 5.6), hips=(34, -40, -6), chest=(16, -44, -10), ra=HC(1.0, -6.5, 3.0, pole=(1, 0.5, 0.5)), la=A(40, 0, 30, -40),
          rl=F(3.2, 1.0, 0.0, toe=60), ll=F(-3.0, 0, 6.0), head=(-12, 20, 0))
    c = Clip(g, 'hakari_sprint', NOTE, 800, priority='MOVEMENT', blendIn=50, blendOut=200)
    c.key(0, run).key(100, w, 'EASE_OUT', marker='anticipation')
    c.hit(170, w, h, lag=20, k=0.10, carry=90)
    c.key(450, K(root=(-0.4, -2.8, 4.4), hips=(16, -16, 0), chest=(10, -14, 0), rl=F(3.2, 0, 1.4, toe=20), ll=F(-3.0, 0, 6.0)), 'EASE_OUT', marker='recovery')
    c.key(800, REST, 'EASE_IN_OUT').write()
    # hakari_heavy_charge: a pitcher's wind-up. Turned right away from them, fist behind his own ear, front shoulder
    # pointing where it's going, bouncing on it.
    hc = K(root=(1.6, -3.4, -2.6), hips=(8, 56, -4), chest=(4, 34, -6), ra=HC(7.0, 0.5, -3.5, pole=(1, 0.3, -0.6)), la=HC(-4.0, -3.0, 7.0),
           rl=F(3.6, 0, -4.4, yaw=40), ll=F(-3.0, 0, 3.6, toe=10), head=(0, 0, 0))
    c = Clip(g, 'hakari_heavy_charge', NOTE, 350, hold=True, priority='ATTACK', blendIn=75, blendOut=150)
    c.key(0, REST).key(220, lerp(REST, hc, 0.85), 'EASE_OUT').breathe(350, hc, period=900, amp=1.4)
    c.write()
    # hakari_heavy: the haymaker. It comes all the way round and he nearly goes over with it.
    deep = tweak(hc, hips=(2, 6, 0), chest=(2, 6, 0), root_pos=(0.3, -0.6, -0.4))
    h = K(root=(-1.6, -3.0, 5.6), hips=(20, -50, 6), chest=(16, -50, 10), ra=HC(-3.6, -3.4, 7.0, pole=(1, 0.8, 0)), la=A(50, 0, 40, -30),
          rl=F(3.4, 1.4, -1.6, toe=66), ll=F(-3.0, 0, 5.0, yaw=-20), head=(0, 10, 0))
    c = Clip(g, 'hakari_heavy', NOTE, 800, priority='ATTACK', blendIn=25, blendOut=250)
    c.key(0, hc).key(120, deep, 'EASE_OUT', marker='anticipation')
    c.hit(205, deep, h, lag=44, k=0.14, carry=95)
    c.key(500, K(root=(-1.2, -3.0, 4.6), hips=(16, -30, 4), chest=(12, -30, 4), ra=HC(-1.0, -7.0, 5.0), rl=F(3.4, 0, 1.0, toe=20), ll=F(-3.0, 0, 5.0)), 'EASE_OUT', marker='recovery')
    c.key(800, REST, 'EASE_IN_OUT').write()


def kit():
    g = 'hakari'
    # door_guard: braced behind the doors, forearms crossed, shoulder into it.
    gd = K(root=(0, -2.6, -0.6), hips=(12, 20, 0), chest=(12, 10, 0), ra=HC(-0.6, -3.0, 6.4, pole=(1, 0.8, 0)), la=HC(1.0, -4.0, 5.4, pole=(-1, 0.8, 0)), head=(8, 0, 0))
    c = Clip(g, 'door_guard', NOTE)
    c.key(0, lerp(REST, gd, 0.5)).key(130, gd, 'OVERSHOOT').breathe(200, gd, period=1500, amp=0.6)
    c.write()

    # reserve_balls: the ball rolled up onto his thumb at hip height, eyes on the target, and flicked like a coin.
    aim = K(root=(0, -1.6, -0.4), hips=(6, 24, 0), chest=(6, 18, 0), ra=HC(5.4, -6.0, 5.0, wrist=-30), la=LO_L, head=(0, 0, 0))
    cock = tweak(aim, rightHand=(-30, 0, 0), rightForearm=(-10, 0, 0), chest=(2, 4, 0))
    flick = K(root=(0, -1.6, 0.8), hips=(8, -10, 0), chest=(8, -16, 0), ra=H(2.4, 16.4, 13.6, wrist=40), la=LO_L, head=(0, 0, 0))
    c = Clip(g, 'reserve_balls', NOTE)
    c.key(0, REST).key(200, aim, 'EASE_OUT', marker='aim').key(300, cock, 'EASE_OUT')
    c.key(350, flick, 'EASE_IN', lag=12, marker='flick').key(550, tweak(flick, rightArm=(-8, 0, 0), rightHand=(-20, 0, 0)), 'EASE_OUT')
    c.key(800, REST, 'EASE_IN_OUT').write()

    # shutter_sign (ShutterDoors.gif): a short drop, then both arms slammed out wide, palms forward, like throwing two
    # doors shut; held open-handed, then let fall.
    dn = K(root=(0, -3.0, 0), hips=(12, 0, 0), chest=(12, 0, 0), ra=HC(2.6, -4.0, 5.0), la=HC(-2.6, -4.0, 5.0), rl=F(3.6, 0, -1.0, yaw=20), ll=F(-3.6, 0, 1.0, yaw=20))
    wide = K(root=(0, -1.6, 0.4), hips=(-2, 0, 0), chest=(-6, 0, 0), ra=A(-78, 30, 70, -10, -50), la=A(-78, 30, 70, -10, -50), rl=F(3.8, 0, -1.0, yaw=20), ll=F(-3.8, 0, 1.0, yaw=20),
             head=(-4, 0, 0))
    c = Clip(g, 'shutter_sign', NOTE, 680)
    c.key(0, REST).key(60, dn, 'EASE_OUT', marker='anticipation').key(160, wide, 'OVERSHOOT', lag=20, marker='doors')
    c.key(400, tweak(wide, rightArm=(4, 0, -4), leftArm=(4, 0, 4)), 'EASE_IN_OUT', marker='hold').key(640, lerp(wide, REST, 0.8), 'EASE_IN_OUT', marker='recovery').write()

    # rough_charge: energy poured into one fist. Sunk low and sideways-on, the fist drawn back at his waist and shaking,
    # the other hand a claw in front of him.
    rc = K(root=(1.4, -4.6, -2.0), hips=(14, 48, 0), chest=(12, 30, 4), ra=HC(7.0, -8.5, -2.5, pole=(1, 0.2, -1)), la=H(-2.0, 15.0, 10.5),
           rl=F(3.6, 0, -4.6, yaw=36), ll=F(-3.0, 0, 4.2), head=(6, 0, 0))
    c = Clip(g, 'rough_charge', NOTE, tremble=1.4)
    c.key(0, REST).key(280, lerp(REST, rc, 0.9), 'EASE_OUT', lag=60).breathe(400, rc, period=800, amp=1.5)
    c.write()
    # rough_strike: let go. The straight goes right through them and he follows it two steps.
    h = K(root=(-1.4, -3.4, 7.0), hips=(26, -44, 0), chest=(16, -40, -6), ra=H(-1.6, 15.6, 21.4), la=A(40, 0, 30, -50),
          rl=F(3.4, 1.0, -2.6, toe=66), ll=F(-3.0, 0, 6.0), head=(0, 0, 0))
    c = Clip(g, 'rough_strike', NOTE)
    c.key(0, rc)
    c.hit(100, rc, h, lag=40, k=0.10, carry=110)
    c.key(400, K(root=(-1.0, -3.0, 5.6), hips=(18, -24, 0), chest=(12, -20, 0), ra=H(-0.8, 13.0, 15.0), rl=F(3.4, 0, 1.0, toe=24), ll=F(-3.0, 0, 6.0)), 'EASE_OUT', marker='recovery')
    c.key(700, REST, 'EASE_IN_OUT').write()

    # Fever Breaker (FeverBreaker.gif). fever_kick: leaning back as he comes in, knee chambered, then a straight boot
    # that stays out, holding them up on the end of it.
    w = K(root=(0.4, -1.0, -1.6), hips=(-18, 8, 0), chest=(12, 4, 0), ra=HC(6.5, -5.0, 2.0), la=HC(-6.5, -4.0, 3.0), rl=L(-90, 112, 6, 0), ll=F(-3.0, 0, 1.6))
    h = K(root=(0.4, -1.0, 3.0), hips=(-36, 4, 0), chest=(26, 2, 0), ra=HC(7.5, -5.0, -2.0), la=HC(-7.0, -4.0, -1.0), rl=L(-64, 2, 4, -26), ll=F(-3.0, 0, 1.6, toe=24),
          head=(4, 0, 0))
    c = Clip(g, 'fever_kick', NOTE)
    c.key(0, REST).key(130, w, 'EASE_OUT', marker='chamber').key(250, h, 'EASE_IN', lag=20, marker='impact')
    c.key(380, tweak(h, rightThigh=(-5, 0, 0), chest=(-3, 0, 0)), 'EASE_OUT', marker='hold').key(700, tweak(h, rightThigh=(-2, 0, 0)), 'EASE_IN_OUT')
    c.key(850, lerp(h, REST, 0.8), 'EASE_IN', marker='recovery').key(900, REST).write()
    # fever_finish: cut onto both feet going through them; he falls out of it onto his side and rolls up to a crouch.
    dk = K(root=(0, 4.0, 3.0), hips=(-70, 0, 0), chest=(24, 0, 0), ra=A(40, 0, 44, -20), la=A(40, 0, 44, -20), rl=L(-30, 0, 4, -30), ll=L(-24, 4, 4, -30), head=(20, 0, 0))
    fall = K(root=(0, -1.0, 2.6), hips=(-40, 0, 20), chest=(30, 0, 0), ra=A(-30, 0, 50, -40), la=A(10, 0, 40, -60), rl=L(-60, 70, 6, 0), ll=L(-40, 60, 6, 0), head=(20, 0, 0))
    crouch = K(root=(0, -5.6, 1.6), hips=(26, 10, 0), chest=(14, 6, 0), ra=H(5.0, 1.0, 5.0), la=HC(-4.0, -7.0, 5.0), rl=F(3.2, 0, -2.4, toe=40), ll=F(-3.0, 0, 3.4), head=(-6, 0, 0))
    c = Clip(g, 'fever_finish', NOTE)
    c.key(0, dk, marker='impact').key(140, tweak(dk, rightThigh=(-6, 0, 0), leftThigh=(-6, 0, 0), root_pos=(0, 0.4, 0.8)), 'EASE_OUT', marker='follow-through')
    c.key(260, fall, 'EASE_IN').key(360, crouch, 'EASE_IN', marker='land').key(750, lerp(crouch, REST, 0.85), 'EASE_IN_OUT', marker='recovery').key(800, REST).write()
    # fever_crush: the doors are called shut on them: one hand thrown out and clenched.
    cl = K(root=(0, -1.8, 0.6), hips=(6, -14, 0), chest=(6, -18, 0), ra=H(1.6, 18.4, 13.4, wrist=-30), head=(0, 0, 0))
    c = Clip(g, 'fever_crush', NOTE)
    c.key(0, REST).key(150, cl, 'OVERSHOOT', lag=20, marker='clamp').key(300, tweak(cl, rightHand=(50, 0, 0), rightForearm=(-16, 0, 0)), 'EASE_OUT').write()
    # fever_raise: the heel goes up over his head, slowly, hands out for balance, looking at where it will land.
    mid = K(root=(-0.6, -0.8, -0.4), hips=(-12, 0, 4), chest=(10, 0, 0), ra=A(-30, 0, 50, -30), la=A(-30, 0, 50, -30), rl=L(-100, 60, 6, -10), ll=F(-2.8, 0, 0.6), head=(10, 0, 0))
    top = K(root=(-0.8, 0.2, -0.6), hips=(-22, 0, 6), chest=(16, 0, 0), ra=A(-20, 0, 60, -30), la=A(-20, 0, 60, -30), rl=L(-156, 4, 6, -30), ll=F(-2.8, 0, 0.6, toe=16), head=(14, 0, 0))
    c = Clip(g, 'fever_raise', NOTE, tremble=0.5)
    c.key(0, REST).key(300, mid, 'EASE_IN_OUT').key(600, top, 'OVERSHOOT', marker='raised').write()
    # fever_axe: and down. The heel is dropped through them and he lands folded over that knee.
    h = K(root=(0.4, -2.8, 1.4), hips=(16, 0, -2), chest=(14, 0, 0), ra=A(10, 0, 40, -30), la=A(-10, 0, 44, -40), rl=F(2.6, 0, 4.4), ll=F(-2.8, 0, 0.2, toe=24), head=(14, 0, 0))
    c = Clip(g, 'fever_axe', NOTE)
    c.key(0, top).key(100, h, 'EASE_IN', marker='impact').key(170, tweak(h, root_pos=(0, -0.9, 0), chest=(4, 0, 0)), 'EASE_OUT')
    c.key(300, h, 'EASE_OUT').key(600, REST, 'EASE_IN_OUT').write()
    # fever_rush: running in on them, low, arms pumping.
    c = Clip(g, 'fever_rush', NOTE)
    r1 = K(root=(0, -2.0, 0), hips=(30, 10, 0), chest=(10, -16, 0), ra=A(46, 0, 10, -84), la=A(-56, 0, 8, -88), rl=L(-60, 40, 3, 10), ll=L(40, 76, 3, 20), head=(-12, 0, 0))
    r2 = K(root=(0, -2.0, 0), hips=(30, -10, 0), chest=(10, 16, 0), la=A(46, 0, 10, -84), ra=A(-56, 0, 8, -88), ll=L(-60, 40, 3, 10), rl=L(40, 76, 3, 20), head=(-12, 0, 0))
    c.key(0, r1).key(150, tweak(lerp(r1, r2, 0.5), root_pos=(0, 0.8, 0)), 'LINEAR').key(300, r2, 'LINEAR').key(450, tweak(lerp(r1, r2, 0.5), root_pos=(0, 0.8, 0)), 'LINEAR').key(600, r1, 'LINEAR')
    c.d.update(loop=True, loopStart=0, loopEnd=600)
    c.duration = 600
    c.write()

    # idg_sign: Idle Death Gamble. Two fingers raised beside a grin, the other hand flicking open at his side.
    sg = K(root=(0, -0.8, 0), hips=(-4, 8, 0), chest=(-6, 6, 0), ra=HC(4.0, 0.5, 4.0, pole=(1, 0.5, 0)), la=A(-10, 0, 40, -30, -30), head=(-10, 0, -6))
    c = Clip(g, 'idg_sign', NOTE)
    c.key(0, REST).key(260, sg, 'OVERSHOOT', marker='sign').breathe(400, sg, period=1600, amp=0.6)
    c.write()
    # hakari_domain_release: the domain opens. He throws both arms out and leans back into it, laughing at the ceiling.
    ex = K(root=(0, -0.4, -0.8), hips=(-12, 0, 0), chest=(-18, 0, 0), ra=A(-50, 0, 80, -20, -30), la=A(-50, 0, 80, -20, -30), rl=F(3.6, 0, -1.2, yaw=24), ll=F(-3.6, 0, 1.2, yaw=24),
           head=(-30, 0, 0))
    c = Clip(g, 'hakari_domain_release', NOTE, 1200, priority='AWAKENING', blendIn=120, blendOut=300)
    c.key(0, sg).key(120, ex, 'EASE_IN', lag=20, marker='expand').key(420, tweak(ex, chest=(-4, 0, 0), rightArm=(-6, 0, 0), leftArm=(-6, 0, 0)), 'EASE_OUT')
    c.key(1050, K(ra=A(-10, 0, 30, -20), la=A(-10, 0, 30, -20)), 'EASE_IN_OUT', marker='settle').key(1200, REST, 'EASE_IN_OUT').write()

    # jackpot (Jackpot.gif): he curls in on himself as it hits, then snaps straight with his chest up and both fists
    # clenched low and wide, standing in the burst.
    tense = K(root=(0, -3.6, 0), hips=(16, 0, 0), chest=(24, 0, 0), ra=HC(2.4, -6.0, 4.4), la=HC(-2.4, -6.0, 4.4), rl=F(3.0, 0, -0.6), ll=F(-3.0, 0, 0.6), head=(22, 0, 0))
    burst = K(root=(0, 0.2, 0), hips=(-8, 0, 0), chest=(-14, 0, 0), ra=A(14, 0, 36, -40, 20), la=A(14, 0, 36, -40, 20), rl=F(3.6, 0, -0.6, yaw=20), ll=F(-3.6, 0, 0.6, yaw=20),
              head=(-14, 0, 0))
    c = Clip(g, 'jackpot', NOTE, tremble=0.8)
    c.key(0, REST).key(250, tense, 'EASE_OUT', marker='anticipation').key(400, burst, 'OVERSHOOT', lag=30, marker='eruption')
    c.key(750, tweak(burst, chest=(3, 0, 0), rightForearm=(-8, 0, 0), leftForearm=(-8, 0, 0)), 'EASE_IN_OUT')
    c.key(1100, burst, 'EASE_IN_OUT', marker='hold').key(1450, REST, 'EASE_IN_OUT').key(1500, REST).write()

    # rhythm_dance (Rhythm.gif): knees wide and bent, elbows high, fists by his head; one side pumps up as the hips
    # sway the other way, once a beat (500 ms), looping from 700.
    def beat(s):
        up, dn = HC(5.6 * 1, 3.5, 1.0, pole=(1, 0.6, 0)), HC(-5.4, -0.5, 2.0, pole=(-1, 0.6, 0))
        p = K(root=(-1.6 * s, -3.2, 0), hips=(4, 0, -8 * s), chest=(2, 0, 12 * s), ra=up if s > 0 else HC(5.4, -0.5, 2.0, pole=(1, 0.6, 0)),
              la=dn if s > 0 else HC(-5.6, 3.5, 1.0, pole=(-1, 0.6, 0)), rl=F(4.2, 0, 0, yaw=30), ll=F(-4.2, 0, 0, yaw=30), head=(-4, 0, 6 * s))
        return p
    mid = K(root=(0, -4.2, 0), hips=(6, 0, 0), chest=(4, 0, 0), ra=HC(5.4, 1.0, 1.6, pole=(1, 0.6, 0)), la=HC(-5.4, 1.0, 1.6, pole=(-1, 0.6, 0)), rl=F(4.2, 0, 0, yaw=30),
            ll=F(-4.2, 0, 0, yaw=30))
    c = Clip(g, 'rhythm_dance', NOTE)
    c.key(0, REST).key(350, mid, 'EASE_OUT', marker='lead-in').key(700, beat(1), 'EASE_OUT', marker='beat: right up').key(950, mid, 'EASE_IN')
    c.key(1200, beat(-1), 'EASE_OUT', marker='beat: left up').key(1450, mid, 'EASE_IN').key(1700, beat(1), 'EASE_OUT', marker='beat: right up')
    c.duration = None
    c.write()


def jackpot_kit():
    g = 'hakari'
    # Lucky Volley (LuckyVolley.gif). volley_open: a hop in and a snapped right to start it.
    w = K(root=(0.6, -2.2, -0.6), hips=(8, 28, 0), chest=(8, 20, 0), ra=HC(5.6, -4.0, 1.5), la=HC(-3.4, -3.0, 6.0))
    h = K(root=(-0.4, -2.0, 3.0), hips=(10, -24, 0), chest=(10, -30, 0), ra=H(-0.4, 18.4, 16.6), la=HC(-4.0, -3.4, 3.4), rl=F(3.4, 0, -1.0, toe=40), ll=F(-3.2, 0, 3.6))
    c = Clip(g, 'volley_open', NOTE)
    c.key(0, REST).key(120, w, 'EASE_OUT', marker='anticipation')
    c.hit(200, w, h, lag=24, k=0.10, carry=50)
    c.key(400, K(ra=HC(3.6, -3.4, 4.6), la=HC(-3.6, -3.4, 4.6)), 'EASE_OUT').write()
    # volley_flurry: both fists going like he is beating a door down, a blow every 100 ms, the whole body rocking.
    base = K(root=(0, -2.4, 1.0), hips=(12, 0, 0), chest=(10, 0, 0), ra=HC(3.6, -3.4, 4.6), la=HC(-3.6, -3.4, 4.6), head=(2, 0, 0))
    r = K(root=(-0.5, -2.6, 2.0), hips=(12, -20, 2), chest=(12, -30, 4), ra=H(-0.4, 17.4, 16.0), la=HC(-4.4, -4.0, 2.4), head=(2, 0, 0))
    l = K(root=(0.5, -2.6, 2.0), hips=(12, 20, -2), chest=(12, 30, -4), la=H(0.4, 17.4, 16.0), ra=HC(4.4, -4.0, 2.4), head=(2, 0, 0))
    c = Clip(g, 'volley_flurry', NOTE)
    c.key(0, base).key(100, r, 'EASE_IN', lag=12, marker='right').key(150, lerp(r, base, 0.4), 'EASE_OUT')
    c.key(200, l, 'EASE_IN', lag=12, marker='left').key(250, lerp(l, base, 0.4), 'EASE_OUT').key(300, r, 'EASE_IN', lag=12, marker='right')
    c.duration = None
    c.write()
    # volley_final: cut onto the last swing landing, turned right through it; it spins him half round before he
    # catches it.
    h = K(root=(-1.4, -2.6, 4.0), hips=(14, -50, 6), chest=(14, -54, 10), ra=HC(-4.0, -3.0, 6.4, pole=(1, 0.8, 0)), la=A(50, 0, 40, -30),
          rl=F(3.4, 1.0, -0.6, toe=60), ll=F(-3.0, 0, 4.4, yaw=-24), head=(0, 14, 0))
    c = Clip(g, 'volley_final', NOTE)
    c.key(0, h, marker='impact').key(120, tweak(h, hips=(0, -10, 0), chest=(2, -8, 0), rightArm=(6, -10, 0)), 'EASE_OUT', marker='follow-through')
    c.key(380, K(root=(-0.8, -2.6, 2.6), hips=(12, -24, 2), chest=(10, -20, 2), ra=HC(1.0, -8.0, 4.0), rl=F(3.4, 0, 0.6, toe=20), ll=F(-3.0, 0, 4.0)), 'EASE_IN_OUT')
    c.key(650, REST, 'EASE_IN_OUT', marker='recovery').key(700, REST).write()

    # Lucky Rushdown. rushdown_run: head down, flat out, one hand already reaching low for an ankle.
    rr = K(root=(0, -2.8, 0.6), hips=(38, 8, 0), chest=(10, -10, 0), ra=H(3.0, 6.0, 11.0), la=A(50, 0, 14, -70), rl=L(-64, 40, 3, 10), ll=L(40, 80, 3, 20), head=(-24, 0, 0))
    rr2 = K(root=(0, -2.8, 0.6), hips=(38, -6, 0), chest=(10, -4, 0), ra=H(3.0, 6.6, 11.6), la=A(-30, 0, 14, -80), rl=L(40, 80, 3, 20), ll=L(-64, 40, 3, 10), head=(-24, 0, 0))
    c = Clip(g, 'rushdown_run', NOTE)
    c.key(0, rr).key(140, tweak(lerp(rr, rr2, 0.5), root_pos=(0, 0.8, 0)), 'LINEAR').key(280, rr2, 'LINEAR').key(420, tweak(lerp(rr, rr2, 0.5), root_pos=(0, 0.8, 0)), 'LINEAR').key(560, rr, 'LINEAR')
    c.d.update(loop=True, loopStart=0, loopEnd=560)
    c.duration = 560
    c.write()
    # rushdown_drag: he has the leg. Still running, the arm trailing straight back behind him with their weight on it.
    d1 = K(root=(0, -2.2, 0.4), hips=(30, -16, 0), chest=(8, -26, 0), ra=H(4.4, 5.0, -8.0, pole=(1, 0.4, 0)), la=A(-50, 0, 10, -84), rl=L(-56, 40, 3, 10), ll=L(40, 76, 3, 20),
           head=(-14, 10, 0))
    d2 = K(root=(0, -2.2, 0.4), hips=(30, -26, 0), chest=(8, -30, 0), ra=H(4.4, 5.6, -8.4, pole=(1, 0.4, 0)), la=A(40, 0, 10, -80), rl=L(40, 76, 3, 20), ll=L(-56, 40, 3, 10),
           head=(-14, 10, 0))
    c = Clip(g, 'rushdown_drag', NOTE, tremble=0.6)
    c.key(0, d1).key(150, tweak(lerp(d1, d2, 0.5), root_pos=(0, 0.7, 0)), 'LINEAR').key(300, d2, 'LINEAR').key(450, tweak(lerp(d1, d2, 0.5), root_pos=(0, 0.7, 0)), 'LINEAR').key(600, d1, 'LINEAR')
    c.d.update(loop=True, loopStart=0, loopEnd=600)
    c.duration = 600
    c.write()
    # rushdown_throw: he plants, and slings them over his shoulder like a hammer throw; the release pulls him up onto
    # his toes.
    w = K(root=(0.8, -3.4, -0.6), hips=(14, 44, 0), chest=(10, 36, 6), ra=H(6.4, 4.0, -7.0, pole=(1, 0.4, 0)), la=HC(-4.0, -3.0, 6.0), rl=F(3.6, 0, -3.0, yaw=30),
          ll=F(-3.0, 0, 3.0), head=(4, 0, 0))
    h = K(root=(-0.6, 0.6, 2.0), hips=(-6, -40, 0), chest=(-8, -40, -6), ra=A(-150, -20, 10, -10, -30), la=A(40, 0, 30, -40), rl=F(3.4, 0, -2.0, toe=50), ll=F(-3.0, 0, 3.0, toe=30),
          head=(-14, 0, 0))
    c = Clip(g, 'rushdown_throw', NOTE)
    c.key(0, w)
    c.hit(150, w, h, lag=40, k=0.14, carry=90, marker='release')
    c.key(450, K(root=(-0.4, -2.2, 1.6), hips=(10, -20, 0), chest=(10, -20, 0), ra=HC(1.0, -8.0, 5.0)), 'EASE_IN_OUT', marker='recovery').key(700, REST, 'EASE_IN_OUT').write()

    # Overwhelming Luck. overwhelm_ready: weight dropped onto the front foot, shoulders rolling, about to go.
    c = Clip(g, 'overwhelm_ready', NOTE)
    rd = K(root=(0, -3.4, 1.4), hips=(22, 20, 0), chest=(12, 14, 0), ra=HC(5.6, -5.0, 1.0), la=HC(-3.6, -4.0, 6.0), rl=F(3.4, 0, -3.4, toe=30), head=(-8, 0, 0))
    c.key(0, REST).key(250, rd, 'OVERSHOOT').write()
    # overwhelm_right / overwhelm_left: wide clubbing hooks on the run, each one throwing his shoulders right round.
    wr = K(root=(0.6, -2.6, 1.0), hips=(14, 30, 0), chest=(10, 26, -4), ra=HC(7.4, -5.0, -1.0, pole=(1, 0.3, -0.6)), la=HC(-3.4, -4.0, 5.0))
    hr = K(root=(-0.6, -2.4, 3.0), hips=(14, -34, 2), chest=(12, -44, 6), ra=HC(-1.8, -3.0, 7.6, pole=(1, 0.8, 0)), la=HC(-6.4, -6.0, 0.0), rl=F(3.4, 0, -1.6, toe=44, yaw=-20))
    for name, w, h in (('overwhelm_right', wr, hr), ('overwhelm_left', mirror(wr), mirror(hr))):
        c = Clip(g, name, NOTE)
        c.key(0, w).key(100, h, 'EASE_IN', lag=26, marker='impact').key(150, over(h, w, 0.12), 'EASE_OUT').key(200, lerp(h, rd, 0.4), 'EASE_IN_OUT').write()
    # overwhelm_final: he jumps into the last one, a right hand brought down from as high as he can reach.
    w = K(root=(0.6, 1.6, 0.6), hips=(-6, 34, 0), chest=(-10, 26, -6), ra=HC(6.0, 4.0, -3.0, pole=(1, 0.3, -0.4)), la=HC(-3.0, -1.0, 7.0), rl=L(30, 90, 6, 30), ll=L(-40, 70, 6, 20),
          head=(4, 0, 0))
    h = K(root=(-0.8, -3.6, 4.6), hips=(28, -36, 0), chest=(22, -36, -6), ra=H(-0.8, 10.0, 17.0), la=A(50, 0, 30, -40), rl=F(3.4, 0, 0.4, toe=44), ll=F(-3.0, 0, 5.4), head=(8, 0, 0))
    c = Clip(g, 'overwhelm_final', NOTE)
    c.key(0, w)
    c.hit(150, w, h, lag=30, k=0.10, carry=100)
    c.key(500, tweak(h, root_pos=(0, 0.8, -0.6), chest=(-6, 10, 0)), 'EASE_OUT', marker='recovery').key(800, REST, 'EASE_IN_OUT').write()

    # Energy Surge. surge_dash: the dash punch held out in front of him like a battering ram, body flat behind it.
    sd = K(root=(0, -1.6, 3.0), hips=(44, -30, 0), chest=(12, -30, -6), ra=H(-0.6, 15.5, 19.5), la=A(50, 0, 24, -40), rl=L(40, 30, 4, 40), ll=L(-50, 90, 4, 20), head=(-30, 0, 0))
    c = Clip(g, 'surge_dash', NOTE)
    c.key(0, lerp(REST, sd, 0.5)).key(160, sd, 'OVERSHOOT', lag=30).breathe(400, sd, period=700, amp=0.6)
    c.write()
    # energy_leap: gone upward after them, knees tucked, one arm trailing.
    lp = K(root=(0, 2.6, 0), hips=(-10, 0, 0), chest=(-6, 0, 0), ra=A(-160, 0, 16, -30), la=A(20, 0, 24, -30), rl=L(-70, 110, 6, 30), ll=L(-20, 60, 6, 40), head=(-24, 0, 0))
    c = Clip(g, 'energy_leap', NOTE)
    c.key(0, K(root=(0, -4.0, 0), hips=(20, 0, 0), chest=(10, 0, 0))).key(150, lp, 'OVERSHOOT', marker='leap').key(350, tweak(lp, rightThigh=(20, 0, 0), rightShin=(-40, 0, 0)), 'EASE_OUT').write()
    # surge_kick: above them. He turns over in the air and brings the heel round and down on top of them.
    w = K(root=(0, 1.6, 0), hips=(-16, 40, 0), chest=(10, 20, 0), ra=A(-40, 0, 50, -40), la=A(-60, 0, 30, -60), rl=L(-150, 30, 10, -20), ll=L(-10, 70, 6, 30), head=(16, 0, 0))
    h = K(root=(0, 0.2, 1.4), hips=(20, -30, 0), chest=(10, -10, 0), ra=A(30, 0, 44, -20), la=A(-20, 0, 50, -30), rl=L(-58, 2, 6, 10), ll=L(-40, 100, 6, 30), head=(22, 0, 0))
    c = Clip(g, 'surge_kick', NOTE)
    c.key(0, lp).key(200, w, 'EASE_OUT', marker='anticipation')
    c.hit(350, w, h, lag=20, k=0.12, carry=80)
    c.key(700, lerp(h, AIR0, 0.8), 'EASE_IN_OUT').write()


if __name__ == '__main__':
    melee()
    kit()
    jackpot_kit()
    print('wrote', len(WRITTEN), 'clips:', ' '.join(WRITTEN))
