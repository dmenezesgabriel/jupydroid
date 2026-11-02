# ADR-0053 — A cell at rest carries no mark, and the editor's border is the cell's boundary

Status: accepted
Date: 2026-08-21

## Context

**What was there, and the argument for it.** `cellStripe` painted a resting grey line down the left of every
cell that was neither active nor failed, and both `CellStripe.kt` and `CellStripeTest` argued for it in the
same words: *"Every cell gets one, not only the selected one and not only code cells"* — on the grounds that
IntelliJ draws a *"vertical gray line for markdown cells in the gutter"*, and that on touch there is no hover,
so it is the only thing always on screen saying where one cell ends and the next begins.

**What the reference actually says, read rather than recalled**, is that a cell at rest carries no mark and
the **editor's own border** is the boundary. `.jp-InputArea-editor` carries
`border: var(--jp-border-width) solid var(--jp-cell-editor-border-color)` **unconditionally**, and the cell
being typed into swaps that colour for `--jp-cell-editor-active-border-color`, which is the brand colour.

Separately, three inventions had crept into the cell header:

- `CellHeader` drew the words **"markdown"** and **"raw"** where the reference draws nothing, and `[7]` where
  it draws `[7]:`. `InputPrompt`'s setter is ``this.node.textContent = `[${value || ' '}]:` `` — the trailing
  colon is part of the notation, and `[ ]:` is what an unrun cell shows because the empty string falls
  through `||` to a space. `_setPrompt` has **exactly one caller**, `CodeCell._updatePrompt`; `MarkdownCell`
  and `RawCell` never call it, so their prompt node is drawn and left textless.
- The heading caret was drawn as a control in a row above the heading, beside the Run button, with nothing
  on screen connecting it to what it folds. The reference appends it to the cell's **prompt node**.
- The document's name shared the app bar with undo, redo, search and the `⋮`, leaving it **64dp of the
  175dp** that `Heading Sections.ipynb` needs at 320dp — truncated to `Gutt…` in every phone screenshot this
  repository holds.

## Decision drivers

- An invented mark cannot be checked against anything, and this one had a test arguing for it.
- What the prompt draws sets where every cell's first glyph sits, so an extra word is a layout fault.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep the resting stripe | Touch has no hover, so a permanent boundary is defensible | It is an invention with a test arguing for it, and the reference's boundary is the editor's own border |
| Hold the border to WCAG 1.4.11's 3:1 against the cell | Accessible by the letter | **Measured: no ramp step that reads as a border reaches it.** The floor was asking for something that is not a border |
| Take the reference's border, and hold only what we chose ourselves to a floor | Parity, and the boundary is always drawn | The resting boundary is subtler than the stripe was |

## Decision

**A cell at rest carries no mark.** The editor's border is the boundary, drawn unconditionally by one
factory — `Modifier.cellEditorBox(editing)` — for all three field types, turning the brand colour for the
cell being typed into.

**3:1 is the wrong floor for it**, measured rather than assumed: no ramp step that reads as a border reaches
it against the cell. The floor applies to the colours this app chose for itself
([ADR-0041](0041-parity-with-the-resolved-themes-outranks-the-contrast-floor.md)), and the active border is
held to it; a resting border transcribed from the reference is parity.

**The prompt is the reference's, and a non-code cell has none.** `[7]:` with the colon, `[ ]:` when unrun,
and nothing at all on markdown and raw — so every cell header's leading mark sits at one x.

**The invented word cost more than a word.** It was the only thing in front of a heading cell's collapser, so
the collapser pushed it: two markdown cells in one frame started **48dp apart**, measured on device and
visible in committed screenshots for several phases before anyone read them.

**The heading caret belongs to the heading**, where the reference pins it, not in a strip above it.

**The document's name has a row of its own**, which is the mode the reference is in: `LabShell.mode`'s setter
shows a title panel above the document in `single-document` mode, and that panel holds the document's name
and nothing else.

## Consequences

Removing the stripe removed the only always-on boundary, and the editor border is subtler. That is the
reference's answer and the trade is accepted knowingly.

Because the prompt sets the leading x for every cell type, anything added in front of it re-introduces the
48dp raggedness. `cell-header-prompts` is the flow that measures it.

The thing a future reader will be tempted to "fix" is the resting border's contrast, since an audit will
flag it against 3:1. No border-like colour reaches 3:1 on the cell; the active border is the one held to a
floor.
