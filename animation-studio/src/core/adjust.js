// Plain-language adjustments ("make it faster", "more power"...). Each edits keyframes in place
// for the whole animation or one section, and returns a sentence describing what changed.
import { CHANNELS, round, scaleTimeRange, setKey, sampleChannel } from './animation.js';
import { enforceLimits } from './assist.js';

const inRange = (k, r) => k.t >= r.start - 1e-4 && k.t <= r.end + 1e-4;
const rangeOf = (anim, section) => (section ? { start: section.start, end: section.end } : { start: 0, end: anim.length });

function eachKey(anim, r, fn, channels = CHANNELS) {
  for (const [bone, tr] of Object.entries(anim.tracks)) for (const c of channels) for (const k of tr[c] || []) if (inRange(k, r)) fn(k, bone, c, tr[c]);
}

// Fastest rotation segment in range → { t0, t1 } (the "strike")
export function findStrike(anim, r) {
  let best = null;
  for (const tr of Object.values(anim.tracks)) {
    const keys = tr.rotation || [];
    for (let i = 0; i < keys.length - 1; i++) {
      const a = keys[i], b = keys[i + 1];
      if (!inRange(a, r) || !inRange(b, r)) continue;
      const s = Math.max(...a.v.map((n, j) => Math.abs(b.v[j] - n))) / Math.max(1e-3, b.t - a.t);
      if (!best || s > best.s) best = { s, t0: a.t, t1: b.t };
    }
  }
  return best;
}

const SHARPER = { easeInOutSine: 'easeOutCubic', easeOutSine: 'easeOutCubic', easeOutQuad: 'easeOutCubic', easeOutCubic: 'easeOutQuart', easeOutQuart: 'easeOutQuint', linear: 'easeOutCubic' };
const SOFTER = { easeOutQuint: 'easeOutQuart', easeOutQuart: 'easeOutCubic', easeOutCubic: 'easeInOutSine', easeOutBack: 'easeOutCubic', easeInQuart: 'easeInCubic', easeInCubic: 'easeInOutSine', linear: 'easeInOutSine', step: 'easeInOutSine' };

function scaleAmplitude(anim, r, k, rig) {
  // scale each key's deviation from the pose at the range start (so the section still connects)
  for (const [bone, tr] of Object.entries(anim.tracks)) for (const c of ['rotation', 'position']) {
    const keys = tr[c] || [];
    const ref = sampleChannel(keys, r.start, c);
    for (const key of keys) {
      if (!inRange(key, r) || Math.abs(key.t - r.start) < 1e-4 || Math.abs(key.t - r.end) < 1e-4 && r.end < anim.length - 1e-4) continue;
      // leave whole-body spins/turns alone
      if (c === 'rotation' && rig?.roleOf(bone) === 'root') continue;
      key.v = key.v.map((n, i) => round(ref[i] + (n - ref[i]) * k, 3));
    }
  }
  if (rig) enforceLimits(anim, rig);
}

export const ADJUSTMENTS = {
  faster: { label: 'Faster', icon: '⏩', run(anim, section) { const r = rangeOf(anim, section); scaleTimeRange(anim, 0.8, r.start, r.end); return 'Sped up by 20%.'; } },
  slower: { label: 'Slower', icon: '🐢', run(anim, section) { const r = rangeOf(anim, section); scaleTimeRange(anim, 1.25, r.start, r.end); return 'Slowed down by 25%.'; } },
  power: {
    label: 'More power', icon: '💪',
    run(anim, section, rig) {
      const r = rangeOf(anim, section);
      const s = findStrike(anim, r);
      scaleAmplitude(anim, r, 1.18, rig);
      if (s) {
        eachKey(anim, r, (k) => { if (Math.abs(k.t - s.t1) < 1e-4) k.easing = SHARPER[k.easing] || k.easing; });
        scaleTimeRange(anim, 0.85, s.t0, s.t1);
      }
      return 'Bigger poses, a sharper strike and a faster hit.';
    },
  },
  less: { label: 'Less exaggerated', icon: '🤏', run(anim, section, rig) { scaleAmplitude(anim, rangeOf(anim, section), 0.75, rig); return 'Toned the poses down by 25%.'; } },
  more: { label: 'More exaggerated', icon: '🎭', run(anim, section, rig) { scaleAmplitude(anim, rangeOf(anim, section), 1.25, rig); return 'Pushed the poses 25% further.'; } },
  weight: {
    label: 'More weight', icon: '🏋️',
    run(anim, section, rig) {
      const r = rangeOf(anim, section);
      const s = findStrike(anim, r);
      if (!s) return 'No strong movement found to add weight to.';
      // longer wind-up, heavier landing and a slower recovery
      const windStart = Math.max(r.start, s.t0 - 0.3);
      scaleTimeRange(anim, 1.35, windStart, s.t0);
      const s2 = findStrike(anim, rangeOf(anim, section && { start: section.start, end: section.end }));
      const rootB = rig?.bone('root');
      if (rootB && s2) {
        const pos = anim.tracks[rootB]?.position || [];
        const at = sampleChannel(pos, s2.t1, 'position');
        setKey(anim, rootB, 'position', s2.t1, [at[0], at[1] - 1.2, at[2]], 'easeOutQuad');
      }
      if (s2) scaleTimeRange(anim, 1.25, s2.t1, section ? section.end : anim.length);
      return 'Longer wind-up, the body drops into the hit, and the recovery is heavier.';
    },
  },
  recovery: {
    label: 'Slower recovery', icon: '😮‍💨',
    run(anim, section) {
      const r = rangeOf(anim, section);
      const s = findStrike(anim, r);
      const from = s ? s.t1 : r.start + (r.end - r.start) / 2;
      scaleTimeRange(anim, 1.5, from, r.end);
      return 'The return after the hit now takes 50% longer.';
    },
  },
  smoother: { label: 'Smoother', icon: '🌊', run(anim, section) { let n = 0; eachKey(anim, rangeOf(anim, section), (k) => { const s = SOFTER[k.easing]; if (s) { k.easing = s; n++; } }); return `Softened ${n} transitions.`; } },
  snappier: {
    label: 'Snappier', icon: '⚡',
    run(anim, section) {
      const r = rangeOf(anim, section);
      const s = findStrike(anim, r);
      let n = 0;
      eachKey(anim, r, (k) => { const s2 = SHARPER[k.easing]; if (s2 && (!s || k.t >= s.t1 - 1e-4)) { k.easing = s2; n++; } });
      if (s) scaleTimeRange(anim, 0.8, s.t0, s.t1);
      return `Sharper timing on ${n} keys.`;
    },
  },
};
