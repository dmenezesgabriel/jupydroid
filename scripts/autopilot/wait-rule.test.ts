import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { STANDING_RULES, WAIT_RULE } from "./run.ts";
import { assertContains } from "./testing/assertions.ts";

describe("WAIT_RULE", () => {
  it("says a wait is never a reason to end the turn, and why that costs the session", () => {
    assertContains(WAIT_RULE, "Never end your turn to wait");
    assertContains(WAIT_RULE, "unfinished work");
  });

  it("names devjob as what holds a job until the host has room, so the session starts it", () => {
    assertContains(WAIT_RULE, "devjob");
    assertContains(WAIT_RULE, "background command");
  });

  it("says how to wait: on a result file or a process id, never pgrep -f on text its own command holds", () => {
    // `pgrep -f` reads every command line, the waiting shell's included, so a wait such as
    // `until ! pgrep -f "git commit"` matches itself and ends only when the harness times it out
    // (six iterations lost 30 to 60 minutes each).
    assertContains(WAIT_RULE, "pgrep -f");
    assertContains(WAIT_RULE, "own command line");
    assertContains(WAIT_RULE, "kill -0");
  });

  it("does not tell the session to skip a gate or to bypass the host queue", () => {
    assert.equal(WAIT_RULE.includes("DEVJOB_BYPASS"), false);
    assert.equal(WAIT_RULE.includes("--no-verify"), false);
  });
});

describe("STANDING_RULES", () => {
  it("carries the wait rule, so every session hears it in its opening prompt", () => {
    assertContains(STANDING_RULES, WAIT_RULE);
  });
});
