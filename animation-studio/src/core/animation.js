// Animation data model + sampling. Pure functions, no DOM, no three.js.
//
// Values are stored in the Bedrock / GeckoLib convention (degrees for rotation, model pixels for
// position, multipliers for scale) so export is lossless. The viewer converts to its own space.
//
// animation = {
//   id, name, length (s), loop: 'loop' | 'once' | 'hold',
//   tracks: { [boneName]: { rotation: Key[], position: Key[], scale: Key[] } },
//   sections: [{ id, label, start, end, block, params }]   // optional, from presets/assistant
// }
// Key = { t, v: [x, y, z], easing: string, args?: number[] }

import { ease } from './easing.js';

export const CHANNELS = ['rotation', 'position', 'scale'];
export const TICK = 0.05; // one Minecraft tick
export const DEFAULTS = { rotation: [0, 0, 0], position: [0, 0, 0], scale: [1, 1, 1] };

let idCounter = 0;
export const uid = (p = 'id') => `${p}_${Date.now().toString(36)}_${(idCounter++).toString(36)}${Math.random().toString(36).slice(2, 6)}`;

export const round = (n, d = 3) => { const m = 10 ** d; return Math.round(n * m) / m; };
export const snapTime = (t, step = TICK) => round(Math.round(t / step) * step, 4);

export function createAnimation(name = 'new_animation', length = 1, loop = 'once') {
  return { id: uid('anim'), name, length, loop, tracks: {}, sections: [] };
}

export function cloneAnimation(a, newId = true) {
  const c = JSON.parse(JSON.stringify(a));
  if (newId) c.id = uid('anim');
  return c;
}

function track(anim, bone) {
  if (!anim.tracks[bone]) anim.tracks[bone] = { rotation: [], position: [], scale: [] };
  return anim.tracks[bone];
}

export function sortKeys(keys) { keys.sort((a, b) => a.t - b.t); return keys; }

export function findKey(keys, t, eps = 0.0005) {
  return keys.findIndex((k) => Math.abs(k.t - t) < eps);
}

export function setKey(anim, bone, channel, t, v, easing) {
  const keys = track(anim, bone)[channel];
  t = round(t, 4);
  const i = findKey(keys, t);
  const val = v.map((n) => round(n, 3));
  if (i >= 0) {
    keys[i].v = val;
    if (easing) keys[i].easing = easing;
  } else {
    keys.push({ t, v: val, easing: easing || 'easeInOutSine' });
    sortKeys(keys);
  }
}

export function removeKey(anim, bone, channel, t) {
  const tr = anim.tracks[bone];
  if (!tr) return;
  const chans = channel ? [channel] : CHANNELS;
  for (const c of chans) {
    const i = findKey(tr[c], t);
    if (i >= 0) tr[c].splice(i, 1);
  }
  pruneEmpty(anim);
}

export function pruneEmpty(anim) {
  for (const b of Object.keys(anim.tracks)) {
    const tr = anim.tracks[b];
    if (CHANNELS.every((c) => !tr[c] || tr[c].length === 0)) delete anim.tracks[b];
  }
}

// Sorted unique key times for a bone (all channels), or for all bones if bone is null.
export function keyTimes(anim, bone = null) {
  const set = new Set();
  const bones = bone ? [bone] : Object.keys(anim.tracks);
  for (const b of bones) {
    const tr = anim.tracks[b];
    if (!tr) continue;
    for (const c of CHANNELS) for (const k of tr[c] || []) set.add(round(k.t, 4));
  }
  return [...set].sort((a, b) => a - b);
}

// Move every key on `bones` at time `from` to time `to` (overwrites anything already at `to`).
export function moveKeysAt(anim, bones, from, to) {
  to = Math.max(0, round(to, 4));
  for (const b of bones) {
    const tr = anim.tracks[b];
    if (!tr) continue;
    for (const c of CHANNELS) {
      const keys = tr[c];
      const i = findKey(keys, from);
      if (i < 0) continue;
      const k = keys.splice(i, 1)[0];
      const j = findKey(keys, to);
      if (j >= 0) keys.splice(j, 1);
      k.t = to;
      keys.push(k);
      sortKeys(keys);
    }
  }
}

export function setEasingAt(anim, bones, t, easing, args) {
  for (const b of bones) {
    const tr = anim.tracks[b];
    if (!tr) continue;
    for (const c of CHANNELS) {
      const k = tr[c][findKey(tr[c], t)];
      if (k) { k.easing = easing; if (args && args.length) k.args = args; else delete k.args; }
    }
  }
}

function catmull(p0, p1, p2, p3, t) {
  const t2 = t * t, t3 = t2 * t;
  return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
}

export function sampleChannel(keys, t, channel) {
  const def = DEFAULTS[channel];
  if (!keys || keys.length === 0) return def.slice();
  if (t <= keys[0].t) return keys[0].v.slice();
  const last = keys[keys.length - 1];
  if (t >= last.t) return last.v.slice();
  let i = 0;
  while (i < keys.length - 1 && keys[i + 1].t < t) i++;
  const a = keys[i], b = keys[i + 1];
  const span = b.t - a.t;
  const raw = span <= 0 ? 1 : (t - a.t) / span;
  if (b.easing === 'catmullrom') {
    const p0 = keys[Math.max(0, i - 1)].v, p3 = keys[Math.min(keys.length - 1, i + 2)].v;
    return [0, 1, 2].map((n) => catmull(p0[n], a.v[n], b.v[n], p3[n], raw));
  }
  const e = ease(b.easing, raw, b.args ? b.args[0] : undefined);
  return [0, 1, 2].map((n) => a.v[n] + (b.v[n] - a.v[n]) * e);
}

// Returns { [bone]: { rotation, position, scale } } for every animated bone.
export function sampleAnimation(anim, t) {
  const out = {};
  for (const [bone, tr] of Object.entries(anim.tracks)) {
    out[bone] = {
      rotation: sampleChannel(tr.rotation, t, 'rotation'),
      position: sampleChannel(tr.position, t, 'position'),
      scale: sampleChannel(tr.scale, t, 'scale'),
    };
  }
  return out;
}

export function lastKeyTime(anim) {
  const ts = keyTimes(anim);
  return ts.length ? ts[ts.length - 1] : 0;
}

// Scale time of keys in [from, to] by factor, shifting later keys so nothing overlaps.
export function scaleTimeRange(anim, factor, from = 0, to = Infinity) {
  const end = Math.min(to, Math.max(anim.length, lastKeyTime(anim)));
  const delta = (end - from) * (factor - 1);
  const map = (t) => {
    if (t < from - 1e-6) return t;
    if (t <= end + 1e-6) return from + (t - from) * factor;
    return t + delta;
  };
  for (const tr of Object.values(anim.tracks)) for (const c of CHANNELS) for (const k of tr[c]) k.t = snapTime(map(k.t), 0.0005);
  for (const s of anim.sections || []) { s.start = round(map(s.start), 4); s.end = round(map(s.end), 4); }
  anim.length = round(Math.max(0.05, anim.length + (anim.length >= from ? delta : 0)), 4);
  dedupeKeys(anim);
}

export function dedupeKeys(anim) {
  for (const tr of Object.values(anim.tracks)) for (const c of CHANNELS) {
    sortKeys(tr[c]);
    tr[c] = tr[c].filter((k, i, arr) => i === arr.length - 1 || Math.abs(arr[i + 1].t - k.t) > 0.0005);
  }
}

// Copy the full pose of `bones` at time t (sampled, so works between keys too).
export function copyPose(anim, bones, t) {
  const pose = {};
  for (const b of bones) {
    const tr = anim.tracks[b];
    pose[b] = {
      rotation: sampleChannel(tr?.rotation, t, 'rotation'),
      position: sampleChannel(tr?.position, t, 'position'),
      scale: sampleChannel(tr?.scale, t, 'scale'),
    };
  }
  return pose;
}

const isDefault = (v, c) => v.every((n, i) => Math.abs(n - DEFAULTS[c][i]) < 1e-4);

export function pastePose(anim, pose, t, easing = 'easeInOutSine') {
  for (const [b, p] of Object.entries(pose)) {
    for (const c of CHANNELS) {
      const has = (anim.tracks[b]?.[c]?.length || 0) > 0;
      if (has || !isDefault(p[c], c)) setKey(anim, b, c, t, p[c], easing);
    }
  }
}

// Rename a bone across an animation (used when retargeting library clips).
export function renameBones(anim, map) {
  const tracks = {};
  for (const [b, tr] of Object.entries(anim.tracks)) {
    const nb = map[b] === undefined ? b : map[b];
    if (nb) tracks[nb] = tr;
  }
  anim.tracks = tracks;
}

// Replace keys inside [start, end) for bones in `bones` with those from `src` (times relative to start).
export function spliceSection(anim, src, start, oldEnd, newEnd) {
  const shift = newEnd - oldEnd;
  for (const tr of Object.values(anim.tracks)) for (const c of CHANNELS) {
    tr[c] = tr[c].filter((k) => k.t < start - 1e-4 || k.t >= oldEnd - 1e-4);
    for (const k of tr[c]) if (k.t >= oldEnd - 1e-4) k.t = round(k.t + shift, 4);
  }
  for (const [b, str] of Object.entries(src.tracks)) {
    for (const c of CHANNELS) for (const k of str[c] || []) {
      if (k.t > newEnd - start + 1e-4) continue;
      // don't stomp the boundary key of the next section
      if (k.t >= newEnd - start - 1e-4 && findKey(track(anim, b)[c], newEnd) >= 0) continue;
      setKey(anim, b, c, start + k.t, k.v, k.easing);
    }
  }
  for (const s of anim.sections || []) if (s.start >= oldEnd - 1e-4) { s.start = round(s.start + shift, 4); s.end = round(s.end + shift, 4); }
  anim.length = round(anim.length + shift, 4);
  pruneEmpty(anim);
}
