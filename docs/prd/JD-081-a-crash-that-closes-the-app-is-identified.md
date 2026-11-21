# JD-081 — A crash that closes the app is identified

Milestone: `M11` · State: open · Depends on: JD-082 (shipped) · Closes: GAP-199

## Outcome

The crash reported on the published build — the app closing itself while navigating, the example given
being the extensions — has its cause named from a device log and is repaired with a regression test.

## Demo

On the arm64 tablet (`SM-X216B`), the device `AGENTS.md` rule 2 names:

1. Cold start: install the current debug build, launch, enter `base`.
2. Drive the surfaces the report names — the Chat panel, Source Control, a `.chat` document — while the
   Space's `jupyter_server` cannot answer (its proot SIGSTOPped, or the server stopped so `connection()`
   must restart), as `scripts/git-panel-states-driver.sh` already does for a failed git read.
3. If the app dies: `scripts/pull-app-logs.sh` + `scripts/read_app_log.py` show the `CRASH` entry —
   exception class and top frame — and `adb logcat -b crash -d` agrees. That entry is the cause.
4. After the repair: the same drive leaves the app alive with the panel's failed state drawn, and the
   pulled log holds no new `CRASH` entry for it.

The item is not `shipped` until this has been run on that device, with screenshots, snapshots and
`maestro.log` captured under `evidence/<flow-name>/` (local, ADR-0132).

## Who else implements this

The capability is "a crash that closes the app is identified": recording enough of the death that its
cause can be read afterwards. The repair half is ours alone — no reference ships our app.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | ABSENT | — | a web application; a JS exception is a page error, and there is no process whose death needs recording |
| `vscode` | PARTIAL | `src/main.ts:459-480` (`--crash-reporter-directory`), `src/vs/platform/telemetry/common/telemetry.ts`, `src/vs/workbench/contrib/issue/browser/baseIssueReporterService.ts` | Electron's `crashReporter` writes native minidumps and telemetry is opt-in; `BaseIssueReporterService` is user-initiated — no always-on local log a person reads after a crash |
| `vscode-jupyter` | ABSENT | — | an extension inside the workbench; inherits whatever the workbench has |
| `intellij-community` | PARTIAL | `platform/platform-impl/src/com/intellij/diagnostic/DefaultIdeaErrorLogger.java`, `ExceptionAutoReportUtil.kt`, `ExceptionEAPAutoReportManager.kt` | errors land in `idea.log` and auto-report is opt-in; a desktop IDE's model, not a phone's |
| `termux-app` | FULL | `termux-shared/src/main/java/com/termux/shared/crash/CrashHandler.java`, `termux-shared/src/main/java/com/termux/shared/termux/TermuxCrashUtils.java` | an `UncaughtExceptionHandler` writes the crash to a file, chained to the default handler, and the next start shows it — the shape ADR-0163 already shipped |
| `winlator` | ABSENT | — | no `UncaughtExceptionHandler` or crash handler under `app/src/main` |
| the shipped rootfs | ABSENT | — | a Linux userland; it has no Android process whose crash the app must record |

**Count:** 1 of 7 implements this fully, 2 partially. The recording half is settled — Termux's shape is
what ADR-0163 shipped. What remains is the cause itself, which no reference can supply.

## Implementation inventory

### termux-app@3df69d1da197

| Concern | Files |
|---|---|
| Domain model | n/a — records the exception as it happened |
| Port / protocol | n/a |
| Adapter | `termux-shared/src/main/java/com/termux/shared/crash/CrashHandler.java` — `Thread.UncaughtExceptionHandler` writing the crash to a log file, then `defaultUEH.uncaughtException` |
| UI entry point | `termux-shared/src/main/java/com/termux/shared/termux/TermuxCrashUtils.java` — the next start reads the file back to the user |
| Persisted state | the crash file under the app's files directory |
| Empty / error / loading states | n/a |
| Accessibility specifics | n/a |
| Edge cases it handles | chains to the default handler so the platform still ends the process; records crashes only — no native crash, no ANR (the gap ADR-0163 closed with `ExitReasonRecorder`) |

### vscode@3f3380c668d0

| Concern | Files |
|---|---|
| Domain model | n/a |
| Port / protocol | n/a |
| Adapter | `src/main.ts` — Electron `crashReporter.start` gated on `--crash-reporter-directory`/telemetry consent |
| UI entry point | `src/vs/workbench/contrib/issue/browser/baseIssueReporterService.ts` — the user asks for the report |
| Persisted state | crash minidumps in the configured directory |
| Empty / error / loading states | n/a |
| Accessibility specifics | n/a |
| Edge cases it handles | native-process crashes only; a JS exception is not a `crashReporter` event |

### intellij-community@406a27a8da07

| Concern | Files |
|---|---|
| Domain model | n/a |
| Port / protocol | n/a |
| Adapter | `platform/platform-impl/src/com/intellij/diagnostic/DefaultIdeaErrorLogger.java` — the error logger behind `idea.log` |
| UI entry point | `platform/platform-impl/src/com/intellij/diagnostic/ExceptionAutoReportUtil.kt`, `ExceptionEAPAutoReportManager.kt` — opt-in auto reporting |
| Persisted state | `idea.log` under the IDE's system directory |
| Empty / error / loading states | n/a |
| Accessibility specifics | n/a |
| Edge cases it handles | submission is consent-gated; the local log is always written |

## Trade-offs and what we adopt

| Axis | Options seen (project → approach) | Trade-off | Our choice, and why |
|---|---|---|---|
| Where a failed server call becomes the port's exception | termux-app → none needed (no ports); our own `OkHttpGitStatusReader.answerBody` → wrap `IOException` as `GitStatusException` at the adapter | wrapping inside each call site vs once at the boundary | the adapter boundary, once per port call — the panel's catch stays as narrow as the port's contract says (the c8a5688c5 precedent); decided per cause in T2, not pre-emptively |
| How the cause is obtained | the reporter's phone log (unavailable to this host) vs reproducing on `SM-X216B` | reproduction may not hit an intermittent crash | reproduce on the tablet first; a proven escape path repaired red-first is a defect of the same class even if it is not provably the reporter's |

## Gap analysis — what we already have vs the references

| Concern | Ours today (file) | Best reference (`project@sha path`) | Gap |
|---|---|---|---|
| Crash recording | `data/crash/ErrorLogUncaughtExceptionHandler.kt`, `ExitReasonRecorder.kt`, `data/disk/DiskErrorLog.kt` (ADR-0163) | `termux-app@3df69d1da197 CrashHandler.java` | present — exceeds Termux's (exit reasons cover native crash and ANR) |
| Pulling and reading the log | `scripts/pull-app-logs.sh`, `scripts/read_app_log.py` | n/a | present |
| The crash's cause | nothing — no device log has named it | n/a | **absent** — this item |
| Exceptions escaping a contributed panel's catch | see below | n/a | suspects, unproven |

Suspects read on 2026-11-21, each an exception that leaves a `LaunchedEffect` uncaught and kills the
process — the class GAP-199 reports, on the surface it names:

- `extensions/ai/.../data/http/SpaceServerAnswer.kt:30-41` (`callWithToken`) calls `server.address()`,
  `baseUrl.toHttpUrl()` and `execute()` with no wrapping. `OkHttpChatFiles.call`
  (`OkHttpChatFiles.kt:71-77`), `OkHttpAgentChat.choose`/`stop` (`OkHttpAgentChat.kt:116,130`) ride it.
  `ChatFiles.list()` promises `ChatFilesException` "when the workspace cannot be read"
  (`domain/ChatFiles.kt`); `ChatList`'s effect catches only `ChatFilesException` and
  `QueuedQuestionsStoreException` (`ChatList.kt:109-115,118-137`), `ChatPanel.attempt` only
  `AgentChatException` and `QueuedQuestionsStoreException` (`ChatPanel.kt:121-131`). A raw `IOException`,
  an `IllegalArgumentException` from `toHttpUrl()`, or whatever `SpaceServer.connection()` rethrows
  (`data/space/SpaceServer.kt:144-149`) escapes both → crash.
- `OkHttpAgentChat.transcript()`'s `callbackFlow` calls `server.address()` unwrapped
  (`OkHttpAgentChat.kt:78`); `ChatReading.openOnce` catches only `AgentChatException`
  (`domain/ChatReading.kt:49-53`) → the same escape through `LaunchedEffect(chat)` in `ChatPanel.kt:107`.
- `extensions/git/.../data/http/OkHttpGitStatusReader.kt:34-36` — `server.address()` and `statusUrl`'s
  `toHttpUrl()` sit outside `answerBody`'s `IOException`→`GitStatusException` wrap (c8a5688c5), so an
  `address()` failure escapes `readStatus`'s `catch (GitStatusException)`
  (`SourceControlPanel.kt:61-65`) → crash.

## Deviations

- The crash was reported on a phone; this host has the tablet. `SM-X216B` is arm64 and runs the real
  Space, so a crash reproduced there is the same class; what it cannot prove is that it is the
  reporter's exact crash — stated, not assumed away.
- No crash-reporting SDK and nothing sent anywhere: ADR-0163 already decided local-only.

## Open decisions

- Whether the reproduced cause is the reported one. Only the reporter's phone log settles it; this item
  closes on a cause the device log names and a regression test, and says plainly if reproduction stayed
  bounded-negative.

## Surface

| Layer | Files |
|---|---|
| `domain/model` | — |
| `domain/port` | — (the ports' exception contracts already exist) |
| `data/<mechanism>` | `extensions/ai/src/main/java/com/jupydroid/extensions/ai/data/http/` (`SpaceServerAnswer.kt`, `OkHttpChatFiles.kt`, `OkHttpAgentChat.kt`), `extensions/git/src/main/java/com/jupydroid/extensions/git/data/http/OkHttpGitStatusReader.kt` |
| `data/fake` | — |
| `ui/<screen>` | — (repairs stay inside the adapters; the panels' catches are already right) |
| `di/AppContainer` | — |
| unit tests | `extensions/ai/src/test/.../data/http/`, `extensions/git/src/test/.../data/http/` |
| instrumented tests | only if a repair touches something "visible to press" |
| `.maestro/` flow + `scripts/` driver | the reproduction driver T1 needs, if the drive is not a few adb calls |

## Tasks

- [x] T1 — Reproduce on `SM-X216B` and name the cause from the pulled error log: drive the Chat panel,
  Source Control and a `.chat` document against a frozen or restarting Space server; `pull-app-logs.sh`
  + `read_app_log.py` + `logcat -b crash -d` name the exception or record a clean bounded pass.
- [x] T2 — Repair the cause the log names, red test first: the escaping failure becomes the port's
  exception type at the adapter boundary (the `answerBody` precedent), with a regression test.
- [ ] T3 — Sweep the same class across the contributed adapters: every `server.address()` /
  `toHttpUrl()` / unwrapped call that escapes its port's exception contract gets the same treatment,
  each proven escape red-first.
- [ ] T4 — Demo on the tablet (the same drive now leaves the failed state, not a dead app), delete this
  PRD, close `GAP-199` and the backlog row.

## Out of scope

- Native crashes and ANRs: `ExitReasonRecorder` already records them; a cause there would be its own item.
- Sending anything anywhere: ADR-0163 decided local-only; "Report an issue" is a later change it names.
- The reporter's phone itself: unreachable from this host; reproduction on the tablet is the stand-in.
