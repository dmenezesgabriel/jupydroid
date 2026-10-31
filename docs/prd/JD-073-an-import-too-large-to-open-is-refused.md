# JD-073 — An import too large to open is refused, not read whole

Milestone: `M8` · State: open · Depends on: — · Closes: GAP-190

## Outcome

A notebook another app shares that is larger than the bound is refused with a message that names its
size and the limit, instead of being read whole into the heap (or silently dropped).

## Demo

On the arm64 tablet (`SM-X216B`), from a cold start:

1. Install, launch, share a small `.ipynb` from My Files: it opens in its Space, as before.
2. Share a file over the bound from My Files: nothing opens, and a snackbar says its size and the limit.
3. Kill and relaunch: the refused file left nothing in the workspace.

Not `shipped` until run on that device, with screenshots and `maestro.log` under `evidence/jd-073-*/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 packages/filebrowser/src/model.ts` (`LARGE_FILE_SIZE`, `CHUNK_SIZE`) | 15 MiB marks a file "large": it asks before uploading, and refuses where the server cannot chunk. Chunks are 1 MiB |
| `vscode` | FULL | `vscode@3f3380c src/vs/platform/files/common/files.ts` (`TooLargeFileOperationError`), `fileService.ts:757` (`limits.size`) | A read takes a size limit; past it a typed error carrying `size` is thrown, and the editor shows it |
| `vscode-jupyter` | ABSENT | — | Opens through VS Code's file service |
| `intellij-community` | PARTIAL | `intellij-community@406a27a platform/core-api/src/com/intellij/openapi/vfs/limits/FileSizeLimit.kt` | Per-feature file size limits, not an import bound |
| `termux-app` | PARTIAL | `termux-app@3df69d1 app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java` `saveStreamWithName` | Copies 4 KiB at a time to disk: never whole in the heap, but no bound |
| `winlator` | ABSENT | — | Not checked beyond the file picker: no inbound share |
| the shipped rootfs | ABSENT | — | The import ends in the app's `copyIn`, not in the rootfs |

**Count:** 3 of 7 bound or chunk a read. Verify the `winlator` and `vscode-jupyter` rows by grep when
building; they are read as ABSENT from their role, not yet from their code.

## Trade-offs and what we adopt

| Axis | Options seen | Our choice, and why |
|---|---|---|
| Bound | jupyterlab 15 MiB "large"; none elsewhere | 15 MiB, a named constant on the port, as `RemoteImageReader.MAX_IMAGE_BYTES` is. Heap headroom is measured on the tablet before it is fixed (`adb shell getprop dalvik.vm.heapgrowthlimit`) |
| Read | termux 4 KiB loop; vscode size limit on the stream | 4 KiB loop that stops at bound + 1. A provider's reported length can be absent or wrong, so the loop is needed with or without a length pre-check, which is therefore not added |
| Failure carrier | vscode typed error with `size` | A typed `SharedDocumentTooLarge` carrying the size read and the limit; `importNotebook` tells it from an unreadable stream |
| Surface | vscode editor message; jupyterlab dialog | A snackbar (`SnackbarHostState`, as `ConsoleScreen`) naming the file, its size and the limit |

## Gap analysis

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| Bounded read | `ContentResolverSharedDocumentReader.readBytes` is `readBytes()`: absent | termux chunk loop, vscode `limits.size` | absent |
| Refusal reaches the user | `importNotebook` ends `runCatching{}.getOrNull()`: every refusal is silent | vscode `TooLargeFileOperationError` | absent |

## Deviations

None: a phone makes the bound lower than a desktop's would be, which is the measurement above.

## Open decisions

The bound's final value, after the heap measurement. An ADR records it and the snackbar choice.

## Surface

| Layer | Files |
|---|---|
| `domain/port` | `SharedDocumentReader` (bound constant, `SharedDocumentTooLarge`) |
| `data/intent` | `ContentResolverSharedDocumentReader` |
| `data/fake` | `FakeSharedDocumentReader` honours the same bound |
| `ui` | `DocumentOpeners.kt` `importNotebook`, `ImportedNotebookEffect` |
| unit tests | `DocumentOpenersTest`, a reader test over a named fake stream |
| instrumented tests | `ContentResolverSharedDocumentReaderTest` |
| `.maestro/` flow | an import flow beside `step2-import.yaml` |

## Tasks

- [x] T1 — a bounded read that refuses past the bound with the size, and an import that shows it
- [ ] T2 — the device demo above

## Out of scope

Streaming the import to disk without holding it in the heap (termux's shape): the editor holds a
notebook whole anyway, so the bound is what matters.
