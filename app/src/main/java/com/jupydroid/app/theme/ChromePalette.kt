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
  /**
   * Behind the row whose document is open - `list.inactiveSelectionBackground`.
   *
   * **A part, not a Material role, and P7a-1b is what moved it here.** It used to arrive as
   * `secondaryContainer`, which meant one role served this row *and* the search toggles' on-wash
   * *and* two contextual top bars - four consumers, one of them a list. The reference states two
   * different ids there, and states this one **translucent in 2026 Light** (`#DADADA99`), so a
   * single opaque role could not carry both without flattening one of them. Splitting them let each
   * draw the id it always cited (`docs/DECISIONS.md`, "A toggle's on state is a wash").
   *
   * **Inactive rather than active on purpose.** VS Code parts a list that has focus from one that
   * does not; this app marks a row because its document is open, which is the second of those, and
   * the explorer never holds the caret while the row is marked.
   */
  val listInactiveSelectionBackground: Color,
)
