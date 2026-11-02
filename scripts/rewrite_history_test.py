#!/usr/bin/env python3
"""Tests for rewrite_history.py."""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from read_history_markers import count_markers
from rewrite_history import (
    CARRIER_RULES_FILE,
    IDENTITY,
    filter_repo_argv,
    mailmap_text,
    old_emails,
    rewrite,
    rules_text,
)

HOME = Path("/home/ana")
IDENTITY_FOR_TEST = "Ana <1+ana@users.noreply.example.org>"
#: Built here, not written out: a 48-hex literal in a test file is what the token gate refuses.
TOKEN_LINE = "--IdentityProvider.token=" + "a1" * 24
#: `git commit` exports GIT_AUTHOR_* to its pre-commit hook, and inside the hook those override the fixture's
#: `user.email`: the real address would sign the fixture's commits and this suite would count it.
_ENV_WITHOUT_GIT = {name: value for name, value in os.environ.items() if not name.startswith("GIT_")}
CARRIER_RULE = r"regex:--IdentityProvider\.token=[0-9a-f]{48,64}==>--IdentityProvider.token=<token>"


class FixtureRepo:
    """A throwaway git repository whose commits the test composes one at a time."""

    def __init__(self, root: Path, email: str = "old@example.org") -> None:
        self.root = root
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", email)
        self.git("config", "user.name", "Ana")

    def git(self, *args: str) -> str:
        done = subprocess.run(
            ["git", "-C", str(self.root), *args], check=True, capture_output=True, text=True, env=_ENV_WITHOUT_GIT
        )
        return done.stdout

    def write(self, files: dict[str, str]) -> None:
        for name, text in files.items():
            (self.root / name).parent.mkdir(parents=True, exist_ok=True)
            (self.root / name).write_text(text)

    def commit(self, files: dict[str, str], message: str = "add files") -> None:
        self.write(files)
        self.git("add", "-A")
        self.git("commit", "-q", "-m", message)

    def commit_binary(self, name: str, content: bytes, message: str) -> None:
        (self.root / name).parent.mkdir(parents=True, exist_ok=True)
        (self.root / name).write_bytes(content)
        self.git("add", "-A")
        self.git("commit", "-q", "-m", message)

    def remove(self, name: str, message: str) -> None:
        self.git("rm", "-q", name)
        self.git("commit", "-q", "-m", message)

    def tag(self, name: str) -> None:
        self.git("tag", "-a", name, "-m", "an annotated tag")

    def identities(self) -> list[str]:
        out = self.git("log", "--all", "--format=%an <%ae>|%cn <%ce>")
        return sorted(set(out.splitlines()))

    def commit_count(self) -> int:
        return int(self.git("rev-list", "--all", "--count"))


class FakeRunner:
    """Records the argv it was handed and what the two temp files held at that moment."""

    def __init__(self, fail: bool = False) -> None:
        self.argv: list[str] = []
        self.cwd: Path | None = None
        self.seen: dict[str, str] = {}
        self._fail = fail

    def __call__(self, argv: list[str], cwd: Path) -> None:
        self.argv, self.cwd = argv, cwd
        for flag in ("--replace-text", "--mailmap"):
            self.seen[flag] = Path(argv[argv.index(flag) + 1]).read_text()
        if self._fail:
            raise RuntimeError("filter-repo refused")


class RulesTextTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.repo = FixtureRepo(Path(self._tmp.name))
        self.repo.write({CARRIER_RULES_FILE: CARRIER_RULE + "\n"})

    def lines(self, home: Path = HOME) -> list[str]:
        return rules_text(self.repo.root, home).splitlines()

    def test_carriers_then_the_home_rule_when_no_local_file_exists(self) -> None:
        self.assertEqual(self.lines(), [CARRIER_RULE, "/home/ana==>/home/user"])

    def test_the_local_rules_sit_between_the_carriers_and_the_home(self) -> None:
        self.repo.write({".sensitive-strings.local": "R9XX000==>TABLET_SERIAL\n"})
        self.assertEqual(self.lines(), [CARRIER_RULE, "R9XX000==>TABLET_SERIAL", "/home/ana==>/home/user"])

    def test_a_home_naming_no_account_adds_no_rule(self) -> None:
        self.assertEqual(self.lines(Path("/root")), [CARRIER_RULE])

    def test_a_glob_line_is_refused_naming_the_line(self) -> None:
        self.repo.write({".sensitive-strings.local": "glob:*.txt==>X\n"})
        with self.assertRaisesRegex(ValueError, r"glob:\*\.txt==>X"):
            rules_text(self.repo.root, HOME)


class MailmapTextTest(unittest.TestCase):
    def test_one_line_per_distinct_old_address(self) -> None:
        text = mailmap_text(["b@x.org", "a@x.org", "b@x.org"], IDENTITY_FOR_TEST)
        expected = "Ana <1+ana@users.noreply.example.org> <a@x.org>\nAna <1+ana@users.noreply.example.org> <b@x.org>\n"
        self.assertEqual(text, expected)

    def test_the_new_address_is_not_mapped_onto_itself(self) -> None:
        self.assertEqual(mailmap_text(["1+ana@users.noreply.example.org"], IDENTITY_FOR_TEST), "")

    def test_an_identity_without_an_address_is_refused_with_its_value(self) -> None:
        with self.assertRaisesRegex(ValueError, "Ana only"):
            mailmap_text(["a@x.org"], "Ana only")


class FilterRepoArgvTest(unittest.TestCase):
    def test_one_rules_file_feeds_blobs_and_messages(self) -> None:
        argv = filter_repo_argv(Path("/t/rules"), Path("/t/mail"), force=False)
        self.assertEqual(argv[argv.index("--replace-text") + 1], "/t/rules")
        self.assertEqual(argv[argv.index("--replace-message") + 1], "/t/rules")

    def test_empty_commits_are_kept(self) -> None:
        argv = filter_repo_argv(Path("/t/rules"), Path("/t/mail"), force=False)
        self.assertEqual(argv[argv.index("--prune-empty") + 1], "never")

    def test_bytecode_is_dropped_from_history(self) -> None:
        argv = filter_repo_argv(Path("/t/rules"), Path("/t/mail"), force=False)
        self.assertEqual(argv[argv.index("--path-glob") + 1], "*.pyc")
        self.assertIn("--invert-paths", argv)

    def test_force_is_passed_only_when_asked(self) -> None:
        self.assertNotIn("--force", filter_repo_argv(Path("/r"), Path("/m"), force=False))
        self.assertIn("--force", filter_repo_argv(Path("/r"), Path("/m"), force=True))


class RewriteTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.repo = FixtureRepo(Path(self._tmp.name))
        self.repo.commit({CARRIER_RULES_FILE: CARRIER_RULE + "\n"})

    def test_the_runner_sees_both_files_and_the_repository(self) -> None:
        runner = FakeRunner()
        rewrite(self.repo.root, HOME, IDENTITY_FOR_TEST, force=True, runner=runner)
        self.assertEqual(runner.cwd, self.repo.root)
        self.assertIn("/home/ana==>/home/user", runner.seen["--replace-text"])
        self.assertIn("<old@example.org>", runner.seen["--mailmap"])

    def test_the_temp_files_are_gone_afterwards(self) -> None:
        runner = FakeRunner()
        rewrite(self.repo.root, HOME, IDENTITY_FOR_TEST, force=True, runner=runner)
        self.assertFalse(Path(runner.argv[runner.argv.index("--replace-text") + 1]).exists())

    def test_the_temp_files_are_gone_when_the_runner_fails(self) -> None:
        runner = FakeRunner(fail=True)
        with self.assertRaises(RuntimeError):
            rewrite(self.repo.root, HOME, IDENTITY_FOR_TEST, force=True, runner=runner)
        self.assertFalse(Path(runner.argv[runner.argv.index("--mailmap") + 1]).exists())


class OldEmailsTest(unittest.TestCase):
    def test_authors_committers_and_taggers_are_all_read(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            repo = FixtureRepo(Path(tmp), email="author@x.org")
            repo.commit({"a.txt": "x"})
            repo.tag("t1")
            self.assertEqual(old_emails(repo.root), ["author@x.org"])


@unittest.skipUnless(shutil.which("git-filter-repo"), "git-filter-repo is not installed")
class EndToEndTest(unittest.TestCase):
    """The real `git filter-repo` over a fixture history that carries every class T1 counts."""

    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.repo = FixtureRepo(Path(self._tmp.name))
        self.repo.commit({CARRIER_RULES_FILE: CARRIER_RULE + "\n"}, "seed the rules")
        self.repo.commit({"ps.txt": TOKEN_LINE, "run.sh": f"cd {HOME}/work"}, f"ran in {HOME}")
        self.repo.tag("archive")

    def test_every_class_counts_zero_and_one_identity_remains(self) -> None:
        before = self.repo.commit_count()
        rewrite(self.repo.root, HOME, IDENTITY_FOR_TEST, force=True)
        counts = count_markers(self.repo.root, HOME, ["ps.txt"])
        self.assertEqual(counts, dict.fromkeys(counts, 0))
        self.assertEqual(self.repo.commit_count(), before)
        who = "Ana <1+ana@users.noreply.example.org>"
        self.assertEqual(self.repo.identities(), [f"{who}|{who}"])

    def test_bytecode_carrying_the_home_path_is_gone_from_history(self) -> None:
        """`--replace-text` skips a blob with a NUL byte in its first 8 KiB, and bytecode has them."""
        bytecode = b"\x00\x01" + f"{HOME}/scripts/gate.py".encode() + b"\x00"
        pyc = "scripts/__pycache__/gate.cpython-314.pyc"
        self.repo.commit_binary(pyc, bytecode, "track the gate's bytecode")
        self.repo.remove(pyc, "keep the gate's bytecode out of git")
        rewrite(self.repo.root, HOME, IDENTITY_FOR_TEST, force=True)
        counts = count_markers(self.repo.root, HOME, [])
        self.assertEqual(counts, dict.fromkeys(counts, 0))

    def test_the_tagger_address_is_rewritten_too(self) -> None:
        rewrite(self.repo.root, HOME, IDENTITY_FOR_TEST, force=True)
        tagger = self.repo.git("for-each-ref", "--format=%(taggeremail)", "refs/tags/archive")
        self.assertEqual(tagger.strip(), "<1+ana@users.noreply.example.org>")

    def test_the_default_identity_is_the_noreply_address(self) -> None:
        self.assertIn("users.noreply.github.com", IDENTITY)


if __name__ == "__main__":
    unittest.main()
