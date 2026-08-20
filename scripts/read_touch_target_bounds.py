#!/usr/bin/env python3
"""What the two bare-`clickable` rows reserve, read out of a `uiautomator dump`.

P2l-4. Compose expands the *hit area* of any clickable out to 48dp at the input layer, so a row
that reserves 32dp and a row that reserves 48dp are indistinguishable to `touchBoundsInRoot` -
and the expanded half is the half a neighbour's direct hit takes away
(`NodeCoordinator.outOfBoundsHit`). The layout bound is what tells them apart, and the
accessibility tree is where it can be read on a real device.

**A measurement, not a gate.** It reports what it found and exits 0 either way, because half of
its job is to be run on the build *before* the fix, where the honest answer is "under". What
fails red is the instrumented `assertReservesMinimumTouchTarget`.

    adb shell wm density | python3 scripts/read_touch_target_bounds.py hierarchy.xml
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ElementTree

#: Material's minimum interactive size, the same value `minimumInteractiveComponentSize` reserves.
MINIMUM_DP = 48.0
#: The two rows this step is about, by the `contentDescription` each states for a screen reader.
ROWS = ("Collapsed code", "2 cells hidden")
BOUNDS = re.compile(r"\[(\d+),(\d+)]\[(\d+),(\d+)]")
#: `wm density` prints `Physical density: N` and, when one is set, `Override density: N` after it.
DENSITY = re.compile(r"^\s*(Physical|Override) density:\s*(\d+)", re.MULTILINE)


def reserved_px(xml_text: str, description: str) -> tuple[int, int]:
    """The width and height in pixels of the node carrying [description].

    Args:
        xml_text: One `uiautomator dump`.
        description: The node's `content-desc`, which is its `contentDescription` in Compose.

    Raises:
        LookupError: When no node carries it, which means the flow left the wrong frame on screen.

    ```
    reserved_px(open("hierarchy.xml").read(), "2 cells hidden")  # -> (320, 40)
    ```
    """
    for node in ElementTree.fromstring(xml_text).iter("node"):
        if node.get("content-desc") != description:
            continue
        left, top, right, bottom = (int(at) for at in _bounds_of(node.get("bounds", "")))
        return right - left, bottom - top
    raise LookupError(f"no node with content-desc {description!r}; found {_descriptions(xml_text)}")


def _bounds_of(bounds: str) -> tuple[str, str, str, str]:
    """The four numbers in uiautomator's `[l,t][r,b]`."""
    found = BOUNDS.match(bounds)
    if not found:
        raise ValueError(f"bounds {bounds!r} is not uiautomator's '[left,top][right,bottom]'")
    return found.group(1), found.group(2), found.group(3), found.group(4)


def _descriptions(xml_text: str) -> list[str]:
    """Every non-empty `content-desc` in the dump, so a miss says what *was* on screen."""
    return [at for node in ElementTree.fromstring(xml_text).iter("node") if (at := node.get("content-desc"))]


def effective_density(wm_density: str) -> int:
    """The density the window is actually laid out at, from one `adb shell wm density`.

    **The override wins, and reading the first number instead is not hypothetical.** A phone-shaped
    run of this driver overrode a 320x640/mdpi emulator to 1080x1920 at 420dpi; `wm density` then
    prints *both*, physical first, and taking the first reported two 126dp rows as 126dp - which
    happens to clear the minimum, so it would have been read as a pass.

    ```
    effective_density("Physical density: 160\nOverride density: 420")  # -> 420
    ```
    """
    found = DENSITY.findall(wm_density)
    if not found:
        raise ValueError(f"no density in {wm_density!r}; expected a line like 'Physical density: 420'")
    overridden = [int(value) for kind, value in found if kind == "Override"]
    return overridden[-1] if overridden else int(found[-1][1])


def report(xml_text: str, density: int) -> str:
    """One line per row: what it reserves in dp, and whether that clears Material's minimum."""
    lines = [f"density {density} ({density / 160:g}x), minimum {MINIMUM_DP:g}dp"]
    for description in ROWS:
        width, height = reserved_px(xml_text, description)
        dp = (width * 160 / density, height * 160 / density)
        verdict = "ok" if dp[1] >= MINIMUM_DP else f"UNDER by {MINIMUM_DP - dp[1]:g}dp"
        lines.append(f"{description:<20} {dp[0]:g} x {dp[1]:g} dp   {verdict}")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    """Reads one `wm density` off stdin, because the override makes it two numbers, not one."""
    if len(argv) != 2:
        print(f"usage: adb shell wm density | {argv[0]} <hierarchy.xml>", file=sys.stderr)
        return 2
    density = effective_density(sys.stdin.read())
    with open(argv[1], encoding="utf-8") as dump:
        print(report(dump.read(), density))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
