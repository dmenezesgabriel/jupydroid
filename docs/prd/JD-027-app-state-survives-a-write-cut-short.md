# JD-027 — A setting, a recent or a Space's kernel survives a write cut short

Milestone: `M5` · State: in-progress · Depends on: JD-026 · Closes: GAP-171

## Outcome

A kill or a dead battery during a write of the app's own state leaves that file as it was before the
write or as the write left it. It is never cut in half, so a preference, a recent document, a Space's
configuration and a registered environment kernel are never silently lost.

## Demo

On the arm64 tablet (`SM-X216B`), because step 4 registers and runs an environment kernel:

1. Cold start: install, launch, wait for the Spaces home.
2. Change the theme in Settings. Through `run-as`, the store's inode has changed, the new value is
   in the file, and no `.~theme-settings.json` sits beside it.
3. Open a notebook, which writes the recents. The same three checks hold for `recent-documents.json`.
4. Enter a Space, create a venv in its terminal, and register it as a kernel from the Space's
   Settings. The same three checks hold for its `kernel.json`. A notebook on that kernel runs a cell.
5. Rename the Space from the home. The same three checks hold for its config file.
6. Force-stop and relaunch. The theme, the recent, the Space's new name and the registered kernel
   are all still there.

**Not shown on device: a kill landing mid-write.** Each of these files is a few hundred bytes, so the
write lasts microseconds and no driver can aim a kill at it. JD-026 could, because a 30 MB save
lasts long enough to hit. What stands in for it here is `AtomicFileWriteTest`'s failed write
leaving the file whole, each store's red test (a hard link taken before the write still reads the old
file), and the inode change on device.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | The client PUTs settings to `jupyterlab_server`, which the shipped rootfs does not carry |
| `vscode` | FULL | vscode@3f3380c6 `src/vs/platform/state/node/stateService.ts` | Its own `storage.json` goes through `writeFile` with `atomic: { postfix: '.vsctmp' }`: temp, sync, rename |
| `vscode-jupyter` | ABSENT | — | Writes no kernelspec of its own |
| `intellij-community` | PARTIAL | intellij-community@406a27a8 `platform/configuration-store-impl/src/DataWriter.kt` | `safeOutputStream` (backup `<name>~`, rewrite in place, fsync) when the requestor asks for a safe write |
| `termux-app` | FULL | termux-app@3df69d1d `termux-shared/src/main/java/com/termux/shared/settings/preferences/SharedPreferenceUtils.java` | Android `SharedPreferences`, whose platform write keeps a backup and restores it on read |
| `winlator` | ABSENT | winlator@fb66541b `app/app/src/main/java/com/winlator/container/Container.java` (`saveData`) | A container's config is a bare `FileWriter`, rewritten in place |
| the shipped rootfs | ABSENT | `usr/lib/python3.12/site-packages/jupyter_server/config_manager.py` (`set`), `usr/lib/python3.12/site-packages/jupyter_client/kernelspec.py` (`install_kernel_spec`) | Config: encoded up front, then `open(…, "w")` in place. A kernelspec: `shutil.copytree` |

**Count:** 3 of 7 survive a cut-short write of their own state, and only VS Code does it without a
recovering read. That is the shape ADR-0089 already chose for documents.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Write shape | vscode temp+rename; IntelliJ backup+rewrite; Android prefs backup+restore-on-read | Backup shapes need a recovering read on every load path | Temp+rename via `File.writeAtomically`, so there is one write path for documents and state (ADR-0089) |
| Temp name | vscode `.vsctmp` postfix; ours `.~<name>` | — | `.~<name>`, the name the documents already use. No listing scans `filesDir`, a Space's directory or a kernelspec's directory for files |

## Gap analysis

| Concern | Ours today (file) | Gap |
|---|---|---|
| Settings stores | `app/src/main/java/com/jupydroid/app/data/disk/Disk{Theme,Editor,Terminal,Explorer,Launcher}SettingsRepository.kt` `update` | `writeText` in place |
| Recents | `app/src/main/java/com/jupydroid/app/data/disk/DiskRecentDocumentsFile.kt` `write` | `writeText` in place |
| Space config | `app/src/main/java/com/jupydroid/app/data/disk/DiskSpaceRepository.kt` `writeInto` | `writeText` in place |
| Kernelspecs | `app/src/main/java/com/jupydroid/app/data/space/SpaceKernelSpec.kt` `install`, `installEnvironment` | `writeText` in place. A truncated environment spec loses that kernel |
| The atomic write | `app/src/main/java/com/jupydroid/app/data/disk/AtomicFileWrite.kt` | Complete. It takes bytes, so every text caller encodes first |

Present and partial: the mechanism ships, and these callers don't use it.

## Surface

| Layer | Files |
|---|---|
| `data/disk` | `AtomicFileWrite.kt` (a text overload), the five settings stores, `DiskRecentDocumentsFile.kt`, `DiskSpaceRepository.kt` |
| `data/space` | `SpaceKernelSpec.kt` |
| unit tests | `AtomicFileWriteTest`, each store's test, `DiskRecentDocumentsRepositoryTest`, `DiskSpaceRepositoryTest`, `SpaceKernelSpecTest` |
| `.maestro/` flow + `scripts/` driver | one per task, on SM-X216B |

No port, UI or `AppContainer` change: the capability is how existing adapters write.

## Tasks

- [x] T1 — The five settings stores and the recents write through `File.writeTextAtomically`. Red:
  each store's test takes a hard link before `update` and finds the old file there afterwards.
  Device: a theme change and a recent each get a new inode, leave no `.~` file, and survive a
  relaunch.
- [x] T2 — A Space's config and both kernelspec writes go through it. Red: `DiskSpaceRepositoryTest`
  and `SpaceKernelSpecTest`, the same way. Device: a rename and an environment registration each get
  a new inode, and that kernel runs a cell after a relaunch.
- [ ] T3 — The demo above, seeded, on SM-X216B. Then ship: ADR-0089's "Not covered" becomes covered,
  GAP-171 leaves `architecture.md` §11, the backlog row goes to `shipped`, and this PRD is deleted.

## Out of scope

- `SpacePipConfig.ensure` writes only when the file is missing. After that it is the user's file.
- The version stamps (`BaseSpaceInstaller.stampVersion`, `NotebookWorkspaceMigration.runOnce`) are
  a few bytes each, written once, and GAP-171 does not name them.
- A save that checks what it overwrites is GAP-065.
