// Blockbench .bbmodel import / export.
// Supports cube elements (box UV and per-face UV), nested groups (old inline outliner and the
// newer separate "groups" array), the first texture, and embedded animations (incl. the GeckoLib
// plugin's per-keyframe easing). Mesh elements, locators and Molang expressions are reported, not faked.
import { makeCube, computeBoxFaces, FACE_NAMES, sanitizeBoneNames } from '../core/model.js';
import { createAnimation, setKey, uid } from '../core/animation.js';

const num = (v, warn, what) => {
  if (typeof v === 'number') return v;
  const s = String(v ?? '0').trim();
  if (s === '') return 0;
  const n = Number(s);
  if (Number.isFinite(n)) return n;
  warn(`Molang expression "${s}" in ${what} was replaced with 0 (expressions are not editable here yet).`);
  return 0;
};

export function parseBBModel(json) {
  const warnings = [];
  const warn = (m) => { if (!warnings.includes(m)) warnings.push(m); };
  if (typeof json === 'string') json = JSON.parse(json);
  if (!json || !json.meta || !Array.isArray(json.elements)) throw new Error('Not a Blockbench .bbmodel file (missing meta/elements).');

  const fmt = json.meta.model_format || 'free';
  if (!['bedrock', 'bedrock_old', 'geckolib', 'animated_entity_model', 'free', 'modded_entity', 'java_block'].includes(fmt)) warn(`Model format "${fmt}" is untested; importing cubes and groups anyway.`);
  const metaBox = json.meta.box_uv !== undefined ? !!json.meta.box_uv : true;
  const res = json.resolution || { width: 64, height: 64 };

  // Textures
  let texture = null;
  let uvSize = [res.width || 64, res.height || 64];
  if (Array.isArray(json.textures) && json.textures.length) {
    const t = json.textures[0];
    if (t.source && String(t.source).startsWith('data:image')) {
      texture = { dataUrl: t.source, width: t.width || uvSize[0], height: t.height || uvSize[1], name: t.name || 'texture.png' };
    } else warn('The texture is not embedded in the .bbmodel; load the PNG separately.');
    if (t.uv_width && t.uv_height) uvSize = [t.uv_width, t.uv_height];
    if (json.textures.length > 1) warn(`Model has ${json.textures.length} textures; only the first is shown.`);
  }

  const elements = new Map();
  for (const e of json.elements) elements.set(e.uuid, e);
  const groupsById = new Map();
  if (Array.isArray(json.groups)) for (const g of json.groups) groupsById.set(g.uuid, g);

  const bones = [];
  const groupUuidToName = new Map();

  const toCube = (e) => {
    const type = e.type || 'cube';
    if (type !== 'cube') { warn(`Skipped ${type} element "${e.name}" (only cubes are supported).`); return null; }
    if (e.visibility === false) { /* keep it, Blockbench hides but GeckoLib still renders */ }
    const box = e.box_uv !== undefined ? !!e.box_uv : metaBox;
    let faces;
    if (box) faces = computeBoxFaces(e.from, e.to, e.uv_offset || [0, 0], !!e.mirror_uv);
    else {
      faces = {};
      for (const f of FACE_NAMES) {
        const src = e.faces?.[f];
        if (!src || src.texture === null || src.texture === undefined && !src.uv) continue;
        faces[f] = { uv: (src.uv || [0, 0, 0, 0]).slice(), rotation: src.rotation || 0 };
      }
    }
    return makeCube({
      name: e.name, from: e.from, to: e.to, inflate: e.inflate || 0, origin: e.origin || e.from,
      rotation: e.rotation || [0, 0, 0], mirror: !!e.mirror_uv, boxUv: box ? (e.uv_offset || [0, 0]) : null, faces,
    });
  };

  const visitGroup = (node, parentName) => {
    const g = groupsById.get(node.uuid) || node;
    const bone = {
      name: g.name || 'bone', parent: parentName, pivot: (g.origin || [0, 0, 0]).slice(),
      rotation: (g.rotation || [0, 0, 0]).slice(), cubes: [], _uuid: g.uuid,
    };
    bones.push(bone);
    for (const ch of node.children || []) {
      if (typeof ch === 'string') {
        const e = elements.get(ch);
        if (e) { const c = toCube(e); if (c) bone.cubes.push(c); }
      } else if (ch && typeof ch === 'object') visitGroup(ch, bone.name);
    }
  };

  let looseRoot = null;
  for (const node of json.outliner || []) {
    if (typeof node === 'string') {
      const e = elements.get(node);
      if (!e) continue;
      if (!looseRoot) { looseRoot = { name: 'root_cubes', parent: null, pivot: [0, 0, 0], rotation: [0, 0, 0], cubes: [] }; bones.push(looseRoot); warn('Cubes outside any group were put in a bone called "root_cubes".'); }
      const c = toCube(e); if (c) looseRoot.cubes.push(c);
    } else visitGroup(node, null);
  }

  const model = { name: json.name || json.model_identifier || 'imported', uvSize, texture, bones, source: 'bbmodel' };
  const renames = sanitizeBoneNames(model);
  if (Object.keys(renames).length) warn(`Renamed bones so names are unique: ${Object.entries(renames).map(([a, b]) => `${a}→${b}`).join(', ')}`);
  for (const b of bones) { if (b._uuid) groupUuidToName.set(b._uuid, b.name); delete b._uuid; }

  // Animations
  const animations = [];
  for (const a of json.animations || []) {
    const anim = createAnimation(a.name || 'animation', Number(a.length) || 1, a.loop === 'loop' ? 'loop' : a.loop === 'hold' ? 'hold' : 'once');
    for (const [key, animator] of Object.entries(a.animators || {})) {
      const boneName = groupUuidToName.get(key) || animator.name;
      if (!boneName || !bones.find((b) => b.name === boneName)) { if (animator.type !== 'effect') warn(`Animation "${anim.name}" references unknown bone "${animator.name}"; skipped.`); continue; }
      for (const kf of animator.keyframes || []) {
        if (!['rotation', 'position', 'scale'].includes(kf.channel)) continue;
        if (kf.data_points?.length > 1) warn('Keyframes with separate pre/post values were simplified to one value.');
        const dp = (kf.data_points && kf.data_points[0]) || {};
        const v = ['x', 'y', 'z'].map((ax) => num(dp[ax], warn, `${anim.name}/${boneName}/${kf.channel}`));
        let easing = 'linear';
        if (kf.easing) easing = kf.easing;
        else if (kf.interpolation === 'catmullrom') easing = 'catmullrom';
        else if (kf.interpolation === 'step') easing = 'step';
        else if (kf.interpolation === 'bezier') { easing = 'easeInOutSine'; warn('Bezier keyframes were approximated with "Smooth" easing.'); }
        setKey(anim, boneName, kf.channel, Number(kf.time) || 0, v, easing);
        if (kf.easingArgs?.length) {
          const tr = anim.tracks[boneName][kf.channel];
          const k = tr.find((x) => Math.abs(x.t - (Number(kf.time) || 0)) < 0.0005);
          if (k) k.args = kf.easingArgs.map(Number);
        }
      }
    }
    animations.push(anim);
  }
  return { model, animations, warnings };
}

const uuid = () => (globalThis.crypto?.randomUUID ? crypto.randomUUID() : uid('u'));

// Export model (+ animations) back to a .bbmodel that opens in Blockbench (GeckoLib / bedrock format).
export function exportBBModel(model, animations = [], { format = 'animated_entity_model' } = {}) {
  const elements = [];
  const groupUuid = new Map();
  for (const b of model.bones) groupUuid.set(b.name, uuid());
  const groupNode = (b) => {
    const children = [];
    for (const c of b.cubes) {
      const id = uuid();
      const el = {
        name: c.name || 'cube', box_uv: !!c.boxUv, rescale: false, locked: false, render_order: 'default', allow_mirror_modeling: true,
        from: c.from, to: c.to, autouv: 0, color: 0, origin: c.origin, rotation: c.rotation, inflate: c.inflate || 0,
        type: 'cube', uuid: id,
      };
      if (c.mirror) el.mirror_uv = true;
      if (c.boxUv) el.uv_offset = c.boxUv;
      el.faces = {};
      for (const f of FACE_NAMES) {
        const face = c.faces[f];
        el.faces[f] = face ? { uv: face.uv, texture: model.texture ? 0 : null, ...(face.rotation ? { rotation: face.rotation } : {}) } : { uv: [0, 0, 0, 0], texture: null };
      }
      elements.push(el);
      children.push(id);
    }
    for (const ch of model.bones.filter((x) => x.parent === b.name)) children.push(groupNode(ch));
    return { name: b.name, origin: b.pivot, rotation: b.rotation, color: 0, uuid: groupUuid.get(b.name), export: true, isOpen: true, locked: false, visibility: true, autouv: 0, children };
  };
  const outliner = model.bones.filter((b) => !b.parent).map(groupNode);

  const anims = animations.map((a) => {
    const animators = {};
    for (const [bone, tr] of Object.entries(a.tracks)) {
      const id = groupUuid.get(bone);
      if (!id) continue;
      const keyframes = [];
      for (const ch of ['rotation', 'position', 'scale']) for (const k of tr[ch] || []) {
        keyframes.push({
          channel: ch, data_points: [{ x: k.v[0], y: k.v[1], z: k.v[2] }], uuid: uuid(), time: k.t, color: -1,
          interpolation: k.easing === 'catmullrom' ? 'catmullrom' : k.easing === 'step' ? 'step' : 'linear',
          ...(k.easing && !['linear', 'catmullrom', 'step'].includes(k.easing) ? { easing: k.easing } : {}),
          ...(k.args ? { easingArgs: k.args } : {}),
        });
      }
      animators[id] = { name: bone, type: 'bone', keyframes };
    }
    return {
      uuid: uuid(), name: a.name, loop: a.loop === 'loop' ? 'loop' : a.loop === 'hold' ? 'hold' : 'once', override: false,
      length: a.length, snapping: 20, selected: false, anim_time_update: '', blend_weight: '', start_delay: '', loop_delay: '', animators,
    };
  });

  return {
    meta: { format_version: '4.10', model_format: format, box_uv: model.bones.every((b) => b.cubes.every((c) => !!c.boxUv)) },
    name: model.name, model_identifier: model.name, visible_box: [1, 1, 0], variable_placeholders: '', variable_placeholder_buttons: [],
    timeline_setups: [], unhandled_root_fields: {},
    resolution: { width: model.uvSize[0], height: model.uvSize[1] },
    elements, outliner,
    textures: model.texture ? [{
      path: '', name: model.texture.name || `${model.name}.png`, folder: 'block', namespace: '', id: '0', width: model.texture.width, height: model.texture.height,
      uv_width: model.uvSize[0], uv_height: model.uvSize[1], particle: false, render_mode: 'default', render_sides: 'auto', frame_time: 1,
      frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true, saved: false, uuid: uuid(), source: model.texture.dataUrl,
    }] : [],
    animations: anims,
  };
}
