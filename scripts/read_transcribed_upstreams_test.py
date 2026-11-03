#!/usr/bin/env python3
"""Tests for read_transcribed_upstreams.py."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from kotlin_source import comments_only
from read_transcribed_upstreams import (
    CLEAN_ROOM,
    Finding,
    clean_room_violations,
    comment_blocks,
    file_findings,
    pinned_upstreams,
    survey,
)

REPO = Path(__file__).resolve().parent.parent

TERMUX = "3df69d1da197dd9bd71a3bafd902dffd720576b4"
WINLATOR = "fb66541b93a4eb3ee585a433b4c7b20544d58e40"
PINS = {"termux-app": TERMUX, "winlator": WINLATOR, "jupyterlab": "7f0e690680a763d3c3db5dc0231803b0ba0e1e12"}

README_TABLE = f"""
| Project | Pinned SHA | Clone |
|---|---|---|
| `termux-app` | `{TERMUX}` | `git clone --depth 1 https://github.com/termux/termux-app.git` |
| `winlator` | `{WINLATOR}` | `git clone --depth 1 https://github.com/brunodev85/winlator` |
| not a row | `abc` | prose |
"""


def kotlin_findings(source: str) -> list[Finding]:
    return file_findings("A.kt", comments_only(source), PINS)


def kinds(findings: list[Finding]) -> set[tuple[str, str]]:
    return {(f.project, f.kind) for f in findings}


class PinnedUpstreamsTest(unittest.TestCase):
    def test_reads_each_table_row_and_nothing_else(self) -> None:
        self.assertEqual({"termux-app": TERMUX, "winlator": WINLATOR}, pinned_upstreams(README_TABLE))

    def test_the_real_readme_pins_every_clean_room_project(self) -> None:
        pins = pinned_upstreams((REPO / "docs" / "README.md").read_text(encoding="utf-8"))
        self.assertTrue(CLEAN_ROOM <= pins.keys(), f"{CLEAN_ROOM - pins.keys()} not in the README table")


class CommentBlocksTest(unittest.TestCase):
    def test_a_blank_line_ends_a_block(self) -> None:
        self.assertEqual(["// a\n// b", "// c"], comment_blocks("// a\n// b\n   \n// c\n"))

    def test_a_kdoc_with_empty_star_lines_is_one_block(self) -> None:
        kdoc = "/**\n * one\n *\n * two\n */\n"
        self.assertEqual(1, len(comment_blocks(kdoc)))


class FileFindingsTest(unittest.TestCase):
    def test_a_transcribed_word_and_a_cite_in_one_kdoc_is_transcribed(self) -> None:
        source = "/**\n * Transcribed from `TerminalRow` (termux-app 3df69d1d, Apache 2.0).\n */\nclass A\n"
        self.assertEqual({("termux-app", "transcribed")}, kinds(kotlin_findings(source)))

    def test_the_at_and_backtick_forms_are_cites(self) -> None:
        at = "// transcribed: termux-app@3df69d1\n"
        tick = "// transcribed from termux-app `3df69d1d`\n"
        self.assertEqual({("termux-app", "transcribed")}, kinds(kotlin_findings(at)))
        self.assertEqual({("termux-app", "transcribed")}, kinds(kotlin_findings(tick)))

    def test_a_cite_wrapped_onto_the_next_comment_line_is_still_a_cite(self) -> None:
        source = "// transcribed from termux-app\n// 3df69d1d TerminalRow\n"
        self.assertEqual({("termux-app", "transcribed")}, kinds(kotlin_findings(source)))

    def test_the_word_without_a_cite_is_nothing(self) -> None:
        self.assertEqual([], kotlin_findings("// transcribed from our own rootfs\n"))

    def test_a_cite_without_the_word_is_cited_only(self) -> None:
        source = "// adapted from jupyterlab 7f0e690\nclass A\n"
        self.assertEqual({("jupyterlab", "cited-only")}, kinds(kotlin_findings(source)))

    def test_the_word_and_the_cite_in_different_blocks_is_review(self) -> None:
        source = "// Transcribed, byte for byte.\n\nclass A\n\n// see termux-app 3df69d1d\n"
        self.assertEqual({("termux-app", "review")}, kinds(kotlin_findings(source)))

    def test_a_string_literal_is_not_prose(self) -> None:
        source = 'val s = "transcribed from termux-app 3df69d1d"\n'
        self.assertEqual([], kotlin_findings(source))

    def test_a_sha_that_is_not_a_prefix_of_the_pin_is_stale(self) -> None:
        source = "// transcribed from termux-app deadbee1\n"
        self.assertEqual({("termux-app", "stale")}, kinds(kotlin_findings(source)))

    def test_a_word_that_merely_looks_like_hex_is_not_a_cite(self) -> None:
        self.assertEqual([], kotlin_findings("// transcribed, as termux-app defaced it\n"))

    def test_a_longer_project_name_is_not_matched_by_its_prefix(self) -> None:
        pins = {"vscode": "a" * 40, "vscode-jupyter": "b1" * 20}
        prose = comments_only("// transcribed from vscode-jupyter b1b1b1b\n")
        found = {(f.project, f.kind) for f in file_findings("A.kt", prose, pins)}
        self.assertEqual({("vscode-jupyter", "transcribed")}, found)

    def test_one_finding_per_project_per_file(self) -> None:
        source = "// transcribed termux-app 3df69d1d\n\n// termux-app 3df69d1d again\n"
        self.assertEqual(1, len(kotlin_findings(source)))


class CleanRoomViolationsTest(unittest.TestCase):
    def test_a_clean_room_project_marked_transcribed_is_a_violation(self) -> None:
        findings = kotlin_findings(f"// transcribed from winlator {WINLATOR[:7]}\n")
        self.assertEqual(findings, clean_room_violations(findings))

    def test_a_clean_room_project_only_cited_is_fine(self) -> None:
        findings = kotlin_findings(f"// studied winlator {WINLATOR[:7]}\n")
        self.assertEqual([], clean_room_violations(findings))


class SurveyTest(unittest.TestCase):
    def test_reads_each_file_by_its_own_scanner(self) -> None:
        root = Path(tempfile.mkdtemp())
        kotlin = root / "A.kt"
        kotlin.write_text("// transcribed from termux-app 3df69d1d\n", encoding="utf-8")
        shell = root / "b.sh"
        shell.write_text("# transcribed from termux-app 3df69d1d\n", encoding="utf-8")
        found = survey([kotlin, shell], root, PINS)
        self.assertEqual({"A.kt", "b.sh"}, {f.path for f in found})

    def test_a_file_type_with_no_scanner_is_an_error_naming_it(self) -> None:
        root = Path(tempfile.mkdtemp())
        odd = root / "x.rb"
        odd.write_text("# transcribed\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, r"x\.rb"):
            survey([odd], root, PINS)


class RealTreeTest(unittest.TestCase):
    def test_no_clean_room_project_is_transcribed_in_this_repository(self) -> None:
        from check_doc_citations import scanned_files

        pins = pinned_upstreams((REPO / "docs" / "README.md").read_text(encoding="utf-8"))
        found = survey(scanned_files(None), REPO, pins)
        self.assertEqual([], clean_room_violations(found))


if __name__ == "__main__":
    unittest.main()
