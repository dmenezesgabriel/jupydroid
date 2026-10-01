# AUD-003 — Editor language features

Milestone: `M0` · State: in-progress · Depends on: `AUD-002` · Closes: —

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

One area — **editor language features** — taken through four questions at once, never four
sweeps: what the reference offers, what we built, where we differ and *why*, and whether the code
on that path is sound in growth, branching, failure handling and coupling.

The area is everything a cell's editor does **because the text is a language**: the LSP asks, the
kernel introspection they sit beside, and the typing rules. The partition made one command id this
slice's own — `notebook:toggle-autoclosing-brackets`, a typing rule — and `AUD-001` deferred two
port operations and one wire verdict here. The rest of the surface has no command ids at all, which
is itself the shape of the area: language features are asked by typing and caret position, not by
palette.

Three overlapping surfaces, three different enumerations:

- **The LSP surface** — `domain/port/LanguageServerPort.kt` (12 operations) driven by
  `data/space/StdioLanguageServer.kt` + `Lsp*.kt` + `JsonRpcChannel.kt` against `pylsp` over stdio,
  and the ask layer in `ui/editor/`: `NotebookAsks` over
  `Notebook{Completion,Hover,SignatureHelp,Definition,Format,Rename}` through
  `NotebookLanguageDocument`/`NotebookVirtualDocument`, with the `diagnostics` push landing as
  `diagnosticsByCellId` via `NotebookDiagnostics` → `DiagnosticMarks`/`DiagnosticSquiggles`.
  `CellAction.{GoToDefinition,FormatCell,RenameTo}` dispatch as asks in `EditorCellActions.kt`.
- **The kernel introspection it replaced or sits beside** — `KernelGateway.complete` /
  `KernelCompletions` (`complete_request`, now the LSP-failure fallback in `NotebookCompletion`);
  `inspect_request`, never sent, replaced by `textDocument/hover` and flagged **undocumented** by
  `AUD-001` with this slice owning the ADR; `is_complete_request`, `absent` as `GAP-012` — whose
  two reference callers are both the console's own (`console/widget.ts`'s block check and
  `notebook-extension`'s `run-in-console`), so the repair is `AUD-006`'s and this slice records.
- **The typing rules** — `PythonTypingRules` (`continueIndent`/`closePair`/`stepOverPartner`) and
  `EditorSettings.{autoClosingBrackets,indentUnit}`, which also serve `FileEditorScreen` on
  bracketed extensions. `GAP-004` is this row's known limitation.

**Carried neighbours.** The console's `is_complete_request`/`history` callers are `AUD-006`'s own
area (the wire verdicts stay in the `AUD-001` table); the presentation-shaped `editorConfig` keys —
`lineNumbers`, `lineWrap`, `theme`, `keymap`, `cursorBlinkRate`, the highlight-* family — carry to
`AUD-009`; `NotebookRepository`'s three uncalled operations stay `AUD-005`'s; `markdown/`/`html/`
*rendering* is `AUD-008`'s. Syntax highlighting (`syntax/` + `HighlightTransformation`, including
`bracketPairAround`'s `matchBrackets` counterpart) is **in** — it is an editor language feature no
other slice owns, and its search-highlight half is already `AUD-004`'s.

## The surface, produced

Never stored — ADR-0067's rule. `read_reference_commands.py` already does exactly this for the
kernel wire (`--kernelbase`); `T1` adds the same producer for the LSP method surface.

```sh
# the LSP surface the reference's connection layer routes - Method's four direction enums,
# 36 members at the pinned SHA (22 requests + 6 notifications client→server,
# 4 requests + 4 notifications server→client)
python3 scripts/read_reference_commands.py --lsp-methods        # T1 adds this producer

# the non-execute kernel asks in the reference - who sends inspect/complete/is_complete/history
grep -rln "requestInspect\|requestComplete\|requestIsComplete\|requestHistory" \
  reference/research_repos/jupyterlab/packages --include="*.ts" | grep -v "services\|test\|mock"
# 8 files: completer/kernelprovider + inlinehistoryprovider, inspector/kernelconnector,
# tooltip-extension, console/{widget,history}, notebook/src/history, notebook-extension
# (the two history.ts and inlinehistoryprovider ride history_request - AUD-001's `present`)

# vscode-jupyter's half of the same
grep -rln "requestInspect\|requestComplete\|do_inspect" \
  reference/research_repos/vscode-jupyter/src --include="*.ts" | grep -v test
# intellisense/{kernelCompletionProvider,resolveCompletionItem} + variables + raw session

# the editorConfig surface - 27 keys; this slice owns the six that are typing rules
grep -oE "name: '[a-zA-Z]+'" reference/research_repos/jupyterlab/packages/codemirror/src/extension.ts \
  | sort -u          # autoClosingBrackets indentUnit smartIndent matchBrackets tabSize tabFocusable
                     # are this slice's; the rest are presentation, carried to AUD-009

# our side
python3 scripts/read_port_surface.py            # LanguageServerPort: 12 operations
python3 scripts/read_port_surface.py --unused   # what no production file names - see below

# what the shipped server actually answers - probed on the tablet, committed evidence
ANDROID_SERIAL=$TABLET ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.LspSurfaceProbeTest
# -> files/lsp-probe.txt; the run's findings are evidence/p5e-0-lsp-surface/
```

**`read_port_surface.py` under-reports the dead ops here, and the miss is measured, not feared.**
`--unused` names only `closeDocument` for this port. `capabilities` reports "2 files" — the
`"capabilities"` JSON key in `LspWire.kt` and `StdioLanguageServer`'s own field, never the port
operation (`AUD-001` already noted this). `openDocument` reports "3 files" — `Navigation.kt`,
`ShellSidePane.kt` and `LauncherNavigation.kt`'s document-opening function, which is the same
identifier naming a different thing. `changeDocument`'s auto-open rule is why nothing calls it.
Three uncalled operations, two invisible to the instrument — every one of them verdicted by hand
in T2.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL — the behaviour reference | `packages/lsp/src/{tokens,connection,manager,feature}.ts`, `packages/lsp/src/virtual/document.ts`, `packages/completer/src/{handler,model,reconciliator,widget}.ts` + `default/{kernelprovider,contextprovider,inlinehistoryprovider}.ts`, `packages/inspector/`, `packages/tooltip-extension/`, `packages/codemirror/src/extension.ts` | Kernel introspection built-in (complete/inspect/is_complete providers + inspector panel + hover tooltip); LSP as a connection layer the external `jupyterlab-lsp` plugs into, over websocket, against a `VirtualDocument` that extracts foreign-language blocks |
| `vscode` | FULL — the presentation reference | `src/vs/workbench/contrib/notebook/browser/` (each cell a real `TextDocument` in `vscode-notebook-cell`) | Every language feature an extension registers applies per cell; cross-cell semantics are the language server's own problem (Pylance's notebook concat protocol) |
| `vscode-jupyter` | PARTIAL | `src/standalone/intellisense/{kernelCompletionProvider,resolveCompletionItem,diagnosticsProvider}.ts` | Kernel `complete_request` as a *merged* completion provider racing the LSP ones; doc-resolve via `requestInspect`/`do_inspect` on a background thread |
| `intellij-community` | PARTIAL | `platform/lsp/` | §8.3-authoritative for LSP client shape; the Jupyter notebook editor itself is outside the community tree |
| `termux-app` | ABSENT | — | A terminal emulator has no language features |
| `winlator` | ABSENT | — | Container lifecycle has no editor |
| the shipped rootfs | the *answer* side | `usr/lib/python3.12/site-packages/pylsp/` | `pylsp 1.15.0` — its advertised capabilities and reply shapes are measured on device (`evidence/p5e-0-lsp-surface/`), which is the bound an `absent` verdict is read against |

**Count:** 2 of 7 implement the whole area, and a third implements the kernel half. The two that
matter split deliberately per §8.3 — JupyterLab for behaviour, VS Code for presentation.

## Gap analysis — ours today

Read before any verdict is written. Stated plainly: **present and partial.** Six asks plus the
diagnostics push are wired and probed on device; the lifecycle is ours (stdio subprocess owned per
Space, not a websocket to a shared server); and most of what the reference routes is unasked.

| Concern | Ours today (file) | State |
|---|---|---|
| The port | `domain/port/LanguageServerPort.kt` | 12 operations; three uncalled (`capabilities`, `openDocument`, `closeDocument`) — the last a never-sent `didClose` with the leak-shaped consequence the walk will read |
| The adapter | `data/space/StdioLanguageServer.kt`, `LspWire.kt`, `LspFraming.kt`, `JsonRpcChannel.kt` | initialize/initialized/didOpen/didChange/didClose/shutdown/exit; capability-gated asks; whole-text sync per change |
| The virtual document | `ui/editor/Notebook{Language,Virtual}Document.kt` | one `file:///` document per notebook, Python only — the reference's `VirtualDocument` cut down, stated in the file |
| Completion | `NotebookCompletion.kt`, `CellCompletions.kt`, `CompletionQuery.kt` | LSP first, kernel `complete_request` as fallback — *not* the reference's ranked-provider merge; no `completionItem/resolve` (a row's docs are never fetched) |
| Hover | `NotebookHover.kt`, `HoverPopup.kt` | `textDocument/hover`; `inspect_request` unreplaced on the wire — **undocumented**, the ADR is this slice's |
| Signature help | `NotebookSignatureHelp.kt`, `CellSignatureHelp.kt`, `SignatureHelpQuery.kt` | triggered by a typed `(`/`,`/`=` (pylsp's measured `triggerCharacters`), re-asked on any change while armed — and gated by `syntax/`'s `insideUnclosedParen` |
| Definition | `NotebookDefinition.kt`, `LandingOnDefinition.kt` | jumps stay inside the virtual document; foreign targets dropped, stated |
| Format | `NotebookFormat.kt`, `NotebookCellEdits.cellFormatted` | `rangeFormatting` over the cell's block; whole-doc `formatting` unasked — the FileEditor's own candidate |
| Rename | `NotebookRename.kt`, `RenameSymbolDialog.kt`, `NotebookCellEdits.cellsRenamed` | `WorkspaceEdit` sliced and verified back into cells |
| Diagnostics | `NotebookDiagnostics.kt`, `DiagnosticMarks.kt`, `DiagnosticSquiggles.kt`, `CellDiagnostic.kt` | `publishDiagnostics` → gutter marks + squiggles + detail popup; no diagnostics *panel*; `textDocument/diagnostic` pull unasked |
| Server→client asks | — | **absent**: `workspace/configuration`, `client/registerCapability`, `showMessage*` — the route pylsp's plugin config would take |
| Kernel introspection | `KernelCompletions.kt` | `complete_request` as fallback (adapted, cited); `inspect_request` never sent (undocumented → ADR owed); `is_complete_request` absent (`GAP-012`) |
| Typing rules | `PythonTypingRules.kt`, `EditorSettings.kt` | present with `GAP-004`'s two holes; `notebook:toggle-autoclosing-brackets` is this slice's one command id |
| Highlighting + bracket match | `syntax/`, `HighlightTransformation.kt` | present — ANTLR lexers, `matchBrackets` via `bracketPairAround` |
| FileEditor | `ui/fileeditor/` | typing rules + highlighting, **no LSP asks** — the reference's fileeditor adapter has them |

## Deviations

Known going in, so a verdict does not re-litigate them:

- **The kernel is a completion fallback, not a merged provider.** The reference ranks providers and
  merges their rows (`ICompletionProviderManager`, `manager.json`'s `availableProviders`); ours asks
  `pylsp` first and the kernel only when it cannot answer — `NotebookCompletion` says so and cites it.
- **The virtual document is Python-only** — no foreign-language extraction, no magics becoming
  foreign documents (`NotebookVirtualDocument`'s own statement against `virtual/document.ts`).
- **`autoClosingBrackets` defaults true** where the schema's is false — `EditorSettings` carries the
  reason (a soft keyboard's symbol row).
- **stdio, not websocket** — the reference's `jupyter-lsp` route stays a swap behind the port, and
  owning the process is why `didClose`/`shutdown`/`exit` exist at all (the reference's `Method`
  enum routes none of them — it never closes a document and never stops a server).
- **One virtual document per notebook, not per file** — and its uri keys on the workspace name, so
  a rename orphans the server's copy; that is the `closeDocument` row's real content, read in T2.

## Tasks

- [x] T1 — **The instrument and the surface, produced.** `read_reference_commands.py` gains the
      LSP-method producer — `lsp_method_names(tokens.ts)` + `--lsp-methods`, red test first in
      `read_reference_commands_test.py`, same shape as `rootfs_kernel_message_types`/`--kernelbase`:
      the `Method` namespace's four direction enums read as declared members. Land the
      `## Editor language features — AUD-003` section in `research/parity-ledger.md`: the surface
      paragraphs, the produced counts, and which neighbours carry which entries — a partition with
      no verdicts yet.
- [x] T2 — **Verdicts for the LSP half.** One ledger row per reference method and per port op:
      the six asks + lifecycle + `publishDiagnostics`; the unasked methods each read against "can
      the shipped `pylsp` even answer it" (the probe) and "is there a surface for it" —
      `completionItem/resolve`, `references`, `documentHighlight`, `documentSymbol`, `foldingRange`,
      `formatting`, `codeAction`, `typeDefinition`, `implementation`, `inlayHint`, `inlineValue`,
      `linkedEditingRange`, `documentColor`, `diagnostic`, `workspace/symbol(+resolve)`; the
      server→client direction nobody answers (`workspace/configuration` — the `pylsp.plugins.*`
      route `LanguageFormatOptions`' docstring already names); `didSave`/`didChangeConfiguration`;
      the three uncalled port ops decided (kept-with-reason or removed — the reason lands in a
      comment or ADR either way, never in the ledger alone). And the `inspect_request` **ADR**
      `AUD-001` charged here: the kernel answers the *live* object, the server answers the *source* —
      the substitution is real and lossy, and it says which direction it loses.
- [x] T3 — **Verdicts for the kernel-introspection and typing half.** `complete_request` as
      fallback vs the ranked-provider merge (vscode-jupyter's is a third shape — racing); the
      inspector panel, the tooltip-as-separate-widget, completer doc-resolve and the inline-history
      provider each verdicted; `is_complete_request` restated with its owners (`GAP-012`, and
      both reference callers are `AUD-006`'s console side). `notebook:toggle-autoclosing-brackets` and the five other
      language-rule `editorConfig` keys (`indentUnit`, `smartIndent`, `matchBrackets`, `tabSize`,
      `tabFocusable`) against `PythonTypingRules`/`EditorSettings`/`HighlightTransformation`;
      `GAP-004` decided; the highlighting row.
- [x] T4 — **The walk.** `EditorCell`/`CodeCellField` → `rememberCellCompletions` /
      `rememberCellSignatureHelp` (the 250ms debounce, `collectLatest`) → `NotebookAsks` →
      `Notebook{Completion,…}` → `NotebookLanguageDocument.sync` (whole document per ask,
      `documentLock`, the no-change skip) → `StdioLanguageServer.ServerPool` → `JsonRpcChannel` →
      `pylsp`; and back: `diagnostics` → `keepSynced` (a `didChange` per distinct `(cells,
      kernelConnected)`) → `relayPublishes` → `diagnosticsByCellId` → the marks. Growth: every ask
      and every distinct cells state ships the whole notebook text, and `pylsp` re-analyzes it
      (~600ms, measured by the probe); branching: the capability gates, the `session.opened` gate
      that keeps typing from booting a runtime; failure: silence-is-not-a-crash catches, the stale
      mirror the diagnostics map against, the dead-server respawn; coupling: `BASE_SPACE` hardcoded
      (JD-001's question), `NotebookKernel` owning the asks, the uncalled lifecycle ops.

## Out of scope

- **Building anything.** An `AUD-NNN` repairs only a `defect`, red test first; every other repair it
  finds becomes a `JD-NNN` with a demo of its own. A verdict of "remove" on a dead port op is a
  record, and the removal — if wanted — is such an item, not this audit's.
- **A Maestro flow.** Nothing on screen changes — `AGENTS.md` rule 2's one named exception. What
  stands in for the device is the command that reproduces each measurement, in the commit body.
  `LspSurfaceProbeTest` already ran on the tablet; re-running it is how a *new* question about
  `pylsp` gets answered, not a gate T1-T4 owe.
- **The neighbours' own areas.** Console introspection and the running panel (`AUD-006`),
  presentation `editorConfig` keys and notebook chrome (`AUD-009`), `NotebookRepository`'s dead ops
  and the filebrowser (`AUD-005`), markdown/nbformat rendering (`AUD-008`), the comm channel and
  ipywidgets (`GAP-013`), the FileEditor's missing asks get a verdict row here and their build item
  there.
