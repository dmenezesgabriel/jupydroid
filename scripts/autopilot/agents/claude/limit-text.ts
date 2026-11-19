import type { AgentError, RateLimitVerdict } from "../../rate-limit.ts";

const WEEKLY_TEXT = /weekly (usage )?limit|7[- ]day|seven[- ]day/i;
const LIMIT_TEXT =
  /(usage|rate|session|weekly)[ -]limit|hit your (\w+ )?limit|limit reached|quota/i;

/**
 * Classifies a prompt error as a limit, or null when it is some other failure.
 * The adapter's own limit text is not documented, so any message that names a
 * limit polls (a `continue` every interval) and only an explicit weekly limit
 * halts; the structured `rate_limit` reading is what carries a reset time.
 *
 * @example
 * classifyClaudeError({ message: "You've hit your limit" });
 * // => { kind: "poll" }
 */
export function classifyClaudeError(
  error: AgentError,
): RateLimitVerdict | null {
  if (WEEKLY_TEXT.test(error.message)) return { kind: "weekly" };
  return LIMIT_TEXT.test(error.message) ? { kind: "poll" } : null;
}
