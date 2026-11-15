import { readFile } from "node:fs/promises";
import { basename, join, resolve } from "node:path";
import { ActivityLedger } from "./activity-ledger.ts";
import { systemClock } from "./clock.ts";
import { ClaudeAgent, type PromptBlock } from "./claude-agent.ts";
import { GitCli } from "./git-probe.ts";
import { buildHandlers } from "./handlers.ts";
import {
  runIteration,
  type IterationOutcome,
  type IterationTiming,
} from "./iteration.ts";
import { RpcError } from "./json-rpc.ts";
import { DirectoryMailboxFiles, Mailbox, type LoopEvent } from "./mailbox.ts";
import {
  nextBacklogRow,
  readHostReadings,
  readProcEntries,
  waitUntilReady,
  type PreflightDeps,
} from "./preflight.ts";
import { ProgressTracker } from "./progress.ts";
import { DEFAULT_RUN_DIR, RunDir, isProcessAlive } from "./run-dir.ts";
import { summarizeJournal } from "./journal-summary.ts";
import {
  DEFAULT_AGENT_COMMAND,
  spawnClaudeAcp,
  type SpawnedAgent,
} from "./spawn-agent.ts";
import { isEntryPoint } from "./entry-point.ts";
import {
  BackgroundExitWatcher,
  fileTaskOutputs,
} from "./background-exit-watcher.ts";

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;

// Claude Code bounds a foreground command with its Bash timeout and moves
// longer work to a background task, so an hour of total silence is not a test
// suite, and three hours is not a working agent.
const TIMING: IterationTiming = {
  possiblyHungAfterMs: 45 * MINUTE,
  cancelAfterSilenceMs: 3 * HOUR,
  backgroundCeilingMs: 3 * HOUR,
  limitPollMs: 30 * MINUTE,
  limitMarginMs: MINUTE,
  watchEveryMs: MINUTE,
};
const BACKGROUND_POLL_MS = 10_000;

type LoopContext = {
  repoRoot: string;
  promptFile: string;
  agentCommand: string;
  runDir: RunDir;
  git: GitCli;
  mailbox: Mailbox;
  emit(event: LoopEvent): Promise<void>;
};

/**
 * Flags: `--repo <dir>` (default: cwd), `--run-dir <dir>`, `--prompt <file>`
 * (default: prompt-execution.md in the repo), `--agent-command <path>`
 * (default: `claude-agent-acp` from PATH).
 */
export function parseArguments(argv: string[], cwd: string) {
  const flag = (name: string): string | undefined => {
    const at = argv.indexOf(name);
    return at === -1 ? undefined : argv[at + 1];
  };
  const repoRoot = resolve(flag("--repo") ?? cwd);
  return {
    repoRoot,
    runDir: resolve(flag("--run-dir") ?? join(repoRoot, DEFAULT_RUN_DIR)),
    promptFile: resolve(repoRoot, flag("--prompt") ?? "prompt-execution.md"),
    agentCommand: flag("--agent-command") ?? DEFAULT_AGENT_COMMAND,
  };
}

/**
 * Whenever a task, a PRD or the backlog asks for a person to run a demo, the loop's agent runs it
 * itself and says so (ADR-0150). The wording is a rule because an agent left to guess either stops to
 * ask a person who is not there, or calls a green log a demo; the statement that no human ran it is
 * required so a reader of the commit is never led to think one did.
 */
export const DEMO_RULE = [
  "Standing rule for demos: whenever these instructions, a PRD or the backlog ask for a person to run a demo, run it yourself, as a person would: drive the real app with adb and Maestro on the device AGENTS.md rule 2 names (anything that runs a cell goes on the arm64 tablet), through real input along the item's Demo steps.",
  "Take a screenshot and a UI dump at every step that changes the screen, and look at the screenshots and analyze them, not only the dumps and logs: a dump describes the tree, only the image shows what a person sees.",
  "A demo run this way counts as run, but it is run by the agent, not by a human, and that must be explicit: in the commit body write `Demo run by the agent, not by a human`, then what you saw at each step and every difference from what the item expects. Do not stop to ask a person to run it.",
].join("\n");

/**
 * Three sessions ended their turn to wait for the host's load to fall, with a task unfinished and
 * work staged, and each had to be sent back to work. The loop reads a stop with no commit as a
 * missing commit, and `devjob` already holds a job until the host has room, so the wait belongs
 * to the queue and not to the session.
 */
export const WAIT_RULE =
  "Never end your turn to wait for the host's load to fall or for a queued job: devjob holds a job until the host has room, so start it as a background command and carry on from its result, because a turn that ends with unfinished work is read as a task that stopped without its commit. This does not relax a gate: never bypass the host queue and never skip a hook.";

/**
 * The skills under .claude/skills/ are announced to every session but a session that is not told
 * to use one does not (ADR-0149), so every session is told here, once, in its opening prompt.
 * AGENTS.md is ranked above them because `tdd` allows "no new test" where AGENTS.md allows none.
 */
export const STANDING_RULES = [
  "Standing rule for this run: before you change code, invoke the engineering-mode skill and follow it, loading the principle and find-* skills it routes to only where their situation fits.",
  "Where a skill disagrees with AGENTS.md, AGENTS.md wins: test-first work and the commit gate are never relaxed.",
  "Name in the commit body each principle that changed a decision.",
  DEMO_RULE,
  WAIT_RULE,
].join("\n");

export async function openingPrompt(
  promptFile: string,
): Promise<PromptBlock[]> {
  const text = await readFile(promptFile, "utf8");
  return [
    {
      type: "resource",
      resource: {
        uri: `file://${promptFile}`,
        mimeType: "text/markdown",
        text,
      },
    },
    { type: "text", text: `Follow ${basename(promptFile)} (attached).` },
    { type: "text", text: STANDING_RULES },
  ];
}

type Session = {
  spawned: SpawnedAgent;
  ledger: ActivityLedger;
  watcher: BackgroundExitWatcher;
  agent: ClaudeAgent;
};

/**
 * Starts the adapter and wires its callbacks. Claude reports no exit for a
 * background command, so the watcher reads the command's output file and
 * feeds the exit back through the same handler a frame would have used.
 */
function openSession(context: LoopContext): Session {
  const stamp = new Date().toISOString().replaceAll(":", "-");
  const spawned = spawnClaudeAcp(
    context.repoRoot,
    join(context.runDir.path, `acp-${stamp}.jsonl`),
    context.agentCommand,
  );
  const ledger = new ActivityLedger();
  const handlers = buildHandlers({
    ledger,
    clock: systemClock,
    emit: context.emit,
    askQuestions: (questions) => context.mailbox.askQuestions(questions),
    watchBackground: (shellId, outputPath) =>
      watcher.watch(shellId, outputPath),
  });
  const watcher = new BackgroundExitWatcher(
    systemClock,
    fileTaskOutputs,
    (shellId, exitCode) =>
      handlers.onSignal({ type: "background_exited", shellId, exitCode }),
    (shellId, error) =>
      void context.emit({
        type: "background_watch_error",
        shellId,
        error: error.message,
      }),
    BACKGROUND_POLL_MS,
  );
  const agent = new ClaudeAgent(spawned.channel, handlers);
  return { spawned, ledger, watcher, agent };
}

async function driveSession(
  context: LoopContext,
  { agent, ledger }: Session,
): Promise<IterationOutcome> {
  await agent.initialize();
  const sessionId = await agent.newSession(context.repoRoot);
  await context.emit({ type: "session_started", sessionId });
  return runIteration({
    agent,
    ledger,
    git: context.git,
    clock: systemClock,
    askReply: (why, last) => context.mailbox.askReply(why, last),
    emit: context.emit,
    openingPrompt: await openingPrompt(context.promptFile),
    timing: TIMING,
  });
}

async function runSession(context: LoopContext): Promise<IterationOutcome> {
  const session = openSession(context);
  try {
    return await driveSession(context, session);
  } catch (error) {
    if (error instanceof RpcError) {
      return {
        kind: "halted",
        reason: `session setup failed: ${error.message}`,
      };
    }
    throw error;
  } finally {
    session.watcher.stopAll();
    await session.spawned.stop();
  }
}

function preflightDeps(context: LoopContext): PreflightDeps {
  return {
    repoRoot: context.repoRoot,
    readBacklog: () =>
      readFile(join(context.repoRoot, "docs/backlog.md"), "utf8"),
    readHost: readHostReadings,
    readProcs: readProcEntries,
    stopRequested: () => context.runDir.stopRequested(),
    clock: systemClock,
    emit: context.emit,
    recheckMs: MINUTE,
    repeatReportMs: 10 * MINUTE,
  };
}

async function runLoop(context: LoopContext): Promise<number> {
  const tracker = new ProgressTracker();
  for (;;) {
    const ready = await waitUntilReady(preflightDeps(context));
    if (ready !== "ready") {
      await context.emit({ type: ready });
      return 0;
    }
    const row = nextBacklogRow(
      await readFile(join(context.repoRoot, "docs/backlog.md"), "utf8"),
    );
    await context.emit({
      type: "iteration_started",
      row,
      head: await context.git.head(),
    });
    const outcome = await runSession(context);
    await context.emit({ type: `iteration_${outcome.kind}`, ...outcome });
    const verdict = tracker.record(outcome);
    if (!verdict.keepGoing) {
      await context.emit({ type: "loop_halted", reason: verdict.reason });
      return 1;
    }
  }
}

/** A previous loop died mid-iteration: say so in the journal, so the unfinished work is not forgotten. */
async function recordInterrupted(context: LoopContext): Promise<void> {
  const { open } = summarizeJournal(await context.runDir.readEvents());
  if (!open) return;
  await context.emit({
    type: "iteration_interrupted",
    n: open.n,
    row: open.row,
    sessionId: open.sessionId,
    pending: open.pending.map((ask) => ask.id),
    running: open.running,
  });
}

async function main(): Promise<number> {
  const {
    repoRoot,
    runDir: runDirPath,
    promptFile,
    agentCommand,
  } = parseArguments(process.argv.slice(2), process.cwd());
  const runDir = new RunDir(runDirPath);
  await runDir.acquireLock(process.pid, isProcessAlive);
  const files = new DirectoryMailboxFiles(runDirPath, () =>
    new Date().toISOString(),
  );
  const context: LoopContext = {
    repoRoot,
    promptFile,
    agentCommand,
    runDir,
    git: new GitCli(repoRoot),
    mailbox: new Mailbox(files, systemClock),
    emit: (event) => files.appendEvent(event),
  };
  try {
    await context.emit({ type: "loop_started", repoRoot, pid: process.pid });
    await recordInterrupted(context);
    return await runLoop(context);
  } finally {
    await runDir.releaseLock();
  }
}

if (isEntryPoint(import.meta.url)) {
  main().then(
    (code) => process.exit(code),
    (error: unknown) => {
      console.error(
        JSON.stringify({ type: "loop_crashed", error: String(error) }),
      );
      process.exit(2);
    },
  );
}
