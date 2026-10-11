# ADR-0088 — An HTML block in a markdown cell renders as an HTML output

Status: accepted
Date: 2026-10-11

## Context

A markdown cell's `<img src="…" width="…">` is a common way to size a picture, and until now it drew
nothing. `MarkdownDocument` dropped every commonmark `HtmlBlock` and `HtmlInline`, and
`HtmlDocument` had no `img` branch, so an `<img>` disappeared in an HTML output too.

What the references do:

- **JupyterLab** renders all raw HTML in markdown. marked keeps it as `html` tokens, and the
  sanitizer runs over the whole result. Its table of contents keeps an `html` token holding a heading
  tag and reads the level off the rendered `<h1>`-`<h6>`
  (jupyterlab@7f0e690 `packages/markedparser-extension/src/index.ts:281-309`,
  `packages/toc/src/utils/markdown.ts`).
- **VS Code's notebook markdown** renders it too: `new MarkdownIt({ html: true })` with DOMPurify
  over the result (vscode@3f3380c6 `extensions/markdown-language-features/notebook/index.ts:133`).
- **Jewel** converts it when `parseEmbeddedHtml` is on. An `HtmlBlock` becomes blocks. Inline, the
  void tags (`<br>`, `<img>`) convert on their own, and a paired tag is matched across the inline
  nodes between its halves (intellij-community@406a27a8
  `platform/jewel/markdown/core/.../processing/html/MarkdownHtmlInlinesConverter.kt`). An `<img>`
  dimension is leading digits with no unit or `px`, and any other unit is no dimension
  (`.../markdown/DimensionSize.kt`, `parseDimensionSize`).
- **JupyterLab's CSS** gives every `<img>` `max-width: 100%` and `height: auto`. The `width`
  attribute is honoured up to the column, and the `height` attribute is overridden, so the
  picture keeps its own ratio (`packages/rendermime/style/base.css`).

In this app, `HtmlDocument` already parses a `text/html` output into the model a markdown cell
draws. Its subset parse is the sanitizer: nothing it produces can execute (ADR-0039).

## Decision drivers

- Draw what both notebook references draw. Do not invent a filter neither of them has.
- Reuse the HTML front end outputs already trust instead of adding a second one.
- Draw one image node for `![]()` and `<img>`, through one resolver chain.

## Options considered

1. **Keep only the `<img>`s from raw HTML** and drop the rest. No reference does this, and it
   would need a filter of its own.
2. **Parse an HTML block through `HtmlDocument`**, and turn a void inline `<img>` into an image.
   This matches JupyterLab and VS Code for blocks and Jewel for inline void tags.
3. **Option 2 plus matching paired inline tags**, as Jewel does. That is a separate capability with
   its own pairing logic, and `<img>` does not need it.

## Decision

Option 2.

- `MarkdownDocument` parses an `HtmlBlock`'s literal with `HtmlDocument.parse`.
- An `HtmlInline` that is one `<img>` becomes `RichInline.Image` (`HtmlDocument.inlineImage`). Any
  other inline tag still drops, and the markdown text between a paired tag's halves stays.
- `RichInline.Image.width` carries `<img width>` in CSS pixels, using Jewel's rule. Unit-less and `px`
  values count; any other unit is null. There is no height, following JupyterLab's `height: auto`.
- The drawn width is that width, else the picture's own, with one pixel to one dp, capped at the
  column (ADR-0086).

## Consequences

An `<img>` draws in a markdown cell and in an HTML output. A centred `<p align="center"><img></p>`
draws its image. A `<div>` or `<table>` in a markdown cell draws as it would in an output, through
the same subset.

An HTML heading block now counts as the cell's heading, as it does in JupyterLab's table of
contents. That retires ADR-0033's statement that an HTML `<h1>` is dropped.

What a future reader will be tempted to "fix":

- **Honouring `height`.** JupyterLab's CSS overrides it. A picture stretched to two written sizes is
  not what any notebook reference shows.
- **Reading `width="50%"` as a fraction of the column.** A browser would. Jewel does not, and the
  image falls back to its own width capped at the column, which is the safe reading.

Not covered:

- Paired inline tags in markdown (`a <b>bold</b> word` draws as plain text) — `GAP-169`.
