import type {
  AgentDialect,
  AskedQuestions,
  PermissionOption,
  ReadPermission,
} from "../../agent-dialect.ts";
import type { AgentSignal } from "../../agent-signal.ts";
import { signalsFrom } from "./signals.ts";

type FieldOption = {
  const: string;
  title?: string;
  _meta?: Record<string, string>;
};
type FormField = {
  title?: string;
  description?: string;
  oneOf?: FieldOption[];
};
type ElicitationParams = {
  message?: string;
  requestedSchema?: { properties?: Record<string, FormField> };
};
type PermissionParams = {
  toolCall?: { toolCallId?: string };
  options?: PermissionOption[];
};
type ToolCall = { title: string; kind: string };

const SWITCH_MODE_KIND = "switch_mode";

/**
 * `devin acp`'s wire shapes. Form elicitation is declared so that
 * `ask_user_question` reaches the client as a question, and so is
 * `clientProvidedModels`: without it Devin resolves a model among one per
 * family and silently runs the family's default for any other variant. A
 * permission request names its tool call only by id, so the dialect keeps what
 * each `tool_call` frame said and a dialect serves one session.
 */
export class DevinDialect implements AgentDialect {
  readonly clientCapabilities = {
    elicitation: { form: {} },
    _meta: { "cognition.ai/clientProvidedModels": true },
  };
  private readonly toolCalls = new Map<string, ToolCall>();

  askedQuestions(params: unknown): AskedQuestions {
    const form = params as ElicitationParams;
    const properties = Object.entries(form.requestedSchema?.properties ?? {});
    return {
      questions: properties.map(([, field]) => ({
        header: field.title ?? "",
        question: field.description ?? form.message ?? "",
        options: (field.oneOf ?? []).map((option) => ({
          label: option.const,
          description: option._meta?.["cognition.ai/description"],
        })),
      })),
      content: (answers) =>
        Object.fromEntries(
          properties.map(([key], index) => [key, answers[index] ?? ""]),
        ),
    };
  }

  /** The plan is not read here: it arrived on the plan exit's own `tool_call` frame, as a signal. */
  readPermission(params: unknown): ReadPermission {
    const { toolCall, options } = params as PermissionParams;
    const call = this.toolCalls.get(toolCall?.toolCallId ?? "");
    return {
      ask: {
        toolTitle: call?.title ?? "",
        toolName: call?.title ?? "",
        isExitPlan: call?.kind === SWITCH_MODE_KIND,
        options: options ?? [],
      },
      plan: null,
    };
  }

  signalsFrom(method: string, params: unknown): AgentSignal[] {
    this.rememberToolCall(method, params);
    return signalsFrom(method, params);
  }

  private rememberToolCall(method: string, params: unknown): void {
    if (method !== "session/update") return;
    const update = (
      params as {
        update?: {
          sessionUpdate?: string;
          toolCallId?: string;
          title?: string;
          kind?: string;
        };
      }
    ).update;
    if (update?.sessionUpdate !== "tool_call" || !update.toolCallId) return;
    this.toolCalls.set(update.toolCallId, {
      title: update.title ?? "",
      kind: update.kind ?? "",
    });
  }
}
