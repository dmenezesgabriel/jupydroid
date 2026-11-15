import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { ActivityLedger } from "./activity-ledger.ts";

describe("ActivityLedger", () => {
  it("is idle before anything starts", () => {
    assert.equal(new ActivityLedger().isWorking(), false);
  });

  it("does not count a foreground tool call, even one that never reports again", () => {
    // A tool blocked by a hook arrives already failed and gets no later update.
    const ledger = new ActivityLedger();

    ledger.apply({
      type: "tool_started",
      toolCallId: "blocked",
      title: "Hook block: devjob-guard (pre_tool)",
    });

    assert.equal(ledger.isWorking(), false);
    assert.deepEqual(ledger.runningCommands(), []);
  });

  it("keeps working after the turn when a background shell has not exited", () => {
    const ledger = new ActivityLedger();
    ledger.apply({
      type: "background_started",
      shellId: "19d403",
      command: "sleep 45",
      outputPath: "/o/task.output",
    });

    assert.equal(ledger.isWorking(), true);
    assert.deepEqual(ledger.runningCommands(), ["sleep 45"]);
  });

  it("reports each finished background command once, with its exit code", () => {
    const ledger = new ActivityLedger();
    ledger.apply({
      type: "background_started",
      shellId: "a",
      command: "./gradlew testDebugUnitTest",
      outputPath: "/o/task.output",
    });

    ledger.apply({ type: "background_exited", shellId: "a", exitCode: 1 });

    assert.equal(ledger.isWorking(), false);
    assert.deepEqual(ledger.drainFinishedShells(), [
      { command: "./gradlew testDebugUnitTest", exitCode: 1 },
    ]);
    assert.deepEqual(ledger.drainFinishedShells(), []);
  });

  it("ignores the exit of a shell that never went to the background", () => {
    const ledger = new ActivityLedger();

    ledger.apply({
      type: "background_exited",
      shellId: "foreground",
      exitCode: 0,
    });

    assert.deepEqual(ledger.drainFinishedShells(), []);
  });

  it("resolves whenIdle only after the last running work ends", async () => {
    const ledger = new ActivityLedger();
    ledger.apply({
      type: "background_started",
      shellId: "a",
      command: "./gradlew testDebugUnitTest",
      outputPath: "/o/task.output",
    });
    let released = false;
    const idle = ledger.whenIdle().then(() => {
      released = true;
    });

    await Promise.resolve();
    assert.equal(released, false);
    ledger.apply({ type: "background_exited", shellId: "a", exitCode: 0 });
    await idle;

    assert.equal(released, true);
  });

  it("collects the agent's words per turn", () => {
    const ledger = new ActivityLedger();
    ledger.apply({ type: "agent_message", text: "Which " });
    ledger.apply({ type: "agent_message", text: "one?" });
    assert.equal(ledger.lastMessage(), "Which one?");

    ledger.startTurn();

    assert.equal(ledger.lastMessage(), "");
  });

  it("tracks the mode and the proposed plan", () => {
    const ledger = new ActivityLedger();

    ledger.apply({ type: "mode_changed", mode: "plan" });
    ledger.apply({ type: "plan_proposed", plan: "create hi.txt" });

    assert.equal(ledger.mode, "plan");
    assert.equal(ledger.proposedPlan, "create hi.txt");
  });
});

describe("ActivityLedger rate limit", () => {
  const rejected = {
    type: "rate_limit",
    status: "rejected",
    resetsAtMs: 1_800_000_000_000,
    limitType: "five_hour",
  } as const;

  it("turns a rejected reading into a verdict until its reset time", () => {
    const ledger = new ActivityLedger();

    ledger.apply(rejected);

    assert.deepEqual(ledger.rateLimitVerdict(), {
      kind: "until",
      wakeAt: new Date(1_800_000_000_000),
    });
  });

  it("clears the verdict when the account is allowed again or a new turn starts", () => {
    const ledger = new ActivityLedger();
    ledger.apply(rejected);
    ledger.apply({ ...rejected, status: "allowed" });
    assert.equal(ledger.rateLimitVerdict(), null);

    ledger.apply(rejected);
    ledger.startTurn();

    assert.equal(ledger.rateLimitVerdict(), null);
  });
});

describe("ActivityLedger.apply news", () => {
  it("reports a mode change once, however many frames repeat it", () => {
    const ledger = new ActivityLedger();

    assert.equal(ledger.apply({ type: "mode_changed", mode: "plan" }), true);
    assert.equal(ledger.apply({ type: "mode_changed", mode: "plan" }), false);
    assert.equal(ledger.apply({ type: "mode_changed", mode: "bypass" }), true);
  });

  it("reports a background start once and an exit only for a shell it saw start", () => {
    const ledger = new ActivityLedger();
    const start = {
      type: "background_started",
      shellId: "a",
      command: "run the suite",
      outputPath: "/o/task.output",
    } as const;

    assert.equal(ledger.apply(start), true);
    assert.equal(ledger.apply(start), false);
    assert.equal(
      ledger.apply({ type: "background_exited", shellId: "a", exitCode: 0 }),
      true,
    );
    assert.equal(
      ledger.apply({ type: "background_exited", shellId: "a", exitCode: 0 }),
      false,
    );
    assert.equal(
      ledger.apply({
        type: "background_exited",
        shellId: "foreground",
        exitCode: 0,
      }),
      false,
    );
  });
});
