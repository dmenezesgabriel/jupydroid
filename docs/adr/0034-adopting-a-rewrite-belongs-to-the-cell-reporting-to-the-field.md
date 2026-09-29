# ADR-0034 — Adopting a rewritten source belongs to the cell; reporting an edit belongs to the field

Status: accepted
Date: 2026-08-18

## Context

Both halves used to live in the field, and changing a cell's heading level proved that wrong.
`change-cell-to-heading` is the first command that rewrites a cell's source **and** changes its type,
so a `CodeCellField` became a `MarkdownCellField` over the same `TextFieldState` in the frame the
rewrite arrived.

The adoption counter is seeded with the revision it arrives on — correct while a new field always
meant a new state — so the replacement started **already up to date** and adopted nothing. The model
held `## title`, the collapser said so, and the editor showed the text from before.

**Both of the flow's earlier assertions were green; the screenshot is what said otherwise**, for the
second time in this project.

## Decision drivers

- A field is composed, disposed and replaced; the cell's text and the notebook's disagreement with it
  outlive all three.
- The mechanism must stay a counter, never a comparison.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Both halves in the field | One place | A field replaced in the same frame as a rewrite seeds its counter from the new revision and adopts nothing |
| Both halves at the cell | Symmetric | A report is about what the *field* did; hoisting it means the cell has to learn about keystrokes |
| Adoption at the cell, reporting in the field | Each half sits at the lifetime that matches it | Two call sites for one mechanism, and the split has to be stated |

## Decision

`adoptRewrittenSource` is called **where the `TextFieldState` is created**; `reportCellEdits` stays in
the field.

That is [ADR-0031](0031-splitting-reads-the-editor-so-a-cell-owns-one-text-state.md)'s rule — *a cell
owns one text state, not one per field* — followed one layer down: the field over that state is
composed, disposed and replaced, while the state and the notebook's disagreement with it outlive all
three.

Everything else about the mechanism is unchanged and still load-bearing: **a counter and never a
comparison**, because the model is routinely one debounce behind whatever was just typed.

## Consequences

Any future field type over the same cell inherits adoption for free, because adoption is not the
field's to do.

The thing a future reader will be tempted to "fix" is the asymmetry — one half at the cell, one in the
field — which reads as an oversight. Moving adoption back into the field reproduces exactly the defect
above, and no assertion in the flow catches it; only the screenshot does.
