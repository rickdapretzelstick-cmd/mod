#!/usr/bin/env python3
"""
Installs the relic-trial rooms (the "Relic Trial Rooms V2" Blockbench pack) into the mod:

    python3 tools/install_trial_rooms.py [tools/blockbench/trial_rooms_v2]

The pack is five Generic-format .bbmodel environments (16 units to a block, Y up, entrance on +Z, feature wall on -Z)
sharing one 128x128 atlas of 16 px material tiles. They are editable sources, not Minecraft models, so each room is
split in two here:

  - Architecture that lines up with the block grid (floors, walls, column shafts, beams, piers, parapets, the target
    wall) becomes a block plan: data/jjk/trial_rooms/<kit>.json, read by progression.training.TrialRooms and placed
    when a trial opens (and cleared with the realm arena when it closes). A cube becomes blocks only if the voxels stay
    within 0.5 of each of its faces, so no block swallows a detail in front of it; anything else stays a model.
  - Everything else (trims, furniture, lanterns, rings, reels, ivy...) becomes prop models in
    models/bb/trial_<kit>*.bbmodel for the mod's BbModel loader, drawn by a handful of TrialPropEntity instances per
    room (never one entity per cube). Group-name tokens tell the renderer what to do with a group:
      #glow                 emissive (drawn full bright: the atlas colours don't light anything by themselves)
      #spin:ax,ay,az,deg    turns about the group's origin, deg per tick (client side, from the shared game time)
      #bob:amp,period       floats up and down
      #flicker              a candle's flame: a small scale jitter
      #rect                 not drawn: rectangles the renderer draws into (the CRT's screen, the six reel windows)

Each room is turned 180 degrees so the participant faces +Z (south), as every training scene expects, and shifted by
half a block on X and/or Z where that lines its columns up with the block grid (chosen per room, below). Markers are
written in that same room-local frame: blocks, relative to the arena origin's corner.

What is deliberately left out: the Yuta room's "Rika guardian relief" group (the trial shows the mod's own Rika there,
on a low dais this script adds), the CRT's baked scanlines and glass (an animated screen replaces them), the reels'
painted icons (the server's reel symbols replace them) and the floor slabs (the block floor is the floor).

Also writes textures/entity/trial_atlas.png (the pack atlas), trial_crt_static.png (CRT static frames) and
trial_reel_symbols.png (the six reel symbols, in the order the Hakari scene numbers them).
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'blockbench', 'trial_rooms_v2')
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'jjk')
DATA = os.path.join(RES, 'data', 'jjk', 'trial_rooms')

KEYS = ['slate', 'stone', 'dark', 'trim', 'wood', 'woodlight', 'red', 'cloth', 'gold', 'cream', 'white', 'quartz', 'cyan',
        'blue', 'purple', 'pink', 'green', 'leaf', 'paper', 'black', 'orange', 'violet']
GLOW = {'cyan', 'blue', 'purple', 'pink', 'green', 'orange'}
# Coloured, but not lights.
NO_GLOW = {'wisteria cluster', 'reel jewel panel'}

BLOCKS = {'slate': 'minecraft:polished_deepslate', 'stone': 'minecraft:polished_andesite', 'dark': 'minecraft:polished_blackstone',
          'trim': 'minecraft:polished_andesite', 'wood': 'minecraft:dark_oak_planks', 'woodlight': 'minecraft:spruce_planks',
          'red': 'minecraft:red_concrete', 'white': 'minecraft:smooth_quartz', 'quartz': 'minecraft:calcite',
          'gold': 'minecraft:yellow_terracotta', 'cream': 'minecraft:smooth_sandstone', 'violet': 'minecraft:purple_terracotta'}

# Cubes that may become blocks (if they line up with the grid well enough).
ARCHITECTURE = {'aged wall panel', 'side wainscot', 'shaft', 'side entablature', 'blackstone entablature', 'arch upright',
                'back shrine pier', 'low blast parapet', 'gallery beam', 'target fortification'}
# Replaced by something else (see the docstring).
SKIP = {'individual floor slab', 'foundation', 'VHS scanline', 'diamond icon', 'seven top', 'seven diagonal'}
SKIP_GROUPS = {'Rika guardian relief'}
FLOOR_Y = 0.12  # the pack's floor slabs' top: the block floor's top (y 0)
TOL = 0.5
# Entablatures sit on the capitals 0.7 thick: a block row may stand 0.52 proud of them (nothing sits on them).
TOL_FOR = {'side entablature': 0.55, 'blackstone entablature': 0.55}

# The enclosure each room is closed in (walls at its full bounds, props included, and a ceiling), so the realm's void
# never shows: wall, pilaster (every 4 blocks), base course, ceiling, and the visible light fixture set in the ceiling
# every 5 blocks (a lantern hangs one below it where 'hang' is set).
SHELLS = {
    'yuji': dict(wall='minecraft:spruce_planks', pilaster='minecraft:dark_oak_log', base='minecraft:dark_oak_planks',
                 ceiling='minecraft:dark_oak_planks', fixture='minecraft:lantern[hanging=true]', hang=True),
    'gojo': dict(wall='minecraft:quartz_bricks', pilaster='minecraft:quartz_pillar', base='minecraft:smooth_quartz',
                 ceiling='minecraft:smooth_quartz', fixture='minecraft:sea_lantern'),
    'yuta': dict(wall='minecraft:polished_blackstone_bricks', pilaster='minecraft:polished_deepslate', base='minecraft:amethyst_block',
                 ceiling='minecraft:deepslate_tiles', fixture='minecraft:pearlescent_froglight'),
    'ryu': dict(wall='minecraft:stone_bricks', pilaster='minecraft:polished_andesite', base='minecraft:deepslate_bricks',
                ceiling='minecraft:deepslate_bricks', fixture='minecraft:ochre_froglight'),
    'hakari': dict(wall='minecraft:red_terracotta', pilaster='minecraft:yellow_terracotta', base='minecraft:yellow_terracotta',
                   ceiling='minecraft:dark_oak_planks', fixture='minecraft:glowstone'),
}

ROOMS = {
    'yuji': dict(title='The Cursed Screening Room', shift=(0.0, 0.0), rim='minecraft:polished_blackstone',
                 # The studs sat on the panels' face; the block wall's face is 0.45 further in.
                 nudge={'rear timber stud': (0, 0, 0.36)},
                 # The emotional-control trial is watched from the centre sofa, the doll on the lap.
                 markers={'player': (0, 1.23, 7.95), 'player_floor': (0, 0, 6.0), 'television': (0, 3.67, -9.5),
                          'doll': (0, 1.35, 7.45)},
                 lights=12),
    'gojo': dict(title='Hall of Impossible Precision', shift=(0.5, 0.5), rim='minecraft:polished_andesite',
                 markers={'player': (0, 0.12, 9.0), 'blue_core': (0, 5.0, 0)}, lights=13),
    'yuta': dict(title='Shrine of the Bound Promise', shift=(0.5, 0.5), rim='minecraft:polished_blackstone',
                 markers={'player': (0, 0.12, 7.0), 'sword': (0, 2.0, -0.5), 'rika': (0, 1.32, -8.6)}, lights=12),
    'ryu': dict(title='Granite Output Crucible', shift=(0.5, 0.0), rim='minecraft:polished_blackstone',
                blocks={'stone': 'minecraft:stone_bricks'},
                slabs={'firing dais': 'minecraft:polished_deepslate_slab[type=bottom]'},
                nudge={'firing plate': (0, 0.07, 0), 'firing plate inset': (0, 0.07, 0)},
                markers={'player': (0, 0.62, 14.0), 'target': (0, 6.0, -18.6)}, lights=12),
    'hakari': dict(title='The Restless Jackpot Palace', shift=(0.5, 0.5), rim='minecraft:polished_blackstone',
                   markers={'player': (0, 0.12, 8.0), 'reels': (0, 6.0, -10.0)}, lights=13),
}


def mat(e):
    u, v = e['faces']['north']['uv'][:2]
    return KEYS[int(v) // 16 * 8 + int(u) // 16]


class Room:
    def __init__(self, kit, cfg):
        self.kit, self.cfg = kit, cfg
        self.sx, self.sz = cfg['shift']

    # Design (pack) coordinates, blocks -> room-local blocks: turned 180 degrees about Y, shifted, floor at 0.
    def pt(self, x, y, z):
        return (-x + self.sx, y - FLOOR_Y, -z + self.sz)

    def box(self, a, b):
        p, q = self.pt(*a), self.pt(*b)
        return [min(p[i], q[i]) for i in range(3)], [max(p[i], q[i]) for i in range(3)]


def voxel_axis(lo, hi):
    """The block range [a, b) standing in for [lo, hi] on one axis, and how far its faces stray from the cube's."""
    a = [k for k in range(math.floor(lo) - 1, math.ceil(hi) + 1) if min(k + 1, hi) - max(k, lo) >= 0.5]
    if not a:
        c = math.floor((lo + hi) / 2)
        a = [c]
    return a[0], a[-1] + 1, max(abs(a[0] - lo), abs(a[-1] + 1 - hi))


def install(kit, cfg):
    d = json.load(open(os.path.join(SRC, f'{kit}_trial_v2.bbmodel')))
    el = {e['uuid']: e for e in d['elements']}
    room = Room(kit, cfg)
    palette = dict(BLOCKS, **cfg.get('blocks', {}))
    nudge = cfg.get('nudge', {})
    blocks = {}  # (x,y,z) -> block id
    floor = None
    model = {}  # output model name -> {group name -> [cubes]}
    lights = []
    rects = {}
    stats = {'blocks': 0, 'model': 0, 'skipped': 0, 'relief': 0}

    def add_cube(target, group, e, a, b):
        model.setdefault(target, {}).setdefault(group, []).append((e, a, b))
        stats['model'] += 1

    ring_n = {}
    for g in d['outliner']:
        gname = g['name']
        for u in g['children']:
            e = el[u]
            name, m = e['name'], mat(e)
            if gname in SKIP_GROUPS:
                stats['relief'] += 1
                continue
            a = [e['from'][i] / 16 for i in range(3)]
            b = [e['to'][i] / 16 for i in range(3)]
            if name in nudge:
                a = [a[i] + nudge[name][i] for i in range(3)]
                b = [b[i] + nudge[name][i] for i in range(3)]
            if name == 'foundation':
                floor = (a, b)
            if name in SKIP:
                stats['skipped'] += 1
                continue
            lo, hi = room.box(a, b)
            rotated = any(abs(r) > 1e-6 for r in e['rotation'])
            # Architecture: blocks where the grid allows.
            if name in ARCHITECTURE and not rotated:
                ax = [voxel_axis(lo[i], hi[i]) for i in range(3)]
                if max(t[2] for t in ax) <= TOL_FOR.get(name, TOL):
                    for x in range(ax[0][0], ax[0][1]):
                        for y in range(ax[1][0], ax[1][1]):
                            for z in range(ax[2][0], ax[2][1]):
                                blocks[(x, y, z)] = palette.get(m, BLOCKS['slate'])
                    stats['blocks'] += 1
                    continue
            if name in cfg.get('slabs', {}):
                ax = [voxel_axis(lo[i], hi[i]) for i in (0, 2)]
                for x in range(ax[0][0], ax[0][1]):
                    for z in range(ax[1][0], ax[1][1]):
                        blocks[(x, 0, z)] = cfg['slabs'][name]
                stats['blocks'] += 1
                continue
            glow = m in GLOW and name not in NO_GLOW
            if name == 'lantern core':
                c = [(lo[i] + hi[i]) / 2 for i in range(3)]
                lights.append([math.floor(c[0]), math.floor(c[1]), math.floor(c[2])])
            target, group = f'trial_{kit}', gname
            # What each room animates, or draws into.
            if kit == 'yuji' and name == 'CRT glass':
                rects.setdefault('screen', []).append((lo, hi))
                add_cube(f'trial_{kit}_crt', 'screen#rect', e, a, b)
                continue
            if kit == 'gojo' and name == 'voxel energy core':
                add_cube(f'trial_{kit}_blue', 'core' + ('#glow' if glow else ''), e, a, b)
                continue
            if kit == 'gojo' and name in ('levitation orbit',):
                y = round(a[1], 1)
                spin = {3.0: 0.55, 6.99: -0.4, 7.0: -0.4, 8.8: 0.3}.get(y, 0.4)
                group = f'orbit_{y}#spin:0,1,0,{spin}'
            elif kit == 'gojo' and name == 'vertical precision reticle':
                group = 'reticle#spin:0,1,0,0.25'
            elif kit == 'gojo' and name in ('suspended quartz fragment', 'fragment energy node'):
                group = 'fragments#spin:0,1,0,-0.18'
            elif kit == 'gojo' and name == 'floor calibration ring':
                group = 'calibration#spin:0,1,0,0.12'
            elif kit == 'gojo' and name == 'station core':
                c = [(a[i] + b[i]) / 2 for i in range(3)]
                group = f'station_{round(c[0])}_{round(c[2])}#spin:0,1,0,2.2'
            elif kit == 'yuta' and name in ('white blade', 'blade light', 'tsuba', 'sword grip'):
                add_cube(f'trial_{kit}_blade', 'blade' + ('#glow' if glow else ''), e, a, b)
                continue
            elif kit == 'yuta' and name == 'candle flame':
                c = [(a[i] + b[i]) / 2 for i in range(3)]
                group = f'flame_{round(c[0])}_{round(c[2])}#flicker'
            elif kit == 'ryu' and (name in ('target concentric square',) or name == 'target masonry block' and abs((a[0] + b[0]) / 2) < 4.6
                                   and 1.5 < (a[1] + b[1]) / 2 < 10.5):
                add_cube(f'trial_{kit}_target', 'face' + ('#glow' if glow else ''), e, a, b)
                continue
            elif kit == 'hakari' and name == 'reel jewel panel':
                rects.setdefault('reels', []).append((lo, hi))
                add_cube(target, 'windows#rect', e, a, b)
            elif kit == 'hakari' and name == 'suspended diamond':
                c = [(a[i] + b[i]) / 2 for i in range(3)]
                group = f'luck_{round(c[0])}_{round(c[2])}#bob:0.25,90#spin:0,1,0,1.2'
            if glow:
                group += '#glow'
            add_cube(target, group, e, a, b)

    # The block floor: one layer under y 0, its tiles taken from the pack's slab pattern; where a half-block shift leaves
    # a half-covered edge block, that ring is the rim.
    slabs = [(mat(e), [e['from'][i] / 16 for i in range(3)], [e['to'][i] / 16 for i in range(3)]) for e in d['elements']
             if e['name'] == 'individual floor slab']
    flo, fhi = room.box(*floor)
    fx = range(math.floor(flo[0] + 0.5 - 1e-9) if room.sx else math.floor(flo[0]), math.ceil(fhi[0] - 0.5 + 1e-9) if room.sx else math.ceil(fhi[0]))
    fz = range(math.floor(flo[2] + 0.5 - 1e-9) if room.sz else math.floor(flo[2]), math.ceil(fhi[2] - 0.5 + 1e-9) if room.sz else math.ceil(fhi[2]))
    for x in fx:
        for z in fz:
            rim = x + 1 > fhi[0] + 1e-9 or x < flo[0] - 1e-9 or z + 1 > fhi[2] + 1e-9 or z < flo[2] - 1e-9
            if rim:
                blocks[(x, -1, z)] = cfg['rim']
                continue
            cx, cz = x + 0.5, z + 0.5
            tile = 'slate'
            for m, a, b in slabs:
                lo, hi = room.box(a, b)
                if lo[0] <= cx <= hi[0] and lo[2] <= cz <= hi[2]:
                    tile = m
                    break
            blocks[(x, -1, z)] = palette.get(tile, BLOCKS['slate'])

    # Rika's dais, where the relief stood: low and dark, so she is lifted into view at the shrine's rear.
    if kit == 'yuta':
        for x in range(-2, 3):
            for z in range(7, 11):
                blocks[(x, 0, z)] = 'minecraft:polished_blackstone_bricks'
        for x in range(-1, 2):
            for z in range(8, 10):
                blocks[(x, 0, z)] = 'minecraft:chiseled_polished_blackstone'

    # The enclosure: walls just outside everything the room places or draws, a floor out to them, and a ceiling over it.
    pts = [k for k in blocks] + [(c[0], c[1], c[2]) for t in model.values() for cs in t.values() for (_, a, b) in cs
                                 for c in (room.box(a, b)[0], room.box(a, b)[1])]
    x0 = math.floor(min(q[0] for q in pts)) - 1
    x1 = math.ceil(max(q[0] for q in pts))
    z0 = math.floor(min(q[2] for q in pts)) - 1
    z1 = math.ceil(max(q[2] for q in pts))
    top = math.ceil(max(q[1] for q in pts)) + 1
    sh = SHELLS[kit]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if (x, -1, z) not in blocks:
                blocks[(x, -1, z)] = cfg['rim']
            blocks[(x, top, z)] = sh['ceiling']
            if edge:
                corner = x in (x0, x1) and z in (z0, z1)
                pil = corner or (x - x0) % 4 == 0 and z in (z0, z1) or (z - z0) % 4 == 0 and x in (x0, x1)
                for y in range(0, top):
                    blocks[(x, y, z)] = sh['base'] if y == 0 else sh['pilaster'] if pil else sh['wall']
    for x in range(x0 + 3, x1 - 1, 5):
        for z in range(z0 + 3, z1 - 1, 5):
            if sh.get('hang'):
                if (x, top - 1, z) not in blocks:
                    blocks[(x, top - 1, z)] = sh['fixture']
            else:
                blocks[(x, top, z)] = sh['fixture']

    # Lights: each lantern of the pack, and an even fill at two heights so no corner of the room is dark.
    out_lights = []
    for p in lights:
        if tuple(p) not in blocks:
            out_lights.append(p + [14])
    taken = {tuple(l[:3]) for l in out_lights}
    for x in range(x0 + 2, x1 - 1, 4):
        for z in range(z0 + 2, z1 - 1, 4):
            for y, lv in ((3, cfg['lights']), (top - 3, cfg['lights'] - 1)):
                if (x, y, z) not in blocks and (x, y, z) not in taken:
                    out_lights.append([x, y, z, lv])
                    taken.add((x, y, z))
    xs = [k[0] for k in blocks]
    zs = [k[2] for k in blocks]

    # Markers, in the room frame.
    markers = {k: [round(c, 4) for c in room.pt(*v)] for k, v in cfg['markers'].items()}

    # Block plan: palette and runs along X.
    pal = sorted(set(blocks.values()))
    runs = []
    for (x, y, z), bid in sorted(blocks.items(), key=lambda t: (t[0][1], t[0][2], t[0][0])):
        i = pal.index(bid)
        if runs and runs[-1][1] == y and runs[-1][2] == z and runs[-1][3] == x and runs[-1][4] == i:
            runs[-1][3] = x + 1
        else:
            runs.append([x, y, z, x + 1, i])
    allx = xs + [c for t in model.values() for cs in t.values() for (_, a, b) in cs for c in (room.box(a, b)[0][0], room.box(a, b)[1][0])]
    ally = [k[1] for k in blocks] + [c for t in model.values() for cs in t.values() for (_, a, b) in cs for c in (room.box(a, b)[0][1], room.box(a, b)[1][1])]
    allz = zs + [c for t in model.values() for cs in t.values() for (_, a, b) in cs for c in (room.box(a, b)[0][2], room.box(a, b)[1][2])]
    bounds = [math.floor(min(allx)), math.floor(min(ally)), math.floor(min(allz)), math.ceil(max(allx) + 1), math.ceil(max(ally) + 1), math.ceil(max(allz) + 1)]
    props = []
    for name in sorted(model):
        kind = {'_crt': 'crt', '_blue': 'blue', '_blade': 'glow', '_target': 'target'}.get(name[len(f'trial_{kit}'):], 'static')
        if kit == 'hakari' and name == f'trial_{kit}':
            kind = 'reels'
        props.append({'model': name, 'kind': kind})
    plan = {'kit': kit, 'title': cfg['title'], 'biome': f'jjk:trial_{kit}', 'shift': [room.sx, room.sz], 'bounds': bounds, 'markers': markers,
            'palette': pal, 'runs': runs, 'lights': out_lights, 'props': props}
    os.makedirs(DATA, exist_ok=True)
    with open(os.path.join(DATA, f'{kit}.json'), 'w') as f:
        json.dump(plan, f, separators=(',', ':'))

    # Prop models: one file each, groups as the renderer reads them; rect groups sorted for the renderer.
    for name, groups in model.items():
        elements, outliner, n = [], [], 0
        for gname in sorted(groups):
            cubes = groups[gname]
            if '#rect' in gname:
                # Reel windows: by tower from the player's left (+X), upper panel first.
                cubes = sorted(cubes, key=lambda t: (-round((room.box(t[1], t[2])[0][0] + room.box(t[1], t[2])[1][0]) / 2, 1),
                                                    -room.box(t[1], t[2])[0][1]))
            children = []
            pivot = [0, 0, 0]
            if '#spin' in gname or '#bob' in gname or '#flicker' in gname:
                # The pivot: the centre of the group (the Blue's, a ring's, a candle's...), on the room's axis for orbits.
                los = [room.box(a, b)[0] for (_, a, b) in cubes]
                his = [room.box(a, b)[1] for (_, a, b) in cubes]
                pivot = [(min(l[i] for l in los) + max(h[i] for h in his)) / 2 for i in range(3)]
                if gname.split('#')[0] in ('fragments', 'reticle', 'calibration') or gname.startswith('orbit_'):
                    c = room.pt(0, 5.0, 0)
                    pivot = [c[0], pivot[1], c[2]]
            for e, a, b in cubes:
                lo, hi = room.box(a, b)
                o = [(e['origin'][i] / 16) for i in range(3)]
                oo = room.pt(o[0] + nudge.get(e['name'], (0, 0, 0))[0], o[1] + nudge.get(e['name'], (0, 0, 0))[1], o[2] + nudge.get(e['name'], (0, 0, 0))[2])
                r = e['rotation']
                uid = f'{kit[0]}{n}'
                n += 1
                face = e['faces']['north']['uv']
                elements.append({'uuid': uid, 'from': [round(v * 16, 3) for v in lo], 'to': [round(v * 16, 3) for v in hi],
                                 'origin': [round(v * 16, 3) for v in oo], 'rotation': [-r[0], r[1], -r[2]],
                                 'faces': {f: {'uv': face} for f in ('north', 'south', 'east', 'west', 'up', 'down')}})
                children.append(uid)
            outliner.append({'name': gname, 'origin': [round(v * 16, 3) for v in pivot], 'rotation': [0, 0, 0], 'children': children})
        out = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': name,
               'resolution': {'width': 128, 'height': 128}, 'elements': elements, 'outliner': outliner}
        with open(os.path.join(ASSETS, 'models', 'bb', f'{name}.bbmodel'), 'w') as f:
            json.dump(out, f, separators=(',', ':'))
    print(f'{kit}: {len(blocks)} blocks in {len(runs)} runs, {stats["blocks"]} cubes as blocks, {stats["model"]} as model'
          f' ({len(model)} props), {stats["skipped"]} replaced, {stats["relief"]} relief cubes left out, {len(out_lights)} lights,'
          f' bounds {bounds}')


def textures():
    tex = os.path.join(ASSETS, 'textures', 'entity')
    Image.open(os.path.join(SRC, 'trial_atlas.png')).convert('RGBA').save(os.path.join(tex, 'trial_atlas.png'))
    # CRT static: 16 frames of grey noise under dark scanlines, a brighter rolling band moving down through them.
    rnd = random.Random(7)
    w, h, frames = 64, 48, 16
    img = Image.new('RGBA', (w, h * frames))
    px = img.load()
    for f in range(frames):
        band = (f * h // frames + 8) % h
        for y in range(h):
            line = 0.55 if y % 2 else 1.0
            roll = 1.35 if abs(y - band) < 3 else 1.0
            for x in range(w):
                v = int(min(255, (90 + rnd.random() * 140) * line * roll))
                px[x, f * h + y] = (v, v, v, 255)
    img.save(os.path.join(tex, 'trial_crt_static.png'))
    # Reel symbols, 32x32 each, top to bottom: 0 the seven, 1 emerald, 2 diamond, 3 the bust bar, 4 coin, 5 star.
    s = 32
    img = Image.new('RGBA', (s, s * 6), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def diamond(k, fill, edge):
        y0 = k * s
        d.polygon([(16, y0 + 3), (28, y0 + 16), (16, y0 + 29), (4, y0 + 16)], fill=edge)
        d.polygon([(16, y0 + 6), (25, y0 + 16), (16, y0 + 26), (7, y0 + 16)], fill=fill)
        d.line([(16, y0 + 7), (11, y0 + 16)], fill=(255, 255, 255, 255), width=2)

    y0 = 0
    d.rectangle([5, y0 + 4, 27, y0 + 9], fill=(255, 83, 185, 255))
    d.polygon([(21, y0 + 9), (27, y0 + 9), (15, y0 + 28), (9, y0 + 28)], fill=(255, 83, 185, 255))
    d.rectangle([5, y0 + 4, 27, y0 + 5], fill=(255, 200, 235, 255))
    diamond(1, (75, 198, 145, 255), (20, 90, 60, 255))
    diamond(2, (67, 160, 255, 255), (25, 60, 160, 255))
    y0 = 3 * s
    d.rectangle([3, y0 + 11, 28, y0 + 20], fill=(140, 37, 62, 255))
    d.rectangle([5, y0 + 13, 26, y0 + 18], fill=(230, 70, 70, 255))
    y0 = 4 * s
    d.ellipse([5, y0 + 5, 27, y0 + 27], fill=(150, 100, 30, 255))
    d.ellipse([8, y0 + 8, 24, y0 + 24], fill=(240, 190, 80, 255))
    d.rectangle([14, y0 + 11, 18, y0 + 21], fill=(150, 100, 30, 255))
    y0 = 5 * s
    pts = []
    for i in range(10):
        r = 13 if i % 2 == 0 else 6
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((16 + r * math.cos(a), y0 + 17 + r * math.sin(a)))
    d.polygon(pts, fill=(190, 100, 237, 255))
    img.save(os.path.join(tex, 'trial_reel_symbols.png'))


if __name__ == '__main__':
    os.makedirs(os.path.join(ASSETS, 'models', 'bb'), exist_ok=True)
    for kit, cfg in ROOMS.items():
        install(kit, cfg)
    textures()
