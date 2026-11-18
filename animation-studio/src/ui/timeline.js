// Touch-friendly timeline: drag the ruler to scrub, tap a diamond to select a key, drag it to
// retime. The "All" row moves whole poses; bone rows move just that bone's keys.
import { h } from './dom.js';
import { keyTimes, snapTime } from '../core/animation.js';

const LABEL_W = 92;

export class Timeline {
  constructor(el, app) {
    this.el = el; this.app = app; this.zoom = 1;
    this.scroll = h('div', { class: 'tl-scroll' });
    this.inner = h('div', { class: 'tl-inner' });
    this.playhead = h('div', { class: 'tl-playhead' }, h('div', { class: 'tl-playhead-cap' }));
    this.scroll.append(this.inner);
    el.append(this.scroll);
    new ResizeObserver(() => this.render()).observe(this.scroll);
    // pinch zoom
    const pts = new Map(); let pinch0 = 0, zoom0 = 1;
    this.scroll.addEventListener('pointerdown', (e) => { pts.set(e.pointerId, e.clientX); if (pts.size === 2) { const [a, b] = [...pts.values()]; pinch0 = Math.abs(a - b); zoom0 = this.zoom; } });
    this.scroll.addEventListener('pointermove', (e) => {
      if (!pts.has(e.pointerId)) return; pts.set(e.pointerId, e.clientX);
      if (pts.size === 2 && pinch0 > 10) { const [a, b] = [...pts.values()]; this.setZoom(zoom0 * Math.abs(a - b) / pinch0); }
    });
    const up = (e) => pts.delete(e.pointerId);
    this.scroll.addEventListener('pointerup', up); this.scroll.addEventListener('pointercancel', up);
  }

  get pps() {
    const anim = this.app.anim;
    const w = Math.max(200, this.scroll.clientWidth - LABEL_W - 28);
    return Math.max(40, (w / Math.max(0.25, anim?.length || 1)) * this.zoom);
  }
  setZoom(z) { this.zoom = Math.min(8, Math.max(1, z)); this.render(); }
  x(t) { return LABEL_W + 12 + t * this.pps; }
  tAt(clientX) {
    const r = this.inner.getBoundingClientRect();
    return Math.max(0, (clientX - r.left - LABEL_W - 12) / this.pps);
  }

  rows() {
    const { anim, model, selection } = this.app;
    const animated = model.bones.map((b) => b.name).filter((n) => anim.tracks[n]);
    if (selection.bone && !animated.includes(selection.bone)) animated.unshift(selection.bone);
    for (const n of Object.keys(anim.tracks)) if (!animated.includes(n)) animated.push(n); // keys on bones not in model
    return animated;
  }

  render() {
    const { anim, selection } = this.app;
    this.inner.innerHTML = '';
    if (!anim) return;
    const pps = this.pps;
    const width = LABEL_W + 24 + anim.length * pps + 40;
    this.inner.style.width = `${width}px`;

    // ruler
    const ruler = h('div', { class: 'tl-row tl-ruler' }, h('div', { class: 'tl-label' }, 'time'));
    const step = pps > 220 ? 0.05 : pps > 110 ? 0.1 : pps > 50 ? 0.25 : 0.5;
    for (let t = 0; t <= anim.length + 1e-6; t = Math.round((t + step) * 1000) / 1000) {
      const major = Math.abs(t / (step * (step < 0.1 ? 4 : step < 0.25 ? 5 : 2)) - Math.round(t / (step * (step < 0.1 ? 4 : step < 0.25 ? 5 : 2)))) < 1e-6;
      ruler.append(h('div', { class: `tl-tick ${major ? 'major' : ''}`, style: { left: `${this.x(t)}px` } }, major ? `${+t.toFixed(2)}s` : ''));
    }
    const endHandle = h('div', { class: 'tl-end', style: { left: `${this.x(anim.length)}px` }, title: 'Drag to change the animation length', 'aria-label': 'Animation length handle' }, '⇔');
    ruler.append(endHandle);
    this.inner.append(ruler);
    this.bindScrub(ruler);
    this.bindEnd(endHandle);

    // sections (from presets / assistant)
    if (anim.sections?.length) {
      const sec = h('div', { class: 'tl-row tl-sections' }, h('div', { class: 'tl-label' }, 'moves'));
      for (const s of anim.sections) {
        const el = h('button', {
          class: `tl-section ${this.app.selection.section === s.id ? 'sel' : ''}`,
          style: { left: `${this.x(s.start)}px`, width: `${Math.max(18, (s.end - s.start) * pps - 2)}px` },
          onclick: () => this.app.selectSection(s.id),
        }, s.label);
        sec.append(el);
      }
      this.inner.append(sec);
    }

    // All row
    const allRow = h('div', { class: 'tl-row tl-all' }, h('div', { class: 'tl-label' }, 'All bones'));
    for (const t of keyTimes(anim)) allRow.append(this.diamond(null, t, selection.keyBone === null && selection.keyTime !== null && Math.abs(selection.keyTime - t) < 1e-4));
    this.inner.append(allRow);
    this.bindScrub(allRow, true);

    for (const bone of this.rows()) {
      const row = h('div', { class: `tl-row ${selection.bone === bone ? 'cur' : ''}` },
        h('button', { class: 'tl-label', onclick: () => this.app.selectBone(bone) }, bone));
      for (const t of keyTimes(anim, bone)) row.append(this.diamond(bone, t, selection.keyBone === bone && selection.keyTime !== null && Math.abs(selection.keyTime - t) < 1e-4));
      this.inner.append(row);
      this.bindScrub(row, true);
    }
    if (!Object.keys(anim.tracks).length) this.inner.append(h('div', { class: 'tl-empty' }, 'No keyframes yet. Pick a move on the Create tab, or pose a body part and tap “+ Key”.'));
    this.inner.append(this.playhead);
    this.updatePlayhead();
  }

  diamond(bone, t, selected) {
    const anim = this.app.anim;
    let ease = null;
    if (bone) for (const c of ['rotation', 'position', 'scale']) { const k = anim.tracks[bone]?.[c]?.find((x) => Math.abs(x.t - t) < 1e-4); if (k) { ease = k.easing; break; } }
    const d = h('div', { class: `tl-key ${selected ? 'sel' : ''} ${bone ? '' : 'all'} ease-${(ease || '').replace(/[^a-z]/gi, '')}`, style: { left: `${this.x(t)}px` }, role: 'button', 'aria-label': `Keyframe ${bone || 'all bones'} at ${t.toFixed(2)} seconds` });
    let sx = 0, moved = false, newT = t;
    d.addEventListener('pointerdown', (e) => { e.stopPropagation(); d.setPointerCapture(e.pointerId); sx = e.clientX; moved = false; newT = t; });
    d.addEventListener('pointermove', (e) => {
      if (!d.hasPointerCapture(e.pointerId)) return;
      const dx = e.clientX - sx;
      if (!moved && Math.abs(dx) < 6) return;
      moved = true;
      newT = Math.min(Math.max(0, snapTime(t + dx / this.pps)), Math.max(this.app.anim.length, t));
      d.style.left = `${this.x(newT)}px`;
      d.classList.add('dragging');
      this.app.showHint(`${newT.toFixed(2)}s`);
    });
    const up = (e) => {
      if (!d.hasPointerCapture(e.pointerId)) return;
      d.releasePointerCapture(e.pointerId);
      if (moved && Math.abs(newT - t) > 1e-4) this.app.moveKey(bone, t, newT);
      else this.app.selectKey(bone, t);
    };
    d.addEventListener('pointerup', up); d.addEventListener('pointercancel', up);
    return d;
  }

  bindScrub(row, onlyBackground = false) {
    let active = false;
    row.addEventListener('pointerdown', (e) => {
      if (onlyBackground && e.target !== row) return;
      if (e.target.closest('.tl-label') || e.target.closest('.tl-end') || e.target.closest('.tl-section')) return;
      active = true; row.setPointerCapture(e.pointerId);
      this.app.setTime(Math.min(this.app.anim.length, snapTime(this.tAt(e.clientX), 0.01)), true);
    });
    row.addEventListener('pointermove', (e) => { if (active) this.app.setTime(Math.min(this.app.anim.length, snapTime(this.tAt(e.clientX), 0.01)), true); });
    const end = () => { active = false; };
    row.addEventListener('pointerup', end); row.addEventListener('pointercancel', end);
  }

  bindEnd(handle) {
    let sx = 0, l0 = 0, active = false;
    handle.addEventListener('pointerdown', (e) => { e.stopPropagation(); handle.setPointerCapture(e.pointerId); sx = e.clientX; l0 = this.app.anim.length; active = true; });
    handle.addEventListener('pointermove', (e) => {
      if (!active) return;
      const L = Math.max(0.05, snapTime(l0 + (e.clientX - sx) / this.pps));
      handle.style.left = `${this.x(L)}px`;
      this.app.showHint(`Length ${L.toFixed(2)}s`);
      handle.dataset.len = L;
    });
    handle.addEventListener('pointerup', () => { if (active && handle.dataset.len) this.app.setLength(Number(handle.dataset.len)); active = false; });
  }

  updatePlayhead() {
    if (!this.app.anim) return;
    this.playhead.style.left = `${this.x(this.app.time)}px`;
  }

  ensureVisible() {
    const x = this.x(this.app.time);
    const s = this.scroll;
    if (x < s.scrollLeft + LABEL_W + 10 || x > s.scrollLeft + s.clientWidth - 20) s.scrollLeft = Math.max(0, x - s.clientWidth / 2);
  }
}
