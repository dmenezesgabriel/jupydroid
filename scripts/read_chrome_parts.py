#!/usr/bin/env python3
"""Which colour each vertical part of the shell is actually drawn in, read off a screenshot.

`docs/PLAN.md`'s P7a. VS Code draws the workbench as parts a step apart: `activityBar.background`
and `sideBar.background` resolve to `#191A1B` in 2026 Dark and `editor.background` to `#121314`,
with a 1px `panel.border` between them. This app drew all three on the page's own colour, and
**no assertion in the repository could fail on it** - a colour is not something a Maestro flow
can read. Reading the frame's own pixels is what turns that into something a driver can gate on.

**The mode down a column, never one pixel of it.** A row label, a breadcrumb or an icon puts
foreground ink in a column that is otherwise a part's background; sampling a single scanline
reads whichever of the two that row happened to hit, which is how a pixel oracle in this
repository has already been wrong twice (`docs/DECISIONS.md`, P4j-3c and P4j-3d-3). Text is a
minority of any column it appears in, so the mode is the part behind it.

**Bands narrower than [_MIN_PART_WIDTH] are not parts.** A 1-2px seam between two parts is a
border - the thing that *separates* parts - and reporting it as a third one would make the
oracle's own output depend on whether a border happened to be drawn.

**A measurement, not a gate**, the same footing `read_pane_widths.py` and
`terminal_content_extent.py` stand on: a caller compares what it reads against what the resolved
theme says, and this module holds no expected value of its own.

    from read_chrome_parts import chrome_bands
    rail, sidebar, page = chrome_bands("01-the-explorer.png")
    assert rail.colour == sidebar.colour != page.colour

Tests in read_chrome_parts_test.py, including one that reads the committed *before* frame and
asserts the defect is visible in it - an oracle that cannot see the bug cannot see the fix.
"""
from __future__ import annotations

import collections
import dataclasses
import sys

from PIL import Image

#: Narrower than this and it is a seam between parts rather than a part. The rail is the narrowest
#: real part at ~120px on this project's tablet, and a `strokeThickness` border is 1-2px.
_MIN_PART_WIDTH = 24
#: The vertical window sampled, as fractions of height: below the title bars and above any bottom
#: bar, so a column is mostly the part's own background rather than chrome belonging to something else.
_Y_WINDOW = (0.45, 0.85)
#: How many rows of that window to sample per column. Enough for a stable mode, few enough that a
#: 1920x1200 frame is read in well under a second without numpy.
_SAMPLES = 40


@dataclasses.dataclass(frozen=True)
class Band:
    """One part of the shell: where it starts and ends horizontally, and what it is drawn in."""

    start: int
    end: int
    colour: str

    @property
    def width(self) -> int:
        return self.end - self.start + 1


def column_colour(image: Image.Image, x: int) -> str:
    """The modal colour of column `x` inside [_Y_WINDOW], as `#RRGGBB`.

    ```
    column_colour(Image.open("01-the-explorer.png"), 60)   # '#191A1B'
    ```
    """
    height = image.size[1]
    top, bottom = int(height * _Y_WINDOW[0]), int(height * _Y_WINDOW[1])
    step = max(1, (bottom - top) // _SAMPLES)
    counts = collections.Counter(image.getpixel((x, y))[:3] for y in range(top, bottom, step))
    red, green, blue = counts.most_common(1)[0][0]
    return f"#{red:02X}{green:02X}{blue:02X}"


def chrome_bands(image_path: str) -> list[Band]:
    """The screenshot's vertical parts, left to right, seams and text excluded.

    ```
    chrome_bands("01-the-explorer.png")   # [Band(0, 119, '#191A1B'), …, Band(122, 699, '#121314')]
    ```
    """
    with Image.open(image_path) as image:
        frame = image.convert("RGB")
        colours = [column_colour(frame, x) for x in range(frame.size[0])]
    return [band for band in _runs(colours) if band.width >= _MIN_PART_WIDTH]


def _runs(colours: list[str]) -> list[Band]:
    """Every maximal run of one colour, including the narrow ones a caller then drops."""
    bands: list[Band] = []
    start = 0
    for x in range(1, len(colours) + 1):
        if x == len(colours) or colours[x] != colours[start]:
            bands.append(Band(start, x - 1, colours[start]))
            start = x
    return bands


def main() -> int:
    for path in sys.argv[1:]:
        print(path)
        for band in chrome_bands(path):
            print(f"  x{band.start:>5}-{band.end:<5} {band.width:>5}px  {band.colour}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
