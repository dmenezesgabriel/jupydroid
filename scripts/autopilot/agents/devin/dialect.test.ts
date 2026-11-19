import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";
import { AcpAgent, type AgentHandlers } from "../../acp-agent.ts";
import type { PermissionAsk, Question } from "../../agent-dialect.ts";
import type { AgentSignal } from "../../agent-signal.ts";
import { assertPartial, waitUntil } from "../../testing/assertions.ts";
import { CallRecorder } from "../../testing/call-recorder.ts";
import { FakeLineChannel } from "../../testing/fake-line-channel.ts";
import { DevinDialect } from "./dialect.ts";

type Answers = CallRecorder<[Question[]], Promise<string[] | null>>;
type Choices = CallRecorder<[PermissionAsk], Promise<string | null>>;

function connect(
  answers: string[] | null = ["hi.txt"],
  choice: string | null = "plan_bypass",
) {
  const channel = new FakeLineChannel();
  const onSignal = CallRecorder.silent<[AgentSignal]>();
  const onFrame = CallRecorder.silent<[]>();
  const onQuestions: Answers = CallRecorder.resolving(answers);
  const onPermission: Choices = CallRecorder.resolving(choice);
  const handlers: AgentHandlers = {
    onSignal: onSignal.fn,
    onFrame: onFrame.fn,
    onQuestions: onQuestions.fn,
    onPermission: onPermission.fn,
  };
  const agent = new AcpAgent(channel, handlers, new DevinDialect());
  return { channel, agent, onSignal, onFrame, onQuestions, onPermission };
}

// The frame shapes below follow what `devin acp` 3000.12.57 sent in recorded turns.
const QUESTION_FRAME = {
  id: "47bd2f1e",
  method: "elicitation/create",
  params: {
    mode: "form",
    sessionId: "wool-care",
    message: "Which file name?",
    requestedSchema: {
      type: "object",
      properties: {
        q0: {
          type: "string",
          title: "File name",
          description: "Which file name?",
          oneOf: [
            {
              const: "hello.txt",
              title: "hello.txt",
              _meta: { "cognition.ai/description": "Create hello.txt" },
            },
            { const: "hi.txt", title: "hi.txt" },
          ],
        },
      },
      required: ["q0"],
    },
  },
};

describe("AcpAgent handshake with the Devin dialect", () => {
  it("declares form elicitation and the client-provided models, without which a model variant silently runs its family's default", async () => {
    const { channel, agent } = connect();

    const initializing = agent.initialize();
    channel.deliver({ id: 1, result: { protocolVersion: 1 } });
    await initializing;

    assertPartial(channel.sent[0], {
      method: "initialize",
      params: {
        clientCapabilities: {
          elicitation: { form: {} },
          _meta: { "cognition.ai/clientProvidedModels": true },
        },
      },
    });
  });
});

describe("Devin questions", () => {
  it("hands the form to the handler and accepts with the answers keyed by field", async () => {
    const { channel, onQuestions } = connect();

    channel.deliver(QUESTION_FRAME);
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assert.deepEqual(onQuestions.calls, [
      [
        [
          {
            header: "File name",
            question: "Which file name?",
            options: [
              { label: "hello.txt", description: "Create hello.txt" },
              { label: "hi.txt", description: undefined },
            ],
          },
        ],
      ],
    ]);
    assert.deepEqual(channel.lastSent(), {
      jsonrpc: "2.0",
      id: "47bd2f1e",
      result: { action: "accept", content: { q0: "hi.txt" } },
    });
  });

  it("cancels the form when the handler returns no answers", async () => {
    const { channel } = connect(null);

    channel.deliver(QUESTION_FRAME);
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.lastSent(), { result: { action: "cancel" } });
  });
});

describe("Devin permissions", () => {
  const exitPlanUpdate = {
    method: "session/update",
    params: {
      sessionId: "wool-care",
      update: {
        sessionUpdate: "tool_call",
        toolCallId: "call_96",
        title: "Exit plan mode",
        kind: "switch_mode",
        rawInput: { plan: "create the file" },
      },
    },
  };
  const permissionFrame = (toolCallId: string) => ({
    id: "ecf596c1",
    method: "session/request_permission",
    params: {
      sessionId: "wool-care",
      toolCall: { toolCallId },
      options: [
        {
          optionId: "plan_accept_edits",
          name: "Yes, accept edits",
          kind: "allow_once",
        },
        {
          optionId: "plan_bypass",
          name: "Yes, bypass permissions",
          kind: "allow_once",
        },
        { optionId: "reject_once", name: "No", kind: "reject_once" },
      ],
    },
  });

  it("tells the handler a request belongs to the plan exit by the kind of its tool call, and sends back its choice", async () => {
    const { channel, onPermission } = connect();

    channel.deliver(exitPlanUpdate);
    channel.deliver(permissionFrame("call_96"));
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assert.equal(onPermission.calls.length, 1);
    assertPartial(onPermission.calls[0]?.[0], {
      isExitPlan: true,
      toolTitle: "Exit plan mode",
    });
    assertPartial(channel.lastSent(), {
      id: "ecf596c1",
      result: { outcome: { outcome: "selected", optionId: "plan_bypass" } },
    });
  });

  it("does not take a request for any other tool call, or an unknown one, for the plan exit", async () => {
    const { channel, onPermission } = connect(["hi.txt"], "plan_accept_edits");

    channel.deliver({
      method: "session/update",
      params: {
        update: {
          sessionUpdate: "tool_call",
          toolCallId: "call_7",
          title: "Ran hostname",
          kind: "execute",
        },
      },
    });
    channel.deliver(permissionFrame("call_7"));
    channel.deliver({ ...permissionFrame("call_never_seen"), id: "x2" });
    await waitUntil(() => assert.equal(channel.sent.length, 2));

    assertPartial(onPermission.calls[0]?.[0], {
      isExitPlan: false,
      toolTitle: "Ran hostname",
    });
    assertPartial(onPermission.calls[1]?.[0], {
      isExitPlan: false,
      toolTitle: "",
    });
  });

  it("reports the proposed plan once, from the tool call frame, not again from the request", async () => {
    const { channel, onSignal } = connect();

    channel.deliver(exitPlanUpdate);
    channel.deliver(permissionFrame("call_96"));
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    const plans = onSignal.calls.filter(
      ([signal]) => signal.type === "plan_proposed",
    );
    assert.deepEqual(plans, [
      [{ type: "plan_proposed", plan: "create the file" }],
    ]);
  });

  it("reports a cancelled outcome when the handler declines to choose", async () => {
    const { channel } = connect(["hi.txt"], null);

    channel.deliver(permissionFrame("call_96"));
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.lastSent(), {
      result: { outcome: { outcome: "cancelled" } },
    });
  });
});

describe("Devin recorded plan-exit turn", () => {
  it("meets the plan exit request with its options and the bypass among them", async () => {
    const { channel, onPermission } = connect(["hi.txt"], null);
    const frames = readFileSync(
      new URL(
        "../../../testdata/autopilot-frames/devin-3000.12.57-plan-exit.ndjson",
        import.meta.url,
      ),
      "utf8",
    )
      .trim()
      .split("\n")
      .map((line) => JSON.parse(line) as { dir: string; msg: object })
      .filter((frame) => frame.dir === "in")
      .map((frame) => frame.msg as Record<string, unknown>);

    for (const frame of frames) channel.deliver(frame);
    await waitUntil(() => assert.ok(onPermission.calls.length > 0));

    const ask = onPermission.calls[0]?.[0];
    assert.equal(ask?.isExitPlan, true);
    assert.deepEqual(
      ask?.options.map((option) => option.optionId),
      ["plan_accept_edits", "plan_bypass", "reject_once"],
    );
  });
});

describe("Devin signals", () => {
  it("forwards normalised signals and counts every frame as activity", () => {
    const { channel, onSignal, onFrame } = connect();

    channel.deliver({
      method: "session/update",
      params: {
        sessionId: "wool-care",
        update: {
          sessionUpdate: "current_mode_update",
          currentModeId: "bypass",
        },
      },
    });

    assert.deepEqual(onSignal.calls, [[{ type: "mode_changed", mode: "bypass" }]]);
    assert.equal(onFrame.calls.length, 1);
  });
});
