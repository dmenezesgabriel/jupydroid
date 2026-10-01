#!/usr/bin/env python3
"""Working Agreement (AGENTS.md): a palette states colours something draws, and nothing else.

`ColorRamp` has carried the rule in prose since P2u-1 - *"A position with no consumer is not here.
The ramp holds what `Theme.kt` maps a role onto and nothing else; the resolved themes have many
more greys than these, and carrying the unused ones would be a list nobody could tell was stale."*
`EditorPalette` had no such gate, and P2u-3 found exactly what the prose predicts: `caretRow` and
`outputForeground` were declared, given a value in **both** palettes, cited to an IntelliJ
attribute key, and read by nothing at all.

That is worse than an unused variable. Each one is two hex values a reader takes for a statement
about how the app draws, and a re-sourcing step has to look up, measure and transcribe a colour
for a surface that does not exist. This gate is what stops the next one being added.

**One gate, two palettes since P7a-1.** `ChromePalette` states the activity bar's and the side
panels' own surfaces, and a palette this file did not know about would be a palette with no gate -
`ThisRepositoryTest.test_every_palette_this_app_has_is_held` is what stops the next one being
added silently.

**A consumer is production code**, `app/src/main/…`, outside the palette's own two files. A field
read only by a test is not drawn by anything, which is the case this exists to catch - the test
would keep passing and the field would keep lying.

**And neither is a field read only by a comment**, which is the same lie one step out and was a
live false green here: `terminal/TerminalPalette.kt` cross-references
`LightEditorPalette.editorBackground` in a KDoc, and `.editorBackground` matched that text. Every
palette in this repository documents its fields by naming its neighbours', so any new field
mentioned in a KDoc would have passed for good. Comments are stripped before the search by
`kotlin_source.without_comments`, which is where that scanner lives now that `check_ui_dimensions.py`
has hit the same defect from the other side - it refused `theme/CornerRadius.kt` over a sentence
*about* a retired literal (P7a-3). Why a scanner and not a regex is written there.

One thing is deliberately outside the rule, and it is here rather than in a comment nobody reads.
**`SyntaxPalette` is not checked**: its fields are consumed by `styles()`, which lives in the
declaration file by design, so "outside the palette's own files" is the wrong question to ask of
them - `EditorPaletteTest.everySyntaxKindButPlainIsStyled` is what holds that set complete.

**`editorBackground` used to be exempt too** - drawn before there is a composition, by a window
that takes it from `res/values{,-night}/colors.xml` - and P7a-2 retired the exemption by giving it
Kotlin consumers: the three document `Scaffold`s state the page rather than inheriting it from
`colorScheme.background`. The `colors.xml` duplication is still real and is still `AppWindowTest`'s
to keep honest; it is no longer the field's *only* reader.

Tests in check_palette_consumers_test.py, including one that runs the gate over this repository,
so a gate that has stopped matching anything fails rather than passing quietly.
"""
from __future__ import annotations

import dataclasses
import pathlib
import re
import sys

from kotlin_source import without_comments

MAIN = pathlib.Path("app/src/main/java/com/jupydroid/app")
#: `val name: Color,` at the data class's own indent.
FIELD = re.compile(r"^ {2}val\s+(\w+)\s*:\s*Color\s*,\s*$", re.MULTILINE)


@dataclasses.dataclass(frozen=True)
class Palette:
    """One palette this gate holds, and the two files that do not count as reading it.

    ```
    Palette("ChromePalette", "theme/ChromePalette.kt", "theme/ChromePalettes.kt")
    ```
    """

    #: The `data class` to read fields from. Anything declared after it in the same file is not read.
    name: str
    #: Where that class is declared, relative to `MAIN`.
    declaration: str
    #: Where its instances are built. Assigning a field there is not drawing with it.
    instances: str


#: The document's colours - the page, the cell, the syntax.
EDITOR = Palette("EditorPalette", "theme/EditorPalette.kt", "theme/EditorPalettes.kt")
#: The workbench's parts - the activity bar and the side panels (P7a-1).
CHROME = Palette("ChromePalette", "theme/ChromePalette.kt", "theme/ChromePalettes.kt")

PALETTES = (EDITOR, CHROME)


def declared_fields(source: pathlib.Path, name: str) -> list[str]:
    """Every `Color` field of the `name` data class in `source`, in declaration order.

    The body ends at its closing `)`, which is what keeps a companion declaration out: `SyntaxPalette`
    sits in `EditorPalette.kt` and its fields answer to `styles()` rather than to this rule.

    ```
    declared_fields(Path("…/theme/EditorPalette.kt"), "EditorPalette")   # ['editorBackground', …]
    ```
    """
    text = source.read_text(encoding="utf-8")
    start = re.search(rf"^data class {re.escape(name)}\($", text, re.MULTILINE)
    if not start:
        raise ValueError(f"no `data class {name}(` in {source}; the gate cannot see its fields")
    body = text[start.end():]
    return FIELD.findall(body[:body.index("\n)")])


def unread_fields(root: pathlib.Path) -> list[str]:
    """Palette fields no production file outside that palette's own two reads, `Palette.field` named.

    Qualified because there is more than one palette now, and `bar` alone would not say which.
    """
    body = _production_text(root)
    return [
        f"{palette.name}.{field}"
        for palette in PALETTES
        for field in declared_fields(root / MAIN / palette.declaration, palette.name)
        if not re.search(rf"\.{field}\b", body)
    ]


def _production_text(root: pathlib.Path) -> str:
    """Every production Kotlin file except every palette's own declaration and instances, code only."""
    skip = {file for palette in PALETTES for file in (palette.declaration, palette.instances)}
    return "\n".join(
        without_comments(path.read_text(encoding="utf-8"))
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
        print(f"  {field}", file=sys.stderr)
    print("\nA reader in a test does not count, and neither does the palette's own instances file.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
