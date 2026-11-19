import { mkdtemp, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import assert from "node:assert/strict";
import { describe, it, beforeEach, afterEach } from "node:test";
import { DirectoryMailboxFiles } from "./mailbox.ts";
import { RunDir } from "./run-dir.ts";
import { assertPartial } from "./testing/assertions.ts";

let dir = "";

beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), "autopilot-test-"));
});

afterEach(async () => {
  await rm(dir, { recursive: true, force: true });
});

describe("RunDir and DirectoryMailboxFiles share one contract", () => {
  it("delivers an answer written by ctl to the loop, once", async () => {
    const files = new DirectoryMailboxFiles(dir, () => "t");

    await new RunDir(dir).writeAnswer("q1", ["hi.txt"]);

    assertPartial(await files.readAnswer("q1"), {
      answer: ["hi.txt"],
    });
    await files.clearAnswer("q1");
    assert.equal(await files.readAnswer("q1"), null);
  });

  it("appends events as JSON lines with a timestamp", async () => {
    const files = new DirectoryMailboxFiles(dir, () => "2026-11-13T22:00:00Z");

    await files.appendEvent({ type: "needs_answer", id: "q1" });
    await files.appendEvent({ type: "iteration_done" });

    const { readFile } = await import("node:fs/promises");
    const lines = (await readFile(join(dir, "events.jsonl"), "utf8"))
      .trim()
      .split("\n");
    assert.deepEqual(
      lines.map((line) => JSON.parse(line)),
      [
        { at: "2026-11-13T22:00:00Z", type: "needs_answer", id: "q1" },
        { at: "2026-11-13T22:00:00Z", type: "iteration_done" },
      ],
    );
  });
});

describe("RunDir.readEvents", () => {
  it("returns the journaled events in order and skips a torn line", async () => {
    const files = new DirectoryMailboxFiles(dir, () => "t");
    await files.appendEvent({ type: "loop_started" });
    const { appendFile } = await import("node:fs/promises");
    await appendFile(join(dir, "events.jsonl"), '{"at":"t","type":"iterat\n');
    await files.appendEvent({ type: "iteration_done" });

    const events = await new RunDir(dir).readEvents();

    assert.deepEqual(
      events.map((event) => event.type),
      ["loop_started", "iteration_done"],
    );
  });

  it("is empty before the loop ever ran", async () => {
    assert.deepEqual(await new RunDir(dir).readEvents(), []);
  });
});

describe("RunDir stop and lock", () => {
  it("reports a stop request until it is cleared", async () => {
    const runDir = new RunDir(dir);
    assert.equal(await runDir.stopRequested(), false);

    await runDir.requestStop();
    assert.equal(await runDir.stopRequested(), true);
    await runDir.clearStop();

    assert.equal(await runDir.stopRequested(), false);
  });

  it("refuses a second loop while the first one's pid is alive", async () => {
    const runDir = new RunDir(dir);
    await runDir.acquireLock(111, () => true);

    await assert.rejects(
      runDir.acquireLock(222, () => true),
      /already runs here as pid 111/,
    );
  });

  it("spends the previous loop's stop request when a new loop takes the lock, so a restart is not stopped at once", async () => {
    const runDir = new RunDir(dir);
    await runDir.acquireLock(111, () => true);
    await runDir.requestStop();
    await runDir.releaseLock();

    await runDir.acquireLock(222, () => false);

    assert.equal(await runDir.stopRequested(), false);
  });

  it("keeps a stop request when the lock is refused, because the loop that holds it must still see it", async () => {
    const runDir = new RunDir(dir);
    await runDir.acquireLock(111, () => true);
    await runDir.requestStop();

    await assert.rejects(runDir.acquireLock(222, () => true));

    assert.equal(await runDir.stopRequested(), true);
  });

  it("takes over a lock whose pid is gone", async () => {
    const runDir = new RunDir(dir);
    await runDir.acquireLock(111, () => true);

    await runDir.acquireLock(222, () => false);

    assert.equal(await runDir.lockHolder(), 222);
  });
});

describe("RunDir choice file", () => {
  const devin = { agent: "devin", model: "swe-2-high", effort: null };

  it("is empty until a choice was written", async () => {
    assert.equal(await new RunDir(dir).readChoice(), null);
  });

  it("reads back what was written and leaves no temporary file for a reader to trip on", async () => {
    const runDir = new RunDir(dir);

    await runDir.writeChoice(devin);

    assert.deepEqual(await runDir.readChoice(), devin);
    assert.deepEqual(await readdir(dir), ["choice.json"]);
  });

  it("replaces an earlier choice whole", async () => {
    const runDir = new RunDir(dir);
    await runDir.writeChoice(devin);

    await runDir.writeChoice({ agent: "claude", model: null, effort: "high" });

    assert.deepEqual(await runDir.readChoice(), {
      agent: "claude",
      model: null,
      effort: "high",
    });
  });

  it("refuses a file that is not a choice, naming its path", async () => {
    await writeFile(join(dir, "choice.json"), "not json");

    await assert.rejects(
      new RunDir(dir).readChoice(),
      (error: Error) => error.message.includes(join(dir, "choice.json")),
    );
  });
});
