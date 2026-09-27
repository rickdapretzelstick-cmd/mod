"""Generates particle textures and the training dummy skin. Run: python3 tools/gen_textures.py"""
import numpy as np, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'jjk', 'textures')
os.makedirs(os.path.join(ROOT, 'particle'), exist_ok=True)
os.makedirs(os.path.join(ROOT, 'entity'), exist_ok=True)

def save_rgba(name, alpha, rgb=(255, 255, 255)):
    a = np.clip(alpha, 0, 1)
    img = np.zeros(a.shape + (4,), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = rgb
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(os.path.join(ROOT, 'particle', name + '.png'))

N = 32
y, x = np.mgrid[0:N, 0:N]
cx = cy = (N - 1) / 2
r = np.hypot(x - cx, y - cy) / (N / 2)
save_rgba('glow', np.exp(-r ** 2 * 4.5) * (r < 1))
save_rgba('core', np.clip(1.2 - r * 1.4, 0, 1) ** 1.5)
save_rgba('ring', np.exp(-((r - 0.78) / 0.09) ** 2) * (r < 1))
ang = np.arctan2(y - cy, x - cx)
save_rgba('star', np.clip((np.abs(np.cos(2 * ang)) ** 18) * np.exp(-r * 2.6) + np.exp(-r ** 2 * 30), 0, 1))
save_rgba('spark', np.exp(-((x - cx) / 13) ** 2 - ((y - cy) / 1.6) ** 2))
rng = np.random.default_rng(3)
noise = rng.random((8, 8))
big = np.array(Image.fromarray((noise * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC)) / 255.0
save_rgba('smoke', np.exp(-r ** 2 * 3) * (0.55 + 0.45 * big))
save_rgba('shard', ((np.abs(x - cx) / 3 + np.abs(y - cy) / 12) < 1).astype(float) * 0.95)

# Training dummy: burlap body, painted target on the chest, rope bands. Classic 64x64 humanoid layout.
S = 64
img = np.zeros((S, S, 4), np.uint8)
base = np.array([176, 138, 92])
tex = (rng.random((S, S)) * 26 - 13)[..., None]
img[..., :3] = np.clip(base + tex, 0, 255)
img[..., 3] = 255
def rect(x0, y0, w, h, color):
    img[y0:y0 + h, x0:x0 + w, :3] = color
# head front: simple stitched face
rect(8, 8, 8, 8, [196, 160, 110]); rect(10, 11, 1, 1, [60, 40, 30]); rect(13, 11, 1, 1, [60, 40, 30]); rect(10, 13, 4, 1, [110, 70, 50])
# chest target (body front 20..28 x 20..32)
for yy in range(20, 32):
    for xx in range(20, 28):
        d = np.hypot(xx - 23.5, yy - 25.5)
        if d < 1.2: img[yy, xx, :3] = [200, 30, 30]
        elif d < 2.4: img[yy, xx, :3] = [235, 235, 225]
        elif d < 3.6: img[yy, xx, :3] = [200, 30, 30]
# rope bands on arms and legs
for x0, y0, w in [(44, 23, 4), (36, 55, 4), (4, 23, 4), (20, 55, 4)]:
    rect(x0, y0, w, 2, [120, 92, 55])
img[:8, :8, 3] = 0; img[:8, 24:40, 3] = 0; img[:16, 56:, 3] = 0  # unused UV regions transparent
img[0:16, 32:64, 3] = 0  # hat layer transparent
Image.fromarray(img, 'RGBA').save(os.path.join(ROOT, 'entity', 'training_dummy.png'))
print('textures written')
