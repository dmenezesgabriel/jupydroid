# AUD-010 — Spaces and runtime

Milestone: `M0` · State: in-progress · Depends on: `AUD-009` · Closes: —

> An **audit** item, so this is not `_TEMPLATE.md` filled in. There is no
> Outcome, Demo, or Maestro section: an `AUD-NNN` ships a measurement, not a
> capability (ADR-0067), and the audit replaces the device flow (§6). What it
> does produce: the reference's model for the area, ours, the diff with
> rationale or its absence, and a soundness read of every code path the area
> owns — growth, failure, the second call.
> What is examined: **the Space — the directory a runtime lives in, and
> everything that brings it up**.
> Reference: **`winlator` @ `fb66541b93a4eb3ee585a433b4c7b20544d58e40`**.

## What is examined

One area — **the Space: the directory a runtime lives in, and everything that
brings it up** — taken through four questions at once, per AGENTS.md:

1. What does the reference offer for it — the whole lifecycle of an installed
   environment?
2. What does Jupydroid offer today?
3. Where do they differ, and is the difference written down? The ledger's
   verdicts (ADR-0067) apply as written.
4. Is the code sound — growth (a second Space, a second start), branching
   (every reference path a Jupydroid path is taken over), failure (the
   extraction dies half-written, the process dies mid-launch, the directory
   is missing), coupling (who else reads the stamp, owns the server, touches
   the Space dir)?

The reference is `winlator` for what it offers: it is the app's whole model —
a user browses a list of installed *containers*, each a directory under
`files/containers/<id>` with a `.container` config inside, sitting on a
*rootfs* the app extracted once into `files/rootfs`; containers are created,
edited, duplicated, removed, browsed, run — the run marks one active for
`XrActivity`; the rootfs carries a `rootfsVersion.txt` stamp the installer
compares against the app's pinned `LATEST_VERSION`. Its authority here is
the same shape the app's own ADRs already cite: ADR-0007
(`rootfsVersion.txt`), ADR-0010 (a Space is a directory; its id comes from
the scan), ADR-0011 (site-packages inside the Space dir), and three KDocs
that name winlator paths (`SpaceConfig`'s `container_pattern.tzst`,
`DiskSpaceRepository`'s `ContainerManager` scan, `BaseSpaceInstaller`'s
`RootFSInstaller`).

The area at five scales:

- **The image.** What makes a rootfs a rootfs: its source
  (`tools/rootfs/build.sh` → the shipped `rootfs-arm64-v8a.tzst` asset), its
  format (zstd-over-tar, ADR-0012), and its contents (musl Python, pip,
  jupyter_server, terminado, nbconvert, jupyterlab-server; ipykernel;
  `jupydroid_kernel.py`; the `python`/`jupyter`/`pip` busybox-style applets;
  `etc/resolv.conf` written at install).
  Reference: `installable_components/` (rootfs, container_pattern, box64,
  dxvk, turnip, vkd3d, wined3d), `RootFS` (`isValid`, `getVersion`,
  `isInstalled`), `RootFSInstaller` (`installIfNeeded`).
- **The model and its lifecycle acts.** A Space's identity
  (id-from-directory-name), its config (name, pythonVersion, createdAt),
  its acts (list/create/rename/delete; duplicate? activate? browse?), its
  version machinery (`rootfs-version` stamp vs `rootfsVersion.txt` +
  `pkg_version`), its schema (`spaceWithId` defaults vs `.container`
  `data.put`/`get` fallbacks + `WineUtils.updateWineprefix` migration).
  Reference: `Container`, `ContainerManager`, `Shortcut`,
  `WineUtils.saveToFile`/`updateWineprefix`.
- **The surfaces.** Where the lifecycle is shown: the Space is invisible in
  the UI today — no list, no detail, no menu — while winlator's
  `ContainersFragment` (a Run button per row; a per-item popup of File
  Manager, Edit, Duplicate, Remove, Storage Info) is the app's home screen,
  and `ContainerDetailFragment` is its create/edit form.
  Reference: `ContainersFragment`, `ContainerDetailFragment`,
  `ContainerFileManagerFragment`, `ShortcutsFragment`, `SettingsFragment`,
  `main_menu.xml`, `container_popup_menu.xml`, `containers_menu.xml`.
- **The runtime.** What `start()` means: `SpaceRuntime`'s ProcessBuilder +
  env, `JupyterServerLauncher` (ephemeral port, runtime-file read, token,
  UDS kernels), `BaseSpaceServer` (lazy, mutex, generation, one shared
  server), `IdleServerStopper` (inactivity stop), `SpaceBackedPorts` (what
  the server gives the ports), `AndroidBaseSpace` (ABI gate → real or
  fakes), `BaseSpaceInstaller` (extract-once, relink-always, kernel.json,
  resolv.conf).
  Reference: `XEnvironment` (`EnvironmentComponent` start/stop, tmpdir
  cleanup), `GuestProgramLauncherComponent` (proot + envvars), the run flow
  in `XServerDisplayActivity`/`XrActivity`.
- **The exec model and the sandbox.** Where ours must differ absolutely:
  `targetSdkVersion 28` on both reference apps vs `targetSdk 36` here, so
  neither winlator's `processBuilder` on `files/rootfs` nor Termux's
  bootstrap on `files/usr` can exec at all on this app's SDK — ours is the
  `jniLibs` + launch-time-relink model ADR-0002/0003 record. And the
  multi-Space question: winlator's containers share one rootfs read-only
  over a per-container `drive_c`; the app's `files/spaces/<id>` model
  already carries a per-Space rootfs the moment a second Space exists —
  while `BaseSpaceServer` is one server for the app. That difference is the
  audit's spine.

**Carried in.** The *contents* of the Space are other slices': the workspace
and its disk (`JD-003`, `JD-007`, `GAP-010`), the kernel the server spawns
and the `KERNEL_ID` spec (`AUD-002`, `AUD-006`, `GAP-007`), the shell it
hands terminado (`GAP-003`), the packages inside (`JD-004`/`JD-005`), the
ABI split (`ADR-0003` + `SpaceAbi`), the relink (`ADR-0002` + `CLOSED-001`),
the idle stop (`ADR-0024`), the token/loopback (`ADR-0025`/`0026`). The
served contents root (`NotebookWorkspace`, `files/workspace`) is the
filesystem slice's; the kernelspec *write* at install (`SpaceKernelSpec`)
is this slice's — it ships the image.

**Neighbours, not this slice's.** The Jupyter server's request surface —
kernelspecs, contents, sessions, terminals, kernels — is `AUD-001`/`006`/`007`/
`008`'s wire, served by `SpaceBackedPorts`; this slice stops at the launcher,
the env, and the install. The disk settings/files ports are `AUD-008`'s.

## The surface, produced

Winlator's lifecycle is Java; the command partition instrument is typed for
JupyterLab's `x:y` ids and claims none of this area (AUD-007's Termux
precedent: prose + reproduction greps, no new `read_*` script where no count
is asserted). The surface is enumerated by greps, and every list it produces
comes from a reproducible command:

```sh
# the lifecycle verbs the reference ships
grep -n 'public.*(' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/container/ContainerManager.java
# the config keys a container persists
grep -o 'data\.put("[a-zA-Z_0-9]*"' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/ContainerDetailFragment.java | sort -u
# the per-container menu and the list's menu
cat reference/research_repos/winlator/app/app/src/main/res/menu/container_popup_menu.xml
cat reference/research_repos/winlator/app/app/src/main/res/menu/containers_menu.xml
cat reference/research_repos/winlator/app/app/src/main/res/menu/main_menu.xml
# what the app downloads vs ships (component/version machinery)
ls reference/research_repos/winlator/installable_components/
grep -o 'Type\.[A-Z_]*' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/core/GeneralComponents.java | sort -u
cat reference/research_repos/winlator/app/app/src/main/java/com/winlator/core/DefaultVersion.java
grep -n 'LATEST_VERSION\|UPDATE_WINEPREFIX_VERSION\|getRFSVersionFile\|getVersion\|isValid\|clearRootDir\|resetContainerRFSVersions' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/RootFS.java \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/RootFSInstaller.java
# the run side
ls reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/components/
grep -rn 'execGuestProgram\|environment\.put\|proot' \
  reference/research_repos/winlator/app/app/src/main/java/com/winlator/xenvironment/components/GuestProgramLauncherComponent.java

# ours, measured
python3 scripts/read_port_surface.py --ports
ls app/src/main/java/com/jupydroid/app/data/space/ \
   app/src/main/java/com/jupydroid/app/data/disk/ \
   app/src/main/java/com/jupydroid/app/data/fake/ | grep -i 'space\|server\|rootfs\|jupyter\|kernel\|tar\|abi\|busybox\|console\|dns\|idle\|python\|shell\|workspace'
find tools/rootfs app/src/main/assets/spaces app/src/main/jniLibs -type f | sort
grep -rn 'spaces/' app/src/main/java/com/jupydroid/app/ --include='*.kt' -l
```

## Who else implements this

| Reference | Implements? | The space/environment model it owns |
| --- | --- | --- |
| jupyterlab | no | knows *kernelspecs* (many kernels per server); the multi-user/multi-env answer is JupyterHub, outside this tree (`hub-extension` is in it but unclaimed). No env lifecycle. |
| vscode | no | per-workspace interpreter *pickers* via extensions; environments live outside the app. |
| intellij-community | partial | `python/src/com/jetbrains/python/sdk/` (`PythonSdkType`, `PySdkFlavor`, venv machinery) — an interpreter registry and venv creator, but SDKs live wherever the user points; no bundled rootfs, no container lifecycle. |
| termux-app | partial | `TermuxInstaller` + `TermuxBootstrap` — one bootstrap `files/usr` extracted from a zip with a staging-dir rename; one env, not a browsable set. No list/detail lifecycle. |
| winlator | full | the model described above — containers as directories, rootfs shared read-only, per-container config + migration, a browsable list + detail + popup, downloadable components. |
| jediterm | no | a terminal widget. |
| box64 / box86 | no | the translators themselves. |

That is **1 of 8 references** owning the whole shape — which is why the Area
partition table gives this slice no command ids and the instrument stays
untouched: there is nothing `x:y` to count on the reference side, and the
area is the app's own invention-adapted-from-winlator.

## Implementation inventory

### `winlator` @ `fb66541b…`

The lifecycle, end to end:

- **Identity is a directory.** `ContainerManager` scans
  `files/containers/`; a container is a directory named `<id>`; the id comes
  from `getNextContainerId()` (max+1). Names are display strings inside the
  config, not the id. `loadContainers()` rebuilds the list every call —
  the scan *is* the truth, the list is a projection.
- **Config lives inside the dir.** `.container` is a JSON Object; every
  `data.get` carries a literal default (`"name"`, `"unknown"`), so a partial
  or future config loads field-by-field, never throws. `Container`'s
  fields span display (name, coverArt), run (graphicsDriver, dxwrapper,
  audioDriver, emulator, wincomponents, drives, showFPS, cpuList,
  desktopTheme, screenSize, envVars, mouseWarpOverride, locale,
  midiSoundFont, inputType, lc_all, primaryController, controllerMapping,
  winVersion, startupSelection, box86Preset, box64Preset,
  vkbasicHapticFeedback, xvfbW/RefreshRate, desktopFullscreen,
  sdlHapticFeedback), and state (winePrefixNeedsUpdate).
- **Acts.** `createContainerAsync` (background thread → `Container.createDir`
  → save → callback), `duplicateContainerAsync` (`FileUtils.deepCopy` the
  dir under a fresh id), `removeContainerAsync` (`FileUtils.delete` the dir,
  `onRemoveContainer` closes `XServerDisplayActivity` if running it),
  `saveData` (in-place `.container` rewrite, 18 keys), and
  `activateContainer` — not a user act but the run path's mark: when
  `XServerDisplayActivity` launches a container it records it as the live
  one for `XrActivity` to pick up.
- **Config migration is an act of its own.** `updateWineprefix` flips
  `winePrefixNeedsUpdate=false` after running `WineRegistryEditor` +
  `WineStartMenuCreator` + `WineThemeManager` — schema change is a
  *migration pass on next launch*, not a config rewrite.
- **The rootfs is separate, versioned, and read-only to containers.**
  `RootFS.isValid` = `files/rootfs` is a dir + `.winlator/.rfs_version`
  exists; `getVersion` parses that stamp's first line. `installIfNeeded`
  extracts `rootfs.tzst` (a top-level asset, with `rootfs_patches.tzst`
  beside it) whenever `!isValid || version < LATEST_VERSION (=19)` — and
  `Container.createDir` creates the container's own `root` dir as its
  `drive_c` view *into* the shared rootfs (`rootfs/home/xuser` ⇄
  `container/root` via `.sys`). A container is cheap; the image is the
  heavy thing and it is shared.
- **Install clears-then-extracts in place, preserving user dirs, and the
  stamp is written last.** `clearRootDir` deletes everything under
  `files/rootfs` **except `home` and `opt`** (and `opt`'s `installed-wine`),
  then the extract runs into the live dir, then — on success only —
  `createRFSVersionFile(LATEST_VERSION)` and `resetContainerRFSVersions`:
  every container's `rfsVersion` extra is cleared, and any provisioned
  under ≤ `UPDATE_WINEPREFIX_VERSION (=16)` on the main wine gets
  `wineprefixNeedsUpdate=t` — the rootfs bump *cascades* into per-container
  migration flags read on next launch. Termux is the stronger variant:
  zip → `usr-staging` → `renameTo(usr)` — a half-extracted staging dir can
  never masquerade as installed.
- **Components are downloadable.** `installable_components/` carries
  box64, dxvk, turnip, vkd3d, wined3d (rootfs + container_pattern ship as
  top-level assets); `GeneralComponents.Type` enumerates seven downloadable
  parts;
  `DefaultVersion` pins each (box64 `0.4.0`, turnip `26.1.0`, …);
  `SettingsFragment` shows installed-vs-downloadable and lets the user pick
  versions. The env is a *set of versioned parts*, not one blob.
- **The surfaces are the app.** `main_menu.xml` drawer: Containers,
  Shortcuts, Input Controls, Settings. `ContainersFragment` is the default
  view — each row carries a Run button (`runContainer` →
  `XServerDisplayActivity`/`XrActivity`), and the per-item
  `container_popup_menu.xml` holds five verbs: File Manager
  (`ContainerFileManagerFragment` over the container dir — the container is
  also a browsable filesystem), Edit, Duplicate, Remove, Storage Info
  (`StorageInfoDialog`), Duplicate and Remove behind `ContentDialog.confirm`.
  The list's toolbar `containers_menu.xml` carries a single Add.
  `ContainerDetailFragment` is the create/edit form — every config key above
  is a field. `ShortcutsFragment` lists per-container `.desktop` entries
  (from `container/desktop` + `wineprefix/drive_c/.../Desktop`), and
  `ShortcutSettingsDialog` gives a shortcut *per-entry overrides* of the
  container config (`putExtra`/`getExtra` extras bag, read in
  `XServerDisplayActivity`).
- **The run side composes components.** `XServerDisplayActivity` builds an
  `XEnvironment`, adds `EnvironmentComponent`s (`XServerComponent`,
  `PulseAudioComponent`, `XorgComponent`/`XrComponent`, `SteamComponent`,
  `MidiManagerComponent`, `ALSAServerComponent`, `GuestProgramLauncherComponent`
  …), `startEnvironmentComponents()` clears `files/tmp` and starts them.
  `GuestProgramLauncherComponent` execs `proot` with the guest argv +
  `environment.put(LD_LIBRARY_PATH|PATH|HOME|TMPDIR|WINEPREFIX|…)`; wine runs
  on box64/wow64 per `container.getEmulator`. `onDestroy` calls
  `stopEnvironmentComponents`.
- **There is a download/progress surface** (`ContentDialog` progress bars,
  `contents_manager` UI) and an **uninstall-all** (`GeneralComponents`
  delete) — lifecycle acts over *parts*, not just containers.

### `termux-app` @ `3df69d1d…`

`TermuxInstaller.setupBootstrapIfNeeded`: extract `bootstrap-<abi>.zip` into
`files/usr-staging`, then `renameTo(files/usr)`; a broken/corrupted staging
dir is deleted and re-extracted; symlinks are created from a `SYMLINKS.txt`
manifest inside the zip. One environment — the scan/duplicate/remove surface
does not exist because there is nothing to scan. Same
`files`-dir-under-`targetSdk 28` exec dodge as winlator (its bootstrap blobs
exec from `files/usr` only because it asks the OS for SDK 28).

### `intellij-community` @ `406a27a8…`

`python/src/com/jetbrains/python/sdk/` — `PythonSdkType` discovers
interpreters on PATH/well-known paths, `sdk/add/target` creates venvs and
targets (incl. remote/Docker via the new exec-service modules). A registry
of *external* runtimes with add/remove/detect; the runtime itself is never
shipped. The right-shaped port surface (many envs, one active) without the
rootfs or the lifecycle UI.

### The shipped rootfs (`tools/rootfs/` + `assets/spaces/base/`)

`build.sh` builds a musl rootfs in Docker: `python3.12`, pip,
`jupyter_server` + `terminado` + `nbconvert` + `jupyterlab-server`,
`ipykernel` + `jupydroid_kernel.py` (the `KERNEL_ID` spec whose argv is
rewritten at install by `SpaceKernelSpec` to point at the `nativeLibraryDir`
link), `python`/`jupyter`/`pip` applets (`BusyBoxApplets`/`ConsoleScripts`
map `argv[0]`/console-script names to the real entry points), a stripped
layout (`strip-all`, `rfc3987-syntax-out` ADR-0014). `stage-app-artifacts.sh`
produces `rootfs-arm64-v8a.tzst` → the APK asset; arm64-only (ADR-0003);
extracted once at `files/spaces/base/` with `rootfs-version` as the stamp
(ADR-0007), executable paths relinked into `nativeLibraryDir` *every* launch
(ADR-0002, CLOSED-001 — `nativeLibraryDir` is not stable across updates).
That last act — the launch-time relink — is the one thing neither reference
has to do, because neither runs on targetSdk 36.

### Where the references disagree

- **Stamp granularity.** winlator versions the *rootfs* and each
  *component* (`rootfsVersion.txt` + `DefaultVersion` + `pkg_version` per
  part); ours versions the whole `files/spaces/base` extraction with one
  `rootfs-version` stamp and no per-part versions.
- **Shared vs per-env image.** winlator's rootfs is one shared base every
  container mounts a view into; ours is `files/spaces/<id>` — a Space *is*
  its own rootfs copy, so N Spaces = N extracted images (or N views, if
  `drive_c`-style sharing ever lands).
- **Config schema discipline.** winlator reads every `.container` key with a
  literal default and migrates the *runtime artifacts* (`winePrefixNeedsUpdate`)
  rather than the file; `DiskSpaceRepository` does the same field-by-field —
  but the stamp/migration story (`rootfs-version` bump → what happens to the
  old tree?) is the open audit question.
- **One env vs many.** termux: exactly one bootstrap, no list. winlator:
  the list *is* the app. Ours: the model supports many, the runtime is
  built for one (`BaseSpaceServer` singular), the UI shows zero.

## Gap analysis — ours today

| Concern | Reference | Ours | Reading |
| --- | --- | --- | --- |
| Identity | dir name = id; scan is truth | `files/spaces/<id>`; `SPACE_ID_PREFIX`, `nextSpaceId` | shape matches (ADR-0010) |
| Config | `.container` JSON, literal-default `data.get`, in-dir | `config.json`, `spaceWithId` field-by-field | shape matches; `pythonVersion` is metadata-only — no real selection yet |
| Acts | create/duplicate/edit/remove/browse; activate-by-run | `SpaceRepository` (list/create/rename/delete) | exists but **zero production callers** — `AppContainer` never builds it; JD-002's ground |
| Rootfs | `files/rootfs`, `.winlator/.rfs_version` stamp; `isValid` = dir + stamp | `files/spaces/base`, `.jupydroid/rootfs-version` stamp; install-needed = `installedVersion != bundledVersion` | same stamp shape; winlator's validity adds only file-existence |
| Install | clear-except-`home`/`opt` → extract in place → stamp last; version bump cascades `wineprefixNeedsUpdate` to containers | `BaseSpaceInstaller`: extract in place (no clear) → relink → stamp last | same stamp-last ordering; **no clear** — entries a new image dropped linger, user files survive by absence from the archive |
| Reinstall semantic | `clearRootDir` preserves `home/` and `opt` across the bump | `TarZstdExtractor` writes over `files/spaces/base`, leaving `home/jupydroid` (the workspace) untouched by construction | both preserve user files; winlator's is a named exception, ours is absence — stale-entry accretion is ours alone |
| Surfaces | Containers list/detail/popup, file manager, shortcuts, settings | none — no Space UI exists | absent as a surface; present-but-unreachable underneath (JD-002) |
| Runtime | `EnvironmentComponent`s + proot + envvars per container run | `SpaceRuntime` ProcessBuilder + env, `JupyterServerLauncher`, one `BaseSpaceServer` | one server vs per-container envs — the coupling the audit reads |
| Components | downloadable, versioned, pickable (`GeneralComponents`, `DefaultVersion`) | one blob, one version | absent (GAP candidate) — no per-part upgrade path |
| Multi-env | many containers share one rootfs read-only | model supports N dirs; runtime + server assume base-only | the audit's spine — N Spaces = N rootfs copies; one server? |
| Exec model | `files/…` exec under `targetSdk 28` | `jniLibs` + `nativeLibraryDir` relink under `targetSdk 36` | ADR-0002/0003 — ours alone vs Android; no reference authority |
| Failure arms | staging-rename (termux); `onRemoveContainer` kills the running act | `tmp`-dir + rename; `JupyterServerLauncher.start` kills first; `BaseSpaceServer` mutex/generation | soundness read — the walk verdicts the arms |

## Deviations

- **The exec model is a forced deviation, not an adaptation.** winlator and
  termux both hold `targetSdkVersion 28` specifically so `exec()` on
  `files/…` stays permitted. This app targets 36; `files/spaces/base/…`
  cannot exec *at all* — hence `jniLibs`, `nativeLibraryDir`, and the
  launch-time relink (ADR-0002, CLOSED-001). No winlator path is a parity
  target here; the reference informs the *lifecycle*, never the loader.
- **`pythonVersion` is written, not selected.** `SpaceConfig` persists it and
  `BUNDLED_PYTHON_VERSION` says what ships, but nothing picks an interpreter
  by it — the config anticipates multi-Python; the runtime is one-Python.
  Whether that field is honest metadata or a lie in the file is a verdict,
  not a guess.
- **The rootfs-per-Space layout changes the reinstall semantics.** winlator
  clears its rootfs on a bump precisely because it names the two dirs to
  keep (`home`, `opt`); `files/spaces/base` contains `home/jupydroid` —
  the served workspace — and ours answers the same constraint by *never
  clearing*: user data survives by absence from the archive, and the cost
  is that nothing ever removes what a new image dropped. That asymmetry,
  not a destroy-path, is what the verdict has to weigh.
- **One server vs N environments is the app's deliberate simplification.**
  `BaseSpaceServer` owns one lazy shared server; winlator runs one env per
  Activity. Whether per-Space servers are a `GAP`, a `JD`, or a documented
  `adapted` is the audit's call — not this session's to pre-empt.

## Open decisions

- Is `SpaceRepository` shipped-but-unreachable the intended `JD-002` ground
  (a `GAP` for the surface, machinery already `present`), or does the audit
  name its unwired state a `defect`? The ledger verdict decides.
- The reinstall semantic is settled by construction — `TarZstdExtractor`
  never clears, so a `rootfs-version` bump cannot destroy `home/jupydroid`
  — but its inverse is the open verdict: entries a new image *dropped* are
  never removed either (winlator's `clearRootDir` is the absent arm), and
  `installedVersion` compares stamps only, never a shape check. Stale
  accretion + stamp-only validity: `adapted`, `GAP`, or `defect`?
- Does a second Space's *existence* (a `space-1` dir on disk) perturb the
  base server's `JUPYDROID_SPACE_ROOT`/kernelspec/`drive`-equivalent
  (`NotebookWorkspace`) — i.e. is the coupling leak already live, or does
  the base Space ignore siblings by construction?
- The version machinery: is `rootfs-version`'s single stamp `adapted`
  (deliberate vs winlator's per-part versions) or `undocumented`? ADR-0007
  records the stamp; does it record the choice against `DefaultVersion`'s
  granularity?

## Tasks

- [ ] **T1 — the surface, produced and opened.** Run the §"The surface,
  produced" greps; land the `## Spaces and runtime — AUD-010` ledger section
  with the surface paragraphs, the produced commands, the ours-map, the
  carried-ins, and the neighbours. No verdicts; Areas row stays
  `unexamined`; no partition rows; `read_ledger_partition --unassigned` and
  `read_ledger_verdicts --missing` stay at 0.
- [ ] **T2 — the model and its lifecycle acts.** `Space`/`SpaceConfig`/
  `SpaceRepository`/`DiskSpaceRepository`/`FakeSpaceRepository` vs
  `Container`/`ContainerManager`/`Shortcut`/`.container` — identity,
  config schema/defaults, create/rename/delete/duplicate/activate/browse,
  the unwired-state verdict, `pythonVersion`'s honesty.
- [ ] **T3 — the image and the install.** `BaseSpaceInstaller`/
  `TarZstdExtractor`/`SpaceKernelSpec`/`BusyBoxApplets`/`ConsoleScripts`/
  `tools/rootfs` vs `RootFS`/`RootFSInstaller`/`installable_components`/
  `DefaultVersion`/`GeneralComponents` + termux's staging — stamp vs
  validity, staged-vs-stamp order, reinstall-vs-workspace, per-part
  versions, `etc/resolv.conf`, the relink.
- [ ] **T4 — the runtime and the lifecycle.** `SpaceRuntime`/
  `JupyterServerLauncher`/`JupyterRuntimeFile`/`BaseSpaceServer`/
  `IdleServerStopper`/`AndroidBaseSpace`/`SpaceAbi`/`SpaceLifecycleLog`
  vs `XEnvironment`/`GuestProgramLauncherComponent`/`XServerDisplayActivity`
  — env construction, start/stop ordering, single-server-vs-multi-Space
  coupling, unsupported-ABI arm, stale runtime file, concurrent start,
  process death, delete-while-running.
- [ ] **T5 — the walk, the verdicts, the debts.** The end-to-end lifecycle
  walk (install → create → run → idle → second Space → reinstall), the
  ledger rows' verdicts with reproduction commands, ADRs for
  `undocumented`, `GAP`s for `absent`, `JD`s for the non-defect repairs,
  red-first fixes for `defect`s.

## Out of scope

- A Spaces UI — the audit *finds* it absent (and likely a `GAP`/`JD`), it
  does not build it.
- Per-Space servers, downloadable components, Space export/import —
  findings, not implementation.
- The `JD-NNN` items this audit opens — each gets its own session, PRD,
  demo, and commit, per AGENTS.md.
- The Jupyter protocol surface `BaseSpaceServer` happens to serve — that
  wire was `AUD-001`/`006`/`007`/`008`'s.
