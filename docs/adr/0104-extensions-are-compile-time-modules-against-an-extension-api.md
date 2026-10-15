# ADR-0104 — Extensions are compile-time modules against an extension API

Status: accepted
Date: 2026-10-15

## Context

[ADR-0071](0071-nothing-extends-the-app.md) fixed the feature set: nothing could extend the app,
because the UI is one Compose tree with nothing for a plugin to register into. The product now wants
what JupyterLab users install as extensions. The first three are jupyterlab-git, jupyter-ai and
Colab's runtime connection, each kept in its own directory with its own concerns.

Each of the three has two halves, and only one of them can run here:

- **jupyterlab-git** is a Python server extension plus a TypeScript frontend. The server half
  registers REST handlers under `/git` on `jupyter_server` (`setup_handlers`, `NAMESPACE = "/git"`,
  jupyterlab-git@b62a87b7 `packages/jupyterlab/jupyterlab_git/handlers.py`). It is enabled by
  `ServerApp.jpserver_extensions` (`packages/jupyterlab/jupyter-config/server-config/jupyterlab_git.json`)
  and documented whole in `specification/Git_REST_API.yml`. Its Python dependencies are
  `jupyterlab-git-core[nbdime]`, `jupyter_server>=2.0.1,<3` and `traitlets`, and do not include
  JupyterLab. The frontend is a `JupyterFrontEndPlugin` that adds its panel to the left area at rank 200
  (`src/index.ts`).
- **jupyter-ai** is a metapackage over twelve `jupyter-ai-contrib` packages, each with a server half and
  a frontend (jupyter-ai@5b23f55d `pyproject.toml`, `.gitmodules`).
- **colab-vscode** has no server half. It signs in with Google OAuth and asks the Colab API for a remote
  runtime (colab-vscode@96e23580 `src/auth/`, `src/colab/client/`).

A TypeScript frontend needs a DOM and a module loader, and this app has neither
([ADR-0039](0039-a-compose-native-cell-editor-not-a-webview.md)). A Python server half needs only the
Space's `jupyter_server`, which already runs per Space ([ADR-0073](0073-a-space-owns-its-rootfs-and-runs-under-proot.md)).

Android limits how code can arrive. From API 34, a dynamically loaded dex file must be read-only,
or loading it throws
(https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading).
Google Play's Device and Network Abuse policy forbids an app from downloading executable code from
anywhere but Play (https://support.google.com/googleplay/android-developer/answer/9888379).

## Decision drivers

- Each extension in its own directory, with a boundary the build enforces rather than review.
- No re-implementation of a server-side algorithm upstream already maintains.
- No code that arrives at runtime.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Runtime-loaded extensions (`DexClassLoader` over a downloaded jar or APK) | closest to JupyterLab's extension manager | Play forbids it; it would need signing, API versioning and a sandbox, none of which an extension needs today |
| Extensions as packages inside `:app` | no build change | nothing stops an extension from reaching into the app, so the boundary holds only by convention |
| **Compile-time Gradle modules against an `:extension-api` module** | the build refuses a forbidden dependency; every extension is reviewed and shipped with the APK | adding or removing one means a new build |
| Kotlin-native server halves (git through JGit or the CLI) | no Python dependency | rebuilds what jupyterlab-git already maintains |

## Decision

**An extension is a Gradle module built into the APK. It depends on `:extension-api` and on
nothing in `:app`. Its server half, if it has one, is the reference's own Python server extension,
running in the Space's `jupyter_server`.**

This is JupyterLab's *prebuilt* extension in an Android shape. The contract is the module
`:extension-api`, which plays the part of `JupyterFrontEndPlugin`. `:app` collects every extension
and is the only module that depends on all of them. The layout is:

```
extension-api/          :extension-api      the contract
extensions/git/         :extensions:git     jupyterlab-git
extensions/ai/          :extensions:ai      jupyter-ai
extensions/colab/       :extensions:colab   colab-vscode
```

Inside a module, an extension keeps `:app`'s own layering (`domain/`, `data/<mechanism>/`, `ui/`,
`data/fake/`). Its server half talks to the running Space through the `ServerConnection` the host
hands it, as `OkHttpNotebookExporter` already does (`app/src/main/java/com/jupydroid/app/data/space/OkHttpNotebookExporter.kt`).
A Python server half is installed into the base image, so it works offline and the image build gates
it.

## Consequences

- ADR-0071 is superseded. "Every feature is ours to ship or ours to lack" still holds, because an
  extension is code in this repository. What changes is that a feature now has a home that is not
  `:app`.
- The single `:app` module of `docs/architecture/building-blocks.md` §5.1 ends here, at the first
  boundary that is real. Every gate has to name the new modules, or it passes them silently
  ([ADR-0016](0016-name-every-gate-task-explicitly.md)).
- The parity ledger's `pluginmanager:*` and `extensionmanager:*` rows rested on ADR-0071. They are
  verdicted again once the host has a registry to list.
- A server half makes the base image larger and may make cold start slower. Both are measured
  whenever one is added.
- The cost is accepted: no extension can be added without a release.
