# Scores a candidate run with the frozen fitter (frozen/: the colour classes and the agreement rule as committed),
# whatever the candidate changed. Writes OUT.score.json and OUT.png (footage | colours seen | fitted rig, per frame).
import sys, os, json, pickle
B = os.path.dirname(os.path.abspath(__file__))
SCORER = os.environ.get('SCORE_TOOLS', os.path.join(B, 'frozen'))
sys.path.insert(0, SCORER)
import numpy as np, torch, cv2
import r6_fit as F, r6_fit_torch as T
from PIL import Image, ImageDraw
run = json.load(open(sys.argv[1]))
c = pickle.load(open(os.path.join(B, 'cache', run['cache'] + '.pkl'), 'rb'))
pal = json.load(open(os.path.join(SCORER, 'palettes', 'side_colours.json'))); F.use_palette(pal)
cap = cv2.VideoCapture(c['video'])
show = {i + 1: c_['show'] for i, c_ in enumerate(pal['classes'])}; show[F.UNKNOWN] = [255, 255, 255]
def img(a_, r):
    o = np.zeros((r, r, 3), np.uint8)
    for k, col in show.items(): o[a_ == k] = col
    return Image.fromarray(o).resize((168, 168), Image.NEAREST)
res, tiles = [], []
for row in run['rows']:
    n = row['frame']; p = np.array(row['params'])
    cap.set(cv2.CAP_PROP_POS_FRAMES, n); ok, f = cap.read(); rgb = f[..., ::-1].copy()
    enc = F.classify(rgb, pal)
    v = T.View([rgb], {0: enc}, [0], 30, 112); v.lite = True
    fit = T.Fit([v], p[None, T.NC:], [p[None, :5]], [float(p[5])], [c['light']])
    v.window(0, p, c['fitter'])
    J, C = fit.J.detach()[[0]], (fit.cams[0][[0]] * T.CAM_UNIT).detach()
    T.HARD_SCORE = False if hasattr(T, 'HARD_SCORE') else None
    ag = float(T.agreement(fit, 0, 0, J, C)[0])
    o_ = v.windows[0][1].numpy()
    known_share = float(((o_ > 0) & (o_ != F.UNKNOWN)).sum() / max(1, ((o_ > 0) | (o_ == F.UNKNOWN)).sum()))
    hips = [p[F.JI[('hips', a)]] for a in range(3)]
    sane = abs(hips[0]) <= 50 and abs(hips[2]) <= 50
    res.append({'frame': n, 'seconds': row['seconds'], 'how': row['how'], 'agreement': round(ag, 3), 'upright': bool(sane), 'known_share': round(known_share, 3)})
    pix, obs, _, sc = v.windows[0]
    quads, depth, vis, shade = T.geometry(J, C, fit.persp[0], fit.lights[0])
    prob, _ = T.rasterise(quads, depth, vis, shade, pix[None], 0.05 * sc, fit.classes, fit.ncls)
    drawn = prob.argmax(-1)[0].numpy().reshape(112, 112); o = obs.numpy().reshape(112, 112)
    x0, y0 = float(pix[0, 0]) - 0.5 * float(sc), float(pix[0, 1]) - 0.5 * float(sc)
    foot = Image.fromarray(rgb).crop((int(x0), int(y0), int(x0 + 112 * float(sc)), int(y0 + 112 * float(sc)))).resize((168, 168))
    t = Image.new('RGB', (3 * 170, 186), (16, 17, 21))
    for k, im in enumerate((foot, img(o, 112), img(drawn, 112))): t.paste(im, (k * 170, 18))
    ImageDraw.Draw(t).text((4, 3), f'frame {n}  {ag*100:.0f}%  {row["seconds"]:.1f} s  {row["how"]}', fill=(255, 255, 255))
    tiles.append(t)
cols = 3
sheet = Image.new('RGB', (cols * 512, ((len(tiles) + cols - 1) // cols) * 188), (0, 0, 0))
for k, t in enumerate(tiles): sheet.paste(t, ((k % cols) * 512, (k // cols) * 188))
base = os.path.splitext(sys.argv[1])[0]
sheet.save(base + '.png')
ags = [r['agreement'] for r in res]; secs = [r['seconds'] for r in res]
summ = {'frames': len(res), 'min_agreement': min(ags), 'mean_agreement': round(float(np.mean(ags)), 3),
        'frames_below_0.80': [r['frame'] for r in res if r['agreement'] < 0.80], 'max_seconds': max(secs),
        'mean_seconds': round(float(np.mean(secs)), 2), 'frames_over_10s': [r['frame'] for r in res if r['seconds'] > 10],
        'not_upright': [r['frame'] for r in res if not r['upright']], 'min_known_share': min(r['known_share'] for r in res), 'scorer': SCORER,
        'pass': min(ags) >= 0.80 and max(secs) < 10}
json.dump({'summary': summ, 'frames': res}, open(base + '.score.json', 'w'), indent=1)
print(json.dumps(summ))
