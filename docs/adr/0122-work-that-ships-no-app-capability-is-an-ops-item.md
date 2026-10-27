# ADR-0122 — Work that ships no app capability is an OPS item, closed on the run that proves it

Status: accepted
Date: 2026-10-27

## Context

The backlog has two kinds of item. A `JD-NNN` is one user-visible capability, crossing domain → port
→ adapter → UI, and it closes on a demo run on the device that `AGENTS.md` rule 2 names. An
`AUD-NNN` examines one area and ships a measurement. Until now it was "the only kind that is not a
capability" ([ADR-0067](0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)).

The next body of work fits neither kind. It covers taking personal data out of the repository's
history, a licence and its notices, CI that runs the commit gate and free security scanners, a
release pipeline, and a documentation site. None of it changes a screen, so no Maestro demo can
close it, and a Maestro flow written for it would prove nothing about it. It is also not a
measurement. It builds something, and that something works or it doesn't. Folded into `JD-NNN`, it
would carry a demo that cannot exist. Folded into `AUD-NNN`, it would be held to "repairs only a
`defect`", which forbids the work itself.

What *does* prove such work is a run anyone can repeat. Examples are a green workflow run, a
published Release, and a `git log --all -S '<marker>'` that finds nothing.

## Decision drivers

- Every item closes on evidence that someone other than its author can reproduce.
- No item carries a demo it cannot have.
- The two existing families keep their meaning. `JD` stays "a capability", `AUD` stays "a
  measurement".

## Options considered

| Option | Pro | Con |
|---|---|---|
| File it as `JD-NNN` | No new id | Its demo rule cannot be met, so every row would be `partial` forever, or the rule would quietly stop meaning anything |
| File it as `AUD-NNN` | Already exempt from Maestro | An audit records and repairs only a `defect`; building a CI pipeline is neither |
| A third family, `OPS-NNN`, closed on a reproducible run | Each family keeps one meaning; the closing evidence matches the work | One more id to learn |

## Decision

Work on the repository or its delivery that ships no app capability is an `OPS-NNN` item. It closes
on **the command or workflow run that reproduces it**, and that run is named in the commit body.

- An OPS item has a PRD written from `docs/prd/_TEMPLATE.md` when it is picked up, and the PRD is
  deleted when the item ships, as a `JD-NNN`'s is
  ([ADR-0001](0001-split-durable-decisions-from-ephemeral-build-evidence.md)). The PRD's *Demo*
  section names the run instead of a Maestro script.
- It runs no Maestro flow, because nothing on screen changed. An OPS task that does change a screen
  owes the flow like any other. When work has a user-visible half, such as the app showing its own
  licences or a user installing from a release, that half is a `JD-NNN` with a demo of its own.
- Its id is ephemeral, so `scripts/check_doc_citations.py` bans `OPS-NNN` in code comments as it
  bans `JD-NNN`.
- TDD still applies to every line of code an OPS item writes, scripts and gates included.

## Consequences

The backlog can now hold repository hygiene, CI/CD and documentation work without bending the
demo rule. The cost is a third family, and the judgement of which family an item belongs to.

The test is **does it change what a user of the app can do?** If yes, it is `JD-NNN`, even when it
also needs CI. If no, it is `OPS-NNN`.

A future reader will be tempted to give OPS items a Maestro flow "for consistency". It would go green
against an unchanged app and prove nothing, which is the failure rule 2 exists to prevent.
