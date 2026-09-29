# ADR-0037 — The table of contents is two presentations of one derived tree

Status: accepted
Date: 2026-08-18

## Context

The reference has one answer: a sidebar tab, `shell.add(toc, 'left', { rank: 400 })`, with
`toc:show-panel` doing nothing but activate it.

That is a desktop canvas — a 32px icon strip beside a ≥250px panel — and **JupyterLab has never
adapted it narrow.** An exhaustive grep of `packages/application/{src,style}` and
`packages/ui-components/src` finds no media query, no width branch, and a `format: 'mobile'` property
that is declared and never assigned. There is no narrow version of it to have parity with.

## Decision drivers

- A notebook must not become narrower than the phone the single-pane layout was designed for.
- The width that matters is the one the notebook actually has, not the one the window has.

## Options considered

| Option | Pro | Con |
|---|---|---|
| One sidebar panel, as the reference has | Parity | At 840dp the explorer has already spent 360dp, leaving the editor ~456dp; a column out of that is narrower than the phone layout |
| `ListDetailSceneStrategy.extraPane()` | The library's own third pane | **A blocker, not a preference**: an extra pane is a separate `NavEntry`, and every entry here owns its own `ViewModelStore`, so a ToC entry could reach neither this notebook's `EditorViewModel` for the headings nor its `LazyListState` to scroll. It would force per-document state up into a shared owner. And the pane arithmetic does not help: three partitions arrive only at 1200dp+, and at two the extra pane displaces the **explorer** — the file browser would vanish to show a ToC |
| Two presentations of one model, chosen on the editor's width | A column inside the editor's own scene shares the ViewModel and the scroll state for free | Two surfaces to keep in step |

## Decision

Two presentations of one model — `TableOfContentsPane` beside the notebook, `TableOfContentsPanel`
over it — which is `CellActionPresentation`'s own split applied to a second picker rather than a new
rule.

**The width that decides is the *editor's*, never the window's.** `tocPresentationFor` takes the width
its caller measured with `BoxWithConstraints`, at the expanded lower bound of 840dp: the number this app
already means by "wide enough to split in two", applied one level down to the pane doing the splitting.
It is also the first width branch here that a test can drive both sides of —
`currentWindowAdaptiveInfo()` reads the activity's window, which nothing under a composable can force,
while a layout constraint is just a constraint.

**The tree is derived rather than kept.** `buildTree` takes as a heading's children the ones that follow
it until an equal or lower level appears — `headingSectionOf`'s rule one rank along. Nothing records that
a row is hidden, so adding, deleting or re-levelling a heading is correct here without knowing the
feature exists, for the same reason
[ADR-0033](0033-a-heading-owns-a-section-derived-not-stored.md) derives hidden cells.

**Folding here is not the notebook's fold, and that is the reference's own default.**
`syncCollapseState` is false, so an entry folded here hides rows in that list and leaves
`jp-MarkdownHeadingCollapsed` alone — measured on screen. Its lifetime is the reference's too: rebuilt on
every `getHeadings()` and never persisted, so `TocFolds` is remembered and not saved. **Collapse All is
narrowed to the entries that own something**, where the reference marks every heading; a leaf's flag
hides nothing either way.

**Hidden by default, which is the reference's own default** — the file browser is the sidebar panel that
opens with the shell, not this one. The View row flips its label the way the line-numbers row does, but
only in pane mode, since an overlay is dismissed by the pick and has no state left to name.

**Heading numbers go in the document, not only in the panel.** `toc:display-numbering`'s own label says
so — *"Show heading number in the document"* — and `addPrefix` writes the computed prefix into the
**rendered heading's own node**. A toggle that numbered the list alone would be a control whose label
lies. The numbers are the document's, so they are computed over every heading at once: `filterHeadings`
threads one `levels` array through every cell, which is why `tocPrefixes` takes the whole list and
`EditorContent` computes the map once rather than per cell. Two consequences — a folded-away entry
**still counts**, and the prefix a markdown cell draws is handed to it rather than derived from its own
source.

**A heading a cell printed is an entry, and it points at the cell rather than at the output.**
`CodeCell.getHeadings` walks a code cell's outputs for `<h1>`–`<h6>`, and the schema has `includeOutput`
**on** by default, so this app's default is on too. It reads what the output *renders as*, not the mime
bundle: `CellOutput.render()` already picks the richest type this app can draw, so taking the headings
out of its `RenderedOutput.Document` keeps an entry pointing at something actually on screen. That
narrows the reference by one type — `text/markdown` is not read, because nothing renders it here yet and
an entry for prose the output shows as plain text would be a promise it does not keep.

**An entry needs an identity that is not its cell**, and this is the first thing to need one: a code cell
can print several headings, so `TocHeading.key` is the cell's id for a heading in a source and
`"<cell>#<output>.<n>"` for one found in an output — which is `INotebookHeading`'s own `outputIndex`
under another name. Folds and Collapse All key on that; the jump still keys on `cellId`, because this app
scrolls a `LazyColumn` by item and its item is the cell. On a phone the cell and its output are the same
screenful, so the adaptation costs nothing.

## Consequences

The 840dp constant means two things at two levels — "split the window in two" and "the editor is wide
enough for a column" — and it is the same number on purpose.

The extra-pane route is closed for a structural reason, not a stylistic one. Anything that wants to share
a document's ViewModel and scroll state has to live inside that document's scene.

The thing a future reader will be tempted to "fix" is reading the window's width instead of the editor's,
since that is what every other breakpoint here does. At two panes the window is 840dp and the notebook is
456dp, and the notebook is what has to hold a column.
