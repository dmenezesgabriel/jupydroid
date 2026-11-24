# JD-101 — A cell's stop square interrupts it

Milestone: `M11` · State: open · Depends on: JD-099, JD-100 · Closes: —

## Outcome

Pressing the red square on a running cell interrupts its kernel — the run halts and the cell
reports it — **including a Stop pressed while the kernel is still connecting**, which today is
dropped without a trace and lets the cell run to completion behind a control that did nothing.

## Demo

On the arm64 tablet (`SM-X216B` — anything that runs a cell; x86_64 `dev36` echoes through
`FakeKernelGateway` and proves nothing, ADR-0003):

1. Cold start: install, launch, enter `base`, open a notebook.
2. A code cell with a long, chatty loop (the `run-survives.ipynb` fixture's `tick` shape — a
   print a second for minutes) → Run → while `[*]:` and "Stop cell" show, tap "Stop cell".
3. The run halts: `[*]:` and "Stop cell" are gone, the cell reports stopped, its outputs carry
   the kernel's `KeyboardInterrupt`, and the driver reads the same `KeyboardInterrupt` off the
   notebook file's checkpoint.
4. The connect leg — the regression's own shape: with the Space cold (fresh install, or the
   Space stopped so the next run pays the whole bring-up), Run a cell and tap "Stop cell"
   while the kernel is still coming up. The cell must end stopped **without its code ever
   reaching the kernel** — no output, no completed run — not run to completion behind a Stop
   that did nothing.
5. If cheap inside the same flow: Run All over several cells → Stop mid-run → the running cell
   CANCELLED and the rest SKIPPED.

Screenshots, UI dumps and `maestro.log` at every step into `evidence/<flow-name>/` (local,
ADR-0132). The item is not `shipped` until this has been run on that device.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `packages/notebook/src/default-toolbar.tsx:297` (toolbar `interrupt` item → `AppToolbar.createInterruptButton`, `apputils/src/toolbar/widget.tsx:38-55`), `packages/notebook-extension/src/index.ts:3382` (`notebook:interrupt-kernel`), `packages/mainmenu-extension/src/index.ts:5405` (Kernel menu), `packages/console-extension/src/index.ts:1132` | `sessionContext.session?.kernel?.interrupt()` → REST `POST api/kernels/<id>/interrupt`; toolbar item enabled always, no per-cell stop |
| `vscode` | FULL | `src/vs/workbench/contrib/notebook/browser/controller/executeActions.ts` — `InterruptNotebook` (~640-700, `notebook.interruptExecution`, stopIcon in EditorTitle/NotebookToolbar) and `CancelExecuteCell` (~441-500, `notebook.cell.cancelExecution`, per-cell "Stop Cell Execution") | core draws both affordances; both end at `notebookEditor.cancelNotebookCells` → `kernel.cancelNotebookCellExecution` → the extension's `$cancelCells` |
| `vscode-jupyter` | FULL | `src/notebooks/controllers/vscodeNotebookController.ts:207` (`controller.interruptHandler`), `src/notebooks/notebookCommandListener.ts:93` (`jupyter.interruptkernel`), `src/kernels/kernel.ts:283` (`interrupt()`), `:563` (`interruptExecution`) | `interrupt()` **awaits the in-flight `_jupyterSessionPromise`** — the connect-window answer ours lacks — then `session.kernel.interrupt()` raced with `interruptTimeout`; pending executions resolve through `willInterrupt`/`interruptCompleted` hooks; `TimedOut` → restart offer; no session → executions aborted client-side |
| `intellij-community` | ABSENT | — | the Jupyter notebook editor and its kernel session live in the proprietary PyCharm plugin; the community clone carries only CEF preview plumbing under `jupyter/src/com/intellij/notebooks/jupyter/core/` (verified: no interrupt anywhere under `jupyter/src`) |
| `termux-app` | ABSENT | — | a terminal emulator; no notebook and no kernel-protocol client |
| `winlator` | ABSENT | — | a container runtime; no notebook and no kernel-protocol client |
| the shipped rootfs | FULL | `usr/lib/python3.12/site-packages/jupyter_server/services/kernels/handlers.py` `KernelActionHandler.post`; `jupyter_client/manager.py::_async_interrupt_kernel`; `jupyter_client/kernelspec.py:36` | `POST api/kernels/<id>/interrupt` → `interrupt_mode` defaults `"signal"` → SIGINT to the kernel's process group; `_async_interrupt_kernel` awaits `_ready` first, so an interrupt that reaches the server during boot is handled server-side |

**Count:** 4 of 7 implement interrupt — three products plus the server we ship. The question
this item answers is not whether to build it (the wire path exists and is device-verified in
steady state) but the two places ours drops or loses the ask.

## Implementation inventory

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| Domain model | n/a — interrupt is a session/kernel act; the cell's `executionState` flips idle on the kernel's own status, not on the button |
| Port / protocol | `packages/services/src/kernel/` — `kernel.interrupt()` issues the REST call |
| Adapter | `packages/apputils/src/sessioncontext.tsx` — `sessionContext.session?.kernel?.interrupt()`, null-safe |
| UI entry point | `packages/notebook/src/default-toolbar.tsx:297`, `packages/notebook-extension/src/index.ts:3382-3399` (toolbar variant `isEnabled` unconditionally), `packages/mainmenu-extension/src/index.ts:5405`, `packages/console-extension/src/index.ts:1132-1152` |
| Persisted state | n/a |
| Empty / error / loading states | interrupt on an idle kernel is a server-side no-op; the button stays enabled |
| Accessibility specifics | n/a |
| Edge cases it handles | `kernel?.interrupt()` — no kernel, no ask; no client-side timeout or retry |

### vscode@3f3380c668d07649b953eb8278354b2f263ea6ff

| Concern | Files |
|---|---|
| Domain model | `NotebookCellExecutionState` on the core cell (`executing`/`pending`) — what the per-cell stop's `cellCancelCondition` reads |
| Port / protocol | n/a — core speaks no kernel protocol (the extension's job) |
| Adapter | `src/vs/workbench/contrib/notebook/browser/notebookEditorWidget.ts:2305` → `services/notebookExecutionServiceImpl.ts:105` `cancelNotebookCells` → `kernel.cancelNotebookCellExecution(uri, handles)` → `mainThreadNotebookKernels.ts:246` `$cancelCells` |
| UI entry point | `executeActions.ts` — `InterruptNotebook` (~640-700, stopIcon, `NOTEBOOK_HAS_SOMETHING_RUNNING` + `NOTEBOOK_INTERRUPTIBLE_KERNEL`), `CancelExecuteCell` (~441-500, "Stop Cell Execution" in the cell's inline execute menu) |
| Persisted state | n/a |
| Empty / error / loading states | both gated on something running / interruptible kernel |
| Accessibility specifics | n/a |
| Edge cases it handles | per-cell cancel still routes to the kernel-wide interrupt — VS Code has no per-cell interrupt either |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Domain model | `src/kernels/execution/cellExecution.ts`, `cellExecutionQueue.ts` — pending executions the interrupt resolves through hooks |
| Port / protocol | `src/kernels/kernel.ts`, `src/kernels/jupyter/` — `session.kernel.interrupt()` |
| Adapter | `src/kernels/kernel.ts:283-320` `interrupt()` — awaits `_jupyterSessionPromise`, dedupes on `_interruptPromise`, fires `willInterrupt`/`interruptCompleted` hooks |
| UI entry point | `src/notebooks/controllers/vscodeNotebookController.ts:207` (`interruptHandler`), `:520-530` (`handleInterrupt` → `Commands.InterruptKernel`); `src/notebooks/notebookCommandListener.ts:93,136-160`; `src/interactive-window/editor-integration/codeLensFactory.ts:293` (debug stop reuses it) |
| Persisted state | n/a |
| Empty / error / loading states | `InterruptResult.Success/TimedOut/Restarted/Dead`; `TimedOut` → restart offer (`kernel.ts:315-321`) |
| Accessibility specifics | n/a |
| Edge cases it handles | **the connect window**: interrupt during session startup waits on the same promise the run is awaiting, then interrupts; no session → executions aborted client-side; kernel ignoring the interrupt → bounded wait → restart offer |

### the shipped rootfs (ours)

| Concern | Files |
|---|---|
| Domain model | n/a — server-side only |
| Port / protocol | `jupyter_server/services/kernels/handlers.py` `KernelActionHandler.post` (~95-109): `interrupt` → `km.interrupt_kernel(kernel_id)` with **no** try/except (unlike `restart`, which is wrapped into a 500); `jupyter_client/manager.py::_async_interrupt_kernel` (~728-756) awaits `_ready` when the kernel is still starting, then `interrupt_mode == "signal"` → `_async_signal_kernel(SIGINT)` to the process group, `"message"` → `interrupt_request` on the control channel |
| Adapter | `app/src/main/java/com/jupydroid/app/data/space/KernelLifecycle.kt:217-219` (`rest.post("api/kernels/$kernelId/interrupt")`), `RealKernelGateway.kt:121`, `BaseSpaceKernelGateway.kt:74`, `SpaceKernelSpec.kt:126-133` (`kernelSpecJson` writes no `interrupt_mode` → `jupyter_client/kernelspec.py:36` default `"signal"`) |
| UI entry point | n/a |
| Persisted state | n/a |
| Empty / error / loading states | unknown kernel → 404; dead process → `RuntimeError` propagates → 500 |
| Accessibility specifics | n/a |
| Edge cases it handles | interrupt during kernel boot is awaited server-side (`_ready`) — the race ours loses is entirely client-side |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where a stop pressed during connect lands | vscode-jupyter → `interrupt()` awaits `_jupyterSessionPromise`, then interrupts the kernel; ours-candidate-A → same await shape via `reopeningIfStale`; ours-candidate-B → check the pending stop inside `eventsFor` after `open()` and skip the execute | awaiting needs `open()` to be single-flight — a second `connect` races the run's own `POST api/sessions`; interrupting a just-born kernel races `execute_request` on the wire, and a SIGINT that lands first is spent on an idle kernel while the cell runs anyway | **candidate B**: at the one point that sees both `interrupting` and the resolved session, skip `debugger.startSession`/`execute` — the cell resolves CANCELLED through the existing epilogue, the kernel is left idle so no interrupt POST is owed, and nothing races. The observable end state is identical to vscode-jupyter's (cell stopped, kernel alive) without new machinery |
| What a refused interrupt means | fold it (kernel or server gone — the run's own channel reports the truth) vs surface it | interrupt is best-effort by nature; every reference treats "nothing to interrupt" as a non-event; today a `KernelLifecycleException`/`IOException` escapes `scope.launch` → uncaught coroutine failure, the class `b9497e60` repaired for stale sessions | fold non-stale failures inside `stopRunningCell`'s launch — `whileOpen` keeps its stale-only contract because `answerInput`'s failure has a different honest answer (the cell stays blocked on `input()`; that deserves its own surface, not this item's) |
| Interrupt timeout → restart offer | vscode-jupyter → `interruptTimeout` then offer restart; jupyterlab → fire-and-forget | a kernel stuck in native code ignores SIGINT; the offer is real UX but adds a timer and a second dialog, and `NotebookBar`'s Restart is one tap away | out of scope — JupyterLab's shape; reopen only if the demo shows a kernel that ignores SIGINT |

Anything here that outlives the item becomes an ADR before the PRD is deleted.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Stop → interrupt wire | present — `NotebookCellRuns.stopRunningCell` (`ui/editor/NotebookCellRuns.kt:153-160`) → `session.whileOpen` → `kernelGateway.interrupt` → `POST api/kernels/<id>/interrupt`; steady state device-verified by the run-survives driver's part 7 (`scripts/run-survives-leaving-driver.sh:161-167`, `KeyboardInterrupt` read off disk) | jupyterlab `notebook-extension/src/index.ts:3382`; rootfs `handlers.py` `KernelActionHandler.post` | complete in steady state |
| Stop during connect | **absent** — `whileOpen` returns on `opened == null` (`NotebookKernelSession.kt:230-237`), while `startingRun` has already shown RUNNING + Stop (`NotebookCellRuns.kt:195` precedes `eventsFor` → `open()`), so the window is user-visible for the whole connect; the ask is dropped and `interrupting` stays set until the run ends — a completed cell then mislabeled CANCELLED | vscode-jupyter `src/kernels/kernel.ts:283-303` awaits the in-flight session promise | the reported "stop square does nothing" shape; on v0.2.0's wedged kernel (JD-099's root cause) this window was effectively infinite |
| Refused interrupt | **defect** — `whileOpen` catches `StaleKernelSessionException` only; `KernelLifecycleException`/`IOException` (kernel gone → rootfs `KernelActionHandler.post` has no try/except around `interrupt` → 500; server unreachable) escapes `scope.launch` → uncaught coroutine failure | n/a — the references' interrupt calls land in their own UI lanes | crash path, same class `b9497e60` repaired red-first |
| The cell reports the stop | present — `cancellingRun` → CANCELLED ("stopped" to a screen reader, `ExecutionLabel.kt:75`), the kernel's `KeyboardInterrupt` lands as error output, `checkpoint()` persists it | jupyterlab error output + idle | complete; the demo verifies on device |
| Console interrupt | same `whileOpen` window (`ui/console/ConsoleViewModel.kt:131-134`) | same references | present-but-shared-defect — out of scope, recorded so a later item inherits it knowingly |

State plainly: the wire path is **present**; the connect window is **absent**; the refusal
handling is a **defect**; the reporting is **complete**.

## Deviations

- **A stop that predates the execute never reaches the kernel.** vscode-jupyter interrupts a
  kernel that may not have the cell yet and resolves the cell client-side through hooks; ours
  skips the execute outright once `open()` returns — identical observable end state (cell
  stopped, kernel alive and idle) without a SIGINT racing a not-yet-sent `execute_request`.
  Stated here and at `eventsFor`, not assumed.
- **No interrupt timeout.** See trade-offs — JupyterLab's shape, and Restart is one tap away.
- **The console keeps today's shape.** The item is the cell's stop square; the console's menu
  Interrupt has the same window and is named in Gap analysis rather than silently different.

## Open decisions

- Whether the pending-stop check lives in `eventsFor` (the proposed shape — the one point that
  sees both `interrupting` and the resolved session) or as a `stopRunningCell`-side deferred
  interrupt. T1 decides with the red test; `eventsFor` is proposed because it needs no new
  state and no second connect.
- Whether the refusal fold sits in `stopRunningCell`'s launch (proposed) or widens `whileOpen`'s
  catch — widening would also change `answerInput`'s failure shape, which has a different
  honest answer and is probably wrong to fold here.
- Whether the demo earns the Run-All leg (queued cells → SKIPPED after a mid-queue stop). It is
  cheap inside the same flow; T3 decides.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/<mechanism>` | — (no adapter change: the POST already exists) |
| `data/fake` | `FakeKernelGateway` + `app/src/test/.../EditorTestFixtures.kt` — a held-`connect` gate beside `StaleSessionKernelGateway.holdRuns` (`EditorTestFixtures.kt:205-287`) |
| `ui/editor` | `NotebookCellRuns.kt` (`eventsFor` pending-stop check; `stopRunningCell` refusal fold), `NotebookKernelSession.kt` (only if the fold lands in `whileOpen` — proposed: it does not) |
| `di/AppContainer` | — |
| unit tests | `app/src/test/java/com/jupydroid/app/ui/editor/` — stop-during-connect and refused-interrupt cases; the existing stop tests (`EditorViewModelTest.kt:264-288`, `StaleSessionReconnectTest.kt:92-108`, `NotebookExecution*Test`) stay green |
| instrumented tests | only if a "something visible to press" question survives the unit tier — the Stop control's enabled state is already pinned by `NotebookBarTest`/`CellHeader`-adjacent tests |
| `.maestro/` flow + `scripts/` driver | `cell-stop-*.yaml` + `scripts/cell-stop-driver.sh` for the demo's legs; `maestro.log` under `evidence/<flow-name>/` |

Every port gets a `Fake…` in `data/fake/`, which is also what `AppContainer` defaults to.

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — Stop during connect: `stopRunningCell` while `opened == null` keeps the ask — once
  `open()` returns, the cell's execute is never sent and the cell resolves CANCELLED, not
  run-to-completion mislabeled. Red first: a gateway whose `connect` is held reproduces today's
  drop; green: the pending stop is checked inside `reopeningIfStale` after `open()` and before
  `debugger.startSession`/`execute`.
- [x] T2 — A refused interrupt folds: `kernelGateway.interrupt` throwing a non-stale failure
  must not escape `stopRunningCell`'s launch; the run resolves through its own channel. Red
  first with a throwing fake.
- [ ] T3 — The demo on `SM-X216B`: the mid-run Stop leg (warm kernel — regression check) and
  the Stop-during-connect leg (cold Space — the fix's proof), screenshots, dumps and
  `maestro.log` under `evidence/<flow-name>/`; the driver script committed; decisions that outlive
  become ADRs; PRD deleted, backlog closed.

## Out of scope

- `ConsoleViewModel.interrupt`'s identical `whileOpen` window — same defect class, different
  surface; recorded in Gap analysis for a later item.
- vscode-jupyter's interrupt-timeout → restart offer — JupyterLab doesn't have it either, and
  `NotebookBar`'s Restart is one tap away.
- A stop surface for a detached run — JD-100's shipped semantics: reopen and stop, or stop the
  Space.
- `interrupt_mode` — the shipped spec already resolves to signal/SIGINT
  (`jupyter_client/kernelspec.py:36` default; `SpaceKernelSpec.kt:126-133` writes none).
- Per-cell interrupt semantics — Jupyter's interrupt is kernel-wide; ours already funnels the
  cell's Stop and the bar's Interrupt to the same act.
