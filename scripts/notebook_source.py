#!/usr/bin/env python3
"""Reading a Jupyter notebook the way a gate needs to: the comments of its code cells.

The counterpart of `script_source.python_prose` for a `.ipynb`. A notebook is JSON, and JSON has no
comment syntax (RFC 8259), but the Python in its code cells does: `.maestro/fixtures/app-chrome.ipynb`
carried a plan-step code in a `#` comment that no scanner of the file as text would have called prose.
Markdown cells and the metadata are fixture content a flow shows on screen, not comments, so they are
not read, as a `.md` file is not.

Tests in notebook_source_test.py.
"""

from __future__ import annotations

import json
from typing import Any

from script_source import python_prose

#: One blank line between two cells. nbformat leaves the last line of a cell without a newline, so
#: the separator supplies both: a comment ending one cell is never joined to the next cell's first.
CELL_SEPARATOR = "\n\n"


def notebook_code_comments(source: str) -> str:
    """The `#` comments of every code cell of one notebook, with its code blanked to spaces.

    Line numbers count the lines of the code cells, one blank line between two cells, and not the
    lines of the file: a notebook is usually one line of JSON, so a file line would say nothing.

    ```
    notebook_code_comments('{"cells": [{"cell_type": "code", "source": "x = 1  # why"}]}')
    # '       # why'
    ```

    Args:
        source: the contents of one `.ipynb` file, nbformat 4.

    Returns:
        The code cells' text with everything outside a comment or docstring replaced by spaces, or
        an empty string when the notebook has no cells.

    Raises:
        ValueError: when `source` is not JSON, naming its start and the shape expected.
    """
    cells = _cells_of(source)
    code = [_text_of(cell["source"]).rstrip("\n") for cell in cells if cell.get("cell_type") == "code"]
    return python_prose(CELL_SEPARATOR.join(code))


def _cells_of(source: str) -> list[dict[str, Any]]:
    """The `cells` array of one notebook document, empty when the key is absent."""
    try:
        document = json.loads(source)
    except json.JSONDecodeError as error:
        raise ValueError(f"not an nbformat 4 notebook (a JSON object with `cells`): {source[:60]!r}") from error
    cells: list[dict[str, Any]] = document.get("cells", []) if isinstance(document, dict) else []
    return cells


def _text_of(cell_source: str | list[str]) -> str:
    """A cell's source, which nbformat stores as one string or as a list of lines."""
    return cell_source if isinstance(cell_source, str) else "".join(cell_source)
