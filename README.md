# JupyterDroid

Offline Jupyter notebook app for Android. Runs a **real** `jupyter_server` + `ipykernel`
inside a bundled, rootless **musl** (Alpine) Linux rootfs on loopback, with a mobile-optimised
UI: a Jetpack Compose shell and a **Compose-native** cell editor.

## Status

Working end to end on an arm64 tablet: rootfs installs, `jupyter_server` starts on 127.0.0.1,
kernels start through `POST /api/sessions` and reattach, cells run and render their outputs
(stream, tracebacks, PNG, `text/html`, tables), and notebooks persist as nbformat on a filesystem
the app, the server and the kernel all share. There is a file browser, a Compose cell editor with
syntax highlighting and kernel-backed completion, and a two-pane layout at ≥ 840dp.

Also built: Spaces as directories under `files/spaces/`, a real busybox terminal over terminado,
`pylsp` over stdio, the debugger over DAP on the kernel's control channel, the console, workspace
search and replace, and the table of contents. The server's lifecycle is built too — it stops itself
when it is idle or the app is left, comes back on the next run, and a kernel can be ended on purpose
from the kernel menu.

What is *not* built is in [`docs/backlog.md`](docs/backlog.md) — the Space system's runtime and UI, and
whatever the next cold-path measurement names. The eleven measured gaps are
[`docs/architecture.md` §11](docs/architecture.md).

Test and flow counts are **produced, not stored**, because a stored one drifted repeatedly:

```sh
scripts/count-tests.sh
```

Kotlin + Jetpack Compose, `com.jupydroid.app`, AGP 9, compileSdk 37, minSdk 32, targetSdk 36.
The runtime is **arm64-only**; on x86_64 the app runs against a fake kernel for UI work.

## Documentation

Start at **[`docs/README.md`](docs/README.md)** — it is the map, and it carries the id scheme and the
greps. Documents are separated by **lifetime**, not by topic
([ADR-0001](docs/adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

| Path | What it is | Lifetime |
|---|---|---|
| [`docs/architecture.md`](docs/architecture.md) + `docs/architecture/` | an arc42 description: goals, constraints, building blocks, runtime views, crosscutting concepts, risks | durable |
| [`docs/adr/`](docs/adr/) | one decision per file, and what each forbids | durable |
| [`docs/backlog.md`](docs/backlog.md) | what gets built next — one line per item | ephemeral |
| `docs/prd/` | how to build one item; written when it is picked up, deleted when it ships | ephemeral |
| [`docs/research/`](docs/research/) | measured investigations, and questions closed so nobody re-opens them | durable |
| `CLAUDE.md` | how work is done: the working agreement, per-step procedure, tooling | durable |
| `evidence/<step>/` | the screenshots, snapshots and logs a step closed on | durable |

## Build

```sh
./gradlew :app:assembleDebug
```

Requires the Android SDK at `~/Android/Sdk` (see `local.properties`). The rootfs asset and the four
native ELFs (`libloader.so`, `libpython3.so`, `libbusybox.so`, `libshebang.so`) are build artifacts,
not committed files — build them with
`tools/rootfs/build.sh arm64-v8a` then `tools/rootfs/stage-app-artifacts.sh arm64-v8a`.

## Reference

Study material lives in `reference/`, which is **git-ignored**: none of it travels with a clone, and
nothing under it is ever vendored. Because it travels with no clone, no durable document and no code
comment depends on it — a comment cites an upstream project as `<project>@<sha> <path>` instead.

[`docs/README.md`](docs/README.md) has the nine pinned SHAs and the clone commands;
[`docs/architecture/cross-cutting.md` §8.3](docs/architecture/cross-cutting.md) records what each
reference is authoritative for, and §8.4 what each licence allows — Winlator and sora-editor are
LGPL-2.1 and therefore clean-room only.

- `reference/research_repos/` — upstream clones studied for technique: `jupyterlab`, `vscode`,
  `vscode-jupyter`, `intellij-community`, `jediterm`, `termux-app`, `winlator`, `box64`, `box86`
- `reference/xeus-android/` — the original feasibility and architecture study
