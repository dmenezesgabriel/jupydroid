#!/usr/bin/env python3
"""Take the device's status and navigation bars off a screenshot before it is committed.

A frame committed to the repository is published. `scripts/check_device_secrets.py` judges a binary by
its path only, because a frame is pixels, so nothing there can see the clock, the notifications or the
Wi-Fi icon along the top edge. This crops them off, by the height the *device* reports for each bar
rather than a number remembered from another run: a tablet, a phone and a gesture-navigation device
all differ.

The heights come from `dumpsys window`, whose `InsetsSource` lines name each bar's frame and the edge
it sits on (`uiautomator dump` lists only the app's own window, so it cannot see a system bar):

    adb shell dumpsys window > window.txt
    adb exec-out screencap -p > raw.png
    python3 scripts/crop_system_bars.py window.txt raw.png cropped.png

Tests in crop_system_bars_test.py, against a real dump kept in scripts/testdata/system-bars-dumpsys-window/.
"""

from __future__ import annotations

import pathlib
import re
import sys
from typing import NamedTuple

from PIL import Image

#: `InsetsSource id=… type=statusBars frame=[0,0][1920,45] visible=true … sideHint=TOP`. The gesture and
#: tappable-element sources repeat the same frames under other types, so only the two bars are read.
BAR_SOURCE = re.compile(
    r"type=(?P<name>statusBars|navigationBars) frame=\[\d+,(?P<top>\d+)]\[\d+,(?P<bottom>\d+)]"
    r" visible=(?P<visible>true|false).*?sideHint=(?P<side>\w+)"
)


class SystemBarInsets(NamedTuple):
    """Rows to remove from the top and the bottom of a full-screen capture."""

    top_px: int
    bottom_px: int


def read_system_bar_insets(dumpsys_window: str) -> SystemBarInsets:
    """The rows the status bar and the navigation bar occupy, from one `dumpsys window`.

    A hidden bar takes no rows. A bar on a side edge is refused rather than cropped wrongly, since
    this only removes rows.

    Args:
        dumpsys_window: The text of `adb shell dumpsys window`.

    ```
    read_system_bar_insets(open("window.txt").read())  # -> SystemBarInsets(top_px=45, bottom_px=72)
    ```
    """
    found = list(BAR_SOURCE.finditer(dumpsys_window))
    if not found:
        raise ValueError("no statusBars or navigationBars InsetsSource found; expected `adb shell dumpsys window` text")
    top = bottom = 0
    for bar in found:
        rows = int(bar["bottom"]) - int(bar["top"]) if bar["visible"] == "true" else 0
        if bar["side"] == "TOP":
            top = max(top, rows)
        elif bar["side"] == "BOTTOM":
            bottom = max(bottom, rows)
        else:
            raise ValueError(f"sideHint={bar['side']} for {bar['name']}; expected TOP or BOTTOM")
    return SystemBarInsets(top, bottom)


def crop_system_bars(image: Image.Image, insets: SystemBarInsets) -> Image.Image:
    """The frame without its first `insets.top_px` and last `insets.bottom_px` rows.

    ```
    crop_system_bars(Image.open("raw.png"), SystemBarInsets(top_px=45, bottom_px=72))
    ```
    """
    width, height = image.size
    top, bottom = insets
    if top < 0 or bottom < 0 or top + bottom >= height:
        raise ValueError(
            f"insets top_px={top}, bottom_px={bottom} leave no rows of a frame of height {height}; "
            "expected both non-negative and their sum below the height"
        )
    return image.crop((0, top, width, height - bottom))


def main(argv: list[str]) -> int:
    """Crop `IN.png` by the bars `DUMPSYS_WINDOW` names, and write `OUT.png`."""
    if len(argv) != 3:
        raise SystemExit(f"usage: crop_system_bars.py DUMPSYS_WINDOW IN.png OUT.png (got {argv!r})")
    dumpsys, source, target = (pathlib.Path(argument) for argument in argv)
    insets = read_system_bar_insets(dumpsys.read_text())
    with Image.open(source) as raw:
        crop_system_bars(raw, insets).save(target)
    print(f"cropped {insets.top_px} rows off the top and {insets.bottom_px} off the bottom of {source.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
