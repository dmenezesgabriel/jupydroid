"""Tests for stage_proot.py: the carve, the pin and the staged files, on synthetic bytes."""
from __future__ import annotations

import hashlib
import io
import pathlib
import struct
import tarfile
import tempfile
import unittest

import stage_proot as stage


def elf64(section_offset: int, section_count: int, body_size: int) -> bytes:
    """A minimal ELF64 header whose section table ends at offset+count*64, padded to that size."""
    header = bytearray(64)
    header[0:4] = b"\x7fELF"
    header[4] = 2  # ELFCLASS64
    struct.pack_into("<Q", header, 0x28, section_offset)
    struct.pack_into("<HH", header, 0x3A, 64, section_count)
    end = section_offset + section_count * 64
    return bytes(header) + b"\0" * (max(end, body_size) - 64)


class CarveEmbeddedElfTest(unittest.TestCase):
    def test_carves_the_inner_elf_to_its_section_table_end(self):
        inner = elf64(section_offset=200, section_count=3, body_size=0)
        outer = elf64(section_offset=64, section_count=0, body_size=128) + inner + b"trailing"

        self.assertEqual(inner, stage.carve_embedded_elf(outer))

    def test_an_outer_file_with_no_inner_elf_names_its_size(self):
        outer = elf64(section_offset=64, section_count=0, body_size=128)

        with self.assertRaisesRegex(ValueError, "no embedded ELF64 in 128 bytes"):
            stage.carve_embedded_elf(outer)


class RequirePinnedTest(unittest.TestCase):
    def test_matching_bytes_pass(self):
        payload = b"proot"

        stage.require_pinned(payload, hashlib.sha256(payload).hexdigest(), "proot.apk")

    def test_a_mismatch_names_both_digests(self):
        with self.assertRaisesRegex(ValueError, "proot.apk: sha256 .* expected 00"):
            stage.require_pinned(b"proot", "00", "proot.apk")


def apk_holding(member: str, payload: bytes) -> bytes:
    """A one-member gzip tar, which is what an Alpine `.apk` reads as."""
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as archive:
        info = tarfile.TarInfo(member)
        info.size = len(payload)
        archive.addfile(info, io.BytesIO(payload))
    return buffer.getvalue()


class StageIntoTest(unittest.TestCase):
    def setUp(self):
        self.inner = elf64(section_offset=200, section_count=3, body_size=0)
        self.proot = elf64(section_offset=64, section_count=0, body_size=128) + self.inner
        self.out = pathlib.Path(self.enterContext(tempfile.TemporaryDirectory()))

    def test_proot_ships_as_it_came_out_of_the_package(self):
        stage.stage_into(apk_holding(stage.PROOT_MEMBER, self.proot), self.out)

        self.assertEqual(self.proot, (self.out / "libproot.so").read_bytes())

    def test_its_loader_ships_beside_it(self):
        stage.stage_into(apk_holding(stage.PROOT_MEMBER, self.proot), self.out)

        self.assertEqual(self.inner, (self.out / "libproot-loader.so").read_bytes())

    def test_a_package_without_proot_names_the_missing_member(self):
        with self.assertRaisesRegex(KeyError, "proot.static"):
            stage.stage_into(apk_holding("usr/bin/other", self.proot), self.out)


if __name__ == "__main__":
    unittest.main()
