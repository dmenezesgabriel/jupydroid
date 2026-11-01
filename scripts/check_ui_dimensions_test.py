"""Tests for check_ui_dimensions.py."""

from __future__ import annotations

import pathlib
import tempfile
import unittest

import check_ui_dimensions as gate


class OffencesTest(unittest.TestCase):
    def offences(self, source: str, name: str = "ui/editor/Thing.kt") -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            return gate.offences_in(path, pathlib.Path(tmp))

    def test_an_inline_literal_in_a_modifier_is_an_offence(self):
        found = self.offences("Modifier.padding(horizontal = 16.dp)\n")
        self.assertEqual(1, len(found), found)
        self.assertIn("16.dp", found[0])

    def test_every_inline_literal_on_one_line_is_reported(self):
        found = self.offences("Modifier.padding(horizontal = 12.dp, vertical = 14.dp)\n")
        self.assertEqual(2, len(found), found)

    def test_a_named_value_with_a_kdoc_above_it_is_allowed(self):
        self.assertEqual([], self.offences("/** `codeCellLeftLinePadding`. */\nprivate val GAP = 5.dp\n"))

    def test_a_named_value_without_a_kdoc_is_an_offence(self):
        found = self.offences("private val GAP = 5.dp\n")
        self.assertEqual(1, len(found), found)
        self.assertIn("GAP", found[0])

    def test_a_kdoc_may_be_several_lines(self):
        source = "/**\n * Why this number and not another.\n */\ninternal val PANE = 280.dp\n"
        self.assertEqual([], self.offences(source))

    def test_a_blank_line_between_the_kdoc_and_the_value_does_not_count(self):
        found = self.offences("/** Detached. */\n\nprivate val GAP = 5.dp\n")
        self.assertEqual(1, len(found), found)

    def test_zero_is_a_sentinel_rather_than_a_gap(self):
        self.assertEqual([], self.offences("if (width <= 0.dp) return this\n"))

    def test_the_scale_itself_may_hold_literals(self):
        source = "object Spacing {\n  val xs: Dp = 4.dp\n}\n"
        self.assertEqual([], self.offences(source, "theme/Spacing.kt"))

    def test_a_line_comment_naming_the_source_is_not_a_kdoc(self):
        found = self.offences("// from the reference\nprivate val GAP = 5.dp\n")
        self.assertEqual(1, len(found), found)

    def test_a_literal_in_a_line_comment_is_not_a_dimension(self):
        """Prose is not a point of use, which is the whole rule - see the module docstring."""
        self.assertEqual([], self.offences('// 8.dp used to live here\nval name = "x"\n'))

    def test_a_kdoc_may_name_the_number_it_is_explaining(self):
        """`theme/CornerRadius.kt` is the file that found this: its KDoc explains a retired literal."""
        source = "/** Was `TOGGLE_CORNER = 8.dp`, chosen against nothing. */\nval large: Dp = 8.dp\n"
        self.assertEqual([], self.offences(source))

    def test_a_real_literal_beside_a_comment_is_still_reported(self):
        """The blanking must not become an escape hatch: only the comment half goes."""
        found = self.offences("Modifier.padding(16.dp)  // and 24.dp was too much\n")
        self.assertEqual(1, len(found), found)
        self.assertIn("16.dp", found[0])

    def test_a_block_comment_does_not_renumber_what_follows_it(self):
        """Blanked rather than deleted, so a comment's newlines survive and line 4 is still line 4."""
        found = self.offences("/*\n * 5.dp\n */\nModifier.padding(16.dp)\n")
        self.assertEqual(1, len(found), found)
        self.assertIn(":4:", found[0])

    def test_a_file_with_no_dimensions_is_silent(self):
        self.assertEqual([], self.offences('val name = "x"\n'))


class ScopeTest(unittest.TestCase):
    def test_the_gate_reads_the_real_tree(self):
        """The repository it is shipped in has to pass it, or the gate is theatre."""
        self.assertEqual([], gate.offences())


if __name__ == "__main__":
    unittest.main()
