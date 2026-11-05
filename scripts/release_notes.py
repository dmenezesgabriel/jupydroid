#!/usr/bin/env python3
"""Cuts one tag's section out of CHANGELOG.md, to be the body of its GitHub Release.

python-semantic-release writes `## vX.Y.Z (YYYY-MM-DD)` per release, newest first, and the release commit
carries that file, so the tag's own checkout holds its notes. `semantic-release publish` is not used here:
it refuses the detached HEAD a tag-triggered job checks out. A prefix match on `## v1.2.3` would also
take `## v1.2.30`, so the header is matched whole.

Usage:
    python scripts/release_notes.py CHANGELOG.md v1.2.3 > release-notes.md
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

NEXT_RELEASE_HEADER = re.compile(r"^## v\d", re.MULTILINE)


def extract_release_notes(changelog: str, tag: str) -> str:
    """Returns the body under the tag's `## vX.Y.Z (date)` header, up to the next release header.

    Example:
        >>> extract_release_notes("## v1.0.0 (2026-11-05)\\n\\n- one\\n", "v1.0.0")
        '- one\\n'

    Raises:
        ValueError: the changelog has no section for the tag, or the section is empty.
    """
    header = re.search(rf"^## {re.escape(tag)} \(\d{{4}}-\d{{2}}-\d{{2}}\)[ \t]*\n", changelog, re.MULTILINE)
    if header is None:
        raise ValueError(f"CHANGELOG.md has no section for {tag!r}; expected a line '## {tag} (YYYY-MM-DD)'")
    rest = changelog[header.end() :]
    following = NEXT_RELEASE_HEADER.search(rest)
    body = (rest[: following.start()] if following else rest).strip()
    if not body:
        raise ValueError(f"the CHANGELOG.md section for {tag!r} is empty; expected at least one release note")
    return body + "\n"


def main(argv: list[str]) -> int:
    """Prints the notes of `argv[1]` from the file `argv[0]`; exits 1 with the reason on stderr otherwise."""
    if len(argv) != 2:
        print(f"expected a changelog path and a release tag such as 'v1.2.3', got {argv!r}", file=sys.stderr)
        return 1
    try:
        notes = extract_release_notes(Path(argv[0]).read_text(encoding="utf-8"), argv[1])
    except (OSError, ValueError) as refusal:
        print(refusal, file=sys.stderr)
        return 1
    sys.stdout.write(notes)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
