# JD-070 — Every native library in the APK has full RELRO

Milestone: `M8` · State: open · Depends on: — · Closes: GAP-187

## Outcome

Every `.so` the APK ships has a `GNU_RELRO` segment linked `BIND_NOW` on every ABI, so its GOT is
read-only once relocations resolve. Today one of the two prebuilts is below that:
`libzstd-jni-1.5.7-9.so` has no RELRO at all on arm64-v8a and partial RELRO on the other three ABIs
(`evidence/aud-012-code-scans/mobsf-native-libs.tsv`), while `libandroidx.graphics.path.so` is Full
everywhere and Alpine's `libproot*.so` already read Full on arm64.

## Demo

This runs no cell, but a Space's create exercises the repaired code, so it goes on the arm64 tablet
(`SM-X216B`): the arm64 .so is the one with no RELRO today.

1. Install the APK built with the bump over the one on the tablet (`adb install -r`).
2. Create a Space. The ~14 s image copy runs `SpaceImageInstaller` → `TarZstdExtractor`, which is
   commons-compress's `ZstdCompressorInputStream` → `com.github.luben.zstd` → the arm64-v8a
   `libzstd-jni-<version>.so` itself — the exact file this item repairs. The row `space-1 · ...`
   appears only after the extract returns.
3. Kill and relaunch — the Space persists.

The item is not `shipped` until this has run on the tablet, with screenshots, snapshots and
`maestro.log` under `evidence/jd-070-native-libs-relro/`.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | Not an Android app |
| `vscode` | ABSENT | — | Not an Android app |
| `vscode-jupyter` | ABSENT | — | Not an Android app |
| `intellij-community` | ABSENT | — | Not an Android app |
| `termux-app` | ABSENT | — | Bootstrap is apt `.deb`s, no zstd-jni |
| `winlator` | PARTIAL | `winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40 app/app/build.gradle` | Ships the same `com.github.luben:zstd-jni@aar` for the same `.tzst` install — pinned `1.5.2-3`, older than our `-9`, so its .so predates the RELRO fix too |
| the shipped rootfs | — | — | This item is the APK's `.so`s, not the image's |

**Count:** 0 of 7 ship it fixed. The authority is the upstream project:
[luben/zstd-jni#381](https://github.com/luben/zstd-jni/issues/381) is our finding, raised by another
consumer of the AAR, and closed on the third attempt.

## Implementation inventory

### ours

| Concern | Files |
|---|---|
| Version | `gradle/libs.versions.toml` (`zstdJni = "1.5.7-9"`) |
| Resolution | `app/gradle.lockfile`, STRICT `dependencyLocking` in `app/build.gradle.kts` |
| Checksums | `gradle/verification-metadata.xml` |
| The path it serves | `implementation("...@aar")` → `data/space/TarZstdExtractor.kt` → commons-compress's `ZstdCompressorInputStream` → `com.github.luben.zstd` |
| The audit's reading | `evidence/aud-012-code-scans/mobsf-native-libs.tsv`: `libzstd-jni-1.5.7-9.so` = No RELRO on arm64-v8a, Partial on armeabi-v7a/x86/x86_64 |
| Update procedure | `AGENTS.md`: the gate plus `assembleDebugAndroidTest`, `--write-locks` and `--write-verification-metadata sha256` on the same run, then again with neither |

### upstream (luben/zstd-jni)

| Release | What its Android .so is |
|---|---|
| 1.5.7-9 (ours) | No `GNU_RELRO` on arm64; the segment present but no `BIND_NOW` elsewhere |
| 1.5.7-10 | `-z relro -z now` added → `dlopen` fails on 16 KiB devices ("program alignment (4096) cannot be smaller than system page size (16384)") |
| 1.5.7-11 | RELRO reverted — the workaround, not the fix |
| 1.5.7-12 | `-fuse-ld=lld` fixed the bfd mis-link, but the RELRO segment still ends 4 KiB past a boundary → `PageSizeMismatchDialog` |
| 1.5.7-13 | `common-page-size=16384` added; verified on a real 16 KiB device. NDK moved to r29 |
| 1.5.7-24 | Verified in this session: all four ABIs carry `GNU_RELRO` + `FLAGS BIND_NOW` + `FLAGS_1 NOW`, LOAD `p_align` 0x4000, RELRO ends 0x7c000/0x60000/0x8c000/0x90000 — every end 16 KiB-aligned |
| 1.5.7-25 | Maven Central 2026-10-28, one day old and no upstream tag; its delta is unrelated API additions (`ZstdDecompressCtx.setLongMax`, dict-training guards) |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Which zstd-jni | 1.5.7-13 (first fixed) · 1.5.7-24 (newest tagged, 11 days old) · 1.5.7-25 (newest, 1 day old) | Any ≥-13 carries the fix. -25's delta touches nothing `ZstdCompressorInputStream` calls, but it is a day old and has no tag. The -10/-11/-12 line must not be landed on at all | **1.5.7-24** — the newest release with an upstream tag (`v1.5.7-24`, `ad254df`) and a week of soak. Upstream's own history here is three shipped regressions found by users within days |
| How to prove the ELF | Re-run MobSF on the APK · `readelf` the `.so`s directly | MobSF is the audit's whole-APK scan in docker; its native-libs table *is* a `readelf` reading plus columns this item does not assert | **`readelf`, wrapped in `scripts/read_apk_native_libs.py`** — same facts MobSF derives, plus the RELRO-end alignment column #381 showed a plain Full/Partial read misses |
| Raise upstream | File the issue · it is already closed | The item said "raised upstream if it is unchanged" — it changed, on #381 | **Nothing to file.** The PRD records where it landed instead |

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| zstd-jni's .so at full RELRO | `1.5.7-9` — none on arm64, partial elsewhere | present but stale; fixed upstream since 1.5.7-13 |
| A reading of every APK `.so`'s RELRO | AUD-012's one-shot MobSF table | absent as a repeatable measure |

## Open decisions

None. The version question is settled by the ELF readings above (taken 2026-10-29 from the
`1.5.7-9` and `1.5.7-24` AARs on Maven Central), and "raise upstream" resolved to "already closed".

## Surface

| Layer | Files |
|---|---|
| versions | `gradle/libs.versions.toml` |
| locks and sums | `app/gradle.lockfile`, `gradle/verification-metadata.xml` (every lockfile's diff is read) |
| measure | `scripts/read_apk_native_libs.py`, `scripts/read_apk_native_libs_test.py` |
| `.maestro/` flow + `scripts/` driver | `.maestro/jd-070-native-libs-relro-demo-*.yaml`, `scripts/jd-070-native-libs-relro-demo-driver.sh` |
| evidence | `evidence/jd-070-native-libs-relro/` |

## Tasks

- [ ] T1 — The measure. Red: no tool reads the APK's `.so`s for RELRO. Green:
  `scripts/read_apk_native_libs.py` classifies each `lib/**/*.so` in an APK or AAR —
  `none`/`partial`/`full` plus whether the RELRO segment ends on a 16 KiB boundary — with fixture
  tests built from the real `readelf` output of the `-9` and `-24` binaries. Its run on the
  current APK reproduces AUD-012's rows; its run on the `-24` AAR reads all-Full.
- [ ] T2 — The bump. `zstdJni = "1.5.7-24"`, a catalog comment recording that ≥1.5.7-13 is the
  release line whose Android .so links full RELRO (the upstream issue URL), then the gate plus
  `assembleDebugAndroidTest` with `--write-locks` and `--write-verification-metadata sha256`
  together, and again with neither (ADR-0123's order). The lockfile diff shows only the zstd-jni
  move; the measure on the built APK reads every `.so` Full.
- [ ] T3 — The demo on the tablet, with evidence. The driver refuses to run unless the lockfile
  resolves `1.5.7-24` and the measure on the built APK is all-Full. Then ship: delete this PRD and
  edit the backlog row.

## Out of scope

- The `libproot*.so` canary question — AUD-012 already read it by hand (static-pie, no symbol
  table; MobSF's check cannot see a canary either way).
- A CI gate re-reading the APK's ELF — the audit cadence owns re-measurement; `OPS-007` owns CI
  scanners.
