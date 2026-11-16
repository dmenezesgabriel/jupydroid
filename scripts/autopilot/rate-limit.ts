/** What a rate-limit signal or error tells the loop to do. */
export type RateLimitVerdict =
  /** Claude reported when the limit resets: wait until then. */
  | { kind: "until"; wakeAt: Date }
  /** No reset time is known: send `continue` on an interval. */
  | { kind: "poll" }
  /** A seven-day cap: waiting for it is not an overnight job, so the loop halts. */
  | { kind: "weekly" };

export type AgentError = { message: string; data?: unknown };

/** The `rate_limit` signal's payload: Claude Code's `rate_limit_info`, with the reset time in milliseconds. */
export type RateLimitReading = {
  status: string;
  resetsAtMs: number | null;
  limitType: string | null;
};

const WEEKLY_TYPE = /^seven_day/;
const WEEKLY_TEXT = /weekly (usage )?limit|7[- ]day|seven[- ]day/i;
const LIMIT_TEXT =
  /(usage|rate|session|weekly)[ -]limit|hit your (\w+ )?limit|limit reached|quota/i;

/**
 * Classifies a `rate_limit` reading. Only `rejected` stops the work; `allowed`
 * and `allowed_warning` are the adapter reporting how close the account is.
 *
 * @example
 * classifyRateLimit({ status: "rejected", resetsAtMs: 1_800_000_000_000, limitType: "five_hour" });
 * // => { kind: "until", wakeAt: <that instant> }
 */
export function classifyRateLimit(
  reading: RateLimitReading,
): RateLimitVerdict | null {
  if (reading.status !== "rejected") return null;
  if (reading.limitType && WEEKLY_TYPE.test(reading.limitType)) {
    return { kind: "weekly" };
  }
  if (reading.resetsAtMs === null) return { kind: "poll" };
  return { kind: "until", wakeAt: new Date(reading.resetsAtMs) };
}

/**
 * Classifies a prompt error as a limit, or null when it is some other failure.
 * The adapter's own limit text is not documented, so any message that names a
 * limit polls (a `continue` every interval) and only an explicit weekly limit
 * halts; the structured `rate_limit` reading is what carries a reset time.
 *
 * @example
 * classifyAgentError({ message: "You've hit your limit" });
 * // => { kind: "poll" }
 */
export function classifyAgentError(error: AgentError): RateLimitVerdict | null {
  if (WEEKLY_TEXT.test(error.message)) return { kind: "weekly" };
  return LIMIT_TEXT.test(error.message) ? { kind: "poll" } : null;
}
