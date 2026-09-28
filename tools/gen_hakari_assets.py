"""Generates Hakari's block textures, block models and blockstates. Run: python3 tools/gen_hakari_assets.py

Everything is 16x16 pixel art in Hakari's casino palette (black, white, hot pink, red, gold, pachinko chrome):
  - idg_barrier / idg_floor: Idle Death Gamble's neon pachinko wall and casino floor (domain blocks)
  - shutter_panel: the steel shutter of Shutter Doors
  - gamble_door: Door Guard's red lacquer door
  - pachinko_ball: a Reserve Ball
  - reel (digit 0-7): the slot reels hung over the domain (0 is a spinning blur)
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')
TEX = os.path.join(ROOT, 'textures', 'block')
MODELS = os.path.join(ROOT, 'models', 'block')
STATES = os.path.join(ROOT, 'blockstates')
for d in (TEX, MODELS, STATES):
    os.makedirs(d, exist_ok=True)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def canvas(fill):
    return Image.new('RGBA', (16, 16), rgb(fill))


def save(img, name):
    img.save(os.path.join(TEX, name + '.png'))


# --- Idle Death Gamble wall: dark casino lacquer, a grid of gold pachinko pins, a pink neon stripe. ---
img = canvas(0x1A0710)
for y in range(16):
    for x in range(16):
        if (x + y) % 7 == 0:
            img.putpixel((x, y), rgb(0x24091A))
for y in range(1, 16, 4):
    for x in range(1 + (y // 4 % 2) * 2, 16, 4):
        img.putpixel((x, y), rgb(0xF0C040))
        if x + 1 < 16:
            img.putpixel((x + 1, y), rgb(0x8A6420))
for x in range(16):
    img.putpixel((x, 7), rgb(0xFF3FA0))
    img.putpixel((x, 8), rgb(0xB0206C))
save(img, 'idg_barrier')

# --- Idle Death Gamble floor: a black and white casino checker with pink seams. ---
img = canvas(0x101010)
for y in range(16):
    for x in range(16):
        white = (x // 8 + y // 8) % 2 == 0
        c = 0xE8E4EC if white else 0x141018
        if x % 8 == 0 or y % 8 == 0:
            c = 0xFF4FA8
        img.putpixel((x, y), rgb(c))
save(img, 'idg_floor')

# --- Shutter panel: corrugated steel slats, a hazard-pink kick strip at the bottom. ---
img = canvas(0x9098A4)
for y in range(16):
    for x in range(16):
        c = [0xB8C0CC, 0x9AA2AE, 0x7A828E, 0x5E6672][y % 4]
        img.putpixel((x, y), rgb(c))
for x in range(16):
    img.putpixel((x, 13), rgb(0xFF3FA0 if (x // 2) % 2 == 0 else 0x202028))
    img.putpixel((x, 14), rgb(0xFF3FA0 if (x // 2) % 2 == 1 else 0x202028))
    img.putpixel((x, 15), rgb(0x3A3F48))
for y in range(16):
    img.putpixel((0, y), rgb(0x4A505A))
    img.putpixel((15, y), rgb(0x4A505A))
save(img, 'shutter_panel')

# --- Gamble door: red lacquer panels in a gold frame, a white 7 in the middle. ---
img = canvas(0xB01828)
for y in range(16):
    for x in range(16):
        if x in (0, 15) or y in (0, 15):
            img.putpixel((x, y), rgb(0xE8B840))
        elif x in (1, 14) or y in (1, 14):
            img.putpixel((x, y), rgb(0x7A0E18))
        elif x == 7 or x == 8:
            img.putpixel((x, y), rgb(0x8A1020))
seven = ["#####", "....#", "...#.", "..#..", "..#..", "..#.."]
for j, row in enumerate(seven):
    for i, ch in enumerate(row):
        if ch == '#':
            img.putpixel((5 + i, 5 + j), rgb(0xFFFFFF))
img.putpixel((12, 8), rgb(0xE8B840))
img.putpixel((3, 8), rgb(0xE8B840))
save(img, 'gamble_door')

# --- Pachinko ball: polished chrome with a pink glint. ---
img = canvas(0x8C94A0)
for y in range(16):
    for x in range(16):
        t = (x + y) / 30
        v = int(210 - 110 * t)
        img.putpixel((x, y), (v, v + 4, v + 12, 255))
for x, y in ((4, 4), (5, 4), (4, 5), (5, 5), (6, 5), (5, 6)):
    img.putpixel((x, y), rgb(0xFFFFFF))
img.putpixel((11, 11), rgb(0xFF7FC0))
img.putpixel((12, 11), rgb(0xFF3FA0))
save(img, 'pachinko_ball')

# --- Reels: a white face with a bold number (7 in red, the rest in black); 0 is a motion blur. ---
DIGITS = {
    1: ["..#..", ".##..", "..#..", "..#..", "..#..", ".###."],
    2: [".##..", "#..#.", "...#.", "..#..", ".#...", "####."],
    3: ["###..", "...#.", ".##..", "...#.", "...#.", "###.."],
    4: ["#..#.", "#..#.", "####.", "...#.", "...#.", "...#."],
    5: ["####.", "#....", "###..", "...#.", "...#.", "###.."],
    6: [".##..", "#....", "###..", "#..#.", "#..#.", ".##.."],
    7: ["#####", "....#", "...#.", "..#..", "..#..", "..#.."],
}
for n in range(8):
    img = canvas(0xF4F0F6)
    for y in range(16):
        for x in range(16):
            if y in (0, 15):
                img.putpixel((x, y), rgb(0xE8B840))
            elif x in (0, 15):
                img.putpixel((x, y), rgb(0xB0206C))
    if n == 0:
        for y in range(2, 14):
            for x in range(3, 13):
                if (y + x // 3) % 3 == 0:
                    img.putpixel((x, y), rgb(0x9A94A0))
                elif y % 5 == 0:
                    img.putpixel((x, y), rgb(0xE05060))
    else:
        col = 0xE0102A if n == 7 else 0x141018
        for j, row in enumerate(DIGITS[n]):
            for i, ch in enumerate(row):
                if ch == '#':
                    for dx in (0, 1):
                        for dy in (0, 1):
                            img.putpixel((3 + i * 2 + dx - (1 if n != 7 else 0), 2 + j * 2 + dy), rgb(col))
    save(img, 'reel_%d' % n)


def write(path, data):
    with open(path, 'w') as f:
        json.dump(data, f)


for name in ('idg_barrier', 'idg_floor'):
    write(os.path.join(MODELS, name + '.json'), {"parent": "minecraft:block/cube_all", "textures": {"all": "jjk:block/" + name}})
    write(os.path.join(STATES, name + '.json'), {"variants": {"": {"model": "jjk:block/" + name}}})


def panel(tex, frm, to):
    faces = {d: {"texture": "#t", "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
    return {"textures": {"t": "jjk:block/" + tex, "particle": "jjk:block/" + tex}, "elements": [{"from": frm, "to": to, "faces": faces}]}


write(os.path.join(MODELS, 'shutter_panel.json'), panel('shutter_panel', [0, 0, 7], [16, 16, 9]))
write(os.path.join(MODELS, 'gamble_door.json'), panel('gamble_door', [0, 0, 7], [16, 16, 9]))
write(os.path.join(MODELS, 'pachinko_ball.json'), panel('pachinko_ball', [5, 5, 5], [11, 11, 11]))
for name in ('shutter_panel', 'gamble_door', 'pachinko_ball'):
    write(os.path.join(STATES, name + '.json'), {"variants": {"": {"model": "jjk:block/" + name}}})
for n in range(8):
    write(os.path.join(MODELS, 'reel_%d.json' % n), {"parent": "minecraft:block/cube_all", "textures": {"all": "jjk:block/reel_%d" % n}})
write(os.path.join(STATES, 'reel.json'), {"variants": {"digit=%d" % n: {"model": "jjk:block/reel_%d" % n} for n in range(8)}})
print('hakari assets written')
