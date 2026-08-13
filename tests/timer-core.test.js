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
