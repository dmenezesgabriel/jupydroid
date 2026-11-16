import type { LoopEvent } from "./mailbox.ts";

/**
 * Which agent runs the next session, on which model and at which effort. A null model or effort leaves
 * that setting on the agent's own default, which is what every run did before it could be chosen.
 */
export type AgentChoice = {
  agent: string;
  model: string | null;
  effort: string | null;
};

const FILE_SHAPE = '{"agent": "<name>", "model": "<id>" | null, "effort": "<value>" | null}';

export function sameChoice(left: AgentChoice, right: AgentChoice): boolean {
  return (
    left.agent === right.agent &&
    left.model === right.model &&
    left.effort === right.effort
  );
}

/**
 * One line for a person: the agent, then the model and effort or "default" where none is named.
 *
 * @example
 * describeChoice({ agent: "devin", model: "swe-2-high", effort: null });
 * // => "devin, model swe-2-high, effort default"
 */
export function describeChoice(choice: AgentChoice): string {
  const model = choice.model ?? "default";
  const effort = choice.effort ?? "default";
  return `${choice.agent}, model ${model}, effort ${effort}`;
}

function textOrNull(value: unknown): string | null | undefined {
  if (value === undefined || value === null) return null;
  return typeof value === "string" ? value : undefined;
}

/**
 * The choice a `choice.json` holds. A half-written or hand-edited file is refused with its path and
 * the shape it should have, because the loop reads it between iterations and must not guess.
 *
 * @example
 * parseChoiceFile('{"agent":"devin","model":"swe-2-high"}', "/run/choice.json");
 * // => { agent: "devin", model: "swe-2-high", effort: null }
 */
export function parseChoiceFile(text: string, path: string): AgentChoice {
  const refusal = (why: string): Error =>
    new Error(`choice file ${path} ${why}: expected ${FILE_SHAPE}, got ${text}`);
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw refusal("is not valid JSON");
  }
  const fields = (parsed ?? {}) as Record<string, unknown>;
  const model = textOrNull(fields["model"]);
  const effort = textOrNull(fields["effort"]);
  if (typeof fields["agent"] !== "string") throw refusal("names no agent");
  if (model === undefined) throw refusal("has a model that is not text");
  if (effort === undefined) throw refusal("has an effort that is not text");
  return { agent: fields["agent"], model, effort };
}

export type InitialChoiceInput = {
  /** Whether `--agent`, `--model` or `--effort` was given on the command line. */
  flagged: boolean;
  /** What the flags (or their defaults) say. */
  fromFlags: AgentChoice;
  /** What the choice file holds, or null when it does not exist. */
  stored: AgentChoice | null;
};

/**
 * The choice a starting loop uses: a flag on the command line wins and is written to the file, so a
 * later restart without flags keeps it; with no flag the file a previous run or `ctl use` left is read;
 * with neither, the default agent.
 */
export function initialChoice({
  flagged,
  fromFlags,
  stored,
}: InitialChoiceInput): { choice: AgentChoice; writeFile: boolean } {
  if (flagged) return { choice: fromFlags, writeFile: true };
  return { choice: stored ?? fromFlags, writeFile: false };
}

export type RefreshDeps = {
  /** The choice file's content, null when absent; throws when it cannot be read as a choice. */
  read(): Promise<AgentChoice | null>;
  emit(event: LoopEvent): Promise<void>;
  knowsAgent(name: string): boolean;
};

/**
 * The choice the next session uses, read between iterations so `ctl use` takes effect without a
 * restart and never inside a running session. An unreadable file or an unknown agent keeps the
 * current choice and is journaled: a typo must not stop a loop that is working.
 */
export async function refreshChoice(
  deps: RefreshDeps,
  current: AgentChoice,
): Promise<AgentChoice> {
  try {
    const wanted = await deps.read();
    if (wanted === null || sameChoice(wanted, current)) return current;
    if (!deps.knowsAgent(wanted.agent)) {
      throw new Error(`unknown agent "${wanted.agent}" in the choice file`);
    }
    await deps.emit({ type: "agent_choice_changed", from: current, to: wanted });
    return wanted;
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    await deps.emit({ type: "agent_choice_invalid", error: reason });
    return current;
  }
}
