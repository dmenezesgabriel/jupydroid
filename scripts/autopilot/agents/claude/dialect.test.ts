import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { AcpAgent, type AgentHandlers } from "../../acp-agent.ts";
import type { PermissionAsk, Question } from "../../agent-dialect.ts";
import type { AgentSignal } from "../../agent-signal.ts";
import { assertPartial, waitUntil } from "../../testing/assertions.ts";
import { CallRecorder } from "../../testing/call-recorder.ts";
import { FakeLineChannel } from "../../testing/fake-line-channel.ts";
import { ClaudeDialect } from "./dialect.ts";

function connect(
  answers: string[] | null = ["red"],
  choice: string | null = "exit-plan-bypass",
) {
  const channel = new FakeLineChannel();
  const onSignal = CallRecorder.silent<[AgentSignal]>();
  const onFrame = CallRecorder.silent<[]>();
  const onQuestions = CallRecorder.resolving<[Question[]], string[] | null>(
    answers,
  );
  const onPermission = CallRecorder.resolving<[PermissionAsk], string | null>(
    choice,
  );
  const handlers: AgentHandlers = {
    onSignal: onSignal.fn,
    onFrame: onFrame.fn,
    onQuestions: onQuestions.fn,
    onPermission: onPermission.fn,
  };
  return {
    channel,
    onSignal,
    onFrame,
    onQuestions,
    onPermission,
    agent: new AcpAgent(channel, handlers, new ClaudeDialect()),
  };
}

// The frame shapes below follow what `claude-agent-acp` 0.91.0 sent in the recorded turns.
function questionFrame(properties: Record<string, unknown>) {
  return {
    id: 7,
    method: "elicitation/create",
    params: {
      mode: "form",
      sessionId: "s1",
      toolCallId: "toolu_1",
      message: "Which color?",
      requestedSchema: { type: "object", properties },
    },
  };
}

const COLOR_FIELD = {
  type: "string",
  title: "Color",
  oneOf: [
    { const: "red", title: "red", description: "Write red" },
    { const: "blue", title: "blue" },
  ],
};
const OTHER_FIELD = { type: "string", title: "Other" };

describe("AcpAgent handshake with the Claude dialect", () => {
  it("declares form elicitation, without which Claude never routes a question to us", async () => {
    const { channel, agent } = connect();

    const initializing = agent.initialize();
    channel.deliver({ id: 1, result: { protocolVersion: 1 } });
    await initializing;

    assertPartial(channel.sent[0], {
      method: "initialize",
      params: { clientCapabilities: { elicitation: { form: {} } } },
    });
  });

  it("opens a session in the cwd and selects a mode through the config option", async () => {
    const { channel, agent } = connect();

    const opening = agent.newSession("/repo");
    channel.deliver({ id: 1, result: { sessionId: "s1" } });
    assert.equal(await opening, "s1");
    const switching = agent.setMode("plan");
    channel.deliver({ id: 2, result: { configOptions: [] } });
    await switching;

    assertPartial(channel.sent[0], {
      method: "session/new",
      params: { cwd: "/repo", mcpServers: [] },
    });
    assertPartial(channel.sent[1], {
      method: "session/set_config_option",
      params: { sessionId: "s1", configId: "mode", value: "plan" },
    });
  });

  it("resolves a prompt with its stop reason", async () => {
    const { channel, agent } = connect();
    const opening = agent.newSession("/repo");
    channel.deliver({ id: 1, result: { sessionId: "s1" } });
    await opening;

    const prompting = agent.prompt([{ type: "text", text: "go" }]);
    channel.deliver({ id: 2, result: { stopReason: "end_turn" } });

    assert.equal(await prompting, "end_turn");
  });
});

describe("Claude questions", () => {
  it("hands each select field to the handler and accepts with the label keyed by field", async () => {
    const { channel, onQuestions } = connect(["blue"]);

    channel.deliver(
      questionFrame({
        question_0: COLOR_FIELD,
        question_0_custom: OTHER_FIELD,
      }),
    );
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assert.deepEqual(onQuestions.calls, [
      [
        [
          {
            header: "Color",
            question: "Which color?",
            options: [
              { label: "red", description: "Write red" },
              { label: "blue", description: undefined },
            ],
          },
        ],
      ],
    ]);
    assert.deepEqual(channel.lastSent(), {
      jsonrpc: "2.0",
      id: 7,
      result: { action: "accept", content: { question_0: "blue" } },
    });
  });

  it("sends an answer that is not an offered label as the custom answer", async () => {
    const { channel } = connect(["green, please"]);

    channel.deliver(
      questionFrame({
        question_0: COLOR_FIELD,
        question_0_custom: OTHER_FIELD,
      }),
    );
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.lastSent(), {
      result: {
        action: "accept",
        content: { question_0_custom: "green, please" },
      },
    });
  });

  it("answers a multi-select question with a list", async () => {
    const multi = {
      type: "array",
      title: "Colors",
      description: "Which colors?",
      items: { anyOf: [{ const: "red", title: "red" }] },
    };
    const { channel } = connect(["red"]);

    channel.deliver(questionFrame({ question_0: multi }));
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.lastSent(), {
      result: { content: { question_0: ["red"] } },
    });
  });

  it("cancels the form when the handler returns no answers", async () => {
    const { channel } = connect(null);

    channel.deliver(questionFrame({ question_0: COLOR_FIELD }));
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.lastSent(), { result: { action: "cancel" } });
  });
});

describe("Claude permissions", () => {
  const exitPlanRequest = {
    id: 9,
    method: "session/request_permission",
    params: {
      sessionId: "s1",
      toolCall: {
        toolCallId: "toolu_2",
        name: "ExitPlanMode",
        kind: "switch_mode",
        title: "Approve Plan",
        rawInput: { plan: "create color.txt" },
      },
      options: [
        { optionId: "exit-plan-default", name: "Yes", kind: "allow_once" },
        { optionId: "exit-plan-bypass", name: "Bypass", kind: "allow_always" },
        { optionId: "reject", name: "No", kind: "reject_once" },
      ],
    },
  };

  it("tells the handler the request is the plan exit, with the plan, and sends back its choice", async () => {
    const { channel, onPermission, onSignal } = connect();

    channel.deliver(exitPlanRequest);
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(onPermission.calls[0]?.[0], {
      isExitPlan: true,
      toolTitle: "Approve Plan",
    });
    assert.deepEqual(onSignal.calls, [
      [{ type: "plan_proposed", plan: "create color.txt" }],
    ]);
    assertPartial(channel.lastSent(), {
      id: 9,
      result: {
        outcome: { outcome: "selected", optionId: "exit-plan-bypass" },
      },
    });
  });

  it("does not take an ordinary tool request for the plan exit", async () => {
    const { channel, onPermission } = connect(["red"], "allow");

    channel.deliver({
      id: 10,
      method: "session/request_permission",
      params: {
        sessionId: "s1",
        toolCall: {
          toolCallId: "t3",
          name: "Bash",
          kind: "execute",
          title: "ls",
        },
        options: [{ optionId: "allow", name: "Yes", kind: "allow_once" }],
      },
    });
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(onPermission.calls[0]?.[0], {
      isExitPlan: false,
      toolName: "Bash",
    });
  });

  it("reports a cancelled outcome when the handler declines to choose", async () => {
    const { channel } = connect(["red"], null);

    channel.deliver(exitPlanRequest);
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.lastSent(), {
      result: { outcome: { outcome: "cancelled" } },
    });
  });
});

describe("Claude notifications", () => {
  it("announces a background task once its command, id and output file are all known", () => {
    const { channel, onSignal } = connect();
    const update = (body: Record<string, unknown>) =>
      channel.deliver({
        method: "session/update",
        params: {
          sessionId: "s1",
          update: {
            sessionUpdate: "tool_call_update",
            toolCallId: "t",
            ...body,
          },
        },
      });

    update({ rawInput: { command: "pnpm test" } });
    update({
      _meta: { claudeCode: { toolResponse: { backgroundTaskId: "b1" } } },
    });
    update({
      rawOutput: "Output is being written to: /x/tasks/b1.output. Done.",
    });

    assert.ok(
      onSignal.calls.some(([signal]) =>
        isEqual(signal, {
          type: "background_started",
          shellId: "b1",
          command: "pnpm test",
          outputPath: "/x/tasks/b1.output",
        }),
      ),
    );
  });

  it("counts every incoming frame as liveness", () => {
    const { channel, onFrame } = connect();

    channel.deliver({ method: "session/update", params: { update: {} } });

    assert.ok(onFrame.calls.length > 0);
  });
});

function isEqual(actual: unknown, wanted: unknown): boolean {
  try {
    assert.deepEqual(actual, wanted);
    return true;
  } catch {
    return false;
  }
}
