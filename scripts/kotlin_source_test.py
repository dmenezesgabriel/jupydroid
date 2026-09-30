"""Tests for kotlin_source.py."""
from __future__ import annotations

import unittest

from kotlin_source import comments_only, without_comments


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

    def test_a_raw_string_ending_in_a_quote_closes_at_the_last_triple(self):
        # Kotlin closes a raw string at the LAST triple quote of a run of quotes, so a literal
        # spelled with four quotes each side holds one quote of content. Reading the *first*
        # triple as the closer left a quote over to open a fresh literal, and every comment
        # after it was swallowed as string content - which is how DiskWorkspaceSearcherTest
        # kept a citation of a deleted document past check_doc_citations.py.
        source = 'val j = """"cat""""\n// why\n'
        self.assertEqual(['val j = """"cat""""', " " * len("// why")], without_comments(source).splitlines())

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


class CommentsOnlyTest(unittest.TestCase):
    """The inverse of [without_comments], for a gate that checks what the prose says.

    `check_doc_citations.py` needs exactly the half this module already throws away, and it needs it
    at the same offsets so it can report a line number.
    """

    def test_code_is_blanked_and_the_comment_survives(self):
        self.assertEqual("// note\n         ", comments_only("// note\nval a = 1"))

    def test_a_block_comment_survives(self):
        self.assertEqual("/* n */         ", comments_only("/* n */val a = 1"))

    def test_a_citation_inside_a_string_is_not_a_comment(self):
        """The defect this exists to avoid: a URL or a path in a string literal is not prose."""
        source = 'val u = "docs/PLAN.md"'
        self.assertEqual(" " * len(source), comments_only(source))

    def test_a_comment_marker_inside_a_raw_string_stays_code(self):
        self.assertEqual(" " * len('val u = """a // b"""'), comments_only('val u = """a // b"""'))

    def test_the_result_is_the_same_length_as_the_source(self):
        source = 'val u = "//x" /* a\nb */ // tail\nval a = 1\n'
        self.assertEqual(len(source), len(comments_only(source)))

    def test_newlines_survive_so_line_numbers_hold(self):
        only = comments_only("val a = 1\n// why\nval b = 2")
        self.assertEqual(["         ", "// why", "         "], only.splitlines())

    def test_a_comment_after_a_quote_hugging_raw_string_is_still_prose(self):
        """The `comments_only` half of the same defect: the comment must survive, not be blanked."""
        only = comments_only('val j = """"cat""""\n// why\n')
        self.assertEqual(["                   ", "// why"], only.splitlines())

    def test_it_is_the_complement_of_without_comments(self):
        source = '/** k */\nval u = "a // b" // tail\n'
        for kept, dropped in zip(comments_only(source), without_comments(source)):
            if kept == "\n":
                continue  # a newline is kept by both halves on purpose, so line numbers hold
            self.assertTrue(kept == " " or dropped == " ", f"{kept!r} survived both")


if __name__ == "__main__":
    unittest.main()
