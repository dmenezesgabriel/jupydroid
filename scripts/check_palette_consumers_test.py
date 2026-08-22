#!/usr/bin/env python3
"""Tests for the palette-consumer gate, including one run over this repository."""
from __future__ import annotations

import pathlib
import tempfile
import unittest

from check_palette_consumers import DECLARATION, INSTANCES, MAIN, declared_fields, unread_fields

DECLARED = """package com.jupydroid.app.theme

@Immutable
data class EditorPalette(
  /** The page. */
  val editorBackground: Color,
  val cellBackground: Color,
  val ghost: Color,
  val syntax: SyntaxPalette,
)

@Immutable
data class SyntaxPalette(
  val keyword: Color,
  val string: Color,
)

fun SyntaxPalette.styles(): Map<SyntaxKind, SpanStyle> = mapOf(
  SyntaxKind.KEYWORD to SpanStyle(color = keyword),
  SyntaxKind.STRING to SpanStyle(color = string),
)
"""


class FakeTree:
    """A throwaway repository with one palette and one screen, so the gate can be run end to end."""

    def __init__(self, screen: str) -> None:
        self._dir = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self._dir.name)
        palette = self.root / MAIN / "theme"
        palette.mkdir(parents=True)
        (palette / "EditorPalette.kt").write_text(DECLARED, encoding="utf-8")
        (palette / "EditorPalettes.kt").write_text("val Dark = EditorPalette(ghost = Color(0))", encoding="utf-8")
        screens = self.root / MAIN / "ui"
        screens.mkdir(parents=True)
        (screens / "Screen.kt").write_text(screen, encoding="utf-8")

    def close(self) -> None:
        self._dir.cleanup()


class DeclaredFieldsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tree = FakeTree("")
        self.addCleanup(self.tree.close)

    def test_reads_the_editor_palettes_own_fields(self) -> None:
        fields = declared_fields(self.tree.root / MAIN / DECLARATION)
        self.assertEqual(["editorBackground", "cellBackground", "ghost"], fields)

    def test_stops_before_syntax_palette(self) -> None:
        """`SyntaxPalette` answers to `styles()`, not to this rule - see the module docstring."""
        fields = declared_fields(self.tree.root / MAIN / DECLARATION)
        self.assertNotIn("keyword", fields)
        self.assertNotIn("string", fields)

    def test_a_file_without_the_class_is_an_error_rather_than_an_empty_pass(self) -> None:
        """A gate that has stopped finding the declaration must fail, not report nothing wrong."""
        empty = self.tree.root / MAIN / INSTANCES
        with self.assertRaises(ValueError):
            declared_fields(empty)


class UnreadFieldsTest(unittest.TestCase):
    def test_a_field_nothing_reads_is_reported(self) -> None:
        tree = FakeTree("val c = palette.cellBackground")
        self.addCleanup(tree.close)
        self.assertEqual(["ghost"], unread_fields(tree.root))

    def test_a_field_a_screen_reads_is_not_reported(self) -> None:
        tree = FakeTree("val c = palette.cellBackground\nval g = palette.ghost")
        self.addCleanup(tree.close)
        self.assertEqual([], unread_fields(tree.root))

    def test_the_instances_file_does_not_count_as_a_reader(self) -> None:
        """`EditorPalettes.kt` assigns `ghost`; assigning a value is not drawing with it."""
        tree = FakeTree("")
        self.addCleanup(tree.close)
        self.assertIn("ghost", unread_fields(tree.root))

    def test_the_window_colour_is_exempt(self) -> None:
        """It is drawn from `res/values{,-night}/colors.xml`, before there is a composition."""
        tree = FakeTree("")
        self.addCleanup(tree.close)
        self.assertNotIn("editorBackground", unread_fields(tree.root))


class ThisRepositoryTest(unittest.TestCase):
    def test_no_palette_field_is_unread(self) -> None:
        """Run over the real tree, so a gate that has stopped matching fails rather than passes."""
        root = pathlib.Path(__file__).resolve().parent.parent
        self.assertEqual([], unread_fields(root))

    def test_the_real_palette_still_declares_fields(self) -> None:
        """If the shape of the declaration changes, the gate must not quietly see nothing."""
        root = pathlib.Path(__file__).resolve().parent.parent
        self.assertIn("cellBackground", declared_fields(root / MAIN / DECLARATION))


if __name__ == "__main__":
    unittest.main()
