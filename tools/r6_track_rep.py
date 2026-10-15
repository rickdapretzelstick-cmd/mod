# One repetition of a move tracked from an idle frame, forwards (fwd: from IDLE_S to IDLE_E) or backwards (bwd: from
# IDLE_E to IDLE_S): per frame the params, agreement and how well each part is seen. Usage:
#   OMP_NUM_THREADS=1 python3 tools/r6_track_rep.py VIDEO NAME IDLE_S IDLE_E fwd|bwd OUTDIR
# (one thread per job: several run side by side, and maths libraries' own thread pools would fight over the CPUs)
import sys, os, json, time, argparse
import numpy as np, torch, cv2
torch.set_num_threads(1); cv2.setNumThreads(1)
sys.path.insert(0, '/home/user/mod/tools')
import r6_fit as F, r6_fit_torch as T
vid, name, a_, b_, dirn, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5], sys.argv[6]
PAL = '/home/user/mod/tools/palettes/side_colours.json'
pal = json.load(open(PAL)); F.use_palette(pal)
cap = cv2.VideoCapture(vid)
def grab(n):
    cap.set(cv2.CAP_PROP_POS_FRAMES, n); ok, f = cap.read(); return f[..., ::-1].copy()
order = list(range(a_, b_ + 1)) if dirn == 'fwd' else list(range(b_, a_ - 1, -1))
args = argparse.Namespace(palette=PAL, res=112, at=None, flip=False, quick_start=False)
t0 = time.time()
# The idle frame: keyframe fit, searched again from the standing pose when that is implausible (fit_idle).
p, ag_idle, light, f0 = T.fit_idle(grab(order[0]), args)
agi = [ag_idle]
if os.environ.get('R6_CALIBRATE', '1') != '0':
    # The still camera calibrated on the idle frame to the pixel (turn, tilt, scale, perspective): tracking locks it
    # (calibrate_idle keeps the uncalibrated camera when the result is not a plausible standing fit).
    rgb0 = grab(order[0])
    p, agi[0] = T.calibrate_idle(rgb0, F.classify(rgb0, pal), p, light, f0, log=print)
cam_lock = [float(p[0]), float(p[1]), float(p[2])]
rows = {order[0]: {'params': [float(x) for x in p], 'agreement': float(agi[0]), 'how': 'idle', 'parts': None}}
print(f'{name} {dirn} idle {order[0]}: {agi[0]:.3f} ({time.time()-t0:.0f} s)', flush=True)
for n in order[1:]:
    t = time.time()
    rgb = grab(n)
    p, ag, how, fit = T.track_frame(rgb, F.classify(rgb, pal), p, cam_lock, light, f0, detail=True)
    m, s = T.part_agreement(fit)
    rows[n] = {'params': [float(x) for x in p], 'agreement': float(ag), 'how': how, 'parts': [m.tolist(), s.tolist()],
               'seconds': round(time.time() - t, 2)}
    print(f'{name} {dirn} {n}: {ag:.3f} {how} {time.time()-t:.1f} s', flush=True)
json.dump({'name': name, 'dir': dirn, 'video': vid, 'idle': [a_, b_], 'cam_lock': cam_lock,
           'light': [light[0].tolist(), float(light[1])], 'parts': [q[3] for q in F.PARTS], 'frames': rows},
          open(os.path.join(out, f'{name}_{dirn}.json'), 'w'))
print(f'{name} {dirn} done in {(time.time()-t0)/60:.1f} min', flush=True)
