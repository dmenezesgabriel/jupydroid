#!/usr/bin/env python3
"""How many places in ALL of history still carry what a stranger must not read.

**Why this exists.** `read_personal_data.py` counts the tree, and a history rewrite is not judged by
the tree: a blob that once held the host's home path is in every clone until the commit that added it
is gone. This counts the history itself, so the same command gives the baseline before the rewrite,
the verdict on its rehearsal, and the verdict on the fresh clone of the new remote.

**A measurement, not a gate.** It exits 1 on any hit so a run can be chained, but it is not wired into
pre-commit: `git log --all -p` reads the patch of every commit, about 57 MB here.

Classes (each a count of a different thing, named in the report line):

- home path in blobs: commits whose diff adds or removes the host's `/home/<name>` (`git log -S`);
- home path in messages: message lines carrying it (`--replace-text` leaves messages alone);
- home path in file names: distinct paths ever carrying the account name;
- paths under the local evidence directory: distinct paths ever tracked there, since evidence is local and
  no commit may carry it;
- commits adding a credential: commits with an added line holding a Jupyter server credential anywhere in the
  tree, read with `TOKEN_CARRIERS` from `check_device_secrets.py`, so the two cannot drift.

The commit identity is not a class: after the rewrite exactly one is expected, which is not a zero.
Read it with `git log --all --format='%an <%ae>|%cn <%ce>' | sort | uniq -c`.

```sh
python3 scripts/read_history_markers.py
```

Tests in read_history_markers_test.py.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from check_device_secrets import TOKEN_CARRIERS, home_rule

REPO = Path(__file__).resolve().parent.parent
CLASS_NAMES = (
    "home path in blobs",
    "home path in messages",
    "home path in file names",
    "paths under evidence/",
    "commits adding a credential",
)
#: Starts each commit in the `git log -p` output; a diff line never begins with a NUL. An argv cannot carry
#: one, so git is asked for it by its `%x00` escape.
COMMIT_START = "\0"
COMMIT_FORMAT = "--format=%x00%H"


def _git_lines(root: Path, *args: str) -> list[str]:
    """Lines `git -C root args` printed; a failing git raises with its own stderr."""
    done = subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True, errors="replace", check=True)
    return done.stdout.splitlines()


def home_blob_commits(root: Path, home: Path) -> int:
    """Commits whose diff adds or removes [home]; 0 when [home] names no account."""
    if home_rule(home) is None:
        return 0
    return len(_git_lines(root, "log", "--all", f"-S{home}", "--format=%H"))


def home_message_lines(root: Path, home: Path) -> int:
    """Commit-message lines containing [home]; 0 when [home] names no account."""
    if home_rule(home) is None:
        return 0
    return sum(str(home) in line for line in _git_lines(root, "log", "--all", "--format=%B"))


def home_file_names(root: Path, home: Path) -> int:
    """Distinct paths, across all history, that contain the account name of [home]."""
    if home_rule(home) is None:
        return 0
    return sum(home.name in name for name in _history_paths(root))


def _history_paths(root: Path) -> set[str]:
    """Every distinct path that any commit reachable from any ref touched."""
    return set(_git_lines(root, "log", "--all", "--name-only", "--format="))


def evidence_paths(root: Path) -> int:
    """Distinct paths under the local evidence directory across all history; evidence is local, so any is a miss."""
    return sum(name.startswith("evidence/") for name in _history_paths(root))


def _adds_credential(line: str) -> bool:
    """Whether a `git log -p` line is an added line holding a credential; `+++ ` is a file header."""
    if not line.startswith("+") or line.startswith("+++ "):
        return False
    return any(carrier.pattern.search(line) for carrier in TOKEN_CARRIERS)


def credential_commits(root: Path) -> int:
    """Commits that added a Jupyter server credential to any file.

    ```
    credential_commits(Path("."))  # -> 0 once the rewrite has scrubbed every carrier
    ```
    """
    flagged: set[str] = set()
    commit = ""
    for line in _git_lines(root, "log", "--all", "-p", "-U0", "--no-color", COMMIT_FORMAT):
        if line.startswith(COMMIT_START):
            commit = line
        elif _adds_credential(line):
            flagged.add(commit)
    return len(flagged)


def count_markers(root: Path, home: Path) -> dict[str, int]:
    """One count per class in CLASS_NAMES, over every commit reachable from any ref.

    ```
    count_markers(Path("."), Path("/home/ana"))["home path in messages"]  # -> 0 on a clean history
    ```
    """
    return {
        "home path in blobs": home_blob_commits(root, home),
        "home path in messages": home_message_lines(root, home),
        "home path in file names": home_file_names(root, home),
        "paths under evidence/": evidence_paths(root),
        "commits adding a credential": credential_commits(root),
    }


def report(counts: dict[str, int]) -> str:
    """One `class: N` line per class, in the order given."""
    return "\n".join(f"{name}: {count}" for name, count in counts.items())


def main(root: Path = REPO, home: Path | None = None) -> int:
    """Print the counts of [root]'s history; 1 when any class is above zero, else 0."""
    counts = count_markers(root, Path.home() if home is None else home)
    print(report(counts))
    return int(any(counts.values()))


if __name__ == "__main__":
    sys.exit(main())
