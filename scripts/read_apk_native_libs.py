#!/usr/bin/env python3
"""Every native library in an APK or AAR, and the RELRO state of each.

**Why this exists.** A MobSF scan read `libzstd-jni-1.5.7-9.so` with no RELRO on
arm64-v8a and partial on the other three ABIs - the only `.so` in the APK below Full.
MobSF's native-libs table is itself a
`readelf` reading; this produces the same fact without the container, so the claim "every
native library has full RELRO" is re-runnable on any build.

**Why `relro_end_16k` is a column.** https://github.com/luben/zstd-jni/issues/381 shows a
Full/Partial read is not the whole fact: upstream shipped a `BIND_NOW` binary (1.5.7-12)
whose RELRO segment still ended 4 KiB past a 16 KiB boundary, and Android's page-size check
rejected it ("RELRO segment not aligned"). Full here means `GNU_RELRO` present *and*
`BIND_NOW`/`FLAGS_1 NOW`; the third column asks whether that segment's end lands on a 16 KiB
page.

**A measurement, not a gate.** Nothing in the build fails on its output.

```sh
python3 scripts/read_apk_native_libs.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/read_apk_native_libs.py /tmp/zstd-jni-1.5.7-24.aar
python3 scripts/read_apk_native_libs.py <dir-of-.so-files>
```

Tests in read_apk_native_libs_test.py.
"""

from __future__ import annotations

import argparse
import dataclasses
import re
import subprocess
import sys
import tempfile
import zipfile
from collections.abc import Callable, Iterable
from pathlib import Path

PAGE_16K = 0x4000

#: A `Program Headers:` row of `readelf -lW`: type, then offset, vaddr, paddr, filesz, memsz,
#: flags and align as hex. Captures vaddr, memsz and align; the flags column (`R`, `RW`,
#: `R E`) is skipped non-greedily so 32- and 64-bit layouts both parse.
PHDR = re.compile(
    r"^\s+(\w+)\s+0x[0-9a-f]+\s+0x([0-9a-f]+)\s+0x[0-9a-f]+\s+0x[0-9a-f]+\s+0x([0-9a-f]+)\s+\S.*?0x([0-9a-f]+)$",
    re.M,
)

#: BIND_NOW shows three ways in a dynamic section: a value of FLAGS or FLAGS_1, or the
#: standalone (BIND_NOW) tag older linkers emit.
BIND_NOW = re.compile(r"\(FLAGS\)[^\n]*BIND_NOW|\(FLAGS_1\)[^\n]*NOW|\(BIND_NOW\)")

#: `lib/<abi>/x.so` inside an APK, `jni/<abi>/x.so` inside an AAR.
NATIVE_ENTRY = re.compile(r"(?:lib|jni)/[^/]+/[^/]+\.so$")

HEADER = ("library", "relro", "load_align", "relro_end_16k")


@dataclasses.dataclass(frozen=True)
class Phdr:
    type: str
    vaddr: int
    memsz: int
    align: int


@dataclasses.dataclass(frozen=True)
class NativeLibRow:
    library: str
    relro: str  # "none" | "partial" | "full"
    load_align: int
    relro_end_16k: str  # "yes" | "no" | "n/a" when there is no GNU_RELRO


def program_headers(readelf: str) -> list[Phdr]:
    """Parses the `Program Headers:` table of a `readelf -lW` run."""
    return [Phdr(m[1], int(m[2], 16), int(m[3], 16), int(m[4], 16)) for m in PHDR.finditer(readelf)]


def binds_now(readelf: str) -> bool:
    return bool(BIND_NOW.search(readelf))


def relro_class(phdrs: list[Phdr], now: bool) -> str:
    """MobSF's three classes: no segment, segment without BIND_NOW, or both."""
    if not any(p.type == "GNU_RELRO" for p in phdrs):
        return "none"
    return "full" if now else "partial"


def relro_end_aligned(phdrs: list[Phdr], page: int = PAGE_16K) -> str:
    """Whether the RELRO segment ends on a page boundary - what Android's 16 KiB check reads."""
    relro = next((p for p in phdrs if p.type == "GNU_RELRO"), None)
    if relro is None:
        return "n/a"
    return "yes" if (relro.vaddr + relro.memsz) % page == 0 else "no"


def load_align(phdrs: list[Phdr]) -> int:
    """The largest LOAD p_align - the other half of the 16 KiB question (`max-page-size`)."""
    return max((p.align for p in phdrs if p.type == "LOAD"), default=0)


def row_for(entry: str, readelf: str) -> NativeLibRow:
    phdrs = program_headers(readelf)
    return NativeLibRow(entry, relro_class(phdrs, binds_now(readelf)), load_align(phdrs), relro_end_aligned(phdrs))


def native_entries(names: Iterable[str]) -> list[str]:
    return sorted(n for n in names if NATIVE_ENTRY.search(n))


def readelf(path: Path) -> str:
    out = subprocess.run(["readelf", "-lW", "-dW", str(path)], check=True, capture_output=True, text=True)
    return out.stdout


def rows_of_archive(archive: Path, read: Callable[[Path], str] = readelf) -> list[NativeLibRow]:
    """Rows for every `lib/` or `jni/` `.so` entry; entry paths are preserved under the temp
    dir because two ABIs can carry the same basename."""
    rows = []
    with zipfile.ZipFile(archive) as zf, tempfile.TemporaryDirectory() as tmp:
        for entry in native_entries(zf.namelist()):
            so = Path(tmp) / entry
            so.parent.mkdir(parents=True, exist_ok=True)
            so.write_bytes(zf.read(entry))
            rows.append(row_for(entry, read(so)))
    return rows


def rows_of_dir(root: Path, read: Callable[[Path], str] = readelf) -> list[NativeLibRow]:
    return [row_for(str(p.relative_to(root)), read(p)) for p in sorted(root.rglob("*.so")) if p.is_file()]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("target", type=Path, help="an APK or AAR, or a directory of .so files")
    args = parser.parse_args(argv)
    target: Path = args.target
    if not target.exists():
        parser.error(f"no such file or directory: {target}")
    rows = rows_of_dir(target) if target.is_dir() else rows_of_archive(target)
    print("\t".join(HEADER))
    for row in rows:
        print("\t".join((row.library, row.relro, hex(row.load_align), row.relro_end_16k)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
