# JD-025 — An image output too large to draw draws shrunk

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-168

## Outcome

A cell that displays an image too large for a canvas — `display(Image(...))` of a 6400² PNG — draws
it shrunk to the column, in the notebook and the Console, instead of crashing the app, and the
notebook that saved it opens again.

## Demo

On the arm64 tablet (`SM-X216B`) — steps 2 and 4 run a cell.

1. Cold start: seed `Large Image Output.ipynb`, whose code cell builds a 12800 × 3200 PNG with `zlib`
   and `struct`, displays it and prints `6 * 7`, and open it. (A 6400² one draws taller than the
   screen, so its bounds cannot be read whole; this one is the same 163 840 000 bytes decoded.)
2. Run the cell: the image draws across the column, a quarter as tall, above the kernel's `42`.
3. A second cell displays a 300 × 60000 PNG: it draws 300 dp wide, not 150.
4. Open a Console on the notebook's kernel and display the 12800 × 3200 image there: it draws
   across the entry.
5. Force-stop and relaunch — the notebook opens and both images draw again from the saved file.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/rendermime/src/renderers.ts` (`renderImage`), `packages/rendermime/style/base.css` (`.jp-RenderedImage img`) | One `<img>` on a `data:` URL; the browser decodes and scales it, and `max-width: 100%` caps it at the output area |
| `vscode` | FULL | `vscode@3f3380c6 extensions/notebook-renderers/src/index.ts` (`renderImage`) | One `<img>` on a blob URL in the output webview; the browser decodes and scales it |
| `vscode-jupyter` | ABSENT | — | vscode's own renderer draws image outputs |
| `intellij-community` | PARTIAL (markdown, desktop) | `intellij-community@406a27a8 platform/jewel/markdown/extensions/images/src/main/kotlin/org/jetbrains/jewel/markdown/extensions/images/Coil3ImageRendererExtensionImpl.kt` | Coil decodes at `Size.ORIGINAL`; a desktop JVM has no canvas limit to meet |
| `termux-app` | ABSENT | — | No image surface |
| `winlator` | ABSENT | — | No image surface |
| the shipped rootfs | — | `usr/lib/python3.12/site-packages/IPython/core/display.py` (`Image`) | Sends the bytes whole, with `width`/`height` in per-mime metadata when asked |

**Count:** 2 of 7 draw an image output, and both draw one of any size, because a browser does the
decode. None of them shrinks an image itself: the limit is Android's hardware canvas, and the only
precedent for meeting it is this app's own markdown path (ADR-0087).

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| UI entry point | `packages/rendermime/src/renderers.ts` (`renderImage`): `img.width` only from a numeric metadata `width` |
| Edge cases it handles | `style/base.css`: `max-width: 100%; height: auto`, so an image of any pixel size draws at most the column wide |

### ours, the markdown path

| Concern | Files |
|---|---|
| Sample size | `app/src/main/java/com/jupydroid/app/ui/editor/ImageSampleSize.kt` (`imageSampleSize`), `ImageSampleSizeTest` |
| Decode | `app/src/main/java/com/jupydroid/app/ui/editor/DecodedImage.kt` (`decodedImage`) |
| Instrumented | `app/src/androidTest/java/com/jupydroid/app/ui/editor/RichTextImageTest.kt` (`anImageTooLargeToDrawWholeDrawsShrunkToTheColumn`) |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Too large to draw | browser (Lab, vscode) → draws it; ours, markdown → decode at a power-of-two sample size under 100 MiB | A little resolution lost on an image that only fits the column anyway | The markdown path's decode, shared: one rule for every image (ADR-0087, edited to say so) |
| A shrunk image's own width | Lab → the image's natural width, whatever the decoder did | A sampled bitmap is narrower than the image, so its width cannot stand in for the image's | The header's width, carried beside the bitmap (T2) |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| A too-large image output | `ui/editor/CellOutputsView.kt` (`OutputImage`): decoded whole, throws at draw | `jupyterlab@7f0e690 packages/rendermime/src/renderers.ts` | Crash; present and partial |
| A too-large markdown image | `ui/editor/RichInlineText.kt`: sampled | same | None for a wide image |
| Own width after sampling | both use the decoded bitmap's width | same | A 300 × 60000 image draws 150 dp wide; latent in the markdown path |

## Deviations

The image is decoded below its own resolution; a browser keeps every pixel. It is drawn no wider
than the column either way (ADR-0086).

## Open decisions

None. ADR-0087 already decided the sample-size rule; this item widens what it covers.

## Surface

| Layer | Files |
|---|---|
| `domain/model`, `domain/port`, `data/*`, `di/AppContainer` | — |
| `ui/editor` | `CellOutputsView.kt`, `RichInlineText.kt`, a new file holding the shared decode |
| unit tests | `ImageSampleSizeTest` (unchanged) |
| instrumented tests | `CellOutputImageTest`, `RichTextImageTest` |
| `.maestro/` flow + `scripts/` driver | `jd-025-large-image-output-*` |

## Tasks

- [x] T1 — an image output too large to draw whole draws shrunk to the column, in the notebook and the Console
- [x] T2 — an image shrunk while decoding keeps its own width, as an output and in a markdown cell
- [ ] T3 — the demo on SM-X216B

## Out of scope

An animated GIF's frames (`GAP-163`), `unconfined` (`GAP-164`), `needs_background` (`GAP-165`), an
image's alt as its content description (`GAP-166`), and SVG (`GAP-102`).
