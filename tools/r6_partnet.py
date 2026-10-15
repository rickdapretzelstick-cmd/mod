# A small segmentation network that says, for every pixel of the tracker's 112-px window, which body part of the
# template-clothed R6 character it shows (or none) -- trained only on r6_synth.py's synthetic pictures. Part identity
# is what colours cannot give (arms and torso share the shirt's colours; the Dummy's skin passes for orange), and the
# synthetic pictures carry strike trails, smoke, blur and Dummies, so the network learns to look through them.
#   python3 tools/r6_partnet.py train OUT.pt DATA.npz [DATA2.npz ...]     (EPOCHS, BATCH env)
#   python3 tools/r6_partnet.py show OUT.pt VIDEO PATH.json OUT.png       predictions on real frames of a path
import sys, os, json, time
import numpy as np, torch, torch.nn as nn, torch.nn.functional as Fn, cv2

NPART = 7


def block(a, b):
    return nn.Sequential(nn.Conv2d(a, b, 3, padding=1), nn.BatchNorm2d(b), nn.ReLU(inplace=True),
                         nn.Conv2d(b, b, 3, padding=1), nn.BatchNorm2d(b), nn.ReLU(inplace=True))


class PartNet(nn.Module):
    def __init__(self, w=(24, 48, 96, 160)):
        super().__init__()
        self.e = nn.ModuleList([block(3, w[0]), block(w[0], w[1]), block(w[1], w[2]), block(w[2], w[3])])
        self.u = nn.ModuleList([nn.ConvTranspose2d(w[i + 1], w[i], 2, stride=2) for i in range(3)])
        self.d = nn.ModuleList([block(2 * w[i], w[i]) for i in range(3)])
        self.out = nn.Conv2d(w[0], NPART, 1)

    def forward(self, x):
        skips = []
        for i, e in enumerate(self.e):
            x = e(x)
            if i < 3:
                skips.append(x)
                x = Fn.max_pool2d(x, 2)
        for i in (2, 1, 0):
            x = self.u[i](x)
            x = self.d[i](torch.cat([x, skips[i]], 1))
        return self.out(x)


def to_tensor(X):
    return torch.from_numpy(X).permute(0, 3, 1, 2).float() / 127.5 - 1


def train(out, paths):
    torch.set_num_threads(int(os.environ.get('THREADS', '4')))
    X = np.concatenate([np.load(p)['X'] for p in paths])
    Y = np.concatenate([np.load(p)['Y'] for p in paths])
    n = len(X)
    val = np.arange(n) >= n - 500
    Xt, Yt, Xv, Yv = X[~val], Y[~val], X[val], Y[val]
    net = PartNet()
    opt = torch.optim.AdamW(net.parameters(), 2e-3, weight_decay=1e-4)
    epochs, bs = int(os.environ.get('EPOCHS', '8')), int(os.environ.get('BATCH', '32'))
    steps = epochs * (len(Xt) // bs)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, 2e-3, total_steps=steps)
    wts = torch.tensor([0.3] + [1.0] * (NPART - 1))
    t0, step = time.time(), 0
    for ep in range(epochs):
        net.train()
        order = np.random.permutation(len(Xt))
        tot = 0.0
        for b in range(len(Xt) // bs):
            idx = order[b * bs:(b + 1) * bs]
            x, y = to_tensor(Xt[idx]), torch.from_numpy(Yt[idx]).long()
            loss = Fn.cross_entropy(net(x), y, weight=wts)
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
            tot += float(loss)
            step += 1
            if step % 100 == 0:
                print(f'epoch {ep} step {step}/{steps} loss {tot / (b + 1):.3f} ({(time.time() - t0) / 60:.1f} min)', flush=True)
        net.eval()
        with torch.no_grad():
            pr = torch.cat([net(to_tensor(Xv[i:i + 50])).argmax(1) for i in range(0, len(Xv), 50)]).numpy()
        ious = []
        for c in range(1, NPART):
            inter = ((pr == c) & (Yv == c)).sum()
            uni = ((pr == c) | (Yv == c)).sum()
            ious.append(inter / max(uni, 1))
        print(f'epoch {ep}: validation part IoU {np.round(ious, 2).tolist()} mean {np.mean(ious):.3f}', flush=True)
        torch.save(net.state_dict(), out)


def load(path):
    net = PartNet()
    net.load_state_dict(torch.load(path, map_location='cpu'))
    net.eval()
    return net


def window_rgb(rgb, roi):
    """The footage as the tracker's window samples it (roi = (x0, y0, px per footage px, w, h))."""
    x0, y0, k, w, h = roi
    M = np.float32([[k, 0, -x0 * k], [0, k, -y0 * k]])
    return cv2.warpAffine(np.ascontiguousarray(rgb), M, (int(w), int(h)), flags=cv2.INTER_AREA, borderMode=cv2.BORDER_REPLICATE)


def predict(net, imgs):
    with torch.no_grad():
        lo = net(to_tensor(np.stack(imgs)))
        return lo.softmax(1).numpy()


def show(model, video, path_json, out):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import r6_oversee as O, r6_global as G, r6_fit_torch as T, r6_fit as F
    from r6_synth import LAB_COL
    net = load(model)
    tr = json.load(open(path_json))
    clip = O.Clip(video, tr)
    rows = []
    for k in (530, 544, 546, 550, 555, 562, 567, 592):
        p = np.array(tr['frames'][str(k)]['params'], float)
        rgb = clip.rgb(k)
        win = G.UnionWindow(clip.f0, [p], clip.light, 112)
        roi = win.roi(p, F.classify(rgb, O.pal))
        img = window_rgb(rgb, roi)
        pr = predict(net, [img])[0].argmax(0)
        rows.append(np.hstack([img, LAB_COL[pr]]))
    grid = np.vstack([np.hstack(rows[i:i + 4]) for i in range(0, len(rows), 4)])
    cv2.imwrite(out, cv2.resize(grid, None, fx=2, fy=2, interpolation=cv2.INTER_NEAREST)[..., ::-1])


if __name__ == '__main__':
    a = sys.argv[1:]
    if a and a[0] == 'train':
        train(a[1], a[2:])
    elif a and a[0] == 'show':
        show(*a[1:5])
    else:
        sys.exit('train OUT.pt DATA.npz... | show OUT.pt VIDEO PATH.json OUT.png')
