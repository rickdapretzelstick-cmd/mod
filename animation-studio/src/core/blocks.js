// Motion building blocks. Each block returns a list of "beats" (timed full-body poses on roles).
// They are authored with animation principles baked in: anticipation before strikes, a fast
// eased strike, overshoot/follow-through, weight shift through the hips/legs, torso rotation
// leading the arm, and a counter-rotating head that keeps looking at the target.
//
// Pose = { [role]: { r?: [x,y,z], p?: [x,y,z] } }   (GeckoLib degrees / pixels)
// Beat = { d: seconds since previous beat, pose, ease, tag? }  tag ∈ anticipation|strike|follow|recover|hold|contact

const R = (x = 0, y = 0, z = 0) => [x, y, z];

export const STANCES = {
  neutral: {
    root: { r: R(), p: R() }, body: { r: R() }, head: { r: R() },
    rArm: { r: R() }, rFore: { r: R() }, lArm: { r: R() }, lFore: { r: R() },
    rLeg: { r: R() }, rShin: { r: R() }, lLeg: { r: R() }, lShin: { r: R() }, rItem: { r: R() },
  },
  ready: {
    root: { r: R(), p: R(0, -0.6, 0) }, body: { r: R(6, 8, 0) }, head: { r: R(-4, -8, 0) },
    rArm: { r: R(-20, 0, 8) }, rFore: { r: R(-70, 0, 0) }, lArm: { r: R(-30, 0, -6) }, lFore: { r: R(-80, 0, 0) },
    rLeg: { r: R(10, 0, 6) }, rShin: { r: R(14, 0, 0) }, lLeg: { r: R(-14, 0, -6) }, lShin: { r: R(10, 0, 0) }, rItem: { r: R() },
  },
};

// ---------- helpers ----------
export function mirrorPose(pose) {
  const swap = { rArm: 'lArm', lArm: 'rArm', rFore: 'lFore', lFore: 'rFore', rLeg: 'lLeg', lLeg: 'rLeg', rShin: 'lShin', lShin: 'rShin', rItem: 'lItem', lItem: 'rItem' };
  const out = {};
  for (const [role, v] of Object.entries(pose)) {
    const nr = swap[role] || role;
    out[nr] = {};
    if (v.r) out[nr].r = [v.r[0], -v.r[1], -v.r[2]];
    if (v.p) out[nr].p = [-v.p[0], v.p[1], v.p[2]];
  }
  return out;
}

function merge(...poses) {
  const out = {};
  for (const p of poses) for (const [role, v] of Object.entries(p || {})) {
    out[role] = { ...(out[role] || {}), ...JSON.parse(JSON.stringify(v)) };
  }
  return out;
}

// Scale a pose's deviation from a base pose (exaggeration / power).
function amplify(pose, base, k) {
  if (k === 1) return pose;
  const out = {};
  for (const [role, v] of Object.entries(pose)) {
    out[role] = {};
    for (const ch of ['r', 'p']) if (v[ch]) {
      // whole-body rotations (turns, spins, falling over) are exact angles, never exaggerated
      if (role === 'root' && ch === 'r') { out[role][ch] = v[ch].slice(); continue; }
      const b = base?.[role]?.[ch] || [0, 0, 0];
      out[role][ch] = v[ch].map((n, i) => b[i] + (n - b[i]) * k);
    }
  }
  return out;
}

function rng(seed) {
  let s = (seed * 9301 + 49297) % 233280 || 1;
  return () => { s = (s * 9301 + 49297) % 233280; return s / 233280; };
}

// Final per-block processing: side mirroring, amplitude, timing, variation.
function finish(beats, o, base = STANCES.ready) {
  const amp = (o.exaggeration ?? 1) * (0.85 + 0.15 * (o.power ?? 1));
  const rand = o.variation ? rng(o.variation) : null;
  return beats.map((b, i) => {
    let pose = amplify(b.pose, base, amp * (rand && i > 0 ? 0.9 + rand() * 0.2 : 1));
    if (o.side === 'left') pose = mirrorPose(pose);
    let d = b.d / (o.speed ?? 1);
    if (b.tag === 'strike') d /= Math.max(0.6, Math.min(1.6, o.power ?? 1)); // more power → faster strike
    if (b.tag === 'recover') d *= o.recovery ?? 1;
    if (b.tag === 'anticipation') d *= o.anticipation ?? 1;
    if (rand && i > 0) d *= 0.92 + rand() * 0.16;
    return { ...b, d: Math.max(0.02, d), pose };
  });
}

const counterHead = (bodyY, k = 0.6) => -bodyY * k;

// ---------- blocks ----------
export const BLOCKS = {
  idle: {
    label: 'Idle (breathing)', category: 'Basics', loop: true, icon: '🧍',
    build(o) {
      const a = { body: { r: R(0, 0, 0) }, head: { r: R(0, 0, 0) }, rArm: { r: R(0, 0, 3) }, lArm: { r: R(0, 0, -3) }, rFore: { r: R(-5, 0, 0) }, lFore: { r: R(-5, 0, 0) }, root: { p: R(0, 0, 0) } };
      const b = { body: { r: R(2.5, 0, 0) }, head: { r: R(-3, 0, 1.5) }, rArm: { r: R(-2, 0, 6) }, lArm: { r: R(-2, 0, -6) }, rFore: { r: R(-9, 0, 0) }, lFore: { r: R(-9, 0, 0) }, root: { p: R(0, -0.25, 0) } };
      return finish([
        { d: 0, pose: merge(STANCES.neutral, a), ease: 'easeInOutSine' },
        { d: 1.1, pose: b, ease: 'easeInOutSine' },
        { d: 1.1, pose: a, ease: 'easeInOutSine' },
      ], o, STANCES.neutral);
    },
  },
  ready: {
    label: 'Fighting stance', category: 'Basics', loop: false, icon: '🥋',
    build(o) { return finish([{ d: 0, pose: STANCES.ready, ease: 'easeInOutSine' }, { d: 0.5, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'hold' }], o); },
  },
  walk: {
    label: 'Walk', category: 'Movement', loop: true, icon: '🚶',
    build(o) {
      const L = 28, A = 22;
      const contactR = { root: { p: R(0, -0.4, 0) }, body: { r: R(3, 5, 0) }, head: { r: R(-2, counterHead(5), 0) }, rLeg: { r: R(-L, 0, 0) }, rShin: { r: R(4, 0, 0) }, lLeg: { r: R(L * 0.9, 0, 0) }, lShin: { r: R(12, 0, 0) }, rArm: { r: R(A, 0, 3) }, rFore: { r: R(-12, 0, 0) }, lArm: { r: R(-A, 0, -3) }, lFore: { r: R(-22, 0, 0) } };
      const passR = { root: { p: R(0, 0.35, 0) }, body: { r: R(3, 0, 0) }, head: { r: R(-2, 0, 0) }, rLeg: { r: R(0, 0, 0) }, rShin: { r: R(2, 0, 0) }, lLeg: { r: R(-6, 0, 0) }, lShin: { r: R(42, 0, 0) }, rArm: { r: R(0, 0, 3) }, rFore: { r: R(-15, 0, 0) }, lArm: { r: R(0, 0, -3) }, lFore: { r: R(-15, 0, 0) } };
      const contactL = mirrorPose(contactR), passL = mirrorPose(passR);
      return finish([
        { d: 0, pose: merge(STANCES.neutral, contactR), ease: 'easeInOutSine', tag: 'contact' },
        { d: 0.25, pose: passR, ease: 'easeInOutSine' },
        { d: 0.25, pose: contactL, ease: 'easeInOutSine', tag: 'contact' },
        { d: 0.25, pose: passL, ease: 'easeInOutSine' },
        { d: 0.25, pose: contactR, ease: 'easeInOutSine', tag: 'contact' },
      ], { ...o, side: 'right' }, STANCES.neutral);
    },
  },
  run: {
    label: 'Run', category: 'Movement', loop: true, icon: '🏃',
    build(o) {
      const contactR = { root: { p: R(0, -1, 0) }, body: { r: R(16, 8, 0) }, head: { r: R(-12, counterHead(8), 0) }, rLeg: { r: R(-50, 0, 0) }, rShin: { r: R(15, 0, 0) }, lLeg: { r: R(40, 0, 0) }, lShin: { r: R(35, 0, 0) }, rArm: { r: R(45, 0, 5) }, rFore: { r: R(-70, 0, 0) }, lArm: { r: R(-55, 0, -5) }, lFore: { r: R(-95, 0, 0) } };
      const flightR = { root: { p: R(0, 1.4, 0) }, body: { r: R(16, 0, 0) }, head: { r: R(-12, 0, 0) }, rLeg: { r: R(-15, 0, 0) }, rShin: { r: R(20, 0, 0) }, lLeg: { r: R(-20, 0, 0) }, lShin: { r: R(95, 0, 0) }, rArm: { r: R(5, 0, 5) }, rFore: { r: R(-85, 0, 0) }, lArm: { r: R(-5, 0, -5) }, lFore: { r: R(-85, 0, 0) } };
      const contactL = mirrorPose(contactR), flightL = mirrorPose(flightR);
      return finish([
        { d: 0, pose: merge(STANCES.neutral, contactR), ease: 'easeInOutSine', tag: 'contact' },
        { d: 0.15, pose: flightR, ease: 'easeOutSine' },
        { d: 0.15, pose: contactL, ease: 'easeInSine', tag: 'contact' },
        { d: 0.15, pose: flightL, ease: 'easeOutSine' },
        { d: 0.15, pose: contactR, ease: 'easeInSine', tag: 'contact' },
      ], { ...o, side: 'right' }, STANCES.neutral);
    },
  },
  jump: {
    label: 'Jump', category: 'Movement', loop: false, icon: '⤴️',
    build(o) {
      const crouch = { root: { p: R(0, -3, 0) }, body: { r: R(22, 0, 0) }, head: { r: R(-15, 0, 0) }, rLeg: { r: R(-40, 0, 4) }, rShin: { r: R(70, 0, 0) }, lLeg: { r: R(-40, 0, -4) }, lShin: { r: R(70, 0, 0) }, rArm: { r: R(45, 0, 8) }, rFore: { r: R(-20, 0, 0) }, lArm: { r: R(45, 0, -8) }, lFore: { r: R(-20, 0, 0) } };
      const launch = { root: { p: R(0, 6, 0) }, body: { r: R(-6, 0, 0) }, head: { r: R(-8, 0, 0) }, rLeg: { r: R(-6, 0, 2) }, rShin: { r: R(8, 0, 0) }, lLeg: { r: R(10, 0, -2) }, lShin: { r: R(12, 0, 0) }, rArm: { r: R(-120, 0, 12) }, rFore: { r: R(-20, 0, 0) }, lArm: { r: R(-110, 0, -12) }, lFore: { r: R(-20, 0, 0) } };
      const apex = { root: { p: R(0, 9, 0) }, body: { r: R(8, 0, 0) }, head: { r: R(0, 0, 0) }, rLeg: { r: R(-45, 0, 4) }, rShin: { r: R(75, 0, 0) }, lLeg: { r: R(-35, 0, -4) }, lShin: { r: R(80, 0, 0) }, rArm: { r: R(-70, 0, 25) }, rFore: { r: R(-30, 0, 0) }, lArm: { r: R(-65, 0, -25) }, lFore: { r: R(-30, 0, 0) } };
      const fall = { root: { p: R(0, 3, 0) }, body: { r: R(4, 0, 0) }, rLeg: { r: R(-18, 0, 4) }, rShin: { r: R(22, 0, 0) }, lLeg: { r: R(-14, 0, -4) }, lShin: { r: R(25, 0, 0) }, rArm: { r: R(-40, 0, 35) }, lArm: { r: R(-40, 0, -35) } };
      const land = { root: { p: R(0, -2.6, 0) }, body: { r: R(20, 0, 0) }, head: { r: R(-12, 0, 0) }, rLeg: { r: R(-38, 0, 5) }, rShin: { r: R(65, 0, 0) }, lLeg: { r: R(-38, 0, -5) }, lShin: { r: R(65, 0, 0) }, rArm: { r: R(-25, 0, 20) }, rFore: { r: R(-30, 0, 0) }, lArm: { r: R(-25, 0, -20) }, lFore: { r: R(-30, 0, 0) } };
      return finish([
        { d: 0, pose: STANCES.neutral, ease: 'easeInOutSine' },
        { d: 0.28, pose: crouch, ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.12, pose: launch, ease: 'easeOutQuad', tag: 'strike' },
        { d: 0.22, pose: apex, ease: 'easeOutSine' },
        { d: 0.22, pose: fall, ease: 'easeInQuad' },
        { d: 0.1, pose: land, ease: 'easeOutQuad', tag: 'contact' },
        { d: 0.35, pose: STANCES.neutral, ease: 'easeInOutSine', tag: 'recover' },
      ], o, STANCES.neutral);
    },
  },
  fall: {
    label: 'Falling', category: 'Movement', loop: true, icon: '🪂',
    build(o) {
      const a = { root: { p: R(0, 0, 0) }, body: { r: R(-10, 0, 3) }, head: { r: R(-15, 0, 0) }, rArm: { r: R(-150, 0, 35) }, rFore: { r: R(-25, 0, 0) }, lArm: { r: R(-135, 0, -45) }, lFore: { r: R(-35, 0, 0) }, rLeg: { r: R(-20, 0, 8) }, rShin: { r: R(35, 0, 0) }, lLeg: { r: R(10, 0, -8) }, lShin: { r: R(50, 0, 0) } };
      const b = { root: { p: R(0, 0.3, 0) }, body: { r: R(-12, 0, -3) }, head: { r: R(-18, 0, 0) }, rArm: { r: R(-135, 0, 45) }, rFore: { r: R(-35, 0, 0) }, lArm: { r: R(-150, 0, -35) }, lFore: { r: R(-25, 0, 0) }, rLeg: { r: R(10, 0, 8) }, rShin: { r: R(50, 0, 0) }, lLeg: { r: R(-20, 0, -8) }, lShin: { r: R(35, 0, 0) } };
      return finish([
        { d: 0, pose: merge(STANCES.neutral, a), ease: 'easeInOutSine' },
        { d: 0.35, pose: b, ease: 'easeInOutSine' },
        { d: 0.35, pose: a, ease: 'easeInOutSine' },
      ], o, STANCES.neutral);
    },
  },
  land: {
    label: 'Landing', category: 'Movement', loop: false, icon: '⬇️',
    build(o) {
      const air = { root: { p: R(0, 4, 0) }, body: { r: R(-6, 0, 0) }, rArm: { r: R(-60, 0, 30) }, lArm: { r: R(-60, 0, -30) }, rLeg: { r: R(-15, 0, 4) }, rShin: { r: R(20, 0, 0) }, lLeg: { r: R(-10, 0, -4) }, lShin: { r: R(20, 0, 0) } };
      const impact = { root: { p: R(0, -4, 0) }, body: { r: R(32, 0, 0) }, head: { r: R(-20, 0, 0) }, rLeg: { r: R(-50, 0, 8) }, rShin: { r: R(90, 0, 0) }, lLeg: { r: R(-50, 0, -8) }, lShin: { r: R(90, 0, 0) }, rArm: { r: R(-20, 0, 30) }, rFore: { r: R(-30, 0, 0) }, lArm: { r: R(-20, 0, -30) }, lFore: { r: R(-30, 0, 0) } };
      const absorb = amplify(impact, STANCES.neutral, 0.85);
      return finish([
        { d: 0, pose: merge(STANCES.neutral, air), ease: 'easeInOutSine' },
        { d: 0.12, pose: impact, ease: 'easeInQuad', tag: 'strike' },
        { d: 0.12, pose: absorb, ease: 'easeOutQuad', tag: 'follow' },
        { d: 0.45, pose: STANCES.neutral, ease: 'easeInOutSine', tag: 'recover' },
      ], o, STANCES.neutral);
    },
  },
  punch: {
    label: 'Punch', category: 'Attacks', loop: false, icon: '👊', sided: true,
    build(o) {
      const wind = { root: { p: R(0, -0.8, 0.6) }, body: { r: R(4, 28, 0) }, head: { r: R(-4, counterHead(28, 0.8), 0) }, rArm: { r: R(28, 0, 10) }, rFore: { r: R(-115, 0, 0) }, lArm: { r: R(-45, 0, -6) }, lFore: { r: R(-85, 0, 0) }, rLeg: { r: R(14, 0, 6) }, rShin: { r: R(20, 0, 0) }, lLeg: { r: R(-16, 0, -6) }, lShin: { r: R(14, 0, 0) } };
      const hit = { root: { p: R(0, -1, -1.6) }, body: { r: R(12, -32, 0) }, head: { r: R(-8, counterHead(-32, 0.8), 0) }, rArm: { r: R(-92, -8, 0) }, rFore: { r: R(-4, 0, 0) }, lArm: { r: R(12, 0, -10) }, lFore: { r: R(-110, 0, 0) }, rLeg: { r: R(22, 0, 6) }, rShin: { r: R(8, 0, 0) }, lLeg: { r: R(-26, 0, -6) }, lShin: { r: R(22, 0, 0) } };
      const follow = merge(hit, { body: { r: R(14, -38, 0) }, rArm: { r: R(-96, -12, 0) }, rFore: { r: R(-2, 0, 0) } });
      const beats = [
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.16, pose: wind, ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.08, pose: hit, ease: 'easeOutQuart', tag: 'strike' },
        { d: 0.07, pose: follow, ease: 'easeOutSine', tag: 'follow' },
      ];
      if (o.recover !== false) beats.push({ d: 0.34, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o);
    },
  },
  uppercut: {
    label: 'Uppercut', category: 'Attacks', loop: false, icon: '🤜', sided: true,
    build(o) {
      const dip = { root: { p: R(0, -2.4, 0) }, body: { r: R(18, 22, 8) }, head: { r: R(-10, -15, 0) }, rArm: { r: R(20, 0, 15) }, rFore: { r: R(-80, 0, 0) }, lArm: { r: R(-45, 0, -6) }, lFore: { r: R(-90, 0, 0) }, rLeg: { r: R(-20, 0, 6) }, rShin: { r: R(45, 0, 0) }, lLeg: { r: R(-25, 0, -6) }, lShin: { r: R(40, 0, 0) } };
      const up = { root: { p: R(0, 0.8, -0.8) }, body: { r: R(-10, -25, -6) }, head: { r: R(-12, 15, 0) }, rArm: { r: R(-150, -10, 5) }, rFore: { r: R(-75, 0, 0) }, lArm: { r: R(5, 0, -10) }, lFore: { r: R(-100, 0, 0) }, rLeg: { r: R(8, 0, 6) }, rShin: { r: R(5, 0, 0) }, lLeg: { r: R(-10, 0, -6) }, lShin: { r: R(8, 0, 0) } };
      const beats = [
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.2, pose: dip, ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.1, pose: up, ease: 'easeOutBack', tag: 'strike' },
        { d: 0.08, pose: merge(up, { rArm: { r: R(-158, -10, 5) } }), ease: 'easeOutSine', tag: 'follow' },
      ];
      if (o.recover !== false) beats.push({ d: 0.4, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o);
    },
  },
  kick: {
    label: 'Kick', category: 'Attacks', loop: false, icon: '🦵', sided: true,
    build(o) {
      const chamber = { root: { p: R(0, 0.4, 0.5) }, body: { r: R(-10, 6, 4) }, head: { r: R(6, -6, 0) }, rLeg: { r: R(-70, 0, 6) }, rShin: { r: R(95, 0, 0) }, lLeg: { r: R(4, 0, -3) }, lShin: { r: R(14, 0, 0) }, rArm: { r: R(-10, 0, 25) }, rFore: { r: R(-60, 0, 0) }, lArm: { r: R(-40, 0, -30) }, lFore: { r: R(-75, 0, 0) } };
      const extend = merge(chamber, { root: { p: R(0, 0.2, -0.6) }, body: { r: R(-22, 4, 4) }, head: { r: R(12, -4, 0) }, rLeg: { r: R(-95, 0, 4) }, rShin: { r: R(2, 0, 0) }, lShin: { r: R(10, 0, 0) }, rArm: { r: R(15, 0, 35) }, lArm: { r: R(-25, 0, -40) } });
      const beats = [
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.2, pose: chamber, ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.09, pose: extend, ease: 'easeOutCubic', tag: 'strike' },
        { d: 0.08, pose: merge(extend, { rLeg: { r: R(-99, 0, 4) } }), ease: 'easeOutSine', tag: 'follow' },
        { d: 0.14, pose: chamber, ease: 'easeInOutSine' },
      ];
      if (o.recover !== false) beats.push({ d: 0.3, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o);
    },
  },
  sword_slash: {
    label: 'Sword slash', category: 'Weapons', loop: false, icon: '🗡️', sided: true,
    build(o) {
      const wind = { root: { p: R(0, -0.6, 0.6) }, body: { r: R(0, 42, 0) }, head: { r: R(-4, -30, 0) }, rArm: { r: R(-75, 20, 75) }, rFore: { r: R(-45, 0, 0) }, rItem: { r: R(0, 0, 0) }, lArm: { r: R(-35, 0, -20) }, lFore: { r: R(-70, 0, 0) }, rLeg: { r: R(12, 0, 6) }, rShin: { r: R(18, 0, 0) }, lLeg: { r: R(-14, 0, -6) }, lShin: { r: R(12, 0, 0) } };
      const slash = { root: { p: R(0, -1.2, -1.2) }, body: { r: R(10, -45, 0) }, head: { r: R(-6, 30, 0) }, rArm: { r: R(-80, -20, -35) }, rFore: { r: R(-10, 0, 0) }, rItem: { r: R(0, 0, 0) }, lArm: { r: R(10, 0, -25) }, lFore: { r: R(-60, 0, 0) }, rLeg: { r: R(20, 0, 6) }, rShin: { r: R(14, 0, 0) }, lLeg: { r: R(-26, 0, -6) }, lShin: { r: R(24, 0, 0) } };
      const follow = merge(slash, { body: { r: R(12, -56, 0) }, rArm: { r: R(-62, -25, -50) }, rFore: { r: R(-18, 0, 0) } });
      const beats = [
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.26, pose: wind, ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.1, pose: slash, ease: 'easeOutQuart', tag: 'strike' },
        { d: 0.12, pose: follow, ease: 'easeOutSine', tag: 'follow' },
      ];
      if (o.recover !== false) beats.push({ d: 0.42, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o);
    },
  },
  sword_overhead: {
    label: 'Overhead strike', category: 'Weapons', loop: false, icon: '⚔️', sided: true,
    build(o) {
      const raise = { root: { p: R(0, 0.6, 0.8) }, body: { r: R(-16, 12, 0) }, head: { r: R(-10, -8, 0) }, rArm: { r: R(-172, 0, 8) }, rFore: { r: R(-55, 0, 0) }, lArm: { r: R(-160, 0, -18) }, lFore: { r: R(-60, 0, 0) }, rLeg: { r: R(10, 0, 6) }, rShin: { r: R(8, 0, 0) }, lLeg: { r: R(-12, 0, -6) }, lShin: { r: R(8, 0, 0) } };
      const chop = { root: { p: R(0, -2.2, -1.8) }, body: { r: R(32, -4, 0) }, head: { r: R(-22, 4, 0) }, rArm: { r: R(-48, 0, -6) }, rFore: { r: R(-8, 0, 0) }, lArm: { r: R(-45, 0, 12) }, lFore: { r: R(-20, 0, 0) }, rLeg: { r: R(25, 0, 6) }, rShin: { r: R(30, 0, 0) }, lLeg: { r: R(-40, 0, -6) }, lShin: { r: R(45, 0, 0) } };
      const impact = merge(chop, { body: { r: R(36, -4, 0) }, rArm: { r: R(-40, 0, -6) }, root: { p: R(0, -2.6, -1.8) } });
      const beats = [
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.34, pose: raise, ease: 'easeOutCubic', tag: 'anticipation' },
        { d: 0.06, pose: merge(raise, { rArm: { r: R(-176, 0, 8) } }), ease: 'easeInOutSine', tag: 'hold' },
        { d: 0.11, pose: chop, ease: 'easeInQuart', tag: 'strike' },
        { d: 0.08, pose: impact, ease: 'easeOutQuad', tag: 'follow' },
        { d: 0.12, pose: impact, ease: 'linear', tag: 'hold' },
      ];
      if (o.recover !== false) beats.push({ d: 0.5, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o);
    },
  },
  sword_stab: {
    label: 'Thrust / stab', category: 'Weapons', loop: false, icon: '🔪', sided: true,
    build(o) {
      const pull = { root: { p: R(0, -0.8, 1) }, body: { r: R(0, 30, 0) }, head: { r: R(-4, -24, 0) }, rArm: { r: R(25, 0, 12) }, rFore: { r: R(-95, 0, 0) }, lArm: { r: R(-60, 0, -15) }, lFore: { r: R(-40, 0, 0) }, rLeg: { r: R(15, 0, 6) }, rShin: { r: R(20, 0, 0) }, lLeg: { r: R(-15, 0, -6) }, lShin: { r: R(15, 0, 0) } };
      const thrust = { root: { p: R(0, -1.4, -2.4) }, body: { r: R(15, -22, 0) }, head: { r: R(-10, 18, 0) }, rArm: { r: R(-88, -5, 0) }, rFore: { r: R(0, 0, 0) }, lArm: { r: R(20, 0, -20) }, lFore: { r: R(-30, 0, 0) }, rLeg: { r: R(28, 0, 6) }, rShin: { r: R(10, 0, 0) }, lLeg: { r: R(-38, 0, -6) }, lShin: { r: R(35, 0, 0) } };
      const beats = [
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.2, pose: pull, ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.08, pose: thrust, ease: 'easeOutQuint', tag: 'strike' },
        { d: 0.1, pose: merge(thrust, { root: { p: R(0, -1.5, -2.8) } }), ease: 'easeOutSine', tag: 'follow' },
      ];
      if (o.recover !== false) beats.push({ d: 0.36, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o);
    },
  },
  spin_attack: {
    label: 'Spin attack', category: 'Special', loop: false, icon: '🌀', sided: true,
    build(o) {
      const wind = { root: { r: R(0, 0, 0), p: R(0, -1.5, 0) }, body: { r: R(10, 45, 0) }, head: { r: R(-5, -20, 0) }, rArm: { r: R(-40, 0, 70) }, rFore: { r: R(-20, 0, 0) }, lArm: { r: R(-30, 0, -40) }, lFore: { r: R(-60, 0, 0) }, rLeg: { r: R(-20, 0, 10) }, rShin: { r: R(35, 0, 0) }, lLeg: { r: R(-20, 0, -10) }, lShin: { r: R(35, 0, 0) } };
      const half = { root: { r: R(0, -180, 0), p: R(0, 0.5, 0) }, body: { r: R(5, -10, 0) }, head: { r: R(0, 0, 0) }, rArm: { r: R(-80, 0, 85) }, rFore: { r: R(-5, 0, 0) }, lArm: { r: R(-80, 0, -85) }, lFore: { r: R(-5, 0, 0) }, rLeg: { r: R(-5, 0, 8) }, rShin: { r: R(10, 0, 0) }, lLeg: { r: R(-5, 0, -8) }, lShin: { r: R(10, 0, 0) } };
      const full = merge(half, { root: { r: R(0, -360, 0), p: R(0, -1, 0) }, body: { r: R(14, -30, 0) }, rArm: { r: R(-75, -10, 40) } });
      const reset = merge(full, { root: { r: R(0, 0, 0), p: R(0, -1, 0) } });
      const beats = [
        { d: 0, pose: merge(STANCES.ready, { root: { r: R(0, 0, 0) } }), ease: 'easeInOutSine' },
        { d: 0.25, pose: wind, ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.2, pose: half, ease: 'easeInSine', tag: 'strike' },
        { d: 0.2, pose: full, ease: 'easeOutSine', tag: 'follow' },
        { d: 0.05, pose: reset, ease: 'step', tag: 'hold' }, // -360° → 0° is the same orientation; step avoids spinning back
      ];
      if (o.recover !== false) beats.push({ d: 0.45, pose: merge(STANCES.ready, { root: { r: R(0, 0, 0) } }), ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o);
    },
  },
  block: {
    label: 'Block / guard', category: 'Defense', loop: false, icon: '🛡️',
    build(o) {
      const guard = { root: { p: R(0, -1.4, 0.4) }, body: { r: R(14, 0, 0) }, head: { r: R(14, 0, 0) }, rArm: { r: R(-80, 25, -20) }, rFore: { r: R(-105, 0, 0) }, lArm: { r: R(-80, -25, 20) }, lFore: { r: R(-105, 0, 0) }, rLeg: { r: R(12, 0, 8) }, rShin: { r: R(25, 0, 0) }, lLeg: { r: R(-16, 0, -8) }, lShin: { r: R(25, 0, 0) } };
      const brace = merge(guard, { root: { p: R(0, -1.6, 0.9) }, body: { r: R(10, 0, 0) } });
      return finish([
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.12, pose: guard, ease: 'easeOutBack', tag: 'strike' },
        { d: 0.08, pose: brace, ease: 'easeOutQuad', tag: 'follow' },
        { d: 0.4, pose: guard, ease: 'easeInOutSine', tag: 'hold' },
        ...(o.recover !== false ? [{ d: 0.3, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' }] : []),
      ], o);
    },
  },
  dodge: {
    label: 'Dodge', category: 'Defense', loop: false, icon: '💨', sided: true,
    build(o) {
      // side 'right' = dodge to the character's right. Use o.direction === 'back' for a backstep.
      if (o.direction === 'back') {
        const lean = { root: { p: R(0, -1, -0.5) }, body: { r: R(10, 0, 0) }, rLeg: { r: R(-10, 0, 0) }, lLeg: { r: R(-10, 0, 0) }, rShin: { r: R(25, 0, 0) }, lShin: { r: R(25, 0, 0) } };
        const back = { root: { p: R(0, -1.5, 6) }, body: { r: R(-16, 0, 0) }, head: { r: R(10, 0, 0) }, rArm: { r: R(-45, 0, 25) }, rFore: { r: R(-50, 0, 0) }, lArm: { r: R(-45, 0, -25) }, lFore: { r: R(-50, 0, 0) }, rLeg: { r: R(25, 0, 6) }, rShin: { r: R(35, 0, 0) }, lLeg: { r: R(-20, 0, -6) }, lShin: { r: R(20, 0, 0) } };
        return finish([
          { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
          { d: 0.07, pose: merge(STANCES.ready, lean), ease: 'easeOutSine', tag: 'anticipation' },
          { d: 0.16, pose: back, ease: 'easeOutCubic', tag: 'strike' },
          { d: 0.12, pose: merge(back, { root: { p: R(0, -1.8, 6.4) } }), ease: 'easeOutSine', tag: 'follow' },
          ...(o.recover !== false ? [{ d: 0.38, pose: merge(STANCES.ready, { root: { p: R(0, -0.6, 6) } }), ease: 'easeInOutSine', tag: 'recover' }] : []),
        ], { ...o, side: 'right' });
      }
      const load = { root: { p: R(-0.6, -1, 0) }, body: { r: R(8, 0, 6) }, lLeg: { r: R(-10, 0, -10) }, lShin: { r: R(30, 0, 0) } };
      const out = { root: { p: R(-6, -2, 0) }, body: { r: R(10, 0, -18) }, head: { r: R(0, 0, 10) }, rArm: { r: R(-30, 0, 45) }, rFore: { r: R(-60, 0, 0) }, lArm: { r: R(-50, 0, -15) }, lFore: { r: R(-90, 0, 0) }, rLeg: { r: R(-10, 0, 25) }, rShin: { r: R(40, 0, 0) }, lLeg: { r: R(-5, 0, 10) }, lShin: { r: R(10, 0, 0) } };
      return finish([
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.07, pose: merge(STANCES.ready, load), ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.15, pose: out, ease: 'easeOutCubic', tag: 'strike' },
        { d: 0.12, pose: merge(out, { root: { p: R(-6.5, -2.2, 0) } }), ease: 'easeOutSine', tag: 'follow' },
        ...(o.recover !== false ? [{ d: 0.38, pose: merge(STANCES.ready, { root: { p: R(-6, -0.6, 0) } }), ease: 'easeInOutSine', tag: 'recover' }] : []),
      ], o);
    },
  },
  hit: {
    label: 'Get hit', category: 'Reactions', loop: false, icon: '💥',
    build(o) {
      const snap = { root: { p: R(0, -0.5, 1.6) }, body: { r: R(-24, 0, 4) }, head: { r: R(-32, 8, 6) }, rArm: { r: R(-40, 0, 25) }, rFore: { r: R(-40, 0, 0) }, lArm: { r: R(-35, 0, -30) }, lFore: { r: R(-50, 0, 0) }, rLeg: { r: R(8, 0, 4) }, rShin: { r: R(10, 0, 0) }, lLeg: { r: R(-6, 0, -4) }, lShin: { r: R(8, 0, 0) } };
      const beats = [
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.06, pose: snap, ease: 'easeOutQuad', tag: 'strike' },
      ];
      if (o.heavy) {
        // stumble backward: two catching steps
        const step1 = { root: { p: R(0, -1, 3.5) }, body: { r: R(-14, -6, 0) }, head: { r: R(-12, 4, 0) }, rLeg: { r: R(30, 0, 6) }, rShin: { r: R(35, 0, 0) }, lLeg: { r: R(-15, 0, -6) }, lShin: { r: R(10, 0, 0) }, rArm: { r: R(-50, 0, 40) }, lArm: { r: R(-30, 0, -45) } };
        const step2 = { root: { p: R(0, -1.4, 6) }, body: { r: R(-6, 6, 0) }, head: { r: R(-6, -4, 0) }, rLeg: { r: R(-10, 0, 6) }, rShin: { r: R(20, 0, 0) }, lLeg: { r: R(30, 0, -6) }, lShin: { r: R(40, 0, 0) }, rArm: { r: R(-30, 0, 30) }, lArm: { r: R(-45, 0, -35) } };
        beats.push({ d: 0.22, pose: step1, ease: 'easeOutSine' });
        beats.push({ d: 0.22, pose: step2, ease: 'easeInOutSine' });
        if (o.recover !== false) beats.push({ d: 0.45, pose: merge(STANCES.ready, { root: { p: R(0, -0.6, 6) } }), ease: 'easeInOutSine', tag: 'recover' });
      } else {
        beats.push({ d: 0.18, pose: amplify(snap, STANCES.ready, 0.6), ease: 'easeOutSine', tag: 'follow' });
        if (o.recover !== false) beats.push({ d: 0.36, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' });
      }
      return finish(beats, o);
    },
  },
  death: {
    label: 'Death', category: 'Reactions', loop: false, hold: true, icon: '💀',
    build(o) {
      const fwd = o.direction === 'forward';
      const reel = { root: { r: R(0, 0, 0), p: R(0, 0, 1) }, body: { r: R(-18, 0, 5) }, head: { r: R(-25, 10, 5) }, rArm: { r: R(-30, 0, 30) }, rFore: { r: R(-30, 0, 0) }, lArm: { r: R(-25, 0, -30) }, lFore: { r: R(-35, 0, 0) }, rLeg: { r: R(6, 0, 2) }, rShin: { r: R(8, 0, 0) }, lLeg: { r: R(-4, 0, -2) }, lShin: { r: R(6, 0, 0) } };
      const buckle = { root: { r: R(0, 0, 0), p: R(0, -3.2, 1) }, body: { r: R(24, 0, -4) }, head: { r: R(25, 0, -6) }, rArm: { r: R(8, 0, 8) }, rFore: { r: R(-15, 0, 0) }, lArm: { r: R(10, 0, -8) }, lFore: { r: R(-10, 0, 0) }, rLeg: { r: R(-55, 0, 4) }, rShin: { r: R(95, 0, 0) }, lLeg: { r: R(-48, 0, -4) }, lShin: { r: R(88, 0, 0) } };
      const tip = fwd ? 88 : -88;
      const down = { root: { r: R(tip, 0, 0), p: R(0, 2, fwd ? -2 : 2) }, body: { r: R(fwd ? -5 : 5, 0, 0) }, head: { r: R(fwd ? -20 : 15, 25, 0) }, rArm: { r: R(fwd ? -150 : -20, 0, 35) }, rFore: { r: R(-20, 0, 0) }, lArm: { r: R(fwd ? -140 : -15, 0, -45) }, lFore: { r: R(-10, 0, 0) }, rLeg: { r: R(-5, 0, 6) }, rShin: { r: R(10, 0, 0) }, lLeg: { r: R(4, 0, -4) }, lShin: { r: R(15, 0, 0) } };
      const bounce = merge(down, { root: { r: R(tip * 0.94, 0, 0), p: R(0, 2.4, fwd ? -2 : 2) } });
      return finish([
        { d: 0, pose: merge(STANCES.ready, { root: { r: R(0, 0, 0) } }), ease: 'easeInOutSine' },
        { d: 0.12, pose: reel, ease: 'easeOutQuad', tag: 'strike' },
        { d: 0.38, pose: buckle, ease: 'easeInOutSine' },
        { d: 0.36, pose: down, ease: 'easeInQuad', tag: 'contact' },
        { d: 0.1, pose: bounce, ease: 'easeOutQuad', tag: 'follow' },
        { d: 0.14, pose: down, ease: 'easeInQuad', tag: 'hold' },
      ], o);
    },
  },
  charge: {
    label: 'Charge energy', category: 'Special', loop: false, icon: '⚡',
    build(o) {
      const gather = { root: { p: R(0, -2.2, 0) }, body: { r: R(16, 0, 0) }, head: { r: R(12, 0, 0) }, rArm: { r: R(30, 0, 22) }, rFore: { r: R(-105, 0, 0) }, lArm: { r: R(30, 0, -22) }, lFore: { r: R(-105, 0, 0) }, rLeg: { r: R(-12, 0, 16) }, rShin: { r: R(30, 0, 0) }, lLeg: { r: R(-12, 0, -16) }, lShin: { r: R(30, 0, 0) } };
      const shakeA = merge(gather, { body: { r: R(18, 0, 2) }, root: { p: R(0.25, -2.4, 0) }, head: { r: R(10, 0, -2) } });
      const shakeB = merge(gather, { body: { r: R(18, 0, -2) }, root: { p: R(-0.25, -2.4, 0) }, head: { r: R(10, 0, 2) } });
      const n = Math.max(2, Math.round(4 * (o.duration ?? 1)));
      const beats = [
        { d: 0, pose: STANCES.ready, ease: 'easeInOutSine' },
        { d: 0.4, pose: gather, ease: 'easeOutSine', tag: 'anticipation' },
      ];
      for (let i = 0; i < n; i++) beats.push({ d: 0.1, pose: i % 2 ? shakeB : shakeA, ease: 'easeInOutSine', tag: 'hold' });
      return finish(beats, o);
    },
  },
  release: {
    label: 'Energy blast', category: 'Special', loop: false, icon: '🔥',
    build(o) {
      const push = { root: { p: R(0, -1.2, 1.8) }, body: { r: R(-6, 0, 0) }, head: { r: R(-4, 0, 0) }, rArm: { r: R(-90, 10, -8) }, rFore: { r: R(-2, 0, 0) }, lArm: { r: R(-90, -10, 8) }, lFore: { r: R(-2, 0, 0) }, rLeg: { r: R(25, 0, 8) }, rShin: { r: R(25, 0, 0) }, lLeg: { r: R(-25, 0, -8) }, lShin: { r: R(25, 0, 0) } };
      const recoilA = merge(push, { root: { p: R(0, -1.3, 2.3) }, body: { r: R(-9, 0, 0) } });
      const beats = [
        { d: 0, pose: merge(STANCES.ready, { rArm: { r: R(30, 0, 22) }, rFore: { r: R(-105, 0, 0) }, lArm: { r: R(30, 0, -22) }, lFore: { r: R(-105, 0, 0) } }), ease: 'easeInOutSine' },
        { d: 0.09, pose: push, ease: 'easeOutBack', tag: 'strike' },
        { d: 0.1, pose: recoilA, ease: 'easeOutQuad', tag: 'follow' },
        { d: 0.1, pose: push, ease: 'easeInOutSine', tag: 'hold' },
        { d: 0.1, pose: recoilA, ease: 'easeInOutSine', tag: 'hold' },
      ];
      if (o.recover !== false) beats.push({ d: 0.5, pose: STANCES.ready, ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o);
    },
  },
  turn: {
    label: 'Turn around', category: 'Movement', loop: false, icon: '↩️',
    build(o) {
      const deg = o.angle ?? 180;
      const look = { root: { r: R(0, 0, 0), p: R(0, 0, 0) }, head: { r: R(0, deg > 0 ? 45 : -45, 0) }, body: { r: R(0, deg * 0.08, 0) } };
      const mid = { root: { r: R(0, deg * 0.55, 0), p: R(0, -0.6, 0) }, head: { r: R(0, deg > 0 ? 25 : -25, 0) }, body: { r: R(0, deg * 0.06, 0) }, rLeg: { r: R(-15, 0, 0) }, rShin: { r: R(25, 0, 0) }, lLeg: { r: R(10, 0, 0) } };
      const done = { root: { r: R(0, deg, 0), p: R(0, 0, 0) }, head: { r: R(0, 0, 0) }, body: { r: R(0, 0, 0) }, rLeg: { r: R(0, 0, 0) }, rShin: { r: R(0, 0, 0) }, lLeg: { r: R(0, 0, 0) } };
      return finish([
        { d: 0, pose: merge(STANCES.neutral, { root: { r: R(0, 0, 0) } }), ease: 'easeInOutSine' },
        { d: 0.18, pose: look, ease: 'easeOutSine', tag: 'anticipation' },
        { d: 0.25, pose: mid, ease: 'easeInSine' },
        { d: 0.28, pose: done, ease: 'easeOutSine', tag: 'follow' },
      ], { ...o, side: 'right' }, STANCES.neutral);
    },
  },
  wave: {
    label: 'Wave hello', category: 'Emotes', loop: false, icon: '👋', sided: true,
    build(o) {
      const up = { rArm: { r: R(-10, 0, 150) }, rFore: { r: R(0, 0, 0) }, head: { r: R(0, 0, -6) }, body: { r: R(0, 0, -4) } };
      const a = merge(up, { rFore: { r: R(0, 0, 20) }, rArm: { r: R(-10, 0, 140) } });
      const b = merge(up, { rFore: { r: R(0, 0, -20) }, rArm: { r: R(-10, 0, 160) } });
      const beats = [{ d: 0, pose: STANCES.neutral, ease: 'easeInOutSine' }, { d: 0.25, pose: up, ease: 'easeOutBack', tag: 'anticipation' }];
      for (let i = 0; i < 4; i++) beats.push({ d: 0.16, pose: i % 2 ? b : a, ease: 'easeInOutSine' });
      if (o.recover !== false) beats.push({ d: 0.35, pose: STANCES.neutral, ease: 'easeInOutSine', tag: 'recover' });
      return finish(beats, o, STANCES.neutral);
    },
  },
  look_around: {
    label: 'Look around', category: 'Emotes', loop: false, icon: '👀',
    build(o) {
      return finish([
        { d: 0, pose: STANCES.neutral, ease: 'easeInOutSine' },
        { d: 0.4, pose: { head: { r: R(-5, 55, 0) }, body: { r: R(0, 10, 0) } }, ease: 'easeInOutSine' },
        { d: 0.5, pose: { head: { r: R(-5, 55, 0) }, body: { r: R(0, 10, 0) } }, ease: 'linear', tag: 'hold' },
        { d: 0.55, pose: { head: { r: R(-5, -55, 0) }, body: { r: R(0, -10, 0) } }, ease: 'easeInOutSine' },
        { d: 0.5, pose: { head: { r: R(-5, -55, 0) }, body: { r: R(0, -10, 0) } }, ease: 'linear', tag: 'hold' },
        { d: 0.4, pose: { head: { r: R(0, 0, 0) }, body: { r: R(0, 0, 0) } }, ease: 'easeInOutSine', tag: 'recover' },
      ], o, STANCES.neutral);
    },
  },
  taunt: {
    label: 'Taunt', category: 'Emotes', loop: false, icon: '😤',
    build(o) {
      const puff = { body: { r: R(-12, 0, 0) }, head: { r: R(-15, 0, 0) }, rArm: { r: R(-10, 0, 35) }, rFore: { r: R(-120, 0, 0) }, lArm: { r: R(-10, 0, -35) }, lFore: { r: R(-120, 0, 0) }, root: { p: R(0, 0.5, 0) } };
      const beckon = merge(puff, { rArm: { r: R(-80, 0, 10) }, rFore: { r: R(-60, 0, 0) } });
      const beckon2 = merge(puff, { rArm: { r: R(-80, 0, 10) }, rFore: { r: R(-110, 0, 0) } });
      return finish([
        { d: 0, pose: STANCES.neutral, ease: 'easeInOutSine' },
        { d: 0.3, pose: puff, ease: 'easeOutBack', tag: 'anticipation' },
        { d: 0.2, pose: beckon, ease: 'easeOutSine' },
        { d: 0.15, pose: beckon2, ease: 'easeInOutSine' },
        { d: 0.15, pose: beckon, ease: 'easeInOutSine' },
        { d: 0.15, pose: beckon2, ease: 'easeInOutSine' },
        ...(o.recover !== false ? [{ d: 0.4, pose: STANCES.neutral, ease: 'easeInOutSine', tag: 'recover' }] : []),
      ], o, STANCES.neutral);
    },
  },
  crouch: {
    label: 'Crouch', category: 'Movement', loop: false, icon: '🧎',
    build(o) {
      const c = { root: { p: R(0, -3.5, 0) }, body: { r: R(28, 0, 0) }, head: { r: R(-24, 0, 0) }, rLeg: { r: R(-45, 0, 6) }, rShin: { r: R(80, 0, 0) }, lLeg: { r: R(-45, 0, -6) }, lShin: { r: R(80, 0, 0) }, rArm: { r: R(-20, 0, 5) }, rFore: { r: R(-30, 0, 0) }, lArm: { r: R(-20, 0, -5) }, lFore: { r: R(-30, 0, 0) } };
      return finish([{ d: 0, pose: STANCES.neutral, ease: 'easeInOutSine' }, { d: 0.25, pose: c, ease: 'easeOutBack' }, { d: 0.4, pose: c, ease: 'linear', tag: 'hold' }], o, STANCES.neutral);
    },
  },
};

export const BLOCK_LIST = Object.entries(BLOCKS).map(([id, b]) => ({ id, ...b }));

// Starter presets shown on the Create tab (beginner-facing names → block + options).
export const PRESETS = [
  { id: 'idle', label: 'Idle', block: 'idle', opts: {} },
  { id: 'walk', label: 'Walk', block: 'walk', opts: {} },
  { id: 'run', label: 'Run', block: 'run', opts: {} },
  { id: 'jump', label: 'Jump', block: 'jump', opts: {} },
  { id: 'fall', label: 'Fall', block: 'fall', opts: {} },
  { id: 'land', label: 'Land', block: 'land', opts: {} },
  { id: 'punch', label: 'Punch', block: 'punch', opts: { side: 'right' } },
  { id: 'kick', label: 'Kick', block: 'kick', opts: { side: 'right' } },
  { id: 'sword_slash', label: 'Sword swing', block: 'sword_slash', opts: { side: 'right' } },
  { id: 'sword_overhead', label: 'Overhead strike', block: 'sword_overhead', opts: { side: 'right' } },
  { id: 'block', label: 'Block', block: 'block', opts: {} },
  { id: 'dodge', label: 'Dodge', block: 'dodge', opts: { side: 'right' } },
  { id: 'hit', label: 'Get hit', block: 'hit', opts: {} },
  { id: 'death', label: 'Death', block: 'death', opts: {} },
  { id: 'charge', label: 'Charge attack', seq: [{ block: 'charge', opts: {} }, { block: 'release', opts: {} }] },
  { id: 'special', label: 'Spin attack', block: 'spin_attack', opts: { side: 'right' } },
  { id: 'combo', label: '3-hit combo', seq: [{ block: 'punch', opts: { side: 'left', recover: false, speed: 1.15 } }, { block: 'punch', opts: { side: 'right', recover: false } }, { block: 'kick', opts: { side: 'right' } }] },
];
