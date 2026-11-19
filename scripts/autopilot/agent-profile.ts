import type { AgentDialect } from "./agent-dialect.ts";
import type { IterationTiming } from "./iteration.ts";
import type { AgentError, RateLimitVerdict } from "./rate-limit.ts";

/** The permission options an agent offers when it asks to leave plan mode. */
export type ExitPlanOptions = {
  /** Leaves plan mode into the mode that asks for nothing further. */
  bypassOptionId: string;
  /** Refuses the exit; null when the agent has no such option and the request is cancelled instead. */
  declineOptionId: string | null;
};

/**
 * Everything the loop needs to know about one agent that is not the same for
 * every agent: how to start it, what its modes and plan-exit options are
 * called, how its wire frames and limit errors read, and how long its silence
 * means something. The loop itself reads only this.
 */
export interface AgentProfile {
  readonly name: string;
  /** The executable started when `--agent-command` names none, resolved from PATH. */
  readonly defaultCommand: string;
  readonly launchArgs: readonly string[];
  /** The mode that runs without asking: where an approved plan is carried out. */
  readonly bypassMode: string;
  readonly exitPlan: ExitPlanOptions;
  readonly timing: IterationTiming;
  /** One dialect per session: it may join facts that arrive in several frames. */
  createDialect(): AgentDialect;
  /** A prompt error that is a usage limit, or null when it is some other failure. */
  classifyError(error: AgentError, now: Date): RateLimitVerdict | null;
}
