package com.jupydroid.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The workbench's **parts** - the surfaces Material has no role for.
 *
 * A second palette rather than more fields on [EditorPalette], the shape ADR-0020 settled for the
 * ANSI ramp: that one is the colours a *document* is drawn with, and a rail is not a document.
 *
 * **Why these are stated rather than taken from `colorScheme`.** VS Code separates a part's
 * identity from a surface's elevation - `activityBar.background`, `sideBar.background`,
 * `panel.background` and `editor.background` are four ids, and Material has one `surface` with
 * levels under it. Two of those ids happen to hold the same hex as `surfaceContainer` today, and
 * that is the trap: `ColorSchemeTest` asserts a role is *somewhere* on [ColorRamp] and never which
 * position, so a role could drift onto `menu.background` with every test still green. The rule is
 * ADR-0042: the palette states its colours and does not derive them from a Material role -
 * *a colour a role could supply is a colour no test can hold*.
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
   * The text on [sideBarBackground] - `sideBar.foreground`, which 2026 Dark states `#bfbfbf` and
   * 2026 Light `#202020`.
   *
   * **Stated because a `Scaffold` will otherwise choose it by accident.** Handed
   * `containerColor = sideBarBackground`, it derives its content colour with `contentColorFor`,
   * which matches the container to a Material role *by hex*. `#191A1B` is also `surfaceVariant` in
   * dark and `#FAFAFD` in light, so every colourless `Text` on a side panel arrived as
   * `onSurfaceVariant` - the secondary colour - for a reason no test could hold. A row names the
   * colour it is drawn in from here instead (ADR-0042).
   */
  val sideBarForeground: Color,
  /**
   * Behind the row whose document is open - `list.inactiveSelectionBackground`.
   *
   * **A part, not a Material role.** It used to arrive as
   * `secondaryContainer`, which meant one role served this row *and* the search toggles' on-wash
   * *and* two contextual top bars - four consumers, one of them a list. The reference states two
   * different ids there, and states this one **translucent in 2026 Light** (`#DADADA99`), so a
   * single opaque role could not carry both without flattening one of them. Splitting them let each
   * draw the id it always cited (ADR-0042).
   *
   * **Inactive rather than active on purpose.** VS Code parts a list that has focus from one that
   * does not; this app marks a row because its document is open, which is the second of those, and
   * the explorer never holds the caret while the row is marked.
   */
  val listInactiveSelectionBackground: Color,
  /**
   * The text on that row - `list.inactiveSelectionForeground`: `#ededed` in 2026 Dark, a step
   * *above* [sideBarForeground] so the open row reads as the one in front, and `#202020` in 2026
   * Light, where the wash is translucent and both ids coincide.
   */
  val listInactiveSelectionForeground: Color,
  /**
   * Between the activity bar and the side panel - `activityBar.border`, the reference's own words
   * being *"border color separating to the side bar"* (`workbench/common/theme.ts:417`).
   *
   * **Drawn by the bar itself**, which is where the reference draws it:
   * `.monaco-workbench .activitybar.left.bordered::before` is a 1px `border-right`
   * (`parts/activitybar/media/activitybarpart.css:30-34`), and JupyterLab budgets the same line
   * into the tab strip's own width - `calc(var(--jp-private-sidebar-tab-width) + var(--jp-border-width))`
   * (`packages/application/style/sidepanel.css:41-44`).
   *
   * **It used to be drawn and not stated.** `Navigation.kt`'s seam was a bare `VerticalDivider()`,
   * whose colour is `DividerDefaults.color` -> `outlineVariant` -> `ColorRamp.DarkGray5`, which is
   * `panel.border`. The same hex as this id in both default themes, a different id, and nothing
   * holding it - the trap [ChromePalette]'s own KDoc opens with.
   */
  val activityBarBorder: Color,
  /**
   * Between the side panel and the document - `sideBar.border`, *"border color on the side
   * separating to the editor"* (`workbench/common/theme.ts:623`), drawn by the side bar as a 1px
   * `borderRightWidth` on its own container (`parts/sidebar/sidebarPart.ts:175-180`).
   *
   * **This is the one nothing drew.** Both ids resolve to `#2A2B2C` / `#F0F1F2` in the 2026 themes,
   * and the app drew the first and let the panel abut the page: measured at the seam on a two-pane
   * tablet, the side panel's band ending at x661 and the document's beginning at x662 with no
   * column between them.
   *
   * Equal to [activityBarBorder] in both default themes and **kept separate anyway**, for the
   * reason [sideBarBackground] is kept separate from [activityBarBackground], and the reason a
   * fold here had to be undone once already: two ids on one field cannot follow a theme that
   * parts them.
   */
  val sideBarBorder: Color,
  /**
   * Behind the main area's tab strip - `editorGroupHeader.tabsBackground`.
   *
   * The same hex as [activityBarBackground] in both default themes and stated separately anyway:
   * the strip is a different part, and a theme may draw it a step down from the bar.
   */
  val tabStripBackground: Color,
  /**
   * The strip's own bottom edge - `editorGroupHeader.tabsBorder`, the border separating the strip
   * from the editor group below it. Equal to [sideBarBorder] in both default themes.
   */
  val tabStripBorder: Color,
  /**
   * Behind the tab whose document is on screen - `tab.activeBackground`, which both default
   * themes state as the editor's own background so the current tab reads as part of the page
   * (JupyterLab does the same: `lm-mod-current` wears `--jp-layout-color1`, the main area's own).
   */
  val tabActiveBackground: Color,
  /**
   * Behind every tab but the current one - `tab.inactiveBackground`. Equal to
   * [tabStripBackground] in both default themes, two ids.
   */
  val tabInactiveBackground: Color,
  /**
   * The 2px line across the top of the current tab - `tab.activeBorderTop`. 2026 Light states it
   * **black**, the theme's own answer for marking the active tab against a white page.
   */
  val tabActiveBorderTop: Color,
  /**
   * Between one tab and the next - `tab.border`. Equal to [tabStripBorder] in both default themes,
   * a different id, the rule [activityBarBorder]'s own note already records.
   */
  val tabBorder: Color,
)
