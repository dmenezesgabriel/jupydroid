#!/usr/bin/env python3
"""Whether a captured frame draws the shell's parts the way the resolved reference does.

`docs/PLAN.md`'s P7a. `read_chrome_parts.py` says what a frame *is* drawn in; this says whether
that is right, so `p7a-chrome-parts-driver.sh` fails on a regression instead of printing bands for
a human to compare.

**Position, not presence, and the distinction is the whole point.** `EditorPalette.cellBackground`
is `#191A1B` in 2026 Dark - a cell sits a step off the page exactly as the side bar does - so
"the part colour appears somewhere in this frame" is satisfied by an open notebook's own cells
whether or not the rail was ever fixed. That check would have passed over the very defect P7a
exists to close. What is asserted instead is that the **leftmost** band is the part: the activity
rail beside two panes, the side panel where there is one, and never the document.

**The browser frame carries the whole claim**, and only at two panes: nothing is open in the
detail pane there, so no cell can lend the part colour and the three bands must read
rail / side panel / page exactly.

    python3 scripts/check_chrome_parts.py '#191A1B' '#121314' two-pane evidence/…/*.png

Tests in check_chrome_parts_test.py, including one over the frames P7a actually captured.
"""
from __future__ import annotations

import pathlib
import sys

from read_chrome_parts import chrome_bands

#: The frame with nothing open beside the panel - the one whose shape is unambiguous.
_UNAMBIGUOUS_FRAME = "the-file-browser"
#: The frame with a document in it. Where the shell draws **one** pane that document *is* the
#: window - the side panel is popped and the activity bar is deliberately not drawn over a document
#: (`docs/DECISIONS.md`, "The bar is drawn over the panel and never over a document") - so the frame
#: carries no part to check and is context rather than evidence. Beside two panes it carries the
#: rail, and is checked.
_DOCUMENT_FRAME = "a-document"


def frame_failures(frame: str, part: str, page: str, layout: str) -> list[str]:
    """What is wrong with `frame`, or an empty list.

    ```
    frame_failures("01-the-file-browser.png", "#191A1B", "#121314", "two-pane")   # []
    ```

    Args:
        frame: the captured PNG.
        part: what `activityBar.background` and `sideBar.background` resolve to in this theme.
        page: what `editor.background` resolves to in this theme.
        layout: `two-pane`, which has a rail and a document beside the panel, or `one-pane`.
    """
    if layout == "one-pane" and _DOCUMENT_FRAME in pathlib.Path(frame).name:
        return []
    bands = chrome_bands(frame)
    if not bands:
        return [f"{frame}: no band wide enough to be a part"]
    failures = []
    if bands[0].colour != part:
        failures.append(f"{frame}: leftmost part is {bands[0].colour}, expected {part}")
    if layout == "two-pane" and _UNAMBIGUOUS_FRAME in pathlib.Path(frame).name:
        shape = [band.colour for band in bands]
        if shape != [part, part, page]:
            failures.append(f"{frame}: shape {shape}, expected ['{part}', '{part}', '{page}']")
    return failures


def main() -> int:
    part, page, layout, *frames = sys.argv[1:]
    failures = [failure for frame in frames for failure in frame_failures(frame, part, page, layout)]
    for failure in failures:
        print(f"FAIL {failure}", file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
