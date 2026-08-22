#!/usr/bin/env python3
"""Working Agreement (CLAUDE.md): a palette states colours something draws, and nothing else.

`ColorRamp` has carried the rule in prose since P2u-1 - *"A position with no consumer is not here.
The ramp holds what `Theme.kt` maps a role onto and nothing else; the resolved themes have many
more greys than these, and carrying the unused ones would be a list nobody could tell was stale."*
`EditorPalette` had no such gate, and P2u-3 found exactly what the prose predicts: `caretRow` and
`outputForeground` were declared, given a value in **both** palettes, cited to an IntelliJ
attribute key, and read by nothing at all.

That is worse than an unused variable. Each one is two hex values a reader takes for a statement
about how the app draws, and a re-sourcing step has to look up, measure and transcribe a colour
for a surface that does not exist. This gate is what stops the next one being added.

**A consumer is production code**, `app/src/main/…`, outside the palette's own two files. A field
read only by a test is not drawn by anything, which is the case this exists to catch - the test
would keep passing and the field would keep lying.

Two things are deliberately outside the rule, and both are here rather than in a comment nobody
reads. **`SyntaxPalette` is not checked**: its fields are consumed by `styles()`, which lives in
the declaration file by design, so "outside the palette's own files" is the wrong question to ask
of them - `EditorPaletteTest.everySyntaxKindButPlainIsStyled` is what holds that set complete.
And **`editorBackground` is exempt**, because it is drawn before there is a composition: the window
takes it from `res/values{,-night}/colors.xml`, a duplication `AppWindowTest` exists to keep
honest. It is the one field whose consumer is not Kotlin.

Tests in check_palette_consumers_test.py, including one that runs the gate over this repository,
so a gate that has stopped matching anything fails rather than passing quietly.
"""
from __future__ import annotations

import pathlib
import re
import sys

MAIN = pathlib.Path("app/src/main/java/com/jupydroid/app")
#: Where the palette is declared and where its two instances are built. Neither counts as a use.
DECLARATION = "theme/EditorPalette.kt"
INSTANCES = "theme/EditorPalettes.kt"
#: `val name: Color,` at the data class's own indent.
FIELD = re.compile(r"^ {2}val\s+(\w+)\s*:\s*Color\s*,\s*$", re.MULTILINE)
#: The declaration this gate is about. `SyntaxPalette` follows it and is deliberately not checked.
CLASS = re.compile(r"^data class EditorPalette\($", re.MULTILINE)
#: Drawn by the window before Compose exists - see the module docstring.
EXEMPT = frozenset({"editorBackground"})


def declared_fields(source: pathlib.Path) -> list[str]:
    """Every `Color` field of `EditorPalette`, in declaration order - `SyntaxPalette`'s excluded.

    The body ends at its closing `)`, which is what keeps the two apart: `SyntaxPalette` is
    declared in the same file and its fields answer to `styles()` rather than to this rule.

    ```
    declared_fields(Path("…/theme/EditorPalette.kt"))   # ['editorBackground', …]
    ```
    """
    text = source.read_text(encoding="utf-8")
    start = CLASS.search(text)
    if not start:
        raise ValueError(f"no `data class EditorPalette(` in {source}; the gate cannot see its fields")
    body = text[start.end():]
    return FIELD.findall(body[:body.index("\n)")])


def unread_fields(root: pathlib.Path) -> list[str]:
    """Declared palette fields that no production file outside the palette's own two reads."""
    declaration = root / MAIN / DECLARATION
    fields = declared_fields(declaration)
    body = _production_text(root)
    return [
        field for field in fields
        if field not in EXEMPT and not re.search(rf"\.{field}\b", body)
    ]


def _production_text(root: pathlib.Path) -> str:
    """Every production Kotlin file except the palette's declaration and its instances."""
    skip = {DECLARATION, INSTANCES}
    return "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted((root / MAIN).rglob("*.kt"))
        if path.relative_to(root / MAIN).as_posix() not in skip
    )


def main() -> int:
    root = pathlib.Path(__file__).resolve().parent.parent
    unread = unread_fields(root)
    if not unread:
        return 0
    print("Palette fields that nothing draws with. State a colour or delete it:", file=sys.stderr)
    for field in unread:
        print(f"  EditorPalette.{field}", file=sys.stderr)
    print(f"\n{DECLARATION} is the declaration; a reader in a test does not count.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
