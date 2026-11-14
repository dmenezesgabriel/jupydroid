import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { ReplyReason, StopReason } from "./decide.ts";
import { ActivityLedger } from "./activity-ledger.ts";
import {
  runIteration,
  type IterationDeps,
  type IterationTiming,
} from "./iteration.ts";
import { ChannelClosedError, RpcError } from "./json-rpc.ts";
import type { LoopEvent } from "./mailbox.ts";
import { FakeAgentSession, FakeGit } from "./testing/fake-agent-session.ts";
import { CallRecorder } from "./testing/call-recorder.ts";
import { FakeClock } from "./testing/fake-mailbox.ts";
import { assertContains, assertContainsEqual } from "./testing/assertions.ts";

const TIMING: IterationTiming = {
  possiblyHungAfterMs: 45 * 60_000,
  cancelAfterSilenceMs: 3 * 60 * 60_000,
  backgroundCeilingMs: 3 * 60 * 60_000,
  limitPollMs: 30 * 60_000,
  limitMarginMs: 60_000,
  watchEveryMs: 60_000,
};

function build(turnsFor: (parts: Parts) => Array<() => StopReason>) {
  const ledger = new ActivityLedger();
  const clock = new FakeClock();
  const git = new FakeGit();
  const events: LoopEvent[] = [];
  const parts: Parts = { ledger, clock, git, events };
  const agent = new FakeAgentSession(turnsFor(parts));
  const askReply = CallRecorder.resolving<[ReplyReason, string], string>(
    "Use the existing helper.",
  );
  const deps: IterationDeps = {
    agent,
    ledger,
    git,
    clock,
    askReply: askReply.fn,
    emit: async (event) => {
      events.push(event);
    },
    openingPrompt: [{ type: "text", text: "run the task" }],
    timing: TIMING,
  };
  return { deps, agent, ...parts, askReply };
}

type Parts = {
  ledger: ActivityLedger;
  clock: FakeClock;
  git: FakeGit;
  events: LoopEvent[];
};

/** The agent leaves plan mode and commits, as the happy path does. */
const commitsInBypass =
  ({ ledger, git }: Parts, sha = "bbb222"): (() => StopReason) =>
  () => {
    ledger.apply({ type: "mode_changed", mode: "bypass" });
    git.headSha = sha;
    return "end_turn";
  };

describe("runIteration", () => {
  it("is done when the agent stopped in bypass with a new commit and a clean tree", async () => {
    const { deps, agent } = build((parts) => [commitsInBypass(parts)]);

    const outcome = await runIteration(deps);

    assert.deepEqual(outcome, { kind: "done", head: "bbb222" });
    assert.deepEqual(agent.modes, ["plan"]);
    assert.deepEqual(agent.promptTexts(), ["run the task"]);
  });

  it("asks the orchestrator, not the clock, when the agent stops while still in plan mode", async () => {
    const { deps, agent, askReply } = build((parts) => [
      () => "end_turn",
      commitsInBypass(parts),
    ]);

    const outcome = await runIteration(deps);

    assert.deepEqual(askReply.calls, [["plan_not_exited", ""]]);
    assert.equal(agent.promptTexts()[1], "Use the existing helper.");
    assert.equal(outcome.kind, "done");
  });

  it("does not call the iteration done while a background command still runs, and tells the agent how it ended", async () => {
    const { deps, agent, ledger, clock, events } = build((parts) => [
      () => {
        parts.ledger.apply({ type: "mode_changed", mode: "bypass" });
        parts.ledger.apply({
          type: "background_started",
          shellId: "19d403",
          command: "./gradlew testDebugUnitTest",
          outputPath: "/o/19d403.output",
        });
        return "end_turn";
      },
      commitsInBypass(parts),
    ]);
    clock.holdSleepsLongerThanMs = TIMING.backgroundCeilingMs - 1;
    const baseEmit = deps.emit;
    deps.emit = async (event) => {
      await baseEmit(event);
      if (event.type === "awaiting_work") {
        ledger.apply({
          type: "background_exited",
          shellId: "19d403",
          exitCode: 0,
        });
      }
    };

    const outcome = await runIteration(deps);

    assertContains(
      events.map((e) => e.type),
      "awaiting_work",
    );
    assertContains(
      agent.promptTexts()[1],
      "`./gradlew testDebugUnitTest` exited with code 0",
    );
    assert.equal(outcome.kind, "done");
  });

  it("halts, reporting the work, when a background command outlives its ceiling", async () => {
    const { deps, clock, events } = build((parts) => [
      () => {
        parts.ledger.apply({ type: "mode_changed", mode: "bypass" });
        parts.ledger.apply({
          type: "background_started",
          shellId: "x",
          command: "./gradlew testDebugUnitTest",
          outputPath: "/o/x.output",
        });
        return "end_turn";
      },
    ]);
    clock.holdSleepsLongerThanMs = TIMING.backgroundCeilingMs + 1;

    const outcome = await runIteration(deps);

    assert.deepEqual(outcome, {
      kind: "halted",
      reason: "background work outlived its ceiling",
    });
    assertContainsEqual(events, {
      type: "possibly_hung",
      running: ["./gradlew testDebugUnitTest"],
    });
  });

  it("waits out a rejected limit the adapter reported and sends continue, not a nudge", async () => {
    // The turn ends normally with the limit text as the agent's last words.
    const { deps, agent, clock, events } = build((parts) => [
      () => {
        parts.ledger.apply({
          type: "rate_limit",
          status: "rejected",
          resetsAtMs: 5 * 60_000,
          limitType: "five_hour",
        });
        return "end_turn";
      },
      commitsInBypass(parts),
    ]);

    const outcome = await runIteration(deps);

    assertContains(clock.sleeps, 5 * 60_000 + TIMING.limitMarginMs);
    assertContains(
      events.map((e) => e.type),
      "rate_limited",
    );
    assert.equal(agent.promptTexts()[1], "continue");
    assert.equal(outcome.kind, "done");
  });

  it("polls with continue when the prompt fails with a limit message and no reset time", async () => {
    const { deps, clock } = build((parts) => [
      () => {
        throw new RpcError(-32000, "Claude usage limit reached");
      },
      commitsInBypass(parts),
    ]);

    await runIteration(deps);

    assertContains(clock.sleeps, TIMING.limitPollMs);
  });

  it("retries an unknown error after a backoff, then continues", async () => {
    const { deps, clock, agent } = build((parts) => [
      () => {
        throw new RpcError(-32603, "Internal error");
      },
      commitsInBypass(parts),
    ]);

    const outcome = await runIteration(deps);

    assertContains(clock.sleeps, 30_000);
    assert.equal(agent.promptTexts()[1], "continue");
    assert.equal(outcome.kind, "done");
  });

  it("halts on the weekly limit instead of waiting days", async () => {
    const { deps } = build(() => [
      () => {
        throw new RpcError(-32000, "You've hit your weekly limit");
      },
    ]);

    assert.deepEqual(await runIteration(deps), {
      kind: "halted",
      reason: "weekly_limit",
    });
  });

  it("reports a crash when the channel closes under a prompt", async () => {
    const { deps } = build(() => [
      () => {
        throw new ChannelClosedError("session/prompt");
      },
    ]);

    assert.deepEqual(await runIteration(deps), { kind: "crashed" });
  });

  it("stalls after two replies that did not produce a commit", async () => {
    const { deps, askReply } = build(() => [
      () => "end_turn",
      () => "end_turn",
      () => "end_turn",
    ]);

    const outcome = await runIteration(deps);

    assert.equal(askReply.calls.length, 2);
    assert.equal(outcome.kind, "stalled");
  });
});

describe("runIteration silence watch", () => {
  it("tells the orchestrator once when the agent has been silent too long, and does not cancel", async () => {
    const { deps, agent, clock, events } = build((parts) => [
      () => {
        clock.advance(TIMING.possiblyHungAfterMs + 1);
        clock.tickAll();
        clock.tickAll();
        return commitsInBypass(parts)();
      },
    ]);

    await runIteration(deps);

    assert.equal(events.filter((e) => e.type === "possibly_hung").length, 1);
    assert.equal(agent.cancelCount, 0);
  });

  it("cancels only after silence no working agent produces", async () => {
    const { deps, agent, clock, events } = build(() => [
      () => {
        clock.advance(TIMING.cancelAfterSilenceMs + 1);
        clock.tickAll();
        return "cancelled";
      },
    ]);

    const outcome = await runIteration(deps);

    assert.equal(agent.cancelCount, 1);
    assertContains(
      events.map((e) => e.type),
      "hung_cancelled",
    );
    assert.deepEqual(outcome, { kind: "halted", reason: "cancelled" });
  });

  it("starts the silence clock with the turn, not with the last frame of an earlier one", async () => {
    const { deps, agent, clock } = build((parts) => [
      () => {
        clock.tickAll();
        return commitsInBypass(parts)();
      },
    ]);
    clock.advance(TIMING.cancelAfterSilenceMs * 2);

    await runIteration(deps);

    assert.equal(agent.cancelCount, 0);
  });
});
