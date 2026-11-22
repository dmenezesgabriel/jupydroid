import type { IterationOutcome } from "./iteration.ts";

export type LoopVerdict =
  | { keepGoing: true }
  | { keepGoing: false; reason: string };

export const MAX_STALLED_ITERATIONS = 2;
export const MAX_CRASHES_IN_A_ROW = 3;

/**
 * The circuit breaker for a loop meant to run all night: it ends the run
 * when iterations stop producing commits, so a stuck row does not burn the
 * quota until morning. A finished iteration resets it.
 */
export class ProgressTracker {
  private stalls = 0;
  private crashes = 0;

  record(outcome: IterationOutcome): LoopVerdict {
    if (outcome.kind === "done") {
      this.stalls = 0;
      this.crashes = 0;
      return { keepGoing: true };
    }
    if (outcome.kind === "halted") {
      return { keepGoing: false, reason: `halted: ${outcome.reason}` };
    }
    if (outcome.kind === "crashed") return this.afterCrash();
    return this.afterStall(outcome.reason);
  }

  private afterCrash(): LoopVerdict {
    this.crashes += 1;
    if (this.crashes < MAX_CRASHES_IN_A_ROW) return { keepGoing: true };
    return {
      keepGoing: false,
      reason: `the agent crashed ${this.crashes} times in a row`,
    };
  }

  private afterStall(reason: string): LoopVerdict {
    this.stalls += 1;
    if (this.stalls < MAX_STALLED_ITERATIONS) return { keepGoing: true };
    return {
      keepGoing: false,
      reason: `no progress in ${this.stalls} iterations: ${reason}`,
    };
  }
}
