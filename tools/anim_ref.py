#!/usr/bin/env python3
"""
Reference frames for animation work: turns a GIF (or a folder of frames) into a numbered contact sheet, so a pose can
be read off an exact frame and keyed in a clip at the same frame number.

    python3 tools/anim_ref.py RapidPunches.gif --from 0 --to 60 --step 3 --out sheet.png [--crop x0,y0,x1,y1] [--scale 0.5]

Every cell is labelled with its frame number and its time in ms (from the GIF's own frame delays; 20 ms = 50 fps when
the GIF doesn't say). A clip keyed with "fps" equal to the reference's frame rate can then use the same numbers as
"f" in its keys. The timing table is printed too, so holds and hit frames can be read without the sheet.
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageSequence


def frames(path):
    if os.path.isdir(path):
        names = sorted(n for n in os.listdir(path) if n.lower().endswith(('.png', '.jpg')))
        return [(Image.open(os.path.join(path, n)).convert('RGB'), 20) for n in names]
    im = Image.open(path)
    out = []
    for f in ImageSequence.Iterator(im):
        out.append((f.convert('RGB'), f.info.get('duration') or 20))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src')
    ap.add_argument('--from', dest='start', type=int, default=0)
    ap.add_argument('--to', dest='end', type=int, default=-1)
    ap.add_argument('--step', type=int, default=4)
    ap.add_argument('--cols', type=int, default=6)
    ap.add_argument('--crop', default=None)
    ap.add_argument('--scale', type=float, default=0.5)
    ap.add_argument('--out', default='ref_sheet.png')
    a = ap.parse_args()
    fr = frames(a.src)
    t, times = 0, []
    for _, d in fr:
        times.append(t)
        t += d
    end = len(fr) - 1 if a.end < 0 else min(a.end, len(fr) - 1)
    picks = list(range(a.start, end + 1, a.step))
    crop = tuple(map(int, a.crop.split(','))) if a.crop else None
    cells = []
    for i in picks:
        im = fr[i][0].crop(crop) if crop else fr[i][0]
        im = im.resize((max(1, int(im.width * a.scale)), max(1, int(im.height * a.scale))))
        d = ImageDraw.Draw(im)
        label = 'f%d  %dms' % (i, times[i])
        d.rectangle((0, 0, 8 + 6 * len(label), 12), fill=(0, 0, 0))
        d.text((3, 1), label, fill=(255, 230, 80))
        cells.append(im)
    if not cells:
        sys.exit('no frames in range')
    w, h = cells[0].size
    rows = (len(cells) + a.cols - 1) // a.cols
    sheet = Image.new('RGB', (w * min(a.cols, len(cells)), h * rows))
    for k, im in enumerate(cells):
        sheet.paste(im, ((k % a.cols) * w, (k // a.cols) * h))
    sheet.save(a.out)
    total = t
    print('%s: %d frames, %d ms (%.1f fps average)' % (a.src, len(fr), total, len(fr) * 1000.0 / max(1, total)))
    print('sheet %s: frames %s' % (a.out, ', '.join(map(str, picks))))


if __name__ == '__main__':
    main()
