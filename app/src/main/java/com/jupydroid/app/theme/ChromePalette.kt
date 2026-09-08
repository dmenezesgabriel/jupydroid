package com.jupydroid.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The workbench's **parts** - the surfaces Material has no role for.
 *
 * A second palette rather than more fields on [EditorPalette], the shape P4g settled for the ANSI
 * ramp: that one is the colours a *document* is drawn with, and a rail is not a document.
 *
 * **Why these are stated rather than taken from `colorScheme`.** VS Code separates a part's
 * identity from a surface's elevation - `activityBar.background`, `sideBar.background`,
 * `panel.background` and `editor.background` are four ids, and Material has one `surface` with
 * levels under it. Two of those ids happen to hold the same hex as `surfaceContainer` today, and
 * that is the trap: `ColorSchemeTest` asserts a role is *somewhere* on [ColorRamp] and never which
 * position, so a role could drift onto `menu.background` with every test still green. The rule is
 * `docs/DECISIONS.md`, "The palette states its colours; it does not derive them from a Material
 * role" - *a colour a role could supply is a colour no test can hold*.
 *
 * `@Immutable` so a composable holding one is skippable, matching [EditorPalette].
 *
 * ```
 * NavigationRail(containerColor = LocalChromePalette.current.activityBarBackground)
 * ```
 */
@Immutable
data class ChromePalette(
  /**
   * Behind the activity bar - `activityBar.background`, whether it is drawn as the rail beside two
   * panes or as the bottom bar below one. One field for both, because the reference gives its
   * three `ActivityBarPosition`s one id.
   */
  val activityBarBackground: Color,
  /**
   * Behind whichever side panel is showing - `sideBar.background`.
   *
   * Equal to [activityBarBackground] in both default themes and **kept separate anyway**: they are
   * two ids in the reference, a theme may part them, and folding them here would make the app
   * unable to follow one that did.
   */
  val sideBarBackground: Color,
)
