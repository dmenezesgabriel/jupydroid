#!/usr/bin/env python3
"""Tests for the dialog-margin reader, including one run over a real `dev36` frame in `testdata/`."""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from PIL import Image
from read_dialog_margin import dialog_margins_dp, surface_margins_px

SCRIM = (0x64, 0x64, 0x65)
SURFACE = (0xF0, 0xF1, 0xF2)
INK = (0x20, 0x20, 0x20)
#: A frame captured on `dev36` at 320dp with Delete's dialog up, tracked so the run holds in any clone (ADR-0132).
TESTDATA = pathlib.Path(__file__).resolve().parent / "testdata" / "dialog-margin"


def _frame(left: int, surface: int, right: int, ink_at: list[int]) -> str:
    """A one-row-band PNG: scrim, surface with ink columns at `ink_at`, scrim; saved to a temporary path."""
    image = Image.new("RGB", (left + surface + right, 10), SCRIM)
    for x in range(left, left + surface):
        for y in range(10):
            image.putpixel((x, y), INK if x in ink_at else SURFACE)
    path = pathlib.Path(tempfile.mkdtemp()) / "frame.png"
    image.save(path)
    return str(path)


class SurfaceMarginsTest(unittest.TestCase):
    def test_a_surface_with_a_scrim_each_side_reads_each_scrim_width(self) -> None:
        self.assertEqual((24, 30), surface_margins_px(_frame(24, 266, 30, []), 5))

    def test_ink_inside_the_surface_does_not_move_its_edge(self) -> None:
        """Text on the row is a minority of it: the surface's colour is the row's mode, not one pixel."""
        frame = _frame(24, 272, 24, list(range(60, 120)))
        self.assertEqual((24, 24), surface_margins_px(frame, 5))

    def test_a_surface_that_fills_the_window_reads_no_margin(self) -> None:
        """The state before the margin existed: a wrapped dialog's card was the window's whole width."""
        self.assertEqual((0, 0), surface_margins_px(_frame(0, 320, 0, [100]), 5))


class DialogMarginsDpTest(unittest.TestCase):
    def test_deletes_dialog_at_320dp_keeps_24dp_each_side(self) -> None:
        """Delete's card is a third of the column, so a column-mode reader reads 30px; its row does not."""
        margins = dialog_margins_dp(str(TESTDATA / "delete-320dp.png"), str(TESTDATA / "delete-320dp.xml"), 160)
        self.assertEqual((24.0, 24.0), margins)


if __name__ == "__main__":
    unittest.main()
