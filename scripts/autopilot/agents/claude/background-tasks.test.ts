import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { BackgroundExitWatcher } from "../../background-exit-watcher.ts";
import { BackgroundTaskTracker } from "../../agents/claude/background-task-tracker.ts";
import type { AgentSignal } from "../../agent-signal.ts";
import {
  FakeClock,
  FakeTaskOutputs,
} from "../../testing/fake-mailbox.ts";

const OUTPUT_TEXT =
  "Command running in background with ID: bf6i5c6ud. Output is being written to: /scratch/tmp/tasks/bf6i5c6ud.output. You will be notified when it completes.";

function foldAll(signals: AgentSignal[]): AgentSignal[] {
  const tracker = new BackgroundTaskTracker();
  return signals.flatMap((signal) => tracker.fold(signal));
}

describe("BackgroundTaskTracker", () => {
  it("joins the command, the task id and the output file into one started task", () => {
    const signals = foldAll([
      { type: "tool_command", toolCallId: "t1", command: "pnpm test" },
      { type: "background_task_id", toolCallId: "t1", taskId: "bf6i5c6ud" },
      { type: "tool_output", toolCallId: "t1", text: OUTPUT_TEXT },
    ]);

    assert.deepEqual(signals.filter((s) => s.type === "background_started"), [
      {
        type: "background_started",
        shellId: "bf6i5c6ud",
        command: "pnpm test",
        outputPath: "/scratch/tmp/tasks/bf6i5c6ud.output",
      },
    ]);
  });

  it("announces the task once, whichever fact arrives last", () => {
    const signals = foldAll([
      { type: "tool_output", toolCallId: "t1", text: OUTPUT_TEXT },
      { type: "tool_command", toolCallId: "t1", command: "pnpm test" },
      { type: "background_task_id", toolCallId: "t1", taskId: "bf6i5c6ud" },
      { type: "tool_output", toolCallId: "t1", text: OUTPUT_TEXT },
    ]);

    assert.equal(signals.filter((s) => s.type === "background_started").length, 1);
  });

  it("ignores a foreground command, which never gets a task id", () => {
    const signals = foldAll([
      { type: "tool_command", toolCallId: "t2", command: "ls" },
      { type: "tool_output", toolCallId: "t2", text: "a.txt" },
    ]);

    assert.equal(signals.some((s) => s.type === "background_started"), false);
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
    const errors: Array<[string, string]> = [];
    const watcher = new BackgroundExitWatcher(
      clock,
      outputs,
      (shellId, exitCode) => exits.push([shellId, exitCode]),
      (shellId, error) => errors.push([shellId, error.message]),
      5_000,
    );
    return { clock, outputs, exits, errors, watcher };
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

  it("reports a killed task as an exit with no code, because no code was ever observed", async () => {
    // 124 of 948 real task output files end in `[killed]`, not in an exit code.
    const { clock, outputs, exits, watcher } = build();
    watcher.watch("a", "/o/a.output");
    outputs.files.set("/o/a.output", "partial output\n\n[killed]\n");

    clock.tickAll();
    await settle();

    assert.deepEqual(exits, [["a", null]]);
  });

  it("does not take a killed line in the middle of the output for the end of the task", async () => {
    const { clock, outputs, exits, watcher } = build();
    outputs.files.set("/o/a.output", "[killed]\nstill printing\n");
    watcher.watch("a", "/o/a.output");

    clock.tickAll();
    await settle();

    assert.deepEqual(exits, []);
  });

  it("reports a read failure once per task, keeps polling, and still sees the exit afterwards", async () => {
    const { clock, outputs, exits, errors, watcher } = build();
    watcher.watch("a", "/o/a.output");
    outputs.failWith = new Error("EACCES: permission denied");

    for (let tick = 0; tick < 3; tick += 1) {
      clock.tickAll();
      await settle();
    }
    outputs.failWith = null;
    outputs.files.set("/o/a.output", "[exited with code 0]\n");
    clock.tickAll();
    await settle();

    assert.deepEqual(errors, [["a", "EACCES: permission denied"]]);
    assert.deepEqual(exits, [["a", 0]]);
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
