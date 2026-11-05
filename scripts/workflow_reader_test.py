#!/usr/bin/env python3
"""Tests for workflow_reader.py, against the real .github/workflows/ci.yml so a rename fails here first."""

from __future__ import annotations

import unittest

from workflow_reader import FULL_SHA, load_workflow, steps_of, triggers_of, used_actions


class WorkflowReaderTest(unittest.TestCase):
    def test_it_parses_a_workflow_to_a_mapping_with_jobs(self) -> None:
        self.assertIn("python-gate", load_workflow("ci.yml")["jobs"])

    def test_the_on_key_is_found_although_the_parser_files_it_under_true(self) -> None:
        self.assertIn("push", triggers_of(load_workflow("ci.yml")))

    def test_it_lists_the_steps_of_one_job(self) -> None:
        self.assertTrue(steps_of(load_workflow("ci.yml"), "python-gate"))

    def test_an_unknown_job_is_refused_naming_the_jobs_that_exist(self) -> None:
        with self.assertRaises(AssertionError) as refusal:
            steps_of(load_workflow("ci.yml"), "no-such-job")
        self.assertIn("'no-such-job'", str(refusal.exception))
        self.assertIn("python-gate", str(refusal.exception))

    def test_it_lists_the_actions_without_their_trailing_comment(self) -> None:
        used = used_actions(load_workflow("ci.yml"))
        self.assertTrue(used)
        self.assertTrue(all(FULL_SHA.search(reference) for reference in used), used)

    def test_a_missing_workflow_file_is_an_error(self) -> None:
        with self.assertRaises(FileNotFoundError):
            load_workflow("no-such-workflow.yml")


if __name__ == "__main__":
    unittest.main()
