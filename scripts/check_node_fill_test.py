#!/usr/bin/env python3
"""Tests for the node-fill gate."""
from __future__ import annotations

import pathlib
import tempfile
import unittest

from check_node_fill import fill_failures
from PIL import Image

PANEL = (0xFA, 0xFA, 0xFD)
WASH = (0xDA, 0xDA, 0xDA)


def _screen(desc: str, fill: tuple[int, int, int]) -> tuple[str, str]:
    """A dump and a frame of one 320x640 screen, with [desc] filled in [fill] at a known square."""
    image = Image.new("RGB", (320, 640), PANEL)
    for x in range(40, 88):
        for y in range(100, 148):
            image.putpixel((x, y), fill)
    directory = pathlib.Path(tempfile.mkdtemp())
    frame = directory / "01-the-toggle-on.png"
    image.save(frame)
    dump = directory / "hierarchy.xml"
    dump.write_text(
        '<?xml version="1.0" encoding="UTF-8"?><hierarchy rotation="0">'
        '<node content-desc="" bounds="[0,0][320,640]">'
        f'<node content-desc="{desc}" bounds="[40,100][88,148]" /></node></hierarchy>',
        encoding="utf-8",
    )
    return str(dump), str(frame)


class FillFailuresTest(unittest.TestCase):
    def test_the_expected_fill_is_no_failure(self) -> None:
        dump, frame = _screen("Ignore case", WASH)
        self.assertEqual([], fill_failures(dump, frame, "content-desc", "Ignore case", "#DADADA"))

    def test_a_different_fill_names_both_colours(self) -> None:
        """A gate whose message does not carry the offending value is one nobody can act on."""
        dump, frame = _screen("Ignore case", (0xD6, 0xD6, 0xD6))
        failure = fill_failures(dump, frame, "content-desc", "Ignore case", "#DADADA")
        self.assertEqual(1, len(failure))
        self.assertIn("#D6D6D6", failure[0])
        self.assertIn("#DADADA", failure[0])

    def test_a_frame_that_never_showed_the_node_fails_rather_than_passing(self) -> None:
        """The flow left the wrong screen up - the one way a colour gate passes for free."""
        dump, frame = _screen("Match case", WASH)
        failure = fill_failures(dump, frame, "content-desc", "Ignore case", "#DADADA")
        self.assertEqual(1, len(failure))
        self.assertIn("Ignore case", failure[0])

    def test_the_comparison_ignores_the_case_the_hex_is_written_in(self) -> None:
        dump, frame = _screen("Ignore case", WASH)
        self.assertEqual([], fill_failures(dump, frame, "content-desc", "Ignore case", "#dadada"))


if __name__ == "__main__":
    unittest.main()
