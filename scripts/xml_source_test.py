#!/usr/bin/env python3
"""Tests for xml_source."""
import unittest

from xml_source import xml_comments


class XmlCommentsTest(unittest.TestCase):
    def test_a_comment_survives_and_markup_is_blanked(self):
        source = '<manifest>\n  <!-- why -->\n</manifest>\n'
        self.assertEqual(["          ", "  <!-- why -->", "           "], xml_comments(source).splitlines())

    def test_a_comment_spanning_lines_keeps_its_newlines(self):
        source = '<a>\n<!-- one\ntwo -->\n<b/>\n'
        self.assertEqual(["   ", "<!-- one", "two -->", "    "], xml_comments(source).splitlines())

    def test_a_comment_does_not_nest(self):
        # XML forbids `--` inside a comment, so the first `-->` closes it and a second opener is
        # markup again. This is what makes an XML scanner shorter than kotlin_source's.
        source = '<!-- a <!-- b --> <c/>\n'
        self.assertEqual("<!-- a <!-- b -->" + " " * len(" <c/>"), xml_comments(source).rstrip("\n"))

    def test_the_result_is_the_same_length_as_the_source(self):
        source = '<a>\n  <!-- p -->\n  <b attr="1"/>\n</a>\n'
        self.assertEqual(len(source), len(xml_comments(source)))

    def test_an_unterminated_comment_runs_to_the_end(self):
        source = '<a>\n<!-- forever\n'
        self.assertEqual(["   ", "<!-- forever"], xml_comments(source).splitlines())


if __name__ == "__main__":
    unittest.main()
