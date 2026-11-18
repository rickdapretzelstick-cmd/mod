// Maps arbitrary bone names to humanoid "roles" so presets, the assistant and the library can
// drive any Minecraft-style biped (GeckoLib snake_case, Bedrock camelCase, bipedRightArm, arm_r...).
// Users can override any role by hand; overrides live in model.roles.

export const ROLES = [
  { id: 'root', label: 'Whole body (root)', parent: null },
  { id: 'hips', label: 'Hips / waist', parent: 'root' },
  { id: 'body', label: 'Torso', parent: 'hips' },
  { id: 'head', label: 'Head', parent: 'body' },
  { id: 'rArm', label: 'Right upper arm', parent: 'body' },
  { id: 'rFore', label: 'Right forearm', parent: 'rArm' },
  { id: 'rItem', label: 'Right hand item', parent: 'rFore' },
  { id: 'lArm', label: 'Left upper arm', parent: 'body' },
  { id: 'lFore', label: 'Left forearm', parent: 'lArm' },
  { id: 'lItem', label: 'Left hand item', parent: 'lFore' },
  { id: 'rLeg', label: 'Right thigh / leg', parent: 'hips' },
  { id: 'rShin', label: 'Right shin', parent: 'rLeg' },
  { id: 'lLeg', label: 'Left thigh / leg', parent: 'hips' },
  { id: 'lShin', label: 'Left shin', parent: 'lLeg' },
];
export const ROLE_IDS = ROLES.map((r) => r.id);

// Plausible joint ranges in GeckoLib degrees: [[minX,maxX],[minY,maxY],[minZ,maxZ]].
// Signs: limbs X<0 swings forward; shins X>0 bends knee; forearms X<0 bends elbow;
// right arm Z>0 raises outward (left arm mirrored); body/head X>0 leans/looks down; Y>0 turns right.
export const JOINT_LIMITS = {
  head: [[-70, 80], [-85, 85], [-40, 40]],
  body: [[-45, 90], [-70, 70], [-45, 45]],
  hips: [[-45, 90], [-70, 70], [-45, 45]],
  rArm: [[-200, 70], [-90, 90], [-30, 180]],
  lArm: [[-200, 70], [-90, 90], [-180, 30]],
  rFore: [[-155, 5], [-60, 60], [-30, 30]],
  lFore: [[-155, 5], [-60, 60], [-30, 30]],
  rLeg: [[-120, 60], [-45, 45], [-10, 70]],
  lLeg: [[-120, 60], [-45, 45], [-70, 10]],
  rShin: [[-5, 150], [-15, 15], [-10, 10]],
  lShin: [[-5, 150], [-15, 15], [-10, 10]],
};

const tokens = (name) => String(name)
  .replace(/([a-z])([A-Z])/g, '$1 $2')
  .toLowerCase()
  .split(/[^a-z0-9]+|(?<=[a-z])(?=[0-9])/)
  .filter(Boolean);

function sideOf(tk, raw) {
  if (tk.includes('right') || tk.includes('r') || /(^|[_.\s])r($|[_.\s])/i.test(raw)) return 'r';
  if (tk.includes('left') || tk.includes('l') || /(^|[_.\s])l($|[_.\s])/i.test(raw)) return 'l';
  return null;
}

function classify(name) {
  const tk = tokens(name);
  const has = (...w) => w.some((x) => tk.includes(x));
  const joined = tk.join('');
  const side = sideOf(tk, name);
  if (has('root', 'main', 'everything', 'all', 'master', 'entity', 'model') && !has('arm', 'leg', 'head')) return { part: 'root' };
  if (has('head', 'skull', 'neck') && !has('band')) return { part: 'head' };
  if (has('hips', 'hip', 'pelvis', 'waist', 'lowerbody') || joined.includes('lowerbody')) return { part: 'hips' };
  if (has('body', 'torso', 'chest', 'spine', 'upperbody', 'abdomen', 'belly') || joined.includes('upperbody')) return { part: 'body' };
  const lower = has('forearm', 'lower', 'elbow', 'hand', 'fore', 'bottom', 'low', '2') || joined.includes('forearm') || joined.includes('lowerarm');
  if (has('item', 'weapon', 'sword', 'held', 'tool', 'holding')) return { part: 'item', side: side || 'r' };
  if (has('arm', 'shoulder', 'arms') || joined.includes('arm')) return { part: lower ? 'fore' : 'arm', side };
  const lowerLeg = has('shin', 'calf', 'knee', 'foot', 'lowerleg', 'lower', 'bottom', 'low', '2', 'feet') || joined.includes('lowerleg');
  if (has('leg', 'thigh', 'legs') || joined.includes('leg') || has('shin', 'calf', 'foot')) return { part: lowerLeg ? 'shin' : 'leg', side };
  return null;
}

// Returns { role: boneName }.
export function detectRoles(model) {
  const found = {};
  const claim = (role, bone) => { if (!found[role]) found[role] = bone; };
  for (const b of model.bones) {
    const c = classify(b.name);
    if (!c) continue;
    const S = c.side === 'l' ? 'l' : c.side === 'r' ? 'r' : null;
    switch (c.part) {
      case 'root': if (!b.parent || !found.root) claim('root', b.name); break;
      case 'head': claim('head', b.name); break;
      case 'hips': claim('hips', b.name); break;
      case 'body': claim('body', b.name); break;
      case 'arm': if (S) claim(`${S}Arm`, b.name); break;
      case 'fore': if (S) claim(`${S}Fore`, b.name); break;
      case 'item': claim(`${S || 'r'}Item`, b.name); break;
      case 'leg': if (S) claim(`${S}Leg`, b.name); break;
      case 'shin': if (S) claim(`${S}Shin`, b.name); break;
    }
  }
  // Side fallback from geometry: arms/legs without side words → use pivot X (Blockbench +X = right).
  for (const b of model.bones) {
    const c = classify(b.name);
    if (!c || c.side || !['arm', 'leg', 'fore', 'shin'].includes(c.part)) continue;
    const S = b.pivot[0] >= 0 ? 'r' : 'l';
    const role = `${S}${c.part[0].toUpperCase()}${c.part.slice(1)}`;
    claim(role, b.name);
  }
  // A lower limb named "arm" child of the upper arm: child of rArm containing cubes → rFore
  for (const S of ['r', 'l']) {
    if (found[`${S}Arm`] && !found[`${S}Fore`]) {
      const kid = model.bones.find((b) => b.parent === found[`${S}Arm`] && b.cubes.length && !Object.values(found).includes(b.name) && !/item|sword|weapon/i.test(b.name));
      if (kid) found[`${S}Fore`] = kid.name;
    }
    if (found[`${S}Leg`] && !found[`${S}Shin`]) {
      const kid = model.bones.find((b) => b.parent === found[`${S}Leg`] && b.cubes.length && !Object.values(found).includes(b.name));
      if (kid) found[`${S}Shin`] = kid.name;
    }
  }
  return found;
}

// Effective role map: auto-detected + user overrides (model.roles), and fallbacks for missing
// structural roles so whole-body motion still lands somewhere sensible.
export function resolveRig(model) {
  const auto = detectRoles(model);
  const map = { ...auto, ...(model.roles || {}) };
  for (const k of Object.keys(map)) if (!map[k] || !model.bones.find((b) => b.name === map[k])) delete map[k];
  const fallback = {};
  if (!map.root) {
    const tops = model.bones.filter((b) => !b.parent);
    if (tops.length === 1) fallback.root = tops[0].name;
  }
  const rig = { map, fallback, model };
  rig.bone = (role) => map[role] || fallback[role] || null;
  rig.roleOf = (bone) => Object.keys(map).find((r) => map[r] === bone) || Object.keys(fallback).find((r) => fallback[r] === bone) || null;
  rig.score = ['head', 'body', 'rArm', 'lArm', 'rLeg', 'lLeg'].filter((r) => map[r]).length / 6;
  return rig;
}

export function limitFor(role) { return JOINT_LIMITS[role] || null; }

export function clampToLimits(role, v) {
  const L = JOINT_LIMITS[role];
  if (!L) return { v, clamped: false };
  let clamped = false;
  const out = v.map((n, i) => {
    const c = Math.min(L[i][1], Math.max(L[i][0], n));
    if (Math.abs(c - n) > 1e-6) clamped = true;
    return c;
  });
  return { v: out, clamped };
}
