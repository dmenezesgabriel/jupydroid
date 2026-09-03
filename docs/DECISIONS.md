# Decisions — what is settled, and what it forbids

Every entry here is a constraint on future work, not a history entry. Each was measured or read
out of a source, and each names where. If a decision here disagrees with code, the code is wrong.

How things get built is `CLAUDE.md`. What gets built next is `docs/PLAN.md`. This file is why
neither may be re-litigated without new evidence.

Citations are either a path in `reference/research_repos/…` or a path **inside the shipped
rootfs**, readable without extracting:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tgz
tar -xzOf $T usr/lib/python3.12/site-packages/ipykernel/kernelapp.py | sed -n '605,620p'
```

---

## Runtime

### Exec model

**A `targetSdk 36` app cannot `execve` anything under `filesDir`.** AOSP's SELinux change removed
`execute_no_trans` from app data files and kept `execute` (commit `0dd738d8`, recorded from the
original Step 8 research):

| Operation on a file in `filesDir`, targetSdk ≥ 29 | Allowed? |
|---|---|
| `execve()` — needs `execute_no_trans` | **blocked** |
| `dlopen()` / `mmap(PROT_EXEC)` — needs `execute` | allowed |

The behaviour stands on the device evidence regardless of the commit id: the app works only
because it is arranged this way (`evidence/step8.2*`, `evidence/step8.3/`).

Winlator `ProcessBuilder`-execs out of `filesDir` only because it sets `targetSdkVersion 28`
(`reference/research_repos/winlator/app/app/build.gradle:9`; Termux does the same). **We do not
follow it there.**

**So: anything that must be executed ships in `jniLibs/<abi>/` and is reached through a symlink
from the rootfs.** Android extracts `jniLibs` to `/data/app/…/lib/<abi>/` with SELinux context
`apk_data_file`, where execution is permitted; the kernel resolves the symlink and checks the
*target's* context. Two files use this today — `libloader.so` (musl's loader) and `libpython3.so`
(CPython) — patched at build time by `tools/rootfs/stage-app-artifacts.sh`:

```
patchelf --set-interpreter /data/data/com.jupydroid.app/files/spaces/base/lib/ld-musl-aarch64.so.1 \
         --set-rpath      /data/data/com.jupydroid.app/files/spaces/base/usr/lib:…/lib \
         libpython3.so
```

`PT_INTERP` names a **rootfs** path because `/data/data/<pkg>` is stable and `nativeLibraryDir`
is not; `BaseSpaceInstaller.linkLoaderToNativeLibrary` points that path at the native library on
every launch.

**This forbids:** proot, chroot, `LD_PRELOAD` exec interceptors, `/system/bin/linker64` tricks, a
targetSdk downgrade, and the NDK (nothing is compiled — we ship prebuilt ELFs). It also means
**every new tool costs an ABI-sized ELF in `jniLibs`** — which is what defers uv and what the
terminal must pay for busybox.

`useLegacyPackaging = true` is load-bearing: without it the libs stay inside the APK.

### busybox is built here, not taken from Alpine, and one flag is why

**Alpine's busybox cannot run a single applet in this app's process.** It is built with
`CONFIG_FEATURE_SUID=y`, which compiles in `check_suid()` - called on *every* applet dispatch
(`libbb/appletlib.c:965`) and ending, for every `BB_SUID_DROP` applet, in `setgid(rgid); setuid(ruid);`.
Both are on bionic's `libc/SECCOMP_BLOCKLIST_APP.TXT` for lp64, an app process is zygote-spawned
and carries that filter, and busybox has no SIGSYS handler - so `sh`, `ls` and `echo` alike were
killed about seven milliseconds after exec, having printed nothing. Full account in
`evidence/p4-terminal/busybox-under-app-seccomp.md`.

So `tools/rootfs/Dockerfile` builds it, from Alpine's own `busyboxconfig` for the same version with
**one line flipped** (`tools/rootfs/busybox.config`). `CONFIG_FEATURE_SUID=n` replaces `check_suid`
with `#define check_suid(x) ((void)0)`, so the calls are gone at compile time.

**This is the reference implementation's setting, not an invention.** Termux runs busybox in an
Android app process under this same filter and sets `# CONFIG_FEATURE_SUID is not set`
(`termux-packages/packages/busybox/busybox.config:32`). Alpine sets it the other way because Alpine
is a distribution, where `su` and `passwd` need it.

**utmps is linked statically**, following Alpine's *static* recipe (`APKBUILD:219`,
`-Wl,--push-state,-Bstatic`) rather than its dynamic one. The rootfs carries no libutmps - the base
image's busybox is Alpine's BOOTSTRAP build, which skips it - so a dynamic link made every applet
fail on device with `Error loading shared library libutmps.so.0.1`.

**This forbids** taking busybox from `apk add` again, and it is gated:
`scripts/check_busybox_seccomp.py` fails on a config that restores the flag and on any staged
`libbusybox.so` carrying the `check_suid` fingerprint. **A `run-as` gate cannot enforce it** -
that process is not under the app's seccomp filter, which is exactly how P4b shipped this defect
green. The device gate is `BusyBoxAppletsDeviceTest`, which forks applets from inside the app.

### The Space's shell is named once, in `SHELL`, and three things read it

`SpaceRuntime.environment()` sets `SHELL` to `spaceShellIn(baseDir)`. Three consumers, all read in
the shipped source rather than assumed:

- `jupyter_server_terminals` consults it before falling back to `which("sh")` - though P4c pins
  `shell_command` outright, so this is its belt;
- pexpect resolves it for a cell's printing escape, `!ls` (`_process_posix.py:48`);
- and `IPython/utils/_process_common.py:86-90` passes it as `Popen(executable=…)` for the *capture*
  escape, `files = !ls`. **Unset, that stays `None` and CPython falls back to the absolute
  `/bin/sh`, which Android does not have** - so the command ran nowhere and the cell got an empty
  string back, with no error. That one is why this is a decision and not a tidy-up;
  `evidence/p4-terminal/shell-escapes.md` has the before and after.

### musl, not glibc; and arm64 ships, x86_64 does not (yet)

Android 16's app seccomp filter kills glibc's libc startup with SIGSYS on both ABIs; musl starts
cleanly under the same filter with everything else held constant. The Jupyter stack still comes
from ordinary wheels — musllinux rather than manylinux — so nothing is hand-cross-compiled.
`evidence/step8.3/`.

x86_64 ships no rootfs, and **the reason has changed even though the rule has not**. It was
"the same filter kills musl there too"; that is superseded. The refusals are now named — seven of
them, all cases where musl issues a legacy syscall and bionic issues the `*at` form, which do not
exist in the arm64 kernel ABI at all — and `tools/rootfs/musl_at_shim.c` routes every one. With it,
`jupyter_server` starts on the emulator, `POST /api/sessions` returns 201 and a kernel starts, with
**zero** refused syscalls. `evidence/x86-64-seccomp-syscalls/`.

What still stops it is that the kernel dies after starting, for a reason that is *not* seccomp and
not memory. So `SpaceAbi` stays arm64-only, the app still logs
`no Space runtime here: no rootfs is built for ABI 'x86_64'` and falls back to
`FakeKernelGateway`, which echoes a cell's source back as its output. **No emulator flow may run
a cell** — it would go green against the fake. That rule is unchanged and stays unchanged until a
cell actually runs there; when it does, it will still be weaker evidence than the tablet, because
x86_64 reaches the kernel through a shim arm64 does not use.

An arm64 emulator is not the way round it on an x86_64 host: the emulator refuses the AVD outright
(`not supported by the QEMU2 emulator on x86_64 host`), on both engines, and NDK translation
segfaults on an `execve`'d arm64 binary. Both measured, in the same evidence directory.

### SIGSYS is a signal to handle, not a verdict

Android refuses syscalls outside bionic's allowlist with `SECCOMP_RET_TRAP`, and an unhandled
SIGSYS terminates the process — which is why `import numpy` used to die.
`tools/rootfs/sigsys_report.c` installs a handler that reports the syscall and returns, leaving
the `ENOSYS` the kernel already set. numpy and scipy then work in-process; no VM, no emulation.
`evidence/step8.7/`.

Reports go to a log file, never stderr: ipykernel forwards a kernel's stderr as cell output.

**Residual:** a refused syscall a package genuinely *needs* is still fatal. The set is discovered
by running software and reading the reported refusals — bounded and observable, not open-ended.

### Two Android hazards inside CPython, both worked around, both still true

- **`psutil.net_if_addrs()` segfaults.** `getifaddrs()` gets EACCES from Android's network
  restrictions and psutil does not check the return — `psutil/__init__.py:2246`,
  `rawlist = _psplatform.net_if_addrs()`, `exit=139`. The SIGSYS handler cannot help: this is a
  SIGSEGV. psutil cannot be dropped (an `ipykernel` dependency), but ipykernel only uses it for
  process management, so the hazard is one function reachable only from a user's own notebook.
  `--KernelManager.transport=ipc` exists partly to keep `jupyter_client` off it.
- **IOPub's forked-subprocess pipe binds TCP and aborts.** `IOPubThread._setup_pipe_in` binds
  `tcp://127.0.0.1` regardless of transport; on Android libzmq turns the EACCES into an
  `errno_assert` → SIGABRT that ipykernel's `except zmq.ZMQError` never reaches.
  `tools/rootfs/jupydroid_kernel.py` selects the library's own default `IOPubThread(pipe=False)`.
  **Cost:** output from processes the kernel *forks* is not captured; cell output, tracebacks and
  display data are unaffected. `evidence/step8.5/getifaddrs-root-cause.txt`.

### `nativeLibraryDir` moves on every app update

The extracted Space survives an update; the native library directory does not (per-install random
path segments). So the loader symlink **and** the kernelspec `argv` are rewritten on every launch,
not just on a fresh install. Found the hard way: `TarGzExtractor` once wrote *through* the stale
loader symlink and crash-looped the app.

**Every install-time path needs a second install to be tested at all.**

### Kernel logging cannot leak into cell output

Three links, all verified in the shipped tree, and worth knowing before turning on any kernel
logging: `jupyter_client/launcher.py:68-76` leaves `stdout`/`stderr` as `None` off Windows, so the
kernel **inherits** the server's; `JupyterServerLauncher` redirects the server's output to
`run/jupyter_server.log`; and `ipykernel/kernelapp.py:546-554` detects a log handler bound to fd 2
and reroutes it to the raw file descriptor (*"Seeing logger to stderr, rerouting to raw
filedescriptor"*). So kernel logs land in the server's log file, not in the notebook.

**Measured, once the kernel was actually put at DEBUG** (P0a): 29 KB of kernel DEBUG in
`jupyter_server.log` and nothing on screen, `evidence/kernel-observability/shell-dispatch-logged/`.
It is upstream behaviour rather than ours, so `step10-run-cell-real-kernel` asserts it —
`assertNotVisible: ".*MESSAGE TYPE.*"` after the run — instead of trusting that an ipykernel
upgrade will keep it.

---

## Protocol and behaviour

### nbformat is an open format, so a writer that rebuilds is a shredder

Every interface in `packages/nbformat/src/index.ts` extends `PartialJSONObject` — "these keys, plus
anything else" is the contract. Unknown fields ride on the domain type as `PreservedJson` and are
written back verbatim; a duplicated cell keeps them, because there is nothing on disk to merge a
new id with. Eight distinct losses (`tags`, `attachments`, `language_info`, …) were found by
measuring rather than reading.

### Kernels start through sessions

`POST /api/sessions` with `{path, type, name}` — `type` is `ext === '.ipynb' ? 'notebook' : 'file'`
and `name` is the basename (`packages/docregistry/src/context.ts:82-90`); the session model carries
the kernel (`packages/services/src/session/session.ts:381`, `IModel.kernel`).

**Reattach needs no client-side lookup.** `jupyter_server/services/sessions/handlers.py:85-90`
returns the existing session when the path already has one
(`session_exists` → `get_session`, else `create_session`), so a `listRunning()`-and-find would be
duplicated logic, not parity. Measured: two visits to one notebook produce two client session ids
and **one** kernel id, and the second POST takes 2 ms against the first's 854 ms
(`evidence/step17-sessions/`). `interrupt` and `restart` stay on `api/kernels/<id>`, where
JupyterLab leaves them too.

### An unanswered greeting replaces the kernel; it is never asked twice

`kernel_info_request` going unanswered means the shell channel is not carrying replies, and
asking it again does not change that — measured, 2 stalls in 80 runs with 120 asks and no reply
(`evidence/kernel-observability/seventh-sighting/`). Every sighting cleared on a fresh kernel, so
the recovery is a **restart followed by one more greeting**, and if that is no better the cell
**fails** rather than being handed to a channel known to be silent.

Three consequences, each deliberate:

- **The socket is not reopened with it.** JupyterLab's `restart()` follows the REST call with
  `reconnect()` for "cases where kernel ports have changed" (`default.ts:547-563`), but
  `jupyter_server` calls `km.restart_kernel(kernel_id)` with `newports` defaulting to False, and
  its `on_kernel_restarted` only publishes a status. The endpoints do not move.
- **This departs from the reference, which sends the pending messages anyway after the timeout.**
  That failsafe is what produced the stall: a request enqueued against a channel carrying nothing,
  waited on for ever with no error raised anywhere.
- **Typing never triggers it.** `complete` waits on the same handshake, so recovery would
  otherwise discard the variables of a kernel someone had merely opened a notebook onto. A
  completion has an answer for "the kernel cannot say"; a cell does not.

### The app stops its own server, so the app repairs what that breaks

Left alone, the app stops `jupyter_server` (P1c). Three things follow, and each is a constraint:

- **A run is in flight from the moment it is asked for, not from its first frame.** Opening the
  kernel *is* part of a run: on the tablet the Space install, the server start and the kernel start
  are ~14 s before a cell's `execute_request` goes anywhere. A count that starts later says
  "nothing is running" through the slowest part of a run, and the backstop killed a 30 s cell 16 s
  into it on exactly that reading (`evidence/p1c-app-death-backstop/`). `ActiveCellRuns` is the one
  place that counts, and `NotebookKernel` moves it around the whole of `execute`.
- **The idle grace is *continuous* idleness.** Run All finishes one cell before starting the next,
  so the count passes through zero between every pair; a grace that sampled once would fire there.
- **A session that outlives its server is re-opened silently, never reported.** The app stopped
  that server, so a dead kernel is not news to the user. `BaseSpaceKernelGateway` knows which
  server issued which session and refuses a stale one (`StaleKernelSessionException`);
  `NotebookKernelSession` opens a fresh one and runs the cell — **exactly one retry**, because a
  second refusal is a real failure. Measured cost: 1-3 s of server + kernel start, ~7 s
  tap-to-answer, against ~130 MB reclaimed (`evidence/step18-lifecycle/`).

  **This departs from the reference.** JupyterLab reports a dead kernel — `SessionContext`
  displays the kernel's error on `status === 'dead'`
  (`packages/apputils/src/sessioncontext.tsx:1167`), and its `KernelConnection` asks the REST API
  whether the kernel is really gone before saying so (`packages/services/src/kernel/default.ts`,
  `getKernelModel` on socket close). Nothing there stops a server on the client's behalf, so
  there a death is always news. We do not probe either: the gateway *knows* it replaced the
  server, which is a fact rather than an inference from a 404.

**The server dies with the app, so there is nothing to reap.** `am force-stop` with a server
running leaves no `jupyter_server` process (`evidence/p1c-app-death-backstop/orphan-after-force-stop.txt`)
— the server is a child of the app process. P1c is therefore about the app being *left*, not about
it being killed, whatever `docs/PLAN.md` once called it.

### A kernel is ended by a command, never by closing the document

`DELETE api/sessions/<id>` exists and is reached from two menu items —
`notebook:shutdown-kernel` and `notebook:close-and-shutdown`
(`packages/notebook-extension/src/index.ts:184-186,3171-3213`). **Closing a notebook does not
reach it.** `docs/PLAN.md` P1d once said it should, and the reference says otherwise twice: the
`kernelShutdown` setting is *"Whether to shut down or not the kernel when closing a notebook"* with
`"default": false` (`packages/notebook-extension/schema/tracker.json:741-745`), and
`SessionContext.dispose()` shuts down only `if (this.kernelPreference.shutdownOnDispose)`
(`sessioncontext.tsx:690`).

That default is also what makes reattach reachable at all: leaving a notebook calls `disconnect`,
the kernel lives on, and reopening finds it by path with its namespace intact
("Kernels start through sessions", above). Both are measured on one build —
`notebook-session-reattaches` prints 44 after a *leave*, and `p1d-part3` raises `NameError` after
a *shutdown* (`evidence/p1d-shutdown-kernel/`).

Three rules follow:

- **Only 204 is success**, plus 404 read as "somebody got there first" and logged; 410 keeps the
  server's own sentence about a kernel deleted before its session
  (`services/session/restapi.ts:65-80`, `services/sessions/handlers.py:203-212`). A 200 is a
  refusal.
- **`KernelSession` carries three ids and they are not interchangeable**: ours (signs every
  message), the kernel's (`interrupt`, `restart`), and the server's session id (`shutdown`).
- **Shutting down must never start a server**, exactly as `disconnect` must not: a kernel whose
  server P1c stopped is already gone.

Whether closing *ever* shuts down is P6c's, where `kernelShutdown` becomes a setting.

### The kernel's working directory is the session's path

Without it a cell's `open('data.csv','w')` raises `OSError: [Errno 30]` on a read-only filesystem.
The server root (`--ServerApp.root_dir`), the kernel's `HOME` and the repository root are **one
tree**.

### The server's own cwd is that tree as well, because upstream it cannot be anything else

`jupyter_server` declares **no** default for `root_dir`; the `@default` under it returns
`os.getcwd()` (`serverapp.py:1839-1848`). So everywhere but here the server's process cwd and its
`root_dir` are one directory *by construction* - you run `jupyter lab` somewhere and that somewhere
becomes both. X2h passed `--ServerApp.root_dir` explicitly and left `ProcessBuilder` alone, which
split them: root_dir the workspace, cwd Android's read-only `/`.

**Kernels could not see it and terminals cannot avoid it.** A kernel's cwd comes from `root_dir`
through `cwd_for_path` (`services/kernels/kernelmanager.py:211-218`). A terminal's is *inherited* -
`PtyWithClients` is handed `cwd=None` whenever the `POST` carries none
(`terminado/management.py:225-232`), which is what JupyterLab's own launcher sends
(`terminal-extension/src/index.ts:514`). That is why eight phases of runtime work went by.

**So `JupyterServerLauncher` calls `.directory(rootDir)`.** Measured, not reasoned: with the line
backed out, `/proc/<server pid>/cwd` on the tablet read `/`
(`evidence/p4-terminal/server-working-dir.md`).

Every reference sets a spawned shell's cwd **at the spawn point** rather than inheriting, and
defaults it to the workspace or home: Termux `chdir`s the forked child
(`terminal-emulator/src/main/jni/termux.c:101`) to the same constant it uses for `$HOME`
(`TermuxShellEnvironment.java:78, 101`); Winlator calls `ProcessBuilder.directory`
(`core/ProcessHelper.java:68`); VS Code resolves an absolute cwd and **fails the launch** if it is
not a directory (`terminalProcess.ts:261-273`); IntelliJ walks configured → default → project path
→ home, checking each exists (`LocalOptionsConfigurer.java:77-101`). vscode-jupyter has this app's
divergence exactly - `--notebook-dir` with no child cwd
(`jupyterInterpreterSubCommandExecutionService.node.ts:100-135`) - and never reaches the defect
because it runs only kernels against that server.

**This forbids** the alternative repair: sending an absolute `cwd` on every `POST /api/terminals`.
It leaves the server's cwd wrong for P5's LSP and P6b's Console, and it leaves the handler's
*relative* branch broken - `api_handlers.py:41-49` resolves a relative `cwd` against the process cwd
**before** falling back to `server_root_dir`, so under cwd `/` a request for `notebooks` would
silently mean `/notebooks`. That branch is what "Open Terminal Here" uses.

**Two gates, because one is one-sided.** `JupyterServerWorkingDirTest` asserts the directory *and*
that it equals the `--ServerApp.root_dir` argument - a launcher pointed somewhere else would pass a
test that only checked the first. `TerminalServiceTest.theServerProcessRunsInTheWorkspaceItIsRootedAt`
reads the **running** process, which no argument assertion can see.

### A terminal is ready when `setup` arrives, not when the socket opens

`TermSocket.open` sends `["setup", {}]` and *then* appends the client to `terminal.clients`
(`terminado/websocket.py`). The websocket handshake completes a round trip earlier, so a frame
written in between is accepted by the socket and dropped by a server that has not associated it -
**silently, at both ends**. The reference refuses to call itself connected until `setup`
(`packages/services/src/terminal/default.ts:301-307`) and queues what is typed meanwhile
(`_sendMessage` into `_pendingMessages`).

So `TerminalGateway.send` and `.resize` **suspend** until readiness rather than failing: a screen
composes its key handler and its collector independently, so a keystroke can genuinely arrive first
and refusing would make that a crash on a race the UI cannot order. Bounded at ten seconds, and the
readiness signal is completed *exceptionally* when a socket dies before `setup` - a stale name
answers 404 at upgrade, and every pending keystroke has to learn that rather than merely stop. P0 is
the argument against shipping a silent forever-wait.

**This forbids** treating `JupyterSocketEvent.Opened` as readiness anywhere on this wire.

### The terminal socket is opened by the attach, and the route is outside `api/`

Two shapes that are not the kernel's.

`r"/terminals/websocket/(\w+)"` is registered by `jupyter_server_terminals/app.py` while that same
extension's REST handlers are `r"/api/terminals"`. Getting the prefix wrong is a 404 at upgrade and
nothing else.

And the socket belongs to the *attach*, not to the gateway. A kernel's socket is held from the first
execution until the document closes, because the server counts connections against the kernel. A
terminal has no per-request traffic - its socket **is** the reading of it - so it opens when the
flow is collected and closes when the collection stops. That is what keeps the first prompt:
`TermSocket.open` writes `setup` and immediately drains `read_buffer`, so a socket opened eagerly
would deliver both into a flow with no subscriber and no replay. It is also what makes leaving the
screen cheap, since the shell lives on and the next attach is replayed into.

**This forbids** a `BaseSpaceTerminalGateway` registry of issued names. `BaseSpaceKernelGateway`
needs one because a stale kernel session surfaces as an undebuggable 404; a terminal's does not - a
replaced server lists no terminals, and an attach to a name it never minted is refused by
`TermSocket.get` and arrives as `TerminalEvent.Ended` carrying that 404. A registry would invent a
state the wire already reports.

### A pty echoes, so "type X, read X back" proves nothing

`docs/PLAN.md`'s P4d gate was *"`send("echo ok\n")` -> `ok` read back"*. A terminal in canonical
mode echoes the typed line before any program sees it, so `ok` arrives inside the echo of the
command whether or not a shell ever ran. Measured on the first green run: the captured output was
`… $ echo ok` and nothing after it.

The same defect, sharper, for the working directory: busybox ash's default `PS1` is `\w \$`, so the
cwd is in the **prompt** - and the prompt is replayed out of `read_buffer` the instant an attach
connects. A bare `pwd` matched before the command had been echoed, let alone run.

**So an assertion on a shell's output must name something that cannot appear in the command text or
the prompt**: `echo $((6*7))` -> `42`, `echo cwd=$(pwd)` -> `cwd=<path>`. This is
`scripts/check_maestro_output_assertions.py`'s rule - *a post-Run wait must need a kernel* -
reaching the terminal by the same route, and the third time in P4 a gate has been green against the
wrong thing (P4b's `sh -c 'echo ok'`, P4c's `GET /api/terminals`).

### One `jupyter_server` per instrumentation run

Instrumentation runs every test class in one process, so a second `JupyterServerLauncher` starts a
second server against the same `run/` directory - where `JupyterRuntimeFile.clear` deletes every
`jpserver-*.json` and `read` takes the first by name. Two servers there means one wiping the other's
file and either reading the other's port, and both truncating the one `jupyter_server.log` every
Space investigation reads.

`BaseSpaceUnderTest` is therefore the test suite's composition root, holding the one server, the way
`JupydroidApplication` holds the one container per process. **This forbids** any device test calling
`baseSpaceServerLauncher` for itself.

### Cell semantics come from `NotebookActions`, and most are not the obvious answer

Read out of `packages/notebook/src/actions.tsx` and `packages/cells/src/model.ts`:

- Deleting the **last** cell leaves an empty cell, not an empty notebook — of
  `notebookConfig.defaultCell`, not hardcoded "code" — and the replacement is inserted **inside
  the same transaction**, "to make the deletion of a notebook's last cell undoable". A cell with
  `deletable: false` is skipped, and deleting a collapsed heading takes its children.
- Clearing outputs also clears the **execution count** — `clearExecution` at
  `packages/cells/src/model.ts:706` sets `executionCount = null`.
- A **duplicate keeps** the original's outputs and count; only its id is renewed — and the one
  exception is deliberate: a code cell pasted after a **cut** keeps its id (`addCells`, the
  `lastClipboardInteraction === 'cut'` branch).
- **Splitting keeps the outputs on the *last* fragment**, not the first: the original cell object
  becomes the final piece "to preserve kernel connection", and every new cell is created with
  `outputs: undefined`. It splits at every selection, not only at the caret.
- **The undoable unit is one `sharedModel.transact { }` block**, over a Yjs `undoManager`
  (`actions.tsx:1704`). That is why `deleteCells` inserts its replacement cell inside the
  transaction rather than after it.

Mime-bundle preference order is JupyterLab's `defaultRank`, not taste.

### There are two undos, and a cell operation never restores text

The reference declares four commands, not two: `notebook:undo-cell-action` / `redo-cell-action`
reach `NotebookActions.undo/redo` and unwind the *document's* stack, while `notebook:undo` /
`notebook:redo` reach `cell.editor?.undo()` and unwind the *editor's*
(`packages/notebook-extension/src/index.ts:280-286`, `:4476-4547`). P2a built the first pair; the
second is already ours, because Compose's `TextFieldState` carries its own history exactly as
CodeMirror carries the reference's.

**So an undo restores structure and keeps the source of every cell that is still there.** The
record supplies a source only for the cells it is bringing back. Without that rule the sequence
"delete a cell, type forty lines into another one, undo" loses the forty lines — a whole-document
record cannot tell a cell it is *restoring* from one it would be *reverting*. Measured on both
devices with `TYPED` standing in for the forty lines (`evidence/p2a-undo-cell-operation/`).

**The undoable unit is one cell operation**, which is `CellEdits.applying()` — the same boundary as
the reference's `sharedModel.transact { }`, and why `deleteCells` creates its replacement cell
*inside* the transaction (`actions.tsx:3391`). Which operations count needs no list: an entry is
recorded when the document actually changed, which is Yjs's own rule and covers `Copy` (clipboard
only) and every stale-id no-op for free. The stack starts empty per opened document, as
`Context.initialize()`'s `clearUndoHistory()` does (`packages/docregistry/src/context.ts:257-263`).

**One adaptation, and it is Clear Outputs.** There, `clearExecution` deletes the `execution`
metadata — tracked, so an entry exists — while the outputs themselves are not tracked at all
(*"output changes are not tracked by the undo manager"*, `actions.tsx`, `Private.restoreExecution`),
so `Z` restores nothing a user can see. That is Yjs scoping rather than a decision. Here the entry
restores the outputs and the count with it, because on a phone "Clear outputs" is one row above
"Delete" in a thirteen-row sheet, and a control that lights up and visibly does nothing teaches the
user that the control lies.

**Undo is a document action, so it lives on the app bar** — not on `NotebookBar`, which is what you
ask the *kernel*. The reference files it under Edit and the notebook's own context menu, never the
notebook toolbar, and binds `Z` in command mode only
(`notebook-extension/schema/tracker.json:31-35`, `:341-349`, `:572-648`). The icons are read out of
`ui-components/style/icons/…/undo.svg`, whose path is Material's `AutoMirrored.Undo` verbatim.

### A selection is a contiguous run, and it is what every cell command means

Read out of `packages/notebook/src/widget.ts` rather than designed:
`extendContiguousSelectionTo` is *"move the head of an existing contiguous selection"* (`:2216`),
`getContiguousSelection()` **throws** on anything else (`:2325-2348`), and shift-click and
shift-drag — the only two multi-select gestures there — both go through it (`:3181`, `:3290`).
There is no ctrl-click and no discontiguous selection to model. So the whole feature is two ends:
an anchor and a head, with the cells between them derived rather than stored.

**And the operand of almost every cell command is that run, not the active cell.** Merge (`:367`),
move (`:594`), cut/copy (`:3109`), paste (`:1611`), duplicate (`:1542`), clear outputs (`:1832`),
change type (`:3232`), delete (`:3342`) and every collapse (`:1877`–`:2109`) iterate
`isSelectedOrActive` in `actions.tsx`. `_updateSelectedCells` includes the active cell in that
(`widget.ts:3736-3739`), which is what makes today's single-cell behaviour the **degenerate case**
of the run rather than a second path beside it.

Six rules follow, each a constraint:

- **Extending back onto the anchor ends the selection outright** rather than leaving that one cell
  marked — *"Collapse the selection if it is only the active cell"* → `deselectAll()`
  (`widget.ts:2245-2249`). Material says the same about clearing the last item, so the reference
  and the platform agree and nothing had to be adapted.
- **Entering edit mode drops the run** — *"Edit mode deselects all cells"* (`setMode`,
  `widget.ts:1925-1929`). One line there, and it is what lets a tap into a cell keep meaning "type
  in this one" while a run is marked.
- **Three operations keep the run and every other one clears it.** `move` (`:586-610`),
  `clearOutputs` (`:1822-1840`) and the collapses (`:1877-2110`) are the only cell operations with
  no `deselectAll()` in them, because after each the same cells are still there and still worth
  acting on together. Undo is on the clearing side (`:1758`, `:1779`).
- **Merge ignores its direction once more than one cell is marked**, because `mergeAbove` is
  consulted only inside `if (toMerge.length === 1)` (`:390-425`).
- **Duplicate lands after the *last* cell of the run, paste below the head.**
  `notebook:duplicate-below` passes `'belowSelected'` (`notebook-extension/src/index.ts:3675`) and
  `pasteBelow` passes `'below'` (`:3594`). The difference is what keeps copies out of the middle of
  a run whose head is at its top.
- **The collapses set rather than toggle.** `hideCode` and `showCode` are separate commands over
  the selection (`:1877-1910`), so a run comes out uniform; toggling each cell against its own
  state would invert a mixed run and leave it exactly as mixed.

**One departure, and it keeps the reference's own invariant against a call site that breaks it.**
`selectAll` marks every widget and leaves `activeCellIndex` alone (`actions.tsx:1376-1386`); with
the active cell in the middle, the next `extendSelectionAbove` reaches `getContiguousSelection()`,
which throws *"Active cell not at endpoint of selection"* (`widget.ts:2344-2348`) with nothing
catching it. The same file states the rule that call site breaks — *"The head of a contiguous
selection is always the active cell"* — so here the head goes on the last cell.

**The touch adaptations, and each names what it departs from.** A long press is `BasicTextField`'s
text selection and is not available, so the way in is a row in the cell menu; the way out is a
contextual bar replacing the notebook's own, which is Material's rule and also the reference's
split under another name — its selection commands live in the Edit menu and the notebook's context
menu, never on the cell (`notebook-extension/src/index.ts:5267-5268`). The cell's `⋮` is hidden
while that bar is up, so there is never a question of what a menu applies to. Two things were
measured rather than chosen: **four reach arrows do not fit** — at 360dp a navigation icon plus
four actions is 240dp of 360 and the count wrapped to three lines reading `3 / select / ed`, so all
four `extend-marked-cells-*` are overflow rows — and **the tint is the reference's own**:
`.jp-Cell.jp-mod-selected` is a cell *background* (`notebook/style/base.css:141-143`), `#e3f2fd`
light and `rgba(33,150,243,.24)` dark, applied only once the run holds more than one cell, since
`jp-mod-multiSelected` is added at `count > 1` (`widget.ts:2763-2765`).

**Labels pluralise where the reference's `trans._n` does and nowhere else** — `Delete` /
`Delete cells`, `Cut` / `Cut cells` (`index.ts:3480`, `:3733`) — while `Clear outputs` and
`Collapse code` keep one form, as `'Clear Cell Output'` and `'Collapse Selected Code'` do (`:3365`,
`:4741`). With a run marked the two directional merge rows become one, because both would do the
same thing; the replacement is the reference's own third command, `notebook:merge-cells`, labelled
*"Merge Selected Cells"* (`:3953`).

### A run has a range, and the range is not the only thing that separates the run commands

Read out of `packages/notebook/src/actions.tsx` and `notebook-extension/src/index.ts`, and four of
these contradict the obvious answer.

- **`run-all-above` excludes the head and `run-all-below` includes it.** `slice(0, activeCellIndex)`
  against `slice(activeCellIndex)` (`:1005-1090`). Nothing about the two names says so, and one cell
  is the whole difference between them.
- **Every `run*` command deselects, and every one but `above` moves the head to the last cell it
  ran** (`:966`, `:1032`, `:1077`, `:3031`). Above is exempt in the reference's own words —
  *"The currently active cell will remain selected"*.
- **The compound restarts do neither**, because they are built on the *public*
  `NotebookActions.runCells` rather than on a `run*` action (`index.ts:3283`, `:3315`). So the same
  cells run with a different effect on the caret depending on which command asked, and that belongs
  to the command rather than to the range — which is why `run(range)` applies it here and
  `runCells(cells)` does not.
- **`run-all-below` is refused on the last cell of a multi-cell notebook** even though its range is
  non-empty, and **allowed on the only cell of a one-cell one** (`index.ts:3113-3119`). With nothing
  below it, *"Run Selected Cell and All Below"* is the cell's own Run under a longer name.
- **A restart's second half runs after the restart, and runs even when there was no kernel.** The
  first is the difference between a result and a lie — cells executed first would answer from the
  interpreter about to be destroyed. The second is `shutdown(then:)`'s rule: with nothing to replace
  there is nothing to wait for.
- **Running a markdown cell renders it** and unfolds its source (`cellexecutor.ts`,
  `case 'markdown'`), which is why rendered-ness had to leave the composable: a range run has to be
  able to close an open editor, and `rememberSaveable` state could not be reached from a run or seen
  by a test.
- **Clearing outputs throws away three things and unfolds a fourth.** `clearExecution` empties the
  outputs, nulls the execution count, sets `executionState = 'idle'` and deletes the `execution`
  metadata (`packages/cells/src/model.ts:706-714`), and the transaction around it sets
  `outputHidden = false` (`actions.tsx:1835`, `:1864`). The last two are one thing here — a
  `CellExecution` holds the stripe and the duration — so a cleared cell keeps neither, and
  `NotebookRevision` carries `executions` so an undo puts them back.

**A notebook has an active cell before anything is touched, and it is the first.**
`activeCellIndex` starts at `-1` and its setter clamps to `Math.max(newValue, 0)` once there are
widgets (`widget.ts:1965-1974`), so a load leaves it at 0. Ours left it null until a field took
focus, and the head-relative commands are what made that visible: both greyed on a freshly opened
notebook with nothing to say why.

**The touch adaptations, and each names what it departs from.** The reference has a Run menu and a
Kernel menu; a phone's bottom bar has room for one overflow, so the two become one list separated
the way its own schema separates them (`"type": "separator"`). The marked run's Run is on the
contextual bar, and the cell's own Run still means that one cell — two Run affordances with
different operands, kept apart by never being on screen together: **the kernel bar goes while a run
is marked**, which is P2c's "one surface acts on the run" followed through, and which
`evidence/p2d-3-run-the-marked-run/` shows was necessary rather than tidy. Aiming a head-relative
command still costs a trip through the keyboard, because focus is what selects a cell here; that is
an open item in `docs/PLAN.md` rather than a decision, since the obvious repair is the gesture U5a
deliberately removed.

### Splitting is the one cell operation that reads the editor, and the one whose undo restores text

`splitCell` (`actions.tsx:187-320`) contradicts the obvious answer twice, and both are load-bearing
here: **the original cell becomes the *last* fragment** — *"The last piece will remain in the
original cell to preserve kernel connection"* — and **every new cell is created with
`outputs: undefined`**, so the results and the execution count stay on that last fragment. Neither
costs anything in this app: the original keeps its id, and `outputs`/`executionCounts`/`executions`
are all keyed by id. Each fragment is stripped of its own leading and trailing **newlines only**
(`/^\n+/`, `/\n+$/`) so a fragment cut from inside a block keeps its indentation, and a cell
mid-run does not hand its `execution` metadata to fragments that never ran. Measured on both
devices, and on disk: `code-1` — the fixture's id — comes back on the *second* cell, with the
output and `execution_count: 7` (`evidence/p2b-split-cell/`).

**One adaptation, and it is a narrowing.** The reference splits at *every* selection; a Compose
`TextFieldState` carries one, so this is two fragments from a caret and three from a range, never
more.

Three consequences, each a constraint:

- **A cell operation is not always a pure function of the document.** Split needs the caret, which
  is not in the document at all, and the field's text, which may be *ahead* of it by up to one
  150 ms debounce — the tap that opens the cell menu can beat the report. So `CellEditorText`
  travels beside the action and `CellEdits.applying` brings the cell up to it before recording
  anything. The reference reads its editor for the same reason (`editor.getSelections()` beside
  `sharedModel.getSource()`). Getting this wrong is not theoretical: the undo record held the
  stale text, and a test caught it.
- **P2a's "a cell operation never restores text" gains exactly one exception, and it is not a
  waiver.** The record now names the cells whose source *the operation itself* rewrote
  (`NotebookRevision.rewrote`, computed from the before/after diff) and restores only those; every
  other surviving cell keeps the source it has now. Without it, undoing a split would drop the new
  fragment and leave the original holding only the half below the caret — the text above it
  destroyed, with nothing left to undo. The diff is Yjs's own shape: a transaction records the
  delta, not the document. P2i's find-and-replace inherits it.
- **A field on screen has to be *told* its text was rewritten, never left to notice.** The same
  diff bumps `EditorUiState.sourceRevisions`, and `trackCellSource` adopts on a bump. A comparison
  cannot work: the model is routinely one debounce behind what was just typed, so a field that is
  merely ahead looks exactly like one that is behind, and adopting on the difference eats
  keystrokes. Both directions share one `LaunchedEffect` so a report held in the debounce cannot
  land *after* a rewrite and undo it.

**Split is offered on every cell and whether or not a caret was ever put in one**, which is the
reference's `isEnabled` (`!!activeCell?.editor`) read literally. A cell whose caret was never
placed splits at 0 and gets an empty cell above, and an undo takes it back. **A markdown cell is
included, rendered or not**: `renderInput` and `showEditor` swap only which widget is *displayed*
(`packages/cells/src/widget.ts:2489-2503`), and `inputArea.editor` exists either way — so the
first guess, that a rendered markdown cell has no editor to read a caret from, is wrong about the
reference as well as unnecessary here.

**So a cell owns one text state, not one per field.** A cell has one source however it is being
shown, and both fields are handed the same `TextFieldState` — which is what makes a markdown cell
splittable at all, and what keeps its caret across being rendered and opened again.

**A control's name goes on the button, not on the icon inside it.** `enabled` lands on the
`IconButton`'s accessibility node and a `contentDescription` on the `Icon` lands on a *child* of
it, so naming the icon leaves the label and the state on two different nodes — measured, not
reasoned: a flow asking for the disabled control found a node reporting `enabled=true` with a
disabled parent one level up.

### A raw cell is an editor and nothing else, and running one is not a thing that happens

The third `cell_type` nbformat 4 defines, and until P2e a notebook holding one **could not be
opened at all**: `CellFile.toDomain` threw and the editor showed the exception as the whole screen
(`evidence/p2e-1-raw-cells/before/`). JupyterLab opens the same file without comment.

**It has no second state.** `RawCell extends Cell<IRawCellModel>` with no renderer, no output area
and no rendered/source toggle (`packages/cells/src/widget.ts:2671-2694`), and `RawCellModel` is the
one cell model that sets **no** mimeType where `MarkdownCellModel` sets `text/x-ipythongfm`
(`packages/cells/src/model.ts:550-578`). So `RawCellField` is plain text: no highlighting, no
bracket matching, no completion, no typing rules — and no placeholder, because
`jp-mod-emptyMarkdownPlaceholder` is markdown's alone (`packages/cells/style/widget.css:145`). It
keeps the line-number gutter for `MarkdownCellField`'s reason rather than the reference's
`raw: { lineNumbers: false }` (`packages/notebook/src/widget.ts:1349-1353`): the gutter counts
lines, not code, and that departure was already made for prose.

**Running one does nothing.** `runCell`'s switch answers `markdown` and `code` and falls to
`default: break` (`packages/notebook/src/cellexecutor.ts:36-141`). A range run therefore passes
over it with no change of any kind — not queued, not skipped, not failed — which `runCells` already
did by construction, filtering `CODE` for the queue and `MARKDOWN` for rendering.

**So a raw cell's header carries no Run control**, and that is an adaptation with no reference line
to depart from: the reference has no per-cell Run at all — its cell toolbar is duplicate / move /
insert / delete (`packages/cell-toolbar-extension/schema/plugin.json`) — while ours is a mobile
adaptation. A Run button that lights up and does nothing is what `cellActionItems` already refuses
to ship.

**`outputs` and `execution_count` on a raw cell are forbidden, not merely absent.** The shipped
`nbformat/v4/nbformat.v4.schema.json` makes `definitions.raw_cell` `additionalProperties: false`
over `id`, `cell_type`, `metadata`, `source` and `attachments`, so writing either produces a
notebook `nbformat.validate` rejects. The writer's existing `if (kind == CODE)` guard was already
right; the schema is why it must stay.

**And the change-type row had to become two.** "The type this cell is not" is a complete answer only
while there are two types; with three, one row names one of the other two and the third is
unreachable from the menu. The reference has a command per type —
`notebook:change-cell-to-{code,markdown,raw}` (`packages/notebook-extension/src/index.ts:212-214`)
— so each cell offers both. A code cell's menu is eighteen rows as a result.

### A heading owns a section, and what a collapsed one hides is derived rather than stored

The third collapse, and the only one that is not about the cell it is asked of. `setHeadingCollapse`
walks **forward** from a heading and stops at the first heading of equal or higher level
(`packages/notebook/src/actions.tsx:2374-2405`), so a heading owns the cells between it and its next
sibling — an H1's section swallows the H2 under it, and a code or raw cell can never end one because
`getHeadingInfo` answers `headingLevel: 7` for anything that is not markdown (`:2481-2493`).

**The level is parsed, never pattern-matched.** `MarkdownCell.headingInfo` takes the
**lowest-numbered** heading anywhere in the cell — `headings.reduce((prev, curr) => prev.level <=
curr.level ? prev : curr)` (`packages/cells/src/widget.ts:2213-2226`) — over headings obtained by
rendering the source and reading back `<h1>`–`<h6>` (`packages/toc/src/utils/markdown.ts:78-114`).
So a setext underline is a heading, a `#` inside a fence is not, and a cell opening on `###` and
going on to `#` heads an H1 section. **Top level only**: `getHeadingTokens` inspects the type of each
token `_marked.lexer` returns and never descends (`packages/markedparser-extension/src/index.ts:268-298`),
so a heading inside a blockquote or a list item is not the cell's heading. Ours is
`MarkdownDocument.headingLevel`, over the same commonmark AST the cell already renders from — which
carries one narrowing this app already had: an HTML `<h1>` block is dropped rather than counted,
because `MarkdownDocument` has never modelled HTML blocks at all.

**Only the heading's own flag is persisted, and hidden-ness is a function of it.** `headingCollapsed`
writes `jp-MarkdownHeadingCollapsed` at the *top* of the cell's metadata — a third key, not inside
`jupyter` — and **deletes** it rather than writing false (`widget.ts:156`, `:2263-2276`). No cell ever
records that it is hidden, and the reference says why in its own words:
`_refreshCollapsedHeadingVisibility` is *"Recompute hidden cells from the current collapsed heading
structure"* (`packages/notebook/src/widget.ts:916-940`), called after `moveCells` reorders (`:462`).
Ours derives it instead of re-running it — the union of every collapsed heading's section — so every
insert, delete, move and undo is correct without knowing the feature exists.

The union is also what gives the reference's `localCollapsed` branch for free (`actions.tsx:2400-2420`):
expanding an H1 over a still-collapsed H2 shows the H2 and leaves the H2's own children hidden,
because that H2 is still in the union on its own account. Measured on screen, not reasoned about —
`evidence/p2f-1-heading-collapse/04-sub-heading-stayed-collapsed.png`.

**The operand is the head, not the marked run**, which is the reference's own narrowing:
`toggleCurrentHeadingCollapse` reads `notebook.activeCell` and never the selection (`:2440-2455`).
It keeps the run for the same reason the other collapses do — there is no `deselectAll()` in it —
with one repair the reference never needs: a heading *inside* a marked run can swallow the head, and
a head nothing draws is one the ranged runs would still compute their range from, so both ends move
onto the heading. `expandParent` states the invariant that keeps (`:2288-2306`) and repairs it by
expanding, which is the wrong half here: expanding is what the user did not ask for.

**Two adaptations, and each names what it departs from.** The reference surfaces none of the eleven
heading commands in a menu — command palette and keyboard only
(`packages/notebook-extension/src/index.ts:5279-5340`, `schema/tracker.json:558-570`) — so every
surface here is ours. The collapser is a chevron on the cell header where the reference appends a
button to the *prompt node*, created only for `level > 0` (`widget.ts:2387-2408`), and it is
`ChevronRight`/`ExpandMore` rather than `ExpandLess`/`ExpandMore` because a section is a tree node
and the cell itself is exactly what stays on screen. It is **not** in the cell menu: the button is
visible, named and in the accessibility tree already, and an eighteen-row sheet does not need a
nineteenth row for a control that is one tap away.

**A collapsed heading is not a collapsed cell**, so the placeholder is different too. `CollapsedCell`
shows a line of what is folded so the cell stays identifiable; a section has no one line, and the
reference names it by size instead — `trans._n('%1 cell hidden', '%1 cells hidden', numberChildNodes)`
over an `addIcon`, drawn only while the heading is collapsed **and** owns something
(`widget.ts:2412-2455`).

**Collapse All and Expand All are not each other's mirror, and the difference is one line.**
`collapseAllHeadings` ends on `notebook.activeCellIndex = 0` and `expandAllHeadings` has no such
line at all (`actions.tsx:2221-2246`) — because only one of them can hide the cell the head is on,
and the first cell is the one cell no collapse can reach, with nothing above it to own it. Both walk
`notebook.widgets` and set only the headings; the cells between go by *being in a section*, which is
why a markdown cell above every heading survives a Collapse All
(`evidence/p2f-2-collapse-all-headings/03-all-collapsed.png`).

They live in a **View overflow on the notebook's own bar**, which is this app's third menu surface
and the reason it exists: `NotebookBar` states what it is — what you ask the *kernel* — and folding a
section away asks it nothing. The reference files both under View for that reason, though only in its
command palette and as `Ctrl Shift Arrow` (`index.ts:5279-5281`, `schema/tracker.json:562-570`).
P2g's four view toggles land there rather than needing a surface of their own. Neither row is ever
disabled, which is the reference's choice too: unlike the undo pair beside them, neither command
declares an `isEnabled` (`index.ts:5094-5121`).

Each is **one entry on the undo stack**, so a Collapse All comes back whole rather than a heading at
a time — the same `CellEdits.applying()` boundary every cell operation uses, reached through
`NotebookCellEdits` rather than through a `CellAction`, because like `clearAllOutputs` neither has a
cell to be asked from.

### A heading level is a change of type carrying a rewrite, and one row stands for six

`notebook:change-cell-to-heading-1..6` are not six commands with six behaviours: `setMarkdownHeader`
is `changeCellType(notebook, 'markdown', { headingLevel })` (`actions.tsx:2197-2214`), so every rule
of a change of type applies — the id survives, the outputs and the execution count do not, and the
cell is left showing its source in command mode. On top of that, one rewrite:
`Private.setMarkdownHeader` replaces `^#+\s*` — or, failing that, `^\s*` — with the marker
(`:3429-3438`). One anchored regex with two alternatives, so it strips a header the cell already had
*or* the leading whitespace it did not, and touches nothing past the first line. **A cell that is
already markdown takes the rewrite and no type change** (`:3300-3308`), which is how a heading is
re-levelled. The level is clamped to six (`:2208`) because a seventh `#` renders as prose.

**One menu row and a picker, and the two visual surfaces differ from the accessibility one on
purpose.** The reference offers all six only in its command palette
(`notebook-extension/src/index.ts:5334-5339`), so any row at all is an adaptation. The cell menu is
eighteen rows already — long enough that a shared Maestro subflow exists purely to scroll it — so
*Change to heading…* opens a second `CellActionSurface` holding H1–H6, which inherits the
sheet-versus-dropdown rule, the row height and the `Cell menu …` naming rather than restating them.
The picker **replaces** the list rather than nesting in it, so there is never a question of what a
tap applies to — the same rule that hides a cell's `⋮` while a run is marked. The accessibility
context menu expands the row into its six instead: it has no width to run out of and no fold, and a
submenu is the worst shape there is to operate with TalkBack.

**The picker taking over is not a dismissal**, and forgetting that cost a red flow.
`CellActionSurface` dismisses after *every* choice, so the row that opens the second step opened it
and closed it in the same frame — the whole menu simply went away
(`evidence/p2f-3-change-to-heading/`).

### An inserted heading takes the section's level, and "below" means the end of the section

`insertSameLevelHeading{Above,Below}` (`actions.tsx:1183-1231`), and the second half is the one a
first guess gets wrong.

- **The level is the section's, not the cell's.** `determineHeadingLevel` answers the head's own
  level if it is a heading and its *parent heading's* if it is not (`:3576-3597`), falling back to 1
  when there is no heading at or above it — which is every cell of a notebook that has none. So a
  heading asked for from a code cell inside `## Part A` is an H2.
- **`below` inserts at `findLowerEqualLevelHeadingBelow`** (`:3520-3550`) — the start of the next
  *sibling* section — so the new heading **closes** the section it was asked from rather than cutting
  it in half. Measured on disk: asked from the cell under `## Part A`, `## Part B` lands after that
  cell and before `# Chapter two` (`evidence/p2f-4-insert-heading/`).
- **It ends in edit mode**, `notebook.mode = 'edit'` (`:3645`), which here is the caret being asked
  for. That is the opposite of every other insert and of `changeCellType`, and the reason is the
  cell: an inserted heading holds nothing but its marker, so there is nothing to read and everything
  to type.

**One adaptation, and it is the label.** The reference's are *"Insert Heading Above/Below"*
(`notebook-extension/src/index.ts:4120,4137`), but this menu already has *"Insert code below"* one
row up, which puts a cell **after this one** — two rows both reading "below" would promise the same
place and mean different ones. So the row names the section: *Insert heading after section*.

### A collapsed heading is its whole section, for four commands and no others

`Private.selectedCells` adds a collapsed heading's children to what is copied, cut or duplicated
(`actions.tsx:3103-3130`) and `deleteCells` marks them for deletion (`:3340-3365`). **Nothing else
does** — `changeCellType`, `mergeCells`, `moveCells`, `clearOutputs` and every collapse iterate
`isSelectedOrActive` and stop there — and the asymmetry is deliberate rather than an oversight:
those four *remove or reproduce* the heading, so leaving its section behind would orphan cells under
a heading that has gone, while the rest leave the structure standing.

**Paste is the fifth site and it is a position, not an operand.** `addCells`'s `'below'` and
`'belowSelected'` branches add `numberChildNodes` to the index (`:1590-1612`), so a cell pasted
under a collapsed heading lands *after* its children rather than somewhere nobody can see.

**The sixth is inert here, and that is the model rather than an omission.** `Notebook.deselect`
clears the selected flag on a collapsed heading's children (`widget.ts:2140-2160`); ours has no
per-cell flag to clear — a selection is an anchor and a head with the cells between them derived
(`docs/DECISIONS.md`, "A selection is a contiguous run") — so there is nothing that could be left
marked.

`numberChildNodes` is not stored either. The reference recomputes it inside `setHeadingCollapse`
(`:2427-2429`); here it is the section's size, which is where that number came from.

### The three folds are a category, not three commands that happen to rhyme

`CellAction.Fold` exists because detekt asked for `transformed` to be split three times and was
pointing at the same seam each time. `hideCode`/`showCode`, the output collapses and
`toggleCurrentHeadingCollapse` share both of the things the dispatch cares about: none has a
`deselectAll()` in it (`actions.tsx:1877-2110`, `:2440-2455`), so all three keep the marked run, and
each reads the **head's** own state and sets from it so a mixed run comes out uniform rather than
inverted. It is also the category `CellFolds` already names in prose — *"a fold discards nothing"* —
given a type so the two exhaustive `when`s over `CellAction` can say it once instead of three times.

### Adopting a rewritten source belongs to the cell; reporting an edit belongs to the field

Both halves used to live in the field, and P2f-3 is what proved that wrong.
`change-cell-to-heading` is the first command that rewrites a cell's source **and** changes its
type, so a `CodeCellField` became a `MarkdownCellField` over the same `TextFieldState` in the frame
the rewrite arrived. The adoption counter is seeded with the revision it arrives on — correct while
a new field always means a new state — so the replacement started already up to date and adopted
nothing. The model held `## title`, the collapser said so, and the editor showed the text from
before. **Both of the flow's earlier assertions were green; the screenshot is what said otherwise**,
for the second time in this project.

So `adoptRewrittenSource` is called where the `TextFieldState` is created and `reportCellEdits`
stays in the field. That is the rule this file already carries — *"a cell owns one text state, not
one per field"* — followed one layer down: the field over that state is composed, disposed and
**replaced**, while the state and the notebook's disagreement with it outlive all three. Everything
else about the mechanism is unchanged and still load-bearing: **a counter and never a comparison**,
because the model is routinely one debounce behind whatever was just typed.

### Unrendered is not the same as asked for, and only a tap asks

`changeCellType` ends with *"Fetch the new widget and unrender it"* —
`(child as MarkdownCell).rendered = false` — and that `if` sits **outside** the
`child.model.type !== value` branch (`packages/notebook/src/actions.tsx:3308-3312`), so it reaches
every cell the command visited and not only the ones whose type changed. A mixed run changed to
markdown opens the ones that already were. Ours rendered them at once, so converting a code cell
hid the very text that had just been converted.

**Fixing that exposed one of ours.** `MarkdownCellField` requested focus for having been composed at
all — a complete answer while a tap on the rendered prose was the only way to compose one. With a
second way in, a run changed to markdown composed several editors in one frame, each asking for the
caret and one winning it: the soft keyboard up over a notebook nobody was typing in
(`evidence/p2e-3-change-to-markdown/before/`). Both of the flow's assertions were green; the
screenshot is what said otherwise.

So the two facts are separate state. `markdownBeingEdited` is whether a cell is unrendered;
`markdownTakingCaret` is whether its editor was **asked for**, set by `withMarkdownEditorOpened` —
the tap — and by nothing else, and withdrawn when the cell renders again. One id and not a set,
because only one field can hold the caret. That is the reference's mode: `changeCellType` ends on
`notebook.deselectAll()` with no mode change, so the notebook stays in **command** mode.

The flow asserts the symbol row's first key is *not* on screen afterwards, which is how "still in
command mode" becomes a claim rather than something a screenshot happens to reveal.

### P2g — view toggles, and two defaults kept against the reference on purpose

Three commands, four sub-steps: `notebook:toggle-all-cell-line-numbers`,
`notebook:enable/disable/toggle-output-scrolling`, and
`fileeditor:toggle-autoclosing-brackets-universal`. Each settles a default the reference makes one
way and this app makes the other, and both departures are deliberate rather than inherited.

**The line-number gutter defaults to shown, against `lineNumbers: false`**
(`packages/notebook/src/widget.ts:1336-1355`, `notebook-extension/schema/tracker.json:704,752,767`).
This app has drawn the gutter on every cell since Step 5; a phone shows a fraction of a cell at
once and a traceback names a line, so the departure keeps every screenshot since Step 5 - and
`step5-line-numbers` itself - meaning what it always meant. The command turns it *off*, which is
the reference's own polarity, just starting from the other side of it. One boolean for all three
cell types, read off `toggleAllLineNumbers`'s own conjunction (`actions.tsx:1792-1812`), never
persisted - the command writes `notebook.editorConfig`, which belongs to the widget.

**`metadata.scrolled` reads `'auto'` as false**, because JupyterLab does not implement it either -
`loadScrolledState`'s own comment is *"We don't have the notion of 'auto' scrolled, so we make it
false"* (`packages/cells/src/widget.ts:1532-1537`). Written only when true and deleted when false,
the same rule `CellCollapse.kt` already carries for `collapsed`, `jupyter.source_hidden` and
`jp-MarkdownHeadingCollapsed` - a fourth key at the same shape rather than a special case.

**`notebook:toggle-output-scrolling`'s operand is all-of over the marked run, not head-of.**
Every other `Fold` — collapsing a cell's source, its outputs, a heading's section — reads the
*head* cell's own state and sets the whole run to its opposite. This one is different because the
reference is: `isOutputScrollingEnabled` is true only when **every** selected code cell is already
scrolled (`notebook-extension/src/index.ts:2780-2796`), so a mixed run of two turns *on* rather
than inverting per cell. `EditorUiState.allMarkedCodeCellsScrolled` carries that predicate apart
from the toggle it feeds, and it is the one fold whose write also filters to code cells at the
transform itself (`withOutputsScrolledSet`) rather than trusting the operand it is handed - the
reference's own write loop repeats the `cell.model.type === 'code'` guard inside
`enableOutputScrolling`/`disableOutputScrolling` rather than relying on the read that decided to
call them (`actions.tsx:2081-2114`).

**Its menu row carries one fixed label, "Enable output scrolling", whatever the state - literal
parity, not this app's usual flip-with-state wording.** The reference's own context-menu
registration for this exact command pins `args: {isMenu: true}`, which forces the label to
*"Enable Scrolling for Outputs"* regardless of `isToggled`
(`notebook-extension/src/index.ts:4963-4967`, `schema/tracker.json:328-335`) - so a tap on it can
disable an already-scrolled run even though it still reads "Enable". That is the reference's own
choice for *this* surface, and it is why the auto-closing-brackets row two paragraphs down does
not copy it: there is no reference row on the surface that row occupies to have parity with.

**`resize: vertical` on a scrolled output is not built.** A drag handle is not a touch idiom, and
there is no pointer to hover it into view with first. Recorded rather than silently dropped.

**Auto-closing brackets defaults to on, against `default: false`**
(`packages/codemirror/src/extension.ts:636-644`) - on a soft keyboard, `(` and `)` are two trips
through the symbol row rather than one. `PythonTypingRules` takes it as a constructor flag gating
`closePair` and `stepOverPartner` only; `continueIndent` is a different CodeMirror setting there
and must not move with it - a cell that closes nothing must still carry a block's indent onto the
next line.

**One app-wide setting is the universal command, not a simplification of it.** The reference's
`notebook:toggle-autoclosing-brackets` and `fileeditor:toggle-autoclosing-brackets-universal` are
two commands because CodeMirror configures a notebook cell, a console and a file editor
separately there; this app has always run one `PythonTypingRules` under both a code cell and the
plain file editor, so one `EditorSettingsRepository` boolean *is* what "universal" means here -
gating it once reaches every surface the reference flips together.

**Its row flips label with state, unlike output-scrolling's fixed one, because there is no
reference row on this surface to have parity with.** The reference files the universal command
under Settings, which this app does not have until P6c; until then it sits on `EditorTopBar`'s
View menu beside the other two toggles, in this app's own convention
(`ENABLE_AUTO_CLOSING_BRACKETS`/`DISABLE_AUTO_CLOSING_BRACKETS`, matching
`SHOW_LINE_NUMBERS`/`HIDE_LINE_NUMBERS`) rather than borrowing a fixed label from a menu this app
does not have yet.

**`notebook:toggle-virtual-scrollbar` (Show Minimap) is deferred past P2h, not scheduled here.**
It is a cell-preview strip with a viewport indicator and click-to-jump
(`packages/notebook/src/widget.ts:1546-1710`) that is not a touch target at 360dp, and P2h's table
of contents both answers "jump around a long notebook" on a phone and already pays for the side
panel a minimap would want - `TableOfContentsPane`, which is a column in the editor's own scene
rather than the `extraPane()` this once assumed (see below). Re-costed once the ToC exists rather
than built twice.

### The table of contents is a panel where there is room for one, and a menu where there is not

Two presentations of one model, which is `CellActionPresentation`'s own split applied to a second
picker rather than a new rule: `TableOfContentsPane` beside the notebook, `TableOfContentsPanel`
over it. Where the reference has one answer - a sidebar tab, `shell.add(toc, 'left', { rank: 400 })`
(`packages/toc-extension/src/index.ts:309`), with `toc:show-panel` doing nothing but activate it
(`:161-172`) - this has two, because a 32px icon strip beside a ≥250px panel is a desktop canvas
and JupyterLab has **never** adapted it narrow: an exhaustive grep of `packages/application/{src,style}`
and `packages/ui-components/src` finds no media query, no width branch, and a `format: 'mobile'`
property that is declared and never assigned. There is no narrow version of it to have parity with.

**The width that decides is the *editor's*, never the window's**, and that is the rule rather than
a detail of it. In the two-pane layout the explorer has already spent 360dp, so an 840dp *window*
leaves the editor about 456dp - and a column carved out of that would leave the notebook narrower
than the phone the single-pane layout was designed for. `tocPresentationFor` therefore takes the
width its caller measured with `BoxWithConstraints`, at the expanded lower bound (840dp): the
number this app already means by "wide enough to split in two" (`PaneDirective.kt`), applied one
level down to the pane doing the splitting. It is also the first width branch in this app that a
test can drive both sides of - `currentWindowAdaptiveInfo()` reads the activity's window, which
nothing under a composable can force, while a layout constraint is just a constraint.

**It is not `ListDetailSceneStrategy.extraPane()`, and that is a blocker rather than a preference.**
`docs/PLAN.md` assumed the third pane; the library says an extra pane is a separate `NavEntry`, and
every entry here owns its own `ViewModelStore` (`docs/architecture.md` §4, the decorator that fixed
defect D1). So a ToC entry could reach neither this notebook's `EditorViewModel` for the headings
nor its `LazyListState` to scroll - it would have forced per-document state up into a shared owner,
which is a refactor the feature does not need. A column inside the editor's own scene shares both
for free. The pane-count arithmetic was checked as well and is *not* what ruled it out: three
partitions do arrive, but only at 1200dp+ (`calculatePaneScaffoldDirective`, Compact/Medium 1,
Expanded 2, else 3), and at two partitions the extra pane displaces the **explorer** - so the file
browser would vanish to show a ToC.

**Hidden by default, which is the reference's own default**: the file browser is the sidebar panel
that opens with the shell, not this one. Shown from the View menu, closed from either that row or
the pane's own `×`, and the row flips its label the way `SHOW_LINE_NUMBERS` does - but only in pane
mode, since an overlay is dismissed by the pick and has no state left to name.

**A pick does not close the pane**, which is the whole of what a panel buys over a menu: jumping
twice costs two taps rather than four, and the structure stays on screen while the document
scrolls. The overlay keeps the opposite rule, `CellActionSurface`'s `choose`.

**A heading nested inside an ancestor's collapsed section is a no-op tap**, stated at the call site
rather than fixed: the ToC's own collapse state is independent of the notebook's in the reference
too (`syncCollapseState` defaults false, `packages/toc-extension/schema/registry.json:32-69`), so a
hidden cell simply has no index on screen to scroll to.

**One harness lesson, and it cost two red runs.** A ToC row and the heading it names are the *same
words*, so a flow that means "the notebook moved" cannot say so by naming a heading - the pane is
still showing that text. The second attempt was ambiguous for a different reason: the tablet's
window is tall enough to hold the cell under the first heading and the third heading at once, so
that cell never left the screen either. Both halves are settled by the notebook's **last** cell,
which is code and therefore the notebook's alone. This is the same rule this file already records
about a document's title being the wrong handle beside a file list showing it, one layer along.

**`NARROW WINDOW` joins `TABLET ONLY` and `WIDE WINDOW` as a driver rule**, and the ToC is the
first feature to need it: a flow asserting the compact-width adaptation is not failing on a tablet,
it is asking the wrong question there. The mirror of a rule the harness already had.

### The table of contents folds its own tree, and that tree is derived rather than kept

`toc:toggle-collapse`, plus the disclosure beside each entry that the command sets in bulk. The
tree it folds is built **at draw time from the flat list** - `buildTree` takes as a heading's
children the ones that follow it until an equal or lower level appears
(`packages/toc/src/toctree.tsx:57-102`), which is `headingSectionOf`'s rule one rank along. Nothing
records that a row is hidden, so adding, deleting or re-levelling a heading is correct here without
knowing the feature exists - the same reason P2f derives `hiddenCellIds` instead of storing it.

**It is not the notebook's fold, and that is the reference's own default.** `syncCollapseState` is
false (`packages/toc-extension/schema/registry.json:32-69`), so an entry folded here hides rows in
that list and leaves `jp-MarkdownHeadingCollapsed` alone - measured on screen rather than reasoned
about: `evidence/p2h-4-table-of-contents-folds/02-folded-in-the-list-only.png` has *Part A* gone
from the panel with its cell and `part = 2` still in the notebook beside it. Its lifetime is the
reference's too: rebuilt on every `getHeadings()` and never persisted
(`packages/notebook/src/toc.ts:274,293`), so `TocFolds` is remembered and not saved.

**Collapse All is narrowed to the entries that own something**, where the reference marks every
heading (`toggleCollapse({collapsed: true})`). A leaf's flag hides nothing either way, and keeping
it would leave `someExpanded` permanently true in a notebook with no nesting - so the one control
would fold for ever and never unfold. Same reasoning one step on: **the control is absent entirely
where nothing nests**, rather than drawn and dead. The reference draws it unconditionally and can
afford to; this app already refuses to ship a control that lights up and does nothing, which is why
a raw cell has no Run and a non-heading cell no collapser.

**One control for both halves, named for the half the next tap performs** - `someExpanded` is the
reference's own predicate for exactly that (`packages/toc-extension/src/index.ts:174-210`), and a
name that flips is what a Maestro flow can read where a toggled state is not. Its words are
*"Collapse all entries"* / *"Expand all entries"* rather than the reference's *"Collapse All"*,
because `COLLAPSE_ALL_HEADINGS` is one menu away and folds the **notebook's** sections: two
controls a tap apart must not answer to one string.

**The overlay has no disclosures at all**, and that is the adaptation rather than an omission. A
fold whose lifetime is the composition is worth nothing on a surface that is opened, picked from
and dismissed in one gesture; the pane is the only one that outlives a jump. So `TableOfContentsRows`
takes a null `onToggleCollapsed` there and draws the spacer a disclosure would have occupied, which
is what keeps both surfaces' rows lined up at the same rank.

### Heading numbers go in the document, not only in the panel

`toc:display-numbering`'s own label says so - *"Show heading number in the document"*
(`packages/toc-extension/src/index.ts:113`) - and the reference means it literally: `addPrefix`
writes the computed prefix into the **rendered heading's own node**
(`packages/notebook/src/toc.ts:733-748`), so the number appears on the notebook's markdown cell as
well as on the ToC row. A toggle that numbered the list alone would be a control whose label lies,
which is why P2h-5 was split - the counter shipped first with no surface at all, and the two
commands only once both renderings existed. Measured on one screen:
`evidence/p2h-5-heading-numbers/02-numbered-in-the-list-and-the-document.png`.

**The numbers are the document's, so they are computed over every heading at once.** `filterHeadings`
threads one `levels` array through every cell (`packages/toc/src/utils/common.ts:29-60`), which is
why `tocPrefixes` takes the whole list and why `EditorContent` computes the map once rather than per
cell. Two consequences: a folded-away entry **still counts**, and the prefix a markdown cell draws
is handed to it rather than derived from its own source.

**Three of `getPrefix`'s rules contradict a first guess** (`common.ts:157-196`): descending a level
opens a counter at 1 and **returning drops every deeper level** (`levels.splice(level)`); a skipped
level is counted as `0` rather than closed up, so an H1 followed by an H3 reads `1.0.1.`; and with
`numberingH1` off the top level leaves the string entirely (`levels.slice(1)`) while a heading with
nothing open above it gets no prefix at all.

**Two pieces of the reference are deliberately not carried, and a mutation test is what said so
rather than judgement.** Its loop zeroing the levels between the last heading and this one is
redundant here - `setLevel` pads with zeroes and `levels.size` is always the previous heading's
level, so those slots never exist to hold anything else - and
`level === highestLevel ? baseNumbering : 1` collapses to `1`, because `baseNumbering` is a schema
setting with no command whose default is 1. Deleting either killed no test, which is what sent us
looking; both are recorded in `TocNumbering.kt` so P6c can put the second one back with the setting.

**`maximalDepth` is not implemented, and that is a decision rather than an omission.** The
reference's default of 4 (`toc-extension/schema/registry.json:32-69`) means `filterHeadings` drops
H5 and H6 from the ToC entirely. Here an entry is 1:1 with a cell, so dropping one would make that
cell unreachable from the panel with nothing on screen to explain it - and with no settings surface
until P6c the omission could not be undone. Revisit when there is somewhere to change it.

**Both rows live on the View menu, where the reference puts them on the ToC panel's own toolbar**
(`toc-extension/src/index.ts:260-306`). That is the only surface the two presentations share: a
phone gets the overlay, which has no toolbar to hang a control from, and a 280dp pane header has no
room for a fourth. **The first-level row is offered only while something is numbered**, which is
that command's own `isEnabled` read through this app's rule - with numbering off, both of its states
look identical on screen.

### A heading a cell printed is an entry, and it points at the cell rather than at the output

`toc:display-outputs-numbering`. `CodeCell.getHeadings` walks a code cell's outputs and reads the
HTML or Markdown ones for `<h1>`-`<h6>` (`packages/cells/src/widget.ts:1312-1370`, over
`getHTMLHeadings`), and the schema has `includeOutput` **on** by default
(`packages/toc-extension/schema/registry.json:32-69`) - so this app's default is on too.

**It reads what the output renders as, not the mime bundle.** `CellOutput.render()` already picks
the richest type this app can draw, so taking the headings out of its `RenderedOutput.Document` is
what keeps an entry pointing at something actually on screen. That narrows the reference by one
type: `text/markdown` is not read, because nothing renders it here yet and an entry for prose the
output shows as plain text would be a promise it does not keep. It also comes free of the
reference's `!syncCollapseState` guard (`packages/notebook/src/toc.ts:260-263`), since this app has
no such setting to suppress it.

**An entry needs an identity that is not its cell**, and this is the first thing to need one: a
code cell can print several headings, so `TocHeading.key` is the cell's id for a heading written in
a source and `"<cell>#<output>.<n>"` for one found in an output - which is `INotebookHeading`'s own
`outputIndex` (`toc.ts:60-80`) under another name. Folds and Collapse All key on that; the jump
still keys on `cellId`.

**The jump lands on the cell, where the reference lands on the output element.** It scrolls to
`heading.cellRef.node` and uses `outputIndex` to reach the right one inside it
(`toc.ts:728-748`); this app scrolls a `LazyColumn` by item, and its item is the cell. On a phone
the cell and its output are the same screenful, so the adaptation costs nothing worth building a
second scroll mechanism for.

**The number for a printed heading appears in the panel and not in the output**, which is the one
place this departs from `addPrefix` and it is written down rather than left to be discovered. The
entry counts - so the headings after it number correctly - but writing the prefix into the output
would mean threading a per-heading number through the output renderer for a case that is rare, and
`numberHeaders`'s document half is already covered where headings are actually written. Revisit if
printed headings turn out to be common.

**The flow needs no kernel, against what `docs/PLAN.md` assumed.** nbformat stores a cell's
outputs, so the fixture carries the `text/html` this reads and the flow is `WIDE WINDOW` rather
than `TABLET ONLY` - repeatable, and green on the first run.

---

## Editor

### The lexer is generated, never hand-written

A rule, not a preference. The grammar is `antlr/grammars-v4` at
`42b1ea521705c126d84331186b221c9819db1058`, path `python/python3_14`
(`tools/grammars/python/README.md:3`), turned into a lexer by ANTLR 4.13.2
(`gradle/libs.versions.toml:19`). Both licences are permissive and neither is native — so no NDK,
no CMake, no 16 KB page-size obligation, and no crash surface that debugs like C.

`python3_14`, **not** `python3`: the older grammar discards comments
(`SKIP_: (SPACES | COMMENT | LINE_JOINING) -> skip`) so they can never be coloured. f-strings
tokenise structurally (`FSTRING_START` / `FSTRING_MIDDLE` / `LBRACE` / …), so `{name}` can be
coloured as an expression.

Two costs, measured: the **parser** is generated too (`PythonLexerBase.java` constructs a
`PythonParser`), and `INDENT`/`DEDENT` are synthetic, zero-width and carry inverted ranges — they
must be filtered or they produce negative-length spans.

### A Compose-native cell editor, not a WebView

Changed on measurement, 2026-08-05. Per-cell CodeMirror WebViews cost **+56.7 MB** for one 30-cell
notebook plus a **93.4 MB** renderer process; `BasicTextField` + `TextFieldState` with our own
highlighting costs **+10.5 MB** and no renderer — about **139 MB saved**, on the same tablet with
everything else held constant. Compose owns IME composing regions, autocorrect, gesture typing,
selection handles and the magnifier; we own only the language layer. `evidence/step5.2/tablet/`.

### The presentation reference is VS Code; the behaviour reference stays JupyterLab

`docs/PLAN.md` opens with *"Parity is not a matter of taste"* and names JupyterLab's command ids
as the specification. That still holds — for **behaviour**. What it decided by default rather than
by argument is **presentation**, and since P2t that is VS Code's:
`reference/research_repos/vscode` at `f3fa55c39d3df2923b46a3d76cf6baf0afa1db33`, beside
`vscode-jupyter` at `f01d80ca0aa0dfdfa1d2e10a08943e12b776d155`.

**What this reopens:** the palette, the cell's chrome, the notebook's top, and the shape of the
side panels. Those are re-derived from VS Code, and where an entry below was decided from
JupyterLab's CSS its *citation* is superseded even where its *outcome* survives.

**What it does not reopen, and nobody may re-litigate on presentation grounds:** the command
surface (the `notebook:*` ids and their semantics), `nbformat` and its round-trip, the kernel wire
protocol, and every P2a–P2k behaviour decision in this file. A control may move; what it does when
pressed is still JupyterLab's answer.

**vscode-jupyter is authoritative for less than its name suggests, and it was checked rather than
assumed.** The notebook UI is core VS Code; the extension contributes kernel selection, status and
its own commands. `src/` is `kernels`, `notebooks`, `standalone`, `webviews`, and

```sh
grep -rl 'run-button-container\|execution-count-label\|cellRunGutter' src/   # no output
```

So a question about cell chrome is answered in `vscode/src/vs/workbench/contrib/notebook/`, never
here.

### The default VS Code theme is 2026 Dark / 2026 Light, not Dark Modern

Worth stating because it is the trap this re-base nearly walked into: **Dark Modern is no longer
the default.** `ThemeSettingDefaults.COLOR_THEME_DARK` is `'Dark 2026'`
(`src/vs/workbench/services/themes/common/workbenchThemeService.ts:41-46`), and
`migrateThemeSettingsId` maps the former id `'VS Code Dark'` onto it (`:53-70`) — so "the VS Code
dark theme" **is** 2026 Dark.

It is not a standalone file. `2026-dark.json` includes `dark_modern.json`, which includes
`dark_plus.json`, which includes `dark_vs.json`, and the outermost overrides most of what it
inherits: 725 lines against Dark Modern's 133. Reading any one file in that chain gives the wrong
colour. **The chain must be resolved before a value is transcribed** — `include` walked outermost
last, `colors` merged by key and `tokenColors` concatenated so the last matching rule wins, which
is tmTheme precedence.

Measured after resolving, the difference is not cosmetic: `editor.background` is `#121314`, not
Dark Modern's `#1F1F1F`.

The chain-walking is `scripts/resolve_vscode_theme.py`, so this is checkable rather than asserted:

```sh
cd reference/research_repos/vscode/extensions/theme-defaults/themes
python3 ../../../../../../scripts/resolve_vscode_theme.py 2026-dark.json --color editor.background
```

### The palette swap must be measured against the **cell**, not the page

**This entry was written against the wrong surface, and P2u-2 corrected it by measuring.** It
originally claimed the 4.5:1 floor survived the swap intact; it does not, and the entry below
records what replaced it.
The original table measured every role against `editor.background` - `#FFFFFF` in light - which is
the page a *file* editor draws on. The app draws code on a **cell**, and VS Code's
`notebook.cellEditorBackground` resolves through its registered default to `sideBar.background`
(`src/vs/workbench/contrib/notebook/browser/notebookEditorWidget.ts:3339-3344`), which is `#FAFAFD`
light and `#191A1B` dark. The difference is small and it decided a value:

| Role | 2026 Dark on `#191A1B` | 2026 Light on `#FAFAFD` |
|---|---|---|
| plain | `#BBBEBF` 9.32:1 | `#202020` 15.64:1 |
| keyword | `#C586C0` 6.26:1 | `#AF00DB` 5.24:1 |
| string | `#a5d6ff` 11.34:1 | `#0a3069` 12.29:1 |
| number | `#b5cea8` 10.26:1 | `#098658` **4.42:1 — under** |
| comment | `#8b949e` 5.67:1 | `#6e7781` 4.36:1 (floor 3.0) |
| function | `#d2a8ff` 8.95:1 | `#8250df` 4.84:1 |
| heading | `#79c0ff` 8.96:1 | `#0550ae` 7.29:1 |
| link | `#a5d6ff` 11.34:1 | `#0a3069` 12.29:1 |

Light's `number` is the **only** role that fails, and it fails on the IntelliJ cell surface the
tree carried before P2u-3 as well (4.33:1 on `#F7F8FA`), so it is not an artefact of which
sub-step lands first. `resolve_vscode_theme_test.py` asserts that "only" rather than leaving it as
a claim in prose. The value is **kept** - see the entry below for why, and for what replaced the
floor as this palette's gate.

The table is this tool's own output, one command per theme:

```sh
python3 scripts/resolve_vscode_theme.py \
  reference/research_repos/vscode/extensions/theme-defaults/themes/2026-light.json \
  --against sideBar.background
```

### Parity with the 2026 themes outranks the contrast floor

Light's `constant.numeric` is `#098658`, and on the cell surface it measures **4.42:1** - under
WCAG AA 4.5:1. It is kept exactly as the reference draws it.

**The floor used to gate this and no longer does, and that is a decision, not a lapse.** With VS
Code as the presentation reference, what the syntax palette owes is parity: the app should draw a
notebook the way VS Code draws one, including where VS Code is imperfect. Correcting a value here
would mean the app's Python numbers are a colour that appears in no VS Code theme, which is a
worse answer to "which reference did this come from" than a recorded 4.42:1.

`EditorPaletteTest` therefore pins the eight syntax values **by equality** to the resolved themes
instead of by ratio. That is the stronger assertion for the other seven: a ratio permits any other
legible colour, equality permits nothing, which is what *a value may not be nudged "to match VS
Code more closely" without re-measuring* actually asks for.

**What the shortfall is bounded to, and how that stays true.** One role, one theme.
`resolve_vscode_theme_test.py` asserts that light's under-floor list is exactly `["number"]` and
that dark's is empty, so a second role slipping under fails a test rather than passing unnoticed,
and the decision gets re-taken on purpose.

**What is still held to AA**, because parity does not reach it: every colour this app chose for
itself rather than transcribed. The two search-match foregrounds are stated precisely so a match
can be read (`searchMatchText`, `currentSearchMatchText`), and the active editor border is held to
1.4.11's 3:1. Those are ours; nothing about the reference excuses them.

**Two alternatives were measured before this was settled**, and both are recorded because they are
what a reader will think of first:

- *`hc_light.json`'s `#096d48`* - the same green darkened by VS Code's own high-contrast theme,
  hue moving 0.6° in OKLCH, measuring 6.00:1. Rejected: it is a value from a theme the app is not
  transcribing, so it buys AA at the cost of the parity that is the point of the phase.
- *2026 Light's own `constant` rule* `#0550ae` (7.29:1). Rejected twice over: light numbers would
  read blue where VS Code shows green, and dark would have to follow onto `#79c0ff`, which is
  dark's `heading`.

The measurement is reproducible per role:

```sh
python3 scripts/resolve_vscode_theme.py \
  reference/research_repos/vscode/extensions/theme-defaults/themes/2026-light.json \
  --against sideBar.background
```

### A search match is a wash over the code, and the bold on the current one is ours

VS Code states **no foreground** for a match. `editor.findMatchForeground` and
`editor.findMatchHighlightForeground` are both registered `null`
(`platform/theme/common/colors/editorColors.ts:152,160`), and the backgrounds are translucent so
the syntax colour underneath survives - the registration for `findMatchHighlight` says so in
words, *"must not be opaque so as not to hide underlying decorations"*.

This app did the opposite until P2u-3e: an opaque fill with the text colour restated on top, which
is JupyterLab's structure and was taken after measuring that IntelliJ's approach left
`DEFAULT_KEYWORD` at 3.16:1 inside a match. Parity replaces it, and `searchMatchText` and
`currentSearchMatchText` are **deleted** rather than left unread - there is no foreground to state.
`bothKindsOfMatchAreAWashRatherThanAFill` asserts the alpha, because translucency is now
load-bearing: an opaque value here hides the code and nothing is left to rescue it.

**What that costs, measured.** The reference separates the current match from the others by alpha
alone - `#27678290` against `#27678280` - which composites on the dark cell to `#214555` against
`#20414F`, **1.06:1** apart. Light is `#BBD6F1` against `#E1EBF8`, 1.25:1. Neither theme sets
`editor.findMatchBorder` or `editor.findMatchHighlightBorder`, so nothing else carries it. At arm's
length on a phone, the current match is not visually distinguishable from the rest by colour.

**Two things carry it instead, and one of them is a departure.** The find bar's counter and arrows
are the reference's own answer - VS Code's find widget has both, and so does `FindTopBar`. The
other is the **bold weight** on the current match, which is this app's and predates P2u-3e: *a
weight reads at a glance where a hue alone has to be compared against its neighbours.* It was a
nicety when the current match was also bright yellow; with parity it is most of the signal. Kept,
and recorded here as an adaptation rather than left to read as something the reference does.

### A link takes the string colour, and the underline is the whole signal

`string.other.link` resolves to `string`'s own value in both 2026 themes - `#a5d6ff` dark,
`#0a3069` light - and VS Code carries the difference in `markup.underline`, a rule that sets
`fontStyle` and no colour at all. The app already underlined links, so nothing changed in
`styles()`; what changed is that the underline went from decoration to **load-bearing**, and
`EditorPaletteTest.aLinkTakesTheStringColourAndIsToldApartByItsUnderline` asserts both halves so
neither can be "tidied" away. The obvious tidy - giving the link a colour of its own - would depart
from the reference to solve a problem the reference does not have.

### The palette states its colours; it does not derive them from a Material role

Syntax colours used to come from `colorScheme` roles — so `NUMBER` sat close to the body text and
the value ultimately came from the user's wallpaper, which nothing could assert. They are stated
constants now, with every WCAG ratio pinned by `EditorPaletteTest`.

**What is pinned outlived where it was read from.** This entry was written when the source was
IntelliJ's `expUI_{dark,light}Scheme.xml`; since P2u-2 the syntax half is VS Code's resolved 2026
themes, and P2u-3 moves the surfaces. The decision that survives every re-source is the one in the
heading: a colour a role could supply is a colour no test can hold.

### One editor font, whatever the cell holds — source is code, rendered prose is not

A markdown cell's **source** is drawn in `codeSourceStyle`, the same style a code and a raw cell's
source is drawn in. `markdownSourceStyle` is deleted rather than redefined: one editor font means
one function, and a second name returning "the same thing" is what let the two drift.

Both references agree, and each was read rather than assumed:

- **VS Code** builds a markup cell's editor as a plain `CodeEditorWidget` over
  `getBaseCellEditorOptions(viewCell.language)` — a code cell's own options — with no font override
  in the file (`view/cellParts/markupCell.ts:66,331`; `grep -c RunToolbar` there is 0 for the same
  structural reason). Its one markdown-specific font setting, `markupFontFamily`, is a
  `computeWebviewOptions()` value (`notebookOptions.ts:748-772`) and styles the **rendered preview**.
- **JupyterLab**'s `.cm-editor` takes `--jp-code-font-family`
  (`packages/codemirror/style/base.css:5-9`) with no per-cell-type override.

**This is not P2l-3b and must not be collapsed into it.** That entry settled that code inside
*rendered* markdown inherits the **prose** size (`rendermime/style/base.css:407-417`). Different
surface, opposite direction: source is code that looks like code, rendered prose is prose that
happens to contain code. `EditorThemeTest` pins both in one test so the pair cannot drift apart.

### An emulator with a hardware keyboard cannot prove a phone's layout

P2s's flow was green on `dev36` and failed on the tablet, and the assertion was not what was wrong:
opening a markdown cell's editor takes the caret, the soft keyboard comes up, and it covered the
cell the frame was comparing against. `dev36` has a hardware keyboard, so nothing ever came up
there. Any flow that opens an editor and then looks at what is *below* it must put the keyboard
away — `hideKeyboard`, which `a5-markdown-editor.yaml` already did for this reason.

**And `wm size` is not a geometry on its own.** It sets the *natural* orientation's resolution, so
`1080x2400` on an auto-rotated tablet came out `2400x1080` — a landscape two-pane shell with the
notebook in a narrow column, which is not the layout being measured and not what the emulator drew.
A driver that overrides the window must also lock rotation, the way `p1g-one-container-driver.sh`
does, and restore it on exit.

### A one-second `assembleDebug` looks exactly like a stale APK

Gradle's build cache returns a previously-compiled variant in about a second, which is
indistinguishable from "nothing was rebuilt" in the log. For a **before/after** pair that
difference is the whole evidence: a `before` frame captured from an `after` APK is green,
identical to its neighbour, and worthless.

So a paired capture verifies the artefact before it captures anything. P2s checked the symbol its
change deletes:

```sh
for f in $(unzip -l app-debug.apk | grep -o 'classes[0-9]*\.dex'); do
  unzip -p app-debug.apk $f | strings | grep -c markdownSourceStyle
done
```

`classes4.dex` carried three occurrences in the `before` build and no dex carried any in the
`after` one. The check is cheap, and it is the only thing standing between a cached build and a
fabricated pair.

### Markdown renders natively — no WebView, no HTML

commonmark-java AST → recursive composables, following JetBrains Jewel
(`reference/research_repos/intellij-community/platform/jewel/markdown`, Apache-2.0): blocks recurse,
leaf blocks hold an inline *model* and the `AnnotatedString` is built at draw time, inline code
takes `SpanStyle.background`, a quote's rule is `drawBehind`. Its `gfm-tables` extension gave
`RichTable` its shape: cells hold inlines only, and column widths come from `maxIntrinsicWidth` in
a custom `Layout`. The same applies to `text/html` outputs — parsed with jsoup, drawn as Compose.

**Audit A1c:** a rendered markdown cell renders by default and a tap opens the editor **with the
caret placed**. The first version opened an editor with no caret, which no JVM test could see.

### A search query is a regular expression, and Java's `^` is not JavaScript's

`Private.parseQuery` (`packages/documentsearch/src/searchmodel.ts:398-427`) turns all four of the
search box's inputs into one `RegExp`, so **every search is a regex** and "Use Regular Expression"
only decides whether the user wrote it or the app did. The escape set is kept character for
character rather than swapped for Kotlin's `Regex.escape`, which wraps in `\Q…\E` and has a seam
on an input containing `\E`.

The last of its rules - *"if the empty string is hit, the search logic will freeze the browser
tab"* - **cannot be transcribed literally**, and measuring is what said so:
`Pattern.compile("^", MULTILINE).matcher("").find()` is `false` on this machine's JDK, where
`new RegExp('^', 'gim').test('')` is `true`. Read as written, a bare `^` would slip past the guard
and report a zero-width hit at every line start. The probe drops `MULTILINE` and the searching
regex keeps it, which is what makes the two agree; `$` and every unanchored pattern already did.

### Typing a query finds and counts; only the arrows move the notebook

`startQuery` ends with `highlightNext(true, { from: 'selection-start', scroll: false, select: false })`
(`packages/notebook/src/searchprovider.ts:389-401`), and `activateNewMatch`'s **first line** is
`if (!shouldScroll) return` (`:604-610`). So a new query sets the current match and does nothing
else - no scroll, no active-cell change, no unhiding. `highlightNext()` from the arrows passes no
options, defaults `scroll` to true, and gets the whole reveal.

That is the seam P2i-3 and P2i-4 are split on, and it is the reference's own rather than a
convenience: a search box that scrolled the document on every keystroke would move the notebook
out from under whoever was typing.

Two consequences worth stating: **every cell is searched, hidden or collapsed** - `startQuery`
builds a provider per widget with no visibility filter at all (`:352-386`) - and the query is
re-run after every edit, which the reference names in its own comment (*"`startQuery` is also used
as 'restartQuery' after each text change"*, `:391-395`).

### A match is drawn with its own foreground, and that is measured rather than copied

IntelliJ's `SEARCH_RESULT_ATTRIBUTES` declares `FOREGROUND` empty and leaves the code its own
syntax colours. Transcribed literally, its dark green puts `DEFAULT_KEYWORD` at **3.16:1** on this
app's dark palette - under the 4.5:1 floor `EditorPaletteTest` holds every other piece of source
to - and every background dark enough to fix that measures **1.08:1** against the cell, which is
not visibly a highlight at all.

JupyterLab is out of the same squeeze the same way: `.cm-searchMatch` sets `color` as well as
`background-color` (`packages/codemirror/src/theme.ts:106-115`), losing the syntax colour inside a
match on purpose. So this app takes **IntelliJ's backgrounds and JupyterLab's structure**, and the
foreground is whichever of black or white clears the floor - asserted, not chosen.

The **current** match is JupyterLab's `--jp-search-selected-match-background-color` and Compose is
why: IntelliJ marks it with no background at all, a rounded box outlined in `CARET_COLOR`
(`EditorLivePreviewPresentation.kt:27-29`), and a `SpanStyle` carries a background, a weight and a
decoration but not a border. It is the second colour in `EditorPalette` that is JupyterLab's, for
the same kind of reason as `cellMultiSelected`.

### A match in rendered prose is counted and not lit

`MarkdownCellSearchProvider` searches a markdown cell twice - its editor, and the **rendered** DOM
through a `GenericSearchProvider` over `cell.renderer` (`packages/cells/src/searchprovider.ts:330-345`).
This app searches the source only, because rendered prose here is `RichText` blocks rather than a
text field and threading spans through them is the work the output filter was already scoped away
from.

The count therefore includes a match nothing on screen shows, until the cell is unrendered - which
a tap does, and which reaching the match does too (`_unrenderedByHighlight`, `:380-410`). Stated
rather than hidden by keeping such matches out of the count: a search that silently skipped rendered
cells would be wrong about the document, where this is merely late.

### The find bar takes the notebook's bar, and the notebook's bottom bar goes with it

The reference anchors a floating panel at the top-right of the document carrying a find box, three
option toggles, a counter, two arrows, a filter button and a close - nine controls in one row
(`packages/documentsearch/src/searchview.tsx:640-760`). At 360dp that is the arithmetic
`SelectionTopBar` already did once and lost, so searching takes the bar, exactly as marking a run
does.

**And the bottom bar goes**, which is not symmetry but a measured repeat of the U6 defect. The find
box holds the caret while the notebook is in *command* mode - a state nothing before P2i could
reach - so the kernel bar would be on screen under a soft keyboard. On a 320dp-tall landscape phone
that stacks a find bar, a kernel bar and the IME, and `Scaffold` resolves the overflow by handing
the cell list **zero height** (`evidence/u6-ime-layout/640dp/`).

**One command, two strings, and that is a harness rule.** The action on the notebook's bar is
"Find in notebook" and the box is "Find". A flow closing the bar asserts the box is gone, and
Maestro matches whole nodes - so two controls meaning the same thing must not be spelled the same
way, or the assertion is answered by the icon that just came back.

### Reaching a cell reveals it - in two strengths, and the line between them is the reference's

**Any** jump expands the collapsed heading above its target: `activeCellChanged` is wired to
`NotebookActions.expandParent` (`packages/notebook-extension/src/index.ts:2871-2873`, over
`actions.tsx:2288-2306`). **Landing on a search match** adds two more, and both exist only to put
the matched characters where they can be seen: `if (activeCell.inputHidden) activeCell.inputHidden
= false` (`packages/notebook/src/searchprovider.ts:630-634`), and `cell.rendered = false` for a
markdown cell (`packages/cells/src/searchprovider.ts:380-410`).

**The first citation corrects a reading recorded in P2h.** The table of contents' jump treated a
hidden heading as unreachable *on the grounds that the reference does not solve it either*. It
does.

**And the second is why one helper for both was wrong**, which cost a green flow to find. Shipped
as a single `withCellRevealed`, a table-of-contents jump unrendered the heading it landed on and
put `## Part A` on screen as an editor where somebody had asked to read a heading. The reference is
emphatic about exactly this and says so in a comment: a ToC jump forces command mode first,
*"to avoid entering Markdown cell in edit mode if the document was in edit mode"*
(`packages/notebook/src/toc.ts:681-684`). Every assertion in the flow was green; the screenshot is
what said otherwise, for the fourth time in this project.

Two rules the reveal has to keep, both of which a test pins:

- **It returns the state unchanged when nothing was hiding the cell.** Two of the three flags are
  persisted, so stepping through an ordinary notebook's matches would otherwise mark it dirty and
  rewrite the file for having been read - the rule `withMarkdownRendered` already states.
- **Unrendering does not take the caret.** A command that unrenders is not a tap asking to type in
  the cell; conflating them is what put a soft keyboard over an untouched notebook in P2e-3.

### A `LazyColumn` needs the IME inset when nothing else is taking it

Every surface this app can have on screen under a soft keyboard carries its own `imePadding` -
`SymbolRow` in edit mode, `NotebookBar` in command mode - so the cell list has never had to.
**Searching is the first state with a keyboard and no bottom bar at all**, and without the inset
the list lays out to the whole window and draws its lower half behind the keys.

Measured rather than noticed: with the IME up the list reported `viewport = 0..987` on the tablet,
so `visibleItemsInfo` called a cell *visible* while it was under the keyboard - and the arrows
declined to scroll to a match nobody could see, correctly by their own rule and wrongly by any
other. Three flow runs went green through it; the screenshot is what said otherwise, for the third
time in this project.

**And `inViewport` alone is not the reference's rule either.** `activateNewMatch` scrolls when the
cell is out of view *and then* calls `editor.revealPosition(...)` (`:625-641`) - two steps, and the
second is what makes the match readable rather than merely present. With a cell-sized list item the
equivalent is asking for the whole cell, so the guard is *fully* visible rather than visible.

### A replacement is two steps, and Kotlin throws where JavaScript does not

Both of the reference's call sites build it the same way
(`packages/codemirror/src/searchprovider.ts:344-352`, `:412-422`): substitute first when the query
is a regular expression - `match.text.replace(query, newText)`, so `$1` works - then apply
`preserveCase` to *whatever that produced*, not to what was typed. The third arm of `preserveCase`
is the one a guess gets wrong: sentence case lowercases the rest of the word
(`first.toUpperCase() + suffix.join('').toLowerCase()`), so `Cat` → `dOG` gives `Dog`.

**Kotlin's `Regex.replace` raises on a group reference that does not exist; JavaScript's
`String.prototype.replace` leaves `$9` as the characters it is.** A replacement being typed names a
group that is not there yet for most of the time it is being typed, so this falls back to the
literal text. Same shape as the `^` divergence in the query, and the same reasoning as
`CompiledQuery.Invalid`: half-typed input is the ordinary case here, not the exceptional one.

**A replace is undoable, and it cost nothing to be.** `docs/DECISIONS.md` wrote the `rewrote` rule
for Split and named find-and-replace as its next user; `recording` derives the rewritten cells from
the before/after diff and bumps `sourceRevisions` with them, so the cell's field is told its text
changed underneath it without replace knowing that mechanism exists. **Replace All is one undo**,
not one per cell, which is `CellEdits.applying`'s own boundary and the reference's
`sharedModel.transact { }`.

**Where the cursor goes after a replace is forward, and it has to be said explicitly.**
`replaceCurrentMatch` walks the regenerated matches for `position >= positionAfterReplacement`
(`:360-380`). Landing by *cell* alone - which is what a new query does - would send a replacement
in the second of two matches in one cell back to the first, which is backwards. `firstHitAtOrAfter`
is the offset-aware sibling of `firstHitFrom` for exactly that.

**`startWithReplace` is a menu row rather than a chevron**, and `preserveCase` is offered only
beside a replacement. The reference puts both on the box (`searchview.tsx:245-255`, `:694-699`);
the bar has no room for a seventh target at 320dp, which is the same arithmetic that put the three
query options behind the overflow.

### The `output` filter searches what it can also show, and stops there

`getFilters` declares two, both **off** by default (`packages/notebook/src/searchprovider.ts:228-256`):
`output` (*"Search Cell Outputs"*, `supportReplace: false`) and `selection`
(*"Search in %1 Selected Cells"*). Both are built; the first is narrower than the reference's and
says so in its own label.

The reference searches its **rendered DOM**, so a DataFrame's cells are searchable there. Here an
output becomes one of three things (`RenderedOutput`), and only `Text` is a string: `Document` is
`RichBlock`s and `Image` is bytes. So this searches stream, stderr, a traceback and `text/plain`,
and leaves `text/html` and tables alone. **An uncounted match is better than a counted one nobody
can see** - the same rule that made a match in rendered prose late rather than silent, and the same
rule that put a collapsed *output* area in `withMatchRevealed` even though `activateNewMatch` never
looks at one.

**`supportReplace: false` is one rule here rather than a disabled control plus a guard.** The
reference disables the filter while the replace options are shown and says why in the filter's own
text (*"not available when replace options are shown"*); `SearchState.outputsSearched` is
`searchOutputs && !replaceShown`, so with the row open there are no output hits at all and a
replace cannot stand on one. The toggle is remembered, so closing the row brings them back.

**A hit carries where it is, not just which cell.** `SearchHit.outputAt` is null for the source and
otherwise the index of the *rendered* output - the reference's own split, where a code cell holds
one `CellSearchProvider` for its editor and a `GenericSearchProvider` per output widget
(`packages/cells/src/searchprovider.ts:63-100`). The two are not interchangeable: only a source
match can be replaced, only a source match is drawn by a text field, and an output match is drawn
by an `AnnotatedString` instead, because an output is drawn and never typed into.

**The `selection` filter is reachable, and checking that was the point.** Marking a run replaces
the notebook's bar, so the Find action is unreachable *from* a marked run - but a cell's own `⋮`
still offers "Select cells" while the find bar is up, and search wins the bar over marking, so the
run can be started underneath it. The row is offered only while something is marked, this app's
rule for a control whose two states would look identical.

### A shell is reached through a path named `sh`, and all 304 applets with it

P4b. busybox dispatches on the **basename of the path it is reached by**, and its `argv[1]`
fallback engages only for a basename prefixed `busybox`. `libbusybox.so` is not, and `lib*.so` is
not negotiable - it is what AGP packages into `lib/<abi>/`. So the applet name cannot ride on
`argv[0]` either: `ptyprocess.PtyProcess.spawn`, which is how terminado reaches a shell, does
`command = which(argv[0])` and then **overwrites `argv[0]` with it**.

Measured against the shipped BusyBox 1.37.0 rather than argued from its source, with a control
proving the multiplexer is present and working (`evidence/p4-terminal/busybox-dispatch.md`):

| Invocation | Basename | Result |
|---|---|---|
| `libbusybox.so` | `libbusybox.so` | `applet not found`, 127 |
| `libbusybox.so sh -c …` | `libbusybox.so` | `applet not found`, 127 |
| `sh -c …` (symlink to it) | `sh` | works |
| `busybox-x sh -c …` | `busybox-x` | works |

**And `bin/sh` alone is not enough, which the obvious gate hides.** 304 of the rootfs's 353
symlinks are the same absolute `-> /bin/busybox` - 81 in `bin`, 59 in `sbin`, 137 in `usr/bin`, 27
in `usr/sbin` - so a shell with only `bin/sh` repaired has no `ls`, `cat`, `grep` or `vi`, and
`sh -c 'echo ok'` still prints `ok` because `echo` is an ash builtin. **P4b's gate forks an applet.**

**One maintained link rather than 305.** `bin/busybox` points into `nativeLibraryDir` and is
repaired on every launch, as the loader link is; the applets are made *relative* to it once, so
they can never go stale. `execve` walks the chain to find the inode and runs the SELinux check on
the target, while busybox reads `ls` off the path the caller gave it.

**PATH decides whose coreutils the shell gets, and this was nearly captioned wrong.** Under
`run-as`, Android's PATH is in place and an unqualified `ls` is **toybox 0.8.12-android**, not ours;
with the Space's PATH it is BusyBox 1.37.0. The first draft of
`evidence/p4-terminal/busybox-on-device.txt` captioned two such lines as proof that `sh` had forked
a busybox applet, and they were not. The app is unaffected - `SpaceRuntime.environment()` sets
PATH to the Space's, the server inherits it, and terminado's shell inherits it from the server -
but the evidence had to say what it actually showed.

### Staging needs no emulation, and patchelf never cared about the target architecture

`tools/rootfs/stage-app-artifacts.sh` ran patchelf inside the **target-ABI** image, so staging
arm64 on an x86_64 host needed `binfmt_misc` registered - an undocumented prerequisite that fails
as `exec format error`. patchelf rewrites ELF headers and never executes the binary, so an x86_64
patchelf edits an aarch64 ELF correctly; measured, and the loader it produces is **byte-identical
by sha256** to one staged the old way.

The ELFs now come from the tarball - which *is* `docker export` of that image, so the bytes are the
same - and patchelf runs in a host-architecture container. The script's own comment used to justify
the old shape as editing *"binaries against the same libc that produced them"*; that is not what
patchelf does, and the real reason for a container - the host needs no patchelf installed - is
preserved.

### A pseudoterminal works on the device, and the terminal phase keeps its shape

P4a, measured before anything was built: `pty.fork()` succeeds from this app, a process `execv`'d
inside the pty keeps it (`os.isatty(1)` is True on the far side, which is what terminado actually
does), and `TIOCSWINSZ` on the **master** reaches the process inside - rows=43 cols=132 set by the
parent, read back as `132x43` by the child. **Zero seccomp refusals**, checked in `tmp/sigsys.log`
rather than assumed from a green assertion. `evidence/p4-terminal/pty-support.md`.

**Probed first, ahead of staging busybox, and that ordering is the decision.** `docs/PLAN.md` had
the staging step first; a `pty.fork()` child can write and `_exit` with no shell at all, so the
cheaper question was also the one whose answer could have thrown the other away.

**The `\r\n` is the proof, not the payload.** The child wrote `\n` and the master read `\r\n`, so a
line discipline is in the path: this is a pty rather than a pipe that happened to work. The same
fact is what broke the first draft of the probe - a canonical master holds a byte until a newline,
so a go-ahead written as `b"g"` hung the child, the parent and the instrumentation, which reported
`Tests 0/3 completed` and nothing else. **Every probe now runs under `signal.alarm`**, because a
refusal is an answer and a hang is not.

**Termux is evidence and not proof, and the distinction is worth keeping.**
`terminal-emulator/src/main/jni/termux.c:36-51` opens `/dev/ptmx` and calls
`grantpt`/`unlockpt`/`ptsname_r` from an ordinary app process - but at `targetSdkVersion=28` and
against bionic, where ours is musl under a preloaded SIGSYS handler at 36. It raised the prior; it
could not close it.

**This does not settle that a shell can be reached** - see the busybox entry.

### ANSI handling today is a stripper, not an emulator

`RenderedOutput.kt:96-102` regex-replaces SGR escapes out of tracebacks
(`withoutAnsiColours()`). It is not reusable for a terminal, which needs a grid with cursor
addressing — see `docs/PLAN.md` P4e-P4h, which were P4d until the terminal phase was re-lettered.

---

## Architecture and UI

### The two-pane layout is `ListDetailSceneStrategy`, and it cost a toolchain

U7 hand-wrote `TwoPaneSceneStrategy.kt` + `TwoPaneScene.kt` (165 lines) because
`NavigableListDetailPaneScaffold` puts both panes inside **one** `NavEntry`, which stops the editor
being a navigation entry at all — and both nav3 decorators key on `NavEntry.contentKey`. That
breaks ViewModel *identity* (`viewModel { }` resolves on class name, so notebook B is handed
notebook A's ViewModel) **and** lifetime (`onCleared` never runs, so the kernel socket is never
closed). It also said: *"If that artifact goes stable, this is the thing to delete."*

**P1e deleted it.** `androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0` is released,
and `ListDetailSceneStrategy` keeps every entry an entry — `calculateScene` collects
`scaffoldEntries: MutableList<NavEntry<T>>` and `ThreePaneScaffoldScene` renders each one's own
`Content()`. So the scaffold is still not what this uses, and the reason has not changed; only who
writes the scene has.

Three things it cost, all measured (`evidence/p1e-list-detail-scene/`):

- **compileSdk 37, AGP 9.1.1, Gradle 9.3.1.** `adaptive-*:1.3.0` refuses to be consumed below
  compileSdk 37 and AGP 9.1.0 and says so in its AAR metadata; AGP 9.1.1 then requires Gradle
  9.3.1. **`targetSdk` stays at 36** — compileSdk decides which APIs may be called, targetSdk
  is what the exec model above depends on.
- **The adaptive family left the Compose BOM.** Its POM requires `adaptive-navigation` strictly
  `[1.3.0]`, so every `adaptive*` the BOM pinned at 1.2.0 resolves to 1.3.0.
- **`BackNavigationBehavior.PopLatest` is mandatory, not a preference.** With both panes expanded
  the default `PopUntilScaffoldValueChange` finds no earlier destination whose scaffold value
  differs, returns `-1`, and the scene pops *every* entry — back from a notebook left the app for
  the launcher, measured on the tablet.

**This forbids** re-deriving pane pairing in this codebase: roles are declared with
`ListDetailSceneStrategy.listPane()` / `.detailPane()`, never by position. **`extraPane()` is the
third role and P2h did not take it**, which is worth stating here because this file used to say it
would: an extra pane is another `NavEntry` with its own `ViewModelStore`, and a table of contents
needs the notebook's - see "The table of contents is a panel where there is room for one". A pane
that is *another destination* is what this role is for; a pane that is a second view of the one
already open is a column inside its scene.

Two panes at **840dp**, not 600dp: a 360dp list pane leaves 239dp for the notebook in a 600dp
window, narrower than the phone the single-pane layout was designed for. A width rule, never a
device one — and **the library's own default**, which was checked before the hand-written rule was
deleted: `calculatePaneScaffoldDirective` gives Compact and Medium one horizontal partition and
Expanded two, and Expanded starts at 840dp.

### Touch rules, and the two that cost something to learn

- **48dp is the minimum touch target** (Material), pinned by `CellTouchTargetTest`. The nuance is
  worth keeping because it once produced a false accessibility bug report: `IconButton` applies
  `minimumInteractiveComponentSize()` **outside** the caller's modifier, so wrapping one in
  `Modifier.size(32.dp)` shrinks the ripple and *not* the pressable area — measured on device at
  48×48dp with the wrapper in place. What the test is really guarding is a control built from a
  bare `clickable`, which would have a real 24dp target and nothing else to catch it.
- **A long press on a cell is not available.** `BasicTextField` owns that gesture for text
  selection, so cell actions need a visible control — which is why the cell header carries a `⋮`
  button and not a hidden gesture. It took three attempts to learn: a press on the cell produced
  Android's Select all / Autofill toolbar, and a press on the header worked but was
  undiscoverable. **Nothing on screen announcing an interaction is not an interaction.**
- **A control that is on screen for a state it does not serve is a control that lies.** The symbol
  row shows in edit mode whatever the cell is, and `MarkdownCellField` never registered itself with
  `FocusedCellInput` — so in a markdown cell the row sat above the keyboard doing nothing at all.
  It is the same failure the `⋮` button exists to avoid, one layer down, and it went unseen until a
  flow tried to drive a caret with `←` there. Registering is one shared composable now
  (`attachWhileFocused`) rather than a block each field could forget.
- **Anything drawn above the soft keyboard needs `imePadding()` *and* `navigationBarsPadding()`** —
  the second for the moment between the keyboard closing and the layout catching up (`SymbolRow`).
- **A behaviour that cannot be reached on a phone is adapted, never invented**, and the adaptation
  is written down beside what it departs from.

### A gap has a name, and `theme/Spacing.kt` is where the name is

**Measured before the rule existed**, which is what makes it a rule rather than a preference: 63
`.dp` literals across 20 files in 16 distinct values, **13 of them off Material's 4dp grid** -
`14.dp` three times in the explorer and its recents, and one each of `6`, `3`, `2`, `5`, `18`,
`1`. No single one of those was wrong. What was wrong is that a gap had no name, so the next one
was chosen by eye against whatever a neighbouring file happened to use.

**The rule is about the point of use, not about the number.** A literal buried in a modifier chain
is a decision nobody can find, compare or change. `scripts/check_ui_dimensions.py` refuses one, and
accepts two things instead: a value taken from `Spacing`, or a named `val` whose KDoc says where
the number came from. `0.dp` is exempt - it is a sentinel, not a spacing decision.

**Not every dimension is a gap, and forcing one onto the scale would rebuild the problem.**
`CellStripe`'s 4dp and 2dp are `DefaultNotebookEditorAppearanceSizes`, `CellFrame`'s 5dp is
`codeCellLeftLinePadding`, `POPUP_ELEVATION` is `ElevationTokens.Level2`, `MARKER_WIDTH` is a
column that exists so list text starts at one x. Twenty literals survive on those terms, each
carrying its citation - which is what `CellFrame` and `CellStripe` were already paying before the
gate asked anyone else to.

**Two heights were decided by arithmetic and then measured on device** (`evidence/p2l-2-spacing/`,
emulator at density 160 so 1px = 1dp, read from `uiautomator dump` rather than counted in pixels):

- The explorer's two-line row was 68dp and is **72dp**, which is `ListItemDefaults`' own two-line
  height rather than a number this app picked.
- The breadcrumb crumb was **exactly 48dp**, Material's floor. `Spacing.md` would have made it
  **44dp**, under it - so the crumb takes `lg` like the rows. **An off-grid value is not
  automatically rounded down**; where a control is on the touch minimum, the grid step that keeps
  it above wins.

**A gate that cannot fail is worse than no gate**, and this one could for as long as it took to
run its own test from the wrong directory: `rglob` over a directory that is not there yields
nothing, so it reported a clean tree. It raises `FileNotFoundError` now, and its test suite
includes one case that runs it over this repository.

### Material's type scale is taken whole, where its colour scheme is not

**The asymmetry is the decision, and each half has a reason.**

`DarkColorScheme` states every reachable role because whatever is left out keeps
`darkColorScheme()`'s default, and those defaults are the Material baseline **purple** - a colour
from a different design system than the editor's. `JupydroidTypography` states nothing, because
Material's default type scale *is* the design system: fifteen roles with measured sizes, line
heights and tracking (`TypeScaleTokens`). Re-typing those numbers here would be a copy that can
drift from the library rather than a decision.

**What this forbids is a hand-built `TextStyle` for a Material role**, and that is not a style
preference. Material derives every role from `DefaultTextStyle`, which carries
`platformStyle = defaultPlatformTextStyle()` - `includeFontPadding = false` on Android - and
`lineHeightStyle = DefaultLineHeightStyle` (`TypographyTokens.kt:272-276`). A bare `TextStyle`
carries neither, so a role rebuilt by hand lays out differently from every role beside it while
appearing to name the same numbers.

The app shipped that way from Step 1 to P2l-1. `theme/Type.kt` was the Android Studio template:
one `bodyLarge` at 16sp/24sp/0.5sp, which are exactly the three values Material already supplies
for it. So it changed nothing it appeared to change, and stripped the vertical metrics from the
one role `MaterialTheme` also provides as the ambient `LocalTextStyle` (`MaterialTheme.kt:107`) -
the default for every `Text` no container has styled, and for the cell source through
`codeSourceStyle()`.

**Measured, not eyeballed** (`evidence/p2l-1-typography/`): a before/after pair from the same
driver, emulator and fixture. In the explorer's row the file name moved y154-169 to y156-171 and
the gap to its `just now` subtitle went 5px to 8px, the name no longer pushed up by the font's
legacy ascent padding. The ten-line code cell in the fixture is **pixel-identical**, because the
code field states its own line height - so this repairs a role, not the editor.

A real departure stays allowed. It goes in `theme/Typography.kt` with its reason and into
`TypographyTest`, which enumerates the fifteen roles the way `ColorSchemeTest` enumerates the
colour ones, so the next hand-built role fails a test rather than waiting to be seen.

### The breakpoints, so 840dp is not a magic number

`WindowSizeClass` (official): compact `< 600dp`, medium `600–840`, expanded `840–1200`, large
`1200–1600`, extra-large `≥ 1600`. The app reads
`WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND` rather than a literal.

Two panes start at **expanded**, not medium, and the arithmetic is the reason: the list pane is
360dp, so a 600dp window leaves the notebook 239dp — narrower than the phone the single-pane
layout was designed for — where 840dp leaves it 479dp.

### Window width is a parameter, never measured inside a composable

`currentWindowAdaptiveInfo()` reads the *activity's* window, which `DeviceConfigurationOverride`
does not change — so a test that "forces a width" drives one branch twice while appearing to cover
both. `MainNavigation` takes `twoPane` as a parameter.

### A file's name is its identity (X1b)

A notebook's id is its path, suffix included; renaming the title renames the file. The rule and the
409-on-collision belong to **files**, not to notebooks — the same rule governs `.py` and anything
else in the workspace.

### Reads may hit the repository; server-side algorithms may not be re-implemented

The workspace is read from disk directly (`DiskWorkspaceRepository`) rather than through
`/api/contents`: it is faster and works before the server is up. But checkpoints, notebook
trust/signing and nbconvert export are **server-side algorithms** and go through the server's API.
Re-deriving HMAC notebook signing in Kotlin would be re-implementing a security mechanism.

**Writing directly has one known consequence, and it is not free.** `ContentsManager` signs a
notebook on save — `check_and_sign` over `nbformat.sign.NotebookNotary`
(`services/contents/manager.py:116-120,694-706`) — so a notebook this app writes is **unsigned**
and would open untrusted. What follows from that is the entry below.

### `notebook:trust` is inert here, and this names what would make it real

**Decided in P2j on the measurement rather than on the parity list.** The command is not built,
and the reason is that there is nothing on this device for it to gate.

**Trust gates exactly three renderers in JupyterLab**, and the list is closed rather than
representative:

| Mime type | Untrusted behaviour | Where |
|---|---|---|
| `image/svg+xml` | refuses to draw: *"Cannot display an untrusted SVG"* | `packages/rendermime/src/renderers.ts:450-453`, `factories.ts:58` `safe: false` |
| `text/javascript`, `application/javascript` | no renderer is chosen at all | `factories.ts:88-89`, `safe: false` |
| `text/html` | sanitized, and its `<script>` tags are not eval'd | `renderers.ts:44-58` |

Everything else its registry ships — `text/plain`, `image/png`, `text/markdown`, `text/latex`,
`application/vnd.jupyter.std{out,err}` — is `safe: true` and renders identically either way
(`factories.ts:12-79`). The choice is made once, in `preferredMimeType(bundle, trusted ? 'any' :
'ensure')` (`packages/outputarea/src/widget.ts:649,772`).

**This app renders none of the three, and each half of that was checked rather than assumed:**

- `HtmlInlines.kt:62` drops `script` and `style` *before the model is built*, so no markup this
  app parses can carry code. It is there for a rendering reason — pandas wraps every DataFrame in
  a `<style scoped>` block — and the safety is a consequence, which is exactly why this entry
  exists: nothing is currently holding that line on purpose.
- `RANKED_MIME_TYPES` (`RenderedOutput.kt:68`) is `text/html`, `image/png`, `text/plain` and
  nothing else. There is no SVG renderer and no JavaScript renderer to be unsafe.
- **There is no WebView anywhere.** The three matches for the word in `app/src/main` are comments
  saying so (`RichText.kt:26`, `HtmlDocument.kt:11`, `MarkdownDocument.kt:30`).
- `RichInline.Link` is annotated with its destination (`RichInlineString.kt:57`) and **nothing in
  `app/src/main` consumes `RICH_LINK_TAG`** — only its own test does. A link in an output is
  styled and inert; it opens nothing.

So a trust dialog here would change what every existing notebook's `text/html` and table outputs
show — a shipped, evidenced feature — in exchange for guarding a hazard that does not exist. That
is the control-that-lies failure pointed the other way.

**The trigger, stated so nobody has to re-derive this.** Whoever adds the first **SVG renderer,
JavaScript renderer, WebView, or clickable link** owns `notebook:trust` with it, and all of it:

- the per-cell state, derived by nbformat's own rule rather than a new one — `_check_cell`
  distrusts a cell only when a `display_data` or `execute_result` output carries keys outside
  `{output_type, execution_count, metadata}`, and a cell with only `stream` or `error` output is
  always trusted (`nbformat/sign.py:505-536`);
- the warning dialog, which is what the reference's `trust()` is almost entirely made of, and
  which does nothing when every cell is trusted already (`packages/notebook/src/actions.tsx:2504-2565`);
- `metadata.trusted = true` written into each code cell on accept, then a save — the reference's
  own two steps (`actions.tsx:2558-2562`, `notebook-extension/src/index.ts:3223-3236`). That
  persists here despite the unsigned file, because `_check_cell` **pops** the field and honours it
  (`sign.py:517`);
- and the gate itself: an untrusted output falls back to the safe mime type, never to a blank.

**Still forbidden either way:** re-deriving HMAC signing in Kotlin, and routing notebook *saves*
through the Contents API to get signing for free. The second is not a shortcut but a regression —
saves are debounced at 500 ms (`NotebookSaveDebounce`) and the server is stopped after 30 s of
idleness (`AppContainer.IDLE_STOP_GRACE`), so every keystroke's write would depend on a runtime
that takes seconds to come back.

### One object owns the Space's server, because starting one stops the last

`JupyterServerLauncher.start` begins with `stop()` (`JupyterServerLauncher.kt:50-51`). So a second
lazy starter beside `BaseSpaceKernelGateway` - an export, and later a terminal or a language server
- would kill the kernel's server the first time it was used, and strand the session running on it.

`BaseSpaceServer` is the one that starts it and `PerRunningServer` is the rule every client of it
needs: one instance while the server lives, a fresh one when a replacement takes over. **A client
learns it was replaced inside its own `build`**, which is what keeps `BaseSpaceKernelGateway`'s
"nothing the old server issued carries over" from drifting apart from "a new gateway exists".

The generation counter rather than the connection is what identifies a server, and that is a
measurement: the launcher mints its token once per *launcher*, and the OS may hand back the same
ephemeral port, so two `ServerConnection`s can compare equal across a restart.

### An export is named by the server, and its shape depends on the notebook

`GET /nbconvert/<format>/<path>?download=true` answers with three things and **none of them may be
derived from the format** (`evidence/p2j-2-export-formats/`):

- the `Content-Disposition` is RFC 5987 - `filename*=utf-8''name.html` - and never a plain
  `filename=`, on every one of the six formats that converted;
- exporting a notebook **with an image output** to `markdown` returns a **zip** named `<stem>.zip`,
  because that exporter writes each image beside the document and `respond_zip` then packs both
  (`jupyter_server/nbconvert/handlers.py:38-65`). The same notebook to `html` is not a zip, since
  that exporter inlines images as data URIs;
- so `ExportedDocument` carries the file name, the media type and the bytes, and
  `WorkspaceRepository` grew `writeBytes` beside `writeText` - a zip written through a `String` is
  a corrupt zip.

### The export menu offers what was measured, not what the server lists

`GET /api/nbconvert` names every exporter that *constructs* - `get_export_names` keeps one if
`e.enabled` (`nbconvert/exporters/base.py`) - and constructing is not converting. On the shipped
rootfs **fourteen are listed and six convert**: `asciidoc`, `latex`, `pdf` and `rst` route every
markdown cell through `convert_pandoc` and there is no pandoc; `webpdf` wants playwright and
`qtpdf`/`qtpng` want PyQtWebEngine.

The reference offers all of them and lets a conversion fail. **A row that can only fail is a
control that lies**, so this app draws the intersection of what the Space lists with what it has a
label and a measurement for - four rows. A Space that loses an exporter loses its row; one that
gains pandoc gains nothing until the list says so.

**And "Executable Script" is served by `python`, not by `script`.** `ScriptExporter` reads
`nb.metadata.language_info` to pick a language exporter and its extension
(`nbconvert/exporters/script.py:52-60`), and this app writes `kernelspec` alone - so on the device
`script` answered `text/plain`, named `.txt`, holding the cell sources concatenated with none of
`python`'s `# In[1]` structure. The reference excludes `python` only because its own `script`
already dispatches to it. **Writing `language_info` is the fidelity fix**, it is not built, and it
is the trigger that would let `script` be offered as itself.

### The format list is fetched when the picker opens, and not before

The Space is started on demand, so a menu that fetched the list on every open of the notebook's
overflow would boot a runtime - seconds - to populate itself. The reference caches on the first
notebook because its server is always up (`packages/notebook-extension/src/index.ts:855-890`).

That leaves this app with a state the reference has no name for, and it is a **row of text** rather
than a spinner: Android's accessibility tree carries a `DropdownMenuItem`'s label, so a
`CircularProgressIndicator` in a menu is a control no flow can read - the same reading that made
every toggle in this app two labels rather than one and a check mark.

### An export lands beside the notebook, and replaces what was there

The reference hands the bytes to the browser - `window.open(url, download=true)` - and where they
go is the browser's business. An Android app has no such place, so the artifact goes into the
workspace beside the document, where the explorer already lists it and a cell can already `open()`
it.

**An existing file of that name is replaced**, which is `jupyter nbconvert`'s own behaviour on the
command line and the only one that does not litter: a fourth `Analysis2.html` beside three others
answers nobody's question. The report names the file, so the replacement is never silent.

### A file leaves the app through a grant, never through a path

`IntentDocumentSharer` shares a `content://` URI from a `FileProvider` rooted at
`spaces/base/home/jupydroid` - the workspace, and nothing else under `files/`. A `file://` URI has
thrown `FileUriExposedException` since targetSdk 24 and the workspace is private storage besides;
and a provider rooted one level up could hand the rootfs, the recents file, the editor settings or
**the server's token** to any app the user picked.

### Kernel history is the kernel's, and the filtered list is not the history

`notebook:access-previous-history-entry` / `access-next-history-entry`. The wire is
`history_request` on the shell channel, `{output: false, raw: true, hist_access_type: 'tail', n}`
(`packages/notebook/src/history.ts:402-409`), a batch of **10** grown by 10 on each exhaustion
(`:99`, `:248`). The shipped kernel answers it out of IPython's sqlite input history:
`ipykernel/kernelbase.py:947-965` -> `ipkernel.py:612-645` -> `history_manager.get_tail`, which
calls `writeout_cache()` before it queries (`IPython/core/history.py:916-975`), so a cell run a
moment ago is already in the reply, and orders the **current** session's rows last.

So this is not a log of the notebook. It reaches cells that have been deleted and sittings the
document was never open for, which is the whole of what it buys over scrolling for the cell that
ran something.

**`setFilter` is three rules, and each changes what a tap does** (`:355-368`): a run of the same
input counts once; the text **already in the cell is removed**; and that same text is pushed on as
the last entry, which is what makes walking forward off the end restore what was being typed
rather than leave the cell holding somebody's old code. The first rule is why a repeat survives
only when something came between it - `last` moves only when an entry is kept.

**A longer tail does not move the cursor by the length difference.** `cursorOffset` (`:253-264`)
counts the entries that arrived *newer* than the ones already walked, because the kernel may have
run more cells between the two asks. Mutation-checked rather than reasoned: returning 0 from
`shiftAgainst` fails one test and only one. `fetchBatch` also re-seats the cursor **one entry older
than the oldest it had** - that is the `+1` in its own arithmetic - so the caller must not step
again after it, which is a bug this shipped with for one test run.

The reply's `session` number is read into `_kernelSession` there and consulted by nothing; it is
not transcribed.

**The round trip is shared with completion.** `complete` and `executionHistory` are the same shape -
mint a `msg_id`, wait for the socket, send, take the first frame a total reader recognises - so
`RealKernelGateway.SessionSocket.askOnce` is that, and it settles `mayRestartTheKernel = false`
rather than taking it from two callers who must not get it wrong. It is the **read-only half** of
the socket: a cell is worth replacing a silent kernel for, asking what a word completes to or what
was run an hour ago is not.

### Kernel history access is off by default — the first reference default this app keeps

`accessKernelHistory` is `false` (`packages/notebook-extension/schema/tracker.json:873-878`,
`packages/notebook/src/widget.ts:1488`), both actions return on their first line without it
(`actions.tsx:2616`, `:2634`), and the reference's own user guide makes it an instruction: *"To use
this feature, enable kernel history access in the notebook settings"*
(`docs/source/user/notebook.md:191`).

`EditorSettings`' two other departures were each bought with a reason a phone supplies - brackets
that close themselves save a trip through the symbol row, a gutter names the line a traceback
blames on a screen showing a fraction of a cell. **Nothing about a touch screen makes a cell's text
safer to overwrite**, so this one is left where the reference put it. The adaptation is *where*:
the reference files it under Settings, this app has none until P6c, so the row sits in View beside
the other persisted toggles and moves with them.

**The store is a diff, not a record.** `editor-settings.json` holds `{"accessKernelHistory":true}`
and nothing else, because kotlinx.serialization omits a property equal to its default. Reading back
fills the rest in, which is the same mechanism `EditorSettings`' docstring relies on for a field
added later - so a driver must not assert on a setting the flow did not touch, and the round trip
belongs to `EditorSettingsRepositoryContract`.

### The history keys go on the symbol row, and their place on it was measured

The reference binds these to `Alt ArrowUp` / `Alt ArrowDown` under a
`.jp-Notebook.jp-mod-editMode` selector (`schema/tracker.json:452-462`). There is no Alt key here,
and `SymbolRow` is the only thing on screen exactly when that selector would match - so it is the
surface, not the cell menu, which is eighteen rows deep and reachable in command mode too.

**Absent, not disabled, while the setting is off** - the rule this app applies to every control
that could do nothing.

**Labelled `Hist ↑` / `Hist ↓` rather than `↑` / `↓`.** Beside `←` and `→`, which move the caret one
character, a lone arrow reads as moving it one *line*. The word is what separates walking the
document from walking the kernel's history; the `contentDescription` carries the reference's own
command label.

**They sit after `Tab`, and that is a measurement.** Appended after `←`/`→` they fell off the right
edge of the editor pane on the tablet - `Hist ↓` entirely - and the flow failed with the key not
found. The row scrolls, so nothing was broken; but two keys somebody turned a setting on to reach
cost a swipe before every tap, which is this row's own frequency ordering read backwards
(`evidence/p2k-4-kernel-history/`).

### A history walk belongs to the cell it started in, and to the text it wrote

Two rules, one narrowing and one departure, both about when a walk ends.

**Typing ends it; the write the walk itself made does not.** `onTextChange` -> `reset()` guarded by
`_setByHistory` (`history.ts:313-319`). Here it is a *value* rather than a flag, because
`CodeCellField` reports an edit on a 150 ms debounce and the report can land after the next tap has
been made - comparing the text stays true whenever it arrives.

**Leaving the cell ends it — a narrowing.** The reference binds its editor once and keeps
`_hasSession` across a cell change (`:160-170`), so walking forward to the end there writes the
first cell's placeholder into whichever cell has the caret now. The placeholder belongs to a cell.

**An empty entry is applied — a departure.** `updateEditor` returns on `!content` (`:217`), and the
comment beside `back()` says what that guard is for: *"This shouldn't ever be undefined as setFilter
will always be run first"* (`:187`). It catches `''` only because the empty string is falsy in
JavaScript. Transcribed literally it would mean walking back out of an **empty** cell and never
being able to walk forward into it again - the commonest way into this feature, with no way back.
`NotebookHistoryCursor.current` is non-null by construction, so there is no null left to guard.

**Tapping past the oldest entry asks the kernel again**, every time, and that is kept rather than
optimised: `fetchBatch` runs on every overshoot there (`:182-184`), and a kernel that had nothing
more a moment ago may have run something since. Remembering otherwise would be inventing state the
reference does not keep - asserted in a test so a later reader does not "fix" it.

**One walk at a time, where the reference serialises nothing.** `executionHistory` is a websocket
round trip and suspends, and tapping back several entries quickly is how this feature is used -
without a lock both taps see no walk in progress, both open one, and the second overwrites the
first, so two taps move one entry. The reference's `back()` is equally `async` (`:179-189`) and
gets away with it because its editor reports synchronously and its history arrives over a
connection already open; neither is true here. Measured rather than feared: the test double
`yield()`s before answering, because the real gateway does, and the test fails without the lock.
**A double that never suspends hides every ordering question a class like this has**, which is the
general lesson.

---

### Code is drawn at one size, and it is 14sp rather than the reference's 13px

Four surfaces drew code and each drew it differently: the cell's source stated **no size at all**
(`codeSourceStyle` read `LocalTextStyle`, whose Material default is `TextStyle.Default`, so
`fontSize` resolved to Compose's `DefaultFontSize = 14.sp` and the line height to the font's own),
outputs and the folded-cell placeholder were `bodySmall` (12sp/16sp), and the completion popup
`bodyMedium` (14sp/20sp). JupyterLab has one `--jp-code-font-size` and one `--jp-code-line-height`
(`variables.css:196-197`) read by the editor, the input area, `.jp-RenderedText pre` and **both**
completer selectors (`codemirror/base.css:8`, `inputarea.css:37`, `rendermime/base.css:28`,
`completer/base.css:75,118`).

**The ratio is transcribed; the size is adapted.** 13px is a CSS pixel on a desktop, and `sp` is
neither a pixel nor fixed - it scales with the reader's own font setting - so the number does not
transfer, only the decision to have one. 14sp is `TypeScaleTokens.BodyMediumSize` and is what the
source already resolved to, so what changed is everything else joining it rather than the
most-read surface moving. The line height is then derived - 14 x 1.3077 = 18.3 -> 18sp - and
`CodeTextTest` re-derives it, so changing one without the other fails a test.

**This forbids** a code surface stating its own size: `theme/CodeText.kt` holds the numbers and
`codeTextStyle()` hands out the style. It also forbids reading `--jp-code-font-size` as covering
*all* monospace - see the two exceptions below.

**Not the symbol row**, which stays `bodyLarge` at 16sp. Its keys are monospace for the shape of
`->` and `Tab`, not because they are source being read; the reference has no such row, it being a
phone adaptation; and they are captions on touch targets, where 14sp would make the surface most
used one-handed harder to read for nothing gained. Stated here so it is not re-derived as an
oversight.

**Not rendered markdown's code either, and that is the reference's own rule rather than ours.**
`packages/rendermime/style/base.css` names `.jp-RenderedHTMLCommon pre` twice - `:24-29` gives it
`--jp-code-font-size`, `:407-417` gives it the code family, the code line height and
**`font-size: inherit`** - and the two selectors tie on specificity, so the later one wins: inside
rendered markdown, code inherits the *prose* size. The earlier rule still governs
`.jp-RenderedText pre`, which is stream output and is not rendered markdown. P2l-3b implements the
second rule as `richTextCodeStyle()`, **derived** from `richTextProseStyle()` rather than written
as a number, because prose is `bodyMedium` at 14sp and [CodeText.size] is 14sp - the two rules
agree today, and only the derivation says which one is meant.

**This also forbids naming `FontFamily.Monospace` outside `ui/editor/EditorTheme.kt`**, which is
where all three styles are built - `codeTextStyle`, `richTextCodeStyle` and `symbolKeyStyle` - and
where the symbol row's exception is therefore stated beside the rule.
`scripts/check_monospace_home.py` enforces it. The rule is about where the font may be *named*
rather than about the size: a size is easy to grep for and keep in step, while a new surface
reaching for the font directly is how four sizes happened in the first place, one reasonable commit
at a time.

### A touch target is reserved space, not expanded bounds

Compose expands the *hit area* of any clickable node out to 48dp at the input layer:
`SemanticsNode.touchBoundsInRoot` takes the expanded path whenever the node carries an `OnClick`
action, and `NodeCoordinator` pads the measured bounds by `calculateMinimumTouchTargetPadding` -
`maxOf(0f, (48dp - measured) / 2)`. So an assertion asking `touchBoundsInRoot` computes
`max(measured, 48dp) >= 48dp` and **cannot fail**. `assertIsAtLeastMinimumTouchTarget` had been
asking exactly that since P2b, and its docstring claimed the opposite - that a bare `clickable`
*"would have a real 24dp target and nothing but this to notice"*. `HiddenCellsRowTest` shipped
green against a 40dp row on the strength of it, and P2l-4 was planned around a gate that could not
go red.

The expansion is real but **conditional**, which is why the reservation is the thing that matters.
`NodeCoordinator.outOfBoundsHit`: *"hit in this node's … minimum touch target bounds overlaps with
a direct hit in the other node. The node with direct hit will get the event."* A folded cell sits
in a `LazyColumn` between a cell header and an editor; both take direct hits, so the borrowed half
is the half that is missing when a thumb lands near the edge. Material says the same of its own
modifier: *"This modifier is not needed for touch target expansion to happen. It only affects
layout, to make sure there is adequate space for touch target expansion."*

**This forbids** measuring a touch minimum with `touchBoundsInRoot`, and building a tap target
from a bare `clickable` without `minimumInteractiveComponentSize()`. The measurement is
`assertReservesMinimumTouchTarget`, over the layout bound - and it is **only** for a control that
is its own layout node. On an `IconButton` or a `TextButton` it reads 40dp against a control that
is correct, because the merged semantics node is the `clickable` *inside*
`.size(IconButtonDefaults.smallContainerSize())` while the 48dp reservation is the
`minimumInteractiveComponentSize()` node outside it, which carries no semantics
(`material3/IconButton.kt`, `IconButtonImpl`). Those keep the weaker assertion, which still says
the node is clickable and so does get the expansion.

Measured on four geometries (`evidence/p2l-4-touch-targets/`): 32dp and 40dp before, 48dp after,
at densities 160, 240 and 420.

### A cell at rest carries no mark, and this reverses what the code argued (P2m-1)

**What was there, and the argument for it.** `cellStripe` painted `stripeResting` - Gray5 dark,
Gray8 light - down the left of every cell that was neither active nor failed, and both
`CellStripe.kt` and `CellStripeTest` argued for it in the same words: *"**Every cell gets one**,
not only the selected one and not only code cells"*, on the grounds that IntelliJ draws a
*"vertical gray line for markdown cells in the gutter"* (`markerRenderers/README.txt`) and that on
touch it does more work than on a desktop - no hover, so it is the only thing always on screen
saying where one cell ends and the next begins.

**What the reference actually says, read rather than recalled.** Three rules, and they agree:

- `.jp-Collapser { background: transparent; }` (`packages/cells/style/collapser.css:6-15`)
- `.jp-Cell.jp-mod-active .jp-Collapser { background: var(--jp-brand-color1); }`
  (`packages/notebook/style/base.css:108-110`) - the **active** cell alone
- `.jp-Cell { background: transparent; border: none; }` (`packages/cells/style/widget.css:18-24`)

So JupyterLab answers *"where does this cell end"* with the **input area's own border**, which it
draws unconditionally - `1px solid var(--jp-cell-editor-border-color)`
(`packages/cells/style/inputarea.css:18-26`), going to the brand colour plus
`--jp-cell-editor-box-shadow` in edit mode (`base.css:160-164`). This app had it exactly backwards
in both halves: a gutter mark the reference does not draw, and no border where the reference always
has one. **The touch argument was answered by the wrong control**, not wrong on its own terms - and
the border is the better answer for it, because it bounds the whole editable region rather than
marking one edge of it.

**This forbids** re-adding a resting stripe, and it makes P2m-2 load-bearing rather than cosmetic:
between the two commits a rendered markdown cell has no chrome at all, and that is the reference's
end state as well as the intermediate one - a rendered cell has no editor box in either app.

**Two things survive the reversal, both named so they are not read as leftovers:**

- **The widths stay IntelliJ's while the colour rule becomes JupyterLab's.** 4dp command and 2dp
  edit are `DefaultNotebookEditorAppearanceSizes`; the reference's collapser is 8px
  (`--jp-cell-collapser-width`) because it is a **button** - `.jp-Collapser` collapses the cell on
  click, and it has hover and active rules to prove it. Ours is a marker nobody taps, so it is
  sized as a marker and exempt from the 48dp minimum every button beside it takes. That hybrid is
  deliberate; it is recorded here so the next reader does not "fix" one half of it.
- **The error stripe is this app's own and it stays.** JupyterLab has no per-cell failure mark and
  IntelliJ tints a gutter icon that only appears on hover, which is no use on a touch screen. It is
  now the **only** non-active mark on screen, which is the job its docstring always claimed -
  findable while scrolling - and could not do while every resting cell carried a grey line too.

**One measurement corrected in passing, and the screenshot is what said so.**
`.maestro/u3-cell-chrome.yaml` labelled its first frame *"Nothing selected yet: every stripe is at
rest"*. The committed screenshot shows the markdown cell blue and the two code cells grey:
`EditorUiState.kt` opens a notebook with `selectedCellId = opened.first().id`, matching the
reference's `activeCellIndex = 0`. The comment had been false since the flow was written and was
invisible while every cell had a stripe of some colour; P2m-1 makes that frame one blue line and
nothing else, so the label is corrected with the behaviour rather than after it.

### The editor's border is the cell's boundary, and 3:1 is the wrong floor for it (P2m-2)

The other half of P2m, and the half that made P2m-1 safe: `.jp-InputArea-editor` carries
`border: var(--jp-border-width) solid var(--jp-cell-editor-border-color)` **unconditionally**
(`packages/cells/style/inputarea.css:18-26`), and the cell being typed into swaps that colour for
`--jp-cell-editor-active-border-color` = `--jp-brand-color1`
(`packages/notebook/style/base.css:160-164`). One factory, `Modifier.cellEditorBox(editing)`,
draws it for all three fields.

**The plan's floor did not survive being measured.** It asked for the WCAG 1.4.11 non-text floor
of 3:1 against `cellBackground`, decided by whichever IntelliJ ramp step reached it. Measured, no
step that reads as a border does:

| Candidate | On the cell |
|---|---|
| Gray5 `#4E5157` dark - IntelliJ's `Component.borderColor` | **1.74:1** |
| Gray7 `#6F737A` dark | 2.90:1 |
| Gray9 `#C9CCD6` light - IntelliJ's `Component.borderColor` | **1.51:1** |
| Gray7 `#818594` light | 3.46:1 |
| JupyterLab's own `#616161` on its own dark surface | 2.60:1 |
| JupyterLab's own `#E0E0E0` on its own light surface | **1.21:1** |

**Neither reference clears 3:1 either**, and the value that would is a hard grey rule around every
cell in the document - heavier than anything either app draws, and noise once a notebook has
twenty cells. So the floor moves to the requirement that actually applies. 1.4.11 asks for
contrast in what is *required to identify* a component and its **states**; the state here is
"which cell am I typing in", and the brand border that says so measures **3.23:1 dark and 4.03:1
light** - held to 3:1 in `EditorPaletteTest` and passing. What the resting border owes is weaker
and assertable: that it strengthens the boundary the fill step alone makes (1.74 against 1.19 dark,
1.51 against 1.06 light).

**The grey that was wrong in the gutter is right on the box.** Dark's border is Gray5 `#4E5157` -
the exact value P2m-1 deleted as `stripeResting`. Same ramp step, different role, and that is the
point of the pair: the reference bounds a cell with its box, not with a mark beside it.

**Three departures, each measured rather than assumed:**

- **No inner glow.** The reference adds `inset 0 0 2px var(--md-blue-300)` in edit mode. Compose
  has no inset shadow, and elevation is not its substitute: an elevation casts *outward* and would
  lift the field off the cell, which the reference never does. Same shape as
  `EditorPalette.currentSearchMatch`, which declined IntelliJ's outline for the same
  not-expressible-here reason and took a contrast answer instead.
- **The corner stays `shapes.small` where the reference squares it** (`border-radius: 0`, same
  rule). A cell's outputs, its collapsed placeholder and its completion popup are all drawn on that
  radius; squaring only the editor would make it the odd panel in its own cell.
- **The active *background* swap is declined, and the numbers are why.** The reference also moves
  the active editor to `--jp-cell-editor-active-background` (`layout-color1` to `layout-color0`,
  a real step in its palette). The equivalent pair here measures **1.19:1 dark and 1.06:1 light**,
  so copying the rule would add a branch nobody can see. Every syntax colour was checked against
  the page first and all of them clear their floor there - the reason for declining is that it is
  invisible, not that it is unsafe.

**And it folded a divergence nothing would have caught.** `MarkdownCellField` drew its panel with
`colorScheme.surfaceVariant` while the code and raw fields used `cellBackground` - one role, two
colours, in three hand-written copies. `Modifier.border` strokes inside the layout bounds, so none
of this moved the gutter, which is P2n's subject; `CellEditorBoxTest` asserts that against the
pre-P2m-2 shape rather than against a number.


### The prompt is the reference's, and a non-code cell has none (P2n)

`CellHeader` drew the words **"markdown"** and **"raw"** where the reference draws nothing, and
`[7]` where it draws `[7]:`. Both were read out of the reference rather than remembered:

- `InputPrompt`'s setter is `this.node.textContent = \`[${'$'}{value || ' '}]:\``
  (`packages/cells/src/inputarea.ts:283-290`). The trailing colon is part of the notation, and
  `[ ]:` is what an unrun cell shows because the empty string falls through `||` to a space.
- `_setPrompt` has **exactly one caller**, `CodeCell._updatePrompt`
  (`packages/cells/src/widget.ts:1693-1700`). `MarkdownCell` and `RawCell` never call it, so their
  prompt node is drawn and left textless.

**The invented word cost more than a word.** It was the only thing in front of a heading cell's
collapser, so the collapser pushed it: two markdown cells in one frame started 48dp apart, measured
on device at `evidence/p2n-gutter/before/gutter.txt` (`RAGGED by 48dp`) and visible since P2h in
`git show b4f157b:evidence/p2h-3-table-of-contents-pane/02-the-pane.png` - P2o-2 renamed that flow
to `p2o-2-side-panel`, so the frame is in git history rather than in the tree. With the word gone
the collapser takes the
leading position instead, which is where the reference draws it anyway - `maybeCreateCollapseButton`
appends it to the *prompt node* at `position: absolute; right: 0`
(`packages/cells/src/widget.ts:2386-2408`, `packages/cells/style/widget.css:154-168`), directly
beside the heading. Every header's leading mark is now at one x: `aligned` in
`evidence/p2n-gutter/after/gutter.txt` and `after-tablet/`, on both devices.

**A reserved gutter was tried first and is refused.** The plan's row asked for two fixed-width
slots, citing `flex: 0 0` on `.jp-Collapser` and `.jp-InputPrompt`
(`packages/cells/style/inputarea.css:29-31`). That is the **desktop** rule. At `width <= 760px` the
reference overrides it - `flex: 0 0 auto; text-align: left` (`:76-88`) - and this app's header has
been that column layout at every width since Step 4. So a reserved column is something neither
reference draws at the widths this app runs at; built, it indented every header 48dp and detached
the prompt from the editor it labels (the frame is in this repository's history, not in
`evidence/`). Two 48dp slots also left about 10dp of headroom at 320dp once a cell showed its
duration, which `CellHeaderTest.theMenuButtonStaysOnScreenAt320dp` now pins.

**What it costs, and it is named rather than hidden.** A markdown or raw cell's header now holds
only its Run control and its `⋮`, so nothing on screen says what kind it is. That is the
reference's own position, and the reference answers it elsewhere: the notebook toolbar carries a
`cellType` item at rank 40 (`packages/notebook-extension/schema/panel.json`) showing and changing
the active cell's type. This app has no such control, and adding one is filed rather than smuggled
in here.

**Three things this moved that the plan did not name:**

- **28 prompt assertions across 13 flows needed the colon**, and `assertNotVisible` was the risk:
  `\[ \]` no longer matches `[ ]:`, so an un-updated negative assertion would have gone on
  passing while asserting nothing.
- **Three flows anchored a tap on the invented word.** `a1c`, `a4` and `a5` all seed
  `a1c-markdown-render.ipynb`, whose markdown cell is `# Analysis` - a heading. Its collapser is a
  *better* anchor than the word was: it names that one cell rather than every markdown cell on
  screen.
- **Two flows proved a cell's kind by reading the word.** They read it off the cell's own shape
  now: a raw cell is the one kind with no Run control at all (`cellexecutor.ts:36-141`), and a cell
  that has become markdown is one that has lost its bracket - asserted `below` the neighbouring
  code cell's source, since that cell keeps its own.

### The document's name has a row of its own (P2q)

Sharing the app bar with undo, redo, search and the `⋮`, the title had **64dp of the 175dp**
`Heading Sections.ipynb` needs at 320dp - measured, as
`EditorTopBarTest.theWholeFileNameFitsAtTheNarrowestWidth`'s failure message before the change -
and truncated to `Gutt…` in every phone screenshot this repository holds.

**The reference separates the two, and this app is in the mode where it does.** `LabShell.mode`'s
setter shows a title panel above the document when the mode is `single-document`
(`packages/application/src/shell.ts:690-705`), and `#jp-title-panel` holds the document's name and
nothing else - an `<input>` at `width: 100%`, transparent and borderless, in a strip whose
`min-height` is 28px (`packages/application/style/titlepanel.css:10-37`). The document's own
toolbar sits below it. One document at a time is this app's only shape, so this is its arrangement
rather than a choice between two.

**And the reference's toolbar carries none of what was crowding ours**: `save`, `insert`, cut,
copy, paste, `run`, `interrupt`, `restart`, `restart-and-run`, `cellType`, `kernelName`,
`executionProgress` (`packages/notebook-extension/schema/panel.json`). Undo, redo and search are
Edit-menu commands there. Demoting ours to the `⋮` was the cheaper answer in height and is
**declined**: the title row is what the reference actually draws, and P2a's own evidence is that a
control nobody can see is a control nobody uses.

**Four things measured rather than assumed:**

| | |
|---|---|
| Total height | 48dp title row + 64dp action row = **112dp**, which is `MediumTopAppBar`'s container height - Material's own two-row bar, same split. Not a stack that happens to add up. |
| Title size | `titleMedium` (16sp). The reference asks for `calc(--jp-ui-font-size1 * 18 / 13)` = 18px on a 13px base; stating a Material role rather than computing a size is what P2l-1 settled. **Costs the weight**: the reference says `font-weight: normal`, `titleMedium` is 500. |
| Strip height | Material's interactive minimum, not the reference's 28px: unlike an `<input>` reached with a mouse, the whole strip is a touch target. |
| Inset | On the `Column`, and `TopAppBar` is passed `WindowInsets(0)`. |

**That last row is a defect the screenshot caught and no test could.** `TopAppBar` consumes
`TopAppBarDefaults.windowInsets` itself. Left to do so under a strip now above it, it padded the
*second* row down by the status bar's height while the *first* row drew underneath the clock. Every
assertion in the suite was green: the test environment has no system insets, so nothing
instrumented can see it. It is the third defect in this project's history that was green against
every assertion and wrong in the picture, and the frame is in `evidence/p2n-gutter/` history.

**The row is not free, and it does not pretend to be (P2q-2).** Measured at 320dp it cost **48dp
of notebook** - enough to put a fourth cell's source under the bottom bar - and four flows caught
it at once. The reference does not have this problem because its chrome is proportionally tiny: a
28px strip over a ~28px toolbar. Material's own answer for a two-row bar is that the second row
collapses with the content, which is what `MediumTopAppBar` does for a title *below* the actions;
`titleCollapseFraction` is the same rule for one above them. The name is there at the top of the
document, where you look for it, and the 48dp is the notebook's from the first scroll on. Measured
on device: the action row moves from y=92 to y=64 as the strip goes.

**Driven from the list's own scroll, not from a `nestedScroll` connection.** `EditorScreen` already
holds the `LazyListState` the cells are drawn from, so the fraction is derived from what is on
screen rather than from a second channel that could disagree with it - and the whole rule is a pure
function with a JVM test, rather than a `TopAppBarScrollBehavior` threaded through three layers.

**Two of the four flows were not this defect at all, and finding that out cost a re-run each.**
`p2i-4-next-previous` and `p2i-6-replace` tap a file in the explorer immediately after
`tapOn: "Back to notebooks"`. `tapOn` locates a node and then taps where it *was*: fired while the
document is still on screen it can read the title strip - which carries the same file name - and
tap those coordinates once the explorer has replaced it, landing on the header. A manual tap at the
same point opened the notebook every time, which is what said the app was right and the flow was
early. Three flows wait for `Workspace` now, the section only the explorer draws. It was already
failing intermittently before this phase; P2q made it deterministic.

**Left open, and named so nobody re-derives it.** The explorer pane's own header draws "Files" far
larger than a notebook's title now sits beside it on a tablet. Whether the two headers should agree
is a question about the *shell*, not about this bar, and it belongs with P2o's rail.

### The heading caret belongs to the heading, not to a strip above it (P2n-3)

`CellHeader` drew the chevron as one of its own controls, a row above the heading and beside the
Run button, with nothing on screen connecting it to what it folds
(`evidence/p2f-1-heading-collapse/01-expanded.png`, before this). The reference does not have it
there. `maybeCreateCollapseButton` appends the caret to the cell's **prompt node**
(`packages/cells/src/widget.ts:2386-2408`) and pins it at `position: absolute; right: 0; top: 0;
bottom: 0` (`packages/cells/style/widget.css:154-168`) - the right edge of the 64px prompt column,
which is immediately left of the content. It is a disclosure triangle on the heading.

It is beside the **source** as well as beside the rendered heading, because the prompt node is
there in both states: `InputArea` builds it once in its constructor and `renderInput` hides only
the *editor* (`packages/cells/src/inputarea.ts:53-56,111-119`).

**The vertical placement is derived, and by eye it was 10dp wrong.** The reference spends six rules
on it - one per level, each setting `background-position-y: calc(0.3 * <that level's font size>)`
for a 25px caret (`widget.css:184-213`) - which for `h1` at 29px in a ~43px line box puts the icon
centre at 21.2px against a line centre of 21.5px. Top-aligned inside its own 48dp button, ours sat
at **316 against the heading's 306**, measured from a `uiautomator` dump. A box exactly one heading
line tall, reading the same `headingStyle(level)` the heading is drawn in, lands it at 306 - and
tracks the level the way the reference's six rules do. `MarkdownCellTest` pins all six.

**IntelliJ is empty for this, and it was checked so nobody searches it again.** Everything under
`notebooks/` that collapses collapses *outputs* - `CollapsingComponent`,
`NotebookOutputCollapseAllInSelectedCellsAction`. Markdown heading folding lives in its
closed-source Jupyter plugin, the same gap that made `EXECUTION_TIME_COLOR`'s placement unreadable.

**What follows for the header strip, which is the other half of the ask.** With the caret gone, a
heading cell's strip is a markdown cell's strip: Run and `⋮`, nothing else. That is as close to the
reference as this app gets - JupyterLab has no per-cell strip at all, its cell actions being a
right-click context menu and its Run being a toolbar command - and the two controls stay because
touch has neither a right-click nor a keyboard, which `CellHeaderTest` has argued since Step 4b.

**Two costs, both named:**

- **A heading's text is indented by the caret and a prose cell's is not.** The reference indents
  *everything* by the prompt column so the two align; at 320dp that column is 15% of the width and
  the reference itself abandons it below 760px (`inputarea.css:76-88`). So the caret pushes only
  the heading, which is what a disclosure triangle does everywhere else.
- **The caret's 48dp press area overflows its one-line box** by design, so a short heading keeps a
  full-size target. It overlaps the neighbouring cells' rows, where direct hits win
  (`NodeCoordinator.outOfBoundsHit`), which is the rule P2l-4 established.

### The side panel reads a model the document publishes, because it cannot reach it (P2o-1)

The shell's side panel lives in the **list pane** and a notebook's headings are derived in the
**detail pane's** `EditorViewModel`. Every `NavEntry` owns its own `ViewModelStore`
(`docs/architecture.md` §4), so the panel can reach neither that ViewModel nor the editor's
`LazyListState` - the same wall that ruled out `ListDetailSceneStrategy.extraPane()` for P2h.

**The reference has the same seam and the same answer, and it is a settable, nullable model.**
`TableOfContentsPanel.model: TableOfContents.Model | null` is assigned from outside
(`packages/toc/src/panel.ts:41-60`), by an extension listening to `labShell.currentChanged`:
`toc.model = model` (`packages/toc-extension/src/index.ts:316,353`). `OpenDocumentOutline` is that
property, and `DocumentOutline` is that model reduced to what a panel actually draws with - the
title the header takes (`panel.ts:77-80`), the headings, the numbering, and the jump.

**Nothing is derived twice, which is the constraint the whole sub-step was written around.**
`EditorScreen` publishes the list it already computes for its own cells (`tableOfContentsHeadings`),
so `EditorViewModel` stays the one place headings, folds and numbering are worked out. A panel that
re-read the notebook would be a second answer to "what is in this document", and the two would
disagree the moment a cell was edited.

**A table of contents cannot outlive its document.** The reference disposes the model with the
widget - `widget.disposed.connect(() => { model?.dispose(); })` (`index.ts:341-343`) - and ours
clears instead, which is the same observable outcome without leaving a dead object on the panel.
The reference's own `onConnect` returns early when there is no current widget (`index.ts:331-334`),
so its panel keeps a *disposed* model where ours keeps `null`; stated as a departure because it is
one, and because `null` is what makes "is anything open" a single check for the placeholder P2o-2
draws.

**Remembered by `MainNavigation`, not owned by `AppContainer`,** and the lifetime is the whole
argument. Every other piece of app-level state here is on the container (`ActiveCellRuns`), but a
process-scoped holder survives the activity relaunch that a rotation is - still carrying a `jumpTo`
closed over a disposed composition's `LazyListState`. It is remembered by the composition that owns
both panes, and it is passed in rather than built inside, so a test can read what a real editor
entry published.

**Two effects, not one.** The publish is keyed on what it publishes and the clear is a
`DisposableEffect` of its own, because folding the clear into the publish's `onDispose` would empty
and refill the panel on every keystroke inside a heading.

**Named as a *document*'s outline rather than a notebook's**, because the reference's own
placeholder is *"The table of contents shows headings in notebooks and supported files"*
(`panel.ts:32-36`) and P5e feeds `.py` document symbols into the same panel.

**Picking the panel already showing collapses the area**, which is `SidePanel?.chosen`'s only rule
and the reference's own: `collapse()` sets `this._sideBar.currentTitle = null` and, in `'side'`
mode, *"only deselects the active tab, leaving the activity bar visible"*
(`packages/application/src/shell.ts:2383-2394`) - what `application:toggle-left-area` drives
(`packages/application-extension/src/index.tsx:514-534`). A pure function rather than a holder:
`SidePanel?` round-trips through `rememberSaveable` unaided, so the state needs no `Saver` and the
one rule that is not Compose's own gets a JVM test.

**One cost the plan did not name.** The lifetime assertion was checked against a build with the
`DisposableEffect` removed before it was believed - `expected null, but was:
<com.jupydroid.app.DocumentOutline@...>`. This project has shipped three assertions that could not
fail (`HiddenCellsRowTest`'s touch target, the dimension gate's `rglob`, `ktlintCheck` itself), and
a test whose subject is a teardown is exactly the shape that passes for the wrong reason.

### The table of contents is a sidebar panel of the shell, not a column in the notebook (P2o-2)

`docs/PLAN.md` measured the gap: *"the reference has one side area; this app has two half-answers."*
JupyterLab has a single left sidebar holding File Browser (rank 100) and Table of Contents (rank
400) among others; this app drew the ToC on the **right of the notebook** and gave the explorer a
pane with no panel chrome. `TableOfContentsPane`, `TocPresentation` and `tocPresentationFor` are
gone, and the rule they encoded - "is the editor 840dp wide" - is replaced by "does the shell have a
side area".

**The activity bar is beside the whole shell, not inside the side area, and `docs/PLAN.md` had it
wrong.** That row asked for a `NavigationRail` "at the left edge of the list pane". Reading
`shell.ts` corrected it: `hboxPanel` holds `leftHandler.sideBar` next to the split that contains
*both* the panel and the document area (`packages/application/src/shell.ts:468-477`). The
correction is load-bearing rather than cosmetic - it is what lets the area collapse to the bar
alone while the document keeps the width.

**A collapsed side area is a one-pane layout**, which is `collapseLeft` said in this app's own
terms: `collapse()` sets `currentTitle = null` and, in `'side'` mode, *"only deselects the active
tab, leaving the activity bar visible"* (`shell.ts:2383-2394`). So the collapse is spelled on the
`PaneScaffoldDirective` rather than inside the pane, and the notebook actually gains the 360dp -
measured on the tablet at `evidence/p2o-2-side-panel/06-collapsed-to-the-rail-alone.png`.

**`toc:show-panel` is not a toggle, so its row does not name two states.** Every other row in the
View menu flips its label, which is this app's convention because Android's accessibility tree keeps
a `DropdownMenuItem`'s toggled state on a node carrying no label. This row departs from it because
the *command* departs from it: `execute` is `labShell.activateById(toc.id)` and there is no
`isToggled` (`packages/toc-extension/src/index.ts:161-172`). `HIDE_TABLE_OF_CONTENTS` is gone with
the pane whose close button also carried it.

**One placeholder for both empty states.** `if (!this.model || this.model.headings.length === 0)`
(`packages/toc/src/treeview.tsx:31-40`) - so "nothing is open" and "nothing has headings" draw the
same headline and the same line of explanation (`packages/toc/src/panel.ts:32-36`), in this app's
sentence case. The wording is about *documents* rather than notebooks because that is what the
reference promises and what P5e delivers for `.py` files.

**The panel is named for the document, which also answers the question P2q left open.**
`this._title.setTitle(this._model?.title ?? this._trans.__('Table of Contents'))`
(`panel.ts:77-80`), rendered into a `TopAppBar` - the same chrome `ExplorerScreen`'s `FilesBar`
has. P2q recorded that "the explorer pane's own header draws 'Files' far larger than a notebook's
title now sits beside it" and said the answer belonged with this rail. It does, and the answer is
that both panels wear the same bar. The cost is that on a tablet the document's name is on screen
twice, in the panel's header and in the notebook's own title row; the reference has the same
duplication with a tab in place of the title row, and P2q-2's collapse takes ours back on the first
scroll.

**Three defects the assertions could not see, and the screenshot did.** The flow went green on
every line of the first run and the frame showed: the panel's header drawn *on top of the status
bar* (a bare `Column` outside any `Scaffold` gets no window inset), the rail's "Table of contents"
label spilling out of the rail and across the panel beside it, and no edge at all between the bar
and what it was showing. Fixed by the `Scaffold` above, by an **icon-only** rail - which is the
reference's own 32px strip (`packages/application/style/sidepanel.css:11-14`), with the name on the
item for a screen reader and a flow - and by a `VerticalDivider`. This is the fourth time in this
project's history that a green suite was wrong in the picture.

**`TocFolds` moved into the panel and is keyed on the document.** It used to be `remember`ed by
`EditorScreen`, which died with the notebook; the panel outlives every notebook, so a fold set
would otherwise carry from one document to the next. Keyed on the title rather than on the outline
object, which is republished on every edit to a heading. A rename therefore resets the ToC's folds,
which is a consequence rather than a rule and is small enough to state rather than engineer around.

**What the rail costs and what it does not.** It is drawn only where there is room for two panes:
on a 320dp phone a rail would spend a quarter of the screen on chrome, and the notebook's own menu
still opens the overlay there, which is the narrow adaptation this app has had since P2h. The bar
on compact is P2o-4.

### 840dp stays, and the rail is what settles it (P2o-3)

`docs/PLAN.md` asked for the two-pane threshold to be re-derived against the activity bar rather
than kept on faith, and said the outcome was to be recorded whichever way it landed. Measured on
device at three widths (`evidence/p2o-3-two-pane-threshold/`, `scripts/read_pane_widths.py`):

| Window | Columns |
|---|---|
| 1280dp | rail **80.0dp**, divider **1.3dp**, list pane **360.0dp**, spacer **24.0dp**, document **814.7dp** |
| 800dp | one column, 800.0dp |
| 320dp | one column, 320.0dp |

Everything left of the document is fixed, so the document absorbs any change in window width. In a
**800dp** window two panes would leave it **334.7dp** - narrower than the 360dp the single-pane
layout was designed for, which is the floor this threshold has always been argued from. So the
tablet in portrait keeps one pane, and 840dp stays.

**The rail did change the arithmetic, in the direction that keeps the number.** An 840dp window
used to leave the document 478.7dp; it now leaves **374.7dp**. The bar and the spacer between the
panes cost 104dp, which is almost exactly the headroom that would have allowed lowering the
threshold. 840dp is now 14.7dp above the floor rather than 118dp above it - worth knowing before
anything else is put to the left of the document.

**And the measurement found a rule this app had conflated.** The rail is drawn when there are
**two panes**; Material draws a `NavigationSuiteScaffold` rail from the *medium* window class
(600dp) upward, and JupyterLab's activity bar is not a function of pane count at all. So an 800dp
tablet in portrait has no activity bar and reaches the table of contents only through the
notebook's overlay. That is the same question P2o-4 exists for, arriving at a second width: the
problem is not "the compact window" but **every window the shell draws as one pane**, where the
`Explorer` entry hosting the side panels is not composed while a notebook is open. P2o-4 is costed
against both widths rather than against 320dp alone.

### One pane grows no navigation bar, and two destinations is why (P2o-4, **reversed by P2y**)

`docs/PLAN.md` asked for "the bar on compact, the same two panels", with a named risk that would
decide it. P2o-3 sharpened the question from "compact" to **every window the shell draws as one
pane** - the 320dp phone *and* the tablet in portrait at 800dp. Neither gets a bar. Three things
say so, and the first is not ours.

**Two destinations is below Material's own floor.** Android's guidance for `NavigationBar` is
*"three to five destinations of equal importance"*
(`developer.android.com/develop/ui/compose/components/navigation-bar`). This app has two panels, and
at one pane only one of them is a destination at all: Files is a screen you navigate to, and the
table of contents is a surface over the document you are already in. A bar whose two items are one
navigation and one bottom sheet is not "destinations of equal importance".

**The named risk does not bite, and the flow says so at both widths.** The risk was that the ToC
would be "reachable only after leaving the document". It is not, and never was: `toc:show-panel` in
the notebook's own menu opens the overlay P2h built - a `ModalBottomSheet` at 320dp and a
`DropdownMenu` at 800dp, which is `CellActionPresentation`'s rule applied to one list of headings.
`p2o-4-one-pane-side-panels` drives both entry points at both one-pane widths.

**And a bar's cost is measured rather than argued.** The notebook's cell list is `y 136..536` =
**400.0dp** at 320×640dp and `y 142..1152` = **1010.0dp** at 800×1280dp. An 80dp bar is **20% of
the notebook** on the phone - and a cell block there measures 96-110dp, so roughly three-quarters of
a cell of the four on screen. P2q-2 gave the notebook back **48dp** at that width because it was
enough to push a fourth cell's source under the bottom bar, and four flows caught it; spending 80dp
to add a second path to two things that already have one is that trade in reverse. The modal drawer
`docs/PLAN.md` named as the fallback is declined for the same reason: its trigger would be 48dp of a
320dp top row that P2q already had to split in two to fit a filename into.

**What would reopen it is a third destination**, and `docs/PLAN.md` already names it: P6d's Launcher
is *"the second top-level destination a navigation rail has been waiting for since U7"*. Files +
Launcher + Table of contents is three, which is the floor, and the Launcher is a destination in the
way the ToC is not. Re-cost it then, against these numbers.

**Reversed by P2y, and not by the reason this entry predicted.** What reopened it was not a third
destination but a **second one with no route at all**: P2x-2 added workspace search to the activity
bar, and unlike the table of contents it had no overlay to reach it by, so at one pane it could not
be reached at all. The count is still two and still below Material's floor, which is now recorded
rather than argued from. Of the three reasons above, the second and third **survive unchanged** -
the ToC is still reachable without leaving the document and is still not on the bar, and the 80dp
cost is still real, which is exactly why P2y draws the bar over the *panel* and never over a
document. See "The bar is drawn wherever the shell draws one pane" and the three entries after it.

**A harness limitation this named rather than fixed.** `WIDE WINDOW` means >= 600dp to the sharder,
but `p2o-2-side-panel` and `p2h-4-table-of-contents-folds` need **two panes**, which is >= 840dp. On
the devices this project drives the two are the same set, so nothing is misplaced today; attach a
device between 600 and 840dp and those two flows would be placed on it and **fail** - loudly, which
is the failure mode `driver-rules.sh` exists to prefer. A `TWO PANES` rule would fix it and is not
built, because there is no device to exercise it.

### The bar names the active cell's type, and the reference's `-` is not modelled (P2r-1)

P2n removed this app's invented `markdown` and `raw` prompt words, on the reading that
`_setPrompt`'s only caller is `CodeCell._updatePrompt` (`packages/cells/src/widget.ts:1693-1700`) -
a non-code cell has no prompt in the reference. That was right and it left a hole: **nothing named
the active cell's type at all**. The reference's own answer is not the prompt but the `cellType`
item on the notebook toolbar at rank 40 (`packages/notebook-extension/schema/panel.json`), an
`HTMLSelect` whose value is `notebook.activeCell.model.type`
(`packages/notebook/src/default-toolbar.tsx:332-397`). It is deliberately not a *cell* toolbar item
there - that toolbar's six entries are duplicate, move, insert and delete
(`packages/cell-toolbar-extension/schema/plugin.json`).

**The reference's `-` option is declined, and that is a reading of this app rather than a
simplification of the reference.** `-` is what the select shows when a cell in the *selection* has a
different type. Here `EditorTopBars` swaps in `SelectionTopBar` the moment `anchorCellId` is
non-null, so whenever this control is on screen `selectedCellIds` is exactly one cell
(`CellRangeSelection.kt`). A `-` branch would be unreachable by any test. The marked-run case is not
lost: the reference's own commands are on the other bar's overflow already
(*"Change cells to markdown"*, `cellActionItems`).

**Where it goes was measured, not chosen.** Free width in the action row between the navigation icon
and the first action, off `evidence/p2o-3-two-pane-threshold/<w>/hierarchy.xml`: **72dp at 320dp**,
**552dp at 800dp**, ~567dp at 1280dp. Instrumented on both devices, the widest label the control can
draw - "Markdown" at `labelLarge` - wants **73.3dp**, *before* its caret and the button's padding.
So the phone cannot have it and both of the tablet's orientations can with room to spare, and
`cellTypeOnBar` states the medium boundary the two neighbouring rules already state
(`cellActionPresentationFor`, `editorTopBarVisible`). What happens at 320dp is P2r-2: a
`ReactiveToolbar` *moves* an item that does not fit into its popup opener
(`packages/ui-components/src/components/toolbar.tsx:570-700`), and this app's popup opener is
`NotebookActionsMenu`.

**It sits in `TopAppBar`'s title slot**, which is that free space, and that is also rank order: the
reference lays its toolbar out left to right and `cellType` at 40 is well before the `spacer` at
100. The document's *name* is not there - P2q gave it the row above, which is the reference's
single-document mode (`application/style/titlepanel.css:10-37`).

**Two things the screenshot decided that no assertion would have.** A `TextButton` draws its label
in the brand colour by default, which made the control the only coloured thing in the row - and this
app spends brand blue on exactly two things, the active cell's collapser (P2m-1) and the editor
being typed into (P2m-2). Neutral now, which is also the reference's: its select is drawn in
`--jp-ui-font-color0` like the rest of its toolbar. And a `TextButton`'s own minimum is **40dp**, so
Material's 48dp target is asked for rather than inherited - measured with `touchBoundsInRoot`, since
`minimumInteractiveComponentSize` expands what accepts a press while the ripple stays the
component's size, the distinction `CellTouchTargetTest` was written to record.

**The state is in the control's *name*, not only in its text.** A Compose `contentDescription`
replaces the text under it on Android, so a fixed *"Cell type"* would leave TalkBack and every flow
unable to read which type is showing. `cellTypeDescription` folds the value in - `Cell type Markdown`
- which is the rule `SHOW_LINE_NUMBERS` already states for menu rows, applied to a control. Its
picker's rows are `Cell type picker Markdown`, spelled so neither string contains the other:
`cellMenuDescription` records that collision between "Cell actions" and "Cell menu Delete".

**A finding it did not fix.** Comparing frames 03 and 04 of `evidence/p2r-cell-type-on-the-bar/`,
the same three lines are drawn in the code font as a raw cell and in the **prose** font once the
cell is markdown - `markdownSourceStyle` is `bodyMedium` where `codeSourceStyle` is the code family.
The reference has no such split: `.cm-editor` takes `--jp-code-font-family`
(`packages/codemirror/style/base.css:5-9`) and no cell-type stylesheet overrides it. Filed in
`docs/PLAN.md` rather than folded in - it needs its own failing test and its own evidence, which is
how P2l-2 handed `CollapsedCell` to P2l-4.

### The overflow is the toolbar's popup opener, and `COMPACT WINDOW` is the rule that says where (P2r-2)

P2r-1 measured that the notebook's action row has **72dp** free at 320dp against a **73.3dp** label,
so the bar cannot carry the cell-type control on a phone. What happens then is not a choice: a
`ReactiveToolbar` *moves* an item too wide for the row into a popup opener
(`packages/apputils/src/mainareawidget.ts:51`,
`packages/ui-components/src/components/toolbar.tsx:570-700`), and this app's popup opener is
`NotebookActionsMenu` - the one overflow a phone's bar has room for, which that file has argued
since U5c.

**It widens what that menu is, and the widening is stated rather than absorbed.** The menu was "the
reference's File and View menus"; it is now that *plus what the reference's own toolbar could not
fit*. The row is first and divided off so it reads as the second thing rather than as more View -
and it is `cellTypeInMenu`, a nullable that is null when the bar has the control, because a
`ReactiveToolbar` moves an item and does not copy it.

**The row carries the value, like the trigger does.** `Cell type: Markdown`, not `Cell type…` -
`cellTypeRowLabel`. A flow addresses it by that string, and none of the three strings this control
uses contains another, because a Maestro selector is a regex matched against the **whole** node.
That last fact cost two runs: `E = mc^2` found nothing first because `^` anchors, and then because
the raw cell's field holds three lines and the selector had no `.*`.

**The harness had no rule for this width, and `NARROW WINDOW` would have been wrong.** `WIDE WINDOW`
means `>= 600dp` to `run-all-flows.sh` and `run-flows-sharded.sh`; `NARROW WINDOW` means `< 840dp`,
a boundary that came from the pane threshold. Declaring the latter would place this flow on the
tablet in portrait at **800dp** - a window where the bar *does* carry the control, so the flow's
first assertion is false there and it would fail for being asked the wrong question. `COMPACT
WINDOW` (`< 600dp`) is the exact complement of `WIDE WINDOW`, added to both scripts in one commit
because `driver-rules.sh` exists precisely so the runner and the sharder cannot disagree about a
rule.

### The chrome ramp is VS Code's, and two roles are not the id the plan named (P2u-1)

`theme/ColorRamp.kt` is re-sourced from the **resolved** `2026-dark.json` / `2026-light.json`, and
`theme/Theme.kt` maps every Material role onto it. This is the first code step of the presentation
re-base; the syntax palette is still IntelliJ's until P2u-2 and P2u-3.

Measured on the emulator, before and after, in `evidence/p2u-1-chrome/`:

| What | Before (IntelliJ) | After (VS Code 2026) | Colour id |
|---|---|---|---|
| the page | `#1E1F22` | `#121314` | `editor.background` |
| body text | `#CED0D6` | `#BBBEBF` | `editor.foreground` |
| the sidebar and the bars | `#2B2D30` | `#191A1B` | `sideBar.background` |
| the blockquote's rule | `#4E5157` 1.74:1 | `#707070` **3.76:1** | `checkbox.border` |
| the thematic break | `#393B40` | `#2A2B2C` | `panel.border` |

**Two mappings `docs/PLAN.md` named did not survive being measured**, and both are recorded here
rather than quietly transcribed. The pattern is P2m-2's: a floor moves only when a number says so.

**`primary` is `textLink.foreground`, not `button.background`.** VS Code splits what Material
fuses - `button.background` `#297AA0` is a fill drawn under `button.foreground`, and
`textLink.foreground` `#48A0C7` is the accent drawn *on* a surface. Material has one `primary` for
both. Which one this app needs was not a matter of taste but of counting its call sites: **nine,
every one a label, an icon tint or a caret, and not one a filled button** - `HiddenCellsRow`,
`CollapsedCell`, `CellHeader`, `HeadingCollapser`, `TableOfContentsPanel`, and three `cursorBrush`.
The app has no `Button` and no `FloatingActionButton` at all.

```sh
grep -rnE '(^|[^a-zA-Z])Button\(' app/src/main | grep -vE 'IconButton|TextButton|OutlinedButton'
```

returns nothing. On dark's page `button.background` measures **3.88:1** and `textLink.foreground`
**6.31:1**, so the fill accent would have put every one of those nine sites under AA. The fill pair
is kept intact and unused-but-correct on `primaryContainer` / `onPrimaryContainer`, at 4.79:1.

**`outline` is `checkbox.border`, not `panel.border`.** `panel.border` measures **1.31:1** dark and
**1.13:1** light, and that is not a flaw in the theme - VS Code draws separators that faint on
purpose, and its `textBlockQuote.border` is the same value. But Material's `outline` is the role
that bounds a *component*, which WCAG 1.4.11 asks 3:1 of, and this app's one reader of it is the
blockquote rule at `RichText.kt:82`, which would have vanished. VS Code has both kinds of border,
so each takes the Material role that means the same thing: `outline` ← `checkbox.border` (3.76:1
dark, 3.64:1 light) and `outlineVariant` ← `panel.border`, which is Material's *decorative*
divider and is meant to be faint. Nothing was invented; the two references were lined up.

**VS Code 2026 has no green surface**, checked across `charts.*`, `inputValidation.*`,
`diffEditor.*`, `gitDecoration.*` and `editorGutter.*`. So only `tertiary` is green and the
tertiary *container* is the theme's neutral container with the theme's foreground on it. Light's
`charts.green` `#388A34` is **4.33:1** and was passed over for `gitDecoration.addedResourceForeground`
`#587C0C` at **4.88:1** - a choice between two of the reference's own values for the same idea,
not a departure from it.

**`ColorSchemeTest` gained a contrast gate, and the old ramp fails it.** Membership on the ramp
cannot say a role is legible on what it is drawn over, which is the whole subject here. Run against
the IntelliJ ramp the new assertion reports **thirteen** pairs under their floor - including
`primary on surface` at 3.85:1 dark and `outline on surface` at 2.07:1 - so this is a measured
improvement rather than a lateral move. Without it, `docs/DECISIONS.md`'s own rule that a value may
not be nudged without re-measuring would have had nothing enforcing it.

**One thing this step knowingly leaves inconsistent for one commit.** `primary` and
`EditorPalette.stripeSelected` used to be the same `#3574F0`; `primary` has moved and
`stripeSelected` has not, so the active cell draws a `#3574F0` collapser beside a `#48A0C7`
chevron. `EditorPalette` is P2u-2's and P2u-3's, and P2u-3 re-derives that border from
`notebook.focusedCellBorder` → `focusBorder` `#3994BC` - the same teal family. Moving it here to
match would transcribe the value twice, the second time with the wrong citation, and would ship a
cell-chrome change without the `u3-cell-chrome` re-capture that P2u-3's gate requires. It is
visible in `evidence/p2u-1-chrome/after/dark/03-the-document-at-rest.png` and named here so it is
a known state rather than a defect nobody wrote down.

### The cell keeps its header strip, and VS Code's run gutter is declined (P2v)

P2v was written as a re-derivation of the cell chrome from VS Code: the run control into a gutter
**outside** the cell (`width: 35px; left: -35px`, `media/notebook.css:228-234`), the execution
count at that gutter's foot (`cellExecution.ts:139,200`), the heading caret onto the same left
indicator (`cellRenderer.ts:148`), and `CellHeader` retired - the `⋮` to the cell's top-right
(`notebookOptions.ts:162`), the duration to a foot status bar. **All four rows are declined.**

**Unlike the first time this was written down, the gutter was built before it was refused.** The
frames are `evidence/p2v-1-run-gutter/` on the unreachable branch `p2v-declined-attempt`
(`git show 74b9cd8:evidence/p2v-1-run-gutter/tablet/01-nothing-run-yet.png`), and they are the
measurement this entry rests on rather than the arithmetic that preceded them:

- **The width is not there, and a phone is where it is spent.** The column is 48dp and not the
  reference's 35px, because P2l-4 settled that a touch target is reserved space rather than
  expanded bounds - so it is as wide as the press it holds. 48dp beside every editor is **15% of a
  320dp window**, and what it takes is the one thing a cell exists for: the width of the text being
  edited. P2o-4 declined an 80dp bottom bar at 20% for more than this buys.
- **The reference does not pay the same price.** VS Code spends `codeCellLeftMargin: 28` +
  `cellRunGutter: 32` = 60px of a desktop window (`notebookOptions.ts:118-136`) - single digits as
  a fraction. A ratio transcribed from a surface an order of magnitude wider is not the same
  decision on a phone, which is the whole reason this file separates what is transcribed from what
  is adapted.
- **Built, it broke P2n's own invariant.** The gutter arrives beside the editor while the header
  strip above it keeps the prompt, so `[ ]:` sits at the frame's left edge and the editor 48dp
  right of it - **two leading marks at two x's**, which is precisely the ragged edge P2n spent
  itself removing (`evidence/p2n-gutter/before/gutter.txt`). Only retiring the header closes it,
  and retiring the header is the row that has to justify itself independently.
- **The header it retires fits.** `CellHeaderTest.theMenuButtonStaysOnScreenAt320dp` holds the
  strip's worst case - prompt, Run, duration label, `⋮` - inside 320dp. Moving three controls to a
  top-right toolbar and a foot status bar solves crowding this app does not have.
- **VS Code's markup affordances assume a pointer.** It renders a markup cell on blur or Escape;
  a touch screen has neither, which is why this app's rendered markdown *is* the tap target that
  opens its editor. The chevron/play slot P2v-3 asked for was an adaptation of an adaptation.

This does not reopen the presentation re-base. **Colour is settled and stays settled** - P2u-1
through P2u-3f are VS Code's and untouched - and P2w onward (the breadcrumb, the kernel indicator,
workspace search, the phone's bottom navigation) is unaffected: none of it is cell chrome. What is
refused is the *cell layout* half, and only that.

What ships instead is two adaptations, each narrower than the row it replaces.

**A cell's play is outlined, not filled.** A filled triangle at rest reads as "playing"; outlined
reads as available, which is what an idle control means, and a notebook draws one per cell so the
misread is repeated down the whole document. Scope is the three run controls in `CellHeader` -
`NotebookBar`'s *Run all* and `SelectionTopBar`'s play are toolbar commands acting on a selection
rather than a cell's own state, and keep their filled glyphs. `cellRunIcon` is a value rather than
an inline argument so `CellRunIconTest` has something to pin: without it the departure back to
filled is a one-token edit no gate would see.

**A heading's fold chevron shows rendered only.** JupyterLab's prompt node carries the caret in
both states - `InputArea` builds it once and hides only the *editor* when the cell renders
(`packages/cells/src/inputarea.ts:53-56,111-119`) - and P2n-3 transcribed that. Composing it while
the source is open spends a column of the editor's width on an affordance that cannot be used
mid-typing anyway: collapsing the section would close what is being written. Hidden while editing,
a heading's editor runs full-width like every other cell's, and the header's play - the control
that renders the cell - is the only thing on it. The caret returns when the cell renders again.
**This supersedes the both-states assertion of P2n-3**, which `MarkdownCellTest` carried as
*"Unrendered too: the reference's prompt node - and so its caret - is there in both states."*

**Its cost, named rather than discovered later:** `CellAction.ToggleHeadingCollapse` has exactly
one caller, the caret itself (`MarkdownCell.kt`), and it is in no menu - so while a heading cell is
being edited its section cannot be folded at all. Rendering the cell first is the route, which is
the sequence the reasoning above already assumes. **Expanding is not affected**: a collapsed
heading draws `HiddenCellsRow` inside its own frame whatever state its source is in
(`EditorCell.kt`), so a section folded and then edited is never a dead end. A menu row for the
command is deliberately *not* added - a second route to folding-while-typing would undo the step.

### The notebook's breadcrumb is ancestors only, and absent at the root (P2w-1)

The trail sits between the document's name and its actions, which is where `BreadcrumbsControl`
sits relative to a tab title. **Three decisions in it are not transcription**, and each is here
because measuring or reading said so.

**Ancestors only - the document's own name is never a segment.** VS Code's file path ends with the
file itself: `_initFilePathInfo` unshifts a `FileKind.FILE` element for the resource before walking
up (`breadcrumbsModel.ts:142`), and `breadcrumbs.filePath` defaults to `'on'`, the whole path
(`parts/editor/breadcrumbs.ts:132-136`). It can afford to repeat the name because its breadcrumb
sits under a **tab bar**, and a tab is also a close target and a document selector; ours sits
directly under a title row that is neither and already draws the name at `titleMedium`. Repeating
it would spend a row of a phone's chrome on a string already on screen 48dp above. Every segment is
therefore a folder, which is also what makes *"a segment opens that folder"* one rule rather than
two - VS Code needs two, because its last segment opens a picker instead.

**A tap navigates the browser, which is JupyterLab's behaviour and not VS Code's.** VS Code's plain
click opens a picker over that folder and only a modifier reveals in an editor
(`breadcrumbsControl.ts:609-641`, `:729-733`); JupyterLab's is `this._model.cd(destination)`
(`packages/filebrowser/src/crumbs.ts:415`). This is inside the re-base's stated limits - *"The
presentation reference is VS Code; the behaviour reference stays JupyterLab"*. VS Code answers
where the row sits and what it looks like. It does not answer what a tap does, and a picker is also
a second navigation surface over a tree this app already has a screen for.

**Absent at the workspace root, where VS Code renders `"no elements"`.** A document with no
ancestry has nothing to say, and most notebooks in this workspace are at the root - so drawn
unconditionally the row would be a permanent tax paid by the common case for the rare one. The bar
is exactly P2q-2's 112dp for a root notebook and 160dp for a nested one. The trade is a bar whose
height differs between documents, which is accepted: the alternative is the 48dp P2v declined the
run gutter over, spent on a single crumb reading *Workspace*.

**The cost is pinned as a difference, not as a total**, and that is a measurement lesson rather
than a style choice. `EditorTopBar` consumes `TopAppBarDefaults.windowInsets`, so the bar's total
height includes a status bar that is 24dp on the emulator and something else on the tablet - the
first version of `theThirdRowsCostIsPinnedAgainstTheTwoRowBar` asserted 112dp and measured 640dp
and 132dp before it was reading the right thing. What each row *reserves* is
`LocalMinimumInteractiveComponentSize` on every device, so the assertion is what the trail adds.

**One fraction collapses both rows.** `titleCollapseFraction` already takes the region's height as
a parameter (P2q-2), so a second collapsing row is a change to the argument `EditorScreen` passes
it. Two fractions could disagree; one cannot. Collapsed, a three-row bar is the 64dp action row -
the same place a notebook that never had a trail reaches.

**And the explorer's own trail moved rather than being copied.** `Breadcrumb` is one composable in
`ui/` serving both surfaces. It costs the browser's row 4dp - intrinsic height made it 52dp, a
20dp label inside 16dp of vertical padding, and it now states Material's 48dp minimum, which is
also the number `EditorScreen` needs in order to say how far the bar slides.

**The seam is hoisted state, not a holder.** `OpenDocumentOutline` exists because the outline flows
*sideways* between two `NavEntry`s that cannot see each other; a crumb's target flows **down** from
their common parent, so `MainNavigation` holds a `browseRequest` and both entries read it. What is
not symmetric is what each layout needs beside it: in one window the browser is not composed until
the editor entry is popped, and beside the notebook the side area may be drawing the table of
contents, so the request also shows Files. Both halves are driven - `p2w-1-breadcrumb-wide` raises
the outline on purpose, which is also what makes its selectors unambiguous, since two panes
otherwise put "Workspace" and "reports" on screen twice.

**It found a race, and the race was older than it.** See the entry below.

### The kernel indicator is right of Find and left of the `⋮` (P2w-2)

`docs/PLAN.md`'s P2w-2 row asked for *"the rightmost item"*, and reading the reference's own layout
rather than recalling it says that is not where the reference puts it either.

JupyterLab ranks `kernelName` **1000** and `executionProgress` **1002**
(`packages/notebook-extension/schema/panel.json`), after the spacer at 100 - the last two things on
its notebook toolbar. But that toolbar is a `ReactiveToolbar`, and its **popup opener is always the
last widget**: it is inserted first (`packages/ui-components/src/components/toolbar.tsx:391`) and
every later `insertItem` clamps to `widgets.length - 1` (`:470-480`), so items land *before* it;
`_getWidgetsToRemove` then walks `while (index < toIndex)` with `toIndex = widgets.length - 1`
(`:661-700`), which is the opener's own index, so it is never collected either. This app's popup
opener is `NotebookActionsMenu`, as `ActionRowPlacement` already states about itself. Android's
top-app-bar convention puts the overflow last too, so the two agree and there was nothing to trade.

**The two toolbar items are one control here.** A phone's action row has no width for two 48dp
targets answering one question, and VS Code already draws them as one - a single item in group
`'status'` at order `-10` (`viewParts/notebookKernelView.ts:73-76`).

**Not a picker**, which is both references' behaviour for it (`dialogs.selectKernel`,
`KernelPickerMRUStrategy.showQuickPick`). `KernelGateway` has no kernelspec listing and the base
Space has one kernel, so a picker over one entry is chrome; the port it would need is P3's. The tap
opens the kernel menu the bottom bar's `⋮` already anchors - the thing that says a kernel is stuck
is the thing you restart it from - and `notebookKernelCommands` is what stops the two anchors from
drifting apart.

### The kernel's name goes before its status does, which reverses the reference's order (P2w-2)

`_getWidgetsToRemove` sheds a `ReactiveToolbar`'s **rightmost** items into the popup first
(`toolbar.tsx:661-700`), so JupyterLab would push `executionProgress` (1002) in *before*
`kernelName` (1000). This app does the opposite: below 600dp the status glyph stays on the bar and
the kernel's name is what goes.

Measured, not preferred: free width in this action row is **72dp at 320dp** and **552dp at 800dp**
(`evidence/p2o-3-two-pane-threshold/<w>/hierarchy.xml`), and a status glyph beside "Python 3" wants
about 110dp - the same arithmetic that put the cell type in the overflow at P2r. What differs is
which half is worth keeping: a phone is this app's primary device, the kernel's *state* is what a
glance is for, and a name that never changes is what a glance does not need.
`evidence/p2w-2-kernel-indicator-narrow/` is the frame - six controls in 288dp of a 320dp window,
nothing truncated.

### Active kernel states are solid, resting ones are outlined, and the reference disagrees with itself (P2w-2)

Two components in the reference draw this and they do not draw it the same way.

The retired `KernelStatus` toolbar item used `circleIcon` - a **solid disc** - for busy, starting,
restarting, terminating and initializing, `circleEmptyIcon` for idle, and `offlineBoltIcon` for
connecting/disconnected/unknown (`packages/apputils/src/toolbar/widget.tsx:213-232`). The
`ExecutionIndicator` that replaced it keeps that split for starting and restarting
(`packages/notebook/src/executionindicator.tsx:140-151`) but draws busy and idle as a
`ProgressCircle` at **0% and 100%** (`:153-188`), so **its idle is the filled one**.

This app follows the retired item, and the reason is what the newer one *is*. `ProgressCircle` is a
progress meter: a stroked ring with a filled pie inside whose angle is `executed/scheduled`
(`packages/statusbar/src/components/progressCircle.tsx`). 100% reads as "full" only where a
percentage is being drawn, and this bar draws none - where a run got to is on the cell's own header
(`ExecutionLabel`). Two discs with no scale to read them against is worse than one rule.

**The cost is named rather than glossed:** a JupyterLab user's eye is trained on the meter's
polarity, and here a filled mark means *busy* where theirs means *finished*.

**And the bolt is outlined where the reference's is filled**, which is a change of weight and
nothing else. `offline-bolt.svg` is Material's filled `offline_bolt` verbatim, and every toolbar
icon there - this one included - is the same `jp-icon3` grey
(`ui-components/style/icons.css:23`, `theme-light-extension/style/variables.css:231`), so the
reference makes no tonal distinction either. What differs is **which state rests**: JupyterLab
opens a session with the document and is practically never here, while this app starts a kernel
lazily and so shows this on every notebook until something is run. Drawn filled beside four
outlined glyphs it was the loudest thing in the row - seen in the frame, not predicted - and a
resting state that reads as an alarm is the misread P2v-1 retired the filled play for.
`KernelStatusGlyphTest` pins all of it.

### The indicator can read *Idle* for a kernel P1c has already stopped (P2w-2)

`KernelStatus` is derived from whether a session is open, and `IdleServerStopper` stops the app's
own server after 30s of a backgrounded idle app (`data/space/IdleServerStopper.kt`). Nothing tells
the editor that happened, so for one window - *backgrounded past the grace, returned, nothing run
yet* - the bar says the kernel is idle when there is none.

It corrects itself on the next run: the stale session is refused,
`NotebookKernelSession.reopeningIfStale` drops it, and the status falls to `STARTING`. The honest
fix is a server-generation signal reaching the editor - `BaseSpaceServer` already counts one - and
that is a new port surface, so it belongs with P3's rather than here. Recorded so the gap is a
known cost and not a bug somebody re-finds.

### A listing in flight could overwrite where the browser had just gone

`ExplorerViewModel.refresh` re-lists `uiState.path` - where the browser *is* - and
`KeepTheListingFresh` calls it the moment the screen enters composition. A navigation starting in
the same frame therefore raced a listing of the directory it was leaving, two `viewModelScope`
coroutines each writing the whole state, and whichever finished last won.

**The frame is what caught it**, on the tablet: the crumb brought the browser back beside the
notebook and left it standing in `reports` after a tap on `Workspace`. Every assertion up to that
point was green, and the emulator's one-pane run had passed the same code.

Two halves, and one alone inverts the bug rather than fixing it. `listing?.cancel()` settles which
listing wins. `browsing` - where the browser is *going*, which leads `uiState.path` for as long as
a listing is in flight - is what `refresh` re-lists, so a poll launched between a navigation
starting and landing follows it instead of undoing it; cancelling alone would have let that poll
cancel the navigation and re-list the old directory.

`SlowListingWorkspaceRepository` exists because a `StandardTestDispatcher` completes queued
coroutines in launch order, and this defect needs the opposite: the listing started **first** has
to finish **last**. A per-path delay is the only way to put the completions in that order on
purpose.

### `SearchQuery` lives in `domain/model`, and the measurement is what put it there (P2x-1a)

`docs/PLAN.md`'s P2x-1 row says `SearchQuery` *"moves out of `ui/editor/` to a shared home"* and
does not name the home. Two were candidates, and one of them is not free.

The row's own argument - *"it is document logic both surfaces read"* - points at CLAUDE.md's
document-and-language rule, which would give it a package of its own beside `syntax/`. But
`domain/port/WorkspaceSearcher` takes a `SearchQuery`, so wherever it goes, **`domain` imports
it**. Measured across `domain/`, every project-local import is
`com.jupydroid.app.domain.model` - one package, no exceptions - and `docs/architecture.md` §1
states the rule that keeps it so: *"domain imports nothing project-local"*. A `search/` package
would have been the first exception, and §1 and §5 would both have needed amending in the same
commit to stay true.

`domain/model` needs no amendment, and it is not a loophole: `WorkspaceEntry` already stands in
exactly this relationship to `WorkspaceRepository` - the type a port's signature is written in.
`SearchQuery` is now that for `WorkspaceSearcher`.

**`replacementFor` moved with it**, from `internal` in `ui/editor` to public here. The port owns
replace-across-files, so `DiskWorkspaceSearcher` is what substitutes - and `data` may not import
`ui` (§5). The alternative was a second copy of the reference's two-step rule
(`substitutedText` then `preserveCase`), which is the duplication CLAUDE.md bans.

Nothing else moved. `SearchHit`, `NotebookSearch` and `NotebookReplace` are what a *notebook* does
with a query, and stay where the notebook is.

### The glob language is transcribed, because the platform's disagrees about depth (P2x-1b)

`search.exclude` and `files.exclude` are glob expressions, and their eight registered defaults are
the first thing workspace search has to honour. Android ships a glob matcher already -
`java.nio.file.FileSystems.getDefault().getPathMatcher("glob:...")` - so the cheap move was to hand
the patterns to it.

**Measured on this machine's JDK, it disagrees with the reference on the one case the defaults are
made of.** A leading globstar there requires a separator to be present:

| Pattern | Path | `java.nio` | VS Code |
|---|---|---|---|
| globstar + `/node_modules` | `node_modules` | **false** | **true** |
| globstar + `/node_modules` | `a/node_modules` | true | true |
| globstar + `/*.py` | `notes.py` | **false** | **true** |
| globstar + `/*.py` | `reports/deep/notes.py` | true | true |

Every registered default is written with that leading globstar, so the platform matcher would have
excluded a nested `node_modules` and quietly kept a top-level one. A search that misses files, or
reports files it should not, with nothing on screen to say so.

So `com.jupydroid.app.glob` transcribes `parseRegExp` (`glob.ts:108-256`) instead - eight branches,
about 90 lines of reference. **The gate for a transcription is equality, not behaviour**:
`PathGlobParityTest` holds the regular-expression source for 25 patterns against the reference's
own output, obtained by extracting `starsToRegExp`, `splitGlobAware` and `parseRegExp` into a
standalone module and running them under node at `f3fa55c39d3`. It was mutation-checked - narrowing
the no-separator class by one character fails it - which is the same reason `EditorPaletteTest`
pins the palette by equality rather than by contrast.

**The one departure is the separator class.** The reference's `PATH_REGEX` is `[/` backslash `]`,
because Windows produces a backslash; every path this app matches is workspace-relative POSIX, and
`WorkspaceRoot` refuses anything else. The narrowing is applied to the reference's output before
the parity comparison, so it is the only edit and it is visible in the test's own doc.

### The three things a workspace walk declines to read (P2x-1d, and one of them lifted by P2x-4b)

`DiskWorkspaceSearcher` reads every file in the tree to answer one query, and three kinds of file
are passed over. Each is a decision, and one of them departs from the reference.

**A binary file, by ripgrep's rule.** VS Code states none of its own - it sets `maxFileSize` to
16 GB and delegates the whole question to the engine (`rawSearchService.ts:85-87`,
`ripgrepTextSearchEngine.ts:444-446`) - so the rule was read out of ripgrep's `GUIDE.md`, "Binary
data": *"a file is considered 'binary' if and only if it contains a NUL byte somewhere in its
contents"*, and its default mode, *"the one that applies to files searched as a result of recursive
directory traversal"*, removes such a file from the search completely. Checked on the bytes rather
than after decoding, so a PNG is never turned into a megabyte of replacement characters on the way
to being discarded.

**A notebook, until a hit could name its cell - and P2x-4b lifted it.** Read as the JSON it is, a
`.ipynb` would report `"cell_type"` and the inside of a base64 image output as matches. Searching it
wrongly and fixing it later would have been a defect shipped on purpose, so it was skipped, with the
skip named in the code and pinned by a test. A notebook is now *parsed* rather than passed over, and
the test that pinned the skip is the test that pins the opposite; what took its place as the walk's
fourth refusal is **a notebook this app cannot read** - see the P2x-4b entry below.

**A directory symlink, and this is the departure.** `search.followSymlinks` defaults to **true**
(`search.contribution.ts:186-190`). The workspace lives inside a rootfs and a cell can call
`os.symlink`, so a loop is reachable; ripgrep detects one and nothing in this app would. Following
them would need loop detection - canonical paths held for the length of a walk - which is machinery
bought for a case no user has met. A file symlink is still followed, because it cannot loop.
`aSymlinkLoopDoesNotHangTheWalk` is the regression test, and it fails when the rule is removed.

### Replace across files goes through the editor's model, not the filesystem (P2x-1e)

P2x-1c put `replaceAll` on `WorkspaceSearcher`, arguing that finding and replacing are one feature
*"because the reference's replace-all walks the results the search just produced"*. **Reading the
reference retired that argument, and the method with it.**

`ReplaceService.replace` builds `ResourceTextEdit`s and hands them to `IBulkEditService`
(`replaceService.ts:115-118`). `BulkTextEdits._createEditsTasks` then calls
`_textModelResolverService.createModelReference(uri)` for **every** resource in the set - open or
not (`bulkTextEdits.ts:227`) - edits the *models*, and only afterwards saves each one:
`this.textFileService.files.get(e.resource)?.save(...)` (`replaceService.ts:136`). A notebook cell
resolves its notebook model and saves that (`:120-128`).

So there is no "write the file, then tell whoever has it open". The document **is** what is edited;
where none exists, one is resolved for the occasion. Three consequences follow from the same code
and none of them was in this app's plan:

- **Replace-all is one undo.** A `MultiModelEditStackElement` over every touched model, in one
  `UndoRedoGroup` (`bulkTextEdits.ts:319-330`).
- **A conflict throws rather than prompting.** `_validateBeforePrepare` and `_validateTasks` raise
  `"has changed in the meantime"` on a `versionId` mismatch (`:213-216`, `:300-306`). There is no
  dialog, and an earlier draft of this entry guessed there was one.
- **The confirmation is counted.** *"Replace {0} occurrences across {1} files?"*, and the same
  counts are reported afterwards (`searchView.ts:831-837`, `:860-925`).

**`WorkspaceSearcher.replaceAll` is therefore gone** (P2x-1e), along with `textWithMatchesReplaced`
and `ReplacedText`. It had no caller, and its signature could not express what the reference
guarantees: it took a *query* and re-ran the search, where `IReplaceService.replace` takes the
*matches already on screen* - so a file changed between the search and the replace would have been
rewritten against matches the user never saw, which is the exact case `_validateBeforePrepare`
exists to refuse. Removing an uncalled method is cheaper than leaving one that invites a caller it
cannot serve.

**JupyterLab answers none of this**, which is itself worth recording: its search is scoped to
`app.shell.currentWidget` (`documentsearch-extension/src/index.ts:227`) and it has no workspace-wide
search or replace at all. P2t split the references - VS Code for presentation, JupyterLab for
behaviour - and P2x is the first place where the behaviour reference has nothing to say, so VS Code
answers both halves here.

**What this leaves open is a prerequisite, not a detail.** Parity needs a registry of open documents
keyed by path, which this app does not have: `Navigation.kt:161` scopes ViewModels to the Activity's
`ViewModelStore` through `rememberViewModelStoreNavEntryDecorator`, keyed by class inside a nav
entry, so nothing can be asked for "the ViewModel holding `reports/a.py`". That is the same gap
`docs/PLAN.md`'s `openNotebook` back-stack risk describes from the other side, and P2x-5 cannot be
attempted before it exists.

### The activity bar's order takes both references, and neither could state it alone (P2x-2)

`docs/PLAN.md`'s P2x-2 row said the rail followed *"VS Code's activity-bar order — which is also
JupyterLab's rank order, so `SidePanel`'s existing doc comment stays true."* **The second half is
false, and it was checked in both trees rather than assumed.**

| | file browser | search | table of contents |
|---|---|---|---|
| JupyterLab | rank 100 (`filebrowser-extension/src/index.ts:676`) | **none** | rank 400 (`toc-extension/src/index.ts:309`) |
| VS Code | `order: 0` (`files/browser/explorerViewlet.ts:265`) | **`order: 1`** (`search/browser/search.contribution.ts:59`) | **none** |

`shell.add(…, 'left', …)` is called **five** times in JupyterLab's whole tree — File Browser 100,
Sessions 200, Command Palette 300, Table of Contents 400, Extension Manager 1000 — and none of them
is a search. Its `documentsearch` is scoped to `app.shell.currentWidget`
(`documentsearch-extension/src/index.ts:227`), which is the find bar P2i shipped, not a panel. VS
Code, from the other side, registers no table-of-contents container: its Outline is a view *inside*
the explorer's, `registerViews([…{ order: 2 }], VIEW_CONTAINER)`
(`outline/browser/outline.contribution.ts:11,26,35`).

So the two answer **different halves and agree where they overlap**: the browser is first in both,
search is second because VS Code says so, the table of contents is last because JupyterLab says so.
This is the same split P2x-1e recorded from the behaviour side — P2x is where JupyterLab stops
having an opinion, and here it stops having one about presentation too.

**The panel is named and empty, and that is the reference rather than an omission.**
`TableOfContentsSidePanel` carries a placeholder because `treeview.tsx:31-40` draws one; VS Code's
search view with an empty query is its input box and nothing else. There is no string to
transcribe, and an invented one would be a string P2x-3 deletes. What the empty body did cost is a
**measurement instead of a screenshot**: a panel with no content has no visible right edge, so
whether it sat in the list pane or spanned the window could not be read off the picture. Dumped on
the tablet at 1.5× (`evidence/p2x-2-search-panel/hierarchy-with-search-up.xml`): rail
`[0,0][120,1200]` = 80dp, panel `[122,0][662,1200]` = the 360dp list pane, document area
`[698,0][1920,1200]` = 815dp.

### A one-pane window draws the browser, whatever the rail was left on (P2x-2b)

`sidePanel` is `rememberSaveable`, so it survives the activity relaunch a rotation causes. The rail
is drawn only `if (twoPane)`. Nothing sat between them, so a tablet rotated to portrait — 1280dp
landscape is two panes, 800dp portrait is one — kept drawing the panel it had **with no rail to
leave it by**, and the file browser could not be reached again without killing the app. Collapsed,
the pane drew nothing at all.

Pre-existing since **P2o-2** and not introduced by P2x-2, which took it from two panels to three —
which is why it was fixed rather than filed. `p2o-4`'s own header already states the rule it broke:
*"where the shell draws one pane there is no side area."*

The fix derives the showing panel once, beside the `directive` line that already spells the other
half of the same rule, so the `when` keeps exactly the arms it had. **An override rather than a
reset**: rotating back restores the panel the user had chosen, where clearing `sidePanel` would
have thrown it away.

**Measured before it was fixed**, which is the whole reason it is an entry and not a hunch: 9
instrumented cases, 2 failed, both of them the new ones. The two drive the width as *state* —
`ForcedSize` plus a `MutableState<Boolean>` — because that is the half of a rotation composition
sees. The other half is a real configuration change, which no Compose test drives, so it is a
device flow: `p2x-2-rotation`, TABLET ONLY (the emulator is one pane in both orientations) and
NOT A SUITE FLOW (it rotates the device, which every flow after it on that device would inherit).

### The option toggles are drawn as type, because Material ships none of the three glyphs (P2x-3d)

`match_case`, `match_word` and `regular_expression` are Material **Symbols**. The artifact this app
depends on is `material-icons-extended`, and it has **none of them** — checked in the jar, not
assumed: what it does have near the subject is `Abc`, `Spellcheck`, `TextFields` and `FormatSize`,
each of which means something else. A near-miss icon is worse than no icon; it is a control that
lies about what it does.

The reference's own icons are **typographic**, read from the codicon sources rather than remembered
(`vscode-codicons/src/icons/`): `case-sensitive` is a capital and a lower-case A, `whole-word` is
two letters over a full-width rule, `regex` is an asterisk and a dot. So type draws them — `Aa`,
`ab` underlined, `.*` — in `symbolKeyStyle`, the style that exists for exactly this case and says so
in its own doc: monospace for the *shape* of a mark rather than because it is source
(`scripts/check_monospace_home.py` states the rule and this exception together).

### A toggle's on state is a wash and a new name, and the wash is a role this app already had (P2x-3d)

The reference's `Toggle` marks *on* with `inputOption.activeBackground` / `activeForeground` and no
check mark. Resolved in the 2026 themes (`scripts/resolve_vscode_theme.py`) against what Material's
`secondaryContainer` pair already resolves to on this app's ramp:

| | reference | this app's role | delta |
|---|---|---|---|
| dark background | `#313233` | `#2C2D2E` (`DarkGray6`) | one step of the same ramp |
| dark foreground | `#bfbfbf` | `#BBBEBF` (`DarkGray9`) | 4/255 |
| light background | `#D6D6D6` | `#DADADA` (`LightGray4`) | 4/255 |
| light foreground | `#202020` | `#202020` (`LightGray1`) | **equal** |

So the role carries it and no fourth colour id is introduced. P2u's own rule is that a palette
states colours something draws (`scripts/check_palette_consumers.py`); three new ids a hair from
three existing ones would be the opposite of that.

**The wash is not the accessible state.** Android keeps a toggled state on a node that carries no
label, so what says which way a toggle is set is the label itself, flipped — the rule every toggle
in this app has followed since the export picker. A control here reads as what a **tap will do**,
never as what is currently true, which is why a flow asserts *"Match anywhere"* to prove whole-word
is **on**.

### A toggle is walked at once and a keystroke is not, so the delay moved onto the request (P2x-3d)

`onDidOptionChange` calls `triggerQueryChange()` bare (`searchView.ts:712-713`), and that function's
own default is `delay: 0` (`:1590`). Typing is the **one** caller that passes a period,
`searchOnTypeDebouncePeriod` (`:584`), and the include/exclude boxes are the other (`:582-588`).

The reason holds outside the reference too: a debounce exists to wait for the next character, and a
toggle has no next character coming.

So `WorkspaceSearchViewModel` carries the wait on the request rather than on the operator —
`debounce { it.waitMillis }` over a `SearchRequest(query, waitMillis)`, which is `triggerQueryChange`
as a value. One path for every caller, and `collectLatest`'s cancellation still supersedes a pending
walk whichever door it came through. Pinned by `anOptionIsWalkedAtOnceWhereAKeystrokeWaits` and
mutation-checked: setting `AT_ONCE` to the debounce period fails it.

### The replace toggle is P2x-5's, not P2x-3's (P2x-3d)

`docs/PLAN.md`'s P2x-3 row lists a replace toggle among the query UI. It is **declined here** and
ships with the behaviour behind it, for the reason P2x-1e already recorded: `replaceAll` came off
the port because the reference replaces through the editor-model stack over *the matches on screen*
(`replaceService.ts:115-118`, `bulkTextEdits.ts:227`), and P2x-5 is where that lands together with
the open-document registry the open risks name.

A Replace All that cannot replace is the control this file already refuses under the export
formats — *"a row that can only fail is a control that lies"*. The row is corrected rather than
quietly under-delivered.

### The search panel is one scrolling surface, box included (P2x-3e)

The reference keeps its widgets fixed above a scrolling tree (`searchview.css:118-146`), and until
the glob boxes arrived so did this panel. Then it stopped working, and the measurement says why:
with both fields open the panel's fixed chrome is about **290dp**, and a soft keyboard leaves less
than that.

**The results were composed, correct, and zero pixels tall.** Logged off the tablet:
`walk ended, state=true n=2`, with the panel drawing the box, the `…` and the two fields and nothing
else — no count message, no tree, and nothing to scroll. The semantics tree printed in the
instrumented run ends at the exclude box: the later items were never composed at all. A screenshot
is what asked the question and a log is what answered it; neither would have done alone.

So `SearchResults` became `LazyListScope.searchResults` and the panel owns one `LazyColumn` — box,
`…`, glob fields, messages and tree as its items. The reference never meets this because it has no
keyboard taking half the view, which makes it a departure the platform forces rather than one taste
chose. The instrumented tests scroll through `scrollToSettled`, and that scroll **is** the
assertion: before the change there was nothing to scroll to.

### A glob heading is drawn and cleared out of the tree (P2x-3e)

The reference gives each pattern input an `ariaLabel` equal to the `h4` above it
(`searchView.ts:542`). In a browser the heading is decoration and the label is the control's name.
Here both landed in the semantics tree, so two nodes answered to *"files to exclude"* — and
`p2x-3-search-scope` tapped the **label**: `reports` went into the *include* box, and the flow failed
one assertion later against a count that was exactly right for what had actually been typed.

The rule this app already had — two controls a tap apart may not be spelled the same way — is what
the heading breaks, and `clearAndSetSemantics {}` is what keeps the reference's split intact: the
heading is drawn, the field carries the name. `eachGlobHeadingNamesExactlyOneNode` pins it.

### The details open focused and do not close focused (P2x-3e)

`toggleQueryDetails` moves focus both ways — into the include box on open, back to the query box on
close (`searchView.ts:1499-1517`). The opening half ships, because you tapped `…` to type a glob and
landing anywhere else is a second tap for nothing.

The closing half does not, and the reason is the platform's: focusing a text field on Android raises
the soft keyboard, so giving focus back would re-raise the keyboard the reader had just put away by
closing the details — the opposite of what the tap meant. The reference has no keyboard to raise.

### Opening a result lands on the match, and the match rides beside the route (P2x-3f)

`searchView.open` hands `openEditor` a `selection` built from the match's own range
(`searchView.ts:2240-2256`) - a range and not a line, so the editor reveals it *and* shows which
characters were found.

**Beside the route rather than inside it.** `NavEntry.contentKey` defaults to the key's
`toString()`, so a match carried in `FileEditor` would make the same file opened from search and
from the browser two entries with two `ViewModelStore`s - two editors on one document, which
`FileEditor`'s own doc already warns about. It travels as `revealRequest` in the shell, consumed
once, which is the pattern `browseRequest` set.

**Selecting is half of landing.** The other half is the scroll, and only the field's own
`TextLayoutResult` knows where a line ended up once wrapping has had its say - so `CodeCellField`
now offers the layout it already computed for its gutter through `onLayoutMeasured`, and the screen
scrolls to `getLineTop(getLineForOffset(…))`. Line-index × line height would be wrong the moment a
line wraps.

**The reference centres a revealed range and this puts it at the top.** Centring needs the viewport
height, which the layout does not carry, and the lines above a match are the context a reader wants
anyway - centring would spend half the screen on what came before it.

**A file that moved on is not guessed at.** `MatchedLine.rangeIn` answers null rather than selecting
whatever now sits at those offsets, because the workspace is shared with the kernel and a cell can
rewrite a file between the walk and the tap. The reference refuses a stale match the same way,
throwing *"has changed in the meantime"* on a version mismatch (`replaceService.ts:213-216`).

### A notebook is searched as its cells, and the cell is named by **index** (P2x-4a)

`INotebookCellMatchNoModel` is `{ index, contentResults, webviewResults }`
(`vscode/…/search/common/searchNotebookHelpers.ts:18-22`), and `matchedCellsIn` is the first two of
those three: one pass per cell over that cell's source, stamped with the cell's index.

**The index and not the cell's id, on a measurement rather than a preference.** The reference falls
back to `rawCell#${index}` wherever there is no live cell model to ask (`searchNotebookHelpers.ts:20`,
`notebookSearchModel.ts:162`). Here the id is worse than merely absent: nbformat's cell id is
optional before 4.5 and `CellFile.toDomain` mints a fresh `UUID` for a cell that carries none, so
**the id the walk read and the id the editor loaded need not be the same string**. The index is.

`MatchedLine.line` stays one-based and becomes the line **within that cell**, which is what makes
the cell load-bearing in the label rather than decorative - two cells both have a line 1.

### The fourth kind of file a workspace walk declines (P2x-4b)

Three were recorded at P2x-1d - a binary file, a notebook, a directory symlink - and P2x-4b turns
the second into its opposite: a notebook is now the one kind of file that is *parsed*. What it adds
in its place is **a notebook this app cannot read**.

`Json.readNotebook` throws for JSON that is not an object and for a `cell_type` outside nbformat's
three (`NotebookFile.kt`), and one such file in a workspace must not end the walk over every other.
The reference swallows a serializer's failure per file for the same reason - `catch (e) { return; }`
around each notebook it deserializes (`extHostNotebook.ts:555-558`).

Pinned by `aNotebookThatCannotBeReadIsPassedOver`, which is red without the decline.

### VS Code searches a **closed** notebook as JSON, so this is its experimental branch (P2x-4b)

`docs/PLAN.md`'s P2x-4 row said notebook-aware matching *"is VS Code parity rather than a departure
from it"*. **Only for an open notebook**, and the tree says so: `NotebookSearchService` searches
open notebook widgets cell-by-cell always, but reaches a *closed* one only when
`search.experimental.closedNotebookRichContentResults` is on, and its registered default is
**false** (`search.contribution.ts:287-291`, `notebookSearchService.ts:77`). Out of the box VS Code
hands a closed `.ipynb` to ripgrep and **does** report `"cell_type"`.

So what P2x-4 builds is that experimental branch - which is also, unconditionally, what JupyterLab's
own document search does. The same "both references answer different halves" split P2x-2 recorded
for the activity bar's order. It is a *better* answer than the reference's default rather than a
worse one, and it is named here so nobody reads the code against the wrong baseline.

### Cell outputs are not searched from the workspace panel (P2x-4)

`webviewResults` - the third field of `INotebookCellMatchNoModel` - is not built. Three measurements
decide it, and none of them is "later":

**The references disagree, and the one that is closer to this app says no.** JupyterLab's `output`
filter is **off** by default (`packages/notebook/src/searchprovider.ts:229-243`, already transcribed
in `SearchState.searchOutputs`). VS Code's four notebook filters default **on**
(`searchWidget.ts:204-208`) - but its filter control is hidden unless a notebook is open
(`searchWidget.ts:227-231`), and this panel has no settings screen to expose one until P6c.

**A hit in an output could not be landed on.** `SearchHit.outputAt` indexes the **rendered** outputs
(`renderedOutputsOf`), not the stored ones, because that is what `CellOutputs` draws - and rendering
drops `ClearOutput`. A disk-side index would name a different output from the one the editor shows.

**And the rule for which outputs even have text is in the wrong layer to be shared.** *"Only what
`RenderedOutput.Text` draws"* lives in `ui/editor/`, which `data/` may not import
(`ArchitectureGuardTest`, `docs/architecture.md` §1). Closing this means giving "what text an output
offers a search" one home that both can read - a real refactor with a real design question in it
(the mime ranking that decides `text/plain` loses to `text/html`), not an afterthought.

`docs/PLAN.md`'s open risks carry it as a gap with a named prerequisite.

### The cell is named in the row's label, not drawn as a level of the tree (P2x-4c)

`searchResultsView.ts` has **no notebook branch at all** - `grep -n cell` over it returns nothing.
The cell rides on `MatchInNotebook`'s id (`notebookSearchModel.ts:35`) and is used for opening, never
for rendering; the tree is file → match for a notebook exactly as it is for a `.py`.

Ours follows, and the 360dp panel is the second reason: a third level of indentation would spend the
width the code needs, which is the call P2x-3 already made against `FolderMatchRenderer`'s folder
level. What the cell gets instead is the row's `contentDescription` - which is where the line number
already lives, `search.showLineNumbers` being false - so a screen reader and a Maestro flow both
have one string per row and two rows a tap apart cannot answer to the same one.

One-based in that sentence where `MatchedLine.cell` is zero-based: it is spoken beside a one-based
line number, and the reference's own `rawCell#${index}` is an id that is never read aloud.

### A `LaunchedEffect` must not be keyed on what it changes (P2x-4d)

`LandingOnAMatch` was keyed on `state.cells`. **Landing changes the cells** - it expands the heading
hiding the match - and Compose cancels a running effect the moment a key changes, so
`animateScrollToItem` died mid-animation and `onRevealHandled` was never called. The restarted
effect found its own `landedOn` guard already set and did nothing, leaving the notebook on cell one
with the match far below the fold.

Keyed on `state.loaded`, which flips false→true once and never again, both halves survive: it still
waits for the notebook to arrive, and nothing the landing does re-arms it.

The general shape is worth keeping: an effect whose body mutates state is only safe on keys that
body cannot touch. `EditorScreen`'s two neighbouring effects are keyed on `state.mode` /
`state.selectedCellId` and on `state.search?.current` - neither of which the effect itself writes.

### The open-document registry is **one** document, and that is a measurement (P2x-5b)

`docs/PLAN.md`'s first open risk said this was P2x-5's prerequisite and not a detail of it, and it
was right: `Navigation.kt` scopes ViewModels per `NavEntry`, so a document is reachable from its own
screen and nowhere else, while `BulkTextEdits` resolves a text model for **every** resource it edits
(`bulkTextEdits.ts:227`). `OpenDocuments` is that bridge. The risk is closed and lives here now.

**It holds one document rather than a map, and the reason is `NotebookBackStack`'s.**
`chooseDetailThatFitsTheSurface` removes the current detail entry before adding the next, so this
app's back stack *cannot* hold two documents at once - the reference needs a map because it has tabs
and split editors, and this refuses both. If a surface ever opens a second document it becomes a map
keyed by `OpenDocument.path` and nothing else about it changes.

**Implemented by the ViewModels rather than by an adapter around them**, because `path` has to be
read *live*: a rename moves the file while the document carries on, and the ViewModel is what learns
of that. And `closed()` clears **by identity**, because `chooseDetailThatFitsTheSurface` registers
the incoming document before the outgoing entry is disposed - clearing unconditionally there would
empty the registry while a document was on screen.

`p2x-5-replace-under-an-open-document` is the run that says the bridge works: two panes, the editor
never closed between the replace and the assertion, and the file read back afterwards.

### A replace-all empties the tree on success and leaves it standing on a refusal (P2x-5g)

`this.clear()` runs only in `replaceAll`'s resolve arm (`textSearchHeading.ts:299-303`); the reject
arm sets `replacingAll = false` and nothing else. Both halves are one rule - results describe a
workspace, and a refusal wrote to none of it - and neither is a preference: a tree left standing
after a successful replace would offer matches that no longer exist, and a tree cleared after a
refusal would hide the ones that still do.

### An undo brings the **results** back, where the reference's cannot (P2x-5g, P2x-5i)

VS Code undoes a replace-all with Ctrl+Z from any editor it touched; the search view never learns it
happened, so its tree stays cleared. Here the panel is what performs the undo, and
`WorkspaceReplace.undo` refuses unless **every** resource still reads as the replace left it
(`_checkWorkspaceUndo`, `undoRedoService.ts:853-869`) - so once it succeeds each file is byte for
byte what the walk read and the matches it found are exactly valid again.

Re-drawing them is also the only feedback an undo has. Nothing else on this panel would have
changed, and a panel that merely emptied would have said nothing at all - which is the same problem
the surface question below answers.

### The undo sits beside the report, and the choice was made on lifetime (P2x-5i)

Two Material idioms were available and a snackbar is the classic one for a reversible bulk action -
this app already uses a snackbar for an export's outcome. It was declined: a snackbar carries the
message **and the only route to the undo** away after a few seconds, where the reference's undo
stays available until the file is edited. Beside the report it lives exactly as long as the sentence
that offers it, which is `clearMessage()`'s own lifetime (`searchView.ts:1996`) - one undo, cleared
by the next walk.

There is no redo, and nothing asks for one: `MultiModelEditStackElement` has both and a panel can
reach one. And the offer is **absent** beside a refusal rather than disabled, which is the inverse of
the Replace All button below and for the stated reason - a disabled Replace All explains its own
precondition, and an Undo that wrote nothing has nothing to explain.

It is called *"Undo this replace"* and not *"Undo"*: `EditorTopBar` ships *"Undo cell operation"* and
at 1280dp that bar and this panel are two panes of one window, where *"Undo"* is a prefix of it.

### `truncateValue` is declined rather than transcribed (P2x-5f)

`buildReplaceAllConfirmationMessage` clamps a replacement of more than ten lines to ten and an
ellipsis (`searchView.ts:889-899`). The replace box here is `singleLine = true`, so a replacement
cannot hold a newline and the clamp could never fire. Transcribing it would be unreachable code,
which `CLAUDE.md` forbids; a multi-line replace box is what would bring it back.

The sixteen counted strings around it *are* transcribed, and by equality
(`ReplaceAllMessagesTest`), because the reference spells every plural combination out only because
`nls.localize` needs a key per literal - the arithmetic underneath is three substitutions.

### Material's `find_replace` reads as *refresh*, so Replace All is a labelled button (P2x-5h)

`Codicon.replaceAll` is typographic - the SVG read this phase is a `b` letterform, a curved arrow and
a box of letters - and Material's only transcription of the idea is `find_replace`, a magnifier
wrapped in a circular arrow. Shipped inline where the reference puts it (`.replace-container` is an
`inline-flex` with the action bar pushed onto it, `searchWidget.ts:583-586`, `searchview.css:90-115`)
and **drawn at 24dp on the tablet, it read as refresh** - eleven dp from a query box whose own
leading icon is the magnifier `find_replace` is built from. An action that rewrites every matching
file in the workspace may not be drawn as the one glyph that means *do it again, harmlessly*.

Neither the unit nor the instrumented suite could have said so. The screenshot did, which is the
fourth time this phase (`evidence/p2x-5-replace-all/`).

What ships is a `TextButton` on a **row of its own**. Two departures, each measured:

- **Type rather than a glyph**, which is P2x-3d's answer to a missing codicon (`Aa`, `ab`, `.*`)
  applied to an action - and the control Material gives type is a button, not a label.
- **Its own row**, because a label inline costs the box about 100dp, and this panel spends *height*:
  the same trade the replace chevron's own column made (P2x-5e) and the one that made the panel one
  scrolling surface (P2x-3e). The box keeps its full 296dp.

**The label is not the reference's.** VS Code says *"Replace All"* and `FindTopBar` already ships
JupyterLab's *"Replace all"*; at 840dp both are in one tree, two buttons a tap apart, one rewriting a
document and one rewriting every file in the workspace. *"Replace all in workspace"* carries the
scope that tells them apart - the rule `REPLACE_IN_WORKSPACE_BOX` was named under. The **dialog**
keeps the reference's strings word for word, because a modal dialog is a tree of its own and nothing
of the find bar is in it.

### Replace All is drawn disabled rather than hidden, and it is the exception this file allows (P2x-5h)

`docs/DECISIONS.md`'s standing rule is that a row which can only fail is a control that lies - it is
why the export formats nothing can produce are absent. A disabled Replace All is the case that rule
does not cover, because the reference's own label *explains the precondition*: `REPLACE_ALL_DISABLED_LABEL`
is *"Replace All (Submit Search to Enable)"* (`searchWidget.ts:118`). An absent control says nothing;
this one says exactly why it cannot be pressed and what would make it pressable.

On a phone that label is what a **screen reader** hears, where the reference raises it as a tooltip.
The visible text does not change with the state, so the row does not reflow as a walk settles.

### The bar is drawn wherever the shell draws **one pane**, not below 600dp (P2y-c)

`docs/PLAN.md`'s P2y row asked for a bottom navigation *"present below 600dp and absent at and above
it"*, which is Material's own breakpoint - navigation bars are for *"compact window sizes"*
(`developer.android.com/develop/ui/compose/components/navigation-bar`). **It is not this app's rail
rule.** The rail is drawn at `maxHorizontalPartitions > 1` (`PaneDirective.kt`), which is 840dp, so
the row as written left **600-840dp with neither control** - and that band is this project's own
tablet in portrait, measured at `w800dp` by P2o-3, where workspace search would have stayed
unreachable with no overlay to fall back to.

P2o-3 had already recorded the sharper rule and this row had not caught up with it: *"the problem is
not 'the compact window' but every window the shell draws as one pane"*. So the bar is `!twoPane`,
the rail is `twoPane`, and there is no width with neither.

**What that departs from is Material's bar/rail split, and it is a departure this app already made.**
`NavigationSuiteScaffold`'s documented default is a bar at compact and a rail at everything else;
this app has drawn its rail on pane count rather than width class since P2o-2, because the rail is
the activity bar *beside the side area* and a side area is what two panes means. Keeping the bar on
the same rule is the consistent half of that choice, not a second exception.

### The bar carries two destinations, which is below Material's own floor (P2y-a)

Android's guidance for `NavigationBar` is *"three to five destinations of equal importance"*. This
app's bar has two: Files and Search. The third panel, the table of contents, is deliberately **not**
on it, and P2o-4's own argument is why - Files is a screen you navigate *to* and an outline is a
surface over the document you are already *in*. Where the bar is drawn a panel **is** the window, so
there is no open document for an outline to belong to at all; its route at this width is the overlay
`toc:show-panel` opens from the notebook's menu, which `p2o-4-one-pane-side-panels` still proves.

Recorded rather than padded to three. A third item that is not a destination would satisfy the count
by breaking the sentence the count comes from, and `docs/PLAN.md` already names the real third:
P6d's Launcher.

### The bar is drawn over the panel and never over a document (P2y-c)

In one window `NavDisplay` composes only the top entry, so where the bar is *spelled* decides
whether it is drawn over an open notebook. It is spelled inside the `Explorer` entry.

Three things say so. Android's own rule is that a navigation bar carries top-level destinations, and
an open document is not one. P2o-4 measured the alternative: an 80dp bar is **20%** of the 400.0dp
cell list at 320dp, and P2q-2 had to fight for 48dp at that same width. And the screenshot showed
the half P2o-4 could not have known - **the notebook already carries its own bottom action row**
(`evidence/p2y-bottom-navigation/*/02-no-bar-over-the-notebook.png`), so an always-on bar would have
stacked two bottom bars on a phone.

The cost of scoping it is that Search is two taps from inside a document rather than one: back, then
the bar. Back is already one tap and `p2o-4` proves it.

### The bar's items carry no visible text, and two strings measured it (P2y-b)

Material recommends labels on a navigation bar. This one is icon-only, with the panel's name on the
item as a `contentDescription` - which is the rule `SidePanelRail` already had, for the reason a flow
cost this project in P2b: a screen reader and a Maestro flow address one string.

**Measured, not preferred.** `ExplorerScreen` titles itself **"Files"** and `SearchSidePanel` titles
itself **"Search"**. A labelled item sits directly under one of those, so each string would be on
screen twice at one pane: `onNodeWithText("Files")` throws on two matches and Maestro's `tapOn` picks
one of them silently. Keeping the names in semantics keeps every visible string unique - and it is
what makes the flow's own addressing work, since on the browser only the bar says "Search" and on the
search panel only the bar says "Files".

VS Code agrees: its activity bar is icon-only in `DEFAULT`, `TOP` **and** `BOTTOM`
(`vscode/…/parts/activitybar/activitybarPart.ts`). `SidePanelBarTest.theItemsCarryNoVisibleText`
pins it.

### The bar does not collapse, where the rail does (P2y-a, P2y-b)

Picking the showing panel on the rail collapses the side area - `collapse()` *"only deselects the
active tab, leaving the activity bar visible"* (`packages/application/src/shell.ts:2383-2394`), which
is what `SidePanel.chosen` models. The bar assigns instead. Beside a document a collapsed area is a
coherent state; at one pane the panel **is** the window, so collapsing it leaves nothing on screen.
It is the one behavioural difference between the two controls, and `SidePanelBarTest` and
`SidePanelRailTest` pin the pair from both sides so they cannot drift into agreeing.

### A one-pane window keeps a panel the bar can leave, which narrows P2x-2b (P2y-a)

P2x-2b forced every narrowed window to the file browser, because there was no control there to leave
a panel by: a tablet rotated from 1280dp to 800dp with a panel up could not reach the files again
without killing the app. With a bar there that override is too broad - a user who rotates mid-search
should not lose the search. `SidePanel.showingAtOnePane` keeps Files and Search and falls back only
for the table of contents, which has no item on the bar, and for the collapsed `null`.

P2x-2b's defect is not reopened; it is narrowed to exactly the case that still has it.

### Back reaches the start destination before it leaves the app (P2y-c)

Android's principle: the start destination *"is also the last screen the user sees when they return
to the launcher after pressing the Back button"* (`developer.android.com/guide/navigation/principles`).
The file browser is this app's root, so backing out of a one-pane Search selects Files rather than
exiting.

`NavDisplay` cannot do it - there is one entry on the stack and nothing to pop - so it is a
`BackHandler` in `MainNavigation`, enabled only where the bar is drawn, the panel is not already
Files, and the `Explorer` entry is on top. That last condition is what keeps Back belonging to the
document while one is open.

### The bar's arrangement is the reference's own sentence, and the tablet is what asked for it (P2y-d)

P2y-c drew one arrangement at both one-pane widths. Measured on the tablet in portrait, that was
wrong: each of two items was **396dp** wide with centres **404dp** apart - a 24dp icon inside a tap
target half the window across, with the two controls stranded at the quarter points.

`ShortNavigationBar`'s own KDoc (material3 1.4.0) states the rule: *"In small screens … its
arrangement should be `EqualWeight`, so that the navigation items are equally distributed on the
bar"*; *"In medium screens … its arrangement should be `Centered`, so that the navigation items are
distributed grouped on the center of the bar"*. `itemArrangementAt` is that, keyed on
`WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND` - so the width branch is the reference's, not an
adaptation of it. `ShortNavigationBar` is stable in 1.4.0, so this cost no experimental opt-in, and
at 64dp against `NavigationBar`'s 80dp it gave the panel 16dp back at both widths.

**The bar measures itself** with `BoxWithConstraints` rather than asking the window. At one pane the
pane *is* the window, so the bar's own constraints are the width the rule is about - and unlike
`currentWindowAdaptiveInfo()`, which reads the activity's window, a constraint is something a test
can set. `ActionRowPlacement` records what the alternative cost: a test that forces a width drives
one branch twice while appearing to cover both.

Measured after, on both devices (`evidence/p2y-bottom-navigation/measurement.txt`): 320dp gives
160dp items with centres at the quarter points; 800dp gives 200dp items in a 400dp group centred at
400dp, which is `calculateCenteredContentHorizontalPadding`'s own answer for two items.

### The emulator's storage and its parser are two steps, because the reference's tests are (P4e-1)

`docs/PLAN.md` gated **P4e** - "the screen buffer" - on seven Termux test classes. Counted in the
tree rather than assumed, five of them drive the *parser*, not the buffer:
`CursorAndScreenTest` (84 `enterString`/`withTerminalSized` call sites), `ScrollRegionTest` (51),
`RectangularAreasTest` (45), `ResizeTest` (40) and `UnicodeInputTest` (31), each of them zero direct
`TerminalBuffer` or `TerminalRow` construction. `ScreenBufferTest` is three-fifths the same. Cursor
addressing, the scroll region and ED/EL erase are `TerminalEmulator.java`; `TerminalBuffer.java`'s
whole public surface is `setChar`, `blockSet`, `blockCopy`, `scrollDownOneLine`, `resize`, the
transcript readers and the line-wrap flags.

So P4e is the **storage** - `CharacterWidth`, `CellStyle`, `TerminalRow`, `TerminalGrid`, one
sub-step each - gated by the three classes that reach it (`WcWidthTest`, `TextStyleTest`,
`TerminalRowTest`) plus `ScreenBufferTest`'s two buffer-level tests. The five escape-driven classes
are **P4f's** gate, where the reference itself puts them.

**This forbids** closing P4e on a test that types an escape sequence: if a gate needs
`enterString`, it belongs to the parser and the parser is a later step. It is also why `terminal/`
gets four files before it gets a caller - a storage layer whose tests all go through a parser is a
layer with no gate of its own.

### A selection takes a wide character whole, or not at all (P4e-4)

Two rules of `TerminalBuffer.getSelectedText` that this transcription got wrong on its first draft,
both now pinned in `TerminalGridTest` and both reached by the differential:

- A selection lying **wholly inside** one wide character yields the whole character. Both column
  ends map to that character's own start, so the span would be empty; the reference reaches one
  column further to break the tie.
- A selection **ending on** a wide character's first half **drops it**. Columns 0..1 over `a` then
  a wide character at columns 1-2 give `a` alone, because column 2 maps back to that character's
  start and the span closes before it.

**This forbids** "fixing" the second: it is the same rule as the first seen from the other side,
and a selection UI that wants the character has to ask for the column it also covers.

The trailing-space rule is the third: a row whose `lineWrap` is set and whose selection runs to the
last column keeps its trailing spaces, because a wrapped row and its continuation are one logical
line and the spaces between them are real text.

### `TerminalGrid` is three classes, and rows are addressed one way (P4e-4)

`TerminalBuffer.java` has nineteen functions where this project's ceiling is eleven, so it ships as
`RowRing` (the circular buffer and the external↔internal row map), `TerminalGrid` (the screen
written through it) and `GridSelection` (a region read back as text) - the same storage/behaviour
seam `RowText` and `TerminalRow` were cut along, for the same reason.

**`externalToInternalRow` is not public here**, where it is in the reference; `rowAt(externalRow)`
is. The reference exposes the slot index because its own tests use it, and a slot index is the
ring's business - nothing outside `RowRing` has any use for one. Ours take `rowAt`.

**And the three line-wrap wrappers are gone.** `setLineWrap`, `getLineWrap` and `clearLineWrap`
exist upstream because `TerminalRow.mLineWrap` is package-private there; `TerminalRow.lineWrap` is
a public `var` here, so `grid.rowAt(row).lineWrap` is the same access with no second API to keep in
step.

**Three members are deliberately absent**, each left to the step that owns its caller: `resize` to
P4h, `setOrClearEffect` to P4f's rectangular/DECSCA path, and `getWordAtLocation` to selection,
which nothing in P4 schedules. `TerminalGrid` is at eight functions rather than eleven so P4f has
somewhere to put the second of those.

One behaviour is stricter than the reference and is a strict improvement: `GridSelection` reads
rows through `rowAt`, which allocates a blank row, where the reference indexes `mLines` directly
and would dereference null. The reference's own `getStyleAt` already allocates.

### A transcription of an algorithm is diffed against it, not reviewed against it (P4e-3)

`TerminalRow.setChar` is 117 lines upstream at a complexity detekt refuses, so it ships here as
nine functions across two classes. Twenty-six transcribed assertions are a thin oracle for a
rewrite of an array splice, so both implementations were **run against the same scripted
sequence** - `java.util.Random` is specified, so a seed produces the same stream in Java and in
Kotlin - and their entire observable state compared after every operation: `spaceUsed`, `isBlank`,
every java char in hex, all 41 column starts, all 40 styles.

12,000 operations over three seeds, **byte-identical**
(`evidence/p4-terminal/row-differential.md`).

Four mutations were run to show the comparison can fail, and the two that survive are explained
rather than shrugged at: the combining cap is out of the script's reach - measured, it never stacks
more than **four** marks on one column - and the array's growth chunk is capacity, which no caller
can observe.

**And the first script was not enough, which the method itself showed.** It drove only `setChar`
and `clear`, so `copyInterval` - the method this transcription changed most - had no coverage
beyond two unit tests until it was added. It is identical with ~1,275 of them in.

**This is the standard for P4f-P4h**, where `TerminalEmulator.java` is 2,617 lines and the same
decomposition pressure applies: a transcription of an algorithm is closed by a differential against
the source, and the transcribed unit tests cover what the differential's inputs cannot reach.

### `RowText` is the seam a 117-line method had to be cut along (P4e-3)

detekt found four things in the first draft and each answer is a design change rather than a
suppression - this repo carries no detekt suppression and does not start here.

`TooManyFunctions` (17 against 11) is the real one. Decomposing one long method necessarily makes
many short ones, so `LongMethod` and `TooManyFunctions` pull against each other for any dense
algorithm. The split that resolves it is a real boundary rather than an arithmetic one: **where
does column N live in this char array, and how do I make room there** is not **what code point and
style is column N**. `RowText` owns the first - the packed array, `findStartOfColumn`, the splice,
the shift, the grow - and `TerminalRow` owns the second. Both land at exactly 11 functions, which
is a ceiling to notice when P4f adds to either.

The other three: `ReturnCount` twice, answered by folding two exits into one expression each -
`excludeGuardClauses` is **false** by default in detekt 2.x, so a leading `return` is counted;
`LongMethod` on `copyInterval`, answered by making the read and write columns one cursor and an
offset, and by clearing the second-half-of-a-wide flag unconditionally, which is equivalent because
it can only be true on the first iteration. `PackedText.kt` came out of the same pass: the
surrogate-pair decode appears four times across the two classes where the reference inlines it each
time.

Every one of those changes was re-run through the differential, which is what makes them safe to
make at all.

### `decodeEffect` reads two bits `encode` never wrote, and the mask is narrowed (P4e-2)

`TextStyle.encode` masks the effect to nine bits (`0b111111111`, [BOLD]..[DIM]) and then sets the
two truecolor markers at bits 9 and 10 itself. `decodeEffect` reads **eleven**. So a cell with a
24-bit colour reports a marker as an attribute. **Measured, not argued**: transcribed with the
upstream mask, `CellStyle.encode(0xFF808080, 0, 0).effect` returns **512**, and the narrowed mask
is what makes it 0.

Upstream's own tests walk straight past it, which is why it is worth writing down.
`testEncodingSingle` sweeps every indexed pair and every effect - and indexed colours set neither
marker. `testEncoding24Bit` is the only test that sets them, and it never asks for the effect back.

`CellStyle.effect` therefore masks nine bits, matching what `encode` wrote.

**This forbids** reading an attribute out of a style with a raw `and` against an eleven-bit mask,
and it forbids adding a tenth attribute bit without moving the two markers - `CellStyleTest` fails
on both, since it asserts each attribute is a distinct single bit below `1 shl 9`.

Two further mutations were run and **survived, correctly**: swapping `FOREGROUND_SHIFT` with
`BACKGROUND_SHIFT`, and detecting truecolor on any non-zero alpha rather than on an opaque one.
Both are equivalent mutants - the layout never escapes the value class, and no reachable colour
here carries partial alpha - so no test was invented to kill them. What *is* pinned instead is the
one place the encoding does escape: `CellStyle(0L)` is palette index 0 on both sides with no
effect, because `ScreenBufferTest` and `TerminalGrid`'s callers pass a literal `0` as a style.

### `CharacterWidthRanges` is packed, and a test checks the packing (P4e-1)

`WcWidth.java` is 566 lines because its two interval tables are an `int[][]` literal at one range
per line - `ZERO_WIDTH` alone is 343 ranges. This project's ceiling is 500 non-blank lines
(`scripts/check-file-line-length.sh`), so the tables are transcribed as flat `intArrayOf(low, high,
…)`, four ranges per line, into a file of their own: 146 lines for both.

That is a shape change, and a shape change to 464 hand-moved numbers needs its own gate, because
the nine spot checks `WcWidthTest` carries would pass over a truncated or misordered table.
`CharacterWidthTest` therefore also asserts that every pair has `low <= high`, that both tables are
ascending and disjoint - which is what makes the binary search correct at all - and that they hold
**343** and **121** ranges. Four mutations were run rather than trusted: indexing the search by row
instead of by pair, dropping the C0/C1 guard, deleting the last `ZERO_WIDTH` range, and swapping one
pair's endpoints. Each fails at least one test.

**Three tests exist that upstream has none of**, and they cover code upstream only reaches through
`TerminalRow`: `CharacterWidth.at` over a surrogate pair, `zeroWidthCountIn`, and the C0/C1 rule -
which is the one place this file departs from `wcwidth(3)` (0 rather than -1) and had nothing
pinning it.

### DEC Special Graphics `d` and `h` are control pictures, not the controls (P4f-1)

`TerminalEmulator.java`'s line-drawing table maps `d` to `'\r'` and `h` to `'\n'` — the literal
control characters. Transcribed as-is that is a **defect here specifically**: `CharacterWidth`
scores a C0 control 0, so a zero-width `\r` written through `TerminalScreen.writeCodePoint` lands
as a *combining mark on the previous cell* rather than as a glyph of its own. The mapping is to
`U+240D SYMBOL FOR CARRIAGE RETURN` and `U+2424 SYMBOL FOR NEWLINE`, the Unicode control pictures
the graphics actually are.

Settled against `vttest`'s own character-set page
(`reference/research_repos/jediterm/core/tests/resources/testData/vttest/Test3_Characters/1`)
rather than by preference, and the three positions where Termux and JediTerm disagree each have a
test in `LineDrawingCharsetTest`.

**This forbids** "restoring parity" by writing the control characters back, and it is the one
divergence the P4f differential has to exclude by hand: reaching it needs `ESC ( 0`, which is
P4f-3, so P4f-3's script must exclude it **explicitly** rather than let it pass because nothing
emits it.

### SGR is P4f-2's, and the P4g row that claimed it was wrong (P4f-2)

`docs/PLAN.md`'s P4g row read *"SGR to attributes, and the sixteen ANSI colours…"*. Checked against
the tree, the first half cannot live there: SGR **is** a CSI sequence, so anything that applies it
needs the parameter buffer, the CSI state and the final-byte dispatch — which is the whole of P4f's
machine. Two places in the tree had already scheduled it correctly (`TerminalScreen.kt`'s
`currentStyle` KDoc and `PlainTextInputTest`'s), and the row had not caught up.

What is left in P4g is a palette question with no parser in it: what index 1 **is**, in ARGB, out of
VS Code's `terminalColorRegistry.ts` registered defaults. P4f-2 settles only that it is 1.

**This forbids** resolving a colour inside `terminal/`: `GraphicRendition` writes indices and 24-bit
values into a `CellStyle` and never a theme colour, which is also what keeps `terminal/` free of
`theme/` and under the palette-consumers gate when P4g arrives.

### `58` and `59` are parsed and thrown away, and parsing them is the point (P4f-2)

The reference stores an underline colour in `mUnderlineColor`. Measured rather than assumed:
`getStyle()` is `TextStyle.encode(mForeColor, mBackColor, mEffect)` — three fields, and that is not
one of them — and a grep across `terminal-emulator/` **and** `terminal-view/` finds four writes and
no reads. It is a field nothing in the reference has ever used, and `CellStyle`'s 59 occupied bits
have nowhere to put it.

So `58` and `59` change nothing here. But they are still **parsed**, and dropping the codes would be
a defect rather than a simplification: `58`'s arguments would then be read as codes of their own, so
`58;5;1` would arrive as blink and bold. `GraphicRenditionTest.anUnderlineColourIsParsedAndDiscarded`
is what stops someone deleting the branch as dead.

The same reading explains a quirk that looks like a bug and is parity:
`anInvalidExtendedColourFormIsReadAgainAsACodeOfItsOwn`. An extended colour whose form is neither
`2` nor `5` consumes **nothing**, so the walk reads its arguments again — `48;8;129` ends up
invisible, because the `8` that named the bad form is read a second time as SGR 8.

### A final byte P4f-2 does not implement is swallowed, never printed (P4f-2)

The machine ships whole and the vocabulary arrives sub-step by sub-step. That leaves a choice for
every sequence the machine can *reach* and not yet act on, and the answer is that it is consumed
with its parameters and nothing reaches the screen. The alternative — end the sequence at the
unknown byte — puts `2J` on the shell's screen as text the first time anything clears it.

Three consequences, all transcribed from the reference's routing rather than generalised from
ECMA-48:

- The private parameter bytes `< = > ?` go to one swallowing state, and the intermediate bytes
  `! " ' $ *` and space to another — **exactly the six the reference gives a state of its own**.
  Every *other* intermediate byte ends the sequence, which is `parseArg`'s own fall-through and not
  a rule of ECMA-48. Transcribed as it stands, so the two agree byte for byte.
- `ESC` followed by anything but `[` ends the sequence taking only itself. That is right for the
  introducers the reference does not implement and **a declared, temporary divergence** for the
  twelve it does; `EscapeSequenceTest` says so at the test that pins it, and P4f-3 replaces it.
- Neither is in the differential's reach, on purpose: the reference *implements* those bytes, so
  emitting one would compare a divergence this step declares against a defect. The evidence file
  states the exclusion.

**This forbids** closing a later sub-step without deleting the tests that pin the swallowing it
replaces — they are named "changes at P4f-3" / "P4f-4" where they stand.

### The differential's state dump gained the styles, and it was hiding all of them (P4f-2)

P4f-1's dump printed the cursor, the bell count, the transcript depth and every live row's
characters and line-wrap flag. It did not print a single **style**, which was harmless while
`currentStyle` was `CellStyle.NORMAL` for the whole run and useless the moment SGR existed: two
implementations disagreeing about every colour on the screen would still have printed the same
characters.

Styles are now dumped **run-length encoded per row**. Spelled out per column they multiplied the
dump tenfold for a value that changes far less often than a character does; encoded, the dumps land
at ~28 MB and lose nothing.

This also gave the write path's style argument its first exercise: until now `setChar`, `blockSet`
and `scrollDownOneLine` were compared with one style throughout, so a transcription that had dropped
the argument would have passed all 12,000 operations.

**The general rule, and it is the one P4e-3 set seen from the other side**: a differential compares
what it prints. When a sub-step adds a *field* to the state, the dump gains it in the same commit —
otherwise the script grows while the oracle does not.

### The screen is three classes, and detekt named both seams (P4f-3a)

`TerminalScreen` reached **20 functions against detekt's 11** the moment the `ESC` dispatch had
somewhere to put its operations. Raising the threshold was not on the table — every other class in
this tree that hit `TooManyFunctions` was split instead (`CellFolds`, `CellSplit`, `NotebookKernel`,
`WorkspaceReplaceAll`) — so the question is where the two cuts go, and detekt was pointing at real
seams both times.

- **`CharacterPlacement`** takes the write path: `write` and the four helpers that exist only for
  it. Nothing else in the tree calls `wrapBeforeWriting`, `wrapToNextRow`, `makeRoomForInsert` or
  `columnToWrite`, where every *other* operation on a screen is one a control character or an
  escape sequence asks for by name. Upstream this is one 150-line `emitCodePoint`.
- **`EscapeDispatch`** takes the `ESC` operations themselves — NEL, RI, RIS, DECBI, DECFI and the
  alignment test — which is **where the reference puts them too** (`doEsc` does the work inline).
  Each is reachable from an `ESC` sequence and from nowhere else, so a screen would be carrying
  them for one caller apiece. Three of the nine go elsewhere for a reason of their own: DECSC and
  DECRC stay on the screen because `CSI s` and `CSI u` reach them as well (P4f-3b), and lower-left
  is a cursor motion with no grid in it, so it is `TerminalCursor.moveToLowerLeft`.

What is left on `TerminalScreen` is the state a terminal has and the operations a **control
character** names: BS, HT, LF, CR, the scroll they can cause, and the reset.

**This forbids** a later sub-step growing `TerminalScreen` back: P4f-3b's cursor sequences belong to
its own applier over `TerminalCursor`, and P4f-3c's erases and edits to theirs. It also forbids
`EscapeParser` reaching into `TerminalGrid` — the parser routes, and the two operation classes are
what touch the grid, which is the JediTerm seam `TerminalScreen`'s own comment states.

### Two `ESC` sequences are transcribed as the reference's comments read, not as its code runs (P4f-3a)

Both were **measured against the shipped reference** rather than argued from its source, with a
scratch oracle that prints what Termux's emulator makes of one input string; the same oracle is
where every expectation in `EscapeDispatchTest` came from.

- **`ESC F` — cursor to lower left.** `doEsc` calls `setCursorRowCol(0, mBottomMargin - 1)`, whose
  parameters are `(row, col)`. On a 4×3 screen the reference therefore lands at **row 0, column 2**
  while its own comment on the line says *"Cursor to lower-left corner of screen"* — the two
  arguments are transposed. xterm's `ctlseqs` is unambiguous (*"ESC F — Cursor to lower left corner
  of screen"*), so `TerminalCursor.moveToLowerLeft` is `moveTo(bottomMargin - 1, 0)`.
- **`ESC c` — RIS.** The reference's `reset()` assigns `mForeColor` and `mBackColor` and **never
  `mEffect`**, so upstream `ESC [ 1;4m . ESC c .` leaves the second character bold and underlined;
  measured at effect `5` on the shipped build. RIS is a *hard* reset — xterm's `ctlseqs` calls it
  "Full Reset" — so `TerminalScreen.reset` clears the whole style, effects included.

**Both are excluded from the differential's alphabet by name**, which is the same discipline the
`d`/`h` line-drawing divergence set at P4f-1: a divergence a step declares must not be emitted, or
the comparison scores it against a defect. `evidence/p4-terminal/emulator-differential.md` states
the exclusions and `EmulatorDump.ESCAPES` carries them as a comment beside the omission.

**This forbids** "restoring parity" on either, and it forbids adding `F` or `c` to the differential
without also removing the divergence.

### Two P4f-3b behaviours are the reference's minus a mode nothing can set yet (P4f-3b)

Both are places where transcribing the reference *exactly* would mean writing code against state
that does not exist, and both are written as the reference's own **behaviour with that state off**
rather than as a guess at it.

- **`CSI s`.** Upstream it is `if (DECLRMM) setLeftAndRightMargins() else saveCursor()`. DECLRMM is
  `CSI ? 69 h`, which is P4f-4, and the margins it would set are P4f-5 — so at P4f-3b the guard has
  exactly one reachable arm and `CSI s` saves the cursor. P4f-5 adds the other arm with the test
  that can finally tell them apart.
- **The six sequences that respect origin mode** — CUP, HVP, CNL, CPL, HPA and VPR all reach the
  cursor through `setCursorPosition`, whose effective margins are the scrolling region under DECOM
  and the whole screen without it. DECOM is P4f-4. What is written is the clamp to the screen,
  **spelled as a clamp** rather than read off `TerminalCursor.topMargin`/`leftMargin`: those are
  identical today and would stop being identical the moment P4f-5 can set a margin, which is the
  kind of silent change a differential cannot catch because both sides would move together.

**This forbids** "simplifying" either into the margins it happens to equal, and it forbids P4f-4
or P4f-5 landing without revisiting both — `CursorAddressing`'s KDoc names them, and so does
`docs/PLAN.md`.

### An erase blanks to the colours in force, and two of the ten edits leave the pending wrap alone (P4f-3c)

Two asymmetries that look like bugs, are parity, and would each be "tidied away" by anyone
regularising the ten sequences into one shape.

**What an erase blanks a cell *to* is the current style.** The reference's `blockClear` passes
`getStyle()`, not the terminal's defaults, so `ESC [ 48;5;129 m ESC [ 2 J` paints the whole screen
that background — background-colour erase, which every full-screen program relies on and
`TerminalTest.testBackgroundColorErase` pins. `ScreenEditing.blockClear` is the one place this is
decided, and it reads `screen.currentStyle`.

**Six of the ten clear the pending wrap; two do not, and one does so conditionally.** ED and EL
clear it *only when their parameter was one they understood* — upstream the clear is the line after
the inner `switch`, and the unknown arm `return`s before reaching it. IL never clears it and SU, SD
and REP never touch it. So `eraseInDisplay` and `eraseInLine` return a `Boolean` and the caller
clears on it, which is the reference's own control flow rather than a shape invented here.
`CursorAndScreenTest.testClearingOfAutowrap` covers the six that clear; **the conditional half is
only visible with a parameter nothing claims**, which is why `9J` and `9K` are in the differential's
alphabet.

**REP stores the code point, not the glyph.** `CharacterPlacement` records
`lastEmittedCodePoint` *before* the graphic set substitutes, and `repeatLastCharacter` writes it
back through `writeCodePoint`, which substitutes again. Storing the already-substituted glyph looks
equivalent and saves a lookup - and is, right up until the set changes between the character and
the repeat: `ESC ( 0 a ESC ( B ESC [ b` draws `▒a` on the reference and `▒▒` with the glyph stored.
**The differential could not see this** - it needs four steps in one order - so it is
`ScreenEditingTest.repeatDrawsThroughTheGraphicSetInForceWhenItRunsRatherThanTheGlyphItDrew` that
holds the line, and that test was run against the mutation to watch it fail.

**This forbids** hoisting the pending-wrap clear out of the ten into one place, it forbids
`blockClear` taking `CellStyle.NORMAL` "because a blank cell has no colour", and it forbids
`lastEmittedCodePoint` moving to after the substitution.

### A mode "save" marks the flag set, and a cursor save carries exactly two flags (P4f-4a)

Three things about the modes that read as bugs, are the reference's behaviour, and would each be
"corrected" by anyone implementing from the specification rather than from the source.

**`CSI ? Pm s` records no value.** The name says save, and DECSET's own documentation says save,
but the arm is `mSavedDecSetFlags |= internalBit` with no read of the current state - so the flag
is *marked*, not saved, and the `CSI ? Pm r` that follows always turns the mode **on**. Measured on
the shipped reference rather than read off it: `?7l ?7s ?7r`, `?7l ?7r` and `?7s ?7l ?7r` all leave
wrapping on. `ScreenModes.mark` is named for what it does, and
`ScreenModesTest.savingADecFlagMarksItSetRatherThanRecordingTheValueItHad` pins it.

**DECSC and DECRC carry two DEC flags and only two** - autowrap and origin mode - because
`restoreCursor` masks the saved flags down to that pair before putting them back. So `SavedCursor`
stores those two and not the other ten: the rest would be state nothing ever reads.
**The differential is what found this**, not a reading of the source - the first defect it has
caught in this phase (`evidence/p4-terminal/emulator-differential.md`, "the differential found a
defect the unit tests did not"). It is also why the sub-step plan moved the flags from P4f-4b to
P4f-4a: they are needed the moment a mode can change, not when a second buffer exists.

**DECCOLM does not change the width, and keeps its side effects anyway.** The reference does not
resize on `CSI ? 3 h` either, but it still resets all four margins, turns DECLRMM off, erases the
screen and homes the cursor - because `reset(1)` leans on exactly those. Dropping them "since the
width is unchanged" is what breaks a shell's reset.

**This forbids** making `mark` read the current value, storing the other ten flags in
`SavedCursor`, and reducing DECCOLM to a no-op.

### The scrollback belongs to the main screen, and the saved cursor to whichever is live (P4f-4b)

`CSI ? 47`, `? 1047` and `? 1049` reach **one arm** in the reference, and it is not the arm the
three numbers historically mean: xterm distinguishes them - `1049` saves the cursor where `47` does
not - and Termux does not. Transcribed as it stands, because a differential against a reference this
codebase then diverges from is no longer a differential. `1048` is DECSC and DECRC under another
number, with no switch in it.

**The transcript is the main screen's, whichever buffer is live.** `CSI 3 J` and RIS both name
`mMainBuffer` upstream rather than the current pointer, so a full-screen program that clears the
scrollback from inside the alternate screen clears the one the user sees when it exits. Measured on
the shipped reference rather than read off it: `a b c d ESC[?1049h ESC[3J ESC[?1049l` leaves the
transcript at `y\nz`, and the same input with the `3 J` removed leaves it whole.
`ScreenBuffers.clearTranscript` is therefore the *only* way either reaches a transcript, and both
call sites go through it.

**There is one saved cursor per buffer**, picked by which is live at the moment of the save - which
is what makes entering and leaving carry the cursor: `saveCursor` runs *before* the swap and
`restoreCursor` *after* it, so both land on the main screen's slot. A DECSC inside the alternate
screen writes the other slot and is invisible to the main one.
`AlternateScreenTest.theSavedCursorBelongsToTheBufferItWasSavedIn` pins it, expectations read off
`RefScreen`.

**RIS inside the alternate screen stays there.** `reset()` clears *both* saved cursors and leaves
the buffer pointer alone, so a shell that resets while in the alternate screen does not fall back to
the main one - it clears the alternate screen and the main screen's scrollback and stays put.

**The alternate screen is built with exactly as many rows as the screen**, so nothing ever reaches
its transcript - `RowRing` grows history only while `activeTranscriptRows < totalRows - screenRows`.
That structure *is* the behaviour, and `EmulatorFixture.assertInvariants` asserts the behaviour
rather than the structure: after every write, an alternate screen that is live has no history.

**What is not here:** the reference resizes when the two buffers disagree on size. Nothing resizes
until **P4h**, which owns `resize`, so that branch is unreachable and is left to the step that makes
it reachable rather than written blind - the same call P4f-4a made for origin mode's clamp.

**This forbids** giving `47`/`1047`/`1049` three different behaviours, routing a transcript clear
through `screen.grid`, sharing one saved cursor between the buffers, and making a reset swap back.

### Backspace tests `>` where the reference tests `==`, because the reference can crash (P4f-5a)

**The first divergence in this phase that is not "the reference does something a spec-reader would
correct" but "the reference is wrong".** `CSI ? 69 h` then `CSI 2 s` sets a left margin of 1 and
homes the cursor to column 0 - *outside the margin it just set*, because DECSLRM's home ignores the
margins when origin mode is off. The reference's backspace then tests `mCursorCol == mLeftMargin`,
which is false at column 0 with a margin of 1, so it decrements into column **-1**. It clamps that
for the write (`if (column < 0) column = 0`, under a comment guessing at a thread race) and
**throws** on it for the insert-mode `blockCopy`.

The differential found it, not a reading: the widened script aborted the *Java* side, and this
aborted with it, because the transcription was faithful. `>` instead of `==` differs from the
reference only when the cursor is already left of the margin - the state it cannot represent - and
is the same predicate in every other case, margin or none. The truth table, the minimal repro and
the two rejected fix sites are in `evidence/p4-terminal/backspace-past-the-left-margin.md`.

**This forbids** fixing it at `blockCopy` instead, which would keep a cursor position no terminal
can hold and merely hide the abort, and fixing it at DECSLRM's home, which would change where every
DECSLRM leaves the cursor - behaviour the reference gets right.

**It also costs the differential three alphabet entries**, excluded by name: `2s`, `2;3s` and `9;9s`.
Five mutations become unreachable by the script as a result, and each is pinned by a test run
against it - including CBT's two, which a one-column DECSLRM region makes observable.

### Exactly two C0 controls ask the escape state, and the guard is one state narrower than "a string is in progress" (P4f-6b-1)

**The emulator's rule since P4f-1 was that C0 controls are dispatched before the escape state is
looked at.** That is right for six of them and wrong for two, and the OSC string is where the
difference becomes visible: a `BEL` **ends** the string where it would otherwise ring the bell, and
an `ESC` opens the two-byte `ESC \` terminator where it would otherwise start a new sequence. The
reference guards exactly those two (`TerminalEmulator.java:583-588` and `:633-643`) and leaves
`BS`, `HT`, `LF`, `VT`, `FF`, `CR`, `SO` and `SI` acting on the screen while the string collects -
so `ESC ] 0 ; xy CR z BEL` returns the carriage and sets the title to `xyz`.

**Both guards read `mEscapeState == ESC_OSC`, which is narrower than "a string is in progress".**
One byte after an unresolved `ESC` the machine is in `ESC_OSC_ESC`, where a `BEL` **rings** - and
the byte after *that* is still read as `doOscEsc`'s default, so the `ESC` reaches the string anyway.
Measured on the shipped reference rather than read off it, because the source reads either way:

```
printf '\033]0;q\033\007r\007' | RefScreen 10 2
# title = q<ESC>r          bells = 1
```

`EscapeParser.isCollectingString` is therefore `state == OperatingSystemCommand` and explicitly not
`OperatingSystemCommandEscape`. There is a mutation for the wider predicate and the differential
detects it.

**This forbids** the two shapes that look tidier and are wrong: routing every C0 through
`EscapeParser.accept` first, which would stop a carriage return inside an `OSC` from returning the
carriage; and defining the guard as "any string state", which would make a `BEL` after an `ESC` end
the string. **P4f-6c and P4f-6d may not widen this predicate** - the reference's DCS guard is
`== ESC_P` and *ignores* the `ESC` where this one collects it, and its APC path runs before the C0
switch is reached at all, so each of those steps needs a predicate of its own.

### `RefScreen` reports what the notices carried, not only what the screen holds (P4f-6b-1)

**A transcribed expectation is only as good as what the oracle prints**, and until this step
`RefScreen` printed the screen, the cursor, the transcript, the styles and the replies. None of them
moves when a shell sets a window title, copies to the clipboard or changes a colour - the three
things the whole `OSC` family exists to do.

It now also prints `title =`, `titles =` (every old-to-new pair, so a set that changed nothing is
distinguishable from one that did), `bells =`, `clipboard =` and `colorsChanged =`. **The last two
are added before they have a producer on purpose**: P4f-6b-2 and P4f-6b-3 need them, and an oracle
that changes shape mid-family is one whose earlier measurements cannot be re-run against its later
form.

The same blind spot existed in the differential dump and the same fix closed it - `title=` for the
value and `titled=` for the count. **The count is not redundant**: a title set to what it already
was is invisible in the value, and one mutation survived on exactly that until `titled=` existed.

### The colour defaults are a constructor argument, so P4g stays a palette question (P4f-6b-2)

**Upstream's `TerminalColors.COLOR_SCHEME` is a process-global mutable static** - one array shared
by every emulator, rewritten in place by `updateWith(Properties)` from Termux's own theme file.
Transcribing that shape would have made the ANSI ramp a property of the *process*, and this app's
ramp is a property of the **theme**: VS Code registers `terminal.ansiGreen` as `#107C10` in light
and `#0DBC79` in dark (`terminalColorRegistry.ts:120-127`), and `terminal/` may not import from
`theme/` to ask which is in force.

So `TerminalPalette` is a value and `TerminalEmulator` takes one, the way it already takes
`cellWidthPixels`. **This step owns the mechanism and P4g owns which colours** - the mutable table,
the XParseColor parser, and set / query / reset are here; resolving VS Code's ramp per theme and
handing it in at the composition root is P4g's, unchanged and un-anticipated.

It is also what makes the differential possible at all. The comparison runs against the shipped
reference, so the Kotlin side has to be able to hold **Termux's** scheme; a hard-coded VS Code ramp
would have made every colour byte differ by construction. `TerminalPalette.REFERENCE` is that
scheme, and `TerminalColorsTest` pins it to the reference's own table.

**This forbids** reaching the ramp through a global, a service locator or an `object` singleton
inside `terminal/`, and it forbids P4g changing `TerminalPalette.REFERENCE` - that value is what the
differential compares against, and P4g adds a second palette rather than editing this one.

### `OSC 104` is fixed rather than transcribed, because an ordinary prompt reaches its crash (P4f-6b-2)

**The fourth reference defect this phase has found, and the first that needs no exotic mode.**
`printf '\033]104;1;\007'` - a colour reset with a trailing semicolon - throws
`StringIndexOutOfBoundsException`, and `printf '\033]104;259\007'` throws
`ArrayIndexOutOfBoundsException`. Two unguarded failures in one arm: a `break` placed inside a `try`
that swallows `NumberFormatException`, so the loop walks past the end of its own string; and an
unchecked array write, in an arm whose sibling `OSC 4` checks its index.

`ColorCommands.resetIndexed` splits on `;` and applies each token independently, skipping one that
is not an index and one outside the table. It agrees with the reference on **every input the
reference survives** - measured, all nine forms - and where the reference throws it does what that
`catch`'s own comment says: `// Ignore.`

**Why fixed rather than shared**, where P4f-6a let both sides throw together first: the trigger is
not exotic. A shell prompt, a colour-scheme script or a `tmux` restore emits `OSC 104` in the
ordinary course of resetting a palette, and the whole emulator would die on it.
`evidence/p4-terminal/the-colour-reset-that-walks-off-its-own-string.md` has the reproductions.

**This forbids** guarding only one of the two - the loop and the index fail for different reasons
and either alone still crashes - and it forbids widening `OSC 4`'s index guard to match, or
narrowing `OSC 104`'s to match `OSC 4`: the two arms **disagree on purpose enough to be measured**,
`OSC 104 ; 258` resets the cursor colour and `OSC 4 ; 258 ; #FFFFFF` is refused.

**It also costs the differential eight alphabet entries**, excluded by name: `104;1;x`, `104;x`,
`104;;`, `104;1;`, `104;99999999999`, `104;259`, `104;999` and `104;-1`. Two tests whose
expectations are **ours rather than the reference's** cover them -
`OperatingSystemColorsTest.aResetSkipsATokenThatIsNotAnIndexRatherThanWalkingOffTheString` and
`aResetSkipsAnIndexOutsideTheTableRatherThanThrowing`.

## Harness

### A `LazyColumn` composes past its own edge, so "visible" is not "on screen" (P2x-4d)

`p2x-4-open-at-the-cell` asserted the matched cell was visible after the tap and **passed against a
notebook sitting on cell one**, the match fourteen cells below the fold - the screenshot is what
said otherwise (`evidence/p2x-4-open-at-the-cell/00-…`).

This is the same blindness `p2x-3-open-at-the-match` recorded for the file editor, reached from the
other direction. There the editor is one node carrying every line, so any text in it matches at any
scroll position. Here the list *is* one node per cell - which is why a collapsed section is a real
assertion, since its cells are not in the tree at all - but a `LazyColumn` composes and keeps a
margin beyond its own viewport, and a composed-but-clipped item is still in the semantics tree.

What can see it is the **other end of the notebook**: `assertNotVisible: "filler_1 = 1"` names the
second cell, which is on screen exactly when the landing did not happen. That line and
`LandingOnAMatchTest` both fail on the defect; the `assertVisible` on the match itself never did.

### Where the editor is scrolled to is a thing no Maestro flow can assert (P2x-3f)

`p2x-3-open-at-the-match` asserted the matched line was visible after the tap and **passed with the
editor on line 1**, the match forty-six lines below the fold: the editor is one node carrying the
whole file, so any text in it matches at any scroll position.

The gutter was the next idea and is not one either - line numbers are *drawn* by
`Modifier.lineNumbers`, not composed as nodes, so `assertVisible: "47"` fails against an editor
showing line 47 perfectly well. Written, run, and removed on the measurement.

What can see it: the **screenshot**, and an instrumented test reading the node's own
`VerticalScrollAxisRange` (`FileEditorRevealTest.theMatchIsScrolledOnToTheScreen`, red first at
*"the editor never scrolled off the top"*). The flow keeps the file assertion, states in its own
header that it cannot assert the landing, and names the two things that do. This is the working
agreement's *"a green log is only half the evidence"* in one step.

### A lazy list is what a two-pane test cannot lay out, and a `Column` was hiding it (P2x-3e)

`SidePaneNavigationTest` drives the shell under `DeviceConfigurationOverride.ForcedSize`. This file
already records that `currentWindowAdaptiveInfo()` reads the *activity's* window and not the
override; what it did not record is the consequence for **layout**. Measured when the search panel
became a `LazyColumn`:

    box         = Rect(0, 0, 0, 0)
    panel list  = Rect(0, 0, 0, 0)
    rail Search = Rect(0, 40, 20, 54)

The rail is laid out and the whole list pane is **zero by zero**. The test used to read a result row
in that pane and `assertIsDisplayed()` on it - which passed because a `Column` lays its children out
anyway, overflowing a parent with no room. A `LazyColumn` composes only what fits, so the same rows
stopped existing, and the assertion that had been passing turns out to have been describing
something no reader could have seen.

So the claim was split rather than weakened: the shell test asserts the **query** survives the rail
switch - a rebuilt `WorkspaceSearchViewModel` starts at `SearchQuery("")`, so surviving text is a
surviving instance - and `SearchSidePanelTest.whatWasFoundSurvivesThePanelLeavingComposition` asserts
the **results** survive, hosting the panel on its own where it has a window to be laid out in.

### A scroll action inside a `waitUntil` hangs the whole instrumentation (P2x-3e)

Reaching a row in the panel's lazy list needs `performScrollToNode`, and the first two attempts put
it inside the wait that gives the walk time to finish:

    compose.waitUntil(TIMEOUT) { runCatching { list.performScrollToNode(what) }.isSuccess }

Run that way the suite **stopped**: `TestRunner` logged the test started, the app process sat at
`R` with no timeout ever firing, and the run had to be killed. Both shapes did it - `waitUntil` and a
plain bounded loop - because the hazard is the action, not the loop: `performScrollToNode`
synchronises on idleness, and while a walk is in flight the panel's `LinearProgressIndicator` is
animating, so idle never arrives.

`ui/ScrollUntilFound.kt` is the shape that works, and it is written with that reading in its own doc
so the next person does not re-derive it from a hung device.

### The local gate does not compile `androidTest`, and it went green over a file that would not build (P2x-2)

Measured in P2x-2, not reasoned about: an instrumented test file with an unresolved reference
survived the whole documented gate -

    ktlintCheck ktlintMainSourceSetCheck ktlintTestSourceSetCheck
    ktlintAndroidTestSourceSetCheck detekt lintDebug testDebugUnitTest assembleDebug

`BUILD SUCCESSFUL`. The next `connectedDebugAndroidTest` failed at
`compileDebugAndroidTestKotlin` with `Unresolved reference 'value'`.

**Every task in that list stops short of the source set.** `assembleDebug` compiles `main`;
`testDebugUnitTest` compiles `test`; `ktlintAndroidTestSourceSetCheck` is a *formatter*, and a
formatter parses without resolving - which is exactly the shape of the P2t finding above, where
`ktlintCheck` reported success over source sets it was not looking at. The pre-commit hooks inherit
the gap, since they run the same tasks.

The cost is bounded and known: a broken instrumented test cannot reach a device, so it fails loudly
at the next `connectedDebugAndroidTest` rather than passing quietly. But it fails **after** the
7-minute device run has been queued, and a commit can carry it. `compileDebugAndroidTestKotlin` is
the one task that closes it and it is seconds; adding it to the documented command and to the hooks
is not done here because P2x-2 is a shell sub-step and changing the commit gate is not, but it is
recorded so the next harness step does not re-derive it.

### `ktlintCheck` never checked a `.kt` file, because the plugin was waiting for a plugin AGP 9 made unnecessary

Found in P2l-3a, by removing three imports the step had made unused and noticing the gate had not
asked for them. `./gradlew :app:tasks --all` listed **only** `runKtlintCheckOverKotlinScripts` and
`ktlintKotlinScriptCheck`: no per-source-set task existed, so `ktlintCheck` - an aggregate over
whatever the plugin registered - resolved to the `.kts` build scripts and nothing else. Probed
directly: an unused import *and* an out-of-order one in `theme/CodeText.kt` produced
`BUILD SUCCESSFUL in 715ms`.

**The cause is one line that is not in `app/build.gradle.kts`.** `ktlint-gradle` 12.2.0 attaches its
Android applier to the plugin id `org.jetbrains.kotlin.android`; AGP 9 ships Kotlin support built
in, so nothing applies that id, and the applier never ran. Nothing was misconfigured and nothing
logged - the plugin was waiting for a plugin that no longer needs to exist. `ktlint-gradle` 14.2.0
registers `ktlintMainSourceSetCheck` and its siblings against AGP 9's own source sets.

**Turning it on reported 11,939 offences, and 9,864 of them were this file being wrong rather than
the code.** `.editorconfig` had claimed `indent_size = 4` for `.kt` since Step 1 while every `.kt`
file in the repository is 2-space - a claim nothing had ever checked. Three settings now describe
the style the code was actually written in, and each earns its line there: 2-space for `.kt` and 4
for `.kts`; `ktlint_code_style = intellij_idea`, because ktlint's own default breaks any signature
of two or more parameters across lines whatever its width, which added 108 lines to one test file
and pushed five over the 500-line gate; and `no-consecutive-comments` disabled, because a
file-header KDoc is this project's convention in 19 files.

What survived was **1,600 real offences**, `ktlintFormat`'s to fix: **185 unused imports**, import
ordering, and wrapping. Net **-172 lines** across 175 files. One could not be auto-corrected and
was a genuine defect - `RenameFileDialogTest.kt` held a class called `RenameNotebookDialogTest`,
left behind when the composable under it was renamed.

**This forbids** relying on an aggregate task to prove a gate ran. The commit hook names
`ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck` and `ktlintAndroidTestSourceSetCheck`
explicitly, so a plugin that stops seeing a source set fails with *"Task not found"* rather than
passing quietly - and the same reasoning applies to any future gate whose task list is discovered
rather than declared.

**Two of those three names were wrong until P4a, and the bug was this decision's own failure mode
one level up.** The hook named `ktlintTestDebugSourceSetCheck` and
`ktlintAndroidTestDebugSourceSetCheck`. Those tasks exist - `ktlint-gradle` registers one per
*variant* source set - but `src/testDebug/` and `src/androidTestDebug/` do not, so both were
permanently `NO-SOURCE` -> `SKIPPED`, and every unit and instrumented test file went unchecked by
the named half of the gate for as long as it has existed. The paragraph above claimed *"verified by
planting an unused import in each of the three source sets and watching all three tasks fail"*; what
actually failed was the aggregate `ktlintCheck` sitting beside them on the same command line -
the exact substitution this decision forbids, performed by the verification of the decision.

Re-measured, one task at a time, on an unused import planted in `PythonRunnerTest.kt`:
`ktlintTestDebugSourceSetCheck` alone -> `NO-SOURCE`, `BUILD SUCCESSFUL in 952ms`;
`ktlintTestSourceSetCheck` alone -> `BUILD FAILED`, naming the line. The names are now the
source-set ones, which is why `src/` has three directories and not five. There is no backlog to
clear: the aggregate had been covering both all along.

### The emulator the suite runs on is 320dp wide **and** density 160, and only the first is deliberate

`dev36` is 320x640px at `hw.lcd.density = 160`. The width is a decision - `docs/PLAN.md` uses
320dp as the narrowest window the app must survive, and it is what settled the notebook bar's
sixth target. The **density** is an AVD default nobody chose, and it means every screenshot in
`evidence/` is drawn at 1px per dp: a frame no user will ever see, and one where a 48dp control
takes 15% of the width against 11.7% on a 411dp phone. It has not misled a *measurement* - dp is
dp, and P2l-4 read the same 48dp at 160, 240 and 420 - but it does mean "look at the screenshots"
has been looking at the wrong proportions.

**Not resolved here.** Moving the standard emulator would re-key 92 flows and the sharder's
measured time balance. P2l-4 captured `phone/` at 411dp/420dpi alongside the standard `after/` to
show the difference, via `wm size` / `wm density` overrides that are reset afterwards.

- **Never `kill -9` maestro.**
- **A flow against a lock screen is not a red result, it is no result.** A 50-run sweep passed 31
  and then failed 19 in a row with the app blameless: `stay_on_while_plugged_in` is 0 on the
  tablet, the screen went off, and every run after that drove the lock screen — the failing steps'
  screen-hierarchy captures contain clock digits and nothing else. `maestro_test` now sets
  `svc power stayon usb` and refuses to run unless **both** `mWakefulness=Awake` and
  `mDreamingLockscreen=false`. Two signals because they fail apart: a device woken by
  `KEYCODE_WAKEUP` reports `Awake` while still behind its PIN, which is how the first repair of
  this still lost two runs. A secure keyguard cannot be dismissed over adb, so the harness says so
  and stops.
- **Evidence must be captured on failure, not only on pass.** `run_flow` once copied screenshots
  and logcat only when the flow passed — `set -e` killed the driver at the failed assertion first,
  discarding the one capture that mattered.
- **Cell-running flows are tablet-only**, because on x86_64 they would assert against
  `FakeKernelGateway`. `step5-brackets` is tablet-only for a second reason: an emulator IME
  artefact. `scripts/run-flows-sharded.sh` respects each flow's `TABLET ONLY` / `WIDE WINDOW`
  header.
- **A popup's position is not observable from a Compose test** — a popup has its own window, so
  `getUnclippedBoundsInRoot` reports coordinates inside it. Anchoring is a screenshot gate.
- **An index over a scrolling list does not address the cell it names.** `index: 1` counts the
  buttons *rendered*; a narrower window changes which those are.
- **`scrollUntilVisible` swipes at the centre of the *screen*, not of the thing being scrolled.**
  That is inside the phone's bottom sheet and **outside** the tablet's dropdown, where it scrolls
  the notebook *behind* the menu and the menu dismisses itself — the failure capture showed the
  notebook with no menu on it at all. It went unnoticed while thirteen rows fitted the tablet and
  surfaced the moment P2b made it fourteen. A swipe at an explicit point inside both surfaces
  (`90%, 60%`) is the fix, and it lives in one subflow rather than in each of the five flows
  that need it.
- **A swipe drags *from* its anchor, so the anchor has to be on the side it is heading away
  from.** A leftward swipe started on the leftmost key moves nothing - which is how a
  `while notVisible` loop span 950 times without scrolling the symbol row a pixel. Each step
  names the key that is rightmost at that stage instead, and is skipped once the target is in
  view.
- **The soft keyboard is in the hierarchy, and it prints symbols too.** The tablet's keyboard
  shows `=` and `]` as hints on its `r` and `y` keys, so a swipe anchored on `"="` matched the
  *keyboard* rather than the symbol row - and a one-second drag across a keyboard is **glide
  typing**. It wrote " ya tea" into a cell, and the failure surfaced three steps later as a
  split in a place nothing on screen explained. Two lessons, and the second is the general one:
  disambiguate an anchor against the keyboard, and assert a precondition where it is
  established rather than where it is used.
- **A caret is placed by driving it, not by tapping at it.** A tap lands where it lands. `←` clamps
  at offset 0, so twenty of them give a known origin and `→` counts from there.
- **A flow against a device with no app installed is not a red result either, and one sweep found
  out the hard way.** `./gradlew :app:connectedDebugAndroidTest` **uninstalls** the APK when it
  finishes, so a sweep started straight afterwards reported `passed 0 · failed 28` on that shard
  with the app blameless — `run-as: unknown package` three times in every driver, then a flow
  driving the launcher. `require_installed_app` refuses now, from `reset_notebooks` as well as from
  `maestro_test`, because the first `run-as` happens before the flow does. It **checks and does not
  install**: a harness that installed would be choosing which build is under test, and the point of
  a sweep is that it runs the one you built.
- **`stopApp` is a `force-stop`, so a flow that kills the app inside the save debounce tests the
  timer rather than the feature.** Writes are held for 500 ms (`NotebookSaveDebounce`) and flushed
  from `EditorViewModel.onCleared`, which a force-stop does not run — so the collapse a flow made two
  steps before `stopApp` had never reached disk, and the dump proved it: the key on the heading that
  had a screenshot's worth of time, nothing on the one that did not. The fix is not a longer wait but
  the thing a user actually does — **leave the document before killing the app**. `collapse-source`
  has had the same shape since Step 4b and has been passing on timing.
- **Maestro matches a whole node, so a prefix assertion is an assertion that cannot fail.**
  `assertNotVisible: "Pick A Few"` was green against a node reading `Pick A Few.ipynb` and would
  have stayed green with the bar on screen. **A document's title is also the wrong handle on a
  tablet**, where the file list beside it shows the same name — so a flow that means "the
  notebook's own bar is up" names a control that is only on it (`Back to notebooks`), never the
  title. Both halves cost one red run to find and neither would have failed loudly.
- **`childOf: {text: "Cell output"}` scopes nothing on a screen with more than one output.**
  Maestro resolves the scope against the *first* container, so a notebook carrying a PNG and a
  table besides the cell being run failed an assertion its own hierarchy capture proved was on
  screen. `u4-execution-status` already recorded this; P2j-2 hit it again from the other side.
  Where the value is in no cell's source the flow needs no scope, and the output gate agrees.
- **An output below the fold is not a failed run.** With the runnable cell last, its output landed
  under the kernel bar and an unscoped assertion failed too - against a screenshot whose own
  *"ran in 6.2 s"* said the cell had run. The fixture puts that cell **first** now. Third time in
  this project that the screenshot is what said otherwise.
- **A driver check that cannot fail is worse than no check, and `adb shell content` gives two ways
  to write one.** `content read` against an `exported="false"` provider is denied for the shell
  user *whatever the configuration says* - so a check asserting it succeeds measures nothing, and
  the refusal is the assertion instead (no bytes, and `not exported from UID` in the error). And
  `content` **exits 0 when it is denied**: it catches the SecurityException, prints it on stderr
  and returns success, so `|| fail` never fires. Read what a command wrote, not what it returned.
  Both in `evidence/p2j-5-share-export/`.
- **A grep for source code in an exported document will not find it.** Pygments wraps every token
  in its own `<span>`, so `print(3 ** 5)` appears nowhere in the markup as one string and
  `grep '3 \*\* 5'` fails against a perfectly good export. Strip the tags and read the document as
  text, which is what a reader sees.
- **A device rule counts only where it is *declared*, and the sharder and the runner read it in one
  place.** A bare `grep -q "WIDE WINDOW"` also matched prose *about* the rule:
  `p2j-4-export-menu` declares NARROW WINDOW on one line and calls itself "the mirror of `WIDE
  WINDOW`" three lines later, so it was placed on the 1280dp tablet and skipped there. **The sweep
  read `failed 0` with a flow that had never run** - which is the whole failure class this harness
  exists to prevent, arriving as a `skipped` rather than as a red. `scripts/driver-rules.sh` is
  now the one reading (`^#\s*<RULE>`), sourced by both scripts so they cannot disagree, and the
  rules **intersect** rather than shadow each other - `x2h-browser-notices-what-a-cell-wrote` needs
  an arm64 device *and* a wide one, and an if/elif chain placed it by the first and let the runner
  skip it for the second. Two drivers had to declare properly before the tightening was safe: that
  one's WIDE WINDOW and `p1c`'s NOT A SUITE FLOW were both only ever matched mid-sentence.
- **`./gradlew :app:installDebug` says *"Installed on 1 device"* rather than failing when a device
  has dropped off USB.** It is not an error, and the sweep that follows drives whatever build was
  there before. `adb kill-server && adb start-server` is the recovery, already recorded for
  `evidence/u-flow-sweep/`.
- **A greyed `DropdownMenuItem` cannot be asserted greyed from a flow, and there is no fix in the
  app for it.** Compose's *merged* semantics tree — what `createComposeRule` reads — folds the item
  into one node carrying both its label and its disabled state, so `assertIsNotEnabled` on the
  label works in an instrumented test. Android's **accessibility** tree — what Maestro reads —
  keeps the state on a clickable node that carries no label, one level above the `TextView`, so
  `assertVisible: {text: …, enabled: false}` matches the leaf and reports `enabled=true` over a row
  that is visibly grey. Adding `Modifier.semantics { contentDescription = …; disabled() }` does not
  help: the item applies the caller's modifier *below* its own `clickable`, so the named node lands
  as a **child** and reports `enabled=true` too. All three were dumped from the device
  (`evidence/p2d-2-ranged-runs/`). So the state is asserted where the tree carries it — an
  instrumented test — and a flow proves the same fact by **behaviour**: a live row closes the menu,
  a dead one leaves it open.
- **A multi-line `Text` is one node, so a flow cannot assert one line's position inside it.**
  Whether "line 40" of a forty-line stream output is *visible* was meant to distinguish a bounded
  output from an unbounded one (P2g-2a) — but `OutputText` renders the whole string as one `Text`,
  so `assertVisible`/`assertNotVisible` for a substring of it can only report whether *that node*
  is visible, and the node is, from its very first line. `assertNotVisible: "(?s).*line 40.*"`
  failed against a screenshot showing only line 1 through line 20, for exactly this reason.
  **Reachability of a cell below was tried next and rejected too**, once measured: Compose's
  nested scroll (below) means the *total* distance to a cell after a bounded output is close to
  the same as after an unbounded one, so `scrollUntilVisible` on the next cell lands at a similar
  place either way and the screenshots at that point looked the same. What the flow asserts
  instead is the one thing nested scroll actually changes — see the next entry.
- **`Modifier.verticalScroll` bounds a composable's own layout size and does not clip its
  drawing.** `ScrollNode.measure` reports a height coerced to the incoming constraint
  (confirmed by an instrumented `getUnclippedBoundsInRoot` comparison, which is exactly right and
  passed first try), but the oversized child is still *placed* and *drawn* past that boundary —
  so a `Box(heightIn(max = …)) { Column(Modifier.verticalScroll(...)) { … } }` looked, on device,
  identical whether bounded or not: the tail of the content was on screen either way, just past
  where the box was meant to end. `Modifier.clipToBounds()` on the scrolling `Column` is what
  makes the bound a visual one, not only a layout one.
- **Nested scroll is real and free, and it is what a flow has to key on instead of a line count.**
  A swipe with both ends inside a bounded output scrolls that output's own `verticalScroll`
  first — the same `overflow-y: auto` behaviour a mouse wheel gets over the reference's output
  area — so the cell's code stays on screen. The identical swipe over an unbounded output has no
  inner scroll region to consume it, so it moves the whole cell. Confirmed by hand with
  `uiautomator dump` and `adb shell input swipe` before it went into a flow: the same gesture at
  the same coordinates left the code cell's source visible in one notebook and scrolled it away
  in the other. `p2g-2a-scrolled-outputs.yaml` asserts only the positive half of that (the code
  stays visible) — the negative half (the code is gone) is not, because a `LazyColumn` keeps a
  couple of items composed just past the viewport and their semantics nodes can still report
  visible there; two screenshots carry that half instead.
- **A persisted app-wide setting is a new class of state `reset_notebooks` did not clear, and
  P2g-3 is the first feature to write one.** Every reset before it emptied the workspace and the
  recents list — both scoped to *documents*. `EditorSettingsRepository`'s file is scoped to the
  *app*, so a flow that leaves auto-closing brackets off would leave it off for every flow run
  after it on the same device, with nothing in either flow naming the other as a cause.
  `reset_notebooks` now removes `editor-settings.json` alongside `recent-documents.json`, on the
  same reasoning `check_driver_isolation.py` already enforces for notebooks: a suite is only
  repeatable if what one run leaves behind cannot change what the next one proves.
- **`reset_notebooks` cannot reach a dotfile, and the kernel keeps its history in one.** IPython's
  input history is `files/spaces/base/home/jupydroid/.ipython/profile_default/history.sqlite`, and
  `rm -rf ./*` does not match dotfiles - so `.cache`, `.ipython`, `.jupyter` and `.local` all
  survive every reset, carrying entries from every previous run of every flow. Cleared in
  `p2k-4-kernel-history-driver.sh` rather than in `reset_notebooks`, because it is that flow's
  concern alone: `get_tail` orders the current session's rows last, so every other flow is
  indifferent to what the file holds. Green twice back to back with the clear in place.

---

### A one-pane defect can need the tablet, not the phone (P2y-d)

The 320dp emulator is this project's narrow device and it could not have found P2y-d. The bar's two
arrangements differ only above Material's 600dp boundary, and the only one-pane window this project
has above it is **the tablet rotated to portrait** - 800dp, which the phone cannot reach and the
tablet's default landscape is two panes away from.

So "narrow window" is two devices here rather than one, which `p2o-4-one-pane-side-panels-driver.sh`
already said and P2y's driver repeats: both name their evidence directory from the width they
measured, so a run cannot be mislabelled. The rule this adds is that a **one-pane** feature owes its
second width a run before it is called shipped, exactly as a two-pane one owes the tablet.

### A layout assertion in dp measures the component's padding by accident (P2y-d)

P2y-d's first assertion was "two items fill the bar", written as a sum of widths against the window.
It read **312dp of 320dp** and failed - the missing 8dp being the gap `NavigationBar` puts between
its items, which is not what the test was about.

Rewritten as the **fraction of the bar between the two items' centres**, the two arrangements are
0.500 and 0.250 and nothing in between: scale-free, padding-free, and impossible to confuse. The
rule: assert the relationship the rule is about, not a dimension that happens to encode it.

### Evidence is a directory, and `run_flow` used to only ever add to it (P2z-h)

`run_flow` did `mkdir -p "$out"` and then `cp`. Nothing removed. So renaming a `takeScreenshot`
left its predecessor in `evidence/<step>/` **and in the commit**, for as long as the repository
lives - and a stale frame is not clutter, it is a second answer to the question the current frame
answers. P2z-x found eleven, in six directories:

- `evidence/p2o-4-…/800dp/04-files-is-one-tap-from-the-document.png`, from 22 Aug, showing a
  browser with **no bottom bar** - beside the frame whose entire point is that the bar is there.
- `evidence/u5a-cell-actions/02-changed-to-markdown.png`, from 18 Aug, drawing the word
  **`markdown`** where a cell's prompt goes: what P2n removed and what `p2n-gutter` now asserts is
  absent.
- Seven frames of the **Recents launch screen** X2h retired, across `x2a`, `x2d`, `x2e` and
  `x2h-1`. `x2e-home-new-python-file` carried four, one of them answering a current frame's
  question under a different number.

**Deleting whatever a run did not write would have been wrong twice.** Five drivers run several
flows into one directory (`p1b`, `p1c`, `p1d`, `p1g`, `p2x-2`), so a later part would wipe an
earlier part's frames; and a driver may write a frame itself - `00-the-defect-only-a-screenshot-
could-see` is deliberate, and a rule that could not tell it from a leftover would delete the most
valuable frame in the tree.

So `scripts/prune_stale_frames.py` keeps a **per-flow manifest**, `evidence/<step>/.frames`, of
which flow wrote which frame, and removes only what *that flow* wrote before and has stopped
writing. Six tests, one per way it could get this wrong. It protects a directory from the run
after its first manifest, not before - the eleven already committed were deleted by hand, and
`p2o-4`'s twelfth was deleted in P2z-i for exactly that reason, which is the limitation
demonstrating itself.

## Licences

Winlator (LGPL-2.1), sora-editor (LGPL-2.1) and Callisto (proprietary) are **clean-room**:
techniques studied, no code vendored. For sora this is load-bearing rather than tidy — it is the
reference for the editor we write ourselves, and a fork would stay LGPL-2.1, so forking answers the
licence question with "no".

VS Code and vscode-jupyter are both **MIT** (`vscode/LICENSE.txt`, `vscode-jupyter/LICENSE`),
which is why P2u may transcribe colour values out of them where Winlator's LGPL forced clean-room:
MIT permits reuse with attribution, and every transcribed value carries its colour id in a doc
comment.

Termux's `terminal-emulator` and `terminal-view` are **Apache 2.0**, and this is an exception
rather than the repository's licence: `termux-app/LICENSE.md` is GPLv3 with those two modules
carved out, inherited from Jack Palevich's Android Terminal Emulator. That is what lets P4e-P4h
**transcribe** them the way P2u transcribes VS Code, where Winlator's LGPL forced clean-room. Every
transcribed file names its upstream path and the checked-out commit
(`3df69d1da197dd9bd71a3bafd902dffd720576b4`) in its KDoc; the app carries no `NOTICE` file because
it ships no `LICENSE` of its own yet, and one attribution point per file is the form the rest of
this repo already uses for citations.

`reference/` is gitignored and travels with no clone. The six consulted here, at the remotes the
local clones actually point at:

```sh
cd reference/research_repos
git clone --depth 1 https://github.com/jupyterlab/jupyterlab
git clone --depth 1 https://github.com/JetBrains/intellij-community.git   # 2.4 GB
git clone --depth 1 https://github.com/brunodev85/winlator
git clone --depth 1 https://github.com/microsoft/vscode.git               # 1.7 GB
git clone --depth 1 https://github.com/microsoft/vscode-jupyter.git       # 256 MB
git clone --depth 1 https://github.com/termux/termux-app.git
```

---

## Closed by measurement, so nobody re-investigates them

- **Orphaned Space processes are not a problem.** The server and kernel are ordinary children of
  the app process; `am force-stop` and `am kill` take them with it. Nothing leaks across app death.
- **`ExecutionOutcome.TimedOut` was exercised and retired.** A cell that runs for an hour is a
  cell, not a failure. Stopping one is the user's job, via interrupt.
- **Sending an `execute_request` before the socket connects was a real race and not the stall.**
  Removed anyway (JupyterLab does not do it either); run 24 of 50 stalled with the ordering
  correct.
- **Retrying `kernel_info_request` on the server's own 0.5 s cadence does not fix the stall.**
  Measured over 80 tablet runs: 120 asks over 60 s, not one reply, while iopub delivered. Reverted
  to the reference's shape. `evidence/kernel-observability/seventh-sighting/`.
- **Waiting for the server's DEBUG log to explain the stall will not work.** The server's nudge is
  satisfied by a **control** reply alone (`jupyter_server/services/kernels/connection/channels.py:158-164`
  and `:291-292`), so a kernel with a dead shell channel looks healthy to it. See `docs/PLAN.md` P0.

### A change to shared chrome owes the flows that draw it a re-run (P2x-5e, found at P2x-5k)

P2x-5e added the replace chevron's row to the search panel - 48dp above the two glob boxes - and
re-ran none of the panel's other flows. `p2x-3-search-scope` had been red ever since, and nothing
knew until P2x-5k asked it again: `assertVisible: "files to exclude"` fails, because the details open
**focused** (`toggleQueryDetails`' `moveFocus`), the IME comes up with them, and 48dp was more than
the margin left.

It was already marginal and the committed evidence said so in a picture nobody re-read: the frame
from P2x-3f shows the exclude *field* half clipped, with only its heading above the fold. A frame
that is *just* passing is a frame that will fail on the next 48dp, and this file has now spent two
phases learning that a green log is half the evidence.

The app is not what changed - the panel is one scrolling surface (P2x-3e) and a reader reaches the
box. The flow puts the keyboard away before reading the panel, which its own later steps have always
done before reading a count.

The rule: a sub-step that changes chrome two screens share re-runs both, not the one it was written
for. P2x-5k re-ran all eight panel flows on the tablet.

### An assertion that cannot fail is not an assertion (P2z-c, P2z-f, P2z-i)

Three flows in this suite asserted something that was already true before the step that was
supposed to make it true. All three were green from the day they were written, and all three were
found by looking at the frame rather than at the log.

- `p2h-2-table-of-contents` scrolled and then asserted the previous heading was gone - a proxy for
  "away from the top" that encodes a screen height. At 320dp its seven-cell fixture overflowed; at
  800dp it did not, so `scrollUntilVisible` found the target **without scrolling** and the jump had
  no movement to undo (P2z-c).
- `p2h-6-output-headings` tapped a table-of-contents entry and asserted the text it points at was
  visible. The fixture was three cells and fitted every window the flow runs in (P2z-f).
- `p2o-4-one-pane-side-panels` did the same with `two = 3`, vacuous at 800dp for the same reason
  (P2z-i).

Two fixes, and which one applies is decided by **whose claim it is**:

1. If the flow owns the claim, the fixture grows until the premise can be asserted - and the
   premise *is* asserted, before the step. `p2h-2` and `p2h-6` both took a fifteen- and a
   thirteen-cell fixture and an `assertNotVisible` ahead of the tap.
2. If another flow owns it, the flow stops claiming it and its frame is renamed for what it does
   show. `p2o-4` is about the side area at one pane; the jump is `p2h-2`'s, already proved at both
   widths, and copying a tall fixture here would have been a second proof of a proved thing.

The general rule, which `scripts/check_maestro_output_assertions.py` already enforces for one
special case (a post-Run wait must need a kernel): **before asserting that a step made something
true, assert that it was false.** Where the flow cannot, the frame's name must not say it did.

### A caption is a claim, and it goes stale like any other (P2z-g, P2z-j)

Two flows carried prose describing what a reader should see, and the prose had stopped being true
of the frame beneath it.

- `p2i-3-find` said *"the current match amber"*. Nothing in this app has ever been amber. Both
  match marks take the same teal one alpha apart (`#27678280` / `#27678290`) and what separates
  them is the **bold weight** `EditorTheme.kt` adds for exactly that reason. A reader checking the
  frame against its caption would have concluded the palette had regressed, and a palette "fixed"
  to match would have undone P2u-3d.
- `p2u-1-chrome` was worse: the sentence was in the **fixture**, so it is rendered into the
  committed screenshot. *"A blockquote's rule is the app's only reader of `colorScheme.outline`"*
  was true until P2u-3f gave the quote a wash and moved its rule to `blockQuoteBorder` at a
  deliberate 1.08:1. The frame measures 1.08:1; the sentence in it promises 3.76:1.

The rule: **a caption that names a colour, a value or a role is checked against the tree when the
thing it names moves**, the same as any citation in this file. A fixture that states a fact about
the app states it in the evidence, permanently.

And the correction that came out of chasing the second one, kept because it is a trap worth
naming: `colorScheme.outline` looked unread - `grep -rn colorScheme.outline app/src/main` returns
one comment and no code. It is read by Material itself. `OutlinedTextFieldTokens.OutlineColor` is
`ColorSchemeKeyTokens.Outline` and `TextFieldDefaults.kt:1393` makes it every outlined field's
resting border, so it is drawn in the rename dialog and the four boxes on the search panel.
**Grepping app code is the wrong question to ask of a Material role**; the token tables are.

### A differential that prints deltas measures neither side's baseline (P4f-6b-2)

**A mutation survived that nothing about the sequence stream could have caught**, and the reason was
in the harness, not the alphabet. The colour table is 259 entries and only two or three ever move,
so the dump prints the entries that **differ from the defaults** - and each side compared against
*its own* defaults. Change the grey ramp's starting value on the Kotlin side and both sides still
print nothing: the entry equals its own default on each. A systematically wrong palette cancelled
out exactly where a differential is supposed to be strongest.

Both dumps now print `palette=`, `Arrays.hashCode` over all 259 defaults - which Java and Kotlin
compute identically, so it is comparable without spelling the table out on every line.

The rule this states, and it is not about colours: **a state dump that reports a delta has to report
what the delta is against.** The same shape is already in this file twice - P4f-2 found the styles
were invisible and P4f-6a found the replies were - and each time the fix was to widen what is
printed rather than to widen the alphabet. A field the two sides compute from different baselines
is worse than an absent one, because it looks like coverage.

The counterpart correction, in the same step: `painted=` counts colour reports, and **the
reference's constructor calls `reset()` and reports one before the stream sends a byte** where this
transcription builds its table without reporting. `EmulatorDump` zeroes the counter after
construction so both sides count what the stream caused. Making the emulator fire a notice at
construction to match a counter would have been changing the subject to suit the instrument.
