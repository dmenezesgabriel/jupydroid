# ADR-0042 — A search match is a wash, a link is told apart by its underline, and the palette states its colours

Status: accepted
Date: 2026-08-22

## Context

Three findings about how the editor draws things, each of which reversed what this app was doing.

**VS Code states no foreground for a search match.** `editor.findMatchForeground` and
`editor.findMatchHighlightForeground` are both registered `null`, and the backgrounds are translucent so
the syntax colour underneath survives — the registration for `findMatchHighlight` says so in words,
*"must not be opaque so as not to hide underlying decorations"*. This app did the opposite: an opaque
fill with the text colour restated on top, which is JupyterLab's structure, taken after measuring that
IntelliJ's approach left a keyword at 3.16:1 inside a match.

**A link takes the string colour.** `string.other.link` resolves to `string`'s own value in both 2026
themes — `#a5d6ff` dark, `#0a3069` light — and VS Code carries the difference in `markup.underline`, a
rule that sets `fontStyle` and no colour at all.

**Syntax colours used to come from `colorScheme` roles**, so a number sat close to the body text and the
value ultimately came from the user's wallpaper — which nothing could assert.

## Decision drivers

- A transcribed palette is only worth transcribing if it is exact.
- A colour a Material role could supply is a colour no test can hold.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Opaque match fill with a restated foreground | High contrast inside the match | Hides the code, which is what the reference's own registration comment forbids |
| Translucent wash, no foreground | Parity, and the syntax colour survives | The current match is then barely distinguishable from the others |
| Give a link its own colour | Obvious, and easy to see | Departs from the reference to solve a problem the reference does not have |
| Derive syntax colours from Material roles | Themes for free | The value comes from the user's wallpaper; nothing can assert it |

## Decision

**A match is a wash.** `searchMatchText` and `currentSearchMatchText` are **deleted** rather than left
unread — there is no foreground to state. `bothKindsOfMatchAreAWashRatherThanAFill` asserts the alpha,
because translucency is now load-bearing: an opaque value here hides the code and nothing is left to
rescue it.

**What that costs, measured.** The reference separates the current match from the others by **alpha
alone** — `#27678290` against `#27678280` — which composites on the dark cell to `#214555` against
`#20414F`, **1.06:1** apart. Light is `#BBD6F1` against `#E1EBF8`, 1.25:1. Neither theme sets
`editor.findMatchBorder` or `editor.findMatchHighlightBorder`, so nothing else carries it. At arm's length
on a phone, the current match is **not** distinguishable from the rest by colour.

**Two things carry it instead, and one is a departure.** The find bar's counter and arrows are the
reference's own answer — VS Code's find widget has both. The other is the **bold weight** on the current
match, which is this app's: a weight reads at a glance where a hue alone has to be compared against its
neighbours. It was a nicety when the current match was bright yellow; with parity it is most of the
signal. Recorded as an adaptation rather than left to read as something the reference does.

**A link takes the string colour and the underline is the whole signal.** Nothing changed in the styles;
what changed is that the underline went from decoration to **load-bearing**, and
`EditorPaletteTest.aLinkTakesTheStringColourAndIsToldApartByItsUnderline` asserts both halves so neither
can be tidied away.

**The palette states its colours; it does not derive them from a Material role.** They are stated
constants, with every ratio pinned by `EditorPaletteTest`.

**One editor font, whatever the cell holds.** A markdown cell's **source** is drawn in `codeSourceStyle`,
the same style a code and a raw cell's source is drawn in. `markdownSourceStyle` is deleted rather than
redefined: one editor font means one function, and a second name returning "the same thing" is what let
the two drift. Both references agree and each was read rather than assumed — VS Code builds a markup
cell's editor as a plain `CodeEditorWidget` over a code cell's own options, with no font override.

## Consequences

The bold weight on the current match is the one visual signal here that is ours rather than the
reference's, and with parity it is doing most of the work. Removing it for parity's sake would leave a
1.06:1 difference as the only cue.

The underline on a link and the alpha on a match are both asserted, because both look like decoration and
both are the entire signal.

**The decision that survives every re-source is the heading's**: a colour a role could supply is a colour
no test can hold. This entry was first written when the source was IntelliJ's own scheme files; the source
changed to VS Code's resolved themes and the rule did not.

The thing a future reader will be tempted to "fix" is rendered markdown's code size inheriting the prose
size, which is the reference's `font-size: inherit` for `.jp-RenderedHTMLCommon pre, code` and **not** the
same question as a cell's source font.
