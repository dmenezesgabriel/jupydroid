# OPS-022 — The deployed docs move with each release

Milestone: `M10` · State: open · Depends on: OPS-020 (shipped) · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A `v*` tag push redeploys the documentation site built from that tag, and the getting-started
page names the release the build documents — the tag itself on a tag deploy, the latest
published release on a `main` deploy — so the site a visitor reads always names a release
that is live.

## Demo

An OPS item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): no
screen of the app changes — the site is not the app — so the demo is a run anyone can repeat,
not a Maestro script.

1. `JUPYDROID_DOCS_RELEASE=v9.9.9-test uv run --locked --group docs mkdocs build --strict -f
   docs-site/mkdocs.yml` then `grep -o "v9.9.9-test" build/site/getting-started/index.html`
   answers matches — the hook substitutes the env-provided release into the page.
2. `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` in a clone
   holding tags resolves `git describe --tags --abbrev=0` — `grep -o "v0.3.1"
   build/site/getting-started/index.html` answers matches (the nearest ancestor tag), proving
   the fallback without CI.
3. The tag deploy itself, which only a real tag can produce: after the next `v*` tag is cut,
   `gh run list --workflow=docs.yml --event push` shows a run on the tag whose `deploy` job is
   `success`, and `curl -fsS https://dmenezesgabriel.github.io/jupydroid/getting-started/ |
   grep -o "vX.Y.Z"` serves that tag — the check the backlog row names.

## Who else implements this

Every app-codebase reference is ABSENT — none carries this repository's docs pipeline. The one
row with a real answer is the sibling whose `docs.yml` this one was copied from, read live
today: `dmenezesgabriel/claude-acc-manager@5233d605` `.github/workflows/docs.yml` deploys on
`main` pushes only, with no tag trigger and no version mechanism — the same gap this item
closes here.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | ABSENT | — | no per-release docs deploy of this shape |
| `vscode@3f3380c668d0` | ABSENT | — | same |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | — | same |
| `intellij-community@406a27a8da07` | ABSENT | — | same |
| `termux-app@3df69d1da197` | ABSENT | — | same |
| `winlator@fb66541b93a4` | ABSENT | — | same |
| `claude-acc-manager@5233d605` | ABSENT | `.github/workflows/docs.yml` | identical main-only deploy, verified via `gh api` today |
| the shipped rootfs | ABSENT | — | the runtime ships no documentation site |

**Count:** 0 of 8. The mechanism is this repository's own delivery concern; the references
answer nothing here.

## Implementation inventory

No reference implementation exists to port — the item rearranges our own deploy. What the
executing session must open:

| Concern | Files |
|---|---|
| Deploy workflow | `.github/workflows/docs.yml` — `on.push` gains `tags: ["v*"]`; the `docs` job gains a version-stamp step before the strict build; the deploy path needs no `if` change because a tag push is `event_name == 'push'` |
| Version resolution | `docs-site/hooks/release_version.py` (new) — `JUPYDROID_DOCS_RELEASE` env, then `git describe --tags --abbrev=0`, then unresolved |
| Page substitution | the same hook's `on_page_markdown` replaces `<version>` in page source before `md.convert` |
| Site wiring | `docs-site/mkdocs.yml` `hooks:` entry; `docs-site/pages/getting-started.md` Install section |
| Gates | `scripts/docs_workflow_test.py` (trigger + stamp step), `scripts/docs_site_release_test.py` (new — hook behavior), `scripts/docs_site_config_test.py` (the `hooks:` entry names a real file) |
| Durable record | `docs/adr/0176-*.md` (new), `docs/backlog.md` row, this PRD (deleted on ship) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Deploy trigger | `push.tags: ["v*"]`; `release: [published]`; both | the tag push is earlier — the site is live without waiting for `release.yml`'s rootfs/APK/publish — and carries `ref_name`; a `release` event only fires after the whole release build | `push.tags: ["v*"]` — the same pattern `release.yml` uses; a tag push is `event_name == 'push'`, so the existing `visibility == 'public'` guard on `configure-pages`, `upload-pages-artifact` and `deploy` covers it with zero `if` changes |
| Version into page content | mkdocs `hooks:` + `on_page_markdown`; `!ENV` into `extra`/`site_name`; a generated include (`pymdownx.snippets`); a workflow step rewriting the page | `!ENV` only feeds config values, and page markdown renders no Jinja — `Page.render` in mkdocs 1.6.1 is `md.convert(self.markdown)`, verified in `.venv`; a generated include must exist for local builds, re-creating the drift; rewriting the page in CI deploys a document the repo does not hold | `hooks:` — mkdocs core (`config_options.Hooks`, each entry a `File(exists=True)` resolved under `docs-site/`), one mechanism for local and CI builds, and `plugins:` stays `["search"]` so the Zensical drop-in assertion in `docs_site_config_test.py` holds |
| Token | `<version>`; `{{ release_version }}`; an HTML comment | `<version>` is already the page's filename placeholder and reads as a template when unsubstituted — degradation is today's text, not breakage; `{{ }}` looks like Jinja mkdocs never runs; a comment vanishes silently | `<version>` — the hook replaces every occurrence; unresolved leaves the placeholder, which is exactly what ships today |
| Non-tag deploy version | latest published release (`gh release view`); `unreleased`; nothing | `main` merges deploy far more often than tags fire; naming nothing wastes the mechanism; `unreleased` is honest but a live release exists | latest published release — every deploy names a real, installable release; `|| true` keeps a `gh` failure from failing the required `docs` check, and the hook's `git describe` then placeholder chain absorbs it |
| Hook home | `docs-site/hooks/`; `scripts/` via a `../` path | `docs-site/` is covered by the `docs-site-build` hook's `files:` pattern and colocates the site's machinery; `scripts/` would gain ruff/mypy coverage but needs a `../` path in `hooks:` and a `files:` pattern extension | `docs-site/hooks/` — the site's own directory; its behavior is test-covered from `scripts/docs_site_release_test.py` |
| Commit type for T3 | `feat`; `docs` | `feat` bumps the version — `pyproject.toml` `[tool.semantic_release]` releases on `feat`/`fix`/`perf` and nothing for `docs`/`ci`/`chore` — so the item produces the very tag whose deploy verifies it | `feat(docs-site)` — honest (a new site capability) and it makes the next release the verification vehicle |

## Gap analysis — what we already have vs what the item needs

| Concern | Ours today (file) | Needed | Gap |
|---|---|---|---|
| Tag deploy | `docs.yml` `on.push.branches: [main]` only | `tags: ["v*"]` too | absent |
| Version source | none — the page carries `<version>` placeholders | resolved per deploy | absent |
| Page naming the release | `getting-started.md` generic placeholders | the concrete `vX.Y.Z` | present but static |

## Deviations

- The version resolves to the tag on a tag deploy even before `release.yml` publishes the GitHub
  Release — the deploy documents the tag, not the Release object, so the race is benign.
- `v*` matches non-semver tags; `scripts/release_version.py` already refuses those for the
  release side, and a docs deploy stamping `vfoo` is harmless.
- A future Zensical may not run Python hooks: `<version>` then stays literal — today's
  behavior, not breakage. The ADR records it.

## Open decisions

None — display placement (the getting-started page) and non-tag semantics (the latest published
release) were put to the maintainer and answered before this PRD was written.

## Surface

| Layer | Files |
|---|---|
| workflow | `.github/workflows/docs.yml` |
| site config | `docs-site/mkdocs.yml` (`hooks:`) |
| site hook | `docs-site/hooks/release_version.py` (new) |
| site page | `docs-site/pages/getting-started.md` |
| tests | `scripts/docs_workflow_test.py`, `scripts/docs_site_release_test.py` (new), `scripts/docs_site_config_test.py` |
| durable record | `docs/adr/0176-*.md` (new), `docs/backlog.md`, this PRD (deleted on ship) |

No `domain`, `data`, `ui` or `di` change: this item ships no port and no app code.

## Tasks

One TDD unit each where code is written (red → green → refactor), one conventional commit each.

- [ ] T1 — this PRD.
- [ ] T2 — `docs.yml`: `tags: ["v*"]` under `on.push`, the version-stamp step before "Build site
  (strict)", and the header comment's "on main pushes and release tags"; red → green in
  `docs_workflow_test.py` (the trigger assertion gains the tags filter; a new assertion holds the
  stamp step's `ref_type == 'tag'` → `ref_name`, else `gh release view`, `|| true` shape).
  Commit `ci(docs)`.
- [ ] T3 — the hook: `docs-site/hooks/release_version.py`, the `hooks:` entry in `mkdocs.yml`,
  and getting-started's `<version>` release naming; red → green in
  `docs_site_release_test.py` (env precedence, `git describe` fallback, unresolved leaves
  `<version>` untouched, `on_page_markdown` substitution, `on_config` stores `config.extra`).
  Verify: a stamped build greps the version out of `build/site/getting-started/index.html`; a
  build with resolution impossible keeps `<version>`. Commit `feat(docs-site)` — it bumps the
  version, so the release it triggers is the tag deploy this item is verified by.
- [ ] T4 — ADR-0176 (the deploy follows `v*` tags; the resolution order; the `<version>`
  contract; the Zensical degradation; cites ADR-0169), the backlog row, this PRD deleted — after
  the next tag's deploy is observed serving that tag (`gh run list --workflow=docs.yml` and the
  Pages curl). If no tag has been cut when the code is otherwise done, the row stays open naming
  the pending check rather than claiming shipped.

## Out of scope

- `release.yml`, `release-tag.yml`, `release-cut.yml` — the release machinery is untouched; the
  tag it already produces is the trigger.
- Other pages naming the release — getting-started is the page that installs the release; a
  second consumer is a later item's choice.
- `site_name` and header branding — considered and rejected (Trade-offs).
- Pushing — the loop's machinery syncs; CI evidence is read with `gh`.
