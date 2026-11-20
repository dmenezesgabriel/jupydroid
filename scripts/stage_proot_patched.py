#!/usr/bin/env python3
"""Stages the patched proot pair into the androidTest jniLibs for the device probe.

`tools/proot/build.sh` produces `build/proot/proot.static`, a musl-static aarch64
ELF carrying the seccomp-SIGSYS patch. It is staged under the shipped pair's own
names - `libproot.so`, with its embedded loader carved out as `libproot-loader.so`
by `stage_proot.carve_embedded_elf` - into the *test* APK's jniLibs, so
`GlibcUnderProotProbeDeviceTest` can exec it from the test package's
`nativeLibraryDir` while the app's own pair stays the shipped one:

    python3 scripts/stage_proot_patched.py build/proot/proot.static app/src/androidTest/jniLibs/arm64-v8a

The directory is gitignored: the pair is a build artifact, reproducible from
tools/proot/.
"""

from __future__ import annotations

import pathlib
import struct
import sys

import stage_proot

ELF64_MACHINE_OFFSET = 0x12
EM_AARCH64 = 183


def is_aarch64_elf64(payload: bytes) -> bool:
    """Whether `payload` is an ELF64 for aarch64, e.g. the proot.static build.sh wrote."""
    if len(payload) < stage_proot.ELF64_HEADER_SIZE:
        return False
    header = payload[: stage_proot.ELF64_HEADER_SIZE]
    if header[:4] != stage_proot.ELF_MAGIC or header[4] != stage_proot.ELFCLASS64:
        return False
    machine: int = struct.unpack_from("<H", header, ELF64_MACHINE_OFFSET)[0]
    return machine == EM_AARCH64


def stage_into(proot: bytes, out_dir: pathlib.Path) -> None:
    """Writes the pair `stage_proot.stage_into` writes, from patched bytes instead of the APK."""
    if not is_aarch64_elf64(proot):
        raise ValueError(f"not an aarch64 ELF64 ({len(proot)} bytes); expected proot.static from tools/proot/build.sh")
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "libproot.so").write_bytes(proot)
    (out_dir / "libproot-loader.so").write_bytes(stage_proot.carve_embedded_elf(proot))


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(f"usage: {argv[0]} <proot.static> <jniLibs/arm64-v8a dir>; got {argv[1:]}", file=sys.stderr)
        return 2
    proot = pathlib.Path(argv[1]).read_bytes()
    out_dir = pathlib.Path(argv[2])
    stage_into(proot, out_dir)
    print(f"staged libproot.so and libproot-loader.so into {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
