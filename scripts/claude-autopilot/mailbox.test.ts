import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { Mailbox } from "./mailbox.ts";
import { FakeClock, FakeMailboxFiles } from "./testing/fake-mailbox.ts";
import {
  assertContains,
  assertContainsEqual,
  assertPartial,
} from "./testing/assertions.ts";

const TIMING = { pollMs: 1_000, overdueAfterMs: 5_000, defaultAfterMs: 10_000 };
const QUESTIONS = [
  {
    header: "Library",
    question: "Which library?",
    options: [{ label: "zod" }, { label: "valibot (Recommended)" }],
  },
];

describe("Mailbox.askQuestions", () => {
  it("returns the orchestrator's answer as soon as its file appears", async () => {
    const files = new FakeMailboxFiles();
    const clock = new FakeClock((now) => {
      if (now >= 2_000) files.answers.set("q1", { answer: ["zod"] });
    });

    const answers = await new Mailbox(files, clock, TIMING).askQuestions(
      QUESTIONS,
    );

    assert.deepEqual(answers, ["zod"]);
    assert.deepEqual(files.eventTypes(), ["needs_answer", "answer_received"]);
    assert.equal(files.answers.has("q1"), false);
  });

  it("journals the decision with the reason the orchestrator gave", async () => {
    const files = new FakeMailboxFiles();
    const clock = new FakeClock(() =>
      files.answers.set("q1", { answer: ["zod"], why: "ADR-0004" }),
    );

    await new Mailbox(files, clock, TIMING).askQuestions(QUESTIONS);

    assertContainsEqual(files.events, {
      type: "answer_received",
      id: "q1",
      answer: ["zod"],
      why: "ADR-0004",
    });
  });

  it("flags the question overdue, then defaults to the Recommended option at the deadline", async () => {
    const files = new FakeMailboxFiles();
    const clock = new FakeClock();

    const answers = await new Mailbox(files, clock, TIMING).askQuestions(
      QUESTIONS,
    );

    assert.deepEqual(answers, ["valibot (Recommended)"]);
    assert.deepEqual(files.eventTypes(), [
      "needs_answer",
      "needs_answer_overdue",
      "answer_defaulted",
    ]);
  });

  it("defaults to the first option when none is marked Recommended", async () => {
    const files = new FakeMailboxFiles();
    const questions = [
      {
        header: "H",
        question: "Q?",
        options: [{ label: "a" }, { label: "b" }],
      },
    ];

    const answers = await new Mailbox(
      files,
      new FakeClock(),
      TIMING,
    ).askQuestions(questions);

    assert.deepEqual(answers, ["a"]);
  });

  it("numbers each question so answers cannot cross", async () => {
    const files = new FakeMailboxFiles();
    const mailbox = new Mailbox(files, new FakeClock(), TIMING);

    await mailbox.askQuestions(QUESTIONS);
    await mailbox.askQuestions(QUESTIONS);

    const ids = files.events
      .filter((e) => e.type === "needs_answer")
      .map((e) => e.id);
    assert.deepEqual(ids, ["q1", "q2"]);
  });
});

describe("Mailbox.askReply", () => {
  it("returns the orchestrator's text and shows it what the agent last said", async () => {
    const files = new FakeMailboxFiles();
    const clock = new FakeClock(() =>
      files.answers.set("r1", { answer: "Use the existing helper." }),
    );

    const reply = await new Mailbox(files, clock, TIMING).askReply(
      "no_commit",
      "Shall I add a helper?",
    );

    assert.equal(reply, "Use the existing helper.");
    assertContains(files.eventTypes(), "reply_received");
    assertPartial(files.events[0], {
      type: "needs_reply",
      why: "no_commit",
      lastMessage: "Shall I add a helper?",
    });
  });

  it("sends the fixed nudge for the reason when nobody answers", async () => {
    const files = new FakeMailboxFiles();

    const reply = await new Mailbox(files, new FakeClock(), TIMING).askReply(
      "plan_not_exited",
      "",
    );

    assertContains(reply, "exit_plan_mode");
    assertContains(files.eventTypes(), "reply_defaulted");
  });
});
