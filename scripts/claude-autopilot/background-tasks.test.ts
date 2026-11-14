import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { BackgroundExitWatcher } from "./background-exit-watcher.ts";
import { BackgroundTaskTracker } from "./background-task-tracker.ts";
import type { AgentSignal } from "./signals.ts";
import { FakeClock, FakeTaskOutputs } from "./testing/fake-mailbox.ts";

const OUTPUT_TEXT =
  "Command running in background with ID: bf6i5c6ud. Output is being written to: /scratch/tmp/tasks/bf6i5c6ud.output. You will be notified when it completes.";

function foldAll(signals: AgentSignal[]): AgentSignal[] {
  const tracker = new BackgroundTaskTracker();
  return signals.flatMap((signal) => tracker.fold(signal));
}

describe("BackgroundTaskTracker", () => {
  it("joins the command, the task id and the output file into one started task", () => {
    const signals = foldAll([
      {
        type: "tool_command",
        toolCallId: "t1",
        command: "./gradlew testDebugUnitTest",
      },
      { type: "background_task_id", toolCallId: "t1", taskId: "bf6i5c6ud" },
      { type: "tool_output", toolCallId: "t1", text: OUTPUT_TEXT },
    ]);

    assert.deepEqual(
      signals.filter((s) => s.type === "background_started"),
      [
        {
          type: "background_started",
          shellId: "bf6i5c6ud",
          command: "./gradlew testDebugUnitTest",
          outputPath: "/scratch/tmp/tasks/bf6i5c6ud.output",
        },
      ],
    );
  });

  it("announces the task once, whichever fact arrives last", () => {
    const signals = foldAll([
      { type: "tool_output", toolCallId: "t1", text: OUTPUT_TEXT },
      {
        type: "tool_command",
        toolCallId: "t1",
        command: "./gradlew testDebugUnitTest",
      },
      { type: "background_task_id", toolCallId: "t1", taskId: "bf6i5c6ud" },
      { type: "tool_output", toolCallId: "t1", text: OUTPUT_TEXT },
    ]);

    assert.equal(
      signals.filter((s) => s.type === "background_started").length,
      1,
    );
  });

  it("ignores a foreground command, which never gets a task id", () => {
    const signals = foldAll([
      { type: "tool_command", toolCallId: "t2", command: "ls" },
      { type: "tool_output", toolCallId: "t2", text: "a.txt" },
    ]);

    assert.equal(
      signals.some((s) => s.type === "background_started"),
      false,
    );
  });

  it("passes every incoming signal through unchanged", () => {
    const incoming: AgentSignal = { type: "mode_changed", mode: "plan" };

    assert.deepEqual(foldAll([incoming]), [incoming]);
  });
});

describe("BackgroundExitWatcher", () => {
  function build() {
    const clock = new FakeClock();
    const outputs = new FakeTaskOutputs();
    const exits: Array<[string, number | null]> = [];
    const watcher = new BackgroundExitWatcher(
      clock,
      outputs,
      (shellId, exitCode) => exits.push([shellId, exitCode]),
      5_000,
    );
    return { clock, outputs, exits, watcher };
  }
  const settle = (): Promise<void> =>
    new Promise((resolve) => setImmediate(resolve));

  it("stays quiet while the output file has no exit line", async () => {
    const { clock, outputs, exits, watcher } = build();
    outputs.files.set("/o/a.output", "running...\n");
    watcher.watch("a", "/o/a.output");

    clock.tickAll();
    await settle();

    assert.deepEqual(exits, []);
  });

  it("reports the exit code once the file ends with its exit line, then stops polling", async () => {
    const { clock, outputs, exits, watcher } = build();
    watcher.watch("a", "/o/a.output");
    outputs.files.set("/o/a.output", "done\n\n[exited with code 3]\n");

    clock.tickAll();
    await settle();
    clock.tickAll();
    await settle();

    assert.deepEqual(exits, [["a", 3]]);
    assert.equal(outputs.reads, 1);
  });

  it("treats a file that does not exist yet as still running", async () => {
    const { clock, exits, watcher } = build();
    watcher.watch("a", "/o/missing.output");

    clock.tickAll();
    await settle();

    assert.deepEqual(exits, []);
  });

  it("stops every watch when asked", async () => {
    const { clock, outputs, exits, watcher } = build();
    outputs.files.set("/o/a.output", "[exited with code 0]\n");
    watcher.watch("a", "/o/a.output");

    watcher.stopAll();
    clock.tickAll();
    await settle();

    assert.deepEqual(exits, []);
  });
});
