# JD-080 — The Source Control panel says what is happening in every state

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-198`

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

## Out of scope

A refresh on a loaded repository, polling, "Initialize Repository" (VS Code offers it; `notARepositoryMessage`
leaves it out on purpose), and whether the escaped `IOException` ended the process: that stays with the log
JD-081 reads.
