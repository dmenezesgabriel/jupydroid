# JD-023 — A shared notebook opens with its cells

Milestone: `M4` · State: in-progress · Depends on: — · Closes: GAP-119

## Outcome

A `.ipynb` opened from another app — a file manager, a mail attachment — opens in the editor with
its own cells and saved outputs, instead of as an empty notebook that only borrowed its name.

## Demo

On the arm64 tablet (`SM-X216B`) — step 3 runs a cell.

1. Cold start: push `Shared analysis.ipynb` (a markdown cell, a code cell with a saved output, a
   second code cell) to `Download/` and open it from the system file manager with Jupydroid.
2. The notebook opens as `Shared analysis.ipynb`: the markdown renders, the saved output shows.
3. Run the second cell — its output is the kernel's.
4. Open the same file from the file manager again — `Shared analysis1.ipynb` opens; the first copy
   is untouched.
5. Force-stop and relaunch — both are in the workspace with their cells.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `jupyterlab@7f0e690 packages/filebrowser/src/model.ts` (`upload`), `packages/filebrowser-extension/src/index.ts` (`autoOpenUploads`), `packages/docmanager/src/dialogs.ts` (`shouldOverwrite`) | The file's bytes are uploaded into the browser's folder as they are, then `docmanager:open` opens the copy; a taken name asks before it overwrites |
| `vscode` | FULL, in place | `vscode@3f3380c6 src/vs/platform/windows/electron-main/windowsMainService.ts` (`filesToOpenOrCreate`), `extensions/ipynb/src/notebookSerializer.ts` (`deserializeNotebook`) | A path the OS hands over opens where it lies; the ipynb serializer turns its bytes into cells |
| `vscode-jupyter` | ABSENT | `vscode-jupyter@f01d80c package.json` | Offers new notebooks only; opening a file is vscode's own `ipynb` extension |
| `intellij-community` | PARTIAL | `intellij-community@406a27a8 platform/platform-impl/src/com/intellij/ide/actions/OpenFileAction.kt` | Opens any file in place; the notebook editor itself is not in the community tree |
| `termux-app` | FULL | `termux-app@3df69d1 app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java` (`handleContentUri`, `saveStreamWithName`) | A shared content URI's `DISPLAY_NAME` names it, `openInputStream` copies the bytes into `~/downloads`, then an editor opens the copy |
| `winlator` | PARTIAL | `winlator@fb66541 app/app/src/main/java/com/winlator/SettingsFragment.java` (`ACTION_OPEN_DOCUMENT`), `core/FileUtils.java` (`readString(Context, Uri)`) | Reads a picked content URI through the content resolver; receives no VIEW intent |
| the shipped rootfs | PARTIAL | `jupyter_server/services/contents/filemanager.py` (`save` with `format: base64`) | The server half of Lab's upload: bytes written verbatim under the path given |

**Count:** 3 of 7 implement it fully, and every one that does reads the document's bytes. The two
that live on Android or behind a server (Lab, Termux) copy the bytes into their own tree before
opening — which is our shape too, since a content URI is not a path the server or kernel can see.

## Implementation inventory

### jupyterlab@7f0e690

| Concern | Files |
|---|---|
| Adapter | `packages/filebrowser/src/model.ts` `upload` → `_upload` |
| UI entry point | `packages/filebrowser/src/upload.ts`, `packages/filebrowser-extension/src/index.ts` (`autoOpenUploads`) |
| Edge cases it handles | taken name → `shouldOverwrite` dialog; large file → `_shouldUploadLarge` confirm; chunked upload |

### termux-app@3df69d1

| Concern | Files |
|---|---|
| Adapter | `FileReceiverActivity.java` `handleContentUri`, `saveStreamWithName` |
| Empty / error states | `showErrorDialogAndQuit` on a failed read or write |
| Edge cases it handles | name from `DISPLAY_NAME`, else `EXTRA_TITLE`, else the URI's basename; a name prompt before saving; a taken name is overwritten |

**Disagreement:** a taken name. Lab asks before overwriting; Termux overwrites without asking.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| What lands | Lab, Termux → the bytes verbatim; vscode → the file in place | In place cannot work: a content URI is not a path the server or kernel sees, and the grant ends with the activity | The bytes verbatim, through `WorkspaceRepository.writeBytes`. Nothing is parsed and rebuilt, so nothing the app does not model is lost |
| Name taken | Lab → ask; Termux → overwrite | Overwriting silently can destroy the user's own notebook; asking adds a dialog to an open | Increment (`incrementedFileName`), as `createNotebook` already does. A VIEW intent asks to *view*, never to replace |
| Invalid nbformat | Lab → open and report (GAP-118) | — | Copied and opened; the editor's existing error state says why |
| Where the read runs | Termux → on the activity's thread | A notebook with image outputs can be MBs | Inside the import's coroutine, through a port |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Name from the URI | `MainActivity.displayNameOf` | `termux-app@3df69d1 FileReceiverActivity.java` | complete |
| Bytes from the URI | — | `termux-app@3df69d1 FileReceiverActivity.java` | absent |
| Write into the workspace | `WorkspaceRepository.writeBytes` | `jupyterlab@7f0e690 filebrowser/src/model.ts` | present but unreachable from the import |
| Open the result | `importNotebook` → `openDocument` (`DocumentOpeners.kt`) | `autoOpenUploads` | complete |

## Deviations

- A taken name increments rather than asking (Lab) or overwriting (Termux) — in the KDoc of
  `importNotebook`.

## Open decisions

None.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `SharedNotebook` (the URI and the file name) |
| `domain/port` | `SharedDocumentReader` |
| `data/intent` | `ContentResolverSharedDocumentReader` |
| `data/fake` | `FakeSharedDocumentReader` |
| app shell | `MainActivity`, `DocumentOpeners.kt`, `Navigation.kt`, `import/` file-name rule |
| `di/AppContainer` | the reader |
| unit tests | `DocumentOpenersTest`, the file-name rule's test |
| instrumented tests | `ContentResolverSharedDocumentReaderTest` |
| `.maestro/` flow + `scripts/` driver | a VIEW intent on a fixture with cells and an output |

## Tasks

- [x] T1 — A VIEW intent's bytes land in the workspace under its name, incremented when taken, and
  open: the cells, a saved output and a run cell, on SM-X216B
- [ ] T2 — The demo above, run end to end on SM-X216B

## Out of scope

- Any file that is not a `.ipynb`, and a picker to bring one in — `GAP-054`.
- Validating the copied notebook and saying what is wrong with it — `GAP-118`.
