#!/usr/bin/env python3
"""Reading XML the way a gate needs to: its comments, and nothing else.

**The Android manifest is prose too.** `check_doc_citations.py` scanned `app/src/**/*.kt` and read
no other language under that tree, so four citations of retired plan steps sat in
`app/src/main/AndroidManifest.xml`'s comments - one of them naming an evidence directory - and the
gate reported the tree clean. That is the same defect as reading a shell script with a scanner that
only knows `//`: the gate looked, found nothing it could parse, and called it success.

**An XML comment cannot nest** (`<!--` inside a comment is literal, and `--` is not even legal
there), which is what makes this shorter than `kotlin_source`. What it does share is the rule that
matters: **comments are blanked, not deleted**, so the result is the same length as the source,
character for character, and every line number a gate reports is the one the file has.

Tests in xml_source_test.py.
"""
from __future__ import annotations

_OPEN, _CLOSE = "<!--", "-->"


def xml_comments(source: str) -> str:
    """One XML file's comments, with its markup blanked to spaces.

    ```
    xml_comments('<a/>\\n<!-- why -->\\n')   # '    \\n<!-- why -->\\n'
    ```

    Args:
        source: the contents of one XML file.

    Returns:
        The same text, character for character, with everything outside a comment replaced by a
        space - newlines excepted, so offsets and line numbers survive.
    """
    kept, at = [], 0
    while at < len(source):
        start = source.find(_OPEN, at)
        if start < 0:
            kept.append(_blanked(source[at:]))
            break
        kept.append(_blanked(source[at:start]))
        end = source.find(_CLOSE, start + len(_OPEN))
        stop = len(source) if end < 0 else end + len(_CLOSE)
        kept.append(source[start:stop])
        at = stop
    return "".join(kept)


def _blanked(markup: str) -> str:
    """The same span with every character but a newline replaced by a space."""
    return "".join("\n" if char == "\n" else " " for char in markup)
