# ADR-0123 — Dependencies are locked STRICT and verified by sha256

Status: accepted
Date: 2026-10-27

## Context

The Gradle build declared versions in `gradle/libs.versions.toml` and resolved everything else
transitively. No file recorded which exact jars a build used, so a security scanner such as
osv-scanner had nothing exact to read. A dependency, or a plugin's transitive jar, could also
change between two builds without any edit in this repository. Nothing checked that a downloaded
artifact was the one published.

The wrapper was not sound either. `gradle/wrapper/gradle-wrapper.jar` was Gradle 4.6's, left from
the project template. The bump to 9.3.1 had edited `gradle-wrapper.properties` and never re-ran the
`wrapper` task, so every `./gradlew` launched a jar eight majors behind the distribution it
downloaded. The jar was genuine, and its sha256 equalled Gradle's published 4.6 sum.

None of the references can settle this. Read at the pins in `docs/README.md`, 0 of 7 lock or verify
Gradle dependencies: jupyterlab, vscode and vscode-jupyter are not Gradle builds, intellij-community
uses its own module model, and winlator has neither. termux-app validates its wrapper jar in CI and
does nothing more. The authority is therefore Gradle's own documentation:
[Locking dependency versions](https://docs.gradle.org/9.3.1/userguide/dependency_locking.html) and
[Verifying dependencies](https://docs.gradle.org/9.3.1/userguide/dependency_verification.html).

## Decision drivers

- What ships is exactly what is committed, and a scanner reads it from files it parses natively.
- A tampered or substituted artifact fails the build instead of being used.
- A new configuration cannot quietly resolve free of the locks.
- Updating after a bump is one known command, not a hunt.

## Options considered

| Axis | Options | Trade-off |
|---|---|---|
| Where locking is declared | per module `dependencyLocking {}`; root `subprojects {}` | cross-project configuration couples modules and Gradle discourages it |
| Lock mode | DEFAULT, STRICT, LENIENT | DEFAULT silently accepts a configuration with no lock state; STRICT fails on it |
| Plugin classpath | locked through `buildscript { configurations.classpath }` in the root; left floating | unlocked, AGP, KGP, detekt and ktlint's transitive jars drift |
| Verification | sha256 checksums; PGP signatures plus sha256 | PGP is stronger, but not every artifact is signed, a trusted-key list must be kept, and checksums have to be bootstrapped first either way |
| Wrapper | `distributionSha256Sum` only; also the jar's sum; `gradle/actions/wrapper-validation` in CI | the CI action is the community standard, but this repository has no CI yet |

## Decision

- **Locking.** Each of `:app`, `:extension-api`, `:extensions:git` and `:extensions:ai` declares
  `dependencyLocking { lockAllConfigurations(); lockMode.set(LockMode.STRICT) }` in its own build
  file. The root locks the plugin classpath (`buildscript-gradle.lockfile`), and Gradle writes
  `settings-gradle.lockfile` for the catalog import. Every lockfile is committed.
- **STRICT holds only when the locks are written while the whole gate runs.** That means the
  `AGENTS.md` gate command plus `assembleDebugAndroidTest`, run with `--write-locks`. Writing them
  through `<module>:dependencies --write-locks` left `:app:androidApis` without lock state, and
  `:app:compileDebugKotlin` then failed on it.
- **A lock is a strict constraint.** An upgrade planted in the catalog fails resolution with
  `{strictly 5.4.0} ... Dependency version enforced by Dependency Locking`. A planted downgrade does
  not fail, but resolves the locked version, so the build still ships exactly what is locked.
- **Verification.** `gradle/verification-metadata.xml` records the sha256 of every artifact and
  metadata file the build resolves. It is generated with `--write-verification-metadata sha256`
  over the same whole-gate run, so the detached tool classpaths are recorded too: lint, detekt,
  ktlint, the Kotlin compiler, aapt2 and the Unified Test Platform. A wrong sum fails with
  "Dependency verification failed". Signatures stay off, and adopting PGP later is still open.
  `-sources` and `-javadoc` jars are trusted rather than recorded, as Gradle's guide recommends. The
  IDE downloads them, and no build uses them.
- **Wrapper.** Both `distributionSha256Sum` and the jar's sha256 equal Gradle's published sums at
  `services.gradle.org`. A Gradle bump re-runs the `wrapper` task with
  `--gradle-distribution-sha256-sum`, and never edits `gradle-wrapper.properties` by hand. Validating
  the wrapper in CI waits for CI to exist.

## Consequences

A dependency or plugin bump now costs two extra runs, recorded in `AGENTS.md`: `--write-locks`, then
`--write-verification-metadata sha256`. Both run over the whole gate. Committing the result shows
every transitive change in the diff, and the diff is the review.

The sums were recorded on Linux. `aapt2` is a per-OS artifact (`aapt2-<version>-linux.jar`), so a
build on macOS or Windows fails verification until that OS's sum is added there.

Gradle never removes unused entries from the verification metadata, so the file grows over time.
Pruning it means moving it aside, regenerating it with `--dry-run`, and comparing the two.

A future reader will be tempted to fix a failing verification by deleting the entry or setting
`verify-metadata` to false. Either one silences the check the file exists for. The answer is to
regenerate it, then read the diff.
