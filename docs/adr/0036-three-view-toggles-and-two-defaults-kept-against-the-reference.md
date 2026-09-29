# ADR-0036 — Three view toggles, and two defaults kept against the reference on purpose

Status: accepted
Date: 2026-08-18

## Context

Three commands — `notebook:toggle-all-cell-line-numbers`,
`notebook:enable/disable/toggle-output-scrolling`, and
`fileeditor:toggle-autoclosing-brackets-universal` — each settle a default the reference makes one way
and this app makes the other. Both departures are deliberate rather than inherited, and a phone is why.

## Decision drivers

- A soft keyboard changes the cost of typing a character, so a default measured for a desktop does not
  transfer.
- Parity is the rule, so a departure has to say what it departs from and why.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Take every reference default as-is | No argument to have | `lineNumbers: false` hides a gutter this app has drawn since its fifth step, and `autoClosing: false` makes `(` and `)` two trips through the symbol row instead of one |
| Depart where a touch surface changes the cost, and state it | The defaults fit the device | Two departures to justify, and both must keep the reference's own *polarity* so the commands still read correctly |

## Decision

**The line-number gutter defaults to shown, against `lineNumbers: false`.** A phone shows a fraction of
a cell at once and a traceback names a line. The command turns it *off*, which is the reference's own
polarity started from the other side. One boolean for all three cell types, read off
`toggleAllLineNumbers`'s own conjunction, and never persisted by the command — the reference writes
`notebook.editorConfig`, which belongs to the widget.

**Auto-closing brackets defaults to on, against `default: false`.** On a soft keyboard `(` and `)` are
two trips through the symbol row rather than one. `PythonTypingRules` takes it as a constructor flag
gating `closePair` and `stepOverPartner` **only**; `continueIndent` is a different CodeMirror setting
there and must not move with it — a cell that closes nothing must still carry a block's indent onto the
next line.

**`metadata.scrolled` reads `'auto'` as false**, because JupyterLab does not implement it either:
`loadScrolledState`'s own comment is *"We don't have the notion of 'auto' scrolled, so we make it
false"*. Written only when true and deleted when false — the same rule `CellCollapse.kt` already carries
for `collapsed`, `jupyter.source_hidden` and `jp-MarkdownHeadingCollapsed`, a fourth key at the same
shape rather than a special case.

**`toggle-output-scrolling`'s operand is all-of over the marked run, not head-of.** Every other fold
reads the *head* cell's state and sets the whole run to its opposite. This one differs because the
reference does: `isOutputScrollingEnabled` is true only when **every** selected code cell is already
scrolled, so a mixed run of two turns *on* rather than inverting per cell.
`EditorUiState.allMarkedCodeCellsScrolled` carries that predicate apart from the toggle it feeds, and it
is the one fold whose write also filters to code cells at the transform itself
(`withOutputsScrolledSet`) rather than trusting the operand it was handed — the reference repeats the
`cell.model.type === 'code'` guard inside its own write loop rather than relying on the read that
decided to call it.

**Its menu row carries one fixed label, "Enable output scrolling", whatever the state.** That is literal
parity rather than this app's usual flip-with-state wording: the reference's own context-menu
registration for this exact command pins `args: {isMenu: true}`, which forces the label regardless of
`isToggled` — so a tap on it can disable an already-scrolled run even though it still reads "Enable".
It is the reference's choice for *this* surface, which is why the auto-closing-brackets row does not
copy it: there is no reference row on the surface that row occupies to have parity with.

**`resize: vertical` on a scrolled output is not built.** A drag handle is not a touch idiom, and there
is no pointer to hover it into view with first. Recorded rather than silently dropped.

## Consequences

Two defaults here disagree with the reference, and both disagreements are load-bearing for screenshots:
the gutter departure is what keeps every screenshot since this app's fifth step meaning what it always
meant.

The output-scrolling fold is the one fold that is not head-of, and the one label that does not flip.
Both look like bugs and both are parity.

The thing a future reader will be tempted to "fix" is `continueIndent` moving with the auto-close flag,
since both are "bracket behaviour". Tying them makes a cell that closes nothing also stop indenting a
block.
