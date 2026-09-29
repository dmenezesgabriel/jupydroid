# ADR-0014 — Uninstall `rfc3987-syntax` from the image, rather than removing it from the lock

Status: accepted
Date: 2026-09-27

## Context

`-X importtime` on the tablet showed `import jupyter_server.serverapp` costing **3.05 s**, of which
about **2.0 s** was `jupyter_events` → `jsonschema._format` → `rfc3987_syntax`.

That package exists only to power `iri` and `iri-reference` format checks, and it builds a Lark
parser at import — about 1.7 s of the 2.0 s. `jsonschema/_format.py` wraps its import in
`suppress(ImportError)`, so its **absence is upstream's own graceful path**, not something we are
forcing.

The shipped event schemas use `uri` and `datetime` only, and `uri` keeps its checker via
`rfc3986-validator`.

## Decision drivers

- Server boot is on the cold path and was dominated by one import.
- A lock file must say what was resolved, not what we wish had been.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep it | Nothing to maintain | ~2.0 s of every server boot for format checkers no shipped schema uses |
| Remove it from `requirements.lock` | Looks like the tidy fix | **It comes back anyway**: pip resolves `jsonschema[format-nongpl]` for `jupyter-events` and pulls it in. A lock that omits a resolved package is a lie |
| `pip uninstall` it in the Dockerfile, after resolution | Honest lock, and the package is genuinely absent at runtime | The removal lives in the image build rather than in the dependency list, so it needs a gate to stay true |

## Decision

It is `pip uninstall`'d in `tools/rootfs/Dockerfile` rather than deleted from `requirements.lock`,
because pip resolves `jsonschema[format-nongpl]` for `jupyter-events` and pulls it back either way —
a lock must say what was resolved.

A `RUN` gate fails the image build if `jupyter_events` stops importing cleanly or if `uri` loses its
checker.

Measured: `server.starting → server.listening` went **4.4 s → 2.2 s** warm.

## Consequences

**This forbids** reinstating `rfc3987-syntax` or `rfc3987`. The latter is GPL, which is the reason
the `format-nongpl` extra exists at all.

An upgrade that makes a shipped event schema use `iri` or `iri-reference` would break validation
rather than merely slowing it. The `RUN` gate checks `uri` and clean import, not that; a schema
change is the thing to re-check by hand.

The thing a future reader will be tempted to "fix" is the split between the lock and the Dockerfile,
which looks like the dependency list lying. It is the opposite: the lock is honest about what pip
resolved, and the image is honest about what ships.
