cat > ~/Documents/Wifi-scanner/sw.js << 'ENDOFFILE'
const CACHE_NAME = 'wifi-scanner-v6';
const ASSETS = [
  '/wifi-scanner/',
  '/wifi-scanner/index.html',
  '/wifi-scanner/manifest.json',
  '/wifi-scanner/icon-512.png',
  '/wifi-scanner/icon.svg'
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(names => {
      return Promise.all(
        names.map(name => {
          if (name !== CACHE_NAME) return caches.delete(name);
        })
      );
    })
  );
});

self.addEventListener('fetch', e => {
  e.respondWith(
    caches.match(e.request)
      .then(response => response || fetch(e.request))
  );
});
ENDOFFILE
