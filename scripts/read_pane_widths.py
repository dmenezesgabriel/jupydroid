#!/usr/bin/env python3
"""What each column of the shell actually gets, read out of a `uiautomator dump`.

The two-pane threshold is 840dp because of an arithmetic that predates the activity bar:
360dp of list pane plus a divider left the notebook more than the width the single-pane layout was
designed for. A rail takes width off the *window* before either pane sees it, so the arithmetic has
to be redone against a measured rail rather than an assumed one.

**A measurement, not a gate.** It reports what it found and exits 0, because its whole job is to
say which way the decision should go - including "the threshold stays", which is a result and not
a failure.

    adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml > ui.xml
    adb shell wm density | python3 scripts/read_pane_widths.py ui.xml 1280

Tests in read_pane_widths_test.py.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ElementTree

BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")
#: `wm density` prints `Physical density: N` and, when one is set, `Override density: N` after it.
#: The override is the one in force, so the *last* match wins - reading the first is what made a
#: phone-shaped run read the wrong density, as `read_touch_target_bounds.effective_density` records.
DENSITY = re.compile(r"^\s*(?:Physical|Override) density:\s*(\d+)", re.MULTILINE)
#: Material's baseline density, at which one dp is one pixel.
BASELINE_DPI = 160.0
#: Narrower than this and a column is a control, not a column of the shell.
MIN_COLUMN_PX = 100


def shell_columns(xml_text: str, density: int) -> list[tuple[float, float]]:
    """The shell's columns as (left, right) in dp, left to right.

    A column is a node as tall as the window: the activity bar and each pane are, and everything
    drawn *inside* one is not. Nodes that contain another column are dropped, so the window itself
    and the region holding both panes do not appear - what is left is the columns a reader sees.

    Args:
        xml_text: One `uiautomator dump`.
        density: The device's dpi, from `wm density`.

    ```
    shell_columns(open("ui.xml").read(), 240)  # -> [(0.0, 80.0), (81.3, 441.3), (465.3, 1280.0)]
    ```
    """
    height = _window_height_px(xml_text)
    spans = set()
    for node in ElementTree.fromstring(xml_text).iter("node"):
        found = BOUNDS.match(node.get("bounds", ""))
        if not found:
            continue
        left, top, right, bottom = (int(at) for at in found.groups())
        if (top, bottom) != (0, height) or right - left < MIN_COLUMN_PX:
            continue
        spans.add((left, right))
    innermost = [s for s in spans if not any(_contains(s, other) for other in spans)]
    scale = density / BASELINE_DPI
    return sorted((round(left / scale, 1), round(right / scale, 1)) for left, right in innermost)


def detail_width_at(window_dp: float, columns: list[tuple[float, float]]) -> float:
    """What the document pane would get in a [window_dp]-wide window, keeping this chrome.

    Everything left of the document pane keeps the width it was measured at - the bar, the divider,
    the list pane and the spacer between the panes are all fixed - so the document pane absorbs the
    whole of the difference. That is the number the threshold has to clear.

    Raises:
        ValueError: When fewer than two columns were measured, which means the dump came from a
            one-pane window and there is no split to re-do the arithmetic on.

    ```
    detail_width_at(800, [(0.0, 80.0), (81.3, 441.3), (465.3, 1280.0)])  # -> 334.7
    ```
    """
    if len(columns) < 2:
        raise ValueError(f"need at least two columns to re-do the split, got {columns}")
    return round(window_dp - columns[-1][0], 1)


def _contains(outer: tuple[int, int], inner: tuple[int, int]) -> bool:
    """Whether [outer] strictly contains [inner], which is what makes it a container and not a column."""
    return outer != inner and outer[0] <= inner[0] and inner[1] <= outer[1]


def _window_height_px(xml_text: str) -> int:
    """The window's height, which is the tallest node in the dump."""
    heights = [
        int(found.group(4))
        for node in ElementTree.fromstring(xml_text).iter("node")
        if (found := BOUNDS.match(node.get("bounds", "")))
    ]
    if not heights:
        raise LookupError("no node carried bounds; the dump is not a uiautomator hierarchy")
    return max(heights)


def _density_from(text: str) -> int:
    """The density in force - the *last* line `wm density` printed, which is the override if set."""
    found = DENSITY.findall(text)
    if not found:
        raise ValueError(f"no 'density: N' line in {text!r}; expected `adb shell wm density` output")
    return int(found[-1])


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    hierarchy, window_dp = sys.argv[1], float(sys.argv[2])
    columns = shell_columns(open(hierarchy).read(), _density_from(sys.stdin.read()))
    print(f"window {window_dp:.0f}dp, {len(columns)} column(s)")
    for left, right in columns:
        print(f"  x {left:8.1f} .. {right:8.1f}dp   width {right - left:8.1f}dp")
    if len(columns) < 2:
        print("  one pane: nothing to re-split")
        return 0
    for candidate in (800.0, 840.0, 1280.0):
        print(f"  document pane in a {candidate:.0f}dp window: {detail_width_at(candidate, columns):.1f}dp")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
