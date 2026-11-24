# JD-100 — A running notebook survives leaving it

Milestone: `M11` · State: open · Depends on: — · Closes: —

## Outcome

A cell run keeps going when its notebook's screen goes away — to the explorer, the Spaces home,
another surface or tab, or the tab itself closed — and reopening the notebook shows the run still
in flight, streaming its outputs, or finished with its outputs and count already written. Only the
cell's or kernel's own Stop, or stopping the Space, ends it.

## Demo

On the arm64 tablet (`SM-X216B` — anything that runs a cell; x86_64 `dev36` echoes through
`FakeKernelGateway` and proves nothing, ADR-0003):

1. Cold start: install, launch, enter `base`, open a notebook (or create one).
2. In a code cell, run something slow and chatty — e.g. `import time` then a loop of `print(i)`
   with `time.sleep(1)` — and tap Run while it is still RUNNING.
3. While it runs, leave: back to the explorer (one pane), then the Spaces home (`leaveWorkspace`),
   then back into the notebook; repeat with a second surface (another tab, Settings) and with the
   tab's ✕ close followed by reopening the notebook from the explorer.
4. Each time the notebook is reopened the cell is still RUNNING with the output streamed so far;
   when the loop ends the outputs and `[n]` count are on the cell and in the file.
5. Run it again, leave, come back, and tap the cell's Stop — the run ends CANCELLED. Then leave a
   run going and stop the Space — the run ends with it.

Screenshots, UI dumps and `maestro.log` at every step into `evidence/<flow-name>/` (local,
ADR-0132). The item is not `shipped` until this has been run on that device.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `packages/apputils/src/sessioncontext.tsx` (`dispose`, ~679-709), `packages/notebook-extension/schema/tracker.json` (`kernelShutdown`, ~741), `packages/cells/src/widget.ts` (`model.executionState`, ~421-1864) | widget+context disposed on close → cell execution state and mid-run outputs lost; session disposed client-side only — `shutdownOnDispose` defaults false, so the kernel keeps running server-side |
| `vscode` | PARTIAL | `src/vs/workbench/contrib/notebook/common/notebookEditorModelResolverServiceImpl.ts` (`ReferenceCollection`, dirty auto-reference ~100-120), `src/vs/workbench/api/browser/mainThreadNotebookDocumentsAndEditors.ts` | the notebook model is reference-counted and a dirty model is never disposed, so a streaming (dirty) notebook's model and execution state can outlive its editors; the kernel session lives in the extension host regardless |
| `vscode-jupyter` | PARTIAL | `src/kernels/execution/cellExecution.ts`, `src/kernels/execution/cellExecutionQueue.ts`, `src/platform/notebooks/cellExecutionStateService.ts`, `src/kernels/kernelExecution.ts` | execution state rides the core `NotebookCell` (`executionSummary`, `NotebookCellExecutionState` Idle/Pending/Executing) and outputs go through `execution.appendOutput`; `CellExecution` objects live in the extension host keyed to the kernel session — the kernel outlives the editor, the client-side stream does not |
| `intellij-community` | ABSENT | — | the Jupyter notebook editor and its kernel session live in the proprietary PyCharm plugin; the community clone carries only language/preview plumbing under `jupyter/src/com/intellij/notebooks/jupyter/core/` |
| `termux-app` | ABSENT | — | a terminal emulator; no notebook and no kernel-protocol client |
| `winlator` | ABSENT | — | a container runtime; no notebook and no kernel-protocol client |
| the shipped rootfs | PARTIAL | `services/sessions/handlers.py` `SessionRootHandler.post` — `session_exists(path)` → `get_session(path)` (cited in `app/src/main/java/com/jupydroid/app/data/space/KernelLifecycle.kt:58-61`) | `POST api/sessions` reattaches by path, so session+kernel survive a client disconnect; the server keeps no client-side run state to hand back |

**Count:** 0 of 7 keep client-side run state across document close; 4 of 7 keep the kernel/session
alive. Every reference answers "the kernel survives, the progress does not" — JupyterLab loses it
outright, VS Code keeps it only while the dirty-model auto-reference happens to hold. This item
asks for more than any of them, so the retained-execution design is a deliberate step past the
references, not a port.

## Implementation inventory

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| Domain model | `packages/cells/src/widget.ts` — `model.executionState` (`idle`/`running`) lives on the cell model, disposed with the widget's context |
| Port / protocol | `packages/services/src/session/restapi.ts` — `POST api/sessions`; `packages/services/src/kernel/restapi.ts` |
| Adapter | `packages/apputils/src/sessioncontext.tsx` — `dispose()` (~679-709): `shutdownOnDispose` → `sessionManager.shutdown`, else `session.dispose()` drops the client connection only |
| UI entry point | `packages/notebook/src/widget.ts`, `packages/cellexecutor` — run state is widget/model state |
| Persisted state | `kernelShutdown` setting (`packages/notebook-extension/schema/tracker.json` ~741, default `false`) → `factory.shutdownOnClose` (`packages/notebook-extension/src/index.ts` ~2467) |
| Empty / error / loading states | n/a to this item |
| Accessibility specifics | n/a |
| Edge cases it handles | kernel kept alive across close by default; `kernelShutdown: true` opts into shutdown on close |

### vscode@3f3380c668d07649b953eb8278354b2f263ea6ff

| Concern | Files |
|---|---|
| Domain model | `src/vs/workbench/contrib/notebook/common/model/notebookTextModel.ts` — cells, outputs, execution state on the model |
| Port / protocol | n/a — core speaks no kernel protocol (extension's job) |
| Adapter | `src/vs/workbench/contrib/notebook/common/notebookEditorModelResolverServiceImpl.ts` — `ReferenceCollection`; `destroyReferencedObject` disposes only when `canDispose()` and no dirty auto-reference (~100-160) |
| UI entry point | `src/vs/workbench/contrib/notebook/browser/notebookEditorWidget.ts` — editor dispose does not itself dispose the model |
| Persisted state | dirty notebooks auto-acquire a reference (`onDidChangeDirty`, ~105-120) so unsaved work — including streaming output — is never disposed |
| Empty / error / loading states | n/a |
| Accessibility specifics | n/a |
| Edge cases it handles | model retained while dirty or referenced; disposed when clean and unreferenced — execution state survives only inside that window |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Domain model | `src/kernels/execution/cellExecution.ts` (`CellExecution`), `src/kernels/execution/cellExecutionQueue.ts` — the queue and per-cell task, extension-host lifetime |
| Port / protocol | `src/kernels/kernelExecution.ts`, `src/kernels/jupyter/` — kernel session + message handling |
| Adapter | `src/platform/notebooks/cellExecutionStateService.ts` — `NotebookCellExecutionState` events over core `NotebookCell`s |
| UI entry point | outputs via `execution.appendOutput` (`cellExecution.ts` ~300-330, `cellExecutionMessageHandler.ts`) into the core document |
| Persisted state | execution state on `cell.executionSummary`/core metadata, not the file |
| Empty / error / loading states | `NotebookCellStateTracker` (WeakMap, logging/tests only — `helpers.ts` ~85-103) |
| Accessibility specifics | n/a |
| Edge cases it handles | kernel session registry outlives editors; document disposal ends the client stream (`document.dispose()` → `removedDocuments`, `extHostNotebook.ts` ~605-625) |

### the shipped rootfs (ours)

| Concern | Files |
|---|---|
| Domain model | n/a — server-side only |
| Port / protocol | `services/sessions/handlers.py` `SessionRootHandler.post` — `session_exists(path)`/`get_session(path)` reattach (jupyter_server, unpinned; cited via `KernelLifecycle.kt:58-61`) |
| Adapter | `app/src/main/java/com/jupydroid/app/data/space/KernelLifecycle.kt`, `app/src/main/java/com/jupydroid/app/domain/port/KernelGateway.kt` (`disconnect` vs `shutdown`) |
| UI entry point | n/a |
| Persisted state | sessions/kernels are server processes — children of the app, die with it (`docs/research/closed-questions.md` CLOSED-001) |
| Empty / error / loading states | n/a |
| Accessibility specifics | n/a |
| Edge cases it handles | reconnect by path with namespace intact; idle backstop (`IdleServerStopper`) is ours, not the server's |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where run state lives | per-screen `EditorViewModel` (today) → dies with the entry; JupyterLab → widget state, lost on close; VS Code → model retained only while dirty/referenced | a per-screen owner cannot outlive the screen; a nav-entry-retention scheme breaks `isInWorkspace`/`enteredSpaceId` and the tab model, and cannot cover tab close | a retained per-document **execution holder** — session + run machinery + the uiState flow, on `applicationScope`, keyed by `(Space, DocumentOpening)` — the `Context`+`SessionContext` analogue that outlives the widget |
| The uiState flow | each VM its own `MutableStateFlow` → kernel writes die with it; one shared flow adopted by whichever VM is attached | a shared flow keeps every kernel write landing somewhere alive; adopting it wholesale would show stale document state | the holder owns the flow; a reopening VM **adopts** it and merges fresh disk state with the retained run fields — document fields refresh, run fields continue |
| Holder lifetime | `AppContainer` (process) vs activity-retained `ViewModel` (`RetainedOpenDocuments` pattern) | the process outlives a finished activity and would hold sockets/kernels nobody can reach; activity scope matches "outlives a screen" exactly, and process death kills kernels anyway (CLOSED-001) | activity-retained `ViewModel`, same pattern as `RetainedOpenDocuments`/`RetainedConsole` |
| Retention trigger | any open session vs run-in-flight only | keeping every detached session's socket open accumulates connections for notebooks nobody is running; the server already reattaches an idle session on `POST api/sessions` | retain while `executions` has RUNNING/QUEUED; an idle kernel disconnects on leave exactly as today — cheap reattach, no behaviour change for the common case |
| Coroutine scope | `viewModelScope` (dies with entry) vs `applicationScope` + explicit lifetime | kernel collectors on `viewModelScope` cannot survive detach; on `applicationScope` they must be cancelled explicitly on real close or they leak per notebook | `applicationScope` under a `Job` the close/teardown paths cancel — `NotebookKernel` already documents that `onCleared` runs after `viewModelScope` is cancelled |
| How alive on return | kernel-busy-only (JupyterLab-faithful, cheap) vs visible RUNNING + streaming outputs | the item says "keeps its run going" and the demo verifies "a run still alive" — a busy kernel with no visible run is the weaker reading, and VS Code's dirty-retention shows the stronger one is the expected UX | full run state: `executions`, `outputs`, `executionCounts`, `inputRequests`, `clearsPending`, kernel fields — live, not a snapshot |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Run survives covered entries (explorer, other tabs, Console… in-workspace) | present — per-entry `ViewModelStore` keeps `EditorViewModel`+`viewModelScope` alive beneath the top (`Navigation.kt`, `rememberViewModelStoreNavEntryDecorator`) | n/a — same shape as VS Code's retained model | complete for this leg; device-verified in the demo |
| Run survives `leaveWorkspace()` → Spaces home | **absent** — `SpacesHome.kt` `leaveWorkspace()` `retainAll { it == SpacesHome }` drops every entry → `EditorViewModel.onCleared` (`EditorViewModel.kt:391`) → `viewModelScope` cancelled mid-`collect`, `kernel.close` → `session.forget()` + `disconnect` | JupyterLab keeps kernel, loses progress (`sessioncontext.tsx:679-709`) | the core defect |
| Run survives tab ✕ close | **absent** — same `onCleared` path | same as above | same mechanism; the item says "leaving it", and the screen cannot tell the two apart without extra state — covered by the same fix |
| Kernel/session reattach on reopen | present — `POST api/sessions` → `session_exists(path)` (`KernelLifecycle.kt:58-61`) | jupyterlab sessioncontext reconnect; rootfs handlers | complete — the server half already does what the references' servers do |
| Client run state on reopen | **absent** — fresh `EditorViewModel` + fresh `readStampedNotebook`; in-flight outputs never checkpointed (`checkpoint()` fires at cell end only, `NotebookCellRuns.kt:196`) | VS Code dirty-model retention (`notebookEditorModelResolverServiceImpl.ts` ~105-120) | the visible half of the defect |
| `activeCellRuns` while detached | **absent** — `whileRunning`'s `finally` decrements when `viewModelScope` cancels the collect, so `IdleServerStopper` can read 0 and stop the server under a detached run | n/a | exposed by the fix, not today — but must stay correct through it |
| Stop / space-stop still end the run | present — `stopRunningCell` interrupts via `session.whileOpen`; space stop kills server → sessions end → `kernelSessions.ended` → `endIfMatches` | all references | must keep working through detach/reattach (T3/T4) |

State plainly: in-workspace legs are **present**; Spaces-home and tab-close legs are **absent** —
the run's collection, state and socket die with the `ViewModelStore` entry even though the kernel
process survives on the server.

## Deviations

- **Beyond every reference.** No surveyed product keeps client-side run state across document
  close — JupyterLab loses it, VS Code keeps it only inside the dirty-model window. Ours retains
  the run deliberately; that is the item's ask, and it wants an ADR before this PRD is deleted.
- **Tab close is covered too.** The item names surfaces; the mechanism (`onCleared`) cannot
  distinguish "navigated away" from "tab closed", and a user who closes a running notebook's tab
  has not asked for its run to die — only Stop did. Stated, not assumed.
- **No new surface for stopping a detached run.** Stop lives on the cell (and kernel shutdown on
  the picker/Running panel); a detached run is stopped by reopening and stopping it, or by the
  Space stopping. A per-cell stop in the Running panel is out of scope.
- **Debugger-armed runs** ride the same retention — `watchForStop` is a kernel-side collector like
  the execute flow. Whether step-after-reattach needs the retained debugger bundled is a T3/T4
  question, not assumed here.

## Open decisions

- **Idle-session detach policy.** Retain only while a run is in flight (chosen above) vs keep the
  socket for every detached open kernel. T4 decides with evidence; the run-in-flight rule is the
  default because it changes nothing for notebooks that are not running.
- **`shutdownKernelOnClose` while detached.** The setting means "shut down when the document
  goes"; with retention, "going" no longer ends anything. Whether the setting applies at
  detach-final (run ended, still detached) or is suspended while a run is in flight is T4's call —
  the item says only Stop or space-stop ends a run, which argues the setting cannot preempt one.
- **Rename/delete while detached.** `DocumentFileFollow` dies with the VM, so a detached
  checkpoint persists to the path known at detach; a file renamed in the browser mid-run could be
  written back at its old path. T4 decides between porting the follow into the holder and
  revalidating the path at persist time.
- **Session/kernel ended while detached.** The `kernelSessions.ended`/`rebound` collectors move
  to the retained scope, so the flow keeps hearing it — but the exact landing (indicator, run
  failure, teardown ordering) is verified in T3/T4, not assumed.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — (run-in-flight predicate may land as an `EditorUiState` extension in `ui/editor/`) |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | existing fakes cover the tests (`FakeKernelGateway`, `FakeKernelSessions`, …) |
| `com.jupydroid.app` | `RetainedNotebookExecutions.kt` (activity-retained `ViewModel` + `remember…`, the `RetainedOpenDocuments` pattern) |
| `ui/editor` | `NotebookExecution.kt` + `NotebookExecutions.kt` (new — the holder and its registry), `NotebookKernel.kt` (scope → `applicationScope` + explicit lifetime), `NotebookKernelSession.kt` (only if attach/detach needs a hook), `EditorViewModel.kt` (attach on init, detach on `onCleared`, merge on adopt), `EditorScreen.kt` + `Navigation.kt` (pass the retained registry), `CellExecutions.kt` (in-flight predicate if needed) |
| `di/AppContainer` | — (the registry is activity-retained, not process state) |
| unit tests | `app/src/test/java/com/jupydroid/app/ui/editor/` — holder lifecycle, detach mid-run, reattach seed, checkpoint-while-detached, teardown |
| instrumented tests | only if a "something visible to press" question survives the unit tier |
| `.maestro/` flow + `scripts/` driver | `jd-100-*.yaml` + driver for the demo's round trips |

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — `NotebookKernel`'s kernel-side collectors (session ended/rebound, run collection,
  explain-miss, debug watch, diagnostics, history, rename-follow) run on `applicationScope`
  under a lifetime `Job` that `close()`/`shutdown()`-final paths cancel. Refactor only:
  `viewModelScope` death no longer silently ends them, so every exit path must cancel
  explicitly — behaviour identical while the VM lives.
- [ ] T2 — The run outlives its screen: `NotebookExecution` holder + `NotebookExecutions`
  registry + `RetainedNotebookExecutions`; `EditorViewModel.onCleared` detaches (the owed-write
  flush unchanged) and hands kernel + uiState to the holder while `executions` has
  RUNNING/QUEUED; events, checkpoint and `activeCellRuns` continue on `applicationScope`; the
  holder tears down — disconnect, cancel lifetime, drop — when the last run ends detached.
- [ ] T3 — Reopening adopts the run: a new `EditorViewModel` for the same `(Space,
  DocumentOpening)` takes the holder's flow and kernel, seeds fresh disk state merged with the
  retained run fields (`executions`, `outputs`, `executionCounts`, `inputRequests`,
  `clearsPending`, kernel fields, debug stop), rebinds checkpoint/kernel-bound to itself, and
  live events reach the new screen; Stop and `answerInput` work mid-run.
- [ ] T4 — Detached edges: idle-session policy, `shutdownKernelOnClose` at detach-final,
  session/kernel ended while detached, rename/delete while detached, eviction races (run ends
  between the detach check and the handoff), ordinal reuse on reopen.
- [ ] T5 — The demo on `SM-X216B`: every leg of the Demo script with screenshots, dumps and
  `maestro.log` under `evidence/<flow-name>/`; any surviving decision becomes an ADR; PRD
  deleted, backlog closed.

## Out of scope

- A Running-panel per-cell stop for detached runs (the panel lists sessions, not cells).
- Surviving app process death — kernels are app children and die with it (CLOSED-001).
- Surviving a Space's server being stopped for any reason other than the Space stop itself —
  the idle backstop is already held off by `activeCellRuns`, and a stopped server ends the run
  honestly.
- Cell *queue* semantics changes — a Run All's QUEUED cells ride the same retention as the
  RUNNING one, no new queue behaviour.
