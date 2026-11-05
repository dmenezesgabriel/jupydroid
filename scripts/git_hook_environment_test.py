#!/usr/bin/env python3
"""Guard for the environment the test suite is started in."""

from __future__ import annotations

import os
import unittest


class GitHookEnvironmentTest(unittest.TestCase):
    def test_the_suite_runs_without_the_index_a_commit_hook_exports(self) -> None:
        # `git commit -a` exports GIT_INDEX_FILE as the outer repository's index.lock to every hook. A
        # test that builds a repository in a temp dir and runs `git add` then writes into that index,
        # and the commit fails with "invalid object ... Error building trees".
        # Compared as a value, not with assertNotIn, whose failure message prints the whole environment.
        self.assertIsNone(
            os.environ.get("GIT_INDEX_FILE"),
            "GIT_INDEX_FILE is set; start the suite with `env -u GIT_INDEX_FILE` "
            "(the maestro-gate-tests hook in .pre-commit-config.yaml does)",
        )


if __name__ == "__main__":
    unittest.main()
