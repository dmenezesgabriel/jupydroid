# OPS-001 — Dependencies are locked and verified

Milestone: `M8` · State: in-progress · Depends on: — · Closes: —

> An **OPS** item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): it
> changes the repository's build, not a screen, so its *Demo* is the run that reproduces it and no
> Maestro flow is owed. **Deleted when the item ships** — its durable residue is an ADR, the build
> comments and an `AGENTS.md` note on updating ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Every build resolves exactly the dependency versions committed in lockfiles, and only artifacts whose
sha256 matches `gradle/verification-metadata.xml`: a drifted version or a tampered jar fails the
build, and a scanner (osv-scanner, in `AUD-012`) reads exact versions from files it parses natively.

## Demo

The run, from a clean checkout, that anyone repeats:

1. The full local gate in `AGENTS.md` is green with locking and verification on.
2. A version planted in `gradle/libs.versions.toml` without `--write-locks` fails resolution with
   Gradle's lock-state error, and passes again once reverted.
3. One sha256 planted wrong in `gradle/verification-metadata.xml` fails the build with
   "Dependency verification failed", and passes again once reverted.
4. `sha256sum gradle/wrapper/gradle-wrapper.jar` equals
   `https://services.gradle.org/distributions/gradle-9.3.1-wrapper.jar.sha256`, and
   `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` equals
   `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip.sha256`.

Each step's output is quoted in the commit body of the task that lands it.

## Who else implements this

Read at the pins in [`../README.md`](../README.md): a `find` for `gradle.lockfile`,
`verification-metadata.xml` and `gradle-wrapper-validation.yml` over each clone.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | not a Gradle build (yarn.lock is its lock) |
| `vscode` | ABSENT | — | not a Gradle build (package-lock.json) |
| `vscode-jupyter` | ABSENT | — | not a Gradle build |
| `intellij-community` | ABSENT | — | JPS/Bazel module model, no Gradle locking |
| `termux-app` | PARTIAL | `.github/workflows/gradle-wrapper-validation.yml` | validates the wrapper jar in CI; no locking, no verification |
| `winlator` | ABSENT | — | Gradle build with neither |
| the shipped rootfs | n/a | `tools/rootfs/requirements.lock` | pip side already pinned `==` |

**Count:** 0 of 7 lock or verify Gradle dependencies; 1 validates the wrapper. The references are no
authority here, so Gradle's own documentation is:
[Locking dependency versions](https://docs.gradle.org/9.3.1/userguide/dependency_locking.html) and
[Verifying dependencies](https://docs.gradle.org/9.3.1/userguide/dependency_verification.html).

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where locking is declared | per project `dependencyLocking {}` (Gradle docs); root `subprojects {}` | cross-project configuration couples modules and is discouraged by Gradle | per module: four build files, one block each |
| Lock mode | DEFAULT, STRICT, LENIENT (Gradle docs) | DEFAULT silently accepts a configuration with no lock state; STRICT fails on it | STRICT — a new configuration must be locked before it builds; settled on measurement in T1 |
| Plugin classpath | `buildscript { configurations.classpath { resolutionStrategy.activateDependencyLocking() } }` in the root, writing `buildscript-gradle.lockfile` | without it AGP, KGP, detekt and ktlint's transitive jars float | locked in the root build file, which declares every plugin `apply false` |
| Verification | sha256 checksums; PGP + sha256 (Gradle docs) | PGP is stronger per Gradle but not every artifact is signed, a key list must be maintained, and checksums must be bootstrapped first anyway | sha256, generated while the full gate runs so detached configurations (lint, detekt, aapt2) are recorded; PGP is a later choice the ADR leaves open |
| Wrapper | `distributionSha256Sum` (present); jar check; `gradle/actions/wrapper-validation` in CI | the CI action is the community standard but CI does not exist yet | check both sums against services.gradle.org now; the action lands with `OPS-006` |

## Gap analysis — what we already have

| Concern | Ours today | Gap |
|---|---|---|
| Version locking | none in `app/`, `extension-api/`, `extensions/git/`, `extensions/ai/` build files | absent |
| Plugin classpath locking | none in `build.gradle.kts` | absent |
| Artifact verification | no `gradle/verification-metadata.xml` | absent |
| Wrapper distribution | `distributionSha256Sum` + `validateDistributionUrl=true` | present; the sum is not yet checked against Gradle's published one |
| Wrapper jar | `gradle/wrapper/gradle-wrapper.jar`, never checked | absent |

## Open decisions

- ~~Does STRICT hold under the full gate?~~ **Settled in T1:** it holds only when the locks are
  written while the whole gate runs (`--write-locks` on the `AGENTS.md` command plus
  `assembleDebugAndroidTest`). `<module>:dependencies --write-locks` alone left `:app:androidApis`
  without lock state, and the build failed on it. A planted upgrade fails on
  `{strictly 5.4.0} ... enforced by Dependency Locking`; a planted downgrade resolves the locked
  version without failing, so what resolves is always what is locked. The ADR and the `AGENTS.md`
  update note in T3 carry this.

## Tasks

- [x] T1 — **Locking.** STRICT `dependencyLocking { lockAllConfigurations() }` in each module and
  plugin-classpath locking in the root; `gradle.lockfile` per module and
  `buildscript-gradle.lockfile` committed; a planted drift fails, reverted.
- [ ] T2 — **Verification.** `gradle/verification-metadata.xml` from
  `--write-verification-metadata sha256` over the full gate; a planted wrong sum fails, reverted;
  the wrapper jar and distribution sums checked against services.gradle.org.
- [ ] T3 — **Close.** An ADR for the locking and verification choices, an `AGENTS.md` note on
  updating after a dependency bump (`--write-locks`, `--write-verification-metadata sha256`), this
  PRD deleted, the row `shipped` and `← next` back on `AUD-012`.

## Out of scope

- CI and `gradle/actions/wrapper-validation` — `OPS-006`.
- Scanning the locked versions for vulnerabilities — `AUD-012`.
- Dependabot keeping the locks current — `OPS-006`.
