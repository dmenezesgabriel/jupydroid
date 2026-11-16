import type { AgentError, RateLimitVerdict } from "./rate-limit.ts";

export type StopReason =
  | "end_turn"
  | "max_tokens"
  | "max_turn_requests"
  | "refusal"
  | "cancelled";

/** How a prompt ended, from the agent's own response. */
export type TurnEnd =
  | { kind: "stopped"; reason: StopReason }
  | { kind: "failed"; error: AgentError }
  | { kind: "crashed" };

/** What the loop has observed, not what it hopes: every field comes from a signal or from git. */
export type World = {
  mode: string;
  working: boolean;
  /** A background command exited and the agent has not been told yet. */
  unreportedExit: boolean;
  commitsMade: boolean;
  clean: boolean;
  replyRounds: number;
  errorRetries: number;
  /** The usage limit the turn that just ended hit, from a rejected reading or from the error it failed with. */
  limit: RateLimitVerdict | null;
  /** The loop itself declined the agent's plan exit during this turn, to approve the plan with a follow-up command. */
  planDeclined: boolean;
};

export type ReplyReason = "plan_not_exited" | "no_commit" | "dirty_tree";

export type Decision =
  | { do: "finish" }
  | { do: "approve_plan" }
  | { do: "await_work" }
  | { do: "ask_reply"; why: ReplyReason }
  | { do: "continue" }
  | { do: "wait_until"; wakeAt: Date }
  | { do: "poll_limit" }
  | { do: "retry"; backoffMs: number }
  | { do: "stall" }
  | {
      do: "halt";
      reason:
        | "weekly_limit"
        | "monthly_limit"
        | "refusal"
        | "cancelled"
        | "error";
    }
  | { do: "crash" };

/** The mode an agent starts a session in so that it plans before it acts; every ACP agent this loop drives names it so. */
export const PLAN_MODE = "plan";

export const MAX_REPLY_ROUNDS = 2;
export const MAX_ERROR_RETRIES = 3;
const BASE_BACKOFF_MS = 30_000;

/**
 * The one place that turns "the prompt ended" into an action. A stop is never
 * read as done while a tool call or background shell is running, nor while the
 * agent is still in plan mode; silence is not an input here at all.
 *
 * @example
 * decide({ kind: "stopped", reason: "end_turn" }, { ...world, working: true });
 * // => { do: "await_work" }
 */
export function decide(end: TurnEnd, world: World): Decision {
  if (end.kind === "crashed") return { do: "crash" };
  if (end.kind === "failed") return decideAfterError(world);
  switch (end.reason) {
    case "end_turn":
      return decideAfterEndTurn(world);
    case "max_tokens":
    case "max_turn_requests":
      return { do: "continue" };
    case "refusal":
      return { do: "halt", reason: "refusal" };
    case "cancelled":
      // Declining the plan exit ends the turn as `cancelled`, before any work
      // (measured on claude-agent-acp 0.91.0). Only the loop's own decline is
      // an approval; any other cancellation still halts.
      return world.planDeclined
        ? { do: "approve_plan" }
        : { do: "halt", reason: "cancelled" };
  }
}

function decideAfterEndTurn(world: World): Decision {
  // A rejected limit ends the turn like any other stop, with the limit text as
  // the agent's last words: reading that as "no commit" would nudge a session
  // that cannot answer.
  if (world.limit) return decideForLimit(world.limit);
  if (world.working) return { do: "await_work" };
  // Checked before the stall count: an exit the agent has not heard about is
  // a race the loop lost, not a sign that the agent stopped without working.
  if (world.unreportedExit && isUnfinished(world)) return { do: "await_work" };
  // An agent that ends the turn when its plan exit is declined, instead of
  // cancelling it, is told apart from one that stopped without working by the
  // decline the loop made itself.
  if (world.planDeclined) return { do: "approve_plan" };
  if (world.replyRounds >= MAX_REPLY_ROUNDS)
    return decideWhenRepliesSpent(world);
  if (world.mode === PLAN_MODE)
    return { do: "ask_reply", why: "plan_not_exited" };
  if (!world.commitsMade) return { do: "ask_reply", why: "no_commit" };
  if (!world.clean) return { do: "ask_reply", why: "dirty_tree" };
  return { do: "finish" };
}

/**
 * The agent has stopped again and the replies it may be sent are spent. A
 * commit is progress, and the circuit breaker ends a run that stops producing
 * them: two stalls in a row halt the loop. So a session that committed and left
 * files in the tree finishes, and the next session finds that tree, as any
 * session does. One still in plan mode, or with no commit, has made none.
 */
function decideWhenRepliesSpent(world: World): Decision {
  const committed = world.mode !== PLAN_MODE && world.commitsMade;
  return committed ? { do: "finish" } : { do: "stall" };
}

/** What `finish` requires and the agent has not done yet. */
function isUnfinished(world: World): boolean {
  return world.mode === PLAN_MODE || !world.commitsMade || !world.clean;
}

function decideForLimit(limit: RateLimitVerdict): Decision {
  if (limit.kind === "weekly") return { do: "halt", reason: "weekly_limit" };
  if (limit.kind === "monthly") return { do: "halt", reason: "monthly_limit" };
  if (limit.kind === "until") return { do: "wait_until", wakeAt: limit.wakeAt };
  return { do: "poll_limit" };
}

function decideAfterError(world: World): Decision {
  if (world.limit) return decideForLimit(world.limit);
  if (world.errorRetries >= MAX_ERROR_RETRIES)
    return { do: "halt", reason: "error" };
  return { do: "retry", backoffMs: BASE_BACKOFF_MS * 2 ** world.errorRetries };
}
