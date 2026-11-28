#!/usr/bin/env python3
"""
Hit reactions for every two-legged fighter (players, the training dummy, zombies, skeletons, piglins... anything drawn
with the humanoid model): how they take a blow, chosen per hit by ClientAnimations from the way it pushed them and how
hard (the hitstun), and the states after it.

    python3 tools/reaction_anims.py        # rewrites assets/jjk/animations/reactions/*.json

  * a flinch snaps to its impact pose within ~70 ms of the blow (the blend in is 40 ms: hit reactions skip the 90 ms
    floor), the head and chest first and the arms flung after them, overshoots a touch, then settles into a stunned hold
    that breathes for as long as the hitstun lasts;
  * from the way the blow pushed them: struck in front (two flinches that alternate, so a flurry doesn't repeat one pose),
    from behind, on the left, on the right; a heavy blow (18+ ticks of hitstun) is a bigger flinch with a step back;
  * every flinch starts from its own stunned pose, so a fresh hit while still reeling snaps from the hold, not from
    standing straight;
  * launched: thrown back, then flailing in the air until they land; spiked: driven down; knocked down: buckling,
    falling back and hitting the floor with a bounce, then lying there breathing; getting up inside the 500 ms wake-up;
  * guard broken, overloaded (Unlimited Void) and pulled (Blue): holds with life in them.

Conventions: tools/anim_author.py (arm x < 0 raises forward, thigh x < 0 swings the leg forward, shin x > 0 bends the
knee, hips x > 0 lean forward, head x < 0 looks up; root pos is (right, up, forward) px, root rot tips the whole body).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anim_kit  # noqa: E402
from anim_kit import Seq, arm, body, breathe, leg, mirror, pose, shifted  # noqa: E402

anim_kit.OUT = os.path.join(anim_kit.ANIMS, 'reactions')
anim_kit.AUTHORED = 'tools/reaction_anims.py (hit reactions)'
REACTION = dict(priority='RAGDOLL', interruptible=False, blendIn=40, blendOut=250, motion='reaction')


def write(name, s, **extra):
    anim_kit.write(name, s, **{**REACTION, **extra})


LIMBS = ('rightArm', 'leftArm', 'rightForearm', 'leftForearm', 'rightThigh', 'leftThigh', 'rightShin', 'leftShin')


def limbs_wait(s, p, t=25):
    """The limbs stay put a beat while the blow throws the head and chest: they're flung after (the body leads)."""
    s.add(t, {b: v for b, v in p.items() if b in LIMBS}, 'EASE_IN_OUT', overlap=False)


# --- Stunned holds (what a flinch settles into) ---
STUN = pose(body(lean=-8, crouch=1.2, head_down=-8), arm('right', 12, 0, 16, -22), arm('left', 10, 0, 14, -20),
            leg('right', 6, 14, 2), leg('left', -8, 16, 2))
STUN_BACK = pose(body(lean=12, crouch=1.4, head_down=6), arm('right', 18, 0, 14, -16), arm('left', 16, 0, 12, -18),
                 leg('right', -10, 16, 2), leg('left', 8, 14, 2))
STUN_HEAVY = pose(body(lean=8, crouch=3.0, head_down=12), arm('right', 10, 0, 12, -28), arm('left', 8, 0, 10, -32),
                  leg('right', 12, 32, 4), leg('left', -14, 34, 4))
STUN_SIDE = pose(body(twist=-12, lean=-4, crouch=1.4, head_down=-4, side=0.6), arm('right', 0, 0, 24, -24), arm('left', 14, 0, 14, -20),
                 leg('right', 4, 16, 6), leg('left', -6, 14, 2))

# --- Impacts ---
FRONT = pose(body(lean=-26, crouch=1.6, head_down=-30, fwd=-1.4), arm('right', 34, 0, 34, -30), arm('left', 30, 0, 30, -26),
             leg('right', 14, 20, 4), leg('left', -10, 14, 3))
FRONT_B = pose(body(twist=-22, lean=-20, crouch=1.6, head_down=-22, head_turn=-16, fwd=-1.2), arm('right', 42, -10, 38, -34),
               arm('left', 16, 0, 22, -40), leg('right', 16, 22, 5), leg('left', -12, 14, 3))
FRONT_HEAVY = pose(body(lean=-38, crouch=2.8, head_down=-36, fwd=-2.8), arm('right', 58, 0, 50, -40), arm('left', 52, 0, 46, -36),
                   leg('right', 28, 34, 6), leg('left', -6, 28, 4))
BACK = pose(body(lean=26, crouch=1.8, head_down=-22, fwd=1.6), arm('right', 38, 0, 22, -18), arm('left', 34, 0, 20, -16),
            leg('right', -22, 18, 3), leg('left', 16, 12, 3))
BACK_HEAVY = pose(body(lean=40, crouch=3.0, head_down=-30, fwd=3.0), arm('right', 60, 0, 34, -22), arm('left', 56, 0, 30, -20),
                  leg('right', -30, 26, 4), leg('left', 24, 18, 4))
# Struck on the left: the left shoulder driven back, the body pushed right, the head snapping away.
LEFT = pose(body(twist=-28, lean=-10, crouch=1.6, head_turn=-22, side=1.6), arm('right', -12, 0, 44, -30), arm('left', 28, 0, 30, -24),
            leg('right', 6, 18, 9), leg('left', -4, 12, 2))
STUN_LEFT, LEFT_IMPACT = STUN_SIDE, LEFT
STUN_RIGHT, RIGHT_IMPACT = mirror(STUN_SIDE), mirror(LEFT)


def flinch(name, impact, hold, t_hit=70, settle=380, heavy=False):
    """Snaps from the stunned pose to {impact} at {t_hit} (decelerating, as a body thrown by a blow), overshoots, settles
    back into {hold} by {settle} and breathes there."""
    end = settle + 800
    s = Seq(end)
    s.add(0, hold, overlap=False)
    limbs_wait(s, hold)
    s.add(t_hit, impact, 'EASE_OUT', marker='contact')
    over = shifted(impact, head=(-4, 0, 0), chest=(-2.5, 0, 0), rightArm=(4, 0, 3), leftArm=(4, 0, -3))
    s.add(t_hit + 50, over, 'EASE_OUT')
    if heavy:  # (a step back to keep their feet)
        stagger = shifted(blend_to(impact, hold, 0.5), root=(0, 0, -2.5), rightThigh=(-14, 0, 0), leftThigh=(10, 0, 0))
        s.add(int(settle * 0.6), stagger, 'EASE_IN_OUT')
    else:
        s.add(int(settle * 0.55), blend_to(impact, hold, 0.6), 'EASE_IN_OUT')
    s.add(settle, hold, 'EASE_IN_OUT', overlap=False)
    breathe(s, hold, settle, 400, 2, amp=0.9 if not heavy else 1.3, tremble=0.6)
    write(name, s, loop=(settle, end))


def blend_to(a, b, k):
    return anim_kit.blend(a, b, k)


# --- Airborne and floored ---
LAUNCH = pose(body(lean=-32, crouch=0.4, head_down=-32, fwd=-1.6), arm('right', 40, 0, 46, -34), arm('left', 36, 0, 42, -30),
              leg('right', -36, 46, 8), leg('left', 12, 32, 6))
AIR_A = pose(body(lean=-24, crouch=0.2, head_down=-22), arm('right', 20, 0, 58, -50), arm('left', 50, 0, 30, -20),
             leg('right', -42, 56, 8), leg('left', 6, 24, 6))
AIR_B = mirror(AIR_A)
SPIKED = pose(body(lean=42, crouch=0.6, head_down=-18, fwd=1.0), arm('right', -150, 0, 30, -20), arm('left', -146, 0, 28, -18),
              leg('right', 30, 24, 6), leg('left', 24, 30, 6))
CRUMPLE = pose(body(lean=-12, crouch=3.6, head_down=-14), arm('right', 24, 0, 30, -30), arm('left', 20, 0, 28, -30),
               leg('right', 18, 50, 6), leg('left', -6, 54, 6))
LYING = pose(body(lean=0, crouch=0, head_down=10), arm('right', -10, 0, 40, -15), arm('left', -10, 0, 40, -15),
             leg('right', -6, 10, 6), leg('left', -4, 16, 4))


def root(rot, up, fwd=0.0):
    return {'root': {'rot': [rot, 0, 0], 'pos': [0, up, fwd]}}


def main():
    # Flinches: front (two, alternating), front heavy, behind, behind heavy, the sides.
    flinch('reaction_hit_front', FRONT, STUN)
    flinch('reaction_hit_front_b', FRONT_B, STUN)
    flinch('reaction_hit_front_heavy', FRONT_HEAVY, STUN_HEAVY, t_hit=80, settle=560, heavy=True)
    flinch('reaction_hit_back', BACK, STUN_BACK)
    flinch('reaction_hit_back_heavy', BACK_HEAVY, STUN_HEAVY, t_hit=80, settle=560, heavy=True)
    flinch('reaction_hit_left', LEFT_IMPACT, STUN_LEFT)
    flinch('reaction_hit_right', RIGHT_IMPACT, STUN_RIGHT)

    # Launched: thrown back, then flailing until they land (it lasts as long as they're airborne).
    s = Seq(1400)
    s.add(0, STUN, overlap=False)
    limbs_wait(s, STUN)
    s.add(80, LAUNCH, 'EASE_OUT', marker='contact').add(130, shifted(LAUNCH, head=(-4, 0, 0)), 'EASE_OUT')
    s.add(300, AIR_A, 'EASE_IN_OUT', overlap=False).add(600, AIR_B, 'EASE_IN_OUT').add(900, AIR_A, 'EASE_IN_OUT').add(1200, AIR_B, 'EASE_IN_OUT')
    s.add(1400, AIR_A, 'EASE_IN_OUT', overlap=False)
    write('reaction_hit_launched', s, loop=(300, 1400), tremble=1.5)

    # Spiked: driven down, held, shaking with the speed of it.
    s = Seq(900)
    s.add(0, STUN, overlap=False)
    limbs_wait(s, STUN)
    s.add(70, SPIKED, 'EASE_OUT', marker='contact').add(130, shifted(SPIKED, chest=(4, 0, 0), head=(-4, 0, 0)), 'EASE_OUT')
    s.add(260, SPIKED, 'EASE_IN_OUT', overlap=False)
    breathe(s, SPIKED, 260, 320, 2, amp=1.2, tremble=1.5)
    write('reaction_spiked', s, loop=(260, 900))

    # Knocked down: the knees buckle, they topple back, the floor (a bounce), then lying there.
    s = Seq(1300)
    s.add(0, pose(STUN, root(0, 0)), overlap=False)
    s.add(110, pose(CRUMPLE, root(-8, 0, -0.5)), 'EASE_OUT')
    s.add(240, pose(blend_to(CRUMPLE, LYING, 0.5), root(-58, 0.5, -2.5)), 'EASE_IN')  # (falling: gravity)
    s.add(300, pose(shifted(LYING, head=(-12, 0, 0), rightArm=(-20, 0, 10), leftArm=(-20, 0, 10)), root(-94, 2.0)), 'EASE_IN', marker='contact')
    s.add(370, pose(LYING, root(-84, 2.8)), 'EASE_OUT')  # (the bounce)
    s.add(460, pose(LYING, root(-90, 2.0)), 'EASE_IN_OUT', overlap=False)
    breathe(s, pose(LYING, root(-90, 2.0)), 460, 420, 2, amp=0.8)
    write('reaction_knockdown', s, loop=(460, 1300), blendIn=60)

    # Getting up (the 500 ms wake-up): rolled onto a knee, pushed up, standing by 450.
    s = Seq(500)
    s.add(0, pose(LYING, root(-90, 2.0)), overlap=False)
    s.add(160, pose(shifted(CRUMPLE, rightArm=(-30, 0, 0), leftArm=(-40, 0, 0)), root(-40, 0.6, -1)), 'EASE_IN_OUT')
    s.add(300, pose(CRUMPLE, root(-6, 0, -0.5)), 'EASE_OUT')
    s.add(450, pose(STUN, root(0, 0)), 'EASE_IN_OUT', marker='contact')
    s.add(500, pose(STUN, root(0, 0)), 'EASE_IN_OUT', overlap=False)
    write('reaction_getup', s, motion='transition', blendIn=40)

    # Guard broken: arms knocked wide, staggering back, wobbling.
    gb = pose(body(lean=-22, crouch=1.6, head_down=-18, fwd=-1.6), arm('right', -40, 0, 64, -32), arm('left', -40, 0, 64, -32),
              leg('right', 14, 22, 4), leg('left', -8, 14, 3))
    gb_hold = pose(body(lean=-16, crouch=1.8, head_down=-14), arm('right', -28, 0, 54, -28), arm('left', -28, 0, 54, -28),
                   leg('right', 10, 20, 4), leg('left', -6, 16, 3))
    s = Seq(1300)
    s.add(0, gb_hold, overlap=False).add(90, gb, 'EASE_OUT', marker='contact').add(150, shifted(gb, head=(-4, 0, 0)), 'EASE_OUT')
    s.add(320, shifted(blend_to(gb, gb_hold, 0.5), root=(0, 0, -2)), 'EASE_IN_OUT').add(500, gb_hold, 'EASE_IN_OUT', overlap=False)
    breathe(s, gb_hold, 500, 400, 2, amp=1.6, tremble=0.8)
    write('reaction_guard_broken', s, loop=(500, 1300))

    # Overloaded (Unlimited Void): the mind gone, head tipped back, arms slack, shaking.
    ov = pose(body(lean=-4, crouch=0.8, head_down=-28), arm('right', 8, 0, 12, -10), arm('left', 8, 0, 12, -10),
              leg('right', 2, 8, 2), leg('left', -2, 8, 2))
    s = Seq(1400)
    s.add(0, STUN, overlap=False).add(200, ov, 'EASE_OUT', marker='contact').add(400, ov, 'EASE_IN_OUT', overlap=False)
    breathe(s, ov, 400, 500, 2, amp=0.6, tremble=1.0)
    write('reaction_overload', s, loop=(400, 1400), tremble=4, blendIn=200)

    # Pulled (Blue): dragged off their feet, arms reaching ahead, legs trailing.
    pl = pose(body(lean=34, crouch=0.4, head_down=-20, fwd=1.8), arm('right', -120, 0, 35, -20), arm('left', -120, 0, 35, -20),
              leg('right', 36, 40, 10), leg('left', 22, 30, 10))
    s = Seq(1300)
    s.add(0, STUN, overlap=False)
    limbs_wait(s, STUN)
    s.add(100, pl, 'EASE_OUT', marker='contact').add(300, pl, 'EASE_IN_OUT', overlap=False)
    breathe(s, pl, 300, 500, 2, amp=3.2, tremble=1.2)
    write('reaction_pulled', s, loop=(300, 1300), tremble=3, blendIn=100)
    print('wrote the hit reactions')


if __name__ == '__main__':
    main()
