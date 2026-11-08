#!/usr/bin/env python3
"""Tests for read_font_scale_fit.py."""

from __future__ import annotations

import contextlib
import io
import pathlib
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


class NamedOverlapTest(unittest.TestCase):
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
        self.assertIn(("Tab: Launcher", "Reload tab: theme-baseline.ipynb"), [(at.first, at.second) for at in after])
        self.assertEqual([], newly_overlapping(before, after))

    def test_no_target_falls_under_48dp_at_two_hundred_percent(self) -> None:
        before = touch_targets(self.before, 240)
        after = touch_targets(self.after, 240)
        self.assertEqual([], newly_short_targets(before, after))


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

    def test_without_arguments_it_prints_its_usage(self) -> None:
        code, text = self._run(["read_font_scale_fit.py"])
        self.assertEqual(2, code)
        self.assertIn("read_font_scale_fit.py", text)


if __name__ == "__main__":
    unittest.main()
