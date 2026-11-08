#!/usr/bin/env python3
"""Tests for read_touch_targets.py."""

from __future__ import annotations

import contextlib
import io
import pathlib
import unittest

from read_touch_targets import (
    TouchTarget,
    cut_by_scroller,
    main,
    touch_targets,
    under_minimum,
    unnamed,
)

TESTDATA = pathlib.Path(__file__).parent / "testdata" / "layout-spacing"

#: Density 160, so one pixel is one dp. A 40x48 clickable (short), a 48x48 one, a clickable row named
#: by its label, a clickable with nothing to say, a text field, and a window of another package. A scrolling
#: column holds a narrow control at its side, one cut by its bottom edge and a full row;
#: a scrolling strip holds a tab taller than the strip is and one lying beyond its end.
SCREEN = """<hierarchy>
  <node bounds="[0,0][1000,800]" class="android.widget.FrameLayout" package="app"/>
  <node bounds="[0,0][40,48]" class="android.view.View" package="app" clickable="true" content-desc="Short"/>
  <node bounds="[0,60][48,108]" class="android.view.View" package="app" clickable="true" content-desc="Square"/>
  <node bounds="[100,0][500,48]" class="android.view.View" package="app" clickable="true">
    <node bounds="[110,10][200,40]" class="android.widget.TextView" package="app" text="a row"/>
  </node>
  <node bounds="[100,60][148,108]" class="android.view.View" package="app" clickable="true"/>
  <node bounds="[200,60][400,108]" class="android.widget.EditText" package="app" text="typed"/>
  <node bounds="[200,120][400,168]" class="android.widget.EditText" package="app" hint="Search"/>
  <node bounds="[0,700][1000,748]" class="android.view.View" package="com.android.systemui" clickable="true"/>
  <node bounds="[500,200][900,300]" class="android.widget.ScrollView" package="app" scrollable="true">
    <node bounds="[500,210][540,250]" class="android.view.View" package="app" clickable="true" content-desc="Narrow"/>
    <node bounds="[500,280][900,300]" class="android.view.View" package="app" clickable="true" content-desc="Edge"/>
    <node bounds="[500,230][900,278]" class="android.view.View" package="app" clickable="true" content-desc="Row"/>
  </node>
  <node bounds="[500,320][900,360]" class="android.widget.HorizontalScrollView" package="app" scrollable="true">
    <node bounds="[500,310][700,350]" class="android.view.View" package="app" clickable="true"
          content-desc="Strip tab"/>
    <node bounds="[950,325][952,331]" class="android.view.View" package="app" clickable="true" content-desc="Beyond"/>
  </node>
</hierarchy>"""

#: A tab (clickable) holding a label and its own close button (clickable, named).
TAB_WITH_CLOSE = """<hierarchy>
  <node bounds="[0,0][200,48]" class="android.view.View" package="app" clickable="true">
    <node bounds="[200,0][248,48]" class="android.view.View" package="app" clickable="true"
          content-desc="Close tab"/>
  </node>
</hierarchy>"""


def _targets() -> list[TouchTarget]:
    return touch_targets(SCREEN, 160, "app")


class ReadingTest(unittest.TestCase):
    def test_only_the_apps_own_actionable_nodes_are_targets(self) -> None:
        self.assertEqual(11, len(_targets()))

    def test_size_is_the_layout_bound_in_dp_at_the_density_in_force(self) -> None:
        self.assertEqual((20.0, 24.0), touch_targets(SCREEN, 320, "app")[0].size)

    def test_a_text_field_is_a_target_though_it_is_not_clickable(self) -> None:
        self.assertEqual(["EditText", "EditText"], [at.kind for at in _targets()[4:6]])


class MinimumTest(unittest.TestCase):
    def test_a_node_short_of_48_in_either_direction_is_under(self) -> None:
        self.assertEqual(["Short", "Narrow", "Strip tab"], [at.name for at in under_minimum(_targets())])

    def test_a_node_cut_by_its_scroller_is_listed_apart_and_not_called_short(self) -> None:
        self.assertEqual(["Edge", "Beyond"], [at.name for at in cut_by_scroller(_targets())])

    def test_a_control_as_tall_as_a_horizontal_strip_is_not_cut_by_it(self) -> None:
        self.assertNotIn("Strip tab", [at.name for at in cut_by_scroller(_targets())])

    def test_a_full_width_row_touching_its_scrollers_sides_is_not_cut(self) -> None:
        self.assertNotIn("Row", [at.name for at in cut_by_scroller(_targets())])

    def test_exactly_48_is_not_under(self) -> None:
        self.assertNotIn("Square", [at.name for at in under_minimum(_targets())])


class NameTest(unittest.TestCase):
    def test_a_node_with_a_description_is_named_by_it(self) -> None:
        self.assertEqual("Short", _targets()[0].name)

    def test_a_row_without_a_description_is_named_by_its_label(self) -> None:
        self.assertEqual("a row", _targets()[2].name)

    def test_a_node_with_nothing_beneath_it_is_unnamed(self) -> None:
        self.assertEqual([(100.0, 60.0), (200.0, 60.0)], [(at.box.left, at.box.top) for at in unnamed(_targets())])

    def test_a_text_fields_value_is_not_its_name(self) -> None:
        typed, hinted = _targets()[4:6]
        self.assertEqual(("", "Search"), (typed.name, hinted.name))

    def test_a_nested_control_does_not_name_the_control_around_it(self) -> None:
        tab, close = touch_targets(TAB_WITH_CLOSE, 160, "app")
        self.assertEqual(("", "Close tab"), (tab.name, close.name))


class DumpTest(unittest.TestCase):
    def test_the_tablets_two_pane_files_panel_has_43_targets_11_under_48dp_2_cut_and_all_named(self) -> None:
        found = touch_targets((TESTDATA / "files-panel-two-panes.xml").read_text(), 240, "com.jupydroid.app")
        self.assertEqual(
            (43, 11, 0, 2), (len(found), len(under_minimum(found)), len(unnamed(found)), len(cut_by_scroller(found)))
        )

    def test_main_prints_one_line_of_counts_per_dump(self) -> None:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = main(["read_touch_targets.py", str(TESTDATA / "delete-dialog-popup.xml"), "240"])
        self.assertEqual(0, code)
        self.assertIn("targets", out.getvalue())


if __name__ == "__main__":
    unittest.main()
