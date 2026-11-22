# autopilot: orchestrator runbook

The loop in `run.ts` repeats the manual workflow: open a fresh session of the chosen agent (`claude-agent-acp` or `devin acp`, [ADR-0157](../../docs/adr/0157-the-loop-drives-claude-or-devin-chosen-when-it-starts-and-switchable-between-iterations.md)) in plan mode, give it `prompt-execution.md`, answer its questions, let it leave plan mode into bypass, wait for the commit, start the next session. It contains no model. A headed Claude Code session is the orchestrator: it reads the loop's events and answers what only judgement can answer.

## Start

Node runs these files directly, but its default type stripping rejects the constructor parameter properties the loop uses, so every command carries `--experimental-transform-types --no-warnings`. Below, `ctl` stands for `node --experimental-transform-types --no-warnings scripts/autopilot/ctl.ts`.

```sh
# 1. the loop, detached so it outlives any one Monitor. It is a daemon, so it runs
#    directly, not through devjob (devjob-guard lets a bare `node` through). Start it from a
#    shell whose PATH has node and claude-agent-acp (nvm's default version); from any
#    other shell add --agent-command <absolute path of claude-agent-acp>
nohup node --experimental-transform-types --no-warnings scripts/autopilot/run.ts > /dev/null 2>&1 &

# 2. in the Claude session (auto mode, because /goal does not change the permission mode)
/goal The loop in scripts/autopilot reported backlog_exhausted or stop, or it reported loop_halted and I looked at why. Until then: keep a Monitor armed on .autopilot-run/events.jsonl (re-arm at each 30-minute expiry), answer every needs_answer and needs_reply per this runbook, push-notify me on the events marked ESCALATE, and report each iteration_done with its commit. Stop after 40 iterations.
```

Run both from the repository root: `ctl` and `run.ts` resolve `.autopilot-run/` against the directory they start in, and `run.ts` against `--repo` when it is given.

The Monitor command wakes Claude only for events that need judgement: `tail -n 0 -F .autopilot-run/events.jsonl | grep --line-buffered -E '"type":"(needs_answer|needs_reply|needs_answer_overdue|possibly_hung|hung_cancelled|permission_requested|exit_plan_bypass_missing|background_watch_error|loop_halted|iteration_done|iteration_stalled|iteration_halted|iteration_crashed|backlog_exhausted)"'`. Routine events (`preflight_wait`, `rate_limited`, `rate_limit_rejected`, `awaiting_work`, `turn_ended`, `agent_signal`, `plan_ready`) stay in `events.jsonl` for the morning and never reach the chat.

## Choosing the agent and the model

`run.ts` takes `--agent claude|devin`, `--model <id>` and `--effort <value>`; none of them means the agent's own default. Whatever the agent offers is checked at the start of each session: a model or effort it does not offer, or does not take, halts the iteration with the offered values listed. A flag that chooses outranks `.autopilot-run/choice.json` and rewrites it; with no flag the file is read; with neither, Claude.

To switch while the loop runs, change the file between iterations, never inside one:

```sh
ctl use devin --model swe-2-high --effort high   # next session on Devin; omit a flag to reset it to the agent's default
ctl use claude                                   # back to Claude's own defaults
ctl status                                       # "next session: ..." is the choice, "session <id> on ..." is what ran
```

`ctl use` checks the agent name at once; the model and effort are checked by the next session, which is the only place the agent lists them. A file the loop cannot read, or an agent it does not know, keeps the current choice and is journaled as `agent_choice_invalid`. `--agent-command` names the executable of the agent given with `--agent` only.

The agent changes what to read in a transcript: Claude's shell calls are `tool_call_update` frames with `_meta.claudeCode.toolName`, Devin's are `tool_call` frames with `kind: "execute"` and `rawInput.command`. `iteration-evidence.py` reads both; it counts skills for Claude only, because a Devin skill call has not been seen as a tool call.

## Nothing is lost: the journal is the only state

`events.jsonl` is append-only and holds every question, every answer with its `--why`, every defaulted answer, each session id, mode change, background command start and exit, rate-limit wait and iteration outcome. Nothing else keeps state, so a restarted loop or a fresh Claude session loses nothing.

- `ctl status` rebuilds from it: the loop's state and whether its pid is alive, the open iteration with its session, mode, running commands and questions still waiting (flagged OVERDUE), the last finished iterations with their commits, and the last decisions with their reasons.
- A new orchestrator session starts by running `ctl status`, then re-arms the Monitor. Answer whatever `waiting for` lines it lists.
- If the loop process died mid-iteration, `status` says its pid is NOT running. Restart it; on start it journals `iteration_interrupted` with the session, the unanswered questions and the commands that were running, then opens a new session. Nothing it left in the tree is discarded: the next session's preflight reads `git status`.
- The full ACP traffic of each session is in `.run/acp-<timestamp>.jsonl`; read it when a stop needs explaining.
- Never delete `.run/` while a loop runs. It is gitignored and holds the only record of the night.

## Keeping the subscription cost low

Claude spends tokens only when a turn runs, so the design keeps turns rare.

- Run the orchestrator session on a small model (`/model sonnet` or `haiku`): answering a multiple-choice question against the policy below does not need the largest one.
- Never poll. The Monitor is the only wake-up; do not `sleep`, `tail` or re-read `events.jsonl` between events.
- One short action per event: grep the one PRD or ADR row a question names, run one `ctl` command, and say nothing else. Report only `iteration_done` (commit hash and subject) and escalations.
- Re-arm the Monitor at its 30-minute expiry with no commentary.
- Prefer `/goal` with a turn cap (`Stop after 40 iterations`) so a stuck evaluator cannot spin.
- Start a fresh orchestrator session every few iterations: its context grows with every question. The loop and its files hold all state, so a new session needs only this file and `ctl status`.
- Everything that needs no judgement is the loop's job, not Claude's: rate-limit waits, host load, leftover processes, background commands, retries and the circuit breaker.

Only one loop runs per run directory: `run.ts` refuses to start when `.run/loop.pid` names a live process. `ctl status` prints that pid and the last events.

## Events and what to do

| Event                                     | Meaning                                                                                                                                        | Do                                                                                                                                                                                                                                                                                              |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `iteration_started`, `iteration_done`     | One session began; one ended with a new commit and a clean tree                                                                                | Report the commit in one line. Nothing else                                                                                                                                                                                                                                                     |
| `needs_answer {id, questions}`            | The agent asked a multiple-choice question and waits                                                                                           | Answer by the policy below: `ctl answer <id> --why "<ADR or evidence>" "<label>" ...`, one label per question. Always give `--why`: it is journaled with the answer. A label that is not one of the offered options is sent as the question's own "Other" text. ESCALATE when it needs the user |
| `needs_answer_overdue`                    | Ten minutes without an answer                                                                                                                  | ESCALATE: push-notify the user and answer as soon as you can. At 60 minutes the loop answers with the option marked Recommended, else the first, and logs `answer_defaulted`                                                                                                                    |
| `needs_reply {id, why, lastMessage}`      | The agent stopped in plan mode, or stopped in bypass with no commit or a dirty tree. Read `lastMessage`: it is usually a question or a blocker | Answer in plain words: `ctl reply <id> --why "<reason>" "<text>"`. At 10 minutes the loop sends a fixed nudge and logs `reply_defaulted`                                                                                                                                                        |
| `awaiting_work`                           | The agent ended its turn while a background command it started still runs                                                                      | Nothing. The loop watches the command's output file for its exit line, then tells the agent how it ended                                                                                                                                                                                        |
| `possibly_hung`                           | 45 minutes without any frame from the agent, or a background command beyond its 3 hour ceiling                                                 | Read the newest `.run/acp-*.jsonl` tail. If the agent is stuck, `ctl stop`; the loop cancels it by itself after 3 hours of silence                                                                                                                                                              |
| `rate_limited {wakeAt}`                   | A limit hit; the loop sleeps and sends `continue`                                                                                              | Nothing                                                                                                                                                                                                                                                                                         |
| `rate_limit_rejected`                     | Claude Code reported a rejected limit, with its type and reset time                                                                            | Nothing: the loop's `rate_limited` follows. A seven-day limit becomes `loop_halted`                                                                                                                                                                                                             |
| `preflight_wait {reason}`                 | A new session is held back: busy host, or a leftover dev server or browser from the last session                                               | Nothing for a busy host. For a leftover process, look at it and tell the user; never kill what you did not start                                                                                                                                                                                |
| `permission_requested`                    | The permission mode still asked about a tool; the loop allowed it once                                                                         | Read the tool. ESCALATE if it is destructive or outside the repo                                                                                                                                                                                                                                |
| `background_watch_error {shellId, error}` | The loop could not read a background command's output file (anything but "missing"); it reported this once and keeps polling                   | Look at the file the error names. If it stays unreadable the loop only gives up at the 3 hour ceiling: `ctl stop`, or fix the permission. Never edit the output file by hand: its last line is what the loop trusts                                                                             |
| `exit_plan_bypass_missing {offered}`      | Leaving plan mode did not offer bypass (the adapter offers it only to a non-root process); the loop declined and the iteration halts           | ESCALATE with the offered option ids. Do not restart until bypass is available                                                                                                                                                                                                                  |
| `agent_signal`                            | A `_claude/` notification or a session update nobody has read yet                                                                              | Note its payload in your report; no action                                                                                                                                                                                                                                                      |
| `agent_choice_changed {from, to}` | The loop read a new `choice.json` (from `ctl use`) between iterations and the next session uses it | Nothing. Say it in the report |
| `agent_choice_invalid {error}` | `choice.json` could not be read, or names an agent the loop does not know; the loop kept the current choice | Fix it with `ctl use`, or tell the user |
| `agent_stopped {cause}` | Devin reported its agent ended its turn for a reason other than completing | Read `cause`; a limit or an error is followed by the loop's own `rate_limited` or retry |
| `exit_plan_reject_missing {offered}` | `--plan-follow-up` is set and the agent offered no way to decline the plan exit; the loop declined nothing and the iteration halts | ESCALATE with the offered option ids |
| `plan_approved {via}` | The loop declined the plan exit itself, left plan mode and handed the agent its plan under the follow-up command | Nothing |
| `loop_halted {reason}`                    | Weekly limit, refusal, repeated stalls or crashes, session setup failure                                                                       | ESCALATE with the reason. Do not restart without looking                                                                                                                                                                                                                                        |
| `backlog_exhausted`, `stop`               | No `open ← next` row is left, or `ctl stop` was run                                                                                            | Done: end the goal                                                                                                                                                                                                                                                                              |

## Answering policy

Choose by this order. It is the rule the user applies by hand.

1. The option that follows an accepted ADR (`docs/adr/`) and `AGENTS.md`.
2. The option the reference implementations support best, read from their source: `docs/architecture/cross-cutting.md` §8.3 says which reference is authoritative for which question, and `docs/research/closed-questions.md` says what is already closed. An option no reference ships is an invention and loses to one they do.
3. The more conservative and reversible option. An existing tool or library over a new dependency; never one from an unofficial source. Strict TDD stays.
4. The skills under `.claude/skills/` are the session's tools, not an authority: where one disagrees with `AGENTS.md`, `AGENTS.md` wins (item 1). Never answer toward "no new test" because `tdd` allows it.
5. Never an option that skips a gate, a test, the Maestro flow or the one-task-per-session scope. A device choice is not free: anything that runs a cell goes on the arm64 tablet (`AGENTS.md` rule 2), because the x86_64 emulator echoes the cell's source back.
6. Grep the PRD or ADR row the question names before answering. A question that needs the user is an ESCALATE: push-notify, leave it unanswered, and let the overdue path run. That means money, credentials, a destructive action, a force-push, rewriting history, a package from an unofficial source, and signing in to an account. A demo is not on this list: the user delegated it, see the standing rule below.

## Standing rule: the agent runs every demo itself, and says it was not a human

Stated by the user on 2026-11-14 ([ADR-0150](../../docs/adr/0150-the-loops-agent-runs-every-demo-itself-and-says-no-human-did.md)). Whenever a step, a task or a question asks for a person to run an item's demo, the agent in the loop runs it itself, as a person would: it drives the real app with `adb` and Maestro on the device `AGENTS.md` rule 2 names, takes a screenshot and a UI dump at every step that changes the screen, and looks at and analyzes the screenshots, not only the dumps and logs. A demo run this way counts as run. It is not run by a human, and that is explicit: the commit body says `Demo run by the agent, not by a human`, then what was seen at each step and every difference from what the item expects.

What the orchestrator does:

- A question that asks "has the demo been run, ship it?" is never answered "run" on the agent's word and never by claiming a human ran it. Answer "Prepare only" or the equivalent, then reply: run the demo yourself with `adb` and Maestro as above, read the screenshots, and ship only if it passes.
- A session that stops with "waiting on the human demo" gets a `needs_reply` with the same instruction.
- `STANDING_RULES` in `run.ts` states the rule to every session, so most need no reminder.
- Check the shipping commit before reporting it: its body must contain `Demo run by the agent, not by a human` and what each screenshot showed, and the session's `.autopilot-run/acp-*.jsonl` must show the `adb`/Maestro calls and screenshot reads. If they are missing, say so in the report.
- The device is still not a free choice: a flow that runs a cell on the x86_64 emulator proves nothing, because it echoes the source back. A demo that ran a cell there does not count; reply with that.

## Facts the loop relies on

Measured against `claude-agent-acp 0.91.0` driving `claude 2.1.325`, on 2026-11-13, in the repository this loop was written for; here they are carried over, with the two recorded sessions as fixtures in `scripts/testdata/autopilot-frames/`. A new adapter version may change them; `agent_signal` events are how that would show.

| Fact                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Where it shows                                                                                      |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| A new session starts in `auto` mode. Mode is the `mode` config option; `plan` and `bypassPermissions` are its values, announced by `current_mode_update`                                                                                                                                                                                                                                                                                                     | `claude-agent.ts` `setMode`; fixture `claude-0.91.0-plan-question-exit`                             |
| `AskUserQuestion` reaches the client only when `initialize` declares `elicitation.form`; it arrives as `elicitation/create` with a `question_<n>` field per question and a `question_<n>_custom` text field; the accept reply is `{action:"accept", content:{question_0:"<label>"}}`                                                                                                                                                                         | `claude-agent.ts` `answerQuestions`                                                                 |
| Leaving plan mode is a `session/request_permission` for the tool `ExitPlanMode` (`kind: "switch_mode"`, `rawInput.plan` holds the plan) whose options include `exit-plan-bypass`                                                                                                                                                                                                                                                                             | `handlers.ts` `exitPlan`                                                                            |
| The adapter accepts an embedded `resource` prompt block (`promptCapabilities.embeddedContext`)                                                                                                                                                                                                                                                                                                                                                               | `run.ts` `openingPrompt`                                                                            |
| A command run with `run_in_background` ends its tool call at once, carries `_meta.claudeCode.toolResponse.backgroundTaskId`, and names an output file in its result text. The turn can end before the command does; no frame reports the exit, and the output file ends with `[exited with code N]`, or with `[killed]` when Claude Code stopped the task (823 and 124 of 948 files measured), which has no code and is reported as an exit with a null code | `background-task-tracker.ts`, `background-exit-watcher.ts`; fixture `claude-0.91.0-background-bash` |
| A rate limit is the SDK's `rate_limit_info` (`status` `rejected`, `resetsAt` in epoch seconds, `rateLimitType` `five_hour` or `seven_day*`) on a `usage_update`'s `_meta["_claude/rateLimit"]`                                                                                                                                                                                                                                                               | `signals.ts`, `rate-limit.ts`                                                                       |
| Project skills in `.claude/skills/` are announced to the session as commands in `available_commands_update`, and a session whose prompt does not name one did not use one (1 run: 14 tool calls, no `Skill` call) | `opening-prompt.test.ts`; ADR-0149 |
| With `STANDING_RULES` in the opening prompt the session made a `Skill` call for `engineering-mode` first, then read the principle skills that fit (1 run: 19 tool calls, twice the wall time), and the commit body named the principles | `run.ts` `STANDING_RULES`; ADR-0149 |
| **Not measured:** the prompt error text of a limit hit (a limit cannot be forced), whether `rate_limit_info` always precedes it, and how long a working Claude turn can stay silent. Any error naming a limit polls every 30 minutes; the silence thresholds are Devin-era guesses                                                                                                                                                                           | `rate-limit.ts` `classifyAgentError`, `run.ts` `TIMING`                                             |

Measured against `devin 3000.12.57` on 2026-11-16 (the frames are `scripts/testdata/autopilot-frames/devin-3000.12.57-plan-exit.ndjson`, [ADR-0157](../../docs/adr/0157-the-loop-drives-claude-or-devin-chosen-when-it-starts-and-switchable-between-iterations.md)):

- `session/new` lists `configOptions` with `mode` (`accept-edits`, `smart`, `ask`, `plan`, `bypass`), `model` (about a thousand ids, effort in the id's suffix) and `thought_level` (`medium`, `high`, `max`, depending on the model). `initialize` must declare `_meta["cognition.ai/clientProvidedModels"]`, or a model variant silently runs its family's default.
- Leaving plan mode is a `session/request_permission` that names its tool call only by id; the call is the earlier `tool_call` frame with `kind: "switch_mode"`, whose `rawInput.plan` is the plan. Its options are `plan_accept_edits`, `plan_bypass` and `reject_once`, all `allow_once`.
- A background command reports its own exit (`terminal_exit`), so no output file is watched. A limit is an error text: the free-model limit prints "reset in N minutes (at HH:MM UTC)" and the loop waits for it, the daily quota prints no time and polls, and "monthly usage limit" halts the loop as `monthly_limit`.
- This repository's `.claude/skills/` are announced to Devin as commands too (116 against Claude's 97).
