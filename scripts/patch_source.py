#!/usr/bin/env python3
"""Reading a unified diff the way a gate needs to: the comments it adds, and nothing else.

A `.patch` has no comment syntax of its own - its prose is the target file's, inside the lines
the diff adds, so `tools/proot/patches/` arrived as seven files with no scanner and failed
`check_doc_citations.py` by name. Only `+` lines are read: context and `-` lines are the patched
file's own text, pinned by the tarball's sha512 rather than by this repository, and the `+++ `
header is a path, not prose. Each added line loses its diff marker and
`kotlin_source.comments_only` reads it as the C `//` and `/* */` the patches under `tools/` write
- proot is C, and a patch adding another language's comments is a gap a reviewer sees, not a line
this scanner can tell from `#define`.

One output line per input line, blanked or kept, so a line number a gate reports is the patch's
own.

Tests in patch_source_test.py.
"""

from __future__ import annotations

from kotlin_source import comments_only


def patch_prose(source: str) -> str:
    """The comments a unified diff adds, with everything else blanked to spaces.

    ```
    patch_prose('@@\n // kept\n-old // gone\n+// added\n')   # the `// added` line only
    ```

    Args:
        source: the contents of one `.patch` file.

    Returns:
        The same number of lines as [source], each added line stripped of its `+` marker and read
        for C comments, every other line blank.
    """
    added = "".join(line[1:] if _adds(line) else _blanked(line) for line in source.splitlines(keepends=True))
    return comments_only(added)


def _adds(line: str) -> bool:
    """Whether a diff line is an addition: `+` content, not the `+++ ` file header."""
    return line.startswith("+") and not line.startswith("+++ ")


def _blanked(line: str) -> str:
    """One non-added line as the whitespace that stands in for it, its newline kept."""
    return "\n" if line.endswith("\n") else ""
