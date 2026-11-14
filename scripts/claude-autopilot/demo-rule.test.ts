import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { DEMO_RULE, STANDING_RULES } from "./run.ts";
import { assertContains } from "./testing/assertions.ts";

describe("DEMO_RULE", () => {
  it("has the agent run the demo itself, as a person would, with adb and Maestro", () => {
    assertContains(DEMO_RULE, "run it yourself");
    assertContains(DEMO_RULE, "as a person would");
    assertContains(DEMO_RULE, "adb");
    assertContains(DEMO_RULE, "Maestro");
  });

  it("asks for screenshots and UI dumps at each step, and for them to be looked at", () => {
    assertContains(DEMO_RULE, "screenshot");
    assertContains(DEMO_RULE, "UI dump");
    assertContains(DEMO_RULE, "look at the screenshots");
  });

  it("says outright that no human ran the demo, and has the commit body say so", () => {
    assertContains(DEMO_RULE, "not by a human");
    assertContains(DEMO_RULE, "Demo run by the agent, not by a human");
  });

  it("keeps the device AGENTS.md names, since a cell run on the emulator proves nothing", () => {
    assertContains(DEMO_RULE, "AGENTS.md");
    assertContains(DEMO_RULE, "arm64 tablet");
  });

  it("tells the session not to stop and ask for a person", () => {
    assertContains(DEMO_RULE, "Do not stop to ask a person");
  });
});

describe("STANDING_RULES", () => {
  it("carries the demo rule, so every session hears it in its opening prompt", () => {
    assertContains(STANDING_RULES, DEMO_RULE);
  });

  it("still sends the session to engineering-mode and ranks AGENTS.md above the skills", () => {
    assertContains(STANDING_RULES, "engineering-mode");
    assert.ok(
      STANDING_RULES.indexOf("AGENTS.md wins") >= 0,
      "the precedence sentence is missing",
    );
  });
});
