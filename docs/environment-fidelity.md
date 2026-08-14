# Environment fidelity: what the sandbox actually costs

Recorded 2026-08-04, after Steps 8.3–8.7. **This file is the reasoning, not the rule** — the
constraints it settles live in `docs/DECISIONS.md`, and if the two ever disagree, that one wins.

The document reaches a conclusion that overturns the argument it started from. The conclusion is
first, and the superseded argument is kept below it in §4 because the AOSP and precedent research
it rests on is what the correction was built on.

---

## 1. The answer: SECCOMP_RET_TRAP is a signal, not a verdict

Android's app seccomp filter refuses a syscall with `SECCOMP_RET_TRAP`, which raises **SIGSYS**.
Nothing in a musl process handles SIGSYS, so the default action — terminate — applied, and that
is what killed `import numpy`.

The kernel has *already* made the refused call return `-ENOSYS` by the time the signal is
delivered. Install a handler that reports and **returns**, and the call simply returns ENOSYS as
it already did. numpy imports. numpy and scipy then compute correct answers, under threads and
BLAS, exit 0 across the board (`evidence/step8.7/sigsys-syscall-named.txt`):

```
numpy import:     exit=0  SIGSYS seccomp-refused syscall=235 arch=0xc00000b7  ->  2.5.1
numpy compute:    exit=0  SIGSYS seccomp-refused syscall=235                  ->  dot 25.0
scipy import:     exit=0  SIGSYS seccomp-refused syscall=235                  ->  1.18.0
scipy compute:    exit=0  SIGSYS seccomp-refused syscall=235                  ->  solve [2.0, 3.0]
numpy under load: exit=0  SIGSYS seccomp-refused syscall=235                  ->  threads 4 fft 3175.34
```

Syscall **235 is `mbind`** — NUMA memory policy (`asm-generic/unistd.h`, `__NR_mbind 235`; arm64
uses that table, and `arch=0xc00000b7` is `AUDIT_ARCH_AARCH64`). numpy and OpenBLAS call it
opportunistically and do not care whether it succeeds; on a one-memory-node phone it is a no-op
either way, and on a Linux kernel built without NUMA it returns ENOSYS and every caller copes
silently.

This is implemented as `tools/rootfs/sigsys_report.c` — an 8 KB `LD_PRELOAD` shim that installs a
`sigaction(SIGSYS, SA_SIGINFO)` handler from an ELF constructor, writes `si_syscall` / `si_arch` /
`si_call_addr` with `write(2)`, and returns. It is wired by `SpaceRuntime.sigsysHandler()` and
reports to a **log file, never stderr**: ipykernel forwards a kernel's stderr as cell output, so
reporting there would print "SIGSYS seccomp-refused" into the user's notebook on every numpy
import.

Returning rather than exiting is what turned one number into the whole answer. Continuing is
exactly what the program would have done on a Linux kernel that lacked the syscall, and it lets
one run enumerate *every* distinct refusal instead of stopping at the first.

**Why this is not "patch the libc" in disguise.** Nothing is reimplemented and no libc is
patched. Winlator's `android_sysvshm` *supplies a missing feature* over Unix sockets; this
supplies nothing and merely stops converting "unsupported" into a fatal signal. It is arguably
*more* faithful to Linux than the status quo, since ENOSYS from an unsupported syscall is
ordinary Linux behaviour.

## 2. The four failures, and their three mechanisms

| Component | Failure | Cause | Status |
|---|---|---|---|
| glibc (any binary) | SIGSYS at libc startup | `set_robust_list` refused (`__NR_set_robust_list 99`) | **musl instead of glibc**, `evidence/step8.3/` |
| numpy import | SIGSYS | `mbind` refused and unhandled | **fixed** by §1 — the syscall is not needed |
| `psutil.net_if_addrs` | SIGSEGV | `getifaddrs()` EACCES, return unchecked | bounded: one function, reachable only from a user's own notebook |
| libzmq TCP bind | SIGABRT | same EACCES, `errno_assert` | `jupydroid_kernel.py` selects `IOPubThread(pipe=False)` |

Three mechanisms, not four failures: a **refused syscall** (SIGSYS), an **unchecked EACCES**
dereferenced (SIGSEGV), and an **unchecked EACCES asserted on** (SIGABRT). Only the first is what
§1 addresses; the other two are upstream bugs in how Linux software handles a denied network
query, and each needed its own answer.

The mistake in §4 was subtler than fixing them one at a time: it treated *"the kernel refused a
syscall"* and *"the program cannot proceed"* as the same event. They are not.

## 3. How the maintained projects actually handle it

Neither obvious precedent achieves "every Linux package just works" inside an app process. They
avoid the problem in different ways, and the cost of each is visible in their repositories.

**Termux — rebuild the ecosystem against bionic.** `targetSdkVersion=28`
(`termux-app/gradle.properties:19`; min 21, compile 36), which buys the exec exemption but **not**
seccomp relief: the filter applies to every zygote-spawned process regardless of target SDK.
Termux does not need relief, because its packages are compiled against bionic and therefore only
ever issue syscalls bionic issues. Fidelity is achieved by construction, and paid for by
maintaining a package build farm — `termux-packages` rebuilds the userland rather than importing
Linux binaries. The app ships that as a bootstrap zip, embedded as an assembly blob
(`termux-app/app/src/main/cpp/termux-bootstrap-zip.S`) and unpacked by `TermuxInstaller`, which
replays a `SYMLINKS.txt` manifest with `Os.symlink` (`TermuxInstaller.java:163`, `:210`) — the
half of their design this project adopted, pointed at `nativeLibraryDir`.

**Winlator — patch glibc where Android differs.** It does ship a glibc rootfs and carries
`glibc_patches/`. Those patches are, in their entirety, six files in
`glibc_patches/sysdeps/unix/sysv/linux/`:

```
android_sysvshm.c   android_sysvshm.h   shmat.c   shmctl.c   shmdt.c   shmget.c
```

SysV shared memory reimplemented in userspace over Unix sockets, "based on
https://github.com/pelya/android-shmem" (`android_sysvshm.c:7`, `android_sysvshm.h:15`). Nothing
about the robust list. Nothing general. It is a targeted fix for the one gap Wine needs, not a
fidelity layer, and it works because Winlator only has to make Wine and box64 run — not arbitrary
Linux software.

**So the honest reading of the precedent: nobody solves this inside an app process.** The closest
anyone gets is Termux, and it gets there by rebuilding every package. Projects that run
unmodified Linux userlands faithfully — the AVF "Linux Terminal", QEMU-in-an-APK efforts — all
put a guest kernel underneath.

## 4. The argument that was wrong, and why it was wrong

Kept because its research is accurate and is what §1 is built on. Its **conclusion** is
superseded.

### Why the gap looked unbounded

Android's app seccomp policy is an **allowlist**, and AOSP states what defines it
(`bionic/libc/SECCOMP_ALLOWLIST_APP.TXT`, verbatim):

> This file is used to populate seccomp's allowlist policy in combination with SYSCALLS.TXT. Note
> that the resultant policy is applied only to zygote spawned processes.

`SYSCALLS.TXT` is the set of syscalls **bionic** uses. So the permitted surface is "what bionic
needs", plus a short explicit allowlist, minus a short blocklist (setuid, mount, chroot, reboot,
module loading, clock setting — none of which we want anyway).

The consequence was the whole argument: **anything outside bionic's own usage is denied by
default.** A glibc or musl userland, and every wheel built for Linux, is written against Linux's
syscall surface, not bionic's. The failures are not a list to work through — they are everything
in the gap between the two, and that gap is not enumerable from the policy. It is why
`set_robust_list` is refused despite being used by every pthread program on Linux: bionic does
not call it, so it was never allowlisted.

### Why that seemed to force a VM

"applied only to zygote spawned processes". An app *is* a zygote child; seccomp filters are
inherited across fork and exec and cannot be removed once installed. So no process an app can
create is free of the filter. Fidelity therefore appeared to require that our Linux processes'
syscalls never reach Android's filter at all — a guest kernel servicing them. A VM.

Measured on the Galaxy Tab A9+ (SM-X216B, Snapdragon 680):

```
pm list features | grep virtualization  -> nothing
ls /dev/kvm                             -> No such file or directory
AVF / terminal packages                 -> none
```

No hardware virtualization and no Android Virtualization Framework. AVF exists on Android 15+ but
needs vendor pKVM support and its API is privileged, so it is not a path a normal app can rely on
across devices. That left software emulation — a guest kernel under QEMU TCG — at a large and
never-measured performance cost.

### Where the argument breaks

It conflated the syscall surface a userland *uses* with the surface it *requires*. The gap
between Linux and bionic is real and is not enumerable from the policy — **that part stands**.
But a program only breaks on the part of that gap it cannot tolerate an error from, and that is a
much smaller set. It is also an **empirical** one: run the software and read the reported
refusals.

### What that does to the four options

1. **Guest kernel (fidelity).** No longer required for the scientific stack; its TCG cost need
   not be measured. Unavailable on this hardware anyway.
2. **Bionic userland (Termux's answer).** Reliable and proven, but it means sourcing or building
   the Python stack against bionic and giving up manylinux/musllinux wheels — owning a build
   farm. Not needed.
3. **Patch the libc per gap (Winlator's answer).** Stays rejected: our gap is not one feature
   like SysV shm, it is the whole difference between two syscall surfaces.
4. **Handle SIGSYS and let ENOSYS propagate.** §1. The cheapest of the four, and the one shipped.

The runtime keeps the architecture Step 8 proved: musl rootfs, interpreter exec'd from
`nativeLibraryDir`, stock wheels.

## 5. What this does not fix

Stated plainly so it is not discovered later:

- **It only fixes refusals the caller tolerates.** A package that genuinely *needs* a refused
  syscall still fails, and no handler can change that. This is a live risk, tracked in
  `docs/PLAN.md`.
- **The handler must log every distinct refusal**, as shipped behaviour and not merely as a
  diagnostic. Silently swallowing them would trade loud deaths for quiet wrong answers, which is
  a worse failure mode than the one being fixed.
- **The set is discovered by running software, not derived in advance.** That is a different kind
  of unknown from the one in §4 — bounded per package, and observable — but it is still an
  unknown.
- **`psutil.net_if_addrs` is untouched by any of this.** It is a SIGSEGV from a bad dereference,
  not a refused syscall, and handling *that* would be far more dangerous than the crash.

**What the packaging model owes to none of this:** execve out of `nativeLibraryDir`, the patched
`PT_INTERP`, the installer's symlink, the rootfs in `filesDir`. `jupyter_server`, a real kernel
and `2 + 2 = 4` worked before the correction and after it, and musllinux aarch64 wheels for numpy
and scipy install cleanly.
