# The template clothing's printed letters (F, B, L, R, U, D, FRONT, BACK) as a pose hint. A face's colour says which
# side of a box faces the camera but not which way that side is turned (a cyan arm top looks the same rotated 90 deg,
# an arm twisted half round shows the same colours); its letter does. Each box face's letter is learnt once from frames
# whose pose is trusted (the idle frames, the clean frames), as a darkness pattern in the face's own coordinates; a
# candidate pose is then judged by whether, drawn with those letters, the dark strokes land where the footage has them.
#   python3 tools/r6_glyph.py learn VIDEO PATH.json OUT.npz [MIN_AGREEMENT]   letters from a path's trusted frames
#   python3 tools/r6_glyph.py check VIDEO PATH.json OUT.npz                    does the letter score prefer the
#                                                                              path's pose to its twisted/flipped twins
#   python3 tools/r6_glyph.py select VIDEO CANDS.json OUT.npz OUTDIR [W]     whole-clip pick on colours + W x letters
import sys, os, json
import numpy as np, cv2
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F
import r6_motion as M

TEX = 32          # texture cells per face side
MIN_AREA = 350    # footage px a face must cover to learn from or judge it
DARK = 0.62       # a pixel darker than this share of its neighbourhood's brightness is a printed stroke


def darkness(rgb):
    """Printed marks, 0..1: pixels much darker than their neighbourhood (the pants' black letters and outlines) or much
    paler (the shirt's white letters: saturation drops)."""
    hsv = cv2.cvtColor(np.ascontiguousarray(rgb), cv2.COLOR_RGB2HSV)
    out = 0
    for ch in (2, 1):
        x = hsv[..., ch]
        ref = cv2.dilate(cv2.medianBlur(x, 9), np.ones((5, 5), np.uint8)).astype(np.float32)
        d = np.clip((DARK * ref - x.astype(np.float32)) / (DARK * ref + 1e-3) * 3, 0, 1)
        out = np.maximum(out, d * (ref > 60))  # (a pale background has no saturation to lose)
    return out


def faces_of(p, shape):
    """Visible faces under params p, near last: [(key (box n, face index), 4 footage-px corners, area)] and a label
    image of which face is in front (index into that list, -1 none)."""
    world = F.pose_world(p)
    view, proj = F.camera_of(p)
    persp = p[5]
    out = []
    for n, bone, lo, hi, part in M.BOXES:
        if part == 'head':
            continue  # the head carries the face decal, not a letter
        L = M.LOCAL[n]
        w = F.MODEL_TO_WORLD @ (world[bone] @ np.c_[L, np.ones(8)].T)[:3]
        v = view @ w
        uv = proj(v)
        rotw = F.MODEL_TO_WORLD @ world[bone][:3, :3]
        for fi, (idx, normal) in enumerate(F.FACES):
            nc = view @ (rotw @ normal)
            centre = v[:, idx].mean(1)
            toward = -centre + np.array([0, 0, 1 / persp]) if persp > 1e-6 else np.array([0, 0, 1.0])
            if nc @ toward <= 0:
                continue
            q = uv[idx]
            d1, d2 = q[2] - q[0], q[3] - q[1]
            area = 0.5 * abs(d1[0] * d2[1] - d1[1] * d2[0])
            out.append((centre[2], (n, fi), q.astype(np.float32), area))
    out.sort(key=lambda f: f[0])
    lab = np.full(shape, -1, np.int32)
    for i, (_, _, q, _) in enumerate(out):
        cv2.fillConvexPoly(lab, np.round(q * 4).astype(np.int32), i, lineType=cv2.LINE_8, shift=2)
    return [(key, q, a) for _, key, q, a in out], lab


SQ = np.float32([[0, 0], [TEX, 0], [TEX, TEX], [0, TEX]])


def unwarp(img, q):
    return cv2.warpPerspective(img, cv2.getPerspectiveTransform(q, SQ), (TEX, TEX), flags=cv2.INTER_AREA)


def learn(video, path_json, out, min_ag=0.95):
    tr = json.load(open(path_json))
    rows = {int(k): v for k, v in tr['frames'].items()}
    cap = cv2.VideoCapture(video)
    acc, wsum = {}, {}
    used = 0
    for k in sorted(rows):
        if rows[k]['agreement'] < min_ag:
            continue
        cap.set(cv2.CAP_PROP_POS_FRAMES, k)
        ok, f = cap.read()
        rgb = f[..., ::-1]
        d = darkness(rgb)
        p = np.array(rows[k]['params'], float)
        faces, lab = faces_of(p, d.shape)
        for i, (key, q, area) in enumerate(faces):
            if area < MIN_AREA:
                continue
            vis = (lab == i).astype(np.float32)
            if vis.sum() < 0.8 * area:
                continue  # partly hidden: its letter would be cut
            t = unwarp(d * vis, q)
            w = area
            acc[key] = acc.get(key, 0) + w * t
            wsum[key] = wsum.get(key, 0) + w
        used += 1
    tex = {f'{n}_{fi}': acc[(n, fi)] / wsum[(n, fi)] for (n, fi) in acc}
    np.savez_compressed(out, **tex)
    print(f'{out}: {len(tex)} faces learnt from {used} frames')
    return tex


def load(path):
    z = np.load(path)
    return {tuple(int(x) for x in k.split('_')): z[k] for k in z.files}


def score(rgb_dark, ps, tex):
    """Per params vector: how well its faces' learnt letters match the strokes seen (correlation over the pixels of
    every visible face that has a learnt letter, weighted by area). None-able: 0 when no face can be judged."""
    out = []
    H, W = rgb_dark.shape
    for p in ps:
        faces, lab = faces_of(np.asarray(p, float), (H, W))
        num = den = 0.0
        for i, (key, q, area) in enumerate(faces):
            if key not in tex or area < MIN_AREA:
                continue
            m = lab == i
            if m.sum() < 0.5 * area:
                continue
            # the letter drawn into the footage frame where this face is seen
            x0, y0 = np.floor(q.min(0)).astype(int)
            x1, y1 = np.ceil(q.max(0)).astype(int) + 1
            x0, y0, x1, y1 = max(0, x0), max(0, y0), min(W, x1), min(H, y1)
            if x1 <= x0 or y1 <= y0:
                continue
            Mx = cv2.getPerspectiveTransform(SQ, q - np.float32([x0, y0]))
            pred = cv2.warpPerspective(tex[key].astype(np.float32), Mx, (x1 - x0, y1 - y0), flags=cv2.INTER_LINEAR)
            sub = m[y0:y1, x0:x1]
            a, b = pred[sub], rgb_dark[y0:y1, x0:x1][sub]
            if a.std() < 1e-3 or b.std() < 1e-3:
                continue
            c = float(np.corrcoef(a, b)[0, 1])
            num += c * area
            den += area
        out.append(num / den if den else 0.0)
    return out


def twins(p):
    """A pose's look-alikes the colours struggle with: each limb twisted a quarter / half round about its length."""
    out = []
    for b in ('rightArm', 'leftArm', 'rightThigh', 'leftThigh'):
        for t in (90, 180, -90):
            q = np.array(p, float)
            j = F.JI[(b, 1)]
            q[j] = (q[j] + t + 180) % 360 - 180
            out.append((f'{b} twisted {t:+d}', q))
    return out


def check(video, path_json, tex_path):
    tr = json.load(open(path_json))
    rows = {int(k): v for k, v in tr['frames'].items()}
    tex = load(tex_path)
    cap = cv2.VideoCapture(video)
    wins = total = 0
    for k in sorted(rows)[::4]:
        cap.set(cv2.CAP_PROP_POS_FRAMES, k)
        ok, f = cap.read()
        d = darkness(f[..., ::-1])
        p = np.array(rows[k]['params'], float)
        tw = twins(p)
        sc = score(d, [p] + [q for _, q in tw], tex)
        beaten = [(l, round(s, 2)) for (l, _), s in zip(tw, sc[1:]) if s >= sc[0]]
        wins += len(tw) - len(beaten)
        total += len(tw)
        print(f'{k}: path {sc[0]:.2f} (colour {rows[k]["agreement"]:.2f}); twins beating it: {beaten}', flush=True)
    print(f'path pose beats its twisted twins {wins}/{total}')



def select(video, cands_json, tex_path, outdir, w=0.1, top=4):
    """Each frame's best `top` colour candidates plus every limb twisted a quarter/half round (the look-alikes letters
    tell apart), all scored on colours (one common window) and on letters; the whole-clip path then maximises colour
    + w x letters less motion (r6_global's Viterbi). -> OUTDIR/<name>_cands.json, <name>_global.json + report."""
    import r6_oversee as O, r6_global as G
    data = json.load(open(cands_json))
    tr = json.load(open(data['track']))
    clip = O.Clip(video, tr)
    tex = load(tex_path)
    for k, cands in data['frames'].items():
        if cands[0]['from'] == 'idle':
            continue
        best = sorted(cands, key=lambda c: -c['agreement'])[:top]
        new = list(best)
        for c in best:
            for lab, q in twins(np.array(c['params'], float)):
                new.append({'from': c['from'] + ' / ' + lab, 'params': [float(x) for x in q]})
        ps = [np.array(c['params'], float) for c in new]
        col = G.common_scores(clip, int(k), ps)
        let = score(darkness(clip.rgb(int(k))), ps, tex)
        for c, a, l in zip(new, col, let):
            c['colour'], c['letters'] = a, l
            c['agreement'] = a + w * l
        data['frames'][k] = new
    for k, cands in data['frames'].items():
        if cands[0]['from'] == 'idle':
            cands[0]['colour'], cands[0]['letters'] = cands[0]['agreement'], 0.0
    os.makedirs(outdir, exist_ok=True)
    json.dump(data, open(os.path.join(outdir, f'{data["name"]}_cands.json'), 'w'))
    G.select(outdir, data['name'])
    path = json.load(open(os.path.join(outdir, f'{data["name"]}_global.json')))['frames']
    pick = {k: next(c for c in data['frames'][k] if c['params'] == path[k]['params']) for k in path}
    moving = [k for k in pick if data['frames'][k][0]['from'] != 'idle']
    twisted = [int(k) for k in moving if ' / ' in pick[k]['from']]
    rep = {'letter_weight': w, 'colour_mean': round(float(np.mean([pick[k]['colour'] for k in moving])), 4),
           'letters_mean': round(float(np.mean([pick[k]['letters'] for k in moving])), 4),
           'colour_ge_0.90': int(sum(pick[k]['colour'] >= 0.9 for k in moving)),
           'frames_given_a_twisted_limb': sorted(twisted)}
    for k in path:  # the path's agreement as the colour agreement alone (what every other report uses)
        path[k]['agreement'], path[k]['letters'] = pick[k]['colour'], pick[k]['letters']
    full = json.load(open(os.path.join(outdir, f'{data["name"]}_global.json')))
    full['frames'] = path
    full['letters'] = rep
    json.dump(full, open(os.path.join(outdir, f'{data["name"]}_global.json'), 'w'))
    print('LETTERS', json.dumps(rep), flush=True)
    return rep


if __name__ == '__main__':
    a = sys.argv[1:]
    if a and a[0] == 'learn':
        learn(a[1], a[2], a[3], float(a[4]) if len(a) > 4 else 0.95)
    elif a and a[0] == 'check':
        check(*a[1:4])
    elif a and a[0] == 'select':
        select(a[1], a[2], a[3], a[4], float(a[5]) if len(a) > 5 else 0.1)
    else:
        sys.exit('learn VIDEO PATH.json OUT.npz [MIN_AG] | check VIDEO PATH.json OUT.npz')
