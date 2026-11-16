import { classifyRateLimit, type RateLimitVerdict } from "./rate-limit.ts";
import type { AgentSignal } from "./agent-signal.ts";

export type FinishedShell = { command: string; exitCode: number | null };
type RateLimitSignal = Extract<AgentSignal, { type: "rate_limit" }>;

/**
 * What the agent has running that outlives its turn, rebuilt from its
 * signals. The loop asks this, never a clock, whether work is still running: a
 * test suite that is silent for ten minutes still has its background shell.
 * Foreground tool calls are not tracked: the agent waits for them before it
 * ends a turn, and a tool blocked by a hook arrives already failed and never
 * reports again, so counting open tool calls would wait for work that is gone.
 *
 * @example
 * ledger.apply({ type: "background_started", shellId: "b1", command: "./gradlew testDebugUnitTest", outputPath: "/x/b1.output" });
 * ledger.isWorking(); // true until the matching background_exited arrives
 */
export class ActivityLedger {
  mode = "";
  proposedPlan: string | null = null;
  /** Set by the loop when it declines the plan exit itself, so the cancelled stop that follows reads as an approval. */
  planDeclined = false;
  /** When the agent last sent any frame at all, in clock milliseconds. */
  lastFrameAtMs = 0;
  private rateLimit: RateLimitSignal | null = null;
  private readonly runningShells = new Map<string, string>();
  private finishedShells: FinishedShell[] = [];
  private messageText = "";
  private idleWaiters: Array<() => void> = [];

  /**
   * Folds one signal into the ledger. Returns whether it told us something new
   * about modes or background shells, the facts the journal records: the
   * adapter repeats a mode change in several frames.
   */
  apply(signal: AgentSignal): boolean {
    const news = this.isNews(signal);
    this.fold(signal);
    if (!this.isWorking()) this.releaseIdleWaiters();
    return news;
  }

  private isNews(signal: AgentSignal): boolean {
    if (signal.type === "mode_changed") return signal.mode !== this.mode;
    if (signal.type === "background_started") {
      return !this.runningShells.has(signal.shellId);
    }
    if (signal.type === "background_exited") {
      return this.runningShells.has(signal.shellId);
    }
    return false;
  }

  private fold(signal: AgentSignal): void {
    switch (signal.type) {
      case "background_started":
        this.runningShells.set(signal.shellId, signal.command);
        break;
      case "background_exited":
        this.finishShell(signal.shellId, signal.exitCode);
        break;
      case "agent_message":
        this.messageText += signal.text;
        break;
      case "mode_changed":
        this.mode = signal.mode;
        break;
      case "rate_limit":
        this.rateLimit = signal.status === "rejected" ? signal : null;
        break;
      case "plan_proposed":
        this.proposedPlan = signal.plan;
        break;
      default:
        break;
    }
  }

  isWorking(): boolean {
    return this.runningShells.size > 0;
  }

  /** Resolves as soon as no background shell is running. */
  whenIdle(): Promise<void> {
    if (!this.isWorking()) return Promise.resolve();
    return new Promise((resolve) => this.idleWaiters.push(resolve));
  }

  runningCommands(): string[] {
    return [...this.runningShells.values()];
  }

  /**
   * Whether a command exited that the agent has not been told about. The loop
   * reads a stop with nothing running and nothing committed as an idle agent;
   * with this true the agent is more likely waiting for that exit, which can
   * land milliseconds before the stop.
   */
  hasUnreportedExit(): boolean {
    return this.finishedShells.length > 0;
  }

  /** Background commands that exited since the last call, each reported once. */
  drainFinishedShells(): FinishedShell[] {
    const finished = this.finishedShells;
    this.finishedShells = [];
    return finished;
  }

  /** The agent's words since the last prompt: the orchestrator reads them to judge a stop. */
  lastMessage(): string {
    return this.messageText.trim();
  }

  /** What a rejected rate-limit reading in this turn asks the loop to do, or null. */
  rateLimitVerdict(): RateLimitVerdict | null {
    return this.rateLimit ? classifyRateLimit(this.rateLimit) : null;
  }

  startTurn(): void {
    this.messageText = "";
    this.rateLimit = null;
  }

  private finishShell(shellId: string, exitCode: number | null): void {
    const command = this.runningShells.get(shellId);
    if (command === undefined) return;
    this.runningShells.delete(shellId);
    this.finishedShells.push({ command, exitCode });
  }

  private releaseIdleWaiters(): void {
    const waiters = this.idleWaiters;
    this.idleWaiters = [];
    for (const release of waiters) release();
  }
}
