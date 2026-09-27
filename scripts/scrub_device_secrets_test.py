"""Tests for scrub_device_secrets - the counterpart to check_device_secrets.

The gate refuses a local literal in anything committed; `run_flow` writes the
literal (the serial is in `get-serialno` and in maestro's own banner) every
time. Scrubbing is the same transformation the history rewrite applied, done
at write time instead of commit time.
"""
from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))

import scrub_device_secrets  # noqa: E402


class ScrubDeviceSecretsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        (self.root / ".sensitive-strings.local").write_text("R9XX000==>TABLET_SERIAL\n")

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_replaces_the_literal_with_its_alias(self) -> None:
        evidence = self.root / "evidence"
        evidence.mkdir()
        (evidence / "device.txt").write_text("serial   R9XX000\nmodel    SM-X216B\n")
        scrub_device_secrets.scrub(evidence, self.root)
        self.assertEqual("serial   TABLET_SERIAL\nmodel    SM-X216B\n", (evidence / "device.txt").read_text())

    def test_binary_files_are_untouched(self) -> None:
        evidence = self.root / "evidence"
        evidence.mkdir()
        blob = b"\x00R9XX000\xff"
        (evidence / "frame.png").write_bytes(blob)
        scrub_device_secrets.scrub(evidence, self.root)
        self.assertEqual(blob, (evidence / "frame.png").read_bytes())

    def test_a_clone_without_the_literals_file_is_a_no_op(self) -> None:
        bare = self.root / "clone"
        bare.mkdir()
        (bare / "device.txt").write_text("serial   R9XX000\n")
        scrub_device_secrets.scrub(bare, bare)  # no .sensitive-strings.local under bare/
        self.assertEqual("serial   R9XX000\n", (bare / "device.txt").read_text())

    def test_replacement_applies_inside_maestro_log(self) -> None:
        evidence = self.root / "evidence"
        evidence.mkdir()
        (evidence / "maestro.log").write_text("Running on R9XX000\n")
        scrub_device_secrets.scrub(evidence, self.root)
        self.assertIn("TABLET_SERIAL", (evidence / "maestro.log").read_text())


if __name__ == "__main__":
    unittest.main()
