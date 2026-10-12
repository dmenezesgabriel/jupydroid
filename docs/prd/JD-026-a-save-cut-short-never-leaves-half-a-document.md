# JD-026 — A save cut short never leaves half a document

Milestone: `M5` · State: in-progress · Depends on: — · Closes: GAP-023

## Outcome

A notebook or a workspace file whose save is cut short — the app killed, the battery dead — opens
afterwards as either what it was before the save or what the save wrote, never as half of it.

## Demo

On the arm64 tablet (`SM-X216B`). No step runs a cell, but the notebook is the tablet's real
workspace and the kill lands on the real app process.

1. Cold start: seed `Large Save.ipynb`, a notebook whose stored outputs make it several MiB, so a
   save takes long enough to be caught mid-write, and open it.
2. Edit a markdown cell. While the autosave writes, a driver polling the workspace kills the app
   (`kill -9`) the moment the write is under way.
3. Relaunch and open the notebook: it opens with its cells, holding the text before the edit or
   after it.
4. Repeat 2–3 against a ~30 MiB `notes.txt` beside it, written by Replace All from the search
   panel. The file editor cannot hold a file whose save takes long enough to be caught: on the
   tablet a 4 MB file is not responding on open, and a 2 MB one draws nothing in a minute. Both
   write through `WorkspaceRepository.writeText`.
5. A clean save, of the notebook and of an executable `notes.txt` in the file editor, leaves no `.~`
   file beside either. The file is replaced (its inode changes) rather than rewritten in place, and
   it keeps its mode.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` committed under `evidence/<flow-name>/`. Unit tests alone never close it.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | Saves through the server's contents API; the server writes the file |
| `vscode` | FULL | `vscode@3f3380c6 src/vs/platform/files/node/diskFileSystemProvider.ts` (`writeFile`, `canWriteFileAtomic`, `doWriteFileAtomic`) | Writes a temp sibling `<name><postfix>` and `fdatasync`s it on close, renames it over the target, deletes the temp if the rename fails; no atomic write through a symlink |
| `vscode-jupyter` | ABSENT | — | A notebook is saved by vscode's own file service |
| `intellij-community` | FULL | `intellij-community@406a27a8 platform/util/src/com/intellij/util/io/SafeFileOutputStream.java` | Copies the target to `<name>~`, overwrites the target in place, fsyncs, deletes the backup; restores it if the write throws |
| `termux-app` | PARTIAL | `termux-app@3df69d1 app/src/main/java/com/termux/app/TermuxInstaller.java` | A staging prefix directory renamed into place once complete — the same idea for a bootstrap, not for a save |
| `winlator` | ABSENT | — | No document is saved |
| the shipped rootfs | FULL | `usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py` (`atomic_writing`, `_read_notebook`) | IntelliJ's shape — `.~<name>` backup, write in place, fsync — plus a read that moves an unreadable notebook to `<name>.invalid` and restores the backup |

**Count:** 3 of 7 save a document atomically, and every one of the three that saves documents
does. Not having it is the outlier.

## Implementation inventory

### the shipped rootfs (jupyter_server)

| Concern | Files |
|---|---|
| Adapter | `fileio.py` `atomic_writing`: `path_to_intermediate` is `.~<name>` ("The .~ prefix will make Dropbox ignore the temporary file"); a symlinked target is resolved first |
| Persisted state | `fileio.py` `_read_notebook`: an unreadable notebook beside a `.~` file is moved to `.invalid` and the backup put back |
| Edge cases it handles | A directory it cannot write falls back to a direct write; `use_atomic_writing` turns the whole thing off |

### vscode@3f3380c6

| Concern | Files |
|---|---|
| Adapter | `src/vs/platform/files/node/diskFileSystemProvider.ts` `doWriteFileAtomic`: write temp, `rename` over the target, delete the temp on a failed rename |
| Edge cases it handles | `canWriteFileAtomic`: a symlink target is written directly, since a rename across filesystems is not atomic |

### intellij-community@406a27a8

| Concern | Files |
|---|---|
| Adapter | `platform/util/src/com/intellij/util/io/SafeFileOutputStream.java`: backup copy, in-place write, `fsync`, `restoreFromBackup` on an `IOException` |
| Unit tests | `platform/util/testSrc/com/intellij/util/io/SafeFileOutputStreamTest.java` |

**The disagreement.** jupyter_server and IntelliJ write the target in place behind a backup;
vscode writes beside it and renames. A killed process raises nothing, so the backup shape leaves
a truncated target and a whole backup, and only a *read* that knows to look for the backup
recovers it — jupyter_server has that read, IntelliJ leaves it to the user. The rename shape never
leaves the target partial: it is the old file until the rename, the new one after.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Shape | jupyter_server, IntelliJ → backup + in-place write; vscode → temp + rename | The backup shape needs read-side recovery for a kill; the rename shape replaces the inode, so a hard link keeps the old content | Temp + rename: a kill is the failure GAP-023 names, and only the rename survives it without a recovery read (ADR-0089) |
| Durability | jupyter_server, IntelliJ → `fsync` before finishing; vscode → `fdatasync` on closing a write handle (microsoft/vscode#9589) | One sync per save | Sync the temp before the rename, as all three do, or a power cut can rename an empty file into place |
| Temp name | jupyter_server → `.~<name>`; IntelliJ → `<name>~`; vscode → `<name><postfix>` | A non-dot name shows in the explorer and the search | `.~<name>`: the explorer and the search skip dotfiles already (`WorkspaceListing.kt`) |
| Symlinked target | jupyter_server → resolve it; vscode → write directly | Rename onto the link replaces the link | Resolve the link and write beside its target, as jupyter_server does |
| Mode | jupyter_server, IntelliJ → kept, the inode is the same; vscode → the temp's default | A new inode takes the app's 0600 | Carry the target's permissions onto the temp, so a script made executable in the terminal stays executable (ADR-0089) |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Notebook save | `app/src/main/java/com/jupydroid/app/data/disk/DiskNotebookRepository.kt` (`writeFile`): `File.writeText` | `vscode@3f3380c6 src/vs/platform/files/node/diskFileSystemProvider.ts` | Absent |
| Workspace file save | `app/src/main/java/com/jupydroid/app/data/disk/DiskWorkspaceRepository.kt` (`writeText`, `writeBytes`) | same | Absent |
| A whole-directory publish | `app/src/main/java/com/jupydroid/app/data/disk/DiskSpaceRepository.kt` (staging + `renameTo`) | `termux-app@3df69d1 app/src/main/java/com/termux/app/TermuxInstaller.java` | Complete; the precedent for this shape here |

## Deviations

No read-side `.invalid` recovery: a rename leaves no partial target to recover from.
`use_atomic_writing` has no counterpart — there is no case for turning it off.

## Open decisions

None open: the shape is ADR-0089.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — (the ports' contracts do not change) |
| `data/disk` | new `AtomicFileWrite.kt`; `DiskNotebookRepository.kt`; `DiskWorkspaceRepository.kt` |
| `data/fake` | — (fakes hold memory, not files) |
| `ui/<screen>` | — |
| `di/AppContainer` | — |
| unit tests | new `AtomicFileWriteTest`; `DiskNotebookRepositoryTest`; `DiskWorkspaceRepositoryTest` |
| instrumented tests | — (nothing new to press) |
| `.maestro/` flow + `scripts/` driver | `jd-026-atomic-save-*`; `scripts/jd-026-atomic-save-kill.sh` shared by the drivers |

## Tasks

- [x] T1 — A notebook save replaces its file whole: `AtomicFileWrite.kt`, `DiskNotebookRepository`
      through it, ADR-0089; a kill mid-save on the tablet, measured before and after.
- [x] T2 — A workspace file save replaces its file whole and keeps its mode:
      `DiskWorkspaceRepository.writeText` and `writeBytes` through it (the file editor, replace-all,
      export); the kill on Replace All, the clean save in the file editor.
- [ ] T3 — The demo, run on the tablet.

## Out of scope

The app's own state files — the five `Disk*SettingsRepository` stores, recents, a Space's config
and its kernelspecs — are written the same un-atomic way and recorded as `GAP-171`. They are not a
user's documents: an unreadable one costs a preference or a kernel registration, never work.
