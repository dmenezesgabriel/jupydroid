#!/usr/bin/env python3
"""Tests for the node-fill oracle, including one run over this repository's own committed frame."""
from __future__ import annotations

import pathlib
import tempfile
import unittest

from PIL import Image
from read_node_fill import node_fill

WASH = (0xDA, 0xDA, 0xDA)
PANEL = (0xFA, 0xFA, 0xFD)
INK = (0x20, 0x20, 0x20)


def _dump(
    nodes: dict[str, tuple[int, int, int, int]],
    screen: tuple[int, int] = (320, 640),
    attribute: str = "content-desc",
) -> str:
    """A `uiautomator dump` holding [nodes] as [attribute] -> bounds, inside a [screen] root."""
    rows = "".join(
        f'<node {attribute}="{desc}" bounds="[{left},{top}][{right},{bottom}]" />'
        for desc, (left, top, right, bottom) in nodes.items()
    )
    return (
        '<?xml version="1.0" encoding="UTF-8"?><hierarchy rotation="0">'
        f'<node content-desc="" bounds="[0,0][{screen[0]},{screen[1]}]">{rows}</node>'
        "</hierarchy>"
    )


def _frame(size: tuple[int, int], ground: tuple[int, int, int]) -> Image.Image:
    return Image.new("RGB", size, ground)


def _saved(image: Image.Image) -> str:
    path = pathlib.Path(tempfile.mkdtemp()) / "frame.png"
    image.save(path)
    return str(path)


class NodeFillTest(unittest.TestCase):
    def test_a_nodes_fill_is_the_colour_inside_its_own_bounds(self) -> None:
        image = _frame((320, 640), PANEL)
        for x in range(40, 88):
            for y in range(100, 148):
                image.putpixel((x, y), WASH)
        dump = _dump({"Ignore case": (40, 100, 88, 148)})
        self.assertEqual("#DADADA", node_fill(dump, "content-desc", "Ignore case", _saved(image)))

    def test_a_glyph_inside_the_node_does_not_become_its_fill(self) -> None:
        """The mode inside the bounds, never one pixel - a toggle draws `Aa` over its own wash."""
        image = _frame((320, 640), PANEL)
        for x in range(40, 88):
            for y in range(100, 148):
                image.putpixel((x, y), WASH)
        for x in range(58, 70):               # the glyph's own strokes, a minority of the square
            for y in range(115, 133):
                image.putpixel((x, y), INK)
        dump = _dump({"Ignore case": (40, 100, 88, 148)})
        self.assertEqual("#DADADA", node_fill(dump, "content-desc", "Ignore case", _saved(image)))

    def test_the_surrounding_panel_is_not_read(self) -> None:
        """A node narrower than its panel must report its own fill, not the ground around it."""
        image = _frame((320, 640), PANEL)
        for x in range(40, 88):
            for y in range(100, 148):
                image.putpixel((x, y), WASH)
        dump = _dump({"Ignore case": (40, 100, 88, 148)})
        self.assertNotEqual("#FAFAFD", node_fill(dump, "content-desc", "Ignore case", _saved(image)))

    def test_bounds_are_scaled_when_the_frame_is_not_the_dumps_own_size(self) -> None:
        """Two capture paths, one screen: a frame twice the dump's size still reads the same node."""
        image = _frame((640, 1280), PANEL)
        for x in range(80, 176):
            for y in range(200, 296):
                image.putpixel((x, y), WASH)
        dump = _dump({"Ignore case": (40, 100, 88, 148)})
        self.assertEqual("#DADADA", node_fill(dump, "content-desc", "Ignore case", _saved(image)))

    def test_the_marked_row_is_found_by_the_state_the_app_sets_not_by_a_name(self) -> None:
        """The explorer's open row carries no description - it sets `selected`, so that selects it."""
        image = _frame((320, 640), PANEL)
        for x in range(0, 320):
            for y in range(137, 209):
                image.putpixel((x, y), WASH)
        dump = _dump({"true": (0, 137, 320, 209)}, attribute="selected")
        self.assertEqual("#DADADA", node_fill(dump, "selected", "true", _saved(image)))

    def test_a_selector_matching_several_nodes_is_refused_rather_than_resolved(self) -> None:
        """Measuring whichever node came first is how an oracle reports an untraceable number."""
        dump = _dump({"Ignore case": (40, 100, 88, 148)}) + ""
        doubled = dump.replace(
            '<node content-desc="Ignore case" bounds="[40,100][88,148]" />',
            '<node content-desc="Ignore case" bounds="[40,100][88,148]" />'
            '<node content-desc="Ignore case" bounds="[90,100][138,148]" />',
        )
        with self.assertRaises(LookupError) as raised:
            node_fill(doubled, "content-desc", "Ignore case", _saved(_frame((320, 640), PANEL)))
        self.assertIn("2 nodes", str(raised.exception))

    def test_a_missing_node_names_itself_and_what_was_there(self) -> None:
        """The flow left the wrong frame on screen, and the message has to say so."""
        dump = _dump({"Match case": (40, 100, 88, 148)})
        with self.assertRaises(LookupError) as raised:
            node_fill(dump, "content-desc", "Ignore case", _saved(_frame((320, 640), PANEL)))
        self.assertIn("Ignore case", str(raised.exception))
        self.assertIn("Match case", str(raised.exception))

    def test_a_frame_of_a_different_shape_is_refused_rather_than_guessed_at(self) -> None:
        """A landscape frame against a portrait dump is two different screens, not a scale."""
        dump = _dump({"Ignore case": (40, 100, 88, 148)})
        with self.assertRaises(ValueError) as raised:
            node_fill(dump, "content-desc", "Ignore case", _saved(_frame((640, 400), PANEL)))
        self.assertIn("640x400", str(raised.exception))
        self.assertIn("320x640", str(raised.exception))


class ThisRepositoryTest(unittest.TestCase):
    """The defect P7a-1b exists to close, read off the frames it is visible in.

    **An oracle that cannot see the bug cannot be trusted to see the fix.** These two pairs were
    captured at `b4a81e47`, the commit before this step, and are committed rather than overwritten
    by the run that proves the fix - `evidence/p7a-1b-toggle-wash/before-the-fix/README.md` says so
    in as many words. What they hold is a search toggle drawing its *on* wash from
    `list.inactiveSelectionBackground`, the id that belongs to the explorer's row, rather than from
    the `inputOption.activeBackground` its own KDoc has always cited.
    """

    #: `content-desc` of the toggle in its *on* state - what a tap would do, which is this app's
    #: own state readout (ADR-0036, and the flow's note on why not "Match case").
    ON_TOGGLE = "Ignore case"
    BEFORE = pathlib.Path("evidence/p7a-1b-toggle-wash/before-the-fix")

    def _read(self, theme: str) -> str:
        directory = self.BEFORE / theme
        dump = (directory / "hierarchy.xml").read_text(encoding="utf-8")
        return node_fill(dump, "content-desc", self.ON_TOGGLE, str(directory / "02-the-toggle-on.png"))

    def test_the_dark_wash_was_the_lists_colour_and_not_the_toggles(self) -> None:
        self.assertEqual("#2C2D2E", self._read("dark"))

    def test_the_light_wash_was_the_lists_colour_and_not_the_toggles(self) -> None:
        self.assertEqual("#DADADA", self._read("light"))

    def test_the_marked_row_had_the_alpha_flattened_out_of_it(self) -> None:
        """The other half of the same defect, on the tablet, selected the way the driver selects.

        2026 Light states `list.inactiveSelectionBackground` at 60% - `#DADADA99`, which paints
        `#E7E7E8` over the side bar. The ramp held it opaque, so the row read the raw `#DADADA`.
        Dark is deliberately not here: its own value is opaque in the reference, so before and
        after are the same colour and a frame of it would prove nothing.
        """
        directory = pathlib.Path("evidence/p7a-1b-list-selection/before-the-fix/light")
        dump = (directory / "hierarchy.xml").read_text(encoding="utf-8")
        frame = str(directory / "02-the-open-row-is-washed.png")
        self.assertEqual("#DADADA", node_fill(dump, "checked", "true", frame))


if __name__ == "__main__":
    unittest.main()
