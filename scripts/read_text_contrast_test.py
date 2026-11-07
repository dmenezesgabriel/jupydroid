#!/usr/bin/env python3
"""Tests for the text-contrast reader, including one run over real device frames in `testdata/`."""

from __future__ import annotations

import pathlib
import tempfile
import unittest

from PIL import Image
from read_text_contrast import STATUS_BAR_ROWS, region_contrast, status_bar_contrast, text_contrasts

WHITE = (0xFF, 0xFF, 0xFF)
#: The darkest grey that still clears 4.5:1 on white (4.54:1), and the one just under it (4.48:1).
AA_EDGE = (0x76, 0x76, 0x76)
JUST_UNDER = (0x77, 0x77, 0x77)
#: Frames and dumps captured on the tablet, tracked so the run below holds in any clone (ADR-0132).
TESTDATA = pathlib.Path(__file__).resolve().parent / "testdata" / "toggle-wash-from-the-list-colour"


def _dump(texts: dict[str, tuple[int, int, int, int]], screen: tuple[int, int] = (320, 640)) -> str:
    """A `uiautomator dump` holding [texts] as `text` -> bounds, inside a [screen] root."""
    rows = "".join(
        f'<node text="{text}" bounds="[{left},{top}][{right},{bottom}]" />'
        for text, (left, top, right, bottom) in texts.items()
    )
    return (
        '<?xml version="1.0" encoding="UTF-8"?><hierarchy rotation="0">'
        f'<node text="" bounds="[0,0][{screen[0]},{screen[1]}]">{rows}</node></hierarchy>'
    )


def _frame_with_ink(size: tuple[int, int], ink: tuple[int, int, int], inked: tuple[int, int, int, int]) -> Image.Image:
    """A white frame with one [ink] rectangle, `inked` as left, top, right, bottom."""
    image = Image.new("RGB", size, WHITE)
    image.paste(Image.new("RGB", (inked[2] - inked[0], inked[3] - inked[1]), ink), inked[:2])
    return image


def _saved(image: Image.Image) -> str:
    path = pathlib.Path(tempfile.mkdtemp()) / "frame.png"
    image.save(path)
    return str(path)


class RegionContrastTest(unittest.TestCase):
    def test_ink_at_the_aa_edge_reads_just_over_4_5(self) -> None:
        reading = region_contrast(_frame_with_ink((100, 40), AA_EDGE, (10, 10, 20, 20)), (0, 0, 100, 40))
        self.assertEqual((reading.ground, reading.ink), ("#FFFFFF", "#767676"))
        self.assertGreaterEqual(reading.ratio, 4.5)

    def test_ink_one_step_lighter_reads_under_4_5(self) -> None:
        reading = region_contrast(_frame_with_ink((100, 40), JUST_UNDER, (10, 10, 20, 20)), (0, 0, 100, 40))
        self.assertLess(reading.ratio, 4.5)

    def test_ground_is_the_fill_when_ink_is_the_minority(self) -> None:
        image = _frame_with_ink((100, 40), (0, 0, 0), (10, 10, 60, 30))
        self.assertEqual(region_contrast(image, (0, 0, 100, 40)).ground, "#FFFFFF")

    def test_a_region_of_one_colour_reads_one_to_one(self) -> None:
        self.assertEqual(region_contrast(Image.new("RGB", (10, 10), WHITE), (0, 0, 10, 10)).ratio, 1.0)

    def test_an_empty_box_names_the_box_and_the_frame(self) -> None:
        with self.assertRaisesRegex(ValueError, r"\(5, 5, 5, 9\).*10x10"):
            region_contrast(Image.new("RGB", (10, 10), WHITE), (5, 5, 5, 9))


class TextContrastsTest(unittest.TestCase):
    def test_each_text_node_is_read_inside_its_own_bounds(self) -> None:
        image = _frame_with_ink((320, 640), AA_EDGE, (20, 20, 30, 30))
        dump = _dump({"Search": (10, 10, 60, 40), "Elsewhere": (100, 100, 150, 130)})
        readings = {at.text: at for at in text_contrasts(dump, _saved(image))}
        self.assertGreaterEqual(readings["Search"].reading.ratio, 4.5)
        self.assertEqual(readings["Elsewhere"].reading.ratio, 1.0)

    def test_a_frame_at_twice_the_dumps_scale_reads_the_same_pair(self) -> None:
        image = _frame_with_ink((640, 1280), AA_EDGE, (40, 40, 60, 60))
        (found,) = text_contrasts(_dump({"Search": (10, 10, 60, 40)}), _saved(image))
        self.assertEqual((found.reading.ground, found.reading.ink), ("#FFFFFF", "#767676"))

    def test_a_frame_of_another_shape_is_refused_with_both_sizes(self) -> None:
        with self.assertRaisesRegex(ValueError, r"640x640.*320x640"):
            text_contrasts(_dump({"Search": (10, 10, 60, 40)}), _saved(Image.new("RGB", (640, 640), WHITE)))

    def test_nodes_without_text_or_area_produce_no_reading(self) -> None:
        dump = _dump({"": (10, 10, 60, 40), "Hidden": (70, 70, 70, 90)})
        self.assertEqual(text_contrasts(dump, _saved(Image.new("RGB", (320, 640), WHITE))), [])

    def test_a_node_scrolled_partly_off_the_frame_is_read_on_what_is_visible(self) -> None:
        image = _frame_with_ink((320, 640), AA_EDGE, (300, 600, 320, 640))
        (found,) = text_contrasts(_dump({"Edge": (290, 590, 400, 700)}), _saved(image))
        self.assertEqual(found.box, (290, 590, 320, 640))
        self.assertGreaterEqual(found.reading.ratio, 4.5)

    def test_a_node_wholly_off_the_frame_produces_no_reading(self) -> None:
        dump = _dump({"Gone": (400, 700, 500, 800)})
        self.assertEqual(text_contrasts(dump, _saved(Image.new("RGB", (320, 640), WHITE))), [])

    def test_readings_come_lowest_ratio_first(self) -> None:
        image = _frame_with_ink((320, 640), (0, 0, 0), (100, 100, 120, 120))
        dump = _dump({"Strong": (100, 100, 150, 130), "Faint": (10, 10, 60, 40)})
        self.assertEqual([at.text for at in text_contrasts(dump, _saved(image))], ["Faint", "Strong"])


class RealFrameTest(unittest.TestCase):
    """Ground and ink below were sampled by hand from the committed PNGs, not read by the module."""

    def _readings(self, theme: str) -> dict[str, tuple[str, str, float]]:
        found = text_contrasts(
            (TESTDATA / theme / "hierarchy.xml").read_text(encoding="utf-8"),
            str(TESTDATA / theme / "02-the-toggle-on.png"),
        )
        return {at.text: (at.reading.ground, at.reading.ink, round(at.reading.ratio, 2)) for at in found}

    def test_dark_labels_and_the_toggle_on_its_wash(self) -> None:
        readings = self._readings("dark")
        self.assertEqual(readings["Search"], ("#191A1B", "#BBBEBF", 9.32))
        self.assertEqual(readings["Aa"], ("#2C2D2E", "#BBBEBF", 7.38))
        self.assertEqual(readings["ab"], ("#191A1B", "#8C8C8C", 5.18))

    def test_light_labels_and_the_toggle_on_its_wash(self) -> None:
        readings = self._readings("light")
        self.assertEqual(readings["Search"], ("#FAFAFD", "#202020", 15.64))
        self.assertEqual(readings["Aa"], ("#DADADA", "#202020", 11.66))
        self.assertEqual(readings["ab"], ("#FAFAFD", "#606060", 6.04))


class StatusBarContrastTest(unittest.TestCase):
    def test_the_band_is_read_across_the_frames_full_width(self) -> None:
        top, bottom = STATUS_BAR_ROWS
        image = _frame_with_ink((320, 640), AA_EDGE, (300, top + 2, 310, bottom - 2))
        reading = status_bar_contrast(image)
        self.assertEqual((reading.ground, reading.ink), ("#FFFFFF", "#767676"))

    def test_ink_below_the_band_is_not_the_status_bars(self) -> None:
        image = _frame_with_ink((320, 640), (0, 0, 0), (0, 300, 320, 400))
        self.assertEqual(status_bar_contrast(image).ratio, 1.0)


if __name__ == "__main__":
    unittest.main()
