#!/usr/bin/env python3
"""Tests for check_monospace_home.py."""
from __future__ import annotations

import pathlib
import tempfile
import unittest

from check_monospace_home import UI_SOURCE, offences, offences_in

OFFENDING = """package com.jupydroid.app.ui.editor

@Composable
private fun OutputText(text: String) {
  Text(text = text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}
"""
COMMENT_ONLY = """package com.jupydroid.app.ui.editor

/**
 * Takes its font from [codeTextStyle] rather than naming `FontFamily.Monospace` itself.
 */
@Composable
private fun OutputText(text: String) {
  // FontFamily.Monospace lives in EditorTheme.kt - see check_monospace_home.py.
  Text(text = text, style = codeTextStyle())
}
"""


class OffencesInTest(unittest.TestCase):
    def setUp(self):
        self.root = pathlib.Path(tempfile.mkdtemp())
        self.tree = self.root / UI_SOURCE / "ui" / "editor"
        self.tree.mkdir(parents=True)

    def _write(self, name: str, body: str) -> pathlib.Path:
        path = self.tree / name
        path.write_text(body)
        return path

    def test_naming_the_font_outside_its_home_is_an_offence(self):
        path = self._write("CellOutputsView.kt", OFFENDING)

        found = offences_in(path, self.root)

        self.assertEqual(1, len(found), found)
        self.assertIn("CellOutputsView.kt:5", found[0])

    def test_the_home_may_name_it(self):
        path = self._write("EditorTheme.kt", OFFENDING)

        self.assertEqual([], offences_in(path, self.root))

    def test_a_comment_about_the_rule_is_not_a_use(self):
        """Prose explaining where the font lives is the opposite of reaching for it."""
        path = self._write("CellOutputsView.kt", COMMENT_ONLY)

        self.assertEqual([], offences_in(path, self.root))

    def test_a_longer_name_ending_in_it_is_not_a_match(self):
        path = self._write("SymbolRow.kt", "val x = MyFontFamily.Monospace\n")

        self.assertEqual([], offences_in(path, self.root))


class ScopeTest(unittest.TestCase):
    """The gate has to be pointed at something, or it reports clean by matching nothing."""

    def test_it_raises_rather_than_passing_when_run_from_the_wrong_place(self):
        with tempfile.TemporaryDirectory() as empty:
            with self.assertRaises(FileNotFoundError) as raised:
                offences(pathlib.Path(empty))
            self.assertIn(str(UI_SOURCE), str(raised.exception))

    def test_the_gate_reads_the_real_tree(self):
        """The repository it is shipped in has to pass it, or the gate is theatre."""
        self.assertEqual([], offences(pathlib.Path(__file__).resolve().parent.parent))


if __name__ == "__main__":
    unittest.main()
