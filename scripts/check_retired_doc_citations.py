#!/usr/bin/env python3
"""Working Agreement (AGENTS.md): a citation must still resolve in a fresh clone.

**Why this exists.** ADR-0001 split `docs/` by lifetime and deleted three monoliths. The sweep that
followed scoped itself to shipped code, so 233 citations of the deleted files survived in
`.maestro/` flows, `scripts/` drivers and `evidence/` write-ups - prose a human reads, naming a
path that resolves nowhere. `check_doc_citations.py` could not catch them: it reads comments in
`app/src/` and `tools/`, and those trees were already clean.

**Narrow on purpose.** It bans three names rather than checking that every `docs/` path resolves.
A general check was measured and declined: upstream prose cites paths like jupyterlab's
`docs/source/user/notebook.md`, which is not this repo's file and never resolves here.

A document retired later is added to `RETIRED` in the same commit that deletes it.

Tests in check_retired_doc_citations_test.py, including one over every tracked file.
"""

from __future__ import annotations

import pathlib
import re
import sys

#: Deleted by ADR-0001. Git history still holds them - `git show 718fa5dc^:docs/PLAN.md`.
RETIRED = ("docs/PLAN.md", "docs/DECISIONS.md", "docs/environment-fidelity.md")

#: Naming a retired document is these files' job: two ADRs record the retirement and the rule for
#: a retired attribution, the triage is the map of where its entries went, `docs/README.md` gives
#: the `git show` that recovers one, and the citation gate quotes the banned forms as test data.
RECORDING_SITES = (
    "docs/adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md",
    "docs/adr/0066-a-retired-attribution-is-deleted-not-rewritten.md",
    "docs/research/decisions-triage.md",
    "docs/README.md",
    "scripts/check_doc_citations.py",
    "scripts/check_doc_citations_test.py",
    # This gate holds the three names in `RETIRED`, and its test asserts on each of them.
    "scripts/check_retired_doc_citations.py",
    "scripts/check_retired_doc_citations_test.py",
)

NAMES = re.compile("|".join(re.escape(name) for name in RETIRED))

#: Git's own rule for a binary file, so committed PNGs cost nothing to skip.
FIRST_FEW_BYTES = 8000


def cites_retired(text: str) -> list[str]:
    """Every retired document named in one file's text, in the order they appear.

    >>> cites_retired("the rule (`docs/DECISIONS.md`)")
    ['docs/DECISIONS.md']
    """
    return NAMES.findall(text)


def text_of(file: pathlib.Path) -> str:
    """One file's text, or "" when it is binary or unreadable."""
    try:
        raw = file.read_bytes()
    except OSError:
        return ""
    if b"\0" in raw[:FIRST_FEW_BYTES]:
        return ""
    return raw.decode("utf-8", errors="replace")


def main(paths: list[str], root: pathlib.Path = pathlib.Path(), texts: dict[str, str] | None = None) -> int:
    failures = 0
    for rel in paths:
        if rel in RECORDING_SITES:
            continue
        text = texts[rel] if texts is not None else text_of(root / rel)
        for name in dict.fromkeys(cites_retired(text)):
            print(f"{rel}: cites {name}, which ADR-0001 deleted - state the fact and cite an ADR")
            failures += 1
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
