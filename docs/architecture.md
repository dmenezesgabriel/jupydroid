# JupyterDroid — Architecture

Offline Jupyter notebook app for Android. This document defines the layers, ports,
data model, and interface wireframes, per Step 1 of `docs/PLAN.md`. It is the contract
for what lives where; the guard test in
`app/src/test/java/com/jupydroid/app/architecture/ArchitectureGuardTest.kt` enforces the
domain-layer purity rules.

Source material: `reference/xeus-android/FEASIBILITY.md`, `reference/xeus-android/STUDY_CASES.md`,
`reference/callisto/FINDINGS.md`.

---

## 1. Layers

Pragmatic hexagonal + UDF/MVVM in a **single `:app` module** (no premature modularity).
Dependencies point inward: `ui → domain`, `data → domain`, `domain → nothing`.

```
┌───────────────────────────────────────────────────────────────────────┐
│ ui (Compose) — screens, ViewModels, state hoisting (UDF/MVVM)         │
│   HomeRecent · Editor · Console · Workspace · Settings · Onboarding   │
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
│   disk/   DiskNotebookRepository (Step 3) · websocket (Step 10)        │
│   server/ ProcessBuilder + rootfs launcher (Step 8)                    │
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
- The WebView cell editors (CodeMirror 6) and the rootfs/`jupyter_server` process are
  **adapters behind ports** — nothing outside `data` knows a WebView or a process exists.

## 2. Ports (domain)

Defined in `domain/port/`; each has a fake (Steps 1–2) and a real implementation.

| Port | Purpose | Real impl | Lands |
|---|---|---|---|
| `NotebookRepository` | CRUD + observe list/summaries | `DiskNotebookRepository` over nbformat JSON | Step 3 |
| `KernelGateway` | execute/interrupt/complete against a kernel session | WebSocket client to `jupyter_server` | Step 10 |
| `ServerLauncher` | start/stop the `jupyter_server` process for a Space | `ProcessBuilder` → rootfs dynamic loader | Step 8 |

`Space` is a **first-class domain type** (`files/spaces/<id>/`), not a string. Named
after SageMaker Studio rather than "environment", which would collide with the Python
environments living inside one.

## 3. Data model (domain)

```
Space(id, name, kernels: List<KernelSpec>)
KernelSpec(id, displayName)

Notebook(id, title, kernelId, cells: List<Cell>, updatedAt: Instant)
Cell(id, kind: CellKind, source)
CellKind { CODE, MARKDOWN }
NotebookSummary(id, title, updatedAt)        // Home recent row

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
CompletionResult(matches)

ServerConnection(baseUrl, port)
```

## 4. Wireframes (Step 1 interface drawings)

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
- `NotebookSummary` exists so Home never deserializes every cell just to render recents.
- `KernelEvent.Idle` is the kernel-busy signal: the ViewModel derives Running/Idle from
  the event stream, so Run All and per-cell busy indicators need no extra state.
