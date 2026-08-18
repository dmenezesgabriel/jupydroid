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
of contents both answers "jump around a long notebook" on a phone and already pays for the third
pane (`ListDetailSceneStrategy.extraPane()`) a minimap would want. Re-costed once the ToC exists
rather than built twice.

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

### The palette is read out of IntelliJ, and its contrast is pinned

Syntax colours used to come from `colorScheme` roles — so `NUMBER` sat close to the body text and
the value ultimately came from the user's wallpaper, which nothing could assert. Colours now come
from IntelliJ's own schemes with every WCAG ratio pinned by `EditorPaletteTest`.

### Markdown renders natively — no WebView, no HTML

commonmark-java AST → recursive composables, following JetBrains Jewel
(`reference/research_repos/intellij-community/platform/jewel/markdown`, Apache-2.0): blocks recurse,
leaf blocks hold an inline *model* and the `AnnotatedString` is built at draw time, inline code
takes `SpanStyle.background`, a quote's rule is `drawBehind`. Its `gfm-tables` extension gave
`RichTable` its shape: cells hold inlines only, and column widths come from `maxIntrinsicWidth` in
a custom `Layout`. The same applies to `text/html` outputs — parsed with jsoup, drawn as Compose.

**Audit A1c:** a rendered markdown cell renders by default and a tap opens the editor **with the
caret placed**. The first version opened an editor with no caret, which no JVM test could see.

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
`ListDetailSceneStrategy.listPane()` / `.detailPane()`, and P2h's table of contents uses
`extraPane()` rather than a third hand-written scene.

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
and would open untrusted. That is inert today because nothing renders trust, and it is a decision
P2j has to make rather than discover.

---

## Harness

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

---

## Licences

Winlator (LGPL-2.1), sora-editor (LGPL-2.1) and Callisto (proprietary) are **clean-room**:
techniques studied, no code vendored. For sora this is load-bearing rather than tidy — it is the
reference for the editor we write ourselves, and a fork would stay LGPL-2.1, so forking answers the
licence question with "no".

`reference/` is gitignored and travels with no clone. The three consulted here, at the remotes the
local clones actually point at:

```sh
cd reference/research_repos
git clone --depth 1 https://github.com/jupyterlab/jupyterlab
git clone --depth 1 https://github.com/JetBrains/intellij-community.git   # 2.4 GB
git clone --depth 1 https://github.com/brunodev85/winlator
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
