# JD-019 — A `text/markdown` output draws as markdown

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-101

## Outcome

A kernel's `display(Markdown("# Title\n**bold**"))` draws a heading and bold text, in the notebook and
the Console, where it now draws `<IPython.core.display.Markdown object>`.

## Demo

On `SM-X216B` (it runs a cell), from a cold launch:

1. New notebook; a cell runs
   `from IPython.display import Markdown, display; display(Markdown("# Heading\n**bold** and `code`\n- item")); print(6*7)`.
   The output draws an H1 "Heading", "bold" in bold, `code` in monospace, a bullet "item", then `42`.
   No literal `#`, `**` or backtick is on screen.
2. The table of contents lists "Heading" under the cell.
3. A Console on the same kernel runs the same `display(Markdown(...))` and draws it the same way.
4. Force-stop and relaunch with No Kernel: the cell's output still draws as markdown, read from the
   saved file.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/rendermime/src/factories.ts`, `renderers.ts` (`renderMarkdown`) | `markdownRendererFactory` at rank 60, between `text/html` 50 and the images 90; parse, sanitize, anchor headings, typeset |
| `vscode` | FULL | `vscode@3f3380c6 extensions/markdown-language-features/notebook/index.ts`, `src/vs/workbench/contrib/notebook/common/notebookCommon.ts` (`NOTEBOOK_DISPLAY_ORDER`) | `vscode.markdown-it-renderer` claims `text/markdown`; the display order puts it after `text/html` and before `image/png` |
| `vscode-jupyter` | FULL (delegates) | `vscode-jupyter@f01d80ca0 src/webviews/webview-side/ipywidgets/kernel/mimeTypes.ts` | Routes `text/markdown` to vscode's markdown-it renderer |
| `intellij-community` | ABSENT | — | No notebook output renderer in the community tree |
| `termux-app` | ABSENT | — | No notebook |
| `winlator` | ABSENT | — | No notebook |
| the shipped rootfs | sender | `usr/lib/python3.12/site-packages/IPython/core/display.py` (`Markdown._repr_markdown_`, `DisplayObject.__repr__`) | Sends `text/markdown` beside a `text/plain` that is the repr `<IPython.core.display.Markdown object>`, not the source |

**Count:** 3 of 3 notebook front ends draw it. Both orders agree that HTML wins over markdown,
and markdown wins over every image.

## Gap analysis

| Concern | Ours today (file) | Gap |
|---|---|---|
| Parse | `app/src/main/java/com/jupydroid/app/markdown/MarkdownDocument.kt` (`parse`) | none |
| Draw | `RenderedOutput.Document` → `RichText` in `app/src/main/java/com/jupydroid/app/ui/editor/CellOutputsView.kt`, shared by the Console | none |
| Route | `RANKED_MIME_TYPES` in `app/src/main/java/com/jupydroid/app/ui/editor/RenderedOutput.kt` has no `text/markdown` | **absent** |
| ToC | `outputHeadingsOf` in `app/src/main/java/com/jupydroid/app/ui/editor/CellHeadings.kt` reads any `Document`; its KDoc excludes markdown only because nothing renders it | follows the routing |

Ours is **present but unreachable**: the parser and the renderer exist, and only the routing is missing.

## Deviations

- No heading anchors, no `setFragment` and no link following. Those belong to `GAP-093`.
- No math typesetting. That is `GAP-096`.
- commonmark only, so GFM tables don't parse. That is `GAP-111`.

## Surface

| Layer | Files |
|---|---|
| `ui/editor` | `RenderedOutput.kt`, `CellHeadings.kt` (KDoc) |
| unit tests | `RenderedOutputTest.kt`, `TableOfContentsHeadingsTest.kt` |
| `.maestro/` flow + `scripts/` driver | `jd-019-markdown-output-*` |

## Tasks

- [ ] T1: route `text/markdown` to `MarkdownDocument.parse` at JupyterLab's rank 60, so that HTML
  wins over it, it wins over the images, and a blank payload falls through. A markdown output's
  headings join the ToC. Run the flow on `SM-X216B`: a cell's `display(Markdown(...))` draws
  formatted.
- [ ] T2: the demo above, typed: notebook, ToC, Console, relaunch.

## Out of scope

`text/latex` (no renderer here), `GAP-093`, `GAP-096` and `GAP-111`.
