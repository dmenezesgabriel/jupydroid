import type { AgentSignal } from "../../agent-signal.ts";

type ToolFacts = {
  command: string;
  taskId: string | null;
  outputPath: string | null;
  announced: boolean;
};

// The tool result names the file a backgrounded command writes to, for example
// "Command running in background with ID: bf6i5c6ud. Output is being written
// to: /tmp/.../tasks/bf6i5c6ud.output. You will be notified when it completes."
const OUTPUT_PATH = /Output is being written to: (\S+?\.output)(?=[.\s]|$)/;

/**
 * Joins the three frames that together say "this command went to the
 * background": the command (rawInput), the task id (`_meta.claudeCode`) and the
 * result text that names the output file. They arrive in separate frames, in
 * an order the adapter does not promise, so the join waits for all three.
 *
 * @example
 * tracker.fold({ type: "tool_command", toolCallId: "t", command: "pnpm test" });
 * tracker.fold({ type: "background_task_id", toolCallId: "t", taskId: "b1" });
 * tracker.fold({ type: "tool_output", toolCallId: "t", text: "...written to: /x/b1.output." });
 * // the last call returns the tool_output signal and a background_started one
 */
export class BackgroundTaskTracker {
  private readonly byToolCall = new Map<string, ToolFacts>();

  /** The signal itself, followed by `background_started` when it completed a task's facts. */
  fold(signal: AgentSignal): AgentSignal[] {
    if (!isToolFact(signal)) return [signal];
    const facts = this.factsOf(signal.toolCallId);
    this.record(facts, signal);
    return [signal, ...this.announce(facts)];
  }

  private factsOf(toolCallId: string): ToolFacts {
    const known = this.byToolCall.get(toolCallId);
    if (known) return known;
    const fresh = {
      command: "",
      taskId: null,
      outputPath: null,
      announced: false,
    };
    this.byToolCall.set(toolCallId, fresh);
    return fresh;
  }

  private record(facts: ToolFacts, signal: ToolFactSignal): void {
    if (signal.type === "tool_command") facts.command = signal.command;
    if (signal.type === "background_task_id") facts.taskId = signal.taskId;
    if (signal.type === "tool_output") {
      facts.outputPath ??= OUTPUT_PATH.exec(signal.text)?.[1] ?? null;
    }
  }

  private announce(facts: ToolFacts): AgentSignal[] {
    if (facts.announced || !facts.taskId || !facts.outputPath) return [];
    facts.announced = true;
    return [
      {
        type: "background_started",
        shellId: facts.taskId,
        command: facts.command,
        outputPath: facts.outputPath,
      },
    ];
  }
}

type ToolFactSignal = Extract<
  AgentSignal,
  { type: "tool_command" | "background_task_id" | "tool_output" }
>;

function isToolFact(signal: AgentSignal): signal is ToolFactSignal {
  return (
    signal.type === "tool_command" ||
    signal.type === "background_task_id" ||
    signal.type === "tool_output"
  );
}
