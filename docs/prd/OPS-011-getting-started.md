# OPS-011 — Getting started

Milestone: `M12` · State: in-progress · Depends on: OPS-009, OPS-010 · Closes: —

## Outcome

A new user can install the published arm64 release, open the Spaces home, run and open `base`,
create a notebook, run `print(6 * 7)` and see `42`, following one page on the documentation site.

## Demo

An `OPS-NNN` item ships no app capability, so it closes on the command or workflow run that
reproduces it ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)), not a
Maestro flow. The page's claims are reproduced by:

1. `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` — the page is in
   `nav`, every link and image resolves, and the site emits under `build/site`.
2. `uv run --locked python scripts/docs_site_config_test.py`,
   `uv run --locked python scripts/check_markdown_links.py`, and
   `uv run --locked python scripts/check_site_images.py` — the site contract the commit hook
   already enforces.
3. `ANDROID_SERIAL=<SM-X216B> scripts/site-screenshots-driver.sh` — once T2 lands, this regenerates
   every image the page uses in both schemes; evidence stays under `EVIDENCE_ROOT`
   ([ADR-0132](../adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)).

The underlying install → Space → cell path is already proven for a release APK by
`scripts/release-install-demo-driver.sh` and `.maestro/release-install-demo.yaml`; this item
reuses those labels rather than inventing new ones.

## Who else implements this

**Every reference gets a row, including the ones that do not have it.** `microsoft/vscode-docs` is
censused beside the template's `vscode` row because the backlog names it and the source repo
carries no user guide; its SHA was read from `main` on 2026-11-21.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | FULL | `docs/source/index.md`, `docs/source/getting_started/overview.md`, `docs/source/getting_started/installation.md`, `docs/source/getting_started/starting.md`, `docs/source/user/index.md`, `docs/source/user/notebook.md` | Get Started separates install, launch and first document, then the User Guide goes deep. |
| `vscode` | ABSENT | `README.md`, `CONTRIBUTING.md` | Source repo; the user documentation lives in `microsoft/vscode-docs`. |
| `vscode-docs` | FULL | `docs/toc.json`, `docs/getstarted/overview.md`, `docs/getstarted/what-is-vscode.md`, `docs/supporting/requirements.md`, `docs/editing/getting-started/editor-tutorial.md` | Install by platform, requirements, open a project, then a concrete hands-on tutorial. |
| `vscode-jupyter` | PARTIAL | `README.md` | Numbered Quick Start: editor, Python environment/kernel, create notebook, select kernel, code. |
| `intellij-community` | ABSENT | `docs/readme.md`, `docs/plugin.md` | Platform/plugin developer notes; product user docs are external. |
| `termux-app` | ABSENT | `docs/en/index.md` | Placeholder page only. |
| `winlator` | PARTIAL | `README.md` | APK install, launch, wait for setup, then tips; no first-document run. |
| the shipped rootfs | ABSENT | — | Runtime packages, not user documentation. |

**Count:** 8 rows censused — the template's seven plus `vscode-docs`, which the item names: 2 FULL,
2 PARTIAL, 4 ABSENT. Every mature product separates install, launch and first use; a smaller
project keeps one page, not a docs tree. Which reference is authoritative for which question is in
[`../architecture/cross-cutting.md`](../architecture/cross-cutting.md).

## Implementation inventory

### jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12

| Concern | Files |
|---|---|
| Getting-started structure | `docs/source/getting_started/overview.md`, `docs/source/index.md` |
| Install guidance | `docs/source/getting_started/installation.md` |
| Launch guidance | `docs/source/getting_started/starting.md` |
| First document | `docs/source/user/notebook.md` |
| Supporting pages | `docs/source/getting_started/issue.md`, `docs/source/getting_started/faq.md`, `docs/source/getting_started/accessibility.md`, `docs/source/getting_started/lifecycle.md` |
| Feature depth | `docs/source/user/index.md` |

### vscode-docs@284f20ec9cfc6589f9505c1a5895056e728d355d

| Concern | Files |
|---|---|
| Getting-started navigation | `docs/toc.json` |
| Install and requirements | `docs/getstarted/overview.md`, `docs/supporting/requirements.md` |
| Product orientation | `docs/getstarted/what-is-vscode.md` |
| Hands-on first project | `docs/editing/getting-started/editor-tutorial.md` |
| Extra getting-started material | `docs/getstarted/introvideos.md`, `docs/getstarted/educators-and-students.md` |

### vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155

| Concern | Files |
|---|---|
| Quick start | `README.md` |
| Feature and command depth | `README.md` |

### winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40

| Concern | Files |
|---|---|
| Install and launch | `README.md` |
| Operational tips | `README.md` |

**Disagreement the item settles:** JupyterLab splits install, launch and first document across a
Get Started tree plus a User Guide; VS Code puts install, requirements, open-project and next steps
on one overview and keeps the hands-on tutorial separate; `vscode-jupyter` compresses the same arc
into a README Quick Start. JupyterDroid keeps the arc on one page because the site has one audience
path and OPS-012 owns per-feature pages.

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Structure | jupyterlab → `getting_started/` tree plus `user/` guide; vscode-docs → `toc.json` Get Started plus a separate editor tutorial; vscode-jupyter → one README Quick Start | More pages teach more but demand a docs tree OPS-012 has not built yet | One `getting-started.md` page; feature depth stays out of scope. |
| Install | jupyterlab → conda/mamba/uv/pip/pipenv/docker matrix; vscode-docs → per-OS installer tabs; winlator → one APK from Releases; ours → one arm64 APK | A matrix we cannot support adds failure modes | GitHub Releases arm64 APK only, optional `SHA256SUMS` check, `README.md#build-from-source` for contributors. |
| Launch model | jupyterlab → `jupyter lab` command and browser URL; vscode-docs → open folder/workspace trust; ours → Spaces home, Run/Open `base` | Server URLs and trust dialogs do not exist here | Document the Spaces home exactly as `SpacesHomeScreen.kt` and `open-base-space.yaml` prove it. |
| First document | jupyterlab → Launcher/notebook user guide; vscode-docs → files, terminal, preview, Git tutorial; vscode-jupyter → create notebook, select kernel | A desktop tutorial teaches editors we do not have | Files → New → New notebook on `Python 3`, type `print(6 * 7)`, Run cell, expect `42` — the release demo's own path. |
| Pictures | references embed many screenshots; ours → produced light/dark pairs only ([ADR-0170](../adr/0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md)) | Hand-taken frames cannot be told from produced ones | Reuse `release-install-demo.yaml`'s three states as the tutorial's frames; embed only inspected pairs. |
| Help and depth | jupyterlab → issue/faq/accessibility/lifecycle; vscode-docs → FAQ, requirements, educator paths | A getting-started page that answers everything becomes OPS-012 early | Keep a short next-steps pointer; troubleshooting and feature pages stay with OPS-012. |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Getting-started page | absent — `docs-site/pages/` holds only `index.md` | `jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12 docs/source/getting_started/overview.md`, `vscode-docs@284f20ec9cfc6589f9505c1a5895056e728d355d docs/getstarted/overview.md` | absent |
| Install path | present in delivery, absent in user docs — `.github/workflows/release.yml`, `docs/adr/0140-a-release-is-cut-from-conventional-commits-and-published-from-a-tag.md`; `README.md` still says there is no release | `vscode-docs@284f20ec9cfc6589f9505c1a5895056e728d355d docs/getstarted/overview.md`, `winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40 README.md` | present but unreachable from the site |
| First Space and notebook run | present — `app/src/main/java/com/jupydroid/app/ui/spaces/SpacesHomeScreen.kt`, `.maestro/open-base-space.yaml`, `.maestro/release-install-demo.yaml` | `jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12 docs/source/getting_started/starting.md`, `vscode-jupyter@f01d80ca0aa0dfdfa1d2e10a08943e12b776d155 README.md` | capability complete, tutorial absent |
| Produced images | present for the landing tour — `docs-site/pages/index.md`, `docs-site/pages/images/` | `vscode-docs@284f20ec9cfc6589f9505c1a5895056e728d355d docs/editing/getting-started/editor-tutorial.md` | producer exists, getting-started frames absent |
| Site navigation | present but only `Home` — `docs-site/mkdocs.yml` | `jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12 docs/source/index.md`, `vscode-docs@284f20ec9cfc6589f9505c1a5895056e728d355d docs/toc.json` | absent |

## Deviations

- No browser URL, server port, workspace trust, extension install, or Python environment setup: the
  runtime is bundled, loopback-only and arm64-only, which `index.md` already states.
- No videos, educator paths, AI/agent guidance, accessibility statement, lifecycle page, or FAQ:
  none of those exist here yet, and OPS-012 owns feature/troubleshooting pages.
- No new ADR is expected: [ADR-0169](../adr/0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)
  and [ADR-0170](../adr/0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md)
  already decide the site and its images. If T1–T3 surface a durable choice they do not cover, it
  becomes an ADR before this PRD is deleted ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Open decisions

- Which of `release-install-demo.yaml`'s three frames the tutorial keeps: settle in T2 by inspecting
  the produced pairs; the default is all three (`spaces-home`, `first-cell`, `first-output`) unless a
  frame proves misleading.
- Whether `SHA256SUMS` verification is required or optional in the install steps: settle in T1
  against the release assets and `scripts/release-install-demo-driver.sh`; default is optional verify.
- Whether the page links README's build-from-source section for contributors: settle in T1 by
  checking the anchor resolves and that it does not repeat the stale Install section.
- The nav label: settle in T1; default is `Getting started`, matching the backlog's wording.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — |
| `data/<mechanism>` | — |
| `data/fake` | — |
| `ui/<screen>` | — |
| `di/AppContainer` | — |
| unit tests | `scripts/docs_site_config_test.py` (existing nav contract; no change expected) |
| instrumented tests | — |
| `.maestro/` flow + `scripts/` driver | T2: `scripts/site-screenshots-driver.sh`, `.maestro/release-install-demo.yaml` header |
| docs site | T1: `docs-site/pages/getting-started.md`, `docs-site/mkdocs.yml`, `docs-site/pages/index.md`; T2/T3: `docs-site/pages/images/*-{light,dark}.png` |

## Tasks

One TDD unit each where code changes; documentation tasks end on the gates that read them. Small
enough that each takes well under an hour; if one does not, decompose it in this PRD before coding.

- [x] T1 — Add `docs-site/pages/getting-started.md`, add it to `docs-site/mkdocs.yml` `nav`, and link
  it from `docs-site/pages/index.md`; text-first install → launch → first Space → first notebook run
  using only verified labels, with the arm64/release caveat and a short next-steps pointer.
- [x] T2 — Extend `scripts/site-screenshots-driver.sh` so each theme pass resets, runs
  `.maestro/release-install-demo.yaml`, and crops its three screenshots into
  `docs-site/pages/images/{spaces-home,first-cell,first-output}-{light,dark}.png`; update the flow
  header to name the second consumer; run the driver on `SM-X216B` and inspect every frame.
- [ ] T3 — Embed the produced pairs in `getting-started.md` at the matching steps; rerun the strict
  build, `check_site_images.py`, `check_markdown_links.py`, and the docs-site tests.
- [ ] T4 — Ship: rerun the gates, delete this PRD, edit the single backlog row to `shipped`, and
  commit.

## Out of scope

- A page per feature area, troubleshooting, settings/themes, and the rest of the User Guide shape:
  OPS-012 owns them.
- Reconciling `README.md`'s stale Install section: not this item unless T1 proves the tutorial cannot
  link around it; if it must change, that is its own smallest docs fix.
- Release mechanics, signing, versioning, or publishing changes: ADR-0140 and the release workflow
  already own them.
- Videos, localization, an accessibility statement, and a lifecycle page: no reference gap here
  requires them for first use, and none are scheduled.
