#!/usr/bin/env python3
"""Working Agreement (CLAUDE.md): a gap this app draws has a name, and the name is in one place.

Measured before this gate existed: **63 `.dp` literals across 20 files in 16 distinct values, 13
of them off Material's 4dp grid** - `14.dp` three times in the explorer and its recents, and one
each of `6`, `3`, `2`, `5`, `18`, `1`. No single one of those was wrong. What was wrong is that a
gap had no name, so the next one was chosen by eye against whatever a neighbouring file happened
to use, and two surfaces drawn a week apart disagreed by 2dp with nothing saying which was meant.

**The rule is about the point of use, not about the number.** A literal buried in a modifier chain
is a decision nobody can find, compare or change; the same number in a named `val` with a KDoc
above it is one the next reader can. So:

  Modifier.padding(horizontal = 16.dp)        <- refused
  Modifier.padding(horizontal = Spacing.lg)   <- the scale

  private val SIDE_PADDING = 16.dp            <- refused: named, but says nothing
  /** `codeCellLeftLinePadding`. */
  private val STRIPE_GAP = 5.dp               <- allowed: the KDoc is the justification

That second pair is why the gate is not simply "no literals outside `theme/Spacing.kt`". Not every
dimension is a gap. `CellStripe`'s 4dp and 2dp are `DefaultNotebookEditorAppearanceSizes`,
`CellFrame`'s 5dp is `codeCellLeftLinePadding`, `TOC_PANE_WIDTH` is a pane and `MAX_HEIGHT` is a
cap - none of those belongs on a spacing scale, and forcing them onto one would make the scale a
list of unrelated numbers again with an `object` around it. What they owe is a sentence saying
where they came from, which `CellFrame` and `CellStripe` were already paying before this gate.

`0.dp` is exempt: it is a sentinel - `if (width <= 0.dp)` - not a spacing decision.

**And a number a comment is talking *about* is not a use of it.** This gate read raw file text
until P7a-3, when `theme/CornerRadius.kt` arrived with a KDoc explaining which literal it had
retired - `` `TOGGLE_CORNER = 8.dp`, a number chosen against nothing `` - and the gate refused
the file over its own justification. That is the same defect `check_palette_consumers.py` had
and fixed one step earlier from the other side, so the scanner has one home:
`kotlin_source.without_comments`, which blanks comments **to spaces** rather than deleting them
so this file's line numbers and its read of the line above a declaration still hold.

Tests in check_ui_dimensions_test.py, including one that runs the gate over this repository, so a
gate that has stopped matching anything fails rather than passing quietly.
"""
from __future__ import annotations

import pathlib
import re
import sys

from kotlin_source import without_comments

UI_SOURCE = pathlib.Path("app/src/main/java/com/jupydroid/app")
#: The scale is where the numbers live, so it is the one file allowed to spell them out.
THE_SCALE = "theme/Spacing.kt"
#: Any `<number>.dp`. `0.dp` is excluded here rather than filtered later so the message never
#: names a sentinel as an offence.
LITERAL = re.compile(r"(?<![\w.])(?!0\.dp)(\d+(?:\.\d+)?)\.dp\b")
#: A declaration whose whole point is to name one dimension. Anything else on the line - a
#: modifier chain, a call argument - is a use rather than a declaration.
DECLARATION = re.compile(r"^\s*(?:private |internal |public )?val\s+(\w+)\s*(?::\s*Dp\s*)?=\s*\S+\.dp\s*$")
#: The last line of a KDoc block, which is what a declaration has to be preceded by.
KDOC_END = re.compile(r"^\s*(?:/\*\*.*\*/|\*/)\s*$")


def offences_in(path: pathlib.Path, root: pathlib.Path) -> list[str]:
    """Every literal in one file that the rule above refuses, as a printable line each."""
    relative = path.relative_to(root).as_posix()
    if relative.endswith(THE_SCALE):
        return []
    source = path.read_text()
    # Two readings of the same file, and they are not interchangeable. Literals are counted in the
    # *code*, so a KDoc naming a number it retired is not an offence; the KDoc check and the
    # message read the **source**, because a blanked comment is whitespace and neither would work.
    lines = source.splitlines()
    code = without_comments(source).splitlines()
    found: list[str] = []
    for at, line in enumerate(lines):
        literals = LITERAL.findall(code[at])
        if not literals:
            continue
        declared = DECLARATION.match(line)
        if declared and _is_documented(lines, at):
            continue
        found += [_message(relative, at, line, declared, value) for value in literals]
    return found


def _is_documented(lines: list[str], at: int) -> bool:
    """Whether the declaration on [at] has a KDoc block closing on the line directly above it.

    Directly above, with no blank line between: a comment separated from what it describes is one
    the next edit moves away from, and the point of the rule is that the justification travels
    with the number.
    """
    return at > 0 and bool(KDOC_END.match(lines[at - 1]))


def _message(relative: str, at: int, line: str, declared: re.Match | None, value: str) -> str:
    if declared:
        return (
            f"{relative}:{at + 1}: `{declared.group(1)}` is {value}.dp with no KDoc saying why - "
            f"put the reason above it, or take the value from `Spacing`"
        )
    return (
        f"{relative}:{at + 1}: {value}.dp inline - use `Spacing`, or name it in a documented "
        f"`val`: {line.strip()}"
    )


def offences(root: pathlib.Path | None = None) -> list[str]:
    """Every offence in the app's own sources, in path order.

    Raises:
        FileNotFoundError: if [UI_SOURCE] is not under [root]. `rglob` over a directory that is
            not there yields nothing, so without this the gate reports a clean tree from the wrong
            working directory - which is the one failure a checker must not have, and which this
            one had for exactly as long as it took its own test to run from `scripts/`.
    """
    base = root or pathlib.Path(".")
    source = base / UI_SOURCE
    if not source.is_dir():
        raise FileNotFoundError(f"no {UI_SOURCE} under {base.resolve()}; run this from the repository root")
    found: list[str] = []
    for path in sorted(source.rglob("*.kt")):
        found += offences_in(path, source)
    return found


def main() -> int:
    found = offences()
    for offence in found:
        print(offence, file=sys.stderr)
    if found:
        name = pathlib.Path(__file__).name
        print(f"\n{len(found)} unnamed dimension(s). See scripts/{name} for why.", file=sys.stderr)
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
