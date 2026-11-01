#!/usr/bin/env python3
"""Tests for read_gutter_x.py."""

from __future__ import annotations

import unittest

from read_gutter_x import effective_density, leading_marks, report

#: One `uiautomator dump` from the build *before* P2n, trimmed to the three headers it is about:
#: this app's invented `markdown` label, pushed 48px right on the heading cell by the collapser
#: sitting in front of it, and a code prompt with no colon.
BEFORE = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node index="0" text="" bounds="[0,0][320,640]">
    <node index="0" text="markdown" bounds="[16,100][80,118]" />
    <node index="1" text="Notes before any heading." bounds="[16,130][300,148]" />
    <node index="2" content-desc="Collapse heading" bounds="[16,192][64,234]" />
    <node index="3" text="markdown" bounds="[64,200][128,218]" />
    <node index="4" text="[ ]" bounds="[16,300][40,318]" />
  </node>
</hierarchy>
"""
#: The same frame after it: no kind labels at all, the collapser leading its own header, and the
#: prompt carrying the reference's colon. Bounds are the real ones from
#: `evidence/p2n-gutter/after/hierarchy.xml`.
AFTER = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node index="0" text="" bounds="[0,0][320,640]">
    <node index="0" text="Notes before any heading." bounds="[16,148][185,168]" />
    <node index="1" content-desc="Collapse heading" bounds="[16,192][64,234]" />
    <node index="2" text="Results" bounds="[16,244][88,272]" />
    <node index="3" text="[ ]:" bounds="[16,312][31,328]" />
  </node>
</hierarchy>
"""


class LeadingMarksTest(unittest.TestCase):
    def test_reads_every_mark_in_document_order(self):
        self.assertEqual(
            [("markdown", 16), ("Collapse heading", 16), ("markdown", 64), ("[ ]", 16)],
            leading_marks(BEFORE),
        )

    def test_after_the_change_only_the_collapser_and_the_prompt_lead(self):
        self.assertEqual([("Collapse heading", 16), ("[ ]:", 16)], leading_marks(AFTER))

    def test_prose_is_not_a_mark(self):
        """The cell's own text sits in the same tree; only a prompt or a collapser leads."""
        self.assertNotIn("Notes before any heading.", [text for text, _ in leading_marks(AFTER)])

    def test_a_numbered_prompt_is_a_mark_with_or_without_the_colon(self):
        for notation in ("[12]", "[12]:"):
            with self.subTest(notation):
                dump = AFTER.replace('text="[ ]:"', f'text="{notation}"')
                self.assertIn((notation, 16), leading_marks(dump))

    def test_a_running_and_a_skipped_prompt_are_marks_too(self):
        for notation in ("[*]:", "[-]:"):
            with self.subTest(notation):
                dump = AFTER.replace('text="[ ]:"', f'text="{notation}"')
                self.assertIn((notation, 16), leading_marks(dump))

    def test_an_expanded_headings_collapser_reads_the_other_way_round(self):
        dump = AFTER.replace('content-desc="Collapse heading"', 'content-desc="Expand heading"')
        self.assertIn(("Expand heading", 16), leading_marks(dump))

    def test_the_wrong_frame_says_what_was_on_screen_instead(self):
        """A dump taken before the notebook opened is the failure this is most likely to hit."""
        with self.assertRaises(LookupError) as raised:
            leading_marks('<hierarchy><node text="Gutter.ipynb" bounds="[0,0][320,48]" /></hierarchy>')
        self.assertIn("Gutter.ipynb", str(raised.exception))


class ReportTest(unittest.TestCase):
    def test_calls_the_shipped_layout_ragged_by_what_the_collapser_cost(self):
        self.assertIn("RAGGED by 48dp", report(BEFORE, 160))

    def test_names_the_invented_labels_while_they_are_still_drawn(self):
        self.assertIn("invented kind labels still drawn: markdown", report(BEFORE, 160))

    def test_calls_the_reference_layout_aligned(self):
        self.assertIn("aligned", report(AFTER, 160))

    def test_says_nothing_about_invented_labels_once_they_are_gone(self):
        self.assertNotIn("invented", report(AFTER, 160))

    def test_names_each_mark_and_where_it_starts(self):
        lines = report(AFTER, 160).splitlines()
        self.assertIn("Collapse heading   x = 16 dp", lines)
        self.assertIn("[ ]:               x = 16 dp", lines)

    def test_converts_out_of_pixels_at_a_density_that_is_not_160(self):
        """The tablet is not at density 160, and a raw bound there is not a dp."""
        self.assertIn("RAGGED by 24dp", report(BEFORE, 320))


class EffectiveDensityTest(unittest.TestCase):
    def test_a_device_with_no_override_reports_its_physical_density(self):
        self.assertEqual(240, effective_density("Physical density: 240\n"))

    def test_an_override_wins_over_the_physical_density(self):
        self.assertEqual(420, effective_density("Physical density: 160\nOverride density: 420\n"))

    def test_it_says_what_it_was_given_when_there_is_no_density_at_all(self):
        with self.assertRaises(ValueError) as raised:
            effective_density("nothing here")
        self.assertIn("nothing here", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
