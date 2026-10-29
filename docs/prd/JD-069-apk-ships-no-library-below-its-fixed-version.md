# JD-069 — The APK ships no library below its fixed version

Milestone: `M8` · State: open · Depends on: — · Closes: GAP-185

## Outcome

Every jar `releaseRuntimeClasspath` resolves is at or past the version that fixes its published
advisories, so the APK a user installs carries no library a scanner can match to a fixable
advisory. Today osv-scanner flags two that are below: `org.jsoup:jsoup` 1.23.1
([GHSA-65r4-943x-97jj](https://github.com/advisories/GHSA-65r4-943x-97jj), fixed in 1.23.2) and
`org.apache.commons:commons-lang3` 3.16.0
([GHSA-j288-q9x7-2f5v](https://github.com/advisories/GHSA-j288-q9x7-2f5v), fixed in 3.18.0),
which `commons-compress` 1.27.1 carries in transitively.

## Demo

This runs a cell, so it goes on the arm64 tablet (`SM-X216B`).

1. Install the APK built with the bump over the one on the tablet (`adb install -r`).
2. Create a Space. The ~14 s image copy runs `SpaceImageInstaller` → `TarZstdExtractor`, which
   is commons-compress's `TarArchiveInputStream` and `ZstdCompressorInputStream` - the library
   that carries lang3 inside it.
3. In base, open a seeded notebook and run a cell that emits `display(HTML("…"))`. `text/html`
   renders through `HtmlDocument` (jsoup): the markup is drawn as blocks, and its tags are not
   on screen.
4. Kill and relaunch - the Space persists, and the cell renders the same again.

The item is not `shipped` until this has run on the tablet, with screenshots, snapshots and
`maestro.log` under `evidence/jd-069-apk-advisories/`.

## Who else implements this

ADR-0123 already measured the neighbouring question: at the pins in
[`../README.md`](../README.md), 0 of 7 lock or verify Gradle dependencies, so none keeps a
scanner-readable dependency set at fixed versions either.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | Not a Gradle build |
| `vscode` | ABSENT | — | Not a Gradle build |
| `vscode-jupyter` | ABSENT | — | Not a Gradle build |
| `intellij-community` | ABSENT | — | Its own module model, not Gradle-resolved jars |
| `termux-app` | ABSENT | — | Gradle, but locks nothing; validates only the wrapper in CI (ADR-0123) |
| `winlator` | ABSENT | — | No locking or verification |
| the shipped rootfs | — | — | This item is the APK half of the repair JD-064 made for the image |

**Count:** 0 of 7. The authorities are Gradle's own documentation (ADR-0123) and the two
advisories linked in *Outcome*.

## Implementation inventory

### ours

| Concern | Files |
|---|---|
| Versions | `gradle/libs.versions.toml` (`jsoup`, `commonsCompress`) |
| Resolution | `app/gradle.lockfile`, STRICT `dependencyLocking` in `app/build.gradle.kts` |
| Checksums | `gradle/verification-metadata.xml` |
| jsoup's path | `implementation(libs.jsoup)` → `html/HtmlDocument.kt`, which `ui/editor/RenderedOutput.kt` calls on a `text/html` output |
| lang3's carrier | `implementation(libs.commons.compress)` → `data/space/TarZstdExtractor.kt`; commons-compress 1.27.1 declares `commons-lang3:3.16.0` compile-scoped (its POM) |
| Regression net for the jsoup patch | `HtmlDocumentTest`, `HtmlInlinesTest`, `HtmlTableTest` |
| The audit's ship-check | `evidence/aud-012-dependency-scans/README.md`: osv-scanner over `app/gradle.lockfile`, findings filtered to `releaseRuntimeClasspath` |
| Update procedure | `AGENTS.md`: the gate plus `assembleDebugAndroidTest`, `--write-locks` then `--write-verification-metadata sha256` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| How lang3 moves | bump `commons-compress` to 1.28.0, whose POM declares lang3 3.18.0 (and moves commons-io to 2.20.0, commons-codec to 1.19.0) · a `constraints {}` floor on `implementation` | A bump moves four jars at once and upgrades the library `TarZstdExtractor` is written against, which this item did not read. A floor moves exactly the flagged jar | **Constraint.** `constraints { implementation(libs.commons.lang3) }` is a floor, not a declared need - nothing of ours calls lang3. It is also the backlog's own wording |
| Which lang3 | 3.18.0 (the first fixed) · 3.21.0 (the newest) | Identical cost here; nothing calls the library either way | **3.18.0**, the version commons-compress's own next release (1.28.0) declares: the pairing upstream itself ships, so the floor matches what a compress bump would have resolved |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| jsoup at its fixed version | `jsoup = "1.23.1"` in the catalog | present but stale |
| lang3 at its fixed version | 3.16.0, transitive on `releaseRuntimeClasspath` via commons-compress | present but stale |
| A scan gate keeping this true | none; AUD-012 ran osv-scanner by hand | absent - `OPS-007` owns it, not this item |

## Open decisions

None on the repair. AUD-012 already read reachability: neither flaw is reachable today - HTML
enters only through `Jsoup.parseBodyFragment` (`html/HtmlDocument.kt`), nothing calls
`Parser.xmlParser`, and nothing under any `src/main` calls `ClassUtils`. That changes what the
item must prove (that the shipped versions moved), not whether it repairs.

## Surface

| Layer | Files |
|---|---|
| versions | `gradle/libs.versions.toml` |
| `:app` build | `app/build.gradle.kts` (`constraints {}`) |
| locks and sums | `app/gradle.lockfile`, `gradle/verification-metadata.xml` (every lockfile's diff is read) |
| `.maestro/` flow + `scripts/` driver | `.maestro/jd-069-apk-advisories-demo-*.yaml`, `scripts/jd-069-apk-advisories-demo-driver.sh` |
| evidence | `evidence/jd-069-apk-advisories/` |

## Tasks

- [x] T1 — The bump and the floor. Red: on `releaseRuntimeClasspath`, `dependencyInsight`
  resolves jsoup 1.23.1 and lang3 3.16.0, and the audit's ship-check flags both. Green:
  `jsoup = "1.23.2"`, `commonsLang3 = "3.18.0"` declared as an `implementation` constraint,
  then the gate plus `assembleDebugAndroidTest` run once with
  `--write-locks --write-verification-metadata sha256` together and again with neither
  (ADR-0123's two-flag order fails: unlocked resolution downloads candidate metadata the
  recorded sums never covered). The lockfile diff shows only the
  two moves, the ship-check is clean, and the html/ tests still pass.
- [x] T2 — The demo on the tablet, with evidence. Then ship: delete this PRD and edit the
  backlog row.

## Out of scope

- The 3.16.0 that AGP's `_internal-unified-test-platform-*` configurations and the buildscript
  `classpath` keep resolving is build tooling, which no APK ships - `GAP-186`, repaired by
  `OPS-016`.
- A scanner gate in CI is `OPS-007`.
