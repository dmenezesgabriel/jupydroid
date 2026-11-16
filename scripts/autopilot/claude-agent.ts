import { BackgroundTaskTracker } from "./background-task-tracker.ts";
import type { StopReason } from "./decide.ts";
import { JsonRpcConnection, RpcError, type LineChannel } from "./json-rpc.ts";
import { signalsFrom, type AgentSignal } from "./signals.ts";

export type QuestionOption = { label: string; description?: string };
export type Question = {
  header: string;
  question: string;
  options: QuestionOption[];
};
export type PermissionOption = { optionId: string; name: string; kind: string };
export type PermissionAsk = {
  toolTitle: string;
  toolName: string;
  isExitPlan: boolean;
  options: PermissionOption[];
};

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

type FieldOption = { const: string; description?: string };
type FormField = {
  type?: string;
  title?: string;
  description?: string;
  oneOf?: FieldOption[];
  items?: { anyOf?: FieldOption[] };
};
type ElicitationParams = {
  message?: string;
  requestedSchema?: { properties?: Record<string, FormField> };
};

type PermissionParams = {
  toolCall?: {
    name?: string;
    title?: string;
    rawInput?: { plan?: unknown };
  };
  options?: PermissionOption[];
};

const METHOD_NOT_FOUND = -32601;
const EXIT_PLAN_TOOL = "ExitPlanMode";
const CUSTOM_ANSWER_SUFFIX = "_custom";

/**
 * The slice of the Agent Client Protocol that `claude-agent-acp` needs from
 * us, with its wire shapes read into plain types. Client capabilities
 * advertise form elicitation: without it the adapter never routes
 * `AskUserQuestion` to the client.
 *
 * @example
 * const agent = new ClaudeAgent(channel, handlers);
 * await agent.initialize();
 * const sessionId = await agent.newSession("/repo");
 */
export class ClaudeAgent {
  private readonly rpc: JsonRpcConnection;
  private readonly background = new BackgroundTaskTracker();
  private sessionId = "";

  constructor(
    channel: LineChannel,
    private readonly handlers: AgentHandlers,
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
      clientCapabilities: { elicitation: { form: {} } },
      clientInfo: { name: "autopilot", version: "1.0.0" },
    });
  }

  async newSession(cwd: string): Promise<string> {
    const reply = (await this.rpc.request("session/new", {
      cwd,
      mcpServers: [],
    })) as { sessionId: string };
    this.sessionId = reply.sessionId;
    return this.sessionId;
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
    if (method === "elicitation/create")
      return this.answerQuestions(params as ElicitationParams);
    if (method === "session/request_permission") {
      return this.answerPermission(params as PermissionParams);
    }
    throw new RpcError(
      METHOD_NOT_FOUND,
      `autopilot does not serve "${method}"`,
    );
  }

  private async answerQuestions(params: ElicitationParams): Promise<unknown> {
    const fields = selectFields(params);
    const questions = fields.map(([, field]) => toQuestion(field, params));
    const answers = await this.handlers.onQuestions(questions);
    if (!answers) return { action: "cancel" };
    return { action: "accept", content: formContent(fields, answers) };
  }

  private async answerPermission(params: PermissionParams): Promise<unknown> {
    const call = params.toolCall ?? {};
    const isExitPlan = call.name === EXIT_PLAN_TOOL;
    if (isExitPlan && typeof call.rawInput?.plan === "string") {
      this.handlers.onSignal({
        type: "plan_proposed",
        plan: call.rawInput.plan,
      });
    }
    const optionId = await this.handlers.onPermission({
      toolTitle: call.title ?? "",
      toolName: call.name ?? "",
      isExitPlan,
      options: params.options ?? [],
    });
    return {
      outcome: optionId
        ? { outcome: "selected", optionId }
        : { outcome: "cancelled" },
    };
  }

  private notified(method: string, params: unknown): void {
    for (const signal of signalsFrom(method, params)) {
      for (const folded of this.background.fold(signal)) {
        this.handlers.onSignal(folded);
      }
    }
  }
}

type Field = [key: string, field: FormField];

/** The select fields in order; each question's optional `_custom` text box is not a question. */
function selectFields(params: ElicitationParams): Field[] {
  const properties = Object.entries(params.requestedSchema?.properties ?? {});
  return properties.filter(([key]) => !key.endsWith(CUSTOM_ANSWER_SUFFIX));
}

function optionsOf(field: FormField): FieldOption[] {
  return field.oneOf ?? field.items?.anyOf ?? [];
}

function toQuestion(field: FormField, params: ElicitationParams): Question {
  return {
    header: field.title ?? "",
    question: field.description ?? params.message ?? "",
    options: optionsOf(field).map((option) => ({
      label: option.const,
      description: option.description,
    })),
  };
}

/**
 * An answer that is one of the offered labels selects it; anything else goes
 * into the question's own custom-answer field, which Claude reads as the
 * user's "Other" text.
 */
function formContent(
  fields: Field[],
  answers: string[],
): Record<string, string | string[]> {
  const content: Record<string, string | string[]> = {};
  fields.forEach(([key, field], index) => {
    const answer = answers[index] ?? "";
    const offered = optionsOf(field).some((option) => option.const === answer);
    if (offered) content[key] = field.type === "array" ? [answer] : answer;
    else if (answer !== "") content[`${key}${CUSTOM_ANSWER_SUFFIX}`] = answer;
  });
  return content;
}
