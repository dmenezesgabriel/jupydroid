import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { classifyClaudeError } from "../../agents/claude/limit-text.ts";

describe("classifyClaudeError (a prompt error that mentions a limit)", () => {
  it("polls on an error that names a usage or rate limit", () => {
    for (const message of [
      "You've hit your limit",
      "Claude usage limit reached",
      "API Error: rate limit exceeded",
    ]) {
      assert.deepEqual(classifyClaudeError({ message }), { kind: "poll" });
    }
  });

  it("halts on an error that names the weekly limit", () => {
    const verdict = classifyClaudeError({
      message: "You've hit your weekly limit",
    });

    assert.deepEqual(verdict, { kind: "weekly" });
  });

  it("does not take any other failure for a limit", () => {
    assert.equal(classifyClaudeError({ message: "Internal error" }), null);
  });
});
