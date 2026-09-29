# Backlog

**This file is the entry point for a work session.** It is forward-looking: one line per item, no
prose blocks, no shipped narrative. What is settled and why is [`adr/`](adr/); how work is done is
`CLAUDE.md`; how each item was reached is `git log` and `evidence/`.

Find the next item with:

```sh
grep -n "open ← next" docs/backlog.md
```

## The rules

- An item is one **user-visible capability**, crossing domain → port → adapter → UI. It is never cut
  horizontally: an adapter with no entry point is not shipped. Too large is fixed by narrowing the
  capability, never by cutting the stack.
- An item is `shipped` only after **a human has run its demo** on the device `CLAUDE.md` rule 2
  names. Otherwise it is `partial`, and its row names the missing entry point.
- **Passing gates do not make an item usable.** More than one flow in this repo's history has gone
  green against the wrong thing; the demo records what was measured on screen.
- **Finish what was started.** No milestone opens while an earlier one has an `open` or `partial`
  item.
- A PRD is **written when the item is picked up**, not in advance, and **deleted when it ships**.
  Evidence gathered early rots before it is used. Anything worth keeping becomes an
  [ADR](adr/0000-template.md) or a code comment first.
- Gate results go in the **commit body**, never here. A row gets one line.
- Correct this file by **editing** it. Git holds the history — no dated correction blocks, no
  appended errata. That accretion is what [ADR-0001](adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)
  exists to prevent.
- A row never carries a test count, a line count or any other number that drifts. Those are
  **produced** by a command, not stored.

## Ledger

State is one of `open`, `in-progress`, `partial`, `shipped`. The next item to pick up carries
`open ← next`.

| Id | Capability | Milestone | State | PRD | Closes |
|---|---|---|---|---|---|

<!-- Rows are added by the PLAN.md migration; see ADR-0001. -->

## Not scheduled

Listed so nobody re-derives them, with no commitment to build them.

| What | Why not now |
|---|---|
