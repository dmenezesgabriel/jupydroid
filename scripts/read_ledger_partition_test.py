#!/usr/bin/env python3
"""Tests for read_ledger_partition.py."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from read_ledger_partition import PARTITION_HEADING, declared_ids, ledger_partition, unassigned_ids
from read_reference_commands import reference_dir

REPO = Path(__file__).resolve().parent.parent


def _ledger(body: str) -> Path:
    """A throwaway ledger holding [body], so a parse is tested without the real document."""
    path = Path(tempfile.mkdtemp()) / "parity-ledger.md"
    path.write_text(body, encoding="utf-8")
    return path


def _packages(body_by_package: dict[str, str]) -> Path:
    """A throwaway jupyterlab `packages/` tree, one index.ts per package."""
    root = Path(tempfile.mkdtemp())
    for package, body in body_by_package.items():
        source = root / package / "src" / "index.ts"
        source.parent.mkdir(parents=True)
        source.write_text(body, encoding="utf-8")
    return root


_TABLE_HEAD = f"{PARTITION_HEADING}\n\n| Reference ids | Owner | Why it is that slice's |\n|---|---|---|\n"


class LedgerPartitionTest(unittest.TestCase):
    """The stored half of the audit, read back - see read_ledger_partition.py."""

    def test_a_row_maps_every_id_in_its_first_column_to_its_owner(self) -> None:
        found = ledger_partition(_ledger(
            _TABLE_HEAD
            + "| `notebook:insert-cell-above`, `notebook:insert-cell-below` | `AUD-002` `T2b` | ours |\n"))
        self.assertEqual({"notebook:insert-cell-above": "AUD-002 T2b",
                          "notebook:insert-cell-below": "AUD-002 T2b"}, found)

    def test_an_id_claimed_by_two_rows_is_refused_naming_both_owners(self) -> None:
        with self.assertRaises(ValueError) as raised:
            ledger_partition(_ledger(
                _TABLE_HEAD
                + "| `notebook:trust` | `AUD-008` | rendering |\n"
                + "| `notebook:trust` | `AUD-009` | chrome |\n"))
        for named in ("notebook:trust", "AUD-008", "AUD-009"):
            self.assertIn(named, str(raised.exception))

    def test_a_row_with_ids_and_no_slice_is_refused_naming_the_row(self) -> None:
        """An owner column that names no `AUD-NNN` is the unassigned id this file exists to catch."""
        with self.assertRaises(ValueError) as raised:
            ledger_partition(_ledger(_TABLE_HEAD + "| `notebook:trust` | later | not decided |\n"))
        self.assertIn("notebook:trust", str(raised.exception))
        self.assertIn("AUD-NNN", str(raised.exception))

    def test_a_table_before_the_heading_is_not_part_of_the_partition(self) -> None:
        """The ledger is mostly verdict tables, which carry ids and own nothing."""
        found = ledger_partition(_ledger(
            "| `notebook:run-cell` | `AUD-001` | a verdict row, elsewhere |\n\n"
            + _TABLE_HEAD + "| `notebook:trust` | `AUD-008` | rendering |\n"))
        self.assertEqual({"notebook:trust": "AUD-008"}, found)

    def test_a_table_after_the_next_heading_is_not_part_of_the_partition(self) -> None:
        found = ledger_partition(_ledger(
            _TABLE_HEAD + "| `notebook:trust` | `AUD-008` | rendering |\n"
            + "\n## The next section\n\n| `notebook:undo` | `AUD-002` `T2b` | beyond the table |\n"))
        self.assertEqual({"notebook:trust": "AUD-008"}, found)

    def test_a_ledger_without_the_heading_names_the_heading_it_looked_for(self) -> None:
        with self.assertRaises(ValueError) as raised:
            ledger_partition(_ledger("# Parity ledger\n\nNothing partitioned yet.\n"))
        self.assertIn(PARTITION_HEADING, str(raised.exception))

    def test_unassigned_reports_both_directions(self) -> None:
        """An id owned by nobody, and an owned id the reference does not declare - both are wrong."""
        owned = {"notebook:trust": "AUD-008", "notebook:invented": "AUD-002 T2b"}
        self.assertEqual((["notebook:undo"], ["notebook:invented"]),
                         unassigned_ids(["notebook:trust", "notebook:undo"], owned))

    def test_the_families_the_partition_has_reached_are_read(self) -> None:
        """`notebook:` alone misses AUD-004's own ids - `documentsearch:` is camelCase.

        The prefix list is closed on purpose: an open `x:y` pattern would read a verdict-table
        first cell like `inspector:open` (AUD-003's caller table) as an id the partition owns
        nowhere, so a family enters the pattern only when a slice has claimed it.
        """
        found = ledger_partition(_ledger(
            _TABLE_HEAD
            + "| `documentsearch:highlightNext`, `toc:display-numbering` | `AUD-004` `T2` | in-document |\n"
            + "| `fileeditor:find`, `editmenu:find` | `AUD-004` `T2` | carried |\n"))
        self.assertEqual({"documentsearch:highlightNext": "AUD-004 T2",
                          "toc:display-numbering": "AUD-004 T2",
                          "fileeditor:find": "AUD-004 T2",
                          "editmenu:find": "AUD-004 T2"}, found)

    def test_an_unreached_family_is_not_read(self) -> None:
        """`inspector:open` is a real id verdicted in AUD-003's caller table - and unclaimed,
        because no slice owns the inspector-extension family yet. Unread rather than wrongly
        claimed."""
        found = ledger_partition(_ledger(
            _TABLE_HEAD + "| `inspector:open` | `AUD-003` | only a verdict cell |\n"))
        self.assertEqual({}, found)

    def test_declared_ids_cover_whole_packages_and_carried_ones(self) -> None:
        """`documentsearch-extension` and `toc-extension` enter whole; from the packages a later
        slice owns, only the carried ids do - `fileeditor:undo` waits with the rest."""
        packages = _packages({
            # `COMMAND_ID` reads a namespace member - indented, never at column 0.
            "notebook-extension": "  export const u = 'notebook:undo';\n"
                                  "  export const r = 'notebook:redo';",
            "documentsearch-extension": "  export const s = 'documentsearch:start';\n"
                                        "  export const n = 'documentsearch:highlightNext';",
            "toc-extension": "  export const t = 'toc:show-panel';",
            "fileeditor-extension": "  export const f = 'fileeditor:find';\n"
                                    "  export const u = 'fileeditor:undo';",
            "mainmenu-extension": "  export const e = 'editmenu:find';\n"
                                  "  export const r = 'editmenu:redo';",
        })
        self.assertEqual(sorted(["notebook:undo", "notebook:redo",
                                 "documentsearch:start", "documentsearch:highlightNext",
                                 "toc:show-panel", "fileeditor:find", "editmenu:find"]),
                         sorted(declared_ids(packages)))

    def test_a_toc_id_declared_inside_notebook_extension_is_carried(self) -> None:
        """`toc:run-cells` is declared by `notebook-extension` under the `toc:` prefix
        (`index.ts:351`) for the ToC row's context menu - a package-whole read of
        `toc-extension` misses it, and the `notebook:` filter over `notebook-extension`'s
        own ids would drop it too."""
        packages = _packages({
            "notebook-extension": "  export const t = 'toc:run-cells';\n"
                                  "  export const u = 'notebook:undo';",
        })
        self.assertIn("toc:run-cells", declared_ids(packages))


class PartitionedSurfaceTest(unittest.TestCase):
    """Every id the partition has reached is owned by exactly one slice.

    The claim `docs/prd/AUD-002-*.md` makes in prose - *"an id assigned to no slice is how an area
    silently stays unexamined"* - asserted rather than hoped for. A bumped pin that adds a command
    fails here instead of leaving it owned by nobody. `declared_ids` names which packages the
    partition covers, and why a package can enter it one id at a time.

    Skipped where `reference/` is absent, which is every fresh clone - PinnedSurfaceTest's shape.
    """

    @classmethod
    def setUpClass(cls) -> None:
        try:
            clone = reference_dir("jupyterlab")
        except FileNotFoundError as absent:
            raise unittest.SkipTest(str(absent)) from absent
        cls.declared = declared_ids(clone / "packages")
        cls.owned = ledger_partition(REPO / "docs/research/parity-ledger.md")

    def test_no_declared_id_is_owned_by_nobody(self) -> None:
        unowned, _ = unassigned_ids(self.declared, self.owned)
        self.assertEqual([], unowned)

    def test_the_partition_owns_no_id_the_reference_does_not_declare(self) -> None:
        _, invented = unassigned_ids(self.declared, self.owned)
        self.assertEqual([], invented)


if __name__ == "__main__":
    unittest.main()
