# JD-065 — Settings shows the licences of everything the APK ships

Milestone: `M0` · State: in-progress | Depends on: `OPS-003` · Closes: `GAP-136`

## Outcome

A user with only the APK opens Settings → Licences and reads the app's own licence, what it
transcribed from other projects, the Gradle libraries it links, and the rootfs packages it bundles,
with the written source offer for the GPL and LGPL parts.

## Demo

No cell runs, so the `dev36` emulator is enough (`AGENTS.md` rule 2).

1. Cold start: install, launch, open Settings (the gear, or More → Settings at one pane).
2. Scroll to the Licences row and tap it: a screen listing five documents opens over Settings.
3. Open each in turn: `LICENSE` (Apache-2.0 text), `NOTICE` (Termux, VS Code, Jupyter Server credits),
   Gradle libraries, `THIRD_PARTY.md` (245 rootfs packages, scrolls), `SOURCE_OFFER.md`.
4. Tap a link in the source offer: the browser opens it (`ExternalLinkOpener`).
5. Back returns to the list, back again to Settings. Kill and relaunch: the screen opens the same.

Not `shipped` until run on that device, with screenshots looked at and `maestro.log` kept under the
local `evidence/` (ADR-0132).

## Who else implements this

Read on disk under `reference/research_repos/` at the pinned SHAs of [`../README.md`](../README.md).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `packages/apputils-extension/src/licensesplugin.ts:142-210`, `packages/apputils/src/licenses.tsx:30-160,285-312` | A `Licenses` split panel: bundle tabs on the left, a per-package grid on the right, **fetched from the server's `licensesUrl`**; the command returns early when that page-config is unset |
| `vscode` | PARTIAL | `src/vs/workbench/browser/actions/helpActions.ts:243-273` | "View License" opens `product.licenseUrl` in the browser; nothing is shown in-app. `ThirdPartyNotices.txt` ships beside the binary |
| `vscode-jupyter` | ABSENT | — | No licence surface in `src/`; the extension host's own is VS Code's |
| `intellij-community` | FULL | `platform/platform-impl/src/com/intellij/ide/actions/AboutDialog.java:95,383-400` | About dialog's "third-party software" button reads the bundled `license/third-party-libraries.html` and shows it in a dialog |
| `termux-app` | ABSENT | `LICENSE.md` (repo file only) | No in-app licence view; the licence is a file in the repository |
| `winlator` | PARTIAL | `app/app/src/main/java/com/winlator/contentdialog/AboutDialog.java:27-39` | About dialog with a hard-coded credits list of project names and links; no licence text |
| the shipped rootfs | FULL | `THIRD_PARTY.md`, `SOURCE_OFFER.md` (`tools/rootfs/sbom.sh` generates the first) | The data this screen shows already exists as generated, hash-pinned files |

**Count:** 2 of 7 show licences or credits inside the product (IntelliJ, JupyterLab); Winlator shows
credits only; VS Code opens a URL. IntelliJ is the closest: a file bundled with the product, read
from disk at the tap. JupyterLab is the richest, and its source is a server API we do not have
(`GAP-136`, [`../architecture.md`](../architecture.md)).

## Implementation inventory

### `intellij-community@406a27a8`

| Concern | Files |
|---|---|
| Domain model | none; a file's text |
| Adapter | `AboutDialog.java:383-400` reads `license/third-party-libraries.html` under the install dir |
| UI entry point | the About dialog's button; shown in a dialog |
| Empty / error / loading states | a progress task wraps the read; an `IOException` shows an error |

### `jupyterlab@7f0e6906`

| Concern | Files |
|---|---|
| Domain model | bundles of packages, each with name, version, licence id, licence text (`licenses.tsx`) |
| Port / protocol | `GET licensesUrl` from the server extension; unset means the command does nothing |
| UI entry point | `licensesplugin.ts:142-210`; `help-extension` forwards its deprecated `help:licenses` here |
| Edge cases | a refresh command re-reads the model; a report download in three formats |

**Where they disagree:** IntelliJ bundles a file and reads it at the tap; JupyterLab asks a server.
We have no licences server, and an APK is a fixed artifact, so we bundle.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Source of the text | JupyterLab: fetched at runtime; IntelliJ: bundled file | A fetch needs a server and fails offline; a bundle cannot go stale against the APK | **Bundled.** The text must match the binary it came with |
| Where the files live | copy into `app/src/main/assets`; copy at build time | A committed copy drifts from `LICENSE`/`NOTICE` | **A Gradle `Copy` at build time** into a generated assets dir; the repo files stay the one source |
| Gradle libraries | none of the seven; Google's `oss-licenses-plugin`; our own task over POMs | A plugin is less code, but is a new locked, hash-verified dependency | **`oss-licenses-plugin`** (official, decided with the user). T2 proves it on AGP 9 or reports back |
| Surface | JupyterLab: a main-area widget; IntelliJ: a dialog | A 245-row table does not fit a dialog | **A `NavKey` screen over Settings**, as `Settings` is |
| Rendering | HTML (IntelliJ), a grid (JupyterLab) | Re-implementing a renderer is new code | **Reuse** `markdown/` and `richtext/` for the three Markdown files; plain monospaced text for `LICENSE` and the Gradle list |

## Gap analysis — what we already have vs the references

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| The texts | `LICENSE`, `NOTICE`, `THIRD_PARTY.md`, `SOURCE_OFFER.md` at the repo root | `intellij-community` bundled file | **present but unreachable**: none is in the APK |
| Gradle libraries' licences | none | `jupyterlab` per-package grid | absent |
| A Settings entry | `SettingsScreen` has no link row | `help-extension` menu entry | absent |
| A route | `Settings.kt` is the `NavKey` pattern to copy | — | absent |
| Link opening | `ExternalLinkOpener` port exists | `vscode` `openerService.open` | complete |

## Deviations

- JupyterLab's `licensesUrl` is a page-config the Lab server extension writes and bare
  `jupyter_server` does not, so its command is inert on this stack
  (`docs/research/parity-ledger.md`, the `help:licenses` row). We bundle instead of fetching.
- No refresh or report-download command: a bundle changes only with a new APK.
- No About surface (`GAP-135`); this item adds only the Licences entry.

## Open decisions

- Whether `oss-licenses-plugin` 0.12.0 works under AGP 9 with STRICT locking and sha256
  verification ([ADR-0123](../adr/0123-dependencies-are-locked-strict-and-verified-by-sha256.md)).
  T2 is a spike that answers it. If it does not, T2 stops and reports.
- How the plugin's concatenated raw resource, with its byte-offset metadata, is split into the
  port's documents. Decided by reading the generated output in T2.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | `LicenceDocument` |
| `domain/port` | `LicenceDocuments` |
| `data/asset` | `AssetLicenceDocuments` |
| `data/fake` | `FakeLicenceDocuments` (the `AppContainer` default) |
| `ui/settings` | `LicencesScreen`, `LicencesViewModel`, a Licences row in `SettingsScreen` |
| `Settings.kt` beside | `Licences` `NavKey`, `openLicences` |
| `di/AppContainer` | builds `AssetLicenceDocuments` |
| unit tests | one per new type; `ArchitectureGuardTest` stays green |
| instrumented tests | the row is visible and opens the screen |
| `.maestro/` flow + `scripts/` driver | Settings → Licences → each document → back |

## Tasks

- [x] T1 — Bundle the repo's licence files into the APK: `LicenceDocument`, `LicenceDocuments`,
  `FakeLicenceDocuments`, the Gradle `Copy`, `AssetLicenceDocuments`.
- [ ] T2 — Gradle libraries' licences via `oss-licenses-plugin`, through the same port.
- [ ] T3 — `Licences` `NavKey`, `openLicences` and `LicencesScreen`: a list, tap to read.
- [ ] T4 — The Licences row in `SettingsScreen`, the `Navigation.kt` entry, an instrumented test.
- [ ] T5 — The Maestro flow and its driver on `dev36`; screenshots looked at.
- [ ] T6 — ADR, `cross-cutting.md` §8.4, the ledger's `GAP-136` row, delete this PRD, backlog to
  `shipped`.

## Out of scope

An About or help surface (`GAP-135`); producing the SBOM in CI (`OPS-007`); README, CONTRIBUTING and
SECURITY (`OPS-004`); going public (`OPS-005`).
