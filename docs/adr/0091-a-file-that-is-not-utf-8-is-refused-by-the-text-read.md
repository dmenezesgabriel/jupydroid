# ADR-0091 — A file that is not UTF-8 is refused by the text read

Status: accepted
Date: 2026-10-12

## Context

Every file tapped in the explorer that is not a notebook opens in the file editor, and the file
editor read it with `File.readText`, which turns each byte it cannot decode into U+FFFD. A `.png`
opened as a page of replacement characters in an editable field, and the first keystroke's autosave
wrote that decoding back over the image. A workspace replace on a closed Latin-1 file did the same.
That was the first half of `GAP-097`.

What the references do:

- **JupyterLab**'s editor claims every file type and asks the server for `format: 'text'`. The
  server's refusal reaches `_handleError`, which shows **File Load Error for <name>**, and the
  document does not populate (jupyterlab@7f0e6906 `packages/docregistry/src/default.ts`,
  `TextModelFactory.fileFormat`; `packages/fileeditor-extension/src/index.ts`;
  `packages/docregistry/src/context.ts`, `_revert`, `_handleError`).
- **jupyter_server**, in the shipped rootfs, decodes a `format="text"` read as UTF-8 strictly:
  `UnicodeError` is HTTP 400 `"<path> is not UTF-8 encoded"`, reason `bad format`
  (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py`, `_read_file`).
- **VS Code** calls a file binary when its first 512 bytes hold a NUL that is not UTF-16's pattern,
  and hands the input to a placeholder pane: "The file is not displayed in the text editor because
  it is either binary or uses an unsupported text encoding", with **Open Anyway** (vscode@3f3380c6
  `src/vs/workbench/services/textfile/common/encoding.ts`, `detectEncodingFromBuffer`;
  `src/vs/workbench/contrib/files/browser/editors/textFileEditor.ts`;
  `src/vs/workbench/browser/parts/editor/binaryEditor.ts`).
- **IntelliJ** decides by file type, not content: the text editor accepts a file only when
  `!fileType.isBinary` (intellij-community@406a27a8
  `platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/text/TextEditorProvider.kt`,
  `isTextFile`).

All four that open documents refuse a binary file in a text editor by default. One offers a way
past the refusal.

## Decision drivers

- The editor saves `text.toByteArray()`, which is UTF-8. Whatever the read lets through must be
  something that save writes back byte for byte.
- A caller must not be able to forget the check: the file editor, its revert and a workspace
  replace all read text.
- A refusal that is dismissed must not leave a tab that says nothing.

## Options considered

1. **VS Code's NUL sniff.** It passes a Latin-1 file, which holds no NUL and is not UTF-8.
2. **IntelliJ's type table.** It passes a `.txt` that is not text, and needs a table to keep.
3. **The shipped server's strict UTF-8 decode.** It is exactly "the save writes back what was read".

## Decision

Option 3, in the read.

- **The rule is a strict UTF-8 decode of the file's bytes.** `ByteArray.decodedAsTextOf`
  (`app/src/main/java/com/jupydroid/app/domain/port/FileNotTextException.kt`) is the one decode,
  used by `DiskWorkspaceRepository` and `FakeWorkspaceRepository`, so the disk and its fake refuse
  the same files.
- **The check lives in the read.** `readStampedText` refuses, and `readText` reads through it, so
  the file editor, a revert and a workspace replace are covered by one rule. `readBytes` still
  reads the file.
- **The refusal is a type**, `FileNotTextException` in `domain/port/`, beside
  `FileChangedOnDiskException` (ADR-0090), so a reader can tell "not text" from "not there".
- **The reason is drawn on the document's own surface.** The file editor already drew a failed read
  with no field (`app/src/main/java/com/jupydroid/app/ui/fileeditor/FileEditorScreen.kt`,
  `FileEditorError`): the document stays unloaded and nothing is ever saved. The exception's message
  is what is drawn, and it stays for as long as the tab does.
- **A workspace replace leaves the file alone.** A failed read was already a refusal there
  (`app/src/main/java/com/jupydroid/app/ui/shell/WorkspaceReplace.kt`, `sourceOnDisk`).

## Consequences

An image, an archive or a file in another encoding can be tapped, typed at, closed and reopened, and
its bytes are the ones it had.

What a future reader will be tempted to "fix":

- **Adding Open Anyway.** The way past is the corruption this decision removes: the field's save is
  UTF-8, and no file that fails the decode survives it.
- **Guessing an encoding.** VS Code guesses and converts. The server this app ships does neither,
  and a file read as Latin-1 and saved as UTF-8 is a different file.
- **Refusing a text file that holds a NUL.** It is valid UTF-8 and is written back byte for byte.
  Search passes over it by its own rule, ripgrep's
  (`app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceSearcher.kt`, `readableTextAt`), and
  never writes.
- **Moving the check into the screen.** Every other reader would then have to remember it.

Not covered, on purpose:

- **An image viewer.** The file says why it is not shown; showing it is what is left of `GAP-097`.
- **Viewers for pdf, audio and video**, `GAP-109`, and a CSV grid, `GAP-098`.
- **Routing a file by its type before an editor is chosen.** Every non-notebook file still goes to
  the file editor, which refuses what it cannot hold.
