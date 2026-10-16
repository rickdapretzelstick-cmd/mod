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
# Cursed Strikes against the Roblox reference video: he reaches out with the right first, then drops into the low ready;
# the dash is pitched far forward with the right fist leading and the left arm and leg trailing.
CS_REACH = pose(body(twist=18, lean=6, crouch=1.4, head_down=-2), arm('right', -88, 4, 6, -12, -4), arm('left', -40, -10, 18, -90, 6),
                leg('right', 12, 20, 3, -6), leg('left', -12, 20, 3, 6))
CS_DASH = pose(body(twist=-14, lean=40, crouch=5.0, fwd=3.0, head_down=-30), arm('right', -124, 6, 8, -10, -6), arm('left', 42, -14, 20, -40, 0),
               leg('right', -36, 40, 6, 10), leg('left', 56, 30, 4, -12))
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
    # The windup (strikesWindup 9 ticks): the reach, then down into the ready; the dash snaps in fast.
    clip('yuji', 'cursed_strikes_ready', 450, [key(0, STANCE), key(200, CS_REACH, 'EASE_OUT'), key(420, CS_READY, 'EASE_IN')], 'SPECIAL', 50, 100, hold=True)
    clip('yuji', 'cursed_strikes_slide', 120, [key(0, CS_READY), key(120, CS_DASH, 'EASE_OUT')], 'MOVEMENT', 25, 100, hold=True)
    flurry = [key(0, CS_READY)]
    t, side = 50, 'right'
    while t + 200 <= 1500:
        flurry += [key(t, jab_wind(side), 'EASE_OUT'), key(t + 60, jab_hit(side, 1.05), 'EASE_IN'), key(t + 120, jab_settle(side), 'EASE_OUT')]
        t += 180
        side = 'left' if side == 'right' else 'right'
    flurry.append(key(1500, STANCE, 'EASE_IN_OUT'))
    clip('yuji', 'cursed_strikes_flurry', 1500, flurry, 'SPECIAL', 25, 150)
    # The last punch of the rush (strikesFinalAt 4 ticks = 200 ms to contact): wound back, driven through, held, back to guard.
    clip('yuji', 'cursed_strikes_final', 650, [key(0, STANCE), key(120, FIN_WIND, 'EASE_OUT'), key(200, FIN_HIT, 'EASE_IN'), key(380, FIN_SETTLE, 'EASE_OUT'),
                                               key(650, STANCE, 'EASE_IN_OUT')], 'SPECIAL', 30, 200)
    clip('yuji', 'cursed_strikes_kick', 600, [key(0, STANCE), key(110, KICK_WIND, 'EASE_OUT'), key(170, KICK_HIT, 'EASE_IN'),
                                              key(300, KICK_SETTLE, 'EASE_OUT'), key(600, STANCE, 'EASE_IN_OUT')], 'SPECIAL', 15, 200)
    print('wrote the M1 chain, Divergent Fist and Cursed Strikes clips')


# --- Variants (pass 2): everything else in the M1, Divergent Fist, Cursed Strikes and Manji Kick families ---

def tucked(p, lift=1.5):
    """The same upper body in the air: legs tucked under, no crouch, a little lift."""
    q = dict(p)
    q.update(leg('right', -40, 78, 6, 10))
    q.update(leg('left', -18, 62, 6, 8))
    r = q['root']['pos']
    q['root'] = {'pos': [r[0], lift, r[2]]}
    return q


UPPER_WIND = pose(body(twist=30, lean=18, crouch=5.0, head_down=6), arm('right', 24, 20, 20, -120, 16), arm('left', -80, -14, 12, -100, 6),
                  leg('right', 28, 56, 6, -14), leg('left', -26, 50, 6, 14))
UPPER_HIT = pose(body(twist=-24, lean=-14, crouch=-1.5, fwd=2.5, head_down=-14), arm('right', -168, 6, 8, -26, -10), arm('left', -30, -10, 20, -110, 6),
                 leg('right', 22, 8, 4, -16), leg('left', -14, 12, 4, 10))
UPPER_SETTLE = pose(body(twist=-20, lean=-10, crouch=-1.0, fwd=2.0, head_down=-10), arm('right', -160, 4, 8, -34, -8), arm('left', -34, -10, 20, -106, 6),
                    leg('right', 20, 12, 4, -14), leg('left', -14, 14, 4, 10))
SLAM_WIND = pose(body(twist=0, lean=-14, crouch=-1.0, head_down=-10), arm('right', -172, 0, 10, -70, 10), arm('left', -172, 0, 10, -70, 10),
                 leg('right', -40, 80, 6, 10), leg('left', -20, 64, 6, 8))
SLAM_HIT = pose(body(twist=0, lean=42, crouch=3.5, fwd=2.0, head_down=14), arm('right', -40, 0, 6, -12, -10), arm('left', -40, 0, 6, -12, -10),
                leg('right', -30, 70, 6, 10), leg('left', -10, 50, 6, 8))
STOMP_WIND = pose(body(twist=8, lean=-6, crouch=0.5, head_down=12), arm('right', -40, 10, 40, -60, 0), arm('left', -50, -10, 36, -70, 0),
                  leg('right', -95, 100, 6, 18), leg('left', 4, 14, 4, 4))
STOMP_HIT = pose(body(twist=4, lean=18, crouch=3.0, head_down=26), arm('right', -20, 10, 46, -40, 0), arm('left', -26, -10, 44, -46, 0),
                 leg('right', -10, 14, 6, -6), leg('left', 14, 40, 4, 8))
RUN_WIND = pose(body(twist=26, lean=28, crouch=2.0, fwd=1.0), arm('right', 40, 20, 18, -70, 10), arm('left', -70, -10, 10, -90, 6),
                leg('right', 40, 60, 4, -10), leg('left', -50, 40, 4, 12))
DROPKICK = pose(body(twist=-10, lean=-58, crouch=-2.0, fwd=4.0, head_down=30), arm('right', 40, 10, 50, -20, 0), arm('left', 30, -10, 50, -20, 0),
                leg('right', -92, 0, 4, 20), leg('left', -80, 14, 4, 20))
DIVE = pose(body(twist=-6, lean=-36, crouch=-1.0, fwd=3.0, head_down=24), arm('right', 20, 10, 40, -40, 0), arm('left', -60, -10, 40, -60, 0),
            leg('right', -72, 6, 6, 20), leg('left', -30, 70, 6, 10))
HOP = pose(body(twist=10, lean=10, crouch=-2.0, head_down=-6), arm('right', -120, 10, 30, -60, 0), arm('left', -110, -10, 30, -60, 0),
           leg('right', -60, 96, 6, 16), leg('left', -40, 90, 6, 14))
LAND = pose(body(twist=12, lean=34, crouch=6.0, fwd=1.0, head_down=8), arm('right', -24, 16, 24, -6, -10), arm('left', -70, -20, 30, -60, 0),
            leg('right', 30, 76, 10, -18), leg('left', -42, 64, 8, 16))
FLOOR_WIND = pose(body(twist=30, lean=40, crouch=5.0, head_down=24), arm('right', -150, 20, 20, -80, 0), arm('left', -60, -10, 30, -60, 0),
                  leg('right', 30, 80, 10, -16), leg('left', -40, 70, 8, 14))
FLOOR_HIT = pose(body(twist=-20, lean=58, crouch=6.5, fwd=2.0, head_down=34), arm('right', -40, 6, 8, 0, -10), arm('left', -30, -20, 40, -40, 0),
                 leg('right', 34, 90, 10, -20), leg('left', -44, 76, 8, 14))
RECOIL = pose(body(twist=26, lean=-16, crouch=2.0, fwd=-3.0, head_down=-12), arm('right', -40, 30, 40, -60, 10), arm('left', -30, -20, 40, -60, 10),
              leg('right', 30, 26, 6, -8), leg('left', -10, 20, 4, 4))
SPIN_WIND = pose(body(twist=70, lean=10, crouch=3.0, roll=-6), arm('right', -60, 30, -20, -130, 0), arm('left', -70, -20, 30, -90, 0),
                 leg('right', 24, 40, 6, -10), leg('left', -24, 36, 6, 10))
SPIN_HIT = pose(body(twist=-90, lean=6, crouch=2.0, fwd=2.0, roll=6), arm('right', -92, -10, 86, -10, 0), arm('left', -60, -20, 30, -100, 0),
                leg('right', 20, 30, 6, -8), leg('left', -26, 34, 6, 10))
# Manji Kick (JJS GIF): the low counter guard, the duck under the blow, then the spinning rising kick with the body thrown
# sideways and the kicking leg straight up (the manji), the arms flung out for balance.
MANJI_STANCE = pose(body(twist=10, lean=14, crouch=3.4, head_down=6), arm('right', -84, -20, -16, -118, 10), arm('left', -78, -24, -18, -112, 10),
                    leg('right', 18, 40, 14, -10), leg('left', -20, 36, 12, 10))
MANJI_DUCK = pose(body(twist=40, lean=30, crouch=6.4, side=-2.0, roll=22, head_down=14), arm('right', -40, 20, 40, -100, 0), arm('left', -70, -10, 20, -110, 0),
                  leg('right', 30, 80, 14, -14), leg('left', -36, 70, 12, 14))
MANJI_CHAMBER = pose(body(twist=70, lean=-10, crouch=2.0, roll=-30, head_down=-6), arm('right', -30, 20, 70, -40, 0), arm('left', -20, -20, 80, -30, 0),
                     leg('right', -84, 110, 24, 20), leg('left', 0, 26, 4, 4))
MANJI_HIT = pose(body(twist=-30, lean=-30, crouch=-0.5, fwd=1.5, roll=-52, head_down=-10), arm('right', 10, 30, 84, -10, 0), arm('left', -10, -20, 96, -14, 0),
                 leg('right', -158, 0, 36, 30), leg('left', 8, 16, 6, 4))
MANJI_HOLD = pose(body(twist=-26, lean=-26, crouch=0.0, fwd=1.5, roll=-46, head_down=-8), arm('right', 6, 28, 80, -16, 0), arm('left', -12, -20, 90, -18, 0),
                  leg('right', -146, 10, 34, 28), leg('left', 6, 18, 6, 4))
FACE_KICK = pose(body(twist=-12, lean=-24, crouch=-0.5, fwd=2.0, roll=-8, head_down=-12), arm('right', 20, 20, 70, -20, 0), arm('left', -10, -20, 70, -20, 0),
                 leg('right', -170, 0, 6, 34), leg('left', 10, 14, 4, 4))
SWOOP = pose(body(twist=-20, lean=44, crouch=5.0, fwd=3.0, roll=10, head_down=-10), arm('right', 50, 20, 30, -20, 0), arm('left', 46, -20, 30, -20, 0),
             leg('right', 44, 70, 8, -14), leg('left', -54, 50, 8, 18))
GRAB = pose(body(twist=-10, lean=16, crouch=2.0, fwd=3.0), arm('right', -96, -16, -10, -36, 0), arm('left', -96, 16, -10, -36, 0),
            leg('right', 24, 26, 6, -8), leg('left', -24, 30, 6, 8))
GRAB_SPIN = pose(body(twist=80, lean=6, crouch=2.6, fwd=1.0, roll=-10), arm('right', -86, 40, 30, -50, 0), arm('left', -100, 10, 0, -40, 0),
                 leg('right', 10, 30, 14, -6), leg('left', -30, 40, 14, 10))
THROW_UP = pose(body(twist=10, lean=-22, crouch=-1.0, head_down=-24), arm('right', -170, 0, 16, -40, 0), arm('left', -170, 0, 16, -40, 0),
                leg('right', 16, 10, 6, -8), leg('left', -16, 12, 6, 8))
THROW_DOWN = pose(body(twist=0, lean=50, crouch=5.0, fwd=3.0, head_down=30), arm('right', -30, 0, 10, -6, -10), arm('left', -30, 0, 10, -6, -10),
                  leg('right', 40, 60, 8, -16), leg('left', -40, 70, 8, 14))


def meta(group, name):
    return json.load(open(os.path.join(OUT, group, name + '.json')))


def rework(group, name, keys):
    """Rewrites a clip's keys, keeping its length, hold, blends and priority exactly (the move's timing is the server's)."""
    m = meta(group, name)
    d = m['duration']
    keys = [dict(k, t=min(d, max(0, int(round(k['t'] * d if isinstance(k['t'], float) and k['t'] <= 1.0 else k['t']))))) for k in keys]
    clip(group, name, d, keys, m['priority'], m.get('blendIn', 40), m.get('blendOut', 150), hold=m.get('hold', False))


def f(x):
    """A key time as a share of the clip's length."""
    return float(x)


def variants():
    a = 'common'
    # Air M1s: the same strikes, legs tucked.
    rework(a, 'air_1', [key(0, tucked(STANCE)), key(70, tucked(jab_wind('right')), 'EASE_OUT'), key(130, tucked(jab_hit('right')), 'EASE_IN'),
                        key(200, tucked(jab_settle('right')), 'EASE_OUT'), key(f(1.0), tucked(STANCE), 'EASE_IN_OUT')])
    rework(a, 'air_2', [key(0, tucked(STANCE)), key(70, tucked(jab_wind('left')), 'EASE_OUT'), key(130, tucked(jab_hit('left', 1.1)), 'EASE_IN'),
                        key(200, tucked(jab_settle('left')), 'EASE_OUT'), key(f(1.0), tucked(STANCE), 'EASE_IN_OUT')])
    rework(a, 'air_3', [key(0, tucked(STANCE)), key(110, tucked(HOOK_WIND), 'EASE_OUT'), key(190, tucked(HOOK_HIT), 'EASE_IN'),
                        key(260, tucked(HOOK_SETTLE), 'EASE_OUT'), key(f(1.0), tucked(STANCE), 'EASE_IN_OUT')])
    rework(a, 'uppercut', [key(0, STANCE), key(110, UPPER_WIND, 'EASE_OUT'), key(170, UPPER_HIT, 'EASE_IN'), key(280, UPPER_SETTLE, 'EASE_OUT'),
                           key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(a, 'downslam', [key(0, tucked(STANCE)), key(120, SLAM_WIND, 'EASE_OUT'), key(190, SLAM_HIT, 'EASE_IN'), key(330, SLAM_HIT, 'EASE_OUT'),
                           key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(a, 'stomp', [key(0, STANCE), key(90, STOMP_WIND, 'EASE_OUT'), key(150, STOMP_HIT, 'EASE_IN'), key(260, STOMP_HIT, 'EASE_OUT'),
                        key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(a, 'sprint', [key(0, RUN_WIND), key(150, FIN_WIND, 'EASE_OUT'), key(220, FIN_HIT, 'EASE_IN'), key(420, FIN_SETTLE, 'EASE_OUT'),
                         key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(a, 'heavy_charge', [key(0, STANCE), key(f(1.0), FIN_WIND, 'EASE_OUT')])
    rework(a, 'heavy', [key(0, FIN_WIND), key(90, DF_COIL_DEEP, 'EASE_OUT'), key(150, FIN_HIT, 'EASE_IN'), key(330, FIN_SETTLE, 'EASE_OUT'),
                        key(f(1.0), STANCE, 'EASE_IN_OUT')])
    y = 'yuji'
    # Black Flash strikes (Divergent Fist's timed press, and its chain off the back).
    rework(y, 'black_flash_punch', [key(0, DF_COIL_DEEP), key(55, DF_HIT, 'EASE_IN'), key(260, DF_FOLLOW, 'EASE_OUT'), key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(y, 'black_flash', [key(0, STANCE), key(200, DF_COIL_DEEP, 'EASE_OUT'), key(260, DF_HIT, 'EASE_IN'), key(480, DF_FOLLOW, 'EASE_OUT'),
                              key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(y, 'black_flash_uppercut', [key(0, UPPER_WIND), key(80, UPPER_HIT, 'EASE_IN'), key(300, UPPER_SETTLE, 'EASE_OUT'), key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(y, 'black_flash_dropkick', [key(0, STANCE), key(110, HOP, 'EASE_OUT'), key(170, DROPKICK, 'EASE_IN'), key(420, DROPKICK, 'EASE_OUT'),
                                       key(f(1.0), LAND, 'EASE_IN_OUT')])
    # Cursed Strikes' other beats.
    rework(y, 'cursed_strikes_hop', [key(0, CS_READY), key(f(1.0), HOP, 'EASE_OUT')])
    rework(y, 'cursed_strikes_dropkick', [key(0, HOP), key(f(1.0), DIVE, 'EASE_OUT')])
    rework(y, 'cursed_strikes_land', [key(0, DIVE), key(90, LAND, 'EASE_IN'), key(260, LAND, 'EASE_OUT'), key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(y, 'cursed_strikes_floor', [key(0, STANCE), key(140, FLOOR_WIND, 'EASE_OUT'), key(210, FLOOR_HIT, 'EASE_IN'), key(330, FLOOR_HIT, 'EASE_OUT'),
                                       key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(y, 'cursed_strikes_blocked', [key(0, jab_hit('right')), key(110, RECOIL, 'EASE_OUT'), key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(y, 'cursed_strikes_spin', [key(0, STANCE), key(150, SPIN_WIND, 'EASE_OUT'), key(220, SPIN_HIT, 'EASE_IN'), key(380, SPIN_HIT, 'EASE_OUT'),
                                      key(f(1.0), STANCE, 'EASE_IN_OUT')])
    # Manji Kick and its variants.
    rework(y, 'manji_stance', [key(0, STANCE), key(f(1.0), MANJI_STANCE, 'EASE_OUT')])
    rework(y, 'manji_dodge', [key(0, MANJI_STANCE), key(110, MANJI_DUCK, 'EASE_OUT'), key(f(1.0), MANJI_DUCK, 'EASE_IN_OUT')])
    rework(y, 'manji_kick', [key(0, MANJI_DUCK), key(140, MANJI_CHAMBER, 'EASE_OUT'), key(210, MANJI_HIT, 'EASE_IN'), key(400, MANJI_HOLD, 'EASE_OUT'),
                             key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(y, 'manji_face_kick', [key(0, MANJI_DUCK), key(100, MANJI_CHAMBER, 'EASE_OUT'), key(160, FACE_KICK, 'EASE_IN'), key(f(1.0), FACE_KICK, 'EASE_OUT')])
    rework(y, 'manji_recover', [key(0, MANJI_HOLD), key(f(1.0), STANCE, 'EASE_IN_OUT')])
    rework(y, 'manji_swoop', [key(0, MANJI_STANCE), key(180, SWOOP, 'EASE_OUT'), key(f(1.0), SWOOP, 'EASE_IN_OUT')])
    rework(y, 'manji_grapple_spin', [key(0, SWOOP), key(150, GRAB, 'EASE_OUT'), key(400, GRAB_SPIN, 'EASE_IN_OUT'), key(f(1.0), THROW_UP, 'EASE_OUT')])
    rework(y, 'manji_slam', [key(0, THROW_UP), key(140, THROW_DOWN, 'EASE_IN'), key(300, THROW_DOWN, 'EASE_OUT'), key(f(1.0), STANCE, 'EASE_IN_OUT')])
    print('wrote the variants: air M1s, uppercut, downslam, stomp, sprint, heavy, Black Flash strikes, Cursed Strikes, Manji Kick')


if __name__ == '__main__':
    main()
    variants()
