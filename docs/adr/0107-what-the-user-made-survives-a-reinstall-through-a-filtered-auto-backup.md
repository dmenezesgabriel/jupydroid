# ADR-0107 — What the user made survives a reinstall through a filtered Auto Backup

Status: accepted
Date: 2026-10-19

## Context

Every Space lives in the app's private storage, `files/spaces/<id>/`, and so does everything made
inside one: the workspace a notebook, a file or a `.chat` is saved to
(`files/spaces/base/home/jupydroid`, `notebookWorkspaceIn` in
`app/src/main/java/com/jupydroid/app/data/space/NotebookWorkspace.kt`), and what a program the user
installed keeps under its home, such as opencode's console sign-in in
`~/.local/share/opencode/opencode.db`. Android deletes an app's private storage when it is
uninstalled (developer.android.com/training/data-storage). On SM-X216B an `adb uninstall` and a
fresh install left base extracted again from the APK, with an empty workspace and opencode gone.

Of seven references, none keeps an app-private store across a reinstall. The desktop four never have
to. JupyterLab (jupyterlab@7f0e6906 `jupyterlab/labapp.py`), VS Code
(vscode@3f3380c6 `src/vs/platform/environment/node/userDataPath.ts`), vscode-jupyter and IntelliJ
(intellij-community@406a27a8 `plugins/settings-sync/`) keep documents in a folder the user owns.
VS Code's Settings Sync (`src/vs/platform/userDataSync/common/userDataSync.ts`,
`ALL_SYNC_RESOURCES`) and IntelliJ's settings-sync plugin carry settings to a cloud copy, and no
document. The two Android references lose everything. termux switches backup off and offers
`~/storage` links under all-files access
(termux-app@3df69d1d `app/src/main/AndroidManifest.xml`, `TermuxInstaller.java`), and winlator turns
a SAF-picked folder back into a path (winlator@fb66541b `ContainerDetailFragment.java`). Both need
legacy storage access at targetSdk 28. Neither is open to an app under Play's rules
([ADR-0002](0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md)).

The platform's own mechanism for app data is Auto Backup
(developer.android.com/identity/data/autobackup). It covers `getFilesDir()` by default and is capped
at 25 MB per app. Over the cap it "doesn't back up data to the cloud" at all. It runs when idle, on
Wi-Fi, at most every 24 h, and is end-to-end encrypted on Android 9+. A restore happens when the app
is installed again, `adb install` included, before its first launch. This app's `filesDir` measured
950 MB with base and a second Space, and base's rootfs alone is far past the cap. Before this
decision, `android:allowBackup="true"` was on with the template's empty rules, so every backup was
over quota and nothing was carried.

## Decision drivers

- Nothing the user wrote, no sign-in a program wrote, is lost to an uninstall.
- No permission Play gates, and no document leaves the path the runtime binds.
- What one install or one `pip install` rebuilds is not stored twice.
- A sign-in goes only where nobody but the user can read it.

## Options considered

| Question | Options | Chosen |
|---|---|---|
| Where documents survive | a folder in shared storage (all-files access, Play-gated); a SAF tree (no POSIX path for jupyter_server or proot); Auto Backup of `filesDir` | **Auto Backup.** It keeps the path and needs no permission; its cap is the cost |
| What is backed up | static `<include>`/`<exclude>` rules; an agent that walks the tree | **An agent.** A static rule cannot exclude a venv at a path the user chose, and one numpy venv passes 25 MB, which would carry nothing at all |
| An uninstall before a backup ran, no Google account, over quota | nothing; `android:hasFragileUserData` | **Keep data.** One attribute, the user ticks a box, and all of `filesDir` stays, rootfs included |
| A program installed into the rootfs | back up `/usr/local`; install it again | **Install it again.** opencode is 195 MB in `/usr/local/bin` ([ADR-0106](0106-the-chat-panel-answers-from-an-acp-agent-the-user-installs-in-the-space.md)), past the cap alone |

## Decision

**Auto Backup carries each Space's home, its `config.json` and the app's own state files, filtered
by `SpaceHomeBackupAgent`, only to a transport that encrypts them, and the system uninstall offers to
keep everything else.**

- `SpaceHomeBackupScope` (`app/src/main/java/com/jupydroid/app/data/backup/SpaceHomeBackupScope.kt`)
  is the filter, a pure function of `filesDir`. It returns each Space's home minus `~/.cache` and
  any directory holding `pyvenv.cfg`, each Space's `config.json`, and the app's settings and recents
  files. The walk enters only the home, so the rootfs, `pkgs`, `run`, `tmp` and
  `.jupydroid/rootfs-version` are out by construction, and a symlink is neither returned nor
  followed. Measured: 13 files and 31,190 B for base's three documents and a second Space's home;
  19 files and 574,269 B once opencode is installed and signed in, most of it `opencode.db`.
- `SpaceHomeBackupAgent` (`SpaceHomeBackupAgent.kt`), declared with `android:backupAgent` and
  `android:fullBackupOnly="true"`, feeds the scope to `fullBackupFile`. There is no rules file. The
  overridden `onFullBackup` never reads one, and on restore an `<include>` would drop files.
  `dataExtractionRules` is what API 31+ reads, and minSdk is 32, so the template's
  `backup_rules.xml` and `data_extraction_rules.xml` were deleted.
- The agent writes nothing to a cloud transport without client-side encryption, which is what
  `disableIfNoEncryptionCapabilities` does for an app with no agent (`mayCarrySignIns`). A
  device-to-device transfer never reaches a server, so it is allowed.
- The agent's `onRestoreFile` converts the date. The platform's tar reader hands it the header's
  mtime in seconds (platform/frameworks/base@6e47c707
  `services/backup/java/com/android/server/backup/utils/TarBackupReader.java`), and the default
  passes that to `File.setLastModified`, which takes milliseconds
  (`core/java/android/app/backup/FullBackup.java`, `restoreFile`). Unconverted, every restored file
  is dated 1970-01-21, and the explorer showed "21 Jan". `restoredModifiedMillis` converts it, and a
  restored `notes.md` reads the second it was backed up at.
- A restore brings back a Space's config and home but never its rootfs. base is extracted again from
  the APK by `BaseSpaceInstaller`, around the restored home and without touching it. A Space other
  than base is extracted from the bundled image when it next runs
  (`SpaceImageInstaller.extractIfAbsent`), 15.6 s on SM-X216B, once. A Space that already has a
  rootfs is still never extracted again
  ([ADR-0073](0073-a-space-owns-its-rootfs-and-runs-under-proot.md)).
- `android:hasFragileUserData="true"` is the second layer. One UI shows the keep-data box from
  Settings → Apps → Uninstall and from the launcher's `ACTION_DELETE` alike, and keeping it kept
  all of `filesDir` (679 MB, then 782 MB). opencode's binary answered `--version` after the
  reinstall with nothing installed again.

Measured on SM-X216B from a fresh install: three documents, Recent, the Light theme and opencode's
console sign-in were backed up (`bmgr backupnow` Success). `adb uninstall` removed the app's data,
and `adb install` restored the same 19 files before the first launch: `filesDir` was 672,302 KiB
before and 686 KiB after. Base
opened with all of them, and its notebook's cell answered from the kernel. Once opencode was
installed again, its console found the restored account with no new login.

## Consequences

- A program installed into the rootfs (`/usr/local`, `pkgs/`) is not carried. Its sign-in and
  settings under the home come back, and the binary is installed again, or survives through keep
  data. A venv and `~/.cache` are rebuilt by the user's next `pip install` or run.
- The cap is the limit. A home whose documents pass 25 MB carries nothing to the cloud, documents
  included, because that is how Auto Backup treats an app over quota. The scope keeps the
  rebuildable bulk out, and keep data still covers that case.
- A backup needs backup on, a Google account and a lock screen. Without encryption the agent writes
  nothing, which is deliberate: the home holds sign-ins.
- A force-stopped package is skipped with "Backup is not allowed" (`appIsStopped`,
  platform/frameworks/base@6e47c707
  `services/backup/java/com/android/server/backup/fullbackup/PerformFullTransportBackupTask.java`),
  until the user next launches the app.
- `adb uninstall` without `-k` still wipes, and an `adb install` after it restores the last backup.
  A test that needs a fresh install switches restore off around it (`bmgr autorestore false`).
  Otherwise it starts from an earlier run's home.
- Not covered: a workspace in a folder the user owns (SAF, or a path in shared storage), which needs
  its own path decision under ADR-0002, and restoring onto a different device. A device-to-device
  transfer goes through the same agent but was not measured.
