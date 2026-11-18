// Tiny DOM helpers: h() element builder, toasts, modal dialogs, prompt/confirm, file pick/download.
export function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'html') el.innerHTML = v;
    else if (k in el && typeof v !== 'string') el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat(Infinity)) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

let toastTimer;
export function toast(msg, kind = 'info', ms = 2600) {
  let el = document.getElementById('toast');
  if (!el) { el = h('div', { id: 'toast', role: 'status', 'aria-live': 'polite' }); document.body.append(el); }
  el.className = `toast show ${kind}`;
  el.textContent = msg;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), ms);
}

export function modal(title, body, actions = [{ label: 'Close' }]) {
  return new Promise((resolve) => {
    const close = (v) => { wrap.remove(); resolve(v); };
    const wrap = h('div', { class: 'modal-wrap', onclick: (e) => { if (e.target === wrap) close(null); } },
      h('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
        h('h2', {}, title),
        h('div', { class: 'modal-body' }, body),
        h('div', { class: 'modal-actions' }, actions.map((a) => h('button', { class: `btn ${a.primary ? 'primary' : ''} ${a.danger ? 'danger' : ''}`, onclick: () => close(a.value !== undefined ? a.value : a.label) }, a.label)))));
    document.body.append(wrap);
    wrap.querySelector('.btn.primary, input, select')?.focus();
  });
}

export async function ask(title, value = '', label = '') {
  const input = h('input', { class: 'input', value, 'aria-label': label || title });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') wrapOk(); });
  let wrapOk;
  const p = modal(title, [label ? h('label', { class: 'lbl' }, label) : null, input], [{ label: 'Cancel', value: null }, { label: 'OK', primary: true, value: '__ok' }]);
  wrapOk = () => input.closest('.modal').querySelector('.btn.primary').click();
  setTimeout(() => { input.focus(); input.select(); }, 30);
  const r = await p;
  return r === '__ok' ? input.value.trim() : null;
}

export async function confirm(title, text, okLabel = 'OK', danger = false) {
  const r = await modal(title, h('p', {}, text), [{ label: 'Cancel', value: false }, { label: okLabel, primary: !danger, danger, value: true }]);
  return r === true;
}

export function pickFiles(accept, multiple = true) {
  return new Promise((resolve) => {
    const input = h('input', { type: 'file', accept, multiple, style: { display: 'none' } });
    input.addEventListener('change', () => { resolve([...input.files]); input.remove(); });
    document.body.append(input);
    input.click();
  });
}

// Saves a file. Inside a claude.ai artifact the page can't trigger downloads itself, so it asks
// the viewer through the `downloads` capability; everywhere else it uses a normal download link.
let dlCap;
async function downloadsCapability() {
  if (dlCap !== undefined) return dlCap;
  try { dlCap = window.claude?.use ? await window.claude.use('downloads') : null; } catch { dlCap = null; }
  return dlCap;
}
export async function download(name, data, type = 'application/octet-stream') {
  const blob = data instanceof Blob ? data : new Blob([typeof data === 'string' || data instanceof Uint8Array ? data : JSON.stringify(data, null, 2)], { type });
  const cap = await downloadsCapability();
  if (cap) {
    // the viewer's save prompt only accepts common extensions; .bbmodel travels as .bbmodel.json
    const safe = name.endsWith('.bbmodel') ? `${name}.json` : name;
    try {
      await cap.save({ filename: safe, data: blob });
      if (safe !== name) toast('Saved – rename the file to end in .bbmodel before opening it in Blockbench.', 'info', 5000);
      return true;
    } catch (e) {
      if (e?.code === 'declined') return false;
      toast(`Couldn't save the file (${e?.code || 'unavailable'}).`, 'error');
      return false;
    }
  }
  const url = URL.createObjectURL(blob);
  const a = h('a', { href: url, download: name, style: { display: 'none' } });
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
  return true;
}

export const readText = (file) => file.text();
export const readDataUrl = (file) => new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(r.result); r.onerror = rej; r.readAsDataURL(file); });

// Drag-to-scrub number field: drag sideways to change, tap to type. Big enough for thumbs.
export function scrubber({ label, value, step = 1, fine = 0.1, min = -Infinity, max = Infinity, unit = '', color, onStart, onInput, onEnd }) {
  const valEl = h('span', { class: 'scrub-val' });
  const el = h('div', { class: 'scrub', tabindex: 0, role: 'slider', 'aria-label': label, style: color ? { '--axis': color } : {} },
    h('span', { class: 'scrub-lbl' }, label), valEl, h('span', { class: 'scrub-hint' }, '⟷'));
  let v = value;
  const show = () => { valEl.textContent = `${(Math.round(v * 10) / 10).toFixed(1)}${unit}`; el.setAttribute('aria-valuenow', v); };
  show();
  let sx = 0, sv = 0, dragging = false, moved = false;
  el.addEventListener('pointerdown', (e) => { el.setPointerCapture(e.pointerId); sx = e.clientX; sv = v; dragging = true; moved = false; });
  el.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    const dx = e.clientX - sx;
    if (!moved && Math.abs(dx) < 4) return;
    if (!moved) { moved = true; onStart?.(); }
    v = Math.min(max, Math.max(min, sv + dx * (e.shiftKey ? fine : step) * 0.5));
    v = Math.round(v / fine) * fine;
    show(); onInput?.(v);
  });
  const end = async () => {
    if (!dragging) return; dragging = false;
    if (moved) onEnd?.(v);
    else {
      const r = await ask(label, String(Math.round(v * 100) / 100));
      if (r !== null && r !== '' && Number.isFinite(Number(r))) { onStart?.(); v = Math.min(max, Math.max(min, Number(r))); show(); onInput?.(v); onEnd?.(v); }
    }
  };
  el.addEventListener('pointerup', end); el.addEventListener('pointercancel', end);
  el.addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    e.preventDefault(); onStart?.(); v = Math.min(max, Math.max(min, v + (e.key === 'ArrowRight' ? step : -step))); show(); onInput?.(v); onEnd?.(v);
  });
  el.setValue = (n) => { if (!dragging) { v = n; show(); } };
  return el;
}
