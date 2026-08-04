# Environment fidelity: why per-package fixes cannot work

Recorded 2026-08-04, after Steps 8.3-8.7.

## The pattern

| Component | Failure | Cause |
|---|---|---|
| glibc (any binary) | SIGSYS at libc startup | `set_robust_list` refused |
| psutil.net_if_addrs | SIGSEGV | `getifaddrs()` EACCES, return unchecked |
| libzmq TCP bind | SIGABRT | same EACCES, `errno_assert` |
| numpy import | SIGSYS | a refused syscall, not yet named |

Four failures, three mechanisms, one sandbox. Fixing them one at a time was
working, and was the wrong strategy.

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

The performance of a TCG guest on this class of hardware is the number that
decides whether fidelity is affordable. Nothing else should be decided before
it is measured.

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

Not yet done, and not to be guessed at. It requires building QEMU system-mode for
android-arm64, shipping it the same way the interpreter is shipped today
(nativeLibraryDir, which is already proven), booting a minimal arm64 guest, and
timing the same numpy workload inside the guest against the native musl figure we
already have from this device.

Until that number exists, option 1 cannot be compared with option 2 on anything
but preference, and no more runtime code should be written on either path.
