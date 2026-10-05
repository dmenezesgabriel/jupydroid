# ADR-0006 — Build busybox here, with `CONFIG_FEATURE_SUID` compiled out

Status: accepted
Date: 2026-08-29

## Context

**Alpine's busybox cannot run a single applet in this app's process.** It is built with
`CONFIG_FEATURE_SUID=y`, which compiles in `check_suid()` — called on *every* applet dispatch
(`libbb/appletlib.c`) and ending, for every `BB_SUID_DROP` applet, in `setgid(rgid); setuid(ruid);`.

Both calls are on bionic's `libc/SECCOMP_BLOCKLIST_APP.TXT` for lp64. An app process is
zygote-spawned and carries that filter, and busybox has no SIGSYS handler — so `sh`, `ls` and `echo`
alike were killed about seven milliseconds after exec, having printed nothing.

## Decision drivers

- The terminal needs a real shell, not a re-implementation.
- The fix must be provable from inside the app process, where the filter actually applies.

## Options considered

| Option | Pro | Con |
|---|---|---|
| `apk add busybox` from Alpine | Nothing to build | Killed on every applet dispatch by `setuid`/`setgid` under the app seccomp filter |
| Give busybox a SIGSYS handler | Reuses [ADR-0004](0004-handle-sigsys-and-return.md) | `setuid` returning ENOSYS mid-`check_suid` is a security-relevant path to leave half-executed; compiling it out is simpler and exact |
| Build it here with the flag flipped | The calls are gone at compile time, and it is the reference implementation's own setting | We own a build step |

## Decision

`tools/rootfs/Dockerfile` builds busybox from **Alpine's own `busyboxconfig`** for the same version
with **one line flipped** (`tools/rootfs/busybox.config`). `CONFIG_FEATURE_SUID=n` replaces
`check_suid` with `#define check_suid(x) ((void)0)`, so the calls are gone at compile time.

**This is the reference implementation's setting, not an invention.** Termux runs busybox in an
Android app process under this same filter and sets `# CONFIG_FEATURE_SUID is not set`. Alpine sets
it the other way because Alpine is a distribution, where `su` and `passwd` need it.

**utmps is linked statically**, following Alpine's *static* recipe rather than its dynamic one. The
rootfs carries no libutmps — the base image's busybox is Alpine's BOOTSTRAP build, which skips it —
so a dynamic link made every applet fail on device with
`Error loading shared library libutmps.so.0.1`.

## Consequences

**This forbids** taking busybox from `apk add` again. `scripts/check_busybox_seccomp.py` fails on a
config that restores the flag and on any staged `libbusybox.so` carrying the `check_suid`
fingerprint.

**A `run-as` gate cannot enforce this.** That process is not under the app's seccomp filter, which is
exactly how this defect once shipped green. The device gate is `BusyBoxAppletsDeviceTest`, which
forks applets from inside the app.

The thing a future reader will be tempted to "fix" is the custom build, on the grounds that a
distribution package would be simpler to maintain. It would not run.

## Amendment — 2026-10-05

Amended by [ADR-0073](0073-a-space-owns-its-rootfs-and-runs-under-proot.md): nothing ships as
`libbusybox.so` any more — under proot the rootfs's own `bin/busybox` is what runs, so the gate's
binary arm moved with the bytes: it reads `bin/busybox` out of the shipped
`assets/spaces/base/rootfs-*.tzst`, fingerprinting the exact file the APK carries. The config arm
and the forbids are unchanged.
