#!/usr/bin/env python3
"""Where each cell header's leading mark starts, read out of a `uiautomator dump`.

`CellHeader` used to draw a kind label - the words "markdown" and "raw", this app's own - in
front of the heading collapser, so the label's x was a function of what the cell happened to carry:
in a capture of the table-of-contents pane a prose cell's label and a heading
cell's start 48dp apart in the same frame. The reference gives a non-code cell no prompt at all
(`_setPrompt`'s single caller is `CodeCell._updatePrompt`, `packages/cells/src/widget.ts:1693-1700`)
and its collapser costs no width, being an absolutely positioned child of the prompt
(`packages/cells/src/widget.ts:2386-2408`, `packages/cells/style/widget.css:154-168`).

Nothing on screen says "these two are misaligned" and no assertion in the suite could: the labels
read the right words, in the right cells, in the right order. What tells them apart is the left
layout bound, and the accessibility tree is where it can be read on a real device.

**It reads both halves of the pair with one rule.** The `before` build draws `[ ]` with no colon
and the words "markdown" and "raw"; the `after` build draws `[ ]:` and nothing. Both are leading
marks and both are matched, so one tool reads the whole pair and the report names which notation
it found.

**A measurement, not a gate.** It reports what it found and exits 0 either way, because half of
its job is to be run on the build *before* the fix, where the honest answer is "ragged". What
fails red is the instrumented `everyCodePromptInOneFrameStartsAtTheSameX`.

    adb shell wm density | python3 scripts/read_gutter_x.py hierarchy.xml
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ElementTree

#: Jupyter's prompt notation. The colon is optional so that a dump taken on the build *before*
#: the kind labels were removed still parses - the reference's own `[${value || ' '}]:`
#: (`packages/cells/src/inputarea.ts:283-290`) is what the trailing one is.
PROMPT = re.compile(r"^\[(?: |\*|-|\d+)\]:?$")
#: The two words this app used to draw where the reference draws nothing. Matched on purpose: they
#: are what makes the `before` half of the pair ragged, and a reader that skipped them would report
#: the defect as absent.
LEGACY_PROMPT = ("markdown", "raw")
#: The heading collapser, by the labels `CellHeader` gives it. It leads a heading cell's header,
#: so it is a leading mark exactly as a prompt is.
COLLAPSERS = ("Collapse heading", "Expand heading")
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]")
#: `wm density` prints `Physical density: N` and, when one is set, `Override density: N` after it.
DENSITY = re.compile(r"^\s*(Physical|Override) density:\s*(\d+)", re.MULTILINE)


def leading_marks(xml_text: str) -> list[tuple[str, int]]:
    """Every mark that can lead a cell header, with its left bound in pixels, in document order.

    A prompt, a legacy kind label, or a heading collapser - the three things this app has ever
    drawn at the start of a header. `Run cell` is deliberately not one: its `contentDescription`
    sits on the `Icon` rather than on the button around it, so the dump reports a 24dp node inset
    inside a 48dp target and its bound is not comparable with the others'.

    Args:
        xml_text: One `uiautomator dump`.

    Raises:
        LookupError: When the dump holds no leading mark at all, which means the flow left the
            wrong frame on screen.

    ```
    leading_marks(open("hierarchy.xml").read())  # -> [('Collapse heading', 16), ('[ ]:', 16)]
    ```
    """
    found = []
    for node in ElementTree.fromstring(xml_text).iter("node"):
        text, description = node.get("text", ""), node.get("content-desc", "")
        if PROMPT.match(text) or text in LEGACY_PROMPT:
            found.append((text, int(_bounds_of(node.get("bounds", ""))[0])))
        elif description in COLLAPSERS:
            found.append((description, int(_bounds_of(node.get("bounds", ""))[0])))
    if not found:
        raise LookupError(f"no leading mark in the dump; found text {_texts(xml_text)}")
    return found


def _bounds_of(bounds: str) -> tuple[str, str, str, str]:
    """The four numbers in uiautomator's `[l,t][r,b]`."""
    match = BOUNDS.match(bounds)
    if not match:
        raise ValueError(f"bounds {bounds!r} is not uiautomator's '[left,top][right,bottom]'")
    return match.group(1), match.group(2), match.group(3), match.group(4)


def _texts(xml_text: str) -> list[str]:
    """Every non-empty `text` in the dump, so a miss says what *was* on screen."""
    return [at for node in ElementTree.fromstring(xml_text).iter("node") if (at := node.get("text"))]


def effective_density(wm_density: str) -> int:
    """The density the window is actually laid out at, from one `adb shell wm density`.

    The override wins; reading the first number instead is the mistake an earlier version of this tool made and
    `read_touch_target_bounds.effective_density` documents in full.

    ```
    effective_density("Physical density: 160\\nOverride density: 420")  # -> 420
    ```
    """
    found = DENSITY.findall(wm_density)
    if not found:
        raise ValueError(f"no density in {wm_density!r}; expected a line like 'Physical density: 420'")
    overridden = [int(value) for kind, value in found if kind == "Override"]
    return overridden[-1] if overridden else int(found[-1][1])


def report(xml_text: str, density: int) -> str:
    """One line per leading mark - what it is and where it starts - then whether they line up."""
    marks = leading_marks(xml_text)
    in_dp = [(text, left * 160 / density) for text, left in marks]
    lines = [f"density {density} ({density / 160:g}x), {len(in_dp)} leading marks"]
    lines += [f"{text:<18} x = {dp:g} dp" for text, dp in in_dp]
    legacy = [text for text, _ in in_dp if text in LEGACY_PROMPT]
    if legacy:
        lines.append(f"invented kind labels still drawn: {', '.join(sorted(set(legacy)))}")
    spread = max(dp for _, dp in in_dp) - min(dp for _, dp in in_dp)
    lines.append("aligned" if spread == 0 else f"RAGGED by {spread:g}dp")
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
