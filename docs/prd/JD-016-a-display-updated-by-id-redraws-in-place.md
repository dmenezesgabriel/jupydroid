# JD-016 — A display updated by id redraws in place

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-113, GAP-104

## Outcome

A cell that calls `display(x, display_id=…)` and later `update_display(...)` — or `handle.update()`
from any later cell — sees one output that changes, not a stack of stale frames; and a notebook
opened and saved here keeps every output's `metadata`.

## Demo

On the arm64 tablet `SM-X216B` (it runs cells):

1. Cold start: install, launch, enter the `base` Space, create a notebook.
2. Cell 1: `h = display("frame 0", display_id=True)`; run. One output: `'frame 0'`.
3. Cell 2: `import time`, then `for i in range(1, 4): time.sleep(0.5); h.update(f"frame {i}")`;
   run. Cell 1 shows `'frame 3'` and nothing else; cell 2 shows nothing.
4. Cell 3: `from IPython.display import Image, display; display(Image(<png bytes>, width=100))`;
   run.
5. Kill and relaunch, reopen the notebook: cell 1 still shows `'frame 3'`, and the saved file's
   image output carries `"metadata": {"image/png": {"width": 100}}`.
6. Console: the same two-step `display`/`update` across two entries updates the first entry.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/outputarea/src/widget.ts` (`_onIOPub`, `_displayIdMap`, `_moveDisplayIdIndices`), `packages/services/src/kernel/default.ts` (`_handleDisplayId`), `packages/rendermime/src/outputmodel.ts` (`toJSON` deletes `transient`) | Per output area id→indices; the kernel re-routes an update to every earlier request that showed the id, while that request's future lives |
| `vscode` | PARTIAL | `vscode@3f3380c extensions/ipynb/src/deserializers.ts` (`getOutputMetadata`) | The ipynb serializer keeps output `metadata` and carries `transient` in memory only; the update itself is the extension's |
| `vscode-jupyter` | FULL | `vscode-jupyter@f01d80ca0 src/kernels/execution/cellDisplayIdTracker.ts` | Per-notebook id→output map that outlives the cell's run, dropped when that cell's outputs are cleared |
| `intellij-community` | ABSENT | — (grep for `update_display_data` under `python/` and `notebooks/` finds nothing) | — |
| `termux-app` | ABSENT | — | Not a notebook |
| `winlator` | ABSENT | — | Not a notebook |
| the shipped rootfs | Producer | `usr/lib/python3.12/site-packages/ipykernel/zmqshell.py` (`publish`: `content["transient"]`, `update_display_data` when `update=True`) | What arrives on the wire |

**Count:** 2 of the 6 front-end references implement it, and both notebook front ends do. It is
the protocol's own message; a front end without it renders `tqdm.notebook`, `handle.update()` and
every live display wrong.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Domain model | `packages/rendermime/src/outputmodel.ts` (`OutputModel` keeps `metadata`; `toJSON` drops `transient`) |
| Adapter | `packages/services/src/kernel/default.ts` (`_handleDisplayId`, `_displayIdToParentIds`, `_msgIdToDisplayIds`) |
| UI entry point | `packages/outputarea/src/widget.ts` (`_onIOPub`: `update_display_data` → `model.set` for each target; `display_data` with an id registers `model.length - 1`) |
| Edge cases it handles | Indices shift when outputs are removed (`_moveDisplayIdIndices`); `clear()` empties the map; a repeated `display_data` with a seen id is also sent as an update to the earlier requests, then appended |

### vscode-jupyter@f01d80ca0

| Concern | Files |
|---|---|
| Persisted state | `src/kernels/execution/cellDisplayIdTracker.ts` — `WeakMap<NotebookDocument, Map<displayId, output>>`; a cleared cell drops its mapping |

**Where they disagree:** JupyterLab forgets an id once the request that showed it is disposed, so
`h.update()` from a later cell reaches a finished cell only by accident of lifetime; vscode-jupyter
keeps the id for the notebook's life. That is the decision below.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the id lives | JupyterLab → side map of indices kept in step with removals; vscode-jupyter → side map of output objects | A side map must be shifted on every removal, clear and undo | **On the output itself** (`DisplayData.displayId`), and the target found by scanning the notebook's outputs. Our outputs are immutable lists in `EditorUiState`; a field travels through undo, clear and paste for free |
| Scope of an update | JupyterLab → while the original request lives; vscode-jupyter → the whole notebook | Notebook-wide matches what a user calling `h.update()` from cell 2 expects | **The whole notebook** (and the whole Console) |
| `display_data` with a seen id | JupyterLab → update earlier ones, then append | — | Same |
| Persisting the id | Both drop `transient` on save | A reopened display cannot be targeted | Same — `displayId` is never written |
| Output `metadata` | Both keep it through save | — | Same; today we write `{}` |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Wire read | `app/src/main/java/com/jupydroid/app/data/space/KernelOutputs.kt` reads `data` only; `update_display_data` becomes a new `DisplayData` | `jupyterlab@7f0e690 packages/outputarea/src/widget.ts` | absent |
| Domain carrier | `app/src/main/java/com/jupydroid/app/domain/model/CellOutput.kt` — no `metadata`, no id | `packages/rendermime/src/outputmodel.ts` | absent |
| File | `app/src/main/java/com/jupydroid/app/data/disk/NotebookOutputReader.kt` drops `metadata`; `NotebookOutputWriter.kt` writes `{}` | `vscode@3f3380c extensions/ipynb/src/deserializers.ts` | absent — a round trip loses data |
| Notebook reducer | `app/src/main/java/com/jupydroid/app/ui/editor/EditorUiState.kt` (`afterOutput` → `withOutputFolded` appends) | `widget.ts` `_onIOPub` | absent |
| Console reducer | `app/src/main/java/com/jupydroid/app/ui/console/ConsoleViewModel.kt` (`after`) | same | absent |

## Deviations

- No index map: the id rides on the output (see trade-offs).
- An update the kernel sends after every request that could carry it has ended (a background
  thread updating a finished cell) is not received: `JupyterFrame.ours` filters iopub by parent
  `msg_id`. JupyterLab drops it too once the future is disposed.

## Open decisions

None left open; the scope decision becomes an ADR with T2.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `CellOutput.kt` (`metadata`, `displayId`); a pure display-update fold beside `StreamFold.kt` |
| `domain/port` | — (`KernelEvent.Output` already carries a `CellOutput`) |
| `data/space` | `KernelOutputs.kt` |
| `data/disk` | `NotebookOutputReader.kt`, `NotebookOutputWriter.kt` |
| `data/fake` | — (`FakeKernelGateway` emits `Stream`s) |
| `ui/editor` | `EditorUiState.kt` |
| `ui/console` | `ConsoleViewModel.kt` |
| unit tests | `KernelOutputsTest`, `NotebookOutputsTest`, `NotebookRoundTripTest`, the fold's test, `EditorUiStateTest`/console VM test |
| `.maestro/` flow + `scripts/` driver | `jd-016-display-update-*` |

## Tasks

- [x] T1 — An output's `metadata` round-trips through the wire, the domain and the file; `transient.display_id` is read onto `DisplayData` and never written
- [x] T2 — `update_display_data` replaces every notebook output carrying its id and appends nothing; a repeated `display_data` updates and appends (ADR)
- [ ] T3 — The Console applies the same rule across its entries
- [ ] T4 — The demo on `SM-X216B`

## Out of scope

- Rendering `metadata` (image width/height, `needs_background`): `GAP-103`/`GAP-104`'s consumers,
  a later item. T1 carries it; nothing draws from it yet.
- Updates arriving with no live request (see Deviations).
- Execute-reply payloads (`GAP-116`).
