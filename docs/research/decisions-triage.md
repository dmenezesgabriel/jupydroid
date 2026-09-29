# Migration audit — where each `DECISIONS.md` entry went

`docs/DECISIONS.md` held 228 entries under 7 themes, with no ids, no dates and no status field. This
file is the audit trail for splitting it, so that nothing was dropped silently and the pass is
reviewable. It is deleted once the migration is complete and the source file is gone.

The triage rule, applied entry by entry
([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)):

| If the entry… | Destination |
|---|---|
| states a rule that forbids a future alternative | an `ADR-NNNN` |
| describes a mechanism several files share | `architecture/cross-cutting.md` §8 |
| states where code lives | `architecture/building-blocks.md` §5 |
| is a negative result — we looked, there is nothing there | `research/closed-questions.md` as `CLOSED-NNN` |
| is a measurement with no decision attached | the Context of the ADR that consumed it, else `research/` |
| is an erratum on `PLAN.md` prose | deleted; `PLAN.md` is going away |

## Progress

| Section | Entries | Migrated |
|---|---|---|
| Runtime | 14 | **yes** |
| Protocol and behaviour | 36 | no |
| Editor | 32 | no |
| Architecture and UI | 116 | no |
| Harness | 11 | **yes** |
| Licences | prose | no |
| Closed by measurement | 41 | partly — 5 preamble bullets are `CLOSED-001`–`005` |

## Runtime — 14 entries

| Source entry | Destination |
|---|---|
| Exec model | [ADR-0002](../adr/0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md) |
| `nativeLibraryDir` moves on every app update | folded into ADR-0002 Context — it is the constraint that forces the per-launch relink, not a separate decision |
| musl, not glibc; and arm64 ships, x86_64 does not (yet) | [ADR-0003](../adr/0003-musl-not-glibc-and-arm64-only-at-runtime.md) |
| SIGSYS is a signal to handle, not a verdict | [ADR-0004](../adr/0004-handle-sigsys-and-return.md) |
| DNS cannot be fixed by redirecting a file, so `getaddrinfo()` is interposed instead | [ADR-0005](../adr/0005-interpose-getaddrinfo-rather-than-redirect-resolv-conf.md) |
| busybox is built here, not taken from Alpine, and one flag is why | [ADR-0006](../adr/0006-build-busybox-here-with-suid-compiled-out.md) |
| The Space's shell is named once, in `SHELL`, and three things read it | [ADR-0007](../adr/0007-name-the-spaces-shell-once-in-shell.md) |
| Two Android hazards inside CPython, both worked around, both still true | [ADR-0008](../adr/0008-work-around-two-android-hazards-inside-cpython.md) |
| Kernel logging cannot leak into cell output | [ADR-0009](../adr/0009-keep-kernel-logging-out-of-cell-output.md) |
| A Space is a directory, and its id comes from the scan | [ADR-0010](../adr/0010-a-space-is-a-directory-and-its-id-comes-from-the-scan.md) |
| Packages land in the Space's own site, and pip is steered by environment, not flags | [ADR-0011](../adr/0011-install-packages-into-the-spaces-user-site-via-environment.md) |
| The rootfs archive is zstd, because the decoder was the floor | [ADR-0012](../adr/0012-ship-the-rootfs-as-zstd.md) |
| Bytecode ships in the image, `checked-hash` because extraction keeps no mtimes | [ADR-0013](../adr/0013-ship-bytecode-invalidated-by-checked-hash.md) |
| `rfc3987-syntax` is uninstalled from the image, not removed from the lock | [ADR-0014](../adr/0014-uninstall-rfc3987-syntax-from-the-image.md) |

Dates on these ADRs are the date the decision first appeared in `DECISIONS.md`, recovered with
`git log --format=%cs -S'<phrase>' --reverse -- docs/DECISIONS.md`, not the date of the migration.

## Harness — 11 entries

Mostly testing lessons rather than decisions, so most consolidate into one place instead of becoming
eleven small files.

| Source entry | Destination |
|---|---|
| History was rewritten once, before the first push, to take the device out of it | [ADR-0015](../adr/0015-rewrite-history-once-to-take-the-device-out-of-it.md) |
| `ktlintCheck` never checked a `.kt` file, because the plugin was waiting for a plugin AGP 9 made unnecessary | [ADR-0016](../adr/0016-name-every-gate-task-explicitly.md) |
| Evidence is a directory, and `run_flow` used to only ever add to it | [ADR-0017](../adr/0017-prune-stale-evidence-frames-from-a-per-flow-manifest.md) |
| The local gate does not compile `androidTest`, and it went green over a file that would not build | `GAP-008` in `architecture.md` §11 — it records an open gap, not a decision taken |
| A `LazyColumn` composes past its own edge, so "visible" is not "on screen" | `cross-cutting.md` §8.6, *What a test here cannot do* |
| Where the editor is scrolled to is a thing no Maestro flow can assert | same |
| A lazy list is what a two-pane test cannot lay out, and a `Column` was hiding it | same |
| A scroll action inside a `waitUntil` hangs the whole instrumentation | same |
| A layout assertion in dp measures the component's padding by accident | same |
| A one-pane defect can need the tablet, not the phone | same |
| The emulator the suite runs on is 320dp wide **and** density 160 | same |
