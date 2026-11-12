#!/usr/bin/env python3
"""How far a terminal's text sits in from its pane, and what pitch it is drawn at against the columns told.

Read out of one screenshot of a ruler row (a line of `#` as wide as the pty's own column count) and the
`uiautomator dump` taken with it. The pane is the node `terminal-input`; the ruler is the widest row of
ink inside it, so a prompt above or a shorter line below cannot stand in for it. Its first and last ink
columns give the gap to each edge of the pane in dp, and the start of each glyph gives the pitch they are
drawn at, which is not the width of the cell the columns were counted with when the two differ.

`columns_at_pitch` is `floor((pane width - 2 x margin) / pitch)`. Beside it sits `reported_columns`, what
`os.get_terminal_size` answered on the pty (`TIOCGWINSZ`, the one live answer this rootfs gives,
`docs/research/closed-questions.md` CLOSED-006), which `terminalGridSize` computes from the same width
and the cell's whole-pixel width (`ui/terminal/TerminalGridSize.kt`). They agree only if the glyphs are
drawn a whole cell apart.

The gaps include the `#` glyph's own side bearing, a pixel or two, so a margin of 12dp reads a little over it.

**A measurement, not a gate.** It prints what it found and exits 0.

    adb shell uiautomator dump /sdcard/ui.xml && adb exec-out cat /sdcard/ui.xml > window.xml
    python3 scripts/read_terminal_margin.py ruler.png window.xml 240 104

Tests in read_terminal_margin_test.py.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass

from PIL import Image
from pixel_reads import pixel_at

BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")
#: `TERMINAL_INPUT_TAG` in `ui/terminal/TerminalPaneScreen.kt`: the node the canvas and the gestures sit in.
PANE_TAG = "terminal-input"
#: `terminalMargin` in `ui/terminal/TerminalGridSize.kt`, which is `Spacing.md`.
MARGIN_DP = 12.0
#: Material's baseline density, at which one dp is one pixel.
BASELINE_DPI = 160.0
#: How far a pixel's R+G+B sum must sit from the pane's background to be ink, as `terminal_content_extent`.
_CONTRAST_THRESHOLD = 200


@dataclass(frozen=True)
class MarginReading:
    """What one ruler frame says about the margin and the grid."""

    left_gap_dp: float
    right_gap_dp: float
    pitch_px: float
    pane_width_px: int
    columns_at_pitch: int
    reported_columns: int


def read_terminal_margin(
    image_path: str,
    hierarchy_xml: str,
    density: int,
    reported_columns: int,
    margin_dp: float = MARGIN_DP,
) -> MarginReading:
    """The gaps, the drawn pitch and the columns that pitch fits, for one ruler frame.

    Args:
        image_path: A screenshot with the ruler row drawn inside the pane.
        hierarchy_xml: The `uiautomator dump` taken with it.
        density: The device's dpi, from `wm density`.
        reported_columns: The pty's own column count, `os.get_terminal_size(0)[0]`.
        margin_dp: The margin the pane is built with.

    Raises:
        ValueError: If the ruler's glyph count is not [reported_columns], so it is not the row asked for.

    ```
    read_terminal_margin("ruler.png", open("window.xml").read(), 240, 104).left_gap_dp  # -> 12.7
    ```
    """
    pane = _pane_bounds(hierarchy_xml)
    starts, last = _ruler_glyphs(Image.open(image_path).convert("RGB"), pane)
    if len(starts) != reported_columns:
        raise ValueError(f"the ruler has {len(starts)} glyphs; expected the {reported_columns} columns reported")
    px_per_dp = density / BASELINE_DPI
    pitch = (starts[-1] - starts[0]) / (len(starts) - 1)
    inside = pane[2] - pane[0] - 2 * round(margin_dp * px_per_dp)
    return MarginReading(
        left_gap_dp=(starts[0] - pane[0]) / px_per_dp,
        right_gap_dp=(pane[2] - last - 1) / px_per_dp,
        pitch_px=pitch,
        pane_width_px=pane[2] - pane[0],
        columns_at_pitch=int(inside // pitch),
        reported_columns=reported_columns,
    )


def _pane_bounds(hierarchy_xml: str) -> tuple[int, int, int, int]:
    """`(left, top, right, bottom)` of the node [PANE_TAG], in px."""
    for node in ElementTree.fromstring(hierarchy_xml).iter("node"):
        found = BOUNDS.match(node.get("bounds", ""))
        if node.get("resource-id", "").endswith(PANE_TAG) and found:
            left, top, right, bottom = (int(edge) for edge in found.groups())
            return left, top, right, bottom
    raise LookupError(f"no node with resource-id {PANE_TAG!r} and bounds in the dump; expected a terminal screen")


def _ruler_glyphs(image: Image.Image, pane: tuple[int, int, int, int]) -> tuple[list[int], int]:
    """The first ink x of each glyph of the ruler and its last ink x, inclusive, inside [pane].

    The ruler is the widest row of ink; its glyph rows are the run of rows around it whose ink spans at
    least half as far, since a `#` is drawn over several. A glyph starts where a column with ink follows
    one without.
    """
    left, top, right, bottom = pane
    background = sum(pixel_at(image, ((left + right) // 2, bottom - 2)))
    extents = {y: _ink_extent(image, y, left, right, background) for y in range(top, bottom)}
    inked = {y: extent for y, extent in extents.items() if extent}
    if not inked:
        raise ValueError(f"no ink inside the pane {pane}; expected the ruler row drawn there")
    widest = max(inked, key=lambda y: inked[y][1] - inked[y][0])
    rows = _rows_around(widest, inked)
    inked_columns = {x for y in rows for x in range(left, right) if _is_ink(image, (x, y), background)}
    starts = sorted(x for x in inked_columns if x - 1 not in inked_columns)
    return starts, max(inked_columns)


def _ink_extent(image: Image.Image, y: int, left: int, right: int, background: int) -> tuple[int, int] | None:
    """`(first, last)` ink x of row [y], or `None` for a row with none."""
    inked = [x for x in range(left, right) if _is_ink(image, (x, y), background)]
    return (inked[0], inked[-1]) if inked else None


def _rows_around(widest: int, inked: dict[int, tuple[int, int]]) -> list[int]:
    """The consecutive rows around [widest] whose ink spans at least half as far as its own."""
    half = (inked[widest][1] - inked[widest][0]) / 2
    rows = [widest]
    for step in (-1, 1):
        y = widest + step
        while y in inked and inked[y][1] - inked[y][0] >= half:
            rows.append(y)
            y += step
    return rows


def _is_ink(image: Image.Image, xy: tuple[int, int], background: int) -> bool:
    return abs(sum(pixel_at(image, xy)) - background) > _CONTRAST_THRESHOLD


def main() -> int:
    if len(sys.argv) != 5:
        print(__doc__)
        return 2
    image_path, hierarchy, density, columns = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
    reading = read_terminal_margin(image_path, open(hierarchy).read(), density, columns)
    print(f"pane {reading.pane_width_px}px, glyphs drawn {reading.pitch_px:.2f}px apart")
    print(f"  left gap  {reading.left_gap_dp:6.1f}dp   right gap {reading.right_gap_dp:6.1f}dp")
    print(f"  columns   at that pitch {reading.columns_at_pitch}   reported {reading.reported_columns}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
