# Execution session

You are starting a clean session with no memory of earlier ones. The repo's docs and git history are your
memory. Follow these steps in order.

## 1. Orient — grep first, read only what you need

- `git log --oneline -15` and `git status --short`. Uncommitted or untracked work from an earlier session
  means that session stopped partway through. Understand it before starting anything new.
- Open `docs/backlog.md`. Find the next item with `grep -n "\*\*open ← next" docs/backlog.md`.
- Read that item's PRD in `docs/prd/JD-NNN-*.md`, and its first unchecked task with
  `grep -n "\[ \]" docs/prd/JD-NNN-*.md`. **If it has no PRD, write it first** from
  `docs/prd/_TEMPLATE.md`.
- For a decision, grep the id and read only that ADR: `grep -rn "ADR-0022" docs/ app/src/`.
- For what lives where, `docs/architecture/building-blocks.md`. For how something behaves at runtime,
  `docs/architecture/runtime-views.md`. For a concern that crosses screens — the exec model, the palette, the
  testing strategy, which reference answers which question — `docs/architecture/cross-cutting.md`.
- Before investigating anything, check it is not already closed: `docs/research/closed-questions.md`.
- Follow `AGENTS.md`. Do the searches yourself; spawn agents or workflows only if I ask.

## 2. Scope — one task per session

- Do **one task** (`T<n>`) of the item, then stop. If a task is still too big, split it in the PRD first,
  then do the first piece.
- An item is one user-visible capability crossing domain → port → adapter → UI. Never cut it horizontally:
  an adapter with no entry point is not shipped. Too large is fixed by narrowing the capability.
- Finish what was started. Don't open a new item while the current one is `open` or `partial`.

## 3. Build — strict TDD, evidence over assumption

- Read how the references named in the PRD solve the task before writing code, and take the best of them.
  Act on what you measured and read, never on assumption. `docs/README.md` has the nine pinned SHAs and the
  clone commands; `cross-cutting.md` §8.3 says which reference is authoritative for which question, and
  what each was checked and found **empty** for.
- RED → GREEN → REFACTOR, strictly. Every new or refactored line gets unit tests, and instrumented tests
  wherever the question is "is there something visible to press".
- No workarounds, no band-aids, no over-engineering, no dead code. A defect you introduce is yours to fix
  before you stop, whether or not it was planned.
- Use web search and `curl` when the references and official docs are not enough to settle a fact.
- References are read-only: never install, import, link or vendor them. What each licence permits is
  `cross-cutting.md` §8.4.

## 4. Validate like a human would

- Maestro on **the device `AGENTS.md` rule 2 names**: anything that runs a cell goes on the arm64 tablet,
  because on x86_64 the app falls back to `FakeKernelGateway` and echoes the cell's source back
  ([ADR-0003](docs/adr/0003-musl-not-glibc-and-arm64-only-at-runtime.md)).
- **Look at the screenshots.** More than one flow here has gone green against the wrong thing; a green log
  is half the evidence. Before writing a flow, read `cross-cutting.md` §8.6 *What a test here cannot do* —
  every row in it passed against the wrong thing once.
- Measure; don't eyeball. For any layout you touch, record the numbers before and after.
- Screenshots, snapshots and `maestro.log` into `evidence/<step>/`, committed. `logcat` is captured and
  **never committed** ([ADR-0015](docs/adr/0015-rewrite-history-once-to-take-the-device-out-of-it.md)).

## 5. Close the step

- Run the full gate; results go in the **commit body**, never in a doc.
- Commit each task with a conventional commit, and tick the PRD's checkbox.
- **A code comment states its fact so it stands alone**, then cites an `ADR-NNNN`, a path in `app/src/`, a
  path inside the shipped rootfs, an upstream project at a **pinned SHA**, a spec or issue URL, or a test
  name — never a step code, `docs/backlog.md`, a `JD-NNN`, `evidence/<step>/` or `reference/…`.
  `scripts/check_doc_citations.py` is the gate.
- **When the item ships — its demo run on the device — delete its PRD.** Anything worth keeping becomes an
  ADR or a code comment first ([ADR-0001](docs/adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).
- Update the backlog ledger by **editing** it: one line per item, no appended corrections.
- Stop, and tell me what shipped, what you measured, and what the next task is.
