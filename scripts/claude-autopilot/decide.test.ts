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
  commitsMade: true,
  clean: true,
  replyRounds: 0,
  errorRetries: 0,
  limit: null,
  ...overrides,
});

describe("decide after end_turn", () => {
  it("finishes only with a new commit and a clean tree outside plan mode", () => {
    assert.deepEqual(decide(END_TURN, world()), { do: "finish" });
  });

  it("waits for running work instead of calling the iteration done", () => {
    // The agent can end its turn while a background suite still runs.
    assert.deepEqual(
      decide(END_TURN, world({ working: true, commitsMade: false })),
      {
        do: "await_work",
      },
    );
  });

  it("waits for the limit instead of nudging an agent whose last words are the limit text", () => {
    const wakeAt = new Date("2026-11-13T22:12:00Z");

    assert.deepEqual(
      decide(
        END_TURN,
        world({ commitsMade: false, limit: { kind: "until", wakeAt } }),
      ),
      { do: "wait_until", wakeAt },
    );
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
    assert.deepEqual(
      decide(
        END_TURN,
        world({ commitsMade: false, replyRounds: MAX_REPLY_ROUNDS }),
      ),
      { do: "stall" },
    );
  });
});

describe("decide after other stops", () => {
  it("continues after a token or request ceiling", () => {
    assert.deepEqual(
      decide({ kind: "stopped", reason: "max_tokens" }, world()),
      {
        do: "continue",
      },
    );
    assert.deepEqual(
      decide({ kind: "stopped", reason: "max_turn_requests" }, world()),
      {
        do: "continue",
      },
    );
  });

  it("halts on a refusal and on a cancel nobody asked for", () => {
    assert.deepEqual(decide({ kind: "stopped", reason: "refusal" }, world()), {
      do: "halt",
      reason: "refusal",
    });
    assert.deepEqual(
      decide({ kind: "stopped", reason: "cancelled" }, world()),
      {
        do: "halt",
        reason: "cancelled",
      },
    );
  });

  it("reports a crash", () => {
    assert.deepEqual(decide({ kind: "crashed" }, world()), { do: "crash" });
  });
});

describe("decide after an error", () => {
  const failed = (message: string): TurnEnd => ({
    kind: "failed",
    error: { message },
  });

  it("waits until the reset the adapter reported before the prompt failed", () => {
    const wakeAt = new Date("2026-11-13T22:12:00Z");

    assert.deepEqual(
      decide(
        failed("You've hit your limit"),
        world({ limit: { kind: "until", wakeAt } }),
      ),
      { do: "wait_until", wakeAt },
    );
  });

  it("polls on an error that names a limit and halts on a weekly one", () => {
    assert.deepEqual(decide(failed("Claude usage limit reached"), world()), {
      do: "poll_limit",
    });
    assert.deepEqual(decide(failed("You've hit your weekly limit"), world()), {
      do: "halt",
      reason: "weekly_limit",
    });
  });

  it("retries an unknown error with a doubling backoff, then halts", () => {
    assert.deepEqual(
      decide(failed("Internal error"), world({ errorRetries: 0 })),
      {
        do: "retry",
        backoffMs: 30_000,
      },
    );
    assert.deepEqual(
      decide(failed("Internal error"), world({ errorRetries: 1 })),
      {
        do: "retry",
        backoffMs: 60_000,
      },
    );
    assert.deepEqual(
      decide(
        failed("Internal error"),
        world({ errorRetries: MAX_ERROR_RETRIES }),
      ),
      {
        do: "halt",
        reason: "error",
      },
    );
  });
});
