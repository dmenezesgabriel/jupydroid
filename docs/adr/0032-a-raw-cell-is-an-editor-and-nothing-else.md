# ADR-0032 — A raw cell is an editor and nothing else, and running one does nothing

Status: accepted
Date: 2026-08-18

## Context

`raw` is the third `cell_type` nbformat 4 defines, and a notebook holding one **could not be opened at
all**: `CellFile.toDomain` threw and the editor showed the exception as the whole screen. JupyterLab
opens the same file without comment.

**It has no second state.** `RawCell extends Cell<IRawCellModel>` with no renderer, no output area and
no rendered/source toggle, and `RawCellModel` is the one cell model that sets **no** mimeType where
`MarkdownCellModel` sets `text/x-ipythongfm`.

**Running one does nothing.** `runCell`'s switch answers `markdown` and `code` and falls to
`default: break`. A range run therefore passes over it with no change of any kind — not queued, not
skipped, not failed.

## Decision drivers

- A notebook another tool wrote must open, whatever cell types it holds.
- A control that lights up and does nothing teaches the user that controls lie.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Treat `raw` as a code cell | No new type | It would be highlighted, completed and runnable, none of which the format means |
| Treat `raw` as markdown | Has a rendered state to reuse | A raw cell has no rendered state; the reference gives it no renderer at all |
| A third kind with no second state | Matches the format and the reference | A third change-type row, and the cell menu grows |

## Decision

`RawCellField` is **plain text**: no highlighting, no bracket matching, no completion, no typing
rules — and no placeholder, because `jp-mod-emptyMarkdownPlaceholder` is markdown's alone.

It keeps the line-number gutter, departing from the reference's `raw: { lineNumbers: false }` for
`MarkdownCellField`'s reason: the gutter counts lines, not code, and that departure was already made
for prose.

**A raw cell's header carries no Run control**, which is an adaptation with no reference line to depart
from — the reference has no per-cell Run at all, its cell toolbar being duplicate / move / insert /
delete, while ours is a mobile adaptation.

**`outputs` and `execution_count` on a raw cell are forbidden, not merely absent.** The shipped
`nbformat/v4/nbformat.v4.schema.json` makes `definitions.raw_cell` `additionalProperties: false` over
`id`, `cell_type`, `metadata`, `source` and `attachments`, so writing either produces a notebook
`nbformat.validate` rejects. The writer's existing `if (kind == CODE)` guard was already right; the
schema is why it must stay.

**And the change-type row had to become two.** "The type this cell is not" is a complete answer only
while there are two types; with three, one row names one of the other two and the third is unreachable
from the menu. The reference has a command per type — `notebook:change-cell-to-{code,markdown,raw}` —
so each cell offers both. A code cell's menu is eighteen rows as a result.

## Consequences

Read the schema before adding any field to a cell on write. `additionalProperties: false` on
`raw_cell` means a well-meant addition produces a file `nbformat.validate` rejects, and this app's own
reader would accept it.

The eighteen-row code-cell menu is a direct cost of the third type, accepted because an unreachable
change-type is worse.

The thing a future reader will be tempted to "fix" is the gutter, since the reference turns it off for
raw. It counts lines, and prose has lines.
