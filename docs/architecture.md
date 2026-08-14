# JupyterDroid — Architecture

Offline Jupyter notebook app for Android. This document defines the layers, ports and
data model. It is the contract for what lives where; the guard test in
`app/src/test/java/com/jupydroid/app/architecture/ArchitectureGuardTest.kt` enforces the
domain-layer purity rules.

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
│   port/   — NotebookRepository, KernelGateway, ServerLauncher         │
└───────▲───────────────────────────────────────┬───────────────────────┘
        │ implemented by                        │ implemented by
┌───────┴───────────────────────────────────────▼───────────────────────┐
│ data — adapters over ports (fakes first, real impls later)            │
│   fake/   FakeNotebookRepository, FakeKernelGateway, FakeServerLauncher│
│   disk/   DiskNotebookRepository · DiskWorkspaceRepository · …         │
│   space/  rootfs installer, jupyter_server launcher, kernel WebSocket  │
└───────────────────────────────────────────────────────────────────────┘
┌───────────────────────────────────────────────────────────────────────┐
│ di — AppContainer: manual composition root, built once in MainActivity │
└───────────────────────────────────────────────────────────────────────┘
```

Rules:
- **domain** imports nothing project-local and no `android.*` (enforced by
  `ArchitectureGuardTest`). It may use kotlinx-coroutines `Flow` and `java.time`.
- **ui** reads ports through the container or constructor injection; it never constructs
  a `data` implementation directly.
- **data** implements the ports; the container swaps fakes for real impls step by step.
- The rootfs and the `jupyter_server` process are **adapters behind ports** — nothing outside
  `data` knows a process exists. (The cell editor is Compose-native and lives in `ui`; the
  per-cell CodeMirror WebViews this line once described were retired on measurement, see
  `docs/DECISIONS.md`.)

## 2. Ports (domain)

Defined in `domain/port/`; each is contract-tested and has a real implementation.

| Port | Purpose | Real impl |
|---|---|---|
| `NotebookRepository` | read/write one notebook | `DiskNotebookRepository` over nbformat JSON |
| `WorkspaceRepository` | list/read/write/rename/delete in the shared tree, and a change stream | `DiskWorkspaceRepository` |
| `RecentDocumentsRepository` | what was opened, persisted outside the workspace | `DiskRecentDocumentsRepository` |
| `KernelGateway` | connect/execute/interrupt/restart/complete against a kernel session | `RealKernelGateway` over the kernel WebSocket, behind `BaseSpaceKernelGateway` |
| `ServerLauncher` | start/stop the `jupyter_server` process for a Space | `JupyterServerLauncher` → `ProcessBuilder` → rootfs dynamic loader |

`Space` is a **first-class domain type** (`files/spaces/<id>/`), not a string. Named
after SageMaker Studio rather than "environment", which would collide with the Python
environments living inside one.

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

## 4. Wireframes — the original Step-1 drawings

**Historical.** These are the sketches the project started from, kept for intent. The shipped UI
has diverged: the file **Explorer** replaced the Home/Recents screen (Recents is a section of it),
the cell editor is Compose-native rather than CodeMirror, and cell actions hang off a `⋮` button
on the cell header rather than a long press — a long press on a cell is `BasicTextField`'s text
selection. Console, Workspace-as-a-screen and Settings are **not built**; see `docs/PLAN.md`
P4/P6.

### 4.1 Home — Recent
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

### 4.2 Editor — cells
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

### 4.3 Console — REPL sharing the notebook's kernel
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

### 4.4 Workspace — files
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

### 4.5 Settings
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

## 5. Dependency rules (enforced)

- `domain` → nothing project-local; no `android.*`; no `data`/`ui`/`di` imports.
- `ui` → `domain`, `di`; never instantiates a `data` adapter.
- `data` → `domain`; never imports `ui`.
- The container is the only place that constructs implementations (`AppContainer`).

## 6. Deliberate simplifications

- One module, manual DI, constructor injection with defaults to fakes — no DI framework,
  no multi-module Gradle split until a real boundary appears.
- Recents is a history of what was *opened*, persisted outside the workspace and validated
  against it — not a scan of a directory, so it survives files moving.
- `KernelEvent.Idle` is the kernel-busy signal: the ViewModel derives Running/Idle from
  the event stream, so Run All and per-cell busy indicators need no extra state.
