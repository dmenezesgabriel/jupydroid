import type { AgentDialect, PermissionAsk, Question } from "./agent-dialect.ts";
import type { AgentSignal } from "./agent-signal.ts";
import type { StopReason } from "./decide.ts";
import { JsonRpcConnection, RpcError, type LineChannel } from "./json-rpc.ts";

export interface AgentHandlers {
  onSignal(signal: AgentSignal): void;
  onFrame(): void;
  /** One answer per question (an option label or free text); null cancels the question. */
  onQuestions(questions: Question[]): Promise<string[] | null>;
  /** The chosen optionId; null cancels the request. */
  onPermission(ask: PermissionAsk): Promise<string | null>;
}

export type PromptBlock =
  | { type: "text"; text: string }
  | EmbeddedResourceBlock;
type EmbeddedResourceBlock = {
  type: "resource";
  resource: { uri: string; mimeType: string; text: string };
};

const METHOD_NOT_FOUND = -32601;
/** How many of an agent's offered values an error message lists; some agents offer over a thousand models. */
const OFFERED_SHOWN = 20;

type ConfigOption = {
  id: string;
  category?: string;
  currentValue?: string;
  options?: Array<{ value: string }>;
};
type ChosenOption = { id: string; running: string; offered: string[] };

/** One of the settings a run can choose, found among an agent's config options by its category, never by its id. */
type Choice = {
  key: "model" | "effort";
  category: string;
  flag: string;
  noun: string;
  absent: string;
  runs: string;
};
const MODEL: Choice = {
  key: "model",
  category: "model",
  flag: "--model",
  noun: "a model",
  absent: "no model option",
  runs: "the agent runs",
};
/** Claude calls it `effort` and Devin `thought_level`; both file it under the thought_level category. */
const EFFORT: Choice = {
  key: "effort",
  category: "thought_level",
  flag: "--effort",
  noun: "an effort",
  absent: "no thought-level option",
  runs: "the agent runs effort",
};

/** The agent did not take the model or effort the run asked for; the message names the value and what was offered. */
export class ModelSelectionError extends Error {}

/** The option of [choice] among an agent's config options, or null when it reports none. */
function optionOf(
  options: ConfigOption[] | undefined,
  choice: Choice,
): ChosenOption | null {
  const option = options?.find(({ category }) => category === choice.category);
  if (option?.currentValue === undefined) return null;
  const offered = (option.options ?? []).map(({ value }) => value);
  return { id: option.id, running: option.currentValue, offered };
}

/**
 * The slice of the Agent Client Protocol the loop needs from an agent, with
 * the agent's own wire shapes read by its {@link AgentDialect}. The client
 * capabilities the dialect declares decide which requests the agent routes
 * to us: without form elicitation a question never arrives.
 *
 * @example
 * const agent = new AcpAgent(channel, handlers, profile.createDialect());
 * await agent.initialize();
 * const sessionId = await agent.newSession("/repo");
 */
export class AcpAgent {
  private readonly rpc: JsonRpcConnection;
  private sessionId = "";
  private readonly chosen: Record<Choice["key"], ChosenOption | null> = {
    model: null,
    effort: null,
  };

  constructor(
    channel: LineChannel,
    private readonly handlers: AgentHandlers,
    private readonly dialect: AgentDialect,
  ) {
    this.rpc = new JsonRpcConnection(channel, {
      onRequest: (method, params) => this.answer(method, params),
      onNotification: (method, params) => this.notified(method, params),
      onFrame: () => handlers.onFrame(),
    });
  }

  async initialize(): Promise<void> {
    await this.rpc.request("initialize", {
      protocolVersion: 1,
      clientCapabilities: this.dialect.clientCapabilities,
      clientInfo: { name: "autopilot", version: "1.0.0" },
    });
  }

  async newSession(cwd: string): Promise<string> {
    const reply = (await this.rpc.request("session/new", {
      cwd,
      mcpServers: [],
    })) as { sessionId: string; configOptions?: ConfigOption[] };
    this.sessionId = reply.sessionId;
    this.chosen.model = optionOf(reply.configOptions, MODEL);
    this.chosen.effort = optionOf(reply.configOptions, EFFORT);
    return this.sessionId;
  }

  /** The model the new session runs, as the agent reports it; null when it reports no model option. */
  currentModel(): string | null {
    return this.chosen.model?.running ?? null;
  }

  /** The effort the session runs, as the agent reports it; null when it reports no thought-level option. */
  currentEffort(): string | null {
    return this.chosen.effort?.running ?? null;
  }

  /**
   * Runs the session on `model`, one the agent offers: an agent answers a model
   * it does not know with a bare "Internal error" or silently runs another, so
   * the choice is checked against the offered list first and against the
   * agent's own read-back after. Only the reply to this request is trusted for
   * the model that runs: replies to a mode change were measured naming a model
   * other than the one that answered. The same reply carries the efforts the
   * new model offers, which differ from the old one's.
   *
   * @example
   * await agent.selectModel("sonnet"); // => "sonnet"
   */
  async selectModel(model: string): Promise<string> {
    const reply = await this.setOption(MODEL, model);
    this.chosen.effort = optionOf(reply, EFFORT);
    return this.currentModel() ?? model;
  }

  /**
   * Runs the session at `effort`, one the agent offers for the model it runs now: call it after
   * {@link selectModel}, because the offered efforts depend on the model.
   *
   * @example
   * await agent.selectEffort("high"); // => "high"
   */
  async selectEffort(effort: string): Promise<string> {
    await this.setOption(EFFORT, effort);
    return this.currentEffort() ?? effort;
  }

  /** Checks [value] against what the agent offers, sets it, and trusts only the read-back in the reply. */
  private async setOption(
    choice: Choice,
    value: string,
  ): Promise<ConfigOption[] | undefined> {
    const option = this.chosen[choice.key];
    const wanted = JSON.stringify(value);
    if (option === null) {
      throw new ModelSelectionError(
        `${choice.flag} ${wanted} cannot be applied: the agent reports ${choice.absent}`,
      );
    }
    if (!option.offered.includes(value)) {
      throw new ModelSelectionError(
        `${choice.flag} ${wanted} is not ${choice.noun} the agent offers (${describeOffered(option.offered)})`,
      );
    }
    const reply = (await this.rpc.request("session/set_config_option", {
      sessionId: this.sessionId,
      configId: option.id,
      value,
    })) as { configOptions?: ConfigOption[] };
    const read = optionOf(reply.configOptions, choice);
    if (read?.running !== value) {
      throw new ModelSelectionError(
        `${choice.runs} ${JSON.stringify(read?.running ?? null)} after ${choice.flag} ${wanted} was set: it did not take the choice`,
      );
    }
    this.chosen[choice.key] = read;
    return reply.configOptions;
  }

  async setMode(mode: string): Promise<void> {
    await this.rpc.request("session/set_config_option", {
      sessionId: this.sessionId,
      configId: "mode",
      value: mode,
    });
  }

  /** Resolves with the stop reason; rejects with an RpcError when the agent answers with an error. */
  async prompt(blocks: PromptBlock[]): Promise<StopReason> {
    const reply = (await this.rpc.request("session/prompt", {
      sessionId: this.sessionId,
      prompt: blocks,
    })) as { stopReason: StopReason };
    return reply.stopReason;
  }

  cancel(): void {
    this.rpc.notify("session/cancel", { sessionId: this.sessionId });
  }

  private async answer(method: string, params: unknown): Promise<unknown> {
    if (method === "elicitation/create") return this.answerQuestions(params);
    if (method === "session/request_permission") {
      return this.answerPermission(params);
    }
    throw new RpcError(
      METHOD_NOT_FOUND,
      `autopilot does not serve "${method}"`,
    );
  }

  private async answerQuestions(params: unknown): Promise<unknown> {
    const asked = this.dialect.askedQuestions(params);
    const answers = await this.handlers.onQuestions(asked.questions);
    if (!answers) return { action: "cancel" };
    return { action: "accept", content: asked.content(answers) };
  }

  private async answerPermission(params: unknown): Promise<unknown> {
    const { ask, plan } = this.dialect.readPermission(params);
    if (ask.isExitPlan && plan !== null) {
      this.handlers.onSignal({ type: "plan_proposed", plan });
    }
    const optionId = await this.handlers.onPermission(ask);
    return {
      outcome: optionId
        ? { outcome: "selected", optionId }
        : { outcome: "cancelled" },
    };
  }

  private notified(method: string, params: unknown): void {
    for (const signal of this.dialect.signalsFrom(method, params)) {
      this.handlers.onSignal(signal);
    }
  }
}

function describeOffered(offered: string[]): string {
  const shown = offered.slice(0, OFFERED_SHOWN).join(", ");
  const more = offered.length > OFFERED_SHOWN ? ", ..." : "";
  return `${offered.length} offered: ${shown}${more}`;
}
