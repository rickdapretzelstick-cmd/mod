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

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F  # noqa: E402

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
CORNERS = torch.tensor([[[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
                        for _, lo, hi, _ in F.PARTS], dtype=DT)  # (parts, 8, 3)
FACE_IDX = torch.tensor([f[0] for f in F.FACES])  # (6, 4)
FACE_N = torch.tensor(np.stack([f[1] for f in F.FACES]), dtype=DT)  # (6, 3)
M2W = torch.tensor([-1.0, -1.0, 1.0], dtype=DT)
# Cameras are optimised in these units (degrees, 1/100 of the scale, pixels) so one step size suits every entry.
CAM_UNIT = torch.tensor([1.0, 1.0, 0.01, 1.0, 1.0], dtype=DT)


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


def geometry(J, cam, persp, light):
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


def rasterise(quads, depth, vis, shade, pix, sigma, classes, ncls):
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
    alpha = torch.sigmoid(inside / sigma) * vis[:, None] * (area.abs() > 0.5).float()[:, None]
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
    return prob, sh


class View:
    """One take: its frames' colour maps in fixed windows, and its camera per frame."""

    def __init__(self, frames, encs, idx, fps, res):
        self.frames, self.idx, self.fps, self.res = frames, idx, fps, res
        self.encs = encs
        self.windows = {}

    def window(self, i, p, fitter):
        """The frame's window (footage px per pixel) around the body's colours; class/shade targets in it."""
        fitter.res = self.res
        roi = fitter.roi(p, self.encs[i])
        x0, y0, k, w, h = roi
        prep = fitter.prepare(fitter.view(self.encs[i], roi))
        obs, shade = prep[0], prep[3]
        gx, gy = np.meshgrid((np.arange(w) + 0.5) / k + x0, (np.arange(h) + 0.5) / k + y0)
        self.windows[i] = (torch.tensor(np.stack([gx, gy], -1).reshape(-1, 2), dtype=DT),
                           torch.tensor(obs.reshape(-1).astype(np.int64)), torch.tensor(shade.reshape(-1), dtype=DT), 1 / k)


def frame_loss(prob, sh, obs, shade, ncls):
    """Per-sample loss: cross-entropy of each pixel's class (floor counts less, UNKNOWN not at all), plus shading."""
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
    return ce + 0.5 * sl


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

    def loss_frames(self, v, ks, sigma_px):
        view = self.views[v]
        J = torch.stack([self.joints_at(v, k) for k in ks])
        cam = self.cams[v][ks] * CAM_UNIT
        quads, depth, vis, shade = geometry(J, cam, self.persp[v], self.lights[v])
        pix = torch.stack([view.windows[view.idx[k]][0] for k in ks])
        obs = torch.stack([view.windows[view.idx[k]][1] for k in ks])
        tsh = torch.stack([view.windows[view.idx[k]][2] for k in ks])
        scale = torch.tensor([view.windows[view.idx[k]][3] for k in ks], dtype=DT)
        prob, sh = rasterise(quads, depth, vis, shade, pix, (sigma_px * scale)[:, None, None], self.classes, self.ncls)
        return frame_loss(prob, sh, obs, tsh, self.ncls)

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
    """Joint vectors trying each limb's swing (and the body's lean), and the left/right swaps."""
    out = [j.clone()]
    for limb in ('rightArm', 'leftArm', 'rightThigh', 'leftThigh'):
        ix, iz = F.JI[(limb, 0)] - NC, F.JI[(limb, 2)] - NC
        sgn = 1 if limb.startswith('right') else -1
        for x in (-160, -120, -80, -40, 0, 40):
            for z in (0, 45, 90):
                q = j.clone()
                q[ix], q[iz] = x, sgn * z
                out.append(q)
    for x in (-60, -30, 0, 30, 60):
        for z in (-45, 0, 45):
            q = j.clone()
            q[F.JI[('hips', 0)] - NC], q[F.JI[('hips', 2)] - NC] = x, z
            out.append(q)
    for r, l in (('rightArm', 'leftArm'), ('rightThigh', 'leftThigh')):
        q = j.clone()
        q[F.JI[(r, 0)] - NC], q[F.JI[(l, 0)] - NC] = j[F.JI[(l, 0)] - NC], j[F.JI[(r, 0)] - NC]
        for ax in (1, 2):
            q[F.JI[(r, ax)] - NC], q[F.JI[(l, ax)] - NC] = -j[F.JI[(l, ax)] - NC], -j[F.JI[(r, ax)] - NC]
        out.append(q)
    return torch.stack(out)


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
                sub = slice(None, None, 3)  # every third pixel is plenty to rank candidates
                losses = []
                for c0 in range(0, len(cands), 24):
                    sl = slice(c0, c0 + 24)
                    n = quads[sl].shape[0]
                    prob, sh = rasterise(quads[sl], depth[sl], vis[sl], shade[sl], w[0][sub].expand(n, -1, -1), 1.5 * w[3],
                                         fit.classes, fit.ncls)
                    losses.append(frame_loss(prob, sh, w[1][sub].expand(n, -1), w[2][sub].expand(n, -1), fit.ncls))
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
        loss = adam(groups, 60, closure, 2.0, 0.7)
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
        sig = 1.5 * (0.5 / 1.5) ** (s / max(1, steps - 1))
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
