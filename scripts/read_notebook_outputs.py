#!/usr/bin/env python3
"""What a notebook's cells printed, as plain text - a runtime probe needs the answer itself.

A screenshot of a traceback is a picture of an answer; the probes that follow are written *from* these
strings, so they are read out of the saved nbformat rather than transcribed off a frame. Only the
two output types a probe produces are handled - `stream` (what `print` writes) and `error` (what a
raised exception leaves) - and an unknown type is reported rather than skipped, so a silent gap
cannot look like a cell that printed nothing.
"""

from __future__ import annotations

import json
import sys
from typing import Any


def outputs_of(path: str) -> str:
    """Every cell's source and what it printed, in order, as one printable block."""
    notebook = json.loads(open(path).read())
    blocks = []
    for index, cell in enumerate(notebook.get("cells", []), start=1):
        source = "".join(cell.get("source", [])).rstrip()
        blocks.append(f"=== cell {index} ===\n{source}\n--- output ---\n{rendered(cell)}")
    return "\n\n".join(blocks)


def rendered(cell: dict[str, Any]) -> str:
    """One cell's outputs, or a note saying it produced none."""
    lines = []
    for output in cell.get("outputs", []):
        kind = output.get("output_type")
        if kind == "stream":
            lines.append("".join(output.get("text", [])).rstrip())
        elif kind == "error":
            lines.append(f"{output.get('ename')}: {output.get('evalue')}")
            lines.extend(output.get("traceback", []))
        elif kind in ("execute_result", "display_data"):
            lines.append("".join(output.get("data", {}).get("text/plain", [])).rstrip())
        else:
            lines.append(f"<unhandled output_type {kind!r}: {sorted(output)}>")
    return "\n".join(lines) if lines else "<no output>"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {sys.argv[0]} <notebook.ipynb>; got {sys.argv[1:]}")
    print(outputs_of(sys.argv[1]))
