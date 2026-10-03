# AUD-008 — Rendering and formats

Milestone: `M0` · State: in-progress · Depends on: `AUD-007` · Closes: —

> An **audit** item, so this is not `_TEMPLATE.md` filled in. What an `AUD-NNN` ships is a
> *measurement* rather than a screen
> ([ADR-0067](../adr/0067-a-parity-ledger-stores-verdicts-and-produces-its-counts.md)), so the
> template's Outcome, Demo, Maestro flow and domain→port→adapter Surface have nothing to say here
> and are replaced by what is examined and the command that reproduces each number. The template's
> two load-bearing sections stay, because they are the whole point: a row per reference, and a gap
> analysis against our own code.
>
> **Deleted when the item closes.** Its durable residue is the ledger section, an ADR per
> `undocumented` verdict, a `GAP-NNN` per `absent` one, and the code comments — nothing else
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## What is examined

One area — **everything that turns stored bytes into drawn content** — taken through four
questions at once, never four sweeps: what the reference offers, what we built, where we differ
and *why*, and whether the code on that path is sound in growth, branching, failure handling and
coupling.

This slice meets two different *kinds* of reference: the **behaviour** spec is JupyterLab's
command surface plus the widgets those commands act on, and the **format** spec is nbformat — the
same spec twice, once as `packages/nbformat`'s TypeScript interfaces and once as the shipped
rootfs's `nbformat`/`nbconvert` packages. The area at four scales:

- **The command surface.** **23 ids.** Five `notebook:` ids the partition already carries for this
  slice — `notebook:trust`, `notebook:export-to-format`, `notebook:show-export-guidance`,
  `notebook:copy-to-clipboard`, `notebook:create-output-view` — plus seven packages' whole
  surfaces: `rendermime-extension`'s `rendermime:handle-local-link`, `markdownviewer-extension`'s
  `open`/`edit`/`trust`, `htmlviewer-extension`'s `trust-html`, `mathjax-extension`'s
  `clipboard`/`scale`, `imageviewer-extension`'s eight zoom/rotate/flip/invert/reset acts,
  `csvviewer-extension`'s `csv:`/`tsv:go-to-line`, and `mermaid-extension`'s `copy-source`.
  Verified: no other package under `packages/` declares an id in any of these families.
  Settings with no ids: `markdownviewer-extension`'s six (`fontFamily`, `fontSize`, `lineHeight`,
  `lineWidth`, `hideFrontMatter`, `renderTimeout`), `htmlviewer-extension`'s `toolbar` and
  `trustByDefault`, `csvviewer-extension`'s `toolbar` per schema, `imageviewer-extension`'s empty
  `properties`; `mathjax-extension`'s `plugin.json` is not settings at all — a context-menu
  contribution hanging both commands off a `.MathJax` node that only a rendered equation creates.
- **The renderer registry and its factories.** `packages/rendermime/` — `RenderMimeRegistry`
  (`preferredMimeType` walking `defaultRank`, `createRenderer` wiring the five plugs:
  `resolver`, `sanitizer`, `linkHandler`, `latexTypesetter`, `markdownParser`),
  `standardRendererFactories` (`renderers.ts` + `factories.ts`: html, image over
  bmp/png/jpeg/gif/webp, latex, markdown, svg, text, stderr, and the **javascript placeholder** —
  a "run to view" stub that never executes), `mimemodel`, `outputmodel` (`convertBundle` keeping
  payloads as JSON), `attachmentmodel` (the `attachment:` URI resolver), `latex.ts`, `widgets.ts`.
  The plugs' implementations live elsewhere: `apputils/src/sanitizer.ts` (`Sanitizer`'s
  `allowedTags`/`allowedAttributes` whitelists), `markedparser-extension` (the `IMarkdownParser`
  over vendored `marked`, whose custom lexer is what ToC reads), `mathjax-extension`
  (`MathJaxTypesetter` over `mathjax-full`), `attachments/` (`AttachmentsModel`, the cell
  attachment store). And the zero-id `IRenderMime.IExtension` plugins — `json`, `pdf`, `vega5`,
  `javascript`, `audio`, `video`, `mermaid/src/mime.ts`'s `RenderedMermaid`/`rendererFactory`,
  `nbconvert-css` — which mount extra factories into the same registry, so "the registry" is a
  plugin surface, not a closed list.
- **The document viewers.** `markdownviewer/` — `MarkdownViewer` rendering a `.md` through the
  registry behind `ABCWidgetFactory`, `MarkdownViewerTableOfContentsFactory`, the six-key `IConfig`
  — `htmlviewer/`'s `HTMLViewer` (a **sandboxed `IFrame`** with `allow-same-origin` and nothing
  else, plus `UNTRUSTED_LINK_STYLE`'s painted warning), `csvviewer/` (`CSVViewer`'s grid over
  `parse.ts`, the `CSVDelimiter` toolbar, `goToLine`, and `csvviewer-extension/searchprovider.ts`'s
  document-search registration — a `GAP-036` sibling), `imageviewer/` (the transform widget the
  eight commands drive), `mermaid/` (the diagram renderer doing double duty as a document widget
  and a markdown-fence processor via `mermaid/src/markdown.ts`).
- **The output area.** `outputarea/src/widget.ts` — `OutputArea` (one wrapped widget per output,
  the `jp-OutputArea-promptOverlay` hover prompt, `_outputTracker`, `isIsolated`'s per-mime
  metadata gate, `display_id`/`update_display_data` routing, `clear_output`'s `wait` flag),
  `OutputPrompt` (`Out[n]`), `Stdin` (the `input_request` prompt — `GAP-014`),
  `SimplifiedOutputArea` (the console's stripped variant), `OutputArea.execute`, and `model.ts`'s
  `OutputAreaModel`/`IOutputModel`. Under it, `nbformat/src/index.ts` is the TS half of the
  format: `INotebookContent`/`ICell`/`IOutput`/`IMimeBundle`/`IAttachments`/`MultilineString`,
  `IUnrecognizedCell`/`IUnrecognizedOutput` as the open-format escape hatches,
  `validateMimeValue`, `MAJOR_VERSION 4`/`MINOR_VERSION 4`.
- **The wire and the server.** `KernelOutputs` maps iopub's `stream`/`execute_result`/
  `display_data`/`update_display_data`/`error`/`clear_output` onto `CellOutput` (AUD-001 already
  verdicted the message types; what the *renderer* owes them is this slice's). Inside the image,
  `nbformat` 5.10.4 (`validator.py`, `reader.py`, `v4/{nbjson,nbbase,convert,rwbase}` — the
  rejoin/split asymmetry `MimeBundleLines` transcribes) and `nbconvert` 7.17.1 behind
  `jupyter_server/nbconvert/handlers.py`'s `GET api/nbconvert` and
  `GET nbconvert/<format>/<path>[?download=true&sanitize_html=…]`, with `mistune`, `jinja2`,
  `pygments`, `jupyterlab_pygments`, `bleach` and `pandocfilters` as its dependency surface (and
  pandoc itself **absent** — ADR-0051's measured consequence).

**Ours.** The parsers — `markdown/MarkdownDocument` (commonmark-java's AST → `RichBlock`, raw HTML
blocks *dropped* by `asBlock`'s fallthrough) and `html/{HtmlDocument,HtmlInlines,HtmlTable}` (jsoup
→ the same `RichBlock`, unknown tags flattened or dropped — the subset parse that stands where a
sanitizer would) — the model `richtext/{RichBlock,RichInlineString}` defines (with the
`RICH_LINK_TAG` annotation written and, so far, read only by its own test), and the renderers
`ui/editor/{RichText,RichTable}` over it. The outputs — `domain/model/CellOutput`,
`ui/editor/RenderedOutput` (three mimes: `text/html`, `image/png`, `text/plain`, ranked off
JupyterLab's `defaultRank` and falling back to "what it was"; ANSI escapes stripped to text),
`CellOutputsView` (the scrolled look), `ExecutionLabel` (the `[n]:` input prompt). The format —
`data/disk/{NotebookFile,NotebookJsonWriter,NotebookOutputReader,NotebookOutputWriter,
MimeBundleLines,DiskNotebookRepository}` around `PreservedJson` (ADR-0021). Export —
`domain/port/{NotebookExporter,DocumentSharer}` behind `data/space/{OkHttpNotebookExporter,
BaseSpaceNotebookExporter,NbConvertResponse}` and `ui/editor/{NotebookExport,OfferedExportFormat,
ExportStatus}`. Ingest — `import/ImportTitles` + `MainActivity`'s VIEW-intent `.ipynb` import. And
`ui/fileeditor/`, which is what every document viewer here falls back to: plain text.

**Carried in.** `GAP-013`'s comm-channel absence lands here as the missing
`application/vnd.jupyter.widget-view+json` renderer; `GAP-014`'s `allow_stdin: false` as the
missing `Stdin`; `GAP-017` (output text unselectable) is what `notebook:copy-to-clipboard` is
measured against; `GAP-023` (a save not atomic) is the format half's open wound. `GAP-019` was
already verdicted `absent` under `AUD-002` `T2b` (`notebook:render-all-markdown` sets cell state —
recorded, not rejudged). Five ADRs already carry this area's decisions: `ADR-0009` (kernel logging
out of output), `ADR-0021` (`PreservedJson`), `ADR-0035` (unrendered ≠ asked-for), `ADR-0039`
(**Compose, not a WebView** — the load-bearing deviation every renderer verdict rests on),
`ADR-0051` (export named by the server). No `CLOSED-*` entry touches the area.

**Neighbours, not this slice's.** `syntax/`'s highlighting was verdicted in `AUD-003` `T3`. Every
other `notebook:` id is `AUD-002`'s; the wire message types are `AUD-001`'s; the file acts that
*reach* a document (the explorer's opens, docmanager's saves) are `AUD-005`'s; the console's own
surface is `AUD-006`'s though it draws through the same `RenderedOutput` path, which this slice
owns; `apputils-extension`'s 25 ids — themes, fonts, palette, notifications, print — are the
chrome, `AUD-009`'s; what runs *inside* a Space to produce these bytes is `AUD-010`'s.

## The surface, produced

Never stored — ADR-0067's rule.

```sh
# the command surface - 18 ids across seven packages, plus five carried notebook: ids
for p in rendermime-extension markdownviewer-extension htmlviewer-extension mathjax-extension \
         imageviewer-extension csvviewer-extension mermaid-extension; do
  echo "== $p"; python3 scripts/read_reference_commands.py --jupyterlab-package "$p"
done

# verified: no other package declares these families (18 ids; index.tsx counts too)
grep -rhoE "'(rendermime|markdownviewer|htmlviewer|mathjax|imageviewer|csv|tsv|mermaid):[A-Za-z0-9-]+'" \
  reference/research_repos/jupyterlab/packages/*/src --include='*.ts' --include='*.tsx' | sort -u

# the settings each schema declares (rendermime-/mermaid-extension ship none;
# mathjax's is a .MathJax context-menu contribution, no properties at all)
for f in reference/research_repos/jupyterlab/packages/{markdownviewer-extension,htmlviewer-extension,mathjax-extension,imageviewer-extension,csvviewer-extension}/schema/*.json; do
  echo "== $f"; python3 -c "import json;print(sorted(json.load(open('$f')).get('properties',{})))"
done

# the registry and its plugs, the output area, the format
ls reference/research_repos/jupyterlab/packages/rendermime/src \
   reference/research_repos/jupyterlab/packages/outputarea/src \
   reference/research_repos/jupyterlab/packages/nbformat/src
grep -c "allowedTags\|allowedAttributes" \
  reference/research_repos/jupyterlab/packages/apputils/src/sanitizer.ts

# ours
python3 scripts/read_port_surface.py            # NotebookExporter 2, DocumentSharer 1
python3 scripts/read_port_surface.py --unused
ls app/src/main/java/com/jupydroid/app/{markdown,html,richtext,import} \
   app/src/main/java/com/jupydroid/app/data/disk

# the server, inside the image
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/nbconvert/handlers.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/nbformat/validator.py
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/nbformat/v4/rwbase.py
```

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `rendermime/src/{registry,factories,renderers,mimemodel,outputmodel,attachmentmodel,latex,widgets}.ts`, `rendermime-interfaces/src/index.ts`, `outputarea/src/{widget,model,tokens}.ts`, `nbformat/src/index.ts`, `attachments/src/model.ts`, `apputils/src/sanitizer.ts`, `markedparser-extension/src/index.ts`, `mathjax-extension/src/index.ts`, `markdownviewer/src/{widget,tokens,toc}.ts`, `htmlviewer/src/widget.tsx`, `csvviewer/src/{widget,parse,toolbar,model}.ts`, `imageviewer/src/{widget,tokens}.ts`, `mermaid/src/{mime,markdown,manager}.ts`, the `*-extension` plugins and the zero-id mimerender extensions | one `IRenderMimeRegistry`, a `IRendererFactory` per mimetype ranked by `defaultRank`; every document viewer is an `ABCWidgetFactory` over it, every output a `MimeModel` through it; trust and sanitization are DOM facts |
| `vscode` | PARTIAL — presentation | `src/vs/workbench/contrib/notebook/browser/view/renderers/{cellRenderer,backLayerWebView,webviewPreloads,webviewMessages}.ts`, `view/notebookCellList*`, media preview under `extensions/media-preview`/`media` | §8.3's split: markdown cells through a `marked`+sanitize DOM renderer, **outputs in a backlayer WebView** — a second process boundary where JupyterLab draws in place |
| `vscode-jupyter` | PARTIAL — widget renderers | `src/webviews/webview-side/ipywidgets/renderer`, `src/test/datascience/widgets/` | renderers *contributed into* VS Code's output webview — the ipywidgets mime and friends; behaviour of the widget protocol, not the document formats |
| `intellij-community` | PARTIAL — markdown and notebook layout | `platform/jewel/markdown/core/src/main/kotlin/org/jetbrains/jewel/markdown/` (`Markdown.kt`, `processing/MarkdownProcessor`, `extensions/` incl. GFM tables/alerts/images), `notebooks/visualization` | §8.3's markdown authority — a **Compose-native** renderer over commonmark-java, the one reference shaped like ours |
| `termux-app` | ABSENT | — | a terminal, no rich document |
| `winlator` | ABSENT | — | container lifecycle |
| the shipped rootfs | the formats' server half | `nbformat/{validator,reader}.py`, `v4/{nbjson,nbbase,convert,rwbase}.py`, `nbconvert/` exporters, `jupyter_server/nbconvert/handlers.py`, `mistune`/`jinja2`/`pygments`/`jupyterlab_pygments`/`bleach`/`pandocfilters` | what the device actually validates and converts — the `api/nbconvert` + `nbconvert/<format>/<path>` wire ours already mirrors |

**Count:** 4 of 7 do some of this — JupyterLab the whole registry-and-viewers stack, VS Code and
vscode-jupyter the output half by their own means, IntelliJ the markdown half in our own idiom.
Termux and Winlator have nothing to compare. The rootfs is the format's authority and the export
wire, not a surface we draw.

## Implementation inventory

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| Domain model | `nbformat/src/index.ts` (the whole `INotebookContent`/`ICell`/`IOutput`/`IMimeBundle`/`IAttachments` tree, `MultilineString`, `IUnrecognized*`, `validateMimeValue`), `rendermime/src/{mimemodel,outputmodel,attachmentmodel}.ts` |
| Port / protocol | `rendermime-interfaces/src/index.ts` — `IRenderer`, `IRendererFactory`, `IRenderMimeRegistry`, `IMimeModel`, `IOutputModel`, `ISanitizer`, `IMarkdownParser`, `ILatexTypesetter`, `IResolver`, `ILinkHandler`, `IExtension` |
| Adapter | `rendermime/src/renderers.ts` + `factories.ts` (the standard eight), `latex.ts`, `widgets.ts`; the plug implementations: `apputils/src/sanitizer.ts`, `markedparser-extension`, `mathjax-extension`, `attachments/`; the extension-mounted factories: `mermaid/src/mime.ts`, `pdf-extension`, `vega5-extension`, `javascript-extension`, `json-extension`, `audio-extension`, `video-extension` |
| UI entry point | the `*-extension` `index.ts`s — `rendermime-extension` (registry + `handle-local-link` + debugger link), `markdownviewer-extension` (factory + tracker + ToC factory), `htmlviewer-extension` (trust ask), `csvviewer-extension` (`goToLine` + `searchprovider.ts`), `imageviewer-extension` (the eight transform commands), `mermaid-extension` (`copy-source`) |
| Persisted state | the schemas above (`markdownviewer`'s six, `htmlviewer`'s two, `csvviewer`'s `toolbar` ×2); cell `attachments`; `metadata.scrolled`/hidden flags; output `metadata.isolated`; `display_id`/`transient` |
| Empty / error / loading states | `renderers.ts`'s text fallback and the mimetype-listing last resort; `RenderedError`'s traceback rendering; `htmlviewer`'s `UNTRUSTED_LINK_STYLE`; `markdownviewer`'s `renderTimeout` ActivityMonitor |
| Accessibility specifics | `markdownviewer`'s `tabIndex`, the output DOM's real text nodes (what a screen reader gets for free and a Compose `Canvas` does not) |
| Edge cases it handles | `preferredMimeType` picking by rank on *presence* not renderability; `isIsolated` per-mime metadata; `update_display_data` routed by `display_id`; `clear_output`'s `wait` flag; `attachment:` resolution through `attachmentmodel`; the javascript factory being a **placeholder**, never an executor; untrusted outputs rendered through the sanitizer |

### vscode@3f3380c668d07649b953eb8278354b2f263ea6ff

| Concern | Files |
|---|---|
| Markdown rendering | `src/vs/workbench/contrib/notebook/browser/view/renderers/cellRenderer.ts` — markup cells via `MarkdownRenderer` (marked + `domSanitize`), the in-place fold/join |
| Output rendering | `backLayerWebView.ts`, `webviewPreloads.ts`, `webviewMessages.ts`, `webviewThemeMapping.ts` — outputs live in an iframe-process webview the host only posts messages to |
| Media preview | `extensions/media-preview/` — image/audio/video editors as the presentation analog of JupyterLab's viewers |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Widget renderers | `src/webviews/webview-side/ipywidgets/renderer/` — renderer contributions registered on VS Code's notebook renderer API |

### intellij-community@406a27a8da071c59b052719fe6f1208c613e96b5

| Concern | Files |
|---|---|
| Markdown (Compose) | `platform/jewel/markdown/core/src/main/kotlin/org/jetbrains/jewel/markdown/` — `Markdown.kt` (`processor`/`blockRenderer`/`styling` triple), `processing/`, `extensions/` (GFM tables, alerts, images, autolinks) |
| Notebook layout | `notebooks/visualization/` — the cell presentation §8.3 names |

**Where references disagree, name the disagreement:** where outputs live — JupyterLab renders them
into the same DOM the document owns (selection, find, ToC all reach in), VS Code walls them into a
backlayer webview (presentation authority) and IntelliJ draws notebook output natively. A second:
the markdown *engine* — JupyterLab uses `marked`, IntelliJ uses commonmark-java like ours, VS Code
uses `marked` plus its own sanitizer; and the format boundary — JupyterLab trusts nbformat enough
to preserve unknown everything (`IUnrecognized*`, `PartialJSONObject`), which `PreservedJson`
already answers.

## Gap analysis — ours today

Read before any verdict is written. Stated plainly: **present but narrow** — the pipeline exists
end to end (source → parse → `RichBlock` → Compose; wire → `CellOutput` → `RenderedOutput` →
drawn; file → `NotebookFile` → domain → `PreservedJson` round-trip), and the narrowness is the
shape: three drawn mimes against the registry's eight standard factories plus the extensions',
and no document viewer at all.

| Concern | Ours today (file) | State |
|---|---|---|
| Markdown cell render | `markdown/MarkdownDocument` → `RichBlock` → `MarkdownCell`/`RichText` | present — commonmark-java, no extensions (no GFM tables/alerts/footnotes; raw HTML dropped) |
| HTML output | `html/HtmlDocument` → `RichBlock` | present and partial — a subset; scripts/styles iframes all flatten or drop, which is also the sanitization story |
| The model | `richtext/{RichBlock,RichInlineString}` | present — blocks/inlines enough for both parsers; `RICH_LINK_TAG` written, **never read** outside its test |
| Output kinds | `CellOutput` (stream/error/display_data/execute_result/clear_output) | present — `update_display_data` arrives *as* `DisplayData` (`KernelOutputs`), its display_id semantics unmodelled |
| Mime coverage | `RenderedOutput.RANKED_MIME_TYPES` — html, png, plain | partial — no `text/markdown`, `image/svg+xml`, `text/latex`, `image/{jpeg,gif,webp,bmp}`, `application/json`, `application/javascript`, `application/pdf`, mermaid, vega, widget-view; the fallback prints the bundle's keys |
| Error rendering | `CellOutput.Error` → ANSI-stripped `RenderedOutput.Text` | present, styled-plain — the reference renders ANSI colours |
| Output area shape | `CellOutputsView` (column/scrolled), `ExecutionLabel` (`[n]:` on input) | partial — no `Out[n]` prompt visible, no `Stdin` (GAP-014), no isolated-output gate, no `wait`-flag read on `clear_output` |
| Document viewers | none — `.md`/`.html`/`.csv`/`.png`/`.mmd` all open `FileEditorScreen` as plain text | **absent** — the whole viewer column of the table above; `markdownviewer:*`/`htmlviewer:*`/`imageviewer:*`/`csv*:`/`tsv:`/`mermaid:*` have no surface |
| Trust | none — the subset parser permits nothing to gate | machinery-n/a vs `adapted`, a T2 read |
| Local links in rendered content | `RICH_LINK_TAG` annotation only | machinery only — `rendermime:handle-local-link`'s question |
| Math rendering | none — `$…$` renders as literal text | **absent** — `mathjax:*` and `text/latex` alike |
| Attachments | `PreservedJson` round-trips `attachments` verbatim | preserved, never resolved — `attachment:` URIs have no model to answer |
| nbformat read/write | `NotebookFile`/`NotebookJsonWriter`/`NotebookOutput*`/`MimeBundleLines` | present — open-format preservation, minor-version writes; **no validation** (nbformat's `validator.py` is never called), `ClearOutput` writes `{}` (a T4 read) |
| Save | `DiskNotebookRepository.writeFile` | present, **not atomic** — `GAP-023` |
| Export | `NotebookExporter` → `OkHttpNotebookExporter`/`BaseSpaceNotebookExporter` → server `nbconvert`; `DocumentSharer`/`IntentDocumentSharer` leaves | present — ADR-0051's measured menu; the picker is `OfferedExportFormat`/`ExportStatus` |
| Import/ingest | `import/ImportTitles` + `MainActivity` VIEW intent → `.ipynb` | present — title from filename only |
| In-output find | none — `GAP-036`/`GAP-017` family | **absent** — `RenderedOutput.Text` is the only searchable output form (AUD-004's verdict stands) |

## Deviations

Known going in, so a verdict does not re-litigate them:

- **Compose, not a WebView** — `ADR-0039`. Every "the reference draws X in its DOM" row is measured
  against `RichBlock` + `RichText`, and "no DOM" is the answer to several machinery absences, not
  an `absent` verdict each.
- **The subset parse is the sanitizer** — `HtmlDocument`/`MarkdownDocument` drop what they cannot
  model rather than sanitize-then-render, so `defaultSanitizer`'s whitelists have no DOM to gate.
  Whether `trust` is `adapted` under that framing or `absent` is T2's read — stated here so it is
  read once.
- **`application/javascript` is a placeholder even in JupyterLab** — `javascript-extension`'s
  renderer is a "run to view" stub, not an executor; nothing executes either way.
- **`update_display_data` folds into `DisplayData`** — `KernelOutputs` maps both to the same
  `CellOutput`; whether `display_id` update-in-place semantics are owed is T4's question.
- **`ClearOutput` writes `{}`** — `NotebookOutputWriter` emits an empty object for the instruction;
  nbformat has no shape for it. Read back it drops to nothing — whether the stray `{}` is a defect
  or harmless preservation is T4's read.
- **Markdown engine is commonmark-java** — the reference's is `marked`; IntelliJ's
  `MarkdownProcessor` is the same commonmark lineage, which makes it the closer read for our
  pipeline. `markedparser-extension`'s lexer staying "the ToC's parser" was already verdicted in
  AUD-004.
- **Output text is not selectable** — `GAP-017` is carried in; `notebook:copy-to-clipboard` is
  measured against it rather than re-gapped.
- **No validation** — `nbformat.validator` is never invoked; the reader is tolerant by design
  (`toDomainOutput` drops rather than throws). Which side of `absent`/`adapted` that lands on is
  T4's.

## Open decisions

- **Per-mime granularity.** `text/markdown`, `image/svg+xml`, `text/latex`, the other
  `image/*`s, `application/json`, `application/pdf`, mermaid/vega, widget-view: one `absent` row
  per mimetype (each naming the factory read), or one family row? Decided at T3 on the AUD-007
  precedent — a verdict names what was read; the ledger decides the fold.
- **`handle-local-link`**: `absent` (no click reach) or `defect` (annotation machinery written,
  never consumed — AUD-006's completer/`GridSelection` question again)?
- **ANSI tracebacks**: `adapted` (stripping stated in `RenderedOutput`'s KDoc) or `absent`
  (ANSI-colour rendering never built)?
- **Whether `Out[n]`/`OutputPrompt` is owed** — the reference draws it per execute_result; ours
  draws the count on the *input* prompt only.
- **What "viewer" means without one** — `.md` opening as editable text might be `adapted` (the
  file *is* shown) rather than `absent`; the question is whether `markdownviewer:open`'s
  *rendered* preview is the capability, and the file-type factory split (`DocumentOpeners`'s
  suffix rule) is where the read lands.

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — **The instrument and the surface, produced.** `read_ledger_partition.py`'s
      `COMMAND_IN_CELL` gains `rendermime|markdownviewer|htmlviewer|mathjax|imageviewer|csv|tsv|
      mermaid` and `PARTITIONED_PACKAGES` gains the seven `*-extension` packages whole — red tests
      first in `read_ledger_partition_test.py` and `read_ledger_verdicts_test.py`, which shares the
      pattern. No stray-id sweep needed (verified: no backticked id in any of these families exists
      in the ledger or `app/src/`). Land the `AUD-008` ledger section: the surface paragraphs, the
      produced counts, the partition rows — no verdicts.
- [x] T2 — **Verdicts for the command surface.** The 18 package ids plus the five carried
      `notebook:` ids against `ui/editor/{NotebookExport,OfferedExportFormat,ExportStatus}` and
      `DocumentSharer`/`IntentDocumentSharer` (export/guidance), `CellOutputsView`/`RenderedOutput`
      (copy-to-clipboard, create-output-view), the `FileEditorScreen` fallback (markdownviewer,
      htmlviewer, imageviewer, csvviewer, mermaid document surfaces and their trust acts),
      `RichInlineString`'s `RICH_LINK_TAG` (`handle-local-link`), and the mathjax pair.
- [x] T3 — **Verdicts for the rendermime half.** `registry.ts`/`factories.ts`/`renderers.ts`/
      `mimemodel.ts`/`outputmodel.ts`/`attachmentmodel.ts`/`latex.ts`/`widgets.ts` plus the plugs
      (`sanitizer.ts`, `markedparser-extension`, `mathjax-extension`, `attachments/`) and the
      extension-mounted factories against `markdown/`+`html/`+`richtext/`+`RenderedOutput`/
      `MarkdownCell`; `platform/jewel/markdown` and VS Code's `cellRenderer`/`backLayerWebView`
      read for presentation.
- [ ] T4 — **Verdicts for the output area and the format.** `outputarea` (OutputArea/OutputPrompt/
      Stdin/SimplifiedOutputArea/collapser/isolated/display_id/clear_output) + `nbformat` (TS types
      and Python validator/reader/rwbase) + `nbconvert`/`handlers.py` against `CellOutput`/
      `CellOutputsView`/`KernelOutputs`, `data/disk/*`, `data/space/*Exporter*`,
      `import/ImportTitles` + `MainActivity`.
- [ ] T5 — **The walk.** `.ipynb` open → `readNotebook` → `PreservedJson` → autosave →
      `NotebookJsonWriter` round-trip; iopub → `KernelOutputs` → `CellOutput` → `renderRichest` →
      drawn; export → `api/nbconvert` → `nbconvert/<format>/<path>` → share sheet; `.md` tap →
      file editor; `attachment:`/`display_id`/`wait`-flag paths; growth (per-output recompose,
      bundle sizes), branching, failure, coupling.

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair
  it finds becomes a `JD-NNN` with a demo of its own.
- **A Maestro flow.** Nothing on screen changes — `AGENTS.md` rule 2's named exception; the
  reproducing command stands in for the device.
- **The `notebook:` cell-state ids** and `render-all-markdown` — `AUD-002`'s, verdicted.
- **`syntax/`** — `AUD-003`'s, verdicted.
- **The wire's message types** — `AUD-001`'s, verdicted.
- **`apputils-extension`'s chrome** — `AUD-009`'s.
- **What runs inside a Space** — `AUD-010`'s; this slice owns the format of what it produces, and
  the export wire.
