"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const snippet = fs.readFileSync(path.join(__dirname, "..", "deploy", "nginx-minions-timer.conf"), "utf8");

test("timer nginx boundary is static-only and read-only", () => {
  assert.match(snippet, /alias \/opt\/minions-timer\/current\/web\//);
  assert.match(snippet, /limit_except GET \{ deny all; \}/);
  assert.match(snippet, /autoindex off;/);
  assert.doesNotMatch(snippet, /^\s*(?:proxy_pass|fastcgi_pass|uwsgi_pass)\b/m);
});

test("timer nginx boundary cannot address the game release or API", () => {
  assert.doesNotMatch(snippet, /\/opt\/minions(?:\/|\s)/);
  assert.doesNotMatch(snippet, /127\.0\.0\.1:8000|location\s+\/api/);
});
