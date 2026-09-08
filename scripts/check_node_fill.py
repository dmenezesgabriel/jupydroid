#!/usr/bin/env python3
"""Whether a named region of a captured frame is filled with the colour the reference resolves.

`docs/PLAN.md`'s P7a-1b. `read_node_fill.py` says what a region *is* filled with; this says whether
that is right, so the drivers fail on a regression instead of printing a hex for a human to compare
- the same split `read_chrome_parts.py` and `check_chrome_parts.py` stand either side of, and for
the same reason: half of a measurement's job is to be run on the build *before* the fix.

**A missing node is a failure, not a skip.** It is the one way a colour gate passes for free: a
flow that left the wrong screen up describes no such node, and "nothing to check" would be green.

    python3 scripts/check_node_fill.py hierarchy.xml frame.png content-desc "Ignore case" '#D6D6D6'

Tests in check_node_fill_test.py.
"""
from __future__ import annotations

import pathlib
import sys

from read_node_fill import node_fill


def fill_failures(
    dump: str, frame: str, attribute: str, value: str, expected: str
) -> list[str]:
    """What is wrong with the region [attribute]=[value] names in [frame], or an empty list.

    ```
    fill_failures("hierarchy.xml", "02-the-toggle-on.png", "content-desc", "Ignore case", "#D6D6D6")
    ```

    Args:
        dump: a `uiautomator dump` of the screen [frame] captures.
        frame: the captured PNG.
        attribute: which attribute names the region - see `read_node_fill.node_fill`.
        value: what that attribute has to be.
        expected: what the resolved reference says that region is drawn in, `#RRGGBB`.
    """
    name = pathlib.Path(frame).name
    try:
        found = node_fill(pathlib.Path(dump).read_text(encoding="utf-8"), attribute, value, frame)
    except (LookupError, ValueError) as refused:
        return [f"{name}: {refused}"]
    if found.upper() != expected.upper():
        return [f"{name}: {attribute}={value!r} is filled {found}, expected {expected.upper()}"]
    return []


def main() -> int:
    dump, frame, attribute, value, expected = sys.argv[1:]
    failures = fill_failures(dump, frame, attribute, value, expected)
    for failure in failures:
        print(f"FAIL {failure}", file=sys.stderr)
    if not failures:
        print(f"ok   {pathlib.Path(frame).name}: {attribute}={value!r} is {expected.upper()}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
