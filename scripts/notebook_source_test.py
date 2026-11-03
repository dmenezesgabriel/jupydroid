"""Tests for notebook_source.py."""

from __future__ import annotations

import json
import unittest

from notebook_source import notebook_code_comments


def notebook(*cells: dict[str, object]) -> str:
    """A minimal nbformat 4 document holding these cells."""
    return json.dumps({"cells": list(cells), "metadata": {}, "nbformat": 4, "nbformat_minor": 5})


def code_cell(source: str | list[str]) -> dict[str, object]:
    return {"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [], "source": source}


def markdown_cell(source: str) -> dict[str, object]:
    return {"cell_type": "markdown", "metadata": {}, "source": source}


class NotebookCodeCommentsTest(unittest.TestCase):
    """A notebook is JSON, so its comments are the `#` lines of its code cells and nothing else."""

    def test_a_comment_in_a_code_cell_is_kept_and_its_code_blanked(self):
        prose = notebook_code_comments(notebook(code_cell("x = 1  # why\n")))
        self.assertEqual("       # why", prose.rstrip("\n"))

    def test_a_cell_source_may_be_a_list_of_lines(self):
        prose = notebook_code_comments(notebook(code_cell(["# first\n", "y = 2\n"])))
        self.assertEqual(["# first", "     "], prose.splitlines())

    def test_a_markdown_cell_is_not_prose_to_read(self):
        """It is fixture content shown on screen, as a `.md` file is documentation."""
        self.assertEqual("", notebook_code_comments(notebook(markdown_cell("# Heading\n"))).strip())

    def test_the_metadata_title_is_not_a_comment(self):
        source = json.dumps({"cells": [], "metadata": {"title": "# not a comment"}})
        self.assertEqual("", notebook_code_comments(source))

    def test_a_notebook_with_no_cells_key_has_no_prose(self):
        self.assertEqual("", notebook_code_comments(json.dumps({"metadata": {}, "nbformat": 4})))

    def test_cells_are_separated_by_one_blank_line_so_line_numbers_count_code_lines(self):
        prose = notebook_code_comments(notebook(code_cell("# one\n"), code_cell("# two\n")))
        self.assertEqual(["# one", "", "# two"], prose.rstrip("\n").splitlines())

    def test_invalid_json_names_the_value_and_the_expected_shape(self):
        with self.assertRaises(ValueError) as raised:
            notebook_code_comments("not json at all")
        self.assertIn("not json at all", str(raised.exception))
        self.assertIn("nbformat", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
