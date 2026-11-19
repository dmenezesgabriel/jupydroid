import { spawn } from "node:child_process";
import { appendFile } from "node:fs/promises";
import { createInterface } from "node:readline";
import type { LineChannel } from "./json-rpc.ts";

export type SpawnedAgent = { channel: LineChannel; stop(): Promise<void> };

const KILL_AFTER_MS = 10_000;

/**
 * Starts an ACP agent in the repo and exposes its stdio as a
 * {@link LineChannel}. Every line in either direction is appended to the
 * transcript, the record to read in the morning when an iteration went
 * somewhere unexpected.
 *
 * @example
 * const spawned = spawnAcpAgent("/repo", "/repo/.run/acp.jsonl", "claude-agent-acp", []);
 */
export function spawnAcpAgent(
  cwd: string,
  transcriptPath: string,
  command: string,
  args: readonly string[],
): SpawnedAgent {
  // The installed agent is resolved from PATH on purpose (or named by the
  // caller when the shell has no nvm): the loop must run whichever version is
  // installed, as the manual workflow does.
  const child = spawn(command, [...args], {
    cwd,
    stdio: ["pipe", "pipe", "ignore"],
  });
  const record = (dir: "in" | "out", line: string): void => {
    void appendFile(
      transcriptPath,
      `${JSON.stringify({ at: Date.now(), dir, line })}\n`,
    );
  };
  const closed = new Promise<void>((resolve) =>
    child.once("close", () => resolve()),
  );
  const channel: LineChannel = {
    send: (line) => {
      record("out", line);
      child.stdin.write(`${line}\n`);
    },
    onLine: (listener) => {
      createInterface({ input: child.stdout }).on("line", (line) => {
        record("in", line);
        listener(line);
      });
    },
    onClose: (listener) => {
      void closed.then(listener);
    },
  };
  return { channel, stop: () => stopChild(child, closed) };
}

async function stopChild(
  child: ReturnType<typeof spawn>,
  closed: Promise<void>,
): Promise<void> {
  if (child.exitCode !== null || child.signalCode !== null) return;
  child.kill("SIGTERM");
  const timer = setTimeout(() => child.kill("SIGKILL"), KILL_AFTER_MS);
  await closed;
  clearTimeout(timer);
}
