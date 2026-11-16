import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  AcpAgent,
  ModelSelectionError,
  type AgentHandlers,
} from "./acp-agent.ts";
import type {
  AgentDialect,
  AskedQuestions,
  Question,
  ReadPermission,
} from "./agent-dialect.ts";
import type { AgentSignal } from "./agent-signal.ts";
import { assertPartial, waitUntil } from "./testing/assertions.ts";
import { CallRecorder } from "./testing/call-recorder.ts";
import { FakeLineChannel } from "./testing/fake-line-channel.ts";

/** A dialect unlike any real agent's, so these tests hold the agent to its seam and not to one wire shape. */
class FakeDialect implements AgentDialect {
  readonly clientCapabilities = { fake: { declared: true } };
  readonly signals: AgentSignal[] = [{ type: "agent_message", text: "hi" }];
  plan: string | null = null;

  askedQuestions(): AskedQuestions {
    return {
      questions: [{ header: "H", question: "Q?", options: [] }],
      content: (answers) => ({ answeredWith: answers.join("|") }),
    };
  }

  readPermission(): ReadPermission {
    return {
      ask: {
        toolTitle: "Leave",
        toolName: "Leave",
        isExitPlan: true,
        options: [{ optionId: "go", name: "Go", kind: "allow_once" }],
      },
      plan: this.plan,
    };
  }

  signalsFrom(): AgentSignal[] {
    return this.signals;
  }
}

function connect(
  answers: string[] | null = ["yes"],
  choice: string | null = "go",
) {
  const channel = new FakeLineChannel();
  const dialect = new FakeDialect();
  const onSignal = CallRecorder.silent<[AgentSignal]>();
  const onQuestions = CallRecorder.resolving<[Question[]], string[] | null>(
    answers,
  );
  const onPermission = CallRecorder.resolving<[unknown], string | null>(choice);
  const handlers: AgentHandlers = {
    onSignal: onSignal.fn,
    onFrame: () => undefined,
    onQuestions: onQuestions.fn,
    onPermission: onPermission.fn,
  };
  return {
    channel,
    dialect,
    onSignal,
    onQuestions,
    agent: new AcpAgent(channel, handlers, dialect),
  };
}

describe("AcpAgent", () => {
  it("declares the capabilities its dialect names", async () => {
    const { channel, agent } = connect();

    const initializing = agent.initialize();
    channel.deliver({ id: 1, result: { protocolVersion: 1 } });
    await initializing;

    assertPartial(channel.sent[0], {
      method: "initialize",
      params: { clientCapabilities: { fake: { declared: true } } },
    });
  });

  it("puts answers back into the shape its dialect reads", async () => {
    const { channel, onQuestions } = connect();

    channel.deliver({ id: 5, method: "elicitation/create", params: {} });
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assert.deepEqual(onQuestions.calls, [
      [[{ header: "H", question: "Q?", options: [] }]],
    ]);
    assertPartial(channel.sent[0], {
      id: 5,
      result: { action: "accept", content: { answeredWith: "yes" } },
    });
  });

  it("cancels a question the handlers cancel", async () => {
    const { channel } = connect(null);

    channel.deliver({ id: 5, method: "elicitation/create", params: {} });
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.sent[0], { result: { action: "cancel" } });
  });

  it("reports the plan a plan-exit request carries before it asks for a choice", async () => {
    const { channel, dialect, onSignal } = connect();
    dialect.plan = "1. do it";

    channel.deliver({
      id: 6,
      method: "session/request_permission",
      params: {},
    });
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assert.deepEqual(onSignal.calls, [
      [{ type: "plan_proposed", plan: "1. do it" }],
    ]);
    assertPartial(channel.sent[0], {
      result: { outcome: { outcome: "selected", optionId: "go" } },
    });
  });

  it("answers a cancelled outcome when the handlers choose nothing", async () => {
    const { channel } = connect(["yes"], null);

    channel.deliver({
      id: 6,
      method: "session/request_permission",
      params: {},
    });
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.sent[0], {
      result: { outcome: { outcome: "cancelled" } },
    });
  });

  it("hands every signal its dialect reads from a notification to the handlers", () => {
    const { channel, onSignal } = connect();

    channel.deliver({ method: "session/update", params: {} });

    assert.deepEqual(onSignal.calls, [[{ type: "agent_message", text: "hi" }]]);
  });

  it("refuses a request no agent of this loop serves, naming the method", async () => {
    const { channel } = connect();

    channel.deliver({ id: 9, method: "fs/read_text_file", params: {} });
    await waitUntil(() => assert.equal(channel.sent.length, 1));

    assertPartial(channel.sent[0], {
      id: 9,
      error: {
        code: -32601,
        message: 'autopilot does not serve "fs/read_text_file"',
      },
    });
  });
});

const MODEL_OPTION = (currentValue: string, values: string[]) => ({
  id: "model",
  category: "model",
  currentValue,
  options: values.map((value) => ({ value })),
});

/** An agent whose session/new has been answered with the given config options. */
async function openSession(configOptions: unknown[]) {
  const connected = connect();
  const opening = connected.agent.newSession("/repo");
  connected.channel.deliver({
    id: 1,
    result: { sessionId: "s1", configOptions },
  });
  await opening;
  return connected;
}

describe("AcpAgent model selection", () => {
  it("reports the model the new session runs, or none when the agent offers no model option", async () => {
    const withModel = await openSession([
      MODEL_OPTION("sonnet", ["sonnet", "haiku"]),
    ]);
    const without = await openSession([]);

    assert.equal(withModel.agent.currentModel(), "sonnet");
    assert.equal(without.agent.currentModel(), null);
  });

  it("sets an offered model through the model option and returns what the agent reads back", async () => {
    const { channel, agent } = await openSession([
      MODEL_OPTION("sonnet", ["sonnet", "haiku"]),
    ]);

    const selecting = agent.selectModel("haiku");
    channel.deliver({
      id: 2,
      result: { configOptions: [MODEL_OPTION("haiku", ["sonnet", "haiku"])] },
    });

    assert.equal(await selecting, "haiku");
    assertPartial(channel.sent[1], {
      method: "session/set_config_option",
      params: { sessionId: "s1", configId: "model", value: "haiku" },
    });
    assert.equal(agent.currentModel(), "haiku");
  });

  it("refuses a model the agent does not offer, naming the value and what is offered, without asking the agent", async () => {
    const { channel, agent } = await openSession([
      MODEL_OPTION("sonnet", ["sonnet", "haiku"]),
    ]);

    const selecting = agent.selectModel("gpt-9");

    await assert.rejects(
      selecting,
      (error: Error) =>
        error instanceof ModelSelectionError &&
        error.message ===
          '--model "gpt-9" is not a model the agent offers (2 offered: sonnet, haiku)',
    );
    assert.equal(channel.sent.length, 1);
  });

  it("lists only the first offered models when an agent offers a great many", async () => {
    const many = Array.from({ length: 1073 }, (_, index) => `m${index}`);
    const { agent } = await openSession([MODEL_OPTION("m0", many)]);

    await assert.rejects(
      agent.selectModel("nope"),
      /1073 offered: m0, m1, .*, m19, \.\.\.\)$/,
    );
  });

  it("refuses a model when the agent reports no model option", async () => {
    const { agent } = await openSession([]);

    await assert.rejects(
      agent.selectModel("haiku"),
      (error: Error) =>
        error.message ===
        '--model "haiku" cannot be applied: the agent reports no model option',
    );
  });

  it("refuses when the agent reads back a model other than the one set, as one that silently runs another", async () => {
    const { channel, agent } = await openSession([
      MODEL_OPTION("sonnet", ["sonnet", "haiku"]),
    ]);

    const selecting = agent.selectModel("haiku");
    channel.deliver({
      id: 2,
      result: { configOptions: [MODEL_OPTION("sonnet", ["sonnet", "haiku"])] },
    });

    await assert.rejects(
      selecting,
      (error: Error) =>
        error.message ===
        'the agent runs "sonnet" after --model "haiku" was set: it did not take the choice',
    );
  });
});

const EFFORT_OPTION = (id: string, currentValue: string, values: string[]) => ({
  id,
  category: "thought_level",
  currentValue,
  options: values.map((value) => ({ value })),
});

describe("AcpAgent effort selection", () => {
  it("reports the effort the new session runs, whatever id the agent gives its thought-level option", async () => {
    const claude = await openSession([
      MODEL_OPTION("sonnet", ["sonnet"]),
      EFFORT_OPTION("effort", "high", ["low", "high"]),
    ]);
    const devin = await openSession([
      MODEL_OPTION("m", ["m"]),
      EFFORT_OPTION("thought_level", "medium", ["medium", "high", "max"]),
    ]);
    const without = await openSession([MODEL_OPTION("m", ["m"])]);

    assert.equal(claude.agent.currentEffort(), "high");
    assert.equal(devin.agent.currentEffort(), "medium");
    assert.equal(without.agent.currentEffort(), null);
  });

  it("sets an offered effort through the option's own id and returns what the agent reads back", async () => {
    const { channel, agent } = await openSession([
      MODEL_OPTION("m", ["m"]),
      EFFORT_OPTION("thought_level", "medium", ["medium", "high", "max"]),
    ]);

    const selecting = agent.selectEffort("max");
    channel.deliver({
      id: 2,
      result: {
        configOptions: [
          EFFORT_OPTION("thought_level", "max", ["medium", "high", "max"]),
        ],
      },
    });

    assert.equal(await selecting, "max");
    assertPartial(channel.sent[1], {
      method: "session/set_config_option",
      params: { sessionId: "s1", configId: "thought_level", value: "max" },
    });
    assert.equal(agent.currentEffort(), "max");
  });

  it("offers what the model's own reply lists, because an agent's efforts depend on the model", async () => {
    const { channel, agent } = await openSession([
      MODEL_OPTION("opus", ["opus", "haiku"]),
      EFFORT_OPTION("effort", "high", ["low", "high", "xhigh"]),
    ]);

    const selectingModel = agent.selectModel("haiku");
    channel.deliver({
      id: 2,
      result: {
        configOptions: [
          MODEL_OPTION("haiku", ["opus", "haiku"]),
          EFFORT_OPTION("effort", "low", ["low", "high"]),
        ],
      },
    });
    await selectingModel;

    await assert.rejects(
      agent.selectEffort("xhigh"),
      /--effort "xhigh" is not an effort the agent offers \(2 offered: low, high\)/,
    );
    assert.equal(channel.sent.length, 2);
  });

  it("refuses an effort the agent does not offer, naming the value and what is offered, without asking the agent", async () => {
    const { channel, agent } = await openSession([
      MODEL_OPTION("m", ["m"]),
      EFFORT_OPTION("effort", "high", ["low", "high"]),
    ]);

    await assert.rejects(
      agent.selectEffort("ultra"),
      (error: Error) =>
        error instanceof ModelSelectionError &&
        error.message ===
          '--effort "ultra" is not an effort the agent offers (2 offered: low, high)',
    );
    assert.equal(channel.sent.length, 1);
  });

  it("refuses an effort when the agent reports no thought-level option", async () => {
    const { agent } = await openSession([MODEL_OPTION("m", ["m"])]);

    await assert.rejects(
      agent.selectEffort("high"),
      (error: Error) =>
        error.message ===
        '--effort "high" cannot be applied: the agent reports no thought-level option',
    );
  });

  it("refuses when the agent reads back another effort than the one set", async () => {
    const { channel, agent } = await openSession([
      MODEL_OPTION("m", ["m"]),
      EFFORT_OPTION("effort", "high", ["low", "high"]),
    ]);

    const selecting = agent.selectEffort("low");
    channel.deliver({
      id: 2,
      result: {
        configOptions: [EFFORT_OPTION("effort", "high", ["low", "high"])],
      },
    });

    await assert.rejects(
      selecting,
      (error: Error) =>
        error.message ===
        'the agent runs effort "high" after --effort "low" was set: it did not take the choice',
    );
  });
});
