# Environment fidelity: what the sandbox actually costs

Recorded 2026-08-04, after Steps 8.3-8.7.

> **Superseded in its central claim, same day.** This document argued that the
> refused-syscall gap was unbounded and that only a guest kernel could close it. Then
> the syscall was named. It is `mbind`, numpy does not need it, and handling SIGSYS is
> enough to make numpy and scipy run correctly inside the app process
> (`evidence/step8.7/sigsys-syscall-named.txt`). The reasoning below is kept because
> the AOSP citations and the survey of Termux and Winlator remain accurate and are
> what the correction is built on - but read "The correction" at the bottom first.

## The pattern

| Component | Failure | Cause |
|---|---|---|
| glibc (any binary) | SIGSYS at libc startup | `set_robust_list` refused |
| psutil.net_if_addrs | SIGSEGV | `getifaddrs()` EACCES, return unchecked |
| libzmq TCP bind | SIGABRT | same EACCES, `errno_assert` |
| ~~numpy import~~ | ~~SIGSYS~~ | **`mbind` refused, and unhandled. Not needed - see the correction.** |

Four failures, three mechanisms, one sandbox. Fixing them one at a time was
working, and was the wrong strategy. The error in what follows is subtler than that:
it treated "the kernel refused a syscall" and "the program cannot proceed" as the same
event. They are not.

## Why it is unbounded

Android's app seccomp policy is an **allowlist**, and AOSP states what defines it
(bionic/libc/SECCOMP_ALLOWLIST_APP.TXT):

> This file is used to populate seccomp's allowlist policy in combination with
> SYSCALLS.TXT. Note that the resultant policy is applied only to zygote spawned
> processes.

SYSCALLS.TXT is the set of syscalls **bionic** uses. So the permitted surface is
"what bionic needs", plus a short explicit allowlist, minus a short blocklist
(setuid, mount, chroot, reboot, module loading, clock setting - none of which we
want anyway).

The consequence is the whole argument: **anything outside bionic's own usage is
denied by default.** A glibc or musl userland, and every wheel built for Linux,
is written against Linux's syscall surface, not bionic's. The failures are not a
list we can work through - they are everything in the gap between the two, and
that gap is not enumerable from the policy.

This is why `set_robust_list` is refused despite being used by every pthread
program on Linux: bionic does not call it, so it was never allowlisted.

## The only escape, stated by the policy itself

"applied only to zygote spawned processes". An app is a zygote child; seccomp
filters are inherited across fork and exec and cannot be removed once installed.
So no process an app can create is free of the filter.

Fidelity therefore requires that our Linux processes' syscalls never reach
Android's filter at all - which means a guest kernel servicing them. A VM.

## What that costs on real hardware

Measured on the Galaxy Tab A9+ (SM-X216B, Snapdragon 680):

    pm list features | grep virtualization  -> nothing
    ls /dev/kvm                             -> No such file or directory
    AVF / terminal packages                 -> none

No hardware virtualization, and no Android Virtualization Framework. AVF exists
on Android 15+ but needs vendor pKVM support and its API is privileged, so it is
not a path a normal app can rely on across devices today.

That leaves software emulation - a guest kernel under QEMU TCG - which gives
full fidelity at a large and as-yet-unmeasured performance cost.

## What is not in doubt

The packaging model is sound and is unaffected by any of this: execve out of
nativeLibraryDir, the patched PT_INTERP, the installer's symlink, the rootfs in
filesDir. jupyter_server, a real kernel and 2 + 2 = 4 all work today. musllinux
aarch64 wheels exist for numpy and scipy and install cleanly.

What is in doubt is only whether an Android app process can host a faithful
Linux userland. On this evidence it cannot, for any userland that is not bionic.

## Open

~~The performance of a TCG guest on this class of hardware is the number that
decides whether fidelity is affordable.~~ Closed without being measured: see
"The correction" below. What is open instead is which *other* packages report
refusals, and that is answered by running them.

## How the maintained projects actually handle it

Neither of the two obvious precedents achieves "every Linux package just works"
inside an app process. They avoid the problem in different ways, and the cost of
each is visible in their repositories.

**Termux — rebuild the ecosystem against bionic.** `targetSdkVersion=28`, which
buys the exec exemption but *not* seccomp relief: the filter is applied to every
zygote-spawned process regardless of target SDK. Termux does not need relief,
because its packages are compiled against bionic and therefore only ever issue
syscalls bionic issues. Fidelity is achieved by construction, and paid for by
maintaining a package build farm — `termux-packages` rebuilds the userland rather
than importing Linux binaries. The app ships that as a bootstrap zip
(`TermuxInstaller` extracts it; `app/src/main/cpp/termux-bootstrap-zip.S`).

**Winlator — patch glibc where Android differs.** It does ship a glibc rootfs,
and it carries `glibc_patches/`. Those patches are, in their entirety:
`shmget`, `shmat`, `shmctl`, `shmdt` and a supporting `android_sysvshm.[ch]` -
SysV shared memory reimplemented in userspace over Unix sockets, derived from
`pelya/android-shmem`. Nothing about the robust list, nothing general. It is a
targeted fix for one gap that Wine needs, not a fidelity layer, and it works
because Winlator only has to make Wine and box64 run - not arbitrary Linux
software.

So the honest reading of the precedent: **nobody solves this inside an app
process.** The closest anyone gets is Termux, and it gets there by rebuilding
every package rather than by running Linux binaries. Projects that do run
unmodified Linux userlands faithfully - the AVF "Linux Terminal", QEMU-in-an-APK
efforts - all put a guest kernel underneath.

That maps onto three honest options, and they should be named plainly:

1. **Guest kernel (fidelity).** Unmodified Linux, every wheel works, no
   per-package handling ever. Needs a VM. AVF is unavailable here (measured
   above) and privileged besides, so it means software emulation, at a
   performance cost that is currently unmeasured.
2. **Bionic userland (Termux's answer).** Reliable and proven, but it means
   sourcing or building the Python stack against bionic and giving up
   manylinux/musllinux wheels - i.e. owning a build farm.
3. **Patch the libc for specific gaps (Winlator's answer).** Cheap per gap, and
   exactly the band-aid strategy rejected here, because our gap is not one
   feature like SysV shm - it is the whole difference between Linux's syscall
   surface and bionic's.

## The measurement that decides between 1 and 2

Superseded before it was taken - see below. It would have required building QEMU
system-mode for android-arm64, booting a minimal arm64 guest, and timing numpy inside
it against the native figure. That work is not needed, because option 4 exists.

---

## The correction: SECCOMP_RET_TRAP is a signal, not a verdict

Measured 2026-08-04, after the above was written.
Evidence: `evidence/step8.7/sigsys-syscall-named.txt`.

The syscall numpy's import could not make is **235, `mbind`** - NUMA memory policy.
numpy and OpenBLAS call it opportunistically and do not care whether it succeeds; on a
one-memory-node phone it is a no-op regardless. On a Linux kernel built without NUMA it
returns ENOSYS and every caller copes silently.

What killed the process was not the refusal. Android's filter refuses with
`SECCOMP_RET_TRAP`, which raises SIGSYS - and nothing in a musl process handles SIGSYS,
so the default action, terminate, applied. Install a handler that reports and returns,
and the refused call simply returns -ENOSYS as it already did, numpy imports, and both
numpy and scipy compute correct answers under threads and BLAS. Exit 0 across the board.

So the argument above conflated two things: the syscall surface a userland *uses* and
the surface it *requires*. The gap between Linux and bionic is real and is not
enumerable from the policy - that part stands. But a program only breaks on the part of
that gap it cannot tolerate an error from, and that is a much smaller set. It is also
an empirical one: run the software, read the reported refusals.

That is a fourth option, and it is the cheapest of the four:

4. **Handle SIGSYS and let ENOSYS propagate.** No syscall is reimplemented and no libc
   is patched, so it is not option 3 in disguise - Winlator's `android_sysvshm` supplies
   a missing feature over Unix sockets, whereas this supplies nothing and merely stops
   converting "unsupported" into a fatal signal. Arguably *more* faithful to Linux than
   the status quo, since ENOSYS from an unsupported syscall is ordinary Linux behaviour.

Its limits, stated plainly so they are not discovered later:

- It fixes only refusals the caller tolerates. A package that genuinely needs a refused
  syscall still fails, and no handler can change that.
- As shipped behaviour rather than a diagnostic, the handler must log every distinct
  refusal. Silently swallowing them would trade loud deaths for quiet wrong answers,
  which is a worse failure mode than the one being fixed.
- The set is now discovered by running software, not derived in advance. That is a
  different kind of unknown from the one above - bounded per package, and observable -
  but it is still an unknown.

**Where this leaves the three original options.** Option 1 (guest kernel) is no longer
required for the scientific stack and its TCG cost need not be measured. Option 2
(bionic userland, a build farm) is not needed either. Option 3 stays rejected. The
runtime keeps the architecture Step 8 already proved: musl rootfs, interpreter exec'd
from nativeLibraryDir, stock wheels.
