#!/usr/bin/env python3
"""Which slice of the audit owns which reference command id, read back off the parity ledger.

**Why a reader rather than a list in a document.** `docs/research/parity-ledger.md` stores the
partition because ownership is a judgement and no script can take it (ADR-0067). But a partition
nobody checks is how an area silently stays unexamined: an id assigned to no slice is simply
absent from every slice's reading, and nothing says so. This reads the stored table back and
`read_ledger_partition_test.py` asserts it against the ids `read_reference_commands.py` enumerates,
so a bumped pin that adds a command fails a test instead of leaving it owned by nobody.

**A measurement, not a gate**, on the same footing as `read_reference_commands.py` and
`read_port_surface.py`: nothing in the build fails on its output, and the cross-check skips where
`reference/` is absent, which is every fresh clone.

**It stores no count and produces none.** The ledger names the entry and the owner; a reader who
wants a total runs this.

```sh
python3 scripts/read_ledger_partition.py              # every id and the slice that owns it
python3 scripts/read_ledger_partition.py --unassigned # the ids nobody owns, and owners of nothing
```

Tests in read_ledger_partition_test.py.
"""
from __future__ import annotations

import argparse
import re
from collections.abc import Iterator
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

#: The ledger holding the stored half of the audit - ADR-0067.
LEDGER = REPO / "docs/research/parity-ledger.md"

#: The heading the partition table sits under. Matched exactly, and the table ends at the next
#: heading of any level - the ledger is mostly *verdict* tables, which carry the same ids in the
#: same notation and own nothing, so a reader bounded only by "a table with ids in it" would
#: collect them too.
PARTITION_HEADING = "### Who owns which id"

#: A reference command id as the ledger's first column writes it, backticked.
COMMAND_IN_CELL = re.compile(r"`(notebook:[a-z0-9-]+)`")

#: A slice, and optionally the task inside it: `AUD-002` `T2b`. The task is kept because it is what
#: makes the remaining work pickable - "this slice's editing half" is a different answer from "this
#: slice", and the second is the one that leaves a reader guessing.
OWNER_IN_CELL = re.compile(r"`(AUD-\d{3}|T\d+[a-z]?)`")


def ledger_partition(ledger: Path = LEDGER) -> dict[str, str]:
    """Every command id the partition table names, mapped to the slice that owns it.

    An id claimed twice is refused rather than resolved: two slices each believing the other
    examined it is the same hole as nobody claiming it at all.

    ```
    ledger_partition()["notebook:split-cell-at-cursor"]   # 'AUD-002 T2b'
    ```
    """
    owners: dict[str, str] = {}
    for ids, owner in _partition_rows(ledger):
        for found in ids:
            if found in owners:
                raise ValueError(f"{ledger}: {found} is claimed by both `{owners[found]}` and "
                                 f"`{owner}`; each id belongs to exactly one slice, and a family "
                                 f"that spans two is split into one row per owner")
            owners[found] = owner
    return owners


def unassigned_ids(declared: list[str], owned: dict[str, str]) -> tuple[list[str], list[str]]:
    """The ids nobody owns, and the ids owned that the reference does not declare.

    Both directions, because both are the same defect seen from either end: the first is an area
    nobody will examine, the second a row still naming a command the reference has since renamed.

    ```
    unassigned_ids(["notebook:trust"], {"notebook:undo": "AUD-002 T2b"})
    # (['notebook:trust'], ['notebook:undo'])
    ```
    """
    return sorted(set(declared) - set(owned)), sorted(set(owned) - set(declared))


def _partition_rows(ledger: Path) -> Iterator[tuple[list[str], str]]:
    """Each table row under the heading that names at least one id, as its ids and its owner."""
    for line in _partition_lines(ledger):
        if not line.lstrip().startswith("|"):
            continue
        columns = [cell.strip() for cell in line.strip().strip("|").split("|")]
        ids = COMMAND_IN_CELL.findall(columns[0])
        if ids:
            yield ids, _owner_of(ids, columns, ledger)


def _owner_of(ids: list[str], columns: list[str], ledger: Path) -> str:
    """The slice named in the row's second column, refusing a row that names none."""
    stated = columns[1] if len(columns) > 1 else ""
    named = OWNER_IN_CELL.findall(stated)
    if not any(token.startswith("AUD-") for token in named):
        raise ValueError(f"{ledger}: {', '.join(ids)} is owned by nobody - the Owner column reads "
                         f"{stated!r}, and must name an `AUD-NNN`, optionally with the task inside "
                         f"it, as in '`AUD-002` `T2b`'")
    return " ".join(named)


def _partition_lines(ledger: Path) -> list[str]:
    """The lines between the partition heading and whatever heading follows it."""
    lines = ledger.read_text(encoding="utf-8").splitlines()
    opened = next((at for at, line in enumerate(lines) if line.strip() == PARTITION_HEADING), None)
    if opened is None:
        raise ValueError(f"{ledger} has no `{PARTITION_HEADING}` heading, so nothing in it says "
                         f"which slice owns which command id")
    rest = enumerate(lines[opened + 1:], opened + 1)
    return lines[opened + 1:next((at for at, line in rest if line.startswith("#")), len(lines))]


def _report_unassigned(ledger: Path) -> None:
    """Both directions, against the reference the ledger's own section names."""
    from read_reference_commands import jupyterlab_command_ids, reference_dir

    clone = reference_dir("jupyterlab")
    declared = [found for found in jupyterlab_command_ids(clone / "packages")["notebook-extension"]
                if found.startswith("notebook:")]
    unowned, invented = unassigned_ids(declared, ledger_partition(ledger))
    print(f"owned by no slice     {len(unowned)}" + "".join(f"\n    {found}" for found in unowned))
    print(f"declared by nobody    {len(invented)}" + "".join(f"\n    {found}" for found in invented))


def main() -> None:
    parser = argparse.ArgumentParser(description="Which slice owns which reference command id.")
    parser.add_argument("--unassigned", action="store_true",
                        help="print the ids nobody owns, and the owned ids nothing declares")
    parser.add_argument("--ledger", type=Path, default=LEDGER, help="the parity ledger to read")
    args = parser.parse_args()
    if args.unassigned:
        _report_unassigned(args.ledger)
    else:
        for found, owner in sorted(ledger_partition(args.ledger).items()):
            print(f"{found:55s} {owner}")


if __name__ == "__main__":
    main()
