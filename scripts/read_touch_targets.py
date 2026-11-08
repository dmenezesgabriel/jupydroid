#!/usr/bin/env python3
"""How big each control is and whether a screen reader can name it, read out of `uiautomator dump`s.

A target is a node the user can act on: `clickable`, `checkable`, or a text field. Its size is the bound
the platform reports for it, which is what Accessibility Scanner reads, and **not** the layout bound: the
tab's close button is declared 24dp (`TAB_CLOSE_TOUCH` in
`app/src/main/java/com/jupydroid/app/ui/shell/MainAreaTab.kt`) and the dump reports 40 x 40. So a bound under
48dp is a confirmed shortfall, while one at 48dp or more does not prove the layout reserves that much, which
is what ADR-0048 asks and the instrumented `assertReservesMinimumTouchTarget` answers. Its name is what
TalkBack reads: the node's own text or description, else those of the nodes beneath it. A descendant that is
itself a target is left out, since it is announced as its own stop and a tab is not renamed by the close
button inside it. A text field's own `text` is the typed value, which is its content, so only its description or
hint is its own name; Compose draws a field's label and supporting text as `TextView` children of the
`EditText`, so those name it like they would any other control.

`uiautomator` reports the visible part of a node, so one scrolled half out of its scroller reads short. Such a
target is listed apart as cut by its scroller and is neither counted under 48dp nor dropped.

**A measurement, not a gate.** It reports and exits 0. Which shortfalls are decided already is ADR-0048's
and the ledger's, not this script's.

    python3 scripts/read_touch_targets.py <dump.xml> <density> [<another dump.xml> ...]

The first dump is printed in full, every further one as counts only.
"""

from __future__ import annotations

import dataclasses
import pathlib
import sys
import xml.etree.ElementTree as ElementTree

from read_layout_spacing import Box
from read_pane_widths import BASELINE_DPI, BOUNDS
from read_touch_target_bounds import MINIMUM_DP

APP_PACKAGE = "com.jupydroid.app"
TEXT_FIELD_CLASS = "android.widget.EditText"
HORIZONTAL_SCROLLER_CLASS = "android.widget.HorizontalScrollView"
#: The attributes a screen reader takes a control's name from, in the order it prefers them.
NAME_ATTRIBUTES = ("content-desc", "text", "hint")
#: A text field's `text` is what was typed into it, which is its content and not its name.
FIELD_NAME_ATTRIBUTES = ("content-desc", "hint")
#: Pixels divided by a density are not exact, so two edges that are one edge differ in the last digits.
_EDGE_TOLERANCE_DP = 1e-6


@dataclasses.dataclass(frozen=True)
class TouchTarget:
    """One control: the bound the platform reports in dp, its class, the name a screen reader gives it ('' if none)
    and whether a scroller shows only part of it."""

    box: Box
    kind: str
    name: str
    cut: bool = False

    @property
    def size(self) -> tuple[float, float]:
        return self.box.width, self.box.height


@dataclasses.dataclass(frozen=True)
class Scroller:
    """A scrolling container's visible bound, and whether it scrolls sideways (`ScrollView` scrolls down)."""

    box: Box
    horizontal: bool


def touch_targets(xml_text: str, density: int, package: str = APP_PACKAGE) -> list[TouchTarget]:
    """Every control of [package] in one dump, in document order.

    Args:
        xml_text: One `uiautomator dump`.
        density: The density the window is laid out at, from `read_touch_target_bounds.effective_density`.
        package: Whose windows to read; the status and navigation bars belong to the system.

    ```
    touch_targets(open("hierarchy.xml").read(), 240)  # -> [TouchTarget(Box(...), "View", "Files"), ...]
    ```
    """
    found: list[TouchTarget] = []
    _collect(ElementTree.fromstring(xml_text), density / BASELINE_DPI, package, None, found)
    return found


def under_minimum(targets: list[TouchTarget]) -> list[TouchTarget]:
    """The targets short of 48dp in either direction, leaving out those a scroller cut."""
    return [at for at in targets if min(at.size) < MINIMUM_DP and not at.cut]


def cut_by_scroller(targets: list[TouchTarget]) -> list[TouchTarget]:
    """The targets whose bound is only the part a scroller shows, so its size says nothing.

    `uiautomator` reports the visible part of a node. One lying outside its scroller, or short along an axis
    where it touches the scroller's edge, is scrolled half away and not built short.
    """
    return [at for at in targets if at.cut]


def unnamed(targets: list[TouchTarget]) -> list[TouchTarget]:
    """The targets a screen reader would announce with no name."""
    return [at for at in targets if not at.name]


def _collect(
    element: ElementTree.Element, scale: float, package: str, scroller: Scroller | None, found: list[TouchTarget]
) -> None:
    if element.get("package") == package and _is_target(element):
        box = _box_dp(element, scale)
        found.append(TouchTarget(box, _kind(element), _name_of(element), _is_cut(box, scroller)))
    if element.get("scrollable") == "true" and element.get("bounds"):
        scroller = Scroller(_box_dp(element, scale), element.get("class") == HORIZONTAL_SCROLLER_CLASS)
    for child in element:
        _collect(child, scale, package, scroller, found)


def _is_cut(box: Box, scroller: Scroller | None) -> bool:
    """Whether the scroller's own axis shows only part of [box]; the other axis is the layout's, not its."""
    if scroller is None:
        return False
    edge = scroller.box
    low, high, edge_low, edge_high = (
        (box.left, box.right, edge.left, edge.right)
        if scroller.horizontal
        else (box.top, box.bottom, edge.top, edge.bottom)
    )
    lies_outside = low < edge_low - _EDGE_TOLERANCE_DP or high > edge_high + _EDGE_TOLERANCE_DP
    short_at_the_edge = high - low < MINIMUM_DP and (_same(low, edge_low) or _same(high, edge_high))
    return lies_outside or short_at_the_edge


def _same(one: float, other: float) -> bool:
    return abs(one - other) < _EDGE_TOLERANCE_DP


def _is_target(element: ElementTree.Element) -> bool:
    return (
        element.get("clickable") == "true"
        or element.get("checkable") == "true"
        or element.get("class") == TEXT_FIELD_CLASS
    )


def _kind(element: ElementTree.Element) -> str:
    return (element.get("class") or "").rsplit(".", 1)[-1]


def _box_dp(element: ElementTree.Element, scale: float) -> Box:
    found = BOUNDS.match(element.get("bounds", ""))
    if not found:
        raise ValueError(f"bounds {element.get('bounds')!r} is not uiautomator's '[left,top][right,bottom]'")
    return Box(*(int(at) / scale for at in found.groups()))


def _own_name(element: ElementTree.Element) -> str:
    attributes = FIELD_NAME_ATTRIBUTES if element.get("class") == TEXT_FIELD_CLASS else NAME_ATTRIBUTES
    return next((value for attribute in attributes if (value := element.get(attribute))), "")


def _name_of(element: ElementTree.Element) -> str:
    """The node's own name, else the names beneath it up to the next control, joined by a space."""
    return _own_name(element) or " ".join(_names_beneath(element))


def _names_beneath(element: ElementTree.Element) -> list[str]:
    names: list[str] = []
    for child in element:
        if _is_target(child):
            continue
        names.extend([_own_name(child)] if _own_name(child) else _names_beneath(child))
    return names


def _counts_line(path: str, targets: list[TouchTarget]) -> str:
    return (
        f"{path}: {len(targets)} targets, {len(under_minimum(targets))} under {MINIMUM_DP:g}dp, "
        f"{len(unnamed(targets))} unnamed, {len(cut_by_scroller(targets))} cut by a scroller"
    )


def _detail_lines(targets: list[TouchTarget]) -> list[str]:
    lines = [f"under {MINIMUM_DP:g}dp: {_describe(at)} {at.name!r}" for at in under_minimum(targets)]
    lines += [f"cut by a scroller: {_describe(at)} {at.name!r}" for at in cut_by_scroller(targets)]
    return lines + [f"unnamed: {_describe(at)} at ({at.box.left:g}, {at.box.top:g})" for at in unnamed(targets)]


def _describe(target: TouchTarget) -> str:
    return f"{target.kind} {target.size[0]:g} x {target.size[1]:g} dp"


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__)
        return 2
    density = int(argv[2])
    for index, path in enumerate([argv[1], *argv[3:]]):
        targets = touch_targets(pathlib.Path(path).read_text(), density)
        print(_counts_line(path, targets))
        if index == 0:
            print("\n".join(_detail_lines(targets)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
