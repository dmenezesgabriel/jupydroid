#!/usr/bin/env python3
"""Tests for read_font_scale_fit.py."""

from __future__ import annotations

import contextlib
import io
import pathlib
import tempfile
import unittest

from read_font_scale_fit import (
    Overlap,
    main,
    named_overlaps,
    newly_overlapping,
    newly_short_targets,
)
from read_touch_targets import touch_targets

TESTDATA = pathlib.Path(__file__).parent / "testdata" / "font-scale"

#: Density 160, so one pixel is one dp. A label (named by its text) overlapping a button (named by its
#: description) on one side, a pair where one lies inside the other, an unnamed overlap, and a node of
#: another package overlapping the label.
OVERLAPPING = """<hierarchy>
  <node bounds="[0,0][100,40]" class="android.widget.TextView" package="app" text="Title"/>
  <node bounds="[80,0][160,40]" class="android.view.View" package="app" content-desc="Close"/>
  <node bounds="[200,0][300,40]" class="android.view.View" package="app" content-desc="Outer"/>
  <node bounds="[210,10][250,30]" class="android.widget.TextView" package="app" text="Inner"/>
  <node bounds="[400,0][500,40]" class="android.view.View" package="app"/>
  <node bounds="[450,0][550,40]" class="android.view.View" package="app"/>
  <node bounds="[0,0][50,40]" class="android.view.View" package="com.android.systemui" content-desc="Clock"/>
</hierarchy>"""

#: The same two controls, side by side and then pushed into each other by a larger label.
APART = """<hierarchy>
  <node bounds="[0,0][70,40]" class="android.widget.TextView" package="app" text="Title"/>
  <node bounds="[80,0][160,40]" class="android.view.View" package="app" content-desc="Close"/>
</hierarchy>"""

#: A 48dp-tall tab, and the same tab 40dp tall; a short one that stays short; a 60dp one.
TALL_ROWS = """<hierarchy>
  <node bounds="[0,0][200,48]" class="android.view.View" package="app" clickable="true" content-desc="Tab"/>
  <node bounds="[0,60][200,100]" class="android.view.View" package="app" clickable="true" content-desc="Close"/>
  <node bounds="[0,120][200,180]" class="android.view.View" package="app" clickable="true" content-desc="Row"/>
</hierarchy>"""
SHORT_ROWS = """<hierarchy>
  <node bounds="[0,0][200,40]" class="android.view.View" package="app" clickable="true" content-desc="Tab"/>
  <node bounds="[0,60][200,100]" class="android.view.View" package="app" clickable="true" content-desc="Close"/>
  <node bounds="[0,120][200,180]" class="android.view.View" package="app" clickable="true" content-desc="Row"/>
</hierarchy>"""

#: A list scrolled so its last row shows 10dp of itself: uiautomator clips the row to the scroller's foot
#: and leaves its button's box whole, so the two "overlap" where the scroller cut them. The same pair
#: with the scroller tall enough to show both is a real overlap.
CUT_AT_THE_FOOT = """<hierarchy>
  <node bounds="[0,0][200,100]" class="android.widget.ScrollView" package="app" scrollable="true">
    <node bounds="[0,90][200,100]" class="android.view.View" package="app" content-desc="Open row"/>
    <node bounds="[150,92][198,140]" class="android.view.View" package="app" content-desc="Shut Down row"/>
  </node>
</hierarchy>"""
SHOWN_WHOLE = """<hierarchy>
  <node bounds="[0,0][200,150]" class="android.widget.ScrollView" package="app" scrollable="true">
    <node bounds="[0,90][200,140]" class="android.view.View" package="app" content-desc="Open row"/>
    <node bounds="[150,92][198,148]" class="android.view.View" package="app" content-desc="Shut Down row"/>
  </node>
</hierarchy>"""


class NamedOverlapTest(unittest.TestCase):
    def test_a_pair_the_scroller_cut_at_its_foot_is_not_an_overlap(self) -> None:
        self.assertEqual([], named_overlaps(CUT_AT_THE_FOOT, 160, "app"))

    def test_the_same_pair_shown_whole_is_an_overlap(self) -> None:
        found = named_overlaps(SHOWN_WHOLE, 160, "app")
        self.assertEqual([("Open row", "Shut Down row")], [(at.first, at.second) for at in found])

    def test_a_label_and_a_button_that_share_pixels_overlap_in_part(self) -> None:
        found = named_overlaps(OVERLAPPING, 160, "app")
        self.assertEqual([("Title", "Close")], [(at.first, at.second) for at in found])

    def test_the_overlap_is_an_area_in_dp(self) -> None:
        self.assertEqual(20 * 40, named_overlaps(OVERLAPPING, 160, "app")[0].area)
        self.assertEqual(10 * 20, named_overlaps(OVERLAPPING, 320, "app")[0].area)

    def test_a_node_inside_another_is_nesting_and_not_an_overlap(self) -> None:
        names = {name for at in named_overlaps(OVERLAPPING, 160, "app") for name in (at.first, at.second)}
        self.assertTrue(names.isdisjoint({"Outer", "Inner"}))

    def test_a_node_with_nothing_to_say_is_not_compared(self) -> None:
        self.assertEqual(1, len(named_overlaps(OVERLAPPING, 160, "app")))

    def test_the_systems_own_windows_are_not_the_apps(self) -> None:
        self.assertNotIn("Clock", [at.first for at in named_overlaps(OVERLAPPING, 160, "app")])

    def test_two_controls_that_do_not_touch_do_not_overlap(self) -> None:
        self.assertEqual([], named_overlaps(APART, 160, "app"))


class NewOverlapTest(unittest.TestCase):
    def test_a_pair_that_overlaps_only_at_the_larger_scale_is_new(self) -> None:
        before = named_overlaps(APART, 160, "app")
        after = named_overlaps(OVERLAPPING, 160, "app")
        self.assertEqual([("Title", "Close")], [(at.first, at.second) for at in newly_overlapping(before, after)])

    def test_a_pair_that_already_overlapped_is_not_new(self) -> None:
        both = named_overlaps(OVERLAPPING, 160, "app")
        self.assertEqual([], newly_overlapping(both, both))

    def test_nothing_overlapping_afterwards_is_nothing_new(self) -> None:
        self.assertEqual([], newly_overlapping(named_overlaps(OVERLAPPING, 160, "app"), []))

    def test_an_overlap_is_keyed_by_the_names_it_holds(self) -> None:
        moved = [Overlap("Title", "Close", 5.0)]
        self.assertEqual([], newly_overlapping([Overlap("Title", "Close", 900.0)], moved))


class NewlyShortTargetTest(unittest.TestCase):
    def test_a_target_that_cleared_48dp_and_no_longer_does_is_listed(self) -> None:
        before = touch_targets(TALL_ROWS, 160, "app")
        after = touch_targets(SHORT_ROWS, 160, "app")
        self.assertEqual(["Tab"], [at.name for at in newly_short_targets(before, after)])

    def test_a_target_short_at_both_scales_is_not_new(self) -> None:
        both = touch_targets(SHORT_ROWS, 160, "app")
        self.assertEqual([], newly_short_targets(both, both))

    def test_a_target_the_other_dump_does_not_have_is_not_compared(self) -> None:
        after = touch_targets(SHORT_ROWS, 160, "app")
        self.assertEqual([], newly_short_targets([], after))


class RealFramesTest(unittest.TestCase):
    """The tablet at two panes, the Files panel beside a notebook, at font scale 1.0 and 2.0."""

    def setUp(self) -> None:
        self.before = (TESTDATA / "notebook-and-files-1.0.xml").read_text()
        self.after = (TESTDATA / "notebook-and-files-2.0.xml").read_text()

    def test_the_tab_strips_overlaps_are_there_at_both_scales_and_so_not_new(self) -> None:
        before = named_overlaps(self.before, 240)
        after = named_overlaps(self.after, 240)
        self.assertIn(("Tab: Settings", "Close tab: Settings"), [(at.first, at.second) for at in after])
        self.assertEqual([], newly_overlapping(before, after))

    def test_the_launcher_tab_the_strips_scroller_cuts_is_not_compared(self) -> None:
        after = [(at.first, at.second) for at in named_overlaps(self.after, 240)]
        self.assertNotIn(("Tab: Launcher", "Reload tab: theme-baseline.ipynb"), after)

    def test_no_target_falls_under_48dp_at_two_hundred_percent(self) -> None:
        before = touch_targets(self.before, 240)
        after = touch_targets(self.after, 240)
        self.assertEqual([], newly_short_targets(before, after))


def _directories_holding(root: pathlib.Path, surfaces: list[str]) -> tuple[pathlib.Path, pathlib.Path]:
    """Two directories, one per scale, each holding the real notebook frames under every surface's name."""
    before, after = root / "scale-1.0", root / "scale-2.0"
    for directory, scale in ((before, "1.0"), (after, "2.0")):
        directory.mkdir()
        for surface in surfaces:
            (directory / f"{surface}.xml").write_text((TESTDATA / f"notebook-and-files-{scale}.xml").read_text())
    return before, after


class MainTest(unittest.TestCase):
    def _run(self, argv: list[str]) -> tuple[int, str]:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = main(argv)
        return code, out.getvalue()

    def test_it_reports_each_finding_and_still_exits_zero(self) -> None:
        paths = [str(TESTDATA / f"notebook-and-files-{scale}.xml") for scale in ("1.0", "2.0")]
        code, text = self._run(["read_font_scale_fit.py", *paths, "240"])
        self.assertEqual(0, code)
        self.assertIn("0 newly overlapping", text)
        self.assertIn("0 newly under 48dp", text)

    def test_two_directories_are_read_one_surface_at_a_time(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            before, after = _directories_holding(pathlib.Path(tmp), ["files-panel", "notebook"])
            code, text = self._run(["read_font_scale_fit.py", str(before), str(after), "240"])
        self.assertEqual(0, code)
        self.assertEqual(
            ["-- files-panel", "-- notebook"], [line for line in text.splitlines() if line.startswith("--")]
        )
        self.assertEqual(2, text.count("0 newly overlapping, 0 newly under 48dp"))

    def test_a_surface_captured_at_one_scale_only_is_named(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            before, after = _directories_holding(pathlib.Path(tmp), ["notebook"])
            (before / "terminal.xml").write_text("<hierarchy/>")
            (after / "launcher.xml").write_text("<hierarchy/>")
            _, text = self._run(["read_font_scale_fit.py", str(before), str(after), "240"])
        self.assertIn("-- launcher: captured at the second scale only", text)
        self.assertIn("-- terminal: captured at the first scale only", text)

    def test_a_directory_beside_a_file_is_refused(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            before, after = _directories_holding(pathlib.Path(tmp), ["notebook"])
            with self.assertRaisesRegex(ValueError, "both be directories or both be dumps"):
                self._run(["read_font_scale_fit.py", str(before), str(after / "notebook.xml"), "240"])

    def test_without_arguments_it_prints_its_usage(self) -> None:
        code, text = self._run(["read_font_scale_fit.py"])
        self.assertEqual(2, code)
        self.assertIn("read_font_scale_fit.py", text)


if __name__ == "__main__":
    unittest.main()
