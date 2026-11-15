#!/usr/bin/env python3
"""Tests for read_terminal_margin.py."""

from __future__ import annotations

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image
from read_terminal_margin import first_row_centre_y, last_cell_centre_x, read_terminal_margin

_BACKGROUND = (25, 26, 27)  # measured off a real VS_CODE_DARK capture
_INK = (204, 204, 204)
_WIDTH = 1000
_HEIGHT = 400
_GLYPH_WIDTH = 8
_PITCH = 10
_RULER_Y = 100
_PANE = (100, 50, 900, 350)  # left, top, right, bottom in px


def _hierarchy(bounds: tuple[int, int, int, int], resource_id: str = "terminal-input") -> str:
    left, top, right, bottom = bounds
    return (
        '<?xml version="1.0"?><hierarchy rotation="0">'
        '<node resource-id="" bounds="[0,0][1000,400]">'
        f'<node resource-id="{resource_id}" bounds="[{left},{top}][{right},{bottom}]"/>'
        "</node></hierarchy>"
    )


class _Frame:
    """A painted capture and its directory, cleaned up with the test."""

    def __init__(self, case: unittest.TestCase) -> None:
        self.image = Image.new("RGB", (_WIDTH, _HEIGHT), _BACKGROUND)
        directory = TemporaryDirectory()
        case.addCleanup(directory.cleanup)
        self.path = str(Path(directory.name) / "screenshot.png")

    def ruler(self, first_x: int, glyphs: int, y: int = _RULER_Y, pitch: float = _PITCH) -> None:
        """[glyphs] ink cells, [pitch] apart, a thin stroke tall, the first drawn at [first_x]."""
        for glyph in range(glyphs):
            start = round(first_x + glyph * pitch)
            for x in range(start, start + _GLYPH_WIDTH):
                self.image.putpixel((x, y), _INK)

    def save(self) -> str:
        self.image.save(self.path)
        return self.path


class PressPointTest(unittest.TestCase):
    def test_the_last_columns_centre_is_half_a_pitch_before_the_end_of_the_counted_columns(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=77)
        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

        # pane left 100 + margin 12 + 76.5 columns of 10 px
        self.assertEqual(877, last_cell_centre_x(reading, pane_left_px=_PANE[0], density=160))

    def test_the_first_rows_centre_y_is_the_middle_of_the_topmost_band_of_ink(self) -> None:
        frame = _Frame(self)
        for y in (90, 91, 92, 93, 94):  # the row a cleared screen starts with
            frame.ruler(first_x=112, glyphs=77, y=y)
        frame.ruler(first_x=112, glyphs=3, y=130)  # the prompt below it

        self.assertEqual(92, first_row_centre_y(frame.save(), _hierarchy(_PANE)))

    def test_names_the_pane_when_there_is_no_ink_to_press(self) -> None:
        frame = _Frame(self)

        with self.assertRaisesRegex(ValueError, "no ink"):
            first_row_centre_y(frame.save(), _hierarchy(_PANE))


class ReadTerminalMarginTest(unittest.TestCase):
    def test_reads_the_gap_from_the_panes_left_edge_in_dp(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=77)

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

        self.assertEqual(12.0, reading.left_gap_dp)

    def test_converts_pixels_to_dp_at_the_devices_density(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=118, glyphs=50)  # 18 px in from the pane at 1.5 px per dp

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=240, reported_columns=50)

        self.assertEqual(12.0, reading.left_gap_dp)

    def test_reports_zero_when_the_ink_starts_at_the_panes_edge(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=_PANE[0], glyphs=79)

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=79)

        self.assertEqual(0.0, reading.left_gap_dp)

    def test_reads_the_gap_from_the_last_column_to_the_panes_right_edge(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=77)  # the last glyph ends at 112 + 76 * 10 + 8 = 880

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

        self.assertEqual(20.0, reading.right_gap_dp)

    def test_measures_the_pitch_the_glyphs_are_drawn_at(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=77)

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

        self.assertEqual(float(_PITCH), reading.pitch_px)

    def test_counts_the_columns_the_width_inside_the_margin_holds_at_that_pitch(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=77)

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

        self.assertEqual((800 - 2 * 12) // 10, reading.columns_at_pitch)
        self.assertEqual(77, reading.reported_columns)

    def test_shows_a_drawn_pitch_narrower_than_the_one_the_columns_were_counted_with(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=77, pitch=9.8)  # 776 px inside the margin holds 79 of these, not 77

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

        self.assertAlmostEqual(9.8, reading.pitch_px, places=2)
        self.assertEqual(79, reading.columns_at_pitch)

    def test_names_both_counts_when_the_ruler_is_not_the_reported_width(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=70)

        with self.assertRaisesRegex(ValueError, "70 glyphs.*77"):
            read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

    def test_takes_the_ruler_from_the_widest_row_not_the_first_one_drawn(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=3, y=60)  # a prompt line above the ruler
        frame.ruler(first_x=112, glyphs=77)
        frame.ruler(first_x=112, glyphs=5, y=140)  # and a shorter one below it

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

        self.assertEqual(20.0, reading.right_gap_dp)

    def test_ignores_ink_outside_the_pane(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=77)
        frame.ruler(first_x=0, glyphs=100, y=20)  # the app bar, above the pane

        reading = read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)

        self.assertEqual(12.0, reading.left_gap_dp)

    def test_names_the_value_when_the_dump_has_no_terminal(self) -> None:
        frame = _Frame(self)
        frame.ruler(first_x=112, glyphs=77)

        with self.assertRaisesRegex(LookupError, "terminal-input"):
            read_terminal_margin(frame.save(), _hierarchy(_PANE, "other"), density=160, reported_columns=77)

    def test_names_the_value_when_nothing_is_drawn_in_the_pane(self) -> None:
        frame = _Frame(self)

        with self.assertRaisesRegex(ValueError, r"\(100, 50, 900, 350\)"):
            read_terminal_margin(frame.save(), _hierarchy(_PANE), density=160, reported_columns=77)


if __name__ == "__main__":
    unittest.main()
