# ADR-0025 — A kernel is ended by a command, never by closing the document

Status: accepted
Date: 2026-08-15

## Context

`DELETE api/sessions/<id>` exists and is reached from two menu items in the reference —
`notebook:shutdown-kernel` and `notebook:close-and-shutdown`. **Closing a notebook does not reach
it**, and the reference says so twice: the `kernelShutdown` setting is *"Whether to shut down or not
the kernel when closing a notebook"* with `"default": false`, and `SessionContext.dispose()` shuts
down only `if (this.kernelPreference.shutdownOnDispose)`.

An earlier plan row here said closing *should* shut the kernel down. That was wrong, and it is the
default that makes reattach reachable at all: leaving a notebook calls `disconnect`, the kernel lives
on, and reopening finds it by path with its namespace intact
([ADR-0022](0022-kernels-start-through-sessions-and-one-tree-is-the-root.md)).

Both halves are measured on one build: a reattach prints `44` after a *leave*, and a `NameError`
follows a *shutdown*.

## Decision drivers

- A user who taps Back has not asked to lose their variables.
- Ending a kernel is destructive, so it needs a deliberate act.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Shut down on close | Frees the kernel eagerly | Destroys a namespace on a navigation gesture, and breaks reattach — which is the thing that makes leaving a notebook cheap |
| Never shut down at all | Nothing to get wrong | No way to reclaim a kernel deliberately; the reference has two |
| Shut down only on an explicit command | Matches the reference's own default | Two ids to keep straight, and a kernel can outlive interest in it |

## Decision

Only an explicit command ends a kernel. Closing a document calls `disconnect`, and the kernel lives on
to be reattached to.

Three rules follow:

- **Only 204 is success**, plus 404 read as "somebody got there first" and logged; 410 keeps the
  server's own sentence about a kernel deleted before its session. **A 200 is a refusal.**
- **`KernelSession` carries three ids and they are not interchangeable**: ours, which signs every
  message; the kernel's, for `interrupt` and `restart`; and the server's session id, for `shutdown`.
- **Shutting down must never start a server**, exactly as `disconnect` must not. A kernel whose server
  the idle stopper already stopped
  ([ADR-0024](0024-the-app-stops-its-own-server-so-the-app-repairs-what-that-breaks.md)) is already
  gone, and starting a server to tell it so would be absurd.

Whether closing *ever* shuts down is a setting question — the reference's `kernelShutdown` — not a
behaviour question.

## Consequences

Reattach is the everyday path and shutdown is the exception, which is the reverse of what a naive
reading of "close the document" suggests.

The three ids are the most likely source of a future bug here. `shutdown` taking the kernel id
instead of the session id produces a 404 that looks like "already gone" and is not.

The thing a future reader will be tempted to "fix" is treating 200 as success, because most APIs do.
Here it means the server declined and said so politely.
