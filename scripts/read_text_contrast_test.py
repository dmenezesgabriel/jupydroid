#!/usr/bin/env python3
"""Tests for the text-contrast reader, including one run over real device frames in `testdata/`."""

from __future__ import annotations

import contextlib
import io
import pathlib
import tempfile
import unittest

from crop_system_bars import read_system_bar_insets
from PIL import Image
from read_text_contrast import STATUS_BAR_ROWS, main, region_contrast, status_bar_contrast, text_contrasts

WHITE = (0xFF, 0xFF, 0xFF)
#: The darkest grey that still clears 4.5:1 on white (4.54:1), and the one just under it (4.48:1).
AA_EDGE = (0x76, 0x76, 0x76)
JUST_UNDER = (0x77, 0x77, 0x77)
#: Frames and dumps captured on the tablet, tracked so the run below holds in any clone (ADR-0132).
#: The line `adb shell dumpsys window` printed on the `dev36` emulator, a phone: 24px at 160dpi.
DEV36_STATUS_BAR_SOURCE = (
    "    InsetsSource id=ef1a0000 type=statusBars frame=[0,0][320,24] visible=true flags= sideHint=TOP"
    " boundingRects=null\n"
)
#: A `dev36` frame (320x640 at 160dpi) and the `statusBars` line of its `dumpsys window`, tracked likewise.
PHONE_TESTDATA = pathlib.Path(__file__).resolve().parent / "testdata" / "phone-status-bar"
TESTDATA = pathlib.Path(__file__).resolve().parent / "testdata" / "toggle-wash-from-the-list-colour"
#: Crops of tablet frames around one border or one focus mark, tracked likewise. Each box below is in its crop's pixels.
BORDER_TESTDATA = pathlib.Path(__file__).resolve().parent / "testdata" / "border-and-focus"


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


def _popup_dump(text: str, window: tuple[int, int, int, int]) -> str:
    """A `uiautomator dump` of a popup: one [text] node whose root states only the popup's own [window]."""
    left, top, right, bottom = window
    bounds = f'bounds="[{left},{top}][{right},{bottom}]"'
    return f'<hierarchy rotation="0"><node text="" {bounds}><node text="{text}" {bounds} /></node></hierarchy>'


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

    def test_a_popup_windows_dump_is_read_at_the_frames_own_scale(self) -> None:
        # A popup's dump states only its own corner of the display, so it has no scale to derive.
        image = _frame_with_ink((320, 640), AA_EDGE, (210, 110, 220, 120))
        (found,) = text_contrasts(_popup_dump("No headings", (200, 100, 300, 140)), _saved(image))
        self.assertEqual(found.box, (200, 100, 300, 140))
        self.assertGreaterEqual(found.reading.ratio, 4.5)

    def test_a_popup_that_overruns_the_frame_is_refused_with_both_sizes(self) -> None:
        small_frame = _saved(Image.new("RGB", (100, 100), WHITE))
        with self.assertRaisesRegex(ValueError, r"300x140.*100x100"):
            text_contrasts(_popup_dump("No headings", (200, 100, 300, 140)), small_frame)

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


class BelowFloorTest(unittest.TestCase):
    """`--below` is how a driver reads 24 frames: the pairs under the floor, and how many it read."""

    def _run(self, *args: str) -> tuple[int, str]:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = main(["read_text_contrast.py", *args])
        return status, output.getvalue()

    def _files(self) -> tuple[str, str]:
        image = _frame_with_ink((320, 640), JUST_UNDER, (20, 20, 30, 30))
        image.paste(Image.new("RGB", (10, 10), (0, 0, 0)), (110, 110))
        dump = _dump({"Faint": (10, 10, 60, 40), "Strong": (100, 100, 150, 130), "Blank": (200, 200, 250, 230)})
        directory = pathlib.Path(tempfile.mkdtemp())
        (directory / "hierarchy.xml").write_text(dump, encoding="utf-8")
        image.save(directory / "frame.png")
        return str(directory / "hierarchy.xml"), str(directory / "frame.png")

    def test_only_the_pairs_under_the_floor_are_listed(self) -> None:
        status, printed = self._run(*self._files(), "--below", "4.5")
        self.assertEqual(status, 0)
        self.assertIn("'Faint'", printed)
        self.assertNotIn("'Strong'", printed)

    def test_a_region_with_no_ink_is_named_apart_from_the_pairs_under_the_floor(self) -> None:
        # A node a scroll container clips to a sliver has no glyph in view: 1.00:1 is not its ratio.
        printed = self._run(*self._files(), "--below", "4.5")[1]
        self.assertIn("no ink in view  'Blank'", printed)
        self.assertNotIn("1.00:1", printed)

    def test_the_last_line_counts_what_was_under_and_what_had_no_ink_against_what_was_read(self) -> None:
        printed = self._run(*self._files(), "--below", "4.5")[1]
        self.assertEqual(printed.splitlines()[-1], "  1 of 3 text nodes under 4.5:1, 1 with no ink in view")

    def test_the_count_line_says_nothing_of_ink_when_every_node_has_some(self) -> None:
        image = _frame_with_ink((320, 640), (0, 0, 0), (110, 110, 120, 120))
        directory = pathlib.Path(tempfile.mkdtemp())
        (directory / "hierarchy.xml").write_text(_dump({"Strong": (100, 100, 150, 130)}), encoding="utf-8")
        image.save(directory / "frame.png")
        printed = self._run(str(directory / "hierarchy.xml"), str(directory / "frame.png"), "--below", "4.5")[1]
        self.assertEqual(printed.splitlines()[-1], "  0 of 1 text nodes under 4.5:1")

    def test_a_floor_that_is_not_a_number_is_refused_with_its_value(self) -> None:
        status, _ = self._run(*self._files(), "--below", "AA")
        self.assertEqual(status, 2)


def _button_dump() -> str:
    """Two text buttons as the platform dumps them: the merged node is the text's parent and carries `enabled`."""
    return (
        '<hierarchy rotation="0"><node text="" bounds="[0,0][320,640]">'
        '<node text="" enabled="false" bounds="[0,0][160,60]">'
        '<node text="Dead" enabled="true" bounds="[10,10][150,50]" /></node>'
        '<node text="" enabled="true" bounds="[160,0][320,60]">'
        '<node text="Live" enabled="true" bounds="[170,10][310,50]" /></node>'
        '<node text="Own" enabled="false" bounds="[0,100][160,160]" />'
        "</node></hierarchy>"
    )


class DisabledControlTest(unittest.TestCase):
    """A disabled text button states `enabled="false"` on the node that merges it, never on its text."""

    def _readings(self) -> dict[str, bool]:
        image = _frame_with_ink((320, 640), (0, 0, 0), (20, 20, 30, 30))
        return {at.text: at.disabled for at in text_contrasts(_button_dump(), _saved(image))}

    def test_a_text_under_a_disabled_parent_is_disabled(self) -> None:
        self.assertTrue(self._readings()["Dead"])

    def test_a_text_under_an_enabled_parent_is_not(self) -> None:
        self.assertFalse(self._readings()["Live"])

    def test_a_text_node_that_states_enabled_false_itself_is_disabled(self) -> None:
        self.assertTrue(self._readings()["Own"])

    def test_below_names_a_disabled_text_apart_and_counts_it(self) -> None:
        directory = pathlib.Path(tempfile.mkdtemp())
        (directory / "hierarchy.xml").write_text(_button_dump(), encoding="utf-8")
        image = _frame_with_ink((320, 640), JUST_UNDER, (20, 20, 30, 30))
        image.paste(Image.new("RGB", (10, 10), (0, 0, 0)), (200, 20))
        image.save(directory / "frame.png")
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            main(
                [
                    "read_text_contrast.py",
                    str(directory / "hierarchy.xml"),
                    str(directory / "frame.png"),
                    "--below",
                    "4.5",
                ]
            )
        lines = output.getvalue().splitlines()
        self.assertEqual([line for line in lines if "'Dead'" in line], ["  disabled  'Dead'  4.48:1"])
        self.assertEqual(lines[-1], "  0 of 3 text nodes under 4.5:1, 2 disabled")


class RealDisabledButtonsTest(unittest.TestCase):
    """The Running panel on the tablet: `Save All` and both `Shut Down All` are drawn greyed, `Close All` is live."""

    def test_the_greyed_buttons_are_disabled_and_the_live_one_is_not(self) -> None:
        directory = pathlib.Path(__file__).resolve().parent / "testdata" / "disabled-text-buttons"
        found = text_contrasts(
            (directory / "running-panel.xml").read_text(encoding="utf-8"), str(directory / "running-panel.png")
        )
        disabled = sorted(
            at.text for at in found if at.disabled and at.text in ("Save All", "Shut Down All", "Close All")
        )
        self.assertEqual(disabled, ["Save All", "Shut Down All", "Shut Down All"])
        close_all = next(at for at in found if at.text == "Close All")
        self.assertFalse(close_all.disabled)


class StatusBarContrastTest(unittest.TestCase):
    def test_the_band_is_read_across_the_frames_full_width(self) -> None:
        top, bottom = STATUS_BAR_ROWS
        image = _frame_with_ink((320, 640), AA_EDGE, (300, top + 2, 310, bottom - 2))
        reading = status_bar_contrast(image)
        self.assertEqual((reading.ground, reading.ink), ("#FFFFFF", "#767676"))

    def test_ink_below_the_band_is_not_the_status_bars(self) -> None:
        image = _frame_with_ink((320, 640), (0, 0, 0), (0, 300, 320, 400))
        self.assertEqual(status_bar_contrast(image).ratio, 1.0)

    def test_the_band_follows_the_bar_height_the_device_reports(self) -> None:
        # dev36 draws a 24px bar at 160dpi (`statusBars frame=[0,0][320,24]`); the tablet-tuned band
        # reaches row 40, so the app's own top bar would be read as the system's.
        app_bar_ink = _frame_with_ink((320, 640), (0, 0, 0), (16, 30, 120, 38))
        self.assertEqual(status_bar_contrast(app_bar_ink, bar_px=24).ratio, 1.0)

    def test_ink_inside_a_reported_bar_is_the_status_bars(self) -> None:
        in_bar = _frame_with_ink((320, 640), AA_EDGE, (250, 6, 300, 18))
        reading = status_bar_contrast(in_bar, bar_px=24)
        self.assertEqual((reading.ground, reading.ink), ("#FFFFFF", "#767676"))

    def test_a_real_phone_frame_reads_the_ground_and_ink_sampled_by_hand(self) -> None:
        # The dev36 Spaces home in the light theme: 24px bar, white ground, the icons `#666666` (5.74:1).
        insets = read_system_bar_insets((PHONE_TESTDATA / "window.txt").read_text(encoding="utf-8"))
        with Image.open(PHONE_TESTDATA / "spaces-home-light.png") as frame:
            reading = status_bar_contrast(frame, insets.top_px)
        self.assertEqual((insets.top_px, reading.ground, reading.ink), (24, "#FFFFFF", "#666666"))
        self.assertEqual(round(reading.ratio, 2), 5.74)

    def test_the_cli_takes_the_bar_height_from_a_dumpsys_window_text(self) -> None:
        window = pathlib.Path(tempfile.mkdtemp()) / "window.txt"
        window.write_text(DEV36_STATUS_BAR_SOURCE, encoding="utf-8")
        app_bar_ink = _frame_with_ink((320, 640), (0, 0, 0), (16, 30, 120, 38))
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = main(["read_text_contrast.py", "--status-bar", _saved(app_bar_ink), str(window)])
        self.assertEqual(status, 0)
        self.assertIn("contrast 1.00:1", output.getvalue())

    def test_a_dumpsys_text_without_a_status_bar_is_refused_with_its_shape(self) -> None:
        window = pathlib.Path(tempfile.mkdtemp()) / "window.txt"
        window.write_text("Window #0 nothing to read here", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "no statusBars or navigationBars InsetsSource"):
            main(["read_text_contrast.py", "--status-bar", _saved(Image.new("RGB", (320, 640), WHITE)), str(window)])


def _region_cli(frame: pathlib.Path, box: tuple[int, int, int, int]) -> tuple[int, str]:
    output = io.StringIO()
    with contextlib.redirect_stdout(output):
        status = main(["read_text_contrast.py", "--region", str(frame), *(str(edge) for edge in box)])
    return status, output.getvalue()


class RegionCliTest(unittest.TestCase):
    """`--region` reads a border or a focus mark off a frame, as the status bar's icons are read."""

    def test_the_dark_seam_between_the_rail_and_the_panel_reads_1_23(self) -> None:
        # `#2A2B2C` on the side bar's `#191A1B`, sampled by hand.
        status, printed = _region_cli(BORDER_TESTDATA / "dark-seam.png", (8, 0, 32, 20))
        self.assertEqual(status, 0)
        self.assertIn("ground #191A1B  ink #2A2B2C  contrast 1.23:1", printed)

    def test_the_light_seam_reads_1_09(self) -> None:
        _, printed = _region_cli(BORDER_TESTDATA / "light-seam.png", (8, 0, 32, 20))
        self.assertIn("ground #FAFAFD  ink #F0F1F2  contrast 1.09:1", printed)

    def test_the_dark_segmented_controls_outline_reads_3_76(self) -> None:
        _, printed = _region_cli(BORDER_TESTDATA / "dark-outline.png", (8, 0, 26, 30))
        self.assertIn("ground #121314  ink #707070  contrast 3.76:1", printed)

    def test_the_light_segmented_controls_outline_reads_3_64(self) -> None:
        _, printed = _region_cli(BORDER_TESTDATA / "light-outline.png", (8, 0, 26, 30))
        self.assertIn("ground #FFFFFF  ink #868686  contrast 3.64:1", printed)

    def test_a_dark_rail_item_under_keyboard_focus_is_a_wash_of_1_14(self) -> None:
        _, printed = _region_cli(BORDER_TESTDATA / "dark-focus.png", (0, 0, 40, 16))
        self.assertIn("#191A1B", printed)
        self.assertIn("#242526", printed)
        self.assertIn("contrast 1.14:1", printed)

    def test_a_light_rail_item_under_keyboard_focus_is_a_wash_of_1_14(self) -> None:
        _, printed = _region_cli(BORDER_TESTDATA / "light-focus.png", (0, 0, 40, 16))
        self.assertIn("#EBEBED", printed)
        self.assertIn("#FAFAFD", printed)
        self.assertIn("contrast 1.14:1", printed)

    def test_a_box_that_is_not_four_whole_numbers_is_refused_with_its_value(self) -> None:
        frame = _saved(Image.new("RGB", (40, 40), WHITE))
        with self.assertRaisesRegex(ValueError, "'wide'.*four whole pixel numbers"):
            main(["read_text_contrast.py", "--region", frame, "0", "0", "wide", "10"])


if __name__ == "__main__":
    unittest.main()
