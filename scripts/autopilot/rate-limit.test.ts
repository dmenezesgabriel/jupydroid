import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { classifyRateLimit } from "./rate-limit.ts";

const RESETS_AT_MS = Date.parse("2026-11-13T22:12:00Z");

describe("classifyRateLimit (the structured rate_limit_event)", () => {
  it("waits until the reset time of a rejected five-hour limit", () => {
    const verdict = classifyRateLimit({
      status: "rejected",
      resetsAtMs: RESETS_AT_MS,
      limitType: "five_hour",
    });

    assert.deepEqual(verdict, { kind: "until", wakeAt: new Date(RESETS_AT_MS) });
  });

  it("halts on a rejected weekly limit instead of waiting days", () => {
    for (const limitType of [
      "seven_day",
      "seven_day_opus",
      "seven_day_sonnet",
    ]) {
      const verdict = classifyRateLimit({
        status: "rejected",
        resetsAtMs: RESETS_AT_MS,
        limitType,
      });

      assert.deepEqual(verdict, { kind: "weekly" });
    }
  });

  it("polls when a rejection prints no reset time", () => {
    const verdict = classifyRateLimit({
      status: "rejected",
      resetsAtMs: null,
      limitType: null,
    });

    assert.deepEqual(verdict, { kind: "poll" });
  });

  it("is not a limit while the status is allowed or only a warning", () => {
    for (const status of ["allowed", "allowed_warning"]) {
      assert.equal(classifyRateLimit({
          status,
          resetsAtMs: RESETS_AT_MS,
          limitType: null,
        }), null);
    }
  });
});
