"""What `read_search_box_width` reads out of a dump - the query box and the replace box."""

from __future__ import annotations

import unittest

from read_search_box_width import (
    REPLACE_BOX_LABEL,
    REPLACE_TOGGLE_LABELS,
    box_span,
    text_room_between,
    toggle_spans,
)

#: A 1200px-wide list pane at density 240 (1.5x): the box inset 16dp, three 48dp toggles at its end.
DUMP = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy>
  <node bounds="[0,0][1920,1200]">
    <node content-desc="Search the workspace" bounds="[204,120][708,204]"/>
    <node content-desc="Match case" bounds="[492,126][564,198]"/>
    <node content-desc="Match whole word" bounds="[564,126][636,198]"/>
    <node content-desc="Use regular expression" bounds="[636,126][708,198]"/>
  </node>
</hierarchy>
"""


class ReadSearchBoxWidthTest(unittest.TestCase):
    def test_the_box_is_found_by_the_name_it_answers_to(self) -> None:
        self.assertEqual((136.0, 472.0), box_span(DUMP, density=240))

    def test_the_toggles_come_back_left_to_right(self) -> None:
        spans = toggle_spans(DUMP, density=240)

        self.assertEqual(["Match case", "Match whole word", "Use regular expression"], [at[0] for at in spans])
        self.assertEqual((328.0, 376.0), spans[0][1:])

    def test_a_flipped_toggle_is_still_a_toggle(self) -> None:
        """A toggle names what a tap will *do*, so "Ignore case" is the on state of the same control."""
        flipped = DUMP.replace('content-desc="Match case"', 'content-desc="Ignore case"')

        self.assertEqual(
            ["Ignore case", "Match whole word", "Use regular expression"],
            [at[0] for at in toggle_spans(flipped, density=240)],
        )

    def test_the_room_left_for_a_query_is_the_box_up_to_the_first_toggle(self) -> None:
        self.assertEqual(192.0, text_room_between(box_span(DUMP, density=240), toggle_spans(DUMP, density=240)))

    def test_a_dump_with_no_panel_says_so_rather_than_reporting_zero(self) -> None:
        with self.assertRaises(LookupError):
            box_span("<hierarchy><node bounds='[0,0][10,10]'/></hierarchy>", density=240)


class ReadReplaceBoxWidthTest(unittest.TestCase):
    """The replace box, whose row starts with a chevron and ends with one toggle."""

    #: The same pane with the replace row open: a 48dp chevron leading, then the box to the edge.
    DUMP = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy>
  <node bounds="[0,0][1920,1200]">
    <node content-desc="Search the workspace" bounds="[204,120][708,204]"/>
    <node content-desc="Hide replace" bounds="[204,222][276,294]"/>
    <node content-desc="Replace across the workspace" bounds="[276,210][708,306]"/>
    <node content-desc="Preserve case" bounds="[636,222][708,294]"/>
  </node>
</hierarchy>
"""

    def test_the_replace_box_is_found_by_its_own_name(self) -> None:
        self.assertEqual((184.0, 472.0), box_span(self.DUMP, density=240, label=REPLACE_BOX_LABEL))

    def test_the_replace_row_carries_one_toggle_and_it_flips_its_name(self) -> None:
        flipped = self.DUMP.replace('content-desc="Preserve case"', 'content-desc="Replace verbatim"')

        self.assertEqual(
            ["Replace verbatim"], [at[0] for at in toggle_spans(flipped, density=240, labels=REPLACE_TOGGLE_LABELS)]
        )

    def test_the_room_left_for_a_replacement_stops_at_its_toggle(self) -> None:
        box = box_span(self.DUMP, density=240, label=REPLACE_BOX_LABEL)
        toggles = toggle_spans(self.DUMP, density=240, labels=REPLACE_TOGGLE_LABELS)

        self.assertEqual(240.0, text_room_between(box, toggles))

    def test_a_collapsed_row_is_named_as_such_rather_than_measured_as_zero(self) -> None:
        """The row is behind a chevron, so "not there" is a state this can be pointed at."""
        closed = self.DUMP.replace('content-desc="Replace across the workspace"', 'content-desc="something else"')

        with self.assertRaises(LookupError):
            box_span(closed, density=240, label=REPLACE_BOX_LABEL)


if __name__ == "__main__":
    unittest.main()
