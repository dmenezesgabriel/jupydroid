#!/usr/bin/env python3
"""Tests for read_ledger_partition.py."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from read_ledger_partition import PARTITION_HEADING, ledger_partition, unassigned_ids
from read_reference_commands import jupyterlab_command_ids, reference_dir

REPO = Path(__file__).resolve().parent.parent


def _ledger(body: str) -> Path:
    """A throwaway ledger holding [body], so a parse is tested without the real document."""
    path = Path(tempfile.mkdtemp()) / "parity-ledger.md"
    path.write_text(body, encoding="utf-8")
    return path


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


class PartitionedSurfaceTest(unittest.TestCase):
    """Every `notebook:*` id the reference declares is owned by exactly one slice.

    The claim `docs/prd/AUD-002-*.md` makes in prose - *"an id assigned to no slice is how an area
    silently stays unexamined"* - asserted rather than hoped for. A bumped pin that adds a command
    fails here instead of leaving it owned by nobody.

    Skipped where `reference/` is absent, which is every fresh clone - PinnedSurfaceTest's shape.
    """

    @classmethod
    def setUpClass(cls) -> None:
        try:
            clone = reference_dir("jupyterlab")
        except FileNotFoundError as absent:
            raise unittest.SkipTest(str(absent)) from absent
        cls.declared = [found for found in jupyterlab_command_ids(clone / "packages")["notebook-extension"]
                        if found.startswith("notebook:")]
        cls.owned = ledger_partition(REPO / "docs/research/parity-ledger.md")

    def test_no_declared_id_is_owned_by_nobody(self) -> None:
        unowned, _ = unassigned_ids(self.declared, self.owned)
        self.assertEqual([], unowned)

    def test_the_partition_owns_no_id_the_reference_does_not_declare(self) -> None:
        _, invented = unassigned_ids(self.declared, self.owned)
        self.assertEqual([], invented)


if __name__ == "__main__":
    unittest.main()
