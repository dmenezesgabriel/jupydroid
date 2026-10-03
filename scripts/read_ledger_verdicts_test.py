#!/usr/bin/env python3
"""Tests for read_ledger_verdicts.py."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from read_ledger_partition import PARTITION_HEADING, ledger_partition
from read_ledger_verdicts import VERDICTS, ledger_verdicts, unverdicted_ids, verdict_counts
from read_reference_commands import reference_dir

REPO = Path(__file__).resolve().parent.parent


def _ledger(body: str) -> Path:
    """A throwaway ledger holding [body], so a parse is tested without the real document."""
    path = Path(tempfile.mkdtemp()) / "parity-ledger.md"
    path.write_text(body, encoding="utf-8")
    return path


_TABLE_HEAD = "| Reference entry | Ours | Verdict | Why / id |\n|---|---|---|---|\n"


class LedgerVerdictTest(unittest.TestCase):
    """The verdicts the ledger stores, read back - see read_ledger_verdicts.py."""

    def test_a_row_gives_every_id_in_its_first_column_its_verdict(self) -> None:
        found = ledger_verdicts(_ledger(
            _TABLE_HEAD + "| `notebook:move-cell-up`, `notebook:move-cell-down` | `withCellsMoved` | present | — |\n"))
        self.assertEqual({"notebook:move-cell-up": "present", "notebook:move-cell-down": "present"}, found)

    def test_emphasis_around_a_verdict_is_not_part_of_it(self) -> None:
        """The ledger bolds the verdicts that oblige something - `**absent**` is still `absent`."""
        found = ledger_verdicts(_ledger(_TABLE_HEAD + "| `notebook:trust` | — | **undocumented** | ADR-0068 |\n"))
        self.assertEqual({"notebook:trust": "undocumented"}, found)

    def test_an_id_given_two_verdicts_is_refused_naming_both(self) -> None:
        with self.assertRaises(ValueError) as raised:
            ledger_verdicts(_ledger(
                _TABLE_HEAD
                + "| `notebook:undo` | ours | present | — |\n"
                + "| `notebook:undo` | ours | adapted | a comment |\n"))
        for named in ("notebook:undo", "present", "adapted"):
            self.assertIn(named, str(raised.exception))

    def test_a_verdict_that_is_none_of_the_five_is_refused_naming_the_five(self) -> None:
        with self.assertRaises(ValueError) as raised:
            ledger_verdicts(_ledger(_TABLE_HEAD + "| `notebook:trust` | — | partial | — |\n"))
        self.assertIn("partial", str(raised.exception))
        for verdict in VERDICTS:
            self.assertIn(verdict, str(raised.exception))

    def test_absent_without_a_gap_is_refused(self) -> None:
        """ADR-0067's obligation for `absent`, made something a reader can fail on."""
        with self.assertRaises(ValueError) as raised:
            ledger_verdicts(_ledger(_TABLE_HEAD + "| `notebook:trust` | — | absent | later |\n"))
        self.assertIn("notebook:trust", str(raised.exception))
        self.assertIn("GAP-NNN", str(raised.exception))

    def test_undocumented_without_an_adr_is_refused(self) -> None:
        with self.assertRaises(ValueError) as raised:
            ledger_verdicts(_ledger(_TABLE_HEAD + "| `notebook:trust` | — | undocumented | nobody wrote it |\n"))
        self.assertIn("ADR-NNNN", str(raised.exception))

    def test_the_partition_table_is_not_read_as_verdicts(self) -> None:
        """It names the same ids in the same notation, and judges none of them."""
        found = ledger_verdicts(_ledger(
            f"{PARTITION_HEADING}\n\n| Reference ids | Owner | Why it is that slice's |\n|---|---|---|\n"
            + "| `notebook:trust` | `AUD-008` | rendering |\n\n"
            + _TABLE_HEAD + "| `notebook:undo` | ours | present | — |\n"))
        self.assertEqual({"notebook:undo": "present"}, found)

    def test_a_row_naming_no_command_id_is_not_a_verdict_on_one(self) -> None:
        """AUD-001's wire table has the same header, and its entries are message types."""
        found = ledger_verdicts(_ledger(_TABLE_HEAD + "| `execute_request` | ours | present | — |\n"))
        self.assertEqual({}, found)

    def test_a_verdict_reaches_past_the_notebook_family(self) -> None:
        """Verdicts and partition share the one id pattern: `documentsearch:`/`toc:`,
        AUD-005's `filebrowser:`/`docmanager:`, AUD-006's `console:`/`running:` plus the
        menu delegates, AUD-007's `terminal:`, and AUD-008's eight rendering families are
        collected - camelCase included - while a family no slice has claimed stays unread."""
        found = ledger_verdicts(_ledger(
            _TABLE_HEAD
            + "| `documentsearch:start` | ours | present | — |\n"
            + "| `filebrowser:rename`, `docmanager:save` | ours | present | — |\n"
            + "| `console:clear`, `running:show-panel`, `kernelmenu:shutdownAll` | ours | adapted | — |\n"
            + "| `terminal:create-new` | ours | present | — |\n"
            + "| `rendermime:handle-local-link`, `markdownviewer:trust` | ours | adapted | — |\n"
            + "| `htmlviewer:trust-html`, `imageviewer:zoom-in` | ours | absent | GAP-001 |\n"
            + "| `csv:go-to-line`, `tsv:go-to-line`, `mermaid:copy-source` | ours | absent | GAP-001 |\n"
            + "| `mathjax:clipboard` | ours | present | — |\n"
            + "| `inspector:open` | ours | adapted | a caller-table row |\n"))
        self.assertEqual({"documentsearch:start": "present", "filebrowser:rename": "present",
                          "docmanager:save": "present", "console:clear": "adapted",
                          "running:show-panel": "adapted", "kernelmenu:shutdownAll": "adapted",
                          "terminal:create-new": "present",
                          "rendermime:handle-local-link": "adapted",
                          "markdownviewer:trust": "adapted", "htmlviewer:trust-html": "absent",
                          "imageviewer:zoom-in": "absent", "csv:go-to-line": "absent",
                          "tsv:go-to-line": "absent", "mermaid:copy-source": "absent",
                          "mathjax:clipboard": "present"}, found)

    def test_a_task_with_one_verdict_owes_one_for_every_id_it_owns(self) -> None:
        """Examined is derived, not stored: the first verdict opens the task, and opens all of it."""
        owned = {"notebook:undo": "AUD-002 T2b", "notebook:redo": "AUD-002 T2b", "notebook:trust": "AUD-008"}
        self.assertEqual(["notebook:redo"], unverdicted_ids(owned, {"notebook:undo": "present"}))

    def test_a_task_with_no_verdict_owes_nothing_yet(self) -> None:
        self.assertEqual([], unverdicted_ids({"notebook:trust": "AUD-008"}, {}))

    def test_a_verdict_on_an_id_nobody_owns_is_reported(self) -> None:
        """The other direction: a verdict the partition does not know is an id renamed under it."""
        self.assertEqual(["notebook:invented"], unverdicted_ids({}, {"notebook:invented": "present"}))

    def test_counts_are_per_verdict_and_name_all_five(self) -> None:
        counts = verdict_counts({"a": "present", "b": "present", "c": "absent"})
        self.assertEqual({"present": 2, "adapted": 0, "undocumented": 0, "absent": 1, "defect": 0}, counts)


class VerdictedSurfaceTest(unittest.TestCase):
    """Every id an examined task owns carries exactly one verdict, against the real ledger.

    PartitionedSurfaceTest's shape one level up: that one refuses an id nobody owns, this one an id
    a task owns and left unjudged. Skipped where `reference/` is absent, because the partition it
    reads is only checked against the reference there.
    """

    @classmethod
    def setUpClass(cls) -> None:
        try:
            reference_dir("jupyterlab")
        except FileNotFoundError as absent:
            raise unittest.SkipTest(str(absent)) from absent
        ledger = REPO / "docs/research/parity-ledger.md"
        cls.owned = ledger_partition(ledger)
        cls.verdicts = ledger_verdicts(ledger)

    def test_no_examined_task_left_an_id_without_a_verdict(self) -> None:
        self.assertEqual([], unverdicted_ids(self.owned, self.verdicts))

    def test_the_editing_half_is_examined(self) -> None:
        """T2b's own claim, so the check above cannot pass by there being nothing to check."""
        editing = [found for found, owner in self.owned.items() if owner == "AUD-002 T2b"]
        self.assertTrue(editing)
        self.assertEqual([], [found for found in editing if found not in self.verdicts])


if __name__ == "__main__":
    unittest.main()
