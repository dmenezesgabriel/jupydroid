# ADR-0083 — Colour a cell's output with the terminal's SGR walk and VS Code's renderer rules

Status: accepted
Date: 2026-10-09

## Context

A cell's text output often colours itself. IPython's traceback is mostly `ESC [ … m` escapes, and so
are a `print("\x1b[31m…")` and a `__repr__` from a library such as `rich`. Both notebook front ends
draw these in colour. JupyterLab runs every text and error render through `ansiSpan`
(jupyterlab@7f0e690 `packages/rendermime/src/renderers.ts`), and VS Code runs them through
`handleANSIOutput` (vscode@3f3380c6 `extensions/notebook-renderers/src/ansi.ts`). Each has its own
parser. Ours stripped the escapes with a regex in `RenderedOutput.kt`, so every coloured output drew
monochrome.

[ADR-0039](0039-a-compose-native-cell-editor-not-a-webview.md) called that stripper and the
terminal's VT emulator "two ANSI paths, on purpose". It argued that the emulator would be absurd for
a traceback. But the emulator's SGR half is not a grid concern. `GraphicRendition` over
`EscapeParameters` (`app/src/main/java/com/jupydroid/app/terminal/`) turns one sequence's parameters
into a `CellStyle`. It is pure JVM, already tested against Termux's `selectGraphicRendition`, and
covers 16, 256 and truecolour colours, sub-parameters and the malformed forms.
`TerminalPalette.VS_CODE_DARK`/`LIGHT` already hold VS Code's resolved `terminal.ansi*` ramps.

The escape byte reaches the renderer. `KernelOutputsTest.outputOf_keepsTheEscapeByteOfAColouredTraceback`
reads wire JSON carrying `\u001b`. The writer stores it as `\u001b` and the reader gives it back
(`DiskNotebookRepositoryTest.saveAndRead_keepsTheEscapesThatColourAnOutput`). So a bare `[31m` with
no ESC before it is text, and neither reference parses it either.

## Decision drivers

- One meaning per escape across the app. A second parser is a second set of bugs.
- Fidelity to how a notebook draws an output, which `docs/architecture/cross-cutting.md` §8.3 makes VS
  Code's question for presentation and the palette.
- Search and the collapsed preview read the output's text, and that text must stay plain.
- The colours must be legible on whatever frame the output draws in.

## Options considered

| Option | Pro | Con |
|---|---|---|
| A regex in the output path, as both references have | Small, and local to outputs | A second SGR reading, which would disagree with the terminal on 256-colour indices, sub-parameters and malformed input |
| **The terminal's `GraphicRendition` over `EscapeParameters`, laying runs into a string** | One reading, already tested; the palette is shared too | The output path depends on `terminal/` |
| The whole VT emulator | Nothing new | A grid, cursor and scrollback for a string that has none of them |

## Decision

**A text output is colour-parsed by the terminal's own SGR walk, and drawn by VS Code's notebook
renderer rules on VS Code's terminal ramp.**

- `SgrText.parse` (`app/src/main/java/com/jupydroid/app/terminal/SgrText.kt`) feeds each `ESC [ … m`
  through `EscapeParameters` and `GraphicRendition`, exactly as the emulator does. It returns the text
  with every escape removed and a list of `SgrRun`s beside it. `RenderedOutput.Text.text` stays
  escape-free, so search and the preview are unchanged, and the colours ride in `runs`.
- **Every control sequence that is not a plain SGR is dropped**, as `ansiSpan` drops it: nothing in an
  output can move a cursor. A sequence the text ends inside is kept as written, as
  `handleANSIOutput` keeps it.
- **The palette is the ramp the terminal uses**, `TerminalPalette.VS_CODE_DARK`/`LIGHT`, provided
  with the theme as `LocalAnsiPalette`. The 256-colour cube is the xterm levels that palette already
  generates (JupyterLab's), not VS Code's `255/5` steps, so the app has one cube.
- **Bold is weight only.** VS Code's `.code-bold` keeps the colour it was given, where JupyterLab and
  a terminal promote a bold 0-7 to its bright twin. On the tablet, a `1;32` word draws in `ansiGreen`
  with no `ansiBrightGreen` pixel, in both themes.
- **The default colours are the output's own.** Indices 256 and 257 draw no span, so `onSurface` or
  the error colour shows through rather than the terminal's foreground. Inverse swaps only what is
  set. Dim, blink and hidden draw nothing, since VS Code's stylesheet gives them no rule
  (`OutputTextColours.kt` `outputSpanStyle`).
- **A coloured error is ruled, not tinted.** ANSI red `#CD3131`, most of an IPython traceback,
  measured **2.97:1** on the dark error tint `#3A1D1D`, which is below WCAG's 3:1. On the surface it
  is 3.61:1 in dark and 5.15:1 in light, and the surface is where VS Code draws a traceback, with no
  fill. So an error that carries colour draws on the surface with a 2dp error-coloured rule at its
  edge, and an error in one colour keeps the tint (`RenderedOutput.Text.frame()`).

## Consequences

The notebook, the Console and a reopened file all colour through one path, and the terminal and the
outputs can never disagree on what `38;5;208` means. Colour costs nothing on disk: the file keeps the
escapes the kernel sent, so any other front end opening it draws the same colours.

The output path now imports from `terminal/`. That package is document and language logic with no
`android.*` in it, so `SgrTextTest` and `OutputTextColoursTest` are plain JVM tests.

What a future reader will be tempted to "fix":

- **Replacing `SgrText` with a regex**, because both references have one. Ours would then disagree
  with the terminal on every edge the emulator's tests pin.
- **Brightening bold**, because the terminal does. A notebook output is VS Code's renderer, not a
  terminal, and `OutputTextColoursTest.outputSpanStyle_drawsBoldAsWeightAndKeepsItsColour` pins the
  difference.
- **Putting the tint back behind a coloured traceback.** It costs red its contrast in dark, as the
  2.97:1 measurement shows (`OutputTextColoursTest.frame_rulesAnErrorThatCarriesColourInsteadOfTintingIt`).

Not covered: underline colour (`58`), overline, superscript and subscript. `CellStyle` has no bits
for them, as in the terminal.
