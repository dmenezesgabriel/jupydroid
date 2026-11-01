#!/usr/bin/env python3
"""Resolve a VS Code colour theme's `include` chain, and measure a colour pair against WCAG AA.

**Why this exists rather than reading the theme file.** VS Code's default themes are the outermost
file of a four-deep chain: `2026-dark.json` includes `dark_modern.json` includes `dark_plus.json`
includes `dark_vs.json`, and the outermost overrides most of what it inherits - 725 lines against
Dark Modern's 133. Reading any single file gives the wrong colour, and the difference is not
cosmetic: `editor.background` is `#121314` resolved and `#1F1F1F` in Dark Modern alone.
ADR-0041: the default VS Code theme is 2026 Dark / 2026 Light, not Dark Modern.

A measurement, not a gate: nothing in the build fails on its output. It exists so a value
transcribed into `theme/ColorRamp.kt` can be checked against its origin instead of argued about,
and so ADR-0041's contrast measurements can be reproduced.

```sh
cd reference/research_repos/vscode/extensions/theme-defaults/themes
python3 .../resolve_vscode_theme.py 2026-dark.json --role comment
```
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

#: The app's `SyntaxPalette` roles, and the VS Code scopes each is read from, best match first.
#: Kept here rather than in the caller so the mapping has one home - it is the thing P2u-2
#: transcribes, and a role that silently resolved to a different scope would be invisible.
SYNTAX_SCOPES: dict[str, list[str]] = {
    "keyword": ["keyword.control", "keyword"],
    "string": ["string"],
    "number": ["constant.numeric"],
    "comment": ["comment"],
    "function": ["entity.name.function"],
    "heading": ["markup.heading", "entity.name.section"],
    "link": ["markup.underline.link", "string.other.link"],
    "codeSpan": ["markup.inline.raw"],
    "blockQuote": ["markup.quote"],
    "listMarker": ["punctuation.definition.list.begin.markdown"],
    "variable": ["variable"],
}

#: WCAG AA for body text, which is the floor `EditorPaletteTest` holds every syntax colour to.
AA_FLOOR = 4.5


def strip_jsonc(text: str) -> str:
    """JSON with `//` comments and trailing commas - which is what a theme file is - as plain JSON.

    A scanner rather than a regex because `"vscode://schemas/color-theme"` is the first line of
    every theme file, and a regex that treats `//` as a comment anywhere eats half of it.

    ```
    strip_jsonc('{"a": 1,} // note')   # '{"a": 1}'
    ```
    """
    out: list[str] = []
    i, n = 0, len(text)
    while i < n:
        char = text[i]
        if char == '"':
            i = _copy_string(text, i, out)
            continue
        if char == "/" and text.startswith("//", i):
            newline = text.find("\n", i)
            if newline < 0:
                break
            i = newline
            continue
        if char == "/" and text.startswith("/*", i):
            i = text.find("*/", i) + 2
            continue
        out.append(char)
        i += 1
    return re.sub(r",(\s*[}\]])", r"\1", "".join(out))


def _copy_string(text: str, start: int, out: list[str]) -> int:
    """Copies the string literal opening at [start] verbatim; returns the index just past it."""
    j = start + 1
    while j < len(text):
        if text[j] == "\\":
            j += 2
            continue
        if text[j] == '"':
            break
        j += 1
    out.append(text[start : j + 1])
    return j + 1


def resolve(path: Path) -> dict[str, Any]:
    """The theme at [path] with its whole `include` chain folded in, outermost last.

    `colors` merge by key and `tokenColors` concatenate, so the outermost file's colour wins and
    its token rule sorts after the one it overrides - see [scope_foreground] for why that order is
    what matters.

    ```
    resolve(Path("2026-dark.json"))["colors"]["editor.background"]   # '#121314'
    ```
    """
    theme = json.loads(strip_jsonc(Path(path).read_text(encoding="utf-8")))
    colors: dict[str, str] = {}
    tokens: list[dict[str, Any]] = []
    if "include" in theme:
        base = resolve(Path(path).parent / theme["include"])
        colors, tokens = base["colors"], base["tokenColors"]
    return {
        "name": theme.get("name"),
        "type": theme.get("type"),
        "colors": {**colors, **theme.get("colors", {})},
        "tokenColors": tokens + theme.get("tokenColors", []),
    }


def scope_foreground(theme: dict[str, Any], scope: str) -> str | None:
    """The foreground [scope] resolves to, or None where no rule claims it.

    **The last matching rule wins**, which is tmTheme precedence and the reason this is not a
    `next(...)`: an override in the outermost file is appended after the rule it overrides, so
    taking the first match would return the value that was overridden.

    A rule setting only `fontStyle` does not claim the scope - it must not shadow the colour below
    it.

    **This matches an *exact* scope, where VS Code descends a trie**, and that difference is
    checked rather than assumed. VS Code sorts rules lexicographically with declaration index as
    the tiebreak, inserts each into a trie by dot segment, and returns the deepest node it can
    reach (`src/vs/editor/common/languages/supports/tokenization.ts:106-151`, `:361-382`) - so
    specificity wins and last-rule-wins applies only between equal scopes, which is the half
    modelled here. The two agree wherever a rule names the scope outright, which is every entry of
    [SYNTAX_SCOPES] but `entity.name.section` and `markup.underline.link`; both are unclaimed and
    fall through, which is what [role_foreground] wants of them. A trie for eleven roles would be
    machinery for a case this tool does not have.

    ```
    scope_foreground(resolve(Path("2026-dark.json")), "comment")   # '#8b949e'
    ```
    """
    found: str | None = None
    for rule in theme["tokenColors"]:
        foreground = rule.get("settings", {}).get("foreground")
        if foreground and scope in _scopes_of(rule):
            found = foreground
    return found


def _scopes_of(rule: dict[str, Any]) -> list[str]:
    """A rule's scopes, whether it spelled them as a list, one string, or one comma-separated one."""
    declared = rule.get("scope", [])
    listed = [declared] if isinstance(declared, str) else declared
    return [scope.strip() for entry in listed for scope in entry.split(",")]


def _relative_luminance(hex_color: str) -> float:
    digits = hex_color.lstrip("#")[:6]
    if len(digits) != 6:
        raise ValueError(f"expected a 6- or 8-digit hex colour like '#8b949e', got {hex_color!r}")
    channels = [int(digits[i : i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast_ratio(one: str, other: str) -> float:
    """WCAG 2.1 contrast between two hex colours, alpha ignored.

    Alpha is dropped rather than composited because the ramp this feeds is opaque: an eight-digit
    id like `sideBar.border`'s `#2A2B2CFF` contributes its six digits.

    ```
    contrast_ratio("#6e7781", "#FFFFFF")   # 4.55
    ```
    """
    lighter, darker = sorted([_relative_luminance(one), _relative_luminance(other)], reverse=True)
    return (lighter + 0.05) / (darker + 0.05)


def role_foreground(theme: dict[str, Any], role: str) -> str | None:
    """One `SyntaxPalette` role's colour: `plain` is the editor's own foreground, the rest scopes."""
    if role == "plain":
        foreground: str | None = theme["colors"].get("editor.foreground")
        return foreground
    scopes = SYNTAX_SCOPES[role]
    return next((color for scope in scopes if (color := scope_foreground(theme, scope))), None)


def _report(theme: dict[str, Any], against: str) -> None:
    """Every syntax role of [theme] measured against the colour id [against]."""
    background = theme["colors"][against]
    print(f"{theme['name']}  {against}={background}  floor {AA_FLOOR}:1")
    for role in ("plain", *SYNTAX_SCOPES):
        foreground = role_foreground(theme, role)
        if not foreground:
            print(f"  {role:9s} no rule claims it")
            continue
        ratio = contrast_ratio(foreground, background)
        verdict = "ok" if ratio >= AA_FLOOR else "BELOW"
        print(f"  {role:9s} {foreground:9s} {ratio:6.2f}:1  {verdict}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Resolve a VS Code theme and measure its contrast.")
    parser.add_argument("theme", type=Path, help="the outermost theme file, e.g. 2026-dark.json")
    parser.add_argument("--role", help="print one syntax role's colour and nothing else")
    parser.add_argument("--color", help="print one workbench colour id and nothing else")
    parser.add_argument("--against", default="editor.background", help="the colour id every role is measured against")
    args = parser.parse_args()
    theme = resolve(args.theme)
    if args.color:
        print(theme["colors"].get(args.color, "unset"))
    elif args.role:
        print(role_foreground(theme, args.role) or "unset")
    else:
        _report(theme, args.against)


if __name__ == "__main__":
    main()
