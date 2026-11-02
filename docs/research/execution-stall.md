# The execution stall

A research memo: what was measured, what it rules out, and what the next session would run. Tracked
as `GAP-007` in [`../architecture.md` §11](../architecture.md).

**Status: parked as a diagnosis, closed as a repair.** The user-visible damage is fixed — a stall
costs about 1.4 s and a replaced kernel instead of hanging forever. What remains is that it
invalidates any timing measured above it, and that its cause is unknown.

Measured over 50 tablet runs: 50 passed, 0 failed, 1 was a real stall recovered in 1.4 s.

## Symptom

One defect, nine sightings across four distinct flows, every one passing on an immediate re-run of
the same build.

On a stalled connection the socket connects, **iopub delivers**, and the shell channel answers
nothing — not the app's `kernel_info_request`, and not 120 asks over 60 s.
Three `status` frames arrive carrying parent ids
the app never sent (`<sessionId>_<pid>_<n>`, minted by the server's `Session`), then silence.

## Why the server cannot notice it

Read out of the shipped rootfs, not inferred:

```sh
T=app/src/main/assets/spaces/base/rootfs-arm64-v8a.tzst
P=usr/lib/python3.12/site-packages
zstd -dc $T | tar -xOf - $P/jupyter_server/services/kernels/connection/channels.py | sed -n '155,170p;288,295p'
zstd -dc $T | tar -xOf - $P/ipykernel/kernelbase.py | sed -n '360,370p'
```

`nudge()` asks **both** shell and control, and its own docstring says it resolves on *"a shell or
control reply and at least one iopub message"*. `process_control` then publishes `status` on iopub
parented to that request. So a kernel whose shell channel alone is dead still passes the nudge:
control replies, both futures resolve, the server declares the connection live and logs nothing.

Two consequences:

1. **The three unexplained `status` frames are the kernel answering the *control* nudge.** The trail
   is fully accounted for; only the cause of the dead shell channel is open.
2. **The server is structurally unable to notice this failure**, which is why six sightings produced
   no server-side complaint — and why waiting for the server's own account at DEBUG will not work
   ([CLOSED-005](closed-questions.md)).

Both are confirmed on device rather than only read out of source. With the kernel at DEBUG, a healthy
run logs `Nudge: attempt 1`, then a shell dispatch **and** a `Control received:` for a second request,
then `Nudge: shell info reply received`.

## What the eighth sighting settled

Read with both halves of the trail for the first time.
Nine milliseconds after the kernel dispatched the
server's shell nudge and replied to it, the app's `kernel_info_request` produced **no dispatch line at
all** — then three seconds in which neither process logged anything, ended by the app's restart. The
same kernel handled a `shutdown_request` on **control** immediately afterwards, so the process was
healthy; its shell channel had simply stopped delivering inbound.

So of the two candidate causes it is **the request never reached the kernel**, not *the kernel
received it and never replied*. What that still does not say is **where** it was lost: the server logs
no line per relayed WebSocket message, so the server's write and the kernel's read remain
indistinguishable.

## The hypothesis, and how to falsify it

Inbound shell delivery stopped while control and iopub — each on its own thread and loop — kept
working. That is the shape ipykernel 7's `ShellChannelThread` would produce.

ipykernel 7 moved the shell channel into its own thread: `ipykernel/shellchannel.py` defines
`ShellChannelThread`, `ipykernel/socket_pair.py` routes to subshells over inproc `PAIR` sockets and is
marked `.. versionadded:: 7`, `kernelapp.py` binds `shell_stream` to that thread's `IOLoop` and starts
it, and `Kernel.start()` registers `shell_stream.on_recv(...)` afterwards, from the main thread. None
of this existed in ipykernel 6. The control channel — the one that has never stalled — is the older
path.

**The variable to remove is the shell channel *thread*, not the ipykernel version**, and the shipped
7.3.0 still carries the pre-7 topology behind a guard: `kernelapp.py` guards on
`if self.shell_channel_thread`, `kernelbase.py` wires `shell_stream.on_recv` straight onto the main
loop when it is absent, and `_supports_kernel_subshells` then returns False so a subshell request is
refused rather than crashing. So `shell_channel_thread = None` is a supported configuration of the
shipped kernel and changes exactly one variable. `JupydroidKernelApp` already overrides `init_iopub`
for a comparable reason. The ipykernel 6 pin stays the fallback, since it also changes zmq usage.

Traitlets config cannot substitute: `init_sockets` assigns the attribute unconditionally *after*
config load, so only a subclass override works.

## What it would cost

**One prerequisite, and it is the actual cost.** `jupydroid_kernel.py` is baked into the image by
`tools/rootfs/Dockerfile`, so a six-line change to our own kernel entry point needs an emulated arm64
rebuild — and this host has no `binfmt` registered (`docker run --platform linux/arm64` fails with
`exec format error`; `docker run --privileged --rm tonistiigi/binfmt --install arm64` is how the
shipped tarball was evidently produced).

The clean unblock is to ship `jupydroid_kernel.py` as an **app asset written into the Space on
install**, exactly as `SpaceKernelSpec` writes `kernel.json`.

**The sample size, measured rather than assumed.** The stall rate is **2 recoveries in 81 valid tablet
runs, ≈ 2.5%**. Zero in 50 runs is therefore a **29%** event even if nothing changed — no evidence at
all. p < 0.05 needs roughly **120 runs per arm** (~2 h at ~55 s per run), with the ipykernel-7
baseline topped up to match.
