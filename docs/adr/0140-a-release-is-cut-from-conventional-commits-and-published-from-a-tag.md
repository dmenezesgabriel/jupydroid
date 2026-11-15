# ADR-0140 — A release is cut from conventional commits and published from a tag

Status: accepted
Date: 2026-11-07

## Context

A user had no way to install Jupydroid except to build it. A release needs a version, a signed APK that
carries the arm64 rootfs, and a page to download both from, with the maintainer's only act being the
decision to release.

Of the 7 references at the pins in `docs/README.md`, 2 automate any of it: `termux-app` attaches
**debug-signed** APKs to a Release a person creates, with `versionName` taken from the tag and `versionCode`
a constant edited by hand; `jupyterlab` releases Python packages. `winlator`, `vscode` and `intellij-community`
have no release workflow, and `vscode-jupyter` was not read. None signs an APK with a release key. The pattern
comes from the sibling repository `dmenezesgabriel/claude-acc-manager` at `5233d605`: a standing release pull
request, a second workflow that tags its merge, a third that publishes the tag. Signing, the arm64 rootfs build
and provenance have no reference; each follows its own tool's documentation.

## Decision drivers

- The tag is the one input: nothing about a version is edited by hand, because a forgotten `versionCode` makes
  Android refuse the update.
- A release is signed with a key an install can be updated under. A debug-signed one cannot.
- Every tool that decides what ships is locked and verified ([ADR-0123](0123-dependencies-are-locked-strict-and-verified-by-sha256.md),
  [ADR-0130](0130-python-dependencies-are-locked-and-verified-by-sha256.md)).
- The repository is private on the free plan: no rulesets, no auto-merge, no hosted arm64 runner, no
  environments with required reviewers, no build-provenance attestation.

## Decision

- **The tag is `vMAJOR.MINOR.PATCH` and nothing else.** `scripts/release_version.py` derives `versionName` from
  it and `versionCode` as `MAJOR*1_000_000 + MINOR*1_000 + PATCH`, which orders like semver and stays under
  Android's 2,100,000,000 cap while MAJOR is at most 2099 and MINOR and PATCH at most 999. A prerelease or
  build suffix is refused, naming the value. Gradle reads two properties and keeps `1.0` and `1` as the
  developer default.
- **The release is cut by `python-semantic-release` 10.7.0**, locked in the `dev` group of `uv.lock`. It reads
  this repository's ten commit types and bumps on `feat`, `fix` and `perf`; a rootfs change cuts a release by
  its commit type alone (`tools/rootfs` history is 18 `build`, 13 `feat`, 5 `fix`, 3 `perf`, so a `build` does
  not). It edits no file but `CHANGELOG.md`. The first version is `0.1.0`: no tag existed in 1,614 commits.
- **Three workflows, one act.** `release-cut.yml` keeps the standing `release/next` pull request; merging it
  is the maintainer's only act. `release-tag.yml` tags the merge commit from its release commit
  (`scripts/release_commit_tag.py`). `release.yml` runs on the pushed tag. The first two use a GitHub App token,
  `JUPYDROID_RELEASE_CLIENT_ID` (variable) and `JUPYDROID_RELEASE_APP_PRIVATE_KEY` (secret), because an event
  authored by `GITHUB_TOKEN` starts no workflow. Without the pair they warn and stay green. No auto-merge is
  armed until the repository is public.
- **The arm64 rootfs is built in CI under qemu**, from the digest-pinned image, on a 2 vCPU `ubuntu-24.04`
  runner: `build.sh` took 31 min 06 s and `sbom.sh` 16 s, 31 min 39 s for the job, a tenth of the 6-hour limit.
  The job's `timeout-minutes` is 90. No native runner and no layer cache are needed.
- **The release key is an Actions secret** read from four variables, `JUPYDROID_RELEASE_KEYSTORE_FILE`,
  `_KEYSTORE_PASSWORD`, `_KEY_ALIAS` and `_KEY_PASSWORD`. GitHub documents that "Users with GitHub Free plans
  can only configure environments for public repositories", so an environment with a reviewer is unavailable.
  The key is not rotated: a new key cannot update installs signed by the old one.
- **A release without the key fails.** The `apk` job runs `apksigner verify` and checks the badging against the
  tag before anything is uploaded, so an empty secret stops the job instead of publishing an APK no install
  can be updated from.
- **The notes are the tag's own `CHANGELOG.md` section**, cut by `scripts/release_notes.py` matching the whole
  `## vX.Y.Z (date)` header. `semantic-release publish` refuses the detached HEAD a tag-triggered job checks out,
  and prefix matching would let `## v1.2.3` also take `## v1.2.30`.
- **The Release holds** the APK, `SHA256SUMS`, the rootfs SBOM, the notes and `SOURCE_OFFER.md`. A
  build-provenance attestation is written and guarded on `github.event.repository.visibility == 'public'`, as
  [ADR-0138](0138-code-scanning-jobs-wait-for-a-public-repository-and-the-rest-block-in-plain-text.md) guards CodeQL.
- **`release.yml` is idempotent**: `workflow_dispatch` with a `ref` rebuilds an existing tag and updates its
  Release, which is also how a failed release is retried.

## Consequences

- **Measured on the tablet** (`SM-X216B`, `scripts/release-install-demo-driver.sh v0.1.1`): merging the release
  pull request produced the tag, and the tag's push event alone ran `release.yml` green (`version`, `rootfs`,
  `apk`, `publish`; `attest` skipped while private) and published a Release of four assets. The APK passed
  `sha256sum --check` and `apksigner verify`, its badging read `versionName='0.1.1'` and `versionCode='1001'`,
  and after a fresh install the package manager reported the same, not debuggable. The base Space then answered
  `print(6 * 7)` with `42` from a real kernel, which the cell's own source cannot produce.
- **The first release needed two fixes and a manual republish.** Merging the first release pull request tagged
  nothing, because `release-tag.yml` required the merge commit's subject to be `chore(release): vX.Y.Z` and a
  merge commit's is "Merge pull request #24 from ..." (`96b2351d`). The first tag-triggered run, for `v0.1.0`,
  failed in 10 seconds in the `version` job, at a `git fetch` that had no credentials: the checkout does not
  persist them and the repository is private (`d76e756f`). `v0.1.0` was republished by `workflow_dispatch`, and
  `v0.1.1` is the first release the automatic path produced end to end.
- **No screen shows the version.** `versionName` reaches only the User-Agent of remote image fetches
  (`app/src/main/java/com/jupydroid/app/data/http/OkHttpRemoteImageReader.kt`). The version is therefore checked
  from the package manager, `scripts/installed_release_check.py`, and not read off a screen; showing it is its
  own item.
- **A developer build cannot be updated to a release.** The keys differ, so installing a release over the debug
  build means `adb uninstall`, which wipes the app's data. The demo driver does that on purpose and says so.
- **A release APK is not debuggable**, so the `run-as` the other drivers read app files with is refused on it;
  the demo's evidence is `adb`, the package manager and screenshots.
- Until the repository is public there is no attestation, no auto-merge and no environment gate on the key.
  Those come back with it.
