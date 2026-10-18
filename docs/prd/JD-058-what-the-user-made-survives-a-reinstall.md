# JD-058 — What the user made survives a reinstall

Milestone: `M5` · State: in-progress · Depends on: — · Closes: GAP-176

## Outcome

After the app is uninstalled and installed again, base's workspace (its notebooks, files and `.chat`s),
the sign-ins and settings a program wrote under its home, and the app's own settings and recents are
there when the Space is entered.

## Demo

On SM-X216B, because step 6 runs a cell, and on x86_64 `MainActivity` falls back to
`FakeKernelGateway`.

1. Fresh install (`adb uninstall com.jupydroid.app`, then install the debug APK) and launch. Enter base.
2. Create `survives.ipynb` with one cell, `notes.md` and `talk.chat`. Install opencode in base's
   terminal and run `opencode auth login`. Pick a non-default theme.
3. Force a backup: `adb shell bmgr backupnow com.jupydroid.app` reports `Success`.
4. `adb uninstall com.jupydroid.app` (data gone), then install the debug APK again. Restore runs between
   install and first launch.
5. Launch and enter base. The three documents are listed in the explorer and open with their content.
   The theme and the recents are what step 2 left. `~/.local/share/opencode/auth.json` is present in the
   terminal.
6. Run `survives.ipynb`'s cell: it answers from the kernel, so the rootfs re-extracted around the
   restored home.
7. The second layer: uninstall from the system's Settings → Apps → Uninstall and tick **Keep app data**.
   Install again and launch: everything from step 2 is there, opencode's binary too, with no backup
   involved.

The item is not `shipped` until this has been run on SM-X216B, with screenshots, `maestro.log` and the
`bmgr`/`adb` transcript committed under `evidence/<flow-name>/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | `jupyterlab@7f0e6906 jupyterlab/labapp.py` (`get_user_settings_dir`, `get_workspaces_dir`) | Documents are the user's own folder (`root_dir`); app state lives in separate per-user dirs. Nothing is lost on reinstall because nothing was inside the app |
| `vscode` | PARTIAL | `vscode@3f3380c6 src/vs/platform/environment/node/userDataPath.ts`, `src/vs/platform/userDataSync/common/userDataSync.ts` (`ALL_SYNC_RESOURCES`) | Documents are the opened folder, outside the user-data dir. Settings Sync carries settings, keybindings, snippets, prompts, tasks, extensions, global state, profiles and MCP to the cloud, and no document |
| `vscode-jupyter` | ABSENT | — | Inherits VS Code's split; adds nothing |
| `intellij-community` | PARTIAL | `intellij-community@406a27a8 plugins/settings-sync/` | Same split: projects are user folders; the settings-sync plugin carries IDE settings, not projects |
| `termux-app` | ABSENT | `termux-app@3df69d1d app/src/main/AndroidManifest.xml` (`allowBackup="false"`, `MANAGE_EXTERNAL_STORAGE`), `app/src/main/java/com/termux/app/TermuxInstaller.java` (`setupStorageSymlinks`) | Everything is private and lost on uninstall; cloud backup is switched off. It offers `~/storage/*` symlinks into shared storage under all-files access, off Play, at targetSdk 28 |
| `winlator` | ABSENT | `winlator@fb66541b app/app/src/main/java/com/winlator/ContainerDetailFragment.java` (`ACTION_OPEN_DOCUMENT_TREE`), `core/FileUtils.java` | Containers are private and lost on uninstall. A drive can point at a SAF-picked folder, turned back into a filesystem path, which needs legacy storage access at targetSdk 28 |
| the shipped rootfs | ABSENT | `home/`, `home/jupydroid/` are bare directory entries (`app/src/main/java/com/jupydroid/app/data/space/NotebookWorkspace.kt`) | Nothing of the user's is in the image; it is re-extracted around the home |

**Count:** 0 of 7 preserve an *app-private* store across a reinstall. The desktop four never have to:
documents are the user's own folder, and only settings go to a cloud copy (vscode and intellij). The
two Android references lose everything. So "documents in a folder the user owns, settings in a platform
backup" is the shape every desktop reference takes. On Android under Play rules (ADR-0002), the
platform's mechanisms for that shape are SAF for a folder and Auto Backup for app data.

## Implementation inventory

### Android platform (authoritative for the mechanism; no reference implements it)

| Concern | Source |
|---|---|
| What an uninstall removes | developer.android.com/training/data-storage: app-specific files, prefs and DBs are removed; shared-storage documents (SAF) and media are not |
| Auto Backup scope and limits | developer.android.com/guide/topics/data/autobackup: `getFilesDir()` is included by default; 25 MB per app, and over quota "doesn't back up data to the cloud" at all; runs when idle, on Wi-Fi, at most every 24 h, with backup on; E2E-encrypted on Android 9+; restore "whenever the app is installed … or by running `adb install`", before first launch |
| Filtering what is backed up | `BackupAgent.onFullBackup(FullBackupDataOutput)` + `fullBackupFile()`, with `android:backupAgent` and `android:fullBackupOnly="true"`; `android:dataExtractionRules` is what API 31+ reads (minSdk is 32, so `backup_rules.xml`/`fullBackupContent` is never read) |
| Keep data on uninstall | `android:hasFragileUserData`: "Whether to show the user a prompt to keep the app's data when the user uninstalls the app. The default value is `false`" (developer.android.com/guide/topics/manifest/application-element) |
| Shared storage with a path | Google Play policy answer/10467955: `MANAGE_EXTERNAL_STORAGE` only for file management, backup, anti-virus, document management, search, encryption and migration, review-gated; SAF is the recommended alternative |

## Trade-offs and what we adopt

| Axis | Options seen (source → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where documents survive | termux → all-files access + shared storage; winlator → SAF tree turned into a path; desktop references → a user folder; Android → Auto Backup of `filesDir` | A path in shared storage needs all-files access (Play-gated, ADR-0002) and has no exec or symlinks, and `DiskSpaceEnvironments.ENV_ROOTS` puts venvs at the workspace root. SAF gives no POSIX path, and jupyter_server and proot need one. Auto Backup keeps the path and needs no permission, but caps at 25 MB | **Auto Backup**, filtered by a `FullBackupAgent` subclass: the platform's standard for app data, Play-safe, restored on `adb install`. A folder the user owns is a separate item (Out of scope) |
| What is backed up | static `<include>`/`<exclude>` rules; a `BackupAgent` that walks the tree | Static rules can't exclude a venv whose path the user chose, and a numpy venv alone passes 25 MB. Over quota *nothing* is backed up, documents and sign-ins included | **The agent**: each Space's home minus what can be rebuilt (any directory holding `pyvenv.cfg`, `~/.cache`), each Space's `config.json`, and the app's own state files. Never the rootfs, `pkgs/`, `run/`, `tmp/` or `.jupydroid/rootfs-version` |
| An uninstall before a backup ran, no Google account, or over quota | `hasFragileUserData`; nothing | One attribute; the user has to tick the box. It keeps all of `filesDir`, rootfs and opencode's binary included. `adb uninstall` without `-k` still wipes | **Add it** as the second layer: it covers exactly the cases backup can't |
| A program the user installed into the rootfs | back up `/usr/local`; re-install | opencode is 195 MB in `/usr/local/bin` (ADR-0106), over the quota alone | **Not backed up.** Its sign-in under the home comes back; the binary is installed again, or survives through keep-data |

ADR-0107 records the backup scope and the keep-data layer when the item ships.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Backup on | `android:allowBackup="true"` (`app/src/main/AndroidManifest.xml`) | Android Auto Backup | present but unmeasured: with no rules, the default would back up all of `filesDir`, rootfs included. That is far over 25 MB, so nothing is backed up |
| Backup rules | `app/src/main/res/xml/data_extraction_rules.xml`, `backup_rules.xml`: the template's empty comments | — | absent |
| Keep-data prompt | — | `hasFragileUserData` | absent |
| A restore landing under a re-extraction | `TarZstdExtractor` never wipes its destination (`NotebookWorkspace.kt` KDoc); `BaseSpaceInstaller` | — | present, unmeasured against a restored home |

Ours is **present but unreachable**: backup is switched on, but nothing reaches a restore.

## Deviations

- The rootfs and what was installed into it (`pkgs/`, `/usr/local`) are not backed up. A Space is
  re-extracted from the APK either way (GAP-176), and they are what blows the quota.
- No document goes outside private storage. A folder the user owns needs a path the runtime can bind,
  which under ADR-0002 is a separate decision.

## Open decisions

Each one is settled by a measurement in the task that needs it:

- One UI on SM-X216B shows the keep-data prompt, from Settings and from the launcher's
  `ACTION_DELETE` alike, and keeping it keeps all of `filesDir` (679 MB, rootfs and opencode included). (T1)
- What does base's home measure (`du`) after the agent's filter, against 25 MB? (T3)
- Does `BaseSpaceInstaller` re-extract over a restored `home/` without touching it, given that the
  restore brings back no `.jupydroid/rootfs-version`? (T4)
- A Space other than base: does a restored `config.json` plus its home bring it back, or does its rootfs
  need re-creating first? (T3: measure, then either cover it or name it in Out of scope)

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — (no port: the platform calls the agent, and nothing in the app asks it anything) |
| `data/backup` | `SpaceHomeBackupScope.kt` (pure: given a `filesDir`, the files to back up), `SpaceHomeBackupAgent.kt` (`FullBackupAgent`, feeds the scope to `fullBackupFile`) |
| `di/AppContainer` | — (the platform constructs a `BackupAgent` by its class name) |
| manifest / res | `android:backupAgent`, `android:fullBackupOnly`, `android:hasFragileUserData`; `data_extraction_rules.xml` |
| unit tests | `SpaceHomeBackupScopeTest` (plain JVM over a temp `filesDir`) |
| instrumented tests | — (nothing visible to press) |
| `.maestro/` flow + `scripts/` driver | a reinstall flow: create, `bmgr backupnow`, uninstall, install, check, run a cell |

## Tasks

- [x] T1: `hasFragileUserData="true"`. Measure on SM-X216B that the system uninstall offers Keep app
  data, and that a reinstall finds the workspace and opencode's binary. Evidence: screenshots of the
  dialog and of the explorer afterwards.
- [ ] T2: `SpaceHomeBackupScope`, red test first. It returns each Space's home minus `pyvenv.cfg`
  directories and `.cache`, each Space's `config.json`, and the app's own state files. Never the
  rootfs, `pkgs`, `run`, `tmp`, the rootfs stamp or a symlink.
- [ ] T3: `SpaceHomeBackupAgent` wired through the manifest and `data_extraction_rules.xml`. Measure
  `bmgr backupnow` on SM-X216B and the size of the backup.
- [ ] T4: the demo (steps 1–6) on SM-X216B with its flow and driver, plus T1's step 7 again. Write
  ADR-0107, then delete this PRD.

## Out of scope

- A workspace in a folder the user owns (SAF, or a path in shared storage): its own `JD`/`GAP`, since
  it needs a path decision under ADR-0002.
- Backing up a Space's rootfs, `pkgs/`, or a program installed into `/usr/local`.
- Restoring onto a different device (device-to-device transfer goes through the same agent, but it
  isn't measured here).
