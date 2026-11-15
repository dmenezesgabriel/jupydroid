#!/usr/bin/env python3
"""What each column of the shell leaves around and between its rows, read out of a `uiautomator dump`.

`read_pane_widths.py` says how wide a column is and `check_ui_dimensions.py` gates the `.dp` literals in
the source against `theme/Spacing.kt`. Neither says what *landed*: a literal can be on the scale and the
row it sits in still ends up 2dp from its neighbour once a parent adds its own padding. This reads the
result, in dp, off the node bounds the accessibility tree reports.

Four things per column, left to right:

- **Rows.** The innermost nodes at least 90% as wide as their column and no taller than `MAX_ROW_DP`
  that draw something (a text, a `content-desc`) or hold something that does. Two nodes with the same
  bounds are one row, and a row that holds another row is not one, so a header and the inset above it
  read as the header.
- **Insets.** From a row's left edge to the first thing drawn in it, and from its right edge to the last
  thing drawn in its right half - a trailing control; text that runs left to right has none - or `None`
  for a side with nothing there. A clickable's text is reported with the clickable's own bounds, so a
  label that is its own touch target reads inset 0 here whatever its glyphs sit at.
- **Gaps.** From one row's bottom to the next row's top, with the `Spacing` step each equals.
- **Blank bands.** Runs of a column's height, from its first row to the bar across the foot of the
  window, that no row and no loose text covers and that are at least `MIN_BAND_DP` tall.

**A measurement, not a gate.** It reports what it found and exits 0, because what a number means - a
margin that is deliberate, an empty state that is meant to be empty - is for the verdict to say.

    adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml > ui.xml
    python3 scripts/read_layout_spacing.py ui.xml 240 [other.xml ...]

Any further dump is compared with the first on geometry alone - the six theme combinations lay out
the same, and this is what says so, or names the row that moved.

Tests in read_layout_spacing_test.py.
"""

from __future__ import annotations

import dataclasses
import pathlib
import re
import sys
import xml.etree.ElementTree as ElementTree
from typing import NamedTuple

from read_pane_widths import BASELINE_DPI, BOUNDS, shell_columns

#: The file that defines the gaps the app draws; the scale is read from it so no number is copied here.
SPACING_SOURCE = "app/src/main/java/com/jupydroid/app/theme/Spacing.kt"
SPACING_STEP = re.compile(r"val\s+(\w+):\s*Dp\s*=\s*(\d+(?:\.\d+)?)\.dp")
#: A row spans nearly its whole column: a control inside one is not a row.
ROW_WIDTH_SHARE = 0.9
#: A node taller than this is a region holding rows (a list, a scroll view), not a row.
MAX_ROW_DP = 100.0
#: One pixel at 240 dpi is 0.67dp, rounded up: a value this close to a step is that step, drawn in pixels.
STEP_TOLERANCE_DP = 0.75
#: A divider: Compose's default is 1dp, which two pixels at 240 dpi make 1.33dp. Under this and over a pixel is a line.
HAIRLINE_DP = 2.0
#: A run shorter than `Spacing.xl`, "between sections of one surface", is a gap between rows, not unused space.
MIN_BAND_DP = 24.0
#: A bar across the window at most this share of its height is chrome the columns sit above (the navigation bar).
BAR_HEIGHT_SHARE = 0.25
#: Pixels divided by a density are not exact, so two edges that are one edge differ in the last digits.
_EPSILON = 1e-6


class Box(NamedTuple):
    """A node's bounds in dp: left, top, right, bottom."""

    left: float
    top: float
    right: float
    bottom: float

    @property
    def width(self) -> float:
        return round(self.right - self.left, 1)

    @property
    def height(self) -> float:
        return round(self.bottom - self.top, 1)

    def contains(self, other: Box) -> bool:
        """Whether [other] lies inside this box, borders touching included, to within a float's rounding."""
        return (
            self.left - _EPSILON <= other.left
            and other.right <= self.right + _EPSILON
            and self.top - _EPSILON <= other.top
            and other.bottom <= self.bottom + _EPSILON
        )


@dataclasses.dataclass(frozen=True)
class Node:
    """One node of a dump: where it is and whether it draws anything a reader could name."""

    box: Box
    drawn: bool
    clickable: bool = False


@dataclasses.dataclass(frozen=True)
class RowLayout:
    """A row, and how far what is drawn in it sits from its left edge and from its right (`None`: nothing there)."""

    box: Box
    left_inset: float | None
    right_inset: float | None


@dataclasses.dataclass(frozen=True)
class ColumnLayout:
    """One column: its span, its rows top to bottom, what covers it and where its usable height ends.

    [edge_margins] is set only for a popup surface: how far its first row is from its top edge and its last
    row from its bottom edge, which are its padding rather than unused space.
    """

    left: float
    right: float
    rows: list[RowLayout]
    covered: list[tuple[float, float]]
    floor: float
    edge_margins: tuple[float, float] | None = None


def load_spacing_scale(source: pathlib.Path) -> dict[str, float]:
    """The gap scale `theme/Spacing.kt` defines, as step name to dp.

    Raises:
        FileNotFoundError: When [source] is not there, naming it.
        ValueError: When the file defines no step, which means the format changed under this reader.

    ```
    load_spacing_scale(pathlib.Path(SPACING_SOURCE))  # -> {"xs": 4.0, "sm": 8.0, ..., "xxl": 32.0}
    ```
    """
    steps = {name: float(value) for name, value in SPACING_STEP.findall(source.read_text())}
    if not steps:
        raise ValueError(f"no 'val <name>: Dp = <n>.dp' in {source}; expected the Spacing object's steps")
    return steps


def spacing_step(value_dp: float, scale: dict[str, float]) -> str:
    """The step [value_dp] equals within a pixel; `none` for zero, `hairline` for a divider, else `off-scale`.

    ```
    spacing_step(16.0, {"lg": 16.0})  # -> "lg"
    ```
    """
    if abs(value_dp) < STEP_TOLERANCE_DP:
        return "none"
    if abs(value_dp) < HAIRLINE_DP:
        return "hairline"
    for name, step in scale.items():
        if abs(value_dp - step) <= STEP_TOLERANCE_DP:
            return name
    return "off-scale"


def column_layouts(xml_text: str, density: int) -> list[ColumnLayout]:
    """The shell's columns, left to right, each with the rows and the coverage this module reads.

    A dump with no node as tall as the window - a dialog's window, an overlay - has no shell in it, and is
    read as one popup column instead (`_popup_layout`). A column the width rule finds no row in is read by
    its bands (`_band_rows`).

    Args:
        xml_text: One `uiautomator dump`.
        density: The device's dpi, from `wm density`.

    ```
    column_layouts(open("ui.xml").read(), 240)[1].rows[0].left_inset  # -> 16.0
    ```
    """
    nodes = _nodes(xml_text, density)
    spans = shell_columns(xml_text, density)
    window = _largest_box(nodes)
    if not spans:
        return [_popup_layout(nodes, window)]
    floor = _foot_of(nodes, window)
    layouts = []
    for left, right in spans:
        rows = _rows_in(nodes, left, right) or _band_rows(nodes, Box(left, window.top, right, window.bottom))
        layouts.append(ColumnLayout(left, right, rows, _covered(nodes, rows, left, right, window), floor))
    return layouts


def row_gaps(rows: list[RowLayout]) -> list[float]:
    """The space from each row's bottom to the next row's top, in dp, top to bottom.

    ```
    row_gaps(column_layouts(open("ui.xml").read(), 240)[0].rows)  # -> [4.0, 4.0, ...]
    ```
    """
    return [round(below.box.top - above.box.bottom, 1) for above, below in zip(rows, rows[1:], strict=False)]


def layout_differences(reference: list[ColumnLayout], other: list[ColumnLayout]) -> list[str]:
    """Where [other]'s rows are not [reference]'s, one printable line per row, empty when the layouts agree.

    Only geometry is compared, so a label that reads differently in the same bounds is the same layout.

    ```
    layout_differences(column_layouts(light, 240), column_layouts(dark, 240))  # -> []
    ```
    """
    if len(reference) != len(other):
        return [f"{len(reference)} columns against {len(other)}"]
    found: list[str] = []
    for at, (ours, theirs) in enumerate(zip(reference, other, strict=True)):
        found += [f"column {at}: {box[0]} only in the reference" for box in sorted(_boxes(ours) - _boxes(theirs))]
        found += [f"column {at}: {box[0]} only in the other" for box in sorted(_boxes(theirs) - _boxes(ours))]
    return found


def _boxes(column: ColumnLayout) -> set[tuple[Box, float | None, float | None]]:
    return {(row.box, row.left_inset, row.right_inset) for row in column.rows}


def blank_bands(column: ColumnLayout) -> list[tuple[float, float]]:
    """The runs of [column]'s height that nothing covers, from its first row to its floor, as (top, bottom).

    ```
    blank_bands(column_layouts(open("ui.xml").read(), 240)[1])  # -> [(331.3, 752.0)]
    ```
    """
    bands: list[tuple[float, float]] = []
    reached = column.covered[0][0] if column.covered else column.floor
    for top, bottom in column.covered:
        if top - reached >= MIN_BAND_DP:
            bands.append((reached, top))
        reached = max(reached, bottom)
    if column.floor - reached >= MIN_BAND_DP:
        bands.append((reached, column.floor))
    return bands


def _nodes(xml_text: str, density: int) -> list[Node]:
    scale = density / BASELINE_DPI
    nodes = []
    for element in ElementTree.fromstring(xml_text).iter("node"):
        found = BOUNDS.match(element.get("bounds", ""))
        if not found:
            continue
        box = Box(*(int(at) / scale for at in found.groups()))
        drawn = bool(element.get("text") or element.get("content-desc"))
        nodes.append(Node(box, drawn, element.get("clickable") == "true"))
    return nodes


def _largest_box(nodes: list[Node]) -> Box:
    return max((node.box for node in nodes), key=lambda box: box.width * box.height)


def _popup_layout(nodes: list[Node], surface: Box) -> ColumnLayout:
    """A popup as one column of its bands; the padding to its first and last row is its margins, not blank."""
    rows = _band_rows(nodes, surface)
    margins = (rows[0].box.top - surface.top, surface.bottom - rows[-1].box.bottom) if rows else None
    floor = rows[-1].box.bottom if rows else surface.bottom
    left, right = round(surface.left, 1), round(surface.right, 1)
    return ColumnLayout(left, right, rows, [(row.box.top, row.box.bottom) for row in rows], floor, _rounded(margins))


def _band_rows(nodes: list[Node], area: Box) -> list[RowLayout]:
    """The rows of [area] as bands of its height that something drawn or pressable fills, each as wide as it.

    Rows with no common width - a dialog's title, sentence and pair of buttons, a drawer whose items wrap their
    labels - are found by no rule about width. A band is every node that overlaps the one above it vertically,
    which makes two buttons side by side one row, spanning [area] so its insets are the padding around them.
    A pressable node counts as drawn here: a button's touch box, not its label, is what a spec measures.
    """
    content = [
        Node(node.box, True) for node in nodes if (node.drawn or node.clickable) and _strictly_in(area, node.box)
    ]
    strips = [Box(area.left, top, area.right, bottom) for top, bottom in _vertical_bands(content)]
    return [_row_layout(strip, content) for strip in strips]


def _rounded(margins: tuple[float, float] | None) -> tuple[float, float] | None:
    return None if margins is None else (round(margins[0], 1), round(margins[1], 1))


def _vertical_bands(content: list[Node]) -> list[tuple[float, float]]:
    """The (top, bottom) runs that nodes cover, where one that starts above the run's end joins it."""
    bands: list[tuple[float, float]] = []
    for node in sorted(content, key=lambda node: node.box.top):
        if bands and node.box.top < bands[-1][1] - _EPSILON:
            bands[-1] = (bands[-1][0], max(bands[-1][1], node.box.bottom))
        else:
            bands.append((node.box.top, node.box.bottom))
    return bands


def _foot_of(nodes: list[Node], window: Box) -> float:
    """Where the usable height ends: the top of a bar across the window's foot, else the window's bottom."""
    bars = [
        node.box.top
        for node in nodes
        if node.box.width >= window.width - 1
        and node.box.height <= window.height * BAR_HEIGHT_SHARE
        and node.box.top > window.height / 2
    ]
    return min(bars, default=window.bottom)


def _rows_in(nodes: list[Node], left: float, right: float) -> list[RowLayout]:
    column = Box(left, 0.0, right, float("inf"))
    candidates = [
        node
        for node in nodes
        if column.contains(node.box)
        and node.box.width >= (right - left) * ROW_WIDTH_SHARE
        and node.box.height <= MAX_ROW_DP
    ]
    holders = {node.box for node in candidates if any(_drawn_in(node.box, other) for other in nodes)}
    innermost = [box for box in holders if not any(box != other and box.contains(other) for other in holders)]
    lone = {node.box for node in candidates if node.drawn and not any(_strictly_in(h, node.box) for h in holders)}
    return [_row_layout(box, nodes) for box in sorted(set(innermost) | lone, key=lambda box: (box.top, box.left))]


def _strictly_in(outer: Box, inner: Box) -> bool:
    return outer != inner and outer.contains(inner)


def _drawn_in(row: Box, other: Node) -> bool:
    return other.drawn and _strictly_in(row, other.box)


def _row_layout(row: Box, nodes: list[Node]) -> RowLayout:
    inside = [node.box for node in nodes if _drawn_in(row, node)]
    if not inside:
        return RowLayout(row, None, None)
    trailing = [box for box in inside if box.left >= (row.left + row.right) / 2]
    last = round(row.right - max(box.right for box in trailing), 1) if trailing else None
    return RowLayout(row, round(min(box.left for box in inside) - row.left, 1), last)


def _covered(
    nodes: list[Node], rows: list[RowLayout], left: float, right: float, window: Box
) -> list[tuple[float, float]]:
    """Each row's height, and the height of any loose drawn node that is no part of a row, by top."""
    column = Box(left, 0.0, right, float("inf"))
    spans = [(row.box.top, row.box.bottom) for row in rows]
    for node in nodes:
        loose = node.drawn and column.contains(node.box) and node.box.height < window.height / 2
        if loose and not any(row.box.contains(node.box) for row in rows):
            spans.append((node.box.top, node.box.bottom))
    return sorted(spans)


def _report(column: ColumnLayout, scale: dict[str, float]) -> list[str]:
    lines = [f"column {column.left:.1f}..{column.right:.1f}dp  width {column.right - column.left:.1f}dp"]
    gaps = [*row_gaps(column.rows), None][: len(column.rows)]
    for row, gap in zip(column.rows, gaps, strict=True):
        lines.append(_row_line(row, gap, scale))
    if column.edge_margins is not None:
        lines.append(_margin_line(column.edge_margins, scale))
    lines += [f"  blank {top:.1f}..{bottom:.1f}dp  ({bottom - top:.1f}dp)" for top, bottom in blank_bands(column)]
    return lines


def _margin_line(margins: tuple[float, float], scale: dict[str, float]) -> str:
    top, bottom = margins
    return f"  margin top {top:g} ({spacing_step(top, scale)}) bottom {bottom:g} ({spacing_step(bottom, scale)})"


def _row_line(row: RowLayout, gap: float | None, scale: dict[str, float]) -> str:
    insets = "no inner content" if row.left_inset is None else _inset_text(row.left_inset, row.right_inset, scale)
    below = "" if gap is None else f"  gap below {gap:g} ({spacing_step(gap, scale)})"
    return f"  row {row.box.top:7.1f}..{row.box.bottom:7.1f}  h {row.box.height:5.1f}  {insets}{below}"


def _inset_text(left: float, right: float | None, scale: dict[str, float]) -> str:
    trailing = "no trailing control" if right is None else f"R {right:g} ({spacing_step(right, scale)})"
    return f"inset L {left:g} ({spacing_step(left, scale)}) {trailing}"


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__)
        return 2
    density = int(argv[2])
    scale = load_spacing_scale(pathlib.Path(__file__).parent.parent / SPACING_SOURCE)
    reference = column_layouts(pathlib.Path(argv[1]).read_text(), density)
    for column in reference:
        print("\n".join(_report(column, scale)))
    for other in argv[3:]:
        differences = layout_differences(reference, column_layouts(pathlib.Path(other).read_text(), density))
        print(f"{other}: {'same rows' if not differences else '; '.join(differences)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
