// Bedrock / GeckoLib geometry (.geo.json) import + export.
// Bedrock space is Blockbench space mirrored on X: pivot.x and cube x are negated and
// rotation X/Y flip sign (exactly what Blockbench's bedrock codec does).
import { makeCube, computeBoxFaces, FACE_NAMES, modelBounds, sanitizeBoneNames } from '../core/model.js';

const neg = (x) => (x ? -x : 0); // avoid -0 in output

export function parseGeo(json) {
  const warnings = [];
  if (typeof json === 'string') json = JSON.parse(json);
  let geo, tw = 64, th = 64, name = 'imported';
  if (Array.isArray(json['minecraft:geometry'])) {
    geo = json['minecraft:geometry'][0];
    tw = geo.description?.texture_width || 64; th = geo.description?.texture_height || 64;
    name = (geo.description?.identifier || 'geometry.imported').replace(/^geometry\./, '');
    if (json['minecraft:geometry'].length > 1) warnings.push('File has several geometries; only the first was loaded.');
  } else {
    const key = Object.keys(json).find((k) => k.startsWith('geometry.'));
    if (!key) throw new Error('Not a Bedrock/GeckoLib geometry file (no "minecraft:geometry").');
    geo = json[key]; tw = geo.texturewidth || 64; th = geo.textureheight || 64; name = key.replace(/^geometry\./, '').split(':')[0];
  }
  const bones = [];
  for (const b of geo.bones || []) {
    const pivot = b.pivot || [0, 0, 0];
    const rot = b.rotation || [0, 0, 0];
    const bone = { name: b.name, parent: b.parent || null, pivot: [neg(pivot[0]), pivot[1], pivot[2]], rotation: [neg(rot[0]), neg(rot[1]), rot[2]], cubes: [] };
    for (const c of b.cubes || []) {
      const o = c.origin || [0, 0, 0], s = c.size || [0, 0, 0];
      const inflate = c.inflate || 0;
      const from = [neg(o[0] + s[0]), o[1], o[2]], to = [neg(o[0]), o[1] + s[1], o[2] + s[2]];
      const mirror = c.mirror !== undefined ? !!c.mirror : !!b.mirror;
      let boxUv = null, faces;
      if (Array.isArray(c.uv) || c.uv === undefined) {
        boxUv = c.uv || [0, 0];
        faces = computeBoxFaces(from, to, boxUv, mirror);
      } else {
        faces = {};
        for (const f of FACE_NAMES) {
          const fd = c.uv[f];
          if (!fd) continue;
          const [u, v] = fd.uv || [0, 0], [uw, vh] = fd.uv_size || [0, 0];
          faces[f] = { uv: [u, v, u + uw, v + vh], rotation: fd.uv_rotation || 0 };
        }
      }
      const cp = c.pivot, cr = c.rotation;
      bone.cubes.push(makeCube({
        name: 'cube', from, to, inflate, mirror, boxUv, faces,
        origin: cp ? [neg(cp[0]), cp[1], cp[2]] : undefined, rotation: cr ? [neg(cr[0]), neg(cr[1]), cr[2]] : [0, 0, 0],
      }));
    }
    bones.push(bone);
  }
  const model = { name, uvSize: [tw, th], texture: null, bones, source: 'geo' };
  const renames = sanitizeBoneNames(model);
  if (Object.keys(renames).length) warnings.push('Some bone names were made unique.');
  return { model, warnings };
}

const r = (n) => Math.round(n * 10000) / 10000 || 0;

export function exportGeo(model, identifier) {
  const { min, max } = modelBounds(model);
  const id = identifier || `geometry.${model.name}`;
  const bones = model.bones.map((b) => {
    const out = { name: b.name };
    if (b.parent) out.parent = b.parent;
    out.pivot = [r(-b.pivot[0]), r(b.pivot[1]), r(b.pivot[2])];
    if (b.rotation.some((x) => x)) out.rotation = [r(-b.rotation[0]), r(-b.rotation[1]), r(b.rotation[2])];
    if (b.cubes.length) out.cubes = b.cubes.map((c) => {
      const size = [r(c.to[0] - c.from[0]), r(c.to[1] - c.from[1]), r(c.to[2] - c.from[2])];
      const cube = { origin: [r(-c.to[0]), r(c.from[1]), r(c.from[2])], size };
      if (c.rotation.some((x) => x)) {
        cube.pivot = [r(-c.origin[0]), r(c.origin[1]), r(c.origin[2])];
        cube.rotation = [r(-c.rotation[0]), r(-c.rotation[1]), r(c.rotation[2])];
      }
      if (c.inflate) cube.inflate = c.inflate;
      if (c.boxUv) { cube.uv = c.boxUv.slice(); if (c.mirror) cube.mirror = true; }
      else {
        cube.uv = {};
        for (const f of FACE_NAMES) {
          const fd = c.faces[f];
          if (!fd) continue;
          cube.uv[f] = { uv: [r(fd.uv[0]), r(fd.uv[1])], uv_size: [r(fd.uv[2] - fd.uv[0]), r(fd.uv[3] - fd.uv[1])] };
          if (fd.rotation) cube.uv[f].uv_rotation = fd.rotation;
        }
      }
      return cube;
    });
    return out;
  });
  const w = Math.max(Math.abs(min[0]), Math.abs(max[0]), Math.abs(min[2]), Math.abs(max[2])) * 2 / 16;
  return {
    format_version: '1.12.0',
    'minecraft:geometry': [{
      description: {
        identifier: id, texture_width: model.uvSize[0], texture_height: model.uvSize[1],
        visible_bounds_width: Math.ceil(w + 1), visible_bounds_height: Math.ceil(max[1] / 16 + 1), visible_bounds_offset: [0, r(max[1] / 32), 0],
      },
      bones,
    }],
  };
}
