export type JournalEvent = {
  at: string;
  type: string;
  [detail: string]: unknown;
};

export type PendingAsk = {
  id: string;
  kind: "question" | "reply";
  since: string;
  overdue: boolean;
};
export type Decision = {
  at: string;
  id: string;
  how: "answered" | "defaulted";
  answer: unknown;
  why: string | null;
};
export type FinishedIteration = {
  n: number;
  row: string;
  outcome: string;
  detail: string;
  startedAt: string;
  endedAt: string;
};
export type OpenIteration = {
  n: number;
  row: string;
  startedAt: string;
  sessionId: string | null;
  mode: string | null;
  running: string[];
  pending: PendingAsk[];
  rateLimitedUntil: string | null;
};
export type LoopStatus =
  | "never_started"
  | "running"
  | "halted"
  | "exhausted"
  | "stopped";
export type JournalSummary = {
  loop: LoopStatus;
  haltReason: string | null;
  open: OpenIteration | null;
  finished: FinishedIteration[];
  decisions: Decision[];
};

const OUTCOMES: Record<string, string> = {
  iteration_done: "done",
  iteration_stalled: "stalled",
  iteration_halted: "halted",
  iteration_crashed: "crashed",
  iteration_interrupted: "interrupted",
};

/**
 * Rebuilds what the loop is doing, what it finished and what was decided from
 * the append-only event journal alone, so a restarted loop or orchestrator
 * loses nothing: the journal is the only state.
 *
 * @example
 * summarizeJournal(events).open?.pending // questions still waiting for an answer
 */
export function summarizeJournal(events: JournalEvent[]): JournalSummary {
  const summary: JournalSummary = {
    loop: "never_started",
    haltReason: null,
    open: null,
    finished: [],
    decisions: [],
  };
  let started = 0;
  for (const event of events) {
    if (event.type === "iteration_started") started += 1;
    applyLoopEvent(summary, event);
    applyIterationEvent(summary, event, started);
    applyAskEvent(summary, event);
  }
  return summary;
}

function applyLoopEvent(summary: JournalSummary, event: JournalEvent): void {
  if (event.type === "loop_started") summary.loop = "running";
  if (event.type === "loop_halted") {
    summary.loop = "halted";
    summary.haltReason = String(event["reason"]);
  }
  if (event.type === "backlog_exhausted") summary.loop = "exhausted";
  if (event.type === "stop") summary.loop = "stopped";
}

function applyIterationEvent(
  summary: JournalSummary,
  event: JournalEvent,
  n: number,
): void {
  if (event.type === "iteration_started") {
    summary.open = {
      n,
      row: String(event["row"] ?? ""),
      startedAt: event.at,
      sessionId: null,
      mode: null,
      running: [],
      pending: [],
      rateLimitedUntil: null,
    };
    return;
  }
  const open = summary.open;
  if (!open) return;
  if (event.type in OUTCOMES) return finishIteration(summary, open, event);
  trackSession(open, event);
}

function finishIteration(
  summary: JournalSummary,
  open: OpenIteration,
  event: JournalEvent,
): void {
  summary.finished.push({
    n: open.n,
    row: open.row,
    outcome: OUTCOMES[event.type] ?? event.type,
    detail: String(event["head"] ?? event["reason"] ?? ""),
    startedAt: open.startedAt,
    endedAt: event.at,
  });
  summary.open = null;
}

function trackSession(open: OpenIteration, event: JournalEvent): void {
  if (event.type === "session_started")
    open.sessionId = String(event["sessionId"]);
  if (event.type === "mode_changed") open.mode = String(event["mode"]);
  if (event.type === "rate_limited")
    open.rateLimitedUntil = String(event["wakeAt"]);
  if (event.type === "turn_ended") open.rateLimitedUntil = null;
  if (event.type === "background_started") open.running.push(commandKey(event));
  if (event.type === "background_exited") {
    open.running = open.running.filter(
      (entry) => !entry.startsWith(`${String(event["shellId"])}:`),
    );
  }
}

function commandKey(event: JournalEvent): string {
  return `${String(event["shellId"])}: ${String(event["command"])}`;
}

function applyAskEvent(summary: JournalSummary, event: JournalEvent): void {
  const open = summary.open;
  const id = String(event["id"] ?? "");
  if (open && (event.type === "needs_answer" || event.type === "needs_reply")) {
    const kind = event.type === "needs_answer" ? "question" : "reply";
    open.pending.push({ id, kind, since: event.at, overdue: false });
  }
  if (open && event.type === "needs_answer_overdue") {
    for (const ask of open.pending) if (ask.id === id) ask.overdue = true;
  }
  const how = DECISION_EVENTS[event.type];
  if (!how) return;
  if (open) open.pending = open.pending.filter((ask) => ask.id !== id);
  summary.decisions.push({
    at: event.at,
    id,
    how,
    answer: event["answer"] ?? null,
    why: typeof event["why"] === "string" ? event["why"] : null,
  });
}

const DECISION_EVENTS: Record<string, Decision["how"] | undefined> = {
  answer_received: "answered",
  reply_received: "answered",
  answer_defaulted: "defaulted",
  reply_defaulted: "defaulted",
};

/** Plain-text report for `ctl status`; `pidAlive` says whether the process that wrote the journal still runs. */
export function describeSummary(
  summary: JournalSummary,
  pid: number | null,
  pidAlive: boolean,
): string {
  const lines = [
    `loop: ${summary.loop}${pidNote(pid, pidAlive)}${haltNote(summary)}`,
  ];
  if (summary.open) lines.push(...describeOpen(summary.open));
  const recent = summary.finished.slice(-5);
  lines.push(`finished iterations: ${summary.finished.length}`);
  for (const done of recent) {
    lines.push(`  #${done.n} ${done.outcome} ${done.detail} (${done.row})`);
  }
  lines.push(`decisions: ${summary.decisions.length}`);
  for (const decision of summary.decisions.slice(-5)) {
    const reason = decision.why ? ` because ${decision.why}` : "";
    lines.push(
      `  ${decision.id} ${decision.how} ${JSON.stringify(decision.answer)}${reason}`,
    );
  }
  return lines.join("\n");
}

function pidNote(pid: number | null, alive: boolean): string {
  if (pid === null) return "";
  return alive
    ? ` (pid ${pid} alive)`
    : ` (pid ${pid} is NOT running: the loop died, restart it)`;
}

function haltNote(summary: JournalSummary): string {
  return summary.haltReason ? ` — ${summary.haltReason}` : "";
}

function describeOpen(open: OpenIteration): string[] {
  const lines = [
    `iteration #${open.n} since ${open.startedAt}: ${open.row}`,
    `  session ${open.sessionId ?? "-"}, mode ${open.mode ?? "-"}`,
  ];
  if (open.rateLimitedUntil)
    lines.push(`  rate limited until ${open.rateLimitedUntil}`);
  for (const command of open.running) lines.push(`  running: ${command}`);
  for (const ask of open.pending) {
    const flag = ask.overdue ? " OVERDUE" : "";
    lines.push(`  waiting for ${ask.kind} ${ask.id} since ${ask.since}${flag}`);
  }
  return lines;
}
