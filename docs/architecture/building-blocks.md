# 5. Building block view

arc42 §5. What lives where, and what stops it moving. Everything here is derived from the tree
rather than from intent; where a rule is enforced by a test, §5.4 names the test, and where it is
only true today, §5.4 says that too.

## 5.1 Level 1 — layers

Pragmatic hexagonal + UDF/MVVM in a **single `:app` module** (no premature modularity).
Dependencies point inward: `ui → domain`, `data → domain`, `domain → nothing`.

```
┌───────────────────────────────────────────────────────────────────────┐
│ ui (Compose) — screens, ViewModels, state hoisting (UDF/MVVM)         │
│   Explorer (+ Recents) · Editor (notebook) · FileEditor · Terminal    │
│   collectAsStateWithLifecycle ← StateFlow                             │
└───────────────▲──────────────────────────────────┬────────────────────┘
                │ uses (domain types + ports)      │ drives
┌───────────────┴──────────────────────────────────▼────────────────────┐
│ domain (pure Kotlin, zero android.* imports — guard-tested)           │
│   model/  — Space, KernelSpec, Notebook, Cell, CellOutput, …          │
│   port/   — NotebookRepository, WorkspaceRepository, KernelGateway, … │
└───────▲───────────────────────────────────────┬───────────────────────┘
        │ implemented by                        │ implemented by
┌───────┴───────────────────────────────────────▼───────────────────────┐
│ data — adapters over ports                                            │
│   fake/   FakeNotebookRepository, FakeKernelGateway, …                │
│   disk/   DiskNotebookRepository · DiskWorkspaceRepository · …        │
│   space/  rootfs installer, jupyter_server launcher, kernel WebSocket │
└───────────────────────────────────────────────────────────────────────┘
┌───────────────────────────────────────────────────────────────────────┐
│ di — AppContainer: manual composition root, one per *process*         │
└───────────────────────────────────────────────────────────────────────┘
```

### Rules

- **domain** imports nothing project-local and no `android.*`. It may use kotlinx-coroutines `Flow`
  and `java.time`.
- **ui** reads ports through the container or constructor injection; it never constructs a `data`
  implementation directly.
- **data** implements the ports.
- The rootfs and the `jupyter_server` process are **adapters behind ports** — nothing outside `data`
  knows a process exists. The cell editor is Compose-native and lives in `ui`.

## 5.2 Level 2 — where each package goes, and why

Those four are not the whole tree. Ten packages sit under `com.jupydroid.app`, plus the root itself,
which holds the app shell. The rule that places them is **what a package is allowed to depend on**,
which is checkable.

| Package | What it is | Placement rule |
|---|---|---|
| `domain/{model,port}` | the app's own types and its ports | no `android.*`, no other layer — **guard-tested** |
| `data/{disk,fake,intent,space}` | adapters implementing ports | one file per adapter, named `<Mechanism><PortConcept>` |
| `ui/{editor,explorer,fileeditor,terminal}` | screens and ViewModels | reads ports; never constructs an adapter |
| `ui/shell` | the chrome that belongs to **no** screen — the activity bar and the side panels | drawn by `Navigation` beside the panes rather than inside one; the state it moves stays at the root with `Navigation.kt` |
| `di` | `AppContainer`, the composition root | constructed once per process, in `JupydroidApplication` |
| `glob`, `html`, `import`, `markdown`, `syntax`, `terminal` | **document and language logic** — a glob pattern as the regex it denotes, parsing HTML into blocks, an import's title, commonmark, the ANTLR lexer, and the VT screen a shell writes into | **pure JVM, zero `android.*`.** Not `domain/`, because they model *documents and languages* rather than this app's domain; not `data/`, because they implement no port. Every test for them is a plain JVM test |
| `richtext` | **mixed on purpose**: `RichBlock` is the model, `RichInlineString` renders it | the split is the point — the model is testable without Compose, and only the renderer imports it |
| `theme` | Compose design tokens on VS Code's resolved Dark/Light palette (`ColorRamp.kt`) | Compose throughout; `EditorPaletteTest` pins its contrast ratios |
| root — `JupydroidApplication`, `MainActivity`, `Navigation`, `PaneDirective`, `NotebookBackStack`, `SidePanel`, `DocumentOutline`, the four `NavKey`s | the app shell and its navigation | see [runtime-views.md §6.1](runtime-views.md) |

**The practical test for a new file:** if it needs Compose it belongs in `ui/` or `theme/`; if it
implements a port it belongs in `data/`; if it is one of the app's own nouns it belongs in `domain/`;
and if it is none of those — a parser, a format, a language — it gets a package of its own beside
`syntax/` and stays free of `android.*`.

**The android-free claim is counted, not asserted.** An earlier revision of this document stated the
per-package counts in prose and they drifted every time a package gained a file — the prose itself
recorded being corrected twice before being corrected again. The count is now **produced**:

```sh
for p in glob html import markdown syntax terminal theme richtext; do
  d=app/src/main/java/com/jupydroid/app/$p
  printf '%-10s %s of %s import android.*/androidx.*\n' "$p" \
    "$(grep -rlE '^import (android|androidx)\.' $d | wc -l)" "$(ls $d/*.kt | wc -l)"
done
```

The six document-and-language packages read 0. `theme` and `richtext`'s renderer are Compose by
design. `ArchitectureGuardTest` covers the `domain` half of this claim, so what can go stale in the
command's output is the denominator, not the rule.

`terminal` is the one departure a reference forced a choice on: Termux's `TerminalEmulator` reaches
for `android.util.Base64` on its OSC path, where `java.util.Base64` does the same job — the strict
decoder, matching the reference's own shim rather than `getMimeDecoder()`. Its
`TerminalColors.java` likewise imports `android.graphics.Color` for a perceived-brightness helper
only its theme file calls, which is not transcribed.

## 5.3 Ports

Defined in `domain/port/`; each is contract-tested and has a real implementation.

| Port | Purpose | Real implementation |
|---|---|---|
| `NotebookRepository` | read/write one notebook | `DiskNotebookRepository` over nbformat JSON |
| `WorkspaceRepository` | list/read/write/rename/delete in the shared tree, and a change stream | `DiskWorkspaceRepository` |
| `WorkspaceSearcher` | find a query across the whole tree, and replace it there | `DiskWorkspaceSearcher` |
| `RecentDocumentsRepository` | what was opened, persisted outside the workspace | `DiskRecentDocumentsRepository` |
| `SpaceRepository` | the Spaces on this device — list/create/rename/delete, as directories under `files/spaces/` | `DiskSpaceRepository`, scanning the tree on every call |
| `KernelGateway` | connect/execute/interrupt/restart/complete/history against a kernel session | `RealKernelGateway` over the kernel WebSocket, behind `BaseSpaceKernelGateway` |
| `KernelDebugger` | DAP (`debug_request`/`debug_reply`) over the kernel's control channel — breakpoints, step/continue, call stack, variables | `RealKernelDebugger` over the same session's `KernelSockets`, behind `BaseSpaceKernelDebugger` |
| `KernelSessions` | start, list, reattach and end kernel sessions on the server | `BaseSpaceKernelSessions` over `PerRunningServer<KernelSockets>` |
| `KernelSpecs` | the kernelspec catalogue — live `GET api/kernelspecs`, the Space's declared list when the server is stopped | `BaseSpaceKernelSpecs` |
| `LanguageServerPort` | LSP over stdio — diagnostics, completion, hover, signature help, definition, format, rename | `StdioLanguageServer` against `pylsp` in the rootfs |
| `TerminalGateway` | open, write to, resize and close a terminado shell | `RealTerminalGateway`, behind `ActivityTrackedTerminalGateway` so a live terminal holds the server up |
| `ThemeSettingsRepository` | the app-wide theme preference | `DiskThemeSettingsRepository` |
| `LauncherSettingsRepository` | the launcher's own preferences | `DiskLauncherSettingsRepository` |
| `ServerLauncher` | start/stop the `jupyter_server` process for a Space | `JupyterServerLauncher` → `ProcessBuilder` → rootfs dynamic loader |
| `EditorSettingsRepository` | the app-wide editor preferences | `DiskEditorSettingsRepository` |
| `TerminalSettingsRepository` | the app-wide terminal preferences | `DiskTerminalSettingsRepository` |
| `ExplorerSettingsRepository` | the app-wide explorer preferences | `DiskExplorerSettingsRepository` |
| `NotebookExporter` | list nbconvert's formats, convert one notebook | `OkHttpNotebookExporter` over the server's nbconvert endpoints, behind `BaseSpaceNotebookExporter` |
| `DocumentSharer` | offer a workspace file to another app | `IntentDocumentSharer` → `FileProvider` + `ACTION_SEND` |

Every port also has a `Fake…` in `data/fake/`, which is what `AppContainer`'s defaults are — tests
get one for free, and an x86_64 device, where no Space can run, keeps the fake kernel so the UI still
works. That one-to-one is checkable rather than asserted:

```sh
P=app/src/main/java/com/jupydroid/app/domain/port
diff <(grep -lE '^(interface|fun interface) ' $P/*.kt | xargs -n1 basename | sed 's/\.kt$//' | sort) \
     <(ls app/src/main/java/com/jupydroid/app/data/fake/Fake*.kt | xargs -n1 basename | sed 's/^Fake//;s/\.kt$//' | sort)
```

**Prefer a uniform port surface over case-by-case minimalism.** A single-implementation port stays a
port when it keeps every boundary and operation discoverable in one place; that consistency is the
map a newcomer navigates by, not ceremony.

**Multiple clients of the Space's server, and one object that starts it.** `BaseSpaceServer` owns the
lazy install-and-start, and `PerRunningServer` gives each client one instance per server —
`BaseSpaceKernelGateway` and `BaseSpaceNotebookExporter` each their own. This is not layering for its
own sake: `JupyterServerLauncher.start` begins with `stop()`, so a second starter would kill the
kernel's server.

**One of those instances is itself shared by two clients.** `BaseSpaceKernelGateway` and
`BaseSpaceKernelDebugger` do not each build their own `PerRunningServer<KernelSockets>` — they are
handed the *same* one, built once as `baseSpaceKernelSockets`, because a `debug_request` on the
control channel reuses the identical per-session socket that `execute`, `complete` and `history`
already share rather than opening a second one.

`Space` is a **first-class domain type** (`files/spaces/<id>/`), not a string. Named after SageMaker
Studio rather than "environment", which would collide with the Python environments living inside one.

## 5.4 Dependency rules — what is enforced, and what is only true

`ArchitectureGuardTest` enforces the domain layer's purity, and only that. The other three rules hold
today — checked by grep, zero hits each — with nothing keeping them true.

| Rule | Status |
|---|---|
| `domain` imports no `android.*` | **enforced** — `ArchitectureGuardTest.domainLayer_hasSources_withZeroAndroidImports` |
| `domain` imports no `data`/`ui`/`di` | **enforced** — `ArchitectureGuardTest.domainLayer_doesNotImportOtherLayers` |
| `ui` never imports `data` | true today, **unguarded** |
| `data` never imports `ui` | true today, **unguarded** |
| `AppContainer` is the only place that constructs implementations | true today, **unguarded** — `JupydroidApplication` builds the container; nothing else builds an adapter |

Extending the guard to the last three is a code change, tracked as `GAP-006` in
[`../architecture.md` §11](../architecture.md). It would go green immediately, which is the point of
a regression gate.

## 5.5 Domain data model

```
Space(id, name, kernels: List<KernelSpec>)
KernelSpec(name, displayName, language)

// What a person configured about a Space, as against what its rootfs advertises. `Space` is the
// identity a kernel and a server are keyed by; this is `files/spaces/<id>/config.json`. The id is
// the directory's own name and is deliberately NOT in the file: the scan is the only source of it,
// which is what makes `space-2` unable to disagree with the directory called `space-2`.
SpaceConfig(id, name, created: Instant, pythonVersion,
            requestedPackages: List<String>, extra: JsonObject)

Notebook(id, title, kernelId, cells: List<Cell>, preserved: PreservedJson)
Cell(id, kind, source, outputs, executionCount,
     sourceHidden, outputsHidden, preserved: PreservedJson)
CellKind { CODE, MARKDOWN }

// Deliberately NOT on Notebook: when it was last written is a fact about the file.
// It is WorkspaceEntry.updatedAt, read off the file's mtime — the reference's
// `last_modified`. PreservedJson is nbformat's open-format remainder, kept verbatim.
WorkspaceEntry(path, name, kind, updatedAt: Instant, …)
RecentDocument(path, title, openedAt)

CellOutput (sealed)
  Stream(name: StreamName, text)             // stdout/stderr
  Error(name, value, traceback)
  DisplayData(mimeBundle)                    // mime -> payload
  ExecuteResult(mimeBundle, executionCount)
  ClearOutput

KernelSession(id, spaceId, kernelId)
KernelEvent (sealed)
  ExecutionCount(cellId, count)
  Output(cellId, output: CellOutput)
  Idle                                       // kernel done with this cell
CompletionResult(matches, replacedStart, replacedEnd)

ServerConnection(baseUrl, port, token)
```

`ActiveCellRuns` (`domain/model/`) counts the runs in flight across every open notebook:
`NotebookKernel` moves it, and `IdleServerStopper` (`data/space/`, driven by `JupydroidApplication`
from `ProcessLifecycleOwner`) reads it before stopping the server. It is in `domain` because both
`ui` and `di` touch it and `ui ↛ data` — a counter of the app's own runs is one of the app's own
nouns, even though nothing serialises it.

## 5.6 Deliberate simplifications

- One module, manual DI, constructor injection defaulting to fakes — no DI framework, no
  multi-module Gradle split until a real boundary appears.
- Recents is a history of what was *opened*, persisted outside the workspace and validated against
  it — not a scan of a directory, so it survives files moving.
- `KernelEvent.Idle` is the kernel-busy signal: the ViewModel derives Running/Idle from the event
  stream, so Run All and per-cell busy indicators need no extra state.
- The workspace is read from disk directly rather than through the server's Contents API — it is
  faster and works before the server is up. Server-side *algorithms* (checkpoints, notebook trust,
  nbconvert) are not re-implemented.
