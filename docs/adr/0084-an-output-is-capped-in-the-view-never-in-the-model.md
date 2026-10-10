# ADR-0084 — An output is capped in the view, never in the model

Status: accepted
Date: 2026-10-09

## Context

A cell can print without end or display hundreds of outputs. Before any cap, on SM-X216B,
`for i in range(200_000): print(i)` never reached `[1]:` within 300 s. The app sat at 176% CPU for 11
CPU-minutes and drew nothing, although the kernel had finished long before. Three costs were stacked:

- **The fold was quadratic.** `withOutputFolded` (`app/src/main/java/com/jupydroid/app/domain/model/StreamFold.kt`)
  rebuilt the stream's whole text for every token, and a print loop sends two tokens per line. On a
  desktop JVM, 40k, 80k and 160k lines took 0.8 s, 2.7 s and 15.5 s. On ART the tokens also came from
  `Regex.findAll`, which makes a `Matcher` per match, and an Android `Matcher` copies its whole input into
  ICU. So a message cost tokens × its own length. Both are defects, and both are repaired: one buffer per
  message, scanned by index.
- **The draw copied the text per line.** With the fold repaired, `simpleperf` put 85% of samples in
  `art::JNI::GetStringChars` under `TextAnnotatedStringNode.draw → Layout.drawText → TextLine.drawRun`.
  One `Text` held all 200k lines, and each line it drew handed the whole 1.3 MB string to native code, so
  drawing cost lines × length.
- **The layout shapes every line once.** With the draw repaired, what's left is
  `StaticLayout.generate` (70.6%). That cost is linear, but at 200k lines it takes ~27 s whatever the
  text is split into.

Both notebook front ends bound what a runaway draws, and they bound different things. JupyterLab counts
**outputs**: an output at index ≥ `maxNumberOutputs` (50 in a notebook,
jupyterlab@7f0e690 `packages/notebook-extension/schema/tracker.json`) gets no widget, only a
`Show more outputs` button (`packages/outputarea/src/widget.ts`, `_insertOutput`, `Private.TrimmedOutputs`).
VS Code counts **lines**: past `notebook.output.textLineLimit` (30) it draws the first `limit - 5` lines,
`...` and the last 5 (vscode@3f3380c6 `extensions/notebook-renderers/src/textHelper.ts`,
`truncatedArrayOfString`). A scrolled output draws its last `softScrollableLineLimit` (5000) lines
(`scrollableArrayOfString`). After the fold, a print loop is one output, so only a line cap bounds it.
300 `display()` calls are 300 short outputs, so only a count cap bounds them.

## Decision drivers

- A notebook saved here is the file JupyterLab would save from the same run.
- The editor stays responsive while a runaway prints and after it ends.
- Nothing the kernel sent is out of reach: search, the collapsed preview and a save read all of it.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **Cap the view, keep the model** (JupyterLab, VS Code) | The file keeps every line; search and save are unchanged | Memory grows with the run |
| Cap the data, as a ring (IntelliJ's run console `idea.cycle.buffer.size`, Termux's transcript) | Bounds memory | The saved notebook loses output another front end would keep. Both precedents are a console and a terminal, not a notebook |
| One cap only, by count or by lines | Half the code | Each one misses the other's runaway |

## Decision

**What is drawn is cut. The model, the file, the search and the preview keep every output and every line.**

- **The line cut is VS Code's:** past `OUTPUT_LINE_LIMIT` (30) lines, a text output draws its first 25
  and last 5, and between them an `N lines hidden` row draws the rest
  (`app/src/main/java/com/jupydroid/app/ui/editor/OutputLineWindow.kt`, `outputLineWindowOf`). A final
  `\n` ends the last line rather than opening an empty one. VS Code's split counts it, so its tail spends a
  row on nothing after every `print`.
- **The count cut is JupyterLab's:** past `OUTPUT_COUNT_LIMIT` (50) outputs, the rest stay undrawn behind
  `Show more outputs` (`OutputCountLimit.kt`, `drawnOutputCountOf`). As JupyterLab's `onInputRequest`
  does, the cap reaches the last stdin echo, so an answer to `input()` is always drawn.
- **Expand in place, per output**, and the expansion lasts as long as the output does (`rememberSaveable`
  in `CellOutputsView.kt`). VS Code opens the whole output in an editor, and there is no document surface
  for an output to open into.
- **An output drawn whole is one `Text` per 1000 lines** (`OUTPUT_CHUNK_LINES`, `outputTextChunksOf`), so
  a drawn line copies its chunk and not the whole output. The one layout pass is still paid. On
  SM-X216B it draws a 200k-line print's every line in ~27 s and then scrolls.
- **A scrolled box draws its last 5000 lines** (`SCROLLED_OUTPUT_LINE_LIMIT`, `outputTailWindowOf`), under
  the same row. The box bounds the height but not the layout. With every line in it, a scrolled 200k-line
  print stalled the UI thread for 1388 frames (~23 s). With the tail alone, the longest stall was 93
  frames (~1.5 s).
- **Colour is cut after the SGR walk** ([ADR-0083](0083-output-colour-reuses-the-terminals-sgr-walk.md)).
  VS Code parses the tail alone, so its tail loses a colour that was set in the hidden middle.
- **Stepping onto a hidden search hit draws its output; typing a query draws nothing.** Search reads
  the model, so a hidden line is counted. A step with the find bar's arrows expands that output
  (`SearchState.currentStepped`), and a typed query only lands on the hit. Expanding a 200k-line output
  costs its whole layout, which a keystroke should not trigger. Both references search only the drawn
  DOM, so for them a hidden line is never a hit.
- **The Console draws through the same `CellOutputs`**, so it cuts as a notebook cell does
  (`ConsoleTranscriptViewTest`).

## Consequences

On SM-X216B the 200k-line print finishes and scrolls: the cell says "ran in 3.2 s", against no `[1]:`
in 300 s before. 300 `display()` calls draw 50 rows, with p90 frames at 20 ms. The file on disk still
holds all 200k lines and all 300 outputs, so JupyterLab opening it shows what it would have shown if it
had run the cell itself.

The model is unbounded, as it is in both references. The server's iopub limits (1000 msg/s, 1 MB/s in the
shipped rootfs's `jupyter_server/services/kernels/connection/channels.py`, `limit_rate`) are the only
bound on what comes in.

What a future reader will be tempted to "fix":

- **Dropping output from the model to save memory.** The saved file would then differ from the one
  JupyterLab saves, and search would miss what was dropped.
- **Drawing an expanded output as one `Text`.** Every drawn line copies the whole string through JNI on
  Android (`CellOutputsViewTest.aLongOutputDrawnWholeHoldsAChunkOfLinesPerText`).
- **Tokenising a stream with `Regex.findAll`.** On ART each match copies the input
  (`StreamFoldOnArtTest.oneMessageOfManyLinesFoldsInLinearTime`, which timed out at 5 s with the regex and
  runs in 0.27 s with the scan by index). A desktop JVM doesn't copy, so a JVM test can't catch this.
- **Expanding on a typed query.** Each keystroke that lands on a hidden hit would pay a full layout
  (`CellOutputsViewTest.steppingOntoAHitInTheHiddenLinesDrawsThemAll` pins that a hit made current but
  not stepped onto keeps the cut).

Not covered:

- One line that never ends (`print(x, end="")` in a loop). Neither reference cuts within a line.
- `update_display_data` replacing an output in place.
- Scrolling to a match's line inside a tall output.
- The scrolled box opens at its top. VS Code's `initializeScroll` scrolls a scrollable output to its
  bottom.
