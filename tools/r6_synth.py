# Synthetic training pictures of the template-clothed R6 rig, with the right answer attached: which body part each
# pixel belongs to. Random poses (the tracked ones jittered, and fully random ones) under the footage's own cameras
# and light, the template's side colours and printed letters, composited over real footage background with the real
# character painted out (so the arena, the Dummy and real trails beside it are there and are "not body"), plus fake
# red strike trails, smoke, motion blur, a second Dummy-coloured rig in front or behind, and colour/noise jitter.
#   python3 tools/r6_synth.py sheet VIDEO OUT.png         a sheet of samples (picture | labels)
#   python3 tools/r6_synth.py make VIDEO OUT.npz N         N samples for training (r6_partnet.py)
import sys, os, json, glob
import numpy as np, cv2
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F
import r6_motion as M
import r6_glyph as Gl

HERE = os.path.dirname(os.path.abspath(__file__))
pal = json.load(open(os.path.join(HERE, 'palettes', 'side_colours.json'))); F.use_palette(pal)
NC = len(F.CAM)
S = 112  # picture side (the tracker's window is 112 px too)
PART_ID = {'torso': 1, 'head': 2, 'face': 2, 'rightArm': 3, 'leftArm': 4, 'rightLeg': 5, 'leftLeg': 6}
NPART = 7
SHIRT = {'torso', 'rightArm', 'leftArm'}
TRACKS = sorted(glob.glob(os.path.join(HERE, 'fit_data', 'cursed_strikes_gd6', 'tracks', '*.json')))
GLYPHS = os.path.join(HERE, 'fit_data', 'cursed_strikes_gd6', 'oversee', 'left_glyphs.npz')


def load_tracks():
    out = []
    for t in TRACKS:
        tr = json.load(open(t))
        for k, r in tr['frames'].items():
            out.append((int(k), np.array(r['params'], float), (np.array(tr['light'][0]), float(tr['light'][1]))))
    return out


class Synth:
    def __init__(self, video, seed=0):
        self.rng = np.random.default_rng(seed)
        self.cap = cv2.VideoCapture(video)
        self.rows = load_tracks()
        self.tex = Gl.load(GLYPHS) if os.path.exists(GLYPHS) else {}
        self.frames = {}

    def frame(self, k):
        if k not in self.frames:
            if len(self.frames) > 60:
                self.frames.pop(next(iter(self.frames)))
            self.cap.set(cv2.CAP_PROP_POS_FRAMES, k)
            ok, f = self.cap.read()
            self.frames[k] = f[..., ::-1].copy()
        return self.frames[k]

    def pose(self):
        r = self.rng
        k, p, light = self.rows[r.integers(len(self.rows))]
        p = p.copy()
        if r.random() < 0.3:  # fully random joints within the limits
            for j, (b, ax) in enumerate(F.JOINTS):
                lo, hi = F.LIMITS[(b, ax)]
                p[NC + j] = r.uniform(lo, hi) if b != 'hips' or ax == 1 else r.uniform(-60, 60)
        else:  # a tracked pose jittered
            p[NC:] += r.normal(0, 20, len(F.JOINTS))
        p[F.JI[('hips', 1)]] = r.uniform(-180, 180)
        for (b, ax), (lo, hi) in F.LIMITS.items():
            p[F.JI[(b, ax)]] = np.clip(p[F.JI[(b, ax)]], lo, hi)
        ld = light[0] + r.normal(0, 0.25, 3)
        return k, p, (ld / np.linalg.norm(ld), float(np.clip(light[1] + r.normal(0, 0.08), 0.3, 0.8)))

    def background(self, k, p_real, box):
        """The footage around where the real character stood at frame k, the character painted out."""
        x0, y0, w = box
        rgb = self.frame(k)
        Mx = np.float32([[S / w, 0, -x0 * S / w], [0, S / w, -y0 * S / w]])
        bg = cv2.warpAffine(rgb, Mx, (S, S), flags=cv2.INTER_AREA, borderMode=cv2.BORDER_REFLECT)
        mask = np.zeros((S, S), np.uint8)
        for poly, cls, sh in F.faces(p_real, F.DEFAULT_LIGHT):
            q = (np.array(poly) - [x0, y0]) * S / w
            cv2.fillConvexPoly(mask, np.round(q * 4).astype(np.int32), 255, shift=2)
        mask = cv2.dilate(mask, np.ones((13, 13), np.uint8))  # (the fit is off by a few px at the edges)
        return cv2.inpaint(bg, mask, 5, cv2.INPAINT_TELEA)

    def draw_rig(self, img, lab, p, light, box, colours=None, label=True):
        """The rig drawn over img (and its parts into lab) far to near, with the template's letters and the pants'
        dark outlines; colours: a part -> rgb override (a Dummy)."""
        x0, y0, w = box
        k = S / w
        world = F.pose_world(p)
        view, proj = F.camera_of(p)
        persp = p[5]
        faces = []
        for n, (bone, lo, hi, part) in enumerate(F.PARTS):
            if part == 'face' or (n == 7):
                continue
            L = M.box_corners_local(part, lo, hi)
            wc = F.MODEL_TO_WORLD @ (world[bone] @ np.c_[L, np.ones(8)].T)[:3]
            v = view @ wc
            uv = (proj(v) - [x0, y0]) * k
            rotw = F.MODEL_TO_WORLD @ world[bone][:3, :3]
            for fi, (idx, normal) in enumerate(F.FACES):
                nw = rotw @ normal
                nc = view @ nw
                centre = v[:, idx].mean(1)
                toward = -centre + np.array([0, 0, 1 / persp]) if persp > 1e-6 else np.array([0, 0, 1.0])
                if nc @ toward <= 0:
                    continue
                shade = light[1] + (1 - light[1]) * max(0.0, float(nw @ light[0]))
                faces.append((centre[2], n, fi, part, uv[idx].astype(np.float32), shade))
        faces.sort(key=lambda f: f[0])
        for _, n, fi, part, q, shade in faces:
            if colours:
                col = np.array(colours.get(part, (40, 40, 45)), float)
            else:
                col = np.array(F.SHOW[F.SIDE_CLASS[part][fi]], float)
            col = np.clip(col * (0.55 + 0.6 * shade), 0, 255)
            pts = np.round(q * 8).astype(np.int32)
            face = np.zeros((S, S), np.uint8)
            cv2.fillConvexPoly(face, pts, 255, lineType=cv2.LINE_AA, shift=3)
            a = face.astype(np.float32)[..., None] / 255
            layer = np.empty_like(img, dtype=np.float32)
            layer[:] = col
            if not colours and (n, fi) in self.tex:
                t = self.tex[(n, fi)]
                t = t / max(float(t.max()), 1e-3)
                tw = cv2.warpPerspective(t.astype(np.float32), cv2.getPerspectiveTransform(Gl.SQ, q), (S, S))[..., None]
                ink = np.array((250, 250, 250) if part in SHIRT else (15, 15, 20), float)
                layer = layer * (1 - 0.85 * tw) + ink * 0.85 * tw
            img[:] = img * (1 - a) + layer * a
            if not colours and part not in SHIRT:  # the pants' printed dark outline
                cv2.polylines(img, [pts], True, (20, 20, 25), max(1, int(round(k * 0.6))), cv2.LINE_AA, shift=3)
            if label is not None:
                lab[face > 127] = PART_ID[part] if label else 0

    def trail(self, img):
        r = self.rng
        layer = np.zeros((S, S), np.float32)
        for _ in range(r.integers(1, 4)):
            p0 = r.uniform(-20, S + 20, 2)
            d = r.normal(0, 1, 2); d /= np.linalg.norm(d)
            L = r.uniform(30, 160)
            bend = r.normal(0, 25, 2)
            pts = np.array([p0 + d * L * t + bend * np.sin(np.pi * t) for t in np.linspace(0, 1, 24)])
            cv2.polylines(layer, [np.round(pts * 4).astype(np.int32)], False, 1.0, int(r.uniform(1, 5)), cv2.LINE_AA, shift=2)
        glow = cv2.GaussianBlur(layer, (0, 0), r.uniform(2, 6))
        core = np.clip(layer, 0, 1)[..., None]
        g = np.clip(glow * r.uniform(1.5, 3), 0, 1)[..., None]
        red = np.array((255, 25, 40), float)
        img[:] = img * (1 - 0.8 * g) + red * 0.8 * g
        img[:] = img * (1 - core) + np.array((255, 220, 220), float) * core

    def smoke(self, img):
        r = self.rng
        m = np.zeros((S, S), np.float32)
        for _ in range(r.integers(1, 6)):
            cv2.circle(m, tuple(int(x) for x in r.uniform(0, S, 2)), int(r.uniform(8, 40)), 1.0, -1)
        m = cv2.GaussianBlur(m, (0, 0), 8)[..., None] * r.uniform(0.2, 0.6)
        img[:] = img * (1 - m) + 235 * m

    def sample(self):
        r = self.rng
        k, p, light = self.pose()
        real = next(q for kk, q, _ in self.rows if kk == k)
        # the window: around the drawn rig, sized as the tracker's (0.62 x its extent, at least 0.7 x 30 x scale)
        uv = np.concatenate([np.array(poly) for poly, _, _ in F.faces(p, light)])
        lo, hi = uv.min(0), uv.max(0)
        half = max(0.7 * 30 * p[2], 0.62 * float((hi - lo).max())) * r.uniform(0.9, 1.3)
        c = 0.5 * (lo + hi) + r.normal(0, 0.08 * half, 2)
        box = (c[0] - half, c[1] - half, 2 * half)
        # the background: footage where the real character stood (it painted out), same footage scale
        rc = real[3:5] - [0, 6 * real[2]] + r.normal(0, 0.2 * half, 2)
        bg = self.background(k, real, (rc[0] - half, rc[1] - half, 2 * half)).astype(np.float32)
        img, lab = bg.copy(), np.zeros((S, S), np.uint8)
        dummy = {'head': (230, 205, 172), 'rightArm': (230, 205, 172), 'leftArm': (230, 205, 172), 'torso': (30, 30, 34),
                 'rightLeg': (45, 50, 60), 'leftLeg': (45, 50, 60)}
        d_front = r.random() < 0.5
        if r.random() < 0.35 and not d_front:  # a Dummy-coloured rig behind
            q, _ = self.pose()[1], None
            q[3:5] = p[3:5] + r.normal(0, 25 * p[2], 2)
            self.draw_rig(img, lab, q, light, box, colours=dummy, label=None)
        # the character, motion-blurred as its own layer
        ch, clab = img.copy(), lab.copy()
        self.draw_rig(ch, clab, p, light, box)
        if r.random() < 0.4:
            n = int(r.integers(3, 12))
            ker = np.zeros((n, n), np.float32)
            a = r.uniform(0, np.pi)
            cv2.line(ker, (int(n / 2 - np.cos(a) * n / 2), int(n / 2 - np.sin(a) * n / 2)),
                     (int(n / 2 + np.cos(a) * n / 2), int(n / 2 + np.sin(a) * n / 2)), 1.0, 1)
            ker /= max(ker.sum(), 1)
            ch = cv2.filter2D(ch, -1, ker)
        img, lab = ch, clab
        if r.random() < 0.35 and d_front:  # a Dummy-coloured rig in front: what it covers is not the character
            q = self.pose()[1]
            q[3:5] = p[3:5] + r.normal(0, 25 * p[2], 2)
            self.draw_rig(img, lab, q, light, box, colours=dummy, label=False)
        if r.random() < 0.6:
            self.trail(img)
        if r.random() < 0.3:
            self.smoke(img)
        img = img * r.uniform(0.85, 1.15) + r.normal(0, 4, img.shape)
        img = np.clip(img, 0, 255).astype(np.uint8)
        if r.random() < 0.5:
            ok, enc = cv2.imencode('.jpg', img[..., ::-1], [cv2.IMWRITE_JPEG_QUALITY, int(r.uniform(50, 95))])
            img = cv2.imdecode(enc, 1)[..., ::-1]
        return img, lab


LAB_COL = np.array([(30, 30, 30), (220, 60, 60), (240, 200, 40), (60, 200, 60), (60, 120, 240), (200, 80, 220),
                    (60, 220, 220)], np.uint8)


def sheet(video, out, n=24):
    s = Synth(video, 1)
    tiles = []
    for _ in range(n):
        img, lab = s.sample()
        tiles.append(np.hstack([img, LAB_COL[lab]]))
    rows = [np.hstack(tiles[i:i + 4]) for i in range(0, n, 4)]
    cv2.imwrite(out, cv2.resize(np.vstack(rows), None, fx=1.5, fy=1.5, interpolation=cv2.INTER_NEAREST)[..., ::-1])


def make(video, out, n, seed=0):
    s = Synth(video, seed)
    X = np.zeros((n, S, S, 3), np.uint8)
    Y = np.zeros((n, S, S), np.uint8)
    for i in range(n):
        X[i], Y[i] = s.sample()
        if i % 1000 == 0:
            print(out, i, flush=True)
    np.savez(out, X=X, Y=Y)


if __name__ == '__main__':
    a = sys.argv[1:]
    if a and a[0] == 'sheet':
        sheet(a[1], a[2])
    elif a and a[0] == 'make':
        make(a[1], a[2], int(a[3]), int(a[4]) if len(a) > 4 else 0)
    else:
        sys.exit('sheet VIDEO OUT.png | make VIDEO OUT.npz N [SEED]')
