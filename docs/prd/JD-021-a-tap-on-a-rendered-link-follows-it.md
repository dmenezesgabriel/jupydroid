# JD-021 — A tap on a rendered link follows it

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-093

## Outcome

A tap on a link drawn in a markdown cell, a `text/markdown` or `text/html` output or a Console entry
goes where the link says: a web address opens in the browser, a workspace path opens that document,
where today the tap does nothing, or in a markdown cell opens the cell's editor.

## Demo

On `SM-X216B` (it runs a cell), from a cold launch:

1. New notebook; a markdown cell holds `Read [Jupyter](https://jupyter.org) and [notes](notes.md)`,
   and `notes.md` exists beside the notebook. Rendered, both draw as links.
2. Tap "Jupyter": the browser comes to the front on `jupyter.org`. Back returns to the notebook, the
   cell still rendered, never opened for editing.
3. Tap "Read": the cell opens for editing, as a tap on rendered markdown always has.
4. Tap "notes": `notes.md` opens as a tab.
5. A code cell runs `from IPython.display import Markdown, display;
   display(Markdown("see https://jupyter.org")); print(6*7)`. Tap the URL in the output: the browser
   comes to the front.
6. A Console on the same kernel runs the same `display(Markdown(...))`; its link opens the browser too.
7. Force-stop and relaunch with No Kernel: the saved cell's and the saved output's links still open.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/rendermime/src/renderers.ts` (`handleUrls`, `handleAnchor`), `packages/rendermime/src/registry.ts` (`UrlResolver`), `packages/rendermime-extension/src/index.ts` (`handle-local-link`), `packages/coreutils/src/url.ts` (`isLocal`) | Per anchor: a bare `#hash` scrolls to `[data-jupyter-id]` in the same document; a non-local href is left to the browser in a new tab; a local one is resolved against the document's directory, checked with `contents.get`, then `openOrReveal` and `setFragment` |
| `vscode` | FULL | `vscode@3f3380c6 src/vs/workbench/contrib/notebook/browser/view/renderers/webviewPreloads.ts` (posts `clicked-link`), `backLayerWebView.ts` (routes it) | One click listener in the webview posts the href; the host sends `http`, `https` and `mailto` to the opener service, a scheme-less href to `_handleResourceOpening` as a file path, and `command:` only for an allowlist |
| `vscode-jupyter` | ABSENT (delegates) | — | Outputs are drawn by vscode's notebook webview, so the route above is the only one |
| `intellij-community` | FULL | `intellij-community@406a27a8 platform/jewel/markdown/core/src/main/kotlin/org/jetbrains/jewel/markdown/rendering/DefaultInlineMarkdownRenderer.kt` (`renderLink`), `.../Markdown.kt` (`onUrlClick`) | A `LinkAnnotation.Clickable` per link, its listener calling an `onUrlClick` parameter threaded through every renderer |
| `termux-app` | PARTIAL | `termux-app@3df69d1 termux-shared/src/main/java/com/termux/shared/interact/ShareUtils.java` (`openUrl`) | `ACTION_VIEW` on the parsed URL, `ActivityNotFoundException` caught. The Android precedent for the hand-off, not for a rendered link |
| `winlator` | PARTIAL | `winlator@fb66541 app/app/src/main/java/com/winlator/core/FileUtils.java` | `ACTION_VIEW` on a path or a `FileProvider` URI |
| the shipped rootfs | ABSENT | — | The server and kernel draw nothing; a link is a client's concern |

**Count:** 3 of 7 follow a rendered link, every one of the three that renders markdown. A drawn link
that does nothing exists in none of them.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Local or not | `packages/coreutils/src/url.ts` `isLocal`: no protocol and no leading `/` |
| Resolve a local href | `packages/rendermime/src/registry.ts` `resolveUrl`: against `dirname` of the document's path |
| Open it | `packages/rendermime-extension/src/index.ts` `handle-local-link`: `contents.get(path, {content: false})` first, then `openOrReveal` |
| Same-document hash | `packages/rendermime/src/renderers.ts` `handleAnchor`, ids from `createHeaderId` |
| Error state | a path the server does not have rejects, and nothing opens |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Which schemes leave the app | `src/vs/workbench/contrib/notebook/browser/view/renderers/backLayerWebView.ts`, `clicked-link`: `http`, `https`, `mailto` |
| A scheme-less href | the same file, `_handleResourceOpening` |

### intellij-community@406a27a8

| Concern | Files |
|---|---|
| The tap, in Compose | `platform/jewel/markdown/core/.../rendering/DefaultInlineMarkdownRenderer.kt` `renderLink` |

**The disagreement** is how the tap reaches its consumer. Jewel threads a callback through every
renderer; Compose's own `Text` hands a `LinkAnnotation.Url` to `LocalUriHandler`.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Tap → consumer | Jewel → an `onUrlClick` parameter on every renderer; Compose → `LinkAnnotation.Url` + `LocalUriHandler` | The parameter is explicit and crosses about eight signatures here (`RichText`, `RichTable`, `CellOutputs`, `EditorCell`, `EditorScreen`, the Console); the local is ambient and crosses none | `LinkAnnotation.Url`, with this project's own `UriHandler` provided once in `Navigation.kt`. It is the framework's channel for exactly this |
| Which schemes leave the app | vscode → `http`, `https`, `mailto`; JupyterLab → whatever its sanitizer left in the `href` | An `intent:` or `file:` href handed to `startActivity` is another app's entry point chosen by a notebook's author | vscode's three. Anything else with a scheme does nothing |
| A link to nothing | JupyterLab → `contents.get` rejects and nothing opens; intellij-community → `MarkdownLinkOpenerImpl` returns; vscode → an editor opens on "the file was not found" | Silence makes a link look broken (visibility of system status); vscode's own editor is a whole pane for one miss | **Decided in T2:** a snackbar naming the resolved path, on the one `EditorScreen` already shows "No definition found" on. A directory is browsed through `onGoToFolder` |
| A link inside a tap-to-edit cell | JupyterLab → a rendered cell is entered by double click, so a single click is the link's | Ours enters on a single tap | The link takes its own tap; the rest of the cell still opens the editor. Measured by an instrumented test, not assumed |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The destination | `RichInline.Link.destination`, written under `RICH_LINK_TAG` (`app/src/main/java/com/jupydroid/app/richtext/RichInlineStyles.kt`, renamed from `RichInlineString.kt` in T1) | Jewel `renderLink` | present but unreachable: a string annotation nothing reads |
| The hand-off to a browser | none; `IntentDocumentSharer` is the shape (`app/src/main/java/com/jupydroid/app/data/intent/`) | termux `ShareUtils.openUrl` | absent |
| Opening a workspace path | `openDocument` (`app/src/main/java/com/jupydroid/app/DocumentOpeners.kt`) | JupyterLab `handle-local-link` | present; no link reaches it, and nothing resolves a relative href |
| A `#heading` link | `TocHeading` jumps from the outline (`app/src/main/java/com/jupydroid/app/ui/editor/CellHeadings.kt`); no heading has an id | JupyterLab `createHeaderId` | absent |

## Deviations

- No download attribute, no `command:` links and no `kernel` scope: there is no download folder, no
  command registry and no debugger source viewer for them to reach.
- `ftp` and `tel` are not followed. vscode's three schemes are the set.

## Open decisions

- **T3:** whether a `#heading` link is built here or recorded as a gap. It needs a heading id rule
  (`createHeaderId`) that nothing here has; decided when T2 is done, on what the outline's jump can
  already be asked.

## Surface

| Layer | Files |
|---|---|
| `domain/port` | `ExternalLinkOpener.kt` |
| `data/intent` | `IntentExternalLinkOpener.kt` |
| `data/fake` | `FakeExternalLinkOpener.kt` |
| `richtext/` | `RichInlineStyles.kt`, `RichLinkDestination.kt` (`workspaceLinkPath`) |
| `ui/editor/` | `WorkspaceLinkUriHandler.kt`, provided in `EditorScreen.kt` around its Scaffold; `CellRevealScroll.kt` made the room |
| app root | `RenderedLinkUriHandler.kt`, provided in `MainActivity.kt` - `Navigation.kt` is ten lines from the file limit |
| `di/AppContainer` | the port, defaulting to the fake; `JupydroidApplication` builds the real one |
| unit tests | `RichInlineStringTest`, `RichLinkDestinationTest`, `FakeExternalLinkOpenerTest`, `WorkspaceLinkUriHandlerTest` |
| instrumented tests | a markdown cell's link tap, `IntentExternalLinkOpenerTest`, `WorkspaceLinksFollowedTest` |
| `.maestro/` flow + `scripts/` driver | `jd-021-rendered-link-t<n>` |

## Tasks

- [x] T1 — a web link opens in the browser: `http`, `https` and `mailto`, from a markdown cell and an
  output; the rest of a markdown cell still opens its editor. The Console draws through the same
  `CellOutputs`, and is driven in T4's demo.
- [x] T2 — a workspace link opens the document: resolved against the notebook's directory, checked to
  exist, then `openDocument`.
- [ ] T3 — a `#heading` link scrolls to its heading, or becomes a gap (see Open decisions).
- [ ] T4 — the demo on `SM-X216B`; ship, delete this PRD, close `GAP-093`.

## Out of scope

- Linkifying plain text output and tracebacks: `GAP-105`.
- A rendered markdown or HTML document as a link's landing: `GAP-094`, `GAP-095`. A `.md` link opens
  the file editor, as a tap in the explorer does.
- Links in the terminal: `GAP-082`.
