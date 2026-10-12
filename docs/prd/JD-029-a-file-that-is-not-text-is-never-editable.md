# JD-029 — A file that is not text is never opened as editable text

Milestone: `M5` · State: in-progress · Depends on: JD-028 · Closes: GAP-097 (its first step; the viewer stays a gap)

## Outcome

Tapping an image, an archive or any other file that is not UTF-8 text no longer opens it as a page
of replacement characters whose first keystroke is saved back over the file: the document says why
it is not shown, offers no field, and the file's bytes are left as they were.

## Demo

No step runs a cell, so the emulator is allowed; the tablet (`SM-X216B`) is used when it is attached.

1. Cold start: install, launch, enter the Space. Through `run-as`, the workspace holds `plot.png`
   (a real PNG) and `notes.txt`; record the PNG's `sha256`.
2. Tap `plot.png` in the explorer. Its tab opens and says the file is not UTF-8 text and is not
   opened in the text editor. There is no text field and no keyboard.
3. Close the tab. Through `run-as`, the PNG's `sha256` is the one recorded in step 1.
4. Tap `notes.txt`. It opens in the field as before, an edit is saved, and the file holds it.
5. Force-stop and relaunch. `plot.png`'s tab says the same thing, and its `sha256` is unchanged.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | jupyterlab@7f0e6906 `packages/docregistry/src/default.ts` (`TextModelFactory.fileFormat`), `packages/fileeditor-extension/src/index.ts` (`defaultFor: ['markdown', '*']`), `packages/docregistry/src/context.ts` (`_revert`, `_handleError`) | The editor factory asks the server for `format: 'text'`; the server's refusal reaches `_handleError`, which shows **File Load Error for <name>** and the document does not populate |
| `vscode` | FULL | vscode@3f3380c6 `src/vs/workbench/services/textfile/common/encoding.ts` (`detectEncodingFromBuffer`), `src/vs/workbench/contrib/files/browser/editors/textFileEditor.ts` (`handleSetInputError`, `openAsBinary`), `src/vs/workbench/browser/parts/editor/binaryEditor.ts` | A NUL in the first 512 bytes that is not UTF-16's pattern is `FILE_IS_BINARY`; the text editor hands the input to a placeholder pane: "The file is not displayed in the text editor because it is either binary or uses an unsupported text encoding", with **Open Anyway** |
| `vscode-jupyter` | ABSENT of its own | — | No `isBinary`/`seemsBinary` in `src/`; a file is VS Code's to open, so it inherits the row above |
| `intellij-community` | FULL | intellij-community@406a27a8 `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/text/TextEditorProvider.kt` (`isTextFile`) | By file type, not by content: the text editor accepts a file only when `!fileType.isBinary`, or a decompiler exists for the type |
| `termux-app` | ABSENT | — | No document editor |
| `winlator` | ABSENT | — | No document editor |
| the shipped rootfs | FULL | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py` (`_read_file`) | `format="text"` decodes the bytes as UTF-8 strictly; `UnicodeError` is HTTP 400 `"<path> is not UTF-8 encoded"`, reason `bad format`. With no format it falls back to base64 |

**Count:** 4 of 4 that open documents refuse a binary file in a text editor by default. One of them
offers a way past the refusal.

## Implementation inventory

### the shipped rootfs

| Concern | Files |
|---|---|
| The rule | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py` `_read_file` (both the sync and the async class) |

### jupyterlab@7f0e6906

| Concern | Files |
|---|---|
| Which format a text document asks for | `packages/docregistry/src/default.ts` `TextModelFactory.fileFormat`, against `Base64ModelFactory.fileFormat` |
| The editor claiming every file type | `packages/fileeditor-extension/src/index.ts` |
| The failed read on screen | `packages/docregistry/src/context.ts` `_revert`, `_handleError` |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| The rule | `src/vs/workbench/services/textfile/common/encoding.ts` `detectEncodingFromBuffer` |
| The hand-over to the placeholder | `src/vs/workbench/contrib/files/browser/editors/textFileEditor.ts` |
| The placeholder's words and its one action | `src/vs/workbench/browser/parts/editor/binaryEditor.ts` |

### intellij-community@406a27a8

| Concern | Files |
|---|---|
| The rule | `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/text/TextEditorProvider.kt` `isTextFile` |

**Where they disagree.** On the rule: jupyter_server decodes strictly, VS Code sniffs for a NUL,
IntelliJ reads the file's type. And on the way past: VS Code alone offers **Open Anyway**.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| The rule | rootfs: strict UTF-8; vscode: a NUL in 512 bytes; intellij: the file's type | A NUL sniff passes a Latin-1 file, and a type table passes a `.txt` that is not text. The editor saves `text.toByteArray()`, which is UTF-8 | Strict UTF-8 on the bytes: it is exactly "the save writes back what was read", so nothing that passes can be corrupted by a save |
| Where the check lives | rootfs: in the read; vscode: in the read; intellij: before an editor is chosen | A check in the screen leaves every other reader to remember it | In the read: `readStampedText` refuses, so the file editor, a revert and a workspace replace are all covered by one rule |
| The refusal | rootfs: HTTP 400 with a reason; vscode: `FILE_IS_BINARY` | — | `FileNotTextException` in `domain/port/`, beside `FileChangedOnDiskException` |
| What the user sees | jupyterlab: an error dialog; vscode: a placeholder in the document's pane | A dialog is gone once dismissed and leaves a tab that says nothing | On the document's own surface, which already draws a failed read and no field (`FileEditorError`): the status stays visible for as long as the tab does |
| A way past | vscode: Open Anyway; the other three: none | The way past is the corruption this item removes | None |

## Gap analysis

| Concern | Ours today (file) | Gap |
|---|---|---|
| The text read | `app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceRepository.kt` `readStampedText` | `file.readText()`, lenient: an invalid byte becomes U+FFFD |
| The fake's read | `app/src/main/java/com/jupydroid/app/data/fake/FakeWorkspaceRepository.kt` | `decodeToString()`, lenient too |
| A failed read on screen | `app/src/main/java/com/jupydroid/app/ui/fileeditor/FileEditorScreen.kt` `FileEditorError`; `FileEditorViewModel`'s `init` | Present, and enough: `loaded` stays false, no field is drawn, `persist` writes nothing |
| Workspace replace on a closed file | `app/src/main/java/com/jupydroid/app/ui/shell/WorkspaceReplace.kt` `sourceOnDisk` | Reads through the same lenient read; a failed read is already a refusal |
| Search | `app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceSearcher.kt` `readableTextAt` | None: ripgrep's NUL rule, and it never writes |

Ours is **absent**.

## Deviations

- **No Open Anyway.** Stated above.
- **No encoding but UTF-8.** VS Code guesses and converts; the server this app ships does neither,
  and a file in another encoding is refused rather than re-encoded.
- **A text file holding a NUL opens.** It is valid UTF-8 and is written back byte for byte; search
  still passes over it.

## Open decisions

None that the references leave open.

## Surface

| Layer | Files |
|---|---|
| `domain/port` | `FileNotTextException.kt`, `WorkspaceRepository.kt` |
| `data/disk` | `DiskWorkspaceRepository.kt` |
| `data/fake` | `FakeWorkspaceRepository.kt` |
| `ui/fileeditor` | none expected; `FileEditorViewModel.kt` only if the message needs shaping |
| unit tests | `WorkspaceRepositoryContract.kt`, `FileEditorViewModelTest.kt`, the workspace replace tests |
| instrumented tests | `androidTest/.../ui/fileeditor/` |
| `.maestro/` flow + `scripts/` driver | `jd-029-not-text-*` |

## Tasks

- [ ] T1 — A file that is not UTF-8 text is refused by the read, the file editor says why and
  offers no field, a workspace replace leaves it alone, and its bytes are unchanged on the device.
- [ ] T2 — The demo above, seeded, from a cold start with a relaunch.

## Out of scope

- An image viewer and its eight acts: what is left of `GAP-097`.
- Viewers for pdf, audio and video: `GAP-109`. A CSV grid: `GAP-098`.
- Routing a file by its type before any editor is chosen.
