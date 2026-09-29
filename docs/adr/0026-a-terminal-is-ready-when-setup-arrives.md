# ADR-0026 — A terminal is ready when `setup` arrives, and its socket belongs to the attach

Status: accepted
Date: 2026-08-29

## Context

terminado's `TermSocket.open` sends `["setup", {}]` and *then* appends the client to
`terminal.clients`. The WebSocket handshake completes a round trip earlier, so a frame written in
between is **accepted by the socket and dropped by a server that has not associated it — silently, at
both ends.**

The reference refuses to call itself connected until `setup` arrives
(`packages/services/src/terminal/default.ts`) and queues what is typed meanwhile, in
`_pendingMessages`.

Two further shapes on this wire are not the kernel's. The route is
`r"/terminals/websocket/(\w+)"`, registered by `jupyter_server_terminals/app.py`, while that same
extension's REST handlers are `r"/api/terminals"` — **outside `api/`**. Getting the prefix wrong is a
404 at upgrade and nothing else.

And a terminal has no per-request traffic: its socket **is** the reading of it. `TermSocket.open`
writes `setup` and immediately drains `read_buffer`, so a socket opened eagerly would deliver both the
setup and the first prompt into a flow with no subscriber and no replay.

## Decision drivers

- A screen composes its key handler and its collector independently, so a keystroke can genuinely
  arrive before readiness.
- Never ship a silent unbounded wait — that is what the execution stall was.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Treat `Opened` as ready | Simple | Frames written between the handshake and `setup` are dropped silently at both ends |
| Fail a send before readiness | Explicit | Makes a race the UI cannot order into a crash |
| Suspend until readiness, bounded | The reference's own queueing, with an error path | Readiness has to be completed *exceptionally* when a socket dies before `setup`, or a stale name hangs |

## Decision

`TerminalGateway.send` and `.resize` **suspend** until readiness rather than failing, bounded at ten
seconds. The readiness signal is completed **exceptionally** when a socket dies before `setup` — a
stale name answers 404 at upgrade, and every pending keystroke has to *learn* that rather than merely
stop waiting.

**The socket belongs to the attach, not to the gateway.** It opens when the flow is collected and
closes when the collection stops. A kernel's socket is held from the first execution until the
document closes, because the server counts connections against the kernel; a terminal's is not. That
is what keeps the first prompt, and it is what makes leaving the screen cheap — the shell lives on and
the next attach is replayed into.

## Consequences

**This forbids** treating `JupyterSocketEvent.Opened` as readiness anywhere on this wire, and it
forbids a `BaseSpaceTerminalGateway` registry of issued names. `BaseSpaceKernelGateway` needs one
because a stale kernel session surfaces as an undebuggable 404; a terminal's does not — a replaced
server lists no terminals, and an attach to a name it never minted is refused by `TermSocket.get` and
arrives as `TerminalEvent.Ended` carrying that 404. A registry would invent a state the wire already
reports.

Because a re-attach replays the *whole* session rather than the tail
([CLOSED-007](../research/closed-questions.md)), the local screen is cleared first and rebuilt from
the replay.

The thing a future reader will be tempted to "fix" is the ten-second bound, which looks arbitrary. The
bound is not the point; having one at all is, and the exceptional completion beside it is what stops a
stale name waiting out the whole bound for nothing.
