const assert = require("node:assert/strict");
const test = require("node:test");
const { parseJavaMajorVersion } = require("../dist/java.js");

test("parses OpenJDK and Oracle version output", () => {
  assert.equal(
    parseJavaMajorVersion('openjdk version "25.0.1" 2025-10-21'),
    25
  );
  assert.equal(
    parseJavaMajorVersion('java version "1.8.0_402"\nJava(TM) SE Runtime Environment'),
    8
  );
  assert.equal(parseJavaMajorVersion("openjdk 26 2026-03-17"), 26);
});

test("rejects output without a recognizable Java version", () => {
  assert.equal(parseJavaMajorVersion("unknown runtime"), undefined);
});
