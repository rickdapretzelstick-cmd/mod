// "Blocky": a built-in Minecraft-style humanoid with elbows, knees and a held sword, so beginners
// can animate immediately. Its bone names follow common GeckoLib conventions (snake_case).
import { makeCube } from './model.js';

const bone = (name, parent, pivot, cubes = [], extra = {}) => ({ name, parent, pivot, rotation: [0, 0, 0], cubes, ...extra });

export function createSampleModel() {
  const B = [
    bone('root', null, [0, 0, 0]),
    bone('body', 'root', [0, 12, 0], [makeCube({ name: 'torso', from: [-4, 12, -2], to: [4, 24, 2], boxUv: [16, 16] })]),
    bone('head', 'body', [0, 24, 0], [makeCube({ name: 'head', from: [-4, 24, -4], to: [4, 32, 4], boxUv: [0, 0] })]),
    bone('right_arm', 'body', [5, 22, 0], [makeCube({ name: 'upper_arm', from: [4, 18, -2], to: [8, 24, 2], boxUv: [40, 16] })]),
    bone('right_forearm', 'right_arm', [6, 18, 0], [makeCube({ name: 'forearm', from: [4, 12, -2], to: [8, 18, 2], boxUv: [40, 26] })]),
    bone('right_item', 'right_forearm', [6, 12.5, 0], [
      makeCube({ name: 'blade', from: [5.5, 12, -13], to: [6.5, 13, -1], boxUv: [0, 36] }),
      makeCube({ name: 'guard', from: [4.5, 12, -1], to: [7.5, 13, 0], boxUv: [0, 50] }),
      makeCube({ name: 'grip', from: [5.5, 12, 0], to: [6.5, 13, 3], boxUv: [8, 50] }),
    ], { hidden: true }), // shown automatically for weapon moves
    bone('left_arm', 'body', [-5, 22, 0], [makeCube({ name: 'upper_arm', from: [-8, 18, -2], to: [-4, 24, 2], boxUv: [32, 32] })]),
    bone('left_forearm', 'left_arm', [-6, 18, 0], [makeCube({ name: 'forearm', from: [-8, 12, -2], to: [-4, 18, 2], boxUv: [32, 42] })]),
    bone('right_leg', 'root', [2, 12, 0], [makeCube({ name: 'thigh', from: [0, 6, -2], to: [4, 12, 2], boxUv: [0, 16] })]),
    bone('right_shin', 'right_leg', [2, 6, 0], [makeCube({ name: 'shin', from: [0, 0, -2], to: [4, 6, 2], boxUv: [0, 26] })]),
    bone('left_leg', 'root', [-2, 12, 0], [makeCube({ name: 'thigh', from: [-4, 6, -2], to: [0, 12, 2], boxUv: [48, 36] })]),
    bone('left_shin', 'left_leg', [-2, 6, 0], [makeCube({ name: 'shin', from: [-4, 0, -2], to: [0, 6, 2], boxUv: [48, 46] })]),
  ];
  return { name: 'blocky', uvSize: [64, 64], texture: null, bones: B, source: 'sample' };
}

// Paint a simple texture for Blocky. `makeCanvas(w,h)` returns a 2D-capable canvas.
export function paintSampleTexture(model, makeCanvas) {
  const [W, H] = model.uvSize;
  const cv = makeCanvas(W, H);
  const g = cv.getContext('2d');
  g.clearRect(0, 0, W, H);
  const palette = {
    head: '#c98f6b', torso: '#3a7bd5', upper_arm: '#3a7bd5', forearm: '#c98f6b',
    thigh: '#2f3b63', shin: '#2f3b63', blade: '#d6e2ea', guard: '#c7a43a', grip: '#6b4428',
  };
  const shade = (hex, f) => {
    const n = parseInt(hex.slice(1), 16);
    const c = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((x) => Math.max(0, Math.min(255, Math.round(x * f))));
    return `rgb(${c[0]},${c[1]},${c[2]})`;
  };
  const faceShade = { up: 1.15, north: 1, south: 0.85, east: 0.92, west: 0.92, down: 0.7 };
  const rect = (uv, color) => {
    const x = Math.min(uv[0], uv[2]), y = Math.min(uv[1], uv[3]);
    g.fillStyle = color; g.fillRect(x, y, Math.abs(uv[2] - uv[0]), Math.abs(uv[3] - uv[1]));
  };
  for (const b of model.bones) for (const c of b.cubes) {
    const base = palette[c.name] || '#999999';
    for (const [f, face] of Object.entries(c.faces)) rect(face.uv, shade(base, faceShade[f]));
    // details
    if (c.name === 'head') {
      const n = c.faces.north.uv; const x = Math.min(n[0], n[2]), y = n[1];
      g.fillStyle = '#4a2f1d'; g.fillRect(x, y, 8, 2); // hair fringe
      for (const f of ['east', 'west', 'south']) { const q = c.faces[f].uv; g.fillRect(Math.min(q[0], q[2]), q[1], Math.abs(q[2] - q[0]), f === 'south' ? 6 : 3); }
      const u = c.faces.up.uv; rect(u, '#4a2f1d');
      g.fillStyle = '#ffffff'; g.fillRect(x + 1, y + 4, 2, 1); g.fillRect(x + 5, y + 4, 2, 1);
      g.fillStyle = '#2b4fa8'; g.fillRect(x + 2, y + 4, 1, 1); g.fillRect(x + 5, y + 4, 1, 1);
      g.fillStyle = '#8a5a44'; g.fillRect(x + 3, y + 6, 2, 1);
    }
    if (c.name === 'torso') {
      for (const f of ['north', 'south', 'east', 'west']) { const q = c.faces[f].uv; g.fillStyle = '#3b2a1a'; g.fillRect(Math.min(q[0], q[2]), Math.max(q[1], q[3]) - 2, Math.abs(q[2] - q[0]), 1); }
    }
    if (c.name === 'forearm') {
      for (const f of ['north', 'south', 'east', 'west']) { const q = c.faces[f].uv; g.fillStyle = '#3a7bd5'; g.fillRect(Math.min(q[0], q[2]), q[1], Math.abs(q[2] - q[0]), 1); }
    }
    if (c.name === 'shin') {
      for (const f of ['north', 'south', 'east', 'west']) { const q = c.faces[f].uv; g.fillStyle = '#5a5a5a'; g.fillRect(Math.min(q[0], q[2]), Math.max(q[1], q[3]) - 2, Math.abs(q[2] - q[0]), 2); }
      rect(c.faces.down.uv, '#3d3d3d');
    }
  }
  return { dataUrl: cv.toDataURL('image/png'), width: W, height: H, name: 'blocky.png' };
}
