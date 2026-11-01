#!/usr/bin/env python3
"""Tests for read_personal_data.py."""

from __future__ import annotations

import unittest
from pathlib import Path

from read_personal_data import CLASSES, files_per_class, report, scrub_rules


class FilesPerClassTest(unittest.TestCase):
    def test_a_home_path_counts_the_file_once_however_often_it_appears(self) -> None:
        counts = files_per_class({"a.log": "/home/ana/x /home/ana/y", "b.log": "clean"})
        self.assertEqual(counts["home path"], 1)

    def test_an_email_is_found_and_a_kotlin_receiver_label_is_not(self) -> None:
        counts = files_per_class({"a.md": "mail bob@mail.org", "b.kt": "return@launch this@Foo.bar"})
        self.assertEqual(counts["email"], 1)

    def test_every_class_is_reported_even_at_zero(self) -> None:
        self.assertEqual(set(files_per_class({})), {name for name, _ in CLASSES})

    def test_report_prints_one_line_per_class(self) -> None:
        lines = report({"home path": 3, "email": 0}).splitlines()
        self.assertEqual(lines, ["home path: 3 files", "email: 0 files"])


class ScrubRulesTest(unittest.TestCase):
    def test_the_hosts_home_is_replaced_by_a_neutral_one_in_filter_repo_format(self) -> None:
        self.assertEqual(scrub_rules(Path("/home/ana")), ["/home/ana==>/home/user"])

    def test_a_guest_path_is_not_a_rule(self) -> None:
        rules = scrub_rules(Path("/home/ana"))
        self.assertFalse(any("jupydroid" in rule for rule in rules))


if __name__ == "__main__":
    unittest.main()
