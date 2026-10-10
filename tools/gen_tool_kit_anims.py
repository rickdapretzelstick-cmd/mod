"""Animation clips for the cursed tools' movesets (animations/tool/*.json), and the draw and holster.

    python3 tools/gen_tool_kit_anims.py

Every tool move has its own clip, shaped by how the tool is held:
- Slaughter Demon (one hand, fast): low, darting, the blade arm leading; the free hand stays back for balance.
- Cursed Cleaver (two hands, heavy): wide stances, both hands on the haft, big wind-ups, the body dropping into blows.
- Cursed Rifle (two hands, ranged): shouldered, compact; the stock and the lenses are what it fights with up close.
- Cursed Blade (two hands, a great cleaver): both hands on the handle, the whole body behind every cut, big committed
  wind-ups and follow-throughs; its own M1 chain (cb_light_*, ...) and a stance it holds while drawn (idle, walk, run:
  upper body only, so the legs keep the real walk).
Draw and holster have a hip version (a sheath at the left hip) and a back version (slung over the right shoulder).

Bone angles are degrees (x: an arm's negative x raises it forward; y swings it across; z out to the side), root
position is in pixels (z forward). Markers name the moment a blow lands; each move's server timing matches them.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'animations', 'tool')


def k(t, bones, ease='EASE_IN_OUT', marker=None):
    e = {'t': t, 'ease': ease, 'bones': {b: ({'pos': v[1]} if v[0] == 'pos' else {'rot': v[1]}) for b, v in bones.items()}}
    if marker:
        e['marker'] = marker
    return e


def r(x, y=0, z=0):
    return ('rot', [x, y, z])


def p(x, y=0, z=0):
    return ('pos', [x, y, z])


def clip(name, duration, keys, priority='ATTACK', hold=False, blend_in=40, blend_out=160, note=None, layer=None, loop=False):
    c = {'name': name, 'duration': duration}
    if note:
        c['note'] = note
    if hold:
        c['hold'] = True
    if loop:
        c['loop'] = True
    if layer:
        c['layer'] = layer
    c.update({'blendIn': blend_in, 'blendOut': blend_out, 'priority': priority, 'keys': keys})
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(c, f, indent=2)
        f.write('\n')


# Stances the moves start from and settle back into.
LOW = {'leftThigh': r(-40, 0, -6), 'leftShin': r(36), 'rightThigh': r(28, 0, 6), 'rightShin': r(20), 'root': p(0, -3, 0)}
WIDE = {'leftThigh': r(-30, 0, -14), 'leftShin': r(30), 'rightThigh': r(24, 0, 14), 'rightShin': r(18), 'root': p(0, -3.5, 0)}
SETTLE = {'head': r(2), 'chest': r(2, 6), 'hips': r(2, 4), 'rightArm': r(-40, -10, 6), 'rightForearm': r(-24), 'rightHand': r(14),
          'leftArm': r(-18, 14, -12), 'leftForearm': r(-18), 'rightThigh': r(8, 0, 4), 'leftThigh': r(-10, 0, -4), 'rightShin': r(6),
          'leftShin': r(8), 'root': p(0, -1, 0)}
TWO_SETTLE = {'head': r(2), 'chest': r(4, 10), 'hips': r(2, 6), 'rightArm': r(-46, -18, 4), 'rightForearm': r(-30),
              'leftArm': r(-50, 34, -4), 'leftForearm': r(-34), 'rightThigh': r(10, 0, 6), 'leftThigh': r(-12, 0, -6),
              'rightShin': r(8), 'leftShin': r(10), 'root': p(0, -1.5, 0)}


def merge(*ds):
    out = {}
    for d in ds:
        out.update(d)
    return out


# --- Draw and holster ---
clip('kit_draw_hip', 360, [
    k(0, {'rightArm': r(-20, 40, 10), 'rightForearm': r(-50), 'chest': r(4, -16), 'head': r(6, 10)}),
    k(120, {'rightArm': r(-10, 60, 20), 'rightForearm': r(-70), 'rightHand': r(30), 'chest': r(6, -24)}, 'EASE_IN', 'draw'),
    k(360, {'rightArm': r(-50, -14, 6), 'rightForearm': r(-20), 'rightHand': r(10), 'chest': r(2, 4)}, 'OVERSHOOT'),
], priority='COMBAT', note='The blade comes out of the sheath at the left hip in one pull.')
clip('kit_draw_back', 420, [
    k(0, {'rightArm': r(-120, 10, 20), 'rightForearm': r(-40), 'head': r(-4, -10)}),
    k(150, {'rightArm': r(-170, 20, 26), 'rightForearm': r(-70), 'rightHand': r(-20), 'chest': r(-4, 10)}, 'EASE_IN', 'draw'),
    k(420, {'rightArm': r(-56, -16, 4), 'rightForearm': r(-30), 'leftArm': r(-50, 34, -4), 'leftForearm': r(-34), 'chest': r(4, 10)}, 'OVERSHOOT'),
], priority='COMBAT', note='Reached over the right shoulder and swung down into both hands.')
clip('kit_holster_hip', 320, [
    k(0, {'rightArm': r(-40, 10), 'rightForearm': r(-30)}),
    k(200, {'rightArm': r(-6, 56, 18), 'rightForearm': r(-64), 'rightHand': r(26), 'chest': r(4, -18), 'head': r(10, 12)}, 'EASE_OUT', 'sheathe'),
    k(320, {'rightArm': r(0, 10, 6), 'rightForearm': r(-6), 'chest': r(0)}),
], priority='COMBAT')
clip('kit_holster_back', 380, [
    k(0, {'rightArm': r(-50, -10), 'leftArm': r(-40, 30)}),
    k(220, {'rightArm': r(-168, 20, 24), 'rightForearm': r(-80), 'chest': r(-4, 10), 'head': r(-6, -8)}, 'EASE_OUT', 'sling'),
    k(380, {'rightArm': r(0, 0, 6), 'rightForearm': r(0), 'leftArm': r(0), 'chest': r(0)}),
], priority='COMBAT')

# --- Slaughter Demon: one hand, fast ---
clip('sd_quickstep', 420, [
    k(0, merge(LOW, {'chest': r(18, 30), 'hips': r(10, 16), 'rightArm': r(-30, 70, 30), 'rightForearm': r(-40), 'leftArm': r(20, -10, -30)})),
    k(110, merge(LOW, {'chest': r(26, -40), 'hips': r(12, -20), 'rightArm': r(-80, -80, -20), 'rightForearm': r(-4), 'rightHand': r(80),
                       'leftArm': r(30, 0, -40), 'root': p(0, -4.5, 7)}), 'EASE_IN', 'cut'),
    k(420, SETTLE),
], note='A low dart: the blade drawn back at the hip, then one flat cut across as the body passes.')
clip('sd_return_cut', 380, [
    k(0, {'chest': r(10, -60), 'hips': r(6, -40), 'rightArm': r(-70, -80, -20)}),
    k(120, merge(LOW, {'chest': r(16, 90), 'hips': r(8, 70), 'rightArm': r(-90, 90, 20), 'rightHand': r(70), 'root': p(0, -3.5, -6)}), 'EASE_IN', 'cut'),
    k(380, SETTLE),
], note='Back the way it came: a turning backhand.')
clip('sd_flurry', 640, [
    k(0, merge(LOW, {'chest': r(10, 30), 'rightArm': r(-150, 40, 30), 'rightHand': r(60)})),
    k(90, {'chest': r(18, -30), 'rightArm': r(-40, -60, -20), 'root': p(0, -3, 2)}, 'EASE_IN', 'cut1'),
    k(200, {'chest': r(10, 34), 'rightArm': r(-60, 80, 40), 'rightHand': r(-40), 'root': p(0, -3, 3)}, 'EASE_IN', 'cut2'),
    k(330, {'chest': r(22, -20), 'rightArm': r(-170, -10, -10), 'rightHand': r(70), 'root': p(0, -2.5, 3.5)}, 'EASE_IN', 'cut3'),
    k(450, {'chest': r(30, 0), 'rightArm': r(-20, 0, 0), 'rightForearm': r(-2), 'root': p(0, -4.5, 5)}, 'EASE_IN', 'cut4'),
    k(640, SETTLE),
], note='Four cuts: across, back, from overhead, and a last one driven down through.')
clip('sd_rising', 460, [
    k(0, merge(LOW, {'chest': r(30, 10), 'rightArm': r(10, 30, 20), 'rightHand': r(60), 'root': p(0, -5, 1)})),
    k(140, {'chest': r(-24, -10), 'head': r(-20), 'rightArm': r(-176, -10, 10), 'rightHand': r(40), 'leftThigh': r(-10), 'rightThigh': r(10),
            'root': p(0, 2, 2)}, 'EASE_IN', 'launch'),
    k(460, SETTLE),
], note='From a crouch, straight up: the cut that throws them into the air.')
clip('sd_sever_windup', 260, [
    k(260, merge(LOW, {'chest': r(10, 60), 'hips': r(6, 34), 'head': r(6, -40), 'rightArm': r(-30, 90, 40), 'rightForearm': r(-50),
                       'rightHand': r(40), 'leftArm': r(-60, -30, -10)}), 'EASE_OUT'),
], hold=True, note='Drawn right back, the free hand marking the target.')
clip('sd_sever', 700, [
    k(0, merge(LOW, {'chest': r(10, 60), 'rightArm': r(-30, 90, 40)})),
    k(120, merge(LOW, {'chest': r(20, -70), 'hips': r(10, -40), 'head': r(10, -10), 'rightArm': r(-86, -100, -24), 'rightHand': r(90),
                       'leftArm': r(10, 30, -30), 'root': p(0, -4.5, 5)}), 'EASE_IN', 'sever'),
    k(420, {'chest': r(22, -76), 'rightArm': r(-60, -110, -30), 'root': p(0, -4.5, 4.5)}),
    k(700, SETTLE),
], note='One decisive horizontal cut, held at the end of the follow-through.')
clip('sd_parry_stance', 200, [
    k(200, merge(LOW, {'chest': r(4, 20), 'rightArm': r(-80, 40, 0), 'rightForearm': r(-70), 'rightHand': r(-80), 'leftArm': r(-40, -20, -10),
                       'head': r(4, -10)}), 'EASE_OUT'),
], hold=True, priority='ATTACK', note='The blade upright in front of the face, edge out.')
clip('sd_riposte', 520, [
    k(0, {'chest': r(0, 40), 'rightArm': r(-80, 40)}),
    k(150, merge(LOW, {'chest': r(20, -150), 'hips': r(10, -120), 'rightArm': r(-90, 0, 0), 'rightForearm': r(0), 'rightHand': r(90),
                       'root': p(0, -3.5, 4)}), 'EASE_IN', 'riposte'),
    k(520, SETTLE),
], note='A spin behind the attacker and a thrust into their back.')
clip('sd_drawcut', 520, [
    k(0, merge(WIDE, {'chest': r(10, -30), 'hips': r(4, -20), 'rightArm': r(-10, 70, 30), 'rightForearm': r(-70), 'leftArm': r(-10, 30, -10)})),
    k(130, merge(WIDE, {'chest': r(14, 60), 'hips': r(6, 40), 'rightArm': r(-90, -90, -10), 'rightHand': r(90), 'root': p(0, -4, 3)}), 'EASE_IN', 'sweep'),
    k(520, SETTLE),
], note='From the sheath straight into a wide sweep.')
clip('sd_flicker', 260, [
    k(0, merge(LOW, {'chest': r(30, 30), 'rightArm': r(-30, 80, 40), 'root': p(0, -5, 0)})),
    k(70, merge(LOW, {'chest': r(30, -40), 'rightArm': r(-90, -90, -20), 'rightHand': r(90), 'root': p(0, -5, 6)}), 'EASE_IN', 'cut'),
    k(260, merge(LOW, {'chest': r(24, -30), 'rightArm': r(-70, -80, -20)})),
], priority='SPECIAL', blend_in=0, blend_out=80, note='Thousand Cuts: one of the flickers between targets.')

# --- Cursed Cleaver: two hands, heavy ---
clip('cl_heavy_charge', 300, [
    k(300, merge(WIDE, {'chest': r(-20, 20), 'hips': r(-4, 10), 'head': r(-6, -10), 'rightArm': r(-170, 20, 10), 'rightForearm': r(-60),
                        'leftArm': r(-170, -10, -10), 'leftForearm': r(-60), 'root': p(0, -2.5, -1.5)}), 'EASE_OUT'),
], hold=True, note='Both hands on the haft, the head hauled up behind; the body leans back into it.')
clip('cl_heavy', 760, [
    k(0, merge(WIDE, {'chest': r(-20, 20), 'rightArm': r(-170, 20, 10), 'leftArm': r(-170, -10, -10)})),
    k(170, merge(WIDE, {'chest': r(44, 0), 'hips': r(16, 0), 'head': r(20), 'rightArm': r(-40, -20, 0), 'rightForearm': r(-6),
                        'leftArm': r(-44, 30, 0), 'leftForearm': r(-8), 'root': p(0, -6, 4)}), 'EASE_IN', 'impact'),
    k(480, merge(WIDE, {'chest': r(46, 0), 'rightArm': r(-36, -20), 'leftArm': r(-40, 30), 'root': p(0, -6, 4)})),
    k(760, TWO_SETTLE),
], note='The whole body drops into it; it stays buried a moment after.')
clip('cl_whirl', 700, [
    k(0, merge(WIDE, {'chest': r(4, -80), 'rightArm': r(-90, 60, 30), 'leftArm': r(-90, 80, 20)})),
    k(200, merge(WIDE, {'chest': r(4, 100), 'hips': r(0, 80), 'rightArm': r(-90, -70, -20), 'leftArm': r(-90, -40, -20)}), 'EASE_IN', 'spin1'),
    k(420, merge(WIDE, {'chest': r(4, -100), 'hips': r(0, -60), 'rightArm': r(-90, 60, 30), 'leftArm': r(-90, 80, 20)}), 'LINEAR', 'spin2'),
    k(700, TWO_SETTLE),
], note='The swing carries on round: a full turn with the cleaver out at arm\'s length.')
clip('cl_charge', 600, [
    k(0, {'chest': r(34, -30), 'hips': r(16, -20), 'head': r(10, 20), 'rightArm': r(-20, 30, 10), 'rightForearm': r(-80),
          'leftArm': r(-50, 40, -10), 'leftForearm': r(-70), 'rightThigh': r(-50), 'rightShin': r(60), 'leftThigh': r(30), 'root': p(0, -3, 2)}),
    k(300, {'rightThigh': r(30), 'leftThigh': r(-50), 'leftShin': r(60), 'rightShin': r(10), 'root': p(0, -3, 3)}, 'LINEAR', 'drive'),
    k(600, {'rightThigh': r(-50), 'rightShin': r(60), 'leftThigh': r(30), 'leftShin': r(10), 'root': p(0, -3, 3)}, 'LINEAR'),
], hold=True, priority='ATTACK', note='Head down, shoulder first, the haft across the chest.')
clip('cl_splitter', 820, [
    k(0, merge(WIDE, {'chest': r(-26, 0), 'rightArm': r(-176, 0, 10), 'leftArm': r(-176, 0, -10), 'root': p(0, -1, 0)})),
    k(220, merge(WIDE, {'chest': r(60, 0), 'hips': r(22, 0), 'head': r(26), 'rightArm': r(-10, -10), 'leftArm': r(-14, 20),
                        'root': p(0, -8, 5)}), 'EASE_IN', 'split'),
    k(560, merge(WIDE, {'chest': r(58, 0), 'root': p(0, -8, 5)})),
    k(820, TWO_SETTLE),
], note='Straight overhead and down, driving the edge into the ground.')
clip('cl_wall', 220, [
    k(220, merge(WIDE, {'chest': r(10, 0), 'head': r(6), 'rightArm': r(-80, -40, 0), 'rightForearm': r(-50), 'leftArm': r(-80, 40, 0),
                        'leftForearm': r(-50), 'root': p(0, -4, -0.5)}), 'EASE_OUT'),
], hold=True, priority='ATTACK', note='Braced, the cleaver held flat across the front like a bar.')
clip('cl_counter', 620, [
    k(0, merge(WIDE, {'chest': r(4, 70), 'rightArm': r(-80, 80, 20), 'leftArm': r(-80, 90, 10)})),
    k(140, merge(WIDE, {'chest': r(16, -80), 'hips': r(6, -50), 'rightArm': r(-90, -90, -20), 'leftArm': r(-86, -60, -20),
                        'root': p(0, -4, 4)}), 'EASE_IN', 'counter'),
    k(620, TWO_SETTLE),
], note='Out of the wall, everything absorbed goes into one wide swing back.')
clip('cl_haft', 340, [
    k(0, {'chest': r(4, 20), 'rightArm': r(-60, 10), 'leftArm': r(-50, 40)}),
    k(80, {'chest': r(18, -10), 'rightArm': r(-90, -20), 'rightForearm': r(0), 'leftArm': r(-90, 20), 'leftForearm': r(0), 'root': p(0, -1.5, 4)},
      'EASE_IN', 'haft'),
    k(340, TWO_SETTLE),
], note='A short, sharp jab with the butt of the haft.')
clip('cl_exec_leap', 400, [
    k(400, {'chest': r(-30), 'head': r(-20), 'rightArm': r(-178, 10, 10), 'rightForearm': r(-30), 'leftArm': r(-178, -10, -10),
            'leftForearm': r(-30), 'rightThigh': r(-70), 'rightShin': r(90), 'leftThigh': r(-40), 'leftShin': r(80), 'root': p(0, 1, 0)}, 'EASE_OUT'),
], hold=True, priority='SPECIAL', note='In the air: the cleaver held as high as it goes, knees tucked.')
clip('cl_exec_crash', 900, [
    k(0, {'chest': r(-30), 'rightArm': r(-178, 10, 10), 'leftArm': r(-178, -10, -10)}),
    k(120, merge(WIDE, {'chest': r(64, 0), 'hips': r(24), 'head': r(30), 'rightArm': r(0, -10), 'leftArm': r(-4, 20), 'root': p(0, -9, 3)}),
      'EASE_IN', 'crash'),
    k(620, merge(WIDE, {'chest': r(60, 0), 'root': p(0, -9, 3)})),
    k(900, TWO_SETTLE),
], priority='SPECIAL', note='The landing: everything comes down at once.')

# --- Cursed Rifle: shouldered, compact ---
clip('rf_bash', 420, [
    k(0, {'chest': r(4, 30), 'rightArm': r(-70, 0, 0), 'rightForearm': r(-50), 'leftArm': r(-80, 40, 0), 'leftForearm': r(-30)}),
    k(100, {'chest': r(16, -24), 'hips': r(6, -10), 'rightArm': r(-96, -30, 0), 'rightForearm': r(-10), 'leftArm': r(-60, 10), 'root': p(0, -1.5, 4)},
      'EASE_IN', 'bash'),
    k(420, {'chest': r(2, 6), 'rightArm': r(-80, -6), 'rightForearm': r(-40), 'leftArm': r(-84, 36), 'leftForearm': r(-30), 'root': p(0, -0.5, 0)}),
], note='Turned side-on, the stock driven forward in both hands.')
clip('rf_flare', 520, [
    k(0, {'rightArm': r(-80, -6), 'leftArm': r(-84, 36)}),
    k(160, {'chest': r(-10, 0), 'head': r(-6), 'rightArm': r(-120, -10, 0), 'rightForearm': r(-20), 'leftArm': r(-126, 30, 0), 'leftForearm': r(-14)},
      'EASE_IN', 'flare'),
    k(520, {'rightArm': r(-80, -6), 'rightForearm': r(-40), 'leftArm': r(-84, 36), 'leftForearm': r(-30)}),
], note='The rifle tipped up so the arms\' lenses catch and throw the light.')
print('tool clips written:', len(os.listdir(OUT)))


# --- Cursed Blade: two hands on a great cleaver ---
# Both hands on the handle: the left hand sits just above the right, so the arms move together; y swings the pair
# across the body (negative: to the left), x raises them (negative: forward and up).


def grip(x, y, fx=-28, z=4):
    """Both arms on the handle at one angle: right hand low on the grip, left hand just above it."""
    return {'rightArm': r(x, y, z), 'rightForearm': r(fx), 'leftArm': r(x - 6, y + 48, -z), 'leftForearm': r(fx - 6)}


CB_SETTLE = merge(TWO_SETTLE, grip(-44, -16))
TUCK = {'leftThigh': r(-46, 0, -6), 'leftShin': r(60), 'rightThigh': r(-30, 0, 6), 'rightShin': r(50)}

# The M1 chain: right to left, back left to right, a diagonal down from high, and the overhead finisher.
clip('cb_light_1', 380, [
    k(0, merge(grip(-70, 52), {'chest': r(4, 32), 'hips': r(2, 16)})),
    k(100, merge(grip(-74, -62), {'chest': r(10, -40), 'hips': r(4, -22), 'root': p(0, -1.5, 2)}), 'EASE_IN', 'hit'),
    k(380, CB_SETTLE),
], blend_in=30, note='A flat two-handed cut, right to left.')
clip('cb_light_2', 380, [
    k(0, merge(grip(-74, -62), {'chest': r(8, -38), 'hips': r(4, -20)})),
    k(100, merge(grip(-70, 56), {'chest': r(10, 36), 'hips': r(4, 22), 'root': p(0, -1.5, 2)}), 'EASE_IN', 'hit'),
    k(380, CB_SETTLE),
], blend_in=30, note='Straight back the other way, left to right.')
clip('cb_light_3', 420, [
    k(0, merge(grip(-150, 30, -40), {'chest': r(-6, 24), 'head': r(-6, -10)})),
    k(110, merge(grip(-32, -42, -10), {'chest': r(22, -24), 'hips': r(6, -12), 'head': r(10, 6), 'root': p(0, -2.5, 3)}), 'EASE_IN', 'hit'),
    k(420, CB_SETTLE),
], blend_in=30, note='Down on the diagonal from high on the right.')
clip('cb_light_4', 560, [
    k(0, merge(WIDE, grip(-172, 4, -30), {'chest': r(-14, 6), 'head': r(-10)})),
    k(150, merge(LOW, grip(-22, -6, -6), {'chest': r(32, 0), 'hips': r(10, 0), 'head': r(14), 'root': p(0, -4.5, 4)}), 'EASE_IN', 'hit'),
    k(300, merge(LOW, grip(-18, -6, -4), {'chest': r(34, 0), 'root': p(0, -4.5, 4)})),
    k(560, CB_SETTLE),
], blend_in=40, note='The finisher: the whole blade brought straight down from overhead.')
for i, (a, b) in enumerate([((-70, 52), (-74, -62)), ((-74, -62), (-70, 56)), ((-150, 30), (-32, -42)), ((-172, 4), (-30, -6))]):
    clip('cb_air_%d' % (i + 1), 380, [
        k(0, merge(TUCK, grip(*a), {'chest': r(-4, 30 if i == 0 else -30 if i == 1 else 0)})),
        k(100, merge(TUCK, grip(*b), {'chest': r(16, -30 if i == 0 else 30 if i == 1 else 0)}), 'EASE_IN', 'hit'),
        k(380, merge(TUCK, CB_SETTLE)),
    ], blend_in=30, note='In the air, legs tucked.')
clip('cb_uppercut', 520, [
    k(0, merge(LOW, grip(-12, 20, -20), {'chest': r(26, 14), 'root': p(0, -4.5, 0)})),
    k(150, merge(grip(-172, 4, -16), {'chest': r(-24, 0), 'head': r(-18), 'root': p(0, 2, 2)}), 'EASE_IN', 'hit'),
    k(520, CB_SETTLE),
], note='From a crouch, the blade swept up through them.')
clip('cb_downslam', 520, [
    k(0, merge(TUCK, grip(-176, 0, -20), {'chest': r(-16)})),
    k(140, merge(grip(-20, 0, -4), {'chest': r(40), 'head': r(16), 'root': p(0, -3, 3)}), 'EASE_IN', 'hit'),
    k(520, CB_SETTLE),
], note='From the air, straight down.')
clip('cb_sprint', 480, [
    k(0, merge(LOW, grip(-60, 60), {'chest': r(16, 40), 'root': p(0, -3, 0)})),
    k(120, merge(LOW, grip(-80, -70), {'chest': r(20, -50), 'hips': r(8, -30), 'root': p(0, -3.5, 6)}), 'EASE_IN', 'hit'),
    k(480, CB_SETTLE),
], note='Running in, a wide cut carried by the run.')
clip('cb_stomp', 440, [
    k(0, merge(grip(-150, 0, -40), {'chest': r(-6)})),
    k(120, merge(grip(-8, 0, 0), {'chest': r(48), 'head': r(20), 'root': p(0, -4, 2)}), 'EASE_IN', 'hit'),
    k(440, CB_SETTLE),
], note='Point down into someone on the ground.')
clip('cb_heavy_charge', 300, [
    k(300, merge(WIDE, grip(-150, 54, -36), {'chest': r(-6, 40), 'hips': r(2, 20), 'head': r(-4, -30)}), 'EASE_OUT'),
], hold=True, priority='ATTACK', note='Held: the blade drawn back high over the right shoulder.')
clip('cb_heavy', 520, [
    k(0, merge(WIDE, grip(-150, 54, -36), {'chest': r(-6, 40)})),
    k(110, merge(WIDE, grip(-36, -56, -8), {'chest': r(26, -46), 'hips': r(8, -24), 'root': p(0, -4, 4)}), 'EASE_IN', 'hit'),
    k(520, CB_SETTLE),
], note='The charged cut, high right to low left.')

# The moves.
clip('cb_heavy_slash', 800, [
    k(0, CB_SETTLE),
    k(300, merge(WIDE, grip(-166, 44, -34), {'chest': r(-10, 36), 'hips': r(2, 18), 'head': r(-6, -24)}), 'EASE_OUT'),
    k(350, merge(WIDE, grip(-34, -56, -8), {'chest': r(26, -46), 'hips': r(8, -26), 'head': r(12, 8), 'root': p(0, -4, 4)}), 'EASE_IN', 'slash'),
    k(560, merge(WIDE, grip(-28, -64, -6), {'chest': r(28, -50), 'root': p(0, -4, 4)})),
    k(800, CB_SETTLE),
], note='Heavy Slash: a long wind-up over the right shoulder, then the diagonal cleave (server: the cut at tick 7).')
clip('cb_cross', 500, [
    k(0, merge(WIDE, grip(-160, -52, -30), {'chest': r(-8, -34), 'head': r(-4, 20)})),
    k(200, merge(WIDE, grip(-30, 62, -8), {'chest': r(24, 42), 'hips': r(8, 24), 'root': p(0, -4, 4)}), 'EASE_IN', 'cross'),
    k(500, CB_SETTLE),
], note='Crimson Cross: straight back across the first cut (server: 4 ticks in).')
clip('cb_wave_charge', 400, [
    k(400, merge(WIDE, grip(-24, 84, -30), {'chest': r(6, 66), 'hips': r(4, 34), 'head': r(4, -46)}), 'EASE_OUT'),
], hold=True, note='Cursed Wave, held: the blade drawn far back low on the right, the energy gathering on it.')
clip('cb_wave_release', 600, [
    k(0, merge(WIDE, grip(-24, 84, -30), {'chest': r(6, 66), 'hips': r(4, 34)})),
    k(60, merge(WIDE, grip(-86, -74, -6), {'chest': r(14, -58), 'hips': r(6, -34), 'root': p(0, -3.5, 4)}), 'EASE_IN', 'release'),
    k(320, merge(WIDE, grip(-80, -80, -4), {'chest': r(14, -62), 'root': p(0, -3.5, 4)})),
    k(600, CB_SETTLE),
], note='The release: one flat sweep that throws the crescent.')
clip('cb_lunge', 1000, [
    k(0, CB_SETTLE),
    k(200, merge(LOW, grip(-58, 24, -60), {'chest': r(8, 34), 'hips': r(4, 20), 'root': p(0, -3.5, -2)}), 'EASE_OUT'),
    k(260, merge(LOW, grip(-88, -8, 0), {'chest': r(20, -10), 'hips': r(10, -6), 'head': r(10), 'root': p(0, -4, 6)}), 'EASE_IN', 'thrust'),
    k(500, merge(LOW, grip(-90, -8, 0), {'chest': r(22, -10), 'root': p(0, -4, 6)})),
    k(650, merge(LOW, grip(-70, 30, -20), {'chest': r(12, 30), 'root': p(0, -3, 3)}), 'EASE_OUT', 'thorns'),
    k(1000, CB_SETTLE),
], note='Thorn Lunge: drawn back, then a straight two-handed thrust driven through; a twist as the thorns erupt.')
clip('cb_guard', 200, [
    k(200, merge(WIDE, grip(-84, -2, -86), {'chest': r(4, 12), 'head': r(4, -6)}), 'EASE_OUT'),
], hold=True, note='Thorn Guard: the blade upright before the face, thorns bristling.')
clip('cb_guard_counter', 600, [
    k(0, merge(WIDE, grip(-84, -2, -86))),
    k(100, merge(LOW, grip(-30, 0, -20), {'chest': r(30, 0), 'root': p(0, -4.5, 2)}), 'EASE_IN', 'thorns'),
    k(600, CB_SETTLE),
], note='The answer: the blade driven down, and the thorns come up under the attacker.')
clip('cb_rising', 700, [
    k(0, merge(LOW, grip(-14, 40, -24), {'chest': r(26, 24), 'root': p(0, -4.5, 0)})),
    k(200, merge(grip(-174, 10, -16), {'chest': r(-24, -6), 'head': r(-18), 'leftThigh': r(-10), 'rightThigh': r(10), 'root': p(0, 2, 2)}), 'EASE_IN', 'cut'),
    k(700, CB_SETTLE),
], note='Rising Cut: up from a crouch (server: the cut at tick 4).')
clip('cb_ult_raise', 900, [
    k(400, merge(WIDE, grip(-178, 0, -12), {'chest': r(-16, 0), 'head': r(-24), 'root': p(0, -1, 0)}), 'EASE_OUT'),
    k(900, merge(WIDE, grip(-180, 0, -8), {'chest': r(-18, 0), 'head': r(-26), 'root': p(0, -0.5, 0)})),
], hold=True, priority='SPECIAL', note='Black Thorn: the blade raised high in both hands while the thorns gather.')
clip('cb_ult_plunge', 1400, [
    k(0, merge(WIDE, grip(-180, 0, -8), {'chest': r(-18)})),
    k(100, merge(LOW, grip(-6, 0, 0), {'chest': r(56, 0), 'hips': r(14), 'head': r(24), 'root': p(0, -6.5, 2)}), 'EASE_IN', 'plunge'),
    k(1000, merge(LOW, grip(-6, 0, 0), {'chest': r(54, 0), 'head': r(20), 'root': p(0, -6.5, 2)})),
    k(1400, CB_SETTLE),
], priority='SPECIAL', note='Driven into the ground, held there while the rings of thorns tear outward.')

# The stance while the blade is drawn (sampled under everything else by the client, upper body only).
clip('stance_cursed_blade_idle', 2400, [
    k(0, merge(grip(-40, -14, -34), {'chest': r(4, 12), 'head': r(2, -6)})),
    k(1200, merge(grip(-43, -14, -36), {'chest': r(6, 13), 'head': r(3, -6)}), 'EASE_IN_OUT'),
    k(2400, merge(grip(-40, -14, -34), {'chest': r(4, 12), 'head': r(2, -6)}), 'EASE_IN_OUT'),
], priority='IDLE', layer='UPPER_BODY', loop=True, blend_in=200, blend_out=200,
    note='Idle: the blade held low across the body in both hands, breathing.')
clip('stance_cursed_blade_walk', 1000, [
    k(0, merge(grip(-36, -12, -34), {'chest': r(6, 10)})),
    k(500, merge(grip(-40, -16, -36), {'chest': r(7, 14)}), 'EASE_IN_OUT'),
    k(1000, merge(grip(-36, -12, -34), {'chest': r(6, 10)}), 'EASE_IN_OUT'),
], priority='IDLE', layer='UPPER_BODY', loop=True, blend_in=200, blend_out=200,
    note='Walking: the same hold, swaying with the steps.')
clip('stance_cursed_blade_run', 600, [
    k(0, merge(grip(-20, 30, -50), {'chest': r(18, 20), 'head': r(-6, -12)})),
    k(300, merge(grip(-24, 26, -52), {'chest': r(20, 16), 'head': r(-6, -10)}), 'EASE_IN_OUT'),
    k(600, merge(grip(-20, 30, -50), {'chest': r(18, 20), 'head': r(-6, -12)}), 'EASE_IN_OUT'),
], priority='IDLE', layer='UPPER_BODY', loop=True, blend_in=200, blend_out=200,
    note='Running: leaning in, the blade carried low and back along the right side.')
