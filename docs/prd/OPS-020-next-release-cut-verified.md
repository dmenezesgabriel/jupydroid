# OPS-020 — The next release is cut and verified end to end

Milestone: `M10` · State: open · Depends on: OPS-005 shipped (the `main required checks` ruleset is
active) · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).
>
> This row was added as `OPS-013`, which the shipped M8 row already holds (the Kotlin quality gates,
> [ADR-0129](../adr/0129-kotlin-quality-gates-are-kept-where-their-findings-are-real.md)); it takes the
> next free id, `OPS-020` — `OPS-018` was spent on the security scans and `OPS-019` on the `docs`
> ruleset row.

## Outcome

The next release exists the way a user meets it: `aud-014-visual-consistency-audit` merged into
`main` under the ruleset, the merge's push proven to deploy the documentation site live, the semver
tag the conventional commits call for pushed by `release-tag.yml`, the release run green with every
asset on the Release, and the *released* APK — not a debug build — installed on `SM-X216B` answering
a cell.

## Demo

An OPS item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): no screen
changes of its own, so the demo is the run anyone can repeat, not a Maestro script authored here.

1. `gh pr view 36` shows the audit branch merged; `git log origin/main -1` is its merge commit.
2. `curl -fsS https://dmenezesgabriel.github.io/jupydroid/` answers the built site — it answered
   `404` before the merge, because `docs.yml` was not on `main`.
3. `gh release view <tag>` lists `jupydroid-<tag>-arm64-v8a.apk`, `SHA256SUMS`,
   `sbom-arm64-v8a.json`, `SOURCE_OFFER.md`, the tag's changelog notes and the build-provenance
   attestation; `gh run list --workflow release.yml` shows the run green.
4. `scripts/release-install-demo-driver.sh <tag>` on `SM-X216B` downloads the APK from the Release,
   checks it against `SHA256SUMS` and `apksigner`, installs it fresh, proves the installed version is
   the tag's and not debuggable, and runs `.maestro/release-install-demo.yaml`, which runs a cell.
   The driver's evidence stays local and is never committed
   ([ADR-0132](../adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)).

## Who else implements this

Release machinery is hosted state plus workflows; no reference clone carries it. Every reference
reads ABSENT for want of evidence. The pattern being exercised is the maintainer's own sibling
repository, already adopted by [ADR-0140](../adr/0140-a-release-is-cut-from-conventional-commits-and-published-from-a-tag.md)
and [ADR-0169](../adr/0169-the-documentation-site-is-mkdocs-material-under-docs-site-and-pages-deploys-only-while-public.md).

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e690680a7` | ABSENT | — | no release-cut/tag/publish pipeline in the tree |
| `vscode@3f3380c668d0` | ABSENT | — | same |
| `vscode-jupyter@f01d80ca0aa0` | ABSENT | — | same |
| `intellij-community@406a27a8da07` | ABSENT | — | same |
| `termux-app@3df69d1da197` | ABSENT | — | F-Droid/fastlane publishing, not in the clone |
| `winlator@fb66541b93a4` | ABSENT | — | same |
| the shipped rootfs | ABSENT | — | not a repository with releases |
| `claude-acc-manager@5233d605` | PARTIAL | its release/docs workflows | the pattern source: cut PR, tag on merge, publish from tag — already ours via ADR-0140 |

**Count:** 0 of 7 references implement this; the sibling supplies the shape only. The work is running
the machinery ADR-0140 already built, not designing a new one.

## Implementation inventory

### Our own files the tasks touch

| Concern | Files |
|---|---|
| The merge | PR #36 (`aud-014-visual-consistency-audit` → `main`), merge commit per its body; the `main required checks` ruleset (id 27844457) names the eight contexts that must be green |
| The docs deploy | `.github/workflows/docs.yml` — `docs` job (`mkdocs build --strict`) then `deploy` (`pages: write`, `github-pages` environment), both on `push: [main]` guarded by `visibility == 'public'` |
| The release cut | `.github/workflows/release-cut.yml` — on every `main` push rebuilds `release/next` on `origin/main`, runs `semantic-release version`, force-pushes, opens or refreshes the standing release PR; config in `pyproject.toml` `[tool.semantic_release]` (`tag_format = "v{version}"`, `major_on_zero = false` → feat bumps minor) |
| The tag | `.github/workflows/release-tag.yml` — on a merged `release/*` PR, `scripts/release_commit_tag.py` finds the `chore(release): vX.Y.Z` commit and pushes the tag with the app token, because a `GITHUB_TOKEN` tag starts no workflow |
| The release | `.github/workflows/release.yml` — `version` (tag must be an ancestor of `main`) → `rootfs` on `ubuntu-24.04-arm` (native since ADR-0173) → `apk` (release-keystore signed, badging checked) → `publish` (APK, `SHA256SUMS`, `sbom-arm64-v8a.json`, `SOURCE_OFFER.md`, notes from `scripts/release_notes.py`) → `attest` (runs: the repository is public) |
| The install proof | `scripts/release-install-demo-driver.sh` (checksum, `apksigner`, `aapt2` badging, fresh install, `scripts/installed_release_check.py` version/DEBUGGABLE check, then the Maestro flow), `.maestro/release-install-demo.yaml` |
| Version arithmetic | `scripts/release_version.py`, `scripts/pending_release.py`, `scripts/release_commit_tag.py` — all stdlib, all tested |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Merge vehicle for PR #36 | merge commit; squash; rebase | the branch's body says merge commit; squash would collapse 242 commits the changelog and history read | `gh pr merge 36 --merge`, once the eight required contexts are green and the draft flag is lifted |
| Which release PR to merge | the open PR #31 as it stands; the rebuilt one | PR #31 was cut from `e0dc82ae4`, before the aud-014 merge — its CHANGELOG would omit the 242 incoming commits | wait for `release-cut.yml` to rebuild `release/next` on the new `main` and refresh the PR, then merge that |
| How the tag is pushed | `git tag` + push by hand; let `release-tag.yml` do it | a hand-pushed tag either starts no workflow (`GITHUB_TOKEN`) or bypasses the job that proves the tag sits on a release commit | merge the `release/*` PR and let `release-tag.yml` tag the merge commit |
| Which version | pick one; accept what psr derives | the conventional commits decide; over `v0.1.1` the branch carries `feat` commits and no `BREAKING`, and `major_on_zero = false` makes that a minor bump — v0.2.0 expected, but psr recomputes on the rebuilt branch | whatever `chore(release): vX.Y.Z` the refreshed PR carries |
| Proof the release works | green workflow runs only; plus the on-device install | a green pipeline proves the artifact was built, not that a user can install and run it | `scripts/release-install-demo-driver.sh` — already the tested script JD-066 T6 shipped for v0.1.1 |

## Gap analysis — what we already have vs what the item needs

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| Audit branch on `main` | PR #36 open, DRAFT, mergeable; `git merge-tree --write-tree origin/main HEAD` clean (tree `ac8e9ec`); seven of eight required checks green, `gradle-gate` pending at writing | — | present — T2 waits for the last check and lifts the draft |
| Docs site live | `docs.yml` not on `main`; `https://dmenezesgabriel.github.io/jupydroid/` answers 404 | sibling `docs.yml` shape already adopted | absent until the merge push fires `deploy` — T3 proves it |
| Release PR current | PR #31 open (`chore(release): v0.2.0`) but based on pre-merge `main` | `release-cut.yml` rebuilds it on the merge push | stale — merge only the refreshed PR |
| Tag + Release | machinery shipped under JD-066; last release `v0.1.1`; arm64 runner proven by the OPS-005 probe | ADR-0140 design | present, never run end to end on the native arm64 path — T4 is the first real run |
| Installed-release proof | driver script and flow exist, ran for `v0.1.1` | — | present — T5 reruns it for the new tag |

## Deviations

- No `domain`/`data`/`ui`/`di` surface: this item ships no port and no app capability, so the only
  device evidence is the one the install driver already produces
  ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)).
- Tasks whose whole act is a `gh` or `git` operation carry their evidence in the commit body — run
  ids, the Release URL, the driver's output — because there is no file to change
  (the OPS-005 precedent).
- `docs` joining the required checks is deliberately **not** here: that is the OPS-019 row, which
  this item's merge makes possible. This item only proves the first `deploy` run live.

## Open decisions

| Decision | Evidence to gather | Task |
|---|---|---|
| Whether `release-cut.yml`'s rebuild keeps `v0.2.0` | the refreshed PR #31's title after the merge push | T4 reads it; a different version is accepted as psr's answer, not overridden |

## Surface

| Layer | Files |
|---|---|
| workflows | `.github/workflows/docs.yml`, `release-cut.yml`, `release-tag.yml`, `release.yml` — read, not edited |
| scripts | `scripts/release-install-demo-driver.sh`, `scripts/installed_release_check.py` — run, not edited |
| Maestro | `.maestro/release-install-demo.yaml` — run by the driver |
| GitHub state | PR #36, PR #31, the `main required checks` ruleset, the Pages deployment, the Release |
| device | `SM-X216B` via `adb` for T5 |
| docs | this PRD (deleted at ship); `docs/backlog.md` row |

## Tasks

- [x] T1 — This PRD, and the `OPS-013` → `OPS-020` renumber that gives the row a unique id.
- [x] T2 — Merge the audit branch: `gh pr ready 36`, wait until `gradle-gate` and the other seven
  required contexts are green on the pull request, then `gh pr merge 36 --merge` (merge commit, per
  the PR's body). The commit body names the merge commit sha and the check-run ids it waited on.
- [x] T3 — Prove the docs deploy live: watch the `Docs` workflow's `deploy` job go green on the merge
  push, then `curl -fsS https://dmenezesgabriel.github.io/jupydroid/` until it serves the built site
  (it answered 404 before). Commit body quotes the job id and the fetched page's identifying line.
  If `deploy` fails on Pages source configuration, `actions/configure-pages` in `docs.yml` is the
  first thing read — a defect to fix, not a workaround.
- [ ] T4 — Cut the release: wait for the `Release cut` run on the merge push to rebuild
  `release/next` and refresh PR #31 (confirm its head contains the merge — never merge the stale
  pre-merge PR), wait its required checks, `gh pr merge 31 --merge`; `release-tag.yml` tags the
  merge commit; `gh run watch` the `Release` run to green; `gh release view <tag>` shows the APK,
  `SHA256SUMS`, `sbom-arm64-v8a.json`, `SOURCE_OFFER.md`, the changelog notes and the attestation.
  Commit body names every run id and the Release URL.
- [ ] T5 — Install the released APK on `SM-X216B`:
  `scripts/release-install-demo-driver.sh <tag>` — checksum, signature, badging, fresh install,
  non-debuggable version check, and the Maestro flow's cell run; the driver's evidence stays local
  ([ADR-0132](../adr/0132-evidence-is-local-and-reproduction-is-a-tested-script.md)). The commit body
  says `Demo run by the agent, not by a human`,
  what was seen at each step, and every difference from what the item expects.
- [ ] T6 — Ship: delete this PRD; `docs/backlog.md` row to `shipped`; `← next` moves to `OPS-019`
  (`docs` joins the required checks — its precondition, `docs.yml` on `main`, is what T2 delivered).
  Commit body names the flow, the run ids and what each showed.

## Out of scope

Adding `docs` to the `main required checks` ruleset and watching a *subsequent* Pages deploy —
OPS-019 owns it; this item proves the first deploy live only. Pushing a tag by hand, choosing a
version by hand, or rebuilding the release by `workflow_dispatch` — `release-tag.yml` and the
merged release PR are the only tag path. Deleting the merged `aud-014-visual-consistency-audit` or
`release/next` branches — the repository leaves merged branches (`delete_branch_on_merge` is off).
Fixing unrelated pull-request check failures — a red required check on PR #36 is a defect to repair
before the merge, named as such.
