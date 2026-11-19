import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { splitWhy, useArguments } from "./ctl.ts";

describe("splitWhy", () => {
  it("leaves the arguments alone when no reason is given", () => {
    assert.deepEqual(splitWhy(["zod", "valibot"]), {
      values: ["zod", "valibot"],
    });
  });

  it("takes the reason off the front so it can be journaled with the answer", () => {
    assert.deepEqual(splitWhy(["--why", "ADR-0004 keeps zod", "zod"]), {
      why: "ADR-0004 keeps zod",
      values: ["zod"],
    });
  });
});

describe("useArguments", () => {
  it("reads the agent, the model and the effort, in any order after the agent", () => {
    assert.deepEqual(
      useArguments(["devin", "--effort", "high", "--model", "swe-2-high"]),
      { agent: "devin", model: "swe-2-high", effort: "high" },
    );
  });

  it("leaves the model and the effort on the agent's own default when none is named, so a switch can reset them", () => {
    assert.deepEqual(useArguments(["claude"]), {
      agent: "claude",
      model: null,
      effort: null,
    });
  });

  it("refuses an unknown agent, listing the ones the loop can drive", () => {
    assert.throws(
      () => useArguments(["gemini"]),
      (error: Error) =>
        error.message.includes('"gemini"') &&
        error.message.includes("claude, devin"),
    );
  });

  it("refuses no agent, and a flag with no value, instead of switching to something nobody asked for", () => {
    assert.throws(() => useArguments([]), /needs one of claude, devin, got none/);
    assert.throws(
      () => useArguments(["devin", "--model"]),
      /--model needs a model id the agent offers, got none/,
    );
    assert.throws(
      () => useArguments(["devin", "--bogus", "x"]),
      (error: Error) => error.message.includes('"--bogus"'),
    );
  });
});
