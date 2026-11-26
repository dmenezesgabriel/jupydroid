# ADR-0175 — The front door embeds the site-shot pairs as `<picture>` and keeps no screenshot producer of its own

Status: accepted
Date: 2026-11-24

## Context

The README's two screenshots were light-only frames under `docs/images/`, produced on 2026-11-03 by
`scripts/readme-screenshots-driver.sh` — a second producer beside the site-shot suite
([ADR-0170](0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md)), which captures
every picture the documentation site uses as `-light`/`-dark` pairs under
`docs-site/pages/images/` on the tablet (`SM-X216B`). The suite's pairs are the same two frames in
both themes, produced 2026-11-21 and -22 against the current UI, so the front door's pictures were
stale and single-theme while newer, theme-complete ones sat one tree away.

GitHub renders a README's raw HTML, and its documented mechanism for a picture that follows the
reader's theme is `<picture>` with `prefers-color-scheme` sources — the "Adding an image to suit
your visitors" section of docs.github.com's writing quickstart, announced by github.blog's
2022-08-15 changelog. The older `#gh-light-mode-only`/`#gh-dark-mode-only` URL fragments are
deprecated: GitHub's documentation dropped them when the `<picture>` section was rewritten
(<https://github.com/github/docs/pull/35832>, merged 2025-01-06).

[ADR-0169](0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)
keeps `docs/` and `docs-site/` separate because MkDocs serves only `docs_dir` (`pages/`); it never
forbade an inbound reference, and `scripts/check_markdown_links.py` reads both trees as one
repository. [ADR-0136](0136-the-front-door-borrows-its-text-and-routes-by-github-not-by-address.md)
set the front door's image conventions — a committed image is a published image, cropped past the
status bar, its paths gated — and its "frames under `docs/images/`" clause described the layout
this change retires.

## Decision drivers

- One producer per frame: two drivers capturing the same screens drift, which is the failure the
  site-shot suite exists to prevent ([ADR-0170](0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md)).
- Each reader sees their theme: the pairs exist for both schemes, and the mechanism must select
  between them on github.com, where Material's `#only-light`/`#only-dark` fragments mean nothing.
- A path in raw HTML is still a path: a `srcset` typo renders a 404 on github.com while a
  Markdown-only link check stays green, so the gate must read HTML embeds to keep them honest.
- Referencing beats copying: a second copy of the same pixels is the drift a single producer
  removes.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Duplicate the pairs into `docs/images/` | Each tree self-contained | Two copies of the same pixels drift; a second producer returns |
| Move the pairs to `docs/images/` and repoint the site | One image tree | MkDocs serves only `docs_dir`; every site embed breaks ([ADR-0169](0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md)) |
| `#gh-light-mode-only`/`#gh-dark-mode-only` fragments | Plain `![]()` syntax | Deprecated by GitHub and dropped from its docs; will stop selecting |
| One image, no theme pair | Simplest markup | A dark-mode reader gets a light frame; the pairs already exist |
| Keep `readme-screenshots-driver.sh` producing `docs/images/` | No cross-tree reference | A light-only producer for frames nothing references — dead weight that looks alive |
| **Reference the committed pairs as `<picture>`; retire the README driver** | One producer, both themes, gated paths | The front door reaches into `docs-site/pages/images/` — an inbound reference across the two-tree boundary |

## Decision

**`README.md` embeds the site-shot pairs — `docs-site/pages/images/notebook-with-outputs-{light,dark}.png`
and `docs-site/pages/images/html-table-output-{light,dark}.png` — as two `<picture>` blocks: a dark
`<source>`, a light `<source>`, and an `<img>` fallback carrying the light file.
`scripts/check_markdown_links.py` holds `src` and `srcset` on `img`/`source` tags to the same
tracked-file rule as `[text](target)`. `scripts/readme-screenshots-driver.sh` and the two
`docs/images/*.png` frames are retired; `.maestro/readme-screenshots.yaml` names
`scripts/site-screenshots-driver.sh` its only consumer.**

- The `<img>` fallback carries the light file, so a renderer without `prefers-color-scheme` still
  shows a correct picture.
- ADR-0169's separation is about what MkDocs serves, not what the repository may reference: the
  link gate reads both trees as one repository, so an inbound path resolves like any other.
- A `srcset`'s comma-separated candidates are each checked; absolute addresses and non-path values
  are skipped, exactly as for links.

## Consequences

- Adding a front-door picture is adding a site-shot flow frame and a `<picture>` block; no second
  producer can enter the tree unnoticed, because nothing else writes `docs-site/pages/images/`.
- Freshness stays procedural ([ADR-0170](0170-the-sites-screenshots-are-produced-by-one-command-on-the-tablet.md)):
  CI cannot reach the tablet, so a rerun is a person or agent job whose command the commit body
  names ([ADR-0122](0122-work-that-ships-no-app-capability-is-an-ops-item.md)).
- `docs/images/` keeps only `icons/` — launcher art another item owns; no screenshot frame lives
  there, so ADR-0136's "frames under `docs/images/`" clause describes the retired layout. The
  cropping and secrets checks now run inside the site-shot driver
  (`scripts/crop_system_bars.py`, and `scripts/check_device_secrets.py` reads every tracked file).
- The thing a future reader will be tempted to "fix" is the cross-tree reference or the duplicated
  image trees: the separation is ADR-0169's decision about what MkDocs serves, and referencing
  beats copying because copies drift.
- Reproduce: `uv run --locked python scripts/check_markdown_links.py` exits 0,
  `uv run --locked python scripts/check_site_images.py` exits 0,
  `gh api markdown -f mode=gfm -f text="$(cat README.md)"` renders two `<themed-picture>` blocks,
  `git grep -n "docs/images/" -- README.md` finds nothing, and
  `grep -rn "ADR-0175" docs/ app/src/ scripts/` finds the citations.
