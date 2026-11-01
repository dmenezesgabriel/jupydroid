#!/usr/bin/env python3
"""Tests for read_apk_native_libs.py.

The fixtures are verbatim `readelf -lW` + `readelf -dW` output of the real zstd-jni AAR
binaries on Maven Central: 1.5.7-9 (what the APK ships today), 1.5.7-12 (Full RELRO whose
RELRO segment still fails a 16 KiB device - luben/zstd-jni#381) and 1.5.7-24 (the fix).
"""
from __future__ import annotations

import unittest
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory

from read_apk_native_libs import (
    NativeLibRow,
    binds_now,
    native_entries,
    program_headers,
    relro_class,
    relro_end_aligned,
    row_for,
    rows_of_archive,
    rows_of_dir,
)

#: com.github.luben:zstd-jni:1.5.7-9, jni/arm64-v8a - no GNU_RELRO segment at all.
ARM64_1_5_7_9 = """
Elf file type is DYN (Shared object file)
Entry point 0xfa90
There are 7 program headers, starting at offset 64

Program Headers:
  Type           Offset   VirtAddr           PhysAddr           FileSiz  MemSiz   Flg Align
  LOAD           0x000000 0x0000000000000000 0x0000000000000000 0x091be8 0x091be8 R E 0x4000
  LOAD           0x092000 0x0000000000096000 0x0000000000096000 0x000f48 0x000fe0 RW  0x4000
  DYNAMIC        0x0922a0 0x00000000000962a0 0x00000000000962a0 0x0001b0 0x0001b0 RW  0x8
  NOTE           0x091b30 0x0000000000091b30 0x0000000000091b30 0x000098 0x000098 R   0x4
  NOTE           0x091bc8 0x0000000000091bc8 0x0000000000091bc8 0x000020 0x000020 R   0x8
  GNU_EH_FRAME   0x07c118 0x000000000007c118 0x000000000007c118 0x004bdc 0x004bdc R   0x4
  GNU_STACK      0x000000 0x0000000000000000 0x0000000000000000 0x000000 0x000000 RW  0x10

 Section to Segment mapping:
  Segment Sections...
   00     .hash .dynsym .dynstr .gnu.version .gnu.version_r .rela.dyn .rela.plt .plt .text .rodata .eh_frame_hdr .eh_frame .note.android.ident .note.gnu.property
   01     .fini_array .data.rel.ro .dynamic .got .got.plt .data .bss
   02     .dynamic
   03     .note.android.ident
   04     .note.gnu.property
   05     .eh_frame_hdr
   06

Dynamic section at offset 0x922a0 contains 23 entries:
  Tag        Type                         Name/Value
 0x0000000000000001 (NEEDED)             Shared library: [libm.so]
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so]
 0x000000000000000e (SONAME)             Library soname: [libzstd-jni-1.5.7-9.so]
 0x000000000000001a (FINI_ARRAY)         0x96000
 0x000000000000001c (FINI_ARRAYSZ)       16 (bytes)
 0x0000000000000004 (HASH)               0x1c8
 0x0000000000000005 (STRTAB)             0x5d68
 0x0000000000000006 (SYMTAB)             0x15e0
 0x000000000000000a (STRSZ)              23155 (bytes)
 0x000000000000000b (SYMENT)             24 (bytes)
 0x0000000000000003 (PLTGOT)             0x964a0
 0x0000000000000002 (PLTRELSZ)           8088 (bytes)
 0x0000000000000014 (PLTREL)             RELA
 0x0000000000000017 (JMPREL)             0xc5c0
 0x0000000000000007 (RELA)               0xbdf8
 0x0000000000000008 (RELASZ)             1992 (bytes)
 0x0000000000000009 (RELAENT)            24 (bytes)
 0x000000006ffffffe (VERNEED)            0xbdd8
 0x000000006fffffff (VERNEEDNUM)         1
 0x000000006ffffff0 (VERSYM)             0xb7dc
 0x000000006ffffff9 (RELACOUNT)          23
 0x0000000000000000 (NULL)               0x0
"""

#: com.github.luben:zstd-jni:1.5.7-9, jni/armeabi-v7a - the segment exists, no BIND_NOW.
ARMV7_1_5_7_9 = """
Elf file type is DYN (Shared object file)
Entry point 0x0
There are 8 program headers, starting at offset 52

Program Headers:
  Type           Offset   VirtAddr   PhysAddr   FileSiz MemSiz  Flg Align
  PHDR           0x000034 0x00000034 0x00000034 0x00100 0x00100 R   0x4
  LOAD           0x000000 0x00000000 0x00000000 0x72f68 0x72f68 R E 0x4000
  LOAD           0x073d84 0x00077d84 0x00077d84 0x0094c 0x009a0 RW  0x4000
  DYNAMIC        0x073f10 0x00077f10 0x00077f10 0x000f0 0x000f0 RW  0x4
  NOTE           0x000134 0x00000134 0x00000134 0x00098 0x00098 R   0x4
  GNU_STACK      0x000000 0x00000000 0x00000000 0x00000 0x00000 RW  0x10
  ARM_EXIDX      0x0694d0 0x000694d0 0x000694d0 0x01f88 0x01f88 R   0x4
  GNU_RELRO      0x073d84 0x00077d84 0x00077d84 0x0027c 0x0027c RW  0x4

 Section to Segment mapping:
  Segment Sections...
   00
   01     .note.android.ident .dynsym .dynstr .hash .gnu.version .gnu.version_d .gnu.version_r .rel.dyn .rel.plt .plt .text .ARM.exidx .ARM.extab .rodata
   02     .fini_array .data.rel.ro .dynamic .data .got .bss
   03     .dynamic
   04     .note.android.ident
   05
   06     .ARM.exidx
   07     .fini_array .data.rel.ro .dynamic

Dynamic section at offset 0x73f10 contains 25 entries:
  Tag        Type                         Name/Value
 0x00000003 (PLTGOT)                     0x78038
 0x00000002 (PLTRELSZ)                   3352 (bytes)
 0x00000017 (JMPREL)                     0xb13c
 0x00000014 (PLTREL)                     REL
 0x00000011 (REL)                        0xae14
 0x00000012 (RELSZ)                      808 (bytes)
 0x00000013 (RELENT)                     8 (bytes)
 0x6ffffffa (RELCOUNT)                   38
 0x00000006 (SYMTAB)                     0x1cc
 0x0000000b (SYMENT)                     16 (bytes)
 0x00000005 (STRTAB)                     0x34ac
 0x0000000a (STRSZ)                      24012 (bytes)
 0x00000004 (HASH)                       0x9278
 0x00000001 (NEEDED)                     Shared library: [libm.so]
 0x00000001 (NEEDED)                     Shared library: [libdl.so]
 0x00000001 (NEEDED)                     Shared library: [libc.so]
 0x0000000e (SONAME)                     Library soname: [libzstd-jni-1.5.7-9.so]
 0x0000001a (FINI_ARRAY)                 0x77d84
 0x0000001c (FINI_ARRAYSZ)               8 (bytes)
 0x6ffffff0 (VERSYM)                     0xa75c
 0x6ffffffc (VERDEF)                     0xadb8
 0x6ffffffd (VERDEFNUM)                  1
 0x6ffffffe (VERNEED)                    0xadd4
 0x6fffffff (VERNEEDNUM)                 2
 0x00000000 (NULL)                       0x0
"""

#: com.github.luben:zstd-jni:1.5.7-12, jni/arm64-v8a - Full RELRO that ends 4 KiB past a
#: 16 KiB boundary: the shape a plain Full/Partial read misses (luben/zstd-jni#381).
ARM64_1_5_7_12 = """
Elf file type is DYN (Shared object file)
Entry point 0x2c000
There are 9 program headers, starting at offset 64

Program Headers:
  Type           Offset   VirtAddr           PhysAddr           FileSiz  MemSiz   Flg Align
  PHDR           0x000040 0x0000000000000040 0x0000000000000040 0x0001f8 0x0001f8 R   0x8
  LOAD           0x000000 0x0000000000000000 0x0000000000000000 0x02b584 0x02b584 R   0x4000
  LOAD           0x02c000 0x000000000002c000 0x000000000002c000 0x067fc0 0x067fc0 R E 0x4000
  LOAD           0x094000 0x0000000000094000 0x0000000000094000 0x004f28 0x008098 RW  0x4000
  DYNAMIC        0x0982a0 0x00000000000982a0 0x00000000000982a0 0x0001a0 0x0001a0 RW  0x8
  GNU_RELRO      0x098000 0x0000000000098000 0x0000000000098000 0x000f28 0x001000 R   0x1
  GNU_EH_FRAME   0x015b4c 0x0000000000015b4c 0x0000000000015b4c 0x004be4 0x004be4 R   0x4
  GNU_STACK      0x000000 0x0000000000000000 0x0000000000000000 0x000000 0x000000 RW  0
  NOTE           0x00fee0 0x000000000000fee0 0x000000000000fee0 0x0000b8 0x0000b8 R   0x8

 Section to Segment mapping:
  Segment Sections...
   00
   01     .dynsym .gnu.version .gnu.version_r .gnu.hash .hash .dynstr .rela.dyn .rela.plt .note.android.ident .note.gnu.property .rodata .eh_frame_hdr .eh_frame
   02     .text .plt
   03     .data .fini_array .data.rel.ro .dynamic .got .got.plt .bss
   04     .dynamic
   05     .fini_array .data.rel.ro .dynamic .got .got.plt
   06     .eh_frame_hdr
   07
   08     .note.android.ident .note.gnu.property

Dynamic section at offset 0x982a0 contains 26 entries:
  Tag        Type                         Name/Value
 0x0000000000000001 (NEEDED)             Shared library: [libm.so]
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so]
 0x000000000000000e (SONAME)             Library soname: [libzstd-jni-1.5.7-12.so]
 0x000000000000001e (FLAGS)              BIND_NOW
 0x000000006ffffffb (FLAGS_1)            Flags: NOW
 0x0000000000000007 (RELA)               0xd780
 0x0000000000000008 (RELASZ)             1992 (bytes)
 0x0000000000000009 (RELAENT)            24 (bytes)
 0x000000006ffffff9 (RELACOUNT)          23
 0x0000000000000017 (JMPREL)             0xdf48
 0x0000000000000002 (PLTRELSZ)           8088 (bytes)
 0x0000000000000003 (PLTGOT)             0x98488
 0x0000000000000014 (PLTREL)             RELA
 0x0000000000000006 (SYMTAB)             0x238
 0x000000000000000b (SYMENT)             24 (bytes)
 0x0000000000000005 (STRTAB)             0x7d24
 0x000000000000000a (STRSZ)              23132 (bytes)
 0x000000006ffffef5 (GNU_HASH)           0x4f40
 0x0000000000000004 (HASH)               0x6574
 0x000000000000001a (FINI_ARRAY)         0x98000
 0x000000000000001c (FINI_ARRAYSZ)       16 (bytes)
 0x000000006ffffff0 (VERSYM)             0x4930
 0x000000006ffffffe (VERNEED)            0x4f1c
 0x000000006fffffff (VERNEEDNUM)         1
 0x0000000000000000 (NULL)               0x0
"""

#: com.github.luben:zstd-jni:1.5.7-24, jni/arm64-v8a - Full RELRO ending on a 16 KiB boundary.
ARM64_1_5_7_24 = """
Elf file type is DYN (Shared object file)
Entry point 0x0
There are 9 program headers, starting at offset 64

Program Headers:
  Type           Offset   VirtAddr           PhysAddr           FileSiz  MemSiz   Flg Align
  PHDR           0x000040 0x0000000000000040 0x0000000000000040 0x0001f8 0x0001f8 R   0x8
  LOAD           0x000000 0x0000000000000000 0x0000000000000000 0x0755a0 0x0755a0 R E 0x4000
  LOAD           0x0755a0 0x00000000000795a0 0x00000000000795a0 0x000c00 0x002a60 RW  0x4000
  LOAD           0x0761a0 0x000000000007e1a0 0x000000000007e1a0 0x000000 0x000090 RW  0x4000
  DYNAMIC        0x0757d8 0x00000000000797d8 0x00000000000797d8 0x0001a0 0x0001a0 RW  0x8
  GNU_RELRO      0x0755a0 0x00000000000795a0 0x00000000000795a0 0x000c00 0x002a60 R   0x1
  GNU_EH_FRAME   0x012000 0x0000000000012000 0x0000000000012000 0x003ed4 0x003ed4 R   0x4
  GNU_STACK      0x000000 0x0000000000000000 0x0000000000000000 0x000000 0x000000 RW  0
  NOTE           0x000238 0x0000000000000238 0x0000000000000238 0x000098 0x000098 R   0x4

 Section to Segment mapping:
  Segment Sections...
   00
   01     .note.android.ident .dynsym .gnu.version .gnu.version_r .gnu.hash .hash .dynstr .rela.dyn .rela.plt .rodata .eh_frame_hdr .eh_frame .text .plt
   02     .data.rel.ro .fini_array .dynamic .got .got.plt .relro_padding
   03     .bss
   04     .dynamic
   05     .data.rel.ro .fini_array .dynamic .got .got.plt .relro_padding
   06     .eh_frame_hdr
   07
   08     .note.android.ident

Dynamic section at offset 0x757d8 contains 26 entries:
  Tag        Type                         Name/Value
 0x0000000000000001 (NEEDED)             Shared library: [libm.so]
 0x0000000000000001 (NEEDED)             Shared library: [libdl.so]
 0x0000000000000001 (NEEDED)             Shared library: [libc.so]
 0x000000000000000e (SONAME)             Library soname: [libzstd-jni-1.5.7-24.so]
 0x000000000000001e (FLAGS)              BIND_NOW
 0x000000006ffffffb (FLAGS_1)            Flags: NOW
 0x0000000000000007 (RELA)               0xbb68
 0x0000000000000008 (RELASZ)             1704 (bytes)
 0x0000000000000009 (RELAENT)            24 (bytes)
 0x000000006ffffff9 (RELACOUNT)          19
 0x0000000000000017 (JMPREL)             0xc210
 0x0000000000000002 (PLTRELSZ)           6024 (bytes)
 0x0000000000000003 (PLTGOT)             0x799b0
 0x0000000000000014 (PLTREL)             RELA
 0x0000000000000006 (SYMTAB)             0x2d0
 0x000000000000000b (SYMENT)             24 (bytes)
 0x0000000000000005 (STRTAB)             0x68ac
 0x000000000000000a (STRSZ)              21174 (bytes)
 0x000000006ffffef5 (GNU_HASH)           0x4478
 0x0000000000000004 (HASH)               0x547c
 0x000000000000001a (FINI_ARRAY)         0x797c8
 0x000000000000001c (FINI_ARRAYSZ)       16 (bytes)
 0x000000006ffffff0 (VERSYM)             0x3f48
 0x000000006ffffffe (VERNEED)            0x4454
 0x000000006fffffff (VERNEEDNUM)         1
 0x0000000000000000 (NULL)               0x0
"""


class ProgramHeadersTest(unittest.TestCase):
    def test_parses_type_vaddr_memsz_and_align(self) -> None:
        headers = program_headers(ARM64_1_5_7_24)
        relro = next(h for h in headers if h.type == "GNU_RELRO")
        self.assertEqual(relro.vaddr, 0x795A0)
        self.assertEqual(relro.memsz, 0x2A60)
        self.assertEqual(relro.align, 0x1)

    def test_32bit_addresses(self) -> None:
        headers = program_headers(ARMV7_1_5_7_9)
        relro = next(h for h in headers if h.type == "GNU_RELRO")
        self.assertEqual(relro.vaddr, 0x77D84)

    def test_section_mapping_lines_are_not_headers(self) -> None:
        self.assertEqual(len(program_headers(ARM64_1_5_7_9)), 7)


class BindsNowTest(unittest.TestCase):
    def test_flags_and_flags_1(self) -> None:
        self.assertTrue(binds_now(ARM64_1_5_7_24))

    def test_no_flags(self) -> None:
        self.assertFalse(binds_now(ARMV7_1_5_7_9))

    def test_standalone_bind_now_tag(self) -> None:
        self.assertTrue(binds_now(" 0x0000000000000018 (BIND_NOW)"))


class RowForTest(unittest.TestCase):
    def test_arm64_1_5_7_9_has_no_relro(self) -> None:
        row = row_for("jni/arm64-v8a/libzstd-jni-1.5.7-9.so", ARM64_1_5_7_9)
        self.assertEqual(
            row, NativeLibRow("jni/arm64-v8a/libzstd-jni-1.5.7-9.so", "none", 0x4000, "n/a")
        )

    def test_armv7_1_5_7_9_is_partial(self) -> None:
        row = row_for("jni/armeabi-v7a/libzstd-jni-1.5.7-9.so", ARMV7_1_5_7_9)
        self.assertEqual(row.relro, "partial")
        self.assertEqual(row.relro_end_16k, "yes")

    def test_arm64_1_5_7_12_is_full_but_ends_misaligned(self) -> None:
        row = row_for("jni/arm64-v8a/libzstd-jni-1.5.7-12.so", ARM64_1_5_7_12)
        self.assertEqual(row.relro, "full")
        self.assertEqual(row.relro_end_16k, "no")

    def test_arm64_1_5_7_24_is_full_and_aligned(self) -> None:
        row = row_for("jni/arm64-v8a/libzstd-jni-1.5.7-24.so", ARM64_1_5_7_24)
        self.assertEqual(row.relro, "full")
        self.assertEqual(row.relro_end_16k, "yes")


class NativeEntriesTest(unittest.TestCase):
    def test_keeps_lib_and_jni_so_entries_sorted(self) -> None:
        entries = native_entries(
            ["classes.jar", "jni/x86/b.so", "lib/arm64-v8a/a.so", "jni/arm64-v8a/", "AndroidManifest.xml"]
        )
        self.assertEqual(entries, ["jni/x86/b.so", "lib/arm64-v8a/a.so"])

    def test_ignores_files_that_are_not_under_an_abi_dir(self) -> None:
        self.assertEqual(native_entries(["lib/a.so", "lib/arm64-v8a/nested/b.so"]), [])


class RelroEndAlignedTest(unittest.TestCase):
    def test_missing_segment_is_na(self) -> None:
        self.assertEqual(relro_end_aligned(program_headers(ARM64_1_5_7_9)), "n/a")

    def test_relro_class_needs_segment_and_bind_now(self) -> None:
        self.assertEqual(relro_class(program_headers(ARMV7_1_5_7_9), False), "partial")
        self.assertEqual(relro_class(program_headers(ARMV7_1_5_7_9), True), "full")
        self.assertEqual(relro_class(program_headers(ARM64_1_5_7_9), True), "none")


class RowsOfTest(unittest.TestCase):
    def test_archive_reads_each_abi_entry(self) -> None:
        with TemporaryDirectory() as tmp:
            archive = Path(tmp) / "lib.aar"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("classes.jar", b"jar")
                # The same basename under two ABIs: extraction must not clobber one with the other.
                zf.writestr("jni/arm64-v8a/same-name.so", b"arm64")
                zf.writestr("jni/x86/same-name.so", b"x86")
            def read(path: object) -> str:
                return ARM64_1_5_7_24 if "v8a" in str(path) else ARMV7_1_5_7_9

            rows = rows_of_archive(archive, read)
        self.assertEqual([r.library for r in rows], ["jni/arm64-v8a/same-name.so", "jni/x86/same-name.so"])
        self.assertEqual([r.relro for r in rows], ["full", "partial"])

    def test_dir_reads_so_rows(self) -> None:
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "lib/arm64-v8a").mkdir(parents=True)
            (root / "lib/arm64-v8a/real.so").write_bytes(b"elf")
            (root / "classes.jar").write_bytes(b"jar")
            rows = rows_of_dir(root, lambda _p: ARM64_1_5_7_24)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0].library, "lib/arm64-v8a/real.so")


if __name__ == "__main__":
    unittest.main()
