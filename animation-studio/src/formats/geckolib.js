// GeckoLib animation JSON (Bedrock animation format + GeckoLib easing extensions).
// Export, import and a validator that mirrors the rules of GeckoLib's own parser
// (software.bernie.geckolib.loading.json.typeadapter.*), so a file that passes here is
// structurally loadable by GeckoLib 4.x / 5.x.
import { createAnimation, setKey, CHANNELS, round } from '../core/animation.js';
import { EASING_NAMES } from '../core/easing.js';

export const GECKOLIB_EASINGS = EASING_NAMES; // same keys as GeckoLib's EasingType registry

// "animation.<model>.<name>" – lowercase, [a-z0-9_.]
export function animationId(modelName, animName) {
  const clean = (s) => String(s).toLowerCase().trim().replace(/[^a-z0-9_.]+/g, '_').replace(/^_+|_+$/g, '') || 'unnamed';
  return `animation.${clean(modelName)}.${clean(animName)}`;
}

const t2s = (t) => {
  const s = String(round(t, 4));
  return s.includes('.') ? s : `${s}.0`;
};

export function exportAnimation(anim) {
  const bones = {};
  for (const [bone, tr] of Object.entries(anim.tracks)) {
    const b = {};
    for (const ch of CHANNELS) {
      const keys = tr[ch] || [];
      if (!keys.length) continue;
      const obj = {};
      for (const k of keys) {
        const vector = k.v.map((n) => round(n, 3));
        if (!k.easing || k.easing === 'linear') obj[t2s(k.t)] = { vector };
        // GeckoLib reads "easing": "catmullrom"; Bedrock reads lerp_mode. Writing both keeps either happy.
        else if (k.easing === 'catmullrom') obj[t2s(k.t)] = { vector, easing: 'catmullrom', lerp_mode: 'catmullrom' };
        else {
          obj[t2s(k.t)] = { vector, easing: k.easing };
          if (k.args?.length) obj[t2s(k.t)].easingArgs = k.args;
        }
      }
      b[ch] = obj;
    }
    if (Object.keys(b).length) bones[bone] = b;
  }
  const out = {};
  if (anim.loop === 'loop') out.loop = true;
  else if (anim.loop === 'hold') out.loop = 'hold_on_last_frame';
  out.animation_length = round(anim.length, 4);
  out.bones = bones;
  return out;
}

export function exportAnimationFile(modelName, anims) {
  const animations = {};
  for (const a of anims) animations[animationId(modelName, a.name)] = exportAnimation(a);
  return { format_version: '1.8.0', animations, geckolib_format_version: 2 };
}

// ---------- Import ----------
function parseVec(v, warn, where) {
  const one = (x) => {
    if (typeof x === 'number') return x;
    const n = Number(String(x).trim());
    if (Number.isFinite(n)) return n;
    warn(`Molang "${x}" at ${where} was replaced with 0.`);
    return 0;
  };
  if (Array.isArray(v)) return [one(v[0] ?? 0), one(v[1] ?? v[0] ?? 0), one(v[2] ?? v[0] ?? 0)];
  const s = one(v); return [s, s, s];
}

export function parseAnimationFile(json, knownBones = null) {
  if (typeof json === 'string') json = JSON.parse(json);
  if (!json || typeof json.animations !== 'object') throw new Error('Not a GeckoLib/Bedrock animation file (missing "animations").');
  const warnings = [];
  const warn = (m) => { if (!warnings.includes(m)) warnings.push(m); };
  const anims = [];
  for (const [id, a] of Object.entries(json.animations)) {
    const name = id.split('.').slice(2).join('.') || id;
    const loop = a.loop === true || a.loop === 'loop' ? 'loop' : a.loop === 'hold_on_last_frame' ? 'hold' : 'once';
    const anim = createAnimation(name, Number(a.animation_length) || 0, loop);
    let maxT = 0;
    for (const [bone, chans] of Object.entries(a.bones || {})) {
      if (knownBones && !knownBones.includes(bone)) warn(`Bone "${bone}" is not in the current model; its keys were kept but won't show.`);
      for (const ch of CHANNELS) {
        const data = chans[ch];
        if (data === undefined) continue;
        const where = `${name}/${bone}/${ch}`;
        if (Array.isArray(data) || typeof data !== 'object') { setKey(anim, bone, ch, 0, parseVec(data, warn, where), 'linear'); continue; }
        if ('vector' in data) { setKey(anim, bone, ch, 0, parseVec(data.vector, warn, where), data.easing || 'linear'); continue; }
        for (const [ts, kv] of Object.entries(data)) {
          const t = Number(ts);
          if (!Number.isFinite(t)) { warn(`Bad keyframe time "${ts}" at ${where}.`); continue; }
          maxT = Math.max(maxT, t);
          let vec, easing = 'linear', args;
          if (Array.isArray(kv) || typeof kv !== 'object') vec = kv;
          else if ('vector' in kv) { vec = kv.vector; easing = kv.easing || (kv.lerp_mode === 'catmullrom' ? 'catmullrom' : 'linear'); args = kv.easingArgs; }
          else if ('post' in kv || 'pre' in kv) {
            const p = kv.pre ?? kv.post;
            vec = Array.isArray(p) ? p : p?.vector ?? p;
            if (kv.lerp_mode === 'catmullrom') easing = 'catmullrom';
            if ('pre' in kv && 'post' in kv) warn('Pre/post keyframes were simplified to a single value.');
          } else { warn(`Unrecognised keyframe at ${where} @ ${ts}.`); continue; }
          if (!GECKOLIB_EASINGS.includes(easing)) { warn(`Unknown easing "${easing}" → linear.`); easing = 'linear'; }
          setKey(anim, bone, ch, t, parseVec(vec, warn, where), easing);
          if (args?.length) { const k = anim.tracks[bone][ch].find((x) => Math.abs(x.t - t) < 0.0005); if (k) k.args = args.map(Number); }
        }
      }
    }
    if (!anim.length) anim.length = maxT || 1;
    anims.push(anim);
  }
  return { animations: anims, warnings };
}

// ---------- Validation ----------
// Returns { ok, errors: [], warnings: [] }. `geo` (optional) is an exported geo.json for bone cross-checks.
export function validateAnimationFile(file, geo = null) {
  const errors = [], warnings = [];
  const isNum = (x) => typeof x === 'number' && Number.isFinite(x);
  const isVal = (x) => isNum(x) || (typeof x === 'string' && x.trim() !== '');
  const checkVec = (v, where) => {
    if (isVal(v)) return;
    if (!Array.isArray(v) || v.length !== 3 || !v.every(isVal)) errors.push(`${where}: vector must be [x, y, z] of numbers.`);
  };
  if (!file || typeof file !== 'object') return { ok: false, errors: ['File is not a JSON object.'], warnings };
  if (file.format_version !== '1.8.0') warnings.push(`format_version is "${file.format_version}", expected "1.8.0".`);
  if (file.geckolib_format_version !== undefined && ![1, 2].includes(file.geckolib_format_version)) errors.push('geckolib_format_version must be 1 or 2.');
  if (!file.animations || typeof file.animations !== 'object' || Array.isArray(file.animations)) { errors.push('"animations" must be an object.'); return { ok: false, errors, warnings }; }
  const geoBones = geo ? new Set((geo['minecraft:geometry']?.[0]?.bones || []).map((b) => b.name)) : null;
  const names = Object.keys(file.animations);
  if (!names.length) warnings.push('No animations in file.');
  for (const id of names) {
    const a = file.animations[id];
    const W = (m) => `${id}: ${m}`;
    if (!/^[a-z0-9_.\-]+$/.test(id)) warnings.push(W('animation names should be lowercase a-z, 0-9, "_" and "." to be safe as resource keys.'));
    if (!id.startsWith('animation.')) warnings.push(W('convention is "animation.<model>.<name>".'));
    if (a.loop !== undefined && ![true, false, 'loop', 'hold_on_last_frame', 'play_once'].includes(a.loop)) errors.push(W(`invalid loop value ${JSON.stringify(a.loop)}.`));
    if (a.animation_length !== undefined && !(isNum(a.animation_length) && a.animation_length >= 0)) errors.push(W('animation_length must be a non-negative number.'));
    if (!a.bones || typeof a.bones !== 'object') { warnings.push(W('has no bones.')); continue; }
    let maxT = 0;
    for (const [bone, chans] of Object.entries(a.bones)) {
      if (geoBones && !geoBones.has(bone)) errors.push(W(`bone "${bone}" does not exist in the geometry – GeckoLib would ignore it.`));
      for (const ch of Object.keys(chans)) {
        if (!CHANNELS.includes(ch)) { warnings.push(W(`unknown channel "${ch}" on ${bone}.`)); continue; }
        const data = chans[ch];
        const where = `${id}/${bone}/${ch}`;
        if (Array.isArray(data) || isVal(data)) { checkVec(data, where); continue; }
        if (typeof data !== 'object' || data === null) { errors.push(`${where}: invalid channel data.`); continue; }
        if ('vector' in data) { checkVec(data.vector, where); continue; }
        for (const [ts, kv] of Object.entries(data)) {
          const t = Number(ts);
          if (!Number.isFinite(t) || t < 0) { errors.push(`${where}: keyframe time "${ts}" is not a valid number of seconds.`); continue; }
          maxT = Math.max(maxT, t);
          if (Array.isArray(kv) || isVal(kv)) { checkVec(kv, `${where}@${ts}`); continue; }
          if (kv && typeof kv === 'object') {
            if ('vector' in kv) checkVec(kv.vector, `${where}@${ts}`);
            else if ('pre' in kv || 'post' in kv) { if ('pre' in kv) checkVec(Array.isArray(kv.pre) ? kv.pre : kv.pre?.vector ?? kv.pre, `${where}@${ts}.pre`); if ('post' in kv) checkVec(Array.isArray(kv.post) ? kv.post : kv.post?.vector ?? kv.post, `${where}@${ts}.post`); }
            else errors.push(`${where}@${ts}: keyframe needs "vector" or "pre"/"post".`);
            if (kv.easing !== undefined && !GECKOLIB_EASINGS.includes(kv.easing)) errors.push(`${where}@${ts}: unknown easing "${kv.easing}".`);
            if (kv.easingArgs !== undefined && !(Array.isArray(kv.easingArgs) && kv.easingArgs.every(isNum))) errors.push(`${where}@${ts}: easingArgs must be an array of numbers.`);
            if (kv.lerp_mode !== undefined && !['linear', 'catmullrom', 'step'].includes(kv.lerp_mode)) errors.push(`${where}@${ts}: unknown lerp_mode "${kv.lerp_mode}".`);
          } else errors.push(`${where}@${ts}: invalid keyframe.`);
        }
      }
    }
    if (isNum(a.animation_length) && maxT > a.animation_length + 1e-3) warnings.push(W(`has keyframes at ${maxT}s after animation_length ${a.animation_length}s (they will be cut off).`));
  }
  return { ok: errors.length === 0, errors, warnings };
}

export function validateGeo(geo) {
  const errors = [], warnings = [];
  const g = geo?.['minecraft:geometry']?.[0];
  if (!g) return { ok: false, errors: ['Missing "minecraft:geometry" array.'], warnings };
  if (!g.description?.identifier?.startsWith('geometry.')) errors.push('description.identifier must start with "geometry.".');
  if (!(g.description?.texture_width > 0 && g.description?.texture_height > 0)) errors.push('texture_width/texture_height must be positive.');
  const names = new Set();
  for (const b of g.bones || []) {
    if (!b.name) errors.push('A bone has no name.');
    if (names.has(b.name)) errors.push(`Duplicate bone "${b.name}".`);
    names.add(b.name);
    if (!Array.isArray(b.pivot) || b.pivot.length !== 3) errors.push(`Bone "${b.name}" needs a pivot [x,y,z].`);
    for (const c of b.cubes || []) {
      if (!Array.isArray(c.origin) || !Array.isArray(c.size)) errors.push(`Bone "${b.name}" has a cube without origin/size.`);
    }
  }
  for (const b of g.bones || []) if (b.parent && !names.has(b.parent)) errors.push(`Bone "${b.name}" parent "${b.parent}" does not exist.`);
  return { ok: errors.length === 0, errors, warnings };
}
