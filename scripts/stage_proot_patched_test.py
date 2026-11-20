"""Tests for stage_proot_patched.py: the arch check and the staged pair, on synthetic bytes."""

from __future__ import annotations

import pathlib
import struct
import tempfile
import unittest

import stage_proot_patched as stage

EM_X86_64 = 62


def elf64(machine: int, section_offset: int, section_count: int, body_size: int = 0) -> bytes:
    """A minimal ELF64 header for `machine`, its section table ending at offset+count*64."""
    header = bytearray(64)
    header[0:4] = b"\x7fELF"
    header[4] = 2  # ELFCLASS64
    struct.pack_into("<H", header, 0x12, machine)
    struct.pack_into("<Q", header, 0x28, section_offset)
    struct.pack_into("<HH", header, 0x3A, 64, section_count)
    end = section_offset + section_count * 64
    return bytes(header) + b"\0" * (max(end, body_size) - 64)


class IsAarch64Elf64Test(unittest.TestCase):
    def test_an_aarch64_elf64_passes(self):
        payload = elf64(stage.EM_AARCH64, section_offset=64, section_count=0, body_size=128)

        self.assertTrue(stage.is_aarch64_elf64(payload))

    def test_another_machine_is_refused(self):
        payload = elf64(EM_X86_64, section_offset=64, section_count=0, body_size=128)

        self.assertFalse(stage.is_aarch64_elf64(payload))

    def test_short_or_foreign_bytes_are_refused(self):
        self.assertFalse(stage.is_aarch64_elf64(b""))
        self.assertFalse(stage.is_aarch64_elf64(b"\x7fELF" + b"\0" * 60))


class StageIntoTest(unittest.TestCase):
    def setUp(self):
        self.inner = elf64(stage.EM_AARCH64, section_offset=200, section_count=3, body_size=0)
        self.proot = elf64(stage.EM_AARCH64, section_offset=64, section_count=0, body_size=128) + self.inner
        self.out = pathlib.Path(self.enterContext(tempfile.TemporaryDirectory()))

    def test_proot_ships_as_it_came_out_of_the_build(self):
        stage.stage_into(self.proot, self.out)

        self.assertEqual(self.proot, (self.out / "libproot.so").read_bytes())

    def test_its_loader_ships_beside_it(self):
        stage.stage_into(self.proot, self.out)

        self.assertEqual(self.inner, (self.out / "libproot-loader.so").read_bytes())

    def test_a_non_aarch64_elf_is_refused_before_anything_is_written(self):
        foreign = elf64(EM_X86_64, section_offset=64, section_count=0, body_size=128) + self.inner

        with self.assertRaisesRegex(ValueError, "not an aarch64 ELF64"):
            stage.stage_into(foreign, self.out)
        self.assertFalse((self.out / "libproot.so").exists())

    def test_an_aarch64_elf_without_an_embedded_loader_names_the_missing_elf(self):
        bare = elf64(stage.EM_AARCH64, section_offset=64, section_count=0, body_size=128)

        with self.assertRaisesRegex(ValueError, "no embedded ELF64"):
            stage.stage_into(bare, self.out)


if __name__ == "__main__":
    unittest.main()
