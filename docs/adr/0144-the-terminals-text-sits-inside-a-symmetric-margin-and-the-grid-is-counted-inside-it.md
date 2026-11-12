# ADR-0144 — The terminal's text sits inside a symmetric margin, and the grid is counted inside it

Status: accepted
Date: 2026-11-12

## Context

The terminal pane insets its text from the pane's left and right edges by `Spacing.md`, 12dp
(`app/src/main/java/com/jupydroid/app/ui/terminal/TerminalPaneScreen.kt`). Three things must agree on that
number: the padding the canvas is drawn inside, the width `terminalGridSize` counts columns of (which is the
column count sent to the pty), and the offset a press subtracts to find its cell. Until now the number was
written at two sites, tied by a comment, and nothing failed if either moved.

The references disagree on whether to inset at all:

| Reference | Inset | Where |
|---|---|---|
| JupyterLab | 8px, all four sides | `jupyterlab@7f0e6906 packages/terminal/style/base.css:12` |
| VS Code | 20px, left only, "for the gutter" | `vscode@3f3380c6 src/vs/workbench/contrib/terminal/browser/media/terminal.css:76-77` |
| IntelliJ | the editor gutter's width; the scroll pane's own border is empty | `intellij-community@406a27a8 plugins/terminal/src/org/jetbrains/plugins/terminal/block/ui/TerminalUiUtils.kt:115` |
| Termux, JediTerm | none; the view's size is the grid's | `termux-app@3df69d1d terminal-view/src/main/java/com/termux/view/TerminalView.java`, `jediterm@377b76e6 ui/src/com/jediterm/terminal/ui/TerminalPanel.java` |
| Winlator | draws no terminal | — |

Three of seven inset. The desktop references do it because a selection or a cursor at the very edge is hard
to hit, which holds on a touch screen as well.

Measured on the arm64 tablet (`SM-X216B`, 240 dpi) from a screenshot of a ruler row, a line of `#` as wide as
the pty's own `TIOCGWINSZ` column count, and the layout dump taken with it:

| | pane | left gap | right gap | glyphs drawn | columns at that pitch | columns the pty reports |
|---|---|---|---|---|---|---|
| landscape | 1146 px | 12.0 dp | 22.7 dp | 14.81 px apart | 74 | 74 |
| portrait | 1200 px | 12.0 dp | 28.7 dp | 14.82 px apart | 78 | 77 |

The margin is 12.0dp on the left at both widths, and the columns the shell is told equal
`floor((pane - 2 x 12dp) / 15 px)` at both. The right gap is not the margin: a run is drawn at the font's own
advance, 14.8 px, from a cell origin counted at the cell's whole-pixel width, 15 px
(`terminalCellMetrics` returns the `IntSize` of a measured "X"). The last glyph of a 77-column line is drawn
14 px left of its cell, and at 800dp the width holds 78 drawn columns where the shell is told 77. Termux keeps
that width a float (`termux-app@3df69d1d terminal-view/src/main/java/com/termux/view/TerminalRenderer.java`,
`mFontWidth`).

## Decision drivers

- What is drawn, what the shell is told and what a press maps to are one number, so a test fails when they stop
  agreeing.
- A phone has few rows and few columns, so every dp of inset costs one of them.
- A touch screen needs the edge column to be pressable and selectable, as the desktop references find.

## Options considered

| Option | Pro | Con |
|---|---|---|
| No inset (Termux, JediTerm) | Every column and row the screen can hold | A cursor or selection handle at the screen's edge is hard to reach |
| Left only, 20px (VS Code) | Fewer columns lost than an inset on both sides | The left inset is VS Code's gutter alignment; a phone has no gutter, so it serves nothing, and the right edge is as hard to hit as the left |
| All four sides (JupyterLab) | Even margin | A vertical inset costs rows, the scarcer axis on a phone, and the extra-keys row below the grid already supplies the bottom edge |
| Left and right, 12dp, grid counted inside | Both side edges reachable; no row lost; columns equal what is drawn inside the margin | Three columns fewer than no inset at the tablet's 15 px cell (24dp is 48 px) |

## Decision

**The terminal's text is inset 12dp (`Spacing.md`) from the pane's left and right edges, with no vertical
inset, and the grid is counted from the width inside the inset.** The value has one name, `terminalMargin` in
`TerminalGridSize.kt`. The canvas pads by it, `terminalGridSize` counts inside it, and a press subtracts it.

This departs from VS Code's left-only 20px: the reason for that rule is a gutter this app does not have.

Two tests pin it, and each is red by mutation. Removing the canvas's padding fails the columns pin, which
asserts the columns equal `(width - 2 x margin) / cell`. Removing the offset from `terminalCellAt`'s argument
fails the press pin, which taps at `margin + half a cell` and expects the word at that column. The press pin
cannot see the padding, because it adds the margin the pane subtracts.

## Consequences

The margin itself is measured and correct on the tablet. Nothing on screen changed.

The right gap is wider than the left by the drift between the font's advance and the cell's pixel width, up to
28.7dp at 800dp. That is a rendering defect and not part of this decision: `GAP-092` records it. A fix that
draws glyphs at the pitch the cells are counted at moves the right gap toward 12dp and may change the column
count the shell is told, so it is its own change with its own measurement, and it does not supersede this
decision.

The thing a future reader will be tempted to do is to widen the right inset until the gap looks even. That
would hide the drift behind a number that is correct on the left, and it would move with the font size.
