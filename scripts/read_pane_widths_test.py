#!/usr/bin/env python3
"""Tests for read_pane_widths.py."""
from __future__ import annotations

import unittest

from read_pane_widths import _density_from, detail_width_at, shell_columns

#: A shell with an activity bar and two panes, at density 240 - the tablet's own numbers, taken
#: from `evidence/p2o-3-two-pane-threshold/tablet-1280dp/hierarchy.xml`.
TWO_PANES = """<hierarchy>
  <node bounds="[0,0][1920,1200]" class="android.widget.FrameLayout"/>
  <node bounds="[0,0][120,1200]" class="android.view.View"/>
  <node bounds="[122,0][1920,1200]" class="android.view.View"/>
  <node bounds="[122,0][662,1200]" class="android.view.View"/>
  <node bounds="[698,0][1920,1200]" class="android.view.View"/>
  <node bounds="[206,411][497,451]" class="android.widget.TextView" text="Heading Sections.ipynb"/>
</hierarchy>"""

#: The same shell in a window with room for one pane: no bar, no split.
ONE_PANE = """<hierarchy>
  <node bounds="[0,0][480,960]" class="android.widget.FrameLayout"/>
  <node bounds="[0,0][480,960]" class="android.view.View"/>
  <node bounds="[24,100][456,140]" class="android.widget.TextView" text="Workspace"/>
</hierarchy>"""


class ShellColumnsTest(unittest.TestCase):
    def test_the_bar_and_both_panes_are_columns(self) -> None:
        self.assertEqual([(0.0, 80.0), (81.3, 441.3), (465.3, 1280.0)], shell_columns(TWO_PANES, 240))

    def test_a_container_holding_a_column_is_not_itself_one(self) -> None:
        """`[122,1920]` holds both panes and the window holds everything; neither is a column."""
        for left, right in shell_columns(TWO_PANES, 240):
            self.assertNotEqual((81.3, 1280.0), (left, right))
            self.assertNotEqual((0.0, 1280.0), (left, right))

    def test_something_drawn_inside_a_pane_is_not_a_column(self) -> None:
        """The file row is as wide as a control and nothing like as tall as the window."""
        self.assertNotIn((137.3, 331.3), shell_columns(TWO_PANES, 240))

    def test_a_one_pane_window_has_one_column(self) -> None:
        self.assertEqual([(0.0, 480.0)], shell_columns(ONE_PANE, 160))


class DetailWidthTest(unittest.TestCase):
    """Everything left of the document pane is fixed, so the document absorbs the difference."""

    def setUp(self) -> None:
        self.columns = shell_columns(TWO_PANES, 240)

    def test_the_window_it_was_measured_in_gives_back_what_was_measured(self) -> None:
        self.assertEqual(814.7, detail_width_at(1280, self.columns))

    def test_a_narrower_window_takes_it_all_off_the_document(self) -> None:
        self.assertEqual(374.7, detail_width_at(840, self.columns))
        self.assertEqual(334.7, detail_width_at(800, self.columns))

    def test_one_pane_cannot_be_re_split(self) -> None:
        with self.assertRaises(ValueError):
            detail_width_at(800, shell_columns(ONE_PANE, 160))


class DensityTest(unittest.TestCase):
    """The override is the density in force, and reading the first line is the P2l-4 defect."""

    def test_the_physical_density_is_read_when_nothing_overrides_it(self) -> None:
        self.assertEqual(240, _density_from("Physical density: 240\n"))

    def test_an_override_wins_over_the_physical_density(self) -> None:
        self.assertEqual(420, _density_from("Physical density: 160\nOverride density: 420\n"))

    def test_output_with_no_density_says_what_it_wanted(self) -> None:
        with self.assertRaises(ValueError):
            _density_from("no such command")


if __name__ == "__main__":
    unittest.main()
