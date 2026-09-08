#!/usr/bin/env python3
"""Tests for the chrome-parts check, including one run over the frames P7a captured."""
from __future__ import annotations

import pathlib
import tempfile
import unittest

from PIL import Image

from check_chrome_parts import frame_failures

RAIL = (0x19, 0x1A, 0x1B)
PAGE = (0x12, 0x13, 0x14)
SEAM = (0x2A, 0x2B, 0x2C)
PART_HEX = "#191A1B"
PAGE_HEX = "#121314"


def _painted(bands: list[tuple[int, tuple[int, int, int]]], name: str) -> str:
    image = Image.new("RGB", (sum(width for width, _ in bands), 200))
    x = 0
    for width, colour in bands:
        for column in range(x, x + width):
            for row in range(200):
                image.putpixel((column, row), colour)
        x += width
    path = pathlib.Path(tempfile.mkdtemp()) / name
    image.save(path)
    return str(path)


class FrameFailuresTest(unittest.TestCase):
    def test_the_reference_shape_passes(self) -> None:
        frame = _painted([(120, RAIL), (2, SEAM), (540, RAIL), (2, SEAM), (700, PAGE)], "01-the-file-browser.png")
        self.assertEqual([], frame_failures(frame, PART_HEX, PAGE_HEX, "two-pane"))

    def test_the_defect_this_step_closes_is_reported(self) -> None:
        """P2u-1's own frame: rail, side panel and page all one field."""
        frame = _painted([(120, PAGE), (2, SEAM), (540, PAGE), (2, SEAM), (700, PAGE)], "01-the-file-browser.png")
        self.assertNotEqual([], frame_failures(frame, PART_HEX, PAGE_HEX, "two-pane"))

    def test_a_cell_supplying_the_part_colour_does_not_excuse_a_wrong_rail(self) -> None:
        """The reason this checks position rather than presence.

        `EditorPalette.cellBackground` is the same hex as `sideBar.background`, so an open
        notebook puts the part colour on screen whatever the rail is drawn in. A check that
        only asked "is `#191A1B` somewhere in this frame" would pass over a broken rail.
        """
        frame = _painted([(120, PAGE), (2, SEAM), (700, RAIL)], "02-a-document-beside-the-panel.png")
        self.assertEqual(
            ["02-a-document-beside-the-panel.png: leftmost part is #121314, expected #191A1B"],
            [failure.split("/")[-1] for failure in frame_failures(frame, PART_HEX, PAGE_HEX, "two-pane")],
        )

    def test_one_pane_is_not_held_to_the_two_pane_shape(self) -> None:
        """At one pane there is no rail and no document beside the panel - the panel is the window."""
        frame = _painted([(320, RAIL)], "01-the-file-browser.png")
        self.assertEqual([], frame_failures(frame, PART_HEX, PAGE_HEX, "one-pane"))

    def test_a_one_pane_document_frame_carries_no_part_to_check(self) -> None:
        """The document *is* the window there, so the frame is context and not evidence.

        Holding it to "the leftmost band is the part" would fail on correct behaviour - which is
        a gate that has to be worked around, and this repository's rule is the opposite.
        """
        frame = _painted([(320, PAGE)], "03-a-document-beside-the-panel.png")
        self.assertEqual([], frame_failures(frame, PART_HEX, PAGE_HEX, "one-pane"))

    def test_a_two_pane_document_frame_still_has_its_rail_checked(self) -> None:
        """The same frame at two panes does carry the rail, so the skip must not reach it."""
        frame = _painted([(120, PAGE), (2, SEAM), (700, RAIL)], "03-a-document-beside-the-panel.png")
        self.assertNotEqual([], frame_failures(frame, PART_HEX, PAGE_HEX, "two-pane"))


class ThisRepositoryTest(unittest.TestCase):
    def test_the_captured_two_pane_frames_hold(self) -> None:
        root = pathlib.Path(__file__).resolve().parent.parent
        captured = sorted((root / "evidence/p7a-chrome-parts/two-pane/dark").glob("*.png"))
        self.assertNotEqual([], captured, "P7a's own frames are missing")
        for frame in captured:
            self.assertEqual([], frame_failures(str(frame), PART_HEX, PAGE_HEX, "two-pane"))


if __name__ == "__main__":
    unittest.main()
