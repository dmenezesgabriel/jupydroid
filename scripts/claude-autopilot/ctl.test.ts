import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { splitWhy } from "./ctl.ts";

describe("splitWhy", () => {
  it("leaves the arguments alone when no reason is given", () => {
    assert.deepEqual(splitWhy(["zod", "valibot"]), {
      values: ["zod", "valibot"],
    });
  });

  it("takes the reason off the front so it can be journaled with the answer", () => {
    assert.deepEqual(splitWhy(["--why", "ADR-0004 keeps zod", "zod"]), {
      why: "ADR-0004 keeps zod",
      values: ["zod"],
    });
  });
});
