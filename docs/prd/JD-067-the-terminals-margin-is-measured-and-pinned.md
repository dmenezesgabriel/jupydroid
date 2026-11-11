# JD-067 — The terminal's text sits inside a margin, measured and pinned

Milestone: `M11` · State: open · Depends on: — · Closes: —

## Outcome

The terminal's 12dp margin on each side is named once, a test fails if the grid stops being laid out
inside it, and the margin and the columns the shell receives are measured on the tablet. Nothing
changes on screen: the inset already ships, unpinned and unmeasured.

## Demo

On the arm64 tablet (`SM-X216B`), cold start:

1. Install, launch, open a Space, open a terminal.
2. Read the screenshot: the first glyph column starts 12dp from the pane's left edge and the last
   whole column ends at least 12dp from its right edge.
3. Run `vi` in the shell and read its reflow: the columns it draws equal
   `floor((pane width - 2 x margin) / cell width)`, which is what the PTY was told.
4. Rotate and reread: the same equation holds at the new width.

Screenshots, snapshots and `maestro.log` go in the local `evidence/` (ADR-0132); the commit body
quotes the numbers.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e6906 packages/terminal/style/base.css:12` | `.jp-Terminal-body { padding: 8px }`, all four sides |
| `vscode` | PARTIAL | `vscode@3f3380c6 src/vs/workbench/contrib/terminal/browser/media/terminal.css:76-77` | `.xterm { padding-left: 20px }` "for the gutter": left only, none on the right, top or bottom |
| `vscode-jupyter` | ABSENT | — | draws no terminal of its own |
| `intellij-community` | PARTIAL | `intellij-community@406a27a8 plugins/terminal/src/org/jetbrains/plugins/terminal/block/ui/TerminalUiUtils.kt:115` | the scroll pane's border is `JBUI.Borders.empty()`; the inset is the editor gutter's width, no fixed number |
| `termux-app` | ABSENT | `termux-app@3df69d1d terminal-view/src/main/java/com/termux/view/TerminalView.java` | the view fills its bounds; no padding read in the view or its layouts |
| `jediterm` | ABSENT | `jediterm@377b76e6 ui/src/com/jediterm/terminal/ui/TerminalPanel.java` | no inset read; the panel's size is the grid's |
| `winlator` | ABSENT | — | no terminal view (its one `terminal` match, `MSLink.java`, is a shortcut file) |

**Count:** 3 of 7 inset the terminal's text (JupyterLab on four sides, VS Code on one, IntelliJ by its
gutter); the two Android references and the Java one do not. The inset is common on desktop, where a
selection and a cursor at the edge are hard to hit, which is the case on a touch screen too.

## Implementation inventory

### Ours (what exists)

| Concern | Files |
|---|---|
| The margin | `app/src/main/java/com/jupydroid/app/ui/terminal/TerminalPaneScreen.kt:235` (`padding(horizontal = Spacing.md)`), `:135` (`canvasInsetPx`) |
| Columns and rows the PTY gets | `ui/terminal/TerminalCanvas.kt:117-119` (`BoxWithConstraints` inside the padding), `ui/terminal/TerminalGridSize.kt` (`terminalGridSize`), `TerminalPaneViewModel.resize` |
| A tap's cell | `ui/terminal/TerminalCellPosition.kt` (`terminalCellAt`), `TerminalPaneScreen.kt:136-139` |
| Existing tests nearby | `app/src/androidTest/java/com/jupydroid/app/TerminalCanvasTest.kt`, `TerminalPaneScreenTest.kt`, `TerminalCellMetricsTest.kt` |
| Existing flows | `.maestro/terminal-resize-before-rotation.yaml`, `terminal-resize-after-rotation.yaml`, `scripts/terminal-resize-driver.sh`, `scripts/terminal_content_extent.py` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Which edges | JupyterLab: all four; VS Code: left; IntelliJ: gutter | a vertical inset costs rows on a screen with few of them; a right inset costs a column | Left and right, 12dp, as built. A phone has no gutter to align a margin to, so VS Code's left-only rule has nothing to serve, and the key row below already supplies the bottom edge |
| How large | JupyterLab 8px, VS Code 20px | wider costs columns | 12dp (`Spacing.md`), kept: nobody measured a reason to move it |
| Where the grid is computed | inside the inset (here) or from the full width | inside keeps the PTY's columns equal to what is drawn | inside, as built, and pinned |

The decision outlives the item, so it becomes an ADR before this PRD is deleted.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Margin drawn | present, `TerminalPaneScreen.kt:235` | `vscode@3f3380c6 terminal.css:76-77` | none |
| Columns account for it | present, `TerminalCanvas.kt:117-119` | n/a | none |
| Taps account for it | present, `TerminalPaneScreen.kt:135-139` | n/a | none |
| One name for the 12dp | absent: `Spacing.md` is written at two sites, tied by a comment | n/a | the two can drift apart and a tap then lands one column off |
| A test that pins it | absent | n/a | nothing fails if the padding is dropped |
| A measurement | one reading, "inset 12.0", off a layout dump | n/a | no screenshot read, no columns read |

Ours is present and complete, and unpinned.

## Deviations

Symmetric 12dp rather than VS Code's left-only 20px, for the reason in the trade-offs table. The
deviation is recorded in the ADR this item ends with.

## Open decisions

None. The scope was settled with the user: measure and pin what exists, no visual change.

## Surface

| Layer | Files |
|---|---|
| `ui/terminal` | `TerminalGridSize.kt` (the named margin), `TerminalPaneScreen.kt` (both uses) |
| unit tests | none: the pins need a real layout |
| instrumented tests | `TerminalPaneScreenTest.kt` or `TerminalCanvasTest.kt`: columns and a tap's column |
| `.maestro/` flow + `scripts/` driver | the existing resize flows and driver, plus a reader only if the existing one cannot report the margin |

No port, domain or adapter change.

## Tasks

- [ ] T1 — One name for the margin, pinned: columns equal `(width - 2 x margin) / cell`, and a tap at
  `margin + half a cell` lands in column 0. Red by mutation: removing the padding fails both.
- [ ] T2 — Measure on the tablet: the margin in dp from the screenshot, and the columns the shell sees
  from `vi`'s reflow, at two widths.
- [ ] T3 — Close: a verdict in the ledger's terminal row, the ADR, the backlog row rewritten and
  `shipped`, this PRD deleted.

## Out of scope

A left-only 20dp gutter, a vertical inset, and the extra-keys row's own inset.
