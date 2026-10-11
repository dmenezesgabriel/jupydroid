# JD-022 — A link printed in a text output is followed

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-105

## Outcome

A web address printed to a stream, written into a traceback or returned as `text/plain` draws as a
link, in the notebook and the Console, and a tap on it opens the browser; a traceback's path to a
workspace file opens that file. Today both are plain text.

## Demo

On `SM-X216B` (it runs a cell), from a cold launch:

1. New notebook; a code cell runs `print("see https://jupyter.org")`. The URL draws as a link.
2. Tap the URL: the browser comes to the front on `jupyter.org`. Back returns to the notebook.
3. Tap "see": nothing leaves the app.
4. A cell runs `raise ValueError("https://jupyter.org")`; tap the URL in the traceback: the browser.
5. `mod.py` beside the notebook holds `def f(): 1/0`; a cell runs `import mod; mod.f()`. Tap
   `~/mod.py` in the traceback: `mod.py` opens as a tab.
6. A Console on the same kernel runs step 1's `print`; its link opens the browser too.
7. Force-stop and relaunch with No Kernel: the saved outputs' links still open.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/rendermime/src/renderers.ts` (`autolink`, `WebLinker`, `PathLinker`, `renderText`, `renderError`, `handlePathAnchor`), `packages/rendermime/src/registry.ts` (`resolvePath`) | Web links in every text and error render; paths only in errors, linked only after the server's resolve-path API finds them |
| `vscode` | FULL | `vscode@3f3380c6 extensions/notebook-renderers/src/linkify.ts` (`LinkDetector`), `src/index.ts` (`linkifyFilePaths`), `src/stackTraceHelper.ts` | The same web regex (Lab's is copied from it); paths in streams and errors behind a setting; IPython's `Cell In[n]` rewritten to a cell link |
| `vscode-jupyter` | ABSENT (delegates) | — | Outputs are vscode's notebook renderers above |
| `intellij-community` | FULL | `intellij-community@406a27a8 platform/execution-impl/src/com/intellij/execution/filters/UrlFilter.java`, `PatternBasedFileHyperlinkFilter.java` | A console filter per line: `URLUtil` patterns for URLs, file filters for `path:line` |
| `termux-app` | PARTIAL | `termux-app@3df69d1 termux-shared/src/main/java/com/termux/shared/termux/data/TermuxUrlUtils.java`, `app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java` | A URL under a tap (opt-in) or a "Select URL" list, from a scheme-list regex over the terminal's text |
| `winlator` | ABSENT | — | No text output surface with links |
| the shipped rootfs | ABSENT | — | The kernel prints text; links are a client's concern |

**Count:** 4 of 7 detect links in plain text, every one that draws a console or an output.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Web pattern | `renderers.ts` `ILinker.webLinkRegex`: `scheme://`, `data:` or `www.`, then no whitespace, control code or `"`, and a last character outside `'(){}[],:;.!?` |
| `www.` | `WebLinker.createAnchor` prefixes `https://`; `processPath` drops a trailing `>`/`<` |
| Path pattern | `pathLinkRegex`: `posixPathRegex` (`~` or `.` optional, then `/` segments) + `lineColumnRegex` (`:N` or `", line N`, then `:C`) |
| Which renders | `renderText` web only; `renderError` web and paths |
| Path check | `handlePathAnchor` → `resolver.resolvePath` (server API, legacy heuristics); a miss stays text |
| Streaming cost | `getApplicableLinkCache` re-links only the text appended since the last render |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Web pattern | `linkify.ts` `WEB_LINK_REGEX`, trailing `'` not excluded |
| Path pattern | `POSIX_PATH` with a `(?<=^\|\s)` lookbehind, `LINE_COLUMN` `:N:C` only |
| Traceback cells | `stackTraceHelper.ts` |

**The disagreement** is where paths get linked: Lab only in errors and only once it has checked
they exist; VS Code in streams too, unchecked.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Which URLs are linked | Lab, VS Code → any `scheme://`, `data:`, `www.` | `RenderedLinkUriHandler` follows `http`, `https`, `mailto` only, so an `ftp://` link would draw and do nothing | `http://`, `https://`, `www.` (→ `https://`). A link that does nothing reads as broken |
| The pattern | Lab and VS Code's `webLinkRegex` | — | Theirs, scheme narrowed, Lab's trailing set (which also drops `'`) |
| Where detection runs | Lab → at render, cached by prefix | `render()` is called uncached by search and the outline, which need no links | In `OutputText`, remembered on the text, laid on after the colours and before search marks |
| Which outputs link paths | Lab → errors; VS Code → streams and errors | **Decided in T2** | |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Following a link | `RenderedLinkUriHandler.kt`, `ui/editor/WorkspaceLinkUriHandler.kt` | Lab `handle-local-link` | complete |
| Finding a link in text | none — `OutputText` in `ui/editor/CellOutputsView.kt` draws `coloured()` text | Lab `autolink` | absent |
| Path to a workspace file | `workspaceLinkPath` takes a relative path only (`richtext/RichLinkDestination.kt`); the workspace is the kernel's `HOME` (`data/space/SpaceRuntime.kt`) | Lab `resolvePath` | absent; `~/x` maps to `x` |
| The Console's links | `ConsoleTranscriptView.kt` draws `CellOutputs` under the app root's handler only | — | web links follow; no workspace link handler |

## Deviations

- No `data:`, `ftp://` or other scheme is linked: nothing would follow it.
- A path link opens the file and goes no further: no document jumps to a line (`GAP-122`).

## Surface

| Layer | Files |
|---|---|
| `richtext/` | `PlainTextLink.kt` |
| `ui/editor/` | `OutputTextLinks.kt`, `CellOutputsView.kt` |
| `ui/console/` | T2: a workspace link handler around the transcript |
| unit tests | `PlainTextLinkTest`, `OutputTextLinksTest` |
| instrumented tests | `CellOutputsViewTest` |
| `.maestro/` flow + `scripts/` driver | `jd-022-text-output-link-t<n>` |

## Tasks

- [x] T1 — a web address in a stream, error or `text/plain` output draws as a link and opens the
  browser, in the notebook and the Console.
- [ ] T2 — a traceback's path to a workspace file opens it: `~/mod.py:3`, an absolute path under
  the workspace, `File "…", line N`; a path outside the workspace stays text; the Console follows
  workspace links too.
- [ ] T3 — the demo on `SM-X216B`; ship, delete this PRD, close `GAP-105`.

## Out of scope

- Jumping to the line a path names: `GAP-122`.
- `Cell In[n]` as a link to the cell: VS Code's `stackTraceHelper.ts`, no gap yet.
- Links in the terminal: `GAP-082`.
