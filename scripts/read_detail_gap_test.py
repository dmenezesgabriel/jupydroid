#!/usr/bin/env python3
"""Tests for read_detail_gap.py."""

from __future__ import annotations

import unittest

from read_detail_gap import Rect, described_line, node_rects

#: The tablet's landscape state while the card is up, cut down from a real dump: the popup's
#: window is rooted at the card's own bounds and holds nothing of the app behind it.
CARD_OPEN = """<hierarchy rotation="1">
  <node content-desc="Diagnostic detail" bounds="[810,494][1230,599]">
    <node text="error: undefined name 'never_defined_lsp_name'" bounds="[860,506][1212,560]"/>
    <node text="pyflakes" bounds="[860,560][935,587]"/>
  </node>
</hierarchy>"""

#: The same screen with the card dismissed: each cell's EditText wears the padding, and the
#: "Code cell" node inside it is the 31px line run itself.
CARD_CLOSED = """<hierarchy rotation="1">
  <node text="x = 1 + 1" class="android.widget.EditText" bounds="[883,274][1884,346]">
    <node content-desc="Code cell" bounds="[883,294][1884,325]"/>
  </node>
  <node text="print(never_defined_lsp_name)" class="android.widget.EditText" bounds="[883,443][1884,515]">
    <node content-desc="Code cell" bounds="[883,463][1884,494]"/>
  </node>
  <node text="y=2" class="android.widget.EditText" bounds="[883,612][1884,684]">
    <node content-desc="Code cell" bounds="[883,632][1884,663]"/>
  </node>
</hierarchy>"""

NO_CARD = '<hierarchy><node content-desc="Code cell" bounds="[883,294][1884,325]"/></hierarchy>'


class NodeRectsTest(unittest.TestCase):
    def test_reads_every_node_with_the_desc(self) -> None:
        self.assertEqual(
            [
                Rect(883, 294, 1884, 325),
                Rect(883, 463, 1884, 494),
                Rect(883, 632, 1884, 663),
            ],
            node_rects(CARD_CLOSED, "Code cell"),
        )

    def test_other_descs_and_unnamed_nodes_do_not_match(self) -> None:
        self.assertEqual([], node_rects(CARD_CLOSED, "Diagnostic detail"))
        self.assertEqual([], node_rects(NO_CARD, "Files"))


class DescribedLineTest(unittest.TestCase):
    def test_the_card_below_picks_the_line_just_above_it(self) -> None:
        lines = node_rects(CARD_CLOSED, "Code cell")
        line, gap = described_line(Rect(810, 494, 1230, 599), lines)
        self.assertEqual(Rect(883, 463, 1884, 494), line)
        self.assertEqual(0, gap)

    def test_the_pre_fix_card_reads_its_overlap_as_a_negative_gap(self) -> None:
        line, gap = described_line(Rect(810, 482, 1230, 587), node_rects(CARD_CLOSED, "Code cell"))
        self.assertEqual(Rect(883, 463, 1884, 494), line)
        self.assertEqual(-12, gap)

    def test_a_card_flipped_above_measures_to_the_line_below_it(self) -> None:
        line, gap = described_line(Rect(810, 300, 1230, 460), node_rects(CARD_CLOSED, "Code cell"))
        self.assertEqual(Rect(883, 463, 1884, 494), line)
        self.assertEqual(3, gap)


if __name__ == "__main__":
    unittest.main()
