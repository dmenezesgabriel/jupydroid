# OPS-009 — A documentation site on GitHub Pages

Milestone: `M12` · State: in-progress · Depends on: — (the deploy waits for `OPS-005`, the build does not) · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A pull request that breaks a link or leaves a page out of the navigation fails a strict site build, and a
push to `main` publishes the site to GitHub Pages once the repository is public. Pages for users
(`OPS-011`, `OPS-012`) then have somewhere to go.

## Demo

No device flow ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)). It closes on:

1. `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` exits 0 and writes
   `build/site/index.html`.
2. The same command exits non-zero when a page links to a file that does not exist, and when a page is
   missing from `nav`.
3. A green `docs` job on a pull request, and a `deploy` job that is skipped while the repository is private.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | FULL (not Pages) | `docs/source/`, `.readthedocs.yml` | A Sphinx site built and hosted by Read the Docs; `.github/workflows/` has no docs or Pages workflow |
| `termux-app@3df69d1da197` | PARTIAL | `docs/en/index.md` | One 13-line Markdown page and no workflow that builds or deploys it |
| `intellij-community@406a27a8da07` | ABSENT | `docs/` | Developer notes (`plugin.md`, PlantUML sources), no site build in the repository |
| `vscode@3f3380c668d0` | ABSENT | | No docs or Pages workflow in `.github/workflows/`; the docs live on Microsoft's own site |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | | No docs workflow in `.github/workflows/` |
| `winlator@fb66541b93a4` | ABSENT | `.github/` | `FUNDING.yml` and an issue template only |
| `claude-acc-manager` (sibling, `5233d605`) | FULL | `mkdocs.yml`, `.github/workflows/docs.yml`, `pyproject.toml` `[dependency-groups] docs` | MkDocs Material, `mkdocs build --strict` as the merge-facing `docs` job, a separate `deploy` job holding `pages: write` and `id-token: write`, both guarded on `visibility == 'public'` |

**Count:** 1 of the 6 reference projects ships a docs site from its own repository, and it builds on Read the Docs, not
Pages. The sibling supplies the pattern the backlog names. The generator, the directory and the deploy guard
are decided here.

## Implementation inventory

### `claude-acc-manager@5233d605`

| Concern | Files |
|---|---|
| Config | `mkdocs.yml`: `theme.name: material` with three palette entries, `plugins: [search]` only, `exclude_docs`, a `validation` block that turns an omitted file or broken link into an error under `--strict`, an explicit `nav` |
| Build and deploy | `.github/workflows/docs.yml`: `docs` (contents: read; `mkdocs build --strict`), `deploy` (`needs: docs`; `pages: write`, `id-token: write`; `if: push && visibility == 'public'`); `NO_MKDOCS_2_WARNING` silences a vendor banner |
| Dependency | `pyproject.toml` `[dependency-groups] docs = ["mkdocs-material>=9.7,<10"]`, run with `uv run --group docs` |

**Disagreement to settle:** the sibling's site is its repository's `docs/` directory. Ours is not available:
`docs/` holds the engineering documents (ADRs, architecture, the backlog), which are not for users and
keep their ids and lifetimes ([`../README.md`](../README.md)).

### Our own guard

`.github/workflows/security.yml` `codeql`: `if: ${{ github.event.repository.visibility == 'public' }}`, the
pattern [ADR-0138](../adr/0138-code-scanning-jobs-wait-for-a-public-repository-and-the-rest-block-in-plain-text.md) names.
`scripts/workflow_reader.py` and `scripts/release_publish_workflow_test.py` are how a workflow is held to a rule.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Generator | MkDocs Material (sibling); Sphinx (`jupyterlab`); Zensical | Measured on PyPI, 2026-11-06: `mkdocs-material` 9.8.0 released 2026-10-24 (13 days, past the 7-day cooldown), `requires-python >=3.11`, its project URLs list "Maintenance" pointing at Zensical's migration page. `mkdocs` 1.6.1 was last released 2024-08-30. `zensical` is 0.1.0, released 2026-11-05, and 0.0.74 came on 2026-10-30: inside the cooldown, and pre-1.0 | MkDocs Material, `>=9.8,<10`. Zensical reads `mkdocs.yml` natively, so keeping the config to plain keys (no third-party plugin, no `custom_dir`) leaves the move a drop-in |
| Source directory | the repository's `docs/` (sibling); a new directory | `docs/` is the engineering record and is read by `check_doc_citations.py` and `check_markdown_links.py` under their own rules | `docs-site/`, holding `mkdocs.yml` and `pages/`. The site links to the engineering documents by their GitHub URL, since `docs-site/` and `docs/` are separate trees |
| Where the dependency lives | the `dev` group; its own group | `default-groups = ["host", "dev"]` installs every default group into the environment every hook and test uses | A `docs` group outside `default-groups`, run with `--group docs`, so no hook or unit test installs it |
| Build output | `site/` (MkDocs default); `build/site` | `/build` is already gitignored | `site_dir: ../build/site` |
| Deploy guard | always; `visibility == 'public'` | Pages needs a public repository on the free plan | `github.event_name == 'push' && github.event.repository.visibility == 'public'`, on the upload step and the `deploy` job, as the sibling and ADR-0138 do |
| Checks the config may skip | none; some | A page absent from `nav` is silently hidden | `nav` is explicit and `validation` states `omitted_files: warn`, `absolute_links: warn`; `--strict` makes them errors |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Site source, config | none | sibling `mkdocs.yml` | absent |
| Docs dependency group | `pyproject.toml` has `host` and `dev` only | sibling `pyproject.toml` | absent |
| Build and deploy workflow | `.github/workflows/` has `ci`, `release*`, `scorecard`, `security` | sibling `docs.yml` | absent |
| Strict build as a commit gate | `.pre-commit-config.yaml` has no docs hook | the sibling runs the same command in its hook | absent |
| Link check over Markdown | `scripts/check_markdown_links.py` over tracked files | | present, and it reads the new pages too |
| Markdown quirks the generator reads | | | `check_evidence_citations.py` and `check_retired_doc_citations.py` read all tracked Markdown, so the pages obey them |

## Deviations

- No `docs/` reuse (above).
- No generated reference pages and no screenshots in this item: `OPS-010` owns the screenshots, `OPS-011` and
  `OPS-012` the pages.
- The site's `site_url` and `repo_url` are those of `dmenezesgabriel/jupydroid`; a fork's Pages build is a
  fork's own, so nothing here guards the repository name as `release.yml` does.

## Open decisions

| Decision | Evidence to gather | Task |
|---|---|---|
| ~~Whether `mkdocs-material` 9.8.0 resolves and builds under `requires-python >=3.14`~~ **Decided in T1:** it does. `uv lock` added 16 packages, four of them younger than 7 days and locked one release back, as `python-semantic-release`'s were: `backrefs` 8.1 (8.2 is 3 days old), `markdown` 3.11.2 (3.11.3, 1 day), `python-dateutil` 3.0.0 (4.0.0, 5 days) and `watchdog` 6.0.0 (7.0.0 and 7.1.0 are both inside the window). Locking `python-dateutil` 3.0.0 pulled in `six` 1.17.0, from 2024-12. `mkdocs build --strict` passes, and exits 1 on a link to a missing page and on a page outside `nav` | | T1 |
| ~~The site's name and landing copy~~ **Decided in T1:** `JupyterDroid`, and a landing page that restates the README's first paragraph and its arm64-only note, with no screenshot (`OPS-010` owns those) | | T1 |
| ~~Which action versions the workflow pins~~ **Decided in T2:** the sibling's pins, each re-read at its tag on 2026-11-21 and still the latest release — `actions/configure-pages` v6.0.0, `actions/upload-pages-artifact` v5.0.0, `actions/deploy-pages` v5.0.1 — pinned by full SHA beside checkout v7.0.1 and setup-uv v10.3.3, the versions `ci.yml` already carries ([ADR-0123](../adr/0123-dependencies-are-locked-strict-and-verified-by-sha256.md)) | | T2 |

## Surface

| Layer | Files |
|---|---|
| site | `docs-site/mkdocs.yml`, `docs-site/pages/index.md` (T1) |
| dependency | `pyproject.toml` `[dependency-groups] docs`, `uv.lock` (T1) |
| workflow | `.github/workflows/docs.yml` (T2) |
| gate | `.pre-commit-config.yaml` `docs-site-build` (T3) |
| unit tests | `scripts/docs_site_config_test.py` (T1), `scripts/docs_workflow_test.py` (T2) |

No `domain`, `data`, `ui` or `di` change: this item ships no port.

## Tasks

- [x] T1 — `docs-site/` with a landing page and a `docs` dependency group; the strict build passes; a test holds the config to the rules above (explicit `nav` naming every page, `validation` states, `site_dir` under `build/`)
- [x] T2 — `docs.yml`: the strict build on every pull request and push, the Pages upload and `deploy` guarded on a public repository; a workflow test (pinned actions, token scopes, the guard)
- [ ] T3 — the same strict build as a commit hook; ADR-0142 records the generator, the directory and the deploy guard; delete this PRD; the backlog row to shipped

## Out of scope

Making the repository public (`OPS-005`); the screenshots (`OPS-010`); the getting-started tutorial and the
feature pages (`OPS-011`, `OPS-012`); a custom domain; versioned docs; a search plugin beyond the built-in one.
