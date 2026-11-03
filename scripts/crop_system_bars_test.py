"""Tests for crop_system_bars: a README frame must not carry the device's status or navigation bar."""

from __future__ import annotations

import io
import pathlib
import tempfile
import unittest

from crop_system_bars import (
    SystemBarInsets,
    crop_system_bars,
    main,
    read_system_bar_insets,
)
from PIL import Image
from pixel_reads import pixel_at

FIXTURE = pathlib.Path(__file__).parent / "testdata" / "system-bars-dumpsys-window" / "landscape-tablet.txt"
BAR = (255, 0, 0)
PAGE = (10, 20, 30)


def frame_with_bars(width: int, height: int, top: int, bottom: int) -> Image.Image:
    """A page-coloured frame whose first `top` and last `bottom` rows are BAR-coloured."""
    image = Image.new("RGB", (width, height), PAGE)
    for y in list(range(top)) + list(range(height - bottom, height)):
        for x in range(width):
            image.putpixel((x, y), BAR)
    return image


class ReadSystemBarInsetsTest(unittest.TestCase):
    def test_reads_the_tablets_landscape_bars_from_a_real_dumpsys(self) -> None:
        insets = read_system_bar_insets(FIXTURE.read_text())
        self.assertEqual(SystemBarInsets(top_px=45, bottom_px=72), insets)

    def test_ignores_the_gesture_and_ime_sources_that_share_the_same_edge(self) -> None:
        text = "InsetsSource id=3 type=ime frame=[0,0][0,0] visibleFrame=[0,100][50,200] sideHint=NONE\n"
        text += "InsetsSource id=1 type=statusBars frame=[0,0][50,7] visible=true sideHint=TOP\n"
        self.assertEqual(SystemBarInsets(top_px=7, bottom_px=0), read_system_bar_insets(text))

    def test_a_hidden_bar_takes_no_rows(self) -> None:
        text = "InsetsSource id=1 type=statusBars frame=[0,0][50,7] visible=false sideHint=TOP\n"
        self.assertEqual(SystemBarInsets(top_px=0, bottom_px=0), read_system_bar_insets(text))

    def test_a_bar_on_the_side_is_refused_with_its_value(self) -> None:
        text = "InsetsSource id=2 type=navigationBars frame=[0,0][72,50] visible=true sideHint=LEFT\n"
        with self.assertRaisesRegex(ValueError, r"sideHint=LEFT.*navigationBars"):
            read_system_bar_insets(text)

    def test_text_with_no_bar_at_all_is_refused_rather_than_read_as_zero_insets(self) -> None:
        with self.assertRaisesRegex(ValueError, r"no statusBars or navigationBars"):
            read_system_bar_insets("Window #1 Window{abc u0 com.example/Main}\n")


class CropSystemBarsTest(unittest.TestCase):
    def test_removes_exactly_the_top_and_bottom_rows_and_keeps_the_width(self) -> None:
        cropped = crop_system_bars(frame_with_bars(8, 40, 3, 5), SystemBarInsets(top_px=3, bottom_px=5))
        self.assertEqual((8, 32), cropped.size)

    def test_no_bar_coloured_pixel_survives(self) -> None:
        cropped = crop_system_bars(frame_with_bars(8, 40, 3, 5), SystemBarInsets(top_px=3, bottom_px=5))
        colours = {pixel_at(cropped, (x, y)) for x in range(8) for y in range(32)}
        self.assertEqual({PAGE}, colours)

    def test_insets_that_leave_no_rows_are_refused_with_the_values(self) -> None:
        with self.assertRaisesRegex(ValueError, r"top_px=20.*bottom_px=20.*height 40"):
            crop_system_bars(Image.new("RGB", (8, 40)), SystemBarInsets(top_px=20, bottom_px=20))

    def test_a_negative_inset_is_refused_with_its_value(self) -> None:
        with self.assertRaisesRegex(ValueError, r"top_px=-1"):
            crop_system_bars(Image.new("RGB", (8, 40)), SystemBarInsets(top_px=-1, bottom_px=0))


class MainTest(unittest.TestCase):
    def test_crops_a_png_on_disk_by_the_bars_a_dumpsys_file_names(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source, target = pathlib.Path(tmp) / "raw.png", pathlib.Path(tmp) / "out.png"
            frame_with_bars(1920, 1200, 45, 72).save(source)
            status = main([str(FIXTURE), str(source), str(target)])
            cropped = Image.open(io.BytesIO(target.read_bytes()))
        self.assertEqual((0, (1920, 1200 - 45 - 72)), (status, cropped.size))

    def test_a_missing_argument_is_a_usage_error_naming_the_shape(self) -> None:
        with self.assertRaisesRegex(SystemExit, r"usage: crop_system_bars.py"):
            main(["only-one"])


if __name__ == "__main__":
    unittest.main()
