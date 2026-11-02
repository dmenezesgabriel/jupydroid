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

The rules, in order: the token carriers (`evidence/aud-013-scrub-list/token-carriers.txt`), the
gitignored `.sensitive-strings.local`, and the host's own `/home/<name>`
(`read_personal_data.scrub_rules`). Empty commits are kept, so every branch has the commit count it
had. `--force` is needed on a repository that `git filter-repo` has already rewritten once
(`.git/filter-repo/already_ran`), and on any that is not a fresh clone.

```sh
python3 scripts/rewrite_history.py --force   # in the working repository; rehearse on a bundle clone first
```

Tests in rewrite_history_test.py.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from collections.abc import Callable
from pathlib import Path

from check_device_secrets import LOCAL_LITERALS, local_rules_from
from read_personal_data import scrub_rules

REPO = Path(__file__).resolve().parent.parent
CARRIER_RULES_FILE = "evidence/aud-013-scrub-list/token-carriers.txt"
#: The GitHub noreply address of the repository's owner; the name is kept because the docs and ADRs carry it.
IDENTITY = "Gabriel Menezes <50274255+dmenezesgabriel@users.noreply.github.com>"

#: Runs an argv inside a directory; injected so a test can see the argv without a rewrite happening.
Runner = Callable[[list[str], Path], None]


def run_in(argv: list[str], cwd: Path) -> None:
    """Run [argv] in [cwd]; a non-zero exit raises, since a half-rewritten history is worse than none."""
    subprocess.run(argv, cwd=cwd, check=True)


def _git_lines(root: Path, *args: str) -> list[str]:
    """Lines `git -C root args` printed; a failing git raises with its own stderr."""
    done = subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True, errors="replace", check=True)
    return done.stdout.splitlines()


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
    lines = (root / CARRIER_RULES_FILE).read_text().splitlines() + local_text.splitlines()
    return "\n".join([*(line for line in lines if line.strip()), *_home_lines(home)]) + "\n"


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
    """The `git filter-repo` command: one rules file for blobs and messages, empty commits kept."""
    argv = ["git", "filter-repo", "--replace-text", str(rules), "--replace-message", str(rules)]
    argv += ["--mailmap", str(mailmap), "--prune-empty", "never"]
    return [*argv, "--force"] if force else argv


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
