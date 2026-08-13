"use strict";

const CACHE_NAME = "minions-timer-v2";
const APP_SHELL = [
  "/timer/", "/timer/index.html", "/timer/style.css?v=20260813-arrow-fit", "/timer/timer-core.js", "/timer/app.js", "/timer/manifest.webmanifest",
  "/timer/assets/icon.svg", "/timer/assets/icon-192.png", "/timer/assets/icon-512.png",
  "/timer/assets/orbitron_wght.ttf", "/timer/assets/share_tech_mono_regular.ttf",
  "/timer/assets/beep.wav", "/timer/assets/buzzer_sound.wav", "/timer/assets/ding.wav",
  "/timer/assets/yellow_start_turn.wav", "/timer/assets/blue_start_turn.mp3"
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET" || new URL(event.request.url).origin !== self.location.origin) return;
  if (event.request.mode === "navigate") {
    event.respondWith(fetch(event.request).then((response) => {
      const copy = response.clone();
      void caches.open(CACHE_NAME).then((cache) => cache.put("/timer/index.html", copy));
      return response;
    }).catch(() => caches.match("/timer/index.html")));
    return;
  }
  event.respondWith(caches.match(event.request).then((cached) => cached || fetch(event.request).then((response) => {
    if (response.ok) {
      const copy = response.clone();
      void caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
    }
    return response;
  })));
});
