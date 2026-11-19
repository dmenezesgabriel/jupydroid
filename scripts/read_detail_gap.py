#!/usr/bin/env python3
"""The gap in px between a finding's card and the line it describes, read off two dumps.

A `uiautomator dump` holds only the focused window's nodes, so the two reads need two dumps:
`card-open.xml` is the popup alone while the card is up - a focused popup also hides the IME -
and `card-closed.xml` is the app tree after dismissal, where the "Code cell" node is the 31px
line run inside each padded field. The driver hides the IME the dismissal re-raises so the
caret-reveal scroll returns, keeping both reads in the one geometry.

    card-open.xml    the dump with `Diagnostic detail` up
    card-closed.xml  the dump once it was dismissed

    python3 scripts/read_detail_gap.py card-open.xml card-closed.xml

The card's line is the `Code cell` node nearest its own edge - the fixture's cells hold one text
line apiece and that node is the line's run, not the padded field. Prints the bounds it read and
the signed gap: card top minus line bottom when the card sits below, the mirror when it flipped
above, so 0 is edge to edge and a negative is how much of the line the card still covers. Exits 1
when the gap is under -1px - the instrumented tests' own tolerance (`PopupAnchorAsserts.kt`).

Tests in read_detail_gap_test.py.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass

BOUNDS = re.compile(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
NODE = re.compile(r"<node\b[^>]*>")
DESC = re.compile(r'content-desc="([^"]*)"')


@dataclass(frozen=True)
class Rect:
    """A node's bounds in screen px."""

    left: int
    top: int
    right: int
    bottom: int


def node_rects(xml_text: str, desc: str) -> list[Rect]:
    """Every node whose content-desc is `desc`, as a Rect.

    ```
    node_rects('<node content-desc="Code cell" bounds="[1,2][3,4]"/>', "Code cell")[0]
    # -> Rect(left=1, top=2, right=3, bottom=4)
    ```
    """
    rects = []
    for node in NODE.findall(xml_text):
        if (name := DESC.search(node)) and name.group(1) == desc:
            bounds = BOUNDS.search(node)
            if bounds:
                rects.append(Rect(*(int(at) for at in bounds.groups())))
    return rects


def described_line(card: Rect, lines: list[Rect]) -> tuple[Rect, int]:
    """The line the card describes and the gap to it, in px.

    `CompletionPopupPosition` puts the card's top on the line's bottom when it fits below and
    the card's bottom on the line's top when it does not, so the described line is the edge
    nearest the card on either side. The gap is positive when the card clears the line and
    negative by the overlap when it does not.

    ```
    described_line(Rect(0, 100, 10, 200), [Rect(0, 50, 10, 100)])
    # -> (Rect(...50..100), 0)
    ```
    """
    best = min(
        lines,
        key=lambda line: min(abs(card.top - line.bottom), abs(line.top - card.bottom)),
    )
    if abs(card.top - best.bottom) <= abs(best.top - card.bottom):
        return best, card.top - best.bottom
    return best, best.top - card.bottom


def main(argv: list[str]) -> int:
    with open(argv[1], encoding="utf-8") as card_open, open(argv[2], encoding="utf-8") as card_closed:
        cards = node_rects(card_open.read(), "Diagnostic detail")
        lines = node_rects(card_closed.read(), "Code cell")
    if not cards:
        print("FAIL: no `Diagnostic detail` node in the card-open dump - the card was not up")
        return 1
    if not lines:
        print("FAIL: no `Code cell` nodes in the card-closed dump - nothing to measure against")
        return 1
    card = cards[0]
    line, gap = described_line(card, lines)
    print(f"card    x {card.left}-{card.right}  y {card.top}-{card.bottom}")
    print(f"its line x {line.left}-{line.right}  y {line.top}-{line.bottom}")
    print(f"gap = {gap}px")
    if gap < -1:
        print(f"FAIL: the card covers {-gap}px of the line it describes")
        return 1
    print("PASS: the card clears the line it describes")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
