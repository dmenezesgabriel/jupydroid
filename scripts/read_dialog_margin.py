#!/usr/bin/env python3
"""How far a dialog's surface stands from the window's left and right edges, read off a screenshot.

`read_chrome_parts.py` reads the mode down each column, which is right for a pane that fills its column and
wrong for a dialog: Delete's card is a third of the window's height, so the two columns beside it read as the
scrim, 30px at 320dp where the card is drawn 24px in. A dump cannot say either, since the dialog's window is
the screen's whole width and the card is not a node. This reads one row through the card instead, at the middle
of the dump's rows, so it crosses the surface and never a corner.

**The surface's colour is the row's mode**, not one pixel: a row of text is mostly its ground.

**A measurement, not a gate.** It reports what it found and exits 0.

    python3 scripts/read_dialog_margin.py delete-dialog.png delete-dialog.xml 160   # left 24.0dp  right 24.0dp

Tests in read_dialog_margin_test.py.
"""

from __future__ import annotations

import collections
import pathlib
import sys

from PIL import Image
from pixel_reads import pixel_at
from read_layout_spacing import column_layouts

BASELINE_DPI = 160


def surface_margins_px(image_path: str, y: int) -> tuple[int, int]:
    """Pixels from the frame's left edge to the surface and from its right edge, on row `y`.

    A surface that fills the window reads (0, 0), which is the defect this exists to see.

    ```
    surface_margins_px("delete-dialog.png", 300)   # (24, 24)
    ```
    """
    image = Image.open(image_path).convert("RGB")
    width = image.size[0]
    row = [pixel_at(image, (x, y)) for x in range(width)]
    ground = collections.Counter(row[width // 4 : width - width // 4]).most_common(1)[0][0]
    on_surface = [x for x, pixel in enumerate(row) if pixel == ground]
    return on_surface[0], width - 1 - on_surface[-1]


def dialog_margins_dp(image_path: str, dump_path: str, density: int) -> tuple[float, float]:
    """The surface's margin to the window's left and right edges in dp, on the row between the dialog's first
    and last rows as the dump reports them.

    Args:
        image_path: The screenshot of the dialog.
        dump_path: The `uiautomator dump` of the same moment.
        density: The device's dpi, from `wm density`.

    ```
    dialog_margins_dp("delete-dialog.png", "delete-dialog.xml", 160)   # (24.0, 24.0)
    ```
    """
    rows = column_layouts(pathlib.Path(dump_path).read_text(), density)[0].rows
    middle_dp = (rows[0].box.top + rows[-1].box.bottom) / 2
    left, right = surface_margins_px(image_path, round(middle_dp * density / BASELINE_DPI))
    return round(left * BASELINE_DPI / density, 1), round(right * BASELINE_DPI / density, 1)


def main(argv: list[str]) -> int:
    image_path, dump_path, density = argv[1], argv[2], int(argv[3])
    left, right = dialog_margins_dp(image_path, dump_path, density)
    print(f"{image_path}  left {left}dp  right {right}dp")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
