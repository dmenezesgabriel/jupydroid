package com.jupydroid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's raw chrome ramps - every hex value the Material scheme draws with, dark and light.
 *
 * The values are transcribed from VS Code's **resolved** default themes, `2026-dark.json` and
 * `2026-light.json`, read out of `vscode` at `3f3380c668d`. Each position
 * below names the colour id it came from, so a value can be checked against its origin instead of
 * argued about:
 *
 * ```sh
 * cd <your vscode clone>/extensions/theme-defaults/themes
 * python3 ../../../../../../scripts/resolve_vscode_theme.py 2026-dark.json --color editor.background
 * ```
 *
 * **Resolved, and that word is the whole trap.** `2026-dark.json` is the outermost file of a
 * four-deep `include` chain and overrides most of what it inherits; reading any single file gives
 * the wrong colour, and not by a little - `editor.background` is `#121314` resolved and `#1F1F1F`
 * in Dark Modern alone. The default is 2026 Dark / 2026 Light, not Dark Modern (ADR-0041).
 *
 * Named by ramp position rather than by job, because a position is a fact and a job is an
 * opinion: `Gray2` is one specific grey, where "surfaceVariant" is a role several greys could
 * fill. Both ramps run **ascending luminance**, so `Gray1` is the darkest step of each - which
 * means `DarkGray1` is a page and `LightGray1` is body text, and they must not be swapped.
 *
 * **A position with no consumer is not here.** The ramp holds what `Theme.kt` maps a role onto and
 * nothing else; the resolved themes have many more greys than these, and carrying the unused ones
 * would be a list nobody could tell was stale.
 *
 * This file is not the app's only statement of a colour: [EditorPalette]'s two instances state
 * their own hex, from the same resolved VS Code themes. What is true is
 * narrower and still worth having - **no Material role states a colour**, so a role that has
 * drifted off the ramp fails `ColorSchemeTest` rather than waiting to be noticed in a screenshot.
 */
internal object ColorRamp {
  // Dark - 2026 Dark, ascending luminance.
  /** `editor.background` - the page a document is drawn on. */
  val DarkGray1 = Color(0xFF121314)

  /** `sideBar.background` - the explorer and the rail, one step up from the page. */
  val DarkGray2 = Color(0xFF191A1B)

  /** `editorWidget.background`, which is also `menu.background`. */
  val DarkGray3 = Color(0xFF202122)

  /** `checkbox.background`, which is also `editor.lineHighlightBackground`. */
  val DarkGray4 = Color(0xFF242526)

  /** `panel.border` - a separator between two panels, and faint by design. */
  val DarkGray5 = Color(0xFF2A2B2C)

  /**
   * `inputOption.activeBackground` - the wash a search toggle takes when it is on.
   *
   * **It used to be `list.inactiveSelectionBackground`, and the two were parted.** One role served
   * a list row and three things that are not lists; the reference states two ids there and states
   * the list's one translucent in light, so an opaque position could not carry both. The row went
   * to [ChromePalette.listInactiveSelectionBackground] and this position kept the consumers it had
   * always described (ADR-0042).
   */
  val DarkGray6 = Color(0xFF313233)

  /** `checkbox.border` - a *component's* bounds, which is a different job from `panel.border`. */
  val DarkGray7 = Color(0xFF707070)

  /** `descriptionForeground` - secondary text, and `icon.foreground`. */
  val DarkGray8 = Color(0xFF8C8C8C)

  /** `editor.foreground` - body text. */
  val DarkGray9 = Color(0xFFBBBEBF)

  /** `button.foreground`. */
  val DarkGray10 = Color(0xFFFFFFFF)

  /** `button.background` - the accent as a *fill*, under [DarkGray10]. */
  val DarkBlue1 = Color(0xFF297AA0)

  /** `textLink.foreground` - the accent as *text on a surface*, which is a different value. */
  val DarkBlue2 = Color(0xFF48A0C7)

  /** `charts.green`. */
  val DarkGreen1 = Color(0xFF86CF86)

  /** `inputValidation.errorBackground`. */
  val DarkRed1 = Color(0xFF3A1D1D)

  /** `errorForeground`, which is also `list.errorForeground`. */
  val DarkRed2 = Color(0xFFF48771)

  // Light - 2026 Light, ascending luminance.
  /** `editor.foreground` - body text. */
  val LightGray1 = Color(0xFF202020)

  /** `descriptionForeground` - secondary text, and `icon.foreground`. */
  val LightGray2 = Color(0xFF606060)

  /** `checkbox.border` - a component's bounds. */
  val LightGray3 = Color(0xFF868686)

  /** `inputOption.activeBackground` - the toggle wash, light's half of [DarkGray6]'s re-source. */
  val LightGray4 = Color(0xFFD6D6D6)

  /** `button.secondaryBackground`, which is also `checkbox.background`. */
  val LightGray5 = Color(0xFFEAEAEA)

  /** `panel.border` - a separator, faint by design. */
  val LightGray6 = Color(0xFFF0F1F2)

  /** `sideBar.background` - the explorer and the rail, one step *down* from the page here. */
  val LightGray7 = Color(0xFFFAFAFD)

  /** `editor.background` - the page. */
  val LightGray8 = Color(0xFFFFFFFF)

  /**
   * `button.background`, which in this theme is also `textLink.foreground` - light does not split
   * its fill accent from its text accent the way dark does.
   */
  val LightBlue1 = Color(0xFF0069CC)

  /** `inputValidation.infoBackground` - the pale blue that gives [LightBlue1] a container. */
  val LightBlue2 = Color(0xFFE6F2FA)

  /**
   * `gitDecoration.addedResourceForeground`, and **not** `charts.green`.
   *
   * Light's `charts.green` `#388A34` measures 4.33:1 on `editor.background`, under AA; this one
   * measures 4.88:1. The two are the same "added/success" idea in the same theme, so taking the
   * one that clears the floor is a choice between the reference's own values rather than a
   * departure from it.
   */
  val LightGreen1 = Color(0xFF587C0C)

  /** `inputValidation.errorBackground`. */
  val LightRed1 = Color(0xFFFDEDED)

  /** `errorForeground`, which is also `inputValidation.errorBorder`. */
  val LightRed2 = Color(0xFFAD0707)

  /**
   * The base both themes composite their overlays over - `button.secondaryBackground` is
   * `#00000000` in dark and `toolbar.hoverBackground` is `#0000001F` in light. Shared rather than
   * written twice, because it is one colour and neither ramp owns it.
   */
  val Black = Color(0xFF000000)

  /**
   * Every colour above, for the assertion that no Material role has drifted off the ramp.
   *
   * A set rather than a list: what matters is membership, and two positions holding the same hex
   * would be a fact about the theme rather than a mistake to be caught here.
   */
  val all: Set<Color> = setOf(
    DarkGray1, DarkGray2, DarkGray3, DarkGray4, DarkGray5, DarkGray6, DarkGray7, DarkGray8,
    DarkGray9, DarkGray10,
    DarkBlue1, DarkBlue2, DarkGreen1, DarkRed1, DarkRed2,
    LightGray1, LightGray2, LightGray3, LightGray4, LightGray5, LightGray6, LightGray7, LightGray8,
    LightBlue1, LightBlue2, LightGreen1, LightRed1, LightRed2,
    Black,
  )
}
