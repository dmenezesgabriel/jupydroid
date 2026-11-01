#!/usr/bin/env python3
"""Tests for read_named_bounds.py."""

from __future__ import annotations

import unittest

from read_named_bounds import named_rows, settings_count

#: The modal rail open at one pane on SM-X216B (240 dpi), cut down from
#: `evidence/settings-from-more/rail-open/window.xml`: a text and a content-desc node, an unnamed one,
#: and the same name twice at one place, as Compose's merged and unmerged trees both report it.
RAIL_OPEN = """<hierarchy>
  <node text="" content-desc="" bounds="[0,0][1200,1920]"/>
  <node text="Settings" content-desc="" bounds="[102,1724][191,1757]"/>
  <node text="" content-desc="Close rail" bounds="[330,0][1200,1920]"/>
  <node text="New terminal" content-desc="" bounds="[102,137][242,170]"/>
  <node text="New terminal" content-desc="" bounds="[102,137][242,170]"/>
</hierarchy>"""


class NamedRowsTest(unittest.TestCase):
    def test_every_named_node_once_top_to_bottom_in_dp(self) -> None:
        self.assertEqual(
            [
                "  Close rail                               x 220-800 (580dp)  y 0-1280 (1280dp)",
                "  New terminal                             x 68-161 (93dp)  y 91-113 (22dp)",
                "  Settings                                 x 68-127 (59dp)  y 1149-1171 (22dp)",
            ],
            named_rows(RAIL_OPEN, 240),
        )

    def test_a_node_with_no_name_is_not_a_row(self) -> None:
        self.assertEqual(3, len(named_rows(RAIL_OPEN, 240)))


class SettingsCountTest(unittest.TestCase):
    def test_counts_every_node_named_settings(self) -> None:
        self.assertEqual(1, settings_count(RAIL_OPEN))

    def test_a_window_with_no_gear_counts_none(self) -> None:
        self.assertEqual(0, settings_count('<hierarchy><node text="Files" bounds="[0,0][1,1]"/></hierarchy>'))


if __name__ == "__main__":
    unittest.main()
