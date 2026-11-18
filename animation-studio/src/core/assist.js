// Natural-motion assistance. Works on any animation (preset, generated or hand-keyed) and only
// ever adds or edits normal keyframes, so the result stays fully editable.
import { CHANNELS, round, sampleChannel, setKey, sortKeys, findKey } from './animation.js';
import { clampToLimits, JOINT_LIMITS } from './rig.js';

const CHILD_DELAY = { rFore: 0.035, lFore: 0.035, head: 0.03, rItem: 0.05, lItem: 0.05, rShin: 0.02, lShin: 0.02 };

// Overlapping action: child joints trail their parent by a tick or so, which turns a stiff
// "everything arrives at once" pose change into a whip-like motion.
export function addOverlap(anim, rig, scale = 1) {
  let changed = 0;
  for (const [role, delay] of Object.entries(CHILD_DELAY)) {
    const bone = rig.bone(role);
    const keys = bone && anim.tracks[bone]?.rotation;
    if (!keys || keys.length < 3) continue;
    for (let i = 1; i < keys.length - 1; i++) {
      const room = (keys[i + 1].t - keys[i].t) * 0.4;
      const d = Math.min(delay * scale, room);
      if (d > 0.005) { keys[i].t = round(keys[i].t + d, 4); changed++; }
    }
  }
  return changed;
}

export function enforceLimits(anim, rig) {
  const report = [];
  for (const role of Object.keys(JOINT_LIMITS)) {
    const bone = rig.bone(role);
    const keys = bone && anim.tracks[bone]?.rotation;
    if (!keys) continue;
    for (const k of keys) {
      const { v, clamped } = clampToLimits(role, k.v);
      if (clamped) { report.push({ bone, t: k.t, from: k.v.slice(), to: v }); k.v = v.map((n) => round(n, 3)); }
    }
  }
  return report;
}

const angSpeed = (a, b) => {
  const dt = Math.max(1e-3, b.t - a.t);
  return Math.max(...a.v.map((n, i) => Math.abs(b.v[i] - n))) / dt;
};

// "Polish" = the assistant's one-tap fix for robotic motion. Returns human-readable notes.
export function polish(anim, rig, { anticipation = true, followThrough = true, easing = true, headStabilize = true, weight = true, limits = true } = {}) {
  const notes = [];
  // 1. Easing: linear segments look mechanical
  if (easing) {
    let n = 0;
    for (const tr of Object.values(anim.tracks)) for (const c of CHANNELS) for (const k of tr[c] || []) if (k.easing === 'linear') { k.easing = 'easeInOutSine'; n++; }
    if (n) notes.push(`Smoothed ${n} linear keyframe${n > 1 ? 's' : ''} so motion speeds up and slows down naturally.`);
  }
  // 2/3. Anticipation and follow-through around fast, big rotations
  let ant = 0, fol = 0;
  for (const [bone, tr] of Object.entries(anim.tracks)) {
    const keys = tr.rotation;
    if (!keys || keys.length < 2) continue;
    const add = [];
    for (let i = 0; i < keys.length - 1; i++) {
      const a = keys[i], b = keys[i + 1];
      const delta = b.v.map((n, j) => n - a.v[j]);
      const amp = Math.max(...delta.map(Math.abs));
      if (amp < 35 || angSpeed(a, b) < 250) continue;
      // anticipation: small move the opposite way just before the strike
      const prevT = i > 0 ? keys[i - 1].t : 0;
      const gapBefore = a.t - prevT;
      if (anticipation && (i === 0 ? a.t >= 0.1 : gapBefore >= 0.12)) {
        const at = round(a.t - Math.min(0.1, gapBefore * 0.4), 4);
        const base = sampleChannel(keys, at, 'rotation');
        if (findKey(keys, at) < 0) { add.push({ t: a.t, v: a.v.map((n, j) => n - delta[j] * 0.12), easing: 'easeOutSine', shiftOrig: true, at, base }); ant++; }
      }
      // follow-through: overshoot slightly, then settle
      const nextT = i + 2 < keys.length ? keys[i + 2].t : anim.length;
      const gapAfter = nextT - b.t;
      if (followThrough && gapAfter >= 0.12) {
        add.push({ t: round(b.t + Math.min(0.08, gapAfter * 0.35), 4), v: b.v.map((n, j) => n + delta[j] * 0.07), easing: 'easeOutSine' });
        b.easing = b.easing === 'easeInOutSine' || b.easing === 'linear' ? 'easeOutCubic' : b.easing;
        fol++;
      }
    }
    for (const k of add) {
      if (k.shiftOrig) {
        // the pose before the strike becomes a coil: original key moves earlier, coil sits at the strike start
        const orig = keys.find((x) => Math.abs(x.t - k.t) < 0.0005);
        const coil = { t: k.t, v: k.v.map((n) => round(n, 3)), easing: 'easeOutSine' };
        if (orig) { orig.t = k.at; }
        keys.push(coil);
      } else if (findKey(keys, k.t) < 0) keys.push({ t: k.t, v: k.v.map((n) => round(n, 3)), easing: k.easing });
    }
    sortKeys(keys);
  }
  if (ant) notes.push(`Added ${ant} anticipation beat${ant > 1 ? 's' : ''} (a small wind-up the opposite way before fast moves).`);
  if (fol) notes.push(`Added ${fol} follow-through beat${fol > 1 ? 's' : ''} (moves overshoot a little and settle instead of stopping dead).`);

  // 4. Head stabilisation: counter-rotate the head against torso twist so the character keeps looking at the target
  const bodyB = rig.bone('body'), headB = rig.bone('head');
  if (headStabilize && bodyB && headB && anim.tracks[bodyB]?.rotation?.length) {
    const bk = anim.tracks[bodyB].rotation;
    const maxTwist = Math.max(...bk.map((k) => Math.abs(k.v[1])));
    const hk = anim.tracks[headB]?.rotation || [];
    const alreadyCounters = hk.some((k) => Math.abs(k.v[1]) > 3);
    if (maxTwist > 12 && !alreadyCounters) {
      for (const k of bk) {
        const hv = sampleChannel(hk.length ? hk : null, k.t, 'rotation');
        setKey(anim, headB, 'rotation', k.t, [hv[0], hv[1] - k.v[1] * 0.55, hv[2]], k.easing);
      }
      notes.push('Head now counter-rotates against the torso so the character keeps eyes on the target.');
    }
  }

  // 5. Weight: big arm strikes with a static body look weightless – dip the hips at the strike
  const rootB = rig.bone('root');
  if (weight && rootB) {
    const pos = anim.tracks[rootB]?.position || [];
    if (!pos.length) {
      let best = null;
      for (const role of ['rArm', 'lArm', 'rLeg', 'lLeg']) {
        const keys = anim.tracks[rig.bone(role)]?.rotation;
        if (!keys) continue;
        for (let i = 0; i < keys.length - 1; i++) { const s = angSpeed(keys[i], keys[i + 1]); if (s > 300 && (!best || s > best.s)) best = { s, t: keys[i + 1].t, t0: keys[i].t }; }
      }
      if (best) {
        setKey(anim, rootB, 'position', 0, [0, 0, 0], 'easeInOutSine');
        setKey(anim, rootB, 'position', best.t0, [0, 0.2, 0.2], 'easeInOutSine');
        setKey(anim, rootB, 'position', best.t, [0, -1, -0.6], 'easeOutCubic');
        setKey(anim, rootB, 'position', Math.min(anim.length, best.t + 0.35), [0, 0, 0], 'easeInOutSine');
        notes.push('Added a weight shift: the body dips into the strongest strike.');
      }
    }
  }

  // 6. Joint limits
  if (limits) {
    const r = enforceLimits(anim, rig);
    if (r.length) notes.push(`Fixed ${r.length} impossible joint angle${r.length > 1 ? 's' : ''} (e.g. elbows or knees bending backwards).`);
  }
  if (!notes.length) notes.push('Looks good already – nothing needed fixing.');
  return notes;
}

// Report problems without changing anything (shown as hints in the Pose panel / timeline).
export function analyze(anim, rig) {
  const issues = [];
  for (const [role] of Object.entries(JOINT_LIMITS)) {
    const bone = rig.bone(role);
    for (const k of anim.tracks[bone]?.rotation || []) {
      if (clampToLimits(role, k.v).clamped) { issues.push({ bone, t: k.t, msg: `${bone} bends past a natural limit at ${k.t.toFixed(2)}s` }); break; }
    }
  }
  let linear = 0;
  for (const tr of Object.values(anim.tracks)) for (const c of CHANNELS) linear += (tr[c] || []).filter((k) => k.easing === 'linear').length;
  if (linear > 3) issues.push({ msg: `${linear} keyframes use Linear easing – motion may look robotic.` });
  return issues;
}
