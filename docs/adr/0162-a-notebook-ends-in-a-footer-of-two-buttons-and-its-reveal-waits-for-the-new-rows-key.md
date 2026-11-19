# ADR-0162 — A notebook ends in a footer of two buttons, and its reveal waits for the new row's key

Status: accepted
Date: 2026-11-19

## Context

Adding a cell, the most repeated notebook act, was five rows of a cell's `⋮` menu among fourteen
(`CellActionItems.kt` `addingItems`), and `NotebookBar.kt` had no control for it. VS Code draws a "Code" and a
"Markdown" button in a between-cells toolbar and in the notebook's top toolbar (`vscode@3f3380c668d0`
`src/vs/workbench/contrib/notebook/browser/controller/insertCellActions.ts:236-350`, default `both`,
`notebook.contribution.ts:1049-1062`). JupyterLab draws a footer after the last cell
(`jupyterlab@7f0e690680a7` `packages/notebook/src/notebookfooter.ts:25,47-53`). `vscode-jupyter` and
`colab-vscode` offer a command and no button; IntelliJ's community tree has no notebook UI.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **A footer after the last cell, one button per kind** | Always on screen where a reader scrolls to append; no hover; no second tap to choose a kind | Appends only at the end |
| A between-cells toolbar (VS Code) | Inserts anywhere | Appears on hover, which a touch screen has not; always drawn it costs vertical space on a 640dp-high phone |
| One "add" button | One control | A second tap to choose the kind |

## Decision

**The notebook's last list item is `AddCellFooter`: a "Code" and a "Markdown" button.** A tap names a kind and
never an index; `appendCell` (`NotebookCellEdits.kt`) fixes the position at the document's end, so a cell hidden
in a collapsed section cannot move it, and leaves the rest to `insertCellAt`: the new cell is the active one and
the append is one undo step. The cell menu keeps its rows, as the references keep their commands. A control
between cells or at the top stays open until the footer's cost is measured on a phone.

**The new cell is scrolled whole into view by its list key, not by an item count** (`AppendedCellReveal.kt`
`appendCellInView`). `CellRevealScroll` scrolls only in `EDIT` mode and an appended cell is active in `COMMAND`
mode, so nothing scrolled: on a 320x640dp window the list spans y 136-536 and the new cell's editor was cut at
536. A wait for the list to reach the new row's *index* reads true at once, because the footer is an item and the
count is already past that index before the new row exists. `AppendedCellRevealTest` pins it by asking in the
frame the row is added, as the tap does. A cell inside a collapsed trailing section has no row and scrolls
nothing.

No notebook is empty (`withCellsDeleted` leaves one code cell) and none is read-only (`EditorSettings.readOnly`
locks no surface), so the footer has neither an empty-notebook nor a read-only state.

## Consequences

- `scripts/add-cell-footer-driver.sh` runs `.maestro/add-cell-footer.yaml` at font scale 1.0 and 2.0 on a
  device, checks the saved notebook after an undo and a kill, and reads the two scales' dumps with
  `scripts/read_font_scale_fit.py`; it is run on the phone-sized `dev36` and the tablet-sized `tab36`.
- Read off `dev36` at 320x640dp: the labels span x 28-63 and 95-163 at font scale 1.0 and x 28-91 and
  123-249 at 2.0, a 32dp gap, none cut. On `tab36` at 1280x800dp the buttons stay left under the last cell.
  At 2.0 the fit reader found no new overlap and, on the tablet, two targets newly under 48dp, the cell
  header's run and `⋮` controls at 46.7dp; neither is the footer's.
- `AddCellFooterTest` asserts at 2.0 and 320dp that neither label is cut or broken inside a word and the two
  buttons do not overlap.
- A future reader will be tempted to wait for a count in the reveal, or to scroll from `CellRevealScroll`. The
  first never waits; the second runs only while a cell is being typed into.
- Not decided: a control between cells or at the top.
