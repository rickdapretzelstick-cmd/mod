#!/usr/bin/env python3
"""No-clash clothing for footage fitting: a classic shirt and pants (Roblox's 585 x 559 template layout) where no colour
appears on two parts, plus the fitting palette that reads them.

  python3 tools/make_clothing_templates.py

Each part has its own hue, 60 degrees apart (torso red, right arm yellow, left arm green, right leg cyan, left leg blue;
the head magenta, set as a body colour in the avatar editor); within a part each side has a tone: the front full
colour, the back pale, the four other sides dark (which of those faces the camera follows from where it shows beside
the front or back). Every side carries its letter (F B L R U D, as Roblox's own template) so the mapping can be checked
in footage; the fitter reads lettering as unknown. Writes tools/clothing/no_clash_shirt.png, no_clash_pants.png and
tools/palettes/no_clash.json.
"""
import colorsys
import json
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
SIZE = (585, 559)
# Side rectangles (x, y, w, h), measured from Roblox's official templates (creator-docs Template-Shirts-R15.png and
# Template-Pants-R15.png; both sheets share the layout, the pants' limb blocks being the legs).
TORSO = {'U': (231, 8, 128, 64), 'R': (165, 74, 64, 128), 'F': (231, 74, 128, 128), 'L': (361, 74, 64, 128),
         'B': (427, 74, 128, 128), 'D': (231, 204, 128, 64)}
RIGHT_LIMB = {'U': (217, 289, 64, 64), 'L': (19, 355, 64, 128), 'B': (85, 355, 64, 128), 'R': (151, 355, 64, 128),
              'F': (217, 355, 64, 128), 'D': (217, 485, 64, 64)}
LEFT_LIMB = {'U': (308, 289, 64, 64), 'F': (308, 355, 64, 128), 'L': (374, 355, 64, 128), 'B': (440, 355, 64, 128),
             'R': (506, 355, 64, 128), 'D': (308, 485, 64, 64)}
HUE = {'torso': 0, 'rightArm': 60, 'leftArm': 120, 'rightLeg': 180, 'leftLeg': 240, 'head': 300}
TONE = {'F': 'pure', 'B': 'pale', 'L': 'dark', 'R': 'dark', 'U': 'dark', 'D': 'dark'}
SV = {'pure': (1.0, 1.0), 'pale': (0.4, 1.0), 'dark': (1.0, 0.45)}
# Classification bands (PIL HSV, 0-255): each tone's saturation and value, each hue +-30 degrees.
BAND = {'pure': {'s': [165, 255], 'v': [150, 255]}, 'pale': {'s': [45, 164], 'v': [150, 255]},
        'dark': {'s': [128, 255], 'v': [30, 149]}}


def rgb(hue, tone):
    s, v = SV[tone]
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue / 360.0, s, v))


def paint(sheet, rects, part):
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.load_default(size=22)
    except TypeError:
        font = ImageFont.load_default()
    for side, (x, y, w, h) in rects.items():
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=rgb(HUE[part], TONE[side]) + (255,))
        tw, th = d.textbbox((0, 0), side, font=font)[2:]
        ink = (255, 255, 255, 255) if TONE[side] == 'dark' else (0, 0, 0, 255)
        d.text((x + (w - tw) / 2, y + (h - th) / 2), side, fill=ink, font=font)


def main():
    out = os.path.join(HERE, 'clothing')
    os.makedirs(out, exist_ok=True)
    shirt = Image.new('RGBA', SIZE, (0, 0, 0, 0))
    paint(shirt, TORSO, 'torso')
    paint(shirt, RIGHT_LIMB, 'rightArm')
    paint(shirt, LEFT_LIMB, 'leftArm')
    shirt.save(os.path.join(out, 'no_clash_shirt.png'))
    pants = Image.new('RGBA', SIZE, (0, 0, 0, 0))
    paint(pants, TORSO, 'torso')  # under the shirt; the same colours if it shows
    paint(pants, RIGHT_LIMB, 'rightLeg')
    paint(pants, LEFT_LIMB, 'leftLeg')
    pants.save(os.path.join(out, 'no_clash_pants.png'))
    # The palette: a class per part and tone (the head one colour all round), the sides mapped in the fitter's order.
    classes, sides = [], {}
    order = ['R', 'L', 'U', 'D', 'F', 'B']  # the fitter's box sides: right, left, top, bottom, front, back
    for part in ('torso', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg'):
        for tone in ('pure', 'pale', 'dark'):
            h = HUE[part] * 255 / 360
            lo, hi = (h - 21) % 256, (h + 21) % 256
            classes.append({'name': f'{part}_{tone}', 'parts': [part] if tone == 'pure' else [],
                            'h': [round(lo), round(hi)], **BAND[tone], 'show': list(rgb(HUE[part], tone))})
        sides[part] = [f'{part}_{TONE[s]}' for s in order]
    h = HUE['head'] * 255 / 360
    classes.append({'name': 'head', 'parts': ['head'], 'h': [round((h - 21) % 256), round((h + 21) % 256)],
                    's': [128, 255], 'v': [60, 255], 'show': list(rgb(HUE['head'], 'pure'))})
    sides['head'] = ['head'] * 6
    classes.append({'name': 'face', 'parts': ['face'], 'v': [0, 70], 'show': [40, 40, 255]})
    pal = {'comment': 'tools/make_clothing_templates.py: no colour on two parts; per part a hue, per side a tone '
                      '(front pure, back pale, the others dark). Bands to be re-measured from footage.',
           'classes': classes, 'sides': sides, 'marks': True, 'veil': {'s_min': 22, 'v_min': 150, 'reach': 0.02}}
    json.dump(pal, open(os.path.join(HERE, 'palettes', 'no_clash.json'), 'w'), indent=1)
    print('wrote tools/clothing/no_clash_shirt.png, no_clash_pants.png, tools/palettes/no_clash.json')


if __name__ == '__main__':
    main()
