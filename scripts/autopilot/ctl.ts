import { resolve } from "node:path";
import { describeChoice, type AgentChoice } from "./agent-choice.ts";
import { profileFor } from "./agents/index.ts";
import { requiredValue } from "./flags.ts";
import { describeSummary, summarizeJournal } from "./journal-summary.ts";
import { DEFAULT_RUN_DIR, RunDir, isProcessAlive } from "./run-dir.ts";
import { isEntryPoint } from "./entry-point.ts";

const USAGE = `usage:
  ctl.ts status                       what runs, what waits, what finished, what was decided
  ctl.ts answer <id> [--why <reason>] <label>...   one label per question of a needs_answer event
  ctl.ts reply  <id> [--why <reason>] <text>       the free-text reply to a needs_reply event
  ctl.ts use <agent> [--model <id>] [--effort <value>]   run the next session on that agent (claude, devin);
                                      no --model or --effort means the agent's own default; a running
                                      session is never interrupted
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

const USE_FLAGS: Record<string, string> = {
  "--model": "a model id the agent offers",
  "--effort": "an effort the agent offers",
};

/**
 * The choice `ctl use` records. The agent is checked now, against the ones the loop can drive; the
 * model and effort can only be checked by the next session, which is the one place the agent lists them.
 *
 * @example
 * useArguments(["devin", "--model", "swe-2-high"]);
 * // => { agent: "devin", model: "swe-2-high", effort: null }
 */
export function useArguments(args: string[]): AgentChoice {
  const [name, ...flags] = args;
  const agent = profileFor(name).name;
  for (let at = 0; at < flags.length; at += 2) {
    const flag = flags[at] ?? "";
    if (!(flag in USE_FLAGS)) {
      throw new Error(`ctl use takes --model and --effort, got ${JSON.stringify(flag)}`);
    }
  }
  return {
    agent,
    model: requiredValue(flags, "--model", USE_FLAGS["--model"] ?? ""),
    effort: requiredValue(flags, "--effort", USE_FLAGS["--effort"] ?? ""),
  };
}

async function run(argv: string[]): Promise<string> {
  const runDir = new RunDir(
    resolve(process.env["AUTOPILOT_RUN_DIR"] ?? DEFAULT_RUN_DIR),
  );
  const [command, id, ...rest] = argv;
  if (command === "status") return status(runDir);
  if (command === "use") {
    const choice = useArguments(argv.slice(1));
    await runDir.writeChoice(choice);
    return `next session: ${describeChoice(choice)}`;
  }
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
