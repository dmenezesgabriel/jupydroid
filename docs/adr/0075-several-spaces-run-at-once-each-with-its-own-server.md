# ADR-0075 — Several Spaces run at once, each with its own server, stopped through `api/shutdown`

Status: accepted
Date: 2026-10-05

## Context

The Spaces home ([ADR-0074](0074-the-app-opens-on-a-spaces-home.md)) gives every Space a row, and
the row is where Run and Stop go. Until then one `jupyter_server` ran, base's, started lazily by the
first client that needed it and stopped by the app-leave backstop (`IdleServerStopper`).

The references disagree on how many environments run at once:

- Winlator runs one container at a time. Run on a row starts a `singleTask` activity, and its exit
  stops the environment
  (`winlator@fb66541b93a4eb3ee585a433b4c7b20544d58e40 app/app/src/main/java/com/winlator/ContainersFragment.java`
  `runContainer`, `XServerDisplayActivity.java` `exit`).
- SageMaker Studio runs many spaces at once, each with its own Run and Stop, and Stop keeps the
  space's volume (<https://docs.aws.amazon.com/sagemaker/latest/dg/studio-updated-running-stop.html>).
- Termux keeps many sessions alive in one foreground service, and one action stops them all
  (`termux-app@3df69d1da197dd9bd71a3bafd902dffd720576b4 app/src/main/java/com/termux/app/TermuxService.java`).

They also disagree on how to stop a server. JupyterLab shuts down its sessions and terminals and
then posts `api/shutdown` to its own server
(`jupyterlab@7f0e690680a763d3c3db5dc0231803b0ba0e1e12 packages/mainmenu-extension/src/index.ts`,
`CommandIDs.shutdown`). Winlator kills the process, and so did this app.

Measured on SM-X216B by `TwoSpacesAtOnceDeviceTest`, each case on a fresh tree:

- A running Space costs **~124 MB**: proot ~0.36 MB, `jupyter_server` ~66 MB, a booted kernel ~57 MB.
  Two run side by side on distinct ports, and each refuses the other's token.
- **`POST api/shutdown` is the only stop that works under proot.** It took the whole tree down in
  772–991 ms while the other Space kept answering. SIGTERM to proot, which is what
  `Process.destroy()` sends, stopped nothing: all three processes were alive 10 s later. SIGKILL to
  proot orphans its tracees, so the server and kernel run on outside the app's process tree.

## Decision drivers

- A user with a long computation in one Space should be able to start another without losing it.
- A Space's server must stop when asked, and stopping one must not touch another.
- Memory is the cost, and the app must not hold it once the user has left.

## Options considered

| Option | Pro | Con |
|---|---|---|
| One Space at a time (Winlator) | One server's memory at most | Running a second Space stops the first and its kernels |
| Several at once (SageMaker, Termux) | Spaces are independent | ~124 MB each |
| Stop by signal (Winlator, this app before) | No dependency on a live HTTP endpoint | Under proot SIGTERM is ignored and SIGKILL orphans the server |
| Stop by `api/shutdown` (JupyterLab) | The server takes its own kernels down; measured to work under proot | Needs the server to be answering |

## Decision

**Several Spaces run at once, with no cap.** The `SpaceServers` port
(`app/src/main/java/com/jupydroid/app/domain/port/SpaceServers.kt`) runs, stops and reports each
Space's server. `JupyterSpaceServers` keeps one `SpaceServer` per Space, each with its own
`JupyterServerLauncher`, because a launcher's `start` begins with `stop()` and a shared one would
stop the first Space when the second runs. `running` follows the processes rather than the calls,
so a server that stops itself when idle reads Stopped on the home exactly as a Stop does.

**A server is stopped by `POST api/shutdown`**, and the launcher waits for the process to exit. A
server that outlives the wait is logged and kept, never signalled (`JupyterServerLauncher.stop`).

**Leaving the app stops every running Space.** After 30 s with no cell running and no terminal open,
the backstop calls `SpaceServers.stopAll()` (`AppContainer.idleServerStopper`). Delete stops a
running Space before it removes the directory.

## Consequences

- Each running Space holds ~124 MB. If that becomes a problem, a cap belongs in `JupyterSpaceServers`,
  measured first.
- No foreground service: a Space does not keep running once the app is left, unlike Termux.
- A kernel's websocket upgrade can take longer than OkHttp's 10 s default when another Space is
  busy, so the opener waits jupyter_server's 60 s `kernel_info_timeout`
  (`app/src/main/java/com/jupydroid/app/data/space/OkHttpKernelSocketOpener.kt`).
- Every Space's idle time is the same today. A per-Space idle time is a setting, not a change to
  this decision.
