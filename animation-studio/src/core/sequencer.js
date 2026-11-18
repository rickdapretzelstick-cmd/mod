// Turns a list of motion blocks into a real, editable keyframe animation.
// Transitions between blocks are automatic: each block starts from wherever the previous one
// ended, so a combo flows straight from one strike's follow-through into the next wind-up.
import { BLOCKS } from './blocks.js';
import { createAnimation, setKey, round, CHANNELS, spliceSection, uid } from './animation.js';
import { addOverlap, enforceLimits } from './assist.js';

const CH = { r: 'rotation', p: 'position' };

function snapshot(pose) { return JSON.parse(JSON.stringify(pose)); }

export function compileSequence(steps, rig, { name, assist = true, transition = 0.08 } = {}) {
  const roles = new Set();
  const built = steps.map((s) => {
    const def = BLOCKS[s.block];
    if (!def) throw new Error(`Unknown motion block "${s.block}"`);
    const beats = def.build(s.opts || {});
    for (const b of beats) for (const r of Object.keys(b.pose)) roles.add(r);
    return { step: s, def, beats };
  });

  // start every used role at rest so the first frame is fully defined
  let cur = {};
  for (const r of roles) cur[r] = { r: [0, 0, 0], p: [0, 0, 0] };
  const snaps = [];
  const sections = [];
  let t = 0;
  built.forEach(({ step, def, beats }, i) => {
    const start = t;
    beats.forEach((beat, j) => {
      if (i > 0 && j === 0) return; // flow from the previous block's last pose
      let d = beat.d;
      if (i === 0 && j === 0) d = 0;
      else if (i > 0 && j === 1) d += transition / (step.opts?.speed ?? 1);
      t = round(t + d, 4);
      for (const [role, v] of Object.entries(beat.pose)) cur[role] = { ...cur[role], ...snapshot(v) };
      snaps.push({ t, pose: snapshot(cur), ease: beat.ease, tag: beat.tag });
    });
    sections.push({ id: uid('sec'), label: step.label || def.label, start, end: t, block: step.block, opts: { ...(step.opts || {}) } });
  });

  const only = built.length === 1 ? built[0].def : null;
  const last = built[built.length - 1].def;
  const anim = createAnimation(name || steps.map((s) => s.block).join('_then_'), Math.max(0.05, t), only?.loop ? 'loop' : last.hold ? 'hold' : 'once');
  anim.sections = sections;

  const rootBone = rig.bone('root');
  const bodyBone = rig.bone('body');
  for (const s of snaps) {
    for (const [role, v] of Object.entries(s.pose)) {
      let bone = rig.bone(role);
      if (role === 'root' && !rootBone && bodyBone) bone = bodyBone; // no root: whole-body motion goes on the torso
      if (!bone) continue;
      for (const [k, ch] of Object.entries(CH)) {
        if (!v[k]) continue;
        let val = v[k];
        if (role === 'body' && !rootBone && s.pose.root?.[k]) val = val.map((n, i) => n + s.pose.root[k][i]);
        if (role === 'root' && !rootBone) { if (bone === bodyBone) continue; }
        setKey(anim, bone, ch, s.t, val, s.ease);
      }
    }
  }
  pruneRedundant(anim);
  if (assist) {
    addOverlap(anim, rig);
    enforceLimits(anim, rig);
  }
  return anim;
}

// Drop keys that sit in the middle of a hold (prev == this == next), and channels that never move.
export function pruneRedundant(anim) {
  const same = (a, b) => a.v.every((n, i) => Math.abs(n - b.v[i]) < 0.01);
  for (const [bone, tr] of Object.entries(anim.tracks)) {
    for (const ch of CHANNELS) {
      const keys = tr[ch];
      if (!keys?.length) continue;
      const def = ch === 'scale' ? 1 : 0;
      if (keys.every((k) => k.v.every((n) => Math.abs(n - def) < 0.01))) { tr[ch] = []; continue; }
      tr[ch] = keys.filter((k, i) => i === 0 || i === keys.length - 1 || !(same(keys[i - 1], k) && same(k, keys[i + 1])));
    }
    if (CHANNELS.every((c) => !tr[c].length)) delete anim.tracks[bone];
  }
}

// Rebuild one section with new options (e.g. regenerate with variation, or more power),
// replacing only that section's keys and leaving manual edits elsewhere untouched.
export function regenerateSection(anim, sectionId, newOpts, rig) {
  const idx = anim.sections.findIndex((s) => s.id === sectionId);
  if (idx < 0) throw new Error('Section not found');
  const steps = anim.sections.map((s, i) => ({ block: s.block, label: s.label, opts: i === idx ? { ...s.opts, ...newOpts } : s.opts }));
  const fresh = compileSequence(steps, rig, { name: anim.name });
  const ns = fresh.sections[idx], os = anim.sections[idx];
  // extract fresh section keys relative to its start
  const sub = { tracks: {} };
  for (const [b, tr] of Object.entries(fresh.tracks)) {
    sub.tracks[b] = { rotation: [], position: [], scale: [] };
    for (const c of CHANNELS) for (const k of tr[c]) if (k.t >= ns.start - 1e-4 && k.t <= ns.end + 1e-4) sub.tracks[b][c].push({ ...k, t: round(k.t - ns.start, 4) });
  }
  const oldStart = os.start, oldEnd = os.end;
  const newEnd = oldStart + (ns.end - ns.start);
  spliceSection(anim, sub, oldStart, oldEnd, newEnd);
  os.opts = { ...os.opts, ...newOpts };
  os.end = round(newEnd, 4);
  if (idx === anim.sections.length - 1) anim.length = Math.max(anim.length, os.end);
  return anim;
}
