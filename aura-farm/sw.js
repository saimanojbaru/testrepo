const CACHE_NAME = 'aura-farm-v1';
const ASSETS = [
  '/aura-farm/index.html',
  '/aura-farm/style.css',
  '/aura-farm/app.js',
  '/aura-farm/manifest.json',
  '/aura-farm/icons/icon-192.svg',
  '/aura-farm/icons/icon-512.svg'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE_NAME).then(c => c.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', e => {
  e.respondWith(
    caches.match(e.request).then(r => r || fetch(e.request).catch(() =>
      caches.match('/aura-farm/index.html')
    ))
  );
});
