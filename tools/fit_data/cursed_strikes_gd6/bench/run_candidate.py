# Runs a candidate tracker (tools/r6_fit_torch.py's track_frame from TOOLS) over a cached sequence; times each frame
# (classification + tracking; decoding not counted). Usage: run_candidate.py TOOLS CACHE FIRST LAST STEP OUT.json
import sys, os, json, time, pickle
tools, name, first, last, step, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), sys.argv[6]
sys.path.insert(0, tools)
import numpy as np, cv2
import r6_fit as F, r6_fit_torch as T
B = os.path.dirname(os.path.abspath(__file__))
c = pickle.load(open(os.path.join(B, 'cache', name + '.pkl'), 'rb'))
pal = json.load(open(os.path.join(tools, 'palettes', 'side_colours.json'))); F.use_palette(pal)
cap = cv2.VideoCapture(c['video'])
p = c['p']
rows = []
for n in range(first, last + 1, step):
    cap.set(cv2.CAP_PROP_POS_FRAMES, n); ok, f = cap.read(); rgb = f[..., ::-1].copy()
    t = time.time()
    enc = F.classify(rgb, pal)
    t1 = time.time()
    p, ag, how = T.track_frame(rgb, enc, p, c['cam_lock'], c['light'], c['fitter'])
    dt = time.time() - t
    rows.append({'frame': n, 'seconds': round(dt, 2), 'classify_s': round(t1 - t, 2), 'how': how, 'own_agreement': round(float(ag), 3),
                 'params': [float(x) for x in p]})
    print(f'{n}: {dt:.1f} s ({how}), own agreement {ag:.3f}', flush=True)
json.dump({'tools': tools, 'cache': name, 'rows': rows}, open(out, 'w'))
