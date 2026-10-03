"""Writes Ryu Ishigori's (True Cannon) animation clips. Run: python3 tools/gen_ryu_anims.py

Clips follow docs/ANIMATION.md: keys at milliseconds, bone rotations in degrees [x, y, z] (rightArm x -90 = arm straight
out in front), root pos in pixels [right, up, forward]. Timings follow the moves' own ticks (1 tick = 50 ms) so the
frame a blow lands on is the frame it lands in the code. His fighting style (JJS GIFs): an upright brawler who fires
Cursed Energy Discharge from the cannon of his pompadour by snapping his head forward, and from his fingertip for
Every Last Drop.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'animations', 'ryu')
os.makedirs(OUT, exist_ok=True)

# --- Poses (bone -> {"rot": [...]} / {"pos": [...]}) ---

GUARD = {
    "head": {"rot": [4, 0, 0]},
    "chest": {"rot": [6, 10, 0]},
    "rightArm": {"rot": [-55, -15, 14]},
    "leftArm": {"rot": [-70, 25, -12]},
    "rightThigh": {"rot": [12, 0, 6]},
    "leftThigh": {"rot": [-14, 0, -6]},
    "hips": {"rot": [4, 10, 0]},
    "root": {"pos": [0, -1, 0]},
}
NEUTRAL = {
    "head": {"rot": [0, 0, 0]},
    "chest": {"rot": [0, 0, 0]},
    "rightArm": {"rot": [0, 0, 6]},
    "leftArm": {"rot": [0, 0, -6]},
    "rightThigh": {"rot": [0, 0, 2]},
    "leftThigh": {"rot": [0, 0, -2]},
    "hips": {"rot": [0, 0, 0]},
    "root": {"pos": [0, 0, 0]},
}


def merge(*poses):
    out = {}
    for p in poses:
        for k, v in p.items():
            out[k] = dict(out.get(k, {}), **v)
    return out


def right_punch(depth=1.0):
    return {
        "head": {"rot": [6, -10, 0]},
        "chest": {"rot": [8, -40 * depth, 0]},
        "rightArm": {"rot": [-100, -8, 0]},
        "leftArm": {"rot": [-40, 30, -24]},
        "rightThigh": {"rot": [38, 0, 6]},
        "leftThigh": {"rot": [-36, 0, -6]},
        "hips": {"rot": [14, -24 * depth, 0]},
        "root": {"pos": [0, -3, 6 * depth]},
    }


def left_punch(depth=1.0):
    return {
        "head": {"rot": [6, 10, 0]},
        "chest": {"rot": [8, 40 * depth, 0]},
        "leftArm": {"rot": [-100, 8, 0]},
        "rightArm": {"rot": [-40, -30, 24]},
        "rightThigh": {"rot": [-36, 0, 6]},
        "leftThigh": {"rot": [38, 0, -6]},
        "hips": {"rot": [14, 24 * depth, 0]},
        "root": {"pos": [0, -3, 6 * depth]},
    }


def wind_right():
    return {
        "head": {"rot": [6, 20, 0]},
        "chest": {"rot": [4, 46, 0]},
        "rightArm": {"rot": [40, 30, 40]},
        "leftArm": {"rot": [-85, 30, -12]},
        "rightThigh": {"rot": [26, 0, 6]},
        "leftThigh": {"rot": [-30, 0, -6]},
        "hips": {"rot": [8, 34, 0]},
        "root": {"pos": [0, -3, -1.5]},
    }


def slam_down():
    return {
        "head": {"rot": [30, 0, 0]},
        "chest": {"rot": [40, -10, 0]},
        "rightArm": {"rot": [-30, -10, 10]},
        "leftArm": {"rot": [-20, 10, -20]},
        "rightThigh": {"rot": [-60, 0, 8]},
        "leftThigh": {"rot": [30, 0, -8]},
        "hips": {"rot": [30, 0, 0]},
        "root": {"pos": [0, -8, 4]},
    }


def overhead():
    return {
        "head": {"rot": [-12, 0, 0]},
        "chest": {"rot": [-14, 0, 0]},
        "rightArm": {"rot": [-170, 0, 10]},
        "leftArm": {"rot": [-160, 0, -12]},
        "rightThigh": {"rot": [-10, 0, 6]},
        "leftThigh": {"rot": [10, 0, -6]},
        "hips": {"rot": [-6, 0, 0]},
        "root": {"pos": [0, 1, -1]},
    }


def head_cannon(recoil):
    """The pompadour's cannon fired: the head snaps forward and down, the body braces back against the recoil."""
    return {
        "head": {"rot": [28 * recoil, 0, 0]},
        "chest": {"rot": [-8 * recoil, 0, 0]},
        "rightArm": {"rot": [-20, 0, 22 + 8 * recoil]},
        "leftArm": {"rot": [-20, 0, -22 - 8 * recoil]},
        "rightThigh": {"rot": [16, 0, 8]},
        "leftThigh": {"rot": [-18, 0, -8]},
        "hips": {"rot": [-4 * recoil, 0, 0]},
        "root": {"pos": [0, -2, -2.5 * recoil]},
    }


def point(arm_x=-92):
    """Every Last Drop: right arm out, finger at the target, the left hand bracing the wrist."""
    return {
        "head": {"rot": [2, -14, 0]},
        "chest": {"rot": [2, -30, 0]},
        "rightArm": {"rot": [arm_x, -20, 0]},
        "leftArm": {"rot": [arm_x + 16, 46, -4]},
        "rightThigh": {"rot": [26, 0, 8]},
        "leftThigh": {"rot": [-30, 0, -8]},
        "hips": {"rot": [6, -24, 0]},
        "root": {"pos": [0, -2.5, 0]},
    }


def kick():
    return {
        "head": {"rot": [4, 0, 0]},
        "chest": {"rot": [-16, 30, 0]},
        "rightArm": {"rot": [-30, -10, 50]},
        "leftArm": {"rot": [-70, 30, -20]},
        "rightThigh": {"rot": [-105, 10, 10]},
        "leftThigh": {"rot": [8, 0, -4]},
        "hips": {"rot": [-12, 30, 0]},
        "root": {"pos": [0, 0, 2]},
    }


def run():
    return {
        "head": {"rot": [8, 0, 0]},
        "chest": {"rot": [22, 0, 0]},
        "rightArm": {"rot": [40, 0, 14]},
        "leftArm": {"rot": [-70, 0, -14]},
        "rightThigh": {"rot": [-50, 0, 4]},
        "leftThigh": {"rot": [36, 0, -4]},
        "hips": {"rot": [18, 0, 0]},
        "root": {"pos": [0, -2, 3]},
    }


def run_b():
    return {
        "head": {"rot": [8, 0, 0]},
        "chest": {"rot": [22, 0, 0]},
        "rightArm": {"rot": [-70, 0, 14]},
        "leftArm": {"rot": [40, 0, -14]},
        "rightThigh": {"rot": [36, 0, 4]},
        "leftThigh": {"rot": [-50, 0, -4]},
        "hips": {"rot": [18, 0, 0]},
        "root": {"pos": [0, -1, 3]},
    }


def shoulder_ram():
    """Tetsuzanko-style back clash: the back driven into them."""
    return {
        "head": {"rot": [10, 70, 0]},
        "chest": {"rot": [10, 85, 0]},
        "rightArm": {"rot": [-20, 0, 40]},
        "leftArm": {"rot": [-60, 40, -30]},
        "rightThigh": {"rot": [-30, 0, 16]},
        "leftThigh": {"rot": [30, 0, -16]},
        "hips": {"rot": [10, 70, 0]},
        "root": {"pos": [0, -5, 7]},
    }


def toss():
    return {
        "head": {"rot": [-14, 0, 0]},
        "chest": {"rot": [-20, -10, 0]},
        "rightArm": {"rot": [-150, -10, 20]},
        "leftArm": {"rot": [-140, 10, -20]},
        "rightThigh": {"rot": [10, 0, 6]},
        "leftThigh": {"rot": [-20, 0, -6]},
        "hips": {"rot": [-10, 0, 0]},
        "root": {"pos": [0, 1, 1]},
    }


def comb():
    """Restyle: one hand running a comb back through the pompadour, chin up."""
    return {
        "head": {"rot": [-16, 6, 0]},
        "chest": {"rot": [-4, 0, 0]},
        "rightArm": {"rot": [-160, -30, -30]},
        "leftArm": {"rot": [-10, 0, -40]},
        "hips": {"rot": [0, 0, 0]},
        "root": {"pos": [0, 0, 0]},
    }


def comb_back():
    return merge(comb(), {"rightArm": {"rot": [-170, -10, -50]}, "head": {"rot": [-22, 6, 0]}})


def knuckles():
    """Awakened Restyle: cracking his knuckles."""
    return {
        "head": {"rot": [10, 0, 0]},
        "chest": {"rot": [6, 0, 0]},
        "rightArm": {"rot": [-70, -40, 20]},
        "leftArm": {"rot": [-70, 40, -20]},
        "root": {"pos": [0, -1, 0]},
    }


# --- Clips ---


def clip(name, duration, keys, hold=False, priority="SPECIAL", blend_in=40, blend_out=160):
    data = {"name": name, "duration": duration, "blendIn": blend_in, "blendOut": blend_out, "priority": priority}
    if hold:
        data["hold"] = True
    data["keys"] = [dict({"t": t, "bones": b}, **({"ease": e} if e else {})) for t, e, b in keys]
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(data, f, indent=2)


# Cursed Energy Discharge: the third M1, a ray from the pompadour.
clip("ryu_m1_ray", 500, [
    (0, None, head_cannon(-0.5)),
    (100, "EASE_IN", head_cannon(1.0)),
    (220, "EASE_OUT", head_cannon(0.7)),
    (500, "EASE_OUT", GUARD),
], priority="ATTACK")

# Granite Blast: aiming (held), then the shot (tap / charged), and the dash variant.
clip("ryu_granite_aim", 600, [
    (0, None, head_cannon(-0.3)),
    (300, "EASE_OUT", merge(head_cannon(-0.6), {"rightThigh": {"rot": [22, 0, 10]}, "leftThigh": {"rot": [-24, 0, -10]}})),
    (600, "EASE_IN_OUT", merge(head_cannon(-0.7), {"rightThigh": {"rot": [22, 0, 10]}, "leftThigh": {"rot": [-24, 0, -10]}})),
], hold=True)
clip("ryu_granite_fire", 450, [
    (0, None, head_cannon(-0.6)),
    (60, "EASE_IN", head_cannon(1.0)),
    (200, "EASE_OUT", head_cannon(0.8)),
    (450, "EASE_OUT", GUARD),
])
clip("ryu_granite_fire_held", 700, [
    (0, None, head_cannon(-0.9)),
    (60, "EASE_IN", merge(head_cannon(1.3), {"root": {"pos": [0, -3, -5]}})),
    (320, "EASE_OUT", head_cannon(1.0)),
    (700, "EASE_OUT", GUARD),
])
clip("ryu_granite_dash", 800, [
    (0, None, head_cannon(-0.8)),
    (200, "EASE_IN", merge(head_cannon(1.2), {"head": {"rot": [-30, 0, 0]}})),
    (250, "EASE_OUT", run()),
    (800, "EASE_OUT", GUARD),
])

# Unsatisfied: three blows (ticks 5, 9, 13), a back clash (20), a toss (26), done at 36.
clip("ryu_unsatisfied", 1800, [
    (0, None, wind_right()),
    (250, "EASE_IN", right_punch()),
    (330, "EASE_OUT", GUARD),
    (450, "EASE_IN", left_punch()),
    (530, "EASE_OUT", GUARD),
    (650, "EASE_IN", right_punch(1.2)),
    (850, "EASE_OUT", merge(GUARD, {"chest": {"rot": [6, -60, 0]}, "hips": {"rot": [4, -50, 0]}})),
    (1000, "EASE_IN", shoulder_ram()),
    (1150, "EASE_OUT", merge(shoulder_ram(), {"root": {"pos": [0, -4, 4]}})),
    (1300, "EASE_IN", toss()),
    (1800, "EASE_OUT", GUARD),
])
clip("ryu_unsatisfied_whiff", 600, [
    (0, None, wind_right()),
    (250, "EASE_IN", right_punch()),
    (600, "EASE_OUT", GUARD),
])

# Second Helping: rush in and slam them into the floor (ground), or an air punch.
clip("ryu_second_helping", 600, [
    (0, None, run()),
    (200, "LINEAR", run_b()),
    (400, "LINEAR", run()),
    (600, "LINEAR", merge(run_b(), {"rightArm": {"rot": [-150, 0, 14]}})),
], hold=True)
clip("ryu_second_helping_slam", 700, [
    (0, None, overhead()),
    (120, "EASE_IN", slam_down()),
    (400, "EASE_OUT", slam_down()),
    (700, "EASE_OUT", GUARD),
])
clip("ryu_second_helping_air", 500, [
    (0, None, merge(wind_right(), {"rightThigh": {"rot": [-60, 0, 6]}, "leftThigh": {"rot": [-20, 0, -6]}})),
    (500, "EASE_OUT", merge(wind_right(), {"rightThigh": {"rot": [-70, 0, 6]}, "leftThigh": {"rot": [-30, 0, -6]}})),
], hold=True)
clip("ryu_second_helping_punch", 600, [
    (0, None, wind_right()),
    (90, "EASE_IN", right_punch(1.2)),
    (350, "EASE_OUT", right_punch(1.0)),
    (600, "EASE_OUT", GUARD),
])
clip("ryu_second_helping_whiff", 600, [
    (0, None, overhead()),
    (150, "EASE_IN", slam_down()),
    (600, "EASE_OUT", GUARD),
])

# Appetizer: two blasts from the pompadour (ticks 8, 17), then the ray out of the ground (32), done at 44.
clip("ryu_appetizer", 2200, [
    (0, None, GUARD),
    (300, "EASE_OUT", head_cannon(-0.6)),
    (400, "EASE_IN", head_cannon(1.0)),
    (600, "EASE_OUT", head_cannon(-0.6)),
    (850, "EASE_IN", head_cannon(1.0)),
    (1100, "EASE_OUT", overhead()),
    (1600, "EASE_IN", merge(slam_down(), {"head": {"rot": [10, 0, 0]}})),
    (2200, "EASE_OUT", GUARD),
])

# Restyle: a pose (cool off), the comb (from 100%), and the awakened knuckle crack.
clip("ryu_restyle_pose", 1000, [
    (0, None, GUARD),
    (200, "EASE_OUT", comb()),
    (600, "EASE_IN_OUT", comb_back()),
    (1000, "EASE_OUT", NEUTRAL),
])
clip("ryu_restyle_comb", 2750, [
    (0, None, GUARD),
    (250, "EASE_OUT", comb()),
    (700, "EASE_IN_OUT", comb_back()),
    (1100, "EASE_IN_OUT", comb()),
    (1600, "EASE_IN_OUT", comb_back()),
    (2100, "EASE_IN_OUT", comb()),
    (2500, "EASE_IN_OUT", comb_back()),
    (2750, "EASE_OUT", NEUTRAL),
])
clip("ryu_restyle_knuckles", 1100, [
    (0, None, GUARD),
    (250, "EASE_OUT", knuckles()),
    (450, "EASE_IN", merge(knuckles(), {"rightArm": {"rot": [-80, -50, 30]}, "leftArm": {"rot": [-80, 50, -30]}})),
    (650, "EASE_OUT", knuckles()),
    (1100, "EASE_OUT", GUARD),
])

# Every Last Drop: the point (counter / aim), the long charge, the shot.
clip("ryu_eld_point", 400, [
    (0, None, GUARD),
    (400, "EASE_OUT", point()),
], hold=True)
clip("ryu_eld_charge", 2500, [
    (0, None, GUARD),
    (400, "EASE_OUT", merge(point(-80), {"root": {"pos": [0, -3, -1]}})),
    (1800, "EASE_IN_OUT", merge(point(-90), {"root": {"pos": [0, -4, -1]}, "chest": {"rot": [6, -34, 0]}})),
    (2500, "EASE_IN", merge(point(-92), {"root": {"pos": [0, -4.5, -1.5]}})),
], hold=True)
clip("ryu_eld_fire", 600, [
    (0, None, merge(point(-92), {"root": {"pos": [0, -4.5, -1.5]}})),
    (80, "EASE_IN", merge(point(-100), {"root": {"pos": [0, -4, -4]}, "chest": {"rot": [-6, -30, 0]}, "head": {"rot": [-6, -14, 0]}})),
    (600, "EASE_OUT", merge(point(-94), {"root": {"pos": [0, -4, -3]}})),
], hold=True)

# --- Decadence ---

# "What are you after?": the floor slam (ground) / leap and lunge (air), the trade or the miss.
clip("ryu_after_slam", 500, [
    (0, None, overhead()),
    (300, "EASE_IN", slam_down()),
    (500, "EASE_OUT", slam_down()),
], hold=True)
clip("ryu_after_leap", 400, [
    (0, None, slam_down()),
    (200, "EASE_OUT", merge(toss(), {"rightThigh": {"rot": [-70, 0, 8]}, "leftThigh": {"rot": [-30, 0, -8]}})),
    (400, "EASE_OUT", merge(wind_right(), {"rightThigh": {"rot": [-60, 0, 8]}})),
], hold=True)
clip("ryu_after_lunge", 400, [
    (0, None, wind_right()),
    (400, "EASE_OUT", merge(wind_right(), {"root": {"pos": [0, -2, 4]}, "chest": {"rot": [20, 46, 0]}})),
], hold=True)
clip("ryu_after_trade", 900, [
    (0, None, wind_right()),
    (100, "EASE_IN", right_punch(1.3)),
    (450, "EASE_OUT", merge(right_punch(1.0), {"head": {"rot": [-20, 20, 0]}})),
    (650, "EASE_IN", toss()),
    (900, "EASE_OUT", GUARD),
])
clip("ryu_after_miss", 700, [
    (0, None, wind_right()),
    (150, "EASE_IN", right_punch(1.3)),
    (700, "EASE_OUT", GUARD),
])

# "I had no idea...": an armoured wind-up, the punch, then the exchange of blows.
clip("ryu_no_idea_windup", 800, [
    (0, None, GUARD),
    (800, "EASE_OUT", merge(wind_right(), {"root": {"pos": [0, -4, -2]}})),
], hold=True)
clip("ryu_no_idea_punch", 600, [
    (0, None, wind_right()),
    (80, "EASE_IN", right_punch(1.3)),
    (600, "EASE_OUT", right_punch(1.0)),
], hold=True)
keys = [(0, None, GUARD)]
t = 0
for k in range(7):
    t += 120
    keys.append((t, "EASE_IN", right_punch() if k % 2 == 0 else left_punch()))
    t += 80
    keys.append((t, "EASE_OUT", merge(GUARD, {"head": {"rot": [-16, 20 if k % 2 else -20, 0]}})))
keys.append((t + 300, "EASE_OUT", GUARD))
clip("ryu_no_idea_exchange", t + 300, keys)

# "This is what dessert is like!": the run in, the kick (or a miss), the swing and the flurry.
clip("ryu_dessert_run", 400, [
    (0, None, run()),
    (200, "LINEAR", run_b()),
    (400, "LINEAR", run()),
], hold=True)
clip("ryu_dessert_kick", 500, [
    (0, None, run()),
    (120, "EASE_IN", kick()),
    (500, "EASE_OUT", GUARD),
])
clip("ryu_dessert_miss", 600, [
    (0, None, run()),
    (150, "EASE_IN", kick()),
    (600, "EASE_OUT", GUARD),
])
clip("ryu_dessert_swing", 500, [
    (0, None, wind_right()),
    (150, "EASE_IN", merge(right_punch(1.4), {"rightArm": {"rot": [-90, -60, 0]}})),
    (500, "EASE_OUT", GUARD),
])
keys = [(0, None, GUARD)]
t = 0
for k in range(8):
    t += 90
    keys.append((t, "EASE_IN", right_punch(0.8) if k % 2 == 0 else left_punch(0.8)))
    t += 60
    keys.append((t, "EASE_OUT", GUARD))
keys.append((t + 200, "EASE_IN", merge(kick(), {"root": {"pos": [0, 0, 4]}})))
keys.append((t + 600, "EASE_OUT", GUARD))
clip("ryu_dessert_flurry", t + 600, keys)

# "You weren't invited.": the wind-up (held to charge), the lunge, the punch.
clip("ryu_invited_windup", 900, [
    (0, None, GUARD),
    (500, "EASE_OUT", merge(wind_right(), {"chest": {"rot": [4, 60, 0]}, "rightArm": {"rot": [50, 40, 46]}})),
    (900, "EASE_IN_OUT", merge(wind_right(), {"chest": {"rot": [4, 66, 0]}, "rightArm": {"rot": [56, 44, 50]}, "root": {"pos": [0, -4, -2]}})),
], hold=True)
clip("ryu_invited_lunge", 300, [
    (0, None, wind_right()),
    (300, "EASE_OUT", merge(wind_right(), {"root": {"pos": [0, -3, 6]}, "chest": {"rot": [16, 50, 0]}})),
], hold=True)
clip("ryu_invited_punch", 800, [
    (0, None, wind_right()),
    (70, "EASE_IN", merge(right_punch(1.5), {"rightArm": {"rot": [-96, 20, -10]}})),
    (450, "EASE_OUT", right_punch(1.3)),
    (800, "EASE_OUT", GUARD),
])

print('ryu clips written to', os.path.abspath(OUT))
