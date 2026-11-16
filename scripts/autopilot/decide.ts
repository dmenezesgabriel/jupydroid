import {
  classifyAgentError,
  type AgentError,
  type RateLimitVerdict,
} from "./rate-limit.ts";

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
  commitsMade: boolean;
  clean: boolean;
  replyRounds: number;
  errorRetries: number;
  /** A rejected rate-limit reading seen during the turn that just ended. */
  limit: RateLimitVerdict | null;
};

export type ReplyReason = "plan_not_exited" | "no_commit" | "dirty_tree";

export type Decision =
  | { do: "finish" }
  | { do: "await_work" }
  | { do: "ask_reply"; why: ReplyReason }
  | { do: "continue" }
  | { do: "wait_until"; wakeAt: Date }
  | { do: "poll_limit" }
  | { do: "retry"; backoffMs: number }
  | { do: "stall" }
  | { do: "halt"; reason: "weekly_limit" | "refusal" | "cancelled" | "error" }
  | { do: "crash" };

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
  if (end.kind === "failed") return decideAfterError(end.error, world);
  switch (end.reason) {
    case "end_turn":
      return decideAfterEndTurn(world);
    case "max_tokens":
    case "max_turn_requests":
      return { do: "continue" };
    case "refusal":
      return { do: "halt", reason: "refusal" };
    case "cancelled":
      return { do: "halt", reason: "cancelled" };
  }
}

function decideAfterEndTurn(world: World): Decision {
  // A rejected limit ends the turn like any other stop, with the limit text as
  // the agent's last words: reading that as "no commit" would nudge a session
  // that cannot answer.
  if (world.limit) return decideForLimit(world.limit);
  if (world.working) return { do: "await_work" };
  if (world.replyRounds >= MAX_REPLY_ROUNDS) return { do: "stall" };
  if (world.mode === "plan") return { do: "ask_reply", why: "plan_not_exited" };
  if (!world.commitsMade) return { do: "ask_reply", why: "no_commit" };
  if (!world.clean) return { do: "ask_reply", why: "dirty_tree" };
  return { do: "finish" };
}

function decideForLimit(limit: RateLimitVerdict): Decision {
  if (limit.kind === "weekly") return { do: "halt", reason: "weekly_limit" };
  if (limit.kind === "until") return { do: "wait_until", wakeAt: limit.wakeAt };
  return { do: "poll_limit" };
}

function decideAfterError(error: AgentError, world: World): Decision {
  const limit = world.limit ?? classifyAgentError(error);
  if (limit) return decideForLimit(limit);
  if (world.errorRetries >= MAX_ERROR_RETRIES)
    return { do: "halt", reason: "error" };
  return { do: "retry", backoffMs: BASE_BACKOFF_MS * 2 ** world.errorRetries };
}
