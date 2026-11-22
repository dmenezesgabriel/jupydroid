#!/usr/bin/env python3
"""Rewrites every commit so a stranger's clone carries no home path, no credential and no personal email.

**Why one script.** A history rewrite is run three times: rehearsed on a bundle clone, run for real,
and (if the rehearsal found a miss) run again. Building the `git filter-repo` argument list by hand
each time is how a rule reaches the blobs and not the messages. This builds both from one rules file,
so `--replace-text` and `--replace-message` cannot disagree.

**What never lands in the repository.** The rules hold the host's home path and the tablet's serial,
and the mailmap holds the address being removed. Both are written to a temporary directory outside the
repository and deleted when the run ends, so the script carries the shape of the rewrite and none of
what it removes.

The rules, in order: one per token carrier, derived from `check_device_secrets.TOKEN_CARRIERS` so the
rewrite and the gate cannot drift (see `carrier_rule`), the gitignored `.sensitive-strings.local`, and the
host's own `/home/<name>` (`read_personal_data.scrub_rules`). Empty commits are kept, so every branch has
the commit count it had; bytecode and the evidence tree are removed from every commit (see `filter_repo_argv`).
`--force` is needed on a repository that `git filter-repo` has already rewritten once
(`.git/filter-repo/already_ran`), and on any that is not a fresh clone.

```sh
python3 scripts/rewrite_history.py --force   # in the working repository; rehearse on a bundle clone first
```

Tests in rewrite_history_test.py.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
from collections.abc import Callable, Iterator
from pathlib import Path

from check_device_secrets import LOCAL_LITERALS, TOKEN_CARRIERS, local_rules_from
from read_personal_data import scrub_rules

REPO = Path(__file__).resolve().parent.parent
#: The GitHub noreply address of the repository's owner; the name is kept because the docs and ADRs carry it.
IDENTITY = "Gabriel Menezes <50274255+dmenezesgabriel@users.noreply.github.com>"

#: Runs an argv inside a directory; injected so a test can see the argv without a rewrite happening.
Runner = Callable[[list[str], Path], None]


def _environment_without_git() -> dict[str, str]:
    """The caller's environment minus every `GIT_*` variable.

    A pre-commit hook run from a linked worktree exports `GIT_DIR`, which outranks `git -C` and `cwd=`: a
    `git filter-repo` started from a test then rewrote the shared repository instead of its fixture.
    """
    return {name: value for name, value in os.environ.items() if not name.startswith("GIT_")}


def run_in(argv: list[str], cwd: Path) -> None:
    """Run [argv] in [cwd]; a non-zero exit raises, since a half-rewritten history is worse than none."""
    subprocess.run(argv, cwd=cwd, check=True, env=_environment_without_git())


def _git_lines(root: Path, *args: str) -> list[str]:
    """Lines `git -C root args` printed; a failing git raises with its own stderr."""
    done = subprocess.run(
        ["git", "-C", str(root), *args],
        capture_output=True,
        text=True,
        errors="replace",
        check=True,
        env=_environment_without_git(),
    )
    return done.stdout.splitlines()


def _structural_chars(pattern: str) -> Iterator[tuple[int, str]]:
    """Each character of [pattern] that is regex syntax: escaped ones and those inside `[...]` are skipped."""
    escaped = in_class = False
    for index, char in enumerate(pattern):
        if escaped or char == "\\":
            escaped = not escaped
        elif in_class or char == "[":
            in_class = char != "]" if in_class else True
        else:
            yield index, char


def _group_one_span(pattern: str) -> tuple[int, int]:
    """Where the first capturing group of [pattern] starts and ends, `)` included, in the pattern's text."""
    start, depth = -1, 0
    for index, char in _structural_chars(pattern):
        if start < 0 and char == "(" and not pattern.startswith("(?", index):
            start = index
        if start < 0:
            continue
        depth += {"(": 1, ")": -1}.get(char, 0)
        if depth == 0:
            return start, index + 1
    raise ValueError(f"carrier pattern: expected a capturing group around the credential; got {pattern!r}")


def carrier_rule(pattern: str) -> str:
    """The `--replace-text` line that rewrites [pattern]'s group 1, the credential, to `<token>`.

    `git filter-repo` takes a replacement string, not a function, so the text around the credential is
    captured and put back by backreference; `scrub_device_secrets` makes the same edit to the tree.

    ```
    carrier_rule(r'"key": "([0-9a-f]{48,64})"')  # -> 'regex:("key": ")([0-9a-f]{48,64})(")==>\\1<token>\\3'
    ```
    """
    start, end = _group_one_span(pattern)
    return f"regex:({pattern[:start]}){pattern[start:end]}({pattern[end:]})==>\\1<token>\\3"


def rules_text(root: Path, home: Path) -> str:
    """The `--replace-text` file: carriers, then `.sensitive-strings.local`, then [home]'s account.

    A `glob:` line in the local file raises, since `git filter-repo` would honour it and
    `check_device_secrets` would not, and the gate would then pass the leak it was written to refuse.

    ```
    rules_text(Path("."), Path("/home/ana")).splitlines()[-1]  # -> "/home/ana==>/home/user"
    ```
    """
    local = root / LOCAL_LITERALS
    local_text = local.read_text() if local.is_file() else ""
    local_rules_from(local_text)
    carriers = [carrier_rule(carrier.pattern.pattern) for carrier in TOKEN_CARRIERS]
    local_lines = [line for line in local_text.splitlines() if line.strip()]
    return "\n".join([*carriers, *local_lines, *_home_lines(home)]) + "\n"


def _home_lines(home: Path) -> list[str]:
    """The rule for [home], or none when it names no account: `/root` would rewrite the rootfs's own paths."""
    return scrub_rules(home) if home.parent == Path("/home") else []


def old_emails(root: Path) -> list[str]:
    """Every distinct address that signs a commit or an annotated tag in [root]'s history, sorted."""
    commits = _git_lines(root, "log", "--all", "--format=%ae%n%ce")
    taggers = _git_lines(root, "for-each-ref", "--format=%(taggeremail)", "refs/tags")
    return sorted({address.strip("<>") for address in [*commits, *taggers] if address.strip("<>")})


def mailmap_text(emails: list[str], identity: str) -> str:
    """A mailmap line sending each of [emails] to [identity]; the identity's own address is left alone.

    ```
    mailmap_text(["old@x.org"], "Ana <ana@noreply.org>")  # -> "Ana <ana@noreply.org> <old@x.org>\\n"
    ```
    """
    if "<" not in identity or not identity.endswith(">"):
        raise ValueError(f"identity: expected 'Name <address>'; got {identity!r}")
    own = identity[identity.index("<") + 1 : -1]
    return "".join(f"{identity} <{address}>\n" for address in sorted(set(emails)) if address != own)


def filter_repo_argv(rules: Path, mailmap: Path, force: bool) -> list[str]:
    """The `git filter-repo` command: one rules file for blobs and messages, empty commits kept.

    `--replace-text` skips a blob with a NUL byte in its first 8 KiB (`git_filter_repo.py`, `_tweak_blob`), and
    CPython bytecode embeds the build path and has them, so `*.pyc` is dropped from history rather than scrubbed:
    rewriting bytes inside length-prefixed bytecode would only corrupt it. The evidence tree is dropped because
    evidence is kept on the machine that ran it and never published; the repeated `--path-glob` values are OR-ed.
    """
    argv = ["git", "filter-repo", "--replace-text", str(rules), "--replace-message", str(rules)]
    argv += ["--mailmap", str(mailmap), "--prune-empty", "never"]
    argv += ["--path-glob", "*.pyc", "--path-glob", "evidence/*", "--invert-paths"]
    return [*argv, "--force"] if force else argv


def forget_previous_run(root: Path) -> None:
    """Remove the marker `git filter-repo` leaves, so this rewrite is a fresh run and never a continuation.

    Past a day old the marker makes `git_filter_repo.py` ask on stdin whether to continue the previous run, and
    answering anything but `y` deletes it; a younger one is continued without asking. A continuation updates
    refs it already moved instead of starting from the repository as it stands, which is not this rewrite.

    ```
    forget_previous_run(Path("."))
    ```
    """
    (root / ".git" / "filter-repo" / "already_ran").unlink(missing_ok=True)


def rewrite(root: Path, home: Path, identity: str, force: bool, runner: Runner = run_in) -> None:
    """Run the rewrite of [root]'s history; the rules and the mailmap exist only while [runner] runs.

    ```
    rewrite(Path("."), Path.home(), IDENTITY, force=True)
    ```
    """
    with tempfile.TemporaryDirectory(prefix="rewrite-history-") as scratch:
        rules, mailmap = Path(scratch) / "rules.txt", Path(scratch) / "mailmap.txt"
        rules.write_text(rules_text(root, home))
        mailmap.write_text(mailmap_text(old_emails(root), identity))
        forget_previous_run(root)
        runner(filter_repo_argv(rules, mailmap, force), root)


def main(argv: list[str]) -> int:
    """Rewrite [REPO]'s history in place; `--force` is required once `git filter-repo` has run here."""
    parser = argparse.ArgumentParser(description="Rewrite history to remove the host's markers.")
    parser.add_argument("--force", action="store_true", help="pass --force to git filter-repo")
    arguments = parser.parse_args(argv)
    rewrite(REPO, Path.home(), IDENTITY, arguments.force)
    print(f"rewrote the history of {REPO}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
