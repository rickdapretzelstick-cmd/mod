// Easing functions. Names match GeckoLib's EasingType keys so they export 1:1.
// An easing on a keyframe controls the segment that ENDS at that keyframe (GeckoLib semantics).

const PI = Math.PI;
const bounceOut = (t) => {
  const n1 = 7.5625, d1 = 2.75;
  if (t < 1 / d1) return n1 * t * t;
  if (t < 2 / d1) return n1 * (t -= 1.5 / d1) * t + 0.75;
  if (t < 2.5 / d1) return n1 * (t -= 2.25 / d1) * t + 0.9375;
  return n1 * (t -= 2.625 / d1) * t + 0.984375;
};
const inOut = (fin) => (t) => (t < 0.5 ? fin(t * 2) / 2 : 1 - fin((1 - t) * 2) / 2);
const out = (fin) => (t) => 1 - fin(1 - t);
const pow = (p) => (t) => Math.pow(t, p);
const sineIn = (t) => 1 - Math.cos((t * PI) / 2);
const expoIn = (t) => (t === 0 ? 0 : Math.pow(2, 10 * t - 10));
const circIn = (t) => 1 - Math.sqrt(1 - t * t);
const backIn = (s = 1.70158) => (t) => (s + 1) * t * t * t - s * t * t;
const elasticIn = (b = 1) => (t) => {
  if (t === 0 || t === 1) return t;
  const c4 = (2 * PI) / (3 / Math.max(0.1, b));
  return -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * c4);
};
const bounceIn = (t) => 1 - bounceOut(1 - t);

// fn(t, arg) where arg is the optional easingArgs[0]
export const EASINGS = {
  linear: (t) => t,
  step: (t, a) => { const n = Math.max(1, Math.round(a ?? 1)); return Math.min(1, Math.floor(t * n) / n + (t >= 1 ? 0 : 0)); },
  catmullrom: (t) => t, // handled specially by the sampler (needs neighbour keys)
  easeInSine: sineIn, easeOutSine: out(sineIn), easeInOutSine: inOut(sineIn),
  easeInQuad: pow(2), easeOutQuad: out(pow(2)), easeInOutQuad: inOut(pow(2)),
  easeInCubic: pow(3), easeOutCubic: out(pow(3)), easeInOutCubic: inOut(pow(3)),
  easeInQuart: pow(4), easeOutQuart: out(pow(4)), easeInOutQuart: inOut(pow(4)),
  easeInQuint: pow(5), easeOutQuint: out(pow(5)), easeInOutQuint: inOut(pow(5)),
  easeInExpo: expoIn, easeOutExpo: out(expoIn), easeInOutExpo: inOut(expoIn),
  easeInCirc: circIn, easeOutCirc: out(circIn), easeInOutCirc: inOut(circIn),
  easeInBack: (t, a) => backIn(a)(t), easeOutBack: (t, a) => out(backIn(a))(t), easeInOutBack: (t, a) => inOut(backIn(a))(t),
  easeInElastic: (t, a) => elasticIn(a)(t), easeOutElastic: (t, a) => out(elasticIn(a))(t), easeInOutElastic: (t, a) => inOut(elasticIn(a))(t),
  easeInBounce: bounceIn, easeOutBounce: bounceOut, easeInOutBounce: inOut(bounceIn),
};

// Beginner-facing presets → GeckoLib easing names.
export const EASE_PRESETS = [
  { id: 'linear', label: 'Linear', easing: 'linear', hint: 'Constant speed. Feels mechanical.' },
  { id: 'smooth', label: 'Smooth', easing: 'easeInOutSine', hint: 'Starts and stops gently. Good default.' },
  { id: 'easeIn', label: 'Ease in', easing: 'easeInCubic', hint: 'Starts slow, ends fast. Good for wind-ups into hits.' },
  { id: 'easeOut', label: 'Ease out', easing: 'easeOutCubic', hint: 'Starts fast, slows down. Good for strikes and impacts.' },
  { id: 'bounce', label: 'Bounce', easing: 'easeOutBounce', hint: 'Bounces into place, like landing.' },
  { id: 'overshoot', label: 'Overshoot', easing: 'easeOutBack', hint: 'Goes past the pose then settles. Adds snap.' },
];

export function presetForEasing(name) {
  return EASE_PRESETS.find((p) => p.easing === name) || null;
}

export function ease(name, t, arg) {
  const f = EASINGS[name] || EASINGS.linear;
  return f(Math.min(1, Math.max(0, t)), arg);
}

export const EASING_NAMES = Object.keys(EASINGS);
