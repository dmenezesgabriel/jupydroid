# ADR-0046 — A transcription of an algorithm is diffed against its source, not reviewed against it

Status: accepted
Date: 2026-08-29

## Context

The terminal emulator is a transcription of `TerminalEmulator.java` (2,617 lines) and the row storage
under it. Two forces pull against each other: the transcription must behave identically, and detekt
refuses the upstream shape — `TerminalRow.setChar` is 117 lines at a complexity the config rejects, so it
ships here as **nine functions across two classes**.

Twenty-six transcribed assertions are a thin oracle for a rewrite of an array splice.

## Decision drivers

- A transcription's whole value is behavioural identity; "it looks right" is not evidence of that.
- Decomposition is forced by the linter, so the risk of divergence is structural rather than incidental.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Transcribe the upstream tests and review the code | Cheap | 26 assertions over a rewritten array splice; the shapes differ most exactly where the tests are thinnest |
| Keep the upstream shape and suppress detekt | No decomposition risk | A 117-line method at refused complexity, and the same pressure recurs on every one of 2,617 lines |
| **Run both implementations against the same scripted sequence** and compare their entire observable state | Identity is measured, not argued | A harness to build, and the script's own reach has to be checked |

## Decision

**A transcription of an algorithm is closed by a differential against its source**, and the transcribed
unit tests cover what the differential's inputs cannot reach.

Both implementations are run against the same scripted sequence — `java.util.Random` is specified, so a
seed produces the same stream in Java and in Kotlin — and their entire observable state is compared after
every operation: `spaceUsed`, `isBlank`, every Java char in hex, all 41 column starts, all 40 styles.

**12,000 operations over three seeds, byte-identical.**

**Four mutations were run to show the comparison can fail**, and the two that survive are explained rather
than shrugged at: the combining cap is out of the script's reach — measured, it never stacks more than
**four** marks on one column — and the array's growth chunk is capacity, which no caller can observe.

**The first script was not enough, and the method itself showed that.** It drove only `setChar` and
`clear`, so `copyInterval` — the method this transcription changed most — had no coverage beyond two unit
tests until it was added. It is identical with ~1,275 of those operations in.

## Consequences

A differential that cannot be made to fail proves nothing, so **mutation of the transcription is part of
the method**, not an extra. A surviving mutation must be explained, and "the script cannot reach it" is an
acceptable explanation only when that reach has been measured.

This is what found four defects in the reference itself, each written up beside the differential rather
than silently worked around.

**A differential that prints deltas measures neither side's baseline** — both sides must be printed, which
is one of the standing rules in
[`../architecture/cross-cutting.md` §8.6](../architecture/cross-cutting.md).

The thing a future reader will be tempted to "fix" is the differential harness, which looks like test
scaffolding for work that is finished. It is the only thing that makes the next decomposition of
transcribed code safe.
