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
FACE_IDX = torch.tensor([f[0] for f in F.FACES])  # (6, 4)
FACE_N = torch.tensor(np.stack([f[1] for f in F.FACES]), dtype=DT)  # (6, 3)
M2W = torch.tensor([-1.0, -1.0, 1.0], dtype=DT)
# Cameras are optimised in these units (degrees, 1/100 of the scale, pixels) so one step size suits every entry.
CAM_UNIT = torch.tensor([1.0, 1.0, 0.01, 1.0, 1.0], dtype=DT)
RECT_W = 1.5  # weight of each box's own-rectangle overlap


def rotm(a):
    """(..., 3) degrees -> (..., 3, 3) = Rz Ry Rx, as client.anim.rig.Rig."""
    x, y, z = torch.deg2rad(a).unbind(-1)
    o, n = torch.ones_like(x), torch.zeros_like(x)
    sh = x.shape + (3, 3)
    rx = torch.stack([o, n, n, n, x.cos(), -x.sin(), n, x.sin(), x.cos()], -1).reshape(sh)
    ry = torch.stack([y.cos(), n, y.sin(), n, o, n, -y.sin(), n, y.cos()], -1).reshape(sh)
    rz = torch.stack([z.cos(), -z.sin(), n, z.sin(), z.cos(), n, n, n, o], -1).reshape(sh)
    return rz @ ry @ rx


def part_classes():
    return torch.tensor([F.CLASS_OF[part] for _, _, _, part in F.PARTS])


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
    X = (WR.unsqueeze(2) @ CORNERS.unsqueeze(0).unsqueeze(-1)).squeeze(-1) + WT.unsqueeze(2)  # (B, P, 8, 3) model
    X = X * M2W
    zeros = torch.zeros_like(cam[:, 0])
    view = rotm(torch.stack([cam[:, 1], zeros, zeros], -1)) @ rotm(torch.stack([zeros, cam[:, 0], zeros], -1))  # (B, 3, 3)
    V = (view[:, None, None] @ X.unsqueeze(-1)).squeeze(-1)  # (B, P, 8, 3)
    f = cam[:, 2, None, None] / torch.clamp(1 - V[..., 2] * persp, min=0.2)
    uv = torch.stack([cam[:, 3, None, None] + f * V[..., 0], cam[:, 4, None, None] - f * V[..., 1]], -1)  # (B, P, 8, 2)
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
    a = quads
    b = torch.roll(quads, -1, 2)
    e = b - a  # (B, Fc, 4, 2)
    elen = torch.clamp(e.norm(dim=-1), min=1e-3)
    area = (a[..., 0] * b[..., 1] - b[..., 0] * a[..., 1]).sum(-1)  # twice the signed area (B, Fc)
    orient = torch.where(area >= 0, 1.0, -1.0).detach()
    rel = pix[:, :, None, None, :] - a[:, None]  # (B, N, Fc, 4, 2)
    cross = (e[:, None, ..., 0] * rel[..., 1] - e[:, None, ..., 1] * rel[..., 0]) / elen[:, None]
    inside = (cross * orient[:, None, :, None]).min(-1).values  # (B, N, Fc) signed distance to the face's outline
    # A face seen nearly edge-on is a sliver whose short edges no longer bound it (it would smear into a line): fade
    # faces out below ~10 px of area.
    solid = torch.clamp(area.abs() / 20.0, 0, 1)
    alpha = torch.sigmoid(inside / sigma) * (vis * solid)[:, None]
    order = torch.argsort(depth.detach(), dim=1, descending=True)  # nearest first
    alpha = torch.gather(alpha, 2, order[:, None].expand_as(alpha))
    trans = torch.cumprod(torch.cat([torch.ones_like(alpha[..., :1]), 1 - alpha[..., :-1]], -1), -1)
    w = alpha * trans  # each face's share of the pixel
    floor = trans[..., -1] * (1 - alpha[..., -1])
    cls = torch.gather(classes.expand(order.shape[0], -1), 1, order)  # (B, Fc)
    onehot = torch.nn.functional.one_hot(cls, ncls).to(DT)  # (B, Fc, C)
    prob = w @ onehot  # (B, N, C)
    prob = torch.cat([prob[..., :1] + floor.unsqueeze(-1), prob[..., 1:]], -1)
    sh = (w * torch.gather(shade, 1, order)[:, None]).sum(-1) / (w.sum(-1) + 1e-6)
    if parts:
        pid = torch.gather(PART_OF_FACE.expand(order.shape[0], -1), 1, order)
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
        self.targets = {}  # frame -> (boxes' target masks (boxes, N), which boxes have one (boxes,))

    def window(self, i, p, fitter):
        """The frame's window (footage px per pixel) around the body's colours; class/shade targets in it."""
        fitter.res = self.res
        roi = fitter.roi(p, self.encs[i])
        x0, y0, k, w, h = roi
        prep = fitter.prepare(fitter.view(self.encs[i], roi))
        obs, shade = prep[0].copy(), prep[3]
        # Only the body's own cluster of colours is body (a lamp or an effect of the same colour further off is floor).
        body_cls = sorted({F.CLASS_OF[part] for _, _, _, part in F.PARTS})
        stray = np.isin(obs, body_cls) & ~RR.body_cluster(obs, {c: 1 for c in body_cls})
        obs[stray] = 0
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
        for x1, y1, x2, y2 in segs:
            d = np.array([x2 - x1, y2 - y1], float)
            L = np.linalg.norm(d)
            if L < 3:
                continue
            nrm = np.array([-d[1], d[0]]) / L
            t = np.linspace(0.1, 0.9, 9)
            pts = np.stack([x1 + d[0] * t, y1 + d[1] * t], 1)
            cls_sides = {}  # colour -> which side of the edge it is on (+1 along nrm, -1 against)
            for sgn in (1, -1):
                q = np.clip(np.round(pts + sgn * 2.0 * nrm).astype(int), 0, side_n - 1)
                vals = obs[q[:, 1], q[:, 0]]
                vals = vals[np.isin(vals, list(counts))]
                if len(vals) >= 5:
                    c_, n_ = np.unique(vals, return_counts=True)
                    if n_.max() >= 5:
                        cls_sides.setdefault(int(c_[np.argmax(n_)]), sgn)
            if cls_sides:
                coloured.append((np.array([x1, y1, x2, y2], float), cls_sides, nrm))
        self.coloured_edges[i] = coloured
        self.targets.pop(i, None)


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
        self.classes = part_classes().repeat_interleave(6)
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

    def loss_frames(self, v, ks, sigma_px):
        view = self.views[v]
        J = torch.stack([self.joints_at(v, k) for k in ks])
        cam = self.cams[v][ks] * CAM_UNIT
        quads, depth, vis, shade = geometry(J, cam, self.persp[v], self.lights[v])
        pix = torch.stack([view.windows[view.idx[k]][0] for k in ks])
        obs = torch.stack([view.windows[view.idx[k]][1] for k in ks])
        tsh = torch.stack([view.windows[view.idx[k]][2] for k in ks])
        scale = torch.tensor([view.windows[view.idx[k]][3] for k in ks], dtype=DT)
        prob, sh, pprob = rasterise(quads, depth, vis, shade, pix, (sigma_px * scale)[:, None, None], self.classes,
                                    self.ncls, parts=True)
        loss = frame_loss(prob, sh, obs, tsh, self.ncls, view.res)
        # Every box onto its own rectangle: soft overlap of the box's share of each pixel with the rectangle's colour.
        for b, k in enumerate(ks):
            i = view.idx[k]
            if i not in view.targets:
                self.assign(v, k)
            tgt, has = view.targets[i]
            if has.any():
                p = pprob[b][:, has].T  # (boxes with a rectangle, N)
                t = tgt[has]
                inter = (p * t).sum(-1)
                union = p.sum(-1) + t.sum(-1) - inter
                loss = loss + torch.cat([loss.new_zeros(b), (RECT_W * (1 - inter / (union + 1e-6))).sum()[None],
                                         loss.new_zeros(len(ks) - b - 1)])
        return loss

    def regular(self):
        J = self.J
        r = (torch.relu(J - HI) ** 2 + torch.relu(LO - J) ** 2).sum() * 0.01
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


def agreement(fit, v, i, J, cam):
    """The fit's rule: drawn hard (the boxes at their exact R6 sizes), how many pixels have the footage's colour.
    Agreeing body pixels over all body pixels of either (a box on floor, or body colour with no box on it, both count
    against; pixels hidden behind something are left out). 1.0 is a perfect fit. Batched: (B,)."""
    view = fit.views[v]
    pix, obs, _, scale = view.windows[i]
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
            known = (o != F.UNKNOWN) & ~view.border[i]
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
END_RULE = os.environ.get('R6_END_RULE', '1') != '0'


def judge(fit, v, i, J, cam, detail=False, only=None):
    """The rule every candidate pose is held to (boxes at their exact R6 sizes), higher is better (0..1):
    - colour: every box seeks its own colour: full credit on it, half where it covers another part's colour (parts
      overlap), none on floor; shading is not judged (any shade of the right colour counts); and every body-coloured
      pixel should be under some box of that colour;
    - edges (the steadfast rule): every straight edge of the footage with a body colour along it must be covered by
      the outline of a box of that colour, and every box's end must meet such an edge;
    weighted side edges 5, ends 1, colour 1;
    - and the hard rule: each visible limb has its hand end or its shoulder end flush on an edge of its colour (a pose
      breaking it scores below every pose keeping it).
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
            hidx = cv2.convexHull(pts.astype(np.float32), returnPoints=False).reshape(-1)
            for ci, cap in enumerate((CAP_FAR, CAP_NEAR)):
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
                hit |= d <= 1.5
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
                if d_ang > math.radians(8):
                    continue
                mid = np.array([(x1 + x2) / 2, (y1 + y2) / 2])
                if np.sign((centre - mid) @ nrm) != cls_sides[cls]:
                    continue
                el2 = float(e @ e)
                u = np.clip(((P - (x1, y1)) @ e) / el2, 0, 1)
                d = np.hypot(*(P - (np.array([x1, y1]) + u[:, None] * e)).T)
                hit |= d <= 1.5
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
        total = (5 * edge + endr + colour) / 7 - (broken if END_RULE else 0)  # breaking it loses to keeping it
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
    cam_now = lambda: (fit.cams[v][[k]] * CAM_UNIT).detach()
    best = (float(judge(fit, v, i, fit.joints_at(v, k).detach()[None], cam_now())[0]),
            fit.J[k].detach().clone(), fit.cams[v][k].detach().clone())
    for s in range(steps):
        sig = sigma0 * (sigma1 / sigma0) ** (s / max(1, steps - 1))
        opt.zero_grad()
        loss = fit.loss_frames(v, [k], sig)[0] + fit.regular() * 0.1
        loss.backward()
        opt.step()
        if s % every == every - 1 or s == steps - 1:
            a = float(judge(fit, v, i, fit.joints_at(v, k).detach()[None], cam_now())[0])
            if a > best[0]:
                best = (a, fit.J[k].detach().clone(), fit.cams[v][k].detach().clone())
    with torch.no_grad():
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
    torso_score = lambda tc: float(judge(fit, v, i, tc[0][None], tc[1][None], only=('torso',))[0])
    ranked = [torsos[0]] + sorted(torsos[1:], key=lambda tc: -torso_score(tc))[:8]
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
                            opts += twisted(J, bone, xd, zd)
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


def init_camera_for(fitter, enc, joints, flip=False):
    """A take's first camera with the pose already known (view 2): search the camera only."""
    best = None
    for cyaw in range(0, 360, 20):
        for cpitch in range(0, 90, 15):
            p = np.zeros(F.NP)
            fg = enc >= 1000
            lab, n = F.ndimage.label(F.ndimage.binary_dilation(fg, iterations=4))
            sizes = F.ndimage.sum(fg, lab, range(1, n + 1))
            cy, cx = F.ndimage.center_of_mass(lab == (1 + int(np.argmax(sizes))))
            ys = np.nonzero(lab == (1 + int(np.argmax(sizes))))[0]
            p[:6] = cyaw + (180 if flip else 0), cpitch, max(10, np.ptp(ys)) / 31, cx, cy, 0.005
            p[NC:] = joints
            roi = fitter.roi(p, enc)
            c = fitter.cost(p, fitter.prepare(fitter.view(enc, roi)), roi, None)
            if best is None or c < best[0]:
                best = (c, p)
    sig = list(fitter.init_sigma[:NC]) + [0] * NJ
    p = fitter.cem(best[1], sig, enc, None, iters=30, pop=48)
    fitter.fit_light(p, enc)
    return p


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
    ap.add_argument('--res', type=int, default=112)
    ap.add_argument('--steps', type=int, default=400)
    ap.add_argument('--out', default=None)
    ap.add_argument('--selftest', action='store_true')
    a = ap.parse_args()
    if a.selftest:
        return selftest(a)
    frames, fps = F.frames_of(a.video, a.fps)
    lo, hi = (int(x) for x in a.range.split(':')) if a.range else (0, len(frames) - 1)
    first = min(lo, a.warmup) if a.warmup is not None else lo
    vin = [(frames, list(range(first, hi + 1)), fps)]
    if a.view2:
        f2, fps2 = F.frames_of(a.view2, a.fps)
        lo2, hi2 = (int(x) for x in a.range2.split(':')) if a.range2 else (0, len(f2) - 1)
        vin.append((f2, list(range(lo2, hi2 + 1)), fps2))
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
