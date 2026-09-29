# ADR-0061 — Replace across files goes through the open document's model, not the filesystem

Status: accepted
Date: 2026-08-25

## Context

An earlier draft put `replaceAll` on `WorkspaceSearcher`, arguing that finding and replacing are one feature
*"because the reference's replace-all walks the results the search just produced"*. **Reading the reference
retired that argument, and the method with it.**

`ReplaceService.replace` builds `ResourceTextEdit`s and hands them to `IBulkEditService`.
`BulkTextEdits._createEditsTasks` then calls `_textModelResolverService.createModelReference(uri)` for **every**
resource in the set — open or not — edits the **models**, and only afterwards saves each one. A notebook cell
resolves its notebook model and saves that.

So there is no "write the file, then tell whoever has it open". **The document *is* what is edited**; where
none exists, one is resolved for the occasion.

## Decision drivers

- A replacement must reach a document that is open, not the stale bytes under it.
- An undo must put back what the user can see, not only what is on disk.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `replaceAll` on the searcher, writing files | Finding and replacing in one place | Writes under an open document, which then either overwrites the change or shows stale text |
| Write files, then notify open documents | Keeps the searcher's shape | Invents a notification the reference does not have, and races the document's own debounce |
| Resolve a model per resource and edit **that**, open or not | The reference's own mechanism | The searcher loses `replaceAll`, and the registry of open documents becomes load-bearing |

## Decision

**A replacement goes through the document's model.** `WorkspaceSearcher` carries `search` and **only** search —
`ISearchService.textSearch` and nothing else; replace has its own home.

**The open-document registry is *one* document, and that is a measurement** rather than a simplification: only
one document is open at a time in the case this had to serve, so the registry answers by current path, or
through `openedAs` by the path an entry opened under.

**A replace-all empties the tree on success and leaves it standing on a refusal.**

**An undo brings the *results* back, where the reference's cannot.** The reference's bulk edit is undoable as
text; its search results are gone. Here the results are part of what is restored, because on a phone the
results tree is the only thing that says what just happened.

**The undo sits beside the report, and that choice was made on lifetime** — the report outlives the panel's
composition, and an undo that did not would be offered and then vanish.

**`truncateValue` is declined rather than transcribed.**

**Material's `find_replace` icon reads as *refresh*, so Replace All is a labelled button**, and it is **drawn
disabled rather than hidden** — the one exception this project allows to "a control that can do nothing is
absent", because its absence would leave the replace row looking unfinished rather than unavailable.

## Consequences

`WorkspaceSearcher` is search-only, which is why its contract is small and why `SearchReplace` lives where it
does.

Because edits go through models, a replacement reaches an open document correctly and a closed one by resolving
a model for the occasion — the same path, which is the point.

The thing a future reader will be tempted to "fix" is putting `replaceAll` back on the searcher, because the two
features feel like one. The reference's own implementation is what says otherwise.
