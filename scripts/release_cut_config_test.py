#!/usr/bin/env python3
"""Tests for the [tool.semantic_release] table in pyproject.toml.

Each test builds a scratch git repository, commits conventional subjects and asks python-semantic-release
(the locked one, through `uv run --locked --project`) which version the release cut would produce. No network:
`--noop` stops before any push or file write.
"""

from __future__ import annotations

import shutil
import subprocess
import tempfile
import tomllib
import unittest
from pathlib import Path

from pending_release import pending_release
from release_version import parse_release_tag

REPO_ROOT = Path(__file__).resolve().parent.parent


class ScratchRepo:
    """A throwaway git repository that carries this repository's pyproject.toml and nothing else."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "scratch")
        self.git("config", "user.email", "scratch@example.invalid")
        # PSR reads the remote's URL to pick its hosting adapter even under --noop, which never contacts it.
        self.git("remote", "add", "origin", "https://github.com/dmenezesgabriel/jupydroid.git")
        shutil.copy(REPO_ROOT / "pyproject.toml", directory / "pyproject.toml")
        self.commit("chore: scaffold")

    def git(self, *args: str) -> str:
        done = subprocess.run(["git", *args], cwd=self.directory, check=True, capture_output=True, text=True)
        return done.stdout

    def commit(self, subject: str) -> None:
        self.git("commit", "-q", "--allow-empty", "-m", subject)

    def tag(self, name: str) -> None:
        self.git("tag", "-a", name, "-m", name)

    def next_version(self) -> str | None:
        """The version the cut would release, or None when no commit since the last tag bumps it."""
        done = subprocess.run(
            ["uv", "run", "--locked", "--project", str(REPO_ROOT), "semantic-release", "--noop", "version", "--print"],
            cwd=self.directory,
            capture_output=True,
            text=True,
            check=False,
        )
        if done.returncode != 0:
            raise AssertionError(f"semantic-release exited {done.returncode}: {done.stderr!r}")
        return pending_release(done.stdout, self.git("tag", "--list", "v*").split())


class ReleaseCutConfigTest(unittest.TestCase):
    def setUp(self) -> None:
        self._scratch = tempfile.TemporaryDirectory()
        self.addCleanup(self._scratch.cleanup)
        self.repo = ScratchRepo(Path(self._scratch.name))

    def test_the_first_feature_releases_0_1_0_not_1_0_0(self) -> None:
        self.repo.commit("feat(ui): a thing")
        self.assertEqual(self.repo.next_version(), "0.1.0")

    def test_a_fix_after_a_tag_is_a_patch_and_a_feature_a_minor(self) -> None:
        self.repo.commit("feat: first")
        self.repo.tag("v0.1.0")
        self.repo.commit("fix: second")
        self.assertEqual(self.repo.next_version(), "0.1.1")
        self.repo.commit("feat: third")
        self.assertEqual(self.repo.next_version(), "0.2.0")

    def test_perf_is_a_patch(self) -> None:
        self.repo.commit("feat: first")
        self.repo.tag("v0.1.0")
        self.repo.commit("perf(kernel): faster")
        self.assertEqual(self.repo.next_version(), "0.1.1")

    def test_commits_that_ship_no_behaviour_release_nothing(self) -> None:
        self.repo.commit("feat: first")
        self.repo.tag("v0.1.0")
        for kind in ["docs", "build", "ci", "chore", "refactor", "test", "style"]:
            self.repo.commit(f"{kind}: no behaviour")
        self.assertIsNone(self.repo.next_version())

    def test_a_history_with_no_tag_and_nothing_bumpable_releases_nothing(self) -> None:
        self.repo.commit("docs: only docs")
        self.assertIsNone(self.repo.next_version())

    def test_a_breaking_change_on_zero_major_stays_below_1_0_0(self) -> None:
        self.repo.commit("feat: first")
        self.repo.tag("v0.1.0")
        self.repo.commit("feat!: breaking")
        self.assertEqual(self.repo.next_version(), "0.2.0")

    def test_the_printed_version_is_a_tag_the_gradle_properties_accept(self) -> None:
        self.repo.commit("feat: first")
        version = self.repo.next_version()
        self.assertEqual(parse_release_tag(f"v{version}").code, 1_000)


class ReleaseCutShapeTest(unittest.TestCase):
    def setUp(self) -> None:
        with (REPO_ROOT / "pyproject.toml").open("rb") as handle:
            self.table = tomllib.load(handle)["tool"]["semantic_release"]

    def test_the_tag_is_v_then_the_version(self) -> None:
        self.assertEqual(self.table["tag_format"], "v{version}")

    def test_the_release_commit_is_a_chore_release_the_tag_workflow_recognises(self) -> None:
        self.assertEqual(self.table["commit_message"], "chore(release): v{version}")

    def test_the_cut_runs_on_main_and_on_the_release_branch(self) -> None:
        pattern = self.table["branches"]["main"]["match"]
        for branch in ["main", "release/next"]:
            with self.subTest(branch=branch):
                self.assertRegex(branch, f"^{pattern}$")
        self.assertNotRegex("feature/x", f"^{pattern}$")


if __name__ == "__main__":
    unittest.main()
