"""Tests for check_evidence_citations.py, including one over every tracked Markdown file."""

from __future__ import annotations

import pathlib
import subprocess
import unittest

from check_evidence_citations import cites_evidence_dir, main


class CitesEvidenceDirTest(unittest.TestCase):
    def test_a_backticked_directory_is_named(self):
        self.assertEqual(["evidence/step8.7"], cites_evidence_dir("exit 0 (`evidence/step8.7/x.txt`)"))

    def test_a_relative_link_into_evidence_is_named(self):
        text = "[the readings](../../evidence/call-stack/README.md)"
        self.assertEqual(["evidence/call-stack"], cites_evidence_dir(text))

    def test_a_shell_assignment_is_named(self):
        self.assertEqual(["evidence/code-scans"], cites_evidence_dir("E=evidence/code-scans  # out"))

    def test_a_placeholder_is_not_named(self):
        self.assertEqual([], cites_evidence_dir("drivers write to `evidence/<step>/`"))

    def test_the_bare_directory_is_not_named(self):
        self.assertEqual([], cites_evidence_dir("`evidence/` is local (ADR-0132)"))

    def test_a_glob_is_not_named(self):
        """`git filter-repo --path-glob 'evidence/*'` and `.gitignore` patterns name no directory."""
        for glob in ("evidence/*", "evidence/**/*logcat*.txt", "evidence/…"):
            self.assertEqual([], cites_evidence_dir(f"`{glob}`"), glob)


class MainTest(unittest.TestCase):
    def test_a_named_directory_fails(self):
        self.assertEqual(1, main(["a.md"], texts={"a.md": "see `evidence/p1g-x/`"}))

    def test_a_clean_file_passes(self):
        self.assertEqual(0, main(["a.md"], texts={"a.md": "`evidence/<step>/` is local"}))


class TheWholeTreeTest(unittest.TestCase):
    def test_no_tracked_markdown_names_an_evidence_directory(self):
        root = pathlib.Path(__file__).resolve().parent.parent
        tracked = subprocess.run(["git", "ls-files", "*.md"], cwd=root, capture_output=True, text=True, check=True)
        self.assertEqual(0, main(tracked.stdout.split(), root=root))


if __name__ == "__main__":
    unittest.main()
