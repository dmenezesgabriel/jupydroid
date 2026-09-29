# ADR-0024 — The app stops its own server, so the app repairs what that breaks

Status: accepted
Date: 2026-08-14

## Context

Left alone, the app stops `jupyter_server` to reclaim memory — about 130 MB. That is a deliberate
trade, and three things follow from it that are constraints rather than details.

**The server dies with the app, so there is nothing to reap.** `am force-stop` with a server running
leaves no `jupyter_server` process: it is a child of the app process
([CLOSED-001](../research/closed-questions.md)). So the idle stopper is about the app being *left*,
not about it being killed.

## Decision drivers

- Reclaiming ~130 MB while the user is elsewhere is worth real complexity.
- A user must never lose a running cell to housekeeping.
- A kernel the app itself killed is not news to the user.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Never stop the server | No repair needed | ~130 MB held while the app is in the background |
| Stop it, and report a dead kernel like the reference does | Parity | The app *caused* the death; reporting it blames the user's notebook for our housekeeping |
| Stop it, and repair the three things it breaks | Memory back, invisible to the user | Three constraints that must each be got right |

## Decision

The server is stopped when the app is idle or left, and the app repairs the three consequences.

**A run is in flight from the moment it is asked for, not from its first frame.** Opening the kernel
*is* part of a run: on the tablet the Space install, the server start and the kernel start are ~14 s
before a cell's `execute_request` goes anywhere. A count that starts later says "nothing is running"
through the slowest part of a run — and the backstop killed a 30 s cell 16 s into it on exactly that
reading. `ActiveCellRuns` is the one place that counts, and `NotebookKernel` moves it around the whole
of `execute`.

**The idle grace is *continuous* idleness.** Run All finishes one cell before starting the next, so
the count passes through zero between every pair; a grace that sampled once would fire there.

**A session that outlives its server is re-opened silently, never reported.** The app stopped that
server, so a dead kernel is not news. `BaseSpaceKernelGateway` knows which server issued which
session and refuses a stale one with `StaleKernelSessionException`; `NotebookKernelSession` opens a
fresh one and runs the cell — **exactly one retry**, because a second refusal is a real failure.
Measured cost: 1–3 s of server and kernel start, ~7 s tap-to-answer, against ~130 MB reclaimed.

**This departs from the reference.** JupyterLab reports a dead kernel — `SessionContext` displays the
kernel's error on `status === 'dead'` — and its `KernelConnection` asks the REST API whether the
kernel is really gone before saying so. Nothing there stops a server on the client's behalf, so there
a death is always news. We do not probe either: the gateway *knows* it replaced the server, which is
a fact rather than an inference from a 404.

## Consequences

Two counters guard the stopper, and they have different shapes because their lifetimes differ —
`ActiveCellRuns` wraps one bounded call, `ActiveTerminals` spans two
([ADR-0019](0019-an-activity-counter-lives-where-its-lifetime-matches.md)).

The silent retry is exactly one. A second `StaleKernelSessionException` is a real failure and must
surface, or the app hides a broken server behind an infinite loop.

The thing a future reader will be tempted to "fix" is counting a run from its first iopub frame,
which reads more naturally. That is the reading that killed a cell 16 s in.
