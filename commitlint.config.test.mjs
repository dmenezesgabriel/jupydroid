// Tests for commitlint.config.mjs: they run the real CLI over a message on stdin, because what the
// config promises is what `commitlint` does with it, not what the object says.
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { test } from "node:test";

const COMMITLINT = "node_modules/.bin/commitlint";

function lint(message) {
  return spawnSync(COMMITLINT, [], { input: message, encoding: "utf8" });
}

function assertAccepted(message) {
  const result = lint(message);
  assert.equal(result.status, 0, `expected ${JSON.stringify(message)} accepted:\n${result.stdout}`);
}

function assertRejected(message) {
  const result = lint(message);
  // Exactly 1 is commitlint saying "rule violated"; a missing binary or a crash must not pass as a rejection.
  assert.equal(result.status, 1, `expected ${JSON.stringify(message)} rejected with exit 1, got ${result.status}`);
}

test("a type, a scope and a subject is accepted", () => assertAccepted("feat(scope): add the thing"));

test("a scope is optional", () => assertAccepted("fix: repair the thing"));

test("a breaking change marker is accepted", () => assertAccepted("fix(api)!: drop the old field"));

test("every type the history uses is accepted", () => {
  for (const type of ["feat", "fix", "docs", "test", "refactor", "build", "ci", "chore", "perf", "style"]) {
    assertAccepted(`${type}(x): subject`);
  }
});

test("a capitalised subject is accepted", () => assertAccepted("refactor(tests): Kotlin fixtures carry no code"));

test("a header past one hundred characters is accepted", () => {
  assertAccepted(`docs(adr): ${"a long subject ".repeat(20)}(OPS-008 T1)`);
});

test("a body line past one hundred characters is accepted", () => {
  assertAccepted(`fix(x): subject\n\n${"a long body line ".repeat(20)}`);
});

test("a closing 'Gates:' line right under the body raises no warning", () => {
  // Gate results go in the body (AGENTS.md); commitlint parses "Gates: ..." as a footer token.
  const result = lint("fix(x): subject\n\nWhat changed and why.\nGates: the hook set ran on this commit.");
  assert.equal(result.status, 0);
  assert.equal(result.stdout, "", `expected no warning, got:\n${result.stdout}`);
});

test("a merge commit is ignored", () => assertAccepted("Merge pull request #19 from owner/branch"));

test("a type outside the list is rejected", () => assertRejected("wip: half done"));

test("revert is rejected, since the working agreement forbids reverts", () => {
  assertRejected('revert: "feat: add the thing"');
});

test("a subject with no type is rejected", () => assertRejected("Add verification harness for step 0"));

test("an empty subject is rejected", () => assertRejected("feat(scope): "));
