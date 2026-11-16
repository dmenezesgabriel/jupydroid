import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { ActivityLedger } from "./activity-ledger.ts";
import { buildHandlers } from "./handlers.ts";
import type { Question } from "./claude-agent.ts";
import type { LoopEvent } from "./mailbox.ts";
import { CallRecorder } from "./testing/call-recorder.ts";
import { FakeClock } from "./testing/fake-mailbox.ts";
import {
  assertContainsEqual,
  assertPartial,
  waitUntil,
} from "./testing/assertions.ts";

function build() {
  const ledger = new ActivityLedger();
  const clock = new FakeClock();
  const events: LoopEvent[] = [];
  const askQuestions = CallRecorder.resolving<[Question[]], string[]>(["red"]);
  const watchBackground = CallRecorder.silent<[string, string]>();
  const handlers = buildHandlers({
    ledger,
    clock,
    emit: async (event) => {
      events.push(event);
    },
    askQuestions: askQuestions.fn,
    watchBackground: watchBackground.fn,
  });
  return { ledger, clock, events, askQuestions, watchBackground, handlers };
}

const EXIT_PLAN_OPTIONS = [
  { optionId: "exit-plan-default", name: "manual", kind: "allow_once" },
  { optionId: "exit-plan-auto", name: "auto", kind: "allow_always" },
  { optionId: "exit-plan-bypass", name: "bypass", kind: "allow_always" },
  { optionId: "reject", name: "No, keep planning", kind: "reject_once" },
];
const EXIT_PLAN = {
  toolTitle: "Approve Plan",
  toolName: "ExitPlanMode",
  isExitPlan: true,
};

describe("buildHandlers permissions", () => {
  it("leaves plan mode into bypass, the choice made by hand in the REPL", async () => {
    const { handlers } = build();

    const chosen = await handlers.onPermission({
      ...EXIT_PLAN,
      options: EXIT_PLAN_OPTIONS,
    });

    assert.equal(chosen, "exit-plan-bypass");
  });

  it("declines the plan exit when bypass is not offered, and says which options were", async () => {
    const { handlers, events } = build();
    const withoutBypass = EXIT_PLAN_OPTIONS.filter(
      (option) => option.optionId !== "exit-plan-bypass",
    );

    const chosen = await handlers.onPermission({
      ...EXIT_PLAN,
      options: withoutBypass,
    });

    assert.equal(chosen, null);
    assertContainsEqual(events, {
      type: "exit_plan_bypass_missing",
      offered: ["exit-plan-default", "exit-plan-auto", "reject"],
    });
  });

  it("allows an ordinary prompt once, never always, and records it", async () => {
    const { handlers, events } = build();

    const chosen = await handlers.onPermission({
      toolTitle: "rm -rf build",
      toolName: "Bash",
      isExitPlan: false,
      options: [
        { optionId: "always", name: "Always", kind: "allow_always" },
        { optionId: "once", name: "Once", kind: "allow_once" },
        { optionId: "no", name: "No", kind: "reject_once" },
      ],
    });

    assert.equal(chosen, "once");
    assertContainsEqual(events, {
      type: "permission_requested",
      tool: "rm -rf build",
      chose: "once",
    });
  });

  it("declines an ordinary prompt that offers no allow-once option", async () => {
    const { handlers } = build();

    const chosen = await handlers.onPermission({
      toolTitle: "x",
      toolName: "Bash",
      isExitPlan: false,
      options: [{ optionId: "no", name: "No", kind: "reject_once" }],
    });

    assert.equal(chosen, null);
  });
});

describe("buildHandlers signals", () => {
  it("feeds signals to the ledger and counts a frame as the agent being alive", () => {
    const { handlers, ledger, clock } = build();
    clock.advance(5_000);

    handlers.onFrame();
    handlers.onSignal({ type: "mode_changed", mode: "bypassPermissions" });

    assert.equal(ledger.lastFrameAtMs, 5_000);
    assert.equal(ledger.mode, "bypassPermissions");
  });

  it("announces the plan with a bounded preview", async () => {
    const { handlers, events } = build();

    handlers.onSignal({ type: "plan_proposed", plan: "x".repeat(1_000) });
    await waitUntil(() => assert.equal(events.length, 1));

    assertPartial(events[0], { type: "plan_ready" });
    assert.equal(String(events[0]?.["plan"]).length, 400);
  });

  it("surfaces an unread vendor payload", async () => {
    const { handlers, events } = build();

    handlers.onSignal({
      type: "vendor",
      method: "_claude/fresh_thing",
      params: { attempt: 2 },
    });
    await waitUntil(() => assert.equal(events.length, 1));

    assertPartial(events[0], {
      type: "agent_signal",
      method: "_claude/fresh_thing",
    });
  });

  it("journals a rejected rate limit with its reset time, and nothing for an allowed one", async () => {
    const { handlers, events } = build();

    handlers.onSignal({
      type: "rate_limit",
      status: "allowed",
      resetsAtMs: 1_800_000_000_000,
      limitType: "five_hour",
    });
    handlers.onSignal({
      type: "rate_limit",
      status: "rejected",
      resetsAtMs: 1_800_000_000_000,
      limitType: "five_hour",
    });
    await waitUntil(() => assert.equal(events.length, 1));

    assert.deepEqual(events, [
      {
        type: "rate_limit_rejected",
        limitType: "five_hour",
        resetsAt: "2027-01-15T08:00:00.000Z",
      },
    ]);
  });

  it("sends questions to the orchestrator's mailbox", async () => {
    const { handlers, askQuestions } = build();
    const questions = [
      { header: "H", question: "Q?", options: [{ label: "red" }] },
    ];

    assert.deepEqual(await handlers.onQuestions(questions), ["red"]);
    assert.deepEqual(askQuestions.calls, [[questions]]);
  });
});

describe("buildHandlers journal", () => {
  const started = {
    type: "background_started",
    shellId: "a",
    command: "run the suite",
    outputPath: "/o/a.output",
  } as const;

  it("journals the mode, and each background command's start and exit", async () => {
    const { handlers, events } = build();

    handlers.onSignal({ type: "mode_changed", mode: "bypassPermissions" });
    handlers.onSignal(started);
    handlers.onSignal({ type: "background_exited", shellId: "a", exitCode: 1 });
    await waitUntil(() => assert.equal(events.length, 3));

    assert.deepEqual(events, [
      { type: "mode_changed", mode: "bypassPermissions" },
      { type: "background_started", shellId: "a", command: "run the suite" },
      { type: "background_exited", shellId: "a", exitCode: 1 },
    ]);
  });

  it("starts watching a background command's output file once, when it starts", () => {
    const { handlers, watchBackground } = build();

    handlers.onSignal(started);
    handlers.onSignal(started);

    assert.deepEqual(watchBackground.calls, [["a", "/o/a.output"]]);
  });

  it("journals a repeated mode once and skips the exit of a command it never saw start", async () => {
    const { handlers, events } = build();

    handlers.onSignal({ type: "mode_changed", mode: "plan" });
    handlers.onSignal({ type: "mode_changed", mode: "plan" });
    handlers.onSignal({
      type: "background_exited",
      shellId: "foreground",
      exitCode: 0,
    });
    handlers.onSignal({ type: "mode_changed", mode: "bypassPermissions" });
    await waitUntil(() => assert.equal(events.length, 2));

    assert.deepEqual(events, [
      { type: "mode_changed", mode: "plan" },
      { type: "mode_changed", mode: "bypassPermissions" },
    ]);
  });
});
