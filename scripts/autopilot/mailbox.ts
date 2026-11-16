import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { Clock } from "./clock.ts";
import type { Question } from "./claude-agent.ts";
import type { ReplyReason } from "./decide.ts";

export type LoopEvent = { type: string; [detail: string]: unknown };

/** One label per question, or free text for a reply. */
export type Answer = string[] | string;
/** What the orchestrator writes: the answer and, for the record, why it chose it. */
export type AnswerFile = { answer: Answer; why?: string };

/** Where the loop and the orchestrator meet: an append-only event log and answer files. */
export interface MailboxFiles {
  appendEvent(event: LoopEvent): Promise<void>;
  readAnswer(id: string): Promise<AnswerFile | null>;
  clearAnswer(id: string): Promise<void>;
}

export type MailboxTiming = {
  pollMs: number;
  overdueAfterMs: number;
  defaultAfterMs: number;
};

export const DEFAULT_TIMING: MailboxTiming = {
  pollMs: 5_000,
  overdueAfterMs: 10 * 60_000,
  defaultAfterMs: 60 * 60_000,
};

const DEFAULT_REPLIES: Record<ReplyReason, string> = {
  plan_not_exited:
    "Continue. Ask decisions with ask_user_question, and call exit_plan_mode with your plan when it is ready.",
  no_commit:
    "Continue the task. When it is finished, commit it as the instructions say, then stop.",
  dirty_tree:
    "The working tree is not clean. Commit what belongs to the task or explain what is left, then stop.",
};

/**
 * Asks the orchestrator and waits for the answer file, without ever waiting
 * forever: it reports the question, flags it overdue, and after the deadline
 * answers with a default and records that it did.
 */
export class Mailbox {
  private nextId = 1;

  constructor(
    private readonly files: MailboxFiles,
    private readonly clock: Clock,
    private readonly timing: MailboxTiming = DEFAULT_TIMING,
  ) {}

  /** One answer per question: the orchestrator's, or the Recommended (else first) option when it stays silent. */
  async askQuestions(questions: Question[]): Promise<string[]> {
    const id = `q${this.nextId++}`;
    await this.files.appendEvent({ type: "needs_answer", id, questions });
    const given = await this.awaitAnswer(id);
    if (Array.isArray(given?.answer)) {
      await this.recordAnswer("answer_received", id, given);
      return given.answer;
    }
    await this.files.appendEvent({ type: "answer_defaulted", id });
    return questions.map(defaultAnswerFor);
  }

  /** A free-text reply for a stop the loop cannot read as done: the orchestrator's, or a fixed nudge. */
  async askReply(why: ReplyReason, lastMessage: string): Promise<string> {
    const id = `r${this.nextId++}`;
    await this.files.appendEvent({ type: "needs_reply", id, why, lastMessage });
    const given = await this.awaitAnswer(id);
    if (typeof given?.answer === "string") {
      await this.recordAnswer("reply_received", id, given);
      return given.answer;
    }
    await this.files.appendEvent({ type: "reply_defaulted", id, why });
    return DEFAULT_REPLIES[why];
  }

  /** Every decision stays in the journal with the reason the orchestrator gave. */
  private async recordAnswer(
    type: "answer_received" | "reply_received",
    id: string,
    given: AnswerFile,
  ): Promise<void> {
    await this.files.appendEvent({
      type,
      id,
      answer: given.answer,
      why: given.why ?? null,
    });
  }

  private async awaitAnswer(id: string): Promise<AnswerFile | null> {
    const startedAt = this.clock.now();
    let overdueReported = false;
    for (;;) {
      const answer = await this.files.readAnswer(id);
      if (answer !== null) {
        await this.files.clearAnswer(id);
        return answer;
      }
      const waited = this.clock.now() - startedAt;
      if (waited >= this.timing.defaultAfterMs) return null;
      if (!overdueReported && waited >= this.timing.overdueAfterMs) {
        overdueReported = true;
        await this.files.appendEvent({
          type: "needs_answer_overdue",
          id,
          waitedMs: waited,
        });
      }
      await this.clock.sleep(this.timing.pollMs);
    }
  }
}

function defaultAnswerFor(question: Question): string {
  const recommended = question.options.find((option) =>
    /\(recommended\)/i.test(option.label),
  );
  return (recommended ?? question.options[0])?.label ?? "";
}

/** The production files: `<dir>/events.jsonl` and `<dir>/answers/<id>.json`. */
export class DirectoryMailboxFiles implements MailboxFiles {
  constructor(
    private readonly dir: string,
    private readonly stamp: () => string,
  ) {}

  async appendEvent(event: LoopEvent): Promise<void> {
    await mkdir(this.dir, { recursive: true });
    const line = JSON.stringify({ at: this.stamp(), ...event });
    await writeFile(join(this.dir, "events.jsonl"), `${line}\n`, { flag: "a" });
  }

  async readAnswer(id: string): Promise<AnswerFile | null> {
    try {
      const text = await readFile(
        join(this.dir, "answers", `${id}.json`),
        "utf8",
      );
      return JSON.parse(text) as AnswerFile;
    } catch {
      return null;
    }
  }

  async clearAnswer(id: string): Promise<void> {
    await rm(join(this.dir, "answers", `${id}.json`), { force: true });
  }
}
