# ADR-0013 — Ship bytecode in the image, invalidated by `checked-hash`

Status: accepted
Date: 2026-09-27

## Context

The image once deleted every `__pycache__` as "pure bulk". Device measurement contradicted that:
`ipykernel.kernelapp` imports in **4.70 s cold vs 1.30 s warm** — about 3.4 s of recompilation paid
by *every* Python process the app starts: the server, the kernel, and `pylsp`.

Timestamp-mode `.pyc` were tried first and measured **flat**. `TarZstdExtractor` gives files
extraction-time mtimes ([ADR-0012](0012-ship-the-rootfs-as-zstd.md)), so every stored source mtime
mismatched and CPython recompiled anyway.

## Decision drivers

- Cold start is paid by three processes, not one.
- A user editing a `.py` inside the Space must still see their edit take effect.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Strip `__pycache__` | ~16 MB smaller | ~3.4 s of recompilation per process, every cold start |
| Ship timestamp-invalidated `.pyc` | The default mode | **Measured flat**: extraction rewrites mtimes, so every cache misses |
| `--invalidation-mode checked-hash` | Validates on source *content*, so it is mtime-agnostic; an edited `.py` still recompiles | A hash check per import, and +16 MB compressed |
| `unchecked-hash` | No validation cost at all | Silently ignores source edits — a user editing a file in their own Space would see nothing change |

## Decision

`compileall --invalidation-mode checked-hash` runs in `tools/rootfs/Dockerfile` **after** the
uninstall and strip steps, so no stale cache survives, and the caches ship — about 4,900 `.pyc`,
+16 MB compressed.

Measured: cold server boot **4.8 → ~2.3 s**, kernel spawn **~2.9 → ~1.2 s**, WebSocket nudge
**~2.7 → ~1.8 s**.

## Consequences

**This forbids** stripping `__pycache__` again, switching to `unchecked-hash`, and relying on
extractor mtimes for correctness anywhere else.

The ordering inside the Dockerfile is load-bearing: `compileall` must run after every step that
removes or rewrites a `.py`, or the image ships bytecode for code that is no longer there.

The thing a future reader will be tempted to "fix" is the +16 MB. It buys about 3.4 s on every cold
start of every Python process, and the timestamp alternative was measured and does not work here.
