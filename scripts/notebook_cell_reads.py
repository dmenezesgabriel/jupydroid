#!/usr/bin/env python3
"""What a notebook's cells hold, read after a flow that put code into one from the Chat.

The screen cannot say which cell the code went into, or that it was saved: a flow sees `print(6*7)`
whether it landed in the cell, in another field, or in an unsaved buffer. The `.ipynb` the server
saved is what a driver checks, cell by cell, in order. Each expected cell is `type:source`, split at
its first colon, so `code:` is an empty code cell.

    python3 scripts/notebook_cell_reads.py Untitled.ipynb code: 'code:print(6*7)'
"""

import json
import sys

TypedNotebook = dict[str, object]
TypedCell = tuple[str, str]


class CellReadError(Exception):
    """The notebook does not hold the cells the flow on screen put into it."""


def cell_pairs(notebook: TypedNotebook) -> list[TypedCell]:
    """Each cell as its type and source, the source joined as nbformat allows it stored as lines.

    >>> cell_pairs({"cells": [{"cell_type": "code", "source": ["a\\n", "b"]}]})
    [('code', 'a\\nb')]
    """
    return [(c["cell_type"], "".join(c["source"])) for c in notebook["cells"]]


def expected_pairs(arguments: list[str]) -> list[TypedCell]:
    """Each `type:source` argument as a cell, e.g. `code:` as `('code', '')`.

    >>> expected_pairs(["code:print(6*7)"])
    [('code', 'print(6*7)')]
    """
    malformed = [a for a in arguments if ":" not in a]
    if malformed:
        raise CellReadError(f"expected cells as type:source, e.g. code:print(1), got {malformed}")
    return [tuple(a.split(":", 1)) for a in arguments]


def require_cells(cells: list[TypedCell], expected: list[TypedCell]) -> None:
    """Fails unless `cells` are exactly `expected`, in order.

    >>> require_cells([("code", "")], [("code", "")])
    """
    if cells != expected:
        raise CellReadError(f"the notebook holds {cells}, expected {expected}")


def main(argv: list[str]) -> int:
    with open(argv[0], encoding="utf-8") as saved:
        cells = cell_pairs(json.load(saved))
    print(f"cells: {cells}")
    require_cells(cells, expected_pairs(argv[1:]))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except CellReadError as error:
        sys.exit(str(error))
