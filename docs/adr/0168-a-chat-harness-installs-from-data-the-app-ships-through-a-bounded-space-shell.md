# ADR-0168 — A Chat harness installs from data the app ships, through a bounded Space shell, in stopped Spaces only

Status: accepted
Date: 2026-11-21

## Context

`jupyter-ai-acp-client` ships eight chat personas, and each refuses at import unless its agent
CLI is on the Space's PATH: `claude.py` needs `claude-agent-acp`, `opencode.py` needs `opencode`
at `>=1.0.0,<2` read from `opencode --version`
(`jupyter-ai-acp-client@77016f63530f jupyter_ai_acp_client/acp_personas/`). The shipped rootfs
deliberately carries none — its own comment says the personas "spawn an agent CLI the user
installs in the Space" (`tools/rootfs/Dockerfile`) — and a rootfs upgrade deletes what `usr/`
held (ADR-0125), so "installed" can never be a fact the app stores.

Two constraints bound where and how an install can run. First, the persona list is built once
per server process: `PersonaManager._init_ep_persona_classes` catches each entry point's
`PersonaRequirementsUnmet` at load, `_ep_persona_classes` is a `ClassVar` kept for the process's
life, and only `/refresh-personas` re-runs the loads
(`jupyter-ai-persona-manager@d5ed04bc6dfa05fd222d7ea1e54a528b404af4c3
jupyter_ai_persona_manager/persona_manager.py`). A harness installed while the server runs would
probe installed yet stay invisible to Chat until a restart — an answer the Settings row cannot
give honestly. Second, the Space's `jupyter_server` is not needed to ask the disk a question:
proot execs with the Space as `/` (ADR-0073), so a stopped Space's shell answers `command -v`
and `--version` exactly as a running one's would.

## Decision drivers

- "Compatible with the Chat" is a measurement: only a harness that has run on the arm64 tablet
  enters the catalog, because musl/arm64 startup is unmeasured for the other six.
- A person sees the exact bytes before anything runs, and what they saw is what runs.
- The installed answer is live, so a rootfs upgrade re-offers the install by itself.
- An install into a running Space would be invisible to Chat — refused by scope, not by error.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Catalog fetched at runtime | Always current | A fetch adds a failure mode and an unpinned answer for two measured entries; the catalog is reviewable in the diff only as data |
| **Catalog shipped as APK data** | Pinned, unit-testable, grows only on a measurement | A new harness waits for a release |
| Install scripts templated from catalog fields | One source | The shown bytes and the run bytes can drift; the script is the artifact a person audits |
| **Script assets, shown and run as the same string** | "Bytes shown are bytes run" is one read, tested | Scripts are files to keep ShellCheck-clean |
| Install through the running server | Reuses its environment | The Settings dialog's own scope is stopped Spaces, and the persona cache would hide the result anyway |
| **`SpaceShell`: bounded, streamed proot exec for a stopped Space** | Answers for stopped *and* running Spaces; the persona's own `which` + `--version` is the probe | A new port and adapter to keep honest |
| Stored "installed" flag | Cheap | Lies after every rootfs upgrade; the live probe is the only honest answer |
| Allow install while the Space runs | Convenient | Installed-but-invisible until `/refresh-personas` or restart — dishonest UI |

## Decision

**The harness catalog ships as data — `CHAT_HARNESS_CATALOG` in
`app/src/main/java/com/jupydroid/app/domain/model/ChatHarness.kt` plus one POSIX sh script per
entry under `app/src/main/assets/chat-harnesses/` — and installs run through `SpaceShell`, a
port that execs a script under proot with the Space as `/`, bounded by a `Duration` the
Space-side `timeout` enforces and streamed as `Output` lines ending in one `Finished`.**

- The section lives in the stopped Space's Settings dialog
  (`app/src/main/java/com/jupydroid/app/ui/spaces/SpaceHarnessesSection.kt`), the only state the
  dialog edits — which is also the only honest state, since persona classes are cached per
  server process.
- "Installed" is a live probe on every open and again after each clean install: `command -v`
  plus `--version`, parsed by the persona's own rule (`presenceFromProbe` in
  `app/src/main/java/com/jupydroid/app/domain/model/ChatHarnessProbe.kt`). A rootfs upgrade
  clears `usr/` and the row re-offers Install with no migration.
- The script string the expander shows is the string `SpaceShell` is handed — read once into
  the row's state (`shownScriptOf` in `SpaceHarnessesViewModel`), so the audit and the run
  cannot diverge.
- Installs are bounded by `HARNESS_INSTALL_TIMEOUT` (5 minutes, kept from the startup script's
  bound because `ChatHarnessInstallProbeDeviceTest` measured ~49 s worst on the tablet), the
  screen is held while one runs, and the scripts are idempotent so a re-run resumes.
- Signing in to a harness is out of scope — credentials are `GAP-179`'s, and they live under the
  Space's `home/`, which survives the rootfs upgrade that takes the binaries.

## Consequences

- `SpaceShell` is the general "run a script inside a stopped Space" port; the next Space-side
  ask — a kernel toggle, a file probe — has its mechanism already, and `data/fake/`'s
  `FakeSpaceShell` keeps every consumer testable off-device.
- Concurrent runs against one Space serialize on `ProotSpaceShell.prepareMutex`: the settings
  section probes every catalog entry at open, and unguarded prepares raced the kernelspec
  rewrite and directory creation (`ProotSpaceShellTest.concurrentRunsPrepareTheSpaceOneAtATime`).
- The catalog's "what it adds" sizes are measured on the tablet, not estimated; a harness joins
  only after its install has run there, which is why six of the eight personas are absent.
- A running Space still cannot be installed into from Settings — the dialog does not offer it,
  and `/refresh-personas` is the upstream surface that would make it honest.
- The Chat panel still gives no pointer when no harness exists; that remainder of the gap is
  `GAP-201`'s own section in `docs/architecture.md`.
- Reproduce: `ANDROID_SERIAL=<tablet> scripts/chat-harness-install-driver.sh` runs the whole
  demo — install, picker, ask, relaunch — and `grep -rn "ADR-0168" docs/ app/src/` finds the
  citations.
