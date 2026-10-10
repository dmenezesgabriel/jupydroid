# JD-017 — The Console clears an entry when the kernel says so

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-162

## Outcome

A Console entry that calls `clear_output()` shows only what it printed afterwards, and one that
calls `clear_output(wait=True)` keeps its output until the next output replaces it — as a notebook
cell here already does.

## Demo

On the arm64 tablet `SM-X216B` (it runs cells):

1. Cold start: install, launch, enter the `base` Space, open a notebook and run a cell so the kernel
   starts; open a Console from Kernel actions.
2. Entry 1: `from IPython.display import clear_output; print('before'); clear_output(); print('after')`.
   The entry shows `after`, and not `before`.
3. Entry 2: `import time; print('first'); clear_output(wait=True); time.sleep(3); print('second')`.
   While it sleeps the entry still shows `first`; once it finishes it shows `second` and not `first`.
4. Entry 1 still shows `after`: a clear empties only the entry that asked for it.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/outputarea/src/model.ts` (`clear`, `clearNext`, `add`), `packages/console/src/widget.ts` (entries are `CodeCell`s) | A console entry is a code cell, so it gets the same `OutputArea` model and the same clear |
| `vscode` | ABSENT | — | The editor has no kernel; `clear_output` belongs to the extension |
| `vscode-jupyter` | FULL | `vscode-jupyter@f01d80ca0 src/kernels/execution/cellExecutionMessageHandler.ts` (`handleClearOutput`, `clearOutputOnNextUpdateToOutput`, `clearOutputIfNecessary`) | One handler per cell execution, which its Interactive Window cells run through as notebook cells do |
| `intellij-community` | ABSENT | — (no `clear_output` anywhere in the clone) | — |
| `termux-app` | ABSENT | — | Not a notebook |
| `winlator` | ABSENT | — | Not a notebook |
| the shipped rootfs | Producer | `usr/lib/python3.12/site-packages/IPython/core/display_functions.py` (`clear_output`), `usr/lib/python3.12/site-packages/ipykernel/zmqshell.py` (`ZMQDisplayPublisher.clear_output`) | Sends a `clear_output` message carrying `wait` on iopub |

**Count:** 2 of the 6 front-end references implement it, and both do it by running a console
entry through the same output model a notebook cell uses. That is the rule here too: the notebook
already carries it out, and the Console does not.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Domain model | `packages/outputarea/src/model.ts` — `clear(wait)`: `wait` sets `clearNext`, otherwise disposes the list; `add` clears first when `clearNext` is set; `_lastStreamName` resets so the next stream does not fold onto a cleared one |
| UI entry point | `packages/outputarea/src/widget.ts` (`_onIOPub`: `clear_output` → `model.clear(msg.content.wait)`) |
| Edge cases it handles | `set` (a display update) does not use up `clearNext`; only `add` does |

### vscode-jupyter@f01d80ca0

| Concern | Files |
|---|---|
| UI entry point | `src/kernels/execution/cellExecutionMessageHandler.ts` — `handleClearOutput`: `wait` sets `clearOutputOnNextUpdateToOutput`, otherwise clears now; `clearOutputIfNecessary` runs before the next output is added |
| Edge cases it handles | A clear inside an Output widget clears the widget, not the cell (we have no comms, `GAP-013`) |

The two agree; there is no decision between them to make.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where a pending `wait` lives | JupyterLab → a flag on the output area; our notebook → `EditorUiState.clearsPending`, a set of cell ids | The Console's state is a `List<ConsoleEntry>`, with no screen-level state beside it | **A flag on the entry** (`ConsoleEntry.clearPending`), the same place the entry's `inputRequest` already lives |
| What uses up a pending clear | JupyterLab → `add`, not `set` | — | Same as the notebook: the next output added, not a display update |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Wire read | `app/src/main/java/com/jupydroid/app/data/space/KernelOutputContent.kt` reads `clear_output` and `wait` | — | complete |
| Notebook reducer | `app/src/main/java/com/jupydroid/app/ui/editor/OutputArrival.kt` (`afterClear`, `clearsPending`) | `jupyterlab@7f0e690 packages/outputarea/src/model.ts` | complete |
| Console reducer | `app/src/main/java/com/jupydroid/app/ui/console/ConsoleOutputArrival.kt` folds `ClearOutput` into the entry like a result | same | absent: the clear is kept as an output that draws nothing |

## Deviations

None.

## Open decisions

None. The rule is the notebook's, which the parity ledger already records (`clear_output`/`clear(wait)`).

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/space` | — |
| `data/fake` | — |
| `ui/console` | `ConsoleEntry.kt` (`clearPending`), `ConsoleOutputArrival.kt` |
| unit tests | `ConsoleOutputArrivalTest` |
| `.maestro/` flow + `scripts/` driver | `jd-017-console-clear-*` |

## Tasks

- [x] T1 — The Console carries out `clear_output`, immediate and `wait`, by entry; a flow on `SM-X216B`
- [x] T2 — The demo on `SM-X216B`; ship

## Out of scope

- A clear inside an Output widget: no comms (`GAP-013`).
- The `input()` echo using up a pending clear: the notebook's echo does not either
  (`EditorUiState.kt`), and the two stay the same.
