#!/usr/bin/env python3
"""Working Agreement (CLAUDE.md): the console-script trampoline must be the one that was built.

**P7e-2, and this gate is the same shape as `check_busybox_seccomp.py`.** `pip`, `jupyter`,
`ipython` and 28 other console scripts ship in the rootfs as `#!/usr/bin/python3` text files under
`filesDir`, where SELinux refuses `execve` whatever the mode
(`PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet`). The
kernel will not read the `#!` line, so `tools/rootfs/shebang_exec.c` does - reached by name through
`usr/local/bin`, resolving to `nativeLibraryDir/libshebang.so`, which Android *will* exec.

**What is checked, and why each half can rot alone:**

  1. `tools/rootfs/Dockerfile` keeps the `shebang-artifact` target and the dispatch probe, so a
     build that stopped compiling or checking the trampoline fails here rather than on the device;
     and
  2. a staged `jniLibs/*/libshebang.so` is the trampoline - it carries the two strings the source
     emits (`shebang-exec:` and `JUPYDROID_SPACE_ROOT`) and is a **statically linked** ELF with no
     `PT_INTERP`, which is what lets it ship with none of `stage-app-artifacts.sh`'s loader
     surgery. A dynamic ELF here would dangle on the first app update.

The build-time probe in the Dockerfile is the behavioural check (it dispatches, finds the script
and not itself, passes args through, honours a `#!/usr/bin/env` arg); this gate is the
does-the-artifact-match-the-source half, the way the busybox gate splits its config check from its
fingerprint check.

Tests in check_shebang_exec_test.py, including one that runs the gate over this repository.
"""
from __future__ import annotations

import pathlib
import sys

SHEBANG_ELFS = "app/src/main/jniLibs/*/libshebang.so"
DOCKERFILE = pathlib.Path("tools/rootfs/Dockerfile")

#: The Dockerfile must still build the trampoline and prove it dispatches.
DOCKERFILE_MARKERS = ("FROM scratch AS shebang-artifact", "shebang-exec dispatches")

#: Emitted by shebang_exec.c and nothing else here - its presence says this file is the trampoline.
SOURCE_FINGERPRINTS = (b"shebang-exec: %s: %s", b"JUPYDROID_SPACE_ROOT")

#: ELF e_type. A trampoline is ET_EXEC (2, static-nopie) or ET_DYN (3); only ET_EXEC has no
#: PT_INTERP to go stale. musl -static links ET_EXEC.
ELF_MAGIC = b"\x7fELF"
PT_INTERP = 3


def has_interp_segment(elf: bytes) -> bool:
    """True if this 64-bit little-endian ELF carries a PT_INTERP program header.

    ```
    has_interp_segment(pathlib.Path("libshebang.so").read_bytes())  # -> False, it is -static
    ```
    """
    if elf[:4] != ELF_MAGIC:
        raise ValueError(f"not an ELF: {len(elf)} bytes starting {elf[:16]!r}")
    if elf[4] != 2:  # EI_CLASS: 2 == ELFCLASS64, which is all this app ships
        raise ValueError(f"expected a 64-bit ELF; EI_CLASS byte is {elf[4]}")
    e_phoff = int.from_bytes(elf[32:40], "little")
    e_phentsize = int.from_bytes(elf[54:56], "little")
    e_phnum = int.from_bytes(elf[56:58], "little")
    for i in range(e_phnum):
        p_type = int.from_bytes(elf[e_phoff + i * e_phentsize : e_phoff + i * e_phentsize + 4], "little")
        if p_type == PT_INTERP:
            return True
    return False


def is_the_trampoline(elf: bytes) -> bool:
    """Whether this ELF is the shebang_exec.c build - by the strings it emits."""
    return all(fp in elf for fp in SOURCE_FINGERPRINTS)


def main(root: pathlib.Path = pathlib.Path()) -> int:
    """Returns 0 when the Dockerfile still builds the trampoline and every staged ELF is it."""
    offences: list[str] = []

    dockerfile = root / DOCKERFILE
    if not dockerfile.is_file():
        offences.append(f"{DOCKERFILE} is missing; nothing builds the trampoline")
    else:
        text = dockerfile.read_text()
        for marker in DOCKERFILE_MARKERS:
            if marker not in text:
                offences.append(f"{DOCKERFILE}: '{marker}' is gone; the trampoline is unbuilt or unchecked")

    # jniLibs is a gitignored build output (.gitignore:25); a fresh clone has nothing here to fail.
    for elf in sorted(root.glob(SHEBANG_ELFS)):
        data = elf.read_bytes()
        rel = elf.relative_to(root)
        if not is_the_trampoline(data):
            offences.append(f"{rel}: not the shebang_exec.c build (missing {SOURCE_FINGERPRINTS})")
            continue
        if has_interp_segment(data):
            offences.append(f"{rel}: has a PT_INTERP segment; a dynamic trampoline dangles on an app update")

    for offence in offences:
        print(offence, file=sys.stderr)
    return 1 if offences else 0


if __name__ == "__main__":
    sys.exit(main())
