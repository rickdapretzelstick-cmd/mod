# Checks a built converter app works on this PC, without a real recording: the libraries load, the part network
# loads, worker processes start (the way every pipeline step uses them), one script can run another, and a video can be
# read (a made-up one: still, a square moving out and back, still, which r6_takes must find as one take).
#   jjk_worker.exe tools/app_selftest.py
import sys, os, json, subprocess, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_mp

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def square(x):
    import numpy as np
    return float(np.square(x))


def video(path):
    import cv2, numpy as np
    w = cv2.VideoWriter(path, cv2.VideoWriter_fourcc(*'mp4v'), 30, (320, 240))
    for i in range(120):  # still, out and back (the same picture after as before, like a take), still
        im = np.full((240, 320, 3), 90, np.uint8)
        x = 20 + int(160 * np.sin(np.pi * (i - 30) / 60)) if 30 <= i < 90 else 20
        cv2.rectangle(im, (x, 40), (x + 120, 200), (0, 200, 255), -1)
        w.write(im)
    w.release()


def main():
    import numpy, scipy, cv2, torch, PIL
    print('libraries:', 'numpy', numpy.__version__, 'scipy', scipy.__version__, 'opencv', cv2.__version__, 'torch',
          torch.__version__, flush=True)
    import r6_partnet as P
    P.load(os.path.join(ROOT, 'build', 'synth', 'partnet2.pt'))
    print('part network: loaded', flush=True)
    import r6_limbpass, r6_neutral, r6_batch  # every step's imports
    with r6_mp.Pool(2) as pool:
        got = pool.map(square, [1, 2, 3])
    assert got == [1.0, 4.0, 9.0], got
    print(f'worker processes ({r6_mp.method()}): ok', flush=True)
    with tempfile.TemporaryDirectory() as d:
        v = os.path.join(d, 'test.mp4')
        video(v)
        out = subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'r6_takes.py'), v, '--json'],
                             capture_output=True, text=True)
        print(out.stdout.strip(), out.stderr.strip()[-500:], flush=True)
        takes = json.loads(out.stdout.strip().splitlines()[-1])
        assert len(takes) == 1, takes
    print('video reading and take finding: ok', flush=True)
    print('SELFTEST OK')


if __name__ == '__main__':
    main()
