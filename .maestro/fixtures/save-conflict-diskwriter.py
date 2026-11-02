"""What a cell does to the notebook it lives in, behind the editor's back.

Seeded beside the notebook as `diskwriter.py` by scripts/save-conflict-notebook-driver.sh, so the
cell that calls it is one line a flow can tap and the marker below is in no cell's source.
"""

import json
import pathlib


def add_cell_on_disk(name: str) -> int:
    """Appends a markdown cell to the notebook file [name] in place, and answers its cell count.

    The marker carries that count, so a second call's cell is told from the first's.
    """
    path = pathlib.Path(name)
    notebook = json.loads(path.read_text())
    count = len(notebook["cells"]) + 1
    marker = {"cell_type": "markdown", "id": f"disk{count}", "metadata": {}, "source": [f"ADDED ON DISK {count}"]}
    notebook["cells"].append(marker)
    path.write_text(json.dumps(notebook, indent=1))
    return count
