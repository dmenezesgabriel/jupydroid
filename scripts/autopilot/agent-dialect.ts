import type { AgentSignal } from "./agent-signal.ts";

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

/** The questions an agent asked, and how to put the answers back into the shape it reads. */
export type AskedQuestions = {
  questions: Question[];
  /** The reply's `content` for one answer per question (an option label or free text, in order). */
  content(answers: string[]): Record<string, unknown>;
};

/** A permission request read into plain types; `plan` is the proposed plan when the request is the plan exit. */
export type ReadPermission = { ask: PermissionAsk; plan: string | null };

/**
 * What differs between ACP agents on the wire, read into the plain types the
 * loop uses: the capabilities to declare, how a question and a permission
 * request are shaped, and which signals a notification carries. One dialect
 * serves one session, because an agent may report a fact in several frames
 * that the dialect has to join.
 *
 * @example
 * const dialect = profile.createDialect();
 * dialect.signalsFrom("session/update", { update: { sessionUpdate: "current_mode_update", currentModeId: "plan" } });
 * // => [{ type: "mode_changed", mode: "plan" }]
 */
export interface AgentDialect {
  /** The client capabilities `initialize` declares. */
  readonly clientCapabilities: Record<string, unknown>;
  askedQuestions(params: unknown): AskedQuestions;
  readPermission(params: unknown): ReadPermission;
  signalsFrom(method: string, params: unknown): AgentSignal[];
}
