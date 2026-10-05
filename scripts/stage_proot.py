#!/usr/bin/env python3
"""Stages proot and its loader into arm64 `jniLibs`, the runtime every Space runs under (ADR-0073).

Alpine's `proot-static` is a static-pie ELF, so it runs from `nativeLibraryDir` with no loader
surgery (ADR-0002). It carries its own exec loader embedded and, unset, writes that out under
`PROOT_TMP_DIR` and execs it from there - which is `filesDir`, where SELinux refuses `execve` at
targetSdk 36. So the loader is carved out here and shipped beside it as `libproot-loader.so`, and
`ProotCommand` points `PROOT_LOADER` at it: the bytes are exactly the ones proot would have written.

The package is pinned by sha256 and lives in Alpine *edge* - no release branch packages proot for
aarch64 - and edge drops superseded revisions, so a 404 here means the pin needs a deliberate bump,
not a silent float. `tools/rootfs/stage-app-artifacts.sh arm64-v8a` runs it:

    python3 scripts/stage_proot.py app/src/main/jniLibs/arm64-v8a

Tests in stage_proot_test.py.
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


def stage_into(apk: bytes, out_dir: pathlib.Path) -> None:
    """Writes proot and its carved-out loader into `out_dir`, e.g. `stage_into(apk, jni_dir)`."""
    proot = proot_from_apk(apk)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "libproot.so").write_bytes(proot)
    (out_dir / "libproot-loader.so").write_bytes(carve_embedded_elf(proot))


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <jniLibs/arm64-v8a dir>; got {argv[1:]}", file=sys.stderr)
        return 2
    apk = urllib.request.urlopen(PROOT_URL).read()
    require_pinned(apk, PROOT_SHA256, PROOT_URL)
    out_dir = pathlib.Path(argv[1])
    stage_into(apk, out_dir)
    print(f"staged libproot.so and libproot-loader.so into {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
