# JD-066 — A user installs Jupydroid from GitHub Releases

Milestone: `M10` · State: in-progress · Depends on: OPS-006, OPS-008 · Closes: —

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A user downloads one signed APK from a GitHub Release, installs it, and runs a cell, without building
anything; the maintainer's only act is merging a release pull request.

## Demo

On the arm64 tablet (`SM-X216B`), because a cell runs:

1. Merge the release pull request the cut opened. The tag workflow tags the merge commit `vX.Y.Z`.
2. The release workflow publishes a Release holding the APK, `SHA256SUMS`, the rootfs SBOM, the notes
   and `SOURCE_OFFER.md`.
3. Download the APK from the Release, check `sha256sum -c SHA256SUMS`, `apksigner verify`, install it.
4. Launch, create a Space, run a cell, see its real output (not the cell's source echoed back).
5. The version shown in the app and `aapt2 dump badging` both equal the tag.

Not `shipped` until this has run on that device, with screenshots, snapshots and `maestro.log` under
`evidence/<flow-name>/` (local, ADR-0132).

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `termux-app` | PARTIAL | `.github/workflows/attach_debug_apks_to_release.yml`, `app/build.gradle` | A human publishes a Release; a workflow builds and attaches **debug-signed** APKs and a sha256sums file. `versionName` comes from the tag through an env var and is validated as semver; `versionCode` is a constant edited by hand (118) |
| `winlator` | ABSENT | `.github/` holds `FUNDING.yml` and an issue template only; `app/app/build.gradle` | `versionCode 28`, `versionName "11.1"` edited by hand, no workflow |
| `jupyterlab` | FULL (Python packages, not an APK) | `.github/workflows/prep-release.yml`, `check-release.yml`, `publish-release.yml`, `publish-changelog.yml` | `jupyter_releaser`: a dispatched prep workflow, a check on every push, a publish workflow. Teaches the shape; produces no APK |
| `vscode` | ABSENT | no release or publish workflow in `.github/workflows/` | Releases are cut on Microsoft's own pipeline, outside the repository |
| `vscode-jupyter` | not read | | An extension published through the Marketplace; no APK |
| `intellij-community` | ABSENT | no release or publish workflow in `.github/workflows/` | Built and uploaded by JetBrains' own infrastructure |
| `claude-acc-manager` (sibling, `5233d605`) | FULL (PyPI, not an APK) | `.github/workflows/release-cut.yml`, `release-tag.yml`, `release.yml` | The pattern the backlog names: a standing `release/next` PR cut by `python-semantic-release` on each push to `main`, auto-merged once checks pass, tagged by a second workflow, published by a third. A GitHub App token, because events authored by `GITHUB_TOKEN` trigger no workflow |
| the shipped rootfs | n/a | | Not a release channel. It is an input: `tools/rootfs/build.sh arm64-v8a` builds it from a digest-pinned image |

**Count:** 2 of the 7 reference projects (`termux-app`, `jupyterlab`) automate any of it, 3 are ABSENT or
not read, and the sibling repository supplies the pattern. None signs an APK with a release key. Only `termux-app` ships an Android APK, and it ships
debug-signed ones. The signing, the arm64 rootfs and the provenance have no reference; their source is each
tool's own documentation, fetched when the task is done.

## Implementation inventory

### `termux-app`

| Concern | Files |
|---|---|
| Version from the tag | `app/build.gradle` (`appVersionName`, `validateVersionName`) |
| Publish | `.github/workflows/attach_debug_apks_to_release.yml` |
| Integrity | the same workflow writes `*_sha256sums` beside the APKs |

### `claude-acc-manager`

| Concern | Files |
|---|---|
| Cut and tag | `.github/workflows/release-cut.yml`, `release-tag.yml` |
| Publish and idempotent rebuild | `.github/workflows/release.yml` (`workflow_dispatch` with `ref`, notes taken from the tag's changelog section) |
| Release-tool config | `pyproject.toml` `[tool.semantic_release]` |

**Disagreement to settle:** `termux-app` publishes from a human-created Release; the sibling publishes from a
pushed tag. We follow the sibling, because the backlog asks for a release PR and nobody should click through a
Release page.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| `versionName` | env var validated as semver (`termux-app`); hand-edited (`winlator`); derived from the tag (sibling) | A hand edit drifts from the tag | The tag, passed as a Gradle property; the local default stays `1.0` so a developer build is unchanged |
| `versionCode` | hand-edited constant (both Android references) | Forgetting to raise it makes Android refuse the update | Derived: `MAJOR*1_000_000 + MINOR*1_000 + PATCH`. Strictly increasing with the tag, under Android's 2_100_000_000 cap, so MAJOR ≤ 2099 and MINOR, PATCH ≤ 999. The tag is the single input |
| Tags accepted | any semver including prerelease and build metadata (`termux-app`) | A prerelease maps to a versionCode only by inventing a rule | `vMAJOR.MINOR.PATCH` only; anything else is refused with the value and the expected shape |
| Where the derivation lives | in Gradle (`termux-app`); in a script | Gradle logic is not covered by the unit suite and the release workflow cannot reuse it | `scripts/release_version.py`, tested in the existing unittest suite; Gradle only reads two properties |
| Release tool | `python-semantic-release` (sibling); `jupyter_releaser`; Node `semantic-release`; release-please | The tool must be locked, sha256-verified and not obscure | Open (below); the recommendation is `python-semantic-release` through uv, already the locked toolchain |
| Rootfs on a private repository | native arm64 runner (public repositories only); x86 runner under qemu | qemu is slower but runs now | qemu on `ubuntu-24.04`; a native-arm64 switch guarded on `visibility == 'public'`, as ADR-0138 does for CodeQL |
| Build provenance | `actions/attest-build-provenance` | Needs a public repository (or a paid plan) | Written and guarded on `visibility == 'public'` |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| `versionName`, `versionCode` | `app/build.gradle.kts` defaultConfig, `1.0` and `1`, never changed | `termux-app` `app/build.gradle` | present and partial: no input from a tag |
| Release signing | none; the `release` build type is unsigned | none | absent |
| Release PR, tag, publish | none | sibling `release-cut.yml`, `release-tag.yml`, `release.yml` | absent |
| arm64 rootfs in CI | `tools/rootfs/build.sh arm64-v8a` runs on a developer host only | none | absent: `ci.yml` assembles a debug APK with no rootfs asset |
| SBOM | `tools/rootfs/sbom.sh` writes `build/rootfs/sbom-arm64-v8a.json` and regenerates `THIRD_PARTY.md` | none | present, local only |
| GPL source offer | `SOURCE_OFFER.md` | none | complete; a Release must carry it |
| Where the version shows | `JupydroidApplication.kt` reads `getPackageInfo().versionName` | | complete |

## Deviations

- No prerelease tags and no `+build` metadata in the first version; a prerelease channel is its own item.
- No debug-signed release APK. `termux-app` ships one; a user cannot update across a key change.
- No Play Store, F-Droid or universal APK: the runtime is arm64-only ([ADR-0003](../adr/0003-musl-not-glibc-and-arm64-only-at-runtime.md)).

## Open decisions

Each needs evidence gathered in the task that owns it, and an ADR (0140) when the item ships.

| Decision | Evidence to gather | Task |
|---|---|---|
| ~~Release tool~~ **Decided in T2:** `python-semantic-release` 10.7.0, locked in the `dev` group of `uv.lock`. It reads our ten commit types (bumps on `feat`, `fix`, `perf`), edits no file when no `version_toml` is set, and was released 2026-09-22, past the 7-day cooldown. Its four newer dependencies (`pydantic`, `pydantic-core`, `typing-inspection`, `wrapt`) were younger than 7 days and are locked one release back | | T2 |
| ~~The credential~~ **Decided in T2:** a GitHub App, `JUPYDROID_RELEASE_CLIENT_ID` (variable) and `JUPYDROID_RELEASE_APP_PRIVATE_KEY` (secret), created by the maintainer; both workflows warn and stay green without it. The repository is private on the free plan, so no ruleset and no auto-merge: the maintainer merges the release PR | | T2 |
| ~~Whether a rootfs-only change cuts a release~~ **Decided in T2:** by commit type alone. `tools/rootfs` history is 18 `build`, 13 `feat`, 5 `fix`, 3 `perf`; a rootfs `feat`/`fix`/`perf` changes the bundled APK and cuts, a `build` does not | | T2 |
| ~~First version~~ **Decided in T2:** `0.1.0` (`allow_zero_version`), no tag exists in 1614 commits; the first notes are PSR's masked "Initial Release" | | T2 |
| ~~qemu build time of the arm64 rootfs against the 6 hour job limit~~ **Decided in T3:** one run on a 2 vCPU `ubuntu-24.04` runner took 31 min 06 s for `build.sh` and 16 s for `sbom.sh`, 31 min 39 s for the job, a tenth of the limit. The job's `timeout-minutes` is 90. No native runner and no layer cache are needed for that | | T3 |
| ~~Where the Release notes come from~~ **Decided in T5:** the tag's own `CHANGELOG.md` section, cut by `scripts/release_notes.py`. `semantic-release publish` refuses the detached HEAD a tag-triggered job checks out (the sibling's finding), and the sibling's awk prefix-matches, so `## v1.2.3` would also take `## v1.2.30`; the script matches the whole `## vX.Y.Z (date)` header. Checked against a real PSR changelog, masked first release included | | T5 |
| ~~Whether a release without the signing secrets may fall back~~ **Decided in T5:** it fails. `release-cut.yml` and `release-tag.yml` skip a step without their credential; the `apk` job would publish an unsigned APK that no install can be updated from, so an empty secret stops the job, and `apksigner verify` and the badging check run before anything is uploaded | | T5 |
| ~~Where the release keystore lives and who rotates it~~ **Decided in T4:** an Actions secret. GitHub documents that "Users with GitHub Free plans can only configure environments for public repositories", so an environment with a required reviewer is unavailable on this private repository. Four variables, `JUPYDROID_RELEASE_KEYSTORE_FILE`, `_KEYSTORE_PASSWORD`, `_KEY_ALIAS` and `_KEY_PASSWORD`, are read by `app/build.gradle.kts`; the release workflow decodes the base64 secret to a file and passes its path. The key is not rotated: a new key cannot update installs signed by the old one | | T4 |

## Surface

| Layer | Files |
|---|---|
| host scripts | `scripts/release_version.py` (T1), `scripts/pending_release.py` (T2), `scripts/release_commit_tag.py` (T2 fix: the tag workflow also reads a merge commit's second parent), `scripts/release_notes.py` (T5) |
| build | `app/build.gradle.kts` (T1 version properties; T4 `signingConfig`), `scripts/release_signing_config_test.py` (T4) |
| workflows | `.github/workflows/release-cut.yml`, `release-tag.yml` (T2), `release.yml`: `rootfs` (T3), `version`, `apk`, `publish`, `attest` (T5) |
| unit tests | `scripts/release_version_test.py` (T1), `scripts/pending_release_test.py`, `scripts/release_cut_config_test.py` (T2), `scripts/release_rootfs_workflow_test.py` (T3), `scripts/release_notes_test.py`, `scripts/release_publish_workflow_test.py`, `scripts/workflow_reader.py` + `_test.py` (T5) |
| `.maestro/` flow + `scripts/` driver | install-and-run-a-cell flow, T6 |

No `domain`, `data`, `ui` or `di` change: this item ships no port.

## Tasks

- [x] T1 — a release tag derives `versionName` and `versionCode`; Gradle reads them as properties and keeps today's defaults. Closes on `aapt2 dump badging` of a debug APK built with the properties, since no screen changes
- [x] T2 — the release cut and the tag workflow, the locked release tool and the changelog; both degrade to a warning without the credential
- [x] T3 — the arm64 rootfs and its SBOM built in CI under qemu from the digest-pinned image; the duration measured
- [x] T4 — a release `signingConfig` read from environment variables; `assembleRelease` verified with `apksigner verify`
- [x] T5 — `release.yml` publishes the Release: APK, `SHA256SUMS`, SBOM, notes, `SOURCE_OFFER.md`; attestation guarded on a public repository
- [ ] T6 — the demo on the tablet; ADR-0140; delete this PRD; the backlog row to shipped

## Out of scope

Making the repository public (`OPS-005`); the documentation site and its install tutorial (`OPS-009`, `OPS-011`);
an in-app update check; F-Droid and Play Store; a prerelease channel.
