#!/usr/bin/env python3
"""Working Agreement (AGENTS.md): the shell must not ask Android for a syscall it refuses apps.

**This gate is the defect it closes.** The busybox the app first shipped was Alpine's stock
build, which sets `CONFIG_FEATURE_SUID=y`. That compiles in `check_suid()`, which busybox calls on
*every* applet dispatch (`libbb/appletlib.c:965`) and which ends, for every `BB_SUID_DROP` applet,
in

    setgid(rgid);
    setuid(ruid);            /* busybox's own comment: "Don't check for errors." */

Both are on bionic's `libc/SECCOMP_BLOCKLIST_APP.TXT` for lp64. An app process is zygote-spawned
and carries that filter, so every applet - `sh`, `ls` and `echo` alike - died of SIGSYS about
seven milliseconds after exec, having printed nothing. Only `busybox` with no applet argument
survived, because that path never reaches `check_suid`. Measured on device under the app's own
seccomp filter.

**The first gate could not see it**, and that is why this one exists. It ran the shell through
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
  2. `bin/busybox` inside every shipped `assets/spaces/base/rootfs-*.tzst` - the artifact the
     APK actually carries - does not. Under proot the image's own binary is the one that runs
     (ADR-0073): nothing is staged into `jniLibs` any more, so the tarball is the only path a
     wrong busybox can still take to a device.

Termux, which runs busybox in an Android app process under this same filter, sets
`# CONFIG_FEATURE_SUID is not set` (`termux-packages/packages/busybox/busybox.config:32`). This is
that setting, gated.

Tests in check_busybox_seccomp_test.py, including one that runs the gate over this repository.
"""

from __future__ import annotations

import pathlib
import sys
import tarfile

ROOTFS_TARBALLS = "app/src/main/assets/spaces/base/rootfs-*.tzst"
BUSYBOX_MEMBER = "bin/busybox"
BUSYBOX_CONFIG = pathlib.Path("tools/rootfs/busybox.config")
SUID_OFF = "# CONFIG_FEATURE_SUID is not set"

#: Emitted by check_suid() and by nothing else, so its presence *is* the dispatch path.
CHECK_SUID_FINGERPRINT = b"must be suid to work properly"

#: So a renamed fingerprint cannot pass by accident on a file that is not busybox at all.
BUSYBOX_BANNER = b"BusyBox v"


def dispatches_through_check_suid(elf: bytes) -> bool:
    """Whether this busybox routes every applet through `setgid`/`setuid`.

    ```
    dispatches_through_check_suid(shipped_busybox(pathlib.Path("rootfs-arm64-v8a.tzst")))  # -> False
    ```
    """
    if BUSYBOX_BANNER not in elf:
        raise ValueError(
            f"expected a busybox binary containing {BUSYBOX_BANNER!r}; got {len(elf)} bytes starting {elf[:16]!r}"
        )
    return CHECK_SUID_FINGERPRINT in elf


def shipped_busybox(tarball: pathlib.Path) -> bytes | None:
    """`bin/busybox` out of a shipped rootfs tarball, or None if it carries none."""
    with tarfile.open(tarball, "r:zst") as archive:
        try:
            member = archive.extractfile(BUSYBOX_MEMBER)
        except KeyError:
            return None
        return None if member is None else member.read()


def main(root: pathlib.Path = pathlib.Path()) -> int:
    """Returns 0 when the shipped busybox asks Android for nothing it refuses."""
    offences: list[str] = []
    config = root / BUSYBOX_CONFIG
    if not config.is_file():
        offences.append(f"{BUSYBOX_CONFIG} is missing; the busybox build has no pinned config")
    elif SUID_OFF not in config.read_text():
        offences.append(f"{BUSYBOX_CONFIG}: expected '{SUID_OFF}'; check_suid() calls setgid/setuid")
    # The tarball is a gitignored build output (.gitignore:24), so a fresh clone has no archive to
    # read and there is nothing to fail about. The config above is committed and always checked;
    # this half runs wherever stage-app-artifacts.sh has actually put a tarball, which is the only
    # place a bad one can exist - the image's own bin/busybox is what a Space's shell execs.
    for tarball in sorted(root.glob(ROOTFS_TARBALLS)):
        rel = tarball.relative_to(root)
        busybox = shipped_busybox(tarball)
        if busybox is None:
            offences.append(f"{rel}: carries no {BUSYBOX_MEMBER}; the Space has no shell")
            continue
        try:
            bad = dispatches_through_check_suid(busybox)
        except ValueError as wrong:
            offences.append(f"{rel}: {BUSYBOX_MEMBER} is not what it claims - {wrong}")
            continue
        if bad:
            offences.append(
                f"{rel}: {BUSYBOX_MEMBER} built with CONFIG_FEATURE_SUID, so every applet "
                f"dispatch calls setgid()/setuid() and dies of SIGSYS in an app process"
            )
    for offence in offences:
        print(offence, file=sys.stderr)
    return 1 if offences else 0


if __name__ == "__main__":
    sys.exit(main())
