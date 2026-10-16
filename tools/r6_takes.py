# The takes in a raw recording: each stretch where the avatar moves between two still moments seen from the same
# camera (the still frame before and the one after look alike), so camera moves and cuts between takes are skipped.
#   python3 tools/r6_takes.py VIDEO            prints one line per take: IDLE_START IDLE_END (frames)
#   python3 tools/r6_takes.py VIDEO --json     the same as JSON
# Thresholds are shares of the picture's pixels (1/8 size, grey, a change of more than GRAY_STEP levels counts), so
# they don't depend on the recording's resolution or the avatar's distance from the camera.
import sys, json, argparse
import numpy as np
import cv2

GRAY_STEP = 6          # grey levels a pixel must change by to count as changed
STILL = 0.003          # a frame is still when less than this share of pixels changed since the previous one
STILL_RUN = 4          # still frames in a row that make a still moment
MIN_MOVE = 30          # frames a move must last (shorter: a twitch or a camera nudge)
SAME_CAMERA = 0.05     # the still frames either side differ by less than this share: same camera, same spot
MOVE_PEAK = 0.02       # the move's busiest frame changes at least this share (something really moved)
PAD = 8                # still frames kept either side of the move (tracking starts on a standing frame)


def frames(video, scale=0.125):
    cap = cv2.VideoCapture(video)
    out = []
    while True:
        ok, f = cap.read()
        if not ok:
            break
        g = cv2.cvtColor(cv2.resize(f, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA), cv2.COLOR_BGR2GRAY)
        out.append(cv2.GaussianBlur(g.astype(np.float32), (3, 3), 0))
    return np.array(out), cap.get(cv2.CAP_PROP_FPS) or 30.0


def find_takes(video):
    """[(idle_start, idle_end)] frame numbers of every take in the recording, in order."""
    fr, _ = frames(video)
    if len(fr) < 2:
        return []
    changed = lambda a, b: float((np.abs(fr[a] - fr[b]) > GRAY_STEP).mean())
    d = np.r_[0.0, (np.abs(fr[1:] - fr[:-1]) > GRAY_STEP).mean((1, 2))]
    still = d < STILL
    runs, i = [], 0
    while i < len(d):
        j = i
        while j < len(d) and still[j] == still[i]:
            j += 1
        if still[i] and j - i >= STILL_RUN:
            runs.append((i, j - 1))
        i = j
    takes = []
    for (a0, b0), (a1, b1) in zip(runs, runs[1:]):
        s, e = b0, a1  # the last still frame before the move, the first one after
        if e - s >= MIN_MOVE and changed(s, e) < SAME_CAMERA and d[s:e + 1].max() >= MOVE_PEAK:
            takes.append((max(a0, s - PAD), min(b1, e + PAD)))
    return takes


def main():
    ap = argparse.ArgumentParser(description='find the takes in a raw recording')
    ap.add_argument('video')
    ap.add_argument('--json', action='store_true')
    a = ap.parse_args()
    takes = find_takes(a.video)
    if a.json:
        print(json.dumps(takes))
    else:
        for s, e in takes:
            print(s, e)
    if not takes:
        sys.exit('no takes found: the avatar needs to stand still before and after each move, camera unmoved')


if __name__ == '__main__':
    main()
