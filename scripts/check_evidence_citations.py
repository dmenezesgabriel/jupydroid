#!/usr/bin/env python3
"""ADR-0132: a tracked document never names a directory under `evidence/`.

**Why this exists.** `evidence/` is local and gitignored, so a path into it resolves only on the
disk that ran the flow. A document that points there points at nothing in a fresh clone. It
restates the measurement, or names the test or script that reproduces it.

**What passes.** The placeholder `evidence/<step>/`, the bare `evidence/`, and a glob such as
`evidence/*`: the policy and the history rewrite name the directory itself, never one of its runs.

Tests in check_evidence_citations_test.py, including one over every tracked Markdown file.
"""

from __future__ import annotations

import pathlib
import re
import sys

#: A named run directory: `evidence/` followed by a letter or digit. `<`, `*` and `…` start a
#: placeholder or a glob, so they are left alone.
EVIDENCE_DIR = re.compile(r"evidence/[A-Za-z0-9][^\s`)/]*")


def cites_evidence_dir(text: str) -> list[str]:
    """Every evidence directory named in one file's text, in the order they appear.

    >>> cites_evidence_dir("measured (`evidence/step8.7/x.txt`)")
    ['evidence/step8.7']
    """
    return EVIDENCE_DIR.findall(text)


def main(paths: list[str], root: pathlib.Path = pathlib.Path(), texts: dict[str, str] | None = None) -> int:
    failures = 0
    for rel in paths:
        text = texts[rel] if texts is not None else (root / rel).read_text(encoding="utf-8", errors="replace")
        for name in dict.fromkeys(cites_evidence_dir(text)):
            print(f"{rel}: names {name}, which is local (ADR-0132) - restate the fact or name the script")
            failures += 1
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
