#!/usr/bin/env python3
"""
Gojo's animation pass: his clips keep their reference choreography (many are matched frame by frame to the JJS GIFs
and timed to the server's hits) and get what they lacked, measured by tools/anim_quality.py:

    python3 tools/gojo_anims.py         # refines assets/jjk/animations/gojo/*.json in place (run once per pass)

  * contact frames marked on the reference's own impact frames (the move traces confirm they land on the hits);
  * a held contact, follow-through past it, and a wind-up where the reference went straight in;
  * overlap: the body reaches each pose first and the limbs whip after (tools/anim_kit OFFSET);
  * the body behind the technique: hips, chest and head move further around the reference's own poses;
  * clips that cut straight onto the contact (rapid_final, twofold_2) travel into it from the previous clip's last pose;
  * held poses (Red's charge, Purple's stages, the domain sign, the guard, Max Blue's steering) breathe instead of freezing.

It refines the files as they are, so it is meant to be run on the reference clips once; rerunning would layer the
refinements twice. The originals are in git (the commit before the Gojo pass).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anim_kit import ClipData, end_pose  # noqa: E402

AUTHORED = 'tools/gojo_anims.py (Gojo animation pass: the reference choreography refined)'


def refine(name, contact=None, wind=None, hold=60, over=None, core=None, alive=None, lead_in=None, overlap=True, body=None,
           start_from=None, **extra):
    c = ClipData('gojo', name)
    if start_from:
        c.start_from(start_from)
    if body:
        c.body(*body[:3], **body[3] if len(body) > 3 else {})
    if lead_in:
        c.lead_in(lead_in[0], end_pose('gojo', lead_in[1]))
        contact = contact + lead_in[0] if contact is not None else None
    if core:
        c.core(core)
    if contact is not None:
        if wind:
            c.wind(contact, lead=wind[0], k=wind[1])
        if hold:
            c.hold(contact, hold)
        if over:
            c.overshoot(contact, contact + hold + over)
        c.mark(contact)
    if overlap:
        c.overlap()
    if alive:
        c.alive(**alive)
    c.save(AUTHORED, **extra)


def main():
    # Blue: the point at 320 ms (Blue grabs at 550), held while it pulls; the kick lands at 400 ms (the server's hit).
    refine('blue_cast', contact=320, wind=(140, 0.6), hold=80, over=70, body=(150, 320, 550, dict(twist=20, lean=8)))
    refine('blue_kick', contact=400, hold=0, over=60, body=(250, 400, 500, dict(twist=-12, lean=-8, crouch=0.4)))
    # Red: the charge breathes while held; the release snaps on the server's hit (90 ms in), wound in the charge.
    refine('red_charge', alive=dict(period=500, cycles=2, amp=1.0, tremble=1.2))
    refine('red_release', contact=90, hold=60, over=50, start_from='red_charge', windup='red_charge',
           body=(30, 90, 300, dict(twist=18, lean=10, crouch=0.8)))
    refine('red_upside_down', contact=200, hold=60, over=60, start_from='red_charge', windup='red_charge',
           body=(100, 200, 450, dict(twist=16, lean=-10, crouch=0.2)))
    refine('max_red_charge', alive=dict(period=450, cycles=2, amp=1.1, tremble=1.6))
    # Max Blue: the point, then held while Blue is steered (the held cast ran 450 ms with nothing posing it).
    refine('max_blue_cast', contact=400, wind=(150, 0.25), hold=80, over=80, core=1.5,
           alive=dict(period=600, cycles=2, amp=0.8))
    # Hollow Purple: each stage holds, breathing (the fusion straining); the release fires on the hit (80 ms in).
    refine('purple_blue', alive=dict(period=500, cycles=2, amp=0.9))
    refine('purple_red', alive=dict(period=500, cycles=2, amp=0.9))
    refine('purple_fusion', alive=dict(period=400, cycles=2, amp=1.2, tremble=2.0))
    refine('purple_release', contact=80, hold=70, over=60, start_from='purple_fusion', windup='purple_fusion',
           body=(30, 80, 300, dict(twist=0, lean=16, crouch=1.4, head=6)))
    # Rapid Punches: the spin kick connects at 540 ms; the barrage loops as it is; the heavy blows at 200/450/700 ms; the
    # hammer cut straight onto its hit, so it now travels 80 ms into it from the heavy blows' overhead wind-up.
    refine('spin_kick', contact=540, hold=0, over=60)
    refine('rapid_heavy', contact=200, hold=0, core=1.15)
    refine('rapid_final', contact=0, hold=60, over=60, lead_in=(80, 'rapid_heavy'), windup='rapid_heavy')
    # Twofold Kick: the side kick at 400 ms; the second kick (the server's hit as twofold_2 starts) now travels 80 ms
    # into its contact from the flip.
    refine('twofold_1', contact=400, wind=(100, 0.2), hold=0, over=60)
    refine('twofold_2', contact=0, hold=60, over=60, lead_in=(80, 'twofold_1'), windup='twofold_1')
    # Teleport: the raise before the blink, the vanish, the strike out of it.
    refine('limitless_raise', contact=250, wind=(130, 0.5), hold=60, over=60, body=(120, 250, 450, dict(twist=18, lean=-8, crouch=-0.8)),
           style='effortless')
    refine('teleport', body=(100, 220, 360, dict(twist=10, lean=14, crouch=1.6)), motion='transition')
    refine('teleport_strike', contact=100, hold=60, over=60, core=1.3, windup='limitless_raise')
    refine('limitless_air_kick', contact=150, wind=(110, 0.5), hold=60, over=70, core=1.2)
    # Infinity, the awakening, the domain.
    refine('infinity_on', contact=200, wind=(130, 0.4), hold=80, over=70, body=(70, 200, 600, dict(twist=16, lean=6)), style='effortless')
    # The awakening's beat: the right hand sweeping up overhead at 350 ms (the blindfold off), the head back at 600.
    refine('awaken', contact=350, wind=(150, 0.5), hold=80, over=80, body=(200, 350, 700, dict(twist=10, lean=-20, crouch=-1.4, head=-20)),
           style='effortless')
    refine('guard_gojo', alive=dict(period=500, cycles=2, amp=0.7))
    refine('domain_sign', alive=dict(period=600, cycles=2, amp=0.8, tremble=1.0))
    refine('domain_release', contact=120, hold=80, over=80, start_from='domain_sign', windup='domain_sign',
           body=(40, 120, 420, dict(twist=0, lean=-26, crouch=-1.8, head=-28)))
    print("refined Gojo's clips")


if __name__ == '__main__':
    main()
