#!/usr/bin/env python3
"""
When the server's hits land inside each clip, read from move traces (MoveTimingAuditClientTest's anim-trace/*.txt):

    python3 tools/hit_timing.py build/run/clientGameTest/anim-trace [clip-prefix] [--any]

For every clip the server played, the hits the same entity landed while it was the newest clip, as ms from the clip's
start. A clip's contact frame belongs on those times (an animation pass marks them as "contact"). --any counts every
entity's hits (Rika's blows are dealt in Yuta's name while her clips play on her; in the audit only the player attacks).
"""
import collections
import glob
import os
import re
import sys

LINE = re.compile(r'^t=(\d+)\s+#(\d+) SERVER (\S+)\s*(.*)$')


def main():
    args = [a for a in sys.argv[1:] if a != '--any']
    anyone = '--any' in sys.argv
    folder = args[0] if args else 'build/run/clientGameTest/anim-trace'
    prefix = args[1] if len(args) > 1 else ''
    seen = collections.defaultdict(list)  # clip -> [(trace, [hit ms...])]
    for path in sorted(glob.glob(os.path.join(folder, '*.txt'))):
        if os.path.basename(path) == 'AUDIT.txt':
            continue
        events = []
        for line in open(path, encoding='utf-8'):
            m = LINE.match(line.strip())
            if m:
                events.append((int(m[1]), int(m[2]), m[3], m[4]))
        events.sort(key=lambda e: e[0])
        events = list(dict.fromkeys(events))  # (the timeline repeats server events after the frames)
        plays = [(t, ent, det.split()[0] if det else '') for t, ent, kind, det in events if kind == 'anim']
        for i, (t, ent, clip) in enumerate(plays):
            if not clip or clip.startswith('(') or not clip.startswith(prefix):
                continue
            nxt = next((t2 for t2, e2, _ in plays[i + 1:] if e2 == ent), t + 60)
            hits = [(t2 - t) * 50 for t2, e2, kind, _ in events if kind == 'hit' and (anyone or e2 == ent) and t <= t2 < nxt]
            seen[clip].append((os.path.basename(path)[:-4], nxt - t, hits))
    for clip in sorted(seen):
        for trace, ticks, hits in seen[clip]:
            print(f'{clip:28s} shown {ticks * 50:5d}ms  hits at {hits if hits else "-"}   ({trace})')


if __name__ == '__main__':
    main()
