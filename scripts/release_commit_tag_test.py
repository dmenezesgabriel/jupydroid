#!/usr/bin/env python3
"""Tests for scripts/release_commit_tag.py, which names the tag a merged release pull request is owed.

The regression is a real one: release-tag.yml read only the merge commit's subject, and a merge-commit merge
makes that "Merge pull request #24 from ...", so the first release was merged and never tagged.
"""

from __future__ import annotations

import io
import subprocess
import tempfile
import unittest
from collections.abc import Iterator
from contextlib import contextmanager, redirect_stderr, redirect_stdout
from pathlib import Path

from release_commit_tag import main, release_tag_of_merge, release_tag_of_subjects
from workflow_reader import load_workflow, steps_of

RELEASE_SUBJECT = "chore(release): v0.1.0"
MERGE_SUBJECT = "Merge pull request #24 from o/release/next"


def git(repo: Path, *arguments: str) -> str:
    done = subprocess.run(
        ["git", "-c", "user.name=t", "-c", "user.email=t@example.invalid", *arguments],
        cwd=repo,
        check=True,
        capture_output=True,
        text=True,
    )
    return done.stdout.strip()


@contextmanager
def scratch_repo() -> Iterator[Path]:
    with tempfile.TemporaryDirectory() as directory:
        repo = Path(directory)
        git(repo, "init", "-q", "-b", "main")
        git(repo, "commit", "-q", "--allow-empty", "-m", "feat: first")
        yield repo


def merge_release_branch(repo: Path) -> str:
    """Merges a branch holding the release commit into main with a merge commit and returns main's tip."""
    git(repo, "switch", "-q", "-c", "release/next")
    git(repo, "commit", "-q", "--allow-empty", "-m", RELEASE_SUBJECT)
    git(repo, "switch", "-q", "main")
    git(repo, "commit", "-q", "--allow-empty", "-m", "fix: moved on")
    git(repo, "merge", "-q", "--no-ff", "release/next", "-m", MERGE_SUBJECT)
    return git(repo, "rev-parse", "HEAD")


class SubjectsTest(unittest.TestCase):
    def test_the_release_commit_subject_names_its_tag(self) -> None:
        self.assertEqual(release_tag_of_subjects([RELEASE_SUBJECT]), "v0.1.0")

    def test_the_first_subject_that_is_a_release_commit_wins(self) -> None:
        subjects = [MERGE_SUBJECT, RELEASE_SUBJECT]
        self.assertEqual(release_tag_of_subjects(subjects), "v0.1.0")

    def test_a_squash_merge_subject_with_the_pull_request_number_names_its_tag(self) -> None:
        self.assertEqual(release_tag_of_subjects(["chore(release): v1.2.3 (#24)"]), "v1.2.3")

    def test_anything_else_names_no_tag(self) -> None:
        for subject in [
            "feat: add chore(release): v9.9.9",
            "chore(release): v1.2",
            "chore(release): v1.2.3-rc1",
            "chore(release): v1.2.3 and more",
            "chore: v1.2.3",
            "",
        ]:
            with self.subTest(subject=subject):
                self.assertIsNone(release_tag_of_subjects([subject]))


class MergeTest(unittest.TestCase):
    def test_a_merge_commit_merge_is_tagged_from_the_release_commit_it_merged(self) -> None:
        with scratch_repo() as repo:
            tip = merge_release_branch(repo)
            self.assertEqual(git(repo, "log", "-1", "--format=%s", tip), MERGE_SUBJECT)
            self.assertEqual(release_tag_of_merge(repo, tip), "v0.1.0")

    def test_a_fast_forward_is_tagged_from_the_commit_itself(self) -> None:
        with scratch_repo() as repo:
            git(repo, "switch", "-q", "-c", "release/next")
            git(repo, "commit", "-q", "--allow-empty", "-m", RELEASE_SUBJECT)
            git(repo, "switch", "-q", "main")
            git(repo, "merge", "-q", "--ff-only", "release/next")
            self.assertEqual(release_tag_of_merge(repo, git(repo, "rev-parse", "HEAD")), "v0.1.0")

    def test_a_merge_of_something_else_names_no_tag(self) -> None:
        with scratch_repo() as repo:
            git(repo, "switch", "-q", "-c", "feature")
            git(repo, "commit", "-q", "--allow-empty", "-m", "feat: other")
            git(repo, "switch", "-q", "main")
            git(repo, "commit", "-q", "--allow-empty", "-m", "fix: moved on")
            git(repo, "merge", "-q", "--no-ff", "feature", "-m", "Merge pull request #5 from o/feature")
            self.assertIsNone(release_tag_of_merge(repo, git(repo, "rev-parse", "HEAD")))

    def test_an_unknown_commit_is_refused_naming_it(self) -> None:
        with scratch_repo() as repo, self.assertRaises(ValueError) as refusal:
            release_tag_of_merge(repo, "0" * 40)
        self.assertIn("0" * 40, str(refusal.exception))


class MainTest(unittest.TestCase):
    def run_main(self, arguments: list[str]) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            status = main(arguments)
        return status, out.getvalue(), err.getvalue()

    def test_it_prints_the_tag_of_a_merged_release(self) -> None:
        with scratch_repo() as repo:
            tip = merge_release_branch(repo)
            self.assertEqual(self.run_main([str(repo), tip]), (0, "v0.1.0\n", ""))

    def test_it_prints_nothing_and_exits_0_when_the_merge_is_not_a_release(self) -> None:
        with scratch_repo() as repo:
            status, out, _ = self.run_main([str(repo), git(repo, "rev-parse", "HEAD")])
        self.assertEqual((status, out), (0, ""))

    def test_a_wrong_argument_count_exits_1(self) -> None:
        status, _, err = self.run_main(["only-one"])
        self.assertEqual(status, 1)
        self.assertIn("['only-one']", err)


class TagWorkflowTest(unittest.TestCase):
    def test_the_workflow_asks_this_script_which_tag_and_fetches_the_parents_it_reads(self) -> None:
        workflow = load_workflow("release-tag.yml")
        steps = steps_of(workflow, "tag")
        checkout = next(s for s in steps if str(s.get("uses", "")).startswith("actions/checkout@"))
        self.assertEqual(checkout["with"]["fetch-depth"], 2)
        scripts = [str(s.get("run", "")) for s in steps]
        self.assertTrue(any("scripts/release_commit_tag.py" in script for script in scripts))
        self.assertFalse(any("chore\\(release\\)" in script for script in scripts), "the subject regex is back inline")


if __name__ == "__main__":
    unittest.main()
