# Contributing to Jupydroid

Thank you for looking. This file is the working agreement for people. [`AGENTS.md`](AGENTS.md) holds
the same rules for coding agents and is the long form of everything below; read it when this file
points there.

## Before you start

- Take part under the [Code of Conduct](CODE_OF_CONDUCT.md).
- Report a vulnerability privately, as [`SECURITY.md`](SECURITY.md) describes. Do not open a public
  issue or pull request for one.
- A contribution is licensed under the repository's Apache-2.0 [`LICENSE`](LICENSE), as section 5 of
  that licence provides. There is no CLA and no sign-off line.
- Two of the studied projects (Winlator and sora-editor) are LGPL-2.1, so their code is read and
  never copied: [`docs/architecture/cross-cutting.md`](docs/architecture/cross-cutting.md) §8.4 says
  what each licence allows. `reference/` is gitignored and nothing under it is ever vendored.

## What you need

| Tool | For |
|---|---|
| JDK 17 | the Gradle build (the Kotlin toolchain is 17) |
| Android SDK | compiling, lint and the instrumented tests |
| [`uv`](https://docs.astral.sh/uv/) | the Python host tooling under `scripts/` (Python 3.14 or newer) |
| [`pre-commit`](https://pre-commit.com/) | the commit gate |
| Docker | the ShellCheck hook and the root filesystem build |
| [Node](https://nodejs.org/) 22.12 or newer | the commit-message check (commitlint) |
| an arm64 Android device, `adb` and [Maestro](https://maestro.mobile.dev/) | anything that runs a cell |

How to build the app and its root filesystem is the Build section of [`README.md`](README.md).

## Set up

```sh
uv tool install pre-commit   # or: pip install pre-commit
pre-commit install
npm ci                       # the locked commitlint the commit-msg hook runs
```

`pre-commit install` sets up both the pre-commit and the commit-msg hook. A clone that skipped
`npm ci` has its commits refused by the commit-msg hook, and a clone that installed hooks before the
commit-msg hook existed runs `pre-commit install` again.

The hooks only check; they never rewrite a file. Format with `./gradlew ktlintFormat` for Kotlin and
`uv run --locked ruff format scripts tools` for Python.

## How work is done

- **Test first.** Every new or changed line starts as a failing test, then the smallest change that
  passes, then a refactor. That holds at every layer: domain, ports, ViewModels, UI and adapters.
- **Small and atomic.** One coherent change per commit, and every commit builds and passes. No
  workarounds to get a gate green, and no abstraction nobody asked for.
- **Style is enforced, not discussed.** ktlint, detekt, Android Lint and a duplication check run on
  every commit; the size and naming limits are in the Code style section of [`AGENTS.md`](AGENTS.md).
- **Layers have homes.** What lives in `domain/`, `data/`, `ui/` and `di/` is in
  [`docs/architecture/building-blocks.md`](docs/architecture/building-blocks.md), and an
  architecture test fails a domain class that imports `android.*`.
- **A decision that will outlive the change becomes an ADR.** Copy
  [`docs/adr/0000-template.md`](docs/adr/0000-template.md). [`docs/README.md`](docs/README.md) is the
  map of the documents.

## The gate

`pre-commit run --all-files` runs what a commit runs, and a commit is blocked on any failure. The
exact Gradle and Python commands are the "Local verification command" in [`AGENTS.md`](AGENTS.md), so
there is one copy to keep current. Instrumented tests and device flows are too slow for every commit
and are run before a change is proposed.

## Verify on a device

Unit tests alone never close a change. Drive the real app with a Maestro flow: launch it, tap, type,
and look at the screenshots, since a flow that goes green against the wrong screen is the usual
failure.

**A change that runs a cell needs an arm64 Android device.** Only arm64 ships a root filesystem; on
an x86_64 emulator the app falls back to a fake kernel that echoes a cell's source back as its
output ([ADR-0003](docs/adr/0003-musl-not-glibc-and-arm64-only-at-runtime.md)), so a flow there says
nothing about the kernel. The emulator is fine for a change that runs no cell. Flows are in
`.maestro/` and their drivers are `scripts/*-driver.sh`.

## Evidence stays on your disk

A flow writes screenshots, hierarchy dumps, `maestro.log` and `logcat` to `evidence/`, which is
gitignored and never committed
([ADR-0132](docs/adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)). `logcat` is the
whole device's log: Wi-Fi names, push tokens, other apps. A hook refuses a staged file that holds
them.

Say in the pull request which flow you ran and quote the lines it relies on. A result someone else
must be able to rerun is a script under `scripts/` with a test, and a frame a test asserts on is a
fixture in `scripts/testdata/`.

## Planning ids stay out of the code

`docs/backlog.md` and `docs/prd/` use ids such as `JD-`, `OPS-` and `AUD-`, and task numbers such as
`T3`. They describe work in progress and are deleted or renumbered, so a comment or a test name that
cites one points at nothing later. A comment states its fact so it stands alone, then cites an
`ADR-NNNN`, a path in `app/src/`, an upstream project at a pinned SHA, a spec or issue URL, or a test
name. [`docs/README.md`](docs/README.md) lists what a comment may cite, and
`scripts/check_doc_citations.py` is the gate.

## Commits

- A conventional-commit subject, such as `feat(scope): ...` or `fix(scope): ...`, with one of
  `feat fix docs test refactor build ci chore perf style` (there is no `revert`: history is not undone
  with commits). The commit-msg hook refuses anything else before the commit exists. `npm run commit`
  (commitizen) asks for the type, scope and subject and builds it from the same rules, so it offers no type
  the hook would refuse; writing the message by hand works as well.
- A pull request's commits and its title are checked by the `commit-messages` job. Correcting only the
  title does not re-run it (GitHub re-runs on a push, not on an edited title): push, or re-run the job.
  Branch names say what the work does (`retry-pinned-binary-downloads`), not which item it belongs to.
- The npm tree behind both is locked and its install scripts are off
  ([ADR-0139](docs/adr/0139-commit-subjects-are-checked-by-commitlint-from-a-locked-npm-tree-and-written-with-commitizen.md)).
- The body names the gates that passed and the command or flow that reproduces the result. Gate
  results go in the commit body, not in a document.
- History is not rewritten once the repository is shared with others
  ([ADR-0133](docs/adr/0133-history-is-rewritten-only-while-private-and-shared-with-nobody.md)).
