import type { AgentError, RateLimitVerdict } from "../../rate-limit.ts";

const MONTHLY = /monthly usage limit/i;
const DAILY_QUOTA = /daily usage quota|quota (has been )?exhausted/i;
const RATE_LIMIT = /rate limit/i;
const RESET_CLOCK = /reset[^.]*?\(at (\d{2}):(\d{2}) UTC\)/i;
const RESET_RELATIVE = /reset in (\d+) minutes?/i;
const RESOURCE_EXHAUSTED_KIND = "resource_exhausted";
const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Classifies a prompt error from `devin acp` as a limit, or null when it is
 * some other failure. The message shapes come from Devin's own logs, for
 * example "Your limit will reset in 12 minutes (at 22:01 UTC)."; a printed
 * clock time wins over the rounded relative minutes. The daily quota prints no
 * reset time and polls; the monthly cap halts.
 *
 * @example
 * classifyDevinError({ message: "... reset in 12 minutes (at 22:01 UTC)." }, new Date());
 * // => { kind: "until", wakeAt: <next 22:01 UTC> }
 */
export function classifyDevinError(
  error: AgentError,
  now: Date,
): RateLimitVerdict | null {
  const { message } = error;
  if (MONTHLY.test(message)) return { kind: "monthly" };
  if (
    DAILY_QUOTA.test(message) ||
    errorKindOf(error) === RESOURCE_EXHAUSTED_KIND
  ) {
    return { kind: "poll" };
  }
  if (!RATE_LIMIT.test(message)) return null;
  const wakeAt = resetTimeIn(message, now);
  return wakeAt ? { kind: "until", wakeAt } : { kind: "poll" };
}

function errorKindOf(error: AgentError): unknown {
  const data = error.data;
  if (typeof data !== "object" || data === null) return undefined;
  return (data as Record<string, unknown>)["cognition.ai/errorKind"];
}

function resetTimeIn(message: string, now: Date): Date | null {
  const clock = RESET_CLOCK.exec(message);
  if (clock) return nextUtcClockTime(Number(clock[1]), Number(clock[2]), now);
  const relative = RESET_RELATIVE.exec(message);
  if (relative) return new Date(now.getTime() + Number(relative[1]) * 60_000);
  return null;
}

function nextUtcClockTime(hour: number, minute: number, now: Date): Date {
  const today = Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate(),
    hour,
    minute,
  );
  return new Date(today >= now.getTime() ? today : today + DAY_MS);
}
