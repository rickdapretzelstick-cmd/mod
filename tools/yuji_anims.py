#!/usr/bin/env python3
"""
Yuji's animation pass (and Sukuna's, his awakened kit): his clips keep their poses and get what they lacked, measured by
tools/anim_quality.py and the move traces (tools/hit_timing.py):

    python3 tools/yuji_anims.py         # refines assets/jjk/animations/yuji/*.json in place (once, on the originals)

  * contact frames on the server's hits. Most of his blows land the tick their clip starts (the wind-up is the held clip
    before: divergent_windup, crushing_blow_charge, cleave_hold, rush_chase...), but the clips wound up again inside
    themselves and struck 60-220 ms late, snapping the arm through 120-170 degrees at the hand-over. Those clips now
    start from the held clip's pose and travel 80 ms into the blow (cut); blows the server lands later (the M1 chain,
    the slams) are retimed onto the hit;
  * a held contact, follow-through past it, a wind-up where the clip went straight in, the body leading the limbs;
  * held poses (charges, stances, chants, the domain sign) breathe instead of freezing.

It refines the files as they are, so it runs on the originals only (it refuses clips it already refined); the
originals are in git (the commit before the Yuji pass).
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anim_kit import ClipData  # noqa: E402
import anim_quality  # noqa: E402

AUTHORED = 'tools/yuji_anims.py (Yuji animation pass: the reference poses refined, timed to the server)'
DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/jjk/animations/yuji')
LEAD = 80  # ms of travel into a blow the server lands as its clip starts


def arrival(name):
    """Where the clip's blow lands as authored: the key that ends the striking limb's fastest stretch."""
    c = anim_quality.Clip(os.path.join(DIR, name + '.json'))
    moving = [b for b in anim_quality.LIMBS if b in c.tracks]
    peak = {b: max(c.speeds(b), key=lambda p: p[1]) for b in moving}
    strike = max(peak, key=lambda b: peak[b][1])
    t_peak = peak[strike][0]
    return next((t for t, _, _ in c.tracks[strike] if t >= t_peak - 10), t_peak)


def refine(name, hit=None, prev=None, old=None, wind=None, hold=60, over=60, body=None, core=None, alive=None, overlap=True,
           remap=None, extend=None, **extra):
    """{hit}: when the server lands the blow, ms from the clip's start (0: as it starts, wound up in {prev})."""
    c = ClipData('yuji', name)
    if extend:
        c.extend(extend)
    if remap:
        c.remap(remap)
    contact = None
    if hit == 'auto':  # (no server hit: the blow lands where it was drawn)
        hit = arrival(name)
    if hit is not None:
        old = arrival(name) if old is None else old
        if hit == 0 and prev:
            c.cut(old, LEAD, prev)
            contact = LEAD
            extra.setdefault('windup', prev)
        else:
            c.retime(old, hit)
            contact = hit
            # A wind-up inside the clip: pulled back before the blow (as long as the way in allows).
            # (A kick that sweeps from the leg drawn back to overhead is already as wound as a leg gets: wind=False.)
            if wind is None and hit >= 60:
                wind = (min(110, hit * 0.7), 0.35)
    if body == (None,):  # the body behind a blow the clip drew with the arm alone
        body = (max(0, contact - 80), contact, contact + 220, dict(twist=18, lean=10, crouch=0.8))
    if body:
        c.body(*body[:3], **body[3] if len(body) > 3 else {})
    if core:
        c.core(core)
    if contact is not None:
        if wind:  # (False: none)
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


BREATHE = dict(period=500, cycles=2, amp=0.8)


def main():
    done = [f for f in glob.glob(os.path.join(DIR, '*.json')) if 'Yuji animation pass' in json.load(open(f)).get('authored', '')]
    if done:
        raise SystemExit(f'{len(done)} Yuji clips are already refined. Restore the originals first (from the commit before the '
                         'Yuji animation pass), e.g. git checkout <commit> -- src/main/resources/assets/jjk/animations/yuji')

    # Held poses first (the blows after them start from their last pose).
    for name in ('cleave_reach', 'cleave_hold', 'crushing_blow_charge', 'crushing_blow_air', 'crushing_blow_dash', 'cursed_strikes_ready',
                 'cursed_strikes_slide', 'cursed_strikes_hop', 'cursed_strikes_dropkick', 'dismantle_windup', 'dismantle_air',
                 'divergent_windup', 'manji_stance', 'manji_swoop', 'open_flames', 'open_clap', 'open_draw', 'open_aim', 'rush_run',
                 'rush_chase', 'rush_leap', 'shrine_heavy_charge', 'shrine_sign', 'sukuna_faint', 'wcs_chant_1', 'wcs_chant_2',
                 'wcs_chant_3'):
        refine(name, alive=BREATHE)

    # Divergent Fist: the punch as the wind-up ends (the cursed energy's second impact 250 ms later); Black Flash links.
    refine('divergent_punch', hit=0, prev='divergent_windup')
    for name in ('black_flash_punch', 'black_flash_dropkick'):
        refine(name, hit=0, prev='divergent_punch')
    refine('black_flash_uppercut', hit=0, prev='divergent_punch', body=(20, 80, 320, dict(twist=12, lean=-14, crouch=-1.0, head=-12)))
    refine('black_flash', hit='auto')

    # Cursed Strikes: the flurry (a punch every 250 ms), the last punch at 200 ms, the finisher's three blows.
    # (drawn as seven punches 180 ms apart; the server's six land 250 ms apart from the first frame)
    refine('cursed_strikes_flurry', hit=500, old=500, hold=40, over=None, wind=False,
           remap=[(0, 0), (110, 60), (290, 250), (470, 500), (650, 750), (830, 1000), (1010, 1250), (1190, 1380), (1250, 1430), (1500, 1650)])
    refine('cursed_strikes_final', hit=200)
    refine('cursed_strikes_kick', hit=0, prev='cursed_strikes_flurry')
    refine('cursed_strikes_floor', hit=0, prev='cursed_strikes_kick')
    refine('cursed_strikes_spin', hit=0, prev='cursed_strikes_floor')
    refine('cursed_strikes_land', hit=0, prev='cursed_strikes_dropkick')
    refine('cursed_strikes_blocked', motion='transition')  # (the punch knocked back: a recoil, no blow)

    # Crushing Blow: grabbed on contact, slammed at 150 (and 650) ms; the release launch, the suplex, the whiff's shockwave.
    refine('crushing_blow_slam', hit=150, body=(60, 150, 400, dict(twist=18, lean=36, crouch=2.6, head=18)), core=1.45)
    refine('crushing_blow_suplex', hit=150, body=(20, 80, 400, dict(twist=0, lean=-34, crouch=2.2, head=-18)), core=1.2)
    refine('crushing_blow_suplex_throw', hit=0, old=200, prev='crushing_blow_suplex')
    refine('crushing_blow_release', hit=0, prev='crushing_blow_slam', body=(20, 80, 300, dict(twist=14, lean=-14, crouch=-1.0, head=-14)))
    refine('crushing_blow_whiff', hit=0, prev='crushing_blow_dash')

    # Manji Kick: the counter kick lands 100 ms in; the finisher's face kick, the spin and the slam.
    refine('manji_kick', hit=100, wind=False, windup='manji_stance', body=(30, 100, 350, dict(twist=-10, lean=-16, crouch=0.4)))
    refine('manji_face_kick', hit=100, wind=False, windup='manji_stance', body=(30, 100, 300, dict(twist=-10, lean=-16, crouch=0.4)))
    refine('manji_slam', hit=0, prev='manji_grapple_spin')
    for name in ('manji_dodge', 'manji_grapple_spin', 'manji_recover'):  # (moves between poses: no blow)
        refine(name, motion='transition')

    # Combat Instincts: the block leaves his hand as the clip starts (no clip before it): thrown 100 ms in.
    refine('instincts_throw', hit=100)
    refine('instincts_feint', motion='transition', body=(60, 120, 320, dict(twist=14, lean=8, crouch=0.8)))  # (a flinch: no blow)

    # Sukuna. M1 chain (MeleeSystem: lights and airs land 100 ms in, the sprint and stomp 150, the heavy 200).
    for name in ('shrine_light_1', 'shrine_light_2', 'shrine_air_1', 'shrine_air_2'):
        refine(name, hit=100)
    for name in ('shrine_light_3', 'shrine_air_3'):
        refine(name, hit=100, body=(40, 100, 300, dict(twist=22, lean=12, crouch=1.0)))
    refine('shrine_light_4', hit=100, body=(40, 100, 350, dict(twist=34, lean=20, crouch=1.6, head=8)), core=1.35)
    refine('shrine_sprint', hit=150, body=(60, 150, 400, dict(twist=32, lean=24, crouch=1.8)), core=1.45)
    refine('shrine_stomp', hit=150, body=(60, 150, 350, dict(twist=10, lean=24, crouch=2.4, head=12)), core=1.15)
    refine('shrine_heavy', hit=200)
    # Dismantle's slashes from 100 ms; the air slash, the World Slash and Cleave's dicing as their clips start.
    refine('dismantle_swing', hit=100)
    refine('dismantle_air_swing', hit=0, prev='dismantle_air', body=(40, 80, 300, dict(twist=22, lean=14, crouch=0.8)))
    refine('wcs_swing', hit=0, prev='wcs_chant_3')
    refine('cleave_release', hit=0, prev='cleave_hold', body=(20, 80, 300, dict(twist=28, lean=14, crouch=1.2)))
    refine('cleave_whiff', hit=220, old=500, extend=300, body=(140, 220, 420, dict(twist=20, lean=10, crouch=0.8)))  # (one sweep to the end: it lands at 220 and settles)
    # (the arrow leaves at the 100 ms key, not the last; a release, not a blow: the body recoils with the shot)
    refine('open_release', hit=0, old=100, prev='open_aim', motion='transition',
           body=(20, 80, 320, dict(twist=-14, lean=-12, crouch=0.6, head=-8)))
    refine('rush_knee', hit=0, prev='rush_chase')
    refine('rush_slam', hit=0, prev='rush_leap')
    refine('sukuna_takeover', motion='transition', body=(300, 700, 1300, dict(twist=14, lean=-24, crouch=-1.6, head=-26)), core=1.3)  # (a transformation)
    print("refined Yuji's clips")


if __name__ == '__main__':
    main()
