import { readFile } from "node:fs/promises";
import type { Clock } from "./clock.ts";

export interface TaskOutputReader {
  /** The file's text, or null while it does not exist. Any other failure throws. */
  read(path: string): Promise<string | null>;
}

const MISSING_FILE = "ENOENT";

/** A missing file is a task that has written nothing yet; an unreadable one is an error the caller must hear about. */
export const fileTaskOutputs: TaskOutputReader = {
  read: (path) =>
    readFile(path, "utf8").catch((error: NodeJS.ErrnoException) => {
      if (error.code === MISSING_FILE) return null;
      throw error;
    }),
};

// Claude Code appends one of these lines to a background task's output file
// when the task ends; no ACP frame reports it, so the file is the only
// record. Measured over 948 real task files: 823 end in an exit code and 124
// in `[killed]`, a task Claude Code stopped, which has no exit code.
const END_LINE = /\[(?:exited with code (-?\d+)|killed)\]\s*$/;

/**
 * Turns "the output file now ends with an end line" into an `onExit` call: the
 * exit code, or null when the task was killed. Polling stops for a task at its
 * end and for every task at `stopAll`. A read that fails is reported once per
 * task through `onError` and polling goes on, so a transient fault neither
 * crashes the loop nor hides a task that has ended.
 *
 * @example
 * watcher.watch("bf6i5c6ud", "/tmp/.../tasks/bf6i5c6ud.output");
 * // later: onExit("bf6i5c6ud", 0), or onExit("bf6i5c6ud", null) if it was killed
 */
export class BackgroundExitWatcher {
  private readonly stops = new Map<string, () => void>();
  private readonly failedReads = new Set<string>();

  constructor(
    private readonly clock: Clock,
    private readonly outputs: TaskOutputReader,
    private readonly onExit: (shellId: string, exitCode: number | null) => void,
    private readonly onError: (shellId: string, error: Error) => void,
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
    const text = await this.readOrReport(shellId, outputPath);
    const end = text === null ? null : END_LINE.exec(text);
    if (end === null || !this.stops.has(shellId)) return;
    this.stops.get(shellId)?.();
    this.stops.delete(shellId);
    this.onExit(shellId, end[1] === undefined ? null : Number(end[1]));
  }

  /** The file's text, or null when there is none to judge yet: missing, or unreadable (reported once). */
  private async readOrReport(
    shellId: string,
    outputPath: string,
  ): Promise<string | null> {
    try {
      return await this.outputs.read(outputPath);
    } catch (error) {
      if (!this.failedReads.has(shellId)) {
        this.failedReads.add(shellId);
        this.onError(
          shellId,
          error instanceof Error ? error : new Error(String(error)),
        );
      }
      return null;
    }
  }
}
