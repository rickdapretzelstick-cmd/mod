// Internal model format (Blockbench coordinate space, pixels, Y up, model faces north = -Z,
// character's right = +X). Mirrors what a .bbmodel stores so import/export stay lossless.
//
// model = {
//   name, uvSize: [w, h],
//   texture: { dataUrl, width, height, name } | null,
//   bones: [{ name, parent: string|null, pivot: [x,y,z], rotation: [x,y,z], hidden?: bool, cubes: Cube[] }]
// }
// Cube = { name, from, to, inflate, origin, rotation, mirror, boxUv: [u,v] | null,
//          faces: { north|east|south|west|up|down: { uv: [u1,v1,u2,v2], rotation } } }

export const FACE_NAMES = ['north', 'east', 'south', 'west', 'up', 'down'];

// Blockbench / Minecraft box-UV layout.
export function computeBoxFaces(from, to, uv, mirror = false) {
  const w = Math.abs(to[0] - from[0]), h = Math.abs(to[1] - from[1]), d = Math.abs(to[2] - from[2]);
  // Minecraft floors fractional box sizes when laying out UVs
  const W = Math.floor(w + 1e-4) || (w > 0 ? 1 : 0), H = Math.floor(h + 1e-4) || (h > 0 ? 1 : 0), D = Math.floor(d + 1e-4) || (d > 0 ? 1 : 0);
  const [u, v] = uv;
  const faces = {
    east: [u, v + D, u + D, v + D + H],
    north: [u + D, v + D, u + D + W, v + D + H],
    west: [u + D + W, v + D, u + 2 * D + W, v + D + H],
    south: [u + 2 * D + W, v + D, u + 2 * D + 2 * W, v + D + H],
    up: [u + D + W, v + D, u + D, v],
    down: [u + D + 2 * W, v, u + D + W, v + D],
  };
  if (mirror) {
    const e = faces.east, wst = faces.west;
    faces.east = [wst[2], wst[1], wst[0], wst[3]];
    faces.west = [e[2], e[1], e[0], e[3]];
    for (const f of ['north', 'south', 'up', 'down']) { const q = faces[f]; faces[f] = [q[2], q[1], q[0], q[3]]; }
  }
  const out = {};
  for (const f of FACE_NAMES) out[f] = { uv: faces[f], rotation: 0 };
  return out;
}

export function makeCube({ name = 'cube', from, to, inflate = 0, origin, rotation = [0, 0, 0], mirror = false, boxUv = null, faces = null }) {
  const c = {
    name, from: from.slice(), to: to.slice(), inflate, mirror,
    origin: (origin || [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2]).slice(),
    rotation: rotation.slice(), boxUv: boxUv ? boxUv.slice() : null, faces,
  };
  if (!c.faces) c.faces = computeBoxFaces(c.from, c.to, c.boxUv || [0, 0], c.mirror);
  return c;
}

export function boneMap(model) {
  const m = new Map();
  for (const b of model.bones) m.set(b.name, b);
  return m;
}

export function childrenOf(model, name) {
  return model.bones.filter((b) => b.parent === name);
}

// Bones ordered so parents come before children; also returns depth for UI indentation.
export function boneTree(model) {
  const out = [];
  const visit = (parent, depth) => {
    for (const b of model.bones.filter((x) => (x.parent || null) === parent)) {
      out.push({ bone: b, depth });
      visit(b.name, depth + 1);
    }
  };
  visit(null, 0);
  // orphans (parent missing) fall back to root
  for (const b of model.bones) if (!out.find((o) => o.bone === b)) out.push({ bone: b, depth: 0 });
  return out;
}

export function modelBounds(model) {
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  for (const b of model.bones) for (const c of b.cubes) for (let i = 0; i < 3; i++) {
    min[i] = Math.min(min[i], c.from[i], c.to[i]);
    max[i] = Math.max(max[i], c.from[i], c.to[i]);
  }
  if (!isFinite(min[0])) return { min: [-8, 0, -8], max: [8, 32, 8] };
  return { min, max };
}

export function validateModel(model) {
  const errors = [];
  const names = new Set();
  for (const b of model.bones) {
    if (!b.name) errors.push('A bone has no name.');
    if (names.has(b.name)) errors.push(`Duplicate bone name "${b.name}" (GeckoLib needs unique bone names).`);
    names.add(b.name);
    if (b.parent && !model.bones.find((x) => x.name === b.parent)) errors.push(`Bone "${b.name}" has missing parent "${b.parent}".`);
  }
  return errors;
}

// Make bone names unique and valid identifiers for GeckoLib (keeps originals where possible).
export function sanitizeBoneNames(model) {
  const used = new Set();
  const renames = {};
  for (const b of model.bones) {
    let n = String(b.name || 'bone').trim().replace(/\s+/g, '_') || 'bone';
    let base = n, i = 2;
    while (used.has(n)) n = `${base}${i++}`;
    used.add(n);
    if (n !== b.name) renames[b.name] = n;
    b.name = n;
  }
  for (const b of model.bones) if (b.parent && renames[b.parent]) b.parent = renames[b.parent];
  return renames;
}
