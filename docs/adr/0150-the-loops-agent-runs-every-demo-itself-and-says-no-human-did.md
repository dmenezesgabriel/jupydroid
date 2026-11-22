# ADR-0150 — The loop's agent runs every demo itself and says no human did

Status: accepted
Date: 2026-11-14

## Context

The backlog says an item is `shipped` only after **a human has run its demo** on the device `AGENTS.md`
rule 2 names, and the loop's runbook (ADR-0147) therefore escalated a question that asked to close an item
on a demo. The loop runs for hours without a person at the tablet, so an item either waited on that person or
was closed on the agent's own read of the device. No item waited, and one was closed the second way, in
`20b2796e`.

The owner stated a standing rule for the loop on 2026-11-14: the agent runs the demos itself, as if it were
a person using the device through Android's `adb` and Maestro, takes snapshots and screenshots and analyzes
them, always, and **this must be explicit: the demo is not done by an actual human**.

`AGENTS.md` rule 2 already asks for what a person's demo is made of: Maestro on the arm64 tablet, evidence
captured, and "look at the screenshots", because more than one flow here went green against the wrong thing.
A sibling repository has the same rule for a browser app (`playwright-cli` in place of `adb` and Maestro), and
its loop states it in every session's opening prompt.

## Decision drivers

- A demo exists to show what a person would see, so the agent has to look at the screen, not at a log.
- Nobody reading a commit may be led to think a person ran what the agent ran.
- The device stays what rule 2 names: on x86_64 a cell's source is echoed back, so a demo that ran a cell
  there proves nothing.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep escalating every demo to the owner | the rule as written | the loop stops until a person is at the tablet |
| Let the agent close items on its device read without saying so | no friction | a commit reads as if a person ran it; the line between a read and a demo is lost |
| **The agent runs the demo itself, looks at the screenshots, and states that no human did** | the loop continues; the record is honest | the demo is only as good as the agent's reading of the image |

## Decision

**A demo the loop's agent runs itself, as a person would, counts as run, and the agent says it was not run by a human.**

- `DEMO_RULE` in `scripts/autopilot/run.ts` is part of `STANDING_RULES`, so every session opens with
  it: run the demo yourself with `adb` and Maestro on the device rule 2 names, take a screenshot and a UI
  dump at every step that changes the screen, look at and analyze the screenshots, and do not stop to ask a
  person. The commit body says `Demo run by the agent, not by a human`, then what each step showed and every
  difference from what the item expects. `demo-rule.test.ts` pins each of those parts.
- The orchestrator never answers "run" on the agent's word, answers a "ship it?" question with "prepare only"
  and the instruction to run it, and checks the shipping commit for that sentence and the transcript for the
  `adb`/Maestro calls and screenshot reads (`scripts/autopilot/ORCHESTRATOR.md`).
- This is how the **loop's** sessions read the backlog's rule. The rule itself is unchanged for a session
  started by hand, where a person is present, and a demo that ran a cell on the x86_64 emulator does not count.

## Consequences

An item can now close with no person having seen it. The commit says so, and the screenshots stay local
(ADR-0132), so the record is the commit body's description of them and not the images; a reader who doubts
one reruns the flow. The sentence in the commit body is what makes that visible, and the orchestrator's
check of it is what keeps it true.

Not yet measured: that a session obeys the rule. The rule is tested as text, and the first evidence is the
next shipping commit's body and transcript. If a session closes an item without the sentence, the fix is the
orchestrator's reply and, if it repeats, a gate on the commit message, not a longer rule.
