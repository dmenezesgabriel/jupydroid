#!/usr/bin/env python3
"""What a larger font scale changes in a layout, read out of two `uiautomator dump`s of the same screen.

Two things a dump's bounds can establish. Nodes that **newly overlap in part**: two nodes that each carry a name
(a text or a description) and share pixels without one lying inside the other, absent at the first scale. A tab
holds its close button and a label sits inside its control, so a pair where one contains the other is nesting and
not an overlap, and a pair that overlapped already at the first scale is the layout's own. And controls that
**newly fall under 48dp**: a target that cleared Material's minimum at the first scale and no longer does.

**What a dump cannot say is whether a string was cut.** A node reports its box and not the extent of its text:
a tab's label is 130px wide at font scale 1.0 and at 2.0, and its string ends in an ellipsis at 2.0. That is read
off the screenshot, and the dump's numbers are quoted beside it. A node cut by its scroller is not compared
either: more content at a larger scale only moves what lies below the fold, which is scroll position and not fit.

**A measurement, not a gate.** It reports and exits 0.

    python3 scripts/read_font_scale_fit.py <dump at the first scale.xml> <dump at the second scale.xml> <density>

Both dumps must be of the same screen in the same state, the second taken after `settings put system font_scale`.
Two directories read every `<surface>.xml` they share, which is what `theme-six-combinations-driver.sh` leaves
per surface when run once at each `FONT_SCALE`; a surface captured at one scale only is named.
"""

from __future__ import annotations

import dataclasses
import pathlib
import sys
import xml.etree.ElementTree as ElementTree

from read_layout_spacing import Box
from read_pane_widths import BASELINE_DPI, BOUNDS
from read_touch_target_bounds import MINIMUM_DP
from read_touch_targets import (
    APP_PACKAGE,
    HORIZONTAL_SCROLLER_CLASS,
    NAME_ATTRIBUTES,
    Scroller,
    TouchTarget,
    is_cut,
    touch_targets,
    under_minimum,
)

TEXT_FIELD_CLASS = "android.widget.EditText"


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
    named: list[_Named] = []
    _collect_named(ElementTree.fromstring(xml_text), density / BASELINE_DPI, package, None, named)
    pairs = ((one, other) for index, one in enumerate(named) for other in named[index + 1 :])
    return [found for one, other in pairs if (found := _partial_overlap(one, other))]


@dataclasses.dataclass(frozen=True)
class _Named:
    name: str
    box: Box
    is_text_field: bool


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


def _collect_named(
    element: ElementTree.Element, scale: float, package: str, scroller: Scroller | None, found: list[_Named]
) -> None:
    name = next((value for attribute in NAME_ATTRIBUTES if (value := element.get(attribute))), "")
    if name and element.get("package") == package:
        box = _box_dp(element, scale)
        if not is_cut(box, scroller):
            found.append(_Named(name, box, element.get("class") == TEXT_FIELD_CLASS))
    if element.get("scrollable") == "true" and element.get("bounds"):
        scroller = Scroller(_box_dp(element, scale), element.get("class") == HORIZONTAL_SCROLLER_CLASS)
    for child in element:
        _collect_named(child, scale, package, scroller, found)


def _box_dp(element: ElementTree.Element, scale: float) -> Box:
    found = BOUNDS.match(element.get("bounds", ""))
    if not found:
        raise ValueError(f"bounds {element.get('bounds')!r} is not uiautomator's '[left,top][right,bottom]'")
    return Box(*(int(at) / scale for at in found.groups()))


def _is_label_on_border(field: _Named, label: _Named) -> bool:
    """A text field's floating label, which Material draws centred on the field's top border.

    The label's box starts above the field's, so the dump lists the two as sharing pixels in part, where the
    screenshot shows the label sitting on the outline (`test_a_fields_floating_label_on_its_border_is_not_an_overlap`).
    """
    if not field.is_text_field or label.is_text_field:
        return False
    centre = (label.box.top + label.box.bottom) / 2
    return field.box.left <= label.box.left and label.box.right <= field.box.right and field.box.top <= centre


def _partial_overlap(one: _Named, other: _Named) -> Overlap | None:
    (first, box), (second, other_box) = (one.name, one.box), (other.name, other.box)
    if box.contains(other_box) or other_box.contains(box):
        return None
    if _is_label_on_border(one, other) or _is_label_on_border(other, one):
        return None
    width = min(box.right, other_box.right) - max(box.left, other_box.left)
    height = min(box.bottom, other_box.bottom) - max(box.top, other_box.top)
    if width <= 0 or height <= 0:
        return None
    return Overlap(first, second, round(width * height, 1))


def _describe(target: TouchTarget) -> str:
    return f"{target.kind} {target.size[0]:g} x {target.size[1]:g} dp {target.name!r}"


def _report(before_xml: str, after_xml: str, density: int) -> list[str]:
    overlaps = newly_overlapping(named_overlaps(before_xml, density), named_overlaps(after_xml, density))
    short = newly_short_targets(touch_targets(before_xml, density), touch_targets(after_xml, density))
    lines = [f"{len(overlaps)} newly overlapping, {len(short)} newly under {MINIMUM_DP:g}dp"]
    lines += [f"overlapping: {at.first!r} and {at.second!r} share {at.area:g} dp2" for at in overlaps]
    lines += [f"under {MINIMUM_DP:g}dp: {_describe(at)}" for at in short]
    return lines


def _report_directories(before: pathlib.Path, after: pathlib.Path, density: int) -> list[str]:
    """One block per surface, a dump named alike in both directories; a surface in one is named, not skipped."""
    first = {at.stem: at for at in before.glob("*.xml")}
    second = {at.stem: at for at in after.glob("*.xml")}
    lines: list[str] = []
    for surface in sorted(first.keys() | second.keys()):
        if surface not in second:
            lines.append(f"-- {surface}: captured at the first scale only")
        elif surface not in first:
            lines.append(f"-- {surface}: captured at the second scale only")
        else:
            lines.append(f"-- {surface}")
            lines += _report(first[surface].read_text(), second[surface].read_text(), density)
    return lines


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__)
        return 2
    before, after = pathlib.Path(argv[1]), pathlib.Path(argv[2])
    density = int(argv[3])
    if before.is_dir() != after.is_dir():
        raise ValueError(f"{before} and {after} must both be directories or both be dumps")
    if before.is_dir():
        lines = _report_directories(before, after, density)
    else:
        lines = _report(before.read_text(), after.read_text(), density)
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
