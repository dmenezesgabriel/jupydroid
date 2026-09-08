#!/usr/bin/env python3
"""What colour a *named* region of the screen is actually filled with, read off a frame.

`docs/PLAN.md`'s P7a-1b. The step moves two colours: the explorer's open row becomes
`list.inactiveSelectionBackground` - translucent in 2026 Light, so it paints `#E7E7E8` over the
side bar rather than the `#DADADA` the ramp used to hand it - and the search toggles' on-wash
becomes `inputOption.activeBackground`, the id they always cited. **Neither is something a Maestro
assertion can read**, which is exactly how P7a-1's own defect survived a green suite
(`scripts/read_chrome_parts.py`, and `docs/DECISIONS.md` on P2u-1).

**Why not `read_chrome_parts.py`.** That one reads *columns*, because a part spans the window's
full height. These two regions are a list row and a 48dp square - neither is a column, and both are
found by an attribute rather than by position. Bounds come from the accessibility tree, which is the
same place `read_gutter_x.py` and `read_touch_target_bounds.py` get theirs; what is new here is
reading the frame's pixels *inside* them.

**Any attribute, because the two regions are named by different ones.** A toggle carries a
`contentDescription`, so it is found by `content-desc`; the explorer's marked row carries none - it
sets `selected` instead (`ExplorerScreen.kt`), which uiautomator writes as `selected="true"` - and
that is the better handle anyway, because it asks the app which row it thinks is open rather than
naming a file the fixture could rename.

**An ambiguous match is refused, not resolved.** Asking for a value several nodes carry and
measuring whichever came first is how an oracle reports a number nobody can trace; this repository
has had two pixel oracles be wrong already (`docs/DECISIONS.md`, P4j-3c and P4j-3d-3).

**The mode inside the bounds, never one pixel of it.** A row carries an icon and a label and a
toggle carries its glyph, so a sampled pixel reads whichever of ink and ground it happened to land
on - the way a pixel oracle in this repository has already been wrong twice (`docs/DECISIONS.md`,
P4j-3c and P4j-3d-3). Ink is a minority of any region drawn around it, so the mode is the fill.

**The two captures are reconciled, not assumed equal.** A dump and a frame come from different
tools; where they disagree on scale the bounds are scaled by the ratio, and where they disagree on
*shape* that is two different screens and this refuses rather than guessing.

**A measurement, not a gate**, the footing `read_touch_target_bounds.py` and `read_pane_widths.py`
stand on: it prints what it found and exits 0, because half its job is to be run on the build
*before* the fix. `check_node_fill.py` is what fails red.

    adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml hierarchy.xml
    python3 scripts/read_node_fill.py hierarchy.xml frame.png content-desc "Ignore case"
    python3 scripts/read_node_fill.py hierarchy.xml frame.png selected true

Tests in read_node_fill_test.py, including one that reads this repository's own committed pre-fix
frame - an oracle that cannot see the bug cannot be trusted to see the fix.
"""
from __future__ import annotations

import collections
import re
import sys
import xml.etree.ElementTree as ElementTree

from PIL import Image

#: `bounds="[left,top][right,bottom]"`, the one notation `uiautomator dump` writes them in.
BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")
#: How far the two captures' scales may differ before they are called different screens. A frame
#: and a dump of one screen scale by the same factor on both axes; rounding is all this allows for.
_SCALE_TOLERANCE = 0.01


def node_fill(xml_text: str, attribute: str, value: str, image_path: str) -> str:
    """The colour filling the one node whose [attribute] is [value], as `#RRGGBB`.

    Args:
        xml_text: One `uiautomator dump`.
        attribute: Which attribute names the region - `content-desc` for a described control,
            `selected` for the row the app itself marks.
        value: What that attribute has to be, exactly as the dump writes it.
        image_path: A frame of the same screen, at any scale of it.

    Returns:
        The mode colour inside the node's own bounds.

    Raises:
        LookupError: When no node matches, or when several do - the first means the flow left the
            wrong frame on screen, the second that the selector does not identify a region.
        ValueError: When the frame and the dump are not the same screen.

    ```
    node_fill(open("hierarchy.xml").read(), "content-desc", "Ignore case", "02-the-toggle-on.png")
    ```
    """
    tree = ElementTree.fromstring(xml_text)
    screen = _screen_of(tree)
    left, top, right, bottom = _bounds_of(tree, attribute, value)
    with Image.open(image_path) as frame:
        scale = _scale(frame.size, screen)
        region = frame.convert("RGB").crop(
            (round(left * scale), round(top * scale), round(right * scale), round(bottom * scale))
        )
        return _mode_colour(region)


def _screen_of(tree: ElementTree.Element) -> tuple[int, int]:
    """The dump's own coordinate space, taken from the outermost node that states bounds."""
    for node in tree.iter("node"):
        match = BOUNDS.match(node.get("bounds", ""))
        if match:
            return int(match.group(3)), int(match.group(4))
    raise LookupError('no node in the dump states bounds; expected one with bounds="[0,0][w,h]"')


def _bounds_of(tree: ElementTree.Element, attribute: str, value: str) -> tuple[int, int, int, int]:
    found = [
        BOUNDS.match(node.get("bounds", ""))
        for node in tree.iter("node")
        if node.get(attribute) == value
    ]
    bounded = [match for match in found if match]
    if len(bounded) == 1:
        return tuple(int(group) for group in bounded[0].groups())  # type: ignore[return-value]
    if not bounded:
        carried = sorted({node.get(attribute, "") for node in tree.iter("node")} - {"", None})
        raise LookupError(f"no node has {attribute}={value!r}; the frame carries {carried}")
    raise LookupError(
        f"{len(bounded)} nodes have {attribute}={value!r}; expected exactly one, so the selector "
        "does not identify a region"
    )


def _scale(frame: tuple[int, int], screen: tuple[int, int]) -> float:
    """How much larger the frame is than the dump's own space, refusing a different shape."""
    across, down = frame[0] / screen[0], frame[1] / screen[1]
    if abs(across - down) > _SCALE_TOLERANCE:
        raise ValueError(
            f"frame {frame[0]}x{frame[1]} is not a scale of the dump's {screen[0]}x{screen[1]}; "
            "the two captures are of different screens"
        )
    return across


def _mode_colour(region: Image.Image) -> str:
    width, height = region.size
    counted = collections.Counter(
        region.getpixel((x, y))[:3] for y in range(height) for x in range(width)
    )
    (red, green, blue), _ = counted.most_common(1)[0]
    return f"#{red:02X}{green:02X}{blue:02X}"


def main(argv: list[str]) -> int:
    if len(argv) != 5:
        print(__doc__.splitlines()[0], file=sys.stderr)
        print(
            "usage: read_node_fill.py <hierarchy.xml> <frame.png> <attribute> <value>",
            file=sys.stderr,
        )
        return 2
    dump, frame, attribute, value = argv[1:]
    with open(dump, encoding="utf-8") as hierarchy:
        print(f"{attribute}={value}: {node_fill(hierarchy.read(), attribute, value, frame)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
