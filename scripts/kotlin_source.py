#!/usr/bin/env python3
"""Reading Kotlin source the way a gate needs to: without its prose.

**A comment is not a point of use, and two gates in this repository have now learned that the hard
way.** `check_palette_consumers.py` reported a palette field as *read* because a KDoc in
`terminal/TerminalPalette.kt` named it (fixed there). `check_ui_dimensions.py` then refused
`theme/CornerRadius.kt` over the sentence *"`TOGGLE_CORNER = 8.dp`, a number chosen against
nothing"* - prose whose whole job is to explain the rule the gate enforces. Same defect,
two gates, so the scanner has one home rather than a copy each.

**A scanner rather than a regex**, for the reason `resolve_vscode_theme.strip_jsonc` is one: a
regex that treats `//` as a comment anywhere eats the second half of `"https://x/y"`, and whatever
is named after that on the same line disappears with it. Block comments **nest**, which Kotlin
allows and JSON does not, so the closer is counted rather than searched for.

**Comments are blanked, not deleted**, which is what lets a gate keep reporting line numbers:
`check_ui_dimensions.py` names a line and reads the line *above* a declaration to find its KDoc, and
a scanner that swallowed a block comment's newlines would renumber the rest of the file. The result
is the same length as the source, character for character.

Tests in kotlin_source_test.py.
"""

from __future__ import annotations


def without_comments(source: str) -> str:
    """Kotlin source with its comments blanked to spaces and its string literals left alone.

    Newlines survive inside a blanked comment, so every offset and every line number in the result
    is the one it had in [source].

    ```
    without_comments('// why\\nval c = palette.cell')   # '      \\nval c = palette.cell'
    ```

    Args:
        source: the contents of one `.kt` file.

    Returns:
        The same text with every comment replaced by spaces, the same length as what went in.
    """
    out: list[str] = []
    at, end = 0, len(source)
    while at < end:
        rest = source[at:]
        if rest.startswith("//"):
            was = at
            at = source.find("\n", at)
            at = end if at < 0 else at
            out.append(_blanked(source[was:at]))
        elif rest.startswith("/*"):
            was = at
            at = _past_block_comment(source, at)
            out.append(_blanked(source[was:at]))
        elif rest.startswith('"""') or source[at] in "\"'":
            was = at
            at = _past_literal(source, at)
            out.append(source[was:at])
        else:
            out.append(source[at])
            at += 1
    return "".join(out)


def comments_only(source: str) -> str:
    """Kotlin source with its *code* blanked to spaces and only its comments left.

    The complement of [without_comments], sharing its scanner so the two cannot disagree about where
    a comment starts. Offsets and line numbers are the ones [source] had, which is what lets a gate
    report a line.

    ```
    comments_only('// why\\nval c = palette.cell')   # '// why\\n                   '
    ```

    Args:
        source: the contents of one `.kt` file.

    Returns:
        The same text with every non-comment character replaced by a space, the same length as what
        went in.
    """
    out: list[str] = []
    at, end = 0, len(source)
    while at < end:
        rest = source[at:]
        if rest.startswith("//"):
            was = at
            at = source.find("\n", at)
            at = end if at < 0 else at
            out.append(source[was:at])
        elif rest.startswith("/*"):
            was = at
            at = _past_block_comment(source, at)
            out.append(source[was:at])
        elif rest.startswith('"""') or source[at] in "\"'":
            was = at
            at = _past_literal(source, at)
            out.append(_blanked(source[was:at]))
        else:
            out.append("\n" if source[at] == "\n" else " ")
            at += 1
    return "".join(out)


def _blanked(comment: str) -> str:
    """One comment as the whitespace that stands in for it, its own newlines kept."""
    return "".join("\n" if char == "\n" else " " for char in comment)


def _past_block_comment(source: str, at: int) -> int:
    """The index just past the `/*` opening at `at`, counting nested openers as Kotlin does."""
    depth, end = 0, len(source)
    while at < end:
        if source.startswith("/*", at):
            depth, at = depth + 1, at + 2
        elif source.startswith("*/", at):
            depth, at = depth - 1, at + 2
            if depth == 0:
                return at
        else:
            at += 1
    return end


def _past_literal(source: str, at: int) -> int:
    """The index just past the string or character literal starting at `at`."""
    quote = '"""' if source.startswith('"""', at) else source[at]
    at, end = at + len(quote), len(source)
    while at < end:
        if quote != '"""' and source[at] == "\\":
            at += 2
        elif source.startswith(quote, at):
            return _past_quote_run(source, at) if len(quote) == 3 else at + len(quote)
        else:
            at += 1
    return end


def _past_quote_run(source: str, at: int) -> int:
    """The index just past the run of quotes that closes a raw string.

    Kotlin closes one at the **last** triple of the run - its grammar spells the closer
    `'"'* '\"\"\"'` - so a literal written with four quotes each side holds one quote of content.
    Stopping at the first triple left a quote over to open a fresh literal, and every comment for
    the rest of the file was read as string content: `DiskWorkspaceSearcherTest` carried a citation
    of a deleted document past `check_doc_citations.py` that way, and four other test files hid 86
    comment lines between them.
    """
    while at < len(source) and source[at] == '"':
        at += 1
    return at
