# ADR-0101 — Give the tree's acts a port of their own, beside the one that reads it

Status: accepted
Date: 2026-10-15

## Context

`WorkspaceRepository` held two kinds of operation on the shared tree. Some read and write what is in
a file: `list`, `readText`, `readStampedText`, `writeText`, `readBytes`, `writeBytes`. The others act
on the tree itself: `createFile`, `createFolder`, `rename`, `overwrite`, `delete`. The `changes`
stream sat beside them, and only those acts announce on it.
[ADR-0070](0070-a-files-every-act-has-one-home.md) gave the acts that home precisely because the
announcement lives there.

A copy beside the source, jupyter_server's `copy` (`services/contents/manager.py` in the shipped
rootfs), would have been the port's twelfth function. That put the port, `DiskWorkspaceRepository`
and `FakeWorkspaceRepository` over detekt's `TooManyFunctions` limit of eleven
(`config/detekt/detekt.yml`). Elsewhere in this repo, that finding has been a real seam every time
(`app/src/main/java/com/jupydroid/app/ui/editor/CellActionItems.kt`,
`app/src/main/java/com/jupydroid/app/terminal/TerminalScreen.kt`).

The callers already split along the same line. The production callers of the acts are the explorer's
New/Rename/Delete (`ExplorerViewModel`), the launcher's new file (`LauncherViewModel`), the tab
strip's Rename/Delete (`MainAreaTabStrip`, `TabDelete`), and the three listeners that only hear
`changes` (`EditorViewModel`, `FileEditorViewModel`, `DeletedDocumentSessions`). Every other caller
only reads or writes contents. JupyterLab draws the same pair: its `Contents.IDrive` carries the acts
and the `fileChanged` signal they raise together (jupyterlab@7f0e690
`packages/services/src/contents/index.ts`).

## Decision drivers

- ADR-0070's rule stays whole: one home for every tree act, and the announcement in that same home.
- A port's surface tells a caller what it may do. A reader of contents should not be handed the
  power to delete.
- detekt's limit is honoured by moving responsibilities, not by raising the limit.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Split the acts and `changes` into `WorkspaceFiles` | Cohesive. Each caller takes the port it uses. The pressure is gone for good | Touches every caller of the acts and their tests |
| A one-operation port for the copy | Smallest diff | The copy is cut off from rename and delete, its siblings, which breaks ADR-0070's one home |
| Raise `TooManyFunctions` to 12 | One line | The next act raises it again. It treats the finding as noise |

## Decision

`WorkspaceFiles` owns `changes` and every act that creates, moves or removes a path in the tree:
`createFile`, `createFolder`, `rename`, `overwrite`, `delete`, and `copy` when it lands.
`WorkspaceRepository` keeps what reads and writes contents. The disk adapters are
`DiskWorkspaceFiles` and `DiskWorkspaceRepository`. They are two classes over one `WorkspaceRoot`
rule, so both address a path the same way. Their fakes are `FakeWorkspaceFiles` and
`FakeWorkspaceRepository`.

## Consequences

`EnteredSpacePorts` carries both ports, built over the same workspace directory. A screen that only
follows its file takes `WorkspaceFiles` for `changes`, and it defaults to the fake, as the watcher
does. A test that acts on a real disk wires `DiskWorkspaceFiles` beside `DiskWorkspaceRepository`.
Before the split, one object did both jobs.

What a future reader will want to "fix": merging the two back, because the same tree is behind both.
The tree is the same, but the question asked of it is not. A caller handed the repository can now
read a file without being able to delete it, and every act still announces in the one place a
document listens.
