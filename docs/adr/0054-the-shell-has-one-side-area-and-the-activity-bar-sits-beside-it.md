# ADR-0054 — The shell has one side area, and the activity bar sits beside the whole thing

Status: accepted; its 1280dp widths are the Expanded ones, and the list pane is wider at Large per [ADR-0141](0141-the-list-pane-takes-the-librarys-width-class-width.md)
Date: 2026-08-22

## Context

This app had drawn **two half-answers**: the table of contents on the *right* of the notebook, and the
explorer in a pane with no panel chrome. JupyterLab has a single left sidebar holding File Browser (rank
100) and Table of Contents (rank 400) among others.

**And the activity bar is beside the whole shell, not inside the side area.** An earlier plan row asked for
a rail "at the left edge of the list pane". Reading `shell.ts` corrected it: `hboxPanel` holds
`leftHandler.sideBar` next to the split that contains *both* the panel and the document area.

**The panel cannot reach the document's model.** The shell's side panel lives in the **list pane** and a
notebook's headings are derived in the **detail pane's** `EditorViewModel`. Every `NavEntry` owns its own
`ViewModelStore`, so the panel can reach neither that ViewModel nor the editor's `LazyListState` — the same
wall that ruled out an extra pane
([ADR-0037](0037-the-table-of-contents-is-two-presentations-of-one-derived-tree.md)).

## Decision drivers

- One home for "a surface beside the document", so a third panel costs nothing structural.
- Nothing may be derived twice.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep the ToC on the right and the explorer chrome-less | No change | Two half-answers, and a third panel would need a third place to live |
| Rail inside the list pane | Reads naturally | Wrong against `shell.ts`, and it is what stops the side area collapsing to the bar alone |
| One side area, rail beside the split | Matches the reference, and the area can collapse to the bar | Something must carry the document's headings across a `ViewModelStore` boundary |
| Let the panel derive the headings itself | No plumbing | Derives the same thing twice, from a source it would have to re-read |

## Decision

**One side area, and the rail beside the split that holds both it and the document area** — which is where
`hboxPanel` puts `leftHandler.sideBar`. That correction is load-bearing rather than cosmetic: it is what
lets the area collapse to the bar alone.

`TableOfContentsPane`, `TocPresentation` and `tocPresentationFor` are gone, and the rule they encoded — *is
the editor 840dp wide* — is replaced by *does the shell have a side area*.

**The side panel reads a model the document publishes**, because it cannot reach it. The reference has the
same seam and the same answer, and it is a settable, nullable model:
`TableOfContentsPanel.model: TableOfContents.Model | null`, assigned from outside by an extension listening
to `labShell.currentChanged`. `OpenDocumentOutline` is that property, and `DocumentOutline` is that model
reduced to what a panel actually draws with — the title the header takes, the headings, the numbering and
the jump. **Nothing is derived twice.**

**840dp stays, and the rail is what settles it.** Measured on device at three widths with
`scripts/read_pane_widths.py`:

| Window | Columns |
|---|---|
| 1280dp | rail **80.0dp**, divider **1.3dp**, list pane **360.0dp**, spacer **24.0dp**, document **814.7dp** |
| 800dp | one column, 800.0dp |
| 320dp | one column, 320.0dp |

Everything left of the document is fixed, so the document absorbs any change in window width. In an
**800dp** window two panes would leave it **334.7dp** — narrower than the 360dp the single-pane layout was
designed for, which is the floor this threshold has always been argued from. So the tablet in portrait
keeps one pane.

## Consequences

A third panel is now a rail item and a panel body, with no structural question to re-answer.

The published-model seam is the one thing that crosses panes, and it is remembered rather than held on the
container, because the container outlives the activity while the published jump closes over a
composition's `LazyListState`.

The thing a future reader will be tempted to "fix" is putting the rail inside the list pane, which reads as
tidier. It is what stops the side area collapsing to the bar.
