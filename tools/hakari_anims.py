#!/usr/bin/env python3
"""
Hakari's animation pass: every clip of his kit (base and Jackpot), rebuilt to one bar, scored by tools/anim_quality.py.

    python3 tools/hakari_anims.py        # rewrites assets/jjk/animations/hakari/*.json

What every clip gets:
  * a style: a boxer-brawler, fists high at the chin, bouncing on the balls of his feet, the whole body behind each blow;
  * contact on the hit: each strike's contact frame (marked "contact") lands where the server's hit lands, measured from
    the move traces (MoveTimingAuditClientTest): e.g. Rough Energy hits the tick its strike clip starts, so the strike's
    contact is ~80 ms in, the earliest the 90 ms minimum blend lets it show;
  * anticipation (a wind-up away from the target), a hit-stop (the contact held a beat), an overshoot and a settle;
  * overlap: each pose is reached hips first, then chest, arm, forearm, hand (and thigh before shin), so the body leads
    and the limb whips after it; the striking limb is the anchor, on time;
  * life in holds: held poses are loops with breath and weight shift; the dashes are real run cycles.

Durations, priorities and blends stay as the server's timing expects (read from the current files).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anim_kit  # noqa: E402
from anim_kit import Seq, arm, blend, body, breathe, leg, mirror, pose, run_cycle, shifted, strike  # noqa: E402

anim_kit.OUT = os.path.join(anim_kit.ANIMS, 'hakari')
anim_kit.AUTHORED = 'tools/hakari_anims.py (Hakari animation pass)'
write = anim_kit.write

# --- Hakari's poses (right-handed; the left side is mirror()) ---

STANCE = pose(body(twist=16, lean=8, crouch=1.8, head_down=4), arm('right', -62, 10, 18, -128, 8), arm('left', -80, -8, 12, -112, 6),
              leg('right', 16, 22, 4, -6), leg('left', -14, 20, 4, 6))

# Straights (the cross; the jab is its mirror).
WIND_R = pose(body(twist=32, lean=4, crouch=2.5, fwd=-1), arm('right', -46, 20, 26, -132, 14), arm('left', -80, -10, 10, -108, 6),
              leg('right', 22, 32, 4, -8), leg('left', -16, 22, 4, 8))
HIT_R = pose(body(twist=-34, lean=14, crouch=1.8, fwd=4.5), arm('right', -94, 2, 0, -6, -6), arm('left', -70, -14, 12, -116, 8),
             leg('right', 34, 12, 4, -12), leg('left', -32, 26, 4, 10))
OVER_R = pose(body(twist=-41, lean=16, crouch=2.0, fwd=5.2), arm('right', -98, -6, -2, -2, -8), arm('left', -68, -16, 12, -118, 8),
              leg('right', 36, 10, 4, -12), leg('left', -34, 28, 4, 10))
SETTLE_R = pose(body(twist=-20, lean=10, crouch=1.9, fwd=3.0), arm('right', -84, 4, 8, -40, -4), arm('left', -74, -12, 12, -112, 8),
                leg('right', 28, 16, 4, -10), leg('left', -26, 24, 4, 9))
WIND_L, HIT_L, OVER_L, SETTLE_L = mirror(WIND_R), mirror(HIT_R), mirror(OVER_R), mirror(SETTLE_R)

# Rough Energy: the charged straight, body thrown behind it.
ROUGH_CHARGE = pose(body(twist=44, lean=16, crouch=4.2, fwd=-1.5, head_down=6), arm('right', 10, 28, 28, -118, 20),
                    arm('left', -88, -20, 14, -30, 0), leg('right', 28, 50, 8, -14), leg('left', -28, 44, 6, 14))
ROUGH_COCK = pose(body(twist=56, lean=19, crouch=4.6, fwd=-2.2, head_down=7), arm('right', 34, 34, 32, -126, 26),
                  arm('left', -90, -22, 14, -26, 0), leg('right', 30, 52, 8, -14), leg('left', -30, 46, 6, 14))
ROUGH_HIT = pose(body(twist=-48, lean=22, crouch=3.0, fwd=8.0, head_down=-2), arm('right', -100, 0, -2, 0, -10), arm('left', 30, 20, 16, -40, 0),
                 leg('right', 46, 6, 4, -16), leg('left', -42, 48, 4, 16))
ROUGH_OVER = pose(body(twist=-55, lean=24, crouch=3.1, fwd=8.6, head_down=-3), arm('right', -103, -6, -4, 0, -12), arm('left', 34, 22, 18, -36, 0),
                  leg('right', 48, 4, 4, -16), leg('left', -44, 50, 4, 16))
ROUGH_SETTLE = pose(body(twist=-34, lean=18, crouch=2.6, fwd=6.4), arm('right', -92, 0, 2, -14, -6), arm('left', 20, 16, 14, -48, 0),
                    leg('right', 42, 10, 4, -14), leg('left', -38, 44, 4, 14))

# Reserve Balls: a sidearm flick of the pachinko balls.
THROW_WIND = pose(body(twist=40, lean=-4, crouch=1.6, fwd=-1, head_down=2), arm('right', 30, 40, 50, -60, 20), arm('left', -70, -10, 20, -60, 0),
                  leg('right', 20, 18, 6, -6), leg('left', -26, 24, 6, 10))
THROW_REL = pose(body(twist=-40, lean=16, crouch=2.4, fwd=4.0), arm('right', -92, -20, 20, -10, -30), arm('left', -40, -20, 30, -110, 0),
                 leg('right', 40, 16, 4, -12), leg('left', -38, 34, 4, 12))
THROW_OVER = pose(body(twist=-40, lean=16, crouch=2.0, fwd=4.0), arm('right', -72, -40, -10, -18, -40), arm('left', -36, -22, 30, -112, 0),
                  leg('right', 32, 12, 4, -10), leg('left', -32, 28, 4, 10))
THROW_SETTLE = pose(body(twist=-18, lean=10, crouch=1.8, fwd=2.5), arm('right', -60, -20, 10, -64, -10), arm('left', -60, -14, 18, -112, 4),
                    leg('right', 24, 16, 4, -8), leg('left', -22, 22, 4, 8))

# Shutter Doors: the summoning thrust of an open palm.
SIGN_WIND = pose(body(twist=30, lean=-4, crouch=1.6, head_down=-2), arm('right', -18, 26, 46, -148, 0), arm('left', -60, -10, 10, -100, 0),
                 leg('right', 18, 20, 4, -6), leg('left', -16, 20, 4, 6))
SIGN_PUSH = pose(body(twist=-34, lean=14, crouch=2.8, fwd=3.0), arm('right', -92, 0, 6, -4, 40), arm('left', -50, -10, 20, -112, 0),
                 leg('right', 36, 16, 4, -10), leg('left', -34, 34, 4, 12))
SIGN_HOLD = shifted(SIGN_PUSH, rightArm=(-3, 0, 0), chest=(1, -3, 0))
SIGN_SETTLE = pose(body(twist=-10, lean=8, crouch=2.0, fwd=1.8), arm('right', -80, 4, 10, -30, 20), arm('left', -60, -10, 16, -112, 0),
                   leg('right', 22, 18, 4, -8), leg('left', -20, 22, 4, 8))

# Fever Breaker: the snap kick, re-chambered into the spinning heel kick that finishes it.
KICK_CHAMBER = pose(body(twist=20, lean=-10, crouch=1.0, roll=-6, head_down=-2), arm('right', -40, 20, 40, -90, 0), arm('left', -60, -20, 30, -80, 0),
                    leg('right', -80, 100, 14, 20), leg('left', -4, 22, 4, 6))
KICK_HIT = pose(body(twist=-24, lean=-20, crouch=0.6, fwd=2.0, roll=-18), arm('right', 16, 30, 56, -40, 0), arm('left', -40, -10, 48, -70, 0),
                leg('right', -100, 6, 30, 30), leg('left', 6, 18, 4, 4))
KICK_OVER = pose(body(twist=-28, lean=-23, crouch=0.6, fwd=2.2, roll=-22), arm('right', 20, 32, 58, -36, 0), arm('left', -36, -10, 50, -66, 0),
                 leg('right', -108, 2, 32, 32), leg('left', 6, 18, 4, 4))
KICK_RETRACT = pose(body(twist=0, lean=-6, crouch=1.2, roll=-8), arm('right', -30, 20, 40, -90, 0), arm('left', -56, -16, 34, -84, 0),
                    leg('right', -70, 90, 14, 16), leg('left', -2, 22, 4, 6))
HIGH_HIT = pose(body(twist=-34, lean=-26, crouch=0.4, fwd=2.4, roll=-26), arm('right', 24, 34, 62, -34, 0), arm('left', -30, -10, 54, -66, 0),
                leg('right', -120, 4, 40, 34), leg('left', 6, 18, 4, 4))
HIGH_OVER = shifted(HIGH_HIT, rightThigh=(-6, 0, 2), chest=(-2, -4, -2))
HIGH_SETTLE = pose(body(twist=-8, lean=-4, crouch=1.6, fwd=1.4, roll=-6), arm('right', -30, 20, 40, -100, 0), arm('left', -60, -14, 30, -96, 0),
                   leg('right', -40, 60, 10, 10), leg('left', 0, 24, 4, 6))
SPIN_CHAMBER = pose(body(twist=62, lean=6, crouch=2.4, roll=-4), arm('right', -60, 30, -20, -130, 0), arm('left', -70, -20, 30, -90, 0),
                    leg('right', 18, 40, 6, -10), leg('left', -20, 36, 6, 10))
SPIN_HIT = pose(body(twist=-80, lean=-16, crouch=0.8, fwd=2.0, roll=24), arm('right', 10, -20, 70, -30, 0), arm('left', -30, 20, 60, -40, 0),
                leg('right', 4, 20, 4, 4), leg('left', -96, 4, 40, 30))
SPIN_OVER = pose(body(twist=-90, lean=-18, crouch=0.8, fwd=2.2, roll=28), arm('right', 14, -22, 72, -26, 0), arm('left', -28, 22, 62, -38, 0),
                 leg('right', 4, 20, 4, 4), leg('left', -104, 2, 42, 32))
SPIN_SETTLE = pose(body(twist=-40, lean=4, crouch=2.6, fwd=1.5, roll=8), arm('right', -40, -10, 30, -100, 0), arm('left', -60, 10, 20, -90, 0),
                   leg('right', 18, 34, 6, -8), leg('left', -30, 40, 6, 10))

# The door combo: the command, the leg raised for the axe kick, the axe.
COMMAND = pose(body(twist=-22, lean=-8, crouch=2.0, head_down=-6, fwd=2.0), arm('right', -120, 10, 30, -40, 0), arm('left', -50, -10, 30, -90, 0),
               leg('right', 30, 26, 6, -10), leg('left', -30, 34, 6, 10))
RAISE = pose(body(twist=8, lean=-14, crouch=0.4, head_down=-10), arm('right', -20, 20, 70, -30, 0), arm('left', -20, -20, 70, -30, 0),
             leg('right', -150, 10, 8, 30), leg('left', 4, 16, 4, 6))
AXE_HIT = pose(body(twist=-6, lean=26, crouch=3.2, fwd=3.0, head_down=20), arm('right', 30, 20, 40, -30, 0), arm('left', 30, -20, 40, -30, 0),
               leg('right', -50, 10, 6, 10), leg('left', -10, 40, 4, 10))
AXE_OVER = shifted(AXE_HIT, hips=(4, 0, 0), chest=(3, 0, 0), root=(0, -0.6, 0), rightThigh=(6, 0, 0))
AXE_SETTLE = pose(body(twist=4, lean=14, crouch=2.6, fwd=2.0, head_down=8), arm('right', -40, 10, 30, -100, 0), arm('left', -50, -10, 30, -100, 0),
                  leg('right', 10, 30, 6, -6), leg('left', -12, 32, 4, 8))

# Guards and signs.
DOOR_GUARD = pose(body(twist=6, lean=8, crouch=2.6, head_down=10), arm('right', -96, -36, -20, -110, 0), arm('left', -92, -40, -22, -116, 0),
                  leg('right', 16, 28, 6, -6), leg('left', -16, 26, 6, 6))
IDG_SIGN = pose(body(twist=0, lean=4, crouch=1.0, head_down=8), arm('right', -72, -40, -22, -122, 0), arm('left', -72, -40, -22, -122, 0),
                leg('right', 8, 14, 6, -4), leg('left', -8, 14, 6, 4))

# Jackpot.
JP_CROUCH = pose(body(twist=10, lean=30, crouch=5.0, head_down=16), arm('right', -20, 20, 30, -110, 0), arm('left', -20, -20, 30, -110, 0),
                 leg('right', 24, 60, 8, -14), leg('left', -24, 56, 8, 14))
JP_BURST = pose(body(twist=0, lean=-30, crouch=-1.8, head_down=-34), arm('right', -170, 0, 30, -20, 0), arm('left', -170, 0, 30, -20, 0),
                leg('right', 2, 4, 12, -10), leg('left', -2, 4, 12, 10))

# Volleys and Overwhelming Luck.
UPPER_HIT = pose(body(twist=-30, lean=-14, crouch=-1.2, fwd=2.5, head_down=-14), arm('right', -160, 6, 8, -30, -10), arm('left', -30, -10, 20, -110, 6),
                 leg('right', 22, 8, 4, -14), leg('left', -14, 12, 4, 10))
UPPER_OVER = shifted(UPPER_HIT, rightArm=(-6, 0, 0), chest=(-3, -4, 0), root=(0, 0.4, 0))
UPPER_SETTLE = pose(body(twist=-18, lean=-4, crouch=0.6, fwd=1.8, head_down=-6), arm('right', -130, 4, 10, -60, -6), arm('left', -50, -10, 18, -112, 6),
                    leg('right', 20, 14, 4, -10), leg('left', -14, 16, 4, 8))
HOOK_WIND = pose(body(twist=40, lean=8, crouch=2.6, fwd=-0.5, roll=-4), arm('right', -60, 30, 62, -96, 10), arm('left', -82, -10, 8, -100, 6),
                 leg('right', 16, 30, 6, -8), leg('left', -20, 26, 4, 8))
HOOK_HIT = pose(body(twist=-50, lean=12, crouch=2.0, fwd=3.5, roll=4), arm('right', -88, -34, 54, -78, 0), arm('left', -64, -16, 14, -112, 8),
                leg('right', 28, 18, 6, -10), leg('left', -28, 30, 4, 10))
HOOK_OVER = shifted(HOOK_HIT, chest=(0, -6, 2), rightArm=(0, -6, 0))
HOOK_SETTLE = pose(body(twist=-30, lean=10, crouch=2.0, fwd=3.0, roll=3), arm('right', -80, -20, 46, -90, 0), arm('left', -66, -14, 14, -108, 8),
                   leg('right', 24, 20, 6, -10), leg('left', -24, 28, 4, 10))

# Rushdown's grip and hurl; the leap after the target.
GRIP = arm('right', -92, -10, 0, -30, 0)
HURL_COCK = pose(body(twist=34, lean=10, crouch=2.6, fwd=0), arm('right', -60, 30, 40, -90, 10), arm('left', -60, -10, 20, -90, 0),
                 leg('right', 22, 30, 6, -8), leg('left', -26, 30, 6, 10))
HURL = pose(body(twist=-50, lean=20, crouch=2.4, fwd=5.0), arm('right', -130, -10, 20, -10, -20), arm('left', 20, 20, 20, -40, 0),
            leg('right', 36, 10, 4, -12), leg('left', -36, 30, 4, 10))
HURL_OVER = shifted(HURL, rightArm=(10, -8, 0), chest=(3, -5, 0))
LEAP_CROUCH = pose(body(twist=0, lean=24, crouch=4.5, head_down=10), arm('right', 40, 20, 20, -40, 0), arm('left', 40, -20, 20, -40, 0),
                   leg('right', 30, 70, 6, -14), leg('left', -30, 66, 6, 14))
LEAP_LAUNCH = pose(body(twist=0, lean=-10, crouch=-2.0, head_down=-10), arm('right', -140, 10, 20, -40, 0), arm('left', -130, -10, 20, -40, 0),
                   leg('right', -20, 10, 4, 20), leg('left', 10, 14, 4, 10))
LEAP_TUCK = pose(body(twist=10, lean=6, crouch=-1.5, head_down=-6), arm('right', -90, 10, 20, -110, 0), arm('left', -80, -10, 20, -110, 0),
                 leg('right', -60, 100, 6, 16), leg('left', -40, 90, 6, 14))

# Energy Surge's flying kick.
SURGE_CHAMBER = pose(body(twist=24, lean=6, crouch=2.4, head_down=4), arm('right', 20, 20, 30, -70, 0), arm('left', -70, -10, 30, -90, 0),
                     leg('right', -90, 110, 10, 20), leg('left', -10, 40, 4, 10))
SURGE_HIT = pose(body(twist=-20, lean=-20, crouch=-2.0, fwd=3.0, roll=-10), arm('right', 30, 20, 50, -40, 0), arm('left', -60, -10, 50, -70, 0),
                 leg('right', -96, 4, 20, 30), leg('left', -30, 80, 6, 14))
SURGE_OVER = shifted(SURGE_HIT, rightThigh=(-6, 0, 0), chest=(-2, -3, 0))
SURGE_LAND = pose(body(twist=10, lean=30, crouch=4.4, fwd=2.0, head_down=8), arm('right', -30, 16, 30, -70, 0), arm('left', -60, -16, 30, -80, 0),
                  leg('right', 30, 70, 8, -16), leg('left', -36, 60, 8, 14))


# A blow the server lands at a known tick starts its clip Anim.LEAD (2) ticks ahead: its contact frame is 100 ms in.
CUED = 100

def main():
    # Base kit.
    s = Seq(800)  # Reserve Balls: released at 260 ms; the balls land at 400 ms (the server's hit, 3 blocks out).
    strike(s, THROW_WIND, THROW_REL, THROW_OVER, THROW_SETTLE, STANCE, 160, 260, start=STANCE)
    write('reserve_balls', s)

    s = Seq(640)  # Shutter Doors: the thrust at 300 ms; the doors rise from it (the server's slam at 400 ms).
    strike(s, SIGN_WIND, SIGN_PUSH, SIGN_HOLD, SIGN_SETTLE, STANCE, 180, 300, start=STANCE, hold_ms=80)
    write('shutter_sign', s)

    s = Seq(1050)  # Rough Energy's charge: into the coil, then held, breathing, shaking with the gathering energy.
    s.add(0, STANCE, overlap=False).add(170, blend(STANCE, ROUGH_CHARGE, 0.75), 'EASE_OUT').add(250, ROUGH_CHARGE, 'EASE_IN_OUT', overlap=False)
    breathe(s, ROUGH_CHARGE, 250, 400, 2, amp=1.2, tremble=1.5)
    write('rough_charge', s, loop=(250, 1050))

    s = Seq(700)  # Rough Energy's strike: its clip starts 2 ticks before the hit (Anim.LEAD), so a last cock and the contact at 100 ms.
    strike(s, None, ROUGH_HIT, ROUGH_OVER, ROUGH_SETTLE, STANCE, None, CUED, start=ROUGH_CHARGE)
    write('rough_strike', s, windup='rough_charge')

    s = Seq(900)  # Fever Breaker's kick: contact at 250 ms (+5 ticks); re-chambered into the spin by 850 (the finish starts).
    s.add(0, STANCE, overlap=False).add(170, KICK_CHAMBER, 'EASE_OUT').add(250, KICK_HIT, 'EASE_IN', marker='contact')
    s.add(310, shifted(KICK_HIT, chest=(0.6, 0, 0)), 'LINEAR').add(390, KICK_OVER, 'EASE_OUT').add(560, KICK_RETRACT, 'EASE_IN_OUT')
    s.add(900, KICK_RETRACT, 'EASE_IN_OUT', overlap=False)
    write('fever_kick', s)

    s = Seq(800)  # Fever Breaker's finish: from the re-chambered leg, a high roundhouse, cued 2 ticks before it lands (contact at 100 ms).
    strike(s, None, HIGH_HIT, HIGH_OVER, HIGH_SETTLE, STANCE, None, CUED, start=KICK_RETRACT)
    write('fever_finish', s, blendIn=60, windup='fever_kick')

    s = Seq(300)  # The door combo's command (the doors rise), stepping toward the leg raise.
    s.add(0, STANCE, overlap=False).add(140, COMMAND, 'EASE_OUT').add(300, blend(COMMAND, RAISE, 0.35), 'EASE_IN_OUT', overlap=False)
    write('fever_crush', s, motion='transition')

    s = Seq(1000)  # The leg raised for the axe (held 12 ticks): up, then balancing on one foot.
    s.add(0, blend(COMMAND, RAISE, 0.35), overlap=False).add(140, shifted(RAISE, rightThigh=(-8, 0, 0)), 'EASE_OUT').add(200, RAISE, 'EASE_IN_OUT', overlap=False)
    breathe(s, RAISE, 200, 400, 2, amp=1.6)
    write('fever_raise', s, loop=(200, 1000))

    s = Seq(600)  # The axe kick: cued 2 ticks before it lands (contact at 100 ms), the whole body dropping into it.
    strike(s, None, AXE_HIT, AXE_OVER, AXE_SETTLE, STANCE, None, CUED, start=RAISE)
    write('fever_axe', s, windup='fever_raise')

    s = Seq(1000)  # Door Guard: arms up behind the doors, braced.
    s.add(0, STANCE, overlap=False).add(140, shifted(DOOR_GUARD, chest=(2, 0, 0)), 'EASE_OUT').add(200, DOOR_GUARD, overlap=False)
    breathe(s, DOOR_GUARD, 200, 400, 2, amp=0.8)
    write('door_guard', s, loop=(200, 1000))

    s = Seq(1400)  # Idle Death Gamble's sign: the hands meet, then held, trembling with the domain gathering.
    s.add(0, STANCE, overlap=False).add(280, shifted(IDG_SIGN, chest=(-3, 0, 0), head=(-6, 0, 0)), 'EASE_OUT').add(400, IDG_SIGN, overlap=False)
    breathe(s, IDG_SIGN, 400, 500, 2, amp=1.0, tremble=1.2)
    write('idg_sign', s, loop=(400, 1400))

    # Jackpot.
    s = Seq(1500)  # The jackpot: down, the burst (marked at its peak), swaying in it, then back to the fight.
    s.add(0, STANCE, overlap=False).add(220, JP_CROUCH, 'EASE_OUT').add(400, JP_BURST, 'EASE_IN', marker='contact')
    s.add(470, shifted(JP_BURST, chest=(-2, 0, 0)), 'LINEAR').add(650, shifted(JP_BURST, chest=(0, 10, 0), hips=(0, 6, 0), rightArm=(6, 0, 0)), 'EASE_IN_OUT')
    s.add(900, shifted(JP_BURST, chest=(0, -10, 0), hips=(0, -6, 0), leftArm=(6, 0, 0)), 'EASE_IN_OUT')
    s.add(1120, shifted(JP_BURST, chest=(2, 0, 0)), 'EASE_IN_OUT').add(1500, STANCE, 'EASE_IN_OUT', overlap=False)
    write('jackpot', s)

    s = Seq(400)  # Lucky Volley's opener: the jab at 200 ms (+4 ticks), ending set for the flurry.
    strike(s, WIND_L, HIT_L, OVER_L, SETTLE_L, blend(SETTLE_L, WIND_R, 0.5), 120, 200, start=STANCE, hold_ms=50)
    write('volley_open', s)

    s = Seq(200)  # The flurry (hits every 100 ms): left, right, the body twisting into each.
    fl = [(0, HIT_L), (50, blend(HIT_L, WIND_R, 0.45)), (100, HIT_R), (150, blend(HIT_R, WIND_L, 0.45)), (200, HIT_L)]
    for t, p in fl:
        s.add(t, p, 'EASE_IN' if t % 100 == 0 else 'EASE_OUT', marker='contact' if t in (0, 100) else None, overlap=False)
    write('volley_flurry', s, loop=(0, 200))

    s = Seq(700)  # The volley's last blow: an uppercut cued 2 ticks before it lands (contact at 100 ms).
    strike(s, None, UPPER_HIT, UPPER_OVER, UPPER_SETTLE, STANCE, None, CUED, start=shifted(WIND_R, rightArm=(20, 0, 0), root=(0, -0.6, 0)))
    write('volley_final', s, blendIn=60, windup='volley_flurry')

    s = Seq(720)  # Lucky Rushdown's run at the target.
    run_cycle(s, 0, 360, 2, lean=24, crouch=1.6)
    write('rushdown_run', s, loop=(0, 720))

    s = Seq(720)  # The drag: still running, the right hand gripping the target.
    run_cycle(s, 0, 360, 2, lean=16, crouch=1.8, twist=-8, right_arm=GRIP)
    write('rushdown_drag', s, loop=(0, 720))

    s = Seq(700)  # The hurl: cued 2 ticks before the release (at 100 ms).
    strike(s, None, HURL, HURL_OVER, SETTLE_R, STANCE, None, CUED, start=HURL_COCK)
    write('rushdown_throw', s, windup='rushdown_drag')

    s = Seq(350)  # The leap after the hurled target: down, up, tucked (the punch follows in the air).
    s.add(0, STANCE, overlap=False).add(110, LEAP_CROUCH, 'EASE_OUT').add(190, LEAP_LAUNCH, 'EASE_IN').add(350, LEAP_TUCK, 'EASE_OUT', overlap=False)
    write('energy_leap', s, motion='transition')

    s = Seq(250)  # Overwhelming Luck's ready: a bounce, then set for the left.
    s.add(0, STANCE, overlap=False).add(110, shifted(STANCE, root=(0, -1.2, 0), hips=(3, 0, 0)), 'EASE_OUT').add(250, WIND_L, 'EASE_IN_OUT', overlap=False)
    write('overwhelm_ready', s, motion='transition')

    for side, wind, hit, over, nxt in (('left', WIND_L, HIT_L, OVER_L, WIND_R), ('right', WIND_R, HIT_R, OVER_R, WIND_L)):
        s = Seq(230)  # Each blow is cued 2 ticks ahead (contact at 100 ms) and winds the other hand for the next.
        s.add(0, wind, overlap=False)
        s.add(CUED, hit, 'EASE_IN', marker='contact').add(CUED + 40, shifted(hit, chest=(0.6, 0, 0)), 'LINEAR').add(CUED + 70, over, 'EASE_OUT')
        s.add(230, nxt, 'EASE_IN_OUT', overlap=False)
        # Held on the next wind until the next blow replaces it (the gaps between blows run 200-350 ms): no dropping the
        # arms between punches. The server releases it when the move ends.
        write('overwhelm_' + side, s, windup='overwhelm_' + ('right' if side == 'left' else 'left'), hold=True)

    s = Seq(800)  # The last blow: a hook cued 2 ticks before it lands (contact at 100 ms).
    strike(s, None, HOOK_HIT, HOOK_OVER, HOOK_SETTLE, STANCE, None, CUED, start=HOOK_WIND)
    write('overwhelm_final', s, windup='overwhelm_left')

    s = Seq(720)  # Fever's rush (not played by any move today): a charging run, both fists cocked.
    run_cycle(s, 0, 360, 2, lean=28, crouch=2.0, right_arm=arm('right', -50, 14, 20, -128, 0), left_arm=arm('left', -60, -14, 20, -124, 0))
    write('fever_rush', s, loop=(0, 720))

    s = Seq(600)  # Energy Surge's dash: low and fast, the right shoulder leading.
    run_cycle(s, 0, 300, 2, lean=34, crouch=2.2, twist=-14, right_arm=arm('right', -40, -10, 10, -120, 0))
    write('surge_dash', s, loop=(0, 600))

    s = Seq(700)  # The flying kick: contact at 200 ms (+4 ticks after it starts), then the landing.
    s.add(0, blend(STANCE, SURGE_CHAMBER, 0.3), overlap=False).add(140, SURGE_CHAMBER, 'EASE_OUT').add(200, SURGE_HIT, 'EASE_IN', marker='contact')
    s.add(260, shifted(SURGE_HIT, chest=(0.6, 0, 0)), 'LINEAR').add(330, SURGE_OVER, 'EASE_OUT').add(500, SURGE_LAND, 'EASE_IN')
    s.add(700, STANCE, 'EASE_IN_OUT', overlap=False)
    write('surge_kick', s)

    # Rhythm: the jackpot dance on the beat (two bars, 400 ms beats), bouncing, shoulders rolling, arms swinging.
    s = Seq(1600)
    for i in range(9):
        t = i * 200
        down = i % 2 == 0
        sway = 1 if (i // 2) % 2 == 0 else -1
        p = pose(body(twist=10 * sway, lean=6, crouch=2.6 if down else 1.0, head_down=8 if down else -2, side=1.0 * sway, roll=4 * sway),
                 arm('right', -50 - 30 * sway, 10, 30, -100 + 20 * sway, 0), arm('left', -50 + 30 * sway, -10, 30, -100 - 20 * sway, 0),
                 leg('right', 10 * sway, 30 if down else 12, 6, -4), leg('left', -10 * sway, 30 if down else 12, 6, 4))
        s.add(min(t, 1600), p, 'EASE_OUT' if down else 'EASE_IN', overlap=False)
    write('rhythm_dance', s, loop=(0, 1600))
    print('wrote Hakari\'s clips')


if __name__ == '__main__':
    main()
