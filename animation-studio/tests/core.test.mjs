// Core tests (no browser needed): node tests/core.test.mjs
import assert from 'node:assert/strict';
import { createSampleModel } from '../src/core/sampleModel.js';
import { resolveRig, detectRoles } from '../src/core/rig.js';
import { compileSequence, regenerateSection } from '../src/core/sequencer.js';
import { BLOCKS, PRESETS } from '../src/core/blocks.js';
import { sampleAnimation, setKey, createAnimation, sampleChannel, scaleTimeRange, keyTimes, moveKeysAt, copyPose, pastePose } from '../src/core/animation.js';
import { exportAnimationFile, validateAnimationFile, parseAnimationFile, validateGeo } from '../src/formats/geckolib.js';
import { exportGeo, parseGeo } from '../src/formats/geo.js';
import { exportBBModel, parseBBModel } from '../src/formats/bbmodel.js';
import { interpret } from '../src/core/assistant.js';
import { ADJUSTMENTS } from '../src/core/adjust.js';
import { polish } from '../src/core/assist.js';
import { ease, EASINGS } from '../src/core/easing.js';
import { buildPackage, TARGETS } from '../src/export/package.js';
import { readZip } from '../src/export/zip.js';

let passed = 0;
const test = (name, fn) => {
  try { fn(); passed++; console.log('  ✓', name); } catch (e) { console.error('  ✗', name, '\n', e); process.exitCode = 1; }
};

const model = createSampleModel();
const rig = resolveRig(model);

test('role detection on the sample rig', () => {
  const r = detectRoles(model);
  assert.equal(r.head, 'head'); assert.equal(r.rArm, 'right_arm'); assert.equal(r.rFore, 'right_forearm');
  assert.equal(r.lShin, 'left_shin'); assert.equal(r.root, 'root'); assert.equal(r.rItem, 'right_item');
});

test('role detection on Bedrock-style names', () => {
  const m = { bones: ['bipedHead', 'bipedBody', 'bipedRightArm', 'bipedLeftArm', 'bipedRightLeg', 'bipedLeftLeg', 'RightArmLower', 'leg_l', 'arm_r'].map((n) => ({ name: n, parent: null, pivot: [0, 0, 0], cubes: [] })) };
  const r = detectRoles(m);
  assert.equal(r.head, 'bipedHead'); assert.equal(r.body, 'bipedBody'); assert.equal(r.rArm, 'bipedRightArm');
  assert.equal(r.lLeg, 'bipedLeftLeg'); assert.equal(r.rFore, 'RightArmLower');
});

test('easing endpoints', () => {
  for (const n of Object.keys(EASINGS)) { if (n === 'catmullrom') continue; assert.ok(Math.abs(ease(n, 0)) < 1e-6, n); assert.ok(Math.abs(ease(n, 1) - 1) < 1e-6, n); }
});

test('sampling interpolates with easing', () => {
  const a = createAnimation('t', 1);
  setKey(a, 'head', 'rotation', 0, [0, 0, 0], 'linear');
  setKey(a, 'head', 'rotation', 1, [90, 0, 0], 'linear');
  assert.deepEqual(sampleChannel(a.tracks.head.rotation, 0.5, 'rotation').map(Math.round), [45, 0, 0]);
  a.tracks.head.rotation[1].easing = 'easeInQuad';
  assert.equal(Math.round(sampleChannel(a.tracks.head.rotation, 0.5, 'rotation')[0]), 23);
});

test('every block compiles to a valid animation', () => {
  for (const id of Object.keys(BLOCKS)) {
    const anim = compileSequence([{ block: id, opts: {} }], rig);
    assert.ok(anim.length > 0, id);
    assert.ok(Object.keys(anim.tracks).length > 0, id);
    const s = sampleAnimation(anim, anim.length / 2);
    for (const v of Object.values(s)) for (const c of Object.values(v)) assert.ok(c.every(Number.isFinite), id);
    const file = exportAnimationFile('blocky', [anim]);
    const res = validateAnimationFile(file, exportGeo(model));
    assert.ok(res.ok, `${id}: ${res.errors.join('; ')}`);
  }
});

test('presets (incl. sequences) compile', () => {
  for (const p of PRESETS) {
    const steps = p.seq || [{ block: p.block, opts: p.opts }];
    const anim = compileSequence(steps, rig, { name: p.id });
    assert.equal(anim.sections.length, steps.length);
  }
});

test('combo flows without returning to stance between hits', () => {
  const anim = compileSequence(PRESETS.find((p) => p.id === 'combo').seq, rig);
  assert.equal(anim.sections.length, 3);
  assert.ok(anim.sections[1].start > 0 && anim.sections[2].start > anim.sections[1].start);
});

test('walk loops seamlessly', () => {
  const anim = compileSequence([{ block: 'walk' }], rig);
  assert.equal(anim.loop, 'loop');
  const a = sampleAnimation(anim, 0), b = sampleAnimation(anim, anim.length);
  for (const bone of Object.keys(a)) assert.deepEqual(a[bone].rotation.map((n) => n.toFixed(1)), b[bone].rotation.map((n) => n.toFixed(1)), bone);
});

test('joint limits respected after exaggeration', () => {
  const anim = compileSequence([{ block: 'sword_overhead', opts: { exaggeration: 2.5 } }], rig);
  for (const k of anim.tracks.right_forearm.rotation) assert.ok(k.v[0] <= 5 && k.v[0] >= -155);
});

test('regenerate one section keeps the others', () => {
  const anim = compileSequence(PRESETS.find((p) => p.id === 'combo').seq, rig);
  const firstKeys = JSON.stringify(anim.tracks.right_arm.rotation.filter((k) => k.t < anim.sections[0].end - 1e-3));
  regenerateSection(anim, anim.sections[2].id, { variation: 7, power: 1.4 }, rig);
  assert.equal(JSON.stringify(anim.tracks.right_arm.rotation.filter((k) => k.t < anim.sections[0].end - 1e-3)), firstKeys);
  assert.ok(validateAnimationFile(exportAnimationFile('blocky', [anim])).ok);
});

test('assistant understands the example prompts', () => {
  const cases = {
    'Make the character perform a powerful overhead sword strike.': ['sword_overhead'],
    'Make this character stumble backward after getting hit.': ['hit'],
    'Create a fast three-hit combo.': ['punch', 'punch', 'uppercut'],
    'Make the character charge energy before releasing an attack.': ['charge', 'release'],
    'Make the character turn around and slowly walk away.': ['turn', 'walk', 'walk'],
  };
  for (const [text, blocks] of Object.entries(cases)) {
    const plan = interpret(text);
    assert.equal(plan.kind, 'create', text);
    assert.deepEqual(plan.steps.map((s) => s.block), blocks, text);
    const anim = compileSequence(plan.steps, rig);
    assert.ok(validateAnimationFile(exportAnimationFile('blocky', [anim])).ok);
  }
  assert.ok(interpret('Make the character perform a powerful overhead sword strike.').steps[0].opts.power > 1);
  assert.ok(interpret('Make this character stumble backward after getting hit.').steps[0].opts.heavy);
  assert.ok(interpret('turn around and slowly walk away').steps[1].opts.speed < 1);
});

test('assistant adjustment commands', () => {
  const cases = { 'Make it faster.': 'faster', 'Add more power.': 'power', 'Make the movement less exaggerated.': 'less', 'Give the attack more weight.': 'weight', 'Make the recovery animation slower.': 'recovery' };
  for (const [text, id] of Object.entries(cases)) {
    const plan = interpret(text, { hasAnimation: true });
    assert.equal(plan.kind, 'adjust', text);
    assert.deepEqual(plan.adjustments, [id], text);
  }
});

test('adjustments edit keys and stay valid', () => {
  for (const id of Object.keys(ADJUSTMENTS)) {
    const anim = compileSequence([{ block: 'punch', opts: { side: 'right' } }], rig);
    const before = anim.length;
    ADJUSTMENTS[id].run(anim, null, rig);
    if (id === 'faster') assert.ok(anim.length < before);
    if (id === 'slower' || id === 'recovery') assert.ok(anim.length > before);
    assert.ok(validateAnimationFile(exportAnimationFile('blocky', [anim])).ok, id);
  }
});

test('polish adds anticipation/follow-through to a robotic hand-keyed punch', () => {
  const a = createAnimation('robot', 1);
  setKey(a, 'right_arm', 'rotation', 0, [0, 0, 0], 'linear');
  setKey(a, 'right_arm', 'rotation', 0.4, [0, 0, 0], 'linear');
  setKey(a, 'right_arm', 'rotation', 0.5, [-90, 0, 0], 'linear');
  setKey(a, 'right_arm', 'rotation', 1, [0, 0, 0], 'linear');
  const before = a.tracks.right_arm.rotation.length;
  const notes = polish(a, rig);
  assert.ok(a.tracks.right_arm.rotation.length > before, notes.join('\n'));
  assert.ok(!a.tracks.right_arm.rotation.some((k) => k.easing === 'linear'));
  assert.ok(a.tracks.root?.position?.length, 'weight shift added');
});

test('time scaling + key moving + pose copy/paste', () => {
  const anim = compileSequence([{ block: 'punch' }], rig);
  const L = anim.length;
  scaleTimeRange(anim, 2);
  assert.ok(Math.abs(anim.length - 2 * L) < 0.01);
  const t = keyTimes(anim)[1];
  moveKeysAt(anim, Object.keys(anim.tracks), t, t + 0.1);
  assert.ok(keyTimes(anim).some((x) => Math.abs(x - (t + 0.1)) < 1e-3));
  const pose = copyPose(anim, ['right_arm'], 0.3);
  pastePose(anim, pose, 0.05);
  assert.ok(keyTimes(anim, 'right_arm').includes(0.05));
});

test('geo.json export → import round trip', () => {
  const geo = exportGeo(model);
  assert.ok(validateGeo(geo).ok);
  const { model: m2 } = parseGeo(geo);
  assert.equal(m2.bones.length, model.bones.length);
  for (let i = 0; i < model.bones.length; i++) {
    assert.deepEqual(m2.bones[i].pivot, model.bones[i].pivot);
    assert.deepEqual(m2.bones[i].cubes.map((c) => [c.from, c.to]), model.bones[i].cubes.map((c) => [c.from, c.to]));
    assert.deepEqual(m2.bones[i].cubes.map((c) => c.faces.north.uv), model.bones[i].cubes.map((c) => c.faces.north.uv));
  }
});

test('.bbmodel export → import round trip incl. animations', () => {
  const anim = compileSequence([{ block: 'kick' }], rig, { name: 'kick' });
  anim.tracks.right_leg.rotation[1].easing = 'easeOutBack';
  anim.tracks.right_leg.rotation[1].args = [2];
  const bb = exportBBModel(model, [anim]);
  const { model: m2, animations, warnings } = parseBBModel(JSON.parse(JSON.stringify(bb)));
  assert.deepEqual(warnings, []);
  assert.deepEqual(m2.bones.map((b) => [b.name, b.parent]), model.bones.map((b) => [b.name, b.parent]));
  assert.equal(animations.length, 1);
  assert.equal(animations[0].tracks.right_leg.rotation[1].easing, 'easeOutBack');
  assert.deepEqual(animations[0].tracks.right_leg.rotation[1].args, [2]);
  assert.deepEqual(animations[0].tracks.right_leg.rotation.map((k) => k.v), anim.tracks.right_leg.rotation.map((k) => k.v));
});

test('GeckoLib animation export → import round trip', () => {
  const anim = compileSequence([{ block: 'sword_slash' }], rig, { name: 'Slash Attack!' });
  const file = exportAnimationFile('Blocky', [anim]);
  assert.ok(file.animations['animation.blocky.slash_attack']);
  const { animations } = parseAnimationFile(JSON.parse(JSON.stringify(file)));
  assert.equal(animations[0].name, 'slash_attack');
  assert.deepEqual(animations[0].tracks.right_arm.rotation.map((k) => [k.t, k.v, k.easing]), anim.tracks.right_arm.rotation.map((k) => [k.t, k.v, k.easing]));
});

test('validator catches real mistakes', () => {
  const bad = { format_version: '1.8.0', animations: { 'animation.x.y': { loop: 'forever', animation_length: -1, bones: { nope: { rotation: { abc: [0, 0], '0.5': { vector: [0, 0, 0], easing: 'easeSideways' } } } } } } };
  const r = validateAnimationFile(bad, exportGeo(model));
  assert.equal(r.ok, false);
  const joined = r.errors.join('\n');
  for (const s of ['loop', 'animation_length', 'nope', 'abc', 'easeSideways']) assert.ok(joined.includes(s), s);
});

test('mod package contains every file at the right path and validates', () => {
  const anim = compileSequence([{ block: 'punch' }], rig, { name: 'punch' });
  for (const target of Object.keys(TARGETS)) {
    const { files, report } = buildPackage({ model, animations: [anim], modId: 'mymod', entityName: 'blocky', target, include: { model: true, animations: true, texture: false, bbmodel: true, java: true, readme: true } });
    assert.ok(report.ok, report.errors.join('; '));
    const paths = files.map((f) => f.path);
    const t = TARGETS[target];
    assert.ok(paths.includes(t.paths('mymod', 'blocky').anim), paths.join(','));
    assert.ok(paths.includes(t.paths('mymod', 'blocky').geo));
    if (t.java) assert.ok(paths.some((p) => p.endsWith('.java')));
    const zip = files.length && readZip(buildPackage({ model, animations: [anim], modId: 'mymod', entityName: 'blocky', target, include: { model: true, animations: true, java: true, readme: true } }).zip());
    assert.equal(zip.length, files.length);
    const animJson = JSON.parse(new TextDecoder().decode(zip.find((f) => f.name === t.paths('mymod', 'blocky').anim).data));
    assert.ok(validateAnimationFile(animJson).ok);
  }
});

console.log(`\n${passed} passed${process.exitCode ? ', some FAILED' : ''}`);
