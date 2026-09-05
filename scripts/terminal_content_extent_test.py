#!/usr/bin/env python3
"""Tests for terminal_content_extent.py."""
from __future__ import annotations

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image

from terminal_content_extent import (
    terminal_content_bottom_y,
    terminal_row_spacing_px,
    terminal_top_strip_differs,
)

_LIGHT_BG = (255, 255, 255)
_LIGHT_INK = (0, 0, 0)
_DARK_BG = (25, 26, 27)  # measured off a real VS_CODE_DARK capture
_DARK_INK = (204, 204, 204)  # measured off the same capture's own text
_WIDTH = 600  # wide enough to clear the module's own background-sample column
_HEIGHT = 400
_CONTENT_TOP = 20


def _painted(rows: dict[int, tuple[int, int]], background: tuple[int, int, int], ink: tuple[int, int, int]) -> str:
    """A [_WIDTH]x[_HEIGHT] [background] PNG with an [ink] stroke at `x in [start, end)` per row.

    Returns the path of a file in a directory the caller is responsible for cleaning up -
    `Image.open` needs a real path, not a `BytesIO`, to match [terminal_content_bottom_y]'s own
    signature exactly.
    """
    image = Image.new("RGB", (_WIDTH, _HEIGHT), background)
    for y, (start, end) in rows.items():
        for x in range(start, end):
            image.putpixel((x, y), ink)
    directory = TemporaryDirectory()
    path = Path(directory.name) / "screenshot.png"
    image.save(path)
    return str(path), directory


class TerminalContentBottomYTest(unittest.TestCase):
    def _painted_path(
        self,
        rows: dict[int, tuple[int, int]],
        background: tuple[int, int, int] = _LIGHT_BG,
        ink: tuple[int, int, int] = _LIGHT_INK,
    ) -> str:
        path, directory = _painted(rows, background, ink)
        self.addCleanup(directory.cleanup)
        return path

    def test_returns_the_content_top_when_nothing_is_drawn(self) -> None:
        path = self._painted_path({})
        self.assertEqual(_CONTENT_TOP, terminal_content_bottom_y(path, content_top_y=_CONTENT_TOP))

    def test_finds_the_bottom_of_a_single_thin_stroke(self) -> None:
        path = self._painted_path({30: (10, 15), 31: (10, 15), 32: (10, 15)})
        self.assertEqual(32, terminal_content_bottom_y(path, content_top_y=_CONTENT_TOP))

    def test_counts_evenly_spaced_thin_rows_as_terminal_content(self) -> None:
        rows = {y: (10, 13) for y in (30, 31, 61, 62, 92, 93)}
        path = self._painted_path(rows)
        self.assertEqual(93, terminal_content_bottom_y(path, content_top_y=_CONTENT_TOP))

    def test_stops_at_a_thicker_band_rather_than_counting_it(self) -> None:
        """A soft keyboard's own key glyphs are taller than a terminal stroke - `docs/PLAN.md`'s
        own P4j-3b finding - so the last *thin* row before one is the answer, not the thick one."""
        rows = {30: (10, 13), 31: (10, 13)}
        rows.update({y: (10, 13) for y in range(60, 75)})  # a 15px-tall band: keyboard-sized
        path = self._painted_path(rows)
        self.assertEqual(31, terminal_content_bottom_y(path, content_top_y=_CONTENT_TOP))

    def test_ignores_a_stroke_above_content_top_y(self) -> None:
        """The app bar's own title text sits above the terminal and must not be counted."""
        path = self._painted_path({5: (10, 15)})
        self.assertEqual(_CONTENT_TOP, terminal_content_bottom_y(path, content_top_y=_CONTENT_TOP))

    def test_a_taller_grid_reports_a_larger_bottom_y_than_a_shorter_one(self) -> None:
        """The actual comparison every driver makes: two real captures, taller one wins."""
        shorter = self._painted_path({y: (10, 13) for y in (30, 31, 61, 62)})
        taller = self._painted_path({y: (10, 13) for y in (30, 31, 61, 62, 92, 93, 123, 124)})
        self.assertGreater(
            terminal_content_bottom_y(taller, content_top_y=_CONTENT_TOP),
            terminal_content_bottom_y(shorter, content_top_y=_CONTENT_TOP),
        )

    def test_finds_light_ink_on_a_dark_background_too(self) -> None:
        """A device run switched themes mid-investigation (`docs/DECISIONS.md`'s own P4j-3b
        entry): `VS_CODE_DARK`'s light-on-dark text must count exactly as `VS_CODE_LIGHT`'s
        dark-on-light does, since a driver cannot know which theme a capture was taken in."""
        path = self._painted_path({30: (10, 13), 31: (10, 13)}, background=_DARK_BG, ink=_DARK_INK)
        self.assertEqual(31, terminal_content_bottom_y(path, content_top_y=_CONTENT_TOP))

    def test_a_taller_grid_wins_in_dark_theme_too(self) -> None:
        shorter = self._painted_path({y: (10, 13) for y in (30, 31)}, background=_DARK_BG, ink=_DARK_INK)
        taller = self._painted_path({y: (10, 13) for y in (30, 31, 61, 62)}, background=_DARK_BG, ink=_DARK_INK)
        self.assertGreater(
            terminal_content_bottom_y(taller, content_top_y=_CONTENT_TOP),
            terminal_content_bottom_y(shorter, content_top_y=_CONTENT_TOP),
        )


class TerminalRowSpacingPxTest(unittest.TestCase):
    """`docs/PLAN.md`'s P4j-3c: the terminal's own font-size setting, read as the pixel distance
    between consecutive rows' own ink - `codeTextStyle`'s cell height rendered, so two captures at
    different sizes compare directly against each other rather than against a layout number
    neither capture carries."""

    def _painted_path(
        self,
        rows: dict[int, tuple[int, int]],
        background: tuple[int, int, int] = _LIGHT_BG,
        ink: tuple[int, int, int] = _LIGHT_INK,
    ) -> str:
        path, directory = _painted(rows, background, ink)
        self.addCleanup(directory.cleanup)
        return path

    def test_returns_zero_when_nothing_is_drawn(self) -> None:
        path = self._painted_path({})
        self.assertEqual(0, terminal_row_spacing_px(path, content_top_y=_CONTENT_TOP))

    def test_returns_zero_with_only_one_row_to_compare(self) -> None:
        path = self._painted_path({30: (10, 13)})
        self.assertEqual(0, terminal_row_spacing_px(path, content_top_y=_CONTENT_TOP))

    def test_reports_the_gap_between_two_rows_own_ink(self) -> None:
        path = self._painted_path({30: (10, 13), 61: (10, 13)})
        self.assertEqual(31, terminal_row_spacing_px(path, content_top_y=_CONTENT_TOP))

    def test_a_bigger_font_reports_a_wider_spacing_than_a_smaller_one(self) -> None:
        """The actual comparison a driver makes: two real captures, the bigger font wins."""
        small = self._painted_path({30: (10, 13), 51: (10, 13), 72: (10, 13)})
        big = self._painted_path({30: (10, 13), 61: (10, 13), 92: (10, 13)})
        self.assertGreater(
            terminal_row_spacing_px(big, content_top_y=_CONTENT_TOP),
            terminal_row_spacing_px(small, content_top_y=_CONTENT_TOP),
        )

    def test_excludes_a_thicker_band_from_the_spacing(self) -> None:
        """A keyboard's own key glyphs must not be read as a terminal row - the same rule
        `terminal_content_bottom_y` already applies, reused here."""
        rows = {30: (10, 13), 61: (10, 13)}
        rows.update({y: (10, 13) for y in range(90, 105)})  # a 15px-tall band: keyboard-sized
        path = self._painted_path(rows)
        self.assertEqual(31, terminal_row_spacing_px(path, content_top_y=_CONTENT_TOP))

    def test_works_on_light_ink_over_a_dark_background_too(self) -> None:
        path = self._painted_path({30: (10, 13), 61: (10, 13)}, background=_DARK_BG, ink=_DARK_INK)
        self.assertEqual(31, terminal_row_spacing_px(path, content_top_y=_CONTENT_TOP))


class TerminalTopStripDiffersTest(unittest.TestCase):
    """`docs/PLAN.md`'s P4j-3d-3: gesture scrollback's own proof that a drag moved *something* -
    a fixed strip just below the app bar, compared pixel-for-pixel between two captures, rather
    than a distant-column ink search. Two things that search tried and both were device-measured
    false positives, not guessed: `TerminalExtraKeysRow`'s own button-label ink reaches whatever
    column a marker line would, and so does a soft keyboard's own key-label ink - and neither
    reaches this strip, which sits just below the app bar regardless of scroll position."""

    def _painted_path(
        self,
        rows: dict[int, tuple[int, int]],
        background: tuple[int, int, int] = _LIGHT_BG,
        ink: tuple[int, int, int] = _LIGHT_INK,
        size: tuple[int, int] = (_WIDTH, _HEIGHT),
    ) -> str:
        image = Image.new("RGB", size, background)
        for y, (start, end) in rows.items():
            for x in range(start, end):
                image.putpixel((x, y), ink)
        directory = TemporaryDirectory()
        path = Path(directory.name) / "screenshot.png"
        image.save(path)
        self.addCleanup(directory.cleanup)
        return str(path)

    def test_false_when_both_captures_show_the_same_content(self) -> None:
        before = self._painted_path({30: (10, 40)})
        after = self._painted_path({30: (10, 40)})
        self.assertFalse(terminal_top_strip_differs(before, after, x_range=(0, _WIDTH), content_top_y=_CONTENT_TOP, strip_height=20))

    def test_true_when_the_strip_shows_different_content(self) -> None:
        before = self._painted_path({30: (10, 40)})  # e.g. "23"
        after = self._painted_path({30: (10, 90)})  # e.g. "MARKER_123..."
        self.assertTrue(terminal_top_strip_differs(before, after, x_range=(0, _WIDTH), content_top_y=_CONTENT_TOP, strip_height=20))

    def test_ignores_a_difference_below_the_strip(self) -> None:
        """A change well past `strip_height` - `TerminalExtraKeysRow`/a keyboard's own territory
        - must not register, the whole reason this compares a bounded strip rather than the
        two captures' full remaining height."""
        size = (_WIDTH, 600)
        before = self._painted_path({30: (10, 40)}, size=size)
        after = self._painted_path({30: (10, 40), 500: (10, 590)}, size=size)
        self.assertFalse(terminal_top_strip_differs(before, after, x_range=(0, _WIDTH), content_top_y=_CONTENT_TOP, strip_height=20))

    def test_works_on_light_ink_over_a_dark_background_too(self) -> None:
        before = self._painted_path({30: (10, 40)}, background=_DARK_BG, ink=_DARK_INK)
        after = self._painted_path({30: (10, 90)}, background=_DARK_BG, ink=_DARK_INK)
        self.assertTrue(terminal_top_strip_differs(before, after, x_range=(0, _WIDTH), content_top_y=_CONTENT_TOP, strip_height=20))


if __name__ == "__main__":
    unittest.main()
