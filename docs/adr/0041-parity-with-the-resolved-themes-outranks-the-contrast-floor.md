# ADR-0041 — Parity with the resolved VS Code themes outranks the contrast floor

Status: accepted
Date: 2026-08-22

## Context

**The default VS Code theme is not Dark Modern, and that is the trap this work nearly walked into.**
`ThemeSettingDefaults.COLOR_THEME_DARK` is `'Dark 2026'`, and `migrateThemeSettingsId` maps the former
id `'VS Code Dark'` onto it — so "the VS Code dark theme" **is** 2026 Dark.

It is not a standalone file. `2026-dark.json` includes `dark_modern.json`, which includes
`dark_plus.json`, which includes `dark_vs.json`, and the outermost overrides most of what it inherits:
725 lines against Dark Modern's 133. **Reading any one file in that chain gives the wrong colour.** The
chain must be resolved first — `include` walked outermost last, `colors` merged by key,
`tokenColors` concatenated so the last matching rule wins, which is tmTheme precedence. Measured after
resolving, `editor.background` is `#121314`, not Dark Modern's `#1F1F1F`.

`scripts/resolve_vscode_theme.py` walks the chain, so every value here is checkable rather than asserted.

**And the palette had been measured against the wrong surface.** An earlier contrast table was measured
against the *page* rather than the *cell*, which is where the code actually sits. Re-measured on the
cell, light's `constant.numeric` (`#098658`) is **4.42:1** — under WCAG AA's 4.5:1.

## Decision drivers

- A transcribed value's whole worth is that it is the reference's value.
- A shortfall that is not bounded and asserted is a shortfall that spreads.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep the contrast floor as the gate, and correct the value | Meets AA everywhere | The app's Python numbers become a colour that appears in **no** VS Code theme — a worse answer to "which reference did this come from" than a recorded 4.42:1 |
| `hc_light.json`'s `#096d48` | The same green darkened by VS Code's own high-contrast theme; hue moves 0.6° in OKLCH, measuring 6.00:1 | A value from a theme this app is not transcribing: it buys AA at the cost of the parity that is the point |
| 2026 Light's own `constant` rule, `#0550ae` (7.29:1) | In the theme being transcribed | Rejected twice over: light numbers would read **blue** where VS Code shows green, and dark would have to follow onto `#79c0ff`, which is dark's `heading` |
| Pin the syntax values **by equality** and bound the shortfall | Parity is exact, and the shortfall is asserted rather than tolerated | One role in one theme is knowingly under AA |

## Decision

**The eight syntax values are pinned by equality to the resolved themes**, not by contrast ratio. Light's
`constant.numeric` stays `#098658` at 4.42:1 on the cell.

Equality is the stronger assertion for the other seven: a ratio permits any other legible colour;
equality permits nothing, which is what *"a value may not be nudged to match VS Code more closely without
re-measuring"* actually asks for.

**The shortfall is bounded to one role in one theme, and that stays true by test.**
`resolve_vscode_theme_test.py` asserts light's under-floor list is exactly `["number"]` and dark's is
empty, so a second role slipping under fails a test rather than passing unnoticed, and the decision gets
re-taken on purpose.

**What is still held to AA, because parity does not reach it: every colour this app chose for itself
rather than transcribed.** The two search-match foregrounds are stated precisely so a match can be read,
and the active editor border is held to WCAG 1.4.11's 3:1. Those are ours; nothing about the reference
excuses them.

## Consequences

A measurement here is only meaningful **against the surface the text sits on**. Measuring against the
page is how the earlier table was wrong, and `--against sideBar.background` is how the script makes the
surface explicit.

`resolve_vscode_theme.py` is the reason this is checkable. A transcription done by reading
`dark_modern.json` directly will be wrong and will look right.

The thing a future reader will be tempted to "fix" is the 4.42:1, which an accessibility audit will flag.
The three alternatives above were each measured and each costs the parity that is the point; the decision
is to record the shortfall, bound it by test, and hold everything we chose ourselves to AA.
