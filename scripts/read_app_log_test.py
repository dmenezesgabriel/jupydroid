"""Tests for read_app_log.py - the error log's day files read, grouped by exception class and top frame.

The fixture in testdata/app-log is invented: two day files, a stack whose first line is the exception header, a
blank line before a frame, a key this reader has never seen, an entry with no stack, and a last line torn
mid-write, which is the one way `DiskErrorLog` can leave a file.

    python3 -m unittest discover -s scripts -p '*_test.py'
"""

from __future__ import annotations

import contextlib
import io
import pathlib
import tempfile
import unittest

import read_app_log

FIXTURE = pathlib.Path(__file__).parent / "testdata" / "app-log"


class ReadEntriesTest(unittest.TestCase):
    def test_it_reads_every_day_file_oldest_first(self) -> None:
        read = read_app_log.read_entries(FIXTURE)
        stamps = [entry.timestamp_millis for entry in read.entries]
        self.assertEqual(sorted(stamps), stamps)
        self.assertEqual(4, len(read.entries))

    def test_it_skips_a_torn_line_and_says_how_many(self) -> None:
        self.assertEqual(1, read_app_log.read_entries(FIXTURE).skipped_lines)

    def test_it_ignores_a_key_it_does_not_know(self) -> None:
        classes = [entry.exception_class for entry in read_app_log.read_entries(FIXTURE).entries]
        self.assertIn("java.lang.NullPointerException", classes)

    def test_it_ignores_a_file_that_is_not_a_day_file(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            stray = pathlib.Path(tmp) / "notes.jsonl"
            stray.write_text('{"timestampMillis":1,"kind":"ERROR"}\n')
            self.assertEqual(0, len(read_app_log.read_entries(pathlib.Path(tmp)).entries))

    def test_a_missing_directory_names_the_path_and_the_expected_shape(self) -> None:
        with self.assertRaises(FileNotFoundError) as raised:
            read_app_log.read_entries(FIXTURE / "absent")
        self.assertIn("absent", str(raised.exception))
        self.assertIn("errors-YYYY-MM-DD.jsonl", str(raised.exception))


class GroupEntriesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.groups = read_app_log.group_entries(read_app_log.read_entries(FIXTURE).entries)

    def test_it_groups_by_exception_class_and_top_frame_most_frequent_first(self) -> None:
        first = self.groups[0]
        self.assertEqual("java.lang.IllegalStateException", first.exception_class)
        self.assertEqual("com.example.Editor.save(Editor.kt:42)", first.top_frame)
        self.assertEqual(2, first.count)

    def test_it_counts_a_group_per_app_version(self) -> None:
        self.assertEqual({"0.9.0": 1, "0.9.1": 1}, self.groups[0].versions)

    def test_a_frame_after_a_blank_line_is_still_the_top_frame(self) -> None:
        frames = {group.exception_class: group.top_frame for group in self.groups}
        self.assertEqual("com.example.Cells.render(Cells.kt:9)", frames["java.lang.NullPointerException"])

    def test_an_entry_without_a_stack_groups_by_class_alone(self) -> None:
        anr = next(group for group in self.groups if group.exception_class == "ANR")
        self.assertEqual("", anr.top_frame)

    def test_equal_counts_are_ordered_by_class_so_a_report_is_repeatable(self) -> None:
        tied = [group.exception_class for group in self.groups[1:]]
        self.assertEqual(["ANR", "java.lang.NullPointerException"], tied)

    def test_a_group_says_when_it_was_first_and_last_seen(self) -> None:
        self.assertEqual((1794909600000, 1794913200000), (self.groups[0].first_millis, self.groups[0].last_millis))

    def test_an_entry_with_no_app_version_counts_as_unknown(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            day = pathlib.Path(tmp) / "errors-2026-11-17.jsonl"
            day.write_text('{"timestampMillis":1,"kind":"ERROR","exceptionClass":"E"}\n')
            groups = read_app_log.group_entries(read_app_log.read_entries(pathlib.Path(tmp)).entries)
        self.assertEqual({"unknown": 1}, groups[0].versions)


class MainTest(unittest.TestCase):
    def report(self) -> str:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            read_app_log.main([str(FIXTURE)])
        return out.getvalue()

    def test_the_report_names_each_group_with_its_count_and_versions(self) -> None:
        lines = self.report().splitlines()
        self.assertIn("2  java.lang.IllegalStateException  com.example.Editor.save(Editor.kt:42)", lines)
        self.assertIn("   0.9.0: 1, 0.9.1: 1", lines)

    def test_the_report_says_how_many_lines_it_skipped(self) -> None:
        self.assertIn("4 entries in 3 groups, 1 unreadable line skipped", self.report())


if __name__ == "__main__":
    unittest.main()
