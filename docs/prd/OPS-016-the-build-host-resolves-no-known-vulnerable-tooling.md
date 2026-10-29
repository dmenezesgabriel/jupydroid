# OPS-016 — The build host resolves no known-vulnerable tooling

Milestone: `M8` · State: open · Depends on: — · Closes: `GAP-186`

> An **OPS** item ([ADR-0122](../adr/0122-work-that-ships-no-app-capability-is-an-ops-item.md)): it
> changes the repository's build, not a screen, so its *Demo* is the run that reproduces it and no
> Maestro flow is owed. **Deleted when the item ships** — its durable residue is the catalog and
> constraint comments plus a `docs/architecture.md` row edit
> ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

`osv-scanner` over the committed lockfiles reports no known-vulnerable Maven version: every plugin
jar and every tool classpath the build host resolves sits at or above its fixed release. None of
these jars ships in the APK — `docs/research/parity-ledger.md`'s AUD-012 row reads them on ktlint,
buildscript and AGP `_internal-unified-test-platform-*` configurations only — so what is repaired
is the machine that builds and tests the app, and the claim is measured, not assumed.

## Demo

The run, from a clean checkout, that anyone repeats:

1. `osv-scanner` (the pinned digest and flags of
   `evidence/aud-012-dependency-scans/README.md`) over every `gradle.lockfile` reports **zero Maven
   findings**.
2. `dependencyInsight` on each configuration the findings named — the root `classpath`, `ktlint`,
   and the `_internal-unified-test-platform-*` set — shows each flagged artifact either carried by
   a bumped plugin or resolved `by constraint` at or above its fixed version.
3. The gate plus `assembleDebugAndroidTest` is green, and `connectedDebugAndroidTest` on the
   emulator is green — UTP is the test runner itself, so that run exercises the forced versions.

Each step's output is quoted in the commit body of the task that lands it.

## Who else implements this

Read at the pins in [`../README.md`](../README.md): a `git ls-files` for `build.gradle*`,
`settings.gradle*`, `gradle.lockfile` and `verification-metadata.xml` over each clone. ADR-0123
already measured the locking half of this question — 0 of 7 lock Gradle dependencies — and the
floors half finds no more.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | Not a Gradle build |
| `vscode` | ABSENT | — | Not a Gradle build |
| `vscode-jupyter` | ABSENT | — | Not a Gradle build |
| `intellij-community` | ABSENT | `intellij-community@406a27a8 BUILD.bazel`, `MODULE.bazel.lock` | Bazel; its lock is Bazel's, not a Gradle answer |
| `termux-app` | ABSENT | `termux-app@3df69d1 build.gradle` | `classpath "com.android.tools.build:gradle:8.13.2"` floating in `buildscript {}` — no lock, no constraint |
| `winlator` | ABSENT | — | No constraint mechanism in its Gradle files |
| the shipped rootfs | ABSENT | — | Not a Gradle artifact |

**Count:** 0 of 7 constrains what its build tooling resolves. The authority is Gradle's own
documentation on [dependency constraints](https://docs.gradle.org/9.3.1/userguide/dependency_constraints.html),
the [locking procedure](https://docs.gradle.org/9.3.1/userguide/dependency_locking.html) ADR-0123
adopted, and the upstream POMs for what each plugin actually carries.

## Implementation inventory

### The flagged set and its carriers — measured on this tree, 2026-10-29

`./gradlew buildEnvironment` attributes every flagged `classpath` jar to
`com.android.tools.build:gradle` except `kotlin-gradle-plugin`, which the catalog itself pins.

| Artifact | Flagged at | Carrier / configuration | Fixed at (OSV, queried 2026-10-29) |
|---|---|---|---|
| `com.google.guava:guava` | 33.3.1-jre | AGP (`com.android.tools:common`, `sdklib`, `apksig`) — `classpath` | 33.7.2 |
| `org.apache.commons:commons-lang3` | 3.16.0 | `commons-compress:1.27.1` under `com.android.tools:repository` — `classpath` | 3.18.0 |
| `org.bitbucket.b_c:jose4j` | 0.9.5 | AGP (`jetifier` subtree) — `classpath` | 0.9.6 |
| `org.jdom:jdom2` | 2.0.6 | `jetifier-processor` — `classpath` | 2.0.6.1 |
| `org.bouncycastle:bcprov/bcpkix/bcutil-jdk18on` | 1.79 | `sdk-common`, `sdklib`, `apksig` — `classpath` | 1.85 (bcprov; bcpkix's lone advisory fixes at 1.84) |
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.3.20 | our catalog (`kotlin = "2.3.20"`); AGP asks 2.2.10 | 2.4.20-Beta1 — unsafe deserialization in the build cache (GHSA-r937-wjx7-w2jp); first stable past the fix is **2.4.21** |
| `org.apache.httpcomponents:httpclient` | resolves 4.5.14 | `classpath` | clean already (fix is 4.5.13) |

Per module — all four modules carry the same two configurations:

| Artifact | Flagged at | Carrier / configuration | Fixed at |
|---|---|---|---|
| `ch.qos.logback:logback-classic` + `logback-core` | 1.3.14 | `com.pinterest.ktlint:ktlint-cli:1.5.0` — `ktlint` | 1.5.34 (the last of its six advisories; classic must move in lockstep) |
| `com.google.guava:guava` | 32.0.1-jre and 33.3.1-jre | `com.google.testing.platform:*:0.0.9-alpha04` — `_internal-unified-test-platform-*` | 33.7.2 |
| `io.netty:netty-codec`, `-codec-http`, `-codec-http2`, `-common`, `-handler`, `-handler-proxy` | 4.1.93.Final and 4.1.110.Final | `grpc-netty:1.57.2` (`-core`) and `grpc-netty:1.69.1` (`-host-emulator-control`) | the family at ≥4.1.137.Final (the max across all flagged advisories); 4.1.139.Final is the newest 4.1.x |
| `org.apache.commons:commons-lang3` | 3.16.0 | `-android-test-plugin-result-listener-gradle` | 3.18.0 |
| `org.apache.httpcomponents:httpclient` | 4.5.6 | `-android-test-plugin-result-listener-gradle` | 4.5.13 (use 4.5.14 — what the classpath already resolves) |
| `org.bouncycastle` trio | 1.79 | `-android-test-plugin-result-listener-gradle` | 1.85 |

The UTP pins (`com.google.testing.platform:*:0.0.9-alpha04`) are constants inside AGP's jar, not
in its POM — what an AGP bump resolves cannot be read from outside, so T1 measures it.

### Plugin versions — measured 2026-10-29

| Plugin | Ours | Newest | What the bump moves |
|---|---|---|---|
| Gradle wrapper | 9.3.1 | current is 9.8.2 | AGP 9.5.0 requires Gradle ≥9.6.0 → **9.6.1**, the newest patch of the minimum minor |
| `com.android.tools.build:gradle` | 9.1.1 | 9.5.0 (9.6.0 is alpha-only) | every flagged `classpath` jar and all 13 UTP pins; still supports API 37, which is `compileSdk` |
| Kotlin | 2.3.20 | 2.4.21 stable | KGP itself past the fix |
| `org.jlleitschuh.gradle.ktlint` | 14.2.0 | 14.2.0 — already newest | nothing |
| ktlint CLI (the plugin's `ktlint` config) | 1.5.0 (default) | 1.8.0 | only logback 1.3.16 — still below the 1.5.34 floor |
| `dev.detekt` | 2.0.0-alpha.5 | 2.0.0-alpha.6 | carries nothing flagged |
| `foojay-resolver-convention` | 1.0.0 | 1.0.0 — already newest | nothing |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Bump AGP or floor everything | bump to 9.5.0 · leave 9.1.1 and constrain each flagged jar | A floor we own is a version upstream never tested together; the AGP bump moves most of the set for one edit and refreshes the UTP pins alongside | **Bump first, constrain the rest** — GAP-186's stated order, and each rescan names what is left |
| Which AGP | newest stable (9.5.0) · minimal step (9.2.x) | Only the newest carries the newest UTP pins; a smaller bump buys nothing the constraints do not already have to cover | **9.5.0**; it still supports API 37, `compileSdk` is unchanged |
| Wrapper version | 9.6.1 · 9.8.2 (current) | The bump is forced by AGP's minimum (9.6.0); newest patch of that minor keeps the change to what was asked | **9.6.1**, re-run through `wrapper` with `--gradle-distribution-sha256-sum` per AGENTS.md |
| Kotlin | 2.4.21 · stay at 2.3.20 | The only fixed line is ≥2.4.20; staying flagged fails the item's claim. The bump rides `compose-compiler` and `kotlin-serialization` on the same ref | **2.4.21** |
| ktlint CLI 1.8.0 | bump · hold at 1.5.0 | Its logback (1.3.16) is still flagged, and a formatter bump rewrites sources for no security gain | **Hold**; constrain logback alone |
| detekt alpha.6 | bump · hold | Carries nothing flagged; a bump is churn this item does not need | **Hold** |
| Floor versions | newest release · newest patch of the smallest line covering every flagged advisory | JD-069's convention is "what its own next release declares" — for these there is no declaring release, so the nearest analogue: the smallest covering line, at its newest patch | logback 1.5.38, netty 4.1.139.Final, bouncycastle 1.85.2, jose4j 0.9.7, jdom2 2.0.6.1, guava 33.7.2-jre, lang3 3.18.0 (same floor the app already floors at), httpclient 4.5.14 |
| Constraints vs `resolutionStrategy.force` | `constraints { add(...) }` · `force` | `force` would also pin *down* a higher version a future AGP might declare — silent; a floor only raises | **Constraints**, one per artifact, `because` naming the advisory URL — matching `app/build.gradle.kts`'s lang3 floor |
| Where the floors live | a `gradle/*.gradle.kts` script applied by each module · repeated in all four build files · `subprojects {}` in the root | The same set is needed in four modules; `subprojects {}` is the cross-project coupling ADR-0123 already rejected | **One script** (`gradle/dependency-floors.gradle.kts`), `apply(from = …)` in each module build file |
| `settings.gradle.kts` classpath | lock it · leave it | foojay resolves on it and `settings-gradle.lockfile` records only `empty=incomingCatalogForLibs0` — the scan reads no settings classpath today | **Open** — see below |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Plugin bumps bringing fixed versions | AGP 9.1.1, Kotlin 2.3.20 (`gradle/libs.versions.toml`) | none — 0 of 7 | absent |
| Floors under build-only classpaths | none | none | absent |
| Locked settings classpath | `settings-gradle.lockfile` records `empty=incomingCatalogForLibs0` | none | present but out of the scan's sight |

## Open decisions

- **What AGP 9.5.0 leaves flagged.** Its UTP pins are constants inside the jar. T1's rescan names
  the remainder; T3's floors cover it.
- **Whether AGP 9.5.0 accepts KGP 2.4.21.** AGP requests `kotlin-gradle-plugin:2.2.10`; newer KGP
  than tested has historically been a warning. T2 measures it.
- **Whether the netty floor breaks UTP's grpc** (1.57.2 and 1.69.1 were built against older netty).
  `connectedDebugAndroidTest` runs *through* those jars, so a green instrumented run is the answer.
- **Lock the settings classpath?** foojay resolves there and the scan never sees it. Three lines in
  `settings.gradle.kts` make the claim honest for everything the host resolves — T3 decides by
  whether it writes a `settings-gradle.lockfile` that scans clean.

## Surface

| Layer | Files |
|---|---|
| versions | `gradle/libs.versions.toml` (`androidGradlePlugin`, `kotlin`) |
| wrapper | `gradle/wrapper/gradle-wrapper.properties`, `gradle-wrapper.jar` |
| floors | `gradle/dependency-floors.gradle.kts` (new), applied in `app/build.gradle.kts`, `extension-api/build.gradle.kts`, `extensions/git/build.gradle.kts`, `extensions/ai/build.gradle.kts`; `classpath` floors in root `build.gradle.kts` |
| locks + sums | the five `gradle.lockfile`s, `settings-gradle.lockfile`, `gradle/verification-metadata.xml` |

## Tasks

One commit each; each regenerates locks and sums with the `AGENTS.md` procedure — the gate plus
`assembleDebugAndroidTest`, once with `--write-locks --write-verification-metadata sha256` together
then again with neither — and each commit body quotes the scan before and after.

- [x] T1 — Gradle wrapper → 9.6.1 and AGP → 9.5.0. The UTP lock entries only rewrite when those
  configurations resolve, so the write-locks run adds `connectedDebugAndroidTest` on the emulator
  (`dependencies --configuration` is the fallback where no device exists). Rescan names what the
  bump cleared.
- [x] T2 — Kotlin → 2.4.21 on the same `kotlin` ref (compose-compiler and serialization ride it).
  Same regen and rescan; the gate's `compileDebugKotlin` is the compat check.
- [x] T3 — The floors for whatever T2's rescan still names, in
  `gradle/dependency-floors.gradle.kts` + the root `classpath` block, decided per family: one
  `netty-bom` platform constraint per UTP configuration, the logback pair on `ktlint`, the singles
  per name. `connectedDebugAndroidTest` re-run exercises UTP under the floors. Rescan → zero Maven
  findings. Includes the settings-classpath decision.
- [ ] T4 — Ship: `docs/architecture.md`'s GAP-186 row resolved, backlog row edited, this PRD
  deleted. An ADR only if a floor's reason outlives the item.

## Out of scope

- `dev.detekt` 2.0.0-alpha.6, ktlint CLI 1.8.0 and Gradle 9.8.x — none of them moves a flagged jar.
- Scanning the lockfiles in CI is `OPS-007`; Dependabot is `OPS-006`.
- `releaseRuntimeClasspath` — `JD-069` and `JD-070` already closed it; this item is everything the
  host resolves *besides* it.
