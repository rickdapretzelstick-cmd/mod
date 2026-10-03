#!/usr/bin/env python3
"""
The Prison Realm cell's blocks: textures (16x16, fixed seed so re-running gives the same pixels), block models and
blockstates. Run: python3 tools/gen_prison_cell_assets.py

  - prison_wall: dark crimson flesh, a few veins (walls, floor, ceiling)
  - seal_lock_closed / _open / _broken: a sealed eye in a bone frame; open it glows magenta; broken it is cracked dark
  - prison_core / prison_core_open: the floor's core, closed (a shut iris) and open (a bright pupil)
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')
TEX = os.path.join(ROOT, 'textures', 'block')
os.makedirs(TEX, exist_ok=True)


def clamp(v):
    return max(0, min(255, int(v)))


def flesh(seed, base=(78, 18, 30)):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = rnd.uniform(-14, 14) + 8 * math.sin(x * 0.9 + y * 0.4 + seed)
            img.putpixel((x, y), (clamp(base[0] + n), clamp(base[1] + n * 0.4), clamp(base[2] + n * 0.5), 255))
    # Veins: a couple of random walks, darker.
    for v in range(3):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for _ in range(18):
            img.putpixel((x % 16, y % 16), (44, 6, 16, 255))
            x += rnd.choice((-1, 0, 1))
            y += rnd.choice((0, 1))
    return img


def eye(img, iris, pupil, glow=False, closed=False):
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - cx) / 6.2, (y - cy) / 3.6)
            # A bone frame round the lid.
            if 0.95 < d <= 1.18:
                img.putpixel((x, y), (196, 184, 160, 255))
            elif d <= 0.95:
                if closed:
                    img.putpixel((x, y), (40, 10, 18, 255) if abs(y - cy) > 0.6 else (120, 40, 60, 255))
                    continue
                r = math.hypot(x - cx, y - cy)
                c = pupil if r < 1.4 else iris if r < 3.2 else (230, 214, 210)
                if glow:
                    c = tuple(clamp(v * 1.15 + 20) for v in c)
                img.putpixel((x, y), (*c, 255))
    return img


def save(img, name):
    img.save(os.path.join(TEX, name + '.png'))


save(flesh(1), 'prison_wall')
save(eye(flesh(2, (60, 14, 24)), None, None, closed=True), 'seal_lock_closed')
save(eye(flesh(2, (110, 24, 70)), (220, 60, 200), (255, 240, 255), glow=True), 'seal_lock_open')
broken = flesh(3, (34, 10, 14))
rnd = random.Random(9)
for _ in range(4):
    x, y = rnd.randrange(16), 0
    while y < 16:
        broken.putpixel((x % 16, y), (8, 2, 4, 255))
        x += rnd.choice((-1, 0, 1))
        y += 1
save(broken, 'seal_lock_broken')
save(eye(flesh(4, (50, 12, 20)), None, None, closed=True), 'prison_core')
save(eye(flesh(4, (90, 20, 60)), (90, 220, 255), (255, 255, 255), glow=True), 'prison_core_open')

models = {
    'prison_wall': 'prison_wall',
    'seal_lock_closed': 'seal_lock_closed', 'seal_lock_open': 'seal_lock_open', 'seal_lock_broken': 'seal_lock_broken',
    'prison_core': 'prison_core', 'prison_core_open': 'prison_core_open',
}
mdir = os.path.join(ROOT, 'models', 'block')
for name, tex in models.items():
    json.dump({'parent': 'minecraft:block/cube_all', 'textures': {'all': 'jjk:block/' + tex}}, open(os.path.join(mdir, name + '.json'), 'w'), indent=2)
bdir = os.path.join(ROOT, 'blockstates')
json.dump({'variants': {'': {'model': 'jjk:block/prison_wall'}}}, open(os.path.join(bdir, 'prison_wall.json'), 'w'), indent=2)
json.dump({'variants': {'lock=0': {'model': 'jjk:block/seal_lock_closed'}, 'lock=1': {'model': 'jjk:block/seal_lock_open'},
                        'lock=2': {'model': 'jjk:block/seal_lock_broken'}}}, open(os.path.join(bdir, 'seal_lock.json'), 'w'), indent=2)
json.dump({'variants': {'open=false': {'model': 'jjk:block/prison_core'}, 'open=true': {'model': 'jjk:block/prison_core_open'}}},
          open(os.path.join(bdir, 'prison_core.json'), 'w'), indent=2)
print('wrote the Prison Realm cell textures, models and blockstates')
