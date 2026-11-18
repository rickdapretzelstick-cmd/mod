// Bottom-sheet panels. Each panel is a function (app, el) that renders into el.
import { h, toast, modal, ask, confirm, pickFiles, download, readText, readDataUrl, scrubber } from './dom.js';
import { PRESETS, BLOCKS, BLOCK_LIST } from '../core/blocks.js';
import { interpret, PRINCIPLE_NOTES } from '../core/assistant.js';
import { ADJUSTMENTS } from '../core/adjust.js';
import { EASE_PRESETS, EASING_NAMES, presetForEasing } from '../core/easing.js';
import { sampleChannel, cloneAnimation, createAnimation, uid, CHANNELS, renameBones, round, scaleTimeRange } from '../core/animation.js';
import { boneTree, sanitizeBoneNames } from '../core/model.js';
import { ROLES, JOINT_LIMITS, clampToLimits, resolveRig } from '../core/rig.js';
import { analyze } from '../core/assist.js';
import { parseBBModel, exportBBModel } from '../formats/bbmodel.js';
import { parseGeo, exportGeo } from '../formats/geo.js';
import { parseAnimationFile, exportAnimationFile, validateAnimationFile, animationId } from '../formats/geckolib.js';
import { buildPackage, TARGETS, cleanId } from '../export/package.js';
import { createSampleModel } from '../core/sampleModel.js';
import * as store from '../core/storage.js';

const info = (title, text) => h('button', { class: 'info', 'aria-label': `What is ${title}?`, onclick: () => modal(title, h('p', {}, text)) }, '?');
const section = (title, ...kids) => h('div', { class: 'psec' }, h('h3', {}, title), ...kids);

// ======================= CREATE =======================
function create(app, el) {
  el.innerHTML = '';
  const sec = app.selectedSection;
  const input = h('textarea', { class: 'assist-input', rows: 2, placeholder: 'Describe a move… e.g. “a powerful overhead sword strike”', 'aria-label': 'Describe an animation', value: app._assistText || '' });
  input.addEventListener('input', () => { app._assistText = input.value; });
  // the reply survives panel re-renders (creating an animation re-renders the panel)
  const out = app._assistOut || (app._assistOut = h('div', { class: 'assist-out' }));
  const run = () => {
    const text = input.value.trim();
    if (!text) return;
    app._assistText = text;
    const plan = interpret(text, { hasAnimation: Object.keys(app.anim.tracks).length > 0 });
    out.innerHTML = '';
    if (plan.kind === 'adjust') {
      const msgs = plan.adjustments.map((id) => app.runAdjust(id));
      out.append(h('div', { class: 'bubble' }, h('b', {}, plan.explanation), h('ul', {}, msgs.map((m) => h('li', {}, m))), h('div', { class: 'muted' }, 'Undo (↶) if you don’t like it.')));
      return;
    }
    if (plan.kind === 'unknown') { out.append(h('div', { class: 'bubble warn' }, plan.explanation)); return; }
    const mode = el.querySelector('#appendMode')?.checked ? 'append' : 'new';
    app.applySteps(plan.steps, { name: plan.name, mode });
    out.append(h('div', { class: 'bubble' },
      h('b', {}, plan.explanation),
      h('ol', {}, plan.steps.map((s) => h('li', {}, `${BLOCKS[s.block].icon} ${s.label}`, s.opts.side && BLOCKS[s.block].sided ? ` (${s.opts.side})` : '', h('div', { class: 'muted' }, PRINCIPLE_NOTES[s.block] || '')))),
      h('div', { class: 'muted' }, 'It’s all normal keyframes now – tap a move on the timeline to tweak or regenerate just that part.')));
  };
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); run(); } });
  const examples = ['powerful overhead sword strike', 'stumble backward after getting hit', 'fast three-hit combo', 'charge energy before releasing an attack', 'turn around and slowly walk away', 'dodge left then punch'];

  el.append(
    section('Describe a move',
      h('div', { class: 'assist-row' }, input, h('button', { class: 'btn primary go', onclick: run, 'aria-label': 'Create' }, 'Go')),
      h('div', { class: 'chips' }, examples.map((x) => h('button', { class: 'chip', onclick: () => { input.value = x; run(); } }, x))),
      h('label', { class: 'check' }, h('input', { type: 'checkbox', id: 'appendMode' }), ' Add to the end of the current animation instead of making a new one'),
      out),
    sec ? sectionTools(app, sec) : null,
    section(`Adjust ${sec ? `“${sec.label}”` : 'the whole animation'}`,
      h('div', { class: 'chips big' }, Object.entries(ADJUSTMENTS).map(([id, a]) => h('button', { class: 'chip', onclick: () => app.runAdjust(id) }, `${a.icon} ${a.label}`))),
      h('button', { class: 'btn wide', onclick: () => { const notes = app.polishMotion(); modal('Motion polish', h('ul', {}, notes.map((n) => h('li', {}, n)))); } }, '🪄 Polish motion (fix robotic movement)')),
    section('Start from a preset',
      h('div', { class: 'preset-grid' }, PRESETS.map((p) => {
        const b = BLOCKS[p.block || p.seq[0].block];
        return h('button', { class: 'preset', onclick: () => { app.applySteps(p.seq || [{ block: p.block, opts: p.opts }], { name: p.id, mode: el.querySelector('#appendMode')?.checked ? 'append' : 'new' }); toast(`${p.label} created – press ▶ to watch it again`); } }, h('span', { class: 'picon' }, p.id === 'charge' ? '⚡' : p.id === 'combo' ? '🥊' : b.icon), h('span', {}, p.label));
      }))),
    section('Build a sequence', h('p', { class: 'muted' }, 'Chain moves together – transitions are automatic.'), sequenceBuilder(app)),
  );
}

function sectionTools(app, sec) {
  const b = BLOCKS[sec.block];
  const opts = sec.opts || {};
  const slider = (label, key, min, max, stepv) => {
    const v = opts[key] ?? 1;
    const out = h('span', { class: 'muted' }, `${Number(v).toFixed(2)}×`);
    const r = h('input', { type: 'range', min, max, step: stepv, value: v, 'aria-label': label });
    r.addEventListener('input', () => { out.textContent = `${Number(r.value).toFixed(2)}×`; });
    r.addEventListener('change', () => app.regenerate(sec.id, { [key]: Number(r.value) }));
    return h('label', { class: 'slider' }, h('span', {}, label), r, out);
  };
  return section(`Selected move: ${b?.icon || ''} ${sec.label}`,
    h('p', { class: 'muted' }, PRINCIPLE_NOTES[sec.block] || ''),
    h('div', { class: 'row' },
      h('button', { class: 'btn', onclick: () => app.regenerate(sec.id, { variation: Math.floor(Math.random() * 9999) + 1 }) }, '🎲 Regenerate'),
      b?.sided ? h('button', { class: 'btn', onclick: () => app.regenerate(sec.id, { side: opts.side === 'left' ? 'right' : 'left' }) }, `↔ Use ${opts.side === 'left' ? 'right' : 'left'} side`) : null,
      h('button', { class: 'btn', onclick: () => app.setTime(sec.start, true) }, '⏮ Go to it')),
    slider('Speed', 'speed', 0.4, 2, 0.05),
    slider('Power', 'power', 0.5, 1.8, 0.05),
    slider('Exaggeration', 'exaggeration', 0.5, 1.8, 0.05),
    h('p', { class: 'muted small' }, 'Regenerating replaces only this move’s keyframes. The adjust buttons below now apply to this move only – tap it on the timeline again to deselect.'));
}

function sequenceBuilder(app) {
  const steps = app._seq || (app._seq = []);
  const list = h('ol', { class: 'seq-list' });
  const draw = () => {
    list.innerHTML = '';
    steps.forEach((s, i) => list.append(h('li', {}, `${BLOCKS[s.block].icon} ${BLOCKS[s.block].label}${s.opts.side ? ` (${s.opts.side})` : ''}`,
      BLOCKS[s.block].sided ? h('button', { class: 'mini', onclick: () => { s.opts.side = s.opts.side === 'left' ? 'right' : 'left'; draw(); } }, '↔') : null,
      h('button', { class: 'mini', 'aria-label': 'Move up', onclick: () => { if (i > 0) { [steps[i - 1], steps[i]] = [steps[i], steps[i - 1]]; draw(); } } }, '↑'),
      h('button', { class: 'mini', 'aria-label': 'Remove', onclick: () => { steps.splice(i, 1); draw(); } }, '✕'))));
    if (!steps.length) list.append(h('li', { class: 'muted' }, 'Empty – add moves below.'));
  };
  draw();
  const select = h('select', { class: 'input', 'aria-label': 'Move to add' }, BLOCK_LIST.map((b) => h('option', { value: b.id }, `${b.icon} ${b.label} · ${b.category}`)));
  return h('div', {}, list,
    h('div', { class: 'row' }, select,
      h('button', { class: 'btn', onclick: () => { const id = select.value; steps.push({ block: id, opts: BLOCKS[id].sided ? { side: 'right' } : {} }); draw(); } }, '+ Add')),
    h('div', { class: 'row' },
      h('button', { class: 'btn primary', disabled: false, onclick: () => { if (!steps.length) return toast('Add at least one move.'); app.applySteps(steps.map((s, i) => ({ ...s, opts: { ...s.opts, recover: i === steps.length - 1 ? undefined : (['punch', 'kick', 'sword_slash', 'sword_stab', 'uppercut'].includes(s.block) && ['punch', 'kick', 'sword_slash', 'sword_stab', 'sword_overhead', 'uppercut'].includes(steps[i + 1]?.block) ? false : undefined) } })), { name: 'sequence' }); } }, '▶ Create sequence'),
      h('button', { class: 'btn', onclick: () => { steps.length = 0; draw(); } }, 'Clear')));
}

// ======================= POSE =======================
function pose(app, el) {
  el.innerHTML = '';
  const bone = app.selection.bone;
  if (!bone) {
    el.append(section('Pose a body part', h('p', {}, 'Tap a body part in the 3D view (or pick one below), then drag the ring handles or the sliders here.'), bonePickerList(app)));
    app._poseSync = null;
    return;
  }
  const isModel = app.mode === 'model';
  const ch = isModel ? (app.gizmoMode === 'move' ? 'position' : 'rotation') : (app.gizmoMode === 'move' ? 'position' : app.gizmoMode === 'scale' ? 'scale' : 'rotation');
  const get = () => {
    if (isModel) { const b = app.model.bones.find((x) => x.name === bone); return ch === 'position' ? b.pivot : b.rotation; }
    return sampleChannel(app.anim.tracks[bone]?.[ch], app.time, ch);
  };
  const role = app.rig.roleOf(bone);
  const axes = ['X', 'Y', 'Z'], colors = ['#ff6b6b', '#5fd068', '#5aa9ff'];
  const hints = ch === 'rotation' ? ['tilt forward / back', 'turn left / right', 'lean sideways'] : ch === 'position' ? ['sideways', 'up / down', 'forward / back'] : ['width', 'height', 'depth'];
  const warn = h('div', { class: 'limit-warn' });
  let vals = get().slice();
  const scr = axes.map((ax, i) => scrubber({
    label: `${ax} · ${hints[i]}`, value: vals[i], step: ch === 'scale' ? 0.01 : ch === 'position' ? 0.1 : 1, fine: ch === 'scale' ? 0.01 : 0.1,
    unit: ch === 'rotation' ? '°' : ch === 'position' ? 'px' : '×', color: colors[i],
    onStart: () => { app.beginEdit(isModel ? 'Edit bone' : 'Pose bone'); vals = get().slice(); },
    onInput: (v) => { vals[i] = v; app.setBoneChannel(bone, ch, vals.slice(), true); checkLimits(); },
    onEnd: () => app.endEdit(),
  }));
  const checkLimits = () => {
    warn.textContent = '';
    if (isModel || ch !== 'rotation' || !role || !JOINT_LIMITS[role]) return;
    const { clamped, v } = clampToLimits(role, get());
    if (clamped) warn.append('⚠ This bends further than a real joint can. ', h('button', { class: 'mini', onclick: () => { app.commit('Fix joint limit', () => app.setBoneChannel(bone, 'rotation', v)); } }, 'Fix'));
  };
  app._poseSync = () => { const v = get(); scr.forEach((s, i) => s.setValue(v[i])); checkLimits(); };
  checkLimits();

  const bonesList = app.model.bones;
  const b = bonesList.find((x) => x.name === bone);
  const kids = bonesList.filter((x) => x.parent === bone);
  el.append(
    section(`${isModel ? 'Model: ' : ''}🦴 ${bone}${role ? ` · ${ROLES.find((r) => r.id === role)?.label}` : ''}`,
      h('div', { class: 'seg wide' },
        h('button', { class: ch === 'rotation' ? 'on' : '', onclick: () => app.setGizmoMode('rotate') }, '⟳ Rotate'),
        h('button', { class: ch === 'position' ? 'on' : '', onclick: () => app.setGizmoMode('move') }, isModel ? '✥ Pivot' : '✥ Move'),
        isModel ? null : h('button', { class: ch === 'scale' ? 'on' : '', onclick: () => app.setGizmoMode('scale') }, '⤢ Scale')),
      ...scr, warn,
      isModel ? h('p', { class: 'muted small' }, 'Model mode edits the bone’s rest rotation / pivot (saved into the model and the exported geo.json).')
        : h('p', { class: 'muted small' }, 'Changes are keyed automatically at the playhead (', info('keyframes', 'A keyframe stores a pose at a moment in time. The app smoothly fills in the motion between keyframes – that’s called interpolation. Move the playhead, change the pose, and a keyframe appears.'), ').'),
      h('div', { class: 'row' },
        b?.parent ? h('button', { class: 'btn', onclick: () => app.selectBone(b.parent) }, `↑ ${b.parent}`) : null,
        kids.slice(0, 3).map((k) => h('button', { class: 'btn', onclick: () => app.selectBone(k.name) }, `↓ ${k.name}`)))),
    isModel ? null : section('Pose tools',
      h('div', { class: 'row wrap' },
        h('button', { class: 'btn', onclick: () => app.keyNow([bone]) }, '◆ Key this part'),
        h('button', { class: 'btn', onclick: () => app.keyNow(app.model.bones.map((x) => x.name)) }, '◆ Key whole pose'),
        h('button', { class: 'btn', onclick: () => app.commit('Reset bone', () => { for (const c of CHANNELS) app.setBoneChannel(bone, c, c === 'scale' ? [1, 1, 1] : [0, 0, 0], true); }) }, '⟲ Reset part'),
        h('button', { class: 'btn', onclick: () => { app.selection.keyTime = null; app.copyKey(); } }, '⧉ Copy pose'),
        h('button', { class: 'btn', onclick: () => app.pasteKey() }, '📋 Paste'),
        h('button', { class: 'btn', onclick: () => app.pasteKey(true) }, '↔ Paste mirrored'))),
  );
}

function bonePickerList(app) {
  return h('div', { class: 'bone-list' }, boneTree(app.model).map(({ bone, depth }) => {
    const role = app.rig.roleOf(bone.name);
    return h('button', { class: `bone-item ${app.selection.bone === bone.name ? 'on' : ''}`, style: { paddingLeft: `${12 + depth * 14}px` }, onclick: () => { app.selectBone(bone.name); document.querySelector('.modal-wrap')?.remove(); } },
      bone.name, role ? h('span', { class: 'muted small' }, ` · ${ROLES.find((r) => r.id === role).label}`) : '', app.anim.tracks[bone.name] ? h('span', { class: 'dot' }) : '');
  }));
}
function bonePicker(app) { modal('Choose a body part', bonePickerList(app), [{ label: 'Close' }]); }

// ======================= KEY BAR (under transport) =======================
function keyBar(app, el) {
  el.innerHTML = '';
  const { keyTime, keyBone } = app.selection;
  if (keyTime === null || app.mode === 'model') { el.classList.remove('show'); return; }
  el.classList.add('show');
  let cur = null;
  for (const b of keyBone ? [keyBone] : Object.keys(app.anim.tracks)) for (const c of CHANNELS) { const k = app.anim.tracks[b]?.[c]?.find((x) => Math.abs(x.t - keyTime) < 1e-4); if (k) { cur = k; break; } }
  const preset = cur ? presetForEasing(cur.easing) : null;
  const adv = h('select', { class: 'input small', 'aria-label': 'All easing types', onchange: (e) => app.setEasing(e.target.value) },
    EASING_NAMES.map((n) => h('option', { value: n, selected: cur?.easing === n }, n)));
  el.append(
    h('span', { class: 'kb-title' }, `◆ ${keyBone || 'All'} @ ${keyTime.toFixed(2)}s`),
    h('div', { class: 'kb-row' },
      h('span', { class: 'muted small' }, 'Motion into this key ', info('easing', 'Easing controls how the movement speeds up or slows down on the way INTO this keyframe. “Smooth” is a safe default; “Ease out” makes strikes snappy; “Overshoot” goes a bit past and settles.')),
      EASE_PRESETS.map((p) => h('button', { class: `chip ${preset?.id === p.id ? 'on' : ''}`, title: p.hint, onclick: () => app.setEasing(p.easing) }, p.label)),
      h('details', { class: 'adv' }, h('summary', {}, 'More'), adv)),
    h('div', { class: 'kb-row' },
      h('button', { class: 'chip', onclick: () => app.moveKey(keyBone, keyTime, Math.max(0, round(keyTime - 0.05, 4))) }, '◀ Earlier'),
      h('button', { class: 'chip', onclick: () => app.moveKey(keyBone, keyTime, round(keyTime + 0.05, 4)) }, 'Later ▶'),
      h('button', { class: 'chip', onclick: () => app.copyKey() }, '⧉ Copy'),
      h('button', { class: 'chip', onclick: () => app.pasteKey() }, '📋 Paste at playhead'),
      h('button', { class: 'chip danger', onclick: () => app.deleteSelectedKey() }, '🗑 Delete'),
      h('button', { class: 'chip', onclick: () => app.clearKeySel() }, '✕')),
  );
}

// ======================= ANIMATIONS + LIBRARY =======================
function anims(app, el) {
  el.innerHTML = '';
  const a = app.anim;
  const issues = analyze(a, app.rig);
  el.append(
    section('Animations in this project',
      h('div', { class: 'anim-list' }, app.project.animations.map((x) => h('button', { class: `anim-item ${x.id === a.id ? 'on' : ''}`, onclick: () => app.selectAnimation(x.id) },
        h('b', {}, x.name), h('span', { class: 'muted small' }, `${x.length.toFixed(2)}s · ${x.loop === 'loop' ? '🔁 loop' : x.loop === 'hold' ? '⏸ hold' : '▶ once'} · ${Object.keys(x.tracks).length} parts`)))),
      h('div', { class: 'row wrap' },
        h('button', { class: 'btn', onclick: () => app.commit('New animation', () => app.addAnimation(createAnimation('new_animation', 1, 'once'))) }, '+ Empty'),
        h('button', { class: 'btn', onclick: () => app.commit('Duplicate animation', () => { const c = cloneAnimation(a); c.name = `${a.name}_copy`; app.addAnimation(c); }) }, '⧉ Duplicate'),
        h('button', { class: 'btn', onclick: async () => { const n = await ask('Rename animation', a.name, 'Lowercase names work best in Minecraft, e.g. sword_slash'); if (n) app.commit('Rename animation', () => { a.name = n; }); } }, '✎ Rename'),
        h('button', { class: 'btn danger', disabled: app.project.animations.length < 2, onclick: async () => { if (await confirm('Delete animation?', `Delete “${a.name}”? You can undo this.`, 'Delete', true)) app.commit('Delete animation', () => { app.project.animations = app.project.animations.filter((x) => x !== a); app.project.activeAnimId = app.project.animations[0].id; }); } }, '🗑 Delete'))),
    section('Settings for “' + a.name + '”',
      h('div', { class: 'seg wide' }, [['once', '▶ Play once'], ['loop', '🔁 Loop'], ['hold', '⏸ Hold last frame']].map(([v, l]) => h('button', { class: a.loop === v ? 'on' : '', onclick: () => app.commit('Loop mode', () => { a.loop = v; }) }, l))),
      h('div', { class: 'row' },
        h('button', { class: 'btn', onclick: async () => { const n = await ask('Length in seconds', String(a.length)); if (n && Number(n) > 0) app.setLength(Number(n)); } }, `⏱ Length ${a.length.toFixed(2)}s`),
        h('button', { class: 'btn', onclick: async () => { const n = await ask('Stretch timing (e.g. 0.5 = twice as fast, 2 = half speed)', '1'); if (n && Number(n) > 0) app.commit('Retime', () => scaleTimeRange(a, Number(n))); } }, '⤢ Stretch timing')),
      issues.length ? h('div', { class: 'bubble warn' }, h('b', {}, 'Tips'), h('ul', {}, issues.map((i) => h('li', {}, i.msg)))) : null,
      h('label', { class: 'lbl' }, 'Combine: add another animation to the end of this one'),
      h('div', { class: 'row' }, (() => {
        const s = h('select', { class: 'input' }, app.project.animations.filter((x) => x !== a).map((x) => h('option', { value: x.id }, x.name)));
        return [s, h('button', { class: 'btn', disabled: app.project.animations.length < 2, onclick: () => { const src = app.project.animations.find((x) => x.id === s.value); if (src) app.commit('Combine animations', () => app.appendAnimation(a, cloneAnimation(src))); } }, '+ Append')];
      })())),
    librarySection(app),
  );
}

function librarySection(app) {
  const wrap = section('📚 My library', h('p', { class: 'muted' }, 'Save animations here to reuse them in any project – they adapt to other characters by body part.'));
  const body = h('div', {}, h('p', { class: 'muted' }, 'Loading…'));
  wrap.append(h('div', { class: 'row' },
    h('button', { class: 'btn primary', onclick: async () => {
      const folder = await ask('Save to folder', app._lastFolder || 'My moves', `Saving “${app.anim.name}”`);
      if (folder === null) return;
      app._lastFolder = folder || 'My moves';
      const rig = app.rig;
      const roleMap = {}; for (const b of Object.keys(app.anim.tracks)) { const r = rig.roleOf(b); if (r) roleMap[b] = r; }
      await store.put('library', { id: uid('lib'), name: app.anim.name, folder: app._lastFolder, animation: cloneAnimation(app.anim), roleMap, savedAt: Date.now() });
      toast(`Saved “${app.anim.name}” to ${app._lastFolder}`); drawLib();
    } }, '⭳ Save current to library')), body);
  const drawLib = async () => {
    const items = (await store.all('library')).sort((x, y) => (x.folder + x.name).localeCompare(y.folder + y.name));
    body.innerHTML = '';
    if (!items.length) { body.append(h('p', { class: 'muted' }, 'Nothing saved yet.')); return; }
    const folders = [...new Set(items.map((i) => i.folder))];
    for (const f of folders) {
      body.append(h('details', { class: 'folder', open: true }, h('summary', {}, `📁 ${f}`),
        items.filter((i) => i.folder === f).map((it) => h('div', { class: 'lib-item' },
          h('span', {}, h('b', {}, it.name), h('span', { class: 'muted small' }, ` ${it.animation.length.toFixed(2)}s`)),
          h('div', { class: 'row' },
            h('button', { class: 'mini', title: 'Preview', onclick: () => { const anim = retarget(app, it); const prev = app.project.activeAnimId; app.project.animations.push(anim); app.project.activeAnimId = anim.id; app.time = 0; app.playing = true; app.refresh(); toast('Previewing – tap “Use” to keep it'); setTimeout(() => { if (app.project.activeAnimId === anim.id && !anim._kept) { app.project.animations = app.project.animations.filter((x) => x !== anim); app.project.activeAnimId = prev; app.playing = false; app.refresh(); } }, Math.max(1500, anim.length * 2000)); } }, '👁'),
            h('button', { class: 'mini', title: 'Use in project', onclick: () => { app.commit('Insert from library', () => app.addAnimation(retarget(app, it))); toast(`Added “${it.name}”`); } }, 'Use'),
            h('button', { class: 'mini', title: 'Append to current', onclick: () => app.commit('Append from library', () => app.appendAnimation(app.anim, retarget(app, it))) }, '+End'),
            h('button', { class: 'mini', title: 'Rename', onclick: async () => { const n = await ask('Rename', it.name); if (n) { it.name = n; it.animation.name = n; await store.put('library', it); drawLib(); } } }, '✎'),
            h('button', { class: 'mini', title: 'Move to folder', onclick: async () => { const n = await ask('Folder', it.folder); if (n) { it.folder = n; await store.put('library', it); drawLib(); } } }, '📁'),
            h('button', { class: 'mini', title: 'Duplicate', onclick: async () => { await store.put('library', { ...structuredClone(it), id: uid('lib'), name: `${it.name}_copy` }); drawLib(); } }, '⧉'),
            h('button', { class: 'mini danger', title: 'Delete', onclick: async () => { if (await confirm('Delete from library?', it.name, 'Delete', true)) { await store.del('library', it.id); drawLib(); } } }, '🗑'))))));
    }
  };
  drawLib();
  return wrap;
}

// Map a library clip's bones onto the current model via body-part roles.
function retarget(app, item) {
  const anim = cloneAnimation(item.animation);
  const rig = app.rig;
  const names = new Set(app.model.bones.map((b) => b.name));
  const map = {};
  for (const b of Object.keys(anim.tracks)) {
    if (names.has(b)) continue;
    const role = item.roleMap?.[b];
    const target = role && rig.bone(role);
    map[b] = target || null;
  }
  renameBones(anim, map);
  return anim;
}

// ======================= EXPORT =======================
function exportPanel(app, el) {
  el.innerHTML = '';
  const st = app._exp || (app._exp = { target: 'geckolib4', modId: 'yourmod', entity: app.model.name, include: { model: true, animations: true, texture: true, bbmodel: true, java: true, readme: true }, anims: null });
  st.entity = st.entity || app.model.name;
  const selected = () => app.project.animations.filter((a) => !st.anims || st.anims.includes(a.id));
  const report = h('div', { class: 'report' });
  const check = (k, label) => h('label', { class: 'check' }, h('input', { type: 'checkbox', checked: st.include[k], onchange: (e) => { st.include[k] = e.target.checked; } }), ` ${label}`);
  const build = () => buildPackage({ model: app.model, animations: selected(), modId: st.modId, entityName: st.entity, target: st.target, include: st.include });
  const validate = () => {
    const { report: r, files } = build();
    report.innerHTML = '';
    report.append(h('div', { class: `bubble ${r.ok ? 'ok' : 'warn'}` },
      h('b', {}, r.ok ? `✅ Valid – ${files.length} files ready` : '❌ Problems found'),
      r.errors.length ? h('ul', {}, r.errors.map((e) => h('li', {}, e))) : null,
      r.warnings.length ? h('details', {}, h('summary', {}, `${r.warnings.length} warning(s)`), h('ul', {}, r.warnings.map((w) => h('li', {}, w)))) : null,
      h('details', {}, h('summary', {}, 'Files'), h('ul', { class: 'mono' }, files.map((f) => h('li', {}, f.path))))));
    return r;
  };
  el.append(
    section('Export to your mod ' , info('GeckoLib', 'GeckoLib is the most common library for animated entities in Minecraft Java mods (Forge, NeoForge, Fabric). This app writes its geometry (.geo.json) and animation (.animation.json) files, validated against GeckoLib’s format.'),
      h('label', { class: 'lbl' }, 'Target'),
      h('select', { class: 'input', onchange: (e) => { st.target = e.target.value; validate(); } }, Object.entries(TARGETS).map(([k, t]) => h('option', { value: k, selected: st.target === k }, t.label))),
      h('div', { class: 'row' },
        h('label', { class: 'lbl grow' }, 'Mod ID', h('input', { class: 'input', value: st.modId, onchange: (e) => { st.modId = cleanId(e.target.value); e.target.value = st.modId; validate(); } })),
        h('label', { class: 'lbl grow' }, 'Entity / model name', h('input', { class: 'input', value: st.entity, onchange: (e) => { st.entity = cleanId(e.target.value); e.target.value = st.entity; validate(); } }))),
      h('label', { class: 'lbl' }, 'Animations to include'),
      h('div', { class: 'chips' }, app.project.animations.map((a) => h('button', { class: `chip ${!st.anims || st.anims.includes(a.id) ? 'on' : ''}`, onclick: () => { const all = app.project.animations.map((x) => x.id); st.anims = st.anims || all.slice(); st.anims = st.anims.includes(a.id) ? st.anims.filter((x) => x !== a.id) : [...st.anims, a.id]; exportPanel(app, el); } }, animationId(st.entity, a.name)))),
      h('details', {}, h('summary', {}, 'What to include'),
        check('model', 'Model geometry (.geo.json)'), check('animations', 'Animations (.animation.json)'), check('texture', 'Texture (.png)'),
        check('bbmodel', 'Blockbench project (.bbmodel)'), check('java', 'Java snippet with animation constants + controller'), check('readme', 'README with install steps')),
      h('div', { class: 'row wrap' },
        h('button', { class: 'btn', onclick: validate }, '✔ Validate'),
        h('button', { class: 'btn primary', onclick: () => { const r = validate(); if (!r.ok) return toast('Fix the problems first.', 'error'); const p = build(); download(`${cleanId(st.modId)}_${cleanId(st.entity)}_${st.target}.zip`, p.zip(), 'application/zip'); toast('Mod asset package downloaded'); } }, '⬇ Download mod package (.zip)')),
      report),
    section('Single files',
      h('div', { class: 'row wrap' },
        h('button', { class: 'btn', onclick: () => { const f = exportAnimationFile(st.entity, [app.anim]); const v = validateAnimationFile(f); if (!v.ok) return toast(v.errors[0], 'error'); download(`${cleanId(st.entity)}.${cleanId(app.anim.name)}.animation.json`, JSON.stringify(f, null, 2), 'application/json'); } }, `⬇ “${app.anim.name}” only`),
        h('button', { class: 'btn', onclick: () => { const f = exportAnimationFile(st.entity, app.project.animations); download(`${cleanId(st.entity)}.animation.json`, JSON.stringify(f, null, 2), 'application/json'); } }, '⬇ All animations (.json)'),
        h('button', { class: 'btn', onclick: () => download(`${cleanId(st.entity)}.geo.json`, JSON.stringify(exportGeo(app.model, `geometry.${cleanId(st.entity)}`), null, 2), 'application/json') }, '⬇ Model (.geo.json)'),
        h('button', { class: 'btn', onclick: () => download(`${cleanId(st.entity)}.bbmodel`, JSON.stringify(exportBBModel(app.model, app.project.animations)), 'application/json') }, '⬇ Blockbench (.bbmodel)'),
        h('button', { class: 'btn', onclick: async () => { const txt = JSON.stringify(exportAnimationFile(st.entity, [app.anim]), null, 2); try { await navigator.clipboard.writeText(txt); toast('Animation JSON copied'); } catch { modal('Animation JSON', h('textarea', { class: 'input mono', rows: 12, value: txt })); } } }, '📋 Copy JSON'))),
  );
  validate();
}

// ======================= PROJECT / MODEL =======================
function project(app, el) {
  el.innerHTML = '';
  const list = h('div', { class: 'anim-list' }, h('p', { class: 'muted' }, 'Loading…'));
  store.all('projects').then((ps) => {
    list.innerHTML = '';
    ps.sort((a, b) => b.updatedAt - a.updatedAt).forEach((p) => list.append(h('div', { class: `anim-item ${p.id === app.project.id ? 'on' : ''}` },
      h('button', { class: 'grow plain', onclick: async () => { if (p.id !== app.project.id) { await app.persist(); await app.openProject(await store.get('projects', p.id), false); toast(`Opened ${p.name}`); } } }, h('b', {}, p.name), h('span', { class: 'muted small' }, ` ${p.model.name} · ${p.animations.length} anim · ${new Date(p.updatedAt).toLocaleString()}`)),
      h('button', { class: 'mini', onclick: async () => { const c = structuredClone(p); c.id = uid('proj'); c.name = `${p.name} copy`; await store.put('projects', c); project(app, el); } }, '⧉'),
      p.id !== app.project.id ? h('button', { class: 'mini danger', onclick: async () => { if (await confirm('Delete project?', `Delete “${p.name}” permanently?`, 'Delete', true)) { await store.del('projects', p.id); project(app, el); } } }, '🗑') : null)));
  });
  const rig = app.rig;
  el.append(
    section('Projects', list,
      h('div', { class: 'row wrap' },
        h('button', { class: 'btn', onclick: async () => { const n = await ask('New project name', 'New animation project'); if (!n) return; await app.persist(); await app.openProject(app.makeProject(n, createSampleModel())); } }, '+ New with Blocky'),
        h('button', { class: 'btn', onclick: () => importFiles(app, true) }, '+ New from model file…'),
        h('button', { class: 'btn', onclick: () => download(`${cleanId(app.project.name)}.mcanim.json`, JSON.stringify(app.project), 'application/json') }, '⬇ Backup project'))),
    section('Import ' , info('import', 'Load a Blockbench .bbmodel (best – includes texture and animations), a GeckoLib/Bedrock .geo.json (+ its .png texture), or a .animation.json to edit existing animations. You can select several files at once.'),
      h('div', { class: 'row wrap' },
        h('button', { class: 'btn primary', onclick: () => importFiles(app, false) }, '📂 Import files into this project'),
        h('button', { class: 'btn', onclick: () => { const m = createSampleModel(); app.makeProject('tmp', m); app.commit('Load sample model', () => { app.project.model = m; }); app.invalidateRig(); app.viewer.setModel(app.model); app.refresh(); } }, '🧍 Use sample character'))),
    section('Body parts ' , info('body parts', 'Presets and the assistant work on body parts (head, arms, legs…). Names like right_arm or bipedRightArm are detected automatically; if something is wrong, fix it here.'),
      h('p', { class: 'muted small' }, `Recognised ${Math.round(rig.score * 100)}% of the main body parts.`),
      h('div', { class: 'roles' }, ROLES.map((r) => h('label', { class: 'role-row' }, h('span', {}, r.label),
        h('select', { class: 'input small', onchange: (e) => app.commit('Set body part', () => { app.model.roles = { ...(app.model.roles || {}), [r.id]: e.target.value }; app.invalidateRig(); }) },
          h('option', { value: '' }, '— none —'), app.model.bones.map((b) => h('option', { value: b.name, selected: rig.bone(r.id) === b.name }, b.name))))))),
    section('Bones',
      h('div', { class: 'bone-list' }, boneTree(app.model).map(({ bone, depth }) => h('div', { class: 'bone-item', style: { paddingLeft: `${12 + depth * 14}px` } },
        h('button', { class: 'plain grow', onclick: () => app.selectBone(bone.name) }, bone.name, h('span', { class: 'muted small' }, ` ${bone.cubes.length} cube${bone.cubes.length === 1 ? '' : 's'}`)),
        h('button', { class: 'mini', title: 'Show / hide', onclick: () => app.commit('Toggle visibility', () => { bone.hidden = !bone.hidden; app.viewer.setBoneVisible(bone.name, !bone.hidden); }) }, bone.hidden ? '🙈' : '👁'),
        h('button', { class: 'mini', title: 'Rename bone', onclick: async () => {
          const n = await ask('Rename bone', bone.name, 'Keyframes in every animation follow the new name.');
          if (!n || n === bone.name || app.model.bones.some((b) => b.name === n)) return;
          app.commit('Rename bone', () => { const old = bone.name; for (const b of app.model.bones) if (b.parent === old) b.parent = n; bone.name = n; for (const a of app.project.animations) renameBones(a, { [old]: n }); if (app.model.roles) for (const k of Object.keys(app.model.roles)) if (app.model.roles[k] === old) app.model.roles[k] = n; app.selection.bone = n; });
          app.viewer.setModel(app.model); app.invalidateRig(); app.refresh();
        } }, '✎'))))),
    section('About', h('p', { class: 'muted small' }, `Storage: ${store.persistent ? 'saved on this device (IndexedDB)' : 'temporary – export backups!'} · Works offline once loaded.`)),
  );
}

export async function importFiles(app, asNewProject) {
  const files = await pickFiles('.bbmodel,.json,.png,.mcanim.json', true);
  if (!files.length) return;
  const warnings = [];
  let model = null, texture = null, anims = [], projectBackup = null;
  for (const f of files) {
    try {
      if (f.name.endsWith('.png')) { texture = await readDataUrl(f); continue; }
      const txt = await readText(f);
      const json = JSON.parse(txt);
      if (f.name.endsWith('.mcanim.json') || (json.model && json.animations && json.activeAnimId)) { projectBackup = json; continue; }
      if (json.meta && json.elements) { const r = parseBBModel(json); model = r.model; anims.push(...r.animations); warnings.push(...r.warnings); }
      else if (json['minecraft:geometry'] || Object.keys(json).some((k) => k.startsWith('geometry.'))) { const r = parseGeo(json); model = r.model; warnings.push(...r.warnings); }
      else if (json.animations) { const r = parseAnimationFile(json, (model || app.model).bones.map((b) => b.name)); anims.push(...r.animations); warnings.push(...r.warnings); }
      else warnings.push(`${f.name}: not a recognised file.`);
    } catch (e) { warnings.push(`${f.name}: ${e.message}`); }
  }
  if (projectBackup) {
    projectBackup.id = uid('proj');
    await app.persist(); await app.openProject(projectBackup);
    toast(`Restored project “${projectBackup.name}”`);
    return;
  }
  if (texture) {
    const img = await new Promise((res) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => res(null); i.src = texture; });
    const tex = { dataUrl: texture, width: img?.width || 64, height: img?.height || 64, name: 'texture.png' };
    if (model) model.texture = model.texture || tex; else app.commit('Load texture', () => { app.model.texture = tex; });
  }
  if (model) {
    if (asNewProject) {
      await app.persist();
      const p = { id: uid('proj'), name: model.name, model, animations: anims.length ? anims : [createAnimation('new_animation', 1)], createdAt: Date.now(), updatedAt: Date.now() };
      p.activeAnimId = p.animations[0].id;
      await app.openProject(p);
    } else {
      app.commit('Import model', () => { app.project.model = model; if (anims.length) { for (const a of anims) app.addAnimation(a, false); app.project.activeAnimId = anims[0].id; } });
      app.invalidateRig(); app.viewer.setModel(app.model); app.refresh();
    }
  } else if (anims.length) {
    app.commit('Import animations', () => { for (const a of anims) app.addAnimation(a, false); app.project.activeAnimId = anims[0].id; });
  }
  if (texture && !model) { app.viewer.setModel(app.model); app.refresh(); }
  const msg = `${model ? `Model “${model.name}” (${model.bones.length} bones)` : ''}${anims.length ? `${model ? ' + ' : ''}${anims.length} animation(s)` : ''}${texture ? ' + texture' : ''}`;
  if (msg) toast(`Imported ${msg}`);
  if (model && !model.texture) warnings.push('No texture yet – import the PNG to see it textured (the model shows in colours meanwhile).');
  const rig = resolveRig(app.model);
  if (model && rig.score < 0.5) warnings.push('Some body parts weren’t recognised automatically – set them under Project → Body parts so presets work.');
  if (warnings.length) modal('Import notes', h('ul', {}, warnings.map((w) => h('li', {}, w))));
}

// ======================= HELP / ONBOARDING =======================
function help() {
  modal('How it works', h('div', { class: 'help' },
    h('ol', {},
      h('li', {}, h('b', {}, 'Create: '), 'pick a preset or describe a move (“a fast three-hit combo”). It becomes real keyframes you can edit.'),
      h('li', {}, h('b', {}, 'Preview: '), 'press ▶. Drag the timeline to scrub. One finger orbits the camera, two fingers zoom/pan.'),
      h('li', {}, h('b', {}, 'Tweak: '), 'tap a body part, drag the rings (or the Pose sliders). The change is keyed at the playhead.'),
      h('li', {}, h('b', {}, 'Timing: '), 'drag ◆ diamonds on the timeline. Tap one to change its easing, copy or delete it.'),
      h('li', {}, h('b', {}, 'Export: '), 'the Export tab builds a GeckoLib-ready asset folder for your mod.')),
    h('p', { class: 'muted small' }, 'Keyboard: Space play · ←/→ step · K key · Ctrl+Z / Ctrl+Y undo/redo · Del delete key.')));
}

async function onboarding(app) {
  const r = await modal('Welcome to Minecraft Animation Studio 👋', h('div', { class: 'help' },
    h('p', {}, 'Make Minecraft mod animations on your phone – no animation experience needed.'),
    h('ol', {},
      h('li', {}, 'Meet ', h('b', {}, 'Blocky'), ', your sample character (or import your own .bbmodel later).'),
      h('li', {}, 'Tap a preset like ', h('b', {}, 'Punch'), ' – or type what you want.'),
      h('li', {}, 'Watch it, tweak it, then export it for GeckoLib.'))),
    [{ label: 'Let’s go – make a punch!', primary: true, value: 'go' }, { label: 'Just explore', value: 'skip' }]);
  await store.setSetting('onboarded', true);
  if (r === 'go') { app.setTab('create', true); app.applySteps([{ block: 'punch', opts: { side: 'right' } }], { name: 'punch' }); toast('Your first animation! Try “Make it faster” or “More power”.', 'info', 4500); }
}

export const PANELS = { create, pose, anims, export: exportPanel, project, keyBar, bonePicker, help, onboarding };
