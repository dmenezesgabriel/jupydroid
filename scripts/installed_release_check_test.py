#!/usr/bin/env python3
"""Tests for installed_release_check.py."""

from __future__ import annotations

import io
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

from installed_release_check import main, require_release_install

# The lines of `adb shell dumpsys package com.jupydroid.app` the check reads, as a real device prints them.
RELEASE_DUMPSYS = """\
Packages:
  Package [com.jupydroid.app] (a1b2c3):
    userId=10321
    versionCode=1001 minSdk=32 targetSdk=36
    versionName=0.1.1
    flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP KILL_AFTER_RESTORE ]
"""

DEBUG_DUMPSYS = RELEASE_DUMPSYS.replace("flags=[ HAS_CODE", "flags=[ DEBUGGABLE HAS_CODE").replace(
    "versionCode=1001", "versionCode=1"
)


class RequireReleaseInstallTest(unittest.TestCase):
    def refusal(self, dumpsys: str, tag: str) -> str:
        with self.assertRaises(ValueError) as caught:
            require_release_install(dumpsys, tag)
        return str(caught.exception)

    def test_a_release_install_of_the_tags_version_passes(self) -> None:
        require_release_install(RELEASE_DUMPSYS, "v0.1.1")

    def test_a_different_version_name_is_refused_naming_both_values(self) -> None:
        message = self.refusal(RELEASE_DUMPSYS, "v0.1.2")
        self.assertIn("0.1.1", message)
        self.assertIn("0.1.2", message)

    def test_a_different_version_code_is_refused_naming_both_values(self) -> None:
        message = self.refusal(RELEASE_DUMPSYS.replace("versionCode=1001", "versionCode=1002"), "v0.1.1")
        self.assertIn("1002", message)
        self.assertIn("1001", message)

    def test_a_debuggable_install_is_refused_even_at_the_tags_version(self) -> None:
        debuggable_at_tag = RELEASE_DUMPSYS.replace("flags=[ HAS_CODE", "flags=[ DEBUGGABLE HAS_CODE")
        self.assertIn("DEBUGGABLE", self.refusal(debuggable_at_tag, "v0.1.1"))

    def test_the_debug_build_the_tablet_ran_before_is_refused(self) -> None:
        self.refusal(DEBUG_DUMPSYS, "v0.1.1")

    def test_a_dump_with_no_version_is_refused_naming_what_was_expected(self) -> None:
        message = self.refusal("Unable to find package: com.jupydroid.app\n", "v0.1.1")
        self.assertIn("versionName=", message)

    def test_a_malformed_tag_is_refused_before_the_dump_is_read(self) -> None:
        self.assertIn("vMAJOR.MINOR.PATCH", self.refusal(RELEASE_DUMPSYS, "0.1.1"))


class MainTest(unittest.TestCase):
    def run_main(self, argv: list[str], stdin: str) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            status = main(argv, io.StringIO(stdin))
        return status, out.getvalue(), err.getvalue()

    def test_a_matching_dump_exits_zero_and_says_what_it_matched(self) -> None:
        status, out, _ = self.run_main(["v0.1.1"], RELEASE_DUMPSYS)
        self.assertEqual(status, 0)
        self.assertIn("0.1.1", out)

    def test_a_refused_dump_exits_one_with_the_refusal_on_stderr(self) -> None:
        debuggable_at_tag = RELEASE_DUMPSYS.replace("flags=[ HAS_CODE", "flags=[ DEBUGGABLE HAS_CODE")
        status, _, err = self.run_main(["v0.1.1"], debuggable_at_tag)
        self.assertEqual(status, 1)
        self.assertIn("DEBUGGABLE", err)

    def test_a_wrong_argument_count_exits_one(self) -> None:
        status, _, err = self.run_main([], RELEASE_DUMPSYS)
        self.assertEqual(status, 1)
        self.assertIn("exactly one release tag", err)


class RunAsAScriptTest(unittest.TestCase):
    def test_it_runs_by_path_from_another_directory_as_the_demo_driver_runs_it(self) -> None:
        # The driver once ran it with `python3 -I`, which drops the script's own directory from sys.path,
        # so `from release_version import ...` failed on the tablet while every import-based test passed.
        script = Path(__file__).resolve().with_name("installed_release_check.py")
        with tempfile.TemporaryDirectory() as elsewhere:
            result = subprocess.run(
                [sys.executable, str(script), "v0.1.1"],
                input=RELEASE_DUMPSYS,
                capture_output=True,
                text=True,
                cwd=elsewhere,
                check=False,
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("installed 0.1.1", result.stdout)


if __name__ == "__main__":
    unittest.main()
