import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { AgentSignal } from "../../agent-signal.ts";
import { signalsFrom } from "../../agents/claude/signals.ts";

type Frame = { dir: string; msg: { method?: string; params?: unknown } };

function signalsOfFixture(name: string): AgentSignal[] {
  const path = new URL(
    `../../../testdata/autopilot-frames/${name}`,
    import.meta.url,
  );
  const frames = readFileSync(path, "utf8")
    .trim()
    .split("\n")
    .map((line) => JSON.parse(line) as Frame)
    .filter((frame) => frame.dir === "in" && frame.msg.method !== undefined);
  return frames.flatMap((frame) =>
    signalsFrom(frame.msg.method ?? "", frame.msg.params),
  );
}

function update(body: Record<string, unknown>): unknown {
  return { sessionId: "s", update: body };
}

describe("signalsFrom a recorded plan-mode turn", () => {
  it("reports the mode change into plan and then into bypassPermissions", () => {
    const signals = signalsOfFixture("claude-0.91.0-plan-question-exit.ndjson");

    const modes = signals.filter((signal) => signal.type === "mode_changed");

    assert.deepEqual(modes, [
      { type: "mode_changed", mode: "plan" },
      { type: "mode_changed", mode: "bypassPermissions" },
    ]);
  });

  it("starts and finishes every tool call it starts", () => {
    const signals = signalsOfFixture("claude-0.91.0-plan-question-exit.ndjson");

    const started = signals.filter((signal) => signal.type === "tool_started");
    const finished = signals.filter(
      (signal) => signal.type === "tool_finished",
    );

    assert.ok(started.length > 0);
    assert.equal(finished.length, started.length);
  });
});

describe("signalsFrom a recorded background command", () => {
  it("reports the command, the task id and the output text of the backgrounded tool call", () => {
    const signals = signalsOfFixture("claude-0.91.0-background-bash.ndjson");

    const command = signals.find((signal) => signal.type === "tool_command");
    const taskId = signals.find(
      (signal) => signal.type === "background_task_id",
    );
    const output = signals.find((signal) => signal.type === "tool_output");

    assert.partialDeepStrictEqual(command, {
      command: "sleep 40 && echo finished > bg.txt",
    });
    assert.partialDeepStrictEqual(taskId, { taskId: "bf6i5c6ud" });
    assert.ok(
      output?.type === "tool_output" &&
        output.text.includes("Output is being written to:"),
    );
  });
});

describe("signalsFrom one frame", () => {
  it("reads a rate limit that was rejected, with its reset time in epoch seconds", () => {
    const frame = update({
      sessionUpdate: "usage_update",
      _meta: {
        "_claude/rateLimit": {
          status: "rejected",
          resetsAt: 1_800_000_000,
          rateLimitType: "five_hour",
        },
      },
    });

    assert.deepEqual(signalsFrom("session/update", frame), [
      {
        type: "rate_limit",
        status: "rejected",
        resetsAtMs: 1_800_000_000_000,
        limitType: "five_hour",
      },
    ]);
  });

  it("keeps a usage update without rate-limit data quiet", () => {
    const frame = update({
      sessionUpdate: "usage_update",
      _meta: { "_claude/model": "claude-sonnet-5-5" },
    });

    assert.deepEqual(signalsFrom("session/update", frame), []);
  });

  it("collects the agent's words", () => {
    const frame = update({
      sessionUpdate: "agent_message_chunk",
      content: { type: "text", text: "Which one?" },
    });

    assert.deepEqual(signalsFrom("session/update", frame), [
      { type: "agent_message", text: "Which one?" },
    ]);
  });

  it("surfaces an unknown session update so nobody misses a new payload", () => {
    const frame = update({ sessionUpdate: "notice", text: "hello" });

    const [signal] = signalsFrom("session/update", frame);

    assert.partialDeepStrictEqual(signal, {
      type: "vendor",
      method: "session/update:notice",
    });
  });

  it("surfaces an unknown _claude/ notification and keeps auth status quiet", () => {
    assert.deepEqual(signalsFrom("_claude/fresh_thing", { a: 1 }), [
      { type: "vendor", method: "_claude/fresh_thing", params: { a: 1 } },
    ]);
    assert.deepEqual(signalsFrom("_auth/status_update", {}), []);
  });

  it("keeps the task list updates quiet", () => {
    const frame = update({ sessionUpdate: "plan", entries: [] });

    assert.deepEqual(signalsFrom("session/update", frame), []);
  });
});
