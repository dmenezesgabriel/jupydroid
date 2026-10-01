# JD-NNN — <capability in the user's words>

Milestone: `M<n>` · State: open | in-progress | partial | shipped · Depends on: JD-NNN | — · Closes: GAP-NNN | —

> Copy this file to start an item. **Delete it when the item ships** — its durable residue is an ADR
> and the code comments, nothing else ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).
>
> A PRD exists so the session that builds this **never guesses and never assumes**. If a decision can
> be settled by opening a reference file, this document says which file. Every path is verified on
> disk in the session that writes the PRD — line ranges rot, so cite paths, and add a line range only
> where a file is large and the range was checked that day.

## Outcome

One sentence: what a user can do afterwards that they cannot do now.

## Demo

A numbered script a human runs from a cold start, on **the device `AGENTS.md` rule 2 names**: anything
that runs a cell goes on the arm64 tablet (`SM-X216B`), because on x86_64 `MainActivity` falls back to
`FakeKernelGateway` and echoes the cell's source back as its output. The emulator is fine for anything
that does not run a cell.

1. Cold start: install, launch, …
2. …
3. Kill and relaunch — the effect persists.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`. Unit tests alone never close it.

## Who else implements this

**Every reference gets a row, including the ones that do not have it** — an absence across several
mature products is evidence too. Verify by reading code, not by memory. Cite `<project>@<sha> <path>`;
the pinned SHAs are in [`../README.md`](../README.md).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL/PARTIAL/ABSENT | `path`, `path` | |
| `vscode` | | | |
| `vscode-jupyter` | | | |
| `intellij-community` | | | |
| `termux-app` | | | |
| `winlator` | | | |
| the shipped rootfs | | | |

**Count:** N of 7 implement this. <What that says about whether to build it.>

Which reference is authoritative for which question is in
[`../architecture/cross-cutting.md`](../architecture/cross-cutting.md) — a claim that "the reference
does X" must name **which** reference.

## Implementation inventory

For **every** reference marked FULL or PARTIAL, the files an implementer must actually open — the
paths, not a summary of them, so the executing session reads the real code and picks what suits us.

### <project>@<sha>

| Concern | Files |
|---|---|
| Domain model | |
| Port / protocol | |
| Adapter | |
| UI entry point | |
| Persisted state | |
| Empty / error / loading states | |
| Accessibility specifics | |
| Edge cases it handles | |

Repeat per reference. **Where two references disagree, name the disagreement** — that disagreement is
the decision this item has to make.

## Trade-offs and what we adopt

One row per real decision. "Options seen" must come from the inventory above, not from imagination.

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|

Anything here that outlives the item becomes an ADR before the PRD is deleted.

## Gap analysis — what we already have vs the references

Read our own code first. An item that half-exists is worse than one that does not, and this is where
that gets caught.

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|

State plainly whether ours is: absent · present but unreachable · present and partial · complete.

## Deviations

Anything we will not port as-is, and why — a phone is not a desktop and the exec model forbids things
the references rely on. A deviation that outlives the item becomes an ADR; the rest is stated in a code
comment at the point it applies.

## Open decisions

Anything this item must settle that the references disagree on, or that none of them answers. Each
needs evidence gathered before it is decided, and an ADR after.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | |
| `domain/port` | |
| `data/<mechanism>` | |
| `data/fake` | |
| `ui/<screen>` | |
| `di/AppContainer` | |
| unit tests | |
| instrumented tests | |
| `.maestro/` flow + `scripts/` driver | |

Every port gets a `Fake…` in `data/fake/`, which is also what `AppContainer` defaults to.

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each. Small enough that a unit
takes well under an hour; if it does not, decompose further before writing code.

- [ ] T1 —
- [ ] T2 —

## Out of scope

What this item deliberately does not do, and which item or milestone owns it.
