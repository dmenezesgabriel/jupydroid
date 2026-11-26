# ADR-0177 — Releases ship what is ready: the standing release pull request is merged after each user-visible item

Status: accepted
Date: 2026-11-24

## Context

[ADR-0140](0140-a-release-is-cut-from-conventional-commits-and-published-from-a-tag.md) built the
release machinery and stated the maintainer's only act — merging the standing `release/next`
pull request — but said nothing about *when*. Two cadences were both consistent with it: merge
after each item that ships user-visible change, or accumulate a milestone's worth and cut once.

The tag history shows the first cadence already happening de facto: `v0.1.0` through `v0.3.1`,
five releases published between 2026-11-06 and 2026-11-24, `v0.3.0` and `v0.3.1` the same day.
What was missing was the decision written down, so a later session choosing the cheaper-looking
option — fewer `release.yml` runs — would not silently revert it.

## Decision drivers

- A user gets a fix as an APK as soon as it lands. Milestone M10's premise is that every shipped
  item reaches a user as a release rather than as a commit; a batched cadence delays that by
  however long the milestone runs.
- The tag history is the audit trail of what shipped when; small frequent tags keep it readable,
  one milestone dump does not.
- Each tag costs a `release.yml` run — the arm64 rootfs build, the signed APK, the publish —
  measured in tens of minutes of runner time. Batching would save that; the cost is accepted
  knowingly because APK freshness is the point of the pipeline.
- Merging publishes a GitHub Release — a real-world act. The cadence is the maintainer's
  decision, supported by the document, never automated by the machinery.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Merge `release/next` after each user-visible item | Users get fixes as APKs; the tag history reads as a changelog of shippable states | More `release.yml` runs; more Releases to keep straight |
| Merge only at milestone boundaries | Fewer releases, fewer runner minutes | A fix waits for the milestone; "what is in vX" becomes a dump nobody audits |
| Arm auto-merge on `release/next` | Zero maintainer acts | Publishes a Release with no human decision; a separate decision ADR-0140 left open, not this one |
| Leave the cadence unwritten | Nothing to maintain | The practice lives only in the maintainer's head and drifts back toward batching |

## Decision

After work lands on `main`, merge the standing `release/next` pull request — small releases ship
what is ready, one merge per user-visible item, rather than a milestone's worth accumulated
before a cut. Commits that bump no version (`docs`, `build`, `ci`, `chore`, `refactor`, `test`,
`style`) never force a release; they ride the next one. The practice is written in
[`docs/releasing.md`](../releasing.md).

## Consequences

- **Easy:** a fix reaches a user's APK within a release cycle of landing; `release-cut.yml` keeps
  the bookkeeping so the maintainer's act stays one merge.
- **Accepted cost:** a `release.yml` run per cut, and a longer Releases list. The arm64 runner
  keeps each run inside its timeout (ADR-0173), so the cost is minutes, not risk.
- **What a future reader will be tempted to "fix":** batching releases to save runner time. That
  trades the product's reason for releasing — users installing fixes — for CI minutes, and it is
  the thing this decision forbids.
- The merge stays a deliberate act. Arming auto-merge is a different decision, still open since
  the repository turned public.
