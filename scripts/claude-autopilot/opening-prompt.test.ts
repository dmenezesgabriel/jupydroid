import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, it } from "node:test";
import { openingPrompt, STANDING_RULES } from "./run.ts";
import { assertContains } from "./testing/assertions.ts";

let dir = "";

beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), "opening-prompt-"));
});

afterEach(async () => {
  await rm(dir, { recursive: true, force: true });
});

describe("openingPrompt", () => {
  it("attaches the prompt file, says to follow it, then states the standing rules", async () => {
    const file = join(dir, "prompt-execution.md");
    await writeFile(file, "# Execution session\n");

    const blocks = await openingPrompt(file);

    assert.deepEqual(blocks, [
      {
        type: "resource",
        resource: {
          uri: `file://${file}`,
          mimeType: "text/markdown",
          text: "# Execution session\n",
        },
      },
      { type: "text", text: "Follow prompt-execution.md (attached)." },
      { type: "text", text: STANDING_RULES },
    ]);
  });
});

describe("STANDING_RULES", () => {
  it("sends the session to the engineering-mode skill before it changes code", () => {
    assertContains(STANDING_RULES, "engineering-mode");
  });

  it("keeps AGENTS.md above the skills, so a skill cannot relax test-first work", () => {
    assertContains(STANDING_RULES, "AGENTS.md");
    assertContains(STANDING_RULES, "wins");
  });
});
