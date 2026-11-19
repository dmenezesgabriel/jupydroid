# ADR-0163 — The error log is local, redacted when written, bounded, and shared only by the person

Status: accepted
Date: 2026-11-19

## Context

Before the error log, a crash left only what `logcat`'s ring buffer still held. The one durable log was the
server's `run/jupyter_server.log` under `files/`, which `run-as` reads on a debuggable build and not on the
published package. The app holds `android.permission.INTERNET` (`app/src/main/AndroidManifest.xml`), so that
nothing leaves the device is a decision to write down, not a consequence of the manifest.

Two shapes were read. Termux installs a `Thread.UncaughtExceptionHandler` that writes the crash to a file and then
hands the exception to the default handler (`termux-app@3df69d1da197`,
`termux-shared/src/main/java/com/termux/shared/crash/CrashHandler.java:18,35-41`); it records crashes only. A
native crash or an ANR runs no handler, and the platform's own record of why a process died
(`ActivityManager.getHistoricalProcessExitReasons`, Android 11) is the only trace of either, so the log takes both
(`app/src/main/java/com/jupydroid/app/data/crash/ErrorLogUncaughtExceptionHandler.kt`, `ExitReasonRecorder.kt`).

The server's bearer token appears in the shapes `SecretRedaction.kt` lists, and a stack or a message can carry
one. The app-specific external directory is the only place `adb pull` reads on a release build with no permission
(measured on `dev36`, API 36: a file the app wrote under `/sdcard/Android/data/com.jupydroid.app/files/` pulled
with neither a permission nor `run-as`).

## Decision drivers

- Nothing leaves the device unless the person sends it.
- A token never reaches a disk another app might read.
- Storage used by the log is bounded without anyone asking.
- A developer can pull the same file from a release build.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **A handler, the platform's exit reasons and a file, behind a port** | No dependency, nothing sent, a native crash and an ANR are covered | The app owns the writer, the caps and the redaction |
| A crash-reporting SDK | Grouping and a dashboard for free | A network call of its own and a dependency; the rule is official, well-maintained dependencies only |
| Handler only, as Termux | Smaller | Records no native crash and no ANR |
| Store under `files/` | Private to the app | Needs `run-as`, so a release build's file cannot be pulled |
| Redact when shared | One place for the rule | Android 10 and below let another app read the external directory, so the unredacted bytes would already sit there |
| No caps | Nothing lost | A log nobody clears grows without bound |

## Decision

**The app keeps its crashes and errors in a local log that only the person sends anywhere.** One JSON line per
entry, one file per UTC day (`errors-YYYY-MM-DD.jsonl`) under `getExternalFilesDir(null)`, written by `DiskErrorLog`
behind the `ErrorLog` port with a `FakeErrorLog`.

- **Redacted when written.** `redactSecrets` runs on an entry's message and stack before a byte lands, for the
  `Bearer` header, `token <16 or more characters>` and `token=` shapes.
- **Capped by size and age.** 1 MiB in total and 7 UTC days, the oldest day first, by `filesToPrune` at every start
  (`AppContainer`) and when a day's file opens. The newest day is never taken for its size, since the entry just
  written lives in it.
- **Shared by a tap.** Settings' "Share log" copies every day, oldest first, into one file under
  `cacheDir/shared-logs/` and hands it to Android's chooser over a `FileProvider` URI
  (`IntentErrorLogSharer`). "Clear logs" deletes the files behind a confirmation that states the size it frees.
- **No network call of the app's own** reads, uploads or reports the log.
- **A death someone chose is no entry.** `ProcessExitReason.DELIBERATE` has no kind, so a swipe away, a finish or an
  update does not fill the size cap with a line a day.

## Consequences

- Reproduce: `grep -rn "ADR-0163" docs/ app/src/`; `scripts/pull-app-logs.sh` pulls the directory without `run-as`
  and `scripts/read_app_log.py` groups its entries by exception class and top frame.
- Redaction is by shape. A secret in a shape `SECRET_SHAPES` does not name reaches the disk, so a new shape is a
  new entry there with its test; the external directory is the reason it cannot wait until sharing.
- The caps come from one measured entry, not from a day of use: 2.9 KB with a real stack and 18.7 KB for the
  same thrown 150 frames deep (`DiskErrorLogOnDeviceTest.anEntryWithARealStackHasAMeasuredSize`, `dev36`), so
  1 MiB holds about 360 of the first or 56 of the second. A day of real entries re-reads them; a change to either
  value replaces this decision.
- Not measured: the phone's Android version, and what another app can read on Android 10 and below. Redaction at
  write is chosen for that case, not proved against it.
- The server's log is outside this decision. It is still truncated at every server start, and its rotation under
  these caps waits for the arm64 tablet, since only arm64 ships a rootfs.
- "Report an issue", which would open the tracker for the person to submit, is not part of this decision; it
  sends nothing and is a later change.
