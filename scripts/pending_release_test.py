#!/usr/bin/env python3
"""Tests for pending_release.py."""

from __future__ import annotations

import io
import unittest
from contextlib import redirect_stdout

from pending_release import main, pending_release

PSR_NOOP_BANNER = "🛡 You are running in no-operation mode, because the '--noop' flag was supplied\n"


class PendingReleaseTest(unittest.TestCase):
    def test_a_new_version_is_pending(self) -> None:
        self.assertEqual(pending_release("0.2.0\n", ["v0.1.0"]), "0.2.0")

    def test_the_last_version_line_wins_over_log_noise(self) -> None:
        printed = f"{PSR_NOOP_BANNER}[16:33:03] WARNING  Token value is missing!\n0.1.0\n"
        self.assertEqual(pending_release(printed, []), "0.1.0")

    def test_the_version_psr_echoes_when_nothing_bumps_is_not_pending_once_its_tag_exists(self) -> None:
        self.assertIsNone(pending_release("0.1.0\n", ["v0.1.0"]))

    def test_no_tag_and_nothing_bumpable_is_not_pending_although_psr_prints_0_0_0(self) -> None:
        # Regression: PSR prints 0.0.0 for a history with no tag and no feat/fix/perf, and v0.0.0 is no tag.
        self.assertIsNone(pending_release("0.0.0\n", []))

    def test_output_with_no_version_line_is_not_pending(self) -> None:
        self.assertIsNone(pending_release(PSR_NOOP_BANNER, []))


class MainTest(unittest.TestCase):
    def run_main(self, printed: str, tags: list[str]) -> tuple[int, str]:
        out = io.StringIO()
        with redirect_stdout(out):
            code = main(tags, io.StringIO(printed))
        return code, out.getvalue()

    def test_a_pending_version_is_printed_alone(self) -> None:
        self.assertEqual(self.run_main("0.2.0\n", ["v0.1.0"]), (0, "0.2.0\n"))

    def test_nothing_pending_prints_nothing_and_still_exits_zero(self) -> None:
        self.assertEqual(self.run_main("0.1.0\n", ["v0.1.0"]), (0, ""))


if __name__ == "__main__":
    unittest.main()
