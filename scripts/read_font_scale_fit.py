#!/usr/bin/env python3
"""What a larger font scale changes in a layout, read out of two `uiautomator dump`s of the same screen.

Two things a dump's bounds can establish. Nodes that **newly overlap in part**: two nodes that each carry a name
(a text or a description) and share pixels without one lying inside the other, absent at the first scale. A tab
holds its close button and a label sits inside its control, so a pair where one contains the other is nesting and
not an overlap, and a pair that overlapped already at the first scale is the layout's own. And controls that
**newly fall under 48dp**: a target that cleared Material's minimum at the first scale and no longer does.

**What a dump cannot say is whether a string was cut.** A node reports its box and not the extent of its text:
a tab's label is 130px wide at font scale 1.0 and at 2.0, and its string ends in an ellipsis at 2.0. That is read
off the screenshot, and the dump's numbers are quoted beside it. A target cut by its scroller is not compared
either: more content at a larger scale only moves what lies below the fold, which is scroll position and not fit.

**A measurement, not a gate.** It reports and exits 0.

    python3 scripts/read_font_scale_fit.py <dump at the first scale.xml> <dump at the second scale.xml> <density>

Both dumps must be of the same screen in the same state, the second taken after `settings put system font_scale`.
"""

from __future__ import annotations

import dataclasses
import pathlib
import sys
import xml.etree.ElementTree as ElementTree

from read_layout_spacing import Box
from read_pane_widths import BASELINE_DPI, BOUNDS
from read_touch_target_bounds import MINIMUM_DP
from read_touch_targets import APP_PACKAGE, NAME_ATTRIBUTES, TouchTarget, touch_targets, under_minimum


@dataclasses.dataclass(frozen=True)
class Overlap:
    """Two named nodes that share [area] dp², by the names they carry, in document order."""

    first: str
    second: str
    area: float


def named_overlaps(xml_text: str, density: int, package: str = APP_PACKAGE) -> list[Overlap]:
    """Every pair of named nodes of [package] that share pixels with neither lying inside the other.

    Args:
        xml_text: One `uiautomator dump`.
        density: The density the window is laid out at, from `read_touch_target_bounds.effective_density`.
        package: Whose windows to read; the status and navigation bars belong to the system.

    ```
    named_overlaps(open("hierarchy.xml").read(), 240)  # -> [Overlap("Tab: Launcher", "Reload tab: ...", 6.0)]
    ```
    """
    named: list[tuple[str, Box]] = []
    _collect_named(ElementTree.fromstring(xml_text), density / BASELINE_DPI, package, named)
    pairs = ((one, other) for index, one in enumerate(named) for other in named[index + 1 :])
    return [found for one, other in pairs if (found := _partial_overlap(one, other))]


def newly_overlapping(before: list[Overlap], after: list[Overlap]) -> list[Overlap]:
    """The overlaps of [after] whose pair of names did not overlap in [before]."""
    known = {(at.first, at.second) for at in before}
    return [at for at in after if (at.first, at.second) not in known]


def newly_short_targets(before: list[TouchTarget], after: list[TouchTarget]) -> list[TouchTarget]:
    """The targets under 48dp in [after] that [before] holds, by kind and name, at 48dp or more.

    A target [before] does not have, or had cut by a scroller, has no size to compare against.
    """
    cleared = {(at.kind, at.name) for at in before if min(at.size) >= MINIMUM_DP}
    return [at for at in under_minimum(after) if (at.kind, at.name) in cleared]


def _collect_named(element: ElementTree.Element, scale: float, package: str, found: list[tuple[str, Box]]) -> None:
    name = next((value for attribute in NAME_ATTRIBUTES if (value := element.get(attribute))), "")
    if name and element.get("package") == package:
        found.append((name, _box_dp(element, scale)))
    for child in element:
        _collect_named(child, scale, package, found)


def _box_dp(element: ElementTree.Element, scale: float) -> Box:
    found = BOUNDS.match(element.get("bounds", ""))
    if not found:
        raise ValueError(f"bounds {element.get('bounds')!r} is not uiautomator's '[left,top][right,bottom]'")
    return Box(*(int(at) / scale for at in found.groups()))


def _partial_overlap(one: tuple[str, Box], other: tuple[str, Box]) -> Overlap | None:
    (first, box), (second, other_box) = one, other
    if box.contains(other_box) or other_box.contains(box):
        return None
    width = min(box.right, other_box.right) - max(box.left, other_box.left)
    height = min(box.bottom, other_box.bottom) - max(box.top, other_box.top)
    if width <= 0 or height <= 0:
        return None
    return Overlap(first, second, round(width * height, 1))


def _describe(target: TouchTarget) -> str:
    return f"{target.kind} {target.size[0]:g} x {target.size[1]:g} dp {target.name!r}"


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__)
        return 2
    before_xml, after_xml = (pathlib.Path(path).read_text() for path in argv[1:3])
    density = int(argv[3])
    overlaps = newly_overlapping(named_overlaps(before_xml, density), named_overlaps(after_xml, density))
    short = newly_short_targets(touch_targets(before_xml, density), touch_targets(after_xml, density))
    lines = [f"{len(overlaps)} newly overlapping, {len(short)} newly under {MINIMUM_DP:g}dp"]
    lines += [f"overlapping: {at.first!r} and {at.second!r} share {at.area:g} dp2" for at in overlaps]
    lines += [f"under {MINIMUM_DP:g}dp: {_describe(at)}" for at in short]
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
