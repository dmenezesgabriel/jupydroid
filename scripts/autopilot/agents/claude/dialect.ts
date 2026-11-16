import type {
  AgentDialect,
  AskedQuestions,
  PermissionOption,
  Question,
  ReadPermission,
} from "../../agent-dialect.ts";
import type { AgentSignal } from "../../agent-signal.ts";
import { BackgroundTaskTracker } from "./background-task-tracker.ts";
import { signalsFrom } from "./signals.ts";

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

type Field = [key: string, field: FormField];

const EXIT_PLAN_TOOL = "ExitPlanMode";
const CUSTOM_ANSWER_SUFFIX = "_custom";

/**
 * `claude-agent-acp`'s wire shapes. Client capabilities advertise form
 * elicitation: without it the adapter never routes `AskUserQuestion` to the
 * client. A background command's facts arrive in three frames, which the
 * tracker joins, so a dialect serves one session.
 */
export class ClaudeDialect implements AgentDialect {
  readonly clientCapabilities = { elicitation: { form: {} } };
  private readonly background = new BackgroundTaskTracker();

  askedQuestions(params: unknown): AskedQuestions {
    const form = params as ElicitationParams;
    const fields = selectFields(form);
    return {
      questions: fields.map(([, field]) => toQuestion(field, form)),
      content: (answers) => formContent(fields, answers),
    };
  }

  readPermission(params: unknown): ReadPermission {
    const { toolCall, options } = params as PermissionParams;
    const call = toolCall ?? {};
    const plan = call.rawInput?.plan;
    return {
      ask: {
        toolTitle: call.title ?? "",
        toolName: call.name ?? "",
        isExitPlan: call.name === EXIT_PLAN_TOOL,
        options: options ?? [],
      },
      plan: typeof plan === "string" ? plan : null,
    };
  }

  signalsFrom(method: string, params: unknown): AgentSignal[] {
    return signalsFrom(method, params).flatMap((signal) =>
      this.background.fold(signal),
    );
  }
}

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
