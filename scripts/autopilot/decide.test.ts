import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  decide,
  MAX_ERROR_RETRIES,
  MAX_REPLY_ROUNDS,
  type TurnEnd,
  type World,
} from "./decide.ts";

const END_TURN: TurnEnd = { kind: "stopped", reason: "end_turn" };

const world = (overrides: Partial<World> = {}): World => ({
  mode: "bypassPermissions",
  working: false,
  unreportedExit: false,
  commitsMade: true,
  clean: true,
  replyRounds: 0,
  errorRetries: 0,
  limit: null,
  planDeclined: false,
  ...overrides,
});

describe("decide after end_turn", () => {
  it("finishes only with a new commit and a clean tree outside plan mode", () => {
    assert.deepEqual(decide(END_TURN, world()), { do: "finish" });
  });

  it("waits for running work instead of calling the iteration done", () => {
    // The agent can end its turn while a background suite still runs.
    assert.deepEqual(decide(END_TURN, world({ working: true, commitsMade: false })), {
      do: "await_work",
    });
  });

  it("waits for an exit the agent was never told about instead of asking it for a reply", () => {
    // A command that ends just before the agent stops leaves nothing running
    // and nothing committed: the agent is waiting for that exit, not idle.
    const unreported = { unreportedExit: true };

    assert.deepEqual(decide(END_TURN, world({ ...unreported, commitsMade: false })), { do: "await_work" });
    assert.deepEqual(decide(END_TURN, world({ ...unreported, clean: false })), {
      do: "await_work",
    });
    assert.deepEqual(decide(END_TURN, world({ ...unreported, mode: "plan" })), {
      do: "await_work",
    });
  });

  it("reads the stop as a stall only once the exit was reported", () => {
    const spent = { replyRounds: MAX_REPLY_ROUNDS, commitsMade: false };

    assert.deepEqual(decide(END_TURN, world({ ...spent, unreportedExit: true })), { do: "await_work" });
    assert.deepEqual(decide(END_TURN, world({ ...spent, unreportedExit: false })), { do: "stall" });
  });

  it("still finishes on a commit and a clean tree when an exit is unreported", () => {
    assert.deepEqual(decide(END_TURN, world({ unreportedExit: true })), {
      do: "finish",
    });
  });

  it("waits for the limit instead of nudging an agent whose last words are the limit text", () => {
    const wakeAt = new Date("2026-11-13T22:12:00Z");

    assert.deepEqual(decide(
        END_TURN,
        world({ commitsMade: false, limit: { kind: "until", wakeAt } }),
      ), { do: "wait_until", wakeAt });
    assert.deepEqual(decide(END_TURN, world({ limit: { kind: "weekly" } })), {
      do: "halt",
      reason: "weekly_limit",
    });
  });

  it("asks for a reply when the agent stopped in plan mode", () => {
    assert.deepEqual(decide(END_TURN, world({ mode: "plan" })), {
      do: "ask_reply",
      why: "plan_not_exited",
    });
  });

  it("asks for a reply when no commit landed", () => {
    assert.deepEqual(decide(END_TURN, world({ commitsMade: false })), {
      do: "ask_reply",
      why: "no_commit",
    });
  });

  it("asks for a reply when the tree is dirty after a commit", () => {
    assert.deepEqual(decide(END_TURN, world({ clean: false })), {
      do: "ask_reply",
      why: "dirty_tree",
    });
  });

  it("stalls after the reply rounds are spent", () => {
    assert.deepEqual(decide(
        END_TURN,
        world({ commitsMade: false, replyRounds: MAX_REPLY_ROUNDS }),
      ), { do: "stall" });
  });

  it("finishes, not stalls, when the reply rounds are spent after a commit and files are left over", () => {
    // A commit is progress. The breaker ends a run that stops producing them, and
    // two stalls in a row halt the loop, so an iteration that committed and left
    // work in the tree must not count as one: the next session finds the tree.
    const spent = { replyRounds: MAX_REPLY_ROUNDS, commitsMade: true };

    assert.deepEqual(decide(END_TURN, world({ ...spent, clean: false })), {
      do: "finish",
    });
  });

  it("still stalls with the reply rounds spent when the agent is in plan mode, committed or not", () => {
    const spent = { replyRounds: MAX_REPLY_ROUNDS, clean: false };

    assert.deepEqual(decide(END_TURN, world({ ...spent, mode: "plan" })), {
      do: "stall",
    });
  });
});

describe("decide after other stops", () => {
  it("continues after a token or request ceiling", () => {
    assert.deepEqual(decide({ kind: "stopped", reason: "max_tokens" }, world()), {
      do: "continue",
    });
    assert.deepEqual(decide({ kind: "stopped", reason: "max_turn_requests" }, world()), {
      do: "continue",
    });
  });

  it("halts on a refusal and on a cancel nobody asked for", () => {
    assert.deepEqual(decide({ kind: "stopped", reason: "refusal" }, world()), {
      do: "halt",
      reason: "refusal",
    });
    assert.deepEqual(decide({ kind: "stopped", reason: "cancelled" }, world()), {
      do: "halt",
      reason: "cancelled",
    });
  });

  it("reports a crash", () => {
    assert.deepEqual(decide({ kind: "crashed" }, world()), { do: "crash" });
  });
});

describe("decide after a declined plan exit", () => {
  // Measured on claude-agent-acp 0.91.0: declining the plan exit ends the turn
  // as `cancelled`, before any work, and a later mode switch is accepted.
  const CANCELLED: TurnEnd = { kind: "stopped", reason: "cancelled" };

  it("approves the plan itself when the cancellation is the loop's own decline", () => {
    assert.deepEqual(decide(CANCELLED, world({ mode: "plan", planDeclined: true })), { do: "approve_plan" });
  });

  it("still halts on a cancellation the loop did not cause", () => {
    assert.deepEqual(decide(CANCELLED, world({ mode: "plan" })), {
      do: "halt",
      reason: "cancelled",
    });
  });

  it("approves the plan itself when an agent ends its turn instead of cancelling after the loop's decline", () => {
    // Measured on devin 3000.12.57: a cancelled plan exit leaves the agent
    // talking, and the turn ends normally with the mode still plan.
    assert.deepEqual(decide(END_TURN, world({ mode: "plan", planDeclined: true })), { do: "approve_plan" });
  });

  it("does not read a stop in plan mode as an approval when the loop declined nothing", () => {
    assert.deepEqual(decide(END_TURN, world({ mode: "plan" })), {
      do: "ask_reply",
      why: "plan_not_exited",
    });
  });
});

describe("decide after an error", () => {
  const failed = (message: string): TurnEnd => ({
    kind: "failed",
    error: { message },
  });

  it("waits until the reset the adapter reported before the prompt failed", () => {
    const wakeAt = new Date("2026-11-13T22:12:00Z");

    assert.deepEqual(decide(
        failed("You've hit your limit"),
        world({ limit: { kind: "until", wakeAt } }),
      ), { do: "wait_until", wakeAt });
  });

  it("acts on the usage limit the failure was classified as", () => {
    const wakeAt = new Date("2026-11-13T22:12:00Z");

    assert.deepEqual(decide(failed("any words"), world({ limit: { kind: "poll" } })), { do: "poll_limit" });
    assert.deepEqual(decide(failed("any words"), world({ limit: { kind: "until", wakeAt } })), { do: "wait_until", wakeAt });
    assert.deepEqual(decide(failed("any words"), world({ limit: { kind: "weekly" } })), { do: "halt", reason: "weekly_limit" });
    assert.deepEqual(decide(failed("any words"), world({ limit: { kind: "monthly" } })), { do: "halt", reason: "monthly_limit" });
  });

  it("retries an unknown error with a doubling backoff, then halts", () => {
    assert.deepEqual(decide(failed("Internal error"), world({ errorRetries: 0 })), {
      do: "retry",
      backoffMs: 30_000,
    });
    assert.deepEqual(decide(failed("Internal error"), world({ errorRetries: 1 })), {
      do: "retry",
      backoffMs: 60_000,
    });
    assert.deepEqual(decide(
        failed("Internal error"),
        world({ errorRetries: MAX_ERROR_RETRIES }),
      ), {
      do: "halt",
      reason: "error",
    });
  });
});
