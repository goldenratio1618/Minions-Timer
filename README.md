# Minions Timer

Minions Timer is available in two forms:

- `app/`: the original native Android application.
- `web/`: the installable, offline-capable web application served at
  `https://minionsofdarkness.com/timer/`.

The web version preserves the two-team timer, five-second grace period,
pause/resume, time adjustments, sounds, custom keypad, and landscape layout.
It uses deadline-based countdowns so delayed browser callbacks do not make the
clock drift. Once installed and loaded successfully, its service worker caches
the complete application shell for offline use.

Run the JavaScript tests with a current Node.js release:

```text
node --test tests/*.test.js
```

Production and security details are in [`deploy/README.md`](deploy/README.md).
