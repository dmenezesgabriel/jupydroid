#!/usr/bin/env python3
"""Working Agreement (CLAUDE.md): what code looks like has one home, and the name is in one place.

P2l-3a/P2l-3b. Measured before this gate existed: **four sizes for one thing**. The cell's source
stated none at all and resolved to Compose's `DefaultFontSize`, outputs and the folded-cell
placeholder were `bodySmall`, the completion popup `bodyMedium`, and rendered markdown's fenced
blocks `bodySmall` again. JupyterLab has one `--jp-code-font-size` and one `--jp-code-line-height`
for every one of those surfaces (`variables.css:196-197`).

**The rule is where `FontFamily.Monospace` may be written, not what size follows it.** A size is
easy to grep for and easy to keep in step; a *new surface* that reaches for the font directly is
how the four sizes happened, one commit at a time, each one reasonable on its own. So the font has
one home - `ui/editor/EditorTheme.kt` - and every surface takes a named style from it:

  Text(output, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                                        <- refused, wherever it is written
  Text(output, style = codeTextStyle())                 <- the code size and line height
  Text(fenced, style = richTextCodeStyle())             <- prose size, code shape (`inherit`)
  Text(key,    style = symbolKeyStyle())                <- monospace and deliberately not code

The exception lives beside the rule for the same reason: `symbolKeyStyle` is monospace for the
shape of `->` and `Tab` rather than because it is source, and a reader asking "what does code look
like here" finds that stated in the file where every answer is.

Tests in check_monospace_home_test.py, including one that runs the gate over this repository, so a
gate that has stopped matching anything fails rather than passing quietly - which
`check_ui_dimensions.py` did, silently, until its own test was run from the wrong directory.
"""
from __future__ import annotations

import pathlib
import re
import sys

UI_SOURCE = pathlib.Path("app/src/main/java/com/jupydroid/app")
#: The one file allowed to name the font, because it is where every code style is built.
THE_HOME = "ui/editor/EditorTheme.kt"
#: `FontFamily.Monospace` as an expression. A mention inside a comment is prose about the rule.
MONOSPACE = re.compile(r"(?<![\w.])FontFamily\.Monospace\b")
COMMENT = re.compile(r"^\s*(\*|//|/\*)")


def offences_in(path: pathlib.Path, root: pathlib.Path) -> list[str]:
    """Every line in one file that names the font where it may not be named."""
    relative = path.relative_to(root).as_posix()
    if relative.endswith(THE_HOME):
        return []
    found = []
    for at, line in enumerate(path.read_text().splitlines()):
        if COMMENT.match(line) or not MONOSPACE.search(line):
            continue
        found.append(f"{relative}:{at + 1}: {line.strip()}")
    return found


def offences(root: pathlib.Path | None = None) -> list[str]:
    """Every offence under [root], newest-readable first.

    Raises:
        FileNotFoundError: When the UI source tree is not under [root], which means the gate was
            run from somewhere it can match nothing - the failure mode that let
            `check_ui_dimensions.py` report a clean tree with 53 offences in it.
    """
    base = root or pathlib.Path.cwd()
    tree = base / UI_SOURCE
    if not tree.is_dir():
        raise FileNotFoundError(f"no {UI_SOURCE} under {base.resolve()}; run this from the repository root")
    return [line for path in sorted(tree.rglob("*.kt")) for line in offences_in(path, base)]


def main() -> int:
    found = offences()
    if not found:
        return 0
    print("check-monospace-home: the editor's font is named outside its one home", file=sys.stderr)
    for line in found:
        print(f"  {line}", file=sys.stderr)
    print(f"\nTake a named style from {THE_HOME} instead - codeTextStyle(), richTextCodeStyle()", file=sys.stderr)
    print("or symbolKeyStyle() - or add one there, with the reason it is not the others.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
