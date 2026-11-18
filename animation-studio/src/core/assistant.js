// The animation assistant: turns plain English into a plan of motion blocks (which then compile
// into ordinary keyframes), or into adjustment commands for the current animation.
//
// It runs fully offline with a rule-based language parser, so it works on any phone with no
// account or API key. The `interpret` function is the single extension point: an LLM-backed
// interpreter can be plugged in later as long as it returns the same plan shape.
import { BLOCKS } from './blocks.js';
import { ADJUSTMENTS } from './adjust.js';

const NUM = { one: 1, single: 1, two: 2, double: 2, three: 3, triple: 3, four: 4, five: 5, six: 6 };

// Ordered: more specific patterns first.
const INTENTS = [
  { re: /\b(over ?head|overhead|downward|down(ward)? (slash|strike|chop)|chop|cleave|smash down|vertical slash)\b/, block: 'sword_overhead' },
  { re: /\b(stab|thrust|lunge|pierce|poke)\b/, block: 'sword_stab' },
  { re: /\b(spin(ning)? (attack|slash)|whirlwind|spin|360|tornado)\b/, block: 'spin_attack' },
  { re: /\b(sword|blade|katana|slash|swing|slice|cut|axe|weapon)\b/, block: 'sword_slash' },
  { re: /\b(uppercut)\b/, block: 'uppercut' },
  { re: /\b(kick|roundhouse|stomp)\b/, block: 'kick' },
  { re: /\b(charge|charging|channel|gather|power(ing)? up|build(s|ing)? (up )?energy|focus energy)\b/, block: 'charge' },
  { re: /\b(releas(e|es|ing)|blast|fire|beam|shoot(s|ing)?|unleash(es|ing)?|throw energy|energy ball|fireball|wave of energy)\b/, block: 'release' },
  { re: /\b(punch|jab|hook|cross|strike|hit him|hit them|attack)\b/, block: 'punch' },
  { re: /\b(block|guard|parry|shield|defend|brace)\b/, block: 'block' },
  { re: /\b(dodge|evade|sidestep|side step|backstep|back step|duck|roll|dash (left|right|back))\b/, block: 'dodge' },
  { re: /\b(stumble|stagger|knock(ed)? back|reel|get(s|ting)? hit|gets? struck|flinch|take(s)? a hit|hurt|damaged|recoil from)\b/, block: 'hit' },
  { re: /\b(die|dies|dying|death|collapse|faint|killed|falls? dead|perish)\b/, block: 'death' },
  { re: /\b(land|landing|lands|touch(es)? down)\b/, block: 'land' },
  { re: /\b(jump|leap|hop|spring up)\b/, block: 'jump' },
  { re: /\b(falling|fall|plummet|drop(ping)?)\b/, block: 'fall' },
  { re: /\b(turn(s)? around|turn back|about[- ]face|spin around|turns? away|turn (left|right))\b/, block: 'turn' },
  { re: /\b(sprint|run|running|dash|jog)\b/, block: 'run' },
  { re: /\b(walk|walking|walks|stroll|step(s)? away|walk away|wander|march)\b/, block: 'walk' },
  { re: /\b(wave|waving|greet|hello|hi)\b/, block: 'wave' },
  { re: /\b(look(s)? around|search|scan|glance)\b/, block: 'look_around' },
  { re: /\b(taunt|mock|provoke|come on|beckon)\b/, block: 'taunt' },
  { re: /\b(crouch|sneak|kneel|squat)\b/, block: 'crouch' },
  { re: /\b(idle|breath|breathing|stand(ing)? still|wait(ing)?)\b/, block: 'idle' },
  { re: /\b(stance|ready|fighting pose|en garde)\b/, block: 'ready' },
];

function modifiersOf(text) {
  const m = { speed: 1, power: 1, exaggeration: 1, recovery: 1, anticipation: 1 };
  const t = text;
  if (/\b(very|super|extremely|really) (fast|quick)|lightning|blindingly\b/.test(t)) m.speed *= 1.6;
  else if (/\b(fast|quick(ly)?|rapid|swift|snappy|brisk)\b/.test(t)) m.speed *= 1.3;
  if (/\b(very|really|super) slow(ly)?\b/.test(t)) m.speed *= 0.55;
  else if (/\b(slow(ly)?|leisurely|sluggish|tired(ly)?|lazy)\b/.test(t)) m.speed *= 0.75;
  if (/\b(powerful|strong|mighty|heavy|devastating|crushing|massive|brutal|hard|forceful)\b/.test(t)) { m.power *= 1.35; m.anticipation *= 1.25; m.recovery *= 1.15; }
  if (/\b(light|weak|gentle|soft|tap|quick jab)\b/.test(t)) { m.power *= 0.75; m.exaggeration *= 0.85; }
  if (/\b(dramatic|exaggerated|cartoony|cartoon|epic|cinematic|anime|over the top|big)\b/.test(t)) m.exaggeration *= 1.3;
  if (/\b(subtle|small|realistic|restrained|minimal|tiny)\b/.test(t)) m.exaggeration *= 0.75;
  if (/\b(slow recovery|recover(s)? slowly|exhausted)\b/.test(t)) m.recovery *= 1.5;
  return m;
}

const sideOf = (s) => (/\bleft\b/.test(s) ? 'left' : /\bright\b/.test(s) ? 'right' : null);

function splitClauses(text) {
  // "charge energy before releasing" → [charge, release]
  let t = text.replace(/\b(\w+(?:\s+\w+){0,4})\s+before\s+(\w+(?:\s+\w+){0,5})/g, '$1 then $2');
  t = t.replace(/\bafter\s+([^,]+?),\s*/g, '$1 then ');
  return t.split(/\s*(?:,|;|\bthen\b|\band then\b|\bfollowed by\b|\bnext\b|\bafterwards?\b|\band finally\b|\bfinally\b|\.\s)\s*/).filter((c) => c.trim());
}

function detectBlocks(clause) {
  const found = [];
  let rest = ` ${clause} `;
  // Allow "and" to chain multiple actions in one clause ("turn around and walk away")
  for (const part of rest.split(/\band\b/)) {
    for (const intent of INTENTS) {
      if (intent.re.test(part)) { found.push({ block: intent.block, text: part.trim() }); break; }
    }
  }
  return found;
}

function comboSteps(text, count, mods) {
  const weapon = /\b(sword|blade|slash|katana|axe)\b/.test(text);
  const kicks = /\bkick/.test(text);
  const steps = [];
  for (let i = 0; i < count; i++) {
    const last = i === count - 1;
    const side = i % 2 === 0 ? 'right' : 'left';
    let block;
    if (weapon) block = last ? 'sword_overhead' : i % 2 === 0 ? 'sword_slash' : 'sword_stab';
    else if (last) block = kicks ? 'kick' : 'uppercut'; // finisher
    else block = kicks && i % 2 === 1 ? 'kick' : 'punch';
    steps.push({
      block,
      opts: { side: weapon ? 'right' : (block === 'punch' ? (i % 2 === 0 ? 'left' : 'right') : side), recover: last, speed: mods.speed * (last ? 1 : 1.15), power: mods.power * (last ? 1.2 : 0.9), exaggeration: mods.exaggeration },
      label: `${BLOCKS[block].label} ${i + 1}`,
    });
  }
  return steps;
}

// Recognise adjustment commands for the current animation.
const ADJUST_PATTERNS = [
  { re: /\b(faster|speed (it )?up|quicker|more quickly)\b/, id: 'faster' },
  { re: /\b(slower|slow (it )?down|less fast)\b(?!.*recover)/, id: 'slower' },
  { re: /\b(more|add|extra|stronger) (power|force|impact|punch)|\bmore powerful\b|\bstronger\b|hit harder/, id: 'power' },
  { re: /\bless (exaggerated|dramatic|extreme|big)|tone (it )?down|more subtle|more realistic|smaller\b/, id: 'less' },
  { re: /\bmore (exaggerated|dramatic|extreme|cartoony|cinematic)|bigger|exaggerate\b/, id: 'more' },
  { re: /\b(more|add|give( it)?( the attack)?( more)?) weight|heavier\b/, id: 'weight' },
  { re: /\b(recovery|recover)\b.*\bslow|\bslow(er)?\b.*\b(recovery|recover)\b/, id: 'recovery' },
  { re: /\bsmoother|less robotic|more natural|less stiff|softer\b/, id: 'smoother' },
  { re: /\bsnappier|sharper|crisper|more snap\b/, id: 'snappier' },
];

export function parseAdjustments(text) {
  const t = text.toLowerCase();
  const ids = [];
  for (const p of ADJUST_PATTERNS) if (p.re.test(t) && !ids.includes(p.id)) ids.push(p.id);
  // "make it faster" + "slower recovery" shouldn't trigger both faster/slower by accident
  if (ids.includes('recovery')) { const i = ids.indexOf('slower'); if (i >= 0) ids.splice(i, 1); }
  return ids;
}

// Main entry: returns either { kind: 'adjust', adjustments, explanation }
// or { kind: 'create', steps, name, explanation } or { kind: 'unknown', explanation }
export function interpret(input, { hasAnimation = false } = {}) {
  const text = ` ${String(input).toLowerCase().replace(/[!?]/g, '.').replace(/\s+/g, ' ').trim()} `;
  const creating = /\b(make|create|have|let|animate|generate|new|do|perform|performs|show)\b.*\b(character|him|her|them|it|mob|player)\b/.test(text) || detectBlocks(text).length > 0 || /\bcombo\b/.test(text);
  const adj = parseAdjustments(text);
  const blocksAnywhere = INTENTS.some((i) => i.re.test(text));
  if (hasAnimation && adj.length && (!blocksAnywhere || /^\s*(make|give|add)\s+(it|the|this)\b/.test(text))) {
    return {
      kind: 'adjust', adjustments: adj,
      explanation: `I'll ${adj.map((a) => ADJUSTMENTS[a].label.toLowerCase()).join(', then ')} on the current animation.`,
    };
  }
  if (!creating) return { kind: 'unknown', explanation: "I didn't recognise a movement. Try something like “a powerful overhead sword strike”, “stumble backward after getting hit” or “a fast three-hit combo”." };

  const mods = modifiersOf(text);
  // combos
  const combo = text.match(/\b(\d|one|two|three|four|five|six|double|triple)[- ]?(hit|strike|attack|punch|slash)?[- ]?combo\b/) || text.match(/\bcombo of (\d|two|three|four|five)\b/);
  if (combo || /\bcombo\b/.test(text)) {
    const raw = combo ? combo[1] : 'three';
    const count = Math.min(6, Math.max(2, Number(raw) || NUM[raw] || 3));
    const steps = comboSteps(text, count, mods);
    return { kind: 'create', steps, name: `combo_${count}_hit`, explanation: explain(steps, mods, `a ${count}-hit combo`) };
  }

  const steps = [];
  const parts = splitClauses(text).flatMap((c) => detectBlocks(c));
  for (const { block, text: part } of parts) {
    {
      // one action: the whole sentence describes it; several: each part has its own adverbs
      const cm = parts.length === 1 ? mods : modifiersOf(part);
      const opts = { speed: cm.speed, power: cm.power, exaggeration: cm.exaggeration, recovery: cm.recovery, anticipation: cm.anticipation };
      const side = sideOf(part);
      if (BLOCKS[block].sided) opts.side = side || 'right';
      if (block === 'dodge') { if (/\b(back|backward|backwards|backstep|back step)\b/.test(part)) opts.direction = 'back'; opts.side = side || 'right'; }
      if (block === 'hit' && /\b(stumble|stagger|knock(ed)? back|backward|reel|heavy|hard|powerful)\b/.test(part + text)) opts.heavy = true;
      if (block === 'death' && /\b(forward|face ?first|on (his|her|their) face)\b/.test(part)) opts.direction = 'forward';
      if (block === 'turn') { opts.angle = /\bleft\b/.test(part) ? -90 : /\bright\b/.test(part) ? 90 : 180; }
      if (block === 'charge' && /\b(long|slowly|while)\b/.test(part)) opts.duration = 1.8;
      // "walk away" after turning: walk stays a loop cycle, but don't loop the whole clip
      steps.push({ block, opts, label: BLOCKS[block].label });
    }
  }
  // Charging should flow into a release if the text implies an attack after it
  if (steps.length === 1 && steps[0].block === 'charge' && /\b(attack|release|unleash|strike)\b/.test(text)) steps.push({ block: 'release', opts: { ...steps[0].opts }, label: BLOCKS.release.label });
  // A walk/run after a turn plays a couple of cycles
  for (let i = 1; i < steps.length; i++) if (['walk', 'run'].includes(steps[i].block) && steps[i - 1].block === 'turn') {
    steps.splice(i + 1, 0, { ...steps[i], label: `${steps[i].label} (cont.)` });
  }
  if (!steps.length) return { kind: 'unknown', explanation: "I couldn't work out the movement. Try naming an action: punch, kick, sword slash, overhead strike, block, dodge, get hit, death, jump, charge, walk, run…" };
  const name = steps.map((s) => s.block).filter((b, i, a) => a.indexOf(b) === i).slice(0, 3).join('_');
  return { kind: 'create', steps, name, explanation: explain(steps, mods) };
}

function explain(steps, mods, what) {
  const feel = [];
  if (mods.speed > 1.1) feel.push('fast'); if (mods.speed < 0.9) feel.push('slow');
  if (mods.power > 1.1) feel.push('powerful'); if (mods.power < 0.9) feel.push('light');
  if (mods.exaggeration > 1.1) feel.push('exaggerated'); if (mods.exaggeration < 0.9) feel.push('subtle');
  const head = `Here's ${what || 'the plan'}${feel.length ? ` (${feel.join(', ')})` : ''}:`;
  return head;
}

// What each block automatically adds – shown to users so they learn the principles.
export const PRINCIPLE_NOTES = {
  punch: 'Torso twists back first (anticipation), hips drive the hit, the other hand pulls back for balance, then a short follow-through.',
  uppercut: 'Dips the knees first so the power comes up from the legs.',
  kick: 'Knee chambers before extending; the torso leans back and the arms swing out to keep balance.',
  sword_slash: 'Big wind-up to the side, a very fast eased slash, then follow-through across the body.',
  sword_overhead: 'Raises the weapon with a brief hang at the top, accelerates down, and the body drops its weight into the impact.',
  sword_stab: 'Pulls the arm back, then lunges with the back leg pushing the body forward.',
  spin_attack: 'Coils the torso before spinning; arms extend during the spin for a wide arc.',
  block: 'Arms snap up with a slight overshoot and the knees bend to absorb impact.',
  dodge: 'A tiny weight shift the opposite way first, then a fast lean and step.',
  hit: 'Head whips back a moment after the body; heavy hits add catching steps.',
  death: 'Reels, the knees buckle, then the body topples with a small bounce on the ground.',
  charge: 'Crouches and pulls the arms in, then trembles while holding the energy.',
  release: 'Arms thrust forward with recoil pushing the body back.',
  jump: 'Crouch (anticipation), explosive launch, tuck at the apex, then a cushioned landing.',
  land: 'Knees and torso absorb the impact, then the character recovers upright.',
  walk: 'Opposite arm and leg swing, the body bobs up on passing poses and the torso twists slightly.',
  run: 'Leans forward with bigger arm pumps, a flight phase and deep knee bends.',
  idle: 'Subtle breathing: chest rises, arms sway, head drifts slightly.',
  fall: 'Arms flail overhead and legs cycle to sell the fall.',
  turn: 'The head leads the turn, then the body follows.',
  wave: 'Arm raises with an overshoot, then the forearm waves side to side.',
  look_around: 'The head leads and the torso follows a little.',
  taunt: 'Chest puffs out, then a beckoning hand.',
  crouch: 'Bends knees and leans forward with a small settle.',
  ready: 'Bent knees, guard up, turned slightly sideways.',
};
