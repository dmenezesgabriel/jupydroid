import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { ActivityLedger } from "./activity-ledger.ts";
import { buildHandlers } from "./handlers.ts";
import type { Question } from "./agent-dialect.ts";
import type { ExitPlanOptions } from "./agent-profile.ts";
import { CLAUDE_PROFILE } from "./agents/claude/profile.ts";
import { DEVIN_PROFILE } from "./agents/devin/profile.ts";
import type { LoopEvent } from "./mailbox.ts";
import { CallRecorder } from "./testing/call-recorder.ts";
import { FakeClock } from "./testing/fake-mailbox.ts";
import {
  assertContainsEqual,
  assertPartial,
  waitUntil,
} from "./testing/assertions.ts";

function build(
  declinePlanExit = false,
  exitPlan: ExitPlanOptions = CLAUDE_PROFILE.exitPlan,
) {
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
    declinePlanExit,
    exitPlan,
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

describe("buildHandlers plan exit follow-up", () => {
  it("declines the plan exit with the reject option and remembers it, once a plan is proposed", async () => {
    const { handlers, ledger } = build(true);
    handlers.onSignal({ type: "plan_proposed", plan: "create hi.txt" });

    const chosen = await handlers.onPermission({
      ...EXIT_PLAN,
      options: EXIT_PLAN_OPTIONS,
    });

    assert.equal(chosen, "reject");
    assert.equal(ledger.planDeclined, true);
  });

  it("declines the plan exit by cancelling when the agent has no reject option, and remembers it", async () => {
    const { handlers, ledger, events } = build(true, DEVIN_PROFILE.exitPlan);
    handlers.onSignal({ type: "plan_proposed", plan: "create hi.txt" });

    const chosen = await handlers.onPermission({
      ...EXIT_PLAN,
      options: [
        { optionId: "plan_bypass", name: "Yes", kind: "allow_once" },
        { optionId: "reject_once", name: "No", kind: "reject_once" },
      ],
    });

    assert.equal(chosen, null);
    assert.equal(ledger.planDeclined, true);
    assert.ok(
      !events
        .map((event) => event.type)
        .includes("exit_plan_reject_missing"),
    );
  });

  it("leaves plan mode through the bypass option the agent names", async () => {
    const { handlers } = build(false, DEVIN_PROFILE.exitPlan);

    const chosen = await handlers.onPermission({
      ...EXIT_PLAN,
      options: [
        { optionId: "plan_accept_edits", name: "Edits", kind: "allow_once" },
        { optionId: "plan_bypass", name: "Bypass", kind: "allow_once" },
      ],
    });

    assert.equal(chosen, "plan_bypass");
  });

  it("leaves plan mode into bypass when there is no plan text to hand to a follow-up", async () => {
    const { handlers, ledger } = build(true);

    const chosen = await handlers.onPermission({
      ...EXIT_PLAN,
      options: EXIT_PLAN_OPTIONS,
    });

    assert.equal(chosen, "exit-plan-bypass");
    assert.equal(ledger.planDeclined, false);
  });

  it("halts instead of improvising when the reject option is not offered, and says which were", async () => {
    const { handlers, events, ledger } = build(true);
    handlers.onSignal({ type: "plan_proposed", plan: "create hi.txt" });
    const withoutReject = EXIT_PLAN_OPTIONS.filter(
      (option) => option.optionId !== "reject",
    );

    const chosen = await handlers.onPermission({
      ...EXIT_PLAN,
      options: withoutReject,
    });

    assert.equal(chosen, null);
    assert.equal(ledger.planDeclined, false);
    assertContainsEqual(events, {
      type: "exit_plan_reject_missing",
      offered: ["exit-plan-default", "exit-plan-auto", "exit-plan-bypass"],
    });
  });

  it("still allows a forced prompt for another tool while declining plan exits", async () => {
    const { handlers } = build(true);

    const chosen = await handlers.onPermission({
      toolTitle: "ls",
      toolName: "Bash",
      isExitPlan: false,
      options: [{ optionId: "allow", name: "Allow", kind: "allow_once" }],
    });

    assert.equal(chosen, "allow");
  });
});
