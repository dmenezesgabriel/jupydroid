#!/usr/bin/env python3
"""Tests for the chrome-parts oracle, including one run over this repository's own frames."""
from __future__ import annotations

import pathlib
import tempfile
import unittest

from PIL import Image

from read_chrome_parts import chrome_bands

RAIL = (0x19, 0x1A, 0x1B)
PAGE = (0x12, 0x13, 0x14)
SEAM = (0x2A, 0x2B, 0x2C)


def _painted(bands: list[tuple[int, tuple[int, int, int]]], height: int = 200) -> str:
    """A PNG built from `(width, colour)` bands, left to right, saved to a temporary path."""
    image = Image.new("RGB", (sum(width for width, _ in bands), height))
    x = 0
    for width, colour in bands:
        for column in range(x, x + width):
            for row in range(height):
                image.putpixel((column, row), colour)
        x += width
    path = pathlib.Path(tempfile.mkdtemp()) / "frame.png"
    image.save(path)
    return str(path)


class ChromeBandsTest(unittest.TestCase):
    def test_three_parts_read_as_three_bands(self) -> None:
        frame = _painted([(120, RAIL), (2, SEAM), (580, RAIL), (2, SEAM), (700, PAGE)])
        self.assertEqual(["#191A1B", "#191A1B", "#121314"], [band.colour for band in chrome_bands(frame)])

    def test_a_seam_is_too_narrow_to_be_a_part(self) -> None:
        """A 1-2px border is a line between parts, not a part - it must not be reported as one."""
        frame = _painted([(200, RAIL), (2, SEAM), (200, PAGE)])
        self.assertNotIn("#2A2B2C", [band.colour for band in chrome_bands(frame)])

    def test_a_band_reports_where_it_starts_and_ends(self) -> None:
        frame = _painted([(120, RAIL), (2, SEAM), (300, PAGE)])
        rail, page = chrome_bands(frame)
        self.assertEqual((0, 119), (rail.start, rail.end))
        self.assertEqual((122, 421), (page.start, page.end))

    def test_text_in_a_column_does_not_split_a_band(self) -> None:
        """The mode down a column, not one pixel of it - a row label must not read as its own part."""
        frame = _painted([(200, RAIL), (200, PAGE)])
        image = Image.open(frame)
        for column in range(40, 60):          # a label's own glyph strokes, a minority of the column
            for row in range(90, 100):
                image.putpixel((column, row), (0xBB, 0xBE, 0xBF))
        image.save(frame)
        self.assertEqual(["#191A1B", "#121314"], [band.colour for band in chrome_bands(frame)])


class ThisRepositoryTest(unittest.TestCase):
    """The defect P7a exists to close, read off the frame that showed it.

    `evidence/p2u-1-chrome/after-tablet/` is the shipped chrome *before* P7a-1. Asserting the
    defect here rather than only in prose is what makes the after-frames' own assertion mean
    something: an oracle that cannot see the bug cannot see the fix either.
    """

    def test_the_committed_before_frame_has_one_field_where_it_should_have_two(self) -> None:
        root = pathlib.Path(__file__).resolve().parent.parent
        frame = root / "evidence/p2u-1-chrome/after-tablet/dark/01-the-explorer.png"
        bands = chrome_bands(str(frame))
        self.assertEqual({"#121314"}, {band.colour for band in bands})


if __name__ == "__main__":
    unittest.main()
