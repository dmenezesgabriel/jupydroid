#!/usr/bin/env python3
"""Working Agreement (AGENTS.md): the shell must not ask Android for a syscall it refuses apps.

**P4b-2, and this gate is the defect it closes.** The busybox shipped through P4b was Alpine's
stock build, which sets `CONFIG_FEATURE_SUID=y`. That compiles in `check_suid()`, which busybox
calls on *every* applet dispatch (`libbb/appletlib.c:965`) and which ends, for every
`BB_SUID_DROP` applet, in

    setgid(rgid);
    setuid(ruid);            /* busybox's own comment: "Don't check for errors." */

Both are on bionic's `libc/SECCOMP_BLOCKLIST_APP.TXT` for lp64. An app process is zygote-spawned
and carries that filter, so every applet - `sh`, `ls` and `echo` alike - died of SIGSYS about
seven milliseconds after exec, having printed nothing. Only `busybox` with no applet argument
survived, because that path never reaches `check_suid`. Measured in
`evidence/p4-terminal/busybox-under-app-seccomp.md`.

**P4b's own gate could not see it**, and that is why this one exists. It ran the shell through
`adb shell run-as`, which is not zygote-spawned and therefore not under the app's seccomp filter:
the identical binary passes there and dies in the app. A gate that runs in the wrong security
domain answers a question nobody asked.

**What is checked, and what deliberately is not.** Not "the ELF imports no blocklisted symbol" -
that rule is wrong, and measuring proved it: a busybox built *without* the flag still imports
`setuid` and `setgroups`, because applets like `login` legitimately call them. Nothing dispatches
into those unless the user runs them. What matters is the **dispatch path**, and that leaves an
exact fingerprint: `"must be suid to work properly"` is emitted inside `check_suid` and nowhere
else, so it is in the binary if and only if every applet call goes through `setgid`/`setuid`.

So two things are checked, the cause and the artifact, because either alone can rot:

  1. `tools/rootfs/busybox.config` keeps the flag off, so a rebuild cannot restore it; and
  2. no staged `jniLibs/*/libbusybox.so` carries the `check_suid` fingerprint - which is what
     catches a binary staged from some other build entirely.

Termux, which runs busybox in an Android app process under this same filter, sets
`# CONFIG_FEATURE_SUID is not set` (`termux-packages/packages/busybox/busybox.config:32`). This is
that setting, gated.

Tests in check_busybox_seccomp_test.py, including one that runs the gate over this repository.
"""
from __future__ import annotations

import pathlib
import sys

BUSYBOX_ELFS = "app/src/main/jniLibs/*/libbusybox.so"
BUSYBOX_CONFIG = pathlib.Path("tools/rootfs/busybox.config")
SUID_OFF = "# CONFIG_FEATURE_SUID is not set"

#: Emitted by check_suid() and by nothing else, so its presence *is* the dispatch path.
CHECK_SUID_FINGERPRINT = b"must be suid to work properly"

#: So a renamed fingerprint cannot pass by accident on a file that is not busybox at all.
BUSYBOX_BANNER = b"BusyBox v"


def dispatches_through_check_suid(elf: bytes) -> bool:
    """Whether this busybox routes every applet through `setgid`/`setuid`.

    ```
    dispatches_through_check_suid(pathlib.Path("libbusybox.so").read_bytes())  # -> False, once fixed
    ```
    """
    if BUSYBOX_BANNER not in elf:
        raise ValueError(
            f"expected a busybox binary containing {BUSYBOX_BANNER!r}; "
            f"got {len(elf)} bytes starting {elf[:16]!r}"
        )
    return CHECK_SUID_FINGERPRINT in elf


def main(root: pathlib.Path = pathlib.Path()) -> int:
    """Returns 0 when the shipped busybox asks Android for nothing it refuses."""
    offences: list[str] = []
    config = root / BUSYBOX_CONFIG
    if not config.is_file():
        offences.append(f"{BUSYBOX_CONFIG} is missing; the busybox build has no pinned config")
    elif SUID_OFF not in config.read_text():
        offences.append(f"{BUSYBOX_CONFIG}: expected '{SUID_OFF}'; check_suid() calls setgid/setuid")
    # jniLibs is a gitignored build output (.gitignore:25), so a fresh clone has no ELF to read and
    # there is nothing to fail about. The config above is committed and always checked; this half
    # runs wherever stage-app-artifacts.sh has actually put a binary, which is the only place a
    # bad one can exist.
    for elf in sorted(root.glob(BUSYBOX_ELFS)):
        if dispatches_through_check_suid(elf.read_bytes()):
            offences.append(
                f"{elf.relative_to(root)}: built with CONFIG_FEATURE_SUID, so every applet "
                f"dispatch calls setgid()/setuid() and dies of SIGSYS in an app process"
            )
    for offence in offences:
        print(offence, file=sys.stderr)
    return 1 if offences else 0


if __name__ == "__main__":
    sys.exit(main())
