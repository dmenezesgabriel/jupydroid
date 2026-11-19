import type { ActivityLedger } from "./activity-ledger.ts";
import type { Clock } from "./clock.ts";
import {
  decide,
  PLAN_MODE,
  type Decision,
  type ReplyReason,
  type StopReason,
  type TurnEnd,
} from "./decide.ts";
import type { PromptBlock } from "./acp-agent.ts";
import { ChannelClosedError, RpcError } from "./json-rpc.ts";
import type { LoopEvent } from "./mailbox.ts";
import type { AgentError, RateLimitVerdict } from "./rate-limit.ts";

/** The live agent session an iteration drives; `AcpAgent` satisfies it after `newSession`. */
export interface AgentSession {
  setMode(mode: string): Promise<void>;
  prompt(blocks: PromptBlock[]): Promise<StopReason>;
  cancel(): void;
}

export interface GitProbe {
  head(): Promise<string>;
  isClean(): Promise<boolean>;
}

export interface IterationDeps {
  agent: AgentSession;
  ledger: ActivityLedger;
  git: GitProbe;
  clock: Clock;
  askReply(why: ReplyReason, lastMessage: string): Promise<string>;
  emit(event: LoopEvent): Promise<void>;
  openingPrompt: PromptBlock[];
  /** Command (for example a slash-command skill) the approved plan is sent under; null leaves plan approval to the agent's own exit. */
  planFollowUp: string | null;
  timing: IterationTiming;
  /** The mode an approved plan is carried out in, by the agent's own name for it. */
  bypassMode: string;
  /** A prompt error that is a usage limit, or null when it is some other failure. */
  classifyError(error: AgentError, now: Date): RateLimitVerdict | null;
}

export type IterationTiming = {
  /** Silence, in every frame, after which the orchestrator is told. */
  possiblyHungAfterMs: number;
  /** Silence after which the turn is cancelled, because nothing has ever been this quiet while working. */
  cancelAfterSilenceMs: number;
  /** How long a background command may outlive the turn that started it. */
  backgroundCeilingMs: number;
  limitPollMs: number;
  limitMarginMs: number;
  watchEveryMs: number;
};

export type IterationOutcome =
  | { kind: "done"; head: string }
  | { kind: "stalled"; reason: string }
  | { kind: "halted"; reason: string }
  | { kind: "crashed" };

const CONTINUE: PromptBlock[] = [{ type: "text", text: "continue" }];
// Worded as an instruction to act: "Plan:" as a label made the agent read the
// follow-up as another request to plan (measured on claude-agent-acp 0.91.0).
const PLAN_APPROVED_NOTICE =
  "The plan below is approved. Carry it out now, in this turn, and verify the result.";

/**
 * Drives one fresh session from the opening prompt to a verified commit. Every
 * step follows the agent's own signals through {@link decide}; the iteration
 * is done only when the agent stopped, nothing it started is still running,
 * it left plan mode, and git shows a new commit with a clean tree.
 */
export async function runIteration(
  deps: IterationDeps,
): Promise<IterationOutcome> {
  const { agent, ledger, git } = deps;
  await agent.setMode(PLAN_MODE);
  ledger.mode = PLAN_MODE;
  const headBefore = await git.head();
  let prompt = deps.openingPrompt;
  let replyRounds = 0;
  let errorRetries = 0;
  for (;;) {
    const end = await runTurn(deps, prompt);
    const head = await git.head();
    const decision = decide(end, {
      mode: ledger.mode,
      working: ledger.isWorking(),
      unreportedExit: ledger.hasUnreportedExit(),
      commitsMade: head !== headBefore,
      clean: await git.isClean(),
      replyRounds,
      errorRetries,
      limit: limitOf(deps, end),
      planDeclined: ledger.planDeclined,
    });
    await deps.emit({
      type: "turn_ended",
      end: describeEnd(end),
      decision: decision.do,
    });
    if (decision.do === "ask_reply") replyRounds += 1;
    errorRetries = decision.do === "retry" ? errorRetries + 1 : 0;
    const next = await act(deps, decision, head);
    if ("outcome" in next) return next.outcome;
    prompt = next.prompt;
  }
}

type Next = { prompt: PromptBlock[] } | { outcome: IterationOutcome };

/** The usage limit a turn hit: the ledger's rejected reading, or the limit its failure error names. */
function limitOf(deps: IterationDeps, end: TurnEnd): RateLimitVerdict | null {
  const reading = deps.ledger.rateLimitVerdict();
  if (reading || end.kind !== "failed") return reading;
  return deps.classifyError(end.error, new Date(deps.clock.now()));
}

async function act(
  deps: IterationDeps,
  decision: Decision,
  head: string,
): Promise<Next> {
  switch (decision.do) {
    case "finish":
      return { outcome: { kind: "done", head } };
    case "stall":
      return {
        outcome: {
          kind: "stalled",
          reason: "the agent kept stopping without a commit",
        },
      };
    case "halt":
      return { outcome: { kind: "halted", reason: decision.reason } };
    case "approve_plan":
      return approvePlan(deps);
    case "crash":
      return { outcome: { kind: "crashed" } };
    case "await_work":
      return awaitWork(deps);
    case "ask_reply":
      return textPrompt(
        await deps.askReply(decision.why, deps.ledger.lastMessage()),
      );
    case "continue":
      return { prompt: CONTINUE };
    case "wait_until":
      return waitForLimit(
        deps,
        decision.wakeAt.getTime() + deps.timing.limitMarginMs,
      );
    case "poll_limit":
      return waitForLimit(deps, deps.clock.now() + deps.timing.limitPollMs);
    case "retry":
      await deps.clock.sleep(decision.backoffMs);
      return { prompt: CONTINUE };
  }
}

/**
 * The loop declined the plan exit and the turn ended before any work: leave
 * plan mode ourselves and hand the agent its plan under the follow-up command.
 * A refused mode switch halts with the agent's own reason; the plan is not
 * sent into a session that is still read-only.
 */
async function approvePlan(deps: IterationDeps): Promise<Next> {
  const { ledger } = deps;
  const command = deps.planFollowUp ?? "";
  const plan = ledger.proposedPlan ?? "";
  try {
    await deps.agent.setMode(deps.bypassMode);
  } catch (error) {
    if (error instanceof RpcError) {
      const reason = `plan approval failed: ${error.message}`;
      return { outcome: { kind: "halted", reason } };
    }
    if (error instanceof ChannelClosedError) {
      return { outcome: { kind: "crashed" } };
    }
    throw error;
  }
  ledger.mode = deps.bypassMode;
  ledger.proposedPlan = null;
  ledger.planDeclined = false;
  await deps.emit({ type: "plan_approved", via: command });
  return textPrompt(`${command}\n\n${PLAN_APPROVED_NOTICE}\n\n${plan}`);
}

async function waitForLimit(
  deps: IterationDeps,
  wakeAtMs: number,
): Promise<Next> {
  await deps.emit({
    type: "rate_limited",
    wakeAt: new Date(wakeAtMs).toISOString(),
  });
  await deps.clock.sleep(Math.max(0, wakeAtMs - deps.clock.now()));
  return { prompt: CONTINUE };
}

/** The turn ended while work it started still runs: wait for that work, then tell the agent how it ended. */
async function awaitWork(deps: IterationDeps): Promise<Next> {
  const { ledger, clock, timing } = deps;
  await deps.emit({
    type: "awaiting_work",
    running: ledger.runningCommands(),
    // True when nothing runs and the wait is only to report an exit the agent has not heard.
    exitUnreported: ledger.hasUnreportedExit(),
  });
  const settled = await Promise.race([
    ledger.whenIdle().then(() => true),
    clock.sleep(timing.backgroundCeilingMs).then(() => false),
  ]);
  if (!settled) {
    await deps.emit({
      type: "possibly_hung",
      running: ledger.runningCommands(),
    });
    return {
      outcome: {
        kind: "halted",
        reason: "background work outlived its ceiling",
      },
    };
  }
  const finished = ledger.drainFinishedShells();
  if (finished.length === 0) return { prompt: CONTINUE };
  const report = finished
    .map(
      (shell) =>
        `The background command \`${shell.command}\` ${describeShellEnd(shell.exitCode)}.`,
    )
    .join("\n");
  return textPrompt(`${report}\nContinue from there.`);
}

/** A null code is a task that was killed: no exit code exists, so none is claimed. */
function describeShellEnd(exitCode: number | null): string {
  return exitCode === null
    ? "was killed before it reported an exit code"
    : `exited with code ${exitCode}`;
}

function textPrompt(text: string): Next {
  return { prompt: [{ type: "text", text }] };
}

async function runTurn(
  deps: IterationDeps,
  blocks: PromptBlock[],
): Promise<TurnEnd> {
  deps.ledger.startTurn();
  // The silence clock starts with the turn, not with the last frame of an earlier one.
  deps.ledger.lastFrameAtMs = deps.clock.now();
  const watchSilence = silenceWatcher(deps);
  const stopWatching = deps.clock.every(deps.timing.watchEveryMs, watchSilence);
  try {
    return { kind: "stopped", reason: await deps.agent.prompt(blocks) };
  } catch (error) {
    if (error instanceof RpcError) {
      return {
        kind: "failed",
        error: { message: error.message, data: error.data },
      };
    }
    if (error instanceof ChannelClosedError) return { kind: "crashed" };
    throw error;
  } finally {
    stopWatching();
  }
}

/**
 * Reports total silence to the orchestrator once per silent stretch, and
 * cancels only after silence nothing legitimate produces: Claude Code bounds a
 * foreground command with its Bash timeout and moves longer work to a
 * background task, so a working agent keeps producing frames.
 */
function silenceWatcher(deps: IterationDeps): () => void {
  const { ledger, clock, timing } = deps;
  let reportedFor = -1;
  return () => {
    const silentMs = clock.now() - ledger.lastFrameAtMs;
    if (silentMs >= timing.cancelAfterSilenceMs) {
      void deps.emit({ type: "hung_cancelled", silentMs });
      deps.agent.cancel();
    } else if (
      silentMs >= timing.possiblyHungAfterMs &&
      reportedFor !== ledger.lastFrameAtMs
    ) {
      reportedFor = ledger.lastFrameAtMs;
      void deps.emit({
        type: "possibly_hung",
        silentMs,
        running: ledger.runningCommands(),
      });
    }
  };
}

function describeEnd(end: TurnEnd): string {
  if (end.kind === "stopped") return end.reason;
  return end.kind === "failed" ? `error: ${end.error.message}` : "crashed";
}
