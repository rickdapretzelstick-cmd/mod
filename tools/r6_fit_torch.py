#!/usr/bin/env python3
"""
Gradient fitter for R6 footage (PyTorch), from one or two takes of the same move: the precise successor of the search
in tools/r6_fit.py, which it reuses for colours, the first frame's camera and the clip/conversion output.

Every box face of the fitted rig is rasterised softly (each pixel's coverage falls off smoothly across the face's
edges), composited front to back, and compared with the footage's colour parts pixel by pixel (cross-entropy) and with
its shading; the softness makes the comparison differentiable, so every joint, camera and light value moves along
its exact gradient toward boxes that sit flush on the colour borders. The whole move is optimised together (with a
smoothness term), after a frame-by-frame warm start that also tries a spread of limb directions.

A second take (--view2) of the same move from another side gets its own camera; both takes share one pose timeline
(view 2's frames are placed on it proportionally, --range2), so what one take cannot see the other pins down.

    python3 tools/r6_fit_torch.py <video> --range a:b --clip yuji/manji_kick [--merge] [--view2 <video> --range2 c:d]
    python3 tools/r6_fit_torch.py <video> --range a:b --preview name          # Conversions tab only
    python3 tools/r6_fit_torch.py --selftest                                  # synthetic one- vs two-view accuracy
    python3 tools/r6_fit_torch.py <video> --range a:b --keyframes --preview n # clear frames only, the rest eased

Options shared with tools/r6_fit.py: --warmup, --palette, --fps, --speed, --tol, --flip, --at, --out.
"""
import argparse
import json
import math
import os
import sys
import time

import numpy as np
import torch
import cv2
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F  # noqa: E402
import r6_rects as RR  # noqa: E402

torch.set_num_threads(max(1, os.cpu_count() or 1))
DT = torch.float32
NC, NJ = len(F.CAM), len(F.JOINTS)
BONES = list(F.REST)
PARENT_IDX = [BONES.index(F.PARENT[b]) if F.PARENT[b] else -1 for b in BONES]
OFFSET = torch.tensor(np.array([np.array(F.REST[b], float) - (np.array(F.REST[F.PARENT[b]], float) if F.PARENT[b] else 0) for b in BONES]), dtype=DT)
JMAP = torch.zeros(NJ, len(BONES) * 3, dtype=DT)  # joint vector -> per-bone (x, y, z) degrees
for _i, (_b, _ax) in enumerate(F.JOINTS):
    JMAP[_i, BONES.index(_b) * 3 + _ax] = 1
LO = torch.tensor([F.LIMITS.get(j, (-180, 180))[0] for j in F.JOINTS], dtype=DT)
HI = torch.tensor([F.LIMITS.get(j, (-180, 180))[1] for j in F.JOINTS], dtype=DT)
PART_BONE = [BONES.index(b) for b, _, _, _ in F.PARTS]
PART_OF_FACE = torch.arange(len(F.PARTS)).repeat_interleave(6)  # which box each face belongs to
CORNERS = torch.tensor([[[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
                        for _, lo, hi, _ in F.PARTS], dtype=DT)  # (parts, 8, 3)
# The avatar's proportions per box (F.PROPORTION, about each joint); SCALE_OVERRIDE (parts, 3) replaces them while
# they are being measured (tools/calibrate_body.py).
SCALE_OVERRIDE = None


def box_scale():
    if SCALE_OVERRIDE is not None:
        return SCALE_OVERRIDE
    return torch.tensor([F.PROPORTION.get(F.GROUP_OF.get(part, part), (1.0, 1.0, 1.0)) for _, _, _, part in F.PARTS],
                        dtype=DT)


FACE_IDX = torch.tensor([f[0] for f in F.FACES])  # (6, 4)
FACE_N = torch.tensor(np.stack([f[1] for f in F.FACES]), dtype=DT)  # (6, 3)
M2W = torch.tensor([-1.0, -1.0, 1.0], dtype=DT)
# Cameras are optimised in these units (degrees, 1/100 of the scale, pixels) so one step size suits every entry.
CAM_UNIT = torch.tensor([1.0, 1.0, 0.01, 1.0, 1.0], dtype=DT)
RECT_W = 1.5  # weight of each box's own-rectangle overlap


def rotm(a):
    """(..., 3) degrees -> (..., 3, 3) = Rz Ry Rx, as client.anim.rig.Rig (the product written out: one stack, no
    matrix products)."""
    r = torch.deg2rad(a)
    c, s = r.cos(), r.sin()
    cx, cy, cz = c.unbind(-1)
    sx, sy, sz = s.unbind(-1)
    return torch.stack([cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                        sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                        -sy, cy * sx, cy * cx], -1).reshape(a.shape[:-1] + (3, 3))


HEAD_TILT_W, HEAD_TURN_W, HEAD_TURN_FACE_W, BODY_LEAN_W = 0.1, 0.1, 0.002, 0.02


def set_head_lock(fit, k):
    """Frame k's head-turn weight from what the footage shows: the face (its dark marks) in view lets the head turn,
    no face holds it square."""
    view = fit.views[0]
    obs = view.windows[view.idx[k]][1]
    face = int((obs == F.FACE).sum()) if F.FACE > 0 else 0
    if fit.head_lock is None:
        fit.head_lock = torch.full((fit.J.shape[0],), HEAD_TURN_W, dtype=DT)
    fit.head_lock[k] = HEAD_TURN_FACE_W if face >= 4 else HEAD_TURN_W


def part_classes():
    return torch.tensor([F.CLASS_OF[part] for _, _, _, part in F.PARTS])


def face_classes():
    """Every box side's class, parts in F.PARTS order, sides in F.FACES order (one colour per side when the palette
    paints sides, else the part's colour six times)."""
    return torch.tensor([c for _, _, _, part in F.PARTS for c in F.SIDE_CLASS[part]])


def sides_mode():
    """Clothing painted per side: parts no longer have one colour each (the per-part steps do not apply)."""
    return any(len(set(v)) > 1 for v in F.SIDE_CLASS.values())


def geometry(J, cam, persp, light, corners=False):
    """Faces of a batch of poses: 2D quads (B, faces, 4, 2) in footage px, depth (B, faces), visible (B, faces),
    shade (B, faces). J (B, joints) degrees, cam (B, 5) = yaw, pitch, scale, x, y."""
    B = J.shape[0]
    ang = (J @ JMAP).reshape(B, len(BONES), 3)
    R = rotm(ang)
    wr, wt = [None] * len(BONES), [None] * len(BONES)
    for b, par in enumerate(PARENT_IDX):
        if par < 0:
            wr[b], wt[b] = R[:, b], OFFSET[b].expand(B, 3)
        else:
            wr[b] = wr[par] @ R[:, b]
            wt[b] = wt[par] + (wr[par] @ OFFSET[b].unsqueeze(-1)).squeeze(-1)
    WR = torch.stack([wr[i] for i in PART_BONE], 1)  # (B, P, 3, 3)
    WT = torch.stack([wt[i] for i in PART_BONE], 1)  # (B, P, 3)
    box = CORNERS * box_scale()[:, None, :]
    X = (WR.unsqueeze(2) @ box.unsqueeze(0).unsqueeze(-1)).squeeze(-1) + WT.unsqueeze(2)  # (B, P, 8, 3) model
    X = X * M2W
    zeros = torch.zeros_like(cam[:, 0])
    view = rotm(torch.stack([cam[:, 1], zeros, zeros], -1)) @ rotm(torch.stack([zeros, cam[:, 0], zeros], -1))  # (B, 3, 3)
    V = (view[:, None, None] @ X.unsqueeze(-1)).squeeze(-1)  # (B, P, 8, 3)
    f = cam[:, 2, None, None] / torch.clamp(1 - V[..., 2] * persp, min=0.2)
    uv = torch.stack([cam[:, 3, None, None] + f * V[..., 0], cam[:, 4, None, None] - f * V[..., 1]], -1)  # (B, P, 8, 2)
    if corners == 'depth':
        return uv, V[..., 2].mean(-1)  # corners (B, boxes, 8, 2) footage px; each box's mean depth (B, boxes)
    if corners:
        return uv  # (B, boxes, 8, 2) footage px
    quads = uv[:, :, FACE_IDX]  # (B, P, 6, 4, 2)
    centre = V[:, :, FACE_IDX].mean(3)  # (B, P, 6, 3)
    nw = (WR.unsqueeze(2) @ FACE_N.view(1, 1, 6, 3, 1)).squeeze(-1) * M2W  # (B, P, 6, 3) world
    nc = (view[:, None, None] @ nw.unsqueeze(-1)).squeeze(-1)
    if persp > 1e-6:
        toward = torch.tensor([0, 0, 1 / persp], dtype=DT) - centre
    else:
        toward = torch.tensor([0, 0, 1.0], dtype=DT).expand_as(centre)
    vis = ((nc * toward).sum(-1) > 0).float()
    ldir, amb = light
    shade = amb + (1 - amb) * torch.relu((nw * ldir).sum(-1))
    depth = centre[..., 2]
    return quads.reshape(B, -1, 4, 2), depth.reshape(B, -1), vis.reshape(B, -1), shade.reshape(B, -1)


def rasterise(quads, depth, vis, shade, pix, sigma, classes, ncls, parts=False):
    """Soft render: per pixel the probability of each class (0 = floor) and the shade. pix (B, N, 2) footage px."""
    # Faces turned away in every sample (vis 0) add nothing (their alpha is exactly 0, and so is its gradient): left out,
    # about half the work.
    face_part = PART_OF_FACE
    keep = vis.detach().amax(0) > 0
    if not bool(keep.all()):
        sel = keep.nonzero()[:, 0]
        quads, depth, vis, shade = quads[:, sel], depth[:, sel], vis[:, sel], shade[:, sel]
        classes, face_part = classes[sel], face_part[sel]
    # Faces sorted nearest first once (not every pixel's list of faces gathered).
    order = torch.argsort(depth.detach(), dim=1, descending=True)  # (B, Fc)
    quads = torch.gather(quads, 1, order[..., None, None].expand_as(quads))
    vis = torch.gather(vis, 1, order)
    shade_o = torch.gather(shade, 1, order)
    cls = torch.gather(classes.expand(order.shape[0], -1), 1, order)  # (B, Fc)
    a = quads
    b = torch.roll(quads, -1, 2)
    e = b - a  # (B, Fc, 4, 2)
    elen = torch.clamp(e.norm(dim=-1), min=1e-3)
    area = (a[..., 0] * b[..., 1] - b[..., 0] * a[..., 1]).sum(-1)  # twice the signed area (B, Fc)
    orient = torch.where(area >= 0, 1.0, -1.0).detach()
    # Signed distance of pixel x to edge (a, e): (e_x (x_y - a_y) - e_y (x_x - a_x)) / |e|, linear in x: one matmul
    # of the pixels with each edge's normal plus its offset, instead of (B, N, Fc, 4, 2) differences.
    s_ = (orient[..., None] / elen)  # (B, Fc, 4)
    nrm = torch.stack([-e[..., 1] * s_, e[..., 0] * s_], -1).reshape(e.shape[0], -1, 2)  # (B, Fc*4, 2)
    off = ((e[..., 1] * a[..., 0] - e[..., 0] * a[..., 1]) * s_).reshape(e.shape[0], -1)  # (B, Fc*4)
    cross = (pix @ nrm.transpose(1, 2) + off[:, None]).reshape(pix.shape[0], pix.shape[1], -1, 4)
    inside = cross.min(-1).values  # (B, N, Fc) signed distance to the face's outline
    # A face seen nearly edge-on is a sliver whose short edges no longer bound it (it would smear into a line): fade
    # faces out below ~10 px of area.
    solid = torch.clamp(area.abs() / 20.0, 0, 1)
    alpha = torch.sigmoid(inside / sigma) * (vis * solid)[:, None]
    trans = torch.cumprod(torch.cat([torch.ones_like(alpha[..., :1]), 1 - alpha[..., :-1]], -1), -1)
    w = alpha * trans  # each face's share of the pixel
    floor = trans[..., -1] * (1 - alpha[..., -1])
    onehot = torch.nn.functional.one_hot(cls, ncls).to(DT)  # (B, Fc, C)
    prob = w @ onehot  # (B, N, C)
    prob = torch.cat([prob[..., :1] + floor.unsqueeze(-1), prob[..., 1:]], -1)
    sh = (w * shade_o[:, None]).sum(-1) / (w.sum(-1) + 1e-6)
    if parts:
        pid = torch.gather(face_part.expand(order.shape[0], -1), 1, order)
        pprob = w @ torch.nn.functional.one_hot(pid, len(F.PARTS)).to(DT)  # (B, N, boxes): each box's share
        return prob, sh, pprob
    return prob, sh


class View:
    """One take: its frames' colour maps in fixed windows, and its camera per frame."""

    def __init__(self, frames, encs, idx, fps, res):
        self.frames, self.idx, self.fps, self.res = frames, idx, fps, res
        self.encs = encs
        self.windows = {}
        self.rects = {}
        self.border = {}
        self.edges = {}
        self.coloured_edges = {}
        self.edge_samples = {}
        self.judge_cache = {}
        self.gray = {}
        self.corners = {}
        self.shadowed = {}
        self.rgb = {}
        self.ref_boxes = {}
        self.depth_folds = {}  # frame -> [(segment x1 y1 x2 y2, colour)]: faint lines inside a part where two faces meet  # frame -> {part: sketch_part_box result}  # frame -> class map with each part colour grown into its shadow (for corners)  # frame -> [(point, dir1, dir2, colour, angle)]: where two straight edges of a colour meet
        self.folds = {}  # frame -> [(segment, colour)]: straight lines inside one colour (where two faces meet)
        self.predicted_folds = {}  # frame -> [(segment, colour)]: folds implied by obtuse corners (predict_folds)
        self.targets = {}  # frame -> (boxes' target masks (boxes, N), which boxes have one (boxes,))
        self.lite = False  # fast mode: the colour map only (no rectangles, edges, corners or folds)

    def window(self, i, p, fitter):
        """The frame's window (footage px per pixel) around the body's colours; class/shade targets in it."""
        fitter.res = self.res
        roi = fitter.roi(p, self.encs[i])
        x0, y0, k, w, h = roi
        if self.lite:
            obs, shade = fitter.prepare_lite(fitter.view(self.encs[i], roi))
        else:
            prep = fitter.prepare(fitter.view(self.encs[i], roi))
            obs, shade = prep[0].copy(), prep[3]
        gate = getattr(fitter, 'gate', None)
        if gate is not None:
            # Tracking: only what lies near where the character was last frame is it (another player beside it, or an
            # effect trailing off it, is floor).
            obs[(obs > 0) & ~gate(roi, obs)] = 0
        # Only the body's own cluster of colours is body (a lamp or an effect of the same colour further off is floor).
        body_cls = sorted(F.BODY_CLASSES)  # every body colour (a part may show several, one per side)
        stray = np.isin(obs, body_cls) & ~RR.body_cluster(obs, {c: 1 for c in body_cls})
        obs[stray] = 0
        if self.lite:
            # Only the character's own patch (scenery of a body colour inside a wide window is not the body).
            keep = F.ndimage.binary_dilation(character_mask(obs), iterations=2) & (obs > 0) | character_mask(obs)
            obs[~keep & (obs > 0)] = 0
        # The footage's colour borders are blended (blur, dithering): the 1-pixel band along them is left out of the score.
        edge = np.zeros(obs.shape, bool)
        edge[:, 1:] |= obs[:, 1:] != obs[:, :-1]
        edge[:, :-1] |= obs[:, 1:] != obs[:, :-1]
        edge[1:] |= obs[1:] != obs[:-1]
        edge[:-1] |= obs[1:] != obs[:-1]
        self.border[i] = torch.tensor(edge.reshape(-1))
        gx, gy = np.meshgrid((np.arange(w) + 0.5) / k + x0, (np.arange(h) + 0.5) / k + y0)
        self.windows[i] = (torch.tensor(np.stack([gx, gy], -1).reshape(-1, 2), dtype=DT),
                           torch.tensor(obs.reshape(-1).astype(np.int64)), torch.tensor(shade.reshape(-1), dtype=DT), 1 / k)
        if self.lite:
            # One colour per part: the colour map alone places every box, so nothing else is worked out.
            side = obs.shape[0]
            self.targets[i] = (torch.zeros((len(F.PARTS), side * side)), torch.zeros(len(F.PARTS), dtype=torch.bool))
            self.judge_cache.pop(i, None)
            return
        # The 2D stage: tight rectangles around each colour's parts (see tools/r6_rects.py).
        counts = {}
        for _, _, _, part in F.PARTS:
            if part != 'face':
                counts[F.CLASS_OF[part]] = counts.get(F.CLASS_OF[part], 0) + 1
        # The picture in the same window (straight edges are found in it), and one stud in window pixels.
        H, W = self.frames[i].shape[:2]
        xs = np.clip(gx.astype(int), 0, W - 1)
        ys = np.clip(gy.astype(int), 0, H - 1)
        gray = self.frames[i][ys, xs].astype(float) @ np.array([0.299, 0.587, 0.114])
        self.gray[i] = gray
        unit = 6 * p[2] * k
        self.rects[i] = (RR.detect_edges(gray, obs, counts, unit), obs)
        # The straight edges as a field: every pixel's distance to the nearest edge and that edge's direction.
        segs = RR.segments(gray, np.isin(obs, list(counts)), max(4, unit * 0.8))
        ids = np.zeros(obs.shape, np.int32)
        for n_, (x1, y1, x2, y2) in enumerate(segs, 1):
            cv2.line(ids, (int(round(x1)), int(round(y1))), (int(round(x2)), int(round(y2))), n_, 1)
        angles = np.array([0.0] + [math.atan2(y2 - y1, x2 - x1) for x1, y1, x2, y2 in segs])
        if len(segs):
            dist, (iy, ix) = ndimage.distance_transform_edt(ids == 0, return_indices=True)
            self.edges[i] = (dist, angles[ids[iy, ix]], segs)
        else:
            self.edges[i] = (np.full(obs.shape, 99.0), np.zeros(obs.shape), segs)
        # Each straight edge's colours: the body colour (if any) along either side of it.
        side_n = obs.shape[0]
        coloured = []
        folds = []
        for x1, y1, x2, y2 in segs:
            d = np.array([x2 - x1, y2 - y1], float)
            L = np.linalg.norm(d)
            if L < 3:
                continue
            nrm = np.array([-d[1], d[0]]) / L
            t = np.linspace(0.1, 0.9, 9)
            pts = np.stack([x1 + d[0] * t, y1 + d[1] * t], 1)
            cls_sides = {}  # colour -> which side of the edge it is on (+1 along nrm, -1 against)
            seen = []
            for sgn in (1, -1):
                q = np.clip(np.round(pts + sgn * 2.0 * nrm).astype(int), 0, side_n - 1)
                vals = obs[q[:, 1], q[:, 0]]
                vals = vals[np.isin(vals, list(counts))]
                if len(vals) >= 5:
                    c_, n_ = np.unique(vals, return_counts=True)
                    if n_.max() >= 5:
                        cls_sides.setdefault(int(c_[np.argmax(n_)]), sgn)
                        seen.append(int(c_[np.argmax(n_)]))
            if len(seen) == 2 and seen[0] == seen[1]:
                # The same colour on both sides: not a border but a fold -- two faces of one box meeting.
                folds.append((np.array([x1, y1, x2, y2], float), seen[0]))
            elif cls_sides:
                coloured.append((np.array([x1, y1, x2, y2], float), cls_sides, nrm))
        self.coloured_edges[i] = coloured
        # Corners from each part's colour grown into its own shadow (same hue, darker), so outlines follow the part's
        # real edge and a shadow line across it makes no corner.
        rgb = self.frames[i][ys, xs].astype(np.uint8)
        self.rgb[i] = rgb
        self.shadowed[i] = shadow_extend(obs, rgb, list(counts))
        self.corners[i] = find_corners(coloured, unit, self.shadowed[i], list(counts))
        self.depth_folds[i] = find_depth_folds(rgb, self.shadowed[i], list(counts), unit)
        self.edge_samples.pop(i, None)
        self.judge_cache.pop(i, None)
        # Folds from every straight line in the picture, short ones too (a face of a limb is only a stud wide).
        lsd = cv2.createLineSegmentDetector()
        raw = lsd.detect(gray.astype(np.uint8))[0] if EXPERIMENTAL else None
        for x1, y1, x2, y2 in (raw.reshape(-1, 4) if raw is not None else []):
            d = np.array([x2 - x1, y2 - y1], float)
            L = np.linalg.norm(d)
            if L < 3:
                continue
            nrm = np.array([-d[1], d[0]]) / L
            t = np.linspace(0.15, 0.85, 7)
            pts = np.stack([x1 + d[0] * t, y1 + d[1] * t], 1)
            seen = []
            for sgn in (1, -1):
                q = np.clip(np.round(pts + sgn * 1.5 * nrm).astype(int), 0, side_n - 1)
                vals = obs[q[:, 1], q[:, 0]]
                c_, n_ = np.unique(vals, return_counts=True)
                top = int(c_[np.argmax(n_)])
                seen.append(top if n_.max() >= 5 and top in counts else -1)
            if seen[0] == seen[1] and seen[0] != -1:
                folds.append((np.array([x1, y1, x2, y2], float), seen[0]))
        self.folds[i] = folds
        # Predicted folds: where two straight edges of one colour meet at an obtuse corner (a box's end, its two
        # visible end faces), the fold between those faces runs from the corner parallel to the box's long side.
        self.predicted_folds[i] = predict_folds(coloured, obs, unit) if EXPERIMENTAL else []
        self.targets.pop(i, None)


def find_depth_folds(rgb, obs, classes, unit, upscale=3):
    """The faint straight lines inside each part where one face of its box meets the next (a lit face against a
    shadier one): per part colour, only that colour's area (shadow included), its brightness stretched to the full
    range so a step of a few shades becomes a clear line, dithering blurred out, everything outside filled with the
    part's own average (its outline makes no line); straight lines found there (at 3x size), kept when at least 0.4
    stud long and lying inside the part, not on its border. [(segment in window px, colour)]"""
    val = np.asarray(Image.fromarray(rgb).convert('HSV'))[..., 2].astype(float)
    out = []
    lsd = cv2.createLineSegmentDetector()
    for cls in classes:
        m = obs == cls
        if m.sum() < unit * unit:
            continue
        inner = ndimage.binary_erosion(m, iterations=1)
        lo, hi = np.percentile(val[m], 3), np.percentile(val[m], 97)
        if hi - lo < 4:
            continue
        img = np.where(m, np.clip((val - lo) / (hi - lo), 0, 1), np.clip((val[m].mean() - lo) / (hi - lo), 0, 1))
        img = ndimage.gaussian_filter(img, 0.7)
        big = cv2.resize((img * 255).astype(np.uint8), None, fx=upscale, fy=upscale, interpolation=cv2.INTER_CUBIC)
        lines = lsd.detect(big)[0]
        if lines is None:
            continue
        for x1, y1, x2, y2 in lines.reshape(-1, 4) / upscale:
            L = math.hypot(x2 - x1, y2 - y1)
            if L < 0.4 * unit:
                continue
            t = np.linspace(0.1, 0.9, 9)
            xs = np.clip(np.round(x1 + (x2 - x1) * t).astype(int), 0, m.shape[1] - 1)
            ys = np.clip(np.round(y1 + (y2 - y1) * t).astype(int), 0, m.shape[0] - 1)
            if inner[ys, xs].mean() >= 0.8:  # inside the part, not along its border
                out.append((np.array([x1, y1, x2, y2]), cls))
    return out


def shadow_extend(obs, rgb, classes):
    """The class map with each part colour grown into connected pixels of the same hue that are darker or duller (in
    the part's own shadow), at most a stud's worth of growth from where the colour was seen."""
    hsv = np.asarray(Image.fromarray(rgb).convert('HSV')).astype(int)
    h, sat, val = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    out = obs.copy()
    for cls in classes:
        core = obs == cls
        if core.sum() < 8:
            continue
        hues = h[core]
        lo, hi = np.percentile(hues, 5), np.percentile(hues, 95)
        span = max(4, (hi - lo) / 2 + 4)
        mid = np.median(hues)
        dh = np.abs((h - mid + 128) % 256 - 128)
        cand = (dh <= span) & (sat >= 50) & (val >= 25) & ((obs == 0) | (obs == F.UNKNOWN) | core)
        grown = ndimage.binary_propagation(core, mask=cand | core)
        out[grown & ~core & ((obs == 0) | (obs == F.UNKNOWN))] = cls
    return out


def road_mask(rgb, obs, unit):
    """The road (floor) in a window: its colour learned from the window's outer ring (mostly floor: the median and
    spread of the background pixels there), then every background pixel of that colour within a tight band, in
    patches joined to the ring or at least a third of a stud^2. Very dark pixels (black clothes, hair, another
    player's dark arm, the floor's grid lines) and lighter greys (a hat) fall outside the band, so they never count."""
    side = obs.shape[0]
    ring = np.zeros(obs.shape, bool)
    b = max(2, side // 20)
    ring[:b], ring[-b:], ring[:, :b], ring[:, -b:] = True, True, True, True
    px = rgb.astype(float)
    bg = (obs == 0)
    val = px.max(-1)
    cand = ring & bg & (val >= 30)
    if cand.sum() < 20:
        return np.zeros(obs.shape, bool)
    med = np.median(px[cand], 0)
    mad = np.median(np.abs(px[cand] - med), 0) * 1.4826
    band = np.clip(2.5 * mad, 6, 14)
    # Its greyness too: the floor's channel differences stay as in the floor (dark clothes lean red/blue).
    dif = px - px.mean(-1, keepdims=True)
    mdif = med - med.mean()
    road = bg & np.all(np.abs(px - med) <= band, -1) & np.all(np.abs(dif - mdif) <= 5, -1)
    road = ndimage.binary_opening(road, np.ones((2, 2), bool))
    lab, n = ndimage.label(road)
    if n == 0:
        return road
    keep = np.zeros(n + 1, bool)
    keep[np.unique(lab[ring & road])] = True
    sizes = ndimage.sum(road, lab, range(1, n + 1))
    keep[1:] |= sizes >= unit * unit / 3
    keep[0] = False
    return keep[lab]


def part_shade_mask(rgb, obs, cls, unit):
    """One colour's pixels plus its shading: pixels of the same hue, however much darker (the faces of a limb turned
    away from the light), joined to the colour, at most a stud's growth from it -- taking pixels the colour split
    called another class or unknown, never other body colours' own hues."""
    core = obs == cls
    if core.sum() < 8:
        return core
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV_FULL).astype(int)
    h, sat, val = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    hues = h[core]
    mid = np.median(hues)
    span = max(6, (np.percentile(hues, 95) - np.percentile(hues, 5)) / 2 + 6)
    dh = np.abs((h - mid + 128) % 256 - 128)
    cand = (dh <= span) & (sat >= 60) & (val >= 30)
    # Another body colour's pixels only where clearly darker than this colour (its shade the split misread).
    other = (obs != cls) & (obs != 0) & (obs != F.UNKNOWN)
    cand &= ~other | (val < 0.75 * np.median(val[core]))
    near = ndimage.distance_transform_edt(~core) <= unit
    grown = ndimage.binary_propagation(core, mask=(cand & near) | core)
    return grown


def part_pieces(rgb, obs, cls, unit, min_px=60):
    """One colour's separate pieces with their shading: each connected area of the colour itself (at least min_px)
    is a piece, and every shading pixel (part_shade_mask) goes to the piece nearest it -- so two limbs of one colour
    whose shaded faces touch stay two pieces. [mask] largest first."""
    core = obs == cls
    lab, n = ndimage.label(core)
    if n == 0:
        return []
    sizes = ndimage.sum(core, lab, range(1, n + 1))
    keep = [k + 1 for k in range(n) if sizes[k] >= min_px]
    if not keep:
        return []
    seeds = np.where(np.isin(lab, keep), lab, 0)
    _, (iy, ix) = ndimage.distance_transform_edt(seeds == 0, return_indices=True)
    nearest = seeds[iy, ix]
    shade = part_shade_mask(rgb, obs, cls, unit)
    out = [shade & (nearest == k) for k in keep]
    return sorted(out, key=lambda m: -int(m.sum()))


def _line_through(pts):
    """Least-squares line through points: (point on it, unit direction)."""
    vx, vy, x0, y0 = cv2.fitLine(pts.astype(np.float32), cv2.DIST_L2, 0, 0.01, 0.01).reshape(-1)
    return np.array([x0, y0]), np.array([vx, vy])


def find_corners(coloured, unit, obs=None, classes=(), min_angle=50, max_angle=175):
    """Box corners in the footage, three ways, merged (within 0.25 stud):
    - each colour area's outline simplified to a polygon: its convex vertices are corners (found however blurred);
    - two straight edges of a colour whose ends meet (within 0.3 stud);
    - two straight edges of a colour that stop short of each other but whose lines cross within half a stud of both
      ends (the line finder stops a few pixels before a blurred corner).
    Angles 50-175 degrees; every corner sits on a shade of its own colour (just inside it, between its edges), on the
    character's own body (the largest connected area of body colours).
    [(corner point, unit direction along edge 1, along edge 2 (both away), colour, angle)]"""
    found = []
    # The character's own body: the largest connected area of body colours (shadows included). A patch of a body
    # colour elsewhere (the dummy's red) is not part of it.
    own_body = None
    if obs is not None and len(classes):
        bm = ndimage.binary_closing(np.isin(obs, list(classes)), iterations=1)
        lab_b, n_b = ndimage.label(bm)
        if n_b:
            sizes_b = ndimage.sum(bm, lab_b, range(1, n_b + 1))
            own_body = lab_b == 1 + int(np.argmax(sizes_b))

    def add(pt, d1, d2, c):
        d1 = d1 / max(1e-9, np.linalg.norm(d1))
        d2 = d2 / max(1e-9, np.linalg.norm(d2))
        if obs is not None:
            # The corner must sit on a shade of its own colour: stepping into the corner (between its two edges) by
            # 0.3 or 0.6 stud lands on that colour (shadowed shades included).
            inward = d1 + d2
            inward = inward / max(1e-9, np.linalg.norm(inward))
            H, W = obs.shape
            on = False
            for f in (0.3, 0.6):
                q = np.round(np.asarray(pt, float) + inward * f * unit).astype(int)
                if 0 <= q[0] < W and 0 <= q[1] < H and obs[q[1], q[0]] == c and (own_body is None or own_body[q[1], q[0]]):
                    on = True
                    break
            if not on:
                return
        ang = math.degrees(math.acos(float(np.clip(d1 @ d2, -1, 1))))
        if min_angle <= ang <= max_angle:
            found.append((np.asarray(pt, float), d1, d2, c, ang))

    # Outlines.
    if obs is not None:
        for c in classes:
            m = (obs == c).astype(np.uint8)
            if m.sum() < unit * unit * 0.3:
                continue
            m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
            contours, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
            for reach in (0.25, 0.4):  # short: a slanted end's two close corners; longer: gentle bends
              w = max(2, int(round(reach * unit)))
              for cnt in contours:
                  if cv2.contourArea(cnt) < unit * unit * 0.3:
                      continue
                  pts = cnt.reshape(-1, 2).astype(float)
                  n = len(pts)
                  if n < 4 * w:
                      continue
                  # Smooth the pixel staircase a little, then measure how much the outline turns at every point.
                  k_ = np.ones(3) / 3
                  pts = np.stack([np.convolve(np.r_[pts[-1:, c], pts[:, c], pts[:1, c]], k_, 'valid') for c in (0, 1)], 1)
                  orient = np.sign(cv2.contourArea(pts.astype(np.float32), oriented=True)) or 1.0
                  a_ = np.roll(pts, w, 0) - pts
                  b_ = np.roll(pts, -w, 0) - pts
                  cosang = (a_ * b_).sum(1) / (np.linalg.norm(a_, axis=1) * np.linalg.norm(b_, axis=1) + 1e-9)
                  turn = 180 - np.degrees(np.arccos(np.clip(cosang, -1, 1)))
                  cross = a_[:, 0] * b_[:, 1] - a_[:, 1] * b_[:, 0]
                  for k in range(n):
                      if turn[k] < 180 - max_angle or cross[k] * orient > 0:
                          continue  # too straight, or concave (where parts overlap)
                      win = turn[np.arange(k - w, k + w + 1) % n]
                      if turn[k] < win.max() or (turn[k] == win.max() and k % n != (np.arange(k - w, k + w + 1) % n)[int(np.argmax(win))]):
                          continue  # not the sharpest point of this bend
                      # Flush to the edges: a line fitted to the outline on each side (past the corner's rounding), the
                      # corner where they cross.
                      side_a = pts[np.arange(k - 2 * w, k) % n]
                      side_b = pts[np.arange(k + 1, k + 2 * w + 1) % n]
                      pa, da = _line_through(side_a)
                      pb, db = _line_through(side_b)
                      den = da[0] * db[1] - da[1] * db[0]
                      corner = pts[k]
                      if abs(den) > 1e-3:
                          t_ = ((pb[0] - pa[0]) * db[1] - (pb[1] - pa[1]) * db[0]) / den
                          x = pa + t_ * da
                          if np.linalg.norm(x - pts[k]) < 0.6 * unit:
                              corner = x
                      a_dir = side_a.mean(0) - corner
                      b_dir = side_b.mean(0) - corner
                      add(corner, a_dir, b_dir, c)
    # Straight edges meeting, or crossing when extended.
    segs = [(seg, c) for seg, sides, _ in coloured for c in sides]
    for a in range(len(segs)):
        for b in range(a + 1, len(segs)):
            (s1, c1), (s2, c2) = segs[a], segs[b]
            if c1 != c2:
                continue
            p1, q1, p2, q2 = s1[:2], s1[2:], s2[:2], s2[2:]
            r, t_ = q1 - p1, q2 - p2
            den = r[0] * t_[1] - r[1] * t_[0]
            if abs(den) < 1e-6:
                continue
            u_ = ((p2[0] - p1[0]) * t_[1] - (p2[1] - p1[1]) * t_[0]) / den
            x = p1 + u_ * r  # where the two lines cross
            e1 = min((p1, q1), key=lambda e: np.linalg.norm(e - x))
            e2 = min((p2, q2), key=lambda e: np.linalg.norm(e - x))
            if max(np.linalg.norm(e1 - x), np.linalg.norm(e2 - x)) > 0.5 * unit:
                continue
            f1 = q1 if e1 is p1 else p1  # the far ends: the edges leave the corner toward them
            f2 = q2 if e2 is p2 else p2
            add(x, f1 - x, f2 - x, c1)
    # Merge corners of a colour closer than 0.25 stud (keep the first).
    out = []
    for cn in found:
        if all(cn[3] != o[3] or np.linalg.norm(cn[0] - o[0]) > 0.25 * unit for o in out):
            out.append(cn)
    return out


def corner_hits(hull_pts, corners, stud):
    """How many footage corners a box outline lands on: an outline vertex within 0.35 stud of the corner whose two
    outline edges point the same ways as the corner's two edges (within 12 degrees, either pairing)."""
    if not corners or len(hull_pts) < 3:
        return 0
    n = len(hull_pts)
    hits = 0
    for cpt, d1, d2, _, ang in corners:
        dist = np.hypot(*(hull_pts - cpt).T)
        vi = int(np.argmin(dist))
        if dist[vi] > 0.35 * stud:
            continue
        v = hull_pts[vi]
        o1 = hull_pts[(vi - 1) % n] - v
        o2 = hull_pts[(vi + 1) % n] - v
        o1, o2 = o1 / max(1e-9, np.linalg.norm(o1)), o2 / max(1e-9, np.linalg.norm(o2))
        # A shallow corner (a slight bend) needs a tighter direction match, or a straight edge would pass for it.
        tol = math.cos(math.radians(min(12.0, max(3.0, (180 - ang) / 3))))
        if (o1 @ d1 > tol and o2 @ d2 > tol) or (o1 @ d2 > tol and o2 @ d1 > tol):
            hits += 1
    return hits


def predict_folds(coloured, obs, unit, min_angle=95, max_angle=175):
    """Folds implied by corners: two straight edges of the same colour whose ends meet (within 0.3 stud) at an obtuse
    angle are a box's end seen at a slant; from that corner a line is drawn parallel to the box's long side -- the
    longer of the two edges, or, when that would only retrace an edge (the two are the end's short sides), the longest
    edge of that colour starting near the corner -- into the colour, as long as that side. [(segment, colour)]"""
    out = []
    side_n = obs.shape[0]
    tol = 0.3 * unit
    segs = [(seg, c) for seg, sides, _ in coloured for c in sides]
    for a in range(len(segs)):
        for b in range(a + 1, len(segs)):
            (s1, c1), (s2, c2) = segs[a], segs[b]
            if c1 != c2:
                continue
            e1 = [s1[:2], s1[2:]]
            e2 = [s2[:2], s2[2:]]
            pair = min(((p, q) for p in range(2) for q in range(2)), key=lambda pq: np.linalg.norm(e1[pq[0]] - e2[pq[1]]))
            if np.linalg.norm(e1[pair[0]] - e2[pair[1]]) > tol:
                continue
            corner = (e1[pair[0]] + e2[pair[1]]) / 2
            d1 = e1[1 - pair[0]] - e1[pair[0]]
            d2 = e2[1 - pair[1]] - e2[pair[1]]
            ang = math.degrees(math.acos(np.clip(d1 @ d2 / (np.linalg.norm(d1) * np.linalg.norm(d2) + 1e-9), -1, 1)))
            if not (min_angle <= ang <= max_angle):
                continue
            # The long side: the longest same-colour edge with an end within a stud of the corner, other than these two
            # if both are short (an end's sides); else the longer of the two.
            l1, l2 = np.linalg.norm(d1), np.linalg.norm(d2)
            long_dir, long_len = (d1, l1) if l1 >= l2 else (d2, l2)
            near = []
            for k, (s3, c3) in enumerate(segs):
                if k in (a, b) or c3 != c1:
                    continue
                ends3 = [s3[:2], s3[2:]]
                if min(np.linalg.norm(e - corner) for e in ends3) <= unit:
                    d3 = s3[2:] - s3[:2]
                    near.append((np.linalg.norm(d3), d3))
            if near and max(near, key=lambda e: e[0])[0] > max(l1, l2):
                long_len, long_dir = max(near, key=lambda e: e[0])
            u = long_dir / max(1e-9, np.linalg.norm(long_dir))
            # Into the colour: the direction along which the colour lies just past the corner.
            best = None
            for sgn in (1, -1):
                probe = corner + sgn * u * min(long_len, unit) * 0.6
                x, y = int(round(probe[0])), int(round(probe[1]))
                if 0 <= x < side_n and 0 <= y < side_n and obs[y, x] == c1:
                    best = sgn
                    break
            if best is None:
                continue
            end = corner + best * u * long_len
            out.append((np.array([corner[0], corner[1], end[0], end[1]]), c1))
    return out


def frame_loss(prob, sh, obs, shade, ncls, side=None):
    """Per-sample loss: cross-entropy of each pixel's class (floor counts less, UNKNOWN not at all), plus shading, plus
    the same comparison blurred at 1/4 and 1/8 size: there a stretch of colour with no box near it (an arm thrown out)
    still draws the nearest box of that colour, from far beyond the sharp edges' reach."""
    eps = 1e-4
    known = obs != F.UNKNOWN
    o = torch.where(known, obs, torch.zeros_like(obs))
    p = torch.gather(prob, 2, torch.clamp(o, max=ncls - 1).unsqueeze(-1)).squeeze(-1)
    if F.FACE > 0:
        p = torch.where(o == F.FACE, p + prob[..., F.HEAD], p)
    wgt = torch.where(o == 0, 0.6, 1.0) * known.float()
    ce = -(torch.log(p + eps) * wgt).mean(-1)
    body = (o > 0) & known & (shade > 0)
    sl = ((sh - shade).abs() * body.float()).mean(-1)
    out = ce + 0.5 * sl
    if side:
        B = prob.shape[0]
        target = torch.nn.functional.one_hot(o, ncls).to(DT)
        target = torch.where(known.unsqueeze(-1), target, prob.detach())  # hidden pixels: whatever the render says
        a = prob.transpose(1, 2).reshape(B, ncls, side, side)
        b = target.transpose(1, 2).reshape(B, ncls, side, side)
        for k, wgt in ((4, 2.0), (8, 3.0)):
            pa = torch.nn.functional.avg_pool2d(a, k, ceil_mode=True)
            pb = torch.nn.functional.avg_pool2d(b, k, ceil_mode=True)
            out = out + wgt * (pa - pb).abs()[:, 1:].sum(1).mean((-1, -2))
    return out


class Fit:
    """The optimisation: shared joints on view 1's timeline, a camera per frame per view."""

    def __init__(self, views, J0, cams0, persp, lights):
        self.views = views
        self.J = torch.tensor(J0, dtype=DT, requires_grad=True)
        self.cams = [(torch.tensor(c, dtype=DT) / CAM_UNIT).requires_grad_() for c in cams0]
        self.persp = persp
        self.lights = [(torch.tensor(l[0], dtype=DT), float(l[1])) for l in lights]
        self.classes = face_classes()
        self.head_lock = None  # per-frame weight on the head's turn (None: the head is free)
        self.lean_w = None  # the body-lean hold (None: BODY_LEAN_W; small once the camera is known)
        self.ncls = max(F.CLASS_OF.values()) + 1

    def joints_at(self, v, k):
        """View v's frame k on view 1's timeline (linear between view 1's frames)."""
        if v == 0:
            return self.J[k]
        n2, n1 = len(self.views[v].idx), len(self.views[0].idx)
        s = k * (n1 - 1) / max(1, n2 - 1)
        a = int(math.floor(s))
        b, f = min(n1 - 1, a + 1), s - a
        return self.J[a] * (1 - f) + self.J[b] * f

    def assign(self, v, k):
        """Each box's rectangle in frame k (by overlap with where the box is now); the target is the rectangle's pixels
        of its colour."""
        view = self.views[v]
        i = view.idx[k]
        rects, obs = view.rects[i]
        side = view.res
        with torch.no_grad():
            J = self.joints_at(v, k)[None]
            cam = self.cams[v][[k]] * CAM_UNIT
            quads, depth, vis, shade = geometry(J, cam, self.persp[v], self.lights[v])
            pix = view.windows[i][0]
            _, _, pprob = rasterise(quads, depth, vis, shade, pix[None], 0.3 * view.windows[i][3], self.classes, self.ncls, parts=True)
            hard = pprob[0].argmax(-1).reshape(side, side).numpy()
            covered = pprob[0].sum(-1).reshape(side, side).numpy() > 0.5
        part_cls = [F.CLASS_OF[part] for _, _, _, part in F.PARTS]
        masks = [(hard == j) & covered for j in range(len(F.PARTS))]
        names = ['torso' if part == 'torso' else 'head' if part == 'head' else part for _, _, _, part in F.PARTS]
        pairs = RR.assign(masks, [c if F.PARTS[j][3] != 'face' else -1 for j, c in enumerate(part_cls)], rects, names)
        tgt = np.zeros((len(F.PARTS), side * side), np.float32)
        has = np.zeros(len(F.PARTS), bool)
        for j, r in pairs:
            m = r.mask((side, side), colour=(obs == r.cls))
            if m.sum() > 4:
                tgt[j], has[j] = m.reshape(-1), True
        view.targets[i] = (torch.tensor(tgt), torch.tensor(has))

    def box_scores(self, v, k):
        """Each box with a rectangle: how much of the rectangle's colour it covers, as overlap (IoU) of its visible
        silhouette with those pixels -- the fit's accuracy, part by part."""
        view = self.views[v]
        i = view.idx[k]
        if i not in view.targets:
            self.assign(v, k)
        tgt, has = view.targets[i]
        with torch.no_grad():
            J = self.joints_at(v, k)[None]
            cam = self.cams[v][[k]] * CAM_UNIT
            quads, depth, vis, shade = geometry(J, cam, self.persp[v], self.lights[v])
            _, _, pprob = rasterise(quads, depth, vis, shade, view.windows[i][0][None], 0.3 * view.windows[i][3],
                                    self.classes, self.ncls, parts=True)
            hard = pprob[0].argmax(-1)
            covered = pprob[0].sum(-1) > 0.5
        out = {}
        for j in range(len(F.PARTS)):
            if has[j]:
                mine = (hard == j) & covered
                t = tgt[j] > 0.5
                out[F.PARTS[j][3]] = float((mine & t).sum()) / max(1.0, float((mine | t).sum()))
        return out

    def loss_frames(self, v, ks, sigma_px, stride=1):
        """Soft loss of frames ks; stride 2 uses every other pixel row and column (a quarter of the work)."""
        view = self.views[v]
        side = view.res
        sel = torch.arange(side * side).reshape(side, side)[::stride, ::stride].reshape(-1) if stride > 1 else None
        if sel is not None:
            side = len(range(0, view.res, stride))
        J = torch.stack([self.joints_at(v, k) for k in ks])
        cam = self.cams[v][ks] * CAM_UNIT
        quads, depth, vis, shade = geometry(J, cam, self.persp[v], self.lights[v])
        pix = torch.stack([view.windows[view.idx[k]][0] for k in ks])
        obs = torch.stack([view.windows[view.idx[k]][1] for k in ks])
        tsh = torch.stack([view.windows[view.idx[k]][2] for k in ks])
        scale = torch.tensor([view.windows[view.idx[k]][3] for k in ks], dtype=DT) * stride
        if sel is not None:
            pix, obs, tsh = pix[:, sel], obs[:, sel], tsh[:, sel]
        for k in ks:
            if view.idx[k] not in view.targets:
                self.assign(v, k)
        # Each box's share of the pixels is only worked out when some box has a rectangle to be pulled onto.
        rects = any(bool(view.targets[view.idx[k]][1].any()) for k in ks)
        out = rasterise(quads, depth, vis, shade, pix, (sigma_px * scale)[:, None, None], self.classes, self.ncls,
                        parts=rects)
        prob, sh = out[0], out[1]
        loss = frame_loss(prob, sh, obs, tsh, self.ncls, side)
        # Every box onto its own rectangle: soft overlap of the box's share of each pixel with the rectangle's colour.
        for b, k in enumerate(ks):
            i = view.idx[k]
            tgt, has = view.targets[i]
            if has.any():
                pprob = out[2]
                p = pprob[b][:, has].T  # (boxes with a rectangle, N)
                t = tgt[has] if sel is None else tgt[has][:, sel]
                inter = (p * t).sum(-1)
                union = p.sum(-1) + t.sum(-1) - inter
                loss = loss + torch.cat([loss.new_zeros(b), (RECT_W * (1 - inter / (union + 1e-6))).sum()[None],
                                         loss.new_zeros(len(ks) - b - 1)])
        return loss

    def regular(self):
        J = self.J
        r = (torch.relu(J - HI) ** 2 + torch.relu(LO - J) ** 2).sum() * 0.01
        if self.head_lock is not None:
            # The head stays square on the body (R6 moves rarely turn it): tilt held hard; turn held hard too unless
            # the face shows in that frame (head_lock[k]: the turn's weight per frame).
            hx, hy, hz = (F.JI[('head', a)] - NC for a in range(3))
            r = r + ((J[:, hx] ** 2 + J[:, hz] ** 2).sum() * HEAD_TILT_W + (self.head_lock * J[:, hy] ** 2).sum())
            # The whole body tipped and the camera tilted look the same from one view: the body is held upright and
            # the camera's tilt takes it (a Roblox camera tilts; a standing character does not lean).
            bx, bz = F.JI[('hips', 0)] - NC, F.JI[('hips', 2)] - NC
            r = r + (J[:, bx] ** 2 + J[:, bz] ** 2).sum() * (BODY_LEAN_W if self.lean_w is None else self.lean_w)
            if self.lean_w is not None:
                # Body sense: the torso stays over the hips (lean and roll past 45 deg cost steeply).
                r = r + ((torch.relu(J[:, bx].abs() - 45) ** 2) + (torch.relu(J[:, bz].abs() - 45) ** 2)).sum() * 0.05
        r = r + (J[:, 0] ** 2 + J[:, 2] ** 2).sum() * 2e-5
        if len(J) > 2:
            acc = J[2:] - 2 * J[1:-1] + J[:-2]
            r = r + (acc ** 2).sum() * 2e-4
        for c in self.cams:
            if len(c) > 1:
                d = (c[1:] - c[:-1]) * CAM_UNIT
                r = r + (d[:, :2] ** 2).sum() * 0.05 + (d[:, 2] ** 2).sum() * 50 + (d[:, 3:] ** 2).sum() * 0.002
        return r

    def params_of(self, v, k):
        p = np.zeros(F.NP)
        p[:5] = (self.cams[v][k] * CAM_UNIT).detach().numpy()
        p[5] = self.persp[v]
        p[NC:] = self.joints_at(v, k).detach().numpy()
        return p


def limb_candidates(j):
    """Joint vectors trying each limb's swing (forward/back, out to the side and across the body), the body's lean and
    turn, and the left/right swaps."""
    out = [j.clone()]
    for limb in ('rightArm', 'leftArm', 'rightThigh', 'leftThigh'):
        ix, iz = F.JI[(limb, 0)] - NC, F.JI[(limb, 2)] - NC
        sgn = 1 if limb.startswith('right') else -1
        arm = limb.endswith('Arm')
        for x in (-180, -140, -100, -60, -20, 20, 60):
            for z in ((-40, 0, 45, 90, 135) if arm else (-20, 0, 40, 80)):
                q = j.clone()
                q[ix], q[iz] = x, sgn * z
                out.append(q)
    for x in (-90, -45, 0, 45, 90):
        for z in (-60, 0, 60):
            for y in (-120, -60, 0, 60, 120, 180):
                q = j.clone()
                q[F.JI[('hips', 0)] - NC], q[F.JI[('hips', 2)] - NC] = x, z
                q[F.JI[('hips', 1)] - NC] = j[F.JI[('hips', 1)] - NC] + y
                out.append(q)
    for r, l in (('rightArm', 'leftArm'), ('rightThigh', 'leftThigh')):
        q = j.clone()
        q[F.JI[(r, 0)] - NC], q[F.JI[(l, 0)] - NC] = j[F.JI[(l, 0)] - NC], j[F.JI[(r, 0)] - NC]
        for ax in (1, 2):
            q[F.JI[(r, ax)] - NC], q[F.JI[(l, ax)] - NC] = -j[F.JI[(l, ax)] - NC], -j[F.JI[(r, ax)] - NC]
        out.append(q)
    return torch.stack(out)


def window_loss(fit, v, i, J, cam, sigma, stride=1):
    """Loss of a batch of poses (J (B, joints), cam (B, 5) real units) in frame i's window, optionally on every
    stride-th pixel row and column (a coarser window for ranking many poses)."""
    view = fit.views[v]
    pix, obs, tsh, scale = view.windows[i]
    side = view.res
    if stride > 1:
        sel = torch.arange(side * side).reshape(side, side)[::stride, ::stride].reshape(-1)
        pix, obs, tsh, side = pix[sel], obs[sel], tsh[sel], len(range(0, side, stride))
    quads, depth, vis, shade = geometry(J, cam, fit.persp[v], fit.lights[v])
    n = J.shape[0]
    prob, sh, pprob = rasterise(quads, depth, vis, shade, pix.expand(n, -1, -1), sigma * scale * stride, fit.classes,
                                fit.ncls, parts=True)
    loss = frame_loss(prob, sh, obs.expand(n, -1), tsh.expand(n, -1), fit.ncls, side)
    if i in view.targets:
        # Each box onto its own rectangle, as in Fit.loss_frames (rectangles assigned from the current pose).
        tgt, has = view.targets[i]
        if has.any():
            t = tgt[has][:, sel] if stride > 1 else tgt[has]
            p = pprob[:, :, has].transpose(1, 2)  # (B, boxes, N)
            inter = (p * t).sum(-1)
            union = p.sum(-1) + t.sum(-1) - inter
            loss = loss + RECT_W * (1 - inter / (union + 1e-6)).sum(-1)
    return loss


# Hard colour scores drawn by painting each candidate's faces (farthest first) with OpenCV instead of the soft rasteriser:
# the same picture argmax(rasterise) gives (bar sub-pixel edges and faces cutting into each other), at ~1/50 the cost.
# R6_SOFT_SCORE=1 goes back to the soft rasteriser.
DEBUG = os.environ.get('R6_DEBUG', '0') == '1'
HARD_SCORE = os.environ.get('R6_SOFT_SCORE', '0') != '1'


# cv2.fillConvexPoly paints every pixel its outline touches: a polygon comes out ~half a pixel bigger all round than the
# pixels whose centres it covers (what the soft rasteriser's argmax, and the footage sampled at pixel centres, show).
# At 112 px per window that is one window pixel (~2 footage px) on every box's width and height. EXACT_EDGES insets
# each face's edges on a box's outline (those whose neighbouring side of the same box is not drawn) by half a pixel, so
# a box covers the pixel centres inside it; the edges between two drawn sides of one box stay where they are (no seam).
# R6_LOOSE_EDGES=1 goes back to the loose drawing.
EXACT_EDGES = os.environ.get('R6_LOOSE_EDGES', '0') != '1'
_ADJ = np.zeros((6, 4), np.int64)  # the side of the same box across each edge of each side
for _f in range(6):
    for _j in range(4):
        _e = {int(FACE_IDX[_f, _j]), int(FACE_IDX[_f, (_j + 1) % 4])}
        _ADJ[_f, _j] = next(g for g in range(6) if g != _f and _e <= set(FACE_IDX[g].tolist()))
_ADJ_ALL = (np.arange(len(F.PARTS))[:, None, None] * 6 + _ADJ[None]).reshape(-1, 4)  # (faces, 4) global side index


def _inset_outline(qw, show, d=0.5):
    """Faces (B, Fc, 4, 2) in window px with each edge on its box's outline moved in by d px; faces left with no
    inside drop out of `show`."""
    a = qw
    b = np.roll(qw, -1, 2)
    e = b - a
    L = np.maximum(np.linalg.norm(e, axis=-1), 1e-6)
    area = (a[..., 0] * b[..., 1] - b[..., 0] * a[..., 1]).sum(-1)
    s = np.where(area >= 0, 1.0, -1.0)[..., None]
    n = np.stack([-e[..., 1], e[..., 0]], -1) * (s / L)[..., None]  # inward unit normals (B, Fc, 4, 2)
    dist = d * ~show[:, _ADJ_ALL]  # (B, Fc, 4): an edge whose neighbour side is not drawn is on the outline
    npv, dpv = np.roll(n, 1, 2), np.roll(dist, 1, 2)  # the edge ending at each vertex
    det = n[..., 0] * npv[..., 1] - n[..., 1] * npv[..., 0]
    ok = np.abs(det) > 0.05
    det = np.where(ok, det, 1.0)
    x = (dist * npv[..., 1] - dpv * n[..., 1]) / det
    y = (n[..., 0] * dpv - npv[..., 0] * dist) / det
    mv = np.where(ok[..., None], np.stack([x, y], -1), n * np.maximum(dist, dpv)[..., None])
    mv = np.clip(mv, -3 * d, 3 * d)
    out = qw + mv
    a2, b2 = out, np.roll(out, -1, 2)
    area2 = (a2[..., 0] * b2[..., 1] - b2[..., 0] * a2[..., 1]).sum(-1)
    return out, show & (area2 * area > 0)


def hard_draw(view, i, J, cam, persp, light, classes, leave_out=()):
    """Each candidate's hard picture in frame i's window: (B, res, res) class labels (0 = floor), the nearest face on
    top (faces sorted by their centres' depth, as rasterise does), back faces and slivers under ~10 px left out."""
    pix, _, _, scale = view.windows[i]
    res = view.res
    k = 1.0 / float(scale)
    x0, y0 = float(pix[0, 0]) - 0.5 * float(scale), float(pix[0, 1]) - 0.5 * float(scale)
    with torch.no_grad():
        quads, depth, vis, _ = geometry(J, cam, persp, light)
    q = quads.numpy()
    a_ = q[..., 0] * np.roll(q[..., 1], -1, 2) - np.roll(q[..., 0], -1, 2) * q[..., 1]
    area = np.abs(a_.sum(-1))  # twice the area, footage px (B, Fc)
    show = (vis.numpy() > 0.5) & (area >= 20.0)
    if leave_out:
        keep = np.array([part not in leave_out for _, _, _, part in F.PARTS]).repeat(6)
        show &= keep[None]
    # Window pixel coordinates (pixel centres on integers), 4 bits of sub-pixel precision for OpenCV.
    qw = (q - [x0, y0]) * k - 0.5
    if EXACT_EDGES:
        # Twice: a side seen nearly edge-on (thinner than a pixel) has no inside once inset, so its neighbour's edge
        # along it is on the outline after all.
        show = _inset_outline(qw, show)[1]
        qw, show = _inset_outline(qw, show)
    pts = np.round(qw * 16).astype(np.int32)
    order = np.argsort(depth.numpy(), 1)  # farthest first (depth grows toward the camera)
    cls = classes.numpy()
    out = np.zeros((len(q), res, res), np.uint8)
    cls = cls.tolist()
    fill = cv2.fillConvexPoly
    for b in range(len(q)):
        img, pb = out[b], pts[b]
        ob = order[b]
        for f in ob[show[b, ob]].tolist():
            fill(img, pb[f], cls[f], cv2.LINE_8, 4)
    return out


def _hard_counts(view, i, drawn, stride=1):
    """drawn (B, res, res) against frame i's colours: agreeing and body pixels (border and unknown left out)."""
    _, obs, _, _ = view.windows[i]
    res = view.res
    o = obs.numpy().reshape(res, res)
    known = (o != F.UNKNOWN) & ~view.border[i].numpy().reshape(res, res)
    if F.FACE > 0:
        o = np.where(o == F.FACE, F.HEAD, o)
        drawn = np.where(drawn == F.FACE, F.HEAD, drawn)
    if stride > 1:
        o, known, drawn = o[::stride, ::stride], known[::stride, ::stride], drawn[:, ::stride, ::stride]
    return o, known, drawn


def agreement(fit, v, i, J, cam, stride=1):
    """The fit's rule: drawn hard (the boxes at their exact R6 sizes), how many pixels have the footage's colour.
    Agreeing body pixels over all body pixels of either (a box on floor, or body colour with no box on it, both count
    against; pixels hidden behind something are left out). 1.0 is a perfect fit. Batched: (B,)."""
    view = fit.views[v]
    if HARD_SCORE:
        drawn = hard_draw(view, i, J.detach(), cam.detach(), fit.persp[v], fit.lights[v], fit.classes)
        o, known, drawn = _hard_counts(view, i, drawn, stride)
        body = (((o > 0)[None] | (drawn > 0)) & known[None]).sum((1, 2))
        agree = ((drawn == o[None]) & (o > 0)[None] & known[None]).sum((1, 2))
        return torch.tensor(agree / np.maximum(body, 1), dtype=DT)
    pix, obs, _, scale = view.windows[i]
    border = view.border[i]
    if stride > 1:
        # Every stride-th pixel each way (candidates ranked at a fraction of the work).
        side = view.res
        sel = torch.arange(side * side).reshape(side, side)[::stride, ::stride].reshape(-1)
        pix, obs, border = pix[sel], obs[sel], border[sel]
    with torch.no_grad():
        quads, depth, vis, shade = geometry(J, cam, fit.persp[v], fit.lights[v])
        out = []
        for c in range(0, J.shape[0], 16):
            sl = slice(c, c + 16)
            n = quads[sl].shape[0]
            prob, _ = rasterise(quads[sl], depth[sl], vis[sl], shade[sl], pix.expand(n, -1, -1), 0.05 * scale, fit.classes, fit.ncls)
            drawn = prob.argmax(-1)
            if F.FACE > 0:
                drawn = torch.where(drawn == F.FACE, F.HEAD, drawn)
            o = torch.where(obs == F.FACE, F.HEAD, obs) if F.FACE > 0 else obs
            known = (o != F.UNKNOWN) & ~border
            body = ((o > 0) | (drawn > 0)) & known
            agree = (drawn == o) & (o > 0) & known
            out.append(agree.sum(-1).float() / body.sum(-1).clamp(min=1).float())
        return torch.cat(out)


# Corner indices (x, y, z picks as in CORNERS: index = 4 xi + 2 yi + zi) of a box's two end caps along its long axis.
CAP_FAR = {2, 3, 6, 7}   # local y max: a limb's hand / foot end, the torso's hip end
CAP_NEAR = {0, 1, 4, 5}  # local y min: the joint end, the torso's shoulder end
SEEK_OVERLAP = 0.5  # a box over another part's colour (one part in front of another): half credit, not a fault
# The hard end rule (every limb that shows has its hand or shoulder end flush on an edge of its colour). On footage
# whose limb ends are blurred or hidden (the wiki GIFs) no pose can keep it, and it drags the fit: R6_END_RULE=0 off.
# Experimental rules, off by default (R6_EXPERIMENTAL=1 turns them on): the end-cap score and the hard either-end
# rule, face folds and corner-predicted folds. On the wiki GIFs they made the fit worse or did nothing yet.
EXPERIMENTAL = os.environ.get('R6_EXPERIMENTAL', '0') == '1'
END_RULE = EXPERIMENTAL and os.environ.get('R6_END_RULE', '1') != '0'
# How close counts as flush, in studs (scales with the footage): measured on the wiki kick, fitted limb ends land
# 2-4 px (window) from their edges at ~12 px per stud, so 1.5 px (1/8 stud) was beyond what the blurred GIF resolves.
SIDE_TOL, END_TOL, END_ANGLE = 0.125, 0.3, 10


def inside_boxes(quads, vis, pts):
    """Hard point-in-box test, batched: quads (B, faces, 4, 2), vis (B, faces), pts (N, 2) or (B, N, 2), all footage
    px -> (B, N, boxes) True where the point is inside one of the box's visible faces (= inside its silhouette)."""
    B = quads.shape[0]
    if pts.dim() == 2:
        pts = pts.expand(B, -1, -1)
    a = quads
    e = torch.roll(quads, -1, 2) - a
    area = (a[..., 0] * torch.roll(quads, -1, 2)[..., 1] - torch.roll(quads, -1, 2)[..., 0] * a[..., 1]).sum(-1)
    orient = torch.where(area >= 0, 1.0, -1.0)
    rel = pts[:, :, None, None, :] - a[:, None]  # (B, N, F, 4, 2)
    cross = e[:, None, ..., 0] * rel[..., 1] - e[:, None, ..., 1] * rel[..., 0]
    inside = ((cross * orient[:, None, :, None]) >= 0).all(-1)
    inside &= (vis > 0)[:, None] & (area.abs() > 0.5)[:, None]
    return inside.reshape(B, pts.shape[1], len(F.PARTS), 6).any(-1)


def _edge_samples(fit, v, i):
    """The coloured straight edges of frame i as point pairs for the batched edge rule: for each sample along an edge
    and each body colour beside it, a point just on that colour's side and one just on the other side (footage px)."""
    view = fit.views[v]
    if i in view.edge_samples:
        return view.edge_samples[i]
    pix, _, _, scale = view.windows[i]
    scale = float(scale)
    x0, y0 = float(pix[0, 0]) - 0.5 * scale, float(pix[0, 1]) - 0.5 * scale
    tol = max(1.0, SIDE_TOL * 6 * float(fit.cams[v][view.idx.index(i), 2].detach() * CAM_UNIT[2]) / scale)
    p_in, p_out, cls, wgt, grp = [], [], [], [], []
    g = 0
    for seg, cls_sides, nrm in view.coloured_edges[i]:
        x1, y1, x2, y2 = seg
        L = float(np.hypot(x2 - x1, y2 - y1))
        n = max(2, int(L / 2))
        for t in (np.arange(n) + 0.5) / n:
            pt = np.array([x1 + (x2 - x1) * t, y1 + (y2 - y1) * t])
            for c, sd in cls_sides.items():
                p_in.append(pt + nrm * sd * tol)
                p_out.append(pt - nrm * sd * tol)
                cls.append(c)
                wgt.append(L / n)
                grp.append(g)
            g += 1
    to_foot = lambda q: torch.tensor(np.array(q).reshape(-1, 2) * scale + (x0, y0), dtype=DT)
    out = (to_foot(p_in), to_foot(p_out), torch.tensor(cls, dtype=torch.long), torch.tensor(wgt, dtype=DT),
           torch.tensor(grp, dtype=torch.long), g)
    view.edge_samples[i] = out
    return out


def _judge_cache(fit, v, i):
    """Per-window arrays the default judge reuses for every candidate."""
    view = fit.views[v]
    if i in view.judge_cache:
        return view.judge_cache[i]
    pix, obs_t, _, scale = view.windows[i]
    side = view.res
    obs = obs_t.numpy().reshape(side, side)
    if F.FACE > 0:
        obs = np.where(obs == F.FACE, F.HEAD, obs)
    hidden = obs == F.UNKNOWN
    body = (obs > 0) & ~hidden
    known = ~hidden & ~view.border[i].numpy().reshape(side, side)
    e_in, e_out, e_cls, e_w, e_grp, n_grp = _edge_samples(fit, v, i)
    x0, y0, sc = float(pix[0, 0]) - 0.5 * float(scale), float(pix[0, 1]) - 0.5 * float(scale), float(scale)
    to_px = lambda q: np.clip(np.floor((q.numpy() - (x0, y0)) / sc).astype(int), 0, side - 1)
    out = dict(obs=obs, hidden=hidden, body=body, known=known, x0=x0, y0=y0, sc=sc,
               e_in=to_px(e_in), e_out=to_px(e_out), e_cls=e_cls.numpy(), e_w=e_w.numpy(), e_grp=e_grp.numpy(), n_grp=n_grp)
    view.judge_cache[i] = out
    return out


def judge(fit, v, i, J, cam, detail=False, only=None):
    """The default rule (see judge_slow for the full description): colour (each box seeks its own colour, half credit
    over another part's, none on floor; every body pixel under a box of its colour) 4, coloured side edges covered by a
    box of that colour on the colour's side 1 -- tested per edge sample as a point just on the colour's side inside such
    a box and one just across outside it. Boxes drawn with OpenCV, geometry batched."""
    if EXPERIMENTAL:
        return judge_slow(fit, v, i, J, cam, detail, only)
    view = fit.views[v]
    side = view.res
    C = _judge_cache(fit, v, i)
    obs, hidden, body, known = C['obs'], C['hidden'], C['body'], C['known']
    parts = [(j, F.CLASS_OF[part]) for j, (_, _, _, part) in enumerate(F.PARTS) if part != 'face' and (only is None or part in only)]
    if only is not None:
        body = body & np.isin(obs, [c for _, c in parts])
    e_in, e_out, e_cls, e_w, e_grp, n_grp = C['e_in'], C['e_out'], C['e_cls'], C['e_w'], C['e_grp'], C['n_grp']
    if only is not None and len(e_cls):
        m_ = np.isin(e_cls, [c for _, c in parts])
        e_in, e_out, e_cls, e_w, e_grp = e_in[m_], e_out[m_], e_cls[m_], e_w[m_], e_grp[m_]
    w_g = np.zeros(n_grp)
    if len(e_cls):
        np.maximum.at(w_g, e_grp, e_w)
    body_known = (body & known).sum()
    with torch.no_grad():
        uv = geometry(J, cam, fit.persp[v], fit.lights[v], corners=True).numpy()
    uv = (uv - (C['x0'], C['y0'])) / C['sc']
    out = []
    m = np.zeros((side, side), np.uint8)
    for b in range(uv.shape[0]):
        seek_num = seek_den = 0.0
        covered = np.zeros((side, side), bool)
        ok = np.zeros(len(e_cls), bool)
        for j, cls in parts:
            m[:] = 0
            cv2.fillConvexPoly(m, np.round(cv2.convexHull(uv[b, j].astype(np.float32)).reshape(-1, 2)).astype(np.int32), 1)
            mb = m.astype(bool)
            area = mb.sum()
            if not area:
                continue
            mine = obs == cls
            own = (mb & (mine | hidden)).sum()
            other = (mb & body & ~mine).sum()
            seek_num += own + SEEK_OVERLAP * other
            seek_den += area
            covered |= mb & mine
            if len(e_cls):
                same = e_cls == cls
                ok |= same & mb[e_in[:, 1], e_in[:, 0]] & ~mb[e_out[:, 1], e_out[:, 0]]
        seek = seek_num / max(1.0, seek_den)
        cover = (covered & known).sum() / max(1, body_known)
        colour = 0.5 * (seek + cover)
        if len(e_cls):
            hit = np.zeros(n_grp)
            np.maximum.at(hit, e_grp, ok.astype(float))
            edge = float((hit * w_g).sum() / max(1e-6, w_g.sum()))
        else:
            edge = 1.0
        total = (4 * colour + edge) / 5
        out.append((total, colour, edge, 1.0, 0) if detail else total)
    return out if detail else torch.tensor(out)


def judge_slow(fit, v, i, J, cam, detail=False, only=None):
    """The rule every candidate pose is held to (boxes at their exact R6 sizes), higher is better (0..1):
    - colour: every box seeks its own colour: full credit on it, half where it covers another part's colour (parts
      overlap), none on floor; shading is not judged (any shade of the right colour counts); and every body-coloured
      pixel should be under some box of that colour;
    - edges (the steadfast rule): every straight edge of the footage with a body colour along it must be covered by
      the outline of a box of that colour, and every box's end must meet such an edge;
    by default weighted colour 4, side edges 1 (edge-led weightings scored wrong poses as highly as right ones); with R6_EXPERIMENTAL=1 also the box ends (1) and the hard rule that each
    visible limb has its hand end or its shoulder end flush on an edge of its colour.
    Batched over J (B, joints), cam (B, 5)."""
    view = fit.views[v]
    pix, obs_t, _, scale = view.windows[i]
    side = view.res
    obs = obs_t.numpy().reshape(side, side)
    if F.FACE > 0:
        obs = np.where(obs == F.FACE, F.HEAD, obs)
    known = (obs != F.UNKNOWN) & ~view.border[i].numpy().reshape(side, side)
    hidden = obs == F.UNKNOWN
    body = (obs > 0) & ~hidden
    dist, ang, _ = view.edges[i]
    unit_px = 6 * float(cam[0, 2]) / float(scale)  # one stud in window px (tolerances scale with it)
    x0, y0 = float(pix[0, 0]) - 0.5 * scale, float(pix[0, 1]) - 0.5 * scale
    with torch.no_grad():
        uv = geometry(J, cam, fit.persp[v], fit.lights[v], corners=True).numpy()
    parts = [(j, F.CLASS_OF[part]) for j, (_, _, _, part) in enumerate(F.PARTS) if part != 'face' and (only is None or part in only)]
    if only is not None:
        body = body & np.isin(obs, [c for _, c in parts])  # coverage of those parts' colours only
    out = []
    for b in range(uv.shape[0]):
        seek_num = seek_den = 0.0
        covered = np.zeros_like(body)
        outlines = []  # (class, a, b) of every box outline edge, window px
        ends = []  # (class, a, b, centre) of every outline edge belonging to a box's end cap
        for j, cls in parts:
            pts = (uv[b, j] - (x0, y0)) / scale
            hull = cv2.convexHull(pts.astype(np.float32)).reshape(-1, 2)
            m = np.zeros((side, side), np.uint8)
            cv2.fillPoly(m, [np.round(hull).astype(np.int32)], 1)
            m = m.astype(bool)
            area = m.sum()
            if area:
                own = (m & ((obs == cls) | hidden)).sum()
                other = (m & body & (obs != cls)).sum()
                seek_num += own + SEEK_OVERLAP * other
                seek_den += area
                covered |= m & (obs == cls)
            centre = hull.mean(0)
            for a_, b_ in zip(hull, np.roll(hull, -1, 0)):
                if np.hypot(*(b_ - a_)) >= 2:
                    outlines.append((cls, a_, b_, centre))
            # End caps, both ends of every box (hand/foot end and shoulder/hip end).
            hidx = cv2.convexHull(pts.astype(np.float32), returnPoints=False).reshape(-1) if EXPERIMENTAL else []
            for ci, cap in enumerate((CAP_FAR, CAP_NEAR) if EXPERIMENTAL else ()):
                for ia, ib in zip(hidx, np.roll(hidx, -1)):
                    if ia in cap and ib in cap and np.hypot(*(pts[ib] - pts[ia])) >= 2:
                        ends.append((j, ci, cls, pts[ia], pts[ib], centre))
        # The steadfast rule: every straight edge with a body colour along it must be covered by the outline of a box
        # of that colour (either side's colour, where two parts meet): lying along it (within 1.5 px, within 12
        # degrees of its direction). Scored as the covered share of all such edges' length.
        e_cov = e_len = 0.0
        for seg, cls_sides, nrm in view.coloured_edges[i]:
            if only is not None and not (set(cls_sides) & {c for _, c in parts}):
                continue
            x1, y1, x2, y2 = seg
            L = float(np.hypot(x2 - x1, y2 - y1))
            n = max(2, int(L))
            t = (np.arange(n) + 0.5) / n
            P = np.stack([x1 + (x2 - x1) * t, y1 + (y2 - y1) * t], 1)
            th = math.atan2(y2 - y1, x2 - x1)
            hit = np.zeros(n, bool)
            mid = P[len(P) // 2]
            for cls, a_, b_, centre in outlines:
                # A box of the edge's colour, whose body lies on that colour's side of the edge.
                if cls not in cls_sides or np.sign((centre - mid) @ nrm) != cls_sides[cls]:
                    continue
                e = b_ - a_
                el = float(np.hypot(*e))
                d_ang = abs((math.atan2(e[1], e[0]) - th + math.pi / 2) % math.pi - math.pi / 2)
                if d_ang > math.radians(12):
                    continue
                u = np.clip(((P - a_) @ e) / (el * el), 0, 1)
                d = np.hypot(*(P - (a_ + u[:, None] * e)).T)
                hit |= d <= SIDE_TOL * unit_px
            e_cov += L * hit.mean()
            e_len += L
        seek = seek_num / max(1.0, seek_den)
        cover = (covered & known).sum() / max(1, (body & known).sum())
        colour = 0.5 * (seek + cover)
        edge = e_cov / e_len if e_len else 1.0
        # The end rule: every box end cap's outline edge must meet a straight edge of its colour, flush (within 1.5 px
        # and 8 degrees), the box on the colour's side -- this pins each box's turn about its own axis. Ends lying
        # where something hides the body are not judged.
        per_cap = {}  # (box, cap) -> [covered length, length]
        segs_c = view.coloured_edges[i]
        for j, ci, cls, a_, b_, centre in ends:
            L = float(np.hypot(*(b_ - a_)))
            n = max(2, int(L))
            t = (np.arange(n) + 0.5) / n
            P = a_ + (b_ - a_) * t[:, None]
            xi = np.clip(P[:, 0].astype(int), 0, side - 1)
            yi = np.clip(P[:, 1].astype(int), 0, side - 1)
            if hidden[yi, xi].mean() > 0.5:
                continue
            th = math.atan2(*(b_ - a_)[::-1])
            hit = np.zeros(n, bool)
            for seg, cls_sides, nrm in segs_c:
                if cls not in cls_sides:
                    continue
                x1, y1, x2, y2 = seg
                e = np.array([x2 - x1, y2 - y1])
                d_ang = abs((math.atan2(e[1], e[0]) - th + math.pi / 2) % math.pi - math.pi / 2)
                if d_ang > math.radians(END_ANGLE):
                    continue
                mid = np.array([(x1 + x2) / 2, (y1 + y2) / 2])
                if np.sign((centre - mid) @ nrm) != cls_sides[cls]:
                    continue
                el2 = float(e @ e)
                u = np.clip(((P - (x1, y1)) @ e) / el2, 0, 1)
                d = np.hypot(*(P - (np.array([x1, y1]) + u[:, None] * e)).T)
                hit |= d <= END_TOL * unit_px
            acc = per_cap.setdefault((j, ci), [0.0, 0.0])
            acc[0] += L * hit.mean()
            acc[1] += L
        # A limb is judged by whichever of its ends meets its edges best (one end is often hidden or blurred); the
        # torso by both (shoulder line and hip line).
        n_cov = n_len = 0.0
        for j in {jj for jj, _ in per_cap}:
            capsj = [per_cap[(j, c)] for c in (0, 1) if (j, c) in per_cap]
            if F.PARTS[j][3] == 'torso':
                n_cov += sum(c[0] for c in capsj)
                n_len += sum(c[1] for c in capsj)
            else:
                best_c = max(capsj, key=lambda c: c[0] / max(1e-6, c[1]))
                n_cov += best_c[0]
                n_len += best_c[1]
        endr = n_cov / n_len if n_len else 1.0
        # The hard rule: every limb that shows has its hand end or its shoulder end -- either, never neither -- flush on
        # an edge of its colour (60% of that end's outline covered).
        # How many limbs of each colour must keep it comes from the footage (the limb rectangles found of that colour),
        # not from where the boxes are -- so moving a box off its colour cannot dodge the rule.
        broken = 0
        limbs_of = {}
        for j, (_, _, _, part) in enumerate(F.PARTS):
            if part not in ('torso', 'head', 'face'):
                limbs_of.setdefault(F.CLASS_OF[part], []).append(j)
        rects_i = view.rects[i][0]
        for cls_, js in limbs_of.items():
            limb_rects = [r for r in rects_i.get(cls_, []) if RR.aspect(r) >= 1.4]
            required = min(len(js), len(limb_rects))
            flush = 0
            for j in js:
                capsj = [per_cap[(j, c)] for c in (0, 1) if (j, c) in per_cap]
                if any(c[0] / max(1e-6, c[1]) >= 0.6 for c in capsj):
                    flush += 1
            broken += max(0, required - flush)
        # Side edges first (5), then the ends (1, they mostly settle each box's turn) and colour (1).
        if EXPERIMENTAL:
            total = (5 * edge + endr + colour) / 7 - (broken if END_RULE else 0)  # breaking it loses to keeping it
        else:
            total = (4 * colour + edge) / 5  # the default: colour leads (4), coloured side edges break ties (1)
        out.append((total, colour, edge, endr, broken) if detail else total)
    return out if detail else torch.tensor(out)


def polish(fit, v, k, steps, sigma0=1.5, sigma1=0.4, every=10):
    """Gradient steps (soft render) that keep the state whose hard colour agreement was best, not the last one."""
    view = fit.views[v]
    i = view.idx[k]
    groups = [{'params': [fit.cams[v]], 'lr': 0.08}]
    if v == 0:
        groups.append({'params': [fit.J], 'lr': 0.8})
    opt = torch.optim.Adam(groups)
    keep_J, keep_C = fit.J.detach().clone(), fit.cams[v].detach().clone()
    cam_now = lambda: (fit.cams[v][[k]] * CAM_UNIT).detach()
    best = (float(judge(fit, v, i, fit.joints_at(v, k).detach()[None], cam_now())[0]),
            fit.J[k].detach().clone(), fit.cams[v][k].detach().clone())
    for s in range(steps):
        sig = sigma0 * (sigma1 / sigma0) ** (s / max(1, steps - 1))
        opt.zero_grad()
        coarse = s < steps - 20  # all but the last 20 steps on every other pixel
        loss = fit.loss_frames(v, [k], sig, stride=2 if coarse else 1)[0] + fit.regular() * 0.1
        loss.backward()
        opt.step()
        if s % every == every - 1 or s == steps - 1:
            a = float(judge(fit, v, i, fit.joints_at(v, k).detach()[None], cam_now())[0])
            if a > best[0]:
                best = (a, fit.J[k].detach().clone(), fit.cams[v][k].detach().clone())
    with torch.no_grad():
        # Only frame k was being fitted: every other frame goes back exactly as it was (the smoothness term and Adam's
        # momentum would otherwise drag frames already fitted).
        fit.J.copy_(keep_J)
        fit.cams[v].copy_(keep_C)
        if v == 0:
            fit.J[k] = best[1]
        fit.cams[v][k] = best[2]
    return best[0]


LIMBS = {'rightArm': 'rightArm', 'leftArm': 'leftArm', 'rightLeg': 'rightThigh', 'leftLeg': 'leftThigh'}


def lift_limbs(fit, v, k, steps=150):
    """Each limb straight from its rectangle: the rectangle's long axis is the limb's direction on screen and its
    length against the limb's true length (12 px) says how far the limb points toward or away from the camera; the
    joint end is the one nearer the limb's shoulder/hip. Both depth signs of every limb are tried together and the
    combination that matches the footage best is kept, then polished by gradient. Returns (loss after, before)."""
    import itertools
    view = fit.views[v]
    i = view.idx[k]
    if i not in view.targets:
        fit.assign(v, k)
    rects, obs = view.rects[i]
    pix, _, _, scale = view.windows[i]
    side = view.res
    x0, y0 = float(pix[0, 0]) - 0.5 * scale, float(pix[0, 1]) - 0.5 * scale
    with torch.no_grad():
        J = fit.joints_at(v, k).clone()
        cam = (fit.cams[v][k] * CAM_UNIT).clone()
        # Where the limbs' joints are now (footage px) and the parents' rotations, from the current body.
        ang = (J[None] @ JMAP).reshape(1, len(BONES), 3)
        R = rotm(ang)[0]
        wr, wt = [None] * len(BONES), [None] * len(BONES)
        for b, par in enumerate(PARENT_IDX):
            wr[b] = R[b] if par < 0 else wr[par] @ R[b]
            wt[b] = OFFSET[b] if par < 0 else wt[par] + wr[par] @ OFFSET[b]
        zeros = torch.zeros(())
        view_r = rotm(torch.stack([cam[1], zeros, zeros])) @ rotm(torch.stack([zeros, cam[0], zeros]))
    options = []
    for part, bone in LIMBS.items():
        j = next(jj for jj, pp in enumerate(F.PARTS) if pp[3] == part)
        tgt, has = view.targets[i]
        if not has[j]:
            options.append([None])
            continue
        # The rectangle this limb was given (the one whose pixels its target is).
        t = tgt[j].reshape(side, side).numpy() > 0.5
        best_r = max((r for rs in rects.values() for r in rs), key=lambda r: (r.mask((side, side)) & t).sum())
        c = best_r.corners
        e = [np.linalg.norm(c[1] - c[0]), np.linalg.norm(c[2] - c[1])]
        if e[0] >= e[1]:
            ends = ((c[0] + c[3]) / 2, (c[1] + c[2]) / 2)
        else:
            ends = ((c[0] + c[1]) / 2, (c[2] + c[3]) / 2)
        ends = [np.array([x0 + q[0] * scale, y0 + q[1] * scale]) for q in ends]  # footage px
        b = BONES.index(bone)
        jw = (wt[b] * M2W)
        jc = view_r @ jw
        joint2d = np.array([float(cam[3] + cam[2] * jc[0]), float(cam[4] - cam[2] * jc[1])])
        start, end = sorted(ends, key=lambda q: np.linalg.norm(q - joint2d))
        d2 = (end - start) / float(cam[2])  # rig px on the camera plane
        length = 12.0
        planar = min(np.linalg.norm(d2), length)
        dz = math.sqrt(max(0.0, length ** 2 - planar ** 2))
        par = PARENT_IDX[b]
        outs = []
        for sgn in (1, -1):
            dc = torch.tensor([d2[0], -d2[1], sgn * dz], dtype=DT)
            dc = dc / dc.norm()
            dw = view_r.T @ dc  # world
            dm = dw * M2W  # model (y down)
            a = wr[par].T @ dm  # in the parent's frame
            x = math.degrees(math.asin(float(torch.clamp(a[2], -1, 1))))
            z = math.degrees(math.atan2(-float(a[0]), float(a[1])))
            outs.append((F.JI[(bone, 0)] - NC, F.JI[(bone, 2)] - NC, x, z))
        options.append(outs)
    combos = []
    for pick in itertools.product(*options):
        q = J.clone()
        for o in pick:
            if o is not None:
                q[o[0]], q[o[1]] = o[2], o[3]
        combos.append(q)
    combos.append(J.clone())
    # The rule: of all the depth combinations (and the pose as it was), the one whose colours disagree least.
    Jc = torch.stack(combos)
    agree = agreement(fit, v, i, Jc, cam.expand(len(Jc), 5))
    before = float(agree[-1])
    with torch.no_grad():
        if v == 0:
            fit.J[k] = Jc[int(torch.argmax(agree))]
    after = polish(fit, v, k, steps)
    return after, before


def _axis_ends(r, x0, y0, scale):
    """A rectangle's long axis as its two end midpoints, in footage px."""
    c = r.corners
    if np.linalg.norm(c[1] - c[0]) >= np.linalg.norm(c[2] - c[1]):
        ends = ((c[0] + c[3]) / 2, (c[1] + c[2]) / 2)
    else:
        ends = ((c[0] + c[1]) / 2, (c[2] + c[3]) / 2)
    return [np.array([x0 + q[0] * scale, y0 + q[1] * scale]) for q in ends]


def _body_frames(J, cam):
    """World rotations/positions of every bone (model space) and the camera rotation, for one pose."""
    ang = (J[None] @ JMAP).reshape(1, len(BONES), 3)
    R = rotm(ang)[0]
    wr, wt = [None] * len(BONES), [None] * len(BONES)
    for b, par in enumerate(PARENT_IDX):
        wr[b] = R[b] if par < 0 else wr[par] @ R[b]
        wt[b] = OFFSET[b] if par < 0 else wt[par] + wr[par] @ OFFSET[b]
    zeros = torch.zeros(())
    view_r = rotm(torch.stack([cam[1], zeros, zeros])) @ rotm(torch.stack([zeros, cam[0], zeros]))
    return wr, wt, view_r


def _to_screen(cam, view_r, p_model):
    c = view_r @ (p_model * M2W)
    return np.array([float(cam[3] + cam[2] * c[0]), float(cam[4] - cam[2] * c[1])])


def _dir_model(view_r, d2, length, sgn, cam):
    """A screen direction (footage px) of a part `length` rig px long, toward (+1) or away from (-1) the camera, as
    a unit direction in model space."""
    d = d2 / float(cam[2])
    planar = min(np.linalg.norm(d), length)
    dz = math.sqrt(max(0.0, length ** 2 - planar ** 2))
    dc = torch.tensor([d[0], -d[1], sgn * dz], dtype=DT)
    return (view_r.T @ (dc / dc.norm())) * M2W


def _euler_zyx(M):
    """Degrees (x, y, z) with Rz Ry Rx = M."""
    y = math.asin(max(-1.0, min(1.0, -float(M[2, 0]))))
    x = math.atan2(float(M[2, 1]), float(M[2, 2]))
    z = math.atan2(float(M[1, 0]), float(M[0, 0]))
    return math.degrees(x), math.degrees(y), math.degrees(z)


TWISTS = (-45, -27, -9, 9, 27, 45, 0)  # a box's square end looks the same every 90 degrees: this covers every turn


def twisted(J, bone, xd, zd, twists=TWISTS):
    """Copies of J with the limb pointing along (x, z) (no twist) and turned about its own long axis by each twist:
    R(x, 0, z) Ry(t) re-expressed in the rig's Euler order, so the limb's direction stays put while its faces turn."""
    out = []
    base = rotm(torch.tensor([xd, 0.0, zd], dtype=DT))
    for t in twists:
        M = base @ rotm(torch.tensor([0.0, float(t), 0.0], dtype=DT))
        x, y, z = _euler_zyx(M)
        q = J.clone()
        q[F.JI[(bone, 0)] - NC], q[F.JI[(bone, 1)] - NC], q[F.JI[(bone, 2)] - NC] = x, y, z
        out.append(q)
    return out


def twist_sweep(fit, v, k, cam, J, score):
    """Every limb (and the torso) turned about its own long axis in steps, keeping the turn the judge likes best."""
    for bone in ('rightArm', 'leftArm', 'rightThigh', 'leftThigh', 'hips'):
        x0_, y0_, z0_ = (float(J[F.JI[(bone, a)] - NC]) for a in range(3))
        base = rotm(torch.tensor([x0_, y0_, z0_], dtype=DT))
        opts = [J.clone()]
        for t in range(-45, 46, 9):
            if t == 0:
                continue
            x, y, z = _euler_zyx(base @ rotm(torch.tensor([0.0, float(t), 0.0], dtype=DT)))
            q = J.clone()
            q[F.JI[(bone, 0)] - NC], q[F.JI[(bone, 1)] - NC], q[F.JI[(bone, 2)] - NC] = x, y, z
            opts.append(q)
        J = opts[int(torch.argmax(score(torch.stack(opts), cam)))]
    return J


def lift_all(fit, v, k, turns=8, rounds=2, steps=150):
    """The whole body from the rectangles, every candidate held to judge(): the torso from its rectangle (either end
    up, tilted toward or away, any turn about its long axis, the body slid so the torso box sits on the rectangle), and
    for each torso every limb from every rectangle of its colour (either end at the joint, toward or away), limbs chosen
    in rounds; the best whole pose is then polished. Returns (judged after, before)."""
    view = fit.views[v]
    i = view.idx[k]
    rects, _ = view.rects[i]
    pix, _, _, scale = view.windows[i]
    x0, y0 = float(pix[0, 0]) - 0.5 * scale, float(pix[0, 1]) - 0.5 * scale
    J0 = fit.joints_at(v, k).detach().clone()
    cam0 = (fit.cams[v][k] * CAM_UNIT).detach().clone()
    score = lambda Js, c: judge(fit, v, i, Js, c.expand(len(Js), 5))
    before = float(score(J0[None], cam0)[0])
    hips = [F.JI[('hips', a)] - NC for a in range(3)]
    hb = BONES.index('hips')
    torsos = [(J0.clone(), cam0.clone())]
    _, _, view_r = _body_frames(J0, cam0)
    # The scale from the limbs' widths: a limb is one stud (6 rig px) wide whichever way it turns, so the short side
    # of its rectangle measures the scale (the median over limbs; 5% either way also tried).
    widths = [min(r.box[1]) * scale / 6 for cls, rs in rects.items() if cls != F.CLASS_OF['torso'] for r in rs]
    scales = [float(cam0[2])]
    if widths:
        w_med = float(np.median(widths))
        scales += [w_med * f for f in (0.95, 1.0, 1.05)]
    for r in rects.get(F.CLASS_OF['torso'], [])[:1]:
        ends = _axis_ends(r, x0, y0, scale)
        centre = (ends[0] + ends[1]) / 2
        # Up candidates: the rectangle's long axis either way (tilted toward or away), and -- since a torso is as wide
        # as it is tall, so a 2:1 slab may be its shoulders seen from above -- directions all round the sphere.
        ups = []
        for a_, b_ in (ends, ends[::-1]):
            for sgn in (1, -1):
                ups.append(_dir_model(view_r, b_ - a_, 12.0, sgn, cam0))
        golden = math.pi * (3 - math.sqrt(5))
        for n_ in range(40):
            yy = 1 - 2 * (n_ + 0.5) / 40
            rr = math.sqrt(1 - yy * yy)
            ups.append(torch.tensor([rr * math.cos(golden * n_), yy, rr * math.sin(golden * n_)], dtype=DT))
        for up in ups:
            if True:
                ydir = -up / up.norm()  # the bone's +y (model down) points away from the head
                helper = torch.tensor([0.0, 0.0, 1.0]) if abs(float(ydir[2])) < 0.9 else torch.tensor([1.0, 0.0, 0.0])
                f0 = helper - ydir * (helper @ ydir)
                f0 = f0 / f0.norm()
                g0 = torch.linalg.cross(ydir, f0)
                for t in range(turns):
                    th = 2 * math.pi * t / turns
                    zdir = f0 * math.cos(th) + g0 * math.sin(th)
                    M = torch.stack([torch.linalg.cross(ydir, zdir), ydir, zdir], 1)
                    q = J0.clone()
                    q[hips[0]], q[hips[1]], q[hips[2]] = _euler_zyx(M)
                    for sc_ in scales:
                        c2 = cam0.clone()
                        c2[2] = sc_
                        wr, wt, vr = _body_frames(q, c2)
                        mid = _to_screen(c2, vr, wt[hb] + wr[hb] @ torch.tensor([0.0, -6.0, 0.0]))
                        c2[3] += float(centre[0] - mid[0])
                        c2[4] += float(centre[1] - mid[1])
                        torsos.append((q, c2))
    # Rank the torsos by the torso box alone (its colour and its edges); carry the best few through the limbs.
    t_scores = judge(fit, v, i, torch.stack([q for q, _ in torsos[1:]]), torch.stack([c for _, c in torsos[1:]]),
                     only=('torso',)) if len(torsos) > 1 else torch.zeros(0)
    ranked = [torsos[0]] + [torsos[1 + int(n)] for n in torch.argsort(-t_scores)[:8]]
    torso_score = lambda tc: float(judge(fit, v, i, tc[0][None], tc[1][None], only=('torso',))[0])
    results = []
    for Jt, cam in ranked:
        J = Jt.clone()
        for _ in range(rounds):
            for part, bone in LIMBS.items():
                b = BONES.index(bone)
                ix, iz = F.JI[(bone, 0)] - NC, F.JI[(bone, 2)] - NC
                wr, wt, view_r = _body_frames(J, cam)
                joint2d = _to_screen(cam, view_r, wt[b])
                par = PARENT_IDX[b]
                opts = [J.clone()]
                for r in rects.get(F.CLASS_OF[part], []):
                    ends = _axis_ends(r, x0, y0, scale)
                    for st, en in (ends, ends[::-1]):
                        if np.linalg.norm(st - joint2d) > 2.5 * np.linalg.norm(en - st) + 6 * float(cam[2]):
                            continue
                        for sgn in (1, -1):
                            a = wr[par].T @ _dir_model(view_r, en - st, 12.0, sgn, cam)
                            xd = math.degrees(math.asin(float(torch.clamp(a[2], -1, 1))))
                            zd = math.degrees(math.atan2(-float(a[0]), float(a[1])))
                            opts += twisted(J, bone, xd, zd, TWISTS if EXPERIMENTAL else (0,))
                # Generic directions too: a limb with no rectangle of its own (hidden) settles where it disagrees least.
                sgn_ = 1 if bone.startswith('right') else -1
                for gx in (-150, -90, -45, 0, 30):
                    for gz in (0, 50, 100):
                        q = J.clone()
                        q[ix], q[iz] = gx, sgn_ * gz
                        q[F.JI[(bone, 1)] - NC] = 0.0
                        opts.append(q)
                J = opts[int(torch.argmax(score(torch.stack(opts), cam)))]
        results.append((float(score(J[None], cam)[0]), J, cam))
    results.sort(key=lambda e: -e[0])
    # The winner's boxes each turned about their own axis until their ends meet their edges.
    if EXPERIMENTAL:
        best_J = twist_sweep(fit, v, k, results[0][2], results[0][1], score)
        results[0] = (float(score(best_J[None], results[0][2])[0]), best_J, results[0][2])
    if os.environ.get('R6_DEBUG'):
        print('torso candidates', len(torsos), 'top torso-only', [round(torso_score(t), 3) for t in ranked[:4]],
              'whole poses', [round(r[0], 3) for r in results[:5]])
    with torch.no_grad():
        if v == 0:
            fit.J[k] = results[0][1]
        fit.cams[v][k] = results[0][2] / CAM_UNIT
    after = polish(fit, v, k, steps)
    return max(after, results[0][0]), before


def global_search(fit, v, k, n=1500, top=6, steps=120, seed=0):
    """Whole-body search for one frame: n random poses (any body turn and lean, every limb anywhere in its range, the
    camera a little either way) ranked on a coarse window, the best few refined by gradient; the winner is kept
    if it beats the current pose. For frames where parts must move together (a body turned side-on)."""
    view = fit.views[v]
    i = view.idx[k]
    if i not in view.targets:
        fit.assign(v, k)
    g = torch.Generator().manual_seed(seed)
    with torch.no_grad():
        J = LO + (HI - LO) * torch.rand(n, NJ, generator=g)
        for (b, ax), jj in ((j, F.JI[j] - NC) for j in F.JOINTS):
            if ax == 1 and b != 'hips':
                J[:, jj] = J[:, jj] * 0.4
        J[:, F.JI[('hips', 1)] - NC] = fit.J[k, F.JI[('hips', 1)] - NC] + torch.rand(n, generator=g) * 360 - 180
        J[0] = fit.J[k].detach()
        cam = (fit.cams[v][k] * CAM_UNIT).detach().expand(n, 5).clone()
        cam[1:, 0] += torch.randn(n - 1, generator=g) * 15
        cam[1:, 1] += torch.randn(n - 1, generator=g) * 10
        losses = torch.cat([window_loss(fit, v, i, J[c:c + 16], cam[c:c + 16], 2.0, stride=2) for c in range(0, n, 16)])
        best = torch.argsort(losses)[:top]
        current = float(window_loss(fit, v, i, fit.J[k:k + 1].detach(), cam[:1], 0.7)[0])
    winner = None
    for b in best.tolist():
        Jb = J[b:b + 1].clone().requires_grad_()
        cb = (cam[b:b + 1] / CAM_UNIT).clone().requires_grad_()
        opt = torch.optim.Adam([{'params': [Jb], 'lr': 1.5}, {'params': [cb], 'lr': 0.15}])
        for s in range(steps):
            sig = 4.0 * (0.7 / 4.0) ** (s / max(1, steps - 1))
            opt.zero_grad()
            loss = window_loss(fit, v, i, Jb, cb * CAM_UNIT, sig)[0]
            loss = loss + (torch.relu(Jb - HI) ** 2 + torch.relu(LO - Jb) ** 2).sum() * 0.01
            loss.backward()
            opt.step()
        if winner is None or float(loss) < winner[0]:
            winner = (float(loss), Jb.detach()[0], cb.detach()[0])
    if winner[0] < current:
        with torch.no_grad():
            if v == 0:
                fit.J[k] = winner[1]
            fit.cams[v][k] = winner[2]
        return winner[0], current
    return current, current


def adam(params_groups, steps, closure, sigma0, sigma1):
    opt = torch.optim.Adam(params_groups)
    for s in range(steps):
        sig = sigma0 * (sigma1 / sigma0) ** (s / max(1, steps - 1))
        opt.zero_grad()
        loss = closure(sig)
        loss.backward()
        opt.step()
    return float(loss)


def warm_start(fit, v, fitter, log):
    """Frame by frame: start from the last frame, keep the best of a spread of limb directions, then descend."""
    view = fit.views[v]
    last_loss = float('inf')
    for k in range(len(view.idx)):
        i = view.idx[k]
        with torch.no_grad():
            if k > 0:
                if v == 0:
                    fit.J[k] = fit.J[k - 1]
                fit.cams[v][k] = fit.cams[v][k - 1]
        view.window(i, fit.params_of(v, k), fitter)
        if v == 0 and k > 0:
            with torch.no_grad():
                cands = limb_candidates(fit.J[k])
                cam = (fit.cams[v][k] * CAM_UNIT).expand(len(cands), 5)
                quads, depth, vis, shade = geometry(cands, cam, fit.persp[v], fit.lights[v])
                w = view.windows[i]
                losses = []
                for c0 in range(0, len(cands), 8):
                    sl = slice(c0, c0 + 8)
                    n = quads[sl].shape[0]
                    prob, sh = rasterise(quads[sl], depth[sl], vis[sl], shade[sl], w[0].expand(n, -1, -1), 1.5 * w[3],
                                         fit.classes, fit.ncls)
                    losses.append(frame_loss(prob, sh, w[1].expand(n, -1), w[2].expand(n, -1), fit.ncls, view.res))
                fit.J[k] = cands[int(torch.argmin(torch.cat(losses)))]
        prevJ = fit.J[k - 1].detach().clone() if k > 0 else None
        prevC = fit.cams[v][k - 1].detach().clone() if k > 0 else None

        def closure(sig):
            loss = fit.loss_frames(v, [k], sig)[0]
            if prevJ is not None:
                if v == 0:
                    loss = loss + ((fit.J[k] - prevJ) ** 2).sum() * 2e-5
                d = (fit.cams[v][k] - prevC) * CAM_UNIT
                loss = loss + (d[:2] ** 2).sum() * 0.02 + d[2] ** 2 * 20
            return loss
        groups = [{'params': [fit.cams[v]], 'lr': 0.15}]
        if v == 0:
            groups.append({'params': [fit.J], 'lr': 1.5})
        loss = adam(groups, 60, closure, 4.0, 0.7)
        if v == 0 and (k == 0 or loss > 1.25 * last_loss):
            after, before = global_search(fit, v, k, seed=k)
            if after < before:
                log(f'view {v + 1} frame {i}: whole-body search {before:.3f} -> {after:.3f}')
                loss = after
        last_loss = loss
        log(f'view {v + 1} frame {i}: {loss:.3f}')


def joint_refine(fit, steps, fitters, log, batch=6):
    """All frames of all views together, a few frames per step (windows re-centred once halfway)."""
    rng = np.random.default_rng(0)
    groups = [{'params': [fit.J], 'lr': 0.6}, {'params': fit.cams, 'lr': 0.08}]
    opt = torch.optim.Adam(groups)
    for s in range(steps):
        if s == steps // 2:
            for v, view in enumerate(fit.views):
                for k, i in enumerate(view.idx):
                    view.window(i, fit.params_of(v, k), fitters[v])
        sig = 3.0 * (0.5 / 3.0) ** (s / max(1, steps - 1))
        opt.zero_grad()
        loss = fit.regular()
        for v, view in enumerate(fit.views):
            ks = sorted(rng.choice(len(view.idx), size=min(batch, len(view.idx)), replace=False).tolist())
            loss = loss + fit.loss_frames(v, ks, sig).mean() * len(view.idx) / len(ks) / len(fit.views)
        loss.backward()
        opt.step()
        if s % 50 == 0 or s == steps - 1:
            log(f'refine {s}: {float(loss):.3f}')


def init_camera_for(fitter, enc, joints, flip=False, keep_scale=False):
    """A take's first camera with the pose already known (view 2): search the camera only. keep_scale: the size held
    near the character's measured height (a pose that does not match exactly would otherwise shrink the body to
    nothing). Returns the parameters, or None when no patch shows most of the body's colours."""
    fg = enc >= 1000
    lab, n = F.ndimage.label(F.ndimage.binary_dilation(fg, iterations=4))
    # The character: the patch with the most different body colours (a yellow wall or a tree trunk shows one), the
    # larger on a tie.
    cl = enc // 1000
    score = [colour_score(cl[(lab == c) & fg]) for c in range(1, n + 1)]
    if not n:
        return None
    pick = 1 + max(range(n), key=lambda c: score[c])
    if keep_scale and score[pick - 1][0] < 4:
        return None
    cy, cx = F.ndimage.center_of_mass(lab == pick)
    ys = np.nonzero(lab == pick)[0]
    best = None
    for cyaw in range(0, 360, 20):
        for cpitch in range(0, 90, 15):
            p = np.zeros(F.NP)
            p[:6] = cyaw + (180 if flip else 0), cpitch, max(10, np.ptp(ys)) / 31, cx, cy, 0.005
            p[NC:] = joints
            roi = fitter.roi(p, enc)
            c = fitter.cost(p, fitter.prepare(fitter.view(enc, roi)), roi, None)
            if best is None or c < best[0]:
                best = (c, p)
    sig = list(fitter.init_sigma[:NC]) + [0] * NJ
    if keep_scale:
        sig[2] = 0.03 * best[1][2]
    p = fitter.cem(best[1], sig, enc, None, iters=30, pop=48)
    fitter.fit_light(p, enc)
    return p


M2W_MAT = np.diag([-1.0, -1.0, 1.0])
# The torso turned to fit its own corners (tried beside the body as placed, the better kept). On the wiki GIFs it never
# won -- the torso's real corners are mostly hidden by the dummy and the arms -- so it is off: R6_TORSO_CORNERS=1 on.
TORSO_CORNERS = os.environ.get('R6_TORSO_CORNERS', '0') == '1'
BOX_DIMS = {'torso': (12, 12, 6), 'head': (7.2, 7, 7.2)}  # rig px along the box's own x, y, z; limbs are (6, 12, 6)


def rotations_from_L(a2, b2, s, dims, slack=0.15, axes=False):
    """Every turn of a box that shows two of its edges, meeting at a corner, as the screen vectors a2 and b2 (footage
    px): each is one of the box's three axes (any sign) at its real length (dims, rig px). On screen an edge keeps its
    sideways extent and loses depth, so its tilt out of the picture follows from its real length; the two edges being
    at right angles fixes how the tilts pair up. Returns camera-space rotations (columns = the box's x, y, z axes)."""
    A = np.array([a2[0], -a2[1]]) / s  # camera plane (y up), rig px
    B = np.array([b2[0], -b2[1]]) / s
    na, nb = np.linalg.norm(A), np.linalg.norm(B)
    out = []
    for i in range(3):
        for j in range(3):
            if i == j:
                continue
            La, Lb = dims[i], dims[j]
            if na > La * (1 + slack) or nb > Lb * (1 + slack) or na < 0.25 * La or nb < 0.25 * Lb:
                continue
            za0 = math.sqrt(max(0.0, La * La - na * na))
            zb0 = math.sqrt(max(0.0, Lb * Lb - nb * nb))
            for sa in (1, -1):
                for sb in (1, -1):
                    za, zb = sa * za0, sb * zb0
                    if abs(A @ B + za * zb) > 0.25 * La * Lb:
                        continue  # not at right angles
                    a3 = np.array([A[0], A[1], za])
                    b3 = np.array([B[0], B[1], zb])
                    au = a3 / max(1e-9, np.linalg.norm(a3))
                    bu = b3 - (b3 @ au) * au
                    bu = bu / max(1e-9, np.linalg.norm(bu))
                    for sga in (1, -1):
                        for sgb in (1, -1):
                            cols = [None] * 3
                            cols[i], cols[j] = sga * au, sgb * bu
                            k = 3 - i - j
                            cols[k] = np.cross(cols[(k + 1) % 3], cols[(k + 2) % 3])
                            out.append((np.stack(cols, 1), i, j, k) if axes else np.stack(cols, 1))
    return out


def sketch_boxes(view, i, cam_scale, classes=None):
    """Reference boxes sketched from the corners (no pose involved): each corner's two edges, at their measured
    lengths, read as two sides of a box of its part's real size (red: torso; yellow: arm or head; lime: leg); every
    turn that fits (rotations_from_L) is completed into the whole box (8 corners, the third side running either way)
    anchored on the corner, and scored by how much of its outline area is its colour (any shade) less the floor it
    covers. The best box per corner is kept, then among boxes overlapping by more than half only the best.
    Returns [(score, colour, part kind, 8 window-px points, corner point)]."""
    obs = view.shadowed[i]
    pix, _, _, scale = view.windows[i]
    sc = float(scale)
    side = obs.shape[0]
    s_win = cam_scale / sc  # window px per rig px
    unit = 6 * s_win
    kinds = {F.CLASS_OF['torso']: [('torso', (12, 12, 6))]}
    for part in ('rightArm', 'rightLeg'):
        kinds.setdefault(F.CLASS_OF[part], []).append(('limb', (6, 12, 6)))
    kinds.setdefault(F.CLASS_OF['head'], []).append(('head', (7.2, 7, 7.2)))
    floor = obs == 0
    best_per_corner = []
    for cpt, d1, d2, cls, ang in view.corners.get(i, []):
        if classes is not None and cls not in classes:
            continue
        l1 = edge_reach(obs, cpt, d1, cls, unit)
        l2 = edge_reach(obs, cpt, d2, cls, unit)
        if min(l1, l2) < 0.3 * unit:
            continue
        colour = obs == cls
        best = None
        for kind, dims in kinds.get(cls, []):
            for R, ai, bj, ck in rotations_from_L(d1 * l1, d2 * l2, s_win, dims, axes=True):
                for third in (1, -1):
                    # The corner's two edges run along axes ai and bj away from it; the third side either way.
                    pts = []
                    for ta in (0, 1):
                        for tb in (0, 1):
                            for tc in (0, 1):
                                vec = ta * dims[ai] * R[:, ai] + tb * dims[bj] * R[:, bj] + tc * third * dims[ck] * R[:, ck]
                                pts.append(cpt + np.array([vec[0], -vec[1]]) * s_win)
                    pts = np.array(pts)
                    m_ = np.zeros((side, side), np.uint8)
                    cv2.fillConvexPoly(m_, np.round(cv2.convexHull(pts.astype(np.float32)).reshape(-1, 2)).astype(np.int32), 1)
                    mb = m_.astype(bool)
                    area = mb.sum()
                    if area < 0.5 * unit * unit:
                        continue
                    score = ((mb & colour).sum() - (mb & floor).sum()) / area
                    if best is None or score > best[0]:
                        best = (score, cls, kind, pts, cpt, mb)
        if best is not None:
            best_per_corner.append(best)
    best_per_corner.sort(key=lambda e: -e[0])
    kept = []
    for cand in best_per_corner:
        if all((cand[5] & k[5]).sum() < 0.5 * min(cand[5].sum(), k[5].sum()) for k in kept):
            kept.append(cand)
    return [k[:5] for k in kept]


def outline_edges(mask, unit, eps=0.05, up=3):
    """A part's outline as straight edges, traced finely: the shape enlarged 3x and smoothed (so the pixel staircase
    makes no false angles), then simplified at 0.05 stud. [(start, end)] in window px."""
    if up == 1:
        big = mask.astype(np.uint8)
    else:
        big = cv2.resize(mask.astype(np.float32), None, fx=up, fy=up, interpolation=cv2.INTER_LINEAR)
        big = (cv2.GaussianBlur(big, (0, 0), up * 0.6) > 0.5).astype(np.uint8)
    contours, _ = cv2.findContours(big, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    out = []
    for cnt in contours:
        if cv2.contourArea(cnt) < 0.5 * (unit * up) ** 2:
            continue
        poly = cv2.approxPolyDP(cnt, eps * unit * up, True).reshape(-1, 2).astype(float) / up
        for a, b in zip(poly, np.roll(poly, -1, 0)):
            out.append((a, b))
    return out


def outline_directions(mask, unit, tol=6.0, min_len=0.12, eps=0.05, up=3):
    """The directions a part's outline runs in: its finely traced edges (outline_edges) of at least 0.12 stud grouped
    by direction (within 4 deg, either way along -- so a short end edge tilted a few degrees off the long sides, the end
    face seen at a slant, keeps its own direction), each group weighted by its total length.
    [(unit direction, total length, longest edge)], longest groups first."""
    edges = []
    for a, b in outline_edges(mask, unit, eps=eps, up=up):
        L = np.linalg.norm(b - a)
        if L >= min_len * unit:
            edges.append((math.degrees(math.atan2(b[1] - a[1], b[0] - a[0])) % 180, L))
    groups = []
    for ang, L in sorted(edges, key=lambda e: -e[1]):
        for g in groups:
            da = (ang - g[0] + 90) % 180 - 90
            if abs(da) <= tol:
                w = g[1] + L
                g[0] = (g[0] + da * L / w) % 180
                g[1] = w
                g[2] = max(g[2], L)
                break
        else:
            groups.append([ang, L, L])
    groups.sort(key=lambda g: -g[1])
    return [(np.array([math.cos(math.radians(g[0])), math.sin(math.radians(g[0]))]), g[1], g[2]) for g in groups]


def rotations_from_three(dirs_img):
    """Every turn of a box whose three axes run along the three screen directions dirs_img (image px, y down): with
    the axes at right angles in 3D, how much each is shortened on screen (lam_i) solves sum lam_i^2 u_i u_i^T = I,
    each axis's tilt out of the picture follows, and the right angles pair the tilts' signs (up to a mirror).
    Returns camera-space rotations (columns = the three axes in the given order) -- empty if no turn fits."""
    U = [np.array([d[0], -d[1]]) for d in dirs_img]  # camera plane, y up
    M = np.array([[u[0] * u[0] for u in U], [u[0] * u[1] for u in U], [u[1] * u[1] for u in U]])
    try:
        lam2 = np.linalg.solve(M, np.array([1.0, 0.0, 1.0]))
    except np.linalg.LinAlgError:
        return []
    if np.any(lam2 < -0.05) or np.any(lam2 > 1.05):
        return []
    lam2 = np.clip(lam2, 0, 1)
    lam = np.sqrt(lam2)
    z = np.sqrt(1 - lam2)
    out = []
    for s0 in (1, -1):
        zs = [s0 * z[0], None, None]
        for j in (1, 2):
            # e_0 . e_j = lam0 lamj (u0 . uj) + z0 zj = 0 fixes zj's sign against z0.
            want = -lam[0] * lam[j] * (U[0] @ U[j])
            zs[j] = z[j] if (zs[0] * z[j] - want) ** 2 <= (zs[0] * -z[j] - want) ** 2 else -z[j]
        cols = [np.array([lam[n] * U[n][0], lam[n] * U[n][1], zs[n]]) for n in range(3)]
        Rm = np.stack(cols, 1)
        uu, _, vv = np.linalg.svd(Rm)  # nearest true rotation
        Rm = uu @ vv
        if np.linalg.det(Rm) < 0:
            Rm[:, 2] *= -1
        out.append(Rm)
    return out


def direction_triples(mask, unit):
    """The direction readings of one part's outline to try as its box's axes: [[(unit dir, total len, longest edge)]
    x 3 (or 2, seen face-on)]. Coarse tracing of the raw pixels (steadier long sides) gives the first reading, its
    three strongest directions; fine tracing adds further third-direction options from short end edges (the end face
    seen at a slant), each read with the two long sides."""
    groups = outline_directions(mask, unit, tol=10.0, min_len=0.2, eps=0.12, up=1)
    if len(groups) < 2:
        return []
    fine = outline_directions(mask, unit)
    ang = lambda g, h: abs((math.degrees(math.atan2(g[0][1], g[0][0])) - math.degrees(math.atan2(h[0][1], h[0][0])) + 90) % 180 - 90)
    first = groups[0]
    second = next((g for g in groups[1:] if ang(g, first) >= 15), None)
    if second is None:
        return []
    thirds = [g for g in groups if g is not first and g is not second and ang(g, first) >= 10 and ang(g, second) >= 10][:2]
    thirds += [g for g in fine if ang(g, first) >= 5 and ang(g, second) >= 5
               and all(ang(g, t) >= 4 for t in thirds)][:3]
    return [groups[:3]] + [[first, second, g3] for g3 in thirds
                           if not (len(groups) >= 3 and g3 is groups[2] and second is groups[1])]


def _axis_turn(rx, ry, rz):
    """Small turn (degrees) about the camera's x, y, z axes."""
    a, b, c = (math.radians(t) for t in (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Y = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Z = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Z @ Y @ X


_BITS = [np.array(b) for b in __import__('itertools').product((0, 1), repeat=3)]


def _box_points(Rm, L, centre, unit):
    """The 8 window-px corners (indexed by bits (x, y, z)) of a box turned Rm (camera space, y up) with sides L (rig
    px), centred at centre."""
    ax = [np.array([Rm[0, n], -Rm[1, n]]) * L[n] * unit / 6 for n in range(3)]
    return np.array([centre + sum((b[n] - 0.5) * ax[n] for n in range(3)) for b in _BITS])


def box_from_outline(mask, unit, dims=(6, 12, 6), free=None, road=None):
    """Draws a part's box from its outline: every direction reading (direction_triples) solved into box turns
    (rotations_from_three, and the face-on reading of its two long sides), every way of laying the box's real sides
    along them, at 0.85 / 1 / 1.15 x the camera's size around the part's centre; the best few are then fine-tuned
    (position, size within 20% of the camera's, a few degrees of turn). Score: overlap with the part's pixels
    (intersection over union), where box area over `free` pixels (other body parts that may hide it) costs nothing and
    over other non-road background (dark clothes, another player, that may hide it too) half; any `road` inside the box
    (more than 2 px of it, the box's 1 px rim aside) fails the box outright. Real size is slightly preferred. Returns (iou, 8 window-px corners
    indexed by bits (x, y, z), [(corner a, corner b, visible)] for its 12 edges, rotation, sides) or None."""
    import itertools
    m = mask.astype(np.uint8)
    area = float(m.sum())
    if area < 4:
        return None
    cost = (1 - m) if free is None else ((1 - m) & (1 - free.astype(np.uint8)))
    road8 = None if road is None else road.astype(np.uint8)
    costw = cost.astype(np.float32) * (1.0 if road is None else 0.5)
    if road is not None:
        costw += (cost & road8) * 0.5
    inner = np.zeros_like(m)
    ys, xs = np.nonzero(m)
    c0 = np.array([xs.mean(), ys.mean()])
    canvas = np.zeros_like(m)

    def score(Rm, L, centre):
        pts = _box_points(Rm, L, centre, unit)
        canvas[:] = 0
        cv2.fillConvexPoly(canvas, np.round(cv2.convexHull(pts.astype(np.float32)).reshape(-1, 2)).astype(np.int32), 1)
        if road8 is not None:
            inner[:] = cv2.erode(canvas, np.ones((3, 3), np.uint8))
            if int((inner & road8).sum()) > 2:
                return -1.0, pts
        inter = float((canvas & m).sum())
        return inter / max(1.0, area + float(costw[canvas == 1].sum())), pts

    turns = []
    for trip in direction_triples(mask, unit):
        dirs = [g[0] for g in trip]
        if len(dirs) >= 3:
            turns += rotations_from_three(dirs)
        u0 = np.array([dirs[0][0], -dirs[0][1]])
        u1 = np.array([dirs[1][0], -dirs[1][1]])
        u1 = u1 - (u1 @ u0) * u0
        if np.linalg.norm(u1) > 1e-6:
            u1 /= np.linalg.norm(u1)
            Rm = np.stack([np.r_[u0, 0], np.r_[u1, 0], np.array([0, 0, 1.0])], 1)
            if np.linalg.det(Rm) < 0:
                Rm[:, 2] *= -1
            turns.append(Rm)
    seeds = []
    for Rm in turns:
        for perm in set(itertools.permutations(range(3))):
            L = np.array([dims[perm[n]] for n in range(3)], float)
            for sc in (0.85, 1.0, 1.15):
                for dx, dy in itertools.product((-0.15, 0, 0.15), repeat=2):
                    c = c0 + np.array([dx, dy]) * unit
                    seeds.append((score(Rm, L * sc, c)[0] - 0.02 * abs(math.log(sc)), Rm, L, sc, c))
    if not seeds:
        return None
    seeds.sort(key=lambda t: -t[0])
    if seeds[0][0] < 0:
        return None  # every box takes in road
    best = None
    for s0, Rm0, L, sc0, cc in [t for t in seeds if t[0] >= 0][:8]:
        # Fine-tune: position, size, small turns about the camera axes; steps halve when nothing improves.
        x = np.array([cc[0], cc[1], math.log(sc0), 0.0, 0.0, 0.0])
        step = np.array([0.1 * unit, 0.1 * unit, 0.05, 3.0, 3.0, 3.0])
        f = lambda x: score(_axis_turn(*x[3:]) @ Rm0, L * math.exp(x[2]), x[:2])[0] - 0.02 * abs(x[2])
        fx = f(x)
        for _ in range(4):
            moved = True
            while moved:
                moved = False
                for d in range(6):
                    for sgn in (1, -1):
                        y = x.copy()
                        y[d] += sgn * step[d]
                        if abs(y[2]) > math.log(1.2):  # real sides: size within 20% of the camera's
                            continue
                        fy = f(y)
                        if fy > fx + 1e-9:
                            x, fx, moved = y, fy, True
                            break
            step = step / 2
        if best is None or fx > best[0]:
            Rm = _axis_turn(*x[3:]) @ Rm0
            best = (fx, score(Rm, L * math.exp(x[2]), x[:2]), Rm, L * math.exp(x[2]))
    _, (iou, pts), Rm, L = best
    edges = []
    for a in range(8):
        for n in range(3):
            if _BITS[a][n] == 0:
                b = a | (1 << (2 - n))
                # An edge shows when either face it borders faces the camera (outward normal toward +z).
                vis = any((2 * _BITS[a][j] - 1) * Rm[2, j] > 1e-6 for j in range(3) if j != n)
                edges.append((a, b, vis))
    return iou, pts, edges, Rm, L


def boxes_from_outline(mask, unit, dims=(6, 12, 6), free=None, most=2, road=None):
    """box_from_outline, then -- when a sizeable piece of the part's colour is left outside the box (two limbs of one
    colour touching) -- another box drawn for that leftover piece (the first box's area free for it), up to `most`."""
    from scipy import ndimage
    out = []
    left = mask.astype(bool).copy()
    free_now = np.zeros_like(left) if free is None else free.astype(bool).copy()
    for _ in range(most):
        r = box_from_outline(left, unit, dims, free_now, road)
        if r is None:
            break
        out.append(r)
        drawn = np.zeros(left.shape, np.uint8)
        cv2.fillConvexPoly(drawn, np.round(cv2.convexHull(r[1].astype(np.float32)).reshape(-1, 2)).astype(np.int32), 1)
        rest = left & ~cv2.dilate(drawn, np.ones((3, 3), np.uint8)).astype(bool)
        lab, n = ndimage.label(rest)
        if n == 0:
            break
        sizes = ndimage.sum(rest, lab, range(1, n + 1))
        k = int(np.argmax(sizes)) + 1
        if sizes[k - 1] < 0.3 * unit * unit:
            break
        free_now = free_now | drawn.astype(bool) | (rest & (lab != k))
        left = lab == k
    return out


def shape_candidates(fit, v, k, J, cam, part, mask, unit):
    """Joint vectors turning one part's box to the three directions its outline runs in (or two, seen face-on): every
    assignment of the box's real sides to those directions, both mirror readings; each assignment's implied scale must
    agree with the camera's within 35%."""
    triples = direction_triples(mask, unit)
    if not triples:
        return []
    bone = 'hips' if part == 'torso' else LIMB_JOINTS[part]
    dims = BOX_DIMS.get(part, (6, 12, 6))
    wr, wt, view_r = _body_frames(J, cam)
    b = BONES.index(bone)
    par = PARENT_IDX[b]
    parent_rot = wr[par].numpy() if par >= 0 else np.eye(3)
    vr = view_r.numpy()
    out = []
    import itertools
    for trip in triples:
        dirs = [g[0] for g in trip]
        lens = [g[2] for g in trip]
        out += _shape_turns(dirs, lens, dims, unit, J, bone, vr, parent_rot, len(trip) >= 3)
    return out


def _shape_turns(dirs, lens, dims, unit, J, bone, vr, parent_rot, three):
    out = []
    import itertools
    for perm in set(itertools.permutations(range(3))):
        if three:
            rots = rotations_from_three(dirs)
        else:
            # Two directions at right angles on screen, third toward the camera.
            u0, u1 = np.array([dirs[0][0], -dirs[0][1]]), np.array([dirs[1][0], -dirs[1][1]])
            if abs(u0 @ u1) > 0.35:
                continue
            u1 = u1 - (u1 @ u0) * u0
            u1 = u1 / np.linalg.norm(u1)
            Rm = np.stack([np.r_[u0, 0], np.r_[u1, 0], np.array([0, 0, 1.0])], 1)
            if np.linalg.det(Rm) < 0:
                Rm[:, 2] *= -1
            rots = [Rm]
        for Rm in rots:
            # Scale check: the longest edge along each direction against that side's real length.
            if three:
                implied = [lens[n] / max(1e-6, dims[perm[n]] * math.hypot(Rm[0, n], Rm[1, n])) for n in range(3)]
                ok = [x for x in implied if np.isfinite(x)]
                if not ok or max(abs(x / (unit / 6) - 1) for x in ok) > 0.35:
                    continue
            R_cam = np.zeros((3, 3))
            for n in range(3):
                R_cam[:, perm[n]] = Rm[:, n]
            if np.linalg.det(R_cam) < 0:
                R_cam[:, perm[2]] *= -1
            xd, yd, zd = joints_for_rotation(R_cam, vr, parent_rot)
            q = J.clone()
            q[F.JI[(bone, 0)] - NC], q[F.JI[(bone, 1)] - NC], q[F.JI[(bone, 2)] - NC] = xd, yd, zd
            out.append(q)
    return out


def sketch_part_box(view, i, cam_scale, part, target, corners):
    """The reference box of one part, from that part's own corners only (corners of its colour lying on its own pixels):
    each corner read as two sides of the part's box (real size), completed into the whole box anchored on the corner,
    scored on the part's own pixels -- its pixels covered, less floor covered, less half of any other part's colour it
    covers -- plus 0.3 x the share of the part's corners its outline lands on (several corners agreeing beat one).
    Returns (score, 8 window-px points, the corner it grew from, rotation, axes) or None."""
    obs = view.shadowed[i]
    pix, _, _, scale = view.windows[i]
    sc = float(scale)
    side = obs.shape[0]
    s_win = cam_scale / sc
    unit = 6 * s_win
    cls = F.CLASS_OF[part]
    dims = BOX_DIMS.get(part, (6, 12, 6))
    floor = obs == 0
    others = (obs > 0) & (obs != F.UNKNOWN) & ~target & (obs != cls) | ((obs == cls) & ~target)
    best = None
    for cpt, d1, d2, c_, ang in corners:
        l1 = edge_reach(obs, cpt, d1, cls, unit)
        l2 = edge_reach(obs, cpt, d2, cls, unit)
        if min(l1, l2) < 0.3 * unit:
            continue
        for R, ai, bj, ck in rotations_from_L(d1 * l1, d2 * l2, s_win, dims, axes=True):
            for third in (1, -1):
                pts = []
                for ta in (0, 1):
                    for tb in (0, 1):
                        for tc in (0, 1):
                            vec = ta * dims[ai] * R[:, ai] + tb * dims[bj] * R[:, bj] + tc * third * dims[ck] * R[:, ck]
                            pts.append(cpt + np.array([vec[0], -vec[1]]) * s_win)
                pts = np.array(pts)
                hull = cv2.convexHull(pts.astype(np.float32)).reshape(-1, 2)
                m_ = np.zeros((side, side), np.uint8)
                cv2.fillConvexPoly(m_, np.round(hull).astype(np.int32), 1)
                mb = m_.astype(bool)
                area = mb.sum()
                if area < 0.5 * unit * unit:
                    continue
                score = ((mb & target).sum() - (mb & floor).sum() - 0.5 * (mb & others).sum()) / area
                score += 0.3 * corner_hits(hull, corners, unit) / max(3, len(corners))
                if best is None or score > best[0]:
                    # Which way the third side runs only picks which box corner sits on the footage corner: the box's
                    # turn is R either way.
                    best = (score, pts, cpt, R, (ai, bj, ck, third))
    return best


def edge_reach(obs, pt, d, cls, unit, max_studs=3.0):
    """How far a corner's edge runs (window px): step along it through the part's colour (gaps up to 0.2 stud
    stepped over) until the colour ends."""
    H, W = obs.shape
    step, gap, last, t = 0.5, 0.0, 0.0, 0.0
    while t < max_studs * unit:
        t += step
        q = np.round(pt + d * t - np.array([-d[1], d[0]]) * 0.6).astype(int)  # just inside the edge
        inside = 0 <= q[0] < W and 0 <= q[1] < H and obs[q[1], q[0]] == cls
        if inside:
            last, gap = t, 0.0
        else:
            gap += step
            if gap > 0.2 * unit:
                break
    return last


def joints_for_rotation(R_cam, view_r, parent_rot):
    """The bone's own (x, y, z) degrees that turn its box to R_cam (camera space) under a parent turned parent_rot."""
    R_model = M2W_MAT @ view_r.T @ R_cam
    return _euler_zyx(torch.tensor(parent_rot.T @ R_model, dtype=DT))


def corner_candidates(fit, v, k, J, cam, part, corners, obs_sh, unit, sc, x0, y0):
    """Joint vectors turning one part's box to fit each of its corners (every reading of each corner's two edges)."""
    bone = 'hips' if part == 'torso' else LIMB_JOINTS[part]
    dims = BOX_DIMS.get(part, (6, 12, 6))
    wr, wt, view_r = _body_frames(J, cam)
    b = BONES.index(bone)
    par = PARENT_IDX[b]
    parent_rot = wr[par].numpy() if par >= 0 else np.eye(3)
    vr = view_r.numpy()
    out = []
    for cpt, d1, d2, cls, ang in corners:
        l1 = edge_reach(obs_sh, cpt, d1, cls, unit)
        l2 = edge_reach(obs_sh, cpt, d2, cls, unit)
        if min(l1, l2) < 0.3 * unit:
            continue
        for R_cam in rotations_from_L(d1 * l1 * sc, d2 * l2 * sc, float(cam[2]), dims):
            xd, yd, zd = joints_for_rotation(R_cam, vr, parent_rot)
            q = J.clone()
            q[F.JI[(bone, 0)] - NC], q[F.JI[(bone, 1)] - NC], q[F.JI[(bone, 2)] - NC] = xd, yd, zd
            out.append((q, cpt))
    return out


def torso_from_corners(fit, v, k, only=None):
    """The torso turned to fit each of its corners, the body slid so the matching box corner lands on the footage
    corner; kept if the judge prefers it. Returns (judged after, before, candidates tried)."""
    view = fit.views[v]
    i = view.idx[k]
    pix, _, _, scale = view.windows[i]
    sc = float(scale)
    x0, y0 = float(pix[0, 0]) - 0.5 * sc, float(pix[0, 1]) - 0.5 * sc
    J = fit.joints_at(v, k).detach().clone()
    cam = (fit.cams[v][k] * CAM_UNIT).detach().clone()
    unit = 6 * float(cam[2]) / sc
    corners = [cn for cn in view.corners.get(i, []) if cn[3] == F.CLASS_OF['torso']]
    before = float(judge(fit, v, i, J[None], cam[None], only=only)[0])
    cands = corner_candidates(fit, v, k, J, cam, 'torso', corners, view.shadowed[i], unit, sc, x0, y0)
    if not cands:
        return before, before, 0
    jt = next(jj for jj, pp in enumerate(F.PARTS) if pp[3] == 'torso')
    Js, cams = [], []
    with torch.no_grad():
        uv = geometry(torch.stack([q for q, _ in cands]), cam.expand(len(cands), 5), fit.persp[v], fit.lights[v], corners=True).numpy()
    for n, (q, cpt) in enumerate(cands):
        foot = np.array([x0, y0]) + cpt * sc
        vi = int(np.argmin(np.hypot(*(uv[n, jt] - foot).T)))
        c2 = cam.clone()
        c2[3] += float(foot[0] - uv[n, jt, vi, 0])
        c2[4] += float(foot[1] - uv[n, jt, vi, 1])
        Js.append(q)
        cams.append(c2)
    scores = judge(fit, v, i, torch.stack(Js), torch.stack(cams), only=only)
    best = int(torch.argmax(scores))
    if float(scores[best]) > before:
        with torch.no_grad():
            if v == 0:
                fit.J[k] = Js[best]
            fit.cams[v][k] = cams[best] / CAM_UNIT
        return float(scores[best]), before, len(cands)
    return before, before, len(cands)


LIMB_JOINTS = {'rightArm': 'rightArm', 'leftArm': 'leftArm', 'rightLeg': 'rightThigh', 'leftLeg': 'leftThigh'}


def front_maps(fit, v, i, J, cam):
    """Which box is front-most at each window pixel, for a batch of poses: (B, side, side) of box index + 1 (0 floor),
    boxes painted far to near with OpenCV."""
    view = fit.views[v]
    pix, _, _, scale = view.windows[i]
    side = view.res
    x0, y0, sc = float(pix[0, 0]) - 0.5 * float(scale), float(pix[0, 1]) - 0.5 * float(scale), float(scale)
    with torch.no_grad():
        uv, z = geometry(J, cam, fit.persp[v], fit.lights[v], corners='depth')
    uv = ((uv.numpy() - (x0, y0)) / sc)
    z = z.numpy()
    out = np.zeros((uv.shape[0], side, side), np.uint8)
    parts = [j for j, pp in enumerate(F.PARTS) if pp[3] != 'face']
    for b in range(uv.shape[0]):
        for j in sorted(parts, key=lambda jj: z[b, jj]):
            hull = cv2.convexHull(uv[b, j].astype(np.float32)).reshape(-1, 2)
            cv2.fillConvexPoly(out[b], np.round(hull).astype(np.int32), int(j + 1))
    return out


def measure_limbs(fit, v, k, prev=None, log=None):
    """Each limb measured from the picture instead of searched (see the module notes): the head's yellow removed first;
    each limb's own pixels (patches of its colour given to the nearest shoulder/hip, a patch touching both split by two
    rectangles); its direction from the long axis of those pixels; its tilt toward/away from the camera from how much
    shorter than its 2 studs it looks; then only toward-vs-away and its twist are decided, by how fully the box -- drawn
    behind/in front of the others as it would be -- covers the limb's pixels (the previous frame breaks ties). A limb
    whose measured box covers less than 55% of its pixels falls back to trying every angle. Returns {part: coverage}."""
    view = fit.views[v]
    i = view.idx[k]
    pix, obs_t, _, scale = view.windows[i]
    side = view.res
    obs = obs_t.numpy().reshape(side, side)
    if F.FACE > 0:
        obs = np.where(obs == F.FACE, F.HEAD, obs)
    J = fit.joints_at(v, k).detach().clone()
    cam = (fit.cams[v][k] * CAM_UNIT).detach().clone()
    sc = float(scale)
    x0, y0 = float(pix[0, 0]) - 0.5 * sc, float(pix[0, 1]) - 0.5 * sc
    stud = 6 * float(cam[2]) / sc  # one stud in window px
    to_win = lambda q: (np.asarray(q) - (x0, y0)) / sc
    body = RR.body_cluster(obs, {c: 1 for c in set(F.CLASS_OF.values())})
    # 1. The head first: its box where the pose puts it, a little enlarged; that colour is head, not arm.
    with torch.no_grad():
        uv = geometry(J[None], cam[None], fit.persp[v], fit.lights[v], corners=True)[0].numpy()
    jh = next(jj for jj, pp in enumerate(F.PARTS) if pp[3] == 'head')
    head = np.zeros((side, side), np.uint8)
    cv2.fillConvexPoly(head, np.round(cv2.convexHull(to_win(uv[jh]).astype(np.float32)).reshape(-1, 2)).astype(np.int32), 1)
    head = ndimage.binary_dilation(head.astype(bool), iterations=max(1, int(round(0.3 * stud))))
    # 2. Each limb's own pixels.
    wr, wt, view_r = _body_frames(J, cam)
    joint2d = {part: to_win(_to_screen(cam, view_r, wt[BONES.index(bone)])) for part, bone in LIMB_JOINTS.items()}
    owned = {}
    for cls in {F.CLASS_OF[p_] for p_ in LIMB_JOINTS}:
        limbs = [p_ for p_ in LIMB_JOINTS if F.CLASS_OF[p_] == cls]
        pool = (obs == cls) & body & ~(head if cls == F.CLASS_OF['head'] else False)
        lab, n = ndimage.label(pool)
        patches = []
        for c in range(1, n + 1):
            ys, xs = np.nonzero(lab == c)
            if len(xs) >= 0.25 * stud * stud:
                patches.append(np.stack([xs, ys], 1))
        patches.sort(key=len, reverse=True)
        if len(limbs) == 2 and len(patches) == 1 and len(patches[0]) > 2.5 * stud * stud:
            # One patch holding both limbs (touching): split it into two rectangles.
            halves = RR.fit_rects(patches[0], 2)
            if len(halves) == 2:
                patches = [g for _, g in halves]
        groups = {p_: [] for p_ in limbs}
        if patches:
            # One patch per limb, the pairing with the least total joint-to-patch distance; extra patches to the nearest.
            from scipy.optimize import linear_sum_assignment
            d = np.array([[np.min(np.hypot(*(g - joint2d[p_]).T)) for p_ in limbs] for g in patches])
            rows, colsx = linear_sum_assignment(d)
            for r_, c_ in zip(rows, colsx):
                groups[limbs[c_]].append(patches[r_])
            for r_ in set(range(len(patches))) - set(rows):
                groups[limbs[int(np.argmin(d[r_]))]].append(patches[r_])
        for p_ in limbs:
            if groups[p_]:
                owned[p_] = np.concatenate(groups[p_])
    report = {}
    for part, bone in LIMB_JOINTS.items():
        j = next(jj for jj, pp in enumerate(F.PARTS) if pp[3] == part)
        pts = owned.get(part)
        if pts is None or len(pts) < 0.5 * stud * stud:
            continue
        target = np.zeros((side, side), bool)
        target[pts[:, 1], pts[:, 0]] = True
        # 3. Direction: the long axis of the limb's pixels, pointing away from its joint.
        ctr = pts.mean(0)
        u = np.linalg.svd(pts - ctr, full_matrices=False)[2][0]
        if (ctr - joint2d[part]) @ u < 0:
            u = -u
        t = (pts - joint2d[part]) @ u
        reach = max(0.0, np.percentile(t, 98) - max(0.0, np.percentile(t, 2)))
        # The limb's own corners (footage corners of its colour on its pixels); those at its far end give the tip.
        cls_ = F.CLASS_OF[part]
        mine_c = []
        for cn in view.corners.get(i, []):
            if cn[3] != cls_:
                continue
            q = np.clip(np.round(cn[0]).astype(int), 0, side - 1)
            if ndimage.binary_dilation(target, iterations=2)[q[1], q[0]]:
                mine_c.append(cn)
        far = [cn for cn in mine_c if (cn[0] - joint2d[part]) @ u > 0.6 * reach]
        if far:
            reach = max(reach * 0.5, max((cn[0] - joint2d[part]) @ u for cn in far) - max(0.0, np.percentile(t, 2)))
        # 4. Tilt: a 2-stud box tilted phi out of the picture shows about 2 cos(phi) + 1.2 sin(phi) studs along its axis.
        phis = np.radians(np.arange(0, 90, 1.0))
        shown = stud * (2 * np.cos(phis) + 1.2 * np.sin(phis))
        phi = phis[int(np.argmin(np.abs(shown - max(reach, stud))))]
        d2 = u * (2 * stud * math.cos(phi)) * sc  # footage px, joint to far end on the picture plane
        b = BONES.index(bone)
        par = PARENT_IDX[b]
        cands = []
        for sgn in (1, -1):
            a = wr[par].T @ _dir_model(view_r, d2, 12.0, sgn, cam)
            xd = math.degrees(math.asin(float(torch.clamp(a[2], -1, 1))))
            zd = math.degrees(math.atan2(-float(a[0]), float(a[1])))
            cands += twisted(J, bone, xd, zd, tuple(range(-45, 46, 9)))
        # The limb's reference box, sketched from its own corners only; its turn starts the candidates.
        ref = sketch_part_box(view, i, float(cam[2]), part, target, mine_c)
        view.ref_boxes.setdefault(i, {})[part] = ref
        if ref is not None:
            xd_, yd_, zd_ = joints_for_rotation(ref[3], view_r.numpy(), wr[par].numpy())
            q = J.clone()
            q[F.JI[(bone, 0)] - NC], q[F.JI[(bone, 1)] - NC], q[F.JI[(bone, 2)] - NC] = xd_, yd_, zd_
            cands.insert(0, q)
        # Shape-built turns: the three directions the limb's outline runs in are its box's three axes.
        cands += shape_candidates(fit, v, k, J, cam, part, target, stud)
        # Corner-built turns: each of the limb's own corners read as two of its box's edges (exact box geometry).
        cands += [q for q, _ in corner_candidates(fit, v, k, J, cam, part, mine_c, view.shadowed[i], stud, sc, x0, y0)]
        # 5. Decide toward/away and twist by coverage of the limb's own pixels (drawn with the others in place).
        def coverage(Js):
            """How fully the box covers the limb's pixels (overlap), plus 0.3 x the share of the limb's own corners its
            outline lands on with matching edge directions."""
            maps = front_maps(fit, v, i, torch.stack(Js), cam.expand(len(Js), 5))
            mine = maps == (j + 1)
            inter = (mine & target).sum((1, 2))
            union = (mine | target).sum((1, 2))
            cov_ = inter / np.maximum(1, union)
            if mine_c:
                with torch.no_grad():
                    uvs = geometry(torch.stack(Js), cam.expand(len(Js), 5), fit.persp[v], fit.lights[v], corners=True).numpy()
                hits = np.array([corner_hits(cv2.convexHull(to_win(uvs[n_, j]).astype(np.float32)).reshape(-1, 2), mine_c, stud)
                                 for n_ in range(len(Js))])
                cov_ = cov_ + 0.3 * hits / max(3, len(mine_c))  # a lone (maybe blurred) corner counts a third
            return cov_
        cov = coverage(cands)
        if prev is not None:
            idxs = [F.JI[(bone, a_)] - NC for a_ in range(3)]
            drift = np.array([np.abs((c[idxs] - prev[idxs] + 180) % 360 - 180).mean() for c in cands])
            cov = cov - 0.0005 * drift  # the previous frame only breaks near-ties
        best = int(np.argmax(cov))
        how = 'measured'
        if cov[best] < 0.55:
            # 6. Clue missing or misleading: every angle of this limb, then its twist.
            sgn_ = 1 if bone.startswith('right') else -1
            grid = []
            for xg in range(-180, 61, 6):
                for zg in range(-30, 161, 6):
                    q = J.clone()
                    q[F.JI[(bone, 0)] - NC], q[F.JI[(bone, 1)] - NC], q[F.JI[(bone, 2)] - NC] = xg, 0.0, sgn_ * zg
                    grid.append(q)
            gcov = coverage(grid)
            g = grid[int(np.argmax(gcov))]
            tw = twisted(J, bone, float(g[F.JI[(bone, 0)] - NC]), float(g[F.JI[(bone, 2)] - NC]), tuple(range(-45, 46, 9)))
            tcov = coverage(tw)
            if tcov.max() > cov[best]:
                cands, cov, best, how = tw, tcov, int(np.argmax(tcov)), 'searched'
        J = cands[best]
        report[part] = (round(float(cov[best]), 2), how, len(mine_c))
    with torch.no_grad():
        if v == 0:
            fit.J[k] = J
    if log:
        log(f'frame {i}: limbs ' + ', '.join(f'{p_} {c} ({h}, {nc} corners)' for p_, (c, h, nc) in report.items()))
    return report


PART_BONES = [('torso', 'hips'), ('head', 'head'), ('rightArm', 'rightArm'), ('leftArm', 'leftArm'),
              ('rightLeg', 'rightThigh'), ('leftLeg', 'leftThigh')]
PART_SURE = 0.4  # a part is "seen" when this share of its front-most pixels is its own colour (and enough of them)


def part_confidence(fit, v, k):
    """How surely each part is seen in frame k: of the pixels where the part's box is the front-most box, the share that
    shows the part's own colour (0 when the box is mostly hidden or covers fewer than 1/3 stud squared of pixels)."""
    view = fit.views[v]
    i = view.idx[k]
    pix, obs_t, _, scale = view.windows[i]
    side = view.res
    with torch.no_grad():
        quads, depth, vis, shade = geometry(fit.joints_at(v, k).detach()[None], (fit.cams[v][[k]] * CAM_UNIT).detach(),
                                            fit.persp[v], fit.lights[v])
        _, _, pprob = rasterise(quads, depth, vis, shade, pix[None], 0.05 * scale, fit.classes, fit.ncls, parts=True)
    front = pprob[0].argmax(-1).numpy()
    drawn = pprob[0].sum(-1).numpy() > 0.5
    obs = obs_t.numpy()
    if F.FACE > 0:
        obs = np.where(obs == F.FACE, F.HEAD, obs)
    stud_px = (6 * float(fit.cams[v][k, 2].detach() * CAM_UNIT[2]) / float(scale)) ** 2
    out = []
    for part, _ in PART_BONES:
        j = next(jj for jj, pp in enumerate(F.PARTS) if pp[3] == part)
        mine = (front == j) & drawn
        n = mine.sum()
        out.append(0.0 if n < stud_px / 3 else float((mine & (obs == F.CLASS_OF[part])).sum()) / n)
    return np.array(out)


def run_keyframes(frames, idx, fps, args, log=print):
    """Clear frames first: every frame is fitted from the last (rectangles, lifting, polish); the frames that fit well
    with little of the body hidden, and whose pose does not jump away from its neighbours (a flip, swapped limbs),
    are kept; every other frame takes its pose eased between the nearest kept frames. Returns (fit, fitter, keep)."""
    pal = json.load(open(args.palette)) if args.palette else F.PALETTE
    F.use_palette(pal)
    encs = {i: F.classify(frames[i], pal) for i in idx}
    view = View(frames, encs, idx, fps, args.res)
    sides = sides_mode()
    view.lite = sides  # clothing painted per side: the colour map alone (the per-part steps assume a colour per part)
    f0 = F.Fitter()
    if sides:
        p0 = standing_start(encs[idx[0]])  # the turn sweep on the first frame does the search
    else:
        p0, _ = f0.init(encs[idx[0]], tuple(float(x) for x in args.at.split(',')) if args.at else None, args.flip,
                        quick=getattr(args, 'quick_start', False))
    fit = Fit([view], np.tile(p0[NC:], (len(idx), 1)), [np.tile(p0[:5], (len(idx), 1))], [float(p0[5])], [f0.light])
    if sides:
        fit.head_lock = torch.full((len(idx),), HEAD_TURN_W, dtype=DT)
        with torch.no_grad():
            for a_ in (0, 1, 2):
                fit.J[:, F.JI[('head', a_)] - NC] = 0.0

    agree, hidden = np.zeros(len(idx)), np.zeros(len(idx))
    for k, i in enumerate(idx):
        with torch.no_grad():
            if k:
                fit.J[k] = fit.J[k - 1]
                fit.cams[0][k] = fit.cams[0][k - 1]
        view.window(i, fit.params_of(0, k), f0)
        if sides:
            # Every side's colour says how the box is turned: gradient steps from the last pose, then from a
            # quick search when the last pose is far off (a turn, a cut).
            track_step(fit, k, 60, sigma0=1.5)
            view.window(i, fit.params_of(0, k), f0)
            a_k = float(agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
            if a_k < 0.85 or k == 0:
                a_k = turn_sweep(fit, k)
                view.window(i, fit.params_of(0, k), f0)
            if a_k < 0.85:
                # The limbs, each searched all round (a punch or kick swings far between frames), then polished.
                snapJ, snapC = fit.J[k].detach().clone(), fit.cams[0][k].detach().clone()
                limb_sweep(fit, k)
                view.window(i, fit.params_of(0, k), f0)
                track_step(fit, k, 40, sigma0=1.0)
                view.window(i, fit.params_of(0, k), f0)
                a2 = float(agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
                if a2 > a_k:
                    a_k = a2
                else:
                    with torch.no_grad():
                        fit.J[k], fit.cams[0][k] = snapJ, snapC
                    view.window(i, fit.params_of(0, k), f0)
            if k == 0:
                # The light, from the shading of the first frame's pose.
                f0.fit_light(fit.params_of(0, 0), encs[i])
                fit.lights[0] = (torch.tensor(f0.light[0], dtype=DT), float(f0.light[1]))
            if a_k < 0.4:
                snapJ, snapC = fit.J[k].detach().clone(), fit.cams[0][k].detach().clone()
                p1, _ = f0.init(encs[i], None, args.flip, quick=True)
                with torch.no_grad():
                    fit.J[k] = torch.tensor(p1[NC:], dtype=DT)
                    fit.cams[0][k] = torch.tensor(p1[:5], dtype=DT) / CAM_UNIT
                view.window(i, fit.params_of(0, k), f0)
                track_step(fit, k, 60, sigma0=1.5)
                view.window(i, fit.params_of(0, k), f0)
                a2 = float(agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
                if a2 <= a_k:
                    with torch.no_grad():
                        fit.J[k], fit.cams[0][k] = snapJ, snapC
                    view.window(i, fit.params_of(0, k), f0)
            cam = (fit.cams[0][[k]] * CAM_UNIT).detach()
            # Standing still is exactly still: the plain standing pose (same turn) taken when it matches as well.
            Jk = fit.J.detach()[k].clone()
            stand = torch.zeros_like(Jk)
            stand[F.JI[('hips', 1)] - NC] = Jk[F.JI[('hips', 1)] - NC]
            both = agreement(fit, 0, i, torch.stack([Jk, stand]), cam.expand(2, 5))
            if float(both[1]) >= float(both[0]) - 0.005:
                with torch.no_grad():
                    fit.J[k] = stand
            agree[k] = float(agreement(fit, 0, i, fit.J.detach()[[k]], cam)[0])
            obs = view.windows[i][1].numpy()
            hidden[k] = (obs == F.UNKNOWN).sum() / max(1, (obs > 0).sum())
            log(f'frame {i}: colour agreement {agree[k]:.2f}, hidden {hidden[k]:.2f}')
            continue
        fit.assign(0, k)
        lift_all(fit, 0, k, steps=60)
        view.window(i, fit.params_of(0, k), f0)
        # Two ways, the better kept: the body as placed, and the torso turned to fit its own corners (judged on the
        # torso alone: its limbs would only drag it), each with its limbs measured and polished.
        prevJ = fit.J.detach()[k - 1].clone() if k else None
        snapJ, snapC = fit.J.detach()[k].clone(), fit.cams[0][k].detach().clone()
        results_k = []
        ways = ('as placed', 'torso from corners') if TORSO_CORNERS else ('as placed',)
        for way in ways:
            with torch.no_grad():
                fit.J[k] = snapJ
                fit.cams[0][k] = snapC
            view.window(i, fit.params_of(0, k), f0)
            if way == 'torso from corners':
                after_t, before_t, n_t = torso_from_corners(fit, 0, k, only=('torso',))
                if after_t <= before_t:
                    continue
                view.window(i, fit.params_of(0, k), f0)
            measure_limbs(fit, 0, k, prev=prevJ, log=None)
            polish(fit, 0, k, 30, sigma0=0.8, sigma1=0.4)
            view.window(i, fit.params_of(0, k), f0)
            a_k = float(agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
            results_k.append((a_k, way, fit.J.detach()[k].clone(), fit.cams[0][k].detach().clone()))
        best_k = max(results_k, key=lambda e: e[0])
        with torch.no_grad():
            fit.J[k] = best_k[2]
            fit.cams[0][k] = best_k[3]
        view.window(i, fit.params_of(0, k), f0)
        if len(results_k) > 1:
            log(f'frame {i}: ' + ', '.join(f'{w} {a:.3f}' for a, w, _, _ in results_k) + f' -> {best_k[1]}')
        cam = (fit.cams[0][[k]] * CAM_UNIT).detach()
        agree[k] = float(agreement(fit, 0, i, fit.J.detach()[[k]], cam)[0])
        obs = view.windows[i][1].numpy()
        hidden[k] = (obs == F.UNKNOWN).sum() / max(1, (obs > 0).sum())
        log(f'frame {i}: colour agreement {agree[k]:.2f}, hidden {hidden[k]:.2f}')
    if sides:
        hold_still(fit, log=log)
    # Check: every frame's final pose still scores what it scored when fitted (nothing drifted afterwards).
    for k, i in enumerate(idx):
        view.window(i, fit.params_of(0, k), f0)
        now = float(agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
        if abs(now - agree[k]) > 0.02:
            log(f'frame {i}: score changed after fitting ({agree[k]:.2f} -> {now:.2f})')
        agree[k] = now
    J = fit.J.detach().numpy().copy()
    # Clear: fits at least as well as the median frame, little hidden; and no jump from both neighbours.
    keep = (agree >= np.median(agree) - 0.05) & (hidden <= 0.35)
    for k in range(1, len(idx) - 1):
        d_prev = np.abs((J[k] - J[k - 1] + 180) % 360 - 180).mean()
        d_next = np.abs((J[k] - J[k + 1] + 180) % 360 - 180).mean()
        if min(d_prev, d_next) > 35:
            keep[k] = False
    if keep.sum() < 2:
        keep[np.argsort(-agree)[:2]] = True
    kept = np.nonzero(keep)[0]
    with torch.no_grad():
        C = fit.cams[0].detach().numpy().copy()
        for k in range(len(idx)):
            if keep[k]:
                continue
            a = kept[kept < k].max() if (kept < k).any() else kept.min()
            b = kept[kept > k].min() if (kept > k).any() else kept.max()
            f = 0.0 if a == b else (k - a) / (b - a)
            dj = (J[b] - J[a] + 180) % 360 - 180
            eased_J = torch.tensor(J[a] + f * dj, dtype=DT)
            eased_C = torch.tensor(C[a] + f * (C[b] - C[a]), dtype=DT)
            # Only when the eased pose matches the footage at least as well as the frame's own fit (never overrule
            # what the footage shows); else the frame keeps its fit.
            both = agreement(fit, 0, idx[k], torch.stack([fit.J[k].detach(), eased_J]),
                             torch.stack([fit.cams[0][k].detach() * CAM_UNIT, eased_C * CAM_UNIT]))
            if float(both[1]) >= float(both[0]) - 0.01:
                fit.J[k] = eased_J
                fit.cams[0][k] = eased_C
            else:
                keep[k] = True
    log(f'{int((~keep).sum())} of {len(idx)} frames eased between their neighbours (the rest keep their own fit)')
    # Per part: a part that cannot be seen in a frame (behind another part, in something in front of the body) takes
    # its angles eased between the nearest frames where that same part was clearly seen.
    conf = np.zeros((len(idx), len(PART_BONES)))
    for k, i in enumerate(idx):
        if not keep[k] or sides:
            continue  # already eased as a whole (per-part confidence needs a colour per part)
        conf[k] = part_confidence(fit, 0, k)
    sure = conf >= PART_SURE
    J = fit.J.detach().numpy().copy()
    borrowed = 0
    for pi, (part, bone) in enumerate(PART_BONES):
        cols = [F.JI[(bone, a)] - NC for a in range(3)]
        ok = np.nonzero(sure[:, pi])[0]
        if len(ok) == 0:
            continue
        for k in range(len(idx)):
            if sure[k, pi] or not keep[k]:
                continue
            a = ok[ok < k].max() if (ok < k).any() else ok.min()
            b = ok[ok > k].min() if (ok > k).any() else ok.max()
            f = 0.0 if a == b else (k - a) / (b - a)
            trial = J[k].copy()
            for c in cols:
                trial[c] = J[a, c] + f * ((J[b, c] - J[a, c] + 180) % 360 - 180)
            # Only where it costs (almost) nothing: borrowing fills what the footage cannot show, never overrules it.
            cam = (fit.cams[0][[k]] * CAM_UNIT).detach()
            both = agreement(fit, 0, idx[k], torch.tensor(np.stack([J[k], trial]), dtype=DT), cam.expand(2, 5))
            if float(both[1]) >= float(both[0]) - 0.01:
                J[k] = trial
                borrowed += 1
    with torch.no_grad():
        fit.J.copy_(torch.tensor(J, dtype=DT))
    log(f'{borrowed} hidden or overlapped part-frames took their angles from the frames around them')
    return fit, f0, keep, agree


def track_step(fit, k, steps=25, sigma0=1.0, sigma1=0.4, lr=0.8, every=5, joints=True, cam_lock=None):
    """Fast mode's per-frame fit: gradient steps on frame k alone from where it starts (the last frame's pose), all
    but the last 5 on every other pixel; every `every` steps the hard colour agreement is checked and the best state
    seen (the start included) is kept. Every other frame is put back as it was."""
    groups = [{'params': [fit.cams[0]], 'lr': 0.1 * lr}] + ([{'params': [fit.J], 'lr': lr}] if joints else [])
    opt = torch.optim.Adam(groups)
    keep_J, keep_C = fit.J.detach().clone(), fit.cams[0].detach().clone()
    view = fit.views[0]
    if fit.head_lock is not None:
        set_head_lock(fit, k)
    i = view.idx[k]
    score = lambda: float(agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
    best = (score(), fit.J[k].detach().clone(), fit.cams[0][k].detach().clone()) if every else None
    for st in range(steps):
        sig = sigma0 * (sigma1 / sigma0) ** (st / max(1, steps - 1))
        opt.zero_grad()
        loss = fit.loss_frames(0, [k], sig, stride=2 if st < steps - 5 else 1)[0] + fit.regular() * 0.1
        loss.backward()
        opt.step()
        if cam_lock is not None:
            with torch.no_grad():
                fit.cams[0][k, :2] = torch.as_tensor(cam_lock[:2], dtype=DT) / CAM_UNIT[:2]
                if len(cam_lock) > 2:
                    # The camera's scale held in its band (cam_lock[2] = (lo, hi)): a still camera's character only
                    # grows or shrinks a little as it moves nearer or farther.
                    lo, hi = cam_lock[2]
                    fit.cams[0][k, 2] = fit.cams[0][k, 2].clamp(lo / float(CAM_UNIT[2]), hi / float(CAM_UNIT[2]))
        if every and (st % every == every - 1 or st == steps - 1):
            a = score()
            if a > best[0]:
                best = (a, fit.J[k].detach().clone(), fit.cams[0][k].detach().clone())
    with torch.no_grad():
        Jk, Ck = (best[1], best[2]) if every else (fit.J[k].detach().clone(), fit.cams[0][k].detach().clone())
        fit.J.copy_(keep_J)
        fit.cams[0].copy_(keep_C)
        fit.J[k] = Jk
        fit.cams[0][k] = Ck


def torso_from_box(fit, k):
    """The torso's turn read from its own colour area: its box drawn from the outline (box_from_outline; other body
    colours may hide it), every reading of which drawn side is up (the 12 x 12 faces: either of the two long sides,
    either way) and both mirror readings, each set as the hips' turn with the limbs kept as they are; the best by hard
    colour agreement is kept if it beats the pose now. Returns the agreement."""
    import itertools
    view = fit.views[0]
    i = view.idx[k]
    pix, obs_t, _, scale = view.windows[i]
    side = view.res
    obs = obs_t.numpy().reshape(side, side)
    tcls = F.CLASS_OF['torso']
    mask = obs == tcls
    J = fit.J.detach()[k].clone()
    cam = (fit.cams[0][k] * CAM_UNIT).detach().clone()
    now = float(agreement(fit, 0, i, J[None], cam[None])[0])
    unit = 6 * float(cam[2]) / float(scale)
    if mask.sum() < 0.5 * unit * unit:
        return now
    free = (obs > 0) & (obs != tcls)
    r = box_from_outline(mask, unit, BOX_DIMS['torso'], free)
    if r is None:
        return now
    _, _, _, Rm, L = r
    zc = int(np.argmin(L))  # the 6-deep side
    a_, b_ = [c for c in range(3) if c != zc]
    _, _, view_r = _body_frames(J, cam)
    vr = view_r.numpy()
    mirror = np.diag([1.0, 1.0, -1.0])
    cands = []
    for R0 in (Rm, mirror @ Rm):
        for yc, xc in ((a_, b_), (b_, a_)):
            for sy, sx in itertools.product((1, -1), repeat=2):
                X, Y = sx * R0[:, xc], sy * R0[:, yc]
                R_cam = np.stack([X, Y, np.cross(X, Y)], 1)
                xd, yd, zd = joints_for_rotation(R_cam, vr, np.eye(3))
                q = J.clone()
                q[F.JI[('hips', 0)] - NC], q[F.JI[('hips', 1)] - NC], q[F.JI[('hips', 2)] - NC] = xd, yd, zd
                cands.append(q)
    Js = torch.stack(cands)
    sc = agreement(fit, 0, i, Js, cam[None].expand(len(cands), 5))
    best = int(torch.argmax(sc))
    if float(sc[best]) > now:
        with torch.no_grad():
            fit.J[k] = Js[best]
        return float(sc[best])
    return now


def colour_score(classes):
    """How much a patch looks like the character: how many different body colours it shows in solid amounts (each at
    least 3% of it; a red wall with a few stray pixels of other colours shows one), then its size."""
    cls = classes[np.isin(classes, F.BODY_CLASSES)]
    if not len(cls):
        return (0, 0)
    u, k = np.unique(cls, return_counts=True)
    return (int((k >= 0.03 * len(cls)).sum()), int(len(cls)))


def character_mask(obs):
    """The character in a window's class map (F.pick_character, nearest the window's centre, where the fit expects
    it); unknown pixels are not part of it."""
    body = np.isin(obs, F.BODY_CLASSES)
    m = F.pick_character(obs, body, near=(obs.shape[1] / 2, obs.shape[0] / 2))
    return m & (obs != F.UNKNOWN)


def body_sweep(fit, k, cam_lock, steps=40, top=3, leans=(-90, -60, -30, 0, 30, 60), rolls=(-45, 0, 45), prev=None,
               cont_w=0.0, lean_max=45.0, window=None):
    """A still camera already known (cam_lock: its yaw and tilt): the body tried turned every way instead -- lean x
    turn (every 15 deg) x roll, the limbs as they are and straight -- each placed onto the character (centre and size)
    and scored by hard colour agreement (quick ranking on every other pixel, the shortlist on every pixel); the best few
    get the limbs searched all round (limb_sweep) and polished with the camera's turn and tilt held. Frame k keeps the
    best, or what it had when nothing beats it."""
    view = fit.views[0]
    i = view.idx[k]
    J0 = fit.J.detach()[k].clone()
    c0 = (fit.cams[0][k] * CAM_UNIT).detach().clone()
    c0[0], c0[1] = float(cam_lock[0]), float(cam_lock[1])
    hx, hy, hz = (F.JI[('hips', a)] - NC for a in range(3))
    straight = torch.zeros_like(J0)
    poses = []
    if prev is not None:
        # Tracking: the body near the last frame's turn (+-60 deg), its lean and roll within lean_max (body sense: a
        # standing fighter's torso stays over its hips).
        p_lean, p_turn, p_roll = float(prev[hx]), float(prev[hy]), float(prev[hz])
        lean_set = sorted({max(-lean_max, min(lean_max, p_lean + d)) for d in (-30, -15, 0, 15, 30)})
        roll_set = sorted({max(-lean_max, min(lean_max, p_roll + d)) for d in (-30, -15, 0, 15, 30)})
        turn_set = [p_turn + d for d in range(-60, 61, 15)]
        limb_sets = (prev.clone(), J0)
    else:
        lean_set, roll_set, turn_set, limb_sets = leans, rolls, range(0, 360, 15), (J0, straight)
    for limbs in limb_sets:
        for lean in lean_set:
            for turn in turn_set:
                for roll in roll_set:
                    q = limbs.clone()
                    q[hx], q[hy], q[hz] = float(lean), float(turn), float(roll)
                    poses.append(q)
    Js = torch.stack(poses)
    C = c0[None].repeat(len(Js), 1)
    place(view, i, fit, Js, C, size=False)
    # Ranked by the body without its arms (their pose comes next, limb_sweep); while tracking, a turn far from the last
    # frame's pays for it (drawn_match counts pixels: the penalty in the same units, per 90 deg).
    def moved(Q):
        if prev is None or not cont_w:
            return torch.zeros(len(Q), dtype=DT)
        d = (Q[:, [hx, hy, hz]] - prev[[hx, hy, hz]] + 180) % 360 - 180
        return cont_w * 400.0 * d.abs().sum(1) / 90.0
    sc = drawn_match(fit, i, Js, C, stride=2) * 4 - moved(Js)
    short = torch.argsort(sc, descending=True)[:4 * top]
    full = drawn_match(fit, i, Js[short], C[short]) - moved(Js[short])
    order = short[torch.argsort(full, descending=True)[:top]]
    now = float(agreement(fit, 0, i, J0[None], c0[None])[0])
    best = (now, J0, (fit.cams[0][k]).detach().clone())
    f0 = window if window is not None else F.Fitter(view.res)  # the window the frame is seen through (track_frame's)
    for o in order.tolist():
        with torch.no_grad():
            fit.J[k] = Js[o]
            fit.cams[0][k] = C[o] / CAM_UNIT
        view.window(i, fit.params_of(0, k), f0)
        limb_sweep(fit, k, prev=prev, cont_w=cont_w)
        view.window(i, fit.params_of(0, k), f0)
        track_step(fit, k, steps, sigma0=1.2, cam_lock=cam_lock)
        view.window(i, fit.params_of(0, k), f0)
        a = float(agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
        if a > best[0]:
            best = (a, fit.J[k].detach().clone(), fit.cams[0][k].detach().clone())
    with torch.no_grad():
        fit.J[k], fit.cams[0][k] = best[1], best[2]
    view.window(i, fit.params_of(0, k), f0)
    return best[0]



def _wrap(d):
    return (d + 180) % 360 - 180


def _distinct(Js, order, cols, tol, most):
    """The first `most` candidates in `order` that differ from every one kept by more than tol deg in some column."""
    keep = []
    for o in order.tolist():
        if all(float(_wrap(Js[o, cols] - Js[k_, cols]).abs().max()) > tol for k_ in keep):
            keep.append(o)
            if len(keep) >= most:
                break
    return keep


THIGHS = ('rightThigh', 'leftThigh')


def leg_cost(Q, per_deg=1.0):
    """Feet under the body: each thigh swung past 30 deg (forward, back or out) costs per_deg per degree beyond, a
    twisted thigh half that (a fighter mostly stands on its legs; a kick must be clearly better). (B,), in the units
    of per_deg."""
    out = torch.zeros(len(Q), dtype=DT)
    for b in THIGHS:
        jx, jz, jy = (F.JI[(b, a)] - NC for a in (0, 2, 1))
        out = out + torch.relu(Q[:, jx].abs() - 30) + torch.relu(Q[:, jz].abs() - 30) + 0.5 * Q[:, jy].abs()
    return per_deg * out


def pose_search(fit, k, cam_lock, prev, cont_w=0.1, lean_max=45.0, bodies=12, keep=7, polish=3, steps=25,
                limb_rounds=1, fine=6.0, deadline=None, window=None):
    """The body and legs searched together, then the limbs, for a still camera (cam_lock: its turn and tilt). Hard
    colour scores (hard_draw) make thousands of candidates affordable:
      1. the body turned all round (15 deg steps; a turn far from the last frame's pays for it) x lean x roll near the
         last frame's (within lean_max: body sense), with a few leg sets (the last frame's, the tracked ones, straight,
         a stride either way), ranked by the drawing without arms (drawn_match) less the turn's and the legs' costs;
      2. the best `bodies` distinct bodies each with both thighs on a grid (forward/back), ranked the same way;
      3. the best `keep` distinct poses get every limb searched (limb_sweep, arms included) and are scored by full
         agreement; the best `polish` get gradient steps with the camera's turn and tilt held.
    Frame k keeps the best, or what it had when nothing beats it. Returns the agreement."""
    view = fit.views[0]
    i = view.idx[k]
    t0 = time.time()
    J0 = fit.J.detach()[k].clone()
    cam_now = fit.cams[0][k].detach().clone()
    c0 = (cam_now * CAM_UNIT).clone()
    c0[0], c0[1] = float(cam_lock[0]), float(cam_lock[1])
    hx, hy, hz = (F.JI[('hips', a)] - NC for a in range(3))
    body = [hx, hy, hz]
    lx = {b: F.JI[(b, 0)] - NC for b in THIGHS}
    legs = [F.JI[(b, a)] - NC for b in THIGHS for a in (0, 2, 1)]
    clamp = lambda a: max(-lean_max, min(lean_max, a))
    p_lean, p_turn, p_roll = float(prev[hx]), float(prev[hy]), float(prev[hz])
    lean_set = sorted({clamp(p_lean + d) for d in (-30, -15, 0, 15, 30)} | {0.0})
    roll_set = sorted({clamp(p_roll + d) for d in (-30, -15, 0, 15, 30)} | {0.0})
    turn_set = [p_turn + d for d in range(-165, 181, 15)]

    def turn_cost(Q):  # drawn_match pixels per 90 deg the body moved from the last frame
        return cont_w * 400.0 * _wrap(Q[:, body] - prev[body]).abs().sum(1) / 90.0

    def rank(Q, C, shifts, stride=1):
        sc, C2 = shifted_match(fit, i, Q, C, shifts, stride=stride)
        return sc - turn_cost(Q) - leg_cost(Q), C2

    wide = [(dx, dy) for dx in (-12, -6, 0, 6, 12) for dy in (-6, 0, 6)]
    near = [(dx, dy) for dx in (-3, -1, 0, 1, 3) for dy in (-3, -1, 0, 1, 3)]

    # 1. Body x a few leg sets.
    leg_sets = [prev[legs], J0[legs], torch.zeros(len(legs), dtype=DT)]
    for a in (25.0, -25.0):
        q = torch.zeros(len(legs), dtype=DT)
        q[0], q[3] = a, -a
        leg_sets.append(q)
    grid = torch.tensor([[lean, turn, roll] for lean in lean_set for turn in turn_set for roll in roll_set], dtype=DT)
    poses = []
    for ls in leg_sets:
        Q = J0[None].repeat(len(grid), 1)
        Q[:, body] = grid
        Q[:, legs] = ls
        poses.append(Q)
    Js = torch.cat(poses)
    C = c0[None].repeat(len(Js), 1)
    place(view, i, fit, Js, C, size=False)
    sc, C = rank(Js, C, wide, stride=2)
    short = torch.argsort(sc, descending=True)[:6 * bodies]
    full, C[short] = rank(Js[short], C[short], near)
    picks = [int(short[o]) for o in _distinct(Js[short], torch.argsort(full, descending=True), body, 10, bodies)]
    t1 = time.time()
    # 2. Each body with both thighs on a grid.
    xs = (-45.0, -30.0, -15.0, 0.0, 15.0, 30.0, 45.0)
    poses, cams = [], []
    for o in picks:
        Q = Js[o][None].repeat(len(xs) ** 2 + 1, 1)
        for n, (a, b) in enumerate((a, b) for a in xs for b in xs):
            Q[n, legs] = 0.0  # straight down, untwisted, swung forward or back (the last row keeps its own legs)
            Q[n, lx['rightThigh']], Q[n, lx['leftThigh']] = a, b
        poses.append(Q)
        cams.append(C[o][None].repeat(len(Q), 1))
    Js2, C2 = torch.cat(poses), torch.cat(cams)
    sc2, C2 = rank(Js2, C2, near)
    tops = _distinct(Js2, torch.argsort(sc2, descending=True), body + legs, 10, keep)
    if fine:  # 2b. each kept pose's body within +-fine deg (lean, turn, roll), its legs held
        d = torch.tensor([[a, b, c] for a in (-fine, 0, fine) for b in (-fine, 0, fine) for c in (-fine, 0, fine)],
                         dtype=DT)
        Q = Js2[tops].repeat_interleave(len(d), 0)
        Q[:, body] = Q[:, body] + d.repeat(len(tops), 1)
        Q[:, hx] = Q[:, hx].clamp(-lean_max, lean_max)
        Q[:, hz] = Q[:, hz].clamp(-lean_max, lean_max)
        sc3, C3 = rank(Q, C2[tops].repeat_interleave(len(d), 0), near)
        best3 = sc3.view(len(tops), len(d)).argmax(1)
        rows = [n * len(d) + int(best3[n]) for n in range(len(tops))]
        Js2, C2, sc2 = Q[rows], C3[rows], sc3[rows]
        tops = list(range(len(tops)))
    if DEBUG:
        print('  bodies', [([round(float(x)) for x in Js[o, body]], round(float(sc[o]))) for o in picks])
        print('  tops', [([round(float(x)) for x in Js2[o, body + legs]], round(float(sc2[o]))) for o in tops])
    t2 = time.time()
    # 3. Limbs, then polish.
    cam_k = lambda: (fit.cams[0][[k]] * CAM_UNIT).detach()
    now = float(agreement(fit, 0, i, J0[None], c0[None])[0])
    results = [(now, J0, cam_now)]
    for n_, o in enumerate(tops):
        if deadline is not None and n_ >= 2 and time.time() > deadline - 2.5:
            break  # out of time: the poses ranked lower go unsearched
        with torch.no_grad():
            fit.J[k] = Js2[o]
            fit.cams[0][k] = C2[o] / CAM_UNIT
        limb_sweep(fit, k, rounds=limb_rounds, prev=prev, cont_w=cont_w, leg_w=0.0005)
        a, Cq = shifted_match(fit, i, fit.J.detach()[[k]], cam_k(), near, full=True)
        a = float(a[0])
        results.append((a, fit.J[k].detach().clone(), (Cq[0] / CAM_UNIT).clone()))
    results.sort(key=lambda r: -r[0])
    if DEBUG:
        print(f'  times {t1 - t0:.2f} {t2 - t1:.2f} {time.time() - t2:.2f}')
        print('  limbs', [(round(r[0], 3), [round(float(x)) for x in r[1]]) for r in results])
    t3 = time.time()
    f0 = window if window is not None else F.Fitter(view.res)  # the window the frame is seen through (track_frame's)
    best = None
    done = []
    for a, Jq, Cq in results:
        if len(done) >= polish or (done and deadline is not None and time.time() > deadline - 0.8):
            break
        if any(float(_wrap(Jq - d).abs().max()) < 5 for d in done):
            continue  # the same pose found from two starts
        done.append(Jq)
        with torch.no_grad():
            fit.J[k], fit.cams[0][k] = Jq, Cq
        view.window(i, fit.params_of(0, k), f0)
        track_step(fit, k, steps, sigma0=1.2, cam_lock=cam_lock)
        view.window(i, fit.params_of(0, k), f0)
        a = float(agreement(fit, 0, i, fit.J.detach()[[k]], cam_k())[0])
        if best is None or a > best[0]:
            best = (a, fit.J[k].detach().clone(), fit.cams[0][k].detach().clone())
    with torch.no_grad():
        fit.J[k], fit.cams[0][k] = best[1], best[2]
    view.window(i, fit.params_of(0, k), f0)
    if DEBUG:
        print(f'  polish {time.time() - t3:.2f} s, {len(done)} poses, best {best[0]:.3f}')
    return float(agreement(fit, 0, i, fit.J.detach()[[k]], cam_k())[0])


def rig_outline(p, light):
    """The rig of params p: each box's 8 corners in footage px (P, 8, 2)."""
    with torch.no_grad():
        J = torch.tensor(np.asarray(p[NC:], float)[None], dtype=DT)
        C = torch.tensor(np.asarray(p[:5], float)[None], dtype=DT)
        return geometry(J, C, float(p[5]), light, corners=True)[0].numpy()


class TrackWindow:
    """The window a tracked frame is seen through, anchored on the rig instead of on whatever colours are nearby: only
    colours within `reach` studs of the outline of the rig as drawn in the last frame (prev_p; its boxes' convex hull,
    so a limb swung between frames stays in) can be the character -- another player beside it, or an effect trailing
    off it, is not. The window takes in the rig drawn now (params p) and those colours, centred on both and sized from
    them, so the character stays whole in view even where the rig is off, and nothing else can drag the window away or
    balloon it. Stands in for an r6_fit.Fitter in View.window (prepare and view are the fitter's own)."""

    def __init__(self, fitter, prev_p, light, res, reach=1.25):
        self.base, self.light, self.res = fitter, light, res
        self.hull = cv2.convexHull(rig_outline(prev_p, light).reshape(-1, 2).astype(np.float32))[:, 0]
        self.reach_px = reach * 6 * float(prev_p[2])  # footage px (one stud is 6 rig units)
        self.colours = None  # the frame's colours inside the gate: (x lo, y lo, x hi, y hi), footage px

    def _near(self, cls, x0, y0, k):
        """Which of these pixels (class map cls, its top left (x0, y0) in footage px, k pixels per footage px) can be
        the character: colours within reach of the last frame's outline, in pieces that touch the outline itself (a
        hand of another player standing just behind it is a piece of its own; a limb swung out is joined to the
        body). Unknown pixels (smoke, blur over it) count as joining."""
        m = np.zeros(cls.shape, np.uint8)
        cv2.fillConvexPoly(m, np.round(((self.hull - [x0, y0]) * k - 0.5) * 16).astype(np.int32), 1,
                           lineType=cv2.LINE_8, shift=4)
        if not m.any():
            return np.ones(cls.shape, bool)
        d = cv2.distanceTransform(1 - m, cv2.DIST_L2, 3)
        some = (np.isin(cls, F.BODY_CLASSES) | (cls == F.UNKNOWN)) & (d <= self.reach_px * k)
        n, lab = cv2.connectedComponents(cv2.dilate(some.astype(np.uint8), np.ones((3, 3), np.uint8)), connectivity=8)
        touch = np.unique(lab[some & (d <= 0.25 * self.reach_px * k)])
        return np.isin(lab, touch[touch > 0]) & some

    def _seen(self, obs_full):
        if self.colours is None:
            H, W = obs_full.shape
            lo = np.floor(self.hull.min(0) - self.reach_px).astype(int)
            hi = np.ceil(self.hull.max(0) + self.reach_px).astype(int)
            xa, ya = max(0, lo[0]), max(0, lo[1])
            xb, yb = min(W, hi[0] + 1), min(H, hi[1] + 1)
            self.colours = ()
            if xb > xa and yb > ya:
                cl = obs_full[ya:yb, xa:xb] // 1000
                ys, xs = np.nonzero(self._near(cl, xa - 0.5, ya - 0.5, 1.0) & np.isin(cl, F.BODY_CLASSES))
                if len(xs) > 30:
                    self.colours = (np.percentile(xs, 1) + xa, np.percentile(ys, 1) + ya, np.percentile(xs, 99) + xa,
                                    np.percentile(ys, 99) + ya)
        return self.colours

    def roi(self, p, obs_full):
        uv = rig_outline(p, self.light).reshape(-1, 2)
        lo, hi = uv.min(0), uv.max(0)
        seen = self._seen(obs_full) if obs_full is not None else ()
        if seen:
            lo, hi = np.minimum(lo, seen[:2]), np.maximum(hi, seen[2:])
        cx, cy = 0.5 * (lo + hi)
        half = max(0.7 * 30 * float(p[2]), 0.62 * float((hi - lo).max()))
        return (cx - half, cy - half, self.res / (2 * half), self.res, self.res)

    def gate(self, roi, obs):
        x0, y0, k, w, h = roi
        return self._near(obs, x0, y0, k)

    def view(self, obs_full, roi):
        return self.base.view(obs_full, roi)

    def prepare(self, enc):
        return self.base.prepare(enc)

    def prepare_lite(self, enc):
        return self.base.prepare_lite(enc)


# A still camera's scale while tracking: within this share of where tracking started, and of the last frame's.
SCALE_BAND, SCALE_STEP = 0.06, 0.01


def part_agreement(fit, k=0):
    """How well each part of frame k's pose is seen: per F.PARTS entry, its drawn pixels whose footage colour is
    known, and how many of those agree. (matched, seen) arrays; weights when several views of one move are fused."""
    view = fit.views[0]
    i = view.idx[k]
    J, C = fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach()
    pid = (PART_OF_FACE + 1).to(torch.int64)
    parts = hard_draw(view, i, J, C, fit.persp[0], fit.lights[0], pid)[0]
    drawn = hard_draw(view, i, J, C, fit.persp[0], fit.lights[0], fit.classes)[0]
    o, known, drawn = _hard_counts(view, i, drawn[None])
    drawn = drawn[0]
    seen = np.zeros(len(F.PARTS)), np.zeros(len(F.PARTS))
    for n in range(len(F.PARTS)):
        m = (parts == n + 1) & known
        seen[1][n] = m.sum()
        seen[0][n] = (m & (drawn == o)).sum()
    return seen


def track_frame(rgb, enc, p, cam_lock, light, fitter, cont_w=0.1, good=0.85, budget=7.5, detail=False):
    """A still camera's next frame from the last one's params p (cam_lock: the camera's turn and tilt, found on the
    idle frame, and optionally its scale there; light and fitter from that fit): re-centred onto the character, gradient
    steps from the last pose, and when that fits below `good` the body, legs and limbs searched (pose_search), within
    `budget` seconds. The window follows the rig (TrackWindow), not the footage's colours, and the camera's scale stays
    near the idle frame's (without one in cam_lock: the first tracked frame's, remembered on the fitter). Returns
    (params, agreement, how), and the Fit (its view windowed on the frame) after them when detail is set."""
    t_start = time.time()
    v = View([rgb], {0: enc}, [0], 30, 112)
    v.lite = True
    if len(cam_lock) > 2:
        ref = float(cam_lock[2])
    else:
        ref = fitter.__dict__.setdefault('track_scale', float(p[2]))
    lo = max(ref * (1 - SCALE_BAND), float(p[2]) * (1 - SCALE_STEP))
    hi = min(ref * (1 + SCALE_BAND), float(p[2]) * (1 + SCALE_STEP))
    if lo > hi:  # the last frame's scale outside the band: back toward it
        lo = hi = ref * (1 - SCALE_BAND) if float(p[2]) < ref else ref * (1 + SCALE_BAND)
    lock = (float(cam_lock[0]), float(cam_lock[1]), (lo, hi))
    p = np.array(p, float)
    p[2] = min(hi, max(lo, p[2]))
    win = TrackWindow(fitter, p, light, v.res)
    fit = Fit([v], p[None, NC:], [p[None, :5]], [float(p[5])], [light])
    fit.head_lock = torch.full((1,), HEAD_TURN_W, dtype=DT)
    fit.lean_w = 1e-4
    prev = torch.tensor(p[NC:], dtype=DT)
    v.window(0, p, win)
    # Where the last pose fits best: as it was, or centred on the character's outline (place), each slid a little
    # (an arm reaching out moves the outline's centre, not the body).
    C = (fit.cams[0][[0]] * CAM_UNIT).detach().clone().repeat(2, 1)
    Jl = fit.J.detach()[[0, 0]]
    place(v, 0, fit, Jl[1:], C[1:], size=False)
    sc, C = shifted_match(fit, 0, Jl, C, [(dx, dy) for dx in range(-6, 7, 2) for dy in range(-6, 7, 2)], full=True)
    with torch.no_grad():
        fit.cams[0][0] = C[int(torch.argmax(sc))] / CAM_UNIT
    v.window(0, fit.params_of(0, 0), win)
    track_step(fit, 0, 30, sigma0=1.2, cam_lock=lock)
    v.window(0, fit.params_of(0, 0), win)
    ag = float(agreement(fit, 0, 0, fit.J.detach()[[0]], (fit.cams[0][[0]] * CAM_UNIT).detach())[0])
    how = 'tracked'
    if ag < good:
        ag = pose_search(fit, 0, lock, prev, cont_w=cont_w, deadline=t_start + budget, window=win)
        how = 'searched'
    if detail:
        return fit.params_of(0, 0), ag, how, fit
    return fit.params_of(0, 0), ag, how


ARM_PARTS = ('rightArm', 'leftArm')


def drawn_match(fit, i, Js, C, leave_out=ARM_PARTS, stride=1):
    """How well the body without some parts (default: the arms) matches where it is drawn: drawn pixels whose seen
    colour agrees, less half of those whose seen colour differs (unknown pixels left out). The arms' pose is not yet
    known while the body's turn is searched; a wrong arm would swamp the score, so they are left undrawn. (B,)"""
    view = fit.views[0]
    if HARD_SCORE:
        drawn = hard_draw(view, i, Js.detach(), C.detach(), fit.persp[0], fit.lights[0], fit.classes, leave_out)
        _, obs, _, _ = view.windows[i]
        o = obs.numpy().reshape(view.res, view.res)
        if F.FACE > 0:
            o = np.where(o == F.FACE, F.HEAD, o)
            drawn = np.where(drawn == F.FACE, F.HEAD, drawn)
        if stride > 1:
            o, drawn = o[::stride, ::stride], drawn[:, ::stride, ::stride]
        known = (o != F.UNKNOWN)[None]
        good = ((drawn == o[None]) & (drawn > 0) & known).sum((1, 2))
        bad = ((drawn != o[None]) & (drawn > 0) & known).sum((1, 2))
        return torch.tensor(good - 0.5 * bad, dtype=DT)
    pix, obs, _, scale = view.windows[i]
    if stride > 1:
        side = view.res
        sel = torch.arange(side * side).reshape(side, side)[::stride, ::stride].reshape(-1)
        pix, obs = pix[sel], obs[sel]
    keep = torch.tensor([part not in leave_out for _, _, _, part in F.PARTS]).repeat_interleave(6)
    with torch.no_grad():
        quads, depth, vis, shade = geometry(Js, C, fit.persp[0], fit.lights[0])
        vis = vis * keep.to(vis.dtype)[None]
        out = []
        for c in range(0, Js.shape[0], 16):
            sl = slice(c, c + 16)
            n = quads[sl].shape[0]
            prob, _ = rasterise(quads[sl], depth[sl], vis[sl], shade[sl], pix.expand(n, -1, -1), 0.05 * scale, fit.classes,
                                fit.ncls)
            drawn = prob.argmax(-1)
            if F.FACE > 0:
                drawn = torch.where(drawn == F.FACE, F.HEAD, drawn)
            o = torch.where(obs == F.FACE, F.HEAD, obs) if F.FACE > 0 else obs
            known = o != F.UNKNOWN
            good = ((drawn == o) & (drawn > 0) & known).sum(-1).float()
            bad = ((drawn != o) & (drawn > 0) & known).sum(-1).float()
            out.append(good - 0.5 * bad)
        return torch.cat(out)


def shifted_match(fit, i, Js, C, shifts, leave_out=ARM_PARTS, stride=1, full=False):
    """drawn_match (or with full=True the agreement rule) of each candidate moved across the window by each of
    `shifts` (window px (dx, dy)): one drawing per candidate, slid instead of redrawn. Centring a candidate on the
    character's outline (place) puts it off by however far the parts not yet known (an outstretched arm) reach out;
    sliding finds where the parts drawn fit best. Returns (best score (B,), C moved to its best shift (B, 5))."""
    view = fit.views[0]
    res = view.res
    drawn = hard_draw(view, i, Js.detach(), C.detach(), fit.persp[0], fit.lights[0], fit.classes,
                      () if full else leave_out)
    _, obs, _, scale = view.windows[i]
    o = obs.numpy().reshape(res, res)
    known = o != F.UNKNOWN
    if full:
        known = known & ~view.border[i].numpy().reshape(res, res)
    if F.FACE > 0:
        o = np.where(o == F.FACE, F.HEAD, o)
        drawn = np.where(drawn == F.FACE, F.HEAD, drawn)
    o, known = o[::stride, ::stride], known[::stride, ::stride]
    S = max(max(abs(dx), abs(dy)) for dx, dy in shifts)
    dp = np.pad(drawn, ((0, 0), (S, S), (S, S)))
    best = np.full(len(drawn), -np.inf)
    arg = np.zeros((len(drawn), 2))
    for dx, dy in shifts:
        d = dp[:, S - dy:S - dy + res:stride, S - dx:S - dx + res:stride]
        on = (d > 0) & known[None]
        same = (d == o[None])
        if full:
            body = ((o > 0)[None] | (d > 0)) & known[None]
            sc = (same & (o > 0)[None] & known[None]).sum((1, 2)) / np.maximum(body.sum((1, 2)), 1)
        else:
            good = (same & on).sum((1, 2))
            sc = (good - 0.5 * (on.sum((1, 2)) - good)) * stride * stride
        better = sc > best
        best = np.where(better, sc, best)
        arg[better] = (dx, dy)
    C2 = C.clone()
    C2[:, 3:5] = C2[:, 3:5] + torch.tensor(arg * float(scale), dtype=DT)
    return torch.tensor(best, dtype=DT), C2


def place(view, i, fit, Js, C, size=True):
    """Candidates (poses Js, cameras C, changed in place) put onto the character: each one's drawn boxes sized to the
    character's full extent (height and width; printed lettering and seams inside it included) and centred on it.
    size=False: centred only (the size already known -- a still camera -- and the pose not yet: a candidate with its
    arms down must not be blown up to the width of outstretched ones)."""
    pix, obs, _, sc_w = view.windows[i]
    o2 = obs.numpy().reshape(view.res, view.res)
    body = character_mask(o2)
    whole = F.ndimage.binary_fill_holes(F.ndimage.binary_closing(body | (o2 == F.UNKNOWN), iterations=2)) & (
        F.ndimage.binary_dilation(body, iterations=3))
    whole = torch.tensor(whole.reshape(-1))
    if int(torch.tensor(body).sum()) <= 20:
        return
    bp = pix[whole]
    half = 0.5 * float(sc_w)
    o_h = bp[:, 1].max() - bp[:, 1].min() + 2 * half
    o_w = bp[:, 0].max() - bp[:, 0].min() + 2 * half
    o_c = 0.5 * (bp.max(0).values + bp.min(0).values)
    with torch.no_grad():
        for _ in range(2):
            uv = geometry(Js, C, fit.persp[0], fit.lights[0], corners=True).reshape(len(C), -1, 2)
            if size:
                d_h = (uv[..., 1].max(1).values - uv[..., 1].min(1).values).clamp(min=1.0)
                d_w = (uv[..., 0].max(1).values - uv[..., 0].min(1).values).clamp(min=1.0)
                C[:, 2] = C[:, 2] * torch.sqrt((o_h / d_h) * (o_w / d_w)).clamp(0.7, 1.4)
                uv = geometry(Js, C, fit.persp[0], fit.lights[0], corners=True).reshape(len(C), -1, 2)
            C[:, 3:5] = C[:, 3:5] + (o_c - 0.5 * (uv.max(1).values + uv.min(1).values))


def limb_sweep(fit, k, step=30, rounds=2, prev=None, cont_w=0.0, leg_w=0.0):
    """Side-coloured clothing: each limb (arms, then legs) tried pointing every way within its range -- its two swing
    angles on a step-degree grid and its turn about its own length (an arm raised forward swings across the chest by
    that turn), every candidate ranked at once by hard colour agreement (every other pixel; the shortlist again on every
    pixel), the best kept; twice round (a limb found helps the next). A fast swing between frames is found here, where
    gradient steps from the last frame would stall. prev (the last frame's joints) and cont_w: each candidate pays cont_w
    per 90 deg its limb moved from there (between frames a limb moves little; a far jump must be clearly better)."""
    view = fit.views[0]
    i = view.idx[k]
    cam = (fit.cams[0][[k]] * CAM_UNIT).detach()
    J = fit.J.detach()[k].clone()

    def moved(Js, cols):
        out = leg_cost(Js, leg_w) if leg_w else torch.zeros(len(Js), dtype=DT)
        if prev is None or not cont_w:
            return out
        d = (Js[:, cols] - prev[cols] + 180) % 360 - 180
        return out + cont_w * d.abs().sum(1) / 90.0

    best = float(agreement(fit, 0, i, J[None], cam)[0])
    for _ in range(rounds):
        for bone in ('rightArm', 'leftArm', 'rightThigh', 'leftThigh'):
            jx, jy, jz = (F.JI[(bone, a)] - NC for a in range(3))
            lx, lz = F.LIMITS[(bone, 0)], F.LIMITS[(bone, 2)]
            turns = (-90, -45, 0, 45, 90) if 'Arm' in bone else (-45, 0, 45)
            cands = []
            for ax in np.arange(lx[0], lx[1] + 1, step):
                for az in np.arange(lz[0], lz[1] + 1, step):
                    for ay in turns:
                        q = J.clone()
                        q[jx], q[jy], q[jz] = float(ax), float(ay), float(az)
                        cands.append(q)
            Js = torch.stack(cands)
            cols = [jx, jy, jz]
            # Arms move fast (a punch crosses half a circle between frames); legs mostly hold.
            wf = 0.25 if 'Arm' in bone else 1.0
            sc = agreement(fit, 0, i, Js, cam.expand(len(Js), 5), stride=2) - wf * moved(Js, cols)
            short = torch.argsort(sc, descending=True)[:8]
            full = agreement(fit, 0, i, Js[short], cam.expand(len(short), 5)) - wf * moved(Js[short], cols)
            now = float(agreement(fit, 0, i, J[None], cam)[0]) - wf * float(moved(J[None], cols)[0])
            o = int(short[int(torch.argmax(full))])
            if float(full.max()) > now:
                J = Js[o].clone()
                best = float(agreement(fit, 0, i, J[None], cam)[0])
    # Fine pass: each limb within +-20 deg (10 deg steps) of what the coarse grid found, its turn within +-30 deg.
    for bone in ('rightArm', 'leftArm', 'rightThigh', 'leftThigh'):
        jx, jy, jz = (F.JI[(bone, a)] - NC for a in range(3))
        cands = []
        for dx in (-20, -10, 0, 10, 20):
            for dz in (-20, -10, 0, 10, 20):
                for dy in (-30, -15, 0, 15, 30):
                    q = J.clone()
                    q[jx], q[jy], q[jz] = J[jx] + dx, J[jy] + dy, J[jz] + dz
                    for j_ in (jx, jy, jz):
                        q[j_] = float(np.clip(float(q[j_]), *F.LIMITS[F.JOINTS[j_]]))
                    cands.append(q)
        Js = torch.stack(cands)
        cols = [jx, jy, jz]
        wf = 0.25 if 'Arm' in bone else 1.0
        full = agreement(fit, 0, i, Js, cam.expand(len(Js), 5)) - wf * moved(Js, cols)
        now = float(agreement(fit, 0, i, J[None], cam)[0]) - wf * float(moved(J[None], cols)[0])
        o = int(torch.argmax(full))
        if float(full[o]) > now:
            J = Js[o].clone()
            best = float(agreement(fit, 0, i, J[None], cam)[0])
    with torch.no_grad():
        fit.J[k] = J
    return best


def standing_start(enc):
    """Side mode's start: the plain standing pose under a camera guessed from the character (the patch showing the
    most body colours: its centre and height); turn_sweep then searches every turn and tilt from it."""
    fg = enc >= 1000
    lab, n = F.ndimage.label(F.ndimage.binary_dilation(fg, iterations=4))
    p = np.zeros(F.NP)
    if not n:
        p[:6] = 0, 20, 1.0, enc.shape[1] / 2, enc.shape[0] / 2, 0.005
        return p
    cl = enc // 1000
    score = [colour_score(cl[(lab == c) & fg]) for c in range(1, n + 1)]
    pick = 1 + max(range(n), key=lambda c: score[c])
    cy, cx = F.ndimage.center_of_mass(lab == pick)
    ys = np.nonzero(lab == pick)[0]
    p[:6] = 0, 20, max(10, np.ptp(ys)) / 31, cx, cy, 0.005
    return p


def turn_sweep(fit, k, steps=30, yaws=24, pitches=(0, 15, 30, 45, 70), top=3):
    """Side-coloured clothing: the camera tried all round the body (yaws x pitches; position, size and pose kept),
    every candidate scored by hard colour agreement (each side's colour makes the right turn stand out), the best
    few polished by gradient (track_step); frame k keeps the best, or what it had when nothing beats it."""
    view = fit.views[0]
    i = view.idx[k]
    J = fit.J.detach()[k].clone()
    c0 = (fit.cams[0][k] * CAM_UNIT).detach().clone()
    # The body as it is, and upright (camera tilt and a backward lean look alike; a wrong pair must not stick).
    if fit.head_lock is not None:
        J = J.clone()
        for a_ in (0, 2):  # the head square on the body (its tilt is never evidence)
            J[F.JI[('head', a_)] - NC] = 0.0
    upright = J.clone()
    upright[F.JI[('hips', 0)] - NC] = 0.0
    upright[F.JI[('hips', 2)] - NC] = 0.0
    # And the plain standing pose (every joint straight): a still character is this under some camera.
    rest = torch.zeros_like(J)
    cams, poses = [], []
    for body in (J, upright, rest):
        for y in range(yaws):
            for pt in pitches:
                c = c0.clone()
                c[0], c[1] = c0[0] + 360.0 * y / yaws, float(pt)
                cams.append(c)
                poses.append(body)
    C, Js = torch.stack(cams), torch.stack(poses)
    # Each candidate placed onto the character: its drawn boxes' centre and height matched to the body's colours
    # (turning the camera swings the body about its feet; unplaced, a right turn would score as wrong).
    pix, obs, _, sc_w = view.windows[i]
    o2 = obs.numpy().reshape(view.res, view.res)
    body = character_mask(o2)
    # Its full extent: printed lettering and dark seams inside it count (they are part of the body's outline).
    whole = F.ndimage.binary_fill_holes(F.ndimage.binary_closing(body | (o2 == F.UNKNOWN), iterations=2)) & (
        F.ndimage.binary_dilation(body, iterations=3))
    body = torch.tensor(body.reshape(-1))
    whole = torch.tensor(whole.reshape(-1))
    if int(body.sum()) > 20:
        bp = pix[whole]
        o_c = bp.mean(0)
        half = 0.5 * float(sc_w)  # a window pixel's half width, in footage px
        o_h = bp[:, 1].max() - bp[:, 1].min() + 2 * half
        o_w = bp[:, 0].max() - bp[:, 0].min() + 2 * half
        with torch.no_grad():
            for _ in range(2):
                uv = geometry(Js, C, fit.persp[0], fit.lights[0], corners=True).reshape(len(C), -1, 2)
                d_h = (uv[..., 1].max(1).values - uv[..., 1].min(1).values).clamp(min=1.0)
                d_w = (uv[..., 0].max(1).values - uv[..., 0].min(1).values).clamp(min=1.0)
                C[:, 2] = C[:, 2] * torch.sqrt((o_h / d_h) * (o_w / d_w)).clamp(0.7, 1.4)
                uv = geometry(Js, C, fit.persp[0], fit.lights[0], corners=True).reshape(len(C), -1, 2)
                C[:, 3:5] = C[:, 3:5] + (o_c - 0.5 * (uv.max(1).values + uv.min(1).values))
            o_c = 0.5 * (bp.max(0).values + bp.min(0).values)
            uv = geometry(Js, C, fit.persp[0], fit.lights[0], corners=True).reshape(len(C), -1, 2)
            C[:, 3:5] = C[:, 3:5] + (o_c - 0.5 * (uv.max(1).values + uv.min(1).values))
    sc = agreement(fit, 0, i, Js, C, stride=2)
    now = float(agreement(fit, 0, i, J[None], c0[None])[0])
    best = (now, J, c0 / CAM_UNIT)
    # The quick ranking (every other pixel) shortlists; the shortlist is ranked again on every pixel (close candidates
    # on a small character are not told apart on a quarter of the pixels).
    short = torch.argsort(sc, descending=True)[:4 * top]
    full = agreement(fit, 0, i, Js[short], C[short])
    order = short[torch.argsort(full, descending=True)[:top]]
    for o in order.tolist():
        with torch.no_grad():
            fit.J[k] = Js[o]
            fit.cams[0][k] = C[o] / CAM_UNIT
        view.window(i, fit.params_of(0, k), F.Fitter(view.res))
        track_step(fit, k, steps, sigma0=1.5)
        a = float(agreement(fit, 0, i, fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
        if a > best[0]:
            best = (a, fit.J[k].detach().clone(), fit.cams[0][k].detach().clone())
    with torch.no_grad():
        fit.J[k], fit.cams[0][k] = best[1], best[2]
    return best[0]


def hold_still(fit, keep_tol=0.02, still_deg=12.0, log=print):
    """Still moments get one pose: runs of frames whose fitted joints all stay within still_deg of the frame before
    (the body not moving, however the camera moves) share the run's median pose, each frame keeping its own camera
    (re-fitted with the pose held); kept only when the run's colour agreement drops by at most keep_tol."""
    view = fit.views[0]
    J = fit.J.detach().numpy()
    T_ = len(J)
    runs, cur = [], [0]
    for k in range(1, T_):
        d = np.abs((J[k] - J[k - 1] + 180) % 360 - 180).max()
        if d < still_deg:
            cur.append(k)
        else:
            runs.append(cur)
            cur = [k]
    runs.append(cur)
    held = 0
    f0 = F.Fitter(view.res)
    score = lambda k: float(agreement(fit, 0, view.idx[k], fit.J.detach()[[k]], (fit.cams[0][[k]] * CAM_UNIT).detach())[0])
    for run in runs:
        if len(run) < 2:
            continue
        before = [score(k) for k in run]
        snapJ, snapC = fit.J.detach().clone(), fit.cams[0].detach().clone()
        Js = J[run]
        ref = Js[int(np.argmax(before))]
        med = ref + np.median((Js - ref + 180) % 360 - 180, 0)
        with torch.no_grad():
            for k in run:
                fit.J[k] = torch.tensor(med, dtype=DT)
        for k in run:
            view.window(view.idx[k], fit.params_of(0, k), f0)
            track_step(fit, k, 20, sigma0=0.8, joints=False)
            view.window(view.idx[k], fit.params_of(0, k), f0)
        after = [score(k) for k in run]
        if np.mean(after) >= np.mean(before) - keep_tol:
            held += len(run)
        else:
            with torch.no_grad():
                fit.J.copy_(snapJ)
                fit.cams[0].copy_(snapC)
            for k in run:
                view.window(view.idx[k], fit.params_of(0, k), f0)
    log(f'{held} still frames share one pose per still moment')
    return held


_FAST = {}  # the frames, shared with the worker processes (forked, so not copied)


def _fast_chunk(job):
    """One stretch of frames (no camera cut inside), fitted in order: a quick start search on its first frame, then
    every frame tracked from the last (track_step); a frame that still fits badly gets the full limb measuring.
    Returns [(frame, params, agreement)]."""
    chunk, pal, res, flip, steps, redo = job
    torch.set_num_threads(1)
    frames, fps = _FAST['frames'], _FAST['fps']
    F.use_palette(pal)
    encs = {i: F.classify(frames[i], pal) for i in chunk}
    view = View(frames, encs, chunk, fps, res)
    view.lite = True
    f0 = F.Fitter()
    p0, _ = f0.init(encs[chunk[0]], None, flip, quick=True)
    fit = Fit([view], np.tile(p0[NC:], (len(chunk), 1)), [np.tile(p0[:5], (len(chunk), 1))], [float(p0[5])], [f0.light])
    out = []
    last_init = 0
    for k, i in enumerate(chunk):
        with torch.no_grad():
            if k:
                fit.J[k] = fit.J[k - 1]
                fit.cams[0][k] = fit.cams[0][k - 1]
        view.window(i, fit.params_of(0, k), f0)
        track_step(fit, k, steps)
        view.window(i, fit.params_of(0, k), f0)
        cam = lambda: (fit.cams[0][[k]] * CAM_UNIT).detach()
        a = float(agreement(fit, 0, i, fit.J.detach()[[k]], cam())[0])
        if a < redo:
            # Lost (a fast turn, a limb swung far): the torso's turn read from its colour area, then the full
            # measuring of the limbs from theirs; kept only if better.
            snapJ, snapC = fit.J[k].detach().clone(), fit.cams[0][k].detach().clone()
            torso_from_box(fit, k)
            view.lite = False
            view.window(i, fit.params_of(0, k), f0)
            fit.assign(0, k)
            measure_limbs(fit, 0, k, prev=fit.J.detach()[k - 1].clone() if k else None, log=None)
            view.lite = True
            view.window(i, fit.params_of(0, k), f0)
            track_step(fit, k, steps)
            view.window(i, fit.params_of(0, k), f0)
            a2 = float(agreement(fit, 0, i, fit.J.detach()[[k]], cam())[0])
            if a2 > a:
                a = a2
            else:
                with torch.no_grad():
                    fit.J[k], fit.cams[0][k] = snapJ, snapC
                view.window(i, fit.params_of(0, k), f0)
        if a < 0.25 and k and k - last_init >= 5:
            last_init = k
            # Collapsed (a camera cut, or the body lost): a fresh quick search on this frame, kept if better.
            p1, _ = f0.init(encs[i], None, flip, quick=True)
            snapJ, snapC = fit.J[k].detach().clone(), fit.cams[0][k].detach().clone()
            with torch.no_grad():
                fit.J[k] = torch.tensor(p1[NC:], dtype=DT)
                fit.cams[0][k] = torch.tensor(p1[:5], dtype=DT) / CAM_UNIT
            view.window(i, fit.params_of(0, k), f0)
            track_step(fit, k, steps)
            view.window(i, fit.params_of(0, k), f0)
            a2 = float(agreement(fit, 0, i, fit.J.detach()[[k]], cam())[0])
            if a2 > a:
                a = a2
            else:
                with torch.no_grad():
                    fit.J[k], fit.cams[0][k] = snapJ, snapC
                view.window(i, fit.params_of(0, k), f0)
        out.append((i, fit.params_of(0, k), a))
    return out


def run_fast(frames, idx, fps, args, log=print):
    """Fast mode, for footage with one colour per body part: the frames split into one even stretch per worker
    process, fitted side by side (_fast_chunk), the results joined. Returns (fit, fitter, agreement per frame)."""
    import multiprocessing as mp
    pal = json.load(open(args.palette)) if args.palette else F.PALETTE
    F.use_palette(pal)
    t0 = time.time()
    workers = max(1, min(args.workers, os.cpu_count() or 1))
    # One even stretch per process, each with its own quick start (a camera cut inside one restarts by itself).
    size = math.ceil(len(idx) / workers)
    chunks = [idx[a:a + size] for a in range(0, len(idx), size)]
    log(f'{len(idx)} frames in {len(chunks)} stretches on {workers} processes')
    _FAST['frames'], _FAST['fps'] = frames, fps
    jobs = [(c, pal, args.res, args.flip, args.fast_steps, args.redo) for c in chunks]
    if workers > 1:
        with mp.get_context('fork').Pool(workers) as pool:
            results = pool.map(_fast_chunk, jobs, chunksize=1)
    else:
        results = [_fast_chunk(j) for j in jobs]
    got = {i: (p, a) for r in results for i, p, a in r}
    P = np.stack([got[i][0] for i in idx])
    agree = np.array([got[i][1] for i in idx])
    log(f'fitted in {time.time() - t0:.0f} s; colour agreement median {np.median(agree):.2f}, '
        f'{int((agree >= 0.8).sum())} of {len(idx)} frames at 0.8 or better')
    encs = {i: F.classify(frames[i], pal) for i in idx[:1]}
    view = View(frames, encs, idx, fps, args.res)
    f0 = F.Fitter()
    fit = Fit([view], P[:, NC:], [P[:, :5]], [float(P[0, 5])], [f0.light])
    return fit, f0, agree


def pose_features(enc):
    """A frame's pose as seen, for picking key poses: each part's colour area's centre (relative to the torso's,
    in torso heights) and its long axis's direction (as cos 2a, sin 2a, so either way along is the same), plus
    how much of each part shows. Returns (features, visible fraction per part)."""
    obs = enc // 1000
    parts = [p_ for _, _, _, p_ in F.PARTS if p_ != 'face']
    tm = obs == F.CLASS_OF['torso']
    if tm.sum() < 20:
        return None, np.zeros(len(parts))
    ty, tx = np.nonzero(tm)
    tc = np.array([tx.mean(), ty.mean()])
    th = max(4.0, math.sqrt(tm.sum()))
    feat, vis = [], []
    for part in parts:
        m = obs == F.CLASS_OF[part]
        ys, xs = np.nonzero(m)
        vis.append(len(xs) / (th * th) if part != 'torso' else 1.0)
        if len(xs) < 10:
            feat += [0.0, 0.0, 0.0, 0.0]
            continue
        c = np.array([xs.mean(), ys.mean()])
        cov = np.cov(np.stack([xs, ys]))
        w, V = np.linalg.eigh(cov)
        a = math.atan2(V[1, 1], V[0, 1])
        feat += list((c - tc) / th) + [math.cos(2 * a), math.sin(2 * a)]
    return np.array(feat), np.array(vis)


def find_moves(frames, pal, fps, calm=0.15, active=0.2, min_calm_s=0.15, min_len_s=1.0, size=480):
    """Where a move is performed, from the footage alone: each frame's body (its colours' area, centred and scaled to
    its own size, so the camera's panning and zoom drop out) against the body two frames before; standing idle it
    hardly changes, during a move it changes a lot. A move starts where the change rises above `active` after at
    least min_calm_s of calm (below `calm`), and ends where calm returns. Returns [(first frame, last frame)]."""
    import cv2
    norm = []
    for f in frames:
        small = cv2.resize(f, (size, round(f.shape[0] * size / f.shape[1])))
        cl = (F.classify(small, pal) // 1000).astype(np.uint8)
        body = np.isin(cl, F.BODY_CLASSES)
        lab, k = ndimage.label(ndimage.binary_dilation(body, iterations=2))
        if not k or body.sum() < 60:
            norm.append(None)
            continue
        sz = ndimage.sum(body, lab, range(1, k + 1))
        m = (lab == 1 + int(np.argmax(sz))) & body
        ys, xs = np.nonzero(m)
        sc = math.sqrt(m.sum())
        g = np.zeros((48, 48), np.uint8)
        g[np.clip(((ys - ys.mean()) / sc * 12 + 24).astype(int), 0, 47),
          np.clip(((xs - xs.mean()) / sc * 12 + 24).astype(int), 0, 47)] = cl[ys, xs]
        norm.append(g)
    chg = np.full(len(norm), np.nan)
    for i in range(2, len(norm)):
        a, b = norm[i], norm[i - 2]
        if a is not None and b is not None:
            both = (a > 0) | (b > 0)
            chg[i] = ((a != b) & both).sum() / max(1, both.sum())
    seen = ~np.isnan(chg)
    sm = ndimage.uniform_filter1d(np.nan_to_num(chg, nan=1.0), 3)
    need = max(1, round(min_calm_s * fps))
    moves, run, start, armed = [], 0, None, False
    for i in range(len(sm)):
        calm_now = seen[i] and sm[i] < calm
        if start is None:
            run = run + 1 if calm_now else 0
            armed = armed or run >= need
            if armed and not calm_now and seen[i] and sm[i] > active:
                start, run, armed = i, 0, False
        else:
            run = run + 1 if calm_now else 0
            if run >= need:
                end = i - run
                if (end - start) / fps >= min_len_s and seen[start:end + 1].mean() >= 0.7:
                    moves.append((start, end))
                start, armed = None, True
    return moves


def pick_key_poses(encs, idx, most=10, tol=0.25, near=2):
    """Key poses of a move: the first and last frames, then repeatedly the frame that straight in-betweening of the
    keys so far would miss most (pose_features), until every frame is within tol or `most` keys are chosen; each key
    then moved to the frame within `near` that shows most of the body (a side-on or hidden pose fits badly)."""
    if sides_mode():
        # Side-coloured clothing: no colour per part to read the pose from; keys evenly spaced.
        step = max(1, math.ceil((len(idx) - 1) / max(1, most - 1)))
        return sorted(set(idx[::step] + [idx[-1]]))
    feats, vis = {}, {}
    for i in idx:
        f, v = pose_features(encs[i])
        feats[i], vis[i] = f, v
    ok = [i for i in idx if feats[i] is not None]
    if len(ok) < 2:
        return ok
    X = np.stack([feats[i] for i in ok])
    keys = [0, len(ok) - 1]
    while len(keys) < most:
        keys.sort()
        worst, at = 0.0, None
        for a, b in zip(keys, keys[1:]):
            for j in range(a + 1, b):
                f = (j - a) / (b - a)
                e = float(np.abs(X[j] - (X[a] * (1 - f) + X[b] * f)).max())
                if e > worst:
                    worst, at = e, j
        if at is None or worst < tol:
            break
        keys.append(at)
    keys.sort()
    out = []
    for n, j in enumerate(keys):
        lo_ = max(keys[n - 1] + 1 if n else 0, j - near)
        hi_ = min(keys[n + 1] - 1 if n + 1 < len(keys) else len(ok) - 1, j + near)
        if n in (0, len(keys) - 1):
            lo_, hi_ = j, j
        best = max(range(lo_, hi_ + 1), key=lambda q: (float(np.minimum(vis[ok[q]], 1.0).sum()), -abs(q - j)))
        out.append(ok[best])
    return sorted(set(out))


def run_keyposes(frames, idx, fps, args, log=print):
    """Key-pose mode: only a move's key poses (pick_key_poses) are fitted, by the full keyframes fit (run_keyframes,
    its start search quick), and every frame between them takes its pose eased between the two keys around it
    (smoothstep, as an animation eases). Returns (fit over every frame, fitter, keys, agreement per key)."""
    pal = json.load(open(args.palette)) if args.palette else F.PALETTE
    F.use_palette(pal)
    t0 = time.time()
    # Side-coloured clothing: keys evenly spaced, nothing to read first (each frame's colours cost ~1 s at 1080p).
    encs = {} if sides_mode() else {i: F.classify(frames[i], pal) for i in idx}
    keys = pick_key_poses(encs, idx, most=args.keyposes)
    log(f'{len(keys)} key poses of {len(idx)} frames: {keys} ({time.time() - t0:.0f} s)')
    fit_k, f0, _, agree = run_keyframes(frames, keys, fps, args, log=log)
    PK = np.stack([fit_k.params_of(0, n) for n in range(len(keys))])
    P = np.zeros((len(idx), PK.shape[1]))
    for n, i in enumerate(idx):
        b = int(np.searchsorted(keys, i))
        if b < len(keys) and keys[b] == i:
            P[n] = PK[b]
            continue
        a_, b_ = max(0, b - 1), min(len(keys) - 1, b)
        f = 0.0 if keys[b_] == keys[a_] else (i - keys[a_]) / (keys[b_] - keys[a_])
        f = f * f * (3 - 2 * f)
        d = PK[b_] - PK[a_]
        d[NC:] = (d[NC:] + 180) % 360 - 180
        P[n] = PK[a_] + f * d
    log(f'key-pose fit in {time.time() - t0:.0f} s; key colour agreement ' + ' '.join(f'{x:.2f}' for x in agree))
    view = View(frames, {i: encs[i] for i in idx if i in encs}, idx, fps, args.res)
    fit = Fit([view], P[:, NC:], [P[:, :5]], [float(P[0, 5])], [f0.light])
    return fit, f0, keys, agree


def map_time(i, a, b):
    """Frame i of one performance to the same moment of another: piecewise straight between matching events
    (a, b: the same events' frames in each, e.g. start, first hit, end)."""
    for n in range(len(a) - 1):
        if i <= a[n + 1] or n == len(a) - 2:
            f = (i - a[n]) / max(1e-9, a[n + 1] - a[n])
            return int(round(b[n] + f * (b[n + 1] - b[n])))
    return b[-1]


def run_two_view_keys(frames, fps, fit_dir, anchors1, anchors2, args, steps=40, log=print):
    """A move's key poses (an earlier key-pose fit in fit_dir) refined from a second performance of the same move
    seen from another angle: each key's moment in the second performance (map_time on the matching events), that
    view's camera searched with the pose held, then the pose and both cameras fitted to both views at once (one set
    of joints, a camera per view), kept per key only where it matches both views better than before. Every other
    frame is eased between the keys. Returns (fit over every frame of performance 1, fitter, report)."""
    pal = json.load(open(args.palette)) if args.palette else F.PALETTE
    F.use_palette(pal)
    t0 = time.time()
    P = json.load(open(os.path.join(fit_dir, 'params.json')))
    K = json.load(open(os.path.join(fit_dir, 'keyposes.json')))
    params = {int(i): np.array(v, float) for i, v in P['frames'].items()}
    idx = sorted(params)
    keys = [int(k) for k in K['keys']]
    keys2 = [map_time(i, anchors1, anchors2) for i in keys]
    log(f'key poses {keys} -> second view frames {keys2}')
    encs1 = {i: F.classify(frames[i], pal) for i in keys}
    encs2 = {i: F.classify(frames[i], pal) for i in keys2}
    v1 = View(frames, encs1, keys, fps, args.res)
    v2 = View(frames, encs2, keys2, fps, args.res)
    v1.lite = v2.lite = True
    f1, f2 = F.Fitter(), F.Fitter()
    J = np.stack([params[i][NC:] for i in keys])
    C1 = np.stack([params[i][:5] for i in keys])
    # View 2's camera per key, the pose held: a full search on the first, then each from the last.
    C2 = np.zeros_like(C1)
    seen2 = []
    for n, i in enumerate(keys2):
        p2 = init_camera_for(f2, encs2[i], J[n], keep_scale=True) if 0 <= i < len(frames) else None
        seen2.append(p2 is not None)
        C2[n] = p2[:5] if p2 is not None else C1[n]
    log(f'second view cameras found ({time.time() - t0:.0f} s); body shown in {sum(seen2)} of {len(keys2)}')
    persp = float(params[keys[0]][5])
    fit = Fit([v1, v2], J, [C1, C2], [persp, persp], [f1.light, f2.light])
    for n in range(len(keys)):
        v1.window(keys[n], fit.params_of(0, n), f1)
        if seen2[n]:
            v2.window(keys2[n], fit.params_of(1, n), f2)
    def both(n):
        a = float(agreement(fit, 0, keys[n], fit.J.detach()[[n]], (fit.cams[0][[n]] * CAM_UNIT).detach())[0])
        b = float(agreement(fit, 1, keys2[n], fit.J.detach()[[n]], (fit.cams[1][[n]] * CAM_UNIT).detach())[0])
        return a, b
    report = []
    for n in range(len(keys)):
        if not seen2[n]:
            log(f'key {keys[n]}: the body is not shown in the second view (frame {keys2[n]}); kept as fitted')
            continue
        before = both(n)
        keep = (fit.J.detach().clone(), [c.detach().clone() for c in fit.cams])
        opt = torch.optim.Adam([{'params': [fit.J], 'lr': 0.8}, {'params': fit.cams, 'lr': 0.08}])
        best = (sum(before), fit.J[n].detach().clone(), fit.cams[0][n].detach().clone(), fit.cams[1][n].detach().clone())
        for st in range(steps):
            sig = 1.2 * (0.4 / 1.2) ** (st / max(1, steps - 1))
            opt.zero_grad()
            stride = 2 if st < steps - 8 else 1
            loss = fit.loss_frames(0, [n], sig, stride)[0] + fit.loss_frames(1, [n], sig, stride)[0] + fit.regular() * 0.1
            loss.backward()
            opt.step()
            if st % 8 == 7 or st == steps - 1:
                v1.window(keys[n], fit.params_of(0, n), f1)
                v2.window(keys2[n], fit.params_of(1, n), f2)
                b1, b2 = both(n)
                s_ = b1 + b2
                # Better on both together, never at the first view's cost.
                if s_ > best[0] and b1 >= before[0] - 0.02:
                    best = (s_, fit.J[n].detach().clone(), fit.cams[0][n].detach().clone(), fit.cams[1][n].detach().clone())
        with torch.no_grad():
            fit.J.copy_(keep[0])
            fit.cams[0].copy_(keep[1][0])
            fit.cams[1].copy_(keep[1][1])
            fit.J[n], fit.cams[0][n], fit.cams[1][n] = best[1], best[2], best[3]
        v1.window(keys[n], fit.params_of(0, n), f1)
        v2.window(keys2[n], fit.params_of(1, n), f2)
        after = both(n)
        report.append((keys[n], keys2[n], before, after))
        log(f'key {keys[n]} / {keys2[n]}: view 1 {before[0]:.2f} -> {after[0]:.2f}, view 2 {before[1]:.2f} -> {after[1]:.2f}')
    PK = {keys[n]: fit.params_of(0, n) for n in range(len(keys))}
    out = np.zeros((len(idx), F.NP))
    for m, i in enumerate(idx):
        if i in PK:
            out[m] = PK[i]
            continue
        b = int(np.searchsorted(keys, i))
        ka, kb = keys[max(0, b - 1)], keys[min(len(keys) - 1, b)]
        f = 0.0 if ka == kb else (i - ka) / (kb - ka)
        f = f * f * (3 - 2 * f)
        d = PK[kb] - PK[ka]
        d[NC:] = (d[NC:] + 180) % 360 - 180
        # Between keys the first view's own camera (it moves), the pose eased.
        out[m] = params[i].copy()
        out[m][NC:] = PK[ka][NC:] + f * d[NC:]
    log(f'two-view fit in {time.time() - t0:.0f} s')
    view = View(frames, {i: F.classify(frames[i], pal) for i in idx[:1]}, idx, fps, args.res)
    full = Fit([view], out[:, NC:], [out[:, :5]], [persp], [f1.light])
    full.view2 = {'keys2': keys2, 'params2': {keys2[n]: fit.params_of(1, n) for n in range(len(keys))}}
    return full, f1, report


def run(views_in, args, log=print):
    """views_in: [(frames, idx, fps)]; returns (fit, fitter_per_view)."""
    pal = json.load(open(args.palette)) if args.palette else F.PALETTE
    F.use_palette(pal)
    views, fitters = [], []
    for frames, idx, fps in views_in:
        encs = {i: F.classify(frames[i], pal) for i in idx}
        views.append(View(frames, encs, idx, fps, args.res))
    # View 1's first frame: the search fitter's full start (camera, pose, light).
    f0 = F.Fitter()
    t = time.time()
    p0, _ = f0.init(views[0].encs[views[0].idx[0]], tuple(float(x) for x in args.at.split(',')) if args.at else None, args.flip)
    log(f'start pose found ({time.time() - t:.0f} s)')
    fitters.append(f0)
    T = len(views[0].idx)
    J0 = np.tile(p0[NC:], (T, 1))
    cams0 = [np.tile(p0[:5], (T, 1))]
    persp, lights = [float(p0[5])], [f0.light]
    for v in range(1, len(views)):
        fv = F.Fitter()
        pv = init_camera_for(fv, views[v].encs[views[v].idx[0]], p0[NC:], args.flip2)
        fitters.append(fv)
        cams0.append(np.tile(pv[:5], (len(views[v].idx), 1)))
        persp.append(float(pv[5]))
        lights.append(fv.light)
        log(f'view {v + 1} camera found: yaw {pv[0]:.0f}, pitch {pv[1]:.0f}')
    fit = Fit(views, J0, cams0, persp, lights)
    for v in range(len(views)):
        warm_start(fit, v, fitters[v], log)
    joint_refine(fit, args.steps, fitters, log)
    return fit, fitters


def selftest(args):
    """Synthetic: the self-test's known motion as two shaded takes; fit with one take, then both."""
    import r6_fit_selftest as ST
    pal = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'r6_palettes', 'six.json')))
    F.use_palette(pal)
    args.palette = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'r6_palettes', 'six.json')
    truth1 = ST.motion(250, 12)
    truth2 = truth1.copy()
    truth2[:, 0] = 70
    truth2[:, 1] = 18
    light = ST.LIGHT
    frames1 = [np.array(F.draw_frame(p, light)) for p in truth1]
    frames2 = [np.array(F.draw_frame(p, light)) for p in truth2]
    idx = list(range(len(truth1)))
    results = {}
    for name, vin in (('one take (side)', [(frames1, idx, 50)]), ('two takes (both sides)', [(frames1, idx, 50), (frames2, idx, 50)])):
        t = time.time()
        fit, _ = run(vin, args, log=lambda s: None)
        P = np.stack([fit.params_of(0, k) for k in idx])
        e = (P[:, NC:] - truth1[:, NC:] + 180) % 360 - 180
        e[:, 1] = (P[:, NC + 1] - P[0, NC + 1]) - (truth1[:, NC + 1] - truth1[0, NC + 1])
        results[name] = (np.abs(e).mean(0), time.time() - t)
    names = [f'{b}.{"xyz"[ax]}' for b, ax in F.JOINTS]
    print(f'{"joint":15s}' + ''.join(f'{k:>26s}' for k in results))
    for n, row in zip(names, zip(*[r[0] for r in results.values()])):
        print(f'{n:15s}' + ''.join(f'{v:26.1f}' for v in row))
    print(f'{"mean":15s}' + ''.join(f'{r[0].mean():26.1f}' for r in results.values()))
    print(f'{"seconds":15s}' + ''.join(f'{r[1]:26.0f}' for r in results.values()))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('video', nargs='?')
    ap.add_argument('--range', default=None)
    ap.add_argument('--warmup', type=int, default=None)
    ap.add_argument('--view2', default=None)
    ap.add_argument('--range2', default=None)
    ap.add_argument('--flip2', action='store_true')
    ap.add_argument('--clip', default=None)
    ap.add_argument('--preview', default=None)
    ap.add_argument('--merge', action='store_true')
    ap.add_argument('--speed', type=float, default=1.0)
    ap.add_argument('--tol', type=float, default=3.0)
    ap.add_argument('--at', default=None)
    ap.add_argument('--flip', action='store_true')
    ap.add_argument('--palette', default=None)
    ap.add_argument('--fps', type=float, default=None)
    ap.add_argument('--res', type=int, default=None, help='fitting window size (default 112; 64 in fast mode)')
    ap.add_argument('--steps', type=int, default=400)
    ap.add_argument('--out', default=None)
    ap.add_argument('--selftest', action='store_true')
    ap.add_argument('--keyframes', action='store_true')
    ap.add_argument('--two-view', default=None, help='refine an earlier --keyposes fit (its --out dir) from a second performance')
    ap.add_argument('--anchors', default=None, help='two-view: frames of matching events in performance 1 (start,hit,end)')
    ap.add_argument('--anchors2', default=None, help='two-view: the same events in performance 2')
    ap.add_argument('--find-moves', action='store_true', help='list where moves are performed (from idle to idle)')
    ap.add_argument('--keyposes', type=int, default=0, help='fit only up to this many key poses, ease the rest')
    ap.add_argument('--quick-start', action='store_true', help='a quicker start search')
    ap.add_argument('--fast', action='store_true', help='footage with one colour per part: quick tracking, in parallel')
    ap.add_argument('--workers', type=int, default=4)
    ap.add_argument('--fast-steps', type=int, default=25)
    ap.add_argument('--redo', type=float, default=0.85, help='fast mode: full limb measuring below this agreement')
    ap.add_argument('--fps-step', type=int, default=1, help='fast mode: fit every n-th frame of the video')
    ap.add_argument('--width', type=int, default=960, help='fast mode: shrink frames to this width')
    a = ap.parse_args()
    if a.find_moves:
        pal = json.load(open(a.palette)) if a.palette else F.PALETTE
        F.use_palette(pal)
        frames, fps = F.frames_of(a.video, a.fps, width=480)
        for m0, m1 in find_moves(frames, pal, fps):
            print(f'move: frames {m0}-{m1} ({m0 / fps:.2f}-{m1 / fps:.2f} s, {(m1 - m0) / fps:.2f} s)')
        return
    if a.res is None:
        a.res = 64 if a.fast else 112
    if a.selftest:
        return selftest(a)
    if a.fast or a.keyposes or a.two_view:
        # Fast and key-pose modes load only the frames they fit (every --fps-step-th; only the --range when given),
        # shrunk to --width.
        only = tuple(int(x) for x in a.range.split(':')) if (a.range and a.keyposes) else None
        frames, fps = F.frames_of(a.video, a.fps, every=a.fps_step, width=a.width, only=only)
    else:
        frames, fps = F.frames_of(a.video, a.fps)
    lo, hi = (int(x) for x in a.range.split(':')) if a.range else (0, len(frames) - 1)
    first = min(lo, a.warmup) if a.warmup is not None else lo
    vin = [(frames, list(range(first, hi + 1)), fps)]
    if a.view2:
        f2, fps2 = F.frames_of(a.view2, a.fps)
        lo2, hi2 = (int(x) for x in a.range2.split(':')) if a.range2 else (0, len(f2) - 1)
        vin.append((f2, list(range(lo2, hi2 + 1)), fps2))
    if a.two_view:
        an1 = [int(x) for x in a.anchors.split(',')]
        an2 = [int(x) for x in a.anchors2.split(',')]
        fit, f0, report = run_two_view_keys(frames, fps, a.two_view, an1, an2, a)
        fitters = [f0]
        lo = fit.views[0].idx[0]
        if a.out:
            os.makedirs(a.out, exist_ok=True)
            json.dump({'report': [[k1, k2, list(b), list(c)] for k1, k2, b, c in report],
                       'view2': {str(k): [round(float(x), 3) for x in v] for k, v in fit.view2['params2'].items()}},
                      open(os.path.join(a.out, 'two_view.json'), 'w'))
    elif a.keyposes:
        fit, f0, keys, agree = run_keyposes(frames, list(range(first, hi + 1)), fps, a)
        fitters = [f0]
        if a.out:
            os.makedirs(a.out, exist_ok=True)
            json.dump({'keys': [int(x) for x in keys], 'agreement': [round(float(x), 3) for x in agree]},
                      open(os.path.join(a.out, 'keyposes.json'), 'w'))
    elif a.fast:
        idx_f = list(range(first, hi + 1))
        fit, f0, agree = run_fast(frames, idx_f, fps, a)
        fitters = [f0]
        lo = idx_f[0]
        if a.out:
            os.makedirs(a.out, exist_ok=True)
            json.dump({'frames': idx_f, 'agreement': [round(float(x), 3) for x in agree]},
                      open(os.path.join(a.out, 'agreement.json'), 'w'))
    elif a.keyframes:
        fit, f0, keep, agree = run_keyframes(frames, vin[0][1], fps, a)
        fitters = [f0]
        json.dump({'kept': [int(x) for x in np.nonzero(keep)[0]], 'agreement': [round(float(x), 3) for x in agree]},
                  open(os.path.join(a.out or os.path.join(F.ROOT, 'build', 'fit', os.path.splitext(os.path.basename(a.video))[0]), 'keyframes.json')
                       if os.path.isdir(a.out or '') else os.devnull, 'w'))
    else:
        fit, fitters = run(vin, a)
    out = a.out or os.path.join(F.ROOT, 'build', 'fit', os.path.splitext(os.path.basename(a.video))[0])
    os.makedirs(out, exist_ok=True)
    clip = a.clip or ('preview/' + a.preview if a.preview else None)
    view = fit.views[0]
    ks = [k for k, i in enumerate(view.idx) if i >= lo]
    idx = [view.idx[k] for k in ks]
    params = {view.idx[k]: fit.params_of(0, k) for k in range(len(view.idx))}
    json.dump({'fps': fps, 'frames': {i: [round(float(x), 3) for x in params[i]] for i in idx},
               'params': F.CAM + [f'{b}.{"xyz"[ax]}' for b, ax in F.JOINTS]}, open(os.path.join(out, 'params.json'), 'w'))
    if clip:
        times = np.array([(i - lo) * 1000.0 / fps / a.speed for i in idx])
        P = np.stack([params[i] for i in idx])
        keep = os.path.join(out, 'clip.json') if clip.startswith('preview/') else None
        path, n = F.write_clip(clip, times, P, a.tol, a.merge, os.path.basename(a.video) + f' frames {lo}-{hi}', keep)
        print(f'{path}: {n} keys, {int(times[-1])} ms')
        fitters[0].res = 96
        F.write_conversion(frames, params, idx, times, clip, fitters[0], out, os.path.basename(a.video) + f' {lo}-{hi}')
        if a.view2:
            v2 = fit.views[1]
            out2 = out + '_view2'
            os.makedirs(out2, exist_ok=True)
            p2 = {i: fit.params_of(1, k) for k, i in enumerate(v2.idx)}
            t2 = np.array([k * times[-1] / max(1, len(v2.idx) - 1) for k in range(len(v2.idx))])
            fitters[1].res = 96
            F.write_conversion(v2.frames, p2, v2.idx, t2, clip, fitters[1], out2, os.path.basename(a.view2) + ' (view 2)')


if __name__ == '__main__':
    main()
