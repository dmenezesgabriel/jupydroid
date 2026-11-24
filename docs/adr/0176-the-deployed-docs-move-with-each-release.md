# ADR-0176 — The deployed docs move with each release: the deploy follows `v*` tags and the site names the release it documents

Status: accepted
Date: 2026-11-24

## Context

[ADR-0169](0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)
gave the user documentation site a home and a deploy, but the deploy fired on `main` pushes only —
nothing tied the published site to the release it documents, and
`docs-site/pages/getting-started.md` carried `jupydroid-<version>-arm64-v8a.apk` as a literal
placeholder that never resolved. The release machinery already cuts tags:
`.github/workflows/release-tag.yml` pushes `vX.Y.Z` with the app token on a `chore(release)` merge,
because a tag pushed with `GITHUB_TOKEN` starts no workflow — and `docs.yml`'s deploy guard is
`github.event_name == 'push' && visibility == 'public'`, which a tag push already satisfies.

Two facts were measured before choosing the mechanism. MkDocs 1.6.1 renders no template variables
in page markdown — `Page.render` is `md.convert(self.markdown)`, verified in the pinned
`.venv/lib/python3.14/site-packages/mkdocs/structure/pages.py` — so `{{ ... }}` would ship
literally and `!ENV` feeds only config values, never page source. And `mkdocs`' own `hooks:` config
option loads a Python file as a first-class extension point
(`mkdocs/config/config_options.py`, `Hooks`), which leaves `plugins: [search]` untouched and the
Zensical drop-in [ADR-0169](0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)
asserts intact.

## Decision drivers

- A page that names a release must name the live one: the earliest durable signal a release exists
  is the tag push itself, not the GitHub Release `release.yml` publishes afterwards.
- Degradation must be today's shipped text: an unresolved version leaves `<version>` literal —
  exactly what the page already said — never a broken build.
- One mechanism serves local `mkdocs build`/`serve` and CI: a `hooks:` entry runs in both, where a
  workflow step rewriting the page would deploy a document the repository does not hold.
- Deploy inputs are untrusted text: `github.ref_name` is a string a person can push, so it reaches
  the step through `env`, never interpolated into `run:`.
- The build that publishes the site must not read a cache a lower-trust run could poison: every
  push `docs.yml` serves publishes, so it is a release workflow end to end (zizmor's
  cache-poisoning audit).

## Options considered

| Option | Pro | Con |
|---|---|---|
| Deploy on the `release: published` event | Fires when the Release exists | Couples the docs deploy to `release.yml`'s publish step and timing; the site names a tag string and needs no Release object |
| `workflow_run` chained after `release.yml` | Exact ordering | Heavier and indirect, and still only names a tag |
| Version via `extra:`/`!ENV` or `site_name` | Native config mechanism | Feeds templates and config only; page markdown renders no Jinja — `Page.render` verified |
| Generated include (`pymdownx.snippets`) | No code | The generated file must exist for local builds, re-creating the drift a hook avoids |
| A workflow step rewriting the page | Simple shell | Deploys a document the repo does not hold; local builds diverge from CI |
| Token `{{ release_version }}` | Reads like a template | MkDocs never runs Jinja in pages — ships literally |
| **`<version>` placeholder + a `hooks:` entry** | Already the page's filename placeholder; unsubstituted it is today's text; one mechanism for local and CI | A future Zensical that cannot run Python hooks leaves it literal — accepted: that is today's behaviour, not breakage |
| Resolved release in module state | Simpler | `mkdocs serve` rebuilds would go stale; `config.extra` is per-build |
| Non-tag deploy names `unreleased` or nothing | Honest about provenance | Every deploy can name a real, installable release — the latest published one |
| Hook under `scripts/` reached by `../` | Inside ruff/mypy scope | Needs a `../` in `hooks:` and a `files:` pattern extension; `docs-site/` is the site's own directory and the `docs-site-build` hook already covers it |
| uv cache enabled, disabled only on tags | Faster builds | Every push this workflow serves publishes the site — `enable-cache: false` outright |

## Decision

**`docs.yml`'s `on.push` gains `tags: ["v*"]` — a tag push is `event_name == 'push'`, so the deploy
guard needs no change and the site deploys from the tagged commit. A "Stamp the release the site
documents" step runs before the strict build: on a tag push it writes the tag to
`JUPYDROID_DOCS_RELEASE`; otherwise it writes `gh release view --json tagName`'s latest published
release, with `|| true` so a `gh` failure cannot fail the required `docs` check.
`docs-site/hooks/release_version.py` — wired as `hooks:` in `mkdocs.yml`, mkdocs core, not a
plugin — resolves the release in `on_config` (`JUPYDROID_DOCS_RELEASE`, then
`git describe --tags --abbrev=0`, then nothing) into `config.extra["release_version"]`, and
`on_page_markdown` replaces every `<version>` placeholder. `getting-started.md` names the release:
"Release `<version>` publishes one APK, `jupydroid-<version>-arm64-v8a.apk`, …".**

- The resolution order is env → `git describe` → unresolved: the workflow always stamps the env on
  a deploy, `git describe` answers a local build in a clone holding tags, and unresolved leaves the
  placeholder — degradation is the text the page already shipped.
- `config.extra` rather than module state keeps the resolution per-build: `mkdocs serve` re-runs
  `on_config` on reload.
- `setup-uv` runs `enable-cache: false`: a tag push makes this a release workflow, and no cache
  entry a lower-trust run wrote may flow into what Pages serves.
- A `v*` tag that is not `vX.Y.Z` still deploys the docs naming it; `release.yml`'s
  `scripts/release_version.py` is what refuses non-semver tags for the release itself, and a docs
  deploy naming a stray tag is harmless.

## Consequences

- The verification this decision owes is a live one: the next `v*` tag's `docs.yml` run must show
  the `docs` job stamping that tag and the Pages site's getting-started page naming it —
  `gh run list --workflow=docs.yml` and a curl of the Pages URL. Until that run is observed the
  item is open, however green the local gates are ([ADR-0122](0122-work-that-ships-no-app-capability-is-an-ops-item.md)).
- Every docs deploy now names a real release: a tag deploy its own tag, a `main` merge the latest
  published one. A repository with no releases yet falls through `gh`'s failure to `git describe`,
  then to the literal placeholder.
- The thing a future reader will be tempted to "fix" is the `enable-cache: false` or the visibility
  guard: both are load-bearing — the first against cache poisoning on a release workflow, the
  second because Pages needs a public repository ([ADR-0169](0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)).
- If Zensical cannot run Python hooks, `<version>` stays literal — today's text — and the fix is a
  Zensical-native equivalent, not a return to Jinja that never ran.
- Reproduce: `uv run --locked python -m unittest discover -s scripts -p 'docs_site_release_test.py'`
  and `-p 'docs_site_config_test.py'` are the hook and wiring gates;
  `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` builds the site, and
  `grep 'Release <code>' build/site/getting-started/index.html` shows the resolved version;
  `JUPYDROID_DOCS_RELEASE=v9.9.9-test` before the same build shows the env path;
  `grep -rn "ADR-0176" docs/ app/src/ scripts/ .github/` finds the citations.
