# JupyterDroid — Architecture

Offline Jupyter notebook app for Android. This document is the contract for **what lives
where**. Everything in it is derived from the tree rather than from intent; where a rule is
enforced by a test, §5 says which, and where it is only true today, §5 says that too.

What is settled and why is `docs/DECISIONS.md`; what gets built next is `docs/PLAN.md`.

Source material: `reference/xeus-android/FEASIBILITY.md`, `reference/xeus-android/STUDY_CASES.md`.

---

## 1. Layers

Pragmatic hexagonal + UDF/MVVM in a **single `:app` module** (no premature modularity).
Dependencies point inward: `ui → domain`, `data → domain`, `domain → nothing`.

```
┌───────────────────────────────────────────────────────────────────────┐
│ ui (Compose) — screens, ViewModels, state hoisting (UDF/MVVM)         │
│   Explorer (+ Recents) · Editor (notebook) · FileEditor               │
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

### Where each package goes, and why

Those four are not the whole tree. Ten packages sit under `com.jupydroid.app` — plus the root
itself, which holds the app shell — and the rule that places them is **what a package is allowed
to depend on**, which is checkable:

| Package | What it is | Placement rule |
|---|---|---|
| `domain/{model,port}` | the app's own types and its ports | no `android.*`, no other layer — **guard-tested** |
| `data/{disk,fake,intent,space}` | adapters implementing ports | one file per adapter, named `<Mechanism><PortConcept>` |
| `ui/{editor,explorer,fileeditor}` | screens and ViewModels | reads ports; never constructs an adapter |
| `di` | `AppContainer`, the composition root | constructed once per process, in `JupydroidApplication` |
| `html`, `import`, `markdown`, `syntax` | **document and language logic** — parsing HTML into blocks, an import's title, commonmark, the ANTLR lexer | **pure JVM, zero `android.*`.** Not `domain/`, because they model *documents and languages*, not this app's domain; not `data/`, because they implement no port. JVM-testable, and every test for them is a plain unit test |
| `richtext` | **mixed on purpose**: `RichBlock` is the model, `RichInlineString` renders it | the split is the point — the model is testable without Compose, and only the renderer imports it |
| `theme` | Compose design tokens, the IntelliJ-derived palette | Compose throughout; `EditorPaletteTest` pins its contrast ratios |
| root — `JupydroidApplication`, `MainActivity`, `Navigation`, `PaneDirective`, `NotebookBackStack`, the three `NavKey`s | the app shell and its navigation | see §4 |

Measured, not asserted: `html` (0 of 3 files), `import` (0/1), `markdown` (0/1) and `syntax`
(0/6) import `android.*`; `theme` imports it in 5 of 5; `richtext` in 1 of 2. That 1 is
`RichInlineString.kt`, and it is the only file in those six packages that touches Compose
besides `theme`.

**The practical test for a new file:** if it needs Compose it belongs in `ui/` or `theme/`; if
it implements a port it belongs in `data/`; if it is one of the app's own nouns it belongs in
`domain/`; and if it is neither — a parser, a format, a language — it gets a package of its own
beside `syntax/` and stays free of `android.*`.

### Rules

- **domain** imports nothing project-local and no `android.*`. It may use kotlinx-coroutines
  `Flow` and `java.time`.
- **ui** reads ports through the container or constructor injection; it never constructs a
  `data` implementation directly.
- **data** implements the ports.
- The rootfs and the `jupyter_server` process are **adapters behind ports** — nothing outside
  `data` knows a process exists. The cell editor is Compose-native and lives in `ui`; the
  per-cell CodeMirror WebViews an earlier version of this document described were retired on
  measurement (`docs/DECISIONS.md`).

## 2. Ports (domain)

Defined in `domain/port/`; each is contract-tested and has a real implementation.

| Port | Purpose | Real impl |
|---|---|---|
| `NotebookRepository` | read/write one notebook | `DiskNotebookRepository` over nbformat JSON |
| `WorkspaceRepository` | list/read/write/rename/delete in the shared tree, and a change stream | `DiskWorkspaceRepository` |
| `RecentDocumentsRepository` | what was opened, persisted outside the workspace | `DiskRecentDocumentsRepository` |
| `KernelGateway` | connect/execute/interrupt/restart/complete/history against a kernel session | `RealKernelGateway` over the kernel WebSocket, behind `BaseSpaceKernelGateway` |
| `ServerLauncher` | start/stop the `jupyter_server` process for a Space | `JupyterServerLauncher` → `ProcessBuilder` → rootfs dynamic loader |
| `EditorSettingsRepository` | the app-wide editor preferences | `DiskEditorSettingsRepository` |
| `NotebookExporter` | list nbconvert's formats, convert one notebook | `OkHttpNotebookExporter` over the server's nbconvert endpoints, behind `BaseSpaceNotebookExporter` |
| `DocumentSharer` | offer a workspace file to another app | `IntentDocumentSharer` → `FileProvider` + `ACTION_SEND` |

Each port also has a `Fake…` in `data/fake/`, which is what `AppContainer`'s defaults are —
tests get one for free, and an x86_64 device (where no Space can run) keeps the fake kernel so
the UI still works.

**Two clients of the Space's server, and one object that starts it.** `BaseSpaceServer` owns the
lazy install-and-start, and `PerRunningServer` gives each client one instance per server -
`BaseSpaceKernelGateway` and `BaseSpaceNotebookExporter` today, a terminal and a language server
later. This is not layering for its own sake: `JupyterServerLauncher.start` begins with `stop()`,
so a second starter would kill the kernel's server (`docs/DECISIONS.md`).

`Space` is a **first-class domain type** (`files/spaces/<id>/`), not a string. Named after
SageMaker Studio rather than "environment", which would collide with the Python environments
living inside one.

## 3. Data model (domain)

```
Space(id, name, kernels: List<KernelSpec>)
KernelSpec(id, displayName)

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
  DisplayData(mimeBundle)                     // mime -> payload
  ExecuteResult(mimeBundle, executionCount)
  ClearOutput

KernelSession(id, spaceId, kernelId)
KernelEvent (sealed)
  ExecutionCount(cellId, count)
  Output(cellId, output: CellOutput)
  Idle                                         // kernel done with this cell
CompletionResult(matches, replacedStart, replacedEnd)

ServerConnection(baseUrl, port, token)
```

## 4. Navigation

Navigation3 (`androidx.navigation3`), not Jetpack Navigation. Lives at the root package because
it wires `ui/` screens together and belongs to none of them.

**Three destinations, and there is no Home.** `Explorer`, `FileEditor`, `Editor` — declared as
entries in `Navigation.kt` (`entry<Explorer>` at :123, `entry<FileEditor>` at :140,
`entry<Editor>` at :150). The explorer is the first screen; the notebook-list Home it replaced
was removed in X2h, and Recents became a section of the explorer.

**One opener, not one per screen.** `rememberDocumentOpener` sends a `.ipynb` to the notebook
editor and anything else to the plain one, and records the recent. That is the reference's own
split — a single `docmanager:open` where the path picks the factory, the notebook factory claims
its extension, and the file editor is the default for the rest.

**Two panes pair on metadata, never on position.** `ListDetailSceneStrategy` puts the explorer in
the list pane and whatever it opened in the detail, at ≥ 840dp. Which entry is which is declared
when the entry providers are built — `ListDetailSceneStrategy.listPane()` on `Explorer`,
`.detailPane()` on `Editor` and `FileEditor` — because `NavEntry` does not expose its key. "The
last two entries" was the earliest rule and it is a montage, not a pairing: it put Recents beside
the file browser, two surfaces neither of which opens the other.

Both panes stay **separate `NavEntry`s**, which is why this is a scene strategy and not
`NavigableListDetailPaneScaffold`; `docs/DECISIONS.md` has what that buys and what the swap cost.
Two knobs are set rather than defaulted, and both are load-bearing: the directive is forced to one
partition when the caller says one pane (the test seam), and `BackNavigationBehavior.PopLatest`
pops one entry where the default popped every one of them.

**Each document gets its own ViewModel *and* its own lifetime.** Two entry decorators, in this
order (`Navigation.kt`, the `entryDecorators` argument):

```kotlin
entryDecorators = listOf(
  rememberSaveableStateHolderNavEntryDecorator(),
  rememberViewModelStoreNavEntryDecorator(),
)
```

`NavDisplay`'s default is the saveable-state decorator **alone**, which leaves the
`ViewModelStoreOwner` inside an entry as the Activity — so `viewModel {}` keys on the class name
and hands the second notebook the first one's ViewModel, id and all. That was defect D1. Order is
load-bearing: `decorateEntry` folds right, so the ViewModel decorator must sit inside the
saveable one, which is where it reads its `SavedStateRegistryOwner` from.

The lifetime half matters as much as the identity half: because each entry owns a
`ViewModelStore`, disposing an entry runs `EditorViewModel.onCleared`, which flushes the last
edit and closes the kernel socket. Nothing closed a socket before this existed.

**One document at a time.** `NotebookBackStack` reveals rather than reopens (JupyterLab's
`openOrReveal`), and a *different* document replaces the open one instead of stacking. Both
decorators key on `NavEntry.contentKey`, so two entries for one path would share a single
`ViewModelStore` and a single `SaveableStateHolder` slot — a double tap is enough to cause it.

**Work that outlives the screen** goes on `AppContainer.applicationScope`, not `viewModelScope`:
recording a recent, and flushing a notebook's last edit while its screen is being disposed. By
then `viewModelScope` is already cancelled. An external scope belongs to something longer-lived
than a screen, which is what a composition root is.

**The container is the process's, not the activity's.** `JupydroidApplication` owns it and
`MainActivity` reads it. That is not a preference: the platform relaunches the activity on every
rotation, and while the container hung off the activity each relaunch built a second one - and a
second `ServerLauncher`, which reopening a notebook turned into a second `jupyter_server`
(`evidence/p1g-one-container-per-process/`). `CompositionRootTest` recreates the activity and
asserts the container survives.

**State that belongs to the app rather than to a screen or a port** also lives on the container,
and there is exactly one shape of it. `ActiveCellRuns` (`domain/model/`) counts the runs in flight
across every open notebook: `NotebookKernel` moves it, `IdleServerStopper` (`data/space/`,
driven by `JupydroidApplication` from `ProcessLifecycleOwner`) reads it before stopping the
server. It is
in `domain` because both `ui` and `di` touch it and `ui ↛ data` — a counter of the app's own runs
is one of the app's own nouns, even though nothing serialises it.

## 5. Dependency rules — what is enforced, and what is only true

`ArchitectureGuardTest` enforces the domain layer's purity, and only that. The other two rules
hold today — checked by grep, zero hits each — with nothing keeping them true.

| Rule | Status |
|---|---|
| `domain` imports no `android.*` | **enforced** — `ArchitectureGuardTest.domainLayer_hasSources_withZeroAndroidImports` |
| `domain` imports no `data`/`ui`/`di` | **enforced** — `ArchitectureGuardTest.domainLayer_doesNotImportOtherLayers` |
| `ui` never imports `data` | true today, **unguarded** |
| `data` never imports `ui` | true today, **unguarded** |
| `AppContainer` is the only place that constructs implementations | true today, **unguarded** (`JupydroidApplication` builds the container; nothing else builds an adapter) |

Extending the guard to the last three is a code change and is tracked as an open risk in
`docs/PLAN.md`. It would go green immediately, which is the point of a regression gate.

## 6. Deliberate simplifications

- One module, manual DI, constructor injection defaulting to fakes — no DI framework, no
  multi-module Gradle split until a real boundary appears.
- Recents is a history of what was *opened*, persisted outside the workspace and validated
  against it — not a scan of a directory, so it survives files moving.
- `KernelEvent.Idle` is the kernel-busy signal: the ViewModel derives Running/Idle from the
  event stream, so Run All and per-cell busy indicators need no extra state.
- The workspace is read from disk directly rather than through the server's Contents API — it
  is faster and works before the server is up. Server-side *algorithms* (checkpoints, notebook
  trust, nbconvert) are not re-implemented; see `docs/DECISIONS.md`.

---

## Appendix A — the original Step-1 wireframes

**Historical.** These are the sketches the project started from, kept for intent. The shipped UI
has diverged: the file **Explorer** replaced the Home/Recents screen (Recents is a section of it),
the cell editor is Compose-native rather than CodeMirror, and cell actions hang off a `⋮` button
on the cell header rather than a long press — a long press on a cell is `BasicTextField`'s text
selection. Console, Workspace-as-a-screen and Settings are **not built**; see `docs/PLAN.md`
P4/P6.

### A.1 Home — Recent
```
┌──────────────────────────────┐
│ JupyterDroid            [ ＋ ] │
│                              │
│  Recents                     │
│  ◗ Untitled.ipynb      5m ago │
│  ◗ Analysis.ipynb  Yesterday │
│  ◗ scratch.py         3d ago │
│                              │
│  (empty state)               │
│  "No recent notebooks yet."  │
│                              │
│  long-press row → rename/    │
│  delete (Step 2)             │
└──────────────────────────────┘
```

### A.2 Editor — cells
```
┌──────────────────────────────┐
│ ◀ Untitled.ipynb   [Run all] │
│ ┌──────────────────────────┐ │
│ │ In [1]:  2 + 2           │ │   code cell, self-sizing
│ │                          │ │   editor (CodeMirror 6)
│ │ Out[1]:  4               │ │   output area under the cell
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ In [2]:  import numpy    │ │
│ │          np.arange(3)    │ │
│ │                          │ │
│ └──────────────────────────┘ │
│                              │
│ long-press cell → run,       │
│ insert above/below, move,    │
│ duplicate, delete, collapse  │
└──────────────────────────────┘
```

### A.3 Console — REPL sharing the notebook's kernel
```
┌──────────────────────────────┐
│ Console                      │
│ >>> 1 + 1                    │
│ 2                            │
│ >>> import numpy as np       │
│ >>> np.arange(4)             │
│ array([0, 1, 2, 3])          │
│ ────────────────────────────  │
│ ⌨ type here  (Enter = run,  │
│    Shift+Enter = newline)    │
└──────────────────────────────┘
```

### A.4 Workspace — files
```
┌──────────────────────────────┐
│ Workspace             [⤓ zip] │
│  notebooks/                  │
│    Untitled.ipynb            │
│  data/                       │
│    stocks.csv                │
│  output/                     │
│    figure.png                │
│                              │
│  [＋ Data]  [＋ Folder]      │
└──────────────────────────────┘
```

### A.5 Settings
```
┌──────────────────────────────┐
│ Settings                     │
│ Theme            [ Dark   ▾ ]│
│ Accent           [ Violet ▾ ]│
│ Editor font size  [   14   ] │
│ Wrap lines            [✓]    │
│ Code completion      [✓]     │
│ Symbol bar           [✓]     │
│ Background autosave  [✓]     │
│ ──────────────────────────── │
│ Spaces                       │
│  ◗ base (bundled, default)   │
└──────────────────────────────┘
```
