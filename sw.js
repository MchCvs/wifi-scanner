cat > ~/Documents/Wifi-scanner/sw.js << 'ENDOFFILE'
const CACHE = 'wifi-scanner-v1';
const ASSETS = ['/wifi-scanner/', '/wifi-scanner/index.html', '/wifi-scanner/manifest.json', '/wifi-scanner/icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)));
});

self.addEventListener('fetch', e => {
  e.respondWith(caches.match(e.request).then(r => r || fetch(e.request)));
});
ENDOFFILE
