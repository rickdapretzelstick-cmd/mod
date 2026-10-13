#!/usr/bin/env python3
"""
2D stage of the R6 fitter: tight rectangles around each body part's colour, before any 3D pose.

Each colour of the palette is worn by a known number of parts (the noob: torso 1; yellow 3 = head and both arms;
lime 2 = the legs). Every connected area of a colour is covered by as few oriented rectangles as explain it (one
leg, or two legs touching, or an arm across the head), each rectangle fitted tight around its pixels; then the 3D fit
(tools/r6_fit_torch.py) gives every box its own rectangle to cover, instead of a colour shared by several boxes.

    rects = detect(class_map, counts)        # {class: [Rect]}, class_map from r6_fit.Fitter.prepare
    pairs = assign(part_masks, rects)        # which box covers which rectangle
"""
import numpy as np
from scipy import ndimage
from scipy.optimize import linear_sum_assignment

import cv2


class Rect:
    def __init__(self, cls, box, pixels):
        self.cls = cls
        self.box = box  # cv2 minAreaRect ((cx, cy), (w, h), angle)
        self.corners = cv2.boxPoints(box)
        self.pixels = pixels  # (N, 2) x, y of the colour pixels it explains

    def mask(self, shape, colour=None):
        """The rectangle's pixels, optionally only where its colour is (what the box must cover)."""
        m = np.zeros(shape, np.uint8)
        cv2.fillPoly(m, [np.round(self.corners).astype(np.int32)], 1)
        m = m.astype(bool)
        return m & colour if colour is not None else m


def _rect_of(pts):
    return cv2.minAreaRect(pts.astype(np.float32))


def _outside(pts, box):
    """Each point's distance outside a rectangle (0 inside)."""
    (cx, cy), (w, h), ang = box
    a = np.radians(ang)
    d = pts - (cx, cy)
    u = d[:, 0] * np.cos(a) + d[:, 1] * np.sin(a)
    v = -d[:, 0] * np.sin(a) + d[:, 1] * np.cos(a)
    return np.hypot(np.maximum(0, np.abs(u) - w / 2), np.maximum(0, np.abs(v) - h / 2))


def fit_rects(pts, n, seeds=6):
    """n oriented rectangles covering the points as tightly as possible (k-rectangles: assign each point to the
    rectangle it is least outside of -- ties to the smaller one -- and refit), best of a few starts."""
    if n == 1 or len(pts) < 8 * n:
        return [(_rect_of(pts), pts)]
    best = None
    rng = np.random.default_rng(0)
    centre = pts.mean(0)
    axis = np.linalg.svd(pts - centre, full_matrices=False)[2][0]
    for s in range(seeds):
        if s == 0:
            # Along the main axis: limbs in a row.
            t = (pts - centre) @ axis
            lab = np.clip(((t - t.min()) / (np.ptp(t) + 1e-6) * n).astype(int), 0, n - 1)
        else:
            c = pts[rng.choice(len(pts), n, replace=False)].astype(float)
            lab = np.argmin(((pts[:, None] - c[None]) ** 2).sum(-1), 1)
        for _ in range(10):
            groups = [pts[lab == k] for k in range(n)]
            if any(len(g) < 4 for g in groups):
                break
            boxes = [_rect_of(g) for g in groups]
            areas = np.array([b[1][0] * b[1][1] for b in boxes])
            d = np.stack([_outside(pts, b) for b in boxes], 1) + 1e-3 * areas[None] / max(1.0, areas.max())
            new = np.argmin(d, 1)
            if (new == lab).all():
                break
            lab = new
        groups = [pts[lab == k] for k in range(n)]
        if any(len(g) < 4 for g in groups):
            continue
        boxes = [_rect_of(g) for g in groups]
        score = sum(b[1][0] * b[1][1] for b in boxes)
        if best is None or score < best[0]:
            best = (score, list(zip(boxes, groups)))
    return best[1] if best else [(_rect_of(pts), pts)]


def body_cluster(obs, counts):
    """The body's own cluster of colours (a lamp or an effect of the same colour further off is not a part)."""
    H = obs.shape[0]
    body = np.isin(obs, list(counts))
    lab, n = ndimage.label(ndimage.binary_dilation(body, iterations=max(3, H // 16)))
    if not n:
        return np.ones_like(body)
    sizes = ndimage.sum(body, lab, range(1, n + 1))
    return lab == 1 + int(np.argmax(sizes))


def detect(obs, counts, min_frac=0.004):
    """{class: [Rect]} for a class map: each class's areas split into as many tight rectangles as they need (at most
    the number of parts wearing that colour, fewer when one rectangle already explains an area well)."""
    out = {}
    H, W = obs.shape
    cluster = body_cluster(obs, counts)
    for cls, count in counts.items():
        m = ndimage.binary_opening((obs == cls) & cluster, iterations=1)
        lab, n = ndimage.label(m)
        comps = []
        for c in range(1, n + 1):
            ys, xs = np.nonzero(lab == c)
            if len(xs) >= min_frac * H * W:
                comps.append(np.stack([xs, ys], 1))
        comps.sort(key=len, reverse=True)
        rects, left = [], count
        for k, pts in enumerate(comps):
            if left <= 0:
                break
            budget = max(1, left - (len(comps) - k - 1))  # leave one for every later area
            options = {1: fit_rects(pts, 1)}
            for j in range(2, budget + 1):
                options[j] = fit_rects(pts, j)
            # As many rectangles as clearly tighten the cover (each extra one must save 15% of the area).
            area = lambda rs: sum(b[1][0] * b[1][1] for b, _ in rs)
            use = 1
            for j in range(2, budget + 1):
                if area(options[j]) < 0.85 * area(options[use]):
                    use = j
            rects += [Rect(cls, b, g) for b, g in options[use]]
            left -= use
        out[cls] = rects
    return out


def segments(gray, mask_any, min_len):
    """Straight edges in the picture (line segment detector) near the body's colours: [(x1, y1, x2, y2)]."""
    lsd = cv2.createLineSegmentDetector()
    lines = lsd.detect(gray.astype(np.uint8))[0]
    if lines is None:
        return np.zeros((0, 4))
    lines = lines.reshape(-1, 4)
    near = ndimage.binary_dilation(mask_any, iterations=3)
    keep = []
    for x1, y1, x2, y2 in lines:
        if np.hypot(x2 - x1, y2 - y1) < min_len:
            continue
        xs = np.clip(np.linspace(x1, x2, 8).round().astype(int), 0, gray.shape[1] - 1)
        ys = np.clip(np.linspace(y1, y2, 8).round().astype(int), 0, gray.shape[0] - 1)
        if near[ys, xs].mean() > 0.7:
            keep.append((x1, y1, x2, y2))
    return np.array(keep).reshape(-1, 4)


def _strip(colour, origin, axis, normal, width, max_len):
    """The rectangle from an edge into its colour: from the edge's line (origin, along axis) across `width` toward
    normal, grown along the axis both ways for as long as the strip's cross-section is mostly that colour."""
    H, W = colour.shape
    def frac(t):
        pts = origin + axis * t + normal * np.linspace(1, width - 1, 7)[:, None]
        x, y = pts[:, 0].round().astype(int), pts[:, 1].round().astype(int)
        ok = (x >= 0) & (x < W) & (y >= 0) & (y < H)
        return colour[y[ok], x[ok]].mean() if ok.any() else 0.0
    # Grow while the cross-section is mostly the colour, stepping over gaps (dithering, a shadow) up to half a part's
    # width when the colour picks up again after them.
    gap = max(2, int(width / 2))
    def grow(sign):
        end, t = 0.0, 0.0
        while abs(t) < max_len:
            if frac(t + sign) > 0.5:
                t += sign
                end = t
                continue
            ahead = [d for d in range(2, gap + 1) if frac(t + sign * d) > 0.5]
            if not ahead:
                break
            t += sign * ahead[0]
            end = t
        return end
    lo, hi = grow(-1), grow(1)
    if hi - lo < 3:
        return None
    a, b = origin + axis * lo, origin + axis * hi
    return np.array([a, b, b + normal * width, a + normal * width], np.float32)


def edge_rects(gray, obs, cls, widths, unit, min_fill=0.6):
    """Rectangles of one colour hung on straight edges: from every edge with the colour on one side, a strip of a
    part's width (each width in `widths`, px) into the colour, as long as the colour runs; or between two parallel
    edges a part's width apart. Scored by how much of the rectangle is the colour and how much of its long sides lie on
    edges; kept greedily while they explain colour not yet explained."""
    colour = obs == cls
    if colour.sum() < 10:
        return []
    segs = segments(gray, colour, max(4, unit * 0.8))
    H, W = colour.shape
    cands = []
    for x1, y1, x2, y2 in segs:
        p1, p2 = np.array([x1, y1], float), np.array([x2, y2], float)
        axis = (p2 - p1) / max(1e-6, np.linalg.norm(p2 - p1))
        normal = np.array([-axis[1], axis[0]])
        mid = (p1 + p2) / 2
        for sgn in (1, -1):
            nrm = normal * sgn
            probe = (mid + nrm * 2).round().astype(int)
            if not (0 <= probe[0] < W and 0 <= probe[1] < H) or not colour[probe[1], probe[0]]:
                continue
            for wdt in widths:
                quad = _strip(colour, mid, axis, nrm, wdt, max_len=4 * max(widths))
                if quad is not None:
                    cands.append(quad)
    scored = []
    for quad in cands:
        m = np.zeros(colour.shape, np.uint8)
        cv2.fillPoly(m, [quad.round().astype(np.int32)], 1)
        m = m.astype(bool)
        area = m.sum()
        if area < 8:
            continue
        fill = (colour & m).sum() / area
        if fill < min_fill:
            continue
        scored.append((fill * np.sqrt(area), quad, m))
    scored.sort(key=lambda e: -e[0])
    chosen, explained = [], np.zeros_like(colour)
    for _, quad, m in scored:
        new = (m & colour & ~explained).sum()
        if new < 0.5 * (m & colour).sum() or new < 10:
            continue
        chosen.append((quad, m))
        explained |= m
    return chosen


class EdgeRect(Rect):
    def __init__(self, cls, quad, m):
        self.cls = cls
        self.corners = quad
        rect = cv2.minAreaRect(quad)
        self.box = rect
        ys, xs = np.nonzero(m)
        self.pixels = np.stack([xs, ys], 1)


def detect_edges(gray, obs, counts, unit):
    """{class: [Rect]} from straight edges (see edge_rects): limbs one stud wide (`unit` px), torso two, head ~1.2.
    Colour left unexplained by edges falls back to the blob rectangles of detect()."""
    out = {}
    obs = np.where(body_cluster(obs, counts) | ~np.isin(obs, list(counts)), obs, 0)
    blob = detect(obs, counts)
    for cls, count in counts.items():
        widths = (unit * 2, unit) if count == 1 else (unit, unit * 1.2)
        rs = [EdgeRect(cls, q, m) for q, m in edge_rects(gray, obs, cls, widths, unit)][:count]
        colour = obs == cls
        explained = np.zeros_like(colour)
        for r in rs:
            explained |= r.mask(colour.shape)
        if (colour & ~explained).sum() > 0.25 * max(1, colour.sum()) and len(rs) < count:
            for r in blob.get(cls, []):
                if (r.mask(colour.shape) & colour & ~explained).sum() > 0.5 * (r.mask(colour.shape) & colour).sum():
                    rs.append(r)
                    explained |= r.mask(colour.shape)
                if len(rs) >= count:
                    break
        out[cls] = rs
    return out


def aspect(r):
    (_, _), (w, h), _ = r.box
    return max(w, h) / max(1e-6, min(w, h))


def assign(part_masks, part_cls, rects, part_names=None):
    """Pairs (part index, Rect): within each colour, the matching of boxes to rectangles that overlaps most."""
    pairs = []
    for cls, rs in rects.items():
        parts = [i for i, c in enumerate(part_cls) if c == cls]
        if not parts or not rs:
            continue
        cost = np.zeros((len(parts), len(rs)))
        for a, i in enumerate(parts):
            pm = part_masks[i]
            for b, r in enumerate(rs):
                rm = r.mask(pm.shape)
                inter, union = (pm & rm).sum(), (pm | rm).sum()
                (cx, cy), _, _ = r.box
                ys, xs = np.nonzero(pm)
                dist = np.hypot(xs.mean() - cx, ys.mean() - cy) / max(pm.shape) if len(xs) else 1.0
                cost[a, b] = 1 - inter / max(1, union) + 0.5 * dist
                # The head is about square, limbs and torso are long: a long rectangle is not a head and back.
                if part_names is not None:
                    head = part_names[i] == 'head'
                    if head and aspect(r) > 1.6 or not head and part_names[i] != 'torso' and aspect(r) < 1.25 and r.pixels.shape[0] > 30:
                        cost[a, b] += 1.0
        ri, ci = linear_sum_assignment(cost)
        pairs += [(parts[a], rs[b]) for a, b in zip(ri, ci)]
    return pairs
