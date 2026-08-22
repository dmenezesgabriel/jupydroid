#!/usr/bin/env python3
"""Tests for resolve_vscode_theme.py."""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from resolve_vscode_theme import (
    AA_FLOOR,
    SYNTAX_SCOPES,
    contrast_ratio,
    resolve,
    role_foreground,
    scope_foreground,
    strip_jsonc,
)


def _theme_dir(files: dict[str, str]) -> Path:
    """A throwaway directory of theme files, so a chain can be written out and resolved."""
    directory = Path(tempfile.mkdtemp())
    for name, body in files.items():
        (directory / name).write_text(body, encoding="utf-8")
    return directory


class StripJsoncTest(unittest.TestCase):
    """What is asserted is that the result *parses to* the right thing.

    Not that it is a particular string: stripping a trailing comment leaves the space before it,
    and whitespace is not part of this function's contract - `json.loads` is.
    """

    def assertParsesTo(self, expected: object, jsonc: str) -> None:
        self.assertEqual(expected, json.loads(strip_jsonc(jsonc)))

    def test_a_line_comment_goes(self) -> None:
        self.assertParsesTo({"a": 1}, '{"a": 1} // trailing note')

    def test_a_line_comment_ending_the_file_without_a_newline_goes(self) -> None:
        self.assertParsesTo({"a": 1}, '{"a": 1}\n// note with no trailing newline')

    def test_a_block_comment_goes(self) -> None:
        self.assertParsesTo({"a": 1}, '{/* note */"a": 1}')

    def test_a_trailing_comma_goes(self) -> None:
        self.assertParsesTo({"a": 1}, '{"a": 1,}')

    def test_a_trailing_comma_in_an_array_goes(self) -> None:
        self.assertParsesTo({"a": [1, 2]}, '{"a": [1, 2,]}')

    def test_a_double_slash_inside_a_string_survives(self) -> None:
        """`"vscode://schemas/color-theme"` is the first line of every theme file."""
        self.assertParsesTo({"$schema": "vscode://schemas/color-theme"},
                            '{"$schema": "vscode://schemas/color-theme"}')

    def test_an_escaped_quote_does_not_end_the_string(self) -> None:
        """A `//` after an escaped quote is still inside the string, not a comment."""
        self.assertParsesTo({"a": 'say " then // not a comment'},
                            '{"a": "say \\" then // not a comment"}')


class ResolveTest(unittest.TestCase):
    def test_the_outermost_file_wins_for_a_colour(self) -> None:
        directory = _theme_dir({
            "base.json": '{"colors": {"editor.background": "#111111", "focusBorder": "#222222"}}',
            "top.json": '{"include": "./base.json", "colors": {"editor.background": "#121314"}}',
        })
        colors = resolve(directory / "top.json")["colors"]
        self.assertEqual("#121314", colors["editor.background"])
        self.assertEqual("#222222", colors["focusBorder"], "an unset role keeps what it inherits")

    def test_a_chain_of_three_resolves_outermost_last(self) -> None:
        directory = _theme_dir({
            "vs.json": '{"colors": {"editor.background": "#1"}}',
            "plus.json": '{"include": "./vs.json", "colors": {"editor.background": "#2"}}',
            "modern.json": '{"include": "./plus.json", "colors": {"editor.background": "#3"}}',
        })
        self.assertEqual("#3", resolve(directory / "modern.json")["colors"]["editor.background"])

    def test_token_rules_concatenate_rather_than_replace(self) -> None:
        directory = _theme_dir({
            "base.json": '{"tokenColors": [{"scope": "comment", "settings": {"foreground": "#aaa"}}]}',
            "top.json": '{"include": "./base.json", "tokenColors": '
                        '[{"scope": "string", "settings": {"foreground": "#bbb"}}]}',
        })
        self.assertEqual(2, len(resolve(directory / "top.json")["tokenColors"]))

    def test_a_file_with_no_include_resolves_to_itself(self) -> None:
        directory = _theme_dir({"only.json": '{"name": "Only", "colors": {"a": "#1"}}'})
        self.assertEqual("Only", resolve(directory / "only.json")["name"])


class ScopeForegroundTest(unittest.TestCase):
    #: An override in the outermost file has to beat the rule it inherits, which is tmTheme
    #: precedence: the last matching rule wins, not the first.
    OVERRIDDEN = {"tokenColors": [
        {"scope": "comment", "settings": {"foreground": "#6A9955"}},
        {"scope": "comment", "settings": {"foreground": "#8b949e"}},
    ]}

    def test_the_last_matching_rule_wins(self) -> None:
        self.assertEqual("#8b949e", scope_foreground(self.OVERRIDDEN, "comment"))

    def test_a_comma_separated_scope_string_is_split(self) -> None:
        theme = {"tokenColors": [{"scope": "keyword, storage.type", "settings": {"foreground": "#c9d"}}]}
        self.assertEqual("#c9d", scope_foreground(theme, "storage.type"))

    def test_a_scope_list_is_read(self) -> None:
        theme = {"tokenColors": [{"scope": ["a", "b"], "settings": {"foreground": "#c9d"}}]}
        self.assertEqual("#c9d", scope_foreground(theme, "b"))

    def test_a_rule_setting_no_foreground_does_not_claim_the_scope(self) -> None:
        """A rule may set only `fontStyle`; it must not shadow the colour below it."""
        theme = {"tokenColors": [
            {"scope": "comment", "settings": {"foreground": "#6A9955"}},
            {"scope": "comment", "settings": {"fontStyle": "italic"}},
        ]}
        self.assertEqual("#6A9955", scope_foreground(theme, "comment"))

    def test_an_unknown_scope_is_none(self) -> None:
        self.assertIsNone(scope_foreground(self.OVERRIDDEN, "entity.name.function"))


class SyntaxScopeTest(unittest.TestCase):
    """The scope map is transcribed into `theme/EditorPalettes.kt`, so what it resolves is pinned.

    Read off the resolved 2026 themes at `f3fa55c39d3`. A rule that stopped claiming its scope
    would otherwise fall through to the next candidate - or to None - and the palette would drift
    off the reference the next time someone re-ran this tool to check a value.
    """

    def setUp(self) -> None:
        themes = (Path(__file__).parent.parent
                  / "reference/research_repos/vscode/extensions/theme-defaults/themes")
        if not themes.is_dir():
            self.skipTest("reference/ is gitignored; the clone is not present")
        self.dark = resolve(themes / "2026-dark.json")
        self.light = resolve(themes / "2026-light.json")

    def test_every_role_resolves_in_both_themes(self) -> None:
        for role in ("plain", *SYNTAX_SCOPES):
            for name, theme in (("dark", self.dark), ("light", self.light)):
                with self.subTest(role=role, theme=name):
                    self.assertIsNotNone(role_foreground(theme, role))

    def test_the_two_unclaimed_scopes_fall_through_to_the_next_candidate(self) -> None:
        """`heading` and `link` are the only roles whose first-choice scope no rule claims.

        Their fall-through is load-bearing: it is why `link` ends up on `string.other.link` and
        shares `string`'s value, which `EditorPaletteTest` asserts from the other side.
        """
        for theme in (self.dark, self.light):
            self.assertIsNone(scope_foreground(theme, "entity.name.section"))
            self.assertIsNone(scope_foreground(theme, "markup.underline.link"))
            self.assertEqual(scope_foreground(theme, "markup.heading"),
                             role_foreground(theme, "heading"))
            self.assertEqual(scope_foreground(theme, "string.other.link"),
                             role_foreground(theme, "link"))

    def test_a_link_takes_the_string_colour(self) -> None:
        """The reference's own doing, and the reason the app underlines a link instead."""
        for theme in (self.dark, self.light):
            self.assertEqual(role_foreground(theme, "string"), role_foreground(theme, "link"))

    def test_light_number_is_the_one_role_under_the_floor_on_the_cell_surface(self) -> None:
        """The measurement that `theme/EditorPalettes.kt` records rather than corrects.

        `notebook.cellEditorBackground` resolves to `sideBar.background`, not to the page, and on
        that surface 2026 Light's `constant.numeric` is under WCAG AA. It is kept, because parity
        with the reference outranks the floor - `docs/DECISIONS.md`, "Parity with the 2026 themes
        outranks the contrast floor". Pinned here so the *scope* of the shortfall stays one role:
        if a second role ever drops under, this fails and the decision gets re-taken deliberately.
        """
        cell = self.light["colors"]["sideBar.background"]
        under = [role for role in ("plain", *SYNTAX_SCOPES)
                 if contrast_ratio(role_foreground(self.light, role), cell) < AA_FLOOR
                 and role != "comment"]
        self.assertEqual(["number"], under)
        self.assertAlmostEqual(4.42, contrast_ratio("#098658", cell), places=2)

    def test_no_dark_role_is_under_the_floor_on_the_cell_surface(self) -> None:
        """Dark's shortfall count is zero, so the entry above is about light and nothing else."""
        cell = self.dark["colors"]["sideBar.background"]
        under = [role for role in ("plain", *SYNTAX_SCOPES)
                 if contrast_ratio(role_foreground(self.dark, role), cell) < AA_FLOOR]
        self.assertEqual([], under)


class ContrastRatioTest(unittest.TestCase):
    def test_black_on_white_is_the_maximum(self) -> None:
        self.assertAlmostEqual(21.0, contrast_ratio("#000000", "#FFFFFF"), places=2)

    def test_a_colour_against_itself_is_one(self) -> None:
        self.assertAlmostEqual(1.0, contrast_ratio("#8b949e", "#8b949e"), places=6)

    def test_the_order_of_the_pair_does_not_matter(self) -> None:
        self.assertAlmostEqual(
            contrast_ratio("#6e7781", "#FFFFFF"), contrast_ratio("#FFFFFF", "#6e7781"), places=6)

    def test_the_tightest_measured_pair_is_reproduced(self) -> None:
        """2026 Light's `comment` on `editor.background` - the value `docs/DECISIONS.md` cites."""
        self.assertAlmostEqual(4.55, contrast_ratio("#6e7781", "#FFFFFF"), places=2)

    def test_an_eight_digit_hex_ignores_its_alpha(self) -> None:
        """`sideBar.border` is `#2A2B2CFF`; the ramp reads six digits."""
        self.assertAlmostEqual(
            contrast_ratio("#2A2B2C", "#FFFFFF"), contrast_ratio("#2A2B2CFF", "#FFFFFF"), places=6)


if __name__ == "__main__":
    unittest.main()
