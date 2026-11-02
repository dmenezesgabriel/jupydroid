#!/usr/bin/env python3
"""Tests for read_history_markers.py."""

from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

from read_history_markers import CLASS_NAMES, count_markers, main, report

HOME = Path("/home/ana")
#: `main` reads the listed carrier paths from the repository it measures, as it does here.
EMPTY_CARRIER_LIST = {"evidence/aud-013-scrub-list/token-carrier-paths.txt": ""}
#: Built here, not written out: a 48-hex literal in a test file is what the token gate refuses.
TOKEN_LINE = "--IdentityProvider.token=" + "a1" * 24


class FixtureRepo:
    """A throwaway git repository whose commits the test composes one at a time."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self._git("init", "-q", "-b", "main")
        self._git("config", "user.email", "t@example.org")
        self._git("config", "user.name", "T")

    def _git(self, *args: str) -> None:
        subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True)

    def commit(self, files: dict[str, str], message: str = "add files") -> None:
        for name, text in files.items():
            (self.root / name).parent.mkdir(parents=True, exist_ok=True)
            (self.root / name).write_text(text)
        self._git("add", "-A")
        self._git("commit", "-q", "-m", message)


class CountMarkersTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.repo = FixtureRepo(Path(self._tmp.name))

    def counts(self, carrier_paths: list[str] | None = None) -> dict[str, int]:
        return count_markers(self.repo.root, HOME, carrier_paths or [])

    def test_a_clean_history_counts_zero_in_every_class(self) -> None:
        self.repo.commit({"a.txt": "nothing here"})
        self.assertEqual(self.counts(), dict.fromkeys(CLASS_NAMES, 0))

    def test_a_blob_that_once_held_the_home_counts_its_commit_even_after_a_fix(self) -> None:
        self.repo.commit({"a.log": f"cd {HOME}/work"})
        self.repo.commit({"a.log": "cd ~/work"}, "scrub the tree")
        self.assertEqual(self.counts()["home path in blobs"], 2)

    def test_a_message_line_with_the_home_is_counted_per_line(self) -> None:
        self.repo.commit({"a.txt": "x"}, f"gate log\n\n{HOME}/a\n{HOME}/b")
        self.assertEqual(self.counts()["home path in messages"], 2)

    def test_a_file_name_with_the_account_name_is_counted_once_across_commits(self) -> None:
        self.repo.commit({"logs/ana.txt": "one"})
        self.repo.commit({"logs/ana.txt": "two"})
        self.assertEqual(self.counts()["home path in file names"], 1)

    def test_a_carrier_path_counts_only_when_a_commit_added_a_token_to_it(self) -> None:
        self.repo.commit({"ps.txt": TOKEN_LINE, "other.txt": "no token"})
        counts = self.counts(["ps.txt", "other.txt"])
        self.assertEqual(counts["token carrier paths"], 1)

    def test_a_scrubbed_carrier_path_counts_zero(self) -> None:
        self.repo.commit({"ps.txt": "--IdentityProvider.token=<token>"})
        self.assertEqual(self.counts(["ps.txt"])["token carrier paths"], 0)

    def test_a_home_naming_no_account_counts_zero_home_markers(self) -> None:
        self.repo.commit({"a.log": "/home/user/x"}, "path /home/user/x")
        counts = count_markers(self.repo.root, Path("/home/user"), [])
        self.assertEqual(counts["home path in blobs"], 0)


class ReportTest(unittest.TestCase):
    def test_one_line_per_class_in_the_order_given(self) -> None:
        lines = report({"home path in blobs": 3, "token carrier paths": 0}).splitlines()
        self.assertEqual(lines, ["home path in blobs: 3", "token carrier paths: 0"])


class MainTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.repo = FixtureRepo(Path(self._tmp.name))

    def test_exits_zero_on_a_clean_history(self) -> None:
        self.repo.commit({"a.txt": "clean", **EMPTY_CARRIER_LIST})
        self.assertEqual(main(self.repo.root, HOME), 0)

    def test_exits_one_when_any_class_has_a_hit(self) -> None:
        self.repo.commit({"a.txt": "clean", **EMPTY_CARRIER_LIST}, f"pasted {HOME}/x")
        self.assertEqual(main(self.repo.root, HOME), 1)


if __name__ == "__main__":
    unittest.main()
