# ADR-0073 — A Space owns its rootfs and runs under proot

Status: proposed
Date: 2026-10-04

## Context

A Space is to be a fully isolated system:
- its own rootfs and system packages
- Python environments of the user's own making inside it
- run and stopped as a unit, several at once

That model differs from the one shipped. Every Space reads base's rootfs, base's interpreter and
base's libraries, and owns only its writable directories
([ADR-0072](0072-a-spaces-environment-is-derived-from-its-directory.md)). Two older decisions would
stand in the way:
- [ADR-0002](0002-ship-executables-in-jnilibs-and-reach-them-by-symlink.md) forbids proot and every
  exec interceptor.
- [ADR-0011](0011-install-packages-into-the-spaces-user-site-via-environment.md) calls a virtualenv
  impossible.

Neither claim had been measured.

The binding constraint is unchanged: at `targetSdk 36`, SELinux refuses `execve` of any file under
`filesDir`, rc=126 whatever its mode
(`PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet`).
The SDK level stays. Lowering it to 28, as Winlator (`winlator@fb66541 app/build.gradle`) and
Termux do, would end Play eligibility.

`SpaceIsolationProbeDeviceTest` measured the alternatives on SM-X216B, inside the app's own process
and seccomp filter:
- **A venv works.** `python3 -m venv` links the venv's `python3` to base's stable `usr/bin/python3`,
  so `pyvenv.cfg` sits beside the executable the interpreter sees. `sys.prefix` is the venv, and pip
  installs into it once `PIP_USER` is unset. Its console scripts are refused (126).
- **The musl loader in `nativeLibraryDir` runs a `filesDir` ELF it is handed.**
  `libloader.so <root>/sbin/apk --version` answers. Through it, `apk --root <root> --no-scripts add
  jq git` installs into a private rootfs in 2.23 s, and base is untouched.
- **The loader does not reach that program's own execs (126), nor its absolute paths.**
  `git ls-remote https://…` fails with "unable to find remote helper for 'https'", because git looks
  for helpers in `/usr/libexec/git-core`.
- **proot closes both gaps.** This is Alpine's `proot-static` 5.4.0 as a `nativeLibraryDir` ELF, with
  its embedded loader shipped beside it through `PROOT_LOADER`. Under `-r <root> -0`, the image's
  own Python reports `sys.prefix=/usr`, its own `/bin/sh` runs, `apk add jq git` installs in 2.59 s,
  and git over https answers. A process start costs about 64 ms against the loader's 48 ms.
- **A private rootfs is a full copy:** 407 MB and 13.4–15.4 s to extract per Space. `link(2)` inside
  `filesDir` is refused, so there is no hard-link clone.

The research behind this, including what SageMaker Studio and Winlator actually isolate, is
`docs/research/space-isolation.md`.

## Decision drivers

- A Space's system packages must not be visible to another Space.
- What a Space installs must run, including the children it execs and the absolute paths it reads.
- `targetSdk` stays at 36.
- Other distributions stay possible later without another runtime.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Writable directories over base's shared rootfs (today, ADR-0072) | Cheapest: a Space is about 9 MB until used | System packages are shared, so it is not isolation |
| A private rootfs run through the musl loader | No ptrace; the fastest process start measured (48 ms) | Every child exec gets 126 and absolute paths miss, unless an `LD_PRELOAD` exec-and-path shim is built, which reimplements proot with termux-exec's documented hole (raw `syscall(execve)`) |
| A private rootfs run under proot | Every exec and path is translated; the image runs unmodified; distribution-agnostic | About 16 ms more per process start, ptrace overhead on filesystem-heavy work, and a ptrace dependency on every device |
| targetSdk 28 | Direct exec, as in Winlator and Termux | Off Play; rejected by the user |

## Decision

**Proposed: each Space owns a private copy of the rootfs and runs every process under proot,
shipped in `nativeLibraryDir` with its loader beside it.** A venv inside a Space is the user's tool
and needs no mechanism of its own.

This stays **proposed** until the one thing this decision rests on and nobody has measured holds:
`jupyter_server`, a kernel and `import numpy` running under proot on the tablet. That run covers ZMQ
over the ipc transport, the SIGSYS handler preloaded inside the translated root, and the idle cull.
If it fails, the loader option is the fallback, with the shim's gaps stated.

## Consequences

When accepted, this:
- **supersedes** the part of ADR-0002 that forbids proot, and the part of ADR-0011 that calls a
  virtualenv impossible
- **narrows** ADR-0072, because the writable/readable split becomes the private root itself
- **retires** the base-path surgery: the patched `PT_INTERP`, the console-script trampoline and
  `JUPYDROID_SPACE_ROOT`, because inside a translated root `/usr/bin/python3` is simply itself

Costs accepted knowingly:
- 407 MB and about 14 s per Space created
- about 16 ms per process start
- a ptrace dependency. Termux's own wording for proot is "not stable on all devices"

The thing a later reader will want to "fix" is the full copy per Space. The hard-link clone that
would make it cheap is refused by the platform, not overlooked.
