#!/usr/bin/env python3
"""
Installs the last six cursed realm environments (the "Cursed Realms Blockbench Pack") as realm layouts, through the
same system as tools/install_realm_rooms.py (a plan of real blocks read by progression.investigation.RealmRooms, and a
prop model drawn by TrialPropEntity):

    python3 tools/install_realm_rooms_b.py [tools/blockbench/cursed_realms_b]

The pack: six Generic-format .bbmodel projects (16 units to a block, origin at the floor centre, Y=0 the walking surface,
north -Z) on one 128x64 atlas, plus geometry/<id>.json, the same cuboids already in block units with a material and a
group each. The geometry is read for placement (block units as given: never divided by 16 again); each cuboid is paired
with its .bbmodel element (same order, checked) for its UVs.

  - Real blocks: every cuboid of a building material (stone, wood, brick...) that the grid holds within half a block of
    each face and that is at least 0.6 thick: floors, foundations, cavern walls, ceilings, ring walls, cliff faces. These
    are the physical arena (collision, mob navigation, protection), in the matching vanilla block.
  - The prop model (models/bb/realmb_<id>.bbmodel, on textures/entity/realm_atlas_b.png): everything else, exactly as
    the pack draws it (rails, chains, webs, lanterns, supports, furniture, fences, thin walls); soul lantern cores glow.
  - Invisible collision (barrier blocks) under the substantial parts of that model (supports, thin walls, partitions,
    ceilings, pillars, fences, hay, furniture), so what looks solid is solid; chains, webs, rails and brush stay walk-through.
  - Light: a light block at every soul lantern, plus a sparse dim fill so the interiors stay dark but readable.

No shell is added: the mine and the house are enclosed by their own walls and ceilings, the others are open islands in
the shared red void. Markers: the arrival (the cliff's high above its landing, for the fall), the encounter centre, and
curse spots on real ground with headroom (the Finger Bearer's at its seal).
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'blockbench', 'cursed_realms_b')
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'jjk')
DATA = os.path.join(RES, 'data', 'jjk', 'realm_rooms')
BIOMES = os.path.join(RES, 'data', 'jjk', 'worldgen', 'biome')
BARRIER = 'minecraft:barrier'

# Per realm: building materials as blocks, the model groups that get collision, where you arrive (and what you face),
# the encounter centre, and how many curse spots.
REALMS = {
    'mine_realm': dict(
        title='The Mine', line='The tunnel goes on further than the mine did. The rails end in rock.',
        blocks={'tuff': 'minecraft:tuff', 'deepslate': 'minecraft:deepslate', 'stone': 'minecraft:stone'},
        collide={'mine_supports', 'cavern_walls', 'ceiling'}, arrival=(0.5, -10.5), face=(0.5, 0.5), centre=(0.5, 0.5), fill=12, enclosed=True, seal_walls=('cavern_walls', 'ceiling')),
    'house_realm': dict(
        title='The Empty House', line='You never fall asleep. The hall goes on further than the house did.',
        blocks={'oak': 'minecraft:dark_oak_planks', 'spruce': 'minecraft:spruce_planks', 'stripped': 'minecraft:stripped_spruce_wood'},
        collide={'exterior_walls', 'end_walls', 'partitions', 'ceiling', 'overturned_furniture', 'barrels', 'cold_hearth', 'red_bed'},
        arrival=(0.5, -19.5), face=(0.5, 21.0), centre=(0.5, 0.5), fill=12, enclosed=True),
    'pasture_realm': dict(
        title='The Dead Pasture', line='The field is dark all the way out. Something is in the grass.',
        blocks={'grass': 'minecraft:grass_block', 'dirt': 'minecraft:coarse_dirt', 'podzol': 'minecraft:podzol', 'deepslate': 'minecraft:deepslate'},
        collide={'fences', 'posts', 'dead_tree', 'blackened_hay', 'bones'}, floor='minecraft:podzol',
        shell=('minecraft:dark_oak_planks', 'minecraft:dark_oak_log', 'minecraft:dark_oak_planks', 'minecraft:soul_lantern[hanging=true]'), arrival=(0.5, 15.5), face=(0.5, 0.5), centre=(0.5, 0.5), fill=14),
    'cliff_realm': dict(
        title='The Fallen Cliff', line='You never hit the ground. The air is thick, and wrong.',
        blocks={'tuff': 'minecraft:tuff', 'deepslate': 'minecraft:deepslate', 'stone': 'minecraft:stone', 'blackstone': 'minecraft:blackstone'},
        collide={'north_cliff', 'lantern_posts'}, floor='minecraft:blackstone',
        shell=('minecraft:deepslate_bricks', 'minecraft:polished_deepslate', 'minecraft:deepslate_tiles', 'minecraft:soul_lantern[hanging=true]'), arrival=(0.5, 4.5), face=(0.5, -6.0), centre=(0.5, 0.5), fall=14, fill=14),
    'finger_bearer_realm': dict(
        title='The Sealed Hall', line='The seal opens under you. Something has been waiting a long time.',
        blocks={'brick': 'minecraft:polished_blackstone_bricks', 'polished': 'minecraft:polished_blackstone', 'deepslate': 'minecraft:deepslate',
                'cracked': 'minecraft:cracked_polished_blackstone_bricks', 'blackstone': 'minecraft:blackstone', 'basalt': 'minecraft:polished_basalt'},
        collide={'basalt_pillars', 'wall_coping'}, floor='minecraft:polished_blackstone',
        shell=('minecraft:polished_blackstone_bricks', 'minecraft:polished_basalt', 'minecraft:blackstone', 'minecraft:soul_lantern[hanging=true]'), arrival=(0.5, 10.5), face=(0.5, 0.5), centre=(0.5, 0.5), seal=(0.5, 0.5), fill=14),
    'hollow_realm': dict(
        title='The Hollow', line='The place is gone. Only the curse remembers it.',
        blocks={'tuff': 'minecraft:tuff', 'deepslate': 'minecraft:deepslate', 'stone': 'minecraft:stone', 'polished': 'minecraft:polished_deepslate'},
        collide={'broken_pillars', 'fallen_fragments'}, floor='minecraft:tuff',
        shell=('minecraft:deepslate_bricks', 'minecraft:polished_deepslate', 'minecraft:deepslate_tiles', 'minecraft:soul_lantern[hanging=true]'), arrival=(0.5, 8.5), face=(0.5, 0.5), centre=(0.5, 0.5), fill=14),
}
GLOW = {'soul'}
# The shared red void's own colours, a little lifted so a fight reads, and the lanterns' cold light.
BIOME_TINT = ('#3a1626', '#cfe4ff', '#1a0812', '#0c0408')


def axis(lo, hi):
    """Grid cells a span fills (each covered at least half), and how far the result is from the span at worst."""
    a = [k for k in range(math.floor(lo) - 1, math.ceil(hi) + 1) if min(k + 1, hi) - max(k, lo) >= 0.5]
    if not a:
        return None
    return a[0], a[-1] + 1, max(abs(a[0] - lo), abs(a[-1] + 1 - hi))


def collision_cells(a, b):
    """The cells under a model part that should block: each axis's half-covered cells, or (thinner than a cell) the one
    holding its middle."""
    spans = []
    for i in range(3):
        ax = axis(a[i], b[i])
        if ax is None:
            if b[i] - a[i] < 0.2 and i != 1:
                return []
            m = math.floor((a[i] + b[i]) / 2)
            ax = (m, m + 1, 0)
        spans.append(ax)
    return [(x, y, z) for x in range(spans[0][0], spans[0][1]) for y in range(spans[1][0], spans[1][1]) for z in range(spans[2][0], spans[2][1])]


def pieces(a, b):
    """A cuboid cut at the whole-block lines on each axis it spans more than a block of."""
    cuts = []
    for i in range(3):
        if b[i] - a[i] <= 1.05:
            cuts.append([(a[i], b[i])])
            continue
        edges = [a[i]] + [k for k in range(math.floor(a[i]) + 1, math.ceil(b[i])) if a[i] + 0.05 < k < b[i] - 0.05] + [b[i]]
        cuts.append(list(zip(edges, edges[1:])))
    return [((x[0], y[0], z[0]), (x[1], y[1], z[1])) for x in cuts[0] for y in cuts[1] for z in cuts[2]]


def install(rid, cfg):
    geo = json.load(open(os.path.join(SRC, 'geometry', f'{rid}.json')))
    assert geo['units'] == 'blocks', geo['units']
    bb = json.load(open(os.path.join(SRC, 'models', f'{rid}.bbmodel')))
    cubes = geo['cuboids']
    els = bb['elements']
    assert len(els) == len(cubes), (rid, len(els), len(cubes))
    problems = []
    for c, e in zip(cubes, els):
        if any(abs(e['from'][i] / 16 - c['a'][i]) > 1e-3 or abs(e['to'][i] / 16 - c['b'][i]) > 1e-3 for i in range(3)):
            problems.append(f'element {e["name"]} does not match its cuboid')
            break
        if any(c['b'][i] - c['a'][i] <= 0 for i in range(3)):
            problems.append(f'empty cuboid {c}')
    blocks = {}
    model = {}
    lights = []
    for c, e in zip(cubes, els):
        a, b, m, g = c['a'], c['b'], c['material'], c['group']
        bid = cfg['blocks'].get(m)
        if bid and min(b[i] - a[i] for i in range(3)) >= 0.6:
            ax = [axis(a[i], b[i]) for i in range(3)]
            if all(ax) and max(t[2] for t in ax) <= 0.5:
                for x in range(ax[0][0], ax[0][1]):
                    for y in range(ax[1][0], ax[1][1]):
                        for z in range(ax[2][0], ax[2][1]):
                            blocks[(x, y, z)] = bid
                continue
        group = g + ('#glow' if m in GLOW else '')
        model.setdefault(group, []).append((e, a, b, g))
        if m == 'soul':
            lights.append(tuple(math.floor((a[i] + b[i]) / 2) for i in range(3)))
    # Sealed rooms: where a wall stops short of the ceiling (the pack's ragged cavern tops), the wall is carried up to it
    # in its own stone, so the room is closed (a deliberate fix: the pack leaves a few gaps to the void at the seams).
    if cfg.get('seal_walls'):
        wall_g, ceil_g = cfg['seal_walls']
        tops, ceils, mats = {}, {}, {}
        for c in cubes:
            a, b = c['a'], c['b']
            for x in range(math.floor(a[0]), math.ceil(b[0])):
                for z in range(math.floor(a[2]), math.ceil(b[2])):
                    if not (a[0] <= x + 0.5 <= b[0] and a[2] <= z + 0.5 <= b[2]):
                        continue
                    if c['group'] == wall_g:
                        tops[(x, z)] = max(tops.get((x, z), -9), b[1])
                        mats[(x, z)] = cfg['blocks'].get(c['material'], 'minecraft:deepslate')
                    elif c['group'] == ceil_g:
                        ceils[(x, z)] = min(ceils.get((x, z), 99), a[1])
        sealed = 0
        for (x, z), t in tops.items():
            cy = ceils.get((x, z))
            if cy is None:
                continue
            for y in range(math.floor(t), math.floor(cy) + 1):
                if (x, y, z) not in blocks:
                    blocks[(x, y, z)] = mats[(x, z)]
                    sealed += 1
        print(f'  {rid}: carried {sealed} wall cells up to the ceiling')
    # Sealed rooms: the floor reaches every wall (no hole at the seams to drop through into the void).
    if cfg.get('enclosed'):
        floor = max(set(v for (x, y, z), v in blocks.items() if y == -1), key=lambda v: sum(1 for (x, y, z), w in blocks.items() if y == -1 and w == v))
        bx = [k[0] for k in blocks]
        bz = [k[2] for k in blocks]
        patched = 0
        for x in range(min(bx), max(bx) + 1):
            for z in range(min(bz), max(bz) + 1):
                if (x, -1, z) not in blocks:
                    blocks[(x, -1, z)] = floor
                    patched += 1
        print(f'  {rid}: floor carried to the walls ({patched} cells)')
    # Invisible collision under what looks solid (never where a block already is, never on the walking line of a doorway:
    # cells are only claimed where the part covers them).
    barriers = 0
    for parts in model.values():
        for e, a, b, g in parts:
            if g not in cfg['collide']:
                continue
            for cell in collision_cells(a, b):
                if cell not in blocks:
                    blocks[cell] = BARRIER
                    barriers += 1

    # Closed in, as every realm room is: walls just outside everything, the floor carried out to them, a ceiling with
    # fixtures (high enough over the cliff for its fall in). The mine and the house close themselves.
    if cfg.get('shell'):
        wall, pilaster, ceiling, fixture = cfg['shell']
        pts = list(blocks) + [p for parts in model.values() for (_, a, b, _) in parts for p in (a, b)]
        sx0 = math.floor(min(q[0] for q in pts)) - 1
        sx1 = math.ceil(max(q[0] for q in pts))
        sz0 = math.floor(min(q[2] for q in pts)) - 1
        sz1 = math.ceil(max(q[2] for q in pts))
        stop = max(math.ceil(max(q[1] for q in pts)) + 2, cfg.get('fall', 0) + 4)
        floor = cfg['floor']
        for x in range(sx0, sx1 + 1):
            for z in range(sz0, sz1 + 1):
                if (x, -1, z) not in blocks:
                    blocks[(x, -1, z)] = floor
                blocks[(x, stop, z)] = ceiling
                if x in (sx0, sx1) or z in (sz0, sz1):
                    corner = x in (sx0, sx1) and z in (sz0, sz1)
                    pil = corner or (x - sx0) % 4 == 0 and z in (sz0, sz1) or (z - sz0) % 4 == 0 and x in (sx0, sx1)
                    for y in range(-1, stop):
                        blocks[(x, y, z)] = pilaster if pil or y <= 0 else wall
        for x in range(sx0 + 3, sx1 - 1, 5):
            for z in range(sz0 + 3, sz1 - 1, 5):
                if fixture.endswith('[hanging=true]'):
                    blocks.setdefault((x, stop - 1, z), fixture)
                else:
                    blocks[(x, stop, z)] = fixture
        cfg = dict(cfg, enclosed=True)

    def solid(x, y, z):
        s_ = blocks.get((x, y, z))
        return s_ is not None and 'lantern' not in s_

    def ground(x, z):
        """The lowest standing height on column (x, z): on something solid with two free cells over it (from below, so
        a ceiling or the top of a wall never counts)."""
        for y in range(-6, 10):
            if solid(x, y - 1, z) and not solid(x, y, z) and not solid(x, y + 1, z):
                return y
        return None

    def clear(x, z, g, h=3, r=0):
        return all(not solid(x + dx, y, z + dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) for y in range(g, g + h))

    def stand(px, pz, h=3, r=0, search=6):
        """The nearest column to (px, pz) with real ground and headroom."""
        best = None
        for d in range(0, search + 1):
            for dx in range(-d, d + 1):
                for dz in range(-d, d + 1):
                    if max(abs(dx), abs(dz)) != d:
                        continue
                    x, z = math.floor(px) + dx, math.floor(pz) + dz
                    g = ground(x, z)
                    if g is None or not clear(x, z, g, h, r):
                        continue
                    dist = (x + 0.5 - px) ** 2 + (z + 0.5 - pz) ** 2
                    if best is None or dist < best[0]:
                        best = (dist, [x + 0.5, g, z + 0.5])
            if best:
                return best[1]
        return None

    markers = {}
    arr = stand(*cfg['arrival'])
    if arr is None:
        problems.append('no clear arrival')
        arr = [cfg['arrival'][0], 0, cfg['arrival'][1]]
    if cfg.get('fall'):
        # Arrival from above: high over a landing with nothing in the way down.
        x, z = math.floor(arr[0]), math.floor(arr[2])
        if any(solid(x, y, z) for y in range(arr[1], arr[1] + cfg['fall'] + 2)):
            problems.append('the fall is blocked')
        markers['landing'] = list(arr)
        arr = [arr[0], arr[1] + cfg['fall'], arr[2]]
    markers['arrival'] = arr
    markers['facing'] = [cfg['face'][0], 0, cfg['face'][1]]
    # On foot: from the arrival (the landing, for the cliff) every curse spot and what you face can be walked to (the
    # house's doorways really let you through, the mine's posts leave routes).
    def walkable_from(x, z, g):
        seen, todo = {(x, z)}, [(x, z, g)]
        while todo:
            x, z, g = todo.pop()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if (nx, nz) in seen:
                    continue
                ng = ground(nx, nz)
                if ng is None or abs(ng - g) > 1 or not clear(nx, nz, ng, 2):
                    continue
                seen.add((nx, nz))
                todo.append((nx, nz, ng))
        return seen
    start = markers.get('landing', arr)
    walk = walkable_from(math.floor(start[0]), math.floor(start[2]), start[1])
    cen = stand(*cfg['centre'], r=0) or [cfg['centre'][0], 0, cfg['centre'][1]]
    markers['encounter_center'] = cen
    spots = []
    if cfg.get('seal'):
        seal = stand(*cfg['seal'], h=5, r=2)
        if seal is None:
            problems.append('the seal has no room for the Finger Bearer')
        else:
            spots.append(seal)
            markers['seal'] = seal
    else:
        for rad in (5, 7, 4, 9, 11, 3):
            for i in range(12):
                ang = 2 * math.pi * (i + (rad % 3) * 0.33) / 12
                px, pz = cen[0] + rad * math.cos(ang), cen[2] + rad * math.sin(ang)
                x, z = math.floor(px), math.floor(pz)
                g = ground(x, z)
                if g is None or (x, z) not in walk or not clear(x, z, g, 3, 1):
                    continue
                if any((s[0] - x - 0.5) ** 2 + (s[2] - z - 0.5) ** 2 < 9 for s in spots):
                    continue
                landing = markers.get('landing', arr)
                if (x + 0.5 - landing[0]) ** 2 + (z + 0.5 - landing[2]) ** 2 < 36:
                    continue
                spots.append([x + 0.5, g, z + 0.5])
                if len(spots) >= 8:
                    break
            if len(spots) >= 8:
                break
        if len(spots) < 3:
            problems.append(f'only {len(spots)} curse spots')
    # Lights: every lantern core, then (enclosed rooms) a sparse dim fill so a fight reads in the dark.
    out_lights = []
    taken = set()
    for p in lights:
        # In the lantern's own cell, or (its post's collision there) the nearest free cell round it.
        for d in ((0, 0, 0), (0, 1, 0), (0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 2, 0)):
            q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if q not in blocks and q not in taken:
                out_lights.append(list(q) + [14])
                taken.add(q)
                break
        else:
            problems.append(f'no room for the light of the lantern at {p}')
    xs = [k[0] for k in blocks]
    zs = [k[2] for k in blocks]
    ys = [k[1] for k in blocks]
    x0, x1, z0, z1 = min(xs), max(xs) + 1, min(zs), max(zs) + 1
    if cfg['fill']:
        for x in range(x0 + 2, x1 - 1, 5):
            for z in range(z0 + 2, z1 - 1, 5):
                g = ground(x, z)
                if g is None:
                    continue
                # Two layers, as the earlier rooms are lit: at head height, and high under the ceiling.
                for dy, lv in ((2, cfg['fill']), (5, cfg['fill'] - 1)):
                    p = (x, g + dy, z)
                    if p not in blocks and p not in taken and all(not solid(x, y, z) for y in range(g, g + dy + 1)):
                        out_lights.append(list(p) + [lv])
                        taken.add(p)
    # The mine and the house are enclosed: nothing walks (or flies) out of them into the void.
    if cfg.get('enclosed'):
        start = (math.floor(arr[0]), arr[1], math.floor(arr[2]))
        seen, todo = {start}, [start]
        lo_y, hi_y = min(k[1] for k in blocks) - 1, max(k[1] for k in blocks) + 1
        leak = None
        while todo and leak is None:
            x, y, z = todo.pop()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                q = (x + d[0], y + d[1], z + d[2])
                if q in seen or q in blocks:
                    continue
                if not (min(xs) <= q[0] <= max(xs) and lo_y < q[1] < hi_y and min(zs) <= q[2] <= max(zs)):
                    leak = q
                    break
                seen.add(q)
                todo.append(q)
        if leak:
            problems.append(f'not enclosed: open to the void at {leak}')
    for sp in spots + [cen]:
        if (math.floor(sp[0]), math.floor(sp[2])) not in walk:
            problems.append(f'{sp} cannot be walked to from the arrival')
    goal = stand(*cfg['face'])
    if goal and (math.floor(goal[0]), math.floor(goal[2])) not in walk:
        problems.append(f'the far end {goal} cannot be walked to')
    print(f'  {rid}: {len(walk)} walkable columns from the arrival')
    # The arena's box must fit the realm's cleared slot (CursedRealms.RADIUS 26, HEIGHT 40).
    if max(-x0, x1, -z0, z1) > 25:
        problems.append(f'too wide for a realm slot: {x0}..{x1}, {z0}..{z1}')

    props = [{'model': f'realmb_{rid}', 'kind': 'static'}]
    pal = sorted(set(blocks.values()))
    runs = []
    for (x, y, z), bid in sorted(blocks.items(), key=lambda t: (t[0][1], t[0][2], t[0][0])):
        i = pal.index(bid)
        if runs and runs[-1][1] == y and runs[-1][2] == z and runs[-1][3] == x and runs[-1][4] == i:
            runs[-1][3] = x + 1
        else:
            runs.append([x, y, z, x + 1, i])
    top = max(max(ys) + 1, math.ceil(max(b[1] for parts in model.values() for _, _, b, _ in parts)) if model else 0)
    bounds = [x0, min(ys), z0, x1, top + 1, z1]
    plan = {'id': rid, 'title': cfg['title'], 'line': cfg['line'], 'biome': f'jjk:realm_{rid}', 'shift': [0, 0], 'bounds': bounds,
            'markers': markers, 'spots': spots, 'palette': pal, 'runs': runs, 'lights': out_lights, 'props': props}
    if cfg.get('fall'):
        plan['from_above'] = True
    os.makedirs(DATA, exist_ok=True)
    with open(os.path.join(DATA, f'{rid}.json'), 'w') as f:
        json.dump(plan, f, separators=(',', ':'))
    # The prop model: the pack's own elements and UVs, regrouped (glow, collision groups kept by name).
    elements, outliner = [], []
    n = 0
    for gname in sorted(model):
        children = []
        for e, a, b, g in model[gname]:
            # The pack maps one 16 px tile onto each face however big the cuboid is: split it at the block grid so the
            # tile repeats per block (no stretched walls), each piece's face showing the share of the tile it covers.
            for pa, pb in pieces(a, b):
                uid = f'r{n}'
                n += 1
                faces = {}
                for f in ('north', 'south', 'east', 'west', 'up', 'down'):
                    if f not in e['faces']:
                        continue
                    u0, v0, u1, v1 = e['faces'][f]['uv'][:4]
                    w = {'north': 0, 'south': 0, 'east': 2, 'west': 2, 'up': 0, 'down': 0}[f]
                    hgt = {'north': 1, 'south': 1, 'east': 1, 'west': 1, 'up': 2, 'down': 2}[f]
                    su = min(1.0, pb[w] - pa[w]) * (u1 - u0)
                    sv = min(1.0, pb[hgt] - pa[hgt]) * (v1 - v0)
                    faces[f] = {'uv': [u0, v0, round(u0 + su, 3), round(v0 + sv, 3)]}
                elements.append({'uuid': uid, 'from': [round(v * 16, 3) for v in pa], 'to': [round(v * 16, 3) for v in pb],
                                 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'faces': faces})
                children.append(uid)
        outliner.append({'name': gname, 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'children': children})
    res = bb.get('resolution', {'width': 128, 'height': 64})
    with open(os.path.join(ASSETS, 'models', 'bb', f'realmb_{rid}.bbmodel'), 'w') as f:
        json.dump({'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': f'realmb_{rid}',
                   'resolution': res, 'elements': elements, 'outliner': outliner}, f, separators=(',', ':'))
    amb, tint, fog, sky = BIOME_TINT
    biome = {"attributes": {"minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {c: [] for c in
             ["ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature", "water_ambient", "water_creature"]}},
             "modifier": "overlay"}, "minecraft:visual/ambient_light_color": amb, "minecraft:visual/block_light_tint": tint,
             "minecraft:visual/fog_color": fog, "minecraft:visual/sky_color": sky},
             "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"}, "features": [], "has_precipitation": False, "temperature": 0.5}
    with open(os.path.join(BIOMES, f'realm_{rid}.json'), 'w') as f:
        json.dump(biome, f, indent=2)
    print(f'{rid}: {len(blocks) - barriers} blocks + {barriers} collision, {len(elements)} model cubes, {len(spots)} curse spots, '
          f'{len(out_lights)} lights, bounds {bounds}, arrival {arr}' + (f'  PROBLEMS: {problems}' if problems else ''))
    return problems


if __name__ == '__main__':
    Image.open(os.path.join(SRC, 'realm_atlas.png')).convert('RGBA').save(os.path.join(ASSETS, 'textures', 'entity', 'realm_atlas_b.png'))
    bad = 0
    for rid, cfg in REALMS.items():
        bad += len(install(rid, cfg))
    sys.exit(1 if bad else 0)
