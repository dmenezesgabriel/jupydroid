# ADR-0089 — A document save writes beside the file and renames over it

Status: accepted
Date: 2026-10-11

## Context

A notebook save was a bare `File.writeText`. That truncates the file where it sits and then rewrites
it, so a process killed or a battery dead mid-write leaves a notebook that opens as invalid JSON. On
the tablet, a poller watching a ~31 MiB notebook during its autosave saw it at 12.7, 21.5 and 3.0 MB
on the way to its full size. A save is a window of a hundred milliseconds or more in which the only
copy of the user's work is half written.

What the references do:

- **jupyter_server**, in the shipped rootfs, copies the file to `.~<name>`, writes the target in
  place, and fsyncs it. If the write raises, it puts the copy back. A killed process raises nothing,
  so the recovery happens on the next *read*: an unreadable notebook beside a `.~` file is moved to
  `<name>.invalid`, and the copy is restored
  (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py`, `atomic_writing`,
  `_read_notebook`).
- **IntelliJ** has the same shape, a `<name>~` backup, an in-place write and an `fsync`. It has no
  read that recovers (intellij-community@406a27a8
  `platform/util/src/com/intellij/util/io/SafeFileOutputStream.java`).
- **VS Code** writes a temp sibling, `fdatasync`s it on close, and renames it over the target. It
  writes through a symlink directly, because a rename across filesystems is not atomic
  (vscode@3f3380c6 `src/vs/platform/files/node/diskFileSystemProvider.ts`, `doWriteFileAtomic`,
  `canWriteFileAtomic`; microsoft/vscode#9589).

## Decision drivers

- A kill is the failure to survive. No code runs after one, so anything that repairs it has to run
  on a later read.
- One write path for every document.
- A temp file must not show in the explorer or the search.

## Options considered

1. **jupyter_server's backup and in-place write, with its recovering read.** It survives a kill, but
   only through a second mechanism on every read path, and it leaves a truncated file on disk until
   that read happens.
2. **VS Code's temp-and-rename.** The target is the old file until the rename and the new one after,
   so no read has anything to recover.

## Decision

Option 2.

- `File.writeAtomically` (`app/src/main/java/com/jupydroid/app/data/disk/AtomicFileWrite.kt`) writes
  `.~<name>` beside the file, syncs it, and moves it over the target with `ATOMIC_MOVE`. If the write
  fails, it deletes the temp and leaves the target as it was.
- The temp is named `.~<name>`, jupyter_server's own name. The explorer and the search skip dotfiles
  already (`WorkspaceListing.kt`).
- A symlinked target is resolved one level, as jupyter_server does. The link stays and the file it
  names is replaced.
- Every document save goes through it: a notebook (`DiskNotebookRepository`), and a workspace file
  written by the file editor, replace-all or an export (`DiskWorkspaceRepository`).

## Consequences

A save cut short leaves the document as it was before the save. At most it also leaves a `.~` file
beside it, which the next save of that document overwrites.

A save now replaces the file's inode. A hard link to a notebook keeps the old version after a save,
as it does under VS Code. A program holding the file open keeps reading the old one.

What a future reader will be tempted to "fix":

- **Dropping the sync before the rename.** Without it, a power cut can leave the rename on disk
  ahead of the data, and the file comes back empty. All three references sync.
- **Adding jupyter_server's `.invalid` recovery.** This write never leaves a partial target, so that
  read would have nothing to recover.

Not covered:

- The app's own state files (settings, recents, a Space's config, kernelspecs) — `GAP-171`.
