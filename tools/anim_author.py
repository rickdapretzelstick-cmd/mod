#!/usr/bin/env python3
"""
Authors the shared melee clips (assets/jjk/animations/common: the default M1 set, dashes, the guard) and the hit
reactions (animations/reactions) with tools/animkit.py.

    python3 tools/anim_author.py

The default M1 set is a boxer-brawler's (Yuji fights with it; a cursed tool without cuts of its own falls back to it).
Gojo, Hakari and Ryu have sets of their own (tools/anim_gojo.py, anim_hakari.py, anim_ryu.py).

Timing is the server's (core/combat/melee/MeleeSystem): a light's hitbox opens 100 ms in, the finisher / uppercut /
downslam / sprint / stomp 150 ms in, the heavy 200 ms in. Each clip's contact pose lands just inside that window.
"""
from animkit import *

NOTE = 'tools/anim_author.py'

# --- The boxer's guard: left foot and left hand lead, chin tucked, weight on the balls of the feet ---
FL, FR = F(-2.4, 0, 3.2), F(2.6, 0, -3.2, toe=18)
GR, GL = HC(3.6, -3.2, 4.6), HC(-3.0, -2.2, 6.8)


def Y(root=(0, -1.2, 0), hips=(4, 14, 0), chest=(5, 10, 0), ra=GR, la=GL, rl=FR, ll=FL, head=(4, 0, 0), **kw):
    return P(root=root, hips=hips, chest=chest, ra=ra, la=la, rl=rl, ll=ll, head=head, **kw)


STANCE = Y()
AIR_L = dict(rl=L(-34, 74, 5, 20), ll=L(-58, 96, 4, 24))  # knees tucked under a jump


def common():
    g = 'common'
    # light_1: the jab. A flick off the lead shoulder: barely any wind-up, the hip snaps a few degrees, the fist is back
    # at the chin before the cross leaves.
    w = Y(root=(0.2, -1.5, -0.5), hips=(4, 20, 0), chest=(6, 14, 0), la=HC(-3.4, -3.0, 5.0))
    h = Y(root=(-0.3, -1.5, 2.2), hips=(6, 2, 0), chest=(7, 30, 0), la=H(-1.0, 19.4, 15.6), ra=HC(3.4, -2.6, 4.2), rl=F(2.6, 0, -3.2, toe=30))
    c = Clip(g, 'light_1', NOTE)
    c.key(0, STANCE).key(50, w, 'EASE_OUT')
    c.hit(110, w, h, lag=16, k=0.08, carry=45, settle=lerp(h, STANCE, 0.55), settle_at=230)
    c.key(450, STANCE, 'EASE_IN_OUT').write()

    # light_2: the cross. The rear heel turns out, the hips and shoulders unwind through the centre line and the right
    # hand goes straight down the pipe; the jab hand snaps back to the cheek.
    w = Y(root=(0.5, -1.8, -0.8), hips=(4, 30, 0), chest=(7, 20, 0), ra=HC(4.6, -4.2, 2.4), la=HC(-2.6, -2.0, 7.4))
    h = Y(root=(-0.8, -1.9, 3.4), hips=(10, -26, 0), chest=(9, -30, -3), ra=H(-0.4, 19.0, 17.2), la=HC(-3.6, -2.2, 3.6),
          rl=F(2.6, 0, -3.0, toe=44, yaw=-30))
    c = Clip(g, 'light_2', NOTE)
    c.key(0, STANCE).key(55, w, 'EASE_OUT')
    c.hit(115, w, h, lag=28, k=0.10, carry=55, settle=lerp(h, STANCE, 0.5), settle_at=250)
    c.key(450, STANCE, 'EASE_IN_OUT').write()

    # light_3: the lead hook. He dips left to load the front leg, then the whole torso turns as one piece with the elbow
    # up and the forearm parallel to the floor; the front foot pivots, the head stays behind the shoulder.
    w = Y(root=(-1.4, -2.6, 0.4), hips=(8, -24, 4), chest=(10, -22, 6), la=HC(-6.4, -4.4, 1.6, pole=(-1, 0.2, -0.4)), ra=HC(3.2, -2.4, 4.6),
          head=(8, -12, 0))
    h = Y(root=(0.8, -1.7, 1.6), hips=(6, 34, -2), chest=(6, 44, -4), la=HC(0.6, -2.6, 7.4, pole=(-1, 0.5, 0.2)), ra=HC(3.8, -2.6, 3.2),
          ll=F(-2.4, 0, 3.2, toe=22, yaw=-38), head=(4, 10, 0))
    c = Clip(g, 'light_3', NOTE)
    c.key(0, STANCE).key(60, w, 'EASE_OUT')
    c.hit(125, w, h, lag=30, k=0.14, carry=60, settle=lerp(h, STANCE, 0.45), settle_at=280)
    c.key(500, STANCE, 'EASE_IN_OUT').write()

    # light_4, the finisher: an overhand right thrown with a full step. He sinks and coils on the back leg, then the back
    # foot leaves the floor as everything he has goes through the target; he lands deep on the front leg and has to
    # catch himself.
    w = Y(root=(1.0, -3.4, -2.0), hips=(12, 40, 0), chest=(12, 26, 4), ra=HC(6.0, -1.0, -1.5, pole=(1, 0.2, -1)), la=HC(-2.0, -1.0, 8.6),
          head=(8, 0, 0))
    h = Y(root=(-1.0, -3.0, 6.2), hips=(22, -34, 0), chest=(16, -34, -6), ra=H(-1.6, 16.6, 19.6), la=HC(-5.6, -5.0, -1.0, pole=(-1, 0, -1)),
          rl=F(3.2, 2.2, -1.4, toe=62), ll=F(-2.4, 0, 6.4), head=(2, 0, 0))
    land = Y(root=(-0.8, -3.4, 5.4), hips=(20, -24, 0), chest=(14, -22, -3), ra=H(-1.0, 14.0, 15.0), la=HC(-5.0, -4.6, 0.6),
             rl=F(3.0, 0, 1.2, toe=30), ll=F(-2.4, 0, 6.4))
    c = Clip(g, 'light_4', NOTE)
    c.key(0, STANCE).key(95, w, 'EASE_OUT', marker='anticipation')
    c.hit(165, w, h, lag=34, k=0.10, carry=70)
    c.key(360, land, 'EASE_OUT', marker='recovery').key(700, STANCE, 'EASE_IN_OUT').write()

    # Air lights: the same hands with nothing to push off, so the legs do the counter-work: knees tucked, the opposite
    # knee driving up as each punch goes out.
    w = Y(root=(0, 0.8, -0.4), hips=(-4, 18, 0), chest=(4, 14, 0), la=HC(-3.4, -3.0, 5.0), **AIR_L)
    h = Y(root=(0, 1.4, 1.2), hips=(8, 0, 0), chest=(8, 30, 0), la=HC(-0.8, -2.6, 13.6), ra=HC(3.6, -2.4, 3.6), rl=L(8, 60, 5, 30), ll=L(-70, 104, 4, 24))
    a0 = Y(root=(0, 0.6, 0), **AIR_L)
    c = Clip(g, 'air_1', NOTE)
    c.key(0, a0).key(50, w, 'EASE_OUT')
    c.hit(110, w, h, lag=14, k=0.08, carry=45, settle=lerp(h, a0, 0.6), settle_at=240)
    c.key(450, a0, 'EASE_IN_OUT').write()

    w = Y(root=(0, 0.8, -0.6), hips=(-6, 28, 0), chest=(6, 20, 0), ra=HC(4.6, -4.2, 2.4), la=HC(-2.6, -2.0, 7.4), **AIR_L)
    h = Y(root=(0, 1.4, 1.6), hips=(12, -22, 0), chest=(10, -30, -3), ra=HC(-0.6, -3.0, 13.8), la=HC(-3.6, -2.2, 3.6), rl=L(-76, 110, 5, 24), ll=L(10, 56, 4, 30))
    c = Clip(g, 'air_2', NOTE)
    c.key(0, a0).key(55, w, 'EASE_OUT')
    c.hit(115, w, h, lag=24, k=0.10, carry=55, settle=lerp(h, a0, 0.6), settle_at=250)
    c.key(450, a0, 'EASE_IN_OUT').write()

    # air_3: a spinning back-fist with the hips thrown round under him.
    w = Y(root=(0, 0.8, 0), hips=(0, -34, 6), chest=(8, -26, 6), la=HC(-6.4, -3.6, 1.6, pole=(-1, 0.2, -0.4)), head=(4, -14, 0), **AIR_L)
    h = Y(root=(0, 1.6, 0.8), hips=(-6, 40, -4), chest=(4, 48, -6), la=HC(0.8, -2.4, 7.6, pole=(-1, 0.5, 0.2)), ra=HC(4.4, -3.2, 2.2),
          rl=L(-20, 40, 14, 20), ll=L(-64, 84, 10, 24), head=(2, 12, 0))
    c = Clip(g, 'air_3', NOTE)
    c.key(0, a0).key(60, w, 'EASE_OUT')
    c.hit(125, w, h, lag=28, k=0.14, carry=60, settle=lerp(h, a0, 0.5), settle_at=290)
    c.key(500, a0, 'EASE_IN_OUT').write()

    # uppercut: dropped right down onto bent knees with the fist by the hip, then both legs fire and the fist is carried
    # past his own chin; he comes off the floor with it and lands back in his guard.
    w = Y(root=(0.6, -4.6, -0.6), hips=(18, 30, 0), chest=(16, 18, 4), ra=HC(5.4, -10.5, 2.0, pole=(1, 0, -1)), la=HC(-3.0, -2.4, 6.0),
          head=(10, 0, 0))
    h = Y(root=(-0.4, 2.2, 2.0), hips=(-10, -22, 0), chest=(-10, -22, -4), ra=H(1.4, 30.0, 6.4, pole=(0.4, -0.2, 1)), la=HC(-4.6, -6.0, 1.0),
          rl=L(10, 14, 4, 34), ll=L(-28, 44, 4, 20), head=(-16, 0, 0))
    hang = Y(root=(-0.3, 1.4, 1.8), hips=(-6, -16, 0), chest=(-6, -16, -3), ra=H(1.6, 28.4, 5.0, pole=(0.4, -0.2, 1)), la=HC(-4.4, -5.6, 1.4),
             rl=L(6, 22, 4, 30), ll=L(-22, 40, 4, 20), head=(-10, 0, 0))
    c = Clip(g, 'uppercut', NOTE)
    c.key(0, STANCE).key(95, w, 'EASE_OUT', marker='anticipation')
    c.key(165, h, 'EASE_IN', lag=30, marker='impact').key(300, hang, 'EASE_OUT')
    c.key(480, Y(root=(0, -2.6, 0.6), hips=(10, 8, 0), chest=(10, 6, 0)), 'EASE_IN', marker='land').key(650, STANCE, 'EASE_OUT').write()

    # downslam: both hands clasped over his head, body arched like a bow; then he folds in half and the hammer comes
    # down between his knees.
    w = Y(root=(0, 1.6, -0.6), hips=(-18, 0, 0), chest=(-16, 0, 0), ra=HC(1.2, 6.5, -1.5, pole=(1, 0.2, 0.6)), la=HC(-1.2, 6.5, -1.5, pole=(-1, 0.2, 0.6)),
          rl=L(26, 70, 5, 30), ll=L(18, 60, 5, 30), head=(-18, 0, 0))
    h = Y(root=(0, 0.2, 1.6), hips=(22, 0, 0), chest=(22, 0, 0), ra=HC(1.2, -11.0, 6.0), la=HC(-1.2, -11.0, 6.0),
          rl=L(-60, 84, 6, 20), ll=L(-48, 70, 6, 20), head=(8, 0, 0))
    c = Clip(g, 'downslam', NOTE)
    c.key(0, a0).key(100, w, 'EASE_OUT', marker='anticipation')
    c.hit(170, w, h, lag=26, k=0.08, carry=70)
    c.key(420, Y(root=(0, -3.2, 0.8), hips=(22, 0, 0), chest=(16, 0, 0), ra=HC(3.0, -8.0, 5.0), la=HC(-3.0, -8.0, 5.0), rl=F(2.8, 0, -2.0), ll=F(-2.8, 0, 2.4)),
          'EASE_IN', marker='land')
    c.key(650, STANCE, 'EASE_IN_OUT').write()

    # stomp: the knee comes up to the chest, the arms fly out for balance, and the heel is driven straight down with his
    # weight dropped onto it.
    w = Y(root=(-0.8, -0.2, 0), hips=(-8, 6, 4), chest=(4, 4, 0), ra=HC(7.0, -3.5, 2.0), la=HC(-7.0, -4.0, 2.5),
          rl=L(-98, 112, 4, 10), ll=F(-2.2, 0, 0.6), head=(26, 0, 0))
    h = Y(root=(0.6, -3.6, 1.0), hips=(14, 4, -3), chest=(10, 2, 0), ra=HC(6.4, -8.0, -1.0), la=HC(-6.0, -8.0, 0.0),
          rl=F(2.2, 0, 3.6), ll=F(-2.6, 0, -0.6, toe=20), head=(18, 0, 0))
    c = Clip(g, 'stomp', NOTE)
    c.key(0, STANCE).key(90, w, 'EASE_OUT', marker='anticipation')
    c.hit(165, w, h, lag=0, k=0.06, carry=60)
    c.key(330, tweak(h, root_pos=(0, 0.8, 0)), 'EASE_OUT').key(650, STANCE, 'EASE_IN_OUT').write()

    # sprint: the superman punch. One long running stride, he leaves the ground off the left foot with the right arm
    # cocked, hangs flat in the air, and the fist arrives with all of the run behind it.
    run = Y(root=(0, -1.4, 0), hips=(26, 10, 0), chest=(14, -18, 0), ra=A(40, 0, 10, -80), la=A(-50, 0, 8, -84), rl=L(-52, 40, 3, 10), ll=L(40, 70, 3, 20),
            head=(-4, 0, 0))
    w = Y(root=(0, 1.2, 1.0), hips=(18, 36, 0), chest=(10, 24, 4), ra=HC(6.2, -1.0, -2.0, pole=(1, 0.2, -1)), la=HC(-2.0, -1.4, 9.0),
          rl=L(34, 96, 4, 30), ll=L(-40, 60, 4, 20))
    h = Y(root=(0, 1.8, 5.6), hips=(38, -30, 0), chest=(16, -34, -6), ra=H(-1.2, 19.4, 20.5), la=HC(-5.6, -5.0, -1.5, pole=(-1, 0, -1)),
          rl=L(44, 26, 4, 40), ll=L(10, 84, 4, 30), head=(-18, 0, 0))
    land = Y(root=(-0.6, -3.2, 5.0), hips=(20, -18, 0), chest=(12, -16, -2), ra=H(-0.8, 14.5, 14.5), la=HC(-4.6, -4.4, 1.0),
             rl=F(3.0, 0, 0.4, toe=30), ll=F(-2.4, 0, 6.0))
    c = Clip(g, 'sprint', NOTE)
    c.key(0, run).key(100, w, 'EASE_OUT', marker='anticipation')
    c.hit(170, w, h, lag=30, k=0.08, carry=80)
    c.key(430, land, 'EASE_IN', marker='land').key(800, STANCE, 'EASE_IN_OUT').write()

    # heavy_charge: he sits down into a long stance and draws the right fist back past his ribs, the left hand out in
    # front measuring the distance; held, breathing, until it is let go.
    hc = Y(root=(1.2, -4.0, -2.4), hips=(12, 44, 0), chest=(12, 28, 4), ra=HC(6.6, -7.0, -2.5, pole=(1, 0.2, -1)), la=H(-1.6, 18.0, 11.5),
           rl=F(3.0, 0, -4.2, toe=24, yaw=-30), ll=F(-2.6, 0, 4.2), head=(6, 0, 0))
    c = Clip(g, 'heavy_charge', NOTE, tremble=0.5)
    c.key(0, STANCE).key(220, lerp(STANCE, hc, 0.8), 'EASE_OUT').breathe(350, hc, period=1400, amp=0.8)
    c.write()

    # heavy: released. A hair deeper first, then the back leg straightens like a piston and the fist goes through the
    # guard with the shoulder behind it; the follow-through drags his back foot forward.
    deep = Y(root=(1.5, -4.6, -3.0), hips=(14, 50, 0), chest=(14, 32, 5), ra=HC(7.0, -7.4, -3.5, pole=(1, 0.2, -1)), la=H(-1.6, 17.0, 12.0),
             rl=F(3.0, 0, -4.2, toe=30, yaw=-30), ll=F(-2.6, 0, 4.2), head=(8, 0, 0))
    h = Y(root=(-1.2, -3.6, 6.4), hips=(24, -40, 0), chest=(16, -36, -6), ra=H(-1.4, 16.2, 20.4), la=HC(-6.0, -4.8, -2.0, pole=(-1, 0, -1)),
          rl=F(3.2, 0.8, -3.0, toe=64), ll=F(-2.6, 0, 5.4), head=(2, 0, 0))
    rec = Y(root=(-0.8, -3.0, 5.0), hips=(18, -22, 0), chest=(12, -18, -2), ra=H(-0.6, 14.0, 14.0), la=HC(-4.8, -4.4, 0.6),
            rl=F(3.0, 0, 0.6, toe=26), ll=F(-2.6, 0, 5.4))
    c = Clip(g, 'heavy', NOTE)
    c.key(0, hc).key(120, deep, 'EASE_OUT', marker='anticipation')
    c.hit(205, deep, h, lag=40, k=0.10, carry=85)
    c.key(470, rec, 'EASE_OUT', marker='recovery').key(800, STANCE, 'EASE_IN_OUT').write()

    # guard: forearms up in front of the face, elbows in, chin down behind them, knees soft.
    gd = Y(root=(0, -2.0, -0.4), hips=(8, 8, 0), chest=(12, 6, 0), ra=HC(2.6, -1.4, 5.2, pole=(0.5, 1, 0.2)), la=HC(-2.4, -1.0, 5.8, pole=(-0.5, 1, 0.2)),
           head=(10, 0, 0))
    c = Clip(g, 'guard', NOTE)
    c.key(0, lerp(STANCE, gd, 0.5)).breathe(150, gd, period=1600, amp=0.5)
    c.write()

    # Dashes: a burst off the far leg, the body thrown flat along the direction of travel, then the lead foot bites and
    # he is back over his feet.
    def dash(name, push, fly, catch):
        c = Clip(g, name, NOTE)
        c.key(0, push, 'EASE_OUT').key(70, push, 'EASE_OUT').key(170, fly, 'EASE_OUT').key(300, fly, 'LINEAR')
        c.key(380, catch, 'EASE_OUT').key(450, STANCE, 'EASE_IN_OUT').write()

    dash('dash_forward',
         Y(root=(0, -3.4, -1.0), hips=(26, 6, 0), chest=(16, 0, 0), ra=A(30, 0, 10, -70), la=A(-40, 0, 8, -90), head=(-10, 0, 0)),
         Y(root=(0, -2.6, 2.4), hips=(44, 0, 0), chest=(14, 0, 0), ra=A(62, 0, 16, -30), la=A(56, 0, 16, -34), rl=L(30, 30, 4, 40), ll=L(-66, 80, 4, 10),
           head=(-34, 0, 0)),
         Y(root=(0, -3.0, 1.6), hips=(20, 6, 0), chest=(12, 4, 0), rl=F(2.8, 0, -3.6, toe=30), ll=F(-2.4, 0, 4.6)))
    dash('dash_back',
         Y(root=(0, -3.2, 1.0), hips=(4, 10, 0), chest=(14, 6, 0), head=(6, 0, 0)),
         Y(root=(0, -2.0, -2.4), hips=(-14, 10, 0), chest=(16, 6, 0), ra=A(-70, 0, 14, -40), la=A(-76, 0, 14, -44), rl=L(-20, 50, 4, 10), ll=L(-60, 30, 4, -10),
           head=(8, 0, 0)),
         Y(root=(0, -3.2, -1.2), hips=(14, 12, 0), chest=(12, 8, 0), rl=F(2.8, 0, -4.8, toe=10), ll=F(-2.4, 0, 2.6)))
    side = (Y(root=(-1.2, -3.2, 0), hips=(8, 10, -8), chest=(8, 6, -6), head=(4, 14, 0)),
            Y(root=(2.6, -2.4, 0), hips=(6, 4, 22), chest=(4, 0, 12), ra=A(-20, 0, 50, -50), la=A(-30, 0, -6, -100), rl=L(-30, 60, 16, 10), ll=L(8, 20, -36, 20),
              head=(2, 8, -10)),
            Y(root=(1.4, -3.2, 0), hips=(8, 10, 8), chest=(8, 8, 4), rl=F(4.6, 0, -2.6), ll=F(-1.6, 0, 3.0, toe=20)))
    dash('dash_right', *side)
    dash('dash_left', *[mirror(p) for p in side])
    # The mirrored dash still ends in the (unmirrored) guard: dash() keys STANCE last.


def reactions():
    g = 'reactions'
    # hitstun: the head snaps away first, the chest caves round the blow and the weight is knocked onto the back foot;
    # a short stagger, then he is fighting to get his guard back up.
    hit = P(root=(0.4, -1.6, -2.2), hips=(-8, 10, 2), chest=(-16, 16, 4), head=(-24, 14, 6), follow=1.0,
            ra=A(-30, 0, 34, -70, 20), la=A(-44, 0, 30, -84, 10), rl=F(2.8, 0, -5.0), ll=F(-2.4, 0, 1.4, toe=16))
    fold = P(root=(0.2, -2.6, -1.4), hips=(10, 6, 0), chest=(20, 8, -2), head=(16, -6, 0), follow=1.0,
             ra=A(-48, 0, 14, -96), la=A(-56, 0, 12, -100), rl=F(2.8, 0, -4.6), ll=F(-2.4, 0, 1.6))
    c = Clip(g, 'reaction_hitstun', NOTE)
    c.key(0, lerp(STANCE, hit, 0.4)).key(70, hit, 'EASE_OUT', eases={'head': 'OVERSHOOT'}, marker='impact')
    c.key(260, fold, 'EASE_IN_OUT').key(520, lerp(fold, STANCE, 0.5), 'EASE_IN_OUT')
    c.write()

    # launched: lifted off his feet and folded round the blow, limbs trailing behind the body; then they whip through
    # as he tumbles.
    a = P(root=(0, 1.0, -1.0), hips=(-30, 0, 0), chest=(34, 0, 0), head=(30, 0, 0), follow=1.0,
          ra=A(-70, 0, 30, -20), la=A(-80, 0, 34, -24), rl=L(-64, 30, 8, 30), ll=L(-76, 40, 8, 30))
    b = P(root=(0, 1.6, -1.6), hips=(-52, 8, 0), chest=(-10, 6, 4), head=(-28, 0, 0), follow=1.0,
          ra=A(-150, 0, 40, -30), la=A(-136, 0, 44, -36), rl=L(-20, 50, 12, 40), ll=L(-40, 70, 10, 40))
    c = Clip(g, 'reaction_hit_launched', NOTE)
    c.key(0, lerp(STANCE, a, 0.5)).key(80, a, 'EASE_OUT', marker='impact').key(380, b, 'EASE_IN_OUT').key(700, lerp(b, a, 0.35), 'EASE_IN_OUT')
    c.d.update(loop=True, loopStart=380, loopEnd=1020)
    c.key(1020, b, 'EASE_IN_OUT')
    c.duration = 1020
    c.write()

    # knockdown: flat on his back, one knee up, an arm thrown out; the chest still heaving.
    down = P(root=(0, -9.6, -6.0), hips=(-84, 0, 0), chest=(-4, 6, 0), head=(-6, 20, 0), follow=1.0,
             ra=A(-20, 0, 70, -30), la=A(10, 0, 40, -60), rl=L(-50, 80, 10, 30), ll=L(-6, 10, 12, 30))
    c = Clip(g, 'reaction_knockdown', NOTE)
    c.key(0, down).breathe(300, down, period=2000, amp=1.2)
    c.write()

    # guard broken: the arms are blown open and he reels back on his heels, chest and throat wide open, before he can
    # gather himself.
    open_ = P(root=(0, -1.0, -3.0), hips=(-14, 0, 0), chest=(-20, -8, 0), head=(-26, 6, 0), follow=1.0,
              ra=A(-96, 0, 74, -40, 30), la=A(-70, 0, 84, -30, 30), rl=F(2.8, 0, -5.6), ll=F(-2.6, 0, 0.6, toe=30))
    sag = P(root=(0, -2.6, -2.2), hips=(6, 0, 0), chest=(14, -4, 0), head=(18, 0, 0), follow=1.0,
            ra=A(-10, 0, 30, -30), la=A(-4, 0, 34, -24), rl=F(2.8, 0, -5.0), ll=F(-2.6, 0, 1.0))
    c = Clip(g, 'reaction_guard_broken', NOTE, tremble=0.8)
    c.key(0, lerp(STANCE, open_, 0.5)).key(90, open_, 'EASE_OUT', eases={'rightArm': 'OVERSHOOT', 'leftArm': 'OVERSHOOT'}, marker='impact')
    c.key(520, sag, 'EASE_IN_OUT').breathe(700, sag, period=1500, amp=1.4)
    c.write()

    # pulled: dragged forward by the chest with his heels dug in, leaning back against it, arms trailing.
    pull = P(root=(0, -1.4, 2.0), hips=(-20, 0, 0), chest=(16, 0, 0), head=(-10, 0, 0), follow=1.0,
             ra=A(-76, 0, 20, -20), la=A(-84, 0, 24, -16), rl=F(2.4, 0, 4.4, toe=-24), ll=F(-2.4, 0, 6.0, toe=-28))
    c = Clip(g, 'reaction_pulled', NOTE, tremble=1.0)
    c.key(0, pull).breathe(200, pull, period=900, amp=1.5)
    c.write()

    # overload (Infinite Void): everything arriving at once. Head thrown back, hands half raised and useless, knees
    # going.
    ov = P(root=(0, -2.2, -0.6), hips=(-6, 0, 0), chest=(-14, 0, 0), head=(-38, 0, 8), follow=1.0,
           ra=A(-34, 0, 26, -60, 30), la=A(-24, 0, 30, -70, 30), rl=F(2.2, 0, -0.6), ll=F(-2.4, 0, 0.8))
    c = Clip(g, 'reaction_overload', NOTE)
    c.key(0, ov).breathe(300, ov, period=2600, amp=1.8, sway=5)
    c.write()


if __name__ == '__main__':
    common()
    reactions()
    print('wrote', len(WRITTEN), 'clips:', ' '.join(WRITTEN))
