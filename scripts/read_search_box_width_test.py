"""What `read_search_box_width` reads out of a dump - P2x-3d."""
from __future__ import annotations

import unittest

from read_search_box_width import option_toggle_spans, search_box_span, text_room_between

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
        self.assertEqual((136.0, 472.0), search_box_span(DUMP, density=240))

    def test_the_toggles_come_back_left_to_right(self) -> None:
        spans = option_toggle_spans(DUMP, density=240)

        self.assertEqual(["Match case", "Match whole word", "Use regular expression"], [at[0] for at in spans])
        self.assertEqual((328.0, 376.0), spans[0][1:])

    def test_a_flipped_toggle_is_still_a_toggle(self) -> None:
        """A toggle names what a tap will *do*, so "Ignore case" is the on state of the same control."""
        flipped = DUMP.replace('content-desc="Match case"', 'content-desc="Ignore case"')

        self.assertEqual(["Ignore case", "Match whole word", "Use regular expression"],
                         [at[0] for at in option_toggle_spans(flipped, density=240)])

    def test_the_room_left_for_a_query_is_the_box_up_to_the_first_toggle(self) -> None:
        self.assertEqual(192.0, text_room_between(search_box_span(DUMP, density=240),
                                                  option_toggle_spans(DUMP, density=240)))

    def test_a_dump_with_no_panel_says_so_rather_than_reporting_zero(self) -> None:
        with self.assertRaises(LookupError):
            search_box_span("<hierarchy><node bounds='[0,0][10,10]'/></hierarchy>", density=240)


if __name__ == "__main__":
    unittest.main()
