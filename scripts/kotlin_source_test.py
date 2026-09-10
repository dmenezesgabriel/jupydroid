"""Tests for kotlin_source.py."""
from __future__ import annotations

import unittest

from kotlin_source import without_comments


class WithoutCommentsTest(unittest.TestCase):
    def test_a_line_comment_is_blanked(self):
        self.assertEqual("       \nval a = 1", without_comments("// note\nval a = 1"))

    def test_a_block_comment_is_blanked(self):
        self.assertEqual("       val a = 1", without_comments("/* n */val a = 1"))

    def test_a_nested_block_comment_closes_where_kotlin_closes_it(self):
        self.assertEqual("             x", without_comments("/* /* n */ */x"))

    def test_a_string_is_left_alone(self):
        self.assertEqual('val u = "https://x/y"', without_comments('val u = "https://x/y"'))

    def test_a_comment_marker_inside_a_raw_string_is_not_a_comment(self):
        self.assertEqual('val u = """a // b"""', without_comments('val u = """a // b"""'))

    def test_an_escaped_quote_does_not_end_the_literal(self):
        self.assertEqual('val q = "a\\"// b"', without_comments('val q = "a\\"// b"'))


class OffsetsAreKeptTest(unittest.TestCase):
    """The property both gates need, and the reason this is not a delete-the-characters scanner.

    `check_ui_dimensions.py` reports a line number and reads the line *above* a declaration to find
    its KDoc. A scanner that removed a block comment's newlines would renumber every line after it,
    so a comment is blanked to spaces and its newlines are kept.
    """

    def test_a_block_comment_keeps_its_newlines(self):
        blanked = without_comments("/**\n * why\n */\nval a = 1")
        self.assertEqual(4, len(blanked.splitlines()))
        self.assertEqual("val a = 1", blanked.splitlines()[3])

    def test_the_result_is_the_same_length_as_the_source(self):
        source = 'val u = "//x" /* a\nb */ // tail\nval a = 1\n'
        self.assertEqual(len(source), len(without_comments(source)))

    def test_a_kdoc_still_looks_like_a_kdoc_line_by_line(self):
        """Blanked, a KDoc's own lines are whitespace - so the KDoc check reads the source, not this."""
        self.assertEqual(["    ", "val a = 1"], without_comments("/**/\nval a = 1").splitlines())


if __name__ == "__main__":
    unittest.main()
