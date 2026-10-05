#!/usr/bin/env python3
"""Stages proot for `SpaceIsolationProbeDeviceTest` - a measurement, never a shipped artifact.

Alpine's `proot-static` is a static-pie ELF, so it runs from `nativeLibraryDir` with no loader
surgery (ADR-0002). It carries its own exec loader embedded and, unset, writes that out under
`PROOT_TMP_DIR` and execs it from there - which is `filesDir`, where SELinux refuses `execve` at
targetSdk 36. So the loader is carved out here and shipped beside it as `libproot-loader.so`, and
the probe points `PROOT_LOADER` at it: the bytes are exactly the ones proot would have written.

Output goes to `build/isolation-probe/jniLibs/arm64-v8a/`, which `app/build.gradle.kts` adds as a
jniLibs source only under `-Pjupydroid.isolationProbe`; a normal build never sees it.

The package is pinned by sha256 and lives in Alpine *edge*, which drops superseded revisions, so a
404 here means the pin needs a deliberate bump, not a silent float.

    python3 scripts/stage_isolation_probe.py

Tests in stage_isolation_probe_test.py.
"""
from __future__ import annotations

import hashlib
import io
import pathlib
import struct
import sys
import tarfile
import urllib.request

PROOT_URL = "https://dl-cdn.alpinelinux.org/alpine/edge/community/aarch64/proot-static-5.4.0-r2.apk"
PROOT_SHA256 = "6965c02f281b4d2c46e69f28b48eb9ceea443eb7bc2b9cad3af5163906a184c3"
PROOT_MEMBER = "usr/bin/proot.static"
OUT_DIR = pathlib.Path("build/isolation-probe/jniLibs/arm64-v8a")

ELF_MAGIC = b"\x7fELF"
ELFCLASS64 = 2
ELF64_HEADER_SIZE = 64


def carve_embedded_elf(outer: bytes) -> bytes:
    """Returns the first ELF64 embedded after offset 0, cut at the end of its section table.

    `carve_embedded_elf(proot_static_bytes)` is proot's own loader.
    """
    at = outer.find(ELF_MAGIC, 1)
    while at >= 0:
        header = outer[at:at + ELF64_HEADER_SIZE]
        if len(header) == ELF64_HEADER_SIZE and header[4] == ELFCLASS64:
            return outer[at:at + _elf64_extent(header)]
        at = outer.find(ELF_MAGIC, at + 1)
    raise ValueError(f"no embedded ELF64 in {len(outer)} bytes; expected proot.static with its loader inside")


def _elf64_extent(header: bytes) -> int:
    section_offset, = struct.unpack_from("<Q", header, 0x28)
    entry_size, entry_count = struct.unpack_from("<HH", header, 0x3A)
    return section_offset + entry_size * entry_count


def require_pinned(payload: bytes, expected_sha256: str, name: str) -> None:
    """Refuses bytes whose sha256 is not the pinned one, e.g. `require_pinned(apk, PROOT_SHA256, "proot.apk")`."""
    actual = hashlib.sha256(payload).hexdigest()
    if actual != expected_sha256:
        raise ValueError(f"{name}: sha256 {actual}, expected {expected_sha256}")


def proot_from_apk(apk: bytes) -> bytes:
    """An Alpine `.apk` is concatenated gzip tar streams; `tarfile` reads through all of them."""
    with tarfile.open(fileobj=io.BytesIO(apk), mode="r:gz") as archive:
        member = archive.extractfile(PROOT_MEMBER)
        if member is None:
            raise ValueError(f"{PROOT_URL} has no regular file {PROOT_MEMBER}")
        return member.read()


def main() -> int:
    apk = urllib.request.urlopen(PROOT_URL).read()
    require_pinned(apk, PROOT_SHA256, PROOT_URL)
    proot = proot_from_apk(apk)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "libproot.so").write_bytes(proot)
    (OUT_DIR / "libproot-loader.so").write_bytes(carve_embedded_elf(proot))
    print(f"staged libproot.so and libproot-loader.so into {OUT_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
