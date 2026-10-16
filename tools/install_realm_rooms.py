#!/usr/bin/env python3
"""
Installs the cursed realm environments (the "Cursed Realm Blockbench Pack") as the realm arenas' layouts:

    python3 tools/install_realm_rooms.py [tools/blockbench/cursed_realms]

The pack is ten Generic-format .bbmodel environments (16 units to a block, Y up, entrance toward +Z) on one 128x128
atlas of 16 px material tiles (the relic-trial atlas plus earth, soil and ash). Each is matched to the realm destination
it was designed for (DESTINATIONS, below; see the README's cursed-realm section for what has no asset) and split the way
tools/install_trial_rooms.py splits the trial rooms:

  - Blocks (data/jjk/realm_rooms/<id>.json, read by progression.investigation.RealmRooms): any upright cube at least
    0.6 thick that the grid can hold within half a block of each face (walls, masonry, shafts, stages, tables, quarry
    blocks, anchors), the floor (its tiles from the pack's slab pattern), and the walkable surfaces that are thinner
    than a block (terraces, tiers, landings, hovering platforms, stairs) as a height field of full blocks and slabs, so
    curses and players stand where the scenery shows ground. Every realm is closed in (walls at its full bounds, props
    included, and a ceiling with light fixtures) and lit evenly; a biome per realm sets its light colour over the
    realm's crimson.
  - Props (models/bb/realm_<id>.bbmodel): everything else, drawn by TrialPropEntity (group tokens as in the trial
    tool: #glow, #spin, #bob). Yuta's courtyard leaves its rear terrace empty for the mod's own Rika: a "rika" prop,
    drawn from models/bb/rika.bbmodel with her own idle clip, checked here against the terrace's reserved clearance.

The room keeps the pack's orientation (arrival at +Z, facing -Z) and is shifted by half a block on X and/or Z where that
puts more of its architecture on the grid. Markers (arrival, the encounter centre, the curses' spots, a guardian's spawn
and the centre its phase beats change) are in room-local blocks from the arena origin's corner. Curse spots are chosen
on real ground with headroom, around the encounter centre.
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'blockbench', 'cursed_realms')
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'jjk')
DATA = os.path.join(RES, 'data', 'jjk', 'realm_rooms')
BIOMES = os.path.join(RES, 'data', 'jjk', 'worldgen', 'biome')

KEYS = ['slate', 'stone', 'dark', 'trim', 'wood', 'woodlight', 'red', 'cloth', 'gold', 'cream', 'white', 'quartz', 'cyan',
        'blue', 'purple', 'pink', 'green', 'leaf', 'paper', 'black', 'orange', 'violet', 'earth', 'soil', 'ash']
GLOW = {'cyan', 'blue', 'purple', 'pink', 'green', 'orange'}
NO_GLOW = {'garden vegetation', 'purple blossom cluster', 'reflecting channel', 'shallow puddle', 'cinema seat', 'cinema seat back',
           'spectator seat pad', 'green gaming felt', 'side acoustic panel', 'torn curtain fold'}
BLOCKS = {'slate': 'minecraft:polished_deepslate', 'stone': 'minecraft:stone_bricks', 'dark': 'minecraft:polished_blackstone',
          'trim': 'minecraft:polished_andesite', 'wood': 'minecraft:dark_oak_planks', 'woodlight': 'minecraft:spruce_planks',
          'red': 'minecraft:red_terracotta', 'white': 'minecraft:smooth_quartz', 'quartz': 'minecraft:calcite',
          'gold': 'minecraft:yellow_terracotta', 'cream': 'minecraft:bone_block', 'paper': 'minecraft:bone_block',
          'violet': 'minecraft:purple_terracotta', 'cloth': 'minecraft:gray_wool', 'earth': 'minecraft:moss_block',
          'soil': 'minecraft:coarse_dirt', 'ash': 'minecraft:tuff', 'leaf': 'minecraft:moss_block', 'black': 'minecraft:black_concrete',
          'green': 'minecraft:green_concrete'}
SLABS = {'slate': 'minecraft:polished_deepslate_slab', 'stone': 'minecraft:stone_brick_slab', 'dark': 'minecraft:polished_blackstone_slab',
         'white': 'minecraft:smooth_quartz_slab', 'quartz': 'minecraft:smooth_quartz_slab', 'wood': 'minecraft:dark_oak_slab',
         'woodlight': 'minecraft:spruce_slab', 'gold': 'minecraft:cut_sandstone_slab', 'red': 'minecraft:red_nether_brick_slab',
         'trim': 'minecraft:polished_andesite_slab', 'violet': 'minecraft:purpur_slab'}
# Never blocks: replaced (the floor is the floor), thin walkable surfaces (the height field), or kept as models.
FLOOR = {'individual floor slab', 'natural ground patch'}
WALKABLE = {'crater terraced rock', 'seating terrace', 'spectator tier', 'ring base tier', 'central hovering platform',
            'broken landing', 'suspended stair', 'sunken garden bed', 'rear guardian terrace', 'exposed floor slab', 'rear balcony',
            'hunting platform', 'altar terrace', 'arrival step', 'fighting mat', 'stage', 'ring access step', 'rear quarry shelf'}
NOT_BLOCK = {'projection screen', 'screen frame', 'voxel energy core', 'marquee housing', 'corrupted canopy', 'sealed offering chest',
             'hanging broken bucket', 'fountain plinth'}
FLOOR_Y = 0.12

# Pack file -> realm destination (CursedRealms layout id), and how each looks: its closing shell, light and biome tint.
DESTINATIONS = {
    'gojo_realm': dict(id='distance_realm', title='Fractured Infinity', line='Space folds back on itself here. The stairs lead nowhere twice.',
                       shell=('minecraft:quartz_bricks', 'minecraft:quartz_pillar', 'minecraft:smooth_quartz', 'minecraft:sea_lantern'),
                       light=14, tint=('#3a4c62', '#cfe6ff', '#0e1620'), spin={'anomaly orbit': 0.5, 'vertical anomaly orbit': 0.3,
                                                                                 'voxel energy core': 1.5}, bob={'floating architectural debris'}),
    'yuji_realm': dict(id='theater_realm', title='The Last Screening', line='The film is still running. Every seat is facing you.',
                       shell=('minecraft:dark_oak_planks', 'minecraft:dark_oak_log', 'minecraft:spruce_planks', 'minecraft:lantern[hanging=true]'),
                       light=14, tint=('#4a3a2a', '#ffc890', '#1c140e')),
    'yuta_realm': dict(id='chapel_realm', title='Courtyard of the Unbroken Promise', line='A promise was kept here. Something still keeps it.',
                       shell=('minecraft:polished_blackstone_bricks', 'minecraft:polished_deepslate', 'minecraft:deepslate_tiles',
                              'minecraft:pearlescent_froglight'), light=15, tint=('#4a3660', '#e8d4ff', '#160e1e'), rika=1.8),
    'ryu_realm': dict(id='crater_realm', title='The Shattered Impact Basin', line='Everything here has already been struck once. The ground is still warm.',
                      shell=('minecraft:stone_bricks', 'minecraft:polished_andesite', 'minecraft:deepslate_bricks', 'minecraft:ochre_froglight'),
                      light=15, tint=('#584c38', '#ffdeb0', '#1a1610')),
    'hakari_realm': dict(id='fight_club_realm', title='The House Always Fights', line='The house is watching. The bell has already rung.',
                         shell=('minecraft:red_terracotta', 'minecraft:yellow_terracotta', 'minecraft:dark_oak_planks', 'minecraft:glowstone'),
                         light=14, tint=('#523022', '#ffcfa2', '#1e0e0c')),
    'rifle_forest': dict(id='forest_realm', title='The Watching Woods',
                         line='The view through the scope, from the inside. The trees lean wrong, and something is moving between them.',
                         shell=('minecraft:dark_oak_log', 'minecraft:stripped_dark_oak_log', 'minecraft:dark_oak_planks', 'minecraft:shroomlight'),
                         light=14, tint=('#3c5034', '#ecffd0', '#0e140c'), spots=['stalker_spawn']),
    'guardian_claw': dict(id='guardian_claw_hunter', title='The Ribbed Hollow', line='It has been waiting for you.',
                          shell=('minecraft:tuff_bricks', 'minecraft:polished_tuff', 'minecraft:deepslate_tiles', 'minecraft:ochre_froglight'),
                          light=14, tint=('#40403a', '#f0f0d0', '#141410'), guardian=True),
    'guardian_stalker': dict(id='guardian_pale_stalker', title='The Silent Courtyard', line='It has been waiting for you.',
                             shell=('minecraft:stone_bricks', 'minecraft:calcite', 'minecraft:deepslate_tiles', 'minecraft:sea_lantern'),
                             light=14, tint=('#384452', '#d8e8ff', '#10141a'), guardian=True),
    'guardian_cyclops': dict(id='guardian_cyclops_bruiser', title='The Sunken Quarry', line='It has been waiting for you.',
                             shell=('minecraft:stone_bricks', 'minecraft:polished_andesite', 'minecraft:deepslate_bricks', 'minecraft:ochre_froglight'),
                             light=14, tint=('#44402e', '#ffe2b0', '#18140e'), guardian=True),
    'guardian_veiled': dict(id='guardian_veiled_reacher', title='The Hanging Sanctuary', line='It has been waiting for you.',
                            shell=('minecraft:polished_blackstone_bricks', 'minecraft:red_terracotta', 'minecraft:dark_oak_planks',
                                   'minecraft:pearlescent_froglight'), light=14, tint=('#40283a', '#ffd0e8', '#180c14'), guardian=True),
}


def mat(e):
    u, v = e['faces']['north']['uv'][:2]
    return KEYS[int(v) // 16 * 8 + int(u) // 16]


def voxel_axis(lo, hi):
    a = [k for k in range(math.floor(lo) - 1, math.ceil(hi) + 1) if min(k + 1, hi) - max(k, lo) >= 0.5]
    if not a:
        a = [math.floor((lo + hi) / 2)]
    return a[0], a[-1] + 1, max(abs(a[0] - lo), abs(a[-1] + 1 - hi))


def install(name, cfg, manifest):
    d = json.load(open(os.path.join(SRC, f'{name}.bbmodel')))
    els = d['elements']
    by_id = {e['uuid']: e for e in els}
    group_of = {u: g['name'] for g in d['outliner'] for u in g['children']}

    def boxes(sx, sz):
        out = []
        for e in els:
            a = [e['from'][0] / 16 + sx, e['from'][1] / 16 - FLOOR_Y, e['from'][2] / 16 + sz]
            b = [e['to'][0] / 16 + sx, e['to'][1] / 16 - FLOOR_Y, e['to'][2] / 16 + sz]
            out.append((e, a, b))
        return out

    def blockable(e, a, b):
        if e['name'] in FLOOR or e['name'] in WALKABLE or e['name'] in NOT_BLOCK or any(abs(r) > 1e-6 for r in e['rotation']):
            return None
        if min(b[i] - a[i] for i in range(3)) < 0.6:
            return None
        ax = [voxel_axis(a[i], b[i]) for i in range(3)]
        return ax if max(t[2] for t in ax) <= 0.5 else None

    # The half-block shift that puts the most architecture on the grid.
    best = None
    for sx in (0.0, 0.5):
        for sz in (0.0, 0.5):
            vol = sum((b[0] - a[0]) * (b[1] - a[1]) * (b[2] - a[2]) for e, a, b in boxes(sx, sz) if blockable(e, a, b))
            if best is None or vol > best[0] + 1e-6:
                best = (vol, sx, sz)
    _, sx, sz = best
    cubes = boxes(sx, sz)

    blocks = {}
    model = {}
    lights = []
    height = {}  # (x, z) -> (top, material) of the walkable surfaces
    for e, a, b in cubes:
        n, m = e['name'], mat(e)
        if n == 'foundation':
            foundation = (a, b)
            continue
        if n in FLOOR:
            continue
        ax = blockable(e, a, b)
        if ax:
            for x in range(ax[0][0], ax[0][1]):
                for y in range(ax[1][0], ax[1][1]):
                    for z in range(ax[2][0], ax[2][1]):
                        blocks[(x, y, z)] = BLOCKS.get(m, BLOCKS['slate'])
            continue
        if n in WALKABLE and not any(abs(r) > 1e-6 for r in e['rotation']) and b[1] < 9:
            # Its top as ground, column by column (where it covers the column's centre).
            for x in range(math.floor(a[0]), math.ceil(b[0])):
                for z in range(math.floor(a[2]), math.ceil(b[2])):
                    if a[0] <= x + 0.5 <= b[0] and a[2] <= z + 0.5 <= b[2]:
                        if b[1] > height.get((x, z), (-9, ''))[0]:
                            height[(x, z)] = (b[1], m, a[1])
            # A thin one still shows as itself, over the blocks standing in for it.
        glow = m in GLOW and n not in NO_GLOW
        if n == 'lantern core':
            c = [(a[i] + b[i]) / 2 for i in range(3)]
            lights.append([math.floor(c[0]), math.floor(c[1]), math.floor(c[2])])
        group = group_of.get(e['uuid'], 'scenery')
        if n in cfg.get('spin', {}):
            group = f'{n}#spin:0,1,0,{cfg["spin"][n]}'
        elif n in cfg.get('bob', set()):
            c = [(a[i] + b[i]) / 2 for i in range(3)]
            group = f'{n}_{round(c[0])}_{round(c[2])}#bob:0.3,{80 + (round(c[0] * 7 + c[2] * 3) % 40)}'
        if glow:
            group += '#glow'
        model.setdefault(group, []).append((e, a, b))

    # The walkable height field: full blocks up to the surface, a bottom slab for the half above.
    for (x, z), (top, m, bottom) in height.items():
        base = max(0, math.floor(bottom + 0.25))
        full = math.floor(top + 0.25)
        for y in range(base, full):
            blocks.setdefault((x, y, z), BLOCKS.get(m, BLOCKS['slate']))
        if top - full >= 0.25 and (x, full, z) not in blocks:
            blocks[(x, full, z)] = SLABS.get(m, 'minecraft:polished_deepslate_slab') + '[type=bottom]'

    # The floor: the foundation's footprint, tiled from the pack's pattern; a half-covered edge ring is the rim.
    fa, fb = foundation
    slab_tiles = [(mat(e), a, b) for e, a, b in cubes if e['name'] in FLOOR]
    x0f = math.floor(fa[0] + 0.5 - 1e-9) if sx else math.floor(fa[0])
    x1f = math.ceil(fb[0] - 0.5 + 1e-9) if sx else math.ceil(fb[0])
    z0f = math.floor(fa[2] + 0.5 - 1e-9) if sz else math.floor(fa[2])
    z1f = math.ceil(fb[2] - 0.5 + 1e-9) if sz else math.ceil(fb[2])
    for x in range(x0f, x1f):
        for z in range(z0f, z1f):
            tile = None
            for m, a, b in slab_tiles:
                if a[0] <= x + 0.5 <= b[0] and a[2] <= z + 0.5 <= b[2]:
                    tile = m
                    break
            blocks[(x, -1, z)] = BLOCKS.get(tile or 'slate', BLOCKS['slate']) if tile else 'minecraft:polished_blackstone'

    # Closed in: walls just outside everything, a floor out to them, a ceiling with fixtures every 5 blocks.
    pts = list(blocks) + [(c[0], c[1], c[2]) for cs in model.values() for (_, a, b) in cs for c in (a, b)]
    x0 = math.floor(min(q[0] for q in pts)) - 1
    x1 = math.ceil(max(q[0] for q in pts))
    z0 = math.floor(min(q[2] for q in pts)) - 1
    z1 = math.ceil(max(q[2] for q in pts))
    top = math.ceil(max(q[1] for q in pts)) + 1
    wall, pilaster, ceiling, fixture = cfg['shell']
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, -1, z) not in blocks:
                blocks[(x, -1, z)] = 'minecraft:polished_blackstone'
            blocks[(x, top, z)] = ceiling
            if x in (x0, x1) or z in (z0, z1):
                corner = x in (x0, x1) and z in (z0, z1)
                pil = corner or (x - x0) % 4 == 0 and z in (z0, z1) or (z - z0) % 4 == 0 and x in (x0, x1)
                for y in range(0, top):
                    blocks[(x, y, z)] = pilaster if pil or y == 0 else wall
    for x in range(x0 + 3, x1 - 1, 5):
        for z in range(z0 + 3, z1 - 1, 5):
            if fixture.endswith('[hanging=true]'):
                if (x, top - 1, z) not in blocks:
                    blocks[(x, top - 1, z)] = fixture
            else:
                blocks[(x, top, z)] = fixture
    out_lights = []
    taken = set()
    for p in lights:
        if tuple(p) not in blocks:
            out_lights.append(p + [14])
            taken.add(tuple(p))
    for x in range(x0 + 2, x1 - 1, 4):
        for z in range(z0 + 2, z1 - 1, 4):
            for y, lv in ((4, cfg['light']), (top - 3, cfg['light'] - 1)):
                if (x, y, z) not in blocks and (x, y, z) not in taken:
                    out_lights.append([x, y, z, lv])
                    taken.add((x, y, z))

    def solid(x, y, z):
        s = blocks.get((x, y, z))
        return s is not None and 'lantern' not in s

    def ground(x, z, limit=9):
        """The height a body stands at on column (x, z): over the topmost block below `limit` (a slab counts half)."""
        for y in range(limit, -2, -1):
            s = blocks.get((x, y, z))
            if s is not None and 'lantern' not in s:
                return y + (0.5 if 'type=bottom' in s else 1.0)
        return None

    def clear(x, z, g, h=3, r=0):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                for y in range(math.ceil(g - 0.01), math.ceil(g - 0.01) + h):
                    if solid(x + dx, y, z + dz):
                        return False
        return True

    def at(name_, v):
        """A pack marker (design blocks) in the room frame, set down on the ground under it."""
        x, y, z = v[0] + sx, v[1] - FLOOR_Y, v[2] + sz
        g = ground(math.floor(x), math.floor(z), math.floor(y) + 2)
        return [round(x, 3), round(g if g is not None else max(0.0, y), 3), round(z, 3)]

    marks = manifest['markers_blocks']
    markers = {k: at(k, v) for k, v in marks.items() if not k.startswith('existing_Rika_clearance')}
    if 'encounter_center' not in markers:
        markers['encounter_center'] = markers.get('combat_center', [sx, 0, sz])
    # The curses' spots: the named spawn first, then a spread round the encounter centre on open ground.
    spots = []
    for k in cfg.get('spots', []) + (['guardian_spawn'] if cfg.get('guardian') else []):
        if k in markers:
            spots.append(markers[k])
    if not cfg.get('guardian'):
        cx, cz = markers['encounter_center'][0], markers['encounter_center'][2]
        for rad in (5, 7, 9, 4, 11):
            for i in range(12):
                ang = 2 * math.pi * (i + (rad % 3) * 0.33) / 12
                x, z = cx + rad * math.cos(ang), cz + rad * math.sin(ang)
                bx, bz = math.floor(x), math.floor(z)
                if not (x0 + 2 <= bx <= x1 - 2 and z0 + 2 <= bz <= z1 - 2):
                    continue
                g = ground(bx, bz)
                if g is None or not clear(bx, bz, g, 3, 1):
                    continue
                if any((s[0] - bx - 0.5) ** 2 + (s[2] - bz - 0.5) ** 2 < 9 for s in spots):
                    continue
                if (bx + 0.5 - markers['arrival'][0]) ** 2 + (bz + 0.5 - markers['arrival'][2]) ** 2 < 49:
                    continue
                spots.append([bx + 0.5, g, bz + 0.5])
                if len(spots) >= 8:
                    break
            if len(spots) >= 8:
                break
    problems = []
    if len(spots) < (1 if cfg.get('guardian') else 3):
        problems.append(f'only {len(spots)} curse spots')
    ar = markers['arrival']
    if not clear(math.floor(ar[0]), math.floor(ar[2]), ar[1], 3):
        problems.append('arrival blocked')
    if cfg.get('guardian'):
        gs = markers['guardian_spawn']
        if not clear(math.floor(gs[0]), math.floor(gs[2]), gs[1], 4, 1):
            problems.append('guardian spawn blocked')
        # The first phase beat raises four 2x2 pillars round the fight's centre, off the line between challenger and
        # guardian: at the usual offsets where the floor is open, else the nearest open 2x2 of floor.
        cc = markers['combat_center']
        cx, cz = math.floor(cc[0]), math.floor(cc[2])

        def open2(x, z):
            return all(solid(x + dx, -1, z + dz) and all(not blocks.get((x + dx, y, z + dz)) or blocks[(x + dx, y, z + dz)].startswith('minecraft:light')
                                                         for y in range(0, 7)) for dx in (0, 1) for dz in (0, 1))

        for i, (px, pz) in enumerate(((6, 6), (-7, 6), (6, -7), (-7, -7))):
            spot = None
            for r in range(0, 5):
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if spot is None and max(abs(dx), abs(dz)) == r and abs(px + dx) >= 4 and abs(pz + dz) >= 4 and open2(cx + px + dx, cz + pz + dz):
                            spot = (px + dx, pz + dz)
            if spot is None:
                problems.append(f'no open floor for phase pillar {i}')
            else:
                markers[f'pillar_{i}'] = [cx + spot[0], 0, cz + spot[1]]

    props = [{'model': f'realm_{cfg["id"]}', 'kind': 'static'}]
    if cfg.get('rika'):
        # The mod's own Rika on the empty rear terrace: her model's real bounds (scaled for the presentation) must fit the
        # clearance the pack reserved, with room for her idle sway.
        rk = json.load(open(os.path.join(ASSETS, 'models', 'bb', 'rika.bbmodel')))
        lo = [min(e['from'][i] for e in rk['elements']) for i in range(3)]
        hi = [max(e['to'][i] for e in rk['elements']) for i in range(3)]
        sc = 0.85 / 16 * cfg['rika']
        size = [(hi[i] - lo[i]) * sc * 1.15 for i in range(3)]
        cmin, cmax = marks['existing_Rika_clearance_min'], marks['existing_Rika_clearance_max']
        room = [cmax[i] - cmin[i] for i in range(3)]
        if any(size[i] > room[i] for i in range(3)):
            problems.append(f'Rika {size} does not fit {room}')
        anchor = [marks['existing_Rika_anchor'][0] + sx, marks['existing_Rika_anchor'][1] - FLOOR_Y, marks['existing_Rika_anchor'][2] + sz]
        props.append({'model': 'rika', 'kind': 'rika', 'at': [round(v, 3) for v in anchor], 'yaw': 0, 'scale': cfg['rika']})
        markers['rika'] = [round(v, 3) for v in anchor]
        print(f'  Rika at x{cfg["rika"]}: {[round(s, 2) for s in size]} in the clearance {room}')

    # Plan and model, as the trial rooms write them.
    pal = sorted(set(blocks.values()))
    runs = []
    for (x, y, z), bid in sorted(blocks.items(), key=lambda t: (t[0][1], t[0][2], t[0][0])):
        i = pal.index(bid)
        if runs and runs[-1][1] == y and runs[-1][2] == z and runs[-1][3] == x and runs[-1][4] == i:
            runs[-1][3] = x + 1
        else:
            runs.append([x, y, z, x + 1, i])
    bounds = [x0, -1, z0, x1 + 1, top + 1, z1 + 1]
    plan = {'id': cfg['id'], 'title': cfg['title'], 'line': cfg['line'], 'biome': f'jjk:realm_{cfg["id"]}', 'shift': [sx, sz],
            'bounds': bounds, 'markers': markers, 'spots': spots, 'palette': pal, 'runs': runs, 'lights': out_lights, 'props': props}
    os.makedirs(DATA, exist_ok=True)
    with open(os.path.join(DATA, f'{cfg["id"]}.json'), 'w') as f:
        json.dump(plan, f, separators=(',', ':'))
    elements, outliner, n = [], [], 0
    for gname in sorted(model):
        cs = model[gname]
        pivot = [0, 0, 0]
        if '#spin' in gname or '#bob' in gname:
            lo = [min(a[i] for _, a, _ in cs) for i in range(3)]
            hi = [max(b[i] for _, _, b in cs) for i in range(3)]
            pivot = [(lo[i] + hi[i]) / 2 for i in range(3)]
        children = []
        for e, a, b in cs:
            uid = f'r{n}'
            n += 1
            o = [e['origin'][0] / 16 + sx, e['origin'][1] / 16 - FLOOR_Y, e['origin'][2] / 16 + sz]
            face = e['faces']['north']['uv']
            elements.append({'uuid': uid, 'from': [round(v * 16, 3) for v in a], 'to': [round(v * 16, 3) for v in b],
                             'origin': [round(v * 16, 3) for v in o], 'rotation': e['rotation'],
                             'faces': {f: {'uv': face} for f in ('north', 'south', 'east', 'west', 'up', 'down')}})
            children.append(uid)
        outliner.append({'name': gname, 'origin': [round(v * 16, 3) for v in pivot], 'rotation': [0, 0, 0], 'children': children})
    with open(os.path.join(ASSETS, 'models', 'bb', f'realm_{cfg["id"]}.bbmodel'), 'w') as f:
        json.dump({'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': f'realm_{cfg["id"]}',
                   'resolution': {'width': 128, 'height': 128}, 'elements': elements, 'outliner': outliner}, f, separators=(',', ':'))
    # Its biome: the realm's own light colour over the crimson.
    amb, tint, fog = cfg['tint']
    biome = {"attributes": {"minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {c: [] for c in
             ["ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature", "water_ambient", "water_creature"]}},
             "modifier": "overlay"}, "minecraft:visual/ambient_light_color": amb, "minecraft:visual/block_light_tint": tint,
             "minecraft:visual/fog_color": fog, "minecraft:visual/sky_color": fog},
             "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"}, "features": [], "has_precipitation": False, "temperature": 0.5}
    with open(os.path.join(BIOMES, f'realm_{cfg["id"]}.json'), 'w') as f:
        json.dump(biome, f, indent=2)
    print(f'{name} -> {cfg["id"]}: shift {sx},{sz}, {len(blocks)} blocks, {len(elements)} model cubes, {len(spots)} curse spots, '
          f'{len(out_lights)} lights, bounds {bounds}' + (f'  PROBLEMS: {problems}' if problems else ''))
    return problems


if __name__ == '__main__':
    manifest = {m['file'][:-len('.bbmodel')]: m for m in json.load(open(os.path.join(SRC, 'manifest.json')))['models']}
    Image.open(os.path.join(SRC, 'realm_atlas.png')).convert('RGBA').save(os.path.join(ASSETS, 'textures', 'entity', 'realm_atlas.png'))
    bad = 0
    for name, cfg in DESTINATIONS.items():
        bad += len(install(name, cfg, manifest[name]))
    sys.exit(1 if bad else 0)
