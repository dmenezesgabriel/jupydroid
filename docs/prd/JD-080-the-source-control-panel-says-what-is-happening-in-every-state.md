# JD-080 — The Source Control panel says what is happening in every state

Milestone: `M11` · State: partial · Depends on: — · Closes: `GAP-198`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

Opening Source Control never shows a blank panel: while git is being read it says so, when the folder is not a
repository it says so, and when the read fails it says why, with a "Retry" that reads again. A folder that is
not a repository offers the same "Retry", so a person who runs `git init` in the terminal sees the branch
without leaving the panel.

## Demo

The failed read needs a Space's `jupyter_server`, so the device is the arm64 tablet `SM-X216B`
(AGENTS.md rule 2); on `dev36` there is no rootfs and the panel reads `FakeGitStatusReader`, which only
answers "not a repository".

1. Cold start, enter `base`, open Source Control on the workspace root: "You are not currently in a Git
   repository." and a "Retry" button.
2. In the Space's terminal run `git init demo`, tap "Retry": the panel still says so for the root. Browse into
   `demo`, open Source Control: the branch. (The panel reads the folder the explorer shows.)
3. Stop `jupyter_server` (`adb shell run-as com.jupydroid.app kill <pid>`), reopen Source Control: a message that
   names the path and the refusal, and a "Retry"; never a blank panel.
4. A screenshot and a UI dump at every step that changes the screen, looked at.

Not `shipped` until this has run on the tablet.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab-git` | ABSENT | `src/components/GitPanel.tsx:431-436` | An empty `div` while `repository === null`; failures go to `console.error`; no retry |
| `vscode` | PARTIAL | `extensions/git/package.json` `viewsWelcome`, `package.nls.json` `view.workbench.scm.*` | A welcome text per state (git missing, no repository, scanning) with a button for the next act |
| `vscode-jupyter` | ABSENT | — | Has no Git panel |
| `intellij-community` | not read | — | Not opened: its tool window is a desktop surface with a status bar; the question here is text in a panel |
| `termux-app`, `winlator`, the shipped rootfs | ABSENT | — | No Git surface |

**Count:** 1 of 7 says what state it is in (VS Code's welcome views); the one we port from (`jupyterlab-git`)
draws nothing. So "reading" and "failed" are our own decisions, and "not a repository" keeps
`jupyterlab-git`'s wording (`SourceControlWording.kt`).

## Gap analysis

| Concern | Ours today | Gap |
|---|---|---|
| Reading | `SourceControlPanel.kt`, `StatusReading.Pending -> Unit` | present and blank |
| Not a repository | `notARepositoryMessage`, a bare `Text` | present, no way to read again |
| Failed read | `StatusReading.Failed`, a bare error `Text` | present for `GitStatusException` only |
| Refused connection or timeout | `OkHttpGitStatusReader.status`, `client.newCall(request).execute()` throws `IOException` | the port says it throws `GitStatusException` only; the panel catches only that, so the exception leaves the `LaunchedEffect` and nothing is drawn (GAP-199's unproven crash lead) |

## Trade-offs and what we adopt

| Axis | Options seen | Our choice, and why |
|---|---|---|
| Where `IOException` becomes a failure | the adapter throws `GitStatusException`; or the panel catches `IOException` too | The adapter. `GitStatusReader.status` promises `GitStatusException` for every failure, so the adapter broke the port, and every other reader and caller then keeps relying on it |
| How long a silent server is waited on | a shorter call timeout; or the `OkHttpClient` default (10 s connect and read) | The default, unchanged: it already bounds the wait, and the reading line makes it visible |
| Retry | none (jupyterlab-git); a button on failure; a button on failure and on "not a repository" | Both states: GAP-198 names "not a Git repository included", and the folder can become one under the panel. A loaded repository gets none (a refresh is another item) |

## Surface

| Layer | Files |
|---|---|
| `data/http` | `extensions/git/.../data/http/OkHttpGitStatusReader.kt` |
| `ui` | `extensions/git/.../ui/SourceControlPanel.kt`, `SourceControlWording.kt` |
| unit tests | `OkHttpGitStatusReaderTest.kt`, `SourceControlWordingTest.kt` |
| instrumented tests | `app/src/androidTest/.../ui/shell/ContributedSidePanelTest.kt` (the only Compose rule that reaches this panel; `:extensions:git` has no Compose UI test dependency) |
| `.maestro/` flow + `scripts/` driver | `.maestro/git-panel-states.yaml`, `scripts/git-panel-states-driver.sh` |

No new port: `GitStatusReader` and `FakeGitStatusReader` already exist.

## Tasks

- [x] T1 — A refused connection or a timeout is a `GitStatusException` naming the path and the cause, thrown by
  `OkHttpGitStatusReader` (red: a shut-down `MockWebServer`, and `SocketPolicy.NO_RESPONSE` with a 100 ms
  `readTimeout` client).
- [x] T2 — Every state says something: the reading line, and "Retry" on a failed read and on "not a repository"
  (red: `ContributedSidePanelTest` with a reader that never answers and one that fails once).
- [ ] T3 — The demo on the tablet, as a flow and a driver; delete this PRD, mark the backlog row `shipped`.
  **Blocked until the tablet is attached to the host:** `adb devices`, `lsusb` and `adb mdns services` listed no
  device on 2026-11-19 after an adb server restart, and the failed read needs a Space's `jupyter_server`, which
  only arm64 ships. No flow or driver is written until it can run: one that has never run proves nothing. The
  plan below is what to build when it is back.

## T3's plan, for when the tablet is attached

Two flows and one driver, modelled on `scripts/git-panel-changed-files-driver.sh` (repository set up, rotation,
rows read back) and `scripts/ai-chat-socket-failure-driver.sh` (the server stopped while a flow runs).

- **`.maestro/git-panel-states-part1-root-and-demo.yaml`**: landscape, `launchApp`, `runFlow: open-base-space.yaml`.
  Demo step 1: open Source Control on the root, assert "You are not currently in a Git repository." and
  "Retry", screenshot. Demo step 2: New Launcher, terminal, `git init -q demo`; tap "Retry" and assert the root
  still says so; tap `demo` in Files, open Source Control, assert `master` and no "Retry", screenshot. A pty
  echoes a typed line whether or not a shell ran it, so the branch the panel names is the evidence, and the
  driver reads `demo/.git/HEAD` back through `run-as`.
- **`.maestro/git-panel-states-part2-server-stopped.yaml`**: **no `launchApp`**, which would start the server
  again. Reopen Source Control after the driver stopped the server and wait for a message naming the path and
  the refusal, with "Retry". "Never blank" is asserted as the red message and the button together, since a
  missing node cannot be asserted. The message text is read from `OkHttpGitStatusReader` when the flow is
  written, not guessed.
- **`scripts/git-panel-states-driver.sh`**: `reset_notebooks`, `rotate_to 1`, `trap restore_rotation`; run part
  1 through `run_flow`; `open_forward`, then `POST /api/shutdown`; run part 2; `pull_server_log`; a
  `uiautomator dump` and a screenshot at every step that changes the screen, kept in the local evidence directory (ADR-0132);
  exit non-zero on any failure. Tablet only, run under `nice -n 10 timeout 1800` with no devjob slot, as the
  driver of the external tablet it is.
- **Decision: the server is stopped with `POST /api/shutdown`, not `kill <pid>`** (demo step 3 says `kill`).
  `/api/shutdown` is the `ServerApp.stop()` the idle shutdown also ends in, and `space-server.sh` already opens
  the forward and holds the token, so the driver needs no pid. The end state is the same: nothing listens on
  the port, and the panel's next read is refused. The demo step is reworded to match when the driver lands.
- **At close**, after a green run whose frames were looked at: delete this file, mark the row `shipped`, move the
  marker back to the next open row, and check whether the `GAP-198` row in `research/parity-ledger.md` (its
  `System` frames, "repaired by `JD-080`") needs its verdict set. The commit body says `Demo run by the agent, not
  by a human`, names the driver command, and quotes what each frame showed.

## Out of scope

A refresh on a loaded repository, polling, "Initialize Repository" (VS Code offers it; `notARepositoryMessage`
leaves it out on purpose), and whether the escaped `IOException` ended the process: that stays with the log
JD-081 reads.
