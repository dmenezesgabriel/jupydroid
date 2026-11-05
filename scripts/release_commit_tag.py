#!/usr/bin/env python3
"""Names the tag a merged release pull request is owed, from the commit it merged.

The release commit is `chore(release): vX.Y.Z`. How the pull request was merged decides where that subject
sits: a fast-forward or rebase leaves the release commit as the tip, a squash puts it first with ` (#N)`
appended, and a merge commit (this repository's usual merge) makes the tip "Merge pull request #N from ...",
the release commit being its second parent. Reading only the tip tagged nothing for the first release.

Usage:
    python scripts/release_commit_tag.py . "$MERGE_SHA"
    # v0.1.0        (nothing is printed when the commit is not a release; the exit status is still 0)
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

RELEASE_SUBJECT = re.compile(r"chore\(release\): (v\d+\.\d+\.\d+)(?: \(#\d+\))?")


def release_tag_of_subjects(subjects: list[str]) -> str | None:
    """Returns the tag named by the first subject that is a release commit, or None.

    Example:
        >>> release_tag_of_subjects(["Merge pull request #24 from o/release/next", "chore(release): v0.1.0"])
        'v0.1.0'
    """
    for subject in subjects:
        match = RELEASE_SUBJECT.fullmatch(subject)
        if match is not None:
            return match.group(1)
    return None


def _subject(repo: Path, revision: str) -> str | None:
    done = subprocess.run(
        ["git", "log", "-1", "--format=%s", revision], cwd=repo, capture_output=True, text=True, check=False
    )
    return done.stdout.strip() if done.returncode == 0 else None


def release_tag_of_merge(repo: Path, merge_sha: str) -> str | None:
    """Returns the tag owed to `merge_sha`: its own subject first, then its second parent's.

    Raises:
        ValueError: `merge_sha` is not a commit of the repository.
    """
    own = _subject(repo, merge_sha)
    if own is None:
        raise ValueError(f"{merge_sha!r} is not a commit in {repo}; expected the pull request's merge commit sha")
    return release_tag_of_subjects([own, _subject(repo, f"{merge_sha}^2") or ""])


def main(argv: list[str]) -> int:
    """Prints the tag owed to `argv[1]` in the repository `argv[0]`; exits 1 on bad arguments or a bad sha."""
    if len(argv) != 2:
        print(f"expected a repository path and a merge commit sha, got {argv!r}", file=sys.stderr)
        return 1
    try:
        tag = release_tag_of_merge(Path(argv[0]), argv[1])
    except ValueError as refusal:
        print(refusal, file=sys.stderr)
        return 1
    if tag is not None:
        print(tag)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
