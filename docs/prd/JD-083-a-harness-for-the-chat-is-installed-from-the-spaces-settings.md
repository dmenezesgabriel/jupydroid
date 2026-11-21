# JD-083 — A harness for the Chat is installed from the Space's settings

Milestone: `M11` · State: open · Depends on: — · Closes: GAP-201

## Outcome

A person installs a Chat agent harness — OpenCode and Claude in the first slice — from a stopped
Space's Settings dialog: each harness's install script is expandable to the exact bytes that will
run, the install runs bounded in the Space's own shell with its output shown, and the section
reports the harness installed with its version, offering it again by itself after a rootfs upgrade
deletes it.

## Demo

On the arm64 tablet (`SM-X216B`), the device `AGENTS.md` rule 2 names — a Space's proot exec exists
only there; on x86_64 `spaceServers` is null and every port stays fake:

1. Cold start: install the debug build, launch. On the Spaces home, Base is stopped (Stop it first
   if it is running — Settings opens only for a stopped Space).
2. Tap Settings on Base's row and scroll to the harness section at the foot of the dialog.
3. Expand OpenCode's entry: the screenshot shows the "what it adds" summary and the install script
   in the startup script's own monospace — the bytes that will run, before anything runs.
4. Tap Install: the section shows the script's output as it streams, then reports the harness
   installed with its version.
5. Install Claude the same way — longer, because it first adds Node.js and npm through `apk`.
6. Leave Settings, Run Base, open a Chat: the agent picker lists OpenCode and Claude, because the
   personas' requirement check ran at the first chat's entry-point load against the now-installed
   executables. Pick one and ask: an answer streams if the harness is signed in (GAP-179 owns
   sign-in; opencode's and Claude's credentials live under the Space's `home/` and survive a
   rootfs upgrade, ADR-0107/0125).
7. Kill and relaunch the app, reopen Base's Settings: both harnesses still report installed — the
   status is a live probe of the Space, not stored app state.
8. The rootfs-upgrade arm needs no separate drive: it is the same probe answering not-installed
   once ADR-0125's re-extract has cleared `usr/` — a device test deletes the binary through the
   same port and watches Install return.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` captured under `evidence/<flow-name>/` (local, ADR-0132).

## Who else implements this

The capability is "a person installs, from inside the app, an agent CLI a chat feature needs, sees
the script first, and watches a bounded install report its result". The two rows past the template's
seven are the requirement's own authority — they say what "compatible with our Chat" means; neither
ships an installer.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | a web application; jupyter-ai's personas document a manual CLI install and nothing in the package set installs one |
| `vscode` | ABSENT | — | chat agents are built-in or contributed by extensions; nothing installs a CLI into a workspace from settings |
| `vscode-jupyter` | ABSENT | — | kernel and session management only; no agent-CLI surface |
| `intellij-community` | ABSENT | — | AI Assistant is a licensed plugin; the IDE ships no "install a CLI into the project" feature |
| `termux-app` | ABSENT | — | `pkg` is the OS package manager; the app installs packages into the system, not components into a container from a settings dialog |
| `winlator` | PARTIAL | `app/app/src/main/java/com/winlator/core/GeneralComponents.java` (`Type`, `isVersioned`, `getInstallMode`), `installable_components/<type>/index.txt` + versioned archives, `app/app/src/main/java/com/winlator/core/GeneralComponents.java` `showDownloadableListDialog`/`downloadComponentFile`, `app/app/src/main/java/com/winlator/SettingsFragment.java` + `ContainerDetailFragment.java` (the pickers), `xenvironment/components/` | a shipped catalogue of versioned parts — `index.txt` manifests fetched, spinner per installed version, download-or-file install, remove with confirm — for Wine/graphics components into the shared rootfs, already measured under AUD-010 (`GAP-149`); the shape is ours' nearest analogue, the domain is not |
| the shipped rootfs | ABSENT | `tools/rootfs/Dockerfile:315-317` (the personas' own comment: "spawn an agent CLI the user installs in the Space"), `tools/rootfs/Dockerfile:98` (`apk add python3 py3-pip git zlib`) | personas shipped, harnesses deliberately not — the image adds no `node`, `npm`, `npx` or `uv`, and `usr/local/bin` starts empty |
| `jupyter-ai-acp-client` | ABSENT | `jupyter_ai_acp_client/acp_personas/{claude,opencode}.py` (module-level `PersonaRequirementsUnmet`), `README.md:57-79` (eight personas, each its executable and install route) | the requirement's authority: `claude.py` needs `shutil.which("claude-agent-acp")` on PATH; `opencode.py` needs `opencode` at `>=1.0.0,<2` via `opencode --version`; no installer anywhere in the package |
| `jupyter-ai-persona-manager` | ABSENT | `jupyter_ai_persona_manager/persona_manager.py` (`_init_ep_persona_classes`, `_ep_persona_classes` ClassVar, `refresh_personas`) | catches `PersonaRequirementsUnmet` at entry-point load, once per server process — which is why installing into a *stopped* Space is the honest scope |

**Count:** 0 of 7 ship this capability; `winlator` is the one PARTIAL and its shape was already
measured. What that says: the installer is ours to design, but not ours to invent — winlator's
catalogue-plus-status is a proven Android shape for "install a versioned part into a container",
and the ACP client's personas fully specify which executables and versions count as compatible.

Which reference is authoritative for which question is in
[`../architecture/cross-cutting.md`](../architecture/cross-cutting.md) §8.3 — `jupyter-ai` covers
"its chat, personas and providers, across the packages its `.gitmodules` names"; the ACP client and
persona manager are pinned separately in [`../README.md`](../README.md).

## Implementation inventory

### winlator@fb66541b93a4

The only PARTIAL — the shape to study, not the code (LGPL-2.1, clean-room per
`cross-cutting.md` §8.4; AUD-010 already walked it for the rootfs itself).

| Concern | Files |
|---|---|
| Domain model | `core/GeneralComponents.java` — `enum Type` (seven parts, five `isVersioned`), `DefaultVersion`'s fourteen pins |
| Port / protocol | n/a — direct file and `HttpUtils.download` calls, no port layer |
| Adapter | `installable_components/<type>/index.txt` fetched from `raw.githubusercontent.com/.../installable_components/%s`, versioned `<name>-<ver>.tzst` archives extracted into per-type destinations (`GeneralComponents.downloadComponentFile`, `extractFile`); installed state is the file's presence plus the spinner's selection |
| UI entry point | `SettingsFragment.java` component pickers and `ContainerDetailFragment.java` — install button per type, version spinner, remove with `ContentDialog.confirm` |
| Persisted state | `installed_components/<type>/<name>-<ver>.tzst` under the app's files dir; the selection in `SharedPreferences` |
| Empty / error / loading states | `PreloaderDialog` while `index.txt` downloads, toast on network error, "there are no items to download" on an empty index |
| Accessibility specifics | none found in the read — desktop dialogs, no content descriptions worth copying |
| Edge cases it handles | builtin vs downloadable (`isBuiltinComponent` refuses remove), install from file as well as download (`InstallMode.FILE`/`BOTH`), versioned vs unversioned parts |

### jupyter-ai-acp-client@77016f63530f — the requirement's authority (ABSENT for the capability)

Not an installer — the files that define what "installed and compatible" means, which the catalog
encodes:

| Concern | Files |
|---|---|
| What counts as installed | `jupyter_ai_acp_client/acp_personas/claude.py` — module-level `shutil.which("claude-agent-acp")`; `acp_personas/opencode.py` — `_check_opencode()`: `which`, then `opencode --version` (5 s timeout), first `(\d+\.\d+\.\d+)` match, `>=1.0.0,<2` |
| When the check runs | `jupyter_ai_persona_manager/persona_manager.py@d5ed04b` — `PersonaManager._init_ep_persona_classes` catches `PersonaRequirementsUnmet` per entry point; `_ep_persona_classes` is a `ClassVar` cached for the server process's life; `/refresh-personas` re-runs the loads |
| What each persona spawns | `acp_personas/{claude,opencode}.py` — `executable=["claude-agent-acp"]` / `["opencode", "acp"]` |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the feature lives | winlator → component pickers inside the container's own settings screen; extension-api → a settings contribution an extension could draw | an extension cannot reach proot — `SidePanelHost` exposes only `SpaceServerEndpoint`, and the install must exec inside the Space; a new extension-api surface is a larger change than two catalog entries justify | `:app`, in `SpaceSettingsDialog` beside the startup script and default kernel, per GAP-201's plan — the dialog already edits only stopped Spaces, which is also the correct scope (Deviations) |
| Catalog form | winlator → `index.txt` manifest fetched at runtime; ours → shipped data | a fetch adds a failure mode and an unpinned answer for two entries; "the app ships as data" is GAP-201's own plan item 1 and keeps the catalog reviewable in the diff | `domain/model/ChatHarness.kt` + `CHAT_HARNESS_CATALOG` — pure Kotlin, unit-testable; entries grow only when a harness has run on the tablet |
| Install script storage | embed script text in the catalog vs ship `.sh` files | embedded text cannot be ShellCheck'd (pre-commit reads every shell file by extension or shebang) and duplicates what probes already prove | `assets/chat-harnesses/<id>.sh` behind a `HarnessScripts` port (`AssetHarnessScripts` in `data/asset/`, `FakeHarnessScripts` in `data/fake/`) — the `AssetLicenceDocuments`/`LicenceDocuments` shape; the displayed bytes are the run bytes |
| How a script runs | reuse `startupScriptArgs`/`startupScriptProcess` verbatim vs a streaming port | the startup-script path redirects output to `run/startup.log` — nothing streams to the screen, which the item requires ("run bounded with its output shown") | a new `domain/port/SpaceShell` — `run(spaceId, script, timeout): Flow<SpaceShellEvent>` emitting output lines then one terminal event; `ProotSpaceShell` in `data/space/` reuses `installOnStartOf`, `spaceRuntime` with the Space's stored settings variables, `SpaceDirs.ensure()`, `killOnExit`, and the same inner-`timeout -s KILL` bound (generalized, not copied); `FakeSpaceShell` in `data/fake/` |
| How "installed" is answered | disk scan for the executable vs exec a probe | `shutil.which` resolves against the Space's own PATH and opencode's persona also runs `--version` — only an exec inside the Space answers both faithfully, and it is what makes the rootfs-upgrade re-offer automatic | a bounded probe script per harness built in domain (`command -v` + `<exe> --version`, first semver parsed — the persona's own regex rule), run through `SpaceShell` on every dialog open; live, never stored |
| The install bound | `STARTUP_SCRIPT_TIMEOUT` (5 min) vs a measured bound | GAP-201: the five-minute bound is kept only if a measured install fits it; the Claude probe allowed apk 600 s + npm 1200 s | measure real durations in T4's device test, then set `HARNESS_INSTALL_TIMEOUT`; probes get their own short bound |
| Keeping the install alive | do nothing vs `keepScreenOn` + warning copy vs block backgrounding | GAP-179 records the platform destroying a Space program's sockets seconds after the app leaves the screen — an install is a download | `keepScreenOn` while installing plus copy that says keep Jupydroid open and that re-running resumes; the scripts are idempotent (`apk add`, `npm i -g`, tarball overwrite), so an interrupted install finishes on re-run |
| Dismiss during install | let it finish vs cancel | a `viewModelScope` install would outlive the dialog unseen; cancelling is honest and the re-run resumes | cancel on dismiss |
| opencode's route | `npm install -g opencode-ai` (the README's route) vs the musl tarball | the README's route needs Node, which the image lacks — installing a runtime for a Bun binary is the heavier path; the tarball route is what `.maestro/fixtures/opencode-probe.sh` proved on the tablet, fetched by `python3`+`certifi` (no Node needed) | the tarball, pinned `opencode-linux-arm64-musl-1.19.3.tgz`, installed to `/usr/local/bin` (on the Space's PATH) |
| claude's route | `@zed-industries/claude-agent-acp` (the README/persona message) vs `@agentclientprotocol/claude-agent-acp` | the adapter was renamed; the persona's message is stale. The tablet probe installed `@agentclientprotocol/claude-agent-acp@0.97.0` — which needs Node ≥ 22, and Alpine 3.23's `nodejs` is 24.22.3 | `apk add --no-cache nodejs npm` when `node`/`npm` are absent, then `npm install -g @agentclientprotocol/claude-agent-acp@0.97.0` — the measured route |
| Version display | presence only vs presence + version | the personas disagree: claude checks `which` alone, opencode checks version too; a person benefits from seeing what was found either way | probe reports `Installed · <version>` where `--version` answers, `Installed` where it does not; opencode's version is checked against its required range and a mismatch is flagged, since the persona would refuse it |

Anything here that outlives the item becomes an ADR before the PRD is deleted — the durable
candidates are the catalog-as-shipped-data decision, the `SpaceShell` port, and the stopped-Space
scope.

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| A catalog of installable harnesses | nothing — no source line names a harness install command (GAP-201 verified) | `winlator@fb66541b93a4 GeneralComponents.java` + `installable_components/` | **absent** — this item |
| The script shown before it runs | the startup script field shows what will run (`SpaceSettingsDialog.kt` `StartupScriptField`) | n/a — the shape is ours' own | mechanism present; the harness section is absent |
| Bounded exec with streamed output | `SpaceStartupScript.kt` bounds by inner `timeout -s KILL` but redirects to `run/startup.log`; `PythonRunner` is unbounded and collects at end; `TerminalGateway` needs the server running | n/a | **absent as a port** — the bounded-args mechanism exists to generalize; streaming does not |
| "Is it installed" for a stopped Space | `DiskSpaceEnvironments` reads a stopped Space's disk without starting it (`SpaceEnvironments` port) — but executables need exec for `--version`, not a file scan | `jupyter-ai-acp-client@77016f63530f acp_personas/` — `which` + `--version` is the persona's own check | **absent** — the probe port this item adds; the stopped-Space-read precedent is `SpaceEnvironments` |
| Where the person meets it | `SpaceSettingsDialog.kt` — stopped Spaces only (`SpacesHomeScreen.kt`, the Settings button's `enabled = actsEnabled && !isRunning`) | `winlator@fb66541b93a4 SettingsFragment.java`/`ContainerDetailFragment.java` | present — the dialog is the right home and already scopes itself correctly |
| Re-offered after a rootfs upgrade | `BaseSpaceInstaller` re-extracts on `BUNDLED_ROOTFS_VERSION` bump and clears `usr/` (ADR-0125); `SpaceOpencodeConfig.ensure` already rewrites opencode's ask-permissions config on every base install | n/a | present by construction — a live probe reports not-installed again; no stored state to invalidate |
| Chat's "no agent" pointer | `ChatPanel.kt` queues questions while `pick.agentIn(transcript)` is null; the persona list arrives empty until a harness exists | GAP-201 item 6 — deferred until the first slice is on a phone | **absent, deferred** — stays open past this item |

State plainly: the catalog, the script display, the streaming exec port, the probe and the section
are absent; the dialog's stopped-Space scoping, the bounded-exec mechanism, the image-ensure path
(`installOnStartOf`) and the post-upgrade clearing are present and reused.

## Deviations

- **Install is offered only on a stopped Space** — not because the dialog says so alone, but
  because upstream caches persona classes per server process: `PersonaManager._ep_persona_classes`
  is a `ClassVar` filled at the first chat's entry-point load
  (`jupyter-ai-persona-manager@d5ed04bc6dfa05fd222d7ea1e54a528b404af4c3
  jupyter_ai_persona_manager/persona_manager.py`). A harness installed while the server runs would
  probe installed yet stay invisible to Chat until `/refresh-personas` or a restart — an "Installed"
  that lies. Installing before the next start makes the personas' own check see it.
- **The shown script is the run script, byte for byte** — no templating, no "generated" wrapper;
  the section displays the asset's text and the port runs that same string, which is what makes
  "the bytes shown are the bytes run" a testable claim rather than a promise.
- **Sign-in is not part of this item** — `GAP-179` owns it; an installed-but-signed-out harness is
  an honest state (the persona lists, its first ask meets the harness's own sign-in wall).
- **The catalog is pinned in the APK, not fetched** — winlator fetches `index.txt` at runtime;
  two measured entries do not justify a network dependency, an unpinned answer, or a failure mode
  the references do not need.
- **Probe scripts are generated, install scripts are files** — the probe is three shell lines any
  harness shares (`command -v` + `--version`), so domain builds it; the install is unique per
  harness and ShellCheck-gated, so it ships as an asset.

## Open decisions

- **Section title and row copy** — "Harnesses", "Chat agents", or the personas' own naming; settled
  in T6 against screenshots, since what a person reads is a design call the references do not make.
- **`HARNESS_INSTALL_TIMEOUT`'s value** — measured in T4's device test (real `apk`+`npm` and
  tarball-fetch durations on the tablet); `STARTUP_SCRIPT_TIMEOUT`'s five minutes is the floor the
  measurement must beat to be reused.
- **Whether the durable decisions get an ADR at ship** — catalog-as-data, `SpaceShell`, and the
  stopped-Space scope are candidates; decided when the item ships, per the template's rule.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `app/src/main/java/com/jupydroid/app/domain/model/ChatHarness.kt` — `ChatHarness`, `HarnessVersion`, `HarnessVersionRange`, `CHAT_HARNESS_CATALOG`; `ChatHarnessProbe.kt` — probe-script builder, output parser, `HarnessPresence` |
| `domain/port` | `SpaceShell.kt` (`run(spaceId, script, timeout): Flow<SpaceShellEvent>`), `HarnessScripts.kt` (`installScript(harness): String`) |
| `data/<mechanism>` | `data/space/ProotSpaceShell.kt` + `spaceShell(context, spaceRepository)` factory (null on a bad ABI, as `spaceServers`); `data/asset/AssetHarnessScripts.kt`; bounded-args generalized out of `data/space/SpaceStartupScript.kt` |
| `data/fake` | `FakeSpaceShell.kt`, `FakeHarnessScripts.kt` — `AppContainer` defaults |
| shipped data | `app/src/main/assets/chat-harnesses/opencode.sh`, `claude-agent-acp.sh` (ShellCheck-gated by pre-commit) |
| `ui/<screen>` | `ui/spaces/SpaceHarnessesSection.kt` + `SpaceHarnessesViewModel.kt`; `SpaceSettingsDialog.kt`, `SpacesHomeScreen.kt`, `Navigation.kt` wiring |
| `di/AppContainer` | `spaceShell`, `harnessScripts` vals; `JupydroidApplication.containerFor` wires `spaceShell(this, spaceRepository)` and `AssetHarnessScripts(assets::open)` |
| unit tests | `app/src/test/.../domain/model/` (catalog, version range, probe parse), `.../data/fake/`, `.../data/asset/` |
| instrumented tests | `app/src/androidTest/.../ui/spaces/SpaceSettingsDialogHarnessesTest.kt` — every visible state, incl. bytes-shown == bytes-run |
| `.maestro/` flow + `scripts/` driver | `chat-harness-install` flow(s) + driver, tablet-only like `opencode-in-a-space.yaml` |

Every port gets a `Fake…` in `data/fake/`, which is also what `AppContainer` defaults to — on the
x86_64 emulator `spaceShell` is null and the section composes over the fakes, as every Space-backed
surface does.

## Tasks

One TDD unit each (red → green → refactor), one conventional commit each.

- [x] T1 — Domain: `ChatHarness` + `HarnessVersion`/`HarnessVersionRange` + `CHAT_HARNESS_CATALOG`
  (OpenCode `>=1.0.0,<2`, Claude presence-only) + the probe-script builder and output parser —
  red-first unit tests pinning the personas' own rules (`which` + `--version` + first semver).
- [x] T2 — Shipped scripts and their port: `assets/chat-harnesses/opencode.sh` and
  `claude-agent-acp.sh` adapted from `.maestro/fixtures/opencode-probe.sh` /
  `claude-acp-probe.sh`'s install arms (self-check ending, ShellCheck-clean),
  `domain/port/HarnessScripts.kt`, `data/asset/AssetHarnessScripts.kt`,
  `data/fake/FakeHarnessScripts.kt`, unit tests.
- [x] T3 — `SpaceShell`: `domain/port/SpaceShell.kt`, `data/space/ProotSpaceShell.kt` +
  `spaceShell(...)` factory (null on a bad ABI, as `spaceServers`), `data/fake/FakeSpaceShell.kt`,
  the bounded-args helper generalized out of `startupScriptArgs`, unit tests on the fake and the
  args, and a tablet device test that runs a real probe — proving the port execs in a Space.
- [x] T4 — The measurement: a tablet device test that runs both real installs through `SpaceShell`,
  recording each duration (the `HARNESS_INSTALL_TIMEOUT` value, set from it) and each installed
  size (the catalog's measured-size text).
- [x] T5 — `SpaceHarnessesViewModel`: probe-all on open, per-harness install lifecycle —
  running with streamed lines, success re-probe, failure excerpt, cancel — unit tests on the fakes.
- [x] T6 — `SpaceHarnessesSection` in `SpaceSettingsDialog` + wiring through `SpacesHomeScreen`,
  `Navigation`, `AppContainer`, `JupydroidApplication`; instrumented tests for every state,
  including the expanded script being byte-identical to what `SpaceShell` was handed.
- [ ] T7 — Demo: `.maestro/` flow + `scripts/` driver on `SM-X216B` — both harnesses installed
  from Settings, Chat's picker listing them, persistence across relaunch, evidence under
  `evidence/<flow-name>/`; close `GAP-201`, delete this PRD, edit the backlog row; an ADR first if
  the durable decisions warrant one.

## Out of scope

- **Harness sign-in** — `GAP-179` owns it; this item ends at "installed and listed by Chat".
- **The other six personas** (Codex, Mistral-Vibe, Copilot, Kilo, Kiro, Goose) — the catalog gains
  an entry only when that harness has been run on the tablet; musl/arm64 startup is unmeasured for
  them (GAP-201).
- **The Chat panel's no-agent pointer** — GAP-201 item 6 stays open past this slice; what the panel
  shows with no agent was read (`ChatPanel.kt` queues while `agentIn` is null) but the pointer is
  its own item.
- **Installing into a running Space** — see Deviations; the personas' per-process cache makes it
  dishonest until `/refresh-personas` exists as a surface.
- **Harness upgrades** — the catalog pins exact versions; a newer release is a catalog change, not
  an in-app update mechanism.
- **The terminal probe flows** (`opencode-in-a-space.yaml`, `claude-acp-in-a-space.yaml`) — they
  prove the Space-side recipes through a terminal, a different path than this feature; they stay.
