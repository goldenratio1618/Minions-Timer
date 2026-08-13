"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const core = require("../web/timer-core.js");

test("converts MMSS keypad digits to seconds", () => {
  assert.equal(core.secondsFromDigits("0200"), 120);
  assert.equal(core.secondsFromDigits("0130"), 90);
  assert.equal(core.secondsFromDigits("45"), 45);
});

test("clamps keypad input to ten minutes", () => {
  assert.equal(core.clampDigits("1000"), "1000");
  assert.equal(core.clampDigits("1001"), "1000");
  assert.equal(core.clampDigits("9999"), "1000");
});

test("formats timer values with tabular minutes and seconds", () => {
  assert.equal(core.formatSeconds(120), "02:00");
  assert.equal(core.formatSeconds(5), "00:05");
  assert.equal(core.formatSeconds(900), "15:00");
  assert.equal(core.formatDigits("1"), "00:01");
});

test("deadline math resists delayed callbacks", () => {
  assert.equal(core.remainingSeconds(121000, 1000), 120);
  assert.equal(core.remainingSeconds(121000, 120001), 1);
  assert.equal(core.remainingSeconds(121000, 121001), 0);
});

test("switches teams", () => {
  assert.equal(core.oppositeTeam("yellow"), "blue");
  assert.equal(core.oppositeTeam("blue"), "yellow");
});

test("audio coordinator stops the previous cue before a new cue starts", () => {
  const sounds = new Map(["buzzer", "ding"].map((id) => [id, {
    currentTime: 7,
    pauseCount: 0,
    playCount: 0,
    pause() { this.pauseCount += 1; },
    play() {
      this.playCount += 1;
      return Promise.resolve();
    },
  }]));
  const coordinator = core.createAudioCoordinator((id) => sounds.get(id), [...sounds.keys()]);

  coordinator.play("buzzer");
  coordinator.play("ding");

  assert.equal(sounds.get("buzzer").playCount, 1);
  assert.ok(sounds.get("buzzer").pauseCount >= 2);
  assert.equal(sounds.get("buzzer").currentTime, 0);
  assert.equal(sounds.get("ding").playCount, 1);
});

test("audio coordinator cancels a delayed play after timer state changes", async () => {
  let resolvePlay;
  const delayedPlay = new Promise((resolve) => { resolvePlay = resolve; });
  const sound = {
    currentTime: 4,
    pauseCount: 0,
    pause() { this.pauseCount += 1; },
    play() { return delayedPlay; },
  };
  const coordinator = core.createAudioCoordinator(() => sound, ["buzzer"]);

  coordinator.play("buzzer");
  const pausesBeforeStateChange = sound.pauseCount;
  coordinator.stopAll();
  resolvePlay();
  await delayedPlay;
  await Promise.resolve();

  assert.ok(sound.pauseCount > pausesBeforeStateChange);
  assert.equal(sound.currentTime, 0);
});

test("audio coordinator tolerates browsers that reject playback", async () => {
  const sound = {
    currentTime: 0,
    pause() {},
    play() { return Promise.reject(new Error("blocked")); },
  };
  const coordinator = core.createAudioCoordinator(() => sound, ["beep"]);
  assert.doesNotThrow(() => coordinator.play("beep"));
  await Promise.resolve();
});
