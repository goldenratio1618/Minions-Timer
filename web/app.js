"use strict";

const {
  MAX_DIGITS,
  MAX_SECONDS,
  clampDigits,
  createAudioCoordinator,
  digitsFromSeconds,
  formatDigits,
  formatSeconds,
  oppositeTeam,
  remainingSeconds,
  secondsFromDigits,
} = window.TimerCore;

const $ = (id) => document.getElementById(id);
const BEEP_TIMES = new Set([60, 30, 15, 5, 4, 3, 2, 1]);
const GRACE_SECONDS = 5;
const SOUND_IDS = [
  "sound-yellow-start",
  "sound-blue-start",
  "sound-beep",
  "sound-buzzer",
  "sound-ding",
];
const audioCoordinator = createAudioCoordinator($, SOUND_IDS);

const state = {
  timerState: "pre-game",
  team: "yellow",
  totals: { yellow: 120, blue: 120 },
  inputDigits: { yellow: "0200", blue: "0200" },
  activeInputTeam: null,
  remaining: 0,
  deadline: null,
  pausedFrom: null,
  wasGraceTurn: false,
  lastRenderedSecond: null,
  loop: null,
  faceTimers: new Map(),
  installPrompt: null,
  wakeLock: null,
};

function showToast(message) {
  const toast = $("toast");
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => { toast.hidden = true; }, 3200);
}

function playSound(id) {
  audioCoordinator.play(id);
}

function stopSounds() {
  audioCoordinator.stopAll();
}

function updateSetupDisplays() {
  $("yellow-time-display").querySelector("strong").textContent = formatDigits(state.inputDigits.yellow);
  $("blue-time-display").querySelector("strong").textContent = formatDigits(state.inputDigits.blue);
  $("keypad-display").textContent = formatDigits(state.activeInputTeam ? state.inputDigits[state.activeInputTeam] : "");
}

function showScreen(name) {
  $("settings-screen").classList.toggle("active", name === "settings");
  $("timer-screen").classList.toggle("active", name === "timer");
}

function updatePauseButton() {
  const paused = state.timerState === "paused";
  $("pause-icon").textContent = paused ? "▶" : "Ⅱ";
  $("pause-button").setAttribute("aria-label", paused ? "Resume" : "Pause");
}

function renderTimer(force = false) {
  const second = Math.max(0, state.remaining);
  if (!force && second === state.lastRenderedSecond) return;
  state.lastRenderedSecond = second;
  $("time-display").textContent = formatSeconds(second);

  const graceLike = state.timerState === "grace" || (state.timerState === "paused" && state.pausedFrom === "grace");
  const surface = $("turn-surface");
  surface.classList.toggle("grace", graceLike);
  surface.classList.toggle("yellow", !graceLike && state.team === "yellow");
  surface.classList.toggle("blue", !graceLike && state.team === "blue");

  let status = `${state.team === "yellow" ? "Yellow" : "Blue"} Team`;
  if (state.timerState === "paused") status += graceLike ? " · Grace paused" : " · Paused";
  if (state.timerState === "grace") status = `Grace · ${status}`;
  if (state.timerState === "time-out") status = `${status} · Time out — tap to continue`;
  $("timer-status").textContent = status;
  surface.setAttribute("aria-label", state.timerState === "running" ? `End ${status} turn` : status);
  updatePauseButton();
}

async function requestWakeLock() {
  if (!("wakeLock" in navigator) || document.visibilityState !== "visible" || state.timerState === "pre-game" || state.wakeLock) return;
  try {
    state.wakeLock = await navigator.wakeLock.request("screen");
    state.wakeLock.addEventListener("release", () => { state.wakeLock = null; });
  } catch (_error) {
    state.wakeLock = null;
  }
}

function stopLoop() {
  if (state.loop !== null) window.clearInterval(state.loop);
  state.loop = null;
}

function startLoop() {
  stopLoop();
  state.loop = window.setInterval(tick, 100);
  tick();
}

function startTurn() {
  state.timerState = "running";
  state.pausedFrom = null;
  state.remaining = state.totals[state.team];
  state.deadline = Date.now() + state.remaining * 1000;
  state.lastRenderedSecond = null;
  playSound(state.team === "yellow" ? "sound-yellow-start" : "sound-blue-start");
  renderTimer(true);
  startLoop();
  void requestWakeLock();
}

function onTimeOut() {
  stopLoop();
  state.timerState = "time-out";
  state.remaining = 0;
  state.deadline = null;
  playSound("sound-buzzer");
  renderTimer(true);
}

function startGrace(initialSound = "sound-ding") {
  stopLoop();
  state.timerState = "grace";
  state.pausedFrom = null;
  state.remaining = GRACE_SECONDS;
  state.deadline = Date.now() + GRACE_SECONDS * 1000;
  state.lastRenderedSecond = null;
  playSound(initialSound);
  renderTimer(true);
  startLoop();
}

function endGrace() {
  stopLoop();
  state.team = oppositeTeam(state.team);
  startTurn();
}

function tick() {
  if (state.timerState !== "running" && state.timerState !== "grace") return;
  const previous = state.remaining;
  state.remaining = remainingSeconds(state.deadline, Date.now());

  if (state.timerState === "running" && state.remaining <= 0) {
    onTimeOut();
    return;
  }
  if (state.timerState === "grace" && state.remaining <= 0) {
    endGrace();
    return;
  }

  if (state.remaining !== previous) {
    if (state.timerState === "running" && BEEP_TIMES.has(state.remaining)) playSound("sound-beep");
    if (state.timerState === "grace") playSound("sound-ding");
  }
  renderTimer();
}

function togglePause() {
  if (state.timerState === "running" || state.timerState === "grace") {
    tick();
    if (state.timerState !== "running" && state.timerState !== "grace") return;
    stopSounds();
    state.pausedFrom = state.timerState;
    state.timerState = "paused";
    state.deadline = null;
    stopLoop();
  } else if (state.timerState === "paused") {
    stopSounds();
    state.timerState = state.pausedFrom || "running";
    state.deadline = Date.now() + state.remaining * 1000;
    startLoop();
  }
  renderTimer(true);
}

function adjustTime(delta) {
  if (state.timerState === "running") {
    tick();
    if (state.timerState !== "running") return;
  }
  if (state.timerState !== "running" && !(state.timerState === "paused" && state.pausedFrom === "running")) return;
  stopSounds();
  state.remaining = Math.max(0, state.remaining + delta);
  if (state.timerState === "running") state.deadline = Date.now() + state.remaining * 1000;
  if (state.remaining === 0 && state.timerState === "running") {
    onTimeOut();
    return;
  }
  renderTimer(true);
}

function addFiveMinutes() {
  const graceLike = state.timerState === "grace" || (state.timerState === "paused" && state.pausedFrom === "grace");
  if (graceLike || state.timerState === "time-out") {
    stopSounds();
    stopLoop();
    state.timerState = "running";
    state.pausedFrom = null;
    state.remaining = 300;
    state.deadline = Date.now() + state.remaining * 1000;
    renderTimer(true);
    startLoop();
    return;
  }
  adjustTime(300);
}

function addThirtySeconds() {
  if (state.timerState === "time-out") {
    stopSounds();
    state.timerState = "running";
    state.remaining = 30;
    state.deadline = Date.now() + 30000;
    renderTimer(true);
    startLoop();
    return;
  }
  adjustTime(30);
}

function handleTurnSurface() {
  if (state.timerState === "running") {
    startGrace("sound-buzzer");
  } else if (state.timerState === "time-out") {
    startGrace();
  } else if (state.timerState === "grace" || state.timerState === "paused") {
    togglePause();
  }
}

function startGame() {
  const yellow = secondsFromDigits(state.inputDigits.yellow);
  const blue = secondsFromDigits(state.inputDigits.blue);
  if (yellow <= 0 || yellow > MAX_SECONDS || blue <= 0 || blue > MAX_SECONDS) {
    showToast("Set each team to a time between 1 second and 10 minutes.");
    return;
  }
  state.totals = { yellow, blue };
  localStorage.setItem("minions-timer-totals", JSON.stringify(state.totals));
  showScreen("timer");
  if (state.wasGraceTurn) {
    state.team = oppositeTeam(state.team);
    state.wasGraceTurn = false;
  }
  startTurn();
}

function adjustSettings() {
  state.wasGraceTurn = state.timerState === "grace" || (state.timerState === "paused" && state.pausedFrom === "grace");
  if (state.timerState === "running") tick();
  stopSounds();
  stopLoop();
  state.timerState = "pre-game";
  state.deadline = null;
  if (state.wakeLock) void state.wakeLock.release();
  state.inputDigits.yellow = digitsFromSeconds(state.totals.yellow);
  state.inputDigits.blue = digitsFromSeconds(state.totals.blue);
  updateSetupDisplays();
  showScreen("settings");
}

function showKeypad(team) {
  state.activeInputTeam = team;
  state.inputDigits[team] = "";
  $("keypad-title").textContent = `Set ${team === "yellow" ? "Yellow" : "Blue"} Team time`;
  updateSetupDisplays();
  $("keypad-overlay").hidden = false;
  $("keypad-grid").querySelector("button").focus();
}

function hideKeypad() {
  $("keypad-overlay").hidden = true;
  state.activeInputTeam = null;
}

function handleKeypadInput(key) {
  const team = state.activeInputTeam;
  if (!team) return;
  if (key === "enter") {
    hideKeypad();
    return;
  }
  const current = state.inputDigits[team];
  const updated = key === "backspace" ? current.slice(0, -1) : current.length < MAX_DIGITS ? `${current}${key}` : current;
  state.inputDigits[team] = clampDigits(updated);
  updateSetupDisplays();
}

function randomFace(button) {
  button.textContent = Math.random() < 0.5 ? "🙂" : "🙁";
  button.disabled = true;
  clearTimeout(state.faceTimers.get(button));
  state.faceTimers.set(button, setTimeout(() => {
    button.textContent = "🤔";
    button.disabled = false;
  }, 3000));
}

function swapTimes() {
  [state.inputDigits.yellow, state.inputDigits.blue] = [state.inputDigits.blue, state.inputDigits.yellow];
  updateSetupDisplays();
}

function isStandalone() {
  return window.matchMedia("(display-mode: standalone)").matches || window.navigator.standalone === true;
}

function showInstallHelp() {
  if (state.installPrompt) {
    state.installPrompt.prompt();
    void state.installPrompt.userChoice.finally(() => {
      state.installPrompt = null;
      $("install-button").hidden = true;
    });
    return;
  }
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
  $("install-instructions").innerHTML = ios
    ? "<p>In Safari, tap the <strong>Share</strong> button, choose <strong>Add to Home Screen</strong>, turn on <strong>Open as Web App</strong>, then tap <strong>Add</strong>.</p>"
    : "<p>Open your browser menu and choose <strong>Install app</strong> or <strong>Add to Home screen</strong>.</p>";
  $("install-overlay").hidden = false;
}

function closeInstallHelp() {
  $("install-overlay").hidden = true;
}

function restoreTotals() {
  try {
    const saved = JSON.parse(localStorage.getItem("minions-timer-totals") || "null");
    if (saved && saved.yellow > 0 && saved.yellow <= MAX_SECONDS && saved.blue > 0 && saved.blue <= MAX_SECONDS) {
      state.totals = { yellow: saved.yellow, blue: saved.blue };
      state.inputDigits = { yellow: digitsFromSeconds(saved.yellow), blue: digitsFromSeconds(saved.blue) };
    }
  } catch (_error) {
    localStorage.removeItem("minions-timer-totals");
  }
}

function bindControls() {
  $("yellow-time-display").addEventListener("click", () => showKeypad("yellow"));
  $("blue-time-display").addEventListener("click", () => showKeypad("blue"));
  $("swap-button").addEventListener("click", swapTimes);
  $("start-button").addEventListener("click", startGame);
  $("turn-surface").addEventListener("click", handleTurnSurface);
  $("pause-button").addEventListener("click", togglePause);
  $("minus-button").addEventListener("click", () => adjustTime(-30));
  $("plus-button").addEventListener("click", addThirtySeconds);
  $("five-button").addEventListener("click", addFiveMinutes);
  $("adjust-button").addEventListener("click", adjustSettings);
  [$("face-button"), $("settings-face-button")].forEach((button) => button.addEventListener("click", () => randomFace(button)));

  $("keypad-grid").addEventListener("click", (event) => {
    const button = event.target.closest("button[data-key]");
    if (button) handleKeypadInput(button.dataset.key);
  });
  $("keypad-overlay").addEventListener("click", (event) => {
    if (event.target === $("keypad-overlay")) hideKeypad();
  });
  $("install-button").addEventListener("click", showInstallHelp);
  $("install-close").addEventListener("click", closeInstallHelp);
  $("install-overlay").addEventListener("click", (event) => {
    if (event.target === $("install-overlay")) closeInstallHelp();
  });

  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    state.installPrompt = event;
    $("install-button").hidden = false;
  });
  window.addEventListener("appinstalled", () => { $("install-button").hidden = true; });
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      tick();
      void requestWakeLock();
    }
  });
}

function initialize() {
  restoreTotals();
  bindControls();
  updateSetupDisplays();
  $("install-button").hidden = isStandalone();
  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => navigator.serviceWorker.register("/timer/service-worker.js", { scope: "/timer/" }).catch(() => {}));
  }
}

initialize();
