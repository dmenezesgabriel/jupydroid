# ADR-0065 — The kernel indicator keeps its status when the name goes, and solid means active

Status: accepted
Date: 2026-08-25

## Context

`_getWidgetsToRemove` sheds a `ReactiveToolbar`'s **rightmost** items into the popup first, so JupyterLab
would push `executionProgress` (rank 1002) in *before* `kernelName` (1000). On a narrow window the
reference therefore loses the **status** and keeps the **name**.

And the reference **disagrees with itself** about the glyph. The retired `KernelStatus` toolbar item used
`circleIcon` — a solid disc — for busy, starting, restarting, terminating and initializing,
`circleEmptyIcon` for idle, and `offlineBoltIcon` for connecting/disconnected/unknown. The
`ExecutionIndicator` that replaced it keeps that split for starting and restarting but not elsewhere.

## Decision drivers

- On a phone the useful half of "Python 3 · Idle" is *Idle*.
- A glyph that means "active" must mean it consistently, whichever component the reference took it from.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Shed the status first, as the reference's rank order implies | Literal parity | Leaves a kernel's *name* on a phone and drops what it is *doing*, which is the half that changes |
| Keep the status, shed the name | The information that moves stays on screen | Reverses the reference's own shedding order |
| Copy one of the two reference components' glyph split | Either is citable | They disagree, so "the reference does this" is not a complete answer |

## Decision

**Below 600dp the status glyph stays on the bar and the kernel's name is what goes** — the reverse of the
reference's shedding order, because the status is the half that changes.

**Active kernel states are solid and resting ones are outlined**, resolving the reference's own
disagreement in favour of the rule that reads: solid means something is happening. The indicator is
outlined at rest for the same reason the cell's Run control is
([ADR-0057](0057-the-cell-keeps-its-header-strip-and-the-run-gutter-is-declined.md)) — the reference's own
filled bolt was the loudest mark in a row of outlines.

## Consequences

**The indicator can read *Idle* for a kernel the idle stopper has already stopped**, for exactly one
window: backgrounded past the grace, returned, nothing run yet. It corrects itself on the next run — the
stale session is refused, `NotebookKernelSession.reopeningIfStale` drops it, and the status falls to
`STARTING`.

The honest fix is a **server-generation signal reaching the editor**, which `BaseSpaceServer` already
counts. That is a new port surface, so it is recorded as a known gap rather than patched at the UI.

The thing a future reader will be tempted to "fix" is the shedding order, since the reference's ranks say
otherwise. On a 360dp bar the name is the part nobody needs.
