# ADR-0017 — Prune stale evidence frames from a per-flow manifest

Status: accepted
Date: 2026-08-29

## Context

`run_flow` did `mkdir -p "$out"` and then `cp`. Nothing removed anything. So renaming a
`takeScreenshot` left its predecessor in `evidence/<step>/` **and in the commit**, for as long as the
repository lives.

A stale frame is not clutter. It is a **second answer to the question the current frame answers**,
and eleven were found across six directories:

- a frame from 22 August showing a browser with **no bottom bar** — sitting beside the frame whose
  entire point is that the bar is there;
- a frame from 18 August drawing the word `markdown` where a cell's prompt goes, which a later step
  removed and a current gate asserts is absent;
- seven frames of a launch screen that had been retired, one of them answering a current frame's
  question under a different number.

## Decision drivers

- Evidence is only evidence if it answers one question once.
- The most valuable frame in the tree is sometimes one no flow wrote.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Leave it; review by eye | No tooling | Eleven stale frames survived months of review |
| Delete whatever the current run did not write | One rule, no state | **Wrong twice.** Five drivers run several flows into one directory, so a later part would wipe an earlier part's frames; and a driver may write a frame itself — `00-the-defect-only-a-screenshot-could-see` is deliberate, and a rule that could not tell it from a leftover would delete the most valuable frame in the tree |
| Keep a per-flow manifest and remove only what that flow has stopped writing | Handles both cases exactly | Carries state in the evidence directory |

## Decision

`scripts/prune_stale_frames.py` keeps a per-flow manifest, `evidence/<step>/.frames`, recording which
flow wrote which frame. It removes **only what that flow wrote before and has stopped writing**. Six
tests, one per way it could get this wrong.

## Consequences

It protects a directory from the run **after** its first manifest, not before. The eleven already
committed were deleted by hand, and one more was found afterwards for exactly that reason — the
limitation demonstrating itself.

A hand-authored frame in an evidence directory is safe, and deliberately so.

The thing a future reader will be tempted to "fix" is the manifest, which looks like state that a
`git clean` would make unnecessary. The two cases above are why a stateless rule deletes the wrong
frames.
