#!/usr/bin/env python3
"""How many places in ALL of history still carry what a stranger must not read.

**Why this exists.** `read_personal_data.py` counts the tree, and a history rewrite is not judged by
the tree: a blob that once held the host's home path is in every clone until the commit that added it
is gone. This counts the history itself, so the same command gives the baseline before the rewrite,
the verdict on its rehearsal, and the verdict on the fresh clone of the new remote.

**A measurement, not a gate.** It exits 1 on any hit so a run can be chained, but it is not wired into
pre-commit: `git log --all -p` over the carrier paths reads about ten megabytes.

Classes (each a count of a different thing, named in the report line):

- home path in blobs: commits whose diff adds or removes the host's `/home/<name>` (`git log -S`);
- home path in messages: message lines carrying it (`--replace-text` leaves messages alone);
- home path in file names: distinct paths ever carrying the account name;
- token carrier paths: listed paths with a commit that added a Jupyter server credential, read with
  `TOKEN_CARRIERS` from `check_device_secrets.py`, so the two cannot drift.

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
CARRIER_PATHS_FILE = "evidence/aud-013-scrub-list/token-carrier-paths.txt"
CLASS_NAMES = (
    "home path in blobs",
    "home path in messages",
    "home path in file names",
    "token carrier paths",
)
ADDED_FILE_HEADER = "+++ b/"


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
    names = set(_git_lines(root, "log", "--all", "--name-only", "--format="))
    return sum(home.name in name for name in names)


def _added_lines_by_path(root: Path, paths: list[str]) -> dict[str, list[str]]:
    """Every line a commit added, grouped by the file it was added to, over [paths] only."""
    added: dict[str, list[str]] = {}
    current = ""
    for line in _git_lines(root, "log", "--all", "-p", "-U0", "--no-color", "--format=", "--", *paths):
        if line.startswith(ADDED_FILE_HEADER):
            current = line.removeprefix(ADDED_FILE_HEADER).rstrip("\t")
        elif line.startswith("+") and current:
            added.setdefault(current, []).append(line[1:])
    return added


def token_carrier_paths(root: Path, paths: list[str]) -> int:
    """How many of [paths] had a credential added to them by some commit.

    ```
    token_carrier_paths(Path("."), ["evidence/x/server.log"])  # -> 1 while history holds a token there
    ```
    """
    if not paths:
        return 0
    added = _added_lines_by_path(root, paths)
    return sum(
        any(carrier.pattern.search(line) for carrier in TOKEN_CARRIERS for line in lines) for lines in added.values()
    )


def count_markers(root: Path, home: Path, carrier_paths: list[str]) -> dict[str, int]:
    """One count per class in CLASS_NAMES, over every commit reachable from any ref.

    ```
    count_markers(Path("."), Path("/home/ana"), [])["home path in messages"]  # -> 0 on a clean history
    ```
    """
    return {
        "home path in blobs": home_blob_commits(root, home),
        "home path in messages": home_message_lines(root, home),
        "home path in file names": home_file_names(root, home),
        "token carrier paths": token_carrier_paths(root, carrier_paths),
    }


def report(counts: dict[str, int]) -> str:
    """One `class: N` line per class, in the order given."""
    return "\n".join(f"{name}: {count}" for name, count in counts.items())


def main(root: Path = REPO, home: Path | None = None) -> int:
    """Print the counts of [root]'s history; 1 when any class is above zero, else 0."""
    listed = (root / CARRIER_PATHS_FILE).read_text().split()
    counts = count_markers(root, Path.home() if home is None else home, listed)
    print(report(counts))
    return int(any(counts.values()))


if __name__ == "__main__":
    sys.exit(main())
