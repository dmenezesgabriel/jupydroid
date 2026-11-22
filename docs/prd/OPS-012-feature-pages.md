# OPS-012 — A page per feature area

Milestone: `M12` · State: open · Depends on: OPS-009, OPS-010, OPS-011 · Closes: —

## Outcome

A user can open the documentation site and find a page for each feature area the app has —
Spaces, the interface, notebooks, the Console, the terminal, files, search, Git, Chat, MCP
servers, settings and themes, and troubleshooting — each saying what the surface does in this
app, with its pictures produced by `scripts/site-screenshots-driver.sh` in both colour schemes.

## Demo

An `OPS-NNN` item ships no app capability, so it closes on the command or workflow run that
reproduces it ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)), not a
Maestro flow. The pages' claims are reproduced by:

1. `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` — every page is in
   `nav`, every link and image resolves, and the site emits under `build/site`.
2. `uv run --locked python scripts/docs_site_config_test.py`,
   `uv run --locked python scripts/check_markdown_links.py`, and
   `uv run --locked python scripts/check_site_images.py` — the site contract the commit hook
   already enforces.
3. `ANDROID_SERIAL=<SM-X216B> scripts/site-screenshots-driver.sh` — regenerates every image the
   pages use in both schemes; evidence stays under `EVIDENCE_ROOT`
   ([ADR-0132](../adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)).

The underlying surface paths are already proven by the Maestro suite the shot flows are cut
from; this item adds no app code, so no flow of its own is owed beyond the shot flows the driver
runs.

## Who else implements this

**Every reference gets a row, including the ones that do not have it.** This item is a census of
*documentation structure*, so the rows are the doc-bearing references: `jupyterlab`'s User Guide
and `microsoft/vscode-docs` are the two the backlog names; `jupyter-chat`, `jupyter-ai` and
`jupyterlab-git` are added because they are the pinned upstreams of this app's Chat, agent and
Git surfaces. `vscode-docs` is not in `docs/README.md`'s pin table; its SHA was read from `main`
on 2026-11-21 and the clone under `reference/research_repos/vscode-docs` is at it.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `docs/source/user/index.md` toctree, `docs/source/user/*.md` | ~25 User Guide pages, one per surface: interface, files, notebook, consoles, terminal, running, commands, debugger, toc, export, customization. |
| `vscode-docs` | FULL | `docs/toc.json`, `docs/<area>/` | ~11 top-level areas (Editor, Source Control, Terminal, Debugging & Testing, AI in VS Code, …), each a page or small subtree. |
| `vscode` | ABSENT | `README.md`, `CONTRIBUTING.md` | Source repo; user docs live in `microsoft/vscode-docs`. |
| `vscode-jupyter` | PARTIAL | `README.md` | One Quick Start plus a feature list; no per-area pages. |
| `jupyter-chat` | PARTIAL | `docs/source/users/index.md` | A short users page: install, create a chat, basics — no per-feature depth. |
| `jupyter-ai` | PARTIAL | `docs/source/users/index.md` | Users pages for personas, magic commands, troubleshooting — of the upstream, not of a host app. |
| `jupyterlab-git` | PARTIAL | `README.md` | README documents clone/push/PR flow in prose; no user-guide pages. |
| `intellij-community` | ABSENT | `docs/readme.md` | Platform/plugin developer notes; product user docs are external. |
| `termux-app` | ABSENT | `docs/en/index.md` | Placeholder page only. |
| `winlator` | PARTIAL | `README.md` | Install/launch/tips in one README. |
| the shipped rootfs | ABSENT | — | Runtime packages, not user documentation. |
| `textual` | FULL | `mkdocs.yml`, `docs/` | The flagship Material for MkDocs consumer — censused for the site's own configuration: theme features, plugins, markdown extensions, nav shape — not for page structure. |

**Count:** 12 rows censused — the template's seven, `vscode-docs` and `textual` (the backlog
names the first; the maintainer names the second for site polish), and the three upstreams of
this app's contributed surfaces: 3 FULL, 5 PARTIAL, 4 ABSENT. Both
mature products that have a user guide split it by feature area; every smaller reference keeps
its whole surface in one README or one short page. Which reference is authoritative for which
question is in [`../architecture/cross-cutting.md`](../architecture/cross-cutting.md).

## Implementation inventory

For **every** reference marked FULL or PARTIAL, the files an implementer must actually open —
the paths, not a summary of them, so the executing session reads the real pages and picks what
suits us. For a docs item the inventory is the reference *pages*, one row group per page this
item ships.

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Our page | Files |
|---|---|
| Spaces | — (no equivalent; nearest is `docs/source/user/running.md` for run/stop semantics) |
| Interface | `docs/source/user/interface.md`, `docs/source/user/interface_customization.md` |
| Notebooks | `docs/source/user/notebook.md`, `docs/source/user/file_formats.md`, `docs/source/user/export.md` |
| Console | `docs/source/user/code_console.md` |
| Terminal | `docs/source/user/terminal.md` |
| Files | `docs/source/user/files.md`, `docs/source/user/file_editor.md`, `docs/source/user/directories.md` |
| Search | `docs/source/user/commands.md` (command/search palette — we have none; the workspace search panel is ours) |
| Git | — (jupyterlab-git's docs are its README, below) |
| Chat / MCP | — (jupyterlab core has neither; see `jupyter-chat`, `jupyter-ai`) |
| Settings & themes | `docs/source/user/interface_customization.md` |
| Troubleshooting | `docs/source/getting_started/faq.md`, `docs/source/getting_started/issue.md` |

### vscode-docs@284f20ec9cfc6589f9505c1a5895056e728d355d

| Our page | Files |
|---|---|
| Interface | `docs/editing/getting-started/userinterface.md`, `docs/configure/custom-layout.md` |
| Notebooks | `docs/datascience/jupyter-notebooks.md`, `docs/datascience/notebooks-web.md` |
| Terminal | `docs/terminal/getting-started.md`, `docs/terminal/basics.md`, `docs/terminal/appearance.md` |
| Git | `docs/sourcecontrol/overview.md`, `docs/sourcecontrol/quickstart.md`, `docs/sourcecontrol/staging-commits.md`, `docs/sourcecontrol/history.md`, `docs/sourcecontrol/troubleshooting.md` |
| Chat | `docs/agents/overview.md`, `docs/agents/run/chat-view.md`, `docs/chat/chat-overview.md`, `docs/chat/inline-chat.md` |
| MCP | `docs/agent-customization/mcp-servers.md`, `docs/agents/run/manage-mcp-servers.md` |
| Settings & themes | `docs/configure/settings.md`, `docs/configure/themes.md`, `docs/configure/keybindings.md` |
| Troubleshooting | `docs/agents/agent-troubleshooting/`, `docs/sourcecontrol/troubleshooting.md` |

### jupyter-chat@2c224bccb40f45438f85b28c3654da02b5c1461f

| Concern | Files |
|---|---|
| User-facing chat docs | `docs/source/users/index.md` |

### jupyter-ai@5b23f55d15ba245347013ffe0a238f26b1f4f1e8

| Concern | Files |
|---|---|
| User-facing agent docs | `docs/source/users/index.md`, `docs/source/users/troubleshooting.md` |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Feature surface in one page | `README.md` |

### jupyterlab-git@b62a87b719cd151a2c60eaaa6a70a6755784c4ef

| Concern | Files |
|---|---|
| Feature surface in one README | `README.md` |

**Disagreement the item settles:** jupyterlab gives every surface its own page (~25); vscode-docs
groups into ~11 areas with sub-pages only where a surface is deep (Source Control gets ten,
Terminal six). Ours splits by *surface the user can name* — the same list the backlog row
enumerates — and lets depth follow the surface: the notebook and chat pages run long, the Git
page stays short because the panel is read-only status. Where jupyterlab splits what is one
surface here (files + file editor + directories → one Files page; running + documents_kernels →
the interface page's Sessions panel and Spaces page), we group.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Page granularity | jupyterlab → ~25 pages, one per surface; vscode-docs → ~11 areas; vscode-jupyter → one README | More pages track the reference but document surfaces we do not have (rtc, lsp, jupyterhub, binder, workspaces, languages) | 12 pages, one per nameable surface — the backlog's own list plus Interface; everything else is named under Out of scope. |
| Console and terminal | jupyterlab → separate `code_console.md` and `terminal.md`; vscode-docs → Terminal subtree only | Folding them into Notebooks hides that each is its own launcher card and tab kind | Two pages; they share a task because both are small and launcher-made. |
| Git depth | vscode-docs → 10 source-control pages; jupyterlab-git → README prose; ours → read-only branch + changed-files panel (`extensions/git/src/main/java/com/jupydroid/extensions/git/ui/SourceControlPanel.kt`) | Documenting commits/pushes we do not have would lie; a full vscode-docs mirror documents nothing we do | One short page: what the panel shows, that git itself runs in the Space's terminal. |
| MCP placement | vscode-docs → its own subtree under agent customization; ours → `.jupyter/mcp_settings.json` edited from the Chat's "MCP servers" button (`extensions/ai/src/main/java/com/jupydroid/extensions/ai/domain/McpSettingsFile.kt`) | A Chat section buries a distinct artifact users must hand-edit; jupyterlab has nothing to copy | Own short page — the backlog names MCP separately and the file format is the content. |
| Nav shape | jupyterlab → flat toctree; vscode-docs → nested `toc.json` | Nesting groups but adds a level for 14 entries | Flat `nav`, ordered by user journey: Home, Getting started, Spaces, Interface, Notebooks, Console, Terminal, Files, Search, Git, Chat, MCP servers, Settings, Troubleshooting. |
| Pictures | references embed many screenshots; ours → produced light/dark pairs only ([ADR-0170](../adr/0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md)) | Hand-taken frames cannot be told from produced ones | Every image a page uses is a produced pair; a page with nothing worth shooting stays text-only rather than shipping a weak frame. |
| Shot production | current driver → one `frame:name` list for 5 frames; backlog → "one provisioning pass serves every shot, UI-only frames never boot a Space, a flow captures a shot list per area" | ~50 pairs in one flat list is the registry ADR-0170 deferred; per-flow subdirs already exist (`release-install-demo/`) | Site-shot flows are dedicated `.maestro/site-<area>-shots.yaml` files whose `takeScreenshot` names *are* the image ids; the driver crops every PNG each produced, no per-frame list. T1 validates the convention. |
| State between shots | existing drivers → `reset_notebooks` + re-seed between flows; chained flows → `stopApp: false` resume | Resetting per flow is deterministic but re-seeds; chaining saves Space boots but couples flows' order | Reset + re-seed per flow (deterministic, cheap — seeding is `run-as`, installs are not); Space boots are paid per workspace flow because Open requires Running and a killed app reads every row Stopped (`enter-a-running-space-relaunch-persist.yaml`). T1 measures the cost and reports it in the commit body. |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Per-area pages | absent — `docs-site/pages/` holds `index.md`, `getting-started.md`; `docs-site/mkdocs.yml` `nav` names two | `jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12 docs/source/user/index.md`, `vscode-docs@284f20ec9cfc6589f9505c1a5895056e728d355d docs/toc.json` | absent |
| Produced images | present for two pages — `docs-site/pages/images/` holds 5 pairs from `scripts/site-screenshots-driver.sh` | `vscode-docs@284f20ec9cfc6589f9505c1a5895056e728d355d docs/sourcecontrol/overview.md` | producer exists, ~45 pairs absent |
| Shot flows | absent — the 700+ `.maestro/` flows prove surfaces but none is a site-shot list | — | absent |
| Driver shot mapping | present — `frame:name` pairs in `capture_theme`, `scripts/site-screenshots-driver.sh` | `docs/adr/0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md` | present but does not scale to per-area flows; T1 extends it |
| Surface reach-paths | present — `open-base-space.yaml`, `focus-cell.yaml`, `site-setup.yaml`, and per-feature flows (`git-panel-branch.yaml`, `chat-*.yaml`, `one-pane-side-panels.yaml`, …) already drive every surface | — | complete; shot flows compose them |

State plainly: the site skeleton, image producer and every surface's reach-path exist; what is
absent is the pages, the shot flows and the driver mapping for them.

## Deviations

- No videos, localization, per-language pages, keyboard-shortcut reference, accessibility
  statement, or lifecycle page: no reference gap here requires them, and none are scheduled.
- No JupyterHub, RTC, LSP, workspaces, binder, file-formats or extension-authoring pages: those
  surfaces do not exist in this app; the engineering documents in `docs/` keep their own
  audience.
- The Git page documents a read-only status panel and says git runs in the terminal — it does
  not mirror vscode-docs' ten-page Source Control guide because we do not have its commands.
- No new ADR is expected: [ADR-0169](../adr/0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)
  and [ADR-0170](../adr/0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md)
  already decide the site and its images. If the shot-naming convention or nav shape surfaces a
  durable choice they do not cover, it becomes an ADR before this PRD is deleted
  ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Open decisions

- The shot list per area — which frames each page keeps: settled per task by inspecting the
  produced pairs, as OPS-011's T2 did; the backlog names the minimum (every Settings section,
  the rail bar, explorer and search features, context menus).
- Whether site-shot flows cold-`launchApp` each (deterministic, one Space boot per workspace
  flow) or chain with `stopApp: false` (fewer boots, order-coupled): settle in T1 by measuring
  both on `SM-X216B`; default is cold launches.
- Whether the existing two flows (`readme-screenshots.yaml`, `release-install-demo.yaml`) migrate
  to id-named shots or keep their `frame:name` lines: settle in T1; default is they keep theirs —
  they also serve the README driver, whose mapping is separate.
- Whether base reads Stopped at every cold launch (it does — `enter-a-running-space-relaunch-persist.yaml`
  records "no server outlives its app") makes the Spaces page's Running-state shot unreachable
  without a boot: settle in T1; the Spaces flow itself taps Run for that one frame, or the page
  ships without it — inspect before deciding.
- Chat shots need an ACP harness installed in the Space (`chat-harness-install-*.yaml`): settle
  in T7 whether the install survives `reset_notebooks` and app kills like pip packages do; if
  not, it joins the once-per-run provisioning next to `site-setup.yaml`.
- The nav labels and order: settle in T1 with the Spaces page; the order above is the default.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | — |
| `ui/<screen>` | — |
| `di/AppContainer` | — |
| unit tests | `scripts/docs_site_config_test.py` (nav contract — every page on disk named in `nav`), `scripts/check_site_images_test.py` (pair gate) |
| instrumented tests | — |
| `.maestro/` flow + `scripts/` driver | `.maestro/site-<area>-shots.yaml` per area, `scripts/site-screenshots-driver.sh`, possibly `scripts/maestro-run.sh` (per-flow evidence dirs already supported) |
| docs site | `docs-site/pages/<area>.md` ×12, `docs-site/mkdocs.yml` `nav`, `docs-site/pages/images/*-{light,dark}.png` |
| fixtures | `.maestro/fixtures/` — new seeds only where an area needs content none has (e.g., searchable text files) |

## Tasks

One coherent change per commit. Page tasks each end with the driver run on `SM-X216B` that
produces their pairs, every frame inspected, and the strict build plus site gates green. Small
enough that each takes well under a session; if one does not, decompose it here before coding.

- [x] T1 — Shot pipeline + Spaces page: the `site-<area>-shots.yaml` convention (shots named by
  image id, per-flow evidence dirs, crop-every-PNG), UI-only ordering (Spaces shots before any
  boot), the cold-vs-chained launch measurement, and `.maestro/site-spaces-shots.yaml` +
  `docs-site/pages/spaces.md` + `nav` proving it end to end.
- [x] T2 — Interface page: rail, main-area tabs, side panels (Files, Sessions, Search, Outline,
  Debugger, contributed Chat/Source Control), the launcher, one-pane bottom bar and "More" rail.
- [x] T3 — Notebooks page: cells and cell types, running cells, outputs, the cell-actions menu,
  completions, checkpoints and revert, save/save-as, export formats.
- [ ] T4 — Console and Terminal pages: the kernel console and the shell terminal, their launcher
  cards, tabs and settings.
- [ ] T5 — Files and Search pages: the Files panel, New menu, file editor, recents, import;
  workspace search, replace, and in-document find.
- [ ] T6 — Git page: the Source Control panel's branch and changed files, and that git runs in
  the terminal.
- [ ] T7 — Chat and MCP pages: chats, the agent/model/mode/effort chips, slash commands, tool
  calls and permissions, queued questions; `.jupyter/mcp_settings.json` via "MCP servers".
- [ ] T8 — Settings and Troubleshooting pages: every Settings section and theme/app-icon choice;
  the arm64 caveat, the Diagnostics error log, and where a Space's server log lives.
- [ ] T9 — Site polish: census `textualize/textual` at `5e5b7ef58b8c5572c13a3ff9027d756623661495`,
  cloned under `reference/research_repos/textual` — its config is split `mkdocs-common.yml` +
  `mkdocs-online.yml`/`mkdocs-offline.yml` + `mkdocs-nav.yml` via `INHERIT` (`Makefile` composes
  them): read its theme features, plugins, markdown extensions and nav, and adopt each knob a
  smaller project keeps (logo and favicon, palette and scheme toggles, nav footer, tabs,
  annotations, copy buttons as pages need them); the commit body names what was taken and what
  was left, and the strict build stays green.
- [ ] T10 — Ship: full driver rerun on `SM-X216B` with every frame inspected, strict build and all
  site gates green, delete this PRD, edit the backlog row to `shipped`, commit.

## Out of scope

- App code of any kind: this item documents surfaces that shipped in earlier items; a surface
  found wrong while writing its page becomes a `JD-NNN` with its own demo, not a silent edit.
- Developer/extension-authoring documentation: `extension-api` and the `extensions/` modules are
  engineering concerns and stay in `docs/`.
- Reconciling `README.md`'s Install section or the README screenshot set: the README driver is a
  separate consumer with its own mapping.
- Videos, localization, an accessibility statement, release notes: none scheduled.
