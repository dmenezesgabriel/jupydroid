#!/usr/bin/env python3
"""Rewrites the commit SHAs that tracked text cites, after a history rewrite changed every SHA.

**Why a script.** `git filter-repo` rewrites commits, not the prose that names them: a comment's
`Fixes: <12-hex>`, a doc's `git log --oneline` line and an ADR's commit link all dangle once every SHA
is new. `.git/filter-repo/commit-map` is the one place that says old -> new, and it exists only after the
rewrite, so the remap is a separate step run on its output (ADR-0015 did it once as a pasted snippet).

**What is a citation.** A run of 7 to 12 lowercase hex with no letter, digit or underscore on either side
that prefixes exactly one *old* commit. Most hex-looking tokens in the tree are not commits, so a token
that prefixes none is left alone (an upstream project's pinned SHA, another hash). One that prefixes two
or more refuses the whole run: guessing would point a citation at the wrong commit. Edits are made on
bytes and only to tracked files without a NUL in the first 8 KiB, so a binary file, an ignored directory
and an encoding are never touched or guessed. A cited prefix keeps its length: `f5ff4fcd` stays 8 hex.

Nothing is written until every file has been read, so an ambiguity in the last file leaves the first one
as it was.

```sh
python3 scripts/remap_cited_shas.py --dry-run   # counts, writes nothing
python3 scripts/remap_cited_shas.py             # after the rewrite, from .git/filter-repo/commit-map
```

Tests in remap_cited_shas_test.py.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_COMMIT_MAP = REPO / ".git" / "filter-repo" / "commit-map"
#: `git filter-repo` skips a blob with a NUL in its first 8 KiB (`git_filter_repo.py`, `_tweak_blob`); so does this.
BINARY_SNIFF_BYTES = 8192
SHORT_LENGTH = 7
SHA_TOKEN = re.compile(rb"(?<![0-9A-Za-z_])[0-9a-f]{7,12}(?![0-9A-Za-z_])")
FULL_SHA = re.compile(r"[0-9a-f]{40}")


@dataclass(frozen=True)
class RemapReport:
    """How much a run rewrote, or would have: files changed and cited SHAs inside them."""

    files: int
    tokens: int


class CommitMap:
    """Old commit SHAs to their new ones, searchable by any 7 to 12 hex prefix of an old one."""

    def __init__(self, old_to_new: dict[str, str]) -> None:
        self._old_to_new = old_to_new
        self._by_short: dict[str, list[str]] = defaultdict(list)
        for old in old_to_new:
            self._by_short[old[:SHORT_LENGTH]].append(old)

    def new_for(self, token: str) -> str | None:
        """The new SHA, cut to [token]'s length, for the one old commit [token] prefixes; `None` for none.

        A prefix of several old commits raises, naming the token and every candidate.

        ```
        CommitMap({"ab" * 20: "cd" * 20}).new_for("abababab")  # -> "cdcdcdcd"
        ```
        """
        found = [old for old in self._by_short.get(token[:SHORT_LENGTH], []) if old.startswith(token)]
        if len(found) > 1:
            raise ValueError(f"cited SHA {token!r}: expected a prefix of one old commit; got {sorted(found)}")
        return self._old_to_new[found[0]][: len(token)] if found else None


def load_commit_map(text: str) -> CommitMap:
    """Parse `git filter-repo`'s `commit-map`: an `old new` header, then one `<40-hex> <40-hex>` per line.

    ```
    load_commit_map("old new\\n" + "ab" * 20 + " " + "cd" * 20 + "\\n")
    ```
    """
    pairs: dict[str, str] = {}
    for line in text.splitlines():
        fields = line.split()
        if not fields or fields == ["old", "new"]:
            continue
        if len(fields) != 2 or not all(FULL_SHA.fullmatch(field) for field in fields):
            raise ValueError(f"commit-map line: expected '<40-hex> <40-hex>'; got {line!r}")
        pairs[fields[0]] = fields[1]
    return CommitMap(pairs)


def remap_content(content: bytes, commits: CommitMap) -> tuple[bytes, int]:
    """[content] with each cited old SHA replaced by its new one, and how many were.

    One pass, so a new SHA written here is never read as an old one.
    """
    count = 0

    def replace(match: re.Match[bytes]) -> bytes:
        nonlocal count
        new = commits.new_for(match.group().decode())
        count += new is not None
        return match.group() if new is None else new.encode()

    return SHA_TOKEN.sub(replace, content), count


def tracked_files(root: Path) -> list[Path]:
    """Every file `git ls-files` lists under [root], as absolute paths."""
    done = subprocess.run(["git", "-C", str(root), "ls-files", "-z"], capture_output=True, check=True)
    return [root / name.decode() for name in done.stdout.split(b"\0") if name]


def _remapped_text_file(path: Path, commits: CommitMap) -> tuple[bytes, int] | None:
    """[path]'s new bytes and token count, or `None` when it is binary or cites nothing to rewrite."""
    content = path.read_bytes()
    if b"\0" in content[:BINARY_SNIFF_BYTES]:
        return None
    try:
        updated, count = remap_content(content, commits)
    except ValueError as error:
        raise ValueError(f"{path}: {error}") from error
    return (updated, count) if count else None


def remap_tree(root: Path, commits: CommitMap, dry_run: bool) -> RemapReport:
    """Rewrite the cited SHAs of every tracked text file under [root]; [dry_run] only counts them.

    ```
    remap_tree(Path("."), load_commit_map(Path(".git/filter-repo/commit-map").read_text()), dry_run=True)
    ```
    """
    changes = {path: change for path in tracked_files(root) if (change := _remapped_text_file(path, commits))}
    if not dry_run:
        for path, (updated, _) in changes.items():
            path.write_bytes(updated)
    return RemapReport(files=len(changes), tokens=sum(count for _, count in changes.values()))


def main(argv: list[str], root: Path = REPO) -> int:
    """Remap [root]'s tracked text from a `commit-map`; `--dry-run` prints the counts and writes nothing."""
    parser = argparse.ArgumentParser(description="Rewrite cited commit SHAs from a git filter-repo commit-map.")
    parser.add_argument("--commit-map", type=Path, default=DEFAULT_COMMIT_MAP)
    parser.add_argument("--dry-run", action="store_true", help="count what would change; write nothing")
    arguments = parser.parse_args(argv)
    commits = load_commit_map(arguments.commit_map.read_text())
    report = remap_tree(root, commits, arguments.dry_run)
    verb = "would remap" if arguments.dry_run else "remapped"
    print(f"{verb} {report.tokens} cited SHAs in {report.files} files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
