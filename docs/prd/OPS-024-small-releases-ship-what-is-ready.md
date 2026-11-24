# OPS-024 — Small releases ship what is ready

Milestone: `M10` · State: open · Depends on: — · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A reader of `docs/` finds the release cadence written down: after each item that ships
user-visible change, the standing `release/next` pull request is merged — a release is a merge
and a tag away — rather than a milestone's worth of changes accumulating before one cut.

## Demo

An OPS item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): no
screen of the app changes, so the demo is a run anyone can repeat, not a Maestro script. The
verification the backlog row names is the tag history itself:

1. `git tag --list 'v*' --sort=-v:refname` answers `v0.3.1`, `v0.3.0`, `v0.2.0`, `v0.1.1`,
   `v0.1.0` — five releases in eighteen days, two of them the same day, which is the cadence the
   doc prescribes already happening.
2. `gh release list --limit 10` shows each tag carrying a published Release, and
   `gh pr list --head release/next --state all` shows the standing pull request being merged
   (`v0.3.1`, `v0.3.0`, `v0.2.0`) or closed unmerged (`v0.2.1`) — the merge is the release.
3. The doc itself: `docs/releasing.md` exists, `docs/README.md`'s Need|Read table names it, and
   `scripts/check_markdown_links.py` passes over every tracked Markdown file.

## Who else implements this

Every reference gets a row. The question here is not a code feature but a delivery practice, and
no reference carries this repository's release machinery — the three workflows are this repo's
own, patterned on the sibling `dmenezesgabriel/claude-acc-manager@5233d605`, whose
`CONTRIBUTING.md` says only that "release automation will parse" conventional commits and whose
`docs/` holds no release-cadence document (checked via `gh api` while writing this PRD).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | no release-cadence practice of this shape; releases Python packages |
| `vscode` | ABSENT | — | no release workflow at all |
| `vscode-jupyter` | ABSENT | — | same |
| `intellij-community` | ABSENT | — | same |
| `termux-app` | ABSENT | — | attaches debug-signed APKs to a Release a person creates by hand |
| `winlator` | ABSENT | — | no release workflow |
| `claude-acc-manager@5233d605` | ABSENT | `.github/workflows/release-*.yml` | the same three workflows, no written cadence practice |
| the shipped rootfs | ABSENT | — | the runtime ships no delivery process |

**Count:** 0 of 8. The practice is this repository's own delivery concern; the references answer
nothing here.

## Implementation inventory

No reference implementation exists to port — the item writes down a practice this repository
already runs. What the executing session must open:

| Concern | Files |
|---|---|
| The machinery the doc describes | `.github/workflows/release-cut.yml`, `release-tag.yml`, `release.yml` — read their header comments, which state the contract |
| What bumps a version | `pyproject.toml` `[tool.semantic_release]` and the comment above it; `scripts/release_cut_config_test.py` asserts the type list |
| The mechanism decision | `docs/adr/0140-a-release-is-cut-from-conventional-commits-and-published-from-a-tag.md` — the doc cites it, never restates it |
| Where the doc lives | `docs/README.md` Need|Read table — durable docs are `adr/` and `architecture*`; a top-level `docs/releasing.md` is the new home, linked from the map |
| The cadence decision record | `docs/adr/0000-template.md` → `docs/adr/0177-*.md` (next free id) |
| The ledger | `docs/backlog.md` — OPS-024 row flips `shipped`, `← next` moves to `AUD-015` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the practice is written | new `docs/releasing.md`; a section of `docs/README.md`; `CONTRIBUTING.md`; a `docs-site/pages/` page | `docs/README.md` is the map, not the content; `CONTRIBUTING.md` addresses external contributors while the merge is the maintainer's act; `docs-site/` is user documentation and its header says engineering documents stay in `docs/` | `docs/releasing.md` — a greppable top-level doc, one row added to the Need|Read table |
| Whether the cadence gets an ADR | short ADR-0177; the doc alone; fold into ADR-0140 | ADR-0140 decided the mechanism and is silent on cadence; "release per user-visible item" is a durable decision a later session could otherwise re-litigate ("why five releases in eighteen days?") | ADR-0177 — put to the maintainer and answered before this PRD was written |
| When the item ships | on the existing tag history; stay `partial` until a new tag proves the practice prospectively (the OPS-022 T4 pattern) | a prospective check could take a milestone; the history already shows the cadence the doc prescribes, including two same-day releases | ship on the existing history — put to the maintainer and answered before this PRD was written |

Anything here that outlives the item becomes an ADR before the PRD is deleted — ADR-0177 is that
record.

## Gap analysis — what we already have vs what the item needs

| Concern | Ours today (file) | Needed | Gap |
|---|---|---|---|
| The cadence written down | nothing — ADR-0140 states the mechanism, no document states when to merge | `docs/releasing.md` | absent |
| The map pointing at it | `docs/README.md` Need|Read table | one row | absent |
| The decision on record | ADR-0140 (mechanism only) | ADR-0177 (cadence) | absent |

## Deviations

- The doc describes a practice the maintainer performs, not something the app or the agents do:
  merging `release/next` publishes a GitHub Release, a real-world act no session delegates to
  itself. The doc says who merges and when; it automates nothing.
- A `docs/`-only change never forces a release — stated in the doc itself, since this item's own
  commits are the case in point.

## Open decisions

None — doc placement, the ADR, and the ship-on-existing-history verification were put to the
maintainer and answered before this PRD was written.

## Surface

| Layer | Files |
|---|---|
| the practice | `docs/releasing.md` (new) |
| the map | `docs/README.md` (one row) |
| the decision | `docs/adr/0177-*.md` (new) |
| the ledger | `docs/backlog.md`, this PRD (deleted on ship) |

No `domain`, `data`, `ui`, `di`, `app/src` or workflow change: this item ships no port, no app
code and no machinery.

## Tasks

One conventional commit each. No TDD unit: no code is written — the coverage is the existing
Markdown link gate over every tracked file and the retired-document citation gate.

- [x] T1 — this PRD.
- [ ] T2 — `docs/releasing.md` and the `docs/README.md` row. Commit `docs(releasing)`.
- [ ] T3 — ADR-0177, the backlog row flipped `shipped` with `← next` on `AUD-015`, this PRD
  deleted. Commit `docs(adr)`, its body quoting `git tag --list 'v*' --sort=-v:refname` and
  `gh release list` as the reproducer.

## Out of scope

- `.github/workflows/release-*.yml` and `pyproject.toml`'s semantic-release config — the machinery
  is untouched; the item writes down the practice it already runs.
- Auto-merging `release/next` — ADR-0140 leaves the merge as the maintainer's one act; automating
  it is a different decision.
- A `docs-site/pages/` page — the site documents the app for users; the release practice is
  engineering documentation and stays in `docs/`.
- OPS-022's pending ship check — it still waits on the next tag's deploy, which the next release
  cut supplies; this item neither fixes nor blocks it.
