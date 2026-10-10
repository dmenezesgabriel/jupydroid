# JD-020 — GitHub-flavoured markdown parses

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-111

## Outcome

A markdown table, `~~struck~~` text and a bare URL draw as a grid, struck text and a link, in a markdown
cell and in a `text/markdown` output, where each now draws as its own source characters.

## Demo

On `SM-X216B` (it runs a cell), from a cold launch:

1. New notebook; a markdown cell holds

   ```
   | Name | Mid | Count |
   |:-----|:---:|------:|
   | left | centre | 7 |

   ~~gone~~ and https://jupyter.org
   ```

   Rendered, it draws a grid: a bold header row with a rule under it, "left" at its column's start,
   "centre" centred, "7" at its column's end. "gone" is struck through and the URL is drawn as a link.
   No literal `|`, `:--` or `~~` is on screen.
2. A code cell runs `from IPython.display import Markdown, display;
   display(Markdown("| a | b |\n|:--|--:|\n| x | 1 |")); print(6*7)`. The output draws the grid, then `42`.
3. A Console on the same kernel runs the same `display(Markdown(...))` and draws it the same way.
4. Force-stop and relaunch with No Kernel: the markdown cell and the output still draw the grid, read
   from the saved file.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/markedparser-extension/src/index.ts`, `packages/rendermime/style/base.css` | marked with `gfm: true` — tables, strikethrough, bare-URL autolinks, task-list items — plus `marked-gfm-heading-id` and `marked-mangle`. No alerts |
| `vscode` | FULL | `vscode@3f3380c6 extensions/markdown-language-features/src/markdownEngine.ts` | markdown-it, whose defaults parse tables and strikethrough; `linkify` is a setting it passes through |
| `vscode-jupyter` | FULL (delegates) | `vscode-jupyter@f01d80ca0 src/webviews/webview-side/ipywidgets/kernel/mimeTypes.ts` | Routes `text/markdown` to vscode's renderer |
| `intellij-community` | FULL | `intellij-community@406a27a8 platform/jewel/markdown/extensions/` (`gfm-tables`, `gfm-strikethrough`, `autolink`, `gfm-alerts`) | One processor and one renderer extension per feature, over commonmark-java's own `commonmark-ext-*` |
| `termux-app` | ABSENT | — | No markdown |
| `winlator` | ABSENT | — | No markdown |
| the shipped rootfs | sender | `usr/lib/python3.12/site-packages/IPython/core/display.py` (`Markdown._repr_markdown_`) | Sends whatever source it was given; parsing is the front end's |

**Count:** 4 of 4 markdown front ends parse tables, strikethrough and bare URLs. Alerts are Jewel's
alone: JupyterLab, which is authoritative for behaviour, does not draw them.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Parser options | `packages/markedparser-extension/src/index.ts` (`gfm: true`, `loadMarkedPlugins`) |
| Table presentation | `packages/rendermime/style/base.css`, the `/* Tables */` block |

`base.css` right-aligns `td`, `th` and `tr` only under `:not(.jp-RenderedMarkdown)`. A markdown table
is therefore **not** right-aligned: each cell takes the `align` marked writes from its column's
delimiter (`:--`, `:-:`, `--:`). With none, no rule in `base.css` or on an ancestor sets `text-align`,
so the browser's own default applies: a `<td>` at the start, a `<th>` centred. That last step is read
from the stylesheet and the HTML rendering default, not measured in a running JupyterLab.

### intellij-community@406a27a8

| Concern | Files |
|---|---|
| Parse | `platform/jewel/markdown/extensions/gfm-tables/src/main/kotlin/org/jetbrains/jewel/markdown/extensions/github/tables/GitHubTableProcessorExtension.kt` (`getAlignment`) |
| Model | `.../tables/TableBlock.kt`, `TableHeader.kt`, `TableRow.kt`, `TableCell.kt` |
| Draw | `.../tables/GitHubTableBlockRenderer.kt` (`cell.alignment ?: defaultAlignment`), `GitHubTableStyling.kt` |
| Strikethrough | `platform/jewel/markdown/extensions/gfm-strikethrough/.../GitHubStrikethroughProcessorExtension.kt` |
| Autolink | `platform/jewel/markdown/extensions/autolink/.../AutolinkProcessorExtension.kt` (`AutolinkExtension.create()`) |

Jewel keeps a nullable alignment on the cell and lets the renderer's styling supply the default, one
for header cells and one for body cells. JupyterLab's default differs by **where the table came
from** (markdown or HTML), which a renderer here cannot know, so the cell carries the answer.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Parser | Jewel → `commonmark-ext-*`; JupyterLab → marked | We already walk commonmark-java's AST | `commonmark-ext-gfm-tables`, `-gfm-strikethrough`, `-autolink` at the pinned `commonmark` version |
| Where alignment lives | Jewel → nullable on the cell, default in the styling; JupyterLab → CSS by source | `RichTable` draws HTML and markdown tables alike and cannot tell them apart | On `TableCell`, never null: each front end states its rule, HTML `End`, markdown its column's |
| Alerts | Jewel → `gfm-alerts`; JupyterLab → none | A block model and a renderer for something the behaviour reference does not draw | Not built |

## Gap analysis

| Concern | Ours today (file) | Gap |
|---|---|---|
| Parse | `app/src/main/java/com/jupydroid/app/markdown/MarkdownDocument.kt` — `Parser.builder().build()` | **no extension** |
| Table model | `RichBlock.Table`, `TableCell` in `app/src/main/java/com/jupydroid/app/richtext/RichBlock.kt`, filled only by `app/src/main/java/com/jupydroid/app/html/HtmlTable.kt` | no alignment |
| Table drawing | `app/src/main/java/com/jupydroid/app/ui/editor/RichTable.kt` — `TextAlign.End` for every cell | right for HTML, wrong for markdown |
| Struck text | `RichInline` has no such node; `RichInlineString.kt` no such style | **absent** |
| Bare URL | `RichInline.Link` exists and draws | only the detection is missing |
| Reach | `MarkdownDocument.parse` is called by `MarkdownCell.kt`, `RenderedOutput.kt` and `HoverPopup.kt` | none: all three follow the parser |

Ours is **present and partial**: the grid and its renderer exist, and markdown never reaches them.

## Deviations

- No task-list checkboxes (`- [x]`), which marked's `gfm` does draw. A list item here is a list of
  blocks with nowhere to hold a checked state. Recorded as a gap when this item ships.
- No alerts (`> [!NOTE]`): not in JupyterLab.
- A link is drawn and not followed. That is `GAP-093`.
- `<del>` and `<s>` in an HTML output stay unmarked; this item is the markdown parser's.

## Surface

| Layer | Files |
|---|---|
| build | `gradle/libs.versions.toml`, `app/build.gradle.kts` |
| `richtext` | `RichBlock.kt`, `RichInlineString.kt` |
| `markdown` | `MarkdownDocument.kt` (+ `MarkdownTable.kt` if the walk outgrows it) |
| `html` | `HtmlTable.kt` |
| `ui/editor` | `RichTable.kt` |
| unit tests | `MarkdownDocumentTest.kt`, `HtmlDocumentTest.kt`, `RenderedOutputTest.kt` |
| instrumented tests | `RichTableTest.kt` |
| `.maestro/` flow + `scripts/` driver | `jd-020-gfm-markdown-*` |

## Tasks

- [x] T1 — a GFM table parses into `RichBlock.Table` and each cell draws at its column's alignment;
  an HTML table stays right-aligned. Run the flow on `SM-X216B`: a markdown cell and a
  `display(Markdown(...))` output each draw the grid.
- [x] T2 — `~~text~~` draws struck through.
- [x] T3 — a bare URL draws as a link.
- [x] T4 — the demo above, typed: markdown cell, output, Console, relaunch.

## Out of scope

Task-list checkboxes, alerts, `GAP-093` (following a link), `GAP-096` (math), `GAP-107` (images).
