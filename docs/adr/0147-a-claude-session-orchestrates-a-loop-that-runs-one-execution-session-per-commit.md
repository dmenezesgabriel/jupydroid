# ADR-0147 — A Claude session orchestrates a loop that runs one execution session per commit

Status: accepted
Date: 2026-11-14

## Context

`prompt-execution.md` is run by hand: open a clean session, hand it the prompt, answer its questions,
approve the plan, wait for the commit, start the next session. Each round is the same few judgements
against the same documents, and the waiting between them is most of the elapsed time.

The same procedure is automated in a sibling repository by a Node script that drives `claude-agent-acp`
over the Agent Client Protocol (`scripts/claude-autopilot/` there, commit `83baa48e`). It holds no
model: a headed Claude Code session reads its event journal and answers what needs judgement. It was
measured against `claude-agent-acp 0.91.0` driving `claude 2.1.325`, and the two recorded sessions it was
checked against are `scripts/testdata/claude-autopilot-frames/`.

What differs here, measured on 2026-11-14:

- The backlog marks the next row as `open ← next` or `partial ← next`, in a table whose first column is
  a backticked id. The sibling's marker, `**open ← next`, matches no row here.
- The doc-citations gate reads every file under `scripts/` and fails on a type it has no scanner for, so a
  run directory of journals, a pid and a `STOP` file cannot live there, and a `.ts` file needs a scanner.
- `.claude/scheduled_tasks.lock` was tracked and Claude Code rewrites it in every session that runs in the
  tree. The loop reads a dirty tree as an unfinished commit, so the orchestrating session itself kept
  every iteration from finishing.
- Work here ends on a device and behind a commit gate that takes minutes, so what a leftover process is
  differs: the emulator and Maestro, not a dev server or a browser.
- `package.json` is a deliberately small tree (ADR-0139, ADR-0123): exact pins, no install script, nothing
  younger than 7 days.

## Decision drivers

- The procedure stays the one in `prompt-execution.md`; the loop only repeats it.
- No dependency the loop does not need: the sibling's loop imports only Node's own modules.
- A gate that cannot read a file fails, rather than passing quietly (the doc-citations gate's own rule).

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep running the sessions by hand | no new code | the waiting stays, and so does the context each session re-derives |
| Rewrite the loop in Python with the host tooling | one language, `uv.lock` already covers it | no ACP client in the lock, and the recorded frames and the runbook are written against the TypeScript one |
| **Carry the TypeScript loop over, with the tests ported to `node:test`** | the two repositories keep one procedure and one runbook | brings `typescript` and `@types/node` into the npm tree |
| Carry it over with vitest | the tests need no porting | a test runner and its tree for one directory |

## Decision

**The loop lives in `scripts/claude-autopilot/`, runs under Node's type transform, is typechecked by
`tsc` and tested by `node --test`.**

- Its default run directory is `.autopilot-run/`, gitignored and outside `scripts/`.
- `scripts/check_doc_citations.py` reads `.ts` with the `//` scanner and treats `.ndjson` and `.jsonl` as data.
- `.claude/scheduled_tasks.lock` is untracked and ignored.
- `nextBacklogRow` reads the id from a table row that carries `← next`, whatever its state.
- Its tests use named fakes under `scripts/claude-autopilot/testing/`, as `AGENTS.md` asks, not inline stubs.
- `npm test` runs them with the commit tooling's tests, and `npm run typecheck` is its own CI step.

## Consequences

`npm test` needs Node 24: type transform is a flag there, and `engines` still names the older floor the
commit tooling needs. Typechecking the tests found one fixture in the sibling's iteration tests that
omitted a required field, which the sibling's runner never typechecked.

The loop's functions are longer than the 20 lines `AGENTS.md` asks of Kotlin; they were carried over
unchanged so the two repositories stay diffable, and no gate here measures TypeScript function length.

The adapter's behaviour was measured elsewhere. A new `claude-agent-acp` version may change it, and the
`agent_signal` events are how that would show.
