// Persistence: IndexedDB (projects, personal animation library, settings). Falls back to memory
// if IndexedDB is unavailable (private mode), and tells the UI so it can warn the user.
const DB = 'mc-animation-studio';
const VERSION = 1;
const mem = { projects: new Map(), library: new Map(), meta: new Map() };
let dbp = null;
export let persistent = true;

function open() {
  if (dbp) return dbp;
  dbp = new Promise((resolve) => {
    try {
      const req = indexedDB.open(DB, VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        for (const s of ['projects', 'library', 'meta']) if (!db.objectStoreNames.contains(s)) db.createObjectStore(s, { keyPath: 'id' });
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => { persistent = false; resolve(null); };
      req.onblocked = () => { persistent = false; resolve(null); };
    } catch { persistent = false; resolve(null); }
  });
  return dbp;
}

async function tx(store, mode, fn) {
  const db = await open();
  if (!db) return fn(null, mem[store]);
  return new Promise((resolve, reject) => {
    const t = db.transaction(store, mode);
    const s = t.objectStore(store);
    let result;
    Promise.resolve(fn(s)).then((r) => { result = r; });
    t.oncomplete = () => resolve(result);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
  });
}
const req2p = (r) => new Promise((res, rej) => { r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });

export const put = (store, obj) => tx(store, 'readwrite', (s, m) => (s ? req2p(s.put(obj)) : m.set(obj.id, structuredClone(obj))));
export const get = (store, id) => tx(store, 'readonly', (s, m) => (s ? req2p(s.get(id)) : structuredClone(m.get(id))));
export const del = (store, id) => tx(store, 'readwrite', (s, m) => (s ? req2p(s.delete(id)) : m.delete(id)));
export const all = (store) => tx(store, 'readonly', (s, m) => (s ? req2p(s.getAll()) : [...m.values()].map((x) => structuredClone(x))));

export async function getSetting(key, def) { const r = await get('meta', key); return r ? r.value : def; }
export async function setSetting(key, value) { return put('meta', { id: key, value }); }

export async function requestPersistence() {
  try { if (navigator.storage?.persist) return await navigator.storage.persist(); } catch { /* ignore */ }
  return false;
}
