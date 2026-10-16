# One command from a recording to a mod clip: footage of the R6 avatar doing a move, from a still idle frame before it
# to one after it, in; a player clip (build/fit/<CLIP>/clip.json) and its viewer conversion out. Nothing in it is tuned
# to one recording: every step reads only the footage (and the avatar's palette, tools/palettes/side_colours.json).
#   python3 tools/r6_convert.py VIDEO IDLE_START IDLE_END CLIP [--view NAME] [--out DIR] [--net NET.pt] [--viewer]
# IDLE_START / IDLE_END: frame numbers (or seconds, written 3.4s) where the avatar stands still before and after.
# Steps, each skipped when its output is already there (re-run to resume; --fresh to redo):
#   1 track      r6_track_rep.py forwards and backwards from the idle frames (side by side)
#   2 cands      r6_global.py candidates: several poses per frame from the footage alone
#   3 partfit    r6_partfit.py: the part network's proposals, then the whole-clip pick (Viterbi)
#   4 offsets    r6_offsets.py: joints may come away from the body (unlimited, as the footage shows)
#   5 limbpass   r6_limbpass.py: per body part, whole-clip choice that stops 90-180 deg flips the footage doesn't ask for
#   6 clip       r6_oversee.py clip: every frame keyed, Euler angles continuous (no smoothing), viewer conversion
#   7 viewer     (with --viewer) tools/build_anim_viewer.py
# Logs: OUT/<step>.log. Summary: OUT/summary.json (agreement per stage).
import sys, os, json, subprocess, argparse, time
import cv2

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)


def frame_of(s, fps):
    return int(round(float(s[:-1]) * fps)) if s.endswith('s') else int(s)


def run(cmd, log, env=None):
    e = dict(os.environ, OMP_NUM_THREADS='1')
    e.update(env or {})
    t = time.time()
    print('  $', ' '.join(cmd), flush=True)
    with open(log, 'w') as f:
        r = subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT, env=e, cwd=ROOT)
    if r.returncode:
        tail = open(log).read().splitlines()[-15:]
        sys.exit(f'step failed (exit {r.returncode}), see {log}:\n' + '\n'.join(tail))
    return time.time() - t


def last_json(log, tag):
    for line in reversed(open(log).read().splitlines()):
        if line.startswith(tag + ' '):
            return json.loads(line[len(tag) + 1:])
    return None


def main():
    ap = argparse.ArgumentParser(description='recording -> mod clip')
    ap.add_argument('video')
    ap.add_argument('idle_start')
    ap.add_argument('idle_end')
    ap.add_argument('clip', help='clip name (build/fit/<clip>/clip.json)')
    ap.add_argument('--view', default=None, help='a short name for this take (default: the clip name)')
    ap.add_argument('--out', default=None, help='work folder (default build/convert/<clip>)')
    ap.add_argument('--net', default=os.path.join(ROOT, 'build', 'synth', 'partnet2.pt'))
    ap.add_argument('--rounds', type=int, default=3, help='limb pass rounds')
    ap.add_argument('--viewer', action='store_true', help='rebuild build/anim_viewer.html at the end')
    ap.add_argument('--fresh', action='store_true', help='redo every step')
    a = ap.parse_args()
    video = os.path.abspath(a.video)
    cap = cv2.VideoCapture(video)
    fps, nframes = cap.get(cv2.CAP_PROP_FPS) or 30.0, int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    s, e = frame_of(a.idle_start, fps), frame_of(a.idle_end, fps)
    if not (0 <= s < e < nframes):
        sys.exit(f'idle frames {s}..{e} not inside the video (0..{nframes - 1})')
    if not os.path.exists(a.net):
        sys.exit(f'part network {a.net} missing (tools/r6_partnet.py train makes it)')
    name = a.view or a.clip
    out = os.path.abspath(a.out or os.path.join(ROOT, 'build', 'convert', a.clip))
    os.makedirs(out, exist_ok=True)
    py = sys.executable
    T = lambda f: os.path.join(TOOLS, f)
    done = lambda p: os.path.exists(p) and not a.fresh
    print(f'{video}: frames {s}-{e} ({(e - s) / fps:.2f} s at {fps:.2f} fps) -> {a.clip}', flush=True)

    track = os.path.join(out, 'track')
    os.makedirs(track, exist_ok=True)
    if not all(done(os.path.join(track, f'{name}_{d}.json')) for d in ('fwd', 'bwd')):
        print('1/6 tracking forwards and backwards', flush=True)
        t = time.time()
        procs = [subprocess.Popen([py, T('r6_track_rep.py'), video, name, str(s), str(e), d, track],
                                  stdout=open(os.path.join(out, f'track_{d}.log'), 'w'), stderr=subprocess.STDOUT,
                                  env=dict(os.environ, OMP_NUM_THREADS='1'), cwd=ROOT) for d in ('fwd', 'bwd')]
        for p, d in zip(procs, ('fwd', 'bwd')):
            if p.wait():
                sys.exit(f'tracking {d} failed, see {out}/track_{d}.log')
        print(f'  {time.time() - t:.0f} s', flush=True)

    cands = os.path.join(out, 'cands')
    if not done(os.path.join(cands, f'{name}_cands.json')):
        print('2/6 candidate poses per frame', flush=True)
        os.makedirs(cands, exist_ok=True)
        print(f'  {run([py, T("r6_global.py"), "candidates", video, track, name, cands], os.path.join(out, "cands.log")):.0f} s')

    pf = os.path.join(out, 'partfit')
    if not done(os.path.join(pf, f'{name}_global.json')):
        print('3/6 part network proposals + whole-clip pick', flush=True)
        os.makedirs(pf, exist_ok=True)
        print(f'  {run([py, T("r6_partfit.py"), video, os.path.join(cands, f"{name}_cands.json"), a.net, pf, "0.5"], os.path.join(out, "partfit.log")):.0f} s')

    offs = os.path.join(out, f'{name}_offsets.json')
    if not done(offs):
        print('4/6 joint offsets', flush=True)
        print(f'  {run([py, T("r6_offsets.py"), video, os.path.join(pf, f"{name}_global.json"), os.path.join(pf, f"{name}_cands.json"), a.net, offs], os.path.join(out, "offsets.log")):.0f} s')

    final = os.path.join(out, f'{name}_final.json')
    if not done(final):
        print('5/6 whole-clip limb pass', flush=True)
        print(f'  {run([py, T("r6_limbpass.py"), video, offs, os.path.join(pf, f"{name}_cands.json"), a.net, final, str(a.rounds)], os.path.join(out, "limbpass.log")):.0f} s')

    print('6/6 clip + viewer conversion', flush=True)
    run([py, T('r6_oversee.py'), 'clip', video, final, name, a.clip], os.path.join(out, 'clip.log'))
    if a.viewer:
        run([py, T('build_anim_viewer.py')], os.path.join(out, 'viewer.log'))

    summary = {'video': video, 'idle': [s, e], 'fps': fps, 'clip': os.path.join(ROOT, 'build', 'fit', a.clip, 'clip.json'),
               'partfit': last_json(os.path.join(out, 'partfit.log'), 'PARTFIT') if os.path.exists(os.path.join(out, 'partfit.log')) else None,
               'offsets': last_json(os.path.join(out, 'offsets.log'), 'OFFSETS') if os.path.exists(os.path.join(out, 'offsets.log')) else None,
               'limbpass': last_json(os.path.join(out, 'limbpass.log'), 'LIMBPASS') if os.path.exists(os.path.join(out, 'limbpass.log')) else None}
    json.dump(summary, open(os.path.join(out, 'summary.json'), 'w'), indent=1)
    print(json.dumps(summary, indent=1))


if __name__ == '__main__':
    main()
