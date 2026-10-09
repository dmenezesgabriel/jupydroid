# JD-013 — A progress bar rewrites its own line

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-112

## Outcome

A cell or Console entry that prints with `\r` or `\b` — a progress bar, a spinner, `pip`'s download
line — draws one line that rewrites itself, and prints that flush separately land in one output block
instead of one row per flush.

## Demo

On `SM-X216B` (it runs a cell):

1. Cold start: install, launch, enter `base`, create a notebook.
2. Run
   ```python
   import time
   for i in range(5):
       print(f"\r{i*25}%", end="", flush=True); time.sleep(0.3)
   print(" done")
   print("a"); print("b")
   ```
   — the output is **one** block reading `100% done`, `a`, `b`; no `0%`, no stray `\r`.
3. Open the Console and run the same code — the same single block.
4. Kill and relaunch, reopen the notebook — the one block is still there, and the `.ipynb` holds one
   `stream` output whose text is the processed text.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/outputarea/src/model.ts` (`_add`, `Private.processText`, `Private.addText`) | merge into the last output when `name === _lastStreamName`; a cursor (`_streamIndex`) carried across chunks overwrites in place on `\r`, deletes on `\b` |
| `vscode` | FULL | `vscode@3f3380c src/vs/workbench/contrib/notebook/common/notebookCommon.ts` (`formatStreamText`, `fixCarriageReturn`, `fixBackspace`) | raw chunks are kept appended; the whole buffer is re-formatted with regexes on each change, stateless |
| `vscode-jupyter` | FULL | `vscode-jupyter@f01d80c src/kernels/execution/cellExecutionMessageHandler.ts` (`lastUsedStreamOutput`), `src/platform/common/utils.ts` (`formatStreamText`) | appends to the last output of the same stream name; formatting is core VS Code's |
| `intellij-community` | ABSENT | `notebooks/` holds `notebook-ui` and `visualization` only | no Jupyter output model in the community tree |
| `termux-app` | ABSENT (different domain) | `termux-app@3df69d1 terminal-emulator/.../TerminalEmulator.java` `case 13` | a full VT cursor; not a stream-text model |
| `winlator` | ABSENT | — | no notebook |
| the shipped rootfs | — | `usr/lib/python3.12/site-packages/ipykernel/iostream.py` `_flush` | sends `{"name", "text"}` with the buffered text unchanged — `\r` and `\b` reach the wire literally, and one `print` may arrive as several messages |

**Count:** 3 of 7 implement this — every reference that has a notebook output model.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Domain model | `packages/outputarea/src/model.ts` — `OutputAreaModel._add`, `shouldCombine`, `clear` (resets `_lastStreamName`) |
| Edge cases it handles | `Private.processText`: `\b` never deletes across `\n`; `\r` returns to line start, later text overwrites; `\n` appends at end; the cursor survives between chunks |
| Persisted state | `fromJSON` runs `_add` per output, so a split notebook folds on load; `toJSON` writes the processed text |

### vscode@3f3380c / vscode-jupyter@f01d80c

| Concern | Files |
|---|---|
| Domain model | `notebookCommon.ts` `compressOutputItemStreams` → `formatStreamText` |
| Adapter | `cellExecutionMessageHandler.ts` `handleStreamMessage` — `appendOutputItems` onto `lastUsedStreamOutput` |

**Disagreement:** JupyterLab keeps a cursor across chunks; VS Code re-formats the concatenated buffer.
They differ on `"abcdef\r"`, `"xy"`, `"z"`: JupyterLab `xyzdef`, VS Code `xycdefz`.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| `\r` semantics | jupyterlab → cursor across chunks; vscode → stateless regex over the buffer | the cursor costs one `Int` per cell of UI state; the regex re-scans the whole buffer every chunk and keeps raw text | **JupyterLab's cursor.** It is authoritative for behaviour (`cross-cutting.md` §8.3), each chunk costs its own length, and the saved text is what JupyterLab writes |
| When to merge | both → last output is a stream of the same name | — | same; `clear_output` empties the list, which is JupyterLab's `_lastStreamName = ''` |
| Load | jupyterlab → `fromJSON` folds; vscode → stored as-is | — | fold on load (T3), so notebooks saved split before this item reopen folded |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Merge in the notebook | `ui/editor/EditorUiState.kt` `afterOutput` appends | `model.ts` `_add` | absent |
| Merge in the Console | `ui/console/ConsoleViewModel.kt` appends `event.output` | same | absent |
| `\r`/`\b` | `ui/editor/RenderedOutput.kt` `withoutAnsiColours` strips ANSI only | `processText` | absent |
| Fold on load | `data/disk/NotebookOutputReader.kt` maps each output alone | `fromJSON` | absent |

## Deviations

None planned. `unleakString` and the observable-string diff in `addText` exist for browser string
storage and have no Kotlin counterpart.

## Open decisions

None open; the `\r` disagreement is settled above.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `StreamFold.kt` (new) |
| `ui/editor` | `EditorUiState.kt` |
| `ui/console` | `ConsoleViewModel.kt` |
| `data/disk` | `NotebookOutputReader.kt` or its caller |
| unit tests | `domain/model/StreamFoldTest.kt`, `ui/editor/EditorUiStateTest.kt`, `ui/console/ConsoleViewModelTest.kt` |
| `.maestro/` flow + `scripts/` driver | one per device task |

## Tasks

- [ ] T1 — The fold, in the notebook: `StreamFold` (merge + `processText`), wired into `afterOutput` and
  `withInputAnswered`; device evidence of a `\r` loop drawing one line
- [ ] T2 — The Console folds the same way (output and echo paths); device evidence
- [ ] T3 — A notebook read from disk folds adjacent same-name streams; evidence of a split notebook
  reopening folded
- [ ] T4 — The demo above on `SM-X216B`, then ship: delete this PRD, close `GAP-112`, flip its
  parity-ledger row

## Out of scope

ANSI colour (`GAP-106`), `update_display_data` (`GAP-113`), bounding a runaway output (`GAP-115`).
