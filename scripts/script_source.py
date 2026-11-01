#!/usr/bin/env python3
"""Reading shell and Python source the way a gate needs to: its prose, and nothing else.

The counterpart of `kotlin_source.comments_only`, and it exists because that scanner was being run
over `.sh`, `.py` and `.c` files by `check_doc_citations.py`. A Kotlin scanner finds no `//` in a
shell script, so every one of those files came back with no prose at all and was reported clean
while carrying citations - 16 of them in `tools/rootfs/`.

**A scanner rather than a regex**, for the same reason `kotlin_source` is one: `echo "# not a
comment"` has a `#` in it and is not a comment, `${#list[@]}` is a length, and `$#` is the argument
count. Quote state is tracked per line, which is where shell strings end for this purpose - a gate
reads prose, and prose does not span a heredoc.

**Comments are kept and code is blanked, not deleted**, so every line number in the result is the
one it had in the source and a gate can name the line it found.

**A Python docstring is prose too**, and it is where this repository's scripts explain themselves -
`tools/rootfs/jupydroid_kernel.py` cites four things from its module docstring and not one from a
`#` comment. [python_prose] reads both; [hash_comments] alone is for shell, which has no docstring.

Tests in script_source_test.py.
"""

from __future__ import annotations

_QUOTES = ("'", '"')


def hash_comments(source: str) -> str:
    """The `#` comments of one shell or Python file, with its code blanked to spaces.

    ```
    hash_comments('OUT=evidence/x  # why\\n')   # '            # why\\n'
    ```

    Args:
        source: the contents of one `.sh`, `.py` or other `#`-commented file.

    Returns:
        The same text, character for character, with everything outside a comment replaced by
        spaces.
    """
    return "".join(_comment_of(line) for line in source.splitlines(keepends=True))


def _comment_of(line: str) -> str:
    """One line's comment, blanked up to where it starts."""
    start = _comment_start(line)
    if start is None:
        return " " * len(line.rstrip("\n")) + line[len(line.rstrip("\n")) :]
    return " " * start + line[start:]


def _comment_start(line: str) -> int | None:
    """The index of the `#` that opens this line's comment, or None when there is none."""
    quote: str | None = None
    index = 0
    while index < len(line):
        char = line[index]
        if char == "\\":
            index += 2
            continue
        if quote:
            quote = None if char == quote else quote
        elif char in _QUOTES:
            quote = char
        elif char == "#" and not _is_expansion(line, index):
            return index
        index += 1
    return None


def _is_expansion(line: str, index: int) -> bool:
    """Whether the `#` at [index] belongs to `${#name}` or `$#` rather than opening a comment."""
    return line[max(0, index - 2) : index] == "${" or line[max(0, index - 1) : index] == "$"


_TRIPLES = ('"""', "'''")


def python_prose(source: str) -> str:
    """One Python file's prose: its `#` comments and its docstrings, with its code blanked.

    ```
    python_prose('x = 1  # why\n')   # '       # why\n'
    ```

    Args:
        source: the contents of one `.py` file.

    Returns:
        The same text, character for character, with everything that is neither a comment nor a
        triple-quoted string replaced by spaces.
    """
    kept = _triple_quoted_spans(source)
    comments = hash_comments(source)
    return "".join(source[i] if i in kept or source[i] == "\n" else comments[i] for i in range(len(source)))


def _triple_quoted_spans(source: str) -> set[int]:
    """Every character index inside a triple-quoted string, the delimiters included."""
    inside: set[int] = set()
    index = 0
    while index < len(source):
        opener = next((q for q in _TRIPLES if source.startswith(q, index)), None)
        if opener is None:
            index += 1
            continue
        end = source.find(opener, index + len(opener))
        end = len(source) if end == -1 else end + len(opener)
        inside.update(range(index, end))
        index = end
    return inside
