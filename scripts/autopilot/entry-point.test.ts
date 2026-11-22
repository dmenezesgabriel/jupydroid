import { pathToFileURL } from "node:url";
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { isEntryPoint } from "./entry-point.ts";

describe("isEntryPoint", () => {
  it("is true for the script node was started with", () => {
    const [, script] = process.argv;

    assert.equal(isEntryPoint(pathToFileURL(script ?? "").href), true);
  });

  it("is false for any other module", () => {
    // node --test starts each test file as its own script, so this file's URL is the entry point.
    const sibling = new URL("./entry-point.ts", import.meta.url).href;

    assert.equal(isEntryPoint(sibling), false);
  });
});
