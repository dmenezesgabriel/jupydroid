import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  describeSummary,
  summarizeJournal,
  type JournalEvent,
} from "./journal-summary.ts";
import { assertContains, assertPartial } from "./testing/assertions.ts";

let clock = 0;
const at = (
  type: string,
  detail: Record<string, unknown> = {},
): JournalEvent => ({
  at: `t${++clock}`,
  type,
  ...detail,
});

const STARTED = [
  at("loop_started"),
  at("iteration_started", { row: "Items", head: "aaa" }),
  at("session_started", { sessionId: "wool-care" }),
  at("mode_changed", { mode: "plan" }),
];

describe("summarizeJournal", () => {
  it("is never_started for an empty journal", () => {
    assertPartial(summarizeJournal([]), {
      loop: "never_started",
      open: null,
    });
  });

  it("tracks the open iteration's session and mode", () => {
    const summary = summarizeJournal(STARTED);

    assert.equal(summary.loop, "running");
    assertPartial(summary.open, {
      n: 1,
      row: "Items",
      sessionId: "wool-care",
      mode: "plan",
    });
  });

  it("keeps a question pending until its answer arrives, then keeps the decision and its reason", () => {
    const asked = [...STARTED, at("needs_answer", { id: "q1" })];
    assertPartial(summarizeJournal(asked).open?.pending, [
      { id: "q1", kind: "question" },
    ]);

    const answered = summarizeJournal([
      ...asked,
      at("answer_received", {
        id: "q1",
        answer: ["zod"],
        why: "ADR-0004 keeps the schema library",
      }),
    ]);

    assert.deepEqual(answered.open?.pending, []);
    assertPartial(answered.decisions, [
      {
        id: "q1",
        how: "answered",
        answer: ["zod"],
        why: "ADR-0004 keeps the schema library",
      },
    ]);
  });

  it("marks an overdue question and records a defaulted one as a defaulted decision", () => {
    const events = [
      ...STARTED,
      at("needs_answer", { id: "q1" }),
      at("needs_answer_overdue", { id: "q1" }),
    ];
    assert.equal(summarizeJournal(events).open?.pending[0]?.overdue, true);

    const defaulted = summarizeJournal([
      ...events,
      at("answer_defaulted", { id: "q1" }),
    ]);

    assertPartial(defaulted.decisions[0], {
      how: "defaulted",
      why: null,
    });
    assert.deepEqual(defaulted.open?.pending, []);
  });

  it("lists background commands from start to exit", () => {
    const running = [
      ...STARTED,
      at("background_started", { shellId: "19d403", command: "run the suite" }),
    ];
    assert.deepEqual(summarizeJournal(running).open?.running, [
      "19d403: run the suite",
    ]);

    const exited = summarizeJournal([
      ...running,
      at("background_exited", { shellId: "19d403", exitCode: 0 }),
    ]);

    assert.deepEqual(exited.open?.running, []);
  });

  it("shows a rate limit until the next turn ends", () => {
    const limited = [...STARTED, at("rate_limited", { wakeAt: "22:01" })];
    assert.equal(summarizeJournal(limited).open?.rateLimitedUntil, "22:01");

    assert.equal(
      summarizeJournal([...limited, at("turn_ended")]).open?.rateLimitedUntil,
      null,
    );
  });

  it("moves a finished iteration out of open with its outcome and commit", () => {
    const summary = summarizeJournal([
      ...STARTED,
      at("iteration_done", { head: "bbb222" }),
    ]);

    assert.equal(summary.open, null);
    assertPartial(summary.finished, [
      { n: 1, row: "Items", outcome: "done", detail: "bbb222" },
    ]);
  });

  it("numbers iterations across the run and records an interrupted one", () => {
    const summary = summarizeJournal([
      ...STARTED,
      at("iteration_interrupted"),
      at("iteration_started", { row: "Items", head: "aaa" }),
    ]);

    assertPartial(summary.finished[0], { n: 1, outcome: "interrupted" });
    assert.equal(summary.open?.n, 2);
  });

  it("reads how the loop ended", () => {
    assertPartial(
      summarizeJournal([
        at("loop_started"),
        at("loop_halted", { reason: "halted: weekly_limit" }),
      ]),
      {
        loop: "halted",
        haltReason: "halted: weekly_limit",
      },
    );
    assert.equal(
      summarizeJournal([at("loop_started"), at("backlog_exhausted")]).loop,
      "exhausted",
    );
    assert.equal(
      summarizeJournal([at("loop_started"), at("stop")]).loop,
      "stopped",
    );
  });
});

describe("describeSummary", () => {
  it("names what runs, what waits and what was decided, and warns when the loop's pid is gone", () => {
    const summary = summarizeJournal([
      ...STARTED,
      at("background_started", { shellId: "a", command: "run the suite" }),
      at("answer_received", {
        id: "q0",
        answer: ["hi"],
        why: "closest to the original",
      }),
      at("needs_reply", { id: "r1" }),
    ]);

    const text = describeSummary(summary, 4242, false);

    assertContains(text, "pid 4242 is NOT running");
    assertContains(text, "running: a: run the suite");
    assertContains(text, "waiting for reply r1");
    assertContains(text, 'q0 answered ["hi"] because closest to the original');
  });
});
