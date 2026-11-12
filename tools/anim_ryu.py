#!/usr/bin/env python3
"""
Authors Ryu Ishigori's clips (assets/jjk/animations/ryu: True Cannon, and Decadence once Every Last Drop has awakened
him) with tools/animkit.py. Replaces the old gen_ryu_anims.py.

    python3 tools/anim_ryu.py

How he moves: the biggest body in the game, carried upright. Nothing about him is tidy. He stands wide with his chest
out and his chin up, fists low and loose, and every blow is a full-weight slug thrown through the target with a step,
the shoulder first and the back foot dragged after it. The cannon of his pompadour fires by a snap of the whole head
forward, chest and hips thrown back against the recoil and the feet braced wide. Decadence takes the same body and
makes it heavier: slams, grabs and trades, nothing in a hurry, the ground the thing he hits most.

Timings are the abilities' own ticks (one tick = 50 ms): the frame a blow lands on is the tick the code lands it.
The M1 set keeps MeleeSystem's: lights open at 100 ms, the finisher / uppercut / downslam / sprint / stomp at 150 ms,
the heavy at 200 ms; his neutral third hit is the ray (ryu_m1_ray), fired on its 4th tick.
"""
from animkit import *

NOTE = 'tools/anim_ryu.py'

# --- The stance: wide, upright, the chest out, fists carried low and loose, the chin up a touch ---
FL, FR = F(-3.2, 0, 2.2, yaw=10), F(3.4, 0, -2.4, toe=8, yaw=22)
LO_R, LO_L = HC(5.8, -8.2, 3.0), HC(-5.0, -7.4, 4.4)


def R(root=(0, -0.8, 0), hips=(3, 12, 0), chest=(4, 8, 0), ra=LO_R, la=LO_L, rl=FR, ll=FL, head=(-3, 0, 0), **kw):
    return P(root=root, hips=hips, chest=chest, ra=ra, la=la, rl=rl, ll=ll, head=head, **kw)


REST = R()
AIR = dict(rl=L(-22, 56, 8, 24), ll=L(-46, 76, 8, 24))
AIR0 = R(root=(0, 0.6, 0), **AIR)
BRACE_R, BRACE_L = F(4.0, 0, -3.6, toe=6, yaw=26), F(-3.6, 0, 3.0, yaw=10)   # the feet set wide against a recoil


def cannon(k=1.0, sink=0.0, back=0.0, hips_x=None):
    """
    The pompadour's cannon fired, by k: the whole head snapped forward and down, the chest and hips thrown back against
    it, the fists dropped and the feet braced wide. Negative k is the head drawn back to aim. sink lowers him on his
    braced feet; back pushes him further back off the shot.
    """
    rec = max(0.0, k)
    return R(root=(0, -1.4 - 0.8 * rec - sink, -2.4 * k - back), hips=(-7 * k if hips_x is None else hips_x, 4, 0), chest=(-9 * k, 2, 0), head=(28 * k, 0, 0),
             follow=1.0, ra=A(-30 - 6 * rec, 0, 28 + 12 * rec, -66), la=A(-30 - 6 * rec, 0, 28 + 12 * rec, -66), rl=BRACE_R, ll=BRACE_L)


AIM = cannon(-0.5)


def slug(c, t_w, t_h, w, h, end, t_end, lag=34, k=0.12, carry=70, rec=None, t_rec=None, start=None):
    """One of his blows: a wind-up, the hit whipped in with the hips ahead of the fist, carried through, then back."""
    c.key(0, start if start is not None else REST)
    c.key(t_w, w, 'EASE_OUT', marker='anticipation')
    c.hit(t_h, w, h, lag=lag, k=k, carry=carry)
    if rec is not None:
        c.key(t_rec, rec, 'EASE_OUT', marker='recovery')
    c.key(t_end, end, 'EASE_IN_OUT').write()


# --- The blows he throws, used by the M1s and the moves ---

# A lead left thrown big: the shoulder rolled over it, a half step in.
JAB_W = R(root=(0.4, -1.6, -0.6), hips=(4, 24, 0), chest=(6, 18, 0), la=HC(-4.0, -4.6, 5.0), ra=HC(6.0, -7.6, 2.0), head=(-2, 0, 0))
JAB_H = R(root=(-0.6, -1.8, 3.4), hips=(10, -4, 0), chest=(10, 28, -2), la=H(-1.0, 18.8, 17.6), ra=HC(5.2, -7.0, 2.8), rl=F(3.4, 0, -2.4, toe=38, yaw=22),
          ll=F(-3.2, 0, 3.6, yaw=10), head=(0, 0, 0))
# The right: an overhand cross with the whole trunk behind it and the rear heel off the floor.
CROSS_W = R(root=(0.8, -2.2, -1.0), hips=(6, 36, 0), chest=(8, 26, 3), ra=HC(6.6, -4.0, -1.0, pole=(1, 0.3, -0.6)), la=HC(-3.0, -3.4, 6.6), head=(0, 0, 0))
CROSS_H = R(root=(-1.0, -2.4, 4.6), hips=(16, -32, 0), chest=(12, -36, -5), ra=H(-0.8, 17.6, 18.8), la=HC(-4.8, -4.6, 2.2), rl=F(3.4, 0.8, -1.8, toe=58, yaw=-10),
            ll=F(-3.2, 0, 3.6), head=(2, 0, 0))
# A left hook to the body, the knees sunk under it.
HOOK_W = R(root=(-1.2, -2.8, 0.2), hips=(10, -24, 4), chest=(10, -22, 6), la=HC(-6.6, -5.0, 1.2, pole=(-1, 0.2, -0.4)), ra=HC(5.0, -7.0, 3.0), head=(4, -10, 0))
HOOK_H = R(root=(0.8, -2.6, 2.0), hips=(8, 32, -3), chest=(8, 44, -5), la=HC(0.8, -4.6, 7.6, pole=(-1, 0.5, 0.2)), ra=HC(5.4, -6.0, 2.4), ll=F(-3.2, 0, 2.8, toe=20, yaw=-30),
           head=(2, 10, 0))
# The finisher: an overhand right brought down from above his head with a stamp of the lead foot under it.
OVER_W = R(root=(1.2, -3.0, -2.4), hips=(10, 44, 0), chest=(6, 30, 4), ra=HC(6.8, 3.0, -3.0, pole=(1, 0.4, -0.6)), la=HC(-2.6, -2.4, 7.6), head=(0, 0, 0))
OVER_H = R(root=(-1.2, -3.8, 6.4), hips=(28, -36, 0), chest=(22, -34, -6), ra=H(-1.4, 12.5, 19.0, pole=(0.6, -1, 0)), la=HC(-5.4, -5.8, -1.0, pole=(-1, 0, -1)),
           rl=F(3.4, 2.0, -1.2, toe=64), ll=F(-3.2, 0, 6.8), head=(8, 0, 0))
OVER_LAND = R(root=(-0.8, -3.4, 5.4), hips=(20, -24, 0), chest=(14, -22, -3), ra=H(-0.8, 13.0, 15.0), la=HC(-5.0, -5.0, 0.6), rl=F(3.2, 0, 1.4, toe=30), ll=F(-3.2, 0, 6.6))
# A straight right from a long stance: the cannon of a fist, the body a piston behind it.
STRAIGHT_W = R(root=(1.4, -4.0, -2.6), hips=(12, 46, 0), chest=(12, 30, 4), ra=HC(7.0, -7.4, -3.0, pole=(1, 0.2, -1)), la=H(-1.8, 17.0, 11.5),
               rl=F(3.6, 0, -4.4, toe=24, yaw=30), ll=F(-3.2, 0, 4.4), head=(4, 0, 0))
STRAIGHT_H = R(root=(-1.4, -3.6, 7.0), hips=(26, -42, 0), chest=(16, -38, -6), ra=H(-1.6, 16.0, 21.4), la=HC(-6.0, -5.0, -2.0, pole=(-1, 0, -1)),
               rl=F(3.4, 1.4, -2.8, toe=66), ll=F(-3.2, 0, 6.2), head=(2, 0, 0))
STRAIGHT_REC = R(root=(-1.0, -3.0, 5.6), hips=(18, -22, 0), chest=(12, -18, -2), ra=H(-0.8, 13.5, 15.0), la=HC(-4.8, -4.6, 0.6), rl=F(3.2, 0, 0.8, toe=26), ll=F(-3.2, 0, 6.2))


def melee():
    g = 'ryu'
    M = dict(priority='ATTACK', blendIn=40, blendOut=150)
    # ryu_light_1: the big left, a half step in with it.
    slug(Clip(g, 'ryu_light_1', NOTE, 450, **M), 50, 112, JAB_W, JAB_H, REST, 450, lag=20, k=0.10, carry=50)
    # ryu_light_2: the right over the top of it, the rear heel up and the whole trunk turned through.
    slug(Clip(g, 'ryu_light_2', NOTE, 450, **M), 52, 114, CROSS_W, CROSS_H, REST, 450, lag=30, k=0.12, carry=60)
    # ryu_light_3 (the fallback third: his chain is three long and ends on the finisher or the ray): the body hook.
    slug(Clip(g, 'ryu_light_3', NOTE, 500, **M), 60, 124, HOOK_W, HOOK_H, REST, 500, lag=30, k=0.14, carry=60)
    # ryu_light_4, the finisher: the overhand right, hauled down from above his head, the lead foot stamped under it.
    slug(Clip(g, 'ryu_light_4', NOTE, 700, priority='ATTACK', blendIn=25, blendOut=200), 95, 166, OVER_W, OVER_H, REST, 700, lag=36, k=0.10, carry=80,
         rec=OVER_LAND, t_rec=400)

    A_ = dict(priority='ATTACK', blendIn=50, blendOut=150)
    # Air lights: the same hands with the legs trailing, the opposite knee driving up as each one goes out.
    w = R(root=(0, 0.8, -0.4), hips=(-4, 24, 0), chest=(6, 18, 0), la=HC(-4.0, -4.6, 5.0), ra=HC(6.0, -7.6, 2.0), **AIR)
    h = R(root=(0, 1.4, 1.4), hips=(6, -4, 0), chest=(10, 28, -2), la=HC(-1.0, -3.0, 13.6), ra=HC(5.2, -7.0, 2.8), rl=L(10, 56, 8, 30), ll=L(-68, 100, 8, 24))
    c = Clip(g, 'ryu_air_1', NOTE, 450, **A_)
    c.key(0, AIR0).key(50, w, 'EASE_OUT')
    c.hit(112, w, h, lag=16, k=0.10, carry=50, settle=lerp(h, AIR0, 0.6), settle_at=250)
    c.key(450, AIR0, 'EASE_IN_OUT').write()
    w = R(root=(0, 0.8, -0.6), hips=(-6, 36, 0), chest=(8, 26, 3), ra=HC(6.6, -4.0, -1.0, pole=(1, 0.3, -0.6)), la=HC(-3.0, -3.4, 6.6), **AIR)
    h = R(root=(0, 1.4, 1.8), hips=(12, -30, 0), chest=(12, -36, -5), ra=HC(-0.8, -3.4, 14.0), la=HC(-4.8, -4.6, 2.2), rl=L(-74, 106, 8, 24), ll=L(12, 56, 8, 30))
    c = Clip(g, 'ryu_air_2', NOTE, 450, **A_)
    c.key(0, AIR0).key(52, w, 'EASE_OUT')
    c.hit(114, w, h, lag=24, k=0.12, carry=55, settle=lerp(h, AIR0, 0.6), settle_at=250)
    c.key(450, AIR0, 'EASE_IN_OUT').write()
    # ryu_air_3: both fists hammered down together.
    w = R(root=(0, 1.4, -0.4), hips=(-16, 0, 0), chest=(-14, 0, 0), ra=HC(1.6, 6.0, -2.0, pole=(1, 0.2, 0.5)), la=HC(-1.6, 6.0, -2.0, pole=(-1, 0.2, 0.5)),
          rl=L(24, 70, 8, 30), ll=L(16, 60, 8, 30), head=(-14, 0, 0))
    h = R(root=(0, 1.0, 1.6), hips=(22, 0, 0), chest=(22, 0, 0), ra=HC(1.6, -9.5, 7.0), la=HC(-1.6, -9.5, 7.0), rl=L(-58, 84, 8, 20), ll=L(-46, 70, 8, 20), head=(10, 0, 0))
    c = Clip(g, 'ryu_air_3', NOTE, 500, **A_)
    c.key(0, AIR0).key(62, w, 'EASE_OUT')
    c.hit(124, w, h, lag=20, k=0.10, carry=60, settle=lerp(h, AIR0, 0.6), settle_at=300)
    c.key(500, AIR0, 'EASE_IN_OUT').write()

    # ryu_uppercut: dropped onto bent knees with the fist by his knee, then everything fires upward at once and the
    # fist finishes above his own head; he leaves the floor with it.
    w = R(root=(0.8, -5.2, -0.6), hips=(20, 34, 0), chest=(16, 20, 4), ra=HC(6.6, -11.5, 1.5, pole=(1, 0, -1)), la=HC(-4.0, -3.0, 5.0), head=(8, 0, 0))
    h = R(root=(-0.4, 2.6, 2.4), hips=(-12, -28, 0), chest=(-12, -24, -4), ra=H(1.2, 30.6, 7.0, pole=(0.4, -0.2, 1)), la=A(36, 0, 30, -30),
          rl=L(12, 16, 6, 34), ll=L(-36, 62, 6, 20), head=(-18, 0, 0))
    c = Clip(g, 'ryu_uppercut', NOTE, 650, priority='ATTACK', blendIn=25, blendOut=200)
    c.key(0, REST).key(95, w, 'EASE_OUT', marker='anticipation').key(166, h, 'EASE_IN', lag=32, marker='impact')
    c.key(300, tweak(h, root_pos=(0, 0.8, 0), rightArm=(8, 0, 0)), 'EASE_OUT')
    c.key(490, R(root=(0, -3.4, 0.8), hips=(14, 4, 0), chest=(10, 2, 0)), 'EASE_IN', marker='land').key(650, REST, 'EASE_OUT').write()
    # ryu_downslam: a double axe-handle out of the air, the whole body folding over it.
    w = R(root=(0, 1.6, -0.6), hips=(-20, 0, 0), chest=(-18, 0, 0), ra=HC(1.2, 6.5, -2.5, pole=(1, 0.2, 0.5)), la=HC(-1.2, 6.5, -2.5, pole=(-1, 0.2, 0.5)),
          rl=L(28, 76, 8, 30), ll=L(18, 66, 8, 30), head=(-18, 0, 0))
    h = R(root=(0, 0.2, 1.8), hips=(26, 0, 0), chest=(24, 0, 0), ra=HC(1.2, -11.5, 6.0), la=HC(-1.2, -11.5, 6.0), rl=L(-62, 88, 10, 20), ll=L(-50, 74, 10, 20), head=(8, 0, 0))
    c = Clip(g, 'ryu_downslam', NOTE, 650, priority='ATTACK', blendIn=25, blendOut=200)
    c.key(0, AIR0).key(102, w, 'EASE_OUT', marker='anticipation')
    c.hit(170, w, h, lag=26, k=0.08, carry=70)
    c.key(430, R(root=(0, -3.8, 1.0), hips=(22, 0, 0), chest=(16, 0, 0), ra=HC(3.0, -9.0, 5.0), la=HC(-3.0, -9.0, 5.0)), 'EASE_IN', marker='land')
    c.key(650, REST, 'EASE_IN_OUT').write()
    # ryu_stomp: the knee comes up to his chest and his weight is dropped through the heel.
    w = R(root=(-0.8, -0.4, 0), hips=(-8, 6, 4), chest=(6, 4, 0), ra=HC(7.0, -4.0, 2.0), la=HC(-7.0, -4.5, 2.5), rl=L(-100, 114, 6, 10), ll=F(-3.2, 0, 1.0), head=(26, 0, 0))
    h = R(root=(0.6, -3.8, 1.2), hips=(16, 4, -3), chest=(10, 2, 0), ra=HC(6.4, -8.5, -1.0), la=HC(-6.0, -8.5, 0.0), rl=F(3.0, 0, 4.0), ll=F(-3.2, 0, -0.4, toe=20), head=(20, 0, 0))
    c = Clip(g, 'ryu_stomp', NOTE, 650, priority='ATTACK', blendIn=75, blendOut=150)
    c.key(0, REST).key(90, w, 'EASE_OUT', marker='anticipation')
    c.hit(165, w, h, lag=0, k=0.06, carry=60)
    c.key(330, tweak(h, root_pos=(0, 0.8, 0)), 'EASE_OUT').key(650, REST, 'EASE_IN_OUT').write()
    # ryu_sprint: he comes out of the run with the right fist wound right back and leaps into an overhand, the whole
    # run behind it; he lands deep over the front leg.
    run = R(root=(0, -1.6, 0), hips=(26, 10, 0), chest=(12, -16, 0), ra=A(40, 0, 10, -80), la=A(-50, 0, 8, -84), rl=L(-52, 40, 3, 10), ll=L(40, 70, 3, 20), head=(-6, 0, 0))
    w = R(root=(0, 1.4, 1.0), hips=(14, 40, 0), chest=(6, 28, 4), ra=HC(6.8, 3.5, -3.5, pole=(1, 0.4, -0.6)), la=HC(-2.4, -2.0, 8.6), rl=L(36, 96, 4, 30), ll=L(-40, 60, 4, 20),
          head=(-4, 0, 0))
    h = R(root=(0, 1.0, 5.8), hips=(36, -32, 0), chest=(18, -34, -6), ra=H(-1.4, 13.0, 20.0, pole=(0.6, -1, 0)), la=HC(-5.6, -5.4, -1.5, pole=(-1, 0, -1)),
          rl=L(44, 26, 4, 40), ll=L(-6, 84, 4, 30), head=(4, 0, 0))
    c = Clip(g, 'ryu_sprint', NOTE, 800, priority='MOVEMENT', blendIn=50, blendOut=200)
    c.key(0, run).key(100, w, 'EASE_OUT', marker='anticipation')
    c.hit(170, w, h, lag=30, k=0.08, carry=80)
    c.key(430, OVER_LAND, 'EASE_IN', marker='land').key(800, REST, 'EASE_IN_OUT').write()
    # ryu_heavy_charge: the long stance, the right fist drawn back past his ribs with the left out in front of him,
    # shaking with how much is in it.
    c = Clip(g, 'ryu_heavy_charge', NOTE, 350, hold=True, priority='ATTACK', blendIn=75, blendOut=150, tremble=0.6)
    c.key(0, REST).key(220, lerp(REST, STRAIGHT_W, 0.85), 'EASE_OUT').breathe(350, STRAIGHT_W, period=1200, amp=1.0)
    c.write()
    # ryu_heavy: let go. A hair deeper, then the back leg straightens like a piston and the straight goes through
    # whatever was in front of it; the back foot is dragged a step after.
    deep = R(root=(1.7, -4.8, -3.1), hips=(14, 52, 0), chest=(14, 34, 4), ra=HC(7.2, -7.8, -3.5, pole=(1, 0.2, -1)), la=H(-1.8, 16.5, 12.0),
             rl=F(3.6, 0, -4.4, toe=30, yaw=30), ll=F(-3.2, 0, 4.4), head=(5, 0, 0))
    c = Clip(g, 'ryu_heavy', NOTE, 800, priority='ATTACK', blendIn=25, blendOut=250)
    c.key(0, STRAIGHT_W).key(120, deep, 'EASE_OUT', marker='anticipation')
    c.hit(205, deep, STRAIGHT_H, lag=42, k=0.10, carry=90)
    c.key(480, STRAIGHT_REC, 'EASE_OUT', marker='recovery').key(800, REST, 'EASE_IN_OUT').write()


def cannon_moves():
    g = 'ryu'
    # ryu_m1_ray: the neutral third hit. The head is drawn back as the cannon fills, then snapped forward on the 4th
    # tick, the whole body rocked back off it, and he stands up out of the recoil.
    c = Clip(g, 'ryu_m1_ray', NOTE)
    c.key(0, REST).key(110, AIM, 'EASE_OUT', marker='aim').key(200, cannon(1.0), 'EASE_IN', lag=20, marker='fire')
    c.key(270, cannon(1.2), 'EASE_OUT', marker='recoil').key(380, cannon(0.5), 'EASE_IN_OUT').key(500, REST, 'EASE_IN_OUT').write()

    # Granite Blast. ryu_granite_aim: the head drawn back and the feet set, held; the longer it is held the lower he sinks.
    deep = cannon(-0.7, sink=1.2, hips_x=9)
    c = Clip(g, 'ryu_granite_aim', NOTE, tremble=0.3)
    c.key(0, REST).key(250, AIM, 'EASE_OUT', marker='aim').breathe(600, deep, period=1400, amp=0.6)
    c.write()
    # ryu_granite_fire: the tap. Snapped forward on the 2nd tick, rocked back, up again.
    c = Clip(g, 'ryu_granite_fire', NOTE)
    c.key(0, AIM).key(100, cannon(1.0), 'EASE_IN', lag=14, marker='fire').key(160, cannon(1.2), 'EASE_OUT', marker='recoil')
    c.key(300, cannon(0.5), 'EASE_IN_OUT').key(450, REST, 'EASE_IN_OUT').write()
    # ryu_granite_fire_held: the piercing shot. A bigger snap, the recoil shoving him back a whole step.
    big = cannon(1.5, sink=0.6, back=3.0)
    c = Clip(g, 'ryu_granite_fire_held', NOTE)
    c.key(0, deep).key(80, big, 'EASE_IN', lag=16, marker='fire').key(180, tweak(big, head=(6, 0, 0), chest=(-4, 0, 0)), 'EASE_OUT', marker='recoil')
    c.key(400, cannon(0.6), 'EASE_IN_OUT').key(700, REST, 'EASE_IN_OUT').write()
    # ryu_granite_dash: a blast fired backward out of a front dash to throw himself on. The head is thrown back and up
    # as it loops behind him, and the recoil pitches him straight into a sprint.
    loop = R(root=(0, -0.8, -1.0), hips=(-16, 0, 0), chest=(-20, 0, 0), head=(-36, 0, 0), follow=1.0, ra=A(-20, 0, 50, -40), la=A(-20, 0, 50, -40),
             rl=BRACE_R, ll=BRACE_L)
    run = R(root=(0, -1.4, 2.6), hips=(34, 6, 0), chest=(14, -14, 0), ra=A(44, 0, 12, -80), la=A(-54, 0, 10, -84), rl=L(-56, 40, 3, 10), ll=L(44, 70, 3, 20), head=(-14, 0, 0))
    run2 = R(root=(0, -1.2, 2.6), hips=(34, -6, 0), chest=(14, 14, 0), la=A(44, 0, 12, -80), ra=A(-54, 0, 10, -84), ll=L(-56, 40, 3, 10), rl=L(44, 70, 3, 20), head=(-14, 0, 0))
    c = Clip(g, 'ryu_granite_dash', NOTE)
    c.key(0, cannon(-0.6)).key(180, loop, 'EASE_IN', lag=20, marker='fire').key(260, run, 'EASE_OUT', marker='dash').key(520, run2, 'LINEAR')
    c.key(800, REST, 'EASE_IN_OUT').write()

    # Appetizer: two blasts from the pompadour (ticks 8 and 17), then both fists raised and driven into the floor for
    # the ray that rises out of the ground ahead (tick 32); done at 44.
    raise_ = R(root=(0, 0.4, -0.6), hips=(-14, 0, 0), chest=(-18, 0, 0), ra=HC(1.8, 7.0, -2.0, pole=(1, 0.2, 0.5)), la=HC(-1.8, 7.0, -2.0, pole=(-1, 0.2, 0.5)),
               rl=BRACE_R, ll=BRACE_L, head=(-20, 0, 0))
    floor = R(root=(0, -6.6, 2.4), hips=(38, 0, 0), chest=(28, 0, 0), ra=H(1.6, 1.4, 9.5), la=H(-1.6, 1.4, 9.5), rl=F(3.6, 0, -3.8, toe=50, yaw=26), ll=F(-3.4, 0, 3.6, yaw=10),
              head=(24, 0, 0))
    c = Clip(g, 'ryu_appetizer', NOTE)
    c.key(0, REST).key(300, AIM, 'EASE_OUT', marker='aim').key(400, cannon(1.0), 'EASE_IN', lag=14, marker='blast 1').key(470, cannon(1.15), 'EASE_OUT')
    c.key(620, cannon(-0.5), 'EASE_IN_OUT', marker='aim').key(850, cannon(1.0), 'EASE_IN', lag=14, marker='blast 2').key(920, cannon(1.15), 'EASE_OUT')
    c.key(1100, cannon(0.3), 'EASE_IN_OUT').key(1320, raise_, 'EASE_OUT', lag=30, marker='anticipation')
    c.key(1600, floor, 'EASE_IN', lag=30, marker='impact: the ray').key(1680, tweak(floor, root_pos=(0, 0.8, 0), chest=(-5, 0, 0)), 'EASE_OUT')
    c.key(1800, floor, 'EASE_IN_OUT').key(2200, REST, 'EASE_IN_OUT', marker='recovery').write()

    # Restyle. ryu_restyle_pose: a comb through the pompadour, chin up, and the hand flicked away.
    comb = R(root=(0, -0.4, 0), hips=(-2, 6, 0), chest=(-4, 2, 0), ra=HC(2.2, 7.2, 2.4, pole=(1, 0.6, 0), wrist=-30), la=A(-6, 0, 10, -30), head=(-18, 4, 0), follow=1.0)
    comb_back = tweak(comb, rightArm=(-10, -20, -16), rightForearm=(-8, 0, 0), head=(-6, 2, 0))
    flick = R(root=(0, -0.4, 0), hips=(-2, 6, 0), chest=(-4, 2, 0), ra=A(-50, 0, 60, -30, 20), la=A(-6, 0, 10, -30), head=(-8, 0, 0))
    c = Clip(g, 'ryu_restyle_pose', NOTE)
    c.key(0, REST).key(220, comb, 'EASE_OUT', marker='comb').key(600, comb_back, 'EASE_IN_OUT').key(800, flick, 'EASE_OUT', marker='flick')
    c.key(1000, REST, 'EASE_IN_OUT').write()
    # ryu_restyle_comb: overheated. Three long strokes of the comb back over the pompadour, steam coming off it.
    c = Clip(g, 'ryu_restyle_comb', NOTE)
    c.key(0, REST).key(250, comb, 'EASE_OUT', marker='comb')
    for i, t in enumerate((700, 1100, 1600, 2100, 2500)):
        c.key(t, comb_back if i % 2 == 0 else comb, 'EASE_IN_OUT')
    c.key(2750, REST, 'EASE_IN_OUT').write()
    # ryu_restyle_knuckles (Decadence): the knuckles cracked against the other palm, then the neck.
    kn = R(root=(0, -1.0, 0), hips=(4, 2, 0), chest=(8, 0, 0), ra=HC(1.6, -3.0, 6.4, pole=(1, 0.5, 0)), la=HC(-1.2, -3.4, 6.2, pole=(-1, 0.5, 0)), head=(12, 0, 0))
    c = Clip(g, 'ryu_restyle_knuckles', NOTE)
    c.key(0, REST).key(250, kn, 'EASE_OUT', marker='knuckles').key(450, tweak(kn, rightHand=(-30, 0, 0), chest=(2, 0, 0)), 'EASE_IN', marker='crack')
    c.key(650, kn, 'EASE_OUT').key(850, tweak(kn, head=(-8, 0, 24)), 'EASE_IN', marker='neck').key(1100, REST, 'EASE_IN_OUT').write()


def eld():
    g = 'ryu'
    # Every Last Drop: the right arm out with a finger on them, the left hand clamped on the wrist; he sinks as it
    # charges, and the shot shoves him back onto his heels.
    pt = R(root=(0, -2.0, 0), hips=(6, -24, 0), chest=(4, -30, 0), ra=H(0.2, 18.4, 14.0), la=H(0.8, 17.0, 9.6, pole=(-1, -0.4, 0)), rl=F(3.6, 0, -4.0, toe=12, yaw=34),
           ll=F(-3.2, 0, 3.4), head=(2, 0, 0))
    c = Clip(g, 'ryu_eld_point', NOTE)
    c.key(0, REST).key(300, pt, 'OVERSHOOT', lag=30, marker='point').key(400, pt, 'EASE_IN_OUT').write()
    def stage(down, back, lean, twist, arm):
        return R(root=(0, -2.0 - down, -back), hips=(6 + lean, -24 + twist, 0), chest=(4 + lean, -30 + twist, 0), ra=H(0.2, 18.4 + arm, 14.0 + back * 0.4),
                 la=H(0.8, 17.0 + arm, 9.6 + back * 0.4, pole=(-1, -0.4, 0)), rl=F(3.6, 0, -4.0 - back * 0.5, toe=12, yaw=34), ll=F(-3.2, 0, 3.4 - back * 0.3),
                 head=(2 - lean, 0, 0))
    low = stage(2.4, 1.0, 0, 0, 0)
    c = Clip(g, 'ryu_eld_charge', NOTE, tremble=1.2)
    c.key(0, REST).key(400, pt, 'EASE_OUT', lag=40, marker='point').key(1800, low, 'EASE_IN_OUT', marker='charge')
    c.key(2500, stage(3.0, 1.4, 2, -4, 0.2), 'EASE_IN', marker='locked').write()
    fire = stage(2.4, 5.0, -10, 4, 0.6)
    c = Clip(g, 'ryu_eld_fire', NOTE, tremble=1.6)
    c.key(0, low).key(80, fire, 'EASE_IN', lag=16, marker='fire').key(200, stage(2.6, 5.6, -13, 4, 0.4), 'EASE_OUT', marker='recoil')
    c.key(600, fire, 'EASE_IN_OUT').write()


def ground_kit():
    g = 'ryu'
    # Unsatisfied: three blows (ticks 5, 9, 13) walking them backward, the back clash (20), the toss (26), done at 36.
    turn = P(root=(0.6, -2.4, 0.4), yaw=70, hips=(8, 20, 0), chest=(6, 16, 0), ra=HC(6.0, -6.0, 1.0), la=HC(-3.4, -4.0, 6.0), rl=F(3.6, 2.4, -2.6, yaw=20, rel=True),
             ll=F(-3.2, 2.4, 2.4, rel=True), head=(-4, -60, 0), follow=1.0)
    ram = P(root=(-0.4, -2.8, 3.0), yaw=170, hips=(18, 0, 0), chest=(14, 0, 0), ra=A(-20, 0, 36, -50), la=A(-24, 0, 34, -50), rl=F(3.6, 2.8, 2.0, toe=20, rel=True),
            ll=F(-3.4, 2.8, -3.0, rel=True), head=(-10, -70, 0), follow=1.0)
    ram2 = P(root=(-0.4, -3.4, 3.8), yaw=170, hips=(22, 0, 0), chest=(18, 0, 0), ra=A(-20, 0, 36, -50), la=A(-24, 0, 34, -50), rl=F(3.6, 3.4, 1.2, toe=30, rel=True),
             ll=F(-3.4, 3.4, -3.8, rel=True), head=(-10, -70, 0), follow=1.0)
    heave_w = P(root=(0, -3.6, 1.0), yaw=300, hips=(26, 0, 0), chest=(20, 0, 0), ra=HC(4.0, -11.0, 5.0), la=HC(-4.0, -11.0, 5.0), rl=F(3.4, 3.6, -2.0, rel=True),
                ll=F(-3.2, 3.6, 2.4, rel=True), head=(14, 0, 0), follow=1.0)
    heave = P(root=(0, 1.2, 1.0), yaw=360, hips=(-12, 0, 0), chest=(-16, 0, 0), ra=A(-168, 0, 18, -24), la=A(-168, 0, 18, -24), rl=F(3.4, 0, -2.0, toe=36, rel=True),
              ll=F(-3.2, 0, 2.4, toe=36, rel=True), head=(-26, 0, 0), follow=1.0)
    c = Clip(g, 'ryu_unsatisfied', NOTE).spins()
    c.key(0, JAB_W, marker='anticipation').key(250, JAB_H, 'EASE_IN', lag=20, marker='blow 1').key(320, over(JAB_H, JAB_W, 0.10), 'EASE_OUT')
    c.key(380, CROSS_W, 'EASE_OUT').key(450, CROSS_H, 'EASE_IN', lag=22, marker='blow 2').key(520, over(CROSS_H, CROSS_W, 0.10), 'EASE_OUT')
    c.key(570, HOOK_W, 'EASE_OUT').key(650, HOOK_H, 'EASE_IN', lag=24, marker='blow 3').key(720, over(HOOK_H, HOOK_W, 0.12), 'EASE_OUT')
    c.key(850, turn, 'EASE_OUT', marker='turn').key(1000, ram, 'EASE_IN', lag=20, marker='the back').key(1080, ram2, 'EASE_OUT')
    c.key(1200, heave_w, 'EASE_OUT', marker='anticipation').key(1300, heave, 'EASE_IN', lag=24, marker='toss')
    c.key(1320, {'root': {'rot': [0.0, 0.0, 0.0], 'pos': [0.0, 1.2, 1.0]}}, 'STEP')
    c.key(1420, tweak(heave, rightArm=(-6, 0, 8), leftArm=(-6, 0, -8), root_pos=(0, -0.8, 0)), 'EASE_OUT', marker='follow-through')
    c.key(1800, REST, 'EASE_IN_OUT', marker='recovery').write()
    c = Clip(g, 'ryu_unsatisfied_whiff', NOTE)
    c.key(0, JAB_W).key(250, JAB_H, 'EASE_IN', lag=20, marker='blow').key(320, over(JAB_H, JAB_W, 0.10), 'EASE_OUT').key(600, REST, 'EASE_IN_OUT').write()

    # Second Helping. ryu_second_helping: he goes up and over them, the right fist hauled back over his head, and hangs
    # there until he is above them.
    crouch = R(root=(0, -3.6, 0), hips=(16, 10, 0), chest=(12, 6, 0), ra=HC(5.0, -6.0, 3.0), la=HC(-4.0, -4.0, 5.0), head=(-10, 0, 0))
    leap = R(root=(0, 3.0, 1.2), hips=(-10, 20, 0), chest=(-8, 16, 4), ra=HC(5.0, 7.0, -3.0, pole=(1, 0.4, -0.4)), la=A(-60, 0, 30, -40), rl=L(-30, 70, 8, 30), ll=L(10, 50, 8, 30),
             head=(-14, 0, 0))
    hang = tweak(leap, root_pos=(0, 1.0, 0.4), hips=(-6, 4, 0), chest=(-4, 4, 0), rightArm=(-8, 0, 0))
    c = Clip(g, 'ryu_second_helping', NOTE)
    c.key(0, crouch, 'EASE_OUT').key(150, leap, 'EASE_IN', lag=20, marker='leap').key(600, hang, 'EASE_IN_OUT').write()
    # ryu_second_helping_slam: the fist comes down on them from above (tick 2 after the drop) and he folds over it.
    slam = R(root=(0, -5.6, 2.6), hips=(38, -20, 0), chest=(26, -22, 0), ra=H(0.4, 3.0, 10.0), la=A(30, 0, 44, -30), rl=F(3.4, 0, -3.4, toe=50, yaw=26), ll=F(-3.2, 0, 3.8),
             head=(22, 0, 0))
    c = Clip(g, 'ryu_second_helping_slam', NOTE)
    c.key(0, hang).key(100, slam, 'EASE_IN', lag=20, marker='impact').key(170, tweak(slam, root_pos=(0, 0.8, 0), chest=(-5, 0, 0)), 'EASE_OUT')
    c.key(360, slam, 'EASE_IN_OUT').key(700, REST, 'EASE_IN_OUT', marker='recovery').write()
    # ryu_second_helping_air: already in the air with them: the fist drawn back, waiting.
    cock = R(root=(0, 0.8, 0), hips=(-4, 36, 0), chest=(6, 26, 3), ra=HC(6.8, -2.0, -2.5, pole=(1, 0.3, -0.6)), la=HC(-3.0, -3.0, 6.6), head=(2, 0, 0), **AIR)
    c = Clip(g, 'ryu_second_helping_air', NOTE)
    c.key(0, AIR0).key(200, cock, 'EASE_OUT', lag=20).key(500, tweak(cock, rightArm=(-4, 0, 2)), 'EASE_IN_OUT').write()
    # ryu_second_helping_punch: the punch lands at once (tick 2) and the discharge in it goes off a beat later (tick 9),
    # which rocks him back in the air.
    pch = R(root=(0, 1.0, 2.0), hips=(10, -32, 0), chest=(10, -36, -5), ra=HC(-1.0, -4.0, 14.5), la=HC(-4.8, -4.6, 2.0), rl=L(-70, 100, 8, 24), ll=L(12, 56, 8, 30), head=(4, 0, 0))
    kick = tweak(pch, root_pos=(0, 0.6, -1.4), hips=(-14, 6, 0), chest=(-12, 6, 0), head=(-10, 0, 0))
    c = Clip(g, 'ryu_second_helping_punch', NOTE)
    c.key(0, cock).key(100, pch, 'EASE_IN', lag=20, marker='impact').key(170, over(pch, cock, 0.10), 'EASE_OUT').key(420, pch, 'EASE_IN_OUT')
    c.key(460, kick, 'EASE_IN', marker='delayed impact').key(600, AIR0, 'EASE_IN_OUT').write()
    c = Clip(g, 'ryu_second_helping_whiff', NOTE)
    c.key(0, crouch).key(150, leap, 'EASE_IN', lag=20, marker='hop').key(320, slam, 'EASE_IN', lag=20, marker='slam').key(600, REST, 'EASE_IN_OUT').write()


def decadence():
    g = 'ryu'
    # "What are you after?": the floor slammed with both fists (tick 6), a leap straight up (9) and a lunge after them
    # (18); the lunge lands a blow, and a second one eight ticks on throws them away.
    raise_ = R(root=(0, 0.4, -0.6), hips=(-14, 0, 0), chest=(-18, 0, 0), ra=HC(1.8, 7.0, -2.0, pole=(1, 0.2, 0.5)), la=HC(-1.8, 7.0, -2.0, pole=(-1, 0.2, 0.5)),
               rl=BRACE_R, ll=BRACE_L, head=(-20, 0, 0))
    floor = R(root=(0, -6.6, 2.4), hips=(38, 0, 0), chest=(28, 0, 0), ra=H(1.6, 1.4, 9.5), la=H(-1.6, 1.4, 9.5), rl=F(3.6, 0, -3.8, toe=50, yaw=26), ll=F(-3.4, 0, 3.6, yaw=10),
              head=(24, 0, 0))
    c = Clip(g, 'ryu_after_slam', NOTE)
    c.key(0, lerp(REST, raise_, 0.5)).key(160, raise_, 'EASE_OUT', marker='anticipation').key(300, floor, 'EASE_IN', lag=26, marker='impact')
    c.key(380, tweak(floor, root_pos=(0, 0.8, 0), chest=(-5, 0, 0)), 'EASE_OUT').key(500, floor, 'EASE_IN_OUT').write()
    up = R(root=(0, 4.0, 0), hips=(-12, 0, 0), chest=(-10, 0, 0), ra=A(-160, 0, 20, -30), la=A(-150, 0, 24, -34), rl=L(-30, 60, 8, 30), ll=L(-20, 50, 8, 30), head=(-26, 0, 0))
    cock = R(root=(0, 4.4, 0.4), hips=(-8, 34, 0), chest=(-4, 26, 3), ra=HC(6.8, -1.0, -3.0, pole=(1, 0.3, -0.6)), la=A(-70, 0, 30, -40), rl=L(-26, 66, 8, 30), ll=L(0, 50, 8, 30),
             head=(-8, 0, 0))
    c = Clip(g, 'ryu_after_leap', NOTE)
    c.key(0, floor).key(200, up, 'EASE_OUT', lag=20, marker='leap').key(400, cock, 'EASE_IN_OUT', marker='cocked').write()
    lunge = R(root=(0, 1.0, 4.0), hips=(36, 30, 0), chest=(12, 24, 4), ra=HC(7.0, -2.0, -3.5, pole=(1, 0.3, -0.6)), la=A(-70, 0, 20, -30), rl=L(40, 36, 6, 40), ll=L(-56, 90, 4, 20),
              head=(-30, 0, 0))
    c = Clip(g, 'ryu_after_lunge', NOTE)
    c.key(0, cock).key(400, lunge, 'EASE_OUT', lag=20, marker='lunge').write()
    # ryu_after_trade: the lunge connects at once; the second blow at 400 ms is the one that sends them away.
    hit1 = R(root=(0, 0.6, 5.0), hips=(26, -32, 0), chest=(14, -36, -5), ra=H(-1.0, 17.0, 20.0), la=HC(-5.0, -4.8, 1.8), rl=L(40, 26, 6, 40), ll=L(-30, 76, 4, 24), head=(2, 0, 0))
    w2 = R(root=(0.8, -2.6, 2.6), hips=(10, 40, 0), chest=(8, 28, 4), ra=HC(6.8, -5.0, -3.0, pole=(1, 0.3, -0.6)), la=HC(-3.0, -3.4, 6.6), rl=F(3.6, 0, 0.6, toe=10, yaw=24),
           ll=F(-3.2, 0, 4.4), head=(2, 0, 0))
    h2 = R(root=(-1.2, -3.2, 7.2), hips=(28, -40, 0), chest=(18, -38, -6), ra=H(-1.6, 15.6, 22.0), la=HC(-6.0, -5.0, -2.0, pole=(-1, 0, -1)), rl=F(3.6, 1.6, -0.6, toe=66),
           ll=F(-3.2, 0, 7.6), head=(2, 0, 0))
    c = Clip(g, 'ryu_after_trade', NOTE)
    c.key(0, lunge).key(70, hit1, 'EASE_IN', lag=14, marker='impact').key(150, over(hit1, lunge, 0.08), 'EASE_OUT')
    c.key(280, w2, 'EASE_OUT', marker='anticipation').key(400, h2, 'EASE_IN', lag=30, marker='impact: launch').key(500, over(h2, w2, 0.12), 'EASE_OUT')
    c.key(900, REST, 'EASE_IN_OUT', marker='recovery').write()
    miss = tweak(hit1, root_pos=(0, -0.6, 0.6), chest=(4, -6, 0))
    c = Clip(g, 'ryu_after_miss', NOTE)
    c.key(0, lunge).key(120, miss, 'EASE_IN', lag=14, marker='whiff').key(300, R(root=(-0.6, -3.0, 5.0), hips=(18, -16, 0), chest=(12, -14, 0), rl=F(3.4, 0, 1.0, toe=20), ll=F(-3.2, 0, 6.0)),
          'EASE_OUT', marker='land').key(700, REST, 'EASE_IN_OUT').write()

    # "I had no idea...": an armoured wind-up (18 ticks), the grab punch, then seven blows traded fist against fist
    # every four ticks, and a shove.
    wind = R(root=(1.4, -4.2, -2.8), hips=(12, 48, 0), chest=(12, 32, 4), ra=HC(7.2, -7.0, -3.5, pole=(1, 0.2, -1)), la=H(-1.8, 16.5, 11.0), rl=F(3.6, 0, -4.6, toe=20, yaw=32),
             ll=F(-3.2, 0, 4.4), head=(6, 0, 0))
    c = Clip(g, 'ryu_no_idea_windup', NOTE, tremble=0.9)
    c.key(0, REST).key(300, lerp(REST, wind, 0.7), 'EASE_OUT', lag=30).key(800, wind, 'EASE_IN_OUT', marker='wound').write()
    grab = R(root=(-1.2, -2.6, 6.4), hips=(22, -38, 0), chest=(16, -36, -6), ra=H(-1.4, 16.4, 20.0), la=HC(-6.0, -5.0, -2.0, pole=(-1, 0, -1)), rl=F(3.6, 1.0, -2.0, toe=60),
             ll=F(-3.2, 0, 6.6), head=(2, 0, 0))
    hold = R(root=(-0.8, -3.0, 5.0), hips=(16, -26, 0), chest=(12, -24, -3), ra=H(-1.0, 15.6, 16.6), la=HC(-5.0, -4.8, 0.8), rl=F(3.4, 0, 0.4, toe=24), ll=F(-3.2, 0, 6.4), head=(2, 0, 0))
    c = Clip(g, 'ryu_no_idea_punch', NOTE)
    c.key(0, wind).key(70, grab, 'EASE_IN', lag=10, marker='impact').key(150, over(grab, wind, 0.08), 'EASE_OUT').key(600, hold, 'EASE_IN_OUT', marker='held').write()
    # ryu_no_idea_exchange: fist against fist. Each of his lands on the 4th tick; between them he takes one on the jaw
    # and gives it straight back.
    base = R(root=(-0.6, -2.6, 4.0), hips=(12, 0, 0), chest=(10, 0, 0), ra=HC(4.0, -4.0, 5.0), la=HC(-4.0, -4.0, 5.0), rl=F(3.4, 0, 0.4, toe=10), ll=F(-3.2, 0, 6.0), head=(2, 0, 0))
    r = R(root=(-1.2, -2.8, 5.4), hips=(14, -26, 0), chest=(14, -32, -4), ra=H(-0.8, 17.6, 18.8), la=HC(-4.6, -4.0, 2.6), rl=F(3.4, 0.6, 0.4, toe=50), ll=F(-3.2, 0, 6.0), head=(2, 0, 0))
    l = R(root=(0.0, -2.8, 5.4), hips=(14, 24, 0), chest=(14, 32, 4), la=H(0.8, 17.6, 18.8), ra=HC(4.6, -4.0, 2.6), rl=F(3.4, 0, 0.4, toe=10), ll=F(-3.2, 0, 6.0, toe=30, yaw=-20),
          head=(2, 0, 0))
    took = R(root=(-0.4, -2.2, 3.2), hips=(2, 0, 0), chest=(-8, 6, 2), ra=HC(4.4, -4.0, 4.6), la=HC(-4.4, -4.0, 4.6), rl=F(3.4, 0, 0.4), ll=F(-3.2, 0, 6.0), head=(-16, 16, 6), follow=1.0)
    c = Clip(g, 'ryu_no_idea_exchange', NOTE)
    c.key(0, hold).key(120, base, 'EASE_OUT')
    for i in range(7):
        t = 200 + i * 200
        c.key(t, r if i % 2 == 0 else l, 'EASE_IN', lag=14, marker='blow %d' % (i + 1))
        c.key(t + 100, mirror(took) if i % 2 == 0 else took, 'EASE_OUT', marker='takes one')
    shove = R(root=(-0.8, -2.4, 6.0), hips=(18, 0, 0), chest=(12, 0, 0), ra=H(2.0, 17.0, 18.0), la=H(-2.0, 17.0, 18.0), rl=F(3.4, 0.8, 1.0, toe=50), ll=F(-3.2, 0, 7.0), head=(0, 0, 0))
    c.key(1520, lerp(base, shove, 0.3), 'EASE_OUT').key(1600, shove, 'EASE_IN', lag=20, marker='shove').key(1700, over(shove, base, 0.08), 'EASE_OUT').write()

    # "This is what dessert is like!": the armoured run, the kick, the swing that grabs, four blows traded and a kick away.
    r1 = R(root=(0, -2.0, 0), hips=(30, 10, 0), chest=(10, -16, 0), ra=A(46, 0, 10, -84), la=A(-56, 0, 8, -88), rl=L(-60, 40, 3, 10), ll=L(40, 76, 3, 20), head=(-12, 0, 0))
    r2 = R(root=(0, -2.0, 0), hips=(30, -10, 0), chest=(10, 16, 0), la=A(46, 0, 10, -84), ra=A(-56, 0, 8, -88), ll=L(-60, 40, 3, 10), rl=L(40, 76, 3, 20), head=(-12, 0, 0))
    c = Clip(g, 'ryu_dessert_run', NOTE)
    c.key(0, r1).key(100, tweak(lerp(r1, r2, 0.5), root_pos=(0, 0.8, 0)), 'LINEAR').key(200, r2, 'LINEAR').key(300, tweak(lerp(r1, r2, 0.5), root_pos=(0, 0.8, 0)), 'LINEAR')
    c.key(400, r1, 'LINEAR')
    c.d.update(loop=True, loopStart=0, loopEnd=400)
    c.duration = 400
    c.write()
    kw = R(root=(0.4, -1.6, 0.6), hips=(-12, 8, 0), chest=(14, 4, 0), ra=HC(6.5, -5.0, 2.0), la=HC(-6.5, -4.5, 3.0), rl=L(-96, 112, 8, 0), ll=F(-3.2, 0, 1.6), head=(2, 0, 0))
    kh = R(root=(0.4, -1.8, 3.4), hips=(-30, 6, 0), chest=(24, 4, 0), ra=HC(7.5, -5.0, -2.0), la=HC(-7.0, -4.5, -1.0), rl=L(-68, 4, 6, -26), ll=F(-3.2, 0, 1.6, toe=26), head=(4, 0, 0))
    c = Clip(g, 'ryu_dessert_kick', NOTE)
    c.key(0, lerp(r1, kw, 0.6)).key(60, kh, 'EASE_IN', lag=10, marker='impact').key(140, over(kh, kw, 0.08), 'EASE_OUT').key(300, kw, 'EASE_IN_OUT')
    c.key(500, REST, 'EASE_IN_OUT').write()
    c = Clip(g, 'ryu_dessert_miss', NOTE)
    c.key(0, r1).key(90, kw, 'EASE_OUT', marker='chamber').key(150, kh, 'EASE_IN', lag=10, marker='whiff').key(260, over(kh, kw, 0.08), 'EASE_OUT')
    c.key(600, REST, 'EASE_IN_OUT').write()
    # ryu_dessert_swing: a backhand across them that ends with the hand in their collar.
    sw = R(root=(0.8, -2.0, 0.4), hips=(6, -34, 0), chest=(4, -30, -4), ra=HC(-4.0, -3.0, 6.0, pole=(1, 0.8, 0)), la=HC(-4.0, -4.0, 5.0), head=(0, 0, 0))
    sh = R(root=(-0.8, -2.2, 4.0), hips=(12, 34, 0), chest=(10, 40, 4), ra=H(1.0, 18.0, 17.0, pole=(1, 0.4, 0), roll=40), la=HC(-4.6, -4.0, 2.6), rl=F(3.4, 0.6, -1.4, toe=50),
           ll=F(-3.2, 0, 4.0, yaw=-20), head=(0, 0, 0))
    c = Clip(g, 'ryu_dessert_swing', NOTE)
    c.key(0, sw).key(60, sh, 'EASE_IN', lag=14, marker='impact').key(140, over(sh, sw, 0.10), 'EASE_OUT').key(500, lerp(sh, REST, 0.6), 'EASE_IN_OUT').write()
    # ryu_dessert_flurry: four blows of his every six ticks, one of theirs taken between each, then the kick away.
    base = R(root=(-0.6, -2.6, 4.0), hips=(12, 0, 0), chest=(10, 0, 0), ra=HC(4.0, -4.0, 5.0), la=HC(-4.0, -4.0, 5.0), rl=F(3.4, 0, 0.4, toe=10), ll=F(-3.2, 0, 6.0), head=(2, 0, 0))
    c = Clip(g, 'ryu_dessert_flurry', NOTE)
    c.key(0, sh).key(140, base, 'EASE_OUT')
    for i in range(4):
        t = 300 + i * 300
        c.key(t, r if i % 2 == 0 else l, 'EASE_IN', lag=16, marker='blow %d' % (i + 1))
        c.key(t + 90, over(r if i % 2 == 0 else l, base, 0.08), 'EASE_OUT')
        c.key(t + 190, mirror(took) if i % 2 == 0 else took, 'EASE_OUT', marker='takes one')
    pk_w = R(root=(0.2, -2.2, 3.0), hips=(-10, 10, 0), chest=(12, 6, 0), ra=HC(6.0, -5.0, 2.0), la=HC(-6.0, -4.5, 3.0), rl=L(-90, 110, 8, 0), ll=F(-3.2, 0, 5.4), head=(2, 0, 0))
    pk_h = R(root=(0.2, -2.4, 5.6), hips=(-30, 6, 0), chest=(24, 4, 0), ra=HC(7.0, -5.0, -2.0), la=HC(-6.5, -4.5, -1.0), rl=L(-70, 4, 6, -26), ll=F(-3.2, 0, 5.4, toe=26), head=(4, 0, 0))
    c.key(1330, pk_w, 'EASE_OUT', marker='chamber').key(1400, pk_h, 'EASE_IN', lag=10, marker='kick').key(1480, over(pk_h, pk_w, 0.08), 'EASE_OUT')
    c.key(1800, REST, 'EASE_IN_OUT', marker='recovery').write()

    # "You weren't invited.": the fist wound back behind him, the whole body turned away from them and sunk; the lunge;
    # and a hook that comes all the way round and throws them to his left.
    wind = P(root=(1.6, -4.0, -2.4), yaw=40, hips=(10, 30, -4), chest=(6, 26, -6), ra=HC(7.6, -1.0, -5.0, pole=(1, 0.3, -0.6)), la=HC(-4.0, -3.0, 7.0),
             rl=F(3.8, 4.0, -4.0, yaw=40, rel=True), ll=F(-3.2, 4.0, 3.6, rel=True), head=(0, -50, 0), follow=1.0)
    wind2 = P(root=(1.8, -4.8, -2.8), yaw=40, hips=(12, 36, -4), chest=(8, 32, -6), ra=HC(7.8, -0.6, -5.4, pole=(1, 0.3, -0.6)), la=HC(-4.0, -3.0, 7.0),
              rl=F(3.8, 4.8, -4.0, yaw=40, rel=True), ll=F(-3.2, 4.8, 3.6, rel=True), head=(0, -50, 0), follow=1.0)
    c = Clip(g, 'ryu_invited_windup', NOTE, tremble=0.8)
    c.key(0, REST).key(500, wind, 'EASE_OUT', lag=40, marker='wound').key(900, wind2, 'EASE_IN_OUT').write()
    lunge = P(root=(0.8, -3.2, 5.0), yaw=30, hips=(30, 26, -4), chest=(10, 24, -6), ra=HC(7.6, -1.0, -5.0, pole=(1, 0.3, -0.6)), la=A(-60, 0, 20, -40), rl=L(30, 36, 6, 40),
              ll=L(-56, 80, 4, 20), head=(-14, -40, 0), follow=1.0)
    c = Clip(g, 'ryu_invited_lunge', NOTE)
    c.key(0, wind).key(300, lunge, 'EASE_OUT', lag=20, marker='lunge').write()
    hook = P(root=(-1.6, -3.2, 6.6), yaw=-50, hips=(20, -40, 8), chest=(14, -40, 10), ra=HC(-4.0, -3.4, 7.4, pole=(1, 0.8, 0)), la=A(50, 0, 40, -30),
             rl=F(3.6, 4.8, -1.4, toe=66, rel=True), ll=F(-3.2, 3.2, 5.6, rel=True), head=(0, 30, 0), follow=1.0)
    through = P(root=(-2.0, -3.0, 7.0), yaw=-80, hips=(18, -34, 8), chest=(12, -36, 10), ra=HC(-5.0, -4.0, 6.4, pole=(1, 0.8, 0)), la=A(50, 0, 44, -30),
                rl=F(3.6, 4.2, -1.0, toe=60, rel=True), ll=F(-3.2, 3.0, 5.6, rel=True), head=(0, 40, 0), follow=1.0)
    settle = P(root=(-1.2, -3.0, 6.0), yaw=-60, hips=(16, -20, 4), chest=(12, -20, 4), ra=HC(-1.0, -7.0, 5.0), la=HC(-4.0, -4.0, 5.0), rl=F(3.4, 3.0, 1.0, toe=20, rel=True),
               ll=F(-3.2, 3.0, 5.6, rel=True), head=(0, 40, 0), follow=1.0)
    c = Clip(g, 'ryu_invited_punch', NOTE).spins()
    c.key(0, lunge).key(70, hook, 'EASE_IN', lag=20, marker='impact').key(160, through, 'EASE_OUT', marker='follow-through').key(450, settle, 'EASE_IN_OUT', marker='recovery')
    c.key(800, REST, 'EASE_IN_OUT').write()


if __name__ == '__main__':
    melee()
    cannon_moves()
    eld()
    ground_kit()
    decadence()
    print('wrote', len(WRITTEN), 'clips:', ' '.join(WRITTEN))
