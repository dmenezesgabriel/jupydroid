# ADR-0157 — The loop drives Claude or Devin, chosen when it starts and switchable between iterations

Status: accepted
Date: 2026-11-16

## Context

The loop ([ADR-0147](0147-a-claude-session-orchestrates-a-loop-that-runs-one-execution-session-per-commit.md))
started `claude-agent-acp` and nothing else. Two sibling loops already drive `devin acp`, and a third carries
the same code with a seam between the loop and the agent. On 2026-11-16 the Claude session limit stopped
the loop's agent mid-iteration for three hours (the five-hour window reset at 17:40Z), with a release waiting
on three items. The owner asked to choose the agent, Claude or Devin, and its model, and to be able to switch
while the loop runs.

Measured on 2026-11-16 against `claude-agent-acp 0.91.0` and `devin 3000.12.57`, by driving each through
`initialize`, `session/new` and `session/set_config_option` with no prompt:

- Both agents list `configOptions` in the `session/new` reply, with a `model` option (`category: "model"`)
  and one for effort (`category: "thought_level"`): Claude calls it `effort` (`low` to `max`), Devin
  `thought_level` (`medium`, `high`, `max`). `effort` is "Unknown config_id" to Devin.
- Both answer `session/set_config_option` with the whole `configOptions` and the new `currentValue`, and both
  refuse an unknown model (Claude `-32603` "Invalid value for config option model", Devin `-32602`).
- **An agent's efforts depend on its model**: after `claude-haiku-5-5-medium` Devin's thought level read
  `medium`, where its default model's read `high`.
- Devin offers 1073 models. Without `clientProvidedModels` declared at `initialize`, a variant such as
  `swe-2-medium` silently runs the family's default, so the read-back is checked and the capability is declared.
- Devin reads this repository's `.claude/skills/`: its global skills directory holds only `humanizer`, and
  `engineering-mode` and the `principle-*` skills appeared among its 116 commands (Claude: 97).
- A reply to a mode change was measured naming a model other than the one that answered, so only the reply
  to the model request is trusted for the model that runs.

## Decision drivers

- A limit on one vendor must not stop the loop when another can carry on.
- Nothing agent-specific in shared code: a third agent is a new directory.
- A model or effort the agent did not take must stop the run, never run on something else unnoticed.
- Every journal says which agent and model ran each iteration, because the orchestrator verifies a commit
  against the raw transcript of the agent that wrote it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A second loop beside this one for Devin | no risk to the running loop | two copies of a 4,000-line loop that already drifted (a halt reason that survives a restart was fixed in one) |
| `if (agent === "devin")` inside the shared code | smallest diff | every difference, and the next agent's, lands in code that works |
| **A profile per agent behind one seam** (executable, modes, plan-exit options, timing, wire dialect, limit text) | one loop, one runbook; a new agent is one directory | the shared types are the union of what each agent reports |

## Decision

**One loop reads what differs between agents from a profile, and a choice file says which profile runs next.**

- `scripts/autopilot/` (renamed from `claude-autopilot`: the loop is no longer one agent's). `agent-profile.ts`
  and `agent-dialect.ts` are the seam; `agents/claude/` and `agents/devin/` hold each agent's executable, mode
  names, plan-exit options, wire dialect, signals and limit text; `acp-agent.ts` is the one client.
- `--agent claude|devin`, `--model <id>`, `--effort <value>` choose; none of them means the agent's own
  default and sends nothing. The model is set first and the effort second, each checked against what the
  agent offered for the model it now runs and against its own read-back; a value it does not offer, or does not
  take, halts the iteration with the offered values listed.
- `choice.json` in the run directory holds the choice. A flag that chooses outranks the file and rewrites it;
  with no flag the file is read; with neither, Claude. `ctl use <agent> [--model m] [--effort e]` writes it
  (atomically), and the loop reads it between iterations, never inside a session. A file it cannot read, or an
  agent it does not know, keeps the current choice and is journaled as `agent_choice_invalid`.
- `--agent-command` names the executable of the agent given with `--agent` only: a switched-to agent starts its
  own.
- `loop_started` and `agent_choice_changed` carry the choice, `session_started` carries what the agent read
  back, and `ctl status` prints both.

## Consequences

The loop can carry on when one vendor's limit is reached, by `ctl use`, without a restart.

Taken from the sibling loop with the seam, and so new here for the Claude path too: a stop that follows a
background command's exit waits for that exit before it spends a reply, a session that committed and left
files in the tree finishes instead of stalling, and a plan exit the loop declines is approved from the
agent's own plan (`--plan-follow-up`). Each has a test of its own.

Not yet measured: a Devin session driving this repository (an `adb` and Maestro demo, a gate that takes
minutes). The first Devin iteration is read from its raw transcript before it is trusted, and the owner-side
script that counts `adb`/Maestro calls and screenshot reads reads Claude's frame shapes only: it has to learn
Devin's before a Devin iteration can be verified the way the runbook requires.
