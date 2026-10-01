# ADR-0003 — Use musl, not glibc, and ship a runtime for arm64 only

Status: accepted
Date: 2026-08-25

## Context

The app runs a Linux userland inside an Android app process, which is zygote-spawned and carries
bionic's seccomp filter. Android 16's filter **kills glibc's libc startup with SIGSYS on both
ABIs**; musl starts cleanly under the same filter with everything else held constant.

The x86_64 story changed underneath its own rule. It was originally "the same filter kills musl
there too". That is superseded: the refusals are now named — seven of them, every one a case where
musl issues a legacy syscall and bionic issues the `*at` form, which do not exist in the arm64
kernel ABI at all. `tools/rootfs/musl_at_shim.c` routes every one, and with it `jupyter_server`
starts on the emulator, `POST /api/sessions` returns 201 and a kernel starts, with **zero** refused
syscalls.

What still stops x86_64 is that the kernel dies after starting, for a reason that is *not* seccomp
and not memory.

## Decision drivers

- Nothing may be hand-cross-compiled; the Jupyter stack must come from ordinary wheels.
- A device that cannot run a Space must still run the app.
- Evidence taken on a device that does not run a real kernel must never be mistaken for evidence.

## Options considered

| Option | Pro | Con |
|---|---|---|
| glibc userland | The manylinux wheel world | Its libc startup is killed by the app seccomp filter on both ABIs. Non-starter |
| musl userland | Starts cleanly under the filter; **musllinux** wheels exist, so nothing is hand-built | A smaller wheel world, and musl's own internals resist interposition ([ADR-0005](0005-interpose-getaddrinfo-rather-than-redirect-resolv-conf.md)) |
| Ship an x86_64 rootfs too | Emulator flows could run cells | The kernel dies after starting for an unexplained reason; shipping it would make emulator evidence look like device evidence |

## Decision

**musl (Alpine), and the Jupyter stack from musllinux wheels.** Nothing is hand-cross-compiled.

**`SpaceAbi` stays arm64-only.** On x86_64 the app logs
`no Space runtime here: no rootfs is built for ABI 'x86_64'` and falls back to `FakeKernelGateway`,
which echoes a cell's source back as its output — so the UI is fully exercisable on an emulator.

**No emulator flow may run a cell.** It would go green against the fake. This rule is unchanged and
stays unchanged until a cell actually runs there; when it does, emulator evidence will still be
weaker than tablet evidence, because x86_64 reaches the kernel through a shim arm64 does not use.

An arm64 emulator is not the way round it on an x86_64 host: the emulator refuses the AVD outright
(`not supported by the QEMU2 emulator on x86_64 host`) on both engines, and NDK translation
segfaults on an `execve`'d arm64 binary. Both measured.

## Consequences

Every device gate that proves the runtime runs on the tablet, and only there. The emulator is for
UI, and `AGENTS.md` rule 2 is the enforcement.

The `musl_at_shim.c` work is not wasted and is not dead code on arm64 — it is what makes the x86_64
kernel's remaining failure a *single* unexplained problem rather than eight.

The thing a future reader will be tempted to "fix" is the arm64-only rule, on the grounds that the
shim now works. It is not seccomp that stops x86_64 any more; shipping a rootfs whose kernel dies
after starting would buy nothing and would make a class of evidence untrustworthy.
