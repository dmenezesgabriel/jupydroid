#!/usr/bin/env python3
"""Tests for scripts/release_notes.py, which cuts one tag's section out of CHANGELOG.md."""

from __future__ import annotations

import io
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

from release_notes import extract_release_notes, main

# The shape python-semantic-release writes: `## vX.Y.Z (date)` per release, newest first, under a header.
CHANGELOG = """# CHANGELOG

<!-- version list -->

## v1.2.30 (2026-12-01)

### Features

- thirty

## v1.2.3 (2026-11-20)

### Bug Fixes

- three

## v0.1.0 (2026-11-05)

- Initial Release
"""


class ExtractReleaseNotesTest(unittest.TestCase):
    def test_it_returns_the_body_of_the_named_section(self) -> None:
        self.assertEqual(extract_release_notes(CHANGELOG, "v1.2.3"), "### Bug Fixes\n\n- three\n")

    def test_it_stops_at_the_next_release_header(self) -> None:
        self.assertNotIn("v0.1.0", extract_release_notes(CHANGELOG, "v1.2.3"))

    def test_a_tag_that_is_a_prefix_of_another_does_not_match_it(self) -> None:
        self.assertEqual(extract_release_notes(CHANGELOG, "v1.2.30"), "### Features\n\n- thirty\n")

    def test_the_last_section_runs_to_the_end_of_the_file(self) -> None:
        self.assertEqual(extract_release_notes(CHANGELOG, "v0.1.0"), "- Initial Release\n")

    def test_a_missing_section_names_the_tag_and_the_header_it_looked_for(self) -> None:
        with self.assertRaises(ValueError) as refusal:
            extract_release_notes(CHANGELOG, "v9.9.9")
        self.assertIn("'v9.9.9'", str(refusal.exception))
        self.assertIn("## v9.9.9 (YYYY-MM-DD)", str(refusal.exception))

    def test_an_empty_section_is_refused_rather_than_published_blank(self) -> None:
        empty = "## v1.0.0 (2026-11-05)\n\n## v0.1.0 (2026-11-01)\n\n- Initial Release\n"
        with self.assertRaises(ValueError) as refusal:
            extract_release_notes(empty, "v1.0.0")
        self.assertIn("'v1.0.0'", str(refusal.exception))

    def test_a_tag_with_regex_characters_is_matched_literally(self) -> None:
        with self.assertRaises(ValueError):
            extract_release_notes(CHANGELOG, "v1.2x3")


class MainTest(unittest.TestCase):
    def run_main(self, arguments: list[str]) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            status = main(arguments)
        return status, out.getvalue(), err.getvalue()

    def test_it_prints_the_notes_of_the_tag_in_the_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            changelog = Path(directory) / "CHANGELOG.md"
            changelog.write_text(CHANGELOG, encoding="utf-8")
            status, out, _ = self.run_main([str(changelog), "v1.2.3"])
        self.assertEqual((status, out), (0, "### Bug Fixes\n\n- three\n"))

    def test_a_missing_section_exits_1_with_the_reason_on_stderr(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            changelog = Path(directory) / "CHANGELOG.md"
            changelog.write_text(CHANGELOG, encoding="utf-8")
            status, out, err = self.run_main([str(changelog), "v9.9.9"])
        self.assertEqual((status, out), (1, ""))
        self.assertIn("'v9.9.9'", err)

    def test_a_missing_file_exits_1_naming_the_path(self) -> None:
        status, _, err = self.run_main(["/nonexistent/CHANGELOG.md", "v1.0.0"])
        self.assertEqual(status, 1)
        self.assertIn("/nonexistent/CHANGELOG.md", err)

    def test_a_wrong_argument_count_exits_1(self) -> None:
        status, _, err = self.run_main(["only-one"])
        self.assertEqual(status, 1)
        self.assertIn("['only-one']", err)


if __name__ == "__main__":
    unittest.main()
