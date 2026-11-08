#!/usr/bin/env python3
"""Tests for read_layout_spacing.py."""

from __future__ import annotations

import contextlib
import io
import pathlib
import unittest

from read_layout_spacing import (
    SPACING_SOURCE,
    ColumnLayout,
    blank_bands,
    column_layouts,
    layout_differences,
    load_spacing_scale,
    main,
    row_gaps,
    spacing_step,
)

TESTDATA = pathlib.Path(__file__).parent / "testdata" / "layout-spacing"

#: A shell at density 160 (one pixel is one dp) so every number below is the dp it states: a rail, a
#: list pane and a 1000dp-wide system navigation bar across the foot. The list pane has a 64dp header
#: carrying a title and a trailing icon, then two 48dp rows 12dp apart, and nothing below them.
SHELL = """<hierarchy>
  <node bounds="[0,0][1000,800]" class="android.widget.FrameLayout"/>
  <node bounds="[0,0][100,800]" class="android.view.View"/>
  <node bounds="[0,30][100,86]" class="android.view.View" content-desc="Files" clickable="true"/>
  <node bounds="[0,90][100,146]" class="android.view.View" content-desc="Search" clickable="true"/>
  <node bounds="[100,0][500,800]" class="android.view.View"/>
  <node bounds="[100,0][500,94]" class="android.view.View"/>
  <node bounds="[100,30][500,94]" class="android.view.View"/>
  <node bounds="[116,40][180,84]" class="android.widget.TextView" text="Files"/>
  <node bounds="[460,46][484,70]" class="android.view.View" content-desc="New"/>
  <node bounds="[100,100][500,148]" class="android.view.View" clickable="true"/>
  <node bounds="[116,110][220,138]" class="android.widget.TextView" text="first"/>
  <node bounds="[100,160][500,208]" class="android.view.View" clickable="true"/>
  <node bounds="[116,170][220,198]" class="android.widget.TextView" text="second"/>
  <node bounds="[0,752][1000,800]" class="android.view.View"/>
</hierarchy>"""


#: One clickable row and its label, which runs nearly the whole width of it.
WIDE_LABEL = """<hierarchy>
  <node bounds="[0,0][400,800]" class="android.view.View"/>
  <node bounds="[0,100][400,148]" class="android.view.View" clickable="true"/>
  <node bounds="[16,110][390,138]" class="android.widget.TextView" text="a long label"/>
</hierarchy>"""


def _list_pane() -> ColumnLayout:
    return column_layouts(SHELL, 160)[1]


class RowsTest(unittest.TestCase):
    def test_the_rail_and_the_list_pane_are_the_columns(self) -> None:
        self.assertEqual([(0.0, 100.0), (100.0, 500.0)], [(c.left, c.right) for c in column_layouts(SHELL, 160)])

    def test_the_innermost_of_two_nested_headers_is_the_row(self) -> None:
        """`[80,0][480,94]` holds the 30dp status bar above the header; the header is the 64dp one."""
        self.assertEqual([64.0, 48.0, 48.0], [row.box.height for row in _list_pane().rows])

    def test_the_inset_is_to_the_first_and_last_thing_drawn_in_the_row(self) -> None:
        header = _list_pane().rows[0]
        self.assertEqual((16.0, 16.0), (header.left_inset, header.right_inset))

    def test_a_row_with_no_control_on_its_right_states_no_right_inset(self) -> None:
        self.assertEqual((16.0, None), (_list_pane().rows[1].left_inset, _list_pane().rows[1].right_inset))

    def test_text_that_fills_a_row_is_not_a_row_of_its_own(self) -> None:
        """The label is 374dp of a 400dp row; the row is the 48dp touch target around it."""
        rows = column_layouts(WIDE_LABEL, 160)[0].rows
        self.assertEqual([48.0], [row.box.height for row in rows])
        self.assertEqual((16.0, None), (rows[0].left_inset, rows[0].right_inset))

    def test_a_row_with_nothing_inside_it_states_no_inset(self) -> None:
        """A rail item is one node; its own bounds are the only thing there is to measure."""
        rail = column_layouts(SHELL, 160)[0].rows
        self.assertEqual([56.0, 56.0], [row.box.height for row in rail])
        self.assertEqual((None, None), (rail[0].left_inset, rail[0].right_inset))

    def test_a_bar_across_the_whole_window_is_not_a_row_of_any_column(self) -> None:
        for column in column_layouts(SHELL, 160):
            self.assertNotIn(752.0, [row.box.top for row in column.rows])


class GapsTest(unittest.TestCase):
    def test_a_gap_is_the_space_between_one_row_and_the_next(self) -> None:
        self.assertEqual([6.0, 12.0], row_gaps(_list_pane().rows))

    def test_the_rail_items_are_four_dp_apart(self) -> None:
        self.assertEqual([4.0], row_gaps(column_layouts(SHELL, 160)[0].rows))

    def test_one_row_has_no_gap(self) -> None:
        self.assertEqual([], row_gaps(_list_pane().rows[:1]))


class BlankBandsTest(unittest.TestCase):
    def test_what_is_left_below_the_last_row_is_a_band_up_to_the_bar_across_the_foot(self) -> None:
        self.assertEqual([(208.0, 752.0)], blank_bands(_list_pane()))

    def test_the_space_between_two_rows_is_a_band_when_it_is_tall_enough_to_be_one(self) -> None:
        self.assertNotIn((148.0, 160.0), blank_bands(_list_pane()))

    def test_the_rail_leaves_the_rest_of_its_height_blank(self) -> None:
        self.assertEqual([(146.0, 752.0)], blank_bands(column_layouts(SHELL, 160)[0]))


class SpacingStepTest(unittest.TestCase):
    SCALE = {"xs": 4.0, "sm": 8.0, "md": 12.0, "lg": 16.0, "xl": 24.0, "xxl": 32.0}

    def test_a_value_on_the_scale_has_the_steps_name(self) -> None:
        self.assertEqual("lg", spacing_step(16.0, self.SCALE))

    def test_a_pixel_of_rounding_is_still_the_step(self) -> None:
        """At 1.5 px per dp a 12dp inset is 18px and a 16dp one 24px; a 4dp one is 6px exactly."""
        self.assertEqual("md", spacing_step(11.3, self.SCALE))
        self.assertEqual("xs", spacing_step(4.7, self.SCALE))

    def test_a_value_between_steps_is_off_the_scale(self) -> None:
        self.assertEqual("off-scale", spacing_step(20.0, self.SCALE))

    def test_a_divider_is_a_hairline_not_an_off_scale_gap(self) -> None:
        """A 1dp divider is two pixels at 240 dpi, which is 1.33dp."""
        self.assertEqual("hairline", spacing_step(1.3, self.SCALE))

    def test_zero_is_not_a_gap(self) -> None:
        self.assertEqual("none", spacing_step(0.0, self.SCALE))


class LayoutDifferencesTest(unittest.TestCase):
    MOVED = SHELL.replace("[100,160][500,208]", "[100,164][500,212]").replace(
        "[116,170][220,198]", "[116,174][220,202]"
    )
    TEXT_CHANGED = SHELL.replace('text="second"', 'text="a different word"')

    def test_the_same_geometry_with_other_words_is_the_same_layout(self) -> None:
        self.assertEqual([], layout_differences(column_layouts(SHELL, 160), column_layouts(self.TEXT_CHANGED, 160)))

    def test_a_row_that_moved_is_named_in_the_column_it_moved_in(self) -> None:
        found = layout_differences(column_layouts(SHELL, 160), column_layouts(self.MOVED, 160))
        self.assertEqual(2, len(found))
        self.assertTrue(all(line.startswith("column 1: ") for line in found), found)

    def test_several_moved_rows_are_all_named(self) -> None:
        both_moved = self.MOVED.replace("[100,100][500,148]", "[100,104][500,152]")
        found = layout_differences(column_layouts(SHELL, 160), column_layouts(both_moved, 160))
        self.assertEqual(4, len(found))

    def test_a_different_number_of_columns_is_a_difference(self) -> None:
        one_pane = column_layouts(WIDE_LABEL, 160)
        self.assertEqual(["2 columns against 1"], layout_differences(column_layouts(SHELL, 160), one_pane))


class SpacingScaleTest(unittest.TestCase):
    def test_the_scale_is_read_from_the_file_that_defines_it(self) -> None:
        scale = load_spacing_scale(pathlib.Path(__file__).parent.parent / SPACING_SOURCE)
        self.assertEqual({"xs": 4.0, "sm": 8.0, "md": 12.0, "lg": 16.0, "xl": 24.0, "xxl": 32.0}, scale)

    def test_a_missing_file_says_where_it_looked(self) -> None:
        with self.assertRaisesRegex(FileNotFoundError, "nowhere/Spacing.kt"):
            load_spacing_scale(pathlib.Path("nowhere/Spacing.kt"))


class RealTabletFrameTest(unittest.TestCase):
    """The tablet's Files panel at two panes, 240 dpi: the numbers the audit reads."""

    def setUp(self) -> None:
        text = (TESTDATA / "files-panel-two-panes.xml").read_text()
        self.columns = column_layouts(text, 240)

    def test_the_rail_is_80dp_wide_with_56dp_items_four_dp_apart(self) -> None:
        rail = self.columns[0]
        self.assertEqual(80.0, rail.right - rail.left)
        self.assertEqual({56.0}, {row.box.height for row in rail.rows})
        self.assertEqual({4.0}, set(row_gaps(rail.rows[:8])))

    def test_the_list_pane_header_is_64dp_with_a_16dp_margin(self) -> None:
        header = self.columns[1].rows[0]
        self.assertEqual((64.0, 16.0), (header.box.height, header.left_inset))


class RealPhoneFrameTest(unittest.TestCase):
    """A notebook on a 320dp phone at 160 dpi, one pixel to the dp: the one-pane numbers the audit reads."""

    def setUp(self) -> None:
        self.columns = column_layouts((TESTDATA / "notebook-phone.xml").read_text(), 160)

    def test_the_window_is_one_320dp_column(self) -> None:
        self.assertEqual([(0.0, 320.0)], [(column.left, column.right) for column in self.columns])

    def test_the_top_bar_is_48dp_directly_under_the_24dp_status_bar(self) -> None:
        top_bar = self.columns[0].rows[0]
        self.assertEqual((24.0, 48.0, 12.0), (top_bar.box.top, top_bar.box.height, top_bar.left_inset))

    def test_the_document_starts_24dp_under_the_top_bar(self) -> None:
        column = self.columns[0]
        self.assertEqual([24.0], row_gaps(column.rows)[:1])
        self.assertEqual((72.0, 96.0), blank_bands(column)[0])


class PopupSurfaceTest(unittest.TestCase):
    """A dump with no node as tall as the window is one popup: a dialog's window, or an overlay's.

    The Delete confirm at 240 dpi: a 431dp dialog holding a title, a sentence and two buttons, each
    measured here as a band of the dialog's height with a 24dp margin on every side.
    """

    def setUp(self) -> None:
        self.dialog = column_layouts((TESTDATA / "delete-dialog-popup.xml").read_text(), 240)

    def test_the_dialog_is_the_one_column(self) -> None:
        self.assertEqual([(184.0, 615.3)], [(column.left, column.right) for column in self.dialog])

    def test_its_rows_are_the_title_the_sentence_and_the_row_of_buttons(self) -> None:
        self.assertEqual([33.3, 22.7, 48.0], [row.box.height for row in self.dialog[0].rows])

    def test_the_gaps_are_16dp_under_the_title_and_24dp_above_the_buttons(self) -> None:
        self.assertEqual([16.0, 24.0], row_gaps(self.dialog[0].rows))

    def test_text_is_24dp_from_the_dialogs_left_edge(self) -> None:
        self.assertEqual([24.0, 24.0], [row.left_inset for row in self.dialog[0].rows[:2]])

    def test_the_buttons_touch_boxes_end_24dp_from_the_right_edge(self) -> None:
        self.assertEqual(24.0, self.dialog[0].rows[2].right_inset)

    def test_the_margins_to_the_first_and_last_row_are_24dp(self) -> None:
        self.assertEqual((24.0, 24.0), self.dialog[0].edge_margins)

    def test_only_the_24dp_above_the_buttons_is_a_blank_band_not_the_padding_below_them(self) -> None:
        bands = [(round(top, 1), round(bottom, 1)) for top, bottom in blank_bands(self.dialog[0])]
        self.assertEqual([(630.7, 654.7)], bands)

    def test_an_overlay_is_read_the_same_way(self) -> None:
        overlay = column_layouts((TESTDATA / "toc-overlay-popup.xml").read_text(), 240)
        self.assertEqual([(555.3, 796.0)], [(overlay[0].left, overlay[0].right)])
        self.assertEqual((24.0, 24.0), overlay[0].edge_margins)

    def test_a_shell_column_states_no_margins(self) -> None:
        self.assertIsNone(_list_pane().edge_margins)


class ColumnWithoutRowsTest(unittest.TestCase):
    def test_a_scrim_with_nothing_in_it_is_reported_not_raised(self) -> None:
        """More's rail leaves a 220dp scrim beside it that holds only a 'Close rail' target."""
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = main(["read_layout_spacing.py", str(TESTDATA / "more-rail-one-pane.xml"), "240"])
        self.assertEqual(0, code)
        self.assertIn("column 220.0..800.0dp  width 580.0dp", out.getvalue())


class DrawerItemsTest(unittest.TestCase):
    def test_items_that_wrap_their_labels_are_rows_by_their_bands(self) -> None:
        """More's rail: four items 176 to 283 px wide in a 330 px column, so none is 90% of it."""
        rail = column_layouts((TESTDATA / "more-rail-one-pane.xml").read_text(), 240)[0]
        self.assertEqual([56.0, 56.0, 56.0, 56.0], [row.box.height for row in rail.rows])
        self.assertEqual([0.0, 0.0], row_gaps(rail.rows)[:2])


if __name__ == "__main__":
    unittest.main()
