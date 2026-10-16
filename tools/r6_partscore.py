# Poses judged against the part network (r6_partnet.py): the rig drawn as body parts in the tracker's 112-px window,
# compared with the network's part labels of the same window -- how many pixels either calls body get the same part
# from both. Unlike the colour rule it tells the arms from the torso and right from left, and the network was trained
# to see through trails, smoke and the Dummy. The whole-clip pick (r6_global's Viterbi) then runs on colour + W x parts.
#   python3 tools/r6_partscore.py select VIDEO CANDS.json NET.pt OUTDIR [W]
#   python3 tools/r6_partscore.py sheet VIDEO PATH.json NET.pt OUT.png       footage | network parts | the path's rig
import sys, os, json
import numpy as np, torch, cv2
torch.set_num_threads(1)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r6_fit as F, r6_fit_torch as T
import r6_oversee as O, r6_global as G, r6_partnet as P
from r6_synth import PART_ID, LAB_COL

NC = O.NC
BOX_PART = np.array([0] + [PART_ID[part] for _, _, _, part in F.PARTS])  # hard_draw's box id + 1 -> part id


def frame_parts(clip, net, k, ps):
    """(network part labels (112, 112), drawn part labels (B, 112, 112)) for poses ps of frame k, one common window."""
    rgb = clip.rgb(k)
    enc = F.classify(rgb, O.pal)
    v = T.View([rgb], {0: enc}, [0], 30, 112)
    v.lite = True
    win = G.UnionWindow(clip.f0, ps, clip.light, 112)
    fit = T.Fit([v], ps[0][None, NC:], [ps[0][None, :5]], [float(ps[0][5])], [clip.light])
    v.window(0, ps[0], win)
    roi = win.roi(ps[0], enc)
    pred = P.predict(net, [P.window_rgb(rgb, roi)])[0].argmax(0)
    Pm = np.stack(ps)
    pid = (T.PART_OF_FACE + 1).to(torch.int64)
    drawn = T.hard_draw(v, 0, torch.tensor(Pm[:, NC:], dtype=T.DT), torch.tensor(Pm[:, :5], dtype=T.DT), fit.persp[0],
                        fit.lights[0], pid)
    return pred, BOX_PART[drawn]


def part_scores(pred, drawn):
    body = (pred[None] > 0) | (drawn > 0)
    same = (pred[None] == drawn) & (drawn > 0)
    return same.sum((1, 2)) / np.maximum(body.sum((1, 2)), 1)


def select(video, cands_json, net_path, outdir, w=1.0):
    data = json.load(open(cands_json))
    tr = json.load(open(data['track']))
    clip = O.Clip(video, tr)
    net = P.load(net_path)
    for k, cands in data['frames'].items():
        ps = [np.array(c['params'], float) for c in cands]
        pred, drawn = frame_parts(clip, net, int(k), ps)
        sc = part_scores(pred, drawn)
        for c, s in zip(cands, sc):
            c.setdefault('colour', c['agreement'])
            c['parts'] = float(s)
            c['agreement'] = c['colour'] + w * float(s)
    os.makedirs(outdir, exist_ok=True)
    json.dump(data, open(os.path.join(outdir, f'{data["name"]}_cands.json'), 'w'))
    G.select(outdir, data['name'])
    full = json.load(open(os.path.join(outdir, f'{data["name"]}_global.json')))
    path = full['frames']
    pick = {k: next(c for c in data['frames'][k] if c['params'] == path[k]['params']) for k in path}
    moving = [k for k in pick if data['frames'][k][0]['from'] != 'idle']
    best_parts = {k: max(data['frames'][k], key=lambda c: c['parts']) for k in moving}
    rep = {'part_weight': w, 'colour_mean': round(float(np.mean([pick[k]['colour'] for k in moving])), 4),
           'colour_ge_0.90': int(sum(pick[k]['colour'] >= 0.9 for k in moving)),
           'parts_mean': round(float(np.mean([pick[k]['parts'] for k in moving])), 4),
           'parts_ge_0.80': int(sum(pick[k]['parts'] >= 0.8 for k in moving))}
    for k in path:
        path[k]['agreement'], path[k]['parts_agreement'] = pick[k]['colour'], pick[k]['parts']
    full['parts'] = rep
    json.dump(full, open(os.path.join(outdir, f'{data["name"]}_global.json'), 'w'))
    print('PARTS', json.dumps(rep), flush=True)
    return rep


def sheet(video, path_json, net_path, out, frames=(530, 544, 546, 550, 555, 562, 567, 592)):
    tr = json.load(open(path_json))
    clip = O.Clip(video, tr)
    net = P.load(net_path)
    rows = []
    for k in frames:
        p = np.array(tr['frames'][str(k)]['params'], float)
        rgb = clip.rgb(k)
        win = G.UnionWindow(clip.f0, [p], clip.light, 112)
        roi = win.roi(p, F.classify(rgb, O.pal))
        pred, drawn = frame_parts(clip, net, k, [p])
        s = part_scores(pred, drawn)[0]
        img = P.window_rgb(rgb, roi)
        r = np.hstack([img, LAB_COL[pred], LAB_COL[drawn[0]]])
        O.text(r, f'{k} parts {s:.2f} colour {tr["frames"][str(k)]["agreement"]:.2f}', (3, 12), 0.35)
        rows.append(r)
    grid = np.vstack([np.hstack(rows[i:i + 2]) for i in range(0, len(rows), 2)])
    cv2.imwrite(out, cv2.resize(grid, None, fx=2, fy=2, interpolation=cv2.INTER_NEAREST)[..., ::-1])


if __name__ == '__main__':
    a = sys.argv[1:]
    if a and a[0] == 'select':
        select(a[1], a[2], a[3], a[4], float(a[5]) if len(a) > 5 else 1.0)
    elif a and a[0] == 'sheet':
        sheet(*a[1:5])
    else:
        sys.exit('select VIDEO CANDS.json NET.pt OUTDIR [W] | sheet VIDEO PATH.json NET.pt OUT.png')
