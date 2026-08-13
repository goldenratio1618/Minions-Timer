"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const app = fs.readFileSync(path.join(__dirname, "..", "web", "app.js"), "utf8");

function functionSource(name, nextName) {
  const start = app.indexOf(`function ${name}(`);
  const end = app.indexOf(`function ${nextName}(`, start);
  assert.notEqual(start, -1, `missing ${name}`);
  assert.notEqual(end, -1, `missing ${nextName}`);
  return app.slice(start, end);
}

test("time-altering recovery buttons cancel cues from the previous visual state", () => {
  assert.match(functionSource("addFiveMinutes", "addThirtySeconds"), /stopSounds\(\);[\s\S]*timerState = "running"/);
  assert.match(functionSource("addThirtySeconds", "handleTurnSurface"), /timerState === "time-out"[\s\S]*stopSounds\(\);[\s\S]*timerState = "running"/);
  assert.match(functionSource("adjustTime", "addFiveMinutes"), /stopSounds\(\);[\s\S]*state\.remaining/);
});

test("ending a turn emits one coordinated initial grace cue", () => {
  const handler = functionSource("handleTurnSurface", "startGame");
  assert.match(handler, /startGrace\("sound-buzzer"\)/);
  assert.doesNotMatch(handler, /playSound\("sound-buzzer"\)/);
});

test("application audio goes through the coordinator", () => {
  assert.doesNotMatch(app, /\baudio\.play\(/);
  assert.match(app, /audioCoordinator\.play\(id\)/);
});
