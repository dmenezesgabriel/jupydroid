#!/usr/bin/env python3
"""Tests for the docs-site-build commit hook in .pre-commit-config.yaml.

The hook runs the same `mkdocs build --strict` the `docs` job of .github/workflows/docs.yml runs, so a
page that breaks a link or leaves `nav` fails at commit instead of at the pull request. Nothing here
builds the site; each test reads the hook and holds it to the rules whose breaking ships a green commit
over a site that does not build, or a build run on every commit whatever it touched.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path
from typing import Any

import yaml

REPO_ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = REPO_ROOT / ".pre-commit-config.yaml"

# The `docs` job's run line in docs.yml, verbatim: the hook is the same build, so the two cannot drift.
BUILD_COMMAND = "uv run --locked --group docs mkdocs build --strict -f docs-site/mkdocs.yml"

# What `mkdocs build -f docs-site/mkdocs.yml` reads: the site source, and the pyproject group and lock
# that pin mkdocs-material. A commit touching none of them cannot change the built site.
BUILD_INPUTS = (
    "docs-site/mkdocs.yml",
    "docs-site/pages/index.md",
    "pyproject.toml",
    "uv.lock",
)

NOT_BUILD_INPUTS = (
    "docs/adr/0000-template.md",
    "app/src/main/AndroidManifest.xml",
    ".github/workflows/docs.yml",
    "README.md",
    "scripts/docs_site_hook_test.py",
)


def hook_named(hook_id: str) -> dict[str, Any]:
    document = yaml.safe_load(CONFIG_PATH.read_text(encoding="utf-8"))
    for repo in document["repos"]:
        for hook in repo.get("hooks", []):
            if hook.get("id") == hook_id:
                return hook
    raise AssertionError(f"no hook {hook_id!r} in .pre-commit-config.yaml")


class DocsSiteBuildHookTest(unittest.TestCase):
    def setUp(self) -> None:
        self.hook = hook_named("docs-site-build")

    def test_it_runs_the_same_strict_build_the_workflow_runs(self) -> None:
        self.assertIn(BUILD_COMMAND, str(self.hook["entry"]), f"the hook's entry: {self.hook['entry']}")

    def test_it_is_a_local_system_hook_that_takes_no_filenames(self) -> None:
        self.assertEqual(self.hook["language"], "system")
        self.assertIs(self.hook["pass_filenames"], False)
        self.assertFalse(self.hook.get("always_run"), "the build is scoped to its inputs, not every commit")

    def test_it_runs_exactly_when_a_build_input_is_committed(self) -> None:
        pattern = str(self.hook["files"])
        files = re.compile(pattern)
        for path in BUILD_INPUTS:
            self.assertTrue(files.search(path), f"{path} is a build input the pattern misses: {pattern}")
        for path in NOT_BUILD_INPUTS:
            self.assertIsNone(files.search(path), f"{path} is no build input but the pattern claims it: {pattern}")


if __name__ == "__main__":
    unittest.main()
