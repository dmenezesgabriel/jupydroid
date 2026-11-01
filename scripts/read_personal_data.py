#!/usr/bin/env python3
"""How many tracked files carry each class of personal data a stranger could read.

**Why this exists.** The history goes public (M9), and what a scrub must remove is a count taken
off the tree, not a figure remembered from a grep. The classes and their patterns live here so the
number can be reproduced by anyone who clones the repository.

**A measurement, not a gate.** `check_device_secrets.py` is the gate; this tells it, and the
history rewrite, what is there. It counts *files* (what `git grep -l` counts), once each however
often the pattern repeats. Binary files are skipped: their pixels are read by eye.

```sh
python3 scripts/read_personal_data.py                 # files per class
python3 scripts/read_personal_data.py --scrub-rules   # filter-repo rules for this machine's home
```

Tests in read_personal_data_test.py.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

#: `this@Foo.bar` and `return@launch` are Kotlin labels, not addresses; the keyword lookahead drops them.
CLASSES: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("home path", re.compile(r"/home/[A-Za-z0-9._-]+")),
    (
        "email",
        re.compile(
            r"(?<![\w.%+-])(?!(?:this|return|super)@)[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}"
        ),
    ),
)


def files_per_class(texts: dict[str, str]) -> dict[str, int]:
    """Files in [texts] (path -> contents) matching each class.

    ```
    files_per_class({"a": "/home/ana/x"})  # -> {"home path": 1, "email": 0}
    ```
    """
    return {name: sum(1 for text in texts.values() if pattern.search(text)) for name, pattern in CLASSES}


def report(counts: dict[str, int]) -> str:
    """One `class: N files` line per class, in the order given."""
    return "\n".join(f"{name}: {count} files" for name, count in counts.items())


def scrub_rules(home: Path) -> list[str]:
    """`git filter-repo --replace-text` rules that take the host account out of every blob.

    Built from [home], never stored: a committed list of the literal it removes would carry it. Only
    the host account is a rule; a guest's `/home/jupydroid` names nobody. `scripts/java-home.sh`
    *uses* the path, so it is edited by hand to `$HOME` before the rewrite, not left to this rule.

    ```
    scrub_rules(Path("/home/ana"))  # -> ["/home/ana==>/home/user"]
    ```
    """
    return [f"{home}==>/home/user"]


def tracked_texts(root: Path) -> dict[str, str]:
    """Contents of every tracked non-binary file under [root]."""
    listing = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"], capture_output=True, check=True
    ).stdout.decode()
    texts: dict[str, str] = {}
    for name in filter(None, listing.split("\0")):
        path = root / name
        raw = path.read_bytes() if path.is_file() else b"\0"
        if b"\0" not in raw[:8192]:
            texts[name] = raw.decode("utf-8", errors="replace")
    return texts


if __name__ == "__main__":
    if "--scrub-rules" in sys.argv[1:]:
        print("\n".join(scrub_rules(Path.home())))
    else:
        print(report(files_per_class(tracked_texts(REPO))))
