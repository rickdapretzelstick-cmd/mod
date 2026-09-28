"""Generates Hakari's block textures, block models and blockstates. Run: python3 tools/gen_hakari_assets.py

Everything is 16x16 pixel art:
  - idg_barrier / idg_floor: Idle Death Gamble's endless white room (domain blocks)
  - shutter_panel: the steel shutter of Shutter Doors
  - gamble_door: Door Guard's red lacquer door
  - pachinko_ball: a Reserve Ball
  - idg_prop (part 0-1): the bullet-train cars and red seven-segment LED pieces drawn inside the domain
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


# --- Idle Death Gamble wall and floor: the same flat white, no seams at all. With the blocks self-lit, unshaded and
# without ambient occlusion, floor, walls and ceiling melt into one another (no depth to judge by). ---
save(canvas(0xFAFAFB), 'idg_barrier')
save(canvas(0xFAFAFB), 'idg_floor')

# --- Bullet train: white body with a black window band, a dark skirt, and plain white / dark faces. ---
img = canvas(0xF6F6F8)
for x in range(16):
    for y in (5, 6, 7):
        img.putpixel((x, y), rgb(0x10141C))
    img.putpixel((x, 4), rgb(0xE0E0E6))
    img.putpixel((x, 13), rgb(0xDADAE0))
    img.putpixel((x, 14), rgb(0x1A2130))
    img.putpixel((x, 15), rgb(0x1A2130))
save(img, 'train_side')
img = canvas(0xF6F6F8)
for i in range(16):
    img.putpixel((i, 0), rgb(0xE4E4EA))
save(img, 'train_white')
save(canvas(0x1A2130), 'train_dark')
img = canvas(0xF6F6F8)
for x in range(3, 13):
    for y in (4, 5, 6):
        img.putpixel((x, y), rgb(0x10141C))
for x in range(16):
    img.putpixel((x, 14), rgb(0x1A2130))
    img.putpixel((x, 15), rgb(0x1A2130))
save(img, 'train_nose')

# --- LED segment: the solid red of the giant seven-segment reel counters. ---
img = canvas(0xE8162E)
for i in range(16):
    for j in (0, 15):
        img.putpixel((i, j), rgb(0xC00E22))
        img.putpixel((j, i), rgb(0xC00E22))
save(img, 'led_segment')

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

def write(path, data):
    with open(path, 'w') as f:
        json.dump(data, f)


# The room is lit by itself (full-bright faces), so it reads bright white like the reference instead of taking on the
# warm tint of block light.
for name in ('idg_barrier', 'idg_floor'):
    faces = {d: {"texture": "#all", "cullface": d} for d in ("north", "south", "east", "west", "up", "down")}
    # No directional shading either: every face the same flat white, like the Jujutsu Shenanigans room.
    write(os.path.join(MODELS, name + '.json'), {"ambientocclusion": False, "textures": {"all": "jjk:block/" + name, "particle": "jjk:block/" + name},
                                                 "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                                                               "faces": faces}]})
    write(os.path.join(STATES, name + '.json'), {"variants": {"": {"model": "jjk:block/" + name}}})


def panel(tex, frm, to):
    faces = {d: {"texture": "#t", "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
    return {"textures": {"t": "jjk:block/" + tex, "particle": "jjk:block/" + tex}, "elements": [{"from": frm, "to": to, "faces": faces}]}


write(os.path.join(MODELS, 'shutter_panel.json'), panel('shutter_panel', [0, 0, 7], [16, 16, 9]))
write(os.path.join(MODELS, 'gamble_door.json'), panel('gamble_door', [0, 0, 7], [16, 16, 9]))
write(os.path.join(MODELS, 'pachinko_ball.json'), panel('pachinko_ball', [5, 5, 5], [11, 11, 11]))
for name in ('shutter_panel', 'gamble_door', 'pachinko_ball'):
    write(os.path.join(STATES, name + '.json'), {"variants": {"": {"model": "jjk:block/" + name}}})


def box(frm, to, tex, emit=15):
    faces = {d: {"texture": "#" + tex(d)} for d in ("north", "south", "east", "west", "up", "down")}
    return {"from": frm, "to": to, "light_emission": emit, "faces": faces}


# A bullet-train car three blocks long (x from -16 to 32): body, roof, skirt and a nose at each end. Fully lit so the
# cars stay bright white inside the domain no matter where they are drawn.
side = lambda d: "side" if d in ("north", "south") else "white" if d in ("up", "east", "west") else "dark"
write(os.path.join(MODELS, 'train_car.json'), {
    "textures": {"side": "jjk:block/train_side", "white": "jjk:block/train_white", "dark": "jjk:block/train_dark",
                 "nose": "jjk:block/train_nose", "particle": "jjk:block/train_white"},
    "elements": [
        box([-13, 1, 1], [29, 13, 15], side),
        box([-12, 13, 3], [28, 15, 13], lambda d: "white"),
        box([-12, 0, 3], [28, 1, 13], lambda d: "dark"),
        box([29, 1, 3], [32, 10, 13], lambda d: "nose" if d == "east" else "white" if d != "down" else "dark"),
        box([-16, 1, 3], [-13, 10, 13], lambda d: "nose" if d == "west" else "white" if d != "down" else "dark"),
    ]})
write(os.path.join(MODELS, 'led_segment.json'), {
    "textures": {"led": "jjk:block/led_segment", "particle": "jjk:block/led_segment"},
    "elements": [box([0, 0, 0], [16, 16, 16], lambda d: "led")]})
write(os.path.join(STATES, 'idg_prop.json'), {"variants": {"part=0": {"model": "jjk:block/train_car"}, "part=1": {"model": "jjk:block/led_segment"}}})
print('hakari assets written')

# --- The Idle Death Gamble cut-in backdrop: dark teal double helices and cloud shadows (alpha only; the band is teal). ---
import math
GUI = os.path.join(ROOT, 'textures', 'gui')
img = Image.new('RGBA', (256, 128), (0, 0, 0, 0))
px = img.load()
def dot(x, y, a):
    x %= 256
    if 0 <= y < 128:
        r, g, b, old = px[x, y]
        px[x, y] = (8, 40, 44, max(old, a))
# Cloudy shadows.
import random
rnd = random.Random(7)
for _ in range(26):
    cx, cy, rr = rnd.randrange(256), rnd.randrange(128), rnd.randrange(10, 26)
    for y in range(cy - rr, cy + rr):
        for x in range(cx - rr, cx + rr):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < rr:
                dot(x, y, int(60 * (1 - d / rr)))
# Helices running diagonally: two strands and their rungs.
for k in range(3):
    ox, oy = k * 96 - 20, k * 36 - 30
    for t in range(0, 900):
        s = t / 900
        x = ox + s * 180
        y = oy + s * 150
        for strand in (0, math.pi):
            off = math.sin(s * 14 + strand) * 11
            for w in range(-2, 3):
                dot(int(x + off + w), int(y - off * 0.3), 150)
        if t % 45 == 0:
            a = math.sin(s * 14) * 11
            for q in range(-int(abs(a)), int(abs(a)) + 1):
                dot(int(x + q), int(y - q * 0.3), 120)
img.save(os.path.join(GUI, 'cinematic_helix.png'))
print('helix backdrop written')
