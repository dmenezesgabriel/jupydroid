"""Tests for check_retired_doc_citations.py, including one over every tracked file."""

from __future__ import annotations

import pathlib
import subprocess
import unittest

from check_retired_doc_citations import RECORDING_SITES, RETIRED, cites_retired, main


class CitesRetiredTest(unittest.TestCase):
    def test_a_retired_document_is_named(self):
        self.assertEqual(["docs/PLAN.md"], cites_retired("see docs/PLAN.md for the gate"))

    def test_a_backticked_citation_is_named(self):
        self.assertEqual(["docs/DECISIONS.md"], cites_retired("the rule (`docs/DECISIONS.md`)"))

    def test_every_retired_document_is_caught(self):
        for name in RETIRED:
            self.assertEqual([name], cites_retired(f"as {name} says"), name)

    def test_a_live_document_is_not_named(self):
        self.assertEqual([], cites_retired("see docs/architecture.md and docs/adr/0001-x.md"))

    def test_an_upstream_docs_path_is_not_named(self):
        """jupyterlab ships `docs/source/user/notebook.md`; only this repo's dead files are banned."""
        self.assertEqual([], cites_retired("jupyterlab@abc docs/source/user/notebook.md"))


class MainTest(unittest.TestCase):
    def test_a_citation_outside_a_recording_site_fails(self):
        self.assertEqual(1, main(["evidence/x/README.md"], texts={"evidence/x/README.md": "docs/PLAN.md"}))

    def test_a_recording_site_may_name_what_it_retired(self):
        site = RECORDING_SITES[0]
        self.assertEqual(0, main([site], texts={site: "docs/PLAN.md is gone"}))

    def test_a_clean_file_passes(self):
        self.assertEqual(0, main(["a.md"], texts={"a.md": "see ADR-0001"}))


class TheWholeTreeTest(unittest.TestCase):
    def test_no_tracked_file_cites_a_retired_document(self):
        root = pathlib.Path(__file__).resolve().parent.parent
        tracked = subprocess.run(["git", "ls-files"], cwd=root, capture_output=True, text=True, check=True)
        self.assertEqual(0, main(tracked.stdout.split(), root=root))


if __name__ == "__main__":
    unittest.main()
