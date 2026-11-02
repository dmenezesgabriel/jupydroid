#!/usr/bin/env python3
"""What `read_notebook_outputs` must say about a notebook the kernel has written to."""

from __future__ import annotations

import json
import pathlib
import tempfile
import unittest

from read_notebook_outputs import outputs_of


class ReadNotebookOutputsTest(unittest.TestCase):
    def test_a_cell_that_printed_shows_its_source_and_its_text(self) -> None:
        text = self.read(self.cell("print('hi')", [stream("hi\n")]))

        self.assertIn("print('hi')", text)
        self.assertIn("hi", text)

    def test_a_cell_that_raised_shows_the_exception_rather_than_nothing(self) -> None:
        """The probe's interesting answers are failures - a refused syscall, an unreachable name."""
        error = {
            "output_type": "error",
            "ename": "OSError",
            "evalue": "[Errno 8] Exec format error",
            "traceback": ["Traceback...", "OSError: [Errno 8] Exec format error"],
        }

        text = self.read(self.cell("import subprocess", [error]))

        self.assertIn("OSError: [Errno 8] Exec format error", text)

    def test_a_cell_that_printed_nothing_says_so(self) -> None:
        """Silence and an unread output must not look alike; the probes are written from these strings."""
        self.assertIn("<no output>", self.read(self.cell("x = 1", [])))

    def test_an_output_type_it_does_not_know_is_reported_rather_than_dropped(self) -> None:
        text = self.read(self.cell("x", [{"output_type": "future_kind"}]))

        self.assertIn("unhandled output_type 'future_kind'", text)

    def test_every_cell_is_numbered_in_order(self) -> None:
        notebook = {"cells": [self.cell("a", [stream("1")])["cells"][0], self.cell("b", [stream("2")])["cells"][0]]}

        text = self.read(notebook)

        self.assertLess(text.index("=== cell 1 ==="), text.index("=== cell 2 ==="))

    def read(self, notebook: dict) -> str:
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "probe.ipynb"
            path.write_text(json.dumps(notebook))
            return outputs_of(str(path))

    def cell(self, source: str, outputs: list) -> dict:
        return {"cells": [{"cell_type": "code", "source": [source], "outputs": outputs}]}


def stream(text: str) -> dict:
    return {"output_type": "stream", "text": [text]}


if __name__ == "__main__":
    unittest.main()
