#!/usr/bin/env python3
"""The WCAG contrast of every piece of text on a screen, read off a frame and its dump.

`read_chrome_parts.py` reads the colour of each part and `read_node_fill.py` the fill of one named
node; **nothing read the colour of a node's text against what it is drawn on**, so a label could sit
under 4.5:1 in a theme and no assertion here could fail on it. A contrast ratio is not something a
Maestro flow can read either: the accessibility tree has the text and where it is, never its colour.

**Ground is the mode inside the node's bounds, ink is the pixel that contrasts most with it.** Text
is a minority of the box it sits in, so the mode is what the text is drawn on - the rule
`read_node_fill.py` and `read_chrome_parts.py` already stand on, after a pixel oracle in this
repository was wrong twice by reading one pixel (`docs/architecture/cross-cutting.md` §8.6).

**The read is conservative, never generous.** A thin glyph whose anti-aliased stroke never reaches
the full ink colour reads a little under its true ratio. A caller that finds a pair under the floor
looks at the frame before calling it a defect; one that finds a pair over it can trust that.

**Text nodes only.** A node is read when its `text` is non-empty and it covers pixels of the frame.
An icon has no text in the dump; which of those are decorative and which unnamed is a separate read.

**A measurement, not a gate**, the footing `read_node_fill.py` and `read_pane_widths.py` stand on: it
prints what it found and exits 0, and the floor (4.5:1 for text) belongs to the caller, because an
accepted exception such as ADR-0041's `constant.numeric` is a verdict and not a property of a frame.
`--below <ratio>` lists only the pairs under that ratio and ends with how many of the text nodes that
was, which is how a driver reads many frames without printing every node of each.

    adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml hierarchy.xml
    python3 scripts/read_text_contrast.py hierarchy.xml frame.png
    python3 scripts/read_text_contrast.py hierarchy.xml frame.png --below 4.5
    python3 scripts/read_text_contrast.py --status-bar frame.png
    python3 scripts/read_text_contrast.py --status-bar frame.png window.txt   # `adb shell dumpsys window`

Tests in read_text_contrast_test.py, including a run over this repository's own committed device
frames, with ground and ink sampled by hand rather than by the module.
"""

from __future__ import annotations

import dataclasses
import sys
import xml.etree.ElementTree as ElementTree

from crop_system_bars import read_system_bar_insets
from PIL import Image
from read_node_fill import BOUNDS, frame_scale, screen_of
from resolve_vscode_theme import contrast_ratio

#: The rows of the frame the system status bar's icons are drawn in, below its top edge and above
#: the app. It spans the frame's full width. Tuned on the tablet's 45px bar: a caller on any other
#: device passes the bar's own height instead (`bar_px`), since a phone's is 24px at 160dpi.
STATUS_BAR_ROWS = (4, 40)
PixelBox = tuple[int, int, int, int]


@dataclasses.dataclass(frozen=True)
class PairReading:
    """What a region is drawn on, what is drawn on it, and the WCAG ratio between the two."""

    ground: str
    ink: str
    ratio: float


@dataclasses.dataclass(frozen=True)
class NodeReading:
    """One text node: what it says, where it sits in the frame's pixels, and its pair."""

    text: str
    box: PixelBox
    reading: PairReading


def region_contrast(frame: Image.Image, box: PixelBox) -> PairReading:
    """The ground and ink inside [box] of [frame], and the contrast between them.

    Args:
        frame: Any capture of the screen.
        box: `(left, top, right, bottom)` in the frame's own pixels.

    Raises:
        ValueError: When [box] covers no pixel of [frame].

    ```
    region_contrast(Image.open("frame.png"), (16, 42, 84, 70))  # PairReading('#191A1B', '#BBBEBF', 9.32)
    ```
    """
    clamped = _clamped(box, frame.size)
    if clamped[2] <= clamped[0] or clamped[3] <= clamped[1]:
        raise ValueError(
            f"box {box} covers no pixel of the {frame.size[0]}x{frame.size[1]} frame; expected left<right, top<bottom"
        )
    region = frame.convert("RGB").crop(clamped)
    counted = _colour_counts(region)
    ground = _hex(max(counted)[1])
    ink = max((_hex(colour) for _, colour in counted), key=lambda candidate: contrast_ratio(ground, candidate))
    return PairReading(ground, ink, contrast_ratio(ground, ink))


def status_bar_contrast(frame: Image.Image, bar_px: int | None = None) -> PairReading:
    """The system status bar's icons against its background, across the frame's full width.

    Args:
        frame: A full-screen capture.
        bar_px: The bar's height as the device reports it (`read_system_bar_insets(...).top_px`). The
            band is the bar less a tenth of it at each edge, so the app's own top bar is never read as
            the system's. Without it, the band is `STATUS_BAR_ROWS`.

    Raises:
        ValueError: When [bar_px] is not positive, as with a hidden bar.

    ```
    status_bar_contrast(Image.open("frame.png"), bar_px=24)  # PairReading('#191A1B', '#FFFFFF', 17.4)
    ```
    """
    if bar_px is not None and bar_px <= 0:
        raise ValueError(f"status bar height {bar_px}px draws no rows; expected a visible bar, 1px or taller")
    top, bottom = STATUS_BAR_ROWS if bar_px is None else (bar_px // 10, bar_px - bar_px // 10)
    return region_contrast(frame, (0, top, frame.size[0], bottom))


def text_contrasts(xml_text: str, image_path: str) -> list[NodeReading]:
    """Every text node of a dump read against its own bounds in a frame, lowest ratio first.

    Args:
        xml_text: One `uiautomator dump`.
        image_path: A frame of the same screen, at any scale of it.

    Raises:
        ValueError: When the frame is not a scale of the dump's own space, which means two screens.

    ```
    text_contrasts(open("hierarchy.xml").read(), "frame.png")[0].reading.ratio  # -> 5.18
    ```
    """
    tree = ElementTree.fromstring(xml_text)
    with Image.open(image_path) as opened:
        frame = opened.convert("RGB")
    scale = _scale_of(tree, frame.size)
    found = [_read_node(frame, node, scale) for node in tree.iter("node") if node.get("text")]
    return sorted((reading for reading in found if reading), key=lambda at: at.reading.ratio)


def _scale_of(tree: ElementTree.Element, frame_size: tuple[int, int]) -> float:
    """How much larger the frame is than the dump's space, which a popup window's dump cannot say.

    A popup (the notebook's table-of-contents overlay) is a window of its own, and `uiautomator dump`
    of it states only the popup's corner of the display, so its right and bottom edges are not the
    display's. A dump that does not start at the origin is read at the frame's own scale, which is the
    display's: the tablet's `screencap` is at native size. One that reaches past the frame is refused.

    ```
    _scale_of(ElementTree.fromstring(popup_dump), (1200, 1920))  # -> 1.0
    ```
    """
    left, top = _outermost_origin(tree)
    right, bottom = screen_of(tree)
    if (left, top) == (0, 0):
        return frame_scale(frame_size, (right, bottom))
    if right > frame_size[0] or bottom > frame_size[1]:
        raise ValueError(
            f"popup window reaching {right}x{bottom} does not fit a frame of {frame_size[0]}x{frame_size[1]}; "
            "the two captures are of different screens"
        )
    return 1.0


def _outermost_origin(tree: ElementTree.Element) -> tuple[int, int]:
    """The top-left corner the outermost node that states bounds gives, as `screen_of` finds its far one."""
    for node in tree.iter("node"):
        match = BOUNDS.match(node.get("bounds", ""))
        if match:
            return int(match.group(1)), int(match.group(2))
    raise LookupError('no node in the dump states bounds; expected one with bounds="[0,0][w,h]"')


def _read_node(frame: Image.Image, node: ElementTree.Element, scale: float) -> NodeReading | None:
    """The node's reading, or nothing when it has no bounds or none of it is on the frame."""
    match = BOUNDS.match(node.get("bounds", ""))
    if not match:
        return None
    box = _clamped(tuple(round(int(group) * scale) for group in match.groups()), frame.size)  # type: ignore[arg-type]
    if box[2] <= box[0] or box[3] <= box[1]:
        return None
    return NodeReading(node.get("text", ""), box, region_contrast(frame, box))


def _clamped(box: PixelBox, size: tuple[int, int]) -> PixelBox:
    """[box] cut to the frame, because a node scrolled partly off screen reports bounds past its edge."""
    return max(box[0], 0), max(box[1], 0), min(box[2], size[0]), min(box[3], size[1])


def _colour_counts(region: Image.Image) -> list[tuple[int, tuple[int, ...]]]:
    """How many pixels of [region] are each colour; Pillow types the colour as maybe-a-number."""
    counted = region.getcolors(region.size[0] * region.size[1])
    if counted is None:
        raise ValueError(f"region of {region.size[0]}x{region.size[1]} has no colour count; expected an RGB crop")
    return [(count, colour) for count, colour in counted if isinstance(colour, tuple)]


def _hex(rgb: tuple[int, ...]) -> str:
    return "#{:02X}{:02X}{:02X}".format(*rgb[:3])


USAGE = (
    "usage: read_text_contrast.py <hierarchy.xml> <frame.png> [--below <ratio>]"
    " | --status-bar <frame.png> [<dumpsys window text>]"
)


def _print_reading(at: NodeReading) -> None:
    print(f"{at.reading.ratio:>6.2f}:1  ground {at.reading.ground}  ink {at.reading.ink}  {at.text!r}")


def _print_below(readings: list[NodeReading], floor: float) -> None:
    """The pairs under [floor], the nodes with no ink to read, then the counts, for a caller reading many frames.

    A node a scroll container clips to a sliver holds one colour, so its ratio is 1.00:1 whatever its
    text is drawn in. That is not a pair under the floor, and it is named apart so a person looks at it.
    """
    blank = [at for at in readings if at.reading.ground == at.reading.ink]
    under = [at for at in readings if at.reading.ratio < floor and at not in blank]
    for at in under:
        _print_reading(at)
    for at in blank:
        print(f"  no ink in view  {at.text!r}")
    suffix = f", {len(blank)} with no ink in view" if blank else ""
    print(f"  {len(under)} of {len(readings)} text nodes under {floor:g}:1{suffix}")


def _parse_floor(raw: str) -> float | None:
    try:
        return float(raw)
    except ValueError:
        print(f"--below {raw!r} is not a ratio; expected a number such as 4.5", file=sys.stderr)
        return None


def _print_status_bar(frame_path: str, window_path: str | None) -> None:
    bar_px = None
    if window_path is not None:
        with open(window_path, encoding="utf-8") as window:
            bar_px = read_system_bar_insets(window.read()).top_px
    with Image.open(frame_path) as frame:
        reading = status_bar_contrast(frame, bar_px)
    print(f"  status bar ground {reading.ground}  ink {reading.ink}  contrast {reading.ratio:.2f}:1")


def main(argv: list[str]) -> int:
    if len(argv) in (3, 4) and argv[1] == "--status-bar":
        _print_status_bar(argv[2], argv[3] if len(argv) == 4 else None)
        return 0
    if len(argv) not in (3, 5) or (len(argv) == 5 and argv[3] != "--below"):
        print(USAGE, file=sys.stderr)
        return 2
    floor = _parse_floor(argv[4]) if len(argv) == 5 else None
    if len(argv) == 5 and floor is None:
        return 2
    with open(argv[1], encoding="utf-8") as dump:
        readings = text_contrasts(dump.read(), argv[2])
    if floor is None:
        for at in readings:
            _print_reading(at)
    else:
        _print_below(readings, floor)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
