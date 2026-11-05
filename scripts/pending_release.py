#!/usr/bin/env python3
"""Says whether `semantic-release version --print` announces a release that has not been tagged yet.

`--print` is not a yes/no answer: with nothing to bump it echoes the current version, and with no tag at
all and nothing bumpable it prints `0.0.0`, which is not a release. The release cut therefore asks two
questions of its output, and this script is the one place that answers them: is the printed version a
real one, and is its tag still missing.

Usage:
    uv run semantic-release version --print | python scripts/pending_release.py $(git tag --list 'v*')
    # 0.2.0        (printed only when v0.2.0 is not among the tags; nothing is printed otherwise)
"""

from __future__ import annotations

import re
import sys
from typing import TextIO

VERSION_LINE = re.compile(r"^(\d+\.\d+\.\d+)$", re.MULTILINE)

# python-semantic-release prints this for a history with no tag and no feat, fix or perf commit.
NO_RELEASE_VERSION = "0.0.0"


def pending_release(printed: str, existing_tags: list[str]) -> str | None:
    """The version to release, or None when `printed` announces none.

    Example:
        >>> pending_release("0.2.0\\n", ["v0.1.0"])
        '0.2.0'
        >>> pending_release("0.1.0\\n", ["v0.1.0"]) is None
        True
    """
    versions: list[str] = VERSION_LINE.findall(printed)
    if not versions:
        return None
    version = versions[-1]
    if version == NO_RELEASE_VERSION or f"v{version}" in existing_tags:
        return None
    return version


def main(existing_tags: list[str], stdin: TextIO) -> int:
    """Reads PSR's `--print` output from `stdin`; prints the pending version, or nothing. Always exits 0."""
    pending = pending_release(stdin.read(), existing_tags)
    if pending is not None:
        print(pending)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], sys.stdin))
