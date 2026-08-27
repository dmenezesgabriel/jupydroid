#!/usr/bin/env python3
"""How much of the search panel's query box is left for the query, read off a `uiautomator dump`.

P2x-3d. The reference draws the three option toggles **inside** the input, at its trailing edge
(`findInput.ts:196`), and its input is a workbench sidebar's width on a desktop. Ours is a 360dp
list pane, so whether a magnifier plus three 48dp targets still leave a readable query is a question
about this device and not about the reference - and the answer decides whether the toggles stay in
the box or move to a row beneath it.

**A measurement, not a gate.** It prints what it found and exits 0; the decision it informs is
recorded in `docs/DECISIONS.md` with these numbers beside it.

    adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml > ui.xml
    adb shell wm density | python3 scripts/read_search_box_width.py ui.xml

Tests in read_search_box_width_test.py.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ElementTree

BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")
#: `wm density` prints `Physical density: N` and, when one is set, `Override density: N` after it.
#: The override is the one in force, so the *last* match wins - `read_pane_widths` records why.
DENSITY = re.compile(r"^\s*(?:Physical|Override) density:\s*(\d+)", re.MULTILINE)
#: Material's baseline density, at which one dp is one pixel.
BASELINE_DPI = 160.0
#: What the box answers to - `SEARCH_WORKSPACE_BOX`, which is deliberately not the panel's title.
BOX_LABEL = "Search the workspace"
#: Both names of each toggle, because a toggle here says what a **tap will do** rather than what is
#: currently true (`docs/DECISIONS.md`) - so an enabled Match Case is on screen as "Ignore case".
TOGGLE_LABELS = (
    "Match case",
    "Ignore case",
    "Match whole word",
    "Match anywhere",
    "Use regular expression",
    "Search for plain text",
)


def search_box_span(xml_text: str, density: int) -> tuple[float, float]:
    """The query box's left and right edges in dp.

    Args:
        xml_text: One `uiautomator dump`.
        density: The device's dpi, from `wm density`.

    Raises:
        LookupError: The dump holds no query box, which means the panel was not up when it was
            taken - a zero would read as "no room" rather than as "wrong screen".

    ```
    search_box_span(open("ui.xml").read(), 240)  # -> (136.0, 472.0)
    ```
    """
    for label, left, right in _labelled_spans(xml_text, density):
        if label == BOX_LABEL:
            return left, right
    raise LookupError(f"no node labelled {BOX_LABEL!r}; the search panel was not up for this dump")


def option_toggle_spans(xml_text: str, density: int) -> list[tuple[str, float, float]]:
    """Each option toggle as (label, left, right) in dp, left to right.

    ```
    option_toggle_spans(open("ui.xml").read(), 240)[0]  # -> ("Match case", 328.0, 376.0)
    ```
    """
    found = [span for span in _labelled_spans(xml_text, density) if span[0] in TOGGLE_LABELS]
    return sorted(found, key=lambda span: span[1])


def text_room_between(box: tuple[float, float], toggles: list[tuple[str, float, float]]) -> float:
    """The dp between the box's left edge and the first toggle - what a query has to be read in.

    **The leading magnifier is inside this**, because it is drawn rather than labelled and so has no
    node of its own to subtract. Reported as one span for that reason: it is the room the box gives
    everything that is not a toggle, which is the number the placement question turns on.

    ```
    text_room_between((136.0, 472.0), [("Match case", 328.0, 376.0)])  # -> 192.0
    ```
    """
    return round((toggles[0][1] if toggles else box[1]) - box[0], 1)


def _labelled_spans(xml_text: str, density: int) -> list[tuple[str, float, float]]:
    """Every node carrying a `content-desc`, as (label, left, right) in dp."""
    scale = density / BASELINE_DPI
    spans = []
    for node in ElementTree.fromstring(xml_text).iter("node"):
        label = node.get("content-desc", "")
        found = BOUNDS.match(node.get("bounds", ""))
        if label and found:
            left, _, right, _ = (int(at) for at in found.groups())
            spans.append((label, round(left / scale, 1), round(right / scale, 1)))
    return spans


def _density_from(text: str) -> int:
    """The density in force - the *last* line `wm density` printed, which is the override if set."""
    found = DENSITY.findall(text)
    if not found:
        raise ValueError(f"no 'density: N' line in {text!r}; expected `adb shell wm density` output")
    return int(found[-1])


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    xml_text = open(sys.argv[1]).read()
    density = _density_from(sys.stdin.read())
    box = search_box_span(xml_text, density)
    toggles = option_toggle_spans(xml_text, density)
    print(f"box      x {box[0]:7.1f} .. {box[1]:7.1f}dp   width {box[1] - box[0]:7.1f}dp")
    for label, left, right in toggles:
        print(f"  toggle x {left:7.1f} .. {right:7.1f}dp   width {right - left:7.1f}dp   {label}")
    print(f"room for the query, magnifier included: {text_room_between(box, toggles):.1f}dp")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
