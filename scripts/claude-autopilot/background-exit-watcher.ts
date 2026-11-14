import { readFile } from "node:fs/promises";
import type { Clock } from "./clock.ts";

export interface TaskOutputReader {
  /** The file's text, or null while it does not exist. */
  read(path: string): Promise<string | null>;
}

export const fileTaskOutputs: TaskOutputReader = {
  read: (path) => readFile(path, "utf8").catch(() => null),
};

// Claude Code appends this line to a background task's output file when the
// command ends; no ACP frame reports the exit, so the file is the only record.
const EXIT_LINE = /\[exited with code (-?\d+)\]\s*$/;

/**
 * Turns "the output file now ends with an exit line" into an `onExit` call.
 * Polling stops for a task at its exit and for every task at `stopAll`.
 *
 * @example
 * watcher.watch("bf6i5c6ud", "/tmp/.../tasks/bf6i5c6ud.output");
 * // later: onExit("bf6i5c6ud", 0)
 */
export class BackgroundExitWatcher {
  private readonly stops = new Map<string, () => void>();

  constructor(
    private readonly clock: Clock,
    private readonly outputs: TaskOutputReader,
    private readonly onExit: (shellId: string, exitCode: number) => void,
    private readonly pollMs: number,
  ) {}

  watch(shellId: string, outputPath: string): void {
    if (this.stops.has(shellId)) return;
    const stop = this.clock.every(this.pollMs, () => {
      void this.check(shellId, outputPath);
    });
    this.stops.set(shellId, stop);
  }

  stopAll(): void {
    for (const stop of this.stops.values()) stop();
    this.stops.clear();
  }

  private async check(shellId: string, outputPath: string): Promise<void> {
    const text = await this.outputs.read(outputPath);
    const code = text === null ? undefined : EXIT_LINE.exec(text)?.[1];
    if (code === undefined || !this.stops.has(shellId)) return;
    this.stops.get(shellId)?.();
    this.stops.delete(shellId);
    this.onExit(shellId, Number(code));
  }
}
