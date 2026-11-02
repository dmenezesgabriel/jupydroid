#!/usr/bin/env python3
"""Tests for the chrome-parts oracle, including one run over real device frames in `testdata/`."""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from PIL import Image
from read_chrome_parts import chrome_bands, part_seams

RAIL = (0x19, 0x1A, 0x1B)
PAGE = (0x12, 0x13, 0x14)
SEAM = (0x2A, 0x2B, 0x2C)
#: Frames captured on the tablet, tracked so the run below holds in any clone (ADR-0132).
TESTDATA = pathlib.Path(__file__).resolve().parent / "testdata"


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
        for column in range(40, 60):  # a label's own glyph strokes, a minority of the column
            for row in range(90, 100):
                image.putpixel((column, row), (0xBB, 0xBE, 0xBF))
        image.save(frame)
        self.assertEqual(["#191A1B", "#121314"], [band.colour for band in chrome_bands(frame)])


class PartSeamsTest(unittest.TestCase):
    """The border *between* two parts - the thing [chrome_bands] drops on purpose."""

    def test_a_seam_reports_the_colours_drawn_in_it(self) -> None:
        frame = _painted([(120, RAIL), (2, SEAM), (300, PAGE)])
        self.assertEqual([("#2A2B2C",)], [seam.colours for seam in part_seams(frame)])

    def test_two_parts_that_abut_have_an_empty_seam(self) -> None:
        """No border drawn at all, which is a finding rather than an absence of one."""
        frame = _painted([(120, RAIL), (300, PAGE)])
        self.assertEqual([()], [seam.colours for seam in part_seams(frame)])

    def test_a_seam_reports_the_gap_it_fills(self) -> None:
        frame = _painted([(120, RAIL), (2, SEAM), (300, PAGE)])
        (seam,) = part_seams(frame)
        self.assertEqual((119, 122, 2), (seam.after, seam.before, seam.width))

    def test_three_parts_have_two_seams(self) -> None:
        frame = _painted([(120, RAIL), (2, SEAM), (540, RAIL), (2, SEAM), (700, PAGE)])
        self.assertEqual(2, len(part_seams(frame)))

    def test_an_antialiased_seam_reports_every_column_of_it(self) -> None:
        """A 1dp divider lands off the pixel grid, so the border is one solid column and one blend.

        Measured, not supposed: `evidence/p7a-2-part-borders/before-the-fix/two-pane/dark/`
        reads `#2A2B2C` at x=120 and `#1E1E20` at x=121.
        """
        frame = _painted([(120, RAIL), (1, SEAM), (1, (0x1E, 0x1E, 0x20)), (300, PAGE)])
        self.assertEqual([("#2A2B2C", "#1E1E20")], [seam.colours for seam in part_seams(frame)])

    def test_one_part_has_no_seams(self) -> None:
        """A one-pane frame is the whole window in one colour - nothing to separate."""
        self.assertEqual([], part_seams(_painted([(320, RAIL)])))


class ThisRepositoryTest(unittest.TestCase):
    """The chrome defects, read off the frames that showed them.

    `chrome-before-the-rail-colour/` is the shipped chrome from before the rail and side panel had
    colours of their own. Asserting the defect here rather than only in prose is what makes the
    after-frames' own assertion mean something: an oracle that cannot see the bug cannot see the fix either.
    """

    def test_the_committed_before_frame_has_one_field_where_it_should_have_two(self) -> None:
        frame = TESTDATA / "chrome-before-the-rail-colour/dark/01-the-explorer.png"
        bands = chrome_bands(str(frame))
        self.assertEqual({"#121314"}, {band.colour for band in bands})

    def test_the_committed_before_frame_draws_one_of_its_two_seams(self) -> None:
        """The part-border defect, read off the frame shipped before the borders.

        The rail is separated from the side panel and the side panel is **not** separated from the
        document: `sideBar.border` is stated in both 2026 themes and nothing in this app drew it.
        Asserting it here is what makes the after-frames' green mean something.
        """
        before = TESTDATA / "chrome-before-part-borders"
        for theme, border in (("dark", "#2A2B2C"), ("light", "#F0F1F2")):
            with self.subTest(theme):
                rail_to_panel, panel_to_document = part_seams(str(before / theme / "01-the-file-browser.png"))
                self.assertIn(border, rail_to_panel.colours)
                self.assertEqual((), panel_to_document.colours)


if __name__ == "__main__":
    unittest.main()
