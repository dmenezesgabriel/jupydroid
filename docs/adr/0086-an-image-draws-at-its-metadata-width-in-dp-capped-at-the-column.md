# ADR-0086 — An image draws at its metadata width in dp, capped at the column

Status: accepted
Date: 2026-10-10

## Context

An image output used to draw at its pixel count, centred in a node as wide as the output column
(`fillMaxWidth()` with `ContentScale.Fit`, which shrinks but never enlarges). On a 1.5× screen a
96-px strip drew 64 dp wide, two thirds of the size JupyterLab draws it, and the width a kernel asks
for was never read. `IPython.display.Image(..., width=100)` sends that width in per-mime metadata, and
so does a retina figure, whose pixels are twice the width it asks for
(the shipped rootfs's `usr/lib/python3.12/site-packages/IPython/core/display.py`, `_data_and_metadata`;
`IPython/core/pylabtools.py`, `retina_figure`).

JupyterLab draws an image as one `<img>` (jupyterlab@7f0e690 `packages/rendermime/src/renderers.ts`,
`renderImage`; `widgets.ts`, `RenderedImage.render`):

- `img.width` is set from `metadata[mime].width` only when it is a number; `img.height` likewise.
- `style/base.css` `.jp-RenderedImage img { max-width: 100%; height: auto }` caps the width at the
  output area and, because author CSS outranks a presentational attribute, makes the drawn height
  follow the aspect ratio whatever `height` says.
- The `<img>` is inline at the start of its line.

VS Code's renderer draws the natural size capped and reads no metadata
(vscode@3f3380c6 `extensions/notebook-renderers/src/index.ts`, `renderImage`).

## Decision drivers

- An image is the size the person who wrote the cell sees in JupyterLab, which decides output
  behaviour here (`docs/architecture/cross-cutting.md` §8.3).
- A retina figure must not draw twice the size it asked for.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **JupyterLab's: metadata `width`, else natural, in dp, capped, height by ratio, at the start** | The size the cell's author saw; a retina figure at its asked width | Small images draw larger than before |
| Keep pixel size, centred | No change | Two thirds of JupyterLab's size at 1.5×; a retina figure twice its asked size |
| Always fill the column | Uniform | Enlarges a thumbnail into a blur; no reference does it |

## Decision

**An image draws at `metadata[mime].width`, else at its own pixel width, one image pixel to a dp,
at the column's start, capped at the column, its height by the aspect ratio**
(`OutputImage`, `app/src/main/java/com/jupydroid/app/ui/editor/CellOutputsView.kt`).

- One pixel to a dp is the mapping a WebView makes for a CSS px, so the image is the size a WebView
  would draw JupyterLab's `<img>` at.
- `width` is read from the metadata of the mime that won the ranking, and only when it is a JSON
  number, as `renderImage` checks `typeof width === 'number'`. A fraction truncates, as `img.width` is
  an `unsigned long`; a negative is read as no width (`imageWidthOf`,
  `app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt`).
- `Modifier.width` is coerced by the column's max constraint, which is the cap; `aspectRatio` sets the
  height. `height` is not read.

## Consequences

A 96-px image draws 96 dp wide; `Image(..., width=100)` draws 100 dp; an image wider than the column
shrinks to it (`CellOutputImageTest`). The Console draws through the same composable, so it follows.

What a future reader will be tempted to "fix":

- **Honouring `height`.** JupyterLab's `height: auto` ignores it; honouring it would distort an image
  JupyterLab draws undistorted.
- **Centring the image.** An `<img>` with no alignment rule sits at the line's start.

Not covered:

- `unconfined`, which lifts the cap: it needs a horizontal scroll an output area does not have.
- `needs_background`: nothing in the rootfs emits it.
