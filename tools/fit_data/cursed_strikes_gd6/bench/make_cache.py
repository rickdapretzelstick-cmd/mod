# Cache for the tracking benchmark: the idle frame fitted (camera turn and tilt, light) and tracked every frame up to
# the frame before the benchmark starts, with the repo's tools. Usage: make_cache.py VIDEO NAME IDLE LAST
import sys, os, json, time, pickle, argparse, numpy as np, cv2
sys.path.insert(0, '/home/user/mod/tools')
import r6_fit as F, r6_fit_torch as T
vid, name, idle, last = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
PAL = '/home/user/mod/tools/palettes/side_colours.json'
pal = json.load(open(PAL)); F.use_palette(pal)
cap = cv2.VideoCapture(vid)
def grab(n):
    cap.set(cv2.CAP_PROP_POS_FRAMES, n); ok, f = cap.read(); return f[..., ::-1].copy()
a = argparse.Namespace(palette=PAL, res=112, at=None, flip=False, quick_start=False)
t = time.time()
fi, f0, _, agi = T.run_keyframes([grab(idle)], [0], 30, a, log=lambda *x: None)
p = fi.params_of(0, 0)
cam_lock = [float(p[0]), float(p[1])]
light = (fi.lights[0][0].numpy(), fi.lights[0][1])
print(f'idle {idle}: {agi[0]:.3f} in {time.time()-t:.0f} s', flush=True)
for n in range(idle + 1, last + 1):
    rgb = grab(n)
    p, ag, how = T.track_frame(rgb, F.classify(rgb, pal), p, cam_lock, light, f0)
    print(f'{n} {how} {ag:.3f}', flush=True)
pickle.dump({'video': vid, 'idle': idle, 'start': last, 'p': p, 'cam_lock': cam_lock, 'light': light, 'fitter': f0},
            open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'cache', name + '.pkl'), 'wb'))
print('cached', name)
