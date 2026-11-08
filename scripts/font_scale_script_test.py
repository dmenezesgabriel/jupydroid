"""Tests for font-scale.sh - the font scale a driver sets for a capture and puts back on exit.

The tablet's own scale is the user's (1.1 when it was read), so a driver that left 2.0 behind would
change every screen on a tablet someone also uses.

    python3 -m unittest discover -s scripts -p '*_test.py'
"""

from __future__ import annotations

import os
import pathlib
import subprocess
import tempfile
import unittest

FONT_SCALE_SCRIPT = pathlib.Path(__file__).parent / "font-scale.sh"

#: What `adb` prints for `settings get system font_scale`, and a log of every other call it receives.
FAKE_ADB = """#!/bin/sh
case "$*" in
  *"settings get system font_scale"*) printf '%s\\r\\n' "$FAKE_FONT_SCALE" ;;
  *) echo "$*" >>"$FAKE_ADB_LOG" ;;
esac
"""


class FakeAdbDevice:
    """A throwaway `adb` on PATH that holds a font scale and logs the settings written to it."""

    def __init__(self, root: pathlib.Path, font_scale: str) -> None:
        self.log = root / "adb.log"
        self.log.write_text("")
        adb = root / "adb"
        adb.write_text(FAKE_ADB)
        adb.chmod(0o755)
        self.env = {
            "PATH": f"{root}:{os.environ['PATH']}",
            "FAKE_FONT_SCALE": font_scale,
            "FAKE_ADB_LOG": str(self.log),
        }

    def run(self, calls: str) -> list[str]:
        """The `adb` calls made by [calls], run in a shell that sourced font-scale.sh with `adb` as its adb."""
        script = f'adb=(adb); . "{FONT_SCALE_SCRIPT}"; {calls}'
        subprocess.run(["bash", "-c", script], env=self.env, check=True, capture_output=True, text=True)
        return self.log.read_text().splitlines()


class FontScaleScriptTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_it_sets_the_scale_it_is_given(self) -> None:
        device = FakeAdbDevice(self.root, "1.1")
        self.assertEqual(["shell settings put system font_scale 2.0"], device.run("set_font_scale 2.0"))

    def test_it_puts_back_the_scale_the_device_had_before_the_first_set(self) -> None:
        device = FakeAdbDevice(self.root, "1.1")
        calls = device.run("set_font_scale 2.0; set_font_scale 1.0; restore_font_scale")
        self.assertEqual("shell settings put system font_scale 1.1", calls[-1])

    def test_a_device_with_no_scale_of_its_own_has_the_setting_deleted(self) -> None:
        device = FakeAdbDevice(self.root, "null")
        calls = device.run("set_font_scale 2.0; restore_font_scale")
        self.assertEqual("shell settings delete system font_scale", calls[-1])

    def test_restoring_without_a_set_touches_nothing(self) -> None:
        device = FakeAdbDevice(self.root, "1.1")
        self.assertEqual([], device.run("restore_font_scale"))


if __name__ == "__main__":
    unittest.main()
