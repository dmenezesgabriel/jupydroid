# JD-024 — An image in a markdown cell draws

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-107

## Outcome

A markdown cell's image — a pasted attachment, a file beside the notebook, a web address, or an
HTML `<img>` — draws where it is written, instead of vanishing with its alt text.

## Demo

On the arm64 tablet (`SM-X216B`) — step 5 runs a cell.

1. Cold start: seed `Markdown Images.ipynb` (markdown cells holding `![chart](attachment:chart.png)`
   with its attachment, `![plot](img/plot.png)` with `img/plot.png` beside it,
   `![logo](https://…png)`, `<img src="img/plot.png" width="120">`, and one image naming nothing
   that resolves) and open it.
2. The attachment, the workspace file, the web image and the `<img>` draw, each no wider than the
   column; the one that resolves to nothing draws its alt text.
3. Edit a markdown cell's source and run it — it renders again with its image.
4. A code cell displaying `HTML('<img src="img/plot.png">')` draws the image in its output.
5. Run a code cell; its output is the kernel's.
6. Force-stop and relaunch — the images draw again.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/attachments/src/model.ts` (`AttachmentsResolver`), `packages/rendermime/src/renderers.ts` (`handleUrls`, `handleAttr`, `handleDefaults`) | Every local `src` is resolved: `attachment:` to a `data:` URL from the cell's bundle, a path to the server's download URL; a remote `src` is left for the browser to fetch |
| `vscode` | FULL | `vscode@3f3380c6 extensions/ipynb/notebook-src/cellAttachmentRenderer.ts`, `extensions/ipynb/src/notebookImagePaste.ts` | A markdown-it `image` rule swaps `attachment:` for a `data:` URL; the webview fetches the rest |
| `vscode-jupyter` | ABSENT | `vscode-jupyter@f01d80c package.json` | vscode's own `ipynb` extension renders markdown cells |
| `intellij-community` | FULL (Compose) | `intellij-community@406a27a8 platform/jewel/markdown/core/src/main/kotlin/org/jetbrains/jewel/markdown/InlineMarkdown.kt` (`Image`), `.../rendering/DefaultInlineMarkdownRenderer.kt` (`renderImage`), `.../rendering/ImageSourceResolver.kt`, `platform/jewel/markdown/extensions/images/.../Coil3ImageRendererExtensionImpl.kt` | An image is an inline: `appendInlineContent` in the paragraph's string, an `InlineTextContent` sized once Coil decodes it, text when it fails |
| `termux-app` | ABSENT | `termux-app@3df69d1 termux-shared/build.gradle` | Markwon core, strikethrough and linkify, without `markwon:image` |
| `winlator` | ABSENT | — | No markdown surface |
| the shipped rootfs | PARTIAL | `jupyter_server/services/contents/` | The server half of Lab's path resolution: it serves a workspace file's bytes |

**Count:** 3 of 7 draw it, and all three that render notebook markdown do. Every one resolves the
source at draw time rather than at parse time.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Domain model | the cell's `attachments` bundle: `packages/attachments/src/model.ts` (`AttachmentsModel`) |
| Port / protocol | `IRenderMime.IResolver` (`resolveUrl`, `getDownloadUrl`, `isLocal`): `packages/rendermime-interfaces/src/index.ts` |
| Adapter | `AttachmentsResolver` (`attachments/src/model.ts`); `RenderMimeRegistry.UrlResolver` (`rendermime/src/registry.ts`) |
| UI entry point | `renderMarkdown` → `Private.handleUrls` → `handleAttr` (`rendermime/src/renderers.ts`) |
| Empty / error states | a missing attachment resolves to its unprocessed path and draws broken; a non-image mime throws |
| Accessibility specifics | `handleDefaults`: an `<img>` with no `alt` gets `alt="Image"` |
| Edge cases it handles | first mime key only; only `imageRendererFactory`'s mimes; cache-busting query on a non-`data:` local URL |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| UI entry point | `extensions/ipynb/notebook-src/cellAttachmentRenderer.ts` |
| Edge cases it handles | the name is `decodeURIComponent`d, falling back to the raw name; paste writes `<attachment:name>` when the name has a space (`notebookImagePaste.ts`) |

### intellij-community@406a27a8 (Jewel)

| Concern | Files |
|---|---|
| Domain model | `InlineMarkdown.Image(source, alt, title, inlineContent, width, height)` |
| Port / protocol | `ImageSourceResolver`, `ImageRendererExtension.renderImage` → `ImageRenderResult` |
| Adapter | `Coil3ImageRendererExtensionImpl` |
| UI entry point | `DefaultInlineMarkdownRenderer.renderImage` (`appendInlineContent`), `DefaultMarkdownBlockRenderer.resolveImages` |
| Empty / error / loading states | loading: a spinner placeholder at the specified size; failure: the slot's text stays |
| Accessibility specifics | the image's `title` is its content description |

**Disagreements:**
- The attachment name: Lab slices it raw, VS Code percent-decodes it.
- A failed image: Lab draws a broken image, Jewel draws text.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Image in the model | Jewel → an inline in the paragraph; Lab → an `<img>` in the flow | A block image breaks a badge row into lines | An inline, `RichInline.Image`, drawn through `InlineTextContent` |
| When the source resolves | all three → at draw time, through a resolver | Parse-time resolution would tie a parser to the workspace and the network | At draw time, through a resolver the screen provides — the split links already use |
| Attachment name | Lab → raw; VS Code → decoded | Decoding is the superset: a raw name has no `%` escape to break | Decoded, falling back to raw on a malformed escape |
| A failed image | Lab → broken glyph; Jewel → text | A glyph says nothing a reader can use | The alt text, as Jewel does |
| Width | Lab → `max-width: 100%`; Jewel → the image's own size | — | The image's own size with a pixel to a dp, capped at the column — ADR-0086, as an output image |
| A web `src` | Lab, Jewel → fetched | Fetching tells the author when, and from where, the notebook was read | Fetched (chosen with the user), through a port with an HTTP cache in `cacheDir` |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Parse `![alt](src)` | `markdown/MarkdownDocument.kt` `asInline` drops `Image` and its alt | Jewel `InlineMarkdown.Image` | absent |
| Attachment bundle | round-trips in `Cell.preserved` (`domain/model/Notebook.kt`), never read | `AttachmentsResolver` | present but unreachable |
| Workspace path | `richtext/RichLinkDestination.kt` `workspaceLinkPath`; `WorkspaceRepository.readBytes` | `UrlResolver` | present, unused for images |
| Decode and draw | `ui/editor/CellOutputsView.kt` `OutputImage` (`BitmapFactory`, ADR-0086 width) | Coil3 extension | present for outputs only |
| `<img>` in HTML | `html/HtmlInlines.kt` flattens it to nothing; commonmark `HtmlBlock`/`HtmlInline` dropped | `handleAttr` | absent |
| Remote fetch | OkHttp is a dependency (`data/space/`) | browser / Coil | absent |

## Deviations

- A failed image draws its alt text rather than a broken glyph — in a comment where the image draws.
- An `http:` image draws its alt text, where Lab and VS Code's notebook fetch it — ADR-0087.

## Open decisions

- None. T3's cap is 5 MiB, https only, every markdown image decoded under the canvas floor — ADR-0087.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — (attachments stay in `PreservedJson`) |
| `domain/port` | `RemoteImageReader` (T3) |
| `data/<mechanism>` | `data/http/OkHttpRemoteImageReader` (T3) |
| `data/fake` | `FakeRemoteImageReader` (T3) |
| `richtext/`, `markdown/`, `html/` | `RichInline.Image`, its parse from markdown and `<img>` |
| `ui/editor` | the attachment lookup, the resolver seam, `RichText`'s inline image, `MarkdownCell`/`EditorCell` wiring |
| `di/AppContainer` | the remote reader (T3) |
| unit tests | `MarkdownDocumentTest`, the attachment lookup's test, `HtmlDocumentTest` |
| instrumented tests | `RichTextImageTest` |
| `.maestro/` flow + `scripts/` driver | `jd-024-markdown-images-*` |

## Tasks

- [x] T1 — `![alt](attachment:name)` in a markdown cell draws the cell's own attachment, capped at
  the column; a missing one draws its alt text
- [x] T2 — `![alt](img/a.png)` draws a workspace file, resolved against the notebook's directory
  and read off the main thread
- [x] T3 — `![alt](https://…)` is fetched, through a port with an HTTP cache and a size cap
- [x] T4 — `<img src alt width>` draws, in a markdown cell and in a `text/html` output, through
  the same node and resolver
- [ ] T5 — The demo above, run end to end on SM-X216B

## Out of scope

- Pasting or dropping an image into a cell's attachments — the edit side, not a render.
- `image/svg+xml` — `GAP-102`.
- A `.md` file's preview — `GAP-094`.
- An animated GIF's frames — `GAP-163`.
