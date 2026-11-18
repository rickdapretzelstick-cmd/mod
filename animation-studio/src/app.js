// Application state + wiring. Panels live in ui/panels.js, the viewport in ui/viewer.js,
// the timeline in ui/timeline.js. All edits go through commit()/beginEdit() for undo/redo.
import { Viewer } from './ui/viewer.js';
import { Timeline } from './ui/timeline.js';
import { h, toast, confirm, ask } from './ui/dom.js';
import { History } from './core/history.js';
import * as store from './core/storage.js';
import {
  createAnimation, cloneAnimation, sampleAnimation, setKey, removeKey, moveKeysAt, setEasingAt, keyTimes,
  copyPose, pastePose, snapTime, uid, CHANNELS, round, scaleTimeRange, findKey,
} from './core/animation.js';
import { createSampleModel, paintSampleTexture } from './core/sampleModel.js';
import { resolveRig, clampToLimits } from './core/rig.js';
import { compileSequence, regenerateSection } from './core/sequencer.js';
import { ADJUSTMENTS } from './core/adjust.js';
import { polish } from './core/assist.js';
import { PANELS } from './ui/panels.js';

const TEXTURE_TOKEN = '__texture__';

class App {
  constructor() {
    this.history = new History();
    this.time = 0; this.playing = false; this.loopPreview = true; this.speed = 1;
    this.mode = 'animate'; this.gizmoMode = 'rotate';
    this.tab = 'create'; this.panelOpen = true;
    this.selection = { bone: null, keyBone: null, keyTime: null, section: null };
    this.clipboard = null;
    this.project = null;
  }

  // ---------- getters ----------
  get model() { return this.project.model; }
  get anim() { if (!this.project) return null; return this.project.animations.find((a) => a.id === this.project.activeAnimId) || this.project.animations[0]; }
  get rig() { if (!this._rig || this._rigFor !== this.model) { this._rig = resolveRig(this.model); this._rigFor = this.model; } return this._rig; }
  invalidateRig() { this._rig = null; }

  // ---------- boot ----------
  async init() {
    this.buildShell();
    this.viewer = new Viewer(document.getElementById('viewport'), {
      onSelect: (b) => this.selectBone(b),
      onGizmoStart: () => this.beginEdit(this.mode === 'model' ? 'Edit rest pose' : 'Pose bone'),
      onGizmoChange: (v) => this.onGizmo(v),
      onGizmoEnd: () => this.endEdit(),
    });
    this.timeline = new Timeline(document.getElementById('timeline'), this);
    this.bindKeys();
    const lastId = await store.getSetting('lastProject', null);
    let proj = lastId ? await store.get('projects', lastId) : null;
    if (!proj) { proj = this.makeProject('My first animation', createSampleModel()); this.firstRun = true; }
    await this.openProject(proj, false);
    if (!store.persistent) toast('Storage is unavailable (private mode?) – export your work to keep it.', 'warn', 6000);
    store.requestPersistence();
    const seen = await store.getSetting('onboarded', false);
    if (!seen) PANELS.onboarding(this);
    requestAnimationFrame((t) => this.loop(t));
  }

  makeProject(name, model) {
    if (model.source === 'sample' && !model.texture) model.texture = paintSampleTexture(model, (w, hgt) => Object.assign(document.createElement('canvas'), { width: w, height: hgt }));
    const anim = createAnimation('idle', 2.2, 'loop');
    const p = { id: uid('proj'), name, model, animations: [anim], activeAnimId: anim.id, createdAt: Date.now(), updatedAt: Date.now() };
    // start with a gentle idle so the character is alive immediately
    const idle = compileSequence([{ block: 'idle' }], resolveRig(model), { name: 'idle' });
    idle.id = anim.id; p.animations = [idle];
    return p;
  }

  async openProject(proj, save = true) {
    this.project = proj;
    this.history.clear();
    this.invalidateRig();
    this.selection = { bone: null, keyBone: null, keyTime: null, section: null };
    this.time = 0; this.playing = false;
    this.viewer.setModel(proj.model);
    await store.setSetting('lastProject', proj.id);
    if (save) await this.persist();
    this.refresh();
  }

  // ---------- persistence / undo ----------
  // textures are big data URLs: keep them out of undo snapshots and reference them by id
  texRef(tex) {
    if (!tex) return null;
    this._texIds = this._texIds || new WeakMap(); this._texById = this._texById || new Map();
    if (!this._texIds.has(tex)) { const id = `${TEXTURE_TOKEN}${this._texById.size}`; this._texIds.set(tex, id); this._texById.set(id, tex); }
    return this._texIds.get(tex);
  }
  snapshot() {
    const m = { ...this.project.model, texture: this.texRef(this.project.model.texture) };
    return JSON.stringify({ model: m, animations: this.project.animations, activeAnimId: this.project.activeAnimId, name: this.project.name });
  }
  restore(s) {
    const d = JSON.parse(s);
    const modelChanged = this.texRef(this.project.model.texture) !== d.model.texture || JSON.stringify({ ...this.project.model, texture: null }) !== JSON.stringify({ ...d.model, texture: null });
    d.model.texture = d.model.texture ? this._texById.get(d.model.texture) || null : null;
    Object.assign(this.project, d);
    this.invalidateRig();
    if (modelChanged) this.viewer.setModel(this.project.model);
    if (this.selection.bone && !this.model.bones.find((b) => b.name === this.selection.bone)) this.selection.bone = null;
    this.selection.keyTime = null;
    this.time = Math.min(this.time, this.anim.length);
    this.saveSoon(); this.refresh();
  }
  commit(label, fn) {
    const before = this.snapshot();
    const r = fn();
    if (this.snapshot() !== before) { this.history.push(before, label); this.saveSoon(); }
    this.refresh();
    return r;
  }
  beginEdit(label) { this._editBefore = this.snapshot(); this._editLabel = label; }
  endEdit() {
    if (this._editBefore && this._editBefore !== this.snapshot()) { this.history.push(this._editBefore, this._editLabel); this.saveSoon(); }
    this._editBefore = null; this.refresh();
  }
  undo() { const e = this.history.undo(this.snapshot()); if (e) { this.restore(e.s); toast(`Undid: ${e.label}`); } }
  redo() { const e = this.history.redo(this.snapshot()); if (e) { this.restore(e.s); toast(`Redid: ${e.label}`); } }
  saveSoon() { clearTimeout(this._saveT); this.setSaved('saving'); this._saveT = setTimeout(() => this.persist(), 500); }
  async persist() {
    this.project.updatedAt = Date.now();
    try { await store.put('projects', this.project); this.setSaved('saved'); } catch (e) { console.error(e); this.setSaved('error'); toast('Saving failed – export a backup.', 'error'); }
  }
  setSaved(s) { const el = document.getElementById('saved'); if (el) { el.dataset.state = s; el.textContent = s === 'saved' ? '✓ Saved' : s === 'saving' ? '… Saving' : '⚠ Not saved'; } }

  // ---------- playback ----------
  loop(now) {
    const dt = this._last ? Math.min(0.1, (now - this._last) / 1000) : 0;
    this._last = now;
    if (this.playing && this.anim) {
      let t = this.time + dt * this.speed;
      const L = this.anim.length;
      if (t >= L) {
        if (this.loopPreview || this.anim.loop === 'loop') t = L > 0 ? t % L : 0;
        else { t = L; this.playing = false; this.updateTransport(); }
      }
      this.time = t;
      this.applyPose();
      this.timeline.updatePlayhead();
      this.updateTimeLabel();
    }
    requestAnimationFrame((t2) => this.loop(t2));
  }
  togglePlay() {
    if (!this.playing && this.time >= this.anim.length - 1e-3) this.time = 0;
    this.playing = !this.playing; this.updateTransport();
  }
  setTime(t, pause = false) {
    if (pause && this.playing) { this.playing = false; this.updateTransport(); }
    this.time = Math.max(0, Math.min(this.anim.length, t));
    this.applyPose(); this.timeline.updatePlayhead(); this.updateTimeLabel(); this.syncPosePanel();
  }
  applyPose() {
    if (!this.anim) return;
    if (this.mode === 'model') { this.viewer.applyPose({}); return; }
    this.viewer.applyPose(sampleAnimation(this.anim, this.time));
  }

  // ---------- selection ----------
  selectBone(name) {
    this.selection.bone = name;
    this.viewer.select(name, this.gizmoMode, !!name);
    if (name && this.tab !== 'pose' && this.panelOpen && this._autoPoseTab !== false) { /* stay on current tab */ }
    this.renderChrome(); this.timeline.render(); this.renderPanel();
  }
  selectKey(bone, t) {
    this.selection.keyBone = bone; this.selection.keyTime = t;
    if (bone) { this.selection.bone = bone; this.viewer.select(bone, this.gizmoMode); }
    this.setTime(t, true);
    this.timeline.render(); this.renderKeyBar(); this.renderChrome();
  }
  clearKeySel() { this.selection.keyTime = null; this.selection.keyBone = null; this.renderKeyBar(); this.timeline.render(); }
  selectSection(id) {
    this.selection.section = this.selection.section === id ? null : id;
    const s = this.anim.sections.find((x) => x.id === id);
    if (s && this.selection.section) this.setTime(s.start, true);
    this.timeline.render();
    if (this.tab !== 'create' || !this.panelOpen) this.setTab('create', true); else this.renderPanel();
  }
  get selectedSection() { return this.anim.sections?.find((s) => s.id === this.selection.section) || null; }

  // ---------- key editing ----------
  bonesFor(boneOrNull) { return boneOrNull ? [boneOrNull] : Object.keys(this.anim.tracks); }
  moveKey(bone, from, to) {
    this.commit('Move keyframe', () => { moveKeysAt(this.anim, this.bonesFor(bone), from, to); if (to > this.anim.length) this.anim.length = to; this.anim.sections = []; });
    this.selection.keyBone = bone; this.selection.keyTime = to; this.setTime(to, true); this.timeline.render(); this.renderKeyBar();
  }
  deleteSelectedKey() {
    const { keyBone, keyTime } = this.selection;
    if (keyTime === null) return;
    this.commit('Delete keyframe', () => { for (const b of this.bonesFor(keyBone)) removeKey(this.anim, b, null, keyTime); });
    this.clearKeySel();
  }
  setEasing(easing, args) {
    const { keyBone, keyTime } = this.selection;
    if (keyTime === null) return;
    this.commit('Change easing', () => setEasingAt(this.anim, this.bonesFor(keyBone), keyTime, easing, args));
    this.renderKeyBar();
  }
  copyKey() {
    const bones = this.selection.keyTime !== null ? this.bonesFor(this.selection.keyBone) : (this.selection.bone ? [this.selection.bone] : this.model.bones.map((b) => b.name));
    const t = this.selection.keyTime ?? this.time;
    this.clipboard = copyPose(this.anim, bones, t);
    toast(`Copied ${bones.length === 1 ? bones[0] : 'the full pose'} at ${t.toFixed(2)}s`);
    this.renderKeyBar();
  }
  pasteKey(mirror = false) {
    if (!this.clipboard) return toast('Copy a pose first.');
    let pose = this.clipboard;
    if (mirror) pose = this.mirrorBonePose(pose);
    const t = snapTime(this.time);
    this.commit(mirror ? 'Paste mirrored pose' : 'Paste pose', () => pastePose(this.anim, pose, t));
    toast(`Pasted at ${t.toFixed(2)}s`);
  }
  mirrorBonePose(pose) {
    // swap left/right body parts and flip the sideways axes (Y/Z rotation, X position)
    const rig = this.rig, out = {};
    for (const [b, v] of Object.entries(pose)) {
      const role = rig.roleOf(b);
      const other = role && /^[rl][A-Z]/.test(role) ? (role[0] === 'r' ? 'l' : 'r') + role.slice(1) : null;
      const target = (other && rig.bone(other)) || b;
      out[target] = { rotation: [v.rotation[0], -v.rotation[1], -v.rotation[2]], position: [-v.position[0], v.position[1], v.position[2]], scale: v.scale.slice() };
    }
    return out;
  }
  keyNow(bones = null) {
    const t = snapTime(this.time);
    const list = bones || (this.selection.bone ? [this.selection.bone] : this.model.bones.map((b) => b.name));
    this.commit('Add keyframe', () => {
      const pose = copyPose(this.anim, list, this.time);
      for (const [b, p] of Object.entries(pose)) for (const c of CHANNELS) {
        const has = this.anim.tracks[b]?.[c]?.length;
        if (has || c === 'rotation' || bones) setKey(this.anim, b, c, t, p[c]);
      }
    });
    toast(`Keyframe added at ${t.toFixed(2)}s`);
  }
  // value change from pose panel / gizmo
  setBoneChannel(bone, channel, v, live = false) {
    if (this.mode === 'model') {
      const b = this.model.bones.find((x) => x.name === bone);
      if (channel === 'rotation') b.rotation = v.map((n) => round(n, 2));
      else if (channel === 'position') b.pivot = v.map((n) => round(n, 2));
      this.viewer.setModel(this.model); this.viewer.select(bone, this.gizmoMode);
      return;
    }
    const t = snapTime(this.time);
    setKey(this.anim, bone, channel, t, v);
    if (t > this.anim.length) this.anim.length = t;
    if (!live) this.refresh(); else { this.applyPose(); }
  }
  onGizmo(v) {
    if (!v) return;
    const bone = v.bone;
    if (this.mode === 'model') {
      const g = this.viewer.bones.get(bone);
      const b = this.model.bones.find((x) => x.name === bone);
      if (this.gizmoMode === 'rotate') b.rotation = [g.rotation.x, g.rotation.y, g.rotation.z].map((n) => round(n * 180 / Math.PI, 2));
      g.userData.rest = [g.rotation.x, g.rotation.y, g.rotation.z];
      this.syncPosePanel();
      return;
    }
    const ch = this.gizmoMode === 'move' ? 'position' : this.gizmoMode === 'scale' ? 'scale' : 'rotation';
    const t = snapTime(this.time);
    setKey(this.anim, bone, ch, t, v[ch]);
    this.syncPosePanel();
    this._dirtyTimeline = true;
  }
  setLength(L) {
    this.commit('Change length', () => { this.anim.length = round(L, 4); });
    this.setTime(Math.min(this.time, L));
  }

  // ---------- animations ----------
  addAnimation(anim, select = true) {
    let name = anim.name, i = 2;
    while (this.project.animations.some((a) => a.name === name)) name = `${anim.name}_${i++}`;
    anim.name = name;
    this.project.animations.push(anim);
    if (select) { this.project.activeAnimId = anim.id; this.time = 0; this.selection.section = null; this.selection.keyTime = null; }
  }
  selectAnimation(id) { this.project.activeAnimId = id; this.time = 0; this.selection.section = null; this.selection.keyTime = null; this.refresh(); this.saveSoon(); }

  // Create from motion blocks: mode 'new' | 'append' | 'replace'
  applySteps(steps, { name, mode = 'new', play = true } = {}) {
    const anim = compileSequence(steps, this.rig, { name });
    // show the held weapon for weapon moves (sample model keeps it hidden for unarmed moves)
    const item = this.model.bones.find((b) => b.name === this.rig.bone('rItem'));
    const armed = steps.some((s) => /^sword_|spin_attack/.test(s.block));
    if (item && armed && item.hidden) { item.hidden = false; this.viewer.setBoneVisible(item.name, true); }
    this.commit(mode === 'append' ? 'Append move' : 'Create animation', () => {
      if (mode === 'append' && this.anim) this.appendAnimation(this.anim, anim);
      else if (mode === 'replace' && this.anim) { anim.id = this.anim.id; anim.name = this.anim.name; Object.assign(this.anim, anim); }
      else this.addAnimation(anim);
    });
    this.time = 0; this.playing = play; this.updateTransport();
    const missing = ['head', 'body', 'rArm', 'lArm', 'rLeg', 'lLeg'].filter((r) => !this.rig.bone(r));
    if (missing.length >= 3) toast('Some body parts weren’t recognised – set them in Project → Body parts.', 'warn', 5000);
    return this.anim;
  }
  appendAnimation(target, src) {
    const offset = target.length;
    for (const [b, tr] of Object.entries(src.tracks)) for (const c of CHANNELS) for (const k of tr[c]) {
      if (k.t === 0 && findKey(target.tracks[b]?.[c] || [], offset) >= 0) continue;
      setKey(target, b, c, offset + k.t, k.v, k.easing);
    }
    for (const s of src.sections || []) target.sections.push({ ...s, id: uid('sec'), start: s.start + offset, end: s.end + offset });
    target.length = round(offset + src.length, 4);
    if (target.loop === 'loop' && src.loop !== 'loop') target.loop = 'once';
  }
  runAdjust(id) {
    const sec = this.selectedSection;
    let msg;
    this.commit(ADJUSTMENTS[id].label, () => { msg = ADJUSTMENTS[id].run(this.anim, sec, this.rig); });
    toast(`${sec ? `“${sec.label}”: ` : ''}${msg}`);
    return msg;
  }
  regenerate(sectionId, opts) {
    this.commit('Regenerate move', () => regenerateSection(this.anim, sectionId, opts, this.rig));
    const s = this.anim.sections.find((x) => x.id === sectionId);
    if (s) { this.time = s.start; this.playing = true; this.updateTransport(); }
  }
  polishMotion() {
    let notes;
    this.commit('Polish motion', () => { notes = polish(this.anim, this.rig); });
    return notes;
  }

  // ---------- UI shell ----------
  buildShell() {
    const root = document.getElementById('app');
    root.innerHTML = '';
    const tabs = [['create', '✨', 'Create'], ['pose', '🦴', 'Pose'], ['anims', '🎞️', 'Anims'], ['export', '📦', 'Export'], ['project', '📁', 'Project']];
    root.append(
      h('header', { class: 'topbar' },
        h('button', { class: 'icon-btn', id: 'projName', onclick: async () => { const n = await ask('Project name', this.project.name); if (n) this.commit('Rename project', () => { this.project.name = n; }); } }, ''),
        h('span', { id: 'saved', class: 'saved' }),
        h('div', { class: 'seg', role: 'tablist', 'aria-label': 'Editing mode' },
          h('button', { id: 'modeAnim', onclick: () => this.setMode('animate') }, 'Animate'),
          h('button', { id: 'modeModel', onclick: () => this.setMode('model') }, 'Model')),
        h('button', { class: 'icon-btn', id: 'undoBtn', 'aria-label': 'Undo', onclick: () => this.undo() }, '↶'),
        h('button', { class: 'icon-btn', id: 'redoBtn', 'aria-label': 'Redo', onclick: () => this.redo() }, '↷'),
        h('button', { class: 'icon-btn', 'aria-label': 'Help', onclick: () => PANELS.help(this) }, '?')),
      h('main', { class: 'work', id: 'work' },
        h('section', { class: 'viewport-wrap' },
          h('div', { id: 'viewport' }),
          h('div', { class: 'vp-overlay top' },
            h('button', { class: 'chip', id: 'boneChip', onclick: () => PANELS.bonePicker(this) }, 'Tap a body part'),
            h('div', { class: 'vp-tools' },
              h('button', { class: 'chip icon', id: 'gzRot', title: 'Rotate', onclick: () => this.setGizmoMode('rotate') }, '⟳'),
              h('button', { class: 'chip icon', id: 'gzMove', title: 'Move', onclick: () => this.setGizmoMode('move') }, '✥'),
              h('button', { class: 'chip icon', title: 'Re-center camera', onclick: () => this.viewer.frame() }, '⌖'))),
          h('div', { class: 'vp-hint', id: 'vpHint' })),
        h('section', { class: 'timeline-wrap', id: 'timelineWrap' },
          h('div', { class: 'transport' },
            h('button', { class: 'tbtn', 'aria-label': 'Go to start', onclick: () => this.setTime(0, true) }, '⏮'),
            h('button', { class: 'tbtn play', id: 'playBtn', 'aria-label': 'Play', onclick: () => this.togglePlay() }, '▶'),
            h('span', { class: 'tlabel', id: 'timeLabel' }),
            h('button', { class: 'tbtn small', id: 'loopBtn', title: 'Loop preview', onclick: () => { this.loopPreview = !this.loopPreview; this.updateTransport(); } }, '🔁'),
            h('button', { class: 'tbtn small', id: 'speedBtn', title: 'Preview speed', onclick: () => { this.speed = this.speed === 1 ? 0.5 : this.speed === 0.5 ? 0.25 : 1; this.updateTransport(); } }, '1×'),
            h('button', { class: 'tbtn small zoom', title: 'Zoom out', onclick: () => this.timeline.setZoom(this.timeline.zoom / 1.5) }, '−'),
            h('button', { class: 'tbtn small zoom', title: 'Zoom in', onclick: () => this.timeline.setZoom(this.timeline.zoom * 1.5) }, '+'),
            h('button', { class: 'tbtn key', id: 'keyBtn', onclick: () => this.keyNow() }, '+ Key'),
            h('button', { class: 'tbtn small', id: 'tlToggle', title: 'Show/hide timeline', onclick: () => { document.getElementById('work').classList.toggle('tl-collapsed'); this.viewer.resize(); } }, '▾')),
          h('div', { class: 'keybar', id: 'keyBar' }),
          h('div', { id: 'timeline', class: 'timeline' })),
        h('section', { class: 'panel', id: 'panel' })),
      h('nav', { class: 'tabs', role: 'tablist' }, tabs.map(([id, icon, label]) => h('button', { class: 'tab', id: `tab-${id}`, role: 'tab', onclick: () => this.setTab(id) }, h('span', { class: 'tab-icon' }, icon), h('span', {}, label)))),
    );
  }
  setTab(id, forceOpen = false) {
    if (this.tab === id && this.panelOpen && !forceOpen) this.panelOpen = false;
    else { this.tab = id; this.panelOpen = true; }
    document.getElementById('work').classList.toggle('panel-collapsed', !this.panelOpen);
    this.viewer.resize();
    this.renderChrome(); this.renderPanel();
  }
  setMode(m) {
    this.mode = m; this.playing = false;
    if (m === 'model') { this.gizmoMode = 'rotate'; toast('Model mode: changes edit the rest pose and bones, not the animation.'); }
    this.applyPose(); this.viewer.select(this.selection.bone, this.gizmoMode);
    this.refresh();
  }
  setGizmoMode(m) { this.gizmoMode = m; this.viewer.select(this.selection.bone, m); this.renderChrome(); this.renderPanel(); }
  showHint(text) { const el = document.getElementById('vpHint'); el.textContent = text; el.classList.add('show'); clearTimeout(this._hintT); this._hintT = setTimeout(() => el.classList.remove('show'), 1200); }

  refresh() {
    if (!this.project) return;
    this.applyPose();
    this.renderChrome();
    this.timeline.render();
    this.renderKeyBar();
    this.renderPanel();
    this.updateTransport();
  }
  renderChrome() {
    document.getElementById('projName').textContent = `📁 ${this.project.name}`;
    document.getElementById('undoBtn').disabled = !this.history.canUndo;
    document.getElementById('redoBtn').disabled = !this.history.canRedo;
    document.getElementById('modeAnim').classList.toggle('on', this.mode === 'animate');
    document.getElementById('modeModel').classList.toggle('on', this.mode === 'model');
    document.getElementById('app').dataset.mode = this.mode;
    const b = this.selection.bone;
    const role = b ? this.rig.roleOf(b) : null;
    document.getElementById('boneChip').textContent = b ? `🦴 ${b}${role ? '' : ''} ▾` : '👆 Tap a body part';
    document.getElementById('gzRot').classList.toggle('on', this.gizmoMode === 'rotate');
    document.getElementById('gzMove').classList.toggle('on', this.gizmoMode === 'move');
    for (const t of document.querySelectorAll('.tab')) t.classList.toggle('on', t.id === `tab-${this.tab}` && this.panelOpen);
  }
  updateTransport() {
    const pb = document.getElementById('playBtn'); if (!pb) return;
    pb.textContent = this.playing ? '⏸' : '▶'; pb.setAttribute('aria-label', this.playing ? 'Pause' : 'Play');
    document.getElementById('loopBtn').classList.toggle('on', this.loopPreview);
    document.getElementById('speedBtn').textContent = `${this.speed}×`;
    document.getElementById('keyBtn').disabled = this.mode === 'model';
    this.updateTimeLabel();
    if (!this.playing) this.syncPosePanel();
  }
  updateTimeLabel() { const el = document.getElementById('timeLabel'); if (el && this.anim) el.textContent = `${this.time.toFixed(2)} / ${this.anim.length.toFixed(2)}s`; }
  renderPanel() { const el = document.getElementById('panel'); if (!this.panelOpen) return; PANELS[this.tab](this, el); }
  renderKeyBar() { PANELS.keyBar(this, document.getElementById('keyBar')); }
  syncPosePanel() {
    if (this._dirtyTimeline && !this.viewer.gizmo.dragging) { this._dirtyTimeline = false; this.timeline.render(); }
    if (this.tab === 'pose' && this.panelOpen && this._poseSync) this._poseSync();
  }

  bindKeys() {
    window.addEventListener('keydown', (e) => {
      if (e.target.closest('input, textarea, select')) return;
      const mod = e.ctrlKey || e.metaKey;
      if (mod && e.key.toLowerCase() === 'z') { e.preventDefault(); e.shiftKey ? this.redo() : this.undo(); }
      else if (mod && e.key.toLowerCase() === 'y') { e.preventDefault(); this.redo(); }
      else if (mod && e.key.toLowerCase() === 'c') { this.copyKey(); }
      else if (mod && e.key.toLowerCase() === 'v') { this.pasteKey(); }
      else if (e.key === ' ') { e.preventDefault(); this.togglePlay(); }
      else if (e.key === 'Delete' || e.key === 'Backspace') this.deleteSelectedKey();
      else if (e.key.toLowerCase() === 'k') this.keyNow();
      else if (e.key === 'ArrowLeft') this.setTime(snapTime(this.time - 0.05), true);
      else if (e.key === 'ArrowRight') this.setTime(snapTime(this.time + 0.05), true);
    });
  }
}

export const app = new App();
window.__app = app; // handy for debugging and automated tests
app.init().catch((e) => { console.error(e); document.getElementById('app').append(h('pre', { class: 'fatal' }, `Failed to start: ${e.message}`)); });

if ('serviceWorker' in navigator && !window.claude && location.protocol !== 'file:' && !location.search.includes('nosw')) {
  navigator.serviceWorker.register('./sw.js').catch(() => {});
}
