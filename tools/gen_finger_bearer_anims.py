#!/usr/bin/env python3
"""Writes the Finger Bearer's animation clips (rig "cursed_spirit", the supplied cursed_spirit.bbmodel's own bones).

    python3 tools/gen_finger_bearer_anims.py [--preview out_dir]

Bone rotations are Blockbench animator degrees [x, y, z] on the model's real bones (x -90 swings a hanging arm forward,
y + turns the right arm in toward the centre, jaw x + opens the mouth, chest x + hunches forward); body pos is pixels
[x, up, forward-is-negative-z]. The model rests in a stiff A-pose, so every clip starts from STANCE: a hunched, knees-bent,
arms-forward crouch that reads as heavy and unstable. Legs are posed by a two-bone solve that keeps the feet on the floor
when the hips drop.

Timings are the server's ticks (1 tick = 50 ms, FingerBearerEntity): every release or impact key sits on the tick the
damage happens. --preview also draws each clip's keys with tools/bb_pose_preview.py.
"""
import json
import math
import os
import subprocess
import sys

HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'jjk')
OUT = os.path.join(ASSETS, 'animations', 'cursed_spirit')
MODEL = os.path.join(ASSETS, 'models', 'bb', 'cursed_spirit.bbmodel')
TEXTURE = os.path.join(ASSETS, 'textures', 'entity', 'cursed_spirit.png')
TICK = 50

# Server timings (ticks), kept in step with FingerBearerEntity.
SHOT_FIRE, SHOT_END = 12, 24
BLAST_FIRE, BLAST_END = 40, 76
BURST_FIRE, BURST_END = 18, 36
RUSH_WINDUP, STRIKE_HIT, STRIKE_END, MISS_END = 14, 4, 18, 36
SMASH_HIT, SMASH_END = 22, 44
ROAR = 30
DEATH = 44

THIGH = SHIN = 14.0  # px: hip 31 -> knee 17 -> ankle 3


def legs(drop=0.0, right_fwd=0.0, left_fwd=0.0, splay=4.0):
    """Both legs for hips lowered by `drop` px with each foot `fwd` px ahead of its hip (feet stay flat on the floor)."""
    out = {}
    for side, fwd, z in (('right', right_fwd, splay), ('left', left_fwd, -splay)):
        h = THIGH + SHIN - drop
        L = min(THIGH + SHIN - 0.01, math.hypot(fwd, h))
        inner = math.acos(max(-1.0, min(1.0, (THIGH ** 2 + SHIN ** 2 - L * L) / (2 * THIGH * SHIN))))
        bend = math.pi - inner
        # Thigh angle forward of straight down: toward the foot, plus half the knee bend (knees go forward).
        a_thigh = math.atan2(fwd, h) + math.asin(SHIN * math.sin(bend) / L) if L > 1e-6 else 0
        a_foot = a_thigh - bend
        d = math.degrees
        out[side + '_thigh'] = {'rot': [round(-d(a_thigh), 1), 0, z]}
        out[side + '_shin'] = {'rot': [round(d(bend), 1), 0, 0]}
        out[side + '_foot'] = {'rot': [round(d(a_foot), 1), 0, -z * 0.5]}
    out['body'] = dict(out.get('body', {}), pos=[0, -round(drop, 2), 0])
    return out


# Rest directions of the arm bones (model px): the supplied model holds its arms out in an A-pose.
UPPER_REST = {'right': (-6.9, -8.0, 0.0), 'left': (6.9, -8.0, 0.0)}
FORE_REST = {'right': (-8.5, -8.8, -0.3), 'left': (8.5, -8.8, -0.3)}


def _norm(v):
    l = math.sqrt(sum(c * c for c in v))
    return tuple(c / l for c in v)


def _arc(a, b):
    """Rotation matrix taking unit vector a onto unit vector b by the shortest arc."""
    a, b = _norm(a), _norm(b)
    v = (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    c = sum(x * y for x, y in zip(a, b))
    if c < -0.9999:
        return [[-1, 0, 0], [0, -1, 0], [0, 0, 1]]
    k = 1 / (1 + c)
    vx = [[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]]
    vx2 = [[sum(vx[i][m] * vx[m][j] for m in range(3)) for j in range(3)] for i in range(3)]
    return [[(1 if i == j else 0) + vx[i][j] + vx2[i][j] * k for j in range(3)] for i in range(3)]


def _mul(m, v):
    return tuple(sum(m[i][j] * v[j] for j in range(3)) for i in range(3))


def _t(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


def _animator(m):
    """Animator degrees for a bone rotation matrix (the renderer builds Rz*Ry*Rx from (z, -y, -x))."""
    b = -math.asin(max(-1.0, min(1.0, m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    c = math.atan2(m[1][0], m[0][0])
    return [round(-math.degrees(a), 1), round(-math.degrees(b), 1), round(math.degrees(c), 1)]


def _dir(side, d):
    """(forward, up, outward) in the chest's frame to model axes (the model faces -z; outward is -x on the right)."""
    f, u, o = d
    return (-o if side == 'right' else o, u, -f)


def arm(side, elbow, hand, wrist=(0, 0, 0)):
    """One arm pointed by direction: the upper arm along `elbow`, the forearm along `hand` (both (fwd, up, out) in the
    chest's frame). Solved into the bones' own rotations, so poses don't fight the model's A-pose rest."""
    ru = _arc(UPPER_REST[side], _dir(side, elbow))
    fore_world = _dir(side, hand)
    fore_local = _mul(_t(ru), fore_world)
    rf = _arc(FORE_REST[side], fore_local)
    w = list(wrist) if side == 'right' else [wrist[0], -wrist[1], -wrist[2]]
    return {side + '_upper_arm': {'rot': _animator(ru)}, side + '_forearm': {'rot': _animator(rf)}, side + '_hand': {'rot': w}}


def both(elbow, hand, wrist=(0, 0, 0), l_elbow=None, l_hand=None, l_wrist=None):
    return merge(arm('right', elbow, hand, wrist), arm('left', l_elbow or elbow, l_hand or hand, l_wrist or wrist))


def merge(*poses):
    out = {}
    for p in poses:
        for k, v in p.items():
            out[k] = dict(out.get(k, {}), **v)
    return out


def arms(r_upper, r_fore, l_upper, l_fore, r_hand=(0, 0, 0), l_hand=(0, 0, 0)):
    return {
        'right_upper_arm': {'rot': list(r_upper)}, 'right_forearm': {'rot': list(r_fore)}, 'right_hand': {'rot': list(r_hand)},
        'left_upper_arm': {'rot': list(l_upper)}, 'left_forearm': {'rot': list(l_fore)}, 'left_hand': {'rot': list(l_hand)},
    }


def sym(upper, fore, hand=(0, 0, 0)):
    """The same pose on both arms (y and z mirrored for the left)."""
    return arms(upper, fore, (upper[0], -upper[1], -upper[2]), (fore[0], -fore[1], -fore[2]), hand, (hand[0], -hand[1], -hand[2]))


def torso(chest=(0, 0, 0), neck=(0, 0, 0), head=(0, 0, 0), jaw=0):
    return {'chest': {'rot': list(chest)}, 'neck': {'rot': list(neck)}, 'head': {'rot': list(head)}, 'jaw': {'rot': [jaw, 0, 0]}}


def fingers(curl, spread=0):
    """Claws: curl (degrees, + closes the hand) on every finger, its tip and the thumb; spread fans them."""
    out = {}
    for side, s in (('right', 1), ('left', -1)):
        for i in range(1, 5):
            out['%s_finger_%d' % (side, i)] = {'rot': [-curl * 0.6, 0, s * spread * (i - 2.5)]}
            out['%s_finger_tip_%d' % (side, i)] = {'rot': [-curl, 0, 0]}
        out[side + '_thumb'] = {'rot': [-curl * 0.5, s * curl * 0.4, 0]}
    return out


def cloth(front=0, tip=0, back=0):
    return {'loincloth_front': {'rot': [front, 0, 0]}, 'loincloth_tip': {'rot': [tip, 0, 0]}, 'loincloth_back': {'rot': [back, 0, 0]}}


# --- The stance every clip leaves from and returns to: hunched, knees bent, long arms hanging forward, jaw ajar. ---
STANCE = merge(
    legs(3.0, 1.5, -1.0),
    torso(chest=(28, 0, 0), neck=(10, 0, 0), head=(-36, 0, 0), jaw=8),
    both((0.62, -0.78, 0.36), (0.95, -0.55, 0.14), (-10, 0, 0)),
    fingers(18, 2),
    cloth(4, 2, -4),
)
STANCE['body']['rot'] = [0, 0, 0]

BREATH = merge(STANCE, legs(3.8, 1.5, -1.0),
               torso(chest=(32, 0, 0), neck=(10, 0, 0), head=(-34, 3, 0), jaw=15),
               both((0.6, -0.78, 0.42), (0.9, -0.6, 0.2), (-6, 0, 2)), cloth(1, 4, -2))

WINDED = merge(legs(5.0, 1.5, -2.0), torso(chest=(48, 0, 4), neck=(12, 0, 0), head=(-26, 0, 0), jaw=28),
               both((0.78, -0.62, 0.22), (0.7, -0.7, 0.06), (8, 0, 0)), fingers(6, 0), cloth(10, 6, -8))


def key(t_ms, pose, ease='EASE_IN_OUT', marker=None):
    k = {'t': int(t_ms), 'ease': ease, 'bones': json.loads(json.dumps(pose))}
    if marker:
        k['marker'] = marker
    return k


def clip(name, duration, keys, loop=False, hold=False, blend_in=150, blend_out=250, ref=''):
    c = {'name': 'finger_bearer_' + name, 'rig': 'cursed_spirit', 'reference': ref, 'duration': int(duration),
         'priority': 'IDLE' if name == 'idle' else 'SPECIAL', 'blendIn': blend_in, 'blendOut': blend_out, 'keys': keys}
    if loop:
        c['loop'] = True
    if hold:
        c['hold'] = True
    return c


def build():
    clips = []

    # Idle: the supplied model's own breathing (body rise, chest and jaw, arms and hands drifting), deepened and laid
    # over the stance so the curse never stands still.
    clips.append(clip('idle', 4000, [key(0, STANCE), key(2000, BREATH), key(4000, STANCE)], loop=True, blend_in=300, blend_out=300,
                      ref='cursed_spirit.bbmodel animation.cursed_spirit.idle, over the combat stance'))

    # Roar: the encounter's opening tell when it turns on someone. Rears up, arms thrown wide, jaw open.
    rear = merge(legs(0.5, 2, -2), torso(chest=(-8, 0, 0), neck=(-6, 0, 0), head=(-30, 0, 0), jaw=50),
                 both((0.2, 0.35, 1), (0.35, 0.8, 0.6), (-20, 0, 0)), fingers(-10, 8), cloth(-6, -4, 6))
    shake = merge(rear, torso(chest=(-10, 0, 2), neck=(-6, 0, 0), head=(-32, 4, 0), jaw=56))
    clips.append(clip('roar', ROAR * TICK, [key(0, STANCE), key(350, rear, 'EASE_OUT'), key(650, shake), key(900, rear), key(1150, shake),
                                            key(ROAR * TICK, STANCE)], ref='opening roar'))

    # Cursed Energy Shot: right palm drawn back to the shoulder with the chest turned away, then thrust out; the
    # release key is the tick the shot leaves the hand.
    draw = merge(legs(4, -1, 2), torso(chest=(22, -28, 0), neck=(6, 8, 0), head=(-30, 20, 0), jaw=18),
                 arm('right', (-0.55, -0.45, 0.7), (0.55, 0.8, -0.2), (24, 0, 0)), arm('left', (0.7, -0.5, 0.4), (1, -0.05, -0.25)),
                 fingers(30, 0))
    draw2 = merge(draw, torso(chest=(20, -32, 0), neck=(6, 8, 0), head=(-30, 22, 0), jaw=22))
    thrust = merge(legs(4, -3, 4), torso(chest=(30, 22, 0), neck=(8, -6, 0), head=(-36, -14, 0), jaw=34),
                   arm('right', (1, 0.05, -0.1), (1, 0.08, -0.16), (-40, 0, 0)), arm('left', (-0.45, -0.8, 0.45), (0.15, -0.9, 0.35)),
                   fingers(-12, 10), {'body': {'pos': [0, -4, -2.5]}})
    through = merge(thrust, arm('right', (1, 0.1, -0.05), (1, 0.18, -0.1), (-30, 0, 0)))
    clips.append(clip('shot', SHOT_END * TICK, [key(0, STANCE), key(SHOT_FIRE * TICK - 300, draw, 'EASE_OUT'),
                                                key(SHOT_FIRE * TICK - 60, draw2), key(SHOT_FIRE * TICK, thrust, 'EASE_IN', 'release'),
                                                key(SHOT_FIRE * TICK + 200, through), key(SHOT_END * TICK, STANCE)], ref='palm thrust'))

    # Charged Blast: sinks into a deep stance and gathers energy between its cupped claws for two seconds (arms
    # trembling, jaw widening), pulls back, then drives both palms forward. Afterwards it is winded: hunched and panting
    # for the rest of the clip (the punish window); if the blast landed the server cuts to 'recover' early.
    gather = merge(legs(6, 2, -3), torso(chest=(30, 0, 0), neck=(8, 0, 0), head=(-36, 0, 0), jaw=26),
                   both((0.55, -0.45, 0.7), (0.65, 0.1, -0.75), (-10, 0, 0)), fingers(40, -4), cloth(8, 4, -6))
    gather2 = merge(gather, torso(chest=(32, 0, 2), neck=(8, 0, 0), head=(-38, 2, 0), jaw=36),
                    both((0.55, -0.4, 0.74), (0.62, 0.14, -0.77), (-12, 0, 2)))
    pull = merge(legs(7, 1, -4), torso(chest=(16, 0, 0), neck=(6, 0, 0), head=(-26, 0, 0), jaw=42),
                 both((-0.3, -0.6, 0.75), (0.4, 0.2, -0.9), (-16, 0, 0)), fingers(30, 0))
    fire = merge(legs(5, -3, 5), torso(chest=(32, 0, 0), neck=(8, 0, 0), head=(-40, 0, 0), jaw=54),
                 both((1, 0.02, 0.06), (1, 0.04, -0.12), (-40, 0, 0)), fingers(-16, 12), {'body': {'pos': [0, -5, -3]}}, cloth(-10, -8, 10))
    pant = merge(WINDED, torso(chest=(52, 0, -3), neck=(12, 0, 0), head=(-24, 0, 0), jaw=36), legs(5.6, 1.5, -2))
    f = BLAST_FIRE * TICK
    blast = [key(0, STANCE), key(450, gather, 'EASE_OUT', 'gather')]
    for i, t in enumerate(range(700, f - 400, 250)):
        blast.append(key(t, gather2 if i % 2 == 0 else gather))
    blast += [key(f - 300, pull, 'EASE_IN_OUT', 'pull'), key(f, fire, 'EASE_IN', 'release'), key(f + 300, fire),
              key(f + 650, WINDED, 'EASE_OUT', 'winded'), key(f + 1000, pant), key(f + 1300, WINDED), key(f + 1550, pant),
              key(BLAST_END * TICK, STANCE)]
    clips.append(clip('blast', BLAST_END * TICK, blast, ref='two-handed charged blast'))
    clips.append(clip('recover', 600, [key(0, WINDED), key(600, STANCE)], blend_in=120, ref='straightening up after a blast that landed'))

    # Point-Blank Burst: curls in over its own chest, shaking, while the ring on the floor shows the radius, then flings
    # every limb out and the energy erupts (release key = the burst's tick).
    curl = merge(legs(7, 0, -1), torso(chest=(42, 0, 0), neck=(12, 0, 0), head=(-24, 0, 0), jaw=4),
                 both((0.7, -0.3, 0.65), (0.25, 0.5, -1), (10, 0, 0)), fingers(60, -6), cloth(12, 8, -10))
    curl2 = merge(curl, torso(chest=(44, 3, 2), neck=(12, 0, 0), head=(-22, -3, 0), jaw=2))
    fling = merge(legs(0.5, 4, -4, splay=9), torso(chest=(-14, 0, 0), neck=(-8, 0, 0), head=(-34, 0, 0), jaw=58),
                  both((0.1, 0.3, 1), (0.15, 0.45, 1), (-30, 0, 0)), fingers(-20, 14), {'body': {'pos': [0, 0.5, 0]}}, cloth(-14, -10, 14))
    b = BURST_FIRE * TICK
    clips.append(clip('burst', BURST_END * TICK, [key(0, STANCE), key(350, curl, 'EASE_OUT', 'curl'), key(500, curl2), key(620, curl),
                                                  key(740, curl2), key(b - 40, curl), key(b, fling, 'EASE_IN', 'release'),
                                                  key(b + 350, fling), key(BURST_END * TICK, STANCE)], ref='point-blank burst'))

    # Brutal Rush: a low crouch with the arms swept back, the run (a loop), the swinging blow, and the stagger when it
    # misses or meets a wall.
    coil = merge(legs(8, 4, -6), torso(chest=(46, 0, 0), neck=(12, 0, 0), head=(-50, 0, 0), jaw=32),
                 both((-0.6, -0.6, 0.5), (-0.75, -0.45, 0.3), (10, 0, 0)), fingers(30, 0), cloth(16, 10, -16))
    clips.append(clip('rush_windup', RUSH_WINDUP * TICK, [key(0, STANCE), key(350, coil, 'EASE_OUT'),
                                                          key(RUSH_WINDUP * TICK, merge(coil, legs(9, 4, -6)), 'EASE_IN_OUT')],
                      hold=True, ref='rush crouch'))
    run_lean = merge(torso(chest=(48, 0, 0), neck=(12, 0, 0), head=(-52, 0, 0), jaw=38), fingers(30, 0), cloth(-24, -12, 20))
    stride_r = merge(run_lean, {'body': {'pos': [0, -3, 0]}},
                     {'right_thigh': {'rot': [-58, 0, 4]}, 'right_shin': {'rot': [28, 0, 0]}, 'right_foot': {'rot': [10, 0, 0]},
                      'left_thigh': {'rot': [34, 0, -4]}, 'left_shin': {'rot': [58, 0, 0]}, 'left_foot': {'rot': [-10, 0, 0]}},
                     both((-0.65, -0.5, 0.5), (-0.6, -0.6, 0.4), (10, 0, 0), (-0.3, -0.8, 0.5), (0.1, -0.9, 0.4)))
    stride_l = merge(run_lean, {'body': {'pos': [0, -3, 0]}},
                     {'left_thigh': {'rot': [-58, 0, -4]}, 'left_shin': {'rot': [28, 0, 0]}, 'left_foot': {'rot': [10, 0, 0]},
                      'right_thigh': {'rot': [34, 0, 4]}, 'right_shin': {'rot': [58, 0, 0]}, 'right_foot': {'rot': [-10, 0, 0]}},
                     both((-0.3, -0.8, 0.5), (0.1, -0.9, 0.4), (10, 0, 0), (-0.65, -0.5, 0.5), (-0.6, -0.6, 0.4)))
    # Passing positions: legs under the body, hips at their highest.
    clips.append(clip('rush', 600, [key(0, stride_r), key(150, merge(stride_r, legs(1.5, 0, 0)), 'LINEAR'),
                                    key(300, stride_l), key(450, merge(stride_l, legs(1.5, 0, 0)), 'LINEAR'), key(600, stride_r)],
                      loop=True, blend_in=100, blend_out=120, ref='charging run'))
    windup = merge(stride_r, torso(chest=(40, -34, 0), neck=(10, 10, 0), head=(-46, 24, 0), jaw=42),
                   arm('right', (-0.5, 0.4, 0.8), (0.05, 0.95, 0.35), (20, 0, 0)), arm('left', (0.6, -0.5, 0.6), (0.9, -0.2, 0.2)), fingers(50, 0))
    blow = merge(legs(6, -4, 6), torso(chest=(40, 36, 0), neck=(10, -10, 0), head=(-46, -24, 0), jaw=56),
                 arm('right', (1, 0.08, -0.25), (1, -0.02, -0.45), (-10, 0, 0)), arm('left', (-0.4, -0.7, 0.6), (0, -0.8, 0.6)),
                 fingers(70, 0), {'body': {'pos': [0, -6, -4]}})
    follow = merge(blow, torso(chest=(44, 48, 0), neck=(10, -10, 0), head=(-44, -30, 0), jaw=42),
                   arm('right', (0.6, -0.05, -0.8), (0.3, -0.25, -1), (-10, 0, 0)))
    h = STRIKE_HIT * TICK
    clips.append(clip('rush_strike', STRIKE_END * TICK, [key(0, stride_r), key(h - 80, windup, 'EASE_OUT'), key(h, blow, 'EASE_IN', 'impact'),
                                                         key(h + 300, follow, 'EASE_OUT'), key(h + 450, follow),
                                                         key(STRIKE_END * TICK, STANCE)], blend_in=60, ref='rush blow'))
    recoil = merge(legs(2, 6, -3), torso(chest=(-12, 0, 0), neck=(-8, 0, 0), head=(-52, 0, 0), jaw=46),
                   both((0.1, 0.2, 1), (0.25, 0.6, 0.8), (-20, 0, 0)), fingers(-10, 10), {'body': {'pos': [0, -2, 3]}})
    dizzy = merge(legs(6, 1, -2), torso(chest=(52, 0, 10), neck=(12, 0, 4), head=(-6, 0, -12), jaw=36),
                  both((0.8, -0.6, 0.15), (0.6, -0.8, 0.05), (10, 0, 0)), fingers(4, 0))
    dizzy2 = merge(dizzy, torso(chest=(48, 0, -12), neck=(12, 0, -4), head=(-10, 0, 12), jaw=30))
    clips.append(clip('rush_miss', MISS_END * TICK, [key(0, stride_r), key(200, recoil, 'EASE_OUT', 'stagger'), key(550, dizzy),
                                                     key(950, dizzy2), key(1300, dizzy), key(MISS_END * TICK, STANCE)],
                      blend_in=60, ref='stagger after a miss'))

    # Heavy Follow-Up Smash: both arms rise overhead with the back arched (slow, unmistakable), hang there, then
    # hammer down on the marked spot (impact key = the damage tick); it stays bent over the crater for a long beat.
    raise_ = merge(legs(0.5, 2, -2), torso(chest=(-12, 0, 0), neck=(-6, 0, 0), head=(-32, 0, 0), jaw=30),
                   both((0.1, 1, 0.3), (0, 1, -0.45), (-10, 0, 0)), fingers(70, -4), {'body': {'pos': [0, 0.5, 1]}}, cloth(-6, -4, 6))
    top = merge(raise_, torso(chest=(-18, 0, 0), neck=(-8, 0, 0), head=(-28, 0, 0), jaw=40), both((-0.2, 1, 0.3), (-0.35, 0.9, -0.4), (-14, 0, 0)))
    slam = merge(legs(9, -2, 4), torso(chest=(58, 0, 0), neck=(12, 0, 0), head=(-56, 0, 0), jaw=54),
                 both((0.95, -0.2, 0.18), (0.85, -0.45, -0.18), (-10, 0, 0)), fingers(70, -4), {'body': {'pos': [0, -9, -4]}}, cloth(20, 12, -20))
    s = SMASH_HIT * TICK
    clips.append(clip('smash', SMASH_END * TICK, [key(0, STANCE), key(500, raise_, 'EASE_OUT', 'raise'), key(s - 160, top, 'EASE_OUT'),
                                                  key(s, slam, 'EASE_IN', 'impact'), key(s + 350, merge(slam, torso(chest=(54, 0, 0), neck=(12, 0, 0), head=(-50, 0, 0), jaw=38))),
                                                  key(s + 700, merge(WINDED, legs(7, 0, 2))), key(SMASH_END * TICK, STANCE)], ref='two-handed hammer smash'))

    # Taking a hit between moves: a snap back of the head and chest.
    flinch = merge(STANCE, legs(4, 2, -1), torso(chest=(10, 0, 6), neck=(-4, 0, 0), head=(-52, 0, -8), jaw=42),
                   both((0.2, -0.5, 0.85), (0.4, -0.2, 0.9)), {'body': {'pos': [0, -4, 1.5]}})
    clips.append(clip('hurt', 500, [key(0, STANCE), key(90, flinch, 'EASE_OUT'), key(500, STANCE)], blend_in=40, blend_out=150, ref='flinch'))

    # Death: rears back in a last roar, the knees give, and it pitches forward onto its face; held until it dissolves.
    last = merge(legs(1, 2, -2), torso(chest=(-16, 0, 0), neck=(-8, 0, 0), head=(-54, 0, 0), jaw=60),
                 both((0.2, 0.3, 1), (0.4, 0.6, 0.7)), fingers(-10, 10))
    kneel = merge(torso(chest=(34, 0, 6), neck=(10, 0, 0), head=(12, 0, 10), jaw=40), both((0.4, -0.9, 0.2), (0.3, -0.95, 0.1)), fingers(0, 0),
                  {'body': {'pos': [0, -13, -2], 'rot': [0, 0, 0]},
                   'right_thigh': {'rot': [-80, 0, 6]}, 'right_shin': {'rot': [96, 0, 0]}, 'right_foot': {'rot': [40, 0, 0]},
                   'left_thigh': {'rot': [-70, 0, -6]}, 'left_shin': {'rot': [96, 0, 0]}, 'left_foot': {'rot': [40, 0, 0]}})
    fallen = merge(kneel, torso(chest=(10, 0, 4), neck=(0, 0, 0), head=(10, 20, 0), jaw=30), both((0.2, 1, 0.45), (0.1, 1, 0.3)),
                   {'body': {'pos': [0, -30, -8], 'rot': [84, 0, 0]},
                    'right_thigh': {'rot': [-10, 0, 4]}, 'right_shin': {'rot': [10, 0, 0]}, 'right_foot': {'rot': [40, 0, 0]},
                    'left_thigh': {'rot': [-4, 0, -4]}, 'left_shin': {'rot': [20, 0, 0]}, 'left_foot': {'rot': [40, 0, 0]}})
    clips.append(clip('death', DEATH * TICK, [key(0, STANCE), key(300, last, 'EASE_OUT'), key(800, merge(last, torso(chest=(-12, 0, 6), neck=(-6, 0, 0), head=(-40, 10, 0), jaw=50))),
                                              key(1300, kneel, 'EASE_IN'), key(1800, fallen, 'EASE_IN'), key(1950, merge(fallen, {'body': {'pos': [0, -28.5, -8], 'rot': [80, 0, 0]}}), 'EASE_OUT'),
                                              key(DEATH * TICK, fallen)], hold=True, blend_in=80, ref='death'))
    return clips


def main():
    os.makedirs(OUT, exist_ok=True)
    for c in build():
        for k in c['keys']:
            for b in k['bones'].values():
                for ch in ('rot', 'pos'):
                    if ch in b:
                        b[ch] = [round(float(v), 2) for v in b[ch]]
        path = os.path.join(OUT, c['name'] + '.json')
        json.dump(c, open(path, 'w'), indent=1)
        print('wrote', path, len(c['keys']), 'keys')
        if '--preview' in sys.argv:
            prev = sys.argv[sys.argv.index('--preview') + 1]
            os.makedirs(prev, exist_ok=True)
            subprocess.run([sys.executable, os.path.join(HERE, 'bb_pose_preview.py'), MODEL, TEXTURE, path,
                            os.path.join(prev, c['name'] + '.png')], check=True)


if __name__ == '__main__':
    main()
