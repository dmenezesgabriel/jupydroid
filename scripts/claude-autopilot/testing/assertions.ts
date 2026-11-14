import assert from "node:assert/strict";

/** Fails with both values in the message; `assert.ok(a.includes(b))` would say only "false". */
export function assertContains(
  haystack: string | readonly unknown[] | null | undefined,
  needle: unknown,
): void {
  const found = contains(haystack, needle);
  assert.ok(
    found,
    `expected ${JSON.stringify(haystack)} to contain ${JSON.stringify(needle)}`,
  );
}

function contains(
  haystack: string | readonly unknown[] | null | undefined,
  needle: unknown,
): boolean {
  if (haystack === null || haystack === undefined) return false;
  if (typeof haystack === "string") {
    return typeof needle === "string" && haystack.includes(needle);
  }
  return haystack.includes(needle);
}

/** Fails when no element of the list deep-equals the wanted one. */
export function assertContainsEqual(
  list: readonly unknown[],
  wanted: unknown,
): void {
  const found = list.some((item) => {
    try {
      assert.deepEqual(item, wanted);
      return true;
    } catch {
      return false;
    }
  });
  assert.ok(
    found,
    `expected ${JSON.stringify(list)} to contain an element equal to ${JSON.stringify(wanted)}`,
  );
}

/**
 * Every property of `wanted` holds in `actual`, recursively, and `actual` may carry more. Arrays are
 * compared element by element, at equal length: a list that grew is a change a test should see.
 *
 * @example
 * assertPartial({ type: "plan_ready", plan: "x" }, { type: "plan_ready" });
 */
export function assertPartial(actual: unknown, wanted: unknown): void {
  assert.deepEqual(projectOnto(actual, wanted), wanted);
}

function projectOnto(actual: unknown, wanted: unknown): unknown {
  if (Array.isArray(wanted) && Array.isArray(actual)) {
    return actual.map((item, at) => projectOnto(item, wanted[at]));
  }
  if (isRecord(wanted) && isRecord(actual)) {
    return Object.fromEntries(
      Object.keys(wanted).map((key) => [
        key,
        projectOnto(actual[key], wanted[key]),
      ]),
    );
  }
  return actual;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * Polls until the check stops throwing, for what a fake channel does after the call under test has
 * returned. Bounded by wall time because the thing awaited is a microtask chain, not the host's speed.
 *
 * @example
 * await waitUntil(() => assert.equal(channel.sent.length, 1));
 */
export async function waitUntil(
  check: () => void,
  timeoutMs = 1_000,
): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    try {
      return check();
    } catch (error) {
      if (Date.now() >= deadline) throw error;
      await new Promise((resolve) => setTimeout(resolve, 5));
    }
  }
}
