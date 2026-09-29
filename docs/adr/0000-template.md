# ADR-0000 — Title in the imperative

Status: proposed | accepted | superseded by [ADR-NNNN](NNNN-slug.md)
Date: YYYY-MM-DD

## Context

The forces at play, in terms of **this system**. What made a decision necessary, what constraints
bound it, what we knew at the time.

A reader must be able to follow this without `reference/` on disk — it is gitignored and travels
with no clone. Cite a real source of truth: a path in `app/src/`, a path inside the shipped rootfs,
an upstream project at a **pinned SHA**, a published spec, an upstream issue URL, or a test name.
Never cite a plan/step code, a `JD-NNN`, or `evidence/<step>/`: those are construction scaffolding,
and scaffolding comes down. The measurement that settled it belongs here, restated — not linked.

A path inside the shipped rootfs is readable without extracting it:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/ipykernel/kernelapp.py | sed -n '605,620p'
```

## Decision drivers

- What we were optimising for, most important first.

## Options considered

| Option | Pro | Con |
|---|---|---|

## Decision

One sentence in the active voice, then the reasoning.

## Consequences

What this makes easy, what it makes hard, and the cost we accepted knowingly. Name the thing a
future reader will be tempted to "fix" and the reason it is the way it is.
