#!/usr/bin/env python3
"""Where every named node of a window sits, in dp, and how many answer to "Settings".

Read out of one `uiautomator dump`: each node with a `text` or `content-desc`, once, top to bottom,
and the count of nodes named `Settings` - one wherever its gear is drawn (ADR-0109).

**A measurement, not a gate.** It prints what it found and exits 0.

    adb shell uiautomator dump /sdcard/ui.xml && adb exec-out cat /sdcard/ui.xml > window.xml
    python3 scripts/read_named_bounds.py window.xml 240

Tests in read_named_bounds_test.py.
"""

from __future__ import annotations

import re
import sys

NAMED = re.compile(r"(?:text|content-desc)=\"([^\"]+)\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"")
#: Material's baseline density, at which one dp is one pixel.
BASELINE_DPI = 160.0


def named_rows(xml_text: str, density: int) -> list[str]:
    """One line per named node, sorted top to bottom then left to right, with its bounds in dp.

    Args:
        xml_text: One `uiautomator dump`.
        density: The device's dpi, from `wm density`.

    ```
    named_rows(open("window.xml").read(), 240)[0]  # -> "  Close rail   x 220-800 (580dp)  y 0-1280 (1280dp)"
    ```
    """
    px_per_dp = density / BASELINE_DPI
    rows = []
    for name, *px in sorted(set(NAMED.findall(xml_text)), key=lambda n: (int(n[2]), int(n[1]))):
        left, top, right, bottom = (round(int(at) / px_per_dp) for at in px)
        rows.append(f"  {name[:40]:40} x {left}-{right} ({right - left}dp)  y {top}-{bottom} ({bottom - top}dp)")
    return rows


def settings_count(xml_text: str) -> int:
    """How many nodes are named `Settings` - one, where a flow's `tapOn: "Settings"` has one target.

    ```
    settings_count(open("window.xml").read())  # -> 1
    ```
    """
    return len(re.findall(r"=\"Settings\"", xml_text))


def main(argv: list[str]) -> int:
    with open(argv[1], encoding="utf-8") as window:
        xml_text = window.read()
    for row in named_rows(xml_text, int(argv[2])):
        print(row)
    print("  nodes named Settings:", settings_count(xml_text))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
