"""The village news board: block textures (weathered board, pinned paper), model, blockstate and item definition.

    python3 tools/gen_news_board_assets.py
"""
import json
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk')
random.seed(7)


def write_json(obj, *path):
    p = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def save(img, *path):
    p = os.path.join(ROOT, 'textures', *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


# Weathered boards: three planks, grey-brown, with grain and nail heads.
wood = Image.new('RGBA', (16, 16))
base = [(112, 92, 70), (104, 85, 64), (118, 98, 74)]
for y in range(16):
    plank = min(2, y // 6)
    for x in range(16):
        r, g, b = base[plank]
        n = random.randint(-9, 9) + (6 if (x * 3 + y * 7 + plank) % 11 == 0 else 0)
        if y in (5, 11):
            n -= 28  # gaps between the planks
        wood.putpixel((x, y), (max(0, r + n), max(0, g + n), max(0, b + n), 255))
for x, y in ((1, 2), (14, 2), (1, 8), (14, 8), (1, 13), (14, 13)):
    wood.putpixel((x, y), (60, 58, 56, 255))
save(wood, 'block', 'news_board_wood.png')

# A pinned notice: off-white, ink lines, a pin at the top.
paper = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16):
        v = 226 + random.randint(-8, 4) - (y // 4)
        paper.putpixel((x, y), (v, v - 6, v - 22, 255))
for y in range(4, 15, 2):
    length = random.randint(8, 13)
    for x in range(2, 2 + length):
        if random.random() > 0.15:
            paper.putpixel((x, y), (70, 64, 60, 255))
for x, y in ((7, 1), (8, 1), (7, 2), (8, 2)):
    paper.putpixel((x, y), (150, 40, 40, 255))
save(paper, 'block', 'news_board_paper.png')

side = {'texture': '#wood'}
model = {
    'parent': 'minecraft:block/block',
    'textures': {'particle': 'jjk:block/news_board_wood', 'wood': 'jjk:block/news_board_wood', 'paper': 'jjk:block/news_board_paper',
                 'post': 'minecraft:block/stripped_spruce_log'},
    'elements': [
        {'from': [1, 0, 7], 'to': [3, 16, 9], 'faces': {f: {'texture': '#post'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
        {'from': [13, 0, 7], 'to': [15, 16, 9], 'faces': {f: {'texture': '#post'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
        {'from': [0, 5, 7.5], 'to': [16, 15, 8.5], 'faces': {
            'north': {'uv': [0, 1, 16, 11], 'texture': '#wood'}, 'south': {'uv': [0, 1, 16, 11], 'texture': '#wood'},
            'east': {'uv': [0, 1, 1, 11], 'texture': '#wood'}, 'west': {'uv': [0, 1, 1, 11], 'texture': '#wood'},
            'up': {'uv': [0, 0, 16, 1], 'texture': '#wood'}, 'down': {'uv': [0, 0, 16, 1], 'texture': '#wood'}}},
        # A little roof over the board.
        {'from': [-0.5, 15, 6], 'to': [16.5, 16, 10], 'faces': {f: {'texture': '#wood'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
    ],
}
notes = [((2, 8, 7.0), (7, 14, 7.5)), ((8.5, 9, 7.0), (13.5, 13.5, 7.5)), ((5, 5.5, 7.0), (10, 9, 7.5)), ((11, 6, 7.0), (14.5, 8.5, 7.5))]
for i, (a, b) in enumerate(notes):
    model['elements'].append({'from': list(a), 'to': list(b), 'faces': {'north': {'uv': [1, 0, 15, 16], 'texture': '#paper'}}})
write_json(model, 'models', 'block', 'news_board.json')
write_json({'variants': {
    'facing=north': {'model': 'jjk:block/news_board'},
    'facing=east': {'model': 'jjk:block/news_board', 'y': 90},
    'facing=south': {'model': 'jjk:block/news_board', 'y': 180},
    'facing=west': {'model': 'jjk:block/news_board', 'y': 270}}}, 'blockstates', 'news_board.json')
write_json({'model': {'type': 'minecraft:model', 'model': 'jjk:block/news_board'}}, 'items', 'news_board.json')
print('news board assets written')
