# ADR-0050 — Reads may hit the repository; server-side algorithms may not be re-implemented

Status: accepted
Date: 2026-08-14

## Context

The server exposes `/api/contents`, and the app could read the workspace through it. It does not: it reads
from disk directly, because that is faster and works **before the server is up** — and the app deliberately
keeps the server down when it can
([ADR-0024](0024-the-app-stops-its-own-server-so-the-app-repairs-what-that-breaks.md)).

But three things the server does are **algorithms**, not I/O: checkpoints, notebook trust/signing, and
nbconvert export. Re-deriving HMAC notebook signing in Kotlin would be re-implementing a security
mechanism.

## Decision drivers

- The app must work with the server stopped, which is most of the time.
- A security mechanism re-implemented is a security mechanism weakened.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Everything through `/api/contents` | One path, full fidelity | Requires the server for a file listing, which means booting a runtime to open a folder |
| Everything from disk | No server dependency at all | Would mean re-implementing HMAC notebook signing, checkpoints and nbconvert |
| Reads from disk; algorithms through the server | Fast, offline-capable, and no security mechanism re-derived | A notebook this app writes is unsigned, which has a consequence |

## Decision

**Reads hit the repository directly; server-side algorithms go through the server's API.**

**Writing directly has one known consequence, and it is not free.** `ContentsManager` signs a notebook on
save — `check_and_sign` over `nbformat.sign.NotebookNotary` — so a notebook this app writes is **unsigned**
and would open untrusted elsewhere.

**`notebook:trust` is inert here, and that was decided on measurement rather than on the parity list.**
Trust gates exactly three renderers in JupyterLab, and the list is closed rather than representative. None
of the three is something this device renders in a way trust would change, so there is nothing for the
command to gate. It is not built, and this records what would make it real rather than leaving the gap
unexplained.

## Consequences

A notebook this app writes is unsigned. Opening it in JupyterLab will present it as untrusted, which is
correct behaviour on JupyterLab's part and a real interoperability cost here.

Making `notebook:trust` meaningful means first rendering something trust gates. The command is not the
missing piece; the renderer is.

The thing a future reader will be tempted to "fix" is routing writes through `/api/contents` to get signing
for free. That makes every save require a running server, which is the opposite of what the idle stopper is
for.
