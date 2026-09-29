# ADR-0019 — An activity counter lives wherever its lifetime matches the question

Status: accepted
Date: 2026-09-05

## Context

`IdleServerStopper`'s idleness was `cellsInFlight` alone, so a terminal open with no cell running
read as idle and the server stopped **under it** after the 30 s grace. The defect became reachable
only once the app had a real attached shell to lose.

Two obvious homes for a terminal count were rejected before being tried, each for a lifetime reason.

**A client-attachment signal is the wrong signal.** `RealTerminalGateway` already tracks attached
sockets, but a socket tracks whether a *client* is attached, and `TerminalPaneViewModel.attach()`'s
collection is deliberately cancelled on `ON_STOP` via `repeatOnLifecycle(STARTED)`. So an attachment
count reads **zero at the exact moment `onAppStopped()` fires** — the one moment it is consulted.
The correct signal is the server-side one: a terminal *existing*, not a client watching it.

**A gateway-local counter has too short a life.** `BaseSpaceTerminalGateway` rebuilds its underlying
`RealTerminalGateway` whenever the base Space's server is replaced, through `PerRunningServer`. Any
counter kept there resets to zero across exactly the restart the count has to survive — a
process-lifetime question answered by an object with a shorter lifetime than the process.

## Decision drivers

- The counter is read at app-stop, so it must be valid at app-stop.
- It must survive a server restart, because that is what it exists to guard.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Count attached sockets in `RealTerminalGateway` | The data is already there | Reads zero at `ON_STOP`, which is the only moment it is read |
| Keep a counter inside the gateway | Local and obvious | Resets whenever `PerRunningServer` rebuilds the gateway — across the restart it must survive |
| A counter the composition root owns, moved by a decorator | Lifetime matches the process, which is the lifetime of the question | One more object in the wiring |

## Decision

**`ActiveTerminals` (`domain/model/`) mirrors `ActiveCellRuns`'s shape but not its API.**
`ActiveCellRuns.whileRunning` wraps one bounded suspend call, because a cell run's whole lifetime is
exactly that call. A terminal's lifetime spans two independent calls — `open()`, then a much later,
maybe-never `close()` — so `ActiveTerminals` exposes plain `opened()`/`closed()`, floored at zero
against a stray double-close.

`ActivityTrackedTerminalGateway` (`data/space/`) is the one place both calls are wrapped. It
decorates whichever `TerminalGateway` `AppContainer` already holds, forwarding every method unchanged
except `open`/`close`, which additionally move the counter.

The `TerminalGateway` port and its three implementations needed no change and got none: **the
counting is a property of the composition root's wiring, not of the mechanism any of the three
speaks.**

## Consequences

**This forbids** reaching for a gateway-local counter, or a client-attachment signal, for any future
"is this still active" question shaped like this one.

The general rule: the right home is whichever object's lifetime actually matches the question being
asked, and for anything server-side that must survive a restart, that is a counter `AppContainer`
owns directly — not the adapter that happens to answer requests today.

Both counters live in `domain/model/` rather than `data/`, because `ui` and `di` both touch them and
`ui ↛ data`. A counter of the app's own activity is one of the app's own nouns, even though nothing
serialises it.

The thing a future reader will be tempted to "fix" is the decorator, which looks like indirection
around two method calls. Removing it puts the counter back inside an object that is rebuilt at the
wrong moment.
