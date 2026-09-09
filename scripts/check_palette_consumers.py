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
mentioned in a KDoc would have passed for good. Comments are stripped before the search, by a
scanner rather than a regex for the reason `resolve_vscode_theme.strip_jsonc` is one: `"https://…"`
is a string, not a comment, and eating the rest of that line would hide a real consumer.

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


def without_comments(source: str) -> str:
    """Kotlin source with its comments blanked out and its string literals left alone.

    A scanner rather than a regex, for the reason `resolve_vscode_theme.strip_jsonc` is one: a
    regex that treats `//` as a comment anywhere eats the second half of `"https://x/y"`, and a
    field named after that on the same line would be reported as unread. Block comments **nest**,
    which Kotlin allows and JSON does not, so the closer is counted rather than searched for.

    ```
    without_comments('// palette.ghost\nval c = palette.cell')   # '\nval c = palette.cell'
    ```
    """
    out: list[str] = []
    at, end = 0, len(source)
    while at < end:
        rest = source[at:]
        if rest.startswith("//"):
            at = source.find("\n", at)
            at = end if at < 0 else at
        elif rest.startswith("/*"):
            at = _past_block_comment(source, at)
        elif rest.startswith('"""') or source[at] in '"\'':
            was = at
            at = _past_literal(source, at)
            out.append(source[was:at])
        else:
            out.append(source[at])
            at += 1
    return "".join(out)


def _past_block_comment(source: str, at: int) -> int:
    """The index just past the `/*` opening at `at`, counting nested openers as Kotlin does."""
    depth, at, end = 0, at, len(source)
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
            return at + len(quote)
        else:
            at += 1
    return end


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
