// three.js viewport: builds Minecraft-style cube meshes from the model, applies sampled poses,
// handles tap-to-select and the rotate/move gizmo. Touch: 1 finger orbit, 2 fingers pinch/pan.
import * as THREE from 'three';
import { OrbitControls } from '../../vendor/OrbitControls.js';
import { TransformControls } from '../../vendor/TransformControls.js';
import { modelBounds, FACE_NAMES } from '../core/model.js';

const D2R = Math.PI / 180, R2D = 180 / Math.PI;

// Corner order per face: TL, TR, BL, BR as seen from outside (matches Blockbench UV orientation)
function faceCorners(f, a, b) {
  const [x1, y1, z1] = a, [x2, y2, z2] = b;
  switch (f) {
    case 'north': return [[x2, y2, z1], [x1, y2, z1], [x2, y1, z1], [x1, y1, z1]];
    case 'south': return [[x1, y2, z2], [x2, y2, z2], [x1, y1, z2], [x2, y1, z2]];
    case 'east': return [[x2, y2, z2], [x2, y2, z1], [x2, y1, z2], [x2, y1, z1]];
    case 'west': return [[x1, y2, z1], [x1, y2, z2], [x1, y1, z1], [x1, y1, z2]];
    case 'up': return [[x1, y2, z1], [x2, y2, z1], [x1, y2, z2], [x2, y2, z2]];
    case 'down': return [[x1, y1, z2], [x2, y1, z2], [x1, y1, z1], [x2, y1, z1]];
  }
}
const NORMALS = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };

function cubeGeometry(cube, uvW, uvH, offset) {
  const inf = cube.inflate || 0;
  const a = [Math.min(cube.from[0], cube.to[0]) - inf, Math.min(cube.from[1], cube.to[1]) - inf, Math.min(cube.from[2], cube.to[2]) - inf].map((n, i) => n - offset[i]);
  const b = [Math.max(cube.from[0], cube.to[0]) + inf, Math.max(cube.from[1], cube.to[1]) + inf, Math.max(cube.from[2], cube.to[2]) + inf].map((n, i) => n - offset[i]);
  const pos = [], nor = [], uv = [], idx = [];
  for (const f of FACE_NAMES) {
    const face = cube.faces[f];
    if (!face) continue;
    const c = faceCorners(f, a, b);
    const [u1, v1, u2, v2] = face.uv;
    let uvs = [[u1, v1], [u2, v1], [u1, v2], [u2, v2]];
    const rot = ((face.rotation || 0) / 90) % 4;
    for (let r = 0; r < rot; r++) uvs = [uvs[2], uvs[0], uvs[3], uvs[1]];
    const base = pos.length / 3;
    for (let i = 0; i < 4; i++) {
      pos.push(...c[i]); nor.push(...NORMALS[f]);
      uv.push(uvs[i][0] / uvW, 1 - uvs[i][1] / uvH);
    }
    idx.push(base, base + 2, base + 1, base + 2, base + 3, base + 1);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  g.setIndex(idx);
  g.computeBoundingSphere();
  return g;
}

export class Viewer {
  constructor(container, { onSelect, onGizmoStart, onGizmoChange, onGizmoEnd } = {}) {
    this.container = container;
    this.cb = { onSelect, onGizmoStart, onGizmoChange, onGizmoEnd };
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'low-power' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    container.appendChild(this.renderer.domElement);
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x1d2433);
    this.camera = new THREE.PerspectiveCamera(40, 1, 0.05, 200);
    this.camera.position.set(2.6, 2.2, -3.6);
    this.scene.add(new THREE.HemisphereLight(0xdfe8ff, 0x3a3020, 1.6));
    const sun = new THREE.DirectionalLight(0xffffff, 1.6); sun.position.set(3, 6, -4); this.scene.add(sun);
    const fill = new THREE.DirectionalLight(0x9fb6ff, 0.5); fill.position.set(-4, 2, 3); this.scene.add(fill);
    const grid = new THREE.GridHelper(8, 8, 0x5a6a8a, 0x323c52); this.scene.add(grid);
    const shadow = new THREE.Mesh(new THREE.CircleGeometry(0.45, 24), new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.25 }));
    shadow.rotation.x = -Math.PI / 2; shadow.position.y = 0.002; this.scene.add(shadow); this.shadow = shadow;
    // compass hint: front of the model (-Z)
    const arrow = new THREE.ArrowHelper(new THREE.Vector3(0, 0, -1), new THREE.Vector3(0, 0.01, -0.6), 0.5, 0x6fd3ff, 0.12, 0.08);
    this.scene.add(arrow);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true; this.controls.dampingFactor = 0.12;
    this.controls.target.set(0, 1, 0); this.controls.minDistance = 0.8; this.controls.maxDistance = 30;
    this.controls.touches = { ONE: THREE.TOUCH.ROTATE, TWO: THREE.TOUCH.DOLLY_PAN };
    this.controls.addEventListener('change', () => (this.dirty = true));

    this.gizmo = new TransformControls(this.camera, this.renderer.domElement);
    this.gizmo.setSpace('local'); this.gizmo.setMode('rotate'); this.gizmo.setSize(1.15);
    this.gizmoHelper = this.gizmo.getHelper ? this.gizmo.getHelper() : this.gizmo;
    this.scene.add(this.gizmoHelper);
    this.gizmo.addEventListener('dragging-changed', (e) => {
      this.controls.enabled = !e.value;
      if (e.value) this.cb.onGizmoStart?.(); else this.cb.onGizmoEnd?.();
    });
    this.gizmo.addEventListener('objectChange', () => { this.dirty = true; this.cb.onGizmoChange?.(this.readBone(this.selected)); });
    this.gizmo.addEventListener('change', () => (this.dirty = true));

    this.modelRoot = new THREE.Group(); this.modelRoot.scale.setScalar(1 / 16); this.scene.add(this.modelRoot);
    this.bones = new Map(); this.meshes = []; this.selected = null; this.dirty = true;

    // tap-to-select
    const el = this.renderer.domElement;
    let down = null;
    el.addEventListener('pointerdown', (e) => { if (e.isPrimary) down = { x: e.clientX, y: e.clientY, t: performance.now() }; });
    el.addEventListener('pointerup', (e) => {
      if (!down || !e.isPrimary) return;
      const moved = Math.hypot(e.clientX - down.x, e.clientY - down.y);
      const quick = performance.now() - down.t < 450;
      down = null;
      if (moved < 8 && quick && !this.gizmo.dragging) this.pick(e);
    });
    this.resizeObs = new ResizeObserver(() => this.resize()); this.resizeObs.observe(container);
    this.resize();
    const loop = () => {
      const moved = this.controls.update();
      if (moved || this.dirty) { this.renderer.render(this.scene, this.camera); this.dirty = false; }
      requestAnimationFrame(loop);
    };
    requestAnimationFrame(loop);
  }

  resize() {
    const w = this.container.clientWidth || 1, h = this.container.clientHeight || 1;
    this.renderer.setSize(w, h, false);
    this.renderer.domElement.style.width = '100%'; this.renderer.domElement.style.height = '100%';
    this.camera.aspect = w / h; this.camera.updateProjectionMatrix(); this.dirty = true;
  }

  setModel(model) {
    this.gizmo.detach();
    for (const m of this.meshes) { m.geometry.dispose(); }
    this.modelRoot.clear(); this.bones.clear(); this.meshes = [];
    this.model = model;
    let tex = null;
    if (model.texture?.dataUrl) {
      tex = new THREE.TextureLoader().load(model.texture.dataUrl, () => (this.dirty = true));
      tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.NearestFilter; tex.colorSpace = THREE.SRGBColorSpace; tex.generateMipmaps = false;
    }
    const [uvW, uvH] = model.uvSize || [64, 64];
    const byName = new Map(model.bones.map((b) => [b.name, b]));
    const make = (b) => {
      if (this.bones.has(b.name)) return this.bones.get(b.name);
      const parent = b.parent && byName.has(b.parent) ? make(byName.get(b.parent)) : null;
      const g = new THREE.Group(); g.name = b.name;
      const pp = parent ? byName.get(b.parent).pivot : [0, 0, 0];
      g.userData.base = new THREE.Vector3(b.pivot[0] - pp[0], b.pivot[1] - pp[1], b.pivot[2] - pp[2]);
      g.userData.rest = b.rotation.map((n) => n * D2R);
      g.rotation.order = 'ZYX';
      g.position.copy(g.userData.base);
      g.rotation.set(...g.userData.rest);
      g.visible = !b.hidden;
      const hue = ([...b.name].reduce((h, c) => h + c.charCodeAt(0), 0) * 37) % 360;
      const mat = tex
        ? new THREE.MeshLambertMaterial({ map: tex, alphaTest: 0.05, side: THREE.DoubleSide, transparent: false })
        : new THREE.MeshLambertMaterial({ color: new THREE.Color(`hsl(${hue}, 45%, 60%)`), side: THREE.DoubleSide });
      g.userData.mat = mat;
      for (const c of b.cubes) {
        const holder = new THREE.Group();
        holder.position.set(c.origin[0] - b.pivot[0], c.origin[1] - b.pivot[1], c.origin[2] - b.pivot[2]);
        holder.rotation.order = 'ZYX';
        holder.rotation.set(c.rotation[0] * D2R, c.rotation[1] * D2R, c.rotation[2] * D2R);
        const mesh = new THREE.Mesh(cubeGeometry(c, uvW, uvH, c.origin), mat);
        mesh.userData.bone = b.name;
        holder.add(mesh); g.add(holder); this.meshes.push(mesh);
      }
      (parent || this.modelRoot).add(g);
      this.bones.set(b.name, g);
      return g;
    };
    for (const b of model.bones) make(b);
    this.frame();
    this.dirty = true;
  }

  frame() {
    if (!this.model) return;
    const { min, max } = modelBounds(this.model);
    const cy = (min[1] + max[1]) / 32, size = Math.max(max[0] - min[0], max[1] - min[1], max[2] - min[2]) / 16;
    this.controls.target.set(0, cy, 0);
    const dist = Math.max(1.6, size * 2.1);
    const dir = new THREE.Vector3(0.55, 0.35, -0.76).normalize();
    this.camera.position.copy(this.controls.target).addScaledVector(dir, dist);
    this.shadow.scale.setScalar(Math.max(0.6, size * 0.55));
    this.dirty = true;
  }

  // pose: { bone: { rotation:[deg], position:[px], scale:[] } } in GeckoLib space
  applyPose(pose, restOverride = null) {
    for (const [name, g] of this.bones) {
      const p = pose[name];
      const rest = restOverride?.[name] ? restOverride[name].map((n) => n * D2R) : g.userData.rest;
      if (restOverride?.[name]) g.userData.rest = rest;
      if (this.gizmo.dragging && name === this.selected) continue;
      const r = p?.rotation || [0, 0, 0], t = p?.position || [0, 0, 0], s = p?.scale || [1, 1, 1];
      g.rotation.set(rest[0] - r[0] * D2R, rest[1] - r[1] * D2R, rest[2] + r[2] * D2R);
      g.position.set(g.userData.base.x - t[0], g.userData.base.y + t[1], g.userData.base.z + t[2]);
      g.scale.set(s[0] || 1e-4, s[1] || 1e-4, s[2] || 1e-4);
    }
    this.dirty = true;
  }

  // Convert the bone's current transform back into GeckoLib values, unwrapped near `prev`.
  readBone(name, prev = null) {
    const g = this.bones.get(name);
    if (!g) return null;
    const rest = g.userData.rest;
    const e = g.rotation;
    const cand = [[e.x, e.y, e.z], [e.x + Math.PI, Math.PI - e.y, e.z + Math.PI]].map(([x, y, z]) => [-(x - rest[0]) * R2D, -(y - rest[1]) * R2D, (z - rest[2]) * R2D]);
    const ref = prev || this.lastRead?.[name] || [0, 0, 0];
    const unwrap = (v) => v.map((n, i) => { while (n - ref[i] > 180) n -= 360; while (n - ref[i] < -180) n += 360; return n; });
    const opts = cand.map(unwrap);
    const dist = (v) => v.reduce((s, n, i) => s + Math.abs(n - ref[i]), 0);
    const rotation = dist(opts[0]) <= dist(opts[1]) ? opts[0] : opts[1];
    const position = [-(g.position.x - g.userData.base.x), g.position.y - g.userData.base.y, g.position.z - g.userData.base.z];
    const scale = [g.scale.x, g.scale.y, g.scale.z];
    this.lastRead = { ...(this.lastRead || {}), [name]: rotation };
    return { bone: name, rotation, position, scale };
  }

  select(name, mode = 'rotate', showGizmo = true) {
    if (this.selected && this.bones.get(this.selected)) this.bones.get(this.selected).userData.mat.emissive?.setHex(0x000000);
    this.selected = name && this.bones.has(name) ? name : null;
    if (this.selected) {
      const g = this.bones.get(this.selected);
      g.userData.mat.emissive?.setHex(0x2a3f66);
      if (showGizmo) { this.gizmo.attach(g); this.gizmo.setMode(mode === 'move' ? 'translate' : mode === 'scale' ? 'scale' : 'rotate'); this.gizmo.setSpace(mode === 'move' ? 'world' : 'local'); }
      else this.gizmo.detach();
    } else this.gizmo.detach();
    this.dirty = true;
  }

  pick(e) {
    const rect = this.renderer.domElement.getBoundingClientRect();
    const ndc = new THREE.Vector2(((e.clientX - rect.left) / rect.width) * 2 - 1, -((e.clientY - rect.top) / rect.height) * 2 + 1);
    const rc = new THREE.Raycaster(); rc.setFromCamera(ndc, this.camera);
    const hits = rc.intersectObjects(this.meshes.filter((m) => m.parent?.parent?.visible !== false), false);
    this.cb.onSelect?.(hits.length ? hits[0].object.userData.bone : null);
  }

  setBoneVisible(name, v) { const g = this.bones.get(name); if (g) { g.visible = v; this.dirty = true; } }
  snapshot() { this.renderer.render(this.scene, this.camera); return this.renderer.domElement.toDataURL('image/png'); }
}
