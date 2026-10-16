#!/usr/bin/env python3
"""
Measures a Roblox (R6) animation from footage and writes it as a clip of this mod, by colour: an R6 body is six rigid
boxes, so when its parts have known colours (the plain "noob" of the JJS wiki GIFs: red torso, yellow head and arms,
lime legs; or any avatar with per-part body colours) each frame's colour masks pin its pose. The fitter renders this
mod's own bone hierarchy and rotation math (client.anim.rig.Rig: hips -> head/arms/thighs, Euler ZYX, y down, -z
forward) with R6 proportions, in those colours, through a fitted orthographic camera, and searches each frame for the
joint angles whose coloured silhouette matches the frame best (from the last frame's pose, so the left/right limbs
keep their identity). The angles are the clip's bone rotations directly: no retargeting step.

    python3 tools/r6_fit.py <video.gif|mp4> --out build/fit/<name>      # every frame: params.json + sheet.png overlays
    python3 tools/r6_fit.py <video> --range 30:62 --clip <group>/<name> [--merge] [--speed 1.0] [--out build/fit/<name>]

  --range a:b     frames of one move (pick them from the overlay sheet of a full pass)
  --warmup w      start tracking at frame w (< a): a calm, upright frame of the same shot pins the camera best
  --clip g/n      write assets/jjk/animations/g/n.json (keys reduced to within --tol degrees, LINEAR between them)
  --merge         keep that clip's other tracks (forearms, shins, hands, feet, chest, root) and its header
  --at x,y        where the character is in the first frame (default: the biggest patch of its colours)
  --flip          start facing the other way (an R6 body is front/back symmetric; the face decides when visible)
  --palette f     part colours (JSON like PALETTE; tools/r6_palettes/) for other avatars
  --fps n         frame rate of the footage when it is not in the file (wiki GIFs: 50)
  --clip also writes <out>/conversion.json: tools/build_anim_viewer.py puts every build/fit/*/conversion.json in the
                  viewer's Conversions tab (the footage with the fit over it, beside the clip on the mod's rig)
  --preview n     like --clip but the clip stays in <out>/clip.json (Conversions tab only, not the game's assets)
  --compare       with --clip: render the written clip in the animation viewer from each frame's fitted camera and
                  stack it under the footage (<out>/compare.png): the end-to-end check, footage -> clip -> game rig

Recording reference footage (what makes the fit accurate; tools/r6_fit_selftest.py measures it):
  - Avatar: one colour per body part, tools/r6_palettes/six.json (Avatar > Skin Tone > Advanced), no clothes/accessories.
  - Camera: from the side of the move, near eye level, still; zoomed so the character is >= 150 px tall; 60 fps.
    (Swings toward or away from the camera foreshorten: a side view sees them whole.)
  - One move per clip, starting and ending standing; a dummy or VFX in front of the body only costs those frames.

The sheet (<out>/sheet.png) draws the fitted boxes over each frame with the frame number and its mismatch: use it to
judge the fit and choose the range; then view the clip in the animation viewer (tools/build_anim_viewer.py).
R6 limbs have no elbows or knees: the fit gives the upper limbs and body; bends come from --merge or authoring.
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageSequence
from scipy import ndimage

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ANIMS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'jjk', 'animations')

# Part colours: classes in order, each an HSV range (PIL scale: h, s, v 0-255; a hue range may wrap, lo > hi) and the
# body parts wearing it. "face" (dark marks enclosed by the head's colour) is optional. The default is the plain noob of
# the JJS wiki GIFs; avatars with one colour per part (tools/r6_palettes/six.json) are what makes left and right certain.
PALETTE = {'classes': [
    {'name': 'torso', 'parts': ['torso'], 'h': [245, 12], 's': [110, 255], 'v': [60, 255], 'show': [255, 60, 60]},
    {'name': 'arms', 'parts': ['head', 'rightArm', 'leftArm'], 'h': [28, 42], 's': [100, 255], 'v': [90, 255], 'show': [255, 240, 60]},
    {'name': 'legs', 'parts': ['rightLeg', 'leftLeg'], 'h': [42, 66], 's': [90, 255], 'v': [80, 255], 'show': [120, 255, 80]},
    {'name': 'face', 'parts': ['face'], 'v': [0, 70], 'show': [40, 40, 255]}],
    'adaptive': [['arms', 'legs']]}
BG = 0
CLASS_OF, FACE, HEAD, BODY_CLASSES, COST, SHOW, UNKNOWN = {}, -1, 0, [], None, {}, 99
# Each part's six box sides' classes (right, left, top, bottom, front, back: the order of FACES); a palette with
# 'sides' gives every side its own colour (clothing painted per side), otherwise all six are the part's colour.
SIDE_CLASS = {}
# The avatar's proportions: per body group, its boxes' size along x, y, z against the standard R6 sizes (1 = standard),
# measured once from a calibration recording (tools/calibrate_body.py) and kept in the palette as 'proportions'.
# Boxes scale about their own joint (an arm grows away from its shoulder, a leg down from its hip).
PROPORTION = {}
GROUP_OF = {'torso': 'torso', 'head': 'head', 'face': 'head', 'rightArm': 'arms', 'leftArm': 'arms',
            'rightLeg': 'legs', 'leftLeg': 'legs'}


def use_palette(pal):
    """Sets the part -> class map and the mismatch costs for a palette."""
    global FACE, HEAD, BODY_CLASSES, COST, UNKNOWN
    CLASS_OF.clear()
    SHOW.clear()
    FACE = -1
    for i, c in enumerate(pal['classes'], 1):
        for part in c['parts']:
            CLASS_OF[part] = i
        SHOW[i] = tuple(c.get('show', (255, 255, 255)))
        if c['name'] == 'face':
            FACE = i
    CLASS_OF.setdefault('face', CLASS_OF['head'])
    HEAD = CLASS_OF['head']
    PROPORTION.clear()
    PROPORTION.update({g: list(v) for g, v in pal.get('proportions', {}).items()})
    fit_rest()
    SIDE_CLASS.clear()
    names = {c['name']: i for i, c in enumerate(pal['classes'], 1)}
    for part, cls in CLASS_OF.items():
        sides = pal.get('sides', {}).get(part)
        SIDE_CLASS[part] = [names[n] for n in sides] if sides else [cls] * 6
    BODY_CLASSES = [i for i in range(1, len(pal['classes']) + 1) if i != FACE]
    UNKNOWN = len(pal['classes']) + 1
    n = len(pal['classes']) + 1
    # Cost of observing class o where class r is rendered (rows o, columns r): background under the body is cheap
    # (other things occlude it); the face may hide in the head.
    COST = np.ones((n + 1, n), np.float32)
    np.fill_diagonal(COST, 0)
    COST[BG, 1:] = .6
    COST[n] = 0  # UNKNOWN: something in front of the body may hide any part (see Fitter.prepare)
    if FACE > 0:
        COST[FACE, HEAD] = COST[HEAD, FACE] = .3


# The fit rig: this mod's bone math with Roblox R6 pivots and boxes (px, y down, -z forward; 1 stud = 6 px).
REST = {'root': (0, 24, 0), 'hips': (0, 12, 0), 'head': (0, 0, 0), 'rightArm': (-6, 3, 0), 'leftArm': (6, 3, 0),
        'rightThigh': (-6, 12, 0), 'leftThigh': (6, 12, 0)}
STD_REST = dict(REST)


def fit_rest():
    """The pivots moved to the avatar's proportions, so the boxes still meet as an R6 body's do: the neck and the
    shoulders' tops on the torso's top, the arms and legs flush with its sides, the feet on the ground. A box scales
    about its own pivot; without this a taller torso would swallow the neck and longer arms would poke up past the
    shoulders, and the fit would tip parts to hide the step (R6_JOINTS_FOLLOW=1; off by default until measured)."""
    REST.clear()
    REST.update(STD_REST)
    if os.environ.get('R6_JOINTS_FOLLOW', '0') == '0' or not PROPORTION:
        return
    t, a, l = (PROPORTION.get(g, (1.0, 1.0, 1.0)) for g in ('torso', 'arms', 'legs'))
    top = 12 - 12 * t[1]
    REST.update({'root': (0, 12 + 12 * l[1], 0), 'head': (0, top, 0),
                 'rightArm': (-6 * t[0], top + 3 * a[1], 0), 'leftArm': (6 * t[0], top + 3 * a[1], 0),
                 'rightThigh': (-6 * t[0], 12, 0), 'leftThigh': (6 * t[0], 12, 0)})
    T = sys.modules.get('r6_fit_torch')
    if T is not None:
        T.refresh_offsets()
PARENT = {'root': None, 'hips': 'root', 'head': 'hips', 'rightArm': 'hips', 'leftArm': 'hips', 'rightThigh': 'hips',
          'leftThigh': 'hips'}
# The head is rounded, not a box: two crossed boxes (a plus seen from above) cut its corners, so a box's corners sticking
# out past the real head never reward tilting it.
PARTS = [('hips', (-6, -12, -3), (6, 0, 3), 'torso'), ('head', (-3.6, -7, -2.6), (3.6, 0, 2.6), 'head'),
         ('head', (-2.4, -5.4, -3.9), (2.4, -1.6, -3.5), 'face'),
         ('rightArm', (-6, -3, -3), (0, 9, 3), 'rightArm'), ('leftArm', (0, -3, -3), (6, 9, 3), 'leftArm'),
         ('rightThigh', (0, 0, -3), (6, 12, 3), 'rightLeg'), ('leftThigh', (-6, 0, -3), (0, 12, 3), 'leftLeg'),
         ('head', (-2.6, -7, -3.6), (2.6, 0, 3.6), 'head')]
# Parameters: camera (yaw, pitch, scale, x, y, perspective = 1 / its distance in rig px; 0 is orthographic) then joint
# angles in degrees as (bone, axis): every bone turns on all three axes (the limbs' twist shows in their shading).
CAM = ['cyaw', 'cpitch', 'scale', 'tx', 'ty', 'persp']
JOINTS = [('hips', 0), ('hips', 1), ('hips', 2), ('head', 0), ('head', 1), ('rightArm', 0), ('rightArm', 2),
          ('leftArm', 0), ('leftArm', 2), ('rightThigh', 0), ('rightThigh', 2), ('leftThigh', 0), ('leftThigh', 2),
          ('head', 2), ('rightArm', 1), ('leftArm', 1), ('rightThigh', 1), ('leftThigh', 1)]
JI = {j: len(CAM) + i for i, j in enumerate(JOINTS)}
NP = len(CAM) + len(JOINTS)
LIMITS = {('hips', 0): (-150, 150), ('hips', 1): (-180, 180), ('hips', 2): (-120, 120), ('head', 0): (-50, 50), ('head', 1): (-70, 70),
          ('rightArm', 0): (-200, 60), ('leftArm', 0): (-200, 60), ('rightArm', 2): (-30, 160), ('leftArm', 2): (-160, 30),
          ('rightThigh', 0): (-150, 70), ('leftThigh', 0): (-150, 70), ('rightThigh', 2): (-20, 110), ('leftThigh', 2): (-110, 20),
          ('head', 2): (-40, 40), ('rightArm', 1): (-100, 100), ('leftArm', 1): (-100, 100), ('rightThigh', 1): (-80, 80),
          ('leftThigh', 1): (-80, 80)}
MODEL_TO_WORLD = np.diag([-1.0, -1.0, 1.0])  # model (y down, as the game's renderer flips it) -> world (y up)
# Each box face: (axis, side) with its corners in order around it, as (lo/hi per axis) picks.
FACES = []
for _ax in range(3):
    _b, _c = [k for k in range(3) if k != _ax]
    for _side in (0, 1):
        corners = []
        for u, v in ((0, 0), (1, 0), (1, 1), (0, 1)):
            pick = [0, 0, 0]
            pick[_ax], pick[_b], pick[_c] = _side, u, v
            corners.append(pick[0] * 4 + pick[1] * 2 + pick[2])
        normal = [0.0, 0.0, 0.0]
        normal[_ax] = 1.0 if _side else -1.0
        FACES.append((corners, np.array(normal)))
use_palette(PALETTE)


def rot(x, y, z):
    x, y, z = (math.radians(a) for a in (x, y, z))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def angles(p):
    a = {b: [0.0, 0.0, 0.0] for b in REST}
    for (b, ax), v in zip(JOINTS, p[len(CAM):len(CAM) + len(JOINTS)]):
        a[b][ax] = v
    return a


# Joint offsets: Roblox animations move a limb's joint as well as turning it (a Motor6D's position), so a limb can
# come away from the body. Optional: params longer than NP carry, after the joint angles, each OFF_BONES bone's
# offset (x, y, z, rig px in its parent's frame: y down, -z forward); shorter params mean no offsets.
OFF_BONES = ['head', 'rightArm', 'leftArm', 'rightThigh', 'leftThigh']
NOFF = 3 * len(OFF_BONES)


def offsets(p):
    o = {b: np.zeros(3) for b in REST}
    extra = np.asarray(p[NP:NP + NOFF], float) if len(p) > NP else ()
    for i, b in enumerate(OFF_BONES):
        if len(extra) >= 3 * i + 3:
            o[b] = extra[3 * i:3 * i + 3]
    return o


def pose_world(p):
    a = angles(p)
    off = offsets(p)
    world = {}
    for b in REST:
        r, par = np.array(REST[b], float), PARENT[b]
        m = np.eye(4)
        m[:3, :3] = rot(*a[b])
        m[:3, 3] = r - (np.array(REST[par], float) if par else 0) + off[b]
        world[b] = world[par] @ m if par else m
    return world


def camera_of(p):
    """World -> camera rotation and the projection of camera points to footage pixels."""
    cyaw, cpitch, s, tx, ty, persp = p[:6]
    view = rot(cpitch, 0, 0) @ rot(0, cyaw, 0)

    def project(v):
        f = s / np.maximum(0.2, 1 - v[2] * persp)  # nearer (+z) is bigger
        return np.stack([tx + f * v[0], ty - f * v[1]], 1)
    return view, project


def scaled_box(part, lo, hi):
    """A part's box at the avatar's proportions (PROPORTION), scaled about its joint."""
    s = PROPORTION.get(GROUP_OF.get(part, part), (1.0, 1.0, 1.0))
    return tuple(a * f for a, f in zip(lo, s)), tuple(a * f for a, f in zip(hi, s))


def corners_of(world, bone, lo, hi):
    c = np.array([[x, y, z, 1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]).T
    return MODEL_TO_WORLD @ (world[bone] @ c)[:3]


def boxes(p):
    """Projected parts as outlines: [(depth, polygon, class)] in footage pixels (overlays)."""
    world = pose_world(p)
    view, project = camera_of(p)
    out = []
    for bone, lo, hi, part in PARTS:
        lo, hi = scaled_box(part, lo, hi)
        v = view @ corners_of(world, bone, lo, hi)
        out.append((v[2].mean() + (0.01 if part == 'face' else 0), hull(project(v)), CLASS_OF[part]))
    out.sort(key=lambda e: e[0])
    return out


def faces(p, light):
    """The visible box faces, far to near: [(polygon, class, shade 0-1)]; shade is ambient + diffuse from the light."""
    world = pose_world(p)
    view, project = camera_of(p)
    ldir, amb = light
    persp = p[5]
    out = []
    for bone, lo, hi, part in PARTS:
        lo, hi = scaled_box(part, lo, hi)
        wc = corners_of(world, bone, lo, hi)
        v = view @ wc
        uv = project(v)
        rotw = MODEL_TO_WORLD @ world[bone][:3, :3]
        for fi, (idx, normal) in enumerate(FACES):
            nw = rotw @ normal
            nc = view @ nw
            centre = v[:, idx].mean(1)
            toward = -centre + np.array([0, 0, 1 / persp]) if persp > 1e-6 else np.array([0, 0, 1.0])
            if nc @ toward <= 0:
                continue
            shade = amb + (1 - amb) * max(0.0, float(nw @ ldir))
            out.append((centre[2] + (0.01 if part == 'face' else 0), [tuple(uv[k]) for k in idx], SIDE_CLASS[part][fi], shade))
    out.sort(key=lambda e: e[0])
    return [(poly, cls, sh) for _, poly, cls, sh in out]


def draw_frame(p, light, size=400, bg=(45, 45, 48)):
    """Footage-like picture of a pose in the palette's colours, shaded (the self-test's input)."""
    img = Image.new('RGB', (size, size), bg)
    d = ImageDraw.Draw(img)
    for poly, cls, sh in faces(p, light):
        d.polygon(poly, fill=tuple(int(c * sh) for c in SHOW[cls]))
    return img


DEFAULT_LIGHT = (np.array([0.35, 0.8, 0.48]) / np.linalg.norm([0.35, 0.8, 0.48]), 0.5)


def hull(pts):
    pts = sorted(map(tuple, pts))
    def half(seq):
        h = []
        for q in seq:
            while len(h) >= 2 and (h[-1][0] - h[-2][0]) * (q[1] - h[-2][1]) - (h[-1][1] - h[-2][1]) * (q[0] - h[-2][0]) <= 0:
                h.pop()
            h.append(q)
        return h
    lower, upper = half(pts), half(reversed(pts))
    return lower[:-1] + upper[:-1]


def render(p, roi, light=DEFAULT_LIGHT):
    """Class * 1000 + shade (0-255) per pixel of the ROI."""
    x0, y0, k, w, h = roi
    img = Image.new('I', (w, h), 0)
    d = ImageDraw.Draw(img)
    for poly, cls, sh in faces(p, light):
        d.polygon([((u - x0) * k, (v - y0) * k) for u, v in poly], fill=int(cls * 1000 + 255 * sh))
    return np.asarray(img)


def body_fill(mask):
    """The body's outline with its narrow gaps closed: the biggest group of mask pixels lying close together (a lamp or
    an effect of the same colour further off stays out), with every gap narrower than about a limb filled -- where
    something in front of the body can hide a part. Wider gaps (between spread legs) stay floor."""
    lab, n = ndimage.label(ndimage.binary_dilation(mask, iterations=3))
    if not n:
        return mask.copy()
    sizes = ndimage.sum(mask, lab, range(1, n + 1))
    keep = mask & (lab == 1 + int(np.argmax(sizes)))
    r = max(3, mask.shape[0] // 16)
    pad = np.pad(keep, r)
    return ndimage.binary_closing(pad, iterations=r)[r:-r, r:-r] | mask


def straighten(border):
    """A border redrawn as the straight segments found in it (Hough), plus what no segment explains."""
    import cv2
    img = border.astype(np.uint8) * 255
    lines = cv2.HoughLinesP(img, 1, np.pi / 90, threshold=6, minLineLength=max(4, border.shape[0] // 24), maxLineGap=2)
    if lines is None:
        return border
    out = np.zeros_like(img)
    for x1, y1, x2, y2 in lines.reshape(-1, 4):
        cv2.line(out, (int(x1), int(y1)), (int(x2), int(y2)), 255, 1)
    covered = ndimage.binary_dilation(out > 0, iterations=1)
    return (out > 0) | (border & ~covered)


def in_range(ch, r):
    lo, hi = r
    return (ch >= lo) & (ch <= hi) if lo <= hi else (ch >= lo) | (ch <= hi)


_CROSS = np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]], np.uint8)


def _class_bits(pal):
    """Per channel (h, s, v), a 256-entry table of bit masks: bit cls set where that channel's value is in class cls's
    range. A pixel's class is the highest bit set in all three (classify paints classes in order, the last one wins)."""
    key = id(pal)
    if _class_bits.cache.get('key') == (key, json.dumps(pal['classes'], sort_keys=True)):
        return _class_bits.cache['bits']
    vals = np.arange(256)
    bits = [np.zeros(256, np.uint32) for _ in range(3)]
    for cls, c in enumerate(pal['classes'], 1):
        if cls == FACE:
            continue
        for ch, name in enumerate('hsv'):
            bits[ch] |= np.where(in_range(vals, c.get(name, [0, 255])), np.uint32(1 << cls), np.uint32(0))
    top = np.zeros(1 << (len(pal['classes']) + 1), np.uint8)  # highest set bit of every mask
    for m in range(1, len(top)):
        top[m] = m.bit_length() - 1
    _class_bits.cache = {'key': (key, json.dumps(pal['classes'], sort_keys=True)), 'bits': (bits, top)}
    return bits, top


_class_bits.cache = {}


def _cv_closing(mask, iterations):
    """scipy.ndimage.binary_closing(mask, iterations=n) (cross structure, border 0) with OpenCV: same result."""
    import cv2
    m = mask.astype(np.uint8)
    m = cv2.dilate(m, _CROSS, iterations=iterations, borderType=cv2.BORDER_CONSTANT, borderValue=0)
    m = cv2.erode(m, _CROSS, iterations=iterations, borderType=cv2.BORDER_CONSTANT, borderValue=0)
    return m.astype(bool)


def _cv_fill_holes(mask):
    """scipy.ndimage.binary_fill_holes(mask) (cross structure) with OpenCV: background areas (4-connected) not
    reaching the image's edge are filled."""
    import cv2
    n, lab = cv2.connectedComponents((~mask).astype(np.uint8), connectivity=4)
    outside = np.zeros(n, bool)
    outside[np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))] = True
    outside[0] = False  # label 0: the mask itself
    return ~outside[lab]


def _cv_propagate(core, mask):
    """scipy.ndimage.binary_propagation(core, mask=mask) (cross structure): the 4-connected areas of mask holding a
    core pixel."""
    import cv2
    n, lab = cv2.connectedComponents(mask.astype(np.uint8), connectivity=4)
    hit = np.zeros(n, bool)
    hit[np.unique(lab[core & mask])] = True
    hit[0] = False
    return hit[lab]


def _veil_join(core, hue, val):
    """The pale pixels of `hue` that a veil over the part `core` explains, per 4-connected patch of them touching it.
    Smoke over a part covers some of it and leaves the rest clear, so the veiled patch and the clear part meet along
    the smoke's edge, and the smoke, thin here and thick there, shades it unevenly. A pale thing of the part's hue
    that only touches it (a dummy's skin beside a kicking leg) meets it at a corner and is evenly lit, opaque: that
    is not the part seen through smoke. A patch is kept when it shares a long border with the part, or a fair one
    and the uneven shading of smoke."""
    import cv2
    n, lab, stats, _ = cv2.connectedComponentsWithStats(hue.astype(np.uint8), connectivity=4)
    if n <= 1:
        return np.zeros(hue.shape, bool)
    near = cv2.dilate(core.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
    border = np.bincount(lab[near & (lab > 0)].ravel(), minlength=n) / np.sqrt(np.maximum(stats[:, cv2.CC_STAT_AREA], 1))
    vf = val.astype(float).ravel()
    cnt = np.bincount(lab.ravel(), minlength=n).astype(float)
    mean = np.bincount(lab.ravel(), vf, minlength=n) / np.maximum(cnt, 1)
    std = np.sqrt(np.maximum(np.bincount(lab.ravel(), vf * vf, minlength=n) / np.maximum(cnt, 1) - mean ** 2, 0))
    keep = (border >= VEIL_BORDER_SURE) | ((border > 0) & (std >= VEIL_UNEVEN))
    keep[0] = False
    return keep[lab]


VEIL_BORDER_SURE = 3.0  # shared border with the clear part (per sqrt of the patch's area) that is surely a veil
VEIL_UNEVEN = 9.0       # brightness spread (std of V) of smoke's uneven shading; opaque things are flatter


def glow_of(rgb, h, s, v, out, pal):
    """Light effects in a part's colour (an energy trail: a streak of pure, over-bright colour with a white-hot core and
    a soft halo fading into whatever is behind it) -- not body. Returns the mask (or None).

    A streak's core is purer and brighter than any lit face of that colour. A face lit by the streak can look the same,
    but it is a patch inside the part's own solid colour, while the streak runs out of it (most of the streak's rim
    is halo or other things). From the streaks the glow spreads over the colour's hue through pixels paler than any
    body face of it (the halo: the colour faded into what is behind) or white-hot, as far as a halo reaches, plus the
    tint it casts on other colours just beside it."""
    import cv2
    g = pal['glow']
    H = out.shape[0]
    hue = np.zeros(out.shape, bool)
    for c in pal['classes']:
        if c['name'] in g.get('classes', ()):
            pad = g.get('hue_pad', 6)
            hue |= in_range(h, ((c['h'][0] - pad) % 256, (c['h'][1] + pad) % 256))
    core_v, solid_s = g.get('core_v', 222), g.get('solid_s', 150)
    core = hue & (s >= g.get('core_s', 240)) & (v >= core_v)
    if core.sum() < g.get('min_px', 20):
        return None
    reach = max(3, int(g.get('reach', 0.03) * H))
    ys, xs = np.nonzero(core)
    y0, y1 = max(0, ys.min() - reach - 3), min(H, ys.max() + reach + 4)
    x0, x1 = max(0, xs.min() - reach - 3), min(out.shape[1], xs.max() + reach + 4)
    sl = (slice(y0, y1), slice(x0, x1))
    core, hue, s, v, out = core[sl], hue[sl], s[sl], v[sl], out[sl]
    # The part's own solid colour (body saturation, body brightness).
    solid = hue & (s >= solid_s) & (v < core_v) & (out > 0)
    # Per core pixel: what surrounds it, apart from other core pixels -- the part's solid colour (a lit patch of a
    # face) or not (a streak, even where it runs out of a part).
    win = 2 * max(2, int(g.get('rim', 0.007) * H)) + 1
    other = cv2.boxFilter((~core).astype(np.float32), -1, (win, win), normalize=False)
    inside = cv2.boxFilter(solid.astype(np.float32), -1, (win, win), normalize=False) / np.maximum(other, 1)
    # (deep inside a wide patch of it, with nothing else near: a lit face, not a streak, which is thin)
    seed = core & (inside < g.get('inside', 0.5)) & (other >= g.get('thin', 0.15) * win * win)
    if not seed.any():
        return None
    near = cv2.dilate(seed.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * reach + 1,) * 2)) > 0
    halo = near & hue & (s >= g.get('halo_s', 22)) & (v >= g.get('halo_v', 130)) & ((s < solid_s) | (v >= g.get('white_v', 245)))
    grown = _cv_propagate(seed, seed | halo)
    # The tint it casts round itself (what is behind turns pinkish, uneven, washed out), and its own soft edges:
    # every pixel close by but a solid face (saturated and even over a few pixels).
    tw = max(2, int(g.get('tint', 0.012) * H))
    band = cv2.dilate(grown.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * tw + 1,) * 2)) > 0
    img = np.ascontiguousarray(rgb[sl])
    k5 = np.ones((5, 5), np.uint8)
    spread = (cv2.dilate(img, k5).astype(np.int16) - cv2.erode(img, k5)).max(-1)
    face = (s >= g.get('face_s', 140)) & (spread <= g.get('face_spread', 24)) & ~grown
    # (the colour itself at body saturation is the part or a face lit by the streak: kept)
    grown |= band & ~face & ~(hue & (s >= solid_s)) & ((out > 0) | (s >= g.get('halo_s', 22)))
    # The streak's own rims in its colour: thin slivers of it beside the glow (a part is thick).
    rest = (hue & (s >= solid_s) & ~grown).astype(np.uint8)
    thick = cv2.dilate(cv2.erode(rest, k5), k5) > 0
    grown |= band & (rest > 0) & ~thick
    m = np.zeros(h.shape, bool)
    m[sl] = grown
    return m


def classify(rgb, pal):
    """The class map of a frame (classify_ref's rules, the same result), worked out with lookup tables and OpenCV
    morphology instead of full-frame numpy/scipy passes (~10x faster at 1080p)."""
    import cv2
    hsv = np.asarray(Image.fromarray(rgb).convert('HSV'))
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    (bh, bs, bv), top = _class_bits(pal)
    out = top[bh[h] & bs[s] & bv[v]]
    if pal.get('adaptive'):
        hi_ = h.astype(int)
        for pair in pal.get('adaptive', []):
            a_cls, b_cls = (next(i for i, c in enumerate(pal['classes'], 1) if c['name'] == nm) for nm in pair)
            m = (out == a_cls) | (out == b_cls)
            hs = hi_[m].astype(float)
            if len(hs) > 50:
                c0, c1 = np.percentile(hs, 20), np.percentile(hs, 80)
                for _ in range(10):
                    cut = (c0 + c1) / 2
                    lo_, hi2 = hs[hs <= cut], hs[hs > cut]
                    if not len(lo_) or not len(hi2):
                        break
                    c0, c1 = lo_.mean(), hi2.mean()
                if c1 - c0 > 4:
                    lower = a_cls if np.mean(pal['classes'][a_cls - 1]['h']) < np.mean(pal['classes'][b_cls - 1]['h']) else b_cls
                    upper = b_cls if lower == a_cls else a_cls
                    out[m] = np.where(hi_[m] <= (c0 + c1) / 2, lower, upper)
    glow = glow_of(rgb, h.astype(int), s.astype(int), v.astype(int), out, pal) if pal.get('glow') else None
    if glow is not None:
        out[glow] = BG
    if pal.get('marks'):
        body = (out > 0) & (out != FACE)
        closed = _cv_fill_holes(_cv_closing(body, 2))
        out[closed & ~body & (out == BG)] = UNKNOWN
    if pal.get('veil'):
        vc = pal['veil']
        body = (out > 0) & (out != FACE) & (out != UNKNOWN)
        grown3 = cv2.dilate(body.astype(np.uint8), _CROSS, iterations=3)
        n, lab, stats, _ = cv2.connectedComponentsWithStats(grown3, connectivity=4)
        hull = np.zeros(out.shape, np.uint8)
        for c in range(1, n):
            if stats[c, cv2.CC_STAT_AREA] < 200:
                continue
            x, y, w, hh = stats[c, :4]
            ys, xs = np.nonzero(lab[y:y + hh, x:x + w] == c)
            pts = np.stack([xs + x, ys + y], 1).astype(np.int32)
            cv2.fillConvexPoly(hull, cv2.convexHull(pts), 1)
        reach = max(2, int(vc.get('reach', 0.02) * out.shape[0]))
        near = cv2.dilate(hull, _CROSS, iterations=reach).astype(bool)
        free = near & (out == BG) & (v >= vc.get('v_min', 150))
        tinted = free & (s >= vc.get('s_min', 22))
        if tinted.any():
            # Only the box around the pale pixels (+1) can join them to a part's colour (see classify_ref).
            ys, xs = np.nonzero(tinted)
            ya, yb = max(0, ys.min() - 1), min(out.shape[0], ys.max() + 2)
            xa, xb = max(0, xs.min() - 1), min(out.shape[1], xs.max() + 2)
            sub_out, sub_t, sub_h = out[ya:yb, xa:xb], tinted[ya:yb, xa:xb], h[ya:yb, xa:xb]
            seen = sub_out.copy()
            for cls, c in enumerate(pal['classes'], 1):
                if cls == FACE or 'h' not in c or c.get('s', [0])[0] > 255:
                    continue
                core = seen == cls
                if not core.any():
                    continue
                hue = sub_t & in_range(sub_h, c['h']) & (sub_out == BG)
                if glow is not None:
                    hue &= ~glow[ya:yb, xa:xb]
                if not hue.any():
                    continue
                sub_out[_veil_join(core, hue, v[ya:yb, xa:xb])] = cls
        out[free & hull.astype(bool) & (s < vc.get('s_min', 22))] = UNKNOWN
        if glow is not None:
            # Glow over the body may hide a part (unknown); glow beside it is floor.
            out[glow & hull.astype(bool) & (out == BG)] = UNKNOWN
    if FACE > 0:
        head = _cv_closing(out == HEAD, 3)
        out[(out == BG) & head & in_range(v, pal['classes'][FACE - 1]['v'])] = FACE
    return out.astype(np.int32) * 1000 + v


def classify_ref(rgb, pal):
    hsv = np.asarray(Image.fromarray(rgb).convert('HSV')).astype(int)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    out = np.zeros(h.shape, np.uint8)
    for cls, c in enumerate(pal['classes'], 1):
        if cls != FACE:
            out[in_range(h, c.get('h', [0, 255])) & in_range(s, c.get('s', [0, 255])) & in_range(v, c.get('v', [0, 255]))] = cls
    # Two colours close in hue (the noob's yellow arms and lime legs) are split where this frame's hues of the pair
    # actually divide (lighting shifts both), not at a fixed hue.
    for pair in pal.get('adaptive', []):
        a_cls, b_cls = (next(i for i, c in enumerate(pal['classes'], 1) if c['name'] == nm) for nm in pair)
        m = (out == a_cls) | (out == b_cls)
        hs = h[m].astype(float)
        if len(hs) > 50:
            c0, c1 = np.percentile(hs, 20), np.percentile(hs, 80)
            for _ in range(10):
                cut = (c0 + c1) / 2
                lo_, hi_ = hs[hs <= cut], hs[hs > cut]
                if not len(lo_) or not len(hi_):
                    break
                c0, c1 = lo_.mean(), hi_.mean()
            if c1 - c0 > 4:
                lower = a_cls if np.mean(pal['classes'][a_cls - 1]['h']) < np.mean(pal['classes'][b_cls - 1]['h']) else b_cls
                upper = b_cls if lower == a_cls else a_cls
                out[m] = np.where(h[m] <= (c0 + c1) / 2, lower, upper)
    glow = glow_of(rgb, h, s, v, out, pal) if pal.get('glow') else None
    if glow is not None:
        out[glow] = BG
    if pal.get('marks'):
        # Lettering and seams printed on clothing (white text, dark lines) inside the body: not floor, unknown.
        body = (out > 0) & (out != FACE)
        closed = ndimage.binary_fill_holes(ndimage.binary_closing(body, iterations=2))
        out[closed & ~body & (out == BG)] = UNKNOWN
    if pal.get('veil'):
        # Smoke, dust and flashes over the body: white mixed over its colours. Mixing in white leaves a colour's hue
        # as it was (it only turns paler and brighter), so near the body a pale, bright pixel still names its part by
        # hue alone; one too washed out to show any hue may hide a part: unknown, never floor.
        vc = pal['veil']
        import cv2
        body = (out > 0) & (out != FACE) & (out != UNKNOWN)
        # Only within each body patch's own outline (its convex hull, a little wider): smoke over a part lies inside
        # the body's shape; a pale wall or the sky beside it does not.
        lab, n = ndimage.label(ndimage.binary_dilation(body, iterations=3))
        hull = np.zeros(out.shape, np.uint8)
        for c in range(1, n + 1):
            ys, xs = np.nonzero(lab == c)
            if len(xs) < 200:
                continue
            pts = np.stack([xs, ys], 1).astype(np.int32)
            cv2.fillConvexPoly(hull, cv2.convexHull(pts), 1)
        near = ndimage.binary_dilation(hull.astype(bool), iterations=max(2, int(vc.get('reach', 0.02) * out.shape[0])))
        free = near & (out == BG) & (v >= vc.get('v_min', 150))
        tinted = free & (s >= vc.get('s_min', 22))
        seen = out.copy()
        for cls, c in enumerate(pal['classes'], 1):
            if cls == FACE or 'h' not in c or c.get('s', [0])[0] > 255:
                continue
            core = seen == cls
            if not core.any():
                continue
            # Only grown from the part's clearly seen colour (a pale wall of a similar hue nearby is not joined to it).
            hue = tinted & in_range(h, c['h']) & (out == BG)
            if glow is not None:
                hue &= ~glow
            out[_veil_join(core, hue, v)] = cls
        # Washed out past any hue: unknown, but only inside the body's outline itself (no halo round a clean body).
        out[free & hull.astype(bool) & (s < vc.get('s_min', 22))] = UNKNOWN
        if glow is not None:
            # Glow over the body may hide a part (unknown); glow beside it is floor.
            out[glow & hull.astype(bool) & (out == BG)] = UNKNOWN
    if FACE > 0:
        # The face: dark marks enclosed by the head's colour.
        head = ndimage.binary_closing(out == HEAD, iterations=3)
        out[(out == BG) & head & in_range(v, pal['classes'][FACE - 1]['v'])] = FACE
    # Each pixel: class * 1000 + brightness, the brightness carrying the faces' shading (how the boxes are turned).
    return out.astype(np.int32) * 1000 + v


def pick_character(cl, fg, near=None):
    """The character among patches of body colour (cl: class map, fg: body pixels): of the patches showing at least 3
    different body colours in solid amounts (each at least 3% of it; a wall with stray pixels shows one or two), the
    one nearest `near` (x, y: where it is expected), else the largest; with no such patch, the one showing the most.
    Returns its mask."""
    import cv2
    grown = cv2.dilate(fg.astype(np.uint8), _CROSS, iterations=4)  # = ndimage.binary_dilation(fg, iterations=4)
    lab, n = ndimage.label(grown)
    if not n:
        return fg
    # Per patch: its body pixels per class, and where its pixels lie (counted at once, not patch by patch).
    ys, xs = np.nonzero(fg)
    lf = lab[ys, xs]
    cf = cl[ys, xs].astype(np.int64)
    K = int(max(cf.max(initial=0), max(BODY_CLASSES, default=0))) + 1
    isb = np.isin(cf, BODY_CLASSES)
    per = np.bincount(lf[isb] * K + cf[isb], minlength=(n + 1) * K).reshape(n + 1, K)
    npx = np.bincount(lf, minlength=n + 1)
    sx = np.bincount(lf, weights=xs, minlength=n + 1)
    sy = np.bincount(lf, weights=ys, minlength=n + 1)
    found = []
    for c in range(1, n + 1):
        tot = int(per[c].sum())
        if not tot:
            continue
        k = per[c][per[c] > 0]
        found.append((int((k >= 0.03 * tot).sum()), tot, c, sx[c] / npx[c], sy[c] / npx[c]))
    if not found:
        return fg
    rich = [f for f in found if f[0] >= 3]
    if rich and near is not None:
        pick = min(rich, key=lambda f: (f[3] - near[0]) ** 2 + (f[4] - near[1]) ** 2)[2]
    elif rich:
        pick = max(rich, key=lambda f: f[1])[2]
    else:
        pick = max(found, key=lambda f: (f[0], f[1]))[2]
    return (lab == pick) & fg


def pick_character_ref(cl, fg, near=None):
    lab, n = ndimage.label(ndimage.binary_dilation(fg, iterations=4))
    if not n:
        return fg
    found = []
    for c in range(1, n + 1):
        m = (lab == c) & fg
        cls = cl[m]
        cls = cls[np.isin(cls, BODY_CLASSES)]
        if not len(cls):
            continue
        _, k = np.unique(cls, return_counts=True)
        ys, xs = np.nonzero(m)
        found.append((int((k >= 0.03 * len(cls)).sum()), len(cls), c, xs.mean(), ys.mean()))
    if not found:
        return fg
    rich = [f for f in found if f[0] >= 3]
    if rich and near is not None:
        pick = min(rich, key=lambda f: (f[3] - near[0]) ** 2 + (f[4] - near[1]) ** 2)[2]
    elif rich:
        pick = max(rich, key=lambda f: f[1])[2]
    else:
        pick = max(found, key=lambda f: (f[0], f[1]))[2]
    return (lab == pick) & fg


def frames_of(path, fps, every=1, width=None, only=None):
    """The frames as RGB arrays and the frame rate; every: keep every n-th frame (the rate divided to match); width:
    shrink frames wider than this (a long 1080p video would not fit in memory otherwise); only: (first, last) kept
    frame numbers, every other frame None (full size for one stretch of a long video)."""
    if path.lower().endswith('.gif'):
        im = Image.open(path)
        out = [np.array(f.convert('RGB')) for f in ImageSequence.Iterator(im)][::every]
        return out, (fps or 1000.0 / (im.info.get('duration') or 20)) / every
    import cv2
    cap, out, n = cv2.VideoCapture(path), [], 0
    while True:
        ok, f = cap.read()
        if not ok:
            break
        if n % every == 0 and only is not None and not (only[0] <= len(out) <= only[1]):
            out.append(None)
        elif n % every == 0:
            if width and f.shape[1] > width:
                f = cv2.resize(f, (width, round(f.shape[0] * width / f.shape[1] / 2) * 2), interpolation=cv2.INTER_AREA)
            out.append(f[..., ::-1].copy())
        n += 1
    return out, (fps or cap.get(cv2.CAP_PROP_FPS) or 30) / every


class Fitter:
    def __init__(self, res=96):
        self.res = res
        self.w = np.array([4, 4, 0, 0, 0, 0] + [0.15] * len(JOINTS))  # temporal stiffness: the camera moves slowly
        self.step0 = np.array([8, 6, 0.04, 3, 3, 0.003] + [16] * len(JOINTS), float)
        self.init_sigma = [20, 15, 0.4, 4, 4, 0.006] + [40 if ax != 1 or b in ('hips',) else 30 for b, ax in JOINTS]
        self.track_sigma = [3, 3, 0.15, 3, 3, 0] + [18] * len(JOINTS)
        self.light = DEFAULT_LIGHT
        self.shade_w = 2.0
        self.edge_w = 1.2
        self.fold_w = 0.6

    def roi(self, p, obs_full):
        """The window the fit looks through: around the body's colours in the frame when it has one (so a body bigger
        than the current guess is still all in view and pulls the guess up), else around the guess."""
        s, tx, ty = p[2], p[3], p[4]
        half = 30 * s  # the body is ~31 rig px tall around its middle
        cx, cy = tx, ty - 6 * s
        if obs_full is not None:
            H, W = obs_full.shape
            r = int(60 * s) + 20
            ya, yb, xa, xb = max(0, int(cy) - r), min(H, int(cy) + r), max(0, int(cx) - r), min(W, int(cx) + r)
            sub = obs_full[ya:yb, xa:xb]
            fg = sub >= 1000
            if any(len(set(v)) > 1 for v in SIDE_CLASS.values()):
                # Side-coloured clothing: only the character's patch (a wall of one body colour beside it would
                # stretch the window, and the character shrink to a few pixels in it).
                fg = pick_character(sub // 1000, fg, near=(cx - xa, cy - ya))
            ys, xs = np.nonzero(fg)
            if len(xs) > 30:
                lo_x, hi_x = np.percentile(xs, [1, 99]) + xa
                lo_y, hi_y = np.percentile(ys, [1, 99]) + ya
                cx, cy = (lo_x + hi_x) / 2, (lo_y + hi_y) / 2
                half = max(half * 0.7, 0.62 * max(hi_x - lo_x, hi_y - lo_y))
        return (cx - half, cy - half, self.res / (2 * half), self.res, self.res)

    def view(self, obs_full, roi):
        x0, y0, k, w, h = roi
        H, W = obs_full.shape
        ys = np.clip(((np.arange(h) + 0.5) / k + y0).astype(int), 0, H - 1)
        xs = np.clip(((np.arange(w) + 0.5) / k + x0).astype(int), 0, W - 1)
        o = obs_full[ys][:, xs].copy()
        oob = ((np.arange(h) + 0.5) / k + y0 < 0) | ((np.arange(h) + 0.5) / k + y0 >= H)
        o[oob] = BG
        o[:, ((np.arange(w) + 0.5) / k + x0 < 0) | ((np.arange(w) + 0.5) / k + x0 >= W)] = BG
        return o

    def prepare(self, enc):
        """What the cost compares a render with, from one window of the frame:
        - the class map, with UNKNOWN in the body's narrow gaps (where the dummy or an effect in front of it can hide a
          part): parts may lie there at no cost;
        - per part colour, each pixel's distance to that colour (pulls parts toward it) and its visible area;
        - per part colour, each pixel's distance to that colour's border, the border straightened into the line
          segments it is made of (box edges are straight), so outlines sit flush on it;
        - the folds: where the brightness steps inside one colour (two faces of a box meeting), and the distance to them;
        - the shading: each pixel's brightness relative to the brightest of its colour."""
        obs, val = enc // 1000, (enc % 1000).astype(np.float32)
        body = (obs > 0) & (obs != FACE) if FACE > 0 else obs > 0
        hull = body_fill(body)
        obs = obs.copy()
        obs[hull & (obs == BG)] = UNKNOWN
        dist = {c: np.minimum(ndimage.distance_transform_edt(obs != c), 16).astype(np.float32) if (obs == c).any() else None
                for c in BODY_CLASSES}
        area = {c: int((obs == c).sum()) for c in BODY_CLASSES}
        unknown = int((obs == UNKNOWN).sum())
        edist, fdist = {}, {}
        smooth = ndimage.gaussian_filter(val, 1.0)
        grad = np.hypot(ndimage.sobel(smooth, 0), ndimage.sobel(smooth, 1))
        for c in BODY_CLASSES:
            m = obs == c
            if m.sum() <= 8:
                continue
            border = m & ~ndimage.binary_erosion(m)
            # Only borders with the floor or another part are real edges; one with something in front is not.
            border &= ~ndimage.binary_dilation(obs == UNKNOWN, iterations=1)
            edist[c] = np.minimum(ndimage.distance_transform_edt(~straighten(border)), 12).astype(np.float32)
            inner = ndimage.binary_erosion(m, iterations=2)
            fold = inner & (grad > 4 * 18)
            if fold.sum() > 4:
                fdist[c] = np.minimum(ndimage.distance_transform_edt(~fold), 8).astype(np.float32)
        shade = np.zeros(obs.shape, np.float32)
        for c in BODY_CLASSES:
            m = obs == c
            if m.sum() > 8:
                shade[m] = np.minimum(1.2, val[m] / max(1.0, float(np.percentile(val[m], 95))))
        return obs, dist, area, shade, edist, fdist, unknown

    def prepare_lite(self, enc):
        """prepare's class map and shading only (what the per-frame tracker uses), the same values."""
        obs, val = enc // 1000, (enc % 1000).astype(np.float32)
        body = (obs > 0) & (obs != FACE) if FACE > 0 else obs > 0
        hull = body_fill(body)
        obs = obs.copy()
        obs[hull & (obs == BG)] = UNKNOWN
        shade = np.zeros(obs.shape, np.float32)
        for c in BODY_CLASSES:
            m = obs == c
            if m.sum() > 8:
                shade[m] = np.minimum(1.2, val[m] / max(1.0, float(np.percentile(val[m], 95))))
        return obs, shade

    def cost(self, p, frame, roi, prev):
        obs, dist, area, shade, edist, fdist, unknown = frame
        enc = render(p, roi, self.light)
        ren, rsh = enc // 1000, enc % 1000
        n = self.res * self.res
        c = float(COST[obs, ren].sum()) / n * 100
        hidden = obs == UNKNOWN
        for k in BODY_CLASSES:
            m = ren == k
            a = int(m.sum())
            # Rendered parts are pulled toward where their colour is; each colour's visible area should match (a part
            # may be bigger than what shows by as much as is hidden).
            if dist[k] is not None and a:
                c += float(dist[k][m].sum()) / n * 6
            c += (max(0, area[k] - a) + max(0, a - area[k] - unknown)) / n * 40
            if not a:
                continue
            inner = m.copy()
            inner[1:, :] &= m[:-1, :]
            inner[:-1, :] &= m[1:, :]
            inner[:, 1:] &= m[:, :-1]
            inner[:, :-1] &= m[:, 1:]
            # Each rendered colour's outline should sit flush on that colour's border in the footage (where it shows).
            edge = m & ~inner & ~hidden
            if k in edist and edge.any():
                c += float(edist[k][edge].mean()) * self.edge_w
            # The rendered folds (two faces of one box meeting) should lie on the footage's folds: the box's turn.
            if k in fdist:
                f = np.zeros_like(m)
                f[:, 1:] |= (rsh[:, 1:] != rsh[:, :-1]) & m[:, 1:] & m[:, :-1]
                f[1:, :] |= (rsh[1:, :] != rsh[:-1, :]) & m[1:, :] & m[:-1, :]
                f &= ~hidden
                if f.sum() > 2:
                    c += float(fdist[k][f].mean()) * self.fold_w
            # Where the colours agree, the faces' shading should too.
            both = m & (obs == k)
            if both.any():
                rs = rsh[both].astype(np.float32) / max(1.0, float(rsh[m].max()))
                c += float(np.abs(rs - shade[both]).sum()) / n * 100 * self.shade_w
        n0 = len(CAM)
        c += (p[n0] ** 2 + p[n0 + 2] ** 2) * 0.0005
        if prev is not None:
            d = p - prev
            d[:2] = (d[:2] + 180) % 360 - 180
            c += float((self.w * d * d).sum()) / 100
        return c

    def polish(self, p, obs_full, prev, res=192):
        """The last refinement at twice the window's resolution: edges land within a footage pixel."""
        keep = self.res, self.step0
        self.res, self.step0 = res, self.step0 / 4
        try:
            return self.descend(p, obs_full, prev, levels=2)
        finally:
            self.res, self.step0 = keep

    def clamp(self, p):
        for i, j in enumerate(JOINTS):
            lo, hi = LIMITS.get(j, (-180, 180))
            p[len(CAM) + i] = min(hi, max(lo, p[len(CAM) + i]))
        p[1] = min(85, max(-30, p[1]))
        p[5] = min(0.03, max(0.0, p[5]))
        return p

    def descend(self, p, obs_full, prev, levels=4):
        roi = self.roi(p, obs_full)
        obs = self.prepare(self.view(obs_full, roi))
        best = self.cost(p, obs, roi, prev)
        step = self.step0.copy()
        for _ in range(levels):
            improved = True
            while improved:
                improved = False
                for i in range(NP):
                    if step[i] == 0:
                        continue
                    for sgn in (1, -1):
                        q = p.copy()
                        q[i] = q[i] * (1 + sgn * step[i]) if i == 2 else q[i] + sgn * step[i]
                        self.clamp(q)
                        c = self.cost(q, obs, roi, prev)
                        if c < best - 1e-4:
                            p, best, improved = q, c, True
                            break
            step /= 2
        return p, best

    def cem(self, p, sigma, obs_full, prev, iters=25, pop=48, elite=8, seed=0):
        """Cross-entropy search around p: sample whole poses, keep the best few, narrow onto them."""
        roi = self.roi(p, obs_full)
        obs = self.prepare(self.view(obs_full, roi))
        rng = np.random.default_rng(seed)
        mean, sd = p.copy(), np.array(sigma, float)
        best, bc = p.copy(), self.cost(p, obs, roi, prev)
        for _ in range(iters):
            pts = mean + rng.normal(size=(pop, NP)) * sd
            pts[:, 2] = np.maximum(pts[:, 2], 0.2)
            scored = []
            for q in pts:
                self.clamp(q)
                scored.append((self.cost(q, obs, roi, prev), q))
            scored.sort(key=lambda e: e[0])
            if scored[0][0] < bc:
                bc, best = scored[0][0], scored[0][1].copy()
            el = np.array([q for _, q in scored[:elite]])
            mean = 0.7 * el.mean(0) + 0.3 * best
            sd = np.maximum(0.6 * sd + 0.4 * el.std(0), np.array(sigma) * 0.05)
        return best

    def reseed(self, p, obs_full, prev):
        """Tries each limb at a spread of directions and the left/right swaps, keeping what fits better."""
        roi = self.roi(p, obs_full)
        obs = self.prepare(self.view(obs_full, roi))
        best = self.cost(p, obs, roi, prev)
        n = len(CAM)
        # The body's lean first (it carries every limb), then each limb.
        for x in (-90, -45, 0, 45, 90):
            for z in (-90, -45, 0, 45, 90):
                q = p.copy()
                q[n], q[n + 2] = x, z
                c = self.cost(q, obs, roi, prev)
                if c < best:
                    p, best = q, c
        for limb in range(4):
            i = n + 5 + 2 * limb
            for x in (-160, -120, -80, -40, 0, 40):
                for z in (0, 45, 90):
                    q = p.copy()
                    q[i], q[i + 1] = x, z if limb % 2 == 0 else -z
                    self.clamp(q)
                    c = self.cost(q, obs, roi, prev)
                    if c < best:
                        p, best = q, c
        for r, l in (('rightArm', 'leftArm'), ('rightThigh', 'leftThigh')):
            q = p.copy()
            q[JI[(r, 0)]], q[JI[(l, 0)]] = p[JI[(l, 0)]], p[JI[(r, 0)]]
            for ax in (1, 2):
                q[JI[(r, ax)]], q[JI[(l, ax)]] = -p[JI[(l, ax)]], -p[JI[(r, ax)]]
            c = self.cost(q, obs, roi, prev)
            if c < best:
                p, best = q, c
        rng = np.random.default_rng(len(self.__dict__))
        for _ in range(150):
            q = p.copy()
            q[n:] += rng.normal(0, 25, len(JOINTS)) * (rng.random(len(JOINTS)) < 0.35)
            self.clamp(q)
            c = self.cost(q, obs, roi, prev)
            if c < best:
                p, best = q, c
        return p

    def init(self, obs_full, at, flip, quick=False):
        if at is None:
            fg = obs_full >= 1000
            lab, n = ndimage.label(ndimage.binary_dilation(fg, iterations=4))
            sizes = ndimage.sum(fg, lab, range(1, n + 1))
            at = ndimage.center_of_mass(lab == (1 + int(np.argmax(sizes))))[::-1]
        ys, xs = np.nonzero(obs_full >= 1000)
        near = (abs(xs - at[0]) < 80) & (abs(ys - at[1]) < 80)
        hgt = max(10, np.ptp(ys[near]) if near.any() else 40)
        cands = []
        for cyaw in range(0, 360, 30 if quick else 20):
            for cpitch in range(0, 90, 15 if quick else 10):
                p = np.zeros(NP)
                p[:6] = cyaw + (180 if flip else 0), cpitch, hgt / 31, at[0], at[1], 0.005
                roi = self.roi(p, obs_full)
                cands.append((self.cost(p, self.prepare(self.view(obs_full, roi)), roi, None), p))
        cands.sort(key=lambda e: e[0])
        best = None
        nc = len(CAM)
        cam_only = self.init_sigma[:nc] + [0] * len(JOINTS)
        joints_only = [0, 0, 0.05, 2, 2, 0] + self.init_sigma[nc:]
        # quick: the 2 best starts and about a third of the search (footage with one colour per part, where the
        # gradient tracking after it does the fine work).
        q = 3 if quick else 1
        for k, (_, p) in enumerate(cands[:2 if quick else 6]):
            # In stages: the camera with the body at rest, then the joints under that camera, then all of it gently.
            p = self.cem(p, cam_only, obs_full, None, iters=20 // q, pop=48 // (2 if quick else 1), seed=k)
            p = self.cem(p, joints_only, obs_full, None, iters=40 // q, pop=64 // (2 if quick else 1), seed=k + 100)
            p = self.cem(p, [s / 3 for s in self.init_sigma], obs_full, None, iters=20 // q, pop=48 // (2 if quick else 1), seed=k + 200)
            p, c = self.descend(p, obs_full, None, levels=2 if quick else 4)
            if best is None or c < best[1]:
                best = (p, c)
        # The light, from the shading of the pose found; then the pose again under it.
        self.fit_light(best[0], obs_full)
        p = self.cem(best[0], [s / 4 for s in self.init_sigma], obs_full, None, iters=20 // q, pop=48 // (2 if quick else 1), seed=300)
        return self.descend(p, obs_full, None, levels=2 if quick else 4)

    def fit_light(self, p, obs_full):
        roi = self.roi(p, obs_full)
        frame = self.prepare(self.view(obs_full, roi))
        best = (self.cost(p, frame, roi, None), self.light)
        for az in range(0, 360, 30):
            for el in (10, 35, 60, 85):
                for amb in (0.3, 0.45, 0.6, 0.75):
                    a, e = math.radians(az), math.radians(el)
                    self.light = (np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)]), amb)
                    c = self.cost(p, frame, roi, None)
                    if c < best[0]:
                        best = (c, self.light)
        self.light = best[1]


def track(fit, frames, obs, first, hi, at, flip, start=None, final=False):
    """Fits frames first..hi in order, each from the last; a camera cut starts over (from start[frame] when given)."""
    params, costs, prev, shots = {}, {}, None, []
    for i in range(first, hi + 1):
        # A camera cut (most of the picture changes at once) starts the fit over.
        if i > first and np.abs(frames[i].astype(int) - frames[i - 1]).mean() > 28:
            prev, at = None, None
        if prev is None:
            shots.append(i)
            if start and i in start:
                p = fit.cem(start[i], fit.track_sigma, obs[i], None, seed=i)
                p, c = fit.descend(p, obs[i], None)
                if final:
                    p, c = fit.polish(p, obs[i], None)
            else:
                p, c = fit.init(obs[i], at, flip)
        else:
            p = fit.reseed(prev.copy(), obs[i], prev)
            p = fit.cem(p, fit.track_sigma, obs[i], prev, seed=i)
            p, c = fit.descend(p, obs[i], prev)
            if final:
                p, c = fit.polish(p, obs[i], prev)
        params[i], costs[i], prev = p, c, p
        print(f'frame {i}: mismatch {c:.1f}', flush=True)
    return params, costs, shots


def refine_camera(fit, obs, params, span):
    """The camera (yaw, pitch) shift under which a spread of the shot's frames, each re-posed, fits best in total."""
    sample = span[::max(1, len(span) // 8)][:8]
    sig = list(fit.track_sigma)
    sig[0] = sig[1] = 0

    def score(dy, dp, pe=None):
        total = 0.0
        for i in sample:
            q = params[i].copy()
            q[0] += dy
            q[1] += dp
            if pe is not None:
                q[5] = pe
            q = fit.cem(q, sig, obs[i], None, iters=12, seed=i)
            roi = fit.roi(q, None)
            total += fit.cost(q, fit.prepare(fit.view(obs[i], roi)), roi, None)
        return total

    if len(sample) < 4:
        return 0, 0
    best, bs = (0, 0), score(0, 0)
    for dp in (-20, -15, -10, -5, 5, 10, 15, 20):
        sc = score(0, dp)
        if sc < bs:
            best, bs = (0, dp), sc
    for dy in (-30, -20, -10, 10, 20, 30):
        sc = score(dy, best[1])
        if sc < bs:
            best, bs = (dy, best[1]), sc
    pe = None
    for cand in (0.0, 0.004, 0.008, 0.012, 0.018, 0.025):
        sc = score(best[0], best[1], cand)
        if sc < bs:
            pe, bs = cand, sc
    return best[0], best[1], pe


def unwrap(a):
    return np.degrees(np.unwrap(np.radians(a)))


def reduce_keys(times, curves, tol):
    """Ramer-Douglas-Peucker over all angle curves at once: the frames to keep."""
    keep = {0, len(times) - 1}
    def rdp(i, j):
        if j <= i + 1:
            return
        f = (times[i + 1:j] - times[i]) / (times[j] - times[i])
        lin = curves[i] + (curves[j] - curves[i]) * f[:, None]
        err = np.abs(curves[i + 1:j] - lin).max(1)
        k = int(np.argmax(err))
        if err[k] > tol:
            keep.add(i + 1 + k)
            rdp(i, i + 1 + k)
            rdp(i + 1 + k, j)
    rdp(0, len(times) - 1)
    return sorted(keep)


def sheet(frames, params, costs, idx, roi_of, path):
    tiles = []
    font = ImageFont.load_default()
    for i in idx:
        p = params[i]
        x0, y0, k, w, h = roi_of(p)
        big = 160
        sc = big / w
        crop = Image.fromarray(frames[i]).crop((int(x0), int(y0), int(x0 + w / k), int(y0 + h / k))).resize((big, big))
        d = ImageDraw.Draw(crop)
        if p is not None:
            for poly, cls, _ in faces(p, DEFAULT_LIGHT):
                d.polygon([((u - x0) * k * sc, (v - y0) * k * sc) for u, v in poly], outline=SHOW[cls])
        d.text((3, 2), f'{i}  {costs[i]:.1f}', fill=(255, 255, 255), font=font)
        tiles.append(crop)
    cols = 8
    out = Image.new('RGB', (160 * cols, 160 * ((len(tiles) + cols - 1) // cols)))
    for n, t in enumerate(tiles):
        out.paste(t, (160 * (n % cols), 160 * (n // cols)))
    out.save(path)


MOD_PX_PER_RIG_PX = 8.0 / 12.0  # the mod's player is 8 px across the torso where the R6 fit rig is 12 (2 studs)
POS_TOL_W = 10.0  # key reduction: 1 rig px of joint offset counts as 10 degrees


def continuous_angles(P):
    """The joint-angle curves (frames x JOINTS) with each bone's rotation, frame by frame, written in whichever of its
    two equivalent (x, y, z) forms, each wrapped by 360, lies nearest the frame before. The game and the viewer blend
    keys linearly in these angles, so a bone written in the other form (or 360 off) between two keys would spin the long
    way round between them; written this way it takes the short way, and the pose at every key is unchanged."""
    from scipy.spatial.transform import Rotation as Rot
    curves = np.array(P[:, len(CAM):len(CAM) + len(JOINTS)], float)
    for b in REST:
        cols = [JOINTS.index((b, ax)) if (b, ax) in JOINTS else None for ax in range(3)]
        if all(c is None for c in cols):
            continue
        E = np.stack([curves[:, c] if c is not None else np.zeros(len(curves)) for c in cols], 1)
        for i in range(1, len(E)):
            near = E[i - 1]
            z, y, x = Rot.from_euler('ZYX', [E[i, 2], E[i, 1], E[i, 0]], degrees=True).as_euler('ZYX', degrees=True)
            forms = [np.array([x, y, z]), np.array([x + 180, 180 - y, z + 180])]
            forms = [near + (f - near + 180) % 360 - 180 for f in forms]
            if any(c is None for c in cols):  # a bone fitted on fewer axes keeps its own form, wrapped only
                forms = [near + (E[i] - near + 180) % 360 - 180]
            E[i] = min(forms, key=lambda f: float(np.abs(f - near).sum()))
        for ax, c in enumerate(cols):
            if c is not None:
                curves[:, c] = E[:, ax]
    return curves


SMOOTH_FRAMES = float(os.environ.get('R6_CLIP_SMOOTH', '0'))  # gaussian sigma in frames on the written curves (0: none)


def write_clip(target, times_ms, P, tol, merge, src, path=None):
    import warnings
    warnings.filterwarnings('ignore', message='Gimbal lock')
    curves = continuous_angles(P)
    curves[:, 1] -= curves[0, 1]  # the body turns from facing forward at the move's first frame
    if SMOOTH_FRAMES > 0:
        curves = ndimage.gaussian_filter1d(curves, SMOOTH_FRAMES, axis=0, mode='nearest')
    # Joint offsets (params past NP, OFF_BONES): the mod's 'pos' is (right, up, forward) in its own px, and its model
    # space has right at -x, up at -y, forward at -z -- the fit rig's axes -- so pos = -offset, rescaled.
    offs = None
    if P.shape[1] > NP:
        offs = np.zeros((len(P), NOFF))
        offs[:, :P.shape[1] - NP] = P[:, NP:NP + NOFF]
        if SMOOTH_FRAMES > 0:
            offs = ndimage.gaussian_filter1d(offs, SMOOTH_FRAMES, axis=0, mode='nearest')
    keep = reduce_keys(times_ms, curves if offs is None else np.hstack([curves, POS_TOL_W * offs]), tol)
    base = os.path.join(ANIMS, target + '.json')
    path = path or base
    clip = json.load(open(base)) if merge and os.path.exists(base) else {}
    fitted = {b for b, _ in JOINTS}
    keys = []
    for k in keep:
        bones = {}
        for (b, ax), v in zip(JOINTS, curves[k]):
            bones.setdefault(b, {'rot': [0, 0, 0]})['rot'][ax] = round(float(v), 1)
        if offs is not None:
            for i, b in enumerate(OFF_BONES):
                o = offs[k, 3 * i:3 * i + 3]
                if np.abs(offs[:, 3 * i:3 * i + 3]).max() > 0.05:
                    bones[b]['pos'] = [round(float(-x * MOD_PX_PER_RIG_PX), 2) for x in o]
        keys.append({'t': round(float(times_ms[k] - times_ms[0])), 'ease': 'LINEAR', 'bones': bones})
    if merge and clip.get('keys'):
        # The old clip's other bones keep their own keys, rescaled onto the new length.
        span = clip.get('duration', 500)
        dur = keys[-1]['t'] or 1
        for ok in clip['keys']:
            other = {b: v for b, v in (ok.get('bones') or {}).items() if b not in fitted}
            if other:
                t = round(ok.get('t', 0) / span * dur)
                keys.append({'t': t, 'ease': ok.get('ease', 'EASE_IN_OUT'), 'bones': other})
        keys.sort(key=lambda k: k['t'])
    clip.update({'name': os.path.basename(target), 'duration': keys[-1]['t'], 'keys': keys,
                 'fitted': f'tools/r6_fit.py from {src}'})
    clip.setdefault('blendIn', 60)
    clip.setdefault('blendOut', 150)
    clip.setdefault('priority', 'ATTACK')
    clip.pop('authored', None)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(clip, open(path, 'w'), indent=1)
    return path, len(keep)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('video')
    ap.add_argument('--out', default=None)
    ap.add_argument('--range', default=None)
    ap.add_argument('--clip', default=None)
    ap.add_argument('--merge', action='store_true')
    ap.add_argument('--speed', type=float, default=1.0)
    ap.add_argument('--tol', type=float, default=4.0)
    ap.add_argument('--at', default=None)
    ap.add_argument('--flip', action='store_true')
    ap.add_argument('--palette', default=None)
    ap.add_argument('--fps', type=float, default=None)
    ap.add_argument('--compare', action='store_true')
    ap.add_argument('--warmup', type=int, default=None)
    ap.add_argument('--preview', default=None)
    a = ap.parse_args()
    pal = json.load(open(a.palette)) if a.palette else PALETTE
    use_palette(pal)
    frames, fps = frames_of(a.video, a.fps)
    lo, hi = (int(x) for x in a.range.split(':')) if a.range else (0, len(frames) - 1)
    first = min(lo, a.warmup) if a.warmup is not None else lo
    out = a.out or os.path.join(ROOT, 'build', 'fit', os.path.splitext(os.path.basename(a.video))[0])
    os.makedirs(out, exist_ok=True)
    obs = {i: classify(frames[i], pal) for i in range(first, hi + 1)}
    fit = Fitter()
    at = tuple(float(x) for x in a.at.split(',')) if a.at else None
    params, costs, shots = track(fit, frames, obs, first, hi, at, a.flip)
    # The camera of each shot, from all of it: shift it to where the shot's frames fit best together, then track again.
    start = {}
    for k, s0 in enumerate(shots):
        span = [i for i in range(s0, (shots[k + 1] if k + 1 < len(shots) else hi + 1))]
        dy, dp, pe = refine_camera(fit, obs, params, span)
        print(f'shot from frame {s0}: camera yaw {dy:+.0f}, pitch {dp:+.0f}, perspective {pe}', flush=True)
        q = params[s0].copy()
        q[0] += dy
        q[1] += dp
        if pe is not None:
            q[5] = pe
        start[s0] = q
    params, costs, _ = track(fit, frames, obs, first, hi, at, a.flip, start, final=True)
    idx = list(range(lo, hi + 1))
    json.dump({'fps': fps, 'frames': {i: [round(float(x), 2) for x in params[i]] for i in idx},
               'cost': {i: round(float(costs[i]), 2) for i in idx}, 'params': CAM + [f'{b}.{"xyz"[ax]}' for b, ax in JOINTS]},
              open(os.path.join(out, 'params.json'), 'w'))
    step = max(1, len(idx) // 48)
    sheet(frames, params, costs, idx[::step], lambda p: fit.roi(p, None), os.path.join(out, 'sheet.png'))
    print(f'{out}/sheet.png, {out}/params.json')
    if a.preview and not a.clip:
        a.clip = 'preview/' + a.preview
    if a.clip:
        P = np.stack([params[i] for i in idx])
        times = np.array([(i - lo) * 1000.0 / fps / a.speed for i in idx])
        # A preview keeps its clip beside the conversion (build/fit), out of the game's assets.
        keep = os.path.join(out, 'clip.json') if a.clip.startswith('preview/') else None
        path, n = write_clip(a.clip, times, P, a.tol, a.merge, os.path.basename(a.video) + f' frames {lo}-{hi}', keep)
        print(f'{path}: {n} keys, {int(times[-1])} ms')
        write_conversion(frames, params, idx, times, a.clip, fit, out, os.path.basename(a.video) + f' {lo}-{hi}')
        if a.compare:
            compare(frames, params, idx, times, a.clip, fit, out)


def viewer_cam(params, i, hy0):
    """The fit's camera as the viewer's orbit (yaw, pitch) in radians: yaw + 180 degrees, turned with the clip's start."""
    return [round(math.radians(params[i][0] - hy0) + math.pi, 4), round(math.radians(params[i][1]), 4)]


def write_conversion(frames, params, idx, times, clip, fit, out, source, size=240):
    """<out>/conversion.json for the viewer's Conversions tab: each frame's footage (a crop following the character),
    the fitted R6 boxes over it and the camera, beside the clip written from them."""
    import base64
    import io
    hy0 = params[idx[0]][len(CAM) + 1]
    pics, polys, cams = [], [], []
    for i in idx:
        p = params[i]
        x0, y0, k, w, h = fit.roi(p, None)
        crop = Image.fromarray(frames[i]).crop((int(x0), int(y0), int(x0 + w / k), int(y0 + h / k))).resize((size, size))
        buf = io.BytesIO()
        crop.save(buf, 'JPEG', quality=72)
        pics.append('data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode())
        sc = size / (w / k)
        # Every visible face's edges: the boxes' turn shows, not only their outline.
        polys.append([['#%02x%02x%02x' % SHOW[cls], [[round((u - int(x0)) * sc, 1), round((v - int(y0)) * sc, 1)] for u, v in poly]]
                      for poly, cls, _ in faces(p, fit.light)])
        cams.append(viewer_cam(params, i, hy0))
    frame_ms = (times[1] - times[0]) if len(times) > 1 else 20
    json.dump({'name': os.path.basename(clip), 'clip': clip, 'source': source, 'frameMs': round(float(frame_ms), 3), 'size': size,
               'frames': pics, 'boxes': polys, 'cams': cams}, open(os.path.join(out, 'conversion.json'), 'w'), separators=(',', ':'))
    print(f'{out}/conversion.json (rebuild the viewer: python3 tools/build_anim_viewer.py)')


def compare(frames, params, idx, times, clip, fit, out):
    import subprocess
    subprocess.run(['python3', os.path.join(ROOT, 'tools', 'build_anim_viewer.py')], check=True, stdout=subprocess.DEVNULL)
    pick = idx[::max(1, len(idx) // 12)]
    # The fit's camera (world turned by yaw, then pitch) is the viewer's orbit camera at yaw + 180 degrees; the clip starts
    # the body facing forward, so the camera turns with it by the first frame's body yaw (mirrored by the model flip).
    hy0 = params[idx[0]][len(CAM) + 1]
    shots = ['%.0f:%s:%s' % (times[idx.index(i)], *viewer_cam(params, i, hy0)) for i in pick]
    snaps = os.path.join(out, 'snaps')
    npm = subprocess.run(['npm', 'root', '-g'], capture_output=True, text=True).stdout.strip()
    subprocess.run(['node', os.path.join(ROOT, 'tools', 'anim_viewer', 'snap.js'), os.path.join(ROOT, 'build', 'anim_viewer.html'),
                    snaps, clip, *shots], check=True, env={**os.environ, 'NODE_PATH': npm})
    w = 180
    sheet_img = Image.new('RGB', (w * len(pick), 2 * w), (0, 0, 0))
    for k, i in enumerate(pick):
        x0, y0, kk, rw, rh = fit.roi(params[i], None)
        crop = Image.fromarray(frames[i]).crop((int(x0), int(y0), int(x0 + rw / kk), int(y0 + rh / kk))).resize((w, w))
        snap = Image.open(os.path.join(snaps, f'{k:03d}.png')).convert('RGB')
        side = min(snap.size)
        snap = snap.crop(((snap.width - side) // 2, (snap.height - side) // 2, (snap.width + side) // 2, (snap.height + side) // 2))
        sheet_img.paste(crop, (w * k, 0))
        sheet_img.paste(snap.resize((w, w)), (w * k, w))
        ImageDraw.Draw(sheet_img).text((w * k + 3, 2), f'{i}  {times[idx.index(i)]:.0f} ms', fill=(255, 255, 255))
    sheet_img.save(os.path.join(out, 'compare.png'))
    print(f'{out}/compare.png')


if __name__ == '__main__':
    main()
