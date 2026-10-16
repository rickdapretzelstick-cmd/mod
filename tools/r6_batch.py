# A raw recording to a mod clip with nothing picked by hand: the takes found (r6_takes.py), every take converted
# (r6_convert.py, several side by side), then the main take's limb pass redone with every other take as a guide for
# what its camera can't see.
#   python3 tools/r6_batch.py VIDEO CLIP [--main N] [--jobs J] [--viewer] [--fresh]
# The takes are numbered from 0 in the order they are in the video. --main picks the one the clip is made from
# (default: the take whose conversion agrees best with its footage). Work folders: build/convert/CLIP/take<N>;
# the clip: build/fit/CLIP/clip.json. Re-run to resume: finished steps are skipped.
import sys, os, json, subprocess, argparse, shutil
from concurrent.futures import ThreadPoolExecutor

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
sys.path.insert(0, TOOLS)
import r6_takes


def convert(video, s, e, clip, out, guide=None, viewer=False, fresh=False):
    cmd = [sys.executable, os.path.join(TOOLS, 'r6_convert.py'), video, str(s), str(e), clip, '--out', out,
           '--view', clip]
    if guide:
        cmd += ['--guide', ','.join(guide)]
    if viewer:
        cmd.append('--viewer')
    if fresh:
        cmd.append('--fresh')
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, 'convert.log'), 'w') as f:
        r = subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT, cwd=ROOT)
    return r.returncode


def score(out):
    """The conversion's colour agreement with its footage (after joint offsets), or None."""
    try:
        return float(json.load(open(os.path.join(out, 'summary.json')))['offsets']['colour_after'])
    except (OSError, ValueError, KeyError, TypeError):
        return None


def main():
    ap = argparse.ArgumentParser(description='raw recording -> mod clip')
    ap.add_argument('video')
    ap.add_argument('clip')
    ap.add_argument('--main', type=int, default=None, help='take number the clip is made from (default: best fit)')
    ap.add_argument('--jobs', type=int, default=max(1, (os.cpu_count() or 2) // 4),
                    help='takes converted side by side (each uses about 2-4 cores)')
    ap.add_argument('--viewer', action='store_true', help='rebuild build/anim_viewer.html at the end')
    ap.add_argument('--fresh', action='store_true', help='redo every step')
    a = ap.parse_args()
    video = os.path.abspath(a.video)
    takes = r6_takes.find_takes(video)
    if not takes:
        sys.exit('no takes found: the avatar needs to stand still before and after each move, camera unmoved')
    base = os.path.join(ROOT, 'build', 'convert', a.clip)
    outs = [os.path.join(base, f'take{n}') for n in range(len(takes))]
    for n, (s, e) in enumerate(takes):
        print(f'take {n}: frames {s}-{e}', flush=True)

    def one(n):
        s, e = takes[n]
        rc = convert(video, s, e, f'{a.clip}_take{n}', outs[n], fresh=a.fresh)
        print(f'take {n} converted' if rc == 0 else f'take {n} failed (see {outs[n]}/convert.log)', flush=True)
        return rc
    with ThreadPoolExecutor(max_workers=max(1, a.jobs)) as pool:
        rcs = list(pool.map(one, range(len(takes))))
    ok = [n for n, rc in enumerate(rcs) if rc == 0]
    if not ok:
        sys.exit('no take converted')
    if a.main is not None:
        if a.main not in ok:
            sys.exit(f'take {a.main} did not convert')
        main_n = a.main
    else:
        main_n = max(ok, key=lambda n: score(outs[n]) or 0.0)
    guides = [outs[n] for n in ok if n != main_n]
    print(f'main take: {main_n}' + (f', guided by takes {[n for n in ok if n != main_n]}' if guides else ''), flush=True)
    # The main take again under the clip's own name: its first four steps are copied (unchanged), the limb pass redone
    # with the guides.
    out = os.path.join(base, 'main')
    if a.fresh and os.path.isdir(out):
        shutil.rmtree(out)
    if not os.path.isdir(out):
        src, name = outs[main_n], f'{a.clip}_take{main_n}'
        os.makedirs(out)
        for d in ('track', 'cands', 'partfit'):
            shutil.copytree(os.path.join(src, d), os.path.join(out, d))
        shutil.copy(os.path.join(src, f'{name}_offsets.json'), os.path.join(out, f'{name}_offsets.json'))
    s, e = takes[main_n]
    cmd = [sys.executable, os.path.join(TOOLS, 'r6_convert.py'), video, str(s), str(e), a.clip, '--out', out,
           '--view', f'{a.clip}_take{main_n}']
    if guides:
        cmd += ['--guide', ','.join(guides)]
    if a.viewer:
        cmd.append('--viewer')
    r = subprocess.run(cmd, cwd=ROOT)
    if r.returncode:
        sys.exit(r.returncode)
    print(f'done: {os.path.join(ROOT, "build", "fit", a.clip, "clip.json")}')


if __name__ == '__main__':
    main()
