# JD-015 — A runaway cell's output stays usable

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-115

> Delete this file when the item ships — its durable residue is an ADR and the code comments
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A cell that prints without end, or displays hundreds of outputs, leaves the notebook and the Console
responsive: the head and tail of a long text are drawn, the rest is one tap away, and the saved file keeps
every line.

## Demo

On the arm64 tablet (`SM-X216B`), since every step runs a cell.

1. Cold start: install, launch, enter `base`, create a notebook.
2. Run `for i in range(200_000): print(i)`. The cell finishes and the output reads `0`…`24`, then a row
   saying `199970 lines hidden`, then `199995`…`199999`. The editor scrolls while the cell runs.
3. Tap the hidden-lines row. Every line is drawn.
4. Run `for i in range(300): display(i)`. 50 outputs are drawn, then a `Show more outputs` row. Tap it and all
   300 are drawn.
5. Open the Console and run step 2's line. You get the same head, row and tail.
6. Search the notebook for `123456`. Stepping onto the match expands the cell's hidden lines.
7. Kill and relaunch, then reopen the notebook. Its outputs are cut the same way, and the `.ipynb` on disk still
   holds all 200k lines.

Evidence goes under `evidence/jd-015-output-caps/`.

## Baseline, measured before any change

`scripts/jd-015-output-caps-baseline-driver.sh` on SM-X216B at `5b3811c7`. Each run is timed from the `Run cell`
tap to `[1]:`, so it includes the kernel's start. Evidence is in `evidence/jd-015-output-caps/baseline/`.

| Case | Run | Frames (janky · p90 · p99) | PSS | App |
|---|---|---|---|---|
| `print(6 * 7)`, the control | 36 s | 19 (68% · 150 ms · 150 ms) | 119 MB | idle |
| `for i in range(200_000): print(i)` | **no `[1]:` within 300 s** | gfxinfo **unanswered** | 133 MB | **176% CPU, 11 CPU-minutes**, last frame `[*]` with nothing drawn |
| `for i in range(300): display(i)` | 26 s | 87 (38% · 105 ms · 750 ms) | 119 MB | idle |

**The print case is not a layout problem.** The kernel had finished, and the app was still folding.
`withOutputFolded` (`app/src/main/java/com/jupydroid/app/domain/model/StreamFold.kt`) rebuilt the whole text for
every token, and a print loop is two tokens per line. On a desktop JVM 40k, 80k and 160k lines took 0.8 s, 2.7 s
and 15.5 s: quadratic, inside the editor's `uiState.update`. That is a defect of `6afbdf9a`, and T1 repairs it.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL (count) | `packages/outputarea/src/widget.ts` (`_insertOutput`, `_setOutput`, `_showTrimmedOutputs`, `Private.TrimmedOutputs`), `packages/notebook-extension/schema/tracker.json` (`maxNumberOutputs`, default 50) | outputs at index ≥ the cap get no widget, and a `Show more outputs` button sets the cap to ∞; the model keeps all of them |
| `vscode` | FULL (lines) | `extensions/notebook-renderers/src/textHelper.ts` (`truncatedArrayOfString`, `scrollableArrayOfString`, `softScrollableLineLimit` 5000), `src/vs/workbench/contrib/notebook/browser/notebook.contribution.ts` (`notebook.output.textLineLimit`, default 30) | past the limit it draws the first `limit - 5` lines, `...`, the last 5 and a link to the whole output; scrolled, it draws the last 5000 |
| `vscode-jupyter` | ABSENT | — | renders through core VS Code's renderer above |
| `intellij-community` | PARTIAL (data) | `platform/execution-impl/src/com/intellij/execution/impl/ConsoleBuffer.java` (`idea.cycle.buffer.size`, 1024 KB) | a run console drops its oldest text past the buffer; no notebook output cap |
| `termux-app` | PARTIAL (data) | `terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java` (`DEFAULT_TERMINAL_TRANSCRIPT_ROWS` 2000) | a terminal's scrollback is a ring; no output surface outside the grid |
| `winlator` | ABSENT | — | no output surface |
| the shipped rootfs | inflow only | `usr/lib/python3.12/site-packages/jupyter_server/services/kernels/connection/channels.py` (`limit_rate`, `iopub_msg_rate_limit` 1000/s, `iopub_data_rate_limit` 1 MB/s, `rate_limit_window` 3 s) | throttles iopub and writes `IOPub data rate exceeded` to stderr; never bounds a cell's total |

**Count:** both notebook front ends cap what a runaway draws, and **both cap the view and keep the model**. The two
that drop data are a terminal and a run console, not notebooks. So the view cap is the convention, and a data cap
would make a notebook saved here differ from the same notebook saved by either front end.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Cap | `packages/outputarea/src/widget.ts` `_insertOutput` (index == cap inserts `TrimmedOutputs`; index > cap inserts nothing), `_setOutput` (no update past the cap) |
| Expand | `Private.TrimmedOutputs` (`Show more outputs`, title `The first %1 are displayed`), whose click sets `_maxNumberOutputs = Infinity` and calls `_showTrimmedOutputs` |
| Setting | `packages/notebook-extension/schema/tracker.json` `maxNumberOutputs` (50; 0 means no cap) |
| Edge cases | `widget.ts` `onInputRequest` raises the cap to `model.length + 1` when a stdin answer lands, so the echo is never hidden behind the button |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Cut | `extensions/notebook-renderers/src/textHelper.ts` `truncatedArrayOfString` (head `limit - 5`, `...`, tail 5) |
| Expand | `generateViewMoreElement`, a `command:workbench.action.openLargeOutput` link that opens the whole output in an editor |
| Scrolled | `scrollableArrayOfString` (last `softScrollableLineLimit` = 5000 lines), and `appendScrollableOutput`'s `hardScrollableLineLimit` = 8000 |
| Setting | `notebook.output.textLineLimit` (30) |

**Disagreement:** JupyterLab counts outputs, VS Code counts lines. After the fold, a print loop is one output, so
only a line cap bounds it, while 300 `display()` calls are 300 short outputs that only a count cap bounds. They
bound different runaways, so this item adopts both.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| What is capped | the view (JupyterLab, VS Code) · the data (IntelliJ console, Termux) | a data cap bounds memory, but a saved notebook loses output | **the view**: both notebook references do it, and the file stays what JupyterLab would save |
| Line cut | head + tail (VS Code) | — | **head 25 + tail 5 past 30 lines**, VS Code's default |
| Count cut | first N + button (JupyterLab) | — | **50**, JupyterLab's notebook default |
| Expand | in place (JupyterLab) · open in an editor (VS Code) | an editor needs a document surface for an output, which `GAP-100` says we lack | **in place**, per output, lasting while the output does |
| Where colour is cut | parse the tail alone (VS Code) | the tail loses a colour set in the hidden middle | **cut after colouring**, so the tail keeps it |

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Fold cost | `domain/model/StreamFold.kt`, quadratic in tokens | jupyterlab `model.ts` `_add` → `Private.addText`, one new string per message | **defect**, repaired by T1 |
| Line cut | none; `ui/editor/CellOutputsView.kt` `OutputText` lays out the whole text | vscode `truncatedArrayOfString` | absent |
| Count cut | none; `OutputColumn` draws every output | jupyterlab `TrimmedOutputs` | absent |
| Scrolled mode | `ScrolledOutputs`, which bounds height but not work | vscode `scrollableArrayOfString` | partial |
| Console | `ui/console/ConsoleTranscriptView.kt` draws through the same `CellOutputs` | — | follows the notebook's, to be verified |

## Deviations

- A final `\n` ends the last line rather than opening an empty one. VS Code's split counts it, so its tail spends
  a row on nothing after every `print`.
- Colour is cut after the SGR walk (see the trade-offs table).

## Open decisions

- **Search into a hidden region.** Search already reads the model. The recommendation is that stepping onto a
  hit in the hidden middle expands that output, which is JupyterLab's `expandParent` precedent. To be settled in T5.
- **Scrolled mode.** The recommendation is VS Code's last-5000-lines inside the existing 24-line box. To be
  settled in T5 against a measurement.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `StreamFold.kt` (T1) |
| `ui/editor` | new `OutputLineWindow.kt`, `CellOutputsView.kt`, `HiddenCellsRow.kt` (the shared row) |
| `ui/console` | `ConsoleTranscriptView.kt` (T4, if it needs anything) |
| unit tests | `StreamFoldTest`, `OutputLineWindowTest` |
| instrumented tests | `CellOutputsViewTest` |
| `.maestro/` flow + `scripts/` driver | `jd-015-output-caps-*.yaml`, `scripts/jd-015-output-caps-*-driver.sh` |

There are no port changes: this is a fold repair and a view.

## Tasks

- [ ] T1: the fold is linear, so a 200k-line print finishes on SM-X216B and the editor answers again.
- [ ] T2: the line window in the notebook, `N lines hidden` between head and tail, tap to draw all.
- [ ] T3: the output-count cap of 50, with `Show more outputs`, in the notebook.
- [ ] T4: both caps in the Console.
- [ ] T5: search into a hidden region, and scrolled mode's tail.
- [ ] T6: run the demo end to end; write an ADR ("an output is capped in the view, never in the model"); rewrite
  GAP-115; delete this PRD; ship the backlog row.

## Out of scope

- One line that never ends (`print(x, end="")` in a loop). Neither reference cuts within a line.
- Memory: the model keeps every line, as both references do. The server's 1 MB/s iopub limit is the only bound on
  the inflow.
- `update_display_data` replacing in place, which is `GAP-113`.
