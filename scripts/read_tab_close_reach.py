#!/usr/bin/env python3
"""Whether the middle of each tab in the strip is the tab's or its close's, read out of a `uiautomator dump`.

A tab's close is a 24dp slot whose press area Compose widens to 48dp, and the dump reports that area
cut at the tab's right edge (ADR-0142), so the close node's left edge *is* where its reach starts. A
press on the tab's middle activates the tab only while the middle lies left of that edge, which is
what a minimum-width tab has to keep true.

**A measurement, not a gate.** It prints one line per tab and exits 0; the demo that calls it reads the
numbers beside the frames.

    adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml > ui.xml
    adb shell wm density | python3 scripts/read_tab_close_reach.py ui.xml

Tests in read_tab_close_reach_test.py.
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass

from read_pane_widths import BASELINE_DPI, BOUNDS, _density_from

#: A node's (left, top, right, bottom) in px.
Box = tuple[int, int, int, int]

TAB_PREFIX = "Tab: "
CLOSE_PREFIX = "Close tab: "


@dataclass(frozen=True)
class TabReach:
    """One tab, in dp from its own left edge."""

    title: str
    width_dp: float
    reach_dp: float
    middle_dp: float

    @property
    def margin_dp(self) -> float:
        """How far the middle sits left of the close's reach; zero or less means the close takes it."""
        return round(self.reach_dp - self.middle_dp, 1)

    @property
    def middle_is_clear(self) -> bool:
        return self.margin_dp > 0


def tab_reaches(xml_text: str, density: int) -> list[TabReach]:
    """Every whole tab in the dump, left to right, with the reach of its close.

    A tab the strip's scroller cuts is left out: the dump reports only the part that shows, so its width
    and middle are not the tab's.

    Args:
        xml_text: One `uiautomator dump`.
        density: The device's dpi, from `wm density`.

    Raises:
        LookupError: When the dump holds no tab or no whole tab, since each
            means the strip was not read and an empty answer would read as a pass.

    ```
    tab_reaches(open("ui.xml").read(), 160)  # -> [TabReach("a.py", 96.0, 56.0, 48.0)]
    ```
    """
    root = ElementTree.fromstring(xml_text)
    boxes = _boxes_by_description(root)
    titles = [d[len(TAB_PREFIX) :] for d in boxes if d.startswith(TAB_PREFIX)]
    if not titles:
        raise LookupError(f"no '{TAB_PREFIX}' node in the dump; expected the tab strip to be on screen")
    scroller_left = _scroller_left(root, boxes[TAB_PREFIX + titles[0]])
    whole = [t for t in titles if _is_whole(t, boxes, scroller_left)]
    if not whole:
        missing = [CLOSE_PREFIX + t for t in titles if CLOSE_PREFIX + t not in boxes]
        raise LookupError(f"no whole tab among {titles}; scroller left edge {scroller_left}px, no node for {missing}")
    return [_reach_of(t, boxes, density / BASELINE_DPI) for t in whole]


def _is_whole(title: str, boxes: dict[str, Box], scroller_left: int) -> bool:
    """A tab the scroller cuts is read off only the part that shows: on the left its bounds start at the
    scroller's edge, on the right the dump lists the tab and leaves its close out."""
    return boxes[TAB_PREFIX + title][0] > scroller_left and CLOSE_PREFIX + title in boxes


def _reach_of(title: str, boxes: dict[str, Box], scale: float) -> TabReach:
    left, _, right, _ = boxes[TAB_PREFIX + title]
    width = (right - left) / scale
    close_left = boxes[CLOSE_PREFIX + title][0]
    return TabReach(title, round(width, 1), round((close_left - left) / scale, 1), round(width / 2, 1))


def _boxes_by_description(root: ElementTree.Element) -> dict[str, Box]:
    """Each described node's (left, top, right, bottom) in px; a description seen twice keeps the first."""
    boxes: dict[str, Box] = {}
    for node in root.iter("node"):
        box = _box_of(node)
        description = node.get("content-desc", "")
        if box and description and description not in boxes:
            boxes[description] = box
    return boxes


def _scroller_left(root: ElementTree.Element, tab: Box) -> int:
    """The left edge, in px, of the scrollable node the [tab]'s centre lies in."""
    centre_x, centre_y = (tab[0] + tab[2]) / 2, (tab[1] + tab[3]) / 2
    for node in root.iter("node"):
        box = _box_of(node)
        if box and node.get("scrollable") == "true" and box[0] <= centre_x <= box[2] and box[1] <= centre_y <= box[3]:
            return box[0]
    raise LookupError(f"no scrollable node around the tab at {tab}; expected the strip's scroller")


def _box_of(node: ElementTree.Element) -> Box | None:
    """The node's bounds, or None for a node without any area: a tab scrolled fully out of sight is still
    listed, as `[0,0][0,0]`, and reading it would take the strip's left edge for the screen's."""
    found = BOUNDS.match(node.get("bounds", ""))
    if not found:
        return None
    left, top, right, bottom = (int(at) for at in found.groups())
    return (left, top, right, bottom) if right > left and bottom > top else None


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    density = _density_from(sys.stdin.read())
    for tab in tab_reaches(open(sys.argv[1]).read(), density):
        side = "the tab's" if tab.middle_is_clear else "the close's"
        print(
            f"{tab.title}: width {tab.width_dp}dp, close reach starts {tab.reach_dp}dp in, "
            f"middle {tab.middle_dp}dp, margin {tab.margin_dp}dp -> the middle is {side}"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
