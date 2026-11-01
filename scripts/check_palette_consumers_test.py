#!/usr/bin/env python3
"""Tests for the palette-consumer gate, including one run over this repository."""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from check_palette_consumers import EDITOR, MAIN, PALETTES, declared_fields, unread_fields

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


CHROME_DECLARED = """package com.jupydroid.app.theme

@Immutable
data class ChromePalette(
  val bar: Color,
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
        (palette / "ChromePalette.kt").write_text(CHROME_DECLARED, encoding="utf-8")
        (palette / "ChromePalettes.kt").write_text("val Dark = ChromePalette(bar = Color(0))", encoding="utf-8")
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
        fields = declared_fields(self.tree.root / MAIN / EDITOR.declaration, EDITOR.name)
        self.assertEqual(["editorBackground", "cellBackground", "ghost"], fields)

    def test_stops_before_syntax_palette(self) -> None:
        """`SyntaxPalette` answers to `styles()`, not to this rule - see the module docstring."""
        fields = declared_fields(self.tree.root / MAIN / EDITOR.declaration, EDITOR.name)
        self.assertNotIn("keyword", fields)
        self.assertNotIn("string", fields)

    def test_a_file_without_the_class_is_an_error_rather_than_an_empty_pass(self) -> None:
        """A gate that has stopped finding the declaration must fail, not report nothing wrong."""
        empty = self.tree.root / MAIN / EDITOR.instances
        with self.assertRaises(ValueError):
            declared_fields(empty, EDITOR.name)


class UnreadFieldsTest(unittest.TestCase):
    def test_a_field_nothing_reads_is_reported(self) -> None:
        tree = FakeTree("val c = palette.cellBackground")
        self.addCleanup(tree.close)
        self.assertEqual(
            ["EditorPalette.editorBackground", "EditorPalette.ghost", "ChromePalette.bar"],
            unread_fields(tree.root),
        )

    def test_a_field_a_screen_reads_is_not_reported(self) -> None:
        tree = FakeTree("val c = palette.cellBackground\nval g = palette.ghost")
        self.addCleanup(tree.close)
        self.assertEqual(["EditorPalette.editorBackground", "ChromePalette.bar"], unread_fields(tree.root))

    def test_no_field_is_exempt(self) -> None:
        """`editorBackground` was, until P7a-2 gave the document panes their own statement of it.

        The window still takes the same colour from `res/values{,-night}/colors.xml`, which is
        `AppWindowTest`'s to keep honest - but the field is no longer read only from XML, so the
        exemption became a line that could hide the next field that is.
        """
        tree = FakeTree("val c = palette.cellBackground")
        self.addCleanup(tree.close)
        self.assertIn("EditorPalette.editorBackground", unread_fields(tree.root))

    def test_the_instances_file_does_not_count_as_a_reader(self) -> None:
        """`EditorPalettes.kt` assigns `ghost`; assigning a value is not drawing with it."""
        tree = FakeTree("")
        self.addCleanup(tree.close)
        self.assertIn("EditorPalette.ghost", unread_fields(tree.root))

    def test_a_field_only_a_line_comment_names_is_still_unread(self) -> None:
        """The case this gate exists for, one step further out.

        A field read only by a *test* is not drawn by anything - and neither is one read only by
        a KDoc cross-reference, which is what `terminal/TerminalPalette.kt` does for
        `editorBackground`. The gate matched that text and reported the field as consumed.
        """
        tree = FakeTree("// see palette.ghost\nval c = palette.cellBackground")
        self.addCleanup(tree.close)
        self.assertIn("EditorPalette.ghost", unread_fields(tree.root))

    def test_a_field_only_a_block_comment_names_is_still_unread(self) -> None:
        tree = FakeTree("/** [palette.ghost] */\nval c = palette.cellBackground")
        self.addCleanup(tree.close)
        self.assertIn("EditorPalette.ghost", unread_fields(tree.root))

    def test_a_nested_block_comment_closes_where_kotlin_closes_it(self) -> None:
        """Kotlin nests block comments; stopping at the first `*/` would resume mid-comment."""
        tree = FakeTree("/* /* palette.ghost */ */\nval c = palette.cellBackground")
        self.addCleanup(tree.close)
        self.assertIn("EditorPalette.ghost", unread_fields(tree.root))

    def test_a_field_named_inside_a_string_is_a_reader(self) -> None:
        """A `//` inside a string must not eat the rest of the line - a URL is the common case."""
        tree = FakeTree('val u = "https://x/y"\nval g = palette.ghost')
        self.addCleanup(tree.close)
        self.assertNotIn("EditorPalette.ghost", unread_fields(tree.root))

    def test_a_comment_marker_inside_a_raw_string_is_not_a_comment(self) -> None:
        tree = FakeTree('val u = """a /* b"""\nval g = palette.ghost')
        self.addCleanup(tree.close)
        self.assertNotIn("EditorPalette.ghost", unread_fields(tree.root))


class ThisRepositoryTest(unittest.TestCase):
    def test_no_palette_field_is_unread(self) -> None:
        """Run over the real tree, so a gate that has stopped matching fails rather than passes."""
        root = pathlib.Path(__file__).resolve().parent.parent
        self.assertEqual([], unread_fields(root))

    def test_the_real_palette_still_declares_fields(self) -> None:
        """If the shape of the declaration changes, the gate must not quietly see nothing."""
        root = pathlib.Path(__file__).resolve().parent.parent
        self.assertIn("cellBackground", declared_fields(root / MAIN / EDITOR.declaration, EDITOR.name))

    def test_every_palette_this_app_has_is_held(self) -> None:
        """A second palette that nobody added here would be a palette with no gate at all.

        `ChromePalette` (P7a-1) is exactly that case: it states the rail's and the side panels'
        surfaces, and without this the gate would keep passing while reporting on one palette.
        """
        self.assertEqual(["EditorPalette", "ChromePalette"], [palette.name for palette in PALETTES])

    def test_the_chrome_palette_still_declares_fields(self) -> None:
        root = pathlib.Path(__file__).resolve().parent.parent
        chrome = next(palette for palette in PALETTES if palette.name == "ChromePalette")
        self.assertIn("activityBarBackground", declared_fields(root / MAIN / chrome.declaration, chrome.name))


if __name__ == "__main__":
    unittest.main()
