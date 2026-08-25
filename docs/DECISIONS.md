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

### musl, not glibc; arm64, not x86_64

Android 16's app seccomp filter kills glibc's libc startup with SIGSYS on both ABIs; musl starts
cleanly under the same filter with everything else held constant. The Jupyter stack still comes
from ordinary wheels — musllinux rather than manylinux — so nothing is hand-cross-compiled.
`evidence/step8.3/`.

x86_64 has no rootfs at all: the same filter kills musl there too. The app logs
`no Space runtime here: no rootfs is built for ABI 'x86_64'` and falls back to
`FakeKernelGateway`, which echoes a cell's source back as its output. **No emulator flow may run
a cell** — it would go green against the fake. `evidence/step8.3/musl-is-arm64-only.txt`.

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

### ANSI handling today is a stripper, not an emulator

`RenderedOutput.kt:96-102` regex-replaces SGR escapes out of tracebacks
(`withoutAnsiColours()`). It is not reusable for a terminal, which needs a grid with cursor
addressing — see `docs/PLAN.md` P4d.

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

### One pane grows no navigation bar, and two destinations is why (P2o-4)

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

## Harness

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
`ktlintMainSourceSetCheck`, `ktlintTestDebugSourceSetCheck` and
`ktlintAndroidTestDebugSourceSetCheck` explicitly, so a plugin that stops seeing a source set fails
with *"Task not found"* rather than passing quietly - and the same reasoning applies to any future
gate whose task list is discovered rather than declared. Verified by planting an unused import in
each of the three source sets and watching all three tasks fail.

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

## Licences

Winlator (LGPL-2.1), sora-editor (LGPL-2.1) and Callisto (proprietary) are **clean-room**:
techniques studied, no code vendored. For sora this is load-bearing rather than tidy — it is the
reference for the editor we write ourselves, and a fork would stay LGPL-2.1, so forking answers the
licence question with "no".

VS Code and vscode-jupyter are both **MIT** (`vscode/LICENSE.txt`, `vscode-jupyter/LICENSE`),
which is why P2u may transcribe colour values out of them where Winlator's LGPL forced clean-room:
MIT permits reuse with attribution, and every transcribed value carries its colour id in a doc
comment.

`reference/` is gitignored and travels with no clone. The five consulted here, at the remotes the
local clones actually point at:

```sh
cd reference/research_repos
git clone --depth 1 https://github.com/jupyterlab/jupyterlab
git clone --depth 1 https://github.com/JetBrains/intellij-community.git   # 2.4 GB
git clone --depth 1 https://github.com/brunodev85/winlator
git clone --depth 1 https://github.com/microsoft/vscode.git               # 1.7 GB
git clone --depth 1 https://github.com/microsoft/vscode-jupyter.git       # 256 MB
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
