package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * The two [ChromePalette]s, resolved from VS Code's default themes.
 *
 * Read out of `vscode` at `3f3380c668d`, and **resolved** rather than read
 * from one file - `2026-dark.json` is the outermost of a four-deep `include` chain
 * (ADR-0041):
 *
 * ```sh
 * cd <your vscode clone>/extensions/theme-defaults/themes
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
  // `list.inactiveSelectionBackground`, opaque in this theme - only light states an alpha.
  listInactiveSelectionBackground = Color(0xFF2C2D2E),
  // `activityBar.border` and `sideBar.border`, both stated by 2026 Dark at `#2A2B2CFF`
  // (`2026-dark.json:76,84`). Their *registered* defaults are `dark: null, light: null` - only a
  // high-contrast theme gets one from the registry - so the theme file is what makes them real.
  activityBarBorder = Color(0xFF2A2B2C),
  sideBarBorder = Color(0xFF2A2B2C),
  // The tab strip's six ids - `editorGroupHeader.tabsBackground`, `tab.inactiveBackground`,
  // `tab.activeBackground`, `tab.activeBorderTop`, `editorGroupHeader.tabsBorder`, `tab.border` -
  // resolved the same way this file's other ids are.
  tabStripBackground = Color(0xFF191A1B),
  tabStripBorder = Color(0xFF2A2B2C),
  tabActiveBackground = Color(0xFF121314),
  tabInactiveBackground = Color(0xFF191A1B),
  tabActiveBorderTop = Color(0xFF3994BC),
  tabBorder = Color(0xFF2A2B2C),
)

/** 2026 Light's parts. Both ids are `#FAFAFD` here, a step *down* from a white page. */
internal val LightChromePalette = ChromePalette(
  activityBarBackground = Color(0xFFFAFAFD),
  sideBarBackground = Color(0xFFFAFAFD),
  // `list.inactiveSelectionBackground`, which 2026 Light states **translucent**: `#DADADA99`.
  // VS Code writes `#RRGGBBAA` and Compose writes `0xAARRGGBB`, so the alpha swaps ends - the same
  // trap `EditorPalettes.kt`'s own `cellMultiSelected` records. Over the side bar it paints
  // `#E7E7E8`, which `ChromePaletteTest.theLightRowIsAWashAndNotAFill` pins.
  listInactiveSelectionBackground = Color(0x99DADADA),
  // Both border ids again, `#F0F1F2FF` here (`2026-light.json:85,93`).
  activityBarBorder = Color(0xFFF0F1F2),
  sideBarBorder = Color(0xFFF0F1F2),
  // The same six ids as the dark half. `tab.activeBorderTop` is the one the light theme states
  // **black** - its own answer for marking the active tab on a white page.
  tabStripBackground = Color(0xFFFAFAFD),
  tabStripBorder = Color(0xFFF0F1F2),
  tabActiveBackground = Color(0xFFFFFFFF),
  tabInactiveBackground = Color(0xFFFAFAFD),
  tabActiveBorderTop = Color(0xFF000000),
  tabBorder = Color(0xFFF0F1F2),
)
