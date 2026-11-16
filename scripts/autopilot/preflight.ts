import { readdir, readFile, readlink } from "node:fs/promises";
import { cpus } from "node:os";
import type { Clock } from "./clock.ts";
import type { LoopEvent } from "./mailbox.ts";

export type HostReadings = {
  load1: number;
  cores: number;
  memoryStallAvg60: number;
};
export type ProcEntry = { pid: number; cwd: string; cmdline: string };

/**
 * Marks the row the backlog says to work on next. It follows the row's state, whichever that is
 * (`open ← next`, `partial ← next`), because an item part-way through is still the one to continue.
 */
const NEXT_ROW_MARK = "← next";
/**
 * Processes a finished session leaves behind in the repo and must not be started over: the emulator
 * and Maestro, which hold gigabytes and a device. A Gradle daemon is not listed, because an IDE the
 * user has open owns one in this tree and waiting on it would never end.
 */
const LEFTOVER = /\b(qemu-system-\S+|emulator|maestro)\b/;

/** AGENTS.md "Host resources": wait while load exceeds the cores or tasks already stall for memory. */
export function hostBusyReason(readings: HostReadings): string | null {
  if (readings.load1 > readings.cores) {
    return `1-minute load ${readings.load1} exceeds ${readings.cores} cores`;
  }
  if (readings.memoryStallAvg60 > 0) {
    return `tasks stalled for memory ${readings.memoryStallAvg60}% of the last minute`;
  }
  return null;
}

/**
 * The id in the first column of the backlog row marked next, or null when no row is. A table row
 * is the only line that counts: the backlog's prose also names the marker.
 *
 * @example
 * nextBacklogRow("| `ID-1` | A title | open ← next |"); // => "ID-1"
 */
export function nextBacklogRow(backlog: string): string | null {
  const line = backlog
    .split("\n")
    .find(
      (candidate) =>
        candidate.startsWith("|") && candidate.includes(NEXT_ROW_MARK),
    );
  if (!line) return null;
  const firstColumn = line.split("|")[1]?.trim().replaceAll("`", "");
  return firstColumn || line.trim();
}

export function leftoversIn(
  entries: ProcEntry[],
  repoRoot: string,
): ProcEntry[] {
  return entries.filter(
    (entry) =>
      (entry.cwd === repoRoot || entry.cwd.startsWith(`${repoRoot}/`)) &&
      LEFTOVER.test(entry.cmdline),
  );
}

export async function readHostReadings(): Promise<HostReadings> {
  const load = await readFile("/proc/loadavg", "utf8");
  const pressure = await readFile("/proc/pressure/memory", "utf8");
  const stall = /some avg10=\S+ avg60=([\d.]+)/.exec(pressure);
  return {
    load1: Number(load.split(" ")[0]),
    cores: cpus().length,
    memoryStallAvg60: Number(stall?.[1] ?? 0),
  };
}

export async function readProcEntries(): Promise<ProcEntry[]> {
  const pids = (await readdir("/proc")).filter((name) => /^\d+$/.test(name));
  const entries = await Promise.all(pids.map(readProcEntry));
  return entries.filter((entry): entry is ProcEntry => entry !== null);
}

async function readProcEntry(pid: string): Promise<ProcEntry | null> {
  try {
    const cwd = await readlink(`/proc/${pid}/cwd`);
    const cmdline = (await readFile(`/proc/${pid}/cmdline`, "utf8"))
      .replaceAll("\0", " ")
      .trim();
    return { pid: Number(pid), cwd, cmdline };
  } catch {
    return null; // the process ended, or belongs to another user
  }
}

export type PreflightDeps = {
  repoRoot: string;
  readBacklog(): Promise<string>;
  readHost(): Promise<HostReadings>;
  readProcs(): Promise<ProcEntry[]>;
  stopRequested(): Promise<boolean>;
  clock: Clock;
  emit(event: LoopEvent): Promise<void>;
  recheckMs: number;
  repeatReportMs: number;
};

export type PreflightResult = "ready" | "backlog_exhausted" | "stop";

/**
 * Holds the loop until a new session may start: a next backlog row exists,
 * the host has room, and the previous session left no dev server or browser
 * in the repo. It reports why it waits, never kills what it did not start,
 * and gives up only when the backlog is empty or a stop was asked for.
 */
export async function waitUntilReady(
  deps: PreflightDeps,
): Promise<PreflightResult> {
  let reported = "";
  let reportedAt = -Infinity;
  for (;;) {
    if (await deps.stopRequested()) return "stop";
    if (nextBacklogRow(await deps.readBacklog()) === null)
      return "backlog_exhausted";
    const reason = await blockingReason(deps);
    if (reason === null) return "ready";
    const stale = deps.clock.now() - reportedAt >= deps.repeatReportMs;
    if (reason !== reported || stale) {
      reported = reason;
      reportedAt = deps.clock.now();
      await deps.emit({ type: "preflight_wait", reason });
    }
    await deps.clock.sleep(deps.recheckMs);
  }
}

function describeProcess(entry: ProcEntry): string {
  return `${entry.pid} ${entry.cmdline.slice(0, 60)}`;
}

async function blockingReason(deps: PreflightDeps): Promise<string | null> {
  const busy = hostBusyReason(await deps.readHost());
  if (busy) return busy;
  const left = leftoversIn(await deps.readProcs(), deps.repoRoot);
  if (left.length === 0) return null;
  const described = left.map(describeProcess).join("; ");
  return `leftover processes in the repo: ${described}`;
}
