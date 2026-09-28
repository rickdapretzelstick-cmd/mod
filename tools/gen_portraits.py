"""Generates the 32x32 pixel-art character portraits for the character select screen. Run: python3 tools/gen_portraits.py"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'textures', 'gui', 'portrait')
os.makedirs(OUT, exist_ok=True)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


def draw(rows, palette, name):
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((x, y), rgb(palette[ch]))
    img.save(os.path.join(OUT, name + '.png'))


# Legend: H hair, h hair shade, S skin, s skin shade, B blindfold, E eye, W white, K black, J jacket, j jacket shade,
# C collar/shirt, G gold, M mouth, P pink.
GOJO = [
    "................................",
    "...........HHH..HHH.............",
    ".........HHHHHHHHHHHH..H........",
    ".......HHHHHHHHHHHHHHHHH........",
    "......HHHHHHHHHHHHHHHHHHH.......",
    ".....HHHHhHHHHHhHHHHHhHHHH......",
    ".....HHHhhHHHHhhHHHHhhHHHH......",
    "....HHHHHHHHHHHHHHHHHHHHHHH.....",
    "....HHHHHHHHHHHHHHHHHHHHHHH.....",
    "....HHHSSSSSSSSSSSSSSSSSHHH.....",
    "....HHSSSSSSSSSSSSSSSSSSSHH.....",
    "....HBBBBBBBBBBBBBBBBBBBBBH.....",
    "....HBBBBBBBBBBBBBBBBBBBBBH.....",
    "....HBBBBBBBBBBBBBBBBBBBBBH.....",
    ".....SSSSSSSSSSsSSSSSSSSSS......",
    ".....SSSSSSSSSSsSSSSSSSSSS......",
    ".....SSSSSSSSSsssSSSSSSSSS......",
    "......SSSSSSSSSSSSSSSSSSS.......",
    "......SSSSSSSMMMMSSSSSSSS.......",
    ".......SSSSSSSSSSSSSSSSS........",
    "........sSSSSSSSSSSSSSs.........",
    "..........ssSSSSSSSss...........",
    "...........KKsssssKK............",
    ".........KKKKKKKKKKKKK..........",
    ".......KKKKKKKKKKKKKKKKK........",
    ".....KKKKKKKKKKKKKKKKKKKKK......",
    "....KKKKKKKKKKKKKKKKKKKKKKK.....",
    "...KKKKKKKKKKKjjjKKKKKKKKKKK....",
    "..KKKKKKKKKKKKjjjKKKKKKKKKKKK...",
    "..KKKKKKKKKKKKjjjKKKKKKKKKKKK...",
    "..KKKKKKKKKKKKjjjKKKKKKKKKKKK...",
    "..KKKKKKKKKKKKjjjKKKKKKKKKKKK...",
]
draw(GOJO, {'H': 0xF4F6FA, 'h': 0xC8D0DC, 'S': 0xF2D2B6, 's': 0xD8B496, 'B': 0x121218, 'M': 0xB07A6A, 'K': 0x1A1C26,
             'j': 0x2C3040}, 'gojo')

HAKARI = [
    "................................",
    "..........HHHHHHHHHH............",
    "........HHHHHHHHHHHHHH..........",
    ".......HHHHHHHHHHHHHHHHH........",
    "......HHHhHHHHhHHHHhHHHHH.......",
    "......HHhhHHHhhHHHhhHHHHH.......",
    ".....hhHHHHHHHHHHHHHHHHHhh......",
    ".....hhhSSSSSSSSSSSSSSShhh......",
    ".....hhSSSSSSSSSSSSSSSSShh......",
    ".....hSSSSSSSSSSSSSSSSSSSh......",
    ".....SSSKKKKSSSSSSSKKKKSSS......",
    ".....SSSSSSSSSSSSSSSSSSSSS......",
    ".....SSSWEWSSSSSSSSSWEWSSS......",
    ".....SSSSSSSSSSsSSSSSSSSSS......",
    "......SSSSSSSSSsSSSSSSSSS.......",
    "......SSSSSSSSsssSSSSSSSS.......",
    "......SSSSSSSSSSSSSSSSSSS.......",
    "......SSSSMWWWWWWWWMSSSSS.......",
    ".......SSSSMMMMMMMMSSSSS........",
    ".......sSSSSSSSSSSSSSSSs........",
    "........ssSSSSSSSSSSSss.........",
    "..........ssSSSSSSSss...........",
    "...........JJsssssJJ............",
    ".........JJJJCCCCCJJJJ..........",
    ".......JJJJJJCCCCCJJJJJJ........",
    ".....JJJJJJJjCGGGCjJJJJJJJ......",
    "....JJJJJJJJjCCGCCjJJJJJJJJ.....",
    "...JJJJJJJJJjCCCCCjJJJJJJJJJ....",
    "..JJJJJJJJJJjCCCCCjJJJJJJJJJJ...",
    "..JJJJJPJJJJjCCCCCjJJJJPJJJJJ...",
    "..JJJJJJJJJJjCCCCCjJJJJJJJJJJ...",
    "..JJJJJJJJJJjCCCCCjJJJJJJJJJJ...",
]
draw(HAKARI, {'H': 0xD8C08A, 'h': 0x9A8458, 'S': 0xF0CCAE, 's': 0xD2A88A, 'K': 0x3A2A20, 'W': 0xFFFFFF, 'E': 0x2A6A48,
              'M': 0x7A2A2A, 'J': 0xEDE6D6, 'j': 0xB8B0A0, 'C': 0x18181E, 'G': 0xF0C040, 'P': 0xFF3FA0}, 'hakari')
print('portraits written to', os.path.normpath(OUT))
