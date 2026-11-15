import type { JournalEvent } from "./journal-summary.ts";
import type { Answer } from "./mailbox.ts";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";

/**
 * Outside scripts/: the doc-citations gate reads every file under it, and a run directory holds
 * journals, answer files, a pid and a STOP marker, none of which carry prose.
 */
export const DEFAULT_RUN_DIR = ".autopilot-run";

/** The files the loop and `ctl` share, all under one directory that git ignores. */
export class RunDir {
  constructor(readonly path: string) {}

  async writeAnswer(id: string, answer: Answer, why?: string): Promise<void> {
    await mkdir(join(this.path, "answers"), { recursive: true });
    await writeFile(
      join(this.path, "answers", `${id}.json`),
      JSON.stringify({ answer, why }),
    );
  }

  /** Every event the loop journaled, oldest first; a line that is not JSON is skipped. */
  async readEvents(): Promise<JournalEvent[]> {
    const text = await readFile(join(this.path, "events.jsonl"), "utf8").catch(
      () => "",
    );
    return text.split("\n").flatMap(parseEventLine);
  }

  async requestStop(): Promise<void> {
    await mkdir(this.path, { recursive: true });
    await writeFile(join(this.path, "STOP"), "");
  }

  async stopRequested(): Promise<boolean> {
    return readFile(join(this.path, "STOP")).then(
      () => true,
      () => false,
    );
  }

  async clearStop(): Promise<void> {
    await rm(join(this.path, "STOP"), { force: true });
  }

  /**
   * Records this process as the loop; refuses when another live loop already
   * owns the directory. A stop request belongs to the loop that was running:
   * taking the lock spends it, otherwise a restart after `ctl stop` would stop
   * at its first preflight. A refused lock leaves it for the loop that holds it.
   */
  async acquireLock(
    pid: number,
    isAlive: (pid: number) => boolean,
  ): Promise<void> {
    await mkdir(this.path, { recursive: true });
    const holder = await this.lockHolder();
    if (holder !== null && holder !== pid && isAlive(holder)) {
      throw new Error(
        `claude-autopilot already runs here as pid ${holder} (${this.path}/loop.pid)`,
      );
    }
    await writeFile(join(this.path, "loop.pid"), String(pid));
    await this.clearStop();
  }

  async lockHolder(): Promise<number | null> {
    const text = await readFile(join(this.path, "loop.pid"), "utf8").catch(
      () => "",
    );
    const pid = Number(text.trim());
    return Number.isInteger(pid) && pid > 0 ? pid : null;
  }

  async releaseLock(): Promise<void> {
    await rm(join(this.path, "loop.pid"), { force: true });
  }
}

function parseEventLine(line: string): JournalEvent[] {
  try {
    const parsed: unknown = JSON.parse(line);
    return typeof parsed === "object" && parsed !== null
      ? [parsed as JournalEvent]
      : [];
  } catch {
    return [];
  }
}

/** Signal 0 only checks that the process exists. */
export function isProcessAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}
