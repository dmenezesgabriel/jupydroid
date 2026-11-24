# ADR-0174 — A run in flight is handed to a retained holder when its screen dies

Status: accepted
Date: 2026-11-24

## Context

An `EditorViewModel` lives in its `NavEntry`'s `ViewModelStore`, so anything that removes the
entry runs `onCleared`: `leaveWorkspace()` drops every workspace entry for the Spaces home, a
tab's ✕ removes its own, and Back at one pane pops the document outright
(`app/src/main/java/com/jupydroid/app/SpacesHome.kt`,
`app/src/main/java/com/jupydroid/app/MainAreaTabs.kt`). Until this change `onCleared` closed the
kernel unconditionally, so a cell mid-run lost its collector, its state flow and its socket with
the screen — even though the kernel process kept running server-side and `POST api/sessions`
reattaches it by path with its namespace intact
(`app/src/main/java/com/jupydroid/app/data/space/KernelLifecycle.kt`, ADR-0022).

What the references do with the same shape of death:

- **JupyterLab** disposes the widget's context on close: `SessionContext.dispose()` drops only
  the client connection unless `shutdownOnDispose` is set, so the kernel survives — but the
  execution state lived on the widget's model and died with it
  (jupyterlab@7f0e6906 `packages/apputils/src/sessioncontext.tsx`, `dispose`).
- **VS Code** keeps the notebook model only while it is dirty or referenced: a streaming
  notebook is dirty, so its model and execution state can outlive its editors — but a clean one
  is disposed, and the client-side stream dies with the document either way
  (vscode@3f3380c6
  `src/vs/workbench/contrib/notebook/common/notebookEditorModelResolverServiceImpl.ts`).
- **vscode-jupyter** keeps `CellExecution` objects in the extension host keyed to the kernel
  session — the kernel outlives the editor, the client-side stream does not
  (vscode-jupyter@f01d80ca `src/kernels/execution/cellExecution.ts`).

No surveyed product keeps client-side run state across document close. This app asks for more
than any of them deliberately: a run ends only at the cell's or kernel's own Stop, or when the
Space stops.

## Decision drivers

- Only Stop or the Space's stop may end a run — the capability the item names.
- Retention must not change the common case: an idle detached kernel still disconnects on leave,
  because reattach by path is cheap and holding every detached socket open accumulates
  connections nobody can reach.
- The collectors a run owns must not leak: they run on `applicationScope`, which outlives both
  the screen and the holder, so the holder owns an explicit lifetime `Job` every exit path
  cancels.
- The holder must outlive any one `NavEntry`'s store but not the process: kernels are the app's
  children and die with it ([CLOSED-001](../research/closed-questions.md)), so process scope
  would hold sockets and state nobody can ever reach again.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep the run on the per-screen `EditorViewModel` (status quo) | Nothing to build | `viewModelScope` dies mid-`collect` on every entry-removing navigation; the run's state freezes and its socket closes |
| Retain every detached session's socket | Simplest detach rule | Every notebook ever opened-and-left keeps a WebSocket and a kernel binding; the idle case pays for the rare one |
| Process scope (`AppContainer`) | Outlives everything short of death | Outlives the activity that could adopt it, and process death kills the kernels anyway — state retained past its usefulness |
| **Activity-retained `ViewModel` holding a per-document execution** | Exactly "outlives a screen, not the process"; the same lifetime call `RetainedOpenDocuments` and `RetainedConsole` already make (ADR-0092) | One more retained registry to keep honest |

## Decision

**`EditorViewModel.onCleared` asks whether a run is in flight — `runInFlight` in the state flow
or `cellRunsInFlight` on the kernel, the second read covering the window where a finished cell's
checkpoint is still writing — and either closes the kernel as before or hands it to
`NotebookExecutions`, an activity-retained registry keyed by `NotebookExecutionKey(spaceId,
DocumentOpening)`.**

- The holder (`app/src/main/java/com/jupydroid/app/ui/editor/NotebookExecution.kt`) keeps the
  kernel, its debugger, the `MutableStateFlow<EditorUiState>` the collectors write, the
  `applicationScope`, the detached file follow's `Job`, and a drain watch: the moment no
  execution is RUNNING or QUEUED **and** the kernel's own run-work count reaches zero, it calls
  `kernel.close(scope)` — a disconnect, never a shutdown, per ADR-0025 — and reports the drain so
  the registry drops its entry by conditional remove. Both drain conditions are load-bearing:
  `finishedRun` lands in the state before `checkpoint()` returns inside
  `NotebookCellRuns.runCells`, and `lifetime.cancel()` would take that write with it; the count
  is the kernel's own rather than `ActiveCellRuns`' app-wide one, so a second notebook's run
  never holds this one's drain.
- A reopening `EditorViewModel` for the same `(Space, DocumentOpening)` adopts the holder
  (`NotebookExecutions.adopt`, matched by `speaksFor` — same Space, the file where it lives now,
  not gone — so a rename mid-detach is found under its new name and a recreated file at the old
  path is not), takes its flow and kernel as its own state, seeds fresh disk state merged with
  the retained run fields, and rebinds checkpoint and kernel-bound work to itself. Live events
  reach the new screen unchanged; Stop and input answers work mid-run.
- While detached, the kernel-side collectors — session ended/rebound, run collection,
  explain-miss, debug watch, diagnostics, history, rename-follow — run on `applicationScope`
  under the lifetime `Job`, and the file follow keeps running through the dead screen's own
  `adopt`/`stopWriting`, so a rename or delete announced mid-run still reaches the path the
  checkpoint writes. `shutdownKernelOnClose` and the checkpoint's autosave read the settings
  repository's flow directly rather than the screen's frozen copy.

## Consequences

A run now survives every way its screen can die — the explorer, the Spaces home, another tab,
Settings, the tab's ✕, and the activity's recreation on rotation — and a reopening screen finds
it still RUNNING with outputs streamed so far. What it makes easy: the drain rule is one place
(`NotebookExecution.watchDetached`), and adoption is one call in `EditorViewModel`'s init.

What it makes hard, knowingly: an idle detached kernel still disconnects exactly as before, so
a notebook reopened after its run ended pays the same cheap `POST api/sessions` reattach it
always did — there is no client-side run state to hand back because there is no run. A detached
run has no stop of its own: it is stopped by reopening and stopping it, or by stopping the
Space, which is the item's own boundary. Process death still ends everything — the holder is
activity-scoped precisely so nothing pretends otherwise.

The thing a future reader will be tempted to "fix": moving the registry to `AppContainer` so it
survives process recreation. It cannot help — the kernels it would retain are children of the
process and die with it ([CLOSED-001](../research/closed-questions.md)) — and it would hold
sockets and debugger state past the last screen that could adopt them.
