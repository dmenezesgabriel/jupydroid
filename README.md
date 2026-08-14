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

Not built yet: the Space system, a terminal, LSP, the debugger, the console, search and ToC —
and the server has no lifecycle. See `docs/PLAN.md`.

Kotlin + Jetpack Compose, `com.jupydroid.app`, AGP 9, compileSdk 36, minSdk 32, targetSdk 36.
The runtime is **arm64-only**; on x86_64 the app runs against a fake kernel for UI work.

## Documentation

| File | What it is |
|---|---|
| `docs/PLAN.md` | what gets built next, phase by phase |
| `docs/DECISIONS.md` | what is settled and what it forbids — including the exec model |
| `CLAUDE.md` | how work is done: the working agreement, per-step procedure, tooling |
| `docs/architecture.md` | layers, ports and the data model |

## Build

```sh
./gradlew :app:assembleDebug
```

Requires the Android SDK at `~/Android/Sdk` (see `local.properties`). The rootfs asset and the
two native ELFs are build artifacts, not committed files — build them with
`tools/rootfs/build.sh arm64-v8a` then `tools/rootfs/stage-app-artifacts.sh arm64-v8a`.

## Reference

Study material lives in `reference/`, which is **git-ignored**: none of it travels with a clone,
and nothing under it is ever vendored (Winlator is LGPL-2.1). `docs/DECISIONS.md` records what
each reference is authoritative for, and the clone commands.

- `reference/research_repos/` — upstream clones studied for technique: `jupyterlab`,
  `winlator`, `intellij-community`, `termux-app`, `box64`, `box86`
- `reference/xeus-android/` — the original feasibility and architecture study
