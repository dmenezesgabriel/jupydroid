# ADR-0043 — Every search is a regex, and typing finds without moving the notebook

Status: accepted
Date: 2026-08-19

## Context

`Private.parseQuery` turns all four of the search box's inputs into **one `RegExp`**, so every search is
a regex and "Use Regular Expression" only decides whether the user wrote it or the app did.

The last of that function's rules — *"if the empty string is hit, the search logic will freeze the
browser tab"* — **cannot be transcribed literally**, and measuring is what said so:
`Pattern.compile("^", MULTILINE).matcher("").find()` is **false** on this JDK, where
`new RegExp('^', 'gim').test('')` is **true**. Read as written, a bare `^` would slip past the guard and
report a zero-width hit at every line start.

Separately, `startQuery` ends with `highlightNext(true, { from: 'selection-start', scroll: false, select:
false })`, and `activateNewMatch`'s **first line** is `if (!shouldScroll) return`. So a new query sets the
current match and does nothing else — no scroll, no active-cell change, no unhiding. `highlightNext()`
from the arrows passes no options, defaults `scroll` to true, and gets the whole reveal.

## Decision drivers

- A search box that scrolled the document on every keystroke would move the notebook out from under
  whoever is typing.
- A guard transcribed from JavaScript regex semantics has to be *measured* against Java's.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Treat a plain query as substring search | Simpler | Diverges from the reference in the four-way interaction of case, whole-word, regex and the escape set |
| Build one regex from all four inputs, as the reference does | Exactly the reference's behaviour | Its empty-match guard does not transliterate |
| Use Kotlin's `Regex.escape` | Idiomatic | Wraps in `\Q…\E` and has a seam on an input containing `\E`. The reference's escape set is kept character for character instead |

## Decision

**Every search is a regex**, built from all four inputs the way `parseQuery` builds it, with the escape
set kept character for character.

**The empty-match probe drops `MULTILINE` and the searching regex keeps it**, which is what makes the two
agree. `$` and every unanchored pattern already did.

**Typing finds and counts; only the arrows move the notebook.** That is the reference's own seam rather
than a convenience, and it is why finding and revealing are two separate pieces of work here.

Two consequences worth stating: **every cell is searched, hidden or collapsed** — `startQuery` builds a
provider per widget with no visibility filter at all — and the query is re-run after every edit, which the
reference names in its own comment (*"`startQuery` is also used as 'restartQuery' after each text
change"*).

**A match in rendered prose is counted and not lit.** `MarkdownCellSearchProvider` searches a markdown
cell twice — its editor, and the rendered DOM through a `GenericSearchProvider`. Here there is no DOM to
decorate, so a match in rendered prose contributes to the count and carries no highlight.

**A replacement is two steps, and Kotlin throws where JavaScript does not.** Both of the reference's call
sites build it the same way: substitute first when the query is a regex — `match.text.replace(query,
newText)`, so `$1` works — then apply `preserveCase` to *whatever that produced*, not to what was typed.

**The `output` filter searches what it can also show, and stops there.** `getFilters` declares two, both
**off** by default: `output` (`supportReplace: false`) and `selection`. The reference searches its rendered
DOM, so a DataFrame's cells are searchable there; here an output is one of three things and only `Text` is
a string, so the filter is narrower than the reference's and its label says so. The rest is `GAP-001`.

## Consequences

Any rule transcribed from a JavaScript regex must be **measured** against Java's, not read across. The
`^`-on-empty divergence is the one that was caught; there is no reason to assume it is the only one.

Because finding does not move the notebook, the reveal logic is separate and testable on its own —
[ADR-0044](0044-reaching-a-cell-reveals-it-in-two-strengths.md).

The thing a future reader will be tempted to "fix" is the hand-kept escape set, since Kotlin has
`Regex.escape`. It wraps in `\Q…\E`, and an input containing `\E` ends the quote early.
