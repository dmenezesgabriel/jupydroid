import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { classifyDevinError } from "../../agents/devin/limit-text.ts";

// The message strings are verbatim from Devin's own logs and binary.
const FREE_MODEL_LIMIT =
  "Reached free model rate limit. Upgrade to Max for higher limits, or switch to a different model. Your limit will reset in 12 minutes (at 22:01 UTC).";
const DAILY_QUOTA =
  "Your daily usage quota has been exhausted. Visit https://app.devin.ai/settings/usage to purchase on-demand usage or turn on auto-reload.";
const MONTHLY_LIMIT =
  "You've reached your monthly usage limit. Wait for the limit to reset next month.";

describe("classifyDevinError", () => {
  it("waits until the printed UTC reset time for the free-model limit", () => {
    const now = new Date("2026-11-13T21:49:10Z");

    const verdict = classifyDevinError({ message: FREE_MODEL_LIMIT }, now);

    assert.deepEqual(verdict, {
      kind: "until",
      wakeAt: new Date("2026-11-13T22:01:00Z"),
    });
  });

  it("rolls the reset time over midnight to the next day", () => {
    const now = new Date("2026-11-13T23:55:00Z");

    const verdict = classifyDevinError(
      {
        message:
          "Reached free model rate limit. Your limit will reset in 12 minutes (at 00:07 UTC).",
      },
      now,
    );

    assert.deepEqual(verdict, {
      kind: "until",
      wakeAt: new Date("2026-11-14T00:07:00Z"),
    });
  });

  it("falls back to the relative minutes when no clock time is printed", () => {
    const now = new Date("2026-11-13T10:00:00Z");

    const verdict = classifyDevinError(
      { message: "Reached free model rate limit. reset in 5 minutes" },
      now,
    );

    assert.deepEqual(verdict, {
      kind: "until",
      wakeAt: new Date("2026-11-13T10:05:00Z"),
    });
  });

  it("polls when a rate limit prints no reset time at all", () => {
    const verdict = classifyDevinError(
      { message: "Reached free model rate limit." },
      new Date(),
    );

    assert.deepEqual(verdict, { kind: "poll" });
  });

  it("polls on the daily quota, whose reset is not printed", () => {
    assert.deepEqual(classifyDevinError({ message: DAILY_QUOTA }, new Date()), {
      kind: "poll",
    });
  });

  it("polls on the resource_exhausted error kind carried in the data", () => {
    const verdict = classifyDevinError(
      {
        message: "Quota exhausted.",
        data: { "cognition.ai/errorKind": "resource_exhausted" },
      },
      new Date(),
    );

    assert.deepEqual(verdict, { kind: "poll" });
  });

  it("reports the monthly limit as its own kind", () => {
    assert.deepEqual(classifyDevinError({ message: MONTHLY_LIMIT }, new Date()), {
      kind: "monthly",
    });
  });

  it("returns null for an error that is not a limit", () => {
    assert.equal(classifyDevinError({ message: "Internal error" }, new Date()), null);
  });
});
