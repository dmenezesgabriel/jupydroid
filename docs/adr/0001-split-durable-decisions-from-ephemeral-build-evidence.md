# ADR-0001 — Separate documents by lifetime, not by topic

Status: accepted; its tracked `evidence/<step>/` is amended by [ADR-0132](0132-evidence-is-local-and-reproduction-is-a-tested-script.md), which keeps evidence local
Date: 2026-09-29

## Context

This project is built by studying reference implementations and porting their mechanisms. For its
first eight weeks the documentation was four files, 9,150 lines, 1.0 MiB, and two of them carried
three genres at once — durable architectural decisions, ephemeral plan state, and measured
research:

| File | Lines | Bytes | Genres it held |
|---|---|---|---|
| `docs/PLAN.md` | 2,190 | 528 KiB | backlog · shipped record · ADR-shaped rationale · investigation log · risk register |
| `docs/DECISIONS.md` | 6,369 | 442 KiB | 228 decisions · measurements · negative results · licences · errata on `PLAN.md` |
| `docs/architecture.md` | 400 | — | structure · decisions · historical wireframes |
| `docs/environment-fidelity.md` | 191 | — | research memo, conclusion plus the superseded argument |

Five failures followed, each measured rather than supposed.

**The split was already tried once and regressed.** Commit `718fa5dc`, *"docs(plan): split the
record out of the plan, and rewrite what is left"*, cut `PLAN.md` from 3,321 lines to 387 for
exactly this reason. It grew back to 2,190, and 162 `shipped` markers now sit in a file whose own
header reads *"This file is forward-looking only."* Prose discipline alone does not hold this line.

**State was recorded by accretion.** `PLAN.md` was touched in 333 of 583 commits — 57% of all
commits in the repo. Of the 113 `docs:` commits, 50 are scoped `docs(plan):`, several of them
existing only to reconcile the plan with what had already shipped. Its `Tests` row is a running ledger of
recounts, each noting that the previous figure was behind the tree: `256/1261/100/114` → … →
`668/2818/288`, stale at least eight times by its own admission. Shipped state was written in three
redundant vocabularies: 162 markers in all, 26 `*(shipped)*` and 136 `**SHIPPED…**` including
one-off variants such as `**SHIPPED, and the lead was wrong.**`

**The decisions became unfindable.** `DECISIONS.md` held 228 entries under 7 themes with no id, no
date, no status field and no supersession field — `grep -c 'Status:\|\*\*Context\|\*\*Decision\|\*\*Consequences'`
returns 0 for all four. Supersession was done inside the entry title:

```
### One pane grows no navigation bar, and two destinations is why (P2o-4, **reversed by P2y**)
```

So 788 code comments cited the two files by quoted section heading or by plan-step code, and each
citation cost a full-text search of a ~half-megabyte file.

**One document became the other's erratum log.** Twenty-plus `DECISIONS.md` entries quote `PLAN.md`
prose in order to correct it (`:480`, `:3296`, `:3846`, `:3966`). Where a plan row was wrong it was
sometimes fixed and sometimes annotated in place — `PLAN.md:1572` carries *"so this row's own gate
text … is corrected by the code rather than followed."*

**Ephemeral identity leaked into the code, and some of it is now false.** 779 of 1,111 non-doc code
files — 70% — referenced a planning artifact:

| Reference | Count |
|---|---|
| plan/step codes in code | 2,515 lines |
| `docs/DECISIONS.md` | 420 lines, 220 files |
| `docs/PLAN.md` | 368 lines, 217 files |
| `evidence/<step>/` | 1,046 lines, 455 files |
| `reference/…` paths | 250 lines |

The step-code families were not defined in any current document: `A*`, `D*`, `U*` and `X*` are
documented only in `git show 718fa5dc^:docs/PLAN.md`, and the surviving `P*` numbering is itself
unstable — `DECISIONS.md:1914` records *"P4e-P4h, which were P4d until the terminal phase was
re-lettered."* Meanwhile `reference/` is gitignored (`.gitignore:17`), so `git ls-files reference`
returns 0 and all 250 of those citations are unresolvable in any clone. And four comments described
shipped work as future: `domain/port/ServerLauncher.kt` *"Rootfs launcher lands in Step 8"*,
`domain/port/NotebookRepository.kt` *"Disk-backed impl arrives in Step 3"*,
`data/fake/FakeKernelGateway.kt` *"replaced by the real one in Step 10"*, and
`domain/model/Space.kt` *"user-created Spaces arrive in Step 17."*

## Decision drivers

- A decision must stay readable years after the code that motivated it changed.
- Build evidence is worth a great deal on the day it is used and nearly nothing afterwards; keeping
  it inline costs accuracy, because nobody re-verifies it.
- Every session re-reads this documentation, so its size is a running cost, not a one-time one.
- The previous attempt failed on discipline alone. Whatever replaces it needs ids and a gate.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep two growing documents, rewrite the prose again | Nothing to route between | What `718fa5dc` already tried; it regressed from 387 lines to 2,190 |
| Split by topic — one file per subsystem | Familiar | Each file still mixes decision, plan and measurement; the genres are what rot at different rates |
| Split by **lifetime** — durable decisions, ephemeral work, measured research | Each artifact is kept or discarded on its own merits, and ids make it greppable | Requires the discipline to actually delete, so it is gated rather than trusted |

## Decision

Documents are separated by **lifetime**, and the separation is enforced by a pre-commit gate rather
than by intent.

**Durable** — `docs/adr/` and `docs/architecture*`. One decision per file with an `ADR-NNNN` id,
following [MADR](https://adr.github.io/madr/) 4.0.0's `NNNN-title-with-dashes.md` naming; the
architecture description follows [arc42](https://arc42.org/overview)'s 12 sections, where §9 is an
index of the ADRs and nothing more. A durable document must still be true and useful if `reference/`
vanished — which, in every clone, it has.

**Ephemeral** — `docs/backlog.md` and `docs/prd/`. The ledger carries one line per `JD-NNN`; the PRD
is written when the item is picked up and **deleted when it ships**. Its durable residue is exactly
two things: an ADR, if a non-trivial decision was made, and code comments that state their fact
without needing a link.

**Measured research** — `docs/research/`. Investigations, negative results (`CLOSED-NNN`) and
superseded arguments kept because the reading they rest on is load-bearing.

**A code comment states its fact so it stands alone.** It may cite an `ADR-NNNN`, a path in
`app/src/`, a path inside the shipped rootfs, an upstream project at a pinned SHA, a published spec
or upstream issue URL, or a test name. It may not cite a plan/step code, `docs/backlog.md`, a
`JD-NNN`, or `evidence/<step>/`. `scripts/check_doc_citations.py` enforces this.

**Upstream citations name a pinned SHA, not a local path.** `reference/research_repos/jupyterlab/…`
becomes `jupyterlab@7f0e6906 packages/…`. The terminal files already did this and are the reason the
form is known to work; the clone commands and the nine pinned SHAs live in `docs/README.md`.

**Evidence stays committed, and stops being cited by code.** `AGENTS.md` rules 2 and 3 are unchanged:
every step still closes with Maestro flows on the device the rule names, and `evidence/<step>/` is
still committed with the step. What changes is the direction of reference — the ADR restates the
measurement, so the code never has to point at a screenshot. `evidence/` directory names,
`.maestro/*.yaml` and `scripts/*-driver.sh` keep their step codes, because a driver, its flow and its
evidence directory are named in lockstep and that code is their index.

## Consequences

Reference implementations are construction scaffolding. Scaffolding comes down, and its absence from
the finished building is the point rather than an omission.

Corrections are made by editing the file. Git holds the diff, so no document carries its own
revision history inline and no document becomes another's erratum log.

A number that has gone stale is produced rather than stored. The test-count ledger and the
per-package `android.*` counts — both of which this repo's own prose admits drifted repeatedly — are
replaced by the command that counts them.

The cost we accepted: the narrative of how a shipped mechanism was reached is gone from the working
tree, and recovering it means reading the ADR. If the ADR does not answer it, the ADR was written
badly — that is the failure mode to watch, not the missing citation.

The thing a future reader will be tempted to "fix" is
`scripts/doc-citations-baseline.txt`, which lists the files still carrying legacy citations so the
gate could land green on a 70%-affected tree. It only ever shrinks. Adding a file to it re-opens
every failure above.
