#!/usr/bin/env python3
"""Tests for remap_cited_shas.py."""

from __future__ import annotations

import contextlib
import io
import subprocess
import tempfile
import unittest
from pathlib import Path

from remap_cited_shas import RemapReport, load_commit_map, main, remap_content, remap_tree

#: Built from a prefix and a fill so each value is 40 hex without being written out.
OLD_ONE = "abcdef1" + "0" * 33
NEW_ONE = "fedcba9" + "1" * 33
#: Two old commits that share their first seven hex, so `cafe123` alone names neither.
OLD_TWIN_A = "cafe123" + "a" * 33
OLD_TWIN_B = "cafe123" + "b" * 33
NEW_TWIN_A = "5555555" + "2" * 33
NEW_TWIN_B = "6666666" + "3" * 33
MAP_TEXT = "\n".join(
    ["old new", f"{OLD_ONE} {NEW_ONE}", f"{OLD_TWIN_A} {NEW_TWIN_A}", f"{OLD_TWIN_B} {NEW_TWIN_B}", ""]
)


def remapped(text: str) -> str:
    """What [text] becomes under `MAP_TEXT`."""
    return remap_content(text.encode(), load_commit_map(MAP_TEXT))[0].decode()


class LoadCommitMapTest(unittest.TestCase):
    def test_the_header_and_blank_lines_are_skipped(self) -> None:
        commits = load_commit_map(MAP_TEXT)
        self.assertEqual(commits.new_for(OLD_ONE[:12]), NEW_ONE[:12])

    def test_a_malformed_line_raises_with_the_line(self) -> None:
        with self.assertRaisesRegex(ValueError, "not-a-pair"):
            load_commit_map("old new\nnot-a-pair\n")

    def test_a_short_value_raises_with_the_line(self) -> None:
        with self.assertRaisesRegex(ValueError, "abc123 def456"):
            load_commit_map("abc123 def456\n")


class RemapContentTest(unittest.TestCase):
    def test_a_unique_prefix_becomes_the_new_sha_at_the_same_length(self) -> None:
        for length in (7, 8, 12):
            with self.subTest(length=length):
                self.assertEqual(remapped(f"see {OLD_ONE[:length]}."), f"see {NEW_ONE[:length]}.")

    def test_a_token_that_prefixes_no_old_commit_is_untouched(self) -> None:
        self.assertEqual(remapped("pinned at 0123abc and 9999999"), "pinned at 0123abc and 9999999")

    def test_a_prefix_of_two_old_commits_raises_with_the_token_and_candidates(self) -> None:
        with self.assertRaisesRegex(ValueError, f"cafe123.*{OLD_TWIN_A}.*{OLD_TWIN_B}"):
            remapped("Fixes: cafe123")

    def test_a_longer_prefix_tells_the_twins_apart(self) -> None:
        self.assertEqual(remapped("cafe123a"), NEW_TWIN_A[:8])

    def test_a_hex_run_inside_a_longer_word_is_not_a_token(self) -> None:
        text = f"{OLD_ONE[:13]} x{OLD_ONE[:7]}a abc_{OLD_ONE[:7]} {OLD_ONE[:7]}_x"
        self.assertEqual(remapped(text), text)

    def test_the_count_is_the_number_of_tokens_rewritten(self) -> None:
        text = f"{OLD_ONE[:8]} and {OLD_ONE[:12]} and 0123abc"
        self.assertEqual(remap_content(text.encode(), load_commit_map(MAP_TEXT))[1], 2)


class FixtureRepo:
    """A throwaway git repository whose tracked files the test composes."""

    def __init__(self, root: Path) -> None:
        self.root = root
        root.mkdir(parents=True, exist_ok=True)
        self._git("init", "-q", "-b", "main")

    def _git(self, *args: str) -> None:
        subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True)

    def write(self, name: str, content: bytes) -> Path:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def track(self, name: str, content: bytes) -> Path:
        path = self.write(name, content)
        self._git("add", name)
        return path


class RemapTreeTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.repo = FixtureRepo(Path(self._tmp.name))
        self.commits = load_commit_map(MAP_TEXT)
        self.cited = f"fixed in {OLD_ONE[:8]}\n".encode()

    def test_a_tracked_file_that_cites_an_old_commit_is_rewritten(self) -> None:
        notes = self.repo.track("notes.md", self.cited)
        report = remap_tree(self.repo.root, self.commits, dry_run=False)
        self.assertEqual((report, notes.read_text()), (RemapReport(files=1, tokens=1), f"fixed in {NEW_ONE[:8]}\n"))

    def test_an_untracked_file_is_never_touched(self) -> None:
        self.repo.track("kept.md", b"nothing cited")
        local = self.repo.write("evidence/run/log.txt", self.cited)
        remap_tree(self.repo.root, self.commits, dry_run=False)
        self.assertEqual(local.read_bytes(), self.cited)

    def test_a_binary_file_is_never_touched(self) -> None:
        blob = self.repo.track("image.bin", b"\0" + self.cited)
        report = remap_tree(self.repo.root, self.commits, dry_run=False)
        self.assertEqual((report, blob.read_bytes()), (RemapReport(files=0, tokens=0), b"\0" + self.cited))

    def test_an_ambiguity_in_a_later_file_leaves_every_file_unwritten(self) -> None:
        first = self.repo.track("a.md", self.cited)
        self.repo.track("z.md", b"Fixes: cafe123")
        with self.assertRaisesRegex(ValueError, "z.md.*cafe123"):
            remap_tree(self.repo.root, self.commits, dry_run=False)
        self.assertEqual(first.read_bytes(), self.cited)

    def test_a_dry_run_counts_and_writes_nothing(self) -> None:
        notes = self.repo.track("notes.md", self.cited)
        report = remap_tree(self.repo.root, self.commits, dry_run=True)
        self.assertEqual((report, notes.read_bytes()), (RemapReport(files=1, tokens=1), self.cited))

    def test_a_second_run_finds_nothing_left_to_rewrite(self) -> None:
        self.repo.track("notes.md", self.cited)
        remap_tree(self.repo.root, self.commits, dry_run=False)
        self.assertEqual(remap_tree(self.repo.root, self.commits, dry_run=False), RemapReport(files=0, tokens=0))


class MainTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.commit_map = Path(self._tmp.name) / "commit-map"
        self.commit_map.write_text(MAP_TEXT)
        self.repo = FixtureRepo(Path(self._tmp.name) / "repo")
        self.repo.track("notes.md", f"fixed in {OLD_ONE[:8]}\n".encode())

    def run_main(self, *flags: str) -> str:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(main(["--commit-map", str(self.commit_map), *flags], self.repo.root), 0)
        return out.getvalue().strip()

    def test_a_dry_run_says_it_would_remap(self) -> None:
        self.assertEqual(self.run_main("--dry-run"), "would remap 1 cited SHAs in 1 files")

    def test_a_run_says_it_remapped(self) -> None:
        self.assertEqual(self.run_main(), "remapped 1 cited SHAs in 1 files")

    def test_a_missing_commit_map_raises_with_the_path(self) -> None:
        self.commit_map.unlink()
        with self.assertRaisesRegex(FileNotFoundError, "commit-map"):
            self.run_main("--dry-run")


if __name__ == "__main__":
    unittest.main()
