# ADR-0033 — A heading owns a section, and what a collapsed one hides is derived rather than stored

Status: accepted
Date: 2026-08-18

## Context

Heading collapse is the third fold and the only one that is not about the cell it is asked of.
`setHeadingCollapse` walks **forward** from a heading and stops at the first heading of equal or
higher level, so a heading owns the cells between it and its next sibling: an H1's section swallows
the H2 under it, and a code or raw cell can never end one, because `getHeadingInfo` answers
`headingLevel: 7` for anything that is not markdown.

**The level is parsed, never pattern-matched.** `MarkdownCell.headingInfo` takes the
**lowest-numbered** heading anywhere in the cell, over headings obtained by *rendering* the source and
reading back `<h1>`–`<h6>`. So a setext underline is a heading, a `#` inside a fence is not, and a cell
opening on `###` and going on to `#` heads an H1 section. **Top level only**: `getHeadingTokens`
inspects the type of each token the lexer returns and never descends, so a heading inside a blockquote
or a list item is not the cell's heading.

**Only the heading's own flag is persisted.** `headingCollapsed` writes
`jp-MarkdownHeadingCollapsed` at the *top* of the cell's metadata — a third key, not inside `jupyter` —
and **deletes** it rather than writing false. No cell ever records that it is hidden, and the reference
says why in its own words: `_refreshCollapsedHeadingVisibility` is *"Recompute hidden cells from the
current collapsed heading structure"*, called after `moveCells` reorders.

## Decision drivers

- Every insert, delete, move and undo must leave visibility correct, without each of them knowing this
  feature exists.
- A heading's level is a parsing question, and guessing it with a regex gets fences and setext wrong.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Store a hidden flag per cell | Cheap to read | Every reorder, insert, delete and undo has to maintain it. The reference recomputes rather than maintains, and still needs an explicit refresh call after `moveCells` |
| Recompute on each structural change, as the reference does | Matches upstream | Something must remember to call it; the reference's own bug surface |
| **Derive** hidden-ness as the union of every collapsed heading's section | Correct by construction for every operation, including ones not yet written | A derivation on read rather than a lookup |
| Match headings with a regex | Trivial | A `#` inside a fence becomes a heading; a setext underline does not. Both wrong |

## Decision

Hidden-ness is **derived**: the union of every collapsed heading's section. Nothing records that a cell
is hidden, so every insert, delete, move and undo is correct without knowing the feature exists.

The union also gives the reference's `localCollapsed` branch for free: expanding an H1 over a
still-collapsed H2 shows the H2 and leaves the H2's own children hidden, because that H2 is still in the
union on its own account. Measured on screen rather than reasoned about.

The level comes from `MarkdownDocument.headingLevel`, over the same commonmark AST the cell already
renders from — with one narrowing this app already had: an HTML `<h1>` block is dropped rather than
counted, because `MarkdownDocument` has never modelled HTML blocks.

**The operand is the head, not the marked run**, which is the reference's own narrowing —
`toggleCurrentHeadingCollapse` reads `notebook.activeCell` and never the selection. It keeps the run for
the same reason the other folds do, with one repair the reference never needs: a heading *inside* a
marked run can swallow the head, and a head nothing draws is one the ranged runs would still compute a
range from, so **both ends move onto the heading**. The reference's `expandParent` repairs the same
invariant by expanding, which is the wrong half here — expanding is what the user did not ask for.

**A collapsed heading is its whole section for four commands and no others.** `Private.selectedCells`
adds a collapsed heading's children to what is copied, cut or duplicated, and `deleteCells` marks them
for deletion. Nothing else does — `changeCellType`, `mergeCells`, `moveCells`, `clearOutputs` and every
fold iterate `isSelectedOrActive` and stop. The asymmetry is deliberate: those four *remove or
reproduce* the heading, so leaving its section behind would orphan cells under a heading that has gone,
while the rest leave the structure standing.

**Paste is the fifth site and it is a position, not an operand**: `addCells`'s `below` and
`belowSelected` branches add the child count to the index, so a cell pasted under a collapsed heading
lands *after* its children rather than somewhere nobody can see.

**The sixth is inert here, and that is the model rather than an omission.** `Notebook.deselect` clears a
per-cell selected flag on a collapsed heading's children; ours has no per-cell flag to clear, a
selection being an anchor and a head ([ADR-0029](0029-a-selection-is-a-contiguous-run.md)), so there is
nothing that could be left marked. The child count is not stored either — the reference recomputes it
inside `setHeadingCollapse`, and here it is the section's size, which is where that number came from.

**A heading level is a change of type carrying a rewrite, and one row stands for six.**
`notebook:change-cell-to-heading-1..6` are not six behaviours: `setMarkdownHeader` is
`changeCellType(notebook, 'markdown', { headingLevel })`, so every rule of a change of type applies —
the id survives, the outputs and execution count do not, and the cell is left showing its source in
command mode. On top of that, one rewrite: `Private.setMarkdownHeader` replaces `^#+\s*` — or, failing
that, `^\s*` — with the marker. One anchored regex with two alternatives, so it strips a header the cell
already had *or* the leading whitespace it did not, and touches nothing past the first line.

**An inserted heading takes the *section's* level, and "below" means the end of the section.**
`determineHeadingLevel` answers the head's own level if it is a heading and its *parent heading's* if it
is not, falling back to 1 when there is no heading at or above it — so a heading asked for from a code
cell inside `## Part A` is an H2. And `below` inserts at `findLowerEqualLevelHeadingBelow`, the start of
the next *sibling* section, so the new heading **closes** the section it was asked from rather than
cutting it in half. Measured on disk.

**The three folds are a category, not three commands that rhyme.** `CellAction.Fold` exists because
`hideCode`/`showCode`, the output collapses and `toggleCurrentHeadingCollapse` share both things the
dispatch cares about: none has a `deselectAll()`, so all three keep the marked run, and each reads the
**head's** own state and sets from it, so a mixed run comes out uniform rather than inverted.

**Two adaptations, and each names what it departs from.** The reference surfaces none of its eleven
heading commands in a menu — command palette and keyboard only — so every surface here is ours. The
collapser is a chevron on the cell header where the reference appends a button to the *prompt node*, and
it is `ChevronRight`/`ExpandMore` rather than `ExpandLess`/`ExpandMore`, because a section is a tree node.

## Consequences

Because hidden-ness is derived, a future cell operation gets collapse behaviour right **without knowing
collapse exists**. That is the whole return on the derivation, and it is lost the moment anything caches
the result.

The four-command asymmetry is the most likely thing to be "made consistent". Extending it to
`moveCells` or `changeCellType` would take a section's cells somewhere the user did not ask for.

The thing a future reader will be tempted to "fix" is parsing the heading level through the markdown
AST, which looks heavy next to a regex. The regex gets fenced `#` and setext underlines wrong, and the
reference renders for the same reason.
