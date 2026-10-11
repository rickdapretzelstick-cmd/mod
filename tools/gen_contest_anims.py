"""The player's side of a strength contest (animations/contest/*.json): a grapple with the curse that grabbed them.

    python3 tools/gen_contest_anims.py

Locked face to face, both hands on the curse, the lead leg forward and the back leg driving. Every verdict is a beat of
the exchange, and each tap key has its own (the HUD names them PUSH, BRACE, HEAVE):
- grapple_hold: the grab lands and the grip holds (trembling with the strain).
- grapple_push / grapple_brace / grapple_heave: a good tap on the left / centre / right key: a two-handed shove, a
  shoulder dug in low, a lift that pries its hold up.
- grapple_strain: a miss: driven back, arms buckling.
- grapple_throw: broken free: loaded on the back hip, then thrown off to the side.
- grapple_thrown: overpowered: hurled backwards off it.
All but the last two end in the grip and hold it until the next replaces it (StrengthContests plays them).

Bone angles are degrees (x: an arm's or thigh's negative x raises it forward; y swings it across; z out to the side),
root position is in pixels (z forward).
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'animations', 'contest')


def k(t, bones, ease='EASE_IN_OUT'):
    return {'t': t, 'ease': ease, 'bones': {b: ({'pos': v[1]} if v[0] == 'pos' else {'rot': v[1]}) for b, v in bones.items()}}


def r(x, y=0, z=0):
    return ('rot', [x, y, z])


def p(x, y=0, z=0):
    return ('pos', [x, y, z])


def merge(*ds):
    out = {}
    for d in ds:
        out.update(d)
    return out


def clip(name, duration, keys, hold=True, blend_in=60, blend_out=180, tremble=0.0, note=None):
    c = {'name': name, 'duration': duration}
    if note:
        c['note'] = note
    if hold:
        c['hold'] = True
    if tremble:
        c['tremble'] = tremble
    c.update({'blendIn': blend_in, 'blendOut': blend_out, 'priority': 'SPECIAL', 'keys': keys})
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(c, f, indent=2)
        f.write('\n')


# The grip: hands forward at chest height on the curse, leaning into it, the left leg forward and the right driving.
GRIP = {'head': r(8), 'chest': r(18, -6), 'hips': r(10, -4),
        'rightArm': r(-78, -18, 6), 'rightForearm': r(-34), 'rightHand': r(-10),
        'leftArm': r(-82, 16, -6), 'leftForearm': r(-30), 'leftHand': r(-10),
        'leftThigh': r(-38, 0, -8), 'leftShin': r(34), 'rightThigh': r(30, 0, 8), 'rightShin': r(14),
        'root': p(0, -2.5, 1)}

PUSH = merge(GRIP, {'head': r(2), 'chest': r(24, 0), 'rightArm': r(-88, -10, 4), 'rightForearm': r(-6), 'leftArm': r(-90, 10, -4),
                    'leftForearm': r(-6), 'rightThigh': r(42, 0, 8), 'rightShin': r(6), 'leftThigh': r(-30, 0, -8), 'root': p(0, -2.2, 3)})
BRACE = merge(GRIP, {'head': r(14, 10), 'chest': r(30, 24, -6), 'hips': r(16, 12), 'rightArm': r(-60, -30, 12), 'rightForearm': r(-60),
                     'leftArm': r(-70, 30, -4), 'leftForearm': r(-50), 'leftThigh': r(-56, 0, -12), 'leftShin': r(60),
                     'rightThigh': r(26, 0, 12), 'rightShin': r(36), 'root': p(0, -4.5, 1.5)})
HEAVE = merge(GRIP, {'head': r(-16), 'chest': r(-8, -4), 'hips': r(-2), 'rightArm': r(-128, -14, 8), 'rightForearm': r(-26),
                     'leftArm': r(-132, 12, -8), 'leftForearm': r(-22), 'leftThigh': r(-24, 0, -8), 'leftShin': r(14),
                     'rightThigh': r(16, 0, 8), 'rightShin': r(4), 'root': p(0, -0.8, 1.5)})
STRAIN = merge(GRIP, {'head': r(-12, -8, 6), 'chest': r(-6, -14, 4), 'hips': r(2, -8), 'rightArm': r(-64, -26, 14), 'rightForearm': r(-74),
                      'leftArm': r(-70, 24, -10), 'leftForearm': r(-70), 'leftThigh': r(-18, 0, -10), 'leftShin': r(40),
                      'rightThigh': r(46, 0, 10), 'rightShin': r(46), 'root': p(0, -4, -2.5)})


def beat(name, pose, at, hold_to, end, note):
    clip(name, end, [k(0, GRIP), k(at, pose, 'EASE_OUT'), k(hold_to, pose), k(end, GRIP)], blend_in=30, tremble=1.4, note=note)


clip('grapple_hold', 260, [k(0, merge(GRIP, {'rightArm': r(-100, -10, 8), 'leftArm': r(-104, 10, -8), 'root': p(0, -1.5, 2)})),
                           k(260, GRIP, 'OVERSHOOT')], blend_in=50, tremble=1.4,
     note='The grab lands: arms up to meet it, then dug in.')
beat('grapple_push', PUSH, 100, 190, 440, 'A good PUSH: both arms drive out, the back leg straight behind it.')
beat('grapple_brace', BRACE, 120, 220, 480, 'A good BRACE: dropped low, the shoulder dug into it.')
beat('grapple_heave', HEAVE, 130, 240, 500, 'A good HEAVE: up on the toes, prying its hold up off you.')
beat('grapple_strain', STRAIN, 150, 300, 620, 'A miss: driven back, the arms buckling under it.')

LOAD = merge(GRIP, {'head': r(4, 24), 'chest': r(20, 38, -6), 'hips': r(10, 24), 'rightArm': r(-60, -40, 20), 'rightForearm': r(-50),
                    'leftArm': r(-96, 40, -4), 'leftForearm': r(-40), 'root': p(0, -3.5, 0)})
THROW = merge(GRIP, {'head': r(4, -30), 'chest': r(14, -46, 6), 'hips': r(8, -30), 'rightArm': r(-100, 30, 40), 'rightForearm': r(-10),
                     'leftArm': r(-40, -30, -30), 'leftForearm': r(-20), 'leftThigh': r(-30, 0, -10), 'rightThigh': r(20, 0, 14),
                     'root': p(0, -2.5, 2)})
clip('grapple_throw', 760, [k(0, GRIP), k(200, LOAD, 'EASE_IN'), k(330, THROW, 'EASE_OUT'), k(470, THROW),
                            k(760, {'head': r(0), 'chest': r(4, -10), 'hips': r(2, -6), 'rightArm': r(-30, 0, 14), 'rightForearm': r(-20),
                                    'leftArm': r(-24, 0, -10), 'leftForearm': r(-20), 'leftThigh': r(-10), 'leftShin': r(10),
                                    'rightThigh': r(8), 'rightShin': r(6), 'root': p(0, -1, 0)})],
     hold=False, blend_in=30, blend_out=240, note='Broken free: loaded on the back hip, then thrown off to the side.')

HIT = {'head': r(-28), 'chest': r(-26, 10), 'hips': r(-10), 'rightArm': r(-150, 0, 40), 'rightForearm': r(-20), 'leftArm': r(-140, 0, -46),
       'leftForearm': r(-20), 'leftThigh': r(-30, 0, -10), 'leftShin': r(30), 'rightThigh': r(-10, 0, 10), 'rightShin': r(40), 'root': p(0, -1, -2)}
FLUNG = {'head': r(-20), 'chest': r(-34, 6, 8), 'hips': r(-20), 'rightArm': r(-110, 0, 70), 'rightForearm': r(-10), 'leftArm': r(-100, 0, -76),
         'leftForearm': r(-10), 'leftThigh': r(-70, 0, -14), 'leftShin': r(60), 'rightThigh': r(-56, 0, 14), 'rightShin': r(70), 'root': p(0, 0, -3)}
clip('grapple_thrown', 820, [k(0, GRIP), k(110, HIT, 'EASE_OUT'), k(420, FLUNG), k(620, FLUNG),
                             k(820, {'head': r(10), 'chest': r(20), 'hips': r(10), 'rightArm': r(-20, 0, 30), 'leftArm': r(-20, 0, -30),
                                     'leftThigh': r(-40), 'leftShin': r(60), 'rightThigh': r(10), 'rightShin': r(40), 'root': p(0, -3, 0)})],
     hold=False, blend_in=20, blend_out=260, note='Overpowered: hurled backwards off it.')
