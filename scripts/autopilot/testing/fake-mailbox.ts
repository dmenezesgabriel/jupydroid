import type { TaskOutputReader } from "../background-exit-watcher.ts";
import type { Clock } from "../clock.ts";
import type { AnswerFile, LoopEvent, MailboxFiles } from "../mailbox.ts";

/** In-memory {@link MailboxFiles}: `events` is the log, `answers` plays the orchestrator's answer files. */
export class FakeMailboxFiles implements MailboxFiles {
  readonly events: LoopEvent[] = [];
  readonly answers = new Map<string, AnswerFile>();

  async appendEvent(event: LoopEvent): Promise<void> {
    this.events.push(event);
  }

  async readAnswer(id: string): Promise<AnswerFile | null> {
    return this.answers.get(id) ?? null;
  }

  async clearAnswer(id: string): Promise<void> {
    this.answers.delete(id);
  }

  eventTypes(): string[] {
    return this.events.map((event) => event.type);
  }
}

/** A clock whose `sleep` advances time and runs `onSleep`, so a test can answer mid-wait. */
export class FakeClock implements Clock {
  private current = 0;
  readonly sleeps: number[] = [];
  private readonly tickers = new Set<() => void>();

  constructor(private readonly onSleep: (nowMs: number) => void = () => {}) {}

  now(): number {
    return this.current;
  }

  /** Sleeps longer than this never wake, standing in for a wait only an event ends. */
  holdSleepsLongerThanMs = Number.POSITIVE_INFINITY;

  async sleep(ms: number): Promise<void> {
    this.sleeps.push(ms);
    if (ms > this.holdSleepsLongerThanMs) return new Promise<void>(() => {});
    this.advance(ms);
  }

  /** Registers a ticker; a test fires it with `tickAll`. */
  every(_ms: number, tick: () => void): () => void {
    this.tickers.add(tick);
    return () => this.tickers.delete(tick);
  }

  advance(ms: number): void {
    this.current += ms;
    this.onSleep(this.current);
  }

  tickAll(): void {
    for (const tick of [...this.tickers]) tick();
  }
}

/** In-memory {@link TaskOutputReader}: `files` plays the task output files, `reads` counts the polls. */
export class FakeTaskOutputs implements TaskOutputReader {
  readonly files = new Map<string, string>();
  reads = 0;
  /** When set, every read fails with it, as an unreadable file would. */
  failWith: Error | null = null;

  async read(path: string): Promise<string | null> {
    this.reads += 1;
    if (this.failWith) throw this.failWith;
    return this.files.get(path) ?? null;
  }
}
