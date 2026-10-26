"""The check behind the Chat's code-block drivers - see `notebook_cell_reads.py` for why it exists."""

import unittest

from notebook_cell_reads import CellReadError, cell_pairs, expected_pairs, require_cells


def notebook(*cells: dict[str, object]) -> dict[str, object]:
    return {"cells": list(cells), "nbformat": 4}


class CellPairsTest(unittest.TestCase):
    def test_joins_a_source_stored_as_lines(self):
        cells = cell_pairs(notebook({"cell_type": "code", "source": ["a = 1\n", "print(a)"]}))
        self.assertEqual(cells, [("code", "a = 1\nprint(a)")])

    def test_keeps_a_source_stored_as_one_string(self):
        cells = cell_pairs(notebook({"cell_type": "markdown", "source": "# Title"}))
        self.assertEqual(cells, [("markdown", "# Title")])


class ExpectedPairsTest(unittest.TestCase):
    def test_splits_each_argument_at_its_first_colon(self):
        self.assertEqual(expected_pairs(["code:", "code:x = {'a': 1}"]), [("code", ""), ("code", "x = {'a': 1}")])

    def test_fails_on_an_argument_without_a_type(self):
        with self.assertRaisesRegex(CellReadError, "print\\(6\\*7\\)"):
            expected_pairs(["print(6*7)"])


class RequireCellsTest(unittest.TestCase):
    def test_passes_when_every_cell_matches_in_order(self):
        require_cells([("code", ""), ("code", "print(6*7)")], [("code", ""), ("code", "print(6*7)")])

    def test_fails_on_a_cell_of_the_wrong_source(self):
        with self.assertRaisesRegex(CellReadError, "print\\(6\\*7\\)\\\\n"):
            require_cells([("code", "print(6*7)\n")], [("code", "print(6*7)")])

    def test_fails_on_an_extra_cell(self):
        with self.assertRaises(CellReadError):
            require_cells([("code", "print(6*7)"), ("code", "")], [("code", "print(6*7)")])


if __name__ == "__main__":
    unittest.main()
