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

**And what is dropped there is read by [part_seams], because P7a-2 is about exactly it.** VS Code
gives every part its own border id - `activityBar.border` *"separating to the side bar"* and
`sideBar.border` *"on the side separating to the editor"* (`workbench/common/theme.ts:417,623`),
both `#2A2B2C` / `#F0F1F2` in the 2026 themes. This app drew the first and not the second, and
[chrome_bands] alone could not fail on that: a border it deliberately discards is a border it
cannot report missing.

**A measurement, not a gate**, the same footing `read_pane_widths.py` and
`terminal_content_extent.py` stand on: a caller compares what it reads against what the resolved
theme says, and this module holds no expected value of its own.

    from read_chrome_parts import chrome_bands, part_seams
    rail, sidebar, page = chrome_bands("01-the-explorer.png")
    assert rail.colour == sidebar.colour != page.colour
    assert all("#2A2B2C" in seam.colours for seam in part_seams("01-the-explorer.png"))

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


@dataclasses.dataclass(frozen=True)
class Seam:
    """What is drawn between two parts: the gap it fills, and every colour in it.

    **A tuple rather than one colour, because a 1dp border does not land on the pixel grid.** The
    rail's own seam reads `('#2A2B2C', '#1E1E20')` on this project's tablet - one solid column of
    the border and one blended into the part behind it - so a caller asks whether the border is
    *in* the seam and never what the seam *is*.

    An empty [colours] is the finding this class exists for: two parts that abut have no border
    between them at all.
    """

    #: The x the part on the left ends at.
    after: int
    #: The x the part on the right starts at.
    before: int
    #: Every run of colour strictly between them, left to right.
    colours: tuple[str, ...]

    @property
    def width(self) -> int:
        return self.before - self.after - 1


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
    return [band for band in _runs(_column_colours(image_path)) if band.width >= _MIN_PART_WIDTH]


def part_seams(image_path: str) -> list[Seam]:
    """What separates each consecutive pair of parts, left to right - one [Seam] per pair.

    ```
    part_seams("01-the-file-browser.png")   # [Seam(119, 122, ('#2A2B2C', '#1E1E20')), Seam(661, 662, ())]
    ```
    """
    runs = _runs(_column_colours(image_path))
    parts = [at for at, run in enumerate(runs) if run.width >= _MIN_PART_WIDTH]
    return [
        Seam(runs[left].end, runs[right].start, tuple(run.colour for run in runs[left + 1:right]))
        for left, right in zip(parts, parts[1:])
    ]


def _column_colours(image_path: str) -> list[str]:
    """Every column's modal colour, left to right. Read once here so both callers agree."""
    with Image.open(image_path) as image:
        frame = image.convert("RGB")
        return [column_colour(frame, x) for x in range(frame.size[0])]


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
        for seam in part_seams(path):
            drawn = " ".join(seam.colours) if seam.colours else "NOT DRAWN"
            print(f"  seam at x{seam.after + 1:<5} {seam.width:>5}px  {drawn}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
