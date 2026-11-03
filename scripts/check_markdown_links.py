#!/usr/bin/env python3
"""A tracked Markdown file links only to what a fresh clone holds.

**Why this exists.** The front door a stranger reads first is Markdown that links into `docs/`, so
a link that points at nothing is the first thing they meet. Nothing else catches one: a rename
leaves every link to the old name behind, and the gates that read Markdown look for specific
names, not for whether a target exists. A target counts only if it is *tracked*, not merely on the
disk, so a link into a gitignored directory fails here as it would in a fresh clone.

**What it reads.** Inline links, `[text](target)`. A fenced block and an inline code span hold
examples, not links, and are skipped. An address (`https:`, `mailto:`), a same-page `#fragment`
and a link with no path are not files. A `#fragment` or `?query` after a path is cut off before the
path is looked up. Whether the heading a fragment names exists, and whether an address answers,
are not checked.

**Usage.** With no arguments every tracked `*.md` file is read, which is what the hook runs: a
rename breaks links in files the commit never touched, so only the staged files is the wrong set.

    uv run --locked python scripts/check_markdown_links.py            # the whole tree
    uv run --locked python scripts/check_markdown_links.py README.md  # one file

Tests in check_markdown_links_test.py, including one that runs the gate over every tracked file.
"""

from __future__ import annotations

import functools
import pathlib
import posixpath
import re
import subprocess
import sys

FENCED_BLOCK = re.compile(r"^(```|~~~).*?^\1", re.S | re.M)
INLINE_CODE = re.compile(r"`[^`\n]*`")
#: `[text](target)` or `[text](target "title")`; the title is dropped, the target has no spaces.
INLINE_LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
#: `https:`, `mailto:` - anything that names a scheme is not a path in this repository.
ADDRESS = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")


def tracked_files(root: pathlib.Path) -> frozenset[str]:
    """Every path git tracks under `root`, relative to it and with `/` separators."""
    listing = subprocess.run(["git", "ls-files", "-z"], cwd=root, capture_output=True, text=True, check=True)
    return frozenset(name for name in listing.stdout.split("\0") if name)


@functools.cache
def tracked_directories(tracked: frozenset[str]) -> frozenset[str]:
    """Every directory that holds a tracked file, the repository root (`.`) included."""
    return frozenset(str(parent) for name in tracked for parent in pathlib.PurePosixPath(name).parents)


def link_paths(text: str) -> list[str]:
    """The path of every file link in `text`, each once, in order, without fragment or query.

    `DeadLinksTest.test_a_fragment_and_a_query_are_not_part_of_the_path` shows the cut.
    """
    prose = INLINE_CODE.sub("", FENCED_BLOCK.sub("", text))
    targets = [target for target in INLINE_LINK.findall(prose) if not ADDRESS.match(target)]
    return list(dict.fromkeys(path for path in (re.split(r"[?#]", t, maxsplit=1)[0] for t in targets) if path))


def resolve(source: str, path: str) -> str:
    """Where `path`, written in the file `source`, points, from the repository root."""
    if path.startswith("/"):
        return posixpath.normpath(path[1:])
    return posixpath.normpath(posixpath.join(posixpath.dirname(source), path))


def dead_links(source: str, text: str, tracked: frozenset[str]) -> list[str]:
    """The link paths in `text` that are neither a tracked file nor a directory holding one.

    Example: `dead_links("README.md", "[x](gone.md)", frozenset({"README.md"}))` is `["gone.md"]`.
    """
    known = tracked | tracked_directories(tracked)
    return [path for path in link_paths(text) if resolve(source, path) not in known]


def main(
    paths: list[str],
    root: pathlib.Path = pathlib.Path(),
    texts: dict[str, str] | None = None,
    tracked: frozenset[str] | None = None,
) -> int:
    known = tracked if tracked is not None else tracked_files(root)
    failures = 0
    for rel in paths or sorted(name for name in known if name.endswith(".md")):
        text = texts[rel] if texts is not None else (root / rel).read_text(encoding="utf-8", errors="replace")
        for target in dead_links(rel, text, known):
            print(
                f"{rel}: link ({target}) is not a tracked file or directory in a fresh clone - "
                "fix the path, or put the example in backticks"
            )
            failures += 1
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
