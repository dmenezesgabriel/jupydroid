#!/usr/bin/env python3
"""Which verdict the parity ledger gives each reference command id, and which ids still owe one.

**Why a reader rather than a tally in the document.** `docs/research/parity-ledger.md` stores a
verdict per entry and no count beside it (ADR-0067), so a total has to be produced - and the
verdicts need checking for the same reason the partition does. `read_ledger_partition.py` refuses
an id nobody owns; one level up, an id a slice owns and *left unjudged* is the same hole: the slice
reads as examined, and one entry in it never was.

**Examined is derived, not stored.** A task that has recorded one verdict owes one for every id it
owns; a task with none owes nothing yet. So a half-examined task fails, and no list of which tasks
are done has to be kept in step with the ledger.

**A verdict table is any table whose header has a `Verdict` column**, read for the
`notebook:*` ids in its first column. Bounded by the header rather than by a heading, so each
slice's section is read by the same rule - and AUD-001's wire table, whose entries are message
types, contributes nothing.

**A measurement, not a gate**, on `read_ledger_partition.py`'s footing: nothing in the build fails
on its output, and the cross-check skips where `reference/` is absent.

```sh
python3 scripts/read_ledger_verdicts.py            # every id and its verdict
python3 scripts/read_ledger_verdicts.py --missing  # ids an examined task owns and has not judged
python3 scripts/read_ledger_verdicts.py --counts   # how many of each, which the ledger never stores
```

Tests in read_ledger_verdicts_test.py.
"""

from __future__ import annotations

import argparse
import re
from collections.abc import Iterator
from pathlib import Path

from read_ledger_partition import COMMAND_IN_CELL, LEDGER, ledger_partition

#: ADR-0067's five, in its own order.
VERDICTS = ("present", "adapted", "undocumented", "absent", "defect")

#: What each verdict obliges the row to name, where it obliges a name at all (ADR-0067).
OBLIGATION = {"absent": (re.compile(r"GAP-\d{3}"), "GAP-NNN"), "undocumented": (re.compile(r"ADR-\d{4}"), "ADR-NNNN")}


def ledger_verdicts(ledger: Path = LEDGER) -> dict[str, str]:
    """Every command id a verdict table names, mapped to its verdict.

    An id judged twice is refused rather than resolved, whichever verdicts the two rows give: one
    entry, one verdict, is ADR-0067's whole shape.

    ```
    ledger_verdicts()["notebook:move-cell-up"]   # 'present'
    ```
    """
    verdicts: dict[str, str] = {}
    for ids, verdict in _verdict_rows(ledger):
        for found in ids:
            if found in verdicts:
                raise ValueError(
                    f"{ledger}: {found} is judged both `{verdicts[found]}` and `{verdict}`; "
                    f"an entry carries exactly one verdict"
                )
            verdicts[found] = verdict
    return verdicts


def unverdicted_ids(owned: dict[str, str], verdicts: dict[str, str]) -> list[str]:
    """Ids an examined task owns without a verdict, and verdicts on ids nobody owns.

    ```
    unverdicted_ids({"notebook:undo": "AUD-002 T2b", "notebook:redo": "AUD-002 T2b"},
                    {"notebook:undo": "present"})   # ['notebook:redo']
    ```
    """
    examined = {owned[found] for found in verdicts if found in owned}
    unjudged = {found for found, owner in owned.items() if owner in examined and found not in verdicts}
    return sorted(unjudged | (set(verdicts) - set(owned)))


def verdict_counts(verdicts: dict[str, str]) -> dict[str, int]:
    """How many ids carry each verdict, all five named even at zero.

    ```
    verdict_counts({"notebook:undo": "present"})["absent"]   # 0
    ```
    """
    return {verdict: sum(1 for given in verdicts.values() if given == verdict) for verdict in VERDICTS}


def _verdict_rows(ledger: Path) -> Iterator[tuple[list[str], str]]:
    """Each row of a table with a `Verdict` column that names at least one command id."""
    column = None
    for line in ledger.read_text(encoding="utf-8").splitlines():
        if not line.lstrip().startswith("|"):
            column = None
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if column is None:
            column = cells.index("Verdict") if "Verdict" in cells else -1
            continue
        ids = COMMAND_IN_CELL.findall(cells[0])
        if column >= 0 and ids:
            yield ids, _verdict_of(ids, cells, column, ledger)


def _verdict_of(ids: list[str], cells: list[str], column: int, ledger: Path) -> str:
    """The row's verdict, refusing one that is none of the five or skips what it obliges."""
    verdict = cells[column].strip("*` ")
    if verdict not in VERDICTS:
        raise ValueError(
            f"{ledger}: {', '.join(ids)} has the verdict {verdict!r}, and a verdict is one of {', '.join(VERDICTS)}"
        )
    pattern, shape = OBLIGATION.get(verdict, (None, ""))
    if pattern and not pattern.search(cells[-1]):
        raise ValueError(
            f"{ledger}: {', '.join(ids)} is `{verdict}`, which obliges a {shape} in its "
            f"last column; it reads {cells[-1]!r}"
        )
    return verdict


def main() -> None:
    parser = argparse.ArgumentParser(description="Which verdict the parity ledger gives each command id.")
    parser.add_argument("--missing", action="store_true", help="print the ids an examined task owns without a verdict")
    parser.add_argument("--counts", action="store_true", help="print how many ids carry each verdict")
    parser.add_argument("--ledger", type=Path, default=LEDGER, help="the parity ledger to read")
    args = parser.parse_args()
    verdicts = ledger_verdicts(args.ledger)
    if args.missing:
        missing = unverdicted_ids(ledger_partition(args.ledger), verdicts)
        print(f"owed a verdict    {len(missing)}" + "".join(f"\n    {found}" for found in missing))
    elif args.counts:
        for verdict, count in verdict_counts(verdicts).items():
            print(f"{verdict:14s} {count}")
    else:
        for found, verdict in sorted(verdicts.items()):
            print(f"{found:55s} {verdict}")


if __name__ == "__main__":
    main()
