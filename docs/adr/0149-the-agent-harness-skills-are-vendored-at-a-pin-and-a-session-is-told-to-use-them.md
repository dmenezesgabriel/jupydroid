# ADR-0149 — The agent-harness skills are vendored at a pin and a session is told to use them

Status: accepted
Date: 2026-11-14

## Context

`dmenezesgabriel/agent-harness` is a kit of 28 skills in the Agent Skills format: `engineering-mode`,
`tdd`, 20 `principle-*` skills and six `find-*` checks. A sibling repository installs it for Devin
sessions, as a copy at commit `40b3474954c2` under `.agents/skills/`, so that the sessions its autopilot
loop opens work to the same principles. The same loop runs here (ADR-0147), through `claude-agent-acp`.

Measured on 2026-11-14:

- Upstream's current head is `40b3474954c20546b32c76e6c767b224e6515c8a`, the pin the sibling copied. Its
  copy is byte-identical to upstream's `skills/` (`diff -r`): 52 files, 28 directories.
- `claude-agent-acp` starts the Claude Code SDK with `settingSources: ["user", "project", "local"]` and
  `skills: true`, so a project's `.claude/skills/` is read. The upstream README lists claude-code among the
  agents it installs into.
- **Discovery.** A throwaway repository holding the 28 skills under `.claude/skills/`, run by this loop
  with a prompt that names no skill, announced all 28 in the session's `available_commands_update` (96
  commands in all). On a task that matched `flat-methods` and `tdd` (a function nested four deep) the
  agent made 14 tool calls in 33 seconds, none of them a `Skill` call or a read of a `SKILL.md`.
- **Use.** The same repository, run with a one-paragraph standing rule appended to the opening prompt,
  made a `Skill` call for `engineering-mode`, then read `principle-flat-methods`, `tdd` and
  `find-nested-branches`: 19 tool calls in 70 seconds. Plan mode, the exit into bypass, the commit and the
  clean tree all worked as before, and the commit body named the principles that changed a decision.
  Each arm is one run, which shows that the rule changes the behaviour and not by how much.
- The `find-*` checks parse Kotlin with `tree-sitter-language-pack`, declared in each script's PEP 723 header
  as `==1.22.1` with `exclude-newer = "2026-11-01"`. `uv run scripts/find_nested_branches.py` reported a
  Kotlin function with a nested `else` (`depth` 2, `elses` 1). Run with the interpreter alone, the scripts'
  own tests give 9 errors and 1 failure, all `No module named 'tree_sitter_language_pack'`; the same
  result on Python 3.12 and without `-I`, so it is the dependency and not the interpreter.
- `tdd` says "prefer no new test over a bad one", where `AGENTS.md` rule 1 allows none.

## Decision drivers

- A skill is prose an agent obeys, so what is installed must be exactly what was reviewed, and provable.
- A clone, CI and every session see the same skills; the sibling repository's result is the evidence that
  this works, not an assumption.
- `AGENTS.md` is the working agreement; a skill may add to it and never relax it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `npx skills@latest add` | one command | unpinned and unlocked: an installed skill changes under the repository (ADR-0123, ADR-0139) |
| Install at user level, `~/.claude/skills/` | no files in the repository | a clone, CI and another machine's sessions do not have them |
| A git submodule | the pin is the submodule commit | a second checkout step, and a skill directory that is not in the tree an agent reads |
| **A copy at the pin under `.claude/skills/`, a sha256 manifest and a gate** | what the sibling does; byte-checkable | 52 files in the tree, and an update is a deliberate copy |

## Decision

**The 28 skills are copied from `agent-harness@40b3474954c2` into `.claude/skills/`, unedited, and every
session the loop opens is told to use them.**

- `.claude/agent-harness-skills.sha256` records the pin and the sha256 of each of the 52 files;
  `scripts/check_agent_harness_skills.py` fails on a changed file, a missing one, and one added inside a
  vendored skill, and ignores a skill that is not in the manifest (`playwright-cli`). It runs as a commit hook.
- An update is: clone upstream at the new commit, copy `skills/` over, regenerate the manifest from
  `git ls-files skills`, and read the diff. Never edit a vendored file; the gate refuses it.
- `STANDING_RULES` in `scripts/autopilot/run.ts` is a third block of every opening prompt: invoke
  `engineering-mode` before changing code, **where a skill disagrees with `AGENTS.md`, `AGENTS.md` wins**,
  and name in the commit body each principle that changed a decision.
- The `find-*` scripts fetch `tree-sitter-language-pack` when run, at the script's own exact pin and date
  cut-off, with no hash. That is an exception to ADR-0130, which locks the host's gates and the rootfs; these
  scripts gate nothing and are run on demand. Locking them would mean editing vendored files.

## Consequences

Sessions load skills, so a session reads more and takes longer: about twice the wall time on the one task
measured. The standing rule is the loop's, so a session started by hand still loads a skill only when
asked, or when its own triggers match.

This ADR does not settle whether the loop's agent may close an item by its own device read where the
backlog asks for a person's demo. That is the owner's decision and the runbook still escalates it.

Typechecking and tests for the loop stay as ADR-0147 set them; `opening-prompt.test.ts` pins that the
rule names `engineering-mode` and ranks `AGENTS.md` above the skills.
