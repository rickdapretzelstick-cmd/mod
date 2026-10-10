"""Animation clips for the cursed tools' movesets (animations/tool/*.json), and the draw and holster.

    python3 tools/gen_tool_kit_anims.py

Every tool move has its own clip, shaped by how the tool is held:
- Slaughter Demon (one hand, fast): low, darting, the blade arm leading; the free hand stays back for balance.
- Cursed Cleaver (two hands, heavy): wide stances, both hands on the haft, big wind-ups, the body dropping into blows.
- Cursed Rifle (two hands, ranged): shouldered, compact; the stock and the lenses are what it fights with up close.
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


def clip(name, duration, keys, priority='ATTACK', hold=False, blend_in=40, blend_out=160, note=None):
    c = {'name': name, 'duration': duration}
    if note:
        c['note'] = note
    if hold:
        c['hold'] = True
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
