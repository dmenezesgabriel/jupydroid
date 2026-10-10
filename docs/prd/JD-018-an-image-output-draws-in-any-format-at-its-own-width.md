# JD-018 — An image output draws in any format, at its own width

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-103

## Outcome

A cell or Console entry that displays a JPEG, GIF, WebP or BMP draws the image rather than its
`text/plain` repr, and every image draws at the width the kernel asked for — or its own, shrunk to
fit — rather than at its pixel count, which on a 1.5× screen is two thirds of the size JupyterLab
draws it.

## Demo

On the arm64 tablet `SM-X216B` (it runs cells):

1. Cold start: install, launch, enter the `base` Space, open a new notebook.
2. Cell 1 displays one small image in each of `image/jpeg`, `image/gif`, `image/webp` and
   `image/bmp` (`display({mime: b64}, raw=True)`). All four draw; none shows
   `<IPython.core.display.Image object>` or a mime-keys line.
3. Cell 2: `Image(png_bytes, width=100)` draws 100 dp wide, not the column's width.
4. Cell 3: a 96-px-wide PNG with no width metadata draws 96 dp wide at the column's start — 144 px on
   this 1.5× screen, where it drew 96 px centred. (The base rootfs ships no matplotlib, so the plot is
   a literal.)
5. A Console on the same kernel draws cell 3's image the same width.
6. Kill and relaunch — cells 2 and 3 draw at the same widths, read back from the saved metadata.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/rendermime/src/factories.ts` (`imageRendererFactory`), `renderers.ts` (`renderImage`), `widgets.ts` (`RenderedImage.render`), `style/base.css` (`.jp-RenderedImage img`) | Five mimes at rank 90 into one `<img>`, sized by per-mime metadata `width`/`height`, capped by `max-width: 100%; height: auto` |
| `vscode` | PARTIAL | `vscode@3f3380c6 extensions/notebook-renderers/src/index.ts` (`renderImage`; arms `image/gif`, `image/png`, `image/jpeg`, and a typo'd `image/git`) | A blob URL into an `<img>`; no bmp, no webp, no sizing metadata |
| `vscode-jupyter` | PARTIAL | `vscode-jupyter@f01d80ca0 src/kernels/execution/helpers.ts` (`orderOfMimeTypes`, base64 → bytes for `image/*`) | Converts and orders the bundle — gif, then png, then jpeg — and hands drawing to `vscode`'s renderer |
| `intellij-community` | ABSENT | — (no notebook output renderer in the clone; `image/png` appears only in webview asset tables and HTML test data) | — |
| `termux-app` | ABSENT | — | Not a notebook |
| `winlator` | ABSENT | — | Not a notebook |
| the shipped rootfs | Producer | `usr/lib/python3.12/site-packages/IPython/core/display.py` (`Image`, `_data_and_metadata`, `display_jpeg`, `display_webp`), `usr/lib/python3.12/site-packages/IPython/core/pylabtools.py` (`retina_figure`) | `Image` sends png/jpeg/gif/webp with `width`, `height`, `unconfined`, `alt` in per-mime metadata; a retina figure sends `width: w//2` |

**Count:** 3 of the 6 front-end references draw more than PNG, and JupyterLab — which decides
behaviour here (`../architecture/cross-cutting.md` §8.3) — draws all five and is the only one that
reads the size metadata. A retina figure needs that read: its pixels are twice the width it asks for.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Ranking | `packages/rendermime/src/factories.ts` — `imageRendererFactory.mimeTypes` bmp, png, jpeg, gif, webp at `defaultRank: 90`; `registry.ts` `sortedTypes` breaks a rank tie by registration id, which follows that array |
| UI entry point | `packages/rendermime/src/widgets.ts` `RenderedImage.render` — reads `model.metadata[mimeType]` for `width`, `height`, `unconfined` and the output-level `needs_background` |
| Drawing | `packages/rendermime/src/renderers.ts` `renderImage` — `data:<mime>;base64,` `<img>`, `width`/`height` set as attributes when numeric |
| Edge cases it handles | `style/base.css` `.jp-RenderedImage img { max-width: 100%; height: auto }`: author CSS outranks the presentational `height` attribute, so the drawn height always follows the aspect ratio and `height` metadata has no visible effect; `jp-mod-unconfined` lifts the cap |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Drawing | `extensions/notebook-renderers/src/index.ts` `renderImage` — `Blob` → object URL → `<img>`, `alt` from `getAltText` |

### vscode-jupyter@f01d80ca0

| Concern | Files |
|---|---|
| Ranking | `src/kernels/execution/helpers.ts` `orderOfMimeTypes` — `image/gif` above `text/latex`, `text/markdown`, `image/png`, `image/svg+xml`, `image/jpeg` |

**Disagreement:** the order between image mimes. JupyterLab puts bmp before png before jpeg before gif
before webp, all below html; vscode-jupyter puts gif first among images. JupyterLab's is taken (§8.3).
A bundle carrying two raster forms of one image is rare — `select_figure_formats` with both
`png` and `jpeg` is the producer that does it.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Which mimes | JupyterLab → five; vscode → three | `BitmapFactory` decodes all five on API 32 | **The five**, in JupyterLab's order |
| A GIF | JupyterLab, vscode → an `<img>`, which animates | `BitmapFactory` decodes one frame; animating needs `ImageDecoder` + `AnimatedImageDrawable` and a drawable painter | **The first frame**, stated at the drawing; animation recorded as a gap when this ships |
| Drawn width | JupyterLab → metadata `width`, else natural, capped at the column; vscode → natural, capped | Ours draws the image's pixel count, centred, capped at the column: 1 px is 1/1.5 dp on the tablet | **JupyterLab's**, 1 image px = 1 dp, the mapping a WebView makes for a CSS px, at the column's start, where an `<img>` with no alignment rule sits (T2, ADR) |
| Drawn height | JupyterLab → follows the aspect ratio, whatever `height` says | Honouring `height` would distort where JupyterLab does not | **Aspect ratio**, `height` unread |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Metadata carrier | `app/src/main/java/com/jupydroid/app/domain/model/CellOutput.kt` (`DisplayData.metadata`, `ExecuteResult.metadata`) | `jupyterlab@7f0e690 packages/rendermime/src/outputmodel.ts` | complete |
| Ranking | `app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt` (`RANKED_MIME_TYPES`: html, png, plain) | `factories.ts` | partial: one image mime of five |
| Decode | `RenderedOutput.kt` (`decodedPng`, strict base64), `CellOutputsView.kt` (`OutputImage`, `BitmapFactory`) | `renderers.ts` | present: the decoder already takes every format |
| Sizing | `CellOutputsView.kt` `OutputImage` — `fillMaxWidth()` + `ContentScale.Fit`, which never upscales: a full-width node with the image centred at its **pixel** size, shrunk to fit | `base.css`, `renderers.ts` | complete (T2, ADR-0086): metadata `width` as a number, else the pixel width, in dp, at the start, capped, height by ratio. Before, measured in T1: a 96×8 px strip drew as a 1176×8 px node on SM-X216B (240 dpi), the image 64 dp wide |
| Console | `app/src/main/java/com/jupydroid/app/ui/console/ConsoleTranscriptView.kt` draws through `CellOutputs` | — | none of its own: the notebook's change reaches it |

## Deviations

- A GIF draws its first frame (above).
- `unconfined` is not read: lifting the cap needs a horizontal scroll an output area does not have.
- `needs_background` is not read: nothing in the rootfs emits it.

## Open decisions

None. The sizing rule was chosen with the user (JupyterLab's), and the image order is settled by §8.3.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/space` | — |
| `ui/editor` | `RenderedOutput.kt` (ranking, decode, width), `CellOutputsView.kt` (`OutputImage`) |
| unit tests | `RenderedOutputTest`, `CollapsedPreviewTest` |
| instrumented tests | `CellOutputImageTest` (drawn width, T2) |
| `.maestro/` flow + `scripts/` driver | `jd-018-image-formats-*` |

## Tasks

- [x] T1 — The four other image mimes draw, in JupyterLab's order; a flow on `SM-X216B`
- [x] T2 — An image draws at its metadata width, else its own, capped at the column, height by aspect; ADR; a flow on `SM-X216B`
- [x] T3 — The demo on `SM-X216B`; ship

## Out of scope

- GIF animation, `unconfined`, `needs_background`, `alt` as the content description: a gap each
  when this ships.
- `image/svg+xml`: `GAP-102`, with its trust question.
