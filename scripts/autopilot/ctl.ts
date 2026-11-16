import { resolve } from "node:path";
import { describeSummary, summarizeJournal } from "./journal-summary.ts";
import { DEFAULT_RUN_DIR, RunDir, isProcessAlive } from "./run-dir.ts";
import { isEntryPoint } from "./entry-point.ts";

const USAGE = `usage:
  ctl.ts status                       what runs, what waits, what finished, what was decided
  ctl.ts answer <id> [--why <reason>] <label>...   one label per question of a needs_answer event
  ctl.ts reply  <id> [--why <reason>] <text>       the free-text reply to a needs_reply event
  ctl.ts stop                         finish after the current iteration, then exit
env: AUTOPILOT_RUN_DIR overrides ${DEFAULT_RUN_DIR}`;

async function status(runDir: RunDir): Promise<string> {
  const pid = await runDir.lockHolder();
  const summary = summarizeJournal(await runDir.readEvents());
  return describeSummary(summary, pid, pid !== null && isProcessAlive(pid));
}

/** Splits `--why <reason>` off the front of an answer's arguments; the reason goes in the journal. */
export function splitWhy(args: string[]): { values: string[]; why?: string } {
  if (args[0] !== "--why") return { values: args };
  return { why: args[1], values: args.slice(2) };
}

async function run(argv: string[]): Promise<string> {
  const runDir = new RunDir(
    resolve(process.env["AUTOPILOT_RUN_DIR"] ?? DEFAULT_RUN_DIR),
  );
  const [command, id, ...rest] = argv;
  if (command === "status") return status(runDir);
  if (command === "stop") {
    await runDir.requestStop();
    return "stop requested";
  }
  const { values, why } = splitWhy(rest);
  if (command === "answer" && id && values.length > 0) {
    await runDir.writeAnswer(id, values, why);
    return `answered ${id}`;
  }
  if (command === "reply" && id && values.length > 0) {
    await runDir.writeAnswer(id, values.join(" "), why);
    return `replied ${id}`;
  }
  throw new Error(
    `unknown or incomplete command "${argv.join(" ")}"\n${USAGE}`,
  );
}

if (isEntryPoint(import.meta.url)) {
  run(process.argv.slice(2)).then(
    (message) => console.log(message),
    (error: unknown) => {
      console.error(error instanceof Error ? error.message : String(error));
      process.exit(1);
    },
  );
}
