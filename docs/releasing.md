# Releasing

A release is a merge. `.github/workflows/release-cut.yml` keeps one standing pull request,
`release/next`, refreshed on every `main` push: python-semantic-release reads the conventional
commits since the last tag and, when one bumps the version, rebuilds the branch with the
`chore(release): vX.Y.Z` commit — `CHANGELOG.md` is the only file it changes. Merging that pull
request is the whole release: `.github/workflows/release-tag.yml` tags the merge commit
`vX.Y.Z`, and the tag push runs `.github/workflows/release.yml`, which builds the arm64 rootfs
and a signed APK and publishes the GitHub Release with its notes, `SHA256SUMS`, the SBOM and
`SOURCE_OFFER.md` ([ADR-0140](adr/0140-a-release-is-cut-from-conventional-commits-and-published-from-a-tag.md)).

## The cadence

**Small releases ship what is ready.** After each item that ships user-visible change, merge the
standing `release/next` pull request rather than letting a milestone's worth of changes
accumulate. A user gets the fix as an APK as soon as it lands, and the tag history stays the
audit trail of what shipped when.

Only `feat`, `fix` and `perf` commits bump the version — `docs`, `build`, `ci`, `chore`,
`refactor`, `test` and `style` produce no release (`pyproject.toml`'s
`[tool.semantic_release]`, asserted by `scripts/release_cut_config_test.py`). Work that changes
nothing a user sees rides the next release; it never forces one, and the commits that wrote this
document are the case in point.

## Edges worth knowing

- **A closed-unmerged `release/next` is not a release.** `release-tag.yml` fires only on a
  merged pull request; the `v0.2.1` cut was closed unmerged and no tag followed.
- **A failed release is rebuilt by hand**: `workflow_dispatch` on `release.yml` with the tag as
  `ref`. Publishing is idempotent — a second run updates the Release rather than failing on it.
- **The tag must sit on `main`.** `release.yml`'s `version` job refuses a tag that is not an
  ancestor of `main`, so a rebuild can re-publish a release, never an arbitrary commit.
- **Without the app credentials the first two workflows warn and stay green** — an unconfigured
  fork or repository is not a red run (the `JUPYDROID_RELEASE_*` pair, ADR-0140).

## Reading the history

| Question | Command |
|---|---|
| What has shipped | `git tag --list 'v*' --sort=-v:refname` |
| What each release carries | `gh release list`, `gh release view vX.Y.Z` |
| Whether a release is pending | `gh pr list --head release/next --state open` |
| Whether the cut ran | `gh run list --workflow=release-cut.yml` |

The cadence is already measured: five tags — `v0.1.0`, `v0.1.1`, `v0.2.0`, `v0.3.0`, `v0.3.1` —
in the eighteen days to 2026-11-24, two of them cut the same day. The cadence is
[ADR-0177](adr/0177-releases-ship-what-is-ready.md)'s decision; the mechanism is
[ADR-0140](adr/0140-a-release-is-cut-from-conventional-commits-and-published-from-a-tag.md).
