import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { isDeepStrictEqual } from "node:util";
import { readFileSync } from "node:fs";
import type { AgentSignal } from "../../agent-signal.ts";
import { signalsFrom } from "../../agents/devin/signals.ts";

const update = (body: Record<string, unknown>) => ({
  sessionId: "wool-care",
  update: body,
});

describe("signalsFrom", () => {
  it("reads a tool call as started", () => {
    const signals = signalsFrom(
      "session/update",
      update({
        sessionUpdate: "tool_call",
        toolCallId: "c1",
        title: "Ran sleep",
      }),
    );

    assert.deepEqual(signals, [
      { type: "tool_started", toolCallId: "c1", title: "Ran sleep" },
    ]);
  });

  it("reads the plan carried by a switch_mode tool call", () => {
    const signals = signalsFrom(
      "session/update",
      update({
        sessionUpdate: "tool_call",
        toolCallId: "c2",
        title: "Exit plan mode",
        kind: "switch_mode",
        rawInput: { plan: "create hi.txt" },
      }),
    );

    assert.ok(signals.some((entry) => isDeepStrictEqual(entry, {
      type: "plan_proposed",
      plan: "create hi.txt",
    })));
  });

  it("reads a completed or failed tool update as finished, an in_progress one as nothing", () => {
    const status = (value: string) =>
      signalsFrom(
        "session/update",
        update({
          sessionUpdate: "tool_call_update",
          toolCallId: "c1",
          status: value,
        }),
      );

    assert.deepEqual(status("completed"), [
      { type: "tool_finished", toolCallId: "c1" },
    ]);
    assert.deepEqual(status("failed"), [
      { type: "tool_finished", toolCallId: "c1" },
    ]);
    assert.deepEqual(status("in_progress"), []);
  });

  it("reads the background marker of a long-running command", () => {
    const signals = signalsFrom(
      "session/update",
      update({
        sessionUpdate: "tool_call_update",
        toolCallId: "c1",
        status: "in_progress",
        _meta: {
          "cognition.ai/background": true,
          "cognition.ai/backgroundShellId": "19d403",
          "cognition.ai/backgroundCommand": "sleep 45; echo finished",
        },
      }),
    );

    assert.deepEqual(signals, [
      {
        type: "background_started",
        shellId: "19d403",
        command: "sleep 45; echo finished",
        outputPath: null,
      },
    ]);
  });

  it("reads terminal_exit as a background exit with its exit code", () => {
    const signals = signalsFrom(
      "session/update",
      update({
        sessionUpdate: "tool_call_update",
        toolCallId: "c1",
        status: "completed",
        _meta: {
          terminal_exit: { terminal_id: "19d403", exit_code: 0, signal: null },
        },
      }),
    );

    assert.deepEqual(signals, [
      { type: "background_exited", shellId: "19d403", exitCode: 0 },
      { type: "tool_finished", toolCallId: "c1" },
    ]);
  });

  it("reads message chunks and mode changes", () => {
    assert.deepEqual(signalsFrom(
        "session/update",
        update({
          sessionUpdate: "agent_message_chunk",
          content: { type: "text", text: "OK" },
        }),
      ), [{ type: "agent_message", text: "OK" }]);
    assert.deepEqual(signalsFrom(
        "session/update",
        update({
          sessionUpdate: "current_mode_update",
          currentModeId: "bypass",
        }),
      ), [{ type: "mode_changed", mode: "bypass" }]);
  });

  it("reads the agent_stopped cause", () => {
    assert.deepEqual(signalsFrom("_cognition.ai/agent_stopped", {
        cause: "complete",
        stats: {},
      }), [{ type: "agent_stopped", cause: "complete" }]);
  });

  it("drops telemetry and reasoning frames", () => {
    assert.deepEqual(signalsFrom("session/update", update({ sessionUpdate: "usage_update" })), []);
    assert.deepEqual(signalsFrom(
        "session/update",
        update({ sessionUpdate: "agent_thought_chunk" }),
      ), []);
    assert.deepEqual(signalsFrom("_cognition.ai/turn_stats", {}), []);
    assert.deepEqual(signalsFrom("_cognition.ai/mcp/serversChanged", {}), []);
  });

  it("surfaces a vendor notification nobody has read yet", () => {
    const params = { attempt: 2 };

    assert.deepEqual(signalsFrom("_cognition.ai/connection_retry", params), [
      { type: "vendor", method: "_cognition.ai/connection_retry", params },
    ]);
  });
});

type Frame = { dir: string; msg: { method?: string; params?: unknown } };

function signalsOfFixture(name: string): AgentSignal[] {
  return readFileSync(
    new URL(`../../../testdata/autopilot-frames/${name}`, import.meta.url),
    "utf8",
  )
    .trim()
    .split("\n")
    .map((line) => JSON.parse(line) as Frame)
    .filter((frame) => frame.dir === "in" && frame.msg.method !== undefined)
    .flatMap((frame) => signalsFrom(frame.msg.method ?? "", frame.msg.params));
}

// Frames recorded from `devin acp` 3000.12.57: a plan-mode turn that ends in the plan exit.
describe("signalsFrom on a recorded plan-exit turn", () => {
  const signals = signalsOfFixture("devin-3000.12.57-plan-exit.ndjson");

  it("reads the switch to plan mode", () => {
    assert.ok(signals.some((entry) => isDeepStrictEqual(entry, { type: "mode_changed", mode: "plan" })));
  });

  it("reads the plan exit as a started tool and a proposed plan", () => {
    assert.ok(
      signals.some(
        (entry) => entry.type === "tool_started" && entry.title === "Exit plan mode",
      ),
    );
    assert.ok(signals.some((entry) => entry.type === "plan_proposed"));
  });

  it("reads the cause the agent stopped with", () => {
    assert.ok(signals.some((entry) => isDeepStrictEqual(entry, {
      type: "agent_stopped",
      cause: "complete",
    })));
  });
});
