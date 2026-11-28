#!/usr/bin/env python3
"""
Ryu's animation pass: tools/gen_ryu_anims.py writes his clips (timed to his moves' ticks), and this refines them in
place with what they lacked, measured by tools/anim_quality.py and the move traces (tools/hit_timing.py):

    python3 tools/gen_ryu_anims.py && python3 tools/ryu_anims.py

  * contact frames on the server's hits. His multi-hit clips were drawn on them (Appetizer's blasts and ray,
    Unsatisfied's blows, Dessert's flurry, No Idea's exchange, the Granite dash, What Are You After's slam); the blows
    the server lands as their clip starts (Dessert's kick and swing, Not Invited's punch, the traded punch, Granite
    Blast's shot) start from the clip before and travel 80 ms into the blow;
  * a held contact, follow-through past it, a wind-up where the clip went straight in, the body leading the limbs;
  * held poses (the cannon aimed, Every Last Drop's charge, the wind-ups, the runs and leaps) breathe;
  * Restyle's hair and knuckle poses are flourishes, not blows: transitions with the body in them.

It refines the generator's output (it refuses clips it already refined): to start over, re-run the generator.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anim_pass import BREATHE, Pass  # noqa: E402

def both_arms(c):
    """The leap raises both arms for the two-handed slam (it raised only the right; the left jumped 190 degrees as the
    slam took over)."""
    c.tracks['leftArm']['rot'] = [(t, [v[0], -v[1], -v[2]], e) for t, v, e in c.tracks['rightArm']['rot']]


P = Pass('ryu', 'Ryu animation pass', 'tools/ryu_anims.py (Ryu animation pass: gen_ryu_anims.py refined, timed to the server)')
r = P.refine


def main():
    P.guard()

    # Held poses first (the blows after them start from their last pose).
    for name in ('ryu_after_leap', 'ryu_after_lunge', 'ryu_dessert_run', 'ryu_eld_charge', 'ryu_eld_point', 'ryu_granite_aim',
                 'ryu_invited_lunge', 'ryu_invited_windup', 'ryu_no_idea_windup', 'ryu_second_helping_air'):
        r(name, alive=BREATHE)
    r('ryu_second_helping', alive=BREATHE, edit=both_arms)

    # M1: the cannon's ray finishes the chain 200 ms in (MeleeMove startup 4): a discharge, the body takes the recoil.
    r('ryu_m1_ray', hit=200, old=220, motion='transition', body=(120, 200, 400, dict(twist=6, lean=-12, crouch=0.8, head=-14)))
    # Appetizer: blasts at 400 and 850 ms, the ray at 1600 (drawn on them).
    r('ryu_appetizer', hit=400, old=400, wind=(180, 0.5))
    # Unsatisfied: blows at 250, 450, 650 ms, the clash at 1000, the toss at 1300 (drawn on them); the whiff.
    r('ryu_unsatisfied', hit=250, old=250, body=(120, 210, 420, dict(twist=14, lean=8, crouch=0.6)))
    r('ryu_unsatisfied_whiff', hit=250, old=250)
    # Dessert: the run's kick as it starts, the swing as it starts, the flurry's blows every 300 ms (drawn on them).
    r('ryu_dessert_kick', hit=0, old=120, prev='ryu_dessert_run')
    r('ryu_dessert_miss', hit=150, old=150)
    r('ryu_dessert_swing', hit=0, old=150, prev='ryu_dessert_kick')
    r('ryu_dessert_flurry', hit=300, old=300)
    # No Idea: the grab as the punch starts (wound in the wind-up), the exchange's punches every 200 ms (drawn on them).
    r('ryu_no_idea_punch', hit=80, old=80, windup='ryu_no_idea_windup')
    # (the move runs 300 ms past the push at 1600: the exchange settles on its last pose for it)
    r('ryu_no_idea_exchange', hit=200, old=200, extend=300)
    # Not Invited: the punch as it starts, out of the lunge.
    r('ryu_invited_punch', hit=0, old=70, prev='ryu_invited_lunge', travel=80)  # (on reaching them: not cued ahead)
    # Second Helping: the slam and the air punch land 100 ms in; they began far from the leap's pose (a 148-degree arm
    # snap at the hand-over), so they travel in from it first: the slam from where the leap is when it takes over
    # (230 ms in), landing 80 ms after the hit like the other blows that start on it. The whiff.
    r('ryu_second_helping_slam', hit=180, old=120, lead=(100, 'ryu_second_helping', 230))
    r('ryu_second_helping_punch', hit=100, old=90, lead=(60, 'ryu_second_helping_air'), wind=(60, 0.5),
      windup='ryu_second_helping_air')
    r('ryu_second_helping_whiff', hit=150, old=150)
    # What Are You After: the slam at 300 ms (a held clip), the traded punch as it starts, the miss.
    r('ryu_after_slam', hit=300, old=300)
    r('ryu_after_trade', hit=0, old=100, prev='ryu_after_lunge', travel=80)  # (on reaching them: not cued ahead)
    r('ryu_after_miss', hit=150, old=150)
    # Granite Blast: the shot as it starts (tapped or charged): a discharge from the cannon of his pompadour, not a blow
    # of the arms, so the body takes the recoil; the dash's shoulder at 250 ms.
    r('ryu_granite_fire', hit=0, old=60, prev='ryu_granite_aim', travel=80,  # (on the key's release: not cued ahead)
      motion='transition',
      body=(20, 80, 300, dict(twist=6, lean=-14, crouch=0.8, head=-16)))
    r('ryu_granite_fire_held', hit=0, old=60, prev='ryu_granite_aim', travel=80,
      motion='transition',
      body=(20, 80, 400, dict(twist=8, lean=-18, crouch=1.0, head=-20)))
    r('ryu_granite_dash', hit=250, old=250)
    # Every Last Drop: the beam looses as the fire clip starts (held while it pours out).
    r('ryu_eld_fire', hit=80, old=80, windup='ryu_eld_charge', motion='transition',
      body=(20, 80, 400, dict(twist=6, lean=-16, crouch=1.2, head=-10)))
    # Restyle: flourishes.
    r('ryu_restyle_pose', motion='transition', body=(150, 300, 700, dict(twist=14, lean=-10, crouch=-0.8, head=-12)), core=1.4)
    r('ryu_restyle_comb', motion='transition', body=(500, 1100, 1900, dict(twist=12, lean=-8, crouch=-0.6, head=-16)), core=1.4)
    r('ryu_restyle_knuckles', motion='transition', body=(200, 450, 800, dict(twist=14, lean=10, crouch=0.8, head=8)))
    print("refined Ryu's clips")


if __name__ == '__main__':
    main()
