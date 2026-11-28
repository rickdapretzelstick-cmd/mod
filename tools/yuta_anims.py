#!/usr/bin/env python3
"""
Yuta's animation pass, and Rika's (her clips play on her, on her own rig: body, three-part arms, no legs). The clips keep
their poses and get what they lacked, measured by tools/anim_quality.py and the move traces (tools/hit_timing.py):

    python3 tools/yuta_anims.py         # refines animations/yuta and animations/rika in place (once, on the originals)

  * contact frames on the server's hits. Most of Yuta's were drawn on time (the barrage, Severing Path's swings,
    Cursed Speech, Energy Ripple, Resolute Slash); the blows the server lands as their clip starts (Elbow Rush's final
    elbow, Resolute's Black Flash, the domain's blade swing, Rika's throw landing) now start from the
    clip before and travel 80 ms into the blow; the M1 chains are retimed onto MeleeSystem's hits;
  * Rika's blows land late in long clips (her Smash at 700 ms, Downslam at 500, Haymaker at 1100, Slam's grabs from
    400): each is retimed onto its hit, with a held contact, follow-through and her body behind the arm;
  * held poses (charges, stances, the domain sign, Outburst's grip, Rika's calibration) breathe instead of freezing.

It refines the files as they are, so it runs on the originals only (it refuses clips it already refined); the
originals are in git (the commit before the Yuta pass).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anim_pass import BREATHE, Pass  # noqa: E402

AUTH = 'tools/yuta_anims.py (Yuta animation pass: the reference poses refined, timed to the server)'
Y = Pass('yuta', 'Yuta animation pass', AUTH)
R = Pass('rika', 'Yuta animation pass', AUTH)
y, r = Y.refine, R.refine


def main():
    Y.guard()
    R.guard()

    # --- Yuta. Held poses first (the blows after them start from their last pose).
    for name in ('yuta_domain_sign', 'yuta_guard_katana', 'yuta_heavy_charge', 'yuta_steel_heavy_charge', 'yuta_jacobs_ladder',
                 'yuta_outburst_grip'):
        y(name, alive=BREATHE)

    # M1 chains, katana and steel (MeleeSystem: lights and airs land 100 ms in; the fourth light (the finisher), the
    # uppercut, downslam, sprint and stomp 150; the heavy 200).
    for kit in ('yuta_', 'yuta_steel_'):
        # (The steel kit's lefts are straight jabs: the forearm whips out a little after the arm.)
        jab = dict(lag={'leftForearm': 12}) if kit == 'yuta_steel_' else {}
        y(kit + 'light_1', hit=100, **jab)
        y(kit + 'light_2', hit=100)
        # (A blow 100 ms in has little room to wind up: in the chain, the blow before ends wound for it.)
        y(kit + 'light_3', hit=100, wind=(70, 0.5), windup=kit + 'light_2', **jab)
        for i, name in enumerate(('air_1', 'air_2', 'air_3')):  # (in the air the whole body swings with the blade)
            y(kit + name, hit=100, wind=(70, 0.5), core=2.1,
              **({'windup': kit + 'air_' + str(i)} if i else {}))
        y(kit + 'light_4', hit=150)
        y(kit + 'sprint', hit=150, body=(50, 150, 380, dict(twist=16, lean=14, crouch=1.0)))
        y(kit + 'uppercut', hit=150, body=(50, 150, 380, dict(twist=14, lean=-12, crouch=-1.0, head=-10)), core=1.2)
        y(kit + 'downslam', hit=150, body=(50, 150, 400, dict(twist=10, lean=28, crouch=2.0, head=14)), core=1.4)
        y(kit + 'stomp', hit=150, body=(50, 150, 380, dict(twist=8, lean=18, crouch=1.8, head=8)), core=1.2)
        y(kit + 'heavy', hit=200)
    y('yuta_steel_jab', hit=60, old=60, wind=(40, 0.6), body=(20, 60, 200, dict(twist=20, lean=10, crouch=0.6)), core=1.3)  # (a jab out of nothing: the server's hit as it starts; 60 ms is its travel)

    # Elbow Rush: the dash's elbow at 100 ms, the barrage on its six hits (drawn on them), the final elbow as it starts.
    y('yuta_elbow_rush', hit=100, strike='elbow')
    y('yuta_elbow_barrage', hit=100, old=100, strike='elbow', over=None, core=2.0, body=(40, 100, 300, dict(twist=20, lean=12, crouch=0.6)))
    y('yuta_elbow_final', hit=0, prev='yuta_elbow_barrage', strike='elbow')
    y('yuta_elbow_whiff', hit=300, old=300, wind=(150, 0.5), strike='elbow', body=(60, 150, 450, dict(twist=18, lean=12, crouch=0.8)))  # (overextended at 300 ms, staggering)
    # Severing Path: the slide, the three swings at 300/600/900 ms (drawn on them), the whiff; Veilstep's backstep.
    y('yuta_severing_path', motion='transition')
    y('yuta_severing_swings', hit=600, old=600)
    y('yuta_severing_whiff', hit=350, old=350, body=(120, 240, 500, dict(twist=20, lean=10, crouch=0.8)))
    y('yuta_veilstep', motion='transition')
    # Resolute Slash: he vanishes (the wind-ups), reappears and slashes at 150 ms; the Black Flash as it starts.
    y('yuta_resolute_windup', motion='transition')
    y('yuta_resolute_flash_windup', motion='transition', body=(150, 300, 500, dict(twist=18, lean=-10, crouch=1.2, head=-6)))
    y('yuta_resolute_slash', hit=150, old=150, wind=(90, 0.5))
    y('yuta_resolute_flash', hit=0, prev='yuta_resolute_flash_windup')
    # Outburst: the swing as it starts (the burst 150 ms later), the parry's riposte.
    # (a spinning draw: the blade out at 70 ms as the server's swing lands, the hips carrying a full turn into the burst.
    # The burst, the blow, was drawn at 240 ms and the server's lands at 150: brought in to 200, as near as the spin can
    # be squeezed before the chest and arm whip round faster than a frame can show)
    # (no overlap pass: pulling the hips 32 ms ahead crammed the spin's start into the blend, a 102-degree snap; the
    # sword arm alone trails the spin instead)
    y('yuta_outburst_swing', hit=200, old=200, windup='yuta_outburst_grip', remap=[(0, 0), (70, 70), (240, 200), (241, 201), (900, 861)],
      overlap=False, lag={'rightArm': 30})
    y('yuta_outburst_parry', motion='transition', body=(30, 90, 350, dict(twist=-16, lean=-8, crouch=0.8)))  # (a deflection)
    # Second Wind: the grab at 100 ms, the slam at 350 (the grab's light hit as it starts), pummel and swords on their
    # first blow at 200 ms (a blow every 250), the blocked recoil and the whiff.
    # (the dash: the grab lands whenever he reaches them, 100 ms in at a dummy 3 blocks off)
    y('yuta_second_wind', hit=100, old=120, motion='transition')
    # (it began on a pose far from where the dash ends: the arm swung 160 degrees in a tick at the hand-over)
    y('yuta_second_wind_slam', hit=350, old=350, lead=(100, 'yuta_second_wind'))
    y('yuta_second_wind_pummel', hit=200, old=200, strike='elbow')
    y('yuta_second_wind_swords', hit=200, old=200)
    y('yuta_second_wind_blocked', motion='transition')
    y('yuta_second_wind_whiff', hit=250, old=250)
    # Energy Ripple at 700 ms (drawn on it); the fake-out's swing at 100 ms.
    y('yuta_energy_ripple', hit=700, old=700)
    y('yuta_fakeout', hit=100, old=100)
    # Copy: Cursed Speech at 600 ms (drawn on it).
    y('yuta_cursed_speech', hit=600, old=600, body=(400, 600, 900, dict(twist=12, lean=16, crouch=1.0, head=14)), core=1.4)
    # Rika commands: the point, the launch at 250 ms, the throw at 900 ms, the thrown one landing as it starts.
    y('yuta_rika_command', hit='auto', wind=(80, 0.6), body=(40, 120, 400, dict(twist=22, lean=10, crouch=0.6)))
    # (Rika launching him: a crouch and a spring, no blow of his own)
    y('yuta_rika_launch', hit=250, motion='transition', body=(120, 240, 500, dict(twist=8, lean=-12, crouch=-1.0, head=-10)))
    y('yuta_rika_launch_air', hit=250, motion='transition')
    y('yuta_rika_throw', hit=900)
    y('yuta_rika_throw_hit', hit=0, prev='yuta_rika_throw', travel=80,  # (on landing: not cued ahead)
      body=(0, 50, 300, dict(twist=20, lean=14, crouch=1.2)))
    # Authentic Mutual Love: the blade run's swing as it starts; the beams; True Love's cinematic.
    y('yuta_blade_run', alive=None)
    y('yuta_blade_swing', hit=0, prev='yuta_blade_run')
    y('yuta_beam_conjure', hit=400, old=400, wind=(180, 0.5), body=(200, 400, 800, dict(twist=12, lean=-10, crouch=1.0, head=-8)))
    # (the hands thrust at 100 ms; the beam they push out fires at 400)
    y('yuta_beam_quick', hit=100, old=100, wind=(70, 0.5), body=(30, 100, 400, dict(twist=16, lean=14, crouch=1.2)), core=1.2)
    y('yuta_true_love', motion='transition', body=(800, 1500, 2400, dict(twist=14, lean=-26, crouch=-1.8, head=-28)), core=1.9)

    # --- Rika (moves start her clip as they start).
    r('rika_calibrate', alive=BREATHE)
    r('rika_idle')
    # (Her torso drives each blow: she rears back and throws her weight down or through.)
    r('rika_smash', hit=700, body=(450, 700, 1000, dict(twist=14, lean=30, crouch=2.0, head=16)), core=1.5)
    r('rika_smash_air', hit=700, body=(450, 700, 1000, dict(twist=14, lean=30, crouch=2.0, head=16)), core=1.5)
    r('rika_downslam', hit=500, body=(300, 500, 850, dict(twist=10, lean=32, crouch=2.0, head=16)), core=1.5)
    r('rika_downslam_air', hit=500, body=(300, 500, 850, dict(twist=10, lean=32, crouch=2.0, head=16)), core=1.5)
    r('rika_haymaker', hit=1100, body=(800, 1100, 1450, dict(twist=34, lean=16, crouch=1.0, head=8)), core=1.4)
    r('rika_launch', hit=250, wind=(150, 0.5), body=(100, 200, 550, dict(twist=16, lean=-30, crouch=-1.8, head=-20)), core=2.4)
    r('rika_launch_air', hit=250, wind=(220, 0.5), body=(100, 200, 550, dict(twist=16, lean=-30, crouch=-1.8, head=-20)), core=2.6)
    r('rika_throw', hit=900, body=(600, 900, 1200, dict(twist=28, lean=18, crouch=1.2, head=8)), core=1.3)
    r('rika_slam_reach', motion='transition', body=(200, 500, 800, dict(twist=8, lean=20, crouch=1.0, head=10)))
    r('rika_slam', hit=400)  # (grabbed as it starts; slammed at 400, 850, 1300, 1750 and 2200 ms)
    r('rika_barrage', hit=100, old=100, over=None, body=(40, 100, 300, dict(twist=30, lean=20, crouch=0.8)), core=3.6)
    r('rika_beam', hit=3000, old=3000, body=(2450, 2750, 3500, dict(twist=0, lean=-24, crouch=-1.6, head=-28)), core=1.9, lag={'rightArm': 60})  # (fires 60 ticks in)
    r('rika_summon', motion='transition')
    r('rika_true_love', motion='transition', body=(500, 1100, 1800, dict(twist=8, lean=22, crouch=1.4, head=18)), core=1.5)
    print("refined Yuta's and Rika's clips")


if __name__ == '__main__':
    main()
