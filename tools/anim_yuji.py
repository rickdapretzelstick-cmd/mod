#!/usr/bin/env python3
"""
Authors Yuji Itadori's clips, and Sukuna's once he takes over (assets/jjk/animations/yuji), with tools/animkit.py.

    python3 tools/anim_yuji.py

Yuji fights out of the boxer's guard in tools/anim_author.py (his M1s are that set): explosive, low, always driving
forward off the back leg, every technique a punch or a kick with his whole body in it. Sukuna in the same body stands
up straight and stops trying: weight on his heels, chin up, arms hanging open, and the slashes are small contemptuous
gestures of a hand, a wrist or two fingers. Only Cleave, the World Cutting Slash and Open make him move.
Timings are the abilities' own (the contact times of the clips these replace).
"""
from animkit import *
from anim_author import Y, STANCE, FL, FR

NOTE = 'tools/anim_yuji.py'
AIRL = dict(rl=L(-34, 74, 5, 20), ll=L(-58, 96, 4, 24))


def vessel():
    g = 'yuji'
    # --- Divergent Fist: coiled right down over the back leg with the fist cocked at the hip and the lead hand out
    # measuring; then the lunge, the arm thrown straight through and the back leg driving until it leaves the floor.
    coil = Y(root=(1.4, -4.4, -2.6), hips=(14, 48, 0), chest=(14, 30, 4), ra=HC(6.8, -8.0, -3.0, pole=(1, 0.2, -1)), la=H(-1.6, 16.5, 11.5),
             rl=F(3.0, 0, -4.4, toe=26, yaw=30), ll=F(-2.6, 0, 4.2), head=(6, 0, 0))
    deep = tweak(coil, hips=(3, 6, 0), chest=(3, 4, 0), root_pos=(0.3, -0.8, -0.5))
    df = Y(root=(-1.4, -3.4, 7.4), hips=(26, -44, 0), chest=(18, -40, -6), ra=H(-1.8, 15.6, 21.6), la=HC(-6.2, -4.6, -2.5, pole=(-1, 0, -1)),
           rl=F(3.2, 1.6, -2.4, toe=68), ll=F(-2.6, 0, 6.0), head=(0, 0, 0))
    rec = Y(root=(-1.0, -3.0, 5.6), hips=(18, -22, 0), chest=(12, -18, -2), ra=H(-0.8, 13.5, 15.0), la=HC(-4.8, -4.4, 0.6), rl=F(3.0, 0, 1.0, toe=26), ll=F(-2.6, 0, 6.0))
    c = Clip(g, 'divergent_windup', NOTE, tremble=0.6)
    c.key(0, STANCE).key(220, coil, 'EASE_OUT', lag=40, marker='coil').breathe(450, deep, period=1000, amp=0.9)
    c.write()
    c = Clip(g, 'divergent_punch', NOTE)
    c.key(0, deep)
    c.hit(60, deep, df, lag=30, k=0.10, carry=110)
    c.key(380, rec, 'EASE_OUT', marker='recovery').key(700, STANCE, 'EASE_IN_OUT').write()
    # Black Flash: the same fist but nothing held back; it lands a hair earlier and the follow-through takes him
    # right off his feet before he drops back into his stance.
    bf = tweak(df, root_pos=(0, 0.6, 1.2), chest=(4, -6, 0), hips=(4, -4, 0))
    c = Clip(g, 'black_flash_punch', NOTE)
    c.key(0, deep)
    c.hit(55, deep, bf, lag=28, k=0.14, carry=120)
    c.key(400, rec, 'EASE_IN', marker='land').key(700, STANCE, 'EASE_IN_OUT').write()
    c = Clip(g, 'black_flash', NOTE)
    c.key(0, STANCE).key(200, deep, 'EASE_OUT', lag=40, marker='anticipation')
    c.hit(260, deep, bf, lag=30, k=0.14, carry=120)
    c.key(560, rec, 'EASE_IN', marker='land').key(800, STANCE, 'EASE_IN_OUT').write()
    # black_flash_uppercut: from a squat, both legs and the whole back thrown into a fist that finishes above his head.
    uw = Y(root=(0.6, -5.0, -0.4), hips=(20, 30, 0), chest=(18, 18, 4), ra=HC(5.6, -11.0, 2.0, pole=(1, 0, -1)), la=HC(-3.0, -2.4, 6.0), head=(10, 0, 0))
    uh = Y(root=(-0.4, 3.0, 2.2), hips=(-12, -24, 0), chest=(-12, -24, -4), ra=H(1.4, 31.0, 6.6, pole=(0.4, -0.2, 1)), la=HC(-4.8, -6.4, 0.6),
           rl=L(10, 10, 4, 36), ll=L(-34, 54, 4, 20), head=(-18, 0, 0))
    c = Clip(g, 'black_flash_uppercut', NOTE)
    c.key(0, uw).key(80, uh, 'EASE_IN', lag=30, marker='impact').key(300, tweak(uh, root_pos=(0, 0.8, 0), rightArm=(8, 0, 0)), 'EASE_OUT')
    c.key(520, Y(root=(0, -2.8, 0.8), hips=(12, 6, 0), chest=(10, 4, 0)), 'EASE_IN', marker='land').key(700, STANCE, 'EASE_OUT').write()
    # black_flash_dropkick: two steps' worth of jump, both knees to his chest, and both feet through them; he lands
    # on his hands and rolls to a crouch.
    hop = Y(root=(0, 2.4, 0.6), hips=(-10, 0, 0), chest=(28, 0, 0), ra=HC(5.0, -3.0, 5.0), la=HC(-5.0, -3.0, 5.0), rl=L(-100, 122, 6, 0), ll=L(-96, 118, 6, 0), head=(6, 0, 0))
    dk = Y(root=(0, 3.4, 4.0), hips=(-66, 0, 0), chest=(24, 0, 0), ra=A(40, 0, 44, -20), la=A(40, 0, 44, -20), rl=L(-30, 0, 4, -30), ll=L(-24, 4, 4, -30), head=(18, 0, 0))
    crouch = Y(root=(0, -5.4, 2.6), hips=(26, 10, 0), chest=(14, 6, 0), ra=H(5.0, 1.0, 6.0), la=HC(-4.0, -7.0, 5.0), rl=F(3.0, 0, -1.4, toe=40), ll=F(-2.8, 0, 4.4), head=(-6, 0, 0))
    c = Clip(g, 'black_flash_dropkick', NOTE)
    c.key(0, STANCE).key(110, hop, 'EASE_OUT', marker='anticipation').key(170, dk, 'EASE_IN', marker='impact')
    c.key(300, tweak(dk, rightThigh=(-6, 0, 0), leftThigh=(-6, 0, 0), root_pos=(0, -0.6, 0.6)), 'EASE_OUT').key(520, crouch, 'EASE_IN', marker='land')
    c.key(800, lerp(crouch, STANCE, 0.8), 'EASE_IN_OUT').write()

    # --- Cursed Strikes. cursed_strikes_ready: one hand reached out at them, then he drops under it into a sprinter's
    # crouch, eyes up.
    reach = Y(root=(0, -1.2, 0.4), hips=(4, 16, 0), chest=(4, 14, 0), ra=H(1.6, 19.5, 12.0), la=HC(-4.0, -6.0, 3.0), head=(-2, 0, 0))
    ready = Y(root=(0, -4.6, 0.8), hips=(26, 16, 0), chest=(14, 10, 0), ra=HC(4.4, -5.0, 3.0), la=HC(-3.6, -3.4, 6.0), rl=F(2.8, 0, -4.0, toe=36), ll=F(-2.6, 0, 3.4), head=(-10, 0, 0))
    c = Clip(g, 'cursed_strikes_ready', NOTE)
    c.key(0, STANCE).key(200, reach, 'EASE_OUT', marker='reach').key(420, ready, 'EASE_IN', lag=30).key(450, ready).write()
    # cursed_strikes_slide: the dash. Pitched flat with the right fist leading and everything else streaming behind.
    dash = Y(root=(0, -3.4, 3.0), hips=(46, -14, 0), chest=(10, -14, 0), ra=H(0.6, 13.0, 19.0), la=A(50, 0, 20, -40), rl=L(36, 40, 6, 40), ll=L(-60, 96, 4, 10), head=(-36, 0, 0))
    c = Clip(g, 'cursed_strikes_slide', NOTE)
    c.key(0, ready).key(120, dash, 'EASE_OUT', lag=20).write()
    # cursed_strikes_flurry: eight punches in a second and a half (one every 180 ms), crowding forward, shoulders
    # rolling, each fist back at his chin as the other leaves.
    base = Y(root=(0, -2.2, 1.6), hips=(12, 0, 0), chest=(10, 0, 0), head=(2, 0, 0))
    r = Y(root=(-0.5, -2.4, 3.0), hips=(12, -24, 0), chest=(12, -30, -3), ra=H(-0.4, 18.0, 17.6), la=HC(-3.8, -2.4, 3.4), rl=F(2.6, 0, -2.6, toe=44, yaw=-30), head=(2, 0, 0))
    l = Y(root=(0.5, -2.4, 3.0), hips=(12, 20, 0), chest=(12, 32, 3), la=H(0.4, 18.0, 17.6), ra=HC(3.8, -2.4, 3.4), head=(2, 0, 0))
    body = Y(root=(-0.6, -3.4, 2.8), hips=(16, -26, 3), chest=(14, -30, 4), ra=H(0.2, 13.5, 15.6, pole=(0.6, -1, 0)), la=HC(-3.8, -2.4, 3.6), rl=F(2.6, 0, -2.6, toe=40, yaw=-30),
             head=(6, 0, 0))
    c = Clip(g, 'cursed_strikes_flurry', NOTE)
    c.key(0, ready).key(50, base, 'EASE_OUT')
    seq = [r, l, r, l, body, l, r, l]
    t = 110
    for i, p in enumerate(seq):
        c.key(t, p, 'EASE_IN', lag=14, marker='impact')
        c.key(t + 60, lerp(p, base, 0.5), 'EASE_OUT')
        t += 180
    c.key(1500, lerp(base, STANCE, 0.5), 'EASE_IN_OUT').write()
    # cursed_strikes_final: the last punch of the rush, a right hand wound back and driven through (contact 200 ms).
    fw = Y(root=(1.0, -3.4, -2.0), hips=(12, 40, 0), chest=(12, 26, 4), ra=HC(6.0, -1.0, -1.5, pole=(1, 0.2, -1)), la=HC(-2.0, -1.0, 8.6), head=(8, 0, 0))
    fh = Y(root=(-1.0, -3.0, 6.2), hips=(22, -34, 0), chest=(16, -34, -6), ra=H(-1.6, 16.6, 19.6), la=HC(-5.6, -5.0, -1.0, pole=(-1, 0, -1)), rl=F(3.2, 2.0, -1.4, toe=62),
           ll=F(-2.4, 0, 6.4), head=(2, 0, 0))
    c = Clip(g, 'cursed_strikes_final', NOTE)
    c.key(0, base).key(120, fw, 'EASE_OUT', marker='anticipation')
    c.hit(200, fw, fh, lag=34, k=0.10, carry=80)
    c.key(420, rec, 'EASE_OUT', marker='recovery').key(650, STANCE, 'EASE_IN_OUT').write()
    # cursed_strikes_kick: the calf kick. A short chop of the shin across the back of their lead leg, hips whipped over.
    kw = Y(root=(-0.8, -1.4, 0), hips=(2, 30, 0), chest=(6, 10, 0), rl=L(-20, 90, 30, 10), ll=F(-2.4, 0, 2.6, yaw=30), ra=HC(5.0, -4.0, 2.0), la=HC(-4.0, -2.0, 5.0))
    kh = Y(root=(-1.2, -1.6, 0.8), hips=(-2, -50, 10), chest=(8, 34, -6), rl=L(-52, 10, 14, -20), ll=F(-2.4, 0, 2.6, toe=14, yaw=70), ra=A(30, 0, 40, -30), la=HC(-3.6, -2.0, 4.6))
    c = Clip(g, 'cursed_strikes_kick', NOTE)
    c.key(0, STANCE).key(110, kw, 'EASE_OUT', marker='anticipation')
    c.hit(170, kw, kh, lag=22, k=0.12, carry=60)
    c.key(340, lerp(kh, kw, 0.6), 'EASE_IN_OUT').key(600, STANCE, 'EASE_IN_OUT').write()
    # cursed_strikes_spin: a spinning back kick; he turns his back on them, looks over the shoulder, and the heel
    # comes straight out of the turn.
    sw = Y(root=(0, -1.8, 0), hips=(6, 70, 0), chest=(8, 40, 0), rl=F(2.6, 0, -1.0, toe=30), ll=F(-2.4, 0, 2.6, yaw=-40), ra=HC(4.0, -4.0, 3.0), la=HC(-4.0, -3.0, 4.0), head=(4, -20, 0))
    sh = Y(root=(-0.6, -1.4, 1.0), hips=(34, 150, 0), chest=(10, 20, 0), rl=L(-118, 4, 6, 20), ll=F(-2.4, 0, 2.6, toe=20, yaw=-110), ra=A(-30, 0, 50, -40), la=A(-50, 0, 30, -60),
           head=(0, -60, 0), follow=0.9)
    c = Clip(g, 'cursed_strikes_spin', NOTE)
    c.key(0, STANCE).key(150, sw, 'EASE_OUT', marker='anticipation').key(220, sh, 'EASE_IN', lag=20, marker='impact')
    c.key(300, tweak(sh, rightThigh=(-6, 0, 0)), 'EASE_OUT').key(460, lerp(sh, sw, 0.7), 'EASE_IN_OUT').key(700, STANCE, 'EASE_IN_OUT').write()
    # cursed_strikes_floor: a fist put into the ground between his feet to throw them up.
    flw = Y(root=(0.4, -2.6, 0), hips=(10, 30, 0), chest=(4, 24, 0), ra=HC(6.0, 4.0, -1.0, pole=(1, 0.2, -0.4)), la=HC(-3.0, -3.0, 6.0), head=(16, 0, 0))
    flh = Y(root=(0, -6.6, 2.0), hips=(34, -20, 0), chest=(24, -20, 0), ra=H(0.6, 1.5, 9.0), la=A(40, 0, 40, -30), rl=F(3.0, 0, -3.6, toe=50), ll=F(-2.8, 0, 3.6), head=(26, 0, 0))
    c = Clip(g, 'cursed_strikes_floor', NOTE)
    c.key(0, STANCE).key(140, flw, 'EASE_OUT', marker='anticipation')
    c.key(210, flh, 'EASE_IN', lag=20, marker='impact').key(270, tweak(flh, root_pos=(0, 0.7, 0), chest=(-4, 0, 0)), 'EASE_OUT').key(330, flh, 'EASE_IN_OUT')
    c.key(500, lerp(flh, STANCE, 0.7), 'EASE_IN_OUT').write()
    # cursed_strikes_blocked: the fist stops dead on a guard and the shock runs back up his arm; he gives a step.
    c = Clip(g, 'cursed_strikes_blocked', NOTE)
    rc = Y(root=(0.4, -1.8, -2.4), hips=(-6, 22, 0), chest=(-10, 20, 0), ra=HC(6.0, -3.0, 2.0), la=HC(-4.0, -3.0, 5.0), rl=F(2.8, 0, -5.0), ll=F(-2.4, 0, 1.6, toe=20), head=(-8, 0, 0))
    c.key(0, r).key(110, rc, 'EASE_OUT', eases={'chest': 'OVERSHOOT'}, marker='recoil').key(400, STANCE, 'EASE_IN_OUT').write()
    # Airborne: cursed_strikes_hop (up, knees tucked, arms thrown high), cursed_strikes_dropkick (the dive: one leg
    # speared down at them, the other folded), cursed_strikes_land (through his knees to one hand on the floor).
    hp = Y(root=(0, 2.0, 0), hips=(-8, 0, 0), chest=(-6, 0, 0), ra=A(-150, 0, 24, -40), la=A(-140, 0, 26, -44), rl=L(-64, 100, 6, 20), ll=L(-44, 90, 6, 20), head=(10, 0, 0))
    dv = Y(root=(0, 1.0, 2.6), hips=(-44, -10, 0), chest=(20, 0, 0), ra=A(30, 0, 44, -30), la=A(-50, 0, 40, -50), rl=L(-34, 2, 4, -30), ll=L(-70, 120, 6, 10), head=(26, 0, 0))
    ld = Y(root=(0, -5.8, 1.4), hips=(28, 12, 0), chest=(14, 8, 0), ra=H(4.6, 1.0, 6.0), la=HC(-5.0, -5.0, 3.0), rl=F(3.0, 0, -2.6, toe=40), ll=F(-2.8, 0, 3.8), head=(-8, 0, 0))
    c = Clip(g, 'cursed_strikes_hop', NOTE)
    c.key(0, ready).key(300, hp, 'EASE_OUT').write()
    c = Clip(g, 'cursed_strikes_dropkick', NOTE)
    c.key(0, hp).key(200, dv, 'EASE_OUT').write()
    c = Clip(g, 'cursed_strikes_land', NOTE)
    c.key(0, dv).key(90, ld, 'EASE_IN', marker='land').key(170, tweak(ld, root_pos=(0, -0.7, 0)), 'EASE_OUT').key(600, lerp(ld, STANCE, 0.85), 'EASE_IN_OUT').write()

    # --- Manji Kick. manji_stance: the counter guard, open hands low in front of his hips, weight sunk and centred,
    # waiting for it.
    ms = Y(root=(0, -3.0, -0.4), hips=(10, 10, 0), chest=(10, 6, 0), ra=HC(3.0, -7.0, 6.0, wrist=-30), la=HC(-3.0, -6.0, 7.0, wrist=-30), rl=F(3.2, 0, -2.6, yaw=24), ll=F(-3.0, 0, 2.6, yaw=10),
           head=(2, 0, 0))
    c = Clip(g, 'manji_stance', NOTE)
    c.key(0, STANCE).breathe(150, ms, period=1200, amp=0.5)
    c.write()
    # manji_dodge: under it. Dropped and slipped off the line to his left, one hand to the floor.
    duck = Y(root=(-2.4, -6.4, 0.4), hips=(26, 30, 14), chest=(16, 10, 10), ra=HC(5.0, -8.0, 3.0), la=H(-6.4, 1.2, 3.0), rl=F(3.6, 0, -2.6, toe=20), ll=F(-3.4, 0, 2.6, yaw=30), head=(-10, 10, 0))
    c = Clip(g, 'manji_dodge', NOTE)
    c.key(0, ms).key(110, duck, 'EASE_OUT', lag=10, marker='duck').key(300, tweak(duck, root_pos=(0, 0.5, 0)), 'EASE_IN_OUT').write()
    # manji_kick: off that hand the right leg whips round and up, body thrown sideways, heel past vertical; he hangs on
    # it a moment before the leg comes down.
    cham = Y(root=(-2.0, -3.6, 0.6), hips=(6, -40, 30), chest=(6, 30, -4), ra=A(-30, 0, 60, -30), la=H(-6.6, 3.0, 2.0), rl=L(-80, 116, 30, 10), ll=F(-3.0, 0, 2.0, yaw=50), head=(-4, 0, 0))
    mk = Y(root=(-2.4, -1.4, 1.4), hips=(-4, -70, 58), chest=(0, 40, -14), ra=A(20, 0, 70, -20), la=A(-30, 0, 80, -20), rl=L(-20, 0, 96, 30), ll=F(-3.0, 0, 2.0, toe=20, yaw=80), head=(-8, 0, 0))
    c = Clip(g, 'manji_kick', NOTE)
    c.key(0, duck).key(140, cham, 'EASE_OUT', marker='chamber').key(210, mk, 'EASE_IN', lag=24, marker='impact')
    c.key(400, tweak(mk, rightThigh=(0, 0, 6), hips=(0, -6, 4)), 'EASE_OUT', marker='hold').key(540, cham, 'EASE_IN_OUT').key(700, STANCE, 'EASE_IN_OUT').write()
    c = Clip(g, 'manji_recover', NOTE)
    c.key(0, tweak(mk, rightThigh=(0, 0, 6))).key(180, cham, 'EASE_IN_OUT').key(400, STANCE, 'EASE_IN_OUT').write()
    # manji_face_kick: the same chamber, but the foot goes straight up under their chin.
    fk = Y(root=(-0.6, -0.6, 1.4), hips=(-24, 0, 4), chest=(18, 0, 0), ra=A(20, 0, 50, -20), la=A(-10, 0, 60, -20), rl=L(-150, 2, 4, -30), ll=F(-2.8, 0, 1.6, toe=20), head=(-10, 0, 0))
    c = Clip(g, 'manji_face_kick', NOTE)
    c.key(0, duck).key(100, lerp(cham, fk, 0.3), 'EASE_OUT', marker='chamber').key(160, fk, 'EASE_IN', marker='impact').key(400, tweak(fk, rightThigh=(8, 0, 0)), 'EASE_OUT').write()
    # manji_swoop: a bullet went past his ear. He is already going at whoever fired it, body level with the floor.
    swp = Y(root=(0, -4.4, 3.4), hips=(48, -16, 0), chest=(8, -10, 8), ra=A(56, 0, 24, -20), la=A(50, 0, 24, -24), rl=L(40, 36, 6, 40), ll=L(-64, 96, 4, 10), head=(-40, 0, 0))
    c = Clip(g, 'manji_swoop', NOTE)
    c.key(0, ms).key(180, swp, 'EASE_OUT', lag=30).breathe(400, swp, period=600, amp=0.5)
    c.write()
    # manji_grapple_spin: legs scissored round their neck, he turns them once in the air and carries them up over him.
    grab = Y(root=(0, -1.6, 3.0), hips=(14, -10, 0), chest=(10, -8, 0), ra=H(1.6, 18.0, 13.0), la=H(-1.6, 18.0, 13.0), head=(0, 0, 0))
    spin = P(root=(0, 0.6, 2.0), yaw=-180, hips=(6, -20, 10), chest=(6, -20, 0), ra=HC(3.0, -2.0, 7.0), la=HC(-3.0, -2.0, 7.0), rl=L(-30, 60, 14, 10), ll=L(10, 30, 14, 10), head=(0, 0, 0), follow=1.0)
    upp = P(root=(0, 0.4, 1.0), yaw=-360, hips=(-20, 0, 0), chest=(-20, 0, 0), ra=A(-172, 0, 14, -30), la=A(-172, 0, 14, -30), rl=F(2.8, 0, -2.0, toe=30, rel=True), ll=F(-2.8, 0, 2.0, toe=20, rel=True),
            head=(-24, 0, 0), follow=1.0)
    c = Clip(g, 'manji_grapple_spin', NOTE).spins()
    c.key(0, swp).key(150, grab, 'EASE_OUT', marker='grab').key(400, spin, 'LINEAR').key(600, upp, 'EASE_OUT', marker='lift').write()
    # manji_slam: and down. He folds at the waist and puts them into the floor with both arms.
    upp0 = P(root=(0, 0.4, 1.0), hips=(-20, 0, 0), chest=(-20, 0, 0), ra=A(-172, 0, 14, -30), la=A(-172, 0, 14, -30), rl=F(2.8, 0, -2.0, toe=30), ll=F(-2.8, 0, 2.0, toe=20), head=(-24, 0, 0), follow=1.0)
    dn = Y(root=(0, -5.6, 3.0), hips=(36, 0, 0), chest=(26, 0, 0), ra=H(1.6, 3.0, 11.0), la=H(-1.6, 3.0, 11.0), rl=F(3.0, 0, -2.4, toe=40), ll=F(-3.0, 0, 3.4), head=(20, 0, 0))
    c = Clip(g, 'manji_slam', NOTE)
    c.key(0, upp0).key(140, dn, 'EASE_IN', lag=20, marker='impact').key(220, tweak(dn, root_pos=(0, 0.8, 0), chest=(-5, 0, 0)), 'EASE_OUT').key(300, dn, 'EASE_IN_OUT')
    c.key(700, STANCE, 'EASE_IN_OUT').write()

    # --- Crushing Blow. crushing_blow_charge: the right hand held open and low beside him, fingers hooked, filling up;
    # he leans over it, the left arm across to brace the wrist.
    cb = Y(root=(0.8, -3.6, -1.0), hips=(14, 30, 0), chest=(16, 20, 4), ra=H(6.4, 8.0, 3.0, pole=(1, 0, -1), wrist=-30), la=H(4.0, 11.0, 5.0, pole=(-1, 0, 0)), head=(8, 14, 0))
    c = Clip(g, 'crushing_blow_charge', NOTE, tremble=1.2)
    c.key(0, STANCE).key(240, cb, 'EASE_OUT', lag=40).breathe(350, cb, period=800, amp=1.2)
    c.write()
    cba = Y(root=(0, 0.8, 0), hips=(-6, 30, 0), chest=(16, 20, 4), ra=HC(7.0, -9.0, 1.0, pole=(1, 0, -1), wrist=-30), la=HC(2.0, -8.0, 4.0, pole=(-1, 0, 0)), head=(8, 0, 0), **AIRL)
    c = Clip(g, 'crushing_blow_air', NOTE, tremble=1.2)
    c.key(0, lerp(STANCE, cba, 0.5)).breathe(350, cba, period=800, amp=1.2)
    c.write()
    # crushing_blow_dash: across the air at them, the loaded hand dragged behind, the other reaching.
    cd = Y(root=(0, 0.6, 2.6), hips=(40, 20, 0), chest=(10, 20, 0), ra=A(60, 0, 24, -30, -20), la=H(-1.6, 18.0, 17.0), rl=L(40, 40, 4, 40), ll=L(-50, 100, 4, 20), head=(-34, 0, 0))
    c = Clip(g, 'crushing_blow_dash', NOTE)
    c.key(0, cba).key(200, cd, 'EASE_OUT', lag=20).write()
    # crushing_blow_release: nobody there, so the floor gets it. The palm is driven down in front of his feet.
    gp = Y(root=(0, -6.4, 2.2), hips=(36, -24, 0), chest=(24, -22, 0), ra=H(1.0, 1.4, 9.5), la=A(40, 0, 44, -30), rl=F(3.0, 0, -3.6, toe=50), ll=F(-2.8, 0, 3.6), head=(24, 0, 0))
    up = Y(root=(0.6, -2.0, -0.4), hips=(4, 34, 0), chest=(-4, 26, 0), ra=HC(6.0, 5.0, -1.0, pole=(1, 0.2, -0.4), wrist=-30), la=HC(-3.0, -3.0, 6.0), head=(14, 0, 0))
    c = Clip(g, 'crushing_blow_release', NOTE)
    c.key(0, cb).key(80, up, 'EASE_OUT', marker='anticipation').key(150, gp, 'EASE_IN', lag=20, marker='impact')
    c.key(220, tweak(gp, root_pos=(0, 0.8, 0), chest=(-4, 0, 0)), 'EASE_OUT').key(500, lerp(gp, STANCE, 0.7), 'EASE_IN_OUT').write()
    c = Clip(g, 'crushing_blow_whiff', NOTE)
    c.key(0, cb).key(100, gp, 'EASE_IN', lag=20, marker='impact').key(300, tweak(gp, root_pos=(0, 0.8, 0)), 'EASE_OUT').key(600, STANCE, 'EASE_IN_OUT').write()
    # crushing_blow_slam: he has them by the head. Down into the floor on his right (150), hauled up and over his
    # head (500), down on his left (650), and then swung up and let go (950).
    s1 = Y(root=(1.0, -5.6, 2.4), hips=(34, -20, -4), chest=(22, -24, 0), ra=H(4.6, 2.4, 9.0), la=A(40, 0, 44, -30), rl=F(3.2, 0, -3.0, toe=40), ll=F(-3.0, 0, 3.4), head=(20, 10, 0))
    hi = Y(root=(0, 0.4, 0), hips=(-14, 10, 0), chest=(-18, 10, 0), ra=H(1.0, 31.0, 0.0, pole=(1, 0, 0.4)), la=HC(-5.0, -5.0, 1.0), rl=F(3.0, 0, -2.4, toe=20), ll=F(-3.0, 0, 2.4, toe=20), head=(-20, 0, 0))
    s2 = Y(root=(-1.0, -5.6, 2.4), hips=(34, 26, 4), chest=(22, 30, 0), ra=H(-4.0, 2.4, 9.6), la=A(40, 0, 44, -30), rl=F(3.2, 0, -3.0, toe=40), ll=F(-3.0, 0, 3.4), head=(20, -10, 0))
    fl = Y(root=(0.4, 1.0, 0.6), hips=(-16, -30, 0), chest=(-18, -30, 0), ra=A(-160, -10, 20, -10, -30), la=A(30, 0, 30, -40), rl=F(3.0, 0, -2.4, toe=40), ll=F(-3.0, 0, 2.4, toe=30),
           head=(-26, 0, 0))
    c = Clip(g, 'crushing_blow_slam', NOTE)
    c.key(0, grab).key(150, s1, 'EASE_IN', lag=24, marker='slam').key(300, tweak(s1, root_pos=(0, 0.8, 0)), 'EASE_OUT').key(500, hi, 'EASE_IN_OUT', marker='overhead')
    c.key(650, s2, 'EASE_IN', lag=24, marker='slam').key(800, tweak(s2, root_pos=(0, 0.8, 0)), 'EASE_OUT').key(950, fl, 'EASE_IN', lag=30, marker='fling')
    c.key(1200, lerp(fl, STANCE, 0.8), 'EASE_IN_OUT').write()
    # crushing_blow_suplex: arms locked round their waist from behind, a sit, and he bridges all the way over backward.
    lock = Y(root=(0, -3.0, 1.6), hips=(20, 0, 0), chest=(12, 0, 0), ra=H(2.0, 12.0, 9.0, pole=(1, 0, 0)), la=H(-2.0, 12.0, 9.0, pole=(-1, 0, 0)), rl=F(3.2, 0, -1.6), ll=F(-3.2, 0, 1.6), head=(10, 0, 0))
    lift = Y(root=(0, -0.6, -0.6), hips=(-20, 0, 0), chest=(-16, 0, 0), ra=H(2.0, 17.0, 6.0, pole=(1, 0, 0)), la=H(-2.0, 17.0, 6.0, pole=(-1, 0, 0)), rl=F(3.2, 0, -1.6, toe=20), ll=F(-3.2, 0, 1.6, toe=20),
             head=(-20, 0, 0))
    bridge = Y(root=(0, -3.4, -4.0), hips=(-62, 0, 0), chest=(-34, 0, 0), ra=A(-176, 0, 14, -20), la=A(-176, 0, 14, -20), rl=F(3.2, 0, -1.6, toe=40), ll=F(-3.2, 0, 1.6, toe=40), head=(-40, 0, 0), follow=1.0)
    c = Clip(g, 'crushing_blow_suplex', NOTE)
    c.key(0, grab).key(150, lock, 'EASE_OUT', marker='lock').key(400, lift, 'EASE_IN_OUT', marker='lift').key(620, bridge, 'EASE_IN', marker='impact')
    c.key(700, tweak(bridge, root_pos=(0, 0.6, 0)), 'EASE_OUT').key(1000, bridge, 'EASE_IN_OUT').write()
    c = Clip(g, 'crushing_blow_suplex_throw', NOTE)
    c.key(0, bridge).key(200, lift, 'EASE_OUT').key(600, STANCE, 'EASE_IN_OUT').write()

    # --- Combat Instincts. instincts_feint: the blow he was throwing is simply dropped; a hop back onto his toes,
    # hands loose, already choosing something else.
    ft = Y(root=(0, 0.6, -1.6), hips=(-4, 6, 0), chest=(-2, 4, 0), ra=HC(4.6, -5.0, 3.6), la=HC(-4.4, -4.6, 4.6), rl=F(2.6, 0.6, -3.6, toe=40), ll=F(-2.4, 0.6, 2.0, toe=40), head=(-2, 0, 0))
    c = Clip(g, 'instincts_feint', NOTE)
    c.key(0, lerp(STANCE, ft, 0.4)).key(100, ft, 'OVERSHOOT', marker='cancel').key(400, STANCE, 'EASE_OUT').write()
    # instincts_throw: whatever is next to him gets punched across the field: a short pivot and a full straight right.
    tw = Y(root=(0.6, -2.4, -1.0), hips=(8, 36, 0), chest=(8, 24, 0), ra=HC(5.6, -5.0, 0.5, pole=(1, 0.2, -1)), la=HC(-2.6, -2.0, 7.4))
    th = Y(root=(-0.8, -2.4, 4.0), hips=(14, -30, 0), chest=(12, -34, -4), ra=H(-0.8, 16.0, 18.0), la=HC(-4.6, -3.6, 1.6), rl=F(2.6, 0, -2.6, toe=50, yaw=-30), ll=F(-2.4, 0, 4.2))
    c = Clip(g, 'instincts_throw', NOTE)
    c.key(0, tw)
    c.hit(100, tw, th, lag=30, k=0.10, carry=100)
    c.key(600, STANCE, 'EASE_IN_OUT').write()

    # --- King of Curses. sukuna_faint: the strings are cut. Head down, arms dead, knees going.
    faint = P(root=(0, -2.4, -0.4), hips=(10, 0, 0), chest=(22, 0, 3), head=(40, 0, 6), follow=1.0, ra=A(8, 0, 4, -6), la=A(10, 0, 4, -8), rl=F(2.2, 0, -0.6), ll=F(-2.2, 0, 0.6))
    c = Clip(g, 'sukuna_faint', NOTE)
    c.key(0, STANCE).key(400, faint, 'EASE_IN').breathe(400, faint, period=2400, amp=1.0, sway=3)
    c.write()
    # sukuna_takeover: the head lolls back, a slow breath, and then he straightens somebody else: shoulders rolled
    # open, one hand dragged through his hair, looking down his nose at them.
    back = P(root=(0, -1.0, -0.4), hips=(-4, 0, 0), chest=(-12, 0, 0), head=(-34, 0, -8), follow=1.0, ra=A(4, 0, 10, -10), la=A(4, 0, 10, -10), rl=F(2.2, 0, -0.6), ll=F(-2.2, 0, 0.6))
    hair = S(ra=HC(3.0, 5.0, 1.5, pole=(1, 0.3, 0)), head=(-12, 0, 6), chest=(-8, -4, 0))
    c = Clip(g, 'sukuna_takeover', NOTE)
    c.key(0, faint).key(300, tweak(faint, chest=(6, 0, 0)), 'EASE_IN_OUT').key(500, back, 'EASE_IN', marker='takeover').key(900, hair, 'EASE_IN_OUT', lag=60)
    c.key(1500, tweak(hair, rightArm=(10, 0, 10), head=(4, 0, 0)), 'EASE_IN_OUT', marker='king').key(1800, SREST, 'EASE_IN_OUT').write()


# ---------------------------------------------------------------- Sukuna

SFL, SFR = F(-2.6, 0, 0.6, yaw=16), F(2.6, 0, -0.6, yaw=16)
OPEN_R, OPEN_L = A(-6, 0, 22, -22, -20), A(-6, 0, 22, -22, -20)   # arms hanging open, palms half turned out


def S(root=(0, -0.3, -0.2), hips=(-5, 0, 0), chest=(-6, 0, 0), ra=OPEN_R, la=OPEN_L, rl=SFR, ll=SFL, head=(-8, 0, 0), **kw):
    return P(root=root, hips=hips, chest=chest, ra=ra, la=la, rl=rl, ll=ll, head=head, **kw)


SREST = S()
SAIR = dict(rl=L(-10, 30, 6, 30), ll=L(-30, 50, 6, 30))


def slash(name, dur, t_w, t_h, w, h, carry=60, k=0.2, end=None, lag=14, **over_):
    """A Shrine slash: the cut is a hand, so the hand leads and the body barely follows."""
    c = Clip('yuji', name, NOTE, dur, **over_)
    c.key(0, end or SREST).key(t_w, w, 'EASE_OUT', marker='anticipation')
    c.hit(t_h, w, h, lag=lag, k=k, carry=carry)
    c.key(min(dur - 60, t_h + carry + 170), lerp(h, end or SREST, 0.6), 'EASE_IN_OUT').key(dur, end or SREST, 'EASE_IN_OUT')
    c.write()


def sukuna():
    g = 'yuji'
    # Shrine M1s. 1: two fingers flicked across from left to right at throat height, the way you would wave off a fly.
    slash('shrine_light_1', 450, 55, 115,
          S(chest=(-6, -22, 0), ra=HC(-3.4, -1.6, 5.0, pole=(0.6, 0.6, 0.2))),
          S(chest=(-6, 20, 0), hips=(-5, 8, 0), ra=H(7.4, 20.6, 9.4, wrist=-20), head=(-8, 6, 0)))
    # 2: the left hand answers, a backhand cut rising from his hip to his shoulder.
    slash('shrine_light_2', 450, 55, 115,
          S(chest=(-2, 20, 0), la=HC(2.6, -9.0, 4.0, pole=(-0.6, 0.6, 0.2))),
          S(chest=(-10, -22, 0), hips=(-5, -8, 0), la=H(-8.0, 24.0, 8.0, wrist=-20), head=(-10, -6, 0)))
    # 3: both hands crossed at the wrists in front of him and ripped apart: an X.
    slash('shrine_light_3', 500, 62, 122,
          S(root=(0, -0.8, 0), chest=(4, 0, 0), ra=HC(-2.6, -2.0, 6.0, pole=(1, 0.6, 0)), la=HC(2.6, -3.0, 5.4, pole=(-1, 0.6, 0)), head=(-2, 0, 0)),
          S(root=(0, -0.2, 0.4), chest=(-12, 0, 0), ra=A(-50, 20, 74, -14, -30), la=A(-30, 20, 80, -14, -30), head=(-12, 0, 0)), k=0.1)
    # 4, the finisher: the right arm lifted lazily over his head and dropped straight down in front of him; he steps
    # into it for once, and watches them leave.
    slash('shrine_light_4', 700, 100, 166,
          S(root=(0, 0.2, -0.6), chest=(-12, 14, 0), hips=(-6, 8, 0), ra=A(-176, 0, 10, -30, -20), head=(-10, 0, 0)),
          S(root=(0, -1.6, 2.0), chest=(14, -14, 0), hips=(6, -10, 0), ra=H(1.6, 10.0, 12.5, wrist=20), rl=F(2.6, 0, -1.6, toe=30, yaw=16), ll=F(-2.6, 0, 2.6, yaw=16), head=(0, 0, 0)),
          carry=90, k=0.12, lag=30, blendIn=25, blendOut=200)
    AR = S(root=(0, 0.6, 0), **SAIR)
    slash('shrine_air_1', 450, 55, 115,
          S(root=(0, 0.8, 0), chest=(-6, -22, 0), ra=HC(-3.4, -1.6, 5.0, pole=(0.6, 0.6, 0.2)), **SAIR),
          S(root=(0, 1.0, 0), chest=(-6, 20, 0), ra=HC(7.0, -1.4, 7.4, wrist=-20), **SAIR), end=AR)
    slash('shrine_air_2', 450, 55, 115,
          S(root=(0, 0.8, 0), chest=(-2, 20, 0), la=HC(2.6, -9.0, 4.0, pole=(-0.6, 0.6, 0.2)), **SAIR),
          S(root=(0, 1.0, 0), chest=(-10, -22, 0), la=HC(-7.4, 2.0, 6.0, wrist=-20), **SAIR), end=AR)
    # air 3: a slow turn in the air with one arm trailing out; the cut is the whole circle.
    c = Clip(g, 'shrine_air_3', NOTE, 500).spins()
    a = S(root=(0, 0.8, 0), yaw=40, chest=(-6, 20, 0), ra=A(-70, 30, 60, -20), **SAIR)
    b = S(root=(0, 1.2, 0), yaw=-200, chest=(-6, -20, 0), ra=A(-84, -20, 70, -6, -20), la=A(-20, 0, 40, -30), **SAIR)
    e = S(root=(0, 0.8, 0), yaw=-360, **SAIR)
    c.key(0, AR).key(60, a, 'EASE_OUT', marker='anticipation').key(122, lerp(a, b, 0.5), 'EASE_IN', marker='impact').key(260, b, 'LINEAR').key(440, e, 'EASE_OUT')
    c.key(441, AR, 'STEP').key(500, AR).write()
    # heavy: the hand is raised beside his head with the fingers together, like a blade, and held; the release is one
    # diagonal chop down across his body that finishes with the arm thrown out behind him.
    hc = S(root=(0, -0.4, -0.4), hips=(-4, 16, 0), chest=(-8, 18, 0), ra=HC(6.0, 3.0, 1.0, pole=(1, 0.2, 0), wrist=-10), head=(-8, 0, 4))
    c = Clip(g, 'shrine_heavy_charge', NOTE)
    c.key(0, SREST).key(200, hc, 'EASE_OUT').breathe(300, hc, period=1800, amp=0.5)
    c.write()
    hh = S(root=(0, -1.6, 1.6), hips=(6, -20, 0), chest=(12, -30, 4), ra=H(-5.0, 8.0, 8.0, wrist=20), rl=F(2.6, 0, -1.6, toe=30, yaw=16), ll=F(-2.6, 0, 2.2, yaw=16), head=(2, 0, 0))
    c = Clip(g, 'shrine_heavy', NOTE)
    c.key(0, hc).key(130, tweak(hc, chest=(-4, 8, 0), rightArm=(-10, 0, 0)), 'EASE_OUT', marker='anticipation')
    c.hit(205, hc, hh, lag=30, k=0.14, carry=90)
    c.key(480, lerp(hh, SREST, 0.5), 'EASE_IN_OUT', marker='recovery').key(700, SREST, 'EASE_IN_OUT').write()
    # sprint: he does not break stride; one arm is swept up from the ground as he passes.
    run = S(root=(0, -1.0, 0), hips=(18, 6, 0), chest=(0, -8, 0), ra=A(30, 0, 14, -30), la=A(-20, 0, 14, -40), rl=L(-46, 40, 3, 10), ll=L(34, 66, 3, 20))
    slash('shrine_sprint', 700, 100, 168,
          S(root=(0, -2.0, 0.6), hips=(16, 20, 0), chest=(8, 20, 0), ra=H(5.0, 3.0, -2.0, pole=(1, 0, -1)), rl=L(-30, 50, 3, 10), ll=L(20, 60, 3, 20)),
          S(root=(0, -0.4, 2.6), hips=(-4, -20, 0), chest=(-12, -24, 0), ra=H(-2.0, 30.0, 6.0), rl=F(2.6, 0, -2.4, toe=40), ll=F(-2.6, 0, 3.0), head=(-12, 0, 0)),
          carry=90, k=0.1, lag=30, end=None, priority='MOVEMENT')
    # stomp: he looks down at them and closes his raised hand.
    slash('shrine_stomp', 500, 80, 160,
          S(chest=(4, -8, 0), ra=HC(3.6, -1.0, 6.0, pole=(1, 0.6, 0), wrist=-40), head=(26, 0, 0)),
          S(root=(0, -0.8, 0.4), chest=(10, -10, 0), ra=H(2.0, 12.0, 9.0, wrist=40), head=(32, 0, 0)), carry=70, k=0.1)

    # shrine_sign: Malevolent Shrine. Both hands brought together under his chin, fingers laced and pointing down,
    # elbows wide; he smiles over it.
    sg = S(root=(0, -0.6, 0), hips=(-2, 0, 0), chest=(2, 0, 0), ra=HC(0.9, -3.0, 4.6, pole=(1, 0.4, 0), wrist=30), la=HC(-0.9, -3.0, 4.6, pole=(-1, 0.4, 0), wrist=30), head=(6, 0, 0))
    c = Clip(g, 'shrine_sign', NOTE)
    c.key(0, SREST).key(280, sg, 'OVERSHOOT', marker='sign').breathe(400, sg, period=2200, amp=0.4)
    c.write()
    # shrine_domain_release: the hands part and turn out and down, as if setting something on a table; the shrine
    # comes up behind him and he does not look round.
    ex = S(root=(0, -0.2, 0), chest=(-10, 0, 0), ra=A(-30, 0, 50, -20, -40), la=A(-30, 0, 50, -20, -40), head=(-14, 0, 0))
    c = Clip(g, 'shrine_domain_release', NOTE, 1200, priority='AWAKENING', blendIn=120, blendOut=300)
    c.key(0, sg).key(160, ex, 'EASE_IN', marker='expand').key(500, tweak(ex, rightArm=(8, 0, -8), leftArm=(8, 0, 8), chest=(2, 0, 0)), 'EASE_OUT')
    c.key(1050, SREST, 'EASE_IN_OUT', marker='settle').key(1200, SREST).write()

    # Cleave. cleave_reach: a step and his hand is on their face. cleave_hold: he keeps it there, arm straight, head
    # tilted, deciding. cleave_release: the fingers close and he rips the hand down and away through them.
    rw = S(root=(0, -0.8, -0.6), hips=(-2, 20, 0), chest=(-4, 20, 0), ra=HC(5.6, -6.0, 1.0, wrist=-30))
    rg = S(root=(0, -1.2, 3.0), hips=(6, -22, 0), chest=(4, -24, 0), ra=H(0.6, 21.0, 16.0, wrist=-40), rl=F(2.6, 0, -2.6, toe=40, yaw=16), ll=F(-2.6, 0, 3.6, yaw=16), head=(-4, 0, 0))
    c = Clip(g, 'cleave_reach', NOTE)
    c.key(0, rw).key(200, rg, 'EASE_IN', lag=30, marker='grab').key(300, tweak(rg, rightArm=(2, 0, 0)), 'EASE_OUT').write()
    c = Clip(g, 'cleave_hold', NOTE, tremble=0.4)
    hold = tweak(rg, head=(0, 0, 10), chest=(-3, 0, 0))
    c.key(0, rg).breathe(200, hold, period=1400, amp=0.5)
    c.write()
    rip = S(root=(0, -1.8, 1.6), hips=(8, 16, 0), chest=(14, 26, 0), ra=H(7.0, 6.0, 0.0, wrist=30), rl=F(2.6, 0, -2.6, toe=20, yaw=16), ll=F(-2.6, 0, 3.6, yaw=16), head=(2, -10, 0))
    c = Clip(g, 'cleave_release', NOTE)
    c.key(0, hold).key(90, tweak(hold, rightHand=(40, 0, 0), rightArm=(-6, 0, 0)), 'EASE_OUT', marker='clench')
    c.hit(150, hold, rip, lag=20, k=0.12, carry=80, marker='cleave')
    c.key(600, SREST, 'EASE_IN_OUT').write()
    c = Clip(g, 'cleave_whiff', NOTE)
    c.key(0, rg).key(140, tweak(rg, rightHand=(40, 0, 0), rightArm=(10, 0, 6)), 'EASE_OUT').key(500, SREST, 'EASE_IN_OUT').write()

    # Dismantle. dismantle_windup: two fingers raised across his chest toward the far shoulder, the other hand loose.
    dw = S(root=(0, -0.6, -0.4), hips=(-4, -10, 0), chest=(-4, -26, 0), ra=HC(-4.0, -0.6, 4.4, pole=(0.6, 0.8, 0)), head=(-6, 0, -4))
    c = Clip(g, 'dismantle_windup', NOTE)
    c.key(0, SREST).key(280, dw, 'EASE_OUT').breathe(400, dw, period=1500, amp=0.5)
    c.write()
    # dismantle_swing: the fingers are drawn across in one flat stroke and the arm stays out there while the slashes
    # go where he pointed.
    ds = S(root=(0, -0.6, 1.0), hips=(-4, 10, 0), chest=(-6, 28, 0), ra=H(8.6, 19.6, 8.0, wrist=-10), head=(-8, 0, 0))
    c = Clip(g, 'dismantle_swing', NOTE)
    c.key(0, dw)
    c.hit(100, dw, ds, lag=16, k=0.16, carry=70, marker='slash')
    c.key(450, tweak(ds, rightArm=(6, 0, 6)), 'EASE_IN_OUT', marker='hold').key(700, SREST, 'EASE_OUT').write()
    # dismantle_air: a slow backward flip with the arm drawn back, uncurling at the top.
    c = Clip(g, 'dismantle_air', NOTE)
    f0 = S(root=(0, 0.6, 0), ra=HC(-3.0, -1.0, 4.4, pole=(0.6, 0.8, 0)), **SAIR)
    f1 = S(root=(0, 2.0, 0), hips=(-180, 0, 0), chest=(-20, -20, 0), ra=HC(-4.0, 1.0, 3.0, pole=(0.6, 0.8, 0)), rl=L(-70, 100, 6, 20), ll=L(-60, 90, 6, 20), follow=1.0)
    f2 = S(root=(0, 2.4, 0), hips=(-336, 0, 0), chest=(-10, -30, 0), ra=HC(-4.0, 4.0, 2.0, pole=(0.6, 0.8, 0)), rl=L(-30, 60, 6, 20), ll=L(-50, 80, 6, 20), follow=1.0)
    f3 = S(root=(0, 2.0, 0), hips=(-352, 0, 0), chest=(0, -30, 0), ra=HC(-4.0, 5.0, 2.0, pole=(0.6, 0.8, 0)), rl=L(-10, 30, 6, 30), ll=L(-40, 60, 6, 30), head=(16, 0, 0))
    c.key(0, f0).key(350, f1, 'EASE_IN').key(650, f2, 'LINEAR').key(1000, f3, 'EASE_OUT').write()
    # dismantle_air_swing: one long downward stroke along the line, from above his head to past his knee.
    f3n = S(root=(0, 2.0, 0), hips=(8, 0, 0), chest=(0, -30, 0), ra=HC(-4.0, 5.0, 2.0, pole=(0.6, 0.8, 0)), rl=L(-10, 30, 6, 30), ll=L(-40, 60, 6, 30), head=(16, 0, 0))
    dn = S(root=(0, 1.0, 0.6), hips=(14, 0, 0), chest=(18, 30, 0), ra=HC(7.0, -11.0, 3.0, wrist=20), rl=L(-20, 40, 6, 30), ll=L(-50, 70, 6, 30), head=(20, 0, 0))
    c = Clip(g, 'dismantle_air_swing', NOTE)
    c.key(0, f3n)
    c.hit(100, f3n, dn, lag=10, k=0.14, carry=70, marker='slash')
    c.key(600, S(root=(0, 0.6, 0), **SAIR), 'EASE_IN_OUT').write()

    # World Cutting Slash. Three chanted poses, each a held hand sign, then the swing.
    # 1 SCALE OF THE DRAGON: the right hand up beside his face, fingers hooked, eyes closed, head bowed.
    c1 = S(root=(0, -0.6, 0), chest=(4, 0, 0), ra=HC(3.6, -0.6, 4.0, pole=(1, 0.5, 0), wrist=-30), head=(14, 0, 0))
    # 2 RECOIL: that hand pulled back past his shoulder as the left comes forward, palm down, sighting along it.
    c2 = S(root=(0, -1.6, -0.6), hips=(-2, 30, 0), chest=(-2, 24, 0), ra=HC(6.6, -1.6, -2.0, pole=(1, 0.3, -0.6)), la=H(-1.0, 19.0, 12.0, wrist=20), rl=F(2.8, 0, -2.6, yaw=30),
           ll=F(-2.6, 0, 2.4, yaw=10), head=(0, 0, 0))
    # 3 TWIN METEORS: both hands together over his head, a blade made of two arms.
    c3 = S(root=(0, 0.2, -0.4), hips=(-8, 0, 0), chest=(-14, 0, 0), ra=HC(1.0, 9.6, -0.6, pole=(1, 0.1, 0.4)), la=HC(-1.0, 9.6, -0.6, pole=(-1, 0.1, 0.4)), rl=F(2.8, 0, -2.0, yaw=24),
           ll=F(-2.8, 0, 2.0, yaw=24), head=(-10, 0, 0))
    for name, pose_, prev in (('wcs_chant_1', c1, SREST), ('wcs_chant_2', c2, c1), ('wcs_chant_3', c3, c2)):
        c = Clip(g, name, NOTE, tremble=0.5)
        c.key(0, lerp(prev, pose_, 0.3)).breathe(200, pose_, period=1300, amp=0.7)
        c.write()
    # wcs_swing: brought down in front of him to the floor in one stroke, the whole body bowing behind it; he stays
    # down over it while the world comes apart, and only then stands.
    cut = S(root=(0, -4.4, 2.4), hips=(30, 0, 0), chest=(26, 0, 0), ra=H(1.0, 5.0, 12.5), la=H(-1.0, 5.0, 12.5), rl=F(2.8, 0, -2.6, toe=40, yaw=24), ll=F(-2.8, 0, 3.6, yaw=24), head=(10, 0, 0))
    c = Clip(g, 'wcs_swing', NOTE)
    c.key(0, c3)
    c.hit(100, c3, cut, lag=30, k=0.06, carry=90, marker='cut')
    c.key(600, tweak(cut, root_pos=(0, 0.5, 0), chest=(-3, 0, 0)), 'EASE_IN_OUT', marker='hold').key(900, lerp(cut, SREST, 0.75), 'EASE_IN_OUT').write()

    # Open (Fuga). open_flames: fire held in both upturned palms at his waist. open_clap: the palms brought together.
    # open_draw: the left arm out straight like a bow stave, the right hand drawn back past his ear. open_aim: held at
    # full draw, sighting along the arm. open_release: the string hand opens and flies back.
    of = S(root=(0, -0.8, 0), chest=(2, 0, 0), ra=HC(3.6, -8.0, 5.6, pole=(1, 0.4, -0.2), wrist=-40), la=HC(-3.6, -8.0, 5.6, pole=(-1, 0.4, -0.2), wrist=-40), head=(12, 0, 0))
    c = Clip(g, 'open_flames', NOTE)
    c.key(0, SREST).key(250, of, 'EASE_OUT').breathe(350, of, period=1400, amp=0.7)
    c.write()
    cl = S(root=(0, -0.8, 0), chest=(4, 0, 0), ra=HC(0.9, -5.0, 6.4, pole=(1, 0.4, -0.2)), la=HC(-0.9, -5.0, 6.4, pole=(-1, 0.4, -0.2)), head=(8, 0, 0))
    c = Clip(g, 'open_clap', NOTE)
    c.key(0, tweak(of, rightArm=(0, 0, 14), leftArm=(0, 0, -14))).key(110, cl, 'EASE_IN', marker='clap').key(250, tweak(cl, chest=(-2, 0, 0)), 'EASE_OUT').write()
    dr = S(root=(0.4, -1.8, -0.8), hips=(-2, 56, 0), chest=(-4, 24, 0), la=H(-2.0, 20.0, 13.0), ra=HC(5.0, -1.0, -3.0, pole=(1, 0.3, -0.4)), rl=F(3.2, 0, -3.4, yaw=50), ll=F(-2.6, 0, 3.0),
           head=(-2, 0, 0))
    c = Clip(g, 'open_draw', NOTE, tremble=0.5)
    c.key(0, cl).key(180, lerp(cl, dr, 0.4), 'EASE_OUT').key(500, dr, 'EASE_IN_OUT', lag=40, marker='full draw').write()
    c = Clip(g, 'open_aim', NOTE, tremble=0.5)
    c.key(0, dr).breathe(400, tweak(dr, chest=(-1, 2, 0)), period=1200, amp=0.5)
    c.write()
    rl_ = S(root=(0.4, -1.6, -1.2), hips=(-4, 60, 0), chest=(-8, 30, 0), la=H(-2.0, 20.4, 12.4), ra=A(-70, 60, 70, -20, -30), rl=F(3.2, 0, -3.4, yaw=50), ll=F(-2.6, 0, 3.0), head=(-4, 0, 0))
    c = Clip(g, 'open_release', NOTE)
    c.key(0, dr).key(100, rl_, 'EASE_IN', marker='loose').key(180, tweak(rl_, rightArm=(0, 10, 6), leftArm=(-4, 0, 0)), 'EASE_OUT')
    c.key(450, tweak(rl_, rightArm=(20, 0, -10)), 'EASE_IN_OUT').key(700, SREST, 'EASE_IN_OUT').write()

    # Rush. rush_run / rush_chase: Sukuna running is a lean and nothing else; arms trailing straight back, body like a
    # thrown knife. (The chase reaches one hand forward for them.)
    def cycle(name, a, b, period):
        c = Clip(g, name, NOTE)
        m = tweak(lerp(a, b, 0.5), root_pos=(0, 0.9, 0))
        c.key(0, a).key(period / 4, m, 'LINEAR').key(period / 2, b, 'LINEAR').key(period * 3 / 4, m, 'LINEAR').key(period, a, 'LINEAR')
        c.d.update(loop=True, loopStart=0, loopEnd=int(period))
        c.duration = int(period)
        c.write()
    ra_, la_ = A(64, 0, 16, -8), A(64, 0, 16, -8)
    r1 = S(root=(0, -1.6, 1.0), hips=(44, 6, 0), chest=(6, 0, 0), ra=ra_, la=la_, rl=L(-70, 44, 3, 10), ll=L(34, 84, 3, 20), head=(-36, 0, 0))
    r2 = S(root=(0, -1.6, 1.0), hips=(44, -6, 0), chest=(6, 0, 0), ra=ra_, la=la_, rl=L(34, 84, 3, 20), ll=L(-70, 44, 3, 10), head=(-36, 0, 0))
    cycle('rush_run', r1, r2, 320)
    reach = H(1.0, 14.0, 17.0, wrist=-30)
    cycle('rush_chase', tweak(S(root=(0, -1.6, 1.0), hips=(44, -10, 0), chest=(6, -14, 0), ra=reach, la=la_, rl=L(-70, 44, 3, 10), ll=L(34, 84, 3, 20), head=(-36, 0, 0))),
          S(root=(0, -1.6, 1.0), hips=(44, -18, 0), chest=(6, -14, 0), ra=reach, la=la_, rl=L(34, 84, 3, 20), ll=L(-70, 44, 3, 10), head=(-36, 0, 0)), 320)
    # rush_knee: he arrives knee first, hands in their collar, and they go up.
    kw = S(root=(0, -1.6, 1.0), hips=(20, 0, 0), chest=(6, 0, 0), ra=H(1.6, 19.0, 11.0), la=H(-1.6, 19.0, 11.0), rl=F(2.6, 0, -3.0, toe=40), ll=F(-2.6, 0, 2.0))
    kn = S(root=(0, 1.6, 2.6), hips=(-26, -8, 0), chest=(22, 0, 0), ra=H(1.6, 15.0, 9.0), la=H(-1.6, 15.0, 9.0), rl=L(-112, 124, 4, 30), ll=L(14, 8, 3, 40), head=(-4, 0, 0))
    c = Clip(g, 'rush_knee', NOTE)
    c.key(0, kw).key(100, kn, 'EASE_IN', marker='impact').key(250, tweak(kn, root_pos=(0, 0.8, 0), rightThigh=(-6, 0, 0)), 'EASE_OUT').key(500, S(root=(0, 0.4, 1.0), **SAIR), 'EASE_IN_OUT').write()
    # rush_leap: up after them, one arm cocked high.
    lp = S(root=(0, 2.4, 0), hips=(-10, 20, 0), chest=(-10, 20, 0), ra=A(-150, 10, 24, -46, -10), la=A(-60, 0, 30, -30), rl=L(-60, 100, 6, 30), ll=L(-10, 40, 6, 40), head=(-20, 0, 0))
    c = Clip(g, 'rush_leap', NOTE)
    c.key(0, lerp(SREST, lp, 0.5)).key(300, lp, 'OVERSHOOT').write()
    # rush_slam: and the hand comes down on top of them like closing a book.
    sl = S(root=(0, 0.4, 1.2), hips=(20, -24, 0), chest=(20, -26, 0), ra=H(0.0, 9.0, 11.0, wrist=30), la=A(40, 0, 30, -30), rl=L(-50, 80, 6, 30), ll=L(-20, 50, 6, 30), head=(20, 0, 0))
    # He comes down with them: the hand lifts off, the feet find the floor, and he is standing over it.
    land = S(root=(0, -2.4, 1.0), hips=(14, -6, 0), chest=(10, -8, 0), ra=A(-24, 0, 20, -30), la=A(-10, 0, 24, -30), rl=F(2.8, 0, -1.6, toe=10), ll=F(-2.6, 0, 2.2), head=(14, 0, 0))
    c = Clip(g, 'rush_slam', NOTE)
    c.key(0, lp)
    c.hit(100, lp, sl, lag=20, k=0.12, carry=90)
    c.key(420, land, 'EASE_IN', marker='land').key(600, lerp(land, SREST, 0.7), 'EASE_IN_OUT').write()


if __name__ == '__main__':
    vessel()
    sukuna()
    print('wrote', len(WRITTEN), 'clips:', ' '.join(WRITTEN))
