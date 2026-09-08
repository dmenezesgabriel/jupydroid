package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * The two [ChromePalette]s, resolved from VS Code's default themes.
 *
 * Read out of `reference/research_repos/vscode` at `3f3380c668d`, and **resolved** rather than read
 * from one file - `2026-dark.json` is the outermost of a four-deep `include` chain
 * (`docs/DECISIONS.md`, "The default VS Code theme is 2026 Dark / 2026 Light, not Dark Modern"):
 *
 * ```sh
 * cd reference/research_repos/vscode/extensions/theme-defaults/themes
 * python3 ../../../../../../scripts/resolve_vscode_theme.py 2026-dark.json --color sideBar.background
 * ```
 *
 * Their own hex rather than a [ColorRamp] position, for the reason [ChromePalette]'s KDoc gives:
 * the ramp holds what a *Material role* maps onto, and a part is not a role. That the values
 * coincide with `DarkGray2` and `LightGray7` today is a fact about the theme, not a dependency.
 */
internal val DarkChromePalette = ChromePalette(
  activityBarBackground = Color(0xFF191A1B),
  sideBarBackground = Color(0xFF191A1B),
)

/** 2026 Light's parts. Both ids are `#FAFAFD` here, a step *down* from a white page. */
internal val LightChromePalette = ChromePalette(
  activityBarBackground = Color(0xFFFAFAFD),
  sideBarBackground = Color(0xFFFAFAFD),
)
