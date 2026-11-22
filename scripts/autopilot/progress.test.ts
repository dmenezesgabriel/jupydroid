import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  MAX_CRASHES_IN_A_ROW,
  MAX_STALLED_ITERATIONS,
  ProgressTracker,
} from "./progress.ts";
import { assertPartial } from "./testing/assertions.ts";

const DONE = { kind: "done", head: "abc" } as const;
const STALLED = { kind: "stalled", reason: "no commit" } as const;
const CRASHED = { kind: "crashed" } as const;

describe("ProgressTracker", () => {
  it("keeps going after a finished iteration", () => {
    assert.deepEqual(new ProgressTracker().record(DONE), { keepGoing: true });
  });

  it("ends the run on a halt, carrying its reason", () => {
    assert.deepEqual(
      new ProgressTracker().record({ kind: "halted", reason: "weekly_limit" }),
      {
        keepGoing: false,
        reason: "halted: weekly_limit",
      },
    );
  });

  it("ends the run after repeated stalls but forgives one", () => {
    const tracker = new ProgressTracker();
    const verdicts = Array.from({ length: MAX_STALLED_ITERATIONS }, () =>
      tracker.record(STALLED),
    );

    assert.deepEqual(verdicts[0], { keepGoing: true });
    assertPartial(verdicts.at(-1), { keepGoing: false });
  });

  it("forgives a stall that is followed by a finished iteration", () => {
    const tracker = new ProgressTracker();

    tracker.record(STALLED);
    tracker.record(DONE);

    assert.deepEqual(tracker.record(STALLED), { keepGoing: true });
  });

  it("ends the run when the agent keeps crashing", () => {
    const tracker = new ProgressTracker();
    const verdicts = Array.from({ length: MAX_CRASHES_IN_A_ROW }, () =>
      tracker.record(CRASHED),
    );

    assert.deepEqual(verdicts[0], { keepGoing: true });
    assertPartial(verdicts.at(-1), { keepGoing: false });
  });
});
