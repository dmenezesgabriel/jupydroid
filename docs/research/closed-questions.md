# Closed questions

A question that was **measured and answered "no"**, so nobody spends a session re-investigating it.
Each entry states what was asked, what was measured, and what the answer forbids.

This is not a decision register — a decision that forbids an alternative is an
[ADR](../adr/0000-template.md). This is the negative result: the thing we looked for and did not
find, or found and rejected on evidence.

An entry is **re-opened only by new evidence**, and re-opening means editing the entry to say what
changed. Ids are never reused.

```sh
grep -n '^### CLOSED-' docs/research/closed-questions.md
```

---

### CLOSED-001 — Do orphaned Space processes leak across app death?

**No.** The `jupyter_server` and kernel are ordinary children of the app process; `am force-stop` and
`am kill` take them with it. Nothing survives app death, so the app needs no reaper and no PID file.

### CLOSED-002 — Should a cell execution time out?

**No.** `ExecutionOutcome.TimedOut` was implemented, exercised and retired. A cell that runs for an
hour is a cell, not a failure; stopping one is the user's job, through interrupt. Re-adding a timeout
would make a long-running computation indistinguishable from a hung kernel, which is the thing the
kernel status indicator already answers.

### CLOSED-003 — Was sending `execute_request` before the socket connected the cause of the execution stall?

**No, though it was a real race.** It was removed anyway, because JupyterLab does not do it either.
With the ordering correct, 24 of 50 runs still stalled — so the race existed and was not the cause.

### CLOSED-004 — Does retrying `kernel_info_request` on the server's own 0.5 s cadence fix the execution stall?

**No.** Measured over 80 tablet runs: 120 asks over 60 s, not one reply, while iopub delivered
throughout. Reverted to the reference's shape.

### CLOSED-005 — Will the server's own DEBUG log explain the execution stall?

**No, and it cannot.** The server's nudge is satisfied by a **control** reply alone
(`jupyter_server/services/kernels/connection/channels.py:158-164` and `:291-292` in the shipped
rootfs), so a kernel with a dead shell channel looks healthy to it and the log says nothing. See
[`execution-stall.md`](execution-stall.md).

Read that file from the shipped rootfs without extracting it:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
zstd -dc $T | tar -xOf - usr/lib/python3.12/site-packages/jupyter_server/services/kernels/connection/channels.py \
  | sed -n '155,170p;288,295p'
```

### CLOSED-006 — Can `stty size` confirm the terminal resize reached the shell?

**No — not in this rootfs.** `stty size` answers from a fixed default here rather than from a live
`ioctl`, so it reports `25 80` whatever the pty is actually set to. An apparent 79-vs-80 column gap
was chased on that reading and turned out to be the reply, not the pty: a genuine `ioctl` and a
temporary probe both agree the pty, the local grid and the wire `set_size` were at the same value
throughout.

What proves a resize instead is `vi`'s own reflow — a full-screen program redrawing to the new
width.

### CLOSED-007 — Can a terminal re-attach replay only what was missed?

**No.** terminado's `read_buffer` replays the **whole session**, not the tail since the client left.
So a re-attach clears the local screen first and lets the replay rebuild it, rather than appending a
second copy of everything to what is already drawn.

### CLOSED-008 — Does staging arm64 artefacts need an emulated arm64 container?

**No.** The staging script once ran `patchelf` inside the **target-ABI** image, so staging arm64 on an
x86_64 host needed `binfmt_misc` registered — an undocumented prerequisite that fails as
`exec format error`.

`patchelf` rewrites ELF headers and never executes the binary, so an x86_64 `patchelf` edits an aarch64
ELF correctly. Measured: the loader it produces is **byte-identical by sha256** to one staged the old way.

The ELFs now come from the tarball — which *is* a `docker export` of that image, so the bytes are the
same — and `patchelf` runs in a host-architecture container. The script's old comment justified the old
shape as editing *"binaries against the same libc that produced them"*, which is not what `patchelf`
does; the real reason for a container is that the host needs no `patchelf` installed, and that is kept.

*(Since closed: the `patchelf` step itself is retired — nothing is edited at staging any more, so the
question no longer arises at all.)*

### CLOSED-009 — Does a pseudoterminal work inside an Android app process?

**Yes.** Probed on the device before the terminal was designed, so the phase's shape rested on a
measurement rather than on hope. Nothing about `openpty`/`forkpty` under the app's seccomp filter needed a
workaround.
