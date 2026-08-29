#!/usr/bin/env python3
"""Tests for check_busybox_seccomp.py."""
from __future__ import annotations

import pathlib
import unittest

from check_busybox_seccomp import BUSYBOX_BANNER, dispatches_through_check_suid, main

REPO = pathlib.Path(__file__).resolve().parent.parent
STAGED_BUSYBOX = REPO / "app/src/main/jniLibs/arm64-v8a/libbusybox.so"


class DispatchesThroughCheckSuidTest(unittest.TestCase):
    def test_a_binary_carrying_the_fingerprint_is_reported(self) -> None:
        """Alpine's stock build, which is what P4b shipped and what killed every applet."""
        self.assertTrue(
            dispatches_through_check_suid(BUSYBOX_BANNER + b"1.37.0\x00must be suid to work properly\x00")
        )

    def test_a_binary_without_it_is_clean(self) -> None:
        self.assertFalse(dispatches_through_check_suid(BUSYBOX_BANNER + b"1.37.0\x00ash\x00ls\x00"))

    def test_it_refuses_to_answer_about_a_file_that_is_not_busybox(self) -> None:
        """A gate that silently passes on the wrong file is how P4b's gate went green."""
        with self.assertRaises(ValueError) as raised:
            dispatches_through_check_suid(b"\x7fELF" + b"\x00" * 64)
        self.assertIn("expected a busybox binary", str(raised.exception))

    @unittest.skipUnless(STAGED_BUSYBOX.is_file(), "jniLibs is a build output; nothing staged here")
    def test_the_staged_arm64_busybox_is_the_one_without_the_flag(self) -> None:
        self.assertFalse(dispatches_through_check_suid(STAGED_BUSYBOX.read_bytes()))


class ThisRepositoryTest(unittest.TestCase):
    """Runs the gate over this repository, so a gate matching nothing fails rather than passes."""

    def test_the_shipped_busybox_and_its_config_are_clean(self) -> None:
        self.assertEqual(0, main(REPO))


if __name__ == "__main__":
    unittest.main()
