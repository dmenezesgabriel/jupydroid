# JD-082 — The app keeps a bounded local log of its crashes and errors

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-200`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

After the app crashes, a person on a release build can open Settings, see what was recorded, share it or clear it,
and a developer can pull the same file over `adb` without `run-as`. Nothing is sent anywhere, the log never grows
past its caps, and a bearer token never reaches the disk.

## Demo

Everything here runs without a cell, so the device is `dev36` (AGENTS.md rule 2); the one task that needs a Space's
server, T8, is blocked on the tablet and is not part of this demo until it runs there.

1. Cold start, install the debug build on `dev36`, open Settings, "Diagnostics": no entries, 0 B.
2. Cause a crash, relaunch: one `crash` entry with the exception class and the top frame, and an `exit` entry from
   the platform's own record of the death.
3. `scripts/pull-app-logs.sh` pulls the directory without `run-as`; `scripts/read_app_log.py` groups the entries by
   exception class and top frame.
4. "Share log" opens the share sheet with the file; "Clear logs" states the size it frees, and after confirming,
   Diagnostics reads 0 B.
5. A screenshot and a UI dump at every step that changes the screen, looked at.

Not `shipped` until this has run on `dev36`, and T8 on the tablet.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `termux-app` | FULL | `termux-shared/.../crash/CrashHandler.java`, `TermuxCrashUtils.java` | A `Thread.UncaughtExceptionHandler` writes the crash to a file, chains to the default handler, and the next start tells the user from that file |
| `vscode` | PARTIAL | `src/vs/workbench/contrib/issue/browser/baseIssueReporterService.ts` | An issue reporter the person submits; no automatic file |
| `jupyterlab`, `vscode-jupyter`, `intellij-community`, `winlator` | not read | — | Not opened for this item: `GAP-200` read two references and the platform API, and found the shape in those two |

The pinned SHAs and paths are in `GAP-200` (`architecture.md`). The one disagreement that matters: Termux writes
on crash only, and the platform's exit reasons (`ActivityManager.getHistoricalProcessExitReasons`, Android 11) are
the only record of a native crash or an ANR, where no handler runs. This item takes both.

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| Handler | none in `app/src/main` (`JupydroidApplication.kt` installs nothing) | absent |
| Exit reasons | none read | absent |
| Where a log sits | the server's `run/jupyter_server.log` under `files/`, readable only by `run-as` on a debuggable build | a release build's file cannot be pulled |
| Settings | `ui/settings/SettingsScreen.kt` has no diagnostics row | absent |
| Share | a `FileProvider` with authority `${applicationId}.exports` (`AndroidManifest.xml`) | present, reusable |
| Confirmation stating a count | `ui/shell/RunningSidePanel.kt`, "Shut Down All" | present, the pattern to copy |

## Trade-offs and what we adopt

| Axis | Options seen | Our choice, and why |
|---|---|---|
| Library | a crash-reporting SDK; or a handler and a file | The handler. Nothing is sent, and the handler needs no dependency (only official, well-maintained dependencies are used) |
| Where it is stored | `files/` (needs `run-as`); or the app-specific external directory | External: `adb pull` reads it on a release build, with no permission. Android 10 and below let other apps read it, so **an entry is redacted when written**, not when shared |
| Caps | none; or a size cap and an age cap | Both, oldest first, at every start and every rotation. The values are set from the measured size of an entry (T2) and re-read against a day of entries (T9), not guessed |
| Reaching the writer | a global; or a port | A port with a `Fake…` in `data/fake/`, injected through `AppContainer` (AGENTS.md) |

## Open decisions

1. The caps' values: 1 MiB and 7 days, from what T2 could measure. Nothing appended yet, so there was no day of
   use to measure; one entry is 2.9 KB with a real stack and 18.7 KB with the same thrown 150 frames deep
   (`DiskErrorLogOnDeviceTest`, `dev36`), so 1 MiB holds about 360 or 56 entries. T9 re-reads them against a
   day of real entries once T3 and T4 write some.
2. The phone's Android version, and Android 10 and below, where other apps can read the external directory:
   not measured. `adb pull` itself is answered: on `dev36` (API 36, Android 16)
   `adb pull /sdcard/Android/data/com.jupydroid.app/files/...` reads a file the app wrote, with no permission and no
   `run-as`.
3. Whether the cause `adb shell am crash` leaves reaches the handler or only the exit reasons: both. On `dev36`
   (API 36) it throws `RemoteServiceException$CrashedByAdbException` on `main`, the handler appends a `CRASH` entry
   with that class, "shell-induced crash" and the stack, and `dumpsys activity exit-info` also lists the death as
   `reason=4 (APP CRASH(EXCEPTION))`. The demo's step 2 uses `am crash` for both entries.

## Surface

| Layer | Files |
|---|---|
| `domain/port` | an error-log port with a `Fake` in `data/fake/` |
| `data/<mechanism>` | a disk adapter under the external files directory; the exit-reason reader behind its own port |
| `ui/settings` | a "Diagnostics" section in `SettingsScreen.kt` |
| `di/AppContainer`, `JupydroidApplication` | the handler installed once per process, the writer built once |
| unit tests | redaction (a token in a stack), caps, the chained default handler, the grouping reader |
| instrumented tests | Diagnostics shows the size, "Clear logs" confirms with it |
| `.maestro/` flow + `scripts/` | `scripts/pull-app-logs.sh`, `scripts/read_app_log.py` (+ `_test.py`), a flow and a driver for the demo |

## Tasks

- [x] T1 — One JSON line per event written under the external files directory, behind a port with a Fake; redaction
  before a byte lands (red: a stack carrying a bearer token). Measures `adb pull` on `dev36`.
- [x] T2 — Size and age caps pruned oldest first at start and rotation; the values from a measured day.
- [x] T3 — The uncaught-exception handler, installed once in `JupydroidApplication`, chains to the default one
  (red: a handler built with a fake default).
- [x] T4 — The platform's exit reasons since the last start are appended at every start (a death someone chose is
  skipped; the mark lives in `filesDir`, outside the log, so "Clear logs" does not make old deaths read as new).
- [x] T5a — Settings "Diagnostics": the entry count and size, and "Clear logs" behind a confirmation that states the
  size (a read and a delete on the `ErrorLog` port, a ViewModel, a section of `SettingsScreen`).
- [x] T5b — "Share log" through the `FileProvider`: every day file bundled, oldest first, into one file under
  `cacheDir/shared-logs/`, which `res/xml/exported_documents.xml` now covers with a `cache-path`.
- [x] T6 — `scripts/pull-app-logs.sh` and `scripts/read_app_log.py` with a test and a fixture.
- [ ] T7 — The ADR for the policy: local, person-initiated sharing, no network call of its own, the caps.
- [ ] T8 — The server's log rotates under the caps instead of being truncated at every server start
  (`data/space/JupyterServerLauncher.kt`). **Blocked until the tablet is attached to the host:** the rotation is
  proved by a Space's server restarting, and only arm64 ships a rootfs, so `dev36` has no server log to rotate.
- [ ] T9 — The demo on `dev36`, as a flow and a driver; the backlog row `partial` until T8 has run on the tablet,
  then `shipped`, this file deleted.

## Out of scope

Sending a log anywhere, "Report an issue" (`GAP-200` offers it; it opens the tracker and sends nothing, so it is a
later row), and naming the cause of the crash `GAP-199` reports, which `JD-081` reads from a device log.
