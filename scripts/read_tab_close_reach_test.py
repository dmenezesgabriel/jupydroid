#!/usr/bin/env python3
"""Tests for read_tab_close_reach.py."""

from __future__ import annotations

import unittest

from read_tab_close_reach import TabReach, tab_reaches

#: A strip at density 160 (1px = 1dp), the way `uiautomator dump` reports it: the scroller holds the
#: tabs from x=48, `cut.py` is scrolled half under its left edge so its bounds are only the visible
#: part, and each close's node is the 48dp press area (ADR-0142). `a.py` is the 80dp floor GAP-210
#: read, `b.py` the 96dp floor that replaced it.
DUMP = """<hierarchy>
  <node bounds="[0,0][360,640]" class="android.widget.FrameLayout"/>
  <node bounds="[48,24][320,58]" class="android.widget.HorizontalScrollView" scrollable="true"/>
  <node content-desc="Tab: cut.py" bounds="[48,26][96,58]" class="android.view.View"/>
  <node content-desc="Close tab: cut.py" bounds="[56,18][96,66]" class="android.view.View"/>
  <node content-desc="Tab: a.py" bounds="[96,26][176,58]" class="android.view.View"/>
  <node content-desc="Close tab: a.py" bounds="[136,18][176,66]" class="android.view.View"/>
  <node content-desc="Tab: b.py" bounds="[176,26][272,58]" class="android.view.View"/>
  <node content-desc="Close tab: b.py" bounds="[232,18][280,66]" class="android.view.View"/>
</hierarchy>"""


class TabReachesTest(unittest.TestCase):
    def test_each_whole_tab_reports_its_width_reach_and_middle_in_dp_from_its_own_left_edge(self) -> None:
        self.assertEqual(
            [TabReach("a.py", 80.0, 40.0, 40.0), TabReach("b.py", 96.0, 56.0, 48.0)],
            tab_reaches(DUMP, 160),
        )

    def test_a_tab_the_scroller_cuts_is_left_out_because_its_bounds_are_only_what_shows(self) -> None:
        self.assertNotIn("cut.py", [tab.title for tab in tab_reaches(DUMP, 160)])

    def test_a_tab_scrolled_out_of_sight_has_no_area_and_is_not_read(self) -> None:
        """At 360dp the dump still lists a fully scrolled-out tab, with bounds `[0,0][0,0]`."""
        unseen = DUMP.replace(
            '<node content-desc="Tab: cut.py"',
            '<node content-desc="Tab: gone.py" bounds="[0,0][0,0]"/>\n  <node content-desc="Tab: cut.py"',
        )
        self.assertEqual(["a.py", "b.py"], [tab.title for tab in tab_reaches(unseen, 160)])

    def test_a_middle_on_the_boundary_of_the_reach_is_the_closes(self) -> None:
        floor_80, _ = tab_reaches(DUMP, 160)
        self.assertFalse(floor_80.middle_is_clear)

    def test_a_middle_short_of_the_reach_is_the_tabs(self) -> None:
        _, floor_96 = tab_reaches(DUMP, 160)
        self.assertTrue(floor_96.middle_is_clear)
        self.assertEqual(8.0, floor_96.margin_dp)

    def test_pixels_are_read_as_dp_at_the_density_in_force(self) -> None:
        at_240 = """<hierarchy>
          <node bounds="[0,0][480,40]" class="android.widget.HorizontalScrollView" scrollable="true"/>
          <node content-desc="Tab: a.py" bounds="[30,0][150,40]"/>
          <node content-desc="Close tab: a.py" bounds="[90,0][150,40]"/>
        </hierarchy>"""
        self.assertEqual([TabReach("a.py", 80.0, 40.0, 40.0)], tab_reaches(at_240, 240))

    def test_a_tab_whose_close_the_dump_leaves_out_is_not_read(self) -> None:
        """Half scrolled off the strip's right end, a tab is listed but its close is not."""
        right_cut = DUMP.replace(
            '  <node content-desc="Close tab: b.py" bounds="[232,18][280,66]" class="android.view.View"/>\n', ""
        )
        self.assertEqual(["a.py"], [tab.title for tab in tab_reaches(right_cut, 160)])

    def test_a_strip_where_no_tab_shows_its_close_names_the_missing_closes_in_the_error(self) -> None:
        lone = """<hierarchy>
          <node bounds="[0,0][320,40]" class="android.widget.HorizontalScrollView" scrollable="true"/>
          <node content-desc="Tab: a.py" bounds="[10,0][90,40]"/>
        </hierarchy>"""
        with self.assertRaisesRegex(LookupError, "Close tab: a.py"):
            tab_reaches(lone, 160)

    def test_a_dump_with_no_tab_is_an_error_not_an_empty_pass(self) -> None:
        with self.assertRaisesRegex(LookupError, "no 'Tab: ' node"):
            tab_reaches("<hierarchy><node bounds='[0,0][1,1]'/></hierarchy>", 160)

    def test_a_strip_with_no_whole_tab_is_an_error_not_an_empty_pass(self) -> None:
        only_cut = """<hierarchy>
          <node bounds="[48,24][320,58]" class="android.widget.HorizontalScrollView" scrollable="true"/>
          <node content-desc="Tab: cut.py" bounds="[48,26][96,58]"/>
          <node content-desc="Close tab: cut.py" bounds="[56,18][96,66]"/>
        </hierarchy>"""
        with self.assertRaisesRegex(LookupError, "no whole tab"):
            tab_reaches(only_cut, 160)


if __name__ == "__main__":
    unittest.main()
