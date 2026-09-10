#!/usr/bin/env python3
"""Tests for check_shebang_exec.py."""
from __future__ import annotations

import pathlib
import struct
import unittest

from check_shebang_exec import SOURCE_FINGERPRINTS, has_interp_segment, is_the_trampoline, main

REPO = pathlib.Path(__file__).resolve().parent.parent
STAGED = REPO / "app/src/main/jniLibs/arm64-v8a/libshebang.so"


def _elf64(program_header_types: list[int]) -> bytes:
    """A minimal 64-bit LE ELF header plus the given program headers, enough for the parser."""
    e_phoff = 64
    e_phentsize = 56
    header = bytearray(64)
    header[0:4] = b"\x7fELF"
    header[4] = 2  # ELFCLASS64
    header[5] = 1  # little-endian
    header[32:40] = struct.pack("<Q", e_phoff)
    header[54:56] = struct.pack("<H", e_phentsize)
    header[56:58] = struct.pack("<H", len(program_header_types))
    body = b"".join(struct.pack("<I", t) + b"\x00" * (e_phentsize - 4) for t in program_header_types)
    return bytes(header) + body


class HasInterpSegmentTest(unittest.TestCase):
    def test_a_static_elf_has_none(self) -> None:
        self.assertFalse(has_interp_segment(_elf64([1, 1, 2])))  # PT_LOAD, PT_LOAD, PT_DYNAMIC

    def test_a_dynamic_elf_is_caught(self) -> None:
        self.assertTrue(has_interp_segment(_elf64([1, 3, 1])))  # includes PT_INTERP == 3

    def test_it_refuses_a_non_elf(self) -> None:
        with self.assertRaises(ValueError):
            has_interp_segment(b"#!/bin/sh\n")


class IsTheTrampolineTest(unittest.TestCase):
    def test_the_fingerprints_identify_it(self) -> None:
        self.assertTrue(is_the_trampoline(b"..." + b"...".join(SOURCE_FINGERPRINTS) + b"..."))

    def test_some_other_binary_is_not(self) -> None:
        self.assertFalse(is_the_trampoline(b"\x7fELF" + b"busybox" * 8))


class ThisRepositoryTest(unittest.TestCase):
    def test_the_dockerfile_still_builds_and_probes_the_trampoline(self) -> None:
        self.assertEqual(0, main(REPO))

    @unittest.skipUnless(STAGED.is_file(), "jniLibs is a build output; nothing staged here")
    def test_the_staged_arm64_trampoline_is_static_and_ours(self) -> None:
        data = STAGED.read_bytes()
        self.assertTrue(is_the_trampoline(data))
        self.assertFalse(has_interp_segment(data))


if __name__ == "__main__":
    unittest.main()
