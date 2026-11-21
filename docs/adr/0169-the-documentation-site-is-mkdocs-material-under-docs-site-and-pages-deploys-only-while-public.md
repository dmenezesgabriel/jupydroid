# ADR-0169 — The documentation site is MkDocs Material under docs-site/, built strict at commit and in CI, deployed to Pages only while the repository is public

Status: accepted
Date: 2026-11-21

## Context

The user documentation needs a home a pull request can break before it ships: a link to a page that does
not exist, or a page nobody added to `nav`, must fail a build rather than publish. `docs/` cannot be that
home — it is the engineering record (ADRs, architecture, the backlog), read by `check_doc_citations.py`
and `check_markdown_links.py` under their own rules
([ADR-0001](0001-split-durable-decisions-from-ephemeral-build-evidence.md)). GitHub Pages is the publish
target, and Pages on the free plan needs a public repository, which this one is not yet; the item that
ships the site ships no app capability
([ADR-0122](0122-work-that-ships-no-app-capability-is-an-ops-item.md)), and the repository's going public
is its own item.

The pattern comes from the maintainer's sibling `dmenezesgabriel/claude-acc-manager@5233d605`: MkDocs
Material, `mkdocs build --strict` as the merge-facing `docs` job, a separate `deploy` job holding
`pages: write` and `id-token: write`, both guarded on `visibility == 'public'`. Two facts were measured
before adopting it: on PyPI, `mkdocs-material` 9.8.0 was released 2026-10-24 — past the 7-day cooldown —
while `zensical` 0.1.0 came on 2026-11-05, inside it and pre-1.0; and Zensical reads `mkdocs.yml`
natively, so a config kept to plain keys keeps the move a drop-in. `mkdocs` itself last released
2024-08-30, which is why the generator choice is the theme's, not the core's.

## Decision drivers

- A broken link or an omitted page fails the build — `--strict` escalates the `validation` states from
  warn to error, locally and in CI.
- The dependency must not land in the environment every hook and unit test installs: `docs` is a
  dependency group outside `default-groups`
  ([ADR-0130](0130-python-dependencies-are-locked-and-verified-by-sha256.md)).
- Deploy waits for what Pages needs — a public repository, on a push to `main` — the guard
  [ADR-0138](0138-code-scanning-jobs-wait-for-a-public-repository-and-the-rest-block-in-plain-text.md)
  already uses for jobs that cannot run while private.
- Only `deploy` holds `pages: write` and `id-token: write`; the build job is `contents: read` end to end.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Sphinx (`jupyterlab`'s generator) | Batteries included | A heavier config for a small site; the sibling's pattern is MkDocs |
| Zensical now | The successor Material points at | 0.1.0, released inside the cooldown; pre-1.0 |
| **MkDocs Material, plain `mkdocs.yml`** | Past the cooldown, pinned by `uv.lock`; Zensical reads the same config, so the move stays a drop-in | Material is in maintenance mode — accepted: the config is the durable artifact, not the theme |
| Site source in `docs/` | One docs tree | `docs/` is the engineering record with its own citation gates; user pages have different readers and lifetimes |
| **`docs-site/` holding `mkdocs.yml` and `pages/`** | A separate tree under separate rules; links to `docs/` go by GitHub URL | Two places documentation lives — the site is for users, `docs/` for builders |
| `docs` group inside `default-groups` | One `uv sync` | Every hook and test environment installs mkdocs-material for a build most commits never run |
| **Own `docs` group, named by `uv run --group docs`** | Only the build and its gates pay for it | The command must name the group — the hook and the workflow both do |
| Deploy unconditionally | Simpler workflow | Fails on every push while the repository is private |
| **Guard on `push && visibility == 'public'`** | Activates the day Pages can work, like ADR-0138's jobs | The deploy path is unexercised until the repository is public |

## Decision

**The user documentation site is MkDocs Material (`mkdocs-material>=9.8,<10`, locked by `uv.lock`) built
from `docs-site/mkdocs.yml` over `docs-site/pages/`, with an explicit `nav` and `validation` states that
`--strict` turns into errors; `mkdocs build --strict` is the merge-facing `docs` job in
`.github/workflows/docs.yml` and the `docs-site-build` commit hook in `.pre-commit-config.yaml`, scoped
to the files the build reads; and a `deploy` job publishes `build/site` to GitHub Pages on pushes to
`main` while `github.event.repository.visibility == 'public'`.**

- The config stays plain Material — `theme.name: material`, `plugins: [search]`, no `custom_dir`, no
  third-party plugin — so a move to Zensical is a drop-in (`scripts/docs_site_config_test.py` asserts
  both). `NO_MKDOCS_2_WARNING=1` silences the vendor banner the theme prints on every invocation, in the
  workflow's env and the hook's entry.
- `site_dir` is `../build/site`, inside the gitignored `/build`.
- The hook's `files` pattern is `^(docs-site/|pyproject\.toml$|uv\.lock$)` — the site source and the
  lock that pins the generator; a commit touching none of them cannot change the built site, the same
  scoping rule the Gradle hooks follow
  ([ADR-0151](0151-the-gradle-hooks-run-when-a-commit-touches-what-gradle-reads.md)).
- The site links to the engineering documents by their GitHub URL: `docs-site/` and `docs/` are
  separate trees, and the engineering documents keep their ids and lifetimes.

## Consequences

- A page outside `nav`, a link to a file that does not exist, or a flagged absolute link fails
  `mkdocs build --strict` — at commit when the change touches the site or its lock, in the `docs` job on
  every pull request, and in CI's `pre-commit run --all-files`, which runs the hook over the whole tree.
- The pages the site will hold arrive as items of their own; this decision fixes only where they live
  and what holds them honest.
- A fork's Pages build publishes to the fork's own Pages: `site_url` and `repo_url` name this repository
  and nothing guards the name, as a release's metadata does not need to.
- The thing a future reader will be tempted to "fix" is the visibility guard or the `docs` group:
  removing the guard fails every private-repository push, and folding the group into `default-groups`
  makes every hook install the site generator. Both are load-bearing.
- Reproduce: `uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml` builds the
  site, `pre-commit run docs-site-build --all-files` is the hook, and
  `grep -rn "ADR-0169" docs/ app/src/ scripts/` finds the citations.
