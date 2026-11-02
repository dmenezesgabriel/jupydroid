#!/usr/bin/env python3
"""Tests for read_touch_target_bounds.py."""

from __future__ import annotations

import unittest

from read_touch_target_bounds import effective_density, report, reserved_px

#: One `uiautomator dump`, trimmed to the two nodes the tool reads, at the sizes they shipped as.
BEFORE = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node index="0" content-desc="" bounds="[0,0][320,640]">
    <node index="0" content-desc="Collapsed code" bounds="[0,100][320,132]" />
    <node index="1" content-desc="2 cells hidden" bounds="[0,200][320,240]" />
  </node>
</hierarchy>
"""
AFTER = BEFORE.replace("[0,100][320,132]", "[0,100][320,148]").replace("[0,200][320,240]", "[0,200][320,248]")


class ReservedPxTest(unittest.TestCase):
    def test_reads_the_width_and_height_of_a_named_row(self):
        self.assertEqual((320, 32), reserved_px(BEFORE, "Collapsed code"))

    def test_reads_the_second_row_too(self):
        self.assertEqual((320, 40), reserved_px(BEFORE, "2 cells hidden"))

    def test_a_miss_names_what_was_on_screen_instead(self):
        """A dump of the wrong frame is the failure this is most likely to hit."""
        with self.assertRaises(LookupError) as raised:
            reserved_px(BEFORE, "Collapsed outputs")
        self.assertIn("Collapsed code", str(raised.exception))


class ReportTest(unittest.TestCase):
    def test_calls_the_shipped_sizes_under_the_minimum(self):
        lines = report(BEFORE, 160).splitlines()
        self.assertIn("UNDER by 16dp", lines[1])
        self.assertIn("UNDER by 8dp", lines[2])

    def test_calls_the_reserved_sizes_ok(self):
        lines = report(AFTER, 160).splitlines()
        self.assertTrue(all("ok" in line for line in lines[1:]), lines)

    def test_converts_out_of_pixels_at_a_density_that_is_not_160(self):
        """The tablet is not at density 160, and a raw bound there is not a dp."""
        lines = report(AFTER, 320).splitlines()
        self.assertIn("UNDER by 24dp", lines[1])


class EffectiveDensityTest(unittest.TestCase):
    def test_a_device_with_no_override_reports_its_physical_density(self):
        self.assertEqual(240, effective_density("Physical density: 240\n"))

    def test_an_override_wins_over_the_physical_density(self):
        """The bug this function exists for: the first number is the one that is not in use."""
        self.assertEqual(420, effective_density("Physical density: 160\nOverride density: 420\n"))

    def test_it_says_what_it_was_given_when_there_is_no_density_at_all(self):
        with self.assertRaises(ValueError) as raised:
            effective_density("Error: device offline")
        self.assertIn("device offline", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
