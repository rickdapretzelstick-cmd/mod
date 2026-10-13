#!/usr/bin/env python3
"""
Authors the hand-reworked player clips (assets/jjk/animations) from a small pose vocabulary, against the JJS wiki's
reference GIFs: real elbow and knee bends, anticipation before every hit, a snapped contact pose held a beat, overshoot
and settle. Durations, holds and priorities stay as the moves' server timing expects.

    python3 tools/anim_author.py            # rewrites the clips listed in CLIPS

Conventions (the mod's rig): rotations in degrees [x, y, z]; arm x < 0 raises it forward, arm z > 0 swings the right
arm out (mirrored for the left); forearm x < 0 folds the elbow; thigh x < 0 swings the leg forward; shin x > 0 bends the
knee; chest / hips y < 0 turn the right shoulder forward; hips x > 0 lean forward; root pos is (right, up, forward) px.
"""
import json
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'animations')


def arm(side, raise_, swing=0.0, out=0.0, elbow=0.0, wrist=0.0):
    s = 1 if side == 'right' else -1
    return {f'{side}Arm': {'rot': [raise_, swing * s, out * s]}, f'{side}Forearm': {'rot': [elbow, 0, 0]},
            f'{side}Hand': {'rot': [wrist, 0, 0]}}


def leg(side, swing, knee=0.0, out=0.0, foot=0.0):
    s = 1 if side == 'right' else -1
    return {f'{side}Thigh': {'rot': [swing, 0, out * s]}, f'{side}Shin': {'rot': [knee, 0, 0]}, f'{side}Foot': {'rot': [foot, 0, 0]}}


def body(twist=0.0, lean=0.0, hip_twist=None, head_down=0.0, head_turn=0.0, crouch=0.0, fwd=0.0, side=0.0, roll=0.0):
    ht = twist * 0.55 if hip_twist is None else hip_twist
    return {'chest': {'rot': [lean * 0.5, twist, roll]}, 'hips': {'rot': [lean, ht, 0]},
            'head': {'rot': [head_down, -twist * 0.6 + head_turn, 0]}, 'root': {'pos': [side, -crouch, fwd]}}


def pose(*parts):
    out = {}
    for p in parts:
        out.update(p)
    return out


def mirror(p):
    """The same pose, left for right."""
    out = {}
    for k, v in p.items():
        n = k.replace('right', '\0').replace('left', 'right').replace('\0', 'left')
        if 'rot' in v:
            r = v['rot']
            flip = k in ('chest', 'hips', 'head') or 'Arm' in k or 'Thigh' in k
            v = {'rot': [r[0], -r[1] if flip else r[1], -r[2] if flip else r[2]]}
        elif 'pos' in v:
            v = {'pos': [-v['pos'][0], v['pos'][1], v['pos'][2]]}
        out[n] = v
    return out


# --- Poses (right-handed fighter) ---

STANCE = pose(body(twist=14, lean=6, crouch=1.6, head_down=4), arm('right', -55, 8, 14, -105, 10), arm('left', -78, -6, 8, -98, 6),
              leg('right', 14, 22, 3, -6), leg('left', -16, 20, 3, 6))


def jab_wind(side='right'):
    p = pose(body(twist=24, lean=4, crouch=2.2, fwd=-1), arm('right', -42, 18, 22, -118, 14), arm('left', -80, -8, 8, -96, 6),
             leg('right', 18, 26, 3, -8), leg('left', -18, 24, 3, 8))
    return p if side == 'right' else mirror(p)


def jab_hit(side='right', reach=1.0):
    p = pose(body(twist=-30 * reach, lean=12, crouch=1.8, fwd=4.5 * reach), arm('right', -96, 4, 2, -4, -6), arm('left', -72, -14, 10, -110, 8),
             leg('right', 30, 14, 3, -10), leg('left', -30, 28, 3, 10))
    return p if side == 'right' else mirror(p)


def jab_settle(side='right'):
    p = pose(body(twist=-24, lean=10, crouch=1.8, fwd=3.8), arm('right', -91, 2, 4, -10, -4), arm('left', -74, -12, 10, -106, 8),
             leg('right', 27, 16, 3, -9), leg('left', -27, 27, 3, 9))
    return p if side == 'right' else mirror(p)


HOOK_WIND = pose(body(twist=38, lean=8, crouch=2.6, fwd=-0.5, roll=-4), arm('right', -60, 30, 62, -96, 10), arm('left', -82, -10, 8, -100, 6),
                 leg('right', 16, 30, 6, -8), leg('left', -20, 26, 4, 8))
HOOK_HIT = pose(body(twist=-46, lean=12, crouch=2.0, fwd=3.5, roll=4), arm('right', -88, -34, 54, -78, 0), arm('left', -64, -16, 14, -112, 8),
                leg('right', 26, 18, 6, -10), leg('left', -26, 30, 4, 10))
HOOK_SETTLE = pose(body(twist=-40, lean=10, crouch=2.0, fwd=3.0, roll=3), arm('right', -84, -28, 50, -84, 0), arm('left', -66, -14, 14, -108, 8),
                   leg('right', 24, 20, 6, -10), leg('left', -24, 29, 4, 10))

FIN_WIND = pose(body(twist=42, lean=14, crouch=4.0, fwd=-1.5), arm('right', -24, 30, 30, -125, 18), arm('left', -88, -20, 10, -60, 0),
                leg('right', 26, 46, 6, -14), leg('left', -26, 40, 4, 14))
FIN_HIT = pose(body(twist=-44, lean=22, crouch=2.6, fwd=7.5), arm('right', -100, 2, 0, 0, -10), arm('left', 28, 18, 14, -40, 0),
               leg('right', 44, 6, 3, -16), leg('left', -42, 46, 3, 14))
FIN_SETTLE = pose(body(twist=-38, lean=18, crouch=2.8, fwd=6.5), arm('right', -95, 0, 2, -6, -6), arm('left', 20, 16, 14, -48, 0),
                  leg('right', 40, 10, 3, -14), leg('left', -38, 44, 3, 13))

# Divergent Fist (JJS Vessel wiki GIF): deep coiled crouch, fist cocked low at the hip, guard arm out front;
# then the full lunge, the arm thrown straight through, the back leg driving.
DF_COIL = pose(body(twist=48, lean=20, crouch=5.0, fwd=-1.5, head_down=8), arm('right', 18, 30, 26, -110, 20), arm('left', -88, -26, 14, -40, 0),
               leg('right', 30, 52, 8, -16), leg('left', -30, 48, 6, 16))
DF_COIL_DEEP = pose(body(twist=56, lean=24, crouch=5.8, fwd=-2.2, head_down=10), arm('right', 26, 34, 28, -118, 24), arm('left', -92, -30, 14, -34, 0),
                    leg('right', 34, 58, 8, -18), leg('left', -34, 52, 6, 18))
DF_HIT = pose(body(twist=-52, lean=26, crouch=3.0, fwd=9.0, head_down=-2), arm('right', -104, 0, -2, 0, -12), arm('left', 34, 22, 18, -36, 0),
              leg('right', 50, 4, 4, -18), leg('left', -46, 52, 4, 16))
DF_FOLLOW = pose(body(twist=-46, lean=22, crouch=3.0, fwd=8.0), arm('right', -98, -4, 0, -8, -8), arm('left', 26, 18, 16, -44, 0),
                 leg('right', 46, 8, 4, -16), leg('left', -42, 48, 4, 15))

# Cursed Strikes (JJS GIF): low ready, the slide in, an alternating flurry, the spin, the high kick.
CS_READY = pose(body(twist=20, lean=22, crouch=4.4, head_down=10), arm('right', -30, 20, 24, -112, 10), arm('left', -64, -12, 12, -104, 8),
                leg('right', 22, 50, 8, -12), leg('left', -26, 46, 6, 12))
CS_SLIDE = pose(body(twist=10, lean=34, crouch=6.0, fwd=2.0, head_down=-4), arm('right', 34, 20, 30, -30, 0), arm('left', -60, -10, 20, -90, 0),
                leg('right', 40, 70, 10, -10), leg('left', -50, 30, 8, 18))
KICK_WIND = pose(body(twist=30, lean=-6, crouch=2.0, roll=-10), arm('right', -40, 20, 40, -90, 0), arm('left', -60, -20, 30, -80, 0),
                 leg('right', -60, 100, 20, 20), leg('left', -6, 24, 4, 6))
KICK_HIT = pose(body(twist=-20, lean=-22, crouch=0.6, fwd=2.0, roll=-22), arm('right', 20, 30, 60, -40, 0), arm('left', -40, -10, 50, -70, 0),
                leg('right', -98, 4, 34, 30), leg('left', 6, 18, 4, 4))
KICK_SETTLE = pose(body(twist=-16, lean=-14, crouch=1.2, fwd=2.0, roll=-14), arm('right', 10, 26, 54, -50, 0), arm('left', -46, -10, 44, -76, 0),
                   leg('right', -84, 16, 30, 26), leg('left', 4, 20, 4, 4))


def key(t, p, ease=None):
    k = {'t': t, 'bones': p}
    if ease:
        k['ease'] = ease
    return k


def clip(group, name, duration, keys, priority, blend_in, blend_out, hold=False):
    d = {'name': name, 'duration': duration}
    if hold:
        d['hold'] = True
    d.update({'blendIn': blend_in, 'blendOut': blend_out, 'priority': priority,
              'authored': 'tools/anim_author.py (reference: JJS wiki GIFs)', 'keys': keys})
    with open(os.path.join(OUT, group, name + '.json'), 'w') as f:
        json.dump(d, f, indent=1)


def strike(group, name, duration, wind, hit, settle, rest, t_wind, t_hit, priority='ATTACK', blend_in=40, blend_out=150):
    """Wind-up (eased in), a hard snap to contact, held a beat (the hit-stop pose), settled, then back to guard."""
    clip(group, name, duration, [key(0, STANCE), key(t_wind, wind, 'EASE_OUT'), key(t_hit, hit, 'EASE_IN'),
                                 key(t_hit + 70, settle, 'EASE_OUT'), key(duration, rest, 'EASE_IN_OUT')], priority, blend_in, blend_out)


def main():
    # The M1 chain: jab, cross, hook, and the knockback finisher.
    strike('common', 'light_1', 450, jab_wind('right'), jab_hit('right'), jab_settle('right'), STANCE, 70, 130)
    strike('common', 'light_2', 450, jab_wind('left'), jab_hit('left', 1.1), jab_settle('left'), STANCE, 70, 130)
    strike('common', 'light_3', 500, HOOK_WIND, HOOK_HIT, HOOK_SETTLE, STANCE, 110, 190)
    clip('common', 'light_4', 700, [key(0, STANCE), key(140, FIN_WIND, 'EASE_OUT'), key(200, FIN_HIT, 'EASE_IN'), key(330, FIN_SETTLE, 'EASE_OUT'),
                                    key(700, STANCE, 'EASE_IN_OUT')], 'ATTACK', 25, 200)
    # Divergent Fist: the coil (held while it charges), then the punch.
    clip('yuji', 'divergent_windup', 450, [key(0, STANCE), key(220, DF_COIL, 'EASE_OUT'), key(450, DF_COIL_DEEP, 'EASE_IN_OUT')], 'SPECIAL', 50, 100, hold=True)
    clip('yuji', 'divergent_punch', 700, [key(0, DF_COIL_DEEP), key(60, DF_HIT, 'EASE_IN'), key(220, DF_FOLLOW, 'EASE_OUT'),
                                          key(700, STANCE, 'EASE_IN_OUT')], 'SPECIAL', 15, 250)
    # Cursed Strikes.
    clip('yuji', 'cursed_strikes_ready', 400, [key(0, STANCE), key(400, CS_READY, 'EASE_OUT')], 'SPECIAL', 50, 100, hold=True)
    clip('yuji', 'cursed_strikes_slide', 200, [key(0, CS_READY), key(200, CS_SLIDE, 'EASE_OUT')], 'MOVEMENT', 25, 100, hold=True)
    flurry = [key(0, CS_READY)]
    t, side = 50, 'right'
    while t + 200 <= 1500:
        flurry += [key(t, jab_wind(side), 'EASE_OUT'), key(t + 60, jab_hit(side, 1.05), 'EASE_IN'), key(t + 120, jab_settle(side), 'EASE_OUT')]
        t += 180
        side = 'left' if side == 'right' else 'right'
    flurry.append(key(1500, STANCE, 'EASE_IN_OUT'))
    clip('yuji', 'cursed_strikes_flurry', 1500, flurry, 'SPECIAL', 25, 150)
    clip('yuji', 'cursed_strikes_kick', 600, [key(0, STANCE), key(110, KICK_WIND, 'EASE_OUT'), key(170, KICK_HIT, 'EASE_IN'),
                                              key(300, KICK_SETTLE, 'EASE_OUT'), key(600, STANCE, 'EASE_IN_OUT')], 'SPECIAL', 15, 200)
    print('wrote the M1 chain, Divergent Fist and Cursed Strikes clips')


if __name__ == '__main__':
    main()
