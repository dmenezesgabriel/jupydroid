# Notebooks

A notebook is a list of **cells** in a `.ipynb` file in the workspace. Three kinds exist:
**code**, which the kernel runs; **markdown**, which renders as you read and edits as source;
and **raw**, which passes through untouched — no prompt and no Run. Tap a cell to put the caret
in it, and its text becomes the editor.

![The tour notebook open on a tablet: rendered markdown above a code cell with its `[ ]:` prompt, the type control and kernel indicator on the bar above, and the kernel bar below](images/notebooks-open-light.png#only-light)
![The tour notebook open on a tablet: rendered markdown above a code cell with its `[ ]:` prompt, the type control and kernel indicator on the bar above, and the kernel bar below](images/notebooks-open-dark.png#only-dark)

## Cells

- The control on the bar above the cells names the **active cell's type** — Code, Markdown or
  Raw — and changes it in one tap, the way the reference's `cellType` toolbar item does.

![The cell-type picker open on a tablet listing Code, Markdown and Raw](images/notebooks-cell-type-light.png#only-light)
![The cell-type picker open on a tablet listing Code, Markdown and Raw](images/notebooks-cell-type-dark.png#only-dark)

- Every cell's **⋮** opens its menu: insert above or below, move, duplicate, change type,
  change to a heading, merge, split, cut, copy, paste, fold the source or the outputs, clear
  the outputs, delete — and **Select cells**, which marks a run of cells so the bar above acts
  on them together.

![A code cell's menu open on a tablet from Select cells through Delete](images/notebooks-cell-menu-light.png#only-light)
![A code cell's menu open on a tablet from Select cells through Delete](images/notebooks-cell-menu-dark.png#only-dark)

- After the last cell, **Code** and **Markdown** append a new one. **Undo** and **redo** on the
  bar take back and redo cell operations.

## Running cells

- A code cell's **Run** executes it alone; while it works the control becomes **Stop**, its
  prompt reads `[*]:`, and the duration of the last run shows beside it as `ran in …`.
- The prompt is the cell's history: `[ ]:` never run, `[3]:` ran third, `[*]:` working,
  `[-]:` skipped. A markdown cell's Run renders it; a raw cell has no Run at all.

![A cell mid-run on a tablet: its prompt `[*]:`, the spinner and Stop where Run was](images/notebooks-running-light.png#only-light)
![A cell mid-run on a tablet: its prompt `[*]:`, the spinner and Stop where Run was](images/notebooks-running-dark.png#only-dark)

- The **bar along the bottom** is what you ask the kernel: **Run all cells**, **Interrupt
  kernel** while something runs, **Restart kernel**, **Restart and run all**, and a **⋮** with
  run-all-above and run-selected-and-below, the compound restarts, **Clear all outputs**, a
  console for the notebook, and the kernel's ends — change it, shut it down, or close the
  notebook and shut it down.
- The **kernel indicator** on the bar above names the kernel and what it is doing — **No
  Kernel**, **Starting**, **Busy**, **Idle** or **Restarting** — and opens the kernel picker.

## Outputs

- What a cell prints or returns lands under it: stream text, rendered HTML, images, errors —
  the kernel's answer, kept in the file so a reopened notebook shows it again.

![Two executed cells on a tablet: `[2]:` and `[3]:` prompts, `ran in` timings and the text each printed](images/notebooks-outputs-light.png#only-light)
![Two executed cells on a tablet: `[2]:` and `[3]:` prompts, `ran in` timings and the text each printed](images/notebooks-outputs-dark.png#only-dark)

- A code cell's ⋮ folds its outputs away or clears them, and **Enable output scrolling** bounds
  a long run; the kernel ⋮'s **Clear all outputs** empties every cell at once.

## Completions

- Typing in a code cell offers completions from the kernel — keywords, builtins and the names
  the session already knows — and a tap inserts the pick. The popup never boots a kernel by
  itself: it answers only once one is running.

![The completion popup open on a tablet over a code cell, offering print](images/notebooks-completion-light.png#only-light)
![The completion popup open on a tablet over a code cell, offering print](images/notebooks-completion-dark.png#only-dark)

## Saving and checkpoints

- Edits mark the tab dirty — a dot where its ✕ was, and **Unsaved changes** for a screen
  reader. **Autosave** is on by default and writes dirty documents back on an interval; the
  tab's own save button writes now, and **Save All** beside the open tabs in
  [Sessions and Tabs](interface.md#the-side-panels) writes every dirty one.

![The Save File As dialog on a tablet with Notebook Tour copy typed as the new name](images/notebooks-save-as-light.png#only-light)
![The Save File As dialog on a tablet with Notebook Tour copy typed as the new name](images/notebooks-save-as-dark.png#only-dark)

- Every open and every deliberate save leaves a **checkpoint**. A tab's long-press menu reverts
  the document to it — the state it was opened or last saved in — after asking first.

![The Revert Notebook to checkpoint dialog on a tablet](images/notebooks-revert-light.png#only-light)
![The Revert Notebook to checkpoint dialog on a tablet](images/notebooks-revert-dark.png#only-dark)

- **Save As…** on the same menu writes the document under a new path and opens the copy.

## Exporting

- The notebook ⋮'s **Save and Export Notebook As…** asks the Space which formats its
  `nbconvert` offers — the list arrives when the picker opens — and writes the conversion into
  the workspace.

![The notebook's actions menu open on a tablet with Save and Export Notebook As at its head](images/notebooks-menu-light.png#only-light)
![The notebook's actions menu open on a tablet with Save and Export Notebook As at its head](images/notebooks-menu-dark.png#only-dark)

![The export picker on a tablet listing the formats the Space offers](images/notebooks-export-light.png#only-light)
![The export picker on a tablet listing the formats the Space offers](images/notebooks-export-dark.png#only-dark)

![The export report on a tablet: Exported to Notebook Tour copy.md](images/notebooks-exported-light.png#only-light)
![The export report on a tablet: Exported to Notebook Tour copy.md](images/notebooks-exported-dark.png#only-dark)

For the chrome these documents live inside — the rail, the panels and the tab strip — see
[Interface](interface.md); for the Spaces they run in, see [Spaces](spaces.md). The
[first-run walkthrough](getting-started.md) shows a notebook's first cell being typed and run.
