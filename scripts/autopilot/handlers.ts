import type { ActivityLedger } from "./activity-ledger.ts";
import type { Clock } from "./clock.ts";
import type { AgentHandlers, PermissionAsk, Question } from "./claude-agent.ts";
import type { LoopEvent } from "./mailbox.ts";
import type { AgentSignal } from "./signals.ts";

const BYPASS_OPTION_ID = "exit-plan-bypass";
const PLAN_PREVIEW_CHARS = 400;

export type HandlerDeps = {
  ledger: ActivityLedger;
  clock: Clock;
  emit(event: LoopEvent): Promise<void>;
  askQuestions(questions: Question[]): Promise<string[]>;
  /** Starts watching a background command's output file for its exit. */
  watchBackground(shellId: string, outputPath: string): void;
};

/**
 * Wires the agent's callbacks to the loop: signals feed the ledger, every
 * frame is liveness, questions go to the orchestrator, and permission
 * requests follow the one policy this workflow has: leave plan mode into
 * bypass, and allow once what the permission mode still asks about.
 */
export function buildHandlers(deps: HandlerDeps): AgentHandlers {
  return {
    onSignal: (signal) => {
      const news = deps.ledger.apply(signal);
      if (news && signal.type === "background_started") {
        deps.watchBackground(signal.shellId, signal.outputPath);
      }
      void reportSignal(deps, signal, news);
    },
    onFrame: () => {
      deps.ledger.lastFrameAtMs = deps.clock.now();
    },
    onQuestions: (questions) => deps.askQuestions(questions),
    onPermission: (ask) =>
      ask.isExitPlan ? exitPlan(deps, ask) : allowOnce(deps, ask),
  };
}

/** Bypass or nothing: a weaker mode would change what the session may do, so the loop halts instead of picking one. */
async function exitPlan(
  deps: HandlerDeps,
  ask: PermissionAsk,
): Promise<string | null> {
  const offered = ask.options.map((option) => option.optionId);
  if (offered.includes(BYPASS_OPTION_ID)) return BYPASS_OPTION_ID;
  await deps.emit({ type: "exit_plan_bypass_missing", offered });
  return null;
}

/** Never an `allow_always` option: it writes a durable permission rule into the user's settings. */
async function allowOnce(
  deps: HandlerDeps,
  ask: PermissionAsk,
): Promise<string | null> {
  const chosen =
    ask.options.find((option) => option.kind === "allow_once")?.optionId ??
    null;
  await deps.emit({
    type: "permission_requested",
    tool: ask.toolTitle,
    chose: chosen,
  });
  return chosen;
}

async function reportSignal(
  deps: HandlerDeps,
  signal: AgentSignal,
  news: boolean,
): Promise<void> {
  const event = news ? stateEvent(signal) : otherEvent(signal);
  if (event) await deps.emit(event);
}

/** Mode and background changes, journaled only when the ledger saw something new: the adapter repeats a mode in several frames. */
function stateEvent(signal: AgentSignal): LoopEvent | null {
  if (signal.type === "mode_changed") {
    return { type: "mode_changed", mode: signal.mode };
  }
  if (signal.type === "background_started") {
    const { shellId, command } = signal;
    return { type: "background_started", shellId, command };
  }
  if (signal.type === "background_exited") {
    const { shellId, exitCode } = signal;
    return { type: "background_exited", shellId, exitCode };
  }
  return null;
}

/** Signals the orchestrator should read once, however often they repeat: the plan, a payload nobody has read yet, a rejected limit. */
function otherEvent(signal: AgentSignal): LoopEvent | null {
  if (signal.type === "plan_proposed") {
    return {
      type: "plan_ready",
      plan: signal.plan.slice(0, PLAN_PREVIEW_CHARS),
    };
  }
  if (signal.type === "vendor") {
    return {
      type: "agent_signal",
      method: signal.method,
      params: signal.params,
    };
  }
  if (signal.type === "rate_limit" && signal.status === "rejected") {
    const { resetsAtMs, limitType } = signal;
    const resetsAt =
      resetsAtMs === null ? null : new Date(resetsAtMs).toISOString();
    return { type: "rate_limit_rejected", limitType, resetsAt };
  }
  return null;
}
