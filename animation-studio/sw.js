// Offline support: precache the app shell, then serve cache-first and refresh in the background.
const VERSION = 'mcas-v1';
const FILES = [
  './', './index.html', './styles.css', './manifest.webmanifest', './icons/icon.svg', './icons/icon-192.png', './icons/icon-512.png',
  './vendor/three.module.min.js', './vendor/OrbitControls.js', './vendor/TransformControls.js',
  './src/app.js', './src/ui/viewer.js', './src/ui/timeline.js', './src/ui/panels.js', './src/ui/dom.js',
  './src/core/adjust.js', './src/core/animation.js', './src/core/assist.js', './src/core/assistant.js', './src/core/blocks.js',
  './src/core/easing.js', './src/core/history.js', './src/core/model.js', './src/core/rig.js', './src/core/sampleModel.js',
  './src/core/sequencer.js', './src/core/storage.js', './src/export/package.js', './src/export/zip.js',
  './src/formats/bbmodel.js', './src/formats/geckolib.js', './src/formats/geo.js',
];
self.addEventListener('install', (e) => { e.waitUntil(caches.open(VERSION).then((c) => c.addAll(FILES)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== VERSION).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', (e) => {
  if (e.request.method !== 'GET' || new URL(e.request.url).origin !== location.origin) return;
  e.respondWith(caches.open(VERSION).then(async (c) => {
    const hit = await c.match(e.request, { ignoreSearch: true });
    const net = fetch(e.request).then((r) => { if (r.ok) c.put(e.request, r.clone()); return r; }).catch(() => hit);
    return hit || net;
  }));
});
