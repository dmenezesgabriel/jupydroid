#!/usr/bin/env python3
"""Tests for release_version.py."""

from __future__ import annotations

import io
import unittest
from contextlib import redirect_stderr, redirect_stdout

from release_version import ReleaseVersion, main, parse_release_tag


class ParseReleaseTagTest(unittest.TestCase):
    def test_a_tag_gives_its_name_without_the_v_and_a_code_that_orders_like_semver(self) -> None:
        self.assertEqual(parse_release_tag("v1.2.3"), ReleaseVersion(name="1.2.3", code=1_002_003))

    def test_the_first_minor_of_a_zero_major_is_above_zero(self) -> None:
        self.assertEqual(parse_release_tag("v0.1.0"), ReleaseVersion(name="0.1.0", code=1_000))

    def test_the_largest_tag_stays_under_androids_version_code_cap(self) -> None:
        largest = parse_release_tag("v2099.999.999")
        self.assertEqual(largest.code, 2_099_999_999)
        self.assertLessEqual(largest.code, 2_100_000_000)

    def test_a_later_tag_always_has_a_larger_code(self) -> None:
        tags = ["v0.9.9", "v0.10.0", "v1.0.0", "v1.0.1", "v1.1.0", "v2.0.0"]
        codes = [parse_release_tag(tag).code for tag in tags]
        self.assertEqual(codes, sorted(codes))
        self.assertEqual(len(set(codes)), len(codes))


class RefusedTagTest(unittest.TestCase):
    def refusal(self, tag: str) -> str:
        with self.assertRaises(ValueError) as caught:
            parse_release_tag(tag)
        return str(caught.exception)

    def test_each_malformed_tag_is_refused_naming_it_and_the_expected_shape(self) -> None:
        for tag in ["1.2.3", "v1.2", "v1.2.3-rc.1", "v1.2.3+7", "v01.2.3", "v1.2.03", "V1.2.3", "", " v1.2.3"]:
            with self.subTest(tag=tag):
                message = self.refusal(tag)
                self.assertIn(repr(tag), message)
                self.assertIn("vMAJOR.MINOR.PATCH", message)

    def test_a_field_over_its_range_is_refused_naming_the_tag_and_the_limit(self) -> None:
        over = [("v2100.0.0", "MAJOR <= 2099"), ("v1.1000.0", "MINOR <= 999"), ("v1.0.1000", "PATCH <= 999")]
        for tag, limit in over:
            with self.subTest(tag=tag):
                message = self.refusal(tag)
                self.assertIn(repr(tag), message)
                self.assertIn(limit, message)


class MainTest(unittest.TestCase):
    def run_main(self, argv: list[str]) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            status = main(argv)
        return status, out.getvalue(), err.getvalue()

    def test_it_prints_the_two_lines_a_workflow_appends_to_its_output_file(self) -> None:
        status, printed, _ = self.run_main(["v1.2.3"])
        self.assertEqual(status, 0)
        self.assertEqual(printed, "versionName=1.2.3\nversionCode=1002003\n")

    def test_a_refused_tag_exits_nonzero_and_prints_nothing_to_stdout(self) -> None:
        status, printed, complaint = self.run_main(["1.2.3"])
        self.assertEqual(status, 1)
        self.assertEqual(printed, "")
        self.assertIn("'1.2.3'", complaint)

    def test_without_exactly_one_tag_it_exits_nonzero(self) -> None:
        self.assertEqual(self.run_main([])[0], 1)
        self.assertEqual(self.run_main(["v1.0.0", "v1.0.1"])[0], 1)


if __name__ == "__main__":
    unittest.main()
