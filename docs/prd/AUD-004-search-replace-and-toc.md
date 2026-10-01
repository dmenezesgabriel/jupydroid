# AUD-004 — Search, replace and the table of contents

Milestone: `M0` · State: in-progress · Depends on: `AUD-003` · Closes: `GAP-001`, `GAP-002`

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

One area — **search, replace and the table of contents** — taken through four questions at once,
never four sweeps: what the reference offers, what we built, where we differ and *why*, and
whether the code on that path is sound in growth, branching, failure handling and coupling.

The area is finding text and navigating structure, at the two scales this app has: **inside the
open document** (the find bar, the outline) and **across the workspace** (the search panel and
its replace-all). The partition hands it two packages' command ids whole —
`documentsearch-extension`'s six and `toc-extension`'s five — plus two carried out of
neighbours' packages: `fileeditor:find` and `editmenu:find` (whose Edit-menu sibling
`editmenu:find-and-replace` exists only in `plugin.json`'s schema, never declared). The `searchWithCM` `editorConfig` key (`packages/codemirror/src/extension.ts:879`) is
this slice's too — `AUD-003` carried it here.

But the larger half of the surface has no command ids at all. Enumerating ids alone would read
the find bar's commands and miss what a search *is*:

- **Two provider registries.** `ISearchProviderRegistry` takes a provider per widget factory —
  `jp-notebookSearchProvider` (`notebook-extension/src/index.ts:1166`),
  `jp-fileeditorSearchProvider` (`fileeditor-extension/src/index.ts:288`), `csv` and `tsv`
  (csvviewer-extension:183,350), `terminal` (terminal-extension:411). `ITableOfContentsRegistry`
  takes a factory per document kind — the notebook (notebook-extension:1190), LaTeX, Markdown and
  Python file editors (fileeditor-extension:585-587), markdownviewer (:233). What a surface can be
  searched or outlined is decided by registration, not by the widget.
- **The notebook provider's filters** (`packages/notebook/src/searchprovider.ts:229-256`): the
  `output` filter off by default and the `selection` filter, plus the per-cell split the
  reference makes — one `CellSearchProvider` per cell editor and a `GenericSearchProvider` per
  output widget (`packages/cells/src/searchprovider.ts:63-100`).
- **The workspace half exists only in VS Code.** `workbench.view.search` streams
  `ISearchProgressItem`s through `ISearchService.textSearch`, resolves to `ISearchComplete`
  (`services/search/common/search.ts:595-607`), searches a closed notebook through its cell
  models plus `webviewResults`, decides multiline from the pattern (`isMultilineRegexSource` →
  `IPatternInfo.isMultiline`), and replaces through `IBulkEditService`'s resolved editor models
  (`replaceService.ts:115-136`, `bulkTextEdits.ts:213-227`). JupyterLab registers no workspace
  search — `SearchSidePanel.kt` already says so.
- **The ToC's settings** (`toc-extension/schema/registry.json`): `numberHeaders`, `numberingH1`,
  `maximalDepth`, `includeOutput`, `syncCollapseState` — what the panel numbers, filters and
  folds, three of them surfaced by the `toc:display-*` commands.

**Carried neighbours.** The document's own heading collapse (`jp-MarkdownHeadingCollapsed`,
`CellHeadings.withHeadingCollapsed`) is a cell fold `AUD-002` verdicted; so are the two commands
the reference mounts on the ToC's own toolbar — `notebook:select-last-modified-cell` and
`notebook:select-next-modified-cell` (`GAP-022`, `packages/notebook/src/toc.ts:846-868`). Both are
restated where the toolbar they ride on is examined, not rejudged. `fileeditor:replace-selection`
reads like this slice's and is not — it is the completer's write-over-selection path
(`fileeditor-extension/src/commands.ts:733`), judged with the file editor's own surface. The
remaining `fileeditor-extension` ids are `AUD-005`'s and `AUD-008`'s.

## The surface, produced

Never stored — ADR-0067's rule. The producers already exist for the command ids; the
registries are greps.

```sh
python3 scripts/read_reference_commands.py --jupyterlab-package documentsearch-extension   # 6 ids
python3 scripts/read_reference_commands.py --jupyterlab-package toc-extension            # 5 ids
python3 scripts/read_reference_commands.py --jupyterlab-package fileeditor-extension     # 31; fileeditor:find
python3 scripts/read_reference_commands.py --jupyterlab-package mainmenu-extension       # 41; editmenu:find

# the registries - which widgets a search or an outline reaches
grep -rn "searchRegistry.add" reference/research_repos/jupyterlab/packages --include="*.ts"
grep -rn "tocRegistry.add"   reference/research_repos/jupyterlab/packages --include="*.ts"

# vscode's half is workbench code rather than a manifest, so a directory is the enumeration
ls reference/research_repos/vscode/src/vs/workbench/contrib/search/browser/notebookSearch/
ls reference/research_repos/vscode/src/vs/workbench/contrib/outline/browser/
ls reference/research_repos/vscode/src/vs/editor/contrib/find/browser/

# ours
python3 scripts/read_port_surface.py            # WorkspaceSearcher: one operation
python3 scripts/read_port_surface.py --unused
```

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL in-document, **no workspace search at all** | `packages/documentsearch/src/{searchmodel,searchview,searchproviderregistry,tokens}.ts`, `packages/documentsearch/src/providers/`, `packages/notebook/src/searchprovider.ts`, `packages/cells/src/searchprovider.ts`, `packages/toc/src/{panel,registry,toctree,utils/}.ts(x)`, `packages/notebook/src/toc.ts` | A find overlay per widget whose provider the registry picks; a notebook is one provider per cell editor plus one per output, aggregated; the ToC is a registry-fed side panel whose headings each document kind computes |
| `vscode` | FULL — the workspace half is only here | `src/vs/workbench/contrib/search/browser/` (`searchView`, `searchWidget`, `notebookSearch/`), `services/search/common/` (`search.ts`, `queryBuilder.ts`, `replaceService.ts`), `editor/contrib/find/browser/`, `workbench/contrib/outline/browser/` | `textSearch` streams file matches; notebooks searched through cell models plus `webviewResults`; multiline decided off the pattern; replace edits resolved editor models then saves; Outline is `DocumentSymbol`-driven |
| `vscode-jupyter` | ABSENT | — | All 83 contributed commands grepped — nothing search- or outline-shaped; both are core workbench |
| `intellij-community` | PARTIAL | `platform/lang-impl/src/com/intellij/find/` (`EditorSearchSession`, `SearchReplaceComponent`), `FindInProject`, the `*StructureView*` tree | §8.3's editor reference has both find surfaces and structure views; a Jupyter-notebook outline lives outside the community tree (`jupyter/src` carries no toc/outline file) |
| `termux-app` | ABSENT | — | A terminal emulator has no document search |
| `winlator` | ABSENT | — | Container lifecycle |
| the shipped rootfs | ABSENT | — | `jupyter_server` answers no search endpoint; searching is client-side over `Contents` reads |

**Count:** 2 of 7 implement the area whole, and a third implements the editor half. The split is
the deliberate §8.3 one: JupyterLab for behaviour inside a document, VS Code for the workspace
surface and the presentation.

## Gap analysis — ours today

Read before any verdict is written. Stated plainly: **present and partial** — both scales exist
and are instrumented, with `GAP-001` and `GAP-002` recorded as the known holes.

| Concern | Ours today (file) | State |
|---|---|---|
| In-notebook find model | `ui/editor/NotebookSearch.kt` — `SearchState`, `withSearch*` | present — query, options, `output`/`selection` filters, replace row, recompute on edit |
| In-notebook hits and reveal | `ui/editor/SearchHit.kt` (`searchHits`, `outputAt`), `CellReveal.kt`, `LandingOnAMatch.kt`, `MatchLanding.kt` | present — source and rendered-output hits; landing un-hides the section and the source |
| Find bar UI | `ui/editor/FindTopBar.kt`, `EditorTopBars.kt`, `CellSearchHighlight.kt`, `EditorTopBar`'s Find | present — count/current, next/previous, per-cell washes, Back ends |
| In-notebook replace | `ui/editor/NotebookReplace.kt`, `domain/model/SearchReplace.kt` | present — current and all, `preserveCase`, the two-step substitute |
| Find in the file editor | — | **absent, measured**: `FileEditorScreen` takes only a landing `selection` and replace write-backs; `fileeditor:find` has no surface |
| Workspace search | `domain/port/WorkspaceSearcher.kt` → `data/disk/DiskWorkspaceSearcher.kt`, `domain/model/{SearchQuery,SearchScope,SearchProgress,MatchedFile}.kt`, `glob/{SearchGlobs,PathGlob}.kt` | present — streamed `Found`/`Complete`+`limitHit`, the glob language transcribed; `GAP-001` (outputs) and `GAP-002` (line-bounded) recorded |
| Workspace replace | `ui/shell/WorkspaceReplace*.kt`, `ReplaceAll*.kt`, `domain/model/TextEdit.kt`, `OpenDocuments.kt` | present — through the open-documents model, all-or-none on staleness, one undo (ADR-0061) |
| Search panel | `ui/shell/{SearchSidePanel,WorkspaceSearchViewModel,SearchResults,MatchPreview,SearchOptionToggles,SearchScopeBoxes,SearchReplace*}.kt` | present — VS Code's viewlet adapted to a rail pane (ADR-0060) |
| ToC model and reach | `ui/editor/CellHeadings.kt` (`tableOfContentsHeadings`, `TocHeading`), `DocumentOutline.kt` | present — markdown and output headings, notebook only; a jump reveals the collapsed ancestor first |
| ToC surfaces | `ui/shell/TableOfContentsSidePanel.kt`, `ui/editor/TableOfContentsPanel.kt`, `TocFolds/TocNumbering/TocRow`, `EditorSettings.toc*` | present — the side panel at two panes, an overlay at one; folds are the list's own (`syncCollapseState` declined) |
| ToC for other documents | — | **absent** — `FileEditorScreen` publishes no outline where the reference registers LaTeX/Markdown/Python factories |

## Deviations

Known going in, so a verdict does not re-litigate them:

- **Two reference shapes for one area, taken whole.** The in-document bar is JupyterLab's
  `documentsearch` model; the workspace panel is VS Code's viewlet — JupyterLab registers none.
  The adaptations are already written down (ADR-0043, ADR-0060, ADR-0061).
- **A result row is a line** — `matchedLinesIn` bounds matching on purpose; `GAP-002` names the
  presentation change closing it would need.
- **Workspace hits never reach an output** — `GAP-001`'s three hold-shuts are stated in
  `MatchedFile.kt`, the load-bearing one being that the rule for which outputs have searchable
  text lives in `ui/editor/`, which `data/` may not import.
- **No find bar in the file editor** — measured, not assumed; the verdict decides what id it is.

## Open decisions

None known going in. The slice's job is deciding `GAP-001` and `GAP-002`'s ledger shape and
whatever the walk surfaces; an `undocumented` difference earns its ADR here.

## Tasks

- [x] T1 — **The instrument and the surface, produced.** `COMMAND_IN_CELL` in
      `read_ledger_partition.py` is `notebook:`-only and misses the camelCase
      `documentsearch:`/`toc:`/`fileeditor:`/`editmenu:` ids entirely; widen it to a closed
      prefix list covering exactly the families the partition has reached — an open pattern would
      sweep `inspector:open`-shaped verdict cells in as owned-nowhere verdicts, red tests first in
      `read_ledger_partition_test.py` and `read_ledger_verdicts_test.py` (the verdicts reader
      shares the pattern). Extend `PartitionedSurfaceTest`'s declared set:
      `documentsearch-extension` and `toc-extension` whole, plus the two carried ids — **not**
      the whole `fileeditor-extension`/`mainmenu-extension` packages, whose other ~70 ids belong
      to unexamined slices. Land the `AUD-004` ledger section: the surface paragraphs, the
      produced counts, the partition rows — no verdicts.
- [x] T2 — **Verdicts for the in-document half.** The six `documentsearch:*` ids against
      `NotebookSearch`/`SearchHit`/`FindTopBar`/`NotebookReplace`/`CellReveal`/
      `CellSearchHighlight`; `fileeditor:find` and `editmenu:find` (and the schema-only
      `editmenu:find-and-replace`);
      `searchWithCM`; the registry shape (five providers over widget factories vs one bar over
      cells plus rendered outputs); the `output`/`selection` filters against `SearchState`'s two.
- [x] T3 — **Verdicts for the workspace half.** VS Code's `textSearch`/`searchView`/
      `notebookSearch` against `WorkspaceSearcher` → `DiskWorkspaceSearcher` → `glob/` →
      `SearchSidePanel` — `webviewResults` is `GAP-001`, `isMultilineRegexSource` is `GAP-002`,
      both decided here; the replace stack (`WorkspaceReplace*` → `OpenDocuments` → write-back →
      save; the all-or-none staleness refusal; the undo); `search.maxResults`/`limitHit`,
      `files.exclude`/`search.exclude`, `preserveCase`. `read_port_surface.py` on the port's one
      operation.
- [x] T4 — **Verdicts for the table of contents.** The five `toc:*` ids and the five schema
      settings (`numberHeaders`, `numberingH1`, `maximalDepth`, `includeOutput`,
      `syncCollapseState`) against `TocNumbering`/`TocFolds`/`CellHeadings`/`EditorSettings.toc*`/
      `TableOfContents*`; the registry's file-editor factories and the markdownviewer's vs our
      notebook-only outline; VS Code's `DocumentSymbol`-driven Outline; the toolbar's two
      already-verdicted `notebook:select-*` ids restated as `GAP-022`'s.
- [ ] T5 — **The walk.** Find bar: `FindTopBar` → `withSearchQuery` → `searchHits` →
      `CellSearchHighlight`/`withMatchRevealed`, and the model-vs-field window
      `CellSearchHighlight` clamps. Workspace: keystroke → debounce → `search` stream → a row's
      tap → `hitFor`'s re-resolve → `LandingOnAMatch`. Replace: the counted question → model
      edits → save, the staleness refusal, the undo. ToC: `tableOfContentsHeadings` per cells
      change → `outline.publish` → `jumpTo` → `withCellRevealed`. Growth (a re-walk per query, a
      re-match per keystroke), branching, failure (stale results, the parse error, a refused
      replace), coupling (`data/` may not import the render rule — `GAP-001`'s blocker; cell
      index vs cell id).

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair
  it finds becomes a `JD-NNN` with a demo of its own.
- **A Maestro flow.** Nothing on screen changes — `AGENTS.md` rule 2's named exception; the
  reproducing command stands in for the device.
- **The neighbours' own areas.** The rest of `fileeditor-extension`'s ids (`AUD-005`,
  `AUD-008`, `AUD-009`), the document's own section collapse (`AUD-002`'s `toc`-toolbar verdicts
  included), consoles and the running panel (`AUD-006`), rendering (`AUD-008`), chrome
  (`AUD-009`).
