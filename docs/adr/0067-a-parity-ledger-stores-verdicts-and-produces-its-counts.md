# ADR-0067 — Store a parity verdict; produce every count beside it

Status: accepted
Date: 2026-10-01

## Context

This project's headline quality goal is parity: JupyterLab's behaviour and VS Code's presentation,
adapted to touch, *with every adaptation stated*
([§1](../architecture.md)). The forward process enforces that per item —
[`prd/_TEMPLATE.md`](../prd/_TEMPLATE.md) makes every PRD fill in a row per reference, including the
references that do **not** have the capability, and a gap analysis against our own code before any
code is written.

**That analysis is deleted when the item ships.**
[ADR-0001](0001-split-durable-decisions-from-ephemeral-build-evidence.md) deletes the PRD, keeping
only an ADR and the code comments. The consequence was not noticed until it was looked for: for
everything already built there is no standing answer to *what the references offer that we do not,
and which of those absences was a decision*. The eleven gaps in
[§11](../architecture.md) are the whole retrospective record, and every one of them was found
incidentally while building something else rather than by a sweep.

Two measurements taken while deciding this shape the decision, because both are ways a parity
document can be wrong while looking right.

**A count without its pattern is not a fact.** Two reasonable regexes for a `CommandIDs` constant in
`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12` disagree by seven ids: narrowing the
constant's *name* to `[A-Za-z]+` gives 396 where allowing a digit gives 403, and the seven lost are
real — `markdown1`..`markdown6` (`notebook:change-cell-to-heading-1`..`-6`) and `displayH1Numbering`
(`toc:display-h1-numbering`). Either number can be written into a document and defended; only the
pattern decides which is right.

**A measurement that cannot fail is not evidence.** `scripts/read_port_surface.py`, asked which port
operations nothing uses, first reported zero across all 19 ports. Every port has a real adapter and
a `Fake…` by rule, each overriding every operation, so an override counted as a use made the answer
structurally impossible to be anything else. With overrides excluded it reports four.

So the question is not whether to write the parity analysis down, but which half of it is a *fact to
be produced* and which half is a *judgement to be stored*.

## Decision drivers

- A number that is stored drifts. `scripts/count-tests.sh` exists because one drifted at least eight
  times, and [ADR-0001](0001-split-durable-decisions-from-ephemeral-build-evidence.md) records why.
- A verdict is not reproducible. Whether an absence is an adaptation or an omission is a reading of
  intent, and no script can take it.
- `reference/` is gitignored and travels with no clone, so nothing durable may depend on it.
- An audit that reports "complete" while an area was never opened is worse than one that says
  `unexamined`.

## Options considered

| Option | Pro | Con |
|---|---|---|
| One-off audit report in `docs/` | Cheapest to write | Every number in it is stale the next time a pin moves, and nothing says which areas it actually opened |
| Leave it to each PRD, as now | No new document | Only covers what is built *next*; says nothing about the ~60 ADRs already shipped, which is the question asked |
| A ledger of verdicts, counts produced by script | The stored half cannot drift; an unexamined area is visible as such | Two more scripts to keep, and the ledger must be disciplined about storing no numbers |

## Decision

**The parity ledger stores a verdict per entry and produces every count beside it.**

It lives in [`research/parity-ledger.md`](../research/parity-ledger.md) — measured research, not a
durable document, so it may cite `reference/` where an ADR may not. Counts come from
`scripts/read_reference_commands.py` (the references' side) and `scripts/read_port_surface.py`
(ours), both measurements rather than gates, and both printing the pattern or rule that produced the
number. The specification counts that [§1](../architecture.md) states in prose are asserted in
`read_reference_commands_test.py` against the pinned clones, so bumping a pin fails a test instead
of leaving the prose quietly wrong.

Every entry carries exactly one of five verdicts, and each obliges something different:

| Verdict | Means | Obliges |
|---|---|---|
| `present` | we do what the reference does | nothing |
| `adapted` | we do it differently, and an ADR or a code comment states why | nothing; the citation is the record |
| `undocumented` | we do it differently and the reason is in nobody's writing | an ADR, in the slice that examines the area |
| `absent` | the reference has it, we do not, and no reason is recorded | a `GAP-NNN`, and a `JD-NNN` if it is a capability |
| `defect` | ours behaves wrongly against its own intent | a failing test, then a fix, in the slice that finds it |

An area is `unexamined` until a slice examines it, and the ledger says so by name. Partial coverage
stated is the point: it is what makes the next slice pickable.

## Consequences

**A ledger row never carries a number.** Not "7 of 11 implemented", not a percentage. The row names
the entry, our symbol, the verdict and an id; a reader who wants a total runs the script. This is the
same rule [`backlog.md`](../backlog.md) holds its own rows to, for the same reason.

**`undocumented` is the verdict that does the work.** `absent` is already visible to anyone grepping
for a feature and finding nothing. A difference that *looks* decided — because the code is confident
and someone clearly thought about it — is the one that survives review for years without its reason
being written anywhere, and the first one found says so: `ui/editor/NotebookHover.kt` substitutes the
language server's `textDocument/hover` for the kernel's `inspect_request` and notes the substitution
without giving a reason, while the reference has four callers for the message it drops.

**The tools' limits are part of the record.** `read_port_surface.py` counts *mentions*, so a non-zero
count is an upper bound: an unrelated member of the same name masks a dead operation, which is
exactly how `LanguageServerPort.capabilities` reads as used. Where a verdict rests on a reading the
tool cannot make, the row says the reading was by hand. A verdict with a stated method is worth more
than a number with none.

**What a future reader will want to "fix".** The ledger will look redundant beside
`prd/_TEMPLATE.md`, which asks the same questions. It is not the same artifact: the template asks
them *before* building one item and is then deleted, and this one answers them for what is already
built and stays. Deleting it would re-create the hole this ADR exists to close.
